package com.chipoodle.devilrpg.util;

import com.chipoodle.devilrpg.DevilRpg;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * A collection of methods related to target acquisition Source:
 * https://github.com/coolAlias/ZeldaSwordSkills/blob/1.8/src/main/java/zeldaswordskills/util/TargetUtils.java#L189
 */
public class TargetUtils {
    public static final Random RANDOM = new Random();
    /**
     * Maximum range within which to search for targets
     */
    private static final int MAX_DISTANCE = 256;
    /**
     * Max distance squared, used for comparing target distances (avoids having to
     * call sqrt)
     */
    private static final double MAX_DISTANCE_SQ = MAX_DISTANCE * MAX_DISTANCE;

    // TODO write general MovingObjectPosition method, then have specific methods
    // return blockHit or entityHit from that
    // TODO methods for acquiring multiple targets (beam, sphere, etc) with optional
    // number of targets to acquire

    /**
     * Returns the player's current reach distance, taking held item into account if
     * applicable
     */
    // Packet7UseEntity uses 36.0D for determining if an attack should hit, or 9.0D
    // if the entity cannot be seen
    // EntityRenderer uses 36.0D for creative mode, otherwise 9.0D, in calculating
    // whether mouseover entity should be null
    // but using this exactly results in some attacks that in reality hit, being
    // counted as a miss
    // Unlike Creative Mode, the mouseover is always null when an attack should miss
    // when in Survival
    public static double getReachDistanceSq(Player player) {
        // return 38.5D; // seems to be just about right for Creative Mode hit detection
        return 10D; // seems to be just about right for Creative Mode hit detection
    }

    /**
     * Returns true if current target is within the player's reach distance; does
     * NOT check mouse over
     */
    public static boolean canReachTarget(Player player, Entity target) {
        return (player.hasLineOfSight(target) && player.distanceToSqr(target) < getReachDistanceSq(player));
    }

    /**
     * Returns MovingObjectPosition of Entity or Block impacted, or null if nothing
     * was struck
     *
     * @param entity  The entity checking for impact, e.g. an arrow
     * @param shooter An entity not to be collided with, generally the shooter
     * @param hitBox  The amount by which to expand the collided entities' bounding
     *                boxes when checking for impact (may be negative)
     * @param flag    Optional flag to allow collision with shooter, e.g.
     *                (ticksInAir >= 5)
     */
    /*
     * public static RayTraceResult checkForImpact(World world, Entity entity,
     * Entity shooter, double hitBox, boolean flag) { double posY = entity.getY()
     * + (entity.getHeight() / 2); // fix for Dash Vector3d vec3 = new
     * Vector3d(entity.getX(), posY, entity.getZ()); Vector3d motion =
     * entity.getDeltaMovement(); Vector3d vec31 = new Vector3d(entity.getX() +
     * motion.getX(), posY + motion.getY(),entity.getZ() + motion.getZ());
     * BlockMode b; RayTraceContext r = new RayTraceContext(vec3, vec31,
     * BlockMode.COLLIDER, FluidMode.ANY, entity); // RayTraceResult mop =
     * world.rayTraceBlocks(vec3, vec31, false, true, false); RayTraceResult mop =
     * world.rayTraceBlocks(r); vec3 = new Vector3d(entity.getX(), posY,
     * entity.getZ());
     *
     * motion = entity.getDeltaMovement(); vec31 = new Vector3d(entity.getX() +
     * motion.getX(), posY + motion.getY(), entity.getZ() + motion.getZ()); if
     * (mop != null) {
     *
     * vec31 = new Vector3d(mop.getHitVec().x, mop.getHitVec().y,
     * mop.getHitVec().z); } Entity target = null; motion = entity.getDeltaMovement();
     * List<Entity> list = world.getEntitiesOfClassExcludingEntity(entity,
     * entity.getBoundingBox() .expand(motion.getX(), motion.getY(),
     * motion.getZ()).inflate(1.0D, 1.0D, 1.0D)); double d0 = 0.0D; for (int i = 0; i <
     * list.size(); ++i) { Entity entity1 = (Entity) list.get(i); if
     * (entity1.canBeCollidedWith() && (entity1 != shooter || flag)) { AxisAlignedBB
     * axisalignedbb = entity1.getBoundingBox().expand(hitBox, hitBox, hitBox);
     * RayTraceResult mop1 = axisalignedbb.calculateIntercept(vec3, vec31); if (mop1
     * != null) { double d1 = vec3.distanceTo(mop1.getHitVec()); if (d1 < d0 || d0
     * == 0.0D) { target = entity1; d0 = d1; } } } } if (target != null) { mop = new
     * RayTraceResult(target); } if (mop != null && mop.entityHit instanceof
     * Player) { Player player = (Player) mop.entityHit; if
     * (player.capabilities.disableDamage || (shooter instanceof Player &&
     * !((Player) shooter).canAttackPlayer(player))) { mop = null; } } return
     * mop; }
     */

