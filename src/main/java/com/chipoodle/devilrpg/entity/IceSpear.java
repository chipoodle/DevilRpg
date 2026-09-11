package com.chipoodle.devilrpg.entity;

import com.chipoodle.devilrpg.init.ModEntities;
import com.chipoodle.devilrpg.init.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * <b>Lanza de hielo</b> del pasivo del wisp arquero ({@code wisp_ice_spear}).
 * <p>
 * Es la "bola de hielo grande": hereda de {@link FrostBall} (mismo daño directo, misma lentitud y mismas
 * partículas de hielo), pero además:
 * <ul>
 *   <li><b>Persigue</b> al enemigo: cada tick corrige su rumbo hacia el objetivo del wisp que la lanzó y, si
 *       ya no lo tiene, hacia el enemigo válido más cercano. Por eso "realmente sigue" al enemigo, en vez de
 *       volar en línea recta como la espora del hongo.</li>
 *   <li><b>Estalla</b> con salpicadura (daño en área + empuje + lentitud) al chocar con algo, al quedarse sin
 *       tiempo o al alcanzar a su víctima. La salpicadura es <b>manual</b>: no rompe terreno, no hace cráter y
 *       no daña al dueño, a sus otros esbirros ni al propio wisp (una explosión de verdad sí los dañaría).</li>
 * </ul>
 */
public class IceSpear extends FrostBall {

    /** Velocidad de crucero (bloques/tick). Más rápida que la bola de hielo normal. */
    private static final double SPEED = 1.15D;
    /** Radio en el que busca a quién perseguir. */
    private static final double HOMING_RADIUS = 24.0D;
    /** Radio de la salpicadura al estallar. */
    private static final double SPLASH_RADIUS = 3.0D;
    /** Ticks máximos de vuelo: si no acierta, estalla igual (antes de desaparecer sin más). */
    private static final int MAX_LIFETIME_TICKS = 120;
    /** Cuánta velocidad conserva y cuánta gira hacia el enemigo cada tick (0..1). */
    private static final double MOMENTUM = 0.72D;
    private static final double STEERING = 0.65D;

    /** Daño de la salpicadura. Se escala con los puntos del wisp arquero, como el daño directo. */
    private float splashDamage = 2.5F;
    /** ¿Ya estalló? Evita que estalle dos veces por golpear en el mismo tick a entidad y bloque. */
    private boolean exploded;

    public IceSpear(EntityType<? extends IceSpear> type, Level level) {
        super(type, level);
    }

    public IceSpear(Level level, LivingEntity thrower) {
        super(ModEntities.ICE_SPEAR.get(), thrower, level);
    }

    /** La lanza se dibuja con el icono de la habilidad (ver {@code models/item/ice_spear_projectile.json}). */
    @Override
    protected @NotNull Item getDefaultItem() {
        return ModItems.ICE_SPEAR_PROJECTILE.get();
    }

