package com.ecocraft2.common.gas;

import com.ecocraft2.common.giant.GiantUpgradeProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GasDomainTest {

    // --- GasFuelSpec ---

    @Test
    void gasFuelSpecsMatchCanonicalValues() {
        assertEquals(10_000, GasFuelSpec.WATER_GAS.euPerBucket());
        assertEquals(32, GasFuelSpec.WATER_GAS.euPerTick());
        assertEquals(10.0, GasFuelSpec.WATER_GAS.euPerMb(), 1e-12);
        assertEquals(3.2, GasFuelSpec.WATER_GAS.mbPerTick(), 1e-12);

        assertEquals(10_000, GasFuelSpec.NATURAL_GAS.euPerBucket());
        assertEquals(64, GasFuelSpec.NATURAL_GAS.euPerTick());
        assertEquals(10.0, GasFuelSpec.NATURAL_GAS.euPerMb(), 1e-12);
        assertEquals(6.4, GasFuelSpec.NATURAL_GAS.mbPerTick(), 1e-12);

        assertEquals(16_000, GasFuelSpec.BIOGAS.euPerBucket());
        assertEquals(32, GasFuelSpec.BIOGAS.euPerTick());
        assertEquals(16.0, GasFuelSpec.BIOGAS.euPerMb(), 1e-12);
        assertEquals(2.0, GasFuelSpec.BIOGAS.mbPerTick(), 1e-12);

        assertEquals(GasFuelSpec.WATER_GAS, GasFuelSpec.byFluidId("ecera:water_gas").orElseThrow());
        assertEquals(GasFuelSpec.NATURAL_GAS, GasFuelSpec.byFluidId("ecera:natural_gas").orElseThrow());
        assertEquals(GasFuelSpec.BIOGAS, GasFuelSpec.byFluidId("mhera:biogas").orElseThrow());
        assertTrue(GasFuelSpec.byFluidId("minecraft:water").isEmpty());

        // Common fluid tag & ecc2:gas_fuel resolution across both c: and forge: namespaces
        assertEquals(
                GasFuelSpec.WATER_GAS,
                GasFuelSpec.resolve("other:water_gas", "c", "c:water_gas"::equals).orElseThrow());
        assertEquals(
                GasFuelSpec.NATURAL_GAS,
                GasFuelSpec.resolve("other:methane", "forge", "forge:natural_gas"::equals).orElseThrow());
        assertEquals(
                GasFuelSpec.BIOGAS,
                GasFuelSpec.resolve("other:bio_gas", "c", "c:biogas"::equals).orElseThrow());
        assertEquals(
                GasFuelSpec.WATER_GAS,
                GasFuelSpec.resolve("datapack:syngas", "c", "ecera:gas_fuel"::equals).orElseThrow());
        assertTrue(GasFuelSpec.resolve("minecraft:water", "c", tag -> false).isEmpty());
    }

    // --- WaterGasEngine ---

    @Test
    void waterGasEngineRunsAutonomouslyOn2EuSolarAndAtFullSpeedOnLvGrid() {
        // Pure daytime solar (2 EU/t): 1000 EU takes exactly 500 daytime ticks -> 1000 mB water gas
        WaterGasEngine solarOnly = new WaterGasEngine();
        assertEquals(1, solarOnly.inputTier());
        assertEquals(32.0, solarOnly.maxInputPacket());
        assertEquals(8_000, solarOnly.fillWater(10_000, false)); // capped at 8000 mB

        int completions = 0;
        for (int t = 1; t <= 500; t++) {
            GasMachineOutcome out = solarOnly.tick(true, true, true, true, false, true);
            assertTrue(solarOnly.isSolarActive());
            if (out == GasMachineOutcome.COMPLETED) {
                completions++;
            }
        }
        assertEquals(1, completions);
        assertEquals(7_900, solarOnly.waterMb());
        assertEquals(1_000, solarOnly.waterGasMb());
        assertEquals(0.0, solarOnly.storedEu(), 1e-12);

        // At night (no solar) with LV grid power: completes in exactly 200 ticks consuming 1000 EU
        WaterGasEngine gridPowered = new WaterGasEngine();
        gridPowered.fillWater(500, false);
        gridPowered.injectEu(1_000.0);

        for (int t = 1; t <= 199; t++) {
            assertEquals(GasMachineOutcome.WORKING, gridPowered.tick(true, true, true, false, false, true));
            assertFalse(gridPowered.isSolarActive());
        }
        assertEquals(GasMachineOutcome.COMPLETED, gridPowered.tick(true, true, true, false, false, true));
        assertEquals(400, gridPowered.waterMb());
        assertEquals(1_000, gridPowered.waterGasMb());
        assertEquals(0.0, gridPowered.storedEu(), 1e-12);

        // Output tank full pauses cleanly without wasting water or coal
        gridPowered.setWaterGasMb(7_500); // cannot fit +1000 mB
        gridPowered.injectEu(500.0);
        assertEquals(GasMachineOutcome.OUTPUT_FULL, gridPowered.tick(true, true, true, false, false, true));
        assertEquals(400, gridPowered.waterMb());
        assertEquals(7_500, gridPowered.waterGasMb());
    }

    @Test
    void waterGasEnginePvPowerUpgradeScalesAndCapsAtTwoForDaytimeSelfSufficiency() {
        WaterGasEngine engine = new WaterGasEngine();
        assertEquals(0, engine.solarPowerUpgrades());
        assertEquals(2.0, engine.effectiveSolarEuPerTick(), 1e-12);
        assertEquals("2", WaterGasEngine.formatSolarRate(engine.effectiveSolarEuPerTick()));
        assertEquals("2", WaterGasEngine.formatSolarRateDeci(20));

        // Negative values clamp to 0
        engine.setSolarPowerUpgrades(-5);
        assertEquals(0, engine.solarPowerUpgrades());
        assertEquals(2.0, engine.effectiveSolarEuPerTick(), 1e-12);

        // 1 upgrade -> 3.5 EU/t (fractional output)
        engine.setSolarPowerUpgrades(1);
        assertEquals(1, engine.solarPowerUpgrades());
        assertEquals(3.5, engine.effectiveSolarEuPerTick(), 1e-12);
        assertEquals("3.5", WaterGasEngine.formatSolarRate(engine.effectiveSolarEuPerTick()));
        assertEquals("3.5", WaterGasEngine.formatSolarRateDeci(35));
        assertEquals(GasMachineOutcome.IDLE, engine.tick(false, true, true, true, false, true));
        assertTrue(engine.isSolarActive());
        assertEquals(3.5, engine.storedEu(), 1e-12);

        // 2, 3, and 10 upgrades all strictly cap at 5.0 EU/t (effective max = 2)
        for (int installed : new int[]{2, 3, 10}) {
            engine.setSolarPowerUpgrades(installed);
            assertEquals(installed, engine.solarPowerUpgrades());
            assertEquals(5.0, engine.effectiveSolarEuPerTick(), 1e-12);
            assertEquals("5", WaterGasEngine.formatSolarRate(engine.effectiveSolarEuPerTick()));
        }
        assertEquals("5", WaterGasEngine.formatSolarRateDeci(50));

        // With 2 PV upgrades and 0 grid EU, a full 200-tick clear daytime cycle completes cleanly with 0 net EU loss
        WaterGasEngine selfSufficient = new WaterGasEngine();
        selfSufficient.setSolarPowerUpgrades(2);
        assertEquals(8_000, selfSufficient.fillWater(8_000, false));
        assertEquals(0.0, selfSufficient.storedEu(), 1e-12);
        for (int t = 1; t <= 199; t++) {
            assertEquals(GasMachineOutcome.WORKING, selfSufficient.tick(true, true, true, true, false, true));
            assertTrue(selfSufficient.isSolarActive());
            assertEquals(0.0, selfSufficient.storedEu(), 1e-12);
        }
        assertEquals(GasMachineOutcome.COMPLETED, selfSufficient.tick(true, true, true, true, false, true));
        assertEquals(7_900, selfSufficient.waterMb());
        assertEquals(1_000, selfSufficient.waterGasMb());
        assertEquals(0.0, selfSufficient.storedEu(), 1e-12);

        // Installing 3+ PV upgrades must NOT generate extra surplus EU above the 2-upgrade cap
        WaterGasEngine overCapped = new WaterGasEngine();
        overCapped.setSolarPowerUpgrades(10);
        overCapped.fillWater(8_000, false);
        for (int t = 1; t <= 200; t++) {
            overCapped.tick(true, true, true, true, false, true);
        }
        assertEquals(1_000, overCapped.waterGasMb());
        assertEquals(0.0, overCapped.storedEu(), 1e-12);
    }

    // --- GasFurnaceEngine ---

    @Test
    void gasFurnaceConsumesExact50MbPerItemAcrossAllOverclockerLevels() {
        int[] overclockerCounts = {0, 1, 2, 3, 5, 10, 20};
        for (int oc : overclockerCounts) {
            GasFurnaceEngine furnace = new GasFurnaceEngine();
            furnace.setOverclockers(oc);
            int opTicks = furnace.operationTicks();
            assertEquals(1_000, furnace.fillGas(GasFuelSpec.WATER_GAS, 1_000, false));

            int smelted = 0;
            int totalTicks = 20 * opTicks; // 20 items * 50 mB = exact 1,000 mB
            for (int t = 0; t < totalTicks; t++) {
                GasMachineOutcome out = furnace.tick(true, true);
                if (out == GasMachineOutcome.COMPLETED) {
                    smelted++;
                }
            }
            assertEquals(20, smelted, "Failed item count for overclockers=" + oc);
            assertEquals(0, furnace.gasMb(), "Must consume exact 50 mB/item for overclockers=" + oc);
            assertNull(furnace.currentFuel());
        }
    }

    @Test
    void gasFurnacePartialGasResumeAndNoFuelMixing() {
        GasFurnaceEngine furnace = new GasFurnaceEngine();
        assertEquals(17, furnace.fillGas(GasFuelSpec.NATURAL_GAS, 17, false));
        // Refuses mixing biogas while natural gas is in tank
        assertEquals(0, furnace.fillGas(GasFuelSpec.BIOGAS, 100, false));

        // Run until gas runs dry mid-smelt
        int ticks = 0;
        while (furnace.tick(true, true) == GasMachineOutcome.WORKING) {
            ticks++;
        }
        assertEquals(0, furnace.gasMb());
        assertTrue(ticks > 0 && ticks < 100);
        assertEquals(17, furnace.gasSpentOnCurrentItem());

        // Refill with remaining 33 mB (now allowed to switch fuel since tank emptied!)
        assertEquals(33, furnace.fillGas(GasFuelSpec.BIOGAS, 33, false));
        int completed = 0;
        for (int t = 0; t < 100; t++) {
            GasMachineOutcome out = furnace.tick(true, true);
            if (out == GasMachineOutcome.COMPLETED) {
                completed++;
                break;
            }
        }
        assertEquals(1, completed);
        assertEquals(0, furnace.gasMb());
    }

    // --- GasGeneratorEngine (Exact Energy Conservation) ---

    @Test
    void gasGeneratorConservesExactEuForAllThreeFuelsWithZeroLoss() {
        verifyExactGeneratorConservation(GasFuelSpec.WATER_GAS, 1_000, 10_000.0);
        verifyExactGeneratorConservation(GasFuelSpec.NATURAL_GAS, 1_000, 10_000.0);
        verifyExactGeneratorConservation(GasFuelSpec.BIOGAS, 1_000, 16_000.0);

        // Odd small volumes also conserve exact EU with zero loss
        verifyExactGeneratorConservation(GasFuelSpec.WATER_GAS, 1, 10.0);
        verifyExactGeneratorConservation(GasFuelSpec.WATER_GAS, 7, 70.0);
        verifyExactGeneratorConservation(GasFuelSpec.NATURAL_GAS, 137, 1_370.0);
        verifyExactGeneratorConservation(GasFuelSpec.BIOGAS, 3, 48.0);
    }

    private void verifyExactGeneratorConservation(GasFuelSpec fuel, int volumeMb, double expectedTotalEu) {
        GasGeneratorEngine gen = new GasGeneratorEngine();
        assertEquals(volumeMb, gen.fillGas(fuel, volumeMb, false));

        double totalDrawn = 0.0;
        int guard = 0;
        while ((gen.gasMb() > 0 || gen.pendingEu() > 0.0) && guard < 10_000) {
            double generated = gen.tick();
            assertTrue(generated <= fuel.euPerTick() + 1e-12);
            double offer = gen.offeredEnergy();
            assertTrue(offer <= 128.0 + 1e-12, "Must respect MV Tier 2 128 EU/t packet cap");
            totalDrawn += gen.drawEnergy(offer);
            guard++;
        }
        totalDrawn += gen.drawEnergy(gen.storedEu());

        assertEquals(expectedTotalEu, totalDrawn, 1e-9, "Energy lost for " + fuel + " (" + volumeMb + " mB)");
        assertEquals(0, gen.gasMb());
        assertEquals(0.0, gen.pendingEu(), 1e-12);
        assertNull(gen.currentFuel());
    }

    @Test
    void gasGeneratorStopsAtFullBufferRejectsFuelMixAndPersistsPendingEu() {
        GasGeneratorEngine gen = new GasGeneratorEngine();
        assertEquals(128.0, gen.packetSize());
        assertEquals(16_000, gen.fillGas(GasFuelSpec.WATER_GAS, 20_000, false)); // capped at 16,000 mB

        // Fill buffer nearly full (15,990 EU -> only 10 EU room)
        gen.setStoredEu(15_990.0);
        double generated = gen.tick();
        assertEquals(10.0, generated, 1e-12);
        assertEquals(16_000.0, gen.storedEu(), 1e-12);
        assertEquals(15_999, gen.gasMb()); // burned exact 1 mB for 10 EU!

        // Next tick with full buffer burns 0 gas
        assertEquals(0.0, gen.tick(), 1e-12);
        assertFalse(gen.isActive());
        assertEquals(15_999, gen.gasMb());

        // Drain buffer and run 1 tick (32 EU -> draws 4 mB, leaves pendingEu = 8.0)
        gen.drawEnergy(16_000.0);
        assertEquals(32.0, gen.tick(), 1e-12);
        assertEquals(15_995, gen.gasMb());
        assertEquals(8.0, gen.pendingEu(), 1e-12);
        assertEquals(0.8, gen.fractionalMbRemaining(), 1e-12);

        // Cannot mix different fuel while tank or pendingEu is non-empty
        assertEquals(0, gen.fillGas(GasFuelSpec.NATURAL_GAS, 1_000, false));

        // Persistence round-trip preserves exact pendingEu & total remaining potential EU
        double expectedRemaining = gen.totalRemainingPotentialEu();
        GasGeneratorEngine reloaded = new GasGeneratorEngine();
        reloaded.restoreState(gen.currentFuel(), gen.gasMb(), gen.pendingEu(), gen.storedEu());
        assertEquals(expectedRemaining, reloaded.totalRemainingPotentialEu(), 1e-12);
    }

    // --- CultureIncubatorEngine ---

    @Test
    void cultureIncubatorProduces100MbCulturePerBonemealAnd100MbWater() {
        CultureIncubatorEngine inc = new CultureIncubatorEngine();
        assertEquals(1, inc.inputTier());
        assertEquals(8_000, inc.fillWater(9_000, false));
        inc.injectEu(200.0);

        for (int t = 1; t <= 99; t++) {
            assertEquals(GasMachineOutcome.WORKING, inc.tick(true));
        }
        assertEquals(GasMachineOutcome.COMPLETED, inc.tick(true));
        assertEquals(7_900, inc.waterMb());
        assertEquals(100, inc.cultureMb());
        assertEquals(0.0, inc.storedEu(), 1e-12);

        // No bonemeal -> idle
        assertEquals(GasMachineOutcome.IDLE, inc.tick(false));

        // Output tank full -> OUTPUT_FULL
        inc.setCultureMb(7_950);
        inc.injectEu(200.0);
        assertEquals(GasMachineOutcome.OUTPUT_FULL, inc.tick(true));
    }

    // --- OrganicFermenterEngine ---

    @Test
    void organicFermenterProduces30MbPerNutritionPointAndChecksContainerReturn() {
        OrganicFermenterEngine ferm = new OrganicFermenterEngine();
        assertEquals(1, ferm.inputTier());
        assertEquals(30, ferm.biogasPerNutritionMb());
        assertEquals(30, OrganicFermenterEngine.biogasYieldForNutrition(1));
        assertEquals(8_000, ferm.fillCulture(10_000, false));
        ferm.injectEu(2_000.0);

        // Rejects non-food (H <= 0)
        assertEquals(GasMachineOutcome.IDLE, ferm.tick(0));
        assertEquals(GasMachineOutcome.IDLE, ferm.tick(-5));
        assertEquals(8_000, ferm.cultureMb());

        // Ferment steak (H = 8) -> 240 mB ic2:biogas, 50 mB culture, 200 EU / 100 ticks
        for (int t = 1; t <= 99; t++) {
            assertEquals(GasMachineOutcome.WORKING, ferm.tick(8));
        }
        assertEquals(GasMachineOutcome.COMPLETED, ferm.tick(8));
        assertEquals(240, ferm.lastCompletedBiogasMb());
        assertEquals(240, ferm.biogasMb());
        assertEquals(7_950, ferm.cultureMb());
        assertEquals(1_800.0, ferm.storedEu(), 1e-12);

        // Ferment mushroom stew (H = 6) with blocked container return slot -> pauses cleanly
        assertEquals(GasMachineOutcome.OUTPUT_FULL, ferm.tick(6, false));
        assertEquals(0, ferm.progress());

        // Unblock container return slot and swap food mid-cycle -> resets progress safely
        for (int t = 0; t < 50; t++) {
            assertEquals(GasMachineOutcome.WORKING, ferm.tick(1, true));
        }
        assertEquals(50, ferm.progress());
        assertEquals(GasMachineOutcome.WORKING, ferm.tick(10, true));
        assertEquals(1, ferm.progress(), "Food swap mid-cycle must reset progress to prevent exploit");

        // Complete the 10-hunger food (99 more ticks -> +300 mB biogas)
        for (int t = 2; t <= 99; t++) {
            assertEquals(GasMachineOutcome.WORKING, ferm.tick(10, true));
        }
        assertEquals(GasMachineOutcome.COMPLETED, ferm.tick(10, true));
        assertEquals(300, ferm.lastCompletedBiogasMb());
        assertEquals(540, ferm.biogasMb());

        // Upgrades accelerate ticks and scale tier/storage
        ferm.setUpgrades(new GiantUpgradeProfile(2, 1, 1));
        assertEquals(49, ferm.operationTicks());
        assertEquals(2, ferm.inputTier());
        assertEquals(14_000.0, ferm.energyCapacity(), 1e-9);
    }

    @Test
    void biogasH1FoodYields480EuAndRemainsNetPositiveWithFourOverclockers() {
        // 1 food (H = 1) -> 30 mB biogas -> 480 EU in Gas Generator
        OrganicFermenterEngine baseFerm = new OrganicFermenterEngine();
        baseFerm.fillCulture(1_000, false);
        baseFerm.injectEu(1_000.0);
        for (int t = 1; t < baseFerm.operationTicks(); t++) {
            assertEquals(GasMachineOutcome.WORKING, baseFerm.tick(1, true));
        }
        assertEquals(GasMachineOutcome.COMPLETED, baseFerm.tick(1, true));
        assertEquals(30, baseFerm.lastCompletedBiogasMb());

        GasGeneratorEngine gen = new GasGeneratorEngine();
        assertEquals(30, gen.fillGas(GasFuelSpec.BIOGAS, baseFerm.biogasMb(), false));
        double totalEuGenerated = 0.0;
        while (gen.gasMb() > 0 || gen.pendingEu() > 0.0) {
            gen.tick();
            totalEuGenerated += gen.drawEnergy(gen.offeredEnergy());
        }
        totalEuGenerated += gen.drawEnergy(gen.storedEu());
        assertEquals(480.0, totalEuGenerated, 1e-9);

        // Base total energy cost: 0.5 * 200 EU (incubator) + 200 EU (fermenter) = 300 EU < 480 EU
        double baseCostPerFood = 0.5 * CultureIncubatorEngine.BASE_EU_PER_OP
                + OrganicFermenterEngine.BASE_EU_PER_OP;
        assertEquals(300.0, baseCostPerFood, 1e-9);
        assertTrue(totalEuGenerated > baseCostPerFood);

        // With 4 Overclockers on BOTH Culture Incubator and Organic Fermenter:
        GiantUpgradeProfile fourOc = new GiantUpgradeProfile(4, 0, 0);
        CultureIncubatorEngine ocInc = new CultureIncubatorEngine();
        ocInc.setUpgrades(fourOc);
        ocInc.fillWater(1_000, false);
        ocInc.injectEu(1_000.0);
        double incStartEu = ocInc.storedEu();
        for (int t = 0; t < ocInc.operationTicks(); t++) {
            ocInc.tick(true);
        }
        double incEuPer100Mb = incStartEu - ocInc.storedEu();
        assertEquals(100, ocInc.cultureMb());

        OrganicFermenterEngine ocFerm = new OrganicFermenterEngine();
        ocFerm.setUpgrades(fourOc);
        ocFerm.fillCulture(100, false); // enough for 2 H=1 food cycles
        ocFerm.injectEu(1_000.0);
        double fermStartEu = ocFerm.storedEu();
        for (int cycle = 0; cycle < 2; cycle++) {
            for (int t = 0; t < ocFerm.operationTicks(); t++) {
                ocFerm.tick(1, true);
            }
        }
        double fermEuForTwoFoods = fermStartEu - ocFerm.storedEu();
        assertEquals(60, ocFerm.biogasMb());

        double ocCostPerFood = (incEuPer100Mb + fermEuForTwoFoods) / 2.0;
        double continuousFourOcCost = 300.0 * Math.pow(
                GiantUpgradeProfile.OVERCLOCKER_TIME_FACTOR * GiantUpgradeProfile.OVERCLOCKER_ENERGY_FACTOR, 4);
        assertEquals(472.06, continuousFourOcCost, 0.01);
        assertTrue(totalEuGenerated > ocCostPerFood, "480 EU must beat actual 4-OC cost " + ocCostPerFood);
        assertTrue(totalEuGenerated > 472.06, "480 EU must beat theoretical 4-OC cost 472.06 EU");
    }
}
