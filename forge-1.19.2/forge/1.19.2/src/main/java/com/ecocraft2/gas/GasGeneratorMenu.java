package com.ecocraft2.gas;

import com.ecocraft2.common.gas.GasFuelSpec;
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
import org.jetbrains.annotations.Nullable;

/** Menu for the Gas Generator aligned to {@code assets-gen/gui_coordinates.json}. */
public final class GasGeneratorMenu extends AbstractContainerMenu {

    private final BlockPos pos;
    private final ContainerData data;

    public GasGeneratorMenu(int id, Inventory playerInv, FriendlyByteBuf buf) {
        this(
                id,
                playerInv,
                buf.readBlockPos(),
                new ItemStackHandler(GasGeneratorBlockEntity.TOTAL_SLOTS),
                new SimpleContainerData(5));
    }

    public GasGeneratorMenu(
            int id, Inventory playerInv, GasGeneratorBlockEntity be, ContainerData data) {
        this(id, playerInv, be.getBlockPos(), be.inventory(), data);
    }

    private GasGeneratorMenu(
            int id,
            Inventory playerInv,
            BlockPos pos,
            IItemHandler handler,
            ContainerData data) {
        super(GasRegistry.GAS_GENERATOR_MENU.get(), id);
        this.pos = pos;
        this.data = data;
        addDataSlots(data);

        addSlot(new SlotItemHandler(handler, GasGeneratorBlockEntity.SLOT_GAS_IN, 45, 21));
        addSlot(
                new SlotItemHandler(handler, GasGeneratorBlockEntity.SLOT_GAS_OUT, 45, 57) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }
                });
        addSlot(new SlotItemHandler(handler, GasGeneratorBlockEntity.SLOT_CHARGE, 135, 23));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
        }
    }

    public int storedEu() {
        return data.get(0);
    }

    public int gasMb() {
        return data.get(1);
    }

    public @Nullable GasFuelSpec currentFuel() {
        int ord = data.get(2);
        GasFuelSpec[] vals = GasFuelSpec.values();
        return ord >= 0 && ord < vals.length ? vals[ord] : null;
    }

    public int generatingEu() {
        return data.get(3);
    }

    public boolean isActive() {
        return data.get(4) != 0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int machineSlots = GasGeneratorBlockEntity.TOTAL_SLOTS;
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!moveItemStackTo(stack, GasGeneratorBlockEntity.SLOT_CHARGE, GasGeneratorBlockEntity.SLOT_CHARGE + 1, false)
                    && !moveItemStackTo(stack, GasGeneratorBlockEntity.SLOT_GAS_IN, GasGeneratorBlockEntity.SLOT_GAS_IN + 1, false)) {
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
