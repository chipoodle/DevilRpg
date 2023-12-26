/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.chipoodle.devilrpg.eventsubscriber.client;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapability;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.capability.stamina.PlayerStaminaCapability;
import com.chipoodle.devilrpg.capability.stamina.PlayerStaminaCapabilityInterface;
import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.handler.DirectSkillExecutionServerHandler;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import java.util.Random;

/**
 * Subscribe to events from the FORGE EventBus that should be handled on the
 * PHYSICAL CLIENT side in this class
 *
 * @author Chipoodle
 */
@EventBusSubscriber(modid = DevilRpg.MODID, bus = EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientForgeResourceEventSubscriber {

    public static final int TICK_COUNT_REGENERATION = 10;
    public static final float BASE_MANA_DEGENERATION = 0.7f;
    public static final float BASE_STAMINA_DEGENERATION = 0.1f;

    @SubscribeEvent
    public static void onPlayerTickMana(TickEvent.PlayerTickEvent event) {
        if (event.side.equals(LogicalSide.CLIENT)) {
            if (event.phase == TickEvent.Phase.START) {
                if (event.player.tickCount % TICK_COUNT_REGENERATION == 0) {
                    PlayerManaCapabilityInterface unwrappedPlayerCapabilityMana = IGenericCapability.getUnwrappedPlayerCapability(event.player, PlayerManaCapability.INSTANCE);
                    PlayerStaminaCapabilityInterface unwrappedPlayerCapabilityStamina = IGenericCapability.getUnwrappedPlayerCapability(event.player, PlayerStaminaCapability.INSTANCE);
                    PlayerAuxiliaryCapabilityInterface unwrappedPlayerCapabilityAux = IGenericCapability.getUnwrappedPlayerCapability(event.player, PlayerAuxiliaryCapability.INSTANCE);
                    PlayerSkillCapabilityInterface unwrappedPlayerCapabilitySkill = IGenericCapability.getUnwrappedPlayerCapability(event.player, PlayerSkillCapability.INSTANCE);


                    float manaDegeneration = 0.0f;
                    if (unwrappedPlayerCapabilityAux.isWerewolfTransformation()) {
                        manaDegeneration = BASE_MANA_DEGENERATION - (0.01f * unwrappedPlayerCapabilitySkill.getSkillsPoints().get(SkillEnum.TRANSFORM_WEREWOLF));
                        unwrappedPlayerCapabilityStamina.onPlayerTickEventRegeneration(event.player, BASE_STAMINA_DEGENERATION);
                    }

                    unwrappedPlayerCapabilityMana.onPlayerTickEventRegeneration(event.player, manaDegeneration);
                    if(unwrappedPlayerCapabilityMana.getMana() <= 0.0f){
                        //ISkillContainer loadedSkillExecutor = unwrappedPlayerCapabilitySkill.getLoadedSkillExecutor(SkillEnum.TRANSFORM_WEREWOLF);
                        //loadedSkillExecutor.execute(event.player.level,event.player,null);
                        ModNetwork.CHANNEL.sendToServer(new DirectSkillExecutionServerHandler(SkillEnum.TRANSFORM_WEREWOLF));
                        event.player.level.playSound(event.player, event.player.getX(), event.player.getY(), event.player.getZ(),
                                SoundEvents.NOTE_BLOCK_BASS.get(), SoundSource.NEUTRAL, 0.5F,
                                0.4F / (new Random().nextFloat() * 0.4F + 0.8F));

                    }
                }


            }
        }
    }

}
