package com.chipoodle.devilrpg.init;

import static com.chipoodle.devilrpg.DevilRpg.LOGGER;

import com.chipoodle.devilrpg.capability.GenericCapabilityStorage;
import com.chipoodle.devilrpg.capability.auxiliar.IBaseAuxiliarCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliarCapabilityFactory;
import com.chipoodle.devilrpg.capability.experience.IBaseExperienceCapability;
import com.chipoodle.devilrpg.capability.experience.PlayerExperienceCapabilityFactory;
import com.chipoodle.devilrpg.capability.mana.IBaseManaCapability;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapabilityFactory;
import com.chipoodle.devilrpg.capability.minion.IBaseMinionCapability;
import com.chipoodle.devilrpg.capability.minion.PlayerMinionCapabilityFactory;
import com.chipoodle.devilrpg.capability.skill.IBaseSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityFactory;

import net.minecraftforge.common.capabilities.CapabilityManager;

public class ModCapability {
	public static final PlayerManaCapabilityFactory MANA_FACTORY = new PlayerManaCapabilityFactory();
	public static final PlayerSkillCapabilityFactory SKILL_FACTORY = new PlayerSkillCapabilityFactory();
	public static final PlayerExperienceCapabilityFactory EXP_FACTORY = new PlayerExperienceCapabilityFactory();
	public static final PlayerAuxiliarCapabilityFactory AUX_FACTORY = new PlayerAuxiliarCapabilityFactory();
	public static final PlayerMinionCapabilityFactory MINION_FACTORY = new PlayerMinionCapabilityFactory();

	public static void register() {		
		CapabilityManager.INSTANCE.register(IBaseManaCapability.class,
				new GenericCapabilityStorage<IBaseManaCapability>(),
				MANA_FACTORY);
		CapabilityManager.INSTANCE.register(IBaseSkillCapability.class,
				new GenericCapabilityStorage<IBaseSkillCapability>(),
				SKILL_FACTORY);
		CapabilityManager.INSTANCE.register(IBaseExperienceCapability.class,
				new GenericCapabilityStorage<IBaseExperienceCapability>(),
				EXP_FACTORY);
		CapabilityManager.INSTANCE.register(IBaseAuxiliarCapability.class,
				new GenericCapabilityStorage<IBaseAuxiliarCapability>(),
				AUX_FACTORY);
		CapabilityManager.INSTANCE.register(IBaseMinionCapability.class,
				new GenericCapabilityStorage<IBaseMinionCapability>(),
				MINION_FACTORY);
		LOGGER.info("-----> Capabilities registrados");

	}
}
