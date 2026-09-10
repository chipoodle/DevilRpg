package com.chipoodle.devilrpg.eventsubscriber.client;

import com.chipoodle.devilrpg.DevilRpg;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Añade una descripción (lore) al tooltip de los items del mod. Para cada item, si existe la clave de
 * traducción {@code tooltip.<descriptionId>} (por ejemplo
 * {@code tooltip.item.devilrpg.frost_vex_spawn_egg}), se añade como una línea gris/cursiva bajo el nombre.
 * Así los spawn eggs y bloques del mod explican qué hacen, igual que el resto de items.
 */
@EventBusSubscriber(modid = DevilRpg.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ItemTooltipSubscriber {

    private ItemTooltipSubscriber() {
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }
        String descriptionId = stack.getItem().getDescriptionId();
        if (!descriptionId.startsWith("item." + DevilRpg.MODID) && !descriptionId.startsWith("block." + DevilRpg.MODID)) {
            return; // solo items/bloques del mod
        }
        String tooltipKey = "tooltip." + descriptionId;
        if (I18n.exists(tooltipKey)) {
            event.getToolTip().add(Component.translatable(tooltipKey)
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }
}
