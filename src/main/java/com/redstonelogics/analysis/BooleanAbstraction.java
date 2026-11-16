package com.redstonelogics.analysis;

import com.redstonelogics.graph.Netlist;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

public final class BooleanAbstraction {
    /**
     * A recovered boolean function, held either as a full truth table or, when
     * its support exceeds {@link TruthTable#MAX_VARS}, as a {@link Bdd} node.
     *
     * @param vars  netlist element ids backing each variable
     * @param table the tabulated function, or null when only a BDD is held
     * @param bdd   the manager owning {@code node}, or null
     * @param node  the BDD node id, or -1
     */
    public record Fn(int[] vars, TruthTable table, Bdd bdd, int node) {

        public static final Fn UNKNOWN = new Fn(null, null, null, -1);

        /** The tabulated form, which is what most of the pipeline builds. */
        public Fn(int[] vars, TruthTable table) {
            this(vars, table, null, -1);
        }

        public boolean known() {
            return table != null || bdd != null;
        }

        /**
         * Whether a full truth table is available. Anything that renders a sum
         * of products, indexes rows or minimises needs this: a BDD-only
         * function is still known, but has to be handled structurally.
         */
        public boolean tabulated() {
            return table != null;
        }
    }

    private final Netlist netlist;
    private final List<List<Integer>> components = new ArrayList<>();
    private final int[] componentOf;
    private final boolean[] state;
    private final boolean[] variable;
    private final Fn[] cache;
    private final boolean[] visiting;
    private final List<String> notes = new ArrayList<>();

    private BooleanAbstraction(Netlist netlist) {
        this.netlist = netlist;
        int n = netlist.elements().size();
        this.componentOf = new int[n];
        this.state = new boolean[n];
        this.variable = new boolean[n];
        this.cache = new Fn[n];
        this.visiting = new boolean[n];
    }

    private static final int TABULATION_LIMIT = TruthTable.MAX_VARS;

    private Bdd bdd;
    private int widestBdd;
    private int widestBddNodes;
    private int reducedFromWidth;

    public static BooleanAbstraction of(Netlist netlist) {
        BooleanAbstraction b = new BooleanAbstraction(netlist);
        b.findComponents();
        b.chooseVariables();
        b.evaluateAll();
        b.noteWideFunctions();
        return b;
    }

    private void findComponents() {
        List<List<Integer>> successors = netlist.consumers();
        int n = successors.size();
        int[] order = new int[n];
        int[] low = new int[n];
        boolean[] onStack = new boolean[n];
        Arrays.fill(order, -1);
        Deque<Integer> pending = new ArrayDeque<>();
        int[] frameNode = new int[n + 1];
        int[] frameChild = new int[n + 1];
        int counter = 0;

        for (int start = 0; start < n; start++) {
            if (order[start] != -1) {
                continue;
            }
            int top = 0;
            frameNode[0] = start;
            frameChild[0] = 0;
            order[start] = counter;
            low[start] = counter++;
            pending.push(start);
            onStack[start] = true;

            while (top >= 0) {
                int v = frameNode[top];
                List<Integer> adjacent = successors.get(v);
                if (frameChild[top] < adjacent.size()) {
                    int w = adjacent.get(frameChild[top]++);
                    if (order[w] == -1) {
                        order[w] = counter;
                        low[w] = counter++;
                        pending.push(w);
                        onStack[w] = true;
                        frameNode[++top] = w;
                        frameChild[top] = 0;
                    } else if (onStack[w]) {
                        low[v] = Math.min(low[v], order[w]);
                    }
                } else {
                    if (low[v] == order[v]) {
                        List<Integer> group = new ArrayList<>();
                        while (true) {
                            int w = pending.pop();
                            onStack[w] = false;
                            group.add(w);
                            if (w == v) {
                                break;
                            }
                        }
                        for (int w : group) {
                            componentOf[w] = components.size();
                        }
                        components.add(group);
                    }
                    top--;
                    if (top >= 0) {
                        int parent = frameNode[top];
                        low[parent] = Math.min(low[parent], low[v]);
                    }
                }
            }
        }

        for (List<Integer> group : components) {
            boolean loop = group.size() > 1;
            if (!loop) {
                int only = group.get(0);
                for (Netlist.Link l : netlist.element(only).allInputs()) {
                    if (l.from == only) {
                        loop = true;
                        break;
                    }
                }
            }
            if (loop) {
                for (int id : group) {
                    state[id] = true;
                }
                if (group.size() > 1) {
                    List<String> labels = new ArrayList<>();
                    for (int id : group) {
                        labels.add(netlist.element(id).label);
                    }
                    notes.add("Feedback loop across " + String.join(", ", labels)
                            + " - treated as stored state.");
                }
            }
        }
    }

