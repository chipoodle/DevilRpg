package com.chipoodle.devilrpg.network.handler;

import com.chipoodle.devilrpg.capability.skill.IBasePlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityProvider;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class PassiveSkillServerHandler {
	private final CompoundNBT skillCompound;

	public PassiveSkillServerHandler(CompoundNBT skillCompound) {
		this.skillCompound = skillCompound;
	}

	public CompoundNBT getSkillCompound() {
		return skillCompound;
	}

	public static void encode(final PassiveSkillServerHandler msg, final PacketBuffer packetBuffer) {
		packetBuffer.writeNbt(msg.getSkillCompound());
	}

	public static PassiveSkillServerHandler decode(final PacketBuffer packetBuffer) {
		return new PassiveSkillServerHandler(packetBuffer.readNbt());
	}

	public static void onMessage(final PassiveSkillServerHandler msg,
			final Supplier<NetworkEvent.Context> contextSupplier) {
		if (contextSupplier.get().getDirection().equals(NetworkDirection.PLAY_TO_SERVER)) {
			contextSupplier.get().enqueueWork(() -> {
				ServerPlayerEntity sender = contextSupplier.get().getSender(); // the client that sent this packet
				LazyOptional<IBasePlayerSkillCapability> skill = sender.getCapability(PlayerSkillCapabilityProvider.SKILL_CAP);
				//sender.sendMessage(new StringTextComponent("KeyboardSkillServerHandler on msg:"+ msg.getPoder().name()+" Player ID: "+sender.getEntityId()));
				skill.ifPresent(x->x.triggerPassive(sender, msg.getSkillCompound()));
			});
			contextSupplier.get().setPacketHandled(true);
		}
	}
}
