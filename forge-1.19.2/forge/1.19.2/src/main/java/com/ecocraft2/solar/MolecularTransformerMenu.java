package com.ecocraft2.solar;

import com.industrialage.ia2.gui.DeviceAccessMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Menu for the Molecular Transformer aligned to {@code assets-gen/gui_coordinates.json}. */
public final class MolecularTransformerMenu extends DeviceAccessMenu {

    private final BlockPos pos;
    private final ContainerData data;

    public MolecularTransformerMenu(int id, Inventory playerInv, FriendlyByteBuf buf) {
        this(
                id,
                playerInv,
                buf.readBlockPos(),
                null,
                createClientHandler(),
                new SimpleContainerData(8));
    }

    public MolecularTransformerMenu(
            int id,
            Inventory playerInv,
            MolecularTransformerBlockEntity mt,
            ContainerData data) {
        this(id, playerInv, mt.getBlockPos(), mt, mt.rawInventory(), data);
    }

    private MolecularTransformerMenu(
            int id,
            Inventory playerInv,
            BlockPos pos,
            @Nullable BlockEntity device,
            IItemHandler handler,
            ContainerData data) {
        super(SolarKineticRegistry.MOLECULAR_TRANSFORMER_MENU.get(), id, playerInv, device);
        this.pos = pos;
        this.data = data;
        addDataSlots(data);
        addAccessData();

        // Input slot (20, 26), Output slot (20, 68)
        addSlot(new SlotItemHandler(handler, 0, 20, 26));
        addSlot(
                new SlotItemHandler(handler, 1, 20, 68) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }
                });

        // Player inventory (30, 111) & hotbar (30, 169)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 30 + col * 18, 111 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 30 + col * 18, 169));
        }
    }

    public long totalEu() {
        return (((long) (data.get(0) & 0xFFFF)) | (((long) (data.get(1) & 0xFFFF)) << 16));
    }

    public long energyUsed() {
        return (((long) (data.get(2) & 0xFFFF)) | (((long) (data.get(3) & 0xFFFF)) << 16));
    }

    public long lastTickPower() {
        return (((long) (data.get(4) & 0xFFFF)) | (((long) (data.get(5) & 0xFFFF)) << 16));
    }

    public int progressPercent() {
        return Math.max(0, Math.min(100, data.get(6)));
    }

    public int flags() {
        return data.get(7);
    }

    public boolean isWorking() {
        return (flags() & 1) != 0;
    }

    public boolean isOutputBlocked() {
        return (flags() & 2) != 0;
    }

    public boolean isActive() {
        return (flags() & 4) != 0;
    }

    public int scaledProgress(int barHeight) {
        return (progressPercent() * barHeight) / 100;
    }

    public ItemStack inputStack() {
        return slots.get(0).getItem();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < 2) {
            if (!moveItemStackTo(stack, 2, slots.size(), true)) {
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

    private static ItemStackHandler createClientHandler() {
        return new ItemStackHandler(2) {
            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                return slot == 0;
            }
        };
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
}
