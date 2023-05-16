package com.chipoodle.devilrpg.network.handler;

import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PlayerPassiveSkillServerHandler {
    private final CompoundTag skillCompound;

    public PlayerPassiveSkillServerHandler(CompoundTag skillCompound) {
        this.skillCompound = skillCompound;
    }

    public static void encode(final PlayerPassiveSkillServerHandler msg, final FriendlyByteBuf packetBuffer) {
        packetBuffer.writeNbt(msg.getSkillCompound());
    }

    public static PlayerPassiveSkillServerHandler decode(final FriendlyByteBuf packetBuffer) {
        return new PlayerPassiveSkillServerHandler(packetBuffer.readNbt());
    }

    public static void onMessage(final PlayerPassiveSkillServerHandler msg,
                                 final Supplier<NetworkEvent.Context> contextSupplier) {
        if (contextSupplier.get().getDirection().equals(NetworkDirection.PLAY_TO_SERVER)) {
            contextSupplier.get().enqueueWork(() -> {
                ServerPlayer sender = contextSupplier.get().getSender(); // the client that sent this packet
                LazyOptional<PlayerSkillCapabilityInterface> skill = sender.getCapability(PlayerSkillCapability.INSTANCE);
                //sender.sendMessage(new StringTextComponent("KeyboardSkillServerHandler on msg:"+ msg.getPoder().name()+" Player ID: "+sender.getEntityId()));
                skill.ifPresent(x -> x.triggerPassive(sender, msg.getSkillCompound()));
            });
            contextSupplier.get().setPacketHandled(true);
        }
    }

    public CompoundTag getSkillCompound() {
        return skillCompound;
    }
}
