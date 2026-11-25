package com.chipoodle.devilrpg.world;

import net.minecraft.nbt.CompoundTag;

/** Minecraft-aware NBT test for the production FoodStateNbt codec. */
public final class FoodStateNbtTest {
    private static int fallos;

    public static void main(String[] args) {
        CompoundTag a = new CompoundTag();
        CompoundTag b = new CompoundTag();
        check(FoodStateNbt.hunger(a) == 0, "default hambre");
        check(FoodStateNbt.workForDay(a, 7) == 0, "default trabajo");
        check(!FoodStateNbt.hasLastMeal(a), "default ultima comida");

        FoodStateNbt.setHunger(a, 63);
        FoodStateNbt.setWork(a, 7, 15);
        FoodStateNbt.setLastMeal(a, 1000);
        FoodStateNbt.setHunger(b, 90);
        FoodStateNbt.setWork(b, 7, 40);
        FoodStateNbt.setLastMeal(b, 2000);

        CompoundTag saveA = a.copy();
        CompoundTag saveB = b.copy();
        check(FoodStateNbt.hunger(saveA) == 63, "hambre A");
        check(FoodStateNbt.hunger(saveB) == 90, "hambre B aislada");
        check(FoodStateNbt.workForDay(saveA, 7) == 15, "trabajo A");
        check(FoodStateNbt.workForDay(saveB, 7) == 40, "trabajo B aislado");
        check(FoodStateNbt.workForDay(saveA, 8) == 0, "rollover dia");
        check(FoodStateNbt.lastMeal(saveA) == 1000, "comida A");
        check(FoodStateNbt.lastMeal(saveB) == 2000, "comida B aislada");

        CompoundTag repeated = saveA.copy();
        check(FoodStateNbt.workForDay(repeated, 7) == 15, "repetido no duplica trabajo");
        FoodStateNbt.setHunger(repeated, 120);
        check(FoodStateNbt.hunger(repeated) == 100, "cap hambre");
        FoodStateNbt.resetWork(repeated, 8);
        check(FoodStateNbt.workForDay(repeated, 8) == 0, "reset trabajo");

        if (fallos > 0) { System.out.println("FOOD_STATE_NBT_TEST: FAIL (" + fallos + ")"); System.exit(1); }
        System.out.println("FOOD_STATE_NBT_TEST: OK");
    }

    private static void check(boolean ok, String name) {
        if (!ok) { fallos++; System.out.println("  FALLO: " + name); }
    }
}
