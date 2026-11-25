package com.ecocraft2.kinetic;

import com.ecocraft2.common.kinetic.KineticMachineTier;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/** Horizontal 4-direction Wind Kinetic Generator block (Durable, Overclocked, or Precision). */
public final class AdvancedWindKineticBlock extends AdvancedKineticBlock {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public AdvancedWindKineticBlock(Properties properties, KineticMachineTier tier) {
        super(properties, tier, false);
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
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(ACTIVE, false);
    }

    @Override
    public Direction getRotorFace(BlockState state) {
        return state.getValue(FACING);
    }
}
