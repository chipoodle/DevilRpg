package com.chipoodle.devilrpg.world;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * C4 · un puesto, un dueño: la política conserva el UUID menor por POI y marca sobrantes a los demás,
 * con orden estable y sin tocar asignaciones válidas.
 */
public final class VillageJobSiteOwnershipPolicyTest {
    public static void main(String[] args) {
        UUID a = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID b = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID c = UUID.fromString("00000000-0000-0000-0000-000000000003");

        check("sin registros no hay sobrantes", sobrantes(List.of()).isEmpty());
        check("un solo duenio no produce sobrantes",
                sobrantes(List.of(new VillageJobSiteOwnershipPolicy.Registro(a, 10L))).isEmpty());
        check("POIs distintos no producen sobrantes",
                sobrantes(List.of(
                        new VillageJobSiteOwnershipPolicy.Registro(a, 10L),
                        new VillageJobSiteOwnershipPolicy.Registro(b, 20L))).isEmpty());

        Set<UUID> dup = sobrantes(List.of(
                new VillageJobSiteOwnershipPolicy.Registro(b, 10L),
                new VillageJobSiteOwnershipPolicy.Registro(a, 10L)));
        check("dos en el mismo POI: sobra el UUID mayor", dup.size() == 1 && dup.contains(b));
        check("el UUID menor conserva el puesto", !dup.contains(a));

        Set<UUID> tres = sobrantes(List.of(
                new VillageJobSiteOwnershipPolicy.Registro(c, 7L),
                new VillageJobSiteOwnershipPolicy.Registro(b, 7L),
                new VillageJobSiteOwnershipPolicy.Registro(a, 7L)));
        check("tres en el mismo POI: sobran dos", tres.size() == 2 && tres.contains(b) && tres.contains(c));

        Set<UUID> mixto = sobrantes(List.of(
                new VillageJobSiteOwnershipPolicy.Registro(a, 1L),
                new VillageJobSiteOwnershipPolicy.Registro(b, 1L),
                new VillageJobSiteOwnershipPolicy.Registro(c, 2L),
                new VillageJobSiteOwnershipPolicy.Registro(a, 2L)));
        check("sobrantes solo donde hay colision (b en 1, c en 2)",
                mixto.size() == 2 && mixto.contains(b) && mixto.contains(c));

        Set<UUID> repetidoMismo = sobrantes(List.of(
                new VillageJobSiteOwnershipPolicy.Registro(a, 5L),
                new VillageJobSiteOwnershipPolicy.Registro(a, 5L)));
        check("registro repetido del mismo UUID no lo marca", repetidoMismo.isEmpty());

        check("orden de entrada irrelevante",
                sobrantes(List.of(
                        new VillageJobSiteOwnershipPolicy.Registro(a, 9L),
                        new VillageJobSiteOwnershipPolicy.Registro(b, 9L))).equals(
                        sobrantes(List.of(
                                new VillageJobSiteOwnershipPolicy.Registro(b, 9L),
                                new VillageJobSiteOwnershipPolicy.Registro(a, 9L)))));

        System.out.println("VILLAGE_JOB_SITE_OWNERSHIP_POLICY_TEST: OK (" + assertions + " assertions)");
    }

    private static Set<UUID> sobrantes(List<VillageJobSiteOwnershipPolicy.Registro> registros) {
        return VillageJobSiteOwnershipPolicy.sobrantes(registros);
    }

    private static int assertions;

    private static void check(String name, boolean ok) {
        assertions++;
        if (!ok) {
            throw new AssertionError(name);
        }
    }
}
