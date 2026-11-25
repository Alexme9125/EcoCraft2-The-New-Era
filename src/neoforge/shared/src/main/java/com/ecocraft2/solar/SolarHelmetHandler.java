package com.ecocraft2.solar;

import com.ecocraft2.common.config.Ecc2ConfigDefaults;
import com.ecocraft2.common.solar.SolarHelmetEngine;
import com.ecocraft2.common.solar.SolarHelmetSpec;
import com.ecocraft2.config.Ecc2Config;
import com.ecocraft2.kinetic.AdvancedKineticBlock;
import com.industrialage.ia2.gameplay.PortableGear;
import com.industrialage.ia2.industrial.Radiation;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Runtime gameplay handlers for the three Solar Helmets and all Solar/Kinetic item tooltips:
 *
 * <ul>
 *   <li>Per-player isolated {@link SolarHelmetEngine} sky checks &amp; charging priority
 *       ({@code feet -> legs -> chest -> offhand -> main -> self}).
 *   <li>Full Quantum Helmet abilities on Hybrid &amp; Ultimate helmets (underwater oxygen refill,
 *       Poison/Wither/IC2 Radiation clearing, auto-feeding {@code ic2:filled_tin_can}).
 *   <li>Shapeless Hybrid → Ultimate upgrade dye color &amp; EU preservation.
 *   <li>Armor defense attributes &amp; rich tooltips across 1.21.1 and 26.x.
 * </ul>
 */
public final class SolarHelmetHandler {

    private static final Map<UUID, TrackedHelmet> PLAYER_ENGINES = new ConcurrentHashMap<>();

    private record TrackedHelmet(
            SolarHelmetSpec spec, int dayPower, int nightPower, SolarHelmetEngine engine) {}

    private SolarHelmetHandler() {}

    /** Subscribes solar helmet gameplay, crafting, attribute, and tooltip listeners. */
    public static void install(IEventBus gameBus) {
        gameBus.addListener(PlayerTickEvent.Post.class, SolarHelmetHandler::onPlayerTick);
        gameBus.addListener(
                PlayerEvent.PlayerLoggedOutEvent.class,
                event -> PLAYER_ENGINES.remove(event.getEntity().getUUID()));
        gameBus.addListener(PlayerEvent.ItemCraftedEvent.class, SolarHelmetHandler::onItemCrafted);
        gameBus.addListener(
                ItemAttributeModifierEvent.class, SolarHelmetHandler::onArmorAttributes);
        gameBus.addListener(ItemTooltipEvent.class, SolarHelmetHandler::onItemTooltip);
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!(head.getItem() instanceof SolarHelmetItem helmet)) {
            PLAYER_ENGINES.remove(player.getUUID());
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        SolarHelmetSpec spec = helmet.spec();
        SolarHelmetEngine engine = engineFor(player.getUUID(), spec);

        boolean hasSky = level.dimensionType().hasSkyLight();
        var headPos = player.blockPosition().above();
        boolean seesSky = level.canSeeSky(headPos);
        boolean isDay = SolarPlatform.isDay(level);
        boolean isRaining = level.isRaining() || level.isThundering();
        boolean canRain = level.getBiome(player.blockPosition()).value().hasPrecipitation();

        engine.tickGeneration(
                hasSky,
                seesSky,
                isDay,
                isRaining,
                canRain,
                Ecc2Config.helmetsChargeArmorOnly(),
                (slot, offerEu, helmetTier) ->
                        routeCharge(player, head, helmet, slot, offerEu, helmetTier));

        if (spec.hasQuantumAbilities()) {
            applyQuantumAbilities(player, head, helmet, engine);
        }
    }

