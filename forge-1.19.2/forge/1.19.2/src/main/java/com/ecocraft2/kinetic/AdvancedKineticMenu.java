package com.ecocraft2.kinetic;

import com.ecocraft2.bridge.Ecc2RotorHooks;
import com.ecocraft2.common.kinetic.KineticMachineTier;
import com.ecocraft2.solar.SolarKineticRegistry;
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

/** Menu for the 6 Advanced Wind & Water Kinetic Generators aligned to {@code gui_coordinates.json}. */
public final class AdvancedKineticMenu extends AbstractContainerMenu {

    private final BlockPos pos;
    private final ContainerData data;

    public AdvancedKineticMenu(int id, Inventory playerInv, FriendlyByteBuf buf) {
        this(
                id,
                playerInv,
                buf.readBlockPos(),
                new ItemStackHandler(1) {
                    @Override
                    public int getSlotLimit(int slot) {
                        return 1;
                    }

                    @Override
                    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                        return Ecc2RotorHooks.isRotor(stack);
                    }
                },
                new SimpleContainerData(7));
    }

    public AdvancedKineticMenu(
            int id, Inventory playerInv, AdvancedKineticBlockEntity gen, ContainerData data) {
        this(id, playerInv, gen.getBlockPos(), gen.rotorSlot(), data);
    }

    private AdvancedKineticMenu(
            int id,
            Inventory playerInv,
            BlockPos pos,
            IItemHandler handler,
            ContainerData data) {
        super(SolarKineticRegistry.ADVANCED_KINETIC_MENU.get(), id);
        this.pos = pos;
        this.data = data;
        addDataSlots(data);

        // Rotor slot at (80, 24)
        addSlot(new SlotItemHandler(handler, 0, 80, 24));

        // Player inventory (8, 84) & hotbar (8, 142)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
        }
    }

    public int currentKu() {
        return data.get(0);
    }

    public int windOrFlow() {
        return data.get(1);
    }

    public int statusCode() {
        return data.get(2);
    }

    public double rotorLifePercent() {
        return data.get(3) / 10.0;
    }

    public double rotorRemainingHours() {
        return data.get(4) / 10.0;
    }

    public boolean isWater() {
        return data.get(5) != 0;
    }

    public KineticMachineTier tier() {
        int ord = data.get(6);
        KineticMachineTier[] vals = KineticMachineTier.values();
        return ord >= 0 && ord < vals.length ? vals[ord] : KineticMachineTier.DURABLE;
    }

    public boolean hasRotor() {
        return slots.get(0).hasItem();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!moveItemStackTo(stack, 0, 1, false)) {
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
