package com.chipoodle.devilrpg.entity;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.spawnprofile.AggressiveZombieSpawnProfile;
import com.chipoodle.devilrpg.spawnprofile.SpawnScaleProfile;
import com.chipoodle.devilrpg.survival.ThreatLevel;
import com.chipoodle.devilrpg.world.LairGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
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
    /**
     * Tamaño mínimo del rebaño: <b>por debajo de esto el cultivador no sacrifica nada</b>. Sin este suelo, el
     * corral se vaciaba: la cuenta de animales incluye a los salvajes que andan por la guarida, así que el
     * número nunca bajaba del umbral y seguía matando ganado hasta dejar una sola especie.
     */
    private static final int MIN_LIVESTOCK = 6;
    /** Tamaño a partir del cual deja de criar (banda con {@link #MIN_LIVESTOCK}, para que no oscile). */
    private static final int MAX_LIVESTOCK = 8;
    /**
     * Adultos de la <b>misma especie</b> que deben quedar para poder sacrificar uno: con 3 se sacrifica uno y
     * quedan 2, que es una pareja de cría. Así nunca desaparece una especie del corral (que es justo lo que
     * pasaba: se comía vacas, ovejas y cerdos y solo sobrevivían los pollos, que además se reproducen solos
     * poniendo huevos).
     */
    private static final int SPECIES_KEEP = 3;

    /** Radio en el que se siente amenazado por un jugador (y sale corriendo). */
    static final double THREAT_RADIUS_PLAYER = 12.0D;
    /** Radio en el que lo asustan las invocaciones del jugador (mascotas con dueño). */
    static final double THREAT_RADIUS_MINION = 9.0D;
    /** Tras recibir daño huye al menos este tiempo, aunque el atacante se aleje (memoria del susto). */
    private static final int FLEE_AFTER_HURT_TICKS = 120;
    /** Radio en el que sigue considerando amenaza a quien le hizo daño, mientras está en pánico. */
    private static final double PANIC_ATTACKER_RADIUS = 32.0D;

    private double spawnDistance = 0;
    private double spawnThreat = 1.0;
    private boolean attributesAdjusted = false;

    /** Tick hasta el que huye por haber recibido daño hace poco. */
    private int fleeUntil = 0;
    /** Quien le hizo daño por última vez (lo busca mientras está en pánico). */
    private LivingEntity lastAttacker = null;

    /** "Hogar" (núcleo de su guarida): patrulla un radio alrededor. */
    private BlockPos homePos = null;
    private int homeRadius = 0;

    public SculkCultivatorEntity(EntityType<? extends AbstractIllager> type, Level level) {
        super(type, level);
        // El corral de su granja es un cercado CERRADO con una única puerta de madera (así el ganado no se
        // escapa). Para poder entrar a criar y sacrificar necesita las dos mitades de la mecánica de puertas,
        // igual que los aldeanos: el pathfinding que las considera transitables...
        if (getNavigation() instanceof GroundPathNavigation groundNavigation) {
            groundNavigation.setCanOpenDoors(true);
        }
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
        // El cultivador NUNCA pelea (no tiene goals de ataque), así que siempre va "obrando".
        return IllagerArmPose.SPELLCASTING;
    }

    /**
     * El cultivador es un cobarde: no ataca a nadie. Si le hacen daño, recuerda el susto y a quien se lo
     * hizo, y huye (ver {@link FleeThreatGoal}).
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean damaged = super.hurt(source, amount);
        if (damaged && !level().isClientSide) {
            fleeUntil = tickCount + FLEE_AFTER_HURT_TICKS;
            Entity attacker = source.getEntity();
            lastAttacker = attacker instanceof LivingEntity living ? living : null;
        }
        return damaged;
    }

    /**
     * ¿Está aterrado por haber recibido daño hace poco? (Nombre propio a propósito: {@code isPanicking()}
     * ya existe en {@code PathfinderMob} y significa otra cosa — que hay un {@code PanicGoal} corriendo.)
     */
    boolean isTerrified() {
        return tickCount < fleeUntil;
    }

    /**
     * La amenaza actual: el jugador (o invocación suya) más cercano, o —si está en pánico— quien le hizo daño
     * aunque ya esté lejos. Los jugadores en creativo/espectador se ignoran, para poder observarlo trabajar.
     */
    LivingEntity findThreat() {
        if (isTerrified() && lastAttacker != null && lastAttacker.isAlive()
                && distanceToSqr(lastAttacker) <= PANIC_ATTACKER_RADIUS * PANIC_ATTACKER_RADIUS) {
            return lastAttacker;
        }
        Player player = level().getNearestPlayer(this, THREAT_RADIUS_PLAYER);
        if (player != null && player.isAlive() && !player.isCreative() && !player.isSpectator()) {
            return player;
        }
        for (LivingEntity nearby : level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(THREAT_RADIUS_MINION))) {
            if (isOwnedMinion(nearby)) {
                return nearby;
            }
        }
        return null;
    }

    /** ¿Es una invocación del jugador (mascota con dueño)? */
    private static boolean isOwnedMinion(LivingEntity entity) {
        if (entity instanceof ITamableEntity tamable) {
            return tamable.getOwner() != null;
        }
        return entity instanceof TamableAnimal animal && animal.getOwner() != null;
    }

    @Override
    protected void registerGoals() {
        // A propósito NO se llama a super.registerGoals(): Raider añade los goals de raid vanilla
        // (bandera de líder, PathfindToRaid, celebración, moverse por aldeas) que aquí no aplican.
        //
        // El cultivador NO tiene ningún goal de ataque ni targetSelector: no pelea. Solo trabaja (sus tres
        // labores) y huye si se siente amenazado, volviendo a sus labores cuando pasa el peligro.
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new FleeThreatGoal(this));
        // Tareas de cultivador (su "trabajo").
        this.goalSelector.addGoal(2, new SacrificeGoal(this));
        this.goalSelector.addGoal(3, new BreedAnimalsGoal(this));
        this.goalSelector.addGoal(4, new PlantCatalystGoal(this));
        // ...y la otra mitad: abrir de verdad la puerta del corral cuando se topa con ella yendo a trabajar.
        // (OpenDoorGoal no declara flags: solo abre puertas, no navega, así que convive con el goal de turno.)
        this.goalSelector.addGoal(5, new OpenDoorGoal(this, true));
        // Patrullar el radio de su guarida cuando no tiene nada que hacer (y volver tras una huida).
        this.goalSelector.addGoal(8, new PatrolHomeGoal(this));
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

    /**
     * El <b>rebaño del corral</b>: solo los animales marcados como ganado ({@code LIVESTOCK_TAG}). Es a
     * propósito que no cuente a los animales <b>salvajes</b> que anden por la guarida: esos son presa de los
     * zombies, y si entraran en la cuenta del cultivador, su presencia evitaría que el número bajara del
     * mínimo y el cultivador seguiría sacrificando ganado hasta dejar el corral con una sola especie.
     */
    private List<Animal> livestock() {
        return nearbyAnimals().stream().filter(LairGenerator::isLivestock).toList();
    }

    /** Adultos vivos de la misma especie que {@code sample} dentro del rebaño dado. */
    private static long adultsOfSameSpecies(List<Animal> herd, Animal sample) {
        return herd.stream()
                .filter(a -> !a.isBaby() && a.getType() == sample.getType())
                .count();
    }

    // ------------------------------------------------------------------
    // Goals
    // ------------------------------------------------------------------

    /**
     * Goal: el cultivador es un <b>cobarde</b>. No pelea: si un jugador (o una invocación suya) se le acerca,
     * o si le han hecho daño hace poco, <b>sale corriendo en dirección contraria</b>. Cuando la amenaza
     * desaparece el goal suelta el control y vuelve a sus labores; además {@link PatrolHomeGoal} lo trae de
     * vuelta al santuario si se alejó demasiado huyendo.
     */
    static class FleeThreatGoal extends Goal {
        /** Cada cuánto se vuelve a buscar amenaza (no hace falta preguntar cada tick). */
        private static final int CHECK_TICKS = 10;
        /** Cada cuánto se recalcula la ruta de huida. */
        private static final int REPATH_TICKS = 20;
        /** Distancia (horizontal/vertical) a la que busca el punto de huida. */
        private static final int FLEE_DISTANCE = 16;
        private static final int FLEE_VERTICAL = 7;
        /** Corre más rápido que trabajando: las piernas se lo llevan. */
        private static final double SPRINT_SPEED = 1.5D;
        /** Sigue huyendo un poco más allá del borde del radio de amenaza, para no quedarse justo al filo. */
        private static final double KEEP_FLEEING_MARGIN = 4.0D;

        private final SculkCultivatorEntity cult;
        private LivingEntity threat;
        private int checkCooldown;
        private int repathTicks;

        FleeThreatGoal(SculkCultivatorEntity cult) {
            this.cult = cult;
            // Sin flags, un goal puede arrancar aunque otro de más prioridad esté corriendo y no lo bloquea:
            // los flags son el único mecanismo de prioridad de GoalSelector. Los navegadores piden MOVE/LOOK.
            this.setFlags(java.util.EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (cult.isTerrified()) {
                return true; // le acaban de pegar: sale corriendo ya
            }
            if (checkCooldown > 0) {
                checkCooldown--;
                return false;
            }
            checkCooldown = CHECK_TICKS;
            threat = cult.findThreat();
            return threat != null;
        }

        @Override
        public boolean canContinueToUse() {
            if (cult.isTerrified()) {
                return true;
            }
            if (threat == null || !threat.isAlive()) {
                return false;
            }
            double limit = threatRadius(threat) + KEEP_FLEEING_MARGIN;
            return cult.distanceToSqr(threat) <= limit * limit;
        }

        @Override
        public void start() {
            repathTicks = 0;
        }

        @Override
        public void tick() {
            if (--repathTicks > 0) {
                return;
            }
            repathTicks = REPATH_TICKS;
            if (threat == null || !threat.isAlive()) {
                threat = cult.findThreat();
            }
            // Huye en dirección contraria a la amenaza; si no ve a nadie (pánico sin atacante a la vista), a
            // un punto al azar. DefaultRandomPos respeta el terreno, así que no se tira por un barranco.
            Vec3 away = threat != null && threat.isAlive()
                    ? DefaultRandomPos.getPosAway(cult, FLEE_DISTANCE, FLEE_VERTICAL, threat.position())
                    : DefaultRandomPos.getPos(cult, FLEE_DISTANCE, FLEE_VERTICAL);
            if (away != null) {
                cult.getNavigation().moveTo(away.x, away.y, away.z, SPRINT_SPEED);
            }
        }

        @Override
        public void stop() {
            threat = null;
            repathTicks = 0;
            checkCooldown = 0; // la próxima vez que se plantee, que vuelva a mirar enseguida
        }

        private static double threatRadius(LivingEntity entity) {
            return entity instanceof Player ? THREAT_RADIUS_PLAYER : THREAT_RADIUS_MINION;
        }
    }

    /** Patrulla el radio de su guarida (vuelve si se aleja; ronda la zona si no tiene objetivo). */
    static class PatrolHomeGoal extends Goal {
        private final SculkCultivatorEntity cult;
        private static final double SPEED = 1.0D;
        private static final int WANDER_INTERVAL_TICKS = 60;
        private int wanderTicks = 0;

        PatrolHomeGoal(SculkCultivatorEntity cult) {
            this.cult = cult;
            this.setFlags(java.util.EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            BlockPos home = cult.getHomePos();
            if (home == null || cult.getHomeRadius() <= 0) {
                return false;
            }
            // El cultivador nunca tiene objetivo, así que esto es siempre su "trabajo por defecto".
            return true;
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
     * Goal: cuando el rebaño crece <b>por encima del mínimo</b>, sacrifica un animal sobre el sculk (alimenta
     * al catalizador, que expande la infección). Nunca sacrifica por debajo de {@link #MIN_LIVESTOCK} ni a una
     * especie que se quedaría sin pareja ({@link #SPECIES_KEEP}): el corral se mantiene vivo y variado.
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
            this.setFlags(java.util.EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            List<Animal> herd = cult.livestock();
            // Con el rebaño en el mínimo (o por debajo) NO se sacrifica nada: el corral no se vacía.
            if (herd.size() <= MIN_LIVESTOCK) {
                cooldown = RETRY_TICKS;
                return false;
            }
            victim = pickVictim(herd);
            if (victim == null) {
                cooldown = RETRY_TICKS;
                return false;
            }
            reachTicks = 0;
            return true;
        }

        /**
         * Elige víctima: el adulto más cercano <b>de una especie con al menos {@link #SPECIES_KEEP} adultos</b>
         * (así tras el sacrificio quedan 2, que es una pareja de cría y la especie no desaparece del corral).
         */
        private Animal pickVictim(List<Animal> herd) {
            return herd.stream()
                    .filter(a -> !a.isBaby())
                    .filter(a -> adultsOfSameSpecies(herd, a) >= SPECIES_KEEP)
                    .min(Comparator.comparingDouble(a -> a.distanceToSqr(cult)))
                    .orElse(null);
        }

        @Override
        public boolean canContinueToUse() {
            return victim != null && victim.isAlive() && reachTicks <= REACH_TIMEOUT_TICKS;
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
        /** Segunda pareja: hay que poner en celo a DOS de la misma especie o no se aparean. */
        private Animal partner = null;
        private int cooldown = RETRY_TICKS;

        BreedAnimalsGoal(SculkCultivatorEntity cult) {
            this.cult = cult;
            this.setFlags(java.util.EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            List<Animal> herd = cult.livestock();
            if (herd.size() >= MAX_LIVESTOCK) {
                cooldown = RETRY_TICKS;
                return false;
            }
            target = herd.stream()
                    .filter(a -> !a.isBaby() && !a.isInLove() && a.canFallInLove())
                    .min(Comparator.comparingDouble(a -> a.distanceToSqr(cult)))
                    .orElse(null);
            if (target == null) {
                cooldown = RETRY_TICKS;
                return false;
            }
            // Pareja: otro adulto de la MISMA especie que también pueda enamorarse. Con uno solo en celo el
            // apareamiento no ocurre (Animal necesita pareja), y por eso antes el rebaño solo menguaba.
            Animal chosen = target;
            partner = herd.stream()
                    .filter(a -> a != chosen && !a.isBaby() && a.getType() == chosen.getType())
                    .filter(a -> !a.isInLove() && a.canFallInLove())
                    .min(Comparator.comparingDouble(a -> a.distanceToSqr(cult)))
                    .orElse(null);
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
                if (partner != null && partner.isAlive()) {
                    partner.setInLove(null);
                }
                cooldown = RETRY_TICKS * 3;
                target = null;
                partner = null;
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
        /** Si no consigue llegar al punto elegido, desiste y busca otro (no se queda atascado). */
        private static final int REACH_TIMEOUT_TICKS = 300;

        private final SculkCultivatorEntity cult;
        private BlockPos spot = null;
        private int cooldown = SCAN_INTERVAL_TICKS;
        private int scanCooldown = 0;
        private int sculkCount = 0;
        private int catalystCount = 0;
        private int reachTicks = 0;

        PlantCatalystGoal(SculkCultivatorEntity cult) {
            this.cult = cult;
            this.setFlags(java.util.EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
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
            reachTicks = 0;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return spot != null && reachTicks <= REACH_TIMEOUT_TICKS;
        }

        @Override
        public void tick() {
            if (spot == null) return;
            if (cult.distanceToSqr(spot.getX() + 0.5D, spot.getY() + 0.5D, spot.getZ() + 0.5D) > 4.0D) {
                reachTicks++;
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
            reachTicks = 0;
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
