package com.chipoodle.devilrpg.entity;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.entity.nav.TacticalEscapeBudget;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Recuperación compartida de entidades atrapadas en agua.
 *
 * <p>Este controlador NO es un Goal y NO posee autoridad de navegación. El propietario inyecta
 * el adaptador de movimiento (NavExecutor para zombies o Brain/WALK_TARGET para guardias).
 * Comparte únicamente la detección acotada, búsqueda de orillas y el impulso físico de salida.
 */
public final class WaterEscapeController {
    private static final int STUCK_TICKS_BEFORE_ESCAPE = 8;
    private static final int WATER_DWELL_TICKS = 60;
    private static final int RESELECT_STUCK_TICKS = 80;
    private static final int RECALCULATE_TICKS = 200;
    private static final int MAX_RADIUS = 16;
    private static final int JUMP_INTERVAL_TICKS = 6;

    private final TacticalEscapeBudget budget;
    private final Set<BlockPos> attemptedShores = new HashSet<>();
    private boolean tracking;
    private boolean active;
    private boolean exhausted;
    private BlockPos shore;
    private int idleTicks;
    private int waterDwellTicks;
    private int stuckTicks;
    private int escapeTicks;
    private int jumpCooldown;
    private long nextAttemptAt;
    private double lastX;
    private double lastZ;
    private double bestMissionDistance = Double.MAX_VALUE;

    public WaterEscapeController(int maxAttempts) {
        this.budget = new TacticalEscapeBudget(maxAttempts);
    }

    /**
     * Atiende un episodio de agua. Devuelve true solo mientras controla temporalmente el destino.
     * La navegación siempre se delega al callback del propietario.
     */
    public boolean tick(ServerLevel level, LivingEntity entity, BlockPos strategicTarget,
                        Predicate<BlockPos> validLanding, Consumer<BlockPos> requestNavigation) {
        long now = level.getGameTime();
        if (!entity.isInWater()) {
            if (tracking) {
                if (active && entity instanceof Villager villager) {
                    DevilRpg.LOGGER.info("[GuardWaterEscape] guard={} reached dry ground at {} after {} ticks",
                            villager.getUUID(), entity.blockPosition().toShortString(), escapeTicks);
                }
                resetEpisode();
            }
            return false;
        }

        if (!tracking) {
            tracking = true;
            lastX = entity.getX();
            lastZ = entity.getZ();
            bestMissionDistance = missionDistance(entity, strategicTarget);
        }

        updateProgress(entity, strategicTarget);
        if (exhausted) {
            return false;
        }

        if (active) {
            escapeTicks++;
            if (stuckTicks >= RESELECT_STUCK_TICKS || escapeTicks >= RECALCULATE_TICKS) {
                requestShore(level, entity, validLanding, now);
            }
            if (!active || shore == null) {
                return false;
            }
            requestNavigation.accept(shore);
            if (--jumpCooldown <= 0) {
                applyShoreImpulse(entity, shore);
                jumpCooldown = JUMP_INTERVAL_TICKS;
            }
            return true;
        }

        if (now >= nextAttemptAt
                && (idleTicks > STUCK_TICKS_BEFORE_ESCAPE || waterDwellTicks > WATER_DWELL_TICKS)) {
            if (requestShore(level, entity, validLanding, now) && shore != null) {
                requestNavigation.accept(shore);
                applyShoreImpulse(entity, shore);
                jumpCooldown = JUMP_INTERVAL_TICKS;
                return true;
            }
        }
        return false;
    }

    /** Pausa/cancela el episodio cuando una prioridad superior (combate/equipo/marcha) toma el control. */
    public void cancel() {
        resetEpisode();
    }

    public boolean isActive() {
        return active;
    }

    private void updateProgress(LivingEntity entity, BlockPos strategicTarget) {
        double dx = entity.getX() - lastX;
        double dz = entity.getZ() - lastZ;
        if (Double.isNaN(lastX) || dx * dx + dz * dz > 0.01D) {
            lastX = entity.getX();
            lastZ = entity.getZ();
            idleTicks = 0;
            stuckTicks = 0;
        } else {
            idleTicks++;
            if (active) {
                stuckTicks++;
            }
        }

        waterDwellTicks++;
        double distance = missionDistance(entity, strategicTarget);
        if (distance < bestMissionDistance - 4.0D) {
            bestMissionDistance = distance;
            waterDwellTicks = 0;
        }
    }

