package com.chipoodle.devilrpg.world;

/** C4 · la ventana de gracia de carga bloquea el repoblamiento solo durante sus ticks iniciales. */
public final class VillagePopulationLoadPolicyTest {
    public static void main(String[] args) {
        check("sin ventana no bloquea", VillagePopulationLoadPolicy.cargaAsentada(0L, 0L, 0));
        check("sin primer latido no bloquea", VillagePopulationLoadPolicy.cargaAsentada(500L, -1L, 240));
        check("justo antes de la gracia bloquea", !VillagePopulationLoadPolicy.cargaAsentada(239L, 0L, 240));
        check("en la gracia exacta permite", VillagePopulationLoadPolicy.cargaAsentada(240L, 0L, 240));
        check("despues de la gracia permite", VillagePopulationLoadPolicy.cargaAsentada(1000L, 100L, 240));
        check("gracia desde el primer latido observado", !VillagePopulationLoadPolicy.cargaAsentada(300L, 100L, 240));
        check("reloj incoherente no permite", !VillagePopulationLoadPolicy.cargaAsentada(50L, 100L, 240));
        System.out.println("VILLAGE_POPULATION_LOAD_POLICY_TEST: OK (" + assertions + " assertions)");
    }

    private static int assertions;

    private static void check(String name, boolean ok) {
        assertions++;
        if (!ok) {
            throw new AssertionError(name);
        }
    }
}
