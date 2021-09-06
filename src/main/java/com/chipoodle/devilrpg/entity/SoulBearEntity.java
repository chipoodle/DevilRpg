package com.chipoodle.devilrpg.entity;

import java.util.UUID;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.chipoodle.devilrpg.capability.minion.IBaseMinionCapability;
import com.chipoodle.devilrpg.capability.minion.PlayerMinionCapabilityProvider;
import com.chipoodle.devilrpg.capability.skill.IBaseSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityProvider;
import com.chipoodle.devilrpg.client.render.IRenderUtilities;
import com.chipoodle.devilrpg.init.ModEntityTypes;
import com.chipoodle.devilrpg.util.SkillEnum;

import net.minecraft.block.BlockState;
import net.minecraft.entity.AgeableEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntitySize;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.IAngerable;
import net.minecraft.entity.IChargeableMob;
import net.minecraft.entity.ILivingEntityData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.Pose;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.entity.ai.goal.FollowParentGoal;
import net.minecraft.entity.ai.goal.HurtByTargetGoal;
import net.minecraft.entity.ai.goal.LookAtGoal;
import net.minecraft.entity.ai.goal.LookRandomlyGoal;
import net.minecraft.entity.ai.goal.NearestAttackableTargetGoal;
import net.minecraft.entity.ai.goal.OwnerHurtByTargetGoal;
import net.minecraft.entity.ai.goal.OwnerHurtTargetGoal;
import net.minecraft.entity.ai.goal.RandomWalkingGoal;
import net.minecraft.entity.ai.goal.ResetAngerGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.merchant.villager.VillagerEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.TurtleEntity;
import net.minecraft.entity.passive.horse.LlamaEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.IPacket;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Hand;
import net.minecraft.util.RangedInteger;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.TickRangeConverter;
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

