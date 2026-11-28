package com.ecocraft2.compat;

import com.ecocraft2.bridge.Ecc2BridgePlatform;
import com.ecocraft2.bridge.Ecc2RotorHooks;
import com.ecocraft2.common.gas.CultureIncubatorEngine;
import com.ecocraft2.common.gas.GasFuelSpec;
import com.ecocraft2.common.gas.GasFurnaceEngine;
import com.ecocraft2.common.gas.GasGeneratorEngine;
import com.ecocraft2.common.gas.OrganicFermenterEngine;
import com.ecocraft2.common.gas.WaterGasEngine;
import com.ecocraft2.common.giant.GiantPortPermissions;
import com.ecocraft2.common.giant.PortAccessMode;
import com.ecocraft2.gas.CultureIncubatorBlockEntity;
import com.ecocraft2.gas.GasFurnaceBlockEntity;
import com.ecocraft2.gas.GasGeneratorBlockEntity;
import com.ecocraft2.gas.GasMachineBlock;
import com.ecocraft2.gas.OrganicFermenterBlockEntity;
import com.ecocraft2.gas.WaterGasMachineBlockEntity;
import com.ecocraft2.giant.GiantMachineBlock;
import com.ecocraft2.giant.GiantMachineBlockEntity;
import com.ecocraft2.kinetic.AdvancedKineticBlock;
import com.ecocraft2.kinetic.AdvancedKineticBlockEntity;
import com.ecocraft2.solar.MolecularTransformerBlock;
import com.ecocraft2.solar.MolecularTransformerBlockEntity;
import com.ecocraft2.solar.QuantumGeneratorBlock;
import com.ecocraft2.solar.QuantumGeneratorBlockEntity;
import com.ecocraft2.solar.SolarPanelBlock;
import com.ecocraft2.solar.SolarPanelBlockEntity;
import java.util.Locale;
import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IClientRegistrar;
import mcp.mobius.waila.api.ICommonRegistrar;
import mcp.mobius.waila.api.IDataProvider;
import mcp.mobius.waila.api.IDataWriter;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.IServerAccessor;
import mcp.mobius.waila.api.ITooltip;
import mcp.mobius.waila.api.IWailaClientPlugin;
import mcp.mobius.waila.api.IWailaCommonPlugin;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Optional WTHIT ({@code wthit}) integration for EcoCraft 2 on Forge 1.19.2.
 *
 * <p>Registered via {@code wthit_plugins.json} and loaded exclusively when WTHIT is installed.
 */
public final class Ecc2WthitPlugin implements IWailaCommonPlugin, IWailaClientPlugin {

    @Override
    public void register(ICommonRegistrar registrar) {
        registrar.blockData(ServerDataProvider.INSTANCE, SolarPanelBlockEntity.class);
        registrar.blockData(ServerDataProvider.INSTANCE, QuantumGeneratorBlockEntity.class);
        registrar.blockData(ServerDataProvider.INSTANCE, MolecularTransformerBlockEntity.class);
        registrar.blockData(ServerDataProvider.INSTANCE, AdvancedKineticBlockEntity.class);
        registrar.blockData(ServerDataProvider.INSTANCE, GiantMachineBlockEntity.class);
        registrar.blockData(ServerDataProvider.INSTANCE, WaterGasMachineBlockEntity.class);
        registrar.blockData(ServerDataProvider.INSTANCE, GasFurnaceBlockEntity.class);
        registrar.blockData(ServerDataProvider.INSTANCE, GasGeneratorBlockEntity.class);
        registrar.blockData(ServerDataProvider.INSTANCE, CultureIncubatorBlockEntity.class);
        registrar.blockData(ServerDataProvider.INSTANCE, OrganicFermenterBlockEntity.class);
    }

    @Override
    public void register(IClientRegistrar registrar) {
        registrar.body(ClientComponentProvider.INSTANCE, SolarPanelBlock.class);
        registrar.body(ClientComponentProvider.INSTANCE, QuantumGeneratorBlock.class);
        registrar.body(ClientComponentProvider.INSTANCE, MolecularTransformerBlock.class);
        registrar.body(ClientComponentProvider.INSTANCE, AdvancedKineticBlock.class);
        registrar.body(ClientComponentProvider.INSTANCE, GiantMachineBlock.class);
        registrar.body(ClientComponentProvider.INSTANCE, GasMachineBlock.class);
    }

    private enum ServerDataProvider implements IDataProvider<BlockEntity> {
        INSTANCE;

