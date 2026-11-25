package com.ecocraft2.solar;

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

/** Menu for the Creative Quantum Generator aligned to {@code assets-gen/gui_coordinates.json}. */
public final class QuantumGeneratorMenu extends AbstractContainerMenu {

    private final BlockPos pos;
    private final ContainerData data;

    public QuantumGeneratorMenu(int id, Inventory playerInv, FriendlyByteBuf buf) {
        this(id, playerInv, buf.readBlockPos(), new SimpleContainerData(5));
    }

    public QuantumGeneratorMenu(
            int id, Inventory playerInv, BlockPos pos, ContainerData data) {
        super(SolarKineticRegistry.QUANTUM_GENERATOR_MENU.get(), id);
        this.pos = pos;
        this.data = data;
        addDataSlots(data);

        // Player inventory (top-left item at 8, 111; hotbar at 8, 169)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 111 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 169));
        }
    }

    public BlockPos pos() {
        return pos;
    }

    public int production() {
        return (data.get(0) & 0xFFFF) | ((data.get(1) & 0xFFFF) << 16);
    }

    public int tier() {
        return Math.max(1, Math.min(6, data.get(2)));
    }

    public boolean isActive() {
        return data.get(3) != 0;
    }

    public int packetCount() {
        return Math.max(1, data.get(4));
    }

    public int tierPacketSize() {
        return (int) SolarTier.packetSize(tier());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
}
