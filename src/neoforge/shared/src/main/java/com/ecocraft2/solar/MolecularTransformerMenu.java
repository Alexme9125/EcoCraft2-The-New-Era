package com.ecocraft2.solar;

import com.industrialage.ia2.gui.DeviceAccessMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Container menu for {@code ecc2:molecular_transformer} with 32-bit safe EU telemetry, resolved
 * output preview sync, and IC2NE side-access permissions.
 */
public final class MolecularTransformerMenu extends DeviceAccessMenu {

    private static final int DATA_SLOTS = 11;

    private final Container container;
    private final MolecularTransformerBlockEntity blockEntity;
    private final ContainerData data;

    /** Client constructor invoked via {@code IMenuTypeExtension}. */
    public MolecularTransformerMenu(
            int containerId, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInv, resolveClientEntity(playerInv, buf.readBlockPos()));
    }

    private static MolecularTransformerBlockEntity resolveClientEntity(
            Inventory playerInv, BlockPos pos) {
        if (playerInv.player.level().getBlockEntity(pos)
                instanceof MolecularTransformerBlockEntity mt) {
            return mt;
        }
        return null;
    }

    /** Server (and resolved-client) constructor. */
    public MolecularTransformerMenu(
            int containerId, Inventory playerInv, MolecularTransformerBlockEntity blockEntity) {
        super(
                SolarKineticModule.MOLECULAR_TRANSFORMER_MENU.get(),
                containerId,
                playerInv,
                blockEntity);
        this.blockEntity = blockEntity;
        this.container =
                blockEntity != null
                        ? blockEntity
                        : new SimpleContainer(MolecularTransformerBlockEntity.SLOT_COUNT);
        this.data =
                blockEntity != null ? new MtData(blockEntity) : new SimpleContainerData(DATA_SLOTS);

        // Input slot (20, 26) & Output slot (20, 68) matching gui_coordinates.json
        addSlot(new Slot(this.container, MolecularTransformerBlockEntity.SLOT_INPUT, 20, 26));
        addSlot(
                new Slot(this.container, MolecularTransformerBlockEntity.SLOT_OUTPUT, 20, 68) {
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

        addDataSlots(this.data);
        addAccessData();
    }

    public int totalEu() {
        return Math.max(0, Ecc2DataSync.combine32(data.get(0), data.get(1)));
    }

    public int energyUsed() {
        return Math.max(0, Ecc2DataSync.combine32(data.get(2), data.get(3)));
    }

    public int lastTickPower() {
        return Math.max(0, Ecc2DataSync.combine32(data.get(4), data.get(5)));
    }

    public int progressPercent() {
        return Math.max(0, Math.min(100, data.get(6)));
    }

    public boolean isWorking() {
        return (data.get(7) & 1) != 0;
    }

    public boolean isActive() {
        return (data.get(7) & 2) != 0;
    }

    public boolean isOutputBlocked() {
        return (data.get(7) & 4) != 0;
    }

    public ItemStack inputStack() {
        return container.getItem(MolecularTransformerBlockEntity.SLOT_INPUT);
    }

    public ItemStack resolvedOutputPreview() {
        int rawId = Ecc2DataSync.combine32(data.get(8), data.get(9));
        int count = data.get(10);
        if (rawId < 0 || count <= 0) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.byId(rawId);
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, count);
    }

    public int scaledProgress(int barHeight) {
        int total = totalEu();
        int used = energyUsed();
        if (barHeight <= 0 || total <= 0 || used <= 0) {
            return 0;
        }
        return (int) Math.min(barHeight, ((long) used * barHeight) / total);
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
        int invStart = MolecularTransformerBlockEntity.SLOT_COUNT;
        int invEnd = invStart + 27;
        int hotbarEnd = invEnd + 9;

        if (index < invStart) {
            if (!moveItemStackTo(stack, invStart, hotbarEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!moveItemStackTo(
                    stack,
                    MolecularTransformerBlockEntity.SLOT_INPUT,
                    MolecularTransformerBlockEntity.SLOT_INPUT + 1,
                    false)) {
                if (index < invEnd) {
                    if (!moveItemStackTo(stack, invEnd, hotbarEnd, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!moveItemStackTo(stack, invStart, invEnd, false)) {
                    return ItemStack.EMPTY;
                }
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

    private record MtData(MolecularTransformerBlockEntity entity) implements ContainerData {
        @Override
        public int get(int index) {
            int total = (int) Math.min(Integer.MAX_VALUE, Math.round(entity.getTotalEu()));
            int used = (int) Math.min(Integer.MAX_VALUE, Math.round(entity.getEnergyUsed()));
            int power = (int) Math.min(Integer.MAX_VALUE, Math.round(entity.getLastTickPower()));
            int flags =
                    (entity.isWorking() ? 1 : 0)
                            | (entity.isActive() ? 2 : 0)
                            | (entity.isOutputBlocked() ? 4 : 0);
            int outId = entity.getPendingOutputItemId();
            return switch (index) {
                case 0 -> Ecc2DataSync.lo16(total);
                case 1 -> Ecc2DataSync.hi16(total);
                case 2 -> Ecc2DataSync.lo16(used);
                case 3 -> Ecc2DataSync.hi16(used);
                case 4 -> Ecc2DataSync.lo16(power);
                case 5 -> Ecc2DataSync.hi16(power);
                case 6 -> entity.getProgressPercent();
                case 7 -> flags;
                case 8 -> outId < 0 ? 0xFFFF : Ecc2DataSync.lo16(outId);
                case 9 -> outId < 0 ? 0xFFFF : Ecc2DataSync.hi16(outId);
                case 10 -> entity.getPendingOutputCount();
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
