package com.ecocraft2.giant;

import com.ecocraft2.common.config.Ecc2Ids;
import com.ecocraft2.common.giant.GiantCornerPort;
import com.ecocraft2.common.giant.GiantMachineEngine;
import com.ecocraft2.common.giant.GiantPortPermissions;
import com.ecocraft2.common.giant.PortAccessMode;
import com.ecocraft2.common.solar.SolarTier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Container menu for all 3x3x3 Giant Machines, pixel-aligned to {@code assets-gen/gui_coordinates.json}
 * ({@code giant_machine.png}, 256x236).
 */
public final class GiantMachineMenu extends AbstractContainerMenu {

    // Data slot layout (32 synced 16-bit slots):
    // 0..8   : lane progress scaled 0..1000
    // 9..17  : lane batch size 0..3
    // 18..19 : stored EU (low16, high16)
    // 20..21 : energy capacity (low16, high16)
    // 22     : active total batch M (0..27)
    // 23..24 : last tick EU draw * 10 (low16, high16)
    // 25     : input tier (1..14)
    // 26..27 : water mB (low16, high16)
    // 28..29 : water capacity mB (low16, high16)
    // 30     : packed 16-bit GiantPortPermissions
    // 31     : flags bit0=usesWater, bit1=active
    private static final int DATA_COUNT = 32;

    private static final int[][] INPUT_COORDS = {
        {13, 25}, {33, 25}, {53, 25},
        {13, 49}, {33, 49}, {53, 49},
        {13, 73}, {33, 73}, {53, 73}
    };

    private static final int[][] OUTPUT_COORDS = {
        {157, 25}, {177, 25}, {197, 25},
        {157, 49}, {177, 49}, {197, 49},
        {157, 73}, {177, 73}, {197, 73}
    };

    private static final int[][] UPGRADE_COORDS = {
        {226, 25}, {226, 45}, {226, 65}, {226, 85}
    };

    private final Container container;
    private final ContainerData data;
    private final BlockPos corePos;
    private final boolean initialUsesWater;

    public GiantMachineMenu(int containerId, Inventory playerInventory, GiantUnitBlockEntity core) {
        this(
                containerId,
                playerInventory,
                core,
                createServerData(core),
                core.getBlockPos(),
                core.usesWater());
    }

    public static GiantMachineMenu fromNetwork(
            int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        boolean usesWater = false;
        if (playerInventory.player.level().getBlockEntity(pos)
                instanceof GiantUnitBlockEntity be) {
            usesWater = be.usesWater();
        }
        return new GiantMachineMenu(
                containerId,
                playerInventory,
                new SimpleContainer(GiantUnitBlockEntity.INVENTORY_SIZE),
                new SimpleContainerData(DATA_COUNT),
                pos,
                usesWater);
    }

