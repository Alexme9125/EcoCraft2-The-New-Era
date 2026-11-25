package com.ecocraft2.solar;

import com.ecocraft2.EcoCraft2;
import com.ecocraft2.config.Ecc2Config;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;

/** NeoForge 1.21.1 3D BlockEntityRenderer for {@code ecc2:molecular_transformer}. */
public final class MolecularTransformerRenderer
        implements BlockEntityRenderer<MolecularTransformerBlockEntity> {

    private static final ResourceLocation FRAME_TEX =
            ResourceLocation.fromNamespaceAndPath(
                    EcoCraft2.MOD_ID, "textures/entity/molecular_transformer.png");
    private static final ResourceLocation PLASMA_TEX =
            ResourceLocation.fromNamespaceAndPath(EcoCraft2.MOD_ID, "textures/entity/plasma.png");
    private static final ResourceLocation PARTICLES_TEX =
            ResourceLocation.fromNamespaceAndPath(
                    EcoCraft2.MOD_ID, "textures/entity/particles.png");

    private final MolecularTransformerModel model = new MolecularTransformerModel();

    public MolecularTransformerRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(
            MolecularTransformerBlockEntity be,
            float partialTick,
            PoseStack pose,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay) {
        var level = be.getLevel();
        if (level == null) {
            return;
        }
        boolean active = be.isActive();
        double animTicks = (level.getGameTime() % 24000L) + partialTick;
        Direction facing =
                be.getBlockState().hasProperty(MolecularTransformerBlock.FACING)
                        ? be.getBlockState().getValue(MolecularTransformerBlock.FACING)
                        : Direction.NORTH;

        // 1. Structural 3D frame & rotating equatorial energy ring
        pose.pushPose();
        pose.translate(0.5, 0.0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        var frameConsumer = buffers.getBuffer(RenderType.entityCutoutNoCull(FRAME_TEX));
        model.frame().render(pose, frameConsumer, packedLight, packedOverlay);

        pose.pushPose();
        pose.translate(0.0, 0.5, 0.0);
        pose.mulPose(Axis.YP.rotationDegrees(MolecularTransformerModel.ringDegrees(animTicks, active)));
        model.energyRing().render(pose, frameConsumer, packedLight, packedOverlay);
        pose.popPose();
        pose.popPose();

        // 2. Animated billboard plasma core & orbiting breathing sparkles
        if (active && Ecc2Config.drawMolecularTransformerCore()) {
            Quaternionf camRot =
                    new Quaternionf(
                            Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
            Quaternionf invCam = new Quaternionf(camRot).conjugate();

            pose.pushPose();
            pose.translate(0.5, 0.5, 0.5);
            pose.mulPose(camRot);

            var plasmaVc = buffers.getBuffer(RenderType.entityTranslucentEmissive(PLASMA_TEX));
            MolecularTransformerModel.emitPlasmaCore(pose.last(), plasmaVc, animTicks);

            var particleVc = buffers.getBuffer(RenderType.entityTranslucentEmissive(PARTICLES_TEX));
            MolecularTransformerModel.emitSparkles(pose.last(), particleVc, invCam, animTicks);
            pose.popPose();
        }
    }
}
