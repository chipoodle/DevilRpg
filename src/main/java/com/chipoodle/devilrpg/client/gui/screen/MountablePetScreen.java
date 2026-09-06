package com.chipoodle.devilrpg.client.gui.screen;

import com.chipoodle.devilrpg.entity.container.MountablePetContainerMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Pantalla del inventario de la mascota montable (oso). Replica el layout del caballo de 1.21.1
 * (fondo horse.png de 256x256, region util 176x166) y dibuja los sprites de montura, armadura y
 * almacenamiento, ademas de la entidad del oso. Los slots deshabilitados (mitad en nivel 1) se marcan
 * con un recuadro oscuro.
 */
@OnlyIn(Dist.CLIENT)
public class MountablePetScreen extends AbstractContainerScreen<MountablePetContainerMenu> {

    private static final ResourceLocation HORSE_LOCATION = ResourceLocation.withDefaultNamespace("textures/gui/container/horse.png");
    private static final ResourceLocation CHEST_SLOTS_SPRITE = ResourceLocation.withDefaultNamespace("container/horse/chest_slots");
    private static final ResourceLocation SADDLE_SLOT_SPRITE = ResourceLocation.withDefaultNamespace("container/horse/saddle_slot");
    private static final ResourceLocation ARMOR_SLOT_SPRITE = ResourceLocation.withDefaultNamespace("container/horse/armor_slot");

    private float xMouse;
    private float yMouse;

    public MountablePetScreen(MountablePetContainerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.xMouse = mouseX;
        this.yMouse = mouseY;
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int leftPos = (this.width - this.imageWidth) / 2;
        int topPos = (this.height - this.imageHeight) / 2;

        // Fondo (region util 176x166 de la textura 256x256).
        guiGraphics.blit(HORSE_LOCATION, leftPos, topPos, 0, 0, this.imageWidth, this.imageHeight);

        // Dibuja la entidad del oso (como el caballo en HorseInventoryScreen).
        LivingEntity entity = this.minecraft != null && this.minecraft.level != null
                ? (LivingEntity) this.minecraft.level.getEntity(this.menu.getEntityId())
                : null;
        if (entity != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(guiGraphics,
                    leftPos + 26, topPos + 18, leftPos + 78, topPos + 70, 17, 0.25f,
                    this.xMouse, this.yMouse, entity);
        }

        // Caja de almacenamiento SIEMPRE a 5 columnas (90px) para cubrir el cuerpo oscuro del horse.png
        // y evitar la caja negra.
        guiGraphics.blitSprite(CHEST_SLOTS_SPRITE, leftPos + 79, topPos + 17, 5 * 18, 54);
        // Slot de montura.
        guiGraphics.blitSprite(SADDLE_SLOT_SPRITE, leftPos + 7, topPos + 17, 18, 18);
        // Slot de armadura.
        guiGraphics.blitSprite(ARMOR_SLOT_SPRITE, leftPos + 7, topPos + 35, 18, 18);

        // Marcar visualmente los slots de almacenamiento DESHABILITADOS (nivel 1 = solo la mitad activa).
        for (Slot slot : this.menu.getSlotsList()) {
            if (!slot.isActive()) {
                int sx = leftPos + slot.x - 1;
                int sy = topPos + slot.y - 1;
                guiGraphics.fill(sx, sy, sx + 18, sy + 18, 0x80000000);
            }
        }
    }
}
