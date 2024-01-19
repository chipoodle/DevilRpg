package com.chipoodle.devilrpg.network.handler;

import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.skillsystem.AbstractSkillContainer;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Sends a message from the client to the server to execute the given Skill bypassing triggering functions
 * defined on the {@link com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityImplementation}  triggerAction method
 *
 */
public record DirectSkillExecutionServerHandler(SkillEnum skill) {


	public static void encode(final DirectSkillExecutionServerHandler msg, final FriendlyByteBuf packetBuffer) {
		packetBuffer.writeUtf(msg.skill().name());
	}

	public static DirectSkillExecutionServerHandler decode(final FriendlyByteBuf packetBuffer) {
		return new DirectSkillExecutionServerHandler(SkillEnum.valueOf(packetBuffer.readUtf()));
	}

	public static void onMessage(final DirectSkillExecutionServerHandler msg, final Supplier<NetworkEvent.Context> contextSupplier) {
		if (contextSupplier.get().getDirection().equals(NetworkDirection.PLAY_TO_SERVER)) {
			contextSupplier.get().enqueueWork(() -> {
				ServerPlayer sender = contextSupplier.get().getSender(); // the client that sent this packet
				//sender.sendMessage(new StringTextComponent("KeyboardSkillServerHandler on msg:"+ msg.getPoder().name()+" Player ID: "+sender.getEntityId()));
				PlayerSkillCapabilityInterface unwrappedPlayerCapability = IGenericCapability.getUnwrappedPlayerCapability(Objects.requireNonNull(sender), PlayerSkillCapability.INSTANCE);
				AbstractSkillContainer loadedSkillExecutor = unwrappedPlayerCapability.getLoadedSkillExecutor(msg.skill());
				loadedSkillExecutor.execute(sender.level,sender,null);

			});
			contextSupplier.get().setPacketHandled(true);
		}
	}

}
