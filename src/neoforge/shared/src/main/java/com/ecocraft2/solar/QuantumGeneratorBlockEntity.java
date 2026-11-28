package com.ecocraft2.solar;

import com.ecocraft2.common.solar.QuantumGeneratorEngine;
import com.ecocraft2.config.Ecc2Config;
import com.industrialage.ia2.energy.graph.EnergyGraph;
import com.industrialage.ia2.platform.CapabilityFacade.Face;
import com.industrialage.ia2.power.EuBuffer;
import com.industrialage.ia2.power.WorldEnergy;
import com.industrialage.ia2.power.WorldPowerPort;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block entity for the creative {@code ecc2:quantum_generator}.
 *
 * <p>Emits exact multi-packet EU across all six faces with a no-op {@link #draw(double)} (creative
 * infinite source) and redstone-inverted active control.
 */
public final class QuantumGeneratorBlockEntity extends Ecc2SaveBlockEntity
        implements WorldPowerPort, EuBuffer, MenuProvider {

    private static final Set<Face> ALL_FACES = Set.of(Face.values());
    private static final EnergyGraph.Ports SOURCE_PORTS =
            new EnergyGraph.Ports(Set.of(), ALL_FACES, false);

    private final QuantumGeneratorEngine engine;
    private long lastTickedGameTime = Long.MIN_VALUE;

    public QuantumGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(SolarKineticModule.QUANTUM_GENERATOR_BE.get(), pos, state);
        this.engine =
                new QuantumGeneratorEngine(
                        Ecc2Config.quantumGeneratorDefaultProduction(),
                        Ecc2Config.quantumGeneratorDefaultTier());
    }

    /** Server tick maintaining redstone-inverted active state and power-grid participation. */
    public void serverTick(ServerLevel level) {
        lastTickedGameTime = level.getGameTime();
        syncRedstone(level);
    }

    /** Evaluates redstone power and synchronizes both engine and blockstate {@code active}. */
    public void syncRedstone(Level level) {
        boolean powered = level.hasNeighborSignal(worldPosition);
        boolean wasActive = engine.isActive();
        engine.updateRedstone(powered);
        boolean nowActive = engine.isActive();
        BlockState state = getBlockState();
        if (state.hasProperty(QuantumGeneratorBlock.ACTIVE)
                && state.getValue(QuantumGeneratorBlock.ACTIVE) != nowActive) {
            level.setBlock(
                    worldPosition,
                    state.setValue(QuantumGeneratorBlock.ACTIVE, nowActive),
                    Block.UPDATE_ALL);
        }
        if (wasActive != nowActive) {
            setChanged();
        }
    }

    /** Applies a validated GUI button event (0..5, 10..15, 20..25). */
    public boolean handleButtonEvent(int eventId) {
        if (engine.handleButtonEvent(eventId)) {
            setChanged();
            return true;
        }
        return false;
    }

    // ---- Public getters for Menus, Jade, and TOP ----

    public QuantumGeneratorEngine getEngine() {
        return engine;
    }

    public int getProduction() {
        return engine.production();
    }

    public int getTier() {
        return engine.tier();
    }

    public boolean isActive() {
        return engine.isActive();
    }

    public int getPacketCount() {
        return engine.packetCount();
    }

    public double getOfferedPerPacket() {
        return engine.offeredPerPacket();
    }

    // ---- WorldPowerPort & EuBuffer ----

    @Override
    public void onLoad() {
        super.onLoad();
        // Defer redstone / blockstate synchronization to the first serverTick() so chunk loading
        // never mutates world blockstates or queries neighbor chunks mid-load.
        WorldEnergy.connect(this);
    }

    @Override
    public void onChunkUnloaded() {
        WorldEnergy.disconnect(this);
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        WorldEnergy.disconnect(this);
        super.setRemoved();
    }

    @Override
    public boolean ranThisTick(ServerLevel level) {
        return lastTickedGameTime == level.getGameTime();
    }

    @Override
    public EnergyGraph.Ports energyPorts() {
        return SOURCE_PORTS;
    }

    @Override
    public boolean source() {
        return true;
    }

    @Override
    public int packetsPerTick() {
        return engine.packetCount();
    }

    @Override
    public double offer() {
        return engine.offeredPerPacket();
    }

    @Override
    public double draw(double amount) {
        // Creative generator never depletes.
        return amount;
    }

    @Override
    public EuBuffer.Reading bufferedEu() {
        double prod = engine.production();
        return new EuBuffer.Reading(engine.isActive() ? prod : 0.0, Math.max(1.0, prod));
    }

    // ---- Persistence ----

    @Override
    protected void saveEcc2(Ecc2TagIO.Writer out) {
        out.putInt("production", engine.production());
        out.putInt("tier", engine.tier());
        out.putBoolean("active", engine.isActive());
    }

    @Override
    protected void loadEcc2(Ecc2TagIO.Reader in) {
        engine.setProduction(
                in.getInt("production", Ecc2Config.quantumGeneratorDefaultProduction()));
        engine.setTier(in.getInt("tier", Ecc2Config.quantumGeneratorDefaultTier()));
        engine.updateRedstone(!in.getBoolean("active", true));
    }

    // ---- MenuProvider ----

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.ecera.quantum_generator");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new QuantumGeneratorMenu(containerId, playerInv, this);
    }
}
