package com.ecocraft2.compat;

import com.ecocraft2.common.gas.CultureIncubatorEngine;
import com.ecocraft2.common.gas.GasFuelSpec;
import com.ecocraft2.common.gas.GasFurnaceEngine;
import com.ecocraft2.common.gas.GasGeneratorEngine;
import com.ecocraft2.common.gas.OrganicFermenterEngine;
import com.ecocraft2.common.gas.WaterGasEngine;
import com.ecocraft2.common.giant.GiantCornerPort;
import com.ecocraft2.common.giant.PortAccessMode;
import com.ecocraft2.common.kinetic.KineticMachineTier;
import com.ecocraft2.common.solar.SkyCondition;
import com.ecocraft2.common.solar.SolarTier;
import com.ecocraft2.gas.CultureIncubatorBlockEntity;
import com.ecocraft2.gas.GasFurnaceBlockEntity;
import com.ecocraft2.gas.GasGeneratorBlockEntity;
import com.ecocraft2.gas.OrganicFermenterBlockEntity;
import com.ecocraft2.gas.WaterGasMachineBlockEntity;
import com.ecocraft2.giant.GiantUnitBlockEntity;
import com.ecocraft2.kinetic.AdvancedKineticBlockEntity;
import com.ecocraft2.kinetic.AdvancedKineticPhysics;
import com.ecocraft2.solar.Ecc2DataSync;
import com.ecocraft2.solar.MolecularTransformerBlockEntity;
import com.ecocraft2.solar.QuantumGeneratorBlockEntity;
import com.ecocraft2.solar.SolarPanelBlockEntity;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Shared authoritative server-side telemetry extractor and formatter for Jade and The One Probe.
 *
 * <p>Has zero imports from third-party compat APIs so it can safely live in {@code shared/} across
 * NeoForge 1.21.1, 26.1.2, and 26.2.
 */
public final class CompatTelemetry {

    // Machine discriminators stored in Jade server sync NBT
    private static final int KIND_SOLAR = 1;
    private static final int KIND_QUANTUM_GEN = 2;
    private static final int KIND_MOLECULAR_TRANSFORMER = 3;
    private static final int KIND_KINETIC = 4;
    private static final int KIND_GIANT = 5;
    private static final int KIND_WATER_GAS = 6;
    private static final int KIND_GAS_FURNACE = 7;
    private static final int KIND_GAS_GENERATOR = 8;
    private static final int KIND_CULTURE_INCUBATOR = 9;
    private static final int KIND_ORGANIC_FERMENTER = 10;

    private static final String KEY_KIND = "Ecc2Kind";

    private CompatTelemetry() {}

    /** Version-neutral read-only view over a Minecraft {@link CompoundTag}. */
    public interface TagView {
        boolean contains(String key);

        int getInt(String key, int fallback);

        long getLong(String key, long fallback);

        double getDouble(String key, double fallback);

        boolean getBoolean(String key, boolean fallback);

        String getString(String key, String fallback);
    }

