package com.chipoodle.devilrpg.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** C4 · invariantes del censo: 12 puestos, 4 fundadores, un dueño por puesto, cupos por oficio. */
public final class VillageRosterInvariantsTest {
    private static final Map<String, Integer> CUPO = Map.of("farmer", 3, "fletcher", 1, "cleric", 1);

    public static void main(String[] args) {
        check("censo valido", comprobar(censoValido(), 3, 1).ok());

        List<VillageRosterInvariants.Miembro> dupUuid = new ArrayList<>(censoValido());
        dupUuid.add(new VillageRosterInvariants.Miembro(uuid(1), "farmer", 0L, false));
        check("uuid duplicado", violaciones(comprobar(dupUuid, 3, 1)).contains("uuid_duplicado:" + uuid(1)));

        List<VillageRosterInvariants.Miembro> dupPuesto = new ArrayList<>();
        dupPuesto.add(new VillageRosterInvariants.Miembro(uuid(10), "fletcher", 555L, false));
        dupPuesto.add(new VillageRosterInvariants.Miembro(uuid(11), "cleric", 555L, false));
        dupPuesto.add(new VillageRosterInvariants.Miembro(uuid(12), "farmer", 0L, false));
        check("puesto duplicado", violaciones(comprobar(dupPuesto, 3, 1)).contains("puesto_duplicado:555"));

        List<VillageRosterInvariants.Miembro> oficioExcedido = new ArrayList<>();
        oficioExcedido.add(new VillageRosterInvariants.Miembro(uuid(20), "farmer", 1L, false));
        oficioExcedido.add(new VillageRosterInvariants.Miembro(uuid(21), "farmer", 2L, false));
        oficioExcedido.add(new VillageRosterInvariants.Miembro(uuid(22), "farmer", 3L, false));
        oficioExcedido.add(new VillageRosterInvariants.Miembro(uuid(23), "farmer", 4L, false));
        check("oficio excedido", violaciones(comprobar(oficioExcedido, 4, 1)).contains("oficio_excedido:farmer:4/3"));

        check("profesionales de menos", violaciones(comprobar(censoValido(), 4, 1)).contains("profesionales:3/4"));
        check("profesionales de mas", violaciones(comprobar(censoValido(), 2, 1)).contains("profesionales:3/2"));

        List<VillageRosterInvariants.Miembro> sinFundador = new ArrayList<>(censoValido());
        sinFundador.removeIf(m -> m.fundador());
        check("fundadores de menos", violaciones(comprobar(sinFundador, 3, 1)).contains("fundadores:0/1"));

        List<VillageRosterInvariants.Miembro> profVacia = new ArrayList<>(censoValido());
        profVacia.add(new VillageRosterInvariants.Miembro(uuid(30), "", 0L, false));
        check("profesion vacia", violaciones(comprobar(profVacia, 4, 1)).contains("profesion_vacia:" + uuid(30)));

        List<VillageRosterInvariants.Miembro> fuera = new ArrayList<>(censoValido());
        fuera.add(new VillageRosterInvariants.Miembro(uuid(31), "soldier", 0L, false));
        check("profesion fuera del cupo",
                violaciones(comprobar(fuera, 4, 1)).contains("profesion_fuera_del_cupo:soldier:" + uuid(31)));

        List<VillageRosterInvariants.Miembro> nulo = new ArrayList<>(censoValido());
        nulo.add(null);
        check("miembro nulo", violaciones(comprobar(nulo, 3, 1)).contains("miembro_nulo"));

        check("censo nulo", violaciones(comprobar(null, 3, 1)).contains("censo_nulo"));

        List<VillageRosterInvariants.Miembro> cero = new ArrayList<>(censoValido());
        cero.add(new VillageRosterInvariants.Miembro(uuid(40), "fletcher", 0L, false));
        check("puesto 0 repetido no es duplicado",
                !violaciones(comprobar(cero, 3, 1)).contains("puesto_duplicado:0"));

        System.out.println("VILLAGE_ROSTER_INVARIANTS_TEST: OK (" + assertions + " assertions)");
    }

    private static List<VillageRosterInvariants.Miembro> censoValido() {
        return new ArrayList<>(List.of(
                new VillageRosterInvariants.Miembro(uuid(1), "farmer", 101L, false),
                new VillageRosterInvariants.Miembro(uuid(2), "farmer", 102L, false),
                new VillageRosterInvariants.Miembro(uuid(3), "fletcher", 103L, false),
                new VillageRosterInvariants.Miembro(uuid(90), "soldier", 0L, true)));
    }

    private static UUID uuid(int n) {
        return UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", n));
    }

    private static VillageRosterInvariants.Resultado comprobar(List<VillageRosterInvariants.Miembro> roster,
                                                               int puestos, int fundadores) {
        return VillageRosterInvariants.comprobar(roster, puestos, CUPO, fundadores);
    }

    private static List<String> violaciones(VillageRosterInvariants.Resultado r) {
        return r.violaciones();
    }

    private static int assertions;

    private static void check(String name, boolean ok) {
        assertions++;
        if (!ok) {
            throw new AssertionError(name);
        }
    }
}
