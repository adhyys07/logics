package com.redstonelogics.core;

/** An immutable block position. */
public record Pos(int x, int y, int z) implements Comparable<Pos> {

    public Pos offset(Direction d) {
        return new Pos(x + d.dx, y + d.dy, z + d.dz);
    }

    public Pos offset(Direction d, int times) {
        return new Pos(x + d.dx * times, y + d.dy * times, z + d.dz * times);
    }

    public Pos up() {
        return new Pos(x, y + 1, z);
    }

    public Pos down() {
        return new Pos(x, y - 1, z);
    }

    @Override
    public int compareTo(Pos o) {
        if (y != o.y) return Integer.compare(y, o.y);
        if (x != o.x) return Integer.compare(x, o.x);
        return Integer.compare(z, o.z);
    }

    @Override
    public String toString() {
        return x + "," + y + "," + z;
    }
}
