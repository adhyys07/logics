package com.redstonelogics.scanner;

import com.redstonelogics.core.BlockKind;
import com.redstonelogics.core.Direction;
import com.redstonelogics.core.Pos;
import com.redstonelogics.core.RegionSnapshot;
import com.redstonelogics.core.ScannedBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ComparatorMode;

public final class WorldScanner {
    
    public static final int MAX_VOLUME = 200_000;

    private WorldScanner(){
    }

    public static RegionSnapshot scan(Level level, BlockPos a, BlockPos b){
        int minX = Math.min(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxX = Math.max(a.getX(), b.getX());
        int maxY = Math.max(a.getY(), b.getY());
        int maxZ = Math.max(a.getZ(), b.getZ());

        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (volume > MAX_VOLUME) {
            throw new IllegalArgumentException("Selection is " + volume
                    + " blocks; the limit is " + MAX_VOLUME + ". Select a smaller region.");
        }

        RegionSnapshot.Builder builder = RegionSnapshot.builder(
                level.dimension().identifier().toString());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.set(x, y, z);
                    Pos pos = new Pos(x, y, z);
                    builder.bounds(pos);
                    ScannedBlock block = classify(level, cursor);
                    if (block.kind() != BlockKind.AIR) {
                        builder.put(pos, block);
                    }
                }
            }
        }
        return builder.build();
    }

    private static ScannedBlock classify(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        String id = net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY != null
                ? state.getBlock().getDescriptionId()
                : "minecraft:unknown";

        if (state.isAir()) {
            return ScannedBlock.AIR;
        }

        if (state.is(Blocks.REDSTONE_WIRE)) {
            int power = state.hasProperty(BlockStateProperties.POWER)
                    ? state.getValue(BlockStateProperties.POWER) : 0;
            return ScannedBlock.dust(power);
        }

        if (state.is(Blocks.REDSTONE_TORCH) || state.is(Blocks.REDSTONE_WALL_TORCH)) {
            boolean lit = !state.hasProperty(BlockStateProperties.LIT)
                    || state.getValue(BlockStateProperties.LIT);
            Direction towardsSupport = state.is(Blocks.REDSTONE_WALL_TORCH)
                    ? convert(state.getValue(BlockStateProperties.HORIZONTAL_FACING)).opposite()
                    : Direction.DOWN;
            return ScannedBlock.torch(towardsSupport, lit);
        }

        if (state.is(Blocks.REPEATER)) {
            Direction front = convert(state.getValue(BlockStateProperties.HORIZONTAL_FACING));
            return ScannedBlock.repeater(front,
                    state.getValue(BlockStateProperties.POWERED),
                    state.getValue(BlockStateProperties.DELAY),
                    state.hasProperty(BlockStateProperties.LOCKED)
                            && state.getValue(BlockStateProperties.LOCKED));
        }

        if (state.is(Blocks.COMPARATOR)) {
            Direction front = convert(state.getValue(BlockStateProperties.HORIZONTAL_FACING));
            boolean subtract =
                    state.getValue(BlockStateProperties.MODE_COMPARATOR) == ComparatorMode.SUBTRACT;
            boolean powered = state.hasProperty(BlockStateProperties.POWERED)
                    && state.getValue(BlockStateProperties.POWERED);
            return ScannedBlock.comparator(front, powered ? 15 : 0, subtract);
        }

        if (state.is(Blocks.LEVER)) {
            return ScannedBlock.lever(attachment(state),
                    state.getValue(BlockStateProperties.POWERED));
        }

        if (state.is(BlockTags.BUTTONS)) {
            return ScannedBlock.button(attachment(state),
                    state.getValue(BlockStateProperties.POWERED));
        }

        if (state.is(Blocks.REDSTONE_BLOCK)) {
            return ScannedBlock.of(BlockKind.REDSTONE_BLOCK, id);
        }

        if (state.is(Blocks.REDSTONE_LAMP)) {
            return ScannedBlock.of(BlockKind.LAMP, id);
        }

        if (isMechanism(state)) {
            return ScannedBlock.of(BlockKind.MECHANISM, id);
        }

        if (state.isRedstoneConductor(level, pos)) {
            return ScannedBlock.solid(id);
        }
        return ScannedBlock.AIR;
    }

    private static boolean isMechanism(BlockState state){
        return state.is(Blocks.PISTON) || state.is(Blocks.STICKY_PISTON)
                || state.is(Blocks.DISPENSER) || state.is(Blocks.DROPPER)
                || state.is(Blocks.NOTE_BLOCK) || state.is(Blocks.OBSERVER)
                || state.is(Blocks.TNT) || state.is(Blocks.HOPPER)
                || state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS)
                || state.is(BlockTags.FENCE_GATES);
    }

    private static Direction attachment(BlockState state){
        if (state.hasProperty(BlockStateProperties.ATTACH_FACE)) {
            AttachFace face = state.getValue(BlockStateProperties.ATTACH_FACE);
            return switch (face) {
                case FLOOR -> Direction.DOWN;
                case CEILING -> Direction.UP;
                case WALL -> convert(state.getValue(BlockStateProperties.HORIZONTAL_FACING))
                        .opposite();
            };
    }
    return Direction.DOWN;
}

private static Direction convert(net.minecraft.core.Direction d){
    return Direction.byName(d.getName());
}
}

