package com.chipoodle.devilrpg.init;

import static com.chipoodle.devilrpg.DevilRpg.LOGGER;

import com.chipoodle.devilrpg.capability.GenericCapabilityStorage;
import com.chipoodle.devilrpg.capability.experience.IBaseExperienceCapability;
import com.chipoodle.devilrpg.capability.experience.PlayerExperienceCapabilityFactory;
import com.chipoodle.devilrpg.capability.mana.IBaseManaCapability;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapabilityFactory;
import com.chipoodle.devilrpg.capability.skill.IBaseSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityFactory;

import net.minecraftforge.common.capabilities.CapabilityManager;

public class ModCapability {
	// public static final PlayerManaCapabilityStorage MANA_STORAGE = new
	// PlayerManaCapabilityStorage();
	public static final PlayerManaCapabilityFactory MANA_FACTORY = new PlayerManaCapabilityFactory();
	// public static final PlayerSkillCapabilityStorage SKILL_STORAGE = new
	// PlayerSkillCapabilityStorage();
	public static final PlayerSkillCapabilityFactory SKILL_FACTORY = new PlayerSkillCapabilityFactory();
	// public static final PlayerExperienceCapabilityStorage EXP_STORAGE = new
	// PlayerExperienceCapabilityStorage();
	public static final PlayerExperienceCapabilityFactory EXP_FACTORY = new PlayerExperienceCapabilityFactory();

	public static void register() {
		/*
		 * CapabilityManager.INSTANCE.register(IBaseManaCapability.class,
		 * MANA_STORAGE,MANA_FACTORY);
		 * CapabilityManager.INSTANCE.register(IBaseSkillCapability.class,
		 * SKILL_STORAGE,SKILL_FACTORY);
		 * CapabilityManager.INSTANCE.register(IBaseExperienceCapability.class,
		 * EXP_STORAGE,EXP_FACTORY);
		 */
		
		CapabilityManager.INSTANCE.register(IBaseManaCapability.class,
				new GenericCapabilityStorage<IBaseManaCapability>(),
				new PlayerManaCapabilityFactory());
		CapabilityManager.INSTANCE.register(IBaseSkillCapability.class,
				new GenericCapabilityStorage<IBaseSkillCapability>(),
				new PlayerSkillCapabilityFactory());
		CapabilityManager.INSTANCE.register(IBaseExperienceCapability.class,
				new GenericCapabilityStorage<IBaseExperienceCapability>(),
				new PlayerExperienceCapabilityFactory());
		LOGGER.info("-----> Capabilities registrados");

	}
}
