package com.chipoodle.devilrpg.entity.wisp;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.chipoodle.devilrpg.capability.skill.IBaseSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityProvider;
import com.chipoodle.devilrpg.entity.ISoulEntity;
import com.chipoodle.devilrpg.util.SkillEnum;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LogBlock;
import net.minecraft.entity.AgeableEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntitySize;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ILivingEntityData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Pose;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.controller.FlyingMovementController;
import net.minecraft.entity.ai.goal.FollowMobGoal;
import net.minecraft.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.entity.ai.goal.LookAtGoal;
import net.minecraft.entity.ai.goal.PanicGoal;
import net.minecraft.entity.ai.goal.SitGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.IFlyingAnimal;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.IPacket;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.pathfinding.FlyingPathNavigator;
import net.minecraft.pathfinding.PathNavigator;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import net.minecraftforge.fml.network.FMLPlayMessages;
import net.minecraftforge.fml.network.NetworkHooks;

public class WispEntity extends TameableEntity implements IFlyingAnimal, ISoulEntity {
	private static final DataParameter<Integer> VARIANT = EntityDataManager.createKey(WispEntity.class,
			DataSerializers.VARINT);

	public float flap;
	public float flapSpeed;
	public float oFlapSpeed;
	public float oFlap;
	private float flapping = 1.0F;

	private final int SALUD_INICIAL = 8;
	private int puntosAsignados = 0;
	private double saludMaxima = SALUD_INICIAL;
	protected Effect efectoPrimario;
	protected Effect efectoSecundario;
	protected double distanciaEfecto = 20;
	protected int divisorNivelParaPotenciaEfecto = 5;
	protected int durationTicks = 120;
	private LazyOptional<IBaseSkillCapability> skill;

	public WispEntity(EntityType<? extends WispEntity> type, World worldIn) {
		super(type, worldIn);
		this.moveController = new FlyingMovementController(this, 10, false);
		this.setPathPriority(PathNodeType.DANGER_FIRE, -1.0F);
		this.setPathPriority(PathNodeType.DAMAGE_FIRE, -1.0F);
		this.setVariant(this.rand.nextInt(5));
	}

	@Nullable
	public ILivingEntityData onInitialSpawn(IWorld worldIn, DifficultyInstance difficultyIn, SpawnReason reason,
			@Nullable ILivingEntityData spawnDataIn, @Nullable CompoundNBT dataTag) {
		this.setVariant(this.rand.nextInt(5));
		if (spawnDataIn == null) {
			spawnDataIn = new AgeableEntity.AgeableData();
			((AgeableEntity.AgeableData) spawnDataIn).func_226259_a_(false);
		}

		return super.onInitialSpawn(worldIn, difficultyIn, reason, spawnDataIn, dataTag);
	}

	protected void registerGoals() {
		this.sitGoal = new SitGoal(this);
		this.goalSelector.addGoal(0, new PanicGoal(this, 1.25D));
		this.goalSelector.addGoal(0, new SwimGoal(this));
		this.goalSelector.addGoal(1, new LookAtGoal(this, PlayerEntity.class, 8.0F));
		this.goalSelector.addGoal(2, this.sitGoal);
		this.goalSelector.addGoal(2, new FollowOwnerGoal(this, 1.0D, 10.0F, 2.0F, false));
		this.goalSelector.addGoal(2, new WaterAvoidingRandomFlyingGoal(this, 1.0D));
		//this.goalSelector.addGoal(3, new LandOnOwnersShoulderGoal(this));
		this.goalSelector.addGoal(3, new FollowMobGoal(this, 1.0D, 3.0F, 7.0F));
	}

	protected void registerAttributes() {
		super.registerAttributes();
		this.getAttributes().registerAttribute(SharedMonsterAttributes.FLYING_SPEED);
		this.getAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(6.0D);
		this.getAttribute(SharedMonsterAttributes.FLYING_SPEED).setBaseValue((double) 0.4F);
		this.getAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue((double) 0.2F);
		this.getAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(16.0D);
	}
	
	public void updateLevel(PlayerEntity owner, Effect efectoPrimario, Effect efectoSecundario, SkillEnum tipoWisp) {
		setTamedBy(owner);
		skill = getOwner().getCapability(PlayerSkillCapabilityProvider.SKILL_CAP);
		this.efectoPrimario = efectoPrimario;
		this.efectoSecundario = efectoSecundario;
		if (skill != null && skill.isPresent()) {
			this.puntosAsignados = skill.map(x -> x.getSkillsPoints()).orElse(null).get(tipoWisp);
			saludMaxima = 0.6 * this.puntosAsignados + SALUD_INICIAL;
		}

		this.getAttributes().registerAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(0.15D);
	    this.getAttributes().registerAttribute(SharedMonsterAttributes.ARMOR_TOUGHNESS);
		this.getAttribute(SharedMonsterAttributes.FLYING_SPEED).setBaseValue((double) 0.9F);
		this.getAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(saludMaxima);
	}

