package com.redstonelogics.recognize;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rewrites a diagram so each composite match becomes a single MACRO gate.
 *
 * <p>{@link SubgraphMatcher} already finds a full adder; without this pass the
 * diagram still draws its five gates, so a build the size of a CPU renders as
 * an unreadable wall. Collapsing turns "1000 gates" into "32 full adders".
 *
 * <p>Gate ids are list indices, so nothing can be edited in place - a new
 * diagram is built and every reference remapped. The MACRO gate inherits the
 * union of its members' netlist element ids, so clicking it still highlights
 * the right blocks in the world.
 */
public final class MacroCollapse {

    /**
     * Below this many gates the diagram is already readable, and collapsing
     * would hide the detail the user came to see.
     */
    public static final int THRESHOLD = 24;

    private MacroCollapse() {
    }

    public static boolean worthwhile(LogicDiagram d, List<SubgraphMatcher.Match> matches) {
        return !matches.isEmpty() && d.gates().size() > THRESHOLD;
    }

    public static LogicDiagram collapse(LogicDiagram source,
            List<SubgraphMatcher.Match> matches) {

        int n = source.gates().size();
        int[] groupOf = new int[n];
        Arrays.fill(groupOf, -1);

        // A match that swallows a terminal would hide an input or an output, so
        // it is skipped rather than collapsed.
        List<SubgraphMatcher.Match> usable = new ArrayList<>();
        for (SubgraphMatcher.Match m : matches) {
            boolean terminal = false;
            boolean clash = false;
            for (int g : m.gates()) {
                terminal |= source.gate(g).type.isTerminal();
                clash |= groupOf[g] != -1;
            }
            if (terminal || clash) {
                continue;
            }
            int index = usable.size();
            for (int g : m.gates()) {
                groupOf[g] = index;
            }
            usable.add(m);
        }
        if (usable.isEmpty()) {
            return source;
        }

        LogicDiagram out = new LogicDiagram();
        int[] newId = new int[n];
        int[] pinOf = new int[n];
        Arrays.fill(newId, -1);
        Arrays.fill(pinOf, -1);

        List<List<LogicDiagram.Ref>> externals = new ArrayList<>();
        List<Integer> macroGates = new ArrayList<>();

        for (SubgraphMatcher.Match m : usable) {
            Set<Integer> members = new LinkedHashSet<>();
            for (int g : m.gates()) {
                members.add(g);
            }

            // Exported pattern nodes, in pattern order, become the macro's pins.
            List<Integer> exported = new ArrayList<>();
            for (int node : m.pattern().exported) {
                exported.add(m.gates()[node]);
            }
            if (exported.isEmpty()) {
                exported.add(m.gates()[0]);
            }

            // Drivers from outside, deduplicated, keeping first-seen order.
            Map<LogicDiagram.Ref, String> inbound = new LinkedHashMap<>();
            for (int g : m.gates()) {
                LogicDiagram.Gate gate = source.gate(g);
                for (LogicDiagram.Ref r : gate.inputs) {
                    if (!members.contains(r.gate())) {
                        inbound.putIfAbsent(r, source.gate(r.gate()).label);
                    }
                }
            }

            LogicDiagram.Gate macro = out.add(GateType.MACRO,
                    shortName(m.pattern().name) + macroGates.size(),
                    source.gate(m.gates()[0]).origin);
            macro.macroName = m.pattern().name;
            macro.outputPins.clear();

            int worstDelay = 0;
            for (int g : m.gates()) {
                LogicDiagram.Gate gate = source.gate(g);
                macro.elements.addAll(gate.elements);
                worstDelay = Math.max(worstDelay, gate.delayTicks);
            }
            macro.delayTicks = worstDelay;

            for (int i = 0; i < exported.size(); i++) {
                int g = exported.get(i);
                macro.outputPins.add(source.gate(g).label);
                pinOf[g] = i;
            }
            macro.note = m.pattern().name + " over " + m.gates().length + " gates: "
                    + describeMembers(source, m.gates());
            macro.macro = subDiagram(source, m, inbound.keySet());

            for (int g : m.gates()) {
                newId[g] = macro.id;
            }
            externals.add(new ArrayList<>(inbound.keySet()));
            macroGates.add(macro.id);
        }

        // Pass one: copy everything that was not swallowed.
        for (LogicDiagram.Gate gate : source.gates()) {
            if (groupOf[gate.id] != -1) {
                continue;
            }
            LogicDiagram.Gate copy = out.add(gate.type, gate.label, gate.origin);
            copy.note = gate.note;
            copy.delayTicks = gate.delayTicks;
            copy.elements.addAll(gate.elements);
            copy.outputPins.clear();
            copy.outputPins.addAll(gate.outputPins);
            copy.macroName = gate.macroName;
            copy.macro = gate.macro;
            newId[gate.id] = copy.id;
        }

        // Pass two: rewire. Refs are stored rather than resolved, so a forward
        // reference to a gate created later is fine.
        for (LogicDiagram.Gate gate : source.gates()) {
            if (groupOf[gate.id] != -1) {
                continue;
            }
            LogicDiagram.Gate copy = out.gate(newId[gate.id]);
            for (int i = 0; i < gate.inputs.size(); i++) {
                copy.addInput(remap(gate.inputs.get(i), groupOf, newId, pinOf),
                        gate.inputPins.get(i));
            }
        }
        for (int index = 0; index < macroGates.size(); index++) {
            LogicDiagram.Gate macro = out.gate(macroGates.get(index));
            for (LogicDiagram.Ref r : externals.get(index)) {
                macro.addInput(remap(r, groupOf, newId, pinOf), source.gate(r.gate()).label);
            }
        }

        for (String line : source.patterns()) {
            out.recordPattern(line);
        }
        for (String line : source.notes()) {
            out.note(line);
        }
        out.note("Collapsed " + usable.size() + " composite design(s) into single blocks; "
                + source.gates().size() + " gates became " + out.gates().size() + ".");
        return out;
    }

