package com.ecocraft2.giant;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;

/** NeoForge 1.21.1 menu base bridging {@link AbstractContainerMenu#clicked} across MC versions. */
public abstract class Ecc2MenuBase extends AbstractContainerMenu {

    protected Ecc2MenuBase(MenuType<?> menuType, int containerId) {
        super(menuType, containerId);
    }

    /** Captures optional server-side state immediately before a container click executes. */
    protected Object captureBeforeClick(Player player) {
        return null;
    }

    /** Invoked on the server immediately after a container click finishes. */
    protected void afterMenuClick(Player player, Object captured) {}

    @Override
    public final void clicked(int slotId, int button, ClickType clickType, Player player) {
        boolean track = player != null && !player.level().isClientSide();
        Object captured = track ? captureBeforeClick(player) : null;
        try {
            super.clicked(slotId, button, clickType, player);
        } finally {
            if (track) {
                afterMenuClick(player, captured);
            }
        }
    }
}
