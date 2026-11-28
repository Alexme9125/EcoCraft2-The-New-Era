package com.ecocraft2.giant;

import com.industrialage.ia2.content.ProcessingMachine;
import com.industrialage.ia2.logistics.CellProcessing;
import com.industrialage.ia2.logistics.TransportPlatform;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extensible recipe adapter bridging a {@link GiantMachineType} to live IC2NE or custom recipes.
 */
public interface GiantRecipeAdapter {

    /**
     * Resolved single-operation recipe match for one input lane.
     *
     * @param inputCount items consumed from the input slot per single operation
     * @param outputs output stacks produced per single operation (up to 3 for Ore Washer; for
     *     Recycler, holds 1x {@code ic2:scrap} template used for space checks and per-op rolls)
     * @param recycler when {@code true}, each completed operation independently rolls 1/8 scrap
     */
    record Result(int inputCount, List<ItemStack> outputs, boolean recycler) {}

    /** Returns {@code true} if {@code stack} is a valid input item for this giant machine. */
    boolean acceptsInput(ServerLevel level, ItemStack stack);

    /** Resolves the recipe for {@code input}, or {@code null} if none matches. */
    Result resolve(ServerLevel level, ItemStack input);

    /**
     * Standard IC2NE {@link ProcessingMachine} adapter for Macerator, Compressor, and Extractor.
     *
     * @param machineId IC2NE processing machine path ({@code "macerator"}, {@code "compressor"},
     *     {@code "extractor"})
     * @param rejectFilledCells when {@code true} (Compressor), rejects fluid-filled cells
     */
    static GiantRecipeAdapter processing(String machineId, boolean rejectFilledCells) {
        return new GiantRecipeAdapter() {
            @Override
            public boolean acceptsInput(ServerLevel level, ItemStack stack) {
                return resolve(level, stack) != null;
            }

            @Override
            public Result resolve(ServerLevel level, ItemStack input) {
                if (input == null || input.isEmpty()) {
                    return null;
                }
                if (rejectFilledCells && TransportPlatform.cellContainsFluid(input)) {
                    return null;
                }
                String id = GiantGasPlatform.itemId(input);
                if (id.isEmpty()) {
                    return null;
                }
                ProcessingMachine.Recipe recipe =
                        ProcessingMachine.recipe(
                                machineId,
                                id,
                                tag -> GiantGasPlatform.hasItemTag(input, tag));
                if (recipe == null || recipe.inputCount() <= 0) {
                    return null;
                }
                ItemStack out = GiantGasPlatform.itemStack(recipe.output(), recipe.outputCount());
                if (out.isEmpty()) {
                    return null;
                }
                out = CellProcessing.result(machineId, input, out);
                return new Result(recipe.inputCount(), List.of(out), false);
            }
        };
    }

    /**
     * IC2NE Recycler adapter: accepts any non-blacklisted item, consuming 1 item per operation and
     * rolling 1/8 {@code ic2:scrap} independently per operation.
     */
    static GiantRecipeAdapter recycler() {
        return new GiantRecipeAdapter() {
            private boolean excluded(ItemStack stack) {
                return stack == null
                        || stack.isEmpty()
                        || stack.is(Items.SNOWBALL)
                        || stack.is(Items.STICK)
                        || stack.is(Items.GLASS_PANE)
                        || GiantGasPlatform.hasItemTag(stack, "mhera:recycler_blacklist");
            }

            @Override
            public boolean acceptsInput(ServerLevel level, ItemStack stack) {
                return !excluded(stack);
            }

            @Override
            public Result resolve(ServerLevel level, ItemStack input) {
                if (excluded(input)) {
                    return null;
                }
                ItemStack scrap = GiantGasPlatform.itemStack("mhera:scrap", 1);
                if (scrap.isEmpty()) {
                    return null;
                }
                return new Result(1, List.of(scrap), true);
            }
        };
    }

    /**
     * IC2NE Ore Washing Plant adapter: reads live {@code ore_washer} recipes (preferring IC2NE's
     * runtime {@code WorkshopRecipes#forType("ore_washer")} with automatic classpath TSV fallback)
     * yielding up to 3 output stacks per operation.
     */
    static GiantRecipeAdapter oreWasher() {
        return OreWasherAdapter.INSTANCE;
    }

    /** Live IC2NE Ore Washer recipe resolver. */
    final class OreWasherAdapter implements GiantRecipeAdapter {

        static final OreWasherAdapter INSTANCE = new OreWasherAdapter();
        private static final Logger LOG = LoggerFactory.getLogger(OreWasherAdapter.class);
        private static final String TSV_RESOURCE =
                "/com/industrialage/ia2/content/workshop-recipes.tsv";

        private record StackEntry(String id, int count) {}

        private record WasherEntry(StackEntry input, List<StackEntry> outputs) {}

