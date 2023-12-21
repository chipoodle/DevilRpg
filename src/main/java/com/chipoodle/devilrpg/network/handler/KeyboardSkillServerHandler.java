package com.chipoodle.devilrpg.network.handler;

import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.util.PowerEnum;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;


import java.util.function.Supplier;

public record KeyboardSkillServerHandler(PowerEnum poder) {


	public static void encode(final KeyboardSkillServerHandler msg, final FriendlyByteBuf packetBuffer) {
		packetBuffer.writeUtf(msg.poder().name());
	}

	public static KeyboardSkillServerHandler decode(final FriendlyByteBuf packetBuffer) {
		return new KeyboardSkillServerHandler(PowerEnum.valueOf(packetBuffer.readUtf()));
	}

	public static void onMessage(final KeyboardSkillServerHandler msg, final Supplier<NetworkEvent.Context> contextSupplier) {
		if (contextSupplier.get().getDirection().equals(NetworkDirection.PLAY_TO_SERVER)) {
			contextSupplier.get().enqueueWork(() -> {
				ServerPlayer sender = contextSupplier.get().getSender(); // the client that sent this packet
				LazyOptional<PlayerSkillCapabilityInterface> skill = sender.getCapability(PlayerSkillCapability.INSTANCE);
				//sender.sendMessage(new StringTextComponent("KeyboardSkillServerHandler on msg:"+ msg.getPoder().name()+" Player ID: "+sender.getEntityId()));
				skill.ifPresent(x -> x.triggerAction(sender, msg.poder()));
			});
			contextSupplier.get().setPacketHandled(true);
		}
	}

}