    private static SolarHelmetEngine engineFor(UUID playerId, SolarHelmetSpec spec) {
        int day = SolarHelmetLogic.configuredDayPower(spec);
        int night = SolarHelmetLogic.configuredNightPower(spec);
        return PLAYER_ENGINES
                .compute(
                        playerId,
                        (id, existing) -> {
                            if (existing != null
                                    && existing.spec() == spec
                                    && existing.dayPower() == day
                                    && existing.nightPower() == night) {
                                return existing;
                            }
                            return new TrackedHelmet(
                                    spec,
                                    day,
                                    night,
                                    new SolarHelmetEngine(
                                            spec,
                                            day,
                                            night,
                                            Ecc2ConfigDefaults.SOLAR_SKY_CHECK_INTERVAL_TICKS));
                        })
                .engine();
    }

    private static double routeCharge(
            ServerPlayer player,
            ItemStack headStack,
            SolarHelmetItem helmet,
            SolarHelmetEngine.ChargeTargetSlot target,
            double offerEu,
            int helmetTier) {
        return switch (target) {
            case FEET -> chargeStack(player.getItemBySlot(EquipmentSlot.FEET), offerEu, helmetTier);
            case LEGS -> chargeStack(player.getItemBySlot(EquipmentSlot.LEGS), offerEu, helmetTier);
            case CHEST ->
                    chargeStack(player.getItemBySlot(EquipmentSlot.CHEST), offerEu, helmetTier);
            case OFFHAND -> chargeStack(player.getOffhandItem(), offerEu, helmetTier);
            case MAIN -> chargeMainInventory(player, offerEu, helmetTier);
            case SELF -> helmet.ecc2Charge(headStack, offerEu, helmetTier, false, false);
        };
    }

    private static double chargeStack(ItemStack stack, double offerEu, int tier) {
        if (stack.isEmpty() || offerEu <= 0.0) {
            return 0.0;
        }
        return PortableGear.charge(stack, offerEu, tier);
    }

