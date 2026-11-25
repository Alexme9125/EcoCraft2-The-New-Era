package com.ecocraft2.solar;

import com.ecocraft2.EcoCraft2;
import com.ecocraft2.config.Ecc2Config;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** NeoForge 26.x 3D BlockEntityRenderer for {@code ecc2:molecular_transformer}. */
public final class MolecularTransformerRenderer
        implements BlockEntityRenderer<
                MolecularTransformerBlockEntity, MolecularTransformerRenderer.State> {

    private static final RenderType FRAME_LAYER =
            RenderTypes.entityCutout(
                    Identifier.fromNamespaceAndPath(
                            EcoCraft2.MOD_ID, "textures/entity/molecular_transformer.png"));
    private static final RenderType PLASMA_LAYER =
            RenderTypes.entityTranslucentEmissive(
                    Identifier.fromNamespaceAndPath(
                            EcoCraft2.MOD_ID, "textures/entity/plasma.png"));
    private static final RenderType PARTICLES_LAYER =
            RenderTypes.entityTranslucentEmissive(
                    Identifier.fromNamespaceAndPath(
                            EcoCraft2.MOD_ID, "textures/entity/particles.png"));

    private final MolecularTransformerModel model = new MolecularTransformerModel();

    public MolecularTransformerRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(
            MolecularTransformerBlockEntity be,
            State state,
            float partialTick,
            Vec3 camera,
            CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTick, camera, crumbling);
        state.active = be.isActive();
        state.facing =
                be.getBlockState().hasProperty(MolecularTransformerBlock.FACING)
                        ? be.getBlockState().getValue(MolecularTransformerBlock.FACING)
                        : Direction.NORTH;
        var level = be.getLevel();
        state.animTicks = level != null ? (level.getGameTime() % 24000L) + partialTick : 0.0;
    }

    @Override
    public void submit(
            State state,
            PoseStack pose,
            SubmitNodeCollector collector,
            CameraRenderState camera) {
        // 1. Structural 3D frame & rotating equatorial energy ring
        pose.pushPose();
        pose.translate(0.5, 0.0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-state.facing.toYRot()));
        collector.submitModelPart(
                model.frame(), pose, FRAME_LAYER, state.lightCoords, OverlayTexture.NO_OVERLAY, null);

        pose.pushPose();
        pose.translate(0.0, 0.5, 0.0);
        pose.mulPose(
                Axis.YP.rotationDegrees(
                        MolecularTransformerModel.ringDegrees(state.animTicks, state.active)));
        collector.submitModelPart(
                model.energyRing(),
                pose,
                FRAME_LAYER,
                state.lightCoords,
                OverlayTexture.NO_OVERLAY,
                null);
        pose.popPose();
        pose.popPose();

        // 2. Animated billboard plasma core & orbiting breathing sparkles
        if (state.active && Ecc2Config.drawMolecularTransformerCore()) {
            final double ticks = state.animTicks;
            final Quaternionf camRot =
                    camera.orientation != null
                            ? new Quaternionf(camera.orientation)
                            : new Quaternionf();
            final Quaternionf invCam = new Quaternionf(camRot).conjugate();

            pose.pushPose();
            pose.translate(0.5, 0.5, 0.5);
            pose.mulPose(camRot);

            collector.submitCustomGeometry(
                    pose,
                    PLASMA_LAYER,
                    (poseEntry, vc) ->
                            MolecularTransformerModel.emitPlasmaCore(poseEntry, vc, ticks));
            collector.submitCustomGeometry(
                    pose,
                    PARTICLES_LAYER,
                    (poseEntry, vc) ->
                            MolecularTransformerModel.emitSparkles(poseEntry, vc, invCam, ticks));
            pose.popPose();
        }
    }

    public static final class State extends BlockEntityRenderState {
        boolean active;
        Direction facing = Direction.NORTH;
        double animTicks;
    }
}
