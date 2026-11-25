package com.ecocraft2.mixin;

import com.ecocraft2.bridge.Ecc2ExternalRecipes;
import com.ecocraft2.common.util.ExternalWorkshopRecipeCache;
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
 * Injects EcoCraft 2's registered canning bottle recipes ({@code canner_bottle}) and Metal Former
 * extruding recipes ({@code metal_former_extruding}) into IC2NE's {@code WorkshopRecipes#forType}.
 */
@Mixin(targets = "com.industrialage.ia2.industrial.WorkshopRecipes", remap = false)
public final class WorkshopRecipesMixin {

    @Unique
    private static final Logger ECC2_LOG = LoggerFactory.getLogger(WorkshopRecipesMixin.class);

    @Unique private static volatile MethodHandle ecc2$stackCtor;
    @Unique private static volatile MethodHandle ecc2$recipeCtor;
    @Unique private static volatile boolean ecc2$ctorLookupFailed;

    @Unique
    private static final ExternalWorkshopRecipeCache ECC2_EXTRUDER_CACHE =
            new ExternalWorkshopRecipeCache();

    @Unique
    private static final ExternalWorkshopRecipeCache ECC2_CANNER_CACHE =
            new ExternalWorkshopRecipeCache();

    @Inject(method = "forType", at = @At("RETURN"), cancellable = true)
    private static void ecc2$appendExternalWorkshopRecipes(
            String name, CallbackInfoReturnable<List<Object>> cir) {
        if ("metal_former_extruding".equals(name)) {
            if (Ecc2ExternalRecipes.extruderRecipes().isEmpty()) {
                return;
            }
            var merged =
                    ECC2_EXTRUDER_CACHE.getOrMerge(
                            Ecc2ExternalRecipes.revision(),
                            cir.getReturnValue(),
                            WorkshopRecipesMixin::ecc2$buildExtruderAdditions);
            if (merged != null) {
                cir.setReturnValue(merged);
            }
        } else if ("canner_bottle".equals(name)) {
            if (Ecc2ExternalRecipes.canningBottleRecipes().isEmpty()) {
                return;
            }
            var merged =
                    ECC2_CANNER_CACHE.getOrMerge(
                            Ecc2ExternalRecipes.revision(),
                            cir.getReturnValue(),
                            WorkshopRecipesMixin::ecc2$buildCannerAdditions);
            if (merged != null) {
                cir.setReturnValue(merged);
            }
        }
    }

    @Unique
    private static List<Object> ecc2$buildExtruderAdditions() {
        if (!ecc2$ensureHandles()) {
            return null;
        }
        var entries = Ecc2ExternalRecipes.extruderRecipes();
        var additions = new ArrayList<Object>(entries.size());
        int idx = 0;
        for (var entry : entries) {
            var inputs =
                    ExternalWorkshopRecipeCache.nonNullListOf(
                            ecc2$buildStack(entry.inputId(), entry.inputCount()));
            var outputs =
                    ExternalWorkshopRecipeCache.nonNullListOf(
                            ecc2$buildStack(entry.outputId(), entry.outputCount()));
            Object recipe = ecc2$buildRecipe("ecc2:metal_former_extruding/" + (idx++), inputs, outputs);
            if (recipe != null) {
                additions.add(recipe);
            }
        }
        return additions;
    }

    @Unique
    private static List<Object> ecc2$buildCannerAdditions() {
        if (!ecc2$ensureHandles()) {
            return null;
        }
        var entries = Ecc2ExternalRecipes.canningBottleRecipes();
        var additions = new ArrayList<Object>(entries.size());
        int idx = 0;
        for (var entry : entries) {
            var inputs =
                    ExternalWorkshopRecipeCache.nonNullListOf(
                            ecc2$buildStack(entry.containerId(), entry.containerCount()),
                            ecc2$buildStack(entry.ingredientId(), entry.ingredientCount()));
            var outputs =
                    ExternalWorkshopRecipeCache.nonNullListOf(
                            ecc2$buildStack(entry.outputId(), entry.outputCount()));
            Object recipe = ecc2$buildRecipe("ecc2:canner_bottle/" + (idx++), inputs, outputs);
            if (recipe != null) {
                additions.add(recipe);
            }
        }
        return additions;
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
            var base = MethodHandles.lookup();
            var stackClass = Class.forName("com.industrialage.ia2.industrial.WorkshopRecipes$Stack");
            var recipeClass =
                    Class.forName("com.industrialage.ia2.industrial.WorkshopRecipes$Recipe");
            var stackLookup = MethodHandles.privateLookupIn(stackClass, base);
            var recipeLookup = MethodHandles.privateLookupIn(recipeClass, base);
            ecc2$stackCtor =
                    stackLookup.findConstructor(
                            stackClass, MethodType.methodType(void.class, String.class, int.class));
            ecc2$recipeCtor =
                    recipeLookup.findConstructor(
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
        if (ExternalWorkshopRecipeCache.hasNullElement(inputs)
                || ExternalWorkshopRecipeCache.hasNullElement(outputs)) {
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
