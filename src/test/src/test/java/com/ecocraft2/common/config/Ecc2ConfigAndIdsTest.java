package com.ecocraft2.common.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ecc2ConfigAndIdsTest {

    private static final Pattern LOWERCASE_PATH = Pattern.compile("^[a-z0-9/._-]+$");

    @Test
    void canonicalRegistryCountsAndUniqueness() {
        assertEquals("ecc2", Ecc2Ids.MOD_ID);
        assertEquals(23, Ecc2Ids.PLACEABLE_BLOCKS.size());
        assertEquals(3, Ecc2Ids.FLUID_BLOCKS.size());
        assertEquals(25, Ecc2Ids.STANDALONE_ITEMS.size());

        Set<String> allBlockPaths = new HashSet<>();
        for (String path : Ecc2Ids.PLACEABLE_BLOCKS) {
            assertTrue(LOWERCASE_PATH.matcher(path).matches(), "Invalid block path: " + path);
            assertTrue(allBlockPaths.add(path), "Duplicate block path: " + path);
        }
        for (String path : Ecc2Ids.FLUID_BLOCKS) {
            assertTrue(LOWERCASE_PATH.matcher(path).matches(), "Invalid fluid block path: " + path);
            assertTrue(allBlockPaths.add(path), "Duplicate fluid block path: " + path);
        }

        Set<String> allItemPaths = new HashSet<>();
        for (String path : Ecc2Ids.STANDALONE_ITEMS) {
            assertTrue(LOWERCASE_PATH.matcher(path).matches(), "Invalid item path: " + path);
            assertTrue(allItemPaths.add(path), "Duplicate item path: " + path);
        }

        // Duplicate items removed in favor of IC2NE native items; ecc2:iridium_ingot preserved.
        assertFalse(allItemPaths.contains("biogas_cell"), "ecc2:biogas_cell must be removed");
        assertFalse(allItemPaths.contains("uranium_ingot"), "ecc2:uranium_ingot must be removed");
        assertTrue(allItemPaths.contains(Ecc2Ids.IRIDIUM_INGOT), "ecc2:iridium_ingot must be preserved");
        assertTrue(allItemPaths.contains(Ecc2Ids.PV_POWER_UPGRADE_PATH), "ecc2:pv_power_upgrade must be registered");
    }

    @Test
    void namespaceHelpersAndKeyFluidIds() {
        assertEquals("ecc2:water_gas", Ecc2Ids.WATER_GAS_ID);
        assertEquals("ecc2:natural_gas", Ecc2Ids.NATURAL_GAS_ID);
        assertEquals("ecc2:culture_medium", Ecc2Ids.CULTURE_MEDIUM_ID);
        assertEquals("ic2:biogas", Ecc2Ids.IC2_BIOGAS_ID);
        assertEquals("ic2:biogas_cell", Ecc2Ids.IC2_BIOGAS_CELL);
        assertEquals("ic2:uranium_ingot", Ecc2Ids.IC2_URANIUM_INGOT);
        assertEquals("ecc2:iridium_ingot", Ecc2Ids.ecc2(Ecc2Ids.IRIDIUM_INGOT));
        assertEquals("ecc2:methane_ice_cluster", Ecc2Ids.ecc2(Ecc2Ids.METHANE_ICE_CLUSTER));
        assertEquals("pv_power_upgrade", Ecc2Ids.PV_POWER_UPGRADE_PATH);
        assertEquals("ecc2:pv_power_upgrade", Ecc2Ids.PV_POWER_UPGRADE_ID);
        assertEquals("ic2:carbon_rotor", Ecc2Ids.IC2_CARBON_ROTOR);
        assertEquals("ecc2:gas_fuel", Ecc2Ids.TAG_GAS_FUEL);
        assertEquals("ecc2:culture_nutrients", Ecc2Ids.TAG_CULTURE_NUTRIENTS);
        assertEquals("c:water_gas", Ecc2Ids.commonTag("c", Ecc2Ids.COMMON_TAG_WATER_GAS));
        assertEquals("forge:water_gas", Ecc2Ids.commonTag("forge", Ecc2Ids.COMMON_TAG_WATER_GAS));
        assertEquals("c:tools/wrench", Ecc2Ids.commonTag("c", Ecc2Ids.COMMON_TAG_TOOLS_WRENCH));
        assertEquals("forge:tools/wrench", Ecc2Ids.commonTag("forge", Ecc2Ids.COMMON_TAG_TOOLS_WRENCH));
    }

    @Test
    void mixinConfigsAndSourcesExcludeDuplicateVanillaRotorMixins() throws IOException {
        Path repoRoot = findRepoRoot();
        List<Path> mixinConfigs = List.of(
                repoRoot.resolve("src/neoforge/1.21.1/src/main/resources/ecc2.mixins.json"),
                repoRoot.resolve("src/neoforge/26.1.2/src/main/resources/ecc2.mixins.json"),
                repoRoot.resolve("src/neoforge/26.2/src/main/resources/ecc2.mixins.json"),
                repoRoot.resolve("forge-1.19.2/forge/1.19.2/src/main/resources/ecc2.mixins.json")
        );
        for (Path cfg : mixinConfigs) {
            assertTrue(Files.isRegularFile(cfg), "Missing mixin config: " + cfg);
            String json = Files.readString(cfg);
            assertFalse(json.contains("WorkshopNativeMixin"), "WorkshopNativeMixin must be removed from " + cfg);
            assertFalse(json.contains("RotorItemMixin"), "RotorItemMixin must be removed from " + cfg);
            assertTrue(json.contains("WorkshopKineticMixin"), "WorkshopKineticMixin must remain registered in " + cfg);
        }

        assertFalse(Files.exists(repoRoot.resolve(
                "src/neoforge/shared/src/main/java/com/ecocraft2/mixin/WorkshopNativeMixin.java")));
        assertFalse(Files.exists(repoRoot.resolve(
                "forge-1.19.2/forge/1.19.2/src/main/java/com/ecocraft2/mixin/RotorItemMixin.java")));

        for (Path km : List.of(
                repoRoot.resolve("src/neoforge/shared/src/main/java/com/ecocraft2/mixin/WorkshopKineticMixin.java"),
                repoRoot.resolve("forge-1.19.2/forge/1.19.2/src/main/java/com/ecocraft2/mixin/WorkshopKineticMixin.java")
        )) {
            String src = Files.readString(km);
            assertTrue(src.contains("ecc2$convertExternalKineticSource"), "Must retain KU bridge in " + km);
            assertFalse(src.contains("ecc2$tickVanillaRotorWear"), "Must remove duplicate vanilla wear in " + km);
        }

        // Verify both entry points register ic2:uranium_ingot via platform-appropriate common tags
        Path neoEntry = repoRoot.resolve("src/neoforge/shared/src/main/java/com/ecocraft2/EcoCraft2.java");
        Path forgeEntry = repoRoot.resolve("forge-1.19.2/forge/1.19.2/src/main/java/com/ecocraft2/EcoCraft2.java");
        for (Path entry : List.of(neoEntry, forgeEntry)) {
            String src = Files.readString(entry);
            assertFalse(src.contains("ecc2:biogas_cell"), "Must not register ecc2:biogas_cell in " + entry);
            assertFalse(src.contains("ecc2:uranium_ingot"), "Must not register ecc2:uranium_ingot in " + entry);
            assertTrue(src.contains("Ecc2Ids.IC2_URANIUM_INGOT"), "Must target ic2:uranium_ingot in " + entry);
            assertTrue(src.contains("ecc2:iridium_ingot"), "Must preserve ecc2:iridium_ingot in " + entry);
            assertFalse(src.contains("appendRotorTooltip"), "Must remove duplicate vanilla rotor tooltip in " + entry);
        }
        String neoSrc = Files.readString(neoEntry);
        assertTrue(neoSrc.contains("#c:ores/iridium"), "NeoForge compressor bridge must use #c:ores/iridium");
        assertTrue(neoSrc.contains("#c:ores/uranium"), "NeoForge compressor bridge must use #c:ores/uranium");
        assertFalse(neoSrc.contains("#forge:"), "NeoForge entry point must not leak #forge: tags");

        String forgeSrc = Files.readString(forgeEntry);
        assertTrue(forgeSrc.contains("#forge:ores/iridium"), "Forge 1.19.2 compressor bridge must use #forge:ores/iridium");
        assertTrue(forgeSrc.contains("#forge:ores/uranium"), "Forge 1.19.2 compressor bridge must use #forge:ores/uranium");
        assertFalse(forgeSrc.contains("#c:"), "Forge 1.19.2 entry point must not leak #c: tags");

        // Common Ecc2Ids must remain platform-neutral without hardcoded "c:" tag literals
        String idsSrc = Files.readString(
                repoRoot.resolve("src/common/src/main/java/com/ecocraft2/common/config/Ecc2Ids.java"));
        assertFalse(idsSrc.contains("\"c:"), "Common Ecc2Ids must not hardcode c: namespace");

        // Verify WTHIT metadata and 26.x EMI plugin exist
        assertTrue(Files.isRegularFile(repoRoot.resolve("src/neoforge/metadata/wthit_plugins.json")));
        assertTrue(Files.isRegularFile(repoRoot.resolve(
                "forge-1.19.2/forge/1.19.2/src/main/resources/wthit_plugins.json")));
        assertTrue(Files.isRegularFile(repoRoot.resolve(
                "src/neoforge/26.x/src/main/java/com/ecocraft2/compat/Ecc2EmiPlugin.java")));
    }

    private static Path findRepoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isRegularFile(dir.resolve("settings.gradle"))
                    && Files.isDirectory(dir.resolve("forge-1.19.2"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Could not locate EcoCraft 2 repository root");
    }

    @Test
    void aspAndEcoCraftConfigDefaultsMatchSpec() {
        // ASP Solar tiers
        assertEquals(8, Ecc2ConfigDefaults.ADV_SOLAR_GEN_DAY);
        assertEquals(1, Ecc2ConfigDefaults.ADV_SOLAR_GEN_NIGHT);
        assertEquals(32_000, Ecc2ConfigDefaults.ADV_SOLAR_STORAGE);
        assertEquals(1, Ecc2ConfigDefaults.ADV_SOLAR_TIER);

        assertEquals(64, Ecc2ConfigDefaults.HYBRID_SOLAR_GEN_DAY);
        assertEquals(8, Ecc2ConfigDefaults.HYBRID_SOLAR_GEN_NIGHT);
        assertEquals(100_000, Ecc2ConfigDefaults.HYBRID_SOLAR_STORAGE);
        assertEquals(2, Ecc2ConfigDefaults.HYBRID_SOLAR_TIER);

        assertEquals(512, Ecc2ConfigDefaults.ULTIMATE_SOLAR_GEN_DAY);
        assertEquals(64, Ecc2ConfigDefaults.ULTIMATE_SOLAR_GEN_NIGHT);
        assertEquals(1_000_000, Ecc2ConfigDefaults.ULTIMATE_SOLAR_STORAGE);
        assertEquals(3, Ecc2ConfigDefaults.ULTIMATE_SOLAR_TIER);

        assertEquals(4_096, Ecc2ConfigDefaults.QUANTUM_SOLAR_GEN_DAY);
        assertEquals(2_048, Ecc2ConfigDefaults.QUANTUM_SOLAR_GEN_NIGHT);
        assertEquals(10_000_000, Ecc2ConfigDefaults.QUANTUM_SOLAR_STORAGE);
        assertEquals(5, Ecc2ConfigDefaults.QUANTUM_SOLAR_TIER);

        // ASP recipe & behavior fixes
        assertFalse(Ecc2ConfigDefaults.SOLAR_HELMET_CHARGE_ARMOR_ONLY);
        assertTrue(Ecc2ConfigDefaults.HARD_RECIPES);
        assertFalse(Ecc2ConfigDefaults.EASY_ASP_RECIPE);
        assertTrue(Ecc2ConfigDefaults.DISABLE_DOUBLE_SLAB_RECIPE);
        assertTrue(Ecc2ConfigDefaults.DRAW_MOLECULAR_TRANSFORMER_CORE);

        // Kinetic, giant, gas defaults
        assertEquals(20, Ecc2ConfigDefaults.ROTOR_WEAR_INTERVAL_TICKS);
        assertEquals(4.0, Ecc2ConfigDefaults.ROTOR_OVERWIND_WEAR_MULTIPLIER);
        assertEquals(9, Ecc2ConfigDefaults.GIANT_LANE_COUNT);
        assertEquals(3, Ecc2ConfigDefaults.GIANT_MAX_BATCH_PER_LANE);
        assertEquals(3, Ecc2ConfigDefaults.GIANT_BASE_TIER);
        assertEquals(64_000, Ecc2ConfigDefaults.GIANT_ORE_WASHER_TANK_CAPACITY_MB);
        assertEquals(2, Ecc2ConfigDefaults.WATER_GAS_SOLAR_EU_PER_TICK);
        assertEquals(1.5, Ecc2ConfigDefaults.WATER_GAS_SOLAR_UPGRADE_BONUS_EU_PER_TICK, 1e-12);
        assertEquals(2, Ecc2ConfigDefaults.WATER_GAS_SOLAR_UPGRADE_MAX_EFFECTIVE);
        assertEquals(50, Ecc2ConfigDefaults.GAS_FURNACE_MB_PER_ITEM);
        assertEquals(16_000, Ecc2ConfigDefaults.GAS_GENERATOR_EU_CAPACITY);
        assertEquals(30, Ecc2ConfigDefaults.ORGANIC_FERMENTER_BIOGAS_PER_NUTRITION_MB);
    }
}
