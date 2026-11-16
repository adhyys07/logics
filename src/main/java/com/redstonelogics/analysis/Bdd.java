package com.redstonelogics.analysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Bdd {

    public static final int ZERO = 0;
    public static final int ONE = 1;

    private final List<int[]> nodes = new ArrayList<>();      // {var, low, high}
    private final Map<Long, Integer> unique = new HashMap<>();
    private final Map<Long, Integer> memo = new HashMap<>();

    public Bdd() {
        nodes.add(new int[] { Integer.MAX_VALUE, -1, -1 });   // ZERO
        nodes.add(new int[] { Integer.MAX_VALUE, -1, -1 });   // ONE
    }

    private boolean terminal(int n) {
        return n == ZERO || n == ONE;
    }

    public int var(int n) {
        return nodes.get(n)[0];
    }

    private int low(int n){
        return nodes.get(n)[1];
    }

    private int high(int n){
        return nodes.get(n)[2];
    }

    private int mk(int variable, int lo, int hi) {
        if (lo == hi) {
            return lo;
        }
        long key = (((long) variable) << 42) ^ (((long) lo) << 21) ^ hi;
        Integer seen = unique.get(key);
        if (seen != null) {
            return seen;
        }
        nodes.add(new int[] { variable, lo, hi });
        int id = nodes.size() - 1;
        unique.put(key, id);
        return id;
    }

    public int variable(int index) {
        return mk(index, ZERO, ONE);
    }

    public int constant(boolean value) {
        return value ? ONE : ZERO;
    }

    private static final int AND = 0;
    private static final int OR = 1;
    private static final int XOR = 2;

     public int and(int a, int b) {
        return apply(AND, a, b);
    }

    public int or(int a, int b) {
        return apply(OR, a, b);
    }

    public int xor(int a, int b) {
        return apply(XOR, a, b);
    }

    public int not(int a ){
        return apply(XOR, a, ONE);
    }

    private int apply(int op, int a, int b) {
        Integer shortcut = trivial(op, a, b);
        if (shortcut != null) {
            return shortcut;
        }
        long key = (((long) op) << 60) ^ (((long) Math.min(a, b)) << 30) ^ Math.max(a, b);
        Integer seen = memo.get(key);
        if (seen != null) {
            return seen;
        }
        int v = Math.min(var(a), var(b));
        int result = mk(v,
                apply(op, branch(a, v, false), branch(b, v, false)),
                apply(op, branch(a, v, true), branch(b, v, true)));
        memo.put(key, result);
        return result;
    }

    private Integer trivial(int op, int a, int b){
        switch(op) {
            case AND:
                if (a == ZERO || b == ZERO) {
                    return ZERO;
                }
                if (a == ONE) {
                    return b;
                }
                if (b == ONE) {
                    return a;
                }
                if (a == b) {
                    return a;
                }
                return null;
            case OR:
                if (a == ONE || b == ONE) {
                    return ONE;
                }
                if (a == ZERO) {
                    return b;
                }
                if (b == ZERO) {
                    return a;
                }
                if (a == b) {
                    return a;
                }
                return null;
            default:
                if (a == b) {
                    return ZERO;
                }
                if (a == ZERO) {
                    return b;
                }
                if (b == ZERO) {
                    return a;
                }
                return null;
        }
    }

    private int branch(int n, int v, boolean value) {
        if (terminal(n) || var(n) != v) {
            return n;                                 
        }
        return value ? high(n) : low(n);
    }

    public int restrict(int n, int v, boolean value) {
        if (terminal(n)) {
            return n;
        }
        if (var(n) > v) {
            return n;
        }
        if (var(n) == v) {
            return value ? high(n) : low(n);
        }
        return mk(var(n), restrict(low(n), v, value), restrict(high(n), v, value));
    }

    public boolean evaluate(int n, boolean[] assignment) {
        int cur = n;
        while (!terminal(cur)) {
            int v = var(cur);
            cur = (v < assignment.length && assignment[v]) ? high(cur) : low(cur);
        }
        return cur == ONE;
    }

    public int[] support(int n) {
        java.util.TreeSet<Integer> out = new java.util.TreeSet<>();
        collectSupport(n, out, new java.util.HashSet<>());
        int[] arr = new int[out.size()];
        int i = 0;
        for (int v : out) {
            arr[i++] = v;
        }
        return arr;
    }

    private void collectSupport(int n, java.util.Set<Integer> out, java.util.Set<Integer> seen) {
        if (terminal(n) || !seen.add(n)) {
            return;
        }
        out.add(var(n));
        collectSupport(low(n), out, seen);
        collectSupport(high(n), out, seen);
    }

    public int nodeCount(int n) {
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        countNodes(n, seen);
        return seen.size();
    }

    private void countNodes(int n, java.util.Set<Integer> seen) {
        if (terminal(n) || !seen.add(n)) {
            return;
        }
        countNodes(low(n), seen);
        countNodes(high(n), seen);
    }

    public String toExpression(int n, java.util.function.IntFunction<String> name) {
        if (n == ZERO) {
            return "0";
        }
        if (n == ONE) {
            return "1";
        }
        List<String> terms = new ArrayList<>();
        walk(n, new ArrayList<>(), terms, name);
        return String.join(" OR ", terms);
    }

    private void walk(int n, List<String> literals, List<String> terms,
            java.util.function.IntFunction<String> name) {
        if (n == ZERO) {
            return;
        }
        if (n == ONE) {
            terms.add(literals.isEmpty() ? "1" : String.join(" AND ", literals));
            return;
        }
        literals.add("NOT " + name.apply(var(n)));
        walk(low(n), literals, terms, name);
        literals.remove(literals.size() - 1);
        
        literals.add(name.apply(var(n)));
        walk(high(n), literals, terms, name);
        literals.remove(literals.size() - 1);
    }

    /** Interns a node directly. Children must have a higher variable index. */
    public int node(int variable, int lo, int hi) {
        return mk(variable, lo, hi);
    }

    /** One product term: {@code vars[i]} appears, negated when !positive[i]. */
    public record Term(int[] vars, boolean[] positive) {

        static final Term EMPTY = new Term(new int[0], new boolean[0]);

        Term prefixed(int v, boolean value) {
            int[] nv = new int[vars.length + 1];
            boolean[] np = new boolean[positive.length + 1];
            nv[0] = v;
            np[0] = value;
            System.arraycopy(vars, 0, nv, 1, vars.length);
            System.arraycopy(positive, 0, np, 1, positive.length);
            return new Term(nv, np);
        }
    }

    /** A cover, with the function it covers kept alongside it as a node. */
    private record Cover(int node, List<Term> terms) {
    }

    private final Map<Long, Cover> isopCache = new HashMap<>();

    /**
     * An irredundant sum of products for this node.
     *
     * <p>Minato-Morreale: the recursion carries a lower and an upper bound,
     * {@code L} subset of f subset of {@code U}, and the gap between them is
     * the freedom a cube has to overlap its neighbours. That overlap is the
     * whole point - the obvious three-way split into "needs v", "needs not v"
     * and "needs neither" produces a <em>disjoint</em> cover, which is correct
     * but renders an n-input OR as n nested terms instead of {@code a OR b OR
     * c}. Cost tracks the diagram's size, not 2^n.
     */
    public List<Term> isop(int n) {
        return isop(n, n).terms();
    }

    private Cover isop(int lower, int upper) {
        if (upper == ONE) {
            return new Cover(ONE, List.of(Term.EMPTY));
        }
        if (lower == ZERO) {
            return new Cover(ZERO, List.of());
        }
        // Past those two cases both bounds are non-terminal: lower cannot be
        // ONE without upper being ONE, and upper cannot be ZERO without lower
        // being ZERO.
        long key = (((long) lower) << 32) | (upper & 0xFFFFFFFFL);
        Cover hit = isopCache.get(key);
        if (hit != null) {
            return hit;
        }

        int v = Math.min(var(lower), var(upper));
        int l0 = branch(lower, v, false);
        int l1 = branch(lower, v, true);
        int u0 = branch(upper, v, false);
        int u1 = branch(upper, v, true);

        // What only the v=0 half must cover, and likewise for v=1.
        Cover c0 = isop(and(l0, not(u1)), u0);
        Cover c1 = isop(and(l1, not(u0)), u1);

        // Whatever those two left behind needs no literal on v at all.
        Cover cd = isop(or(and(l0, not(c0.node())), and(l1, not(c1.node()))),
                and(u0, u1));

        int lit = variable(v);
        int covered = or(cd.node(),
                or(and(not(lit), c0.node()), and(lit, c1.node())));

        List<Term> terms = new ArrayList<>(cd.terms());
        for (Term t : c0.terms()) {
            terms.add(t.prefixed(v, false));
        }
        for (Term t : c1.terms()) {
            terms.add(t.prefixed(v, true));
        }

        Cover result = new Cover(covered, List.copyOf(terms));
        isopCache.put(key, result);
        return result;
    }

    /** The cover rendered as an expression, for a function held only as a BDD. */
    public String toSop(int n, java.util.function.IntFunction<String> name) {
        List<Term> terms = isop(n);
        if (terms.isEmpty()) {
            return "0";
        }
        List<String> parts = new ArrayList<>();
        for (Term t : terms) {
            if (t.vars().length == 0) {
                return "1";
            }
            List<String> literals = new ArrayList<>();
            for (int i = 0; i < t.vars().length; i++) {
                literals.add((t.positive()[i] ? "" : "NOT ")
                        + name.apply(t.vars()[i]));
            }
            parts.add(terms.size() > 1 && literals.size() > 1
                    ? "(" + String.join(" AND ", literals) + ")"
                    : String.join(" AND ", literals));
        }
        return String.join(" OR ", parts);
    }


    public TruthTable toTruthTable(int n, int[] support) {
        if (support.length > TruthTable.MAX_VARS) {
            throw new IllegalArgumentException(
                    "Support of " + support.length + " exceeds the table limit.");
        }
        boolean[] values = new boolean[1 << support.length];
        boolean[] assignment = new boolean[maxVar(support) + 1];
        for (int row = 0; row < values.length; row++) {
            for (int i = 0; i < support.length; i++) {
                assignment[support[i]] = ((row >>> i) & 1) != 0;
            }
            values[row] = evaluate(n, assignment);
        }
        return TruthTable.of(support.length, values);
    }

    private static int maxVar(int[] support) {
        int m = 0;
        for (int v : support) {
            m = Math.max(m, v);
        }
        return m;
    }
}