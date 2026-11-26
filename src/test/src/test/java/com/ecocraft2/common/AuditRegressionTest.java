package com.ecocraft2.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ecocraft2.common.gas.GasFuelSpec;
import com.ecocraft2.common.gas.GasFurnaceEngine;
import com.ecocraft2.common.gas.GasGeneratorEngine;
import com.ecocraft2.common.gas.GasMachineOutcome;
import com.ecocraft2.common.gas.OrganicFermenterEngine;
import com.ecocraft2.common.gas.WaterGasEngine;
import com.ecocraft2.common.giant.GiantMachineEngine;
import com.ecocraft2.common.giant.GiantMachineKind;
import com.ecocraft2.common.solar.ChargeSink;
import com.ecocraft2.common.solar.QuantumGeneratorEngine;
import com.ecocraft2.common.solar.SolarPanelEngine;
import com.ecocraft2.common.solar.SolarTier;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Regression tests verifying fixes for ECC2-01, ECC2-10, ECC2-11, ECC2-14, and ECC2-17. */
final class AuditRegressionTest {

    @Test
    void quantumGeneratorCapsPacketsAndNeverExceedsTierVoltage() {
        var engine = new QuantumGeneratorEngine(Integer.MAX_VALUE, 1);
        assertEquals(QuantumGeneratorEngine.MAX_PRODUCTION, engine.production());
        assertEquals(QuantumGeneratorEngine.MAX_PACKETS_PER_TICK, engine.packetCount());
        assertTrue(
                engine.offeredPerPacket() <= engine.tierPacketSize() + 1e-9,
                "Per-packet voltage must never exceed Tier 1 packet limit (32 EU)");
        assertEquals(32.0, engine.offeredPerPacket(), 1e-9);

        engine.setTier(6);
        engine.setProduction(QuantumGeneratorEngine.MAX_PRODUCTION);
        assertEquals(64, engine.packetCount());
        assertEquals(32_768.0, engine.offeredPerPacket(), 1e-9);
    }

    @Test
    void solarPanelSharesSinglePacketBudgetAcrossSlotsAndGrid() {
        var panel = new SolarPanelEngine(SolarTier.ADVANCED);
        panel.setStorage(10_000.0);
        double packet = panel.packetSize(); // 32 EU/t

        // First slot takes 10 EU, second slot wants 100 EU (must get at most 22 EU), grid gets 0.
        double[] taken = new double[2];
        ChargeSink slot0 =
                (offer, tier) -> {
                    double d = Math.min(offer, 10.0);
                    taken[0] += d;
                    return d;
                };
        ChargeSink slot1 =
                (offer, tier) -> {
                    double d = Math.min(offer, 100.0);
                    taken[1] += d;
                    return d;
                };

        double charged = panel.tick(true, true, true, false, true, List.of(slot0, slot1));
        assertEquals(packet, charged, 1e-9);
        assertEquals(10.0, taken[0], 1e-9);
        assertEquals(22.0, taken[1], 1e-9);
        assertEquals(0.0, panel.offeredEnergy(), 1e-9);
        assertEquals(0.0, panel.drawEnergy(packet), 1e-9);

        // Next tick resets the per-tick discharge budget; partial slot charge leaves remainder for grid.
        ChargeSink partialSlot = (offer, tier) -> Math.min(offer, 12.0);
        panel.tick(true, true, true, false, true, List.of(partialSlot));
        assertEquals(20.0, panel.offeredEnergy(), 1e-9);
        assertEquals(20.0, panel.drawEnergy(packet), 1e-9);
        assertEquals(0.0, panel.offeredEnergy(), 1e-9);
    }

    @Test
    void giantMachinePreservesProgressWhenOutputFullAndCompletesWhenCleared() {
        var engine = new GiantMachineEngine(GiantMachineKind.MACERATOR);
        engine.setStoredEu(engine.energyCapacity());

        AtomicBoolean outputHasRoom = new AtomicBoolean(true);
        AtomicBoolean inputPresent = new AtomicBoolean(true);
        AtomicInteger completedOps = new AtomicInteger(0);

        GiantMachineEngine.LaneHandler handler =
                new GiantMachineEngine.LaneHandler() {
                    @Override
                    public int availableInputBatch(int lane, int maxBatch) {
                        return lane == 0 && inputPresent.get() ? Math.min(3, maxBatch) : 0;
                    }

                    @Override
                    public int availableBatch(int lane, int maxBatch) {
                        if (lane != 0 || !inputPresent.get() || !outputHasRoom.get()) {
                            return 0;
                        }
                        return Math.min(3, maxBatch);
                    }

                    @Override
                    public void completeOperations(int lane, int ops) {
                        completedOps.addAndGet(ops);
                    }
                };

        // Run half the cycle with output open.
        int opTicks = engine.operationTicks();
        int half = Math.max(1, opTicks / 2);
        for (int i = 0; i < half; i++) {
            engine.tick(handler);
        }
        int savedProgress = engine.laneProgress(0);
        assertTrue(savedProgress > 0, "Lane 0 should accumulate progress");
        assertEquals(3, engine.laneBatchSize(0));

        // Output fills up: progress and EU must pause without resetting!
        outputHasRoom.set(false);
        double euBeforePause = engine.storedEu();
        for (int i = 0; i < 10; i++) {
            var summary = engine.tick(handler);
            assertEquals(0.0, summary.euConsumed(), 1e-9);
            assertEquals(0, summary.completedOps());
        }
        assertEquals(savedProgress, engine.laneProgress(0), "Output-full pause must preserve progress");
        assertEquals(euBeforePause, engine.storedEu(), 1e-9);

        // Output clears: lane resumes from savedProgress and completes!
        outputHasRoom.set(true);
        for (int i = 0; i < opTicks - savedProgress + 2; i++) {
            engine.tick(handler);
            if (completedOps.get() > 0) {
                break;
            }
        }
        assertEquals(3, completedOps.get(), "Lane should complete all 3 ops once output clears");

        // Removing input resets an active lane cleanly.
        engine.tick(handler);
        assertTrue(engine.laneProgress(0) > 0);
        inputPresent.set(false);
        engine.tick(handler);
        assertEquals(0, engine.laneProgress(0));
    }

