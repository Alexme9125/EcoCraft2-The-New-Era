package com.ecocraft2.solar;

import com.ecocraft2.bridge.Ecc2ElectricItem;
import com.ecocraft2.common.solar.ChargeSink;
import com.ecocraft2.common.solar.SkyCondition;
import com.ecocraft2.common.solar.SolarPanelEngine;
import com.ecocraft2.common.solar.SolarTier;
import com.ecocraft2.config.Ecc2Config;
import com.industrialage.ia2.access.MachineAccess;
import com.industrialage.ia2.access.MachineAccessTarget;
import com.industrialage.ia2.energy.graph.EnergyGraph;
import com.industrialage.ia2.gameplay.PortableGear;
import com.industrialage.ia2.platform.CapabilityFacade.Face;
import com.industrialage.ia2.power.EuBuffer;
import com.industrialage.ia2.power.WorldEnergy;
import com.industrialage.ia2.power.WorldPowerPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block entity backing the four Advanced Solar Panels 2 generators.
 *
 * <p>Charges up to four electric items BEFORE offering remaining EU to the IC2NE energy network.
 * Explicitly relaxes the charger tier check for EcoCraft 2's own {@link SolarHelmetItem}s while
 * preserving standard IC2 voltage tier validation for all other electric items.
 */
