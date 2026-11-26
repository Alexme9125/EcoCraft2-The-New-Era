package com.ecocraft2.gas;

import com.ecocraft2.bridge.Ecc2BridgePlatform;
import com.ecocraft2.bridge.Ecc2EnergyHelper;
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
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Menu for the Water Gas Machine aligned to {@code assets-gen/gui_coordinates.json}. */
public final class WaterGasMachineMenu extends DeviceAccessMenu {

    private final BlockPos pos;
    private final ContainerData data;

    public WaterGasMachineMenu(int id, Inventory playerInv, FriendlyByteBuf buf) {
        this(
                id,
                playerInv,
                buf.readBlockPos(),
                null,
                createClientHandler(),
                new SimpleContainerData(8));
    }

    public WaterGasMachineMenu(
            int id,
            Inventory playerInv,
            WaterGasMachineBlockEntity be,
            ContainerData data) {
        this(id, playerInv, be.getBlockPos(), be, be.inventory(), data);
    }

    private WaterGasMachineMenu(
            int id,
            Inventory playerInv,
            BlockPos pos,
            @Nullable BlockEntity device,
            IItemHandler handler,
            ContainerData data) {
        super(GasRegistry.WATER_GAS_MACHINE_MENU.get(), id, playerInv, device);
        this.pos = pos;
        this.data = data;
        addDataSlots(data);
        addAccessData();

        addSlot(new SlotItemHandler(handler, WaterGasMachineBlockEntity.SLOT_COAL_DUST, 61, 25));
        addSlot(new SlotItemHandler(handler, WaterGasMachineBlockEntity.SLOT_WATER_IN, 37, 21));
        addSlot(
                new SlotItemHandler(handler, WaterGasMachineBlockEntity.SLOT_WATER_OUT, 37, 57) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }
                });
        addSlot(new SlotItemHandler(handler, WaterGasMachineBlockEntity.SLOT_GAS_IN, 131, 21));
        addSlot(
                new SlotItemHandler(handler, WaterGasMachineBlockEntity.SLOT_GAS_OUT, 131, 57) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }
                });
        addSlot(new SlotItemHandler(handler, WaterGasMachineBlockEntity.SLOT_BATTERY, 83, 64));
        for (int i = 0; i < 4; i++) {
            addSlot(
                    new SlotItemHandler(
                            handler, WaterGasMachineBlockEntity.SLOT_UPGRADE_START + i, 153, 19 + i * 18));
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 98 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 156));
        }
    }

    public int storedEu() {
        return (data.get(0) & 0xFFFF) | ((data.get(1) & 0xFFFF) << 16);
    }

    public int capacityEu() {
        return Math.max(1, (data.get(2) & 0xFFFF) | ((data.get(3) & 0xFFFF) << 16));
    }

    public int waterMb() {
        return data.get(4);
    }

    public int waterGasMb() {
        return data.get(5);
    }

    public int progressPx() {
        return data.get(6);
    }

    public boolean isSolarActive() {
        return (data.get(7) & 1) != 0;
    }

    public boolean isActive() {
        return (data.get(7) & 2) != 0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int machineSlots = WaterGasMachineBlockEntity.TOTAL_SLOTS;
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!moveItemStackTo(stack, WaterGasMachineBlockEntity.SLOT_UPGRADE_START, machineSlots, false)
                    && !moveItemStackTo(stack, WaterGasMachineBlockEntity.SLOT_BATTERY, WaterGasMachineBlockEntity.SLOT_BATTERY + 1, false)
                    && !moveItemStackTo(stack, WaterGasMachineBlockEntity.SLOT_COAL_DUST, WaterGasMachineBlockEntity.SLOT_COAL_DUST + 1, false)
                    && !moveItemStackTo(stack, WaterGasMachineBlockEntity.SLOT_WATER_IN, WaterGasMachineBlockEntity.SLOT_WATER_IN + 1, false)
                    && !moveItemStackTo(stack, WaterGasMachineBlockEntity.SLOT_GAS_IN, WaterGasMachineBlockEntity.SLOT_GAS_IN + 1, false)) {
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
        return new ItemStackHandler(WaterGasMachineBlockEntity.TOTAL_SLOTS) {
            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                return switch (slot) {
                    case WaterGasMachineBlockEntity.SLOT_COAL_DUST ->
                            WaterGasMachineBlockEntity.isCoalDust(stack);
                    case WaterGasMachineBlockEntity.SLOT_WATER_IN,
                            WaterGasMachineBlockEntity.SLOT_GAS_IN ->
                            FluidUtil.getFluidHandler(Ecc2BridgePlatform.copyWithCount(stack, 1))
                                    .isPresent();
                    case WaterGasMachineBlockEntity.SLOT_BATTERY ->
                            Ecc2EnergyHelper.canDischarge(stack, 14);
                    case 6, 7, 8, 9 -> Ecc2EnergyHelper.isUpgradeItem(stack);
                    default -> false;
                };
            }
        };
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
}
