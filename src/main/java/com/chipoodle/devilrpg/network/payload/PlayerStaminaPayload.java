package com.chipoodle.devilrpg.network.payload;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.stamina.PlayerStaminaCapability;
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

public record PlayerStaminaPayload(CompoundTag staminaCompound) implements CustomPacketPayload {

    public static final Type<PlayerStaminaPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, "player_stamina"));

    public static final StreamCodec<FriendlyByteBuf, PlayerStaminaPayload> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> buf.writeNbt(msg.staminaCompound()),
            buf -> new PlayerStaminaPayload(buf.readNbt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PlayerStaminaPayload msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow() == PacketFlow.SERVERBOUND) {
                if (ctx.player() instanceof ServerPlayer serverPlayer) {
                    serverPlayer.getData(PlayerStaminaCapability.INSTANCE).deserializeNBT(ctx.player().level().registryAccess(), msg.staminaCompound());
                }
            } else {
                LocalPlayer clientPlayer = Minecraft.getInstance().player;
                if (clientPlayer != null) {
                    clientPlayer.getData(PlayerStaminaCapability.INSTANCE).deserializeNBT(ctx.player().level().registryAccess(), msg.staminaCompound());
                }
            }
        });
    }
}
