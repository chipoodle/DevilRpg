package com.chipoodle.devilrpg.eventsubscriber.client;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.client.gui.skillbook.SkillScreen;
import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.handler.KeyboardSkillServerHandler;
import com.chipoodle.devilrpg.util.PowerEnum;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.PlayerEntity;
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
public class KeyInputEventSubscriber {

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

	// Evento que s�lo se ejecuta en el cliente
	@SubscribeEvent
	public static void onKeyInput(KeyInputEvent event) {
		Minecraft instance = Minecraft.getInstance();
		PlayerEntity player = instance.player;

		if (KEYS[0].isPressed()) {
			DevilRpg.LOGGER.debug(KEYS[0].getTranslationKey() + "pressed. " + KEYS[0].getKey().getKeyCode());
			ModNetwork.CHANNEL.sendToServer(new KeyboardSkillServerHandler(PowerEnum.POWER1));
		}
		if (KEYS[1].isPressed()) {
			DevilRpg.LOGGER.debug(KEYS[1].getTranslationKey() + "pressed. " + KEYS[1].getKey().getKeyCode());
			ModNetwork.CHANNEL.sendToServer(new KeyboardSkillServerHandler(PowerEnum.POWER2));
		}
		if (KEYS[2].isPressed()) {
			DevilRpg.LOGGER.debug(KEYS[2].getTranslationKey() + "pressed. " + KEYS[2].getKey().getKeyCode());
			ModNetwork.CHANNEL.sendToServer(new KeyboardSkillServerHandler(PowerEnum.POWER3));
		}
		if (KEYS[3].isPressed()) {
			DevilRpg.LOGGER.debug(KEYS[3].getTranslationKey() + "pressed. " + KEYS[3].getKey().getKeyCode());
			ModNetwork.CHANNEL.sendToServer(new KeyboardSkillServerHandler(PowerEnum.POWER4));
		}
		if (KEYS[4].isPressed()) {
			DevilRpg.LOGGER.debug(KEYS[4].getTranslationKey() + "pressed. " + KEYS[4].getKey().getKeyCode());
			SkillScreen.open(player);

			/*ClientScrollableSkillManager skillManager = new ClientScrollableSkillManager(instance);
			ScrollableSkillInfoPacket packetIn = new ScrollableSkillInfoPacket();
			PacketBuffer buf = new PacketBuffer(buf);
			packetIn.readPacketData(buf);
			skillManager.read(packetIn);
			Minecraft.getInstance().enqueue(() -> Minecraft.getInstance().displayGuiScreen(new ScrollableSkillScreen(skillManager)));*/

		}

	}

}
