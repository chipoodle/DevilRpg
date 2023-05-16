package com.chipoodle.devilrpg.entity;


import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.Team;
import net.minecraftforge.common.capabilities.ICapabilityProvider;

import javax.annotation.Nullable;
import java.util.UUID;

public interface ITamableEntity extends ICapabilityProvider {

    Level getLevel();

    PathNavigation getNavigation();

    LivingEntity getOwner();

    boolean isOrderedToSit();

    void setOrderedToSit(boolean sit);

    double distanceToSqr(LivingEntity livingentity);

    float getPathfindingMalus(BlockPathTypes water);

    void setPathfindingMalus(BlockPathTypes water, float f);

    int getMaxHeadXRot();

    LookControl getLookControl();

    boolean isPassenger();

    boolean isLeashed();

    float getXRot();

    float getYRot();

    void moveTo(double d, double p_226328_2_, double e, float getyRot, float getxRot);

    Vec3i blockPosition();

    Entity getEntity();

    AABB getBoundingBox();


    default RandomSource getRandom() {
        return RandomSource.create();
    }

    boolean isTame();

    void setTame(boolean p_21836_);

    default boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        if (target instanceof ITamableEntity) {
            ITamableEntity entity = (ITamableEntity) target;
            return !entity.isTame() || entity.getOwner() != owner;
        } else if (target instanceof Player && owner instanceof Player
                && !((Player) owner).canHarmPlayer((Player) target)) {
            return false;
        } else return !(target instanceof AbstractHorse) || !((AbstractHorse) target).isTamed();
    }

    AttributeInstance getAttribute(Attribute key);

    //ITextComponent getDisplayName();

    float getMaxHealth();

    void setHealth(float maxHealth);

    default boolean isEntitySameOwnerAsThis(Entity entityIn, ITamableEntity entityThis) {
        boolean isSameOwner = false;
        if (entityIn instanceof ITamableEntity && ((ITamableEntity) entityIn).getOwner() != null)
            isSameOwner = ((ITamableEntity) entityIn).getOwner().equals(entityThis.getOwner());
        return isSameOwner;
    }

    Team getTeam();

    boolean isAlliedTo(Entity p_184191_1_);

    @Nullable
    UUID getOwnerUUID();

    void setOwnerUUID(@Nullable UUID uuid);

    boolean hurt(DamageSource damageSource, float maxValue);


    boolean canAttack(LivingEntity p_213336_1_);

    default boolean isOwnedBy(LivingEntity p_152114_1_) {
        return p_152114_1_ == this.getOwner();
    }

    void tame(Player p_193101_1_);

}
