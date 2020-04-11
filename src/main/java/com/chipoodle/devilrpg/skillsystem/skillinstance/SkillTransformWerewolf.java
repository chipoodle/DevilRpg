package com.chipoodle.devilrpg.skillsystem.skillinstance;

import java.util.List;
import java.util.stream.Collectors;

import com.chipoodle.devilrpg.capability.auxiliar.IBaseAuxiliarCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliarCapabilityProvider;
import com.chipoodle.devilrpg.capability.skill.IBaseSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityProvider;
import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.handler.WerewolfAttackServerHandler;
import com.chipoodle.devilrpg.skillsystem.ISkillContainer;
import com.chipoodle.devilrpg.util.SkillEnum;
import com.chipoodle.devilrpg.util.TargetUtils;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;
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

		}
	}

	public void playerTickEventAttack(PlayerEntity player) {
		LazyOptional<IBaseAuxiliarCapability> aux = player.getCapability(PlayerAuxiliarCapabilityProvider.AUX_CAP);

		int points = parentCapability.getSkillsPoints().get(SkillEnum.TRANSFORM_WEREWOLF);
		long g = (long) (15L - points * 0.5F);

		boolean transformation = aux.map(x -> x.isWerewolfTransformation()).orElse(false);
		boolean attack = aux.map(x -> x.isWerewolfAttack()).orElse(false);
		//player.sendMessage(new StringTextComponent("transformation? " + transformation + " attack? " + attack + " interval? " + g));
		if (transformation && attack) {
			// event.player.isSwingInProgress = false;
			if (player.world.isRemote && player.ticksExisted % points == 0L) {
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
							Hand h = player.ticksExisted % points == 0L ? Hand.MAIN_HAND : Hand.OFF_HAND;
							player.swingArm(h);
							ModNetwork.CHANNEL.sendToServer(new WerewolfAttackServerHandler(target.getEntityId(), h));
						}
					}

				}
			}
		}
	}
}
