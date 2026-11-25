package com.ecocraft2.gas;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.Material;
import org.jetbrains.annotations.Nullable;

/**
 * Seafloor Methane Ice Cluster block (light level 4, iron pickaxe+, drops self with no fortune
 * bonus, stable outside water).
 */
public final class MethaneIceClusterBlock extends Block {

    public MethaneIceClusterBlock() {
        super(
                Properties.of(Material.ICE_SOLID)
                        .strength(2.5f, 4.0f)
                        .sound(SoundType.GLASS)
                        .requiresCorrectToolForDrops()
                        .noOcclusion()
                        .lightLevel(state -> 4));
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable BlockGetter level,
            List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(
                Component.translatable("tooltip.ecc2.methane_ice_cluster")
                        .withStyle(ChatFormatting.AQUA));
    }
}
