package com.redstonelogics.graph;

import com.redstonelogics.core.BlockKind;
import com.redstonelogics.core.Direction;
import com.redstonelogics.core.Pos;
import com.redstonelogics.core.RegionSnapshot;
import com.redstonelogics.core.ScannedBlock;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns a scanned region into a {@link Netlist}.
 *
 * <p>Edges are discovered one driver at a time: energise exactly that component
 * at strength 15, let {@link PowerModel} propagate, and record every component
 * port that receives at least 1. Because redstone combines signals with
 * <em>max</em> rather than by addition, a driver's reach never grows when other
 * drivers switch on - so per-driver isolation finds exactly the real edges, and
 * strength decay is spent here instead of leaking into the logic stage.
 */
public final class NetlistExtractor {

    private final RegionSnapshot snap;
    private final Netlist netlist = new Netlist();

    private NetlistExtractor(RegionSnapshot snap) {
        this.snap = snap;
    }

    public static Netlist extract(RegionSnapshot snap) {
        NetlistExtractor x = new NetlistExtractor(snap);
        x.createElements();
        x.collectDustNets();
        x.connect();
        x.audit();
        return x.netlist;
    }

    // ---------------------------------------------------------------- elements

    private void createElements() {
        int inputIndex = 0;
        int outputIndex = 0;
        int torchIndex = 0;
        int repeaterIndex = 0;
        int comparatorIndex = 0;
        int constantIndex = 0;

        for (Pos p : snap.positions()) {
            ScannedBlock b = snap.at(p);
            Netlist.Element e;
            switch (b.kind()) {
                case TORCH -> {
                    e = netlist.add(Netlist.Kind.INVERTER, p, b.blockId(), 0, false);
                    e.label = "T" + torchIndex++;
                }
                case REPEATER -> {
                    e = netlist.add(Netlist.Kind.BUFFER, p, b.blockId(), b.delay(), b.locked());
                    e.label = "R" + repeaterIndex++;
                }
                case COMPARATOR -> {
                    Netlist.Kind k = b.subtract() ? Netlist.Kind.CMP_SUBTRACT : Netlist.Kind.CMP_COMPARE;
                    e = netlist.add(k, p, b.blockId(), 0, false);
                    e.label = "C" + comparatorIndex++;
                }
                case LEVER, BUTTON -> {
                    e = netlist.add(Netlist.Kind.INPUT, p, b.blockId(), 0, false);
                    e.label = inputName(inputIndex++);
                }
                case REDSTONE_BLOCK -> {
                    e = netlist.add(Netlist.Kind.CONSTANT, p, b.blockId(), 0, false);
                    e.label = "VCC" + (constantIndex++ == 0 ? "" : String.valueOf(constantIndex - 1));
                }
                case LAMP, MECHANISM -> {
                    e = netlist.add(Netlist.Kind.OUTPUT, p, b.blockId(), 0, false);
                    e.label = "Y" + outputIndex++;
                }
                default -> {
                    // Dust and solid blocks are wiring, not elements.
                }
            }
        }
    }

    /** A, B, ... Z, A1, B1, ... for input pins. */
    private static String inputName(int index) {
        char letter = (char) ('A' + index % 26);
        int wrap = index / 26;
        return wrap == 0 ? String.valueOf(letter) : letter + String.valueOf(wrap);
    }

    // --------------------------------------------------------------- dust nets

    private void collectDustNets() {
        Set<Pos> seen = new HashSet<>();
        for (Pos start : snap.positionsOf(BlockKind.DUST)) {
            if (!seen.add(start)) {
                continue;
            }
            List<Pos> net = new ArrayList<>();
            Deque<Pos> queue = new ArrayDeque<>();
            queue.add(start);
            while (!queue.isEmpty()) {
                Pos p = queue.poll();
                net.add(p);
                for (Pos n : PowerModel.dustNeighbours(snap, p)) {
                    if (seen.add(n)) {
                        queue.add(n);
                    }
                }
            }
            Collections.sort(net);
            netlist.addDustNet(net);
        }
    }

    // ------------------------------------------------------------------- edges

