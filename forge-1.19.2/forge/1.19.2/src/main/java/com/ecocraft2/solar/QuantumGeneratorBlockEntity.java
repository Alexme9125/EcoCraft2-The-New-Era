package com.ecocraft2.solar;

import com.ecocraft2.bridge.Ecc2EnergyHelper;
import com.ecocraft2.common.solar.QuantumGeneratorEngine;
import com.ecocraft2.config.Ecc2Config;
import com.industrialage.ia2.energy.graph.EnergyGraph;
import com.industrialage.ia2.power.WorldEnergy;
import com.industrialage.ia2.power.WorldPowerPort;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** BlockEntity for the creative multi-packet Quantum Generator (redstone-inverted, Tier 1..6 MAX). */
public final class QuantumGeneratorBlockEntity extends BlockEntity
        implements WorldPowerPort, MenuProvider {

    private final QuantumGeneratorEngine engine;
    private long lastTick = Long.MIN_VALUE;

    private final ContainerData syncData =
            new ContainerData() {
                @Override
                public int get(int index) {
                    int prod = engine.production();
                    return switch (index) {
                        case 0 -> prod & 0xFFFF;
                        case 1 -> (prod >>> 16) & 0xFFFF;
                        case 2 -> engine.tier();
                        case 3 -> engine.isActive() ? 1 : 0;
                        case 4 -> engine.packetCount();
                        default -> 0;
                    };
                }

                @Override
                public void set(int index, int value) {}

                @Override
                public int getCount() {
                    return 5;
                }
            };

    public QuantumGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(SolarKineticRegistry.QUANTUM_GENERATOR_BE.get(), pos, state);
        this.engine =
                new QuantumGeneratorEngine(
                        Ecc2Config.quantumGeneratorDefaultProduction(),
                        Ecc2Config.quantumGeneratorDefaultTier());
    }

    public QuantumGeneratorEngine engine() {
        return engine;
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        lastTick = serverLevel.getGameTime();
        refreshRedstone();
    }

    public void refreshRedstone() {
        if (level == null || level.isClientSide) {
            return;
        }
        boolean powered = level.hasNeighborSignal(worldPosition);
        boolean wasActive = engine.isActive();
        engine.updateRedstone(powered);
        BlockState state = getBlockState();
        if (state.hasProperty(QuantumGeneratorBlock.ACTIVE)
                && state.getValue(QuantumGeneratorBlock.ACTIVE) != engine.isActive()) {
            level.setBlock(
                    worldPosition,
                    state.setValue(QuantumGeneratorBlock.ACTIVE, engine.isActive()),
                    Block.UPDATE_CLIENTS);
        }
        if (wasActive != engine.isActive()) {
            setChanged();
        }
    }

    public void handleButtonEvent(int buttonId) {
        if (engine.handleButtonEvent(buttonId)) {
            setChanged();
        }
    }

    // ---- WorldEnergy / WorldPowerPort ----

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel) {
            refreshRedstone();
            WorldEnergy.connect(this);
        }
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
        return lastTick == level.getGameTime();
    }

    @Override
    public EnergyGraph.Ports energyPorts() {
        return Ecc2EnergyHelper.ALL_OUTPUT_PORTS;
    }

    @Override
    public boolean source() {
        return true;
    }

    @Override
    public double offer() {
        return engine.offeredPerPacket();
    }

    @Override
    public int packetsPerTick() {
        return engine.packetCount();
    }

    @Override
    public double draw(double amount) {
        if (!engine.isActive() || amount <= 0.0 || !Double.isFinite(amount)) {
            return 0.0;
        }
        return Math.min(amount, engine.offeredPerPacket());
    }

    @Override
    public double packetLimit() {
        return engine.tierPacketSize();
    }

    // ---- NBT & MenuProvider ----

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Production", engine.production());
        tag.putInt("Tier", engine.tier());
        tag.putBoolean("Active", engine.isActive());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Production")) {
            engine.setProduction(tag.getInt("Production"));
        }
        if (tag.contains("Tier")) {
            engine.setTier(tag.getInt("Tier"));
        }
        if (tag.contains("Active")) {
            engine.updateRedstone(!tag.getBoolean("Active"));
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.ecc2.quantum_generator");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new QuantumGeneratorMenu(containerId, playerInv, worldPosition, syncData);
    }
}
