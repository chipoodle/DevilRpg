package com.chipoodle.devilrpg.entity;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.spawnprofile.SpawnScaleProfile;
import com.chipoodle.devilrpg.spawnprofile.VexSpawnProfile;
import com.chipoodle.devilrpg.survival.ThreatLevel;
import com.chipoodle.devilrpg.survival.VeteranGrowth;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Vex "helado" del mod: copia del vex vanilla (vuela, ataca cuerpo a cuerpo y puede spawnear de día) pero
 * con dos añadidos:
 * <ul>
 *   <li><b>Ataque a distancia</b>: lanza una {@link FrostBall} (la misma bola de hielo del wisp), con un
 *       enfriamiento. El ciclo natural es: lanza la bola → entra en cooldown → ataca melee (se acerca y se
 *       aleja volando) → al estar de nuevo a distancia el cooldown ya terminó y vuelve a lanzar.</li>
 *   <li><b>Escalado</b>: sus atributos (vida/velocidad/daño) y su XP escalan por distancia al punto de
 *       inicio + amenaza global, igual que el {@code AggressiveZombieEntity}.</li>
 * </ul>
 */
public class FrostVexEntity extends Vex {

    private static final SpawnScaleProfile SPAWN_PROFILE = VexSpawnProfile.INSTANCE;

    /** Tiempo de enfriamiento entre bolas de hielo (4 s). */
    private static final int SNOWBALL_COOLDOWN_TICKS = 80;
    /** Rango en el que lanza la bola (ni pegado ni demasiado lejos). */
    private static final double SNOWBALL_MIN_RANGE = 3.0;
    private static final double SNOWBALL_MAX_RANGE = 20.0;

    private double spawnDistance = 0;   // distancia al punto de inicio al spawnear
    private double spawnThreat = 1.0;   // amenaza global al spawnear
    private boolean attributesAdjusted = false;
    private int snowballCooldown = 0;

    /**
     * Progreso hacia el siguiente rango de "veterano" (ticks vivo + bajas) y rango actual. Ver
     * {@link VeteranGrowth}: el vex que sobrevive se fortalece solo, y el rango se guarda en NBT.
     */
    private int veteranProgress = 0;
    private int veteranRank = 0;

    public FrostVexEntity(EntityType<? extends Vex> type, Level level) {
        super(type, level);
    }

    /** Atributos base tomados del perfil del vex (valores reducidos a un tercio). */
    public static AttributeSupplier.Builder setAttributes() {
        SpawnScaleProfile p = SPAWN_PROFILE;
        return Vex.createAttributes()
                .add(Attributes.MAX_HEALTH, p.baseHealth())
                .add(Attributes.MOVEMENT_SPEED, p.baseSpeed())
                .add(Attributes.ATTACK_DAMAGE, p.baseDamage())
                // Tamaño: lo usan los "veteranos" para crecer a la vista (ver VeteranGrowth).
                .add(Attributes.SCALE, 1.0D);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals(); // hereda los del vex (incluye el ataque de carga melee)
        // Lanzar la bola de hielo antes del ataque de carga: así alterna bola -> melee -> bola.
        this.goalSelector.addGoal(3, new LaunchSnowballGoal(this));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (snowballCooldown > 0) {
            snowballCooldown--;
        }
        // Si el jugador que perseguía pasó a creativo/espectador, se suelta el objetivo en el acto: no se
        // ataca a quien no puede ser dañado. (setTarget ya impide adquirirlo; esto cubre el cambio a mitad.)
        if (getTarget() != null && !getTarget().canBeSeenAsEnemy()) {
            setTarget(null);
        }
        if (!attributesAdjusted) {
            adjustAttributesBasedOnSpawnDistance(false);
            attributesAdjusted = true;
        }
        // "Se fortalecen con el tiempo": el que sobrevive crece (ver VeteranGrowth).
        tickVeteranGrowth();
    }

