package com.redstonelogics.analysis;

import com.redstonelogics.graph.Netlist;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class DeadLogic {

    public record Finding(String label, String pos, String reason) {
    }

    public static List<Finding> of(Netlist netlist) {
        int n = netlist.elements().size();
        boolean[] live = new boolean[n];

        Deque<Integer> queue = new ArrayDeque<>();
        for (Netlist.Element out : netlist.outputs()) {
            if (!live[out.id]) {
                live[out.id] = true;
                queue.add(out.id);
            }
        }
        while (!queue.isEmpty()) {
            Netlist.Element e = netlist.element(queue.poll());
            for (Netlist.Link l : e.allInputs()) {
                if (!live[l.from]) {
                    live[l.from] = true;
                    queue.add(l.from);
                }
            }
        }

        List<Finding> findings = new ArrayList<>();
        int[] fanout = netlist.fanout();
        for (Netlist.Element e : netlist.elements()) {
            if (e.kind == Netlist.Kind.OUTPUT) {
                continue;
            }
            if (!live[e.id]) {
                findings.add(new Finding(e.label, e.pos.toString(),
                        "output never reaches a lamp, piston or other sink - this "
                        + "component can be removed without changing behaviour"));
            } else if (e.kind == Netlist.Kind.INPUT && fanout[e.id] == 0) {
                findings.add(new Finding(e.label, e.pos.toString(),
                        "lever or button that drives nothing"));
            }
        }
        return findings;
    }

    public static String render(List<Finding> findings) {
        if (findings.isEmpty()) {
            return "## Dead logic\n\nNone: every component contributes to an output.\n";
        }
        StringBuilder sb = new StringBuilder("## Dead logic\n\n");
        for (Finding f : findings) {
            sb.append("- ").append(f.label()).append(" at ").append(f.pos())
              .append(": ").append(f.reason()).append('\n');
        }
        return sb.toString();
    }

    private DeadLogic() {
    }
}