package com.chipoodle.devilrpg.network.handler;

import com.chipoodle.devilrpg.capability.mana.PlayerManaCapability;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PlayerManaClientServerHandler {

    private final CompoundTag manaCompound;

    public PlayerManaClientServerHandler(CompoundTag manaCompound) {
        this.manaCompound = manaCompound;
    }

    public static void encode(final PlayerManaClientServerHandler msg, final FriendlyByteBuf packetBuffer) {
        packetBuffer.writeNbt(msg.getManaCompound());
    }

    public static PlayerManaClientServerHandler decode(final FriendlyByteBuf packetBuffer) {
        return new PlayerManaClientServerHandler(packetBuffer.readNbt());
    }

    public static void onMessage(final PlayerManaClientServerHandler msg,
                                 final Supplier<NetworkEvent.Context> contextSupplier) {
        if (contextSupplier.get().getDirection().equals(NetworkDirection.PLAY_TO_SERVER)) {
            contextSupplier.get().enqueueWork(() -> {
                ServerPlayer serverPlayer = contextSupplier.get().getSender();
                if (serverPlayer != null) {
                    serverPlayer.getCapability(PlayerManaCapability.INSTANCE)
                            .ifPresent(x -> x.deserializeNBT(msg.getManaCompound()));
                    //serverPlayer.sendMessage(new StringTextComponent("PlayerManaClientServerHandler Server side compound:"+ msg.getManaCompound().getFloat("mana")+" Player ID: "+serverPlayer.getEntityId()));
                }
            });
            contextSupplier.get().setPacketHandled(true);
        }

        if (contextSupplier.get().getDirection().equals(NetworkDirection.PLAY_TO_CLIENT)) {
            contextSupplier.get().enqueueWork(() -> {
                Minecraft m = Minecraft.getInstance();
                LocalPlayer clientPlayer = m.player;
                if (clientPlayer != null) {
                    clientPlayer.getCapability(PlayerManaCapability.INSTANCE)
                            .ifPresent(x -> x.deserializeNBT(msg.getManaCompound()));
                    //clientPlayer.sendMessage(new StringTextComponent("PlayerManaClientServerHandler Client side compound:"+ msg.getManaCompound().getFloat("mana")+" Player ID: "+clientPlayer.getEntityId()));
                }

            });
            contextSupplier.get().setPacketHandled(true);
        }
    }

    public CompoundTag getManaCompound() {
        return manaCompound;
    }
}
