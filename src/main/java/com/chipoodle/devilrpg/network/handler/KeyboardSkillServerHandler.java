package com.chipoodle.devilrpg.network.handler;

import static com.chipoodle.devilrpg.DevilRpg.LOGGER;

import java.util.function.Supplier;

import com.chipoodle.devilrpg.capability.skill.IBaseSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityProvider;
import com.chipoodle.devilrpg.util.PowerEnum;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkEvent;

public class KeyboardSkillServerHandler {
	private final PowerEnum poder;

	public KeyboardSkillServerHandler(PowerEnum poder) {
		this.poder = poder;
	}

	public PowerEnum getPoder() {
		return poder;
	}

	public static void encode(final KeyboardSkillServerHandler msg, final PacketBuffer packetBuffer) {
		packetBuffer.writeString(msg.getPoder().name());
	}

	public static KeyboardSkillServerHandler decode(final PacketBuffer packetBuffer) {
		return new KeyboardSkillServerHandler(PowerEnum.valueOf(packetBuffer.readString()));
	}

	public static void onMessage(final KeyboardSkillServerHandler msg,
			final Supplier<NetworkEvent.Context> contextSupplier) {
		if (contextSupplier.get().getDirection().equals(NetworkDirection.PLAY_TO_SERVER)) {
			contextSupplier.get().enqueueWork(() -> {
				ServerPlayerEntity sender = contextSupplier.get().getSender(); // the client that sent this packet
				LazyOptional<IBaseSkillCapability> skill = sender.getCapability(PlayerSkillCapabilityProvider.SKILL_CAP);
				//sender.sendMessage(new StringTextComponent("KeyboardSkillServerHandler on msg:"+ msg.getPoder().name()+" Player ID: "+sender.getEntityId()));
				skill.ifPresent(x->x.triggerAction(sender, msg.getPoder()));
			});
			contextSupplier.get().setPacketHandled(true);
		}
	}
}
