package com.chipoodle.devilrpg.network.handler;

import java.util.function.Supplier;

import com.chipoodle.devilrpg.util.TargetUtils;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.Hand;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkEvent;

public class WerewolfAttackServerHandler {

	public static final String ENTITY_ID_KEY = "entityId";
	public static final String HAND_KEY = "hand";
	private final int entityId;
	private final Hand hand;

	public WerewolfAttackServerHandler(int entityId, Hand hand) {
		this.entityId = entityId;
		this.hand = hand;
	}

	public int getEntityId() {
		return entityId;
	}

	public Hand getHand() {
		return hand;
	}

	public static void encode(final WerewolfAttackServerHandler msg, final PacketBuffer packetBuffer) {
		CompoundNBT c = new CompoundNBT();
		c.putInt(ENTITY_ID_KEY, msg.getEntityId());
		c.putString(HAND_KEY, msg.getHand().name());
		packetBuffer.writeCompoundTag(c);
	}

	public static WerewolfAttackServerHandler decode(final PacketBuffer packetBuffer) {
		CompoundNBT c = packetBuffer.readCompoundTag();
		return new WerewolfAttackServerHandler(c.getInt(ENTITY_ID_KEY), Hand.valueOf(c.getString(HAND_KEY)));
	}

	public static void onMessage(final WerewolfAttackServerHandler msg,
			final Supplier<NetworkEvent.Context> contextSupplier) {
		if (contextSupplier.get().getDirection().equals(NetworkDirection.PLAY_TO_SERVER)) {
			contextSupplier.get().enqueueWork(() -> {
				ServerPlayerEntity sender = contextSupplier.get().getSender();
				Entity target = sender.world.getEntityByID(msg.getEntityId());
				ItemStack item = sender.getHeldItem(msg.getHand());	
				TargetUtils.attackTargetEntityWithItem(sender, target, item);
				//DevilRpg.LOGGER.info("----->HAND: " + msg.getHand().name());
			});
			contextSupplier.get().setPacketHandled(true);
		}
	}
}
