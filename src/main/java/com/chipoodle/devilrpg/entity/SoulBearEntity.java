package com.chipoodle.devilrpg.entity;

import java.util.Random;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.chipoodle.devilrpg.capability.minion.IBaseMinionCapability;
import com.chipoodle.devilrpg.capability.minion.PlayerMinionCapabilityProvider;
import com.chipoodle.devilrpg.capability.skill.IBaseSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityProvider;
import com.chipoodle.devilrpg.util.SkillEnum;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.AgeableEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntitySize;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.IChargeableMob;
import net.minecraft.entity.ILivingEntityData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.Pose;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.entity.ai.goal.FollowParentGoal;
import net.minecraft.entity.ai.goal.HurtByTargetGoal;
import net.minecraft.entity.ai.goal.LookAtGoal;
import net.minecraft.entity.ai.goal.LookRandomlyGoal;
import net.minecraft.entity.ai.goal.NearestAttackableTargetGoal;
import net.minecraft.entity.ai.goal.OwnerHurtByTargetGoal;
import net.minecraft.entity.ai.goal.OwnerHurtTargetGoal;
import net.minecraft.entity.ai.goal.RandomWalkingGoal;
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
import net.minecraft.util.DamageSource;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.Biomes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import net.minecraftforge.fml.network.FMLPlayMessages;
import net.minecraftforge.fml.network.NetworkHooks;

public class SoulBearEntity extends TameableEntity implements ISoulEntity,IChargeableMob,IRenderUtilities {
	private static final DataParameter<Boolean> IS_STANDING = EntityDataManager.createKey(SoulBearEntity.class,
			DataSerializers.BOOLEAN);
	private float clientSideStandAnimation0;
	private float clientSideStandAnimation;
	private int warningSoundTicks;
	
	private final int SALUD_INICIAL = 30;
	private int puntosAsignados = 0;
	private double saludMaxima = SALUD_INICIAL;
	private static double initialArmor;

	public SoulBearEntity(EntityType<? extends SoulBearEntity> type, World worldIn) {
		super(type, worldIn);
	}

	public AgeableEntity createChild(AgeableEntity ageable) {
		return EntityType.POLAR_BEAR.create(this.world);
	}

	/**
	 * Checks if the parameter is an item which this animal can be fed to breed it
	 * (wheat, carrots or seeds depending on the animal type)
	 */
	public boolean isBreedingItem(ItemStack stack) {
		return false;
	}

