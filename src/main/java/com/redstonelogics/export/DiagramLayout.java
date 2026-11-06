package com.redstonelogics.export;

import com.redstonelogics.recognize.GateType;
import com.redstonelogics.recognize.LogicDiagram;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class DiagramLayout {
    public static final double GATE_WIDTH = 64;
    public static final double TERMINAL_WIDTH = 78;
    public static final double PIN_SPACING = 20;
    public static final double LAYER_GAP = 96;
    public static final double ROW_GAP = 26;
    public static final double MARGIN = 28;
    public static final double STUB = 12;

    public record Node(int gate, int layer, double x, double y, double width, double height) {
        public double centreY() {
            return y + height / 2;
        }
    }

    public record  Wire(LogicDiagram.Ref from, int toGate, int toPin, double[] points) {
    }


    private final LogicDiagram diagram;
    private final Node[] nodes;
    private final List<Wire> wires = new ArrayList<>();
    private double width;
    private double height;

    private DiagramLayout(LogicDiagram diagram) {
        this.diagram = diagram;
        this.nodes = new Node[diagram.gates().size()];
    }

    public static DiagramLayout of(LogicDiagram diagram) {
        DiagramLayout l = new DiagramLayout(diagram);
        int[] layers = l.assignLayers();
        l.place(layers);
        l.route();
        return l;
    }

    private int[] assignLayers() {
        List<LogicDiagram.Gate> gates = diagram.gates();
        int[] layer = new int[gates.size()];
        boolean[] pinned = new boolean[gates.size()];
        for (LogicDiagram.Gate g : gates) {
            if (g.type == GateType.INPUT || g.type == GateType.CONST_0
                    || g.type == GateType.CONST_1 || g.type.isSequential()) {
                pinned[g.id] = true;
            }
        }

        for (int pass = 0; pass < gates.size() + 8; pass++) {
            boolean moved = false;
            for (LogicDiagram.Gate g : gates) {
                if (pinned[g.id]) {
                    continue;
                }
                int want = 0;
                for (LogicDiagram.Ref r : g.inputs) {
                    want = Math.max(want, layer[r.gate()] + 1);
                }
                if (want != layer[g.id]) {
                    layer[g.id] = want;
                    moved = true;
                }
            }
            if (!moved) {
                break;
            }
        }

        int last = 0;
        for (int v : layer) {
            last = Math.max(last, v);
        }
        for (LogicDiagram.Gate g : gates) {
            if (g.type == GateType.OUTPUT) {
                layer[g.id] = last + 1;
            }
        }
        return layer;
    }

    private void place(int[] layers) {
        int layerCount = 0;
        for (int v: layers) {
            layerCount = Math.max(layerCount, v + 1);
        }

        List<List<LogicDiagram.Gate>> columns = new ArrayList<>();
        for (int i = 0; i < layerCount; i++) {
            columns.add(new ArrayList<>());
        }
        for (LogicDiagram.Gate g : diagram.gates()) {
            columns.get(layers[g.id]).add(g);
        }

        double[] bary = new double[diagram.gates().size()];
        for (int sweep = 0; sweep < 2; sweep++) {
            for (List<LogicDiagram.Gate> column : columns) {
                for (LogicDiagram.Gate g : column) {
                    if (g.inputs.isEmpty()) {
                        bary[g.id] = sweep == 0 ? indexIn(column, g) : bary[g.id];
                        continue;
                    }
                    double sum = 0;
                    for (LogicDiagram.Ref r : g.inputs) {
                        sum += bary[r.gate()];
                    }
                    bary[g.id] = sum / g.inputs.size();
                }
                column.sort(Comparator.comparingDouble(g -> bary[g.id]));
                for (int i = 0; i < column.size(); i++) {
                    bary[column.get(i).id] = i;
                }
            }
        }

        double x = MARGIN;
        for (int li = 0; li < columns.size(); li++) {
            List<LogicDiagram.Gate> column = columns.get(li);
            double columnWidth = 0;
            double y = MARGIN;
            for (LogicDiagram.Gate g : column) {
                double w = g.type.isTerminal() ? TERMINAL_WIDTH : GATE_WIDTH;
                int pins = Math.max(Math.max(g.inputs.size(), g.outputCount()), 2);
                double h = Math.max(42, pins * PIN_SPACING + 8);
                nodes[g.id] = new Node(g.id, li, x, y, w, h);
                y += h + ROW_GAP;
                columnWidth = Math.max(columnWidth, w);
            }
             height = Math.max(height, y);
            x += columnWidth + LAYER_GAP;
        }
        width = x - LAYER_GAP + MARGIN;
        height = height - ROW_GAP + MARGIN;
    }

    private static int indexIn(List<LogicDiagram.Gate> column, LogicDiagram.Gate g) {
        return column.indexOf(g);
    }

    private void route() {
        for (LogicDiagram.Gate g : diagram.gates()) {
            for (int pin = 0; pin < g.inputs.size(); pin++) {
              LogicDiagram.Ref from = g.inputs.get(pin);
                double[] start = outputPin(from.gate(), from.pin());
                double[] end = inputPin(g.id, pin);
                wires.add(new Wire(from, g.id, pin, path(start, end)));
            }
        }
    }

    private double[] path(double[] start, double[] end) {
        double sx = start[0];
        double sy = start[1];
        double tx = end[0];
        double ty = end[1];
        if (tx - sx > 2 * STUB) {
            double mid = (sx + tx) / 2;
            return new double[] { sx, sy, mid, sy, ty,tx, ty};
    }
    double over = MARGIN / 2;
    return new double[] {
        sx, sy,
        sx + STUB, sy,
        sx + STUB, over,
        tx - STUB, over,
        tx - STUB, ty,
        tx, ty };
    }

    public double[] outputPin(int gate, int pin) {
        Node n = nodes[gate];
        LogicDiagram.Gate g = diagram.gate(gate);
        int count = g.outputCount();
        double step = n.height() / (count + 1);
        return new double[] { n.x() + n.width() + STUB, n.y() + step * (pin + 1) };
    }

    public double[] inputPin(int gate, int pin) {
        Node n = nodes[gate];
        int count = Math.max(1, diagram.gate(gate).inputs.size());
        double step = n.height() / (count + 1);
        return new double[] { n.x() - STUB, n.y() + step * (pin + 1) };
    }

    public Node node(int gate) {
        return nodes[gate];
    }

    public List<Node> nodes() {
        return Arrays.asList(nodes);
    }

    public List<Wire> wires() {
        return wires;
    }

    public LogicDiagram diagram() {
        return diagram;
    }

    public double width() {
        return width;
    }

    public double height() {
        return height;
    }
}