package com.redstonelogics.export;

import com.redstonelogics.analysis.BooleanAbstraction;
import com.redstonelogics.analysis.TruthTable;
import com.redstonelogics.core.BlockKind;
import com.redstonelogics.graph.Netlist;
import com.redstonelogics.mapper.RedstoneLogicMapper;
import com.redstonelogics.recognize.GateType;
import com.redstonelogics.recognize.LogicDiagram;

import java.util.List;
import java.util.Map;

public final class NetlistReport {

    private NetlistReport() {
    }

    public static String render(RedstoneLogicMapper mapped, String title) {
        StringBuilder sb = new StringBuilder();
        Netlist netlist = mapped.netlist();
        LogicDiagram diagram = mapped.diagram();

        sb.append("# ").append(title).append("\n\n");
        sb.append("Region `").append(mapped.snapshot().min()).append("` to `")
          .append(mapped.snapshot().max()).append("` in ")
          .append(mapped.snapshot().world()).append(".\n\n");

        sb.append("## Blocks scanned\n\n| Kind | Count |\n|---|---|\n");
        for (Map.Entry<BlockKind, Integer> e : mapped.snapshot().inventory().entrySet()) {
            sb.append("| ").append(e.getKey()).append(" | ").append(e.getValue()).append(" |\n");
        }
        sb.append("\n").append(netlist.dustNets().size()).append(" dust net(s).\n\n");

        sb.append("## Components\n\n");
        sb.append("| Label | Component | Position | Inputs | Delay |\n|---|---|---|---|---|\n");
        for (Netlist.Element e : netlist.elements()) {
            sb.append("| ").append(e.label)
              .append(" | ").append(describe(e))
              .append(" | `").append(e.pos).append('`')
              .append(" | ").append(e.allInputs().isEmpty() ? "-" : e.allInputs().toString())
              .append(" | ").append(e.delay > 0 ? e.delay + "t" : "-")
              .append(" |\n");
        }

        sb.append("\n## Recognised patterns\n\n");
        if (diagram.patterns().isEmpty()) {
            sb.append("None. Every signal folded into a generic block.\n");
        } else {
            for (String line : diagram.patterns()) {
                sb.append("- ").append(line).append('\n');
            }
        }

        sb.append("\n## Gates\n\n| Gate | Type | From | Function |\n|---|---|---|---|\n");
        for (LogicDiagram.Gate g : diagram.gates()) {
            sb.append("| ").append(g.label)
              .append(" | ").append(g.type.shortName)
              .append(" | `").append(g.origin == null ? "-" : g.origin.toString()).append('`')
              .append(" | ").append(g.note == null ? "" : g.note.replace("|", "\\|"))
              .append(" |\n");
        }

        sb.append("\n## Output truth tables\n\n");
        BooleanAbstraction logic = mapped.logic();
        boolean any = false;
        for (Netlist.Element e : netlist.outputs()) {
            BooleanAbstraction.Fn fn = logic.function(e.id);
            if (!fn.known()) {
                sb.append("`").append(e.label).append("` could not be tabulated.\n\n");
                continue;
            }
            any = true;
            int[] support = fn.table().supportVars();
            TruthTable table = fn.table().collapse(support);
            List<String> names = logic.namesFor(fn);
            List<String> columns = new java.util.ArrayList<>();
            for (int s : support) {
                columns.add(names.get(s));
            }
            sb.append("### ").append(e.label).append(" = ")
              .append(table.toExpression(columns)).append("\n\n");
            if (support.length == 0 || support.length > 8) {
                sb.append("_Table omitted: ").append(support.length)
                  .append(" input(s)._\n\n");
                continue;
            }
            sb.append("| ").append(String.join(" | ", columns)).append(" | ")
              .append(e.label).append(" |\n|")
              .append("---|".repeat(columns.size() + 1)).append('\n');
            for (int row = 0; row < table.rows(); row++) {
                sb.append('|');
                for (int i = 0; i < columns.size(); i++) {
                    sb.append(' ').append((row >>> i) & 1).append(" |");
                }
                sb.append(' ').append(table.get(row) ? 1 : 0).append(" |\n");
            }
            sb.append('\n');
        }
        if (!any && netlist.outputs().isEmpty()) {
            sb.append("No declared outputs in the selection.\n\n");
        }

        if (!diagram.notes().isEmpty()) {
            sb.append("## Notes and warnings\n\n");
            for (String note : diagram.notes()) {
                sb.append("- ").append(note).append('\n');
            }
            sb.append('\n');
        }

        sb.append("## Gate tally\n\n");
        for (Map.Entry<GateType, Integer> e : diagram.tally().entrySet()) {
            sb.append("- ").append(e.getKey().shortName).append(": ").append(e.getValue()).append('\n');
        }
        return sb.toString();
    }

    private static String describe(Netlist.Element e) {
        String base = switch (e.kind) {
            case INPUT -> "input";
            case CONSTANT -> "constant";
            case INVERTER -> "torch (NOT)";
            case BUFFER -> "repeater (buffer)";
            case CMP_COMPARE -> "comparator, compare";
            case CMP_SUBTRACT -> "comparator, subtract";
            case OUTPUT -> "sink";
        };
        return e.locked ? base + ", locked" : base;
    }
}
