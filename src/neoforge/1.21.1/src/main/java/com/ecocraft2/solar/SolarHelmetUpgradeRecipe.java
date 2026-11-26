package com.ecocraft2.solar;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;

/**
 * NeoForge 1.21.1 shapeless crafting recipe wrapper that preserves stored EU and leather dye color
 * when upgrading {@code ecc2:hybrid_solar_helmet} to {@code ecc2:ultimate_hybrid_solar_helmet}
 * across normal clicks, Shift-clicks, crafting previews, and Crafter blocks.
 */
public final class SolarHelmetUpgradeRecipe implements CraftingRecipe {

    private final ShapelessRecipe delegate;

    public SolarHelmetUpgradeRecipe(ShapelessRecipe delegate) {
        this.delegate = delegate;
    }

    public ShapelessRecipe delegate() {
        return delegate;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return delegate.matches(input, level);
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack result = delegate.assemble(input, registries);
        for (ItemStack stack : input.items()) {
            if (!stack.isEmpty() && stack.getItem() instanceof SolarHelmetItem) {
                SolarHelmetHandler.copyHelmetUpgradeData(stack, result);
                break;
            }
        }
        return result;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        return delegate.getRemainingItems(input);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return delegate.canCraftInDimensions(width, height);
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return delegate.getResultItem(registries);
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return delegate.getIngredients();
    }

    @Override
    public String getGroup() {
        return delegate.getGroup();
    }

    @Override
    public CraftingBookCategory category() {
        return delegate.category();
    }

    @Override
    public boolean showNotification() {
        return delegate.showNotification();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SolarKineticModule.SOLAR_HELMET_UPGRADE_SERIALIZER.get();
    }

    /** Serializer delegating JSON and network formats directly to vanilla shapeless recipes. */
    public static final class Serializer implements RecipeSerializer<SolarHelmetUpgradeRecipe> {
        private static final MapCodec<SolarHelmetUpgradeRecipe> CODEC =
                RecipeSerializer.SHAPELESS_RECIPE
                        .codec()
                        .xmap(SolarHelmetUpgradeRecipe::new, SolarHelmetUpgradeRecipe::delegate);

        private static final StreamCodec<RegistryFriendlyByteBuf, SolarHelmetUpgradeRecipe>
                STREAM_CODEC =
                        RecipeSerializer.SHAPELESS_RECIPE
                                .streamCodec()
                                .map(
                                        SolarHelmetUpgradeRecipe::new,
                                        SolarHelmetUpgradeRecipe::delegate);

        @Override
        public MapCodec<SolarHelmetUpgradeRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, SolarHelmetUpgradeRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
