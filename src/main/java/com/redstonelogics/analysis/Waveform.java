package com.redstonelogics.analysis;

import com.redstonelogics.graph.Netlist;

import java.util.ArrayList;
import java.util.List;

public final class Waveform {

    private Waveform() {
    }

    public static String ascii(Netlist netlist, List<TickSimulator.Frame> trace) {
        if (trace.isEmpty()) {
            return "No trace recorded.\n";
        }
        List<Netlist.Element> shown = new ArrayList<>();
        shown.addAll(netlist.inputs());
        for (Netlist.Element e : netlist.elements()) {
            if (e.kind == Netlist.Kind.INVERTER || e.kind == Netlist.Kind.BUFFER) {
                shown.add(e);
            }
        }
        shown.addAll(netlist.outputs());

        int width = 0;
        for (Netlist.Element e : shown) {
            width = Math.max(width, e.label.length());
        }

        StringBuilder sb = new StringBuilder();
        sb.append(" ".repeat(width + 2)).append("tick 0");
        sb.append(" ".repeat(Math.max(1, trace.size() - 6)));
        sb.append(trace.get(trace.size() - 1).tick()).append('\n');

        for (Netlist.Element e : shown) {
            sb.append(String.format("%-" + width + "s  ", e.label));
            boolean prev = trace.get(0).values()[e.id] > 0;
            for (TickSimulator.Frame f : trace) {
                boolean on = f.values()[e.id] > 0;
                sb.append(on == prev ? (on ? '\u2588' : '\u2581') : (on ? '\u2571' : '\u2572'));
                prev = on;
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
