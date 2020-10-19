package com.chipoodle.devilrpg.entity;

import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.chipoodle.devilrpg.capability.minion.IBaseMinionCapability;
import com.chipoodle.devilrpg.capability.minion.PlayerMinionCapabilityProvider;
import com.chipoodle.devilrpg.capability.skill.IBaseSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityProvider;
import com.chipoodle.devilrpg.init.ModEntityTypes;
import com.chipoodle.devilrpg.skillsystem.MinionDeathDamageSource;
import com.chipoodle.devilrpg.util.SkillEnum;

import net.minecraft.block.BlockState;
import net.minecraft.entity.AgeableEntity;
import net.minecraft.entity.CreatureAttribute;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntitySize;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.IChargeableMob;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.Pose;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.entity.ai.controller.FlyingMovementController;
import net.minecraft.entity.ai.controller.LookController;
import net.minecraft.entity.ai.goal.FollowMobGoal;
import net.minecraft.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.entity.ai.goal.FollowParentGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.PanicGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.entity.passive.IFlyingAnimal;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.IPacket;
import net.minecraft.particles.IParticleData;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.pathfinding.FlyingPathNavigator;
import net.minecraft.pathfinding.PathNavigator;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.Tag;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IWorldReader;
import net.minecraft.world.World;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import net.minecraftforge.fml.network.FMLPlayMessages;
import net.minecraftforge.fml.network.NetworkHooks;

public class SoulWispEntity extends TameableEntity implements IFlyingAnimal, ISoulEntity,IChargeableMob {

	@Nullable
	private BlockPos field_226368_bH_ = null;
	@Nullable
	private BlockPos field_226369_bI_ = null;

	private final int SALUD_INICIAL = 8;
	private int puntosAsignados = 0;
	private double saludMaxima = SALUD_INICIAL;
	protected Effect efectoPrimario;
	protected Effect efectoSecundario;
	protected boolean esBeneficioso;

	protected static final double DISTANCIA_EFECTO = 20;
	protected static final int DIVISOR_NIVEL_PARA_POTENCIA_EFECTO = 5;
	protected static final int DURATION_TICKS = 120;

	private ResourceLocation wispPortrait;

	public SoulWispEntity(EntityType<? extends SoulWispEntity> p_i225714_1_, World p_i225714_2_) {
		super(p_i225714_1_, p_i225714_2_);
		this.moveController = new FlyingMovementController(this, 20, true);
		this.lookController = new SoulWispEntity.BeeLookController(this);
		this.setPathPriority(PathNodeType.WATER, -1.0F);
		this.setPathPriority(PathNodeType.COCOA, -1.0F);
		this.setPathPriority(PathNodeType.FENCE, -1.0F);
	}

	protected void registerData() {
		super.registerData();
	}

	public float getBlockPathWeight(BlockPos pos, IWorldReader worldIn) {
		return worldIn.getBlockState(pos).isAir(worldIn, pos) ? 10.0F : 0.0F;
		//return worldIn.getBlockState(pos).isAir() ? 10.0F : 0.0F;
	}

	protected void registerGoals() {
		//this.goalSelector.addGoal(0, new PanicGoal(this, 1.25D));
		//this.goalSelector.addGoal(0, new SwimGoal(this));
		//this.goalSelector.addGoal(2, new FollowOwnerGoal(this, 1.0D, 10.0F, 2.0F, false));
		//this.goalSelector.addGoal(2, new WaterAvoidingRandomFlyingGoal(this, 1.0D));
			//this.goalSelector.addGoal(3, new FollowMobGoal(this, 1.0D, 3.0F, 7.0F));
		//this.goalSelector.addGoal(5, new FollowParentGoal(this, 1.25D));
		//this.goalSelector.addGoal(8, new SoulWispEntity.WanderGoal());
		//this.goalSelector.addGoal(9, new SwimGoal(this));
	}

	public void writeAdditional(CompoundNBT compound) {
		super.writeAdditional(compound);
		compound.putString("OwnerUUID", "");
	}

	/**
	 * (abstract) Protected helper method to read subclass entity data from NBT.
	 */
	public void readAdditional(CompoundNBT compound) {
		this.field_226369_bI_ = null;
		super.readAdditional(compound);
	}

	public boolean attackEntityAsMob(Entity entityIn) {
		return false;
	}

