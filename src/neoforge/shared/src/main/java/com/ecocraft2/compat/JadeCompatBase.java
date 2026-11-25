package com.ecocraft2.compat;

import com.ecocraft2.gas.CultureIncubatorBlockEntity;
import com.ecocraft2.gas.GasFurnaceBlockEntity;
import com.ecocraft2.gas.GasGeneratorBlockEntity;
import com.ecocraft2.gas.GasMachineBlock;
import com.ecocraft2.gas.OrganicFermenterBlockEntity;
import com.ecocraft2.gas.WaterGasMachineBlockEntity;
import com.ecocraft2.giant.GiantUnitBlock;
import com.ecocraft2.giant.GiantUnitBlockEntity;
import com.ecocraft2.kinetic.AdvancedKineticBlock;
import com.ecocraft2.kinetic.AdvancedKineticBlockEntity;
import com.ecocraft2.solar.MolecularTransformerBlock;
import com.ecocraft2.solar.MolecularTransformerBlockEntity;
import com.ecocraft2.solar.QuantumGeneratorBlock;
import com.ecocraft2.solar.QuantumGeneratorBlockEntity;
import com.ecocraft2.solar.SolarPanelBlock;
import com.ecocraft2.solar.SolarPanelBlockEntity;
import net.minecraft.nbt.CompoundTag;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;

/**
 * Shared Jade registration helper used by the versioned {@code @WailaPlugin} classes in {@code
 * 1.21.1/} and {@code 26.x/}.
 */
final class JadeCompatBase {

    private JadeCompatBase() {}

    static void registerServerProviders(
            IWailaCommonRegistration registration, IServerDataProvider<BlockAccessor> provider) {
        registration.registerBlockDataProvider(provider, SolarPanelBlockEntity.class);
        registration.registerBlockDataProvider(provider, QuantumGeneratorBlockEntity.class);
        registration.registerBlockDataProvider(provider, MolecularTransformerBlockEntity.class);
        registration.registerBlockDataProvider(provider, AdvancedKineticBlockEntity.class);
        registration.registerBlockDataProvider(provider, GiantUnitBlockEntity.class);
        registration.registerBlockDataProvider(provider, WaterGasMachineBlockEntity.class);
        registration.registerBlockDataProvider(provider, GasFurnaceBlockEntity.class);
        registration.registerBlockDataProvider(provider, GasGeneratorBlockEntity.class);
        registration.registerBlockDataProvider(provider, CultureIncubatorBlockEntity.class);
        registration.registerBlockDataProvider(provider, OrganicFermenterBlockEntity.class);
    }

    static void registerClientComponents(
            IWailaClientRegistration registration, IBlockComponentProvider provider) {
        registration.registerBlockComponent(provider, SolarPanelBlock.class);
        registration.registerBlockComponent(provider, QuantumGeneratorBlock.class);
        registration.registerBlockComponent(provider, MolecularTransformerBlock.class);
        registration.registerBlockComponent(provider, AdvancedKineticBlock.class);
        registration.registerBlockComponent(provider, GiantUnitBlock.class);
        registration.registerBlockComponent(provider, GasMachineBlock.class);
    }

    static void writeBlockServerData(CompoundTag tag, BlockAccessor accessor) {
        var be = accessor.getBlockEntity();
        if (be != null) {
            CompatTelemetry.writeServerData(tag, be, accessor.getSide());
        }
    }
}
