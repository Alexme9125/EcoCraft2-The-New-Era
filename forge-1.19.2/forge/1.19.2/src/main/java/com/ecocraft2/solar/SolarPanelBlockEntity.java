package com.ecocraft2.solar;

import com.ecocraft2.bridge.Ecc2EnergyHelper;
import com.ecocraft2.common.solar.ChargeSink;
import com.ecocraft2.common.solar.SkyCondition;
import com.ecocraft2.common.solar.SolarPanelEngine;
import com.ecocraft2.common.solar.SolarTier;
import com.ecocraft2.config.Ecc2Config;
import com.industrialage.ia2.energy.graph.EnergyGraph;
import com.industrialage.ia2.power.EuBuffer;
import com.industrialage.ia2.power.WorldEnergy;
import com.industrialage.ia2.power.WorldPowerPort;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** BlockEntity for all 4 ASP 2 Solar Panels, wired to IC2NE {@link WorldEnergy} and 4 charge slots. */
public final class SolarPanelBlockEntity extends BlockEntity
        implements WorldPowerPort, EuBuffer, MenuProvider {

    public static final int CHARGE_SLOT_COUNT = 4;

    private final SolarTier solarTier;
    private final SolarPanelEngine engine;
    private long lastTick = Long.MIN_VALUE;

    private final ItemStackHandler chargeSlots =
            new ItemStackHandler(CHARGE_SLOT_COUNT) {
                @Override
                public int getSlotLimit(int slot) {
                    return 1;
                }

                @Override
                public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                    return Ecc2EnergyHelper.acceptsCharge(stack, engine.tier(), true);
                }

                @Override
                protected void onContentsChanged(int slot) {
                    setChanged();
                }
            };

    private LazyOptional<IItemHandler> itemHandlerCap = LazyOptional.of(() -> chargeSlots);

    private final ContainerData syncData =
            new ContainerData() {
                @Override
                public int get(int index) {
                    int stored = (int) Math.floor(engine.storage());
                    int max = engine.maxStorage();
                    return switch (index) {
                        case 0 -> stored & 0xFFFF;
                        case 1 -> (stored >>> 16) & 0xFFFF;
                        case 2 -> max & 0xFFFF;
                        case 3 -> (max >>> 16) & 0xFFFF;
                        case 4 -> engine.currentGenerating();
                        case 5 -> engine.skyCondition().ordinal();
                        case 6 -> engine.tier();
                        default -> 0;
                    };
                }

                @Override
                public void set(int index, int value) {}

                @Override
                public int getCount() {
                    return 7;
                }
            };

    public SolarPanelBlockEntity(BlockPos pos, BlockState state) {
        this(
                pos,
                state,
                state.getBlock() instanceof SolarPanelBlock block
                        ? block.solarTier()
                        : SolarTier.ADVANCED);
    }

    public SolarPanelBlockEntity(BlockPos pos, BlockState state, SolarTier solarTier) {
        super(SolarKineticRegistry.SOLAR_PANEL_BE.get(), pos, state);
        this.solarTier = solarTier;
        this.engine =
                new SolarPanelEngine(
                        configuredDayPower(solarTier),
                        configuredNightPower(solarTier),
                        configuredStorage(solarTier),
                        configuredTier(solarTier));
    }

    public static int configuredDayPower(SolarTier tier) {
        return switch (tier) {
            case ADVANCED -> Ecc2Config.advancedSolarGenDay();
            case HYBRID -> Ecc2Config.hybridSolarGenDay();
            case ULTIMATE_HYBRID -> Ecc2Config.ultimateSolarGenDay();
            case QUANTUM -> Ecc2Config.quantumSolarGenDay();
        };
    }

    public static int configuredNightPower(SolarTier tier) {
        return switch (tier) {
            case ADVANCED -> Ecc2Config.advancedSolarGenNight();
            case HYBRID -> Ecc2Config.hybridSolarGenNight();
            case ULTIMATE_HYBRID -> Ecc2Config.ultimateSolarGenNight();
            case QUANTUM -> Ecc2Config.quantumSolarGenNight();
        };
    }

    public static int configuredStorage(SolarTier tier) {
        return switch (tier) {
            case ADVANCED -> Ecc2Config.advancedSolarStorage();
            case HYBRID -> Ecc2Config.hybridSolarStorage();
            case ULTIMATE_HYBRID -> Ecc2Config.ultimateSolarStorage();
            case QUANTUM -> Ecc2Config.quantumSolarStorage();
        };
    }

    public static int configuredTier(SolarTier tier) {
        return switch (tier) {
            case ADVANCED -> Ecc2Config.advancedSolarTier();
            case HYBRID -> Ecc2Config.hybridSolarTier();
            case ULTIMATE_HYBRID -> Ecc2Config.ultimateSolarTier();
            case QUANTUM -> Ecc2Config.quantumSolarTier();
        };
    }

    public SolarTier solarTier() {
        return solarTier;
    }

    public SolarPanelEngine engine() {
        return engine;
    }

    public ItemStackHandler chargeSlots() {
        return chargeSlots;
    }

    public ContainerData syncData() {
        return syncData;
    }

    public Container dropInventory() {
        SimpleContainer drops = new SimpleContainer(CHARGE_SLOT_COUNT);
        for (int i = 0; i < CHARGE_SLOT_COUNT; i++) {
            drops.setItem(i, chargeSlots.getStackInSlot(i));
        }
        return drops;
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        lastTick = serverLevel.getGameTime();
        BlockPos above = worldPosition.above();
        boolean hasSky = serverLevel.dimensionType().hasSkyLight();
        boolean seesSky = hasSky && serverLevel.canSeeSky(above);
        boolean isDay = serverLevel.isDay();
        boolean isRaining = serverLevel.isRaining() || serverLevel.isThundering();
        Biome biome = serverLevel.getBiome(above).value();
        boolean canRain = biome.getPrecipitation() != Biome.Precipitation.NONE;

        List<ChargeSink> sinks = new ArrayList<>(CHARGE_SLOT_COUNT);
        for (int i = 0; i < CHARGE_SLOT_COUNT; i++) {
            final int slotIdx = i;
            ItemStack stack = chargeSlots.getStackInSlot(slotIdx);
            if (!stack.isEmpty()) {
                sinks.add(
                        (offer, tier) ->
                                Ecc2EnergyHelper.chargeStack(
                                        chargeSlots.getStackInSlot(slotIdx), offer, tier, true));
            }
        }
        double before = engine.storage();
        double charged = engine.tick(hasSky, seesSky, isDay, isRaining, canRain, sinks);
        if (charged > 0.0 || engine.storage() != before) {
            setChanged();
        }
    }

    // ---- WorldEnergy / WorldPowerPort / EuBuffer ----

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel) {
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
    public void invalidateCaps() {
        super.invalidateCaps();
        itemHandlerCap.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        itemHandlerCap = LazyOptional.of(() -> chargeSlots);
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(
            @NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return itemHandlerCap.cast();
        }
        return super.getCapability(cap, side);
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
        return engine.offeredEnergy();
    }

    @Override
    public int packetsPerTick() {
        return 1;
    }

    @Override
    public double draw(double amount) {
        double drawn = engine.drawEnergy(amount);
        if (drawn > 0.0) {
            setChanged();
        }
        return drawn;
    }

    @Override
    public double packetLimit() {
        return engine.packetSize();
    }

    @Override
    public EuBuffer.Reading bufferedEu() {
        return new EuBuffer.Reading(engine.storage(), engine.maxStorage());
    }

    // ---- NBT & MenuProvider ----

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putDouble("Storage", engine.storage());
        tag.putInt("SkyCondition", engine.skyCondition().ordinal());
        tag.putInt("TickCounter", engine.tickCounter());
        tag.put("ChargeSlots", chargeSlots.serializeNBT());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        engine.setStorage(tag.getDouble("Storage"));
        int condOrd = tag.getInt("SkyCondition");
        SkyCondition[] values = SkyCondition.values();
        if (condOrd >= 0 && condOrd < values.length) {
            engine.setSkyCondition(values[condOrd]);
        }
        engine.setTickCounter(tag.getInt("TickCounter"));
        if (tag.contains("ChargeSlots")) {
            chargeSlots.deserializeNBT(tag.getCompound("ChargeSlots"));
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.ecera." + solarTier.blockPath());
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new SolarPanelMenu(containerId, playerInv, this, syncData);
    }
}
