package com.chipoodle.devilrpg.skillsystem;

import com.chipoodle.devilrpg.util.SkillEnum;

/**
 * <b>Qué skill puede llevar un jugador en una tecla de poder.</b>
 * <p>
 * Los 33 {@link SkillEnum} incluyen skills activas que son <b>de minion</b> (`ofMinion`): WOLF_FROSTBITE,
 * WOLF_ICE_ARMOR, WAR_BEAR y MOUNT_BEAR. No son poderes del jugador y
 * {@link SingletonSkillExecutorFactory} no tiene ejecutor para ellas: si se arrastran a una tecla y se
 * pulsan, el servidor revienta con “There is no SkillExecutor”. Deben poder comprarse en el árbol (son
 * mejoras del lobo/oso), pero <b>no asignarse a poderes del jugador</b>. Los pasivos tampoco se asignan.
 */
public final class SkillCastPolicy {

    private SkillCastPolicy() {
    }

    public static boolean esAsignableAPoder(SkillEnum skill) {
        return skill != null && skill != SkillEnum.EMPTY && !skill.isPassive() && !skill.isForMinion();
    }
}
