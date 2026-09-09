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
     * Goal: romper el bloque que le estorba SOLO cuando el zombie está atascado (el pathfinding no
     * progresa y hay un bloque sólido adelante). Rompe bloques "débiles" por defecto y, si su nivel lo
     * permite ({@link #canBreakObsidian()}), también piedra/obsidiana. No rompe si puede pasar normal.
     */
    static class BreakBlockGoal extends Goal {
        private final AggressiveZombieEntity zombie;
        private BlockPos blockToBreak = null;
        private int stuckTicks = 0;
        private double lastX, lastZ;

        public BreakBlockGoal(AggressiveZombieEntity zombie) {
            this.zombie = zombie;
        }

        @Override
        public boolean canUse() {
            if (zombie.getTarget() == null || !zombie.getTarget().isAlive()) {
                return false;
            }
            return isBlockedAhead();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            stuckTicks = 0;
            lastX = zombie.getX();
            lastZ = zombie.getZ();
        }

        @Override
        public void tick() {
            // Detectar atasco: si en 60 ticks no se movió, romper el bloque que tiene delante.
            double dx = zombie.getX() - lastX;
            double dz = zombie.getZ() - lastZ;
            if (dx * dx + dz * dz < 0.01D) {
                stuckTicks++;
            } else {
                stuckTicks = 0;
                lastX = zombie.getX();
                lastZ = zombie.getZ();
            }
            if (stuckTicks >= 60) {
                blockToBreak = blockingBlockAhead();
                if (blockToBreak != null) {
                    breakBlock(blockToBreak);
                }
                stuckTicks = 0;
                lastX = zombie.getX();
                lastZ = zombie.getZ();
            } else if (blockToBreak != null) {
                zombie.getNavigation().moveTo(blockToBreak.getX(), blockToBreak.getY(), blockToBreak.getZ(), 1.0D);
            }
        }

        /** ¿Hay un bloque sólido rompible justo delante (a la altura del cuerpo)? */
        private boolean isBlockedAhead() {
            return blockingBlockAhead() != null;
        }

        private BlockPos blockingBlockAhead() {
            Entity target = zombie.getTarget();
            if (target == null) return null;
            int sx = Integer.signum((int) Math.floor(target.getX()) - zombie.blockPosition().getX());
            int sz = Integer.signum((int) Math.floor(target.getZ()) - zombie.blockPosition().getZ());
            BlockPos ahead = new BlockPos(
                    zombie.blockPosition().getX() + sx,
                    zombie.blockPosition().getY(),
                    zombie.blockPosition().getZ() + sz);
            BlockState bs = zombie.level().getBlockState(ahead);
            if (!bs.isAir() && bs.isSolid() && canBreak(bs)) {
                return ahead;
            }
            return null;
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
