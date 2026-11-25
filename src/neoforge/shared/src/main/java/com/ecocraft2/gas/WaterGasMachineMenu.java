package com.ecocraft2.gas;

import com.ecocraft2.common.config.Ecc2Ids;
import com.ecocraft2.common.gas.WaterGasEngine;
import com.ecocraft2.giant.Ecc2SyncData;
import com.ecocraft2.giant.Ecc2UpgradeHelper;
import com.ecocraft2.giant.GiantGasModule;
import com.ecocraft2.giant.GiantGasPlatform;
import com.industrialage.ia2.gui.DeviceAccessMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Container menu for {@code ecc2:water_gas_machine} aligned to {@code water_gas_machine.png}. */
public final class WaterGasMachineMenu extends DeviceAccessMenu {

    // 0: progress (0..1000), 1..2: storedEU, 3..4: maxEU, 5: waterMb, 6: gasMb, 7: solarActive
    private static final int DATA_COUNT = 8;

    private final Container container;
    private final ContainerData data;

    public WaterGasMachineMenu(
            int containerId, Inventory playerInventory, WaterGasMachineBlockEntity be) {
        this(containerId, playerInventory, be, be, createData(be));
    }

    public static WaterGasMachineMenu fromNetwork(
            int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        buf.readBlockPos();
        return new WaterGasMachineMenu(
                containerId,
                playerInventory,
                null,
                new SimpleContainer(WaterGasMachineBlockEntity.INVENTORY_SIZE),
                new SimpleContainerData(DATA_COUNT));
    }

    private WaterGasMachineMenu(
            int containerId,
            Inventory playerInventory,
            WaterGasMachineBlockEntity device,
            Container container,
            ContainerData data) {
        super(GiantGasModule.WATER_GAS_MACHINE_MENU.get(), containerId, playerInventory, device);
        this.container = container;
        this.data = data;

        // 0: coal dust (61, 25)
        addSlot(filteredSlot(container, WaterGasMachineBlockEntity.SLOT_COAL_DUST, 61, 25));
        // 1..2: water cell in (37, 21) & out (37, 57)
        addSlot(filteredSlot(container, WaterGasMachineBlockEntity.SLOT_WATER_CELL_IN, 37, 21));
        addSlot(outputSlot(container, WaterGasMachineBlockEntity.SLOT_WATER_CELL_OUT, 37, 57));
        // 3..4: gas cell in (131, 21) & out (131, 57)
        addSlot(filteredSlot(container, WaterGasMachineBlockEntity.SLOT_GAS_CELL_IN, 131, 21));
        addSlot(outputSlot(container, WaterGasMachineBlockEntity.SLOT_GAS_CELL_OUT, 131, 57));
        // 5: battery (83, 64)
        addSlot(filteredSlot(container, WaterGasMachineBlockEntity.SLOT_BATTERY, 83, 64));
        // 6..9: upgrades (153, 19/37/55/73)
        for (int i = 0; i < WaterGasMachineBlockEntity.UPGRADE_COUNT; i++) {
            addSlot(
                    filteredSlot(
                            container,
                            WaterGasMachineBlockEntity.UPGRADE_START + i,
                            153,
                            19 + i * 18));
        }

        // Player inventory (8, 98) & hotbar (8, 156)
        addPlayerSlots(playerInventory, 8, 98, 156);
        addDataSlots(data);
        addAccessData();
    }

    private static ContainerData createData(WaterGasMachineBlockEntity be) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                int eu = (int) Math.min(Integer.MAX_VALUE, Math.round(be.storedEu()));
                int cap = (int) Math.min(Integer.MAX_VALUE, Math.round(be.energyCapacity()));
                return switch (index) {
                    case 0 -> be.engine().scaledProgress(1000);
                    case 1 -> Ecc2SyncData.low16(eu);
                    case 2 -> Ecc2SyncData.high16(eu);
                    case 3 -> Ecc2SyncData.low16(cap);
                    case 4 -> Ecc2SyncData.high16(cap);
                    case 5 -> be.waterMb();
                    case 6 -> be.waterGasMb();
                    case 7 -> be.isSolarActive() ? 1 : 0;
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
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int machineSlots = WaterGasMachineBlockEntity.INVENTORY_SIZE;
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (Ecc2UpgradeHelper.isAcceptedUpgrade(stack, true, true)) {
                moveItemStackTo(stack, 6, 10, false);
            } else if (WaterGasMachineBlockEntity.isCoalDust(stack)) {
                moveItemStackTo(stack, 0, 1, false);
            } else if (GiantGasPlatform.isDrainableContainer(
                    stack, Ecc2Ids.MINECRAFT_WATER_ID::equals)) {
                moveItemStackTo(stack, 1, 2, false);
            } else if (GiantGasPlatform.isFillableContainer(stack, Ecc2Ids.WATER_GAS_ID)) {
                moveItemStackTo(stack, 3, 4, false);
            } else if (container.canPlaceItem(WaterGasMachineBlockEntity.SLOT_BATTERY, stack)) {
                moveItemStackTo(stack, 5, 6, false);
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

    public int progressPermille() {
        return Math.max(0, Math.min(1000, data.get(0)));
    }

    public int storedEu() {
        return Ecc2SyncData.unpack32(data.get(1), data.get(2));
    }

    public int energyCapacity() {
        return Math.max(1, Ecc2SyncData.unpack32(data.get(3), data.get(4)));
    }

    public int waterMb() {
        return Math.max(0, Math.min(WaterGasEngine.WATER_TANK_CAPACITY_MB, data.get(5)));
    }

    public int waterGasMb() {
        return Math.max(0, Math.min(WaterGasEngine.GAS_TANK_CAPACITY_MB, data.get(6)));
    }

    public boolean isSolarActive() {
        return data.get(7) != 0;
    }

    private Slot filteredSlot(Container inv, int slot, int x, int y) {
        return new Slot(inv, slot, x, y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return inv.canPlaceItem(slot, stack);
            }
        };
    }

    private Slot outputSlot(Container inv, int slot, int x, int y) {
        return new Slot(inv, slot, x, y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        };
    }

    private void addPlayerSlots(Inventory inv, int leftX, int invY, int hotbarY) {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new Slot(inv, c + r * 9 + 9, leftX + c * 18, invY + r * 18));
            }
        }
        for (int c = 0; c < 9; c++) {
            addSlot(new Slot(inv, c, leftX + c * 18, hotbarY));
        }
    }
}
