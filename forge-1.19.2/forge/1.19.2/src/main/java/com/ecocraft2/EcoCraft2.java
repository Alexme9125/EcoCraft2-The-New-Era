package com.ecocraft2;

import com.ecocraft2.bridge.Ecc2ExternalRecipes;
import com.ecocraft2.client.Ecc2ClientSetup;
import com.ecocraft2.common.config.Ecc2Ids;
import com.ecocraft2.compat.Ecc2TopCompat;
import com.ecocraft2.config.Ecc2Config;
import com.ecocraft2.config.SolarConfigRecipeCondition;
import com.ecocraft2.gas.GasRegistry;
import com.ecocraft2.giant.GiantRegistry;
import com.ecocraft2.network.Ecc2Network;
import com.ecocraft2.registry.Ecc2Registries;
import com.ecocraft2.solar.SolarHelmetItem;
import com.ecocraft2.solar.SolarKineticRegistry;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Forge 1.19.2 entry point for EcoCraft 2: The New Era ({@code ecc2}). */
@Mod(EcoCraft2.MOD_ID)
public final class EcoCraft2 {

    /** Canonical EcoCraft 2 mod identifier and resource namespace. */
    public static final String MOD_ID = "ecc2";

    private static final Logger LOG = LoggerFactory.getLogger(EcoCraft2.class);

    /** Shared EcoCraft 2 creative mode tab populated by all EcoCraft 2 items and blocks. */
    public static final CreativeModeTab TAB =
            new CreativeModeTab(MOD_ID) {
                @Override
                public ItemStack makeIcon() {
                    return new ItemStack(SolarKineticRegistry.ULTIMATE_HYBRID_SOLAR_PANEL.get());
                }
            };

    public EcoCraft2() {
        Ecc2Config.register();
        registerDefaultIc2Bridges();

        // Trigger static registry declarations across all 6 gameplay modules.
        SolarKineticRegistry.bootstrap();
        GiantRegistry.bootstrap();
        GasRegistry.bootstrap();

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        Ecc2Registries.registerAll(modBus);
        modBus.addListener(this::onRegisterEvent);
        modBus.addListener(this::onCommonSetup);
        modBus.addListener(this::onInterModEnqueue);

        IEventBus gameBus = MinecraftForge.EVENT_BUS;
        gameBus.addListener(this::onPlayerLoggedOut);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Ecc2ClientSetup.register(modBus));

        LOG.info("Initialized EcoCraft 2: The New Era (Forge 1.19.2)");
    }

    private void onRegisterEvent(RegisterEvent event) {
        if (event.getRegistryKey().equals(ForgeRegistries.Keys.RECIPE_SERIALIZERS)) {
            CraftingHelper.register(SolarConfigRecipeCondition.Serializer.INSTANCE);
        }
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(Ecc2Network::register);
    }

    private void onInterModEnqueue(InterModEnqueueEvent event) {
        if (ModList.get().isLoaded("theoneprobe")) {
            Ecc2TopCompat.enqueueImc();
        }
    }

    private void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SolarHelmetItem.clearPlayerEngine(event.getEntity().getUUID());
    }

    private static void registerDefaultIc2Bridges() {
        // 1. Named 1000 mB fluid cells bridged into IC2NE CellFamily & CellItem & TransportPlatform.
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
}
