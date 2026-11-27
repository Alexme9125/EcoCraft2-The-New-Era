package com.ecocraft2.gas;

import com.ecocraft2.bridge.Ecc2PowerConnectable;
import com.ecocraft2.bridge.Ecc2WrenchHelper;
import com.industrialage.ia2.access.MachineAccessTarget;
import com.industrialage.ia2.wrench.WrenchTarget;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/** Shared horizontal-facing machine block for Water Gas, Gas Furnace, Gas Generator, and Biogas machines. */
public class GasMachineBlock<BE extends BlockEntity & GasMachineBlock.TickableMachine> extends Block
        implements EntityBlock, WrenchTarget {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public interface TickableMachine {
        void serverTick();

        Container dropInventory();
    }

    private final BiFunction<BlockPos, BlockState, BE> factory;
    private final Supplier<BlockEntityType<BE>> typeSupplier;
    private final String tooltipKey;

    public GasMachineBlock(
            BiFunction<BlockPos, BlockState, BE> factory,
            Supplier<BlockEntityType<BE>> typeSupplier,
            String tooltipKey) {
        super(
                Properties.of(Material.METAL)
                        .strength(3.5f, 6.0f)
                        .sound(SoundType.METAL)
                        .requiresCorrectToolForDrops());
        this.factory = factory;
        this.typeSupplier = typeSupplier;
        this.tooltipKey = tooltipKey;
        registerDefaultState(
                stateDefinition
                        .any()
                        .setValue(FACING, Direction.NORTH)
                        .setValue(ACTIVE, Boolean.FALSE));
    }

    public static <BE extends BlockEntity & TickableMachine> GasMachineBlock<BE> powered(
            BiFunction<BlockPos, BlockState, BE> factory,
            Supplier<BlockEntityType<BE>> typeSupplier,
            String tooltipKey) {
        return new Powered<>(factory, typeSupplier, tooltipKey);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState()
                .setValue(FACING, ctx.getHorizontalDirection().getOpposite())
                .setValue(ACTIVE, Boolean.FALSE);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return factory.apply(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return type == typeSupplier.get()
                ? (lvl, pos, st, be) -> ((TickableMachine) be).serverTick()
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
        if (Ecc2WrenchHelper.tryHandleThirdPartyWrench(
                this, state, level, pos, player, hand, hit.getDirection())) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MachineAccessTarget target
                    && target.exchangeHeldFluid(player, hand, hit.getDirection())) {
                return InteractionResult.CONSUME;
            }
            if (be instanceof MenuProvider provider && player instanceof ServerPlayer serverPlayer) {
                NetworkHooks.openScreen(serverPlayer, provider, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(
            BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof TickableMachine machine) {
            Containers.dropContents(level, pos, machine.dropInventory());
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable BlockGetter level,
            List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable(tooltipKey).withStyle(ChatFormatting.AQUA));
    }

    /** EU-powered variant implementing {@link Ecc2PowerConnectable} for IC2NE cable connections. */
    public static final class Powered<BE extends BlockEntity & TickableMachine>
            extends GasMachineBlock<BE> implements Ecc2PowerConnectable {

        public Powered(
                BiFunction<BlockPos, BlockState, BE> factory,
                Supplier<BlockEntityType<BE>> typeSupplier,
                String tooltipKey) {
            super(factory, typeSupplier, tooltipKey);
        }
    }
}
