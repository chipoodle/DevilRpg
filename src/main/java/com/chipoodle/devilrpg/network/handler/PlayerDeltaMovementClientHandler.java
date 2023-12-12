package com.chipoodle.devilrpg.network.handler;

import com.chipoodle.devilrpg.DevilRpg;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record PlayerDeltaMovementClientHandler(CompoundTag compound) {
    public static final String MOTION_X = "motionX";
    public static final String MOTION_Y = "motionY";
    public static final String MOTION_Z = "motionZ";
    public static final String AUTO_SPIN_ATTACK_TICKS = "auto_spin_attack_ticks";

    public static void encode(final PlayerDeltaMovementClientHandler msg, final FriendlyByteBuf packetBuffer) {
        packetBuffer.writeNbt(msg.compound());
    }

    public static PlayerDeltaMovementClientHandler decode(final FriendlyByteBuf packetBuffer) {
        return new PlayerDeltaMovementClientHandler(packetBuffer.readNbt());
    }

    public static void onMessage(final PlayerDeltaMovementClientHandler msg,
                                 final Supplier<NetworkEvent.Context> contextSupplier) {


        if (contextSupplier.get().getDirection().equals(NetworkDirection.PLAY_TO_CLIENT)) {
            contextSupplier.get().enqueueWork(() -> {
                Minecraft m = Minecraft.getInstance();
                LocalPlayer clientPlayer = m.player;
                if (clientPlayer != null && clientPlayer.isOnGround()) {
                    double motionX = msg.compound().getDouble(MOTION_X);
                    double motionY = msg.compound().getDouble(MOTION_Y);
                    double motionZ = msg.compound().getDouble(MOTION_Z);
                    int autoSpinAttackTicks = msg.compound().getInt(AUTO_SPIN_ATTACK_TICKS);

                    clientPlayer.push(motionX, motionY, motionZ);
                    clientPlayer.move(MoverType.SELF, new Vec3(0.0D, 2.1999999F, 0.0D));
                    DevilRpg.LOGGER.info("isOnGround move client");
                    clientPlayer.startAutoSpinAttack(autoSpinAttackTicks);
                }
            });
            contextSupplier.get().setPacketHandled(true);
        }
    }
}
