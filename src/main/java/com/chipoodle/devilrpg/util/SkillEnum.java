/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.chipoodle.devilrpg.util;

import java.util.Arrays;

/**
 *
 * @author Dra. Magdalena
 */
public enum SkillEnum {
    SUMMON_SOUL_WOLF("SummonSoulWolf","Soul Wolf","summon_soul_wolf"),
    SUMMON_SOUL_BEAR("SummonSoulBear","Soul Bear","summon_soul_bear"),
    FIREBALL("Fireball","Fireball","frostball"),
    SUMMON_WISP_HEALTH("SummonWispHealth","Wisp Health","summon_wisp_health"),
    SUMMON_WISP_SPEED("SummonWispSpeed","Wisp Speed","summon_wisp_speed"),
    TRANSFORM_WEREWOLF("TransformWereWolf","Werewolf","shapeshift_werewolf"),
	EMPTY("","","");
	
    private final String name;
    private final String description;
    private final String jsonName;

    SkillEnum(String name,String description,String jsonName) {
        this.name = name;
        this.description = description;
        this.jsonName = jsonName;
    }

    public String getName() {
        return name;
    }
    public String getDescription() {
    	return description;
    }
    
    public static SkillEnum getByName(String name) {
    	return Arrays.asList(SkillEnum.values()).stream().filter(x->x.name.equals(name)).findAny().orElse(EMPTY);
    }
    public static SkillEnum getByJsonName(String jsonName) {
    	return Arrays.asList(SkillEnum.values()).stream().filter(x->x.jsonName.equals(jsonName)).findAny().orElse(EMPTY);
    }
    public static SkillEnum getByDescription(String description) {
    	return Arrays.asList(SkillEnum.values()).stream().filter(x->x.description.equals(description)).findAny().orElse(EMPTY);
    }

}
