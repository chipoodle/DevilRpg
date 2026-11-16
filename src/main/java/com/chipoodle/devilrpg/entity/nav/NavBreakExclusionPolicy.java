package com.chipoodle.devilrpg.entity.nav;

import net.minecraft.core.BlockPos;
import java.util.LinkedHashMap;

/** Bounded memory of failed BREAK stand->target edges, invalidated by relevant terrain changes. */
public final class NavBreakExclusionPolicy {
    public static final int MAX_ENTRIES = 32;
    private final LinkedHashMap<String, Long> rejected = new LinkedHashMap<>();

    private static String key(BlockPos stand, BlockPos target) {
        return stand.getX() + "," + stand.getY() + "," + stand.getZ() + "->"
                + target.getX() + "," + target.getY() + "," + target.getZ();
    }

    public void exclude(BlockPos stand, BlockPos target, long fingerprint) {
        String key = key(stand, target);
        rejected.remove(key);
        rejected.put(key, fingerprint);
        while (rejected.size() > MAX_ENTRIES) rejected.remove(rejected.keySet().iterator().next());
    }

    public boolean excluded(BlockPos stand, BlockPos target, long fingerprint) {
        String key = key(stand, target);
        Long previous = rejected.get(key);
        if (previous == null) return false;
        if (previous.longValue() != fingerprint) {
            rejected.remove(key);
            return false;
        }
        return true;
    }

    public void resolve(BlockPos stand, BlockPos target) { rejected.remove(key(stand, target)); }
    public void clear() { rejected.clear(); }
    public int size() { return rejected.size(); }
}
