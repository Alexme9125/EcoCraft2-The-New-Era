package com.ecocraft2.giant;

import com.ecocraft2.common.giant.GiantCornerPort;
import com.ecocraft2.common.giant.PortAccessMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-to-server payload toggling or setting one of the 8 lower-corner port permissions on a
 * formed 3x3x3 Giant Machine, with full server ownership, menu, and reach validation.
 */
public record GiantPortTogglePayload(BlockPos corePos, int portOrdinal, int modeCode)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<GiantPortTogglePayload> TYPE =
            GiantGasPlatform.payloadType("giant_port_toggle");

    public static final StreamCodec<RegistryFriendlyByteBuf, GiantPortTogglePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeBlockPos(payload.corePos());
                        buf.writeVarInt(payload.portOrdinal());
                        buf.writeVarInt(payload.modeCode());
                    },
                    buf ->
                            new GiantPortTogglePayload(
                                    buf.readBlockPos(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Validates sender permissions and applies the requested lower-corner port mode change. */
    public static void handleServer(GiantPortTogglePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (player.isSpectator() || !player.mayBuild()) {
            return;
        }
        int ordinal = payload.portOrdinal();
        GiantCornerPort[] ports = GiantCornerPort.values();
        if (ordinal < 0 || ordinal >= ports.length) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos corePos = payload.corePos();
        if (!level.hasChunkAt(corePos) || !level.mayInteract(player, corePos)) {
            return;
        }
        // 3x3x3 reach: players standing near outer corners can be slightly further from bottom-center.
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
                || core.role() != GiantBlockRole.CORE) {
            return;
        }
        GiantCornerPort port = ports[ordinal];
        int requestedMode = payload.modeCode();
        if (requestedMode < 0) {
            core.cyclePortPermission(level, port);
        } else if (requestedMode <= 3) {
            core.setPortPermission(level, port, PortAccessMode.byCode(requestedMode));
        }
    }
}
