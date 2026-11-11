package com.chipoodle.devilrpg.world;

/**
 * A5.33 · Terrain permission scoped to the TARGET POSITION and the village that owns it.
 *
 * <p>Rules:
 * <ul>
 *   <li>Outside every village: permitted (existing safety checks still apply).</li>
 *   <li>Inside a generated village during an active siege: permitted, subject to the existing block rules.</li>
 *   <li>Inside a saved village whose protective field is active: denied.</li>
 *   <li>Inside a generated village with no active siege or confirmed saved field: denied (fail closed).</li>
 *   <li>Uncertain state: denied inside, but never affects positions outside.</li>
 *   <li>The generated perimeter wall remains the existing breach exception.</li>
 * </ul>
 */
public final class VillageTerrainPolicy {

    private VillageTerrainPolicy() {
    }

    public static boolean allowsModification(final boolean insideVillage,
                                             final boolean saved,
                                             final boolean fieldActive,
                                             final boolean siegeActive,
                                             final boolean stateUncertain,
                                             final boolean perimeterWall) {
        if (!insideVillage) {
            return true; // outside: never globally disabled by another village's state
        }
        if (perimeterWall) {
            return true; // the existing breach path keeps the generated wall breakable
        }
        if (stateUncertain) {
            return false; // fail closed only inside the potentially protected village
        }
        if (saved && fieldActive) {
            return false;
        }
        if (siegeActive) {
            return true;
        }
        return false; // generated but no active siege and not confirmed saved: safe default
    }
}
