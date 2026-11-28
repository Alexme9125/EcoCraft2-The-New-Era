package com.ecocraft2.kinetic;

import com.ecocraft2.common.kinetic.RotorSpec;
import com.ecocraft2.config.Ecc2Config;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.EnumMap;
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
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** NeoForge 26.x 3D spinning rotor renderer for Advanced Wind &amp; Water Kinetic Generators. */
public final class AdvancedRotorRenderer
        implements BlockEntityRenderer<AdvancedKineticBlockEntity, AdvancedRotorRenderer.State> {

    private final AdvancedRotorModel model = new AdvancedRotorModel();
    private final EnumMap<RotorSpec, RenderType> layers = new EnumMap<>(RotorSpec.class);

    public AdvancedRotorRenderer(BlockEntityRendererProvider.Context context) {
        for (RotorSpec spec : RotorSpec.values()) {
            var tex =
                    Identifier.fromNamespaceAndPath(
                            "mhera", AdvancedRotorModel.ic2TexturePath(spec));
            layers.put(spec, RenderTypes.entityCutoutCull(tex));
        }
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(
            AdvancedKineticBlockEntity be,
            State state,
            float partialTick,
            Vec3 camera,
            CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTick, camera, crumbling);
        state.spec = be.getVisualRotorSpec();
        state.waterType = be.isWaterType();
        state.angle =
                model.angle(be, state.spec, partialTick, Ecc2Config.enableRotorRenderAnimation());
        if (state.spec != null) {
            state.face = be.getRotorFace();
            var level = be.getLevel();
            if (level != null) {
                var lightAt = be.getBlockPos().relative(state.face);
                state.lightCoords =
                        LightCoordsUtil.pack(
                                level.getBrightness(LightLayer.BLOCK, lightAt),
                                level.getBrightness(LightLayer.SKY, lightAt));
            }
        }
    }

    @Override
    public void submit(
            State state,
            PoseStack pose,
            SubmitNodeCollector collector,
            CameraRenderState camera) {
        if (state.spec == null) {
            return;
        }
        var parts = model.parts(state.waterType, state.spec);
        var layer = layers.get(state.spec);

        pose.pushPose();
        AdvancedRotorModel.translateToHub(pose, state.face);
        pose.mulPose(
                state.face.getAxis().isVertical()
                        ? Axis.XP.rotationDegrees(-90.0f * state.face.getStepY())
                        : Axis.YP.rotationDegrees(-state.face.toYRot()));
        collector.submitModelPart(
                parts.shaft(), pose, layer, state.lightCoords, OverlayTexture.NO_OVERLAY, null);
        pose.mulPose(Axis.ZP.rotationDegrees(state.angle));
        collector.submitModelPart(
                parts.wheel(), pose, layer, state.lightCoords, OverlayTexture.NO_OVERLAY, null);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(AdvancedKineticBlockEntity be) {
        return AdvancedRotorModel.renderBounds(be);
    }

    public static final class State extends BlockEntityRenderState {
        RotorSpec spec;
        boolean waterType;
        Direction face = Direction.NORTH;
        float angle;
    }
}
