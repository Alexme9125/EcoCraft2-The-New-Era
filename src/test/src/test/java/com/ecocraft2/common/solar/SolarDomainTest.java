package com.ecocraft2.common.solar;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SolarDomainTest {

    // --- SolarTier & SkyCondition ---

    @Test
    void solarTierValuesAndPacketSizes() {
        assertEquals(8, SolarTier.ADVANCED.defaultDayPower());
        assertEquals(1, SolarTier.ADVANCED.defaultNightPower());
        assertEquals(32_000, SolarTier.ADVANCED.defaultMaxStorage());
        assertEquals(1, SolarTier.ADVANCED.defaultTier());
        assertEquals(32.0, SolarTier.ADVANCED.defaultPacketSize());

        assertEquals(64, SolarTier.HYBRID.defaultDayPower());
        assertEquals(8, SolarTier.HYBRID.defaultNightPower());
        assertEquals(100_000, SolarTier.HYBRID.defaultMaxStorage());
        assertEquals(2, SolarTier.HYBRID.defaultTier());
        assertEquals(128.0, SolarTier.HYBRID.defaultPacketSize());

        assertEquals(512, SolarTier.ULTIMATE_HYBRID.defaultDayPower());
        assertEquals(64, SolarTier.ULTIMATE_HYBRID.defaultNightPower());
        assertEquals(1_000_000, SolarTier.ULTIMATE_HYBRID.defaultMaxStorage());
        assertEquals(3, SolarTier.ULTIMATE_HYBRID.defaultTier());
        assertEquals(512.0, SolarTier.ULTIMATE_HYBRID.defaultPacketSize());

        assertEquals(4_096, SolarTier.QUANTUM.defaultDayPower());
        assertEquals(2_048, SolarTier.QUANTUM.defaultNightPower());
        assertEquals(10_000_000, SolarTier.QUANTUM.defaultMaxStorage());
        assertEquals(5, SolarTier.QUANTUM.defaultTier());
        assertEquals(8_192.0, SolarTier.QUANTUM.defaultPacketSize());

        assertEquals(0.0, SolarTier.packetSize(-1));
        assertEquals(8.0, SolarTier.packetSize(0));
        assertEquals(2_048.0, SolarTier.packetSize(4));
        assertEquals(32_768.0, SolarTier.packetSize(6));
        assertEquals(Math.scalb(8.0, 60), SolarTier.packetSize(30));
        assertEquals(Math.scalb(8.0, 60), SolarTier.packetSize(99));
    }

    @Test
    void skyConditionDistinguishesRainWhileGeneratingNightPower() {
        assertEquals(SkyCondition.NONE, SkyCondition.evaluate(false, true, true, false, true));
        assertEquals(SkyCondition.NONE, SkyCondition.evaluate(true, false, true, false, true));
        assertEquals(SkyCondition.NIGHT, SkyCondition.evaluate(true, true, false, false, true));
        assertEquals(SkyCondition.NIGHT, SkyCondition.evaluate(true, true, false, true, true));
        assertEquals(SkyCondition.RAIN, SkyCondition.evaluate(true, true, true, true, true));
        assertEquals(SkyCondition.DAY, SkyCondition.evaluate(true, true, true, true, false));
        assertEquals(SkyCondition.DAY, SkyCondition.evaluate(true, true, true, false, true));

        assertEquals(0, SkyCondition.NONE.generationFor(512, 64));
        assertEquals(64, SkyCondition.NIGHT.generationFor(512, 64));
        assertEquals(64, SkyCondition.RAIN.generationFor(512, 64));
        assertEquals(512, SkyCondition.DAY.generationFor(512, 64));
    }

    // --- SolarPanelEngine ---

    @Test
    void solarPanelRefreshesSkyOnTickZeroAndEvery128Ticks() {
        SolarPanelEngine panel = new SolarPanelEngine(SolarTier.ADVANCED);

        // Tick 0: checks sky immediately -> DAY (8 EU/t)
        panel.tick(true, true, true, false, true);
        assertEquals(SkyCondition.DAY, panel.skyCondition());
        assertEquals(8, panel.currentGenerating());
        assertEquals(8.0, panel.storage());

        // Ticks 1..127: even if sky switches to rain, stays cached until tick 128
        for (int i = 1; i < 128; i++) {
            panel.tick(true, true, true, true, true);
        }
        assertEquals(SkyCondition.DAY, panel.skyCondition());
        assertEquals(128 * 8.0, panel.storage());

        // Tick 128: refreshes sky -> RAIN (1 EU/t)
        panel.tick(true, true, true, true, true);
        assertEquals(SkyCondition.RAIN, panel.skyCondition());
        assertEquals(1, panel.currentGenerating());
        assertEquals(128 * 8.0 + 1.0, panel.storage());
    }

    @Test
    void solarPanelChargingSlotsHavePriorityBeforeGridAndClampsNonNegative() {
        SolarPanelEngine panel = new SolarPanelEngine(SolarTier.HYBRID); // 64/8, tier 2 -> 128 EU/t packet

        double[] slotReceived = new double[2];
        List<ChargeSink> sinks = List.of(
                (offer, tier) -> {
                    assertEquals(2, tier);
                    double take = Math.min(offer, 40.0);
                    slotReceived[0] += take;
                    return take;
                },
                (offer, tier) -> {
                    double take = Math.min(offer, 20.0);
                    slotReceived[1] += take;
                    return take;
                }
        );

        double charged = panel.tick(true, true, true, false, true, sinks);
        assertEquals(60.0, charged);
        assertEquals(40.0, slotReceived[0]);
        assertEquals(20.0, slotReceived[1]);
        // Only 4 EU remain for the power grid after charging slots
        assertEquals(4.0, panel.storage());
        assertEquals(4.0, panel.offeredEnergy());

        // Drawing more than storage never produces negative storage
        assertEquals(4.0, panel.drawEnergy(500.0));
        assertEquals(0.0, panel.storage());
        panel.setStorage(-999.0);
        assertEquals(0.0, panel.storage());
        panel.setStorage(Double.NaN);
        assertEquals(0.0, panel.storage());

        // Malicious sink returning > offered still cannot drive storage negative
        panel.setStorage(50.0);
        double accepted = panel.chargeSlot((offer, tier) -> 9999.0);
        assertEquals(50.0, accepted);
        assertEquals(0.0, panel.storage());

        // Offered energy capped by tier packet size
        panel.setStorage(50_000.0);
        assertEquals(128.0, panel.offeredEnergy());
    }

    // --- QuantumGeneratorEngine ---

    @Test
    void quantumGeneratorDefaultsActiveAndSupportsRedstoneInversion() {
        QuantumGeneratorEngine gen = new QuantumGeneratorEngine();
        assertTrue(gen.isActive(), "Must default active for non-player placement");
        assertEquals(512, gen.production());
        assertEquals(3, gen.tier());
        assertEquals(1, gen.packetCount());
        assertEquals(512.0, gen.offeredPerPacket());

        gen.updateRedstone(true);
        assertFalse(gen.isActive());
        assertEquals(0.0, gen.offeredPerPacket());
        assertEquals(0.0, gen.totalOfferedEnergy());

        gen.updateRedstoneSignal(0);
        assertTrue(gen.isActive());
        assertEquals(512.0, gen.offeredPerPacket());

        gen.updateRedstoneSignal(15);
        assertFalse(gen.isActive());
    }

    @Test
    void quantumGeneratorExactMultiPacketMathAndTierClamping() {
        QuantumGeneratorEngine gen = new QuantumGeneratorEngine(1_000, 1); // LV = 32 EU/packet
        assertEquals(32, gen.packetCount()); // ceil(1000 / 32) = 32
        assertEquals(31.25, gen.offeredPerPacket(), 1e-12);
        assertTrue(gen.offeredPerPacket() <= gen.tierPacketSize());
        assertEquals(1_000.0, gen.packetCount() * gen.offeredPerPacket(), 1e-12);

        gen.setProduction(1_024);
        assertEquals(32, gen.packetCount());
        assertEquals(32.0, gen.offeredPerPacket(), 1e-12);

        gen.setProduction(1_025);
        assertEquals(33, gen.packetCount());
        assertEquals(1_025.0 / 33.0, gen.offeredPerPacket(), 1e-12);
        assertTrue(gen.offeredPerPacket() <= 32.0);
        assertEquals(1_025.0, gen.packetCount() * gen.offeredPerPacket(), 1e-9);

        // Tier 6 = MAX = 32,768 EU/packet
        gen.setTier(99);
        assertEquals(6, gen.tier());
        assertEquals(32_768.0, gen.tierPacketSize());
        gen.setProduction(100_000);
        assertEquals(4, gen.packetCount()); // ceil(100_000 / 32_768) = 4
        assertEquals(25_000.0, gen.offeredPerPacket(), 1e-12);

        // Clamping lower bounds
        gen.setTier(-5);
        assertEquals(1, gen.tier());
        gen.setProduction(-50);
        assertEquals(0, gen.production());
        assertEquals(1, gen.packetCount());
        assertEquals(0.0, gen.offeredPerPacket());
    }

    @Test
    void quantumGeneratorStrictButtonEventValidation() {
        QuantumGeneratorEngine gen = new QuantumGeneratorEngine(100, 1);

        assertTrue(gen.handleButtonEvent(QuantumGeneratorEngine.EVENT_ADD_100));
        assertEquals(200, gen.production());
        assertTrue(gen.handleButtonEvent(QuantumGeneratorEngine.EVENT_ADD_10));
        assertEquals(210, gen.production());
        assertTrue(gen.handleButtonEvent(QuantumGeneratorEngine.EVENT_ADD_1));
        assertEquals(211, gen.production());
        assertTrue(gen.handleButtonEvent(QuantumGeneratorEngine.EVENT_SUB_1));
        assertEquals(210, gen.production());
        assertTrue(gen.handleButtonEvent(QuantumGeneratorEngine.EVENT_SUB_10));
        assertEquals(200, gen.production());
        assertTrue(gen.handleButtonEvent(QuantumGeneratorEngine.EVENT_SUB_100));
        assertEquals(100, gen.production());

        assertTrue(gen.handleButtonEvent(QuantumGeneratorEngine.EVENT_ADD_500));
        assertEquals(600, gen.production());
        assertTrue(gen.handleButtonEvent(QuantumGeneratorEngine.EVENT_ADD_50));
        assertEquals(650, gen.production());
        assertTrue(gen.handleButtonEvent(QuantumGeneratorEngine.EVENT_ADD_5));
        assertEquals(655, gen.production());
        assertTrue(gen.handleButtonEvent(QuantumGeneratorEngine.EVENT_SUB_5));
        assertEquals(650, gen.production());
        assertTrue(gen.handleButtonEvent(QuantumGeneratorEngine.EVENT_SUB_50));
        assertEquals(600, gen.production());
        assertTrue(gen.handleButtonEvent(QuantumGeneratorEngine.EVENT_SUB_500));
        assertEquals(100, gen.production());

        // Floor at 0
        assertTrue(gen.handleButtonEvent(QuantumGeneratorEngine.EVENT_SUB_500));
        assertEquals(0, gen.production());

        // Tier buttons 20..25 -> tiers 1..6
        for (int t = 1; t <= 6; t++) {
            assertTrue(gen.handleButtonEvent(19 + t));
            assertEquals(t, gen.tier());
        }

        // Invalid events rejected with zero state mutation
        int savedProd = gen.production();
        int savedTier = gen.tier();
        int[] invalidEvents = {-100, -1, 6, 7, 8, 9, 16, 17, 18, 19, 26, 27, 999, Integer.MIN_VALUE, Integer.MAX_VALUE};
        for (int bad : invalidEvents) {
            assertFalse(QuantumGeneratorEngine.isValidButtonEvent(bad));
            assertFalse(gen.handleButtonEvent(bad));
            assertEquals(savedProd, gen.production());
            assertEquals(savedTier, gen.tier());
        }
    }

    // --- MolecularTransformerEngine (ALL ASP Bug Regressions) ---

    @Test
    void molecularTransformerExactInjectRemainderAndClearOnCompletion() {
        MolecularTransformerEngine mt = new MolecularTransformerEngine();

        // Idle: demands 0, returns full injected amount
        assertEquals(0.0, mt.demanded());
        assertEquals(500.0, mt.inject(500.0));

        // Start 1,000 EU recipe
        assertEquals(MolecularTransformerEngine.TickResult.WORKING, mt.tick("iron_to_iridium", 1_000.0, true));
        assertEquals(1_000.0, mt.demanded());

        // Inject 600 EU
        assertEquals(0.0, mt.inject(600.0));
        assertEquals(400.0, mt.demanded());

        // Over-inject 1,000 EU -> accepts only remaining 400 EU and returns exact 600 EU remainder!
        assertEquals(600.0, mt.inject(1_000.0));
        assertEquals(0.0, mt.demanded());

        // Complete recipe -> clears energyIn so zero surplus bleeds into the next craft
        assertEquals(MolecularTransformerEngine.TickResult.COMPLETED, mt.tick("iron_to_iridium", 1_000.0, true));
        assertEquals(0.0, mt.energyIn());
        assertEquals(0.0, mt.energyUsed());
        assertFalse(mt.isWorking());
        assertTrue(mt.isActive());

        // Next craft starts cleanly at 0%
        assertEquals(MolecularTransformerEngine.TickResult.WORKING, mt.tick("iron_to_iridium", 1_000.0, true));
        assertEquals(0.0, mt.energyUsed());
        assertEquals(1_000.0, mt.demanded());
    }

    @Test
    void molecularTransformerPausesWhenOutputFullWithZeroItemLoss() {
        MolecularTransformerEngine mt = new MolecularTransformerEngine();

        // Start recipe when output slot initially has space
        assertEquals(MolecularTransformerEngine.TickResult.WORKING, mt.tick("sand_to_gravel", 50_000.0, true));
        assertEquals(0.0, mt.inject(50_000.0));

        // Output fills up right before completion -> pauses, holds progress, demands 0, loses nothing!
        assertEquals(MolecularTransformerEngine.TickResult.OUTPUT_BLOCKED, mt.tick("sand_to_gravel", 50_000.0, false));
        assertTrue(mt.isWorking());
        assertTrue(mt.isOutputBlocked());
        assertEquals(50_000.0, mt.energyUsed());
        assertEquals(0.0, mt.demanded());
        assertEquals(100.0, mt.inject(100.0)); // refuses extra EU while paused

        // Still paused on subsequent ticks
        assertEquals(MolecularTransformerEngine.TickResult.OUTPUT_BLOCKED, mt.tick("sand_to_gravel", 50_000.0, false));

        // Output clears -> completes immediately with 0 extra EU needed!
        assertEquals(MolecularTransformerEngine.TickResult.COMPLETED, mt.tick("sand_to_gravel", 50_000.0, true));
        assertFalse(mt.isWorking());
        assertFalse(mt.isOutputBlocked());
    }

    @Test
    void molecularTransformerIdleTicksCappedAt40NeverOverflowsAndRejectsNonPositiveTotalEu() {
        MolecularTransformerEngine mt = new MolecularTransformerEngine();

        // Rejects <= 0 or non-finite totalEu
        assertThrows(IllegalArgumentException.class, () -> mt.beginRecipe("bad", 0.0));
        assertThrows(IllegalArgumentException.class, () -> mt.beginRecipe("bad", -100.0));
        assertThrows(IllegalArgumentException.class, () -> mt.beginRecipe("bad", Double.NaN));
        assertEquals(MolecularTransformerEngine.TickResult.IDLE, mt.tick("bad", 0.0, true));
        assertEquals(MolecularTransformerEngine.TickResult.IDLE, mt.tick("bad", -500.0, true));
        assertFalse(mt.isWorking());
        assertEquals(0.0, mt.demanded());

        // Trigger active state, then idle for 10,000 ticks (would overflow signed byte 39+ times!)
        mt.tick("coal_to_diamond", 100.0, true);
        mt.inject(100.0);
        assertEquals(MolecularTransformerEngine.TickResult.COMPLETED, mt.tick("coal_to_diamond", 100.0, true));
        assertTrue(mt.isActive());
        assertEquals(0, mt.idleTicks());

        for (int t = 1; t < MolecularTransformerEngine.MAX_IDLE_TICKS; t++) {
            mt.tick(false, 0.0, true);
            assertEquals(t, mt.idleTicks());
            assertTrue(mt.isActive());
        }

        // At tick 40 and all subsequent ticks up to 10,000, stays capped at 40 and inactive
        for (int t = 0; t < 10_000; t++) {
            mt.tick(false, 0.0, true);
            assertEquals(40, mt.idleTicks());
            assertFalse(mt.isActive());
        }
    }

    // --- SolarHelmetSpec & SolarHelmetEngine ---

    @Test
    void solarHelmetSpecsAndPerInstanceSkyIsolation() {
        assertEquals(3, SolarHelmetSpec.ADVANCED.tier());
        assertEquals(1_000_000, SolarHelmetSpec.ADVANCED.capacity());
        assertEquals(3_000, SolarHelmetSpec.ADVANCED.transferLimit());
        assertEquals(800, SolarHelmetSpec.ADVANCED.euPerDamage());
        assertEquals(0.9, SolarHelmetSpec.ADVANCED.damageAbsorptionRatio());
        assertFalse(SolarHelmetSpec.ADVANCED.hasQuantumAbilities());
        assertFalse(SolarHelmetSpec.ADVANCED.isDyeable());

        assertEquals(4, SolarHelmetSpec.HYBRID.tier());
        assertEquals(10_000_000, SolarHelmetSpec.HYBRID.capacity());
        assertEquals(10_000, SolarHelmetSpec.HYBRID.transferLimit());
        assertEquals(2_000, SolarHelmetSpec.HYBRID.euPerDamage());
        assertEquals(1.0, SolarHelmetSpec.HYBRID.damageAbsorptionRatio());
        assertTrue(SolarHelmetSpec.HYBRID.hasQuantumAbilities());
        assertTrue(SolarHelmetSpec.HYBRID.isDyeable());

        assertEquals(4, SolarHelmetSpec.ULTIMATE_HYBRID.tier());
        assertEquals(10_000_000, SolarHelmetSpec.ULTIMATE_HYBRID.capacity());
        assertEquals(10_000, SolarHelmetSpec.ULTIMATE_HYBRID.transferLimit());
        assertEquals(2_000, SolarHelmetSpec.ULTIMATE_HYBRID.euPerDamage());
        assertEquals(1.0, SolarHelmetSpec.ULTIMATE_HYBRID.damageAbsorptionRatio());
        assertTrue(SolarHelmetSpec.ULTIMATE_HYBRID.hasQuantumAbilities());
        assertTrue(SolarHelmetSpec.ULTIMATE_HYBRID.isDyeable());

        // Two independent helmet instances maintain isolated sky conditions (fixes static shared-state bug)
        SolarHelmetEngine playerA = new SolarHelmetEngine(SolarHelmetSpec.HYBRID);
        SolarHelmetEngine playerB = new SolarHelmetEngine(SolarHelmetSpec.HYBRID);

        var resA = playerA.tickGeneration(true, true, true, false, true, false, (slot, offer, tier) -> offer);
        var resB = playerB.tickGeneration(true, false, true, false, true, false, (slot, offer, tier) -> offer);

        assertEquals(SkyCondition.DAY, resA.skyCondition());
        assertEquals(64.0, resA.generatedEu());
        assertEquals(SkyCondition.NONE, resB.skyCondition());
        assertEquals(0.0, resB.generatedEu());
    }

    @Test
    void solarHelmetStrictChargingPriorityOrderAndArmorOnlyMode() {
        SolarHelmetEngine helmet = new SolarHelmetEngine(SolarHelmetSpec.HYBRID); // 64 EU/t day

        List<SolarHelmetEngine.ChargeTargetSlot> visited = new ArrayList<>();
        var result = helmet.tickGeneration(true, true, true, false, true, false, (slot, offer, tier) -> {
            assertEquals(4, tier);
            visited.add(slot);
            return Math.min(offer, 10.0); // each of 6 slots takes 10 EU -> 50 distributed + 10 self + 4 unconsumed
        });

        assertEquals(
                List.of(
                        SolarHelmetEngine.ChargeTargetSlot.FEET,
                        SolarHelmetEngine.ChargeTargetSlot.LEGS,
                        SolarHelmetEngine.ChargeTargetSlot.CHEST,
                        SolarHelmetEngine.ChargeTargetSlot.OFFHAND,
                        SolarHelmetEngine.ChargeTargetSlot.MAIN,
                        SolarHelmetEngine.ChargeTargetSlot.SELF
                ),
                visited
        );
        assertEquals(64.0, result.generatedEu());
        assertEquals(50.0, result.distributedEu());
        assertEquals(10.0, result.selfChargedEu());
        assertEquals(4.0, result.unconsumedEu());

        // Armor-only mode skips OFFHAND and MAIN
        visited.clear();
        var armorOnly = helmet.tickGeneration(true, true, true, false, true, true, (slot, offer, tier) -> {
            visited.add(slot);
            return Math.min(offer, 10.0);
        });
        assertEquals(
                List.of(
                        SolarHelmetEngine.ChargeTargetSlot.FEET,
                        SolarHelmetEngine.ChargeTargetSlot.LEGS,
                        SolarHelmetEngine.ChargeTargetSlot.CHEST,
                        SolarHelmetEngine.ChargeTargetSlot.SELF
                ),
                visited
        );
        assertEquals(30.0, armorOnly.distributedEu());
        assertEquals(10.0, armorOnly.selfChargedEu());
        assertEquals(24.0, armorOnly.unconsumedEu());
    }

    @Test
    void solarHelmetQuantumAbilitiesThresholdsAndBudgeting() {
        SolarHelmetEngine adv = new SolarHelmetEngine(SolarHelmetSpec.ADVANCED);
        assertFalse(adv.evaluateQuantumAbilities(1_000_000, 50, true, true, true, 5, true).hasAnyAction());

        SolarHelmetEngine hybrid = new SolarHelmetEngine(SolarHelmetSpec.HYBRID);
        // Full budget triggers all applicable abilities
        var full = hybrid.evaluateQuantumAbilities(100_000, 100, true, true, true, 18, true);
        assertTrue(full.refillOxygen());
        assertTrue(full.cureWither());
        assertTrue(full.curePoison());
        assertTrue(full.cureRadiation());
        assertTrue(full.autoFeed());
        assertEquals(47_000.0, full.totalEuCost());

        // Above thresholds triggers nothing
        var none = hybrid.evaluateQuantumAbilities(100_000, 101, false, false, false, 19, true);
        assertFalse(none.hasAnyAction());

        // Tight budget (26,000 EU): oxygen (1,000) + wither (25,000) = 26,000; skips poison/radiation/feed
        var budgeted = hybrid.evaluateQuantumAbilities(26_000, 50, true, true, true, 10, true);
        assertTrue(budgeted.refillOxygen());
        assertTrue(budgeted.cureWither());
        assertFalse(budgeted.curePoison());
        assertFalse(budgeted.cureRadiation());
        assertFalse(budgeted.autoFeed());
        assertEquals(26_000.0, budgeted.totalEuCost());
    }

    @Test
    void solarHelmetDamageAbsorptionExactFormula() {
        SolarHelmetEngine adv = new SolarHelmetEngine(SolarHelmetSpec.ADVANCED);
        SolarHelmetEngine ult = new SolarHelmetEngine(SolarHelmetSpec.ULTIMATE_HYBRID);

        // ceil(10 * 0.9) * 800 = 9 * 800 = 7,200 EU
        assertEquals(7_200.0, adv.calculateDamageAbsorptionEu(10.0, 50_000.0));
        // Insufficient EU returns 0
        assertEquals(0.0, adv.calculateDamageAbsorptionEu(10.0, 7_199.0));
        // Quantum helmet: ceil(10 * 1.0) * 2000 = 20,000 EU
        assertEquals(20_000.0, ult.calculateDamageAbsorptionEu(10.0, 50_000.0));
    }
}
