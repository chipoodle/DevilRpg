/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.chipoodle.devilrpg.init;

import com.chipoodle.devilrpg.DevilRpg;

import net.minecraft.item.Item;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

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

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, DevilRpg.MODID);

    // This is a very simple Item. It has no special properties except for being on our creative tab.
    public static final RegistryObject<Item> PORTAL_ITEM = ITEMS.register("portal_item", () -> new Item(new Item.Properties().group(ModItemGroups.MOD_ITEM_GROUP)));
    //public static final RegistryObject<ModdedSpawnEggItem> WILD_BOAR_SPAWN_EGG = ITEMS.register("wild_boar_spawn_egg", () -> new ModdedSpawnEggItem(ModEntityTypes.WILD_BOAR, 0xF0A5A2, 0xA9672B, new Item.Properties().group(ModItemGroups.MOD_ITEM_GROUP)));
}
