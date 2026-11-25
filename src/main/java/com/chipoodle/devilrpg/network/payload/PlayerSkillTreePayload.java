package com.chipoodle.devilrpg.network.payload;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.experience.PlayerExperienceCapability;
import com.chipoodle.devilrpg.capability.experience.PlayerExperienceCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityImplementation;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.skillsystem.SkillTreeSpendPolicy;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;

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
                    PlayerSkillCapabilityInterface actual = serverPlayer.getData(PlayerSkillCapability.INSTANCE);
                    PlayerExperienceCapabilityInterface exp = serverPlayer.getData(PlayerExperienceCapability.INSTANCE);
                    PlayerSkillCapabilityImplementation incoming = new PlayerSkillCapabilityImplementation();
                    incoming.deserializeNBT(serverPlayer.level().registryAccess(), msg.skillCompound());
                    int antes = totalPuntos(actual.getSkillsPoints());
                    int despues = totalPuntos(incoming.getSkillsPoints());
                    if (!SkillTreeSpendPolicy.permite(antes, despues, exp.getUnspentPoints())) {
                        DevilRpg.LOGGER.warn("[SkillTree] arbol rechazado: puntos {} -> {} (sin gastar {})",
                                antes, despues, exp.getUnspentPoints());
                        reenviar(serverPlayer, actual, exp);
                        return;
                    }
                    if (despues == antes + 1) {
                        exp.consumePoint(serverPlayer); // autoridad: gasta un punto en el servidor
                    }
                    actual.applyClientTree(msg.skillCompound, serverPlayer.level().registryAccess());
                    reenviar(serverPlayer, actual, exp);
                }
            } else {
                LocalPlayer clientPlayer = Minecraft.getInstance().player;
                if (clientPlayer != null) {
                    clientPlayer.getData(PlayerSkillCapability.INSTANCE).deserializeNBT(ctx.player().level().registryAccess(), msg.skillCompound());
                }
            }
        });
    }

    private static int totalPuntos(Map<SkillEnum, Integer> puntos) {
        int total = 0;
        if (puntos != null) {
            for (Integer n : puntos.values()) {
                if (n != null && n > 0) {
                    total += n;
                }
            }
        }
        return total;
    }

    private static void reenviar(ServerPlayer player, PlayerSkillCapabilityInterface arbol,
                                 PlayerExperienceCapabilityInterface exp) {
        PacketDistributor.sendToPlayer(player,
                new PlayerSkillTreePayload(arbol.serializeNBT(player.level().registryAccess())));
        PacketDistributor.sendToPlayer(player,
                new PlayerExperiencePayload(exp.serializeNBT(player.level().registryAccess())));
    }
}
