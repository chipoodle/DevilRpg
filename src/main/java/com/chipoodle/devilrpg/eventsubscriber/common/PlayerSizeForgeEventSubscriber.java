package com.chipoodle.devilrpg.eventsubscriber.common;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = DevilRpg.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PlayerSizeForgeEventSubscriber {

    public static final float WEREWOLF_EXTRA_HEIGHT = 0.500f;
    public static final float WEREWOLF_EXTRA_WIDTH = 0.150f;

    /**
     * Changes the size of the druid's hit box when transforming into a werewolf
     * @param event
     */
    @SubscribeEvent
    public static void onSizeChanged(EntityEvent.Size event) {

        if (!(event.getEntity() instanceof Player player))
            return;

        //DevilRpg.LOGGER.info("----------------------->ModEventSubscriber.onSizeChanged()");

        EntityDimensions oldSize = event.getOldSize();
        float oldEyeHeight = event.getOldEyeHeight();
        LazyOptional<PlayerAuxiliaryCapabilityInterface> capability = player.getCapability(PlayerAuxiliaryCapability.INSTANCE);
        if (!capability.isPresent() || !capability.map(PlayerAuxiliaryCapabilityInterface::isWerewolfTransformation).orElse(true)) {
            event.setNewSize(EntityType.PLAYER.getDimensions());
            event.setNewEyeHeight(player.getEyeHeight(player.getPose()));
        } else {
            EntityDimensions newSize;
            if(event.getEntity().isCrouching()){

                newSize = new EntityDimensions(EntityType.PLAYER.getDimensions().width + WEREWOLF_EXTRA_WIDTH + 0.430f,
                        EntityType.PLAYER.getDimensions().height - 0.200f, EntityType.PLAYER.getDimensions().fixed);
                event.setNewSize(newSize);
            }
            else{
                newSize = new EntityDimensions(EntityType.PLAYER.getDimensions().width + WEREWOLF_EXTRA_WIDTH,
                        EntityType.PLAYER.getDimensions().height + WEREWOLF_EXTRA_HEIGHT, EntityType.PLAYER.getDimensions().fixed);
                event.setNewSize(newSize);
                event.setNewEyeHeight(player.getEyeHeight(player.getPose())+ WEREWOLF_EXTRA_HEIGHT );
            }




        }


    }

    @SubscribeEvent
    public static void onLivingTickEvent(LivingEvent.LivingTickEvent event) {

    }

}
