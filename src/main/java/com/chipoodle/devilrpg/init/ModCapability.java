package com.chipoodle.devilrpg.init;

import static com.chipoodle.devilrpg.DevilRpg.LOGGER;

import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityAttacher;

import com.chipoodle.devilrpg.capability.experience.PlayerExperienceCapabilityAttacher;
import com.chipoodle.devilrpg.capability.experience.PlayerExperienceCapabilityInterface;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapabilityAttacher;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapabilityAttacher;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityAttacher;
import com.chipoodle.devilrpg.capability.tamable_minion.TamableMinionCapabilityAttacher;
import net.minecraftforge.event.AttachCapabilitiesEvent;

public class ModCapability {


	public static void register(AttachCapabilitiesEvent event) {
		PlayerAuxiliaryCapabilityAttacher.attach(event);
		PlayerExperienceCapabilityAttacher.attach(event);
		PlayerManaCapabilityAttacher.attach(event);
		PlayerMinionCapabilityAttacher.attach(event);
		PlayerSkillCapabilityAttacher.attach(event);
		TamableMinionCapabilityAttacher.attach(event);
		LOGGER.info("-----> Capabilities done");
	}
}
