package com.ecocraft2.solar;

import com.ecocraft2.EcoCraft2;
import com.ecocraft2.bridge.Ecc2ArmorSuitBridge;
import com.ecocraft2.bridge.Ecc2BridgePlatform;
import com.ecocraft2.bridge.Ecc2ElectricItem;
import com.ecocraft2.bridge.Ecc2EnergyHelper;
import com.ecocraft2.common.config.Ecc2ConfigDefaults;
import com.ecocraft2.common.solar.SolarHelmetEngine;
import com.ecocraft2.common.solar.SolarHelmetSpec;
import com.ecocraft2.config.Ecc2Config;
import com.industrialage.ia2.industrial.Radiation;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;

/**
 * Rechargeable electric Solar Helmet bridging IC2NE's {@code ForgeGear} & {@code ArmorEffects}
 * (Nano/Quantum suit completion, per-player sky state, strict charging priority, and full Quantum
 * Helmet abilities for Hybrid/Ultimate tiers).
 */
public class SolarHelmetItem extends ArmorItem implements Ecc2ElectricItem, Ecc2ArmorSuitBridge {

    public static final String ENERGY_TAG = "Ecc2Energy";

    private static final Map<UUID, Map<SolarHelmetSpec, SolarHelmetEngine>> PLAYER_ENGINES =
            new ConcurrentHashMap<>();

    private final SolarHelmetSpec spec;
    private final String texturePath;

    public SolarHelmetItem(ArmorMaterial material, SolarHelmetSpec spec, String texturePath) {
        super(
                material,
                EquipmentSlot.HEAD,
                new Properties().stacksTo(1).durability(0).tab(EcoCraft2.TAB));
        this.spec = spec;
        this.texturePath = texturePath;
    }

    public SolarHelmetSpec spec() {
        return spec;
    }

    protected int configuredDayPower() {
        return switch (spec) {
            case ADVANCED -> Ecc2Config.advancedHelmetGenDay();
            case HYBRID -> Ecc2Config.hybridHelmetGenDay();
            case ULTIMATE_HYBRID -> Ecc2Config.ultimateHelmetGenDay();
        };
    }

    protected int configuredNightPower() {
        return switch (spec) {
            case ADVANCED -> Ecc2Config.advancedHelmetGenNight();
            case HYBRID -> Ecc2Config.hybridHelmetGenNight();
            case ULTIMATE_HYBRID -> Ecc2Config.ultimateHelmetGenNight();
        };
    }

