package com.redstonelogics.client.gui;

import com.redstonelogics.analysis.BooleanAbstraction;
import com.redstonelogics.analysis.TimingAnalysis;
import com.redstonelogics.client.ClientAnalysis;
import com.redstonelogics.graph.Netlist;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Truth table on the left, timing and warnings on the right. */
public class TablesTab implements CircuitScreen.Tab {

    private static final int ROW = 11;
    /** Above this many inputs the table has more rows than anyone can read. */
    private static final int MAX_TABULATED = 12;

    private int scroll;
    private int maxScroll;

    @Override
    public String title() {
        return "Tables";
    }

    @Override
    public void render(GuiGraphicsExtractor g, int x, int y, int w, int h,
            int mouseX, int mouseY) {
        var font = Minecraft.getInstance().font;
        if (!ClientAnalysis.ready()) {
            g.text(font, "No circuit analysed yet.", x + 8, y + 8, CircuitScreen.DIM);
            return;
        }

        var mapped = ClientAnalysis.result();
        Netlist netlist = mapped.netlist();
        BooleanAbstraction logic = mapped.logic();
        List<Netlist.Element> ins = netlist.inputs();
        List<Netlist.Element> outs = netlist.outputs();

        int half = w / 2;
        renderTable(g, x, y, half - 8, h, ins, outs, logic);
        renderTiming(g, x + half, y, half - 8, h, netlist);
    }

    private void renderTable(GuiGraphicsExtractor g, int x, int y, int w, int h,
            List<Netlist.Element> ins, List<Netlist.Element> outs, BooleanAbstraction logic) {
        var font = Minecraft.getInstance().font;

        StringBuilder header = new StringBuilder();
        for (Netlist.Element e : ins) {
            header.append(e.label).append(' ');
        }
        header.append("| ");
        for (Netlist.Element e : outs) {
            header.append(e.label).append(' ');
        }
        g.text(font, header.toString(), x + 8, y + 8, CircuitScreen.WARN);

        if (ins.size() > MAX_TABULATED) {
            g.text(font, ins.size() + " inputs is " + (1L << ins.size()) + " rows;",
                    x + 8, y + 22, CircuitScreen.DIM);
            g.text(font, "export the CSV instead.", x + 8, y + 33, CircuitScreen.DIM);
            maxScroll = 0;
            return;
        }

        int rows = 1 << ins.size();
        int visible = Math.max(1, (h - 34) / ROW);
        maxScroll = Math.max(0, rows - visible);
        scroll = Math.min(scroll, maxScroll);

        int line = y + 22;
        for (int r = scroll; r < rows && (r - scroll) < visible; r++) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < ins.size(); i++) {
                sb.append((r >>> i) & 1).append(' ');
            }
            sb.append("| ");
            for (Netlist.Element out : outs) {
                Boolean value = Evaluate.output(logic, out, ins, r);
                sb.append(value == null ? "x" : (value ? "1" : "0")).append(' ');
            }
            g.text(font, sb.toString(), x + 8, line, CircuitScreen.TEXT);
            line += ROW;
        }

        if (maxScroll > 0) {
            g.text(font, "rows " + (scroll + 1) + "-"
                    + Math.min(rows, scroll + visible) + " of " + rows + ", scroll for more",
                    x + 8, y + h - 11, CircuitScreen.DIM);
        }
    }

    private void renderTiming(GuiGraphicsExtractor g, int x, int y, int w, int h,
            Netlist netlist) {
        var font = Minecraft.getInstance().font;
        TimingAnalysis timing = TimingAnalysis.of(netlist);

        g.text(font, "Timing", x, y + 8, CircuitScreen.TEXT);
        g.text(font, "critical path: " + timing.criticalPathTicks() + " redstone ticks",
                x, y + 22, CircuitScreen.OK);
        g.text(font, "min clock period: " + timing.minimumClockPeriodGameTicks()
                + " game ticks", x, y + 33, CircuitScreen.DIM);

        List<String> problems = new ArrayList<>(timing.warnings());
        problems.addAll(netlist.warnings());

        int wy = y + 50;
        if (problems.isEmpty()) {
            g.text(font, "no input skew, no warnings", x, wy, CircuitScreen.DIM);
            return;
        }

        g.text(font, "Warnings (" + problems.size() + ")", x, wy, CircuitScreen.WARN);
        wy += 13;
        for (String note : problems) {
            if (wy > y + h - 12) {
                g.text(font, "...", x, wy, CircuitScreen.DIM);
                break;
            }
            for (var part : font.split(Component.literal("- " + note), w)) {
                if (wy > y + h - 12) {
                    break;
                }
                g.text(font, part, x, wy, CircuitScreen.ACCENT);
                wy += 10;
            }
        }
    }

    @Override
    public boolean scroll(double mouseX, double mouseY, double amount) {
        if (maxScroll == 0) {
            return false;
        }
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(amount) * 3));
        return true;
    }
}
