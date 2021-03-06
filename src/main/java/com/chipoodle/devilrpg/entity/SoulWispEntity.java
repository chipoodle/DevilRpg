package com.chipoodle.devilrpg.entity;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.minion.IBaseMinionCapability;
import com.chipoodle.devilrpg.capability.minion.PlayerMinionCapabilityProvider;
import com.chipoodle.devilrpg.capability.skill.IBaseSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityProvider;
import com.chipoodle.devilrpg.client.render.IRenderUtilities;
import com.chipoodle.devilrpg.init.ModEntityTypes;
import com.chipoodle.devilrpg.util.SkillEnum;

import net.minecraft.block.BlockState;
import net.minecraft.entity.AgeableEntity;
import net.minecraft.entity.CreatureAttribute;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntitySize;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.IAngerable;
import net.minecraft.entity.IChargeableMob;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.Pose;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.controller.FlyingMovementController;
import net.minecraft.entity.ai.controller.LookController;
import net.minecraft.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.PanicGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.entity.passive.IFlyingAnimal;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.IPacket;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.particles.IParticleData;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.pathfinding.FlyingPathNavigator;
import net.minecraft.pathfinding.PathNavigator;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.Tag;
import net.minecraft.util.DamageSource;
import net.minecraft.util.RangedInteger;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.TickRangeConverter;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.IWorldReader;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import net.minecraftforge.fml.network.FMLPlayMessages;
import net.minecraftforge.fml.network.NetworkHooks;