    private void chooseVariables() {
        int[] fanout = netlist.fanout();
        for (Netlist.Element e : netlist.elements()) {
            variable[e.id] = switch (e.kind) {
                case INPUT, EDGE -> true;
                case CONSTANT, OUTPUT -> false;
                default -> state[e.id] || fanout[e.id] > 1;
            };
        }
    }


    private void evaluateAll() {
        for (Netlist.Element e : netlist.elements()) {
            function(e.id);
        }
    }

    public Fn function(int id) {
        if (cache[id] != null) {
            return cache[id];
        }
        if (visiting[id]) {
            notes.add("Unbroken feedback at " + netlist.element(id).label + "; function abandoned.");
            return Fn.UNKNOWN;
        }
        visiting[id] = true;
        Fn result = compute(netlist.element(id));
        visiting[id] = false;
        cache[id] = result;
        return result;
    }

    private Fn compute(Netlist.Element e) {
        return switch (e.kind) {
            case INPUT -> varOf(e.id);
            // An observer pulse is a transient with no steady-state value, so
            // it is carried as a free input rather than solved for.
            case EDGE -> {
                notes.add(e.label + " is an observer pulse: a 2-tick transient with no "
                        + "steady-state value, carried as a free input.");
                yield varOf(e.id);
            }
            case CONSTANT -> new Fn(new int[0], TruthTable.constant(0, true));
            case INVERTER -> not(portValue(e, Netlist.PORT_MAIN));
            case BUFFER -> e.locked ? Fn.UNKNOWN : portValue(e, Netlist.PORT_MAIN);
            case CMP_COMPARE, OUTPUT -> portValue(e, Netlist.PORT_MAIN);
            case CMP_SUBTRACT -> and(portValue(e, Netlist.PORT_MAIN),
                    not(portValue(e, Netlist.PORT_SIDE)));
        };
    }

    private Fn portValue(Netlist.Element e, int port) {
        List<Netlist.Link> links = e.port(port);
        if (links.isEmpty()) {
            return new Fn(new int[0], TruthTable.constant(0, false));
        }
        Fn acc = valueOf(links.get(0).from);
        for (int i = 1; i < links.size(); i++) {
            acc = or(acc, valueOf(links.get(i).from));
        }
        return acc;
    }

    public Fn valueOf(int driver) {
        return variable[driver] ? varOf(driver) : function(driver);
    }

    private Fn varOf(int id) {
        return new Fn(new int[] { id }, TruthTable.variable(1, 0));
    }


    private Fn not(Fn a) {
        if (!a.known()) {
            return Fn.UNKNOWN;
        }
        if (!a.tabulated()) {
            int node = a.bdd().not(a.node());
            return fromNode(a.bdd(), node);
        }
        return new Fn(a.vars(), a.table().not());
    }

    private Fn and(Fn a, Fn b) {
        return zip(a, b, true);
    }

    public Fn or(Fn a, Fn b) {
        return zip(a, b, false);
    }

    private Fn zip(Fn a, Fn b, boolean conjunction) {
        if (!a.known() || !b.known()) {
            return Fn.UNKNOWN;
        }
        int[] union = union(a.vars(), b.vars());
        if (union.length > TABULATION_LIMIT) {
            return combineAsBdd(a, b, conjunction, union.length);
        }
        TruthTable ta = a.table().expand(positions(a.vars(), union), union.length);
        TruthTable tb = b.table().expand(positions(b.vars(), union), union.length);
        return new Fn(union, conjunction ? ta.and(tb) : ta.or(tb));
    }

