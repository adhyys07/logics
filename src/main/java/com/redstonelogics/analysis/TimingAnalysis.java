package com.redstonelogics.analysis;

import com.redstonelogics.graph.Netlist;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class TimingAnalysis {
    public static int delayOf(Netlist.Element e) {
        return switch (e.kind) {
            case INVERTER -> 1;                      // a torch takes a tick to flip
            case BUFFER -> Math.max(1, e.delay);     // repeater, 1-4
            case CMP_COMPARE, CMP_SUBTRACT -> 1;
            case EDGE -> 1;                          // observer, before it fires
            case INPUT, CONSTANT, OUTPUT -> 0;
        };
    }

    public record Arrival(int earliest, int latest) {
        public int spread() {
            return latest - earliest;
        }
    }

    private final Netlist netlist;
    private final Arrival[] arrival;
    private final int[] inputSpread;
    private final int[] prev;
    private final boolean[] timed;
    private final List<String> warnings = new ArrayList<>();

    private TimingAnalysis(Netlist netlist) {
        int n = netlist.elements().size();
        this.netlist = netlist;
        this.arrival = new Arrival[n];
        this.inputSpread = new int[n];
        this.prev = new int[n];
        this.timed = new boolean[n];
        java.util.Arrays.fill(prev, -1);
    }

    public static TimingAnalysis of(Netlist netlist) {
        TimingAnalysis t = new TimingAnalysis(netlist);
        t.run();
        return t;
    }

    private void run() {
        List<Integer> order = topoOrder();
        if (order.size() < netlist.elements().size()) {
            warnings.add("Feedback loop present; elements inside it are left untimed, "
                    + "because a cycle has no finite settling time in this model.");
        }

        for (int id: order) {
            Netlist.Element e = netlist.element(id);
            List<Netlist.Link> links = e.allInputs();

            if (links.isEmpty()) {
                arrival[id] = new Arrival(0, 0);
                timed[id] = true;
                continue;
            }

            int earliest = Integer.MAX_VALUE;
            int latest = Integer.MIN_VALUE;
            int latestFrom = -1;
            boolean complete = true;

            for (Netlist.Link l : links) {
                if (!timed[l.from]) {
                    complete = false;
                    continue;
                }
                Arrival a = arrival[l.from];
                if (a.earliest() < earliest) {
                    earliest = a.earliest();
                }
                if (a.latest() > latest) {
                    latest = a.latest();
                    latestFrom = l.from;
                }
            }

            if (!complete || latestFrom < 0) {
                continue;
            }

            int own = delayOf(e);
            inputSpread[id] = latest - earliest;
            arrival[id] = new Arrival(earliest + own, latest + own);
            prev[id] = latestFrom;
            timed[id] = true;

            if (inputSpread[id] > 0 && distinctSources(links) > 1) {
                warnings.add(e.label + " at " + e.pos + " has inputs arriving "
                        + inputSpread[id] + " tick(s) apart (" + earliest + " to " + latest
                        + "); the output can spike while the slower input catches up.");
            }
        }
    }

    private static int distinctSources(List<Netlist.Link> links) {
        List<Integer> seen = new ArrayList<>();
        for (Netlist.Link l : links) {
            if (!seen.contains(l.from)) {
                seen.add(l.from);
            }
        }
        return seen.size();
    }

    private List<Integer> topoOrder() {
        int n = netlist.elements().size();
        int[] indegree = new int[n];
        List<List<Integer>> consumers = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            consumers.add(new ArrayList<>());
        }
        for (Netlist.Element e : netlist.elements()) {
            for (Netlist.Link l : e.allInputs()) {
                consumers.get(l.from).add(e.id);
                indegree[e.id]++;
            }
        }
        Deque<Integer> queue = new ArrayDeque<>();
        for (int i = 0; i < n; i++) {
            if (indegree[i] == 0) {
                queue.add(i);
            }
        }
        List<Integer> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            int id = queue.poll();
            order.add(id);
            for (int c : consumers.get(id)) {
                if (--indegree[c] == 0) {
                    queue.add(c);
                }
            }
        }
        return order;
    }

    /** When this element's output can first and last change, in ticks. */
    public Arrival arrivalAt(int elementId) {
        return arrival[elementId];
    }

    /** Gates whose inputs arrive on different ticks, plus any feedback note. */
    public List<String> warnings() {
        return warnings;
    }

    /** The slowest output, in redstone ticks. */
    public int criticalPathTicks() {
        int worst = 0;
        for (Netlist.Element out : netlist.outputs()) {
            if (timed[out.id]) {
                worst = Math.max(worst, arrival[out.id].latest());
            }
        }
        return worst;
    }

    public List<Integer> criticalPath() {
        int end = -1;
        int worst = -1;
        for (Netlist.Element out : netlist.outputs()) {
            if (timed[out.id] && arrival[out.id].latest() > worst) {
                worst = arrival[out.id].latest();
                end = out.id;
            }
        }
        List<Integer> path = new ArrayList<>();
        for (int id = end; id >= 0; id = prev[id]) {
            path.add(0, id);
        }
        return path;
    }

    public int minimumClockPeriodGameTicks() {
        return criticalPathTicks() * 2;
    }

    public String render(){
        StringBuilder sb = new StringBuilder();
        sb.append("## Timing\n\n");
        sb.append("Critical path: ").append(criticalPathTicks())
          .append(" redstone ticks (").append(minimumClockPeriodGameTicks())
          .append(" game ticks minimum clock period)\n\n");

        List<Integer> path = criticalPath();
        if (!path.isEmpty()) {
            sb.append("    ");
            for (int i = 0; i < path.size(); i++) {
                Netlist.Element e = netlist.element(path.get(i));
                sb.append(i == 0 ? "" : " -> ").append(e.label);
                int d = delayOf(e);
                if (d > 0) {
                    sb.append('+').append(d);
                }
            }
            sb.append("\n\n");
        }

        if (warnings.isEmpty()) {
            sb.append("No input skew detected; every gate sees its inputs on the same tick.\n");
        } else {
            sb.append("### Possible glitches\n\n");
            for (String w : warnings) {
                sb.append("- ").append(w).append('\n');
            }
        }
        return sb.toString();
    }
}