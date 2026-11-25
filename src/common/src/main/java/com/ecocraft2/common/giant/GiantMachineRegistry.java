package com.ecocraft2.common.giant;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Extensible registry of {@link GiantMachineSpec} definitions.
 *
 * <p>Pre-populated with all five built-in {@link GiantMachineKind} types and open to addon
 * mods via {@link #register(GiantMachineSpec)}.
 */
public final class GiantMachineRegistry {

    /** Shared default registry pre-loaded with EcoCraft 2's built-in giant machines. */
    public static final GiantMachineRegistry DEFAULT = new GiantMachineRegistry();

    private final Map<String, GiantMachineSpec> byId = new LinkedHashMap<>();
    private final Map<String, GiantMachineSpec> byUnitBlockPath = new LinkedHashMap<>();

    /**
     * Creates a new registry pre-populated with the five built-in {@link GiantMachineKind} specs.
     */
    public GiantMachineRegistry() {
        for (GiantMachineKind kind : GiantMachineKind.values()) {
            register(kind.spec());
        }
    }

    /**
     * Registers a new giant machine specification for future extensions or addons.
     *
     * @param spec machine specification to register
     * @return the registered {@code spec}
     * @throws IllegalArgumentException if a spec with the same {@code id} or {@code unitBlockPath} already exists
     */
    public synchronized GiantMachineSpec register(GiantMachineSpec spec) {
        Objects.requireNonNull(spec, "spec");
        if (byId.containsKey(spec.id())) {
            throw new IllegalArgumentException("Duplicate GiantMachineSpec id: " + spec.id());
        }
        if (byUnitBlockPath.containsKey(spec.unitBlockPath())) {
            throw new IllegalArgumentException("Duplicate GiantMachineSpec unitBlockPath: " + spec.unitBlockPath());
        }
        byId.put(spec.id(), spec);
        byUnitBlockPath.put(spec.unitBlockPath(), spec);
        return spec;
    }

    /** Looks up a spec by its unique ID (e.g. {@code "ore_washer"}). */
    public synchronized Optional<GiantMachineSpec> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(id));
    }

    /** Looks up a spec by its unit block registry path (e.g. {@code "giant_ore_washer_unit"}). */
    public synchronized Optional<GiantMachineSpec> byUnitBlockPath(String unitBlockPath) {
        if (unitBlockPath == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byUnitBlockPath.get(unitBlockPath));
    }

    /** Returns an unmodifiable snapshot of all registered giant machine specs in registration order. */
    public synchronized Collection<GiantMachineSpec> all() {
        return Collections.unmodifiableList(new java.util.ArrayList<>(byId.values()));
    }

    /** Returns the total number of registered giant machine specs. */
    public synchronized int size() {
        return byId.size();
    }
}
