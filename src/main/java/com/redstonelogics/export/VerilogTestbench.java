package com.redstonelogics.export;

import com.redstonelogics.analysis.BooleanAbstraction;
import com.redstonelogics.graph.Netlist;
import com.redstonelogics.mapper.RedstoneLogicMapper;

import java.util.ArrayList;
import java.util.List;

public final class VerilogTestbench {

    private static final int MAX_INPUTS = 12;

    private VerilogTestbench() {
    }

    public static String render(RedstoneLogicMapper mapped, String moduleName) {
        Netlist netlist = mapped.netlist();
        BooleanAbstraction logic = mapped.logic();
        List<Netlist.Element> ins = netlist.inputs();
        List<Netlist.Element> outs = netlist.outputs();

        StringBuilder sb = new StringBuilder();
        sb.append("`timescale 1ns/1ps\n");
        sb.append("// Exhaustive bench for ").append(moduleName)
          .append(", expectations from the scanned circuit.\n\n");
        sb.append("module ").append(moduleName).append("_tb;\n\n");

        if (ins.size() > MAX_INPUTS) {
            sb.append("  // ").append(ins.size())
              .append(" inputs is too many to enumerate exhaustively.\n");
            sb.append("  initial $display(\"skipped\");\nendmodule\n");
            return sb.toString();
        }

        for (Netlist.Element e : ins) {
            sb.append("  reg  ").append(id(e.label)).append(";\n");
        }
        for (Netlist.Element e : outs) {
            sb.append("  wire ").append(id(e.label)).append(";\n");
        }
        sb.append("  integer errors = 0;\n\n");

        sb.append("  ").append(moduleName).append(" dut (");
        List<String> ports = new ArrayList<>();
        for (Netlist.Element e : ins) {
            ports.add("." + id(e.label) + "(" + id(e.label) + ")");
        }
        for (Netlist.Element e : outs) {
            ports.add("." + id(e.label) + "(" + id(e.label) + ")");
        }
        sb.append(String.join(", ", ports)).append(");\n\n");

        sb.append("  initial begin\n");
        for (int combo = 0; combo < (1 << ins.size()); combo++) {
            for (int i = 0; i < ins.size(); i++) {
                sb.append("    ").append(id(ins.get(i).label))
                  .append(" = 1'b").append((combo >>> i) & 1).append(";\n");
            }
            sb.append("    #1;\n");
            for (Netlist.Element out : outs) {
                Boolean want = expected(logic, out, ins, combo);
                if (want == null) {
                    continue;                     
                }
                sb.append("    if (").append(id(out.label))
                  .append(" !== 1'b").append(want ? 1 : 0).append(") begin\n");
                sb.append("      $display(\"FAIL ").append(out.label)
                  .append(" combo %0d: got %b want ").append(want ? 1 : 0)
                  .append("\", ").append(combo).append(", ").append(id(out.label))
                  .append(");\n");
                sb.append("      errors = errors + 1;\n    end\n");
            }
        }
        sb.append("    if (errors == 0) $display(\"PASS: all ")
          .append(1 << ins.size()).append(" vectors matched\");\n");
        sb.append("    else $display(\"%0d mismatch(es)\", errors);\n");
        sb.append("    $finish;\n  end\n\nendmodule\n");
        return sb.toString();
    }

    private static Boolean expected(BooleanAbstraction logic, Netlist.Element out,
            List<Netlist.Element> ins, int combo) {
        BooleanAbstraction.Fn fn = logic.function(out.id);
        if (!fn.tabulated()) {
            return null;
        }
        List<String> names = logic.namesFor(fn);
        int row = 0;
        for (int v = 0; v < names.size(); v++) {
            for (int i = 0; i < ins.size(); i++) {
                if (ins.get(i).label.equals(names.get(v)) && ((combo >>> i) & 1) != 0) {
                    row |= 1 << v;
                    break;
                }
            }
        }
        return fn.table().get(row);
    }

    private static String id(String raw) {
        String s = raw.replaceAll("[^A-Za-z0-9_]", "_");
        return s.isEmpty() || Character.isDigit(s.charAt(0)) ? "n" + s : s;
    }
}
