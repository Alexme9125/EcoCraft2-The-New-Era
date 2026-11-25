package com.ecocraft2.solar;

import com.ecocraft2.common.solar.QuantumGeneratorEngine;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Pixel-aligned screen for the creative {@code ecc2:quantum_generator}, matching
 * {@code assets-gen/gui_coordinates.json} ({@code quantum_generator.png}, 176×193).
 */
public final class QuantumGeneratorScreen extends Ecc2ScreenBase<QuantumGeneratorMenu> {

    private static final String TEX = "quantum_generator";
    private static final int PANEL_W = 176;
    private static final int PANEL_H = 193;

    private static final int[] BTN_X = {12, 38, 64, 90, 116, 142};
    private static final int PROD_Y = 68;
    private static final int TIER_Y = 87;
    private static final int BTN_W = 24;
    private static final int BTN_H = 16;

    private static final int LED_X = 152;
    private static final int LED_Y = 24;

    private static final String[] NORMAL_LABELS = {"-100", "-10", "-1", "+1", "+10", "+100"};
    private static final String[] SHIFT_LABELS = {"-500", "-50", "-5", "+5", "+50", "+500"};
    private static final String[] TIER_LABELS = {"T1", "T2", "T3", "T4", "T5", "MAX"};

    public QuantumGeneratorScreen(
            QuantumGeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PANEL_W, PANEL_H);
    }

    @Override
    protected void drawBackground(
            Ecc2GuiCanvas canvas, int relMouseX, int relMouseY, float partialTick) {
        canvas.blit(TEX, 0, 0, 0, 0, PANEL_W, PANEL_H);

        // Status LED
        canvas.blit(TEX, LED_X, LED_Y, 180, menu.isActive() ? 0 : 12, 10, 10);

        // Production buttons row (0..5)
        for (int i = 0; i < 6; i++) {
            boolean hovered = hit(relMouseX, relMouseY, BTN_X[i], PROD_Y, BTN_W, BTN_H);
            canvas.blit(TEX, BTN_X[i], PROD_Y, 180, hovered ? 44 : 26, BTN_W, BTN_H);
        }

        // Tier buttons row (T1..T5 + MAX)
        int activeTier = menu.tier();
        for (int i = 0; i < 6; i++) {
            boolean selected = (i + 1) == activeTier;
            boolean hovered = hit(relMouseX, relMouseY, BTN_X[i], TIER_Y, BTN_W, BTN_H);
            int spriteV = selected ? 62 : (hovered ? 44 : 26);
            canvas.blit(TEX, BTN_X[i], TIER_Y, 180, spriteV, BTN_W, BTN_H);
        }
    }

    @Override
    protected void drawForeground(Ecc2GuiCanvas canvas, int relMouseX, int relMouseY) {
        canvas.centeredText(title, PANEL_W / 2, 6, 0xFF283240, false);

        // LCD telemetry lines
        canvas.text(
                Component.translatable(
                        "gui.ecc2.quantum_generator.production",
                        Ecc2DataSync.formatInt(menu.production())),
                15,
                22,
                0xFF7BE8F5,
                false);
        String tierLabel =
                menu.tier() == QuantumGeneratorEngine.MAX_TIER
                        ? Component.translatable("gui.ecc2.quantum_generator.tier_max").getString()
                        : Integer.toString(menu.tier());
        canvas.text(
                Component.translatable(
                        "gui.ecc2.quantum_generator.tier",
                        tierLabel,
                        Ecc2DataSync.formatInt(Math.round(menu.tierPacketSize()))),
                15,
                34,
                0xFFB8D4E4,
                false);
        String perPacket =
                menu.isActive()
                        ? String.format(Locale.ROOT, "%.1f", menu.offeredPerPacket())
                        : "0";
        canvas.text(
                Component.translatable(
                        "gui.ecc2.quantum_generator.packets", menu.packetCount(), perPacket),
                15,
                46,
                menu.isActive() ? 0xFF72F2A8 : 0xFFE87878,
                false);

        // Button captions
        String[] prodLabels = SolarPlatform.hasShiftDown() ? SHIFT_LABELS : NORMAL_LABELS;
        for (int i = 0; i < 6; i++) {
            canvas.centeredText(
                    Component.literal(prodLabels[i]),
                    BTN_X[i] + BTN_W / 2,
                    PROD_Y + 4,
                    0xFFEAF6FF,
                    false);
        }
        int activeTier = menu.tier();
        for (int i = 0; i < 6; i++) {
            boolean selected = (i + 1) == activeTier;
            canvas.centeredText(
                    Component.literal(TIER_LABELS[i]),
                    BTN_X[i] + BTN_W / 2,
                    TIER_Y + 4,
                    selected ? 0xFF10202C : 0xFFEAF6FF,
                    false);
        }
    }

    @Override
    protected boolean handleClick(int relMouseX, int relMouseY, int button) {
        if (button != 0) {
            return false;
        }
        boolean shift = SolarPlatform.hasShiftDown();
        for (int i = 0; i < 6; i++) {
            if (hit(relMouseX, relMouseY, BTN_X[i], PROD_Y, BTN_W, BTN_H)) {
                int eventId = (shift ? QuantumGeneratorEngine.EVENT_SUB_500 : QuantumGeneratorEngine.EVENT_SUB_100) + i;
                SolarPlatform.sendToServer(
                        new QuantumGeneratorButtonPayload(menu.blockPos(), eventId));
                return true;
            }
            if (hit(relMouseX, relMouseY, BTN_X[i], TIER_Y, BTN_W, BTN_H)) {
                int eventId = QuantumGeneratorEngine.EVENT_TIER_1 + i;
                SolarPlatform.sendToServer(
                        new QuantumGeneratorButtonPayload(menu.blockPos(), eventId));
                return true;
            }
        }
        return false;
    }

    @Override
    protected List<Component> hoverTooltip(int relMouseX, int relMouseY) {
        if (hit(relMouseX, relMouseY, LED_X, LED_Y, 10, 10)) {
            return List.of(
                    Component.translatable(
                            menu.isActive()
                                    ? "gui.ecc2.quantum_generator.active"
                                    : "gui.ecc2.quantum_generator.redstone_stopped"));
        }
        for (int i = 0; i < 6; i++) {
            if (hit(relMouseX, relMouseY, BTN_X[i], PROD_Y, BTN_W, BTN_H)) {
                return List.of(
                        Component.translatable("gui.ecc2.quantum_generator.shift_hint"));
            }
        }
        return List.of();
    }

    private static boolean hit(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
