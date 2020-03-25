/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.chipoodle.devilrpg.util;

/**
 *
 * @author Dra. Magdalena
 */
public enum ConstantesSkillName {
    SUMMON_SOUL_WOLF("SummonSoulWolf"),
    SUMMON_SOUL_BEAR("SummonSoulBear"),
    SUMMON_WISP_HEALTH("SummonWispHealth"),
    SUMMON_WISP_SPEED("SummonWispSpeed"),
    FIREBALL("Fireball"),
    TRANSFORM_WEREWOLF("TransformWereWolf");
    private final String name;

    ConstantesSkillName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

}
