package com.chipoodle.devilrpg.network.payload;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.util.PowerEnum;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record KeyboardSkillPayload(PowerEnum poder) implements CustomPacketPayload {

    public static final Type<KeyboardSkillPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, "keyboard_skill"));

    public static final StreamCodec<FriendlyByteBuf, KeyboardSkillPayload> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> buf.writeUtf(msg.poder().name()),
            buf -> new KeyboardSkillPayload(PowerEnum.valueOf(buf.readUtf()))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(KeyboardSkillPayload msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (player instanceof ServerPlayer sender) {
                sender.getData(PlayerSkillCapability.INSTANCE).triggerAction(sender, msg.poder());
            }
        });
    }
}