    /**
     * Returns true if the entity is directly in the crosshairs
     */
    @SuppressWarnings("resource")
    public static boolean isMouseOverEntity(Entity entity) {
        HitResult hitResult = Minecraft.getInstance().hitResult;
        return (hitResult != null && hitResult.getType().equals(HitResult.Type.ENTITY));
    }

    /**
     * Returns the Entity that the mouse is currently over, or null
     */
    @SuppressWarnings("resource")
    public static Entity getMouseOverEntity() {
        HitResult mop = Minecraft.getInstance().hitResult;
        if (mop != null && mop.getType().equals(HitResult.Type.ENTITY))
            ((EntityHitResult) mop).getEntity();
        return null;
    }

    /**
     * Returns the LivingEntity closest to the point at which the seeker is looking
     * and within the distance and radius specified
     */
    public static final LivingEntity acquireLookTarget(LivingEntity seeker, int distance, double radius) {
        return acquireLookTarget(seeker, distance, radius, false);
    }

    /**
     * Returns the LivingEntity closest to the point at which the entity is looking
     * and within the distance and radius specified
     *
     * @param seeker          the LivingEntity
     * @param distance        max distance to check for target, in blocks; negative
     *                        value will check to MAX_DISTANCE
     * @param radius          max distance, in blocks, to search on either side of
     *                        the vector's path
     * @param closestToSeeker if true, the target closest to the seeker and still
     *                        within the line of sight search radius is returned
     * @return the entity the seeker is looking at or null if no entity within sight
     * search range
     */
    public static final LivingEntity acquireLookTarget(LivingEntity seeker, int distance, double radius,
                                                       boolean closestToSeeker) {
        if (distance < 0 || distance > MAX_DISTANCE) {
            distance = MAX_DISTANCE;
        }
        LivingEntity currentTarget = null;
        double currentDistance = MAX_DISTANCE_SQ;
        Vec3 lookAngle = seeker.getLookAngle();
        double targetX = seeker.getX();
        double targetY = seeker.getY() + seeker.getEyeHeight() - 0.10000000149011612D;
        double targetZ = seeker.getZ();
        double distanceTraveled = 0;

        while ((int) distanceTraveled < distance) {
            targetX += lookAngle.x;
            targetY += lookAngle.y;
            targetZ += lookAngle.z;
            distanceTraveled += lookAngle.length();
            AABB bb = new AABB(targetX - radius, targetY - radius, targetZ - radius, targetX + radius,
                    targetY + radius, targetZ + radius);
            List<LivingEntity> list = seeker.level.getEntitiesOfClass(LivingEntity.class, bb);
            for (LivingEntity target : list) {
                if (target != seeker && target.canBeCollidedWith() && isTargetInSight(lookAngle, seeker, target)) {
                    double newDistance = (closestToSeeker ? target.distanceToSqr(seeker)
                            : target.distanceToSqr(targetX, targetY, targetZ));
                    if (newDistance < currentDistance) {
                        currentTarget = target;
                        currentDistance = newDistance;
                    }
                }
            }
        }

        return currentTarget;
    }

