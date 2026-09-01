package com.chipoodle.devilrpg.eventsubscriber.common;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = DevilRpg.MODID, bus = EventBusSubscriber.Bus.GAME)
public class PlayerSizeForgeEventSubscriber {

    public static final float WEREWOLF_EXTRA_HEIGHT = 0.500f;
    public static final float WEREWOLF_EXTRA_WIDTH = 0.150f;

    /**
     * Changes the size of the druid's hit box when transforming into a werewolf.
     * The eye height is derived from the entity dimensions automatically in 1.21.
     *
     * @param event
     */
    @SubscribeEvent
    public static void onSizeChanged(EntityEvent.Size event) {

        if (!(event.getEntity() instanceof Player player))
            return;

        EntityDimensions oldSize = event.getOldSize();
        PlayerAuxiliaryCapabilityInterface capability = player.getData(PlayerAuxiliaryCapability.INSTANCE);
        if (capability == null || !capability.isWerewolfTransformation()) {
            event.setNewSize(EntityType.PLAYER.getDimensions());
        } else {
            EntityDimensions newSize;
            if (event.getEntity().isCrouching()) {

                newSize = EntityDimensions.scalable(EntityType.PLAYER.getDimensions().width() + WEREWOLF_EXTRA_WIDTH + 0.430f,
                        EntityType.PLAYER.getDimensions().height() - 0.200f);
                event.setNewSize(newSize);
            } else {
                newSize = EntityDimensions.scalable(EntityType.PLAYER.getDimensions().width() + WEREWOLF_EXTRA_WIDTH,
                        EntityType.PLAYER.getDimensions().height() + WEREWOLF_EXTRA_HEIGHT);
                event.setNewSize(newSize);
            }
        }
    }

}
