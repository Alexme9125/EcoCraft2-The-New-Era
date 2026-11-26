package com.ecocraft2.compat;

import com.ecocraft2.bridge.Ecc2BridgePlatform;
import com.ecocraft2.common.gas.CultureIncubatorEngine;
import com.ecocraft2.common.gas.GasFurnaceEngine;
import com.ecocraft2.common.gas.GasGeneratorEngine;
import com.ecocraft2.common.gas.OrganicFermenterEngine;
import com.ecocraft2.common.gas.WaterGasEngine;
import com.ecocraft2.common.giant.PortAccessMode;
import com.ecocraft2.gas.CultureIncubatorBlockEntity;
import com.ecocraft2.gas.GasFurnaceBlockEntity;
import com.ecocraft2.gas.GasGeneratorBlockEntity;
import com.ecocraft2.gas.OrganicFermenterBlockEntity;
import com.ecocraft2.gas.WaterGasMachineBlockEntity;
import com.ecocraft2.giant.GiantMachineBlockEntity;
import com.ecocraft2.kinetic.AdvancedKineticBlockEntity;
import com.ecocraft2.solar.MolecularTransformerBlockEntity;
import com.ecocraft2.solar.QuantumGeneratorBlockEntity;
import com.ecocraft2.solar.SolarPanelBlockEntity;
import java.util.Locale;
import java.util.function.Function;
import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.ITheOneProbe;
import mcjty.theoneprobe.api.ProbeMode;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.InterModComms;

/** Optional The One Probe (TOP) integration for EcoCraft 2 (loaded only when TOP is installed). */
public final class Ecc2TopCompat {

    private Ecc2TopCompat() {}

    /** Enqueues the TOP IMC registration callback. */
    public static void enqueueImc() {
        InterModComms.sendTo("theoneprobe", "getTheOneProbe", Registrar::new);
    }

    public static final class Registrar implements Function<ITheOneProbe, Void> {
        @Override
        public Void apply(ITheOneProbe probe) {
            probe.registerProvider(new Provider());
            return null;
        }
    }

    private static final class Provider implements IProbeInfoProvider {
        private static final ResourceLocation ID = Ecc2BridgePlatform.rl("top_machine_info");

        @Override
        public ResourceLocation getID() {
            return ID;
        }

