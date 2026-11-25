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

/** Menu for the Gas Furnace aligned to {@code assets-gen/gui_coordinates.json}. */
public final class GasFurnaceMenu extends AbstractContainerMenu {

    private final BlockPos pos;
    private final ContainerData data;

    public GasFurnaceMenu(int id, Inventory playerInv, FriendlyByteBuf buf) {
        this(
                id,
                playerInv,
                buf.readBlockPos(),
                new ItemStackHandler(GasFurnaceBlockEntity.TOTAL_SLOTS),
                new SimpleContainerData(4));
    }

    public GasFurnaceMenu(
            int id, Inventory playerInv, GasFurnaceBlockEntity be, ContainerData data) {
        this(id, playerInv, be.getBlockPos(), be.inventory(), data);
    }

    private GasFurnaceMenu(
            int id,
            Inventory playerInv,
            BlockPos pos,
            IItemHandler handler,
            ContainerData data) {
        super(GasRegistry.GAS_FURNACE_MENU.get(), id);
        this.pos = pos;
        this.data = data;
        addDataSlots(data);

        addSlot(new SlotItemHandler(handler, GasFurnaceBlockEntity.SLOT_INPUT, 67, 21));
        addSlot(
                new SlotItemHandler(handler, GasFurnaceBlockEntity.SLOT_OUTPUT, 121, 35) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }
                });
        addSlot(new SlotItemHandler(handler, GasFurnaceBlockEntity.SLOT_GAS_IN, 39, 21));
        addSlot(
                new SlotItemHandler(handler, GasFurnaceBlockEntity.SLOT_GAS_OUT, 39, 57) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }
                });
        for (int i = 0; i < 4; i++) {
            addSlot(
                    new SlotItemHandler(
                            handler, GasFurnaceBlockEntity.SLOT_UPGRADE_START + i, 153, 9 + i * 18));
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
        }
    }

    public int gasMb() {
        return data.get(0);
    }

    public @Nullable GasFuelSpec currentFuel() {
        int ord = data.get(1);
        GasFuelSpec[] vals = GasFuelSpec.values();
        return ord >= 0 && ord < vals.length ? vals[ord] : null;
    }

    public int progressPx() {
        return data.get(2);
    }

    public boolean isActive() {
        return data.get(3) != 0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int machineSlots = GasFurnaceBlockEntity.TOTAL_SLOTS;
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!moveItemStackTo(stack, GasFurnaceBlockEntity.SLOT_UPGRADE_START, machineSlots, false)
                    && !moveItemStackTo(stack, GasFurnaceBlockEntity.SLOT_GAS_IN, GasFurnaceBlockEntity.SLOT_GAS_IN + 1, false)
                    && !moveItemStackTo(stack, GasFurnaceBlockEntity.SLOT_INPUT, GasFurnaceBlockEntity.SLOT_INPUT + 1, false)) {
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