    private Fn combineAsBdd(Fn a, Fn b, boolean conjunction, int unionSize) {
        if (bdd == null) {
            bdd = new Bdd();
        }
        int na = toNode(a);
        int nb = toNode(b);
        int node = conjunction ? bdd.and(na, nb) : bdd.or(na, nb);
        Fn out = fromNode(bdd, node);
        if (out.tabulated()) {
            reducedFromWidth = Math.max(reducedFromWidth, unionSize);
        } else {
            widestBdd = Math.max(widestBdd, out.vars().length);
            widestBddNodes = Math.max(widestBddNodes, bdd.nodeCount(node));
        }
        return out;
    }

    private static Fn fromNode(Bdd manager, int node) {
        int[] support = manager.support(node);
        if (support.length <= TABULATION_LIMIT) {
            return new Fn(support, manager.toTruthTable(node, support), manager, node);
        }
        return new Fn(support, null, manager, node);
    }

    private int toNode(Fn f) {
        if (!f.tabulated()) {
            return f.node();
        }
        TruthTable t = f.table();
        int acc = Bdd.ZERO;
        for (TruthTable.Cube c : t.sop()) {
            int term = Bdd.ONE;
            for (int v = 0; v < t.vars; v++) {
                int bit = 1 << v;
                if ((c.mask() & bit) != 0) {
                    continue;                       // a don't-care
                }
                int literal = bdd.variable(f.vars()[v]);
                if ((c.value() & bit) == 0) {
                    literal = bdd.not(literal);
                }
                term = bdd.and(term, literal);
            }
            acc = bdd.or(acc, term);
        }
        return acc;
    }

    private void noteWideFunctions() {
        if (reducedFromWidth > 0) {
            notes.add("A signal spanned " + reducedFromWidth + " sources but cancels down to "
                    + "fewer; recovered through a decision diagram rather than being "
                    + "written off as opaque.");
        }
        if (widestBdd > 0) {
            notes.add("The widest signal depends on " + widestBdd + " distinct sources, past "
                    + "the " + TABULATION_LIMIT + "-variable table limit; held as a decision "
                    + "diagram of " + widestBddNodes + " node(s). It is drawn as one block, "
                    + "but its expression is still recovered and written underneath.");
        }
    }

    private static int[] union(int[] a, int[] b) {
        int[] merged = new int[a.length + b.length];
        System.arraycopy(a, 0, merged, 0, a.length);
        System.arraycopy(b, 0, merged, a.length, b.length);
        return Arrays.stream(merged).distinct().sorted().toArray();
    }

    private static int[] positions(int[] vars, int[] union) {
        int[] out = new int[vars.length];
        for (int i = 0; i < vars.length; i++) {
            out[i] = Arrays.binarySearch(union, vars[i]);
        }
        return out;
    }
    


    public Netlist netlist() {
        return netlist;
    }

    public boolean isVariable(int id) {
        return variable[id];
    }

    public boolean isState(int id) {
        return state[id];
    }

    public List<List<Integer>> components() {
        return components;
    }

    public List<Integer> componentOf(int id) {
        return components.get(componentOf[id]);
    }

    public List<String> notes() {
        return notes;
    }

    /**
     * A function's minimised expression, whether it is tabulated or not.
     *
     * <p>This is the point of the BDD cover: a signal past the table limit used
     * to be drawn as an unexplained block.
     */
    public String expressionOf(Fn fn) {
        if (!fn.known()) {
            return "unknown";
        }
        if (fn.tabulated()) {
            return fn.table().toExpression(namesFor(fn));
        }
        return fn.bdd().toSop(fn.node(), id -> netlist.element(id).label);
    }

    /** The cover of a function, with cube bits indexed in support order. */
    public List<TruthTable.Cube> cubesOf(Fn fn) {
        if (!fn.known()) {
            return List.of();
        }
        return fn.tabulated()
                ? fn.table().sop()
                : Cubes.ofNode(fn.bdd(), fn.node(), fn.vars());
    }

    /** Display names for a function's variables, in table-variable order. */
    public List<String> namesFor(Fn fn) {
        List<String> names = new ArrayList<>();
        if (fn.known()) {
            for (int id : fn.vars()) {
                names.add(netlist.element(id).label);
            }
        }
        return names;
    }
}
