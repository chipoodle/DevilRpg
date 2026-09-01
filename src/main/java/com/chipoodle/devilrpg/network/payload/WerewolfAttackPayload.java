package com.chipoodle.devilrpg.network.payload;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.util.TargetUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record WerewolfAttackPayload(int entityId, InteractionHand hand) implements CustomPacketPayload {

    public static final Type<WerewolfAttackPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, "werewolf_attack"));

    public static final StreamCodec<FriendlyByteBuf, WerewolfAttackPayload> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> {
                buf.writeInt(msg.entityId());
                buf.writeUtf(msg.hand().name());
            },
            buf -> new WerewolfAttackPayload(buf.readInt(), InteractionHand.valueOf(buf.readUtf()))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WerewolfAttackPayload msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (player instanceof ServerPlayer sender) {
                Entity target = sender.level().getEntity(msg.entityId());
                TargetUtils.attackTargetEntityWithItemHand(sender, target, msg.hand());
            }
        });
    }
}
