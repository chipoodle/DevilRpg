package com.chipoodle.devilrpg.entity.goal;

/**
 * Explicit GoalSelector priority policy for village villagers.
 *
 * <p>Minecraft/NeoForge {@code GoalSelector} sorts goals by ascending priority number and
 * arbitrates conflicting {@link net.minecraft.world.entity.ai.goal.Goal.Flag}s by that order:
 * lower number wins; equal priority is resolved by registration order (the first registered
 * goal that owns the flag runs). Flags are the real arbiter; the number only decides who gets
 * to try first. See {@code docs/autonomous-village/AUTONOMOUS-VILLAGE-MASTER-PLAN.md} D5.</p>
 *
 * <p>This class is the single registration policy. It does not model tree claims or the
 * navigation lease; those are separate ownership mechanisms.</p>
 */
public final class VillageGoalPriority {

    /** Survival: flee. Nothing may outrank it. */
    public static final int EMERGENCY = 0;
    /** Critical need: eating when the villager is starving. Above work, below flee. */
    public static final int CRITICAL = 1;
    /** Flagless utilities (gates, doors, dispatcher) and the legacy navigation controller. */
    public static final int UTILITY = 2;
    /** Defense and construction: guard duty, builder repair when the villager has no profession. */
    public static final int SECURITY = 3;
    /** Ordinary profession work: farm, animals, cook, smith, cleric, fisher, miner, lumberjack. */
    public static final int PROFESSION = 4;
    /** Support work below the profession: worker repair, village collection. */
    public static final int SUPPORT = 5;
    /** Opportunistic pickup: below all real work. */
    public static final int OPPORTUNISTIC = 6;

    private VillageGoalPriority() {
    }
}
