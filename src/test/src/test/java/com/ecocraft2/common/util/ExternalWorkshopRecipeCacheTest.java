package com.ecocraft2.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for {@link ExternalWorkshopRecipeCache}, specifically guarding against the
 * {@code List.of(...).contains(null)} {@link NullPointerException} that crashed IC2NE Solid Canner,
 * Canner, Classic Canner, and Metal Former ticks, as well as cross-mod base-list invalidation.
 */
final class ExternalWorkshopRecipeCacheTest {

    @Test
    void hasNullElementNeverThrowsOnImmutableLists() {
        assertFalse(ExternalWorkshopRecipeCache.hasNullElement(List.of()));
        assertFalse(ExternalWorkshopRecipeCache.hasNullElement(List.of("mhera:fluid_cell@1")));
        assertFalse(
                ExternalWorkshopRecipeCache.hasNullElement(
                        List.of("mhera:fluid_cell@1", "ecera:methane_ice_cluster@3")));
        assertFalse(
                ExternalWorkshopRecipeCache.hasNullElement(
                        List.of("a", "b", "c", "d")));
    }

    @Test
    void hasNullElementDetectsNullListAndNullElements() {
        assertTrue(ExternalWorkshopRecipeCache.hasNullElement(null));
        assertTrue(ExternalWorkshopRecipeCache.hasNullElement(Arrays.asList("a", null, "b")));
        List<Object> mutable = new ArrayList<>();
        mutable.add(null);
        assertTrue(ExternalWorkshopRecipeCache.hasNullElement(mutable));
    }

    @Test
    void nonNullListOfRejectsNullElementsWithoutThrowing() {
        assertNull(ExternalWorkshopRecipeCache.nonNullListOf((Object[]) null));
        assertNull(ExternalWorkshopRecipeCache.nonNullListOf((Object) null));
        assertNull(ExternalWorkshopRecipeCache.nonNullListOf("mhera:fluid_cell@1", null));
        assertNull(ExternalWorkshopRecipeCache.nonNullListOf(null, "ecera:methane_ice_cluster@3"));

        List<Object> built =
                ExternalWorkshopRecipeCache.nonNullListOf(
                        "mhera:fluid_cell@1", "ecera:methane_ice_cluster@3");
        assertNotNull(built);
        assertEquals(List.of("mhera:fluid_cell@1", "ecera:methane_ice_cluster@3"), built);
        assertFalse(ExternalWorkshopRecipeCache.hasNullElement(built));
        assertThrows(UnsupportedOperationException.class, () -> built.add("extra"));
    }

    @Test
    void getOrMergeCachesForIdenticalRevisionAndBaseAndInvalidatesOnEitherChange() {
        ExternalWorkshopRecipeCache cache = new ExternalWorkshopRecipeCache();
        AtomicInteger builds = new AtomicInteger();

        List<Object> base1 = List.of("mhera:canner_bottle/apple");
        List<Object> first =
                cache.getOrMerge(
                        1L,
                        base1,
                        () -> {
                            builds.incrementAndGet();
                            return List.of("ecera:canner_bottle/0");
                        });
        assertEquals(List.of("mhera:canner_bottle/apple", "ecera:canner_bottle/0"), first);
        assertEquals(1, builds.get());

        // Exact same revision and base reference hits cache without rebuilding
        List<Object> hit =
                cache.getOrMerge(
                        1L,
                        base1,
                        () -> {
                            builds.incrementAndGet();
                            return List.of("unexpected");
                        });
        assertSame(first, hit);
        assertEquals(1, builds.get());

        // Another mod (e.g. a companion mixin filtering recipes on reload) supplies a new base list
        // while EcoCraft 2's revision is unchanged — cache MUST rebuild on top of the new base.
        List<Object> filteredBase = List.of("mhera:canner_bottle/apple", "compat:custom");
        List<Object> afterModChange =
                cache.getOrMerge(
                        1L,
                        filteredBase,
                        () -> {
                            builds.incrementAndGet();
                            return List.of("ecera:canner_bottle/0");
                        });
        assertEquals(
                List.of("mhera:canner_bottle/apple", "compat:custom", "ecera:canner_bottle/0"),
                afterModChange);
        assertEquals(2, builds.get());

        // EcoCraft 2 revision bump invalidates cache even for the same base reference
        List<Object> afterRevisionBump =
                cache.getOrMerge(
                        2L,
                        filteredBase,
                        () -> {
                            builds.incrementAndGet();
                            return List.of("ecera:canner_bottle/0", "ecera:canner_bottle/1");
                        });
        assertEquals(
                List.of(
                        "mhera:canner_bottle/apple",
                        "compat:custom",
                        "ecera:canner_bottle/0",
                        "ecera:canner_bottle/1"),
                afterRevisionBump);
        assertEquals(3, builds.get());
    }

    @Test
    void getOrMergeHandlesNullBaseNullElementsAndFailedSupplierGracefully() {
        ExternalWorkshopRecipeCache cache = new ExternalWorkshopRecipeCache();

        // Failed supplier (e.g., reflection bind failure) returns null and does not poison cache
        assertNull(cache.getOrMerge(1L, List.of("base"), () -> null));

        // Null base + additions containing nulls are safely skipped
        List<Object> merged =
                cache.getOrMerge(
                        1L, null, () -> Arrays.asList("ecera:recipe/0", null, "ecera:recipe/1"));
        assertEquals(List.of("ecera:recipe/0", "ecera:recipe/1"), merged);

        cache.clear();
        AtomicInteger calls = new AtomicInteger();
        List<Object> rebuilt =
                cache.getOrMerge(
                        1L,
                        null,
                        () -> {
                            calls.incrementAndGet();
                            return List.of("ecera:recipe/0");
                        });
        assertEquals(1, calls.get());
        assertEquals(List.of("ecera:recipe/0"), rebuilt);
    }
}