        private volatile List<WasherEntry> cachedEntries;

        private OreWasherAdapter() {}

        @Override
        public boolean acceptsInput(ServerLevel level, ItemStack stack) {
            return resolve(level, stack) != null;
        }

        @Override
        public Result resolve(ServerLevel level, ItemStack input) {
            if (input == null || input.isEmpty()) {
                return null;
            }
            String itemId = GiantGasPlatform.itemId(input);
            if (itemId.isEmpty()) {
                return null;
            }
            for (WasherEntry entry : entries()) {
                if (matches(entry.input().id(), input, itemId)) {
                    var outputs = new ArrayList<ItemStack>(entry.outputs().size());
                    for (StackEntry outEntry : entry.outputs()) {
                        ItemStack stack =
                                GiantGasPlatform.itemStack(outEntry.id(), outEntry.count());
                        if (!stack.isEmpty()) {
                            outputs.add(stack);
                        }
                    }
                    if (!outputs.isEmpty()) {
                        return new Result(entry.input().count(), List.copyOf(outputs), false);
                    }
                }
            }
            return null;
        }

        private static boolean matches(String spec, ItemStack stack, String itemId) {
            if (spec == null || spec.isEmpty()) {
                return false;
            }
            if (spec.startsWith("#")) {
                return GiantGasPlatform.hasItemTag(stack, spec.substring(1));
            }
            return spec.equals(itemId);
        }

        private List<WasherEntry> entries() {
            var current = cachedEntries;
            if (current != null) {
                return current;
            }
            List<WasherEntry> loaded = loadFromWorkshopRecipes();
            if (loaded.isEmpty()) {
                loaded = loadFromTsvFallback();
            }
            cachedEntries = List.copyOf(loaded);
            return cachedEntries;
        }

        @SuppressWarnings("unchecked")
        private static List<WasherEntry> loadFromWorkshopRecipes() {
            var list = new ArrayList<WasherEntry>();
            try {
                Class<?> recipesClass =
                        Class.forName("com.industrialage.ia2.industrial.WorkshopRecipes");
                Method forType = recipesClass.getDeclaredMethod("forType", String.class);
                forType.setAccessible(true);
                List<Object> rawRecipes = (List<Object>) forType.invoke(null, "ore_washer");
                if (rawRecipes == null || rawRecipes.isEmpty()) {
                    return list;
                }
                Class<?> recipeClass =
                        Class.forName("com.industrialage.ia2.industrial.WorkshopRecipes$Recipe");
                Class<?> stackClass =
                        Class.forName("com.industrialage.ia2.industrial.WorkshopRecipes$Stack");
                Method inputsMethod = recipeClass.getDeclaredMethod("inputs");
                Method outputsMethod = recipeClass.getDeclaredMethod("outputs");
                Method idMethod = stackClass.getDeclaredMethod("id");
                Method countMethod = stackClass.getDeclaredMethod("count");
                inputsMethod.setAccessible(true);
                outputsMethod.setAccessible(true);
                idMethod.setAccessible(true);
                countMethod.setAccessible(true);

                for (Object raw : rawRecipes) {
                    List<Object> rawInputs = (List<Object>) inputsMethod.invoke(raw);
                    List<Object> rawOutputs = (List<Object>) outputsMethod.invoke(raw);
                    if (rawInputs == null
                            || rawInputs.isEmpty()
                            || rawOutputs == null
                            || rawOutputs.isEmpty()) {
                        continue;
                    }
                    Object inObj = rawInputs.get(0);
                    StackEntry input =
                            new StackEntry(
                                    (String) idMethod.invoke(inObj),
                                    Math.max(1, (Integer) countMethod.invoke(inObj)));
                    var outs = new ArrayList<StackEntry>(rawOutputs.size());
                    for (Object outObj : rawOutputs) {
                        outs.add(
                                new StackEntry(
                                        (String) idMethod.invoke(outObj),
                                        Math.max(1, (Integer) countMethod.invoke(outObj))));
                    }
                    list.add(new WasherEntry(input, List.copyOf(outs)));
                }
            } catch (Throwable failure) {
                LOG.debug(
                        "Falling back to Machinera workshop-recipes.tsv for ore_washer recipes: {}",
                        failure.getMessage());
            }
            return list;
        }

        private static List<WasherEntry> loadFromTsvFallback() {
            var list = new ArrayList<WasherEntry>();
            try (var stream = ProcessingMachine.class.getResourceAsStream(TSV_RESOURCE)) {
                if (stream == null) {
                    return list;
                }
                try (var reader =
                        new BufferedReader(
                                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
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
                            list.add(new WasherEntry(ins.get(0), outs));
                        }
                    }
                }
            } catch (Exception failure) {
                LOG.warn("Failed to read ore_washer recipes from {}", TSV_RESOURCE, failure);
            }
            return list;
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
    }
}
