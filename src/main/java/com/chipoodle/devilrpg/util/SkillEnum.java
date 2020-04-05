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
    SUMMON_SOUL_WOLF("SummonSoulWolf","Soul Wolf"),
    SUMMON_SOUL_BEAR("SummonSoulBear","Soul Bear"),
    FIREBALL("Fireball","Fireball"),
    SUMMON_WISP_HEALTH("SummonWispHealth","Wisp Health"),
    SUMMON_WISP_SPEED("SummonWispSpeed","Wisp Speed"),
    TRANSFORM_WEREWOLF("TransformWereWolf","Werewolf");
	
    private final String name;
    private final String description;

    SkillEnum(String name,String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }
    public String getDescription() {
    	return description;
    }
    
    public static SkillEnum getByName(String name) {
    	return Arrays.asList(SkillEnum.values()).stream().filter(x->x.name.equals(name)).findAny().orElse(null);
    }
    public static SkillEnum getByDescription(String description) {
    	return Arrays.asList(SkillEnum.values()).stream().filter(x->x.description.equals(description)).findAny().orElse(null);
    }

}
