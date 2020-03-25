package com.chipoodle.devilrpg.eventsubscriber.common;

import com.chipoodle.devilrpg.DevilRpg;
import static com.chipoodle.devilrpg.DevilRpg.LOGGER;
import com.chipoodle.devilrpg.capability.mana.IBaseManaCapability;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapabilityProvider;
import com.chipoodle.devilrpg.capability.skill.IBaseSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityProvider;
import com.chipoodle.devilrpg.init.ModCapability;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 *
 * @author Christian
 */
@EventBusSubscriber(modid = DevilRpg.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class PlayerModEventSubscriber {

	@SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
    	DevilRpg.LOGGER.info("----------------------->PlayerModEventSubscriber.onCommonSetup()");
        ModCapability.register();
    }
	
    /*@SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
    	DevilRpg.LOGGER.info("----------------------->PlayerModEventSubscriber.onCommonSetup()");
        ModCapability.register();
    }*/
    
    /*@SubscribeEvent
    public void onPlayerFalls(LivingFallEvent event) {
        Entity entity = event.getEntity();

        if (entity.world.isRemote || !(entity instanceof ServerPlayerEntity) || event.getDistance() < 3) {
            return;
        }

        PlayerEntity player = (PlayerEntity) entity;
        IMana mana = (IMana) player.getCapability(ManaProvider.MANA_CAP, null);

        float points = mana.getMana();
        float cost = event.getDistance() * 2;

        if (points > cost) {
            mana.consume(cost);
            String message = String.format("You absorbed fall damage. It cost §7%d§r mana, you have §7%d§r mana left.", (int) cost, (int) mana.getMana());
            player.sendMessage(new StringTextComponent(message));
            event.setCanceled(true);
        }
    }*/
}