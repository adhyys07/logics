package com.redstonelogics.analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class TruthTable {

    public static final int MAX_VARS = 16;

    public final int vars;
    private final long[] words;

    private TruthTable(int vars) {
        if (vars < 0 || vars > MAX_VARS) {
            throw new IllegalArgumentException("variable count out of range: " + vars);
        }
        this.vars = vars;
        this.words = new long[Math.max(1, (1 << vars) / 64)];
    }

    public int rows() {
        return 1 << vars;
    }

    public boolean get(int row) {
        return ((words[row >>> 6] >>> (row & 63)) & 1L) != 0L;
    }

    private void set(int row, boolean value) {
        if (value) {
            words[row >>> 6] |= 1L << (row & 63);
        } else {
            words[row >>> 6] &= ~(1L << (row & 63));
        }
    }


    public static TruthTable constant(int vars, boolean value) {
        TruthTable t = new TruthTable(vars);
        if (value) {
            Arrays.fill(t.words, -1L);
            t.trim();
        }
        return t;
    }

    public static TruthTable variable(int vars, int index) {
        TruthTable t = new TruthTable(vars);
        for (int r = 0; r < t.rows(); r++) {
            t.set(r, ((r >>> index) & 1) != 0);
        }
        return t;
    }

    public static TruthTable of(int vars, boolean[] rows) {
        TruthTable t = new TruthTable(vars);
        if (rows.length != t.rows()) {
            throw new IllegalArgumentException(
                    "expected " + t.rows() + " rows, got " + rows.length);
        }
        for (int r = 0; r < rows.length; r++) {
            t.set(r, rows[r]);
        }
        t.trim();
        return t;
    }

    private void trim() {
        int rows = rows();
        if (rows < 64) {
            words[0] &= (1L << rows) - 1;
        }
    }

    public TruthTable not() {
        TruthTable r = new TruthTable(vars);
        for (int i = 0; i < words.length; i++) {
            r.words[i] = ~words[i];
        }
        r.trim();
        return r;
    }

    public TruthTable and(TruthTable o) {
        return combine(o, 0);
    }

    public TruthTable or(TruthTable o) {
        return combine(o, 1);
    }

    public TruthTable xor(TruthTable o) {
        return combine(o, 2);
    }

    private TruthTable combine(TruthTable o, int op) {
        if (vars != o.vars) {
            throw new IllegalArgumentException("table width mismatch: " + vars + " vs " + o.vars);
        }
        TruthTable r = new TruthTable(vars);
        for (int i = 0; i < words.length; i++) {
            r.words[i] = switch (op) {
                case 0 -> words[i] & o.words[i];
                case 1 -> words[i] | o.words[i];
                default -> words[i] ^ o.words[i];
            };
        }
        r.trim();
        return r;
    }

    public TruthTable expand(int[] positions, int targetVars) {
        TruthTable r = new TruthTable(targetVars);
        for (int row = 0; row < r.rows(); row++) {
            int source = 0;
            for (int i = 0; i < positions.length; i++) {
                if (((row >>> positions[i]) & 1) != 0) {
                    source |= 1 << i;
                }
            }
            r.set(row, get(source));
        }
        return r;
    }

    
    public TruthTable collapse(int[] keep) {
        TruthTable r = new TruthTable(keep.length);
        for (int row = 0; row < r.rows(); row++) {
            int source = 0;
            for (int i = 0; i < keep.length; i++) {
                if (((row >>> i) & 1) != 0) {
                    source |= 1 << keep[i];
                }
            }
            r.set(row, get(source));
        }
        return r;
    }

   
    public int supportMask() {
        int mask = 0;
        for (int v = 0; v < vars; v++) {
            int bit = 1 << v;
            for (int row = 0; row < rows(); row++) {
                if ((row & bit) == 0 && get(row) != get(row | bit)) {
                    mask |= bit;
                    break;
                }
            }
        }
        return mask;
    }

    public int[] supportVars() {
        int mask = supportMask();
        List<Integer> out = new ArrayList<>();
        for (int v = 0; v < vars; v++) {
            if ((mask & (1 << v)) != 0) {
                out.add(v);
            }
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    public boolean isConstant() {
        return supportMask() == 0;
    }

    public boolean constantValue() {
        return get(0);
    }


    /** One product term: {@code mask} bits are don't-cares, {@code value} holds the rest. */
    public record Cube(int value, int mask) {
    }

    /**
     * Above this width the sum of products comes from a decision diagram
     * instead. Quine-McCluskey is minimal, which is worth having for the small
     * tables that make up almost every gate, but its cost is independent of how
     * simple the answer is.
     */
    private static final int QM_LIMIT = 9;

    /**
     * A minimised sum of products. Empty list means constant false; a single
     * all-don't-care cube means true.
     */
    public List<Cube> sop() {
        return vars <= QM_LIMIT ? quineMcCluskey() : Cubes.of(this);
    }

    /** Quine-McCluskey with a greedy cover. Exact, but exponential in vars. */
    private List<Cube> quineMcCluskey() {
        List<Integer> minterms = new ArrayList<>();
        for (int row = 0; row < rows(); row++) {
            if (get(row)) {
                minterms.add(row);
            }
        }
        if (minterms.isEmpty()) {
            return List.of();
        }
        if (minterms.size() == rows()) {
            return List.of(new Cube(0, rows() - 1));
        }

        Set<Cube> current = new LinkedHashSet<>();
        for (int m : minterms) {
            current.add(new Cube(m, 0));
        }
        Set<Cube> primes = new LinkedHashSet<>();
        while (!current.isEmpty()) {
            Set<Cube> merged = new LinkedHashSet<>();
            boolean[] used = new boolean[current.size()];
            List<Cube> list = new ArrayList<>(current);
            for (int i = 0; i < list.size(); i++) {
                for (int j = i + 1; j < list.size(); j++) {
                    Cube a = list.get(i);
                    Cube b = list.get(j);
                    if (a.mask != b.mask) {
                        continue;
                    }
                    int diff = a.value ^ b.value;
                    if (Integer.bitCount(diff) == 1) {
                        merged.add(new Cube(a.value & ~diff, a.mask | diff));
                        used[i] = true;
                        used[j] = true;
                    }
                }
            }
            for (int i = 0; i < list.size(); i++) {
                if (!used[i]) {
                    primes.add(list.get(i));
                }
            }
            current = merged;
        }

        List<Cube> remaining = new ArrayList<>(primes);
        Set<Integer> uncovered = new LinkedHashSet<>(minterms);
        List<Cube> cover = new ArrayList<>();
        while (!uncovered.isEmpty() && !remaining.isEmpty()) {
            Cube best = null;
            int bestCount = 0;
            for (Cube c : remaining) {
                int count = 0;
                for (int m : uncovered) {
                    if ((m & ~c.mask) == c.value) {
                        count++;
                    }
                }
                if (count > bestCount) {
                    bestCount = count;
                    best = c;
                }
            }
            if (best == null) {
                break;
            }
            final Cube chosen = best;
            cover.add(chosen);
            remaining.remove(chosen);
            uncovered.removeIf(m -> (m & ~chosen.mask()) == chosen.value());
        }
        return cover;
    }

    /** Renders the minimised form, e.g. {@code A AND NOT B OR C}. */
    public String toExpression(List<String> names) {
        return render(names, " AND ", " OR ", "NOT ", "0", "1");
    }

    /** Renders the minimised form as a C/Arduino expression. */
    public String toCExpression(List<String> names) {
        return render(names, " && ", " || ", "!", "false", "true");
    }

    private String render(List<String> names, String and, String or, String not,
                          String falseText, String trueText) {
        List<Cube> cubes = sop();
        if (cubes.isEmpty()) {
            return falseText;
        }
        List<String> terms = new ArrayList<>();
        for (Cube c : cubes) {
            List<String> literals = new ArrayList<>();
            for (int v = 0; v < vars; v++) {
                int bit = 1 << v;
                if ((c.mask & bit) != 0) {
                    continue;
                }
                String name = v < names.size() ? names.get(v) : "v" + v;
                literals.add((c.value & bit) != 0 ? name : not + name);
            }
            if (literals.isEmpty()) {
                return trueText;
            }
            terms.add(literals.size() > 1 && cubes.size() > 1
                    ? "(" + String.join(and, literals) + ")"
                    : String.join(and, literals));
        }
        return String.join(or, terms);
    }


    @Override
    public boolean equals(Object o) {
        return o instanceof TruthTable t && t.vars == vars && Arrays.equals(t.words, words);
    }

    @Override
    public int hashCode() {
        return 31 * vars + Arrays.hashCode(words);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(vars + "v:");
        for (int r = 0; r < rows(); r++) {
            sb.append(get(r) ? '1' : '0');
        }
        return sb.toString();
    }
}
