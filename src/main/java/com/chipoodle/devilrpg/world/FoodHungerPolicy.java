package com.chipoodle.devilrpg.world;

/** Política pura del hambre individual; sin clases de Minecraft para poder probarla. */
public final class FoodHungerPolicy {
    public static final int NORMAL = 60;
    public static final int CRITICAL = 90;
    public static final int MAX = 100;
    public static final int MEAL = 25;
    public static final int EFFECT_TICKS = 3 * 60 * 20;
    public static final int DEATH_TICKS = 10 * 60 * 20;

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
}
