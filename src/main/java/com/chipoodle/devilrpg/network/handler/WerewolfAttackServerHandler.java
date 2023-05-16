package com.chipoodle.devilrpg.network.handler;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.util.TargetUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class WerewolfAttackServerHandler {

    public static final String ENTITY_ID_KEY = "entityId";
    public static final String HAND_KEY = "hand";
    private final int entityId;
    private final InteractionHand hand;

    public WerewolfAttackServerHandler(int entityId, InteractionHand hand) {
        this.entityId = entityId;
        this.hand = hand;
    }

    public static void encode(final WerewolfAttackServerHandler msg, final FriendlyByteBuf packetBuffer) {
        CompoundTag c = new CompoundTag();
        c.putInt(ENTITY_ID_KEY, msg.getEntityId());
        c.putString(HAND_KEY, msg.getInteractionHand().name());
        packetBuffer.writeNbt(c);
    }

    public static WerewolfAttackServerHandler decode(final FriendlyByteBuf packetBuffer) {
        CompoundTag c = packetBuffer.readNbt();
        return new WerewolfAttackServerHandler(c.getInt(ENTITY_ID_KEY), InteractionHand.valueOf(c.getString(HAND_KEY)));
    }

    public static void onMessage(final WerewolfAttackServerHandler msg,
                                 final Supplier<NetworkEvent.Context> contextSupplier) {
        if (contextSupplier.get().getDirection().equals(NetworkDirection.PLAY_TO_SERVER)) {
            contextSupplier.get().enqueueWork(() -> {
                ServerPlayer sender = contextSupplier.get().getSender();
                Entity target = sender.level.getEntity(msg.getEntityId());
                DevilRpg.LOGGER.info("Server recieved attack to entity: {}", target.getName());
                //ItemStack item = sender.getHeldItem(msg.getInteractionHand());
                TargetUtils.attackTargetEntityWithItemHand(sender, target, msg.getInteractionHand());
                //DevilRpg.LOGGER.info("----->HAND: " + msg.getInteractionHand().name());
            });
            contextSupplier.get().setPacketHandled(true);
        }
    }

    public int getEntityId() {
        return entityId;
    }

    public InteractionHand getInteractionHand() {
        return hand;
    }
}
