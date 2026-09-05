package com.chipoodle.devilrpg.entity;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapability;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.init.ModEntities;
import com.chipoodle.devilrpg.util.IRenderUtilities;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;


public class SoulWolf extends Wolf implements ITamableEntity, ISoulEntity, PowerableMob, IPassiveMinionUpdater<SoulWolf> {
    public static final int FROSTBITE_DURATION_TICKS = 20;
    /** Duracion de la congelacion (lento/inmovil) que aplica el Frost Bite al objetivo. */
    private static final int FROZEN_DURATION_TICKS = 60;
    private static final int ICE_ARMOR_EFFECT_FACTOR = 2;
    private static final double RADIUS_PARTICLES = 0.7;
    //private static final int NUMBER_OF_PARTICLES_ICE_ARMOR = 11;
    private static final int PROBABILITY_MULTIPLIER = 4;
    private static final int ICE_ARMOR_DURATION_TICKS = 140;
    private static final int INITIAL_HEALTH = 8;
    private static final int NUMBER_OF_PARTICLES_FROST_BITE = 11;
    private int puntosAsignados = 0;
    private double saludMaxima = INITIAL_HEALTH;
    // private double stealingHealth;
    private Integer iceArmor = 0;
    private Integer frostbite = 0;

    public SoulWolf(EntityType<? extends SoulWolf> type, Level LevelIn) {
        super(type, LevelIn);
    }

