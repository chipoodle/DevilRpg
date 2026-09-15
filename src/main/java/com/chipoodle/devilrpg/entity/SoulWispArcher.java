package com.chipoodle.devilrpg.entity;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapability;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.init.ModEntities;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;


public class SoulWispArcher extends SoulWisp implements RangedAttackMob {

    private static final float RADIUS = 0.15f;
    //private final RangedCreatureGoal<SoulWispArcherEntity> bowGoal = new RangedCreatureGoal<>(this, 1.0D, 20, 15.0F);

    // --- Pasivo "Ice spear volley" (skill wisp_ice_spear) -------------------------------------------
    /** Probabilidad (en %) POR PUNTO de que un disparo sea la andanada de lanzas de hielo. 5 puntos = 35%. */
    private static final int ICE_SPEAR_PROBABILITY_PER_POINT = 7;
    /** Cuántas lanzas lanza el poder especial, una detrás de otra. */
    private static final int ICE_SPEAR_COUNT = 3;
    /** Ticks entre una lanza y la siguiente: salen EN SUCESIÓN, como cohetes, no todas de golpe. */
    private static final int ICE_SPEAR_INTERVAL_TICKS = 7;
    /** Velocidad de salida (el guiado de {@link IceSpear} mantiene luego su velocidad de crucero). */
    private static final float ICE_SPEAR_LAUNCH_SPEED = 0.9F;
    /**
     * Desviación inicial de cada lanza. A propósito: salen algo torcidas y el guiado las va enderezando hacia
     * el enemigo, que es lo que da el efecto de "cohete corrigiendo la trayectoria".
     */
    private static final float ICE_SPEAR_INACCURACY = 5.0F;
    /**
     * Maná que le cuesta al dueño <b>cada lanza</b> de la andanada (medio punto). Si al dueño no le llega para
     * la siguiente, la andanada se corta ahí: no sale esa lanza ni las que quedaban. El disparo normal (bola de
     * escarcha) es gratis.
     */
    private static final float MANA_PER_ICE_SPEAR = 0.5F;

    /** Lanzas que faltan por salir de la andanada en curso (0 = no hay ninguna). */
    private int pendingIceSpears;
    /** Ticks que faltan para la siguiente lanza de la andanada. */
    private int nextIceSpearTicks;
    /** Puntos del dueño congelados al empezar la andanada (para no releerlos lanza a lanza). */
    private int pendingIceSpearPoints;
    /** A quién apuntaba la andanada; si muere, las lanzas que falten salen hacia donde mira el wisp. */
    @Nullable
    private LivingEntity iceSpearAim;

