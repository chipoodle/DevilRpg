package com.chipoodle.devilrpg.network.handler;

import java.util.function.Supplier;

import com.chipoodle.devilrpg.capability.experience.PlayerExperienceCapabilityAttacher;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkEvent;

public class PlayerExperienceClientServerHandler {

	private final CompoundTag experienceCompound;

	public PlayerExperienceClientServerHandler(CompoundTag manaCompound) {
		this.experienceCompound = manaCompound;
	}

	public CompoundTag getExperienceCompound() {
		return experienceCompound;
	}

	public static void encode(final PlayerExperienceClientServerHandler msg, final PacketBuffer packetBuffer) {
		packetBuffer.writeNbt(msg.getExperienceCompound());
	}

	public static PlayerExperienceClientServerHandler decode(final PacketBuffer packetBuffer) {
		return new PlayerExperienceClientServerHandler(packetBuffer.readNbt());
	}

	public static void onMessage(final PlayerExperienceClientServerHandler msg,
			final Supplier<NetworkEvent.Context> contextSupplier) {
		if (contextSupplier.get().getDirection().equals(NetworkDirection.PLAY_TO_SERVER)) {
			contextSupplier.get().enqueueWork(() -> {
				ServerPlayerEntity serverPlayer = contextSupplier.get().getSender();
				if (serverPlayer != null) {
					serverPlayer.getCapability(PlayerExperienceCapabilityAttacher.EXPERIENCE_CAP)
							.ifPresent(x -> x.setNBTData(msg.getExperienceCompound()));
				}
			});
			contextSupplier.get().setPacketHandled(true);
		}

		if (contextSupplier.get().getDirection().equals(NetworkDirection.PLAY_TO_CLIENT)) {
			contextSupplier.get().enqueueWork(() -> {
				Minecraft m = Minecraft.getInstance();
				PlayerEntity clientPlayer = m.player;
				if (clientPlayer != null) {
					clientPlayer.getCapability(PlayerExperienceCapabilityAttacher.EXPERIENCE_CAP)
							.ifPresent(x -> x.setNBTData(msg.getExperienceCompound()));
				}

			});
			contextSupplier.get().setPacketHandled(true);
		}
	}
}
