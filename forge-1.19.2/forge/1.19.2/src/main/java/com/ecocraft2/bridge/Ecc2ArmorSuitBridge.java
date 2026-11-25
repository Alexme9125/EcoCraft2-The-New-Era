package com.ecocraft2.bridge;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Implemented by EcoCraft 2 armor items that count as IC2NE Nano or Quantum armor pieces for
 * complete-suit damage absorption and powered-suit bonuses.
 *
 * <p>Hooked into {@code com.industrialage.ia2.equipment.ArmorEffects} via Mixin.
 */
public interface Ecc2ArmorSuitBridge {

    /** Equivalent IC2NE powered armor suit tier. */
    enum SuitClass {
        NONE,
        NANO,
        QUANTUM
    }

    /** Returns the IC2NE suit class this armor stack participates in. */
    SuitClass ecc2SuitClass(ItemStack stack);

    /** Returns the equipment slot this armor item occupies for complete-suit checks. */
    default EquipmentSlot ecc2ArmorSlot(ItemStack stack) {
        return EquipmentSlot.HEAD;
    }
}