	protected void registerGoals() {
		super.registerGoals();
		this.goalSelector.addGoal(0, new SwimGoal(this));
		this.goalSelector.addGoal(1, new SoulBearEntity.MeleeAttackGoal());
		//this.goalSelector.addGoal(1, new SoulBearEntity.PanicGoal());
		this.goalSelector.addGoal(3,new SoulBearEntity.AvoidEntityGoal<VillagerEntity>(this, VillagerEntity.class, 24.0F, 1.5D, 1.5D));
		this.goalSelector.addGoal(3,new SoulBearEntity.AvoidEntityGoal<LlamaEntity>(this, LlamaEntity.class, 24.0F, 1.5D, 1.5D));
		this.goalSelector.addGoal(3,new SoulBearEntity.AvoidEntityGoal<TurtleEntity>(this, TurtleEntity.class, 24.0F, 1.5D, 1.5D));
		this.goalSelector.addGoal(3,new SoulBearEntity.AvoidEntityGoal<IronGolemEntity>(this, IronGolemEntity.class, 24.0F, 1.5D, 1.5D));
		this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.25D));
		this.goalSelector.addGoal(5, new RandomWalkingGoal(this, 1.0D));
		this.goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0D, 10.0F, 2.0F, false));
		this.goalSelector.addGoal(7, new LookAtGoal(this, PlayerEntity.class, 6.0F));
		this.goalSelector.addGoal(8, new LookRandomlyGoal(this));
		//this.targetSelector.addGoal(2, new SoulBearEntity.AttackPlayerGoal());
		//this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, MobEntity.class, 10, true, true,(Predicate<LivingEntity>) null));
		
		this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
		//this.targetSelector.addGoal(3, new SoulBearEntity.HurtByTargetGoal());
		this.targetSelector.addGoal(3, (new HurtByTargetGoal(this)).setCallsForHelp());
		this.targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(this, MobEntity.class, false));
		
	}

	protected void registerAttributes() {
		super.registerAttributes();
		this.getAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(30.0D);
		this.getAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(20.0D);
		this.getAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.25D);
		this.getAttributes().registerAttribute(SharedMonsterAttributes.ATTACK_DAMAGE);
		this.getAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(6.0D);
	}

	public void updateLevel(PlayerEntity owner) {
		setTamedBy(owner);
		LazyOptional<IBaseSkillCapability> skill = getOwner().getCapability(PlayerSkillCapabilityProvider.SKILL_CAP);
		if (skill != null && skill.isPresent()) {
			this.puntosAsignados = skill.map(x -> x.getSkillsPoints()).orElse(null).get(SkillEnum.SUMMON_SOUL_BEAR);
			saludMaxima = 5 * this.puntosAsignados  + SALUD_INICIAL;
			initialArmor = (1.0D * 0.309 * puntosAsignados) + 2;
		}

		this.getAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue((double) 0.4F);
		this.getAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(saludMaxima);
		this.getAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(initialArmor);
		this.getAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(16.0D);
		this.getAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue((1.5 * puntosAsignados) + 4); // 4-34
		setHealth((float) saludMaxima);
	}
	
	@Override
	public void readAdditional(CompoundNBT compound) {
		super.readAdditional(compound);
	}

	@Override
	public void writeAdditional(CompoundNBT compound) {
		super.writeAdditional(compound);
		compound.putString("OwnerUUID", "");
	}
	
	@Override
	public void livingTick() {
		super.livingTick();
		/*if (this.getOwnerId() == null || this.getOwner() == null || !this.getOwner().isAlive() || !this.isTamed())
			this.attackEntityFrom(new MinionDeathDamageSource(""), Integer.MAX_VALUE);*/
		addToLivingTick(this);
	}
	
	/**
	 * Returns whether this Entity is on the same team as the given Entity or they
	 * share the same owner.
	 */
	@Override
	public boolean isOnSameTeam(Entity entityIn) {
		boolean isOnSameTeam = super.isOnSameTeam(entityIn);
		return isOnSameTeam || isEntitySameOwnerAsThis(entityIn,this);
	}

	/**
	 * doesn't interact. It's magical!
	 */
	@Override
	public boolean processInteract(PlayerEntity player, Hand hand) {
		return true;
	}

	@Override
	public boolean shouldAttackEntity(LivingEntity target, LivingEntity owner) {
		return addToshouldAttackEntity(target,owner);
	}
	
	@Override
	public boolean attackEntityAsMob(Entity entityIn) {
		boolean flag = entityIn.attackEntityFrom(DamageSource.causeMobDamage(this),
				(float) ((int) this.getAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getValue()));
		if (flag) {
			this.applyEnchantments(this, entityIn);
		}

		return flag;
	}
	
	/**
	 * Sets the active target the Task system uses for tracking
	 */
	@Override
	public void setAttackTarget(@Nullable LivingEntity entitylivingbaseIn) {
		super.setAttackTarget(entitylivingbaseIn);
	}

	@Override
	public void remove() {
		super.remove();
	}

	/**
	 * Called when the mob's health reaches 0.
	 */
	@Override
	public void onDeath(DamageSource cause) {
		if (getOwner() != null) {
			LazyOptional<IBaseMinionCapability> minionCap = getOwner()
					.getCapability(PlayerMinionCapabilityProvider.MINION_CAP);
			if (!minionCap.isPresent())
				return;
			minionCap.ifPresent(x -> x.removeSoulBear((PlayerEntity) getOwner(), this));
		}
		//super.onDeath(cause);
		customOnDeath();
	}
	
	private void customOnDeath() {
		world.setEntityState(this, (byte) 3);
		this.dead = true;
		this.remove();
		customDeadParticles(this.world,this.rand,this);
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
	public IPacket<?> createSpawnPacket() {
		return NetworkHooks.getEntitySpawningPacket(this);
	}
	
	
	
	
	
	
	
	public static boolean func_223320_c(EntityType<SoulBearEntity> p_223320_0_, IWorld p_223320_1_, SpawnReason reason,
			BlockPos p_223320_3_, Random p_223320_4_) {
		Biome biome = p_223320_1_.getBiome(p_223320_3_);
		if (biome != Biomes.FROZEN_OCEAN && biome != Biomes.DEEP_FROZEN_OCEAN) {
			return func_223316_b(p_223320_0_, p_223320_1_, reason, p_223320_3_, p_223320_4_);
		} else {
			return p_223320_1_.getLightSubtracted(p_223320_3_, 0) > 8
					&& p_223320_1_.getBlockState(p_223320_3_.down()).getBlock() == Blocks.ICE;
		}
	}

	protected SoundEvent getAmbientSound() {
		return this.isChild() ? SoundEvents.ENTITY_POLAR_BEAR_AMBIENT_BABY : SoundEvents.ENTITY_POLAR_BEAR_AMBIENT;
	}

	protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
		return SoundEvents.ENTITY_POLAR_BEAR_HURT;
	}

	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_POLAR_BEAR_DEATH;
	}

	protected void playStepSound(BlockPos pos, BlockState blockIn) {
		this.playSound(SoundEvents.ENTITY_POLAR_BEAR_STEP, 0.15F, 1.0F);
	}

	protected void playWarningSound() {
		if (this.warningSoundTicks <= 0) {
			this.playSound(SoundEvents.ENTITY_POLAR_BEAR_WARNING, 1.0F, this.getSoundPitch());
			this.warningSoundTicks = 40;
		}

	}

	protected void registerData() {
		super.registerData();
		this.dataManager.register(IS_STANDING, false);
	}

	/**
	 * Called to update the entity's position/logic.
	 */
	public void tick() {
		super.tick();
		if (this.world.isRemote) {
			if (this.clientSideStandAnimation != this.clientSideStandAnimation0) {
				this.recalculateSize();
			}

			this.clientSideStandAnimation0 = this.clientSideStandAnimation;
			if (this.isStanding()) {
				this.clientSideStandAnimation = MathHelper.clamp(this.clientSideStandAnimation + 1.0F, 0.0F, 6.0F);
			} else {
				this.clientSideStandAnimation = MathHelper.clamp(this.clientSideStandAnimation - 1.0F, 0.0F, 6.0F);
			}
		}

		if (this.warningSoundTicks > 0) {
			--this.warningSoundTicks;
		}

	}

	public EntitySize getSize(Pose poseIn) {
		if (this.clientSideStandAnimation > 0.0F) {
			float f = this.clientSideStandAnimation / 6.0F;
			float f1 = 1.0F + f;
			return super.getSize(poseIn).scale(1.0F, f1);
		} else {
			return super.getSize(poseIn);
		}
	}

	

	public boolean isStanding() {
		return this.dataManager.get(IS_STANDING);
	}

	public void setStanding(boolean standing) {
		this.dataManager.set(IS_STANDING, standing);
	}

	@OnlyIn(Dist.CLIENT)
	public float getStandingAnimationScale(float p_189795_1_) {
		return MathHelper.lerp(p_189795_1_, this.clientSideStandAnimation0, this.clientSideStandAnimation) / 6.0F;
	}

	protected float getWaterSlowDown() {
		return 0.98F;
	}

	public ILivingEntityData onInitialSpawn(IWorld worldIn, DifficultyInstance difficultyIn, SpawnReason reason,
			@Nullable ILivingEntityData spawnDataIn, @Nullable CompoundNBT dataTag) {
		if (spawnDataIn == null) {
			spawnDataIn = new AgeableEntity.AgeableData();
			((AgeableEntity.AgeableData) spawnDataIn).func_226258_a_(1.0F);
		}

		return super.onInitialSpawn(worldIn, difficultyIn, reason, spawnDataIn, dataTag);
	}

	class AttackPlayerGoal extends NearestAttackableTargetGoal<PlayerEntity> {
		public AttackPlayerGoal() {
			super(SoulBearEntity.this, PlayerEntity.class, 20, true, true, (Predicate<LivingEntity>) null);
		}

		/**
		 * Returns whether execution should begin. You can also read and cache any state
		 * necessary for execution in this method as well.
		 */
		public boolean shouldExecute() {
			if (SoulBearEntity.this.isChild()) {
				return false;
			} else {
				if (super.shouldExecute()) {
					for (SoulBearEntity polarbearentity : SoulBearEntity.this.world.getEntitiesWithinAABB(
							SoulBearEntity.class, SoulBearEntity.this.getBoundingBox().grow(8.0D, 4.0D, 8.0D))) {
						if (polarbearentity.isChild()) {
							return true;
						}
					}
				}

				return false;
			}
		}

		protected double getTargetDistance() {
			return super.getTargetDistance() * 0.5D;
		}
	}

	class MeleeAttackGoal extends net.minecraft.entity.ai.goal.MeleeAttackGoal {
		public MeleeAttackGoal() {
			super(SoulBearEntity.this, 1.25D, true);
		}

		protected void checkAndPerformAttack(LivingEntity enemy, double distToEnemySqr) {
			double d0 = this.getAttackReachSqr(enemy);
			if (distToEnemySqr <= d0 && this.attackTick <= 0) {
				this.attackTick = 20;
				this.attacker.attackEntityAsMob(enemy);
				SoulBearEntity.this.setStanding(false);
			} else if (distToEnemySqr <= d0 * 2.0D) {
				if (this.attackTick <= 0) {
					SoulBearEntity.this.setStanding(false);
					this.attackTick = 20;
				}

				if (this.attackTick <= 10) {
					SoulBearEntity.this.setStanding(true);
					SoulBearEntity.this.playWarningSound();
				}
			} else {
				this.attackTick = 20;
				SoulBearEntity.this.setStanding(false);
			}

		}

		/**
		 * Reset the task's internal state. Called when this task is interrupted by
		 * another one
		 */
		public void resetTask() {
			SoulBearEntity.this.setStanding(false);
			super.resetTask();
		}

		protected double getAttackReachSqr(LivingEntity attackTarget) {
			return (double) (4.0F + attackTarget.getWidth());
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
			return !SoulBearEntity.this.isChild() && !SoulBearEntity.this.isBurning() ? false : super.shouldExecute();
		}
	}

	class AvoidEntityGoal<T extends LivingEntity> extends net.minecraft.entity.ai.goal.AvoidEntityGoal<T> {
		private final SoulBearEntity bear;

		public AvoidEntityGoal(SoulBearEntity wolfIn, Class<T> p_i47251_3_, float p_i47251_4_, double p_i47251_5_,
				double p_i47251_7_) {
			super(wolfIn, p_i47251_3_, p_i47251_4_, p_i47251_5_, p_i47251_7_);
			this.bear = wolfIn;
		}

		/**
		 * Returns whether execution should begin. You can also read and cache any state
		 * necessary for execution in this method as well.
		 */
		public boolean shouldExecute() {
			if (super.shouldExecute() && this.avoidTarget instanceof LlamaEntity) {
				return this.bear.isTamed() && this.avoidLlama((LlamaEntity) this.avoidTarget);
			}
			if (super.shouldExecute() && this.avoidTarget instanceof TurtleEntity) {
				return this.bear.isTamed() && this.avoidTurtle((TurtleEntity) this.avoidTarget);
			}
			if (super.shouldExecute() && this.avoidTarget instanceof VillagerEntity) {
				return this.bear.isTamed() && this.avoidVillager((VillagerEntity) this.avoidTarget);
			}
			if (super.shouldExecute() && this.avoidTarget instanceof IronGolemEntity) {
				return this.bear.isTamed() && this.avoidIronGolem((IronGolemEntity) this.avoidTarget);
			}
			return false;
		}

		private boolean avoidLlama(LlamaEntity p_190854_1_) {
			return p_190854_1_.getStrength() >= SoulBearEntity.this.rand.nextInt(5);
		}

		private boolean avoidTurtle(TurtleEntity p_190854_1_) {
			return true;
		}

		private boolean avoidVillager(VillagerEntity p_190854_1_) {
			return true;
		}

		private boolean avoidIronGolem(IronGolemEntity p_190854_1_) {
			return true;
		}

		/**
		 * Execute a one shot task or start executing a continuous task
		 */
		public void startExecuting() {
			SoulBearEntity.this.setAttackTarget((LivingEntity) null);
			super.startExecuting();
		}

		/**
		 * Keep ticking a continuous task that has already been started
		 */
		public void tick() {
			SoulBearEntity.this.setAttackTarget((LivingEntity) null);
			super.tick();
		}
	}

	@Override
	public boolean func_225509_J__() {
		return true;
	}
	
	/**
	 * Get the experience points the entity currently has.
	 */
	protected int getExperiencePoints(PlayerEntity player) {
		/*if (player.equals(getOwner()))
			return 0;
		return 1 + this.world.rand.nextInt(3);*/
		return 0;
	}
}
