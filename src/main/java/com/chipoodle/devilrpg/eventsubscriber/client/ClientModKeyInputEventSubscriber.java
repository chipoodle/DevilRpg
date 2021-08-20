package com.chipoodle.devilrpg.eventsubscriber.client;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.client.gui.scrollableskillscreen.ScrollableSkillScreen;
import com.chipoodle.devilrpg.client.gui.skillbook.SkillScreen;
import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.handler.KeyboardSkillServerHandler;
import com.chipoodle.devilrpg.util.PowerEnum;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.util.InputMappings;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent.KeyInputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Subscribe to events that should be handled on the PHYSICAL CLIENT in this
 * class
 *
 * @author Christian
 */
@EventBusSubscriber(modid = DevilRpg.MODID, value = Dist.CLIENT)
public class ClientModKeyInputEventSubscriber {

	private static final KeyBinding[] KEYS = new KeyBinding[5];

	static {
		KEYS[0] = new KeyBinding("key.power1", -1, "key.categories.devilrpg");
		KEYS[1] = new KeyBinding("key.power2", -1, "key.categories.devilrpg");
		KEYS[2] = new KeyBinding("key.power3", -1, "key.categories.devilrpg");
		KEYS[3] = new KeyBinding("key.power4", -1, "key.categories.devilrpg");
		KEYS[4] = new KeyBinding("key.skill_gui", -1, "key.categories.devilrpg");

		for (int i = 0; i < KEYS.length; ++i) {
			ClientRegistry.registerKeyBinding(KEYS[i]);
		}
	}

	// Evento que se ejecuta en el cliente
	@SubscribeEvent
	public static void onKeyInput(KeyInputEvent event) throws Exception {
		Minecraft instance = Minecraft.getInstance();
		
		if (KEYS[0].consumeClick()) {
			DevilRpg.LOGGER.debug(KEYS[0].saveString() + " pressed. " + KEYS[0].getKey().getValue());
			ModNetwork.CHANNEL.sendToServer(new KeyboardSkillServerHandler(PowerEnum.POWER1));
		}
		if (KEYS[1].consumeClick()) {
			DevilRpg.LOGGER.debug(KEYS[1].saveString() + " pressed. " + KEYS[1].getKey().getValue());
			ModNetwork.CHANNEL.sendToServer(new KeyboardSkillServerHandler(PowerEnum.POWER2));
		}
		if (KEYS[2].consumeClick()) {
			DevilRpg.LOGGER.debug(KEYS[2].saveString() + " pressed. " + KEYS[2].getKey().getValue());
			ModNetwork.CHANNEL.sendToServer(new KeyboardSkillServerHandler(PowerEnum.POWER3));
		}
		if (KEYS[3].consumeClick()) {
			DevilRpg.LOGGER.debug(KEYS[3].saveString() + " pressed. " + KEYS[3].getKey().getValue());
			ModNetwork.CHANNEL.sendToServer(new KeyboardSkillServerHandler(PowerEnum.POWER4));
		}
		if (KEYS[4].consumeClick()) {
			DevilRpg.LOGGER.debug(KEYS[4].saveString() + " pressed. " + KEYS[4].getKey().getValue());
			//SkillScreen.open(Minecraft.getInstance().player,KEYS[4].getKey());
			Minecraft.getInstance().tell(() -> Minecraft.getInstance().setScreen(new ScrollableSkillScreen(KEYS[4].getKey())));
		}

	}

}
