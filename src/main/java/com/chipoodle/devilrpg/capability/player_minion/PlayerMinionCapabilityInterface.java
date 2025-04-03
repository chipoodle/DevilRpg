package com.chipoodle.devilrpg.capability.player_minion;

import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.entity.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;


public interface PlayerMinionCapabilityInterface extends IGenericCapability {
    ConcurrentLinkedQueue<UUID> getSoulWolfMinions();

    void setSoulWolfMinions(ConcurrentLinkedQueue<UUID> minions, Player player);

    ConcurrentLinkedQueue<UUID> getSoulBearMinions();

    void setSoulBearMinions(ConcurrentLinkedQueue<UUID> minions, Player player);
    CompoundTag getSoulBearInventory();

    void setSoulBearInventory(CompoundTag soulbearInventory, Player player);

    ConcurrentLinkedQueue<UUID> getWispMinions();

    void setWispMinions(ConcurrentLinkedQueue<UUID> minions, Player player);

    ConcurrentLinkedQueue<UUID> getAllMinions();

    ITamableEntity getTamableByUUID(UUID id, Level world);

    void removeWisp(Player owner, SoulWisp entity);

    void removeSoulWolf(Player owner, SoulWolf entity);

    void removeSoulBear(Player owner, SoulBear entity);

    void removeAllWisp(Player owner);

    void removeAllSoulWolf(Player owner);

    void removeAllSoulBear(Player owner);
    SoulWisp existsWisp(Class<? extends SoulWisp> instance, Player player);

    void summonWispComplete(Level levelIn, Player player, Random rand, Supplier<SoulWisp> summonWispFunction, int maxSummons, Class<? extends SoulWisp> instance);

}
