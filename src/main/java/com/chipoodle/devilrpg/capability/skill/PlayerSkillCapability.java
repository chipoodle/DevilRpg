package com.chipoodle.devilrpg.capability.skill;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.mana.IBaseManaCapability;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapabilityProvider;
import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.handler.PlayerSkillClientServerHandler;
import com.chipoodle.devilrpg.skillsystem.ISkillContainer;
import com.chipoodle.devilrpg.skillsystem.SingletonSkillFactory;
import com.chipoodle.devilrpg.util.BytesUtil;
import com.chipoodle.devilrpg.util.PowerEnum;
import com.chipoodle.devilrpg.util.SkillEnum;

import net.minecraft.entity.ai.attributes.Attribute;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.network.PacketDistributor;

public class PlayerSkillCapability implements IBaseSkillCapability {
	public final static String POWERS_KEY = "Powers";
	public final static String SKILLS_KEY = "Skills";
	public final static String MAX_SKILLS_KEY = "MaxSkills";
	public final static String MANA_COST_KEY = "ManaCost";
	public final static String MINIONS_KEY = "Minions";
	public final static String ATTRIBUTE_MODIFIER_KEY = "AttributeModifier";
	
	private CompoundNBT nbt = new CompoundNBT();
	private SingletonSkillFactory singletonSkillFactory;

	public PlayerSkillCapability() {
		if (nbt.isEmpty()) {
			HashMap<PowerEnum, SkillEnum> powers = new HashMap<>();
			HashMap<SkillEnum, Integer> skills = new HashMap<>();
			HashMap<SkillEnum, Integer> maxSkills = new HashMap<>();
			HashMap<SkillEnum, Integer> manaCostContainer = new HashMap<>();
			ConcurrentLinkedQueue<TameableEntity> minions = new ConcurrentLinkedQueue<TameableEntity>();
			HashMap<Attribute, UUID> attributeModifiers = new HashMap<>();
			
			for (PowerEnum p : Arrays.asList(PowerEnum.values())) {
				powers.put(p, null);
			}
			for (SkillEnum s : Arrays.asList(SkillEnum.values())) {
				skills.put(s, 0);
				maxSkills.put(s, 20);
			}
			for (SkillEnum s : Arrays.asList(SkillEnum.values())) {
				manaCostContainer.put(s, 20);
				if(s.equals(SkillEnum.FIREBALL)) {
					manaCostContainer.put(s, 3);
				}	
			}

			try {
				nbt.putByteArray(POWERS_KEY, BytesUtil.toByteArray(powers));
				nbt.putByteArray(SKILLS_KEY, BytesUtil.toByteArray(skills));
				nbt.putByteArray(MAX_SKILLS_KEY, BytesUtil.toByteArray(maxSkills));
				nbt.putByteArray(MANA_COST_KEY, BytesUtil.toByteArray(manaCostContainer));
				nbt.putByteArray(MINIONS_KEY, BytesUtil.toByteArray(minions));
				nbt.putByteArray(ATTRIBUTE_MODIFIER_KEY, BytesUtil.toByteArray(attributeModifiers));
			} catch (IOException e) {
				DevilRpg.LOGGER.error("Error en constructor PlayerSkillCapability", e);
			}
		}
		singletonSkillFactory = new SingletonSkillFactory(this);
	}

	@SuppressWarnings("unchecked")
	@Override
	public HashMap<PowerEnum, SkillEnum> getSkillsNameOfPowers() {
		try {
			return (HashMap<PowerEnum, SkillEnum>) BytesUtil.toObject(nbt.getByteArray(POWERS_KEY));
		} catch (ClassNotFoundException | IOException e) {
			DevilRpg.LOGGER.error("Error en getSkillsNameOfPowers", e);
			return null;
		}
	}

