package com.chipoodle.devilrpg.eventsubscriber.client;

import net.neoforged.neoforge.network.PacketDistributor;
import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.client.gui.scrollableskillscreen.SkillScreen;
import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.payload.KeyboardSkillPayload;
import com.chipoodle.devilrpg.util.PowerEnum;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.HashMap;

/**
 * Subscribe to events that should be handled on the PHYSICAL CLIENT in this
 * class
 *
 * @author Christian
 */

public class ClientModKeyInputEventSubscriber {

    private static final KeyMapping[] KEYS = new KeyMapping[8];
    private static final HashMap<PowerEnum, KeyMapping> keyBindingsHash = new HashMap<>();

    @EventBusSubscriber(modid = DevilRpg.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class KeyRegister {
        @SubscribeEvent
        public static void onRegisterKeyMappingsEvent(RegisterKeyMappingsEvent registerKeyMappingsEvent) {
            //DevilRpg.LOGGER.info("----------------------->ClientModKeyInputEventSubscriber.onRegisterKeyMappingsEvent");
            KEYS[0] = new KeyMapping("key.power1", -1, "key.categories.devilrpg");
            KEYS[1] = new KeyMapping("key.power2", -1, "key.categories.devilrpg");
            KEYS[2] = new KeyMapping("key.power3", -1, "key.categories.devilrpg");
            KEYS[3] = new KeyMapping("key.power4", -1, "key.categories.devilrpg");
            KEYS[4] = new KeyMapping("key.power5", -1, "key.categories.devilrpg");
            KEYS[5] = new KeyMapping("key.power6", -1, "key.categories.devilrpg");
            KEYS[6] = new KeyMapping("key.skill_gui", -1, "key.categories.devilrpg");
            KEYS[7] = new KeyMapping("key.cycle_skill_set", 75, "key.categories.devilrpg"); // K

            keyBindingsHash.put(PowerEnum.POWER1, KEYS[0]);
            keyBindingsHash.put(PowerEnum.POWER2, KEYS[1]);
            keyBindingsHash.put(PowerEnum.POWER3, KEYS[2]);
            keyBindingsHash.put(PowerEnum.POWER4, KEYS[3]);
            keyBindingsHash.put(PowerEnum.POWER5, KEYS[4]);
            keyBindingsHash.put(PowerEnum.POWER6, KEYS[5]);

            try {
                for (int i = 0; i < KEYS.length; ++i) {
                    registerKeyMappingsEvent.register(KEYS[i]);
                }
            } catch (Exception e) {
                //DevilRpg.LOGGER.error("----> Failed to register key binding");
                System.exit(-1);
            }

        }

    }

    @EventBusSubscriber(modid = DevilRpg.MODID, value = Dist.CLIENT)
    public static class KeyEvent {

        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            LocalPlayer p = Minecraft.getInstance().player;

            if(p == null)
                return;

            PlayerSkillCapabilityInterface skill = p.getData(PlayerSkillCapability.INSTANCE);
            if (KEYS[0].consumeClick()) {
                //DevilRpg.LOGGER.debug(KEYS[0].saveString() + " pressed. " + KEYS[0].getKey().getValue());
                //DevilRpg.LOGGER.debug("---->" + getKeyName(PowerEnum.POWER1));
                PacketDistributor.sendToServer(new KeyboardSkillPayload(PowerEnum.POWER1));
                //Para que se ejecute en el cliente
                if (skill != null) skill.triggerAction(p, PowerEnum.POWER1);
            }
            if (KEYS[1].consumeClick()) {
                //DevilRpg.LOGGER.debug(KEYS[1].saveString() + " pressed. " + KEYS[1].getKey().getValue());
                //DevilRpg.LOGGER.debug("---->" + getKeyName(PowerEnum.POWER2));
                PacketDistributor.sendToServer(new KeyboardSkillPayload(PowerEnum.POWER2));
                //Para que se ejecute en el cliente
                if (skill != null) skill.triggerAction(p, PowerEnum.POWER2);
            }
            if (KEYS[2].consumeClick()) {
                //DevilRpg.LOGGER.debug(KEYS[2].saveString() + " pressed. " + KEYS[2].getKey().getValue());
                //DevilRpg.LOGGER.debug("---->" + getKeyName(PowerEnum.POWER3));
                PacketDistributor.sendToServer(new KeyboardSkillPayload(PowerEnum.POWER3));
                //Para que se ejecute en el cliente
                if (skill != null) skill.triggerAction(p, PowerEnum.POWER3);
            }
            if (KEYS[3].consumeClick()) {
                //DevilRpg.LOGGER.debug(KEYS[3].saveString() + " pressed. " + KEYS[3].getKey().getValue());
                //DevilRpg.LOGGER.debug("---->" + getKeyName(PowerEnum.POWER4));
                PacketDistributor.sendToServer(new KeyboardSkillPayload(PowerEnum.POWER4));
                //Para que se ejecute en el cliente
                if (skill != null) skill.triggerAction(p, PowerEnum.POWER4);
            }
            if (KEYS[4].consumeClick()) {
                //DevilRpg.LOGGER.debug(KEYS[4].saveString() + " pressed. " + KEYS[4].getKey().getValue());
                //DevilRpg.LOGGER.debug("---->" + getKeyName(PowerEnum.POWER5));
                PacketDistributor.sendToServer(new KeyboardSkillPayload(PowerEnum.POWER5));
                //Para que se ejecute en el cliente
                if (skill != null) skill.triggerAction(p, PowerEnum.POWER5);
            }
            if (KEYS[5].consumeClick()) {
                //DevilRpg.LOGGER.debug(KEYS[4].saveString() + " pressed. " + KEYS[4].getKey().getValue());
                //DevilRpg.LOGGER.debug("---->" + getKeyName(PowerEnum.POWER5));
                PacketDistributor.sendToServer(new KeyboardSkillPayload(PowerEnum.POWER6));
                //Para que se ejecute en el cliente
                if (skill != null) skill.triggerAction(p, PowerEnum.POWER6);
            }
            if (KEYS[6].consumeClick()) {
                //DevilRpg.LOGGER.debug(KEYS[5].saveString() + " pressed. " + KEYS[5].getKey().getValue());
                //DevilRpg.LOGGER.debug(KEYS[5].getKey().getDisplayName());
                //SkillScreen.open(Minecraft.getInstance().player,KEYS[4].getKey());
                Minecraft.getInstance().tell(() -> Minecraft.getInstance().setScreen(new SkillScreen(KEYS[5].getKey())));
            }
            // Rota hacia adelante el conjunto de skills asignados (loadout).
            if (KEYS[7].consumeClick()) {
                if (skill != null) skill.rotateSkillSet(1, p);
            }

        }

        public static String getKeyName(PowerEnum power) {
            //DevilRpg.LOGGER.debug("---->getKeyName power{} ", power);
            InputConstants.Key key = keyBindingsHash.getOrDefault(power, new KeyMapping("", -1, "")).getKey();
            InputConstants.Key orCreate = InputConstants.Type.KEYSYM.getOrCreate(key.getValue());
            return orCreate.getDisplayName().getString();
        }

    }


}