	/**
	 * Called to update the entity's position/logic.
	 */
	public void tick() {
		super.tick();
		if (this.rand.nextFloat() < 0.05F) {
			for (int i = 0; i < this.rand.nextInt(2) + 1; ++i) {
				this.func_226397_a_(this.world, this.getPosX() - (double) 0.3F, this.getPosX() + (double) 0.3F,
						this.getPosZ() - (double) 0.3F, this.getPosZ() + (double) 0.3F, this.getPosYHeight(0.5D),
						ParticleTypes.CRIT);
			}
		}
	}

	private void func_226397_a_(World p_226397_1_, double p_226397_2_, double p_226397_4_, double p_226397_6_,
			double p_226397_8_, double p_226397_10_, IParticleData p_226397_12_) {
		p_226397_1_.addParticle(p_226397_12_, MathHelper.lerp(p_226397_1_.rand.nextDouble(), p_226397_2_, p_226397_4_),
				p_226397_10_, MathHelper.lerp(p_226397_1_.rand.nextDouble(), p_226397_6_, p_226397_8_), 0.0D, 0.0D,
				0.0D);
	}

	public boolean func_226409_eA_() {
		return this.field_226369_bI_ != null;
	}

	@Nullable
	public BlockPos func_226410_eB_() {
		return this.field_226369_bI_;
	}

	protected void sendDebugPackets() {
		super.sendDebugPackets();
		// DebugPacketSender.func_229749_a_(this);
	}

	/**
	 * Called frequently so the entity can update its state every tick as required.
	 * For example, zombies and skeletons use this to react to sunlight and start to
	 * burn.
	 */
	public void livingTick() {
		super.livingTick();
		if (!this.world.isRemote) {
			if (this.ticksExisted % 20 == 0 && !this.func_226422_eP_()) {
				this.field_226369_bI_ = null;
			}

			if (this.getOwnerId() == null || this.getOwner() == null || !this.getOwner().isAlive() || !this.isTamed())
				this.attackEntityFrom(new MinionDeathDamageSource(""), Integer.MAX_VALUE);

			if (this.world.getGameTime() % 80L == 0L && efectoPrimario != null && efectoSecundario != null) {
				this.addEffectsToPlayers(puntosAsignados, efectoPrimario, efectoSecundario, esBeneficioso);
			}
			addToLivingTick(this);
		}

	}

	private boolean func_226422_eP_() {
		if (!this.func_226409_eA_()) {
			return false;
		} else {
			TileEntity tileentity = this.world.getTileEntity(this.field_226369_bI_);
			return tileentity != null && tileentity.getType() == TileEntityType.field_226985_G_;
		}
	}

	protected void registerAttributes() {
		super.registerAttributes();
		this.getAttributes().registerAttribute(SharedMonsterAttributes.FLYING_SPEED);
		this.getAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(10.0D);
		this.getAttribute(SharedMonsterAttributes.FLYING_SPEED).setBaseValue((double) 0.6F);
		this.getAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue((double) 0.3F);
		this.getAttributes().registerAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(2.0D);
		this.getAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(48.0D);
	}

	/**
	 * Returns new PathNavigateGround instance
	 */
	protected PathNavigator createNavigator(World worldIn) {
		FlyingPathNavigator flyingpathnavigator = new FlyingPathNavigator(this, worldIn) {
			public boolean canEntityStandOnPos(BlockPos pos) {
				return !this.world.getBlockState(pos).isAir(worldIn, pos);
				// return !this.world.getBlockState(pos.down()).isAir();
			}

			public void tick() {
				super.tick();
			}
		};
		flyingpathnavigator.setCanOpenDoors(true);
		flyingpathnavigator.setCanSwim(true);
		flyingpathnavigator.setCanEnterDoors(true);
		return flyingpathnavigator;
	}

	/**
	 * Checks if the parameter is an item which this animal can be fed to breed it
	 * (wheat, carrots or seeds depending on the animal type)
	 */
	public boolean isBreedingItem(ItemStack stack) {
		return stack.getItem().isIn(ItemTags.field_226159_I_);
	}

	protected void playStepSound(BlockPos pos, BlockState blockIn) {
	}

	protected SoundEvent getAmbientSound() {
		return null;
	}

	protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
		return SoundEvents.field_226125_Z_;
	}

	protected SoundEvent getDeathSound() {
		return SoundEvents.field_226124_Y_;
	}

	/**
	 * Returns the volume for the sounds this mob makes.
	 */
	protected float getSoundVolume() {
		return 0.4F;
	}

	public SoulWispEntity createChild(AgeableEntity ageable) {
		return ModEntityTypes.WISP.get().create(this.world);
	}

	protected float getStandingEyeHeight(Pose poseIn, EntitySize sizeIn) {
		return this.isChild() ? sizeIn.height * 0.5F : sizeIn.height * 0.5F;
	}

	public boolean onLivingFall(float distance, float damageMultiplier) {
		return false;
	}

	protected void updateFallState(double y, boolean onGroundIn, BlockState state, BlockPos pos) {
	}

	protected boolean makeFlySound() {
		return true;
	}

	/**
	 * Called when the entity is attacked.
	 */
	public boolean attackEntityFrom(DamageSource source, float amount) {
		return super.attackEntityFrom(source, amount);
	}

	public CreatureAttribute getCreatureAttribute() {
		return CreatureAttribute.ARTHROPOD;
	}

	protected void handleFluidJump(Tag<Fluid> fluidTag) {
		this.setMotion(this.getMotion().add(0.0D, 0.01D, 0.0D));
	}

	private boolean func_226401_b_(BlockPos p_226401_1_, int p_226401_2_) {
		return p_226401_1_.withinDistance(new BlockPos(this), (double) p_226401_2_);
	}

	class BeeLookController extends LookController {
		BeeLookController(MobEntity p_i225729_2_) {
			super(p_i225729_2_);
		}

		/**
		 * Updates look
		 */
		public void tick() {
			super.tick();
		}
	}

	abstract class PassiveGoal extends Goal {
		private PassiveGoal() {
		}

		public abstract boolean func_225506_g_();

		public abstract boolean func_225507_h_();

		/**
		 * Returns whether execution should begin. You can also read and cache any state
		 * necessary for execution in this method as well.
		 */
		public boolean shouldExecute() {
			return true;// this.func_225506_g_() && !WispEntity.this.func_226427_ez_();
		}

		/**
		 * Returns whether an in-progress EntityAIBase should continue executing
		 */
		public boolean shouldContinueExecuting() {
			return true;// this.func_225507_h_() && !WispEntity.this.func_226427_ez_();
		}
	}

	class WanderGoal extends Goal {
		WanderGoal() {
			this.setMutexFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		/**
		 * Returns whether execution should begin. You can also read and cache any state
		 * necessary for execution in this method as well.
		 */
		public boolean shouldExecute() {
			return SoulWispEntity.this.navigator.noPath() && SoulWispEntity.this.rand.nextInt(10) == 0;
		}

		/**
		 * Returns whether an in-progress EntityAIBase should continue executing
		 */
		public boolean shouldContinueExecuting() {
			return SoulWispEntity.this.navigator.func_226337_n_();
		}

		/**
		 * Execute a one shot task or start executing a continuous task
		 */
		public void startExecuting() {
			Vec3d vec3d = this.func_226509_g_();
			if (vec3d != null) {
				SoulWispEntity.this.navigator.setPath(SoulWispEntity.this.navigator.getPathToPos(new BlockPos(vec3d), 1), 1.0D);
			}

		}

		@Nullable
		private Vec3d func_226509_g_() {
			Vec3d vec3d;
			if (SoulWispEntity.this.func_226422_eP_()
					&& !SoulWispEntity.this.func_226401_b_(SoulWispEntity.this.field_226369_bI_, 40)) {
				Vec3d vec3d1 = new Vec3d(SoulWispEntity.this.field_226369_bI_);
				vec3d = vec3d1.subtract(SoulWispEntity.this.getPositionVec()).normalize();
			} else {
				vec3d = SoulWispEntity.this.getLook(0.0F);
			}

			int i = 8;
			Vec3d vec3d2 = RandomPositionGenerator.func_226340_a_(SoulWispEntity.this, 8, 7, vec3d, ((float) Math.PI / 2F),
					2, 1);
			return vec3d2 != null ? vec3d2
					: RandomPositionGenerator.func_226338_a_(SoulWispEntity.this, 8, 4, -2, vec3d,
							(double) ((float) Math.PI / 2F));
		}
	}

	public void updateLevel(PlayerEntity owner, Effect efectoPrimario, Effect efectoSecundario, SkillEnum tipoWisp,
			boolean esBeneficioso) {
		setTamedBy(owner);
		LazyOptional<IBaseSkillCapability> skill = getOwner().getCapability(PlayerSkillCapabilityProvider.SKILL_CAP);
		this.efectoPrimario = efectoPrimario;
		this.efectoSecundario = efectoSecundario;
		this.esBeneficioso = esBeneficioso;
		if (skill != null && skill.isPresent()) {
			this.puntosAsignados = skill.map(x -> x.getSkillsPoints()).orElse(null).get(tipoWisp);
			saludMaxima = 0.6 * this.puntosAsignados + SALUD_INICIAL;
		}

		this.getAttribute(SharedMonsterAttributes.FLYING_SPEED).setBaseValue((double) 0.9F);
		this.getAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(saludMaxima);
		this.getAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(0.15D);
		setHealth((float) saludMaxima);
	}

	protected int getPotenciaPocion(int niveles) {
		return (int) Math.ceil(niveles / (DIVISOR_NIVEL_PARA_POTENCIA_EFECTO));
	}

	/**
	 * Returns whether this Entity is on the same team as the given Entity or they
	 * share the same owner.
	 */
	@Override
	public boolean isOnSameTeam(Entity entityIn) {
		boolean isOnSameTeam = super.isOnSameTeam(entityIn);
		boolean isSameOwner = false;
		if (entityIn instanceof TameableEntity && ((TameableEntity) entityIn).getOwner() != null)
			isSameOwner = ((TameableEntity) entityIn).getOwner().equals(this.getOwner());
		return isOnSameTeam || isSameOwner;
	}

	private void addEffectsToPlayers(int niveles, Effect primaryEffect, Effect secondaryEffect, boolean isBeneficial) {
		if (niveles >= 0 && !this.world.isRemote && primaryEffect != null) {
			// rango entre 0 - 4 el tipo de boost health
			int potenciaPocion = getPotenciaPocion(niveles);
			// System.out.println("Level of effect" + i);

			int k = this.getPosition().getX();
			int l = this.getPosition().getY();
			int i1 = this.getPosition().getZ();
			AxisAlignedBB axisalignedbb = (new AxisAlignedBB((double) k, (double) l, (double) i1, (double) (k + 1),
					(double) (l + 1), (double) (i1 + 1))).grow(DISTANCIA_EFECTO).expand(0.0D,
							(double) this.world.getHeight(), 0.0D);

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
			EffectInstance pri = new EffectInstance(primaryEffect, DURATION_TICKS, amplifierIn, false, true);
			EffectInstance active = entity.getActivePotionEffect(primaryEffect);
			if (active == null || pri.getAmplifier() > active.getAmplifier()) {
				entity.addPotionEffect(pri);
			} else {
				active.combine(pri);
			}
		}
	}
	
	private void applySecondaryEffect(Effect secondaryEffect, List<LivingEntity> alliesList) {
		for (LivingEntity entity : alliesList) {
			EffectInstance sec = new EffectInstance(secondaryEffect, DURATION_TICKS, 0, false, true);
			EffectInstance active = entity.getActivePotionEffect(secondaryEffect);
			if (active == null || sec.getAmplifier() > active.getAmplifier()) {
				entity.addPotionEffect(sec);
			} else {
				active.combine(sec);
			}
		}
	}

	private List<LivingEntity> getAlliesListWithinAABBRange(AxisAlignedBB axisalignedbb) {
		List<LivingEntity> list = this.world.<LivingEntity>getEntitiesWithinAABB(LivingEntity.class, axisalignedbb)
				.stream().filter(x -> x.isOnSameTeam(this.getOwner()) || x.equals(getOwner()))
				.collect(Collectors.toList());
		return list;
	}

	private List<LivingEntity> getEnemiesListWithinAABBRange(AxisAlignedBB axisalignedbb) {
		List<LivingEntity> list = this.world.<MobEntity>getEntitiesWithinAABB(MobEntity.class, axisalignedbb).stream()
				.filter(x -> !x.isOnSameTeam(this.getOwner())).map(x -> (LivingEntity) x).collect(Collectors.toList());
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
	public void onDeath(DamageSource cause) {
		if (getOwner() != null) {
			LazyOptional<IBaseMinionCapability> minionCap = getOwner()
					.getCapability(PlayerMinionCapabilityProvider.MINION_CAP);
			if (!minionCap.isPresent())
				return;
			minionCap.ifPresent(x -> x.removeWisp((PlayerEntity) getOwner(), this));
		}
		super.onDeath(cause);
	}

	public boolean isEsBeneficioso() {
		return esBeneficioso;
	}

	@Override
	public boolean func_225509_J__() {
		return true;
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
	
	/**
	 * Get the experience points the entity currently has.
	 */
	protected int getExperiencePoints(PlayerEntity player) {
		if (player.equals(getOwner()))
			return 0;
		return 1 + this.world.rand.nextInt(3);
	}
}