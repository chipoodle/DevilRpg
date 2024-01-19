package com.chipoodle.devilrpg.skillsystem.skillinstance;

import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapability;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.HashMap;

public class PlayerPassiveManaRegenerationAttribute extends AbstractPlayerPassiveAttribute  {

    public static final float REGENERATION_FACTOR = 0.05f;
    private Player playerIn;

    private static final float REGENERATION = 0.2f; // must be the same as PlayerManaCapabilityImplementation.regeneration initially

    public PlayerPassiveManaRegenerationAttribute(PlayerSkillCapabilityInterface parentCapability) {
        super(parentCapability);
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

            Integer manaRegenSkillPoints = parentCapability.getSkillsPoints().get(SkillEnum.MANA_REGENERATION);
            PlayerManaCapabilityInterface manaCapability = IGenericCapability.getUnwrappedPlayerCapability(playerIn, PlayerManaCapability.INSTANCE);
            manaCapability.setRegeneration((REGENERATION + (manaRegenSkillPoints * REGENERATION_FACTOR)),playerIn);
        }
    }

    public SkillEnum getSkillEnum() {
        return SkillEnum.MANA_REGENERATION;
    }

}
