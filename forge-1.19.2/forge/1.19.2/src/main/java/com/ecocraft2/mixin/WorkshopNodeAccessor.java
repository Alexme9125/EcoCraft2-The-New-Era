package com.ecocraft2.mixin;

import com.industrialage.ia2.industrial.WorkshopNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Package-crossing primitive field accessors for IC2NE's {@link WorkshopNode}. */
@Mixin(value = WorkshopNode.class, remap = false)
public interface WorkshopNodeAccessor {

    @Accessor("energy")
    double ecc2$energy();

    @Accessor("energy")
    void ecc2$setEnergy(double energy);

    @Accessor("auxiliaryUnits")
    int ecc2$auxiliaryUnits();

    @Accessor("auxiliaryUnits")
    void ecc2$setAuxiliaryUnits(int auxiliaryUnits);
}
