package com.ecocraft2.mixin;

import com.ecocraft2.bridge.Ecc2ExternalRecipes;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Injects EcoCraft 2's registered canning bottle recipes ({@code canner_bottle}, shared by IC2NE's
 * Canner mode 0, Solid Canner, and Classic Canner) and Metal Former extruding recipes
 * ({@code metal_former_extruding}) into IC2NE's {@code WorkshopRecipes#forType}.
 *
 * <p>Because {@code WorkshopPlans#plan}, {@code acceptsCanningInput}, {@code storedInput}, and
 * {@code validAutomationInput} all consult {@code WorkshopRecipes#forType}, injecting here
 * automatically enables recipe execution AND slot/automation acceptance.
 */
@Mixin(targets = "com.industrialage.ia2.industrial.WorkshopRecipes", remap = false)
public final class WorkshopRecipesMixin {

    @Unique
    private static final Logger ECC2_LOG = LoggerFactory.getLogger(WorkshopRecipesMixin.class);

    @Unique private static volatile MethodHandle ecc2$stackCtor;
    @Unique private static volatile MethodHandle ecc2$recipeCtor;
    @Unique private static volatile boolean ecc2$ctorLookupFailed;

    @Unique private static volatile long ecc2$cachedExtruderRevision = -1L;
    @Unique private static volatile long ecc2$cachedCannerRevision = -1L;
    @Unique private static volatile List<Object> ecc2$cachedExtruder;
    @Unique private static volatile List<Object> ecc2$cachedCannerBottle;

    @Inject(method = "forType", at = @At("RETURN"), cancellable = true)
    private static void ecc2$appendExternalWorkshopRecipes(
            String name, CallbackInfoReturnable<List<Object>> cir) {
        if ("metal_former_extruding".equals(name)) {
            if (Ecc2ExternalRecipes.extruderRecipes().isEmpty()) {
                return;
            }
            var merged = ecc2$mergedExtruder(cir.getReturnValue());
            if (merged != null) {
                cir.setReturnValue(merged);
            }
        } else if ("canner_bottle".equals(name)) {
            if (Ecc2ExternalRecipes.canningBottleRecipes().isEmpty()) {
                return;
            }
            var merged = ecc2$mergedCannerBottle(cir.getReturnValue());
            if (merged != null) {
                cir.setReturnValue(merged);
            }
        }
    }

    @Unique
    private static List<Object> ecc2$mergedExtruder(List<Object> base) {
        long rev = Ecc2ExternalRecipes.revision();
        var cached = ecc2$cachedExtruder;
        if (cached != null && ecc2$cachedExtruderRevision == rev) {
            return cached;
        }
        if (!ecc2$ensureHandles()) {
            return null;
        }
        var entries = Ecc2ExternalRecipes.extruderRecipes();
        var merged = new ArrayList<Object>(base.size() + entries.size());
        merged.addAll(base);
        int idx = 0;
        for (var entry : entries) {
            Object recipe =
                    ecc2$buildRecipe(
                            "ecc2:metal_former_extruding/" + (idx++),
                            List.of(ecc2$buildStack(entry.inputId(), entry.inputCount())),
                            List.of(ecc2$buildStack(entry.outputId(), entry.outputCount())));
            if (recipe != null) {
                merged.add(recipe);
            }
        }
        var immutable = List.copyOf(merged);
        ecc2$cachedExtruder = immutable;
        ecc2$cachedExtruderRevision = rev;
        return immutable;
    }

    @Unique
    private static List<Object> ecc2$mergedCannerBottle(List<Object> base) {
        long rev = Ecc2ExternalRecipes.revision();
        var cached = ecc2$cachedCannerBottle;
        if (cached != null && ecc2$cachedCannerRevision == rev) {
            return cached;
        }
        if (!ecc2$ensureHandles()) {
            return null;
        }
        var entries = Ecc2ExternalRecipes.canningBottleRecipes();
        var merged = new ArrayList<Object>(base.size() + entries.size());
        merged.addAll(base);
        int idx = 0;
        for (var entry : entries) {
            Object recipe =
                    ecc2$buildRecipe(
                            "ecc2:canner_bottle/" + (idx++),
                            List.of(
                                    ecc2$buildStack(entry.containerId(), entry.containerCount()),
                                    ecc2$buildStack(entry.ingredientId(), entry.ingredientCount())),
                            List.of(ecc2$buildStack(entry.outputId(), entry.outputCount())));
            if (recipe != null) {
                merged.add(recipe);
            }
        }
        var immutable = List.copyOf(merged);
        ecc2$cachedCannerBottle = immutable;
        ecc2$cachedCannerRevision = rev;
        return immutable;
    }

    @Unique
    private static boolean ecc2$ensureHandles() {
        if (ecc2$ctorLookupFailed) {
            return false;
        }
        if (ecc2$stackCtor != null && ecc2$recipeCtor != null) {
            return true;
        }
        try {
            var lookup = MethodHandles.lookup();
            var stackClass = Class.forName("com.industrialage.ia2.industrial.WorkshopRecipes$Stack");
            var recipeClass =
                    Class.forName("com.industrialage.ia2.industrial.WorkshopRecipes$Recipe");
            ecc2$stackCtor =
                    lookup.findConstructor(
                            stackClass, MethodType.methodType(void.class, String.class, int.class));
            ecc2$recipeCtor =
                    lookup.findConstructor(
                            recipeClass,
                            MethodType.methodType(
                                    void.class, String.class, List.class, List.class, Map.class));
            return true;
        } catch (ReflectiveOperationException failure) {
            ecc2$ctorLookupFailed = true;
            ECC2_LOG.error("Failed to bind WorkshopRecipes constructors", failure);
            return false;
        }
    }

    @Unique
    private static Object ecc2$buildStack(String id, int count) {
        try {
            return ecc2$stackCtor.invoke(id, count);
        } catch (Throwable failure) {
            ECC2_LOG.error("Failed to instantiate WorkshopRecipes.Stack({}, {})", id, count, failure);
            return null;
        }
    }

    @Unique
    private static Object ecc2$buildRecipe(String id, List<Object> inputs, List<Object> outputs) {
        if (inputs.contains(null) || outputs.contains(null)) {
            return null;
        }
        try {
            return ecc2$recipeCtor.invoke(id, inputs, outputs, Map.of());
        } catch (Throwable failure) {
            ECC2_LOG.error("Failed to instantiate WorkshopRecipes.Recipe({})", id, failure);
            return null;
        }
    }
}
