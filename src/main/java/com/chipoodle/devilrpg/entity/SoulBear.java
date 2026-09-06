package com.chipoodle.devilrpg.entity;

import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapability;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.entity.container.MountablePetContainerMenu;
import com.chipoodle.devilrpg.entity.goal.TamablePetFollowOwnerGoal;
import com.chipoodle.devilrpg.entity.goal.TamablePetOwnerHurtByTargetGoal;
import com.chipoodle.devilrpg.entity.goal.TamablePetOwnerHurtTargetGoal;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.scores.Team;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class SoulBear extends AbstractChestedHorse implements ITamableEntity, ISoulEntity, PowerableMob, NeutralMob, IPassiveMinionUpdater<SoulBear> {

    private static final ResourceLocation ARMOR_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, "soul_bear_armor");
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

    // Sincronizado al cliente para que el oso sepa (y el jugador controla) que es montable.
    private static final EntityDataAccessor<Integer> DATA_MOUNT_BEAR = SynchedEntityData.defineId(SoulBear.class, EntityDataSerializers.INT);


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
        this.goalSelector.addGoal(6, new TamablePetFollowOwnerGoal(this, 1.0D, 10.0F, 2.0F, false));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new TamablePetOwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new TamablePetOwnerHurtTargetGoal(this));
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

            if (soulBearInventory == null) {
                soulBearInventory = new CompoundTag();
                addAdditionalSaveData(soulBearInventory);
                minion.setSoulBearInventory(soulBearInventory, (Player) getOwner());
            }

            if (!soulBearInventory.isEmpty()) {
                if (!soulBearInventory.isEmpty()) {
                    readAdditionalSaveData(soulBearInventory);
                } else {
                    addAdditionalSaveData(soulBearInventory);
                    minion.setSoulBearInventory(soulBearInventory, (Player) getOwner());
                }
                this.removeAllEffects();
                this.equipSaddle(ItemStack.EMPTY, SoundSource.NEUTRAL);
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
            ItemStack itemstack = ItemStack.parse(this.level().registryAccess(), compound.getCompound("ArmorItem")).orElse(ItemStack.EMPTY);
            if (!itemstack.isEmpty() && !itemstack.isEmpty()) {
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
            compound.put("ArmorItem", this.inventory.getItem(1).save(this.level().registryAccess(), new CompoundTag()));
        }

        PlayerMinionCapabilityInterface minion = IGenericCapability.getUnwrappedPlayerCapability((Player) getOwner(), PlayerMinionCapability.INSTANCE);
        minion.setSoulBearInventory(compound, (Player) getOwner());
    }

    @Override
    public void aiStep() {
        super.aiStep();

        if (!this.level().isClientSide) {
            this.updatePersistentAnger((ServerLevel) this.level(), true);
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
        if (target instanceof LivingEntity livingEntity) {
            livingEntity.setLastHurtByPlayer((Player) getOwner());
        }

        boolean flag = target.hurt(this.damageSources().mobAttack(this), (float) (attackDamage));
        if (flag) {
            int probability = random.nextInt(100);
            if (warBear > 0 && probability <= (warBear * PROBABILITY_MULTIPLIER)) {
                IRenderUtilities.warbearExplosion(this.level(), random, this);
                double radius = 3;
                List<LivingEntity> acquireAllLookTargetsByClass = TargetUtils
                        .acquireAllTargetsInRadiusByClass(this, LivingEntity.class, radius).stream()
                        .filter(entity -> !isAllyForWarBearAoe(entity)).collect(Collectors.toList());

                acquireAllLookTargetsByClass.forEach(
                        mob -> mob.hurt(this.damageSources().mobAttack(this), (float) (attackDamage * SPLASH_DAMAGE_FACTOR)));
                DevilRpg.LOGGER.info("---------->doHurtTarget warBear: {} probability: {} Range of success: {}, enemies: {}, main damage: {} splash damage: {}, armor: {} owner: {} owneruuid: {}", warBear,
                        probability, warBear * PROBABILITY_MULTIPLIER, acquireAllLookTargetsByClass.size(), attackDamage, attackDamage * SPLASH_DAMAGE_FACTOR, Objects.requireNonNull(this.getAttribute(Attributes.ARMOR)).getValue(), getOwner(), getOwnerUUID());
            }
            // doEnchantDamageEffects was removed in 1.21
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
            PlayerMinionCapabilityInterface minionCap = getOwner().getData(PlayerMinionCapability.INSTANCE);
            if (minionCap == null)
                return;
            minionCap.removeSoulBear((Player) getOwner(), this);
        }
        // super.onDeath(cause);
        customOnDeath();
    }

    private void customOnDeath() {
        if (getOwner() != null) {
            CompoundTag c = new CompoundTag();
            addAdditionalSaveData(c);
        }

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
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_MOUNT_BEAR, 0);
    }

    @Override
    public void containerChanged(@NotNull Container container) {
        ItemStack itemstack = this.getBodyArmorAccess().getItem(0);
        super.containerChanged(container);
        ItemStack itemstack1 = this.getBodyArmorAccess().getItem(0);
        if (this.tickCount > 20 && !itemstack1.isEmpty() && itemstack != itemstack1) {
            this.playSound(SoundEvents.HORSE_ARMOR, 0.5F, 1.0F);
        }

    }

    public boolean canWearArmor() {
        return true;
    }



    /**
     * Called to update the entity's position/logic.
     */
    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
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

        if (!this.level().isClientSide) {
            this.updatePersistentAnger((ServerLevel) this.level(), true);
        }

    }

    @Override
    public @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand interactionHand) {
        DevilRpg.LOGGER.info("----------------------->mobInteract mountBear:{}. this.isVehicle(): {}", getMountBearLevel(), this.isVehicle());
        if (getMountBearLevel() <= 0) {
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
                    return InteractionResult.sidedSuccess(this.level().isClientSide);
                }
            }

        }
        return super.mobInteract(player, interactionHand);
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
    public SpawnGroupData finalizeSpawn(@NotNull ServerLevelAccessor p_29533_, @NotNull DifficultyInstance p_29534_, @NotNull MobSpawnType p_29535_, @Nullable SpawnGroupData p_29536_) {
        if (p_29536_ == null) {
            p_29536_ = new AgeableMob.AgeableMobGroupData(1.0F);
        }

        return super.finalizeSpawn(p_29533_, p_29534_, p_29535_, p_29536_);
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

    protected void updateContainerEquipment() {
        if (!this.level().isClientSide) {
    
            this.setArmorEquipment(this.inventory.getItem(1));
            this.setDropChance(EquipmentSlot.CHEST, 0.0F);
        }
    }

    private void setArmorEquipment(ItemStack p_213804_1_) {
        this.getBodyArmorAccess().setItem(0, p_213804_1_);
        if (!this.level().isClientSide) {
            Objects.requireNonNull(this.getAttribute(Attributes.ARMOR)).removeModifier(ARMOR_MODIFIER_ID);
            if (!p_213804_1_.isEmpty()) {
                int i = 0; // Horse armor protection is handled through the equippable component in 1.21
                if (i != 0) {
                    Objects.requireNonNull(this.getAttribute(Attributes.ARMOR)).addTransientModifier(new AttributeModifier(ARMOR_MODIFIER_ID, i, AttributeModifier.Operation.ADD_VALUE));
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
        this.entityData.set(DATA_MOUNT_BEAR, mountBear == null ? 0 : mountBear);
    }

    @Override
    public LivingEntity getControllingPassenger() {
        // Oso magico: al montarlo (mountBear>0) es controlable sin necesidad de silla.
        if (this.getMountBearLevel() > 0) {
            Entity entity = this.getFirstPassenger();
            if (entity instanceof Player)
                return (Player) entity;
        }
        return super.getControllingPassenger();
    }

    @Override
    public boolean isSaddled() {
        // Tratar el oso magico como "ensillado" cuando es montable, para que AbstractHorse
        // (travel) lea la entrada del jugador y se pueda controlar sin una silla de caballo.
        return this.getMountBearLevel() > 0 || super.isSaddled();
    }

    // El oso muestra su inventario de almacenamiento (como un burro) SOLO cuando tiene puntos en
    // Riding Bear; sin puntos no debe verse con cofres ni poder montarse.
    @Override
    public boolean hasChest() {
        return this.getMountBearLevel() > 0;
    }

    private int getMountBearLevel() {
        Level lvl = this.level();
        // En el cliente el campo mountBear llega a 0 (no se setea), asi que leemos la data sync.
        return (lvl != null && lvl.isClientSide) ? this.entityData.get(DATA_MOUNT_BEAR) : this.mountBear;
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
    public void tame(Player player) {
        this.setTamed(true);
        this.setOwnerUUID(player.getUUID());
        if (player instanceof ServerPlayer) {
            CriteriaTriggers.TAME_ANIMAL.trigger((ServerPlayer) player, this);
        }
        this.level().broadcastEntityEvent(this, (byte) 7);
    }

    @Override
    public @NotNull InteractionResult fedFood(@NotNull Player player, @NotNull ItemStack itemStack) {
        boolean flag = this.handleEating(player, itemStack);
        if (!player.getAbilities().instabuild) {
            itemStack.shrink(1);
        }

        if (this.level().isClientSide) {
            return InteractionResult.CONSUME;
        } else {
            return flag ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
    }

    @Override
    protected boolean handleEating(@NotNull Player p_30593_, @NotNull ItemStack p_30594_) {
        return false;
    }

    public boolean canBeLeashed(@NotNull Player player) {
        return false;
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

    /**
     * True si la entidad NO debe recibir dano del AOE del War Bear: el propio dueno, un tamable del
     * mismo dueno, o cualquiera con quien este oso es aliado.
     */
    private boolean isAllyForWarBearAoe(Entity entity) {
        LivingEntity owner = this.getOwner();
        if (owner != null && (entity == owner || Objects.equals(entity, owner))) {
            return true;
        }
        if (entity instanceof ITamableEntity tamable && Objects.equals(tamable.getOwner(), owner)) {
            return true;
        }
        return this.isAlliedTo(entity);
    }

    class MeleeAttackGoal extends net.minecraft.world.entity.ai.goal.MeleeAttackGoal {
        public MeleeAttackGoal() {
            super(SoulBear.this, 1.25D, true);
        }

        @Override
        protected void checkAndPerformAttack(@NotNull LivingEntity enemy) {
            double d0 = this.getAttackReachSqr(enemy);
            // Mientras el oso ya se esta parando/atacando, el golpe alcanza mas lejos para acertar
            // a enemigos en movimiento durante el wind-up (antes se escapaban del rango).
            double strikeReach = (SoulBear.this.isAttacking() || SoulBear.this.isStanding()) ? d0 * 1.6D : d0;
            if (this.mob.distanceToSqr(enemy) <= strikeReach && this.isTimeToAttack()) {
                SoulBear.this.setAttacking(true);
                this.resetAttackCooldown();
                this.mob.doHurtTarget(enemy);
                SoulBear.this.setStanding(false);
            } else if (this.mob.distanceToSqr(enemy) <= d0 * 2.0D) {
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

            protected double getAttackReachSqr(LivingEntity attackTarget) {
            return 9.0F + attackTarget.getBbWidth();
        }
    }

}