package com.ecocraft2.gas;

import com.ecocraft2.common.gas.GasFuelSpec;
import com.ecocraft2.common.gas.GasGeneratorEngine;
import com.ecocraft2.giant.GiantGasModule;
import com.ecocraft2.giant.GiantGasPlatform;
import com.industrialage.ia2.gameplay.PortableGear;
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

/** Container menu for {@code ecc2:gas_generator} aligned to {@code gas_generator.png}. */
public final class GasGeneratorMenu extends DeviceAccessMenu {

    // 0: gasMb, 1: storedEu (0..16000), 2: fuelOrdinal+1 (0=none), 3: generatingEuPerTick, 4: active
    private static final int DATA_COUNT = 5;

    private final Container container;
    private final ContainerData data;

    public GasGeneratorMenu(int containerId, Inventory playerInventory, GasGeneratorBlockEntity be) {
        this(containerId, playerInventory, be, be, createData(be));
    }

    public static GasGeneratorMenu fromNetwork(
            int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        buf.readBlockPos();
        SimpleContainer clientContainer =
                new SimpleContainer(GasGeneratorBlockEntity.INVENTORY_SIZE) {
                    @Override
                    public boolean canPlaceItem(int slot, ItemStack stack) {
                        return switch (slot) {
                            case GasGeneratorBlockEntity.SLOT_GAS_CELL_IN ->
                                    GiantGasPlatform.isDrainableContainer(
                                            stack, id -> GasFuelSpec.byFluidId(id).isPresent());
                            case GasGeneratorBlockEntity.SLOT_CHARGE ->
                                    PortableGear.acceptsCharge(
                                            stack, GasGeneratorEngine.OUTPUT_TIER);
                            default -> false;
                        };
                    }
                };
        return new GasGeneratorMenu(
                containerId,
                playerInventory,
                null,
                clientContainer,
                new SimpleContainerData(DATA_COUNT));
    }

    private GasGeneratorMenu(
            int containerId,
            Inventory playerInventory,
            GasGeneratorBlockEntity device,
            Container container,
            ContainerData data) {
        super(GiantGasModule.GAS_GENERATOR_MENU.get(), containerId, playerInventory, device);
        this.container = container;
        this.data = data;

        // 0..1: gas cell in (45, 21) & out (45, 57)
        addSlot(
                new Slot(container, GasGeneratorBlockEntity.SLOT_GAS_CELL_IN, 45, 21) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return container.canPlaceItem(
                                GasGeneratorBlockEntity.SLOT_GAS_CELL_IN, stack);
                    }
                });
        addSlot(
                new Slot(container, GasGeneratorBlockEntity.SLOT_GAS_CELL_OUT, 45, 57) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }
                });
        // 2: charge slot (135, 23)
        addSlot(
                new Slot(container, GasGeneratorBlockEntity.SLOT_CHARGE, 135, 23) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return container.canPlaceItem(GasGeneratorBlockEntity.SLOT_CHARGE, stack);
                    }
                });

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

    private static ContainerData createData(GasGeneratorBlockEntity be) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> be.gasMb();
                    case 1 -> (int) Math.round(be.storedEu());
                    case 2 -> be.currentFuel() == null ? 0 : be.currentFuel().ordinal() + 1;
                    case 3 -> (int) Math.round(be.generatingEuPerTick());
                    case 4 -> be.isMachineActive() ? 1 : 0;
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
        int machineSlots = GasGeneratorBlockEntity.INVENTORY_SIZE;
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (GiantGasPlatform.isDrainableContainer(
                    stack, id -> GasFuelSpec.byFluidId(id).isPresent())) {
                moveItemStackTo(stack, 0, 1, false);
            } else if (container.canPlaceItem(GasGeneratorBlockEntity.SLOT_CHARGE, stack)) {
                moveItemStackTo(stack, 2, 3, false);
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

    public int gasMb() {
        return Math.max(0, Math.min(GasGeneratorEngine.GAS_TANK_CAPACITY_MB, data.get(0)));
    }

    public int storedEu() {
        return Math.max(0, Math.min((int) GasGeneratorEngine.EU_CAPACITY, data.get(1)));
    }

    public GasFuelSpec currentFuel() {
        int code = data.get(2);
        GasFuelSpec[] all = GasFuelSpec.values();
        return code >= 1 && code <= all.length ? all[code - 1] : null;
    }

    public int generatingEuPerTick() {
        return Math.max(0, data.get(3));
    }

    public boolean isMachineActive() {
        return data.get(4) != 0;
    }
}
