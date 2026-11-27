package com.ecocraft2.kinetic;

import com.ecocraft2.bridge.Ecc2WrenchHelper;
import com.ecocraft2.common.kinetic.KineticMachineTier;
import com.industrialage.ia2.wrench.WrenchTarget;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/**
 * Block for the 6 Advanced Wind (H4 facing) and Water (6-direction facing) Kinetic Generators:
 * Durable (0.80x / 0.50x wear), Overclocked (2.20x / 2.00x wear), Precision (2.40x / 0.50x wear).
 */
public class AdvancedKineticBlock extends Block implements EntityBlock, WrenchTarget {

    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    private final boolean water;
    private final KineticMachineTier tier;

    protected AdvancedKineticBlock(boolean water, KineticMachineTier tier) {
        super(
                Properties.of(Material.METAL)
                        .strength(3.5f, 6.0f)
                        .sound(SoundType.METAL)
                        .requiresCorrectToolForDrops());
        this.water = water;
        this.tier = tier;
        registerDefaultState(
                stateDefinition
                        .any()
                        .setValue(facingProperty(), Direction.NORTH)
                        .setValue(ACTIVE, Boolean.FALSE));
    }

    public static AdvancedKineticBlock wind(KineticMachineTier tier) {
        return new WindBlock(tier);
    }

    public static AdvancedKineticBlock water(KineticMachineTier tier) {
        return new WaterBlock(tier);
    }

    public boolean isWater() {
        return water;
    }

    public KineticMachineTier tier() {
        return tier;
    }

    public DirectionProperty facingProperty() {
        return water ? BlockStateProperties.FACING : BlockStateProperties.HORIZONTAL_FACING;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(facingProperty(), ACTIVE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction dir =
                water
                        ? ctx.getNearestLookingDirection().getOpposite()
                        : ctx.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(facingProperty(), dir).setValue(ACTIVE, Boolean.FALSE);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        DirectionProperty prop = facingProperty();
        return state.setValue(prop, rotation.rotate(state.getValue(prop)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AdvancedKineticBlockEntity(pos, state, water, tier);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return type == com.ecocraft2.solar.SolarKineticRegistry.ADVANCED_KINETIC_BE.get()
                ? (lvl, pos, st, be) -> ((AdvancedKineticBlockEntity) be).serverTick()
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
                && level.getBlockEntity(pos) instanceof AdvancedKineticBlockEntity gen) {
            NetworkHooks.openScreen(serverPlayer, gen, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(
            BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof AdvancedKineticBlockEntity gen) {
            Containers.dropContents(level, pos, gen.dropInventory());
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable BlockGetter level,
            List<Component> tooltip,
            TooltipFlag flag) {
        String key =
                switch (tier) {
                    case DURABLE -> "tooltip.ecc2.kinetic.durable";
                    case OVERCLOCKED -> "tooltip.ecc2.kinetic.overclocked";
                    case PRECISION -> "tooltip.ecc2.kinetic.precision";
                    default -> "tooltip.ecc2.kinetic.durable";
                };
        tooltip.add(Component.translatable(key).withStyle(ChatFormatting.AQUA));
    }

    private static final class WindBlock extends AdvancedKineticBlock {
        WindBlock(KineticMachineTier tier) {
            super(false, tier);
        }

        @Override
        public DirectionProperty facingProperty() {
            return BlockStateProperties.HORIZONTAL_FACING;
        }
    }

    private static final class WaterBlock extends AdvancedKineticBlock {
        WaterBlock(KineticMachineTier tier) {
            super(true, tier);
        }

        @Override
        public DirectionProperty facingProperty() {
            return BlockStateProperties.FACING;
        }

        @Override
        public boolean wrenchSide(BlockState state, Direction side) {
            return side != null;
        }

        @Override
        public boolean wrenchTurn(ServerLevel level, BlockPos pos, BlockState state, Direction side) {
            if (side == null || state.getValue(BlockStateProperties.FACING) == side) {
                return false;
            }
            level.setBlock(
                    pos, state.setValue(BlockStateProperties.FACING, side), Block.UPDATE_ALL);
            return true;
        }
    }
}
