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

    public static void paint(Graphics2D g2, DiagramLayout layout, Palette palette,
                             String caption) {
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

        g2.setStroke(new BasicStroke(1.4f));
        g2.setColor(palette.wire());
        for (DiagramLayout.Wire wire : layout.wires()) {
            double[] p = wire.points();
            Path2D.Double path = new Path2D.Double();
            path.moveTo(p[0], p[1]);
            for (int i = 2; i < p.length; i += 2) {
                path.lineTo(p[i], p[i + 1]);
            }
            g2.draw(path);
        }

        for (LogicDiagram.Gate gate : diagram.gates()) {
            DiagramLayout.Node n = layout.node(gate.id);
            double x = n.x();
            double y = n.y();
            double w = n.width();
            double h = n.height();

            g2.setColor(palette.wire());
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
            g2.setColor(palette.ink());
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
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
                g2.setColor(palette.ink());
                g2.draw(bubble);
            }

            String inside = gate.type == GateType.DELAY
                    ? "\u0394 " + gate.delayTicks
                    : gate.type.shortName;
            boolean roomInside = gate.type == GateType.SR_LATCH || gate.type == GateType.GENERIC
                    || gate.type == GateType.FEEDBACK || gate.type == GateType.INHIBIT;
            if (roomInside) {
                g2.setFont(label);
                g2.setColor(palette.ink());
                centre(g2, inside, x + w / 2, y + h / 2 + 4);
            } else {
                g2.setFont(small);
                g2.setColor(palette.muted());
                centre(g2, inside, x + w / 2, y - 5);
            }
            g2.setFont(small);
            g2.setColor(palette.muted());
            centre(g2, gate.label, x + w / 2, y + h + 12);
        }

        if (caption != null) {
            g2.setFont(captionFont);
            g2.setColor(palette.muted());
            g2.drawString(caption, (float) DiagramLayout.MARGIN,
                    (float) (layout.height() + 32));
        }
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
