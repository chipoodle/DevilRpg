package com.chipoodle.devilrpg.network.handler;

import com.chipoodle.devilrpg.capability.experience.PlayerExperienceCapability;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;


public class PlayerExperienceClientServerHandler {

    private final CompoundTag experienceCompound;

    public PlayerExperienceClientServerHandler(CompoundTag manaCompound) {
        this.experienceCompound = manaCompound;
    }

    public static void encode(final PlayerExperienceClientServerHandler msg, final FriendlyByteBuf packetBuffer) {
        packetBuffer.writeNbt(msg.getExperienceCompound());
    }

    public static PlayerExperienceClientServerHandler decode(final FriendlyByteBuf packetBuffer) {
        return new PlayerExperienceClientServerHandler(packetBuffer.readNbt());
    }

    public static void onMessage(final PlayerExperienceClientServerHandler msg,
                                 final Supplier<NetworkEvent.Context> contextSupplier) {
        if (contextSupplier.get().getDirection().equals(NetworkDirection.PLAY_TO_SERVER)) {
            contextSupplier.get().enqueueWork(() -> {
                ServerPlayer serverPlayer = contextSupplier.get().getSender();
                if (serverPlayer != null) {
                    serverPlayer.getCapability(PlayerExperienceCapability.INSTANCE)
                            .ifPresent(x -> x.deserializeNBT(msg.getExperienceCompound()));
                }
            });
            contextSupplier.get().setPacketHandled(true);
        }

        if (contextSupplier.get().getDirection().equals(NetworkDirection.PLAY_TO_CLIENT)) {
            contextSupplier.get().enqueueWork(() -> {
                Minecraft m = Minecraft.getInstance();
                LocalPlayer clientPlayer = m.player;
                if (clientPlayer != null) {
                    clientPlayer.getCapability(PlayerExperienceCapability.INSTANCE)
                            .ifPresent(x -> x.deserializeNBT(msg.getExperienceCompound()));
                }

            });
            contextSupplier.get().setPacketHandled(true);
        }
    }

    public CompoundTag getExperienceCompound() {
        return experienceCompound;
    }
}
