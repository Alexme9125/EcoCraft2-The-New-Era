package com.ecocraft2.kinetic;

import com.ecocraft2.common.kinetic.RotorSpec;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.EnumMap;
import java.util.WeakHashMap;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Shared 3D rotor geometry, extended bounding-box calculation, and frame-rate-independent smooth
 * angular motion for all six Advanced Wind &amp; Water Kinetic Generators.
 *
 * <p>Uses IC2NE's native rotor model textures ({@code ic2:textures/items/rotor/*_rotor_model.png})
 * and matches IC2NE's 4-blade pitched hub dimensions across 4–11 block diameters.
 */
public final class AdvancedRotorModel {

    public record Parts(ModelPart shaft, ModelPart wheel) {}

    private static final double HUB_OFFSET = 0.875;

    private final EnumMap<RotorSpec, Parts> windParts = new EnumMap<>(RotorSpec.class);
    private final EnumMap<RotorSpec, Parts> waterParts = new EnumMap<>(RotorSpec.class);
    private final WeakHashMap<AdvancedKineticBlockEntity, Motion> motions = new WeakHashMap<>();

    public AdvancedRotorModel() {
        for (RotorSpec spec : RotorSpec.values()) {
            Parts wind = bake(spec, spec.windDiameter());
            windParts.put(spec, wind);
            Parts water =
                    spec.waterDiameter() == spec.windDiameter()
                            ? wind
                            : bake(spec, spec.waterDiameter());
            waterParts.put(spec, water);
        }
    }

    public Parts parts(boolean waterType, RotorSpec spec) {
        return (waterType ? waterParts : windParts).get(spec);
    }

    /** Returns the IC2NE texture path (relative to {@code ic2:}) for {@code spec}. */
    public static String ic2TexturePath(RotorSpec spec) {
        String material =
                switch (spec) {
                    case WOOD -> "wood";
                    case BRONZE -> "bronze";
                    case IRON -> "iron";
                    case STEEL -> "steel";
                    case CARBON -> "carbon";
                };
        return "textures/items/rotor/" + material + "_rotor_model.png";
    }

    private static int bladeWidth(RotorSpec spec) {
        return switch (spec) {
            case WOOD -> 8;
            case BRONZE, IRON -> 10;
            case STEEL -> 11;
            case CARBON -> 12;
        };
    }

    /** Translates {@code pose} from block origin to the rotor hub offset along {@code face}. */
    public static void translateToHub(PoseStack pose, Direction face) {
        pose.translate(
                0.5 + face.getStepX() * HUB_OFFSET,
                0.5 + face.getStepY() * HUB_OFFSET,
                0.5 + face.getStepZ() * HUB_OFFSET);
    }

    /** Computes the extended render bounding box covering the full swept rotor blades. */
    public static AABB renderBounds(AdvancedKineticBlockEntity be) {
        BlockPos pos = be.getBlockPos();
        AABB base = new AABB(pos);
        RotorSpec spec = be.getVisualRotorSpec();
        if (spec == null) {
            return base;
        }
        Direction face = be.getRotorFace();
        Vec3 hub =
                Vec3.atCenterOf(pos)
                        .add(
                                face.getStepX() * HUB_OFFSET,
                                face.getStepY() * HUB_OFFSET,
                                face.getStepZ() * HUB_OFFSET);
        double diameter = be.isWaterType() ? spec.waterDiameter() : spec.windDiameter();
        Direction.Axis axis = face.getAxis();
        AABB disk =
                AABB.ofSize(
                        hub,
                        axis == Direction.Axis.X ? 1.0 : diameter,
                        axis == Direction.Axis.Y ? 1.0 : diameter,
                        axis == Direction.Axis.Z ? 1.0 : diameter);
        return base.minmax(disk);
    }

    /** Advances and returns the smooth spin angle in degrees for {@code be}. */
    public float angle(
            AdvancedKineticBlockEntity be,
            RotorSpec spec,
            float partialTick,
            boolean animate) {
        var level = be.getLevel();
        if (spec == null || level == null) {
            motions.remove(be);
            return 0.0f;
        }
        if (!animate) {
            return 0.0f;
        }
        Motion motion = motions.get(be);
        if (motion == null || motion.spec != spec) {
            motion = new Motion(spec, level.getGameTime(), partialTick);
            motions.put(be, motion);
        }
        return motion.advance(level.getGameTime(), partialTick, be.getVisualSpeedKu());
    }

    private static Parts bake(RotorSpec spec, int diameter) {
        var mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild(
                "shaft",
                CubeListBuilder.create()
                        .texOffs(0, 192)
                        .addBox(-2.5f, -2.5f, -7.0f, 5.0f, 5.0f, 7.0f),
                PartPose.ZERO);

        PartDefinition wheel =
                root.addOrReplaceChild(
                        "wheel",
                        CubeListBuilder.create()
                                .texOffs(0, 208)
                                .addBox(-4.0f, -4.0f, -2.5f, 8.0f, 8.0f, 5.0f)
                                .texOffs(0, 232)
                                .addBox(-2.5f, -2.5f, 2.5f, 5.0f, 5.0f, 2.0f),
                        PartPose.ZERO);

        float width = bladeWidth(spec);
        float radiusPx = diameter * 8.0f - 0.5f;
        float length = (float) Math.sqrt(Math.max(1.0, radiusPx * radiusPx - width * width * 0.25f));
        for (int i = 0; i < 4; i++) {
            wheel.addOrReplaceChild(
                    "blade_" + i,
                    CubeListBuilder.create()
                            .texOffs(0, 0)
                            .addBox(-width / 2.0f, -length, -1.0f, width, length - 8.0f, 2.0f)
                            .texOffs(0, 176)
                            .addBox(-2.5f, -8.0f, -1.5f, 5.0f, 8.0f, 3.0f),
                    PartPose.offsetAndRotation(
                            0.0f, 0.0f, 0.0f, 0.0f, 0.24f, (float) (i * Math.PI / 2.0)));
        }

        ModelPart baked = LayerDefinition.create(mesh, 32, 256).bakeRoot();
        return new Parts(baked.getChild("shaft"), baked.getChild("wheel"));
    }

    private static final class Motion {
        final RotorSpec spec;
        long lastTick;
        float lastPartial;
        float angle;
        float degreesPerTick;

        Motion(RotorSpec spec, long tick, float partial) {
            this.spec = spec;
            this.lastTick = tick;
            this.lastPartial = partial;
        }

        float advance(long tick, float partial, int speedKu) {
            double delta = Math.max(0.0, (tick - lastTick) + (partial - lastPartial));
            angle = (float) ((angle + delta * degreesPerTick) % 360.0);
            lastTick = tick;
            lastPartial = partial;
            degreesPerTick = speedKu <= 0 ? 0.0f : Math.min(18.0f, 0.65f + speedKu / 24.0f);
            return angle;
        }
    }
}
