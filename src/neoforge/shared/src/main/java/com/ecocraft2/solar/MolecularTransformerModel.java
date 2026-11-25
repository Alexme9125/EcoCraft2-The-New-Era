package com.ecocraft2.solar;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Quaternionf;

/**
 * Shared 3D geometry and frame-rate-independent billboard math for the Molecular Transformer
 * renderer across NeoForge 1.21.1 and 26.x.
 *
 * <p>Uses {@code ecc2:textures/entity/molecular_transformer.png} (128×64), {@code plasma.png}
 * (4×4 grid), and {@code particles.png} (8×8 grid).
 */
public final class MolecularTransformerModel {

    private static final int PLASMA_GRID = 4;
    private static final int PARTICLE_GRID = 8;
    private static final int FULL_BRIGHT_LIGHT = 0x00F000F0;

    private final ModelPart root;
    private final ModelPart frame;
    private final ModelPart energyRing;

    public MolecularTransformerModel() {
        this.root = bake();
        this.frame = root.getChild("frame");
        this.energyRing = root.getChild("ring");
    }

    public ModelPart frame() {
        return frame;
    }

    public ModelPart energyRing() {
        return energyRing;
    }

    /** Computes smooth continuous ring rotation degrees from world game time + partialTick. */
    public static float ringDegrees(double animTicks, boolean active) {
        return active ? (float) ((animTicks * 4.5) % 360.0) : (float) ((animTicks * 0.6) % 360.0);
    }

    /**
     * Emits the animated glowing plasma core quad facing {@code cameraRotation} onto
     * {@code consumer}.
     */
    public static void emitPlasmaCore(
            PoseStack.Pose pose, VertexConsumer consumer, double animTicks) {
        int frameIdx = ((int) Math.floor(animTicks * 0.5)) & 15;
        int col = frameIdx % PLASMA_GRID;
        int row = frameIdx / PLASMA_GRID;
        float step = 1.0f / PLASMA_GRID;
        float u0 = col * step;
        float v0 = row * step;
        float u1 = u0 + step;
        float v1 = v0 + step;

        float radius = 0.24f + 0.035f * (float) Math.sin(animTicks * 0.22);
        emitQuad(pose, consumer, 0f, 0f, 0f, radius, u0, v0, u1, v1, 255, 255, 255, 245);
    }

    /**
     * Emits six breathing, orbiting sparkle motes from the 8×8 particle atlas around the plasma
     * core.
     */
    public static void emitSparkles(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            Quaternionf inverseCamera,
            double animTicks) {
        float step = 1.0f / PARTICLE_GRID;
        for (int i = 0; i < 6; i++) {
            double phase = animTicks * 0.14 + i * (Math.PI / 3.0);
            float orbitR = 0.26f + 0.04f * (float) Math.sin(animTicks * 0.19 + i * 1.3);
            float ox = (float) Math.cos(phase) * orbitR;
            float oy = (float) Math.sin(animTicks * 0.17 + i * 1.7) * 0.15f;
            float oz = (float) Math.sin(phase) * orbitR;

            // Project orbit offset into camera billboard plane coordinates
            org.joml.Vector3f local = new org.joml.Vector3f(ox, oy, oz);
            inverseCamera.transform(local);

            int cell = (((int) Math.floor(animTicks * 0.45)) + i * 11) & 63;
            int gx = cell % PARTICLE_GRID;
            int gy = cell / PARTICLE_GRID;
            float u0 = gx * step;
            float v0 = gy * step;
            float u1 = u0 + step;
            float v1 = v0 + step;

            float pulse = 0.55f + 0.45f * (float) Math.sin(animTicks * 0.28 + i * 2.1);
            float size = 0.065f + 0.035f * pulse;
            int alpha = Math.max(90, Math.min(255, (int) (140 + 115 * pulse)));
            emitQuad(
                    pose,
                    consumer,
                    local.x,
                    local.y,
                    local.z,
                    size,
                    u0,
                    v0,
                    u1,
                    v1,
                    220,
                    250,
                    255,
                    alpha);
        }
    }

    private static void emitQuad(
            PoseStack.Pose pose,
            VertexConsumer vc,
            float cx,
            float cy,
            float cz,
            float r,
            float u0,
            float v0,
            float u1,
            float v1,
            int red,
            int green,
            int blue,
            int alpha) {
        int light = FULL_BRIGHT_LIGHT;
        int overlay = OverlayTexture.NO_OVERLAY;
        vc.addVertex(pose, cx - r, cy - r, cz)
                .setColor(red, green, blue, alpha)
                .setUv(u0, v1)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, 0f, 0f, 1f);
        vc.addVertex(pose, cx + r, cy - r, cz)
                .setColor(red, green, blue, alpha)
                .setUv(u1, v1)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, 0f, 0f, 1f);
        vc.addVertex(pose, cx + r, cy + r, cz)
                .setColor(red, green, blue, alpha)
                .setUv(u1, v0)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, 0f, 0f, 1f);
        vc.addVertex(pose, cx - r, cy + r, cz)
                .setColor(red, green, blue, alpha)
                .setUv(u0, v0)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, 0f, 0f, 1f);
    }

    private static ModelPart bake() {
        var mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Pedestal, emitter cap, and 3 vertical containment pillars centered around origin (y: 0..16)
        root.addOrReplaceChild(
                "frame",
                CubeListBuilder.create()
                        // Lower pedestal (14x3x14)
                        .texOffs(0, 0)
                        .addBox(-7.0f, 0.0f, -7.0f, 14.0f, 3.0f, 14.0f)
                        // Lower emitter collar (10x1x10)
                        .texOffs(0, 18)
                        .addBox(-5.0f, 3.0f, -5.0f, 10.0f, 1.0f, 10.0f)
                        // Upper emitter cap (12x3x12)
                        .texOffs(0, 0)
                        .addBox(-6.0f, 13.0f, -6.0f, 12.0f, 3.0f, 12.0f)
                        // Upper emitter collar (8x1x8)
                        .texOffs(0, 18)
                        .addBox(-4.0f, 12.0f, -4.0f, 8.0f, 1.0f, 8.0f)
                        // Pillar 1 (front-left)
                        .texOffs(64, 0)
                        .addBox(-6.0f, 3.0f, -6.0f, 2.0f, 10.0f, 2.0f)
                        // Pillar 2 (front-right)
                        .texOffs(72, 0)
                        .addBox(4.0f, 3.0f, -6.0f, 2.0f, 10.0f, 2.0f)
                        // Pillar 3 (back-center)
                        .texOffs(80, 0)
                        .addBox(-1.0f, 3.0f, 4.0f, 2.0f, 10.0f, 2.0f),
                PartPose.ZERO);

        // Rotating equatorial containment ring at chamber center (y=7..9)
        root.addOrReplaceChild(
                "ring",
                CubeListBuilder.create()
                        .texOffs(0, 32)
                        .addBox(-5.5f, -0.75f, -5.5f, 11.0f, 1.5f, 1.5f)
                        .texOffs(0, 36)
                        .addBox(-5.5f, -0.75f, 4.0f, 11.0f, 1.5f, 1.5f)
                        .texOffs(0, 40)
                        .addBox(-5.5f, -0.75f, -4.0f, 1.5f, 1.5f, 8.0f)
                        .texOffs(0, 40)
                        .addBox(4.0f, -0.75f, -4.0f, 1.5f, 1.5f, 8.0f),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, 128, 64).bakeRoot();
    }
}
