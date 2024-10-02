package com.chipoodle.devilrpg.skillsystem.skillinstance;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.HashMap;

public class PassiveSoulShieldVineRadiusExecutor extends AbstractPlayerPassiveAttributeExecutor {

    private Player playerIn;

    public PassiveSoulShieldVineRadiusExecutor(PlayerSkillCapabilityInterface parentCapability) {
        super(parentCapability);
        DevilRpg.LOGGER.info("----------------------->CONSTRUCTOR PlayerPassiveSoulShieldVineRadiusExecutor. Parent capability: {}", parentCapability);

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

            Integer skillPoints = parentCapability.getSkillsPoints().get(SkillEnum.SOULSHIELDVINE_RADIUS);
            //PlayerManaCapabilityInterface manaCapability = IGenericCapability.getUnwrappedPlayerCapability(playerIn, PlayerManaCapability.INSTANCE);
            //manaCapability.setRegeneration((REGENERATION + (skillPoints * REGENERATION_FACTOR)),playerIn);

            DevilRpg.LOGGER.info("-----------------------> Executing passive PlayerPassiveSoulShieldVineRadiusExecutor skillPoints:{}",skillPoints);
        }
    }

    public SkillEnum getSkillEnum() {
        return SkillEnum.SOULSHIELDVINE_RADIUS;
    }

}
