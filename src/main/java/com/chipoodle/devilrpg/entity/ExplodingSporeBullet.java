package com.chipoodle.devilrpg.entity;

import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapability;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.util.IRenderUtilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.AirAndWaterRandomPos;
import net.minecraft.world.entity.ai.util.AirRandomPos;
import net.minecraft.world.entity.ai.util.HoverRandomPos;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.*;

public class ExplodingSporeBullet extends TamableAnimal implements NeutralMob, FlyingAnimal, ITamableEntity, ISoulEntity {
    public static final int TICKS_PER_FLAP = Mth.ceil(1.4959966F);
    private static final EntityDataAccessor<Byte> DATA_FLAGS_ID = SynchedEntityData.defineId(ExplodingSporeBullet.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_REMAINING_ANGER_TIME = SynchedEntityData.defineId(ExplodingSporeBullet.class, EntityDataSerializers.INT);
    private static final UniformInt PERSISTENT_ANGER_TIME = TimeUtil.rangeOfSeconds(20, 39);
    int remainingCooldownBeforeLocatingNewHive;
    int remainingCooldownBeforeLocatingNewFlower = Mth.nextInt(this.random, 20, 60);
    @Nullable
    private UUID persistentAngerTarget;
    private float rollAmount;
    private float rollAmountO;
    private int timeSinceSting;
    //private int stayOutOfHiveCountdown;
    private int underWaterTicks;

    public ExplodingSporeBullet(EntityType<? extends ExplodingSporeBullet> p_27717_, Level p_27718_) {
        super(p_27717_, p_27718_);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.lookControl = new ExplodingSporeBulletLookControl(this);
        //this.setPathfindingMalus(BlockPathTypes.DANGER_FIRE, -1.0F);
        //this.setPathfindingMalus(BlockPathTypes.WATER, -1.0F);
        //this.setPathfindingMalus(BlockPathTypes.WATER_BORDER, 16.0F);
        //this.setPathfindingMalus(BlockPathTypes.COCOA, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.FENCE, -1.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 10.0D).add(Attributes.FLYING_SPEED, (double) 0.6F).add(Attributes.MOVEMENT_SPEED, (double) 0.3F).add(Attributes.ATTACK_DAMAGE, 2.0D).add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_FLAGS_ID, (byte) 0);
        this.entityData.define(DATA_REMAINING_ANGER_TIME, 0);
    }

    public float getWalkTargetValue(@NotNull BlockPos p_27788_, LevelReader p_27789_) {
        return p_27789_.getBlockState(p_27788_).isAir() ? 10.0F : 0.0F;
    }

    protected void registerGoals() {
        this.goalSelector.addGoal(0, new ExplodingSporeBullet.ExplodingShulkerBulletAttackGoal(this, 1.4F, true));
        this.goalSelector.addGoal(8, new ExplodingSporeBullet.ExplodingShulkerBulletWanderGoal());
        this.goalSelector.addGoal(9, new FloatGoal(this));
        this.targetSelector.addGoal(1, (new ExplodingSporeBullet.ExplodingShulkerBulletHurtByOtherGoal(this)).setAlertOthers());
        this.targetSelector.addGoal(2, new ExplodingSporeBullet.ExplodingShulkerBulletBecomeAngryTargetGoal(this));

        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(3, (new HurtByTargetGoal(this)).setAlertOthers());
        this.targetSelector.addGoal(4,
                new NearestAttackableTargetGoal<>(this, Mob.class, 10, false, false, (entity) ->
                        !(entity instanceof Villager)
                                && !(entity instanceof Llama)
                                && !(entity instanceof Turtle)
                                && !(entity instanceof IronGolem)
                                && !(entity instanceof ITamableEntity && Objects.equals(((ITamableEntity) entity).getOwnerUUID(), this.getOwnerUUID()))
                ));
        //this.targetSelector.addGoal(3, new ResetUniversalAngerTargetGoal<>(this, true));
        this.targetSelector.addGoal(8, new ResetUniversalAngerTargetGoal<>(this, true));
    }

    public void addAdditionalSaveData(@NotNull CompoundTag compoundTag) {
        super.addAdditionalSaveData(compoundTag);

        compoundTag.putBoolean("HasStung", this.hasStung());
        //compoundTag.putInt("CannotEnterHiveTicks", this.stayOutOfHiveCountdown);
        this.addPersistentAngerSaveData(compoundTag);
        compoundTag.putString("OwnerUUID", "");
        compoundTag.putString("Owner", "");
    }

    public void readAdditionalSaveData(CompoundTag p_27793_) {
        super.readAdditionalSaveData(p_27793_);
        this.setHasStung(p_27793_.getBoolean("HasStung"));
        //this.stayOutOfHiveCountdown = p_27793_.getInt("CannotEnterHiveTicks");
        this.readPersistentAngerSaveData(this.level, p_27793_);
    }

    public boolean doHurtTarget(Entity target) {
        boolean flag = target.hurt(this.damageSources().sting(this), (float) ((int) this.getAttributeValue(Attributes.ATTACK_DAMAGE)));
        if (flag) {
            this.doEnchantDamageEffects(this, target);
            if (target instanceof LivingEntity) {
                ((LivingEntity) target).setStingerCount(((LivingEntity) target).getStingerCount() + 1);
                int i = 0;
                if (this.level.getDifficulty() == Difficulty.NORMAL) {
                    i = 10;
                } else if (this.level.getDifficulty() == Difficulty.HARD) {
                    i = 18;
                }

                if (i > 0) {
                    ((LivingEntity) target).addEffect(new MobEffectInstance(MobEffects.POISON, i * 20, 0), this);
                }
            }

            this.setHasStung(true);
            this.stopBeingAngry();
            this.playSound(SoundEvents.BEE_STING, 1.0F, 1.0F);
        }

        return flag;
    }

    public void tick() {
        super.tick();
        if (this.random.nextFloat() < 0.05F) {
            for (int i = 0; i < this.random.nextInt(2) + 1; ++i) {
                this.spawnFluidParticle(this.level, this.getX() - (double) 0.3F, this.getX() + (double) 0.3F, this.getZ() - (double) 0.3F, this.getZ() + (double) 0.3F, this.getY(0.5D), ParticleTypes.FALLING_NECTAR);
            }
        }

        this.updateRollAmount();
    }

    private void spawnFluidParticle(Level p_27780_, double p_27781_, double p_27782_, double p_27783_, double p_27784_, double p_27785_, ParticleOptions p_27786_) {
        p_27780_.addParticle(p_27786_, Mth.lerp(p_27780_.random.nextDouble(), p_27781_, p_27782_), p_27785_, Mth.lerp(p_27780_.random.nextDouble(), p_27783_, p_27784_), 0.0D, 0.0D, 0.0D);
    }

    void pathfindRandomlyTowards(BlockPos p_27881_) {
        Vec3 vec3 = Vec3.atBottomCenterOf(p_27881_);
        int i = 0;
        BlockPos blockpos = this.blockPosition();
        int j = (int) vec3.y - blockpos.getY();
        if (j > 2) {
            i = 4;
        } else if (j < -2) {
            i = -4;
        }

        int k = 6;
        int l = 8;
        int i1 = blockpos.distManhattan(p_27881_);
        if (i1 < 15) {
            k = i1 / 2;
            l = i1 / 2;
        }

        Vec3 vec31 = AirRandomPos.getPosTowards(this, k, l, i, vec3, (double) ((float) Math.PI / 10F));
        if (vec31 != null) {
            this.navigation.setMaxVisitedNodesMultiplier(0.5F);
            this.navigation.moveTo(vec31.x, vec31.y, vec31.z, 1.0D);
        }
    }
    public float getRollAmount(float p_27936_) {
        return Mth.lerp(p_27936_, this.rollAmountO, this.rollAmount);
    }

    private void updateRollAmount() {
        this.rollAmountO = this.rollAmount;
        if (this.isRolling()) {
            this.rollAmount = Math.min(1.0F, this.rollAmount + 0.2F);
        } else {
            this.rollAmount = Math.max(0.0F, this.rollAmount - 0.24F);
        }

    }

    protected void customServerAiStep() {
        boolean flag = this.hasStung();
        if (this.isInWaterOrBubble()) {
            ++this.underWaterTicks;
        } else {
            this.underWaterTicks = 0;
        }

        if (this.underWaterTicks > 20) {
            this.hurt(this.damageSources().drown(), 1.0F);
        }

        if (flag) {
            ++this.timeSinceSting;
            if (this.timeSinceSting % 5 == 0 && this.random.nextInt(Mth.clamp(1200 - this.timeSinceSting, 1, 1200)) == 0) {
                this.hurt(this.damageSources().generic(), this.getHealth());
            }
        }

        if (!this.level.isClientSide) {
            this.updatePersistentAnger((ServerLevel) this.level, false);
        }

    }

    public int getRemainingPersistentAngerTime() {
        return this.entityData.get(DATA_REMAINING_ANGER_TIME);
    }

    public void setRemainingPersistentAngerTime(int p_27795_) {
        this.entityData.set(DATA_REMAINING_ANGER_TIME, p_27795_);
    }

    @Nullable
    public UUID getPersistentAngerTarget() {
        return this.persistentAngerTarget;
    }

    public void setPersistentAngerTarget(@Nullable UUID p_27791_) {
        this.persistentAngerTarget = p_27791_;
    }

    public void startPersistentAngerTimer() {
        this.setRemainingPersistentAngerTime(PERSISTENT_ANGER_TIME.sample(this.random));
    }

    private boolean doesHiveHaveSpace(BlockPos p_27885_) {
        BlockEntity blockentity = this.level.getBlockEntity(p_27885_);
        if (blockentity instanceof BeehiveBlockEntity) {
            return !((BeehiveBlockEntity) blockentity).isFull();
        } else {
            return false;
        }
    }

    @VisibleForDebug
    public GoalSelector getGoalSelector() {
        return this.goalSelector;
    }

    protected void sendDebugPackets() {
        super.sendDebugPackets();
        //DebugPackets.sendBeeInfo(this);
    }

    public void aiStep() {
        super.aiStep();
        if (!this.level.isClientSide) {
            //if (this.stayOutOfHiveCountdown > 0) {
            //    --this.stayOutOfHiveCountdown;
           // }

            if (this.remainingCooldownBeforeLocatingNewHive > 0) {
                --this.remainingCooldownBeforeLocatingNewHive;
            }

            if (this.remainingCooldownBeforeLocatingNewFlower > 0) {
                --this.remainingCooldownBeforeLocatingNewFlower;
            }

            boolean flag = this.isAngry() && !this.hasStung() && this.getTarget() != null && this.getTarget().distanceToSqr(this) < 4.0D;
            this.setRolling(flag);
        }

        addToAiStep(this);

    }

    public boolean hasStung() {
        return this.getFlag(4);
    }

    private void setHasStung(boolean p_27926_) {
        this.setFlag(4, p_27926_);
    }

    private boolean isRolling() {
        return this.getFlag(2);
    }

    private void setRolling(boolean p_27930_) {
        this.setFlag(2, p_27930_);
    }

    boolean isTooFarAway(BlockPos p_27890_) {
        return !this.closerThan(p_27890_, 32);
    }

    private void setFlag(int p_27833_, boolean p_27834_) {
        if (p_27834_) {
            this.entityData.set(DATA_FLAGS_ID, (byte) (this.entityData.get(DATA_FLAGS_ID) | p_27833_));
        } else {
            this.entityData.set(DATA_FLAGS_ID, (byte) (this.entityData.get(DATA_FLAGS_ID) & ~p_27833_));
        }

    }

    private boolean getFlag(int p_27922_) {
        return (this.entityData.get(DATA_FLAGS_ID) & p_27922_) != 0;
    }

    protected @NotNull PathNavigation createNavigation(@NotNull Level p_27815_) {
        FlyingPathNavigation flyingpathnavigation = new FlyingPathNavigation(this, p_27815_) {
            public boolean isStableDestination(BlockPos p_27947_) {
                return !this.level.getBlockState(p_27947_.below()).isAir();
            }

            public void tick() {
                super.tick();
            }
        };
        flyingpathnavigation.setCanOpenDoors(false);
        flyingpathnavigation.setCanFloat(false);
        flyingpathnavigation.setCanPassDoors(true);
        return flyingpathnavigation;
    }

    public boolean isFood(ItemStack p_27895_) {
        return p_27895_.is(ItemTags.FLOWERS);
    }

    protected void playStepSound(@NotNull BlockPos p_27820_, @NotNull BlockState p_27821_) {
    }

    protected SoundEvent getAmbientSound() {
        return null;
    }

    protected SoundEvent getHurtSound(@NotNull DamageSource p_27845_) {
        return SoundEvents.BEE_HURT;
    }

    protected SoundEvent getDeathSound() {
        return SoundEvents.BEE_DEATH;
    }

    protected float getSoundVolume() {
        return 0.4F;
    }

    @Nullable
    public ExplodingSporeBullet getBreedOffspring(@NotNull ServerLevel p_148760_, @NotNull AgeableMob p_148761_) {
        //return EntityType.BEE.create(p_148760_);
        return null;
    }

    protected float getStandingEyeHeight(@NotNull Pose p_27804_, @NotNull EntityDimensions p_27805_) {
        return p_27805_.height * 0.5F;
    }

    protected void checkFallDamage(double p_27754_, boolean p_27755_, @NotNull BlockState p_27756_, @NotNull BlockPos p_27757_) {
    }

    public boolean isFlapping() {
        return this.isFlying() && this.tickCount % TICKS_PER_FLAP == 0;
    }

    public boolean isFlying() {
        return !this.onGround;
    }

    @Override
    public boolean hurt(@NotNull DamageSource p_27762_, float p_27763_) {
        if (this.isInvulnerableTo(p_27762_)) {
            return false;
        } else {
            return super.hurt(p_27762_, p_27763_);
        }
    }

    public @NotNull MobType getMobType() {
        return MobType.ARTHROPOD;
    }

    @Deprecated // FORGE: use jumpInFluid instead
    protected void jumpInLiquid(@NotNull TagKey<Fluid> p_204061_) {
        this.jumpInLiquidInternal();
    }

    private void jumpInLiquidInternal() {
        this.setDeltaMovement(this.getDeltaMovement().add(0.0D, 0.01D, 0.0D));
    }

    @Override
    public void jumpInFluid(net.minecraftforge.fluids.@NotNull FluidType type) {
        this.jumpInLiquidInternal();
    }

    public @NotNull Vec3 getLeashOffset() {
        return new Vec3(0.0D, (double) (0.5F * this.getEyeHeight()), (double) (this.getBbWidth() * 0.2F));
    }

    boolean closerThan(BlockPos p_27817_, int p_27818_) {
        return p_27817_.closerThan(this.blockPosition(), (double) p_27818_);
    }

    @Override
    public boolean isAlliedTo(@NotNull Entity entity) {
        boolean isOnSameTeam = super.isAlliedTo(entity);
        return isOnSameTeam || isEntitySameOwnerAsThis(entity, this);
    }

    @Override
    public boolean wantsToAttack(@NotNull LivingEntity target, @NotNull LivingEntity owner) {
        return ITamableEntity.super.wantsToAttack(target, owner);
    }

    @Override
    public void die(@NotNull DamageSource cause) {
        if (getOwner() != null) {
            LazyOptional<PlayerMinionCapabilityInterface> minionCap = getOwner()
                    .getCapability(PlayerMinionCapability.INSTANCE);
            if (!minionCap.isPresent())
                return;
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

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
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
    public @NotNull Level getLevel() {
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

    public boolean canBeLeashed(@NotNull Player player) {
        return false;
    }

    public void updateLevel(Player owner) {
        tame(owner);
        PlayerSkillCapabilityInterface skill = IGenericCapability.getUnwrappedPlayerCapability((Player) getOwner(), PlayerSkillCapability.INSTANCE);
        if (skill != null) {
            /*this.puntosAsignados = skill.getSkillsPoints().get(SkillEnum.SUMMON_SOUL_WOLF);
            saludMaxima = this.puntosAsignados + INITIAL_HEALTH;*/
            // stealingHealth = (0.135f * puntosAsignados) + 0.5;
        }

        random.setSeed(System.currentTimeMillis());
    }

    static class ExplodingShulkerBulletBecomeAngryTargetGoal extends NearestAttackableTargetGoal<Player> {
        ExplodingShulkerBulletBecomeAngryTargetGoal(ExplodingSporeBullet p_27966_) {
            super(p_27966_, Player.class, 10, true, false, p_27966_::isAngryAt);
        }

        public boolean canUse() {
            return this.ExplodingShulkerBulletCanTarget() && super.canUse();
        }

        public boolean canContinueToUse() {
            boolean flag = this.ExplodingShulkerBulletCanTarget();
            if (flag && this.mob.getTarget() != null) {
                return super.canContinueToUse();
            } else {
                this.targetMob = null;
                return false;
            }
        }

        private boolean ExplodingShulkerBulletCanTarget() {
            ExplodingSporeBullet ExplodingSporeBullet = (ExplodingSporeBullet) this.mob;
            return ExplodingSporeBullet.isAngry() && !ExplodingSporeBullet.hasStung();
        }
    }

    class ExplodingShulkerBulletAttackGoal extends ExplodingSporeBullet.MeleeAttackGoal {
        ExplodingShulkerBulletAttackGoal(ExplodingSporeBullet p_27960_, double p_27961_, boolean p_27962_) {
            super(p_27960_, p_27961_, p_27962_);
        }

        public boolean canUse() {
            return super.canUse() && ExplodingSporeBullet.this.isAngry() && !ExplodingSporeBullet.this.hasStung();
        }

        public boolean canContinueToUse() {
            return super.canContinueToUse() && ExplodingSporeBullet.this.isAngry() && !ExplodingSporeBullet.this.hasStung();
        }
    }

    class ExplodingShulkerBulletHurtByOtherGoal extends HurtByTargetGoal {
        ExplodingShulkerBulletHurtByOtherGoal(ExplodingSporeBullet p_28033_) {
            super(p_28033_);
        }

        public boolean canContinueToUse() {
            return ExplodingSporeBullet.this.isAngry() && super.canContinueToUse();
        }

        protected void alertOther(@NotNull Mob p_28035_, @NotNull LivingEntity p_28036_) {
            if (p_28035_ instanceof ExplodingSporeBullet && this.mob.hasLineOfSight(p_28036_)) {
                p_28035_.setTarget(p_28036_);
            }

        }
    }

    class ExplodingSporeBulletLookControl extends LookControl {
        ExplodingSporeBulletLookControl(Mob p_28059_) {
            super(p_28059_);
        }

        public void tick() {
            if (!ExplodingSporeBullet.this.isAngry()) {
                super.tick();
            }
        }

    }

    class ExplodingShulkerBulletWanderGoal extends Goal {
        private static final int WANDER_THRESHOLD = 22;

        ExplodingShulkerBulletWanderGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        public boolean canUse() {
            return ExplodingSporeBullet.this.navigation.isDone() && ExplodingSporeBullet.this.random.nextInt(10) == 0;
        }

        public boolean canContinueToUse() {
            return ExplodingSporeBullet.this.navigation.isInProgress();
        }

        public void start() {
            Vec3 vec3 = this.findPos();
            if (vec3 != null) {
                ExplodingSporeBullet.this.navigation.moveTo(ExplodingSporeBullet.this.navigation.createPath(BlockPos.containing(vec3), 1), 1.0D);
            }

        }
        @Nullable
        private Vec3 findPos() {
            Vec3 vec3;
            vec3 = ExplodingSporeBullet.this.getViewVector(0.0F);
            int i = 8;
            Vec3 vec32 = HoverRandomPos.getPos(ExplodingSporeBullet.this, 8, 7, vec3.x, vec3.z, ((float) Math.PI / 2F), 3, 1);
            return vec32 != null ? vec32 : AirAndWaterRandomPos.getPos(ExplodingSporeBullet.this, 8, 4, -2, vec3.x, vec3.z, (double) ((float) Math.PI / 2F));
        }
    }


    static class MeleeAttackGoal extends Goal {
        protected final ExplodingSporeBullet mob;
        private final double speedModifier;
        private final boolean followingTargetEvenIfNotSeen;
        private Path path;
        private double pathedTargetX;
        private double pathedTargetY;
        private double pathedTargetZ;
        private int ticksUntilNextPathRecalculation;
        private int ticksUntilNextAttack;
        private final int attackInterval = 20;
        private long lastCanUseCheck;
        private static final long COOLDOWN_BETWEEN_CAN_USE_CHECKS = 20L;
        private int failedPathFindingPenalty = 0;
        private boolean canPenalize = false;

        public MeleeAttackGoal(ExplodingSporeBullet p_25552_, double p_25553_, boolean p_25554_) {
            this.mob = p_25552_;
            this.speedModifier = p_25553_;
            this.followingTargetEvenIfNotSeen = p_25554_;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        public boolean canUse() {
            long i = this.mob.level.getGameTime();
            if (i - this.lastCanUseCheck < 20L) {
                return false;
            } else {
                this.lastCanUseCheck = i;
                LivingEntity livingentity = this.mob.getTarget();
                if (livingentity == null) {
                    return false;
                } else if (!livingentity.isAlive()) {
                    return false;
                } else {
                    if (canPenalize) {
                        if (--this.ticksUntilNextPathRecalculation <= 0) {
                            this.path = this.mob.getNavigation().createPath(livingentity, 0);
                            this.ticksUntilNextPathRecalculation = 4 + this.mob.getRandom().nextInt(7);
                            return this.path != null;
                        } else {
                            return true;
                        }
                    }
                    this.path = this.mob.getNavigation().createPath(livingentity, 0);
                    if (this.path != null) {
                        return true;
                    } else {
                        return this.getAttackReachSqr(livingentity) >= this.mob.distanceToSqr(livingentity.getX(), livingentity.getY(), livingentity.getZ());
                    }
                }
            }
        }

        public boolean canContinueToUse() {
            LivingEntity livingentity = this.mob.getTarget();
            if (livingentity == null) {
                return false;
            } else if (!livingentity.isAlive()) {
                return false;
            } else if (!this.followingTargetEvenIfNotSeen) {
                return !this.mob.getNavigation().isDone();
            } else if (!this.mob.isWithinRestriction(livingentity.blockPosition())) {
                return false;
            } else {
                return !(livingentity instanceof Player) || !livingentity.isSpectator() && !((Player)livingentity).isCreative();
            }
        }

        public void start() {
            this.mob.getNavigation().moveTo(this.path, this.speedModifier);
            this.mob.setAggressive(true);
            this.ticksUntilNextPathRecalculation = 0;
            this.ticksUntilNextAttack = 0;
        }

        public void stop() {
            LivingEntity livingentity = this.mob.getTarget();
            if (!EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(livingentity)) {
                this.mob.setTarget((LivingEntity)null);
            }

            this.mob.setAggressive(false);
            this.mob.getNavigation().stop();
        }

        public boolean requiresUpdateEveryTick() {
            return true;
        }

        public void tick() {
            LivingEntity livingentity = this.mob.getTarget();
            if (livingentity != null) {
                this.mob.getLookControl().setLookAt(livingentity, 30.0F, 30.0F);
                double d0 = this.mob.getPerceivedTargetDistanceSquareForMeleeAttack(livingentity);
                this.ticksUntilNextPathRecalculation = Math.max(this.ticksUntilNextPathRecalculation - 1, 0);
                if ((this.followingTargetEvenIfNotSeen || this.mob.getSensing().hasLineOfSight(livingentity)) && this.ticksUntilNextPathRecalculation <= 0 && (this.pathedTargetX == 0.0D && this.pathedTargetY == 0.0D && this.pathedTargetZ == 0.0D || livingentity.distanceToSqr(this.pathedTargetX, this.pathedTargetY, this.pathedTargetZ) >= 1.0D || this.mob.getRandom().nextFloat() < 0.05F)) {
                    this.pathedTargetX = livingentity.getX();
                    this.pathedTargetY = livingentity.getY();
                    this.pathedTargetZ = livingentity.getZ();
                    this.ticksUntilNextPathRecalculation = 4 + this.mob.getRandom().nextInt(7);
                    if (this.canPenalize) {
                        this.ticksUntilNextPathRecalculation += failedPathFindingPenalty;
                        if (this.mob.getNavigation().getPath() != null) {
                            net.minecraft.world.level.pathfinder.Node finalPathPoint = this.mob.getNavigation().getPath().getEndNode();
                            if (finalPathPoint != null && livingentity.distanceToSqr(finalPathPoint.x, finalPathPoint.y, finalPathPoint.z) < 1)
                                failedPathFindingPenalty = 0;
                            else
                                failedPathFindingPenalty += 10;
                        } else {
                            failedPathFindingPenalty += 10;
                        }
                    }
                    if (d0 > 1024.0D) {
                        this.ticksUntilNextPathRecalculation += 10;
                    } else if (d0 > 256.0D) {
                        this.ticksUntilNextPathRecalculation += 5;
                    }

                    if (!this.mob.getNavigation().moveTo(livingentity, this.speedModifier)) {
                        this.ticksUntilNextPathRecalculation += 15;
                    }

                    this.ticksUntilNextPathRecalculation = this.adjustedTickDelay(this.ticksUntilNextPathRecalculation);
                }

                this.ticksUntilNextAttack = Math.max(this.ticksUntilNextAttack - 1, 0);
                this.checkAndPerformAttack(livingentity, d0);
            }
        }

        protected void checkAndPerformAttack(LivingEntity p_25557_, double p_25558_) {
            double d0 = this.getAttackReachSqr(p_25557_);
            if (p_25558_ <= d0 && this.ticksUntilNextAttack <= 0) {
                this.resetAttackCooldown();
                this.mob.swing(InteractionHand.MAIN_HAND);
                this.mob.doHurtTarget(p_25557_);
                this.mob.explodeCreeper();
            }

        }

        protected void resetAttackCooldown() {
            this.ticksUntilNextAttack = this.adjustedTickDelay(20);
        }

        protected boolean isTimeToAttack() {
            return this.ticksUntilNextAttack <= 0;
        }

        protected int getTicksUntilNextAttack() {
            return this.ticksUntilNextAttack;
        }

        protected int getAttackInterval() {
            return this.adjustedTickDelay(20);
        }

        protected double getAttackReachSqr(LivingEntity p_25556_) {
            return (double)(this.mob.getBbWidth() * 2.0F * this.mob.getBbWidth() * 2.0F + p_25556_.getBbWidth());
        }


    }

    private void explodeCreeper() {
        if (!this.level.isClientSide) {
            float explosionRadius = 2;
            this.dead = true;
            this.level.explode(this, this.getX(), this.getY(), this.getZ(), explosionRadius,true, Level.ExplosionInteraction.MOB);
            this.discard();
            this.spawnLingeringCloud();
        }

    }

    private void spawnLingeringCloud() {
        Collection<MobEffectInstance> collection = this.getActiveEffects();
        if (!collection.isEmpty()) {
            AreaEffectCloud areaeffectcloud = new AreaEffectCloud(this.level, this.getX(), this.getY(), this.getZ());
            areaeffectcloud.setRadius(2.0F);
            areaeffectcloud.setRadiusOnUse(-0.5F);
            areaeffectcloud.setWaitTime(10);
            areaeffectcloud.setDuration(areaeffectcloud.getDuration() / 2);
            areaeffectcloud.setRadiusPerTick(-areaeffectcloud.getRadius() / (float)areaeffectcloud.getDuration());

            for(MobEffectInstance mobeffectinstance : collection) {
                areaeffectcloud.addEffect(new MobEffectInstance(mobeffectinstance));
            }

            this.level.addFreshEntity(areaeffectcloud);
        }

    }

}
