package com.redstonelogics.analysis;

import java.util.ArrayList;
import java.util.List;

public final class Cubes {

    private Cubes() {
     }

    public static List<TruthTable.Cube> of(TruthTable t) {
        Bdd m = new Bdd();
        int node = build(m, t, 0, 0);
        return translate(m.isop(node), t.vars, null);
    }

    public static List<TruthTable.Cube> ofNode(Bdd m, int node, int[] support) {
        return translate(m.isop(node), support.length, support);
    }

    private static int build(Bdd m, TruthTable t, int v, int fixed) {
        if (v == t.vars) {
            return m.constant(t.get(fixed));
        }
        int lo = build(m, t, v + 1, fixed);
        int hi = build(m, t, v + 1, fixed | (1 << v));
        return m.node(v, lo, hi);
    }

    private static List<TruthTable.Cube> translate(List<Bdd.Term> terms, int width,
            int[] support) {
        int all = width >= 32 ? -1 : (1 << width) - 1;
        List<TruthTable.Cube> out = new ArrayList<>(terms.size());
        for (Bdd.Term term : terms) {
            int value = 0;
            int care = 0;
            for (int i = 0; i < term.vars().length; i++) {
                int slot = support == null ? term.vars()[i] : indexOf(support, term.vars()[i]);
                if (slot < 0) {
                    continue;              
                }
                int bit = 1 << slot;
                care |= bit;
                if (term.positive()[i]) {
                    value |= bit;
                }
            }
            out.add(new TruthTable.Cube(value, all & ~care));
        }
        return out;
    }

    private static int indexOf(int[] support, int v) {
        for (int i = 0; i < support.length; i++) {
            if (support[i] == v) {
                return i;
            }
        }
        return -1;
    }
}