package com.ecocraft2.client;

import com.ecocraft2.bridge.Ecc2BridgePlatform;
import com.ecocraft2.common.config.Ecc2Ids;
import com.ecocraft2.common.gas.CultureIncubatorEngine;
import com.ecocraft2.common.gas.GasFuelSpec;
import com.ecocraft2.common.gas.GasFurnaceEngine;
import com.ecocraft2.common.gas.GasGeneratorEngine;
import com.ecocraft2.common.gas.OrganicFermenterEngine;
import com.ecocraft2.common.gas.WaterGasEngine;
import com.ecocraft2.gas.CultureIncubatorMenu;
import com.ecocraft2.gas.GasFurnaceMenu;
import com.ecocraft2.gas.GasGeneratorMenu;
import com.ecocraft2.gas.OrganicFermenterMenu;
import com.ecocraft2.gas.WaterGasMachineMenu;
import com.industrialage.ia2.gui.DeviceScreen;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** All five pixel-aligned GUI screens for the Water Gas, Natural Gas, and Biogas machines. */
public final class GasScreens {

    private static final int COLOR_WATER = 0xCC2C88E6;
    private static final int COLOR_WATER_GAS = Ecc2Ids.WATER_GAS_TINT_ARGB;
    private static final int COLOR_NATURAL_GAS = Ecc2Ids.NATURAL_GAS_TINT_ARGB;
    private static final int COLOR_CULTURE = Ecc2Ids.CULTURE_MEDIUM_TINT_ARGB;
    private static final int COLOR_BIOGAS = 0xE07CB848;

    private GasScreens() {}

    private static int fuelColor(GasFuelSpec fuel) {
        if (fuel == null) {
            return COLOR_WATER_GAS;
        }
        return switch (fuel) {
            case WATER_GAS -> COLOR_WATER_GAS;
            case NATURAL_GAS -> COLOR_NATURAL_GAS;
            case BIOGAS -> COLOR_BIOGAS;
        };
    }

    private static void drawFluidBar(
            PoseStack poseStack, int x, int y, int w, int h, int amount, int cap, int argb) {
        if (amount <= 0 || cap <= 0) {
            return;
        }
        int filled = (int) Math.min(h, Math.max(1L, ((long) amount * h) / cap));
        GuiComponent.fill(poseStack, x, y + h - filled, x + w, y + h, argb);
    }

    // ---- 1. Water Gas Machine Screen (176x180) ----
    public static final class WaterGasScreen extends DeviceScreen<WaterGasMachineMenu> {
        private static final ResourceLocation TEX =
                Ecc2BridgePlatform.rl("textures/gui/water_gas_machine.png");

        public WaterGasScreen(WaterGasMachineMenu menu, Inventory inv, Component title) {
            super(menu, inv, title);
            this.imageWidth = 176;
            this.imageHeight = 180;
            this.titleLabelX = 8;
            this.titleLabelY = 6;
            this.inventoryLabelX = 8;
            this.inventoryLabelY = 86;
        }

        @Override
        protected void renderPanel(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
            renderBackground(poseStack);
            super.renderPanel(poseStack, mouseX, mouseY, partialTick);
            renderTooltip(poseStack, mouseX, mouseY);
            if (isHovering(14, 22, 16, 50, mouseX, mouseY)) {
                renderTooltip(
                        poseStack,
                        Component.translatable(
                                "gui.ecc2.gas.tank",
                                Component.translatable("gui.ecc2.gas.water"),
                                menu.waterMb(),
                                WaterGasEngine.WATER_TANK_CAPACITY_MB),
                        mouseX,
                        mouseY);
            } else if (isHovering(108, 22, 16, 50, mouseX, mouseY)) {
                renderTooltip(
                        poseStack,
                        Component.translatable(
                                "gui.ecc2.gas.tank",
                                Component.translatable("gui.ecc2.gas.water_gas"),
                                menu.waterGasMb(),
                                WaterGasEngine.GAS_TANK_CAPACITY_MB),
                        mouseX,
                        mouseY);
            } else if (isHovering(84, 22, 14, 14, mouseX, mouseY)) {
                renderTooltip(
                        poseStack,
                        Component.translatable(
                                menu.isSolarActive()
                                        ? "gui.ecc2.gas.solar_active"
                                        : "gui.ecc2.gas.solar_inactive"),
                        mouseX,
                        mouseY);
            }
        }

