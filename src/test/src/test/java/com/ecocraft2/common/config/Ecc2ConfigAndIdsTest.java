package com.ecocraft2.common.config;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
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
        assertEquals(26, Ecc2Ids.STANDALONE_ITEMS.size());

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
    }

    @Test
    void namespaceHelpersAndKeyFluidIds() {
        assertEquals("ecc2:water_gas", Ecc2Ids.WATER_GAS_ID);
        assertEquals("ecc2:natural_gas", Ecc2Ids.NATURAL_GAS_ID);
        assertEquals("ecc2:culture_medium", Ecc2Ids.CULTURE_MEDIUM_ID);
        assertEquals("ic2:biogas", Ecc2Ids.IC2_BIOGAS_ID);
        assertEquals("ecc2:methane_ice_cluster", Ecc2Ids.ecc2(Ecc2Ids.METHANE_ICE_CLUSTER));
        assertEquals("ic2:carbon_rotor", Ecc2Ids.IC2_CARBON_ROTOR);
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
        assertEquals(50, Ecc2ConfigDefaults.GAS_FURNACE_MB_PER_ITEM);
        assertEquals(16_000, Ecc2ConfigDefaults.GAS_GENERATOR_EU_CAPACITY);
    }
}