    private boolean requestShore(ServerLevel level, LivingEntity entity, Predicate<BlockPos> validLanding,
                                 long now) {
        active = false;
        shore = null;
        if (!budget.intentar()) {
            exhausted = true;
            if (entity instanceof Villager villager) {
                DevilRpg.LOGGER.warn("[GuardWaterEscape] guard={} exhausted attempts={}/{}; terrain fallback may run",
                        villager.getUUID(), budget.intentos(), budget.maxIntentos());
            }
            return false;
        }

        BlockPos candidate = findNearestShore(entity.blockPosition(), MAX_RADIUS, attemptedShores,
                ground -> level.hasChunkAt(ground)
                        && level.getFluidState(ground).isEmpty()
                        && !level.getBlockState(ground).getCollisionShape(level, ground).isEmpty(),
                validLanding);
        if (candidate == null) {
            nextAttemptAt = now + RECALCULATE_TICKS;
            if (budget.agotado()) {
                exhausted = true;
            }
            if (entity instanceof Villager villager) {
                DevilRpg.LOGGER.warn("[GuardWaterEscape] guard={} no safe shore found attempt={}/{}",
                        villager.getUUID(), budget.intentos(), budget.maxIntentos());
            }
            return false;
        }

        shore = candidate.immutable();
        attemptedShores.add(shore);
        active = true;
        stuckTicks = 0;
        escapeTicks = 0;
        jumpCooldown = 0;
        lastX = entity.getX();
        lastZ = entity.getZ();
        nextAttemptAt = now + RECALCULATE_TICKS;
        if (entity instanceof Villager villager) {
            DevilRpg.LOGGER.info("[GuardWaterEscape] guard={} selected shore={} attempt={}/{}",
                    villager.getUUID(), shore.toShortString(), budget.intentos(), budget.maxIntentos());
        }
        return true;
    }

    private static double missionDistance(LivingEntity entity, BlockPos target) {
        return target == null ? Double.MAX_VALUE
                : entity.distanceToSqr(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D);
    }

    private void resetEpisode() {
        tracking = false;
        active = false;
        exhausted = false;
        shore = null;
        idleTicks = 0;
        waterDwellTicks = 0;
        stuckTicks = 0;
        escapeTicks = 0;
        jumpCooldown = 0;
        nextAttemptAt = 0L;
        lastX = Double.NaN;
        lastZ = 0.0D;
        bestMissionDistance = Double.MAX_VALUE;
        attemptedShores.clear();
        budget.reiniciar();
    }

    /**
     * Búsqueda en espiral compartida. Se inyectan las reglas de suelo y de rellano para que cada
     * entidad conserve sus invariantes físicos y de protección.
     */
    public static BlockPos findNearestShore(BlockPos origin, int radius, Set<BlockPos> attempted,
                                             Predicate<BlockPos> isLand,
                                             Predicate<BlockPos> validLanding) {
        for (int r = 0; r <= radius; r++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (Math.abs(x) != r && Math.abs(z) != r) {
                        continue;
                    }
                    for (int dy = -1; dy <= 0; dy++) {
                        BlockPos ground = new BlockPos(origin.getX() + x, origin.getY() + dy,
                                origin.getZ() + z);
                        if (!isLand.test(ground)) {
                            continue;
                        }
                        BlockPos feet = ground.above();
                        if (!attempted.contains(feet) && validLanding.test(feet)) {
                            return feet.immutable();
                        }
                    }
                }
            }
        }
        return null;
    }

    /** Impulso físico compartido; no teletransporta ni sustituye el adaptador de navegación. */
    public static void applyShoreImpulse(LivingEntity entity, BlockPos shore) {
        BlockPos current = entity.blockPosition();
        int sx = Integer.signum(shore.getX() - current.getX());
        int sz = Integer.signum(shore.getZ() - current.getZ());
        entity.setDeltaMovement(sx * 0.4D, 0.5D, sz * 0.4D);
    }
}
