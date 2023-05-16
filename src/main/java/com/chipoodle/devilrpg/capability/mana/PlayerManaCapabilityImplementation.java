package com.chipoodle.devilrpg.capability.mana;

import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.handler.PlayerManaClientServerHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;


public class PlayerManaCapabilityImplementation implements PlayerManaCapabilityInterface {

    protected float mana = 0f;
    protected float maxMana = 50f;
    protected float regeneration = 0.10f;

    @Override
    public float getMana() {
        return mana;
    }

    @Override
    public void setMana(float mana, Player player) {
        this.mana = mana;
        if (!player.level.isClientSide)
            sendManaChangesToClient((ServerPlayer) player);
        else
            sendManaChangesToServer();
    }

    @Override
    public float getMaxMana() {
        return maxMana;
    }

    @Override
    public void setMaxMana(float maxMana, Player player) {
        this.maxMana = maxMana;
        if (!player.level.isClientSide)
            sendManaChangesToClient((ServerPlayer) player);
        else
            sendManaChangesToServer();
    }

    @Override
    public float getRegeneration() {
        return regeneration;
    }

    @Override
    public void setRegeneration(float regeneration, Player player) {
        this.regeneration = maxMana;
        if (!player.level.isClientSide)
            sendManaChangesToClient((ServerPlayer) player);
        else
            sendManaChangesToServer();
    }

    @Override
    public void onPlayerTickEventRegeneration(Player player) {
        if (mana < maxMana) {
            mana += regeneration;
            setMana(mana > maxMana ? maxMana : mana, player);
        } else {
            mana = maxMana;
        }
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        nbt.putFloat("mana", mana);
        nbt.putFloat("maxMana", maxMana);
        nbt.putFloat("regeneration", regeneration);
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag compound) {
        mana = compound.getFloat("mana");
        maxMana = compound.getFloat("maxMana");
        regeneration = compound.getFloat("regeneration");
    }

    private void sendManaChangesToServer() {
        ModNetwork.CHANNEL.sendToServer(new PlayerManaClientServerHandler(serializeNBT()));
    }

    private void sendManaChangesToClient(ServerPlayer pe) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> pe),
                new PlayerManaClientServerHandler(serializeNBT()));
    }
}