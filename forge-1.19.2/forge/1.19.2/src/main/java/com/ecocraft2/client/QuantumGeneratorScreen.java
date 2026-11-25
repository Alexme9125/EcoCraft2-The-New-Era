package com.ecocraft2.client;

import com.ecocraft2.bridge.Ecc2BridgePlatform;
import com.ecocraft2.network.Ecc2Network;
import com.ecocraft2.solar.QuantumGeneratorMenu;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** GUI screen for the Creative Quantum Generator (176x193, ±1/10/100 Shift ±5/50/500, Tier 1..5+MAX). */
public final class QuantumGeneratorScreen extends AbstractContainerScreen<QuantumGeneratorMenu> {

    private static final ResourceLocation TEXTURE =
            Ecc2BridgePlatform.rl("textures/gui/quantum_generator.png");

    private static final int[] BTN_X = {12, 38, 64, 90, 116, 142};
    private static final int PROD_Y = 68;
    private static final int TIER_Y = 87;
    private static final int BTN_W = 24;
    private static final int BTN_H = 16;

    private static final String[] PROD_LABELS = {"-100", "-10", "-1", "+1", "+10", "+100"};
    private static final String[] SHIFT_LABELS = {"-500", "-50", "-5", "+5", "+50", "+500"};
    private static final String[] TIER_LABELS = {"T1", "T2", "T3", "T4", "T5", "MAX"};

    public QuantumGeneratorScreen(QuantumGeneratorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 193;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 100;
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTick);
        renderTooltip(poseStack, mouseX, mouseY);
    }

    @Override
    protected void renderBg(PoseStack poseStack, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.setShaderTexture(0, TEXTURE);

        blit(poseStack, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        // Active / Stopped LED at (152, 24)
        blit(poseStack, leftPos + 152, topPos + 24, 180, menu.isActive() ? 0 : 12, 10, 10);

        // Production buttons row
        for (int i = 0; i < 6; i++) {
            boolean hover = isHovering(BTN_X[i], PROD_Y, BTN_W, BTN_H, mouseX, mouseY);
            blit(poseStack, leftPos + BTN_X[i], topPos + PROD_Y, 180, hover ? 44 : 26, BTN_W, BTN_H);
        }

        // Tier selection buttons row
        int currentTier = menu.tier();
        for (int i = 0; i < 6; i++) {
            boolean selected = currentTier == i + 1;
            boolean hover = isHovering(BTN_X[i], TIER_Y, BTN_W, BTN_H, mouseX, mouseY);
            int spriteY = selected ? 62 : (hover ? 44 : 26);
            blit(poseStack, leftPos + BTN_X[i], topPos + TIER_Y, 180, spriteY, BTN_W, BTN_H);
        }
    }

    @Override
    protected void renderLabels(PoseStack poseStack, int mouseX, int mouseY) {
        font.draw(poseStack, title, titleLabelX, titleLabelY, 0x303846);

        poseStack.pushPose();
        poseStack.scale(0.8f, 0.8f, 1.0f);
        int x = (int) (14 / 0.8f);
        font.draw(
                poseStack,
                Component.translatable("gui.ecc2.quantum_generator.production", menu.production()),
                x,
                (int) (22 / 0.8f),
                0x8DF0FF);
        String tierStr = menu.tier() == 6 ? "MAX" : String.valueOf(menu.tier());
        font.draw(
                poseStack,
                Component.translatable(
                        "gui.ecc2.quantum_generator.tier", tierStr, menu.tierPacketSize()),
                x,
                (int) (33 / 0.8f),
                0xD0E4F0);
        int perPacket = menu.production() / menu.packetCount();
        font.draw(
                poseStack,
                Component.translatable(
                        "gui.ecc2.quantum_generator.packets", menu.packetCount(), perPacket),
                x,
                (int) (44 / 0.8f),
                0x7FFFB0);
        poseStack.popPose();

        // Button captions
        String[] prodLabels = Screen.hasShiftDown() ? SHIFT_LABELS : PROD_LABELS;
        for (int i = 0; i < 6; i++) {
            int cx = BTN_X[i] + (BTN_W - font.width(prodLabels[i])) / 2;
            font.draw(poseStack, prodLabels[i], cx, PROD_Y + 4, 0xE6F2FA);
            int tx = BTN_X[i] + (BTN_W - font.width(TIER_LABELS[i])) / 2;
            font.draw(
                    poseStack,
                    TIER_LABELS[i],
                    tx,
                    TIER_Y + 4,
                    menu.tier() == i + 1 ? 0x0B2430 : 0xE6F2FA);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            boolean shift = Screen.hasShiftDown();
            for (int i = 0; i < 6; i++) {
                if (isHovering(BTN_X[i], PROD_Y, BTN_W, BTN_H, mouseX, mouseY)) {
                    Ecc2Network.sendQuantumButton(menu.pos(), (shift ? 10 : 0) + i);
                    return true;
                }
                if (isHovering(BTN_X[i], TIER_Y, BTN_W, BTN_H, mouseX, mouseY)) {
                    Ecc2Network.sendQuantumButton(menu.pos(), 20 + i);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
