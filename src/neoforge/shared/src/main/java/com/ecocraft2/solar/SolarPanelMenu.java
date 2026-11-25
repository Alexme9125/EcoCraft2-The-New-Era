package com.ecocraft2.solar;

import com.ecocraft2.common.solar.SkyCondition;
import com.ecocraft2.common.solar.SolarTier;
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
 * Container menu for all four Advanced Solar Panels 2 tiers, synchronized with 32-bit safe EU
 * telemetry and IC2NE side-access permissions.
 */
public final class SolarPanelMenu extends DeviceAccessMenu {

    private static final int DATA_SLOTS = 8;

    private static final int[] CHARGE_SLOT_X = {62, 80, 98, 116};
    private static final int CHARGE_SLOT_Y = 63;

    private final Container container;
    private final SolarPanelBlockEntity blockEntity;
    private final SolarPanelBlockEntity filterHint;
    private final ContainerData data;

    /** Client constructor invoked by NeoForge's {@code IMenuTypeExtension}. */
    public SolarPanelMenu(int containerId, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInv, null, resolveClientEntity(playerInv, buf.readBlockPos()));
    }

    private static SolarPanelBlockEntity resolveClientEntity(Inventory playerInv, BlockPos pos) {
        if (playerInv.player.level().getBlockEntity(pos) instanceof SolarPanelBlockEntity panel) {
            return panel;
        }
        return null;
    }

    /** Server constructor. */
    public SolarPanelMenu(
            int containerId, Inventory playerInv, SolarPanelBlockEntity blockEntity) {
        this(containerId, playerInv, blockEntity, blockEntity);
    }

    private SolarPanelMenu(
            int containerId,
            Inventory playerInv,
            SolarPanelBlockEntity serverEntity,
            SolarPanelBlockEntity filterHint) {
        super(SolarKineticModule.SOLAR_PANEL_MENU.get(), containerId, playerInv, serverEntity);
        this.blockEntity = serverEntity;
        this.filterHint = filterHint;
        this.container =
                serverEntity != null
                        ? serverEntity
                        : new SimpleContainer(SolarPanelBlockEntity.CHARGE_SLOTS);
        this.data =
                serverEntity != null
                        ? new PanelData(serverEntity)
                        : new SimpleContainerData(DATA_SLOTS);

        for (int i = 0; i < SolarPanelBlockEntity.CHARGE_SLOTS; i++) {
            addSlot(
                    new Slot(this.container, i, CHARGE_SLOT_X[i], CHARGE_SLOT_Y) {
                        @Override
                        public boolean mayPlace(ItemStack stack) {
                            return filterHint == null || filterHint.acceptsChargeItem(stack);
                        }

                        @Override
                        public int getMaxStackSize() {
                            return 1;
                        }
                    });
        }

        // Player inventory matching assets-gen/gui_coordinates.json: top-left (17, 86), hotbar (17, 144)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 17 + col * 18, 86 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 17 + col * 18, 144));
        }

        addDataSlots(this.data);
        addAccessData();
    }

    public int storedEu() {
        return Ecc2DataSync.combine32(data.get(0), data.get(1));
    }

    public int maxStorage() {
        return Math.max(1, Ecc2DataSync.combine32(data.get(2), data.get(3)));
    }

    public int generatingEu() {
        return Ecc2DataSync.combine32(data.get(4), data.get(5));
    }

    public int outputTier() {
        return Math.max(1, data.get(6));
    }

    public double maxOutputEu() {
        return SolarTier.packetSize(outputTier());
    }

    public SkyCondition skyCondition() {
        int ord = data.get(7);
        var values = SkyCondition.values();
        return ord >= 0 && ord < values.length ? values[ord] : SkyCondition.NONE;
    }

    public int scaledEnergy(int barWidth) {
        int max = maxStorage();
        int stored = Math.max(0, storedEu());
        if (barWidth <= 0 || max <= 0 || stored <= 0) {
            return 0;
        }
        return (int) Math.min(barWidth, ((long) stored * barWidth) / max);
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
        int machineSlots = SolarPanelBlockEntity.CHARGE_SLOTS;
        int invEnd = machineSlots + 27;
        int hotbarEnd = invEnd + 9;

        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, hotbarEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean accepts = filterHint == null || filterHint.acceptsChargeItem(stack);
            if (accepts && moveItemStackTo(stack, 0, machineSlots, false)) {
                // moved into charge slot
            } else if (index < invEnd) {
                if (!moveItemStackTo(stack, invEnd, hotbarEnd, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, machineSlots, invEnd, false)) {
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

    private record PanelData(SolarPanelBlockEntity entity) implements ContainerData {
        @Override
        public int get(int index) {
            int stored = (int) Math.min(Integer.MAX_VALUE, Math.round(entity.getStoredEu()));
            int max = entity.getMaxStorage();
            int gen = entity.getCurrentGenerating();
            return switch (index) {
                case 0 -> Ecc2DataSync.lo16(stored);
                case 1 -> Ecc2DataSync.hi16(stored);
                case 2 -> Ecc2DataSync.lo16(max);
                case 3 -> Ecc2DataSync.hi16(max);
                case 4 -> Ecc2DataSync.lo16(gen);
                case 5 -> Ecc2DataSync.hi16(gen);
                case 6 -> entity.getOutputTier();
                case 7 -> entity.getSkyCondition().ordinal();
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
