package com.ecocraft2.kinetic;

import com.ecocraft2.common.kinetic.KineticMachineTier;
import com.ecocraft2.solar.Ecc2BaseBlock;
import com.ecocraft2.solar.SolarKineticModule;
import com.ecocraft2.solar.SolarPlatform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Shared abstract block for the six Advanced Wind &amp; Water Kinetic Generators (Durable,
 * Overclocked, and Precision).
 */
public abstract class AdvancedKineticBlock extends Ecc2BaseBlock {

    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    private final KineticMachineTier kineticTier;
    private final boolean waterType;

    protected AdvancedKineticBlock(
            Properties properties, KineticMachineTier kineticTier, boolean waterType) {
        super(properties);
        this.kineticTier = kineticTier;
        this.waterType = waterType;
    }

    /** Returns the operating tier (DURABLE, OVERCLOCKED, or PRECISION). */
    public KineticMachineTier getKineticTier() {
        return kineticTier;
    }

    /** Returns {@code true} for water kinetic generators and {@code false} for wind generators. */
    public boolean isWaterType() {
        return waterType;
    }

    /** Returns the block face where the 3D rotor mounts ({@code _front} cavity). */
    public abstract Direction getRotorFace(BlockState state);

    /** Returns the block face exposing KU to adjacent {@code ic2:kinetic_generator} ({@code _back}). */
    public Direction getOutputFace(BlockState state) {
        return getRotorFace(state).getOpposite();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AdvancedKineticBlockEntity(pos, state, kineticTier, waterType);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (SolarPlatform.isClient(level) || type != SolarKineticModule.KINETIC_GENERATOR_BE.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> {
            if (lvl instanceof ServerLevel server
                    && be instanceof AdvancedKineticBlockEntity kinetic) {
                kinetic.serverTick(server);
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
                && level.getBlockEntity(pos) instanceof AdvancedKineticBlockEntity kinetic) {
            serverPlayer.openMenu(kinetic, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.CONSUME;
    }
}
