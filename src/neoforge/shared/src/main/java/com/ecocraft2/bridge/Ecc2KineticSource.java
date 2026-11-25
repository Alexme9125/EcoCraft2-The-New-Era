package com.ecocraft2.bridge;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * Implemented by EcoCraft 2 kinetic generator block entities that supply KU to IC2NE's
 * {@code ic2:kinetic_generator}.
 *
 * <p>Hooked into {@code com.industrialage.ia2.industrial.WorkshopKinetic#convert} via Mixin.
 */
public interface Ecc2KineticSource {

    /**
     * Draws kinetic units (KU) from this kinetic source through the specified face.
     *
     * @param level the server level
     * @param fromSide the face of this block entity from which the kinetic generator draws KU
     * @param maxRequestedKu maximum KU requested during this tick
     * @return actual KU delivered, clamped to {@code [0, maxRequestedKu]}
     */
    int ecc2DrawKinetic(ServerLevel level, Direction fromSide, int maxRequestedKu);
}
