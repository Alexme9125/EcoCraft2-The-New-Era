package com.ecocraft2.gas;

import com.ecocraft2.bridge.Ecc2PowerConnectable;
import com.ecocraft2.giant.Ecc2BlockBase;
import com.industrialage.ia2.wrench.WrenchTarget;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Placeable machine block for EcoCraft 2's gas and biogas industry machines (horizontal facing +
 * {@code active} state, fluid container right-click exchange, IC2 wrench compatibility).
 */
public class GasMachineBlock extends Ecc2BlockBase implements EntityBlock, WrenchTarget {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    private final BiFunction<BlockPos, BlockState, ? extends GasMachineBlockEntityBase> factory;
    private final Supplier<? extends BlockEntityType<?>> beType;

    public GasMachineBlock(
            Properties properties,
            BiFunction<BlockPos, BlockState, ? extends GasMachineBlockEntityBase> factory,
            Supplier<? extends BlockEntityType<?>> beType) {
        super(properties);
        this.factory = factory;
        this.beType = beType;
        registerDefaultState(
                stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false));
    }

    public static BlockBehaviour.Properties defaultProperties() {
        return BlockBehaviour.Properties.of()
                .strength(2.5F, 10.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops();
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
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected boolean onUseBlock(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            Direction hitFace) {
        if (player.isShiftKeyDown() && !player.getItemInHand(hand).isEmpty()) {
            return false;
        }
        if (!(level.getBlockEntity(pos) instanceof GasMachineBlockEntityBase machine)) {
            return false;
        }
        if (machine.exchangeHeldFluid(player, hand, hitFace)) {
            return true;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(machine, buf -> buf.writeBlockPos(pos));
        }
        return true;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return factory.apply(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (!(level instanceof ServerLevel) || type != beType.get()) {
            return null;
        }
        return (lvl, p, st, be) ->
                ((GasMachineBlockEntityBase) be).serverTick((ServerLevel) lvl, st);
    }

    /** Electrical subclass implementing {@link Ecc2PowerConnectable} so IC2NE cables connect cleanly. */
    public static final class Electric extends GasMachineBlock implements Ecc2PowerConnectable {

        public Electric(
                Properties properties,
                BiFunction<BlockPos, BlockState, ? extends GasMachineBlockEntityBase> factory,
                Supplier<? extends BlockEntityType<?>> beType) {
            super(properties, factory, beType);
        }

        @Override
        public boolean ecc2AcceptsCable(BlockState state, Direction side) {
            return true;
        }
    }
}
