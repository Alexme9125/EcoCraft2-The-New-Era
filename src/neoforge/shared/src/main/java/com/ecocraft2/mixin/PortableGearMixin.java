package com.ecocraft2.mixin;

import com.ecocraft2.bridge.Ecc2ElectricItem;
import com.industrialage.ia2.audio.IndustrialSounds;
import com.industrialage.ia2.content.PortableEquipment;
import com.industrialage.ia2.equipment.JetpackMount;
import com.industrialage.ia2.gameplay.PortableGear;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Bridges EcoCraft 2 electric items ({@link Ecc2ElectricItem}, such as the three Solar Helmets)
 * into IC2NE's {@link PortableGear} so they work natively in BatBox/CESU/MFE/MFSU charging slots,
 * charge pads, battery packs, right-click battery transfers, and powered armor impact absorption.
 */
@Mixin(value = PortableGear.class, remap = false)
public final class PortableGearMixin {

    @Shadow
    private static PortableEquipment type(ItemStack stack) {
        throw new AssertionError();
    }

    @Shadow
    private static void setStored(ItemStack stack, double amount) {
        throw new AssertionError();
    }

    @Inject(method = "acceptsCharge", at = @At("HEAD"), cancellable = true)
    private static void ecc2$acceptsCharge(
            ItemStack stack, int tier, CallbackInfoReturnable<Boolean> cir) {
        if (stack.getItem() instanceof Ecc2ElectricItem electric) {
            cir.setReturnValue(
                    electric.ecc2Tier(stack) <= tier
                            || (tier >= 1 && JetpackMount.installed(stack)));
        }
    }

    @Inject(method = "canDischarge", at = @At("HEAD"), cancellable = true)
    private static void ecc2$canDischarge(
            ItemStack stack, int tier, CallbackInfoReturnable<Boolean> cir) {
        if (stack.getItem() instanceof Ecc2ElectricItem electric) {
            cir.setReturnValue(electric.ecc2CanProvide(stack) && electric.ecc2Tier(stack) <= tier);
        }
    }

    @Inject(method = "canSupplyCharge", at = @At("HEAD"), cancellable = true)
    private static void ecc2$canSupplyCharge(
            ItemStack stack, int tier, CallbackInfoReturnable<Boolean> cir) {
        if (stack.getItem() instanceof Ecc2ElectricItem electric) {
            cir.setReturnValue(
                    electric.ecc2CanProvide(stack)
                            && electric.ecc2Tier(stack) <= tier
                            && electric.ecc2Stored(stack) > 0.0);
        }
    }

    @Inject(method = "chargeComplete", at = @At("HEAD"), cancellable = true)
    private static void ecc2$chargeComplete(
            ItemStack stack, int tier, CallbackInfoReturnable<Boolean> cir) {
        if (stack.getItem() instanceof Ecc2ElectricItem electric) {
            boolean main = electric.ecc2Tier(stack) <= tier;
            var pack = PortableEquipment.ELECTRIC_JETPACK;
            boolean mount = tier >= pack.tier && JetpackMount.installed(stack);
            cir.setReturnValue(
                    (main || mount)
                            && (!main || electric.ecc2Stored(stack) >= electric.ecc2Capacity(stack))
                            && (!mount || JetpackMount.energy(stack) >= pack.capacity));
        }
    }

    @Inject(method = "charge", at = @At("HEAD"), cancellable = true)
    private static void ecc2$charge(
            ItemStack stack, double offer, int tier, CallbackInfoReturnable<Double> cir) {
        if (stack.getItem() instanceof Ecc2ElectricItem electric) {
            if (stack.getCount() != 1) {
                cir.setReturnValue(0.0);
                return;
            }
            double accepted = electric.ecc2Charge(stack, offer, tier, false, false);
            double remaining = Math.max(0.0, offer - accepted);
            cir.setReturnValue(accepted + JetpackMount.charge(stack, remaining, tier));
        }
    }

    @Inject(
            method = "discharge(Lnet/minecraft/world/item/ItemStack;DDI)D",
            at = @At("HEAD"),
            cancellable = true)
    private static void ecc2$discharge(
            ItemStack stack,
            double requested,
            double bufferCapacity,
            int tier,
            CallbackInfoReturnable<Double> cir) {
        if (stack.getItem() instanceof Ecc2ElectricItem electric) {
            if (stack.getCount() != 1) {
                cir.setReturnValue(0.0);
                return;
            }
            cir.setReturnValue(electric.ecc2Discharge(stack, requested, tier, false, false));
        }
    }

    @Inject(method = "stored", at = @At("HEAD"), cancellable = true)
    private static void ecc2$stored(ItemStack stack, CallbackInfoReturnable<Double> cir) {
        if (stack.getItem() instanceof Ecc2ElectricItem electric) {
            cir.setReturnValue(electric.ecc2Stored(stack));
        }
    }

