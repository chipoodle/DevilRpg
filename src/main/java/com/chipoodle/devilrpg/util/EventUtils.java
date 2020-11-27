package com.chipoodle.devilrpg.util;

import java.util.function.Consumer;

import com.chipoodle.devilrpg.capability.auxiliar.IBaseAuxiliarCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliarCapabilityProvider;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

public class EventUtils {

	/*public static void cancelSwing(PlayerEntity player, PlayerInteractEvent event) {
		LazyOptional<IBaseAuxiliarCapability> aux = player.getCapability(PlayerAuxiliarCapabilityProvider.AUX_CAP);
		if (aux == null || !aux.isPresent() || !aux.map(x -> x.isWerewolfTransformation()).orElse(true))
			return;
		boolean transformation = aux.map(x -> x.isWerewolfTransformation()).orElse(false);
		if (transformation) {
			player.isSwingInProgress = false;
			event.setCanceled(true);
		}
	}

	public static void cancelSwing(PlayerEntity player, AttackEntityEvent event) {
		LazyOptional<IBaseAuxiliarCapability> aux = player.getCapability(PlayerAuxiliarCapabilityProvider.AUX_CAP);
		if (aux == null || !aux.isPresent() || !aux.map(x -> x.isWerewolfTransformation()).orElse(true))
			return;
		boolean transformation = aux.map(x -> x.isWerewolfTransformation()).orElse(false);
		if (transformation) {
			player.isSwingInProgress = false;
			event.setCanceled(true);
		}

	}*/

	public static <T> boolean onTransformation(PlayerEntity player, Consumer<T> event, T t) {
		if (player != null) {
			LazyOptional<IBaseAuxiliarCapability> aux = player.getCapability(PlayerAuxiliarCapabilityProvider.AUX_CAP);
			if (aux == null || !aux.isPresent() || !aux.map(x -> x.isWerewolfTransformation()).orElse(true))
				return false;
			event.accept(t);
			return true;
		}
		return false;
	}

}
