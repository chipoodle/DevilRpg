package com.chipoodle.devilrpg.network.handler;

import java.util.function.Supplier;

import com.chipoodle.devilrpg.capability.mana.PlayerManaCapabilityProvider;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkEvent;

public class PlayerManaClientServerHandler {

	private final CompoundNBT manaCompound;

	public PlayerManaClientServerHandler(CompoundNBT manaCompound) {
		this.manaCompound = manaCompound;
	}

	public CompoundNBT getManaCompound() {
		return manaCompound;
	}

	public static void encode(final PlayerManaClientServerHandler msg, final PacketBuffer packetBuffer) {
		packetBuffer.writeCompoundTag(msg.getManaCompound());
	}

	public static PlayerManaClientServerHandler decode(final PacketBuffer packetBuffer) {
		return new PlayerManaClientServerHandler(packetBuffer.readCompoundTag());
	}

	public static void onMessage(final PlayerManaClientServerHandler msg,
			final Supplier<NetworkEvent.Context> contextSupplier) {
		if (contextSupplier.get().getDirection().equals(NetworkDirection.PLAY_TO_SERVER)) {
			contextSupplier.get().enqueueWork(() -> /* DistExecutor.runWhenOn(Dist.DEDICATED_SERVER, () -> () -> */ {
				ServerPlayerEntity serverPlayer = contextSupplier.get().getSender();
				if (serverPlayer != null) {
					serverPlayer.getCapability(PlayerManaCapabilityProvider.MANA_CAP)
							.ifPresent(x -> x.setNBTData(msg.getManaCompound()));
				}
			});
			contextSupplier.get().setPacketHandled(true);
		}

		if (contextSupplier.get().getDirection().equals(NetworkDirection.PLAY_TO_CLIENT)) {
			contextSupplier.get().enqueueWork(() -> /* DistExecutor.runWhenOn(Dist.CLIENT, () -> () -> */ {
				Minecraft m = Minecraft.getInstance();
				PlayerEntity clientPlayer = m.player;
				if (clientPlayer != null) {
					clientPlayer.getCapability(PlayerManaCapabilityProvider.MANA_CAP)
							.ifPresent(x -> x.setNBTData(msg.getManaCompound()));
				}

			});
			contextSupplier.get().setPacketHandled(true);
		}
	}
}
