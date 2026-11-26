package com.ecocraft2.giant;

import com.ecocraft2.common.giant.BlockFace;
import com.ecocraft2.common.giant.BlockPos3;
import com.ecocraft2.common.giant.MultiblockBounds;
import com.ecocraft2.common.giant.MultiblockGeometry;
import com.industrialage.ia2.logistics.TransportBlock;
import com.industrialage.ia2.power.WorldEnergy;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Coordinates automatic 3x3x3 formation and safe, zero-loss dissolution of Giant Machines using
 * {@link MultiblockGeometry}.
 */
public final class GiantMultiblockController {

    private static final ThreadLocal<Set<BlockPos>> ACTIVE_DISSOLUTIONS =
            ThreadLocal.withInitial(HashSet::new);

    private GiantMultiblockController() {}

    public static BlockPos3 toPos3(BlockPos pos) {
        return new BlockPos3(pos.getX(), pos.getY(), pos.getZ());
    }

    public static BlockPos toBlockPos(BlockPos3 pos) {
        return new BlockPos(pos.x(), pos.y(), pos.z());
    }

    public static BlockFace toBlockFace(Direction dir) {
        return switch (dir) {
            case DOWN -> BlockFace.DOWN;
            case UP -> BlockFace.UP;
            case NORTH -> BlockFace.NORTH;
            case SOUTH -> BlockFace.SOUTH;
            case WEST -> BlockFace.WEST;
            case EAST -> BlockFace.EAST;
        };
    }

    public static Direction toDirection(BlockFace face) {
        return switch (face) {
            case DOWN -> Direction.DOWN;
            case UP -> Direction.UP;
            case NORTH -> Direction.NORTH;
            case SOUTH -> Direction.SOUTH;
            case WEST -> Direction.WEST;
            case EAST -> Direction.EAST;
        };
    }

    /**
     * Checks whether the newly placed unformed unit at {@code origin} completes a solid 3x3x3 cube
     * of identical unformed {@link GiantUnitBlock}s, and if so forms the multiblock immediately.
     */
    public static boolean tryForm(
            ServerLevel level, BlockPos origin, GiantMachineType type, Direction structureFacing) {
        Direction horizontalFacing =
                structureFacing.getAxis().isHorizontal() ? structureFacing : Direction.NORTH;
        var found =
                MultiblockGeometry.find3x3x3(
                        toPos3(origin),
                        candidate -> {
                            BlockPos p = toBlockPos(candidate);
                            if (!level.hasChunkAt(p)) {
                                return false;
                            }
                            BlockState st = level.getBlockState(p);
                            return st.getBlock() instanceof GiantUnitBlock unit
                                    && unit.machineType() == type
                                    && !st.getValue(GiantUnitBlock.FORMED);
                        });
        if (found.isEmpty()) {
            return false;
        }
        MultiblockBounds bounds = found.get();
        BlockPos corePos = toBlockPos(bounds.core());

        // Step 1: update all 27 blockstates and bind every BlockEntity to corePos.
        for (BlockPos3 p3 : bounds.allPositions()) {
            BlockPos pos = toBlockPos(p3);
            GiantBlockRole role = GiantBlockRole.fromBoundsRole(bounds.roleOf(p3));
            BlockState current = level.getBlockState(pos);
            BlockState formedState =
                    current.setValue(GiantUnitBlock.FACING, horizontalFacing)
                            .setValue(GiantUnitBlock.FORMED, true)
                            .setValue(GiantUnitBlock.ACTIVE, false)
                            .setValue(GiantUnitBlock.ROLE, role)
                            .setValue(GiantUnitBlock.PORT_MODE, GiantPortVisual.FULL);
            level.setBlock(pos, formedState, Block.UPDATE_ALL);
            if (level.getBlockEntity(pos) instanceof GiantUnitBlockEntity be) {
                be.bindFormed(corePos, horizontalFacing, role);
            }
        }

        // Step 2: attach all 27 formed BEs to IC2NE EnergyNetwork and refresh corner pipes/caps.
        for (BlockPos3 p3 : bounds.allPositions()) {
            BlockPos pos = toBlockPos(p3);
            if (level.getBlockEntity(pos) instanceof GiantUnitBlockEntity be) {
                WorldEnergy.connect(be);
                be.invalidateCapabilities();
                if (be.role() == GiantBlockRole.CORNER) {
                    TransportBlock.refreshConnections(level, pos);
                }
            }
        }
        return true;
    }

    /**
     * Safely dissolves a formed 3x3x3 Giant Machine when any of its 27 blocks is broken, dropping
     * all stored items, upgrades, and battery cleanly once from the core and resetting the
     * remaining 26 blocks to unformed state.
     */
    public static void dissolve(
            ServerLevel level,
            BlockPos corePos,
            BlockPos brokenPos,
            GiantUnitBlockEntity brokenEntity) {
        if (corePos == null) {
            return;
        }
        Set<BlockPos> active = ACTIVE_DISSOLUTIONS.get();
        if (!active.add(corePos)) {
            return;
        }
        try {
            GiantUnitBlockEntity core =
                    corePos.equals(brokenPos)
                            ? brokenEntity
                            : (level.hasChunkAt(corePos)
                                            && level.getBlockEntity(corePos)
                                                    instanceof GiantUnitBlockEntity candidate
                                    ? candidate
                                    : null);

            if (core != null && core.markDissolvedOnce()) {
                // Drop all stored inventory contents cleanly once (inputs, outputs, battery, upgrades, washer slots).
                Containers.dropContents(level, brokenPos, core.coreInventory());
                core.clearContent();
            }

            MultiblockBounds bounds = MultiblockGeometry.boundsFromCore(toPos3(corePos));
            for (BlockPos3 p3 : bounds.allPositions()) {
                BlockPos pos = toBlockPos(p3);
                if (!level.hasChunkAt(pos)) {
                    continue;
                }
                if (pos.equals(brokenPos)) {
                    if (brokenEntity != null) {
                        WorldEnergy.disconnect(brokenEntity);
                        brokenEntity.unbindFormed();
                    }
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                if (state.getBlock() instanceof GiantUnitBlock) {
                    if (level.getBlockEntity(pos) instanceof GiantUnitBlockEntity unitBe) {
                        WorldEnergy.disconnect(unitBe);
                        unitBe.unbindFormed();
                    }
                    if (state.getValue(GiantUnitBlock.FORMED)
                            || state.getValue(GiantUnitBlock.ROLE) != GiantBlockRole.UNFORMED
                            || state.getValue(GiantUnitBlock.ACTIVE)
                            || state.getValue(GiantUnitBlock.PORT_MODE) != GiantPortVisual.FULL) {
                        BlockState unformed =
                                state.setValue(GiantUnitBlock.FORMED, false)
                                        .setValue(GiantUnitBlock.ACTIVE, false)
                                        .setValue(GiantUnitBlock.ROLE, GiantBlockRole.UNFORMED)
                                        .setValue(GiantUnitBlock.PORT_MODE, GiantPortVisual.FULL);
                        level.setBlock(pos, unformed, Block.UPDATE_ALL);
                    }
                    if (level.getBlockEntity(pos) instanceof GiantUnitBlockEntity unitBe) {
                        unitBe.invalidateCapabilities();
                    }
                    TransportBlock.refreshConnections(level, pos);
                }
            }
        } finally {
            active.remove(corePos);
        }
    }
}
