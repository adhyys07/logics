package com.redstonelogics.export;

import com.redstonelogics.recognize.GateType;
import com.redstonelogics.recognize.LogicDiagram;

public final class DotExporter {
    public static String dot(LogicDiagram diagram, String title) {
        StringBuilder sb = new StringBuilder();
        sb.append("digraph circuit {\n");
        sb.append("  rankdir=LR;\n");
        sb.append("  labelloc=\"b\";\n");
        sb.append("  label=").append(quote(title)).append(";\n");
        sb.append("  node [fontname=\"sans-serif\", fontsize=11];\n\n");

        for (LogicDiagram.Gate g : diagram.gates()) {
            sb.append("  g").append(g.id).append(" [")
              .append("shape=").append(shape(g.type))
              .append(", label=").append(quote(caption(g)))
              .append("];\n");
        }
        sb.append('\n');
        for (LogicDiagram.Gate g : diagram.gates()) {
            for (LogicDiagram.Ref r : g.inputs) {
                sb.append("  g").append(r.gate()).append(" -> g").append(g.id).append(";\n");
            }
        }
        sb.append("}\n");
        return sb.toString();
    }

    public static String mermaid(LogicDiagram diagram){
        StringBuilder sb = new StringBuilder("flowchart LR\n");
        for (LogicDiagram.Gate g : diagram.gates()) {
            String text = caption(g).replace("\n", " ");
            sb.append("    g").append(g.id);
            switch (g.type) {
                case INPUT, OUTPUT -> sb.append("([").append(text).append("])");
                case CONST_0, CONST_1 -> sb.append("[/").append(text).append("/]");
                default -> sb.append("[\"").append(text).append("\"]");
            }
            sb.append("\n");
        }
        for (LogicDiagram.Gate g : diagram.gates()) {
            for (LogicDiagram.Ref r : g.inputs) {
                sb.append("    g").append(r.gate()).append(" --> g").append(g.id).append('\n');
            }
        }
        return sb.toString();
    }

    private static String caption(LogicDiagram.Gate g){
        if (g.type == GateType.INPUT || g.type == GateType.OUTPUT) {
            return g.label;
        }
        String base = g.type.shortName + " " + g.label;
        return g.delayTicks != 0 ? base + " (" + g.delayTicks + "t)" : base;
    }

    private static String shape(GateType type) {
        return switch (type) {
            case INPUT, OUTPUT -> "box, style=rounded";
            case CONST_0, CONST_1 -> "parallelogram";
            case NOT -> "invtriangle";
            case SR_LATCH, FEEDBACK -> "box3d";
            case GENERIC -> "box";
            default -> "ellipse";
        };
    }

    private static String quote(String s) {
        return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private DotExporter() {
    }
}