    private GiantMachineMenu(
            int containerId,
            Inventory playerInventory,
            Container container,
            ContainerData data,
            BlockPos corePos,
            boolean initialUsesWater) {
        super(GiantGasModule.GIANT_MACHINE_MENU.get(), containerId);
        this.container = container;
        this.data = data;
        this.corePos = corePos.immutable();
        this.initialUsesWater = initialUsesWater;

        // 0..8: 3x3 Input slots
        for (int i = 0; i < GiantUnitBlockEntity.INPUT_COUNT; i++) {
            final int slotIdx = GiantUnitBlockEntity.INPUT_START + i;
            addSlot(
                    new Slot(container, slotIdx, INPUT_COORDS[i][0], INPUT_COORDS[i][1]) {
                        @Override
                        public boolean mayPlace(ItemStack stack) {
                            return container.canPlaceItem(slotIdx, stack);
                        }
                    });
        }

        // 9..17: 3x3 Output slots
        for (int i = 0; i < GiantUnitBlockEntity.OUTPUT_COUNT; i++) {
            final int slotIdx = GiantUnitBlockEntity.OUTPUT_START + i;
            addSlot(
                    new Slot(container, slotIdx, OUTPUT_COORDS[i][0], OUTPUT_COORDS[i][1]) {
                        @Override
                        public boolean mayPlace(ItemStack stack) {
                            return false;
                        }
                    });
        }

        // 18: Battery discharge slot
        addSlot(
                new Slot(container, GiantUnitBlockEntity.SLOT_BATTERY, 79, 83) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return container.canPlaceItem(GiantUnitBlockEntity.SLOT_BATTERY, stack);
                    }
                });

        // 19..22: 4 IC2 Upgrade slots
        for (int i = 0; i < GiantUnitBlockEntity.UPGRADE_COUNT; i++) {
            final int slotIdx = GiantUnitBlockEntity.UPGRADE_START + i;
            addSlot(
                    new Slot(container, slotIdx, UPGRADE_COORDS[i][0], UPGRADE_COORDS[i][1]) {
                        @Override
                        public boolean mayPlace(ItemStack stack) {
                            return container.canPlaceItem(slotIdx, stack);
                        }
                    });
        }

        // 23..24: Ore Washer water container input & return slots (active only on water-using giant machines)
        addSlot(
                new Slot(container, GiantUnitBlockEntity.SLOT_WASHER_IN, 130, 106) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return usesWater()
                                && container.canPlaceItem(
                                        GiantUnitBlockEntity.SLOT_WASHER_IN, stack);
                    }

                    @Override
                    public boolean isActive() {
                        return usesWater();
                    }
                });
        addSlot(
                new Slot(container, GiantUnitBlockEntity.SLOT_WASHER_OUT, 130, 126) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }

                    @Override
                    public boolean isActive() {
                        return usesWater();
                    }
                });

        // Player inventory (48, 152) and hotbar (48, 210)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 48 + col * 18, 152 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 48 + col * 18, 210));
        }

        addDataSlots(data);
    }

    private static ContainerData createServerData(GiantUnitBlockEntity core) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                GiantMachineEngine eng = core.engine();
                if (index >= 0 && index < 9) {
                    return eng.scaledLaneProgress(index, 1000);
                }
                if (index >= 9 && index < 18) {
                    return eng.laneBatchSize(index - 9);
                }
                int storedEu = (int) Math.min(Integer.MAX_VALUE, Math.round(eng.storedEu()));
                int capEu = (int) Math.min(Integer.MAX_VALUE, Math.round(eng.energyCapacity()));
                int drawTenths =
                        (int) Math.min(Integer.MAX_VALUE, Math.round(eng.lastTickEuConsumed() * 10.0));
                return switch (index) {
                    case 18 -> Ecc2SyncData.low16(storedEu);
                    case 19 -> Ecc2SyncData.high16(storedEu);
                    case 20 -> Ecc2SyncData.low16(capEu);
                    case 21 -> Ecc2SyncData.high16(capEu);
                    case 22 -> eng.lastActiveTotalBatch();
                    case 23 -> Ecc2SyncData.low16(drawTenths);
                    case 24 -> Ecc2SyncData.high16(drawTenths);
                    case 25 -> eng.inputTier();
                    case 26 -> Ecc2SyncData.low16(eng.waterMb());
                    case 27 -> Ecc2SyncData.high16(eng.waterMb());
                    case 28 -> Ecc2SyncData.low16(eng.waterTankCapacityMb());
                    case 29 -> Ecc2SyncData.high16(eng.waterTankCapacityMb());
                    case 30 -> Ecc2SyncData.low16(eng.portPermissions().packed());
                    case 31 -> (core.usesWater() ? 1 : 0) | (eng.isActive() ? 2 : 0);
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int machineSlots = GiantUnitBlockEntity.INVENTORY_SIZE;
        int playerEnd = slots.size();

        if (slotIndex < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, playerEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // 1. Upgrades
            if (Ecc2UpgradeHelper.isAcceptedUpgrade(stack, true, usesWater())) {
                if (moveItemStackTo(
                        stack,
                        GiantUnitBlockEntity.UPGRADE_START,
                        GiantUnitBlockEntity.UPGRADE_START + GiantUnitBlockEntity.UPGRADE_COUNT,
                        false)) {
                    slot.onQuickCraft(stack, copy);
                }
            }
            // 2. Water containers for Ore Washer
            if (!stack.isEmpty()
                    && usesWater()
                    && GiantGasPlatform.isDrainableContainer(
                            stack, Ecc2Ids.MINECRAFT_WATER_ID::equals)) {
                moveItemStackTo(
                        stack,
                        GiantUnitBlockEntity.SLOT_WASHER_IN,
                        GiantUnitBlockEntity.SLOT_WASHER_IN + 1,
                        false);
            }
            // 3. Dischargeable batteries
            if (!stack.isEmpty()
                    && container.canPlaceItem(GiantUnitBlockEntity.SLOT_BATTERY, stack)) {
                moveItemStackTo(
                        stack,
                        GiantUnitBlockEntity.SLOT_BATTERY,
                        GiantUnitBlockEntity.SLOT_BATTERY + 1,
                        false);
            }
            // 4. 3x3 Input lanes
            if (!stack.isEmpty()) {
                moveItemStackTo(
                        stack,
                        GiantUnitBlockEntity.INPUT_START,
                        GiantUnitBlockEntity.INPUT_START + GiantUnitBlockEntity.INPUT_COUNT,
                        false);
            }
            if (stack.getCount() == copy.getCount()) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, stack);
        return copy;
    }

    // ---- Synced Client Telemetry ----

    public BlockPos corePos() {
        return corePos;
    }

    public int laneProgressPermille(int lane) {
        return Math.max(0, Math.min(1000, data.get(lane)));
    }

    public int laneBatch(int lane) {
        return Math.max(0, Math.min(3, data.get(9 + lane)));
    }

    public int storedEu() {
        return Ecc2SyncData.unpack32(data.get(18), data.get(19));
    }

    public int energyCapacity() {
        return Math.max(1, Ecc2SyncData.unpack32(data.get(20), data.get(21)));
    }

    public int activeTotalBatch() {
        return Math.max(0, Math.min(27, data.get(22)));
    }

    public double euDrawPerTick() {
        return Ecc2SyncData.unpack32(data.get(23), data.get(24)) / 10.0;
    }

    public int inputTier() {
        return Math.max(1, data.get(25));
    }

    public int maxInputVoltage() {
        return (int) SolarTier.packetSize(inputTier());
    }

    public int waterMb() {
        return Ecc2SyncData.unpack32(data.get(26), data.get(27));
    }

    public int waterCapacityMb() {
        return Ecc2SyncData.unpack32(data.get(28), data.get(29));
    }

    public int packedPortPermissions() {
        return Ecc2SyncData.unpackU16(data.get(30));
    }

    public PortAccessMode portMode(GiantCornerPort port) {
        return new GiantPortPermissions(packedPortPermissions()).get(port);
    }

    public boolean usesWater() {
        int flags = data.get(31);
        return initialUsesWater || (flags & 1) != 0;
    }

    public boolean isMachineActive() {
        return (data.get(31) & 2) != 0;
    }
}
