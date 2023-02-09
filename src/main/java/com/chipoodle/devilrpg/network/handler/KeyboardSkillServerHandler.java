package com.chipoodle.devilrpg.network.handler;

import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityAttacher;
import com.chipoodle.devilrpg.util.PowerEnum;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;


import java.util.function.Supplier;

public class KeyboardSkillServerHandler {
	private final PowerEnum poder;

	public KeyboardSkillServerHandler(PowerEnum poder) {
		this.poder = poder;
	}

	public PowerEnum getPoder() {
		return poder;
	}

	public static void encode(final KeyboardSkillServerHandler msg, final FriendlyByteBuf packetBuffer) {
		packetBuffer.writeUtf(msg.getPoder().name());
	}

	public static KeyboardSkillServerHandler decode(final FriendlyByteBuf packetBuffer) {
		return new KeyboardSkillServerHandler(PowerEnum.valueOf(packetBuffer.readUtf()));
	}

	public static void onMessage(final KeyboardSkillServerHandler msg,
			final Supplier<NetworkEvent.Context> contextSupplier) {
		if (contextSupplier.get().getDirection().equals(NetworkDirection.PLAY_TO_SERVER)) {
			contextSupplier.get().enqueueWork(() -> {
				ServerPlayer sender = contextSupplier.get().getSender(); // the client that sent this packet
				LazyOptional<PlayerSkillCapabilityInterface> skill = sender.getCapability(PlayerSkillCapabilityAttacher.SKILL_CAP);
				//sender.sendMessage(new StringTextComponent("KeyboardSkillServerHandler on msg:"+ msg.getPoder().name()+" Player ID: "+sender.getEntityId()));
				skill.ifPresent(x->x.triggerAction(sender, msg.getPoder()));
			});
			contextSupplier.get().setPacketHandled(true);
		}
	}

}
