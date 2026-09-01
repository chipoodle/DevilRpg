package com.chipoodle.devilrpg.network.payload;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
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

public record PlayerAuxiliarPayload(CompoundTag auxiliaryCompound) implements CustomPacketPayload {

    public static final Type<PlayerAuxiliarPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, "player_auxiliar"));

    public static final StreamCodec<FriendlyByteBuf, PlayerAuxiliarPayload> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> buf.writeNbt(msg.auxiliaryCompound()),
            buf -> new PlayerAuxiliarPayload(buf.readNbt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PlayerAuxiliarPayload msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow() == PacketFlow.SERVERBOUND) {
                if (ctx.player() instanceof ServerPlayer serverPlayer) {
                    serverPlayer.getData(PlayerAuxiliaryCapability.INSTANCE).deserializeNBT(ctx.player().level().registryAccess(), msg.auxiliaryCompound());
                }
            } else {
                LocalPlayer clientPlayer = Minecraft.getInstance().player;
                if (clientPlayer != null) {
                    clientPlayer.getData(PlayerAuxiliaryCapability.INSTANCE).deserializeNBT(ctx.player().level().registryAccess(), msg.auxiliaryCompound());
                }
            }
        });
    }
}