    /**
     * Similar to the single entity version, but this method returns a List of all
     * LivingEntity entities that are within the entity's field of vision, up to a
     * certain range and distance away
     */
    public static final List<LivingEntity> acquireAllLookTargets(LivingEntity seeker, int distance, double radius) {
        if (distance < 0 || distance > MAX_DISTANCE) {
            distance = MAX_DISTANCE;
        }
        List<LivingEntity> targets = new ArrayList<LivingEntity>();
        Vec3 lookAngle = seeker.getLookAngle();
        double targetX = seeker.getX();
        double targetY = seeker.getY() + seeker.getEyeHeight() - 0.10000000149011612D;
        double targetZ = seeker.getZ();
        double distanceTraveled = 0;

        while ((int) distanceTraveled < distance) {
            targetX += lookAngle.x;
            targetY += lookAngle.y;
            targetZ += lookAngle.z;
            distanceTraveled += lookAngle.length();
            AABB bb = new AABB(targetX - radius, targetY - radius, targetZ - radius, targetX + radius,
                    targetY + radius, targetZ + radius);
            List<LivingEntity> list = seeker.level.getEntitiesOfClass(LivingEntity.class, bb);

            return list.stream()
                    .filter(
                            target -> target != seeker /*&& target.canBeCollidedWith()*/ && isTargetInSight(lookAngle, seeker, target))
                    .distinct().collect(Collectors.toList());

        }

        return targets;
    }

    /**
     * Similar to the single entity version, but this method returns a List of all
     * entities of certain class that are within the entity's field of vision, up to
     * a certain range and distance away
     */
    public static final List<LivingEntity> acquireAllLookTargetsByClass(LivingEntity seeker,
                                                                        Class<? extends LivingEntity> classEntity, int distance, double radius) {
        if (distance < 0 || distance > MAX_DISTANCE) {
            distance = MAX_DISTANCE;
        }
        List<LivingEntity> targets = new ArrayList<LivingEntity>();
        Vec3 vec3 = seeker.getLookAngle();
        double targetX = seeker.getX();
        double targetY = seeker.getY() + seeker.getEyeHeight() - 0.10000000149011612D;
        double targetZ = seeker.getZ();
        double distanceTraveled = 0;

        while ((int) distanceTraveled < distance) {
            targetX += vec3.x;
            targetY += vec3.y;
            targetZ += vec3.z;
            distanceTraveled += vec3.length();
            AABB bb = new AABB(targetX - radius, targetY - radius, targetZ - radius, targetX + radius,
                    targetY + radius, targetZ + radius);
            List<? extends LivingEntity> list = seeker.level.getEntitiesOfClass(classEntity, bb);
            for (LivingEntity target : list) {
                if (target != seeker && target.canBeCollidedWith() && isTargetInSight(vec3, seeker, target)) {
                    if (!targets.contains(target)) {
                        targets.add(target);
                    }
                }
            }
        }

        return targets;
    }

    public static List<? extends LivingEntity> acquireAllTargetsInRadiusByClass(LivingEntity seeker, Class<? extends LivingEntity> classEntity, double radius) {
        AABB bb = new AABB(seeker.getX() - radius, seeker.getY() - radius, seeker.getZ() - radius, seeker.getX() + radius,
                seeker.getY() + radius, seeker.getZ() + radius);
        return seeker.level.getEntitiesOfClass(classEntity, bb);
    }

