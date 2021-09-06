package com.chipoodle.devilrpg.network.handler;

import java.util.function.Supplier;

import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliarCapabilityProvider;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkEvent;

public class PlayerAuxiliarClientServerHandler {

	private final CompoundNBT auxiliarCompound;

	public PlayerAuxiliarClientServerHandler(CompoundNBT manaCompound) {
		this.auxiliarCompound = manaCompound;
	}

	public CompoundNBT getAuxiliarCompound() {
		return auxiliarCompound;
	}

	public static void encode(final PlayerAuxiliarClientServerHandler msg, final PacketBuffer packetBuffer) {
		packetBuffer.writeNbt(msg.getAuxiliarCompound());
	}

	public static PlayerAuxiliarClientServerHandler decode(final PacketBuffer packetBuffer) {
		return new PlayerAuxiliarClientServerHandler(packetBuffer.readNbt());
	}

	public static void onMessage(final PlayerAuxiliarClientServerHandler msg,
			final Supplier<NetworkEvent.Context> contextSupplier) {
		if (contextSupplier.get().getDirection().equals(NetworkDirection.PLAY_TO_SERVER)) {
			contextSupplier.get().enqueueWork(() ->  {
				ServerPlayerEntity serverPlayer = contextSupplier.get().getSender();
				if (serverPlayer != null) {
					serverPlayer.getCapability(PlayerAuxiliarCapabilityProvider.AUX_CAP)
							.ifPresent(x -> x.setNBTData(msg.getAuxiliarCompound()));
				}
			});
			contextSupplier.get().setPacketHandled(true);
		}

		if (contextSupplier.get().getDirection().equals(NetworkDirection.PLAY_TO_CLIENT)) {
			contextSupplier.get().enqueueWork(() ->  {
				Minecraft m = Minecraft.getInstance();
				PlayerEntity clientPlayer = m.player;
				if (clientPlayer != null) {
					clientPlayer.getCapability(PlayerAuxiliarCapabilityProvider.AUX_CAP)
							.ifPresent(x -> x.setNBTData(msg.getAuxiliarCompound()));
				}

			});
			contextSupplier.get().setPacketHandled(true);
		}
	}
}
