package com.redstonelogics.export;

import com.redstonelogics.recognize.GateType;
import com.redstonelogics.recognize.LogicDiagram;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class KiCadExporter {

    /** A 74HC part, its pin numbers, and what it is. */
    private record Part(String value, String description, int[] inPins, int outPin) {
    }

    private KiCadExporter() {
    }

    private static Part partFor(GateType type, int inputs) {
        return switch (type) {
            case NOT -> new Part("74HC04", "Hex inverter", new int[] { 1 }, 2);
            case BUFFER, DELAY -> new Part("74HC125", "Quad buffer", new int[] { 2 }, 3);
            case AND -> new Part("74HC08", "Quad 2-input AND", new int[] { 1, 2 }, 3);
            case OR -> new Part("74HC32", "Quad 2-input OR", new int[] { 1, 2 }, 3);
            case NAND -> new Part("74HC00", "Quad 2-input NAND", new int[] { 1, 2 }, 3);
            case NOR -> new Part("74HC02", "Quad 2-input NOR", new int[] { 2, 3 }, 1);
            case XOR -> new Part("74HC86", "Quad 2-input XOR", new int[] { 1, 2 }, 3);
            case XNOR -> new Part("74HC266", "Quad 2-input XNOR", new int[] { 1, 2 }, 3);
            case SR_LATCH -> new Part("74HC279", "Quad SR latch", new int[] { 1, 2 }, 3);
            // INHIBIT is a AND NOT b, and anything unmatched keeps its pins in
            // order so the netlist still describes the real connectivity.
            default -> new Part("74HC00", "Unmapped logic, pins in order",
                    sequential(inputs), inputs + 1);
        };
    }

    private static int[] sequential(int inputs) {
        int[] pins = new int[Math.max(inputs, 1)];
        for (int i = 0; i < pins.length; i++) {
            pins[i] = i + 1;
        }
        return pins;
    }

    public static String render(LogicDiagram diagram, String title) {
        Map<Integer, String> ref = new LinkedHashMap<>();
        Map<Integer, Part> part = new LinkedHashMap<>();
        int logic = 0;
        int connector = 0;

        for (LogicDiagram.Gate g : diagram.gates()) {
            if (g.type.isTerminal()) {
                ref.put(g.id, "J" + (++connector));
            } else {
                ref.put(g.id, "U" + (++logic));
                part.put(g.id, partFor(g.type, g.inputs.size()));
            }
        }

        // Which gate pins each driver feeds, so a net can list every node on it.
        Map<String, List<int[]>> consumers = new LinkedHashMap<>();
        for (LogicDiagram.Gate g : diagram.gates()) {
            for (int pin = 0; pin < g.inputs.size(); pin++) {
                LogicDiagram.Ref from = g.inputs.get(pin);
                consumers.computeIfAbsent(from.gate() + ":" + from.pin(),
                        k -> new ArrayList<>()).add(new int[] { g.id, pin });
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("(export (version \"E\")\n");
        sb.append("  (design\n");
        sb.append("    (source ").append(quote(title)).append(")\n");
        sb.append("    (date \"").append(LocalDate.now()).append("\")\n");
        sb.append("    (tool \"RedstoneLogics\"))\n");

        sb.append("  (components\n");
        for (LogicDiagram.Gate g : diagram.gates()) {
            String value = g.type.isTerminal()
                    ? g.label
                    : part.get(g.id).value();
            String description = g.type.isTerminal()
                    ? (g.type == GateType.INPUT ? "Input terminal" : "Output terminal")
                    : part.get(g.id).description();
            String lib = g.type.isTerminal() ? "Connector" : "74xx";

            sb.append("    (comp (ref ").append(quote(ref.get(g.id))).append(")\n");
            sb.append("      (value ").append(quote(value)).append(")\n");
            sb.append("      (libsource (lib ").append(quote(lib))
              .append(") (part ").append(quote(value))
              .append(") (description ").append(quote(description)).append("))\n");
            sb.append("      (property (name \"RedstonePos\") (value ")
              .append(quote(String.valueOf(g.origin))).append("))");
            if (g.delayTicks > 0) {
                sb.append("\n      (property (name \"DelayTicks\") (value ")
                  .append(quote(String.valueOf(g.delayTicks))).append("))");
            }
            sb.append(")\n");
        }
        sb.append("  )\n");

        sb.append("  (nets\n");
        int code = 0;
        for (LogicDiagram.Gate g : diagram.gates()) {
            for (int out = 0; out < g.outputCount(); out++) {
                List<int[]> sinks = consumers.get(g.id + ":" + out);
                if (sinks == null || sinks.isEmpty()) {
                    continue;                      // drives nothing, so not a net
                }
                String name = g.outputCount() > 1
                        ? g.label + "/" + g.outputPins.get(out)
                        : g.label;
                sb.append("    (net (code ").append(quote(String.valueOf(++code)))
                  .append(") (name ").append(quote(name)).append(")\n");
                sb.append("      (node (ref ").append(quote(ref.get(g.id)))
                  .append(") (pin ").append(quote(String.valueOf(outPin(g, part, out))))
                  .append("))\n");
                for (int[] sink : sinks) {
                    LogicDiagram.Gate h = diagram.gate(sink[0]);
                    sb.append("      (node (ref ").append(quote(ref.get(h.id)))
                      .append(") (pin ").append(quote(String.valueOf(inPin(h, part, sink[1]))))
                      .append("))\n");
                }
                sb.append("    )\n");
            }
        }
        sb.append("  )\n)\n");
        return sb.toString();
    }

    private static int outPin(LogicDiagram.Gate g, Map<Integer, Part> part, int index) {
        if (g.type.isTerminal()) {
            return 1;
        }
        Part p = part.get(g.id);
        return g.outputCount() > 1 ? p.outPin() + index : p.outPin();
    }

    private static int inPin(LogicDiagram.Gate g, Map<Integer, Part> part, int index) {
        if (g.type.isTerminal()) {
            return 1;
        }
        int[] pins = part.get(g.id).inPins();
        return index < pins.length ? pins[index] : index + 1;
    }

    private static String quote(String s) {
        return '"' + (s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"")) + '"';
    }
}
