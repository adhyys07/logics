package com.redstonelogics.recognize;

import com.redstonelogics.core.Pos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The recognised circuit: logic gates and the wires between them, with every
 * gate still carrying the world position it came from so a diagram can be
 * traced back to the build.
 */
public final class LogicDiagram {

    /** A reference to one output pin of one gate. */
    public record Ref(int gate, int pin) {
    }

    public static final class Gate {
        public final int id;
        public GateType type;
        public String label;
        /** Where this came from in the world; null for synthesised gates. */
        public final Pos origin;
        /** Netlist element ids folded into this gate, for the report. */
        public final List<Integer> elements = new ArrayList<>();
        public final List<Ref> inputs = new ArrayList<>();
        public final List<String> inputPins = new ArrayList<>();
        public final List<String> outputPins = new ArrayList<>();
        /** Minimised expression, delay, or why the gate is generic. */
        public String note;
        /** Redstone ticks of delay folded into this gate. */
        public int delayTicks;

        Gate(int id, GateType type, String label, Pos origin) {
            this.id = id;
            this.type = type;
            this.label = label;
            this.origin = origin;
            this.outputPins.add("Y");
        }

        public void addInput(Ref ref, String pin) {
            inputs.add(ref);
            inputPins.add(pin);
        }

        public int outputCount() {
            return outputPins.size();
        }

        @Override
        public String toString() {
            return "#" + id + " " + type + " " + label;
        }
    }

    private final List<Gate> gates = new ArrayList<>();
    private final List<String> patterns = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();
    private final Map<GateType, Integer> tally = new LinkedHashMap<>();

    public Gate add(GateType type, String label, Pos origin) {
        Gate g = new Gate(gates.size(), type, label, origin);
        gates.add(g);
        return g;
    }

    public List<Gate> gates() {
        return gates;
    }

    public Gate gate(int id) {
        return gates.get(id);
    }

    public List<Gate> of(GateType type) {
        List<Gate> out = new ArrayList<>();
        for (Gate g : gates) {
            if (g.type == type) {
                out.add(g);
            }
        }
        return out;
    }

    public List<Gate> inputs() {
        return of(GateType.INPUT);
    }

    public List<Gate> outputs() {
        return of(GateType.OUTPUT);
    }

    public void recordPattern(String line) {
        patterns.add(line);
    }

    public void note(String line) {
        notes.add(line);
    }

    public List<String> patterns() {
        return patterns;
    }

    public List<String> notes() {
        return notes;
    }

    /** How many of each gate type the diagram ended up with. */
    public Map<GateType, Integer> tally() {
        tally.clear();
        for (Gate g : gates) {
            tally.merge(g.type, 1, Integer::sum);
        }
        return tally;
    }

    /** Consumers of each gate, in gate order. */
    public List<List<Integer>> consumers() {
        List<List<Integer>> out = new ArrayList<>();
        for (int i = 0; i < gates.size(); i++) {
            out.add(new ArrayList<>());
        }
        for (Gate g : gates) {
            for (Ref r : g.inputs) {
                List<Integer> list = out.get(r.gate());
                if (!list.contains(g.id)) {
                    list.add(g.id);
                }
            }
        }
        return out;
    }

    /**
     * Gate ids in dependency order, sequential gates first so that a latch can
     * be read before the combinational logic that feeds it. Any gate left over
     * by a cycle is appended, so the result always contains every gate once.
     */
    public List<Integer> topologicalOrder() {
        int n = gates.size();
        boolean[] placed = new boolean[n];
        List<Integer> order = new ArrayList<>();

        for (Gate g : gates) {
            if (g.type.isTerminal() && g.type != GateType.OUTPUT || g.type.isSequential()) {
                placed[g.id] = true;
                order.add(g.id);
            }
        }

        boolean progress = true;
        while (progress) {
            progress = false;
            for (Gate g : gates) {
                if (placed[g.id]) {
                    continue;
                }
                boolean ready = true;
                for (Ref r : g.inputs) {
                    if (!placed[r.gate()]) {
                        ready = false;
                        break;
                    }
                }
                if (ready) {
                    placed[g.id] = true;
                    order.add(g.id);
                    progress = true;
                }
            }
        }
        for (Gate g : gates) {
            if (!placed[g.id]) {
                order.add(g.id);
            }
        }
        return order;
    }
}
