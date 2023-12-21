package com.chipoodle.devilrpg.network.handler;

import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record PlayerSkillTreeClientServerHandler(CompoundTag skillCompound) {

    public static void encode(final PlayerSkillTreeClientServerHandler msg, final FriendlyByteBuf packetBuffer) {
        packetBuffer.writeNbt(msg.skillCompound());
    }

    public static PlayerSkillTreeClientServerHandler decode(final FriendlyByteBuf packetBuffer) {
        return new PlayerSkillTreeClientServerHandler(packetBuffer.readNbt());
    }

    public static void onMessage(final PlayerSkillTreeClientServerHandler msg,
                                 final Supplier<NetworkEvent.Context> contextSupplier) {
        if (contextSupplier.get().getDirection().equals(NetworkDirection.PLAY_TO_SERVER)) {
            contextSupplier.get().enqueueWork(() -> {
                ServerPlayer serverPlayer = contextSupplier.get().getSender();
                if (serverPlayer != null) {
                    serverPlayer.getCapability(PlayerSkillCapability.INSTANCE)
                            .ifPresent(x -> x.deserializeNBT(msg.skillCompound()));
                }
            });
            contextSupplier.get().setPacketHandled(true);
        }

        if (contextSupplier.get().getDirection().equals(NetworkDirection.PLAY_TO_CLIENT)) {
            contextSupplier.get().enqueueWork(() -> {
                Minecraft m = Minecraft.getInstance();
                LocalPlayer clientPlayer = m.player;
                if (clientPlayer != null) {
                    clientPlayer.getCapability(PlayerSkillCapability.INSTANCE)
                            .ifPresent(x -> x.deserializeNBT(msg.skillCompound()));
                }
            });
            contextSupplier.get().setPacketHandled(true);
        }
    }
}
