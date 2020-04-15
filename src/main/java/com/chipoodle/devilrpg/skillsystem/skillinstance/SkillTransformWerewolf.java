package com.chipoodle.devilrpg.skillsystem.skillinstance;

import java.util.List;
import java.util.stream.Collectors;

import com.chipoodle.devilrpg.capability.auxiliar.IBaseAuxiliarCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliarCapabilityProvider;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.handler.WerewolfAttackServerHandler;
import com.chipoodle.devilrpg.skillsystem.ISkillContainer;
import com.chipoodle.devilrpg.util.SkillEnum;
import com.chipoodle.devilrpg.util.TargetUtils;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.util.LazyOptional;

public class SkillTransformWerewolf implements ISkillContainer {
	private PlayerSkillCapability parentCapability;
	private LazyOptional<IBaseAuxiliarCapability> aux;

	public SkillTransformWerewolf(PlayerSkillCapability parentCapability) {
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
			if(!transformation) {
				
			}
			
			
		}
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
