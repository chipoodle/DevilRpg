package com.chipoodle.devilrpg.entity;

import com.chipoodle.devilrpg.client.render.IRenderUtilities;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.entity.ai.goal.HurtByTargetGoal;
import net.minecraft.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.entity.ai.goal.LookAtGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.NearestAttackableTargetGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.entity.merchant.villager.VillagerEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.TurtleEntity;
import net.minecraft.entity.passive.horse.LlamaEntity;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;

public class SoulWispBomberEntity extends SoulWispEntity {

	private static final float RADIUS = 0.15f;

	public SoulWispBomberEntity(EntityType<? extends SoulWispBomberEntity> type, World worldIn) {
		super(type, worldIn);
	}

	@Override
	protected void registerGoals() {
		// super.registerGoals();

		this.goalSelector.addGoal(0, new SwimGoal(this));
		this.goalSelector.addGoal(3, new WaterAvoidingRandomFlyingGoal(this, 1.0D));
		this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0D, true));
		this.goalSelector.addGoal(5, new FollowOwnerGoal(this, 1.0D, 10.0F, 2.0F, false));

		this.goalSelector.addGoal(6, new LeapAtTargetGoal(this, 0.4F));

		this.goalSelector.addGoal(9, new SoulWispEntity.WanderGoal());
		this.goalSelector.addGoal(10, new LookAtGoal(this, MobEntity.class, 8.0F));
		// this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this,MobEntity.class, false));
		//this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this,MobEntity.class, false));
		this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, MobEntity.class, 10, false, true, (entity) -> {
	         return  !(entity instanceof VillagerEntity) &&
	        		 !(entity instanceof LlamaEntity) && 
	        		 !(entity instanceof TurtleEntity) && 
	        		 !(entity instanceof IronGolemEntity) ;
	     }));
		this.targetSelector.addGoal(2, new HurtByTargetGoal(this));
		//this.targetSelector.addGoal(3, new OwnerHurtByTargetGoal(this));
		//this.targetSelector.addGoal(4, new OwnerHurtTargetGoal(this));
		// this.targetSelector.addGoal(8, new ResetAngerGoal<>(this, true));
	}

	public boolean doHurtTarget(Entity p_70652_1_) {
		explodeCreeper();
		return true;
	}

	private void explodeCreeper() {
		if (!this.level.isClientSide) {
			Explosion.Mode explosionMode = net.minecraftforge.event.ForgeEventFactory.getMobGriefingEvent(this.level,
					this) ? Explosion.Mode.DESTROY : Explosion.Mode.NONE;

			IRenderUtilities.spawnLingeringCloud(this.level,this,RADIUS,puntosAsignados);
			this.dead = true;
			this.level.explode(this, this.getX(), this.getY(), this.getZ(), 1 + RADIUS * puntosAsignados,
					explosionMode);
			this.remove();
		}

	}

	

}
