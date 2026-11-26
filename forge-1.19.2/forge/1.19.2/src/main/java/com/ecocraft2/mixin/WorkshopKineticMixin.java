package com.ecocraft2.mixin;

import com.ecocraft2.bridge.Ecc2KineticSource;
import com.industrialage.ia2.industrial.WorkshopNode;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Bridges IC2NE's {@code WorkshopKinetic} so {@code ic2:kinetic_generator} draws KU natively from
 * any adjacent {@link Ecc2KineticSource} block entity (EcoCraft 2's 6 advanced wind/water kinetic
 * generators).
 *
 * <p>Vanilla IC2NE wind/water rotor durability, wear, and tooltips are implemented natively by
 * base IC2NE and are not duplicated here.
 */
@Mixin(targets = "com.industrialage.ia2.industrial.WorkshopKinetic", remap = false)
public final class WorkshopKineticMixin {

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
                Math.max(
                        0,
                        Math.min(room, source.ecc2DrawKinetic(level, facing.getOpposite(), room)));
        accessor.ecc2$setAuxiliaryUnits(obtained);
        if (obtained <= 0) {
            cir.setReturnValue(false);
            return;
        }
        accessor.ecc2$setEnergy(reading.stored() + obtained * 0.25);
        node.setChanged();
        cir.setReturnValue(true);
    }
}
