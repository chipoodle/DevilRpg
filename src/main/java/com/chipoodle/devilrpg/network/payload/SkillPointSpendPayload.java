package com.chipoodle.devilrpg.network.payload;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.experience.PlayerExperienceCapability;
import com.chipoodle.devilrpg.capability.experience.PlayerExperienceCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.client.gui.scrollableskillscreen.SkillElement;
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
                    || current < 0 || current >= maximum || maximum <= 0
                    || !prerequisitesMet(skills, skill) || experience.getUnspentPoints() <= 0) {
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

    /** Replica la condición de desbloqueo de SkillWidget: el ancestro visible inmediato debe tener progreso. */
    private static boolean prerequisitesMet(PlayerSkillCapabilityInterface skills, SkillEnum skill) {
        SkillElement element = skills.getSkillElementByEnum(skill);
        if (element == null) return false;
        SkillElement parent = element.getParent();
        while (parent != null && parent.getDisplay() == null) {
            parent = parent.getParent();
        }
        if (parent == null) return true;
        SkillEnum parentSkill = parent.getSkillCapability();
        // Las tres raíces visibles del árbol no representan una habilidad y en la UI tienen
        // progreso 0/0 (isDone=true), por lo que sus primeros nodos sí están desbloqueados.
        if (parentSkill == null || parentSkill == SkillEnum.EMPTY) return true;
        int parentLevel = skills.getSkillsPoints(parentSkill);
        HashMap<SkillEnum, Integer> maximums = skills.getMaxSkillsPoints();
        Integer parentMaximum = maximums == null ? null : maximums.get(parentSkill);
        return parentLevel > 0 || (parentMaximum != null && parentLevel >= parentMaximum);
    }

    private static void resend(ServerPlayer player, PlayerSkillCapabilityInterface skills,
                               PlayerExperienceCapabilityInterface experience) {
        PacketDistributor.sendToPlayer(player,
                new PlayerSkillTreePayload(skills.serializeNBT(player.level().registryAccess()).copy()));
        PacketDistributor.sendToPlayer(player,
                new PlayerExperiencePayload(experience.serializeNBT(player.level().registryAccess()).copy()));
    }
}
