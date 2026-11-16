package com.redstonelogics.export;

import com.redstonelogics.analysis.BooleanAbstraction;
import com.redstonelogics.analysis.TruthTable;
import com.redstonelogics.graph.Netlist;
import com.redstonelogics.mapper.RedstoneLogicMapper;

import java.util.ArrayList;
import java.util.List;

public final class CsvExporter {
    public static String render(RedstoneLogicMapper mapped) {
        Netlist netlist = mapped.netlist();
        BooleanAbstraction logic = mapped.logic();

        List<String> ins = new ArrayList<>();
        for (Netlist.Element e : netlist.inputs()) {
            ins.add(e.label);
        }
        List<Netlist.Element> outs = netlist.outputs();

        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", ins));
        for (Netlist.Element o : outs) {
            sb.append(',').append(o.label);
        }
        sb.append('\n');

        for (int combo=0; combo < (1 << ins.size()); combo++) {
            for (int i = 0; i < ins.size(); i++) {
                sb.append(i == 0 ? "" : ",").append((combo >>> i) & 1);
            }
            for (Netlist.Element o : outs) {
                sb.append(',').append(valueAt(logic, o, ins, combo));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String valueAt(BooleanAbstraction logic, Netlist.Element out, List<String> ins, int combo) {
        BooleanAbstraction.Fn fn = logic.function(out.id);
        if (!fn.tabulated()) {
            return "x";
        }
        List<String> names = logic.namesFor(fn);
        TruthTable t = fn.table();
        int row = 0;
        for (int v = 0; v < names.size(); v++) {
            int globalIndex = ins.indexOf(names.get(v));
            if (globalIndex >= 0 && ((combo >>> globalIndex) & 1) != 0) {
                row |= 1 << v;
            }
        }
        return t.get(row) ? "1" : "0";
    }

    private CsvExporter(){
    }
}