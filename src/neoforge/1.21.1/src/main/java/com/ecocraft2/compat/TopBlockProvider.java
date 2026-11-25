package com.ecocraft2.compat;

import com.ecocraft2.EcoCraft2;
import net.minecraft.resources.ResourceLocation;

/** NeoForge 1.21.1 The One Probe provider returning a {@link ResourceLocation} ID. */
final class TopBlockProvider extends TopCompat.BaseProvider {

    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(EcoCraft2.MOD_ID, "machine_info");

    @Override
    public ResourceLocation getID() {
        return ID;
    }
}