    public SoulWispArcher(EntityType<? extends SoulWispArcher> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        // super.registerGoals();

        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomFlyingGoal(this, 1.0D));
        this.goalSelector.addGoal(1, new RangedAttackGoal(this, 1.25D, 20, 10.0F));
        this.goalSelector.addGoal(5, new FollowOwnerGoal(this, 1.0D, 10.0F, 2.0F));
        this.goalSelector.addGoal(9, new SoulWisp.WanderGoal());
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Mob.class, 8.0F));
        this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Mob.class, 10, true, false, (entity) -> Math.abs(entity.getY() - this.getY()) <= 4.0D &&
                !(entity instanceof Villager) &&
                !(entity instanceof Llama) &&
                !(entity instanceof Turtle) &&
                !(entity instanceof IronGolem) &&
                // ANIMALES: el wisp NO va a por animales (vacas, cerdos, ovejas, gallinas, lobos, caballos...) por su
                // cuenta: son del jugador (granjas, mascotas, monturas) y el wisp los masacraba al pasar. Solo los
                // ataca si SU DUEÑO los está atacando (o el animal se ha enfadado con el dueño porque él le pegó).
                (!esAnimal(entity) || elDuenoLeEstaAtacando(entity))));
        this.targetSelector.addGoal(2, new HurtByTargetGoal(this));
    }

    /**
     * ¿Es un animal (pasivo o neutral)? Se dejan en paz salvo que el dueño esté metido en la pelea: {@link Animal}
     * cubre a los de tierra (vacas, cerdos, ovejas, lobos, caballos, zorros, osos polares...), {@link WaterAnimal} a
     * los del agua y {@link AmbientCreature} a los murciélagos.
     */
    private static boolean esAnimal(LivingEntity entity) {
        return entity instanceof Animal || entity instanceof WaterAnimal || entity instanceof AmbientCreature;
    }

    /**
     * ¿El dueño está atacando a <b>ese</b> bicho? Se mira el último al que atacó el jugador y también si el bicho le
     * tiene a él como su agresor (que es lo que pasa cuando le pegas a un lobo o a un oso polar): así el wisp ayuda en
     * la pelea que ha empezado el dueño, pero no la empieza él.
     */
    private boolean elDuenoLeEstaAtacando(LivingEntity entity) {
        if (!(this.getOwner() instanceof Player owner)) {
            return false; // un wisp sin dueño no tiene a quién ayudar: no toca a los animales
        }
        return owner.getLastHurtMob() == entity || entity.getLastHurtByMob() == owner;
    }

    @Override
    public void performRangedAttack(LivingEntity target, float p_82196_2_) {
        double d0 = target.getX() - this.getX();
        double d1 = target.getY(0.3333333333333333D) - this.getY();
        double d2 = target.getZ() - this.getZ();
        double d3 = Math.sqrt(d0 * d0 + d2 * d2);

        // Puntos del dueño (null-safe: un wisp sin dueño, p. ej. de huevo de spawn, dispara igual).
        HashMap<SkillEnum, Integer> skills = null;
        if (this.getOwner() instanceof Player owner) {
            PlayerSkillCapabilityInterface capability = IGenericCapability.getUnwrappedPlayerCapability(owner, PlayerSkillCapability.INSTANCE);
            if (capability != null) {
                skills = capability.getSkillsPoints();
            }
        }
        int archerPoints = skills == null ? 0 : skills.getOrDefault(SkillEnum.SUMMON_WISP_ARCHER, 0);
        int spearPoints = skills == null ? 0 : skills.getOrDefault(SkillEnum.WISP_ICE_SPEAR, 0);

        // PASIVO "Ice spear volley": con una probabilidad que sube con los puntos, el disparo normal se
        // convierte en una andanada de lanzas de hielo explosivas que salen en sucesión (ver aiStep()).
        if (this.pendingIceSpears > 0) {
            // Ya está saliendo la andanada: estos disparos SON el poder especial, no se suma la bola normal.
            return;
        }
        if (spearPoints > 0 && manaSuficiente()
                && this.getRandom().nextInt(100) < spearPoints * ICE_SPEAR_PROBABILITY_PER_POINT) {
            this.startIceSpearVolley(target, archerPoints);
            return;
        }

        var snowballEntity = new FrostBall(this.level(), this);
        snowballEntity.updateLevel(this, archerPoints);
        snowballEntity.shoot(d0, d1 + d3 * (double) 0.1F, d2, 1.6F, (float) (14 - this.level().getDifficulty().getId() * 4));
        this.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
        this.level().addFreshEntity(snowballEntity);
        //this.setHealth(this.getHealth() -1.5F);
    }

    /**
     * Arranca la andanada: la primera lanza sale ya y las demás van saliendo en {@link #aiStep()}, una cada
     * {@link #ICE_SPEAR_INTERVAL_TICKS} ticks. Así se ven salir en cadena y cada una corrigiendo su rumbo.
     */
    private void startIceSpearVolley(LivingEntity target, int archerPoints) {
        this.pendingIceSpears = ICE_SPEAR_COUNT;
        this.nextIceSpearTicks = 0;
        this.pendingIceSpearPoints = archerPoints;
        this.iceSpearAim = target;
        this.launchOneIceSpear();
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide && this.pendingIceSpears > 0 && --this.nextIceSpearTicks <= 0) {
            this.launchOneIceSpear();
        }
    }

    /** Saca la siguiente lanza de la andanada, apuntando al objetivo (o hacia delante si ya murió). */
    private void launchOneIceSpear() {
        // PRIMERO el maná: cada lanza le cuesta MANA_PER_ICE_SPEAR al dueño. Si no le llega, la andanada se corta
        // AQUÍ (no sale esta lanza ni las que quedaban), que es justo lo que se pidió.
        if (!cobrarManaAlDueno()) {
            DevilRpg.LOGGER.info("[WispArcher] al dueno no le queda mana: la andanada se corta (quedaban {} lanza(s))",
                    this.pendingIceSpears);
            this.pendingIceSpears = 0;
            this.iceSpearAim = null;
            return;
        }
        this.pendingIceSpears--;
        this.nextIceSpearTicks = ICE_SPEAR_INTERVAL_TICKS;
        LivingEntity aim = this.iceSpearAim;
        double dx;
        double dy;
        double dz;
        if (aim != null && aim.isAlive()) {
            dx = aim.getX() - this.getX();
            dy = aim.getY(0.3333333333333333D) - this.getY();
            dz = aim.getZ() - this.getZ();
        } else {
            // El objetivo murió entre lanza y lanza: sale hacia delante y el guiado busca otro enemigo.
            Vec3 look = this.getLookAngle();
            dx = look.x * 10.0D;
            dy = look.y * 10.0D;
            dz = look.z * 10.0D;
        }
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        IceSpear spear = new IceSpear(this.level(), this);
        spear.updateLevel(this, this.pendingIceSpearPoints);
        spear.shoot(dx, dy + horizontal * (double) 0.12F, dz, ICE_SPEAR_LAUNCH_SPEED, ICE_SPEAR_INACCURACY);
        this.level().addFreshEntity(spear);
        this.playSound(SoundEvents.SNOW_GOLEM_SHOOT, 1.0F, 0.75F);
    }

    public void updateLevel(Player owner) {
        super.updateLevel(owner, null, null, SkillEnum.SUMMON_WISP_ARCHER, true);
    }

    /**
     * ¿Le llega al dueño para al menos una lanza? No cobra nada: el cobro es por lanza, al dispararla. Si no le
     * llega, ni se empieza la andanada (así el wisp no pierde el ataque: dispara la bola de escarcha normal).
     */
    private boolean manaSuficiente() {
        if (!(this.getOwner() instanceof Player owner)) {
            return true; // sin dueño (p. ej. huevo de spawn): no se cobra a nadie
        }
        PlayerManaCapabilityInterface mana = IGenericCapability.getUnwrappedPlayerCapability(owner, PlayerManaCapability.INSTANCE);
        return mana == null || mana.getMana() >= MANA_PER_ICE_SPEAR;
    }

    /**
     * Le cobra al dueño el maná de una lanza. Devuelve {@code false} <b>sin cobrar nada</b> si no le llega, y el
     * que llama corta la andanada. Un wisp sin dueño no cobra a nadie y dispara igual.
     */
    private boolean cobrarManaAlDueno() {
        if (!(this.getOwner() instanceof Player owner)) {
            return true;
        }
        PlayerManaCapabilityInterface mana = IGenericCapability.getUnwrappedPlayerCapability(owner, PlayerManaCapability.INSTANCE);
        if (mana == null) {
            return true;
        }
        if (mana.getMana() < MANA_PER_ICE_SPEAR) {
            return false;
        }
        mana.addMana(-MANA_PER_ICE_SPEAR, owner); // addMana recorta a [0, max] y sincroniza con el cliente
        return true;
    }

    @Nullable
    @Override
    public SoulWispArcher getBreedOffspring(@NotNull ServerLevel level, @NotNull AgeableMob ageableMob) {
        return ModEntities.WISP_ARCHER.get().create(level);
    }
}