    /**
     * Serializes authoritative server-side telemetry for any EcoCraft 2 {@link BlockEntity} into a
     * Jade sync {@link CompoundTag}. Evaluates {@link GiantCornerPort} on the server so corner port
     * telemetry never relies on client-side core coordinates.
     */
    public static void writeServerData(CompoundTag tag, BlockEntity be, Direction hitSide) {
        if (be instanceof SolarPanelBlockEntity panel) {
            tag.putInt(KEY_KIND, KIND_SOLAR);
            tag.putLong("Eu", Math.round(panel.getStoredEu()));
            tag.putLong("EuMax", panel.getMaxStorage());
            tag.putInt("Sky", panel.getSkyCondition().ordinal());
            tag.putInt("Gen", panel.getCurrentGenerating());
            tag.putLong("MaxOut", Math.round(panel.getMaxOutput()));
            tag.putInt("Tier", panel.getOutputTier());
        } else if (be instanceof QuantumGeneratorBlockEntity qg) {
            tag.putInt(KEY_KIND, KIND_QUANTUM_GEN);
            tag.putBoolean("Active", qg.isActive());
            tag.putInt("Prod", qg.getProduction());
            tag.putInt("Tier", qg.getTier());
            tag.putInt("Packets", qg.getPacketCount());
            tag.putDouble("PerPacket", qg.getOfferedPerPacket());
        } else if (be instanceof MolecularTransformerBlockEntity mt) {
            tag.putInt(KEY_KIND, KIND_MOLECULAR_TRANSFORMER);
            tag.putBoolean("Working", mt.isWorking());
            tag.putBoolean("Blocked", mt.isOutputBlocked());
            tag.putInt("Prog", mt.getProgressPercent());
            tag.putLong("EuUsed", Math.round(mt.getEnergyUsed()));
            tag.putLong("EuTotal", Math.round(mt.getTotalEu()));
            tag.putLong("EuIn", Math.round(mt.getLastTickPower()));
        } else if (be instanceof AdvancedKineticBlockEntity kin) {
            tag.putInt(KEY_KIND, KIND_KINETIC);
            tag.putInt("KinTier", kin.getKineticTier().ordinal());
            tag.putInt("Ku", kin.getCurrentKu());
            tag.putInt("Status", kin.getStatus().ordinal());
            tag.putBoolean("OverWind", kin.isOverWind());
            ItemStack rotor = kin.getRotorStack();
            int max = kin.getRotorMaxDurability();
            if (!rotor.isEmpty() && max > 0) {
                tag.putBoolean("HasRotor", true);
                tag.putString("RotorKey", rotor.getItem().getDescriptionId());
                tag.putDouble("RotorPct", kin.getRotorRemainingPercent());
                tag.putDouble("RotorHrs", kin.getRotorRemainingHours());
            } else {
                tag.putBoolean("HasRotor", false);
            }
        } else if (be instanceof GiantUnitBlockEntity unit) {
            tag.putInt(KEY_KIND, KIND_GIANT);
            boolean formed = unit.isFormed();
            GiantUnitBlockEntity core = formed ? unit.coreEntity() : null;
            tag.putBoolean("Formed", core != null);
            if (core != null) {
                tag.putLong("Eu", Math.round(core.storedEu()));
                tag.putLong("EuMax", Math.round(core.energyCapacity()));
                tag.putInt("GiantOps", core.activeTotalBatch());
                tag.putLong("GiantDraw", Math.round(core.lastTickEuConsumed()));
                if (core.usesWater()) {
                    tag.putBoolean("UsesWater", true);
                    tag.putInt("Water", core.waterMb());
                    tag.putInt("WaterCap", core.waterCapacityMb());
                }
                Optional<GiantCornerPort> portOpt = unit.cornerPortFor(hitSide);
                if (portOpt.isPresent()) {
                    GiantCornerPort port = portOpt.get();
                    PortAccessMode mode = core.portPermissions().get(port);
                    tag.putInt("CornerPort", port.ordinal());
                    tag.putInt("CornerMode", mode.code());
                }
            }
        } else if (be instanceof WaterGasMachineBlockEntity wg) {
            tag.putInt(KEY_KIND, KIND_WATER_GAS);
            tag.putLong("Eu", Math.round(wg.storedEu()));
            tag.putLong("EuMax", Math.round(wg.energyCapacity()));
            tag.putBoolean("Solar", wg.isSolarActive());
            tag.putInt("SolarDeci", (int) Math.round(wg.effectiveSolarEuPerTick() * 10.0));
            tag.putInt("F1Amt", wg.waterMb());
            tag.putInt("F1Cap", WaterGasEngine.WATER_TANK_CAPACITY_MB);
            tag.putInt("F2Amt", wg.waterGasMb());
            tag.putInt("F2Cap", WaterGasEngine.GAS_TANK_CAPACITY_MB);
            tag.putInt("Prog", progressPercent(wg.progress(), wg.operationTicks()));
        } else if (be instanceof GasFurnaceBlockEntity gf) {
            tag.putInt(KEY_KIND, KIND_GAS_FURNACE);
            tag.putString("FuelKey", fuelLangKey(gf.currentFuel()));
            tag.putInt("F1Amt", gf.gasMb());
            tag.putInt("F1Cap", GasFurnaceEngine.GAS_TANK_CAPACITY_MB);
            tag.putInt("Prog", progressPercent(gf.progress(), gf.operationTicks()));
        } else if (be instanceof GasGeneratorBlockEntity gg) {
            tag.putInt(KEY_KIND, KIND_GAS_GENERATOR);
            tag.putLong("Eu", Math.round(gg.storedEu()));
            tag.putLong("EuMax", Math.round(GasGeneratorEngine.EU_CAPACITY));
            tag.putLong("Gen", Math.round(gg.generatingEuPerTick()));
            tag.putString("FuelKey", fuelLangKey(gg.currentFuel()));
            tag.putInt("F1Amt", gg.gasMb());
            tag.putInt("F1Cap", GasGeneratorEngine.GAS_TANK_CAPACITY_MB);
        } else if (be instanceof CultureIncubatorBlockEntity ci) {
            tag.putInt(KEY_KIND, KIND_CULTURE_INCUBATOR);
            tag.putLong("Eu", Math.round(ci.storedEu()));
            tag.putLong("EuMax", Math.round(ci.energyCapacity()));
            tag.putInt("F1Amt", ci.waterMb());
            tag.putInt("F1Cap", CultureIncubatorEngine.WATER_TANK_CAPACITY_MB);
            tag.putInt("F2Amt", ci.cultureMb());
            tag.putInt("F2Cap", CultureIncubatorEngine.CULTURE_TANK_CAPACITY_MB);
            tag.putInt("Prog", progressPercent(ci.progress(), ci.operationTicks()));
        } else if (be instanceof OrganicFermenterBlockEntity of) {
            tag.putInt(KEY_KIND, KIND_ORGANIC_FERMENTER);
            tag.putLong("Eu", Math.round(of.storedEu()));
            tag.putLong("EuMax", Math.round(of.energyCapacity()));
            tag.putInt("F1Amt", of.cultureMb());
            tag.putInt("F1Cap", OrganicFermenterEngine.CULTURE_TANK_CAPACITY_MB);
            tag.putInt("F2Amt", of.biogasMb());
            tag.putInt("F2Cap", OrganicFermenterEngine.BIOGAS_TANK_CAPACITY_MB);
            tag.putInt("Prog", progressPercent(of.progress(), of.operationTicks()));
        }
    }

