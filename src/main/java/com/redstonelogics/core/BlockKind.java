package com.redstonelogics.core;

/**
 * The only block distinctions the logic analysis cares about. Everything the
 * scanner cannot classify collapses into {@link #AIR} (ignorable) or
 * {@link #SOLID} (can carry power, blocks vertical dust connections).
 */
public enum BlockKind {
    /** Air, or any non-solid block that neither carries power nor blocks dust. */
    AIR,
    /** A full block that conducts redstone power. */
    SOLID,
    /** Redstone dust. */
    DUST,
    /** Redstone torch, floor or wall mounted. Inverts the power of its support block. */
    TORCH,
    /** Repeater: buffer with a 1-4 tick delay, lockable from the side. */
    REPEATER,
    /** Comparator, in compare or subtract mode. */
    COMPARATOR,
    /** Lever: a latching input. */
    LEVER,
    /** Button: a momentary input. */
    BUTTON,
    /** Block of redstone: a constant 15. */
    REDSTONE_BLOCK,
    /** Redstone lamp: a pure sink, drawn as a diagram output. */
    LAMP,
    /** Piston, door, dispenser, note block ... any other sink worth drawing. */
    MECHANISM;

    /** Does this kind act as a driver that other components can read? */
    public boolean isDriver() {
        return this == DUST || this == TORCH || this == REPEATER || this == COMPARATOR
                || this == LEVER || this == BUTTON || this == REDSTONE_BLOCK;
    }

    /** Does dust form a connection when it sits next to this kind? */
    public boolean dustConnectable() {
        return this != AIR && this != SOLID;
    }
}