    private static double chargeMainInventory(ServerPlayer player, double offerEu, int tier) {
        double remaining = offerEu;
        var inv = player.getInventory();
        int mainSize = Math.min(36, inv.getContainerSize());
        for (int i = 0; i < mainSize && remaining > 0.0; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty()) {
                double accepted = PortableGear.charge(stack, remaining, tier);
                if (accepted > 0.0) {
                    remaining -= accepted;
                }
            }
        }
        return offerEu - remaining;
    }

    private static void applyQuantumAbilities(
            ServerPlayer player,
            ItemStack headStack,
            SolarHelmetItem helmet,
            SolarHelmetEngine engine) {
        double storedEu = helmet.ecc2Stored(headStack);
        if (storedEu <= 0.0) {
            return;
        }

        int air = player.getAirSupply();
        boolean hasWither = player.hasEffect(MobEffects.WITHER);
        boolean hasPoison = player.hasEffect(MobEffects.POISON);
        boolean hasRadiation = Radiation.has(player);
        int foodLevel = player.getFoodData().getFoodLevel();
        ItemStack filledCan = findFilledCan(player);

        var plan =
                engine.evaluateQuantumAbilities(
                        storedEu,
                        air,
                        hasWither,
                        hasPoison,
                        hasRadiation,
                        foodLevel,
                        !filledCan.isEmpty());
        if (!plan.hasAnyAction()) {
            return;
        }

        double spent = 0.0;
        if (plan.refillOxygen()) {
            player.setAirSupply(SolarHelmetEngine.OXYGEN_REFILL_TARGET_AIR);
            spent += SolarHelmetEngine.OXYGEN_REFILL_EU_COST;
        }
        if (plan.cureWither()) {
            player.removeEffect(MobEffects.WITHER);
            spent += SolarHelmetEngine.CURE_WITHER_EU_COST;
        }
        if (plan.curePoison()) {
            player.removeEffect(MobEffects.POISON);
            spent += SolarHelmetEngine.CURE_POISON_EU_COST;
        }
        if (plan.cureRadiation()) {
            Radiation.clear(player);
            spent += SolarHelmetEngine.CURE_RADIATION_EU_COST;
        }
        if (plan.autoFeed() && !filledCan.isEmpty()) {
            filledCan.shrink(1);
            player.getFoodData().eat(1, 0.6F);
            Item emptyCan = SolarPlatform.findItem("ic2:tin_can");
            if (emptyCan != null && emptyCan != Items.AIR) {
                ItemStack tin = new ItemStack(emptyCan);
                if (!player.getInventory().add(tin)) {
                    player.drop(tin, false);
                }
            }
            spent += SolarHelmetEngine.AUTO_FEED_EU_COST;
        }

        if (spent > 0.0) {
            helmet.ecc2SetStored(headStack, storedEu - spent);
        }
    }

    private static ItemStack findFilledCan(ServerPlayer player) {
        Item filledCanItem = SolarPlatform.findItem("ic2:filled_tin_can");
        if (filledCanItem == null || filledCanItem == Items.AIR) {
            return ItemStack.EMPTY;
        }
        ItemStack offhand = player.getOffhandItem();
        if (offhand.is(filledCanItem) && !offhand.isEmpty()) {
            return offhand;
        }
        var inv = player.getInventory();
        int mainSize = Math.min(36, inv.getContainerSize());
        for (int i = 0; i < mainSize; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(filledCanItem) && !stack.isEmpty()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        ItemStack output = event.getCrafting();
        if (!(output.getItem() instanceof SolarHelmetItem outHelmet)
                || outHelmet.spec() != SolarHelmetSpec.ULTIMATE_HYBRID) {
            return;
        }
        var matrix = event.getInventory();
        for (int i = 0; i < matrix.getContainerSize(); i++) {
            ItemStack ingredient = matrix.getItem(i);
            if (ingredient.getItem() instanceof SolarHelmetItem inHelmet
                    && inHelmet.spec() == SolarHelmetSpec.HYBRID) {
                // Preserve leather dye color across Hybrid -> Ultimate shapeless upgrade.
                var dyed = ingredient.get(DataComponents.DYED_COLOR);
                if (dyed != null) {
                    output.set(DataComponents.DYED_COLOR, dyed);
                }
                // Preserve accumulated EU charge cleanly as well.
                double priorEu = inHelmet.ecc2Stored(ingredient);
                if (priorEu > 0.0) {
                    outHelmet.ecc2SetStored(output, priorEu);
                }
                break;
            }
        }
    }

    private static void onArmorAttributes(ItemAttributeModifierEvent event) {
        if (!(event.getItemStack().getItem() instanceof SolarHelmetItem helmet)) {
            return;
        }
        boolean quantum = helmet.spec().hasQuantumAbilities();
        double armor = 3.0;
        double toughness = quantum ? 3.0 : 2.0;
        event.addModifier(
                Attributes.ARMOR,
                SolarPlatform.armorModifier("solar_helmet_armor", armor),
                EquipmentSlotGroup.HEAD);
        event.addModifier(
                Attributes.ARMOR_TOUGHNESS,
                SolarPlatform.armorModifier("solar_helmet_toughness", toughness),
                EquipmentSlotGroup.HEAD);
    }

    private static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        List<Component> tooltip = event.getToolTip();
        if (stack.getItem() instanceof SolarHelmetItem helmet) {
            SolarHelmetSpec spec = helmet.spec();
            tooltip.add(
                    Component.translatable(
                                    "tooltip.ecc2.helmet.energy",
                                    Ecc2DataSync.formatInt(Math.round(helmet.ecc2Stored(stack))),
                                    Ecc2DataSync.formatInt(spec.capacity()),
                                    spec.tier())
                            .withStyle(ChatFormatting.AQUA));
            tooltip.add(
                    Component.translatable(
                                    "tooltip.ecc2.helmet.generation",
                                    Ecc2DataSync.formatInt(SolarHelmetLogic.configuredDayPower(spec)),
                                    Ecc2DataSync.formatInt(
                                            SolarHelmetLogic.configuredNightPower(spec)))
                            .withStyle(ChatFormatting.GOLD));
            tooltip.add(
                    Component.translatable(
                                    spec.hasQuantumAbilities()
                                            ? "tooltip.ecc2.helmet.quantum_suit"
                                            : "tooltip.ecc2.helmet.nano_suit")
                            .withStyle(ChatFormatting.GRAY));
            if (spec.isDyeable()) {
                tooltip.add(
                        Component.translatable("tooltip.ecc2.helmet.dyeable")
                                .withStyle(ChatFormatting.DARK_AQUA));
            }
            return;
        }
        if (stack.getItem() instanceof BlockItem blockItem) {
            var block = blockItem.getBlock();
            if (block instanceof SolarPanelBlock solarBlock) {
                var tier = solarBlock.getSolarTier();
                int day =
                        switch (tier) {
                            case ADVANCED -> Ecc2Config.advancedSolarGenDay();
                            case HYBRID -> Ecc2Config.hybridSolarGenDay();
                            case ULTIMATE_HYBRID -> Ecc2Config.ultimateSolarGenDay();
                            case QUANTUM -> Ecc2Config.quantumSolarGenDay();
                        };
                int night =
                        switch (tier) {
                            case ADVANCED -> Ecc2Config.advancedSolarGenNight();
                            case HYBRID -> Ecc2Config.hybridSolarGenNight();
                            case ULTIMATE_HYBRID -> Ecc2Config.ultimateSolarGenNight();
                            case QUANTUM -> Ecc2Config.quantumSolarGenNight();
                        };
                int cap =
                        switch (tier) {
                            case ADVANCED -> Ecc2Config.advancedSolarStorage();
                            case HYBRID -> Ecc2Config.hybridSolarStorage();
                            case ULTIMATE_HYBRID -> Ecc2Config.ultimateSolarStorage();
                            case QUANTUM -> Ecc2Config.quantumSolarStorage();
                        };
                int outTier =
                        switch (tier) {
                            case ADVANCED -> Ecc2Config.advancedSolarTier();
                            case HYBRID -> Ecc2Config.hybridSolarTier();
                            case ULTIMATE_HYBRID -> Ecc2Config.ultimateSolarTier();
                            case QUANTUM -> Ecc2Config.quantumSolarTier();
                        };
                tooltip.add(
                        Component.translatable(
                                        "tooltip.ecc2.solar.generation",
                                        Ecc2DataSync.formatInt(day),
                                        Ecc2DataSync.formatInt(night))
                                .withStyle(ChatFormatting.GOLD));
                tooltip.add(
                        Component.translatable(
                                        "tooltip.ecc2.solar.storage",
                                        Ecc2DataSync.formatInt(cap),
                                        outTier,
                                        Ecc2DataSync.formatInt(
                                                Math.round(
                                                        com.ecocraft2.common.solar.SolarTier
                                                                .packetSize(outTier))))
                                .withStyle(ChatFormatting.AQUA));
            } else if (block instanceof QuantumGeneratorBlock) {
                tooltip.add(
                        Component.translatable("tooltip.ecc2.quantum_generator.creative")
                                .withStyle(ChatFormatting.LIGHT_PURPLE));
                tooltip.add(
                        Component.translatable("tooltip.ecc2.quantum_generator.redstone")
                                .withStyle(ChatFormatting.GRAY));
            } else if (block instanceof MolecularTransformerBlock) {
                tooltip.add(
                        Component.translatable("tooltip.ecc2.molecular_transformer.desc")
                                .withStyle(ChatFormatting.AQUA));
            } else if (block instanceof AdvancedKineticBlock kineticBlock) {
                String tierKey =
                        switch (kineticBlock.getKineticTier()) {
                            case DURABLE -> "tooltip.ecc2.kinetic.durable";
                            case OVERCLOCKED -> "tooltip.ecc2.kinetic.overclocked";
                            case PRECISION -> "tooltip.ecc2.kinetic.precision";
                            default -> "tooltip.ecc2.kinetic.durable";
                        };
                tooltip.add(Component.translatable(tierKey).withStyle(ChatFormatting.AQUA));
            }
        }
    }
}
