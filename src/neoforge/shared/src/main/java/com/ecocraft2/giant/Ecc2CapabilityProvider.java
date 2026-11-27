package com.ecocraft2.giant;

import com.industrialage.ia2.access.ProcessingSlots;
import net.minecraft.core.Direction;

/**
 * Implemented by EcoCraft 2 BlockEntities that expose sided item and/or fluid capabilities across
 * NeoForge 1.21.1 and 26.x.
 */
public interface Ecc2CapabilityProvider {

    /**
     * Returns the sided {@link ProcessingSlots} view for {@code side}, or {@code null} if items
     * cannot connect on that face.
     */
    ProcessingSlots itemCapabilitySlots(Direction side);

    /**
     * Returns the fluid tank view for {@code side}, or {@code null} if fluids cannot connect on
     * that face.
     */
    Ecc2FluidTankView fluidCapabilityTank(Direction side);

    /**
     * When {@code true}, wraps capabilities with IC2NE's 6-side {@code MachinePorts} permissions.
     * Giant Machines return {@code false} because they enforce their own 8 lower-corner port
     * permissions directly.
     */
    default boolean usesMachineAccess() {
        return true;
    }

    /** Additional live input gate checked on every item transfer when {@link #usesMachineAccess()} is false. */
    default boolean allowsItemInput(Direction side) {
        return true;
    }

    /** Additional live output gate checked on every item transfer when {@link #usesMachineAccess()} is false. */
    default boolean allowsItemOutput(Direction side) {
        return true;
    }

    /**
     * Notifies the provider after an external automation transfer has committed inserted items
     * into container slot {@code slot}. Never invoked for simulated or rolled-back transfers.
     */
    default void onAutomationInputInserted(int slot) {}
}