    @Override
    public @Nullable String getArmorTexture(
            ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        return EcoCraft2.MOD_ID + ":textures/models/armor/" + texturePath + "_layer_1.png";
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    @Override
    public <T extends LivingEntity> int damageItem(
            ItemStack stack, int amount, T entity, java.util.function.Consumer<T> onBroken) {
        return 0;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        double cap = ecc2Capacity(stack);
        if (cap <= 0.0) {
            return 0;
        }
        return (int) Math.round(Math.min(1.0, ecc2Stored(stack) / cap) * 13.0);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x2CE6F5;
    }

    @Override
    public void fillItemCategory(CreativeModeTab tab, NonNullList<ItemStack> items) {
        if (!allowedIn(tab)) {
            return;
        }
        items.add(new ItemStack(this));
        ItemStack full = new ItemStack(this);
        ecc2SetStored(full, ecc2Capacity(full));
        items.add(full);
    }

    @Override
    public void onArmorTick(ItemStack stack, Level level, Player player) {
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        SolarHelmetEngine engine = engineFor(player);
        BlockPos headPos = player.blockPosition().above();
        boolean hasSky = serverLevel.dimensionType().hasSkyLight();
        boolean seesSky = hasSky && serverLevel.canSeeSky(headPos);
        boolean isDay = serverLevel.isDay();
        boolean isRaining = serverLevel.isRaining() || serverLevel.isThundering();
        Biome biome = serverLevel.getBiome(headPos).value();
        boolean canRain = biome.getPrecipitation() != Biome.Precipitation.NONE;

        engine.tickGeneration(
                hasSky,
                seesSky,
                isDay,
                isRaining,
                canRain,
                Ecc2Config.helmetsChargeArmorOnly(),
                (targetSlot, offerEu, helmetTier) ->
                        routeCharge(player, stack, targetSlot, offerEu, helmetTier));

        if (spec.hasQuantumAbilities() && player instanceof ServerPlayer serverPlayer) {
            applyQuantumAbilities(engine, stack, serverPlayer);
        }
    }

    private double routeCharge(
            Player player,
            ItemStack helmetStack,
            SolarHelmetEngine.ChargeTargetSlot targetSlot,
            double offerEu,
            int helmetTier) {
        return switch (targetSlot) {
            case FEET -> chargeIfValid(player.getItemBySlot(EquipmentSlot.FEET), offerEu, helmetTier);
            case LEGS -> chargeIfValid(player.getItemBySlot(EquipmentSlot.LEGS), offerEu, helmetTier);
            case CHEST -> chargeIfValid(player.getItemBySlot(EquipmentSlot.CHEST), offerEu, helmetTier);
            case OFFHAND -> chargeIfValid(player.getOffhandItem(), offerEu, helmetTier);
            case MAIN -> {
                double remaining = offerEu;
                double total = 0.0;
                for (int i = 0; i < player.getInventory().items.size() && remaining > 0.0; i++) {
                    ItemStack invStack = player.getInventory().items.get(i);
                    if (invStack.isEmpty() || invStack == helmetStack) {
                        continue;
                    }
                    double accepted = chargeIfValid(invStack, remaining, helmetTier);
                    remaining -= accepted;
                    total += accepted;
                }
                yield total;
            }
            case SELF -> ecc2Charge(helmetStack, offerEu, helmetTier, false, false);
        };
    }

    private static double chargeIfValid(ItemStack target, double offerEu, int helmetTier) {
        if (target.isEmpty() || target.getCount() != 1) {
            return 0.0;
        }
        return Ecc2EnergyHelper.chargeStack(target, offerEu, helmetTier, false);
    }

    private void applyQuantumAbilities(
            SolarHelmetEngine engine, ItemStack helmetStack, ServerPlayer player) {
        boolean hasWither = player.hasEffect(MobEffects.WITHER);
        boolean hasPoison = player.hasEffect(MobEffects.POISON);
        boolean hasRadiation = Radiation.has(player);
        int foodSlot = findFilledFoodCanSlot(player);

        var plan =
                engine.evaluateQuantumAbilities(
                        ecc2Stored(helmetStack),
                        player.getAirSupply(),
                        hasWither,
                        hasPoison,
                        hasRadiation,
                        player.getFoodData().getFoodLevel(),
                        foodSlot >= 0);

        if (!plan.hasAnyAction()) {
            return;
        }
        double prior = ecc2Stored(helmetStack);
        if (prior < plan.totalEuCost()) {
            return;
        }
        ecc2SetStored(helmetStack, prior - plan.totalEuCost());

        if (plan.refillOxygen()) {
            player.setAirSupply(SolarHelmetEngine.OXYGEN_REFILL_TARGET_AIR);
        }
        if (plan.cureWither()) {
            player.removeEffect(MobEffects.WITHER);
        }
        if (plan.curePoison()) {
            player.removeEffect(MobEffects.POISON);
        }
        if (plan.cureRadiation()) {
            Radiation.clear(player);
        }
        if (plan.autoFeed() && foodSlot >= 0) {
            consumeOneFoodCan(player, foodSlot);
        }
    }

    private static int findFilledFoodCanSlot(Player player) {
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            ItemStack item = player.getInventory().items.get(i);
            if (!item.isEmpty() && "ic2:filled_tin_can".equals(Ecc2BridgePlatform.itemId(item))) {
                return i;
            }
        }
        return -1;
    }