    /** Formats Jade client tooltip lines from the server-synced {@link TagView}. */
    public static void appendClientLines(TagView tag, Consumer<Component> out) {
        int kind = tag.getInt(KEY_KIND, 0);
        if (kind <= 0) {
            return;
        }
        switch (kind) {
            case KIND_SOLAR ->
                    emitSolar(
                            out,
                            true,
                            tag.getLong("Eu", 0L),
                            tag.getLong("EuMax", 0L),
                            skyByOrdinal(tag.getInt("Sky", 0)),
                            tag.getInt("Gen", 0),
                            tag.getLong("MaxOut", 0L),
                            tag.getInt("Tier", 1));
            case KIND_QUANTUM_GEN ->
                    emitQuantumGenerator(
                            out,
                            true,
                            tag.getBoolean("Active", false),
                            tag.getInt("Prod", 0),
                            tag.getInt("Tier", 1),
                            tag.getInt("Packets", 1),
                            tag.getDouble("PerPacket", 0.0));
            case KIND_MOLECULAR_TRANSFORMER ->
                    emitMolecularTransformer(
                            out,
                            true,
                            tag.getBoolean("Working", false),
                            tag.getBoolean("Blocked", false),
                            tag.getInt("Prog", 0),
                            tag.getLong("EuUsed", 0L),
                            tag.getLong("EuTotal", 0L),
                            tag.getLong("EuIn", 0L));
            case KIND_KINETIC ->
                    emitKinetic(
                            out,
                            true,
                            kineticTierByOrdinal(tag.getInt("KinTier", 0)),
                            tag.getInt("Ku", 0),
                            kineticStatusByOrdinal(tag.getInt("Status", 0)),
                            tag.getBoolean("OverWind", false),
                            tag.getBoolean("HasRotor", false),
                            tag.getString("RotorKey", ""),
                            tag.getDouble("RotorPct", 0.0),
                            tag.getDouble("RotorHrs", 0.0));
            case KIND_GIANT -> {
                GiantCornerPort port =
                        tag.contains("CornerPort")
                                ? cornerPortByOrdinal(tag.getInt("CornerPort", -1))
                                : null;
                PortAccessMode mode =
                        port != null ? PortAccessMode.byCode(tag.getInt("CornerMode", 0)) : null;
                emitGiant(
                        out,
                        true,
                        tag.getBoolean("Formed", false),
                        tag.getLong("Eu", 0L),
                        tag.getLong("EuMax", 0L),
                        tag.getInt("GiantOps", 0),
                        tag.getLong("GiantDraw", 0L),
                        tag.getBoolean("UsesWater", false),
                        tag.getInt("Water", 0),
                        tag.getInt("WaterCap", 0),
                        port,
                        mode);
            }
            case KIND_WATER_GAS ->
                    emitWaterGas(
                            out,
                            true,
                            tag.getLong("Eu", 0L),
                            tag.getLong("EuMax", 0L),
                            tag.getBoolean("Solar", false),
                            tag.getInt("SolarDeci", WaterGasEngine.SOLAR_EU_PER_TICK * 10),
                            tag.getInt("F1Amt", 0),
                            tag.getInt("F1Cap", WaterGasEngine.WATER_TANK_CAPACITY_MB),
                            tag.getInt("F2Amt", 0),
                            tag.getInt("F2Cap", WaterGasEngine.GAS_TANK_CAPACITY_MB),
                            tag.getInt("Prog", 0));
            case KIND_GAS_FURNACE ->
                    emitGasFurnace(
                            out,
                            true,
                            tag.getString("FuelKey", "gui.ecc2.gas.empty"),
                            tag.getInt("F1Amt", 0),
                            tag.getInt("F1Cap", GasFurnaceEngine.GAS_TANK_CAPACITY_MB),
                            tag.getInt("Prog", 0));
            case KIND_GAS_GENERATOR ->
                    emitGasGenerator(
                            out,
                            true,
                            tag.getLong("Eu", 0L),
                            tag.getLong("EuMax", Math.round(GasGeneratorEngine.EU_CAPACITY)),
                            tag.getLong("Gen", 0L),
                            tag.getString("FuelKey", "gui.ecc2.gas.empty"),
                            tag.getInt("F1Amt", 0),
                            tag.getInt("F1Cap", GasGeneratorEngine.GAS_TANK_CAPACITY_MB));
            case KIND_CULTURE_INCUBATOR ->
                    emitCultureIncubator(
                            out,
                            true,
                            tag.getLong("Eu", 0L),
                            tag.getLong("EuMax", 0L),
                            tag.getInt("F1Amt", 0),
                            tag.getInt("F1Cap", CultureIncubatorEngine.WATER_TANK_CAPACITY_MB),
                            tag.getInt("F2Amt", 0),
                            tag.getInt("F2Cap", CultureIncubatorEngine.CULTURE_TANK_CAPACITY_MB),
                            tag.getInt("Prog", 0));
            case KIND_ORGANIC_FERMENTER ->
                    emitOrganicFermenter(
                            out,
                            true,
                            tag.getLong("Eu", 0L),
                            tag.getLong("EuMax", 0L),
                            tag.getInt("F1Amt", 0),
                            tag.getInt("F1Cap", OrganicFermenterEngine.CULTURE_TANK_CAPACITY_MB),
                            tag.getInt("F2Amt", 0),
                            tag.getInt("F2Cap", OrganicFermenterEngine.BIOGAS_TANK_CAPACITY_MB),
                            tag.getInt("Prog", 0));
            default -> {}
        }
    }

