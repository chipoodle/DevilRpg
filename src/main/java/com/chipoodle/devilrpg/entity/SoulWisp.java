package com.chipoodle.devilrpg.entity;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapability;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.util.IRenderUtilities;
import com.chipoodle.devilrpg.init.ModEntities;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.AirAndWaterRandomPos;
import net.minecraft.world.entity.ai.util.HoverRandomPos;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;


public class SoulWisp extends TamableAnimal implements ITamableEntity, FlyingAnimal, ISoulEntity, PowerableMob,
        NeutralMob, IPassiveMinionUpdater<SoulWisp> {

    private static final EntityDataAccessor<Boolean> DATA_DANCING = SynchedEntityData.defineId(SoulWisp.class, EntityDataSerializers.BOOLEAN);
    protected static final double DISTANCIA_EFECTO = 20;
    protected static final int DURATION_TICKS = 120;
    private static final EntityDataAccessor<Integer> DATA_REMAINING_ANGER_TIME = SynchedEntityData.defineId(SoulWisp.class, EntityDataSerializers.INT);
    private static final UniformInt PERSISTENT_ANGER_TIME = TimeUtil.rangeOfSeconds(20, 39);
    private final int SALUD_INICIAL = 4;
    protected int puntosAsignados = 0;
    protected double saludMaxima = SALUD_INICIAL;
    protected MobEffect efectoPrimario;
    protected MobEffect efectoSecundario;
    protected boolean esBeneficioso;
    private UUID lastHurtBy;

    private float holdingItemAnimationTicks;
    private float holdingItemAnimationTicks0;
    private float dancingAnimationTicks;
    private float spinningAnimationTicks;
    private float spinningAnimationTicks0;

    public SoulWisp(EntityType<? extends SoulWisp> type, Level worldIn) {
        super(type, worldIn);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.lookControl = new BeeLookControl(this);
        this.setPathfindingMalus(BlockPathTypes.WATER, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.WATER_BORDER, 16.0F);
        this.setPathfindingMalus(BlockPathTypes.FENCE, -1.0F);
    }

    /**
     * Called in EntityAttributeCreationEvent event
     *
     * @return
     */
    public static AttributeSupplier.Builder setAttributes() {
        return TamableAnimal.createMobAttributes()
                .add(Attributes.MOVEMENT_SPEED, 0.3F)
                .add(Attributes.FLYING_SPEED, 0.9F)
                .add(Attributes.MAX_HEALTH, 8.0D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.ARMOR, 0.15D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_DANCING, false);
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader worldIn) {
        return worldIn.getBlockState(pos).isAir() ? 10.0F : 0.0F;
    }

    @Override
    protected void registerGoals() {

        this.goalSelector.addGoal(0, new PanicGoal(this, 1.25D));
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new FollowOwnerGoal(this, 1.0D, 3.0F, 7.0F, false));
        this.goalSelector.addGoal(2, new WaterAvoidingRandomFlyingGoal(this, 1.0D));
        //// this.goalSelector.addGoal(3, new FollowMobGoal(this, 1.0D, 3.0F,7.0F));
        //// this.goalSelector.addGoal(5, new FollowParentGoal(this, 1.25D));
        this.goalSelector.addGoal(8, new SoulWisp.WanderGoal());
        // this.goalSelector.addGoal(9, new SwimGoal(this));

    }

    public void updateLevel(Player owner, MobEffect efectoPrimario, MobEffect efectoSecundario, SkillEnum tipoWisp,
                            boolean esBeneficioso) {
        tame(owner);
        LazyOptional<PlayerSkillCapabilityInterface> skill = getOwner().getCapability(PlayerSkillCapability.INSTANCE);
        this.efectoPrimario = efectoPrimario;
        this.efectoSecundario = efectoSecundario;
        this.esBeneficioso = esBeneficioso;
        if (skill.isPresent()) {
            this.puntosAsignados = skill.map(PlayerSkillCapabilityInterface::getSkillsPoints).orElse(null).get(tipoWisp);
            saludMaxima = 0.6 * this.puntosAsignados + SALUD_INICIAL;
            // DevilRpg.LOGGER.debug("SoulWispEntity.updateLevel.saludMaxima{}",saludMaxima);
        }

        Objects.requireNonNull(this.getAttribute(Attributes.FLYING_SPEED)).setBaseValue(0.9F);
        Objects.requireNonNull(this.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(saludMaxima);
        Objects.requireNonNull(this.getAttribute(Attributes.ARMOR)).setBaseValue(0.15D);
        Objects.requireNonNull(this.getAttribute(Attributes.MOVEMENT_SPEED)).setBaseValue(0.3F);
        Objects.requireNonNull(this.getAttribute(Attributes.ATTACK_DAMAGE)).setBaseValue(2.0D);
        Objects.requireNonNull(this.getAttribute(Attributes.FOLLOW_RANGE)).setBaseValue(48.0D);
        setHealth((float) saludMaxima);
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putString("OwnerUUID", "");
        compound.putString("Owner", "");
    }

    /**
     * (abstract) Protected helper method to read subclass entity data from NBT.
     */
    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag compound) {
        super.readAdditionalSaveData(compound);
    }

    /**
     * Called to update the entity's position/logic.
     */
    @Override
    public void tick() {
        super.tick();

        this.holdingItemAnimationTicks0 = this.holdingItemAnimationTicks;
        if (this.hasItemInHand()) {
            this.holdingItemAnimationTicks = Mth.clamp(this.holdingItemAnimationTicks + 1.0F, 0.0F, 5.0F);
        } else {
            this.holdingItemAnimationTicks = Mth.clamp(this.holdingItemAnimationTicks - 1.0F, 0.0F, 5.0F);
        }

        if (this.random.nextFloat() < 0.05F) {
            for (int i = 0; i < this.random.nextInt(2) + 1; ++i) {
                this.addParticle(this.level, this.getX() - (double) 0.3F, this.getX() + (double) 0.3F,
                        this.getZ() - (double) 0.3F, this.getZ() + (double) 0.3F, this.getY(0.5D),
                        ParticleTypes.CRIMSON_SPORE);
            }
        }
    }

    private void addParticle(Level worldIn, double p_226397_2_, double p_226397_4_, double p_226397_6_,
                             double p_226397_8_, double posY, SimpleParticleType particleData) {
        worldIn.addParticle(particleData, Mth.lerp(worldIn.random.nextDouble(), p_226397_2_, p_226397_4_), posY,
                Mth.lerp(worldIn.random.nextDouble(), p_226397_6_, p_226397_8_), 0.0D, 0.0D, 0.0D);
    }

    @Override
    protected void sendDebugPackets() {
        super.sendDebugPackets();
        // DebugPacketSender.sendBeeInfo(this);
    }

    /**
     * Called frequently so the entity can update its state every tick as required.
     * For example, zombies and skeletons use this to react to sunlight and start to
     * burn.
     */
    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level.isClientSide) {
            if (this.level.getGameTime() % 80L == 0L && efectoPrimario != null && efectoSecundario != null) {
                this.addEffectsToPlayers(puntosAsignados, efectoPrimario, efectoSecundario, esBeneficioso);
            }
        }
        addToAiStep(this);
    }

    /**
     * Returns new PathNavigateGround instance
     */
    @Override
    protected PathNavigation createNavigation(Level worldIn) {
        FlyingPathNavigation flyingpathnavigator = new FlyingPathNavigation(this, worldIn) {
            public boolean isStableDestination(BlockPos pos) {
                return !this.level.getBlockState(pos.below()).isAir();
            }

            public void tick() {
                super.tick();
            }
        };
        flyingpathnavigator.setCanOpenDoors(true);
        flyingpathnavigator.setCanFloat(true);
        flyingpathnavigator.setCanPassDoors(true);
        return flyingpathnavigator;
    }

    /**
     * Checks if the parameter is an item which this animal can be fed to breed it
     * (wheat, carrots or seeds depending on the animal type)
     */
    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ItemTags.FLOWERS);
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState blockIn) {
    }

    protected SoundEvent getAmbientSound() {
        return SoundEvents.BEACON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        return SoundEvents.BEACON_POWER_SELECT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.BEACON_DEACTIVATE;
    }

    /**
     * Returns the volume for the sounds this mob makes.
     */
    @Override
    protected float getSoundVolume() {
        return 0.4F;
    }

    public SoulWisp getBreedOffspring(AgeableMob ageable) {
        return ModEntities.WISP.get().create(this.level);
    }

    @Override
    protected float getStandingEyeHeight(Pose poseIn, EntityDimensions sizeIn) {
        return sizeIn.height * 0.5F;
    }

    @Override
    public boolean causeFallDamage(float distance, float damageMultiplier, DamageSource damageSource) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGroundIn, BlockState state, BlockPos pos) {
    }

    protected boolean makeFlySound() {
        return true;
    }

    @Override
    public boolean doHurtTarget(Entity entityIn) {
        return false;
    }

    /**
     * Called when the entity is attacked.
     */
    public boolean hurt(DamageSource source, float amount) {
        return super.hurt(source, amount);
    }

    @Override
    public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        //return ((ITameableEntity)this).wantsToAttack(target, owner);
        return ITamableEntity.super.wantsToAttack(target, owner);
    }

    @Override
    public MobType getMobType() {
        return MobType.UNDEFINED;
    }

	/*protected void jumpInLiquid(TagKey<Fluid> p_204061_) {
		this.jumpInLiquidInternal();
	}*/

    private void jumpInLiquidInternal() {
        this.setDeltaMovement(this.getDeltaMovement().add(0.0D, 0.01D, 0.0D));
    }

    @Override
    public void jumpInFluid(net.minecraftforge.fluids.FluidType type) {
        this.jumpInLiquidInternal();
    }

    @Override
    public boolean isFlying() {
        return !this.onGround;
    }

    /**
     * Returns whether this Entity is on the same team as the given Entity or they
     * share the same owner.
     */
    @Override
    public boolean isAlliedTo(Entity entityIn) {
        boolean isOnSameTeam = super.isAlliedTo(entityIn);
        return isOnSameTeam || isEntitySameOwnerAsThis(entityIn, this);
    }

    private void addEffectsToPlayers(int niveles, MobEffect primaryEffect, MobEffect secondaryEffect, boolean isBeneficial) {
        if (niveles >= 0 && !this.level.isClientSide && primaryEffect != null) {
            // rango entre 0 - 4 el tipo de boost health
            int potenciaPocion = getPotenciaPocion(niveles);
            //DevilRpg.LOGGER.debug("niveles {} potenciaPocion {}",niveles, potenciaPocion);

            double k = this.position().x();
            double l = this.position().y();
            double i1 = this.position().z();
            AABB axisalignedbb = (new AABB(k, l, i1, (k + 1), (l + 1), (i1 + 1)))
                    .inflate(DISTANCIA_EFECTO).expandTowards(0.0D, this.level.getHeight(), 0.0D);

            if (niveles > 0) {
                List<LivingEntity> alliesList = null;
                List<LivingEntity> monsterlist = null;
                if (isBeneficial) {
                    alliesList = getAlliesListWithinAABBRange(axisalignedbb);
                    applyPrimaryEffect(primaryEffect, potenciaPocion, alliesList);
                    if (niveles >= 1) {
                        applySecondaryEffect(secondaryEffect, alliesList);
                    }

                } else {
                    monsterlist = getEnemiesListWithinAABBRange(axisalignedbb);
                    applyPrimaryEffect(primaryEffect, potenciaPocion, monsterlist);
                    if (niveles >= 1) {
                        applySecondaryEffect(secondaryEffect, monsterlist);
                    }
                }
            }
        }
    }

    private void applyPrimaryEffect(MobEffect primaryEffect, int amplifierIn, List<LivingEntity> alliesList) {
        for (LivingEntity entity : alliesList) {
            MobEffectInstance pri = new MobEffectInstance(primaryEffect, DURATION_TICKS, amplifierIn, true, true);
            MobEffectInstance active = entity.getEffect(primaryEffect);
            if (!(active == null || pri.getAmplifier() > active.getAmplifier())) {
                active.update(pri);
            }
            entity.addEffect(pri);
        }
    }

    private void applySecondaryEffect(MobEffect secondaryEffect, List<LivingEntity> alliesList) {
        for (LivingEntity entity : alliesList) {
            MobEffectInstance sec = new MobEffectInstance(secondaryEffect, DURATION_TICKS, 0, true, true);
            MobEffectInstance active = entity.getEffect(secondaryEffect);
            if (!(active == null || sec.getAmplifier() > active.getAmplifier())) {
                active.update(sec);
            }
            entity.addEffect(sec);
        }
    }

    private List<LivingEntity> getAlliesListWithinAABBRange(AABB axisalignedbb) {
        List<LivingEntity> list = this.level.getEntitiesOfClass(LivingEntity.class, axisalignedbb)
                .stream().filter(x -> x.isAlliedTo(this.getOwner()) || x.equals(getOwner()))
                .collect(Collectors.toList());
        return list;
    }

    private List<LivingEntity> getEnemiesListWithinAABBRange(AABB axisalignedbb) {
        List<LivingEntity> list = this.level.getEntitiesOfClass(Mob.class, axisalignedbb).stream()
                .filter(x -> !x.isAlliedTo(this.getOwner())).map(x -> (LivingEntity) x).collect(Collectors.toList());
        return list;
    }

    @Override
    public void remove(RemovalReason removalReason) {
        super.remove(removalReason);
    }

    /**
     * Called when the mob's health reaches 0.
     */
    @Override
    public void die(DamageSource cause) {
        if (getOwner() != null) {
            LazyOptional<PlayerMinionCapabilityInterface> minionCap = getOwner()
                    .getCapability(PlayerMinionCapability.INSTANCE);
            if (!minionCap.isPresent())
                return;
            minionCap.ifPresent(x -> x.removeWisp((Player) getOwner(), this));
        }
        // super.onDeath(cause);
        customOnDeath();
    }

    private void customOnDeath() {
        level.broadcastEntityEvent(this, (byte) 3);
        this.dead = true;
        this.remove(RemovalReason.DISCARDED);
        IRenderUtilities.customDeadParticles(this.level, this.random, this);
    }

    public boolean isEsBeneficioso() {
        return esBeneficioso;
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
    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    /**
     * Get the experience points the entity currently has.
     */
    @Override
    public int getExperienceReward() {
        /*
         * if (player.equals(getOwner())) return 0; return 1 +
         * this.level.rand.nextInt(3);
         */
        return 0;
    }

    @Override
    public int getRemainingPersistentAngerTime() {
        return this.entityData.get(DATA_REMAINING_ANGER_TIME);
    }

    @Override
    public void setRemainingPersistentAngerTime(int time) {
        this.entityData.set(DATA_REMAINING_ANGER_TIME, time);
    }

    @Override
    public UUID getPersistentAngerTarget() {
        return this.lastHurtBy;
    }

    @Override
    public void setPersistentAngerTarget(@Nullable UUID target) {
        this.lastHurtBy = target;
    }

    @Override
    public void startPersistentAngerTimer() {
        this.setRemainingPersistentAngerTime(PERSISTENT_ANGER_TIME.sample(this.random));
    }

    @Override
    public boolean isPowered() {
        return true;
    }

    @Override
    public SoulWisp getBreedOffspring(ServerLevel Level, AgeableMob mate) {
        return ModEntities.WISP.get().create(Level);
    }

    @Override
    public Level getLevel() {
        return this.level;
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

    static class BeeLookControl extends LookControl {
        BeeLookControl(Mob beeIn) {
            super(beeIn);
        }

        public void tick() {
            super.tick();
        }
    }

    abstract static class PassiveGoal extends Goal {
        private PassiveGoal() {
        }

        public abstract boolean canBeeStart();

        public abstract boolean canBeeContinue();

        /**
         * Returns whether execution should begin. You can also read and cache any state
         * necessary for execution in this method as well.
         */
        public boolean shouldExecute() {
            return true;// this.canBeeStart() && !WispEntity.this.func_226427_ez_();
        }

        /**
         * Returns whether an in-progress EntityAIBase should continue executing
         */
        public boolean shouldContinueExecuting() {
            return true;// this.canBeeContinue() && !WispEntity.this.func_226427_ez_();
        }
    }

    class WanderGoal extends Goal {
        WanderGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        /**
         * Returns whether execution should begin. You can also read and cache any state
         * necessary for execution in this method as well.
         */
        public boolean canUse() {
            return SoulWisp.this.navigation.isDone() && SoulWisp.this.random.nextInt(10) == 0;
        }

        /**
         * Returns whether an in-progress EntityAIBase should continue executing
         */
        public boolean canContinueToUse() {
            return SoulWisp.this.navigation.isInProgress();
        }

        /**
         * Execute a one shot task or start executing a continuous task
         */
        public void start() {
            Vec3 vec3d = this.findPos();
            if (vec3d != null) {
                SoulWisp.this.navigation.moveTo(SoulWisp.this.navigation.createPath(BlockPos.containing(vec3d), 1), 1.0D);
            }

        }

        @Nullable
        private Vec3 findPos() {
            Vec3 vec3;
            vec3 = SoulWisp.this.getViewVector(0.0F);
            // int i = 8;
            Vec3 vector3d2 = HoverRandomPos.getPos(SoulWisp.this, 8, 7, vec3.x, vec3.z, ((float) Math.PI / 2F), 3, 1);
            return vector3d2 != null ? vector3d2 : AirAndWaterRandomPos.getPos(SoulWisp.this, 8, 4, -2, vec3.x, vec3.z, (double) ((float) Math.PI / 2F));
        }
    }

    public float getHoldingItemAnimationProgress(float p_218395_) {
        return Mth.lerp(p_218395_, this.holdingItemAnimationTicks0, this.holdingItemAnimationTicks) / 5.0F;
    }

    private boolean isOnPickupCooldown() {
        return this.getBrain().checkMemory(MemoryModuleType.ITEM_PICKUP_COOLDOWN_TICKS, MemoryStatus.VALUE_PRESENT);
    }
    @Override
    public boolean canPickUpLoot() {
        return !this.isOnPickupCooldown() && this.hasItemInHand();
    }

    public boolean hasItemInHand() {
        return !this.getItemInHand(InteractionHand.MAIN_HAND).isEmpty();
    }

    public boolean isDancing() {
        return this.entityData.get(DATA_DANCING);
    }

    public void setDancing(boolean p_240178_) {
        if (!this.level.isClientSide && this.isEffectiveAi() && (!p_240178_ /*|| !this.isPanicking()*/)) {
            this.entityData.set(DATA_DANCING, p_240178_);
        }
    }

    public boolean isSpinning() {
        float f = this.dancingAnimationTicks % 55.0F;
        return f < 15.0F;
    }

    public float getSpinningProgress(float p_240057_) {
        return Mth.lerp(p_240057_, this.spinningAnimationTicks0, this.spinningAnimationTicks) / 15.0F;
    }

    public boolean canBeLeashed(Player p_30396_) {
        return false;
    }
}
