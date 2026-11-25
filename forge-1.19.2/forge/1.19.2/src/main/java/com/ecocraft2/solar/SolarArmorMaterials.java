package com.ecocraft2.solar;

import com.ecocraft2.EcoCraft2;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;

/** Forge 1.19.2 armor materials for the three EcoCraft 2 Solar Helmets. */
public enum SolarArmorMaterials implements ArmorMaterial {
    ADVANCED_SOLAR("advanced_solar", 3, 2.0f),
    HYBRID_SOLAR("hybrid_solar", 3, 3.0f),
    ULTIMATE_HYBRID_SOLAR("ultimate_hybrid_solar", 3, 3.0f);

    private final String name;
    private final int defense;
    private final float toughness;

    SolarArmorMaterials(String name, int defense, float toughness) {
        this.name = EcoCraft2.MOD_ID + ":" + name;
        this.defense = defense;
        this.toughness = toughness;
    }

    @Override
    public int getDurabilityForSlot(EquipmentSlot slot) {
        return 0;
    }

    @Override
    public int getDefenseForSlot(EquipmentSlot slot) {
        return slot == EquipmentSlot.HEAD ? defense : 0;
    }

    @Override
    public int getEnchantmentValue() {
        return 0;
    }

    @Override
    public SoundEvent getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_IRON;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.EMPTY;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public float getToughness() {
        return toughness;
    }

    @Override
    public float getKnockbackResistance() {
        return 0.0f;
    }
}
