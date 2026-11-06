package com.redstonelogics.export;

import com.redstonelogics.recognize.GateType;
import com.redstonelogics.recognize.LogicDiagram;

import java.util.Locale;

public final class SvgExporter {

    private SvgExporter() {
    }

    public static String render(LogicDiagram diagram, String title) {
        DiagramLayout layout = DiagramLayout.of(diagram);
        StringBuilder sb = new StringBuilder();
        double w = Math.max(layout.width(), 320);
        double h = Math.max(layout.height(), 160) + 46;

        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append(String.format(Locale.ROOT,
                "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 %.0f %.0f\" "
                        + "width=\"%.0f\" height=\"%.0f\" font-family=\"ui-sans-serif, "
                        + "system-ui, sans-serif\">%n", w, h, w, h));
        sb.append("""
                  <style>
                    :root { --ink:#1b1d21; --bg:#ffffff; --wire:#5b6169; --accent:#2f6f4f;
                            --muted:#8b9199; --fill:#f7f8f9; }
                    @media (prefers-color-scheme: dark) {
                      :root { --ink:#e8eaed; --bg:#16181c; --wire:#9aa2ab; --accent:#7fd0a3;
                              --muted:#787f87; --fill:#21242a; }
                    }
                    .bg   { fill: var(--bg); }
                    .body { fill: var(--fill); stroke: var(--ink); stroke-width: 1.6;
                            stroke-linejoin: round; }
                    .term { fill: none; stroke: var(--accent); stroke-width: 1.6; }
                    .wire { fill: none; stroke: var(--wire); stroke-width: 1.4; }
                    .stub { stroke: var(--wire); stroke-width: 1.4; }
                    .bub  { fill: var(--bg); stroke: var(--ink); stroke-width: 1.6; }
                    .lbl  { fill: var(--ink); font-size: 12px; font-weight: 600; }
                    .pin  { fill: var(--muted); font-size: 9px; }
                    .cap  { fill: var(--muted); font-size: 11px; }
                  </style>
                """);
        sb.append(String.format(Locale.ROOT,
                "  <rect class=\"bg\" x=\"0\" y=\"0\" width=\"%.0f\" height=\"%.0f\"/>%n", w, h));
        sb.append(String.format(Locale.ROOT,
                "  <text class=\"cap\" x=\"%.1f\" y=\"%.1f\">%s</text>%n",
                DiagramLayout.MARGIN, h - 14, escape(title)));

        for (DiagramLayout.Wire wire : layout.wires()) {
            sb.append("  <polyline class=\"wire\" points=\"");
            double[] p = wire.points();
            for (int i = 0; i < p.length; i += 2) {
                if (i > 0) {
                    sb.append(' ');
                }
                sb.append(String.format(Locale.ROOT, "%.1f,%.1f", p[i], p[i + 1]));
            }
            sb.append("\"/>\n");
        }

        for (LogicDiagram.Gate g : diagram.gates()) {
            drawGate(sb, layout, g);
        }
        sb.append("</svg>\n");
        return sb.toString();
    }

