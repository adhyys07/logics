package com.redstonelogics.client.gui;

import com.redstonelogics.analysis.DeadLogic;
import com.redstonelogics.analysis.TimingAnalysis;
import com.redstonelogics.client.ClientAnalysis;
import com.redstonelogics.export.ArduinoExporter;
import com.redstonelogics.export.CsvExporter;
import com.redstonelogics.export.DiagramPainter;
import com.redstonelogics.export.DotExporter;
import com.redstonelogics.export.NetlistReport;
import com.redstonelogics.export.PngExporter;
import com.redstonelogics.export.SvgExporter;
import com.redstonelogics.export.VerilogExporter;
import com.redstonelogics.mapper.RedstoneLogicMapper;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Writes every export format into the game directory. */
public class ExportTab implements CircuitScreen.Tab {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private String message = "";
    private boolean failed;

    @Override
    public String title() {
        return "Export";
    }

    @Override
    public void layout(CircuitScreen screen, int x, int y, int width, int height) {
        int bw = 96;
        int bh = 20;
        int col0 = x + 8;

        int by = y + 26;
        screen.addButton(button("SVG", col0, by, bw, bh, "svg"));
        screen.addButton(button("PNG", col0 + bw + 4, by, bw, bh, "png"));
        screen.addButton(button("Arduino", col0 + 2 * (bw + 4), by, bw, bh, "ino"));
        screen.addButton(button("Verilog", col0 + 3 * (bw + 4), by, bw, bh, "v"));

        by += bh + 4;
        screen.addButton(button("Graphviz", col0, by, bw, bh, "dot"));
        screen.addButton(button("Mermaid", col0 + bw + 4, by, bw, bh, "mmd"));
        screen.addButton(button("Truth CSV", col0 + 2 * (bw + 4), by, bw, bh, "csv"));
        screen.addButton(button("Snapshot", col0 + 3 * (bw + 4), by, bw, bh, "json"));

        by += bh + 4;
        screen.addButton(button("Report", col0, by, bw, bh, "md"));
        screen.addButton(button("Everything", col0 + bw + 4, by, bw, bh, "all"));
    }

    private Button button(String label, int x, int y, int w, int h, String kind) {
        return Button.builder(Component.literal(label), b -> write(kind))
                .bounds(x, y, w, h).build();
    }

    private void write(String kind) {
        if (!ClientAnalysis.ready()) {
            failed = true;
            message = "Nothing to export yet - press Pick first.";
            return;
        }
        RedstoneLogicMapper mapped = ClientAnalysis.result();
        String base = "circuit-" + LocalDateTime.now().format(STAMP);
        Path dir = FabricLoader.getInstance().getGameDir().resolve("redstonelogics");
        String title = base + "  " + mapped.summary();
        boolean all = "all".equals(kind);
        int written = 0;

        try {
            Files.createDirectories(dir);
            if (all || kind.equals("svg")) {
                text(dir.resolve(base + ".svg"), SvgExporter.render(mapped.diagram(), title));
                written++;
            }
            if (all || kind.equals("png")) {
                Files.write(dir.resolve(base + ".png"), PngExporter.toBytes(
                        mapped.diagram(), title, DiagramPainter.Palette.LIGHT, 2.0));
                written++;
            }
            if (all || kind.equals("ino")) {
                text(dir.resolve(base + ".ino"), ArduinoExporter.render(mapped.diagram(), title));
                written++;
            }
            if (all || kind.equals("v")) {
                text(dir.resolve(base + ".v"), VerilogExporter.render(mapped, "circuit"));
                written++;
            }
            if (all || kind.equals("dot")) {
                text(dir.resolve(base + ".dot"), DotExporter.dot(mapped.diagram(), title));
                written++;
            }
            if (all || kind.equals("mmd")) {
                text(dir.resolve(base + ".mmd"), DotExporter.mermaid(mapped.diagram()));
                written++;
            }
            if (all || kind.equals("csv")) {
                text(dir.resolve(base + ".csv"), CsvExporter.render(mapped));
                written++;
            }
            if (all || kind.equals("json")) {
                text(dir.resolve(base + ".rlogic.json"), mapped.snapshot().toJson());
                written++;
            }
            if (all || kind.equals("md")) {
                // The timing and dead-logic sections are not part of NetlistReport
                // yet, so they are appended here.
                String report = NetlistReport.render(mapped, title)
                        + "\n" + TimingAnalysis.of(mapped.netlist()).render()
                        + "\n" + DeadLogic.render(DeadLogic.of(mapped.netlist()));
                text(dir.resolve(base + ".md"), report);
                written++;
            }

            failed = false;
            message = "Wrote " + written + " file(s) to " + dir.toAbsolutePath();
        } catch (Exception e) {
            failed = true;
            message = "Export failed: " + e;
        }
    }

    private static void text(Path path, String body) throws java.io.IOException {
        Files.write(path, body.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void render(GuiGraphicsExtractor g, int x, int y, int w, int h,
            int mouseX, int mouseY) {
        var font = Minecraft.getInstance().font;
        g.text(font, "Export", x + 8, y + 8, CircuitScreen.TEXT);

        if (message.isEmpty()) {
            return;
        }
        int wy = y + h - 34;
        for (var part : font.split(Component.literal(message), w - 16)) {
            g.text(font, part, x + 8, wy, failed ? CircuitScreen.ACCENT : CircuitScreen.OK);
            wy += 10;
        }
    }
}
