package com.ecocraft2.solar;

import com.ecocraft2.common.solar.QuantumGeneratorEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S payload sent when a player clicks a production or tier button inside
 * {@link QuantumGeneratorScreen}.
 *
 * <p>Validates container ownership, interaction distance, chunk load state, and strict event ID
 * range ({@code 0..5}, {@code 10..15}, {@code 20..25}) on the server main thread.
 */
public record QuantumGeneratorButtonPayload(BlockPos pos, int eventId)
        implements CustomPacketPayload {

    public static final Type<QuantumGeneratorButtonPayload> TYPE =
            SolarPlatform.payloadType("quantum_generator_button");

    public static final StreamCodec<RegistryFriendlyByteBuf, QuantumGeneratorButtonPayload>
            STREAM_CODEC =
                    StreamCodec.composite(
                            BlockPos.STREAM_CODEC,
                            QuantumGeneratorButtonPayload::pos,
                            ByteBufCodecs.VAR_INT,
                            QuantumGeneratorButtonPayload::eventId,
                            QuantumGeneratorButtonPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Server-side payload handler with full security and range checks. */
    public static void handleServer(
            QuantumGeneratorButtonPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        if (!QuantumGeneratorEngine.isValidButtonEvent(payload.eventId())) {
            return;
        }
        if (!(player.containerMenu instanceof QuantumGeneratorMenu menu)
                || !menu.isForPos(payload.pos())
                || !menu.stillValid(player)) {
            return;
        }
        if (player.distanceToSqr(Vec3.atCenterOf(payload.pos())) > 64.0) {
            return;
        }
        var level = player.level();
        if (!level.hasChunkAt(payload.pos())) {
            return;
        }
        if (level.getBlockEntity(payload.pos()) instanceof QuantumGeneratorBlockEntity qg
                && !qg.isRemoved()) {
            qg.handleButtonEvent(payload.eventId());
        }
    }
}
