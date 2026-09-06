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
 * Pantalla del inventario de la mascota montable (oso). Replica el layout del caballo de 1.21.1
 * (fondo horse.png de 256x256, region util 176x166) y dibuja los sprites de montura, armadura y
 * almacenamiento para que los slots se vean correctamente (antes daba un fondo negro porque se usaban
 * coordenadas 250x200 que no coinciden con la textura).
 */
@OnlyIn(Dist.CLIENT)
public class MountablePetScreen extends AbstractContainerScreen<MountablePetContainerMenu> {

    private static final ResourceLocation HORSE_LOCATION = ResourceLocation.withDefaultNamespace("textures/gui/container/horse.png");
    private static final ResourceLocation CHEST_SLOTS_SPRITE = ResourceLocation.withDefaultNamespace("container/horse/chest_slots");
    private static final ResourceLocation SADDLE_SLOT_SPRITE = ResourceLocation.withDefaultNamespace("container/horse/saddle_slot");
    private static final ResourceLocation ARMOR_SLOT_SPRITE = ResourceLocation.withDefaultNamespace("container/horse/armor_slot");

    public MountablePetScreen(MountablePetContainerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int leftPos = (this.width - this.imageWidth) / 2;
        int topPos = (this.height - this.imageHeight) / 2;

        // Fondo (region util 176x166 de la textura 256x256).
        guiGraphics.blit(HORSE_LOCATION, leftPos, topPos, 0, 0, this.imageWidth, this.imageHeight);

        // Caja de almacenamiento SIEMPRE a 5 columnas (90px) para cubrir el cuerpo oscuro del horse.png
        // y evitar la caja negra. Los slots inactivos (mitad en nivel 1) se ocultan/nodesactivan aparte.
        guiGraphics.blitSprite(CHEST_SLOTS_SPRITE, leftPos + 79, topPos + 17, 5 * 18, 54);
        // Slot de montura.
        guiGraphics.blitSprite(SADDLE_SLOT_SPRITE, leftPos + 7, topPos + 17, 18, 18);
        // Slot de armadura.
        guiGraphics.blitSprite(ARMOR_SLOT_SPRITE, leftPos + 7, topPos + 35, 18, 18);
    }
}
