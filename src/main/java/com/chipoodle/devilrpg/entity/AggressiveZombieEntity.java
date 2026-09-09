package com.chipoodle.devilrpg.entity;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.spawnprofile.AggressiveZombieSpawnProfile;
import com.chipoodle.devilrpg.spawnprofile.SpawnScaleProfile;
import com.chipoodle.devilrpg.survival.ThreatLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
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

    /** ¿Puede romper este bloque? (nunca bedrock; obsidiana solo si su nivel lo permite). */
    private boolean canBreakBlock(BlockState state) {
        Block b = state.getBlock();
        if (b == Blocks.BEDROCK || b == Blocks.WATER || b == Blocks.LAVA || b == Blocks.AIR) {
            return false;
        }
        if (b == Blocks.OBSIDIAN || b == Blocks.CRYING_OBSIDIAN) {
            return canBreakObsidian();
        }
        return true; // madera, tierra, grava, arena, lana...
    }

    /** Rompe el bloque en la posición, respetando el límite de obsidiana. */
    private void breakBlockAt(BlockPos pos) {
        BlockState bs = level().getBlockState(pos);
        Block b = bs.getBlock();
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
        this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.2D, false)); // Ataque cuerpo a cuerpo más rápido
        this.goalSelector.addGoal(5, new FireballAttackGoal(this)); // Lanzar fuego como los Blaze
        // Si no hay objetivo, marchar hacia el centro de la aldea (para no merodear fuera).
        this.goalSelector.addGoal(6, new MoveToVillageCenterGoal(this));
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
     * Goal: si el zombie está en el agua y atascado (no progresa hacia su objetivo ni sale del agua),
     * buscar la orilla (bloque de tierra) más cercana y nadar hacia ella para desatascarse. Se activa con
     * prioridad alta, antes que romper o atacar, para que no se quede saltando en el agua fuera de la aldea.
     */
    static class EscapeWaterGoal extends Goal {
        private final AggressiveZombieEntity zombie;
        private BlockPos shore = null;
        private int stuckTicks = 0;
        private int jumpCooldown = 0;
        private double lastX, lastZ;

        public EscapeWaterGoal(AggressiveZombieEntity zombie) {
            this.zombie = zombie;
        }

        @Override
        public boolean canUse() {
            return zombie.isInWater() && zombie.isInWaterOrRain() && isReallyStuck();
        }

        @Override
        public boolean canContinueToUse() {
            return zombie.isInWater() && zombie.isInWaterOrRain();
        }

        @Override
        public void start() {
            stuckTicks = 0;
            jumpCooldown = 0;
            lastX = zombie.getX();
            lastZ = zombie.getZ();
            shore = findNearestShore();
            if (shore != null) {
                // Nadar hacia la orilla (el pathfinding navega por el agua hacia el bloque de tierra).
                zombie.getNavigation().moveTo(shore.getX(), shore.getY(), shore.getZ(), 1.2D);
            }
        }

        @Override
        public void tick() {
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
            // Saltar hacia la orilla si está en el agua y hay un bloque de tierra un nivel arriba delante
            // (el desnivel de 1 bloque que no puede trepar desde el agua). Cada ~20 ticks para no saltar sin parar.
            if (zombie.isInWater() && shore != null && --jumpCooldown <= 0) {
                if (jumpTowardShore()) {
                    jumpCooldown = 20;
                } else {
                    jumpCooldown = 5;
                }
            }
        }

        /**
         * Si hay un bloque de tierra justo un nivel por encima de la orilla a la que nada, da un impulso
         * vertical y horizontal para trepar el escalón (y no quedarse flotando en el borde).
         * Devuelve {@code true} si saltó.
         */
        private boolean jumpTowardShore() {
            BlockPos zPos = zombie.blockPosition();
            int sx = Integer.signum(shore.getX() - zPos.getX());
            int sz = Integer.signum(shore.getZ() - zPos.getZ());
            // Bloque delante, a la altura de la cabeza (1 arriba) — el escalón de tierra a trepar.
            BlockPos step = new BlockPos(zPos.getX() + sx, zPos.getY() + 1, zPos.getZ() + sz);
            BlockState stepBlock = zombie.level().getBlockState(step);
            if (!stepBlock.isAir() && stepBlock.isSolid() && !zombie.level().getFluidState(step).is(FluidTags.WATER)) {
                zombie.setDeltaMovement(sx * 0.35D, 0.42D, sz * 0.35D);
                return true;
            }
            return false;
        }

        /** ¿Realmente atascado? (en agua y sin avanzar; se usa como señal de arranque). */
        private boolean isReallyStuck() {
            return true; // Si está en el agua y lejos de tierra, siempre intenta salir.
        }

        /** Busca el bloque de tierra (no agua) más cercano dentro de un radio, escaneando en espiral. */
        private BlockPos findNearestShore() {
            BlockPos pos = zombie.blockPosition();
            int r = 0;
            while (r <= 16) {
                for (int x = -r; x <= r; x++) {
                    for (int z = -r; z <= r; z++) {
                        if (Math.abs(x) != r && Math.abs(z) != r) continue; // borde del cuadrado
                        BlockPos cand = new BlockPos(pos.getX() + x, pos.getY(), pos.getZ() + z);
                        BlockPos ground = new BlockPos(cand.getX(), cand.getY() - 1, cand.getZ());
                        if (isLand(ground)) {
                            return ground.above();
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
            // Si ya pasó el periodo sin mejorar lo suficiente, está orbitando: romper el bloque delante.
            if (evalTicks >= BREAK_EVERY_TICKS) {
                blockToBreak = blockingBlockAhead();
                if (blockToBreak != null) {
                    breakBlock(blockToBreak);
                }
                // Re-anclar tras intentar romper.
                anchorDist = distToTarget();
                evalTicks = 0;
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
            // Si el objetivo está arriba, romper también el bloque +2 para abrir espacio de salto.
            Entity target = zombie.getTarget();
            if (target != null && target.getY() > zombie.getY()) {
                BlockPos above = pos.above();
                if (zombie.canBreakBlock(zombie.level().getBlockState(above))) {
                    zombie.breakBlockAt(above);
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
        private static final double ARRIVE_DIST = 6.0D * 6.0D;
        private static final int BREAK_EVERY_TICKS = 60;
        private static final double IMPROVEMENT_THRESHOLD = 1.5D;
        private final AggressiveZombieEntity zombie;
        private double anchorDist = Double.MAX_VALUE;
        private int evalTicks = 0;

        public MoveToVillageCenterGoal(AggressiveZombieEntity zombie) {
            this.zombie = zombie;
        }

        @Override
        public boolean canUse() {
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
