package com.ecocraft2.compat;

import com.ecocraft2.bridge.Ecc2BridgePlatform;
import com.ecocraft2.bridge.Ecc2RotorHooks;
import com.ecocraft2.common.gas.CultureIncubatorEngine;
import com.ecocraft2.common.gas.GasFuelSpec;
import com.ecocraft2.common.gas.GasFurnaceEngine;
import com.ecocraft2.common.gas.GasGeneratorEngine;
import com.ecocraft2.common.gas.OrganicFermenterEngine;
import com.ecocraft2.common.gas.WaterGasEngine;
import com.ecocraft2.common.giant.GiantCornerPort;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/** Optional Jade WAILA plugin for EcoCraft 2 (discovered automatically by Jade when present). */
@WailaPlugin
public final class Ecc2JadePlugin implements IWailaPlugin {

    public static final ResourceLocation UID = Ecc2BridgePlatform.rl("machine_info");

    @Override
    public void register(IWailaCommonRegistration registration) {
        ServerProvider provider = new ServerProvider();
        registration.registerBlockDataProvider(provider, SolarPanelBlockEntity.class);
        registration.registerBlockDataProvider(provider, QuantumGeneratorBlockEntity.class);
        registration.registerBlockDataProvider(provider, MolecularTransformerBlockEntity.class);
        registration.registerBlockDataProvider(provider, AdvancedKineticBlockEntity.class);
        registration.registerBlockDataProvider(provider, GiantMachineBlockEntity.class);
        registration.registerBlockDataProvider(provider, WaterGasMachineBlockEntity.class);
        registration.registerBlockDataProvider(provider, GasFurnaceBlockEntity.class);
        registration.registerBlockDataProvider(provider, GasGeneratorBlockEntity.class);
        registration.registerBlockDataProvider(provider, CultureIncubatorBlockEntity.class);
        registration.registerBlockDataProvider(provider, OrganicFermenterBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        ClientProvider client = new ClientProvider();
        registration.registerBlockComponent(client, SolarPanelBlock.class);
        registration.registerBlockComponent(client, QuantumGeneratorBlock.class);
        registration.registerBlockComponent(client, MolecularTransformerBlock.class);
        registration.registerBlockComponent(client, AdvancedKineticBlock.class);
        registration.registerBlockComponent(client, GiantMachineBlock.class);
        registration.registerBlockComponent(client, GasMachineBlock.class);
    }

    private static final class ServerProvider implements IServerDataProvider<BlockEntity> {
        @Override
        public ResourceLocation getUid() {
            return UID;
        }

        @Override
        public void appendServerData(
                CompoundTag tag,
                ServerPlayer player,
                Level level,
                BlockEntity be,
                boolean showDetails) {
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
                        tag.putString("F1Key", "gui.ecc2.gas.water");
                        tag.putInt("F1Amt", core.engine().waterMb());
                        tag.putInt("F1Cap", core.configuredWaterCapacity());
                    }
                }
            } else if (be instanceof WaterGasMachineBlockEntity wg) {
                tag.putLong("Eu", (long) Math.floor(wg.engine().storedEu()));
                tag.putLong("EuMax", (long) Math.floor(wg.engine().energyCapacity()));
                tag.putString("F1Key", "gui.ecc2.gas.water");
                tag.putInt("F1Amt", wg.engine().waterMb());
                tag.putInt("F1Cap", WaterGasEngine.WATER_TANK_CAPACITY_MB);
                tag.putString("F2Key", "gui.ecc2.gas.water_gas");
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
                tag.putString("F1Key", "gui.ecc2.gas.water");
                tag.putInt("F1Amt", ci.engine().waterMb());
                tag.putInt("F1Cap", CultureIncubatorEngine.WATER_TANK_CAPACITY_MB);
                tag.putString("F2Key", "gui.ecc2.gas.culture_medium");
                tag.putInt("F2Amt", ci.engine().cultureMb());
                tag.putInt("F2Cap", CultureIncubatorEngine.CULTURE_TANK_CAPACITY_MB);
            } else if (be instanceof OrganicFermenterBlockEntity of) {
                tag.putLong("Eu", (long) Math.floor(of.engine().storedEu()));
                tag.putLong("EuMax", (long) Math.floor(of.engine().energyCapacity()));
                tag.putString("F1Key", "gui.ecc2.gas.culture_medium");
                tag.putInt("F1Amt", of.engine().cultureMb());
                tag.putInt("F1Cap", OrganicFermenterEngine.CULTURE_TANK_CAPACITY_MB);
                tag.putString("F2Key", "gui.ecc2.gas.biogas");
                tag.putInt("F2Amt", of.engine().biogasMb());
                tag.putInt("F2Cap", OrganicFermenterEngine.BIOGAS_TANK_CAPACITY_MB);
            }
        }

        private static String fuelKey(GasFuelSpec fuel) {
            if (fuel == null) {
                return "gui.ecc2.gas.empty";
            }
            return switch (fuel) {
                case WATER_GAS -> "gui.ecc2.gas.water_gas";
                case NATURAL_GAS -> "gui.ecc2.gas.natural_gas";
                case BIOGAS -> "gui.ecc2.gas.biogas";
            };
        }
    }

    private static final class ClientProvider implements IBlockComponentProvider {
        @Override
        public ResourceLocation getUid() {
            return UID;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag tag = accessor.getServerData();
            if (tag.contains("EuMax")) {
                tooltip.add(
                        Component.translatable("jade.ecc2.eu", tag.getLong("Eu"), tag.getLong("EuMax")));
            }
            if (tag.contains("Gen")) {
                tooltip.add(Component.translatable("jade.ecc2.generating", tag.getInt("Gen")));
            }
            if (tag.contains("Ku")) {
                tooltip.add(Component.translatable("jade.ecc2.kinetic_ku", tag.getInt("Ku")));
            }
            if (tag.contains("RotorPct")) {
                tooltip.add(
                        Component.translatable(
                                "jade.ecc2.rotor_life",
                                String.format(Locale.ROOT, "%.1f", tag.getDouble("RotorPct")),
                                String.format(Locale.ROOT, "%.1f", tag.getDouble("RotorHrs"))));
            }
            if (tag.contains("GiantOps")) {
                tooltip.add(
                        Component.translatable(
                                "jade.ecc2.giant_lanes",
                                tag.getInt("GiantOps"),
                                tag.getInt("GiantDraw")));
                if (accessor.getBlockEntity() instanceof GiantMachineBlockEntity unit) {
                    unit.cornerPortForSide(accessor.getSide())
                            .ifPresent(
                                    port -> {
                                        PortAccessMode pm =
                                                new com.ecocraft2.common.giant.GiantPortPermissions(
                                                                tag.getInt("PortPacked"))
                                                        .get(port);
                                        tooltip.add(
                                                Component.translatable(
                                                        "jade.ecc2.giant_port",
                                                        Ecc2BridgePlatform.portName(port),
                                                        Ecc2BridgePlatform.modeName(pm)));
                                    });
                }
            }
            if (tag.contains("Prog")) {
                tooltip.add(Component.translatable("jade.ecc2.progress", tag.getInt("Prog")));
            }
            if (tag.contains("F1Key")) {
                tooltip.add(
                        Component.translatable(
                                "jade.ecc2.fluid",
                                Component.translatable(tag.getString("F1Key")),
                                tag.getInt("F1Amt"),
                                tag.getInt("F1Cap")));
            }
            if (tag.contains("F2Key")) {
                tooltip.add(
                        Component.translatable(
                                "jade.ecc2.fluid",
                                Component.translatable(tag.getString("F2Key")),
                                tag.getInt("F2Amt"),
                                tag.getInt("F2Cap")));
            }
        }
    }
}
