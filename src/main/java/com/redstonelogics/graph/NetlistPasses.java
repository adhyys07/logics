package com.redstonelogics.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class NetlistPasses {

    public record Report(int clamped, int deduped) {
        public boolean changed() {
            return clamped > 0 || deduped > 0;
        }

        @Override
        public String toString() {
            return "clamped " + clamped + " dead link(s), merged " + deduped + " duplicate(s)";
        }
    }

    private NetlistPasses(){
    }

    public static Report optimise(Netlist netlist){
        return new Report(clampWeights(netlist), dedupLinks(netlist));
    }

    public static int clampWeights(Netlist netlist) {
        int removed = 0;
        for (Netlist.Element e : netlist.elements()) {
            for (int p = 0; p < Netlist.PORT_COUNT; p++) {
                List<Netlist.Link> port = e.port(p);
                int before = port.size();
                port.removeIf(l -> l.strength <= 0);
                removed += before - port.size();
            }
        }
        return removed;
    }

    public static int dedupLinks(Netlist netlist) {
        int removed = 0;
        for (Netlist.Element e : netlist.elements()) {
            for (int p = 0; p < Netlist.PORT_COUNT; p++) {
                List<Netlist.Link> port = e.port(p);
                Map<Integer, Netlist.Link> best = new HashMap<>();
                for (Netlist.Link l : port) {
                    Netlist.Link seen = best.get(l.from);
                    if (seen == null || l.strength > seen.strength) {
                        best.put(l.from, l);
                    }
                }
                if (best.size() == port.size()) {
                    continue;
                }
                removed += port.size() - best.size();
                List<Netlist.Link> kept = new ArrayList<>(best.values());
                port.clear();
                port.addAll(kept);
            }
        }
        return removed;
    }
}