package com.redstonelogics.client.gui;

import com.redstonelogics.analysis.BooleanAbstraction;
import com.redstonelogics.graph.Netlist;

import java.util.List;

/**
 * Reads one output out of an analysis at a given input assignment.
 *
 * <p>Each {@link BooleanAbstraction.Fn} carries only the variables it actually
 * depends on, in its own order, so a global assignment over every input has to
 * be projected onto that function's own row index before the table is read.
 */
final class Evaluate {

    private Evaluate() {
    }

    /**
     * @param assignment bit i set means input {@code ins.get(i)} is on
     * @return the output value, or null when the function is not known
     *         (a latch, or anything the abstraction could not resolve)
     */
    static Boolean output(BooleanAbstraction logic, Netlist.Element out,
            List<Netlist.Element> ins, int assignment) {
        BooleanAbstraction.Fn fn = logic.function(out.id);
        if (!fn.tabulated()) {
            return null;
        }
        List<String> names = logic.namesFor(fn);
        int row = 0;
        for (int v = 0; v < names.size(); v++) {
            int index = indexOf(ins, names.get(v));
            if (index >= 0 && ((assignment >>> index) & 1) != 0) {
                row |= 1 << v;
            }
        }
        return fn.table().get(row);
    }

    private static int indexOf(List<Netlist.Element> ins, String label) {
        for (int i = 0; i < ins.size(); i++) {
            if (ins.get(i).label.equals(label)) {
                return i;
            }
        }
        return -1;
    }
}
