/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.chipoodle.devilrpg.eventsubscriber.client;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapability;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapabilityInterface;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Subscribe to events from the FORGE EventBus that should be handled on the
 * PHYSICAL CLIENT side in this class
 *
 * @author Chipoodle
 */
@EventBusSubscriber(modid = DevilRpg.MODID, bus = EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientForgeManaEventSubscriber {

    @SubscribeEvent
    public static void onPlayerTickMana(TickEvent.PlayerTickEvent event) {
        if (event.side.equals(LogicalSide.CLIENT)) {
            if (event.phase == TickEvent.Phase.START) {
                if (event.player.tickCount % 10 == 0) {
                    PlayerManaCapabilityInterface unwrappedPlayerCapability = IGenericCapability.getUnwrappedPlayerCapability(event.player, PlayerManaCapability.INSTANCE);
                    unwrappedPlayerCapability.onPlayerTickEventRegeneration(event.player);
                }

            }
        }
    }

}