    /**
     * Emits authoritative server-side telemetry lines directly from a {@link BlockEntity} for The
     * One Probe ({@code top.ecc2.*} + {@code gui.ecc2.*} lang keys).
     */
    public static void appendServerLines(
            BlockEntity be, Direction hitSide, Consumer<Component> out) {
        if (be instanceof SolarPanelBlockEntity panel) {
            emitSolar(
                    out,
                    false,
                    Math.round(panel.getStoredEu()),
                    panel.getMaxStorage(),
                    panel.getSkyCondition(),
                    panel.getCurrentGenerating(),
                    Math.round(panel.getMaxOutput()),
                    panel.getOutputTier());
        } else if (be instanceof QuantumGeneratorBlockEntity qg) {
            emitQuantumGenerator(
                    out,
                    false,
                    qg.isActive(),
                    qg.getProduction(),
                    qg.getTier(),
                    qg.getPacketCount(),
                    qg.getOfferedPerPacket());
        } else if (be instanceof MolecularTransformerBlockEntity mt) {
            emitMolecularTransformer(
                    out,
                    false,
                    mt.isWorking(),
                    mt.isOutputBlocked(),
                    mt.getProgressPercent(),
                    Math.round(mt.getEnergyUsed()),
                    Math.round(mt.getTotalEu()),
                    Math.round(mt.getLastTickPower()));
        } else if (be instanceof AdvancedKineticBlockEntity kin) {
            ItemStack rotor = kin.getRotorStack();
            boolean hasRotor = !rotor.isEmpty() && kin.getRotorMaxDurability() > 0;
            emitKinetic(
                    out,
                    false,
                    kin.getKineticTier(),
                    kin.getCurrentKu(),
                    kin.getStatus(),
                    kin.isOverWind(),
                    hasRotor,
                    hasRotor ? rotor.getItem().getDescriptionId() : "",
                    kin.getRotorRemainingPercent(),
                    kin.getRotorRemainingHours());
        } else if (be instanceof GiantUnitBlockEntity unit) {
            GiantUnitBlockEntity core = unit.isFormed() ? unit.coreEntity() : null;
            GiantCornerPort port = null;
            PortAccessMode mode = null;
            if (core != null) {
                Optional<GiantCornerPort> portOpt = unit.cornerPortFor(hitSide);
                if (portOpt.isPresent()) {
                    port = portOpt.get();
                    mode = core.portPermissions().get(port);
                }
            }
            emitGiant(
                    out,
                    false,
                    core != null,
                    core != null ? Math.round(core.storedEu()) : 0L,
                    core != null ? Math.round(core.energyCapacity()) : 0L,
                    core != null ? core.activeTotalBatch() : 0,
                    core != null ? Math.round(core.lastTickEuConsumed()) : 0L,
                    core != null && core.usesWater(),
                    core != null ? core.waterMb() : 0,
                    core != null ? core.waterCapacityMb() : 0,
                    port,
                    mode);
        } else if (be instanceof WaterGasMachineBlockEntity wg) {
            emitWaterGas(
                    out,
                    false,
                    Math.round(wg.storedEu()),
                    Math.round(wg.energyCapacity()),
                    wg.isSolarActive(),
                    (int) Math.round(wg.effectiveSolarEuPerTick() * 10.0),
                    wg.waterMb(),
                    WaterGasEngine.WATER_TANK_CAPACITY_MB,
                    wg.waterGasMb(),
                    WaterGasEngine.GAS_TANK_CAPACITY_MB,
                    progressPercent(wg.progress(), wg.operationTicks()));
        } else if (be instanceof GasFurnaceBlockEntity gf) {
            emitGasFurnace(
                    out,
                    false,
                    fuelLangKey(gf.currentFuel()),
                    gf.gasMb(),
                    GasFurnaceEngine.GAS_TANK_CAPACITY_MB,
                    progressPercent(gf.progress(), gf.operationTicks()));
        } else if (be instanceof GasGeneratorBlockEntity gg) {
            emitGasGenerator(
                    out,
                    false,
                    Math.round(gg.storedEu()),
                    Math.round(GasGeneratorEngine.EU_CAPACITY),
                    Math.round(gg.generatingEuPerTick()),
                    fuelLangKey(gg.currentFuel()),
                    gg.gasMb(),
                    GasGeneratorEngine.GAS_TANK_CAPACITY_MB);
        } else if (be instanceof CultureIncubatorBlockEntity ci) {
            emitCultureIncubator(
                    out,
                    false,
                    Math.round(ci.storedEu()),
                    Math.round(ci.energyCapacity()),
                    ci.waterMb(),
                    CultureIncubatorEngine.WATER_TANK_CAPACITY_MB,
                    ci.cultureMb(),
                    CultureIncubatorEngine.CULTURE_TANK_CAPACITY_MB,
                    progressPercent(ci.progress(), ci.operationTicks()));
        } else if (be instanceof OrganicFermenterBlockEntity of) {
            emitOrganicFermenter(
                    out,
                    false,
                    Math.round(of.storedEu()),
                    Math.round(of.energyCapacity()),
                    of.cultureMb(),
                    OrganicFermenterEngine.CULTURE_TANK_CAPACITY_MB,
                    of.biogasMb(),
                    OrganicFermenterEngine.BIOGAS_TANK_CAPACITY_MB,
                    progressPercent(of.progress(), of.operationTicks()));
        }
    }

