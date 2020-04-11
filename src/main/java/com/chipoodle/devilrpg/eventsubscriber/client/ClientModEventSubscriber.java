/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.chipoodle.devilrpg.eventsubscriber.client;

import org.lwjgl.glfw.GLFW;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.init.ModRenderer;
import com.chipoodle.devilrpg.network.handler.PlayerManaClientServerHandler;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DeferredWorkQueue;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.network.PacketDistributor;

/**
 * Subscribe to events from the MOD EventBus that should be handled on the
 * PHYSICAL CLIENT side in this class
 *
 * @author Cadiboo
 */
@EventBusSubscriber(modid = DevilRpg.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEventSubscriber {

	/**
	 * We need to register our renderers on the client because rendering code does
	 * not exist on the server and trying to use it on a dedicated server will crash
	 * the game.
	 * <p>
	 * This method will be called by Forge when it is time for the mod to do its
	 * client-side setup This method will always be called after the Registry
	 * events. This means that all Blocks, Items, TileEntityTypes, etc. will all
	 * have been registered already
	 * 
	 * @param event
	 */
	@SuppressWarnings("deprecation")
	@SubscribeEvent
	public static void onFMLClientSetupEvent(final FMLClientSetupEvent event) {
		// Register TileEntity Renderers
		// ClientRegistry.bindTileEntityRenderer(ModTileEntityTypes.MINI_MODEL.get(),
		// MiniModelTileEntityRenderer::new);
		// ClientRegistry.bindTileEntityRenderer(ModTileEntityTypes.ELECTRIC_FURNACE.get(),
		// ElectricFurnaceTileEntityRenderer::new);
		DevilRpg.LOGGER.debug("Registered TileEntity Renderers");

		// Register Entity Renderers
		ModRenderer.init();
		DevilRpg.LOGGER.debug("Registered Entity Renderers");

		// Register ContainerType Screens
		// ScreenManager.registerFactory is not safe to call during parallel mod loading
		// so we queue it to run later
		/*
		 * DeferredWorkQueue.runLater(() -> {
		 * ScreenManager.registerFactory(ModContainerTypes.HEAT_COLLECTOR.get(),
		 * HeatCollectorScreen::new);
		 * ScreenManager.registerFactory(ModContainerTypes.ELECTRIC_FURNACE.get(),
		 * ElectricFurnaceScreen::new);
		 * ScreenManager.registerFactory(ModContainerTypes.MOD_FURNACE.get(),
		 * ModFurnaceScreen::new); LOGGER.debug("Registered ContainerType Screens"); });
		 */
		DeferredWorkQueue.runLater(() -> {

		});
	}
}
