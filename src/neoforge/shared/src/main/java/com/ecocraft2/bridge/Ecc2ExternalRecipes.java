package com.ecocraft2.bridge;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * Thread-safe static registry populated during EcoCraft 2 initialization and read by IC2NE 1.0.6
 * Mixins (compressor, metal former extruding, canner bottle recipes, and named fluid cells).
 */
public final class Ecc2ExternalRecipes {

    /** Single-input / single-output recipe entry for IC2 compressor or metal former extruder. */
    public record ProcessingEntry(String inputId, int inputCount, String outputId, int outputCount) {
        public ProcessingEntry {
            Objects.requireNonNull(inputId, "inputId");
            Objects.requireNonNull(outputId, "outputId");
            if (inputId.isBlank() || outputId.isBlank() || inputCount <= 0 || outputCount <= 0) {
                throw new IllegalArgumentException(
                        "Invalid processing recipe: " + inputId + " x" + inputCount + " -> " + outputId + " x" + outputCount);
            }
        }
    }

    /** Two-input canning recipe entry for IC2 canner (mode 0), solid canner, and classic canner. */
    public record CanningBottleEntry(
            String containerId,
            int containerCount,
            String ingredientId,
            int ingredientCount,
            String outputId,
            int outputCount) {
        public CanningBottleEntry {
            Objects.requireNonNull(containerId, "containerId");
            Objects.requireNonNull(ingredientId, "ingredientId");
            Objects.requireNonNull(outputId, "outputId");
            if (containerId.isBlank()
                    || ingredientId.isBlank()
                    || outputId.isBlank()
                    || containerCount <= 0
                    || ingredientCount <= 0
                    || outputCount <= 0) {
                throw new IllegalArgumentException("Invalid canning bottle recipe");
            }
        }
    }

    /** Mapping between a full 1000 mB named EcoCraft 2 cell item and its fluid identifier. */
    public record NamedCellEntry(boolean basicFamily, String cellItemId, String fluidId) {
        public NamedCellEntry {
            Objects.requireNonNull(cellItemId, "cellItemId");
            Objects.requireNonNull(fluidId, "fluidId");
            if (cellItemId.isBlank() || fluidId.isBlank()) {
                throw new IllegalArgumentException("Invalid named cell entry");
            }
        }
    }

    private static final AtomicLong REVISION = new AtomicLong(1L);

    private static final CopyOnWriteArrayList<ProcessingEntry> COMPRESSOR =
            new CopyOnWriteArrayList<>();
    private static final Map<String, ProcessingEntry> COMPRESSOR_BY_INPUT =
            new ConcurrentHashMap<>();

    private static final CopyOnWriteArrayList<ProcessingEntry> EXTRUDER =
            new CopyOnWriteArrayList<>();

    private static final CopyOnWriteArrayList<CanningBottleEntry> CANNING_BOTTLE =
            new CopyOnWriteArrayList<>();

    private static final CopyOnWriteArrayList<NamedCellEntry> NAMED_CELLS =
            new CopyOnWriteArrayList<>();
    private static final Map<String, NamedCellEntry> CELL_BY_ITEM_ID = new ConcurrentHashMap<>();
    private static final Map<String, String> BASIC_CELL_BY_FLUID = new ConcurrentHashMap<>();
    private static final Map<String, String> UNIVERSAL_CELL_BY_FLUID = new ConcurrentHashMap<>();

    private Ecc2ExternalRecipes() {}

    /** Registers a recipe into IC2NE's Compressor ({@code ic2:compressor}). */
    public static void registerCompressor(
            String inputId, int inputCount, String outputId, int outputCount) {
        var entry = new ProcessingEntry(inputId, inputCount, outputId, outputCount);
        if (!COMPRESSOR.contains(entry)) {
            COMPRESSOR.add(entry);
            if (!inputId.startsWith("#")) {
                COMPRESSOR_BY_INPUT.put(inputId, entry);
            }
            REVISION.incrementAndGet();
        }
    }

    /** Registers a recipe into IC2NE's Metal Former extruding mode ({@code metal_former_extruding}). */
    public static void registerExtruder(
            String inputId, int inputCount, String outputId, int outputCount) {
        var entry = new ProcessingEntry(inputId, inputCount, outputId, outputCount);
        if (!EXTRUDER.contains(entry)) {
            EXTRUDER.add(entry);
            REVISION.incrementAndGet();
        }
    }

    /**
     * Registers a bottling recipe into IC2NE's Canner (mode 0), Solid Canner, and Classic Canner
     * ({@code canner_bottle}).
     */
    public static void registerCanningBottle(
            String containerId,
            int containerCount,
            String ingredientId,
            int ingredientCount,
            String outputId,
            int outputCount) {
        var entry =
                new CanningBottleEntry(
                        containerId,
                        containerCount,
                        ingredientId,
                        ingredientCount,
                        outputId,
                        outputCount);
        if (!CANNING_BOTTLE.contains(entry)) {
            CANNING_BOTTLE.add(entry);
            REVISION.incrementAndGet();
        }
    }

