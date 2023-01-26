package com.chipoodle.devilrpg.init;

import static com.chipoodle.devilrpg.DevilRpg.LOGGER;

import com.chipoodle.devilrpg.capability.GenericCapabilityStorage;
import com.chipoodle.devilrpg.capability.auxiliar.IBaseAuxiliarCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliarCapabilityFactory;
import com.chipoodle.devilrpg.capability.experience.IBaseExperienceCapability;
import com.chipoodle.devilrpg.capability.experience.PlayerExperienceCapabilityFactory;
import com.chipoodle.devilrpg.capability.mana.IBaseManaCapability;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapabilityFactory;
import com.chipoodle.devilrpg.capability.player_minion.IBaseMinionCapability;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapabilityFactory;
import com.chipoodle.devilrpg.capability.skill.IBasePlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityFactory;

import com.chipoodle.devilrpg.capability.tamable_minion.IBaseTamableMinionCapability;
import com.chipoodle.devilrpg.capability.tamable_minion.TamableMinionCapabilityFactory;
import net.minecraftforge.common.capabilities.CapabilityManager;

public class ModCapability {
	public static final PlayerManaCapabilityFactory MANA_FACTORY = new PlayerManaCapabilityFactory();
	public static final PlayerSkillCapabilityFactory SKILL_FACTORY = new PlayerSkillCapabilityFactory();
	public static final PlayerExperienceCapabilityFactory EXP_FACTORY = new PlayerExperienceCapabilityFactory();
	public static final PlayerAuxiliarCapabilityFactory AUX_FACTORY = new PlayerAuxiliarCapabilityFactory();
	public static final PlayerMinionCapabilityFactory MINION_FACTORY = new PlayerMinionCapabilityFactory();
	public static final TamableMinionCapabilityFactory TAMABLE_MINION_FACTORY = new TamableMinionCapabilityFactory();

	public static void register() {
		CapabilityManager.INSTANCE.register(IBaseManaCapability.class,
				new GenericCapabilityStorage<IBaseManaCapability>(),
				MANA_FACTORY);
		CapabilityManager.INSTANCE.register(IBasePlayerSkillCapability.class,
				new GenericCapabilityStorage<IBasePlayerSkillCapability>(),
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
		CapabilityManager.INSTANCE.register(IBaseTamableMinionCapability.class,
				new GenericCapabilityStorage<IBaseTamableMinionCapability>(),
				TAMABLE_MINION_FACTORY);
		LOGGER.info("-----> Capabilities registrados");

	}
}