    private static void consumeOneFoodCan(ServerPlayer player, int slot) {
        ItemStack stack = player.getInventory().items.get(slot);
        if (stack.isEmpty()) {
            return;
        }
        var food = stack.getFoodProperties(player);
        int nutrition = food != null ? Math.max(2, food.getNutrition()) : 2;
        float saturation = food != null ? Math.max(0.2f, food.getSaturationModifier()) : 0.2f;
        player.getFoodData().eat(nutrition, saturation);
        stack.shrink(1);
        var emptyCan = Ecc2BridgePlatform.findItem("ic2:tin_can");
        if (emptyCan != null) {
            ItemStack remainder = new ItemStack(emptyCan);
            if (!player.getInventory().add(remainder)) {
                player.drop(remainder, false);
            }
        }
    }

    private SolarHelmetEngine engineFor(Player player) {
        var perPlayer =
                PLAYER_ENGINES.computeIfAbsent(
                        player.getUUID(), ignored -> Collections.synchronizedMap(new WeakHashMap<>()));
        int day = configuredDayPower();
        int night = configuredNightPower();
        synchronized (perPlayer) {
            SolarHelmetEngine existing = perPlayer.get(spec);
            if (existing == null || existing.dayPower() != day || existing.nightPower() != night) {
                SolarHelmetEngine created =
                        new SolarHelmetEngine(
                                spec, day, night, Ecc2ConfigDefaults.SOLAR_SKY_CHECK_INTERVAL_TICKS);
                if (existing != null) {
                    created.restoreState(existing.skyCondition(), existing.tickCounter());
                }
                perPlayer.put(spec, created);
                return created;
            }
            return existing;
        }
    }

    // ---- Ecc2ElectricItem ----

    @Override
    public double ecc2Capacity(ItemStack stack) {
        return spec.capacity();
    }

    @Override
    public double ecc2TransferLimit(ItemStack stack) {
        return spec.transferLimit();
    }

    @Override
    public int ecc2Tier(ItemStack stack) {
        return spec.tier();
    }

    @Override
    public boolean ecc2CanProvide(ItemStack stack) {
        return false;
    }

    @Override
    public double ecc2Stored(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(ENERGY_TAG)) {
            return 0.0;
        }
        double value = tag.getDouble(ENERGY_TAG);
        return Double.isFinite(value) ? Math.max(0.0, Math.min(ecc2Capacity(stack), value)) : 0.0;
    }

    @Override
    public void ecc2SetStored(ItemStack stack, double eu) {
        double clamped =
                Double.isFinite(eu) ? Math.max(0.0, Math.min(ecc2Capacity(stack), eu)) : 0.0;
        stack.getOrCreateTag().putDouble(ENERGY_TAG, clamped);
    }

    @Override
    public double ecc2Charge(
            ItemStack stack,
            double offer,
            int chargerTier,
            boolean ignoreLimit,
            boolean simulate) {
        if (!Double.isFinite(offer) || offer <= 0.0 || chargerTier < ecc2Tier(stack)) {
            return 0.0;
        }
        double stored = ecc2Stored(stack);
        double room = Math.max(0.0, ecc2Capacity(stack) - stored);
        double capped = ignoreLimit ? offer : Math.min(offer, ecc2TransferLimit(stack));
        double accepted = Math.min(room, capped);
        if (!simulate && accepted > 0.0) {
            ecc2SetStored(stack, stored + accepted);
        }
        return accepted;
    }

    @Override
    public double ecc2Discharge(
            ItemStack stack,
            double requested,
            int chargerTier,
            boolean ignoreLimit,
            boolean simulate) {
        return 0.0;
    }

    // ---- Ecc2ArmorSuitBridge ----

    @Override
    public SuitClass ecc2SuitClass(ItemStack stack) {
        return spec.hasQuantumAbilities() ? SuitClass.QUANTUM : SuitClass.NANO;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(
                Component.translatable(
                                "tooltip.ecc2.helmet.generation",
                                configuredDayPower(),
                                configuredNightPower())
                        .withStyle(ChatFormatting.YELLOW));
        tooltip.add(
                Component.translatable(
                                "tooltip.ecc2.helmet.energy",
                                (long) Math.floor(ecc2Stored(stack)),
                                (long) ecc2Capacity(stack),
                                spec.tier())
                        .withStyle(ChatFormatting.AQUA));
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
    }
}
