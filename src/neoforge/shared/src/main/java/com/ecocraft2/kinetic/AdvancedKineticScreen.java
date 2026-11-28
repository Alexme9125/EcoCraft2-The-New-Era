package com.ecocraft2.kinetic;

import com.ecocraft2.config.Ecc2Config;
import com.ecocraft2.solar.Ecc2DataSync;
import com.ecocraft2.solar.Ecc2GuiCanvas;
import com.ecocraft2.solar.Ecc2ScreenBase;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Pixel-aligned GUI screen for all six Advanced Wind &amp; Water Kinetic Generators, matching
 * {@code assets-gen/gui_coordinates.json} ({@code kinetic_generator.png}, 176×166).
 */
public final class AdvancedKineticScreen extends Ecc2ScreenBase<AdvancedKineticMenu> {

    private static final String TEX = "kinetic_generator";
    private static final int PANEL_W = 176;
    private static final int PANEL_H = 166;

    private static final int BADGE_X = 134;
    private static final int BADGE_Y = 18;
    private static final int BADGE_W = 34;
    private static final int BADGE_H = 12;

    private static final int DUR_X = 52;
    private static final int DUR_Y = 48;
    private static final int DUR_W = 72;
    private static final int DUR_H = 7;

    public AdvancedKineticScreen(
            AdvancedKineticMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PANEL_W, PANEL_H);
    }

    @Override
    protected void drawBackground(
            Ecc2GuiCanvas canvas, int relMouseX, int relMouseY, float partialTick) {
        canvas.blit(TEX, 0, 0, 0, 0, PANEL_W, PANEL_H);

        // Tier badge sprite (34x12)
        int badgeV =
                switch (menu.kineticTier()) {
                    case DURABLE -> 32;
                    case OVERCLOCKED -> 46;
                    case PRECISION -> 60;
                    default -> 32;
                };
        canvas.blit(TEX, BADGE_X, BADGE_Y, 180, badgeV, BADGE_W, BADGE_H);

        // Rotor durability bar (72x7)
        int durFill = menu.scaledDurability(DUR_W);
        if (durFill > 0) {
            double pct = menu.rotorPercent();
            int durV = pct > 50.0 ? 0 : (pct > 20.0 ? 10 : 20);
            canvas.blit(TEX, DUR_X, DUR_Y, 180, durV, durFill, DUR_H);
        }

        // Status indicator icon inside left edge of LCD
        var status = menu.status();
        if (status == AdvancedKineticPhysics.Status.RUNNING) {
            canvas.blit(TEX, 20, 63, 180, 76, 12, 12);
        } else if (status == AdvancedKineticPhysics.Status.OVERWIND) {
            canvas.blit(TEX, 20, 63, 194, 76, 12, 12);
        }
    }

    @Override
    protected void drawForeground(Ecc2GuiCanvas canvas, int relMouseX, int relMouseY) {
        String trimmedTitle = canvas.trimWidth(title.getString(), PANEL_W - 14);
        canvas.centeredText(Component.literal(trimmedTitle), PANEL_W / 2, 6, 0xFF283240, false);

        // LCD readout at (16, 59, 144, 20)
        var status = menu.status();
        int textX =
                (status == AdvancedKineticPhysics.Status.RUNNING
                                || status == AdvancedKineticPhysics.Status.OVERWIND)
                        ? 35
                        : 20;
        int maxW = 156 - textX;

        switch (status) {
            case NO_ROTOR ->
                    canvas.text(
                            Component.literal(
                                    canvas.trimWidth(
                                            Component.translatable("gui.ecera.kinetic.no_rotor")
                                                    .getString(),
                                            maxW)),
                            textX,
                            65,
                            0xFF9EB4C4,
                            false);
            case OBSTRUCTED ->
                    canvas.text(
                            Component.literal(
                                    canvas.trimWidth(
                                            Component.translatable("gui.ecera.kinetic.obstructed")
                                                    .getString(),
                                            maxW)),
                            textX,
                            65,
                            0xFFF29662,
                            false);
            case NO_WATER ->
                    canvas.text(
                            Component.literal(
                                    canvas.trimWidth(
                                            Component.translatable("gui.ecera.kinetic.no_water")
                                                    .getString(),
                                            maxW)),
                            textX,
                            65,
                            0xFF72C6F2,
                            false);
            case OVERWIND -> {
                canvas.text(
                        Component.literal(
                                canvas.trimWidth(
                                        Component.translatable(
                                                        "gui.ecera.kinetic.output",
                                                        Ecc2DataSync.formatInt(menu.currentKu()))
                                                .getString(),
                                        maxW)),
                        textX,
                        61,
                        0xFFFFD86A,
                        false);
                canvas.text(
                        Component.literal(
                                canvas.trimWidth(
                                        Component.translatable("gui.ecera.kinetic.overwind")
                                                .getString(),
                                        maxW)),
                        textX,
                        70,
                        0xFFFF6E6E,
                        false);
            }
            case RUNNING -> {
                canvas.text(
                        Component.literal(
                                canvas.trimWidth(
                                        Component.translatable(
                                                        "gui.ecera.kinetic.output",
                                                        Ecc2DataSync.formatInt(menu.currentKu()))
                                                .getString(),
                                        maxW)),
                        textX,
                        61,
                        0xFF72F2A8,
                        false);
                String envKey =
                        menu.isWaterType() ? "gui.ecera.kinetic.water" : "gui.ecera.kinetic.wind";
                canvas.text(
                        Component.literal(
                                canvas.trimWidth(
                                        Component.translatable(
                                                        envKey,
                                                        Ecc2DataSync.formatInt(
                                                                menu.environmentReading()))
                                                .getString(),
                                        maxW)),
                        textX,
                        70,
                        0xFF9EDAF0,
                        false);
            }
        }
    }

    @Override
    protected List<Component> hoverTooltip(int relMouseX, int relMouseY) {
        if (relMouseX >= DUR_X
                && relMouseX < DUR_X + DUR_W
                && relMouseY >= DUR_Y
                && relMouseY < DUR_Y + DUR_H
                && menu.rotorMax() > 0) {
            double pct = menu.rotorPercent();
            double wearMult =
                    switch (menu.kineticTier()) {
                        case DURABLE -> Ecc2Config.durableKineticWearMultiplier();
                        case OVERCLOCKED -> Ecc2Config.overclockedKineticWearMultiplier();
                        case PRECISION -> Ecc2Config.precisionKineticWearMultiplier();
                        case STANDARD -> menu.kineticTier().wearMultiplier();
                    };
            double hours = wearMult > 0.0 ? (menu.rotorRemaining() / wearMult) / 3600.0 : 0.0;
            return List.of(
                    Component.translatable(
                            "gui.ecera.kinetic.rotor_life",
                            String.format(Locale.ROOT, "%.1f", pct),
                            String.format(Locale.ROOT, "%.1f", hours)),
                    Component.literal(
                            Ecc2DataSync.formatInt(menu.rotorRemaining())
                                    + " / "
                                    + Ecc2DataSync.formatInt(menu.rotorMax())));
        }
        if (relMouseX >= BADGE_X
                && relMouseX < BADGE_X + BADGE_W
                && relMouseY >= BADGE_Y
                && relMouseY < BADGE_Y + BADGE_H) {
            String key =
                    switch (menu.kineticTier()) {
                        case DURABLE -> "tooltip.ecera.kinetic.durable";
                        case OVERCLOCKED -> "tooltip.ecera.kinetic.overclocked";
                        case PRECISION -> "tooltip.ecera.kinetic.precision";
                        default -> "tooltip.ecera.kinetic.durable";
                    };
            return List.of(Component.translatable(key));
        }
        return List.of();
    }
}
