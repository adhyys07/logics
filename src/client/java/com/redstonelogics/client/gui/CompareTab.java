package com.redstonelogics.client.gui;

import com.redstonelogics.analysis.Equivalence;
import com.redstonelogics.client.ClientAnalysis;
import com.redstonelogics.core.RegionSnapshot;
import com.redstonelogics.core.Schematics;
import com.redstonelogics.mapper.RedstoneLogicMapper;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Compares the current scan against a saved one.
 *
 * <p>The desktop app has had this since the exporters landed; in game it was
 * missing, which is the wrong way round - rebuilding a circuit smaller and
 * checking you did not break it is something you do standing in front of the
 * build, not afterwards at a desk.
 *
 * <p>Candidates are whatever is in the game's {@code redstonelogics} folder, so
 * the workflow is: scan the old build, Export -&gt; Snapshot, rebuild, scan
 * again, come here.
 */
public class CompareTab implements CircuitScreen.Tab {

    private final List<Path> candidates = new ArrayList<>();
    private int selected;
    private List<String> lines = List.of();
    private boolean equivalent;
    private boolean ran;
    private String problem;
    private int scroll;

    @Override
    public String title() {
        return "Compare";
    }

    @Override
    public void layout(CircuitScreen screen, int x, int y, int width, int height) {
        refresh();
        screen.addButton(Button.builder(Component.literal("< file"), b -> {
            if (!candidates.isEmpty()) {
                selected = (selected - 1 + candidates.size()) % candidates.size();
                ran = false;
            }
        }).bounds(x + 8, y + 22, 54, 20).build());

        screen.addButton(Button.builder(Component.literal("file >"), b -> {
            if (!candidates.isEmpty()) {
                selected = (selected + 1) % candidates.size();
                ran = false;
            }
        }).bounds(x + 66, y + 22, 54, 20).build());

        screen.addButton(Button.builder(Component.literal("Compare"), b -> run())
                .bounds(x + 128, y + 22, 70, 20).build());

        screen.addButton(Button.builder(Component.literal("Refresh"), b -> refresh())
                .bounds(x + 202, y + 22, 64, 20).build());
    }

    private void refresh() {
        candidates.clear();
        Path dir = FabricLoader.getInstance().getGameDir().resolve("redstonelogics");
        if (!Files.isDirectory(dir)) {
            return;                 // nothing exported yet, which is not an error
        }
        try (var stream = Files.list(dir)) {
            stream.filter(p -> Schematics.supported(p.getFileName().toString()))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(candidates::add);
        } catch (Exception e) {
            problem = "Could not list " + dir + ": " + e;
        }
        if (selected >= candidates.size()) {
            selected = 0;
        }
    }

    private void run() {
        ran = true;
        lines = List.of();
        problem = null;

        if (!ClientAnalysis.ready()) {
            problem = "Nothing scanned yet - press Pick first.";
            return;
        }
        if (candidates.isEmpty()) {
            problem = "No saved circuits. Use Export -> Snapshot to make one, or drop a "
                    + "file into the redstonelogics folder (" + Schematics.formats() + ").";
            return;
        }
        Path file = candidates.get(selected);
        try {
            RegionSnapshot other = Schematics.read(Files.readAllBytes(file),
                    file.getFileName().toString());
            RedstoneLogicMapper mapped = RedstoneLogicMapper.map(other);
            Equivalence.Result result = Equivalence.compare(
                    ClientAnalysis.result(), "this scan",
                    mapped, file.getFileName().toString());
            lines = result.lines();
            equivalent = result.equivalent();
            scroll = 0;
        } catch (Exception e) {
            problem = "Could not read " + file.getFileName() + ": " + e;
        }
    }

    @Override
    public boolean scroll(double mouseX, double mouseY, double amount) {
        scroll = Math.max(0, scroll - (int) Math.signum(amount) * 3);
        return true;
    }

    @Override
    public void render(GuiGraphicsExtractor g, int x, int y, int w, int h,
            int mouseX, int mouseY) {
        var font = Minecraft.getInstance().font;
        g.text(font, "Compare with a saved circuit", x + 8, y + 8, CircuitScreen.TEXT);

        String chosen = candidates.isEmpty()
                ? "no saved circuits in the redstonelogics folder"
                : (selected + 1) + "/" + candidates.size() + "  "
                        + candidates.get(selected).getFileName();
        g.text(font, chosen, x + 8, y + 50, CircuitScreen.DIM);

        if (problem != null) {
            int py = y + 66;
            for (var part : font.split(Component.literal(problem), w - 16)) {
                g.text(font, part, x + 8, py, CircuitScreen.ACCENT);
                py += 10;
            }
            return;
        }
        if (!ran) {
            g.text(font, "Pick a file and press Compare.", x + 8, y + 66, CircuitScreen.DIM);
            return;
        }

        int ly = y + 68;
        int shown = 0;
        for (int i = scroll; i < lines.size() && ly < y + h - 12; i++) {
            String line = lines.get(i);
            int colour = i == 0
                    ? (equivalent ? CircuitScreen.OK : CircuitScreen.ACCENT)
                    : line.contains("DIFFERENT") || line.contains("only one")
                            || line.contains("too wide")
                                    ? CircuitScreen.WARN
                                    : CircuitScreen.TEXT;
            g.text(font, line, x + 8, ly, colour);
            ly += 10;
            shown++;
        }
        if (scroll + shown < lines.size()) {
            g.text(font, "scroll for more", x + 8, y + h - 12, CircuitScreen.DIM);
        }
    }
}
