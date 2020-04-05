package com.chipoodle.devilrpg.capability.skill;

import java.util.HashMap;
import java.util.Hashtable;

import com.chipoodle.devilrpg.skillsystem.ISkillContainer;
import com.chipoodle.devilrpg.skillsystem.SingletonSkillFactory;
import com.chipoodle.devilrpg.util.PowerEnum;
import com.chipoodle.devilrpg.util.SkillEnum;

import net.minecraft.entity.player.PlayerEntity;

public class ServerSkillTranslator {
	
	private PlayerSkillCapability playerSkillCapability;
	private Hashtable<SkillEnum,Float> manaCost;
	private SingletonSkillFactory singletonSkillFactory = new SingletonSkillFactory();

    public ServerSkillTranslator(PlayerSkillCapability playerSkillCapability) {
    	this.playerSkillCapability = playerSkillCapability; 
    	manaCost = new Hashtable<>();
    	manaCost.put(SkillEnum.SUMMON_SOUL_WOLF, 20f);
	}

	public ISkillContainer getSkill(PowerEnum triggeredPower) {
		SkillEnum skillEnum =  playerSkillCapability.getSkillsNameOfPowers().get(triggeredPower);
		return singletonSkillFactory.create(skillEnum);
    }

    public float getManaCost(SkillEnum power, PlayerEntity playerIn) {
    	return manaCost.getOrDefault(power, 50f);
    }

    public int getSkillLevelFromUserCapability(PlayerEntity playerIn, PowerEnum triggeredPower) {
    	HashMap<PowerEnum, SkillEnum> powers = playerSkillCapability.getSkillsNameOfPowers();
        HashMap<SkillEnum, Integer> skills = playerSkillCapability.getSkillsPoints();
        return skills.get(powers.get(triggeredPower));
    }
}
