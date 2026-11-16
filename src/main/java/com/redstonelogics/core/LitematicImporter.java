package com.redstonelogics.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class LitematicImporter {

    private LitematicImporter() {
    }

    public static RegionSnapshot read(byte[] file, String name) throws IOException {
        Nbt.Tag root = Nbt.read(file);
        Nbt.Tag regions = root.compound("Regions");
        if (regions == null) {
            throw new IOException("No Regions tag; not a .litematic file?");
        }

        RegionSnapshot.Builder out = RegionSnapshot.builder(name);
        int placed = 0;

        for (Map.Entry<String, Object> entry : regions.values.entrySet()) {
            if (!(entry.getValue() instanceof Nbt.Tag region)) {
                continue;
            }
            placed += readRegion(region, out);
        }
        if (placed == 0) {
            throw new IOException("Every region was empty; nothing to analyse.");
        }
        return out.build();
    }

    private static int readRegion(Nbt.Tag region, RegionSnapshot.Builder out)
            throws IOException {

        int[] origin = xyz(region.compound("Position"));
        int[] size = xyz(region.compound("Size"));
        if (origin == null || size == null) {
            throw new IOException("Region is missing Position or Size.");
        }

        int width = Math.abs(size[0]);
        int height = Math.abs(size[1]);
        int length = Math.abs(size[2]);
        int ox = size[0] < 0 ? origin[0] + size[0] + 1 : origin[0];
        int oy = size[1] < 0 ? origin[1] + size[1] + 1 : origin[1];
        int oz = size[2] < 0 ? origin[2] + size[2] + 1 : origin[2];

        List<String> palette = palette(region);
        long[] packed = region.longs("BlockStates");
        if (palette.isEmpty() || packed == null) {
            return 0;
        }

        int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
        long mask = (1L << bits) - 1;
        int total = width * height * length;
        int placed = 0;

        for (int index = 0; index < total; index++) {
            int id = (int) (read(packed, (long) index * bits, bits) & mask);
            if (id < 0 || id >= palette.size()) {
                continue;
            }
            ScannedBlock block = SchematicImporter.classify(palette.get(id));
            if (block.kind() == BlockKind.AIR) {
                continue;
            }
            int y = index / (width * length);
            int rest = index % (width * length);
            int z = rest / width;
            int x = rest % width;

            Pos p = new Pos(ox + x, oy + y, oz + z);
            out.bounds(p);
            out.put(p, block);
            placed++;
        }
        return placed;
    }

    private static long read(long[] data, long bitOffset, int bits) {
        int word = (int) (bitOffset >>> 6);
        int offset = (int) (bitOffset & 63);
        if (word >= data.length) {
            return 0;
        }
        long value = data[word] >>> offset;
        int taken = 64 - offset;
        if (taken < bits && word + 1 < data.length) {
            value |= data[word + 1] << taken;
        }
        return value;
    }

    private static List<String> palette(Nbt.Tag region) {
        List<String> out = new ArrayList<>();
        for (Object item : region.list("BlockStatePalette")) {
            if (!(item instanceof Nbt.Tag state)) {
                out.add("minecraft:air");
                continue;
            }
            String id = state.string("Name");
            if (id == null) {
                out.add("minecraft:air");
                continue;
            }
            Nbt.Tag props = state.compound("Properties");
            if (props == null || props.values.isEmpty()) {
                out.add(id);
                continue;
            }
            StringBuilder sb = new StringBuilder(id).append('[');
            boolean first = true;
            for (Map.Entry<String, Object> e : props.values.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(e.getKey()).append('=').append(e.getValue());
            }
            out.add(sb.append(']').toString());
        }
        return out;
    }

    private static int[] xyz(Nbt.Tag tag) {
        if (tag == null) {
            return null;
        }
        return new int[] { tag.integer("x", 0), tag.integer("y", 0), tag.integer("z", 0) };
    }
}