    // ---- Shared Line Emitters ----

    private static void emitSolar(
            Consumer<Component> out,
            boolean jade,
            long eu,
            long euMax,
            SkyCondition sky,
            int generating,
            long maxOut,
            int tier) {
        out.accept(
                Component.translatable(
                        jade ? "jade.ecc2.eu" : "top.ecc2.eu",
                        Ecc2DataSync.formatInt(eu),
                        Ecc2DataSync.formatInt(euMax)));
        out.accept(skyComponent(sky));
        out.accept(
                Component.translatable(
                        jade ? "jade.ecc2.generating" : "top.ecc2.generating",
                        Ecc2DataSync.formatInt(generating)));
        out.accept(
                Component.translatable(
                                "gui.ecc2.solar.max_output", Ecc2DataSync.formatInt(maxOut), tier)
                        .withStyle(ChatFormatting.GRAY));
    }

    private static void emitQuantumGenerator(
            Consumer<Component> out,
            boolean jade,
            boolean active,
            int production,
            int tier,
            int packets,
            double perPacket) {
        out.accept(
                Component.translatable(
                                active
                                        ? "gui.ecc2.quantum_generator.active"
                                        : "gui.ecc2.quantum_generator.stopped")
                        .withStyle(active ? ChatFormatting.GREEN : ChatFormatting.RED));
        out.accept(
                Component.translatable(
                        jade ? "jade.ecc2.generating" : "top.ecc2.generating",
                        Ecc2DataSync.formatInt(active ? production : 0)));
        Component tierText =
                tier >= 6
                        ? Component.translatable("gui.ecc2.quantum_generator.tier_max")
                        : Component.literal(String.valueOf(tier));
        long packetCap = Math.round(SolarTier.packetSize(tier));
        out.accept(
                Component.translatable(
                                "gui.ecc2.quantum_generator.tier",
                                tierText,
                                Ecc2DataSync.formatInt(packetCap))
                        .withStyle(ChatFormatting.AQUA));
        String perPacketStr =
                perPacket == Math.rint(perPacket)
                        ? Ecc2DataSync.formatInt(Math.round(perPacket))
                        : String.format(Locale.ROOT, "%.1f", perPacket);
        out.accept(
                Component.translatable(
                                "gui.ecc2.quantum_generator.packets", packets, perPacketStr)
                        .withStyle(ChatFormatting.GRAY));
    }

