package com.chipoodle.devilrpg.entity;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapabilityInterface;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapabilityAttacher;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityAttacher;
import com.chipoodle.devilrpg.client.render.IRenderUtilities;
import com.chipoodle.devilrpg.entity.goal.TameableMountableFollowOwnerGoal;
import com.chipoodle.devilrpg.entity.goal.TameableMountableOwnerHurtByTargetGoal;
import com.chipoodle.devilrpg.entity.goal.TameableMountableOwnerHurtTargetGoal;
import com.chipoodle.devilrpg.init.ModEntityTypes;
import com.chipoodle.devilrpg.util.SkillEnum;
import com.chipoodle.devilrpg.util.TargetUtils;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.merchant.villager.VillagerEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.TurtleEntity;
import net.minecraft.entity.passive.horse.CoatColors;
import net.minecraft.entity.passive.horse.CoatTypes;
import net.minecraft.entity.passive.horse.LlamaEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.HorseArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.IPacket;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.IServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import net.minecraftforge.fml.network.FMLPlayMessages;
import net.minecraftforge.fml.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class SoulBearEntity extends AbstractMountableTameableEntity
        implements ITameableEntity, ISoulEntity, IChargeableMob, IAngerable, IPassiveMinionUpdater<SoulBearEntity> {

    private static final UUID ARMOR_MODIFIER_UUID = UUID.fromString("556E1665-8B10-40C8-8F9D-CF9B1667F295");
    private static final DataParameter<Integer> DATA_ID_TYPE_VARIANT = EntityDataManager.defineId(SoulBearEntity.class, DataSerializers.INT);
    private static final RangedInteger PERSISTENT_ANGER_TIME = TickRangeConverter.rangeOfSeconds(20, 39);
    private static final int PROBABILITY_MULTIPLIER = 3;
    private static final int DURATION_TICKS = 100;

    private static final double RADIUS_PARTICLES = 1.0;
    private static final int NUMBER_OF_PARTICLES_WAR_BEAR = 15;
    private static final double SPLASH_DAMAGE_FACTOR = 0.5F;
    //private static final DataParameter<Boolean> DATA_STANDING_ID = EntityDataManager.defineId(SoulBearEntity.class,DataSerializers.BOOLEAN);
    private final int SALUD_INICIAL = 20;
    private float clientSideStandAnimationO;
    private float clientSideStandAnimation;
    private int warningSoundTicks;
    private int remainingPersistentAngerTime;
    private UUID persistentAngerTarget;
    private boolean orderedToSit;
    private int puntosAsignados = 0;
    private double saludMaxima = SALUD_INICIAL;
    private double initialArmor;

    private boolean isAttacking;

    private Integer warBear = 0;
    private Integer mountBear = 0;


    public SoulBearEntity(EntityType<? extends SoulBearEntity> type, World worldIn) {
        super(type, worldIn);
    }

    public static AttributeModifierMap.MutableAttribute setAttributes() {
        return MobEntity
                .createMobAttributes()
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.MAX_HEALTH, 8.0D)
                .add(Attributes.FOLLOW_RANGE, 16.0D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.ARMOR, 0.35D)
                .add(Attributes.JUMP_STRENGTH,0.7D);
    }

    public AgeableEntity getBreedOffspring(ServerWorld level, AgeableEntity ageable) {
        return ModEntityTypes.SOUL_BEAR.get().create(level);
    }

    /**
     * Checks if the parameter is an item which this animal can be fed to breed it
     * (wheat, carrots or seeds depending on the animal type)
     */
    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Override
    protected void registerGoals() {
        // super.registerGoals();
        this.goalSelector.addGoal(0, new SwimGoal(this));
        this.goalSelector.addGoal(1, new SoulBearEntity.MeleeAttackGoal());
        this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.25D));
        this.goalSelector.addGoal(5, new RandomWalkingGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new TameableMountableFollowOwnerGoal(this, 1.0D, 10.0F, 2.0F, false));
        this.goalSelector.addGoal(7, new LookAtGoal(this, PlayerEntity.class, 6.0F));
        this.goalSelector.addGoal(8, new LookRandomlyGoal(this));
        this.targetSelector.addGoal(1, new TameableMountableOwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new TameableMountableOwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(3, (new HurtByTargetGoal(this)).setAlertOthers());
        this.targetSelector.addGoal(5,
                new NearestAttackableTargetGoal<>(this, MobEntity.class, 10, false, false, (entity) -> {
                    return !(entity instanceof VillagerEntity) && !(entity instanceof LlamaEntity)
                            && !(entity instanceof TurtleEntity) && !(entity instanceof IronGolemEntity);
                }));
        this.targetSelector.addGoal(8, new ResetAngerGoal<>(this, true));

    }

    public void updateLevel(PlayerEntity owner) {
        tame(owner);

        PlayerSkillCapabilityInterface skill = IGenericCapability.getUnwrappedPlayerCapability((PlayerEntity) getOwner(),
                PlayerSkillCapabilityAttacher.SKILL_CAP);
        if (skill != null) {
            this.puntosAsignados = skill.getSkillsPoints().get(SkillEnum.SUMMON_SOUL_BEAR);
            saludMaxima = 5 * this.puntosAsignados + SALUD_INICIAL;
            initialArmor = (1.0D * 0.309 * puntosAsignados) + 2;

        }




        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.4F);
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(saludMaxima);
        this.getAttribute(Attributes.ARMOR).setBaseValue(initialArmor);
        this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(16.0D);
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue((1.5 * puntosAsignados) + 4); // 4-34
        //TODO: generar valores del salto de acerdo al nivel
       // this.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(this.generateRandomJumpStrength());
        this.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(0.7D+(mountBear/5));
        setHealth((float) saludMaxima);
    }

    @Override
    public void readAdditionalSaveData(CompoundNBT compound) {
        super.readAdditionalSaveData(compound);

        this.orderedToSit = compound.getBoolean("Sitting");
        this.setInSittingPose(this.orderedToSit);

        //this.setTypeVariant(compound.getInt("Variant"));
        if (compound.contains("ArmorItem", 10)) {
            ItemStack itemstack = ItemStack.of(compound.getCompound("ArmorItem"));
            if (!itemstack.isEmpty() && this.isArmor(itemstack)) {
                this.inventory.setItem(1, itemstack);
            }
        }

        this.updateContainerEquipment();
    }

    @Override
    public void addAdditionalSaveData(CompoundNBT compound) {
        super.addAdditionalSaveData(compound);
        compound.putString("OwnerUUID", "");
        compound.putString("Owner", "");

        compound.putBoolean("Sitting", this.orderedToSit);

        //compound.putInt("Variant", this.getTypeVariant());
        if (!this.inventory.getItem(1).isEmpty()) {
            compound.put("ArmorItem", this.inventory.getItem(1).save(new CompoundNBT()));
        }
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
    public boolean isAlliedTo(Entity entity) {
        boolean isOnSameTeam = super.isAlliedTo(entity);
        return isOnSameTeam || isEntitySameOwnerAsThis(entity, this);
    }

    @Override
    public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        return ITameableEntity.super.wantsToAttack(target, owner);
        //return ((ITameableEntity) this).wantsToAttack(target, owner);
    }

    @Override
    public boolean doHurtTarget(Entity entityIn) {
        double attackDamage = this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        boolean flag = entityIn.hurt(DamageSource.mobAttack(this), (float) (attackDamage));
        if (flag) {
            int probability = random.nextInt(100);
            if (warBear > 0 && probability <= (warBear * PROBABILITY_MULTIPLIER)) {
                IRenderUtilities.rotationParticles(Minecraft.getInstance().level, random, this,
                        ParticleTypes.CRIMSON_SPORE, NUMBER_OF_PARTICLES_WAR_BEAR, RADIUS_PARTICLES);
                double radius = 3;
                List<LivingEntity> acquireAllLookTargetsByClass = TargetUtils
                        .acquireAllTargetsInRadiusByClass(this, LivingEntity.class, radius).stream()
                        .filter(entity -> !isAlliedTo(entity)).collect(Collectors.toList());

                acquireAllLookTargetsByClass.forEach(
                        mob -> mob.hurt(DamageSource.mobAttack(this), (float) (attackDamage * SPLASH_DAMAGE_FACTOR)));
                DevilRpg.LOGGER.info("---------->doHurtTarget warBear: {} prob: {} limit: {} enemies: {}", warBear,
                        probability, warBear * PROBABILITY_MULTIPLIER, acquireAllLookTargetsByClass.size());
            }
            this.doEnchantDamageEffects(this, entityIn);
        }
        return flag;
    }

    @Override
    public boolean hurt(DamageSource damageSource, float amount) {
        boolean hurt = super.hurt(damageSource, amount);
        if (hurt) {

        }
        return hurt;
    }

    @Override
    protected boolean isImmobile() {
        if (!isAttacking)
            return super.isImmobile();
        return this.isDeadOrDying();
    }

    public void startPersistentAngerTimer() {
        this.setRemainingPersistentAngerTime(PERSISTENT_ANGER_TIME.randomValue(this.random));
    }

    public int getRemainingPersistentAngerTime() {
        return this.remainingPersistentAngerTime;
    }

    public void setRemainingPersistentAngerTime(int p_230260_1_) {
        this.remainingPersistentAngerTime = p_230260_1_;
    }

    public UUID getPersistentAngerTarget() {
        return this.persistentAngerTarget;
    }

    public void setPersistentAngerTarget(@Nullable UUID p_230259_1_) {
        this.persistentAngerTarget = p_230259_1_;
    }

    /**
     * Sets the active target the Task system uses for tracking
     */
    @Override
    public void setTarget(@Nullable LivingEntity entitylivingbaseIn) {
        super.setTarget(entitylivingbaseIn);
    }

    @Override
    public void remove() {
        super.remove();
    }

    /**
     * Called when the mob's health reaches 0.
     */
    @Override
    public void die(DamageSource cause) {
        if (getOwner() != null) {
            LazyOptional<PlayerMinionCapabilityInterface> minionCap = getOwner()
                    .getCapability(PlayerMinionCapabilityAttacher.MINION_CAP);
            if (!minionCap.isPresent())
                return;
            minionCap.ifPresent(x -> x.removeSoulBear((PlayerEntity) getOwner(), this));
        }
        // super.onDeath(cause);
        customOnDeath();
    }

    private void customOnDeath() {
        level.broadcastEntityEvent(this, (byte) 3);
        this.dead = true;
        this.remove();
        IRenderUtilities.customDeadParticles(this.level, this.random, this);
    }

    /**
     * Called on the logical server to get a packet to send to the client containing
     * data necessary to spawn your entity. Using Forge's method instead of the
     * default vanilla one allows extra stuff to work such as sending extra data,
     * using a non-default entity factory and having
     * {@link IEntityAdditionalSpawnData} work.
     * <p>
     * It is not actually necessary for our WildBoarEntity to use Forge's method as
     * it doesn't need any of this extra functionality, however, this is an example
     * mod and many modders are unaware that Forge's method exists.
     *
     * @return The packet with data about your entity
     * @see FMLPlayMessages.SpawnEntity
     */
    @Override
    public IPacket<?> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    protected SoundEvent getAmbientSound() {
        return this.isBaby() ? SoundEvents.POLAR_BEAR_AMBIENT_BABY : SoundEvents.POLAR_BEAR_AMBIENT;
    }

    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        return SoundEvents.POLAR_BEAR_HURT;
    }

    protected SoundEvent getDeathSound() {
        return SoundEvents.POLAR_BEAR_DEATH;
    }

    protected void playStepSound(BlockPos pos, BlockState blockIn) {
        this.playSound(SoundEvents.POLAR_BEAR_STEP, 0.15F, 1.0F);
    }

    protected void playWarningSound() {
        if (this.warningSoundTicks <= 0) {
            this.playSound(SoundEvents.POLAR_BEAR_WARNING, 1.0F, this.getVoicePitch());
            this.warningSoundTicks = 40;
        }

    }

    protected void defineSynchedData() {
        super.defineSynchedData();
        //this.entityData.define(DATA_STANDING_ID, false);
    }

    public void containerChanged(IInventory p_76316_1_) {
        ItemStack itemstack = this.getArmor();
        super.containerChanged(p_76316_1_);
        ItemStack itemstack1 = this.getArmor();
        if (this.tickCount > 20 && this.isArmor(itemstack1) && itemstack != itemstack1) {
            this.playSound(SoundEvents.HORSE_ARMOR, 0.5F, 1.0F);
        }

    }

    public boolean canWearArmor() {
        return true;
    }

    public boolean isArmor(ItemStack p_190682_1_) {
        return p_190682_1_.getItem() instanceof HorseArmorItem;
    }


    /**
     * Called to update the entity's position/logic.
     */
    public void tick() {
        super.tick();
        if (this.level.isClientSide) {
            if (this.clientSideStandAnimation != this.clientSideStandAnimationO) {
                this.refreshDimensions();
            }

            this.clientSideStandAnimationO = this.clientSideStandAnimation;
            if (this.isStanding()) {
                this.clientSideStandAnimation = MathHelper.clamp(this.clientSideStandAnimation + 1.0F, 0.0F, 6.0F);
            } else {
                this.clientSideStandAnimation = MathHelper.clamp(this.clientSideStandAnimation - 1.0F, 0.0F, 6.0F);
            }
        }

        if (this.warningSoundTicks > 0) {
            --this.warningSoundTicks;
        }

        if (!this.level.isClientSide) {
            this.updatePersistentAnger((ServerWorld) this.level, true);
        }

    }

    /*@Override
    public ActionResultType mobInteract(PlayerEntity playerIn, Hand hand) {
        return ActionResultType.PASS;
    }*/


    public ActionResultType mobInteract(PlayerEntity p_230254_1_, Hand p_230254_2_) {

        if(mountBear <= 0){
            return ActionResultType.PASS;
        }


        ItemStack itemstack = p_230254_1_.getItemInHand(p_230254_2_);
        if (!this.isBaby()) {
            if (this.isTame() && p_230254_1_.isSecondaryUseActive()) {
                this.openInventory(p_230254_1_);
                return ActionResultType.sidedSuccess(this.level.isClientSide);
            }

            if (this.isVehicle()) {
                return super.mobInteract(p_230254_1_, p_230254_2_);
            }
        }

        this.doPlayerRide(p_230254_1_);
        return ActionResultType.sidedSuccess(this.level.isClientSide);
    }


    @Override
    public EntitySize getDimensions(Pose poseIn) {
        if (this.clientSideStandAnimation > 0.0F) {
            float f = this.clientSideStandAnimation / 6.0F;
            float f1 = 1.0F + f;
            return super.getDimensions(poseIn).scale(1.0F, f1);
        } else {
            return super.getDimensions(poseIn);
        }
    }


    // Method from PolarBear
    @Override
    public boolean isStanding() {
        return super.isStanding();
        //return this.getFlag(32);
        //return this.entityData.get(DATA_STANDING_ID);
    }


    // Method from PolarBear
    @Override
    public void setStanding(boolean isStanding) {
        super.setStanding(isStanding);
        //this.setFlag(32, isStanding);
        //this.entityData.set(DATA_STANDING_ID, isStanding);
    }

    @OnlyIn(Dist.CLIENT)
    public float getStandingAnimationScale(float scale) {
        return MathHelper.lerp(scale, this.clientSideStandAnimationO, this.clientSideStandAnimation) / 6.0F;
    }

    @Override
    protected float getWaterSlowDown() {
        return 0.98F;
    }

    public ILivingEntityData finalizeSpawn(IServerWorld worldIn, DifficultyInstance difficultyIn, SpawnReason reason,
                                           @Nullable ILivingEntityData spawnDataIn, @Nullable CompoundNBT dataTag) {
        if (spawnDataIn == null) {
            spawnDataIn = new AgeableEntity.AgeableData(1.0F);
        }
        return super.finalizeSpawn(worldIn, difficultyIn, reason, spawnDataIn, dataTag);
    }

    public boolean isAttacking() {
        return isAttacking;
    }

    public void setAttacking(boolean attacking) {
        //DevilRpg.LOGGER.info("------- bear setAttacking: {}", attacking);
        isAttacking = attacking;
    }

    /**
     * Get the experience points the entity currently has.
     */
    protected int getExperienceReward(PlayerEntity player) {
        /*
         * if (player.equals(getOwner())) return 0; return 1 +
         * this.world.rand.nextInt(3);
         */
        return 0;
    }

    public ItemStack getArmor() {
        return this.getItemBySlot(EquipmentSlotType.CHEST);
    }

    private void setArmor(ItemStack p_213805_1_) {
        this.setItemSlot(EquipmentSlotType.CHEST, p_213805_1_);
        this.setDropChance(EquipmentSlotType.CHEST, 0.0F);
    }

    private int getTypeVariant() {
        return this.entityData.get(DATA_ID_TYPE_VARIANT);
    }

    private void setTypeVariant(int p_234242_1_) {
        this.entityData.set(DATA_ID_TYPE_VARIANT, p_234242_1_);
    }

    private void setVariantAndMarkings(CoatColors p_234238_1_, CoatTypes p_234238_2_) {
        this.setTypeVariant(p_234238_1_.getId() & 255 | p_234238_2_.getId() << 8 & '\uff00');
    }

    public CoatColors getVariant() {
        return CoatColors.byId(this.getTypeVariant() & 255);
    }

    public CoatTypes getMarkings() {
        return CoatTypes.byId((this.getTypeVariant() & '\uff00') >> 8);
    }

    protected void updateContainerEquipment() {
        if (!this.level.isClientSide) {
            super.updateContainerEquipment();
            this.setArmorEquipment(this.inventory.getItem(1));
            this.setDropChance(EquipmentSlotType.CHEST, 0.0F);
        }
    }

    private void setArmorEquipment(ItemStack p_213804_1_) {
        this.setArmor(p_213804_1_);
        if (!this.level.isClientSide) {
            this.getAttribute(Attributes.ARMOR).removeModifier(ARMOR_MODIFIER_UUID);
            if (this.isArmor(p_213804_1_)) {
                int i = ((HorseArmorItem) p_213804_1_.getItem()).getProtection();
                if (i != 0) {
                    this.getAttribute(Attributes.ARMOR).addTransientModifier(new AttributeModifier(ARMOR_MODIFIER_UUID, "Horse armor bonus", i, AttributeModifier.Operation.ADDITION));
                }
            }
        }

    }

    ///////////////////////////////
    public void tame(PlayerEntity p_193101_1_) {
        this.setTame(true);
        this.setOwnerUUID(p_193101_1_.getUUID());
        if (p_193101_1_ instanceof ServerPlayerEntity) {
            CriteriaTriggers.TAME_ANIMAL.trigger((ServerPlayerEntity) p_193101_1_, this);
        }

    }

    @Override
    public boolean isTame() {
        return this.getFlag(4);
    }

    public void setTame(boolean p_70903_1_) {
        this.setFlag(4, p_70903_1_);
        this.reassessTameGoals();
    }

    protected void reassessTameGoals() {
    }

    @Override
    public LivingEntity getOwner() {
        try {
            UUID uuid = this.getOwnerUUID();
            return uuid == null ? null : this.level.getPlayerByUUID(uuid);
        } catch (IllegalArgumentException illegalargumentexception) {
            return null;
        }
    }

    @Override
    public boolean isOrderedToSit() {
        return this.orderedToSit;
    }

    @Override
    public void setOrderedToSit(boolean sit) {
        this.orderedToSit = sit;
    }

    @Override
    public boolean canAttack(LivingEntity entity) {
        return !this.isOwnedBy(entity) && super.canAttack(entity);
    }

    @Override
    public boolean isPowered() {
        return true;
    }

    public void setWarBear(Integer warBear) {
        this.warBear = warBear;
    }

    public void setMountBear(Integer mountBear) {
        this.mountBear = mountBear;
    }

    @Override
    public World getLevel() {
        return this.level;
    }

    //////////////////////////////////////////

    @Override
    public double distanceToSqr(LivingEntity livingentity) {
        return super.distanceToSqr(livingentity);
    }

    @Override
    public float getxRot() {
        return this.xRot;
    }

    @Override
    public float getyRot() {
        return this.yRot;
    }

    @Override
    public void moveTo(double d, double p_226328_2_, double e, float getyRot, float getxRot) {
        super.moveTo(d, p_226328_2_, e, getyRot, getxRot);
    }

    @Override
    public Entity getEntity() {
        return this;
    }

    class AttackPlayerGoal extends NearestAttackableTargetGoal<PlayerEntity> {
        public AttackPlayerGoal() {
            super(SoulBearEntity.this, PlayerEntity.class, 20, true, true, null);
        }

        /**
         * Returns whether execution should begin. You can also read and cache any state
         * necessary for execution in this method as well.
         */
        public boolean canUse() {
            if (SoulBearEntity.this.isBaby()) {
                return false;
            } else {
                if (super.canUse()) {
                    for (SoulBearEntity polarbearentity : SoulBearEntity.this.level.getEntitiesOfClass(
                            SoulBearEntity.class, SoulBearEntity.this.getBoundingBox().inflate(8.0D, 4.0D, 8.0D))) {
                        if (polarbearentity.isBaby()) {
                            return true;
                        }
                    }
                }

                return false;
            }
        }

        @Override
        protected double getFollowDistance() {
            return super.getFollowDistance() * 0.5D;
        }
    }

    class MeleeAttackGoal extends net.minecraft.entity.ai.goal.MeleeAttackGoal {
        public MeleeAttackGoal() {
            super(SoulBearEntity.this, 1.25D, true);
        }

        @Override
        protected void checkAndPerformAttack(LivingEntity enemy, double distToEnemySqr) {
            double d0 = this.getAttackReachSqr(enemy);
            if (distToEnemySqr <= d0 && this.isTimeToAttack()) {
                SoulBearEntity.this.setAttacking(true);
                this.resetAttackCooldown();
                this.mob.doHurtTarget(enemy);
                SoulBearEntity.this.setStanding(false);
            } else if (distToEnemySqr <= d0 * 2.0D) {
                if (this.isTimeToAttack()) {
                    SoulBearEntity.this.setAttacking(false);
                    SoulBearEntity.this.setStanding(false);
                    this.resetAttackCooldown();
                }

                if (this.getTicksUntilNextAttack() <= 10) {
                    SoulBearEntity.this.setAttacking(true);
                    SoulBearEntity.this.setStanding(true);
                    SoulBearEntity.this.playWarningSound();
                }
            } else {
                this.resetAttackCooldown();
                SoulBearEntity.this.setStanding(false);
                SoulBearEntity.this.setAttacking(false);
            }

        }

        /**
         * Reset the task's internal state. Called when this task is interrupted by
         * another one
         */
        @Override
        public void stop() {
            SoulBearEntity.this.setAttacking(false);
            SoulBearEntity.this.setStanding(false);
            super.stop();
        }

        @Override
        protected double getAttackReachSqr(LivingEntity attackTarget) {
            return 4.0F + attackTarget.getBbWidth();
        }
    }

    class PanicGoal extends net.minecraft.entity.ai.goal.PanicGoal {
        public PanicGoal() {
            super(SoulBearEntity.this, 2.0D);
        }

        /**
         * Returns whether execution should begin. You can also read and cache any state
         * necessary for execution in this method as well.
         */
        public boolean shouldExecute() {
            return (SoulBearEntity.this.isBaby() || SoulBearEntity.this.isOnFire()) && super.canUse();
        }
    }

    class AvoidEntityGoal<T extends LivingEntity> extends net.minecraft.entity.ai.goal.AvoidEntityGoal<T> {
        private final SoulBearEntity bear;

        public AvoidEntityGoal(SoulBearEntity wolfIn, Class<T> entityClassToAvoidIn, float avoidDistanceIn,
                               double farSpeedIn, double nearSpeedIn) {
            super(wolfIn, entityClassToAvoidIn, avoidDistanceIn, farSpeedIn, nearSpeedIn);
            this.bear = wolfIn;
        }

        /**
         * Returns whether execution should begin. You can also read and cache any state
         * necessary for execution in this method as well.
         */
        public boolean canUse() {
            if (super.canUse() && this.toAvoid instanceof LlamaEntity) {
                return this.bear.isTame() && this.avoidLlama((LlamaEntity) this.toAvoid);
            }
            if (super.canUse() && this.toAvoid instanceof TurtleEntity) {
                return this.bear.isTame() && this.avoidTurtle((TurtleEntity) this.toAvoid);
            }
            if (super.canUse() && this.toAvoid instanceof VillagerEntity) {
                return this.bear.isTame() && this.avoidVillager((VillagerEntity) this.toAvoid);
            }
            if (super.canUse() && this.toAvoid instanceof IronGolemEntity) {
                return this.bear.isTame() && this.avoidIronGolem((IronGolemEntity) this.toAvoid);
            }
            return false;
        }

        private boolean avoidLlama(LlamaEntity llamaIn) {
            return llamaIn.getStrength() >= SoulBearEntity.this.random.nextInt(5);
        }

        private boolean avoidTurtle(TurtleEntity llamaIn) {
            return true;
        }

        private boolean avoidVillager(VillagerEntity llamaIn) {
            return true;
        }

        private boolean avoidIronGolem(IronGolemEntity llamaIn) {
            return true;
        }

        /**
         * Execute a one shot task or start executing a continuous task
         */
        public void start() {
            SoulBearEntity.this.setTarget(null);
            super.start();
        }

        /**
         * Keep ticking a continuous task that has already been started
         */
        public void tick() {
            SoulBearEntity.this.setTarget(null);
            super.tick();
        }
    }


    public ActionResultType fedFood(PlayerEntity p_241395_1_, ItemStack p_241395_2_) {
        boolean flag = this.handleEating(p_241395_1_, p_241395_2_);
        if (!p_241395_1_.abilities.instabuild) {
            p_241395_2_.shrink(1);
        }

        if (this.level.isClientSide) {
            return ActionResultType.CONSUME;
        } else {
            return flag ? ActionResultType.SUCCESS : ActionResultType.PASS;
        }
    }

    protected boolean handleEating(PlayerEntity p_190678_1_, ItemStack p_190678_2_) {
        return false;
    }
}
