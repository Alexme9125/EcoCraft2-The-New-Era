package com.ecocraft2.compat;

import java.util.function.Function;
import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.ITheOneProbe;
import mcjty.theoneprobe.api.ProbeMode;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.InterModComms;

/**
 * Shared The One Probe (TOP) integration for EcoCraft 2.
 *
 * <p>Only ever loaded when {@code ModList.get().isLoaded("theoneprobe")} returns {@code true}. The
 * version-specific {@link TopBlockProvider} subclass in {@code 1.21.1/} and {@code 26.x/} supplies
 * the exact {@code getID()} return type ({@code ResourceLocation} vs {@code Identifier}).
 */
final class TopCompat {

    private TopCompat() {}

    static void enqueue() {
        InterModComms.sendTo(
                "theoneprobe",
                "getTheOneProbe",
                () -> (Function<ITheOneProbe, Void>) TopCompat::register);
    }

    private static Void register(ITheOneProbe probe) {
        probe.registerProvider(new TopBlockProvider());
        return null;
    }

    /** Shared server-side TOP block telemetry provider extended by versioned {@code TopBlockProvider}. */
    abstract static class BaseProvider implements IProbeInfoProvider {

        @Override
        public void addProbeInfo(
                ProbeMode mode,
                IProbeInfo info,
                Player player,
                Level level,
                BlockState state,
                IProbeHitData hitData) {
            var pos = hitData.getPos();
            if (!(level instanceof ServerLevel) || !level.hasChunkAt(pos)) {
                return;
            }
            BlockEntity be = level.getBlockEntity(pos);
            if (be == null) {
                return;
            }
            CompatTelemetry.appendServerLines(be, hitData.getSideHit(), info::text);
        }
    }
}
