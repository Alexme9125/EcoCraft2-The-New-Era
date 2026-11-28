package com.ecocraft2.solar;

import com.ecocraft2.bridge.Ecc2PowerConnectable;
import com.ecocraft2.bridge.Ecc2WrenchHelper;
import com.ecocraft2.common.solar.SolarTier;
import com.industrialage.ia2.wrench.WrenchTarget;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/** Block for the four Advanced Solar Panels 2 tiers (Advanced / Hybrid / Ultimate Hybrid / Quantum). */
public final class SolarPanelBlock extends Block
        implements EntityBlock, Ecc2PowerConnectable, WrenchTarget {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    private final SolarTier solarTier;

    public SolarPanelBlock(SolarTier solarTier) {
        super(
                Properties.of(Material.METAL)
                        .strength(3.5f, 6.0f)
                        .sound(SoundType.METAL)
                        .requiresCorrectToolForDrops());
        this.solarTier = solarTier;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public SolarTier solarTier() {
        return solarTier;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SolarPanelBlockEntity(pos, state, solarTier);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return type == SolarKineticRegistry.SOLAR_PANEL_BE.get()
                ? (lvl, pos, st, be) -> ((SolarPanelBlockEntity) be).serverTick()
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
        if (!level.isClientSide
                && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof SolarPanelBlockEntity panel) {
            NetworkHooks.openScreen(serverPlayer, panel, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(
            BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof SolarPanelBlockEntity panel) {
            Containers.dropContents(level, pos, panel.dropInventory());
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable BlockGetter level,
            List<Component> tooltip,
            TooltipFlag flag) {
        int day = SolarPanelBlockEntity.configuredDayPower(solarTier);
        int night = SolarPanelBlockEntity.configuredNightPower(solarTier);
        int storage = SolarPanelBlockEntity.configuredStorage(solarTier);
        int tier = SolarPanelBlockEntity.configuredTier(solarTier);
        tooltip.add(
                Component.translatable("tooltip.ecera.solar.generation", day, night)
                        .withStyle(ChatFormatting.YELLOW));
        tooltip.add(
                Component.translatable(
                                "tooltip.ecera.solar.storage",
                                storage,
                                tier,
                                (long) SolarTier.packetSize(tier))
                        .withStyle(ChatFormatting.AQUA));
    }
}
