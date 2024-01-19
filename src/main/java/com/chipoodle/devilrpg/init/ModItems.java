package com.chipoodle.devilrpg.init;

import com.chipoodle.devilrpg.DevilRpg;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Map;
import java.util.stream.Collectors;

public class ModItems {

    public static final String CREATIVE_TAB_NAME = "devilrpg_tab";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, DevilRpg.MODID);
    public static final RegistryObject<Item> ITEM_VACIO = ITEMS.register("item_vacio", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> ITEM_BLOCK = ITEMS.register("item_block", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> ITEM_CHARGE = ITEMS.register("item_charge", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> ITEM_SUMMON = ITEMS.register("item_summon", () -> new Item(new Item.Properties()));
    public static final RegistryObject<ForgeSpawnEggItem> SOULWOLF_SPAWN_EGG = ITEMS.register("soulwolf_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.SOUL_WOLF, 0x944a7f, 0x3b3636,
                    new Item.Properties().stacksTo(16)));
    public static final RegistryObject<ForgeSpawnEggItem> SOULBEAR_SPAWN_EGG = ITEMS.register("soulbear_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.SOUL_BEAR, 0x944b7f, 0x4b3636,
                    new Item.Properties().stacksTo(16)));
    public static final RegistryObject<ForgeSpawnEggItem> SOULWISP_SPAWN_EGG = ITEMS.register("soulwisp_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.WISP, 0x944c7f, 0x5b3636,
                    new Item.Properties().stacksTo(16)));
    public static final RegistryObject<ForgeSpawnEggItem> SOULWISP_ARCHER_SPAWN_EGG = ITEMS.register("soulwisp_archer_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.WISP_ARCHER, 0x944d7f, 0x6b3636,
                    new Item.Properties().stacksTo(16)));
    public static final RegistryObject<ForgeSpawnEggItem> SOULWISP_BOMBER_SPAWN_EGG = ITEMS.register("soulwisp_bomber_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.WISP_BOMB, 0x944e7f, 0x7b3636,
                    new Item.Properties().stacksTo(16)));
    private static Map<ResourceLocation, Item> itemDictionary;
    private static Map<Item, ResourceLocation> locationDictionary;

    public static Item getItemFromLocation(ResourceLocation location) {
        if (itemDictionary == null)
            itemDictionary = ITEMS.getEntries().stream().collect(Collectors.toMap(RegistryObject::getId, RegistryObject::get));
        return itemDictionary.getOrDefault(location, ITEM_VACIO.get());
    }

    public static ResourceLocation getLocationFromItem(Item item) {
        if (locationDictionary == null)
            locationDictionary = ITEMS.getEntries().stream().collect(Collectors.toMap(RegistryObject::get, RegistryObject::getId));
        return locationDictionary.getOrDefault(item, ITEM_VACIO.getId());
    }


}
