package com.ecocraft2.client;

import com.ecocraft2.bridge.Ecc2BridgePlatform;
import com.ecocraft2.solar.MolecularTransformerMenu;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** GUI screen for the Molecular Transformer (220x193, vertical progress fill + LCD telemetry). */
public final class MolecularTransformerScreen
        extends AbstractContainerScreen<MolecularTransformerMenu> {

    private static final ResourceLocation TEXTURE =
            Ecc2BridgePlatform.rl("textures/gui/molecular_transformer.png");

    public MolecularTransformerScreen(
            MolecularTransformerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 220;
        this.imageHeight = 193;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 30;
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

        int filled = menu.scaledProgress(15);
        if (filled > 0) {
            blit(poseStack, leftPos + 23, topPos + 47, 224, 0, 10, filled);
        } else {
            blit(poseStack, leftPos + 23, topPos + 47, 224, 18, 10, 15);
        }
    }

    @Override
    protected void renderLabels(PoseStack poseStack, int mouseX, int mouseY) {
        font.draw(poseStack, title, titleLabelX, titleLabelY, 0x303846);
        font.draw(poseStack, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x303846);

        poseStack.pushPose();
        poseStack.scale(0.8f, 0.8f, 1.0f);
        int x = (int) (56 / 0.8f);
        ItemStack inStack = menu.inputStack();
        if (menu.isOutputBlocked()) {
            font.draw(
                    poseStack,
                    Component.translatable("gui.ecc2.mt.output_full"),
                    x,
                    (int) (28 / 0.8f),
                    0xFFAA44);
        } else if (!menu.isWorking()) {
            font.draw(
                    poseStack,
                    Component.translatable("gui.ecc2.mt.idle"),
                    x,
                    (int) (28 / 0.8f),
                    0xA8C0D0);
        } else {
            Component inName =
                    inStack.isEmpty() ? Component.literal("-") : inStack.getHoverName();
            font.draw(
                    poseStack,
                    Component.translatable("gui.ecc2.mt.input", inName),
                    x,
                    (int) (26 / 0.8f),
                    0xD0E4F0);
            font.draw(
                    poseStack,
                    Component.translatable("gui.ecc2.mt.energy", menu.totalEu()),
                    x,
                    (int) (40 / 0.8f),
                    0x8DF0FF);
            font.draw(
                    poseStack,
                    Component.translatable("gui.ecc2.mt.input_eu", menu.lastTickPower()),
                    x,
                    (int) (54 / 0.8f),
                    0x7FFFB0);
            font.draw(
                    poseStack,
                    Component.translatable("gui.ecc2.mt.progress", menu.progressPercent()),
                    x,
                    (int) (68 / 0.8f),
                    0xFFFFFF);
        }
        poseStack.popPose();
    }
}
