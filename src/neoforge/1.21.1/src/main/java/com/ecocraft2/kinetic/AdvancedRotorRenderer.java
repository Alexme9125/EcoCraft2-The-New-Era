package com.ecocraft2.kinetic;

import com.ecocraft2.common.kinetic.RotorSpec;
import com.ecocraft2.config.Ecc2Config;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.EnumMap;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/** NeoForge 1.21.1 3D spinning rotor renderer for Advanced Wind &amp; Water Kinetic Generators. */
public final class AdvancedRotorRenderer
        implements BlockEntityRenderer<AdvancedKineticBlockEntity> {

    private final AdvancedRotorModel model = new AdvancedRotorModel();
    private final EnumMap<RotorSpec, RenderType> layers = new EnumMap<>(RotorSpec.class);

    public AdvancedRotorRenderer(BlockEntityRendererProvider.Context context) {
        for (RotorSpec spec : RotorSpec.values()) {
            var tex =
                    ResourceLocation.fromNamespaceAndPath(
                            "mhera", AdvancedRotorModel.ic2TexturePath(spec));
            layers.put(spec, RenderType.entityCutout(tex));
        }
    }

    @Override
    public void render(
            AdvancedKineticBlockEntity be,
            float partialTick,
            PoseStack pose,
            MultiBufferSource buffers,
            int packedLight,
            int overlay) {
        RotorSpec spec = be.getVisualRotorSpec();
        var level = be.getLevel();
        if (spec == null || level == null) {
            return;
        }
        float angle = model.angle(be, spec, partialTick, Ecc2Config.enableRotorRenderAnimation());
        Direction face = be.getRotorFace();
        var parts = model.parts(be.isWaterType(), spec);
        var vertices = buffers.getBuffer(layers.get(spec));
        int light = LevelRenderer.getLightColor(level, be.getBlockPos().relative(face));

        pose.pushPose();
        AdvancedRotorModel.translateToHub(pose, face);
        pose.mulPose(
                face.getAxis().isVertical()
                        ? Axis.XP.rotationDegrees(-90.0f * face.getStepY())
                        : Axis.YP.rotationDegrees(-face.toYRot()));
        parts.shaft().render(pose, vertices, light, overlay);
        pose.mulPose(Axis.ZP.rotationDegrees(angle));
        parts.wheel().render(pose, vertices, light, overlay);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(AdvancedKineticBlockEntity be) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(AdvancedKineticBlockEntity be) {
        return AdvancedRotorModel.renderBounds(be);
    }
}
