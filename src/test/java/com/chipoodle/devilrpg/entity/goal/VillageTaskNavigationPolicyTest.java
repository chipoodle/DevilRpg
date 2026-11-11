package com.chipoodle.devilrpg.entity.goal;

/**
 * Prueba pura de la política semántica de propiedad de navegación.
 * Se ejecuta desde la tarea Gradle policyTest (sin Minecraft).
 */
public final class VillageTaskNavigationPolicyTest {

    private VillageTaskNavigationPolicyTest() {
    }

    public static void main(String[] args) {
        check(VillageTaskNavigationPolicy.esTareaAgricola("farm:0"), "farm:0 agrícola");
        check(VillageTaskNavigationPolicy.esTareaAgricola("farm:2"), "farm:2 agrícola");
        check(!VillageTaskNavigationPolicy.usaNavegacionPropia("farm:0"), "farm no usa nav propia");
        check(!VillageTaskNavigationPolicy.usaNavegacionPropia("farm:2"), "farm no usa nav propia");
        check(VillageTaskNavigationPolicy.duenioDe("farm:1")
                == VillageTaskNavigationPolicy.Owner.PROFESSION_FALLBACK, "farm vuelve al fallback");

        check(VillageTaskNavigationPolicy.usaNavegacionPropia("recado:123"), "recado simple elegible");
        check(VillageTaskNavigationPolicy.duenioDe("recado:123")
                == VillageTaskNavigationPolicy.Owner.CUSTOM_NAVIGATION, "recado simple usa nav");
        check(VillageTaskNavigationPolicy.usaNavegacionPropia("delivery:warehouse"), "delivery elegible");
        check(VillageTaskNavigationPolicy.esTareaDePosicionDeTrabajo("mine:123"), "mine es posición de trabajo");
        check(!VillageTaskNavigationPolicy.usaNavegacionPropia("mine:123"), "mine no usa nav propia");
        check(VillageTaskNavigationPolicy.duenioDe("mine:123")
                == VillageTaskNavigationPolicy.Owner.PROFESSION_FALLBACK, "mine vuelve al fallback");
        check(!VillageTaskNavigationPolicy.usaNavegacionPropia(""), "vacío no elegible");
        check(VillageTaskNavigationPolicy.duenioDe(null)
                == VillageTaskNavigationPolicy.Owner.PROFESSION_FALLBACK, "null vuelve al fallback");

        System.out.println("VillageTaskNavigationPolicyTest: OK");
    }

    private static void check(boolean condition, String mensaje) {
        if (!condition) {
            throw new AssertionError(mensaje);
        }
    }
}