    /**
     * Returns whether the target is in the seeker's field of view based on relative
     * position
     *
     * @param fov seeker's field of view; a wider angle returns true more often
     */
    public static final boolean isTargetInFrontOf(Entity seeker, Entity target, float fov) {
        // thanks again to Battlegear2 for the following code snippet
        double dx = target.getX() - seeker.getX();
        double dz;
        for (dz = target.getZ() - seeker.getZ(); dx * dx + dz * dz < 1.0E-4D; dz = (Math.random() - Math.random())
                * 0.01D) {
            dx = (Math.random() - Math.random()) * 0.01D;
        }
        while (seeker.getYRot() > 360) {
            //seeker.yRot -= 360;
            seeker.setYRot(seeker.getYRot() - 360);
        }
        while (seeker.getYRot() < -360) {
            seeker.setYRot(seeker.getYRot() + 360);
            //seeker.yRot += 360;
        }
        float yaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - seeker.getYRot();
        yaw = yaw - 90;
        while (yaw < -180) {
            yaw += 360;
        }
        while (yaw >= 180) {
            yaw -= 360;
        }
        return yaw < fov && yaw > -fov;
    }

    /**
     * Returns true if the target's position is within the area that the seeker is
     * facing and the target can be seen
     */
    public static final boolean isTargetInSight(LivingEntity seeker, Entity target) {
        return isTargetInSight(seeker.getLookAngle(), seeker, target);
    }

    /**
     * Returns true if the target's position is within the area that the seeker is
     * facing and the target can be seen
     */
    private static final boolean isTargetInSight(Vec3 vec3, LivingEntity seeker, Entity target) {
        DevilRpg.LOGGER.info("isTargetInSight-> targetName {}, canSee: {}, isTargetInFrontOfSeeker: {} ", target.getName(), seeker.hasLineOfSight(target), isTargetInFrontOf(seeker, target, 60));

        return seeker.hasLineOfSight(target) && isTargetInFrontOf(seeker, target, 60);
    }

    /**
     * Applies all vanilla modifiers to passed in arrow (e.g. enchantment bonuses,
     * critical, etc)
     *
     * @param charge should be a value between 0.0F and 1.0F, inclusive
     */
    /*
     * public static final void applyArrowSettings(EntityArrow arrow, ItemStack bow,
     * float charge) { if (charge < 0.0F) { charge = 0.0F; } if (charge > 1.0F) {
     * charge = 1.0F; }
     *
     * if (charge == 1.0F) { arrow.setIsCritical(true); }
     *
     * int k = EnchantmentHelper.getEnchantmentLevel(Enchantment.power.effectId,
     * bow);
     *
     * if (k > 0) { arrow.setDamage(arrow.getDamage() + (double) k * 0.5D + 0.5D); }
     *
     * int l = EnchantmentHelper.getEnchantmentLevel(Enchantment.punch.effectId,
     * bow);
     *
     * if (l > 0) { arrow.setKnockbackStrength(l); }
     *
     * if (EnchantmentHelper.getEnchantmentLevel(Enchantment.flame.effectId, bow) >
     * 0) { arrow.setSecondsOnFire(100); } }
     */

    /**
     * Sets an entity's motion along the given vector at the given velocity, with
     * wobble being an amount of variation applied to the course.
     *
     * @param wobble    set to 0.0F for a true heading
     * @param backwards if true, will set the entity's rotation to the opposite
     *                  direction
     */
    public static void setEntityHeading(Entity entity, double vecX, double vecY, double vecZ, float velocity,
                                        float wobble, boolean backwards) {
        float vectorLength = (float) Math.sqrt(vecX * vecX + vecY * vecY + vecZ * vecZ);
        vecX /= vectorLength;
        vecY /= vectorLength;
        vecZ /= vectorLength;
        vecX += entity.level.random.nextGaussian() * (entity.level.random.nextBoolean() ? -1 : 1) * 0.007499999832361937D
                * wobble;
        vecY += entity.level.random.nextGaussian() * (entity.level.random.nextBoolean() ? -1 : 1) * 0.007499999832361937D
                * wobble;
        vecZ += entity.level.random.nextGaussian() * (entity.level.random.nextBoolean() ? -1 : 1) * 0.007499999832361937D
                * wobble;
        vecX *= velocity;
        vecY *= velocity;
        vecZ *= velocity;
        entity.setDeltaMovement(vecX, vecY, vecZ);
        float f = (float) Math.sqrt(vecX * vecX + vecZ * vecZ);
        entity.yRotO = (backwards ? -1 : 1) * (float) (Math.atan2(vecX, vecZ) * 180.0D / Math.PI);
        entity.setYRot(entity.yRotO);
        entity.xRotO = (backwards ? -1 : 1) * (float) (Math.atan2(vecY, f) * 180.0D / Math.PI);
        entity.setXRot(entity.xRotO);
    }

