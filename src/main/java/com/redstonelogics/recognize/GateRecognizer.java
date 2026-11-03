package com.redstonelogics.recognize;

import com.redstonelogics.analysis.BooleanAbstraction;
import com.redstonelogics.analysis.BooleanAbstraction.Fn;
import com.redstonelogics.analysis.TruthTable;
import com.redstonelogics.core.Pos;
import com.redstonelogics.graph.Netlist;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class GateRecognizer {

    private record Match(GateType type, int[] order) {
    }

    private final BooleanAbstraction ba;
    private final Netlist netlist;
    private final LogicDiagram diagram = new LogicDiagram();
    private final LogicDiagram.Ref[] refOf;
    private final int[] gateOf;

    private final List<Integer> pendingVariables = new ArrayList<>();
    private final List<int[]> latches = new ArrayList<>();
    private final List<int[]> feedbacks = new ArrayList<>();
    private final List<int[]> sinks = new ArrayList<>();
    private int synthetic;

    private GateRecognizer(BooleanAbstraction ba) {
        this.ba = ba;
        this.netlist = ba.netlist();
        int n = netlist.elements().size();
        this.refOf = new LogicDiagram.Ref[n];
        this.gateOf = new int[n];
        Arrays.fill(gateOf, -1);
    }

    public static LogicDiagram recognize(BooleanAbstraction ba){
        GateRecognizer r = new GateRecognizer(ba);
        r.placeFixedGates();
        r.fillVariables();
        r.fillLatches();
        r.fillFeedback();
        r.fillSinks();
        r.detectAnalogEquality();
        r.carryDiagnostics();
        return r.diagram;
    }

    private void placeFixedGates() {
        for (Netlist.Element e : netlist.inputs()) {
            LogicDiagram.Gate g = diagram.add(GateType.INPUT, e.label, e.pos);
            g.elements.add(e.id);
            g.note = e.blockId.endsWith("button") ? "momentary" : "latching";
            gateOf[e.id] = g.id;
            refOf[e.id] = new LogicDiagram.Ref(g.id, 0);
        }

        Set<Integer> handled = new HashSet<>();
        for (List<Integer> group : ba.components()) {
            if (group.isEmpty() || !ba.isState(group.get(0))) {
                continue;
            }
            List<Integer> ids = new ArrayList<>(group);
            ids.sort(null);
            boolean pair = ids.size() == 2
                    && netlist.element(ids.get(0)).kind == Netlist.Kind.INVERTER
                    && netlist.element(ids.get(1)).kind == Netlist.Kind.INVERTER;
            if (pair) {
                int q = ids.get(0);
                int qBar = ids.get(1);
                LogicDiagram.Gate g = diagram.add(GateType.SR_LATCH,
                        "L" + latches.size(), netlist.element(q).pos);
                g.outputPins.clear();
                g.outputPins.add("Q");
                g.outputPins.add("/Q");
                g.elements.add(q);
                g.elements.add(qBar);
                g.note = "Cross-coupled torches (SR NOR latch). Holding S and R together "
                        + "drives both outputs low.";
                refOf[q] = new LogicDiagram.Ref(g.id, 0);
                refOf[qBar] = new LogicDiagram.Ref(g.id, 1);
                gateOf[q] = g.id;
                gateOf[qBar] = g.id;
                latches.add(new int[] { g.id, q, qBar });
                diagram.recordPattern("SR latch at " + netlist.element(q).pos + " from "
                        + netlist.element(q).label + " and " + netlist.element(qBar).label);
                handled.addAll(ids);
            } else {
                for (int id : ids) {
                    Netlist.Element e = netlist.element(id);
                    LogicDiagram.Gate g = diagram.add(GateType.FEEDBACK, e.label, e.pos);
                    g.elements.add(id);
                    g.note = "Feedback loop of " + ids.size()
                            + " components; not a recognised latch, so its state is left opaque.";
                    refOf[id] = new LogicDiagram.Ref(g.id, 0);
                    gateOf[id] = g.id;
                    feedbacks.add(new int[] { g.id, id });
                    handled.add(id);
                }
                diagram.recordPattern("Unclassified feedback loop at "
                        + netlist.element(ids.get(0)).pos + " across " + ids.size() + " components");
            }
        }

        for (Netlist.Element e : netlist.elements()) {
            if (!ba.isVariable(e.id) || gateOf[e.id] >= 0) {
                continue;
            }
            LogicDiagram.Gate g = diagram.add(GateType.GENERIC, e.label, e.pos);
            gateOf[e.id] = g.id;
            refOf[e.id] = new LogicDiagram.Ref(g.id, 0);
            pendingVariables.add(e.id);
        }

        int[] fanout = netlist.fanout();
        int implicit = 0;
        for (Netlist.Element e : netlist.elements()) {
            boolean declared = e.kind == Netlist.Kind.OUTPUT;
            boolean dangling = !declared
                    && e.kind != Netlist.Kind.INPUT
                    && e.kind != Netlist.Kind.CONSTANT
                    && fanout[e.id] == 0;
            if (!declared && !dangling) {
                continue;
            }
            String label = declared ? e.label : "Y*" + implicit++;
            LogicDiagram.Gate g = diagram.add(GateType.OUTPUT, label, e.pos);
            g.elements.add(e.id);
            if (dangling) {
                g.note = "Implicit output: nothing in the selection reads " + e.label + ".";
            } else {
                g.note = e.blockId;
            }
            sinks.add(new int[] { e.id, g.id, declared ? 1 : 0 });
        }
    }

    private void fillVariables() {
        for (int id : pendingVariables) {
            Netlist.Element e = netlist.element(id);
            LogicDiagram.Gate g = diagram.gate(gateOf[id]);
            describe(g, ba.function(id), e);
        }
    }

    private void fillLatches() {
        for (int[] latch : latches) {
            LogicDiagram.Gate g = diagram.gate(latch[0]);
            // Q = NOR(R, /Q) and /Q = NOR(S, Q), so S feeds the inverted side.
            LogicDiagram.Ref set = externalFeed(latch[2], latch[1]);
            LogicDiagram.Ref reset = externalFeed(latch[1], latch[2]);
            if (set != null) {
                g.addInput(set, "S");
            }
            if (reset != null) {
                g.addInput(reset, "R");
            }
        }
    }

    private void fillFeedback() {
        for (int[] entry : feedbacks) {
            LogicDiagram.Gate g = diagram.gate(entry[0]);
            List<Integer> loop = ba.componentOf(entry[1]);
            Fn feed = falseFn();
            for (Netlist.Link l : netlist.element(entry[1]).allInputs()) {
                if (!loop.contains(l.from)) {
                    feed = ba.or(feed, ba.valueOf(l.from));
                }
            }
            LogicDiagram.Ref ref = emitUnlessFalse(feed, netlist.element(entry[1]).pos);
            if (ref != null) {
                g.addInput(ref, "D");
            }
        }
    }

    private void fillSinks() {
        for (int[] sink : sinks) {
            Netlist.Element e = netlist.element(sink[0]);
            LogicDiagram.Gate g = diagram.gate(sink[1]);
            Fn fn = ba.function(e.id);
            LogicDiagram.Ref ref = emitUnlessFalse(fn, e.pos);
            if (ref != null) {
                g.addInput(ref, "");
            } else {
                g.note = (g.note == null ? "" : g.note + " ") + "Never powered.";
            }
        }
    }

    private LogicDiagram.Ref externalFeed(int inverter, int partner) {
        Fn feed = falseFn();
        boolean any = false;
        for (Netlist.Link l : netlist.element(inverter).main()) {
            if (l.from != partner) {
                feed = ba.or(feed, ba.valueOf(l.from));
                any = true;
            }
        }
        return any ? emit(feed, netlist.element(inverter).pos) : null;
    }

    private Fn falseFn() {
        return new Fn(new int[0], TruthTable.constant(0, false));
    }

    private LogicDiagram.Ref emitUnlessFalse(Fn fn, Pos origin) {
        if (fn.known() && fn.table().isConstant() && !fn.table().constantValue()) {
            return null;
        }
        return emit(fn, origin);
    }

    private LogicDiagram.Ref emit (Fn fn, Pos origin) {
        if (!fn.known()) {
            LogicDiagram.Gate g = diagram.add(GateType.GENERIC, "G" + synthetic++, origin);
            g.note = "Depends on more than " + TruthTable.MAX_VARS
                    + " independent signals; left untabulated.";
            return new LogicDiagram.Ref(g.id, 0);
        }
        int[] support = fn.table().supportVars();
        if (support.length == 0) {
            boolean value = fn.table().constantValue();
            LogicDiagram.Gate g = diagram.add(value ? GateType.CONST_1 : GateType.CONST_0,
                    value ? "1" : "0", origin);
            return new LogicDiagram.Ref(g.id, 0);
        }
        TruthTable table = fn.table().collapse(support);
        if (support.length == 1 && table.equals(TruthTable.variable(1, 0))) {
            return refOf[fn.vars()[support[0]]];
        }
        LogicDiagram.Gate g = diagram.add(GateType.GENERIC, "G" + synthetic++, origin);
        wire(g, fn, support, table);
        return new LogicDiagram.Ref(g.id, 0);
    }
    
    private void describe(LogicDiagram.Gate g, Fn fn, Netlist.Element e){
        collectAbsorbed(e.id, g);
        if (!fn.known()) {
            g.type = GateType.GENERIC;
            g.note = "Depends on more than " + TruthTable.MAX_VARS + " independent signals.";
            return;
        }
        int[] support = fn.table().supportVars();
        if (support.length == 0 ){
            boolean value = fn.table().constantValue();
            g.type = value ? GateType.CONST_1 : GateType.CONST_0;
            g.note = "Always " + (value ? "on" : "off") + " for every input combination.";
            return;
        }
        wire(g, fn, support, fn.table().collapse(support));
    }

    private void wire(LogicDiagram.Gate g, Fn fn, int[] support, TruthTable table) {
        Match match = match(table);
        g.type = match.type();

        List<String> names = new ArrayList<>();
        for (int s : support) {
            names.add(netlist.element(fn.vars()[s]).label);
        }
        for (int slot = 0; slot < match.order().length; slot++) {
            int supportIndex = match.order()[slot];
            g.addInput(refOf[fn.vars()[support[supportIndex]]], pinName(match.type(), slot));
        }

        // A repeater that only buffers is still worth drawing as a delay.
        if (g.type == GateType.BUFFER && g.delayTicks > 1) {
            g.type = GateType.DELAY;
        }
        String expression = "Y = " + table.toExpression(names);
        g.note = g.delayTicks > 0
                ? expression + "  (" + g.delayTicks + " redstone tick"
                        + (g.delayTicks == 1 ? "" : "s") + " = " + g.delayTicks * 100 + " ms)"
                : expression;
        
        if (g.type != GateType.GENERIC && !g.type.isTerminal()) {
            diagram.recordPattern(g.type.shortName + " at " + g.origin + " over "
                    + String.join(", ", names)
                    + (g.elements.size() > 1 ? " (" + g.elements.size() + " components folded)" : ""));
        }
    }

    private static String pinName(GateType type, int slot) {
        if (type == GateType.INHIBIT) {
            return slot == 0 ? "A" : "B";
        }
        return "";
    }

    private void collectAbsorbed(int start, LogicDiagram.Gate g) {
        Set<Integer> seen = new LinkedHashSet<>();
        Deque<Integer> stack = new ArrayDeque<>();
        stack.push(start);
        while (!stack.isEmpty()) {
            int id = stack.pop();
            if (!seen.add(id)) {
                continue;
            }
            Netlist.Element e = netlist.element(id);
            g.delayTicks += e.delay;
            for (Netlist.Link l : e.allInputs()) {
                if (!ba.isVariable(l.from)) {
                    stack.push(l.from);
                }
            }
        }
        g.elements.addAll(seen);
    }

    private Match match(TruthTable t) {
        int k = t.vars;
        int[] identity = new int[k];
        for (int i = 0; i < k; i++) {
            identity[i] = i;
        }
        if (k == 1) {
            return new Match(t.equals(TruthTable.variable(1, 0)) ? GateType.BUFFER : GateType.NOT,
                    identity);
        }

        TruthTable and = TruthTable.constant(k, true);
        TruthTable or = TruthTable.constant(k, false);
        TruthTable xor = TruthTable.constant(k, false);
        for (int i = 0; i < k; i++) {
            TruthTable v = TruthTable.variable(k, i);
            and = and.and(v);
            or = or.or(v);
            xor = xor.xor(v);
        }
        if (t.equals(and)) {
            return new Match(GateType.AND, identity);
        }
        if (t.equals(or)) {
            return new Match(GateType.OR, identity);
        }
        if (t.equals(and.not())) {
            return new Match(GateType.NAND, identity);
        }

        if (t.equals(xor)) {
            return new Match(GateType.XOR, identity);
        }
        if (t.equals(xor.not())) {
            return new Match(GateType.XNOR, identity);
        }
        if (k == 2) {
            TruthTable a = TruthTable.variable(2, 0);
            TruthTable b = TruthTable.variable(2, 1);
            if (t.equals(a.and(b.not()))) {
                return new Match(GateType.INHIBIT, new int[] { 0, 1 });
            }
            if (t.equals(b.and(a.not()))) {
                return new Match(GateType.INHIBIT, new int[] { 1, 0 });
            }
        }
        return new Match(GateType.GENERIC, identity);
    }

     private void detectAnalogEquality() {
        List<Netlist.Element> comparators = netlist.of(Netlist.Kind.CMP_COMPARE);
        List<List<Integer>> consumers = netlist.consumers();

        for (int i = 0; i < comparators.size(); i++) {
            for (int j = i + 1; j < comparators.size(); j++) {
                Netlist.Element a = comparators.get(i);
                Netlist.Element b = comparators.get(j);
                Set<Integer> aMain = driverSet(a, Netlist.PORT_MAIN);
                Set<Integer> aSide = driverSet(a, Netlist.PORT_SIDE);
                if (aMain.isEmpty() || aSide.isEmpty()) {
                    continue;
                }
                if (!aMain.equals(driverSet(b, Netlist.PORT_SIDE))
                        || !aSide.equals(driverSet(b, Netlist.PORT_MAIN))) {
                    continue;
                }

                diagram.recordPattern("Analog equality detector (XNOR) at " + a.pos + " from "
                        + a.label + " and " + b.label);

                List<Integer> shared = new ArrayList<>(consumers.get(a.id));
                shared.retainAll(consumers.get(b.id));
                if (shared.size() == 1 && gateOf[shared.get(0)] >= 0) {
                    LogicDiagram.Gate g = diagram.gate(gateOf[shared.get(0)]);
                    g.type = GateType.XNOR;
                    g.note = "Analog equality: true when both comparator inputs have the same "
                            + "signal strength. On one-bit inputs that is XNOR.";
                } else {
                    diagram.note("The equality detector at " + a.pos + " does not converge on a "
                            + "single consumer, so it is reported but not folded into one gate.");
                }
            }
        }
    }

    private Set<Integer> driverSet(Netlist.Element e, int port) {
        Set<Integer> out = new LinkedHashSet<>();
        for (Netlist.Link l : e.port(port)) {
            out.add(l.from);
        }
        return out;
    }

    private void carryDiagnostics() {
        netlist.warnings().forEach(diagram::note);
        ba.notes().forEach(diagram::note);
    }
}