package com.chipoodle.devilrpg.capability.experience;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.handler.PlayerExperienceClientServerHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;

public class PlayerExperienceCapabilityImplementation implements PlayerExperienceCapabilityInterface {

    protected int currentLevel = 0;
    protected int maximumLevel = 0;
    protected int unspentPoints = 0;

    @Override
    public int getCurrentLevel() {
        return currentLevel;
    }

    @Override
    public int getMaximumLevel() {
        return maximumLevel;
    }

    @Override
    public int getUnspentPoints() {
        return unspentPoints;
    }

    @Override
    public void setCurrentLevel(int currentLevel, Player pe) {
        this.currentLevel = currentLevel;
        if (this.currentLevel > maximumLevel) {
            unspentPoints += this.currentLevel - maximumLevel;
            maximumLevel = this.currentLevel;
        }
        if (!pe.level.isClientSide)
            sendExperienceChangesToClient((ServerPlayer) pe);
        else
            sendExperienceChangesToServer();
    }

    @Override
    public int consumePoint() {
        if (unspentPoints > 0) {
            unspentPoints--;
            sendExperienceChangesToServer();
            return 1;
        }
        return 0;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("currentLevel", currentLevel);
        nbt.putInt("maximumLevel", maximumLevel);
        nbt.putInt("unspentPoints", unspentPoints);
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        currentLevel = nbt.getInt("currentLevel");
        maximumLevel = nbt.getInt("maximumLevel");
        unspentPoints = nbt.getInt("unspentPoints");
    }

    private void sendExperienceChangesToServer() {
        ModNetwork.CHANNEL.sendToServer(new PlayerExperienceClientServerHandler(serializeNBT()));
    }

    private void sendExperienceChangesToClient(ServerPlayer pe) {
        DevilRpg.LOGGER.info("----------> sendExperienceChangesToClient. unspentPoints: " + unspentPoints);
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> pe),
                new PlayerExperienceClientServerHandler(serializeNBT()));

    }
}
