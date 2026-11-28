package com.ecocraft2.bridge;

import com.ecocraft2.common.giant.GiantUpgradeProfile;
import com.industrialage.ia2.energy.graph.EnergyGraph;
import com.industrialage.ia2.forge.equipment.ForgeGear;
import com.industrialage.ia2.platform.CapabilityFacade;
import com.industrialage.ia2.world.MachineAutomation;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

/**
 * Shared IC2NE 1.0.6 energy, portable electric item, and upgrade helpers for Forge 1.19.2 block
 * entities and items.
 */
public final class Ecc2EnergyHelper {

    public static final Set<CapabilityFacade.Face> ALL_FACES =
            Set.copyOf(EnumSet.allOf(CapabilityFacade.Face.class));

    public static final EnergyGraph.Ports ALL_OUTPUT_PORTS =
            new EnergyGraph.Ports(Set.of(), ALL_FACES, false);

    public static final EnergyGraph.Ports ALL_INPUT_PORTS =
            new EnergyGraph.Ports(ALL_FACES, Set.of(), false);

    public static final EnergyGraph.Ports NO_PORTS =
            new EnergyGraph.Ports(Set.of(), Set.of(), false);

    private Ecc2EnergyHelper() {}

    /** Converts a Minecraft {@link Direction} to IC2NE's {@link CapabilityFacade.Face}. */
    public static CapabilityFacade.Face toFace(Direction dir) {
        return switch (dir) {
            case DOWN -> CapabilityFacade.Face.DOWN;
            case UP -> CapabilityFacade.Face.UP;
            case NORTH -> CapabilityFacade.Face.NORTH;
            case SOUTH -> CapabilityFacade.Face.SOUTH;
            case WEST -> CapabilityFacade.Face.WEST;
            case EAST -> CapabilityFacade.Face.EAST;
        };
    }

    /** Returns whether {@code stack} accepts charging from a charger of voltage tier {@code tier}. */
    public static boolean acceptsCharge(ItemStack stack, int tier, boolean relaxOwnHelmetTier) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof Ecc2ElectricItem electric) {
            return relaxOwnHelmetTier || electric.ecc2Tier(stack) <= tier;
        }
        return ForgeGear.acceptsCharge(stack, tier);
    }

    /**
     * Charges an IC2NE or EcoCraft 2 electric item stack up to {@code offer} EU.
     *
     * @param relaxOwnHelmetTier when {@code true}, EcoCraft 2 solar panels can charge any EcoCraft 2
     *     solar helmet regardless of tier difference
     */
    public static double chargeStack(
            ItemStack stack, double offer, int chargerTier, boolean relaxOwnHelmetTier) {
        if (stack == null || stack.isEmpty() || stack.getCount() != 1 || offer <= 0.0) {
            return 0.0;
        }
        if (stack.getItem() instanceof Ecc2ElectricItem electric) {
            int effectiveTier =
                    relaxOwnHelmetTier ? Math.max(chargerTier, electric.ecc2Tier(stack)) : chargerTier;
            return electric.ecc2Charge(stack, offer, effectiveTier, false, false);
        }
        return ForgeGear.charge(stack, offer, chargerTier);
    }

    /** Discharges up to {@code requested} EU from an IC2NE battery or electric provider item. */
    public static double dischargeStack(
            ItemStack stack, double requested, double bufferCapacity, int machineTier) {
        if (stack == null || stack.isEmpty() || requested <= 0.0) {
            return 0.0;
        }
        if (stack.getItem() instanceof Ecc2ElectricItem electric) {
            return electric.ecc2Discharge(stack, requested, machineTier, false, false);
        }
        return ForgeGear.discharge(stack, requested, bufferCapacity, machineTier);
    }

    /** Returns whether {@code stack} can supply discharge power into a machine of {@code tier}. */
    public static boolean canDischarge(ItemStack stack, int tier) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof Ecc2ElectricItem electric) {
            return electric.ecc2CanProvide(stack) && electric.ecc2Tier(stack) <= tier;
        }
        return ForgeGear.canDischarge(stack, tier);
    }

    /** Returns whether {@code stack} is any supported IC2NE machine upgrade item. */
    public static boolean isUpgradeItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (MachineAutomation.isProcessingUpgrade(stack)) {
            return true;
        }
        String id = Ecc2BridgePlatform.itemId(stack);
        return "mhera:fluid_ejector_upgrade".equals(id)
                || "mhera:fluid_pulling_upgrade".equals(id)
                || "mhera:redstone_inverter_upgrade".equals(id);
    }

    /** Summarizes installed IC2NE processing upgrades into a {@link GiantUpgradeProfile}. */
    public static GiantUpgradeProfile readUpgradeProfile(List<ItemStack> upgradeStacks) {
        var installed = MachineAutomation.installed(upgradeStacks);
        return new GiantUpgradeProfile(
                installed.overclockers(), installed.transformers(), installed.storage());
    }

    /** Returns {@code true} if any stack in {@code upgradeStacks} is a redstone inverter upgrade. */
    public static boolean hasRedstoneInverter(List<ItemStack> upgradeStacks) {
        for (var stack : upgradeStacks) {
            if ("mhera:redstone_inverter_upgrade".equals(Ecc2BridgePlatform.itemId(stack))) {
                return true;
            }
        }
        return false;
    }
}
