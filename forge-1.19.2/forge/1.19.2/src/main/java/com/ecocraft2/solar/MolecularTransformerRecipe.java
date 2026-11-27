package com.ecocraft2.solar;

import com.ecocraft2.bridge.Ecc2BridgePlatform;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * Datapack JSON recipe for the Molecular Transformer ({@code ecc2:molecular_transformer}).
 *
 * <p>Supports direct item or tag inputs, direct item or tag-resolved outputs (for conditional
 * cross-mod recipes), custom input stack counts, and strictly positive EU costs.
 */
public final class MolecularTransformerRecipe implements Recipe<Container> {

    private final ResourceLocation id;
    private final Ingredient input;
    private final int inputCount;
    private final ItemStack fixedOutput;
    @Nullable private final TagKey<Item> outputTag;
    private final int outputCount;
    private final long energy;

    public MolecularTransformerRecipe(
            ResourceLocation id,
            Ingredient input,
            int inputCount,
            ItemStack fixedOutput,
            @Nullable TagKey<Item> outputTag,
            int outputCount,
            long energy) {
        this.id = id;
        this.input = input;
        this.inputCount = Math.max(1, inputCount);
        this.fixedOutput = fixedOutput;
        this.outputTag = outputTag;
        this.outputCount = Math.max(1, outputCount);
        this.energy = Math.max(1L, energy);
    }

    public Ingredient input() {
        return input;
    }

    public int inputCount() {
        return inputCount;
    }

    public long energy() {
        return energy;
    }

    /** Resolves the output {@link ItemStack}, querying the output tag lazily when tag-based. */
    public ItemStack resolveOutput() {
        if (!fixedOutput.isEmpty()) {
            return fixedOutput.copy();
        }
        if (outputTag != null) {
            var tags = ForgeRegistries.ITEMS.tags();
            if (tags != null) {
                var tag = tags.getTag(outputTag);
                var iterator = tag.iterator();
                if (iterator.hasNext()) {
                    return new ItemStack(iterator.next(), outputCount);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    public boolean isAvailable() {
        return input != null
                && !input.isEmpty()
                && input.getItems().length > 0
                && !resolveOutput().isEmpty()
                && energy > 0L;
    }

    @Override
    public boolean matches(Container container, Level level) {
        ItemStack stack = container.getItem(0);
        return !stack.isEmpty()
                && stack.getCount() >= inputCount
                && input.test(stack)
                && isAvailable();
    }

    @Override
    public ItemStack assemble(Container container) {
        return resolveOutput();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem() {
        return resolveOutput();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        list.add(input);
        return list;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SolarKineticRegistry.MT_RECIPE_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return SolarKineticRegistry.MT_RECIPE_TYPE.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    /** JSON and network serializer for {@link MolecularTransformerRecipe}. */
    public static final class Serializer implements RecipeSerializer<MolecularTransformerRecipe> {

        @Override
        public MolecularTransformerRecipe fromJson(ResourceLocation id, JsonObject json) {
            JsonObject inputObj = GsonHelper.getAsJsonObject(json, "input");
            Ingredient input = Ingredient.fromJson(inputObj);
            int inputCount = GsonHelper.getAsInt(json, "input_count", 1);
            if (inputCount <= 0) {
                throw new JsonSyntaxException("Molecular Transformer input_count must be > 0");
            }
            long energy = GsonHelper.getAsLong(json, "energy");
            if (energy <= 0L) {
                throw new JsonSyntaxException("Molecular Transformer energy must be > 0");
            }
            JsonObject outputObj = GsonHelper.getAsJsonObject(json, "output");
            int outputCount = GsonHelper.getAsInt(outputObj, "count", 1);
            if (outputCount <= 0) {
                throw new JsonSyntaxException("Molecular Transformer output count must be > 0");
            }
            if (outputObj.has("tag")) {
                ResourceLocation tagId =
                        ResourceLocation.tryParse(GsonHelper.getAsString(outputObj, "tag"));
                if (tagId == null) {
                    throw new JsonSyntaxException("Invalid output tag in " + id);
                }
                TagKey<Item> tagKey = TagKey.create(Registry.ITEM_REGISTRY, tagId);
                return new MolecularTransformerRecipe(
                        id, input, inputCount, ItemStack.EMPTY, tagKey, outputCount, energy);
            }
            String itemId =
                    outputObj.has("item")
                            ? GsonHelper.getAsString(outputObj, "item")
                            : GsonHelper.getAsString(outputObj, "id");
            Item outItem = Ecc2BridgePlatform.findItem(itemId);
            ItemStack fixed =
                    outItem == null ? ItemStack.EMPTY : new ItemStack(outItem, outputCount);
            return new MolecularTransformerRecipe(
                    id, input, inputCount, fixed, null, outputCount, energy);
        }

        @Override
        public @Nullable MolecularTransformerRecipe fromNetwork(
                ResourceLocation id, FriendlyByteBuf buf) {
            Ingredient input = Ingredient.fromNetwork(buf);
            int inputCount = buf.readVarInt();
            long energy = buf.readVarLong();
            boolean isTag = buf.readBoolean();
            int outputCount = buf.readVarInt();
            if (isTag) {
                ResourceLocation tagId = buf.readResourceLocation();
                TagKey<Item> tagKey = TagKey.create(Registry.ITEM_REGISTRY, tagId);
                return new MolecularTransformerRecipe(
                        id, input, inputCount, ItemStack.EMPTY, tagKey, outputCount, energy);
            }
            ItemStack out = buf.readItem();
            return new MolecularTransformerRecipe(
                    id, input, inputCount, out, null, outputCount, energy);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, MolecularTransformerRecipe recipe) {
            recipe.input.toNetwork(buf);
            buf.writeVarInt(recipe.inputCount);
            buf.writeVarLong(recipe.energy);
            boolean isTag = recipe.outputTag != null;
            buf.writeBoolean(isTag);
            buf.writeVarInt(recipe.outputCount);
            if (isTag) {
                buf.writeResourceLocation(recipe.outputTag.location());
            } else {
                buf.writeItem(recipe.fixedOutput);
            }
        }
    }
}
