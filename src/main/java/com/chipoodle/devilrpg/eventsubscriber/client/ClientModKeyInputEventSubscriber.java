package com.chipoodle.devilrpg.eventsubscriber.client;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.client.gui.scrollableskillscreen.SkillScreen;
import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.handler.KeyboardSkillServerHandler;
import com.chipoodle.devilrpg.util.PowerEnum;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import java.util.HashMap;

/**
 * Subscribe to events that should be handled on the PHYSICAL CLIENT in this
 * class
 *
 * @author Christian
 */

public class ClientModKeyInputEventSubscriber {

    private static final KeyMapping[] KEYS = new KeyMapping[5];
    private static final HashMap<PowerEnum, KeyMapping> keyBindingsHash = new HashMap<>();

    @Mod.EventBusSubscriber(modid = DevilRpg.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class KeyRegister {
        @SubscribeEvent
        public static void onRegisterKeyMappingsEvent(RegisterKeyMappingsEvent registerKeyMappingsEvent) {
            DevilRpg.LOGGER.info("----------------------->ClientModKeyInputEventSubscriber.onRegisterKeyMappingsEvent");
            KEYS[0] = new KeyMapping("key.power1", -1, "key.categories.devilrpg");
            KEYS[1] = new KeyMapping("key.power2", -1, "key.categories.devilrpg");
            KEYS[2] = new KeyMapping("key.power3", -1, "key.categories.devilrpg");
            KEYS[3] = new KeyMapping("key.power4", -1, "key.categories.devilrpg");
            KEYS[4] = new KeyMapping("key.skill_gui", -1, "key.categories.devilrpg");

            keyBindingsHash.put(PowerEnum.POWER1, KEYS[0]);
            keyBindingsHash.put(PowerEnum.POWER2, KEYS[1]);
            keyBindingsHash.put(PowerEnum.POWER3, KEYS[2]);
            keyBindingsHash.put(PowerEnum.POWER4, KEYS[3]);

            try {
                for (int i = 0; i < KEYS.length; ++i) {
                    registerKeyMappingsEvent.register(KEYS[i]);
                }
            } catch (Exception e) {
                DevilRpg.LOGGER.error("----> Failed to register key binding");
                System.exit(-1);
            }

        }

    }

    @Mod.EventBusSubscriber(modid = DevilRpg.MODID, value = Dist.CLIENT)
    public static class KeyEvent {

        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            if (KEYS[0].consumeClick()) {
                DevilRpg.LOGGER.debug(KEYS[0].saveString() + " pressed. " + KEYS[0].getKey().getValue());
                DevilRpg.LOGGER.debug("---->" + getKeyName(PowerEnum.POWER1));
                ModNetwork.CHANNEL.sendToServer(new KeyboardSkillServerHandler(PowerEnum.POWER1));
            }
            if (KEYS[1].consumeClick()) {
                DevilRpg.LOGGER.debug(KEYS[1].saveString() + " pressed. " + KEYS[1].getKey().getValue());
                DevilRpg.LOGGER.debug("---->" + getKeyName(PowerEnum.POWER2));
                ModNetwork.CHANNEL.sendToServer(new KeyboardSkillServerHandler(PowerEnum.POWER2));
            }
            if (KEYS[2].consumeClick()) {
                DevilRpg.LOGGER.debug(KEYS[2].saveString() + " pressed. " + KEYS[2].getKey().getValue());
                DevilRpg.LOGGER.debug("---->" + getKeyName(PowerEnum.POWER3));
                ModNetwork.CHANNEL.sendToServer(new KeyboardSkillServerHandler(PowerEnum.POWER3));
            }
            if (KEYS[3].consumeClick()) {
                DevilRpg.LOGGER.debug(KEYS[3].saveString() + " pressed. " + KEYS[3].getKey().getValue());
                DevilRpg.LOGGER.debug("---->" + getKeyName(PowerEnum.POWER4));
                ModNetwork.CHANNEL.sendToServer(new KeyboardSkillServerHandler(PowerEnum.POWER4));
            }
            if (KEYS[4].consumeClick()) {
                DevilRpg.LOGGER.debug(KEYS[4].saveString() + " pressed. " + KEYS[4].getKey().getValue());
                DevilRpg.LOGGER.debug(KEYS[4].getKey().getDisplayName());
                //SkillScreen.open(Minecraft.getInstance().player,KEYS[4].getKey());
                Minecraft.getInstance().tell(() -> Minecraft.getInstance().setScreen(new SkillScreen(KEYS[4].getKey())));
            }

        }

        public static String getKeyName(PowerEnum power) {
            DevilRpg.LOGGER.debug("---->getKeyName power{} ", power);
            InputConstants.Key key = keyBindingsHash.getOrDefault(power, new KeyMapping("", -1, "")).getKey();

            InputConstants.Key orCreate = InputConstants.Type.KEYSYM.getOrCreate(key.getValue());
            return orCreate.getDisplayName().getString();
        }

    }


}
