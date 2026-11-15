package com.redstonelogics.export;

import com.redstonelogics.recognize.GateType;
import com.redstonelogics.recognize.LogicDiagram;

import java.util.Locale;

public final class LogisimExporter {
    private static String getName(GateType type) {
        return switch (type) {
            case AND -> "AND Gate";
            case OR -> "OR Gate";
            case NAND -> "NAND Gate";
            case NOR -> "NOR Gate";
            case XOR -> "XOR Gate";
            case XNOR -> "XNOR Gate";
            case NOT -> "NOT Gate";
            case BUFFER, DELAY -> "Buffer";
            case SR_LATCH -> "S-R Flip-Flop";
            case INPUT -> "Pin";
            case OUTPUT -> "Pin";
            case CONST_0, CONST_1 -> "Constant";
            default -> "AND Gate";
        };
    }

    /** Logisim's own component names, per library. */
    private static String gateName(GateType type) {
        return switch (type) {
            case AND -> "AND Gate";
            case OR -> "OR Gate";
            case NAND -> "NAND Gate";
            case NOR -> "NOR Gate";
            case XOR -> "XOR Gate";
            case XNOR -> "XNOR Gate";
            case NOT -> "NOT Gate";
            case BUFFER, DELAY -> "Buffer";
            case SR_LATCH -> "S-R Flip-Flop";
            case INPUT, OUTPUT -> "Pin";
            case CONST_0, CONST_1 -> "Constant";
            // INHIBIT, GENERIC and FEEDBACK have no Logisim equivalent; an AND
            // keeps the pin count right so the sheet still opens.
            default -> "AND Gate";
        };
    }

    public static String render(LogicDiagram diagram, String title) {
        DiagramLayout layout = DiagramLayout.of(diagram);
        StringBuilder sb = new StringBuilder();

        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n");
        sb.append("<project source=\"3.8.0\" version=\"1.0\">\n");
        sb.append("  <lib desc=\"#Wiring\" name=\"0\"/>\n");
        sb.append("  <lib desc=\"#Gates\" name=\"1\"/>\n");
        sb.append("  <lib desc=\"#Memory\" name=\"4\"/>\n");
        sb.append("  <main name=\"main\"/>\n");
        sb.append("  <circuit name=\"main\">\n");
        sb.append("    <a name=\"circuit\" val=\"main\"/>\n");
        sb.append("    <a name=\"appearance\" val=\"logisim_evolution\"/>\n");
        sb.append("    <comment>").append(escape(title)).append("</comment>\n");
    
        for (LogicDiagram.Gate g : diagram.gates()) {
            DiagramLayout.Node n = layout.node(g.id);
            int x = snap(n.x());
            int y = snap(n.centreY());
            String lib = switch (g.type) {
                case INPUT, OUTPUT, CONST_0, CONST_1 -> "0";
                case SR_LATCH -> "4";
                default -> "1";
            };

            sb.append(String.format(Locale.ROOT,
                    "    <comp lib=\"%s\" loc=\"(%d,%d)\" name=\"%s\">%n",
                    lib, x, y, escape(gateName(g.type))));
            sb.append("      <a name=\"label\" val=\"")
              .append(escape(g.label)).append("\"/>\n");
            if (g.type == GateType.INPUT) {
                sb.append("      <a name=\"output\" val=\"false\"/>\n");
            } else if (g.type == GateType.OUTPUT) {
                sb.append("      <a name=\"output\" val=\"true\"/>\n");
            } else if (g.type == GateType.CONST_0 || g.type == GateType.CONST_1) {
                sb.append("      <a name=\"value\" val=\"0x")
                  .append(g.type == GateType.CONST_1 ? 1 : 0).append("\"/>\n");
            } else if (!g.inputs.isEmpty()) {
                sb.append("      <a name=\"inputs\" val=\"")
                  .append(Math.max(2, g.inputs.size())).append("\"/>\n");
            }
            sb.append("    </comp>\n");
        }

        sb.append("  </circuit>\n");
        sb.append("</project>\n");
        return sb.toString();
    }

    private static int snap(double v) {
        return (int) (Math.round(v / 10.0) * 10);
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}