package com.chipoodle.devilrpg.entity.goal;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.SmallFireball;

import java.util.EnumSet;

public class MiniFireAttackGoal extends Goal {
    private final Mob mobEntity;
    private int attackStep;
    private int attackTime;
    private int lastSeen;

    public MiniFireAttackGoal(Mob p_32247_) {
        this.mobEntity = p_32247_;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    public boolean canUse() {
        LivingEntity livingentity = this.mobEntity.getTarget();
        return livingentity != null && livingentity.isAlive() && this.mobEntity.canAttack(livingentity);
    }

    public void start() {
        this.attackStep = 0;
    }

    public void stop() {
        //this.blaze.setCharged(false);
        this.lastSeen = 0;
    }

    public boolean requiresUpdateEveryTick() {
        return true;
    }

    public void tick() {
        --this.attackTime;
        LivingEntity livingentity = this.mobEntity.getTarget();
        if (livingentity != null) {
            boolean flag = this.mobEntity.getSensing().hasLineOfSight(livingentity);
            if (flag) {
                this.lastSeen = 0;
            } else {
                ++this.lastSeen;
            }

            double d0 = this.mobEntity.distanceToSqr(livingentity);
            if (d0 < 4.0D) {
                if (!flag) {
                    return;
                }

                if (this.attackTime <= 0) {
                    this.attackTime = 20;
                    this.mobEntity.doHurtTarget(livingentity);
                }

                this.mobEntity.getMoveControl().setWantedPosition(livingentity.getX(), livingentity.getY(), livingentity.getZ(), 1.0D);
            } else if (d0 < this.getFollowDistance() * this.getFollowDistance() && flag) {
                double d1 = livingentity.getX() - this.mobEntity.getX();
                double d2 = livingentity.getY(0.5D) - this.mobEntity.getY(0.5D);
                double d3 = livingentity.getZ() - this.mobEntity.getZ();
                if (this.attackTime <= 0) {
                    ++this.attackStep;
                    if (this.attackStep == 1) {
                        this.attackTime = 60;
                        //this.blaze.setCharged(true);
                    } else if (this.attackStep <= 4) {
                        this.attackTime = 6;
                    } else {
                        this.attackTime = 100;
                        this.attackStep = 0;
                        //this.blaze.setCharged(false);
                    }

                    if (this.attackStep > 1) {
                        double d4 = Math.sqrt(Math.sqrt(d0)) * 0.5D;
                        if (!this.mobEntity.isSilent()) {
                            this.mobEntity.level.levelEvent((Player) null, 1018, this.mobEntity.blockPosition(), 0);
                        }

                        for (int i = 0; i < 1; ++i) {
                            SmallFireball smallfireball = new SmallFireball(this.mobEntity.level, this.mobEntity, this.mobEntity.getRandom().triangle(d1, 2.297D * d4), d2, this.mobEntity.getRandom().triangle(d3, 2.297D * d4));
                            smallfireball.setPos(smallfireball.getX(), this.mobEntity.getY(0.5D) + 0.5D, smallfireball.getZ());
                            this.mobEntity.level.addFreshEntity(smallfireball);
                        }
                    }
                }

                this.mobEntity.getLookControl().setLookAt(livingentity, 10.0F, 10.0F);
            } else if (this.lastSeen < 5) {
                this.mobEntity.getMoveControl().setWantedPosition(livingentity.getX(), livingentity.getY(), livingentity.getZ(), 1.0D);
            }

            super.tick();
        }
    }

    private double getFollowDistance() {
        return this.mobEntity.getAttributeValue(Attributes.FOLLOW_RANGE);
    }
}