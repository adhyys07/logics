package com.redstonelogics.client;

import com.redstonelogics.core.RegionSnapshot;
import com.redstonelogics.mapper.RedstoneLogicMapper;
import com.redstonelogics.scanner.WorldScanner;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;


public final class ClientAnalysis {

    public enum State { EMPTY, SCANNING, READY, FAILED }

    private static final AtomicReference<State> state = new AtomicReference<>(State.EMPTY);
    private static volatile RedstoneLogicMapper result;
    private static volatile String error;
    private static volatile String sourceLabel = "";

    private ClientAnalysis() {
    }

    public static State state() {
        return state.get();
    }

    public static RedstoneLogicMapper result() {
        return result;
    }

    public static String error() {
        return error;
    }

    public static String sourceLabel() {
        return sourceLabel;
    }

    public static boolean ready() {
        return state.get() == State.READY && result != null;
    }

    public static void clear() {
        state.set(State.EMPTY);
        result = null;
        error = null;
    }

    public static void rescan() {
        if (!ClientSelection.complete()) {
            fail("Set both corners first, or press Pick to flood-fill from the block you are looking at.");
            return;
        }
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            fail("No world loaded.");
            return;
        }

        state.set(State.SCANNING);
        error = null;

        RegionSnapshot snapshot;
        try {
            snapshot = WorldScanner.scan(level, ClientSelection.min(), ClientSelection.max());
        } catch (IllegalArgumentException e) {
            fail(e.getMessage());
            return;
        } catch (RuntimeException e) {
            fail("Scan failed: " + e);
            return;
        }

        sourceLabel = snapshot.size() + " blocks from " + ClientSelection.min()
                + " to " + ClientSelection.max();

        CompletableFuture
                .supplyAsync(() -> RedstoneLogicMapper.map(snapshot))
                .whenComplete((mapped, throwable) -> {
                    if (throwable != null) {
                        fail("Analysis failed: " + throwable.getCause());
                    } else {
                        result = mapped;
                        state.set(State.READY);
                    }
                });
    }

    public static void load(RegionSnapshot snapshot, String label) {
        state.set(State.SCANNING);
        sourceLabel = label;
        CompletableFuture
                .supplyAsync(() -> RedstoneLogicMapper.map(snapshot))
                .whenComplete((mapped, throwable) -> {
                    if (throwable != null) {
                        fail("Analysis failed: " + throwable.getCause());
                    } else {
                        result = mapped;
                        state.set(State.READY);
                    }
                });
    }

    private static void fail(String message) {
        error = message;
        result = null;
        state.set(State.FAILED);
    }
}