    @Test
    void giantMachineAppliesDynamicConfigOverrides() {
        var washer = new GiantMachineEngine(GiantMachineKind.ORE_WASHER);
        washer.setBaseTier(4);
        washer.setMaxBatchPerLane(12);
        washer.setWaterTankCapacityMb(128_000);

        assertEquals(4, washer.inputTier());
        assertEquals(2_048.0, washer.maxInputPacket(), 1e-9);
        assertEquals(12, washer.maxBatchPerLane());
        assertEquals(128_000, washer.waterTankCapacityMb());
        assertEquals(100_000, washer.fillWater(100_000, false));
        assertEquals(100_000, washer.waterMb());

        int[] prog = new int[GiantMachineEngine.LANE_COUNT];
        int[] batch = new int[GiantMachineEngine.LANE_COUNT];
        prog[0] = 10;
        batch[0] = 10;
        washer.restoreLanes(prog, batch);
        assertEquals(10, washer.laneBatchSize(0));
    }

    @Test
    void gasGeneratorRestoreTankStatePreservesPendingEuAndFuel() {
        var gen = new GasGeneratorEngine();
        gen.fillGas(GasFuelSpec.NATURAL_GAS, 1, false); // 1 mB = 10 EU pending, generates 64 EU/t
        // Suppose we configure Natural Gas to 100 EU/mB @ 32 EU/t so 1 mB takes >3 ticks to drain
        gen.configureFuel(GasFuelSpec.NATURAL_GAS, 100_000, 32);
        double firstTick = gen.tick(null);
        assertEquals(32.0, firstTick, 1e-9);
        assertEquals(0, gen.gasMb());
        assertTrue(gen.pendingEu() > 60.0);
        assertEquals(GasFuelSpec.NATURAL_GAS, gen.currentFuel());

        // Simulating a tank snapshot restore after a failed/rolled-back container interaction:
        gen.restoreTankState(null, 0);
        assertNotNull(gen.currentFuel(), "Active fuel must be preserved while pendingEu > 0");
        assertEquals(GasFuelSpec.NATURAL_GAS, gen.currentFuel());
        assertTrue(gen.pendingEu() > 60.0);

        // Drain remaining pendingEu to 0, then restoreTankState(null, 0) clears fuel cleanly.
        gen.tick(null);
        gen.tick(null);
        gen.tick(null);
        assertEquals(0.0, gen.pendingEu(), 1e-9);
        gen.restoreTankState(null, 0);
        assertNull(gen.currentFuel());
    }

    @Test
    void gasEnginesApplyDynamicConfigOverrides() {
        var wg = new WaterGasEngine();
        wg.setSolarEuPerTick(10);
        assertEquals(10, wg.solarEuPerTick());
        wg.tick(false, true, true, true, false, true);
        assertEquals(10.0, wg.storedEu(), 1e-9);

        var furnace = new GasFurnaceEngine();
        furnace.configure(25, 20);
        furnace.fillGas(GasFuelSpec.WATER_GAS, 1_000, false);
        GasMachineOutcome outcome = GasMachineOutcome.IDLE;
        for (int i = 0; i < 20; i++) {
            outcome = furnace.tick(true, true);
        }
        assertEquals(GasMachineOutcome.COMPLETED, outcome);
        assertEquals(975, furnace.gasMb());

        var fermenter = new OrganicFermenterEngine();
        fermenter.configure(40, 25);
        assertEquals(160, fermenter.biogasYield(4));
        fermenter.setStoredEu(fermenter.energyCapacity());
        fermenter.fillCulture(1_000, false);
        GasMachineOutcome fOut = GasMachineOutcome.IDLE;
        for (int i = 0; i < fermenter.operationTicks(); i++) {
            fOut = fermenter.tick(4, true);
        }
        assertEquals(GasMachineOutcome.COMPLETED, fOut);
        assertEquals(975, fermenter.cultureMb());
        assertEquals(160, fermenter.biogasMb());
        assertEquals(GasMachineOutcome.IDLE, fermenter.tick(0, true));
        assertFalse(fermenter.isActive());
    }
}
