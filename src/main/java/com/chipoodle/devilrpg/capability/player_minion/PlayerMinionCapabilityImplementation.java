package com.chipoodle.devilrpg.capability.player_minion;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.entity.ITameableEntity;
import com.chipoodle.devilrpg.entity.SoulBearEntity;
import com.chipoodle.devilrpg.entity.SoulWispEntity;
import com.chipoodle.devilrpg.entity.SoulWolfEntity;
import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.handler.PlayerMinionClientServerHandler;
import com.chipoodle.devilrpg.skillsystem.MinionDeathDamageSource;
import com.chipoodle.devilrpg.util.BytesUtil;
import com.chipoodle.devilrpg.util.TargetUtils;


import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;


public class PlayerMinionCapabilityImplementation implements PlayerMinionCapabilityInterface {
	public final static String SOULWOLF_MINION_KEY = "Wolf_Minions";
	public final static String SOULBEAR_MINION_KEY = "Bear_Minions";
	public final static String WISP_MINIONS_KEY = "Wisp_Minions";
	private CompoundTag nbt = new CompoundTag();

	public PlayerMinionCapabilityImplementation() {
		ConcurrentLinkedQueue<UUID> soulwolf;
		ConcurrentLinkedQueue<UUID> soulbear;
		ConcurrentLinkedQueue<UUID> wisp;
		if (nbt.isEmpty()) {
			soulwolf = new ConcurrentLinkedQueue<UUID>();
			soulbear = new ConcurrentLinkedQueue<UUID>();
			wisp = new ConcurrentLinkedQueue<UUID>();

			try {
				nbt.putByteArray(SOULWOLF_MINION_KEY, BytesUtil.toByteArray(soulwolf));
				nbt.putByteArray(SOULBEAR_MINION_KEY, BytesUtil.toByteArray(soulbear));
				nbt.putByteArray(WISP_MINIONS_KEY, BytesUtil.toByteArray(wisp));
			} catch (IOException e) {
				DevilRpg.LOGGER.error("Error en constructor PlayerSkillCapability", e);
			}
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public ConcurrentLinkedQueue<UUID> getSoulWolfMinions() {
		try {
			return (ConcurrentLinkedQueue<UUID>) BytesUtil.toObject(nbt.getByteArray(SOULWOLF_MINION_KEY));
		} catch (ClassNotFoundException | IOException e) {
			DevilRpg.LOGGER.error("Error en getSoulWolfMinions", e);
			return null;
		}
	}

	@Override
	public void setSoulWolfMinions(ConcurrentLinkedQueue<UUID> soulWolMinions, Player player) {
		try {
			nbt.putByteArray(SOULWOLF_MINION_KEY, BytesUtil.toByteArray(soulWolMinions));
			if (!player.level.isClientSide) {
				sendSkillChangesToClient((ServerPlayer) player);
			} else {
				sendSkillChangesToServer();
			}
		} catch (IOException e) {
			DevilRpg.LOGGER.error("Error en setSoulWolfMinions", e);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public ConcurrentLinkedQueue<UUID> getSoulBearMinions() {
		try {
			return (ConcurrentLinkedQueue<UUID>) BytesUtil.toObject(nbt.getByteArray(SOULBEAR_MINION_KEY));
		} catch (ClassNotFoundException | IOException e) {
			DevilRpg.LOGGER.error("Error en getSoulBearfMinions", e);
			return null;
		}
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public ConcurrentLinkedQueue<UUID> getAllMinions() {
		try {
			ConcurrentLinkedQueue<UUID> soulbears = (ConcurrentLinkedQueue<UUID>) BytesUtil.toObject(nbt.getByteArray(SOULBEAR_MINION_KEY));
			ConcurrentLinkedQueue<UUID> soulwolves = (ConcurrentLinkedQueue<UUID>) BytesUtil.toObject(nbt.getByteArray(SOULWOLF_MINION_KEY));
			ConcurrentLinkedQueue<UUID> wisps = (ConcurrentLinkedQueue<UUID>) BytesUtil.toObject(nbt.getByteArray(WISP_MINIONS_KEY));
			soulbears.addAll(soulwolves);
			soulbears.addAll(wisps);
			return soulbears;
			
		} catch (ClassNotFoundException | IOException e) {
			DevilRpg.LOGGER.error("Error en getSoulBearfMinions", e);
			return new ConcurrentLinkedQueue<>();
		}
	}
	

	@Override
	public void setSoulBearMinions(ConcurrentLinkedQueue<UUID> soulBearMinions, Player player) {
		try {
			nbt.putByteArray(SOULBEAR_MINION_KEY, BytesUtil.toByteArray(soulBearMinions));
			if (!player.level.isClientSide) {
				sendSkillChangesToClient((ServerPlayer) player);
			} else {
				sendSkillChangesToServer();
			}
		} catch (IOException e) {
			DevilRpg.LOGGER.error("Error en setSoulBearMinions", e);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public ConcurrentLinkedQueue<UUID> getWispMinions() {
		try {
			return (ConcurrentLinkedQueue<UUID>) BytesUtil.toObject(nbt.getByteArray(WISP_MINIONS_KEY));
		} catch (ClassNotFoundException | IOException e) {
			DevilRpg.LOGGER.error("Error en getWispMinions", e);
			return null;
		}
	}

	@Override
	public void setWispMinions(ConcurrentLinkedQueue<UUID> wispMinions, Player player) {
		try {
			nbt.putByteArray(WISP_MINIONS_KEY, BytesUtil.toByteArray(wispMinions));
			if (!player.level.isClientSide) {
				sendSkillChangesToClient((ServerPlayer) player);
			} else {
				sendSkillChangesToServer();
			}
		} catch (IOException e) {
			DevilRpg.LOGGER.error("Error en setWispMinions", e);
		}
	}

	@Override
	public ITameableEntity getTameableByUUID(UUID id, Level world) {
		Entity e;
		if (id != null) {
			if (!world.isClientSide) {
				e = TargetUtils.getEntityByUUID((ServerLevel) world, id);
			} else {
				e = TargetUtils.getEntityByUUID((ClientLevel) world, id);
			}
			if (e != null && e instanceof ITameableEntity) {
				return (ITameableEntity) e;
			}
		}
		return null;
	}

	@Override
	public void removeWisp(Player owner, SoulWispEntity entity) {
		ConcurrentLinkedQueue<UUID> wisp = getWispMinions();
		if (wisp != null && entity != null && wisp.contains(entity.getUUID())) {
			wisp.remove(entity.getUUID());
			setWispMinions(wisp, owner);
			entity.hurt(new MinionDeathDamageSource(""), Integer.MAX_VALUE);
		}
	}

	@Override
	public void removeSoulWolf(Player owner, SoulWolfEntity entity) {
		ConcurrentLinkedQueue<UUID> soulwolf = getSoulWolfMinions();
		if (soulwolf != null && entity != null && soulwolf.contains(entity.getUUID())) {
			soulwolf.remove(entity.getUUID());
			setSoulWolfMinions(soulwolf, owner);
			DamageSource minionDeathDamageSource = new MinionDeathDamageSource("");
			entity.hurt(minionDeathDamageSource, Integer.MAX_VALUE);
		}
	}

	@Override
	public void removeAllWisp(Player owner) {
		ConcurrentLinkedQueue<UUID> wisp = getWispMinions();
		wisp.forEach(id -> {
			ITameableEntity entity = getTameableByUUID(id, owner.level);
			removeWisp(owner, (SoulWispEntity) entity);
		});
		wisp.clear();
		setWispMinions(wisp, owner);
	}

	@Override
	public void removeAllSoulWolf(Player owner) {
		ConcurrentLinkedQueue<UUID> soulwolf = getSoulWolfMinions();
		soulwolf.forEach(id -> {
			ITameableEntity entity = getTameableByUUID(id, owner.level);
			removeSoulWolf(owner, (SoulWolfEntity) entity);
		});
		soulwolf.clear();
		setSoulWolfMinions(soulwolf, owner);
	}

	@Override
	public void removeSoulBear(Player owner, SoulBearEntity entity) {
		ConcurrentLinkedQueue<UUID> soulbear = getSoulBearMinions();
		if (soulbear != null && entity != null && soulbear.contains(entity.getUUID())) {
			soulbear.remove(entity.getUUID());
			setSoulBearMinions(soulbear, owner);
			entity.hurt(new MinionDeathDamageSource(""), Integer.MAX_VALUE);
		}

	}

	@Override
	public void removeAllSoulBear(Player owner) {
		ConcurrentLinkedQueue<UUID> soulbear = getSoulBearMinions();
		soulbear.forEach(id -> {
			ITameableEntity entity = getTameableByUUID(id, owner.level);
			removeSoulBear(owner, (SoulBearEntity) entity);
		});
		soulbear.clear();
		setSoulBearMinions(soulbear, owner);
	}

	@Override
	public CompoundTag serializeNBT() {
		return nbt;
	}

	@Override
	public void deserializeNBT(CompoundTag nbt) {
		this.nbt = nbt;
	}

	private void sendSkillChangesToServer() {
		ModNetwork.CHANNEL.sendToServer(new PlayerMinionClientServerHandler(serializeNBT()));
	}

	private void sendSkillChangesToClient(ServerPlayer pe) {
		ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> pe),
				new PlayerMinionClientServerHandler(serializeNBT()));
	}

}
