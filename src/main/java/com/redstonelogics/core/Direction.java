package com.redstonelogics.core;

/**
 * The six block faces, independent of any Minecraft class so that every stage
 * after the world scan can run (and be tested) without the game on the classpath.
 */
public enum Direction {
    DOWN(0, -1, 0),
    UP(0, 1, 0),
    NORTH(0, 0, -1),
    SOUTH(0, 0, 1),
    WEST(-1, 0, 0),
    EAST(1, 0, 0);

    public static final Direction[] ALL = values();
    public static final Direction[] HORIZONTAL = { NORTH, EAST, SOUTH, WEST };

    public final int dx;
    public final int dy;
    public final int dz;

    Direction(int dx, int dy, int dz) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }

    public boolean horizontal() {
        return dy == 0;
    }

    public Direction opposite() {
        return switch (this) {
            case DOWN -> UP;
            case UP -> DOWN;
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case EAST -> WEST;
        };
    }

    /** Quarter turn clockwise looking down; used to find a comparator's two side inputs. */
    public Direction rotateY() {
        return switch (this) {
            case NORTH -> EAST;
            case EAST -> SOUTH;
            case SOUTH -> WEST;
            case WEST -> NORTH;
            default -> this;
        };
    }

    public static Direction byName(String name) {
        return valueOf(name.toUpperCase(java.util.Locale.ROOT));
    }
}
