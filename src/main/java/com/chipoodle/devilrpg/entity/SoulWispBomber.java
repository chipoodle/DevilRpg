package com.chipoodle.devilrpg.entity;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;

import java.util.Collection;


public class SoulWispBomber extends SoulWisp {

    private static final float RADIUS = 0.15f;

    public SoulWispBomber(EntityType<? extends SoulWispBomber> type, Level worldIn) {
        super(type, worldIn);
    }

    @Override
    protected void registerGoals() {
        // super.registerGoals();

        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomFlyingGoal(this, 1.0D));
        this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(5, new FollowOwnerGoal(this, 1.0D, 10.0F, 2.0F, false));

        this.goalSelector.addGoal(6, new LeapAtTargetGoal(this, 0.4F));

        this.goalSelector.addGoal(9, new SoulWisp.WanderGoal());
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Mob.class, 8.0F));
        // this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this,MobEntity.class, false));
        //this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this,MobEntity.class, false));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Mob.class, 10, false, true, (entity) -> {
            return !(entity instanceof Villager) && !(entity instanceof Llama) && !(entity instanceof Turtle) && !(entity instanceof IronGolem);
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
            this.dead = true;
            this.level.explode(this, this.getX(), this.getY(), this.getZ(), RADIUS * puntosAsignados, Level.ExplosionInteraction.MOB);
            this.discard();
            this.remove(RemovalReason.KILLED);
            spawnLingeringCloud();
        }

    }

    private void spawnLingeringCloud() {
        Collection<MobEffectInstance> collection = this.getActiveEffects();
        if (!collection.isEmpty()) {
            AreaEffectCloud areaeffectcloud = new AreaEffectCloud(this.level, this.getX(), this.getY(), this.getZ());
            areaeffectcloud.setRadius(2.5F);
            areaeffectcloud.setRadiusOnUse(-0.5F);
            areaeffectcloud.setWaitTime(10);
            areaeffectcloud.setDuration(areaeffectcloud.getDuration() / 2);
            areaeffectcloud.setRadiusPerTick(-areaeffectcloud.getRadius() / (float) areaeffectcloud.getDuration());

            for (MobEffectInstance mobeffectinstance : collection) {
                areaeffectcloud.addEffect(new MobEffectInstance(mobeffectinstance));
            }

            this.level.addFreshEntity(areaeffectcloud);
        }

    }


}
