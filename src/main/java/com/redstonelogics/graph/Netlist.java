package com.redstonelogics.graph;

import com.redstonelogics.core.Pos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The component-level circuit: one element per redstone component, with edges
 * that already account for dust routing and strength decay.
 *
 * <p>This is the faithful layer. Signal strength 0-15 still means something
 * here ({@link Link#strength}), and comparators keep their two distinct modes.
 * The boolean abstraction on top of it is what collapses strength to true/false.
 */
public final class Netlist {

    /** Port 0: a torch's support, a repeater's or comparator's back input, a sink's input. */
    public static final int PORT_MAIN = 0;
    /** Port 1: a comparator's side inputs, merged. */
    public static final int PORT_SIDE = 1;
    public static final int PORT_COUNT = 2;

    public enum Kind {
        /** Lever or button: a free variable. */
        INPUT,
        /** Block of redstone: a constant 15. */
        CONSTANT,
        /** Redstone torch: inverts its support block's power. */
        INVERTER,
        /** Repeater: buffer, 1-4 tick delay, lockable. */
        BUFFER,
        /** Comparator in compare mode: passes back when back >= side. */
        CMP_COMPARE,
        /** Comparator in subtract mode: max(0, back - side). */
        CMP_SUBTRACT,
        /** Lamp, piston, door: a sink worth drawing. */
        OUTPUT;

        public boolean isSource() {
            return this == INPUT || this == CONSTANT;
        }
    }

    public static final class Link {
        public final int from;
        /** Strength delivered to this port when the driver runs at full 15. */
        public final int strength;

        Link(int from, int strength) {
            this.from = from;
            this.strength = strength;
        }

        @Override
        public String toString() {
            return "#" + from + "@" + strength;
        }
    }

    public static final class Element {
        public final int id;
        public final Kind kind;
        public final Pos pos;
        public final String blockId;
        /** Repeater delay in redstone ticks; 0 for everything else. */
        public final int delay;
        public final boolean locked;
        /** Stable display name: A, B, ... for inputs, Y0, Y1, ... for outputs. */
        public String label;

        private final List<List<Link>> ports = new ArrayList<>(PORT_COUNT);

        Element(int id, Kind kind, Pos pos, String blockId, int delay, boolean locked) {
            this.id = id;
            this.kind = kind;
            this.pos = pos;
            this.blockId = blockId;
            this.delay = delay;
            this.locked = locked;
            for (int i = 0; i < PORT_COUNT; i++) {
                ports.add(new ArrayList<>());
            }
        }

        public List<Link> port(int index) {
            return ports.get(index);
        }

        public List<Link> main() {
            return ports.get(PORT_MAIN);
        }

        public List<Link> side() {
            return ports.get(PORT_SIDE);
        }

        /** Every driver feeding any port of this element. */
        public List<Link> allInputs() {
            List<Link> out = new ArrayList<>();
            for (List<Link> p : ports) {
                out.addAll(p);
            }
            return out;
        }

        public boolean isComparator() {
            return kind == Kind.CMP_COMPARE || kind == Kind.CMP_SUBTRACT;
        }

        @Override
        public String toString() {
            return "#" + id + " " + kind + " " + label + " @" + pos;
        }
    }

    private final List<Element> elements = new ArrayList<>();
    private final Map<Pos, Integer> byPos = new LinkedHashMap<>();
    private final List<String> warnings = new ArrayList<>();
    private final List<List<Pos>> dustNets = new ArrayList<>();

    Element add(Kind kind, Pos pos, String blockId, int delay, boolean locked) {
        Element e = new Element(elements.size(), kind, pos, blockId, delay, locked);
        elements.add(e);
        byPos.put(pos, e.id);
        return e;
    }

    void link(Element to, int port, int from, int strength) {
        for (Link existing : to.port(port)) {
            if (existing.from == from) {
                return;
            }
        }
        to.port(port).add(new Link(from, strength));
    }

    void warn(String message) {
        warnings.add(message);
    }

    void addDustNet(List<Pos> net) {
        dustNets.add(net);
    }

    public List<Element> elements() {
        return elements;
    }

    public Element element(int id) {
        return elements.get(id);
    }

    public Element at(Pos p) {
        Integer id = byPos.get(p);
        return id == null ? null : elements.get(id);
    }

    public List<String> warnings() {
        return warnings;
    }

    /** Connected groups of redstone dust, for the report. */
    public List<List<Pos>> dustNets() {
        return dustNets;
    }

    public List<Element> of(Kind kind) {
        List<Element> out = new ArrayList<>();
        for (Element e : elements) {
            if (e.kind == kind) {
                out.add(e);
            }
        }
        return out;
    }

    public List<Element> inputs() {
        List<Element> out = new ArrayList<>();
        for (Element e : elements) {
            if (e.kind == Kind.INPUT) {
                out.add(e);
            }
        }
        return out;
    }

    public List<Element> outputs() {
        return of(Kind.OUTPUT);
    }

    /** How many distinct elements read each element's output. */
    public int[] fanout() {
        int[] counts = new int[elements.size()];
        for (Element e : elements) {
            boolean[] seen = new boolean[elements.size()];
            for (Link l : e.allInputs()) {
                if (!seen[l.from]) {
                    seen[l.from] = true;
                    counts[l.from]++;
                }
            }
        }
        return counts;
    }

    /** Driver id -> consumer ids, deduplicated. */
    public List<List<Integer>> consumers() {
        List<List<Integer>> out = new ArrayList<>();
        for (int i = 0; i < elements.size(); i++) {
            out.add(new ArrayList<>());
        }
        for (Element e : elements) {
            for (Link l : e.allInputs()) {
                List<Integer> list = out.get(l.from);
                if (!list.contains(e.id)) {
                    list.add(e.id);
                }
            }
        }
        return out;
    }
}
