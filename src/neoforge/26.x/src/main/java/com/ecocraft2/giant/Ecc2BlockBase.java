package com.ecocraft2.giant;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** NeoForge 26.x base block bridging interaction hooks (removal handled via BlockEntity#preRemoveSideEffects). */
public abstract class Ecc2BlockBase extends Block {

    protected Ecc2BlockBase(Properties properties) {
        super(properties);
    }

    /** Handles block right-click interaction across 1.21.1 and 26.x. */
    protected abstract boolean onUseBlock(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            Direction hitFace);

    @Override
    protected InteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        return onUseBlock(state, level, pos, player, hand, hit.getDirection())
                ? InteractionResult.SUCCESS
                : InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return onUseBlock(state, level, pos, player, InteractionHand.MAIN_HAND, hit.getDirection())
                ? InteractionResult.SUCCESS
                : InteractionResult.PASS;
    }
}
