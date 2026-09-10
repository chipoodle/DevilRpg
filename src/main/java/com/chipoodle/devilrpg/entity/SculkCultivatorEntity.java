package com.chipoodle.devilrpg.entity;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.spawnprofile.AggressiveZombieSpawnProfile;
import com.chipoodle.devilrpg.spawnprofile.SpawnScaleProfile;
import com.chipoodle.devilrpg.survival.ThreatLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * <b>Cultivador del sculk</b>: un illager (usa el modelo y la textura del Invocador, con tinte escarlata)
 * que aparece SOLO en las guaridas y se dedica a <b>cultivar la infección</b>:
 * <ul>
 *   <li><b>Cría una granja macabra</b>: alimenta a los animales de la guarida para que se apareen.</li>
 *   <li><b>Sacrifica</b> parte del ganado sobre el sculk cuando ya hay bastantes, alimentando al
 *       catalizador (que expande la infección).</li>
 *   <li><b>Extrae sculk</b> del terreno ya infectado y lo condensa en <b>catalizadores nuevos</b>, que
 *       coloca en los bordes del sculk para que la infección siga creciendo.</li>
 * </ul>
 * Comparte las <b>mismas reglas de escalado</b> que el {@link AggressiveZombieEntity} (perfil por
 * distancia + amenaza, y XP escalada), replicadas aquí porque hereda de {@code AbstractIllager}.
 */
public class SculkCultivatorEntity extends AbstractIllager {

    private static final SpawnScaleProfile SPAWN_PROFILE = AggressiveZombieSpawnProfile.INSTANCE;

    /** Radio en el que el cultivador trabaja (respecto al núcleo de su guarida). */
    private static final int WORK_RADIUS = 24;
    /** Bloques de sculk necesarios para "condensar" un catalizador nuevo. */
    private static final int SCULK_PER_CATALYST = 24;
    /** Máximo de catalizadores que mantiene por guarida. */
    private static final int MAX_CATALYSTS = 6;
    /** Animales en el corral a partir de los cuales empieza a sacrificar. */
    private static final int SACRIFICE_THRESHOLD = 5;

    private double spawnDistance = 0;
    private double spawnThreat = 1.0;
    private boolean attributesAdjusted = false;

    /** "Hogar" (núcleo de su guarida): patrulla un radio alrededor. */
    private BlockPos homePos = null;
    private int homeRadius = 0;

    public SculkCultivatorEntity(EntityType<? extends AbstractIllager> type, Level level) {
        super(type, level);
    }

