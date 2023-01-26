package com.chipoodle.devilrpg.skillsystem.skillinstance;

import java.util.HashMap;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.skill.IBasePlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityProvider;
import com.chipoodle.devilrpg.entity.IPassiveMinionUpdater;
import com.chipoodle.devilrpg.entity.ITameableEntity;
import com.chipoodle.devilrpg.entity.SoulBearEntity;
import com.chipoodle.devilrpg.entity.SoulWispEntity;
import com.chipoodle.devilrpg.entity.SoulWolfEntity;
import com.chipoodle.devilrpg.util.SkillEnum;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.attributes.Attribute;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;

public class MinionPassiveAttributes  {
	public static final String PASSIVE_MINION_HEALTH = "PASSIVE_MINION_HEALTH";
	public static final String PASSIVE_WAR_BEAR_HEALTH = "PASSIVE_WAR_BEAR_HEALTH";
	private float factor;
	private World worldIn;
	private PlayerEntity playerIn;

	public MinionPassiveAttributes(ITameableEntity entity) {
		DevilRpg.LOGGER.info("||---->MinionPassiveAttributes");
		worldIn = entity.getLevel();
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

	@SuppressWarnings("unchecked")
	private void apply(ITameableEntity entity) {
		DevilRpg.LOGGER.info("||---->MinionPassiveAttributes apply");

		if (!worldIn.isClientSide && playerIn != null) {
			IBasePlayerSkillCapability parentCapability = IGenericCapability.getUnwrappedPlayerCapability(playerIn,
					PlayerSkillCapabilityProvider.SKILL_CAP);
			HashMap<Attribute, AttributeModifier> attributes = new HashMap<>();
			attributes.put(Attributes.MAX_HEALTH,
					new AttributeModifier(PASSIVE_MINION_HEALTH,
							factor * parentCapability.getSkillsPoints().get(SkillEnum.MINION_VITALITY),
							AttributeModifier.Operation.ADDITION));
			IPassiveMinionUpdater<ITameableEntity> minion = (IPassiveMinionUpdater<ITameableEntity>) entity;
			minion.applyPassives(attributes, entity);
		}
	}

	private void applyPassives(SoulBearEntity entity) {
		IBasePlayerSkillCapability parentCapability = IGenericCapability.getUnwrappedPlayerCapability(playerIn,
				PlayerSkillCapabilityProvider.SKILL_CAP);
		Integer warBear = parentCapability.getSkillsPoints().get(SkillEnum.WAR_BEAR);
		Integer mountBear = parentCapability.getSkillsPoints().get(SkillEnum.MOUNT_BEAR);
		DevilRpg.LOGGER.info("||---->MinionPassiveAttributes SoulBearEntity warbear:{} factor: {}", warBear, factor);

		entity.setWarBear(warBear);
		entity.setMountBear(mountBear);

		HashMap<Attribute, AttributeModifier> attributes = new HashMap<>();
		attributes.put(Attributes.MAX_HEALTH, new AttributeModifier(PASSIVE_WAR_BEAR_HEALTH, 3.5D * warBear,
				AttributeModifier.Operation.ADDITION));
		IPassiveMinionUpdater<SoulBearEntity> minion = entity;
		minion.applyPassives(attributes, entity);

	}

	private void applyPassives(SoulWolfEntity entity) {
		DevilRpg.LOGGER.info("||---->MinionPassiveAttributes SoulWolfEntity");
		IBasePlayerSkillCapability parentCapability = IGenericCapability.getUnwrappedPlayerCapability(playerIn,
				PlayerSkillCapabilityProvider.SKILL_CAP);
		Integer frostbite = parentCapability.getSkillsPoints().get(SkillEnum.WOLF_FROSTBITE);
		Integer iceArmor = parentCapability.getSkillsPoints().get(SkillEnum.WOLF_ICE_ARMOR);

		entity.setFrostbite(frostbite);
		entity.setIceArmor(iceArmor);
	}

	private void applyPassives(SoulWispEntity entity) {
		DevilRpg.LOGGER.info("||---->MinionPassiveAttributes SoulWispEntity");

	}

}
