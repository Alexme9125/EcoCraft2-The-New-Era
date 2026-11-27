package com.ecocraft2.bridge;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.TriState;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** NeoForge 26.x platform helpers used by shared EcoCraft 2 Mixins and bridge classes. */
public final class Ecc2BridgePlatform {

    private Ecc2BridgePlatform() {}

    /** Looks up a registered item by its namespaced ID, or {@code null} if not registered. */
    public static Item findItem(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        var key = Identifier.tryParse(id);
        return key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
    }

    /** Returns the namespaced item registry ID of {@code stack}, or {@code ""} if empty. */
    public static String itemId(ItemStack stack) {
        return stack == null || stack.isEmpty()
                ? ""
                : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** Returns {@code true} if {@code stack} matches the given item tag ID (with or without {@code '#'}). */
    public static boolean hasItemTag(ItemStack stack, String tagId) {
        if (stack == null || stack.isEmpty() || tagId == null || tagId.isEmpty()) {
            return false;
        }
        String raw = tagId.startsWith("#") ? tagId.substring(1) : tagId;
        var key = Identifier.tryParse(raw);
        return key != null && stack.is(TagKey.create(Registries.ITEM, key));
    }

    /** Registers a damageable unstackable item via NeoForge 26.x's {@code DeferredRegister}. */
    public static void registerDurableSimpleItem(
            DeferredRegister.Items items, String path, int durability) {
        items.registerSimpleItem(path, () -> new Item.Properties().durability(durability));
    }

    /**
     * Registers a low-priority {@link PlayerInteractEvent.RightClickBlock} listener so sneaking
     * with third-party wrenches (which may not override {@code doesSneakBypassUse}) still rotates
     * or dismantles EcoCraft 2 machines cleanly without interfering with IC2's own wrenches.
     */
    public static void registerWrenchListener(IEventBus gameBus) {
        gameBus.addListener(
                EventPriority.LOW,
                PlayerInteractEvent.RightClickBlock.class,
                event -> {
                    if (event.isCanceled() || event.getUseBlock() == TriState.FALSE) {
                        return;
                    }
                    var level = event.getLevel();
                    var pos = event.getPos();
                    var state = level.getBlockState(pos);
                    if (!Ecc2WrenchHelper.isEcc2WrenchTarget(state.getBlock())) {
                        return;
                    }
                    if (Ecc2WrenchHelper.tryHandleThirdPartyWrench(
                            state.getBlock(),
                            state,
                            level,
                            pos,
                            event.getEntity(),
                            event.getHand(),
                            event.getFace())) {
                        event.setCanceled(true);
                        event.setCancellationResult(InteractionResult.SUCCESS);
                    }
                });
    }
}