    public static AttributeSupplier.Builder setAttributes() {
        return TamableAnimal.createMobAttributes()
                .add(Attributes.MOVEMENT_SPEED, 0.3F)
                .add(Attributes.MAX_HEALTH, 8.0D)
                .add(Attributes.FOLLOW_RANGE, 16.0D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.ARMOR, 0.35D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(4, new LeapAtTargetGoal(this, 0.4F));
        this.goalSelector.addGoal(5, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0D, 10.0F, 2.0F));
        this.goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(3, (new HurtByTargetGoal(this)).setAlertOthers());
        //this.targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(this, MobEntity.class, false));
        this.targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(this, Mob.class, 10, false, true, (entity) ->
                !(entity instanceof Villager) &&
                        !(entity instanceof Llama) &&
                        !(entity instanceof Turtle) &&
                        !(entity instanceof IronGolem)
        ));
        this.targetSelector.addGoal(8, new ResetUniversalAngerTargetGoal<>(this, true));
    }

    public void updateLevel(Player owner) {
        tame(owner);
        PlayerSkillCapabilityInterface skill = IGenericCapability.getUnwrappedPlayerCapability((Player) getOwner(), PlayerSkillCapability.INSTANCE);
        if (skill != null) {
            this.puntosAsignados = skill.getSkillsPoints().get(SkillEnum.SUMMON_SOUL_WOLF);
            saludMaxima = this.puntosAsignados + INITIAL_HEALTH;
            // stealingHealth = (0.135f * puntosAsignados) + 0.5;
        }

        Objects.requireNonNull(this.getAttribute(Attributes.MOVEMENT_SPEED)).setBaseValue(0.4F);
        Objects.requireNonNull(this.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(saludMaxima);
        Objects.requireNonNull(this.getAttribute(Attributes.ARMOR)).setBaseValue(0.35D);
        Objects.requireNonNull(this.getAttribute(Attributes.FOLLOW_RANGE)).setBaseValue(16.0D);
        Objects.requireNonNull(this.getAttribute(Attributes.ATTACK_DAMAGE)).setBaseValue((0.25 * puntosAsignados) + 2); // 2.25-5
        setHealth((float) saludMaxima);
        random.setSeed(System.currentTimeMillis());
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag compound) {
        super.readAdditionalSaveData(compound);
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putString("OwnerUUID", "");
        compound.putString("Owner", "");
    }

    @Override
    public void aiStep() {
        super.aiStep();
        addToAiStep(this);
    }

    /**
     * Returns whether this Entity is on the same team as the given Entity or they
     * share the same owner.
     */
    @Override
    public boolean isAlliedTo(@NotNull Entity entity) {
        boolean isOnSameTeam = super.isAlliedTo(entity);
        return isOnSameTeam || isEntitySameOwnerAsThis(entity, this);
    }

    @Override
    public boolean wantsToAttack(@NotNull LivingEntity target, @NotNull LivingEntity owner) {
        return ITamableEntity.super.wantsToAttack(target, owner);
    }

    /**
     * El efecto MobEffects.DAMAGE_BOOST tiene 5 niveles. Cada nivel aumenta el daño que inflige el jugador en 2,5%.
     * Por lo tanto, un jugador con el efecto MobEffects.DAMAGE_BOOST al máximo nivel infligirá 12,5% más daño que un jugador normal.
     *
     * @param target
     * @return
     */
    @Override
    public boolean doHurtTarget(@NotNull Entity target) {
        int randomNumber = random.nextInt(100);
        if (frostbite > 0 && randomNumber <= (frostbite * PROBABILITY_MULTIPLIER)) { //20% randomNumber on lvl5 frostbite

            MobEffectInstance frostbiteEffect = new MobEffectInstance(MobEffects.DAMAGE_BOOST, FROSTBITE_DURATION_TICKS, (int) Math.round((double) frostbite / 2), true, false);
            MobEffectInstance active = this.getEffect(MobEffects.DAMAGE_BOOST);
            if (active != null && frostbiteEffect.getAmplifier() <= active.getAmplifier()) {
                active.update(frostbiteEffect);
            } else
                this.addEffect(frostbiteEffect);
            //Explosion de hielo que sale del hocico del lobo al morder (Frost Bite).
            IRenderUtilities.frostBiteExplosion(this.level(), random, this);
            DevilRpg.LOGGER.debug("----------> frost byte explosion!! ");
            // Congelar al objetivo: queda lento/inmovil y actuando lento unos segundos.
            if (target instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, FROZEN_DURATION_TICKS, 3, true, false));
                living.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, FROZEN_DURATION_TICKS, 2, true, false));
            }
        }
        double attackDamage = this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        boolean flag = target.hurt(this.damageSources().mobAttack(this), (float) (attackDamage));
        MobEffectInstance mobEffectInstance = this.getEffect(MobEffects.DAMAGE_BOOST);
        DevilRpg.LOGGER.debug("---------->doHurtTarget attdmg {} + frostbite: {} random: {} <= limit: {} effect lvl {} owner: {}  owneruuid: {}"
                , attackDamage, Math.round((double) frostbite / 2), randomNumber,
                frostbite * PROBABILITY_MULTIPLIER, mobEffectInstance != null ? mobEffectInstance.getAmplifier() : 0, getOwner().getName().getString(), getOwnerUUID());

        if (flag) {
            // doEnchantDamageEffects removed in 1.21
        }
        return flag;
    }

    @Override
    public boolean hurt(@NotNull DamageSource damageSource, float amount) {

        int probability = random.nextInt(100);
        if (iceArmor > 0 && probability <= (iceArmor * PROBABILITY_MULTIPLIER) && !this.level().isClientSide) {
            //IRenderUtilities.rotationParticles(Minecraft.getInstance().level, random, this, ParticleTypes.SNOWFLAKE, NUMBER_OF_PARTICLES_ICE_ARMOR, RADIUS_PARTICLES);
            MobEffectInstance iceArmorEffect = new MobEffectInstance(MobEffects.ABSORPTION, ICE_ARMOR_DURATION_TICKS, iceArmor * ICE_ARMOR_EFFECT_FACTOR, true, false);
            MobEffectInstance active = this.getEffect(MobEffects.ABSORPTION);
            if (active != null && iceArmorEffect.getAmplifier() <= active.getAmplifier()) {
                active.update(iceArmorEffect);
            } else
                this.addEffect(iceArmorEffect);

            DevilRpg.LOGGER.debug("---------->hurt iceArmor: {} probability: {} Range of success: {}", iceArmor, probability, iceArmor * PROBABILITY_MULTIPLIER);

            MobEffectInstance fireResistance = new MobEffectInstance(MobEffects.FIRE_RESISTANCE, ICE_ARMOR_DURATION_TICKS, Math.max(Math.min(iceArmor - 1, 3), 0));
            this.addEffect(fireResistance);
            boolean hasEffect = this.hasEffect(MobEffects.FIRE_RESISTANCE);

            MobEffectInstance effect = this.getEffect(MobEffects.FIRE_RESISTANCE);
            DevilRpg.LOGGER.debug("---------- fireResistance?:{} amplifier:{} duration: {}", hasEffect, Objects.requireNonNull(effect).getAmplifier(), effect.getDuration());
        }

        return super.hurt(damageSource, amount);
    }

    @Override
    public @NotNull InteractionResult mobInteract(@NotNull Player playerIn, @NotNull InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public void remove(@NotNull RemovalReason removalReason) {
        super.remove(removalReason);
    }

    /**
     * Called when the mob's health reaches 0.
     */
    @Override
    public void die(@NotNull DamageSource cause) {
        if (getOwner() != null) {
            PlayerMinionCapabilityInterface minionCap = getOwner()
                    .getData(PlayerMinionCapability.INSTANCE);
            if (minionCap == null)
                return;
            minionCap.removeSoulWolf((Player) getOwner(), this);
        }
        // super.onDeath(cause);
        customOnDeath();
    }

    private void customOnDeath() {
        level().broadcastEntityEvent(this, (byte) 3);
        this.dead = true;
        this.remove(RemovalReason.DISCARDED);
        IRenderUtilities.customDeadParticles(this.level(), this.random, this);
    }

    /**
     * Called on the logical server to get a packet to send to the client containing
     * data necessary to spawn your entity. Using Forge's method instead of the
     * default vanilla one allows extra stuff to work such as sending extra data,
     * using a non-default entity factory and having
     * {@link Packet} work.
     * <p>
     * It is not actually necessary for our WildBoarEntity to use Forge's method as
     * it doesn't need any of this extra functionality, however, this is an example
     * mod and many modders are unaware that Forge's method exists.
     *
     * @return The packet with data about your entity
     */

    /**
     * Get the experience points the entity currently has.
     */
    public int getExperienceReward() {
        /*
         * if (player.equals(getOwner())) return 0; return 1 +
         * this.Level.rand.nextInt(3);
         */
        return 0;
    }

    @Override
    public LivingEntity getOwner() {
        try {
            return super.getOwner();
        } catch (IllegalArgumentException illegalargumentexception) {
            return null;
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public float getTailAngle() {
        if (this.isAngry()) {
            return 1.5393804F;
        } else {
            return ((float) Math.PI / 5F);
        }
    }

    @Override
    public boolean isPowered() {
        return true;
    }

    @Override
    public SoulWolf getBreedOffspring(ServerLevel Level, AgeableMob mate) {
        return ModEntities.SOUL_WOLF.get().create(Level);
    }

    public void setFrostbite(Integer frostbite) {
        this.frostbite = frostbite;

    }

    public void setIceArmor(Integer iceArmor) {
        this.iceArmor = iceArmor;

    }


    @Override
    public double distanceToSqr(LivingEntity livingentity) {
        return super.distanceToSqr(livingentity);
    }

    @Override
    public float getXRot() {
        return super.getXRot();
    }

    @Override
    public float getYRot() {
        return super.getYRot();
    }

    @Override
    public void moveTo(double d, double p_226328_2_, double e, float getyRot, float getxRot) {
        super.moveTo(d, p_226328_2_, e, getyRot, getxRot);
    }

    @Override
    public Entity getEntity() {
        return this;
    }

    public boolean canBeLeashed(@NotNull Player player) {
        return false;
    }

}