	@Override
	public void setSkillsNameOfPowers(HashMap<PowerEnum, SkillEnum> names, PlayerEntity player) {
		try {
			nbt.putByteArray(POWERS_KEY, BytesUtil.toByteArray(names));
			if (!player.world.isRemote) {
				sendSkillChangesToClient((ServerPlayerEntity) player);
			} else {
				sendSkillChangesToServer();
			}
		} catch (IOException e) {
			DevilRpg.LOGGER.error("Error en setSkillsNameOfPowers", e);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public HashMap<SkillEnum, Integer> getSkillsPoints() {
		try {
			return (HashMap<SkillEnum, Integer>) BytesUtil.toObject(nbt.getByteArray(SKILLS_KEY));
		} catch (ClassNotFoundException | IOException e) {
			DevilRpg.LOGGER.error("Error en getSkillsPoints", e);
			return null;
		}
	}

	@Override
	public void setSkillsPoints(HashMap<SkillEnum, Integer> points, PlayerEntity player) {
		try {
			nbt.putByteArray(SKILLS_KEY, BytesUtil.toByteArray(points));
			if (!player.world.isRemote) {
				sendSkillChangesToClient((ServerPlayerEntity) player);
			} else {
				sendSkillChangesToServer();
			}
		} catch (IOException e) {
			DevilRpg.LOGGER.error("Error en setSkillsPoints", e);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public HashMap<SkillEnum, Integer> getMaxSkillsPoints() {
		try {
			return (HashMap<SkillEnum, Integer>) BytesUtil.toObject(nbt.getByteArray(MAX_SKILLS_KEY));
		} catch (ClassNotFoundException | IOException e) {
			DevilRpg.LOGGER.error("Error en getMaxSkillsPoints", e);
			return null;
		}
	}

	@Override
	public void setMaxSkillsPoints(HashMap<SkillEnum, Integer> points, PlayerEntity player) {
		try {
			nbt.putByteArray(MAX_SKILLS_KEY, BytesUtil.toByteArray(points));
			if (!player.world.isRemote) {
				sendSkillChangesToClient((ServerPlayerEntity) player);
			} else {
				sendSkillChangesToServer();
			}
		} catch (IOException e) {
			DevilRpg.LOGGER.error("Error en setMaxSkillsPoints", e);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public HashMap<SkillEnum, Integer> getManaCostPoints() {
		try {
			return (HashMap<SkillEnum, Integer>) BytesUtil
					.toObject(nbt.getByteArray(MANA_COST_KEY));
		} catch (ClassNotFoundException | IOException e) {
			DevilRpg.LOGGER.error("Error en getManaCost", e);
			return null;
		}
	}

	@Override
	public void setManaCostPoints(HashMap<SkillEnum, Integer> points, PlayerEntity player) {
		try {
			nbt.putByteArray(MANA_COST_KEY, BytesUtil.toByteArray(points));
			if (!player.world.isRemote) {
				sendSkillChangesToClient((ServerPlayerEntity) player);
			} else {
				sendSkillChangesToServer();
			}
		} catch (IOException e) {
			DevilRpg.LOGGER.error("Error en setManaCost", e);
		}

	}
	@SuppressWarnings("unchecked")
	@Override
	public HashMap<String, UUID> getAttributeModifiers() {
		try {
			return (HashMap<String, UUID>) BytesUtil
					.toObject(nbt.getByteArray(ATTRIBUTE_MODIFIER_KEY));
		} catch (ClassNotFoundException | IOException e) {
			DevilRpg.LOGGER.error("Error en getAttributeModifiers", e);
			return null;
		}
	}
	
	@Override
	public void setAttributeModifiers(HashMap<String, UUID> modifiers, PlayerEntity player) {
		try {
			nbt.putByteArray(ATTRIBUTE_MODIFIER_KEY, BytesUtil.toByteArray(modifiers));
			if (!player.world.isRemote) {
				sendSkillChangesToClient((ServerPlayerEntity) player);
			} else {
				sendSkillChangesToServer();
			}
		} catch (IOException e) {
			DevilRpg.LOGGER.error("Error en setAttributeModifiers", e);
		}
		
	}

	@Override
	public void triggerAction(ServerPlayerEntity playerIn, PowerEnum triggeredPower) {
		// playerIn.sendMessage(new StringTextComponent("Capability triggerAction:"+
		// triggeredPower.name()+" Player ID: "+playerIn.getEntityId()));
		if (!playerIn.world.isRemote) {
			if (getSkillLevelFromAssociatedPower(triggeredPower) != 0) {
				ISkillContainer poder = getSkill(triggeredPower);
				if (consumeMana(playerIn, poder)) {
					poder.execute(playerIn.world, playerIn);
				} else {
					String message = "Not enough mana.";
					playerIn.sendMessage(new StringTextComponent(message),playerIn.getUniqueID());
				}
			}
		}
	}

	private ISkillContainer getSkill(PowerEnum triggeredPower) {
		SkillEnum skillEnum = getSkillsNameOfPowers().get(triggeredPower);
		return singletonSkillFactory.create(skillEnum);
	}

	private int getSkillLevelFromAssociatedPower(PowerEnum triggeredPower) {
		return getSkillsPoints().get(getSkillsNameOfPowers().get(triggeredPower));
	}

	private boolean consumeMana(ServerPlayerEntity playerIn, ISkillContainer poder) {
		float consumedMana = getManaCostPoints().get(poder.getSkillEnum());
		LazyOptional<IBaseManaCapability> mana = playerIn.getCapability(PlayerManaCapabilityProvider.MANA_CAP, null);
		if (mana.map(x -> x.getMana() - consumedMana >= 0).orElse(false)) {
			// playerIn.sendMessage(new StringTextComponent("Capability Consumed mana:"+
			// consumedMana+" Player ID: "+playerIn.getEntityId()));
			mana.ifPresent(m -> m.setMana(m.getMana() - consumedMana, playerIn));
			return true;
		}
		return false;
	}

	@Override
	public ISkillContainer getLoadedSkill(SkillEnum skillEnum) {
		return singletonSkillFactory.getExistingSkill(skillEnum);
	}

	@Override
	public ISkillContainer create(SkillEnum skillEnum) {
		return singletonSkillFactory.create(skillEnum);
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
		ModNetwork.CHANNEL.sendToServer(new PlayerSkillClientServerHandler(getNBTData()));
	}

	private void sendSkillChangesToClient(ServerPlayerEntity pe) {
		ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> pe),
				new PlayerSkillClientServerHandler(getNBTData()));
	}
}
