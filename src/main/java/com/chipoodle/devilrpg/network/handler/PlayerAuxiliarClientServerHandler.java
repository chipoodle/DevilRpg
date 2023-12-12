package com.chipoodle.devilrpg.network.handler;

import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record PlayerAuxiliarClientServerHandler(CompoundTag auxiliaryCompound) {

    public static void encode(final PlayerAuxiliarClientServerHandler msg, final FriendlyByteBuf packetBuffer) {
        packetBuffer.writeNbt(msg.auxiliaryCompound());
    }

    public static PlayerAuxiliarClientServerHandler decode(final FriendlyByteBuf packetBuffer) {
        return new PlayerAuxiliarClientServerHandler(packetBuffer.readNbt());
    }

    public static void onMessage(final PlayerAuxiliarClientServerHandler msg,
                                 final Supplier<NetworkEvent.Context> contextSupplier) {
        if (contextSupplier.get().getDirection().equals(NetworkDirection.PLAY_TO_SERVER)) {
            contextSupplier.get().enqueueWork(() -> {
                ServerPlayer serverPlayer = contextSupplier.get().getSender();
                if (serverPlayer != null) {
                    serverPlayer.getCapability(PlayerAuxiliaryCapability.INSTANCE)
                            .ifPresent(x -> x.deserializeNBT(msg.auxiliaryCompound()));
                }
            });
            contextSupplier.get().setPacketHandled(true);
        }

        if (contextSupplier.get().getDirection().equals(NetworkDirection.PLAY_TO_CLIENT)) {
            contextSupplier.get().enqueueWork(() -> {
                Minecraft m = Minecraft.getInstance();
                LocalPlayer clientPlayer = m.player;
                if (clientPlayer != null) {
                    clientPlayer.getCapability(PlayerAuxiliaryCapability.INSTANCE)
                            .ifPresent(x -> x.deserializeNBT(msg.auxiliaryCompound()));
                }

            });
            contextSupplier.get().setPacketHandled(true);
        }
    }
}
