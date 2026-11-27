package com.ecocraft2.bridge;

import com.ecocraft2.EcoCraft2;
import com.ecocraft2.common.config.Ecc2Ids;
import com.ecocraft2.common.gas.GasFuelSpec;
import com.ecocraft2.common.giant.GiantCornerPort;
import com.ecocraft2.common.giant.PortAccessMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Forge 1.19.2 registry and identifier helpers for EcoCraft 2 bridges and gameplay modules. */
public final class Ecc2BridgePlatform {

    private Ecc2BridgePlatform() {}

    /** Builds a {@link ResourceLocation} in the {@code ecc2} namespace. */
    public static ResourceLocation rl(String path) {
        return new ResourceLocation(EcoCraft2.MOD_ID, path);
    }

    /** Looks up a registered item by its namespaced ID, or {@code null} if not registered. */
    public static Item findItem(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        var key = ResourceLocation.tryParse(id);
        if (key == null || !ForgeRegistries.ITEMS.containsKey(key)) {
            return null;
        }
        Item item = ForgeRegistries.ITEMS.getValue(key);
        return item == null || item == Items.AIR ? null : item;
    }

    /** Looks up a registered fluid by its namespaced ID, or {@link Fluids#EMPTY} if absent. */
    public static Fluid findFluid(String id) {
        if (id == null || id.isEmpty()) {
            return Fluids.EMPTY;
        }
        var key = ResourceLocation.tryParse(id);
        if (key == null || !ForgeRegistries.FLUIDS.containsKey(key)) {
            return Fluids.EMPTY;
        }
        Fluid fluid = ForgeRegistries.FLUIDS.getValue(key);
        return fluid == null ? Fluids.EMPTY : fluid;
    }

    /** Creates a {@link FluidStack} from a namespaced fluid ID and mB amount. */
    public static FluidStack fluidStack(String id, int amount) {
        if (amount <= 0) {
            return FluidStack.EMPTY;
        }
        Fluid fluid = findFluid(id);
        return fluid == Fluids.EMPTY ? FluidStack.EMPTY : new FluidStack(fluid, amount);
    }

