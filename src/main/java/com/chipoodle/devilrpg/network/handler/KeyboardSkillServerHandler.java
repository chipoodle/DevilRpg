package com.chipoodle.devilrpg.network.handler;

import static com.chipoodle.devilrpg.DevilRpg.LOGGER;

import java.util.function.Supplier;

import com.chipoodle.devilrpg.skillsystem.ServerSkillTrigger;
import com.chipoodle.devilrpg.util.ConstantesPower;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkEvent;

public class KeyboardSkillServerHandler {
	private final ConstantesPower poder;

	public KeyboardSkillServerHandler(ConstantesPower poder) {
		this.poder = poder;
	}

	public ConstantesPower getPoder() {
		return poder;
	}

	public static void encode(final KeyboardSkillServerHandler msg, final PacketBuffer packetBuffer) {
		packetBuffer.writeString(msg.getPoder().name());
	}

	public static KeyboardSkillServerHandler decode(final PacketBuffer packetBuffer) {
		return new KeyboardSkillServerHandler(ConstantesPower.valueOf(packetBuffer.readString()));
	}

	public static void onMessage(final KeyboardSkillServerHandler msg,
			final Supplier<NetworkEvent.Context> contextSupplier) {
		if (contextSupplier.get().getDirection().equals(NetworkDirection.PLAY_TO_SERVER)) {
			contextSupplier.get().enqueueWork(() -> {
				ServerPlayerEntity sender = contextSupplier.get().getSender(); // the client that sent this packet
				LOGGER.info("keypress message recieved. User: " + sender.getScoreboardName());
				ServerSkillTrigger.SKILL_TRIGGER.triggerAction(sender, msg.getPoder());
			});
			contextSupplier.get().setPacketHandled(true);
		}
	}
}
