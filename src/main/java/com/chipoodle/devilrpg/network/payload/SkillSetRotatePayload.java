package com.chipoodle.devilrpg.network.payload;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Solicitud de rotación del loadout; el servidor limita el delta a un paso. */
public record SkillSetRotatePayload(int delta) implements CustomPacketPayload {
    public static final Type<SkillSetRotatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, "skill_set_rotate"));

    public static final StreamCodec<FriendlyByteBuf, SkillSetRotatePayload> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> buf.writeVarInt(msg.delta()),
            buf -> new SkillSetRotatePayload(buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SkillSetRotatePayload msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (msg.delta() != -1 && msg.delta() != 1) {
                DevilRpg.LOGGER.warn("[SkillTree] rotación de loadout rechazada: delta={}", msg.delta());
                return;
            }
            player.getData(PlayerSkillCapability.INSTANCE).rotateSkillSet(msg.delta(), player);
        });
    }
}
