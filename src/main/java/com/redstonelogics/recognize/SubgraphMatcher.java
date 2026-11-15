package com.redstonelogics.recognize;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds composite designs in a {@link LogicDiagram} by subgraph isomorphism.
 *
 * <p>Complements the truth-table matching in {@link GateRecognizer}: that
 * identifies what a single gate computes, this identifies what a group of gates
 * <em>is</em>. A full adder has no single-gate signature, so it can only be
 * found structurally.
 *
 * <p>Plain backtracking rather than VF2. Patterns are a handful of nodes and
 * the type check rejects most candidates immediately, so the search is bounded
 * by gates of a matching type rather than by pattern size.
 */
public final class SubgraphMatcher {

    /** One located instance: {@code gates[i]} is the gate matching pattern node i. */
    public record Match(GatePattern pattern, int[] gates) {

        public String describe(LogicDiagram diagram) {
            StringBuilder sb = new StringBuilder(pattern.name);
            sb.append(" at ").append(diagram.gate(gates[0]).origin).append(" over ");
            for (int i = 0; i < gates.length; i++) {
                sb.append(i == 0 ? "" : ", ").append(diagram.gate(gates[i]).label);
            }
            return sb.toString();
        }
    }

    private final LogicDiagram diagram;
    private final List<List<Integer>> consumers;

    private SubgraphMatcher(LogicDiagram diagram) {
        this.diagram = diagram;
        this.consumers = diagram.consumers();
    }

    /**
     * Every non-overlapping match, largest patterns first so a big design wins
     * over the smaller ones nested inside it.
     */
    public static List<Match> findAll(LogicDiagram diagram, List<GatePattern> patterns) {
        SubgraphMatcher m = new SubgraphMatcher(diagram);
        List<GatePattern> ordered = new ArrayList<>(patterns);
        ordered.sort((a, b) -> b.nodes.size() - a.nodes.size());

        List<Match> found = new ArrayList<>();
        Set<Integer> claimed = new HashSet<>();
        for (GatePattern p : ordered) {
            for (Match match : m.find(p)) {
                boolean clash = false;
                for (int g : match.gates()) {
                    if (claimed.contains(g)) {
                        clash = true;
                        break;
                    }
                }
                if (clash) {
                    continue;
                }
                for (int g : match.gates()) {
                    claimed.add(g);
                }
                found.add(match);
            }
        }
        return found;
    }

    public List<Match> find(GatePattern pattern) {
        List<Match> out = new ArrayList<>();
        if (pattern.nodes.isEmpty()) {
            return out;
        }
        int[] mapping = new int[pattern.nodes.size()];
        Arrays.fill(mapping, -1);
        extend(pattern, 0, mapping, out);
        return out;
    }

    private void extend(GatePattern p, int node, int[] mapping, List<Match> out) {
        if (node == p.nodes.size()) {
            if (complete(p, mapping)) {
                out.add(new Match(p, mapping.clone()));
            }
            return;
        }
        for (LogicDiagram.Gate g : diagram.gates()) {
            if (!fits(p, node, g, mapping)) {
                continue;
            }
            mapping[node] = g.id;
            extend(p, node + 1, mapping, out);
            mapping[node] = -1;
        }
    }

    /** Constraints that can only be checked once every node is placed. */
    private boolean complete(GatePattern p, int[] mapping) {
        for (GatePattern.Shared s : p.shared) {
            if (commonDrivers(mapping[s.a()], mapping[s.b()]) < s.count()) {
                return false;
            }
        }
        // An internal node's output must not escape the match, or collapsing it
        // would silently drop a connection. Exported nodes are exempt.
        for (int i = 0; i < mapping.length; i++) {
            if (p.exported.contains(i)) {
                continue;
            }
            for (int consumer : consumers.get(mapping[i])) {
                boolean inside = false;
                for (int m : mapping) {
                    if (m == consumer) {
                        inside = true;
                        break;
                    }
                }
                if (!inside) {
                    return false;
                }
            }
        }
        return true;
    }

    /** How many drivers two gates read in common. */
    private int commonDrivers(int gateA, int gateB) {
        Set<Integer> a = new HashSet<>();
        for (LogicDiagram.Ref r : diagram.gate(gateA).inputs) {
            a.add(r.gate());
        }
        int n = 0;
        Set<Integer> counted = new HashSet<>();
        for (LogicDiagram.Ref r : diagram.gate(gateB).inputs) {
            if (a.contains(r.gate()) && counted.add(r.gate())) {
                n++;
            }
        }
        return n;
    }

    private boolean fits(GatePattern p, int node, LogicDiagram.Gate g, int[] mapping) {
        if (p.nodes.get(node) != GatePattern.ANY && g.type != p.nodes.get(node)) {
            return false;
        }
        for (int i = 0; i < node; i++) {
            if (mapping[i] == g.id) {
                return false;                      // the mapping must be injective
            }
        }
        // A gate cannot satisfy a node that expects more inputs than it has.
        if (g.inputs.size() < p.inDegree(node)) {
            return false;
        }

        // Every pattern edge whose other end is already placed must hold. Input
        // order is not constrained, which is correct for commutative gates; an
        // INHIBIT pattern would need pin-aware edges.
        for (GatePattern.Edge e : p.edges) {
            if (e.to() == node && mapping[e.from()] >= 0
                    && !reads(g, mapping[e.from()])) {
                return false;
            }
            if (e.from() == node && mapping[e.to()] >= 0
                    && !reads(diagram.gate(mapping[e.to()]), g.id)) {
                return false;
            }
        }
        return true;
    }

    private static boolean reads(LogicDiagram.Gate consumer, int driverGate) {
        for (LogicDiagram.Ref r : consumer.inputs) {
            if (r.gate() == driverGate) {
                return true;
            }
        }
        return false;
    }
}