    /** Returns the namespaced item registry ID of {@code stack}, or {@code ""} if empty. */
    public static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        var key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }

    /** Returns the namespaced block registry ID of {@code block}, or {@code ""} if unregistered. */
    public static String blockId(Block block) {
        if (block == null) {
            return "";
        }
        var key = ForgeRegistries.BLOCKS.getKey(block);
        return key == null ? "" : key.toString();
    }

    /** Returns the namespaced fluid registry ID of {@code fluid}, or {@code ""} if empty. */
    public static String fluidId(Fluid fluid) {
        if (fluid == null || fluid == Fluids.EMPTY) {
            return "";
        }
        var key = ForgeRegistries.FLUIDS.getKey(fluid);
        return key == null ? "" : key.toString();
    }

    /** Returns the namespaced fluid registry ID of {@code stack}, or {@code ""} if empty. */
    public static String fluidId(FluidStack stack) {
        return stack == null || stack.isEmpty() ? "" : fluidId(stack.getFluid());
    }

    /** Returns {@code true} if {@code stack} matches the given item tag ID (with or without {@code '#'}). */
    public static boolean hasItemTag(ItemStack stack, String tagId) {
        if (stack == null || stack.isEmpty() || tagId == null || tagId.isEmpty()) {
            return false;
        }
        String raw = tagId.startsWith("#") ? tagId.substring(1) : tagId;
        var key = ResourceLocation.tryParse(raw);
        return key != null && stack.is(TagKey.create(Registry.ITEM_REGISTRY, key));
    }

    /** Returns all registered items currently matching {@code tagId} as stacks of {@code count}. */
    public static List<ItemStack> allItemsInTag(String tagId, int count) {
        if (tagId == null || tagId.isEmpty() || count <= 0) {
            return List.of();
        }
        String raw = tagId.startsWith("#") ? tagId.substring(1) : tagId;
        var key = ResourceLocation.tryParse(raw);
        if (key == null) {
            return List.of();
        }
        var tags = ForgeRegistries.ITEMS.tags();
        if (tags == null) {
            return List.of();
        }
        var tag = tags.getTag(TagKey.create(Registry.ITEM_REGISTRY, key));
        var result = new ArrayList<ItemStack>();
        for (Item item : tag) {
            if (item != null && item != Items.AIR) {
                result.add(new ItemStack(item, count));
            }
        }
        return result;
    }

    /** Returns {@code true} if {@code fluid} matches the given fluid tag ID (with or without {@code '#'}). */
    public static boolean hasFluidTag(Fluid fluid, String tagId) {
        if (fluid == null || fluid == Fluids.EMPTY || tagId == null || tagId.isEmpty()) {
            return false;
        }
        String raw = tagId.startsWith("#") ? tagId.substring(1) : tagId;
        var key = ResourceLocation.tryParse(raw);
        return key != null && fluid.defaultFluidState().is(TagKey.create(Registry.FLUID_REGISTRY, key));
    }

    /** Returns {@code true} if {@code stack}'s fluid matches the given fluid tag ID. */
    public static boolean hasFluidTag(FluidStack stack, String tagId) {
        return stack != null && !stack.isEmpty() && hasFluidTag(stack.getFluid(), tagId);
    }

    /** Returns {@code true} if {@code fluid} is vanilla water or tagged {@code #minecraft:water}/{@code #forge:water}. */
    public static boolean isWaterFluid(Fluid fluid) {
        if (fluid == null || fluid == Fluids.EMPTY) {
            return false;
        }
        return fluid == Fluids.WATER
                || fluid.defaultFluidState().is(FluidTags.WATER)
                || hasFluidTag(fluid, Ecc2Ids.commonTag("forge", Ecc2Ids.COMMON_TAG_WATER));
    }

    /** Returns {@code true} if {@code stack} holds water or a water-tagged fluid. */
    public static boolean isWaterFluid(FluidStack stack) {
        return stack != null && !stack.isEmpty() && isWaterFluid(stack.getFluid());
    }

    /** Returns {@code true} if {@code stack} holds culture medium or a {@code #forge:culture_medium} fluid. */
    public static boolean isCultureMediumFluid(FluidStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return Ecc2Ids.CULTURE_MEDIUM_ID.equals(fluidId(stack))
                || hasFluidTag(stack, Ecc2Ids.commonTag("forge", Ecc2Ids.COMMON_TAG_CULTURE_MEDIUM));
    }

    /** Resolves a Forge {@link FluidStack} to a {@link GasFuelSpec} via exact ID or Forge/EcC2 fluid tags. */
    public static Optional<GasFuelSpec> resolveGasFuel(FluidStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        Fluid fluid = stack.getFluid();
        return GasFuelSpec.resolve(fluidId(fluid), "forge", tag -> hasFluidTag(fluid, tag));
    }

    /** Returns a copy of {@code stack} with stack count set to {@code count}. */
    public static ItemStack copyWithCount(ItemStack stack, int count) {
        if (stack == null || stack.isEmpty() || count <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = stack.copy();
        copy.setCount(count);
        return copy;
    }

    /** Returns the localized display name for a gas fuel (or empty). */
    public static Component fuelName(GasFuelSpec fuel) {
        if (fuel == null) {
            return Component.translatable("gui.ecc2.gas.empty");
        }
        return switch (fuel) {
            case WATER_GAS -> Component.translatable("gui.ecc2.gas.water_gas");
            case NATURAL_GAS -> Component.translatable("gui.ecc2.gas.natural_gas");
            case BIOGAS -> Component.translatable("gui.ecc2.gas.biogas");
        };
    }

    /** Returns the localized display name for a giant-machine lower-corner access port. */
    public static Component portName(GiantCornerPort port) {
        return Component.translatable("gui.ecc2.port." + port.name().toLowerCase(Locale.ROOT));
    }

    /** Returns the localized display name for a giant-machine port access mode. */
    public static Component modeName(PortAccessMode mode) {
        return Component.translatable("gui.ecc2.port.mode." + mode.name().toLowerCase(Locale.ROOT));
    }
}
