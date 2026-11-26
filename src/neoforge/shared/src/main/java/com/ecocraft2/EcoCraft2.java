package com.ecocraft2;

import com.ecocraft2.bridge.Ecc2BridgePlatform;
import com.ecocraft2.bridge.Ecc2ExternalRecipes;
import com.ecocraft2.common.config.Ecc2Ids;
import com.ecocraft2.config.Ecc2Config;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NeoForge entry point for EcoCraft 2: The New Era ({@code ecc2}).
 *
 * <p>Bootstraps configuration, IC2NE 1.0.6 bridge registrations (named cells, compressor/extruder/
 * canner recipes), the shared {@code ecc2} Creative Mode Tab, and delegates gameplay/compat
 * registration to modular installers so parallel feature modules never conflict on this class.
 */
@Mod(EcoCraft2.MOD_ID)
public final class EcoCraft2 {

    /** Canonical EcoCraft 2 mod identifier and resource namespace. */
    public static final String MOD_ID = "ecc2";

    private static final Logger LOG = LoggerFactory.getLogger(EcoCraft2.class);

    /**
     * Well-known module bootstrapper classes automatically invoked when present on the classpath.
     * Each class must expose {@code public static void install(IEventBus modBus, IEventBus gameBus,
     * ModContainer container, Dist dist)}.
     */
    private static final List<String> KNOWN_MODULES =
            List.of(
                    "com.ecocraft2.solar.SolarKineticModule",
                    "com.ecocraft2.giant.GiantGasModule",
                    "com.ecocraft2.compat.CompatModule");

    private static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    private static final CopyOnWriteArrayList<Consumer<CreativeModeTab.Output>> TAB_CONTRIBUTORS =
            new CopyOnWriteArrayList<>();

    /** Shared EcoCraft 2 creative mode tab populated by feature modules via {@link #addCreativeTabItems}. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_TAB =
            CREATIVE_TABS.register(
                    "main",
                    () ->
                            CreativeModeTab.builder()
                                    .title(Component.translatable("itemGroup.ecc2"))
                                    .icon(EcoCraft2::creativeTabIcon)
                                    .displayItems(
                                            (parameters, output) -> {
                                                for (var contributor : TAB_CONTRIBUTORS) {
                                                    contributor.accept(output);
                                                }
                                            })
                                    .build());

    /** Extension interface for feature or addon modules loaded via {@link ServiceLoader}. */
    public interface Ecc2Module {
        void install(IEventBus modBus, IEventBus gameBus, ModContainer container, Dist dist);
    }

    /** Called by NeoForge during mod construction. */
    public EcoCraft2(IEventBus modBus, ModContainer container, Dist dist) {
        Ecc2Config.register(container);
        registerDefaultIc2Bridges();

        var gameBus = NeoForge.EVENT_BUS;

        CREATIVE_TABS.register(modBus);
        installKnownModules(modBus, gameBus, container, dist);
        installServiceModules(modBus, gameBus, container, dist);

        var metadata = container.getModInfo();
        LOG.info("Initialized {} {}", metadata.getDisplayName(), metadata.getVersion());
    }

    /**
     * Registers a contributor callback invoked when populating the shared {@code ecc2} Creative
     * Mode Tab.
     */
    public static void addCreativeTabItems(Consumer<CreativeModeTab.Output> contributor) {
        if (contributor != null) {
            TAB_CONTRIBUTORS.add(contributor);
        }
    }

