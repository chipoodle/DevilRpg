package com.chipoodle.devilrpg.entity.goal;

/** Villager flee speed policy: only the panic/flee movement uses this, never normal work speed. */
public final class VillagerFleeSpeedPolicy {
    public static final float ORIGINAL_SPEED = 1.35F;
    public static final float FLEE_SPEED = ORIGINAL_SPEED * 0.70F;

    private VillagerFleeSpeedPolicy() {
    }
}
