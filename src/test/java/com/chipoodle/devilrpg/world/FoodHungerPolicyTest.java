package com.chipoodle.devilrpg.world;

public final class FoodHungerPolicyTest {
    private static int fallos;
    public static void main(String[] args) {
        check(FoodHungerPolicy.hungry(59) == false, "59 no elegible");
        check(FoodHungerPolicy.hungry(60) == true, "60 elegible");
        check(FoodHungerPolicy.hungry(61) == true, "61 elegible");
        check(FoodHungerPolicy.critical(89) == false, "89 no critico");
        check(FoodHungerPolicy.critical(90) == true, "90 critico");
        check(FoodHungerPolicy.add(99, 10) == 100, "cap 100");
        check(FoodHungerPolicy.reduce(5, 25) == 0, "no negativo");
        check(FoodHungerPolicy.reduce(60, 25) == 35, "comida 25");
        check(FoodHungerPolicy.reduce(90, 25) == 65, "critico 90->65");
        check(FoodHungerPolicy.effects(3599) == false, "efectos antes 3600");
        check(FoodHungerPolicy.effects(3600) == true, "efectos en 3600");
        check(FoodHungerPolicy.death(11999) == false, "muerte antes 12000");
        check(FoodHungerPolicy.death(12000) == true, "muerte en 12000");
        check(FoodHungerPolicy.startCritical(0, 500) == 500, "inicio timestamp");
        check(FoodHungerPolicy.startCritical(500, 900) == 500, "timestamp solo una vez");
        check(FoodHungerPolicy.workForDay(3, 3, 9) == 9, "trabajo mismo dia");
        check(FoodHungerPolicy.workForDay(3, 4, 9) == 0, "trabajo reinicia al dia");
        check(FoodHungerPolicy.addWork(0, 7) == 7, "suma trabajo");
        check(FoodHungerPolicy.addWork(7, 0) == 7, "cero no suma");
        check(FoodHungerPolicy.addWork(63, 5) == 64, "tope trabajo");
        check(FoodHungerPolicy.rationForWork(0) == 1, "racion sin trabajo");
        check(FoodHungerPolicy.rationForWork(7) == 1, "racion bajo");
        check(FoodHungerPolicy.rationForWork(8) == 2, "racion umbral 8");
        check(FoodHungerPolicy.rationForWork(16) == 3, "racion 16");
        check(FoodHungerPolicy.rationForWork(24) == 4, "racion 24");
        check(FoodHungerPolicy.rationForWork(64) == 4, "racion tope");
        if (fallos > 0) { System.out.println("FOOD_HUNGER_POLICY_TEST: FAIL (" + fallos + ")"); System.exit(1); }
        System.out.println("FOOD_HUNGER_POLICY_TEST: OK");
    }
    private static void check(boolean ok, String name) { if (!ok) { fallos++; System.out.println("  FALLO: " + name); } }
}
