package com.chipoodle.devilrpg.world;

/** Política pura del hambre individual; sin clases de Minecraft para poder probarla. */
public final class FoodHungerPolicy {
    public static final int NORMAL = 60;
    public static final int CRITICAL = 90;
    public static final int MAX = 100;
    public static final int MEAL = 25;
    public static final int EFFECT_TICKS = 3 * 60 * 20;
    public static final int DEATH_TICKS = 10 * 60 * 20;
    public static final int MAX_WORK = 64;

    private FoodHungerPolicy() {}

    public static int clamp(int hunger) { return Math.max(0, Math.min(MAX, hunger)); }
    public static int add(int hunger, int points) { return clamp(clamp(hunger) + Math.max(0, points)); }
    public static int reduce(int hunger, int points) { return clamp(clamp(hunger) - Math.max(0, points)); }
    public static boolean hungry(int hunger) { return clamp(hunger) >= NORMAL; }
    public static boolean critical(int hunger) { return clamp(hunger) >= CRITICAL; }
    public static boolean effects(int criticalElapsed) { return criticalElapsed >= EFFECT_TICKS; }
    public static boolean death(int criticalElapsed) { return criticalElapsed >= DEATH_TICKS; }
    /** Inicia el timestamp una sola vez; devuelve el existente si ya estaba fijado. */
    public static long startCritical(long existing, long now) { return existing == 0L ? now : existing; }

    /** Work credited today: a new day starts at zero, otherwise the stored value is kept. */
    public static int workForDay(long storedDay, long currentDay, int storedWork) {
        return storedDay == currentDay ? clampWork(storedWork) : 0;
    }

    /** Adds completed-work points with the daily cap; repeated callbacks must still be one call per completion. */
    public static int addWork(int current, int points) {
        if (points <= 0) return clampWork(current);
        return clampWork(clampWork(current) + points);
    }

    /** Ration size grows with completed work: 1 base, +1 per 8 work, capped at 4. */
    public static int rationForWork(int work) {
        return 1 + Math.min(3, clampWork(work) / 8);
    }

    private static int clampWork(int work) { return Math.max(0, Math.min(MAX_WORK, work)); }
}
