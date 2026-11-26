package com.ecocraft2.giant;

import com.ecocraft2.bridge.Ecc2PowerConnectable;
import com.industrialage.ia2.wrench.WrenchTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Placeable unit block for 3x3x3 Giant Machines. Placing 27 matching units in a solid 3x3x3 cube
 * automatically forms the multiblock; breaking any of the 27 dissolves it safely.
 */
public final class GiantUnitBlock extends Ecc2BlockBase
        implements EntityBlock, Ecc2PowerConnectable, WrenchTarget {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final EnumProperty<GiantBlockRole> ROLE =
            EnumProperty.create("role", GiantBlockRole.class);
    public static final EnumProperty<GiantPortVisual> PORT_MODE =
            EnumProperty.create("port_mode", GiantPortVisual.class);

    private final GiantMachineType machineType;

    public GiantUnitBlock(Properties properties, GiantMachineType machineType) {
        super(properties);
        this.machineType = machineType;
        registerDefaultState(
                stateDefinition
                        .any()
                        .setValue(FACING, Direction.NORTH)
                        .setValue(FORMED, false)
                        .setValue(ACTIVE, false)
                        .setValue(ROLE, GiantBlockRole.UNFORMED)
                        .setValue(PORT_MODE, GiantPortVisual.FULL));
    }

    public GiantMachineType machineType() {
        return machineType;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FORMED, ACTIVE, ROLE, PORT_MODE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(FORMED, false)
                .setValue(ACTIVE, false)
                .setValue(ROLE, GiantBlockRole.UNFORMED)
                .setValue(PORT_MODE, GiantPortVisual.FULL);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public boolean ecc2AcceptsCable(BlockState state, Direction side) {
        return state.getValue(FORMED);
    }

    @Override
    public void setPlacedBy(
            Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel server && !state.getValue(FORMED)) {
            Direction facing =
                    placer != null
                            ? placer.getDirection().getOpposite()
                            : state.getValue(FACING);
            GiantMultiblockController.tryForm(server, pos, machineType, facing);
        }
    }

    @Override
    protected void onPlace(
            BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel server
                && !oldState.is(this)
                && !state.getValue(FORMED)) {
            GiantMultiblockController.tryForm(server, pos, machineType, state.getValue(FACING));
        }
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
        if (!state.getValue(FORMED)) {
            if (level instanceof ServerLevel) {
                if (!GiantMultiblockController.tryForm(
                        (ServerLevel) level, pos, machineType, state.getValue(FACING))) {
                    GiantGasPlatform.sendActionBar(
                            player, Component.translatable("gui.ecc2.giant.unformed"));
                }
            }
            return true;
        }
        if (!(level.getBlockEntity(pos) instanceof GiantUnitBlockEntity unitBe)) {
            return false;
        }
        GiantUnitBlockEntity core = unitBe.coreEntity();
        if (core == null) {
            if (level instanceof ServerLevel server && unitBe.tryRecoverOrphan(server, state)) {
                GiantGasPlatform.sendActionBar(
                        player, Component.translatable("gui.ecc2.giant.unformed"));
                return true;
            }
            return false;
        }
        if (core.machineType().spec().usesWater()
                && unitBe.exchangeHeldFluid(player, hand, hitFace)) {
            return true;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(core, buf -> buf.writeBlockPos(core.getBlockPos()));
        }
        return true;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GiantUnitBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (!(level instanceof ServerLevel) || type != GiantGasModule.GIANT_UNIT_BE.get()) {
            return null;
        }
        return (lvl, p, st, be) -> ((GiantUnitBlockEntity) be).serverTick((ServerLevel) lvl, st);
    }

    @Override
    public boolean wrenchable(BlockState state) {
        return !state.getValue(FORMED);
    }
}
