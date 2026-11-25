package com.ecocraft2.network;

import com.ecocraft2.bridge.Ecc2BridgePlatform;
import com.ecocraft2.common.giant.GiantCornerPort;
import com.ecocraft2.common.solar.QuantumGeneratorEngine;
import com.ecocraft2.giant.GiantMachineBlockEntity;
import com.ecocraft2.solar.QuantumGeneratorBlockEntity;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/** Forge 1.19.2 {@link SimpleChannel} networking for EcoCraft 2 interactive machine GUIs. */
public final class Ecc2Network {

    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL =
            NetworkRegistry.newSimpleChannel(
                    Ecc2BridgePlatform.rl("main"),
                    () -> PROTOCOL,
                    PROTOCOL::equals,
                    PROTOCOL::equals);

    private static int nextId = 0;

    private Ecc2Network() {}

    /** Registers all EcoCraft 2 C2S/S2C network packets. */
    public static void register() {
        CHANNEL.messageBuilder(
                        QuantumGeneratorButtonPacket.class,
                        nextId++,
                        NetworkDirection.PLAY_TO_SERVER)
                .encoder(QuantumGeneratorButtonPacket::encode)
                .decoder(QuantumGeneratorButtonPacket::decode)
                .consumerMainThread(QuantumGeneratorButtonPacket::handle)
                .add();

        CHANNEL.messageBuilder(
                        GiantPortTogglePacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(GiantPortTogglePacket::encode)
                .decoder(GiantPortTogglePacket::decode)
                .consumerMainThread(GiantPortTogglePacket::handle)
                .add();
    }

    /** Sends a validated Quantum Generator GUI button click to the server. */
    public static void sendQuantumButton(BlockPos pos, int buttonId) {
        CHANNEL.sendToServer(new QuantumGeneratorButtonPacket(pos, buttonId));
    }

    /** Sends a Giant Machine lower-corner port mode cycle request to the server. */
    public static void sendGiantPortToggle(BlockPos pos, GiantCornerPort port) {
        CHANNEL.sendToServer(new GiantPortTogglePacket(pos, port.ordinal()));
    }

    /** C2S packet for Quantum Generator production/tier buttons (validated by common engine). */
    public record QuantumGeneratorButtonPacket(BlockPos pos, int buttonId) {

        public static void encode(QuantumGeneratorButtonPacket msg, FriendlyByteBuf buf) {
            buf.writeBlockPos(msg.pos);
            buf.writeVarInt(msg.buttonId);
        }

        public static QuantumGeneratorButtonPacket decode(FriendlyByteBuf buf) {
            return new QuantumGeneratorButtonPacket(buf.readBlockPos(), buf.readVarInt());
        }

        public static void handle(
                QuantumGeneratorButtonPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ServerPlayer player = ctx.getSender();
            if (player == null || !QuantumGeneratorEngine.isValidButtonEvent(msg.buttonId)) {
                ctx.setPacketHandled(true);
                return;
            }
            if (player.distanceToSqr(
                            msg.pos.getX() + 0.5, msg.pos.getY() + 0.5, msg.pos.getZ() + 0.5)
                    <= 64.0
                    && player.level.hasChunkAt(msg.pos)
                    && player.level.getBlockEntity(msg.pos)
                            instanceof QuantumGeneratorBlockEntity qg) {
                qg.handleButtonEvent(msg.buttonId);
            }
            ctx.setPacketHandled(true);
        }
    }

    /** C2S packet cycling one of the 8 lower-corner access ports on a formed Giant Machine. */
    public record GiantPortTogglePacket(BlockPos pos, int portOrdinal) {

        public static void encode(GiantPortTogglePacket msg, FriendlyByteBuf buf) {
            buf.writeBlockPos(msg.pos);
            buf.writeVarInt(msg.portOrdinal);
        }

        public static GiantPortTogglePacket decode(FriendlyByteBuf buf) {
            return new GiantPortTogglePacket(buf.readBlockPos(), buf.readVarInt());
        }

        public static void handle(
                GiantPortTogglePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ServerPlayer player = ctx.getSender();
            GiantCornerPort[] ports = GiantCornerPort.values();
            if (player == null || msg.portOrdinal < 0 || msg.portOrdinal >= ports.length) {
                ctx.setPacketHandled(true);
                return;
            }
            if (player.distanceToSqr(
                            msg.pos.getX() + 0.5, msg.pos.getY() + 0.5, msg.pos.getZ() + 0.5)
                    <= 144.0
                    && player.level.hasChunkAt(msg.pos)
                    && player.level.getBlockEntity(msg.pos)
                            instanceof GiantMachineBlockEntity unit) {
                GiantMachineBlockEntity core = unit.resolveCore();
                if (core != null) {
                    core.cycleCornerPort(ports[msg.portOrdinal]);
                }
            }
            ctx.setPacketHandled(true);
        }
    }
}
