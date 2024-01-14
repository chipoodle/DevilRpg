package com.chipoodle.devilrpg.eventsubscriber.common;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.config.ConfigHelper;
import com.chipoodle.devilrpg.config.ConfigHolder;
import com.chipoodle.devilrpg.entity.*;
import com.chipoodle.devilrpg.init.ModEntities;
import com.chipoodle.devilrpg.init.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.CreativeModeTabEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

import java.util.function.Supplier;

import static com.chipoodle.devilrpg.init.ModBlocks.BLOCKS;
import static com.chipoodle.devilrpg.init.ModItems.*;


/**
 * Subscribe to events from the MOD EventBus that should be handled on both
 * PHYSICAL sides in this class
 *
 * @author Cadiboo
 */
@EventBusSubscriber(modid = DevilRpg.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class PlayerModEventSubscriber {


    @SubscribeEvent
    public static void initEntityAttributes(EntityAttributeCreationEvent event) {
        DevilRpg.LOGGER.info("----------------------->ModEventSubscriber.initEntityAttributes()");
        event.put(ModEntities.SOUL_WOLF.get(), SoulWolf.setAttributes().build());
        event.put(ModEntities.SOUL_BEAR.get(), SoulBear.setAttributes().build());
        event.put(ModEntities.WISP.get(), SoulWisp.setAttributes().build());
        event.put(ModEntities.WISP_BOMB.get(), SoulWispBomber.setAttributes().build());
        event.put(ModEntities.WISP_ARCHER.get(), SoulWispArcher.setAttributes().build());
    }

    @SubscribeEvent
    public static void updateEntityAttributes(EntityAttributeModificationEvent event) {
        DevilRpg.LOGGER.info("----------------------->ModEventSubscriber.updateEntityAttributes()");
        /*if (!event.has(EntityType.CREEPER, EXAMPLE_ATTRIBUTE.get())) {
            event.add(EntityType.CREEPER,
                    EXAMPLE_ATTRIBUTE.get() // Applies new attribute to creeper
            );
        }*/
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        DevilRpg.LOGGER.info("----------------------->ModEventSubscriber.onCommonSetup()");

    }

    /**
     * This method will be called by Forge when a config changes.
     *
     * @param event
     */
    @SubscribeEvent
    public static void onModConfigEvent(final ModConfigEvent.Loading  event) {
        DevilRpg.LOGGER.info("----------------------->ModEventSubscriber.onModConfigEvent()");
        final ModConfig config = event.getConfig();
        // Rebake the configs when they change
        if (config.getSpec() == ConfigHolder.CLIENT_SPEC) {
            ConfigHelper.bakeClient(config);
            DevilRpg.LOGGER.debug("Baked client config");
        } else if (config.getSpec() == ConfigHolder.SERVER_SPEC) {
            ConfigHelper.bakeServer(config);
            DevilRpg.LOGGER.debug("Baked server config");
        }
    }

    /**
     * This method will be called by Forge when it is time for the mod to register
     * its Items. This method will always be called after the Block registry method.
     *
     * @param event
     */
    @SubscribeEvent
    public static void onRegisterItems(final RegisterEvent event) {
        if (event.getRegistryKey().equals(ForgeRegistries.Keys.ITEMS)) {
            DevilRpg.LOGGER.info("----------------------->ModEventSubscriber.onRegisterItems()");
            BLOCKS.getEntries().forEach((blockRegistryObject) -> {
                Block block = blockRegistryObject.get();
                Item.Properties properties = new Item.Properties();
                Supplier<Item> blockItemFactory = () -> new BlockItem(block, properties);
                event.register(ForgeRegistries.Keys.ITEMS, blockRegistryObject.getId(), blockItemFactory);
            });
        }
    }

    /**
     * event that allows an item can be added to a CreativeModeTab via CreativeModeTabEvent$BuildContents on the mod event bus. An item(s) can be added without any additional configurations via #accept.
     *
     * @param event
     */
    /*@SubscribeEvent
    public static void onCreativeModeTabEvent$BuildContents(final CreativeModeTabEvent.BuildContents event) {
        if (event.getTab() == CreativeModeTabs.COMBAT) {
            event.accept(PORTAL_ITEM);
        }

    }*/

    // Registered on the MOD event bus
// Assume we have RegistryObject<Item> and RegistryObject<Block> called ITEM and BLOCK
    @SubscribeEvent
    public static void onRegisterCreativeModeTabEvent(CreativeModeTabEvent.Register event) {
        DevilRpg.LOGGER.info("----------------------->ModEventSubscriber.onRegisterCreativeModeTabEvent()");
        event.registerCreativeModeTab(new ResourceLocation(DevilRpg.MODID, ModItems.CREATIVE_TAB_NAME), builder ->
                // Set name of tab to display
                builder.title(Component.translatable("item_group." + DevilRpg.MODID + "." + ModItems.CREATIVE_TAB_NAME))
                        // Set icon of creative tab
                        .icon(() -> new ItemStack(ITEM_VACIO.get()))
                        // Add default items to tab
                        .displayItems((enabledFlags, populator) -> {
                            BLOCKS.getEntries().forEach((blockRegistryObject) -> {
                                Block block = blockRegistryObject.get();
                                populator.accept(block);
                            });
                            populator.accept(SOULWOLF_SPAWN_EGG.get());
                            populator.accept(SOULBEAR_SPAWN_EGG.get());
                            populator.accept(SOULWISP_SPAWN_EGG.get());
                            populator.accept(SOULWISP_ARCHER_SPAWN_EGG.get());
                            populator.accept(SOULWISP_BOMBER_SPAWN_EGG.get());
                        })
        );


    }

}