    private void connect() {
        List<Netlist.Element> drivers = new ArrayList<>();
        List<Netlist.Element> sinks = new ArrayList<>();
        for (Netlist.Element e : netlist.elements()) {
            if (e.kind != Netlist.Kind.OUTPUT) {
                drivers.add(e);
            }
            if (e.kind != Netlist.Kind.INPUT && e.kind != Netlist.Kind.CONSTANT) {
                sinks.add(e);
            }
        }

        for (Netlist.Element driver : drivers) {
            PowerModel model = PowerModel.isolating(snap, driver.pos);
            for (Netlist.Element sink : sinks) {
                if (sink.id == driver.id) {
                    continue;
                }
                int main = mainInput(model, sink);
                if (main >= 1) {
                    netlist.link(sink, Netlist.PORT_MAIN, driver.id, main);
                }
                if (sink.isComparator()) {
                    int side = sideInput(model, sink);
                    if (side >= 1) {
                        netlist.link(sink, Netlist.PORT_SIDE, driver.id, side);
                    }
                }
            }
        }
    }

    private int mainInput(PowerModel model, Netlist.Element sink) {
        ScannedBlock b = snap.at(sink.pos);
        return switch (sink.kind) {
            case INVERTER -> {
                Pos support = b.support(sink.pos);
                yield snap.at(support).isSolid() ? model.blockPower(support) : 0;
            }
            case BUFFER, CMP_COMPARE, CMP_SUBTRACT -> model.signalInto(sink.pos, b.facing().opposite());
            case OUTPUT -> model.mechanismPower(sink.pos);
            default -> 0;
        };
    }

    private int sideInput(PowerModel model, Netlist.Element sink) {
        Direction front = snap.at(sink.pos).facing();
        Direction left = front.rotateY();
        return Math.max(model.signalInto(sink.pos, left),
                model.signalInto(sink.pos, left.opposite()));
    }

    // ----------------------------------------------------------------- warnings

    private void audit() {
        if (netlist.inputs().isEmpty()) {
            netlist.warn("No lever or button in the selection, so the circuit has no free inputs. "
                    + "Add one and rescan, or the diagram will only show constants.");
        }
        if (netlist.outputs().isEmpty()) {
            netlist.warn("No lamp, piston or other sink in the selection. "
                    + "The last component in each chain will be treated as an output.");
        }

        for (Netlist.Element e : netlist.elements()) {
            ScannedBlock b = snap.at(e.pos);
            switch (e.kind) {
                case INVERTER -> {
                    Pos support = b.support(e.pos);
                    if (!snap.at(support).isSolid()) {
                        netlist.warn(e.label + " at " + e.pos + " has no conductive support block at "
                                + support + ", so it can never switch off and is treated as a constant.");
                    } else if (!snap.inBounds(support)) {
                        netlist.warn(e.label + " at " + e.pos + " reads a support block outside the selection.");
                    }
                }
                case BUFFER -> {
                    if (e.locked) {
                        netlist.warn(e.label + " at " + e.pos + " is locked by a side repeater. "
                                + "The lock is modelled as transparent, so the diagram shows the "
                                + "unlocked behaviour.");
                    }
                    if (!snap.inBounds(b.back(e.pos))) {
                        netlist.warn(e.label + " at " + e.pos + " reads its input from outside the selection.");
                    }
                }
                case CMP_COMPARE, CMP_SUBTRACT -> {
                    if (e.main().isEmpty() && e.side().isEmpty()) {
                        netlist.warn(e.label + " at " + e.pos + " has no redstone input. If it is reading a "
                                + "container's fill level, that is not modelled.");
                    }
                }
                default -> {
                }
            }

            if (e.kind != Netlist.Kind.INPUT && e.kind != Netlist.Kind.CONSTANT && e.allInputs().isEmpty()) {
                netlist.warn(e.label + " at " + e.pos + " is driven by nothing and will fold to a constant.");
            }
        }

        for (Map.Entry<BlockKind, Integer> entry : snap.inventory().entrySet()) {
            if (entry.getKey() == BlockKind.MECHANISM) {
                netlist.warn(entry.getValue() + " mechanism block(s) are drawn as plain outputs; "
                        + "piston, observer and quasi-connectivity behaviour is not simulated.");
            }
        }
    }
}
