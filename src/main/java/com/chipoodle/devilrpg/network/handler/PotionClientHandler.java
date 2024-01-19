package com.chipoodle.devilrpg.network.handler;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.util.TargetUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public record PotionClientHandler(CompoundTag compound) {
    public static final String ENTITY_ID_KEY = "entityId";
    public static final String EFFECT_EVENT_TYPE = "effectEventType";
    public static final String POTION_EXPIRY_EVENT = "PotionExpiryEvent";
    public static final String POTION_ADDED_EVENT = "PotionAddedEvent";
    public static final String CUSTOM_ADDED_EVENT = "Added";
    private static final String CUSTOM_EXPIRED_EVENT = "Expired";

    public static void encode(final PotionClientHandler msg, final FriendlyByteBuf packetBuffer) {
        packetBuffer.writeNbt(msg.compound());
    }

    public static PotionClientHandler decode(final FriendlyByteBuf packetBuffer) {
        return new PotionClientHandler(packetBuffer.readNbt());
    }

    public static void onMessage(final PotionClientHandler msg, final Supplier<NetworkEvent.Context> contextSupplier) {


        if (contextSupplier.get().getDirection().equals(NetworkDirection.PLAY_TO_CLIENT)) {
            contextSupplier.get().enqueueWork(() -> {
                Minecraft m = Minecraft.getInstance();
                LocalPlayer clientPlayer = m.player;
                if (clientPlayer != null) {
                    UUID uuid = msg.compound().getUUID(ENTITY_ID_KEY);
                    LivingEntity entityByUUID = (LivingEntity) TargetUtils.getEntityByUUID(Objects.requireNonNull(m.level), uuid);
                    if (entityByUUID != null) {
                        MobEffectInstance effectInstance = MobEffectInstance.load(msg.compound());
                        String effectEventType = msg.compound().getString(EFFECT_EVENT_TYPE);

                        //DevilRpg.LOGGER.info("----->effectEventType {}",effectEventType);
                        if (effectEventType.equals(POTION_ADDED_EVENT) || effectEventType.equals(CUSTOM_ADDED_EVENT)) {
                            assert effectInstance != null;
                            entityByUUID.addEffect(effectInstance);
                            DevilRpg.LOGGER.info("----->effect added to client {}",effectInstance);
                        }
                        if (effectEventType.equals(POTION_EXPIRY_EVENT) || effectEventType.equals(CUSTOM_EXPIRED_EVENT)) {
                            assert effectInstance != null;
                            entityByUUID.removeEffect(effectInstance.getEffect());
                            DevilRpg.LOGGER.info("----->effect removed to client{}",effectInstance);
                        }
                    }
                }
            });
            contextSupplier.get().setPacketHandled(true);
        }
    }
}
