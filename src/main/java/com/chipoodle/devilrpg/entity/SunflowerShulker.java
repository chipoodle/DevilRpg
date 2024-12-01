package com.chipoodle.devilrpg.entity;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.init.ModEntities;
import com.chipoodle.devilrpg.util.IRenderUtilities;
import com.chipoodle.devilrpg.util.SkillEnum;
import com.chipoodle.devilrpg.util.TargetUtils;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.*;

public class SunflowerShulker extends TamableAnimal implements ITamableEntity, ISoulEntity, PowerableMob, NeutralMob, IPassiveMinionUpdater<SunflowerShulker> {
    protected static final EntityDataAccessor<Direction> DATA_ATTACH_FACE_ID = SynchedEntityData.defineId(SunflowerShulker.class, EntityDataSerializers.DIRECTION);
    protected static final EntityDataAccessor<Byte> DATA_PEEK_ID = SynchedEntityData.defineId(SunflowerShulker.class, EntityDataSerializers.BYTE);
    protected static final EntityDataAccessor<Byte> DATA_COLOR_ID = SynchedEntityData.defineId(SunflowerShulker.class, EntityDataSerializers.BYTE);
    static final Vector3f FORWARD = Util.make(() -> {
        Vec3i vec3i = Direction.SOUTH.getNormal();
        return new Vector3f((float) vec3i.getX(), (float) vec3i.getY(), (float) vec3i.getZ());
    });
    private static final EntityDataAccessor<Integer> DATA_REMAINING_ANGER_TIME = SynchedEntityData.defineId(SunflowerShulker.class, EntityDataSerializers.INT);
    private static final UUID COVERED_ARMOR_MODIFIER_UUID = UUID.fromString("7E0292F2-9434-48D5-A29F-9583AF7DF27F");
    private static final AttributeModifier COVERED_ARMOR_MODIFIER = new AttributeModifier(COVERED_ARMOR_MODIFIER_UUID, "Covered armor bonus", 20.0D, AttributeModifier.Operation.ADDITION);
    private static final UniformInt PERSISTENT_ANGER_TIME = TimeUtil.rangeOfSeconds(20, 39);

    private float currentPeekAmountO;
    private float currentPeekAmount;
    @Nullable
    private BlockPos clientOldAttachPosition;
    private int clientSideTeleportInterpolation;
    @Nullable
    private UUID persistentAngerTarget;

    private int puntosAsignados = 0;

    private int limitedLifeTicks;

    public SunflowerShulker(EntityType<? extends SunflowerShulker> p_33404_, Level p_33405_) {
        super(p_33404_, p_33405_);
        this.xpReward = 5;
        this.lookControl = new SunflowerShulker.ShulkerLookControl(this);
        this.limitedLifeTicks = -1;
        DevilRpg.LOGGER.info("Sunflower Shulker constructor uuid: {}", this.getUUID());
    }

