package com.redstonelogics.client;

import net.minecraft.core.BlockPos;

public final class ClientSelection {
    
    private static BlockPos first;
    private static BlockPos second;

    private ClientSelection() {
    }

    public static void setFirst(BlockPos pos) {
        first = pos == null ? null : pos.immutable();
    }

    public static void setSecond(BlockPos pos) {
        second = pos == null ? null : pos.immutable();
    }

    public static void set(BlockPos a, BlockPos b) {
        setFirst(a);
        setSecond(b);
    }

    public static void clear() {
        first = null;
        second = null;
    }

    public static BlockPos first() {
        return first;
    }

    public static BlockPos second() {
        return second;
    }

    public static boolean complete() {
        return first != null && second != null;
    }

    public static BlockPos min() {
        return new BlockPos(
                Math.min(first.getX(), second.getX()),
                Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ()));
    }

    public static BlockPos max() {
        return new BlockPos(
                Math.max(first.getX(), second.getX()),
                Math.max(first.getY(), second.getY()),
                Math.max(first.getZ(), second.getZ()));
    }

    public static long volume() {
        if (!complete()) {
            return 0;
        }
        BlockPos lo = min();
        BlockPos hi = max();
        return (long) (hi.getX() - lo.getX() + 1)
                * (hi.getY() - lo.getY() + 1)
                * (hi.getZ() - lo.getZ() + 1);
    }
}
