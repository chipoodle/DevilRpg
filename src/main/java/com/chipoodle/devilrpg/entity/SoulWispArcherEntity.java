package com.chipoodle.devilrpg.entity;

import com.chipoodle.devilrpg.util.SkillEnum;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.IRangedAttackMob;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.entity.ai.goal.HurtByTargetGoal;
import net.minecraft.entity.ai.goal.LookAtGoal;
import net.minecraft.entity.ai.goal.LookRandomlyGoal;
import net.minecraft.entity.ai.goal.NearestAttackableTargetGoal;
import net.minecraft.entity.ai.goal.RangedAttackGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.entity.merchant.villager.VillagerEntity;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.TurtleEntity;
import net.minecraft.entity.passive.horse.LlamaEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class SoulWispArcherEntity extends SoulWispEntity implements IRangedAttackMob {

	private static final float RADIUS = 0.15f;
	//private final RangedCreatureGoal<SoulWispArcherEntity> bowGoal = new RangedCreatureGoal<>(this, 1.0D, 20, 15.0F);

	public SoulWispArcherEntity(EntityType<? extends SoulWispArcherEntity> type, World worldIn) {
		super(type, worldIn);
	}

	@Override
	protected void registerGoals() {
		// super.registerGoals();

		this.goalSelector.addGoal(0, new SwimGoal(this));
		this.goalSelector.addGoal(3, new WaterAvoidingRandomFlyingGoal(this, 1.0D));
		this.goalSelector.addGoal(1, new RangedAttackGoal(this, 1.25D, 20, 10.0F));
		this.goalSelector.addGoal(5, new FollowOwnerGoal(this, 1.0D, 10.0F, 2.0F, false));
		this.goalSelector.addGoal(9, new SoulWispEntity.WanderGoal());
		this.goalSelector.addGoal(1, new LookAtGoal(this, MobEntity.class, 8.0F));
		this.goalSelector.addGoal(2, new LookRandomlyGoal(this));
		this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, MobEntity.class, 10, true, false, (entity) -> {
	         return Math.abs(entity.getY() - this.getY()) <= 4.0D &&
	        		 !(entity instanceof VillagerEntity) &&
	        		 !(entity instanceof LlamaEntity) && 
	        		 !(entity instanceof TurtleEntity) && 
	        		 !(entity instanceof IronGolemEntity) ;
	     }));
		 
		this.targetSelector.addGoal(2, new HurtByTargetGoal(this));
	}

	@Override
	public void performRangedAttack(LivingEntity p_82196_1_, float p_82196_2_) {
		double d0 = p_82196_1_.getX() - this.getX();
		double d1 = p_82196_1_.getY(0.3233333333333333D) - this.getY();
		double d2 = p_82196_1_.getZ() - this.getZ();
		double d3 = MathHelper.sqrt(d0 * d0 + d2 * d2);

		SoulIceBallEntity snowballentity = new SoulIceBallEntity(this.level, this);
		snowballentity.updateLevel((PlayerEntity) this.getOwner(),SkillEnum.SUMMON_WISP_ARCHER);

		snowballentity.shoot(d0, d1 + d3 * (double) 0.2F, d2, 1.6F,
				(float) (14 - this.level.getDifficulty().getId() * 4));
		this.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
		this.level.addFreshEntity(snowballentity);
		this.setHealth(this.getHealth()-2);
	}

}
