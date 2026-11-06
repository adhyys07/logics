package com.redstonelogics.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.redstonelogics.core.Pos;
import com.redstonelogics.core.RegionSnapshot;
import com.redstonelogics.export.ArduinoExporter;
import com.redstonelogics.export.DiagramPainter;
import com.redstonelogics.export.NetlistReport;
import com.redstonelogics.export.PngExporter;
import com.redstonelogics.export.SvgExporter;
import com.redstonelogics.mapper.RedstoneLogicMapper;
import com.redstonelogics.scanner.WorldScanner;
import com.redstonelogics.selection.SelectionStore;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class RedstoneLogicsCommand {

    private static final DateTimeFormatter STAMP = 
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private RedstoneLogicsCommand() {
    }
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rlogic")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("pos1")
                        .executes(c -> setCorner(c.getSource(), null, true))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(c -> setCorner(c.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(c, "pos"), true))))
                .then(Commands.literal("pos2")
                        .executes(c -> setCorner(c.getSource(), null, false))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(c -> setCorner(c.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(c, "pos"), false))))
                .then(Commands.literal("clear").executes(c -> {
                    SelectionStore.clear(player(c.getSource()).getUUID());
                    feedback(c.getSource(), "Selection cleared.");
                    return 1;
                }))
                .then(Commands.literal("scan").executes(c -> scan(c.getSource())))
                .then(Commands.literal("export")
                        .then(Commands.argument("what", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    for (String s : new String[] { "svg", "png", "ino", "json",
                                            "report", "all" }) {
                                        b.suggest(s);
                                    }
                                    return b.buildFuture();
                                })
                                 .executes(c -> export(c.getSource(),
                                        StringArgumentType.getString(c, "what"))))));
    }

    private static int setCorner(CommandSourceStack source, BlockPos explicit, boolean first)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        BlockPos pos = explicit != null ? explicit : player.blockPosition();
        Pos p = new Pos(pos.getX(), pos.getY(), pos.getZ());
        SelectionStore.Selection selection = first
                ? SelectionStore.setFirst(player.getUUID(), p)
                : SelectionStore.setSecond(player.getUUID(), p);
        feedback(source, "Corner " + (first ? "1" : "2") + " set to " + p
                + (selection.complete() ? " - " + selection.volume() + " blocks selected." : "."));
        return 1;
    }

    private static int scan(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        RedstoneLogicMapper mapped = analyse(source);
        if (mapped == null) {
            return 0;
        }
        feedback(source, mapped.summary());
        for (String line : mapped.diagram().patterns()) {
            feedback(source, "  " + line);
        }
        int shown = 0;
        for (String note : mapped.diagram().notes()) {
            if (shown++ == 4) {
                feedback(source, "  ... " + (mapped.diagram().notes().size() - 4)
                        + " more note(s); run /rlogic export report for all of them.");
                break;
            }
            feedback(source, "  note: " + note);
        }
        return 1;
    }

    private static int export(CommandSourceStack source, String what)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        RedstoneLogicMapper mapped = analyse(source);
        if (mapped == null) {
            return 0;
        }
        String base = "circuit-" + LocalDateTime.now().format(STAMP);
        Path dir = FabricLoader.getInstance().getGameDir().resolve("redstonelogics");
        String title = base + "  " + mapped.summary();

        try {
            Files.createDirectories(dir);
            boolean all = "all".equalsIgnoreCase(what);
            int written = 0;
            if (all || "svg".equalsIgnoreCase(what)) {
                write(dir.resolve(base + ".svg"),
                        SvgExporter.render(mapped.diagram(), title).getBytes());
                written++;
            }
            if (all || "png".equalsIgnoreCase(what)) {
                Files.write(dir.resolve(base + ".png"), PngExporter.toBytes(
                        mapped.diagram(), title, DiagramPainter.Palette.LIGHT, 2.0));
                written++;
            }
            if (all || "ino".equalsIgnoreCase(what)) {
                write(dir.resolve(base + ".ino"),
                        ArduinoExporter.render(mapped.diagram(), title).getBytes());
                written++;
            }
            if (all || "json".equalsIgnoreCase(what)) {
                write(dir.resolve(base + ".rlogic.json"),
                        mapped.snapshot().toJson().getBytes());
                written++;
            }
             if (all || "report".equalsIgnoreCase(what)) {
                write(dir.resolve(base + ".md"),
                        NetlistReport.render(mapped, title).getBytes());
                written++;
            }
            if (written == 0) {
                error(source, "Unknown export '" + what
                        + "'. Use svg, png, ino, json, report or all.");
                return 0;
            }
            feedback(source, "Wrote " + written + " file(s) to " + dir.toAbsolutePath() + ".");
            return written;
        } catch (IOException e) {
            error(source, "Export failed: " + e.getMessage());
            return 0;
        }
    }

    private static void write(Path path, byte[] bytes) throws IOException {
        Files.write(path, bytes);
    }

    private static RedstoneLogicMapper analyse(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        SelectionStore.Selection selection = SelectionStore.get(player.getUUID());
        if (!selection.complete()) {
            error(source, "Set both corners first: /rlogic pos1 and /rlogic pos2.");
            return null;
        }
        try {
            RegionSnapshot snapshot = WorldScanner.scan(player.level(),
                    toBlockPos(selection.first()), toBlockPos(selection.second()));
            return RedstoneLogicMapper.map(snapshot);
        } catch (IllegalArgumentException e) {
            error(source, e.getMessage());
            return null;
        }
    }

    private static BlockPos toBlockPos(Pos p) {
        return new BlockPos(p.x(), p.y(), p.z());
    }

    private static ServerPlayer player(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return source.getPlayerOrException();
    }

    private static void feedback(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal("[rlogic] " + message), false);
    }

    private static void error(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal("[rlogic] " + message));
    }
}