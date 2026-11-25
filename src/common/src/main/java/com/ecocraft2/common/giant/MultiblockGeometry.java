package com.ecocraft2.common.giant;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Pure coordinate geometry solver for 3x3x3 Giant Machine multiblocks.
 */
public final class MultiblockGeometry {

    private MultiblockGeometry() {
    }

    /**
     * Constructs a {@link MultiblockBounds} directly from its bottom-center core coordinate.
     *
     * @param core bottom-center core coordinate
     * @return 3x3x3 multiblock bounds
     */
    public static MultiblockBounds boundsFromCore(BlockPos3 core) {
        Objects.requireNonNull(core, "core");
        return new MultiblockBounds(
                new BlockPos3(core.x() - 1, core.y(), core.z() - 1),
                new BlockPos3(core.x() + 1, core.y() + 2, core.z() + 1),
                core
        );
    }

    /**
     * Validates that all 27 positions of the 3x3x3 structure centered on bottom-center {@code core}
     * satisfy {@code isMatchingUnit}.
     *
     * @param core           bottom-center core coordinate
     * @param isMatchingUnit occupancy predicate returning {@code true} for eligible unformed/matching units
     * @return {@code true} iff all 27 positions match
     */
    public static boolean validate3x3x3(BlockPos3 core, Predicate<BlockPos3> isMatchingUnit) {
        Objects.requireNonNull(core, "core");
        Objects.requireNonNull(isMatchingUnit, "isMatchingUnit");
        for (int dy = 0; dy <= 2; dy++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (!isMatchingUnit.test(core.offset(dx, dy, dz))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Given any coordinate {@code origin} and an occupancy predicate, locates a valid 3x3x3 cube
     * containing {@code origin} where all 27 blocks satisfy {@code isMatchingUnit}, returning its
     * exact bounding box and bottom-center core coordinate.
     *
     * @param origin         any block belonging to the candidate structure
     * @param isMatchingUnit predicate returning {@code true} for matching unit blocks
     * @return {@link MultiblockBounds} if a complete 3x3x3 cube containing {@code origin} exists
     */
    public static Optional<MultiblockBounds> find3x3x3(BlockPos3 origin, Predicate<BlockPos3> isMatchingUnit) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(isMatchingUnit, "isMatchingUnit");
        if (!isMatchingUnit.test(origin)) {
            return Optional.empty();
        }

        // Test all 27 candidate minimum corners that include origin.
        for (int oy = -2; oy <= 0; oy++) {
            int minY = origin.y() + oy;
            for (int oz = -2; oz <= 0; oz++) {
                int minZ = origin.z() + oz;
                for (int ox = -2; ox <= 0; ox++) {
                    int minX = origin.x() + ox;
                    BlockPos3 candidateCore = new BlockPos3(minX + 1, minY, minZ + 1);
                    if (validate3x3x3(candidateCore, isMatchingUnit)) {
                        return Optional.of(boundsFromCore(candidateCore));
                    }
                }
            }
        }
        return Optional.empty();
    }
}
