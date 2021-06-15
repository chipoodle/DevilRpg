package com.chipoodle.devilrpg.skillsystem.skillinstance;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.auxiliar.IBaseAuxiliarCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliarCapabilityProvider;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.handler.WerewolfAttackServerHandler;
import com.chipoodle.devilrpg.skillsystem.ISkillContainer;
import com.chipoodle.devilrpg.util.SkillEnum;
import com.chipoodle.devilrpg.util.TargetUtils;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.util.LazyOptional;

public class SkillShapeshiftWerewolf implements ISkillContainer {
	private PlayerSkillCapability parentCapability;
	private static final ResourceLocation SUMMON_SOUND = new ResourceLocation(DevilRpg.MODID, "summon");
	private LazyOptional<IBaseAuxiliarCapability> aux;
	AttributeModifier healthAttributeModifier;
	AttributeModifier speedAttributeModifier;
	private Random rand = new Random();

	public SkillShapeshiftWerewolf(PlayerSkillCapability parentCapability) {
		this.parentCapability = parentCapability;
	}

	@Override
	public SkillEnum getSkillEnum() {
		return SkillEnum.TRANSFORM_WEREWOLF;
	}

	@Override
	public void execute(World worldIn, PlayerEntity playerIn) {
		if (!worldIn.isClientSide) {
			Random rand = new Random();
			SoundEvent event = new SoundEvent(SUMMON_SOUND);
			worldIn.playSound((PlayerEntity) null, playerIn.getX(), playerIn.getY(), playerIn.getZ(), event,
					SoundCategory.NEUTRAL, 0.5F, 0.4F / (rand.nextFloat() * 0.4F + 0.8F));

			aux = playerIn.getCapability(PlayerAuxiliarCapabilityProvider.AUX_CAP);
			boolean transformation = aux.map(x -> x.isWerewolfTransformation()).orElse(false);
			aux.ifPresent(x -> x.setWerewolfTransformation(!transformation, playerIn));
			if (!transformation) {
				removeCurrentModifiers(playerIn);
				createNewAttributeModifiers();
				addCurrentModifiers(playerIn);
			} else {
				removeCurrentModifiers(playerIn);
			}

		}
	}

	private void createNewAttributeModifiers() {
		healthAttributeModifier = new AttributeModifier(SkillEnum.TRANSFORM_WEREWOLF.name() + "HEALTH",
				parentCapability.getSkillsPoints().get(SkillEnum.TRANSFORM_WEREWOLF),
				AttributeModifier.Operation.ADDITION);

		speedAttributeModifier = new AttributeModifier(SkillEnum.TRANSFORM_WEREWOLF.name() + "SPEED",
				parentCapability.getSkillsPoints().get(SkillEnum.TRANSFORM_WEREWOLF) * 0.0045,
				AttributeModifier.Operation.ADDITION);
	}

	private void removeCurrentModifiers(PlayerEntity playerIn) {
		HashMap<String, UUID> attributeModifiers = parentCapability.getAttributeModifiers();
		if (healthAttributeModifier != null) {
			// playerIn.getAttribute(SharedMonsterAttributes.MAX_HEALTH).removeModifier(healthAttributeModifier.getID());
			playerIn.getAttribute(Attributes.MAX_HEALTH)
					.removeModifier(attributeModifiers.get(Attributes.MAX_HEALTH.getDescriptionId()));
			attributeModifiers.remove(Attributes.MAX_HEALTH.getDescriptionId());
			if (playerIn.getHealth() > playerIn.getMaxHealth())
				playerIn.setHealth(playerIn.getMaxHealth());
		}
		if (speedAttributeModifier != null) {
			// playerIn.getAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).removeModifier(speedAttributeModifier.getID());
			playerIn.getAttribute(Attributes.MOVEMENT_SPEED)
					.removeModifier(attributeModifiers.get(Attributes.MOVEMENT_SPEED.getDescriptionId()));
			attributeModifiers.remove(Attributes.MOVEMENT_SPEED.getDescriptionId());
		}
		parentCapability.setAttributeModifiers(attributeModifiers, playerIn);
	}