	/**
	 * Returns new PathNavigateGround instance
	 */
	protected PathNavigator createNavigator(World worldIn) {
		FlyingPathNavigator flyingpathnavigator = new FlyingPathNavigator(this, worldIn);
		flyingpathnavigator.setCanOpenDoors(false);
		flyingpathnavigator.setCanSwim(true);
		flyingpathnavigator.setCanEnterDoors(true);
		return flyingpathnavigator;
	}

	protected float getStandingEyeHeight(Pose poseIn, EntitySize sizeIn) {
		return sizeIn.height * 0.6F;
	}

	/**
	 * Called frequently so the entity can update its state every tick as required.
	 * For example, zombies and skeletons use this to react to sunlight and start to
	 * burn.
	 */
	public void livingTick() {
		super.livingTick();
		this.calculateFlapping();
		if (this.getOwnerId() == null || !this.getOwner().isAlive() || !this.isTamed())
			remove();
		
		if (this.world.getGameTime() % 80L == 0L && efectoPrimario != null && efectoSecundario != null) {
			this.addEffectsToPlayers(puntosAsignados, efectoPrimario, efectoSecundario);
		}
		addToLivingTick(this);
	}

	/**
	 * Called when a record starts or stops playing. Used to make parrots start or
	 * stop partying.
	 */
	@OnlyIn(Dist.CLIENT)
	public void setPartying(BlockPos pos, boolean isPartying) {
		
	}

	private void calculateFlapping() {
		this.oFlap = this.flap;
		this.oFlapSpeed = this.flapSpeed;
		this.flapSpeed = (float) ((double) this.flapSpeed
				+ (double) (!this.onGround && !this.isPassenger() ? 4 : -1) * 0.3D);
		this.flapSpeed = MathHelper.clamp(this.flapSpeed, 0.0F, 1.0F);
		if (!this.onGround && this.flapping < 1.0F) {
			this.flapping = 1.0F;
		}

		this.flapping = (float) ((double) this.flapping * 0.9D);
		Vec3d vec3d = this.getMotion();
		if (!this.onGround && vec3d.y < 0.0D) {
			this.setMotion(vec3d.mul(1.0D, 0.6D, 1.0D));
		}

		this.flap += this.flapping * 2.0F;
	}

	/**
	 * doesn't interact. It's magical!
	 */
	public boolean processInteract(PlayerEntity player, Hand hand) {
		return true;
	}

	/**
	 * Checks if the parameter is an item which this animal can be fed to breed it
	 * (wheat, carrots or seeds depending on the animal type)
	 */
	public boolean isBreedingItem(ItemStack stack) {
		return false;
	}

	public static boolean func_223317_c(EntityType<WispEntity> p_223317_0_, IWorld p_223317_1_, SpawnReason reason,
			BlockPos p_223317_3_, Random p_223317_4_) {
		Block block = p_223317_1_.getBlockState(p_223317_3_.down()).getBlock();
		return (block.isIn(BlockTags.LEAVES) || block == Blocks.GRASS_BLOCK || block instanceof LogBlock
				|| block == Blocks.AIR) && p_223317_1_.getLightSubtracted(p_223317_3_, 0) > 8;
	}

	public boolean onLivingFall(float distance, float damageMultiplier) {
		return false;
	}

	protected void updateFallState(double y, boolean onGroundIn, BlockState state, BlockPos pos) {
	}

	/**
	 * Returns true if the mob is currently able to mate with the specified mob.
	 */
	public boolean canMateWith(AnimalEntity otherAnimal) {
		return false;
	}

	@Nullable
	public AgeableEntity createChild(AgeableEntity ageable) {
		return null;
	}

	public boolean attackEntityAsMob(Entity entityIn) {
		return false;// entityIn.attackEntityFrom(DamageSource.causeMobDamage(this), 3.0F);
	}

	@Nullable
	public SoundEvent getAmbientSound() {
		return getAmbientSound(this.rand);
	}

	private static SoundEvent getAmbientSound(Random random) {
		return SoundEvents.ENTITY_PARROT_AMBIENT;
	}

	protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
		return SoundEvents.ENTITY_PARROT_HURT;
	}

	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_PARROT_DEATH;
	}

	protected void playStepSound(BlockPos pos, BlockState blockIn) {
		this.playSound(SoundEvents.ENTITY_PARROT_STEP, 0.15F, 1.0F);
	}

	protected float playFlySound(float volume) {
		this.playSound(SoundEvents.ENTITY_PARROT_FLY, 0.15F, 1.0F);
		return volume + this.flapSpeed / 2.0F;
	}

	protected boolean makeFlySound() {
		return true;
	}

	/**
	 * Gets the pitch of living sounds in living entities.
	 */
	protected float getSoundPitch() {
		return getPitch(this.rand);
	}

	private static float getPitch(Random random) {
		return (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F;
	}

	public SoundCategory getSoundCategory() {
		return SoundCategory.NEUTRAL;
	}

	/**
	 * Returns true if this entity should push and be pushed by other entities when
	 * colliding.
	 */
	public boolean canBePushed() {
		return true;
	}

	protected void collideWithEntity(Entity entityIn) {
		if (!(entityIn instanceof PlayerEntity)) {
			super.collideWithEntity(entityIn);
		}
	}

	/**
	 * Called when the entity is attacked.
	 */
	public boolean attackEntityFrom(DamageSource source, float amount) {
		if (this.isInvulnerableTo(source)) {
			return false;
		} else {
			if (this.sitGoal != null) {
				this.sitGoal.setSitting(false);
			}

			return super.attackEntityFrom(source, amount);
		}
	}

	public int getVariant() {
		return MathHelper.clamp(this.dataManager.get(VARIANT), 0, 4);
	}

	public void setVariant(int variantIn) {
		this.dataManager.set(VARIANT, variantIn);
	}

	protected void registerData() {
		super.registerData();
		this.dataManager.register(VARIANT, 0);
	}

	/**
	 * (abstract) Protected helper method to read subclass entity data from NBT.
	 */
	public void readAdditional(CompoundNBT compound) {
		super.readAdditional(compound);
		this.setVariant(compound.getInt("Variant"));
	}

	public boolean isFlying() {
		return !this.onGround;
	}

	protected int getPotenciaPocion(int niveles) {
		return (int) Math.ceil(niveles / (divisorNivelParaPotenciaEfecto));
	}

	@Override
	public void writeAdditional(CompoundNBT compound) {
		super.writeAdditional(compound);
		compound.putString("OwnerUUID", "");
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

	private void addEffectsToPlayers(int niveles, Effect primaryEffect, Effect secondaryEffect) {
		if (niveles >= 0 && !this.world.isRemote && primaryEffect != null) {
			// rango entre 0 - 4 el tipo de boost health
			int i = getPotenciaPocion(niveles);
			// System.out.println("Level of effect" + i);

			int k = this.getPosition().getX();
			int l = this.getPosition().getY();
			int i1 = this.getPosition().getZ();
			AxisAlignedBB axisalignedbb = (new AxisAlignedBB((double) k, (double) l, (double) i1, (double) (k + 1),
					(double) (l + 1), (double) (i1 + 1))).grow(distanciaEfecto).expand(0.0D,
							(double) this.world.getHeight(), 0.0D);
			List<LivingEntity> list = this.world.<LivingEntity>getEntitiesWithinAABB(LivingEntity.class, axisalignedbb)
					.stream().filter(x -> x.isOnSameTeam(this.getOwner()) || x.equals(getOwner()))
					.collect(Collectors.toList());

			if (niveles > 0) {
				for (LivingEntity entity : list) {
					EffectInstance pri = new EffectInstance(primaryEffect, durationTicks, i, false, true);
					EffectInstance active = entity.getActivePotionEffect(primaryEffect);
					if (entity.getActivePotionEffect(primaryEffect) == null
							|| pri.getAmplifier() > active.getAmplifier()) {
						entity.addPotionEffect(pri);
					} else {
						active.combine(pri);
					}
				}

				/*for (LivingEntity entity : list) {
					EffectInstance aux = new EffectInstance(Effects.GLOWING, durationTicks, 0, false, true);
					EffectInstance active = entity.getActivePotionEffect(Effects.GLOWING);
					if (entity.getActivePotionEffect(Effects.GLOWING) == null
							|| aux.getAmplifier() > active.getAmplifier()) {
						entity.addPotionEffect(aux);
					} else {
						active.combine(aux);
					}
				}*/

				if (niveles >= 10) {
					for (LivingEntity entity : list) {
						EffectInstance sec = new EffectInstance(secondaryEffect, durationTicks, 0, false, true);
						EffectInstance active = entity.getActivePotionEffect(secondaryEffect);
						if (entity.getActivePotionEffect(secondaryEffect) == null || sec.getAmplifier() > active.getAmplifier()) {
							entity.addPotionEffect(sec);
						} else {
							active.combine(sec);
						}
					}
				}
			}
		}
	}
	
	/*@Override
	public void remove() {
		if (auraAffectedEntities != null)
			auraAffectedEntities.stream().forEach(x -> {
				x.removeActivePotionEffect(efectoPrimario);
				x.removeActivePotionEffect(efectoSecundario);
				x.removeActivePotionEffect(efectoAuxiliar);
			});
		super.remove();
	}*/

	/**
	 * Called on the logical server to get a packet to send to the client containing data necessary to spawn your entity.
	 * Using Forge's method instead of the default vanilla one allows extra stuff to work such as sending extra data,
	 * using a non-default entity factory and having {@link IEntityAdditionalSpawnData} work.
	 *
	 * It is not actually necessary for our WildBoarEntity to use Forge's method as it doesn't need any of this extra
	 * functionality, however, this is an example mod and many modders are unaware that Forge's method exists.
	 *
	 * @return The packet with data about your entity
	 * @see FMLPlayMessages.SpawnEntity
	 */
	@Override
	public IPacket<?> createSpawnPacket() {
		return NetworkHooks.getEntitySpawningPacket(this);
	}
}
