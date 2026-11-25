package com.chipoodle.devilrpg.skillsystem;

import com.chipoodle.devilrpg.skillsystem.server.SkillServerPolicy;
import com.chipoodle.devilrpg.util.SkillEnum;

/**
 * Compatibility facade for existing tests/callers.
 *
 * <p>The authoritative direct-execution rule is loaded and evaluated by SkillServerPolicy
 * from root_complete.json. The two-argument method only reports rule eligibility; it does not
 * authorize a network request by itself.
 */
public final class DirectSkillExecutionPolicy {

    private DirectSkillExecutionPolicy() {
    }

    /** Eligibility only; the server handler must use the full state-aware overload. */
    public static boolean puedeEjecutar(SkillEnum skill, int nivel) {
        return nivel > 0 && SkillServerPolicy.hasDirectExecutionRule(skill);
    }

    /** Evaluates the server-configured rule with state obtained from server capabilities. */
    public static boolean puedeEjecutar(SkillEnum skill, int nivel, boolean transformado, float mana) {
        return SkillServerPolicy.canExecuteDirectSkill(skill, nivel, transformado, mana);
    }
}