public class SoulWispEntity extends TameableEntity
		implements IFlyingAnimal, ISoulEntity, IChargeableMob, IRenderUtilities, IAngerable {
	private static final DataParameter<Integer> ANGER_TIME = EntityDataManager.defineId(SoulWispEntity.class,DataSerializers.INT);
	private static final RangedInteger PERSISTENT_ANGER_TIME = TickRangeConverter.rangeOfSeconds(20, 39);
	private UUID lastHurtBy;
	private final int SALUD_INICIAL = 8;
	private int puntosAsignados = 0;
	private double saludMaxima = SALUD_INICIAL;
	protected Effect efectoPrimario;
	protected Effect efectoSecundario;
	protected boolean esBeneficioso;

	protected static final double DISTANCIA_EFECTO = 20;
	protected static final int DURATION_TICKS = 120;

	public SoulWispEntity(EntityType<? extends SoulWispEntity> type, World worldIn) {
		super(type, worldIn);
		this.moveControl = new FlyingMovementController(this, 20, true);
		this.lookControl = new SoulWispEntity.BeeLookController(this);
		this.setPathfindingMalus(PathNodeType.WATER, -1.0F);
		// this.setPathPriority(PathNodeType.COCOA, -1.0F);
		this.setPathfindingMalus(PathNodeType.FENCE, -1.0F);
	}

	@Override
	protected void defineSynchedData() {
		super.defineSynchedData();
	}

	@Override
	public float getWalkTargetValue(BlockPos pos, IWorldReader worldIn) {
		return worldIn.getBlockState(pos).isAir() ? 10.0F : 0.0F;
	}

	@Override
	protected void registerGoals() {

		this.goalSelector.addGoal(0, new PanicGoal(this, 1.25D));
		this.goalSelector.addGoal(0, new SwimGoal(this));
		this.goalSelector.addGoal(2, new FollowOwnerGoal(this, 1.0D, 3.0F, 7.0F, false));
		this.goalSelector.addGoal(2, new WaterAvoidingRandomFlyingGoal(this, 1.0D));
		//// this.goalSelector.addGoal(3, new FollowMobGoal(this, 1.0D, 3.0F,7.0F));
		//// this.goalSelector.addGoal(5, new FollowParentGoal(this, 1.25D));
		this.goalSelector.addGoal(8, new SoulWispEntity.WanderGoal());
		this.goalSelector.addGoal(9, new SwimGoal(this));

	}

	public static AttributeModifierMap.MutableAttribute setAttributes() {
		return MobEntity.createMobAttributes()
				.add(Attributes.MOVEMENT_SPEED, (double) 0.3F)
				.add(Attributes.FLYING_SPEED, 0.9F)
				.add(Attributes.MAX_HEALTH, 8.0D)
				.add(Attributes.FOLLOW_RANGE, 48.0D)
				.add(Attributes.ATTACK_DAMAGE, 2.0D)
				.add(Attributes.ARMOR, 0.15D);
	}

	public void updateLevel(PlayerEntity owner, Effect efectoPrimario, Effect efectoSecundario, SkillEnum tipoWisp, boolean esBeneficioso) {
		tame(owner);
		LazyOptional<IBaseSkillCapability> skill = getOwner().getCapability(PlayerSkillCapabilityProvider.SKILL_CAP);
		this.efectoPrimario = efectoPrimario;
		this.efectoSecundario = efectoSecundario;
		this.esBeneficioso = esBeneficioso;
		if (skill != null && skill.isPresent()) {
			this.puntosAsignados = skill.map(x -> x.getSkillsPoints()).orElse(null).get(tipoWisp);
			saludMaxima = 0.6 * this.puntosAsignados + SALUD_INICIAL;
			DevilRpg.LOGGER.debug("SoulWispEntity.updateLevel.saludMaxima{}",saludMaxima);
		}

		this.getAttribute(Attributes.FLYING_SPEED).setBaseValue((double) 0.9F);
		this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(saludMaxima);
		this.getAttribute(Attributes.ARMOR).setBaseValue(0.15D);
		this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue((double) 0.3F);
		this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(2.0D);
		this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(48.0D);
		setHealth((float) saludMaxima);
	}

	@Override
	public void addAdditionalSaveData(CompoundNBT compound) {
		super.addAdditionalSaveData(compound);
		compound.putString("OwnerUUID", "");
		compound.putString("Owner", "");
	}

	/**
	 * (abstract) Protected helper method to read subclass entity data from NBT.
	 */
	@Override
	public void readAdditionalSaveData(CompoundNBT compound) {
		super.readAdditionalSaveData(compound);
	}

	@Override
	public boolean doHurtTarget(Entity entityIn) {
		return false;
	}

	/**
	 * Called to update the entity's position/logic.
	 */
	@Override
	public void tick() {
		super.tick();
		if (this.random.nextFloat() < 0.05F) {
			for (int i = 0; i < this.random.nextInt(2) + 1; ++i) {
				this.addParticle(this.level, this.getX() - (double) 0.3F, this.getX() + (double) 0.3F,
						this.getZ() - (double) 0.3F, this.getZ() + (double) 0.3F, this.getY(0.5D),
						ParticleTypes.CRIMSON_SPORE);
			}
		}
	}

	private void addParticle(World worldIn, double p_226397_2_, double p_226397_4_, double p_226397_6_,
			double p_226397_8_, double posY, IParticleData particleData) {
		worldIn.addParticle(particleData, MathHelper.lerp(worldIn.random.nextDouble(), p_226397_2_, p_226397_4_),
				posY, MathHelper.lerp(worldIn.random.nextDouble(), p_226397_6_, p_226397_8_), 0.0D, 0.0D,
				0.0D);
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
		addToLivingTick(this);
	}

	/**
	 * Returns new PathNavigateGround instance
	 */
	@Override
	protected PathNavigator createNavigation(World worldIn) {
		FlyingPathNavigator flyingpathnavigator = new FlyingPathNavigator(this, worldIn) {
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
		return stack.getItem().is(ItemTags.FLOWERS);
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

	public SoulWispEntity getBreedOffspring(AgeableEntity ageable) {
		return ModEntityTypes.WISP.get().create(this.level);
	}

	@Override
	protected float getStandingEyeHeight(Pose poseIn, EntitySize sizeIn) {
		return this.isBaby() ? sizeIn.height * 0.5F : sizeIn.height * 0.5F;
	}

	@Override
	public boolean causeFallDamage(float distance, float damageMultiplier) {
		return false;
	}

	@Override
	protected void checkFallDamage(double y, boolean onGroundIn, BlockState state, BlockPos pos) {
	}

	protected boolean makeFlySound() {
		return true;
	}

	/**
	 * Called when the entity is attacked.
	 */
	public boolean hurt(DamageSource source, float amount) {
		return super.hurt(source, amount);
	}

	@Override
	public CreatureAttribute getMobType() {
		return CreatureAttribute.ARTHROPOD;
	}

	protected void jumpInLiquid(Tag<Fluid> fluidTag) {
		this.setDeltaMovement(this.getDeltaMovement().add(0.0D, 0.01D, 0.0D));
	}

	class BeeLookController extends LookController {
		BeeLookController(MobEntity beeIn) {
			super(beeIn);
		}
		
		public void tick() {
			super.tick();
		}
	}

	abstract class PassiveGoal extends Goal {
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
			return SoulWispEntity.this.navigation.isDone() && SoulWispEntity.this.random.nextInt(10) == 0;
		}

		/**
		 * Returns whether an in-progress EntityAIBase should continue executing
		 */
		public boolean canContinueToUse() {
			return SoulWispEntity.this.navigation.isInProgress();
		}

		/**
		 * Execute a one shot task or start executing a continuous task
		 */
		public void start() {
			Vector3d vec3d = this.findPos();
			if (vec3d != null) {
				SoulWispEntity.this.navigation
						.moveTo(SoulWispEntity.this.navigation.createPath(new BlockPos(vec3d), 1), 1.0D);
			}

		}

		@Nullable
		private Vector3d findPos() {
			Vector3d vector3d;
			vector3d = SoulWispEntity.this.getViewVector(0.0F);
			// int i = 8;
			Vector3d vector3d2 = RandomPositionGenerator.getAboveLandPos(SoulWispEntity.this, 8, 7, vector3d,
					((float) Math.PI / 2F), 2, 1);
			return vector3d2 != null ? vector3d2
					: RandomPositionGenerator.getAirPos(SoulWispEntity.this, 8, 4, -2, vector3d,
							(double) ((float) Math.PI / 2F));
		}
	}

	/**
	 * Returns whether this Entity is on the same team as the given Entity or they
	 * share the same owner.
	 */
	@Override
	public boolean isAlliedTo(Entity entityIn) {
		boolean isOnSameTeam = super.isAlliedTo(entityIn);
		boolean isSameOwner = false;
		if (entityIn instanceof TameableEntity && ((TameableEntity) entityIn).getOwner() != null)
			isSameOwner = ((TameableEntity) entityIn).getOwner().equals(this.getOwner());
		return isOnSameTeam || isSameOwner;
	}

	private void addEffectsToPlayers(int niveles, Effect primaryEffect, Effect secondaryEffect, boolean isBeneficial) {
		if (niveles >= 0 && !this.level.isClientSide && primaryEffect != null) {
			// rango entre 0 - 4 el tipo de boost health
			int potenciaPocion = getPotenciaPocion(niveles);
			// System.out.println("Level of effect" + i);

			double k = this.position().x();
			double l = this.position().y();
			double i1 = this.position().z();
			AxisAlignedBB axisalignedbb = (new AxisAlignedBB(k, l, i1, (k + 1),
					(l + 1), (i1 + 1))).inflate(DISTANCIA_EFECTO).expandTowards(0.0D,
							(double) this.level.getHeight(), 0.0D);

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

	private void applyPrimaryEffect(Effect primaryEffect, int amplifierIn, List<LivingEntity> alliesList) {
		for (LivingEntity entity : alliesList) {
			EffectInstance pri = new EffectInstance(primaryEffect, DURATION_TICKS, amplifierIn, true, true);
			EffectInstance active = entity.getEffect(primaryEffect);
			if (!(active == null || pri.getAmplifier() > active.getAmplifier())) {
				active.update(pri);
			}
			entity.addEffect(pri);
		}
	}

	private void applySecondaryEffect(Effect secondaryEffect, List<LivingEntity> alliesList) {
		for (LivingEntity entity : alliesList) {
			EffectInstance sec = new EffectInstance(secondaryEffect, DURATION_TICKS, 0, true, true);
			EffectInstance active = entity.getEffect(secondaryEffect);
			if (!(active == null || sec.getAmplifier() > active.getAmplifier())) {
				active.update(sec);
			}
			entity.addEffect(sec);
		}
	}

	private List<LivingEntity> getAlliesListWithinAABBRange(AxisAlignedBB axisalignedbb) {
		List<LivingEntity> list = this.level.<LivingEntity>getEntitiesOfClass(LivingEntity.class, axisalignedbb)
				.stream().filter(x -> x.isAlliedTo(this.getOwner()) || x.equals(getOwner()))
				.collect(Collectors.toList());
		return list;
	}

	private List<LivingEntity> getEnemiesListWithinAABBRange(AxisAlignedBB axisalignedbb) {
		List<LivingEntity> list = this.level.<MobEntity>getEntitiesOfClass(MobEntity.class, axisalignedbb).stream()
				.filter(x -> !x.isAlliedTo(this.getOwner())).map(x -> (LivingEntity) x).collect(Collectors.toList());
		return list;
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
			minionCap.ifPresent(x -> x.removeWisp((PlayerEntity) getOwner(), this));
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

	public boolean isEsBeneficioso() {
		return esBeneficioso;
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

	/**
	 * Get the experience points the entity currently has.
	 */
	@Override
	protected int getExperienceReward(PlayerEntity player) {
		/*
		 * if (player.equals(getOwner())) return 0; return 1 +
		 * this.world.rand.nextInt(3);
		 */
		return 0;
	}

	@Override
	public int getRemainingPersistentAngerTime() {
		return this.entityData.get(ANGER_TIME);
	}

	@Override
	public void setRemainingPersistentAngerTime(int time) {
		this.entityData.set(ANGER_TIME, time);
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
		this.setRemainingPersistentAngerTime(PERSISTENT_ANGER_TIME.randomValue(this.random));
	}

	@Override
	public boolean isPowered() {
		return true;
	}

	@Override
	public SoulWispEntity getBreedOffspring(ServerWorld world, AgeableEntity mate) {
		return ModEntityTypes.WISP.get().create(world);
	}

}
