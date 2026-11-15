package com.redstonelogics.export;

import com.redstonelogics.analysis.TimingAnalysis;
import com.redstonelogics.core.Json;
import com.redstonelogics.core.Pos;
import com.redstonelogics.mapper.RedstoneLogicMapper;
import com.redstonelogics.recognize.LogicDiagram;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DiagramJsonExporter {

    public static final String FORMAT = "redstonelogics.graph/1";

    private DiagramJsonExporter(){
    }

    /**
     * Gates on the slowest path. TimingAnalysis works in netlist elements;
     * gates carry the elements they absorbed, and gates synthesised by
     * GateRecognizer.emit carry none, so position is the fallback.
     */
    private static Set<Integer> criticalGates(RedstoneLogicMapper mapped) {
        Set<Integer> hot = new LinkedHashSet<>();
        for (int element : TimingAnalysis.of(mapped.netlist()).criticalPath()) {
            Pos at = mapped.netlist().element(element).pos;
            for (LogicDiagram.Gate g : mapped.diagram().gates()) {
                if (g.elements.contains(element) || g.origin.equals(at)) {
                    hot.add(g.id);
                }
            }
        }
        return hot;
    }

    public static String render(RedstoneLogicMapper mapped, String title) {
        LogicDiagram diagram = mapped.diagram();
        DiagramLayout layout = DiagramLayout.of(diagram);
        TimingAnalysis timing = TimingAnalysis.of(mapped.netlist());

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("format", FORMAT);
        root.put("title", title);
        root.put("summary", mapped.summary());
        root.put("width", layout.width());
        root.put("height", layout.height());

        List<Object> gates = new ArrayList<>();
        for (LogicDiagram.Gate g : diagram.gates()) {
            DiagramLayout.Node n = layout.node(g.id);
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", g.id);
            o.put("type", g.type.name());
            o.put("symbol", g.type.shortName);
            o.put("label", g.label);
            o.put("pos", List.of(g.origin.x(), g.origin.y(), g.origin.z()));
            o.put("terminal", g.type.isTerminal());
            o.put("negated", g.type.negatedOutput());
            if (g.delayTicks != 0) {
                o.put("delayTicks", g.delayTicks);
            }
            if (g.note != null && !g.note.isBlank()) {
                o.put("note", g.note);
            }
            if (!g.inputPins.isEmpty()) {
                o.put("inputPins", new ArrayList<Object>(g.inputPins));
            }
            if (g.outputCount() > 1) {
                o.put("outputPins", new ArrayList<Object>(g.outputPins));
            }
            o.put("box", List.of(n.x(), n.y(), n.width(), n.height()));
            o.put("layer", n.layer());
            gates.add(o);
        }
        root.put("gates", gates);

        List<Object> wires = new ArrayList<>();
        for (DiagramLayout.Wire w : layout.wires()) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("fromGate", w.from().gate());
            o.put("fromPin", w.from().pin());
            o.put("toGate", w.toGate());
            o.put("toPin", w.toPin());
            o.put("feedback", w.feedback());
            List<Object> pts = new ArrayList<>();
            for (double v : w.points()) {
                pts.add(v);
            }
            o.put("points", pts);
            wires.add(o);
        }
        root.put("wires", wires);

        Map<String, Object> time = new LinkedHashMap<>();
        time.put("criticalPathTicks", timing.criticalPathTicks());
        time.put("minClockGameTicks", timing.minimumClockPeriodGameTicks());
        time.put("warnings", new ArrayList<Object>(timing.warnings()));
        root.put("timing", time);
        root.put("criticalGates", new ArrayList<Object>(criticalGates(mapped)));

        root.put("patterns", new ArrayList<Object>(diagram.patterns()));
        root.put("notes", new ArrayList<Object>(diagram.notes()));
        
         Map<String, Object> tally = new LinkedHashMap<>();
        diagram.tally().forEach((k, v) -> tally.put(k.shortName, v));
        root.put("tally", tally);

        return Json.write(root);
    }
}
