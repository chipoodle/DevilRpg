package com.chipoodle.devilrpg.capability.experience;

import com.chipoodle.devilrpg.capability.IGenericCapability;
import net.minecraft.world.entity.player.Player;

public interface PlayerExperienceCapabilityInterface extends IGenericCapability {
    int getCurrentLevel();

    int getMaximumLevel();

    int getUnspentPoints();

    void setCurrentLevel(int currentLevel, Player pe);

    int consumePoint();

}
