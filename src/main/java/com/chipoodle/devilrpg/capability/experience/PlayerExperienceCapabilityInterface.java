package com.chipoodle.devilrpg.capability.experience;

import com.chipoodle.devilrpg.capability.IGenericCapability;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.INBTSerializable;

public interface PlayerExperienceCapabilityInterface extends IGenericCapability {
    int getCurrentLevel();

    int getMaximumLevel();

    int getUnspentPoints();

    void setCurrentLevel(int currentLevel, Player pe);

    int consumePoint();

}
