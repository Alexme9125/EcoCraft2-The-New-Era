package com.ecocraft2.giant;

import com.ecocraft2.bridge.Ecc2PowerConnectable;
import com.ecocraft2.common.giant.GiantMachineKind;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/**
 * Unit block for all 5 3x3x3 Giant Machines (Macerator, Compressor, Recycler, Extractor, Ore
 * Washing Plant). Placing 27 matching units in a solid 3x3x3 cube automatically forms the machine.
 */
public final class GiantMachineBlock extends Block implements EntityBlock, Ecc2PowerConnectable {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final EnumProperty<GiantUnitRole> ROLE =
            EnumProperty.create("role", GiantUnitRole.class);

    private final GiantMachineKind kind;

    public GiantMachineBlock(GiantMachineKind kind) {
        super(
                Properties.of(Material.METAL)
                        .strength(4.5f, 12.0f)
                        .sound(SoundType.METAL)
                        .requiresCorrectToolForDrops());
        this.kind = kind;
        registerDefaultState(
                stateDefinition
                        .any()
                        .setValue(FACING, Direction.NORTH)
                        .setValue(FORMED, Boolean.FALSE)
                        .setValue(ACTIVE, Boolean.FALSE)
                        .setValue(ROLE, GiantUnitRole.UNFORMED));
    }

    public GiantMachineKind kind() {
        return kind;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FORMED, ACTIVE, ROLE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState()
                .setValue(FACING, ctx.getHorizontalDirection().getOpposite())
                .setValue(FORMED, Boolean.FALSE)
                .setValue(ACTIVE, Boolean.FALSE)
                .setValue(ROLE, GiantUnitRole.UNFORMED);
    }

    @Override
    public void setPlacedBy(
            Level level,
            BlockPos pos,
            BlockState state,
            @Nullable LivingEntity placer,
            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel) {
            GiantMachineBlockEntity.tryFormMultiblock(
                    serverLevel, pos, kind, state.getValue(FACING));
        }
    }

    @Override
    public void onPlace(
            BlockState state,
            Level level,
            BlockPos pos,
            BlockState oldState,
            boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!oldState.is(this) && level instanceof ServerLevel serverLevel) {
            GiantMachineBlockEntity.tryFormMultiblock(
                    serverLevel, pos, kind, state.getValue(FACING));
        }
    }

    @Override
    public void onRemove(
            BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())
                && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof GiantMachineBlockEntity unit) {
            unit.dissolveOnBreak(serverLevel);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GiantMachineBlockEntity(pos, state, kind);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return type == GiantRegistry.GIANT_MACHINE_BE.get()
                ? (lvl, pos, st, be) -> ((GiantMachineBlockEntity) be).serverTick()
                : null;
    }

    @Override
    public InteractionResult use(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof GiantMachineBlockEntity unit) {
            GiantMachineBlockEntity core = unit.resolveCore();
            if (core != null && core.isFormed()) {
                if (core.exchangeHeldFluid(player, hand, hit.getDirection())) {
                    return InteractionResult.CONSUME;
                }
                if (player instanceof ServerPlayer serverPlayer) {
                    NetworkHooks.openScreen(serverPlayer, core, core.getBlockPos());
                }
                return InteractionResult.CONSUME;
            }
            if (level instanceof ServerLevel serverLevel
                    && GiantMachineBlockEntity.tryFormMultiblock(
                            serverLevel, pos, kind, state.getValue(FACING))) {
                GiantMachineBlockEntity newlyFormed = unit.resolveCore();
                if (newlyFormed != null && player instanceof ServerPlayer serverPlayer) {
                    NetworkHooks.openScreen(serverPlayer, newlyFormed, newlyFormed.getBlockPos());
                }
                return InteractionResult.CONSUME;
            }
            player.displayClientMessage(
                    Component.translatable("gui.ecc2.giant.unformed")
                            .withStyle(ChatFormatting.YELLOW),
                    true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable BlockGetter level,
            List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(
                Component.translatable("tooltip.ecc2.giant_unit").withStyle(ChatFormatting.AQUA));
        tooltip.add(
                Component.translatable("tooltip.ecc2.giant_unit.ports")
                        .withStyle(ChatFormatting.GRAY));
    }
}
