package com.chipoodle.devilrpg.capability.stamina;

import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.handler.PlayerStaminaClientServerHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;


public class PlayerStaminaCapabilityImplementation implements PlayerStaminaCapabilityInterface {

    private float stamina = 0f;
    private float maxStamina = 30f;
    private float regeneration = 0.0f;

    @Override
    public float getStamina() {
        return stamina;
    }

    @Override
    public void setStamina(float stamina, Player player) {
        this.stamina = stamina;
        if (!player.level.isClientSide)
            sendStaminaChangesToClient((ServerPlayer) player);
        else
            sendStaminaChangesToServer();
    }

    @Override
    public float getMaxStamina() {
        return maxStamina;
    }

    @Override
    public void setMaxStamina(float maxStamina, Player player) {
        this.maxStamina = maxStamina;
        if (!player.level.isClientSide)
            sendStaminaChangesToClient((ServerPlayer) player);
        else
            sendStaminaChangesToServer();
    }

    @Override
    public float getRegeneration() {
        return regeneration;
    }

    @Override
    public void setRegeneration(float regeneration, Player player) {
        this.regeneration = regeneration;
        if (!player.level.isClientSide)
            sendStaminaChangesToClient((ServerPlayer) player);
        else
            sendStaminaChangesToServer();
    }

    @Override
    public void onPlayerTickEventRegeneration(Player player, final float degeneration) {
            stamina += regeneration-degeneration;
            stamina = Math.max(Math.min(stamina, maxStamina),0.0f);
            setStamina(stamina, player);

    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        nbt.putFloat("stamina", stamina);
        nbt.putFloat("maxStamina", maxStamina);
        nbt.putFloat("regeneration", regeneration);
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag compound) {
        stamina = compound.getFloat("stamina");
        maxStamina = compound.getFloat("maxStamina");
        regeneration = compound.getFloat("regeneration");
    }

    private void sendStaminaChangesToServer() {
        ModNetwork.CHANNEL.sendToServer(new PlayerStaminaClientServerHandler(serializeNBT()));
    }

    private void sendStaminaChangesToClient(ServerPlayer pe) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> pe), new PlayerStaminaClientServerHandler(serializeNBT()));
    }
}
