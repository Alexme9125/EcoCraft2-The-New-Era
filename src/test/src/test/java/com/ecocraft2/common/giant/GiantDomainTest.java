package com.ecocraft2.common.giant;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
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
                "ic2:induction_furnace",
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
}
