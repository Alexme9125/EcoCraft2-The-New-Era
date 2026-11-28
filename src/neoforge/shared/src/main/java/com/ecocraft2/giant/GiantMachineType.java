package com.ecocraft2.giant;

import com.ecocraft2.common.giant.GiantMachineKind;
import com.ecocraft2.common.giant.GiantMachineRegistry;
import com.ecocraft2.common.giant.GiantMachineSpec;
import com.ecocraft2.config.Ecc2Config;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Extensible Giant Machine type binding a pure-Java {@link GiantMachineSpec} to its runtime
 * {@link GiantRecipeAdapter} and localization key.
 *
 * <p>Future giant machines (e.g. Giant Induction Furnace, Giant Thermal Centrifuge, Giant Metal
 * Former) can be added by calling {@link #register(GiantMachineType)} without modifying core
 * multiblock, engine, menu, or screen logic.
 */
public final class GiantMachineType {

    private static final Map<String, GiantMachineType> BY_ID = new LinkedHashMap<>();
    private static final Map<String, GiantMachineType> BY_UNIT_PATH = new LinkedHashMap<>();

    public static final GiantMachineType MACERATOR =
            registerBuiltIn(
                    GiantMachineKind.MACERATOR.spec(),
                    GiantRecipeAdapter.processing("macerator", false));

    public static final GiantMachineType COMPRESSOR =
            registerBuiltIn(
                    GiantMachineKind.COMPRESSOR.spec(),
                    GiantRecipeAdapter.processing("compressor", true));

    public static final GiantMachineType RECYCLER =
            registerBuiltIn(GiantMachineKind.RECYCLER.spec(), GiantRecipeAdapter.recycler());

    public static final GiantMachineType EXTRACTOR =
            registerBuiltIn(
                    GiantMachineKind.EXTRACTOR.spec(),
                    GiantRecipeAdapter.processing("extractor", false));

    public static final GiantMachineType ORE_WASHER =
            registerBuiltIn(GiantMachineKind.ORE_WASHER.spec(), GiantRecipeAdapter.oreWasher());

    private final GiantMachineSpec spec;
    private final GiantRecipeAdapter recipeAdapter;
    private final String containerTitleKey;

    public GiantMachineType(
            GiantMachineSpec spec, GiantRecipeAdapter recipeAdapter, String containerTitleKey) {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.recipeAdapter = Objects.requireNonNull(recipeAdapter, "recipeAdapter");
        this.containerTitleKey = Objects.requireNonNull(containerTitleKey, "containerTitleKey");
    }

    private static GiantMachineType registerBuiltIn(
            GiantMachineSpec spec, GiantRecipeAdapter adapter) {
        var type = new GiantMachineType(spec, adapter, "container.ecera.giant_" + spec.id());
        BY_ID.put(spec.id(), type);
        BY_UNIT_PATH.put(spec.unitBlockPath(), type);
        return type;
    }

    /**
     * Registers a custom addon {@link GiantMachineType} into both the NeoForge runtime registry and
     * {@link GiantMachineRegistry#DEFAULT}.
     */
    public static synchronized GiantMachineType register(GiantMachineType type) {
        Objects.requireNonNull(type, "type");
        if (BY_ID.containsKey(type.id())) {
            throw new IllegalArgumentException("Duplicate GiantMachineType id: " + type.id());
        }
        if (BY_UNIT_PATH.containsKey(type.unitBlockPath())) {
            throw new IllegalArgumentException(
                    "Duplicate GiantMachineType unitBlockPath: " + type.unitBlockPath());
        }
        if (GiantMachineRegistry.DEFAULT.byId(type.id()).isEmpty()) {
            GiantMachineRegistry.DEFAULT.register(type.spec());
        }
        BY_ID.put(type.id(), type);
        BY_UNIT_PATH.put(type.unitBlockPath(), type);
        return type;
    }

    public static synchronized Optional<GiantMachineType> byId(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(BY_ID.get(id));
    }

    public static synchronized Optional<GiantMachineType> byUnitBlockPath(String path) {
        return path == null ? Optional.empty() : Optional.ofNullable(BY_UNIT_PATH.get(path));
    }

    public static synchronized Collection<GiantMachineType> all() {
        return Collections.unmodifiableList(new ArrayList<>(BY_ID.values()));
    }

    public GiantMachineSpec spec() {
        return spec;
    }

    /**
     * Returns a {@link GiantMachineSpec} reflecting live {@link Ecc2Config} balance overrides
     * (base tier and ore washer water tank capacity).
     */
    public GiantMachineSpec effectiveSpec() {
        int baseTier = Math.max(1, Ecc2Config.giantBaseTier());
        int waterCap =
                spec.usesWater()
                        ? Math.max(spec.waterPerOperationMb(), Ecc2Config.giantWasherWaterCapacity())
                        : 0;
        if (baseTier == spec.baseTier() && waterCap == spec.waterTankCapacityMb()) {
            return spec;
        }
        return new GiantMachineSpec(
                spec.id(),
                spec.unitBlockPath(),
                spec.sourceMachineId(),
                spec.baseOperationTicks(),
                spec.baseEuPerTick(),
                baseTier,
                waterCap,
                spec.waterPerOperationMb(),
                spec.scrapChanceDenominator());
    }

    public GiantRecipeAdapter recipeAdapter() {
        return recipeAdapter;
    }

    public String id() {
        return spec.id();
    }

    public String unitBlockPath() {
        return spec.unitBlockPath();
    }

    public String containerTitleKey() {
        return containerTitleKey;
    }
}
