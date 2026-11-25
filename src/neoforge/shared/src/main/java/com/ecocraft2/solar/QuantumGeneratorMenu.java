package com.ecocraft2.solar;

import com.ecocraft2.common.solar.SolarTier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Container menu for the creative {@code ecc2:quantum_generator}. */
public final class QuantumGeneratorMenu extends AbstractContainerMenu {

    private static final int DATA_SLOTS = 5;

    private final BlockPos blockPos;
    private final QuantumGeneratorBlockEntity blockEntity;
    private final ContainerData data;

    /** Client constructor invoked via {@code IMenuTypeExtension}. */
    public QuantumGeneratorMenu(int containerId, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInv, buf.readBlockPos(), null);
    }

    /** Server constructor. */
    public QuantumGeneratorMenu(
            int containerId, Inventory playerInv, QuantumGeneratorBlockEntity blockEntity) {
        this(containerId, playerInv, blockEntity.getBlockPos(), blockEntity);
    }

    private QuantumGeneratorMenu(
            int containerId,
            Inventory playerInv,
            BlockPos pos,
            QuantumGeneratorBlockEntity blockEntity) {
        super(SolarKineticModule.QUANTUM_GENERATOR_MENU.get(), containerId);
        this.blockPos = pos;
        this.blockEntity = blockEntity;
        this.data =
                blockEntity != null
                        ? new GeneratorData(blockEntity)
                        : new SimpleContainerData(DATA_SLOTS);

        // Player inventory matching assets-gen/gui_coordinates.json: top-left (8, 111), hotbar (8, 169)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 111 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 169));
        }

        addDataSlots(this.data);
    }

    public BlockPos blockPos() {
        return blockPos;
    }

    public boolean isForPos(BlockPos pos) {
        return blockPos != null && blockPos.equals(pos);
    }

    public int production() {
        return Math.max(0, Ecc2DataSync.combine32(data.get(0), data.get(1)));
    }

    public int tier() {
        return Math.max(1, Math.min(6, data.get(2)));
    }

    public boolean isActive() {
        return data.get(3) != 0;
    }

    public int packetCount() {
        long cap = (long) SolarTier.packetSize(tier());
        int prod = production();
        return prod > cap ? (int) (((long) prod + cap - 1L) / cap) : 1;
    }

    public double offeredPerPacket() {
        if (!isActive() || production() <= 0) {
            return 0.0;
        }
        return (double) production() / packetCount();
    }

    public double tierPacketSize() {
        return SolarTier.packetSize(tier());
    }

    @Override
    public boolean stillValid(Player player) {
        if (blockEntity == null) {
            return true;
        }
        return !blockEntity.isRemoved()
                && player.level() == blockEntity.getLevel()
                && player.distanceToSqr(Vec3.atCenterOf(blockPos)) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < 27) {
            if (!moveItemStackTo(stack, 27, 36, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, 27, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    private record GeneratorData(QuantumGeneratorBlockEntity entity) implements ContainerData {
        @Override
        public int get(int index) {
            int prod = entity.getProduction();
            return switch (index) {
                case 0 -> Ecc2DataSync.lo16(prod);
                case 1 -> Ecc2DataSync.hi16(prod);
                case 2 -> entity.getTier();
                case 3 -> entity.isActive() ? 1 : 0;
                case 4 -> entity.getPacketCount();
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
