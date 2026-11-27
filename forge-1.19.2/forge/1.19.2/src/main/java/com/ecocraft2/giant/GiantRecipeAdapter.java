package com.ecocraft2.giant;

import com.ecocraft2.bridge.Ecc2BridgePlatform;
import com.ecocraft2.common.giant.GiantMachineKind;
import com.industrialage.ia2.content.ProcessingMachine;
import com.industrialage.ia2.industrial.WorkshopPlatform;
import com.industrialage.ia2.logistics.CellProcessing;
import com.industrialage.ia2.logistics.TransportPlatform;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
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
    private static final String TSV_RESOURCE = "/com/industrialage/ia2/content/workshop-recipes.tsv";

    public record ResolvedRecipe(int inputCount, List<ItemStack> outputs, boolean recycler) {}

    private record StackEntry(String id, int count) {}

    private record WasherEntry(StackEntry input, List<StackEntry> outputs) {}

    private static volatile boolean workshopHandlesInit;
    private static volatile MethodHandle forTypeHandle;
    private static volatile MethodHandle recipeInputsHandle;
    private static volatile MethodHandle recipeOutputsHandle;
    private static volatile MethodHandle stackIdHandle;
    private static volatile MethodHandle stackCountHandle;
    private static volatile MethodHandle excludedRecyclerHandle;
    private static volatile List<WasherEntry> cachedTsvWasherEntries;

    private GiantRecipeAdapter() {}

    /** Resolves a live IC2NE recipe for {@code kind} and {@code input}, or {@code null} if none. */
    public static ResolvedRecipe match(GiantMachineKind kind, ItemStack input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        return switch (kind) {
            case MACERATOR -> matchProcessing("macerator", false, input);
            case COMPRESSOR -> matchProcessing("compressor", true, input);
            case EXTRACTOR -> matchProcessing("extractor", false, input);
            case RECYCLER -> matchRecycler(input);
            case ORE_WASHER -> matchOreWasher(input);
        };
    }

    /** Returns whether {@code input} is accepted by any live recipe for {@code kind} (ignoring count). */
    public static boolean acceptsInput(GiantMachineKind kind, ItemStack input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        ItemStack probe =
                input.getCount() >= 64 ? input : Ecc2BridgePlatform.copyWithCount(input, 64);
        return match(kind, probe) != null;
    }

    /** Returns the single-operation recipe input count for {@code input}, or {@code 1} if none. */
    public static int recipeInputCount(GiantMachineKind kind, ItemStack input) {
        if (input == null || input.isEmpty()) {
            return 1;
        }
        ItemStack probe =
                Ecc2BridgePlatform.copyWithCount(input, Math.max(input.getCount(), 64));
        ResolvedRecipe resolved = match(kind, probe);
        return resolved != null && resolved.inputCount() > 1 ? resolved.inputCount() : 1;
    }

    private static ResolvedRecipe matchProcessing(
            String machineName, boolean rejectFilledCells, ItemStack input) {
        if (rejectFilledCells && TransportPlatform.cellContainsFluid(input)) {
            return null;
        }
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
        if (recipe == null || recipe.inputCount() <= 0 || input.getCount() < recipe.inputCount()) {
            return null;
        }
        Item outItem = Ecc2BridgePlatform.findItem(recipe.output());
        if (outItem == null) {
            return null;
        }
        ItemStack out = CellProcessing.result(
                machineName, input, new ItemStack(outItem, recipe.outputCount()));
        if (out.isEmpty()) {
            return null;
        }
        return new ResolvedRecipe(recipe.inputCount(), List.of(out), false);
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
        String inputId = Ecc2BridgePlatform.itemId(input);
        if (inputId.isEmpty()) {
            return null;
        }
        ensureWorkshopHandles();
        if (forTypeHandle != null) {
            try {
                List<Object> recipes = (List<Object>) forTypeHandle.invoke("ore_washer");
                if (recipes != null && !recipes.isEmpty()) {
                    for (Object recipe : recipes) {
                        List<Object> ins = (List<Object>) recipeInputsHandle.invoke(recipe);
                        if (ins == null || ins.isEmpty()) {
                            continue;
                        }
                        Object req = ins.get(0);
                        String reqId = (String) stackIdHandle.invoke(req);
                        int reqCount = Math.max(1, (int) stackCountHandle.invoke(req));
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
                    return null;
                }
            } catch (Throwable failure) {
                LOG.debug(
                        "Falling back to IC2NE workshop-recipes.tsv for ore_washer recipes: {}",
                        failure.getMessage());
            }
        }
        return matchOreWasherFromTsv(input, inputId);
    }

    private static ResolvedRecipe matchOreWasherFromTsv(ItemStack input, String inputId) {
        for (WasherEntry entry : loadTsvWasherEntries()) {
            if (input.getCount() < entry.input().count()) {
                continue;
            }
            if (!matchesStackSpec(input, inputId, entry.input().id())) {
                continue;
            }
            List<ItemStack> resolvedOuts = new ArrayList<>(entry.outputs().size());
            for (StackEntry outEntry : entry.outputs()) {
                Item outItem = Ecc2BridgePlatform.findItem(outEntry.id());
                if (outItem != null && outEntry.count() > 0) {
                    resolvedOuts.add(new ItemStack(outItem, outEntry.count()));
                }
            }
            if (!resolvedOuts.isEmpty()) {
                return new ResolvedRecipe(entry.input().count(), List.copyOf(resolvedOuts), false);
            }
        }
        return null;
    }

    private static List<WasherEntry> loadTsvWasherEntries() {
        var cached = cachedTsvWasherEntries;
        if (cached != null) {
            return cached;
        }
        var list = new ArrayList<WasherEntry>();
        try (var stream = ProcessingMachine.class.getResourceAsStream(TSV_RESOURCE)) {
            if (stream != null) {
                try (var reader =
                        new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    boolean first = true;
                    while ((line = reader.readLine()) != null) {
                        if (first) {
                            first = false;
                            continue;
                        }
                        String[] cols = line.split("\t", -1);
                        if (cols.length < 4 || !"ore_washer".equals(cols[0])) {
                            continue;
                        }
                        List<StackEntry> ins = parseStacks(cols[2]);
                        List<StackEntry> outs = parseStacks(cols[3]);
                        if (!ins.isEmpty() && !outs.isEmpty()) {
                            list.add(new WasherEntry(ins.get(0), List.copyOf(outs)));
                        }
                    }
                }
            }
        } catch (Exception failure) {
            LOG.warn("Failed to read ore_washer recipes from {}", TSV_RESOURCE, failure);
        }
        cached = List.copyOf(list);
        cachedTsvWasherEntries = cached;
        return cached;
    }

    private static List<StackEntry> parseStacks(String raw) {
        var out = new ArrayList<StackEntry>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String token : raw.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int at = trimmed.lastIndexOf('@');
            String id = at > 0 ? trimmed.substring(0, at) : trimmed;
            int count = 1;
            if (at > 0 && at + 1 < trimmed.length()) {
                try {
                    count = Math.max(1, Integer.parseInt(trimmed.substring(at + 1)));
                } catch (NumberFormatException ignored) {
                    count = 1;
                }
            }
            out.add(new StackEntry(id, count));
        }
        return out;
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
