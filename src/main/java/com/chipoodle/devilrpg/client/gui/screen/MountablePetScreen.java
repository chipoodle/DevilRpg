package com.chipoodle.devilrpg.client.gui.screen;

import com.chipoodle.devilrpg.entity.container.MountablePetContainerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Pantalla del inventario de la mascota montable (oso), con el mismo layout que el del caballo/burro:
 * montura, armadura, almacenamiento 3x9 e inventario del jugador.
 */
@OnlyIn(Dist.CLIENT)
public class MountablePetScreen extends AbstractContainerScreen<MountablePetContainerMenu> {

    private static final ResourceLocation HORSE_LOCATION = ResourceLocation.withDefaultNamespace("textures/gui/container/horse.png");

    public MountablePetScreen(MountablePetContainerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 250;
        this.imageHeight = 200;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int i = (this.width - this.imageWidth) / 2;
        int j = (this.height - this.imageHeight) / 2;
        guiGraphics.blit(HORSE_LOCATION, i, j, 0, 0, this.imageWidth, this.imageHeight);
    }
}
