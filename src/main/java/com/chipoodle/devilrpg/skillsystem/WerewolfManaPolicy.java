package com.chipoodle.devilrpg.skillsystem;

/**
 * <b>Skills runtime · reversión del hombre lobo por maná.</b>
 * <p>
 * El cliente pedía `TRANSFORM_WEREWOLF` cada 10 ticks cuando el maná llegaba a 0, <b>sin mirar si el
 * jugador estaba transformado</b>. Como `SkillShapeshiftWerewolf` <b>alterna</b>, un humano con 0 de
 * maná podía quedar convertido en hombre lobo por el camino directo (que no consume recursos), y el
 * paquete seguía repitiéndose. La política pura exige:
 * <ul>
 *   <li>estar transformado (solo se revierte al lobo, no se transforma al humano);</li>
 *   <li>maná &le; 0;</li>
 *   <li>un tick múltiplo de 20 (como mucho un aviso cada segundo).</li>
 * </ul>
 * La comprobación de lado/servidor sigue siendo la del handler del paquete.
 */
public final class WerewolfManaPolicy {

    private WerewolfManaPolicy() {
    }

    public static boolean debePedirReversion(boolean transformado, float mana, int tickCount) {
        return transformado && mana <= 0.0F && tickCount % 20 == 0;
    }
}
