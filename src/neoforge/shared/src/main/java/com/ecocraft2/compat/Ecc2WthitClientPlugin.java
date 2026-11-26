package com.ecocraft2.compat;

import com.ecocraft2.gas.GasMachineBlock;
import com.ecocraft2.giant.GiantUnitBlock;
import com.ecocraft2.kinetic.AdvancedKineticBlock;
import com.ecocraft2.solar.MolecularTransformerBlock;
import com.ecocraft2.solar.QuantumGeneratorBlock;
import com.ecocraft2.solar.SolarPanelBlock;
import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IClientRegistrar;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import mcp.mobius.waila.api.IWailaClientPlugin;
import net.minecraft.nbt.CompoundTag;

/**
 * Shared NeoForge WTHIT client plugin rendering EcoCraft 2 machine telemetry across 1.21.1, 26.1.2,
 * and 26.2.
 *
 * <p>Registered via {@code wthit_plugins.json} and loaded exclusively when WTHIT is installed.
 */
public final class Ecc2WthitClientPlugin implements IWailaClientPlugin {

    @Override
    public void register(IClientRegistrar registrar) {
        registrar.body(BlockProvider.INSTANCE, SolarPanelBlock.class);
        registrar.body(BlockProvider.INSTANCE, QuantumGeneratorBlock.class);
        registrar.body(BlockProvider.INSTANCE, MolecularTransformerBlock.class);
        registrar.body(BlockProvider.INSTANCE, AdvancedKineticBlock.class);
        registrar.body(BlockProvider.INSTANCE, GiantUnitBlock.class);
        registrar.body(BlockProvider.INSTANCE, GasMachineBlock.class);
    }

    private enum BlockProvider implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendBody(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getData().raw();
            if (data == null || data.isEmpty()) {
                return;
            }
            CompatTelemetry.appendClientLines(CompatTagView.of(data), tooltip::addLine);
        }
    }
}
