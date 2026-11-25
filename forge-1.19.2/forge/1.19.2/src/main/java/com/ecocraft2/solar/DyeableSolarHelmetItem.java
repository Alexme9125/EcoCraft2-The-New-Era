package com.ecocraft2.solar;

import com.ecocraft2.EcoCraft2;
import com.ecocraft2.common.solar.SolarHelmetSpec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Dyeable Solar Helmet (Hybrid & Ultimate Hybrid tiers) implementing {@link DyeableLeatherItem}
 * with default white tint (preserving original pixel art when undyed) and overlay armor texture.
 */
public final class DyeableSolarHelmetItem extends SolarHelmetItem implements DyeableLeatherItem {

    private static final int DEFAULT_UNDYED_COLOR = 0xFFFFFF;
    private final String baseTextureName;

    public DyeableSolarHelmetItem(
            ArmorMaterial material, SolarHelmetSpec spec, String baseTextureName) {
        super(material, spec, baseTextureName);
        this.baseTextureName = baseTextureName;
    }

    @Override
    public int getColor(ItemStack stack) {
        CompoundTag display = stack.getTagElement(TAG_DISPLAY);
        return display != null && display.contains(TAG_COLOR, 99)
                ? display.getInt(TAG_COLOR)
                : DEFAULT_UNDYED_COLOR;
    }

    @Override
    public @Nullable String getArmorTexture(
            ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        String suffix = "overlay".equals(type) ? "_layer_1_overlay.png" : "_layer_1.png";
        return EcoCraft2.MOD_ID + ":textures/models/armor/" + baseTextureName + suffix;
    }
}
