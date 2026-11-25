package com.ecocraft2.solar;

import com.ecocraft2.bridge.Ecc2PowerConnectable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Molecular Transformer block ({@code ecc2:molecular_transformer}): emits light level 12 while
 * active, connects to IC2NE cables on all six faces, and renders its sleek 3D frame and animated
 * plasma core via a custom {@code BlockEntityRenderer}.
 */
public final class MolecularTransformerBlock extends Ecc2BaseBlock
        implements Ecc2PowerConnectable {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 16.0, 15.0);

    public MolecularTransformerBlock(Properties properties) {
        super(properties);
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
    protected VoxelShape getShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MolecularTransformerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (SolarPlatform.isClient(level)
                || type != SolarKineticModule.MOLECULAR_TRANSFORMER_BE.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> {
            if (lvl instanceof ServerLevel server
                    && be instanceof MolecularTransformerBlockEntity mt) {
                mt.serverTick(server);
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
                && level.getBlockEntity(pos) instanceof MolecularTransformerBlockEntity mt) {
            serverPlayer.openMenu(mt, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean ecc2AcceptsCable(BlockState state, Direction side) {
        return true;
    }
}
