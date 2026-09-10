package com.chipoodle.devilrpg.entity;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.spawnprofile.AggressiveZombieSpawnProfile;
import com.chipoodle.devilrpg.spawnprofile.SpawnScaleProfile;
import com.chipoodle.devilrpg.survival.LairManager;
import com.chipoodle.devilrpg.survival.ThreatLevel;
import com.chipoodle.devilrpg.world.LairGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
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
public class SculkCultivatorEntity extends AbstractIllager implements RangedAttackMob {

    private static final SpawnScaleProfile SPAWN_PROFILE = AggressiveZombieSpawnProfile.INSTANCE;

    /** Radio en el que el cultivador trabaja (respecto al núcleo de su guarida). */
    private static final int WORK_RADIUS = 24;
    /** Bloques de sculk necesarios para "condensar" un catalizador nuevo. */
    private static final int SCULK_PER_CATALYST = 22;
    /**
     * Máximo de catalizadores que mantiene por guarida. Cada catalizador es un foco de expansión
     * independiente (en vanilla <b>nunca</b> se crean solos: el sculk solo genera sensores y chilladores), así
     * que este número es el que decide cuánto puede seguir creciendo la mancha. Con 6 se quedaba corta: la
     * guarida ya nace con 6 (4 en el santuario + 2 en el corral), así que el guardián solo podía poner 2 más.
     */
    private static final int MAX_CATALYSTS = 12;
    /** Hasta cuántos bloques por debajo del núcleo se cuenta/mira (el fondo del foso del corral está a 3). */
    private static final int SCAN_BELOW = 4;
    /** Capas hacia arriba y hacia abajo que revisa al buscar dónde plantar (para que la mancha trepe y baje). */
    private static final int PLANT_LAYERS_UP = 3;
    private static final int PLANT_LAYERS_DOWN = 5;
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

    /**
     * Cada cuántos ticks lanza una poción. La bruja vanilla usa 60 (3 s) con radio 10, así que el doble de
     * recarga (6 s) lo deja claramente más débil, como se pidió.
     */
    private static final int POTION_ATTACK_INTERVAL = 120;
    /** Radio desde el que lanza (el mismo que la bruja). Dentro de él se para y dispara. */
    private static final float POTION_ATTACK_RADIUS = 10.0F;

    private double spawnDistance = 0;
    private double spawnThreat = 1.0;
    private boolean attributesAdjusted = false;

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
                // Alcance de detección ACOTADO (la bruja usa 16): es el guardián de su guarida y no debe
                // perseguir al jugador por medio mapa. Con 32 defiende toda la plataforma de la guarida
                // (~31 bloques) y se queda en ella.
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    /** No se une a los raids vanilla: solo sirve a su guarida. */
    @Override
    public boolean canJoinRaid() {
        return false;
    }

    /**
     * El guardián <b>no desaparece por distancia</b>. Si pudiera despawnear, la guarida se quedaría sin nadie
     * a quien matar, el sello no tendría forma de caer (salvo la red de seguridad por tiempo) y el jugador se
     * encontraría el núcleo indefenso sin haber matado a nadie.
     */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    /**
     * Guarda a qué guarida pertenece el guardián. <b>Es imprescindible</b>: al morir, el cultivador avisa a su
     * guarida con ese dato para que caiga el sello. Sin guardarlo, un guardián que sobrevive a un reinicio de
     * la partida se queda sin "hogar", muere sin avisar y <b>el sello nunca se rompe</b> (el núcleo queda
     * inaccesible con el guardián ya muerto).
     */
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (homePos != null) {
            tag.put("DevilRpgLairCore", NbtUtils.writeBlockPos(homePos));
            tag.putInt("DevilRpgLairRadius", homeRadius);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        NbtUtils.readBlockPos(tag, "DevilRpgLairCore").ifPresent(pos -> homePos = pos);
        homeRadius = tag.getInt("DevilRpgLairRadius");
    }

    /**
     * Avisa a su guarida cuando el guardián <b>muere de verdad</b>: es lo único que rompe el sello. Se hace
     * aquí y no contando cultivadores vivos, porque su ausencia también significa "todavía no ha aparecido" o
     * "se ha ido", y con esa deducción el sello caía solo.
     */
    @Override
    public void die(DamageSource cause) {
        BlockPos lair = homePos; // se captura antes de super.die(), que puede limpiar el estado
        BlockPos deathPos = blockPosition();
        super.die(cause);
        if (!level().isClientSide && level() instanceof ServerLevel serverLevel) {
            // Se avisa también si no tiene hogar: LairManager busca entonces la guarida más cercana.
            LairManager.onGuardianKilled(serverLevel, lair, deathPos);
        }
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
        // Con objetivo va "conjurando" (brazos al frente, como el Invocador al lanzar); sin él, tranquilo.
        return getTarget() != null ? IllagerArmPose.SPELLCASTING : IllagerArmPose.CROSSED;
    }

