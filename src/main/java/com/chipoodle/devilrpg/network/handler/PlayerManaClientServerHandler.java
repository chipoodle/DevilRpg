package com.chipoodle.devilrpg.network.handler;

import java.util.function.Supplier;

import com.chipoodle.devilrpg.capability.mana.PlayerManaCapabilityAttacher;

import net.minecraft.client.Minecraft;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

public class PlayerManaClientServerHandler {

	private final CompoundTag manaCompound;

	public PlayerManaClientServerHandler(CompoundTag manaCompound) {
		this.manaCompound = manaCompound;
	}

	public CompoundTag getManaCompound() {
		return manaCompound;
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
					serverPlayer.getCapability(PlayerManaCapabilityAttacher.MANA_CAP)
							.ifPresent(x -> x.setNBTData(msg.getManaCompound()));
					//serverPlayer.sendMessage(new StringTextComponent("PlayerManaClientServerHandler Server side compound:"+ msg.getManaCompound().getFloat("mana")+" Player ID: "+serverPlayer.getEntityId()));
				}
			});
			contextSupplier.get().setPacketHandled(true);
		}

		if (contextSupplier.get().getDirection().equals(NetworkDirection.PLAY_TO_CLIENT)) {
			contextSupplier.get().enqueueWork(() ->  {
				Minecraft m = Minecraft.getInstance();
				LocalPlayer clientPlayer = m.player;
				if (clientPlayer != null) {
					clientPlayer.getCapability(PlayerManaCapabilityAttacher.MANA_CAP)
							.ifPresent(x -> x.setNBTData(msg.getManaCompound()));
					//clientPlayer.sendMessage(new StringTextComponent("PlayerManaClientServerHandler Client side compound:"+ msg.getManaCompound().getFloat("mana")+" Player ID: "+clientPlayer.getEntityId()));
				}

			});
			contextSupplier.get().setPacketHandled(true);
		}
	}
}
