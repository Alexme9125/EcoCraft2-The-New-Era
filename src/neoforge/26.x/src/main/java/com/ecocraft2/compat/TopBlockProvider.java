package com.ecocraft2.compat;

import com.ecocraft2.EcoCraft2;
import net.minecraft.resources.Identifier;

/** NeoForge 26.x The One Probe provider returning an {@link Identifier} ID. */
final class TopBlockProvider extends TopCompat.BaseProvider {

    private static final Identifier ID =
            Identifier.fromNamespaceAndPath(EcoCraft2.MOD_ID, "machine_info");

    @Override
    public Identifier getID() {
        return ID;
    }
}
