package com.chipoodle.devilrpg.util;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.init.ModItems;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public class JsonUtil {

    public static Item convertToItem(JsonObject jsonObject, String itemString) {
        if (jsonObject.has(itemString)) {
            JsonElement jsonElement = jsonObject.get(itemString);
            if (jsonElement.isJsonPrimitive()) {
                String s = jsonElement.getAsString();
                ResourceLocation resourceLocation = new ResourceLocation(s);
                //DevilRpg.LOGGER.debug("--------- Discovering resourceLocation: {} with string: {} to item", resourceLocation, s);
                Item itemFromLocation = ModItems.getItemFromLocation(resourceLocation);
                //DevilRpg.LOGGER.debug("--------- Item Discovered {}", itemFromLocation);
                return itemFromLocation;
            } else {
                throw new JsonSyntaxException("Expected " + itemString + " to be an item, was " + jsonElement.getAsString());
            }
        } else {
            throw new JsonSyntaxException("Missing " + itemString + ", expected to find an item");
        }
    }
}
