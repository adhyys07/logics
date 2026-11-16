package com.redstonelogics.export;
import com.redstonelogics.analysis.BooleanAbstraction;
import com.redstonelogics.analysis.TruthTable;
import com.redstonelogics.core.BlockKind;
import com.redstonelogics.analysis.DeadLogic;
import com.redstonelogics.analysis.TickSimulator;
import com.redstonelogics.analysis.Waveform;
import com.redstonelogics.analysis.TimingAnalysis;
import com.redstonelogics.graph.Netlist;
import com.redstonelogics.graph.StrengthSets;
import com.redstonelogics.mapper.RedstoneLogicMapper;
import com.redstonelogics.recognize.GateType;
import com.redstonelogics.recognize.LogicDiagram;
import java.util.ArrayList;
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
            if (!fn.tabulated()) {
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
        sb.append('\n');
        // Timing and dead logic were previously reachable only from the in-game
        // export; a report that omits them is not a full analysis.
        sb.append(TimingAnalysis.of(mapped.netlist()).render()).append('\n');
        sb.append(DeadLogic.render(DeadLogic.of(mapped.netlist())));
        sb.append(System.lineSeparator()).append(analogSection(mapped.netlist()));
        sb.append(System.lineSeparator()).append(waveformSection(mapped.netlist()));
        return sb.toString();
    }
    /**
     * Elements carrying more than one non-zero strength: the parts a boolean
     * view cannot describe, such as comparator arithmetic and item sorters.
     */
    private static String analogSection(Netlist netlist) {
        StrengthSets sets = StrengthSets.of(netlist);
        List<Netlist.Element> analog = sets.analogElements();
        StringBuilder sb = new StringBuilder("## Signal strengths");
        sb.append(System.lineSeparator()).append(System.lineSeparator());
        if (analog.isEmpty()) {
            sb.append("Every component is simply on or off, so the boolean view above is complete.");
            sb.append(System.lineSeparator());
            return sb.toString();
        }
        sb.append("| Component | Position | Possible strengths |");
        sb.append(System.lineSeparator()).append("|---|---|---|").append(System.lineSeparator());
        for (Netlist.Element e : analog) {
            List<String> levels = new ArrayList<>();
            int mask = sets.setFor(e.id);
            for (int strength = 0; strength <= 15; strength++) {
                if ((mask & (1 << strength)) != 0) {
                    levels.add(String.valueOf(strength));
                }
            }
            sb.append("| ").append(e.label).append(" | `").append(e.pos).append("` | ")
              .append(String.join(", ", levels)).append(" |").append(System.lineSeparator());
        }
        return sb.toString();
    }
    /**
     * A tick-level trace, which is the only view that says anything useful
     * about a latch or a clock. Inputs are walked one at a time from all-off so
     * the trace shows a response rather than an arbitrary starting state.
     */
    private static String waveformSection(Netlist netlist) {
        StringBuilder sb = new StringBuilder("## Timeline");
        sb.append(System.lineSeparator()).append(System.lineSeparator());
        if (netlist.inputs().isEmpty()) {
            sb.append("No lever or button, so there is nothing to drive a trace with.");
            sb.append(System.lineSeparator());
            return sb.toString();
        }
        TickSimulator sim = new TickSimulator(netlist);
        List<Netlist.Element> ins = netlist.inputs();
        for (int i = 0; i < 3; i++) {
            sim.step();
        }
        if (ins.size() <= 4) {
            // Walk every input combination. Driving one input at a time never
            // overlaps them, so an AND would read as permanently low.
            for (int combo = 0; combo < (1 << ins.size()); combo++) {
                for (int i = 0; i < ins.size(); i++) {
                    sim.set(ins.get(i).label, ((combo >>> i) & 1) != 0);
                }
                for (int i = 0; i < 6; i++) {
                    sim.step();
                }
            }
        } else {
            for (Netlist.Element in : ins) {
                sim.set(in.label, true);
                for (int i = 0; i < 5; i++) {
                    sim.step();
                }
                sim.set(in.label, false);
                for (int i = 0; i < 5; i++) {
                    sim.step();
                }
            }
        }
        // Period detection needs the inputs held still: in the trace above they
        // are deliberately toggled, so a repeated input pattern would read as
        // an oscillation. A separate undriven run answers the question.
        TickSimulator quiet = new TickSimulator(netlist);
        int period = quiet.run(64) < 0 ? quiet.detectPeriod() : 0;
        sb.append("```").append(System.lineSeparator());
        sb.append(Waveform.ascii(netlist, sim.trace()));
        sb.append("```").append(System.lineSeparator());
        if (period > 0) {
            sb.append(System.lineSeparator())
              .append("Does not settle with the inputs held still; repeats every ")
              .append(period).append(" tick(s). That is either a clock, or a latch whose ")
              .append("starting state this model cannot pick: every element is evaluated ")
              .append("from the same snapshot each tick, so a symmetric cross-coupled pair ")
              .append("flips together instead of falling into one state.")
              .append(System.lineSeparator());
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
