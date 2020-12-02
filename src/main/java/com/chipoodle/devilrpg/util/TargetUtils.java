package com.chipoodle.devilrpg.util;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.chipoodle.devilrpg.DevilRpg;

import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.dispenser.IPosition;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.CreatureAttribute;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.attributes.Attribute;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.boss.dragon.EnderDragonPartEntity;
import net.minecraft.entity.item.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.TieredItem;
import net.minecraft.network.play.server.SEntityVelocityPacket;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.potion.Effects;
import net.minecraft.stats.Stats;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

/**
 * 
 * A collection of methods related to target acquisition Source:
 * https://github.com/coolAlias/ZeldaSwordSkills/blob/1.8/src/main/java/zeldaswordskills/util/TargetUtils.java#L189
 */
public class TargetUtils {
	/** Maximum range within which to search for targets */
	private static final int MAX_DISTANCE = 256;
	public static final Random RANDOM = new Random();
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
	public static double getReachDistanceSq(PlayerEntity player) {
		// return 38.5D; // seems to be just about right for Creative Mode hit detection
		return 9D; // seems to be just about right for Creative Mode hit detection
	}

	/**
	 * Returns true if current target is within the player's reach distance; does
	 * NOT check mouse over
	 */
	public static boolean canReachTarget(PlayerEntity player, Entity target) {
		return (player.canEntityBeSeen(target) && player.getDistanceSq(target) < getReachDistanceSq(player));
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
	 * Entity shooter, double hitBox, boolean flag) { double posY = entity.getPosY()
	 * + (entity.getHeight() / 2); // fix for Dash Vector3d vec3 = new
	 * Vector3d(entity.getPosX(), posY, entity.getPosZ()); Vector3d motion =
	 * entity.getMotion(); Vector3d vec31 = new Vector3d(entity.getPosX() +
	 * motion.getX(), posY + motion.getY(),entity.getPosZ() + motion.getZ());
	 * BlockMode b; RayTraceContext r = new RayTraceContext(vec3, vec31,
	 * BlockMode.COLLIDER, FluidMode.ANY, entity); // RayTraceResult mop =
	 * world.rayTraceBlocks(vec3, vec31, false, true, false); RayTraceResult mop =
	 * world.rayTraceBlocks(r); vec3 = new Vector3d(entity.getPosX(), posY,
	 * entity.getPosZ());
	 * 
	 * motion = entity.getMotion(); vec31 = new Vector3d(entity.getPosX() +
	 * motion.getX(), posY + motion.getY(), entity.getPosZ() + motion.getZ()); if
	 * (mop != null) {
	 * 
	 * vec31 = new Vector3d(mop.getHitVec().x, mop.getHitVec().y,
	 * mop.getHitVec().z); } Entity target = null; motion = entity.getMotion();
	 * List<Entity> list = world.getEntitiesWithinAABBExcludingEntity(entity,
	 * entity.getBoundingBox() .expand(motion.getX(), motion.getY(),
	 * motion.getZ()).grow(1.0D, 1.0D, 1.0D)); double d0 = 0.0D; for (int i = 0; i <
	 * list.size(); ++i) { Entity entity1 = (Entity) list.get(i); if
	 * (entity1.canBeCollidedWith() && (entity1 != shooter || flag)) { AxisAlignedBB
	 * axisalignedbb = entity1.getBoundingBox().expand(hitBox, hitBox, hitBox);
	 * RayTraceResult mop1 = axisalignedbb.calculateIntercept(vec3, vec31); if (mop1
	 * != null) { double d1 = vec3.distanceTo(mop1.getHitVec()); if (d1 < d0 || d0
	 * == 0.0D) { target = entity1; d0 = d1; } } } } if (target != null) { mop = new
	 * RayTraceResult(target); } if (mop != null && mop.entityHit instanceof
	 * PlayerEntity) { PlayerEntity player = (PlayerEntity) mop.entityHit; if
	 * (player.capabilities.disableDamage || (shooter instanceof PlayerEntity &&
	 * !((PlayerEntity) shooter).canAttackPlayer(player))) { mop = null; } } return
	 * mop; }
	 */