    /** Mismas reglas (perfil y escalado) que el zombie agresivo. */
    public static AttributeSupplier.Builder setAttributes() {
        SpawnScaleProfile p = SPAWN_PROFILE;
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, p.baseHealth())
                .add(Attributes.MOVEMENT_SPEED, p.baseSpeed())
                .add(Attributes.ATTACK_DAMAGE, p.baseDamage())
                .add(Attributes.FOLLOW_RANGE, 64.0D);
    }

    /** No se une a los raids vanilla: solo sirve a su guarida. */
    @Override
    public boolean canJoinRaid() {
        return false;
    }

    /** Obligatorio de {@code Raider}, pero nunca hay raid propia: no aplica ninguna mejora. */
    @Override
    public void applyRaidBuffs(ServerLevel level, int wave, boolean isLeader) {
        // Sin raids: no se aplica nada.
    }

    /** Obligatorio de {@code Raider}: sonido de celebración (se reutiliza el del invocador). */
    @Override
    public SoundEvent getCelebrateSound() {
        return SoundEvents.EVOKER_CELEBRATE;
    }

    @Override
    public IllagerArmPose getArmPose() {
        if (getTarget() != null) {
            return IllagerArmPose.ATTACKING;
        }
        // Sin objetivo está "obrando": brazos al frente como el invocador.
        return IllagerArmPose.SPELLCASTING;
    }

    @Override
    protected void registerGoals() {
        // A propósito NO se llama a super.registerGoals(): Raider añade los goals de raid vanilla
        // (bandera de líder, PathfindToRaid, celebración, moverse por aldeas) que aquí no aplican.
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // Tareas de cultivador (su "trabajo").
        this.goalSelector.addGoal(2, new SacrificeGoal(this));
        this.goalSelector.addGoal(3, new BreedAnimalsGoal(this));
        this.goalSelector.addGoal(4, new PlantCatalystGoal(this));
        this.goalSelector.addGoal(5, new MeleeAttackGoal(this, 1.0D, false));
        // Patrullar el radio de su guarida cuando no tiene nada que hacer.
        this.goalSelector.addGoal(8, new PatrolHomeGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
        // Ataca también a las invocaciones del jugador.
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
                (target) -> target instanceof ITamableEntity it && it.getOwner() != null));
        // Caza animales que estén dentro de su guarida (para que mueran sobre el sculk).
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Animal.class, 10, true, false,
                this::isAnimalInsideHome));
    }

    // ------------------------------------------------------------------
    // Escalado por distancia + amenaza (mismas reglas que el agresivo)
    // ------------------------------------------------------------------

    @Override
    public void aiStep() {
        super.aiStep();
        if (!attributesAdjusted) {
            adjustAttributesBasedOnSpawnDistance();
            attributesAdjusted = true;
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
                    spawnDistance = Math.sqrt(this.blockPosition().distSqr(
                            new BlockPos((int) playerSpawn.x, (int) playerSpawn.y, (int) playerSpawn.z)));
                    spawnThreat = ThreatLevel.current(this.level());
                    DevilRpg.LOGGER.info("SculkCultivator Spawned at: {} | Player Spawn Point: {} | Distance: {} | Threat: {}",
                            this.blockPosition(), playerSpawn, spawnDistance, String.format("%.2f", spawnThreat));
                }
            }
        }
    }

    private void adjustAttributesBasedOnSpawnDistance() {
        if (spawnDistance < SPAWN_PROFILE.minDistance()) {
            return; // Si está en la zona de spawn, no cambia atributos
        }

        double scaleFactor = SPAWN_PROFILE.scaleFactor(spawnDistance, spawnThreat)
                * (1.0 + spawnThreat * ThreatLevel.MAX_EXTRA_DIFFICULTY);
        Objects.requireNonNull(this.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(SPAWN_PROFILE.baseHealth() * scaleFactor);
        Objects.requireNonNull(this.getAttribute(Attributes.MOVEMENT_SPEED)).setBaseValue(SPAWN_PROFILE.baseSpeed() * scaleFactor);
        Objects.requireNonNull(this.getAttribute(Attributes.ATTACK_DAMAGE)).setBaseValue(SPAWN_PROFILE.baseDamage() * scaleFactor);
        this.setHealth(this.getMaxHealth());
    }

    @Override
    protected int getBaseExperienceReward() {
        return SPAWN_PROFILE.experienceReward(spawnDistance, spawnThreat);
    }

    // ------------------------------------------------------------------
    // Hogar (guarida)
    // ------------------------------------------------------------------

    public void setHome(BlockPos home, int radius) {
        this.homePos = home;
        this.homeRadius = radius;
    }

    public BlockPos getHomePos() {
        return homePos;
    }

    public int getHomeRadius() {
        return homeRadius;
    }

    private BlockPos workCenter() {
        return homePos != null ? homePos : blockPosition();
    }

    private int workRadius() {
        return homeRadius > 0 ? homeRadius : WORK_RADIUS;
    }

    /** ¿El animal está dentro del radio de la guarida? (para que muera sobre el sculk y lo expanda). */
    private boolean isAnimalInsideHome(LivingEntity target) {
        BlockPos home = getHomePos();
        if (home == null || getHomeRadius() <= 0) {
            return false;
        }
        double r = getHomeRadius();
        return target.distanceToSqr(home.getX() + 0.5D, home.getY() + 0.5D, home.getZ() + 0.5D) <= r * r;
    }

    /**
     * Escanea <b>una sola vez</b> el radio de trabajo y devuelve
     * <code>[bloques de infección, catalizadores]</code>. Es un barrido caro (miles de posiciones), así
     * que el goal que lo usa lo llama con caché, nunca cada tick.
     */
    private int[] scanInfection() {
        BlockPos c = workCenter();
        int r = workRadius();
        int sculk = 0;
        int catalysts = 0;
        // Solo la capa superficial: la infección es un manto sobre el terreno, no rellena el volumen.
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = -2; y <= 2; y++) {
                    var state = level().getBlockState(c.offset(x, y, z));
                    if (state.is(Blocks.SCULK)) {
                        sculk++;
                    } else if (state.is(Blocks.SCULK_VEIN)) {
                        sculk++;
                    } else if (state.is(Blocks.SCULK_CATALYST)) {
                        sculk++;
                        catalysts++;
                    }
                }
            }
        }
        return new int[]{sculk, catalysts};
    }

    /** Animales vivos dentro del radio de trabajo (el "ganado" de la granja). */
    private List<Animal> nearbyAnimals() {
        BlockPos c = workCenter();
        int r = workRadius();
        return level().getEntitiesOfClass(Animal.class, new AABB(c).inflate(r),
                a -> a.isAlive() && a.distanceToSqr(c.getX() + 0.5D, c.getY() + 0.5D, c.getZ() + 0.5D) <= (double) r * r);
    }

    // ------------------------------------------------------------------
    // Goals
    // ------------------------------------------------------------------

    /** Patrulla el radio de su guarida (vuelve si se aleja; ronda la zona si no tiene objetivo). */
    static class PatrolHomeGoal extends Goal {
        private final SculkCultivatorEntity cult;
        private static final double SPEED = 1.0D;
        private static final int WANDER_INTERVAL_TICKS = 60;
        private int wanderTicks = 0;

        PatrolHomeGoal(SculkCultivatorEntity cult) {
            this.cult = cult;
        }

        @Override
        public boolean canUse() {
            BlockPos home = cult.getHomePos();
            if (home == null || cult.getHomeRadius() <= 0) {
                return false;
            }
            return outsideRadius(home) || cult.getTarget() == null;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            BlockPos home = cult.getHomePos();
            if (home == null) return;
            if (outsideRadius(home)) {
                cult.getNavigation().moveTo(home.getX(), home.getY(), home.getZ(), SPEED);
                return;
            }
            if (cult.getTarget() != null) {
                return; // el ataque manda
            }
            if (--wanderTicks <= 0 || cult.getNavigation().isDone()) {
                var random = cult.getRandom();
                double angle = random.nextDouble() * Math.PI * 2.0;
                double dist = random.nextDouble() * cult.getHomeRadius();
                double wx = home.getX() + Math.cos(angle) * dist;
                double wz = home.getZ() + Math.sin(angle) * dist;
                wanderTicks = WANDER_INTERVAL_TICKS;
                cult.getNavigation().moveTo(wx, home.getY(), wz, SPEED);
            }
        }

        private boolean outsideRadius(BlockPos home) {
            double r = cult.getHomeRadius();
            return cult.distanceToSqr(home.getX() + 0.5D, home.getY() + 0.5D, home.getZ() + 0.5D) > r * r;
        }
    }

    /**
     * Goal: cuando el ganado crece lo suficiente, sacrifica un animal sobre el sculk (alimenta al
     * catalizador, que expande la infección).
     */
    static class SacrificeGoal extends Goal {
        /** Si no hay ganado suficiente, espera antes de volver a consultarlo. */
        private static final int RETRY_TICKS = 40;
        /** Si en este tiempo no consigue llegar a la víctima, desiste (no se queda atascado persiguiéndola). */
        private static final int REACH_TIMEOUT_TICKS = 300;

        private final SculkCultivatorEntity cult;
        private Animal victim = null;
        private int cooldown = RETRY_TICKS;
        private int reachTicks = 0;

        SacrificeGoal(SculkCultivatorEntity cult) {
            this.cult = cult;
        }

        @Override
        public boolean canUse() {
            if (cult.getTarget() != null) return false;
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            List<Animal> animals = cult.nearbyAnimals();
            if (animals.size() < SACRIFICE_THRESHOLD) {
                cooldown = RETRY_TICKS;
                return false;
            }
            victim = animals.stream().min(Comparator.comparingDouble(a -> a.distanceToSqr(cult))).orElse(null);
            if (victim == null) {
                cooldown = RETRY_TICKS;
                return false;
            }
            reachTicks = 0;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return victim != null && victim.isAlive() && cult.getTarget() == null
                    && reachTicks <= REACH_TIMEOUT_TICKS;
        }

        @Override
        public void tick() {
            if (victim == null) return;
            if (cult.distanceToSqr(victim) > 4.0D) {
                reachTicks++;
                cult.getNavigation().moveTo(victim, 1.0D);
            } else {
                victim.hurt(cult.damageSources().mobAttack(cult), Float.MAX_VALUE);
                victim = null;
            }
        }

        @Override
        public void stop() {
            victim = null;
            reachTicks = 0;
            cooldown = RETRY_TICKS;
        }
    }

    /** Goal: alimentar al ganado para que se aparee (cría macabra). */
    static class BreedAnimalsGoal extends Goal {
        /** Si no hay nada que criar, espera antes de volver a consultar el ganado. */
        private static final int RETRY_TICKS = 40;

        private final SculkCultivatorEntity cult;
        private Animal target = null;
        private int cooldown = RETRY_TICKS;

        BreedAnimalsGoal(SculkCultivatorEntity cult) {
            this.cult = cult;
        }

        @Override
        public boolean canUse() {
            if (cult.getTarget() != null) return false;
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            List<Animal> animals = cult.nearbyAnimals();
            if (animals.size() >= SACRIFICE_THRESHOLD + 4) {
                cooldown = RETRY_TICKS;
                return false;
            }
            target = animals.stream()
                    .filter(a -> !a.isInLove() && a.canFallInLove())
                    .min(Comparator.comparingDouble(a -> a.distanceToSqr(cult)))
                    .orElse(null);
            if (target == null) {
                cooldown = RETRY_TICKS;
                return false;
            }
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return target != null && target.isAlive();
        }

        @Override
        public void tick() {
            if (target == null) return;
            if (cult.distanceToSqr(target) > 4.0D) {
                cult.getNavigation().moveTo(target, 1.0D);
            } else {
                target.setInLove(null);
                cooldown = RETRY_TICKS * 3;
                target = null;
            }
        }

        @Override
        public void stop() {
            target = null;
        }
    }

    /** Goal: extraer sculk del terreno y condensarlo en un catalizador nuevo en el borde de la infección. */
    static class PlantCatalystGoal extends Goal {
        /** Cada cuánto se permite volver a barrer el terreno (el barrido es caro). */
        private static final int RETRY_TICKS = 60;
        /** Tras plantar un catalizador, cuánto tarda en empezar otro. */
        private static final int PLANT_COOLDOWN_TICKS = 200;
        /** Cada cuántos ticks se refresca la cuenta de sculk/catalizadores. */
        private static final int SCAN_INTERVAL_TICKS = 100;

        private final SculkCultivatorEntity cult;
        private BlockPos spot = null;
        private int cooldown = SCAN_INTERVAL_TICKS;
        private int scanCooldown = 0;
        private int sculkCount = 0;
        private int catalystCount = 0;

        PlantCatalystGoal(SculkCultivatorEntity cult) {
            this.cult = cult;
        }

        @Override
        public boolean canUse() {
            if (cult.getTarget() != null) return false;
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            // El barrido del terreno es caro: se cachea y solo se refresca cada SCAN_INTERVAL_TICKS.
            if (--scanCooldown <= 0) {
                scanCooldown = SCAN_INTERVAL_TICKS;
                int[] infection = cult.scanInfection();
                sculkCount = infection[0];
                catalystCount = infection[1];
            }
            if (sculkCount < SCULK_PER_CATALYST * 2 || catalystCount >= MAX_CATALYSTS) {
                cooldown = RETRY_TICKS;
                return false;
            }
            spot = findEdgeSpot();
            if (spot == null) {
                cooldown = RETRY_TICKS;
                return false;
            }
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return spot != null && cult.getTarget() == null;
        }

        @Override
        public void tick() {
            if (spot == null) return;
            if (cult.distanceToSqr(spot.getX() + 0.5D, spot.getY() + 0.5D, spot.getZ() + 0.5D) > 4.0D) {
                cult.getNavigation().moveTo(spot.getX(), spot.getY(), spot.getZ(), 1.0D);
                return;
            }
            consumeNearbySculk(spot);
            cult.level().setBlock(spot, Blocks.SCULK_CATALYST.defaultBlockState(), 3);
            cooldown = PLANT_COOLDOWN_TICKS;
            spot = null;
        }

        @Override
        public void stop() {
            spot = null;
        }

        private BlockPos findEdgeSpot() {
            BlockPos c = cult.workCenter();
            int r = cult.workRadius();
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos ground = new BlockPos(c.getX() + x, c.getY() - 1, c.getZ() + z);
                    BlockPos above = ground.above();
                    if (!cult.level().getBlockState(above).isAir()) continue;
                    if (!cult.level().getBlockState(ground).isSolid()) continue;
                    if (cult.level().getBlockState(ground).is(Blocks.SCULK_CATALYST)) continue;
                    if (touchesSculk(above)) {
                        return above;
                    }
                }
            }
            return null;
        }

        private boolean touchesSculk(BlockPos pos) {
            for (Direction d : Direction.values()) {
                if (cult.level().getBlockState(pos.relative(d)).is(Blocks.SCULK)) {
                    return true;
                }
            }
            return false;
        }

        /** Consume (convierte en tierra muerta) algunos bloques de sculk alrededor, como "material". */
        private void consumeNearbySculk(BlockPos center) {
            int consumed = 0;
            for (int x = -3; x <= 3 && consumed < 6; x++) {
                for (int z = -3; z <= 3 && consumed < 6; z++) {
                    BlockPos p = center.offset(x, -1, z);
                    if (cult.level().getBlockState(p).is(Blocks.SCULK)) {
                        cult.level().setBlock(p, Blocks.COARSE_DIRT.defaultBlockState(), 3);
                        consumed++;
                    }
                }
            }
        }
    }
}
