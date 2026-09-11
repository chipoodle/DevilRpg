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
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
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
    /** Si el asedio sigue activo (el zombie sigue marchando al centro). Se desactiva cuando la aldea cae. */
    private boolean goToCenterActive = true;
    /** "Hogar" del zombie (p. ej. el núcleo de una guarida): patrulla un radio alrededor suyo. */
    private BlockPos homePos = null;
    private int homeRadius = 0;
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

    /**
     * Asigna un "hogar" con un radio de patrulla (p. ej. el núcleo de una guarida): el zombie se queda
     * rondando esa zona en vez de alejarse o pegarse al centro exacto.
     */
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

    /** Siempre que se asigne centro, el asedio arranca activo. */
    public void setGoToCenterActive(boolean active) {
        this.goToCenterActive = active;
    }

    public boolean isGoToCenterActive() {
        return goToCenterActive;
    }

    /** ¿Puede este zombie romper obsidiana? (depende de su nivel = distancia de spawn). */
    public boolean canBreakObsidian() {
        return spawnDistance >= OBSIDIAN_THRESHOLD;
    }

    /** ¿Puede romper este bloque? (nunca los inquebrantables; obsidiana solo si su nivel lo permite). */
    private boolean canBreakBlock(BlockState state) {
        Block b = state.getBlock();
        if (b == Blocks.BEDROCK || b == Blocks.WATER || b == Blocks.LAVA || b == Blocks.AIR) {
            return false;
        }
        // Cualquier bloque inquebrantable (dureza -1), como el SELLO que blinda el núcleo de una guarida:
        // si el zombie pudiera picarlo, se saltaría el requisito de matar al cultivador.
        if (b.defaultDestroyTime() < 0.0F) {
            return false;
        }
        if (b == Blocks.OBSIDIAN || b == Blocks.CRYING_OBSIDIAN) {
            return canBreakObsidian();
        }
        return true; // madera, tierra, grava, arena, lana...
    }

    /** Rompe el bloque en la posición, respetando el límite de obsidiana y los bloques inquebrantables. */
    private void breakBlockAt(BlockPos pos) {
        BlockState bs = level().getBlockState(pos);
        Block b = bs.getBlock();
        if (b.defaultDestroyTime() < 0.0F) {
            return; // inquebrantable
        }
        if ((b == Blocks.OBSIDIAN || b == Blocks.CRYING_OBSIDIAN) && !canBreakObsidian()) {
            return;
        }
        level().destroyBlock(pos, true);
    }

    /**
     * Busca el bloque sólido rompible más cercano en la dirección de {@code towards} y lo rompe de forma
     * AGRESIVA: destruye el bloque del cuerpo (+1) y, si el objetivo está arriba (necesita saltar en
     * diagonal), también el de arriba (+2) para abrir un hueco de 2 bloques por el que pueda subir.
     * Además, aleatoriamente destruye bloques contiguos al central (misma fila, +1/+2 pegados) para
     * ensanchar el paso. Usado al ir al centro cuando el zombie está bloqueado por el muro.
     */
    private void breakBlockTowards(BlockPos towards) {
        BlockPos zPos = blockPosition();
        int sx = Integer.signum(towards.getX() - zPos.getX());
        int sz = Integer.signum(towards.getZ() - zPos.getZ());
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int dy = 0; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    BlockPos candidate = new BlockPos(zPos.getX() + dx, zPos.getY() + dy, zPos.getZ() + dz);
                    BlockState bs = level().getBlockState(candidate);
                    if (!bs.isAir() && bs.isSolid() && canBreakBlock(bs)) {
                        double score = Math.abs(dx - sx) + Math.abs(dz - sz) + dy * 0.5D;
                        if (score < bestScore) {
                            bestScore = score;
                            best = candidate;
                        }
                    }
                }
            }
        }
        if (best == null) {
            return;
        }
        // Destruir el bloque principal.
        breakBlockAt(best);
        // Dirección principal hacia el objetivo (para saber si el objetivo está más arriba).
        boolean targetAbove = towards.getY() > zPos.getY();
        int dirX = Integer.signum(best.getX() - zPos.getX());
        int dirZ = Integer.signum(best.getZ() - zPos.getZ());
        // Si el objetivo está arriba, destruir también el bloque +2 (arriba del principal) para poder saltar.
        if (targetAbove) {
            BlockPos above = best.above();
            if (canBreakBlock(level().getBlockState(above))) {
                breakBlockAt(above);
            }
        }
        // Aleatoriamente destruir un bloque contiguo al central (misma fila +1, o +1/+2 al lado) para
        // ensanchar el paso, con probabilidad moderada.
        RandomSource random = level().random;
        if (random.nextFloat() < 0.5F) {
            int side = random.nextBoolean() ? 1 : -1;
            BlockPos adjacent;
            if (dirX != 0) {
                adjacent = new BlockPos(best.getX(), best.getY(), best.getZ() + side);
            } else {
                adjacent = new BlockPos(best.getX() + side, best.getY(), best.getZ());
            }
            if (canBreakBlock(level().getBlockState(adjacent))) {
                breakBlockAt(adjacent);
            }
            // Si objetivo arriba, también el +2 del contiguo.
            if (targetAbove) {
                BlockPos adjacentAbove = adjacent.above();
                if (canBreakBlock(level().getBlockState(adjacentAbove))) {
                    breakBlockAt(adjacentAbove);
                }
            }
        }
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
        // Si está en el agua atascado, nadar hacia la orilla más cercana (prioridad alta, antes de romper).
        this.goalSelector.addGoal(2, new EscapeWaterGoal(this));
        // Romper el bloque que le estorba cuando está atascado (pero no atacar casas si puede pasar).
        this.goalSelector.addGoal(3, new BreakBlockGoal(this));
        // Comportamiento de MANADA: dispersarse y rodear al objetivo, no apilarse en línea recta. Solo
        // actúa mientras el zombie NO está bien posicionado; al estarlo, cede el control a MeleeAttack.
        this.goalSelector.addGoal(4, new HerdBehaviorGoal(this, 1.2D));
        this.goalSelector.addGoal(5, new MeleeAttackGoal(this, 1.2D, false)); // Ataque cuerpo a cuerpo
        this.goalSelector.addGoal(6, new FireballAttackGoal(this)); // Lanzar fuego como los Blaze
        // Si no hay objetivo, marchar hacia el centro de la aldea (para no merodear fuera).
        this.goalSelector.addGoal(7, new MoveToVillageCenterGoal(this));
        // Y si tiene un "hogar" (guarida), patrullar su radio.
        this.goalSelector.addGoal(8, new PatrolHomeGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true)); // Detectar jugadores
        // También ataca a las invocaciones (minions) del jugador, no solo al jugador. Usamos LivingEntity
        // (no TamableAnimal) para cubrir también al oso, que extiende AbstractChestedHorse y no TamableAnimal.
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
                (target) -> target instanceof ITamableEntity it && it.getOwner() != null));
        // Cazar ANIMALES que estén dentro de su guarida: al morir sobre el sculk, el catalizador de la
        // guarida expande la infección (mecánica vanilla). Solo aplica a los que tienen un hogar (guarida).
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Animal.class, 10, true, false,
                this::isAnimalInsideHome));
    }

    /**
     * ¿Es una presa válida para el zombie? Sí si es un animal dentro del radio de su guarida... salvo que sea
     * <b>ganado de la granja macabra</b> (etiqueta {@link LairGenerator#LIVESTOCK_TAG}). Ese rebaño es del
     * cultivador: si los demás enemigos de la guarida lo cazan, el cultivador se queda sin nada que criar ni
     * sacrificar y la granja deja de tener sentido. Los animales <b>salvajes</b> que entren en la guarida sí
     * se siguen cazando (así es como la infección se alimenta sola).
     */
    private boolean isAnimalInsideHome(LivingEntity target) {
        BlockPos home = getHomePos();
        if (home == null || getHomeRadius() <= 0) {
            return false;
        }
        if (LairGenerator.isLivestock(target)) {
            return false;
        }
        double r = getHomeRadius();
        return target.distanceToSqr(home.getX() + 0.5D, home.getY() + 0.5D, home.getZ() + 0.5D) <= r * r;
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
     * Goal: si el zombie está en el agua y atascado (no progresa hacia su objetivo ni sale del agua),
     * buscar la orilla (bloque de tierra) más cercana y nadar hacia ella para desatascarse. Se activa con
     * prioridad alta, antes que romper o atacar, para que no se quede saltando en el agua fuera de la aldea.
     */
    static class EscapeWaterGoal extends Goal {
        /** Ticks nadando sin avanzar antes de considerar que está atascado y tocarle buscar la orilla. */
        private static final int STUCK_TICKS_BEFORE_TRYING = 40;
        /** Tope de intentos seguidos de salir del agua antes de soltar el control. */
        private static final int MAX_ESCAPE_TICKS = 200;

        private final AggressiveZombieEntity zombie;
        private BlockPos shore = null;
        private int stuckTicks = 0;
        private int jumpCooldown = 0;
        private double lastX, lastZ;
        // Seguimiento mientras el goal NO está corriendo, para saber si de verdad está atascado.
        private double trackX = Double.NaN, trackZ = 0;
        private int idleStuckTicks = 0;
        private int escapeTicks = 0;
        private int retryCooldown = 0;

        public EscapeWaterGoal(AggressiveZombieEntity zombie) {
            this.zombie = zombie;
            // Este goal NAVEGA (nada hacia la orilla), así que declara MOVE: sin flags, GoalSelector lo deja
            // arrancar aunque otro goal de más prioridad esté corriendo y no lo bloquea — y los flags son el
            // ÚNICO mecanismo de prioridad. Con MOVE, mientras sale del agua manda él (prioridad 2) sobre la
            // manada (4) y el ataque (5).
            this.setFlags(java.util.EnumSet.of(Goal.Flag.MOVE));
        }

        /**
         * Solo se activa si de verdad está <b>atascado</b> en el agua (nada y no avanza). Antes se activaba
         * con solo tocar agua (el antiguo {@code isReallyStuck()} devolvía {@code true} siempre), y como tiene
         * prioridad 2 eso le bloqueaba TODO lo demás —romper bloques, marchar al centro y atacar—, así que
         * un zombie en el agua se quedaba nadando en el sitio sin hacer nada. Con la aldea flotante rodeada de
         * agua y las oleadas saliendo a 32–40 bloques (en el agua), eso dejaba al asedio entero inútil.
         */
        @Override
        public boolean canUse() {
            if (!zombie.isInWater() || !zombie.isInWaterOrRain()) {
                trackX = Double.NaN;
                idleStuckTicks = 0;
                return false;
            }
            if (retryCooldown > 0) {
                retryCooldown--;
                return false;
            }
            // Mientras nada y avanza, no está atascado: que manden el ataque, la manada, romper o el centro.
            double dx = zombie.getX() - trackX;
            double dz = zombie.getZ() - trackZ;
            if (Double.isNaN(trackX) || dx * dx + dz * dz > 0.01D) {
                trackX = zombie.getX();
                trackZ = zombie.getZ();
                idleStuckTicks = 0;
                return false;
            }
            return ++idleStuckTicks > STUCK_TICKS_BEFORE_TRYING;
        }

        @Override
        public boolean canContinueToUse() {
            // Si en MAX_ESCAPE_TICKS no lo consigue, suelta el control: así deja turno a romper los bloques
            // que le estorban (que es justo lo que hace falta cuando la orilla tiene una pared).
            return zombie.isInWater() && zombie.isInWaterOrRain() && escapeTicks < MAX_ESCAPE_TICKS;
        }

        @Override
        public void start() {
            stuckTicks = 0;
            jumpCooldown = 0;
            escapeTicks = 0;
            lastX = zombie.getX();
            lastZ = zombie.getZ();
            shore = findNearestShore();
            if (shore != null) {
                // Nadar hacia la orilla (el pathfinding navega por el agua hacia el bloque de tierra).
                zombie.getNavigation().moveTo(shore.getX(), shore.getY(), shore.getZ(), 1.2D);
            }
        }

        @Override
        public void stop() {
            // Al rendirse, deja el turno el mismo tiempo que lo intentó antes de volver a probar.
            retryCooldown = MAX_ESCAPE_TICKS;
            trackX = Double.NaN;
            idleStuckTicks = 0;
        }

        @Override
        public void tick() {
            escapeTicks++;
            double dx = zombie.getX() - lastX;
            double dz = zombie.getZ() - lastZ;
            if (dx * dx + dz * dz < 0.01D) {
                stuckTicks++;
            } else {
                stuckTicks = 0;
                lastX = zombie.getX();
                lastZ = zombie.getZ();
            }
            // Si no avanza en 80 ticks, re-buscar la orilla y re-navegar.
            if (stuckTicks >= 80) {
                shore = findNearestShore();
                if (shore != null) {
                    zombie.getNavigation().moveTo(shore.getX(), shore.getY(), shore.getZ(), 1.2D);
                }
                stuckTicks = 0;
                lastX = zombie.getX();
                lastZ = zombie.getZ();
            }
            // Saltar hacia la orilla si está en el agua y no logra salir. Cada ~15 ticks, con un impulso
            // vertical + horizontal hacia la orilla, para trepar el desnivel de la isla (aunque detecte
            // mal la pared, el empuje hacia arriba lo saca del agua).
            if (zombie.isInWater() && shore != null && --jumpCooldown <= 0) {
                jumpTowardShore();
                jumpCooldown = 15;
            }
        }

        /**
         * Empuja al zombie hacia arriba y en la dirección de la orilla para que trepe el desnivel desde el
         * agua. Es deliberadamente simple y robusto: no depende de detectar el bloque exacto, solo acerca
         * el cuerpo a la orilla y lo eleva, para que el pathfinding pueda subir a la tierra.
         */
        private void jumpTowardShore() {
            BlockPos zPos = zombie.blockPosition();
            int sx = Integer.signum(shore.getX() - zPos.getX());
            int sz = Integer.signum(shore.getZ() - zPos.getZ());
            // Empuje horizontal hacia la orilla + empuje vertical para elevarse sobre el agua.
            double vx = sx * 0.4D;
            double vz = sz * 0.4D;
            double vy = 0.5D;
            zombie.setDeltaMovement(vx, vy, vz);
        }

        /** Busca el bloque de tierra (no agua) más cercano dentro de un radio, escaneando en espiral. */
        private BlockPos findNearestShore() {
            BlockPos pos = zombie.blockPosition();
            int r = 0;
            while (r <= 16) {
                for (int x = -r; x <= r; x++) {
                    for (int z = -r; z <= r; z++) {
                        if (Math.abs(x) != r && Math.abs(z) != r) continue; // borde del cuadrado
                        // Se mira el suelo a la altura de sus pies y también uno por encima: en las islas al
                        // nivel del agua el bloque de tierra queda a la MISMA altura que el agua de al lado,
                        // así que mirando solo por debajo no lo encontraría.
                        for (int dy = -1; dy <= 0; dy++) {
                            BlockPos ground = new BlockPos(pos.getX() + x, pos.getY() + dy, pos.getZ() + z);
                            if (isLand(ground)) {
                                return ground.above();
                            }
                        }
                    }
                }
                r++;
            }
            return null;
        }

        private boolean isLand(BlockPos pos) {
            if (!zombie.level().getFluidState(pos).is(FluidTags.WATER)) {
                return zombie.level().getBlockState(pos).isSolid();
            }
            return false;
        }
    }

    /**
     * Goal: romper el obstáculo que le estorba cuando el zombie no logra acercarse a su objetivo, aunque
     * se balancee o rodee (el problema es la deriva/orbitación, no la inmovilidad). Lleva un registro del
     * mejor avance real (distancia al objetivo) y, si tras un periodo no mejoró, rompe el bloque delante.
     * Rompe bloques "débiles" por defecto y obsidiana si su nivel lo permite ({@link #canBreakObsidian()}).
     */
    static class BreakBlockGoal extends Goal {
        private static final int BREAK_EVERY_TICKS = 60;      // evaluar romper cada 3 s
        private static final double IMPROVEMENT_THRESHOLD = 1.5D; // avance mínimo que cuenta como progreso
        private final AggressiveZombieEntity zombie;
        private BlockPos blockToBreak = null;
        private double anchorDist = Double.MAX_VALUE;   // distancia al inicio de medir
        private int evalTicks = 0;

        public BreakBlockGoal(AggressiveZombieEntity zombie) {
            this.zombie = zombie;
            // A PROPÓSITO sin flags: este goal es un observador pasivo que solo rompe bloques cuando detecta
            // que el zombie no progresa. NO navega ni mira, así que no debe pedir MOVE/LOOK; si los pidiera,
            // como se registra en prioridad 3 (más alta que la manada y que el ataque), bloquearía al
            // MeleeAttackGoal de forma permanente mientras haya objetivo y el zombie no atacaría nunca.
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
            anchorDist = distToTarget();
            evalTicks = 0;
            blockToBreak = null;
        }

        @Override
        public void tick() {
            evalTicks++;
            double dist = distToTarget();
            // Si el zombie se acercó de verdad (mejoró >= threshold desde el ancla), re-anclar: hay progreso.
            if (dist < anchorDist - IMPROVEMENT_THRESHOLD) {
                anchorDist = dist;
                evalTicks = 0;
                return;
            }
            // Si ya pasó el periodo sin mejorar lo suficiente, está orbitando o chocando: hay que abrirse paso.
            if (evalTicks >= BREAK_EVERY_TICKS) {
                Entity target = zombie.getTarget();
                boolean targetAbove = target != null && target.getY() > zombie.getY() + 1.0D;
                if (targetAbove) {
                    // El objetivo está ARRIBA: un túnel a su propia altura no le sirve para subir. Rompe un
                    // ESCALÓN: la columna de delante a la altura de la cabeza (y una más arriba), de modo que
                    // el obstáculo quede en un bloque de alto que SÍ puede saltar.
                    breakStepAhead(target);
                } else {
                    blockToBreak = blockingBlockAhead();
                    if (blockToBreak != null) {
                        breakBlock(blockToBreak);
                    }
                }
                // Re-anclar tras intentar romper.
                anchorDist = distToTarget();
                evalTicks = 0;
            }
        }

        /**
         * Abre un <b>escalón</b> hacia el objetivo cuando este está por encima: rompe los bloques de la
         * columna de delante a la altura de la cabeza y uno más arriba. Así un muro de 2–3 bloques queda
         * convertido en un escalón de 1 bloque, que el zombie puede saltar y luego repetir para seguir subiendo.
         */
        private void breakStepAhead(Entity target) {
            BlockPos zPos = zombie.blockPosition();
            int sx = Integer.signum((int) Math.floor(target.getX()) - zPos.getX());
            int sz = Integer.signum((int) Math.floor(target.getZ()) - zPos.getZ());
            // Columnas candidatas: primero las que van hacia el objetivo, y si está justo encima, las cuatro.
            int[][] dirs = (sx == 0 && sz == 0)
                    ? new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}
                    : new int[][]{{sx, 0}, {0, sz}, {sx, sz}};
            for (int[] d : dirs) {
                if (d[0] == 0 && d[1] == 0) continue;
                int px = zPos.getX() + d[0];
                int pz = zPos.getZ() + d[1];
                boolean broke = false;
                for (int dy = 1; dy <= 2; dy++) { // altura de la cabeza y uno más arriba
                    BlockPos p = new BlockPos(px, zPos.getY() + dy, pz);
                    if (canBreak(zombie.level().getBlockState(p))) {
                        zombie.breakBlockAt(p);
                        broke = true;
                    }
                }
                if (broke) {
                    return;
                }
            }
        }

        private double distToTarget() {
            Entity target = zombie.getTarget();
            return target == null ? Double.MAX_VALUE : zombie.distanceToSqr(target.position());
        }

        /**
         * Busca el bloque sólido rompible que más probablemente estorba el paso: mira posiciones alrededor
         * (a la altura del cuerpo y uno arriba, en las 4 direcciones) priorizando la ruta hacia el objetivo.
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
            return zombie.canBreakBlock(state);
        }

        private void breakBlock(BlockPos pos) {
            zombie.breakBlockAt(pos);
            // Abrir SIEMPRE el hueco de 2 de alto (cuerpo + cabeza). Antes solo se rompía el de arriba si el
            // objetivo estaba más alto, así que contra una valla de 2 bloques el zombie picaba el de abajo,
            // seguía sin caber y necesitaba otra ronda entera (3 s más) para el de arriba.
            BlockPos above = pos.above();
            if (zombie.canBreakBlock(zombie.level().getBlockState(above))) {
                zombie.breakBlockAt(above);
            }
            // Y si el objetivo está todavía más arriba, uno más: así el hueco queda en escalón de subida.
            Entity target = zombie.getTarget();
            if (target != null && target.getY() > zombie.getY() + 1.0D) {
                BlockPos higher = above.above();
                if (zombie.canBreakBlock(zombie.level().getBlockState(higher))) {
                    zombie.breakBlockAt(higher);
                }
            }
        }
    }

    /**
     * Goal: si el zombie no tiene objetivo de ataque y conoce el centro de la aldea, marcha hacia él.
     * Si está rodeando sin acercarse (bloqueado por el muro), rompe el bloque delante para entrar.
     * Al llegar (o encontrar un objetivo) el comportamiento normal retoma el control.
     */
    static class MoveToVillageCenterGoal extends Goal {
        // Se considera "llegado" al estar a 3 bloques de radio del centro (el goal deja de apuntar ahí).
        private static final double ARRIVE_DIST = 3.0D * 3.0D;
        private static final int BREAK_EVERY_TICKS = 60;
        private static final double IMPROVEMENT_THRESHOLD = 1.5D;
        private final AggressiveZombieEntity zombie;
        private double anchorDist = Double.MAX_VALUE;
        private int evalTicks = 0;

        public MoveToVillageCenterGoal(AggressiveZombieEntity zombie) {
            this.zombie = zombie;
            // Navega hasta el centro de la aldea: declara MOVE/LOOK para que su prioridad (7) sea real y no
            // pelee por la navegación con MeleeAttackGoal (5) ni con PatrolHomeGoal (8).
            this.setFlags(java.util.EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!zombie.isGoToCenterActive()) {
                return false; // la aldea cayó -> dejar de converger al centro
            }
            if (zombie.getHomePos() != null) {
                return false; // tiene un hogar (guarida): patrulla en su lugar
            }
            if (zombie.getTarget() != null) {
                return false; // ya tiene a quién atacar
            }
            BlockPos center = zombie.getVillageCenter();
            return center != null && distSqr(center) > ARRIVE_DIST;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            anchorDist = centerDistSqr();
            evalTicks = 0;
        }

        @Override
        public void tick() {
            BlockPos center = zombie.getVillageCenter();
            if (center == null) return;
            zombie.getNavigation().moveTo(center.getX(), center.getY(), center.getZ(), 1.0D);

            evalTicks++;
            double dist = centerDistSqr();
            if (dist < anchorDist - IMPROVEMENT_THRESHOLD) {
                anchorDist = dist;
                evalTicks = 0;
                return;
            }
            // Rodeando sin acercarse: romper el bloque delante (para atravesar el muro si hace falta).
            if (evalTicks >= BREAK_EVERY_TICKS) {
                zombie.breakBlockTowards(center);
                anchorDist = centerDistSqr();
                evalTicks = 0;
            }
        }

        private double distSqr(BlockPos center) {
            return zombie.distanceToSqr(center.getX(), center.getY(), center.getZ());
        }

        private double centerDistSqr() {
            BlockPos center = zombie.getVillageCenter();
            return center == null ? Double.MAX_VALUE : distSqr(center);
        }
    }

    /**
     * Goal: si el zombie tiene un <b>hogar</b> con radio (p. ej. el núcleo de su guarida), patrulla esa
     * zona: si se aleja más allá del radio vuelve hacia el hogar, y si está dentro y sin objetivo de ataque
     * deambula por los alrededores. Así los enemigos de una guarida no se quedan pegados al centro ni se
     * pierden por el mundo.
     */
    static class PatrolHomeGoal extends Goal {
        private final AggressiveZombieEntity zombie;
        private static final double SPEED = 1.0D;
        private static final int WANDER_INTERVAL_TICKS = 60; // cada 3 s elige un nuevo punto de ronda
        private int wanderTicks = 0;
        private double wanderX, wanderZ;

        public PatrolHomeGoal(AggressiveZombieEntity zombie) {
            this.zombie = zombie;
            // Navega (vuelve al hogar o ronda): declara MOVE/LOOK. Es el goal de menor prioridad (8), así que
            // con los flags cualquier goal de combate puede interrumpirlo, que es lo que se quiere.
            this.setFlags(java.util.EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            BlockPos home = zombie.getHomePos();
            if (home == null || zombie.getHomeRadius() <= 0) {
                return false;
            }
            // Fuera del radio -> siempre vuelve. Dentro -> patrulla solo si no está atacando.
            return outsideRadius(home) || zombie.getTarget() == null;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            wanderTicks = 0;
        }

        @Override
        public void tick() {
            BlockPos home = zombie.getHomePos();
            if (home == null) {
                return;
            }
            // 1) Si se alejó demasiado, volver al hogar.
            if (outsideRadius(home)) {
                zombie.getNavigation().moveTo(home.getX(), home.getY(), home.getZ(), SPEED);
                return;
            }
            // 2) Si tiene objetivo, el ataque manda (este goal no interfiere).
            if (zombie.getTarget() != null) {
                return;
            }
            // 3) Sin objetivo: rondar un punto aleatorio dentro del radio del hogar.
            if (--wanderTicks <= 0 || zombie.getNavigation().isDone()) {
                java.util.Random random = new java.util.Random();
                double angle = random.nextDouble() * Math.PI * 2.0;
                double dist = random.nextDouble() * zombie.getHomeRadius();
                wanderX = home.getX() + Math.cos(angle) * dist;
                wanderZ = home.getZ() + Math.sin(angle) * dist;
                wanderTicks = WANDER_INTERVAL_TICKS;
                zombie.getNavigation().moveTo(wanderX, home.getY(), wanderZ, SPEED);
            }
        }

        /** ¿Está el zombie más allá del radio de patrulla de su hogar? */
        private boolean outsideRadius(BlockPos home) {
            double r = zombie.getHomeRadius();
            return zombie.distanceToSqr(home.getX() + 0.5D, home.getY() + 0.5D, home.getZ() + 0.5D) > r * r;
        }
    }

    /**
     * Goal: comportamiento de MANADA. Cuando tiene un objetivo, cuenta cuántos otros zombies
     * <b>agresivos</b> del mismo bando están atacando a ese mismo objetivo y, en vez de ir todos en línea
     * recta (apilándose), cada uno persigue un <b>punto de flanqueo</b> alrededor del objetivo según su
     * "número de miembro". Así rodean al jugador desde ángulos distintos. Cuando están bien posicionados
     * (cerca del objetivo), el {@link MeleeAttackGoal} (mayor prioridad) toma el control y atacan.
     */
    static class HerdBehaviorGoal extends Goal {
        private final AggressiveZombieEntity zombie;
        private final double speed;
        private static final double RADIUS = 3.5D;            // distancia de flanqueo alrededor del objetivo
        private static final double ARRIVE_SQR = 2.5D * 2.5D; // considerar "posicionado" a < 2.5 bloques del flanco
        private Vec3 flankPoint = null;
        private int frameTicks = 0;

        public HerdBehaviorGoal(AggressiveZombieEntity zombie, double speed) {
            this.zombie = zombie;
            this.speed = speed;
            // CLAVE para que el comportamiento de manada funcione de verdad: este goal navega al punto de
            // flanqueo, así que pide MOVE/LOOK. Antes (sin flags) corría a la vez que el MeleeAttackGoal, y
            // como MeleeAttackGoal se registra DESPUÉS (prioridad 5) y vuelve a trazar ruta cada 4-11 ticks,
            // su ruta en línea recta pisaba la de flanqueo casi siempre: el rodeo quedaba anulado. Con MOVE,
            // el flanqueo (prioridad 4) manda mientras se coloca, y al llegar a su flanco canUse() pasa a
            // false, suelta el flag y el MeleeAttackGoal retoma el control y ataca: el relevo que documenta
            // el javadoc de esta clase.
            this.setFlags(java.util.EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = zombie.getTarget();
            if (target == null || !target.isAlive()) return false;
            // Solo mientras NO está bien posicionado; al estar cerca de su flanco, cede el control a MeleeAttack.
            return zombie.distanceToSqr(flankPointOr(target)) > ARRIVE_SQR;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            frameTicks = 0;
            recomputeFlankPoint();
        }

        @Override
        public void tick() {
            LivingEntity target = zombie.getTarget();
            if (target == null) return;
            if (frameTicks++ % 40 == 0) {
                recomputeFlankPoint();
            }
            if (flankPoint != null) {
                zombie.getNavigation().moveTo(flankPoint.x, flankPoint.y, flankPoint.z, speed);
            }
        }

        /** Punto de flanqueo, o la posición del objetivo como respaldo (para el cálculo de distancia). */
        private Vec3 flankPointOr(LivingEntity target) {
            if (flankPoint != null) return flankPoint;
            return target.position();
        }

        /**
         * Reparte a los zombies agresivos que atacan al MISMO objetivo en ángulos distintos alrededor de
         * él, usando un índice estable por UUID, para que se dispersen en círculo en vez de apilarse.
         */
        private void recomputeFlankPoint() {
            LivingEntity target = zombie.getTarget();
            if (target == null) {
                flankPoint = null;
                return;
            }
            java.util.List<AggressiveZombieEntity> pack = zombie.level().getEntitiesOfClass(
                    AggressiveZombieEntity.class, zombie.getBoundingBox().inflate(12.0D),
                    other -> other != zombie && other.getTarget() == target && other.isAlive());
            int packSize = pack.size() + 1; // + este zombie
            int index = Math.floorMod((int) zombie.getUUID().getLeastSignificantBits(), packSize);
            double slotAngle = (2.0 * Math.PI) * index / (double) Math.max(1, packSize);
            double angle = slotAngle;
            double tx = target.getX() + Math.cos(angle) * RADIUS;
            double tz = target.getZ() + Math.sin(angle) * RADIUS;
            flankPoint = new Vec3(tx, target.getY(), tz);
        }
    }

    // Clase interna para el comportamiento de lanzar fuego
    static class FireballAttackGoal extends Goal {
        /** Distancia MÍNIMA: si está pegado, que use el ataque cuerpo a cuerpo. */
        private static final double MIN_FIREBALL_RANGE = 3.0D;
        /** Distancia MÁXIMA al objetivo para lanzar fuego. */
        private static final double MAX_FIREBALL_RANGE = 16.0D;
        /** Reintento corto cuando no puede disparar, para hacerlo en cuanto el objetivo esté a tiro. */
        private static final int OUT_OF_RANGE_RETRY_TICKS = 20;
        /** Tiempo entre ataques cuando sí dispara. */
        private static final int ATTACK_INTERVAL_TICKS = 240;

        private final AggressiveZombieEntity zombie;
        private int attackTimer;

        public FireballAttackGoal(AggressiveZombieEntity zombie) {
            this.zombie = zombie;
            // A PROPÓSITO sin flags: es un ataque a distancia instantáneo que NO navega ni mira (dispara
            // usando posiciones y se apoya en el SmallFireball). Si pidiera MOVE/LOOK bloquearía el
            // movimiento del zombie mientras tenga objetivo, que es justo lo contrario de lo que se busca.
        }

        @Override
        public boolean canUse() {
            return zombie.getTarget() != null && zombie.getTarget().isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            // Sin esto, el Goal.canContinueToUse() por defecto (true) lo dejaba "corriendo" para siempre
            // incluso después de morir el objetivo.
            return canUse();
        }

        /**
         * ¿Está el objetivo a tiro? Hay que estar entre {@link #MIN_FIREBALL_RANGE} y
         * {@link #MAX_FIREBALL_RANGE} <b>y con línea de visión</b>. Antes no se comprobaba nada: el zombie
         * lanzaba bolas de fuego a cualquier distancia y a través de las paredes (el {@code d0 = 4.0} del código
         * original solo era la dispersión aleatoria del disparo, no un alcance).
         */
        private boolean isInFireballRange(Entity target) {
            double distanceSqr = zombie.distanceToSqr(target);
            if (distanceSqr < MIN_FIREBALL_RANGE * MIN_FIREBALL_RANGE
                    || distanceSqr > MAX_FIREBALL_RANGE * MAX_FIREBALL_RANGE) {
                return false;
            }
            return zombie.getSensing().hasLineOfSight(target);
        }

        @Override
        public void tick() {
            Entity target = zombie.getTarget();
            if (target == null) return;

            if (--attackTimer > 0) return;

            if (!isInFireballRange(target)) {
                // Reintenta pronto: así dispara en cuanto el objetivo entre en rango o deje de estar tras un
                // muro, en vez de esperar los 240 ticks completos sin haber disparado.
                attackTimer = OUT_OF_RANGE_RETRY_TICKS;
                return;
            }

            double d0 = 4.0D; // dispersión aleatoria del disparo
            double d1 = target.getX() - zombie.getX();
            double d2 = target.getY(0.5D) - zombie.getY(0.5D);
            double d3 = target.getZ() - zombie.getZ();
            SmallFireball fireball = new SmallFireball(zombie.level(), zombie, new Vec3(d1 + zombie.getRandom().nextGaussian() * d0, d2, d3 + zombie.getRandom().nextGaussian() * d0));
            fireball.setPos(fireball.getX(), zombie.getY(0.5D) + 0.5D, fireball.getZ());
            zombie.level().addFreshEntity(fireball);
            attackTimer = ATTACK_INTERVAL_TICKS; // Tiempo entre ataques
        }
    }

}
