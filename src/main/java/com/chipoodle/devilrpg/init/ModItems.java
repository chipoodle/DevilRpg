/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.chipoodle.devilrpg.init;

import com.chipoodle.devilrpg.DevilRpg;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Holds a list of all our {@link Item}s.
 * Suppliers that create Items are added to the DeferredRegister.
 * The DeferredRegister is then added to our mod event bus in our constructor.
 * When the Item Registry Event is fired by Forge and it is time for the mod to
 * register its Items, our Items are created and registered by the DeferredRegister.
 * The Item Registry Event will always be called after the Block registry is filled.
 * Note: This supports registry overrides.
 *
 * @author Cadiboo
 */
public class ModItems {

    public static final String CREATIVE_TAB_NAME = "devilrpg_tab";

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, DevilRpg.MODID);

    // This is a very simple Item. It has no special properties except for being on our creative tab.
    public static final RegistryObject<Item> PORTAL_ITEM = ITEMS.register("portal_item", () -> new Item(new Item.Properties()));
    //public static final RegistryObject<ModdedSpawnEggItem> WILD_BOAR_SPAWN_EGG = ITEMS.register("wild_boar_spawn_egg", () -> new ModdedSpawnEggItem(ModEntityTypes.WILD_BOAR, 0xF0A5A2, 0xA9672B, new Item.Properties().group(ModItemGroups.MOD_ITEM_GROUP)));

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
}
