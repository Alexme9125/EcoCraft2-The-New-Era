package com.ecocraft2.kinetic;

import com.ecocraft2.common.kinetic.RotorSpec;
import java.util.ArrayDeque;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Clean-room wind and water clearance, natural-water connectivity, and base KU evaluation matching
 * IC2NE's wind height/variation and river/ocean water flow semantics.
 */
public final class AdvancedKineticPhysics {

    /** Operating diagnostic status reported to GUIs, Jade, and TOP. */
    public enum Status {
        NO_ROTOR,
        OBSTRUCTED,
        NO_WATER,
        RUNNING,
        OVERWIND
    }

    /** Evaluated kinetic reading before machine-tier scaling. */
    public record Sample(Status status, int environmentReading, double baseKu, boolean overWind) {
        public static Sample inactive(Status status) {
            return new Sample(status, 0, 0.0, false);
        }
    }

    private static final int MAX_WATER_BFS_NODES = 512;
    private static final int MIN_NATURAL_WATER_BLOCKS = 24;

    private AdvancedKineticPhysics() {}

    /** Evaluates wind clearance, altitude/variation wind strength (MCW), and base KU. */
    public static Sample sampleWind(
            ServerLevel level, BlockPos machinePos, Direction rotorFace, RotorSpec rotor) {
        if (rotor == null) {
            return Sample.inactive(Status.NO_ROTOR);
        }
        if (!rotorFace.getAxis().isHorizontal()) {
            return Sample.inactive(Status.OBSTRUCTED);
        }
        BlockPos hub = machinePos.relative(rotorFace);
        if (!isSweepClear(level, hub, rotorFace, rotor.windDiameter(), false)) {
            return Sample.inactive(Status.OBSTRUCTED);
        }

        // Exact IC2NE wind altitude + periodic variation + weather multiplier formula (MCW)
        long sampled = level.getGameTime() / 200L;
        int variation =
                10 * Math.floorMod(sampled + machinePos.getX() + machinePos.getZ(), 4) - 10;
        int altitude = Math.max(0, Math.min(200, hub.getY() - 64));
        double weatherMultiplier =
                level.isThundering() ? 1.5 : (level.isRaining() ? 1.2 : 1.0);
        int windMcw = Math.max(0, (int) Math.round((altitude + variation) * weatherMultiplier));

        double baseKu = Math.max(0.0, (windMcw * (double) rotor.rating()) / 100.0);
        boolean overWind = windMcw > rotor.maxSafeWind();
        return new Sample(
                overWind ? Status.OVERWIND : Status.RUNNING, windMcw, baseKu, overWind);
    }

    /** Evaluates water wheel clearance, immersion, natural river/ocean connectivity, and base KU. */
    public static Sample sampleWater(
            ServerLevel level,
            BlockPos machinePos,
            Direction rotorFace,
            RotorSpec rotor,
            boolean naturalWaterConnected) {
        if (rotor == null || !rotor.waterAllowed()) {
            return Sample.inactive(Status.NO_ROTOR);
        }
        BlockPos hub = machinePos.relative(rotorFace);
        if (!isSweepClear(level, hub, rotorFace, rotor.waterDiameter(), true)) {
            return Sample.inactive(Status.OBSTRUCTED);
        }
        if (!naturalWaterConnected) {
            return Sample.inactive(Status.NO_WATER);
        }

        // Biome-aware river/ocean current modulation (deep ocean 100..120, ocean 90..110, river 80..100)
        var biome = level.getBiome(hub);
        int biomeBase =
                biome.is(BiomeTags.IS_DEEP_OCEAN)
                        ? 100
                        : (biome.is(BiomeTags.IS_OCEAN) ? 90 : 80);
        long phase = level.getGameTime() / 200L;
        int flow =
                biomeBase + 10 * Math.floorMod(phase + machinePos.getX() + machinePos.getZ(), 3);
        double baseKu = Math.max(0.0, (flow * (double) rotor.rating()) / 100.0);
        return new Sample(Status.RUNNING, flow, baseKu, false);
    }

    /**
     * Verifies whether the water at {@code hub} belongs to a natural river, ocean, or deep ocean
     * water body of sufficient extent.
     */
    public static boolean checkNaturalWaterConnectivity(
            ServerLevel level, BlockPos machinePos, Direction rotorFace) {
        BlockPos hub = machinePos.relative(rotorFace);
        if (!isWaterBlock(level, hub)) {
            return false;
        }
        var queue = new ArrayDeque<BlockPos>();
        var visited = new HashSet<Long>();
        queue.add(hub);
        visited.add(hub.asLong());

        int naturalWaterCount = 0;
        while (!queue.isEmpty() && visited.size() < MAX_WATER_BFS_NODES) {
            BlockPos cur = queue.poll();
            var biome = level.getBiome(cur);
            if (biome.is(BiomeTags.IS_RIVER)
                    || biome.is(BiomeTags.IS_OCEAN)
                    || biome.is(BiomeTags.IS_DEEP_OCEAN)) {
                naturalWaterCount++;
                if (naturalWaterCount >= MIN_NATURAL_WATER_BLOCKS) {
                    return true;
                }
            }
            for (Direction dir : Direction.values()) {
                BlockPos next = cur.relative(dir);
                if (Math.abs(next.getY() - hub.getY()) > 6) {
                    continue;
                }
                if (visited.add(next.asLong()) && isWaterBlock(level, next)) {
                    queue.add(next);
                }
            }
        }
        return naturalWaterCount >= MIN_NATURAL_WATER_BLOCKS;
    }

    /**
     * Checks that every block swept by a rotor of {@code diameter} blocks perpendicular to
     * {@code rotorFace} has no solid obstruction (and, for water, requires immersion).
     */
    public static boolean isSweepClear(
            ServerLevel level,
            BlockPos hub,
            Direction rotorFace,
            int diameter,
            boolean waterMode) {
        int radius = Math.max(1, diameter / 2);
        Direction axisU;
        Direction axisV;
        switch (rotorFace.getAxis()) {
            case X -> {
                axisU = Direction.UP;
                axisV = Direction.SOUTH;
            }
            case Y -> {
                axisU = Direction.EAST;
                axisV = Direction.SOUTH;
            }
            default -> {
                axisU = Direction.UP;
                axisV = Direction.EAST;
            }
        }

        int totalCells = 0;
        int waterCells = 0;
        for (int u = -radius; u <= radius; u++) {
            for (int v = -radius; v <= radius; v++) {
                // Circular disk check matches swept rotor blades cleanly
                if (u * u + v * v > radius * radius + 1) {
                    continue;
                }
                BlockPos check = hub.relative(axisU, u).relative(axisV, v);
                if (!level.hasChunkAt(check)) {
                    return false;
                }
                BlockState state = level.getBlockState(check);
                if (!state.getCollisionShape(level, check).isEmpty()) {
                    return false;
                }
                totalCells++;
                if (waterMode && state.getFluidState().is(FluidTags.WATER)) {
                    waterCells++;
                }
            }
        }
        if (waterMode) {
            return totalCells > 0 && isWaterBlock(level, hub) && waterCells * 2 >= totalCells;
        }
        return totalCells > 0;
    }

    private static boolean isWaterBlock(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.getFluidState(pos).is(FluidTags.WATER);
    }
}
