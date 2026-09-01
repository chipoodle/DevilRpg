package com.chipoodle.devilrpg.entity;


import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.Team;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/**
 * Entities that implment this interface carry the TamableMinion data attachment (see {@link com.chipoodle.devilrpg.init.ModCapabilities}).
 */
public interface ITamableEntity extends IAttachmentHolder, OwnableEntity, Leashable {

    @NotNull Level level();

    PathNavigation getNavigation();
    /*@Nullable
    LivingEntity getOwner();*/

    boolean isOrderedToSit();

    void setOrderedToSit(boolean sit);

    double distanceToSqr(LivingEntity livingentity);

    float getPathfindingMalus(PathType water);

    void setPathfindingMalus(PathType water, float f);

    int getMaxHeadXRot();

    LookControl getLookControl();

    boolean isPassenger();

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

    default boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        if (target instanceof ITamableEntity entity) {
            return !entity.isTame() || !Objects.equals(entity.getOwnerUUID(), owner.getUUID());
        } else if (target instanceof Player && owner instanceof Player && !((Player) owner).canHarmPlayer((Player) target)) {
            return false;
        } else return !(target instanceof AbstractHorse) || !((AbstractHorse) target).isTamed();
    }

    AttributeInstance getAttribute(Holder<Attribute> key);

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

    DamageSources damageSources();
}
