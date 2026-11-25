package com.ecocraft2.compat;

import com.ecocraft2.EcoCraft2;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * NeoForge 26.x (26.1.2 &amp; 26.2) Jade ({@code @WailaPlugin}) integration for all EcoCraft 2
 * machines.
 *
 * <p>Loaded exclusively by Jade when Jade is installed.
 */
@WailaPlugin
public final class Ecc2JadePlugin implements IWailaPlugin {

    /** Canonical Jade provider UID matching {@code config.jade.plugin_ecc2.machine_info}. */
    public static final Identifier UID =
            Identifier.fromNamespaceAndPath(EcoCraft2.MOD_ID, "machine_info");

    @Override
    public void register(IWailaCommonRegistration registration) {
        JadeCompatBase.registerServerProviders(registration, ServerProvider.INSTANCE);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        JadeCompatBase.registerClientComponents(registration, ClientProvider.INSTANCE);
    }

    private enum ServerProvider implements IServerDataProvider<BlockAccessor> {
        INSTANCE;

        @Override
        public Identifier getUid() {
            return UID;
        }

        @Override
        public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
            JadeCompatBase.writeBlockServerData(tag, accessor);
        }
    }

    private enum ClientProvider implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public Identifier getUid() {
            return UID;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (data == null || data.isEmpty()) {
                return;
            }
            CompatTelemetry.appendClientLines(new TagView26(data), tooltip::add);
        }
    }

    private record TagView26(CompoundTag tag) implements CompatTelemetry.TagView {

        @Override
        public boolean contains(String key) {
            return tag.contains(key);
        }

        @Override
        public int getInt(String key, int fallback) {
            return tag.getIntOr(key, fallback);
        }

        @Override
        public long getLong(String key, long fallback) {
            return tag.getLongOr(key, fallback);
        }

        @Override
        public double getDouble(String key, double fallback) {
            return tag.getDoubleOr(key, fallback);
        }

        @Override
        public boolean getBoolean(String key, boolean fallback) {
            return tag.getBooleanOr(key, fallback);
        }

        @Override
        public String getString(String key, String fallback) {
            return tag.getStringOr(key, fallback);
        }
    }
}