    public static AttributeSupplier.Builder createAttributes() {

        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 5.0D);
    }

    private static float getPhysicalPeek(float p_149769_) {
        return 0.5F - Mth.sin((0.5F + p_149769_) * (float) Math.PI) * 0.5F;
    }

    public static AABB getProgressAabb(Direction p_149791_, float p_149792_) {
        return getProgressDeltaAabb(p_149791_, -1.0F, p_149792_);
    }

    public static AABB getProgressDeltaAabb(Direction p_149794_, float p_149795_, float p_149796_) {
        double d0 = Math.max(p_149795_, p_149796_);
        double d1 = Math.min(p_149795_, p_149796_);
        return (new AABB(BlockPos.ZERO)).expandTowards((double) p_149794_.getStepX() * d0, (double) p_149794_.getStepY() * d0, (double) p_149794_.getStepZ() * d0).contract((double) (-p_149794_.getStepX()) * (1.0D + d1), (double) (-p_149794_.getStepY()) * (1.0D + d1), (double) (-p_149794_.getStepZ()) * (1.0D + d1));
    }

    public void updateLevel(Player owner) {
        if (owner != null) {
            tame(owner);
            PlayerSkillCapabilityInterface skill = IGenericCapability.getUnwrappedPlayerCapability((Player) getOwner(), PlayerSkillCapability.INSTANCE);
            this.puntosAsignados = skill.getSkillsPoints().get(SkillEnum.VINEFLESHBALL);
            limitedLifeTicks = 400 + puntosAsignados * 20;

            Objects.requireNonNull(this.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(10 + (double) puntosAsignados / 2);
            DevilRpg.LOGGER.info("--------> update limitedLifeTicks seconds: = {} life {}", limitedLifeTicks / 20, Objects.requireNonNull(this.getAttribute(Attributes.MAX_HEALTH)).getValue());
        }
        random.setSeed(System.currentTimeMillis());

    }

    @Override
    public void aiStep() {
        super.aiStep();
        addToAiStep(this);
    }

    @Override
    public boolean wantsToAttack(@NotNull LivingEntity target, @NotNull LivingEntity owner) {
        return ITamableEntity.super.wantsToAttack(target, owner);
    }

    @Override
    public boolean isAlliedTo(@NotNull Entity entityIn) {
        boolean isOnSameTeam = super.isAlliedTo(entityIn);
        return isOnSameTeam || isEntitySameOwnerAsThis(entityIn, this);
    }

    protected void registerGoals() {
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F, 0.02F, true));
        this.goalSelector.addGoal(4, new SunflowerShulker.ShulkerAttackGoal());
        this.goalSelector.addGoal(7, new SunflowerShulker.ShulkerPeekGoal());
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, (new HurtByTargetGoal(this, this.getClass())).setAlertOthers());

        this.targetSelector.addGoal(2, new SunflowerShulker.ShulkerNearestAttackGoal(this));

        //this.targetSelector.addGoal(3, new SunflowerShulker.ShulkerDefenseAttackGoal(this));

        /*this.targetSelector.addGoal(5,
                new NearestAttackableTargetGoal<>(this, Mob.class, 10, false, false, (entity) ->
                        !(entity instanceof Villager)
                                && !(entity instanceof Llama)
                                && !(entity instanceof Turtle)
                                && !(entity instanceof IronGolem)
                                && !(entity instanceof ITamableEntity && Objects.equals(((ITamableEntity) entity).getOwnerUUID(), this.getOwnerUUID()))
                ));*/
    }

    protected Entity.@NotNull MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    public @NotNull SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    protected SoundEvent getAmbientSound() {
        return SoundEvents.SHULKER_AMBIENT;
    }

    public void playAmbientSound() {
        if (!this.isClosed()) {
            super.playAmbientSound();
        }

    }

    protected SoundEvent getDeathSound() {
        return SoundEvents.SHULKER_DEATH;
    }

    protected SoundEvent getHurtSound(@NotNull DamageSource p_33457_) {
        return this.isClosed() ? SoundEvents.SHULKER_HURT_CLOSED : SoundEvents.SHULKER_HURT;
    }

    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_ATTACH_FACE_ID, Direction.DOWN);
        this.entityData.define(DATA_PEEK_ID, (byte) 0);
        this.entityData.define(DATA_COLOR_ID, (byte) 16);
        this.entityData.define(DATA_REMAINING_ANGER_TIME, 0);
    }

    protected @NotNull BodyRotationControl createBodyControl() {
        return new SunflowerShulker.ShulkerBodyRotationControl(this);
    }

    public void readAdditionalSaveData(@NotNull CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.setAttachFace(Direction.from3DDataValue(compound.getByte("AttachFace")));
        this.entityData.set(DATA_PEEK_ID, compound.getByte("Peek"));
        if (compound.contains("Color", 99)) {
            this.entityData.set(DATA_COLOR_ID, compound.getByte("Color"));
        }
        this.limitedLifeTicks = compound.getInt("limitedLifeTicks");
    }

    public void addAdditionalSaveData(@NotNull CompoundTag compoundTag) {
        super.addAdditionalSaveData(compoundTag);
        compoundTag.putByte("AttachFace", (byte) this.getAttachFace().get3DDataValue());
        compoundTag.putByte("Peek", this.entityData.get(DATA_PEEK_ID));
        compoundTag.putByte("Color", this.entityData.get(DATA_COLOR_ID));
        compoundTag.putString("OwnerUUID", "");
        compoundTag.putString("Owner", "");
        compoundTag.putInt("limitedLifeTicks", limitedLifeTicks);
    }

    public void tick() {
        super.tick();
        if (!this.level.isClientSide && !this.isPassenger() && !this.canStayAt(this.blockPosition(), this.getAttachFace())) {
            this.findNewAttachment();
        }

        if (this.updatePeekAmount()) {
            this.onPeekAmountChange();
        }

        if (this.level.isClientSide) {
            if (this.clientSideTeleportInterpolation > 0) {
                --this.clientSideTeleportInterpolation;
            } else {
                this.clientOldAttachPosition = null;
            }
        }

        if (--this.limitedLifeTicks == 0) {
            customOnDeath();
        }
    }

    public void die(@NotNull DamageSource cause) {
        customOnDeath();
    }

    private void customOnDeath() {
        level.broadcastEntityEvent(this, (byte) 3);
        this.dead = true;
        this.remove(RemovalReason.DISCARDED);
        IRenderUtilities.customDeadParticles(this.level, this.random, this);
    }


    private void findNewAttachment() {
        Direction direction = this.findAttachableSurface(this.blockPosition());
        if (direction != null) {
            this.setAttachFace(direction);
        } else {
            this.teleportSomewhere();
        }

    }

    protected @NotNull AABB makeBoundingBox() {
        float f = getPhysicalPeek(this.currentPeekAmount);
        Direction direction = this.getAttachFace().getOpposite();
        float f1 = this.getType().getWidth() / 2.0F;
        return getProgressAabb(direction, f).move(this.getX() - (double) f1, this.getY(), this.getZ() - (double) f1);
    }

    private boolean updatePeekAmount() {
        this.currentPeekAmountO = this.currentPeekAmount;
        float f = (float) this.getRawPeekAmount() * 0.01F;
        if (this.currentPeekAmount == f) {
            return false;
        } else {
            if (this.currentPeekAmount > f) {
                this.currentPeekAmount = Mth.clamp(this.currentPeekAmount - 0.05F, f, 1.0F);
            } else {
                this.currentPeekAmount = Mth.clamp(this.currentPeekAmount + 0.05F, 0.0F, f);
            }

            return true;
        }
    }

    private void onPeekAmountChange() {
        this.reapplyPosition();
        float f = getPhysicalPeek(this.currentPeekAmount);
        float f1 = getPhysicalPeek(this.currentPeekAmountO);
        Direction direction = this.getAttachFace().getOpposite();
        float f2 = f - f1;
        if (!(f2 <= 0.0F)) {
            for (Entity entity : this.level.getEntities(this, getProgressDeltaAabb(direction, f1, f).move(this.getX() - 0.5D, this.getY(), this.getZ() - 0.5D), EntitySelector.NO_SPECTATORS.and((p_149771_) -> !p_149771_.isPassengerOfSameVehicle(this)))) {
                if (!(entity instanceof SunflowerShulker) && !entity.noPhysics) {
                    entity.move(MoverType.SHULKER, new Vec3(f2 * (float) direction.getStepX(), f2 * (float) direction.getStepY(), f2 * (float) direction.getStepZ()));
                }
            }

        }
    }

    public double getMyRidingOffset() {
        EntityType<?> entitytype = Objects.requireNonNull(this.getVehicle()).getType();
        return !(this.getVehicle() instanceof Boat) && entitytype != EntityType.MINECART ? super.getMyRidingOffset() : 0.1875D - this.getVehicle().getPassengersRidingOffset();
    }

    public boolean startRiding(@NotNull Entity p_149773_, boolean p_149774_) {
        if (this.level.isClientSide()) {
            this.clientOldAttachPosition = null;
            this.clientSideTeleportInterpolation = 0;
        }

        this.setAttachFace(Direction.DOWN);
        return super.startRiding(p_149773_, p_149774_);
    }

    public void stopRiding() {
        super.stopRiding();
        if (this.level.isClientSide) {
            this.clientOldAttachPosition = this.blockPosition();
        }

        this.yBodyRotO = 0.0F;
        this.yBodyRot = 0.0F;
    }

    @Nullable
    public SpawnGroupData finalizeSpawn(@NotNull ServerLevelAccessor p_149780_, @NotNull DifficultyInstance p_149781_, @NotNull MobSpawnType p_149782_, @Nullable SpawnGroupData p_149783_, @Nullable CompoundTag p_149784_) {
        this.setYRot(0.0F);
        this.yHeadRot = this.getYRot();
        this.setOldPosAndRot();
        return super.finalizeSpawn(p_149780_, p_149781_, p_149782_, p_149783_, p_149784_);
    }

    @Override
    public AgeableMob getBreedOffspring(@NotNull ServerLevel serverLevel, @NotNull AgeableMob ageableMob) {
        return null;
    }

    public void move(@NotNull MoverType p_33424_, @NotNull Vec3 p_33425_) {
        if (p_33424_ == MoverType.SHULKER_BOX) {
            this.teleportSomewhere();
        } else {
            super.move(p_33424_, p_33425_);
        }

    }

    @Override
    public @NotNull Vec3 getDeltaMovement() {
        return Vec3.ZERO;
    }

    @Override
    public void setDeltaMovement(@NotNull Vec3 p_149804_) {
    }

    public void setPos(double p_33449_, double p_33450_, double p_33451_) {
        BlockPos blockpos = this.blockPosition();
        if (this.isPassenger()) {
            super.setPos(p_33449_, p_33450_, p_33451_);
        } else {
            super.setPos((double) Mth.floor(p_33449_) + 0.5D, (double) Mth.floor(p_33450_ + 0.5D), (double) Mth.floor(p_33451_) + 0.5D);
        }

        if (this.tickCount != 0) {
            BlockPos blockpos1 = this.blockPosition();
            if (!blockpos1.equals(blockpos)) {
                this.entityData.set(DATA_PEEK_ID, (byte) 0);
                this.hasImpulse = true;
                if (this.level.isClientSide && !this.isPassenger() && !blockpos1.equals(this.clientOldAttachPosition)) {
                    this.clientOldAttachPosition = blockpos;
                    this.clientSideTeleportInterpolation = 6;
                    this.xOld = this.getX();
                    this.yOld = this.getY();
                    this.zOld = this.getZ();
                }
            }

        }
    }

    @Nullable
    protected Direction findAttachableSurface(BlockPos p_149811_) {
        for (Direction direction : Direction.values()) {
            if (this.canStayAt(p_149811_, direction)) {
                return direction;
            }
        }

        return null;
    }

    boolean canStayAt(BlockPos p_149786_, Direction p_149787_) {
        if (this.isPositionBlocked(p_149786_)) {
            return false;
        } else {
            Direction direction = p_149787_.getOpposite();
            if (!this.level.loadedAndEntityCanStandOnFace(p_149786_.relative(p_149787_), this, direction)) {
                return false;
            } else {
                AABB aabb = getProgressAabb(direction, 1.0F).move(p_149786_).deflate(1.0E-6D);
                return this.level.noCollision(this, aabb);
            }
        }
    }

    private boolean isPositionBlocked(BlockPos p_149813_) {
        BlockState blockstate = this.level.getBlockState(p_149813_);
        if (blockstate.isAir()) {
            return false;
        } else {
            boolean flag = blockstate.is(Blocks.MOVING_PISTON) && p_149813_.equals(this.blockPosition());
            return !flag;
        }
    }

    protected void teleportSomewhere() {
        if (!this.isNoAi() && this.isAlive()) {
            BlockPos blockpos = this.blockPosition();

            for (int i = 0; i < 5; ++i) {
                //BlockPos blockpos1 = blockpos.offset(Mth.randomBetweenInclusive(this.random, -8, 8), Mth.randomBetweenInclusive(this.random, -8, 8), Mth.randomBetweenInclusive(this.random, -8, 8));
               BlockPos blockpos1 = TargetUtils.findSolidGroundBelowMinusOneEmpty(level, blockpos);

                if (blockpos1.getY() > this.level.getMinBuildHeight() && this.level.isEmptyBlock(blockpos1) && this.level.getWorldBorder().isWithinBounds(blockpos1) && this.level.noCollision(this, (new AABB(blockpos1)).deflate(1.0E-6D))) {
                    Direction direction = this.findAttachableSurface(blockpos1);
                    if (direction != null) {
                        net.minecraftforge.event.entity.EntityTeleportEvent.EnderEntity event = net.minecraftforge.event.ForgeEventFactory.onEnderTeleport(this, blockpos1.getX(), blockpos1.getY(), blockpos1.getZ());
                        if (event.isCanceled()) direction = null;
                        blockpos1 = BlockPos.containing(event.getTargetX(), event.getTargetY(), event.getTargetZ());
                    }

                    if (direction != null) {
                        this.unRide();
                        this.setAttachFace(direction);
                        this.playSound(SoundEvents.SHULKER_TELEPORT, 1.0F, 1.0F);
                        this.setPos((double) blockpos1.getX() + 0.5D, blockpos1.getY(), (double) blockpos1.getZ() + 0.5D);
                        this.level.gameEvent(GameEvent.TELEPORT, blockpos, GameEvent.Context.of(this));
                        this.entityData.set(DATA_PEEK_ID, (byte) 0);
                        this.setTarget(null);
                        return;
                    }
                }
            }
        }
    }

    public void lerpTo(double p_33411_, double p_33412_, double p_33413_, float p_33414_, float p_33415_, int p_33416_, boolean p_33417_) {
        this.lerpSteps = 0;
        this.setPos(p_33411_, p_33412_, p_33413_);
        this.setRot(p_33414_, p_33415_);
    }

    public boolean hurt(@NotNull DamageSource p_33421_, float p_33422_) {
        if (this.isClosed()) {
            Entity entity = p_33421_.getDirectEntity();
            if (entity instanceof AbstractArrow) {
                return false;
            }
        }

        if (!super.hurt(p_33421_, p_33422_)) {
            return false;
        } else {
            if ((double) this.getHealth() < (double) this.getMaxHealth() * 0.5D && this.random.nextInt(4) == 0) {
                //this.teleportSomewhere();
            } else if (p_33421_.is(DamageTypeTags.IS_PROJECTILE)) {
                Entity entity1 = p_33421_.getDirectEntity();
                if (entity1 != null && entity1.getType() == EntityType.SHULKER_BULLET) {
                    //this.hitByShulkerBullet();
                }
            }

            return true;
        }
    }

    @Override
    public void tame(@NotNull Player player) {
        super.tame(player);
        this.level.broadcastEntityEvent(this, (byte) 7);
    }

    private boolean isClosed() {
        return this.getRawPeekAmount() == 0;
    }

    /*private void hitByShulkerBullet() {
        Vec3 vec3 = this.position();
        AABB aabb = this.getBoundingBox();
        if (!this.isClosed() && this.teleportSomewhere()) {
            int i = this.level.getEntities(ModEntities.SUNFLOWER_SHULKER.get(), aabb.inflate(8.0D), Entity::isAlive).size();
            float f = (float) (i - 1) / 5.0F;
            if (!(this.level.random.nextFloat() < f)) {
                SunflowerShulker shulker = ModEntities.SUNFLOWER_SHULKER.get().create(this.level);
                if (shulker != null) {
                    shulker.setVariant(this.getVariant());
                    shulker.moveTo(vec3);
                    this.level.addFreshEntity(shulker);
                }

            }
        }
    }*/

    public boolean canBeCollidedWith() {
        return this.isAlive();
    }

    public Direction getAttachFace() {
        return this.entityData.get(DATA_ATTACH_FACE_ID);
    }

    private void setAttachFace(Direction p_149789_) {
        this.entityData.set(DATA_ATTACH_FACE_ID, p_149789_);
    }

    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> p_33434_) {
        if (DATA_ATTACH_FACE_ID.equals(p_33434_)) {
            this.setBoundingBox(this.makeBoundingBox());
        }

        super.onSyncedDataUpdated(p_33434_);
    }

    private int getRawPeekAmount() {
        return this.entityData.get(DATA_PEEK_ID);
    }

    void setRawPeekAmount(int p_33419_) {
        if (!this.level.isClientSide) {
            Objects.requireNonNull(this.getAttribute(Attributes.ARMOR)).removeModifier(COVERED_ARMOR_MODIFIER);
            if (p_33419_ == 0) {
                Objects.requireNonNull(this.getAttribute(Attributes.ARMOR)).addPermanentModifier(COVERED_ARMOR_MODIFIER);
                this.playSound(SoundEvents.SHULKER_CLOSE, 1.0F, 1.0F);
                this.gameEvent(GameEvent.CONTAINER_CLOSE);
            } else {
                this.playSound(SoundEvents.SHULKER_OPEN, 1.0F, 1.0F);
                this.gameEvent(GameEvent.CONTAINER_OPEN);
            }
        }

        this.entityData.set(DATA_PEEK_ID, (byte) p_33419_);
    }

    public float getClientPeekAmount(float p_33481_) {
        return Mth.lerp(p_33481_, this.currentPeekAmountO, this.currentPeekAmount);
    }

    protected float getStandingEyeHeight(@NotNull Pose p_33438_, @NotNull EntityDimensions p_33439_) {
        return 0.5F;
    }

    public void recreateFromPacket(@NotNull ClientboundAddEntityPacket p_219067_) {
        super.recreateFromPacket(p_219067_);
        this.yBodyRot = 0.0F;
        this.yBodyRotO = 0.0F;
    }

    @Override
    public boolean isOrderedToSit() {
        return false;
    }

    @Override
    public void setOrderedToSit(boolean sit) {

    }

    @Override
    public double distanceToSqr(LivingEntity livingentity) {
        return super.distanceToSqr(livingentity);
    }

    public int getMaxHeadXRot() {
        return 180;
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
    public Entity getEntity() {
        return this;
    }

    @Override
    public boolean isTame() {
        return super.isTame();
    }

    @Override
    public void setTame(boolean tamed) {
        super.setTame(tamed);
    }

    @Override
    public UUID getOwnerUUID() {
        return super.getOwnerUUID();
    }

    @Override
    public void setOwnerUUID(UUID uuid) {
        super.setOwnerUUID(uuid);
    }

    public int getMaxHeadYRot() {
        return 180;
    }

    public void push(@NotNull Entity p_33474_) {
    }

    public Optional<Vec3> getRenderPosition(float p_149767_) {
        if (this.clientOldAttachPosition != null && this.clientSideTeleportInterpolation > 0) {
            double d0 = (double) ((float) this.clientSideTeleportInterpolation - p_149767_) / 6.0D;
            d0 *= d0;
            BlockPos blockpos = this.blockPosition();
            double d1 = (double) (blockpos.getX() - this.clientOldAttachPosition.getX()) * d0;
            double d2 = (double) (blockpos.getY() - this.clientOldAttachPosition.getY()) * d0;
            double d3 = (double) (blockpos.getZ() - this.clientOldAttachPosition.getZ()) * d0;
            return Optional.of(new Vec3(-d1, -d2, -d3));
        } else {
            return Optional.empty();
        }
    }

    public @NotNull Optional<DyeColor> getVariant() {
        return Optional.ofNullable(this.getColor());
    }

    public void setVariant(Optional<DyeColor> p_262609_) {
        this.entityData.set(DATA_COLOR_ID, p_262609_.map((p_262566_) -> {
            return (byte) p_262566_.getId();
        }).orElse((byte) 16));
    }

    @Nullable
    public DyeColor getColor() {
        byte b0 = this.entityData.get(DATA_COLOR_ID);
        return b0 != 16 && b0 <= 15 ? DyeColor.byId(b0) : null;
    }

    @Override
    public int getRemainingPersistentAngerTime() {
        return this.entityData.get(DATA_REMAINING_ANGER_TIME);
    }

    @Override
    public void setRemainingPersistentAngerTime(int remainingAngerTime) {
        this.entityData.set(DATA_REMAINING_ANGER_TIME, remainingAngerTime);
    }

    @Override
    public @Nullable UUID getPersistentAngerTarget() {
        return this.persistentAngerTarget;
    }

    public void setPersistentAngerTarget(@Nullable UUID p_30400_) {
        this.persistentAngerTarget = p_30400_;
    }

    @Override
    public void startPersistentAngerTimer() {
        this.setRemainingPersistentAngerTime(PERSISTENT_ANGER_TIME.sample(this.random));
    }

    @Override
    public boolean isPowered() {
        return true;
    }

    static class ShulkerBodyRotationControl extends BodyRotationControl {
        public ShulkerBodyRotationControl(Mob p_149816_) {
            super(p_149816_);
        }

        public void clientTick() {
        }
    }

    static class ShulkerDefenseAttackGoal extends NearestAttackableTargetGoal<LivingEntity> {
        public ShulkerDefenseAttackGoal(SunflowerShulker p_33496_) {
            super(p_33496_, LivingEntity.class, 10, true, false, (entity) ->
                    !(entity instanceof Villager)
                            && !(entity instanceof Llama)
                            && !(entity instanceof Turtle)
                            && !(entity instanceof IronGolem)
                            && !(entity instanceof ITamableEntity && Objects.equals(((ITamableEntity) entity).getOwnerUUID(), p_33496_.getOwnerUUID()))
            );
        }

        public boolean canUse() {
            return this.mob.getTeam() != null && super.canUse();
        }

        protected @NotNull AABB getTargetSearchArea(double p_33499_) {
            Direction direction = ((SunflowerShulker) this.mob).getAttachFace();
            if (direction.getAxis() == Direction.Axis.X) {
                return this.mob.getBoundingBox().inflate(4.0D, p_33499_, p_33499_);
            } else {
                return direction.getAxis() == Direction.Axis.Z ? this.mob.getBoundingBox().inflate(p_33499_, p_33499_, 4.0D) : this.mob.getBoundingBox().inflate(p_33499_, 4.0D, p_33499_);
            }
        }
    }

    class ShulkerAttackGoal extends Goal {
        private int attackTime;

        public ShulkerAttackGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        public boolean canUse() {
            LivingEntity target = SunflowerShulker.this.getTarget();
            if (target != null && target.isAlive()) {
                return SunflowerShulker.this.level.getDifficulty() != Difficulty.PEACEFUL;
            } else {
                return false;
            }
        }

        public void start() {
            this.attackTime = 20;
            SunflowerShulker.this.setRawPeekAmount(100);
        }

        public void stop() {
            SunflowerShulker.this.setRawPeekAmount(0);
        }

        public boolean requiresUpdateEveryTick() {
            return true;
        }

        public void tick() {
            if (SunflowerShulker.this.level.getDifficulty() != Difficulty.PEACEFUL) {
                --this.attackTime;
                LivingEntity livingentity = SunflowerShulker.this.getTarget();
                if (livingentity != null) {
                    SunflowerShulker.this.getLookControl().setLookAt(livingentity, 180.0F, 180.0F);
                    double d0 = SunflowerShulker.this.distanceToSqr(livingentity);
                    if (d0 < 600.0D) {
                        if (this.attackTime <= 0) {
                            this.attackTime = 20 /*+ SunflowerShulker.this.random.nextInt(10) * 20 / 2*/;


                            d0 = livingentity.getX() - SunflowerShulker.this.getX();
                            double d1 = livingentity.getY(0.3333333333333333D) - SunflowerShulker.this.getY();
                            double d2 = livingentity.getZ() - SunflowerShulker.this.getZ();
                            double d3 = Math.sqrt(d0 * d0 + d2 * d2);


                            PlayerSkillCapabilityInterface unwrappedPlayerCapability = IGenericCapability.getUnwrappedPlayerCapability((Player) getOwner(), PlayerSkillCapability.INSTANCE);
                            Integer assignedPoint = unwrappedPlayerCapability.getSkillsPoints().get(SkillEnum.VINEFLESHBALL);

                           /* var snowballEntity = new GenericItemProjectile(SunflowerShulker.this.level, SunflowerShulker.this);
                            snowballEntity.updateLevel(SunflowerShulker.this, assignedPoint);
                            snowballEntity.shoot(
                                    d0,
                                    d1 + d3 * (double) 0.1F,
                                    d2,
                                    1.6F, (float) (14 - SunflowerShulker.this.level.getDifficulty().getId() * 4));
                            SunflowerShulker.this.level.addFreshEntity(snowballEntity);*/


                            ExplodingSporeBullet explodingSporeBullet = ModEntities.EXPLODING_SPORE_BULLET.get().create((ServerLevel) SunflowerShulker.this.level, null, null, SunflowerShulker.this.blockPosition(), MobSpawnType.MOB_SUMMONED, true, true);
                            Objects.requireNonNull(explodingSporeBullet).updateLevel((Player) getOwner());
                            explodingSporeBullet.moveTo(SunflowerShulker.this.blockPosition(), Mth.wrapDegrees(new Random().nextFloat() * 360.0F), 0.0F);
                            SunflowerShulker.this.level.addFreshEntity(explodingSporeBullet);

                            SunflowerShulker.this.playSound(SoundEvents.SHULKER_SHOOT, 2.0F, (SunflowerShulker.this.random.nextFloat() - SunflowerShulker.this.random.nextFloat()) * 0.2F + 1.0F);
                        }
                    } else {
                        SunflowerShulker.this.setTarget((LivingEntity) null);
                    }

                    super.tick();
                }
            }
        }
    }

    class ShulkerLookControl extends LookControl {
        public ShulkerLookControl(Mob p_149820_) {
            super(p_149820_);
        }

        protected void clampHeadRotationToBody() {
        }

        protected @NotNull Optional<Float> getYRotD() {
            Direction direction = SunflowerShulker.this.getAttachFace().getOpposite();
            Vector3f vector3f = direction.getRotation().transform(new Vector3f(SunflowerShulker.FORWARD));
            Vec3i vec3i = direction.getNormal();
            Vector3f vector3f1 = new Vector3f((float) vec3i.getX(), (float) vec3i.getY(), (float) vec3i.getZ());
            vector3f1.cross(vector3f);
            double d0 = this.wantedX - this.mob.getX();
            double d1 = this.wantedY - this.mob.getEyeY();
            double d2 = this.wantedZ - this.mob.getZ();
            Vector3f vector3f2 = new Vector3f((float) d0, (float) d1, (float) d2);
            float f = vector3f1.dot(vector3f2);
            float f1 = vector3f.dot(vector3f2);
            return !(Math.abs(f) > 1.0E-5F) && !(Math.abs(f1) > 1.0E-5F) ? Optional.empty() : Optional.of((float) (Mth.atan2(-f, f1) * (double) (180F / (float) Math.PI)));
        }

        protected @NotNull Optional<Float> getXRotD() {
            return Optional.of(0.0F);
        }
    }

    class ShulkerNearestAttackGoal extends NearestAttackableTargetGoal<Mob> {
        public ShulkerNearestAttackGoal(SunflowerShulker p_33505_) {
            super(p_33505_, Mob.class, true);
        }

        public boolean canUse() {
            return SunflowerShulker.this.level.getDifficulty() != Difficulty.PEACEFUL && super.canUse();
        }

        protected @NotNull AABB getTargetSearchArea(double p_33508_) {
            Direction direction = ((SunflowerShulker) this.mob).getAttachFace();
            if (direction.getAxis() == Direction.Axis.X) {
                return this.mob.getBoundingBox().inflate(4.0D, p_33508_, p_33508_);
            } else {
                return direction.getAxis() == Direction.Axis.Z ? this.mob.getBoundingBox().inflate(p_33508_, p_33508_, 4.0D) : this.mob.getBoundingBox().inflate(p_33508_, 4.0D, p_33508_);
            }
        }
    }

    class ShulkerPeekGoal extends Goal {
        private int peekTime;

        public boolean canUse() {
            return SunflowerShulker.this.getTarget() == null && SunflowerShulker.this.random.nextInt(reducedTickDelay(40)) == 0 && SunflowerShulker.this.canStayAt(SunflowerShulker.this.blockPosition(), SunflowerShulker.this.getAttachFace());
        }

        public boolean canContinueToUse() {
            return SunflowerShulker.this.getTarget() == null && this.peekTime > 0;
        }

        public void start() {
            this.peekTime = this.adjustedTickDelay(20 * (1 + SunflowerShulker.this.random.nextInt(3)));
            SunflowerShulker.this.setRawPeekAmount(30);
        }

        public void stop() {
            if (SunflowerShulker.this.getTarget() == null) {
                SunflowerShulker.this.setRawPeekAmount(0);
            }

        }

        public void tick() {
            --this.peekTime;
        }
    }
}
