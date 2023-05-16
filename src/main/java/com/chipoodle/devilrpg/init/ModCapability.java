package com.chipoodle.devilrpg.init;

import static com.chipoodle.devilrpg.DevilRpg.LOGGER;

import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityAttacher;

import com.chipoodle.devilrpg.capability.experience.PlayerExperienceCapabilityAttacher;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapabilityAttacher;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapabilityAttacher;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityAttacher;
import com.chipoodle.devilrpg.capability.tamable_minion.TamableMinionCapabilityAttacher;
import com.chipoodle.devilrpg.entity.ITamableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;

public class ModCapability {
	public static void registerForPlayer(AttachCapabilitiesEvent event) {
		PlayerAuxiliaryCapabilityAttacher.attach(event);
		PlayerExperienceCapabilityAttacher.attach(event);
		PlayerManaCapabilityAttacher.attach(event);
		PlayerMinionCapabilityAttacher.attach(event);
		PlayerSkillCapabilityAttacher.attach(event);
		LOGGER.info("-----> Player({}) capabilities registered",((Player)event.getObject()).getUUID());
	}

	public static void registerForTamableEntity(AttachCapabilitiesEvent event) {
		TamableMinionCapabilityAttacher.attach(event);
		LOGGER.info("-----> Minion({}) capabilities registered",((ITamableEntity)event.getObject()).getEntity().getUUID());
	}
}
