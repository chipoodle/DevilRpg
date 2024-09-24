package com.chipoodle.devilrpg.entity;

import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapability;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.util.IRenderUtilities;
import com.google.common.collect.Lists;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.Difficulty;
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
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ExplodingSporeBullet extends TamableAnimal implements NeutralMob, FlyingAnimal, ITamableEntity, ISoulEntity {
    public static final float FLAP_DEGREES_PER_TICK = 120.32113F;
    public static final int TICKS_PER_FLAP = Mth.ceil(1.4959966F);
    public static final String TAG_CANNOT_ENTER_HIVE_TICKS = "CannotEnterHiveTicks";
    public static final String TAG_TICKS_SINCE_POLLINATION = "TicksSincePollination";
    public static final String TAG_HAS_STUNG = "HasStung";
    public static final String TAG_HIVE_POS = "HivePos";
    private static final EntityDataAccessor<Byte> DATA_FLAGS_ID = SynchedEntityData.defineId(ExplodingSporeBullet.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_REMAINING_ANGER_TIME = SynchedEntityData.defineId(ExplodingSporeBullet.class, EntityDataSerializers.INT);
    private static final int FLAG_ROLL = 2;
    private static final int FLAG_HAS_STUNG = 4;
    private static final int FLAG_HAS_NECTAR = 8;
    private static final int STING_DEATH_COUNTDOWN = 1200;
    private static final int TICKS_BEFORE_GOING_TO_KNOWN_FLOWER = 2400;
    private static final int TICKS_WITHOUT_NECTAR_BEFORE_GOING_HOME = 3600;
    private static final int MIN_ATTACK_DIST = 4;
    private static final int MAX_CROPS_GROWABLE = 10;
    private static final int POISON_SECONDS_NORMAL = 10;
    private static final int POISON_SECONDS_HARD = 18;
    private static final int TOO_FAR_DISTANCE = 32;
    private static final int HIVE_CLOSE_ENOUGH_DISTANCE = 2;
    private static final int PATHFIND_TO_HIVE_WHEN_CLOSER_THAN = 16;
    private static final int HIVE_SEARCH_DISTANCE = 20;
    private static final UniformInt PERSISTENT_ANGER_TIME = TimeUtil.rangeOfSeconds(20, 39);
    private static final int COOLDOWN_BEFORE_LOCATING_NEW_HIVE = 200;
    int remainingCooldownBeforeLocatingNewHive;
    int remainingCooldownBeforeLocatingNewFlower = Mth.nextInt(this.random, 20, 60);
    @Nullable
    BlockPos hivePos;
    ExplodingSporeBullet.ExplodingShulkerBulletGoToHiveGoal goToHiveGoal;
    @Nullable
    private UUID persistentAngerTarget;
    private float rollAmount;
    private float rollAmountO;
    private int timeSinceSting;
    private int stayOutOfHiveCountdown;
    private int underWaterTicks;

    public ExplodingSporeBullet(EntityType<? extends ExplodingSporeBullet> p_27717_, Level p_27718_) {
        super(p_27717_, p_27718_);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.lookControl = new ExplodingSporeBulletLookControl(this);
        this.setPathfindingMalus(BlockPathTypes.DANGER_FIRE, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.WATER, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.WATER_BORDER, 16.0F);
        this.setPathfindingMalus(BlockPathTypes.COCOA, -1.0F);
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
        this.goalSelector.addGoal(0, new ExplodingSporeBullet.ExplodingShulkerBulletAttackGoal(this, (double) 1.4F, true));
        this.goalSelector.addGoal(1, new ExplodingSporeBullet.ExplodingShulkerBulletEnterHiveGoal());
        this.goalSelector.addGoal(5, new ExplodingSporeBullet.ExplodingShulkerBulletLocateHiveGoal());
        this.goToHiveGoal = new ExplodingSporeBullet.ExplodingShulkerBulletGoToHiveGoal();
        this.goalSelector.addGoal(5, this.goToHiveGoal);
        this.goalSelector.addGoal(8, new ExplodingSporeBullet.ExplodingShulkerBulletWanderGoal());
        this.goalSelector.addGoal(9, new FloatGoal(this));
        this.targetSelector.addGoal(1, (new ExplodingSporeBullet.ExplodingShulkerBulletHurtByOtherGoal(this)).setAlertOthers(new Class[0]));
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
        if (this.hasHive()) {
            compoundTag.put("HivePos", NbtUtils.writeBlockPos(this.getHivePos()));
        }

        compoundTag.putBoolean("HasStung", this.hasStung());
        compoundTag.putInt("CannotEnterHiveTicks", this.stayOutOfHiveCountdown);
        this.addPersistentAngerSaveData(compoundTag);
        compoundTag.putString("OwnerUUID", "");
        compoundTag.putString("Owner", "");
    }

    public void readAdditionalSaveData(CompoundTag p_27793_) {
        this.hivePos = null;
        if (p_27793_.contains("HivePos")) {
            this.hivePos = NbtUtils.readBlockPos(p_27793_.getCompound("HivePos"));
        }

        super.readAdditionalSaveData(p_27793_);
        this.setHasStung(p_27793_.getBoolean("HasStung"));
        this.stayOutOfHiveCountdown = p_27793_.getInt("CannotEnterHiveTicks");
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

    @VisibleForDebug
    public List<BlockPos> getBlacklistedHives() {
        return this.goToHiveGoal.blacklistedTargets;
    }


    boolean wantsToEnterHive() {
        if (this.stayOutOfHiveCountdown <= 0 && !this.hasStung() && this.getTarget() == null) {
            boolean flag = this.level.isRaining() || this.level.isNight();
            return flag && !this.isHiveNearFire();
        } else {
            return false;
        }
    }

    public void setStayOutOfHiveCountdown(int p_27916_) {
        this.stayOutOfHiveCountdown = p_27916_;
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

    private boolean isHiveNearFire() {
        if (this.hivePos == null) {
            return false;
        } else {
            BlockEntity blockentity = this.level.getBlockEntity(this.hivePos);
            return blockentity instanceof BeehiveBlockEntity && ((BeehiveBlockEntity) blockentity).isFireNearby();
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
    public boolean hasHive() {
        return this.hivePos != null;
    }

    @Nullable
    @VisibleForDebug
    public BlockPos getHivePos() {
        return this.hivePos;
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
            if (this.stayOutOfHiveCountdown > 0) {
                --this.stayOutOfHiveCountdown;
            }

            if (this.remainingCooldownBeforeLocatingNewHive > 0) {
                --this.remainingCooldownBeforeLocatingNewHive;
            }

            if (this.remainingCooldownBeforeLocatingNewFlower > 0) {
                --this.remainingCooldownBeforeLocatingNewFlower;
            }

            boolean flag = this.isAngry() && !this.hasStung() && this.getTarget() != null && this.getTarget().distanceToSqr(this) < 4.0D;
            this.setRolling(flag);
            if (this.tickCount % 20 == 0 && !this.isHiveValid()) {
                this.hivePos = null;
            }
        }

        addToAiStep(this);

    }

    boolean isHiveValid() {
        if (!this.hasHive()) {
            return false;
        } else if (this.isTooFarAway(this.hivePos)) {
            return false;
        } else {
            BlockEntity blockentity = this.level.getBlockEntity(this.hivePos);
            return blockentity instanceof BeehiveBlockEntity;
        }
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

    abstract class BaseExplodingShulkerBulletGoal extends Goal {
        public abstract boolean canExplodingShulkerBulletUse();

        public abstract boolean canExplodingShulkerBulletContinueToUse();

        public boolean canUse() {
            return this.canExplodingShulkerBulletUse() && !ExplodingSporeBullet.this.isAngry();
        }

        public boolean canContinueToUse() {
            return this.canExplodingShulkerBulletContinueToUse() && !ExplodingSporeBullet.this.isAngry();
        }
    }

    class ExplodingShulkerBulletAttackGoal extends MeleeAttackGoal {
        ExplodingShulkerBulletAttackGoal(PathfinderMob p_27960_, double p_27961_, boolean p_27962_) {
            super(p_27960_, p_27961_, p_27962_);
        }

        public boolean canUse() {
            return super.canUse() && ExplodingSporeBullet.this.isAngry() && !ExplodingSporeBullet.this.hasStung();
        }

        public boolean canContinueToUse() {
            return super.canContinueToUse() && ExplodingSporeBullet.this.isAngry() && !ExplodingSporeBullet.this.hasStung();
        }
    }

    class ExplodingShulkerBulletEnterHiveGoal extends ExplodingSporeBullet.BaseExplodingShulkerBulletGoal {
        public boolean canExplodingShulkerBulletUse() {
            if (ExplodingSporeBullet.this.hasHive() && ExplodingSporeBullet.this.wantsToEnterHive() && ExplodingSporeBullet.this.hivePos.closerToCenterThan(ExplodingSporeBullet.this.position(), 2.0D)) {
                BlockEntity blockentity = ExplodingSporeBullet.this.level.getBlockEntity(ExplodingSporeBullet.this.hivePos);
                if (blockentity instanceof BeehiveBlockEntity) {
                    BeehiveBlockEntity BeehiveBlockEntity = (BeehiveBlockEntity) blockentity;
                    if (!BeehiveBlockEntity.isFull()) {
                        return true;
                    }

                    ExplodingSporeBullet.this.hivePos = null;
                }
            }

            return false;
        }

        public boolean canExplodingShulkerBulletContinueToUse() {
            return false;
        }

        public void start() {
            BlockEntity blockentity = ExplodingSporeBullet.this.level.getBlockEntity(ExplodingSporeBullet.this.hivePos);
            if (blockentity instanceof BeehiveBlockEntity BeehiveBlockEntity) {
                BeehiveBlockEntity.addOccupant(ExplodingSporeBullet.this, true);
            }

        }
    }

    @VisibleForDebug
    public class ExplodingShulkerBulletGoToHiveGoal extends ExplodingSporeBullet.BaseExplodingShulkerBulletGoal {
        public static final int MAX_TRAVELLING_TICKS = 600;
        private static final int MAX_BLACKLISTED_TARGETS = 3;
        private static final int TICKS_BEFORE_HIVE_DROP = 60;
        final List<BlockPos> blacklistedTargets = Lists.newArrayList();
        int travellingTicks = ExplodingSporeBullet.this.level.random.nextInt(10);
        @Nullable
        private Path lastPath;
        private int ticksStuck;

        ExplodingShulkerBulletGoToHiveGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        public boolean canExplodingShulkerBulletUse() {
            return ExplodingSporeBullet.this.hivePos != null && !ExplodingSporeBullet.this.hasRestriction() && ExplodingSporeBullet.this.wantsToEnterHive() && !this.hasReachedTarget(ExplodingSporeBullet.this.hivePos) && ExplodingSporeBullet.this.level.getBlockState(ExplodingSporeBullet.this.hivePos).is(BlockTags.BEEHIVES);
        }

        public boolean canExplodingShulkerBulletContinueToUse() {
            return this.canExplodingShulkerBulletUse();
        }

        public void start() {
            this.travellingTicks = 0;
            this.ticksStuck = 0;
            super.start();
        }

        public void stop() {
            this.travellingTicks = 0;
            this.ticksStuck = 0;
            ExplodingSporeBullet.this.navigation.stop();
            ExplodingSporeBullet.this.navigation.resetMaxVisitedNodesMultiplier();
        }

        public void tick() {
            if (ExplodingSporeBullet.this.hivePos != null) {
                ++this.travellingTicks;
                if (this.travellingTicks > this.adjustedTickDelay(600)) {
                    this.dropAndBlacklistHive();
                } else if (!ExplodingSporeBullet.this.navigation.isInProgress()) {
                    if (!ExplodingSporeBullet.this.closerThan(ExplodingSporeBullet.this.hivePos, 16)) {
                        if (ExplodingSporeBullet.this.isTooFarAway(ExplodingSporeBullet.this.hivePos)) {
                            this.dropHive();
                        } else {
                            ExplodingSporeBullet.this.pathfindRandomlyTowards(ExplodingSporeBullet.this.hivePos);
                        }
                    } else {
                        boolean flag = this.pathfindDirectlyTowards(ExplodingSporeBullet.this.hivePos);
                        if (!flag) {
                            this.dropAndBlacklistHive();
                        } else if (this.lastPath != null && ExplodingSporeBullet.this.navigation.getPath().sameAs(this.lastPath)) {
                            ++this.ticksStuck;
                            if (this.ticksStuck > 60) {
                                this.dropHive();
                                this.ticksStuck = 0;
                            }
                        } else {
                            this.lastPath = ExplodingSporeBullet.this.navigation.getPath();
                        }

                    }
                }
            }
        }

        private boolean pathfindDirectlyTowards(BlockPos p_27991_) {
            ExplodingSporeBullet.this.navigation.setMaxVisitedNodesMultiplier(10.0F);
            ExplodingSporeBullet.this.navigation.moveTo((double) p_27991_.getX(), (double) p_27991_.getY(), (double) p_27991_.getZ(), 1.0D);
            return ExplodingSporeBullet.this.navigation.getPath() != null && ExplodingSporeBullet.this.navigation.getPath().canReach();
        }

        boolean isTargetBlacklisted(BlockPos p_27994_) {
            return this.blacklistedTargets.contains(p_27994_);
        }

        private void blacklistTarget(BlockPos p_27999_) {
            this.blacklistedTargets.add(p_27999_);

            while (this.blacklistedTargets.size() > 3) {
                this.blacklistedTargets.remove(0);
            }

        }

        void clearBlacklist() {
            this.blacklistedTargets.clear();
        }

        private void dropAndBlacklistHive() {
            if (ExplodingSporeBullet.this.hivePos != null) {
                this.blacklistTarget(ExplodingSporeBullet.this.hivePos);
            }

            this.dropHive();
        }

        private void dropHive() {
            ExplodingSporeBullet.this.hivePos = null;
            ExplodingSporeBullet.this.remainingCooldownBeforeLocatingNewHive = 200;
        }

        private boolean hasReachedTarget(BlockPos p_28002_) {
            if (ExplodingSporeBullet.this.closerThan(p_28002_, 2)) {
                return true;
            } else {
                Path path = ExplodingSporeBullet.this.navigation.getPath();
                return path != null && path.getTarget().equals(p_28002_) && path.canReach() && path.isDone();
            }
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

    class ExplodingShulkerBulletLocateHiveGoal extends ExplodingSporeBullet.BaseExplodingShulkerBulletGoal {
        public boolean canExplodingShulkerBulletUse() {
            return ExplodingSporeBullet.this.remainingCooldownBeforeLocatingNewHive == 0 && !ExplodingSporeBullet.this.hasHive() && ExplodingSporeBullet.this.wantsToEnterHive();
        }

        public boolean canExplodingShulkerBulletContinueToUse() {
            return false;
        }

        public void start() {
            ExplodingSporeBullet.this.remainingCooldownBeforeLocatingNewHive = 200;
            List<BlockPos> list = this.findNearbyHivesWithSpace();
            if (!list.isEmpty()) {
                for (BlockPos blockpos : list) {
                    if (!ExplodingSporeBullet.this.goToHiveGoal.isTargetBlacklisted(blockpos)) {
                        ExplodingSporeBullet.this.hivePos = blockpos;
                        return;
                    }
                }

                ExplodingSporeBullet.this.goToHiveGoal.clearBlacklist();
                ExplodingSporeBullet.this.hivePos = list.get(0);
            }
        }

        private List<BlockPos> findNearbyHivesWithSpace() {
            BlockPos blockpos = ExplodingSporeBullet.this.blockPosition();
            PoiManager poimanager = ((ServerLevel) ExplodingSporeBullet.this.level).getPoiManager();
            Stream<PoiRecord> stream = poimanager.getInRange((p_218130_) -> {
                return p_218130_.is(PoiTypeTags.BEE_HOME);
            }, blockpos, 20, PoiManager.Occupancy.ANY);
            return stream.map(PoiRecord::getPos).filter(ExplodingSporeBullet.this::doesHiveHaveSpace).sorted(Comparator.comparingDouble((p_148811_) -> {
                return p_148811_.distSqr(blockpos);
            })).collect(Collectors.toList());
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
            if (ExplodingSporeBullet.this.isHiveValid() && !ExplodingSporeBullet.this.closerThan(ExplodingSporeBullet.this.hivePos, 22)) {
                Vec3 vec31 = Vec3.atCenterOf(ExplodingSporeBullet.this.hivePos);
                vec3 = vec31.subtract(ExplodingSporeBullet.this.position()).normalize();
            } else {
                vec3 = ExplodingSporeBullet.this.getViewVector(0.0F);
            }

            int i = 8;
            Vec3 vec32 = HoverRandomPos.getPos(ExplodingSporeBullet.this, 8, 7, vec3.x, vec3.z, ((float) Math.PI / 2F), 3, 1);
            return vec32 != null ? vec32 : AirAndWaterRandomPos.getPos(ExplodingSporeBullet.this, 8, 4, -2, vec3.x, vec3.z, (double) ((float) Math.PI / 2F));
        }
    }

}
