package com.ecocraft2.gas;

import com.ecocraft2.common.config.Ecc2Ids;
import com.ecocraft2.common.gas.OrganicFermenterEngine;
import com.ecocraft2.giant.Ecc2SyncData;
import com.ecocraft2.giant.Ecc2UpgradeHelper;
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
import net.minecraft.world.item.Items;

/** Container menu for {@code ecc2:organic_fermenter} aligned to {@code organic_fermenter.png}. */
public final class OrganicFermenterMenu extends DeviceAccessMenu {

    // 0: progress (0..1000), 1..2: storedEU, 3..4: maxEU, 5: cultureMb, 6: biogasMb
    private static final int DATA_COUNT = 7;

    private final Container container;
    private final ContainerData data;

    public OrganicFermenterMenu(
            int containerId, Inventory playerInventory, OrganicFermenterBlockEntity be) {
        this(containerId, playerInventory, be, be, createData(be));
    }

    public static OrganicFermenterMenu fromNetwork(
            int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        buf.readBlockPos();
        SimpleContainer clientContainer =
                new SimpleContainer(OrganicFermenterBlockEntity.INVENTORY_SIZE) {
                    @Override
                    public boolean canPlaceItem(int slot, ItemStack stack) {
                        return switch (slot) {
                            case OrganicFermenterBlockEntity.SLOT_FOOD_INPUT ->
                                    GiantGasPlatform.foodNutrition(stack) > 0;
                            case OrganicFermenterBlockEntity.SLOT_CULTURE_CELL_IN ->
                                    GiantGasPlatform.isDrainableContainer(
                                            stack, Ecc2Ids.CULTURE_MEDIUM_ID::equals);
                            case OrganicFermenterBlockEntity.SLOT_BIOGAS_CELL_IN ->
                                    GiantGasPlatform.isFillableContainer(
                                            stack, Ecc2Ids.IC2_BIOGAS_ID);
                            case OrganicFermenterBlockEntity.SLOT_BATTERY ->
                                    PortableGear.canDischarge(stack, 14)
                                            || stack.is(Items.REDSTONE);
                            case 7, 8, 9, 10 ->
                                    Ecc2UpgradeHelper.isAcceptedUpgrade(stack, true, true);
                            default -> false;
                        };
                    }
                };
        return new OrganicFermenterMenu(
                containerId,
                playerInventory,
                null,
                clientContainer,
                new SimpleContainerData(DATA_COUNT));
    }

    private OrganicFermenterMenu(
            int containerId,
            Inventory playerInventory,
            OrganicFermenterBlockEntity device,
            Container container,
            ContainerData data) {
        super(GiantGasModule.ORGANIC_FERMENTER_MENU.get(), containerId, playerInventory, device);
        this.container = container;
        this.data = data;

        // 0: food input (61, 21), 1: container return (83, 21)
        addSlot(filteredSlot(container, OrganicFermenterBlockEntity.SLOT_FOOD_INPUT, 61, 21));
        addSlot(outputSlot(container, OrganicFermenterBlockEntity.SLOT_CONTAINER_RETURN, 83, 21));
        // 2..3: culture cell in (37, 21) & out (37, 57)
        addSlot(filteredSlot(container, OrganicFermenterBlockEntity.SLOT_CULTURE_CELL_IN, 37, 21));
        addSlot(outputSlot(container, OrganicFermenterBlockEntity.SLOT_CULTURE_CELL_OUT, 37, 57));
        // 4..5: biogas cell in (131, 21) & out (131, 57)
        addSlot(filteredSlot(container, OrganicFermenterBlockEntity.SLOT_BIOGAS_CELL_IN, 131, 21));
        addSlot(outputSlot(container, OrganicFermenterBlockEntity.SLOT_BIOGAS_CELL_OUT, 131, 57));
        // 6: battery (83, 64)
        addSlot(filteredSlot(container, OrganicFermenterBlockEntity.SLOT_BATTERY, 83, 64));
        // 7..10: upgrades (153, 19/37/55/73)
        for (int i = 0; i < OrganicFermenterBlockEntity.UPGRADE_COUNT; i++) {
            addSlot(
                    filteredSlot(
                            container,
                            OrganicFermenterBlockEntity.UPGRADE_START + i,
                            153,
                            19 + i * 18));
        }

        // Player inventory (8, 98) & hotbar (8, 156)
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new Slot(playerInventory, c + r * 9 + 9, 8 + c * 18, 98 + r * 18));
            }
        }
        for (int c = 0; c < 9; c++) {
            addSlot(new Slot(playerInventory, c, 8 + c * 18, 156));
        }

        addDataSlots(data);
        addAccessData();
    }

    private static ContainerData createData(OrganicFermenterBlockEntity be) {
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
                    case 5 -> be.cultureMb();
                    case 6 -> be.biogasMb();
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
        int machineSlots = OrganicFermenterBlockEntity.INVENTORY_SIZE;
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (Ecc2UpgradeHelper.isAcceptedUpgrade(stack, true, true)) {
                moveItemStackTo(stack, 7, 11, false);
            } else if (GiantGasPlatform.foodNutrition(stack) > 0) {
                moveItemStackTo(stack, 0, 1, false);
            } else if (GiantGasPlatform.isDrainableContainer(
                    stack, Ecc2Ids.CULTURE_MEDIUM_ID::equals)) {
                moveItemStackTo(stack, 2, 3, false);
            } else if (GiantGasPlatform.isFillableContainer(stack, Ecc2Ids.IC2_BIOGAS_ID)) {
                moveItemStackTo(stack, 4, 5, false);
            } else if (container.canPlaceItem(OrganicFermenterBlockEntity.SLOT_BATTERY, stack)) {
                moveItemStackTo(stack, 6, 7, false);
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

    public int cultureMb() {
        return Math.max(0, Math.min(OrganicFermenterEngine.CULTURE_TANK_CAPACITY_MB, data.get(5)));
    }

    public int biogasMb() {
        return Math.max(0, Math.min(OrganicFermenterEngine.BIOGAS_TANK_CAPACITY_MB, data.get(6)));
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
}
