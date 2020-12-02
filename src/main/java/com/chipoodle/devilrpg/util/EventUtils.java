package com.chipoodle.devilrpg.util;

import java.util.function.BiConsumer;

import com.chipoodle.devilrpg.capability.auxiliar.IBaseAuxiliarCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliarCapabilityProvider;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraftforge.common.util.LazyOptional;

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

	public static <T,U extends LazyOptional<IBaseAuxiliarCapability>> boolean onTransformation(PlayerEntity player, BiConsumer<T,U> event, T t) {
		if (player != null) {
			U aux = (U) player.getCapability(PlayerAuxiliarCapabilityProvider.AUX_CAP);
			if (aux == null || !aux.isPresent() || !aux.map(x -> x.isWerewolfTransformation()).orElse(true))
				return false;
			event.accept(t,aux);
			return true;
		}
		return false;
	}

}
