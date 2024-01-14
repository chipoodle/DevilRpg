package com.chipoodle.devilrpg.capability.stamina;

import com.chipoodle.devilrpg.capability.IGenericCapability;
import net.minecraft.world.entity.player.Player;

public interface PlayerStaminaCapabilityInterface extends IGenericCapability {
    float getStamina();

    void setStamina(float stamina, Player player);

    void addStamina(float staminaAdded, Player player);

    float getMaxStamina();

    void setMaxStamina(float maxStamina, Player player);

    float getRegeneration();

    void setRegeneration(float regeneration, Player player);

    void onPlayerTickEventRegeneration(Player player, final float degeneration);
}
