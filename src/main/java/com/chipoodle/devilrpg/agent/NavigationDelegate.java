package com.chipoodle.devilrpg.agent;

/** Test seam for the actual gateway. Production uses the legacy VillageManager/PathNavigation. */
@FunctionalInterface
public interface NavigationDelegate {
    void run();
}
