package com.ecocraft2.solar;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** NeoForge 1.21.1 base block unifying interaction, neighbor updates, and inventory drops. */
public abstract class Ecc2BaseBlock extends Block implements EntityBlock {

    protected Ecc2BaseBlock(Properties properties) {
        super(properties);
    }

    /** Handles right-click interaction on this block. */
    protected InteractionResult onEcc2Use(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    /** Handles neighbor block/redstone changes on this block. */
    protected void onEcc2NeighborChanged(BlockState state, Level level, BlockPos pos) {}

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return onEcc2Use(state, level, pos, player, InteractionHand.MAIN_HAND, hit);
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        var result = onEcc2Use(state, level, pos, player, hand, hit);
        return result.consumesAction()
                ? ItemInteractionResult.sidedSuccess(level.isClientSide)
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected void neighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
            Block neighborBlock,
            BlockPos neighborPos,
            boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        onEcc2NeighborChanged(state, level, pos);
    }

    @Override
    protected void onRemove(
            BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())
                && level instanceof ServerLevel server
                && level.getBlockEntity(pos) instanceof Ecc2SaveBlockEntity entity) {
            var inv = entity.dropInventory();
            if (inv != null) {
                Containers.dropContents(server, pos, inv);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
