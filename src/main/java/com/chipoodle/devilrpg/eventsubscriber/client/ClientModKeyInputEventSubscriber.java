package com.chipoodle.devilrpg.eventsubscriber.client;

import java.util.HashMap;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.client.gui.scrollableskillscreen.ScrollableSkillScreen;
import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.handler.KeyboardSkillServerHandler;
import com.chipoodle.devilrpg.util.PowerEnum;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.util.InputMappings;
import net.minecraft.client.util.InputMappings.Input;
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
    private static final HashMap<PowerEnum, KeyBinding> keyBindingsHash = new HashMap<>();


    static {
        KEYS[0] = new KeyBinding("key.power1", -1, "key.categories.devilrpg");
        KEYS[1] = new KeyBinding("key.power2", -1, "key.categories.devilrpg");
        KEYS[2] = new KeyBinding("key.power3", -1, "key.categories.devilrpg");
        KEYS[3] = new KeyBinding("key.power4", -1, "key.categories.devilrpg");
        KEYS[4] = new KeyBinding("key.skill_gui", -1, "key.categories.devilrpg");

        keyBindingsHash.put(PowerEnum.POWER1, KEYS[0]);
        keyBindingsHash.put(PowerEnum.POWER2, KEYS[1]);
        keyBindingsHash.put(PowerEnum.POWER3, KEYS[2]);
        keyBindingsHash.put(PowerEnum.POWER4, KEYS[3]);

        /*Minecraft instance = Minecraft.getInstance();
        if (instance!= null && instance.level != null && instance.level.isClientSide)*/
        try {
            for (int i = 0; i < KEYS.length; ++i) {
                ClientRegistry.registerKeyBinding(KEYS[i]);
            }
        } catch (Exception e) {
            DevilRpg.LOGGER.error("----> Failed to register key binding");
        }
    }

    // Evento que se ejecuta en el cliente
    @SubscribeEvent
    public static void onKeyInput(KeyInputEvent event) {
        Minecraft instance = Minecraft.getInstance();

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
            Minecraft.getInstance().tell(() -> Minecraft.getInstance().setScreen(new ScrollableSkillScreen(KEYS[4].getKey())));
        }

    }

    public static String getKeyName(PowerEnum power) {
        Input key = keyBindingsHash.getOrDefault(power, new KeyBinding("", -1, "")).getKey();
        Input mapped = InputMappings.Type.KEYSYM.getOrCreate(key.getValue());
        return mapped.getDisplayName().getString();
    }

}
