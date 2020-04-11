package com.chipoodle.devilrpg.network.handler;

import java.util.function.Supplier;

import com.chipoodle.devilrpg.capability.experience.PlayerExperienceCapabilityProvider;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkEvent;

public class PlayerExperienceClientServerHandler {

	private final CompoundNBT experienceCompound;

	public PlayerExperienceClientServerHandler(CompoundNBT manaCompound) {
		this.experienceCompound = manaCompound;
	}

	public CompoundNBT getExperienceCompound() {
		return experienceCompound;
	}

	public static void encode(final PlayerExperienceClientServerHandler msg, final PacketBuffer packetBuffer) {
		packetBuffer.writeCompoundTag(msg.getExperienceCompound());
	}

	public static PlayerExperienceClientServerHandler decode(final PacketBuffer packetBuffer) {
		return new PlayerExperienceClientServerHandler(packetBuffer.readCompoundTag());
	}

	public static void onMessage(final PlayerExperienceClientServerHandler msg,
			final Supplier<NetworkEvent.Context> contextSupplier) {
		if (contextSupplier.get().getDirection().equals(NetworkDirection.PLAY_TO_SERVER)) {
			contextSupplier.get().enqueueWork(() -> {
				ServerPlayerEntity serverPlayer = contextSupplier.get().getSender();
				if (serverPlayer != null) {
					serverPlayer.getCapability(PlayerExperienceCapabilityProvider.EXPERIENCE_CAP)
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
					clientPlayer.getCapability(PlayerExperienceCapabilityProvider.EXPERIENCE_CAP)
							.ifPresent(x -> x.setNBTData(msg.getExperienceCompound()));
				}

			});
			contextSupplier.get().setPacketHandled(true);
		}
	}
}
