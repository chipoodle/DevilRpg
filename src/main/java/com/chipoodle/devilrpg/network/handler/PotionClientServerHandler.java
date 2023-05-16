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

import java.util.UUID;
import java.util.function.Supplier;

public class PotionClientServerHandler {
    public static final String MINION_ID_KEY = "entityId";
    public static final String EFFECT_EVENT_TYPE = "effectEventType";
    private static final String POTION_EXPIRY_EVENT = "PotionExpiryEvent";
    private static final String POTION_ADDED_EVENT = "PotionAddedEvent";
    private final CompoundTag compound;

    public PotionClientServerHandler(CompoundTag compound) {
        this.compound = compound;
    }

    public static void encode(final PotionClientServerHandler msg, final FriendlyByteBuf packetBuffer) {
        packetBuffer.writeNbt(msg.getCompound());
    }

    public static PotionClientServerHandler decode(final FriendlyByteBuf packetBuffer) {
        return new PotionClientServerHandler(packetBuffer.readNbt());
    }

    public static void onMessage(final PotionClientServerHandler msg,
                                 final Supplier<NetworkEvent.Context> contextSupplier) {


        if (contextSupplier.get().getDirection().equals(NetworkDirection.PLAY_TO_CLIENT)) {
            contextSupplier.get().enqueueWork(() -> {
                Minecraft m = Minecraft.getInstance();
                LocalPlayer clientPlayer = m.player;
                if (clientPlayer != null) {
                    UUID uuid = msg.getCompound().getUUID(MINION_ID_KEY);
                    LivingEntity entityByUUID = (LivingEntity) TargetUtils.getEntityByUUID(m.level, uuid);
                    if (uuid != null && entityByUUID != null) {
                        MobEffectInstance effectInstance = MobEffectInstance.load(msg.getCompound());
                        String effectEventType = msg.getCompound().getString(EFFECT_EVENT_TYPE);

                        //DevilRpg.LOGGER.info("----->effectEventType {}",effectEventType);
                        if (effectEventType.equals(POTION_ADDED_EVENT)) {
                            entityByUUID.addEffect(effectInstance);
                            DevilRpg.LOGGER.info("----->effect added to client");
                        }
                        if (effectEventType.equals(POTION_EXPIRY_EVENT)) {
                            entityByUUID.removeEffect(effectInstance.getEffect());
                            DevilRpg.LOGGER.info("----->effect removed to client");
                        }
                    }
                }
            });
            contextSupplier.get().setPacketHandled(true);
        }
    }

    public CompoundTag getCompound() {
        return compound;
    }
}
