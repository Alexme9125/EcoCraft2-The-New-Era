package com.ecocraft2.giant;

import com.ecocraft2.common.giant.GiantCornerPort;
import com.ecocraft2.common.giant.GiantPortPermissions;
import com.ecocraft2.common.giant.PortAccessMode;
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

/**
 * Menu for all 5 3x3x3 Giant Machines aligned pixel-for-pixel to {@code gui_coordinates.json}
 * (256x236 sci-fi panel: 3x3 input, 3x3 output, battery, 4 upgrades, optional 2 washer slots).
 */
public final class GiantMachineMenu extends AbstractContainerMenu {

    private static final int[] INPUT_COL_X = {13, 33, 53};
    private static final int[] INPUT_ROW_Y = {25, 49, 73};
    private static final int[] OUTPUT_COL_X = {157, 177, 197};
    private static final int[] OUTPUT_ROW_Y = {25, 49, 73};
    private static final int[] UPGRADE_Y = {25, 45, 65, 85};

    private final BlockPos pos;
    private final ContainerData data;
    private final boolean hasWaterSlots;

    public GiantMachineMenu(int id, Inventory playerInv, FriendlyByteBuf buf) {
        this(
                id,
                playerInv,
                buf.readBlockPos(),
                new ItemStackHandler(GiantMachineBlockEntity.TOTAL_SLOTS),
                new SimpleContainerData(GiantMachineBlockEntity.SYNC_DATA_COUNT),
                true);
    }

    public GiantMachineMenu(
            int id, Inventory playerInv, GiantMachineBlockEntity core, ContainerData data) {
        this(
                id,
                playerInv,
                core.getBlockPos(),
                core.inventory(),
                data,
                core.kind().spec().usesWater());
    }

    private GiantMachineMenu(
            int id,
            Inventory playerInv,
            BlockPos pos,
            IItemHandler handler,
            ContainerData data,
            boolean hasWaterSlots) {
        super(GiantRegistry.GIANT_MACHINE_MENU.get(), id);
        this.pos = pos;
        this.data = data;
        this.hasWaterSlots = hasWaterSlots;
        addDataSlots(data);

        // 0..8: 3x3 input lanes
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                addSlot(new SlotItemHandler(handler, r * 3 + c, INPUT_COL_X[c], INPUT_ROW_Y[r]));
            }
        }
        // 9..17: 3x3 output grid (extract only)
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                addSlot(
                        new SlotItemHandler(handler, 9 + r * 3 + c, OUTPUT_COL_X[c], OUTPUT_ROW_Y[r]) {
                            @Override
                            public boolean mayPlace(ItemStack stack) {
                                return false;
                            }
                        });
            }
        }
        // 18: Battery slot (79, 83)
        addSlot(new SlotItemHandler(handler, GiantMachineBlockEntity.BATTERY_SLOT, 79, 83));
        // 19..22: 4 upgrade slots (226, 25/45/65/85)
        for (int i = 0; i < GiantMachineBlockEntity.UPGRADE_COUNT; i++) {
            addSlot(
                    new SlotItemHandler(
                            handler, GiantMachineBlockEntity.UPGRADE_START + i, 226, UPGRADE_Y[i]));
        }
        // 23..24: Washer water container in/out slots (130, 106 & 130, 126)
        addSlot(
                new SlotItemHandler(handler, GiantMachineBlockEntity.WASHER_IN_SLOT, 130, 106) {
                    @Override
                    public boolean isActive() {
                        return usesWater();
                    }
                });
        addSlot(
                new SlotItemHandler(handler, GiantMachineBlockEntity.WASHER_OUT_SLOT, 130, 126) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }

                    @Override
                    public boolean isActive() {
                        return usesWater();
                    }
                });

        // Player inventory (48, 152) & hotbar (48, 210)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 48 + col * 18, 152 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 48 + col * 18, 210));
        }
    }

    public BlockPos pos() {
        return pos;
    }

    public int storedEu() {
        return (data.get(0) & 0xFFFF) | ((data.get(1) & 0xFFFF) << 16);
    }

    public int capacityEu() {
        return Math.max(1, (data.get(2) & 0xFFFF) | ((data.get(3) & 0xFFFF) << 16));
    }

    public int activeTotalBatch() {
        return data.get(4);
    }

    public int euDrawPerTick() {
        return data.get(5);
    }

    public int tier() {
        return Math.max(1, data.get(6));
    }

    public int tierMaxPacket() {
        return (int) SolarTier.packetSize(tier());
    }

    public int waterMb() {
        return (data.get(7) & 0xFFFF) | ((data.get(8) & 0xFFFF) << 16);
    }

    public int waterCapacityMb() {
        return Math.max(1, (data.get(9) & 0xFFFF) | ((data.get(10) & 0xFFFF) << 16));
    }

    public int packedPorts() {
        return data.get(11) & 0xFFFF;
    }

    public PortAccessMode portMode(GiantCornerPort port) {
        return new GiantPortPermissions(packedPorts()).get(port);
    }

    public int laneProgressPercent(int lane) {
        return Math.max(0, Math.min(100, data.get(12 + lane)));
    }

    public int laneBatchSize(int lane) {
        return Math.max(0, Math.min(3, data.get(21 + lane)));
    }

    public boolean usesWater() {
        return hasWaterSlots && data.get(30) != 0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int machineSlots = GiantMachineBlockEntity.TOTAL_SLOTS;
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Try upgrades -> battery -> washer container -> 9 input lanes
            if (!moveItemStackTo(stack, GiantMachineBlockEntity.UPGRADE_START, GiantMachineBlockEntity.UPGRADE_START + GiantMachineBlockEntity.UPGRADE_COUNT, false)
                    && !moveItemStackTo(stack, GiantMachineBlockEntity.BATTERY_SLOT, GiantMachineBlockEntity.BATTERY_SLOT + 1, false)
                    && (!usesWater() || !moveItemStackTo(stack, GiantMachineBlockEntity.WASHER_IN_SLOT, GiantMachineBlockEntity.WASHER_IN_SLOT + 1, false))
                    && !moveItemStackTo(stack, GiantMachineBlockEntity.INPUT_START, GiantMachineBlockEntity.INPUT_START + GiantMachineBlockEntity.INPUT_COUNT, false)) {
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
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 144.0;
    }
}
