package com.ecocraft2.solar;

import com.ecocraft2.bridge.Ecc2WrenchHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

/** NeoForge 26.x base block unifying interaction and neighbor updates. */
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
    protected InteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        if (Ecc2WrenchHelper.tryHandleThirdPartyWrench(
                this, state, level, pos, player, hand, hit.getDirection())) {
            return InteractionResult.SUCCESS;
        }
        var result = onEcc2Use(state, level, pos, player, hand, hit);
        return result.consumesAction()
                ? InteractionResult.SUCCESS
                : InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected void neighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
            Block neighborBlock,
            Orientation orientation,
            boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
        onEcc2NeighborChanged(state, level, pos);
    }
}
