package com.chipoodle.devilrpg.capability.experience;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.neoforged.neoforge.network.PacketDistributor;
import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.payload.PlayerExperiencePayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

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
        if (!pe.level().isClientSide)
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
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("currentLevel", currentLevel);
        nbt.putInt("maximumLevel", maximumLevel);
        nbt.putInt("unspentPoints", unspentPoints);
        return nbt;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
        currentLevel = nbt.getInt("currentLevel");
        maximumLevel = nbt.getInt("maximumLevel");
        unspentPoints = nbt.getInt("unspentPoints");
    }

    private void sendExperienceChangesToServer() {
        PacketDistributor.sendToServer(new PlayerExperiencePayload(serializeNBT(RegistryAccess.EMPTY)));
    }

    private void sendExperienceChangesToClient(ServerPlayer pe) {
        DevilRpg.LOGGER.info("----------> sendExperienceChangesToClient. unspentPoints: " + unspentPoints);
        PacketDistributor.sendToPlayer(pe,
                new PlayerExperiencePayload(serializeNBT(RegistryAccess.EMPTY)));

    }
}
