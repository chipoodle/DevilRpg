package com.chipoodle.devilrpg.util;

import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class EventUtils {

    public static <T> boolean onWerewolfTransformation(Player player, BiConsumer<T, PlayerAuxiliaryCapabilityInterface> typedFunctionToExecute, T event) {
        if (player != null) {
            PlayerAuxiliaryCapabilityInterface aux = player.getData(PlayerAuxiliaryCapability.INSTANCE);
            if (aux == null || !aux.isWerewolfTransformation())
                return false;
            typedFunctionToExecute.accept(event, aux);
            return true;
        }
        return false;
    }

    public static <T extends Player, U extends IGenericCapability> void onJoin(T player, BiConsumer<T, U> typedFunctionToExecute, Supplier<AttachmentType<U>> cap) {
        Minecraft mainThread = Minecraft.getInstance();
        if (player != null && !player.level().isClientSide) {
            U capabilityInstance = player.getData(cap);
            if (mainThread != null) {
                mainThread.tell(() -> typedFunctionToExecute.accept(player, capabilityInstance));
            } else {
                typedFunctionToExecute.accept(player, capabilityInstance);
            }
        }
    }
}
