package com.chipoodle.devilrpg.network.handler;

import java.util.function.Supplier;

import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapability;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapabilityAttacher;

import net.minecraft.client.Minecraft;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;


public class PlayerMinionClientServerHandler {

	private final CompoundTag skillCompound;

	public PlayerMinionClientServerHandler(CompoundTag skillCompound) {
		this.skillCompound = skillCompound;
	}

	public CompoundTag getSkillCompound() {
		return skillCompound;
	}

	public static void encode(final PlayerMinionClientServerHandler msg, final FriendlyByteBuf packetBuffer) {
		packetBuffer.writeNbt(msg.getSkillCompound());
	}

	public static PlayerMinionClientServerHandler decode(final FriendlyByteBuf packetBuffer) {
		return new PlayerMinionClientServerHandler(packetBuffer.readNbt());
	}

	public static void onMessage(final PlayerMinionClientServerHandler msg,
			final Supplier<NetworkEvent.Context> contextSupplier) {
		if (contextSupplier.get().getDirection().equals(NetworkDirection.PLAY_TO_SERVER)) {
			contextSupplier.get().enqueueWork(() -> {
				ServerPlayer serverPlayer = contextSupplier.get().getSender();
				if (serverPlayer != null) {
					serverPlayer.getCapability(PlayerMinionCapability.INSTANCE)
							.ifPresent(x -> x.setNBTData(msg.getSkillCompound()));
				}
			});
			contextSupplier.get().setPacketHandled(true);
		}

		if (contextSupplier.get().getDirection().equals(NetworkDirection.PLAY_TO_CLIENT)) {
			contextSupplier.get().enqueueWork(() -> {
				Minecraft m = Minecraft.getInstance();
				PlayerEntity clientPlayer = m.player;
				if (clientPlayer != null) {
					clientPlayer.getCapability(PlayerMinionCapabilityAttacher.MINION_CAP)
							.ifPresent(x -> x.setNBTData(msg.getSkillCompound()));
				}
			});
			contextSupplier.get().setPacketHandled(true);
		}
	}
}