        @Override
        public void addProbeInfo(
                ProbeMode mode,
                IProbeInfo info,
                Player player,
                Level level,
                BlockState state,
                IProbeHitData hitData) {
            BlockEntity be = level.getBlockEntity(hitData.getPos());
            if (be instanceof SolarPanelBlockEntity panel) {
                var e = panel.engine();
                info.mcText(
                        Component.translatable(
                                "top.ecc2.eu", (long) Math.floor(e.storage()), e.maxStorage()));
                info.mcText(Component.translatable("top.ecc2.generating", e.currentGenerating()));
            } else if (be instanceof QuantumGeneratorBlockEntity qg) {
                var e = qg.engine();
                info.mcText(
                        Component.translatable(
                                "top.ecc2.generating", e.isActive() ? e.production() : 0));
            } else if (be instanceof MolecularTransformerBlockEntity mt) {
                var e = mt.engine();
                if (e.isWorking()) {
                    info.mcText(Component.translatable("top.ecc2.progress", e.progressPercent()));
                }
            } else if (be instanceof AdvancedKineticBlockEntity kin) {
                info.mcText(Component.translatable("top.ecc2.kinetic_ku", kin.currentKu()));
                ItemStack rotor = kin.rotorSlot().getStackInSlot(0);
                int max = com.ecocraft2.bridge.Ecc2RotorHooks.maxDurability(rotor);
                if (max > 0) {
                    int rem = Math.max(0, max - rotor.getDamageValue());
                    double pct = (rem * 100.0) / max;
                    double hrs = rem / 3600.0;
                    info.mcText(
                            Component.translatable(
                                    "top.ecc2.rotor_life",
                                    String.format(Locale.ROOT, "%.1f", pct),
                                    String.format(Locale.ROOT, "%.1f", hrs)));
                }
            } else if (be instanceof GiantMachineBlockEntity unit) {
                GiantMachineBlockEntity core = unit.resolveCore();
                if (core != null) {
                    var e = core.engine();
                    info.mcText(
                            Component.translatable(
                                    "top.ecc2.eu",
                                    (long) Math.floor(e.storedEu()),
                                    (long) Math.floor(e.energyCapacity())));
                    info.mcText(
                            Component.translatable(
                                    "top.ecc2.giant_lanes", e.lastActiveTotalBatch()));
                    if (core.kind().spec().usesWater()) {
                        info.mcText(
                                Component.translatable(
                                        "top.ecc2.fluid",
                                        Component.translatable("gui.ecc2.gas.water"),
                                        e.waterMb(),
                                        core.configuredWaterCapacity()));
                    }
                    unit.cornerPortForSide(hitData.getSideHit())
                            .ifPresent(
                                    port -> {
                                        PortAccessMode pm = e.portPermissions().get(port);
                                        info.mcText(
                                                Component.translatable(
                                                        "top.ecc2.giant_port",
                                                        Ecc2BridgePlatform.portName(port),
                                                        Ecc2BridgePlatform.modeName(pm)));
                                    });
                }
            } else if (be instanceof WaterGasMachineBlockEntity wg) {
                var e = wg.engine();
                info.mcText(
                        Component.translatable(
                                "top.ecc2.eu",
                                (long) Math.floor(e.storedEu()),
                                (long) Math.floor(e.energyCapacity())));
                boolean solarActive = e.isSolarActive();
                info.mcText(
                        (solarActive
                                        ? Component.translatable(
                                                "gui.ecc2.gas.solar_active",
                                                WaterGasEngine.formatSolarRate(
                                                        e.effectiveSolarEuPerTick()))
                                        : Component.translatable("gui.ecc2.gas.solar_inactive"))
                                .withStyle(
                                        solarActive ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
                info.mcText(
                        Component.translatable(
                                "top.ecc2.fluid",
                                Component.translatable("gui.ecc2.gas.water"),
                                e.waterMb(),
                                WaterGasEngine.WATER_TANK_CAPACITY_MB));
                info.mcText(
                        Component.translatable(
                                "top.ecc2.fluid",
                                Component.translatable("gui.ecc2.gas.water_gas"),
                                e.waterGasMb(),
                                WaterGasEngine.GAS_TANK_CAPACITY_MB));
            } else if (be instanceof GasFurnaceBlockEntity gf) {
                var e = gf.engine();
                info.mcText(
                        Component.translatable(
                                "top.ecc2.fluid",
                                Ecc2BridgePlatform.fuelName(e.currentFuel()),
                                e.gasMb(),
                                GasFurnaceEngine.GAS_TANK_CAPACITY_MB));
            } else if (be instanceof GasGeneratorBlockEntity gg) {
                var e = gg.engine();
                info.mcText(
                        Component.translatable(
                                "top.ecc2.eu",
                                (long) Math.floor(e.storedEu()),
                                (long) GasGeneratorEngine.EU_CAPACITY));
                info.mcText(
                        Component.translatable(
                                "top.ecc2.generating", (int) Math.round(e.lastTickGeneratedEu())));
                info.mcText(
                        Component.translatable(
                                "top.ecc2.fluid",
                                Ecc2BridgePlatform.fuelName(e.currentFuel()),
                                e.gasMb(),
                                GasGeneratorEngine.GAS_TANK_CAPACITY_MB));
            } else if (be instanceof CultureIncubatorBlockEntity ci) {
                var e = ci.engine();
                info.mcText(
                        Component.translatable(
                                "top.ecc2.eu",
                                (long) Math.floor(e.storedEu()),
                                (long) Math.floor(e.energyCapacity())));
                info.mcText(
                        Component.translatable(
                                "top.ecc2.fluid",
                                Component.translatable("gui.ecc2.gas.water"),
                                e.waterMb(),
                                CultureIncubatorEngine.WATER_TANK_CAPACITY_MB));
                info.mcText(
                        Component.translatable(
                                "top.ecc2.fluid",
                                Component.translatable("gui.ecc2.gas.culture_medium"),
                                e.cultureMb(),
                                CultureIncubatorEngine.CULTURE_TANK_CAPACITY_MB));
            } else if (be instanceof OrganicFermenterBlockEntity of) {
                var e = of.engine();
                info.mcText(
                        Component.translatable(
                                "top.ecc2.eu",
                                (long) Math.floor(e.storedEu()),
                                (long) Math.floor(e.energyCapacity())));
                info.mcText(
                        Component.translatable(
                                "top.ecc2.fluid",
                                Component.translatable("gui.ecc2.gas.culture_medium"),
                                e.cultureMb(),
                                OrganicFermenterEngine.CULTURE_TANK_CAPACITY_MB));
                info.mcText(
                        Component.translatable(
                                "top.ecc2.fluid",
                                Component.translatable("gui.ecc2.gas.biogas"),
                                e.biogasMb(),
                                OrganicFermenterEngine.BIOGAS_TANK_CAPACITY_MB));
            }
        }
    }
}
