package com.redstonelogics.analysis;

import com.redstonelogics.graph.Netlist;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TickSimulator {

    private final Netlist netlist;
    private final int[] out;
    private final int[] pending;
    private final long[] dueAt;
    /** Last value each observer saw at the block it watches. */
    private final boolean[] watched;
    /** Tick at which each observer's pulse ends. */
    private final long[] pulseUntil;
    private final Map<Integer, Boolean> driven = new LinkedHashMap<>();
    private long tick;

    public record Frame(long tick, int[] values) {
    }

    private final List<Frame> trace = new ArrayList<>();

    public TickSimulator(Netlist netlist) {
        this.netlist = netlist;
        int n = netlist.elements().size();
        this.out = new int[n];
        this.pending = new int[n];
        this.dueAt = new long[n];
        this.watched = new boolean[n];
        this.pulseUntil = new long[n];
        for (Netlist.Element e : netlist.inputs()) {
            driven.put(e.id, Boolean.FALSE);
        }
        settle();
    }

    public void set(String inputLabel, boolean on) {
        for (Netlist.Element e : netlist.inputs()) {
            if (e.label.equals(inputLabel)) {
                driven.put(e.id, on);
                return;
            }
        }
        throw new IllegalArgumentException("No input called " + inputLabel);
    }

    public int strengthOf(int elementId) {
        return out[elementId];
    }

    public boolean isOn(int elementId) {
        return out[elementId] > 0;
    }

    public long tick() {
        return tick;
    }

    public List<Frame> trace() {
        return trace;
    }

    public void step(){
        tick++;
        int n = out.length;
        int[] target = new int[n];
        for (Netlist.Element e : netlist.elements()) {
            target[e.id] = compute(e);
        }
        for (int id = 0; id < n; id++) {
            Netlist.Element e = netlist.element(id);
            if (target[id] != pending[id]) {
                pending[id] = target[id];
                // Every element already observes the previous tick's state, so
                // that read is itself one tick of latency. Adding the full
                // delay on top would make a 3-tick repeater take 4.
                dueAt[id] = tick + Math.max(0, delayOf(e) - 1);
            }
            if (out[id] != pending[id] && tick >= dueAt[id]) {
                out[id] = pending[id];
            }
        }
        trace.add(new Frame(tick, out.clone()));
    }

    public int run(int maxTicks) {
        for (int i = 0; i < maxTicks; i++) {
            int[] before = out.clone();
            step();
            boolean same = true;
            for (int j = 0; j < before.length; j++) {
                if (before[j] != out[j]) {
                    same = false;
                    break;
                }
            }
            if (same) {
                return i + 1;
            }
        }
        return -1;
    }

    public int detectPeriod() {
        if (trace.size() < 4) {
            return 0;
        }
        int[] last = trace.get(trace.size() - 1).values();
        for (int back = 2; back <= Math.min(64, trace.size() - 1); back++) {
            if (java.util.Arrays.equals(last, trace.get(trace.size() - back).values())) {
                return back - 1;
            }
        }
        return 0;
    }

    private void settle() {
        run(64);
        trace.clear();
    }

    private int delayOf(Netlist.Element e) {
        return switch (e.kind) {
            case INVERTER -> 1;
            case BUFFER -> Math.max(1, e.delay);
            case CMP_COMPARE, CMP_SUBTRACT -> 1;
            // The pulse length lives in pulseUntil; this is only the lag before
            // the observer reacts at all.
            case EDGE -> 1;
            case INPUT, CONSTANT, OUTPUT -> 0;
        };
    }

    private int compute(Netlist.Element e) {
        switch (e.kind) {
            case INPUT:
                return Boolean.TRUE.equals(driven.get(e.id)) ? 15 : 0;
            case CONSTANT:
                return 15;
            case INVERTER:
                return arriving(e.main()) == 0 ? 15 : 0;
            case BUFFER:
                // A locked repeater holds whatever it last emitted.
                return e.locked ? out[e.id] : (arriving(e.main()) > 0 ? 15 : 0);
            case CMP_COMPARE: {
                int m = arriving(e.main());
                return m > arriving(e.side()) ? m : 0;
            }
            case CMP_SUBTRACT:
                return Math.max(0, arriving(e.main()) - arriving(e.side()));
            case EDGE: {
                // An observer fires on any change to the block it watches, in
                // both directions, and holds for two redstone ticks.
                boolean now = arriving(e.main()) > 0;
                if (now != watched[e.id]) {
                    watched[e.id] = now;
                    pulseUntil[e.id] = tick + 2;
                }
                return tick < pulseUntil[e.id] ? 15 : 0;
            }
            case OUTPUT:
                return arriving(e.main());
            default:
                return 0;
        }
    }

    private int arriving(List<Netlist.Link> port) {
        int best = 0;
        for (Netlist.Link l : port) {
            int delivered = l.strength - (15 - out[l.from]);
            best = Math.max(best, Math.max(0, delivered));
        }
        return best;
    }
}
