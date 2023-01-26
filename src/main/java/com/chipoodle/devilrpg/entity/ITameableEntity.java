package com.chipoodle.devilrpg.entity;

import com.chipoodle.devilrpg.capability.IGenericCapability;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.attributes.Attribute;
import net.minecraft.entity.ai.attributes.ModifiableAttributeInstance;
import net.minecraft.entity.ai.controller.LookController;
import net.minecraft.entity.passive.horse.AbstractHorseEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.pathfinding.PathNavigator;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector3i;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;

import javax.annotation.Nullable;
import java.util.Random;
import java.util.UUID;

public interface ITameableEntity extends ICapabilityProvider {

    World getLevel();

    PathNavigator getNavigation();

    LivingEntity getOwner();

    boolean isOrderedToSit();

    void setOrderedToSit(boolean sit);

    double distanceToSqr(LivingEntity livingentity);

    float getPathfindingMalus(PathNodeType water);

    void setPathfindingMalus(PathNodeType water, float f);

    int getMaxHeadXRot();

    LookController getLookControl();

    boolean isPassenger();

    boolean isLeashed();

    float getxRot();

    float getyRot();

    void moveTo(double d, double p_226328_2_, double e, float getyRot, float getxRot);

    Vector3i blockPosition();

    Entity getEntity();

    AxisAlignedBB getBoundingBox();

    Random getRandom();

    boolean isTame();

    default boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        if (target instanceof ITameableEntity) {
            ITameableEntity entity = (ITameableEntity) target;
            return !entity.isTame() || entity.getOwner() != owner;
        } else if (target instanceof PlayerEntity && owner instanceof PlayerEntity
                && !((PlayerEntity) owner).canHarmPlayer((PlayerEntity) target)) {
            return false;
        } else if (target instanceof AbstractHorseEntity && ((AbstractHorseEntity) target).isTamed()) {
            return false;
        } else {
            return true;
        }
    }

    ModifiableAttributeInstance getAttribute(Attribute key);

    ITextComponent getDisplayName();

    float getMaxHealth();

    void setHealth(float maxHealth);

    default boolean isEntitySameOwnerAsThis(Entity entityIn, ITameableEntity entityThis) {
        boolean isSameOwner = false;
        if (entityIn instanceof ITameableEntity && ((ITameableEntity) entityIn).getOwner() != null)
            isSameOwner = ((ITameableEntity) entityIn).getOwner().equals(entityThis.getOwner());
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

    public void tame(PlayerEntity p_193101_1_);

}
