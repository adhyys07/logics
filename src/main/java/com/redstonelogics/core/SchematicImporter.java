package com.redstonelogics.core;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SchematicImporter {

    private SchematicImporter() {
    }

    public static RegionSnapshot read(byte[] file, String name) throws IOException {
        Nbt.Tag root = Nbt.read(file);
        Nbt.Tag s = root.compound("Schematic") != null ? root.compound("Schematic") : root;

        int width = s.integer("Width", 0);
        int height = s.integer("Height", 0);
        int length = s.integer("Length", 0);
        if (width <= 0 || height <= 0 || length <= 0) {
            throw new IOException("Schematic has no dimensions; not a Sponge schematic?");
        }

        Nbt.Tag blocks = s.compound("Blocks");            
        Nbt.Tag paletteTag = blocks != null ? blocks.compound("Palette") : s.compound("Palette");
        byte[] data = blocks != null ? blocks.bytes("Data") : s.bytes("BlockData");
        if (paletteTag == null || data == null) {
            throw new IOException("Schematic has no block palette or data.");
        }

        Map<Integer, String> byIndex = new HashMap<>();
        for (Map.Entry<String, Object> e : paletteTag.values.entrySet()) {
            if (e.getValue() instanceof Number n) {
                byIndex.put(n.intValue(), e.getKey());
            }
        }

        RegionSnapshot.Builder out = RegionSnapshot.builder(name);
        int cursor = 0;
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    int[] read = varint(data, cursor);
                    cursor = read[1];
                    String state = byIndex.get(read[0]);

                    Pos p = new Pos(x, y, z);
                    out.bounds(p);
                    ScannedBlock block = classify(state);
                    if (block.kind() != BlockKind.AIR) {
                        out.put(p, block);
                    }
                }
            }
        }
        return out.build();
    }

    private static int[] varint(byte[] data, int at) {
        int value = 0;
        int shift = 0;
        while (true) {
            int b = data[at++] & 0xFF;
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return new int[] { value, at };
            }
            shift += 7;
        }
    }

    static ScannedBlock classify(String state) {
        if (state == null) {
            return ScannedBlock.AIR;
        }
        int bracket = state.indexOf('[');
        String id = bracket < 0 ? state : state.substring(0, bracket);
        Map<String, String> props = properties(state, bracket);

        switch (id) {
            case "minecraft:redstone_wire":
                return ScannedBlock.dust(number(props, "power", 0));
            case "minecraft:redstone_torch":
                return ScannedBlock.torch(Direction.DOWN, bool(props, "lit", true));
            case "minecraft:redstone_wall_torch":
                return ScannedBlock.torch(
                        facing(props).opposite(), bool(props, "lit", true));
            case "minecraft:repeater":
                return ScannedBlock.repeater(facing(props),
                        bool(props, "powered", false),
                        number(props, "delay", 1),
                        bool(props, "locked", false));
            case "minecraft:comparator":
                return ScannedBlock.comparator(facing(props),
                        bool(props, "powered", false) ? 15 : 0,
                        "subtract".equals(props.get("mode")));
            case "minecraft:lever":
                return ScannedBlock.lever(attachment(props), bool(props, "powered", false));
            case "minecraft:redstone_block":
                return ScannedBlock.of(BlockKind.REDSTONE_BLOCK, id);
            case "minecraft:redstone_lamp":
                return ScannedBlock.of(BlockKind.LAMP, id);
            case "minecraft:air":
            case "minecraft:cave_air":
            case "minecraft:void_air":
                return ScannedBlock.AIR;
            default:
                break;
        }
        if (id.endsWith("_button")) {
            return ScannedBlock.button(attachment(props), bool(props, "powered", false));
        }
        if (MECHANISMS.contains(id) || id.endsWith("_door") || id.endsWith("_trapdoor")
                || id.endsWith("_fence_gate")) {
            return ScannedBlock.of(BlockKind.MECHANISM, id);
        }
        return NON_SOLID.contains(id) ? ScannedBlock.AIR : ScannedBlock.solid(id);
    }

    private static final List<String> MECHANISMS = List.of(
            "minecraft:piston", "minecraft:sticky_piston", "minecraft:dispenser",
            "minecraft:dropper", "minecraft:note_block", "minecraft:observer",
            "minecraft:tnt", "minecraft:hopper");

    private static final List<String> NON_SOLID = List.of(
            "minecraft:glass", "minecraft:glowstone", "minecraft:water",
            "minecraft:lava", "minecraft:barrier", "minecraft:torch",
            "minecraft:wall_torch", "minecraft:rail", "minecraft:ladder");

    private static Map<String, String> properties(String state, int bracket) {
        Map<String, String> out = new HashMap<>();
        if (bracket < 0 || !state.endsWith("]")) {
            return out;
        }
        for (String pair : state.substring(bracket + 1, state.length() - 1).split(",")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                out.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
            }
        }
        return out;
    }

    private static Direction facing(Map<String, String> props) {
        Direction d = Direction.byName(props.getOrDefault("facing", "north"));
        return d == null ? Direction.NORTH : d;
    }

    private static Direction attachment(Map<String, String> props) {
        return switch (props.getOrDefault("face", "floor")) {
            case "ceiling" -> Direction.UP;
            case "wall" -> facing(props).opposite();
            default -> Direction.DOWN;
        };
    }

    private static int number(Map<String, String> props, String key, int fallback) {
        try {
            return Integer.parseInt(props.getOrDefault(key, String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean bool(Map<String, String> props, String key, boolean fallback) {
        String v = props.get(key);
        return v == null ? fallback : Boolean.parseBoolean(v);
    }
}
