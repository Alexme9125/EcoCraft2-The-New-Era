package com.ecocraft2.common.kinetic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KineticDomainTest {

    @Test
    void rotorSpecsMatchClassicDurabilitiesAndItemIdLookup() {
        assertEquals(10_800, RotorSpec.WOOD.maxDurability());
        assertEquals(60, RotorSpec.WOOD.maxSafeWind());
        assertEquals(70, RotorSpec.WOOD.rating());
        assertEquals(5, RotorSpec.WOOD.windDiameter());
        assertEquals(4, RotorSpec.WOOD.waterDiameter());
        assertFalse(RotorSpec.WOOD.waterAllowed());
        assertEquals(3.0, RotorSpec.WOOD.nominalLifespanHours(), 1e-9);

        assertEquals(86_400, RotorSpec.BRONZE.maxDurability());
        assertEquals(75, RotorSpec.BRONZE.maxSafeWind());
        assertEquals(100, RotorSpec.BRONZE.rating());
        assertEquals(7, RotorSpec.BRONZE.windDiameter());
        assertEquals(4, RotorSpec.BRONZE.waterDiameter());
        assertTrue(RotorSpec.BRONZE.waterAllowed());
        assertEquals(24.0, RotorSpec.BRONZE.nominalLifespanHours(), 1e-9);

        assertEquals(86_400, RotorSpec.IRON.maxDurability());
        assertEquals(75, RotorSpec.IRON.maxSafeWind());
        assertEquals(100, RotorSpec.IRON.rating());
        assertTrue(RotorSpec.IRON.waterAllowed());

        assertEquals(172_800, RotorSpec.STEEL.maxDurability());
        assertEquals(90, RotorSpec.STEEL.maxSafeWind());
        assertEquals(120, RotorSpec.STEEL.rating());
        assertEquals(9, RotorSpec.STEEL.windDiameter());
        assertEquals(6, RotorSpec.STEEL.waterDiameter());
        assertTrue(RotorSpec.STEEL.waterAllowed());
        assertEquals(48.0, RotorSpec.STEEL.nominalLifespanHours(), 1e-9);

        assertEquals(604_800, RotorSpec.CARBON.maxDurability());
        assertEquals(110, RotorSpec.CARBON.maxSafeWind());
        assertEquals(150, RotorSpec.CARBON.rating());
        assertEquals(11, RotorSpec.CARBON.windDiameter());
        assertEquals(8, RotorSpec.CARBON.waterDiameter());
        assertTrue(RotorSpec.CARBON.waterAllowed());
        assertEquals(168.0, RotorSpec.CARBON.nominalLifespanHours(), 1e-9);

        assertEquals(RotorSpec.WOOD, RotorSpec.byItemId("ic2:wooden_rotor").orElseThrow());
        assertEquals(RotorSpec.BRONZE, RotorSpec.byItemId("ic2:bronze_rotor").orElseThrow());
        assertEquals(RotorSpec.IRON, RotorSpec.byItemId("ic2:iron_rotor").orElseThrow());
        assertEquals(RotorSpec.STEEL, RotorSpec.byItemId("ic2:steel_rotor").orElseThrow());
        assertEquals(RotorSpec.CARBON, RotorSpec.byItemId("ic2:carbon_rotor").orElseThrow());
        assertTrue(RotorSpec.byItemId("ic2:unknown").isEmpty());
        assertTrue(RotorSpec.byItemId(null).isEmpty());
    }

    @Test
    void kineticMachineTiersAndOutputScaling() {
        assertEquals(100, KineticCalculation.scaleOutput(100, KineticMachineTier.STANDARD));
        assertEquals(80, KineticCalculation.scaleOutput(100, KineticMachineTier.DURABLE));
        assertEquals(220, KineticCalculation.scaleOutput(100, KineticMachineTier.OVERCLOCKED));
        assertEquals(240, KineticCalculation.scaleOutput(100, KineticMachineTier.PRECISION));
        assertEquals(0, KineticCalculation.scaleOutput(-10, KineticMachineTier.PRECISION));
        assertEquals(25.0, KineticCalculation.kuToEu(100.0), 1e-9);

        // Over-wind is strictly > maxSafeWind
        assertFalse(KineticCalculation.isOverWind(60.0, RotorSpec.WOOD));
        assertTrue(KineticCalculation.isOverWind(60.1, RotorSpec.WOOD));
        assertFalse(KineticCalculation.isOverWind(110.0, RotorSpec.CARBON));
        assertTrue(KineticCalculation.isOverWind(110.5, RotorSpec.CARBON));
        assertFalse(KineticCalculation.canUseInWater(RotorSpec.WOOD));
        assertTrue(KineticCalculation.canUseInWater(RotorSpec.BRONZE));
    }

    @Test
    void rotorWearAccumulatorExactRatesAt50_100_200PercentAnd4xOverwind() {
        // 50% wear (Durable / Precision): 1 durability every 40 active ticks
        RotorWearAccumulator durable = new RotorWearAccumulator();
        int totalDurable = 0;
        for (int t = 1; t <= 2_000; t++) {
            int dmg = durable.recordTick(true, false, KineticMachineTier.DURABLE);
            totalDurable += dmg;
            if (t == 20) {
                assertEquals(0, dmg);
                assertEquals(0.5, durable.fractionalWear(), 1e-12);
            } else if (t == 40) {
                assertEquals(1, dmg);
                assertEquals(0.0, durable.fractionalWear(), 1e-12);
            }
        }
        assertEquals(50, totalDurable); // 2000t = 100s * 0.5 = 50 points exact!

        // 100% wear (Standard): 1 per 20 active ticks
        RotorWearAccumulator standard = new RotorWearAccumulator();
        int totalStandard = 0;
        for (int t = 0; t < 2_000; t++) {
            totalStandard += standard.recordTick(true, false, KineticMachineTier.STANDARD);
        }
        assertEquals(100, totalStandard);

        // 200% wear (Overclocked): 2 per 20 active ticks
        RotorWearAccumulator overclocked = new RotorWearAccumulator();
        int totalOverclocked = 0;
        for (int t = 0; t < 2_000; t++) {
            totalOverclocked += overclocked.recordTick(true, false, KineticMachineTier.OVERCLOCKED);
        }
        assertEquals(200, totalOverclocked);

        // 4x over-wind across all tiers
        RotorWearAccumulator owPrecision = new RotorWearAccumulator();
        RotorWearAccumulator owStandard = new RotorWearAccumulator();
        RotorWearAccumulator owOverclocked = new RotorWearAccumulator();
        int dmgP = 0, dmgS = 0, dmgO = 0;
        for (int t = 0; t < 200; t++) { // 10 seconds
            dmgP += owPrecision.recordTick(true, true, KineticMachineTier.PRECISION);       // 4 * 0.5 = 2/s -> 20
            dmgS += owStandard.recordTick(true, true, KineticMachineTier.STANDARD);         // 4 * 1.0 = 4/s -> 40
            dmgO += owOverclocked.recordTick(true, true, KineticMachineTier.OVERCLOCKED);   // 4 * 2.0 = 8/s -> 80
        }
        assertEquals(20, dmgP);
        assertEquals(40, dmgS);
        assertEquals(80, dmgO);
    }

    @Test
    void rotorWearIgnoresInactiveTicksAndPersistsFractionalRemainder() {
        RotorWearAccumulator acc = new RotorWearAccumulator();

        // 100 inactive ticks cause zero wear and keep tick counter at 0
        for (int t = 0; t < 100; t++) {
            assertEquals(0, acc.recordTick(false, true, KineticMachineTier.OVERCLOCKED));
        }
        assertEquals(0, acc.activeTickCounter());
        assertEquals(0.0, acc.fractionalWear());

        // Run 25 active ticks at 0.5x wear -> tickCounter = 5, fractionalWear = 0.5
        for (int t = 0; t < 25; t++) {
            assertEquals(0, acc.recordTick(true, false, KineticMachineTier.PRECISION));
        }
        assertEquals(5, acc.activeTickCounter());
        assertEquals(0.5, acc.fractionalWear(), 1e-12);

        // Persist and reload into a fresh accumulator -> next 15 active ticks emit exact 1 durability point
        RotorWearAccumulator reloaded = new RotorWearAccumulator();
        reloaded.restore(acc.activeTickCounter(), acc.fractionalWear());
        int sum = 0;
        for (int t = 0; t < 15; t++) {
            sum += reloaded.recordTick(true, false, KineticMachineTier.PRECISION);
        }
        assertEquals(1, sum);
        assertEquals(0, reloaded.activeTickCounter());
        assertEquals(0.0, reloaded.fractionalWear(), 1e-12);
    }
}
