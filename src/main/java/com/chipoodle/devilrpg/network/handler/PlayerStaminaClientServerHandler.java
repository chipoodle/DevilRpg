package com.chipoodle.devilrpg.network.handler;

import com.chipoodle.devilrpg.capability.stamina.PlayerStaminaCapability;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record PlayerStaminaClientServerHandler(CompoundTag staminaCompound) {

    public static void encode(final PlayerStaminaClientServerHandler msg, final FriendlyByteBuf packetBuffer) {
        packetBuffer.writeNbt(msg.staminaCompound());
    }

    public static PlayerStaminaClientServerHandler decode(final FriendlyByteBuf packetBuffer) {
        return new PlayerStaminaClientServerHandler(packetBuffer.readNbt());
    }

    public static void onMessage(final PlayerStaminaClientServerHandler msg,
                                 final Supplier<NetworkEvent.Context> contextSupplier) {
        if (contextSupplier.get().getDirection().equals(NetworkDirection.PLAY_TO_SERVER)) {
            contextSupplier.get().enqueueWork(() -> {
                ServerPlayer serverPlayer = contextSupplier.get().getSender();
                if (serverPlayer != null) {
                    serverPlayer.getCapability(PlayerStaminaCapability.INSTANCE)
                            .ifPresent(x -> x.deserializeNBT(msg.staminaCompound()));
                    //serverPlayer.sendMessage(new StringTextComponent("PlayerStaminaClientServerHandler Server side compound:"+ msg.getStaminaCompound().getFloat("stamina")+" Player ID: "+serverPlayer.getEntityId()));
                }
            });
            contextSupplier.get().setPacketHandled(true);
        }

        if (contextSupplier.get().getDirection().equals(NetworkDirection.PLAY_TO_CLIENT)) {
            contextSupplier.get().enqueueWork(() -> {
                Minecraft m = Minecraft.getInstance();
                LocalPlayer clientPlayer = m.player;
                if (clientPlayer != null) {
                    clientPlayer.getCapability(PlayerStaminaCapability.INSTANCE)
                            .ifPresent(x -> x.deserializeNBT(msg.staminaCompound()));
                    //clientPlayer.sendMessage(new StringTextComponent("PlayerStaminaClientServerHandler Client side compound:"+ msg.getStaminaCompound().getFloat("stamina")+" Player ID: "+clientPlayer.getEntityId()));
                }

            });
            contextSupplier.get().setPacketHandled(true);
        }
    }
}
