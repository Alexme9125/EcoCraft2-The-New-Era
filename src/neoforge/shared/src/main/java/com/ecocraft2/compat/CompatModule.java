package com.ecocraft2.compat;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Optional mod compatibility entry point for EcoCraft 2 ({@code ecc2}).
 *
 * <p>Automatically discovered and invoked by {@link com.ecocraft2.EcoCraft2}. This class never
 * imports or directly links any third-party mod API (JEI, EMI, Jade, or The One Probe) so EcoCraft
 * 2 runs cleanly alongside IC2NE with zero optional mods installed.
 *
 * <ul>
 *   <li>JEI ({@code @JeiPlugin}), EMI ({@code @EmiEntrypoint}, 1.21.1 only), and Jade ({@code
 *       @WailaPlugin}) discover their dedicated plugin classes solely when those mods are present.
 *   <li>The One Probe is wired via {@link InterModEnqueueEvent} strictly behind a {@link
 *       ModList#isLoaded(String)} guard for {@code "theoneprobe"}.
 * </ul>
 */
public final class CompatModule {

    private static final Logger LOG = LoggerFactory.getLogger(CompatModule.class);

    private static volatile boolean installed;

    private CompatModule() {}

    /** Installs optional compatibility hooks without loading absent mod APIs. */
    public static synchronized void install(
            IEventBus modBus, IEventBus gameBus, ModContainer container, Dist dist) {
        if (installed) {
            return;
        }
        installed = true;

        modBus.addListener(InterModEnqueueEvent.class, CompatModule::onInterModEnqueue);

        var mods = ModList.get();
        boolean jei = mods.isLoaded("jei");
        boolean emi = mods.isLoaded("emi");
        boolean jade = mods.isLoaded("jade");
        boolean top = mods.isLoaded("theoneprobe");
        LOG.info(
                "EcoCraft 2 optional compat detected: jei={}, emi={}, jade={}, theoneprobe={}",
                jei,
                emi,
                jade,
                top);
    }

    private static void onInterModEnqueue(InterModEnqueueEvent event) {
        if (ModList.get().isLoaded("theoneprobe")) {
            TopCompat.enqueue();
        }
    }
}
