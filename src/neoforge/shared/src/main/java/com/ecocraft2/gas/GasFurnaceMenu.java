package com.ecocraft2.gas;

import com.ecocraft2.common.gas.GasFuelSpec;
import com.ecocraft2.common.gas.GasFurnaceEngine;
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

/** Container menu for {@code ecc2:gas_furnace} aligned to {@code gas_furnace.png}. */
public final class GasFurnaceMenu extends DeviceAccessMenu {

    // 0: progress (0..1000), 1: gasMb, 2: fuelOrdinal+1 (0=none), 3: active
    private static final int DATA_COUNT = 4;

    private final Container container;
    private final ContainerData data;

    public GasFurnaceMenu(int containerId, Inventory playerInventory, GasFurnaceBlockEntity be) {
        this(containerId, playerInventory, be, be, createData(be));
    }

    public static GasFurnaceMenu fromNetwork(
            int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        buf.readBlockPos();
        SimpleContainer clientContainer =
                new SimpleContainer(GasFurnaceBlockEntity.INVENTORY_SIZE) {
                    @Override
                    public boolean canPlaceItem(int slot, ItemStack stack) {
                        return switch (slot) {
                            case GasFurnaceBlockEntity.SLOT_INPUT -> !stack.isEmpty();
                            case GasFurnaceBlockEntity.SLOT_GAS_CELL_IN ->
                                    GiantGasPlatform.isDrainableContainer(
                                            stack, GiantGasPlatform::isGasFuel);
                            case 4, 5, 6, 7 ->
                                    Ecc2UpgradeHelper.isAcceptedUpgrade(stack, false, true);
                            default -> false;
                        };
                    }
                };
        return new GasFurnaceMenu(
                containerId,
                playerInventory,
                null,
                clientContainer,
                new SimpleContainerData(DATA_COUNT));
    }

    private GasFurnaceMenu(
            int containerId,
            Inventory playerInventory,
            GasFurnaceBlockEntity device,
            Container container,
            ContainerData data) {
        super(GiantGasModule.GAS_FURNACE_MENU.get(), containerId, playerInventory, device);
        this.container = container;
        this.data = data;

        // 0: input (67, 21), 1: output (121, 35)
        addSlot(
                new Slot(container, GasFurnaceBlockEntity.SLOT_INPUT, 67, 21) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return container.canPlaceItem(GasFurnaceBlockEntity.SLOT_INPUT, stack);
                    }
                });
        addSlot(
                new Slot(container, GasFurnaceBlockEntity.SLOT_OUTPUT, 121, 35) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }
                });
        // 2..3: gas cell in (39, 21) & out (39, 57)
        addSlot(
                new Slot(container, GasFurnaceBlockEntity.SLOT_GAS_CELL_IN, 39, 21) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return container.canPlaceItem(
                                GasFurnaceBlockEntity.SLOT_GAS_CELL_IN, stack);
                    }
                });
        addSlot(
                new Slot(container, GasFurnaceBlockEntity.SLOT_GAS_CELL_OUT, 39, 57) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }
                });
        // 4..7: upgrade slots (153, 9/27/45/63)
        for (int i = 0; i < GasFurnaceBlockEntity.UPGRADE_COUNT; i++) {
            final int slotIdx = GasFurnaceBlockEntity.UPGRADE_START + i;
            addSlot(
                    new Slot(container, slotIdx, 153, 9 + i * 18) {
                        @Override
                        public boolean mayPlace(ItemStack stack) {
                            return container.canPlaceItem(slotIdx, stack);
                        }
                    });
        }

        // Player inventory (8, 84) & hotbar (8, 142)
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new Slot(playerInventory, c + r * 9 + 9, 8 + c * 18, 84 + r * 18));
            }
        }
        for (int c = 0; c < 9; c++) {
            addSlot(new Slot(playerInventory, c, 8 + c * 18, 142));
        }

        addDataSlots(data);
        addAccessData();
    }

    private static ContainerData createData(GasFurnaceBlockEntity be) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> be.engine().scaledProgress(1000);
                    case 1 -> be.gasMb();
                    case 2 -> be.currentFuel() == null ? 0 : be.currentFuel().ordinal() + 1;
                    case 3 -> be.isMachineActive() ? 1 : 0;
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
        int machineSlots = GasFurnaceBlockEntity.INVENTORY_SIZE;
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (Ecc2UpgradeHelper.isAcceptedUpgrade(stack, false, true)) {
                moveItemStackTo(stack, 4, 8, false);
            } else if (GiantGasPlatform.isDrainableContainer(
                    stack, GiantGasPlatform::isGasFuel)) {
                moveItemStackTo(stack, 2, 3, false);
            } else {
                moveItemStackTo(stack, 0, 1, false);
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

    public int gasMb() {
        return Math.max(0, Math.min(GasFurnaceEngine.GAS_TANK_CAPACITY_MB, data.get(1)));
    }

    public GasFuelSpec currentFuel() {
        int code = data.get(2);
        GasFuelSpec[] all = GasFuelSpec.values();
        return code >= 1 && code <= all.length ? all[code - 1] : null;
    }

    public boolean isMachineActive() {
        return data.get(3) != 0;
    }
}
