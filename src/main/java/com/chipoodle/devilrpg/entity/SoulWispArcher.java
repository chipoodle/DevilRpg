package com.chipoodle.devilrpg.entity;

import com.chipoodle.devilrpg.init.ModEntities;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;


public class SoulWispArcher extends SoulWisp implements RangedAttackMob {

    private static final float RADIUS = 0.15f;
    //private final RangedCreatureGoal<SoulWispArcherEntity> bowGoal = new RangedCreatureGoal<>(this, 1.0D, 20, 15.0F);

    public SoulWispArcher(EntityType<? extends SoulWispArcher> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        // super.registerGoals();

        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomFlyingGoal(this, 1.0D));
        this.goalSelector.addGoal(1, new RangedAttackGoal(this, 1.25D, 20, 10.0F));
        this.goalSelector.addGoal(5, new FollowOwnerGoal(this, 1.0D, 10.0F, 2.0F, false));
        this.goalSelector.addGoal(9, new SoulWisp.WanderGoal());
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Mob.class, 8.0F));
        this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Mob.class, 10, true, false, (entity) -> Math.abs(entity.getY() - this.getY()) <= 4.0D &&
                !(entity instanceof Villager) &&
                !(entity instanceof Llama) &&
                !(entity instanceof Turtle) &&
                !(entity instanceof IronGolem)));
        this.targetSelector.addGoal(2, new HurtByTargetGoal(this));
    }

    @Override
    public void performRangedAttack(LivingEntity p_82196_1_, float p_82196_2_) {
        double d0 = p_82196_1_.getX() - this.getX();
        double d1 = p_82196_1_.getY(0.3233333333333333D) - this.getY();
        double d2 = p_82196_1_.getZ() - this.getZ();
        double d3 = Math.sqrt(d0 * d0 + d2 * d2);

        FrostBall snowballEntity = new FrostBall(this.level, this);
        snowballEntity.updateLevel((Player) Objects.requireNonNull(this.getOwner()), SkillEnum.SUMMON_WISP_ARCHER);
        snowballEntity.shoot(d0, d1 + d3 * (double) 0.2F, d2, 1.6F, (float) (14 - this.level.getDifficulty().getId() * 4));
        this.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
        this.level.addFreshEntity(snowballEntity);
        //this.setHealth(this.getHealth() -1.5F);
    }

    public void updateLevel(Player owner) {
        super.updateLevel(owner,null,null,SkillEnum.SUMMON_WISP_ARCHER,true);
    }

    @Nullable
    @Override
    public SoulWispArcher getBreedOffspring(@NotNull ServerLevel level, @NotNull AgeableMob ageableMob) {
        return ModEntities.WISP_ARCHER.get().create(level);
    }
}
