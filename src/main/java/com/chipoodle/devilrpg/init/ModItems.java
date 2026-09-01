package com.chipoodle.devilrpg.init;

import com.chipoodle.devilrpg.DevilRpg;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.chipoodle.devilrpg.item.*;

import java.util.Map;
import java.util.stream.Collectors;

public class ModItems {

    public static final String CREATIVE_TAB_NAME = "devilrpg_tab";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, DevilRpg.MODID);
    public static final DeferredHolder<Item, Item> ITEM_VACIO = ITEMS.register("item_vacio", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> ITEM_BLOCK = ITEMS.register("item_block", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> ITEM_CHARGE = ITEMS.register("item_charge", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> ITEM_SUMMON = ITEMS.register("item_summon", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> ITEM_FROST = ITEMS.register("item_frost", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> ITEM_VINE = ITEMS.register("item_vine", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> MANA_BERRY = ITEMS.register("mana_berry",
            () -> new ManaBerryItem(new Item.Properties().food(new FoodProperties.Builder().nutrition(4).saturationModifier(0.3f).build())));

    public static final DeferredHolder<Item, DeferredSpawnEggItem> SOULWOLF_SPAWN_EGG = ITEMS.register("soulwolf_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.SOUL_WOLF, 0x944a7f, 0x3b3636,
                    new Item.Properties().stacksTo(16)));
    public static final DeferredHolder<Item, DeferredSpawnEggItem> SOULBEAR_SPAWN_EGG = ITEMS.register("soulbear_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.SOUL_BEAR, 0x944b7f, 0x4b3636,
                    new Item.Properties().stacksTo(16)));
    public static final DeferredHolder<Item, DeferredSpawnEggItem> SOULWISP_HEALTH_SPAWN_EGG = ITEMS.register("soulwisp_health_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.WISP_HEALTH, 0x944a7f, 0x5b3636,
                    new Item.Properties().stacksTo(16)));
    public static final DeferredHolder<Item, DeferredSpawnEggItem> SOULWISP_ARCHER_SPAWN_EGG = ITEMS.register("soulwisp_archer_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.WISP_ARCHER, 0x944c7f, 0x6b3636,
                    new Item.Properties().stacksTo(16)));

    public static final DeferredHolder<Item, DeferredSpawnEggItem> SOULWISP_CHOPPER_SPAWN_EGG = ITEMS.register("soulwisp_chopper_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.WISP_CHOPPER, 0x944e7f, 0x7b3636,
                    new Item.Properties().stacksTo(16)));

    public static final DeferredHolder<Item, DeferredSpawnEggItem> SOULWISP_FORESTER_SPAWN_EGG = ITEMS.register("soulwisp_forester_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.WISP_FORESTER, 0x944e7f, 0x8b3636,
                    new Item.Properties().stacksTo(16)));

    public static final DeferredHolder<Item, DeferredSpawnEggItem> AGGRESSIVE_ZOMBIE_SPAWN_EGG = ITEMS.register("aggressive_zombie_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.AGGRESSIVE_ZOMBIE, 0x144e7f, 0xab3634,
                    new Item.Properties().stacksTo(16)));
    private static Map<ResourceLocation, Item> itemDictionary;
    private static Map<Item, ResourceLocation> locationDictionary;

    public static Item getItemFromLocation(ResourceLocation location) {
        if (itemDictionary == null)
            itemDictionary = ITEMS.getEntries().stream().collect(Collectors.toMap(DeferredHolder::getId, DeferredHolder::get));
        return itemDictionary.getOrDefault(location, ITEM_VACIO.get());
    }

    public static ResourceLocation getLocationFromItem(Item item) {
        if (locationDictionary == null)
            locationDictionary = ITEMS.getEntries().stream().collect(Collectors.toMap(DeferredHolder::get, DeferredHolder::getId));
        return locationDictionary.getOrDefault(item, ITEM_VACIO.getId());
    }
}
