package com.redstonelogics.client.gui;

import com.redstonelogics.client.ClientAnalysis;
import com.redstonelogics.client.WorldHighlight;
import com.redstonelogics.core.Pos;
import com.redstonelogics.export.DiagramLayout;
import com.redstonelogics.recognize.GateType;
import com.redstonelogics.recognize.LogicDiagram;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the logic diagram inside the screen, reusing the same layout engine the
 * SVG exporter uses.
 *
 * <p>Every wire segment {@link DiagramLayout} produces is axis aligned, so each
 * one renders as a one pixel {@code fill} and no line primitive is needed.
 */
public class DiagramTab implements CircuitScreen.Tab {

    private static final int WIRE = 0xFF6A6A80;
    private static final int GATE_FILL = 0xFF24242E;
    private static final int GATE_FILL_HOT = 0xFF2E2A33;

    private DiagramLayout layout;
    private LogicDiagram source;

    private double panX;
    private double panY;
    private double zoom = 0.45;
    private boolean framed;
    private int selected = -1;

    /** Content origin, captured each frame so clicks map back to layout space. */
    private int originX;
    private int originY;

    @Override
    public String title() {
        return "Diagram";
    }

    @Override
    public void render(GuiGraphicsExtractor g, int x, int y, int w, int h,
            int mouseX, int mouseY) {
        var font = Minecraft.getInstance().font;
        originX = x;
        originY = y;

        if (!ClientAnalysis.ready()) {
            g.text(font, ClientAnalysis.state() == ClientAnalysis.State.SCANNING
                    ? "Analysing..."
                    : "No circuit. Press Pick while looking at a build.",
                    x + 8, y + 8, CircuitScreen.DIM);
            return;
        }

        LogicDiagram diagram = ClientAnalysis.result().diagram();
        if (diagram != source) {
            source = diagram;
            layout = DiagramLayout.of(diagram);
            framed = false;
            selected = -1;
        }
        if (!framed && layout.width() > 0 && layout.height() > 0) {
            zoom = Math.max(0.1, Math.min(1.0,
                    Math.min(w / layout.width(), h / layout.height())));
            panX = (w - layout.width() * zoom) / 2;
            panY = (h - layout.height() * zoom) / 2;
            framed = true;
        }

        // Wires first so the gate boxes sit on top of them.
        for (DiagramLayout.Wire wire : layout.wires()) {
            double[] p = wire.points();
            boolean hot = wire.from().gate() == selected || wire.toGate() == selected;
            int colour = hot ? CircuitScreen.ACCENT : WIRE;
            for (int i = 0; i + 3 < p.length; i += 2) {
                segment(g, p[i], p[i + 1], p[i + 2], p[i + 3], colour);
            }
        }

        for (DiagramLayout.Node node : layout.nodes()) {
            if (node == null) {
                continue;
            }
            LogicDiagram.Gate gate = diagram.gate(node.gate());
            int left = sx(node.x());
            int top = sy(node.y());
            int right = sx(node.x() + node.width());
            int bottom = sy(node.y() + node.height());
            boolean hot = node.gate() == selected;

            g.fill(left, top, right, bottom, hot ? GATE_FILL_HOT : GATE_FILL);
            CircuitScreen.box(g, left, top, right - left, bottom - top,
                    hot ? CircuitScreen.ACCENT : colourFor(gate.type));

            String caption = gate.type.isTerminal() ? gate.label : gate.type.shortName;
            if (right - left > font.width(caption) + 4 && bottom - top > 10) {
                g.text(font, caption,
                        left + (right - left - font.width(caption)) / 2,
                        top + (bottom - top - 8) / 2,
                        CircuitScreen.TEXT);
            }
        }

        if (selected >= 0) {
            LogicDiagram.Gate gate = diagram.gate(selected);
            StringBuilder detail = new StringBuilder();
            detail.append(gate.type.shortName).append(' ').append(gate.label)
                  .append("  at ").append(gate.origin);
            if (gate.delayTicks != 0) {
                detail.append("  ").append(gate.delayTicks).append(" tick(s)");
            }
            if (gate.note != null) {
                detail.append("  -  ").append(gate.note);
            }
            g.fill(x, y, x + w, y + 14, 0xC0000000);
            g.text(font, detail.toString(), x + 6, y + 3, CircuitScreen.WARN);
        }

        g.fill(x, y + h - 14, x + w, y + h, 0xC0000000);
        g.text(font, "drag to pan, scroll to zoom, click a gate to highlight it in the world",
                x + 6, y + h - 11, CircuitScreen.DIM);
    }

    private static int colourFor(GateType type) {
        if (type.isTerminal()) {
            return 0xFF5FBF7F;
        }
        if (type.isSequential()) {
            return 0xFFB07FD0;
        }
        return type == GateType.GENERIC ? 0xFFE0B050 : 0xFF5A8AC0;
    }

    /** One axis-aligned wire segment, never thinner than a pixel. */
    private void segment(GuiGraphicsExtractor g, double x1, double y1, double x2, double y2,
            int colour) {
        int ax = sx(Math.min(x1, x2));
        int ay = sy(Math.min(y1, y2));
        int bx = sx(Math.max(x1, x2));
        int by = sy(Math.max(y1, y2));
        g.fill(ax, ay, Math.max(bx, ax + 1), Math.max(by, ay + 1), colour);
    }

    private int sx(double layoutX) {
        return originX + (int) Math.round(panX + layoutX * zoom);
    }

    private int sy(double layoutY) {
        return originY + (int) Math.round(panY + layoutY * zoom);
    }

    @Override
    public boolean click(double mouseX, double mouseY, int button) {
        if (layout == null) {
            return false;
        }
        double lx = (mouseX - originX - panX) / zoom;
        double ly = (mouseY - originY - panY) / zoom;

        for (DiagramLayout.Node node : layout.nodes()) {
            if (node == null) {
                continue;
            }
            if (lx >= node.x() && lx <= node.x() + node.width()
                    && ly >= node.y() && ly <= node.y() + node.height()) {
                selected = node.gate();
                highlight(node.gate());
                return true;
            }
        }
        selected = -1;
        WorldHighlight.clear();
        return true;
    }

    /** Pushes the real block positions behind this gate to the world overlay. */
    private void highlight(int gateId) {
        if (!ClientAnalysis.ready()) {
            return;
        }
        var mapped = ClientAnalysis.result();
        LogicDiagram.Gate gate = mapped.diagram().gate(gateId);
        List<Pos> blocks = new ArrayList<>();
        for (int element : gate.elements) {
            blocks.add(mapped.netlist().element(element).pos);
        }
        if (blocks.isEmpty()) {
            blocks.add(gate.origin);
        }
        WorldHighlight.set(blocks, gate.type.shortName + " " + gate.label);
    }

    @Override
    public boolean drag(double mouseX, double mouseY, double dragX, double dragY) {
        panX += dragX;
        panY += dragY;
        return true;
    }

    @Override
    public boolean scroll(double mouseX, double mouseY, double amount) {
        if (layout == null) {
            return false;
        }
        // Keep the layout point under the cursor fixed while zooming.
        double lx = (mouseX - originX - panX) / zoom;
        double ly = (mouseY - originY - panY) / zoom;
        zoom = Math.max(0.1, Math.min(3.0, zoom * (amount > 0 ? 1.1 : 1 / 1.1)));
        panX = mouseX - originX - lx * zoom;
        panY = mouseY - originY - ly * zoom;
        return true;
    }
}
