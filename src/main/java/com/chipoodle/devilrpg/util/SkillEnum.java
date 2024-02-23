/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.chipoodle.devilrpg.util;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 *
 * @author Christian
 */
public enum SkillEnum {
    SUMMON_SOUL_WOLF("SummonSoulWolf","Soul Wolf","summon_soul_wolf",true,false),
    WOLF_FROSTBITE("WolfFrostBite","Wolf Frost Bite","wolffrostbite",true,true),
    WOLF_ICE_ARMOR("WolfIceArmor","Wolf Ice Armor","wolficearmor",true,true),
    SUMMON_SOUL_BEAR("SummonSoulBear","Soul Bear","summon_soul_bear",true,false),
    WAR_BEAR("WarBear","War Bear","war_bear",true,true),
    MOUNT_BEAR("MountBear","Mount Bear","mount_bear",true,true),
    FROSTBALL("Frostball","Frostball","frostball",true,false),
    SUMMON_WISP_HEALTH("SummonWispHealth","Wisp Health","summon_wisp_health",true,false),
    SUMMON_WISP_CURSE("SummonWispCurse","Wisp Curse","summon_wisp_curse",true,false),
    SUMMON_WISP_BOMB("SummonWispBomb","Bomber Wisp","summon_wisp_bomb",true,false),
    SUMMON_WISP_ARCHER("SummonWispArcher","Archer Wisp","summon_wisp_archer",true,false),
    TRANSFORM_WEREWOLF("TransformWereWolf","Werewolf","shapeshift_werewolf",true,false),
    SKIN_ARMOR("SkinArmor","Skin Armor","skin_armor",false,false),
    WEREWOLF_HIT("WereWolfHit","Werewolf Hit","werewolf_hit",false,false),
    WEREWOLF_VITALITY("WereWolfVitality","Werewolf Vitality","werewolf_vitality",false,false),
    MINION_VITALITY("MinionVitality","Minion Vitality","summon_minion_vitality",false,true),
    SOULVINE("Soulvine","Soulvine","soulvine",true,false),
    SOULLICHEN("Soullichen","Soullichen","soullichen",true,false),
    MANA_POOL("ManaPool","Mana Pool","mana_pool",false,false),
    MANA_REGENERATION("ManaRegeneration","Mana Regeneration","mana_regeneration",false,false),
    CHARGE("Charge","Charge","charge",true,false),
    BLOCK("Block","Block","block",true,false),
    KNOCKBACK_RESISTANCE("KnockbackResistance","Knockback Resistance","knockback_resistance",false,false),
    EMPTY("","","",false,false);

    private final String name;
    private final String description;
    private final String jsonName;
    private final boolean activeSkill;
    private final boolean ofMinion;
    SkillEnum(String name,String description,String jsonName,boolean activeSkill, boolean ofMinion) {
        this.name = name;
        this.description = description;
        this.jsonName = jsonName;
        this.activeSkill = activeSkill;
        this.ofMinion = ofMinion;
    }

    public String getName() {
        return name;
    }
    public String getDescription() {
    	return description;
    }

    public boolean isPassive(){
        return !this.activeSkill;

    }

    public boolean isForMinion() {
        return ofMinion;
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
    public static List<SkillEnum> getActiveSkills() {
    	return Arrays.asList(SkillEnum.values()).stream().filter(x->x.activeSkill).collect(Collectors.toList());
    }
    public static List<SkillEnum> getPassiveSkills() {
    	return Arrays.asList(SkillEnum.values()).stream().filter(x->!x.activeSkill).collect(Collectors.toList());
    }
    
    public static List<SkillEnum> getSkillsWithoutEmpty() {
    	return Arrays.asList(SkillEnum.values()).stream().filter(x->!x.equals(EMPTY)).collect(Collectors.toList());
    }
}