	private void addCurrentModifiers(PlayerEntity playerIn) {
		HashMap<String, UUID> attributeModifiers = parentCapability.getAttributeModifiers();
		attributeModifiers.put(Attributes.MAX_HEALTH.getDescriptionId(), healthAttributeModifier.getId());
		attributeModifiers.put(Attributes.MOVEMENT_SPEED.getDescriptionId(), speedAttributeModifier.getId());
		parentCapability.setAttributeModifiers(attributeModifiers, playerIn);

		// TODO: Verificar si funciona y usar applyPersisentModifier para probar
		// también
		playerIn.getAttribute(Attributes.MAX_HEALTH).addTransientModifier(healthAttributeModifier);
		playerIn.getAttribute(Attributes.MOVEMENT_SPEED).addTransientModifier(speedAttributeModifier);
	}

	/**
	 * Must be called inside playerTickEvent. Client side
	 * 
	 * @param player
	 */
	@OnlyIn(Dist.CLIENT)
	public void playerTickEventAttack(final PlayerEntity player, LazyOptional<IBaseAuxiliarCapability> aux) {
		if (player.level.isClientSide) {
			//DevilRpg.LOGGER.info("Skill.playerTickEventAttack");
			aux.ifPresent(auxiliarCapability -> {
				int points = parentCapability.getSkillsPoints().get(SkillEnum.TRANSFORM_WEREWOLF);
				float s = (15L - points * 0.5F);
				long attackTime = (long) s;
				//LivingEntity target = null;
				//DevilRpg.LOGGER.info("Skill.playerTickEventAttack.attackTime {} player.tickCount {}",attackTime,player.tickCount);
				
				
				if (Math.floor(player.tickCount % attackTime) == 0) {
					//DevilRpg.LOGGER.info("player.tickCount % attackTime {}",player.tickCount % attackTime);
					Hand hand = auxiliarCapability.swingHands(player);
					getEnemies(player, hand);
				}

			});
		}
	}


	/**
	 * @param player
	 * @param hand
	 */
	private void getEnemies(final PlayerEntity player, Hand hand) {
		DevilRpg.LOGGER.info("getEnemies");
		LivingEntity target;
		int distance = 1;
		double radius = 2;
		if (player != null) {
			List<LivingEntity> targetList = TargetUtils.acquireAllLookTargets(player, distance, radius).stream()
					.filter(x -> !(x instanceof TameableEntity) || !x.isAlliedTo(player))
					.collect(Collectors.toList());

			//DevilRpg.LOGGER.info("targetList.size:{}",targetList.size());
			target = targetList.stream()
					.filter(x -> targetList.size() == 1 || !x.equals(player.getLastHurtMob()))
					.min(Comparator.comparing(entity -> ((LivingEntity) entity).position().distanceToSqr(player.position()))).orElse(null);
			if (target != null /* && TargetUtils.canReachTarget(player, target) */) {
				renderParticles(player, hand);
				player.setLastHurtMob(target);
				ModNetwork.CHANNEL.sendToServer(new WerewolfAttackServerHandler(target.getId(), hand));
			}
		}
	}

	/**
	 * @param player
	 * @param hand
	 */
	private void renderParticles(final PlayerEntity player, Hand hand) {
		Vector3d vec = player.getLookAngle();
		double clawSideX = vec.z() * (hand.equals(Hand.MAIN_HAND) ? 0.6 : -0.6);
		double clawSideZ = vec.x() * (hand.equals(Hand.OFF_HAND) ? 0.6 : -0.6);
		double dx = player.getX() + clawSideX;
		double dz = player.getZ() + clawSideZ;
		double dy = player.getY() + player.getEyeHeight();// + 2.0f;// player.getEyeHeight(); // you
		// probably don't actually want to
		// subtract the vec.yCoord, unless the position depends on the
		// player's pitch
		float movement = 1.78572f;
		double acceleration = 0f;
		for (int i = 0; i < 2; ++i) {
			movement += 0.35714f;
			acceleration += 0.005D;
			double speedX = rand.nextGaussian() * 0.02D;
			double speedY = rand.nextGaussian() * 0.02D;
			double speedZ = rand.nextGaussian() * 0.02D;

			player.level.addParticle(ParticleTypes.CLOUD, dx + (vec.x() * movement), dy,
					dz + (vec.z() * movement), speedX, speedY, speedZ);
		}
	}
}
