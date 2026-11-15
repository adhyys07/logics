package com.redstonelogics.scanner;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

public final class FloodFillSelector {

    public static final int MAX_COMPONENTS = 20_000;

    public record Region(BlockPos min, BlockPos max, int components) {
        public long volume() {
            return (long) (max.getX() - min.getX() + 1)
                    * (max.getY() - min.getY() + 1)
                    * (max.getZ() - min.getZ() + 1);
        }
    }

    private FloodFillSelector() {
    }

    public static Region from(Level level, BlockPos start) {
        if (!conducts(level.getBlockState(start))) {
            return null;
        }

        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start.immutable());
        queue.add(start.immutable());

        int minX = start.getX();
        int minY = start.getY();
        int minZ = start.getZ();
        int maxX = minX;
        int maxY = minY;
        int maxZ = minZ;

        while (!queue.isEmpty() && seen.size() < MAX_COMPONENTS) {
            BlockPos pos = queue.poll();
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        BlockPos next = pos.offset(dx, dy, dz);
                        if (seen.contains(next)) {
                            continue;
                        }
                        if (conducts(level.getBlockState(next))) {
                            BlockPos immutable = next.immutable();
                            seen.add(immutable);
                            queue.add(immutable);
                        }
                    }
                }
            }
        }

        return new Region(
                new BlockPos(minX - 1, minY - 1, minZ - 1),
                new BlockPos(maxX + 1, maxY + 1, maxZ + 1),
                seen.size());
    }

    private static boolean conducts(BlockState state) {
        return state.is(Blocks.REDSTONE_WIRE)
                || state.is(Blocks.REDSTONE_TORCH)
                || state.is(Blocks.REDSTONE_WALL_TORCH)
                || state.is(Blocks.REPEATER)
                || state.is(Blocks.COMPARATOR)
                || state.is(Blocks.LEVER)
                || state.is(Blocks.REDSTONE_BLOCK)
                || state.is(Blocks.REDSTONE_LAMP)
                || state.is(Blocks.OBSERVER)
                || state.is(Blocks.PISTON)
                || state.is(Blocks.STICKY_PISTON)
                || state.is(Blocks.DISPENSER)
                || state.is(Blocks.DROPPER)
                || state.is(Blocks.NOTE_BLOCK)
                || state.is(Blocks.HOPPER)
                || state.is(BlockTags.BUTTONS)
                || state.is(BlockTags.DOORS)
                || state.is(BlockTags.TRAPDOORS)
                || state.is(BlockTags.FENCE_GATES);
    }
}
