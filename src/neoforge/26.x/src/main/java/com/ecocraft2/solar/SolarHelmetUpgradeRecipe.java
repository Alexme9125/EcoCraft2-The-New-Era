package com.ecocraft2.solar;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.level.Level;

/**
 * NeoForge 26.x shapeless crafting recipe wrapper that preserves stored EU and leather dye color
 * when upgrading {@code ecc2:hybrid_solar_helmet} to {@code ecc2:ultimate_hybrid_solar_helmet}
 * across normal clicks, Shift-clicks, crafting previews, and Crafter blocks.
 */
public final class SolarHelmetUpgradeRecipe implements CraftingRecipe {

    public static final MapCodec<SolarHelmetUpgradeRecipe> MAP_CODEC =
            ShapelessRecipe.MAP_CODEC.xmap(
                    SolarHelmetUpgradeRecipe::new, SolarHelmetUpgradeRecipe::delegate);

    public static final StreamCodec<RegistryFriendlyByteBuf, SolarHelmetUpgradeRecipe>
            STREAM_CODEC =
                    ShapelessRecipe.STREAM_CODEC.map(
                            SolarHelmetUpgradeRecipe::new, SolarHelmetUpgradeRecipe::delegate);

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
    public ItemStack assemble(CraftingInput input) {
        ItemStack result = delegate.assemble(input);
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
    public CraftingBookCategory category() {
        return delegate.category();
    }

    @Override
    public String group() {
        return delegate.group();
    }

    @Override
    public boolean showNotification() {
        return delegate.showNotification();
    }

    @Override
    public PlacementInfo placementInfo() {
        return delegate.placementInfo();
    }

    @Override
    public List<RecipeDisplay> display() {
        return delegate.display();
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return delegate.recipeBookCategory();
    }

    @Override
    public RecipeSerializer<? extends CraftingRecipe> getSerializer() {
        return SolarKineticModule.SOLAR_HELMET_UPGRADE_SERIALIZER.get();
    }
}
