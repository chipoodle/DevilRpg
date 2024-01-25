package com.chipoodle.devilrpg.skillsystem;

import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.client.gui.scrollableskillscreen.SkillElement;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.HashMap;

public abstract class AbstractSkillExecutor {
	protected final PlayerSkillCapabilityInterface parentCapability;
	protected final ItemStack icon;
	public AbstractSkillExecutor(PlayerSkillCapabilityInterface parentCapability) {
		this.parentCapability = parentCapability;
		SkillElement skillElementByEnum = parentCapability.getSkillElementByEnum(getSkillEnum());
		icon = skillElementByEnum.getDisplay().getIcon();
	}
	public abstract void execute(Level level, Player playerIn, HashMap<String,String> parameters);

	public boolean arePreconditionsMetBeforeConsumingResource(Player playerIn){return true;}
	public abstract SkillEnum getSkillEnum();

	public boolean isResourceConsumptionBypassed(Player player){return false;}
}