    /**
     * Registers a named 1000 mB fluid cell item in IC2NE's {@code CellFamily} and
     * {@code TransportPlatform}.
     *
     * @param basicFamily {@code true} for {@code ic2:empty_cell} (BASIC), {@code false} for
     *     {@code ic2:fluid_cell} (UNIVERSAL)
     * @param cellItemId full namespaced item ID (e.g. {@code "ecera:water_gas_cell"})
     * @param fluidId full namespaced fluid ID (e.g. {@code "ecera:water_gas"})
     */
    public static void registerNamedCell(boolean basicFamily, String cellItemId, String fluidId) {
        var entry = new NamedCellEntry(basicFamily, cellItemId, fluidId);
        if (!NAMED_CELLS.contains(entry)) {
            NAMED_CELLS.add(entry);
            CELL_BY_ITEM_ID.put(cellItemId, entry);
            if (basicFamily) {
                BASIC_CELL_BY_FLUID.put(fluidId, cellItemId);
            } else {
                UNIVERSAL_CELL_BY_FLUID.put(fluidId, cellItemId);
            }
            REVISION.incrementAndGet();
        }
    }

    /** Monotonic revision counter bumped on every registration for Mixin cache invalidation. */
    public static long revision() {
        return REVISION.get();
    }

    /** Returns an unmodifiable snapshot of registered compressor recipes. */
    public static List<ProcessingEntry> compressorRecipes() {
        return Collections.unmodifiableList(COMPRESSOR);
    }

    /** Returns an unmodifiable snapshot of registered metal-former extruding recipes. */
    public static List<ProcessingEntry> extruderRecipes() {
        return Collections.unmodifiableList(EXTRUDER);
    }

    /** Returns an unmodifiable snapshot of registered canner bottle recipes. */
    public static List<CanningBottleEntry> canningBottleRecipes() {
        return Collections.unmodifiableList(CANNING_BOTTLE);
    }

    /** Returns an unmodifiable snapshot of registered named fluid cells. */
    public static List<NamedCellEntry> namedCells() {
        return Collections.unmodifiableList(NAMED_CELLS);
    }

    /** Looks up a registered processing recipe for {@code machine} (currently {@code "compressor"}). */
    public static ProcessingEntry findProcessing(
            String machine, String inputId, Predicate<String> matchesTag) {
        if (!"compressor".equals(machine) || inputId == null || inputId.isEmpty()) {
            return null;
        }
        var direct = COMPRESSOR_BY_INPUT.get(inputId);
        if (direct != null) {
            return direct;
        }
        if (matchesTag != null) {
            for (var entry : COMPRESSOR) {
                if (entry.inputId().startsWith("#")
                        && matchesTag.test(entry.inputId().substring(1))) {
                    return entry;
                }
            }
        }
        return null;
    }

    /** Returns {@code true} if {@code itemId} is a registered external named cell item. */
    public static boolean isNamedCell(String itemId) {
        return itemId != null && CELL_BY_ITEM_ID.containsKey(itemId);
    }

    /**
     * Returns {@code Boolean.TRUE} if {@code itemId} belongs to the BASIC cell family,
     * {@code Boolean.FALSE} if it belongs to UNIVERSAL, or {@code null} if not registered.
     */
    public static Boolean isBasicNamedCell(String itemId) {
        if (itemId == null) {
            return null;
        }
        var entry = CELL_BY_ITEM_ID.get(itemId);
        return entry == null ? null : entry.basicFamily();
    }

    /** Returns {@code true} if {@code ic2:empty_cell} (BASIC) admits the given fluid ID. */
    public static boolean basicCellAdmits(String fluidId) {
        return fluidId != null && BASIC_CELL_BY_FLUID.containsKey(fluidId);
    }

    /**
     * Returns the full item ID of the named cell for {@code fluidId} within the requested family,
     * or {@code null} if none is registered.
     */
    public static String namedCellForFluid(boolean basicFamily, String fluidId) {
        if (fluidId == null) {
            return null;
        }
        return basicFamily
                ? BASIC_CELL_BY_FLUID.get(fluidId)
                : UNIVERSAL_CELL_BY_FLUID.get(fluidId);
    }

    /** Returns the fluid ID encoded by {@code cellItemId}, or {@code null} if not registered. */
    public static String fluidForNamedCell(String cellItemId) {
        if (cellItemId == null) {
            return null;
        }
        var entry = CELL_BY_ITEM_ID.get(cellItemId);
        return entry == null ? null : entry.fluidId();
    }

    /** Returns all registered external named cell item IDs. */
    public static Collection<String> namedCellItemIds() {
        return Collections.unmodifiableSet(CELL_BY_ITEM_ID.keySet());
    }
}
