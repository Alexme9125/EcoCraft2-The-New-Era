package com.ecocraft2.gas;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

/** Menu for the Culture Incubator aligned to {@code assets-gen/gui_coordinates.json}. */
public final class CultureIncubatorMenu extends AbstractContainerMenu {

    private final BlockPos pos;
    private final ContainerData data;

    public CultureIncubatorMenu(int id, Inventory playerInv, FriendlyByteBuf buf) {
        this(
                id,
                playerInv,
                buf.readBlockPos(),
                new ItemStackHandler(CultureIncubatorBlockEntity.TOTAL_SLOTS),
                new SimpleContainerData(8));
    }

    public CultureIncubatorMenu(
            int id,
            Inventory playerInv,
            CultureIncubatorBlockEntity be,
            ContainerData data) {
        this(id, playerInv, be.getBlockPos(), be.inventory(), data);
    }

    private CultureIncubatorMenu(
            int id,
            Inventory playerInv,
            BlockPos pos,
            IItemHandler handler,
            ContainerData data) {
        super(GasRegistry.CULTURE_INCUBATOR_MENU.get(), id);
        this.pos = pos;
        this.data = data;
        addDataSlots(data);

        addSlot(new SlotItemHandler(handler, CultureIncubatorBlockEntity.SLOT_BONEMEAL, 67, 25));
        addSlot(new SlotItemHandler(handler, CultureIncubatorBlockEntity.SLOT_WATER_IN, 37, 21));
        addSlot(
                new SlotItemHandler(handler, CultureIncubatorBlockEntity.SLOT_WATER_OUT, 37, 57) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }
                });
        addSlot(new SlotItemHandler(handler, CultureIncubatorBlockEntity.SLOT_CULTURE_IN, 131, 21));
        addSlot(
                new SlotItemHandler(handler, CultureIncubatorBlockEntity.SLOT_CULTURE_OUT, 131, 57) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }
                });
        addSlot(new SlotItemHandler(handler, CultureIncubatorBlockEntity.SLOT_BATTERY, 83, 64));
        for (int i = 0; i < 4; i++) {
            addSlot(
                    new SlotItemHandler(
                            handler,
                            CultureIncubatorBlockEntity.SLOT_UPGRADE_START + i,
                            153,
                            19 + i * 18));
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 98 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 156));
        }
    }

    public int storedEu() {
        return (data.get(0) & 0xFFFF) | ((data.get(1) & 0xFFFF) << 16);
    }

    public int capacityEu() {
        return Math.max(1, (data.get(2) & 0xFFFF) | ((data.get(3) & 0xFFFF) << 16));
    }

    public int waterMb() {
        return data.get(4);
    }

    public int cultureMb() {
        return data.get(5);
    }

    public int progressPx() {
        return data.get(6);
    }

    public boolean isActive() {
        return data.get(7) != 0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int machineSlots = CultureIncubatorBlockEntity.TOTAL_SLOTS;
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!moveItemStackTo(stack, CultureIncubatorBlockEntity.SLOT_UPGRADE_START, machineSlots, false)
                    && !moveItemStackTo(stack, CultureIncubatorBlockEntity.SLOT_BATTERY, CultureIncubatorBlockEntity.SLOT_BATTERY + 1, false)
                    && !moveItemStackTo(stack, CultureIncubatorBlockEntity.SLOT_BONEMEAL, CultureIncubatorBlockEntity.SLOT_BONEMEAL + 1, false)
                    && !moveItemStackTo(stack, CultureIncubatorBlockEntity.SLOT_WATER_IN, CultureIncubatorBlockEntity.SLOT_WATER_IN + 1, false)
                    && !moveItemStackTo(stack, CultureIncubatorBlockEntity.SLOT_CULTURE_IN, CultureIncubatorBlockEntity.SLOT_CULTURE_IN + 1, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
}
