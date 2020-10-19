package com.chipoodle.devilrpg.skillsystem.skillinstance;

import java.util.HashMap;
import java.util.List;
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
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundEvents;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.util.LazyOptional;

public class SkillShapeshiftWerewolf implements ISkillContainer {
	private PlayerSkillCapability parentCapability;
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
			//playerIn.getAttribute(SharedMonsterAttributes.MAX_HEALTH).removeModifier(healthAttributeModifier.getID());
			playerIn.getAttribute(SharedMonsterAttributes.MAX_HEALTH).removeModifier(attributeModifiers.get(SharedMonsterAttributes.MAX_HEALTH.getName()));
			attributeModifiers.remove(SharedMonsterAttributes.MAX_HEALTH.getName());
			if(playerIn.getHealth() > playerIn.getMaxHealth())
				playerIn.setHealth(playerIn.getMaxHealth());
		}
		if (speedAttributeModifier != null) {
			//playerIn.getAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).removeModifier(speedAttributeModifier.getID());
			playerIn.getAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).removeModifier(attributeModifiers.get(SharedMonsterAttributes.MOVEMENT_SPEED.getName()));
			attributeModifiers.remove(SharedMonsterAttributes.MOVEMENT_SPEED.getName());
		}
		parentCapability.setAttributeModifiers(attributeModifiers, playerIn);
	}

	private void addCurrentModifiers(PlayerEntity playerIn) {
		HashMap<String, UUID> attributeModifiers = parentCapability.getAttributeModifiers();
		attributeModifiers.put(SharedMonsterAttributes.MAX_HEALTH.getName(), healthAttributeModifier.getID());
		attributeModifiers.put(SharedMonsterAttributes.MOVEMENT_SPEED.getName(), speedAttributeModifier.getID());
		parentCapability.setAttributeModifiers(attributeModifiers, playerIn);

		playerIn.getAttribute(SharedMonsterAttributes.MAX_HEALTH).applyModifier(healthAttributeModifier);
		playerIn.getAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).applyModifier(speedAttributeModifier);
	}

	/**
	 * Must be called inside playerTickEvent. Client side
	 * 
	 * @param player
	 */
	@OnlyIn(Dist.CLIENT)
	public void playerTickEventAttack(PlayerEntity player, LazyOptional<IBaseAuxiliarCapability> aux) {
		if (player.world.isRemote) {
			int points = parentCapability.getSkillsPoints().get(SkillEnum.TRANSFORM_WEREWOLF);
			float s = (15L - points * 0.5F);
			long attackTime = (long) s;
			if (player.ticksExisted % attackTime == 0L) {
				int distance = 1;
				double radius = 2;
				if (player != null) {
					List<LivingEntity> targetList = TargetUtils.acquireAllLookTargets(player, distance, radius).stream()
							.filter(x -> !(x instanceof TameableEntity) || !x.isOnSameTeam(player))
							.collect(Collectors.toList());

					LivingEntity target = targetList.stream().filter(x -> !x.equals(player.getLastAttackedEntity()))
							.findAny().orElseGet(() -> targetList.stream().findAny().orElse(null));

					if (target != null) {
						if (targetList != null && !targetList.isEmpty()) {
							player.setLastAttackedEntity(target);
							aux.ifPresent(x -> {
								Hand h = x.isSwingingMainHand() ? Hand.MAIN_HAND : Hand.OFF_HAND;
								player.swingArm(h);
								x.setSwingingMainHand(!x.isSwingingMainHand(), player);
								ModNetwork.CHANNEL
										.sendToServer(new WerewolfAttackServerHandler(target.getEntityId(), h));
							});
						}
					}

				}

			}
		}
	}
}
