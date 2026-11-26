package com.ecocraft2.client;

import com.ecocraft2.bridge.Ecc2BridgePlatform;
import com.ecocraft2.config.Ecc2Config;
import com.ecocraft2.kinetic.AdvancedKineticBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/**
 * 3D spinning rotor BlockEntityRenderer for all 6 Advanced Wind & Water Kinetic Generators.
 * Mounts on {@link AdvancedKineticBlockEntity#rotorFace()} and rotates smoothly by rotor speed.
 */
public final class AdvancedRotorRenderer
        implements BlockEntityRenderer<AdvancedKineticBlockEntity> {

    private static final ResourceLocation WHITE_TEX =
            Ecc2BridgePlatform.rl("textures/block/giant_casing_macerator_side.png");

    public AdvancedRotorRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(
            AdvancedKineticBlockEntity be,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay) {
        int rotorType = be.visualRotorType();
        if (rotorType <= 0) {
            return;
        }
        Direction face = be.rotorFace();
        float radius = bladeLength(be.isWater(), rotorType);
        int rgb = rotorColor(rotorType);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        long time = be.getLevel() != null ? be.getLevel().getGameTime() : 0L;
        float speed = Ecc2Config.enableRotorRenderAnimation() ? be.visualRotorSpeed() : 0.0f;
        float angle = ((time + partialTick) * speed * 0.25f) % 360.0f;

        poseStack.pushPose();
        poseStack.translate(
                0.5 + face.getStepX() * 0.56,
                0.5 + face.getStepY() * 0.56,
                0.5 + face.getStepZ() * 0.56);

        orientToFace(poseStack, face);
        poseStack.mulPose(Vector3f.ZP.rotationDegrees(angle));

        VertexConsumer vc = buffers.getBuffer(RenderType.entitySolid(WHITE_TEX));
        // Central hub box
        drawBox(poseStack, vc, -0.14f, -0.14f, -0.06f, 0.14f, 0.14f, 0.06f, 80, 92, 108, packedLight);
        // 4 symmetric blades
        for (int i = 0; i < 4; i++) {
            poseStack.pushPose();
            poseStack.mulPose(Vector3f.ZP.rotationDegrees(i * 90.0f));
            poseStack.mulPose(Vector3f.YP.rotationDegrees(14.0f));
            drawBox(
                    poseStack,
                    vc,
                    -0.12f,
                    0.12f,
                    -0.025f,
                    0.12f,
                    radius,
                    0.025f,
                    r,
                    g,
                    b,
                    packedLight);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(AdvancedKineticBlockEntity be) {
        return true;
    }

    private static void orientToFace(PoseStack poseStack, Direction face) {
        switch (face) {
            case SOUTH -> {}
            case NORTH -> poseStack.mulPose(Vector3f.YP.rotationDegrees(180.0f));
            case EAST -> poseStack.mulPose(Vector3f.YP.rotationDegrees(90.0f));
            case WEST -> poseStack.mulPose(Vector3f.YP.rotationDegrees(-90.0f));
            case UP -> poseStack.mulPose(Vector3f.XP.rotationDegrees(-90.0f));
            case DOWN -> poseStack.mulPose(Vector3f.XP.rotationDegrees(90.0f));
        }
    }

    private static float bladeLength(boolean water, int rotorType) {
        int diameter =
                switch (rotorType) {
                    case 1 -> water ? 4 : 5;
                    case 2, 3 -> water ? 4 : 7;
                    case 4 -> water ? 6 : 9;
                    case 5 -> water ? 8 : 11;
                    default -> 5;
                };
        return Math.max(1.2f, diameter * 0.45f);
    }

    private static int rotorColor(int rotorType) {
        return switch (rotorType) {
            case 1 -> 0xB88754; // Wood
            case 2 -> 0xC8D2DC; // Iron
            case 3 -> 0xD49B4E; // Bronze
            case 4 -> 0x7A8796; // Steel
            case 5 -> 0x2B333E; // Carbon
            default -> 0xA0B0C0;
        };
    }

    private static void drawBox(
            PoseStack poseStack,
            VertexConsumer vc,
            float x0,
            float y0,
            float z0,
            float x1,
            float y1,
            float z1,
            int r,
            int g,
            int b,
            int light) {
        PoseStack.Pose last = poseStack.last();
        Matrix4f m = last.pose();
        Matrix3f n = last.normal();
        // Front (+Z)
        quad(vc, m, n, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1, r, g, b, light);
        // Back (-Z)
        quad(vc, m, n, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1, r, g, b, light);
        // Right (+X)
        quad(vc, m, n, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1, 0, 0, r, g, b, light);
        // Left (-X)
        quad(vc, m, n, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0, r, g, b, light);
        // Top (+Y)
        quad(vc, m, n, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0, r, g, b, light);
        // Bottom (-Y)
        quad(vc, m, n, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0, r, g, b, light);
    }

    private static void quad(
            VertexConsumer vc,
            Matrix4f m,
            Matrix3f n,
            float x0,
            float y0,
            float z0,
            float x1,
            float y1,
            float z1,
            float x2,
            float y2,
            float z2,
            float x3,
            float y3,
            float z3,
            float nx,
            float ny,
            float nz,
            int r,
            int g,
            int b,
            int light) {
        vc.vertex(m, x0, y0, z0).color(r, g, b, 255).uv(0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();
        vc.vertex(m, x1, y1, z1).color(r, g, b, 255).uv(1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();
        vc.vertex(m, x2, y2, z2).color(r, g, b, 255).uv(1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();
        vc.vertex(m, x3, y3, z3).color(r, g, b, 255).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();
    }
}
