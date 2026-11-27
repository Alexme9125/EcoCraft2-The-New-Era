package com.ecocraft2.kinetic;

import com.ecocraft2.common.kinetic.KineticMachineTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/** 6-direction Water Kinetic Generator block (Durable, Overclocked, or Precision). */
public final class AdvancedWaterKineticBlock extends AdvancedKineticBlock {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    public AdvancedWaterKineticBlock(Properties properties, KineticMachineTier tier) {
        super(properties, tier, true);
        registerDefaultState(
                stateDefinition
                        .any()
                        .setValue(FACING, Direction.NORTH)
                        .setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getNearestLookingDirection().getOpposite())
                .setValue(ACTIVE, false);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public boolean wrenchSide(BlockState state, Direction side) {
        return side != null;
    }

    @Override
    public boolean wrenchTurn(ServerLevel level, BlockPos pos, BlockState state, Direction side) {
        if (side == null || state.getValue(FACING) == side) {
            return false;
        }
        level.setBlock(pos, state.setValue(FACING, side), Block.UPDATE_ALL);
        return true;
    }

    @Override
    public Direction getRotorFace(BlockState state) {
        return state.getValue(FACING);
    }
}
