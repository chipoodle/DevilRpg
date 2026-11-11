package com.chipoodle.devilrpg.spawner;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.world.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;

/** Opt-in, success-only spawn provenance logging. Never changes spawn behavior. */
public final class AggressiveSpawnAudit {
    private AggressiveSpawnAudit() {
    }

    public static void success(String source, ServerLevel level, Mob mob, BlockPos pos) {
        if (!Boolean.getBoolean("devilrpg.spawnAudit")) {
            return;
        }
        StringBuilder equipment = new StringBuilder();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            var stack = mob.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                if (!equipment.isEmpty()) equipment.append(',');
                equipment.append(slot).append('=').append(stack.getItem());
            }
        }
        DevilRpg.LOGGER.info("[SpawnAudit] source={} uuid={} type={} pos={} dim={} "
                        + "protectedVillage={} equipment=[{}]",
                source, mob.getUUID(), mob.getType(), pos, level.dimension().location(),
                VillageManager.estaProtegida(level, pos), equipment);
    }
}
