package com.ecocraft2.giant;

import com.ecocraft2.bridge.Ecc2BridgePlatform;
import com.ecocraft2.common.giant.GiantMachineKind;
import com.industrialage.ia2.content.ProcessingMachine;
import com.industrialage.ia2.industrial.WorkshopPlatform;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Strategy adapter bridging Giant Machine processing kinds to live IC2NE 1.0.6 recipes (Macerator,
 * Compressor, Extractor, Recycler with blacklist, and Ore Washing Plant multi-output recipes).
 */
public final class GiantRecipeAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(GiantRecipeAdapter.class);

    public record ResolvedRecipe(int inputCount, List<ItemStack> outputs, boolean recycler) {}

    private static volatile boolean workshopHandlesInit;
    private static volatile MethodHandle forTypeHandle;
    private static volatile MethodHandle recipeInputsHandle;
    private static volatile MethodHandle recipeOutputsHandle;
    private static volatile MethodHandle stackIdHandle;
    private static volatile MethodHandle stackCountHandle;
    private static volatile MethodHandle excludedRecyclerHandle;

    private GiantRecipeAdapter() {}

    /** Resolves a live IC2NE recipe for {@code kind} and {@code input}, or {@code null} if none. */
    public static ResolvedRecipe match(GiantMachineKind kind, ItemStack input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        return switch (kind) {
            case MACERATOR -> matchProcessing("macerator", input);
            case COMPRESSOR -> matchProcessing("compressor", input);
            case EXTRACTOR -> matchProcessing("extractor", input);
            case RECYCLER -> matchRecycler(input);
            case ORE_WASHER -> matchOreWasher(input);
        };
    }

    private static ResolvedRecipe matchProcessing(String machineName, ItemStack input) {
        String id = Ecc2BridgePlatform.itemId(input);
        if (id.isEmpty()) {
            return null;
        }
        Predicate<String> tagMatcher =
                tagName -> {
                    String clean = tagName.startsWith("#") ? tagName.substring(1) : tagName;
                    ResourceLocation rl = ResourceLocation.tryParse(clean);
                    return rl != null && input.is(TagKey.create(Registry.ITEM_REGISTRY, rl));
                };
        ProcessingMachine.Recipe recipe = ProcessingMachine.recipe(machineName, id, tagMatcher);
        if (recipe == null || input.getCount() < recipe.inputCount()) {
            return null;
        }
        Item outItem = Ecc2BridgePlatform.findItem(recipe.output());
        if (outItem == null) {
            return null;
        }
        return new ResolvedRecipe(
                recipe.inputCount(), List.of(new ItemStack(outItem, recipe.outputCount())), false);
    }

    private static ResolvedRecipe matchRecycler(ItemStack input) {
        if (isExcludedFromRecycling(input)) {
            return null;
        }
        Item scrap = Ecc2BridgePlatform.findItem("ic2:scrap");
        if (scrap == null) {
            return null;
        }
        return new ResolvedRecipe(1, List.of(new ItemStack(scrap, 1)), true);
    }

    @SuppressWarnings("unchecked")
    private static ResolvedRecipe matchOreWasher(ItemStack input) {
        ensureWorkshopHandles();
        if (forTypeHandle == null) {
            return null;
        }
        String inputId = Ecc2BridgePlatform.itemId(input);
        try {
            List<Object> recipes = (List<Object>) forTypeHandle.invoke("ore_washing");
            if (recipes == null) {
                return null;
            }
            for (Object recipe : recipes) {
                List<Object> ins = (List<Object>) recipeInputsHandle.invoke(recipe);
                if (ins == null || ins.isEmpty()) {
                    continue;
                }
                Object req = ins.get(0);
                String reqId = (String) stackIdHandle.invoke(req);
                int reqCount = (int) stackCountHandle.invoke(req);
                if (input.getCount() < reqCount) {
                    continue;
                }
                if (!matchesStackSpec(input, inputId, reqId)) {
                    continue;
                }
                List<Object> outs = (List<Object>) recipeOutputsHandle.invoke(recipe);
                List<ItemStack> resolvedOuts = new ArrayList<>();
                if (outs != null) {
                    for (Object outObj : outs) {
                        String outId = (String) stackIdHandle.invoke(outObj);
                        int outCount = (int) stackCountHandle.invoke(outObj);
                        Item outItem = Ecc2BridgePlatform.findItem(outId);
                        if (outItem != null && outCount > 0) {
                            resolvedOuts.add(new ItemStack(outItem, outCount));
                        }
                    }
                }
                if (!resolvedOuts.isEmpty()) {
                    return new ResolvedRecipe(reqCount, List.copyOf(resolvedOuts), false);
                }
            }
        } catch (Throwable failure) {
            LOG.error("Failed to query IC2NE ore_washing recipes", failure);
        }
        return null;
    }

    private static boolean matchesStackSpec(ItemStack stack, String itemId, String spec) {
        if (spec == null || spec.isEmpty()) {
            return false;
        }
        if (spec.startsWith("#")) {
            ResourceLocation rl = ResourceLocation.tryParse(spec.substring(1));
            return rl != null && stack.is(TagKey.create(Registry.ITEM_REGISTRY, rl));
        }
        return spec.equals(itemId);
    }

    private static boolean isExcludedFromRecycling(ItemStack stack) {
        ensureWorkshopHandles();
        if (excludedRecyclerHandle != null) {
            try {
                return (boolean) excludedRecyclerHandle.invoke(stack);
            } catch (Throwable ignored) {
                // Fall back to safe minimal check below.
            }
        }
        String id = Ecc2BridgePlatform.itemId(stack);
        return "minecraft:snowball".equals(id)
                || "minecraft:stick".equals(id)
                || "minecraft:glass_pane".equals(id);
    }

    private static synchronized void ensureWorkshopHandles() {
        if (workshopHandlesInit) {
            return;
        }
        workshopHandlesInit = true;
        try {
            var base = MethodHandles.lookup();
            var recipesClass =
                    Class.forName("com.industrialage.ia2.industrial.WorkshopRecipes");
            var recipeClass =
                    Class.forName("com.industrialage.ia2.industrial.WorkshopRecipes$Recipe");
            var stackClass =
                    Class.forName("com.industrialage.ia2.industrial.WorkshopRecipes$Stack");

            var recipesLookup = MethodHandles.privateLookupIn(recipesClass, base);
            var recipeLookup = MethodHandles.privateLookupIn(recipeClass, base);
            var stackLookup = MethodHandles.privateLookupIn(stackClass, base);
            var platformLookup = MethodHandles.privateLookupIn(WorkshopPlatform.class, base);

            forTypeHandle =
                    recipesLookup.findStatic(
                            recipesClass,
                            "forType",
                            MethodType.methodType(List.class, String.class));
            recipeInputsHandle =
                    recipeLookup.findVirtual(
                            recipeClass, "inputs", MethodType.methodType(List.class));
            recipeOutputsHandle =
                    recipeLookup.findVirtual(
                            recipeClass, "outputs", MethodType.methodType(List.class));
            stackIdHandle =
                    stackLookup.findVirtual(stackClass, "id", MethodType.methodType(String.class));
            stackCountHandle =
                    stackLookup.findVirtual(stackClass, "count", MethodType.methodType(int.class));
            excludedRecyclerHandle =
                    platformLookup.findStatic(
                            WorkshopPlatform.class,
                            "excludedFromRecycling",
                            MethodType.methodType(boolean.class, ItemStack.class));
        } catch (Throwable failure) {
            LOG.error("Failed to bind IC2NE WorkshopRecipes/WorkshopPlatform handles", failure);
        }
    }
}
