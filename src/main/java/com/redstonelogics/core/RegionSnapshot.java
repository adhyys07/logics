package com.redstonelogics.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Everything the analysis knows about a scanned region: a sparse map of
 * interesting blocks plus the bounds they came from.
 *
 * <p>This is the product boundary. The mod writes it, the desktop app reads it,
 * and both run the same analysis over it. Anything outside the bounds, and any
 * position not present, reads as {@link ScannedBlock#AIR} - so a circuit that
 * runs off the edge of the selection is analysed as if it were cut there.
 */
public final class RegionSnapshot {

    public static final String FORMAT = "redstonelogics.snapshot/1";

    private final Map<Pos, ScannedBlock> blocks;
    private final Pos min;
    private final Pos max;
    private final String world;

    private RegionSnapshot(Map<Pos, ScannedBlock> blocks, Pos min, Pos max, String world) {
        this.blocks = blocks;
        this.min = min;
        this.max = max;
        this.world = world;
    }

    public static Builder builder(String world) {
        return new Builder(world);
    }

    public ScannedBlock at(Pos p) {
        ScannedBlock b = blocks.get(p);
        return b == null ? ScannedBlock.AIR : b;
    }

    public ScannedBlock at(Pos p, Direction d) {
        return at(p.offset(d));
    }

    public BlockKind kindAt(Pos p) {
        return at(p).kind();
    }

    public boolean inBounds(Pos p) {
        return p.x() >= min.x() && p.x() <= max.x()
                && p.y() >= min.y() && p.y() <= max.y()
                && p.z() >= min.z() && p.z() <= max.z();
    }

    /** Positions of every recorded block, in a stable order. */
    public List<Pos> positions() {
        List<Pos> out = new ArrayList<>(blocks.keySet());
        Collections.sort(out);
        return out;
    }

    public List<Pos> positionsOf(BlockKind kind) {
        List<Pos> out = new ArrayList<>();
        for (Pos p : positions()) {
            if (blocks.get(p).kind() == kind) {
                out.add(p);
            }
        }
        return out;
    }

    public Map<BlockKind, Integer> inventory() {
        Map<BlockKind, Integer> counts = new LinkedHashMap<>();
        for (ScannedBlock b : blocks.values()) {
            counts.merge(b.kind(), 1, Integer::sum);
        }
        return counts;
    }

    public int size() {
        return blocks.size();
    }

    public Pos min() {
        return min;
    }

    public Pos max() {
        return max;
    }

    public String world() {
        return world;
    }

    // ------------------------------------------------------------------ build

    public static final class Builder {
        private final Map<Pos, ScannedBlock> blocks = new TreeMap<>();
        private final String world;
        private int minX = Integer.MAX_VALUE;
        private int minY = Integer.MAX_VALUE;
        private int minZ = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE;
        private int maxY = Integer.MIN_VALUE;
        private int maxZ = Integer.MIN_VALUE;

        Builder(String world) {
            this.world = world;
        }

        /** Records a block. Air is skipped so the snapshot stays sparse. */
        public Builder put(Pos p, ScannedBlock block) {
            if (block.kind() != BlockKind.AIR) {
                blocks.put(p, block);
            }
            return bounds(p);
        }

        /** Grows the bounds without recording a block, so an empty scan still has a volume. */
        public Builder bounds(Pos p) {
            minX = Math.min(minX, p.x());
            minY = Math.min(minY, p.y());
            minZ = Math.min(minZ, p.z());
            maxX = Math.max(maxX, p.x());
            maxY = Math.max(maxY, p.y());
            maxZ = Math.max(maxZ, p.z());
            return this;
        }

        public RegionSnapshot build() {
            if (blocks.isEmpty() && minX == Integer.MAX_VALUE) {
                Pos zero = new Pos(0, 0, 0);
                return new RegionSnapshot(Map.of(), zero, zero, world);
            }
            return new RegionSnapshot(Map.copyOf(blocks),
                    new Pos(minX, minY, minZ), new Pos(maxX, maxY, maxZ), world);
        }
    }

    // ------------------------------------------------------------------- json

    public String toJson() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("format", FORMAT);
        root.put("world", world);
        root.put("min", List.of(min.x(), min.y(), min.z()));
        root.put("max", List.of(max.x(), max.y(), max.z()));

        List<Object> list = new ArrayList<>();
        for (Pos p : positions()) {
            ScannedBlock b = blocks.get(p);
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("p", List.of(p.x(), p.y(), p.z()));
            o.put("k", b.kind().name());
            o.put("f", b.facing().name());
            if (b.on()) {
                o.put("on", Boolean.TRUE);
            }
            if (b.power() != 0) {
                o.put("pw", b.power());
            }
            if (b.delay() != 1) {
                o.put("d", b.delay());
            }
            if (b.subtract()) {
                o.put("sub", Boolean.TRUE);
            }
            if (b.locked()) {
                o.put("lk", Boolean.TRUE);
            }
            o.put("id", b.blockId());
            list.add(o);
        }
        root.put("blocks", list);
        return Json.write(root);
    }

    public static RegionSnapshot fromJson(String text) {
        Map<String, Object> root = Json.asObject(Json.parse(text));
        String format = Json.str(root, "format", "");
        if (!FORMAT.equals(format)) {
            throw new IllegalArgumentException("unsupported snapshot format: " + format);
        }
        Builder b = builder(Json.str(root, "world", "unknown"));
        b.bounds(readPos(Json.asList(root.get("min"))));
        b.bounds(readPos(Json.asList(root.get("max"))));

        for (Object entry : Json.asList(root.get("blocks"))) {
            Map<String, Object> o = Json.asObject(entry);
            Pos p = readPos(Json.asList(o.get("p")));
            b.put(p, new ScannedBlock(
                    BlockKind.valueOf(Json.str(o, "k", "SOLID")),
                    Direction.byName(Json.str(o, "f", "UP")),
                    Json.bool(o, "on", false),
                    Json.integer(o, "pw", 0),
                    Json.integer(o, "d", 1),
                    Json.bool(o, "sub", false),
                    Json.bool(o, "lk", false),
                    Json.str(o, "id", "minecraft:unknown")));
        }
        return b.build();
    }

    private static Pos readPos(List<Object> l) {
        return new Pos(((Number) l.get(0)).intValue(),
                ((Number) l.get(1)).intValue(),
                ((Number) l.get(2)).intValue());
    }
}
