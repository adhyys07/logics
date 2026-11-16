package com.redstonelogics.analysis;

import com.redstonelogics.graph.Netlist;
import com.redstonelogics.mapper.RedstoneLogicMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class Equivalence {
    public record Result(boolean equivalent, List<String> lines) {
    }

    public static Result compare(RedstoneLogicMapper a, String nameA,
                                 RedstoneLogicMapper b, String nameB) {
        List<String> lines = new ArrayList<>();
        boolean same = true;

        Set<String> vars = new LinkedHashSet<>();
        for (Netlist.Element e : a.netlist().inputs()) {
            vars.add(e.label);
        }
        for (Netlist.Element e : b.netlist().inputs()) {
            vars.add(e.label);
        }
        List<String> order = new ArrayList<>(vars);

        Set<String> outputs = new LinkedHashSet<>();
        for (Netlist.Element e : a.netlist().outputs()) {
            outputs.add(e.label);
        }

        for (Netlist.Element e : b.netlist().outputs()) {
            outputs.add(e.label);
        }

        for (String label: outputs) {
            TruthTable ta = aligned(a, label, order);
            TruthTable tb = aligned(b, label, order);

            if (ta == null || tb == null) {
                // A missing table means one of two very different things, and
                // reporting the wrong one sent people looking for a gate that
                // was there all along.
                boolean tooWide = (ta == null && hasOutput(a, label))
                        || (tb == null && hasOutput(b, label));
                lines.add(label + (tooWide
                        ? ": too wide to compare by truth table (over "
                                + TruthTable.MAX_VARS + " inputs)"
                        : ": present in only one circuit ("
                                + (ta != null ? nameA : nameB) + ")"));
                same = false;
                continue;
            }
            if (ta.equals(tb)) {
                lines.add(label + ": identical");
            } else {
                same = false;
                lines.add(label + ": DIFFERENT");
                lines.add("    " + nameA + ": " + ta.toExpression(order));
                lines.add("    " + nameB + ": " + tb.toExpression(order));
                lines.addAll(counterexamples(ta, tb, order));
            }
        }

        lines.add(0, (same ? "equivalent" : "NOT equivalent")
                + "  -  " + nameA + " (" + a.snapshot().size() + " blocks, "
                + a.diagram().gates().size() + " gates) vs "
                + nameB + " (" + b.snapshot().size() + " blocks, "
                + b.diagram().gates().size() + " gates)");
        return new Result(same, lines);
    }

    private static boolean hasOutput(RedstoneLogicMapper m, String label) {
        for (Netlist.Element e : m.netlist().outputs()) {
            if (e.label.equals(label)) {
                return true;
            }
        }
        return false;
    }

    private static TruthTable aligned(RedstoneLogicMapper m, String label, List<String> order) {
        Netlist.Element target = null;
        for (Netlist.Element e : m.netlist().outputs()) {
            if (e.label.equals(label)) {
                target = e;
                break;
            }
        }
        if (target == null) {
            return null;
        }
        BooleanAbstraction.Fn fn = m.logic().function(target.id);
        if (!fn.tabulated()) {
            return null;
        }
        List<String> names = m.logic().namesFor(fn);
        int[] positions = new int[names.size()];
        for (int i = 0; i < names.size(); i++) {
            positions[i] = order.indexOf(names.get(i));
        }
        return fn.table().expand(positions, order.size());
    }

    private static List<String> counterexamples(TruthTable ta, TruthTable tb, List<String> order) {
        List<String> out = new ArrayList<>();
        int shown = 0;
        for (int row = 0; row < ta.rows() && shown < 4; row++) {
            if (ta.get(row) == tb.get(row)) {
                continue;
            }
            StringBuilder sb = new StringBuilder("    differs at ");
            for (int v = 0; v < order.size(); v++) {
                sb.append(v == 0 ? "" : ", ").append(order.get(v))
                  .append('=').append(((row >>> v) & 1));
            }
            sb.append("  -> ").append(ta.get(row) ? 1 : 0)
              .append(" vs ").append(tb.get(row) ? 1 : 0);
            out.add(sb.toString());
            shown++;
        } 
        return  out;
    }

    private Equivalence() {
    }
}