    /**
     * Lanza una <b>poción salpicada</b> al objetivo, como la bruja, pero <b>más débil</b>: mismo radio, el
     * doble de recarga (ver {@link #POTION_ATTACK_INTERVAL}) y sin las variedades fuertes que usa la bruja
     * (nada de daño fuerte ni veneno), así que solo puede hacer daño 6 de vez en cuando.
     */
    @Override
    public void performRangedAttack(LivingEntity target, float distanceFactor) {
        Vec3 velocity = target.getDeltaMovement();
        double dx = target.getX() + velocity.x - getX();
        double dy = target.getEyeY() - 1.1D - getY();
        double dz = target.getZ() + velocity.z - getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        Holder<Potion> potion = Potions.HARMING;
        if (horizontal >= 8.0D && !target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
            potion = Potions.SLOWNESS; // de lejos frena, para poder seguir a distancia
        } else if (horizontal <= 3.0D && !target.hasEffect(MobEffects.WEAKNESS)) {
            potion = Potions.WEAKNESS; // de cerca debilita, que es su defensa
        }
        ThrownPotion thrown = new ThrownPotion(level(), this);
        thrown.setItem(PotionContents.createItemStack(Items.SPLASH_POTION, potion));
        thrown.setXRot(thrown.getXRot() - -20.0F);
        thrown.shoot(dx, dy + horizontal * 0.2D, dz, 0.75F, 8.0F);
        if (!isSilent()) {
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.WITCH_THROW, getSoundSource(), 1.0F,
                    0.8F + random.nextFloat() * 0.4F);
        }
        level().addFreshEntity(thrown);
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
        // El guardián pelea como una BRUJA debilitada: se queda a distancia (10 bloques, como la bruja) y
        // lanza pociones salpicadas, con el DOBLE de recarga que ella. Ya no huye: antes se alejaba demasiado
        // y el asalto se convertía en perseguirlo por medio mapa.
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new RangedAttackGoal(this, 1.0D, POTION_ATTACK_INTERVAL, POTION_ATTACK_RADIUS));
        // Tareas de cultivador (su "trabajo", cuando no tiene a quién lanzar).
        this.goalSelector.addGoal(2, new SacrificeGoal(this));
        this.goalSelector.addGoal(3, new BreedAnimalsGoal(this));
        this.goalSelector.addGoal(4, new PlantCatalystGoal(this));
        // ...y la otra mitad: abrir de verdad la puerta del corral cuando se topa con ella yendo a trabajar.
        // (OpenDoorGoal no declara flags: solo abre puertas, no navega, así que convive con el goal de turno.)
        this.goalSelector.addGoal(5, new OpenDoorGoal(this, true));
        // Patrullar el radio de su guarida cuando no tiene nada que hacer.
        this.goalSelector.addGoal(8, new PatrolHomeGoal(this));
        // Objetivos: jugadores e invocaciones con dueño. Los goals vanilla ya ignoran a los jugadores en
        // creativo/espectador, así que se puede seguir observándolo trabajar.
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
                (target) -> isOwnedMinion(target)));
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
     * <p>
     * El barrido baja hasta {@link #SCAN_BELOW} bloques por debajo del núcleo a propósito: el corral es un
     * foso y sus catalizadores están en el fondo, tres bloques más abajo. Con la ventana anterior (solo −2..+2)
     * esos catalizadores <b>no se contaban</b>, así que el guardián creía tener menos de los que hay.
     */
    private int[] scanInfection() {
        BlockPos c = workCenter();
        int r = workRadius();
        int sculk = 0;
        int catalysts = 0;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = -SCAN_BELOW; y <= 2; y++) {
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
        /** Último punto al que no consiguió llegar, para no volver a elegirlo y quedarse en bucle. */
        private BlockPos lastFailed = null;

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
            lastFailed = null; // se pudo: que vuelva a considerar cualquier punto
            spot = null;
        }

        @Override
        public void stop() {
            // Si se rinde por no poder llegar, apunta el punto para no volver a elegirlo y quedarse en bucle.
            if (spot != null && reachTicks > REACH_TIMEOUT_TICKS) {
                lastFailed = spot;
            }
            spot = null;
            reachTicks = 0;
        }

        /**
         * Busca dónde plantar el catalizador nuevo: un hueco de aire con suelo sólido debajo y que toque sculk.
         * <p>
         * Se revisan <b>varias capas</b> (de {@link #PLANT_LAYERS_UP} por encima del núcleo a
         * {@link #PLANT_LAYERS_DOWN} por debajo) y de arriba hacia abajo: antes solo se miraba la capa del
         * suelo del núcleo, así que la mancha no podía trepar por un desnivel ni bajar al fondo del foso del
         * corral. Se empieza por arriba para que primero avance por la superficie y, cuando ya no queden
         * huecos ahí, siga por las capas de abajo.
         */
        private BlockPos findEdgeSpot() {
            BlockPos c = cult.workCenter();
            int r = cult.workRadius();
            for (int dy = PLANT_LAYERS_UP; dy >= -PLANT_LAYERS_DOWN; dy--) {
                for (int x = -r; x <= r; x++) {
                    for (int z = -r; z <= r; z++) {
                        BlockPos above = new BlockPos(c.getX() + x, c.getY() + dy, c.getZ() + z);
                        if (above.equals(lastFailed)) continue; // ese ya no se pudo alcanzar: no repetir
                        BlockPos ground = above.below();
                        if (!cult.level().getBlockState(above).isAir()) continue;
                        if (!cult.level().getBlockState(ground).isSolid()) continue;
                        if (cult.level().getBlockState(ground).is(Blocks.SCULK_CATALYST)) continue;
                        if (touchesSculk(above)) {
                            return above;
                        }
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
