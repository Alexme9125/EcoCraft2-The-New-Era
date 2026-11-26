package com.ecocraft2.compat;

import com.ecocraft2.gas.CultureIncubatorBlockEntity;
import com.ecocraft2.gas.GasFurnaceBlockEntity;
import com.ecocraft2.gas.GasGeneratorBlockEntity;
import com.ecocraft2.gas.OrganicFermenterBlockEntity;
import com.ecocraft2.gas.WaterGasMachineBlockEntity;
import com.ecocraft2.giant.GiantUnitBlockEntity;
import com.ecocraft2.kinetic.AdvancedKineticBlockEntity;
import com.ecocraft2.solar.MolecularTransformerBlockEntity;
import com.ecocraft2.solar.QuantumGeneratorBlockEntity;
import com.ecocraft2.solar.SolarPanelBlockEntity;
import mcp.mobius.waila.api.ICommonRegistrar;
import mcp.mobius.waila.api.IDataProvider;
import mcp.mobius.waila.api.IDataWriter;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.IServerAccessor;
import mcp.mobius.waila.api.IWailaCommonPlugin;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Shared NeoForge WTHIT common plugin syncing authoritative server-side telemetry for all EcoCraft
 * 2 machines across 1.21.1, 26.1.2, and 26.2.
 *
 * <p>Registered via {@code wthit_plugins.json} and loaded exclusively when WTHIT is installed.
 */
public final class Ecc2WthitCommonPlugin implements IWailaCommonPlugin {

    @Override
    public void register(ICommonRegistrar registrar) {
        registrar.blockData(BlockData.INSTANCE, SolarPanelBlockEntity.class);
        registrar.blockData(BlockData.INSTANCE, QuantumGeneratorBlockEntity.class);
        registrar.blockData(BlockData.INSTANCE, MolecularTransformerBlockEntity.class);
        registrar.blockData(BlockData.INSTANCE, AdvancedKineticBlockEntity.class);
        registrar.blockData(BlockData.INSTANCE, GiantUnitBlockEntity.class);
        registrar.blockData(BlockData.INSTANCE, WaterGasMachineBlockEntity.class);
        registrar.blockData(BlockData.INSTANCE, GasFurnaceBlockEntity.class);
        registrar.blockData(BlockData.INSTANCE, GasGeneratorBlockEntity.class);
        registrar.blockData(BlockData.INSTANCE, CultureIncubatorBlockEntity.class);
        registrar.blockData(BlockData.INSTANCE, OrganicFermenterBlockEntity.class);
    }

    private enum BlockData implements IDataProvider<BlockEntity> {
        INSTANCE;

        @Override
        public void appendData(
                IDataWriter data, IServerAccessor<BlockEntity> accessor, IPluginConfig config) {
            BlockEntity be = accessor.getTarget();
            if (be == null) {
                return;
            }
            Direction hitSide =
                    accessor.getHitResult() instanceof BlockHitResult bhr
                            ? bhr.getDirection()
                            : null;
            CompatTelemetry.writeServerData(data.raw(), be, hitSide);
        }
    }
}
