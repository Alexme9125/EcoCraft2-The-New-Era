package com.ecocraft2.gas;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Seafloor Methane Ice Cluster ({@code ecc2:methane_ice_cluster}): translucent icy-blue crystalline
 * hydrate emitting light level 4, requiring an iron pickaxe or better, dropping itself cleanly, and
 * remaining stable outside water and in heat.
 */
public final class MethaneIceClusterBlock extends TransparentBlock {

    public MethaneIceClusterBlock(Properties properties) {
        super(properties);
    }

    public static BlockBehaviour.Properties defaultProperties() {
        return BlockBehaviour.Properties.of()
                .strength(2.5F, 4.0F)
                .sound(SoundType.GLASS)
                .requiresCorrectToolForDrops()
                .noOcclusion()
                .lightLevel(state -> 4);
    }
}
