package com.ecocraft2.client;

import com.ecocraft2.bridge.Ecc2BridgePlatform;
import com.ecocraft2.config.Ecc2Config;
import com.ecocraft2.solar.MolecularTransformerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * 3D BlockEntityRenderer for the Molecular Transformer rendering an animated pulsing plasma core
 * (4x4 {@code plasma.png}) and orbiting energy particles (8x8 {@code particles.png}), enabled by
 * default via {@link Ecc2Config#drawMolecularTransformerCore()}.
 */
public final class MolecularTransformerRenderer
        implements BlockEntityRenderer<MolecularTransformerBlockEntity> {

    private static final ResourceLocation PLASMA_TEX =
            Ecc2BridgePlatform.rl("textures/entity/plasma.png");
    private static final ResourceLocation PARTICLES_TEX =
            Ecc2BridgePlatform.rl("textures/entity/particles.png");

    public MolecularTransformerRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(
            MolecularTransformerBlockEntity be,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay) {
        if (!Ecc2Config.drawMolecularTransformerCore()) {
            return;
        }
        long gameTime = be.getLevel() != null ? be.getLevel().getGameTime() : 0L;
        float t = gameTime + partialTick;
        boolean active = be.isClientActive();
        float pulse = active ? (0.30f + 0.05f * (float) Math.sin(t * 0.25f)) : 0.17f;

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);

        // Billboard plasma core facing camera
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        poseStack.mulPose(camera.rotation());

        int frame = (int) (t * (active ? 0.5f : 0.15f)) & 15;
        float u0 = (frame % 4) * 0.25f;
        float v0 = (frame / 4) * 0.25f;

        VertexConsumer plasmaBuf = buffers.getBuffer(RenderType.entityTranslucent(PLASMA_TEX));
        drawBillboardQuad(
                poseStack,
                plasmaBuf,
                pulse,
                u0,
                v0,
                u0 + 0.25f,
                v0 + 0.25f,
                active ? 255 : 160);
        poseStack.popPose();

        // Orbiting particle sparks when active
        if (active) {
            VertexConsumer sparkBuf = buffers.getBuffer(RenderType.entityTranslucent(PARTICLES_TEX));
            for (int i = 0; i < 4; i++) {
                float angle = t * 0.18f + i * ((float) Math.PI * 0.5f);
                float rx = 0.24f * (float) Math.cos(angle);
                float rz = 0.24f * (float) Math.sin(angle);
                float ry = 0.10f * (float) Math.sin(t * 0.22f + i * 1.4f);

                poseStack.pushPose();
                poseStack.translate(0.5 + rx, 0.5 + ry, 0.5 + rz);
                poseStack.mulPose(camera.rotation());
                poseStack.mulPose(Vector3f.ZP.rotationDegrees(t * 6.0f + i * 90.0f));
                int pFrame = ((int) (t * 0.6f) + i * 7) & 63;
                float pu0 = (pFrame % 8) * 0.125f;
                float pv0 = (pFrame / 8) * 0.125f;
                drawBillboardQuad(
                        poseStack, sparkBuf, 0.09f, pu0, pv0, pu0 + 0.125f, pv0 + 0.125f, 230);
                poseStack.popPose();
            }
        }
    }

    private static void drawBillboardQuad(
            PoseStack poseStack,
            VertexConsumer vc,
            float r,
            float u0,
            float v0,
            float u1,
            float v1,
            int alpha) {
        PoseStack.Pose last = poseStack.last();
        Matrix4f m = last.pose();
        Matrix3f n = last.normal();
        int light = LightTexture.FULL_BRIGHT;
        vc.vertex(m, -r, -r, 0).color(220, 250, 255, alpha).uv(u0, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 0, 1).endVertex();
        vc.vertex(m, r, -r, 0).color(220, 250, 255, alpha).uv(u1, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 0, 1).endVertex();
        vc.vertex(m, r, r, 0).color(220, 250, 255, alpha).uv(u1, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 0, 1).endVertex();
        vc.vertex(m, -r, r, 0).color(220, 250, 255, alpha).uv(u0, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 0, 1).endVertex();
    }
}
