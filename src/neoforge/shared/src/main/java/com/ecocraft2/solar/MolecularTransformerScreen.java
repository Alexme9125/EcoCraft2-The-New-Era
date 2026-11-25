package com.ecocraft2.solar;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Pixel-aligned GUI screen for {@code ecc2:molecular_transformer}, matching
 * {@code assets-gen/gui_coordinates.json} ({@code molecular_transformer.png}, 220×193).
 */
public final class MolecularTransformerScreen extends Ecc2ScreenBase<MolecularTransformerMenu> {

    private static final String TEX = "molecular_transformer";
    private static final int PANEL_W = 220;
    private static final int PANEL_H = 193;

    private static final int PROG_X = 23;
    private static final int PROG_Y = 47;
    private static final int PROG_W = 10;
    private static final int PROG_H = 15;

    private static final int LCD_TEXT_X = 57;
    private static final int LCD_MAX_W = 146;

    public MolecularTransformerScreen(
            MolecularTransformerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PANEL_W, PANEL_H);
    }

    @Override
    protected void drawBackground(
            Ecc2GuiCanvas canvas, int relMouseX, int relMouseY, float partialTick) {
        canvas.blit(TEX, 0, 0, 0, 0, PANEL_W, PANEL_H);

        // Idle gauge underlay + vertical progress fill (10x15)
        canvas.blit(TEX, PROG_X, PROG_Y, 224, 18, PROG_W, PROG_H);
        int filled = menu.scaledProgress(PROG_H);
        if (filled > 0) {
            canvas.blit(TEX, PROG_X, PROG_Y, 224, 0, PROG_W, filled);
        }
    }

    @Override
    protected void drawForeground(Ecc2GuiCanvas canvas, int relMouseX, int relMouseY) {
        canvas.centeredText(title, PANEL_W / 2, 6, 0xFF283240, false);

        ItemStack inStack = menu.inputStack();
        ItemStack outStack = menu.resolvedOutputPreview();
        boolean hasRecipe = menu.isWorking() || !outStack.isEmpty();

        if (!hasRecipe && !menu.isOutputBlocked()) {
            canvas.text(
                    Component.literal(
                            canvas.trimWidth(
                                    Component.translatable("gui.ecc2.mt.idle").getString(),
                                    LCD_MAX_W)),
                    LCD_TEXT_X,
                    50,
                    0xFF8AA8BE,
                    false);
            return;
        }

        String inName = inStack.isEmpty() ? "-" : inStack.getHoverName().getString();
        String outName =
                outStack.isEmpty()
                        ? "-"
                        : (outStack.getCount() > 1
                                ? outStack.getCount() + "× " + outStack.getHoverName().getString()
                                : outStack.getHoverName().getString());

        String row1 =
                canvas.trimWidth(
                        Component.translatable("gui.ecc2.mt.input", inName).getString(), LCD_MAX_W);
        String row2 =
                canvas.trimWidth(
                        Component.translatable("gui.ecc2.mt.output", outName).getString(),
                        LCD_MAX_W);
        String row3 =
                canvas.trimWidth(
                        Component.translatable(
                                        "gui.ecc2.mt.energy",
                                        Ecc2DataSync.formatCompact(menu.totalEu()))
                                .getString(),
                        LCD_MAX_W);
        String row4 =
                canvas.trimWidth(
                        Component.translatable(
                                        "gui.ecc2.mt.input_eu",
                                        Ecc2DataSync.formatInt(menu.lastTickPower()))
                                .getString(),
                        LCD_MAX_W);
        String row5 =
                menu.isOutputBlocked()
                        ? canvas.trimWidth(
                                Component.translatable("gui.ecc2.mt.output_full").getString(),
                                LCD_MAX_W)
                        : canvas.trimWidth(
                                Component.translatable(
                                                "gui.ecc2.mt.progress", menu.progressPercent())
                                        .getString(),
                                LCD_MAX_W);

        canvas.text(Component.literal(row1), LCD_TEXT_X, 26, 0xFFD4EEF8, false);
        canvas.text(Component.literal(row2), LCD_TEXT_X, 39, 0xFF7BE8F5, false);
        canvas.text(Component.literal(row3), LCD_TEXT_X, 52, 0xFFB8D4E4, false);
        canvas.text(Component.literal(row4), LCD_TEXT_X, 65, 0xFF72F2A8, false);
        canvas.text(
                Component.literal(row5),
                LCD_TEXT_X,
                78,
                menu.isOutputBlocked() ? 0xFFF2B652 : 0xFF9EE8FF,
                false);
    }

    @Override
    protected List<Component> hoverTooltip(int relMouseX, int relMouseY) {
        if (relMouseX >= PROG_X
                && relMouseX < PROG_X + PROG_W
                && relMouseY >= PROG_Y
                && relMouseY < PROG_Y + PROG_H) {
            return List.of(
                    Component.translatable("gui.ecc2.mt.progress", menu.progressPercent()),
                    Component.literal(
                            Ecc2DataSync.formatInt(menu.energyUsed())
                                    + " / "
                                    + Ecc2DataSync.formatInt(menu.totalEu())
                                    + " EU"));
        }
        return List.of();
    }
}
