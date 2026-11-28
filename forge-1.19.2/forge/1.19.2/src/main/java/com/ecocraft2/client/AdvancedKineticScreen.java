package com.ecocraft2.client;

import com.ecocraft2.bridge.Ecc2BridgePlatform;
import com.ecocraft2.kinetic.AdvancedKineticBlockEntity;
import com.ecocraft2.kinetic.AdvancedKineticMenu;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Locale;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** GUI screen for the 6 Advanced Wind & Water Kinetic Generators (176x166). */
public final class AdvancedKineticScreen extends AbstractContainerScreen<AdvancedKineticMenu> {

    private static final ResourceLocation TEXTURE =
            Ecc2BridgePlatform.rl("textures/gui/kinetic_generator.png");

    public AdvancedKineticScreen(AdvancedKineticMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 73;
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

        // Tier badge at (134, 18)
        int badgeY =
                switch (menu.tier()) {
                    case DURABLE -> 32;
                    case OVERCLOCKED -> 46;
                    case PRECISION -> 60;
                    default -> 32;
                };
        blit(poseStack, leftPos + 134, topPos + 18, 180, badgeY, 34, 12);

        // Rotor durability bar at (52, 48, 72x7)
        if (menu.hasRotor()) {
            double pct = menu.rotorLifePercent();
            int fillW = (int) Math.round((pct / 100.0) * 72.0);
            int barY = pct > 50.0 ? 0 : (pct > 20.0 ? 10 : 20);
            if (fillW > 0) {
                blit(poseStack, leftPos + 52, topPos + 48, 180, barY, Math.min(72, fillW), 7);
            }
        }

        // Status icon at (18, 24)
        if (menu.statusCode() == AdvancedKineticBlockEntity.STATUS_OVERWIND) {
            blit(poseStack, leftPos + 18, topPos + 24, 194, 76, 12, 12);
        } else if (menu.currentKu() > 0) {
            blit(poseStack, leftPos + 18, topPos + 24, 180, 76, 12, 12);
        }
    }

    @Override
    protected void renderLabels(PoseStack poseStack, int mouseX, int mouseY) {
        font.draw(poseStack, title, titleLabelX, titleLabelY, 0x303846);

        poseStack.pushPose();
        poseStack.scale(0.75f, 0.75f, 1.0f);
        int x = (int) (20 / 0.75f);
        Component line1 =
                switch (menu.statusCode()) {
                    case AdvancedKineticBlockEntity.STATUS_NO_ROTOR ->
                            Component.translatable("gui.ecera.kinetic.no_rotor");
                    case AdvancedKineticBlockEntity.STATUS_OBSTRUCTED ->
                            Component.translatable("gui.ecera.kinetic.obstructed");
                    case AdvancedKineticBlockEntity.STATUS_NO_WATER ->
                            Component.translatable("gui.ecera.kinetic.no_water");
                    case AdvancedKineticBlockEntity.STATUS_OVERWIND ->
                            Component.translatable("gui.ecera.kinetic.overwind");
                    default ->
                            Component.translatable(
                                    menu.isWater()
                                            ? "gui.ecera.kinetic.water"
                                            : "gui.ecera.kinetic.wind",
                                    menu.windOrFlow());
                };
        int c1 =
                menu.statusCode() == AdvancedKineticBlockEntity.STATUS_OVERWIND
                        ? 0xFF6644
                        : 0x8DF0FF;
        font.draw(poseStack, line1, x, (int) (61 / 0.75f), c1);

        Component line2 =
                Component.translatable("gui.ecera.kinetic.output", menu.currentKu())
                        .append("  ")
                        .append(
                                Component.translatable(
                                        "gui.ecera.kinetic.rotor_life",
                                        String.format(Locale.ROOT, "%.1f", menu.rotorLifePercent()),
                                        String.format(
                                                Locale.ROOT, "%.1f", menu.rotorRemainingHours())));
        font.draw(poseStack, line2, x, (int) (70 / 0.75f), 0x7FFFB0);
        poseStack.popPose();
    }
}
