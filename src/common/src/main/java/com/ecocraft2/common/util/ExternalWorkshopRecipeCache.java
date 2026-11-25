package com.ecocraft2.common.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Thread-safe, null-safe cache and list builder for merging EcoCraft 2 recipes into IC2NE
 * {@code WorkshopRecipes#forType} return lists across all supported Minecraft platforms.
 *
 * <p>Avoids {@link List#contains(Object)} with {@code null} (which throws
 * {@link NullPointerException} on Java's {@link List#of} unmodifiable collections) and tracks both
 * the EcoCraft 2 registration revision and the incoming base list reference so other mods mixing
 * into {@code WorkshopRecipes#forType} are never silently clobbered.
 */
public final class ExternalWorkshopRecipeCache {

    private record Snapshot(long revision, List<Object> base, List<Object> merged) {}

    private volatile Snapshot snapshot;

    /**
     * Returns {@code true} if {@code list} is {@code null} or contains any {@code null} element,
     * without calling {@link List#contains(Object)} (safe on {@link List#of} unmodifiable lists).
     *
     * @param list list to inspect
     * @return {@code true} if the list itself or any of its elements is {@code null}
     */
    public static boolean hasNullElement(List<?> list) {
        if (list == null) {
            return true;
        }
        for (Object item : list) {
            if (item == null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Builds an unmodifiable {@link List} containing {@code elements}, or returns {@code null} if
     * {@code elements} is {@code null} or any element is {@code null}.
     *
     * @param elements candidate list elements
     * @return immutable non-null list, or {@code null} when any element is missing
     */
    public static List<Object> nonNullListOf(Object... elements) {
        if (elements == null) {
            return null;
        }
        for (Object element : elements) {
            if (element == null) {
                return null;
            }
        }
        return List.of(elements);
    }

    /**
     * Returns a cached immutable merged list for {@code (revision, base)}, or computes and caches
     * one by appending non-null recipes from {@code additionsSupplier} onto {@code base}.
     *
     * <p>If {@code additionsSupplier} returns {@code null} (for example, when reflection handles
     * failed to bind), returns {@code null} without poisoning the cache so callers leave
     * {@code base} untouched.
     *
     * @param revision          current external recipe registry revision
     * @param base              base list returned by IC2NE or earlier mixins (may be {@code null})
     * @param additionsSupplier supplier producing recipes to append, or {@code null} on failure
     * @return immutable merged list, or {@code null} if additions could not be constructed
     */
    public List<Object> getOrMerge(
            long revision, List<Object> base, Supplier<List<Object>> additionsSupplier) {
        Objects.requireNonNull(additionsSupplier, "additionsSupplier");
        List<Object> safeBase = base == null ? List.of() : base;
        Snapshot current = snapshot;
        if (current != null && current.revision() == revision && current.base() == safeBase) {
            return current.merged();
        }
        List<Object> additions = additionsSupplier.get();
        if (additions == null) {
            return null;
        }
        var merged = new ArrayList<Object>(safeBase.size() + additions.size());
        for (Object item : safeBase) {
            if (item != null) {
                merged.add(item);
            }
        }
        for (Object item : additions) {
            if (item != null) {
                merged.add(item);
            }
        }
        List<Object> immutable = List.copyOf(merged);
        snapshot = new Snapshot(revision, safeBase, immutable);
        return immutable;
    }

    /** Clears the cached snapshot. */
    public void clear() {
        snapshot = null;
    }
}
