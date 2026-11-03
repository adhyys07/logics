package com.redstonelogics.graph;

import com.redstonelogics.core.BlockKind;
import com.redstonelogics.core.Direction;
import com.redstonelogics.core.Pos;
import com.redstonelogics.core.RegionSnapshot;
import com.redstonelogics.core.ScannedBlock;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Redstone power propagation for one fixed set of driver outputs.
 *
 * <p>The model it implements, stated plainly because it is the part that can be
 * wrong in interesting ways:
 * <ul>
 *   <li>Dust carries strength 15 down to 1, losing one per block.</li>
 *   <li>Dust connects to horizontally adjacent dust, to dust one step up if the
 *       block above the lower dust is not solid, and to dust one step down if the
 *       block above the lower dust is not solid.</li>
 *   <li>Dust weakly powers the block directly below it, and the blocks it is
 *       connected towards - so a wire running <em>past</em> a block does not
 *       power it.</li>
 *   <li>Torches, levers, buttons, repeaters and comparators strongly power one
 *       block each (above, into the support, out of the front). A powered block
 *       re-emits at its own strength into adjacent dust.</li>
 *   <li>A powered block, strongly or weakly, drives components next to it. Real
 *       redstone distinguishes the two for dust; this model does not, which is
 *       the main known simplification.</li>
 * </ul>
 *
 * <p>Quasi-connectivity, observers and container-reading comparators are out of
 * scope and are reported as warnings by {@link NetlistExtractor} instead.
 */
public final class PowerModel {

    private final RegionSnapshot snap;
    /** Component position -> the strength that component is currently emitting. */
    private final Map<Pos, Integer> driverOutputs;
    private final Map<Pos, Integer> dust = new HashMap<>();
    private final Map<Pos, Integer> strong = new HashMap<>();

    public PowerModel(RegionSnapshot snap, Map<Pos, Integer> driverOutputs) {
        this.snap = snap;
        this.driverOutputs = driverOutputs;
        computeStrongPower();
        propagateDust();
    }

    /** A model in which exactly one component drives, at full strength. */
    public static PowerModel isolating(RegionSnapshot snap, Pos driver) {
        return new PowerModel(snap, Map.of(driver, 15));
    }

    // ------------------------------------------------------------ strong power

    private void computeStrongPower() {
        for (Map.Entry<Pos, Integer> e : driverOutputs.entrySet()) {
            int out = e.getValue();
            if (out <= 0) {
                continue;
            }
            Pos p = e.getKey();
            ScannedBlock b = snap.at(p);
            Pos target = switch (b.kind()) {
                case TORCH -> p.up();
                case REPEATER, COMPARATOR -> p.offset(b.facing());
                case LEVER, BUTTON -> b.support(p);
                default -> null;
            };
            if (target != null && snap.at(target).isSolid()) {
                strong.merge(target, out, Math::max);
            }
        }
    }

    // -------------------------------------------------------------- dust power

    private void propagateDust() {
        // Buckets by strength let a plain BFS behave like Dijkstra here, because
        // every step costs exactly one.
        List<List<Pos>> buckets = new ArrayList<>(16);
        for (int i = 0; i < 16; i++) {
            buckets.add(new ArrayList<>());
        }

        for (Map.Entry<Pos, Integer> seed : seeds().entrySet()) {
            Pos p = seed.getKey();
            int s = seed.getValue();
            if (s > 0 && s > dust.getOrDefault(p, 0)) {
                dust.put(p, s);
                buckets.get(s).add(p);
            }
        }

        for (int s = 15; s >= 1; s--) {
            Deque<Pos> queue = new ArrayDeque<>(buckets.get(s));
            while (!queue.isEmpty()) {
                Pos p = queue.poll();
                if (dust.getOrDefault(p, 0) != s) {
                    continue; // superseded by a stronger path
                }
                for (Pos n : dustNeighbours(snap, p)) {
                    int next = s - 1;
                    if (next >= 1 && next > dust.getOrDefault(n, 0)) {
                        dust.put(n, next);
                        buckets.get(next).add(n);
                    }
                }
            }
        }
    }

    /** Dust positions that receive power directly from a driver or a powered block. */
    private Map<Pos, Integer> seeds() {
        Map<Pos, Integer> out = new HashMap<>();

        for (Map.Entry<Pos, Integer> e : driverOutputs.entrySet()) {
            int s = e.getValue();
            if (s <= 0) {
                continue;
            }
            Pos p = e.getKey();
            ScannedBlock b = snap.at(p);
            switch (b.kind()) {
                case TORCH -> {
                    seed(out, p.up(), 15);
                    for (Direction d : Direction.HORIZONTAL) {
                        seed(out, p.offset(d), 15);
                    }
                }
                case REPEATER, COMPARATOR -> seed(out, p.offset(b.facing()), s);
                case LEVER, BUTTON, REDSTONE_BLOCK -> {
                    for (Direction d : Direction.ALL) {
                        seed(out, p.offset(d), 15);
                    }
                }
                default -> {
                }
            }
        }

        // A powered block re-emits into every dust touching it.
        for (Map.Entry<Pos, Integer> e : strong.entrySet()) {
            for (Direction d : Direction.ALL) {
                seed(out, e.getKey().offset(d), e.getValue());
            }
        }
        return out;
    }