    private static void emitMolecularTransformer(
            Consumer<Component> out,
            boolean jade,
            boolean working,
            boolean blocked,
            int progressPct,
            long euUsed,
            long euTotal,
            long euIn) {
        if (working || blocked || euTotal > 0L) {
            out.accept(
                    Component.translatable(
                            jade ? "jade.ecc2.progress" : "top.ecc2.progress", progressPct));
            if (euTotal > 0L) {
                out.accept(
                        Component.translatable(
                                jade ? "jade.ecc2.eu" : "top.ecc2.eu",
                                Ecc2DataSync.formatInt(euUsed),
                                Ecc2DataSync.formatInt(euTotal)));
            }
            out.accept(
                    Component.translatable(
                                    "gui.ecc2.mt.input_eu", Ecc2DataSync.formatInt(euIn))
                            .withStyle(ChatFormatting.GREEN));
            if (blocked) {
                out.accept(
                        Component.translatable("gui.ecc2.mt.output_full")
                                .withStyle(ChatFormatting.GOLD));
            }
        } else {
            out.accept(Component.translatable("gui.ecc2.mt.idle").withStyle(ChatFormatting.GRAY));
        }
    }

    private static void emitKinetic(
            Consumer<Component> out,
            boolean jade,
            KineticMachineTier tier,
            int ku,
            AdvancedKineticPhysics.Status status,
            boolean overWind,
            boolean hasRotor,
            String rotorDescId,
            double rotorPct,
            double rotorHours) {
        out.accept(kineticTierComponent(tier).withStyle(ChatFormatting.AQUA));
        out.accept(
                Component.translatable(
                        jade ? "jade.ecc2.kinetic_ku" : "top.ecc2.kinetic_ku",
                        Ecc2DataSync.formatInt(ku)));
        if (hasRotor) {
            if (rotorDescId != null && !rotorDescId.isEmpty()) {
                out.accept(
                        Component.translatable(rotorDescId)
                                .append(Component.literal(" · "))
                                .append(
                                        Component.translatable(
                                                jade
                                                        ? "jade.ecc2.rotor_life"
                                                        : "top.ecc2.rotor_life",
                                                String.format(Locale.ROOT, "%.1f", rotorPct),
                                                String.format(Locale.ROOT, "%.1f", rotorHours))));
            } else {
                out.accept(
                        Component.translatable(
                                jade ? "jade.ecc2.rotor_life" : "top.ecc2.rotor_life",
                                String.format(Locale.ROOT, "%.1f", rotorPct),
                                String.format(Locale.ROOT, "%.1f", rotorHours)));
            }
        } else {
            out.accept(
                    Component.translatable("gui.ecc2.kinetic.no_rotor")
                            .withStyle(ChatFormatting.GRAY));
        }
        if (overWind || status == AdvancedKineticPhysics.Status.OVERWIND) {
            out.accept(
                    Component.translatable("gui.ecc2.kinetic.overwind")
                            .withStyle(ChatFormatting.RED));
        } else if (status == AdvancedKineticPhysics.Status.OBSTRUCTED) {
            out.accept(
                    Component.translatable("gui.ecc2.kinetic.obstructed")
                            .withStyle(ChatFormatting.GOLD));
        } else if (status == AdvancedKineticPhysics.Status.NO_WATER) {
            out.accept(
                    Component.translatable("gui.ecc2.kinetic.no_water")
                            .withStyle(ChatFormatting.GOLD));
        }
    }

    private static void emitGiant(
            Consumer<Component> out,
            boolean jade,
            boolean formed,
            long eu,
            long euMax,
            int activeOps,
            long euDraw,
            boolean usesWater,
            int waterMb,
            int waterCap,
            GiantCornerPort cornerPort,
            PortAccessMode cornerMode) {
        if (!formed) {
            out.accept(
                    Component.translatable("gui.ecc2.giant.unformed")
                            .withStyle(ChatFormatting.GOLD));
            return;
        }
        out.accept(Component.translatable("gui.ecc2.giant.formed").withStyle(ChatFormatting.GREEN));
        out.accept(
                Component.translatable(
                        jade ? "jade.ecc2.eu" : "top.ecc2.eu",
                        Ecc2DataSync.formatInt(eu),
                        Ecc2DataSync.formatInt(euMax)));
        if (jade) {
            out.accept(
                    Component.translatable(
                            "jade.ecc2.giant_lanes", activeOps, Ecc2DataSync.formatInt(euDraw)));
        } else {
            out.accept(Component.translatable("top.ecc2.giant_lanes", activeOps));
            out.accept(
                    Component.translatable(
                                    "gui.ecc2.giant.eu_draw", Ecc2DataSync.formatInt(euDraw))
                            .withStyle(ChatFormatting.GRAY));
        }
        if (usesWater) {
            out.accept(
                    Component.translatable(
                            jade ? "jade.ecc2.fluid" : "top.ecc2.fluid",
                            Component.translatable("gui.ecc2.gas.water"),
                            Ecc2DataSync.formatInt(waterMb),
                            Ecc2DataSync.formatInt(waterCap)));
        }
        if (cornerPort != null && cornerMode != null) {
            out.accept(
                    Component.translatable(
                                    jade ? "jade.ecc2.giant_port" : "top.ecc2.giant_port",
                                    portName(cornerPort),
                                    portModeName(cornerMode))
                            .withStyle(portModeColor(cornerMode)));
        }
    }

