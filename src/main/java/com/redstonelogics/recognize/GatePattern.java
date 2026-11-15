package com.redstonelogics.recognize;

import java.util.ArrayList;
import java.util.List;

/**
 * A named multi-gate design, as a small labelled DAG to search for in a
 * {@link LogicDiagram}.
 *
 * <p>Nodes are numbered from 0. Edges run {@code from -> to} between pattern
 * nodes; anything a pattern node reads that is not itself in the pattern is a
 * free input and is left unconstrained, which is what lets a pattern match
 * wherever its shape occurs rather than in one circuit only.
 *
 * <p>Edges alone cannot describe every design. A half adder is an XOR and an
 * AND with no connection between them at all - what makes it a half adder is
 * that both read the <em>same</em> pair of free inputs. {@link #shared} carries
 * that constraint, without which such a pattern would match any unrelated XOR
 * and AND in the circuit.
 */
public final class GatePattern {

    /** A wildcard, for nodes whose exact type does not matter. */
    public static final GateType ANY = null;

    public record Edge(int from, int to) {
    }

    /** Two pattern nodes that must read at least {@code count} drivers in common. */
    public record Shared(int a, int b, int count) {
    }

    public final String name;
    public final List<GateType> nodes;
    public final List<Edge> edges;
    public final List<Shared> shared;
    /** Nodes whose output may legitimately be read from outside the match. */
    public final List<Integer> exported;

    private GatePattern(String name, List<GateType> nodes, List<Edge> edges,
            List<Shared> shared, List<Integer> exported) {
        this.name = name;
        this.nodes = nodes;
        this.edges = edges;
        this.shared = shared;
        this.exported = exported;
    }

    public static Builder named(String name) {
        return new Builder(name);
    }

    public int inDegree(int node) {
        int n = 0;
        for (Edge e : edges) {
            if (e.to() == node) {
                n++;
            }
        }
        return n;
    }

    public static final class Builder {
        private final String name;
        private final List<GateType> nodes = new ArrayList<>();
        private final List<Edge> edges = new ArrayList<>();
        private final List<Shared> shared = new ArrayList<>();
        private final List<Integer> exported = new ArrayList<>();

        private Builder(String name) {
            this.name = name;
        }

        /** @return the index of the new node */
        public int node(GateType type) {
            nodes.add(type);
            return nodes.size() - 1;
        }

        public Builder edge(int from, int to) {
            edges.add(new Edge(from, to));
            return this;
        }

        /** Requires two nodes to read at least {@code count} common drivers. */
        public Builder shares(int a, int b, int count) {
            shared.add(new Shared(a, b, count));
            return this;
        }

        /** Marks a node as an output of the composite. */
        public Builder export(int node) {
            exported.add(node);
            return this;
        }

        public GatePattern build() {
            return new GatePattern(name, List.copyOf(nodes), List.copyOf(edges),
                    List.copyOf(shared), List.copyOf(exported));
        }
    }

    // ------------------------------------------------------------ the library

    /**
     * Full adder:
     * <pre>
     *   x1 = A XOR B      a1 = A AND B
     *   S  = x1 XOR Cin   a2 = x1 AND Cin
     *                     Cout = a1 OR a2
     * </pre>
     * x1 and a1 must share both of A and B, or any XOR feeding an unrelated
     * AND chain would match.
     */
    public static GatePattern fullAdder() {
        Builder b = named("full adder");
        int x1 = b.node(GateType.XOR);
        int sum = b.node(GateType.XOR);
        int a1 = b.node(GateType.AND);
        int a2 = b.node(GateType.AND);
        int cout = b.node(GateType.OR);
        return b.edge(x1, sum).edge(x1, a2).edge(a1, cout).edge(a2, cout)
                .shares(x1, a1, 2)
                .export(sum).export(cout).build();
    }

    /** A gated SR latch: set and reset both qualified by an enable. */
    public static GatePattern gatedLatch() {
        Builder b = named("gated SR latch");
        int gs = b.node(GateType.AND);
        int gr = b.node(GateType.AND);
        int latch = b.node(GateType.SR_LATCH);
        return b.edge(gs, latch).edge(gr, latch)
                .shares(gs, gr, 1)                 // the shared enable
                .export(latch).build();
    }

    /** 2:1 multiplexer: two AND arms joined by an OR, one arm inverted select. */
    public static GatePattern mux2to1() {
        Builder b = named("2:1 mux");
        int notSel = b.node(GateType.NOT);
        int armA = b.node(GateType.AND);
        int armB = b.node(GateType.AND);
        int or = b.node(GateType.OR);
        return b.edge(notSel, armA).edge(armA, or).edge(armB, or)
                .export(or).build();
    }

    /** Half adder: S = A XOR B, C = A AND B, both over the same two inputs. */
    public static GatePattern halfAdder() {
        Builder b = named("half adder");
        int sum = b.node(GateType.XOR);
        int carry = b.node(GateType.AND);
        return b.shares(sum, carry, 2).export(sum).export(carry).build();
    }

    /**
     * Largest first, so {@link SubgraphMatcher} reports a full adder rather
     * than the half adder hiding inside it.
     */
    public static List<GatePattern> library() {
        return List.of(fullAdder(), mux2to1(), gatedLatch(), halfAdder());
    }
}
