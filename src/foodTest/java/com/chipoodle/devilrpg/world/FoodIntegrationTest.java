package com.chipoodle.devilrpg.world;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Minecraft-aware integration tests: real container pantry and real VillageSavedData NBT. */
public final class FoodIntegrationTest {
    private static int fallos;

    public static void main(String[] args) {
        net.neoforged.fml.loading.LoadingModList.of(
                java.util.List.of(), java.util.List.of(), java.util.List.of(),
                java.util.List.of(), java.util.Map.of());
        net.minecraft.SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        RegistryAccess.Frozen registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);

        pantryPhysical();
        pantryCreditPersistence(registries);
        if (fallos > 0) { System.out.println("FOOD_INTEGRATION_TEST: FAIL (" + fallos + ")"); System.exit(1); }
        System.out.println("FOOD_INTEGRATION_TEST: OK");
    }

    private static void pantryPhysical() {
        SimpleContainer lleno = new SimpleContainer(9);
        lleno.setItem(0, new ItemStack(Items.BREAD, 16));
        lleno.setItem(1, new ItemStack(Items.CARROT, 32));
        int bread0 = lleno.countItem(Items.BREAD);
        int carrot0 = lleno.countItem(Items.CARROT);
        int points0 = bread0 * VillagePantry.FOOD_PER_BREAD
                + carrot0 * VillagePantry.FOOD_PER_VEGETABLE;
        int got = VillagePantry.sacarComida(lleno, 10, 1);
        int bread1 = lleno.countItem(Items.BREAD);
        int carrot1 = lleno.countItem(Items.CARROT);
        int points1 = bread1 * VillagePantry.FOOD_PER_BREAD
                + carrot1 * VillagePantry.FOOD_PER_VEGETABLE;
        check(got > 0 && got <= 10, "retirada real positiva");
        check(points0 - points1 == got, "conservacion puntos retirados");

        SimpleContainer vacio = new SimpleContainer(9);
        check(VillagePantry.sacarComida(vacio, 5, 1) == 0, "despensa vacia");
        check(VillagePantry.sacarComida(new SimpleContainer(9), 0, 1) == 0, "peticion cero");

        SimpleContainer escaso = new SimpleContainer(9);
        escaso.setItem(0, new ItemStack(Items.BREAD, 2));
        int gotEscaso = VillagePantry.sacarComida(escaso, 99, 1);
        check(gotEscaso >= 0, "stock insuficiente no rompe");

        SimpleContainer reintento = new SimpleContainer(9);
        reintento.setItem(0, new ItemStack(Items.BREAD, 16));
        int a = VillagePantry.sacarComida(reintento, 4, 1);
        int b = VillagePantry.sacarComida(reintento, 4, 1);
        check(a > 0 && b > 0, "retiradas independientes");
    }

    private static void pantryCreditPersistence(RegistryAccess.Frozen registries) {
        VillageSavedData data = VillageSavedData.load(new CompoundTag(), registries);
        check(data.getPantryCredit(0) == 0, "credito vacio");
        data.setPantryCredit(0, 3);
        data.setPantryCredit(1, 7);
        CompoundTag saved = data.save(new CompoundTag(), registries);
        VillageSavedData reloaded = VillageSavedData.load(saved, registries);
        check(reloaded.getPantryCredit(0) == 3, "credito 0 persiste");
        check(reloaded.getPantryCredit(1) == 7, "credito 1 persiste");
        reloaded.setPantryCredit(0, 1);
        CompoundTag second = reloaded.save(new CompoundTag(), registries);
        VillageSavedData again = VillageSavedData.load(second, registries);
        check(again.getPantryCredit(0) == 1, "segundo ciclo no duplica");
        check(again.getPantryCredit(1) == 7, "segundo ciclo no contamina");

        VillageSavedData empty = VillageSavedData.load(new CompoundTag(), registries);
        check(empty.getPantryCredit(0) == 0 && empty.getPantryCredit(1) == 0,
                "estado vacio aislado");
    }

    private static void check(boolean ok, String name) {
        if (!ok) { fallos++; System.out.println("  FALLO: " + name); }
    }
}
