package com.ecocraft2.mixin;

import com.ecocraft2.bridge.Ecc2KineticSource;
import com.ecocraft2.bridge.Ecc2RotorHooks;
import com.industrialage.ia2.industrial.WorkshopNode;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Bridges IC2NE's {@code WorkshopKinetic}:
 *
 * <ol>
 *   <li>Allows {@code ic2:kinetic_generator} to draw KU from any adjacent {@link Ecc2KineticSource}
 *       block entity (EcoCraft 2's 6 advanced wind/water kinetic generators).
 *   <li>Applies 1.0x baseline classic rotor wear (4x on over-wind) to IC2NE's vanilla
 *       {@code wind_kinetic_generator} and {@code water_kinetic_generator}.
 * </ol>
 */
@Mixin(targets = "com.industrialage.ia2.industrial.WorkshopKinetic", remap = false)
public final class WorkshopKineticMixin {

    @Unique
    private static final Logger ECC2_LOG = LoggerFactory.getLogger(WorkshopKineticMixin.class);

    @Unique private static volatile MethodHandle ecc2$inventoryGetter;
    @Unique private static volatile boolean ecc2$inventoryLookupFailed;

    @Inject(method = "convert", at = @At("HEAD"), cancellable = true)
    private static void ecc2$convertExternalKineticSource(
            WorkshopNode node,
            ServerLevel level,
            Direction facing,
            CallbackInfoReturnable<Boolean> cir) {
        var pos = node.getBlockPos().relative(facing);
        if (!level.hasChunkAt(pos)) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof Ecc2KineticSource source)) {
            return;
        }
        var accessor = (WorkshopNodeAccessor) (Object) node;
        var reading = node.bufferedEu();
        int room = (int) ((reading.capacity() - reading.stored()) * 4.0);
        if (room <= 0) {
            accessor.ecc2$setAuxiliaryUnits(0);
            cir.setReturnValue(false);
            return;
        }
        int obtained =
                Math.max(0, Math.min(room, source.ecc2DrawKinetic(level, facing.getOpposite(), room)));
        accessor.ecc2$setAuxiliaryUnits(obtained);
        if (obtained <= 0) {
            cir.setReturnValue(false);
            return;
        }
        accessor.ecc2$setEnergy(reading.stored() + obtained * 0.25);
        node.setChanged();
        cir.setReturnValue(true);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private static void ecc2$tickVanillaRotorWear(
            WorkshopNode node, ServerLevel level, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            return;
        }
        var state = node.getBlockState();
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        boolean isWind = "ic2:wind_kinetic_generator".equals(blockId);
        boolean isWater = "ic2:water_kinetic_generator".equals(blockId);
        if (!isWind && !isWater) {
            return;
        }
        int available = ((WorkshopNodeAccessor) (Object) node).ecc2$auxiliaryUnits();
        if (available <= 0) {
            return;
        }
        var inventory = ecc2$inventory(node);
        if (inventory == null) {
            return;
        }
        int windStrength = 0;
        if (isWind) {
            Direction facing =
                    state.hasProperty(BlockStateProperties.FACING)
                            ? state.getValue(BlockStateProperties.FACING)
                            : state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            windStrength = Ecc2RotorHooks.computeWindStrength(level, node.getBlockPos(), facing);
        }
        Ecc2RotorHooks.tickVanillaRotorWear(node, level, inventory, isWind, windStrength);
    }

    @Unique
    private static Container ecc2$inventory(WorkshopNode node) {
        if (ecc2$inventoryLookupFailed) {
            return null;
        }
        var getter = ecc2$inventoryGetter;
        if (getter == null) {
            try {
                var inventoryType = Class.forName("com.industrialage.ia2.industrial.WorkshopInventory");
                getter =
                        MethodHandles.lookup()
                                .findGetter(WorkshopNode.class, "inventory", inventoryType);
                ecc2$inventoryGetter = getter;
            } catch (ReflectiveOperationException failure) {
                ecc2$inventoryLookupFailed = true;
                ECC2_LOG.error("Failed to bind WorkshopNode.inventory handle", failure);
                return null;
            }
        }
        try {
            return (Container) getter.invoke(node);
        } catch (Throwable failure) {
            ecc2$inventoryLookupFailed = true;
            ECC2_LOG.error("Failed to read WorkshopNode.inventory", failure);
            return null;
        }
    }
}
