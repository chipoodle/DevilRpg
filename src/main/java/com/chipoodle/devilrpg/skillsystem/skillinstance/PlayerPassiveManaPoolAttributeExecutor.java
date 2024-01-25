package com.chipoodle.devilrpg.skillsystem.skillinstance;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapability;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.HashMap;

public class PlayerPassiveManaPoolAttributeExecutor extends AbstractPlayerPassiveAttributeExecutor {
    private Player playerIn;

    private static final Integer BASE_MANA_POOL = 30; // must be the same as PlayerManaCapabilityImplementation.maxMana initially

    public PlayerPassiveManaPoolAttributeExecutor(PlayerSkillCapabilityInterface parentCapability) {
        super(parentCapability);
        DevilRpg.LOGGER.info("----------------------->CONSTRUCTOR PlayerPassiveManaPoolAttributeExecutor. Parent capability: {}", parentCapability);

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

            DevilRpg.LOGGER.info("-----------------------> Executing passive PlayerPassiveManaPoolAttributeExecutor skillPoints:{}",manaPoolPoints);
        }
    }

    public SkillEnum getSkillEnum() {
        return SkillEnum.MANA_POOL;
    }

}
