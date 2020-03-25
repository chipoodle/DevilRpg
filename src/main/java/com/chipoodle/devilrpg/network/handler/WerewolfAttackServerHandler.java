package com.chipoodle.devilrpg.network.handler;

import java.util.function.Supplier;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

public class WerewolfAttackServerHandler {

    private final int entityId;

    public WerewolfAttackServerHandler(int entityId) {
        this.entityId = entityId;
    }

    public int getEntityId() {
        return entityId;
    }

    public static void encode(final WerewolfAttackServerHandler msg, final PacketBuffer packetBuffer) {
        packetBuffer.writeInt(msg.getEntityId());
    }

    public static WerewolfAttackServerHandler decode(final PacketBuffer packetBuffer) {
        return new WerewolfAttackServerHandler(packetBuffer.readInt());
    }

    public static void onMessage(final WerewolfAttackServerHandler msg, final Supplier<NetworkEvent.Context> contextSupplier) {
        contextSupplier.get().enqueueWork(() -> {
            // Work that needs to be threadsafe (most work)
            ServerPlayerEntity sender = contextSupplier.get().getSender(); // the client that sent this packet
            Entity theEntity = sender.world.getEntityByID(msg.getEntityId());
            sender.attackTargetEntityWithCurrentItem(theEntity);
        });
        contextSupplier.get().setPacketHandled(true);
    }
}
