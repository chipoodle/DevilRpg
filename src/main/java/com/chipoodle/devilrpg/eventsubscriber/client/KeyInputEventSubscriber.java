package com.chipoodle.devilrpg.eventsubscriber.client;

import java.util.List;
import java.util.stream.Stream;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.client.gui.skill.SkillScreen;
import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.handler.KeyboardSkillServerHandler;
import com.chipoodle.devilrpg.util.ConstantesPower;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent.KeyInputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
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

    //Evento que sólo se ejecuta en el cliente
    @SubscribeEvent
    public static void onKeyInput(KeyInputEvent event) {
        Minecraft instance = Minecraft.getInstance();
		PlayerEntity player = instance.player;
        

        if (KEYS[0].isPressed()) {
        	DevilRpg.LOGGER.debug(KEYS[0].getLocalizedName() + "pressed. " + KEYS[0].getKey().getKeyCode());        
            ModNetwork.CHANNEL.sendToServer(new KeyboardSkillServerHandler(ConstantesPower.POWER1));
        }
        if (KEYS[1].isPressed()) {
        	DevilRpg.LOGGER.debug(KEYS[1].getLocalizedName() + "pressed. " + KEYS[1].getKey().getKeyCode());
            ModNetwork.CHANNEL.sendToServer(new KeyboardSkillServerHandler(ConstantesPower.POWER2));
        }
        if (KEYS[2].isPressed()) {
        	DevilRpg.LOGGER.debug(KEYS[2].getLocalizedName() + "pressed. " + KEYS[2].getKey().getKeyCode());
            ModNetwork.CHANNEL.sendToServer(new KeyboardSkillServerHandler(ConstantesPower.POWER3));
        }
        if (KEYS[3].isPressed()) {
        	DevilRpg.LOGGER.debug(KEYS[3].getLocalizedName() + "pressed. " + KEYS[3].getKey().getKeyCode());
            ModNetwork.CHANNEL.sendToServer(new KeyboardSkillServerHandler(ConstantesPower.POWER4));
        }
        if (KEYS[4].isPressed()) {
        	DevilRpg.LOGGER.debug(KEYS[4].getLocalizedName() + "pressed. " + KEYS[4].getKey().getKeyCode());
        	SkillScreen.open(player);
        }

    }

}