    private static void emitWaterGas(
            Consumer<Component> out,
            boolean jade,
            long eu,
            long euMax,
            boolean solarActive,
            int solarDeciEuPerTick,
            int waterMb,
            int waterCap,
            int gasMb,
            int gasCap,
            int progressPct) {
        out.accept(
                Component.translatable(
                        jade ? "jade.ecc2.eu" : "top.ecc2.eu",
                        Ecc2DataSync.formatInt(eu),
                        Ecc2DataSync.formatInt(euMax)));
        out.accept(
                (solarActive
                                ? Component.translatable(
                                        "gui.ecc2.gas.solar_active",
                                        WaterGasEngine.formatSolarRateDeci(solarDeciEuPerTick))
                                : Component.translatable("gui.ecc2.gas.solar_inactive"))
                        .withStyle(solarActive ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
        out.accept(
                Component.translatable(
                        jade ? "jade.ecc2.fluid" : "top.ecc2.fluid",
                        Component.translatable("gui.ecc2.gas.water"),
                        Ecc2DataSync.formatInt(waterMb),
                        Ecc2DataSync.formatInt(waterCap)));
        out.accept(
                Component.translatable(
                        jade ? "jade.ecc2.fluid" : "top.ecc2.fluid",
                        Component.translatable("gui.ecc2.gas.water_gas"),
                        Ecc2DataSync.formatInt(gasMb),
                        Ecc2DataSync.formatInt(gasCap)));
        if (progressPct > 0) {
            out.accept(
                    Component.translatable(
                            jade ? "jade.ecc2.progress" : "top.ecc2.progress", progressPct));
        }
    }

    private static void emitGasFurnace(
            Consumer<Component> out,
            boolean jade,
            String fuelKey,
            int gasMb,
            int gasCap,
            int progressPct) {
        out.accept(
                Component.translatable(
                        jade ? "jade.ecc2.fluid" : "top.ecc2.fluid",
                        Component.translatable(fuelKey),
                        Ecc2DataSync.formatInt(gasMb),
                        Ecc2DataSync.formatInt(gasCap)));
        if (progressPct > 0) {
            out.accept(
                    Component.translatable(
                            jade ? "jade.ecc2.progress" : "top.ecc2.progress", progressPct));
        }
    }

    private static void emitGasGenerator(
            Consumer<Component> out,
            boolean jade,
            long eu,
            long euMax,
            long genEuTick,
            String fuelKey,
            int gasMb,
            int gasCap) {
        out.accept(
                Component.translatable(
                        jade ? "jade.ecc2.eu" : "top.ecc2.eu",
                        Ecc2DataSync.formatInt(eu),
                        Ecc2DataSync.formatInt(euMax)));
        out.accept(
                Component.translatable(
                        jade ? "jade.ecc2.generating" : "top.ecc2.generating",
                        Ecc2DataSync.formatInt(genEuTick)));
        out.accept(
                Component.translatable(
                        jade ? "jade.ecc2.fluid" : "top.ecc2.fluid",
                        Component.translatable(fuelKey),
                        Ecc2DataSync.formatInt(gasMb),
                        Ecc2DataSync.formatInt(gasCap)));
    }

    private static void emitCultureIncubator(
            Consumer<Component> out,
            boolean jade,
            long eu,
            long euMax,
            int waterMb,
            int waterCap,
            int cultureMb,
            int cultureCap,
            int progressPct) {
        out.accept(
                Component.translatable(
                        jade ? "jade.ecc2.eu" : "top.ecc2.eu",
                        Ecc2DataSync.formatInt(eu),
                        Ecc2DataSync.formatInt(euMax)));
        out.accept(
                Component.translatable(
                        jade ? "jade.ecc2.fluid" : "top.ecc2.fluid",
                        Component.translatable("gui.ecc2.gas.water"),
                        Ecc2DataSync.formatInt(waterMb),
                        Ecc2DataSync.formatInt(waterCap)));
        out.accept(
                Component.translatable(
                        jade ? "jade.ecc2.fluid" : "top.ecc2.fluid",
                        Component.translatable("gui.ecc2.gas.culture_medium"),
                        Ecc2DataSync.formatInt(cultureMb),
                        Ecc2DataSync.formatInt(cultureCap)));
        if (progressPct > 0) {
            out.accept(
                    Component.translatable(
                            jade ? "jade.ecc2.progress" : "top.ecc2.progress", progressPct));
        }
    }

    private static void emitOrganicFermenter(
            Consumer<Component> out,
            boolean jade,
            long eu,
            long euMax,
            int cultureMb,
            int cultureCap,
            int biogasMb,
            int biogasCap,
            int progressPct) {
        out.accept(
                Component.translatable(
                        jade ? "jade.ecc2.eu" : "top.ecc2.eu",
                        Ecc2DataSync.formatInt(eu),
                        Ecc2DataSync.formatInt(euMax)));
        out.accept(
                Component.translatable(
                        jade ? "jade.ecc2.fluid" : "top.ecc2.fluid",
                        Component.translatable("gui.ecc2.gas.culture_medium"),
                        Ecc2DataSync.formatInt(cultureMb),
                        Ecc2DataSync.formatInt(cultureCap)));
        out.accept(
                Component.translatable(
                        jade ? "jade.ecc2.fluid" : "top.ecc2.fluid",
                        Component.translatable("gui.ecc2.gas.biogas"),
                        Ecc2DataSync.formatInt(biogasMb),
                        Ecc2DataSync.formatInt(biogasCap)));
        if (progressPct > 0) {
            out.accept(
                    Component.translatable(
                            jade ? "jade.ecc2.progress" : "top.ecc2.progress", progressPct));
        }
    }

    // ---- Label Helpers ----

    public static Component skyComponent(SkyCondition sky) {
        return switch (sky) {
            case DAY -> Component.translatable("gui.ecc2.solar.day").withStyle(ChatFormatting.YELLOW);
            case NIGHT ->
                    Component.translatable("gui.ecc2.solar.night").withStyle(ChatFormatting.BLUE);
            case RAIN ->
                    Component.translatable("gui.ecc2.solar.rain").withStyle(ChatFormatting.AQUA);
            case NONE ->
                    Component.translatable("gui.ecc2.solar.blocked").withStyle(ChatFormatting.RED);
        };
    }

    public static net.minecraft.network.chat.MutableComponent kineticTierComponent(
            KineticMachineTier tier) {
        return switch (tier) {
            case DURABLE -> Component.translatable("gui.ecc2.kinetic.tier.durable");
            case OVERCLOCKED -> Component.translatable("gui.ecc2.kinetic.tier.overclocked");
            case PRECISION -> Component.translatable("gui.ecc2.kinetic.tier.precision");
            case STANDARD -> Component.literal("STANDARD");
        };
    }

    public static Component portName(GiantCornerPort port) {
        return Component.translatable("gui.ecc2.port." + port.name().toLowerCase(Locale.ROOT));
    }

    public static Component portModeName(PortAccessMode mode) {
        return Component.translatable("gui.ecc2.port.mode." + mode.name().toLowerCase(Locale.ROOT));
    }

    public static String fuelLangKey(GasFuelSpec fuel) {
        if (fuel == null) {
            return "gui.ecc2.gas.empty";
        }
        return switch (fuel) {
            case WATER_GAS -> "gui.ecc2.gas.water_gas";
            case NATURAL_GAS -> "gui.ecc2.gas.natural_gas";
            case BIOGAS -> "gui.ecc2.gas.biogas";
        };
    }

    private static ChatFormatting portModeColor(PortAccessMode mode) {
        return switch (mode) {
            case FULL -> ChatFormatting.GREEN;
            case INPUT -> ChatFormatting.AQUA;
            case OUTPUT -> ChatFormatting.GOLD;
            case BLOCKED -> ChatFormatting.RED;
        };
    }

    private static int progressPercent(int progress, int opTicks) {
        if (progress <= 0 || opTicks <= 0) {
            return 0;
        }
        return Math.min(100, (progress * 100) / opTicks);
    }

    private static SkyCondition skyByOrdinal(int ordinal) {
        var values = SkyCondition.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : SkyCondition.NONE;
    }

    private static KineticMachineTier kineticTierByOrdinal(int ordinal) {
        var values = KineticMachineTier.values();
        return ordinal >= 0 && ordinal < values.length
                ? values[ordinal]
                : KineticMachineTier.DURABLE;
    }

    private static AdvancedKineticPhysics.Status kineticStatusByOrdinal(int ordinal) {
        var values = AdvancedKineticPhysics.Status.values();
        return ordinal >= 0 && ordinal < values.length
                ? values[ordinal]
                : AdvancedKineticPhysics.Status.NO_ROTOR;
    }

    private static GiantCornerPort cornerPortByOrdinal(int ordinal) {
        var values = GiantCornerPort.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }
}
