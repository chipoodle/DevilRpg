package com.chipoodle.devilrpg.network.payload;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.skillsystem.AbstractSkillExecutor;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Objects;

/**
 * Sends a message from the client to the server to execute the given Skill bypassing triggering functions
 * defined on the {@link com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityImplementation} triggerAction method
 */
public record DirectSkillExecutionPayload(SkillEnum skill) implements CustomPacketPayload {

    public static final Type<DirectSkillExecutionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, "direct_skill_execution"));

    public static final StreamCodec<FriendlyByteBuf, DirectSkillExecutionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> buf.writeUtf(msg.skill().name()),
            buf -> new DirectSkillExecutionPayload(SkillEnum.valueOf(buf.readUtf()))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DirectSkillExecutionPayload msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (player instanceof ServerPlayer sender) {
                PlayerSkillCapabilityInterface unwrappedPlayerCapability = IGenericCapability.getUnwrappedPlayerCapability(Objects.requireNonNull(sender), PlayerSkillCapability.INSTANCE);
                AbstractSkillExecutor loadedSkillExecutor = unwrappedPlayerCapability.getLoadedSkillExecutor(msg.skill());
                loadedSkillExecutor.execute(sender.level(), sender, null);
            }
        });
    }
}