    /**
     * Nunca acepta como objetivo a un jugador en <b>creativo o espectador</b> (ni a una entidad inmune).
     * <p>
     * Los goals vanilla ya filtran eso por su cuenta vía {@code canBeSeenAsEnemy()}, pero el mod asigna el
     * objetivo <b>a mano</b> al spawnear el vex ({@code VexSpawnRule} y {@code LairManager.spawnOne}), y eso
     * se salta el filtro: sin esta guarda, los vexes perseguían y atacaban a un jugador en creativo.
     */
    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (target != null && !target.canBeSeenAsEnemy()) {
            return;
        }
        super.setTarget(target);
    }

    @Override
    public void setPos(double x, double y, double z) {
        super.setPos(x, y, z);
        if (spawnDistance == 0) { // solo al spawnear
            Player nearestPlayer = this.level().getNearestPlayer(this, 128.0D);
            if (nearestPlayer != null) {
                PlayerAuxiliaryCapabilityInterface cap = IGenericCapability.getUnwrappedPlayerCapability(nearestPlayer, PlayerAuxiliaryCapability.INSTANCE);
                Vec3 anchor = cap == null ? null : cap.getAnchorPoint();
                if (anchor == null && cap != null) {
                    anchor = cap.getSpawnPoint();
                }
                if (anchor != null) {
                    spawnDistance = Math.sqrt(this.blockPosition().distSqr(
                            new BlockPos((int) anchor.x, (int) anchor.y, (int) anchor.z)));
                    spawnThreat = ThreatLevel.current(this.level());
                    DevilRpg.LOGGER.info("FrostVex spawned at: {} | anchor: {} | distance: {} | threat: {}",
                            this.blockPosition(), anchor, spawnDistance, String.format("%.2f", spawnThreat));
                }
            }
        }
    }

    /**
     * Escala vida/velocidad/daño por distancia y amenaza (misma fórmula que el zombie agresivo) y, encima, por
     * los rangos de {@link VeteranGrowth} que haya ganado sobreviviendo. Es idempotente: siempre parte de las
     * bases del perfil, así que se puede llamar otra vez al subir de rango sin apilar multiplicadores.
     *
     * @param healGainedHealth si además hay que curarle la vida que acaba de ganar con el rango (en el spawn
     *                         no hace falta: el vex ya se pone a tope de vida).
     */
    private void adjustAttributesBasedOnSpawnDistance(boolean healGainedHealth) {
        double scaleFactor = SPAWN_PROFILE.scaleFactor(spawnDistance, spawnThreat)
                * (1.0 + spawnThreat * ThreatLevel.MAX_EXTRA_DIFFICULTY);

        float maxHealthBefore = this.getMaxHealth();

        Objects.requireNonNull(this.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(
                SPAWN_PROFILE.baseHealth() * scaleFactor * VeteranGrowth.multiplier(veteranRank, VeteranGrowth.HEALTH_PER_RANK));
        Objects.requireNonNull(this.getAttribute(Attributes.MOVEMENT_SPEED)).setBaseValue(
                SPAWN_PROFILE.baseSpeed() * scaleFactor * VeteranGrowth.multiplier(veteranRank, VeteranGrowth.SPEED_PER_RANK));
        Objects.requireNonNull(this.getAttribute(Attributes.ATTACK_DAMAGE)).setBaseValue(
                SPAWN_PROFILE.baseDamage() * scaleFactor * VeteranGrowth.multiplier(veteranRank, VeteranGrowth.DAMAGE_PER_RANK));
        AttributeInstance scaleAttribute = this.getAttribute(Attributes.SCALE);
        if (scaleAttribute != null) {
            scaleAttribute.setBaseValue(VeteranGrowth.multiplier(veteranRank, VeteranGrowth.SCALE_PER_RANK));
        }

        float maxHealthAfter = this.getMaxHealth();
        if (healGainedHealth && maxHealthAfter > maxHealthBefore) {
            this.setHealth(Math.min(maxHealthAfter, this.getHealth() + (maxHealthAfter - maxHealthBefore)));
        } else {
            this.setHealth(this.getMaxHealth());
        }
    }

    /**
     * Cuenta el tiempo vivo (y las bajas) y sube de rango cuando toca. Solo en el servidor y con el vex vivo:
     * el progreso se acumula únicamente mientras la entidad está cargada (o sea, con alguien cerca).
     */
    private void tickVeteranGrowth() {
        if (this.level().isClientSide || !this.isAlive()) {
            return;
        }
        this.veteranProgress++;
        int rank = VeteranGrowth.rankFor(this.veteranProgress);
        if (rank > this.veteranRank) {
            this.veteranRank = rank;
            adjustAttributesBasedOnSpawnDistance(true);
            announceVeteranRankUp(rank);
        }
    }

    /** Aviso visible de la subida de rango (partículas + chillido) para que el jugador lo note. */
    private void announceVeteranRankUp(int rank) {
        if (!(this.level() instanceof ServerLevel server)) {
            return;
        }
        server.sendParticles(ParticleTypes.ANGRY_VILLAGER, this.getX(), this.getY() + 0.8D, this.getZ(),
                12, 0.4D, 0.4D, 0.4D, 0.02D);
        server.playSound(null, this.blockPosition(), SoundEvents.VEX_CHARGE, SoundSource.HOSTILE, 0.9F, 0.7F);
        DevilRpg.LOGGER.info("[Veterano] Vex helado sube a rango {} ({}) en {}",
                rank, VeteranGrowth.rankName(rank), this.blockPosition());
    }

    /** Cada baja que hace el vex le adelanta el reloj: los que han matado se vuelven veteranos antes. */
    @Override
    public boolean killedEntity(ServerLevel level, LivingEntity killed) {
        boolean result = super.killedEntity(level, killed);
        this.veteranProgress += VeteranGrowth.TICKS_PER_KILL;
        return result;
    }

    /** XP escalada por distancia + amenaza (como el zombie agresivo) y por rango de veterano. */
    @Override
    protected int getBaseExperienceReward() {
        int base = SPAWN_PROFILE.experienceReward(spawnDistance, spawnThreat);
        return (int) Math.round(base * VeteranGrowth.multiplier(veteranRank, VeteranGrowth.XP_PER_RANK));
    }

    /** El rango y su progreso viajan con el vex: un veterano que te sobrevive sigue siéndolo al volver. */
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("DevilRpgVeteranProgress", this.veteranProgress);
        tag.putInt("DevilRpgVeteranRank", this.veteranRank);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.veteranProgress = tag.getInt("DevilRpgVeteranProgress");
        this.veteranRank = Math.min(VeteranGrowth.MAX_RANK, tag.getInt("DevilRpgVeteranRank"));
    }

    public double getSpawnDistance() {
        return spawnDistance;
    }

    public int getSnowballCooldown() {
        return snowballCooldown;
    }

    public void setSnowballCooldown(int ticks) {
        this.snowballCooldown = ticks;
    }

    /**
     * Goal: lanzar una bola de hielo al objetivo cuando está a distancia y el cooldown terminó. Es un
     * ataque instantáneo (no bloquea el movimiento), así el vex puede seguir cargando cuerpo a cuerpo.
     */
    static class LaunchSnowballGoal extends Goal {
        private final FrostVexEntity vex;

        public LaunchSnowballGoal(FrostVexEntity vex) {
            this.vex = vex;
        }

        @Override
        public boolean canUse() {
            LivingEntity target = vex.getTarget();
            if (target == null || !target.isAlive() || vex.getSnowballCooldown() > 0) {
                return false;
            }
            double dist = vex.distanceTo(target);
            return dist >= SNOWBALL_MIN_RANGE && dist <= SNOWBALL_MAX_RANGE;
        }

        @Override
        public boolean canContinueToUse() {
            return false; // disparo instantáneo
        }

        @Override
        public void start() {
            LivingEntity target = vex.getTarget();
            if (target == null) {
                return;
            }
            FrostBall ball = new FrostBall(vex.level(), vex);
            // Poder de la bola escalado con el "nivel" del vex (su distancia de spawn).
            int power = (int) Math.min(30.0, vex.getSpawnDistance() / 100.0);
            ball.updateLevel(vex, power);
            double dx = target.getX() - vex.getX();
            double dy = target.getY(0.3333333333333333D) - vex.getY();
            double dz = target.getZ() - vex.getZ();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            ball.shoot(dx, dy + horizontal * 0.1D, dz, 1.6F, 4.0F);
            vex.playSound(SoundEvents.SNOWBALL_THROW, 1.0F, 1.0F / (vex.getRandom().nextFloat() * 0.4F + 0.8F));
            vex.level().addFreshEntity(ball);
            vex.setSnowballCooldown(SNOWBALL_COOLDOWN_TICKS);
        }
    }
}
