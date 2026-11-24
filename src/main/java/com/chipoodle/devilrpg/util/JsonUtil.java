package com.chipoodle.devilrpg.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class JsonUtil {

    /**
     * Resuelve el objeto de un icono de skill contra el <b>registro real</b> de objetos.
     * <p>
     * Antes se delegaba en {@code ModItems.getItemFromLocation}, que recorría los
     * {@code DeferredHolder} del mod y llamaba a {@code get()}: si el árbol de skills se construía
     * antes de que el registro estuviera enlazado —o en una prueba headless sin el mod cargado—
     * lanzaba {@code Trying to access unbound value}. Todos los objetos del mod se registran en
     * {@link BuiltInRegistries#ITEM}, así que el registro de vanilla es la fuente de verdad y no
     * depende del orden de inicialización. Un id desconocido devuelve {@link Items#AIR}.
     */
    public static Item convertToItem(JsonObject jsonObject, String itemString) {
        if (jsonObject.has(itemString)) {
            JsonElement jsonElement = jsonObject.get(itemString);
            if (jsonElement.isJsonPrimitive()) {
                String s = jsonElement.getAsString();
                ResourceLocation resourceLocation = ResourceLocation.parse(s);
                //DevilRpg.LOGGER.debug("--------- Discovering resourceLocation: {} with string: {} to item", resourceLocation, s);
                Item itemFromLocation = BuiltInRegistries.ITEM.get(resourceLocation);
                //DevilRpg.LOGGER.debug("--------- Item Discovered {}", itemFromLocation);
                return itemFromLocation == null ? Items.AIR : itemFromLocation;
            } else {
                throw new JsonSyntaxException("Expected " + itemString + " to be an item, was " + jsonElement.getAsString());
            }
        } else {
            throw new JsonSyntaxException("Missing " + itemString + ", expected to find an item");
        }
    }
}
