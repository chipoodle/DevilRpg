package com.chipoodle.devilrpg.network.payload;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapability;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.skillsystem.AbstractSkillExecutor;
import com.chipoodle.devilrpg.skillsystem.server.SkillServerPolicy;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;


/**
 * Sends a message from the client to the server to execute the given Skill bypassing triggering functions
 * defined on the {@link com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityImplementation} triggerAction method
 */
public record DirectSkillExecutionPayload(SkillEnum skill) implements CustomPacketPayload {

    public static final Type<DirectSkillExecutionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, "direct_skill_execution"));

    public static final StreamCodec<FriendlyByteBuf, DirectSkillExecutionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> buf.writeUtf(msg.skill().name()),
            buf -> new DirectSkillExecutionPayload(readSkill(buf))
    );

    private static SkillEnum readSkill(FriendlyByteBuf buf) {
        try {
            return SkillEnum.valueOf(buf.readUtf(64));
        } catch (IllegalArgumentException ignored) {
            return SkillEnum.EMPTY;
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DirectSkillExecutionPayload msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (player instanceof ServerPlayer sender) {
                if (msg.skill() == null || msg.skill() == SkillEnum.EMPTY) return;
                PlayerSkillCapabilityInterface unwrappedPlayerCapability = IGenericCapability.getUnwrappedPlayerCapability(sender, PlayerSkillCapability.INSTANCE);
                int nivel = unwrappedPlayerCapability.getSkillsPoints() == null ? 0
                        : unwrappedPlayerCapability.getSkillsPoints().getOrDefault(msg.skill(), 0);
                PlayerAuxiliaryCapabilityInterface auxiliary =
                        IGenericCapability.getUnwrappedPlayerCapability(sender, PlayerAuxiliaryCapability.INSTANCE);
                PlayerManaCapabilityInterface mana =
                        IGenericCapability.getUnwrappedPlayerCapability(sender, PlayerManaCapability.INSTANCE);
                boolean transformed = auxiliary != null && auxiliary.isWerewolfTransformation();
                float currentMana = mana == null ? Float.MAX_VALUE : mana.getMana();
                if (!SkillServerPolicy.canExecuteDirectSkill(msg.skill(), nivel, transformed, currentMana)) {
                    DevilRpg.LOGGER.warn("[Skill] reversión directa rechazada: skill={}, nivel={}, transformado={}, mana={}",
                            msg.skill(), nivel, transformed, currentMana);
                    return;
                }
                AbstractSkillExecutor loadedSkillExecutor = unwrappedPlayerCapability.getLoadedSkillExecutor(msg.skill());
                if (loadedSkillExecutor != null && loadedSkillExecutor.arePreconditionsMetBeforeConsumingResource(sender)) {
                    loadedSkillExecutor.execute(sender.level(), sender, null);
                }
            }
        });
    }
}