        @Override
        public void appendData(
                IDataWriter data, IServerAccessor<BlockEntity> accessor, IPluginConfig config) {
            BlockEntity be = accessor.getTarget();
            if (be == null) {
                return;
            }
            CompoundTag tag = data.raw();
            if (be instanceof SolarPanelBlockEntity panel) {
                tag.putLong("Eu", (long) Math.floor(panel.engine().storage()));
                tag.putLong("EuMax", panel.engine().maxStorage());
                tag.putInt("Gen", panel.engine().currentGenerating());
            } else if (be instanceof QuantumGeneratorBlockEntity qg) {
                tag.putInt("Gen", qg.engine().isActive() ? qg.engine().production() : 0);
            } else if (be instanceof MolecularTransformerBlockEntity mt) {
                if (mt.engine().isWorking()) {
                    tag.putInt("Prog", mt.engine().progressPercent());
                }
            } else if (be instanceof AdvancedKineticBlockEntity kin) {
                tag.putInt("Ku", kin.currentKu());
                ItemStack rotor = kin.rotorSlot().getStackInSlot(0);
                int max = Ecc2RotorHooks.maxDurability(rotor);
                if (max > 0) {
                    int rem = Math.max(0, max - rotor.getDamageValue());
                    tag.putDouble("RotorPct", (rem * 100.0) / max);
                    tag.putDouble("RotorHrs", rem / 3600.0);
                }
            } else if (be instanceof GiantMachineBlockEntity unit) {
                GiantMachineBlockEntity core = unit.resolveCore();
                if (core != null) {
                    tag.putLong("Eu", (long) Math.floor(core.engine().storedEu()));
                    tag.putLong("EuMax", (long) Math.floor(core.engine().energyCapacity()));
                    tag.putInt("GiantOps", core.engine().lastActiveTotalBatch());
                    tag.putInt("GiantDraw", (int) Math.round(core.engine().lastTickEuConsumed()));
                    tag.putInt("PortPacked", core.engine().portPermissions().packed());
                    if (core.kind().spec().usesWater()) {
                        tag.putString("F1Key", "gui.ecera.gas.water");
                        tag.putInt("F1Amt", core.engine().waterMb());
                        tag.putInt("F1Cap", core.configuredWaterCapacity());
                    }
                }
            } else if (be instanceof WaterGasMachineBlockEntity wg) {
                tag.putLong("Eu", (long) Math.floor(wg.engine().storedEu()));
                tag.putLong("EuMax", (long) Math.floor(wg.engine().energyCapacity()));
                tag.putBoolean("Solar", wg.engine().isSolarActive());
                tag.putInt("SolarDeci", (int) Math.round(wg.engine().effectiveSolarEuPerTick() * 10.0));
                tag.putString("F1Key", "gui.ecera.gas.water");
                tag.putInt("F1Amt", wg.engine().waterMb());
                tag.putInt("F1Cap", WaterGasEngine.WATER_TANK_CAPACITY_MB);
                tag.putString("F2Key", "gui.ecera.gas.water_gas");
                tag.putInt("F2Amt", wg.engine().waterGasMb());
                tag.putInt("F2Cap", WaterGasEngine.GAS_TANK_CAPACITY_MB);
            } else if (be instanceof GasFurnaceBlockEntity gf) {
                tag.putString("F1Key", fuelKey(gf.engine().currentFuel()));
                tag.putInt("F1Amt", gf.engine().gasMb());
                tag.putInt("F1Cap", GasFurnaceEngine.GAS_TANK_CAPACITY_MB);
            } else if (be instanceof GasGeneratorBlockEntity gg) {
                tag.putLong("Eu", (long) Math.floor(gg.engine().storedEu()));
                tag.putLong("EuMax", (long) GasGeneratorEngine.EU_CAPACITY);
                tag.putInt("Gen", (int) Math.round(gg.engine().lastTickGeneratedEu()));
                tag.putString("F1Key", fuelKey(gg.engine().currentFuel()));
                tag.putInt("F1Amt", gg.engine().gasMb());
                tag.putInt("F1Cap", GasGeneratorEngine.GAS_TANK_CAPACITY_MB);
            } else if (be instanceof CultureIncubatorBlockEntity ci) {
                tag.putLong("Eu", (long) Math.floor(ci.engine().storedEu()));
                tag.putLong("EuMax", (long) Math.floor(ci.engine().energyCapacity()));
                tag.putString("F1Key", "gui.ecera.gas.water");
                tag.putInt("F1Amt", ci.engine().waterMb());
                tag.putInt("F1Cap", CultureIncubatorEngine.WATER_TANK_CAPACITY_MB);
                tag.putString("F2Key", "gui.ecera.gas.culture_medium");
                tag.putInt("F2Amt", ci.engine().cultureMb());
                tag.putInt("F2Cap", CultureIncubatorEngine.CULTURE_TANK_CAPACITY_MB);
            } else if (be instanceof OrganicFermenterBlockEntity of) {
                tag.putLong("Eu", (long) Math.floor(of.engine().storedEu()));
                tag.putLong("EuMax", (long) Math.floor(of.engine().energyCapacity()));
                tag.putString("F1Key", "gui.ecera.gas.culture_medium");
                tag.putInt("F1Amt", of.engine().cultureMb());
                tag.putInt("F1Cap", OrganicFermenterEngine.CULTURE_TANK_CAPACITY_MB);
                tag.putString("F2Key", "gui.ecera.gas.biogas");
                tag.putInt("F2Amt", of.engine().biogasMb());
                tag.putInt("F2Cap", OrganicFermenterEngine.BIOGAS_TANK_CAPACITY_MB);
            }
        }

