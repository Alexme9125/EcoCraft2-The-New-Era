package com.ecocraft2.solar;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/** NeoForge 26.x Molecular Transformer recipe backed by {@link MolecularTransformerSpec}. */
public final class MolecularTransformerRecipe implements Recipe<SingleRecipeInput> {

    public static final MapCodec<MolecularTransformerRecipe> MAP_CODEC =
            MolecularTransformerSpec.MAP_CODEC.xmap(
                    MolecularTransformerRecipe::new, MolecularTransformerRecipe::spec);

    public static final StreamCodec<RegistryFriendlyByteBuf, MolecularTransformerRecipe>
            STREAM_CODEC =
                    MolecularTransformerSpec.STREAM_CODEC.map(
                            MolecularTransformerRecipe::new, MolecularTransformerRecipe::spec);

    private final MolecularTransformerSpec spec;

    public MolecularTransformerRecipe(MolecularTransformerSpec spec) {
        this.spec = spec;
    }

    public MolecularTransformerSpec spec() {
        return spec;
    }

    public Optional<String> inputItem() {
        return spec.input().itemId();
    }

    public Optional<String> inputTag() {
        return spec.input().tagId();
    }

    public int inputCount() {
        return spec.inputCount();
    }

    public Optional<String> outputItem() {
        return spec.output().itemId();
    }

    public Optional<String> outputTag() {
        return spec.output().tagId();
    }

    public int outputCount() {
        return spec.output().count();
    }

    public long energy() {
        return spec.energy();
    }

    public boolean isAvailable() {
        return spec.isAvailable();
    }

    public boolean matchesStack(ItemStack stack) {
        return spec.matchesStack(stack);
    }

    public ItemStack resolveOutput() {
        return spec.resolveOutput();
    }

    public List<ItemStack> resolveInputExamples() {
        return spec.resolveInputExamples();
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return spec.matchesStack(input.item());
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input) {
        return spec.resolveOutput();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.CRAFTING_MISC;
    }

    @Override
    public RecipeSerializer<? extends Recipe<SingleRecipeInput>> getSerializer() {
        return SolarKineticModule.MOLECULAR_TRANSFORMER_SERIALIZER.get();
    }

    @Override
    public RecipeType<? extends Recipe<SingleRecipeInput>> getType() {
        return SolarKineticModule.MOLECULAR_TRANSFORMER_RECIPE_TYPE.get();
    }
}
