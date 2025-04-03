package com.chipoodle.devilrpg.init;

import com.chipoodle.devilrpg.DevilRpg;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import com.chipoodle.devilrpg.item.*;

import java.util.Map;
import java.util.stream.Collectors;

public class ModItems {

    public static final String CREATIVE_TAB_NAME = "devilrpg_tab";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, DevilRpg.MODID);
    public static final RegistryObject<Item> ITEM_VACIO = ITEMS.register("item_vacio", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> ITEM_BLOCK = ITEMS.register("item_block", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> ITEM_CHARGE = ITEMS.register("item_charge", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> ITEM_SUMMON = ITEMS.register("item_summon", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> ITEM_FROST = ITEMS.register("item_frost", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> ITEM_VINE = ITEMS.register("item_vine", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> MANA_BERRY = ITEMS.register("mana_berry",
            () -> new ManaBerryItem(new Item.Properties().food(new FoodProperties.Builder().nutrition(4).saturationMod(0.3f).build())));

    public static final RegistryObject<ForgeSpawnEggItem> SOULWOLF_SPAWN_EGG = ITEMS.register("soulwolf_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.SOUL_WOLF, 0x944a7f, 0x3b3636,
                    new Item.Properties().stacksTo(16)));
    public static final RegistryObject<ForgeSpawnEggItem> SOULBEAR_SPAWN_EGG = ITEMS.register("soulbear_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.SOUL_BEAR, 0x944b7f, 0x4b3636,
                    new Item.Properties().stacksTo(16)));
    /*public static final RegistryObject<ForgeSpawnEggItem> SOULWISP_SPAWN_EGG = ITEMS.register("soulwisp_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.WISP, 0x944c7f, 0x5b3636,
                    new Item.Properties().stacksTo(16)));*/
    public static final RegistryObject<ForgeSpawnEggItem> SOULWISP_HEALTH_SPAWN_EGG = ITEMS.register("soulwisp_health_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.WISP_HEALTH, 0x944a7f, 0x5b3636,
                    new Item.Properties().stacksTo(16)));
    public static final RegistryObject<ForgeSpawnEggItem> SOULWISP_ARCHER_SPAWN_EGG = ITEMS.register("soulwisp_archer_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.WISP_ARCHER, 0x944c7f, 0x6b3636,
                    new Item.Properties().stacksTo(16)));

    public static final RegistryObject<ForgeSpawnEggItem> SOULWISP_CHOPPER_SPAWN_EGG = ITEMS.register("soulwisp_chopper_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.WISP_CHOPPER, 0x944e7f, 0x7b3636,
                    new Item.Properties().stacksTo(16)));

    public static final RegistryObject<ForgeSpawnEggItem> SOULWISP_FORESTER_SPAWN_EGG = ITEMS.register("soulwisp_forester_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.WISP_FORESTER, 0x944e7f, 0x8b3636,
                    new Item.Properties().stacksTo(16)));

    public static final RegistryObject<ForgeSpawnEggItem> AGGRESSIVE_ZOMBIE_SPAWN_EGG = ITEMS.register("aggressive_zombie_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.AGGRESSIVE_ZOMBIE, 0x144e7f, 0xab3634,
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
