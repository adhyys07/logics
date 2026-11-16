package com.redstonelogics.export;

import com.redstonelogics.recognize.GateType;
import com.redstonelogics.recognize.LogicDiagram;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

public final class DiagramPainter {

    public record Palette(Color background, Color ink, Color fill, Color wire,
                          Color accent, Color muted) {

        public static final Palette LIGHT = new Palette(
                new Color(0xFFFFFF), new Color(0x1B1D21), new Color(0xF7F8F9),
                new Color(0x5B6169), new Color(0x2F6F4F), new Color(0x8B9199));

        public static final Palette DARK = new Palette(
                new Color(0x16181C), new Color(0xE8EAED), new Color(0x21242A),
                new Color(0x9AA2AB), new Color(0x7FD0A3), new Color(0x787F87));
    }

    private DiagramPainter() {
    }

    /** Kept so existing callers compile; no critical-path highlighting. */
    public static void paint(Graphics2D g2, DiagramLayout layout, Palette palette,
                             String caption) {
        paint(g2, layout, palette, caption, java.util.Set.of());
    }

    /**
     * @param hotGates gates on the critical path, outlined in the accent colour
     *                 so the PNG says the same thing the SVG does
     */
    public static void paint(Graphics2D g2, DiagramLayout layout, Palette palette,
                             String caption, java.util.Set<Integer> hotGates) {
        LogicDiagram diagram = layout.diagram();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g2.setColor(palette.background());
        g2.fillRect(0, 0, (int) Math.ceil(layout.width()),
                (int) Math.ceil(layout.height()) + 46);

        Font label = new Font(Font.SANS_SERIF, Font.BOLD, 12);
        Font small = new Font(Font.SANS_SERIF, Font.PLAIN, 9);
        Font captionFont = new Font(Font.SANS_SERIF, Font.PLAIN, 11);

        BasicStroke solid = new BasicStroke(1.4f);
        BasicStroke dashed = new BasicStroke(1.2f, BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_MITER, 10f, new float[] { 4f, 3f }, 0f);

        for (DiagramLayout.Wire wire : layout.wires()) {
            double[] p = wire.points();
            Path2D.Double path = new Path2D.Double();
            path.moveTo(p[0], p[1]);
            for (int i = 2; i < p.length; i += 2) {
                path.lineTo(p[i], p[i + 1]);
            }
            // A backwards edge is what makes a latch a latch, so it is drawn
            // differently rather than left looking like an ordinary wire.
            g2.setStroke(wire.feedback() ? dashed : solid);
            g2.setColor(wire.feedback() ? palette.accent() : palette.wire());
            g2.draw(path);
        }

        // A dot wherever one output feeds more than one input, so a crossing
        // can be told apart from a join.
        g2.setStroke(solid);
        g2.setColor(palette.wire());
        java.util.Map<LogicDiagram.Ref, Integer> fan = new java.util.HashMap<>();
        for (DiagramLayout.Wire wire : layout.wires()) {
            fan.merge(wire.from(), 1, Integer::sum);
        }
        for (java.util.Map.Entry<LogicDiagram.Ref, Integer> e : fan.entrySet()) {
            if (e.getValue() < 2) {
                continue;
            }
            double[] p = layout.outputPin(e.getKey().gate(), e.getKey().pin());
            g2.fill(new Ellipse2D.Double(p[0] - 2.4, p[1] - 2.4, 4.8, 4.8));
        }

        for (LogicDiagram.Gate gate : diagram.gates()) {
            DiagramLayout.Node n = layout.node(gate.id);
            double x = n.x();
            double y = n.y();
            double w = n.width();
            double h = n.height();
            boolean hot = hotGates.contains(gate.id);

            g2.setColor(palette.wire());
            g2.setStroke(solid);
            for (int i = 0; i < gate.inputs.size(); i++) {
                double[] p = layout.inputPin(gate.id, i);
                g2.draw(new java.awt.geom.Line2D.Double(p[0], p[1], x, p[1]));
            }
            for (int i = 0; i < gate.outputCount(); i++) {
                double[] p = layout.outputPin(gate.id, i);
                double bubble = gate.type.negatedOutput() ? 10 : 0;
                g2.draw(new java.awt.geom.Line2D.Double(x + w + bubble, p[1], p[0], p[1]));
            }

            if (gate.type.isTerminal()) {
                g2.setColor(palette.accent());
                g2.setStroke(new BasicStroke(1.6f));
                g2.draw(new RoundRectangle2D.Double(x, y + h / 2 - 15, w, 30, 14, 14));
                g2.setColor(palette.ink());
                g2.setFont(label);
                centre(g2, gate.label, x + w / 2, y + h / 2 + 4);
                continue;
            }

            Path2D.Double body = switch (gate.type.body()) {
                case AND -> andShape(x, y, w, h);
                case OR -> orShape(x, y, w, h);
                case XOR -> orShape(x + 7, y, w - 7, h);
                case NOT, BUFFER, DELAY -> triangleShape(x, y, w, h);
                default -> boxShape(x, y, w, h);
            };

            g2.setColor(palette.fill());
            g2.fill(body);
            g2.setColor(hot ? palette.accent() : palette.ink());
            g2.setStroke(new BasicStroke(hot ? 2.6f : 1.6f,
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(body);

            if (gate.type.body() == GateType.XOR) {
                Path2D.Double arc = new Path2D.Double();
                arc.moveTo(x, y);
                arc.quadTo(x + w * 0.22, y + h / 2, x, y + h);
                g2.draw(arc);
            }
            if (gate.type.negatedOutput()) {
                Ellipse2D.Double bubble = new Ellipse2D.Double(x + w, y + h / 2 - 5, 10, 10);
                g2.setColor(palette.background());
                g2.fill(bubble);
                g2.setColor(hot ? palette.accent() : palette.ink());
                g2.draw(bubble);
            }

            String inside = switch (gate.type) {
                case DELAY -> "\u0394 " + gate.delayTicks;
                case MACRO -> gate.macroName == null ? "MACRO" : gate.macroName;
                default -> gate.type.shortName;
            };
            boolean roomInside = gate.type == GateType.SR_LATCH || gate.type == GateType.GENERIC
                    || gate.type == GateType.FEEDBACK || gate.type == GateType.INHIBIT
                    || gate.type == GateType.MACRO || gate.type == GateType.EDGE;
            if (roomInside) {
                g2.setFont(label);
                g2.setColor(palette.ink());
                centre(g2, fit(g2, inside, w - 6), x + w / 2, y + h / 2 + 4);
            } else {
                g2.setFont(small);
                g2.setColor(palette.muted());
                centre(g2, inside, x + w / 2, y - 5);
            }
            g2.setFont(small);
            g2.setColor(palette.muted());
            centre(g2, gate.label, x + w / 2, y + h + 12);

            // Delay badge, as the SVG has had since the timing work landed.
            double below = y + h + 22;
            if (gate.delayTicks > 0 && gate.type != GateType.DELAY) {
                g2.setColor(palette.accent());
                centre(g2, "+" + gate.delayTicks + "t", x + w / 2, below);
                below += 10;
            }
            // The minimised expression, for gates whose symbol does not say it.
            if (gate.note != null
                    && (gate.type == GateType.GENERIC || gate.type == GateType.MACRO)) {
                g2.setColor(palette.muted());
                centre(g2, fit(g2, gate.note, DiagramLayout.GATE_WIDTH + 70),
                        x + w / 2, below);
            }
        }

        if (caption != null) {
            g2.setFont(captionFont);
            g2.setColor(palette.muted());
            g2.drawString(caption, (float) DiagramLayout.MARGIN,
                    (float) (layout.height() + 32));
        }
    }

    /** Truncates to fit, because a long expression would overlap its neighbour. */
    private static String fit(Graphics2D g2, String text, double maxWidth) {
        if (g2.getFontMetrics().stringWidth(text) <= maxWidth) {
            return text;
        }
        String out = text;
        while (out.length() > 1
                && g2.getFontMetrics().stringWidth(out + "\u2026") > maxWidth) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "\u2026";
    }

    private static void centre(Graphics2D g2, String text, double cx, double baseline) {
        int w = g2.getFontMetrics().stringWidth(text);
        g2.drawString(text, (float) (cx - w / 2.0), (float) baseline);
    }

    private static Path2D.Double andShape(double x, double y, double w, double h) {
        Path2D.Double p = new Path2D.Double();
        p.moveTo(x, y);
        p.lineTo(x + w / 2, y);
        p.curveTo(x + w, y, x + w, y + h, x + w / 2, y + h);
        p.lineTo(x, y + h);
        p.closePath();
        return p;
    }

    private static Path2D.Double orShape(double x, double y, double w, double h) {
        Path2D.Double p = new Path2D.Double();
        p.moveTo(x, y);
        p.quadTo(x + w * 0.6, y, x + w, y + h / 2);
        p.quadTo(x + w * 0.6, y + h, x, y + h);
        p.quadTo(x + w * 0.3, y + h / 2, x, y);
        p.closePath();
        return p;
    }

    private static Path2D.Double triangleShape(double x, double y, double w, double h) {
        Path2D.Double p = new Path2D.Double();
        p.moveTo(x, y);
        p.lineTo(x + w, y + h / 2);
        p.lineTo(x, y + h);
        p.closePath();
        return p;
    }

    private static Path2D.Double boxShape(double x, double y, double w, double h) {
        Path2D.Double p = new Path2D.Double();
        p.append(new RoundRectangle2D.Double(x, y, w, h, 6, 6), false);
        return p;
    }
}