	/**
	 * Returns true if the entity is directly in the crosshairs
	 */
	@SuppressWarnings("resource")
	public static boolean isMouseOverEntity(Entity entity) {
		RayTraceResult mop = Minecraft.getInstance().objectMouseOver;
		return (mop != null && mop.getType().equals(RayTraceResult.Type.ENTITY));
	}

	/**
	 * Returns the Entity that the mouse is currently over, or null
	 */
	@SuppressWarnings("resource")
	public static Entity getMouseOverEntity() {
		RayTraceResult mop = Minecraft.getInstance().objectMouseOver;
		if (mop != null && mop.getType().equals(RayTraceResult.Type.ENTITY))
			((EntityRayTraceResult) mop).getEntity();
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
	 * @param distance        max distance to check for target, in blocks; negative
	 *                        value will check to MAX_DISTANCE
	 * @param radius          max distance, in blocks, to search on either side of
	 *                        the vector's path
	 * @param closestToEntity if true, the target closest to the seeker and still
	 *                        within the line of sight search radius is returned
	 * @return the entity the seeker is looking at or null if no entity within sight
	 *         search range
	 */
	public static final LivingEntity acquireLookTarget(LivingEntity seeker, int distance, double radius,
			boolean closestToSeeker) {
		if (distance < 0 || distance > MAX_DISTANCE) {
			distance = MAX_DISTANCE;
		}
		LivingEntity currentTarget = null;
		double currentDistance = MAX_DISTANCE_SQ;
		Vector3d vec3 = seeker.getLookVec();
		double targetX = seeker.getPosX();
		double targetY = seeker.getPosY() + seeker.getEyeHeight() - 0.10000000149011612D;
		double targetZ = seeker.getPosZ();
		double distanceTraveled = 0;

		while ((int) distanceTraveled < distance) {
			targetX += vec3.x;
			targetY += vec3.y;
			targetZ += vec3.z;
			distanceTraveled += vec3.length();
			AxisAlignedBB bb = new AxisAlignedBB(targetX - radius, targetY - radius, targetZ - radius, targetX + radius,
					targetY + radius, targetZ + radius);
			List<LivingEntity> list = seeker.world.getEntitiesWithinAABB(LivingEntity.class, bb);
			for (LivingEntity target : list) {
				if (target != seeker && target.canBeCollidedWith() && isTargetInSight(vec3, seeker, target)) {
					double newDistance = (closestToSeeker ? target.getDistanceSq(seeker)
							: target.getDistanceSq(targetX, targetY, targetZ));
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
		Vector3d vec3 = seeker.getLookVec();
		double targetX = seeker.getPosX();
		double targetY = seeker.getPosY() + seeker.getEyeHeight() - 0.10000000149011612D;
		double targetZ = seeker.getPosZ();
		double distanceTraveled = 0;

		while ((int) distanceTraveled < distance) {
			targetX += vec3.x;
			targetY += vec3.y;
			targetZ += vec3.z;
			distanceTraveled += vec3.length();
			AxisAlignedBB bb = new AxisAlignedBB(targetX - radius, targetY - radius, targetZ - radius, targetX + radius,
					targetY + radius, targetZ + radius);
			List<LivingEntity> list = seeker.world.getEntitiesWithinAABB(LivingEntity.class, bb);

			return list.stream().filter(
					target -> target != seeker && target.canBeCollidedWith() && isTargetInSight(vec3, seeker, target))
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
		Vector3d vec3 = seeker.getLookVec();
		double targetX = seeker.getPosX();
		double targetY = seeker.getPosY() + seeker.getEyeHeight() - 0.10000000149011612D;
		double targetZ = seeker.getPosZ();
		double distanceTraveled = 0;

		while ((int) distanceTraveled < distance) {
			targetX += vec3.x;
			targetY += vec3.y;
			targetZ += vec3.z;
			distanceTraveled += vec3.length();
			AxisAlignedBB bb = new AxisAlignedBB(targetX - radius, targetY - radius, targetZ - radius, targetX + radius,
					targetY + radius, targetZ + radius);
			List<LivingEntity> list = seeker.world.getEntitiesWithinAABB(classEntity, bb);
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

	/**
	 * Returns whether the target is in the seeker's field of view based on relative
	 * position
	 * 
	 * @param fov seeker's field of view; a wider angle returns true more often
	 */
	public static final boolean isTargetInFrontOf(Entity seeker, Entity target, float fov) {
		// thanks again to Battlegear2 for the following code snippet
		double dx = target.getPosX() - seeker.getPosX();
		double dz;
		for (dz = target.getPosZ() - seeker.getPosZ(); dx * dx + dz * dz < 1.0E-4D; dz = (Math.random() - Math.random())
				* 0.01D) {
			dx = (Math.random() - Math.random()) * 0.01D;
		}
		while (seeker.rotationYaw > 360) {
			seeker.rotationYaw -= 360;
		}
		while (seeker.rotationYaw < -360) {
			seeker.rotationYaw += 360;
		}
		float yaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - seeker.rotationYaw;
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
		return isTargetInSight(seeker.getLookVec(), seeker, target);
	}

	/**
	 * Returns true if the target's position is within the area that the seeker is
	 * facing and the target can be seen
	 */
	private static final boolean isTargetInSight(Vector3d vec3, LivingEntity seeker, Entity target) {
		return seeker.canEntityBeSeen(target) && isTargetInFrontOf(seeker, target, 60);
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
	 * 0) { arrow.setFire(100); } }
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
		float vectorLength = MathHelper.sqrt(vecX * vecX + vecY * vecY + vecZ * vecZ);
		vecX /= vectorLength;
		vecY /= vectorLength;
		vecZ /= vectorLength;
		vecX += entity.world.rand.nextGaussian() * (entity.world.rand.nextBoolean() ? -1 : 1) * 0.007499999832361937D
				* wobble;
		vecY += entity.world.rand.nextGaussian() * (entity.world.rand.nextBoolean() ? -1 : 1) * 0.007499999832361937D
				* wobble;
		vecZ += entity.world.rand.nextGaussian() * (entity.world.rand.nextBoolean() ? -1 : 1) * 0.007499999832361937D
				* wobble;
		vecX *= velocity;
		vecY *= velocity;
		vecZ *= velocity;
		entity.setMotion(vecX, vecY, vecZ);
		float f = MathHelper.sqrt(vecX * vecX + vecZ * vecZ);
		entity.prevRotationYaw = entity.rotationYaw = (backwards ? -1 : 1)
				* (float) (Math.atan2(vecX, vecZ) * 180.0D / Math.PI);
		entity.prevRotationPitch = entity.rotationPitch = (backwards ? -1 : 1)
				* (float) (Math.atan2(vecY, f) * 180.0D / Math.PI);
	}

	/**
	 * Returns true if the entity has an unimpeded view of the sky
	 */
	public static boolean canEntitySeeSky(World world, Entity entity) {
		BlockPos pos = new BlockPos((IPosition) entity);
		while (pos.getY() < world.getHeight()) {
			if (!world.isAirBlock(pos)) {
				return false;
			}
			pos = pos.up();
		}
		return true;
	}

	/**
	 * Whether the entity is currently standing in any liquid
	 */

	/*
	 * public static boolean isInLiquid(Entity entity) { BlockState state =
	 * entity.world.getBlockState(new BlockPos(entity.getPositionVec())); return
	 * state.getBlock(). getMaterial().isLiquid(); }
	 */

	/**
	 * Attacks for the player the targeted entity with the currently equipped item.
	 * The equipped item has hitEntity called on it. Args: targetEntity
	 */
	public static void attackTargetEntityWithItemHand(ServerPlayerEntity player, Entity targetEntity,
			Hand currentHand) {
		if (player.interactionManager.getGameType() == GameType.SPECTATOR) {
			player.setSpectatingEntity(targetEntity);
		} else {
			// player.attackTargetEntityWithCurrentItem(targetEntity);
			attackTargetEntity(player, targetEntity, currentHand);
		}

	}

	/**
	 * Attacks for the player the targeted entity with the currently equipped item.
	 * The equipped item has hitEntity called on it. Args: targetEntity
	 */
	private static void attackTargetEntity(ServerPlayerEntity player, Entity targetEntity, Hand currentHand) {

		/*
		 * if (!net.minecraftforge.common.ForgeHooks.onPlayerAttackTarget(player,
		 * targetEntity)) return;
		 */

		if (targetEntity != null && targetEntity.canBeAttackedWithItem()) {
			if (!targetEntity.hitByEntity(player)) {
				float f = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
				float f1;
				if (targetEntity instanceof LivingEntity) {
					f1 = EnchantmentHelper.getModifierForCreature(player.getHeldItem(currentHand),
							((LivingEntity) targetEntity).getCreatureAttribute());
				} else {
					f1 = EnchantmentHelper.getModifierForCreature(player.getHeldItem(currentHand),
							CreatureAttribute.UNDEFINED);
				}

				float f2 = ((RANDOM.nextInt() + new Date().getTime()) % 10 + 1) * 0.1f;// player.getCooledAttackStrength(0.5F);
																						// Porcentaje desde 10 a 100,
																						// de 10 en 10
				f = f * (0.2F + f2 * f2 * 0.8F);
				f1 = f1 * f2;
				player.resetCooldown();
				if (f > 0.0F || f1 > 0.0F) {
					boolean flag = f2 > 0.9F;
					boolean flag1 = false;
					int i = 0;
					i = i + EnchantmentHelper.getKnockbackModifier(player);
					if (player.isSprinting() && flag) {
						player.world.playSound((PlayerEntity) null, player.getPosX(), player.getPosY(),
								player.getPosZ(), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, player.getSoundCategory(),
								1.0F, 1.0F);
						++i;
						flag1 = true;
					}

					boolean flag2 = flag && player.fallDistance > 0.0F && !player.isOnGround() && !player.isOnLadder()
							&& !player.isInWater() && !player.isPotionActive(Effects.BLINDNESS) && !player.isPassenger()
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
					double d0 = (double) (player.distanceWalkedModified - player.prevDistanceWalkedModified);
					if (flag && !flag2 && !flag1 && player.isOnGround() && d0 < (double) player.getAIMoveSpeed()) {
						ItemStack itemstack = player.getHeldItem(currentHand);
						DevilRpg.LOGGER.info("----->HAND: " + currentHand.name() + " ITEM: "
								+ itemstack.getItem().getName().getString());
						if (itemstack.getItem() instanceof SwordItem) {
							flag3 = true;
						}
					}

					float f4 = 0.0F;
					boolean flag4 = false;
					int j = EnchantmentHelper.getFireAspectModifier(player);
					if (targetEntity instanceof LivingEntity) {
						f4 = ((LivingEntity) targetEntity).getHealth();
						if (j > 0 && !targetEntity.isBurning()) {
							flag4 = true;
							targetEntity.setFire(1);
						}
					}

					Vector3d vector3d = targetEntity.getMotion();
					boolean flag5 = targetEntity.attackEntityFrom(DamageSource.causePlayerDamage(player), f);
					if (flag5) {
						if (i > 0) {
							if (targetEntity instanceof LivingEntity) {
								((LivingEntity) targetEntity).applyKnockback((float) i * 0.5F,
										(double) MathHelper.sin(player.rotationYaw * ((float) Math.PI / 180F)),
										(double) (-MathHelper.cos(player.rotationYaw * ((float) Math.PI / 180F))));
							} else {
								targetEntity.addVelocity(
										(double) (-MathHelper.sin(player.rotationYaw * ((float) Math.PI / 180F))
												* (float) i * 0.5F),
										0.1D, (double) (MathHelper.cos(player.rotationYaw * ((float) Math.PI / 180F))
												* (float) i * 0.5F));
							}

							player.setMotion(player.getMotion().mul(0.6D, 1.0D, 0.6D));
							player.setSprinting(false);
						}

						if (flag3) {
							float f3 = 1.0F + EnchantmentHelper.getSweepingDamageRatio(player) * f;

							for (LivingEntity livingentity : player.world.getEntitiesWithinAABB(LivingEntity.class,
									targetEntity.getBoundingBox().grow(1.0D, 0.25D, 1.0D))) {
								if (livingentity != player && livingentity != targetEntity
										&& !player.isOnSameTeam(livingentity)
										&& (!(livingentity instanceof ArmorStandEntity)
												|| !((ArmorStandEntity) livingentity).hasMarker())
										&& player.getDistanceSq(livingentity) < 9.0D) {
									livingentity.applyKnockback(0.4F,
											(double) MathHelper.sin(player.rotationYaw * ((float) Math.PI / 180F)),
											(double) (-MathHelper.cos(player.rotationYaw * ((float) Math.PI / 180F))));
									livingentity.attackEntityFrom(DamageSource.causePlayerDamage(player), f3);
								}
							}

							player.world.playSound((PlayerEntity) null, player.getPosX(), player.getPosY(),
									player.getPosZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, player.getSoundCategory(),
									1.0F, 1.0F);
							player.spawnSweepParticles();
						}

						if (targetEntity instanceof ServerPlayerEntity && targetEntity.velocityChanged) {
							((ServerPlayerEntity) targetEntity).connection
									.sendPacket(new SEntityVelocityPacket(targetEntity));
							targetEntity.velocityChanged = false;
							targetEntity.setMotion(vector3d);
						}

						if (flag2) {
							player.world.playSound((PlayerEntity) null, player.getPosX(), player.getPosY(),
									player.getPosZ(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, player.getSoundCategory(),
									1.0F, 1.0F);
							player.onCriticalHit(targetEntity);
						}

						if (!flag2 && !flag3) {
							if (flag) {
								player.world.playSound((PlayerEntity) null, player.getPosX(), player.getPosY(),
										player.getPosZ(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG,
										player.getSoundCategory(), 1.0F, 1.0F);
							} else {
								player.world.playSound((PlayerEntity) null, player.getPosX(), player.getPosY(),
										player.getPosZ(), SoundEvents.ENTITY_PLAYER_ATTACK_WEAK,
										player.getSoundCategory(), 1.0F, 1.0F);
							}
						}

						if (f1 > 0.0F) {
							player.onEnchantmentCritical(targetEntity);
						}

						player.setLastAttackedEntity(targetEntity);
						if (targetEntity instanceof LivingEntity) {
							EnchantmentHelper.applyThornEnchantments((LivingEntity) targetEntity, player);
						}

						EnchantmentHelper.applyArthropodEnchantments(player, targetEntity);
						ItemStack itemstack1 = player.getHeldItem(currentHand);
						Entity entity = targetEntity;
						if (targetEntity instanceof EnderDragonPartEntity) {
							entity = ((EnderDragonPartEntity) targetEntity).dragon;
						}

						if (!player.world.isRemote && !itemstack1.isEmpty() && entity instanceof LivingEntity) {
							ItemStack copy = itemstack1.copy();
							itemstack1.hitEntity((LivingEntity) entity, player);
							if (itemstack1.isEmpty()) {
								net.minecraftforge.event.ForgeEventFactory.onPlayerDestroyItem(player, copy,
										currentHand);
								player.setHeldItem(currentHand, ItemStack.EMPTY);
							}
						}

						if (targetEntity instanceof LivingEntity) {
							float f5 = f4 - ((LivingEntity) targetEntity).getHealth();
							player.addStat(Stats.DAMAGE_DEALT, Math.round(f5 * 10.0F));
							if (j > 0) {
								targetEntity.setFire(j * 4);
							}

							if (player.world instanceof ServerWorld && f5 > 2.0F) {
								int k = (int) ((double) f5 * 0.5D);
								((ServerWorld) player.world).spawnParticle(ParticleTypes.DAMAGE_INDICATOR,
										targetEntity.getPosX(), targetEntity.getPosYHeight(0.5D),
										targetEntity.getPosZ(), k, 0.1D, 0.0D, 0.1D, 0.2D);
							}
						}

						player.addExhaustion(0.1F);
					} else {
						player.world.playSound((PlayerEntity) null, player.getPosX(), player.getPosY(),
								player.getPosZ(), SoundEvents.ENTITY_PLAYER_ATTACK_NODAMAGE, player.getSoundCategory(),
								1.0F, 1.0F);
						if (flag4) {
							targetEntity.extinguish();
						}
					}
				}

			}
		}
	}

	public static Entity getEntityByUUID(ServerWorld w, UUID uuid) {
		ServerWorld sw = (ServerWorld) w;
		return sw.getEntityByUuid(uuid);
	}

	public static Entity getEntityByUUID(ClientWorld w, UUID uuid) {
		ClientWorld cw = (ClientWorld) w;
		return StreamSupport.stream(cw.getAllEntities().spliterator(), true).filter(x -> x.getUniqueID().equals(uuid))
				.findAny().orElse(null);
	}
}
