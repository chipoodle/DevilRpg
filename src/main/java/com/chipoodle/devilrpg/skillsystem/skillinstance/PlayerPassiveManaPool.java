package com.chipoodle.devilrpg.skillsystem.skillinstance;

import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapability;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapabilityImplementation;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityImplementation;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.skillsystem.ISkillContainer;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.HashMap;

public class PlayerPassiveManaPool extends AbstractPlayerPassive implements ISkillContainer {

    private final PlayerSkillCapabilityInterface parentCapability;
    private Player playerIn;

    private static Integer BASE_MANA_POOL = 30; // must be the same as PlayerManaCapabilityImplementation.maxMana initially

    public PlayerPassiveManaPool(PlayerSkillCapabilityImplementation parentCapability) {
        this.parentCapability = parentCapability;
    }

    /**
     *
     * @param levelIn
     * @param playerIn
     * @param parameters
     */
    @Override
    public void execute(Level levelIn, Player playerIn, HashMap<String, String> parameters) {
        if (!levelIn.isClientSide) {

            if (this.playerIn == null) {
                this.playerIn = playerIn;
            }

            Integer manaPoolPoints = parentCapability.getSkillsPoints().get(SkillEnum.MANA_POOL);
            PlayerManaCapabilityInterface mana = IGenericCapability.getUnwrappedPlayerCapability(playerIn, PlayerManaCapability.INSTANCE);
            mana.setMaxMana(BASE_MANA_POOL+manaPoolPoints,playerIn);
        }
    }

    public SkillEnum getSkillEnum() {
        return SkillEnum.MANA_POOL;
    }

}
