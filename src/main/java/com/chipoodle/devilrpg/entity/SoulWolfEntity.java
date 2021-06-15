package com.chipoodle.devilrpg.entity;

import java.util.UUID;

import com.chipoodle.devilrpg.capability.minion.IBaseMinionCapability;
import com.chipoodle.devilrpg.capability.minion.PlayerMinionCapabilityProvider;
import com.chipoodle.devilrpg.capability.skill.IBaseSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityProvider;
import com.chipoodle.devilrpg.client.render.IRenderUtilities;
import com.chipoodle.devilrpg.init.ModEntityTypes;
import com.chipoodle.devilrpg.util.SkillEnum;

import net.minecraft.entity.AgeableEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.IChargeableMob;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.entity.ai.goal.HurtByTargetGoal;
import net.minecraft.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.entity.ai.goal.LookAtGoal;
import net.minecraft.entity.ai.goal.LookRandomlyGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.NearestAttackableTargetGoal;
import net.minecraft.entity.ai.goal.OwnerHurtByTargetGoal;
import net.minecraft.entity.ai.goal.OwnerHurtTargetGoal;
import net.minecraft.entity.ai.goal.ResetAngerGoal;
import net.minecraft.entity.ai.goal.SitGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WaterAvoidingRandomWalkingGoal;
import net.minecraft.entity.merchant.villager.VillagerEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.TurtleEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.passive.horse.LlamaEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.IPacket;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import net.minecraftforge.fml.network.FMLPlayMessages;
import net.minecraftforge.fml.network.NetworkHooks;

public class SoulWolfEntity extends WolfEntity implements ISoulEntity, IChargeableMob, IRenderUtilities {
	private final int SALUD_INICIAL = 10;
	private int puntosAsignados = 0;
	private double saludMaxima = SALUD_INICIAL;
	private double stealingHealth;

	public SoulWolfEntity(EntityType<? extends SoulWolfEntity> type, World worldIn) {
		super(type, worldIn);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new SwimGoal(this));
		this.goalSelector.addGoal(2, new SitGoal(this));
		this.goalSelector.addGoal(3, new SoulWolfEntity.AvoidEntityGoal<VillagerEntity>(this, VillagerEntity.class, 24.0F, 1.5D, 1.5D));
		this.goalSelector.addGoal(3, new SoulWolfEntity.AvoidEntityGoal<LlamaEntity>(this, LlamaEntity.class, 24.0F, 1.5D, 1.5D));
		this.goalSelector.addGoal(3, new SoulWolfEntity.AvoidEntityGoal<TurtleEntity>(this, TurtleEntity.class, 24.0F, 1.5D, 1.5D));
		this.goalSelector.addGoal(3, new SoulWolfEntity.AvoidEntityGoal<IronGolemEntity>(this, IronGolemEntity.class, 24.0F, 1.5D, 1.5D));
		this.goalSelector.addGoal(4, new LeapAtTargetGoal(this, 0.4F));
		this.goalSelector.addGoal(5, new MeleeAttackGoal(this, 1.0D, true));
		this.goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0D, 10.0F, 2.0F, false));
		this.goalSelector.addGoal(8, new WaterAvoidingRandomWalkingGoal(this, 1.0D));
		this.goalSelector.addGoal(10, new LookAtGoal(this, PlayerEntity.class, 8.0F));
		this.goalSelector.addGoal(10, new LookRandomlyGoal(this));

		this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
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
			this.puntosAsignados = skill.map(x -> x.getSkillsPoints()).orElse(null).get(SkillEnum.SUMMON_SOUL_WOLF);
			saludMaxima = 1.0 * this.puntosAsignados + SALUD_INICIAL;
			stealingHealth = (0.135f * puntosAsignados) + 0.5;
		}

		this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue((double) 0.4F);
		this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(saludMaxima);
		this.getAttribute(Attributes.ARMOR).setBaseValue(0.35D);
		this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(16.0D);
		this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue((0.45 * puntosAsignados) + 2); // 2-11
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
			this.heal((float) stealingHealth);
		}
		return flag;
	}

	class AvoidEntityGoal<T extends LivingEntity> extends net.minecraft.entity.ai.goal.AvoidEntityGoal<T> {
		private final SoulWolfEntity wolf;

		public AvoidEntityGoal(SoulWolfEntity wolfIn, Class<T> entityClassToAvoidIn, float avoidDistanceIn, double farSpeedIn,
				double nearSpeedIn) {
			super(wolfIn, entityClassToAvoidIn, avoidDistanceIn, farSpeedIn, nearSpeedIn);
			this.wolf = wolfIn;
		}

		/**
		 * Returns whether execution should begin. You can also read and cache any state
		 * necessary for execution in this method as well.
		 */
		public boolean canUse() {
			if (super.canUse() && this.toAvoid instanceof LlamaEntity) {
				return this.wolf.isTame() && this.avoidLlama((LlamaEntity) this.toAvoid);
			}
			if (super.canUse() && this.toAvoid instanceof TurtleEntity) {
				return this.wolf.isTame() && this.avoidTurtle((TurtleEntity) this.toAvoid);
			}
			if (super.canUse() && this.toAvoid instanceof VillagerEntity) {
				return this.wolf.isTame() && this.avoidVillager((VillagerEntity) this.toAvoid);
			}
			if (super.canUse() && this.toAvoid instanceof IronGolemEntity) {
				return this.wolf.isTame() && this.avoidIronGolem((IronGolemEntity) this.toAvoid);
			}
			return false;
		}

		private boolean avoidLlama(LlamaEntity llamaIn) {
			return llamaIn.getStrength() >= SoulWolfEntity.this.random.nextInt(5);
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
			SoulWolfEntity.this.setTarget((LivingEntity) null);
			super.start();
		}

		/**
		 * Keep ticking a continuous task that has already been started
		 */
		public void tick() {
			SoulWolfEntity.this.setTarget((LivingEntity) null);
			super.tick();
		}
	}
	
	@Override
	public ActionResultType mobInteract(PlayerEntity playerIn, Hand hand) {
		return ActionResultType.PASS;
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
			minionCap.ifPresent(x -> x.removeSoulWolf((PlayerEntity) getOwner(), this));
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
	@OnlyIn(Dist.CLIENT)
	public float getTailAngle() {
		if (this.isAngry()) {
			return 1.5393804F;
		} else {
			return ((float) Math.PI / 5F);
		}
	}
	
	@Override
	public boolean isPowered() {
		return true;
	}

	@Override
	public SoulWolfEntity getBreedOffspring(ServerWorld world, AgeableEntity mate) {
		return ModEntityTypes.SOUL_WOLF.get().create(world);
	}
}
