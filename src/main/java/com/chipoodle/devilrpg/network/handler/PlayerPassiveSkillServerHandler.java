package com.chipoodle.devilrpg.network.handler;

import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityAttacher;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class PlayerPassiveSkillServerHandler {
	private final CompoundNBT skillCompound;

	public PlayerPassiveSkillServerHandler(CompoundNBT skillCompound) {
		this.skillCompound = skillCompound;
	}

	public CompoundNBT getSkillCompound() {
		return skillCompound;
	}

	public static void encode(final PlayerPassiveSkillServerHandler msg, final PacketBuffer packetBuffer) {
		packetBuffer.writeNbt(msg.getSkillCompound());
	}

	public static PlayerPassiveSkillServerHandler decode(final PacketBuffer packetBuffer) {
		return new PlayerPassiveSkillServerHandler(packetBuffer.readNbt());
	}

	public static void onMessage(final PlayerPassiveSkillServerHandler msg,
			final Supplier<NetworkEvent.Context> contextSupplier) {
		if (contextSupplier.get().getDirection().equals(NetworkDirection.PLAY_TO_SERVER)) {
			contextSupplier.get().enqueueWork(() -> {
				ServerPlayerEntity sender = contextSupplier.get().getSender(); // the client that sent this packet
				LazyOptional<PlayerSkillCapabilityInterface> skill = sender.getCapability(PlayerSkillCapabilityAttacher.SKILL_CAP);
				//sender.sendMessage(new StringTextComponent("KeyboardSkillServerHandler on msg:"+ msg.getPoder().name()+" Player ID: "+sender.getEntityId()));
				skill.ifPresent(x->x.triggerPassive(sender, msg.getSkillCompound()));
			});
			contextSupplier.get().setPacketHandled(true);
		}
	}
}
