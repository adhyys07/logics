package com.redstonelogics.client.gui;

import com.redstonelogics.analysis.BooleanAbstraction;
import com.redstonelogics.client.ClientAnalysis;
import com.redstonelogics.graph.Netlist;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * Toggle the inputs, read the outputs.
 *
 * <p>Combinational only. A latch has no value without a history, so outputs the
 * abstraction could not resolve are reported as stateful rather than guessed.
 */
public class SimulatorTab implements CircuitScreen.Tab {

    private static final int ROW = 14;
    private static final int TOGGLE_W = 28;

    private int assignment;
    private final List<int[]> hitboxes = new ArrayList<>();

    @Override
    public String title() {
        return "Simulate";
    }

    @Override
    public void render(GuiGraphicsExtractor g, int x, int y, int w, int h,
            int mouseX, int mouseY) {
        var font = Minecraft.getInstance().font;
        hitboxes.clear();

        if (!ClientAnalysis.ready()) {
            g.text(font, "No circuit analysed yet.", x + 8, y + 8, CircuitScreen.DIM);
            return;
        }

        var mapped = ClientAnalysis.result();
        Netlist netlist = mapped.netlist();
        BooleanAbstraction logic = mapped.logic();
        List<Netlist.Element> ins = netlist.inputs();
        List<Netlist.Element> outs = netlist.outputs();

        if (ins.size() > 31) {
            g.text(font, ins.size() + " inputs is more than this view can toggle.",
                    x + 8, y + 8, CircuitScreen.WARN);
            return;
        }

        int half = w / 2;

        // ----- inputs
        g.text(font, "Inputs - click to toggle", x + 8, y + 8, CircuitScreen.TEXT);
        int row = y + 24;
        for (int i = 0; i < ins.size(); i++) {
            Netlist.Element e = ins.get(i);
            boolean on = ((assignment >>> i) & 1) != 0;
            int bx = x + 8;

            g.fill(bx, row, bx + TOGGLE_W, row + ROW - 2, on ? 0xFF2F6F43 : 0xFF3A2020);
            CircuitScreen.box(g, bx, row, TOGGLE_W, ROW - 2,
                    on ? CircuitScreen.OK : CircuitScreen.BORDER);
            g.text(font, on ? "ON" : "OFF", bx + 5, row + 2,
                    on ? CircuitScreen.OK : CircuitScreen.DIM);
            g.text(font, e.label + "  " + e.pos, bx + TOGGLE_W + 8, row + 2,
                    CircuitScreen.TEXT);

            hitboxes.add(new int[] { bx, row, bx + TOGGLE_W, row + ROW - 2, i });
            row += ROW;
        }
        if (ins.isEmpty()) {
            g.text(font, "none - this build has no levers or buttons",
                    x + 8, row + 2, CircuitScreen.DIM);
        }

        // ----- outputs
        g.text(font, "Outputs", x + half, y + 8, CircuitScreen.TEXT);
        int outRow = y + 24;
        for (Netlist.Element out : outs) {
            Boolean value = Evaluate.output(logic, out, ins, assignment);
            String shown = value == null ? "stateful" : (value ? "1" : "0");
            int colour = value == null
                    ? CircuitScreen.WARN
                    : (value ? CircuitScreen.OK : CircuitScreen.DIM);

            g.text(font, out.label, x + half, outRow + 2, CircuitScreen.TEXT);
            g.text(font, shown, x + w - 60, outRow + 2, colour);
            outRow += ROW;
        }
        if (outs.isEmpty()) {
            g.text(font, "none - no lamps, pistons or other sinks",
                    x + half, outRow + 2, CircuitScreen.DIM);
        }

        g.fill(x, y + h - 14, x + w, y + h, 0xC0000000);
        g.text(font, "latches read as stateful: their value depends on history, "
                + "which this view does not track", x + 6, y + h - 11, CircuitScreen.DIM);
    }

    @Override
    public boolean click(double mouseX, double mouseY, int button) {
        for (int[] box : hitboxes) {
            if (mouseX >= box[0] && mouseX <= box[2] && mouseY >= box[1] && mouseY <= box[3]) {
                assignment ^= 1 << box[4];
                return true;
            }
        }
        return false;
    }
}
