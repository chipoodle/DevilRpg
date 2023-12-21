package com.chipoodle.devilrpg.client.gui.scrollableskillscreen;

import com.google.gson.JsonObject;

import net.minecraft.util.GsonHelper;

public class SkillResourceCost {
	private final Integer maxSkillLevel;
	private final Integer manaCost;
	private final ResourceType resourceType;

	public SkillResourceCost(Integer maxSkillLevel, Integer manaCost, ResourceType resourceType) {
		super();
		this.maxSkillLevel = maxSkillLevel;
		this.manaCost = manaCost;
		this.resourceType = resourceType;
	}

	public Integer getMaxSkillLevel() {
		return maxSkillLevel;
	}

	public Integer getManaCost() {
		return manaCost;
	}

	public ResourceType getResourceType() {return  resourceType;}

	public static SkillResourceCost deserialize(JsonObject object) {
		Integer maxSkillLevel = Integer.parseInt(GsonHelper.getAsString(object, "maxskilllevel"));
		Integer manaCost = Integer.parseInt(GsonHelper.getAsString(object, "manacost"));
		ResourceType resourceType = ResourceType.valueOf(GsonHelper.getAsString(object, "resourcetype").toUpperCase());
		return new SkillResourceCost(maxSkillLevel,manaCost,resourceType);
	}
}
