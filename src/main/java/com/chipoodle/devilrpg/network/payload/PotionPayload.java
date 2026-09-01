package com.chipoodle.devilrpg.network.payload;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.util.TargetUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Objects;
import java.util.UUID;

public record PotionPayload(CompoundTag compound) implements CustomPacketPayload {

    public static final String ENTITY_ID_KEY = "entityId";
    public static final String EFFECT_EVENT_TYPE = "effectEventType";
    public static final String POTION_EXPIRY_EVENT = "PotionExpiryEvent";
    public static final String POTION_ADDED_EVENT = "PotionAddedEvent";
    public static final String CUSTOM_ADDED_EVENT = "Added";
    private static final String CUSTOM_EXPIRED_EVENT = "Expired";

    public static final Type<PotionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, "potion"));

    public static final StreamCodec<FriendlyByteBuf, PotionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> buf.writeNbt(msg.compound()),
            buf -> new PotionPayload(buf.readNbt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PotionPayload msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            LocalPlayer clientPlayer = Minecraft.getInstance().player;
            if (clientPlayer != null) {
                UUID uuid = msg.compound().getUUID(ENTITY_ID_KEY);
                LivingEntity entityByUUID = (LivingEntity) TargetUtils.getEntityByUUID(Objects.requireNonNull(Minecraft.getInstance().level), uuid);
                if (entityByUUID != null) {
                    MobEffectInstance effectInstance = MobEffectInstance.load(msg.compound());
                    String effectEventType = msg.compound().getString(EFFECT_EVENT_TYPE);

                    if (effectEventType.equals(POTION_ADDED_EVENT) || effectEventType.equals(CUSTOM_ADDED_EVENT)) {
                        assert effectInstance != null;
                        entityByUUID.addEffect(effectInstance);
                    }
                    if (effectEventType.equals(POTION_EXPIRY_EVENT) || effectEventType.equals(CUSTOM_EXPIRED_EVENT)) {
                        assert effectInstance != null;
                        entityByUUID.removeEffect(effectInstance.getEffect());
                    }
                }
            }
        });
    }
}