        @Override
        protected void renderBg(PoseStack poseStack, float partialTick, int mouseX, int mouseY) {
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.setShaderTexture(0, TEX);
            blit(poseStack, leftPos, topPos, 0, 0, imageWidth, imageHeight);

            drawFluidBar(
                    poseStack,
                    leftPos + 14,
                    topPos + 22,
                    16,
                    50,
                    menu.waterMb(),
                    WaterGasEngine.WATER_TANK_CAPACITY_MB,
                    COLOR_WATER);
            drawFluidBar(
                    poseStack,
                    leftPos + 108,
                    topPos + 22,
                    16,
                    50,
                    menu.waterGasMb(),
                    WaterGasEngine.GAS_TANK_CAPACITY_MB,
                    COLOR_WATER_GAS);

            RenderSystem.setShaderTexture(0, TEX);
            blit(poseStack, leftPos + 14, topPos + 22, 180, 68, 16, 50);
            blit(poseStack, leftPos + 108, topPos + 22, 180, 68, 16, 50);

            int prog = menu.progressPx();
            if (prog > 0) {
                blit(poseStack, leftPos + 60, topPos + 46, 180, 0, prog, 15);
            }
            int boltH = (int) Math.min(14L, ((long) menu.storedEu() * 14L) / menu.capacityEu());
            if (boltH > 0) {
                blit(
                        poseStack,
                        leftPos + 62,
                        topPos + 65 + (14 - boltH),
                        180,
                        18 + (14 - boltH),
                        14,
                        boltH);
            }
            blit(
                    poseStack,
                    leftPos + 84,
                    topPos + 22,
                    180,
                    menu.isSolarActive() ? 34 : 50,
                    14,
                    14);
        }
    }

    // ---- 2. Gas Furnace Screen (176x166) ----
    public static final class GasFurnaceScreen extends DeviceScreen<GasFurnaceMenu> {
        private static final ResourceLocation TEX =
                Ecc2BridgePlatform.rl("textures/gui/gas_furnace.png");

        public GasFurnaceScreen(GasFurnaceMenu menu, Inventory inv, Component title) {
            super(menu, inv, title);
            this.imageWidth = 176;
            this.imageHeight = 166;
            this.titleLabelX = 8;
            this.titleLabelY = 6;
            this.inventoryLabelX = 8;
            this.inventoryLabelY = 73;
        }

        @Override
        protected void renderPanel(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
            renderBackground(poseStack);
            super.renderPanel(poseStack, mouseX, mouseY, partialTick);
            renderTooltip(poseStack, mouseX, mouseY);
            if (isHovering(16, 22, 16, 50, mouseX, mouseY)) {
                renderTooltip(
                        poseStack,
                        Component.translatable(
                                "gui.ecc2.gas.tank",
                                Ecc2BridgePlatform.fuelName(menu.currentFuel()),
                                menu.gasMb(),
                                GasFurnaceEngine.GAS_TANK_CAPACITY_MB),
                        mouseX,
                        mouseY);
            }
        }

        @Override
        protected void renderBg(PoseStack poseStack, float partialTick, int mouseX, int mouseY) {
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.setShaderTexture(0, TEX);
            blit(poseStack, leftPos, topPos, 0, 0, imageWidth, imageHeight);

            drawFluidBar(
                    poseStack,
                    leftPos + 16,
                    topPos + 22,
                    16,
                    50,
                    menu.gasMb(),
                    GasFurnaceEngine.GAS_TANK_CAPACITY_MB,
                    fuelColor(menu.currentFuel()));

            RenderSystem.setShaderTexture(0, TEX);
            blit(poseStack, leftPos + 16, topPos + 22, 180, 36, 16, 50);

            if (menu.isActive()) {
                blit(poseStack, leftPos + 68, topPos + 41, 180, 0, 14, 14);
            }
            int prog = menu.progressPx();
            if (prog > 0) {
                blit(poseStack, leftPos + 90, topPos + 36, 180, 18, prog, 15);
            }
        }
    }

    // ---- 3. Gas Generator Screen (176x166) ----
    public static final class GasGeneratorScreen extends DeviceScreen<GasGeneratorMenu> {
        private static final ResourceLocation TEX =
                Ecc2BridgePlatform.rl("textures/gui/gas_generator.png");

        public GasGeneratorScreen(GasGeneratorMenu menu, Inventory inv, Component title) {
            super(menu, inv, title);
            this.imageWidth = 176;
            this.imageHeight = 166;
            this.titleLabelX = 8;
            this.titleLabelY = 6;
            this.inventoryLabelX = 8;
            this.inventoryLabelY = 73;
        }

