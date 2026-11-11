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
        if (fallos > 0) { System.out.println("FOOD_HUNGER_POLICY_TEST: FAIL (" + fallos + ")"); System.exit(1); }
        System.out.println("FOOD_HUNGER_POLICY_TEST: OK");
    }
    private static void check(boolean ok, String name) { if (!ok) { fallos++; System.out.println("  FALLO: " + name); } }
}
