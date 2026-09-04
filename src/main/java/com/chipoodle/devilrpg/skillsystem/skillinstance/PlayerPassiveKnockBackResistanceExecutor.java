package com.chipoodle.devilrpg.skillsystem.skillinstance;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.init.ModEffects;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.HashMap;

public class PlayerPassiveKnockBackResistanceExecutor extends AbstractPlayerPassiveAttributeExecutor {
    public static final String ABSORPTION_TICKS = "ABSORPTION_TICKS";
    public static final String BLOCK_POINTS = "BLOCK_POINTS";
      private Player playerIn;

    public PlayerPassiveKnockBackResistanceExecutor(PlayerSkillCapabilityInterface parentCapability) {
        super(parentCapability);
        DevilRpg.LOGGER.info("----------------------->CONSTRUCTOR PlayerPassiveKnockBackResistanceExecutor. Parent capability: {}", parentCapability);
    }

    /**
     * *
     *
     * @param levelIn Level
     * @param player the player
     * @param parameters Server side called
     */
    @Override
    public void execute(Level levelIn, Player player, HashMap<String, String> parameters) {
        if (!levelIn.isClientSide) {



            if (this.playerIn == null) {
                this.playerIn = player;
            }

            // Estos parametros solo los pone SkillWerewolfBlocking; si el pasivo se dispara solo
            // (pulsando su boton) el mapa viene vacio y habria null -> NumberFormatException.
            Integer absorptionTicks = parameters.get(ABSORPTION_TICKS) != null ? Integer.valueOf(parameters.get(ABSORPTION_TICKS)) : 800;
            Integer blockPoints = parameters.get(BLOCK_POINTS) != null ? Integer.valueOf(parameters.get(BLOCK_POINTS)) : 0;


            PlayerAuxiliaryCapabilityInterface auxiliary = IGenericCapability.getUnwrappedPlayerCapability(player, PlayerAuxiliaryCapability.INSTANCE);
            Integer skillPoints = parentCapability.getSkillsPoints().get(SkillEnum.KNOCKBACK_RESISTANCE);

            DevilRpg.LOGGER.info("-----------------------> Executing passive PlayerPassiveKnockBackResistanceExecutor skillPoints:{}",skillPoints);

            if (auxiliary.isWerewolfTransformation() && skillPoints > 0) {
                MobEffectInstance knockbackEffect = new MobEffectInstance(ModEffects.KNOCKBACK_RESISTANCE, absorptionTicks + blockPoints, skillPoints - 1); // de 0 a 4. 5 máximo amplificador para dar el 100% de resistencia a knockback
                player.addEffect(knockbackEffect);
            }
        }
    }

    public SkillEnum getSkillEnum() {
        return SkillEnum.KNOCKBACK_RESISTANCE;
    }

}