public final class SolarPanelBlockEntity extends Ecc2SaveBlockEntity
        implements WorldPowerPort, EuBuffer, MachineAccessTarget, WorldlyContainer, MenuProvider {

    /** Number of item charging slots in every solar panel. */
    public static final int CHARGE_SLOTS = 4;

    private static final int[] ALL_SLOTS = {0, 1, 2, 3};
    private static final int[] EMPTY_SLOTS = new int[0];
    private static final Set<Face> ALL_FACES = Set.of(Face.values());
    private static final EnergyGraph.Ports SOURCE_PORTS =
            new EnergyGraph.Ports(Set.of(), ALL_FACES, false);

    private final SolarTier solarTier;
    private final SolarPanelEngine engine;
    private final NonNullList<ItemStack> items =
            NonNullList.withSize(CHARGE_SLOTS, ItemStack.EMPTY);
    private long lastTickedGameTime = Long.MIN_VALUE;

    /** Constructs a solar panel block entity inferring its tier from {@code state}. */
    public SolarPanelBlockEntity(BlockPos pos, BlockState state) {
        this(
                pos,
                state,
                state.getBlock() instanceof SolarPanelBlock block
                        ? block.getSolarTier()
                        : SolarTier.ADVANCED);
    }

    /** Constructs a solar panel block entity for the specified {@link SolarTier}. */
    public SolarPanelBlockEntity(BlockPos pos, BlockState state, SolarTier solarTier) {
        super(SolarKineticModule.SOLAR_PANEL_BE.get(), pos, state);
        this.solarTier = solarTier;
        this.engine = createConfiguredEngine(solarTier);
    }

    private static SolarPanelEngine createConfiguredEngine(SolarTier tier) {
        return switch (tier) {
            case ADVANCED ->
                    new SolarPanelEngine(
                            Ecc2Config.advancedSolarGenDay(),
                            Ecc2Config.advancedSolarGenNight(),
                            Ecc2Config.advancedSolarStorage(),
                            Ecc2Config.advancedSolarTier());
            case HYBRID ->
                    new SolarPanelEngine(
                            Ecc2Config.hybridSolarGenDay(),
                            Ecc2Config.hybridSolarGenNight(),
                            Ecc2Config.hybridSolarStorage(),
                            Ecc2Config.hybridSolarTier());
            case ULTIMATE_HYBRID ->
                    new SolarPanelEngine(
                            Ecc2Config.ultimateSolarGenDay(),
                            Ecc2Config.ultimateSolarGenNight(),
                            Ecc2Config.ultimateSolarStorage(),
                            Ecc2Config.ultimateSolarTier());
            case QUANTUM ->
                    new SolarPanelEngine(
                            Ecc2Config.quantumSolarGenDay(),
                            Ecc2Config.quantumSolarGenNight(),
                            Ecc2Config.quantumSolarStorage(),
                            Ecc2Config.quantumSolarTier());
        };
    }

    /** Runs one server tick: sky evaluation, EU generation, and slot charging before grid draw. */
    public void serverTick(ServerLevel level) {
        lastTickedGameTime = level.getGameTime();
        double beforeStorage = engine.storage();
        SkyCondition beforeSky = engine.skyCondition();

        boolean hasSky = level.dimensionType().hasSkyLight();
        boolean seesSky = level.canSeeSky(worldPosition.above());
        boolean isDay = SolarPlatform.isDay(level);
        boolean isRaining = level.isRaining() || level.isThundering();
        boolean canRain = level.getBiome(worldPosition).value().hasPrecipitation();

        List<ChargeSink> sinks = null;
        for (int slot = 0; slot < CHARGE_SLOTS; slot++) {
            ItemStack stack = items.get(slot);
            if (!stack.isEmpty() && acceptsChargeItem(stack)) {
                if (sinks == null) {
                    sinks = new ArrayList<>(CHARGE_SLOTS);
                }
                final int index = slot;
                sinks.add((availableEu, panelTier) -> chargeSlotStack(index, availableEu, panelTier));
            }
        }

        double charged =
                engine.tick(
                        hasSky,
                        seesSky,
                        isDay,
                        isRaining,
                        canRain,
                        sinks == null ? List.of() : sinks);

        if (charged > 0.0
                || engine.storage() != beforeStorage
                || engine.skyCondition() != beforeSky) {
            setChanged();
        }
    }

    private double chargeSlotStack(int slot, double availableEu, int panelTier) {
        ItemStack stack = items.get(slot);
        if (stack.isEmpty() || stack.getCount() != 1 || availableEu <= 0.0) {
            return 0.0;
        }
        // Relax tier restriction for EcoCraft 2's own Solar Helmets so players can charge them
        // inside any EcoCraft 2 solar panel tier.
        if (stack.getItem() instanceof SolarHelmetItem helmet) {
            int effectiveTier = Math.max(panelTier, helmet.ecc2Tier(stack));
            return helmet.ecc2Charge(stack, availableEu, effectiveTier, false, false);
        }
        if (stack.getItem() instanceof Ecc2ElectricItem electric) {
            return electric.ecc2Charge(stack, availableEu, panelTier, false, false);
        }
        return PortableGear.charge(stack, availableEu, panelTier);
    }

    /**
     * Returns {@code true} if {@code stack} may be placed into a charging slot of this panel.
     * Own solar helmets bypass the panel's voltage tier check; all other electric items enforce it.
     */
    public boolean acceptsChargeItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof SolarHelmetItem) {
            return true;
        }
        if (stack.getItem() instanceof Ecc2ElectricItem electric) {
            return electric.ecc2Tier(stack) <= engine.tier();
        }
        return PortableGear.acceptsCharge(stack, engine.tier());
    }

    /** Returns {@code true} if the electric item in a charge slot has reached full charge. */
    public boolean isChargeComplete(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof SolarHelmetItem helmet) {
            return helmet.ecc2Stored(stack) >= helmet.ecc2Capacity(stack);
        }
        if (stack.getItem() instanceof Ecc2ElectricItem electric) {
            return electric.ecc2Stored(stack) >= electric.ecc2Capacity(stack);
        }
        return PortableGear.chargeComplete(stack, engine.tier());
    }

    // ---- Public getters for Menus, Jade, and TOP ----

    public SolarTier getSolarTier() {
        return solarTier;
    }

    public SolarPanelEngine getEngine() {
        return engine;
    }

    public double getStoredEu() {
        return engine.storage();
    }

    public int getMaxStorage() {
        return engine.maxStorage();
    }

    public int getCurrentGenerating() {
        return engine.currentGenerating();
    }

    public SkyCondition getSkyCondition() {
        return engine.skyCondition();
    }

    public int getOutputTier() {
        return engine.tier();
    }

    public double getMaxOutput() {
        return engine.packetSize();
    }

    // ---- WorldPowerPort / EuBuffer / EnergyNetwork lifecycle ----

    @Override
    public void onLoad() {
        super.onLoad();
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
    public EuBuffer.Reading bufferedEu() {
        return new EuBuffer.Reading(engine.storage(), engine.maxStorage());
    }

    // ---- Persistence ----

    @Override
    protected void saveEcc2(Ecc2TagIO.Writer out) {
        out.putItems("items", items);
        out.putDouble("eu", engine.storage());
        out.putInt("sky", engine.skyCondition().ordinal());
        out.putInt("sky_tick", engine.tickCounter());
    }

    @Override
    protected void loadEcc2(Ecc2TagIO.Reader in) {
        in.loadItems("items", items);
        engine.setStorage(in.getDouble("eu", 0.0));
        int skyOrd = in.getInt("sky", SkyCondition.NONE.ordinal());
        var values = SkyCondition.values();
        engine.setSkyCondition(
                skyOrd >= 0 && skyOrd < values.length ? values[skyOrd] : SkyCondition.NONE);
        engine.setTickCounter(in.getInt("sky_tick", 0));
    }

    @Override
    protected Container dropInventory() {
        return this;
    }

    // ---- WorldlyContainer (4 charging slots) ----

    @Override
    public int[] getSlotsForFace(Direction side) {
        return MachineAccess.connects(this, side) ? ALL_SLOTS : EMPTY_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack stack, Direction direction) {
        return MachineAccess.allowsInput(this, direction) && canPlaceItem(index, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
        return MachineAccess.allowsOutput(this, direction) && isChargeComplete(stack);
    }

    @Override
    public int getContainerSize() {
        return CHARGE_SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (var stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < CHARGE_SLOTS ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        var result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) {
            setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= CHARGE_SLOTS) {
            return;
        }
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot >= 0 && slot < CHARGE_SLOTS && acceptsChargeItem(stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < CHARGE_SLOTS; i++) {
            items.set(i, ItemStack.EMPTY);
        }
        setChanged();
    }

    // ---- MenuProvider ----

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.ecera." + solarTier.blockPath());
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new SolarPanelMenu(containerId, playerInv, this);
    }
}
