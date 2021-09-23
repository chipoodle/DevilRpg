package com.chipoodle.devilrpg.network.handler;

import java.util.UUID;
import java.util.function.Supplier;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.util.TargetUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.potion.EffectInstance;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkEvent;

public class PotionClientServerHandler {
	private static final String POTION_EXPIRY_EVENT = "PotionExpiryEvent";
	private static final String POTION_ADDED_EVENT = "PotionAddedEvent";
	public static final String MINION_ID_KEY = "entityId";
	public static final String EFFECT_EVENT_TYPE = "effectEventType";
	private final CompoundNBT compound;

	public PotionClientServerHandler(CompoundNBT compound) {
		this.compound = compound;
	}

	public CompoundNBT getCompound() {
		return compound;
	}

	public static void encode(final PotionClientServerHandler msg, final PacketBuffer packetBuffer) {
		packetBuffer.writeNbt(msg.getCompound());
	}

	public static PotionClientServerHandler decode(final PacketBuffer packetBuffer) {
		return new PotionClientServerHandler(packetBuffer.readNbt());
	}

	public static void onMessage(final PotionClientServerHandler msg,
			final Supplier<NetworkEvent.Context> contextSupplier) {
		

		if (contextSupplier.get().getDirection().equals(NetworkDirection.PLAY_TO_CLIENT)) {
			contextSupplier.get().enqueueWork(() -> {
				Minecraft m = Minecraft.getInstance();
				PlayerEntity clientPlayer = m.player;
				if (clientPlayer != null) {
					EffectInstance effectInstance = EffectInstance.load(msg.getCompound());
					UUID uuid = msg.getCompound().getUUID(MINION_ID_KEY);
					LivingEntity entityByUUID = (LivingEntity) TargetUtils.getEntityByUUID(m.level, uuid);
					String effectEventType = msg.getCompound().getString(EFFECT_EVENT_TYPE);
					//DevilRpg.LOGGER.info("----->effectEventType {}",effectEventType);
					if(effectEventType.equals(POTION_ADDED_EVENT)) {
						entityByUUID.addEffect(effectInstance);		
						//DevilRpg.LOGGER.info("----->effect added to client");
					}
					if(effectEventType.equals(POTION_EXPIRY_EVENT)) {
						entityByUUID.removeEffect(effectInstance.getEffect());
						//DevilRpg.LOGGER.info("----->effect removed to client");
					}
					
					
				}
			});
			contextSupplier.get().setPacketHandled(true);
		}
	}
}
