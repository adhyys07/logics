package com.redstonelogics.graph;

import java.util.List;


public final class StrengthSets {

    private static final int OFF = 1;
    private static final int FULL = 1 | (1 << 15);
    private final Netlist netlist;
    private final int[] sets;
    private StrengthSets(Netlist netlist) {
        this.netlist = netlist;
        this.sets = new int[netlist.elements().size()];
    }

    public static StrengthSets of(Netlist netlist) {
        StrengthSets s = new StrengthSets(netlist);
        s.run();
        return s;
    }

    private void run() {
        for (Netlist.Element e : netlist.elements()) {
            sets[e.id] = switch (e.kind) {
                case INPUT -> FULL;                 // a lever is off or full
                case CONSTANT -> 1 << 15;           // a block of redstone
                default -> OFF;                     // grown below
            };
        }

        boolean changed = true;
        int guard = 0;
        // Monotone, so this settles; the guard only catches a logic error.
        while (changed && guard++ < netlist.elements().size() + 16) {
            changed = false;
            for (Netlist.Element e : netlist.elements()) {
                int next = sets[e.id] | compute(e);
                if (next != sets[e.id]) {
                    sets[e.id] = next;
                    changed = true;
                }
            }
        }
    }

    private int compute(Netlist.Element e) {
        return switch (e.kind) {
            case INPUT, CONSTANT -> sets[e.id];
            case INVERTER, BUFFER -> FULL;
            case CMP_COMPARE -> compare(e);
            case CMP_SUBTRACT -> subtract(e);
            case OUTPUT -> arriving(e.main());
        };
    }

    private int compare(Netlist.Element e) {
        int main = arriving(e.main());
        int side = arriving(e.side());
        int out = OFF;
        for (int m = 0; m <= 15; m++) {
            if ((main & (1 << m)) == 0) {
                continue;
            }
            for (int s = 0; s <= 15; s++) {
                if ((side & (1 << s)) != 0 && m > s) {
                    out |= 1 << m;
                }
            }
        }
        return out;   
    }

    private int subtract(Netlist.Element e ){
        int main = arriving(e.main());
        int side = arriving(e.side());
        int out = OFF;
        for (int m = 0; m <= 15; m++) {
            if ((main & (1 << m)) == 0) {
                continue;
            }
            for (int s = 0; s <= 15; s++) {
                if ((side & (1 << s)) != 0) {
                    out |= 1 << Math.max(0, m - s);
                }
            }
        }
        return out;
    }

    private int arriving(List<Netlist.Link> port) {
        int out = OFF;
        for (Netlist.Link l : port) {
            int source = sets[l.from];
            for (int s = 0; s <= 15; s++) {
                if ((source & (1 << s)) == 0) {
                    continue;
                }
                int delivered = l.strength - (15 - s);
                if (delivered > 0) {
                    out |= 1 << delivered;
                }
            }
        }
        return out;
    }

    public int setFor(int elementId) {
        return sets[elementId];
    }

    public int maxOutput(int elementId) {
        return 15 - Integer.numberOfLeadingZeros(sets[elementId]) + 16;
    }

    public boolean isDigital(int elementId) {
        return (sets[elementId] & ~FULL) == 0;
    }

    public List<Netlist.Element> analogElements() {
        List<Netlist.Element> out = new java.util.ArrayList<>();
        for (Netlist.Element e : netlist.elements()) {
            if (!isDigital(e.id)) {
                out.add(e);
            }
        }
        return out;
    }
    
    public static int pruneUnreachable(Netlist netlist, StrengthSets sets) {
        int removed = 0;
        for (Netlist.Element e : netlist.elements()) {
            for (int p = 0; p < Netlist.PORT_COUNT; p++) {
                List<Netlist.Link> port = e.port(p);
                int before = port.size();
                port.removeIf(l -> l.strength - (15 - sets.maxOutput(l.from)) <= 0);
                removed += before - port.size();
            }
        }
        return removed;
    }
}

