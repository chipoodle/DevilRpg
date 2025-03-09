package com.chipoodle.devilrpg.skillsystem.skillinstance;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.HashMap;

public class PassiveFlorasVigorExecutor extends AbstractPlayerPassiveAttributeExecutor {

    private Player playerIn;

    public PassiveFlorasVigorExecutor(PlayerSkillCapabilityInterface parentCapability) {
        super(parentCapability);
        DevilRpg.LOGGER.info("----------------------->CONSTRUCTOR PassiveFlorasVigorExecutor. Parent capability: {}", parentCapability);

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

            Integer skillPoints = parentCapability.getSkillsPoints().get(SkillEnum.FLORAS_VIGOR);
            DevilRpg.LOGGER.info("-----------------------> Executing passive PassiveFlorasVigorExecutor skillPoints:{}",skillPoints);
        }
    }

    public SkillEnum getSkillEnum() {
        return SkillEnum.FLORAS_VIGOR;
    }

}