    @Override
    public void updateLevel(LivingEntity owner, int puntosAsignados) {
        super.updateLevel(owner, puntosAsignados);
        this.splashDamage = 2.5F + puntosAsignados * 0.12F;
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide && !this.exploded) {
            if (this.tickCount > MAX_LIFETIME_TICKS) {
                // Se le acabó el tiempo: estalla donde esté, para que no se pierda el disparo.
                this.exploded = true;
                this.splash();
                this.discard();
                return;
            }
            this.steerTowardsEnemy();
        }
        super.tick();
        if (this.level().isClientSide) {
            // Rastro de nieve para que se vea la trayectoria curvando hacia el enemigo.
            this.level().addParticle(ParticleTypes.SNOWFLAKE, this.getX(), this.getY(), this.getZ(),
                    0.0D, -0.02D, 0.0D);
        }
    }

    /** Gira el rumbo hacia el enemigo poco a poco: se ve la curva, no un giro instantáneo. */
    private void steerTowardsEnemy() {
        LivingEntity target = this.findEnemy();
        if (target == null) {
            return;
        }
        Vec3 toTarget = target.getEyePosition().subtract(this.position());
        if (toTarget.lengthSqr() < 0.25D) {
            return; // ya está encima: que lo mate el impacto
        }
        Vec3 steered = this.getDeltaMovement().scale(MOMENTUM).add(toTarget.normalize().scale(STEERING));
        if (steered.lengthSqr() > 1.0E-6D) {
            this.setDeltaMovement(steered.normalize().scale(SPEED));
        }
    }

    /**
     * A quién persigue: primero el objetivo del wisp que la lanzó (el que está combatiendo) y, si no hay,
     * el enemigo válido más cercano. Se descartan los aliados (el jugador dueño, el propio wisp y sus otros
     * esbirros), los pacíficos que el wisp no ataca y todo lo que el tirador no pueda atacar.
     */
    @Nullable
    private LivingEntity findEnemy() {
        Entity thrower = this.getOwner();
        if (thrower instanceof Mob shooter) {
            LivingEntity current = shooter.getTarget();
            if (current != null && this.canBeHunted(current) && this.distanceToSqr(current) <= HOMING_RADIUS * HOMING_RADIUS) {
                return current;
            }
        }
        List<LivingEntity> candidates = this.level().getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(HOMING_RADIUS), this::canBeHunted);
        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : candidates) {
            double distance = this.distanceToSqr(candidate);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = candidate;
            }
        }
        return nearest;
    }

    private boolean canBeHunted(LivingEntity candidate) {
        if (!candidate.isAlive() || candidate.isSpectator() || this.isFriendly(candidate)) {
            return false;
        }
        // Mismas exclusiones que el goal de objetivo del wisp arquero: no persigue aldeanos ni animales
        // neutrales/pacíficos (una lanza perdida no debe ir a matar la vaca del jugador).
        if (candidate instanceof Villager || candidate instanceof Llama || candidate instanceof Turtle
                || candidate instanceof IronGolem) {
            return false;
        }
        return this.getOwner() instanceof Mob shooter && shooter.canAttack(candidate);
    }

    /** ¿Es de los nuestros? El wisp que la lanzó, el jugador dueño y los demás esbirros de ese jugador. */
    private boolean isFriendly(LivingEntity candidate) {
        Entity thrower = this.getOwner();
        if (candidate == thrower) {
            return true;
        }
        UUID ownerUuid = thrower instanceof ITamableEntity tamable ? tamable.getOwnerUUID() : null;
        if (ownerUuid != null) {
            if (candidate instanceof ITamableEntity other && Objects.equals(ownerUuid, other.getOwnerUUID())) {
                return true;
            }
            if (candidate.getUUID().equals(ownerUuid)) {
                return true; // el jugador dueño
            }
        }
        return false;
    }

    @Override
    protected void onHit(@NotNull HitResult result) {
        boolean firstHit = !this.exploded;
        this.exploded = true;
        // El impacto directo (daño + lentitud + partículas de hielo) y el descarte los hace FrostBall.
        super.onHit(result);
        if (firstHit && !this.level().isClientSide) {
            this.splash();
        }
    }

    /**
     * Salpicadura: daña, empuja y ralentiza a todo lo que pille cerca, con el estallido visual y sonoro.
     * Es a mano a propósito: {@code level.explode(...)} rompería/destruiría terreno (o, sin romperlo, seguiría
     * dañando al dueño y a sus esbirros) y aquí lo que se quiere es solo el golpe en área.
     */
    private void splash() {
        if (!(this.level() instanceof ServerLevel server)) {
            return;
        }
        Entity thrower = this.getOwner();
        DamageSource source = thrower instanceof LivingEntity living
                ? this.damageSources().mobAttack(living)
                : this.damageSources().generic();
        List<LivingEntity> victims = server.getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(SPLASH_RADIUS), victim -> victim.isAlive() && !this.isFriendly(victim));
        for (LivingEntity victim : victims) {
            victim.hurt(source, this.splashDamage);
            victim.setIsInPowderSnow(true);
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            // Empuje hacia afuera del centro de la explosión (como una explosión de verdad).
            Vec3 push = victim.position().subtract(this.position());
            if (push.lengthSqr() > 1.0E-4D) {
                Vec3 direction = push.normalize().scale(0.6D);
                victim.push(direction.x, 0.35D, direction.z);
                victim.hurtMarked = true;
            }
        }
        server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), this.getY() + 0.2D, this.getZ(),
                1, 0.0D, 0.0D, 0.0D, 0.0D);
        server.sendParticles(ParticleTypes.SNOWFLAKE, this.getX(), this.getY(), this.getZ(),
                40, 1.1D, 1.1D, 1.1D, 0.06D);
        server.playSound(null, this.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.4F, 0.6F);
    }

    /** La lanza estalla al tocar a una entidad; el daño directo lo aplica {@link FrostBall#onHitEntity}. */
}
