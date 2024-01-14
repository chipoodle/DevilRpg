package com.chipoodle.devilrpg.skillsystem.skillinstance;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityImplementation;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.init.ModEffects;
import com.chipoodle.devilrpg.skillsystem.ISkillContainer;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.HashMap;

public class PlayerPassiveKnockBackResistance extends AbstractPlayerPassiveAttribute implements ISkillContainer {
    public static final String ABSORPTION_TICKS = "ABSORPTION_TICKS";
    public static final String BLOCK_POINTS = "BLOCK_POINTS";
    private final PlayerSkillCapabilityInterface parentCapability;
      private Player playerIn;

    public PlayerPassiveKnockBackResistance(PlayerSkillCapabilityImplementation parentCapability) {
        this.parentCapability = parentCapability;
        DevilRpg.LOGGER.info("----------------------->CONSTRUCTOR PlayerPassiveWerewolfHit. Parent capability: {}", parentCapability);
    }

    /**
     * *
     *
     * @param levelIn
     * @param player
     * @param parameters Server side called
     */
    @Override
    public void execute(Level levelIn, Player player, HashMap<String, String> parameters) {
        if (!levelIn.isClientSide) {

            if (this.playerIn == null) {
                this.playerIn = player;
            }

            Integer absorptionTicks = Integer.valueOf(parameters.get(ABSORPTION_TICKS));
            Integer blockPoints = Integer.valueOf(parameters.get(BLOCK_POINTS));


            PlayerAuxiliaryCapabilityInterface auxiliary = IGenericCapability.getUnwrappedPlayerCapability(player, PlayerAuxiliaryCapability.INSTANCE);
            Integer skillPoints = parentCapability.getSkillsPoints().get(SkillEnum.KNOCKBACK_RESISTANCE);
            if (auxiliary.isWerewolfTransformation() && skillPoints > 0) {
                MobEffectInstance knockbackEffect = new MobEffectInstance(ModEffects.KNOCKBACK_RESISTANCE.get(), absorptionTicks + blockPoints, skillPoints); //5 máximo amplificador para dar el 100% de resistencia a knockback
                player.addEffect(knockbackEffect);
            }
        }
    }

    public SkillEnum getSkillEnum() {
        return SkillEnum.KNOCKBACK_RESISTANCE;
    }

}
