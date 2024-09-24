package com.chipoodle.devilrpg.capability.mana;

import com.chipoodle.devilrpg.capability.IGenericCapability;
import net.minecraft.world.entity.player.Player;

public interface PlayerManaCapabilityInterface extends IGenericCapability {
    float getMana();

    void setMana(float mana, Player player);

    float getMaxMana();

    void setMaxMana(float maxMana, Player player);

    float getRegeneration();

    void setRegeneration(float regeneration, Player player);

    void onPlayerTickEventRegeneration(Player player, final float degeneration);
}
