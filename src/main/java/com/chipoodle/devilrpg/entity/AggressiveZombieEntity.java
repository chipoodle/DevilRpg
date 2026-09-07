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

    public AggressiveZombieEntity(EntityType<? extends Zombie> type, Level world) {
        super(type, world);
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
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2D, false)); // Ataque cuerpo a cuerpo más rápido
        this.goalSelector.addGoal(3, new FireballAttackGoal(this)); // Lanzar fuego como los Blaze
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true)); // Detectar jugadores
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
                attackTimer = 60; // Tiempo entre ataques
            }
        }
    }

}
