package com.chipoodle.devilrpg.capability.player_minion;

import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.entity.ITamableEntity;
import com.chipoodle.devilrpg.entity.SoulBear;
import com.chipoodle.devilrpg.entity.SoulWisp;
import com.chipoodle.devilrpg.entity.SoulWolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;


public interface PlayerMinionCapabilityInterface extends IGenericCapability {
    ConcurrentLinkedQueue<UUID> getSoulWolfMinions();

    void setSoulWolfMinions(ConcurrentLinkedQueue<UUID> minions, Player player);

    ConcurrentLinkedQueue<UUID> getSoulBearMinions();

    void setSoulBearMinions(ConcurrentLinkedQueue<UUID> minions, Player player);

    ConcurrentLinkedQueue<UUID> getWispMinions();

    void setWispMinions(ConcurrentLinkedQueue<UUID> minions, Player player);

    ConcurrentLinkedQueue<UUID> getAllMinions();

    ITamableEntity getTameableByUUID(UUID id, Level world);

    void removeWisp(Player owner, SoulWisp entity);

    void removeSoulWolf(Player owner, SoulWolf entity);

    void removeSoulBear(Player owner, SoulBear entity);

    void removeAllWisp(Player owner);

    void removeAllSoulWolf(Player owner);

    void removeAllSoulBear(Player owner);

}
