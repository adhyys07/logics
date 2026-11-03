package com.redstonelogics.core;

/**
 * One block as the world scanner saw it.
 *
 * <p>{@code facing} means different things per kind, which keeps the record flat:
 * <ul>
 *   <li>{@link BlockKind#REPEATER}, {@link BlockKind#COMPARATOR} - the output (front) face.</li>
 *   <li>{@link BlockKind#TORCH}, {@link BlockKind#LEVER}, {@link BlockKind#BUTTON} -
 *       the direction from the component towards the block it is attached to.</li>
 *   <li>everything else - {@link Direction#UP}, unused.</li>
 * </ul>
 */
public record ScannedBlock(
        BlockKind kind,
        Direction facing,
        /** Torch lit, repeater powered, lever flipped, button held. */
        boolean on,
        /** Dust strength, or a comparator's output strength, 0-15. */
        int power,
        /** Repeater delay in redstone ticks, 1-4. */
        int delay,
        /** Comparator in subtract mode rather than compare mode. */
        boolean subtract,
        /** Repeater locked by a powered repeater at its side. */
        boolean locked,
        /** Namespaced id, carried through purely so reports can name the block. */
        String blockId) {

    public static final ScannedBlock AIR =
            new ScannedBlock(BlockKind.AIR, Direction.UP, false, 0, 1, false, false, "minecraft:air");

    public static ScannedBlock of(BlockKind kind, String id) {
        return new ScannedBlock(kind, Direction.UP, false, 0, 1, false, false, id);
    }

    public static ScannedBlock solid(String id) {
        return of(BlockKind.SOLID, id);
    }

    public static ScannedBlock dust(int power) {
        return new ScannedBlock(BlockKind.DUST, Direction.UP, power > 0, power, 1, false, false,
                "minecraft:redstone_wire");
    }

    public static ScannedBlock torch(Direction towardsSupport, boolean lit) {
        return new ScannedBlock(BlockKind.TORCH, towardsSupport, lit, lit ? 15 : 0, 1, false, false,
                towardsSupport == Direction.DOWN ? "minecraft:redstone_torch" : "minecraft:redstone_wall_torch");
    }

    public static ScannedBlock repeater(Direction front, boolean powered, int delay, boolean locked) {
        return new ScannedBlock(BlockKind.REPEATER, front, powered, powered ? 15 : 0, delay, false, locked,
                "minecraft:repeater");
    }

    public static ScannedBlock comparator(Direction front, int output, boolean subtract) {
        return new ScannedBlock(BlockKind.COMPARATOR, front, output > 0, output, 1, subtract, false,
                "minecraft:comparator");
    }

    public static ScannedBlock lever(Direction towardsSupport, boolean on) {
        return new ScannedBlock(BlockKind.LEVER, towardsSupport, on, on ? 15 : 0, 1, false, false,
                "minecraft:lever");
    }

    public static ScannedBlock button(Direction towardsSupport, boolean pressed) {
        return new ScannedBlock(BlockKind.BUTTON, towardsSupport, pressed, pressed ? 15 : 0, 1, false, false,
                "minecraft:stone_button");
    }

    /** The block this component is mounted on, for torches, levers and buttons. */
    public Pos support(Pos self) {
        return self.offset(facing);
    }

    /** Where a repeater or comparator reads its main input from. */
    public Pos back(Pos self) {
        return self.offset(facing.opposite());
    }

    public boolean isSolid() {
        return kind == BlockKind.SOLID;
    }
}
