package com.chipoodle.devilrpg.network.payload;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PlayerSkillTreePayload(CompoundTag skillCompound) implements CustomPacketPayload {

    public static final Type<PlayerSkillTreePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, "player_skill_tree"));

    public static final StreamCodec<FriendlyByteBuf, PlayerSkillTreePayload> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> buf.writeNbt(msg.skillCompound()),
            buf -> new PlayerSkillTreePayload(buf.readNbt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PlayerSkillTreePayload msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow() == PacketFlow.SERVERBOUND) {
                if (ctx.player() instanceof ServerPlayer serverPlayer) {
                    serverPlayer.getData(PlayerSkillCapability.INSTANCE).deserializeNBT(ctx.player().level().registryAccess(), msg.skillCompound());
                }
            } else {
                LocalPlayer clientPlayer = Minecraft.getInstance().player;
                if (clientPlayer != null) {
                    clientPlayer.getData(PlayerSkillCapability.INSTANCE).deserializeNBT(ctx.player().level().registryAccess(), msg.skillCompound());
                }
            }
        });
    }
}
