package com.ecocraft2.bridge;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Implemented by EcoCraft 2 blocks that visually and physically connect to IC2NE cables.
 *
 * <p>Hooked into {@code com.industrialage.ia2.gameplay.CableBlock#updateConnection} via Mixin.
 */
public interface Ecc2PowerConnectable {

    /**
     * Reports whether an adjacent IC2NE cable should visually connect to the given face of this
     * block.
     *
     * @param state the block state of this block
     * @param side the face of this block touched by the cable
     * @return {@code true} if the cable should form a connection arm toward this block
     */
    default boolean ecc2AcceptsCable(BlockState state, Direction side) {
        return true;
    }
}