    private void seed(Map<Pos, Integer> out, Pos p, int strength) {
        if (snap.kindAt(p) == BlockKind.DUST) {
            out.merge(p, strength, Math::max);
        }
    }

    /** Dust cells a given dust cell passes current to. */
    public static List<Pos> dustNeighbours(RegionSnapshot snap, Pos p) {
        List<Pos> out = new ArrayList<>(8);
        boolean openAbove = !snap.at(p.up()).isSolid();
        for (Direction d : Direction.HORIZONTAL) {
            Pos side = p.offset(d);
            if (snap.kindAt(side) == BlockKind.DUST) {
                out.add(side);
            }
            // Climbing the side of a block, only if nothing caps this cell.
            if (openAbove && snap.kindAt(side.up()) == BlockKind.DUST) {
                out.add(side.up());
            }
            // Dropping down, only if nothing caps the lower cell.
            if (!snap.at(side).isSolid() && snap.kindAt(side.down()) == BlockKind.DUST) {
                out.add(side.down());
            }
        }
        return out;
    }

    // ----------------------------------------------------------------- queries

    public int dustAt(Pos p) {
        return dust.getOrDefault(p, 0);
    }

    /** Does the dust at {@code dustPos} have a connection pointing in {@code d}? */
    public boolean dustConnectsTowards(Pos dustPos, Direction d) {
        if (!d.horizontal() || snap.kindAt(dustPos) != BlockKind.DUST) {
            return false;
        }
        Pos n = dustPos.offset(d);
        ScannedBlock b = snap.at(n);
        switch (b.kind()) {
            case DUST:
                return true;
            case REPEATER:
            case COMPARATOR:
                // Only the back and front faces of these connect, never the sides.
                return b.facing() == d || b.facing() == d.opposite();
            case TORCH:
            case LEVER:
            case BUTTON:
            case LAMP:
            case MECHANISM:
            case REDSTONE_BLOCK:
                return true;
            case SOLID:
                // Dust climbing this block's side, if this cell is not capped.
                return snap.kindAt(n.up()) == BlockKind.DUST && !snap.at(dustPos.up()).isSolid();
            case AIR:
            default:
                return snap.kindAt(n.down()) == BlockKind.DUST;
        }
    }

    /** Weak power applied to a block by the dust around it. */
    public int weakPowerTo(Pos blockPos) {
        int best = 0;
        if (snap.kindAt(blockPos.up()) == BlockKind.DUST) {
            best = dustAt(blockPos.up()); // dust always powers the block beneath it
        }
        for (Direction d : Direction.HORIZONTAL) {
            Pos n = blockPos.offset(d);
            if (snap.kindAt(n) == BlockKind.DUST && dustConnectsTowards(n, d.opposite())) {
                best = Math.max(best, dustAt(n));
            }
        }
        return best;
    }

    public int strongPowerTo(Pos blockPos) {
        return strong.getOrDefault(blockPos, 0);
    }

    /** Total power applied to a block, which is what a torch on it reads. */
    public int blockPower(Pos blockPos) {
        return Math.max(strongPowerTo(blockPos), weakPowerTo(blockPos));
    }

    /**
     * The signal a component at {@code componentPos} receives from the neighbour
     * in direction {@code towards}.
     */
    public int signalInto(Pos componentPos, Direction towards) {
        Pos n = componentPos.offset(towards);
        ScannedBlock b = snap.at(n);
        return switch (b.kind()) {
            case DUST -> dustConnectsTowards(n, towards.opposite()) ? dustAt(n) : 0;
            case REPEATER, COMPARATOR ->
                    b.facing() == towards.opposite() ? driverOutputs.getOrDefault(n, 0) : 0;
            case TORCH, LEVER, BUTTON, REDSTONE_BLOCK -> driverOutputs.getOrDefault(n, 0);
            case SOLID -> blockPower(n);
            default -> 0;
        };
    }

    /** Power reaching a lamp, piston or other sink at this position. */
    public int mechanismPower(Pos p) {
        int best = blockPower(p);
        for (Direction d : Direction.ALL) {
            Pos n = p.offset(d);
            ScannedBlock b = snap.at(n);
            if (b.isSolid()) {
                // A powered block drives the mechanisms touching it.
                best = Math.max(best, blockPower(n) > 0 ? 15 : 0);
            } else {
                best = Math.max(best, signalInto(p, d));
            }
        }
        return best;
    }

    public RegionSnapshot snapshot() {
        return snap;
    }
}
