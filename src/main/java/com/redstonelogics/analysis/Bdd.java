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