    private static void drawGate(StringBuilder sb, DiagramLayout layout, LogicDiagram.Gate g) {
        DiagramLayout.Node n = layout.node(g.id);
        double x = n.x();
        double y = n.y();
        double w = n.width();
        double h = n.height();

        for (int i = 0; i < g.inputs.size(); i++) {
            double[] p = layout.inputPin(g.id, i);
            sb.append(String.format(Locale.ROOT,
                    "  <line class=\"stub\" x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\"/>%n",
                    p[0], p[1], x, p[1]));
            if (!g.inputPins.get(i).isEmpty()) {
                sb.append(String.format(Locale.ROOT,
                        "  <text class=\"pin\" x=\"%.1f\" y=\"%.1f\">%s</text>%n",
                        x + 4, p[1] + 3, escape(g.inputPins.get(i))));
            }
        }
        for (int i = 0; i < g.outputCount(); i++) {
            double[] p = layout.outputPin(g.id, i);
            double bubble = g.type.negatedOutput() ? 10 : 0;
            sb.append(String.format(Locale.ROOT,
                    "  <line class=\"stub\" x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\"/>%n",
                    x + w + bubble, p[1], p[0], p[1]));
            if (g.outputCount() > 1) {
                sb.append(String.format(Locale.ROOT,
                        "  <text class=\"pin\" x=\"%.1f\" y=\"%.1f\">%s</text>%n",
                        x + w - 20, p[1] + 3, escape(g.outputPins.get(i))));
            }
        }

        switch (g.type) {
            case INPUT, OUTPUT, CONST_0, CONST_1 -> {
                sb.append(String.format(Locale.ROOT,
                        "  <rect class=\"term\" x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"%.1f\""
                                + " rx=\"10\"/>%n", x, y + h / 2 - 15, w, 30));
                text(sb, "lbl", x + w / 2, y + h / 2 + 4, g.label, "middle");
                return;
            }
            case AND, NAND -> sb.append(String.format(Locale.ROOT,
                    "  <path class=\"body\" d=\"M%.1f %.1f H%.1f A%.1f %.1f 0 0 1 %.1f %.1f "
                            + "H%.1f Z\"/>%n",
                    x, y, x + w / 2, h / 2, h / 2, x + w / 2, y + h, x));
            case OR, NOR -> sb.append(orPath(x, y, w, h));
            case XOR, XNOR -> {
                sb.append(orPath(x + 7, y, w - 7, h));
                sb.append(String.format(Locale.ROOT,
                        "  <path class=\"body\" fill=\"none\" d=\"M%.1f %.1f Q%.1f %.1f %.1f %.1f\"/>%n",
                        x, y, x + w * 0.22, y + h / 2, x, y + h));
            }
            case NOT, BUFFER, DELAY -> sb.append(String.format(Locale.ROOT,
                    "  <path class=\"body\" d=\"M%.1f %.1f L%.1f %.1f L%.1f %.1f Z\"/>%n",
                    x, y, x + w, y + h / 2, x, y + h));
            default -> sb.append(String.format(Locale.ROOT,
                    "  <rect class=\"body\" x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"%.1f\""
                            + " rx=\"4\"/>%n", x, y, w, h));
        }

        if (g.type.negatedOutput()) {
            double cx = g.type == GateType.NOT ? x + w + 5 : x + w + 5;
            sb.append(String.format(Locale.ROOT,
                    "  <circle class=\"bub\" cx=\"%.1f\" cy=\"%.1f\" r=\"5\"/>%n",
                    cx, y + h / 2));
        }

        String caption = switch (g.type) {
            case SR_LATCH -> "SR";
            case DELAY -> "\u0394 " + g.delayTicks;
            case GENERIC, FEEDBACK -> g.type.shortName;
            default -> g.type.shortName;
        };
        boolean roomInside = g.type == GateType.SR_LATCH || g.type == GateType.GENERIC
                || g.type == GateType.FEEDBACK || g.type == GateType.INHIBIT;
        if (roomInside) {
            text(sb, "lbl", x + w / 2, y + h / 2 + 4, caption, "middle");
        } else {
            text(sb, "pin", x + w / 2 - 4, y - 5, caption, "middle");
        }
        text(sb, "pin", x + w / 2, y + h + 12, g.label, "middle");
    }

    private static String orPath(double x, double y, double w, double h) {
        return String.format(Locale.ROOT,
                "  <path class=\"body\" d=\"M%.1f %.1f Q%.1f %.1f %.1f %.1f Q%.1f %.1f %.1f %.1f "
                        + "Q%.1f %.1f %.1f %.1f Z\"/>%n",
                x, y,
                x + w * 0.6, y, x + w, y + h / 2,
                x + w * 0.6, y + h, x, y + h,
                x + w * 0.3, y + h / 2, x, y);
    }

    private static void text(StringBuilder sb, String cls, double x, double y,
                             String body, String anchor) {
        sb.append(String.format(Locale.ROOT,
                "  <text class=\"%s\" x=\"%.1f\" y=\"%.1f\" text-anchor=\"%s\">%s</text>%n",
                cls, x, y, anchor, escape(body)));
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
