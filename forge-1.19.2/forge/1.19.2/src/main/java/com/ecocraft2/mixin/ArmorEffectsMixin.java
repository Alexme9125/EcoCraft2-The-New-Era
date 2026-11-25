package com.ecocraft2.mixin;

import com.ecocraft2.bridge.Ecc2ArmorSuitBridge;
import com.industrialage.ia2.equipment.ArmorEffects;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Allows EcoCraft 2 armor items implementing {@link Ecc2ArmorSuitBridge} (Advanced Solar Helmet as
 * Nano; Hybrid & Ultimate Hybrid Solar Helmets as Quantum) to complete IC2NE Nano/Quantum sets.
 */
@Mixin(value = ArmorEffects.class, remap = false)
public final class ArmorEffectsMixin {

    @Unique
    private static final Logger ECC2_LOG = LoggerFactory.getLogger(ArmorEffectsMixin.class);

    @Unique
    private static final EquipmentSlot[] ECC2_ARMOR_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    @Unique private static volatile Object ecc2$nanoSuit;
    @Unique private static volatile Object ecc2$quantumSuit;
    @Unique private static volatile MethodHandle ecc2$suitLookup;
    @Unique private static volatile boolean ecc2$handlesResolved;
    @Unique private static volatile boolean ecc2$handlesFailed;

    @Inject(method = "suit", at = @At("HEAD"), cancellable = true)
    private static void ecc2$suitForBridgeArmor(
            ItemStack stack, CallbackInfoReturnable<Object> cir) {
        if (stack.getItem() instanceof Ecc2ArmorSuitBridge bridge) {
            cir.setReturnValue(ecc2$toNativeSuit(bridge.ecc2SuitClass(stack)));
        }
    }

    @Inject(method = "completeSuit", at = @At("HEAD"), cancellable = true)
    private static void ecc2$completeSuitWithBridgeArmor(
            LivingEntity wearer, CallbackInfoReturnable<Object> cir) {
        boolean hasBridgePiece = false;
        for (var slot : ECC2_ARMOR_SLOTS) {
            if (wearer.getItemBySlot(slot).getItem() instanceof Ecc2ArmorSuitBridge) {
                hasBridgePiece = true;
                break;
            }
        }
        if (!hasBridgePiece) {
            return;
        }
        Object material = ecc2$resolveSuit(wearer.getItemBySlot(EquipmentSlot.HEAD));
        if (material == null) {
            cir.setReturnValue(null);
            return;
        }
        for (var slot : ECC2_ARMOR_SLOTS) {
            var stack = wearer.getItemBySlot(slot);
            if (ecc2$resolveSuit(stack) != material) {
                cir.setReturnValue(null);
                return;
            }
            boolean validPiece =
                    (stack.getItem() instanceof ArmorEffects.Wearable wearable
                                    && wearable.piece().slot() == slot)
                            || (stack.getItem() instanceof Ecc2ArmorSuitBridge bridge
                                    && bridge.ecc2ArmorSlot(stack) == slot);
            if (!validPiece) {
                cir.setReturnValue(null);
                return;
            }
        }
        cir.setReturnValue(material);
    }

    @Unique
    private static Object ecc2$toNativeSuit(Ecc2ArmorSuitBridge.SuitClass suitClass) {
        if (suitClass == null || suitClass == Ecc2ArmorSuitBridge.SuitClass.NONE) {
            return null;
        }
        if (!ecc2$ensureHandles()) {
            return null;
        }
        return switch (suitClass) {
            case NANO -> ecc2$nanoSuit;
            case QUANTUM -> ecc2$quantumSuit;
            case NONE -> null;
        };
    }

    @Unique
    private static Object ecc2$resolveSuit(ItemStack stack) {
        if (stack.getItem() instanceof Ecc2ArmorSuitBridge bridge) {
            return ecc2$toNativeSuit(bridge.ecc2SuitClass(stack));
        }
        if (!ecc2$ensureHandles()) {
            return null;
        }
        try {
            return ecc2$suitLookup.invoke(stack);
        } catch (Throwable failure) {
            ECC2_LOG.error("Failed to resolve IC2NE ArmorEffects.suit", failure);
            return null;
        }
    }

    @Unique
    private static boolean ecc2$ensureHandles() {
        if (ecc2$handlesResolved) {
            return true;
        }
        if (ecc2$handlesFailed) {
            return false;
        }
        try {
            var lookup = MethodHandles.lookup();
            var suitClass = Class.forName("com.industrialage.ia2.equipment.ArmorEffects$Suit");
            var suitLookup = MethodHandles.privateLookupIn(suitClass, lookup);
            ecc2$nanoSuit = suitLookup.findStaticGetter(suitClass, "NANO", suitClass).invoke();
            ecc2$quantumSuit =
                    suitLookup.findStaticGetter(suitClass, "QUANTUM", suitClass).invoke();
            var effectsLookup = MethodHandles.privateLookupIn(ArmorEffects.class, lookup);
            ecc2$suitLookup =
                    effectsLookup.findStatic(
                            ArmorEffects.class,
                            "suit",
                            MethodType.methodType(suitClass, ItemStack.class));
            ecc2$handlesResolved = true;
            return true;
        } catch (Throwable failure) {
            ecc2$handlesFailed = true;
            ECC2_LOG.error("Failed to bind ArmorEffects.Suit handles", failure);
            return false;
        }
    }
}
