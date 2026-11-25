package com.chipoodle.devilrpg.world;

import net.minecraft.world.entity.MobSpawnType;

/**
 * A5.33 · pure target-position policy tests. These are not a substitute for Minecraft runtime collision,
 * navigation, or block-interaction validation.
 */
public final class VillageTerrainPolicyTest {

    private static int checks;

    private VillageTerrainPolicyTest() {
    }

    public static void main(String[] args) {
        // 1. Active siege inside a generated village: permitted (block-level checks still apply).
        check(true, VillageTerrainPolicy.allowsModification(true, false, false, true, false, false));
        // 2. Saved village with active field inside: denied.
        check(false, VillageTerrainPolicy.allowsModification(true, true, true, false, false, false));
        // 3. Saved village does not deny outside.
        check(true, VillageTerrainPolicy.allowsModification(false, true, true, false, false, false));
        // 4. Uncertain state inside: denied (fail closed).
        check(false, VillageTerrainPolicy.allowsModification(true, false, false, false, true, false));
        // 5. Uncertain state outside: still permitted.
        check(true, VillageTerrainPolicy.allowsModification(false, false, false, false, true, false));
        // 6. Unsaved generated village, no active siege: safe default denies inside.
        check(false, VillageTerrainPolicy.allowsModification(true, false, false, false, false, false));
        // 7. Perimeter wall remains the existing breach exception.
        check(true, VillageTerrainPolicy.allowsModification(true, true, true, false, false, true));
        // 8. Saved+field inside is denied even if a stale siege flag says active.
        check(false, VillageTerrainPolicy.allowsModification(true, true, true, true, false, false));
        // 9. Target-position scoping: inside protected denied, outside permitted in the same call setup.
        check(false, VillageTerrainPolicy.allowsModification(true, true, true, false, false, false));
        check(true, VillageTerrainPolicy.allowsModification(false, true, true, false, false, false));
        // 10. Active siege does not erase a saved+field denial.
        check(false, VillageTerrainPolicy.allowsModification(true, true, true, true, false, false));
        // 11. El sello corta cualquier spawn hostil dentro, no solo una lista cerrada.
        check(true, VillageSpawnGuard.debeCancelar(true, true, MobSpawnType.NATURAL));
        check(true, VillageSpawnGuard.debeCancelar(true, true, MobSpawnType.EVENT));
        check(true, VillageSpawnGuard.debeCancelar(true, true, MobSpawnType.REINFORCEMENT));
        check(true, VillageSpawnGuard.debeCancelar(true, true, MobSpawnType.SPAWNER));
        check(true, VillageSpawnGuard.debeCancelar(true, true, MobSpawnType.STRUCTURE));
        // 12. Lo que crea el jugador o el mod a propósito no se corta.
        check(false, VillageSpawnGuard.debeCancelar(true, true, MobSpawnType.SPAWN_EGG));
        check(false, VillageSpawnGuard.debeCancelar(true, true, MobSpawnType.COMMAND));
        check(false, VillageSpawnGuard.debeCancelar(true, true, MobSpawnType.MOB_SUMMONED));
        // 13. Fuera de aldea, sin monstruo o sin tipo no se corta.
        check(false, VillageSpawnGuard.debeCancelar(true, false, MobSpawnType.NATURAL));
        check(false, VillageSpawnGuard.debeCancelar(false, true, MobSpawnType.NATURAL));
        check(false, VillageSpawnGuard.debeCancelar(true, true, null));
        System.out.println("VILLAGE_TERRAIN_POLICY_TEST: OK (" + checks + " aserciones)");
    }

    private static void check(boolean expected, boolean actual) {
        checks++;
        if (expected != actual) {
            throw new AssertionError("esperado=" + expected + " actual=" + actual);
        }
    }
}
