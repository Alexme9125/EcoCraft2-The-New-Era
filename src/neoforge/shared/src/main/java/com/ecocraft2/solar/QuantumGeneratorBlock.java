package com.ecocraft2.solar;

import com.ecocraft2.bridge.Ecc2PowerConnectable;
import com.industrialage.ia2.wrench.WrenchTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Creative-only configurable multi-packet EU generator ({@code ecc2:quantum_generator}).
 *
 * <p>Uses redstone-inverted active logic: defaults to {@code active = true} when unpowered,
 * including non-player placement via {@code /setblock} or structure blocks.
 */
public final class QuantumGeneratorBlock extends Ecc2BaseBlock
        implements Ecc2PowerConnectable, WrenchTarget {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public QuantumGeneratorBlock(Properties properties) {
        super(properties);
        registerDefaultState(
                stateDefinition
                        .any()
                        .setValue(FACING, Direction.NORTH)
                        .setValue(ACTIVE, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean powered = context.getLevel().hasNeighborSignal(context.getClickedPos());
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(ACTIVE, !powered);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected void onPlace(
            BlockState state,
            Level level,
            BlockPos pos,
            BlockState oldState,
            boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!SolarPlatform.isClient(level)
                && level.getBlockEntity(pos) instanceof QuantumGeneratorBlockEntity qg) {
            qg.syncRedstone(level);
        }
    }

    @Override
    protected void onEcc2NeighborChanged(BlockState state, Level level, BlockPos pos) {
        if (!SolarPlatform.isClient(level)
                && level.getBlockEntity(pos) instanceof QuantumGeneratorBlockEntity qg) {
            qg.syncRedstone(level);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new QuantumGeneratorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (SolarPlatform.isClient(level)
                || type != SolarKineticModule.QUANTUM_GENERATOR_BE.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> {
            if (lvl instanceof ServerLevel server
                    && be instanceof QuantumGeneratorBlockEntity qg) {
                qg.serverTick(server);
            }
        };
    }

    @Override
    protected InteractionResult onEcc2Use(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        if (SolarPlatform.isClient(level)) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof QuantumGeneratorBlockEntity qg) {
            serverPlayer.openMenu(qg, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean ecc2AcceptsCable(BlockState state, Direction side) {
        return true;
    }
}
