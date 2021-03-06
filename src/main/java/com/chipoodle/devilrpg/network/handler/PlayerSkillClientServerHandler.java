package com.chipoodle.devilrpg.network.handler;

import java.util.function.Supplier;

import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityProvider;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkEvent;

public class PlayerSkillClientServerHandler {

	private final CompoundNBT skillCompound;

	public PlayerSkillClientServerHandler(CompoundNBT manaCompound) {
		this.skillCompound = manaCompound;
	}

	public CompoundNBT getSkillCompound() {
		return skillCompound;
	}

	public static void encode(final PlayerSkillClientServerHandler msg, final PacketBuffer packetBuffer) {
		packetBuffer.writeNbt(msg.getSkillCompound());
	}

	public static PlayerSkillClientServerHandler decode(final PacketBuffer packetBuffer) {
		return new PlayerSkillClientServerHandler(packetBuffer.readNbt());
	}

	public static void onMessage(final PlayerSkillClientServerHandler msg,
			final Supplier<NetworkEvent.Context> contextSupplier) {
		if (contextSupplier.get().getDirection().equals(NetworkDirection.PLAY_TO_SERVER)) {
			contextSupplier.get().enqueueWork(() -> {
				ServerPlayerEntity serverPlayer = contextSupplier.get().getSender();
				if (serverPlayer != null) {
					serverPlayer.getCapability(PlayerSkillCapabilityProvider.SKILL_CAP)
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
					clientPlayer.getCapability(PlayerSkillCapabilityProvider.SKILL_CAP)
							.ifPresent(x -> x.setNBTData(msg.getSkillCompound()));
				}
			});
			contextSupplier.get().setPacketHandled(true);
		}
	}
}
