package com.ecocraft2.kinetic;

import com.ecocraft2.common.kinetic.KineticMachineTier;
import com.ecocraft2.solar.Ecc2DataSync;
import com.ecocraft2.solar.SolarKineticModule;
import com.industrialage.ia2.gui.DeviceAccessMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Container menu for all six Advanced Wind &amp; Water Kinetic Generators, synchronized with KU/t,
 * wind/flow readings, rotor durability, machine tier badge, and IC2NE side-access permissions.
 */
public final class AdvancedKineticMenu extends DeviceAccessMenu {

    private static final int DATA_SLOTS = 9;

    private final Container container;
    private final AdvancedKineticBlockEntity blockEntity;
    private final ContainerData data;

    /** Client constructor invoked via {@code IMenuTypeExtension}. */
    public AdvancedKineticMenu(int containerId, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInv, resolveClientEntity(playerInv, buf.readBlockPos()));
    }

    private static AdvancedKineticBlockEntity resolveClientEntity(
            Inventory playerInv, BlockPos pos) {
        if (playerInv.player.level().getBlockEntity(pos)
                instanceof AdvancedKineticBlockEntity kinetic) {
            return kinetic;
        }
        return null;
    }

    /** Server (and resolved-client) constructor. */
    public AdvancedKineticMenu(
            int containerId, Inventory playerInv, AdvancedKineticBlockEntity blockEntity) {
        super(
                SolarKineticModule.KINETIC_GENERATOR_MENU.get(),
                containerId,
                playerInv,
                blockEntity);
        this.blockEntity = blockEntity;
        this.container = blockEntity != null ? blockEntity : new SimpleContainer(1);
        this.data =
                blockEntity != null
                        ? new KineticData(blockEntity)
                        : new SimpleContainerData(DATA_SLOTS);

        // Rotor slot (80, 24) matching assets-gen/gui_coordinates.json
        addSlot(
                new Slot(this.container, AdvancedKineticBlockEntity.SLOT_ROTOR, 80, 24) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return blockEntity == null || blockEntity.acceptsRotor(stack);
                    }

                    @Override
                    public int getMaxStackSize() {
                        return 1;
                    }
                });

        // Player inventory (8, 84) & hotbar (8, 142)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
        }

        addDataSlots(this.data);
        addAccessData();
    }

    public int currentKu() {
        return Math.max(0, data.get(0));
    }

    public int environmentReading() {
        return Math.max(0, data.get(1));
    }

    public AdvancedKineticPhysics.Status status() {
        int ord = data.get(2);
        var values = AdvancedKineticPhysics.Status.values();
        return ord >= 0 && ord < values.length ? values[ord] : AdvancedKineticPhysics.Status.NO_ROTOR;
    }

    public KineticMachineTier kineticTier() {
        int ord = data.get(3);
        var values = KineticMachineTier.values();
        return ord >= 0 && ord < values.length ? values[ord] : KineticMachineTier.DURABLE;
    }

    public boolean isWaterType() {
        return data.get(4) != 0;
    }

    public int rotorRemaining() {
        return Math.max(0, Ecc2DataSync.combine32(data.get(5), data.get(6)));
    }

    public int rotorMax() {
        return Math.max(0, Ecc2DataSync.combine32(data.get(7), data.get(8)));
    }

    public double rotorPercent() {
        int max = rotorMax();
        return max <= 0 ? 0.0 : (rotorRemaining() * 100.0) / max;
    }

    public int scaledDurability(int barWidth) {
        int max = rotorMax();
        int rem = rotorRemaining();
        if (barWidth <= 0 || max <= 0 || rem <= 0) {
            return 0;
        }
        return (int) Math.min(barWidth, ((long) rem * barWidth) / max);
    }

    @Override
    public boolean stillValid(Player player) {
        return blockEntity == null || blockEntity.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int invStart = 1;
        int invEnd = invStart + 27;
        int hotbarEnd = invEnd + 9;

        if (index == 0) {
            if (!moveItemStackTo(stack, invStart, hotbarEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean accepts = blockEntity == null || blockEntity.acceptsRotor(stack);
            if (accepts && moveItemStackTo(stack, 0, 1, false)) {
                // placed rotor
            } else if (index < invEnd) {
                if (!moveItemStackTo(stack, invEnd, hotbarEnd, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, invStart, invEnd, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == copy.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return copy;
    }

    private record KineticData(AdvancedKineticBlockEntity entity) implements ContainerData {
        @Override
        public int get(int index) {
            int rem = entity.getRotorRemainingDurability();
            int max = entity.getRotorMaxDurability();
            return switch (index) {
                case 0 -> entity.getCurrentKu();
                case 1 -> entity.getEnvironmentReading();
                case 2 -> entity.getStatus().ordinal();
                case 3 -> entity.getKineticTier().ordinal();
                case 4 -> entity.isWaterType() ? 1 : 0;
                case 5 -> Ecc2DataSync.lo16(rem);
                case 6 -> Ecc2DataSync.hi16(rem);
                case 7 -> Ecc2DataSync.lo16(max);
                case 8 -> Ecc2DataSync.hi16(max);
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return DATA_SLOTS;
        }
    }
}
