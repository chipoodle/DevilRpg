package com.chipoodle.devilrpg.world;

import java.util.UUID;

/** Prueba pura del mapa autoritativo de parcelas (sin Minecraft). */
public final class VillageFarmOwnershipTest {

    public static void main(String[] args) {
        UUID alice = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID bob = UUID.fromString("00000000-0000-0000-0000-000000000002");
        String villageA = "80,97,0";
        String villageB = "500,70,500";

        FarmPlotOwners owners = new FarmPlotOwners();
        check(owners.claim(villageA + "#0", alice), "alice claims plot 0");
        check(alice.equals(owners.get(villageA + "#0")), "owner is alice");
        check(!owners.claim(villageA + "#0", bob), "bob cannot steal plot 0");
        check(alice.equals(owners.get(villageA + "#0")), "owner remains alice");

        check(owners.claim(villageA + "#1", bob), "bob claims plot 1");
        owners.release(villageA + "#0", bob);
        check(alice.equals(owners.get(villageA + "#0")), "wrong release does nothing");
        owners.release(villageA + "#1", bob);
        check(owners.get(villageA + "#1") == null, "bob releases plot 1");

        check(owners.claim(villageB + "#0", bob), "same plot index other village independent");
        check(alice.equals(owners.get(villageA + "#0")), "village A owner unchanged");
        check(bob.equals(owners.get(villageB + "#0")), "village B owner independent");

        FarmPlotOwners duplicated = new FarmPlotOwners();
        duplicated.putResolved(villageA + "#0", bob);
        duplicated.putResolved(villageA + "#0", alice);
        check(alice.equals(duplicated.get(villageA + "#0")), "deterministic lower UUID wins");

        System.out.println("VillageFarmOwnershipTest: OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
