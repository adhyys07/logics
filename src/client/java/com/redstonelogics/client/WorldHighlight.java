package com.redstonelogics.client;

import com.redstonelogics.core.Pos;

import java.util.List;

/**
 * Which block positions the diagram wants picked out in the world.
 *
 * <p>Held apart from any renderer on purpose: the GUI sets it without touching
 * a rendering API, so the diagram stays useful even with no overlay installed.
 */
public final class WorldHighlight {

    private static volatile List<Pos> positions = List.of();
    private static volatile String label = "";

    private WorldHighlight() {
    }

    public static void set(List<Pos> blocks, String what) {
        positions = List.copyOf(blocks);
        label = what;
    }

    public static void clear() {
        positions = List.of();
        label = "";
    }

    public static List<Pos> positions() {
        return positions;
    }

    public static String label() {
        return label;
    }

    public static boolean active() {
        return !positions.isEmpty();
    }
}
