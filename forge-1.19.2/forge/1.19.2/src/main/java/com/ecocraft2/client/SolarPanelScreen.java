package com.ecocraft2.client;

import com.ecocraft2.bridge.Ecc2BridgePlatform;
import com.ecocraft2.common.solar.SkyCondition;
import com.ecocraft2.solar.SolarPanelMenu;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** GUI screen for the 4 ASP 2 Solar Panels (194x168, sun/moon/rain/none icons + energy bar). */
public final class SolarPanelScreen extends AbstractContainerScreen<SolarPanelMenu> {

    private static final ResourceLocation TEXTURE =
            Ecc2BridgePlatform.rl("textures/gui/solar_panel.png");

    public SolarPanelScreen(SolarPanelMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 194;
        this.imageHeight = 168;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 17;
        this.inventoryLabelY = 74;
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTick);
        renderTooltip(poseStack, mouseX, mouseY);
        if (isHovering(24, 32, 14, 14, mouseX, mouseY)) {
            String key =
                    switch (menu.skyCondition()) {
                        case DAY -> "gui.ecera.solar.day";
                        case NIGHT -> "gui.ecera.solar.night";
                        case RAIN -> "gui.ecera.solar.rain";
                        case NONE -> "gui.ecera.solar.blocked";
                    };
            renderTooltip(poseStack, Component.translatable(key), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(PoseStack poseStack, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.setShaderTexture(0, TEXTURE);

        blit(poseStack, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        int energyW = menu.scaledEnergy(24);
        if (energyW > 0) {
            blit(poseStack, leftPos + 46, topPos + 25, 196, 0, energyW, 14);
        }

        SkyCondition cond = menu.skyCondition();
        int spriteY =
                switch (cond) {
                    case DAY -> 16;
                    case NIGHT -> 32;
                    case RAIN -> 48;
                    case NONE -> 64;
                };
        blit(poseStack, leftPos + 24, topPos + 32, 196, spriteY, 14, 14);
    }

    @Override
    protected void renderLabels(PoseStack poseStack, int mouseX, int mouseY) {
        font.draw(poseStack, title, titleLabelX, titleLabelY, 0x303846);
        font.draw(poseStack, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x303846);

        poseStack.pushPose();
        poseStack.scale(0.75f, 0.75f, 1.0f);
        int x = (int) (76 / 0.75f);
        font.draw(
                poseStack,
                Component.translatable("gui.ecera.solar.storage", menu.storedEu(), menu.maxStorageEu()),
                x,
                (int) (24 / 0.75f),
                0x8DF0FF);
        font.draw(
                poseStack,
                Component.translatable("gui.ecera.solar.generating", menu.generatingEu()),
                x,
                (int) (35 / 0.75f),
                0x7FFFB0);
        font.draw(
                poseStack,
                Component.translatable("gui.ecera.solar.max_output", menu.maxOutputEu(), menu.tier()),
                x,
                (int) (46 / 0.75f),
                0xD0E4F0);
        poseStack.popPose();
    }
}
