package com.ecocraft2.bridge;

import com.ecocraft2.common.config.Ecc2Ids;
import com.industrialage.ia2.wrench.WrenchTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Third-party wrench compatibility helper recognizing {@code #c:tools/wrench} and
 * {@code #c:wrenches} across all EcoCraft 2 machines while explicitly deferring IC2's own wrenches
 * ({@code ic2:wrench} and {@code ic2:electric_wrench}) to IC2NE's native {@link WrenchTarget}
 * handler to avoid double-triggering.
 */
public final class Ecc2WrenchHelper {

    private static final String COMMON_NS = "c";

    private Ecc2WrenchHelper() {}

    /**
     * Returns {@code true} if {@code stack} is a third-party wrench item (in {@code #c:tools/wrench},
     * {@code #c:tools/wrenches}, or {@code #c:wrenches}, excluding {@code ic2:wrench} and
     * {@code ic2:electric_wrench}).
     */
    public static boolean isThirdPartyWrench(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        String id = Ecc2BridgePlatform.itemId(stack);
        if ("mhera:wrench".equals(id) || "mhera:electric_wrench".equals(id)) {
            return false;
        }
        return Ecc2BridgePlatform.hasItemTag(
                        stack, Ecc2Ids.commonTag(COMMON_NS, Ecc2Ids.COMMON_TAG_TOOLS_WRENCH))
                || Ecc2BridgePlatform.hasItemTag(stack, Ecc2Ids.commonTag(COMMON_NS, "tools/wrenches"))
                || Ecc2BridgePlatform.hasItemTag(
                        stack, Ecc2Ids.commonTag(COMMON_NS, Ecc2Ids.COMMON_TAG_WRENCHES));
    }

    /** Returns {@code true} if {@code block} is an EcoCraft 2 {@link WrenchTarget} machine block. */
    public static boolean isEcc2WrenchTarget(Block block) {
        return block instanceof WrenchTarget
                && block.getClass().getName().startsWith("com.ecocraft2.");
    }

    /**
     * Handles right-click (rotate) and sneak-right-click (safe dismantle) on an EcoCraft 2 machine
     * block when the player is holding a third-party wrench.
     *
     * @return {@code true} if the interaction was consumed as a wrench action
     */
    public static boolean tryHandleThirdPartyWrench(
            Block block,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            Direction hitFace) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isThirdPartyWrench(stack)) {
            return false;
        }
        if (block instanceof WrenchTarget target) {
            if (!target.wrenchable(state) || !target.wrenchPermits(level, pos, player)) {
                return false;
            }
        }
        if (!(level instanceof ServerLevel server)) {
            return true;
        }
        if (player.isShiftKeyDown()) {
            Block.popResource(server, pos, new ItemStack(block));
            server.playSound(
                    null,
                    pos,
                    state.getSoundType().getBreakSound(),
                    SoundSource.BLOCKS,
                    0.8F,
                    1.0F);
            server.removeBlock(pos, false);
            if (!player.isCreative() && stack.isDamageableItem()) {
                EquipmentSlot slot =
                        hand == InteractionHand.MAIN_HAND
                                ? EquipmentSlot.MAINHAND
                                : EquipmentSlot.OFFHAND;
                stack.hurtAndBreak(1, player, slot);
            }
            return true;
        }
        BlockState rotated = rotateForWrench(state, hitFace);
        if (!rotated.equals(state)) {
            server.setBlock(pos, rotated, Block.UPDATE_ALL);
            server.playSound(
                    null,
                    pos,
                    state.getSoundType().getPlaceSound(),
                    SoundSource.BLOCKS,
                    0.6F,
                    1.2F);
        }
        return true;
    }

    private static BlockState rotateForWrench(BlockState state, Direction hitFace) {
        if (state.hasProperty(BlockStateProperties.FACING)) {
            Direction current = state.getValue(BlockStateProperties.FACING);
            Direction next = (hitFace != null && hitFace != current) ? hitFace : current.getOpposite();
            return state.setValue(BlockStateProperties.FACING, next);
        }
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            Direction current = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            if (hitFace != null && hitFace.getAxis().isHorizontal() && hitFace != current) {
                return state.setValue(BlockStateProperties.HORIZONTAL_FACING, hitFace);
            }
            return state.rotate(Rotation.CLOCKWISE_90);
        }
        return state;
    }
}
