package com.chipoodle.devilrpg.entity;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.spawnprofile.AggressiveZombieSpawnProfile;
import com.chipoodle.devilrpg.spawnprofile.SpawnScaleProfile;
import com.chipoodle.devilrpg.survival.ThreatLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class AggressiveZombieEntity extends Zombie {

    // Los limites de distancia (200..1500) y la base/escala de atributos viven en
    // AggressiveZombieSpawnProfile (SpawnScaleProfile), compartidos con su spawnRule sin acoplamiento.
    private static final SpawnScaleProfile SPAWN_PROFILE = AggressiveZombieSpawnProfile.INSTANCE;

    private double spawnDistance = 0;  // Se guarda al spawnear el zombie
    private double spawnThreat = 1.0;  // Amenaza global al spawnear (combina con la distancia)
    private boolean attributesAdjusted = false; // Para asegurarnos de que solo se ajusta una vez

    /** Centro de la aldea objetivo (para que los zombies del asedio converjan hacia él). */
    private BlockPos villageCenter = null;
    /** Umbral de distancia para que el zombie pueda romper obsidiana (más lejos = más nivel). */
    private static final double OBSIDIAN_THRESHOLD = 700;

    public AggressiveZombieEntity(EntityType<? extends Zombie> type, Level world) {
        super(type, world);
    }

    /** Asigna el centro de la aldea a la que apunta este zombie. */
    public void setVillageCenter(BlockPos center) {
        this.villageCenter = center;
    }

    public BlockPos getVillageCenter() {
        return villageCenter;
    }

    /** ¿Puede este zombie romper obsidiana? (depende de su nivel = distancia de spawn). */
    public boolean canBreakObsidian() {
        return spawnDistance >= OBSIDIAN_THRESHOLD;
    }

    // No es sensible al sol: puede patrullar tanto de dia como de noche sin quemarse.
    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    // Configurar atributos personalizados: se usan las bases del perfil (ahora iguales a un zombie normal).
    public static AttributeSupplier.Builder setAttributes() {
        SpawnScaleProfile p = SPAWN_PROFILE;
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, p.baseHealth())
                .add(Attributes.MOVEMENT_SPEED, p.baseSpeed())
                .add(Attributes.ATTACK_DAMAGE, p.baseDamage())
                .add(Attributes.FOLLOW_RANGE, 64.0D); // Rango de detección base
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new FloatGoal(this)); // Flotar en agua
        // Romper el bloque que le estorba cuando está atascado (pero no atacar casas si puede pasar).
        this.goalSelector.addGoal(2, new BreakBlockGoal(this));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.2D, false)); // Ataque cuerpo a cuerpo más rápido
        this.goalSelector.addGoal(4, new FireballAttackGoal(this)); // Lanzar fuego como los Blaze
        // Si no hay objetivo, marchar hacia el centro de la aldea (para no merodear fuera).
        this.goalSelector.addGoal(5, new MoveToVillageCenterGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true)); // Detectar jugadores
        // También ataca a las invocaciones (minions) del jugador, no solo al jugador.
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, TamableAnimal.class, 10, true, false,
                (target) -> target instanceof ITamableEntity it && it.getOwner() != null));
    }

    public static boolean checkSpawnRules(EntityType<AggressiveZombieEntity> entityType, ServerLevelAccessor world, MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        Player nearestPlayer = world.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), SPAWN_PROFILE.maxDistance(), false);

        if (nearestPlayer == null) {
            return false; // No hay jugadores cercanos, no spawnea
        }

        PlayerAuxiliaryCapabilityInterface playerCapability = IGenericCapability.getUnwrappedPlayerCapability(nearestPlayer, PlayerAuxiliaryCapability.INSTANCE);
        Vec3 playerSpawn = playerCapability.getSpawnPoint();
        if (playerSpawn == null) {
            return false; // No se ha registrado el punto de spawn del jugador
        }

        // Calcular distancia real en bloques desde el spawn del jugador
        double distance = Math.sqrt(pos.distSqr(new BlockPos((int) playerSpawn.x, (int) playerSpawn.y, (int) playerSpawn.z)));

        double probability = calculateSpawnProbability(distance);
        double doubleRandom = random.nextDouble();
        DevilRpg.LOGGER.info("Spawn Check => playerSpawn: {} distance: {} probability: {} random: {} spawn? {}", playerSpawn, distance, probability, doubleRandom, doubleRandom < probability);
        return doubleRandom < probability;
    }

    private static double calculateSpawnProbability(double distance) {
        // Curva de probabilidad unificada (zona protegida -> max), igual que la usada por la spawnRule.
        return SPAWN_PROFILE.probability(distance);
    }

    @Override
    public void aiStep() {
        super.aiStep();

        if (!attributesAdjusted) {
            adjustAttributesBasedOnSpawnDistance();
            attributesAdjusted = true; // Solo se ejecuta una vez
        }
    }

    @Override
    public void setPos(double x, double y, double z) {
        super.setPos(x, y, z);

        if (spawnDistance == 0) { // Solo se calcula al momento del spawn
            Player nearestPlayer = this.level().getNearestPlayer(this, SPAWN_PROFILE.maxDistance());
            if (nearestPlayer != null) {
                PlayerAuxiliaryCapabilityInterface playerCapability = IGenericCapability.getUnwrappedPlayerCapability(nearestPlayer, PlayerAuxiliaryCapability.INSTANCE);
                Vec3 playerSpawn = playerCapability.getAnchorPoint();
                if (playerSpawn == null) {
                    playerSpawn = playerCapability.getSpawnPoint();
                }
                if (playerSpawn != null) {
                    spawnDistance = Math.sqrt(this.blockPosition().distSqr(new BlockPos((int) playerSpawn.x, (int) playerSpawn.y, (int) playerSpawn.z)));
                    spawnThreat = ThreatLevel.current(this.level());
                    DevilRpg.LOGGER.info("Zombie Spawned at: {} | Player Spawn Point: {} | Distance: {} | Threat: {}", this.blockPosition(), playerSpawn, spawnDistance, String.format("%.2f", spawnThreat));
                }
            }
        }
    }

    private void adjustAttributesBasedOnSpawnDistance() {
        if (spawnDistance < SPAWN_PROFILE.minDistance()) {
            return; // Si está en la zona de spawn, no cambia atributos
        }

        // Escalado lineal por distancia (con la zona protegida que se encoge con la amenaza), multiplicado
        // por la fuerza que aporta el tiempo (amenaza) al momento del spawn.
        double scaleFactor = SPAWN_PROFILE.scaleFactor(spawnDistance, spawnThreat)
                * (1.0 + spawnThreat * ThreatLevel.MAX_EXTRA_DIFFICULTY);

        // Aplicar el escalado sobre los valores base del perfil
        Objects.requireNonNull(this.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(SPAWN_PROFILE.baseHealth() * scaleFactor);
        Objects.requireNonNull(this.getAttribute(Attributes.MOVEMENT_SPEED)).setBaseValue(SPAWN_PROFILE.baseSpeed() * scaleFactor);
        Objects.requireNonNull(this.getAttribute(Attributes.ATTACK_DAMAGE)).setBaseValue(SPAWN_PROFILE.baseDamage() * scaleFactor);

        DevilRpg.LOGGER.info("Attributes Scaled => scaleFactor: {} | DISTANCE: {} | MAX_HEALTH: {} | MOVEMENT_SPEED: {} | ATTACK_DAMAGE: {}",
                scaleFactor,
                spawnDistance,
                Objects.requireNonNull(this.getAttribute(Attributes.MAX_HEALTH)).getValue(),
                Objects.requireNonNull(this.getAttribute(Attributes.MOVEMENT_SPEED)).getValue(),
                Objects.requireNonNull(this.getAttribute(Attributes.ATTACK_DAMAGE)).getValue());
    }

    /**
     * La experiencia que suelta el zombie crece con la distancia (y la amenaza), configurable en el perfil.
     * <code>LivingEntity.getExperienceReward(...)</code> usa este valor; aquí se escala según la distancia.
     */
    @Override
    protected int getBaseExperienceReward() {
        return SPAWN_PROFILE.experienceReward(spawnDistance, spawnThreat);
    }

    /**
     * Goal: romper el obstáculo que le estorba SOLO cuando el zombie no consigue progresar hacia su
     * objetivo (aunque se balancee/salte o el enemigo se mueva). Si la distancia al objetivo no mejora
     * durante un tiempo, rompe el bloque que le bloquea el paso. Rompe bloques "débiles" por defecto y,
     * si su nivel lo permite ({@link #canBreakObsidian()}), también piedra/obsidiana.
     */
    static class BreakBlockGoal extends Goal {
        private final AggressiveZombieEntity zombie;
        private BlockPos blockToBreak = null;
        private int noProgressTicks = 0;
        private double bestDist = Double.MAX_VALUE;

        public BreakBlockGoal(AggressiveZombieEntity zombie) {
            this.zombie = zombie;
        }

        @Override
        public boolean canUse() {
            return zombie.getTarget() != null && zombie.getTarget().isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            noProgressTicks = 0;
            bestDist = distToTarget();
        }

        @Override
        public void tick() {
            double dist = distToTarget();
            // Mejoró la distancia? reiniciar el contador de falta de progreso.
            if (dist < bestDist - 0.5D) {
                bestDist = dist;
                noProgressTicks = 0;
            } else {
                noProgressTicks++;
            }

            // Tras un buen rato sin acercarse al objetivo, intentar romper el bloque que estorba.
            if (noProgressTicks >= 100) {
                blockToBreak = blockingBlockAhead();
                if (blockToBreak != null) {
                    breakBlock(blockToBreak);
                    // Tras romper, dar un margen para que el pathfinding se recalcule.
                    noProgressTicks = -60;
                } else {
                    // No hay bloque directo: reiniciar para no quedarse en bucle.
                    noProgressTicks = 0;
                }
            }
        }

        private double distToTarget() {
            Entity target = zombie.getTarget();
            return target == null ? Double.MAX_VALUE : zombie.distanceToSqr(target.position());
        }

        /**
         * Busca el bloque sólido rompible que más probablemente estorba el paso: mira varias posiciones
         * alrededor (a la altura del cuerpo y un bloque arriba, en las 4 direcciones) y devuelve el más
         * cercano al objetivo.
         */
        private BlockPos blockingBlockAhead() {
            Entity target = zombie.getTarget();
            if (target == null) return null;
            BlockPos zPos = zombie.blockPosition();
            BlockPos best = null;
            double bestScore = Double.MAX_VALUE;
            int sx = Integer.signum((int) Math.floor(target.getX()) - zPos.getX());
            int sz = Integer.signum((int) Math.floor(target.getZ()) - zPos.getZ());
            for (int dy = 0; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        // Priorizar la dirección hacia el objetivo y adyacentes.
                        if (dx == 0 && dz == 0) continue;
                        BlockPos candidate = new BlockPos(zPos.getX() + dx, zPos.getY() + dy, zPos.getZ() + dz);
                        BlockState bs = zombie.level().getBlockState(candidate);
                        if (!bs.isAir() && bs.isSolid() && canBreak(bs)) {
                            double score = Math.abs(dx - sx) + Math.abs(dz - sz) + dy * 0.5D;
                            if (score < bestScore) {
                                bestScore = score;
                                best = candidate;
                            }
                        }
                    }
                }
            }
            return best;
        }

        private boolean canBreak(BlockState state) {
            Block b = state.getBlock();
            if (b == Blocks.BEDROCK || b == Blocks.WATER || b == Blocks.LAVA || b == Blocks.AIR) {
                return false;
            }
            if (b == Blocks.OBSIDIAN || b == Blocks.CRYING_OBSIDIAN) {
                return zombie.canBreakObsidian();
            }
            return true; // madera, tierra, grava, arena, lana...
        }

        private void breakBlock(BlockPos pos) {
            BlockState bs = zombie.level().getBlockState(pos);
            Block b = bs.getBlock();
            if ((b == Blocks.OBSIDIAN || b == Blocks.CRYING_OBSIDIAN) && !zombie.canBreakObsidian()) {
                return;
            }
            zombie.level().destroyBlock(pos, true);
        }
    }

    /**
     * Goal: si el zombie no tiene objetivo de ataque y conoce el centro de la aldea, marcha hacia él.
     * Al llegar (o encontrar un objetivo) el comportamiento normal (ataque/patrulla) retoma el control.
     */
    static class MoveToVillageCenterGoal extends Goal {
        private final AggressiveZombieEntity zombie;
        private static final double ARRIVE_DIST = 6.0D * 6.0D;

        public MoveToVillageCenterGoal(AggressiveZombieEntity zombie) {
            this.zombie = zombie;
        }

        @Override
        public boolean canUse() {
            if (zombie.getTarget() != null) {
                return false; // ya tiene a quién atacar
            }
            BlockPos center = zombie.getVillageCenter();
            return center != null && zombie.distanceToSqr(center.getX(), center.getY(), center.getZ()) > ARRIVE_DIST;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            BlockPos center = zombie.getVillageCenter();
            if (center != null) {
                zombie.getNavigation().moveTo(center.getX(), center.getY(), center.getZ(), 1.0D);
            }
        }
    }

    // Clase interna para el comportamiento de lanzar fuego
    static class FireballAttackGoal extends Goal {
        private final AggressiveZombieEntity zombie;
        private int attackTimer;

        public FireballAttackGoal(AggressiveZombieEntity zombie) {
            this.zombie = zombie;
        }

        @Override
        public boolean canUse() {
            return zombie.getTarget() != null && zombie.getTarget().isAlive();
        }

        @Override
        public void tick() {
            Entity target = zombie.getTarget();
            if (target == null) return;

            if (--attackTimer <= 0) {
                double d0 = 4.0D; // Distancia máxima para lanzar fuego
                double d1 = target.getX() - zombie.getX();
                double d2 = target.getY(0.5D) - zombie.getY(0.5D);
                double d3 = target.getZ() - zombie.getZ();
                SmallFireball fireball = new SmallFireball(zombie.level(), zombie, new Vec3(d1 + zombie.getRandom().nextGaussian() * d0, d2, d3 + zombie.getRandom().nextGaussian() * d0));
                fireball.setPos(fireball.getX(), zombie.getY(0.5D) + 0.5D, fireball.getZ());
                zombie.level().addFreshEntity(fireball);
                attackTimer = 240; // Tiempo entre ataques
            }
        }
    }

}