        @Override
        protected void renderPanel(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
            renderBackground(poseStack);
            super.renderPanel(poseStack, mouseX, mouseY, partialTick);
            renderTooltip(poseStack, mouseX, mouseY);
            if (isHovering(20, 22, 16, 50, mouseX, mouseY)) {
                renderTooltip(
                        poseStack,
                        Component.translatable(
                                "gui.ecc2.gas.tank",
                                Ecc2BridgePlatform.fuelName(menu.currentFuel()),
                                menu.gasMb(),
                                GasGeneratorEngine.GAS_TANK_CAPACITY_MB),
                        mouseX,
                        mouseY);
            }
        }

        @Override
        protected void renderBg(PoseStack poseStack, float partialTick, int mouseX, int mouseY) {
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.setShaderTexture(0, TEX);
            blit(poseStack, leftPos, topPos, 0, 0, imageWidth, imageHeight);

            drawFluidBar(
                    poseStack,
                    leftPos + 20,
                    topPos + 22,
                    16,
                    50,
                    menu.gasMb(),
                    GasGeneratorEngine.GAS_TANK_CAPACITY_MB,
                    fuelColor(menu.currentFuel()));

            RenderSystem.setShaderTexture(0, TEX);
            blit(poseStack, leftPos + 20, topPos + 22, 180, 36, 16, 50);

            if (menu.isActive()) {
                blit(poseStack, leftPos + 76, topPos + 36, 180, 0, 14, 14);
            }
            int euW = (int) Math.min(24L, ((long) menu.storedEu() * 24L) / (long) GasGeneratorEngine.EU_CAPACITY);
            if (euW > 0) {
                blit(poseStack, leftPos + 98, topPos + 24, 180, 18, euW, 14);
            }
        }

        @Override
        protected void renderLabels(PoseStack poseStack, int mouseX, int mouseY) {
            font.draw(poseStack, title, titleLabelX, titleLabelY, 0x303846);
            poseStack.pushPose();
            poseStack.scale(0.7f, 0.7f, 1.0f);
            int x = (int) (75 / 0.7f);
            font.draw(
                    poseStack,
                    Component.translatable(
                            "gui.ecc2.gas.fuel", Ecc2BridgePlatform.fuelName(menu.currentFuel())),
                    x,
                    (int) (57 / 0.7f),
                    0x8DF0FF);
            font.draw(
                    poseStack,
                    Component.translatable("gui.ecc2.gas.output", menu.generatingEu()),
                    x,
                    (int) (66 / 0.7f),
                    0x7FFFB0);
            poseStack.popPose();
        }
    }

    // ---- 4. Culture Incubator Screen (176x180) ----
    public static final class CultureIncubatorScreen extends DeviceScreen<CultureIncubatorMenu> {
        private static final ResourceLocation TEX =
                Ecc2BridgePlatform.rl("textures/gui/culture_incubator.png");

        public CultureIncubatorScreen(CultureIncubatorMenu menu, Inventory inv, Component title) {
            super(menu, inv, title);
            this.imageWidth = 176;
            this.imageHeight = 180;
            this.titleLabelX = 8;
            this.titleLabelY = 6;
            this.inventoryLabelX = 8;
            this.inventoryLabelY = 86;
        }

        @Override
        protected void renderPanel(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
            renderBackground(poseStack);
            super.renderPanel(poseStack, mouseX, mouseY, partialTick);
            renderTooltip(poseStack, mouseX, mouseY);
            if (isHovering(14, 22, 16, 50, mouseX, mouseY)) {
                renderTooltip(
                        poseStack,
                        Component.translatable(
                                "gui.ecc2.gas.tank",
                                Component.translatable("gui.ecc2.gas.water"),
                                menu.waterMb(),
                                CultureIncubatorEngine.WATER_TANK_CAPACITY_MB),
                        mouseX,
                        mouseY);
            } else if (isHovering(108, 22, 16, 50, mouseX, mouseY)) {
                renderTooltip(
                        poseStack,
                        Component.translatable(
                                "gui.ecc2.gas.tank",
                                Component.translatable("gui.ecc2.gas.culture_medium"),
                                menu.cultureMb(),
                                CultureIncubatorEngine.CULTURE_TANK_CAPACITY_MB),
                        mouseX,
                        mouseY);
            }
        }

