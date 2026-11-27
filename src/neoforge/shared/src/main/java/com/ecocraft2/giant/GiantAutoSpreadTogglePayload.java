package com.ecocraft2.giant;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-to-server payload toggling automatic 9-slot input spreading on a formed 3x3x3 Giant
 * Machine, with full server ownership, menu, and reach validation.
 */
public record GiantAutoSpreadTogglePayload(BlockPos corePos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<GiantAutoSpreadTogglePayload> TYPE =
            GiantGasPlatform.payloadType("giant_auto_spread_toggle");

    public static final StreamCodec<RegistryFriendlyByteBuf, GiantAutoSpreadTogglePayload>
            STREAM_CODEC =
                    StreamCodec.of(
                            (buf, payload) -> buf.writeBlockPos(payload.corePos()),
                            buf -> new GiantAutoSpreadTogglePayload(buf.readBlockPos()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Validates sender permissions and toggles auto-spread on the targeted Giant Machine core. */
    public static void handleServer(GiantAutoSpreadTogglePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (player.isSpectator() || !player.mayBuild()) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos corePos = payload.corePos();
        if (!level.hasChunkAt(corePos) || !level.mayInteract(player, corePos)) {
            return;
        }
        if (player.distanceToSqr(
                        corePos.getX() + 0.5, corePos.getY() + 1.0, corePos.getZ() + 0.5)
                > 100.0) {
            return;
        }
        if (!(player.containerMenu instanceof GiantMachineMenu menu)
                || !menu.corePos().equals(corePos)) {
            return;
        }
        if (!(level.getBlockEntity(corePos) instanceof GiantUnitBlockEntity core)
                || !core.isFormed()
                || core.role() != GiantBlockRole.CORE
                || core.isRemoved()) {
            return;
        }
        core.toggleAutoSpread();
    }
}