        private static String fuelKey(GasFuelSpec fuel) {
            if (fuel == null) {
                return "gui.ecera.gas.empty";
            }
            return switch (fuel) {
                case WATER_GAS -> "gui.ecera.gas.water_gas";
                case NATURAL_GAS -> "gui.ecera.gas.natural_gas";
                case BIOGAS -> "gui.ecera.gas.biogas";
            };
        }
    }

    private enum ClientComponentProvider implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendBody(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
            CompoundTag tag = accessor.getData().raw();
            if (tag == null || tag.isEmpty()) {
                return;
            }
            if (tag.contains("EuMax")) {
                tooltip.addLine(
                        Component.translatable(
                                "jade.ecera.eu", tag.getLong("Eu"), tag.getLong("EuMax")));
            }
            if (tag.contains("Solar")) {
                boolean solarActive = tag.getBoolean("Solar");
                int solarDeci =
                        tag.contains("SolarDeci")
                                ? tag.getInt("SolarDeci")
                                : WaterGasEngine.SOLAR_EU_PER_TICK * 10;
                tooltip.addLine(
                        (solarActive
                                        ? Component.translatable(
                                                "gui.ecera.gas.solar_active",
                                                WaterGasEngine.formatSolarRateDeci(solarDeci))
                                        : Component.translatable("gui.ecera.gas.solar_inactive"))
                                .withStyle(
                                        solarActive ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
            }
            if (tag.contains("Gen")) {
                tooltip.addLine(Component.translatable("jade.ecera.generating", tag.getInt("Gen")));
            }
            if (tag.contains("Ku")) {
                tooltip.addLine(Component.translatable("jade.ecera.kinetic_ku", tag.getInt("Ku")));
            }
            if (tag.contains("RotorPct")) {
                tooltip.addLine(
                        Component.translatable(
                                "jade.ecera.rotor_life",
                                String.format(Locale.ROOT, "%.1f", tag.getDouble("RotorPct")),
                                String.format(Locale.ROOT, "%.1f", tag.getDouble("RotorHrs"))));
            }
            if (tag.contains("GiantOps")) {
                tooltip.addLine(
                        Component.translatable(
                                "jade.ecera.giant_lanes",
                                tag.getInt("GiantOps"),
                                tag.getInt("GiantDraw")));
                if (accessor.getBlockEntity() instanceof GiantMachineBlockEntity unit) {
                    unit.cornerPortForSide(accessor.getSide())
                            .ifPresent(
                                    port -> {
                                        PortAccessMode pm =
                                                new GiantPortPermissions(tag.getInt("PortPacked"))
                                                        .get(port);
                                        tooltip.addLine(
                                                Component.translatable(
                                                        "jade.ecera.giant_port",
                                                        Ecc2BridgePlatform.portName(port),
                                                        Ecc2BridgePlatform.modeName(pm)));
                                    });
                }
            }
            if (tag.contains("Prog")) {
                tooltip.addLine(Component.translatable("jade.ecera.progress", tag.getInt("Prog")));
            }
            if (tag.contains("F1Key")) {
                tooltip.addLine(
                        Component.translatable(
                                "jade.ecera.fluid",
                                Component.translatable(tag.getString("F1Key")),
                                tag.getInt("F1Amt"),
                                tag.getInt("F1Cap")));
            }
            if (tag.contains("F2Key")) {
                tooltip.addLine(
                        Component.translatable(
                                "jade.ecera.fluid",
                                Component.translatable(tag.getString("F2Key")),
                                tag.getInt("F2Amt"),
                                tag.getInt("F2Cap")));
            }
        }
    }
}
