package com.redstonelogics.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * An always-on readout in the corner: what is selected, what the last scan
 * found, and what the diagram is currently highlighting.
 */
public final class SelectionHud {

    private static final int LINE = 10;
    private static final int TEXT = 0xFFE6E6EE;
    private static final int DIM = 0xFF8A8A9A;
    private static final int BAD = 0xFFE4604A;
    private static final int WARN = 0xFFE0B050;
    private static final int GOOD = 0xFF5FBF7F;

    private SelectionHud() {
    }

    public static void render(GuiGraphicsExtractor g) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.gui.screen() != null) {
            return;
        }
        if (!ClientSelection.complete()
                && ClientAnalysis.state() == ClientAnalysis.State.EMPTY) {
            return;
        }

        var font = client.font;
        String top = ClientSelection.complete()
                ? ClientSelection.volume() + " blocks selected"
                : "no selection";
        String bottom = statusLine();
        String extra = WorldHighlight.active() ? "highlighting " + WorldHighlight.label() : null;

        int lines = extra == null ? 2 : 3;
        int width = Math.max(font.width(top), font.width(bottom));
        if (extra != null) {
            width = Math.max(width, font.width(extra));
        }

        int x = 6;
        int y = 6;
        g.fill(x - 3, y - 3, x + width + 3, y + lines * LINE + 2, 0x90000000);
        g.text(font, top, x, y, TEXT);
        g.text(font, bottom, x, y + LINE, statusColour());
        if (extra != null) {
            g.text(font, extra, x, y + 2 * LINE, WARN);
        }
    }

    private static String statusLine() {
        return switch (ClientAnalysis.state()) {
            case READY -> ClientAnalysis.result().summary();
            case SCANNING -> "analysing...";
            case FAILED -> String.valueOf(ClientAnalysis.error());
            case EMPTY -> "press V to pick, K to open";
        };
    }

    private static int statusColour() {
        return switch (ClientAnalysis.state()) {
            case READY -> GOOD;
            case SCANNING -> WARN;
            case FAILED -> BAD;
            case EMPTY -> DIM;
        };
    }
}
