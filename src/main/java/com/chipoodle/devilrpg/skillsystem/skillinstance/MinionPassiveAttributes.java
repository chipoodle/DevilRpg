package com.chipoodle.devilrpg.skillsystem.skillinstance;

import java.util.HashMap;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.skill.IBaseSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityProvider;
import com.chipoodle.devilrpg.entity.IPassiveMinionUpdater;
import com.chipoodle.devilrpg.entity.SoulBearEntity;
import com.chipoodle.devilrpg.entity.SoulWispEntity;
import com.chipoodle.devilrpg.entity.SoulWolfEntity;
import com.chipoodle.devilrpg.skillsystem.ISkillContainer;
import com.chipoodle.devilrpg.util.SkillEnum;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.attributes.Attribute;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;

public class MinionPassiveAttributes implements ISkillContainer {
	private float factor;
	private World worldIn;
	private PlayerEntity playerIn;

	public MinionPassiveAttributes(TameableEntity entity) {
		DevilRpg.LOGGER.info("||---->MinionPassiveAttributes");
		worldIn = entity.level;
		LivingEntity owner = entity.getOwner();

		if (!(entity instanceof IPassiveMinionUpdater && owner instanceof PlayerEntity))
			return;

		playerIn = (PlayerEntity) owner;

		if (entity instanceof SoulWolfEntity) {
			factor = 0.3333f;
			applyPassives((SoulWolfEntity) entity);
		}
		if (entity instanceof SoulBearEntity) {
			factor = 1.1f;
			applyPassives((SoulBearEntity) entity);
		}
		if (entity instanceof SoulWispEntity) {
			factor = 1.0f;
			applyPassives((SoulWispEntity) entity);
		}
		apply(entity);
	}

	@Override
	public SkillEnum getSkillEnum() {
		return SkillEnum.EMPTY;
	}

	@Override
	public void execute(World worldIn, PlayerEntity playerIn) {

	}

	@SuppressWarnings("unchecked")
	private void apply(TameableEntity entity) {
		DevilRpg.LOGGER.info("||---->MinionPassiveAttributes apply");

		if (!worldIn.isClientSide && playerIn != null) {
			IBaseSkillCapability parentCapability = IGenericCapability.getUnwrappedCapability(playerIn,
					PlayerSkillCapabilityProvider.SKILL_CAP);
			HashMap<Attribute, AttributeModifier> attributes = new HashMap<>();
			attributes.put(Attributes.MAX_HEALTH,
					new AttributeModifier("PASSIVE_MINION_HEALTH",
							factor * parentCapability.getSkillsPoints().get(SkillEnum.MINION_VITALITY),
							AttributeModifier.Operation.ADDITION));
			IPassiveMinionUpdater<TameableEntity> minion = (IPassiveMinionUpdater<TameableEntity>) entity;
			minion.applyPassives(attributes, entity);
		}
	}

	private void applyPassives(SoulBearEntity entity) {
		IBaseSkillCapability parentCapability = IGenericCapability.getUnwrappedCapability(playerIn,
				PlayerSkillCapabilityProvider.SKILL_CAP);
		Integer warBear = parentCapability.getSkillsPoints().get(SkillEnum.WAR_BEAR);
		Integer mountBear = parentCapability.getSkillsPoints().get(SkillEnum.MOUNT_BEAR);
		DevilRpg.LOGGER.info("||---->MinionPassiveAttributes SoulBearEntity warbear:{} factor: {}", warBear, factor);

		entity.setWarBear(warBear);
		entity.setMountBear(mountBear);

		HashMap<Attribute, AttributeModifier> attributes = new HashMap<>();
		attributes.put(Attributes.MAX_HEALTH, new AttributeModifier("PASSIVE_WAR_BEAR_HEALTH", 3.5D * warBear,
				AttributeModifier.Operation.ADDITION));
		IPassiveMinionUpdater<SoulBearEntity> minion = entity;
		minion.applyPassives(attributes, entity);

	}

	private void applyPassives(SoulWolfEntity entity) {
		DevilRpg.LOGGER.info("||---->MinionPassiveAttributes SoulWolfEntity");
		IBaseSkillCapability parentCapability = IGenericCapability.getUnwrappedCapability(playerIn,
				PlayerSkillCapabilityProvider.SKILL_CAP);
		Integer frosbite = parentCapability.getSkillsPoints().get(SkillEnum.WOLF_FROSTBITE);
		Integer iceAmor = parentCapability.getSkillsPoints().get(SkillEnum.WOLF_ICE_ARMOR);

		entity.setFrostbite(frosbite);
		entity.setIceArmor(iceAmor);
	}

	private void applyPassives(SoulWispEntity entity) {
		DevilRpg.LOGGER.info("||---->MinionPassiveAttributes SoulBearEntity");

	}

}
