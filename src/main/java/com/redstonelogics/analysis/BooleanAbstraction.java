package com.redstonelogics.analysis;

import com.redstonelogics.graph.Netlist;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

public final class BooleanAbstraction {
    public record Fn(int[] vars, TruthTable table) {

        public static final Fn UNKNOWN = new Fn(null, null);

        public boolean known() {
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

    public static BooleanAbstraction of(Netlist netlist) {
        BooleanAbstraction b = new BooleanAbstraction(netlist);
        b.findComponents();
        b.chooseVariables();
        b.evaluateAll();
        return b;
    }

    // ------------------------------------------------------- feedback detection

    /** Tarjan's algorithm, iterative so that a large circuit cannot blow the stack. */
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

        // A component holds state if it is a real loop, or a single element that
        // reads its own output.
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

    /**
     * Picks the cut that every function is expressed over: free inputs, state
     * nodes, and any node read by more than one consumer. Cutting at fanout
     * keeps shared signals as distinct wires in the diagram instead of
     * duplicating the logic behind them into every consumer.
     */
    private void chooseVariables() {
        int[] fanout = netlist.fanout();
        for (Netlist.Element e : netlist.elements()) {
            variable[e.id] = switch (e.kind) {
                case INPUT -> true;
                case CONSTANT, OUTPUT -> false;
                default -> state[e.id] || fanout[e.id] > 1;
            };
        }
    }

    // ------------------------------------------------------------- evaluation

    private void evaluateAll() {
        for (Netlist.Element e : netlist.elements()) {
            function(e.id);
        }
    }

    /** What this element computes, as a function of the variable cut. */
    public Fn function(int id) {
        if (cache[id] != null) {
            return cache[id];
        }
        if (visiting[id]) {
            // Unreachable while every cycle passes through a state node, but a
            // wrong answer here would be silent, so fail loudly instead.
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
            case CONSTANT -> new Fn(new int[0], TruthTable.constant(0, true));
            case INVERTER -> not(portValue(e, Netlist.PORT_MAIN));
            case BUFFER, CMP_COMPARE, OUTPUT -> portValue(e, Netlist.PORT_MAIN);
            case CMP_SUBTRACT -> and(portValue(e, Netlist.PORT_MAIN),
                    not(portValue(e, Netlist.PORT_SIDE)));
        };
    }

    /** Everything feeding one port merges with OR, because dust takes the maximum. */
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

    // -------------------------------------------------------------- Fn algebra

    private Fn not(Fn a) {
        return a.known() ? new Fn(a.vars(), a.table().not()) : Fn.UNKNOWN;
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
        if (union.length > TruthTable.MAX_VARS) {
            notes.add("A signal depends on " + union.length + " distinct sources, past the "
                    + TruthTable.MAX_VARS + "-variable limit; it is drawn as an opaque block.");
            return Fn.UNKNOWN;
        }
        TruthTable ta = a.table().expand(positions(a.vars(), union), union.length);
        TruthTable tb = b.table().expand(positions(b.vars(), union), union.length);
        return new Fn(union, conjunction ? ta.and(tb) : ta.or(tb));
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

    // ---------------------------------------------------------------- accessors

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

    /** The feedback loop this element belongs to, or a single-element list. */
    public List<Integer> componentOf(int id) {
        return components.get(componentOf[id]);
    }

    public List<String> notes() {
        return notes;
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
