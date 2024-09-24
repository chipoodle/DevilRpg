package com.chipoodle.devilrpg.entity;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapability;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.entity.container.MountablePetContainerMenu;
import com.chipoodle.devilrpg.entity.goal.TameablePetFollowOwnerGoal;
import com.chipoodle.devilrpg.entity.goal.TameablePetOwnerHurtByTargetGoal;
import com.chipoodle.devilrpg.entity.goal.TameablePetOwnerHurtTargetGoal;
import com.chipoodle.devilrpg.init.ModEntities;
import com.chipoodle.devilrpg.util.IRenderUtilities;
import com.chipoodle.devilrpg.util.SkillEnum;
import com.chipoodle.devilrpg.util.TargetUtils;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundHorseScreenOpenPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.Container;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.HorseArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.scores.Team;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class SoulBear extends AbstractChestedHorse implements ITamableEntity, ISoulEntity, PowerableMob, NeutralMob, IPassiveMinionUpdater<SoulBear> {

    private static final UUID ARMOR_MODIFIER_UUID = UUID.fromString("556E1665-8B10-40C8-8F9D-CF9B1667F295");
   private static final UniformInt PERSISTENT_ANGER_TIME = TimeUtil.rangeOfSeconds(20, 39);
    private static final int PROBABILITY_MULTIPLIER = 5;
    private static final int DURATION_TICKS = 100;

    private static final double RADIUS_PARTICLES = 1.0;
    private static final int NUMBER_OF_PARTICLES_WAR_BEAR = 3;
    private static final double SPLASH_DAMAGE_FACTOR = 0.35F;
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


    public SoulBear(EntityType<? extends SoulBear> type, Level worldIn) {
        super(type, worldIn);
    }

    public static AttributeSupplier.Builder setAttributes() {
        return TamableAnimal.createMobAttributes()
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.MAX_HEALTH, 8.0D)
                .add(Attributes.FOLLOW_RANGE, 16.0D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.ARMOR, 0.35D)
                .add(Attributes.JUMP_STRENGTH, 0.7D);
    }

    @Override
    public AgeableMob getBreedOffspring(@NotNull ServerLevel level, @NotNull AgeableMob ageable) {
        return ModEntities.SOUL_BEAR.get().create(level);
    }

    /**
     * Checks if the parameter is an item which this animal can be fed to breed it
     * (wheat, carrots or seeds depending on the animal type)
     */
    @Override
    public boolean isFood(@NotNull ItemStack stack) {
        return false;
    }

    @Override
    protected void registerGoals() {
        // super.registerGoals();
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.25D));
        this.goalSelector.addGoal(5, new SoulBear.MeleeAttackGoal());
        this.goalSelector.addGoal(5, new RandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new TameablePetFollowOwnerGoal(this, 1.0D, 10.0F, 2.0F, false));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new TameablePetOwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new TameablePetOwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(3, (new HurtByTargetGoal(this)).setAlertOthers());
        this.targetSelector.addGoal(5,
                new NearestAttackableTargetGoal<>(this, Mob.class, 10, false, false, (entity) ->
                        !(entity instanceof Villager)
                                && !(entity instanceof Llama)
                                && !(entity instanceof Turtle)
                                && !(entity instanceof IronGolem)
                                && !(entity instanceof ITamableEntity && Objects.equals(((ITamableEntity) entity).getOwnerUUID(), this.getOwnerUUID()))
                ));
        this.targetSelector.addGoal(8, new ResetUniversalAngerTargetGoal<>(this, true));

    }

    public void updateLevel(Player owner) {
        tame(owner);
        DevilRpg.LOGGER.debug("-------> updateLevel getOwnerUUID() {} ", this.getOwnerUUID());
        DevilRpg.LOGGER.debug("-------> updateLevel getUUID() {} ", this.getUUID());
        PlayerSkillCapabilityInterface skill = IGenericCapability.getUnwrappedPlayerCapability(owner, PlayerSkillCapability.INSTANCE);
        if (skill != null) {
            this.puntosAsignados = skill.getSkillsPoints().get(SkillEnum.SUMMON_SOUL_BEAR);
            saludMaxima = 5 * this.puntosAsignados + SALUD_INICIAL;
            initialArmor = (1.0D * 0.410 * puntosAsignados) + 3;

            PlayerMinionCapabilityInterface minion = IGenericCapability.getUnwrappedPlayerCapability((Player) getOwner(), PlayerMinionCapability.INSTANCE);
            CompoundTag soulBearInventory = minion.getSoulBearInventory();

            if (soulBearInventory != null && !soulBearInventory.isEmpty()) {
                if (!soulBearInventory.isEmpty()) {
                    readAdditionalSaveData(soulBearInventory);
                } else {
                    addAdditionalSaveData(soulBearInventory);
                    minion.setSoulBearInventory(soulBearInventory, (Player) getOwner());
                }
                this.removeAllEffects();
                this.equipSaddle(null);
            }
        }
        //tame(owner);

        Objects.requireNonNull(this.getAttribute(Attributes.MOVEMENT_SPEED)).setBaseValue(0.4F);
        Objects.requireNonNull(this.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(saludMaxima);
        Objects.requireNonNull(this.getAttribute(Attributes.ARMOR)).setBaseValue(initialArmor + warBear);
        Objects.requireNonNull(this.getAttribute(Attributes.FOLLOW_RANGE)).setBaseValue(16.0D);
        Objects.requireNonNull(this.getAttribute(Attributes.ATTACK_DAMAGE)).setBaseValue((1.4 * puntosAsignados) + 4); // 5.4-32
        Objects.requireNonNull(this.getAttribute(Attributes.JUMP_STRENGTH)).setBaseValue(0.7D + ((double) mountBear / 5));
        setHealth((float) saludMaxima);
        DevilRpg.LOGGER.debug("----------------------->SoulBear.updateLevel(). Owner {}. isTame {}.  ", owner.getUUID(), this.isTame());
    }

    @Override // create
    public void readAdditionalSaveData(@NotNull CompoundTag compound) {
        super.readAdditionalSaveData(compound);

        this.orderedToSit = compound.getBoolean("Sitting");
        //this.setInSittingPose(this.orderedToSit);

        //this.setTypeVariant(compound.getInt("Variant"));
        if (compound.contains("ArmorItem", 10)) {
            ItemStack itemstack = ItemStack.of(compound.getCompound("ArmorItem"));
            if (!itemstack.isEmpty() && this.isArmor(itemstack)) {
                this.inventory.setItem(1, itemstack);
            }
        }

        this.updateContainerEquipment();
    }

    @Override // pause menu, dead
    public void addAdditionalSaveData(@NotNull CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putString("OwnerUUID", "");
        compound.putString("Owner", "");

        compound.putBoolean("Sitting", this.orderedToSit);

        //compound.putInt("Variant", this.getTypeVariant());
        if (!this.inventory.getItem(1).isEmpty()) {
            compound.put("ArmorItem", this.inventory.getItem(1).save(new CompoundTag()));
        }

        PlayerMinionCapabilityInterface minion = IGenericCapability.getUnwrappedPlayerCapability((Player) getOwner(), PlayerMinionCapability.INSTANCE);
        minion.setSoulBearInventory(compound, (Player) getOwner());
    }

    @Override
    public void aiStep() {
        super.aiStep();

        if (!this.level.isClientSide) {
            this.updatePersistentAnger((ServerLevel)this.level, true);
        }

        addToAiStep(this);
    }

    @Override
    public boolean wantsToAttack(@NotNull LivingEntity target, @NotNull LivingEntity owner) {
        return ITamableEntity.super.wantsToAttack(target, owner);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        double attackDamage = this.getAttributeValue(Attributes.ATTACK_DAMAGE);

        //Needed to generate xp orbs after killing target since this entity doesn't inherit from TamableAnimal
        if(target instanceof LivingEntity livingEntity){
            livingEntity.setLastHurtByPlayer((Player) getOwner());
        }

        boolean flag = target.hurt(this.damageSources().mobAttack(this), (float) (attackDamage));
        if (flag) {
            int probability = random.nextInt(100);
            if (warBear > 0 && probability <= (warBear * PROBABILITY_MULTIPLIER)) {
                IRenderUtilities.rotationParticles(Minecraft.getInstance().level, random, this,
                        ParticleTypes.EXPLOSION, NUMBER_OF_PARTICLES_WAR_BEAR, RADIUS_PARTICLES);
                double radius = 3;
                List<LivingEntity> acquireAllLookTargetsByClass = TargetUtils
                        .acquireAllTargetsInRadiusByClass(this, LivingEntity.class, radius).stream()
                        .filter(entity -> !isAlliedTo(entity)).collect(Collectors.toList());

                acquireAllLookTargetsByClass.forEach(
                        mob -> mob.hurt(this.damageSources().mobAttack(this), (float) (attackDamage * SPLASH_DAMAGE_FACTOR)));
                DevilRpg.LOGGER.info("---------->doHurtTarget warBear: {} probability: {} Range of success: {}, enemies: {}, main damage: {} splash damage: {}, armor: {} owner: {} owneruuid: {}", warBear,
                        probability, warBear * PROBABILITY_MULTIPLIER, acquireAllLookTargetsByClass.size(), attackDamage, attackDamage * SPLASH_DAMAGE_FACTOR, Objects.requireNonNull(this.getAttribute(Attributes.ARMOR)).getValue(),getOwner(),getOwnerUUID());
            }
            this.doEnchantDamageEffects(this, target);
        }
        return flag;
    }

    @Override
    public boolean hurt(@NotNull DamageSource damageSource, float amount) {
        boolean hurt = super.hurt(damageSource, amount);
        if (hurt) {

        }
        return hurt;
    }

    @Override
    public boolean isImmobile() {
        if (!isAttacking)
            return super.isImmobile();
        return this.isDeadOrDying();
    }

    @Override
    public void startPersistentAngerTimer() {
        this.setRemainingPersistentAngerTime(PERSISTENT_ANGER_TIME.sample(this.random));
    }

    @Override
    public int getRemainingPersistentAngerTime() {
        return this.remainingPersistentAngerTime;
    }

    @Override
    public void setRemainingPersistentAngerTime(int p_230260_1_) {
        this.remainingPersistentAngerTime = p_230260_1_;
    }

    public UUID getPersistentAngerTarget() {
        return this.persistentAngerTarget;
    }

    @Override
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
    public void remove(@NotNull RemovalReason removalReason) {
        super.remove(removalReason);
    }

    /**
     * Called when the mob's health reaches 0.
     */
    @Override
    public void die(@NotNull DamageSource cause) {
        if (getOwner() != null) {
            LazyOptional<PlayerMinionCapabilityInterface> minionCap = getOwner()
                    .getCapability(PlayerMinionCapability.INSTANCE);
            if (!minionCap.isPresent())
                return;
            minionCap.ifPresent(x -> x.removeSoulBear((Player) getOwner(), this));
        }
        // super.onDeath(cause);
        customOnDeath();
    }

    private void customOnDeath() {
        if (getOwner() != null) {
            CompoundTag c = new CompoundTag();
            addAdditionalSaveData(c);
        }

        level.broadcastEntityEvent(this, (byte) 3);
        this.dead = true;
        this.remove(RemovalReason.DISCARDED);
        IRenderUtilities.customDeadParticles(this.level, this.random, this);
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
    public @NotNull Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }


    @Override
    protected SoundEvent getAmbientSound() {
        return this.isBaby() ? SoundEvents.POLAR_BEAR_AMBIENT_BABY : SoundEvents.POLAR_BEAR_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(@NotNull DamageSource damageSourceIn) {
        return SoundEvents.POLAR_BEAR_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.POLAR_BEAR_DEATH;
    }

    @Override
    protected void playStepSound(@NotNull BlockPos pos, @NotNull BlockState blockIn) {
        this.playSound(SoundEvents.POLAR_BEAR_STEP, 0.15F, 1.0F);
    }

    protected void playWarningSound() {
        if (this.warningSoundTicks <= 0) {
            this.playSound(SoundEvents.POLAR_BEAR_WARNING, 1.0F, this.getVoicePitch());
            this.warningSoundTicks = 40;
        }

    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        //this.entityData.define(DATA_STANDING_ID, false);
    }

    @Override
    public void containerChanged(@NotNull Container container) {
        ItemStack itemstack = this.getArmor();
        super.containerChanged(container);
        ItemStack itemstack1 = this.getArmor();
        if (this.tickCount > 20 && this.isArmor(itemstack1) && itemstack != itemstack1) {
            this.playSound(SoundEvents.HORSE_ARMOR, 0.5F, 1.0F);
        }

    }

    @Override
    public boolean canWearArmor() {
        return true;
    }

    @Override
    public boolean isArmor(ItemStack p_190682_1_) {
        return p_190682_1_.getItem() instanceof HorseArmorItem;
    }


    /**
     * Called to update the entity's position/logic.
     */
    @Override
    public void tick() {
        super.tick();
        if (this.level.isClientSide) {
            if (this.clientSideStandAnimation != this.clientSideStandAnimationO) {
                this.refreshDimensions();
            }

            this.clientSideStandAnimationO = this.clientSideStandAnimation;
            if (this.isStanding()) {
                this.clientSideStandAnimation = Mth.clamp(this.clientSideStandAnimation + 1.0F, 0.0F, 6.0F);
            } else {
                this.clientSideStandAnimation = Mth.clamp(this.clientSideStandAnimation - 1.0F, 0.0F, 6.0F);
            }
        }

        if (this.warningSoundTicks > 0) {
            --this.warningSoundTicks;
        }

        if (!this.level.isClientSide) {
            this.updatePersistentAnger((ServerLevel) this.level, true);
        }

    }

    @Override
    public @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand interactionHand) {
        DevilRpg.LOGGER.info("----------------------->mobInteract mountBear:{}. this.isVehicle(): {}", mountBear, this.isVehicle());
        if (mountBear <= 0) {
            return InteractionResult.PASS;
        }

        boolean flag = !this.isBaby() && this.isTame() && player.isSecondaryUseActive();
        if (!this.isVehicle() && !flag) {
            ItemStack itemstack = player.getItemInHand(interactionHand);
            if (!itemstack.isEmpty()) {
                if (this.isFood(itemstack)) {
                    return this.fedFood(player, itemstack);
                }

                if (!this.isTame()) {
                    this.makeMad();
                    return InteractionResult.sidedSuccess(this.level.isClientSide);
                }
            }

        }
        return super.mobInteract(player, interactionHand);
    }


    @Override
    public @NotNull EntityDimensions getDimensions(@NotNull Pose poseIn) {
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
        return Mth.lerp(scale, this.clientSideStandAnimationO, this.clientSideStandAnimation) / 6.0F;
    }

    @Override
    protected float getWaterSlowDown() {
        return 0.98F;
    }

    @Override
    public SpawnGroupData finalizeSpawn(@NotNull ServerLevelAccessor p_29533_, @NotNull DifficultyInstance p_29534_, @NotNull MobSpawnType p_29535_, @Nullable SpawnGroupData p_29536_, @Nullable CompoundTag p_29537_) {
        if (p_29536_ == null) {
            p_29536_ = new AgeableMob.AgeableMobGroupData(1.0F);
        }

        return super.finalizeSpawn(p_29533_, p_29534_, p_29535_, p_29536_, p_29537_);
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
    public int getExperienceReward() {
        /*
         * if (player.equals(getOwner())) return 0; return 1 +
         * this.world.rand.nextInt(3);
         */
        return 0;
    }

    public ItemStack getArmor() {
        return this.getItemBySlot(EquipmentSlot.CHEST);
    }

    private void setArmor(ItemStack p_213805_1_) {
        this.setItemSlot(EquipmentSlot.CHEST, p_213805_1_);
        this.setDropChance(EquipmentSlot.CHEST, 0.0F);
    }

    @Override
    protected void updateContainerEquipment() {
        if (!this.level.isClientSide) {
            super.updateContainerEquipment();
            this.setArmorEquipment(this.inventory.getItem(1));
            this.setDropChance(EquipmentSlot.CHEST, 0.0F);
        }
    }

    private void setArmorEquipment(ItemStack p_213804_1_) {
        this.setArmor(p_213804_1_);
        if (!this.level.isClientSide) {
            Objects.requireNonNull(this.getAttribute(Attributes.ARMOR)).removeModifier(ARMOR_MODIFIER_UUID);
            if (this.isArmor(p_213804_1_)) {
                int i = ((HorseArmorItem) p_213804_1_.getItem()).getProtection();
                if (i != 0) {
                    Objects.requireNonNull(this.getAttribute(Attributes.ARMOR)).addTransientModifier(new AttributeModifier(ARMOR_MODIFIER_UUID, "Soul bear armor bonus", i, AttributeModifier.Operation.ADDITION));
                }
            }
        }

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
    public boolean isOrderedToSit() {
        return this.orderedToSit;
    }

    @Override
    public void setOrderedToSit(boolean sit) {
        this.orderedToSit = sit;
    }

    @Override
    public boolean canAttack(@NotNull LivingEntity livingEntity) {
        return !this.isOwnedBy(livingEntity) && super.canAttack(livingEntity);
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
    public @NotNull Level getLevel() {
        return this.level;
    }

    //////////////////////////////////////////

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

    @Override
    public boolean isTame() {
        return super.isTamed();
        //return this.getFlag(2);
    }

    @Override
    public void setTame(boolean isTamed) {
        super.setTamed(isTamed);
        //this.setFlag(2, isTamed);
    }

    @Override
    public void tame(Player player) {
        this.setTame(true);
        this.setOwnerUUID(player.getUUID());
        if (player instanceof ServerPlayer) {
            CriteriaTriggers.TAME_ANIMAL.trigger((ServerPlayer) player, this);
        }
        this.level.broadcastEntityEvent(this, (byte) 7);
    }

    public @NotNull InteractionResult fedFood(@NotNull Player player, @NotNull ItemStack itemStack) {
        boolean flag = this.handleEating(player, itemStack);
        if (!player.getAbilities().instabuild) {
            itemStack.shrink(1);
        }

        if (this.level.isClientSide) {
            return InteractionResult.CONSUME;
        } else {
            return flag ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
    }

    protected boolean handleEating(@NotNull Player p_30593_, @NotNull ItemStack p_30594_) {
        return false;
    }

    public boolean canBeLeashed(@NotNull Player player) {
        return false;
    }

    @Override
    public void openCustomInventoryScreen(@NotNull Player player) {
        if (!this.level.isClientSide && (!this.isVehicle() || this.hasPassenger(player)) && this.isTame()) {
            //player.openHorseInventory(this, this.inventory);
            openBearInventory(this, this.inventory, (ServerPlayer) player);
        }

    }

    public void openBearInventory(AbstractHorse abstractHorse, Container p_9060_, ServerPlayer player) {
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
        }

        player.nextContainerCounter();
        player.connection.send(new ClientboundHorseScreenOpenPacket(player.containerCounter, p_9060_.getContainerSize(), abstractHorse.getId()));

        //player.containerMenu = new HorseInventoryMenu(player.containerCounter, player.getInventory(), p_9060_, abstractHorse);
        player.containerMenu = new MountablePetContainerMenu(player.containerCounter, player.getInventory(), p_9060_, abstractHorse);


        player.initMenu(player.containerMenu);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.player.PlayerContainerEvent.Open(player, player.containerMenu));
    }

    @Override
    public Team getTeam() {
        if (this.isTame()) {
            LivingEntity livingentity = this.getOwner();
            if (livingentity != null) {
                return livingentity.getTeam();
            }
        }
        return super.getTeam();
    }

    @Override
    public boolean isAlliedTo(@NotNull Entity entity) {
        if (this.isTame()) {
            LivingEntity livingentity = this.getOwner();
            if (entity == livingentity) {
                return true;
            }

            if (livingentity != null) {
                return livingentity.isAlliedTo(entity);
            }
        }

        boolean isOnSameTeam = super.isAlliedTo(entity);
        return isOnSameTeam || isEntitySameOwnerAsThis(entity, this);
    }
    class MeleeAttackGoal extends net.minecraft.world.entity.ai.goal.MeleeAttackGoal {
        public MeleeAttackGoal() {
            super(SoulBear.this, 1.25D, true);
        }

        @Override
        protected void checkAndPerformAttack(@NotNull LivingEntity enemy, double distToEnemySqr) {
            double d0 = this.getAttackReachSqr(enemy);
            if (distToEnemySqr <= d0 && this.isTimeToAttack()) {
                SoulBear.this.setAttacking(true);
                this.resetAttackCooldown();
                this.mob.doHurtTarget(enemy);
                SoulBear.this.setStanding(false);
            } else if (distToEnemySqr <= d0 * 2.0D) {
                if (this.isTimeToAttack()) {
                    SoulBear.this.setAttacking(false);
                    SoulBear.this.setStanding(false);
                    this.resetAttackCooldown();
                }

                if (this.getTicksUntilNextAttack() <= 10) {
                    SoulBear.this.setAttacking(true);
                    SoulBear.this.setStanding(true);
                    SoulBear.this.playWarningSound();
                }
            } else {
                this.resetAttackCooldown();
                SoulBear.this.setStanding(false);
                SoulBear.this.setAttacking(false);
            }

        }

        /**
         * Reset the task's internal state. Called when this task is interrupted by
         * another one
         */
        @Override
        public void stop() {
            SoulBear.this.setAttacking(false);
            SoulBear.this.setStanding(false);
            super.stop();
        }

        @Override
        protected double getAttackReachSqr(LivingEntity attackTarget) {
            return 4.0F + attackTarget.getBbWidth();
        }
    }

}
