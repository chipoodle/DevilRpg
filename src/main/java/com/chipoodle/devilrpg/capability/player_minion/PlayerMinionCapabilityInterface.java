package com.chipoodle.devilrpg.capability.player_minion;

import com.chipoodle.devilrpg.entity.ITameableEntity;
import com.chipoodle.devilrpg.entity.SoulBearEntity;
import com.chipoodle.devilrpg.entity.SoulWispEntity;
import com.chipoodle.devilrpg.entity.SoulWolfEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.INBTSerializable;

import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;


public interface PlayerMinionCapabilityInterface extends INBTSerializable<CompoundTag> {
    ConcurrentLinkedQueue<UUID> getSoulWolfMinions();

    void setSoulWolfMinions(ConcurrentLinkedQueue<UUID> minions, Player player);

    ConcurrentLinkedQueue<UUID> getSoulBearMinions();

    void setSoulBearMinions(ConcurrentLinkedQueue<UUID> minions, Player player);

    ConcurrentLinkedQueue<UUID> getWispMinions();

    void setWispMinions(ConcurrentLinkedQueue<UUID> minions, Player player);

    ConcurrentLinkedQueue<UUID> getAllMinions();

    ITameableEntity getTameableByUUID(UUID id, Level world);

    void removeWisp(Player owner, SoulWispEntity entity);

    void removeSoulWolf(Player owner, SoulWolfEntity entity);

    void removeSoulBear(Player owner, SoulBearEntity entity);

    void removeAllWisp(Player owner);

    void removeAllSoulWolf(Player owner);

    void removeAllSoulBear(Player owner);

}