    private static LogicDiagram.Ref remap(LogicDiagram.Ref r, int[] groupOf,
            int[] newId, int[] pinOf) {
        if (groupOf[r.gate()] == -1) {
            return new LogicDiagram.Ref(newId[r.gate()], r.pin());
        }
        // An interior node read from outside should have been rejected by
        // SubgraphMatcher.complete; pin 0 keeps the diagram connected if not.
        return new LogicDiagram.Ref(newId[r.gate()], Math.max(0, pinOf[r.gate()]));
    }

    /** The match's own gates, with external drivers turned into INPUT stubs. */
    private static LogicDiagram subDiagram(LogicDiagram source,
            SubgraphMatcher.Match m, Set<LogicDiagram.Ref> inbound) {

        LogicDiagram inner = new LogicDiagram();
        Map<LogicDiagram.Ref, LogicDiagram.Ref> stubs = new LinkedHashMap<>();
        for (LogicDiagram.Ref r : inbound) {
            LogicDiagram.Gate driver = source.gate(r.gate());
            LogicDiagram.Gate stub = inner.add(GateType.INPUT, driver.label, driver.origin);
            stubs.put(r, new LogicDiagram.Ref(stub.id, 0));
        }

        Map<Integer, Integer> inside = new LinkedHashMap<>();
        for (int g : m.gates()) {
            LogicDiagram.Gate from = source.gate(g);
            LogicDiagram.Gate copy = inner.add(from.type, from.label, from.origin);
            copy.note = from.note;
            copy.delayTicks = from.delayTicks;
            copy.elements.addAll(from.elements);
            inside.put(g, copy.id);
        }
        for (int g : m.gates()) {
            LogicDiagram.Gate from = source.gate(g);
            LogicDiagram.Gate copy = inner.gate(inside.get(g));
            for (int i = 0; i < from.inputs.size(); i++) {
                LogicDiagram.Ref r = from.inputs.get(i);
                LogicDiagram.Ref mapped = inside.containsKey(r.gate())
                        ? new LogicDiagram.Ref(inside.get(r.gate()), r.pin())
                        : stubs.get(r);
                if (mapped != null) {
                    copy.addInput(mapped, from.inputPins.get(i));
                }
            }
        }
        return inner;
    }

    private static String describeMembers(LogicDiagram d, int[] gates) {
        List<String> names = new ArrayList<>();
        for (int g : gates) {
            names.add(d.gate(g).type.shortName + " " + d.gate(g).label);
        }
        return String.join(", ", names);
    }

    /** "full adder" becomes "FA", so the collapsed gate keeps a short label. */
    private static String shortName(String patternName) {
        StringBuilder sb = new StringBuilder();
        for (String word : patternName.split("[ :]+")) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
            }
        }
        return sb.length() == 0 ? "M" : sb.toString();
    }
}
