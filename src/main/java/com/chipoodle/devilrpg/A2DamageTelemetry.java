package com.chipoodle.devilrpg;

import com.chipoodle.devilrpg.entity.AggressiveZombieEntity;
import com.chipoodle.devilrpg.entity.nav.NavTrace;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/** Opt-in A2 damage/death telemetry. Never changes damage, health or death behavior. */
public final class A2DamageTelemetry {
    private A2DamageTelemetry() {
    }

    public static void onDamage(final LivingDamageEvent.Post event) {
        if (!NavTrace.on() || !(event.getEntity() instanceof AggressiveZombieEntity mob)) {
            return;
        }
        var source = event.getSource();
        DevilRpg.LOGGER.info("[A2_DAMAGE] uuid={} entityId={} tick={} pos={} amount={} original={} "
                        + "health={} source={} direct={} causing={}",
                mob.getUUID(), mob.getId(), mob.level().getGameTime(), mob.blockPosition().toShortString(),
                event.getNewDamage(), event.getOriginalDamage(), mob.getHealth(),
                source.typeHolder().unwrapKey().map(k -> k.location().toString()).orElse("-"),
                source.getDirectEntity() == null ? "-" : source.getDirectEntity().getUUID(),
                source.getEntity() == null ? "-" : source.getEntity().getUUID());
        mob.reaccionarADanoA2();
    }

    public static void onDeath(final LivingDeathEvent event) {
        if (!NavTrace.on() || !(event.getEntity() instanceof AggressiveZombieEntity mob)) {
            return;
        }
        var source = event.getSource();
        DevilRpg.LOGGER.info("[A2_DEATH] uuid={} entityId={} tick={} pos={} health={} source={} direct={} causing={}",
                mob.getUUID(), mob.getId(), mob.level().getGameTime(), mob.blockPosition().toShortString(),
                mob.getHealth(),
                source.typeHolder().unwrapKey().map(k -> k.location().toString()).orElse("-"),
                source.getDirectEntity() == null ? "-" : source.getDirectEntity().getUUID(),
                source.getEntity() == null ? "-" : source.getEntity().getUUID());
    }
}
