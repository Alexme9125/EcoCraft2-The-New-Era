package com.ecocraft2.common.giant;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GiantDomainTest {

    // --- Spec & Extensible Registry ---

    @Test
    void builtInGiantSpecsAndExtensibleRegistry() {
        GiantMachineRegistry registry = new GiantMachineRegistry();
        assertEquals(5, registry.size());

        GiantMachineSpec macerator = registry.byId("macerator").orElseThrow();
        assertEquals(300, macerator.baseOperationTicks());
        assertEquals(2, macerator.baseEuPerTick());
        assertEquals(3, macerator.baseTier());
        assertEquals(32_000, macerator.baseEnergyCapacity());
        assertFalse(macerator.usesWater());

        GiantMachineSpec recycler = registry.byUnitBlockPath("giant_recycler_unit").orElseThrow();
        assertEquals(45, recycler.baseOperationTicks());
        assertEquals(1, recycler.baseEuPerTick());
        assertEquals(8, recycler.scrapChanceDenominator());
        assertTrue(recycler.isRecycler());

        GiantMachineSpec washer = registry.byId("ore_washer").orElseThrow();
        assertEquals(500, washer.baseOperationTicks());
        assertEquals(16, washer.baseEuPerTick());
        assertEquals(64_000, washer.waterTankCapacityMb());
        assertEquals(1_000, washer.waterPerOperationMb());
        assertEquals(216_000, washer.baseEnergyCapacity()); // 27 * 500 * 16 = 216,000
        assertTrue(washer.usesWater());

        // Addon expansion works cleanly without touching engine code
        GiantMachineSpec custom = new GiantMachineSpec(
                "induction_furnace",
                "giant_induction_furnace_unit",
                "mhera:induction_furnace",
                100,
                16,
                3,
                0,
                0,
                0
        );
        registry.register(custom);
        assertEquals(6, registry.size());
        assertEquals(custom, registry.byId("induction_furnace").orElseThrow());
        assertEquals(custom, registry.byUnitBlockPath("giant_induction_furnace_unit").orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> registry.register(custom));
    }

    // --- MultiblockGeometry ---

    @Test
    void multiblockGeometryFindsSameBoundsAndCoreFromAll27Blocks() {
        BlockPos3 expectedCore = new BlockPos3(10, 64, -20);
        MultiblockBounds expected = MultiblockGeometry.boundsFromCore(expectedCore);
        Set<BlockPos3> occupied = new HashSet<>(expected.allPositions());
        assertEquals(27, occupied.size());

        // Starting from ANY of the 27 positions finds the exact same bounds & bottom-center core
        for (BlockPos3 pos : occupied) {
            MultiblockBounds found = MultiblockGeometry.find3x3x3(pos, occupied::contains).orElseThrow();
            assertEquals(expected, found);
            assertEquals(expectedCore, found.core());
        }

        // Verify roles: 1 core, 4 lower corners, 22 shell
        int cores = 0, corners = 0, shells = 0;
        int exteriorFaces = 0;
        for (BlockPos3 pos : occupied) {
            switch (expected.roleOf(pos)) {
                case CORE -> cores++;
                case CORNER -> corners++;
                case SHELL -> shells++;
                case OUTSIDE -> throw new AssertionError("Should be inside");
            }
            for (BlockFace face : BlockFace.values()) {
                if (expected.isExteriorFace(pos, face)) {
                    exteriorFaces++;
                }
            }
        }
        assertEquals(1, cores);
        assertEquals(4, corners);
        assertEquals(22, shells);
        assertEquals(54, exteriorFaces); // 6 faces * 9 blocks per face = 54 exterior faces

        // Missing even a single block fails validation from all remaining positions
        BlockPos3 removed = expectedCore.offset(1, 2, -1);
        occupied.remove(removed);
        assertFalse(MultiblockGeometry.validate3x3x3(expectedCore, occupied::contains));
        for (BlockPos3 pos : occupied) {
            assertTrue(MultiblockGeometry.find3x3x3(pos, occupied::contains).isEmpty());
        }
    }

    @Test
    void multiblockBoundsFrontCenterMatchesMiddleFrontExteriorForAllHorizontals() {
        BlockPos3 core = new BlockPos3(10, 64, -20);
        MultiblockBounds bounds = MultiblockGeometry.boundsFromCore(core);

        for (BlockFace facing : BlockFace.horizontals()) {
            BlockPos3 front = bounds.frontCenter(facing);
            assertEquals(core.offset(facing).offset(BlockFace.UP), front);
            assertTrue(bounds.contains(front));
            assertTrue(bounds.isFrontCenter(front, facing));
            assertFalse(bounds.isFrontCenter(core, facing));
            assertEquals(MultiblockBounds.BlockRole.SHELL, bounds.roleOf(front));
            assertEquals(core.y() + 1, front.y());

            // Front-center block has exactly one exterior face, pointing outward in facing
            for (BlockFace face : BlockFace.values()) {
                assertEquals(face == facing, bounds.isExteriorFace(front, face));
            }
        }

        assertThrows(IllegalArgumentException.class, () -> bounds.frontCenter(BlockFace.UP));
        assertThrows(IllegalArgumentException.class, () -> bounds.frontCenter(BlockFace.DOWN));
    }

    // --- GiantCornerPort & 16-bit GiantPortPermissions ---

    @Test
    void eightCornerPortsFormBijectionForAllHorizontalFacings() {
        for (BlockFace facing : BlockFace.horizontals()) {
            Set<String> seenPairs = new HashSet<>();
            for (GiantCornerPort port : GiantCornerPort.values()) {
                BlockPos3 offset = port.cornerOffset(facing);
                BlockFace worldFace = port.worldFace(facing);

                assertEquals(0, offset.y());
                assertEquals(1, Math.abs(offset.x()));
                assertEquals(1, Math.abs(offset.z()));
                assertTrue(worldFace.isHorizontal());
                // World face must point outward from the 3x3x3 cube
                BlockPos3 outside = offset.offset(worldFace);
                assertTrue(Math.abs(outside.x()) == 2 || Math.abs(outside.z()) == 2);

                assertTrue(seenPairs.add(offset + ":" + worldFace), "Duplicate port mapping for facing " + facing);
                assertEquals(
                        port,
                        GiantCornerPort.fromOffsetAndFace(facing, offset.x(), offset.y(), offset.z(), worldFace)
                                .orElseThrow()
                );
            }
            assertEquals(8, seenPairs.size());

            // Non-corner, upper layers, vertical faces, and inward faces never map to a port
            assertTrue(GiantCornerPort.fromOffsetAndFace(facing, 0, 0, 0, facing).isEmpty());
            assertTrue(GiantCornerPort.fromOffsetAndFace(facing, 1, 1, 1, BlockFace.EAST).isEmpty());
            assertTrue(GiantCornerPort.fromOffsetAndFace(facing, 1, 0, 1, BlockFace.UP).isEmpty());
            assertTrue(GiantCornerPort.fromOffsetAndFace(facing, 1, 0, 1, BlockFace.DOWN).isEmpty());
            assertTrue(GiantCornerPort.fromOffsetAndFace(facing, 1, 0, 1, BlockFace.WEST).isEmpty()); // inward
        }
    }

    @Test
    void giantPortPermissions16BitPackingAndAccessTruthTable() {
        GiantPortPermissions perms = new GiantPortPermissions();
        assertEquals(0, perms.packed());
        for (GiantCornerPort port : GiantCornerPort.values()) {
            assertEquals(PortAccessMode.FULL, perms.get(port));
            assertTrue(perms.allowsInput(port));
            assertTrue(perms.allowsOutput(port));
            assertTrue(perms.connects(port));
        }

        // Configure distinct modes across all 8 ports and verify zero bit bleed
        perms.set(GiantCornerPort.FRONT_LEFT, PortAccessMode.INPUT);
        perms.set(GiantCornerPort.FRONT_RIGHT, PortAccessMode.OUTPUT);
        perms.set(GiantCornerPort.BACK_LEFT, PortAccessMode.BLOCKED);
        perms.set(GiantCornerPort.BACK_RIGHT, PortAccessMode.FULL);
        perms.set(GiantCornerPort.LEFT_FRONT, PortAccessMode.OUTPUT);
        perms.set(GiantCornerPort.LEFT_BACK, PortAccessMode.INPUT);
        perms.set(GiantCornerPort.RIGHT_FRONT, PortAccessMode.BLOCKED);
        perms.set(GiantCornerPort.RIGHT_BACK, PortAccessMode.FULL);

        GiantPortPermissions restored = new GiantPortPermissions(perms.packed());
        assertEquals(perms, restored);

        assertTrue(restored.allowsInput(GiantCornerPort.FRONT_LEFT));
        assertFalse(restored.allowsOutput(GiantCornerPort.FRONT_LEFT));
        assertTrue(restored.connects(GiantCornerPort.FRONT_LEFT));

        assertFalse(restored.allowsInput(GiantCornerPort.FRONT_RIGHT));
        assertTrue(restored.allowsOutput(GiantCornerPort.FRONT_RIGHT));
        assertTrue(restored.connects(GiantCornerPort.FRONT_RIGHT));

        assertFalse(restored.allowsInput(GiantCornerPort.BACK_LEFT));
        assertFalse(restored.allowsOutput(GiantCornerPort.BACK_LEFT));
        assertFalse(restored.connects(GiantCornerPort.BACK_LEFT));

        // Cycling loops FULL -> INPUT -> OUTPUT -> BLOCKED -> FULL
        assertEquals(PortAccessMode.INPUT, restored.cycle(GiantCornerPort.BACK_RIGHT));
        assertEquals(PortAccessMode.OUTPUT, restored.cycle(GiantCornerPort.BACK_RIGHT));
        assertEquals(PortAccessMode.BLOCKED, restored.cycle(GiantCornerPort.BACK_RIGHT));
        assertEquals(PortAccessMode.FULL, restored.cycle(GiantCornerPort.BACK_RIGHT));

        // World-coordinate helper denies non-port faces
        assertFalse(restored.connects(BlockFace.NORTH, 0, 2, 0, BlockFace.UP));
        // For NORTH facing, FRONT_LEFT is corner (-1, 0, -1) face NORTH -> INPUT
        assertTrue(restored.allowsInput(BlockFace.NORTH, -1, 0, -1, BlockFace.NORTH));
        assertFalse(restored.allowsOutput(BlockFace.NORTH, -1, 0, -1, BlockFace.NORTH));
    }

    // --- GiantMachineEngine (9x3 Concurrency, Partial Batches, Upgrades, Ore Washer, Recycler) ---

    @Test
    void giantMaceratorRunsFull9x3ConcurrencyAtExact54EuPerTick() {
        GiantMachineEngine engine = new GiantMachineEngine(GiantMachineKind.MACERATOR);
        assertEquals(3, engine.inputTier());
        assertEquals(512.0, engine.maxInputPacket());
        assertEquals(32_000.0, engine.energyCapacity());

        engine.injectEu(32_000.0);

        int[] inputs = {6, 6, 6, 6, 6, 6, 6, 6, 6};
        int[] completed = new int[9];
        GiantMachineEngine.LaneHandler handler = new GiantMachineEngine.LaneHandler() {
            @Override
            public int availableBatch(int lane, int maxBatch) {
                return Math.min(inputs[lane], maxBatch);
            }

            @Override
            public void completeOperations(int lane, int completedOps) {
                inputs[lane] -= completedOps;
                completed[lane] += completedOps;
            }
        };

        double totalEu = 0.0;
        int totalOps = 0;
        for (int t = 1; t <= 300; t++) {
            var summary = engine.tick(handler);
            assertEquals(9, summary.activeLanes());
            assertEquals(27, summary.activeTotalBatch());
            assertEquals(54.0, summary.euConsumed(), 1e-12);
            totalEu += summary.euConsumed();
            totalOps += summary.completedOps();
        }

        assertEquals(16_200.0, totalEu, 1e-9);
        assertEquals(27, totalOps);
        assertEquals(32_000.0 - 16_200.0, engine.storedEu(), 1e-9);
        for (int i = 0; i < 9; i++) {
            assertEquals(3, inputs[i]);
            assertEquals(3, completed[i]);
        }
    }

    @Test
    void giantMachineRunsPartial1And2OpBatchesIndependently() {
        GiantMachineEngine engine = new GiantMachineEngine(GiantMachineKind.COMPRESSOR);
        engine.injectEu(32_000.0);

        // Lane 0: 1 item, Lane 1: 2 items, Lane 2: 5 items, Lanes 3..8: empty
        int[] inputs = {1, 2, 5, 0, 0, 0, 0, 0, 0};
        int[] outputs = new int[9];
        GiantMachineEngine.LaneHandler handler = new GiantMachineEngine.LaneHandler() {
            @Override
            public int availableBatch(int lane, int maxBatch) {
                return Math.min(inputs[lane], maxBatch);
            }

            @Override
            public void completeOperations(int lane, int completedOps) {
                inputs[lane] -= completedOps;
                outputs[lane] += completedOps;
            }
        };

        // Tick 1: starts batches 1, 2, 3 -> total M = 6 -> 12 EU/t
        var first = engine.tick(handler);
        assertEquals(3, first.activeLanes());
        assertEquals(6, first.activeTotalBatch());
        assertEquals(12.0, first.euConsumed(), 1e-12);
        assertEquals(1, engine.laneBatchSize(0));
        assertEquals(2, engine.laneBatchSize(1));
        assertEquals(3, engine.laneBatchSize(2));

        // Finish remaining 299 ticks of first cycle
        for (int t = 2; t <= 300; t++) {
            engine.tick(handler);
        }
        assertEquals(1, outputs[0]);
        assertEquals(2, outputs[1]);
        assertEquals(3, outputs[2]);
        assertEquals(0, inputs[0]);
        assertEquals(0, inputs[1]);
        assertEquals(2, inputs[2]);

        // Next tick: only Lane 2 restarts with its remaining partial batch of 2!
        var next = engine.tick(handler);
        assertEquals(1, next.activeLanes());
        assertEquals(2, next.activeTotalBatch());
        assertEquals(4.0, next.euConsumed(), 1e-12);
    }

    @Test
    void giantMachineUpgradeScalingAndOreWasherExactWaterBudget() {
        GiantMachineEngine washer = new GiantMachineEngine(GiantMachineKind.ORE_WASHER);
        washer.setUpgrades(new GiantUpgradeProfile(2, 1, 3)); // 2 overclockers, 1 transformer, 3 storage

        assertEquals(4, washer.inputTier()); // HV(3) + 1 = EV(4)
        assertEquals(2_048.0, washer.maxInputPacket());
        assertEquals(216_000.0 + 30_000.0, washer.energyCapacity(), 1e-9);
        assertEquals(245, washer.operationTicks()); // round(500 * 0.49) = 245
        assertEquals(16.0 * 2.56, washer.upgradedEuPerTick(), 1e-12);

        washer.injectEu(washer.energyCapacity());
        // Supply only 4,000 mB water for two lanes each holding 3 crushed ore -> Lane 0 gets batch 3, Lane 1 gets batch 1!
        assertEquals(4_000, washer.fillWater(4_000, false));

        int[] inputs = {3, 3, 0, 0, 0, 0, 0, 0, 0};
        int[] completed = new int[9];
        GiantMachineEngine.LaneHandler handler = new GiantMachineEngine.LaneHandler() {
            @Override
            public int availableBatch(int lane, int maxBatch) {
                return Math.min(inputs[lane], maxBatch);
            }

            @Override
            public void completeOperations(int lane, int completedOps) {
                inputs[lane] -= completedOps;
                completed[lane] += completedOps;
            }
        };

        var tick1 = washer.tick(handler);
        assertEquals(2, tick1.activeLanes());
        assertEquals(4, tick1.activeTotalBatch());
        assertEquals(3, washer.laneBatchSize(0));
        assertEquals(1, washer.laneBatchSize(1));
        assertEquals(4 * 16.0 * 2.56, tick1.euConsumed(), 1e-9);

        // Complete the 245-tick cycle -> exact 4,000 mB water deducted
        int totalWaterSpent = tick1.waterConsumedMb();
        for (int t = 2; t <= 245; t++) {
            totalWaterSpent += washer.tick(handler).waterConsumedMb();
        }
        assertEquals(4_000, totalWaterSpent);
        assertEquals(0, washer.waterMb());
        assertEquals(3, completed[0]);
        assertEquals(1, completed[1]);
        assertEquals(0, inputs[0]);
        assertEquals(2, inputs[1]);

        // With 0 mB water left, remaining 2 items in Lane 1 wait cleanly without spending EU
        var dryTick = washer.tick(handler);
        assertEquals(0, dryTick.activeLanes());
        assertEquals(0.0, dryTick.euConsumed());
    }

    @Test
    void giantRecyclerScrapRollAndBrownoutBehavior() {
        GiantMachineEngine recycler = new GiantMachineEngine(GiantMachineKind.RECYCLER);
        int hits = 0;
        for (int i = 0; i < 8; i++) {
            int roll = i;
            if (recycler.rollRecyclerScrap(bound -> {
                assertEquals(8, bound);
                return roll;
            })) {
                hits++;
            }
        }
        assertEquals(1, hits);

        // Brownout: only 7 EU available when 3 lanes of batch 3 want 3 EU/t each -> 2 lanes advance (6 EU), 3rd waits
        recycler.injectEu(7.0);
        int[] inputs = {3, 3, 3, 0, 0, 0, 0, 0, 0};
        GiantMachineEngine.LaneHandler handler = new GiantMachineEngine.LaneHandler() {
            @Override
            public int availableBatch(int lane, int maxBatch) {
                return Math.min(inputs[lane], maxBatch);
            }

            @Override
            public void completeOperations(int lane, int completedOps) {
                inputs[lane] -= completedOps;
            }
        };

        var summary = recycler.tick(handler);
        assertEquals(2, summary.activeLanes());
        assertEquals(6, summary.activeTotalBatch());
        assertEquals(6.0, summary.euConsumed(), 1e-12);
        assertEquals(1.0, recycler.storedEu(), 1e-12);
        assertEquals(1, recycler.laneProgress(0));
        assertEquals(1, recycler.laneProgress(1));
        assertEquals(0, recycler.laneProgress(2));
    }

    // --- Auto-Spread Engine Flag & GiantInputDistributor ---

    @Test
    void giantMachineEngineAutoSpreadDefaultsOffAndTogglesCleanly() {
        GiantMachineEngine engine = new GiantMachineEngine(GiantMachineKind.MACERATOR);
        assertFalse(engine.isAutoSpreadInputs());
        assertTrue(engine.toggleAutoSpreadInputs());
        assertTrue(engine.isAutoSpreadInputs());
        assertFalse(engine.toggleAutoSpreadInputs());
        assertFalse(engine.isAutoSpreadInputs());
        engine.setAutoSpreadInputs(true);
        assertTrue(engine.isAutoSpreadInputs());
    }

    @Test
    void giantInputDistributorSingleItemEvenSpreadMinimalChurnAndIdempotency() {
        // 64 items in slot 0 -> [8, 7, 7, 7, 7, 7, 7, 7, 7]
        GiantInputDistributor.SlotInput[] stack64 = emptySlots(9);
        stack64[0] = new GiantInputDistributor.SlotInput(0, 64, 64, 1);
        var res64 = GiantInputDistributor.plan(stack64);
        assertTrue(res64.changed());
        assertEquals(8, res64.outputs()[0].count());
        for (int i = 1; i < 9; i++) {
            assertEquals(7, res64.outputs()[i].count());
        }
        assertFalse(GiantInputDistributor.plan(toInputs(res64.outputs(), 64, 1)).changed());

        // 10 items in slot 4 -> slot 4 keeps the 2, all other 8 slots get 1
        GiantInputDistributor.SlotInput[] stack10 = emptySlots(9);
        stack10[4] = new GiantInputDistributor.SlotInput(0, 10, 64, 1);
        var res10 = GiantInputDistributor.plan(stack10);
        assertTrue(res10.changed());
        assertEquals(2, res10.outputs()[4].count());
        for (int i = 0; i < 9; i++) {
            assertEquals(i == 4 ? 2 : 1, res10.outputs()[i].count());
        }

        // Already balanced [1, 1, 2, 1, 1, 1, 1, 1, 1] -> zero churn (changed == false)
        GiantInputDistributor.SlotInput[] balanced = new GiantInputDistributor.SlotInput[9];
        for (int i = 0; i < 9; i++) {
            balanced[i] = new GiantInputDistributor.SlotInput(0, i == 2 ? 2 : 1, 64, 1);
        }
        assertFalse(GiantInputDistributor.plan(balanced).changed());

        // Partial (< 9): slot 3 has 1, slot 7 has 2 -> keeps slots 3 & 7 intact, puts 3rd in slot 0
        GiantInputDistributor.SlotInput[] partial = emptySlots(9);
        partial[3] = new GiantInputDistributor.SlotInput(0, 1, 64, 1);
        partial[7] = new GiantInputDistributor.SlotInput(0, 2, 64, 1);
        var resPartial = GiantInputDistributor.plan(partial);
        assertTrue(resPartial.changed());
        assertEquals(1, resPartial.outputs()[0].count());
        assertEquals(1, resPartial.outputs()[3].count());
        assertEquals(1, resPartial.outputs()[7].count());
        assertEquals(3, totalCount(resPartial.outputs(), 0));
    }

    @Test
    void giantInputDistributorMixedItemsShareEmptySlotsProportionallyWithoutStealing() {
        // Equal counts: Iron 64 in slot 0, Copper 64 in slot 1, 7 empty -> Iron gets 5 slots, Copper gets 4
        GiantInputDistributor.SlotInput[] equalMix = emptySlots(9);
        equalMix[0] = new GiantInputDistributor.SlotInput(0, 64, 64, 1);
        equalMix[1] = new GiantInputDistributor.SlotInput(1, 64, 64, 1);
        var equalRes = GiantInputDistributor.plan(equalMix);
        assertTrue(equalRes.changed());
        assertEquals(5, countSlotsOfGroup(equalRes.outputs(), 0));
        assertEquals(4, countSlotsOfGroup(equalRes.outputs(), 1));
        assertEquals(64, totalCount(equalRes.outputs(), 0));
        assertEquals(64, totalCount(equalRes.outputs(), 1));

        // Weighted counts: Iron 64 in slot 0, Copper 8 in slot 1 -> Iron gets 8 slots (8 each), Copper gets 1 slot (8)
        GiantInputDistributor.SlotInput[] weightedMix = emptySlots(9);
        weightedMix[0] = new GiantInputDistributor.SlotInput(0, 64, 64, 1);
        weightedMix[1] = new GiantInputDistributor.SlotInput(1, 8, 64, 1);
        var weightedRes = GiantInputDistributor.plan(weightedMix);
        assertTrue(weightedRes.changed());
        assertEquals(8, countSlotsOfGroup(weightedRes.outputs(), 0));
        assertEquals(1, countSlotsOfGroup(weightedRes.outputs(), 1));
        assertEquals(8, weightedRes.outputs()[1].count());
        assertEquals(1, weightedRes.outputs()[1].groupKey());

        // Zero stealing when all 9 slots are occupied: Iron owns 0..4, Copper owns 5..8
        GiantInputDistributor.SlotInput[] fullMix = new GiantInputDistributor.SlotInput[9];
        fullMix[0] = new GiantInputDistributor.SlotInput(0, 20, 64, 1);
        fullMix[1] = new GiantInputDistributor.SlotInput(0, 5, 64, 1);
        fullMix[2] = new GiantInputDistributor.SlotInput(0, 5, 64, 1);
        fullMix[3] = new GiantInputDistributor.SlotInput(0, 5, 64, 1);
        fullMix[4] = new GiantInputDistributor.SlotInput(0, 5, 64, 1);
        fullMix[5] = new GiantInputDistributor.SlotInput(1, 30, 64, 1);
        fullMix[6] = new GiantInputDistributor.SlotInput(1, 2, 64, 1);
        fullMix[7] = new GiantInputDistributor.SlotInput(1, 2, 64, 1);
        fullMix[8] = new GiantInputDistributor.SlotInput(1, 2, 64, 1);
        var fullRes = GiantInputDistributor.plan(fullMix);
        assertTrue(fullRes.changed());
        for (int i = 0; i < 5; i++) {
            assertEquals(0, fullRes.outputs()[i].groupKey());
            assertEquals(8, fullRes.outputs()[i].count());
        }
        for (int i = 5; i < 9; i++) {
            assertEquals(1, fullRes.outputs()[i].groupKey());
            assertEquals(9, fullRes.outputs()[i].count());
        }
    }

    @Test
    void giantInputDistributorMultiInputRecipeAlignmentPreventsDeadlock() {
        // 9 items of a 4-input recipe (cap 64) -> [5, 4] across 2 slots (NOT 1x9!)
        GiantInputDistributor.SlotInput[] nineOfFour = emptySlots(9);
        nineOfFour[0] = new GiantInputDistributor.SlotInput(0, 9, 64, 4);
        var res94 = GiantInputDistributor.plan(nineOfFour);
        assertTrue(res94.changed());
        assertEquals(5, res94.outputs()[0].count());
        assertEquals(4, res94.outputs()[1].count());
        assertEquals(2, countSlotsOfGroup(res94.outputs(), 0));

        // 9 items of a 4-input recipe with cap=4 -> [4, 4, 1] across 3 slots (never exceeds cap, 2 lanes run!)
        GiantInputDistributor.SlotInput[] cap4 = emptySlots(9);
        cap4[0] = new GiantInputDistributor.SlotInput(0, 4, 4, 4);
        cap4[1] = new GiantInputDistributor.SlotInput(0, 4, 4, 4);
        cap4[2] = new GiantInputDistributor.SlotInput(0, 1, 4, 4);
        var resCap4 = GiantInputDistributor.plan(cap4);
        assertFalse(resCap4.changed());

        GiantInputDistributor.SlotInput[] cap4Unbalanced = emptySlots(9);
        cap4Unbalanced[0] = new GiantInputDistributor.SlotInput(0, 3, 4, 4);
        cap4Unbalanced[1] = new GiantInputDistributor.SlotInput(0, 3, 4, 4);
        cap4Unbalanced[2] = new GiantInputDistributor.SlotInput(0, 3, 4, 4);
        var resCap4Fix = GiantInputDistributor.plan(cap4Unbalanced);
        assertTrue(resCap4Fix.changed());
        assertEquals(4, resCap4Fix.outputs()[0].count());
        assertEquals(4, resCap4Fix.outputs()[1].count());
        assertEquals(1, resCap4Fix.outputs()[2].count());

        // 3 items of a 4-input recipe split [1, 1, 1] -> consolidates to [3] on 1 slot
        GiantInputDistributor.SlotInput[] scraps = emptySlots(9);
        scraps[1] = new GiantInputDistributor.SlotInput(0, 1, 64, 4);
        scraps[4] = new GiantInputDistributor.SlotInput(0, 1, 64, 4);
        scraps[7] = new GiantInputDistributor.SlotInput(0, 1, 64, 4);
        var resScraps = GiantInputDistributor.plan(scraps);
        assertTrue(resScraps.changed());
        assertEquals(1, countSlotsOfGroup(resScraps.outputs(), 0));
        assertEquals(3, totalCount(resScraps.outputs(), 0));
        assertEquals(3, resScraps.outputs()[1].count());

        // 38 items of a 4-input recipe -> all 9 slots >= 4: [5, 5, 4, 4, 4, 4, 4, 4, 4]
        GiantInputDistributor.SlotInput[] bulk38 = emptySlots(9);
        bulk38[0] = new GiantInputDistributor.SlotInput(0, 38, 64, 4);
        var res38 = GiantInputDistributor.plan(bulk38);
        assertTrue(res38.changed());
        assertEquals(9, countSlotsOfGroup(res38.outputs(), 0));
        for (int i = 0; i < 9; i++) {
            assertTrue(res38.outputs()[i].count() >= 4);
        }
        assertEquals(38, totalCount(res38.outputs(), 0));

        // minUnit > cap (e.g. minUnit=20, cap=16, total=35) -> safely clamped to effectiveUnit=16, no overflow
        GiantInputDistributor.SlotInput[] unitOverCap = emptySlots(9);
        unitOverCap[0] = new GiantInputDistributor.SlotInput(0, 12, 16, 20);
        unitOverCap[1] = new GiantInputDistributor.SlotInput(0, 12, 16, 20);
        unitOverCap[2] = new GiantInputDistributor.SlotInput(0, 11, 16, 20);
        var resOver = GiantInputDistributor.plan(unitOverCap);
        assertEquals(35, totalCount(resOver.outputs(), 0));
        for (var out : resOver.outputs()) {
            assertTrue(out.count() <= 16);
        }
    }

    @Test
    void giantInputDistributorHandlesUnstackablesLowCapsAndFrozenOversizedStacks() {
        // Unstackables (cap=1): 4 distinct/identical unstackable items stay count 1 each, never merged
        GiantInputDistributor.SlotInput[] unstack = emptySlots(9);
        unstack[0] = new GiantInputDistributor.SlotInput(0, 1, 1, 1);
        unstack[2] = new GiantInputDistributor.SlotInput(0, 1, 1, 1);
        unstack[5] = new GiantInputDistributor.SlotInput(0, 1, 1, 1);
        unstack[8] = new GiantInputDistributor.SlotInput(0, 1, 1, 1);
        var resUnstack = GiantInputDistributor.plan(unstack);
        assertFalse(resUnstack.changed());

        // Low-cap (cap=16): 20 items -> spreads across all 9 slots without exceeding 16
        GiantInputDistributor.SlotInput[] cap16 = emptySlots(9);
        cap16[0] = new GiantInputDistributor.SlotInput(0, 16, 16, 1);
        cap16[1] = new GiantInputDistributor.SlotInput(0, 4, 16, 1);
        var res16 = GiantInputDistributor.plan(cap16);
        assertTrue(res16.changed());
        assertEquals(20, totalCount(res16.outputs(), 0));
        assertEquals(9, countSlotsOfGroup(res16.outputs(), 0));
        for (var out : res16.outputs()) {
            assertTrue(out.count() >= 2 && out.count() <= 3);
        }

        // Oversized stack (count=100 > cap=64) in slot 5 stays frozen untouched while slot 0 spreads across other 8 slots
        GiantInputDistributor.SlotInput[] oversized = emptySlots(9);
        oversized[0] = new GiantInputDistributor.SlotInput(0, 64, 64, 1);
        oversized[5] = new GiantInputDistributor.SlotInput(0, 100, 64, 1);
        var resOversized = GiantInputDistributor.plan(oversized);
        assertTrue(resOversized.changed());
        assertEquals(100, resOversized.outputs()[5].count());
        assertEquals(0, resOversized.outputs()[5].groupKey());
        assertEquals(164, totalCount(resOversized.outputs(), 0));
        for (int i = 0; i < 9; i++) {
            if (i != 5) {
                assertEquals(8, resOversized.outputs()[i].count());
            }
        }
        // Re-planning with same oversized slot is idempotent
        GiantInputDistributor.SlotInput[] secondPass = new GiantInputDistributor.SlotInput[9];
        for (int i = 0; i < 9; i++) {
            secondPass[i] = new GiantInputDistributor.SlotInput(0, resOversized.outputs()[i].count(), 64, 1);
        }
        assertFalse(GiantInputDistributor.plan(secondPass).changed());

        // Pathological synthetic mismatched caps on same groupKey (Integer.MAX_VALUE + 1 with cap=1):
        // must never overflow int narrowing or lose items; safely aborts unchanged.
        GiantInputDistributor.SlotInput[] mismatchedCaps = emptySlots(9);
        mismatchedCaps[0] = new GiantInputDistributor.SlotInput(0, Integer.MAX_VALUE, Integer.MAX_VALUE, 1);
        mismatchedCaps[1] = new GiantInputDistributor.SlotInput(0, 1, 1, 1);
        var resMismatch = GiantInputDistributor.plan(mismatchedCaps);
        assertFalse(resMismatch.changed());
        assertEquals(Integer.MAX_VALUE, resMismatch.outputs()[0].count());
        assertEquals(1, resMismatch.outputs()[1].count());
    }

    @Test
    void giantInputDistributorFuzz12000RandomCasesGuaranteesConservationBoundsAndIdempotency() {
        Random rng = new Random(0xECC2_9513L);
        int[] capChoices = {1, 4, 8, 16, 32, 64};
        int[] unitChoices = {1, 1, 1, 2, 3, 4, 8, 9, 16, 70};

        for (int trial = 0; trial < 12_000; trial++) {
            int distinctGroups = rng.nextInt(6); // 0..5 item types
            int[] groupCaps = new int[Math.max(1, distinctGroups)];
            int[] groupUnits = new int[Math.max(1, distinctGroups)];
            for (int g = 0; g < distinctGroups; g++) {
                groupCaps[g] = capChoices[rng.nextInt(capChoices.length)];
                groupUnits[g] = unitChoices[rng.nextInt(unitChoices.length)];
            }

            GiantInputDistributor.SlotInput[] inputs = new GiantInputDistributor.SlotInput[9];
            Map<Integer, Long> beforeSums = new HashMap<>();
            boolean[] frozenExpected = new boolean[9];
            for (int i = 0; i < 9; i++) {
                if (distinctGroups == 0 || rng.nextInt(100) < 35) {
                    inputs[i] = GiantInputDistributor.SlotInput.empty();
                    continue;
                }
                int g = rng.nextInt(distinctGroups);
                int cap = groupCaps[g];
                int unit = groupUnits[g];
                int count;
                if (rng.nextInt(100) < 4) {
                    // Occasional abnormal oversized stack
                    count = cap + 1 + rng.nextInt(50);
                    frozenExpected[i] = true;
                } else {
                    count = 1 + rng.nextInt(cap);
                }
                inputs[i] = new GiantInputDistributor.SlotInput(g, count, cap, unit);
                beforeSums.merge(g, (long) count, Long::sum);
            }

            var plan1 = GiantInputDistributor.plan(inputs);
            assertEquals(9, plan1.outputs().length);

            Map<Integer, Long> afterSums = new HashMap<>();
            for (int i = 0; i < 9; i++) {
                var out = plan1.outputs()[i];
                if (frozenExpected[i]) {
                    assertEquals(inputs[i].groupKey(), out.groupKey(), "Frozen group altered on trial " + trial);
                    assertEquals(inputs[i].count(), out.count(), "Frozen count altered on trial " + trial);
                }
                if (out.isEmpty()) {
                    assertEquals(GiantInputDistributor.EMPTY_GROUP, out.groupKey());
                    assertEquals(0, out.count());
                } else {
                    int g = out.groupKey();
                    assertTrue(g >= 0 && g < distinctGroups);
                    if (!frozenExpected[i]) {
                        assertTrue(out.count() >= 1 && out.count() <= groupCaps[g],
                                "Count out of bounds on trial " + trial + ": " + out.count() + " > " + groupCaps[g]);
                    }
                    afterSums.merge(g, (long) out.count(), Long::sum);
                }
            }
            assertEquals(beforeSums, afterSums, "Item conservation violated on trial " + trial);

            // Single-pass idempotency check: feeding plan1 outputs back into plan() must yield changed == false
            GiantInputDistributor.SlotInput[] secondInputs = new GiantInputDistributor.SlotInput[9];
            for (int i = 0; i < 9; i++) {
                var out = plan1.outputs()[i];
                if (out.isEmpty()) {
                    secondInputs[i] = GiantInputDistributor.SlotInput.empty();
                } else {
                    int g = out.groupKey();
                    secondInputs[i] = new GiantInputDistributor.SlotInput(g, out.count(), groupCaps[g], groupUnits[g]);
                }
            }
            var plan2 = GiantInputDistributor.plan(secondInputs);
            assertFalse(plan2.changed(), "Distributor was not idempotent on trial " + trial);
        }
    }

    private static GiantInputDistributor.SlotInput[] emptySlots(int n) {
        GiantInputDistributor.SlotInput[] arr = new GiantInputDistributor.SlotInput[n];
        for (int i = 0; i < n; i++) {
            arr[i] = GiantInputDistributor.SlotInput.empty();
        }
        return arr;
    }

    private static GiantInputDistributor.SlotInput[] toInputs(
            GiantInputDistributor.SlotOutput[] outputs, int cap, int unit) {
        GiantInputDistributor.SlotInput[] arr = new GiantInputDistributor.SlotInput[outputs.length];
        for (int i = 0; i < outputs.length; i++) {
            arr[i] =
                    outputs[i].isEmpty()
                            ? GiantInputDistributor.SlotInput.empty()
                            : new GiantInputDistributor.SlotInput(outputs[i].groupKey(), outputs[i].count(), cap, unit);
        }
        return arr;
    }

    private static int totalCount(GiantInputDistributor.SlotOutput[] outputs, int groupKey) {
        int sum = 0;
        for (var out : outputs) {
            if (!out.isEmpty() && out.groupKey() == groupKey) {
                sum += out.count();
            }
        }
        return sum;
    }

    private static int countSlotsOfGroup(GiantInputDistributor.SlotOutput[] outputs, int groupKey) {
        int c = 0;
        for (var out : outputs) {
            if (!out.isEmpty() && out.groupKey() == groupKey) {
                c++;
            }
        }
        return c;
    }
}
