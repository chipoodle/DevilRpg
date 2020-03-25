package com.chipoodle.devilrpg.init;

import static com.chipoodle.devilrpg.DevilRpg.LOGGER;
import com.chipoodle.devilrpg.capability.mana.IBaseManaCapability;
import com.chipoodle.devilrpg.capability.skill.IBaseSkillCapability;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapabilityFactory;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapabilityStorage;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityFactory;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityStorage;

import net.minecraftforge.common.capabilities.CapabilityManager;



public class ModCapability {
	public static final PlayerManaCapabilityStorage MANA_STORAGE = new PlayerManaCapabilityStorage();
	public static final PlayerManaCapabilityFactory MANA_FACTORY = new PlayerManaCapabilityFactory();
	public static final PlayerSkillCapabilityStorage SKILL_STORAGE = new PlayerSkillCapabilityStorage();
	public static final PlayerSkillCapabilityFactory SKILL_FACTORY = new PlayerSkillCapabilityFactory();
	
	public static void register() {
		CapabilityManager.INSTANCE.register(IBaseManaCapability.class, MANA_STORAGE,MANA_FACTORY);
		CapabilityManager.INSTANCE.register(IBaseSkillCapability.class, SKILL_STORAGE,SKILL_FACTORY);
		LOGGER.info("-----> Capabilities registrados");
		
	}	
}
