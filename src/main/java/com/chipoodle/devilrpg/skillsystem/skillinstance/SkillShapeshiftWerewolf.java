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

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
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

	public SkillShapeshiftWerewolf(PlayerSkillCapability parentCapability) {
		this.parentCapability = parentCapability;
	}

	@Override
	public SkillEnum getSkillEnum() {
		return SkillEnum.TRANSFORM_WEREWOLF;
	}

	@Override
	public void execute(World worldIn, PlayerEntity playerIn) {
		if (!worldIn.isRemote) {
			Random rand = new Random();
			SoundEvent event = new SoundEvent(SUMMON_SOUND);
			worldIn.playSound((PlayerEntity) null, playerIn.getPosX(), playerIn.getPosY(), playerIn.getPosZ(), event,
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
					.removeModifier(attributeModifiers.get(Attributes.MAX_HEALTH.getAttributeName()));
			attributeModifiers.remove(Attributes.MAX_HEALTH.getAttributeName());
			if (playerIn.getHealth() > playerIn.getMaxHealth())
				playerIn.setHealth(playerIn.getMaxHealth());
		}
		if (speedAttributeModifier != null) {
			// playerIn.getAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).removeModifier(speedAttributeModifier.getID());
			playerIn.getAttribute(Attributes.MOVEMENT_SPEED)
					.removeModifier(attributeModifiers.get(Attributes.MOVEMENT_SPEED.getAttributeName()));
			attributeModifiers.remove(Attributes.MOVEMENT_SPEED.getAttributeName());
		}
		parentCapability.setAttributeModifiers(attributeModifiers, playerIn);
	}

	private void addCurrentModifiers(PlayerEntity playerIn) {
		HashMap<String, UUID> attributeModifiers = parentCapability.getAttributeModifiers();
		attributeModifiers.put(Attributes.MAX_HEALTH.getAttributeName(), healthAttributeModifier.getID());
		attributeModifiers.put(Attributes.MOVEMENT_SPEED.getAttributeName(), speedAttributeModifier.getID());
		parentCapability.setAttributeModifiers(attributeModifiers, playerIn);

		// TODO: Verificar si funciona y usar applyPersisentModifier para probar
		// también
		playerIn.getAttribute(Attributes.MAX_HEALTH).applyNonPersistentModifier(healthAttributeModifier);
		playerIn.getAttribute(Attributes.MOVEMENT_SPEED).applyNonPersistentModifier(speedAttributeModifier);
	}

	/**
	 * Must be called inside playerTickEvent. Client side
	 * 
	 * @param player
	 */
	@OnlyIn(Dist.CLIENT)
	public void playerTickEventAttack(final PlayerEntity player, LazyOptional<IBaseAuxiliarCapability> aux) {
		if (player.world.isRemote) {
			aux.ifPresent(auxiliarCapability -> {
				int points = parentCapability.getSkillsPoints().get(SkillEnum.TRANSFORM_WEREWOLF);
				float s = (15L - points * 0.5F);
				long attackTime = (long) s;
				LivingEntity target = null;
				if (player.ticksExisted % attackTime == 0L) {
					int distance = 1;
					double radius = 2;
					if (player != null) {
						List<LivingEntity> targetList = TargetUtils.acquireAllLookTargets(player, distance, radius)
								.stream().filter(x -> !(x instanceof TameableEntity) || !x.isOnSameTeam(player))
								.collect(Collectors.toList());

						target = targetList.stream().filter(x -> !x.equals(player.getLastAttackedEntity()))
								.min(Comparator.comparing(x->x.getPosition().distanceSq(player.getPosition())))
								.orElseGet(() -> targetList.stream().findAny().orElse(null));

						if (target != null) {
							if (targetList != null && !targetList.isEmpty()) {
								player.setLastAttackedEntity(target);
								Hand h = auxiliarCapability.isSwingingMainHand() ? Hand.MAIN_HAND : Hand.OFF_HAND;
								player.swingArm(h);
								auxiliarCapability.setSwingingMainHand(!auxiliarCapability.isSwingingMainHand(),
										player);
								ModNetwork.CHANNEL
										.sendToServer(new WerewolfAttackServerHandler(target.getEntityId(), h));
							}
						}
					}
				}

			});
		}
	}
}
