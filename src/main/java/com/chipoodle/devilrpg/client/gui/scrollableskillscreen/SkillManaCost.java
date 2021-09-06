package com.chipoodle.devilrpg.client.gui.scrollableskillscreen;

import com.google.gson.JsonObject;

import net.minecraft.util.JSONUtils;

public class SkillManaCost {
	private final Integer maxSkillLevel;
	private final Integer manaCost;

	public SkillManaCost(Integer maxSkillLevel, Integer manaCost) {
		super();
		this.maxSkillLevel = maxSkillLevel;
		this.manaCost = manaCost;
	}

	public Integer getMaxSkillLevel() {
		return maxSkillLevel;
	}

	public Integer getManaCost() {
		return manaCost;
	}

	public static SkillManaCost deserialize(JsonObject object) {
		Integer maxSkillLevel = Integer.parseInt(JSONUtils.getAsString(object, "maxskilllevel"));
		Integer manaCost = Integer.parseInt(JSONUtils.getAsString(object, "manacost"));
		return new SkillManaCost(maxSkillLevel,manaCost);
	}
}