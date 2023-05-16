package com.chipoodle.devilrpg.entity;

import com.chipoodle.devilrpg.entity.container.TameableMountableHorseContainerMenu;
import com.chipoodle.devilrpg.entity.goal.TameableMountableRunAroundLikeCrazyGoal;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundHorseScreenOpenPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.OldUsersConverter;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Team;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

public abstract class AbstractMountableTamableEntity extends Animal implements ContainerListener, HasCustomInventoryScreen, PlayerRideableJumping, Saddleable {
    private static final Ingredient FOOD_ITEMS = Ingredient.of(Items.WHEAT, Items.SUGAR, Blocks.HAY_BLOCK.asItem(), Items.APPLE, Items.GOLDEN_CARROT, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE);
    private static final EntityDataAccessor<Byte> DATA_ID_FLAGS = SynchedEntityData.defineId(AbstractMountableTamableEntity.class, EntityDataSerializers.BYTE);
    private static final Predicate<LivingEntity> PARENT_HORSE_SELECTOR = (p_213617_0_) -> {
        return p_213617_0_ instanceof AbstractMountableTamableEntity && ((AbstractMountableTamableEntity) p_213617_0_).isBred();
    };
    private static final TargetingConditions MOMMY_TARGETING = TargetingConditions.forNonCombat().range(16.0D).selector(PARENT_HORSE_SELECTOR);
    private static final EntityDataAccessor<Optional<UUID>> DATA_ID_OWNER_UUID = SynchedEntityData.defineId(AbstractMountableTamableEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    public int tailCounter;
    public int sprintCounter;
    protected boolean isJumping;
    protected SimpleContainer inventory;
    protected int temper;
    protected float playerJumpPendingScale;
    protected boolean canGallop = true;
    protected int gallopSoundCounter;
    private int eatingCounter;
    private int mouthCounter;
    private int standCounter;
    private boolean allowStandSliding;
    private float eatAnim;
    private float eatAnimO;
    private float standAnim;
    private float standAnimO;
    private float mouthAnim;
    private float mouthAnimO;
    private net.minecraftforge.common.util.LazyOptional<?> itemHandler = null;

    protected AbstractMountableTamableEntity(EntityType<? extends AbstractMountableTamableEntity> p_i48563_1_, Level p_i48563_2_) {
        super(p_i48563_1_, p_i48563_2_);
        this.maxUpStep = 1.0F;
        this.createInventory();
    }

    public static AttributeSupplier.Builder createBaseHorseAttributes() {
        return Mob.createMobAttributes().add(Attributes.JUMP_STRENGTH).add(Attributes.MAX_HEALTH, 53.0D).add(Attributes.MOVEMENT_SPEED, 0.225F);
    }

    protected void registerGoals() {
        this.goalSelector.addGoal(1, new PanicGoal(this, 1.2D));
        this.goalSelector.addGoal(1, new TameableMountableRunAroundLikeCrazyGoal(this, 1.2D));
        //this.goalSelector.addGoal(2, new BreedGoal(this, 1.0D, AbstractMountableTameableEntity.class));
        this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.addBehaviourGoals();
    }

    protected void addBehaviourGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
    }

    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_ID_FLAGS, (byte) 0);
        this.entityData.define(DATA_ID_OWNER_UUID, Optional.empty());
    }

    protected boolean getFlag(int p_110233_1_) {
        return (this.entityData.get(DATA_ID_FLAGS) & p_110233_1_) != 0;
    }

    protected void setFlag(int p_110208_1_, boolean p_110208_2_) {
        byte b0 = this.entityData.get(DATA_ID_FLAGS);
        if (p_110208_2_) {
            this.entityData.set(DATA_ID_FLAGS, (byte) (b0 | p_110208_1_));
        } else {
            this.entityData.set(DATA_ID_FLAGS, (byte) (b0 & ~p_110208_1_));
        }

    }

    @Nullable
    public UUID getOwnerUUID() {
        return this.entityData.get(DATA_ID_OWNER_UUID).orElse(null);
    }

    public void setOwnerUUID(@Nullable UUID p_184779_1_) {
        this.entityData.set(DATA_ID_OWNER_UUID, Optional.ofNullable(p_184779_1_));
    }

    public boolean isJumping() {
        return this.isJumping;
    }

    public void setIsJumping(boolean p_110255_1_) {
        this.isJumping = p_110255_1_;
    }

    protected void onLeashDistance(float p_142017_1_) {
        if (p_142017_1_ > 6.0F && this.isEating()) {
            this.setEating(false);
        }

    }

    public boolean isEating() {
        return this.getFlag(16);
    }

    public void setEating(boolean p_110227_1_) {
        this.setFlag(16, p_110227_1_);
    }

    public boolean isStanding() {
        return this.getFlag(32);
    }

    public void setStanding(boolean p_110219_1_) {
        if (p_110219_1_) {
            this.setEating(false);
        }

        this.setFlag(32, p_110219_1_);
    }

    public boolean isBred() {
        return this.getFlag(8);
    }

    public void setBred(boolean p_110242_1_) {
        this.setFlag(8, p_110242_1_);
    }

    public boolean isSaddleable() {
        return this.isAlive() && !this.isBaby() && this.isTame();
    }

    public void equipSaddle(@Nullable SoundSource p_230266_1_) {
        this.inventory.setItem(0, new ItemStack(Items.SADDLE));
        if (p_230266_1_ != null) {
            this.level.playSound(null, this, this.getSaddleSoundEvent(), p_230266_1_, 0.5F, 1.0F);
        }

    }

    public boolean isSaddled() {
        return this.getFlag(4);
    }

    public int getTemper() {
        return this.temper;
    }

    public void setTemper(int p_110238_1_) {
        this.temper = p_110238_1_;
    }

    public int modifyTemper(int p_30654_) {
        int i = Mth.clamp(this.getTemper() + p_30654_, 0, this.getMaxTemper());
        this.setTemper(i);
        return i;
    }

    public boolean isPushable() {
        return !this.isVehicle();
    }

    private void eating() {
        this.openMouth();
        if (!this.isSilent()) {
            SoundEvent soundevent = this.getEatingSound();
            if (soundevent != null) {
                this.level.playSound(null, this.getX(), this.getY(), this.getZ(), soundevent, this.getSoundSource(), 1.0F, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.2F);
            }
        }

    }

    public boolean causeFallDamage(float p_225503_1_, float p_225503_2_) {
        if (p_225503_1_ > 1.0F) {
            this.playSound(SoundEvents.HORSE_LAND, 0.4F, 1.0F);
        }

        int i = this.calculateFallDamage(p_225503_1_, p_225503_2_);
        if (i <= 0) {
            return false;
        } else {
            this.hurt(DamageSource.FALL, (float) i);
            if (this.isVehicle()) {
                for (Entity entity : this.getIndirectPassengers()) {
                    entity.hurt(DamageSource.FALL, (float) i);
                }
            }

            this.playBlockFallSound();
            return true;
        }
    }

    protected int calculateFallDamage(float p_225508_1_, float p_225508_2_) {
        return Mth.ceil((p_225508_1_ * 0.5F - 3.0F) * p_225508_2_);
    }

    protected int getInventorySize() {
        return 2;
    }

    protected void createInventory() {
        SimpleContainer simpleContainer = this.inventory;
        this.inventory = new SimpleContainer(this.getInventorySize());
        if (simpleContainer != null) {
            simpleContainer.removeListener(this);
            int i = Math.min(simpleContainer.getContainerSize(), this.inventory.getContainerSize());

            for (int j = 0; j < i; ++j) {
                ItemStack itemstack = simpleContainer.getItem(j);
                if (!itemstack.isEmpty()) {
                    this.inventory.setItem(j, itemstack.copy());
                }
            }
        }

        this.inventory.addListener(this);
        this.updateContainerEquipment();
        this.itemHandler = net.minecraftforge.common.util.LazyOptional.of(() -> new net.minecraftforge.items.wrapper.InvWrapper(this.inventory));
    }

    protected void updateContainerEquipment() {
        if (!this.level.isClientSide) {
            this.setFlag(4, !this.inventory.getItem(0).isEmpty());
        }
    }

    public void containerChanged(Container p_76316_1_) {
        boolean flag = this.isSaddled();
        this.updateContainerEquipment();
        if (this.tickCount > 20 && !flag && this.isSaddled()) {
            this.playSound(SoundEvents.HORSE_SADDLE, 0.5F, 1.0F);
        }

    }

    public double getCustomJump() {
        return this.getAttributeValue(Attributes.JUMP_STRENGTH);
    }

    @Nullable
    protected SoundEvent getEatingSound() {
        return null;
    }

    @Nullable
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Nullable
    protected SoundEvent getHurtSound(DamageSource p_184601_1_) {
        if (this.random.nextInt(3) == 0) {
            this.stand();
        }

        return null;
    }

    @Nullable
    protected SoundEvent getAmbientSound() {
        if (this.random.nextInt(10) == 0 && !this.isImmobile()) {
            this.stand();
        }

        return null;
    }

    @Nullable
    protected SoundEvent getAngrySound() {
        this.stand();
        return null;
    }

    protected void playStepSound(BlockPos p_180429_1_, BlockState p_180429_2_) {
        if (!p_180429_2_.getMaterial().isLiquid()) {
            BlockState blockstate = this.level.getBlockState(p_180429_1_.above());
            SoundType soundtype = p_180429_2_.getSoundType(level, p_180429_1_, this);
            if (blockstate.is(Blocks.SNOW)) {
                soundtype = blockstate.getSoundType(level, p_180429_1_, this);
            }

            if (this.isVehicle() && this.canGallop) {
                ++this.gallopSoundCounter;
                if (this.gallopSoundCounter > 5 && this.gallopSoundCounter % 3 == 0) {
                    this.playGallopSound(soundtype);
                } else if (this.gallopSoundCounter <= 5) {
                    this.playSound(SoundEvents.HORSE_STEP_WOOD, soundtype.getVolume() * 0.15F, soundtype.getPitch());
                }
            } else if (soundtype == SoundType.WOOD) {
                this.playSound(SoundEvents.HORSE_STEP_WOOD, soundtype.getVolume() * 0.15F, soundtype.getPitch());
            } else {
                this.playSound(SoundEvents.HORSE_STEP, soundtype.getVolume() * 0.15F, soundtype.getPitch());
            }

        }
    }

    protected void playGallopSound(SoundType p_190680_1_) {
        this.playSound(SoundEvents.HORSE_GALLOP, p_190680_1_.getVolume() * 0.15F, p_190680_1_.getPitch());
    }

    public int getMaxSpawnClusterSize() {
        return 6;
    }

    public int getMaxTemper() {
        return 100;
    }

    protected float getSoundVolume() {
        return 0.8F;
    }

    public int getAmbientSoundInterval() {
        return 400;
    }

    public void openCustomInventoryScreen(Player player) {
        if (!this.level.isClientSide && (!this.isVehicle() || this.hasPassenger(player)) && this.isTame()) {
            openHorseInventory(this, this.inventory, (ServerPlayer) player);
        }

    }

    public void openHorseInventory(AbstractMountableTamableEntity entity, SimpleContainer simpleContainer, ServerPlayer player) {
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
        }

        player.nextContainerCounter();
        player.connection.send(new ClientboundHorseScreenOpenPacket(player.containerCounter, simpleContainer.getContainerSize(), entity.getId()));
        player.containerMenu = new TameableMountableHorseContainerMenu(player.containerCounter, player.getInventory(), simpleContainer, entity);
        player.initMenu(player.containerMenu);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.player.PlayerContainerEvent.Open(player, player.containerMenu));
    }

    public InteractionResult fedFood(Player p_241395_1_, ItemStack p_241395_2_) {
        boolean flag = this.handleEating(p_241395_1_, p_241395_2_);
        if (!p_241395_1_.getAbilities().instabuild) {
            p_241395_2_.shrink(1);
        }

        if (this.level.isClientSide) {
            return InteractionResult.CONSUME;
        } else {
            return flag ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
    }

    protected boolean handleEating(Player p_30593_, ItemStack p_30594_) {
        boolean flag = false;
        float f = 0.0F;
        int i = 0;
        int j = 0;
        if (p_30594_.is(Items.WHEAT)) {
            f = 2.0F;
            i = 20;
            j = 3;
        } else if (p_30594_.is(Items.SUGAR)) {
            f = 1.0F;
            i = 30;
            j = 3;
        } else if (p_30594_.is(Blocks.HAY_BLOCK.asItem())) {
            f = 20.0F;
            i = 180;
        } else if (p_30594_.is(Items.APPLE)) {
            f = 3.0F;
            i = 60;
            j = 3;
        } else if (p_30594_.is(Items.GOLDEN_CARROT)) {
            f = 4.0F;
            i = 60;
            j = 5;
            if (!this.level.isClientSide && this.isTame() && this.getAge() == 0 && !this.isInLove()) {
                flag = true;
                this.setInLove(p_30593_);
            }
        } else if (p_30594_.is(Items.GOLDEN_APPLE) || p_30594_.is(Items.ENCHANTED_GOLDEN_APPLE)) {
            f = 10.0F;
            i = 240;
            j = 10;
            if (!this.level.isClientSide && this.isTame() && this.getAge() == 0 && !this.isInLove()) {
                flag = true;
                this.setInLove(p_30593_);
            }
        }

        if (this.getHealth() < this.getMaxHealth() && f > 0.0F) {
            this.heal(f);
            flag = true;
        }

        if (this.isBaby() && i > 0) {
            this.level.addParticle(ParticleTypes.HAPPY_VILLAGER, this.getRandomX(1.0D), this.getRandomY() + 0.5D, this.getRandomZ(1.0D), 0.0D, 0.0D, 0.0D);
            if (!this.level.isClientSide) {
                this.ageUp(i);
            }

            flag = true;
        }

        if (j > 0 && (flag || !this.isTame()) && this.getTemper() < this.getMaxTemper()) {
            flag = true;
            if (!this.level.isClientSide) {
                this.modifyTemper(j);
            }
        }

        if (flag) {
            this.eating();
            this.gameEvent(GameEvent.EAT);
        }

        return flag;
    }

    protected void doPlayerRide(Player p_30634_) {
        this.setEating(false);
        this.setStanding(false);
        if (!this.level.isClientSide) {
            p_30634_.setYRot(this.getYRot());
            p_30634_.setXRot(this.getXRot());
            p_30634_.startRiding(this);
        }
    }

    protected boolean isImmobile() {
        //DevilRpg.LOGGER.info("isImmobile: super.isImmobile() {} && this.isVehicle() {} && this.isSaddled() {}|| this.isEating() {} || this.isStanding() {}", super.isImmobile(), this.isVehicle(), this.isSaddled(), this.isEating(), this.isStanding());
        return super.isImmobile() && this.isVehicle() && this.isSaddled() || this.isEating() || this.isStanding();
    }

    public boolean isFood(ItemStack p_70877_1_) {
        return FOOD_ITEMS.test(p_70877_1_);
    }

    private void moveTail() {
        this.tailCounter = 1;
    }

    protected void dropEquipment() {
        super.dropEquipment();
        if (this.inventory != null) {
            for (int i = 0; i < this.inventory.getContainerSize(); ++i) {
                ItemStack itemstack = this.inventory.getItem(i);
                if (!itemstack.isEmpty() && !EnchantmentHelper.hasVanishingCurse(itemstack)) {
                    this.spawnAtLocation(itemstack);
                }
            }

        }
    }

    public void aiStep() {
        if (this.random.nextInt(200) == 0) {
            this.moveTail();
        }

        super.aiStep();
        if (!this.level.isClientSide && this.isAlive()) {
            if (this.random.nextInt(900) == 0 && this.deathTime == 0) {
                this.heal(1.0F);
            }

            if (this.canEatGrass()) {
                if (!this.isEating() && !this.isVehicle() && this.random.nextInt(300) == 0 && this.level.getBlockState(this.blockPosition().below()).is(Blocks.GRASS_BLOCK)) {
                    this.setEating(true);
                }

                if (this.isEating() && ++this.eatingCounter > 50) {
                    this.eatingCounter = 0;
                    this.setEating(false);
                }
            }

            this.followMommy();
        }
    }

    protected void followMommy() {
        if (this.isBred() && this.isBaby() && !this.isEating()) {
            LivingEntity livingentity = this.level.getNearestEntity(AbstractMountableTamableEntity.class, MOMMY_TARGETING, this, this.getX(), this.getY(), this.getZ(), this.getBoundingBox().inflate(16.0D));
            if (livingentity != null && this.distanceToSqr(livingentity) > 4.0D) {
                this.navigation.createPath(livingentity, 0);
            }
        }

    }

    public boolean canEatGrass() {
        return true;
    }

    public void tick() {
        super.tick();
        if (this.mouthCounter > 0 && ++this.mouthCounter > 30) {
            this.mouthCounter = 0;
            this.setFlag(64, false);
        }

        if ((this.isControlledByLocalInstance() || this.isEffectiveAi()) && this.standCounter > 0 && ++this.standCounter > 20) {
            this.standCounter = 0;
            this.setStanding(false);
        }

        if (this.tailCounter > 0 && ++this.tailCounter > 8) {
            this.tailCounter = 0;
        }

        if (this.sprintCounter > 0) {
            ++this.sprintCounter;
            if (this.sprintCounter > 300) {
                this.sprintCounter = 0;
            }
        }

        this.eatAnimO = this.eatAnim;
        if (this.isEating()) {
            this.eatAnim += (1.0F - this.eatAnim) * 0.4F + 0.05F;
            if (this.eatAnim > 1.0F) {
                this.eatAnim = 1.0F;
            }
        } else {
            this.eatAnim += (0.0F - this.eatAnim) * 0.4F - 0.05F;
            if (this.eatAnim < 0.0F) {
                this.eatAnim = 0.0F;
            }
        }

        this.standAnimO = this.standAnim;
        if (this.isStanding()) {
            this.eatAnim = 0.0F;
            this.eatAnimO = this.eatAnim;
            this.standAnim += (1.0F - this.standAnim) * 0.4F + 0.05F;
            if (this.standAnim > 1.0F) {
                this.standAnim = 1.0F;
            }
        } else {
            this.allowStandSliding = false;
            this.standAnim += (0.8F * this.standAnim * this.standAnim * this.standAnim - this.standAnim) * 0.6F - 0.05F;
            if (this.standAnim < 0.0F) {
                this.standAnim = 0.0F;
            }
        }

        this.mouthAnimO = this.mouthAnim;
        if (this.getFlag(64)) {
            this.mouthAnim += (1.0F - this.mouthAnim) * 0.7F + 0.05F;
            if (this.mouthAnim > 1.0F) {
                this.mouthAnim = 1.0F;
            }
        } else {
            this.mouthAnim += (0.0F - this.mouthAnim) * 0.7F - 0.05F;
            if (this.mouthAnim < 0.0F) {
                this.mouthAnim = 0.0F;
            }
        }

    }

    private void openMouth() {
        if (!this.level.isClientSide) {
            this.mouthCounter = 1;
            this.setFlag(64, true);
        }

    }

    private void stand() {
        if (this.isControlledByLocalInstance() || this.isEffectiveAi()) {
            this.standCounter = 1;
            this.setStanding(true);
        }

    }

    public void makeMad() {
        if (!this.isStanding()) {
            this.stand();
            SoundEvent soundevent = this.getAngrySound();
            if (soundevent != null) {
                this.playSound(soundevent, this.getSoundVolume(), this.getVoicePitch());
            }
        }

    }

    public void tame(Player p_193101_1_) {
        this.setTame(true);
        this.setOwnerUUID(p_193101_1_.getUUID());
        if (p_193101_1_ instanceof ServerPlayer) {
            CriteriaTriggers.TAME_ANIMAL.trigger((ServerPlayer) p_193101_1_, this);
        }

    }

    public void travel(Vec3 p_213352_1_) {
        if (this.isAlive()) {
            if (this.isVehicle() && this.canBeControlledByRider() && this.isSaddled()) {
                LivingEntity livingentity = (LivingEntity) this.getControllingPassenger();
                this.setRot(livingentity.getYRot(), livingentity.getXRot() * 0.5F);
                this.yRotO = this.yBodyRot = this.yHeadRot = this.getYRot();
                float f = livingentity.xxa * 0.5F;
                float f1 = livingentity.zza;
                if (f1 <= 0.0F) {
                    f1 *= 0.25F;
                    this.gallopSoundCounter = 0;
                }

                if (this.onGround && this.playerJumpPendingScale == 0.0F && this.isStanding() && !this.allowStandSliding) {
                    f = 0.0F;
                    f1 = 0.0F;
                }

                if (this.playerJumpPendingScale > 0.0F && !this.isJumping() && this.onGround) {
                    double d0 = this.getCustomJump() * (double) this.playerJumpPendingScale * (double) this.getBlockJumpFactor();
                    double d1;
                    if (this.hasEffect(MobEffects.JUMP)) {
                        d1 = d0 + (double) ((float) (this.getEffect(MobEffects.JUMP).getAmplifier() + 1) * 0.1F);
                    } else {
                        d1 = d0;
                    }

                    Vec3 vec3 = this.getDeltaMovement();
                    this.setDeltaMovement(vec3.x, d1, vec3.z);
                    this.setIsJumping(true);
                    this.hasImpulse = true;
                    net.minecraftforge.common.ForgeHooks.onLivingJump(this);
                    if (f1 > 0.0F) {
                        float f2 = Mth.sin(this.getYRot() * ((float) Math.PI / 180F));
                        float f3 = Mth.cos(this.getYRot() * ((float) Math.PI / 180F));
                        this.setDeltaMovement(this.getDeltaMovement().add(-0.4F * f2 * this.playerJumpPendingScale, 0.0D, 0.4F * f3 * this.playerJumpPendingScale));
                    }

                    this.playerJumpPendingScale = 0.0F;
                }

                this.flyingSpeed = this.getSpeed() * 0.1F;
                if (this.isControlledByLocalInstance()) {
                    this.setSpeed((float) this.getAttributeValue(Attributes.MOVEMENT_SPEED));
                    super.travel(new Vec3(f, p_213352_1_.y, f1));
                } else if (livingentity instanceof Player) {
                    this.setDeltaMovement(Vec3.ZERO);
                }

                if (this.onGround) {
                    this.playerJumpPendingScale = 0.0F;
                    this.setIsJumping(false);
                }

                this.calculateEntityAnimation(this, false);
            } else {
                this.flyingSpeed = 0.02F;
                super.travel(p_213352_1_);
            }
        }
    }

    protected void playJumpSound() {
        this.playSound(SoundEvents.HORSE_JUMP, 0.4F, 1.0F);
    }

    public void addAdditionalSaveData(CompoundTag p_213281_1_) {
        super.addAdditionalSaveData(p_213281_1_);
        p_213281_1_.putBoolean("EatingHaystack", this.isEating());
        p_213281_1_.putBoolean("Bred", this.isBred());
        p_213281_1_.putInt("Temper", this.getTemper());
        p_213281_1_.putBoolean("Tame", this.isTame());
        if (this.getOwnerUUID() != null) {
            p_213281_1_.putUUID("Owner", this.getOwnerUUID());
        }

        if (!this.inventory.getItem(0).isEmpty()) {
            p_213281_1_.put("SaddleItem", this.inventory.getItem(0).save(new CompoundTag()));
        }

    }

    public void readAdditionalSaveData(CompoundTag p_70037_1_) {
        super.readAdditionalSaveData(p_70037_1_);
        this.setEating(p_70037_1_.getBoolean("EatingHaystack"));
        this.setBred(p_70037_1_.getBoolean("Bred"));
        this.setTemper(p_70037_1_.getInt("Temper"));
        this.setTame(p_70037_1_.getBoolean("Tame"));
        UUID uuid;
        if (p_70037_1_.hasUUID("Owner")) {
            uuid = p_70037_1_.getUUID("Owner");
        } else {
            String s = p_70037_1_.getString("Owner");
            uuid = OldUsersConverter.convertMobOwnerIfNecessary(this.getServer(), s);
        }

        if (uuid != null) {
            this.setOwnerUUID(uuid);
        }

        if (p_70037_1_.contains("SaddleItem", 10)) {
            ItemStack itemstack = ItemStack.of(p_70037_1_.getCompound("SaddleItem"));
            if (itemstack.getItem() == Items.SADDLE) {
                this.inventory.setItem(0, itemstack);
            }
        }

        this.updateContainerEquipment();
    }

    public boolean canMate(Animal p_70878_1_) {
        return false;
    }

    protected boolean canParent() {
        return !this.isVehicle() && !this.isPassenger() && this.isTame() && !this.isBaby() && this.getHealth() >= this.getMaxHealth() && this.isInLove();
    }

    @Nullable
    public AgeableMob getBreedOffspring(ServerLevel p_149506_, AgeableMob p_149507_) {
        return null;
    }

    /*protected void setOffspringAttributes(AgeableEntity p_190681_1_, AbstractMountableTameableEntity p_190681_2_) {
        double d0 = this.getAttributeBaseValue(Attributes.MAX_HEALTH) + p_190681_1_.getAttributeBaseValue(Attributes.MAX_HEALTH) + (double) this.generateRandomMaxHealth();
        p_190681_2_.getAttribute(Attributes.MAX_HEALTH).setBaseValue(d0 / 3.0D);
        double d1 = this.getAttributeBaseValue(Attributes.JUMP_STRENGTH) + p_190681_1_.getAttributeBaseValue(Attributes.JUMP_STRENGTH) + this.generateRandomJumpStrength();
        p_190681_2_.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(d1 / 3.0D);
        double d2 = this.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) + p_190681_1_.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) + this.generateRandomSpeed();
        p_190681_2_.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(d2 / 3.0D);
    }*/

    public boolean canBeControlledByRider() {
        return this.getControllingPassenger() instanceof LivingEntity;
    }

    @OnlyIn(Dist.CLIENT)
    public float getEatAnim(float p_110258_1_) {
        return Mth.lerp(p_110258_1_, this.eatAnimO, this.eatAnim);
    }

    @OnlyIn(Dist.CLIENT)
    public float getStandAnim(float p_110223_1_) {
        return Mth.lerp(p_110223_1_, this.standAnimO, this.standAnim);
    }

    @OnlyIn(Dist.CLIENT)
    public float getMouthAnim(float p_110201_1_) {
        return Mth.lerp(p_110201_1_, this.mouthAnimO, this.mouthAnim);
    }

    @OnlyIn(Dist.CLIENT)
    public void onPlayerJump(int p_110206_1_) {
        if (this.isSaddled()) {
            if (p_110206_1_ < 0) {
                p_110206_1_ = 0;
            } else {
                this.allowStandSliding = true;
                this.stand();
            }

            if (p_110206_1_ >= 90) {
                this.playerJumpPendingScale = 1.0F;
            } else {
                this.playerJumpPendingScale = 0.4F + 0.4F * (float) p_110206_1_ / 90.0F;
            }

        }
    }

    @Override
    public boolean canJump(Player player) {
        return this.isSaddled();
    }

    public void handleStartJump(int p_184775_1_) {
        this.allowStandSliding = true;
        this.stand();
        this.playJumpSound();
    }

    public void handleStopJump() {
    }

    @OnlyIn(Dist.CLIENT)
    protected void spawnTamingParticles(boolean p_110216_1_) {
        ParticleOptions particleOptions = p_110216_1_ ? ParticleTypes.HEART : ParticleTypes.SMOKE;

        for (int i = 0; i < 7; ++i) {
            double d0 = this.random.nextGaussian() * 0.02D;
            double d1 = this.random.nextGaussian() * 0.02D;
            double d2 = this.random.nextGaussian() * 0.02D;
            this.level.addParticle(particleOptions, this.getRandomX(1.0D), this.getRandomY() + 0.5D, this.getRandomZ(1.0D), d0, d1, d2);
        }

    }

    @OnlyIn(Dist.CLIENT)
    public void handleEntityEvent(byte p_70103_1_) {
        if (p_70103_1_ == 7) {
            this.spawnTamingParticles(true);
        } else if (p_70103_1_ == 6) {
            this.spawnTamingParticles(false);
        } else {
            super.handleEntityEvent(p_70103_1_);
        }

    }

    public void positionRider(Entity p_184232_1_) {
        super.positionRider(p_184232_1_);
        if (p_184232_1_ instanceof Mob) {
            Mob mob = (Mob) p_184232_1_;
            this.yBodyRot = mob.yBodyRot;
        }

        if (this.standAnimO > 0.0F) {
            float f3 = Mth.sin(this.yBodyRot * ((float) Math.PI / 180F));
            float f = Mth.cos(this.yBodyRot * ((float) Math.PI / 180F));
            float f1 = 0.7F * this.standAnimO;
            float f2 = 0.15F * this.standAnimO;
            p_184232_1_.setPos(this.getX() + (double) (f1 * f3), this.getY() + this.getPassengersRidingOffset() + p_184232_1_.getMyRidingOffset() + (double) f2, this.getZ() - (double) (f1 * f));
            if (p_184232_1_ instanceof LivingEntity) {
                ((LivingEntity) p_184232_1_).yBodyRot = this.yBodyRot;
            }
        }

    }

    protected float generateRandomMaxHealth() {
        return 15.0F + (float) this.random.nextInt(8) + (float) this.random.nextInt(9);
    }

    protected double generateRandomJumpStrength() {
        return (double) 0.4F + this.random.nextDouble() * 0.2D + this.random.nextDouble() * 0.2D + this.random.nextDouble() * 0.2D;
    }

    protected double generateRandomSpeed() {
        return ((double) 0.45F + this.random.nextDouble() * 0.3D + this.random.nextDouble() * 0.3D + this.random.nextDouble() * 0.3D) * 0.25D;
    }

    public boolean onClimbable() {
        return false;
    }

    protected float getStandingEyeHeight(Pose p_213348_1_, EntityDimensions p_213348_2_) {
        return p_213348_2_.height * 0.95F;
    }

    public boolean canWearArmor() {
        return false;
    }

    public boolean isWearingArmor() {
        return !this.getItemBySlot(EquipmentSlot.CHEST).isEmpty();
    }

    public boolean isArmor(ItemStack p_190682_1_) {
        return false;
    }

    public boolean setSlot(int p_174820_1_, ItemStack p_174820_2_) {
        int i = p_174820_1_ - 400;
        if (i >= 0 && i < 2 && i < this.inventory.getContainerSize()) {
            if (i == 0 && p_174820_2_.getItem() != Items.SADDLE) {
                return false;
            } else if (i != 1 || this.canWearArmor() && this.isArmor(p_174820_2_)) {
                this.inventory.setItem(i, p_174820_2_);
                this.updateContainerEquipment();
                return true;
            } else {
                return false;
            }
        } else {
            int j = p_174820_1_ - 500 + 2;
            if (j >= 2 && j < this.inventory.getContainerSize()) {
                this.inventory.setItem(j, p_174820_2_);
                return true;
            } else {
                return false;
            }
        }
    }

    @Nullable
    public Entity getControllingPassenger() {
        return this.getPassengers().isEmpty() ? null : this.getPassengers().get(0);
    }

    @Nullable
    private Vec3 getDismountLocationInDirection(Vec3 p_234236_1_, LivingEntity p_234236_2_) {
        double d0 = this.getX() + p_234236_1_.x;
        double d1 = this.getBoundingBox().minY;
        double d2 = this.getZ() + p_234236_1_.z;
        BlockPos.MutableBlockPos blockpos$mutable = new BlockPos.MutableBlockPos();

        for (Pose pose : p_234236_2_.getDismountPoses()) {
            blockpos$mutable.set(d0, d1, d2);
            double d3 = this.getBoundingBox().maxY + 0.75D;

            while (true) {
                double d4 = this.level.getBlockFloorHeight(blockpos$mutable);
                if ((double) blockpos$mutable.getY() + d4 > d3) {
                    break;
                }

                if (DismountHelper.isBlockFloorValid(d4)) {
                    AABB axisalignedbb = p_234236_2_.getLocalBoundsForPose(pose);
                    Vec3 vec3 = new Vec3(d0, (double) blockpos$mutable.getY() + d4, d2);
                    if (DismountHelper.canDismountTo(this.level, p_234236_2_, axisalignedbb.move(vec3))) {
                        p_234236_2_.setPose(pose);
                        return vec3;
                    }
                }

                blockpos$mutable.move(Direction.UP);
                if (!((double) blockpos$mutable.getY() < d3)) {
                    break;
                }
            }
        }

        return null;
    }

    public Vec3 getDismountLocationForPassenger(LivingEntity p_30576_) {
        Vec3 vec3 = getCollisionHorizontalEscapeVector((double)this.getBbWidth(), (double)p_30576_.getBbWidth(), this.getYRot() + (p_30576_.getMainArm() == HumanoidArm.RIGHT ? 90.0F : -90.0F));
        Vec3 vec31 = this.getDismountLocationInDirection(vec3, p_30576_);
        if (vec31 != null) {
            return vec31;
        } else {
            Vec3 vec32 = getCollisionHorizontalEscapeVector((double)this.getBbWidth(), (double)p_30576_.getBbWidth(), this.getYRot() + (p_30576_.getMainArm() == HumanoidArm.LEFT ? 90.0F : -90.0F));
            Vec3 vec33 = this.getDismountLocationInDirection(vec32, p_30576_);
            return vec33 != null ? vec33 : this.position();
        }
    }

    protected void randomizeAttributes(RandomSource p_218804_) {
    }

    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor p_30555_, DifficultyInstance p_30556_, MobSpawnType p_30557_, @Nullable SpawnGroupData p_30558_, @Nullable CompoundTag p_30559_) {
        if (p_30558_ == null) {
            p_30558_ = new AgeableMob.AgeableMobGroupData(0.2F);
        }

        this.randomizeAttributes(p_30555_.getRandom());
        return super.finalizeSpawn(p_30555_, p_30556_, p_30557_, p_30558_, p_30559_);
    }

    @Override
    public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(net.minecraftforge.common.capabilities.Capability<T> capability, @Nullable net.minecraft.core.Direction facing) {
        if (this.isAlive() && capability == net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER && itemHandler != null)
            return itemHandler.cast();
        return super.getCapability(capability, facing);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        if (itemHandler != null) {
            net.minecraftforge.common.util.LazyOptional<?> oldHandler = itemHandler;
            itemHandler = null;
            oldHandler.invalidate();
        }
    }

    public boolean isAlliedTo(Entity entity) {
        if (this.isTame()) {
            LivingEntity livingentity = this.getOwner();
            if (entity == livingentity) {
                return true;
            }

            if (livingentity != null) {
                return livingentity.isAlliedTo(entity);
            }
        }
        return super.isAlliedTo(entity);
    }

    public Team getTeam() {
        if (this.isTame()) {
            LivingEntity livingentity = this.getOwner();
            if (livingentity != null) {
                return livingentity.getTeam();
            }
        }
        return super.getTeam();
    }

    protected abstract LivingEntity getOwner();

    public boolean isTame() {
        return this.getFlag(4);
    }

    public void setTame(boolean p_110234_1_) {
        this.setFlag(4, p_110234_1_);
    }

    public boolean isInSittingPose() {
        return this.getFlag(1);
    }

    public void setInSittingPose(boolean p_233686_1_) {
        this.setFlag(1, p_233686_1_);
    }

    public boolean hasInventoryChanged(Container p_149512_) {
        return this.inventory != p_149512_;
    }

}