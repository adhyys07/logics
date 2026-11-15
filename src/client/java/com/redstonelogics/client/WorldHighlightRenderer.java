package com.redstonelogics.client;

import com.redstonelogics.core.Pos;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a wireframe box around whatever the diagram has selected, so clicking a
 * gate on screen shows you which blocks it came from in the world.
 *
 * <p>Uses the gizmo primitives the debug renderers draw with, submitted in the
 * BEFORE_GIZMOS phase, which keeps this clear of the terrain and entity passes.
 */
public final class WorldHighlightRenderer {

    private static final int COLOUR = 0xFFE4604A;
    private static final float WIDTH = 2.0f;
    /** Pushes the box just outside the block so it does not fight the surface. */
    private static final double GROW = 0.005;

    private WorldHighlightRenderer() {
    }

    public static void register() {
        LevelRenderEvents.BEFORE_GIZMOS.register(context -> {
            if (!WorldHighlight.active()) {
                return;
            }
            DrawableGizmoPrimitives gizmos = new DrawableGizmoPrimitives();
            for (Pos pos : WorldHighlight.positions()) {
                addBox(gizmos, pos);
            }
            if (!gizmos.isEmpty()) {
                gizmos.submit(context.submitNodeCollector(),
                        context.levelState().cameraRenderState, true);
            }
        });
    }

    /** The twelve edges of one block's cube. */
    private static void addBox(DrawableGizmoPrimitives gizmos, Pos pos) {
        double x0 = pos.x() - GROW;
        double y0 = pos.y() - GROW;
        double z0 = pos.z() - GROW;
        double x1 = pos.x() + 1 + GROW;
        double y1 = pos.y() + 1 + GROW;
        double z1 = pos.z() + 1 + GROW;

        // Bottom face.
        line(gizmos, x0, y0, z0, x1, y0, z0);
        line(gizmos, x1, y0, z0, x1, y0, z1);
        line(gizmos, x1, y0, z1, x0, y0, z1);
        line(gizmos, x0, y0, z1, x0, y0, z0);

        // Top face.
        line(gizmos, x0, y1, z0, x1, y1, z0);
        line(gizmos, x1, y1, z0, x1, y1, z1);
        line(gizmos, x1, y1, z1, x0, y1, z1);
        line(gizmos, x0, y1, z1, x0, y1, z0);

        // Uprights.
        line(gizmos, x0, y0, z0, x0, y1, z0);
        line(gizmos, x1, y0, z0, x1, y1, z0);
        line(gizmos, x1, y0, z1, x1, y1, z1);
        line(gizmos, x0, y0, z1, x0, y1, z1);
    }

    private static void line(DrawableGizmoPrimitives gizmos,
            double ax, double ay, double az, double bx, double by, double bz) {
        gizmos.addLine(new Vec3(ax, ay, az), new Vec3(bx, by, bz), COLOUR, WIDTH);
    }
}
