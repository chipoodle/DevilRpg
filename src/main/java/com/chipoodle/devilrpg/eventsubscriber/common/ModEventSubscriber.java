package com.chipoodle.devilrpg.eventsubscriber.common;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.config.ConfigHelper;
import com.chipoodle.devilrpg.config.ConfigHolder;
import com.chipoodle.devilrpg.init.ModBlocks;
import com.chipoodle.devilrpg.init.ModCapability;
import com.chipoodle.devilrpg.init.ModItemGroups;
import com.chipoodle.devilrpg.item.ModdedSpawnEggItem;

import net.minecraft.entity.EntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.IForgeRegistry;

/**
 * Subscribe to events from the MOD EventBus that should be handled on both
 * PHYSICAL sides in this class
 *
 * @author Cadiboo
 */
@EventBusSubscriber(modid = DevilRpg.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ModEventSubscriber {

	@SubscribeEvent
	public static void onCommonSetup(FMLCommonSetupEvent event) {
		DevilRpg.LOGGER.info("----------------------->ModEventSubscriber.onCommonSetup()");
		ModCapability.register();
	}

	/**
	 * This method will be called by Forge when it is time for the mod to register
	 * its Items. This method will always be called after the Block registry method.
	 *
	 * @param event
	 */
	@SubscribeEvent
	public static void onRegisterItems(final RegistryEvent.Register<Item> event) {
		final IForgeRegistry<Item> registry = event.getRegistry();
		// Automatically register BlockItems for all our Blocks
		ModBlocks.BLOCKS.getEntries().stream().map(RegistryObject::get)
				// You can do extra filtering here if you don't want some blocks to have an
				// BlockItem automatically registered for them
				// .filter(block -> needsItemBlock(block))
				// Register the BlockItem for the block
				.forEach(block -> {
					// Make the properties, and make it so that the item will be on our ItemGroup
					// (CreativeTab)
					final Item.Properties properties = new Item.Properties().group(ModItemGroups.MOD_ITEM_GROUP);
					// Create the new BlockItem with the block and it's properties
					final BlockItem blockItem = new BlockItem(block, properties);
					// Set the new BlockItem's registry name to the block's registry name
					blockItem.setRegistryName(block.getRegistryName());
					// Register the BlockItem
					registry.register(blockItem);
				});
		DevilRpg.LOGGER.debug("Registered BlockItems");
	}

	/**
	 * This method will be called by Forge when a config changes.
	 *
	 * @param event
	 */
	@SubscribeEvent
	public static void onModConfigEvent(final ModConfig.ModConfigEvent event) {
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
	 * Exists to work around a limitation with Spawn Eggs: Spawn Eggs require an
	 * EntityType, but EntityTypes are created AFTER Items. Therefore it is
	 * "impossible" for modded spawn eggs to exist. To get around this we have our
	 * own custom SpawnEggItem, but it needs some extra setup after Item and
	 * EntityType registration completes.
	 * 
	 * @param event
	 */
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onPostRegisterEntities(final RegistryEvent.Register<EntityType<?>> event) {
		ModdedSpawnEggItem.initUnaddedEggs();
	}

	/*
	 * @SubscribeEvent public void onPlayerFalls(LivingFallEvent event) { Entity
	 * entity = event.getEntity();
	 * 
	 * if (entity.world.isRemote || !(entity instanceof ServerPlayerEntity) ||
	 * event.getDistance() < 3) { return; }
	 * 
	 * PlayerEntity player = (PlayerEntity) entity; IMana mana = (IMana)
	 * player.getCapability(ManaProvider.MANA_CAP, null);
	 * 
	 * float points = mana.getMana(); float cost = event.getDistance() * 2;
	 * 
	 * if (points > cost) { mana.consume(cost); String message = String.
	 * format("You absorbed fall damage. It cost §7%d§r mana, you have §7%d§r mana left."
	 * , (int) cost, (int) mana.getMana()); player.sendMessage(new
	 * StringTextComponent(message)); event.setCanceled(true); } }
	 */
}
