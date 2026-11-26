package com.ecocraft2.solar;

import com.ecocraft2.bridge.Ecc2EnergyHelper;
import com.ecocraft2.common.solar.SkyCondition;
import com.ecocraft2.common.solar.SolarTier;
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
import org.jetbrains.annotations.NotNull;

/** Menu for the 4 ASP 2 Solar Panels aligned to {@code assets-gen/gui_coordinates.json}. */
public final class SolarPanelMenu extends AbstractContainerMenu {

    private static final int CHARGE_SLOTS = SolarPanelBlockEntity.CHARGE_SLOT_COUNT;
    private static final int[] SLOT_X = {62, 80, 98, 116};
    private static final int SLOT_Y = 63;

    private final BlockPos pos;
    private final ContainerData data;

    public SolarPanelMenu(int id, Inventory playerInv, FriendlyByteBuf buf) {
        this(
                id,
                playerInv,
                buf.readBlockPos(),
                new ItemStackHandler(CHARGE_SLOTS) {
                    @Override
                    public int getSlotLimit(int slot) {
                        return 1;
                    }

                    @Override
                    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                        return Ecc2EnergyHelper.acceptsCharge(stack, 14, true);
                    }
                },
                new SimpleContainerData(7));
    }

    public SolarPanelMenu(
            int id, Inventory playerInv, SolarPanelBlockEntity panel, ContainerData data) {
        this(id, playerInv, panel.getBlockPos(), panel.chargeSlots(), data);
    }

    private SolarPanelMenu(
            int id,
            Inventory playerInv,
            BlockPos pos,
            IItemHandler handler,
            ContainerData data) {
        super(SolarKineticRegistry.SOLAR_PANEL_MENU.get(), id);
        this.pos = pos;
        this.data = data;
        addDataSlots(data);

        for (int i = 0; i < CHARGE_SLOTS; i++) {
            addSlot(new SlotItemHandler(handler, i, SLOT_X[i], SLOT_Y));
        }

        // Player inventory (top-left item at 17, 86; hotbar at 17, 144)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 17 + col * 18, 86 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 17 + col * 18, 144));
        }
    }

    public int storedEu() {
        return (data.get(0) & 0xFFFF) | ((data.get(1) & 0xFFFF) << 16);
    }

    public int maxStorageEu() {
        return Math.max(1, (data.get(2) & 0xFFFF) | ((data.get(3) & 0xFFFF) << 16));
    }

    public int generatingEu() {
        return data.get(4);
    }

    public SkyCondition skyCondition() {
        int ord = data.get(5);
        SkyCondition[] values = SkyCondition.values();
        return ord >= 0 && ord < values.length ? values[ord] : SkyCondition.NONE;
    }

    public int tier() {
        return Math.max(1, data.get(6));
    }

    public int maxOutputEu() {
        return (int) SolarTier.packetSize(tier());
    }

    public int scaledEnergy(int barWidth) {
        long stored = storedEu();
        long max = maxStorageEu();
        return (int) Math.min(barWidth, (stored * barWidth) / max);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < CHARGE_SLOTS) {
            if (!moveItemStackTo(stack, CHARGE_SLOTS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!moveItemStackTo(stack, 0, CHARGE_SLOTS, false)) {
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
