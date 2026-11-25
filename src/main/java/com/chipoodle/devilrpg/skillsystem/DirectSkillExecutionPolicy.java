package com.chipoodle.devilrpg.skillsystem;

import com.chipoodle.devilrpg.util.SkillEnum;

/**
 * <b>Skills runtime · payload directo.</b>
 * <p>
 * {@code DirectSkillExecutionPayload} ejecuta un {@link SkillEnum} en el servidor <b>sin</b> pasar por
 * {@code triggerAction} (nivel, coste, cooldown, precondiciones). El único uso legítimo es la
 * <b>reversión del hombre lobo</b> cuando el maná llega a 0 desde el cliente; cualquier otro skill
 * debe ir por el payload de teclado y sus comprobaciones. Si no se valida, un cliente modificado puede
 * ejecutar cualquier skill gratis.
 */
public final class DirectSkillExecutionPolicy {

    private DirectSkillExecutionPolicy() {
    }

    /** Elegibilidad de la habilidad; no autoriza por sí sola el payload directo. */
    public static boolean puedeEjecutar(SkillEnum skill, int nivel) {
        return skill == SkillEnum.TRANSFORM_WEREWOLF && nivel > 0;
    }

    /** Autoriza únicamente la reversión con el jugador transformado y el maná agotado. */
    public static boolean puedeEjecutar(SkillEnum skill, int nivel, boolean transformado, float mana) {
        return puedeEjecutar(skill, nivel)
                && transformado
                && Float.isFinite(mana)
                && mana <= 0.0001F;
    }
}