    private static void registerDefaultIc2Bridges() {
        // 1. Named 1000 mB fluid cells bridged into IC2NE CellFamily & TransportPlatform.
        // Biogas cells reuse ic2:biogas_cell natively.
        Ecc2ExternalRecipes.registerNamedCell(
                true, "ecc2:basic_culture_medium_cell", "ecc2:culture_medium");
        Ecc2ExternalRecipes.registerNamedCell(false, "ecc2:water_gas_cell", "ecc2:water_gas");
        Ecc2ExternalRecipes.registerNamedCell(false, "ecc2:natural_gas_cell", "ecc2:natural_gas");
        Ecc2ExternalRecipes.registerNamedCell(
                false, "ecc2:culture_medium_cell", "ecc2:culture_medium");

        // 2. Methane ice canning bottle recipe (ic2:canner mode 0 / solid_canner / classic_canner).
        Ecc2ExternalRecipes.registerCanningBottle(
                "ic2:fluid_cell", 1, "ecc2:methane_ice_cluster", 3, "ecc2:natural_gas_cell", 1);

        // 3. ASP 2 compressor recipes (Iridium Ore -> ecc2:iridium_ingot; Uranium ores/dusts/fuel -> ic2:uranium_ingot).
        Ecc2ExternalRecipes.registerCompressor("ic2:iridium_ore", 1, "ecc2:iridium_ingot", 1);
        Ecc2ExternalRecipes.registerCompressor(
                "ic2:uranium_ore", 1, Ecc2Ids.IC2_URANIUM_INGOT, 1);
        Ecc2ExternalRecipes.registerCompressor(
                "ic2:deepslate_uranium_ore", 1, Ecc2Ids.IC2_URANIUM_INGOT, 1);
        Ecc2ExternalRecipes.registerCompressor(
                "ic2:raw_uranium", 1, Ecc2Ids.IC2_URANIUM_INGOT, 1);
        Ecc2ExternalRecipes.registerCompressor(
                "ic2:crushed_uranium", 1, Ecc2Ids.IC2_URANIUM_INGOT, 1);
        Ecc2ExternalRecipes.registerCompressor(
                "ic2:purified_uranium", 1, Ecc2Ids.IC2_URANIUM_INGOT, 1);
        Ecc2ExternalRecipes.registerCompressor("ic2:uranium", 1, Ecc2Ids.IC2_URANIUM_INGOT, 1);

        // 4. ASP 2 metal former extruding recipe (Reinforced Iridium Plate ic2:iridium -> ecc2:iridium_ingot).
        Ecc2ExternalRecipes.registerExtruder("ic2:iridium", 1, "ecc2:iridium_ingot", 1);
    }

    private static void installKnownModules(
            IEventBus modBus, IEventBus gameBus, ModContainer container, Dist dist) {
        for (String className : KNOWN_MODULES) {
            try {
                var type = Class.forName(className, true, EcoCraft2.class.getClassLoader());
                var install =
                        type.getMethod(
                                "install",
                                IEventBus.class,
                                IEventBus.class,
                                ModContainer.class,
                                Dist.class);
                install.invoke(null, modBus, gameBus, container, dist);
                LOG.info("Installed EcoCraft 2 module {}", className);
            } catch (ClassNotFoundException absent) {
                LOG.debug("EcoCraft 2 module {} not present on classpath", className);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(
                        "Failed to install EcoCraft 2 module " + className, failure);
            }
        }
    }

    private static void installServiceModules(
            IEventBus modBus, IEventBus gameBus, ModContainer container, Dist dist) {
        for (var module : ServiceLoader.load(Ecc2Module.class, EcoCraft2.class.getClassLoader())) {
            module.install(modBus, gameBus, container, dist);
            LOG.info("Installed EcoCraft 2 service module {}", module.getClass().getName());
        }
    }

    private static ItemStack creativeTabIcon() {
        for (String candidate :
                List.of(
                        "ecc2:ultimate_hybrid_solar_panel",
                        "ecc2:advanced_solar_panel",
                        "ecc2:water_gas_machine",
                        "ic2:machine")) {
            Item item = Ecc2BridgePlatform.findItem(candidate);
            if (item != null && item != Items.AIR) {
                return new ItemStack(item);
            }
        }
        return new ItemStack(Items.DAYLIGHT_DETECTOR);
    }
}