    /**
     * Returns true if the entity has an unimpeded view of the sky
     */
    public static boolean canEntitySeeSky(Level world, Entity entity) {

        BlockPos pos = new BlockPos(entity.position());
        while (pos.getY() < world.getHeight()) {
            if (!world.isEmptyBlock(pos)) {
                return false;
            }
            pos = pos.above();
        }
        return true;
    }

    /**
     * Whether the entity is currently standing in any liquid
     */

    /*
     * public static boolean isInLiquid(Entity entity) { BlockState state =
     * entity.level.getBlockState(new BlockPos(entity.getPositionVec())); return
     * state.getBlock(). getMaterial().isLiquid(); }
     */

    /**
     * Attacks for the player the targeted entity with the currently equipped item.
     * The equipped item has hurtEnemy called on it. Args: targetEntity
     */
    public static void attackTargetEntityWithItemHand(ServerPlayer player, Entity targetEntity,
                                                      InteractionHand currentHand) {
        if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
            player.setCamera(targetEntity);
        } else {
            // player.attackTargetEntityWithCurrentItem(targetEntity);
            attackTargetEntity(player, targetEntity, currentHand);
        }

    }

    /**
     * Attacks for the player the targeted entity with the currently equipped item.
     * The equipped item has hurtEnemy called on it. Args: targetEntity
     */
    private static void attackTargetEntity(ServerPlayer player, Entity targetEntity, InteractionHand currentHand) {


        if (!net.minecraftforge.common.ForgeHooks.onPlayerAttackTarget(player, targetEntity)) return;


        if (targetEntity != null && targetEntity.isAttackable()) {
            if (!targetEntity.skipAttackInteraction(player)) {
                float f = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
                float f1;
                if (targetEntity instanceof LivingEntity) {
                    f1 = EnchantmentHelper.getDamageBonus(player.getItemInHand(currentHand),
                            ((LivingEntity) targetEntity).getMobType());
                } else {
                    f1 = EnchantmentHelper.getDamageBonus(player.getItemInHand(currentHand), MobType.UNDEFINED);
                }

                float f2 = ((RANDOM.nextInt() + new Date().getTime()) % 10 + 1) * 0.1f;// player.getCooledAttackStrength(0.5F);
                // Porcentaje desde 10 a 100,
                // de 10 en 10
                f = f * (0.2F + f2 * f2 * 0.8F);
                f1 = f1 * f2;
                player.resetAttackStrengthTicker();
                if (f > 0.0F || f1 > 0.0F) {
                    boolean flag = f2 > 0.9F;
                    boolean flag1 = false;
                    int i = 0;
                    i = i + EnchantmentHelper.getKnockbackBonus(player);
                    if (player.isSprinting() && flag) {
                        player.level.playSound(null, player.getX(), player.getY(),
                                player.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, player.getSoundSource(),
                                1.0F, 1.0F);
                        ++i;
                        flag1 = true;
                    }

                    boolean flag2 = flag && player.fallDistance > 0.0F && !player.isOnGround() && !player.onClimbable()
                            && !player.isInWater() && !player.hasEffect(MobEffects.BLINDNESS) && !player.isPassenger()
                            && targetEntity instanceof LivingEntity;
                    flag2 = flag2 && !player.isSprinting();
                    net.minecraftforge.event.entity.player.CriticalHitEvent hitResult = net.minecraftforge.common.ForgeHooks
                            .getCriticalHit(player, targetEntity, flag2, flag2 ? 1.5F : 1.0F);
                    flag2 = hitResult != null;
                    if (flag2) {
                        f *= hitResult.getDamageModifier();
                    }

                    f = f + f1;
                    boolean flag3 = false;
                    double d0 = player.walkDist - player.walkDistO;
                    if (flag && !flag2 && !flag1 && player.isOnGround() && d0 < (double) player.getSpeed()) {
                        ItemStack itemstack = player.getItemInHand(currentHand);
                        //DevilRpg.LOGGER.info("----->HAND: " + currentHand.name() + " ITEM: " + itemstack.getItem().getName().getString());
                        if (itemstack.getItem() instanceof SwordItem) {
                            flag3 = true;
                        }
                    }

                    float f4 = 0.0F;
                    boolean flag4 = false;
                    int j = EnchantmentHelper.getFireAspect(player);
                    if (targetEntity instanceof LivingEntity) {
                        f4 = ((LivingEntity) targetEntity).getHealth();
                        if (j > 0 && !targetEntity.isOnFire()) {
                            flag4 = true;
                            targetEntity.setSecondsOnFire(1);
                        }
                    }

                    Vec3 vector3d = targetEntity.getDeltaMovement();
                    boolean flag5 = targetEntity.hurt(DamageSource.playerAttack(player), f);
                    if (flag5) {
                        if (i > 0) {
                            if (targetEntity instanceof LivingEntity) {
                                ((LivingEntity) targetEntity).knockback((float) i * 0.5F,
                                        Mth.sin(player.getYRot() * ((float) Math.PI / 180F)),
                                        -Mth.cos(player.getYRot() * ((float) Math.PI / 180F)));
                            } else {
                                targetEntity.push(
                                        -Mth.sin(player.getYRot() * ((float) Math.PI / 180F))
                                                * (float) i * 0.5F,
                                        0.1D, Mth.cos(player.getYRot() * ((float) Math.PI / 180F))
                                                * (float) i * 0.5F);
                            }

                            player.setDeltaMovement(player.getDeltaMovement().multiply(0.6D, 1.0D, 0.6D));
                            player.setSprinting(false);
                        }

                        if (flag3) {
                            float f3 = 1.0F + EnchantmentHelper.getSweepingDamageRatio(player) * f;

                            for (LivingEntity livingentity : player.level.getEntitiesOfClass(LivingEntity.class,
                                    targetEntity.getBoundingBox().inflate(1.0D, 0.25D, 1.0D))) {
                                if (livingentity != player && livingentity != targetEntity
                                        && !player.isAlliedTo(livingentity)
                                        && (!(livingentity instanceof ArmorStand)
                                        || !((ArmorStand) livingentity).isMarker())
                                        && player.distanceToSqr(livingentity) < 9.0D) {
                                    livingentity.knockback(0.4F,
                                            Mth.sin(player.getYRot() * ((float) Math.PI / 180F)),
                                            -Mth.cos(player.getYRot() * ((float) Math.PI / 180F)));
                                    livingentity.hurt(DamageSource.playerAttack(player), f3);
                                }
                            }

                            player.level.playSound(null, player.getX(), player.getY(),
                                    player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, player.getSoundSource(),
                                    1.0F, 1.0F);
                            player.sweepAttack();
                        }

                        if (targetEntity instanceof ServerPlayer && targetEntity.hurtMarked) {
                            ((ServerPlayer) targetEntity).connection
                                    .send(new ClientboundSetEntityMotionPacket(targetEntity));
                            targetEntity.hurtMarked = false;
                            targetEntity.setDeltaMovement(vector3d);
                        }

                        if (flag2) {
                            player.level.playSound(null, player.getX(), player.getY(),
                                    player.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, player.getSoundSource(),
                                    1.0F, 1.0F);
                            player.crit(targetEntity);
                        }

                        if (!flag2 && !flag3) {
                            if (flag) {
                                player.level.playSound(null, player.getX(), player.getY(),
                                        player.getZ(), SoundEvents.PLAYER_ATTACK_STRONG,
                                        player.getSoundSource(), 1.0F, 1.0F);
                            } else {
                                player.level.playSound(null, player.getX(), player.getY(),
                                        player.getZ(), SoundEvents.PLAYER_ATTACK_WEAK,
                                        player.getSoundSource(), 1.0F, 1.0F);
                            }
                        }

                        if (f1 > 0.0F) {
                            player.magicCrit(targetEntity);
                        }

                        player.setLastHurtMob(targetEntity);
                        if (targetEntity instanceof LivingEntity) {
                            EnchantmentHelper.doPostHurtEffects((LivingEntity) targetEntity, player);
                        }

                        EnchantmentHelper.doPostDamageEffects(player, targetEntity);
                        ItemStack itemstack1 = player.getMainHandItem();
                        Entity entity = targetEntity;
                        if (targetEntity instanceof net.minecraftforge.entity.PartEntity) {
                            entity = ((net.minecraftforge.entity.PartEntity<?>) targetEntity).getParent();
                        }

                        if (!player.level.isClientSide && !itemstack1.isEmpty() && entity instanceof LivingEntity) {
                            ItemStack copy = itemstack1.copy();
                            itemstack1.hurtEnemy((LivingEntity) entity, player);
                            if (itemstack1.isEmpty()) {
                                net.minecraftforge.event.ForgeEventFactory.onPlayerDestroyItem(player, copy,
                                        currentHand);
                                player.setItemInHand(currentHand, ItemStack.EMPTY);
                            }
                        }

                        if (targetEntity instanceof LivingEntity) {
                            float f5 = f4 - ((LivingEntity) targetEntity).getHealth();
                            player.awardStat(Stats.DAMAGE_DEALT, Math.round(f5 * 10.0F));
                            if (j > 0) {
                                targetEntity.setSecondsOnFire(j * 4);
                            }

                            if (player.level instanceof ServerLevel && f5 > 2.0F) {
                                int k = (int) ((double) f5 * 0.5D);
                                ((ServerLevel) player.level).sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                                        targetEntity.getX(), targetEntity.getY(0.5D),
                                        targetEntity.getZ(), k, 0.1D, 0.0D, 0.1D, 0.2D);
                            }
                        }

                        player.causeFoodExhaustion(0.1F);
                    } else {
                        player.level.playSound(null, player.getX(), player.getY(),
                                player.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, player.getSoundSource(),
                                1.0F, 1.0F);
                        if (flag4) {
                            targetEntity.clearFire();
                        }
                    }
                }

            }
        }
    }

    public static Entity getEntityByUUID(ServerLevel w, UUID uuid) {
        ServerLevel sw = w;
        return sw.getEntity(uuid);
    }

    public static Entity getEntityByUUID(ClientLevel w, UUID uuid) {
        ClientLevel cw = w;
        return StreamSupport.stream(cw.entitiesForRendering().spliterator(), true).filter(x -> x.getUUID().equals(uuid))
                .findAny().orElse(null);
    }

    public static BlockHitResult getPlayerBlockRayResult() {
        Minecraft instance = Minecraft.getInstance();
        HitResult hitResult = instance.hitResult;
        return (BlockHitResult) hitResult;
    }

    public static HitResult getPlayerRayResult() {
        Minecraft instance = Minecraft.getInstance();
        HitResult hitResult = instance.hitResult;
        return hitResult;
    }
}
