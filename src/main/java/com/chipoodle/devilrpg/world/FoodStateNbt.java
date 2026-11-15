package com.chipoodle.devilrpg.world;

import net.minecraft.nbt.CompoundTag;

/** NBT field codec for the per-villager food/work state (actual persistent entity NBT keys). */
public final class FoodStateNbt {
    public static final String HUNGER = "DevilRpgHambre";
    public static final String WORK_DAY = "DevilRpgDiaTrabajo";
    public static final String WORK_TODAY = "DevilRpgTrabajoHoy";
    public static final String LAST_MEAL = "DevilRpgUltimaComida";

    private FoodStateNbt() {}

    public static int hunger(CompoundTag tag) {
        return FoodHungerPolicy.clamp(tag.getInt(HUNGER));
    }

    public static void setHunger(CompoundTag tag, int value) {
        tag.putInt(HUNGER, FoodHungerPolicy.clamp(value));
    }

    public static int workForDay(CompoundTag tag, long day) {
        return FoodHungerPolicy.workForDay(tag.getLong(WORK_DAY), day, tag.getInt(WORK_TODAY));
    }

    public static void setWork(CompoundTag tag, long day, int work) {
        tag.putLong(WORK_DAY, day);
        tag.putInt(WORK_TODAY, FoodHungerPolicy.addWork(0, work));
    }

    public static void resetWork(CompoundTag tag, long day) {
        tag.putLong(WORK_DAY, day);
        tag.putInt(WORK_TODAY, 0);
    }

    public static boolean hasLastMeal(CompoundTag tag) {
        return tag.contains(LAST_MEAL);
    }

    public static long lastMeal(CompoundTag tag) {
        return tag.getLong(LAST_MEAL);
    }

    public static void setLastMeal(CompoundTag tag, long tick) {
        tag.putLong(LAST_MEAL, tick);
    }
}
