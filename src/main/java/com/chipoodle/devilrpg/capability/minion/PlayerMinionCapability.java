package com.chipoodle.devilrpg.capability.minion;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.entity.SoulBearEntity;
import com.chipoodle.devilrpg.entity.SoulWolfEntity;
import com.chipoodle.devilrpg.entity.WispEntity;
import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.handler.PlayerMinionClientServerHandler;
import com.chipoodle.devilrpg.skillsystem.MinionDeathDamageSource;
import com.chipoodle.devilrpg.util.BytesUtil;
import com.chipoodle.devilrpg.util.TargetUtils;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.network.PacketDistributor;

public class PlayerMinionCapability implements IBaseMinionCapability {
	public final static String SOULWOLF_MINION_KEY = "Wolf_Minions";
	public final static String SOULBEAR_MINION_KEY = "Bear_Minions";
	public final static String WISP_MINIONS_KEY = "Wisp_Minions";
	private CompoundNBT nbt = new CompoundNBT();

	public PlayerMinionCapability() {
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
	public void setSoulWolfMinions(ConcurrentLinkedQueue<UUID> soulWolMinions, PlayerEntity player) {
		try {
			nbt.putByteArray(SOULWOLF_MINION_KEY, BytesUtil.toByteArray(soulWolMinions));
			if (!player.world.isRemote) {
				sendSkillChangesToClient((ServerPlayerEntity) player);
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
	
	@Override
	public void setSoulBearMinions(ConcurrentLinkedQueue<UUID> soulBearMinions, PlayerEntity player) {
		try {
			nbt.putByteArray(SOULBEAR_MINION_KEY, BytesUtil.toByteArray(soulBearMinions));
			if (!player.world.isRemote) {
				sendSkillChangesToClient((ServerPlayerEntity) player);
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
	public void setWispMinions(ConcurrentLinkedQueue<UUID> wispMinions, PlayerEntity player) {
		try {
			nbt.putByteArray(WISP_MINIONS_KEY, BytesUtil.toByteArray(wispMinions));
			if (!player.world.isRemote) {
				sendSkillChangesToClient((ServerPlayerEntity) player);
			} else {
				sendSkillChangesToServer();
			}
		} catch (IOException e) {
			DevilRpg.LOGGER.error("Error en setWispMinions", e);
		}
	}

	@Override
	public TameableEntity getTameableByUUID(UUID id, World world) {
		Entity e;
		if (!world.isRemote) {
			e = TargetUtils.getEntityByUUID((ServerWorld) world, id);
		} else {
			e = TargetUtils.getEntityByUUID((ClientWorld) world, id);
		}
		if (e!= null && e instanceof TameableEntity) {
			return (TameableEntity) e;
		}
		return null;
	}

	@Override
	public void removeWisp(PlayerEntity owner, WispEntity entity) {
		ConcurrentLinkedQueue<UUID> wisp = getWispMinions();
		if (wisp!= null && entity!= null && wisp.contains(entity.getUniqueID())) {
			wisp.remove(entity.getUniqueID());
			setWispMinions(wisp, owner);
			entity.attackEntityFrom(new MinionDeathDamageSource(""), Integer.MAX_VALUE);
		}
	}

	@Override
	public void removeSoulWolf(PlayerEntity owner, SoulWolfEntity entity) {
		ConcurrentLinkedQueue<UUID> soulwolf = getSoulWolfMinions();
		if (soulwolf!= null && entity!= null && soulwolf.contains(entity.getUniqueID())) {
			soulwolf.remove(entity.getUniqueID());
			setSoulWolfMinions(soulwolf, owner);
			entity.attackEntityFrom(new MinionDeathDamageSource(""), Integer.MAX_VALUE);
		}

	}

	@Override
	public void removeAllWisp(PlayerEntity owner) {
		ConcurrentLinkedQueue<UUID> wisp = getWispMinions();
		wisp.forEach(id -> {
			TameableEntity entity = getTameableByUUID(id, owner.world);
			removeWisp(owner, (WispEntity) entity);
		});
		wisp.clear();
		setWispMinions(wisp, owner);
	}

	@Override
	public void removeAllSoulWolf(PlayerEntity owner) {
		ConcurrentLinkedQueue<UUID> soulwolf = getSoulWolfMinions();
		soulwolf.forEach(id -> {
			TameableEntity entity = getTameableByUUID(id, owner.world);
			removeSoulWolf(owner, (SoulWolfEntity) entity);
		});
		soulwolf.clear();
		setSoulWolfMinions(soulwolf, owner);
	}
	
	@Override
	public void removeSoulBear(PlayerEntity owner, SoulBearEntity entity) {
		ConcurrentLinkedQueue<UUID> soulbear = getSoulBearMinions();
		if (soulbear!= null && entity!= null && soulbear.contains(entity.getUniqueID())) {
			soulbear.remove(entity.getUniqueID());
			setSoulBearMinions(soulbear, owner);
			entity.attackEntityFrom(new MinionDeathDamageSource(""), Integer.MAX_VALUE);
		}

	}
	
	@Override
	public void removeAllSoulBear(PlayerEntity owner) {
		ConcurrentLinkedQueue<UUID> soulbear = getSoulBearMinions();
		soulbear.forEach(id -> {
			TameableEntity entity = getTameableByUUID(id, owner.world);
			removeSoulBear(owner, (SoulBearEntity) entity);
		});
		soulbear.clear();
		setSoulBearMinions(soulbear, owner);
	}

	@Override
	public CompoundNBT getNBTData() {
		return nbt;
	}

	@Override
	public void setNBTData(CompoundNBT nbt) {
		this.nbt = nbt;
	}

	private void sendSkillChangesToServer() {
		ModNetwork.CHANNEL.sendToServer(new PlayerMinionClientServerHandler(getNBTData()));
	}

	private void sendSkillChangesToClient(ServerPlayerEntity pe) {
		ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> pe),
				new PlayerMinionClientServerHandler(getNBTData()));
	}

}