        @Override
        protected void renderBg(PoseStack poseStack, float partialTick, int mouseX, int mouseY) {
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.setShaderTexture(0, TEX);
            blit(poseStack, leftPos, topPos, 0, 0, imageWidth, imageHeight);

            drawFluidBar(
                    poseStack,
                    leftPos + 14,
                    topPos + 22,
                    16,
                    50,
                    menu.waterMb(),
                    CultureIncubatorEngine.WATER_TANK_CAPACITY_MB,
                    COLOR_WATER);
            drawFluidBar(
                    poseStack,
                    leftPos + 108,
                    topPos + 22,
                    16,
                    50,
                    menu.cultureMb(),
                    CultureIncubatorEngine.CULTURE_TANK_CAPACITY_MB,
                    COLOR_CULTURE);

            RenderSystem.setShaderTexture(0, TEX);
            blit(poseStack, leftPos + 14, topPos + 22, 180, 36, 16, 50);
            blit(poseStack, leftPos + 108, topPos + 22, 180, 36, 16, 50);

            int prog = menu.progressPx();
            if (prog > 0) {
                blit(poseStack, leftPos + 64, topPos + 46, 180, 0, prog, 15);
            }
            int boltH = (int) Math.min(14L, ((long) menu.storedEu() * 14L) / menu.capacityEu());
            if (boltH > 0) {
                blit(
                        poseStack,
                        leftPos + 62,
                        topPos + 65 + (14 - boltH),
                        180,
                        18 + (14 - boltH),
                        14,
                        boltH);
            }
        }
    }

    // ---- 5. Organic Fermenter Screen (176x180) ----
    public static final class OrganicFermenterScreen extends DeviceScreen<OrganicFermenterMenu> {
        private static final ResourceLocation TEX =
                Ecc2BridgePlatform.rl("textures/gui/organic_fermenter.png");

        public OrganicFermenterScreen(OrganicFermenterMenu menu, Inventory inv, Component title) {
            super(menu, inv, title);
            this.imageWidth = 176;
            this.imageHeight = 180;
            this.titleLabelX = 8;
            this.titleLabelY = 6;
            this.inventoryLabelX = 8;
            this.inventoryLabelY = 86;
        }

        @Override
        protected void renderPanel(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
            renderBackground(poseStack);
            super.renderPanel(poseStack, mouseX, mouseY, partialTick);
            renderTooltip(poseStack, mouseX, mouseY);
            if (isHovering(14, 22, 16, 50, mouseX, mouseY)) {
                renderTooltip(
                        poseStack,
                        Component.translatable(
                                "gui.ecc2.gas.tank",
                                Component.translatable("gui.ecc2.gas.culture_medium"),
                                menu.cultureMb(),
                                OrganicFermenterEngine.CULTURE_TANK_CAPACITY_MB),
                        mouseX,
                        mouseY);
            } else if (isHovering(108, 22, 16, 50, mouseX, mouseY)) {
                renderTooltip(
                        poseStack,
                        Component.translatable(
                                "gui.ecc2.gas.tank",
                                Component.translatable("gui.ecc2.gas.biogas"),
                                menu.biogasMb(),
                                OrganicFermenterEngine.BIOGAS_TANK_CAPACITY_MB),
                        mouseX,
                        mouseY);
            }
        }

        @Override
        protected void renderBg(PoseStack poseStack, float partialTick, int mouseX, int mouseY) {
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.setShaderTexture(0, TEX);
            blit(poseStack, leftPos, topPos, 0, 0, imageWidth, imageHeight);

            drawFluidBar(
                    poseStack,
                    leftPos + 14,
                    topPos + 22,
                    16,
                    50,
                    menu.cultureMb(),
                    OrganicFermenterEngine.CULTURE_TANK_CAPACITY_MB,
                    COLOR_CULTURE);
            drawFluidBar(
                    poseStack,
                    leftPos + 108,
                    topPos + 22,
                    16,
                    50,
                    menu.biogasMb(),
                    OrganicFermenterEngine.BIOGAS_TANK_CAPACITY_MB,
                    COLOR_BIOGAS);

            RenderSystem.setShaderTexture(0, TEX);
            blit(poseStack, leftPos + 14, topPos + 22, 180, 36, 16, 50);
            blit(poseStack, leftPos + 108, topPos + 22, 180, 36, 16, 50);

            int prog = menu.progressPx();
            if (prog > 0) {
                blit(poseStack, leftPos + 64, topPos + 44, 180, 0, prog, 15);
            }
            int boltH = (int) Math.min(14L, ((long) menu.storedEu() * 14L) / menu.capacityEu());
            if (boltH > 0) {
                blit(
                        poseStack,
                        leftPos + 62,
                        topPos + 65 + (14 - boltH),
                        180,
                        18 + (14 - boltH),
                        14,
                        boltH);
            }
        }
    }
}
