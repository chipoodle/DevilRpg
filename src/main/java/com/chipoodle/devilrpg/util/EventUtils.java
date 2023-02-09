package com.chipoodle.devilrpg.util;

import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import java.util.function.BiConsumer;

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

    @SuppressWarnings("unchecked")
    public static <T, U extends LazyOptional<PlayerAuxiliaryCapabilityInterface>> boolean onWerewolfTransformation(Player player, BiConsumer<T, U> typedFunctionToExcecute, T event) {
        if (player != null) {
            U aux = (U) player.getCapability(PlayerAuxiliaryCapability.AUX_CAP);
            if (!aux.isPresent() || !aux.map(PlayerAuxiliaryCapabilityInterface::isWerewolfTransformation).orElse(true))
                return false;
            typedFunctionToExcecute.accept(event, aux);
            return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Player, U extends LazyOptional<? extends IGenericCapability>> void onJoin(T player, BiConsumer<T, U> typedFunctionToExecute, Capability<?> cap) {
        Minecraft mainThread = Minecraft.getInstance();
        if (player != null && !player.level.isClientSide) {
            U capabilityInstance = (U) player.getCapability(cap);
            mainThread.tell(() -> typedFunctionToExecute.accept(player, capabilityInstance));
        }
    }
}
