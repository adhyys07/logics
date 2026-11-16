package com.redstonelogics.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Picks the right reader for a saved circuit by its file extension. */
public final class Schematics {

    private Schematics() {
    }

    public static boolean supported(String fileName) {
        String n = fileName.toLowerCase(Locale.ROOT);
        // .graph.json is a diagram for the web viewer, not a block snapshot;
        // offering it would only produce a confusing parse error.
        if (n.endsWith(".graph.json")) {
            return false;
        }
        return n.endsWith(".schem") || n.endsWith(".litematic") || n.endsWith(".json");
    }

    /** Human-readable list of what {@link #read} accepts. */
    public static String formats() {
        return ".rlogic.json snapshot, Sponge .schem, Litematica .litematic";
    }

    public static RegionSnapshot read(byte[] bytes, String fileName) throws IOException {
        String n = fileName.toLowerCase(Locale.ROOT);
        if (n.endsWith(".litematic")) {
            return LitematicImporter.read(bytes, fileName);
        }
        if (n.endsWith(".schem")) {
            return SchematicImporter.read(bytes, fileName);
        }
        return RegionSnapshot.fromJson(new String(bytes, StandardCharsets.UTF_8));
    }
}
