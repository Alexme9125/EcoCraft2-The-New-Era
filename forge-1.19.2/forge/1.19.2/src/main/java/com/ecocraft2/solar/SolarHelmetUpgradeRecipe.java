package com.ecocraft2.solar;

import com.google.gson.JsonObject;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Shapeless crafting recipe for upgrading a Hybrid Solar Helmet into an Ultimate Hybrid Solar
 * Helmet while preserving stored EU and custom leather dye color across normal clicks, Shift-click,
 * and crafting previews.
 */
public final class SolarHelmetUpgradeRecipe implements CraftingRecipe {

    private final ShapelessRecipe delegate;

    public SolarHelmetUpgradeRecipe(ShapelessRecipe delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        return delegate.matches(container, level);
    }

    @Override
    public ItemStack assemble(CraftingContainer container) {
        ItemStack result = delegate.assemble(container);
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack ingredient = container.getItem(i);
            if (!ingredient.isEmpty()
                    && ingredient.getItem() == SolarKineticRegistry.HYBRID_SOLAR_HELMET.get()) {
                copyHelmetUpgradeData(ingredient, result);
                break;
            }
        }
        return result;
    }

    /** Copies custom dye color and stored EU from a Hybrid Solar Helmet to an upgraded helmet. */
    public static void copyHelmetUpgradeData(ItemStack source, ItemStack target) {
        if (!(source.getItem() instanceof DyeableSolarHelmetItem sourceHelmet)
                || !(target.getItem() instanceof DyeableSolarHelmetItem targetHelmet)) {
            return;
        }
        if (sourceHelmet.hasCustomColor(source)) {
            targetHelmet.setColor(target, sourceHelmet.getColor(source));
        }
        double priorEu = sourceHelmet.ecc2Stored(source);
        if (priorEu > 0.0) {
            targetHelmet.ecc2SetStored(target, priorEu);
        }
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return delegate.canCraftInDimensions(width, height);
    }

    @Override
    public ItemStack getResultItem() {
        return delegate.getResultItem();
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
    public ResourceLocation getId() {
        return delegate.getId();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SolarKineticRegistry.SOLAR_HELMET_UPGRADE_SERIALIZER.get();
    }

    /** JSON and network serializer delegating shapeless fields to vanilla's shapeless serializer. */
    public static final class Serializer implements RecipeSerializer<SolarHelmetUpgradeRecipe> {

        @Override
        public SolarHelmetUpgradeRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new SolarHelmetUpgradeRecipe(
                    RecipeSerializer.SHAPELESS_RECIPE.fromJson(id, json));
        }

        @Override
        public @Nullable SolarHelmetUpgradeRecipe fromNetwork(
                ResourceLocation id, FriendlyByteBuf buf) {
            ShapelessRecipe inner = RecipeSerializer.SHAPELESS_RECIPE.fromNetwork(id, buf);
            return inner == null ? null : new SolarHelmetUpgradeRecipe(inner);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, SolarHelmetUpgradeRecipe recipe) {
            RecipeSerializer.SHAPELESS_RECIPE.toNetwork(buf, recipe.delegate);
        }
    }
}
