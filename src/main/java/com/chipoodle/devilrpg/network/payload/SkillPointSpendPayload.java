package com.chipoodle.devilrpg.network.payload;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.experience.PlayerExperienceCapability;
import com.chipoodle.devilrpg.capability.experience.PlayerExperienceCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;

/** Solicitud C2S para comprar exactamente un nivel de una habilidad. */
public record SkillPointSpendPayload(SkillEnum skill) implements CustomPacketPayload {
    public static final Type<SkillPointSpendPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, "skill_point_spend"));

    public static final StreamCodec<FriendlyByteBuf, SkillPointSpendPayload> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> buf.writeUtf(msg.skill().name()),
            buf -> new SkillPointSpendPayload(readSkill(buf))
    );

    private static SkillEnum readSkill(FriendlyByteBuf buf) {
        try {
            return SkillEnum.valueOf(buf.readUtf(64));
        } catch (IllegalArgumentException ignored) {
            return SkillEnum.EMPTY;
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SkillPointSpendPayload msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            PlayerSkillCapabilityInterface skills = player.getData(PlayerSkillCapability.INSTANCE);
            PlayerExperienceCapabilityInterface experience = player.getData(PlayerExperienceCapability.INSTANCE);
            SkillEnum skill = msg.skill();
            HashMap<SkillEnum, Integer> levels = skills.getSkillsPoints();
            HashMap<SkillEnum, Integer> maximums = skills.getMaxSkillsPoints();
            Integer current = skill == null || levels == null ? null : levels.get(skill);
            Integer maximum = skill == null || maximums == null ? null : maximums.get(skill);

            if (skill == null || skill == SkillEnum.EMPTY || current == null || maximum == null
                    || current < 0 || current >= maximum || experience.getUnspentPoints() <= 0) {
                DevilRpg.LOGGER.warn("[SkillTree] compra rechazada: skill={}, current={}, max={}, unspent={}",
                        skill, current, maximum, experience.getUnspentPoints());
                resend(player, skills, experience);
                return;
            }
            if (experience.consumePoint(player) != 1) {
                resend(player, skills, experience);
                return;
            }
            levels.put(skill, current + 1);
            skills.setSkillsPoints(levels, player);
            // El pasivo se aplica en la misma operación validada que compra el nivel. No existe
            // un segundo payload repetible para ejecutar pasivos ya desbloqueados.
            if (skill.isPassive() && !skill.isForMinion()) {
                skills.triggerPassive(player, skill);
            }
        });
    }

    private static void resend(ServerPlayer player, PlayerSkillCapabilityInterface skills,
                               PlayerExperienceCapabilityInterface experience) {
        PacketDistributor.sendToPlayer(player,
                new PlayerSkillTreePayload(skills.serializeNBT(player.level().registryAccess()).copy()));
        PacketDistributor.sendToPlayer(player,
                new PlayerExperiencePayload(experience.serializeNBT(player.level().registryAccess()).copy()));
    }
}