    @Inject(method = "capacity", at = @At("HEAD"), cancellable = true)
    private static void ecc2$capacity(ItemStack stack, CallbackInfoReturnable<Double> cir) {
        if (stack.getItem() instanceof Ecc2ElectricItem electric) {
            cir.setReturnValue(electric.ecc2Capacity(stack));
        }
    }

    @Inject(method = "electricalItem", at = @At("HEAD"), cancellable = true)
    private static void ecc2$electricalItem(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.getItem() instanceof Ecc2ElectricItem) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "spend", at = @At("HEAD"), cancellable = true)
    private static void ecc2$spend(
            ItemStack stack, double amount, CallbackInfoReturnable<Boolean> cir) {
        if (stack.getItem() instanceof Ecc2ElectricItem electric) {
            if (electric.ecc2CanProvide(stack) || !Double.isFinite(amount) || amount <= 0.0) {
                cir.setReturnValue(false);
                return;
            }
            double prior = electric.ecc2Stored(stack);
            if (prior < amount) {
                cir.setReturnValue(false);
                return;
            }
            electric.ecc2SetStored(stack, prior - amount);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "restoreEnergy", at = @At("HEAD"), cancellable = true)
    private static void ecc2$restoreEnergy(ItemStack stack, double amount, CallbackInfo ci) {
        if (stack.getItem() instanceof Ecc2ElectricItem electric) {
            double clamped =
                    Double.isFinite(amount)
                            ? Math.max(0.0, Math.min(electric.ecc2Capacity(stack), amount))
                            : 0.0;
            electric.ecc2SetStored(stack, clamped);
            ci.cancel();
        }
    }

    @Inject(method = "transfer", at = @At("HEAD"), cancellable = true)
    private static void ecc2$transferToExternalConsumer(
            ItemStack source, ItemStack target, CallbackInfo ci) {
        if (!(target.getItem() instanceof Ecc2ElectricItem consumer)
                || consumer.ecc2CanProvide(target)
                || target.getCount() != 1) {
            return;
        }
        var provider = type(source);
        if (provider == null || !provider.canProvide()) {
            return;
        }
        if (provider.kind == PortableEquipment.Kind.DISPOSABLE) {
            double storedTarget = consumer.ecc2Stored(target);
            double room = consumer.ecc2Capacity(target) - storedTarget;
            if (provider.tier >= consumer.ecc2Tier(target) && room >= provider.capacity) {
                consumer.ecc2SetStored(target, storedTarget + provider.capacity);
                source.shrink(1);
            }
            ci.cancel();
            return;
        }
        double sourceStored = PortableGear.stored(source);
        double accepted =
                consumer.ecc2Charge(
                        target, Math.min(provider.rate, sourceStored), provider.tier, false, false);
        if (accepted > 0.0) {
            setStored(source, sourceStored - accepted);
        }
        ci.cancel();
    }

    @Inject(method = "manualTransfer", at = @At("HEAD"), cancellable = true)
    private static void ecc2$manualTransferToExternalConsumer(
            PlayerInteractEvent.RightClickItem event, CallbackInfo ci) {
        var source = event.getItemStack();
        var provider = type(source);
        if (provider == null || !provider.canProvide() || provider.wearable()) {
            return;
        }
        var player = event.getEntity();
        var target =
                player.getItemInHand(
                        event.getHand() == InteractionHand.MAIN_HAND
                                ? InteractionHand.OFF_HAND
                                : InteractionHand.MAIN_HAND);
        if (!(target.getItem() instanceof Ecc2ElectricItem consumer)
                || consumer.ecc2CanProvide(target)
                || target.getCount() != 1) {
            return;
        }
        if (provider.tier < consumer.ecc2Tier(target)
                || PortableGear.stored(source) <= 0.0
                || consumer.ecc2Stored(target) >= consumer.ecc2Capacity(target)) {
            return;
        }
        if (player.level() instanceof ServerLevel level) {
            double before = consumer.ecc2Stored(target);
            if (provider.kind == PortableEquipment.Kind.DISPOSABLE) {
                double room = consumer.ecc2Capacity(target) - before;
                if (room >= provider.capacity) {
                    consumer.ecc2SetStored(target, before + provider.capacity);
                    source.shrink(1);
                }
            } else {
                double sourceStored = PortableGear.stored(source);
                double accepted =
                        consumer.ecc2Charge(
                                target,
                                Math.min(provider.rate, sourceStored),
                                provider.tier,
                                false,
                                false);
                if (accepted > 0.0) {
                    setStored(source, sourceStored - accepted);
                }
            }
            if (consumer.ecc2Stored(target) > before) {
                IndustrialSounds.play(level, player.blockPosition(), "item.battery.use");
            }
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
        ci.cancel();
    }
}