public class SoulBearEntity extends TameableEntity
		implements ISoulEntity, IChargeableMob, IRenderUtilities, IAngerable {
	private static final DataParameter<Boolean> DATA_STANDING_ID = EntityDataManager.defineId(SoulBearEntity.class,DataSerializers.BOOLEAN);

	private float clientSideStandAnimationO;
	private float clientSideStandAnimation;
	private int warningSoundTicks;
	private static final RangedInteger PERSISTENT_ANGER_TIME = TickRangeConverter.rangeOfSeconds(20, 39);
	private int remainingPersistentAngerTime;
   	private UUID persistentAngerTarget;

	private final int SALUD_INICIAL = 30;
	private int puntosAsignados = 0;
	private double saludMaxima = SALUD_INICIAL;
	private static double initialArmor;

	public SoulBearEntity(EntityType<? extends SoulBearEntity> type, World worldIn) {
		super(type, worldIn);
	}

	public AgeableEntity getBreedOffspring(AgeableEntity ageable) {
		return ModEntityTypes.SOUL_BEAR.get().create(this.level);
	}

	public AgeableEntity getBreedOffspring(ServerWorld level, AgeableEntity ageable) {
	      return ModEntityTypes.SOUL_BEAR.get().create(level);
	}

	/**
	 * Checks if the parameter is an item which this animal can be fed to breed it
	 * (wheat, carrots or seeds depending on the animal type)
	 */
	public boolean isFood(ItemStack stack) {
		return false;
	}

	protected void registerGoals() {
		super.registerGoals();
		this.goalSelector.addGoal(0, new SwimGoal(this));
		this.goalSelector.addGoal(1, new SoulBearEntity.MeleeAttackGoal());
		// this.goalSelector.addGoal(1, new SoulBearEntity.PanicGoal());
		this.goalSelector.addGoal(3, new SoulBearEntity.AvoidEntityGoal<VillagerEntity>(this, VillagerEntity.class, 24.0F, 1.5D, 1.5D));
		this.goalSelector.addGoal(3, new SoulBearEntity.AvoidEntityGoal<LlamaEntity>(this, LlamaEntity.class, 24.0F, 1.5D, 1.5D));
		this.goalSelector.addGoal(3, new SoulBearEntity.AvoidEntityGoal<TurtleEntity>(this, TurtleEntity.class, 24.0F, 1.5D, 1.5D));
		this.goalSelector.addGoal(3, new SoulBearEntity.AvoidEntityGoal<IronGolemEntity>(this, IronGolemEntity.class, 24.0F, 1.5D, 1.5D));
		this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.25D));
		this.goalSelector.addGoal(5, new RandomWalkingGoal(this, 1.0D));
		this.goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0D, 10.0F, 2.0F, false));
		this.goalSelector.addGoal(7, new LookAtGoal(this, PlayerEntity.class, 6.0F));
		this.goalSelector.addGoal(8, new LookRandomlyGoal(this));
		
		// this.targetSelector.addGoal(2, new SoulBearEntity.AttackPlayerGoal());
		// this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this,
		// MobEntity.class, 10, true, true,(Predicate<LivingEntity>) null));

		this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
		// this.targetSelector.addGoal(3, new SoulBearEntity.HurtByTargetGoal());
		this.targetSelector.addGoal(3, (new HurtByTargetGoal(this)).setAlertOthers());
		this.targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(this, MobEntity.class, false));
		this.targetSelector.addGoal(8, new ResetAngerGoal<>(this, true));

	}
	
	public static AttributeModifierMap.MutableAttribute setAttributes() {
		return MobEntity.createMobAttributes()
				.add(Attributes.MOVEMENT_SPEED, (double) 0.3F)
				.add(Attributes.MAX_HEALTH, 8.0D)
				.add(Attributes.FOLLOW_RANGE, 16.0D)
				.add(Attributes.ATTACK_DAMAGE, 2.0D)
				.add(Attributes.ARMOR, 0.35D);
	}

	public void updateLevel(PlayerEntity owner) {
		tame(owner);
		LazyOptional<IBaseSkillCapability> skill = getOwner().getCapability(PlayerSkillCapabilityProvider.SKILL_CAP);
		if (skill != null && skill.isPresent()) {
			this.puntosAsignados = skill.map(x -> x.getSkillsPoints()).orElse(null).get(SkillEnum.SUMMON_SOUL_BEAR);
			saludMaxima = 5 * this.puntosAsignados + SALUD_INICIAL;
			initialArmor = (1.0D * 0.309 * puntosAsignados) + 2;
		}

		this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue((double) 0.4F);
		this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(saludMaxima);
		this.getAttribute(Attributes.ARMOR).setBaseValue(initialArmor);
		this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(16.0D);
		this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue((1.5 * puntosAsignados) + 4); // 4-34
		setHealth((float) saludMaxima);
	}

	@Override
	public void readAdditionalSaveData(CompoundNBT compound) {
		super.readAdditionalSaveData(compound);
	}

	@Override
	public void addAdditionalSaveData(CompoundNBT compound) {
		super.addAdditionalSaveData(compound);
		compound.putString("OwnerUUID", "");
		compound.putString("Owner", "");
		//compound.putUniqueId("Owner", UUID.fromString(""));
	}

	@Override
	public void aiStep() {
		super.aiStep();
		addToLivingTick(this);
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

	@Override
	public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
		return addToWantsToAttack(target, owner);
	}

	@Override
	public boolean doHurtTarget(Entity entityIn) {
		boolean flag = entityIn.hurt(DamageSource.mobAttack(this),
				(float) ((int) this.getAttributeValue(Attributes.ATTACK_DAMAGE)));
		if (flag) {
			this.doEnchantDamageEffects(this, entityIn);
		}
		return flag;
	}
	
	public void startPersistentAngerTimer() {
		this.setRemainingPersistentAngerTime(PERSISTENT_ANGER_TIME.randomValue(this.random));
	}

	public void setRemainingPersistentAngerTime(int p_230260_1_) {
		this.remainingPersistentAngerTime = p_230260_1_;
	}

	public int getRemainingPersistentAngerTime() {
		return this.remainingPersistentAngerTime;
	}

	public void setPersistentAngerTarget(@Nullable UUID p_230259_1_) {
		this.persistentAngerTarget = p_230259_1_;
	}

	public UUID getPersistentAngerTarget() {
		return this.persistentAngerTarget;
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
			LazyOptional<IBaseMinionCapability> minionCap = getOwner()
					.getCapability(PlayerMinionCapabilityProvider.MINION_CAP);
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
		customDeadParticles(this.level, this.random, this);
	}

	/**
	 * Called on the logical server to get a packet to send to the client containing
	 * data necessary to spawn your entity. Using Forge's method instead of the
	 * default vanilla one allows extra stuff to work such as sending extra data,
	 * using a non-default entity factory and having
	 * {@link IEntityAdditionalSpawnData} work.
	 *
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
		this.entityData.define(DATA_STANDING_ID, false);
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
			this.updatePersistentAnger((ServerWorld)this.level, true);
	    }

	}
	
	@Override
	public ActionResultType mobInteract(PlayerEntity playerIn, Hand hand) {
		return ActionResultType.PASS;
	}

	public EntitySize getDimensions(Pose poseIn) {
		if (this.clientSideStandAnimation > 0.0F) {
			float f = this.clientSideStandAnimation / 6.0F;
			float f1 = 1.0F + f;
			return super.getDimensions(poseIn).scale(1.0F, f1);
		} else {
			return super.getDimensions(poseIn);
		}
	}

	public boolean isStanding() {
	      return this.entityData.get(DATA_STANDING_ID);
	}

	public void setStanding(boolean p_189794_1_) {
	      this.entityData.set(DATA_STANDING_ID, p_189794_1_);
	}

	@OnlyIn(Dist.CLIENT)
	public float getStandingAnimationScale(float p_189795_1_) {
		return MathHelper.lerp(p_189795_1_, this.clientSideStandAnimationO, this.clientSideStandAnimation) / 6.0F;
	}

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

	class AttackPlayerGoal extends NearestAttackableTargetGoal<PlayerEntity> {
		public AttackPlayerGoal() {
			super(SoulBearEntity.this, PlayerEntity.class, 20, true, true, (Predicate<LivingEntity>) null);
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

		protected double getFollowDistance() {
			return super.getFollowDistance() * 0.5D;
		}
	}

	class MeleeAttackGoal extends net.minecraft.entity.ai.goal.MeleeAttackGoal {
		public MeleeAttackGoal() {
			super(SoulBearEntity.this, 1.25D, true);
		}

		protected void checkAndPerformAttack(LivingEntity enemy, double distToEnemySqr) {
			double d0 = this.getAttackReachSqr(enemy);
			if (distToEnemySqr <= d0 && this.isTimeToAttack()) {
				this.resetAttackCooldown();
				this.mob.doHurtTarget(enemy);
				SoulBearEntity.this.setStanding(false);
			} else if (distToEnemySqr <= d0 * 2.0D) {
				if (this.isTimeToAttack()) {
					SoulBearEntity.this.setStanding(false);
					this.resetAttackCooldown();
				}

				if (this.getTicksUntilNextAttack() <= 10) {
					SoulBearEntity.this.setStanding(true);
					SoulBearEntity.this.playWarningSound();
				}
			} else {
				this.resetAttackCooldown();
				SoulBearEntity.this.setStanding(false);
			}

		}

		/**
		 * Reset the task's internal state. Called when this task is interrupted by
		 * another one
		 */
		public void stop() {
			SoulBearEntity.this.setStanding(false);
			super.stop();
		}

		protected double getAttackReachSqr(LivingEntity attackTarget) {
			return (double) (4.0F + attackTarget.getBbWidth());
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
			return !SoulBearEntity.this.isBaby() && !SoulBearEntity.this.isOnFire() ? false : super.canUse();
		}
	}

	class AvoidEntityGoal<T extends LivingEntity> extends net.minecraft.entity.ai.goal.AvoidEntityGoal<T> {
		private final SoulBearEntity bear;

		public AvoidEntityGoal(SoulBearEntity wolfIn, Class<T> entityClassToAvoidIn, float avoidDistanceIn, double farSpeedIn,
				double nearSpeedIn) {
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
			SoulBearEntity.this.setTarget((LivingEntity) null);
			super.start();
		}

		/**
		 * Keep ticking a continuous task that has already been started
		 */
		public void tick() {
			SoulBearEntity.this.setTarget((LivingEntity) null);
			super.tick();
		}
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
	
	@Override
	public boolean isPowered() {
		return true;
	}
}
