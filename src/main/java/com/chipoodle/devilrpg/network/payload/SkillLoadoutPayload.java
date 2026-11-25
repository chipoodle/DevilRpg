package com.chipoodle.devilrpg.network.payload;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.util.PowerEnum;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;

/** Solicitud para asignar o retirar una habilidad de una ranura del loadout activo. */
public record SkillLoadoutPayload(PowerEnum power, SkillEnum skill) implements CustomPacketPayload {
    public static final Type<SkillLoadoutPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, "skill_loadout"));

    public static final StreamCodec<FriendlyByteBuf, SkillLoadoutPayload> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> {
                buf.writeUtf(msg.power().name());
                buf.writeUtf(msg.skill().name());
            },
            buf -> new SkillLoadoutPayload(readPower(buf), readSkill(buf))
    );

    private static PowerEnum readPower(FriendlyByteBuf buf) {
        try { return PowerEnum.valueOf(buf.readUtf(64)); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private static SkillEnum readSkill(FriendlyByteBuf buf) {
        try { return SkillEnum.valueOf(buf.readUtf(64)); }
        catch (IllegalArgumentException ignored) { return SkillEnum.EMPTY; }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SkillLoadoutPayload msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            PlayerSkillCapabilityInterface skills = player.getData(PlayerSkillCapability.INSTANCE);
            if (msg.power() == null || msg.skill() == null) {
                resend(player, skills);
                return;
            }
            if (msg.skill() != SkillEnum.EMPTY) {
                int level = skills.getSkillsPoints(msg.skill());
                if (msg.skill().isPassive() || msg.skill().isForMinion() || level <= 0) {
                    DevilRpg.LOGGER.warn("[SkillTree] loadout rechazado: power={}, skill={}, level={}",
                            msg.power(), msg.skill(), level);
                    resend(player, skills);
                    return;
                }
            }
            HashMap<PowerEnum, SkillEnum> mapping = skills.getSkillsNameOfPowers();
            mapping.put(msg.power(), msg.skill());
            skills.setSkillsNameOfPowers(mapping, player);
        });
    }

    private static void resend(ServerPlayer player, PlayerSkillCapabilityInterface skills) {
        PacketDistributor.sendToPlayer(player,
                new PlayerSkillTreePayload(skills.serializeNBT(player.level().registryAccess()).copy()));
    }
}
