package com.chipoodle.devilrpg.skillsystem.skillinstance;

import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Random;

public class SkillBlockWerewolf extends AbstractPlayerPassiveAttributeExecutor {

    public static final int ABSORPTION_TICKS = 800;

    public SkillBlockWerewolf(PlayerSkillCapabilityInterface parentCapability) {
        super(parentCapability);
    }

    @Override
    public SkillEnum getSkillEnum() {
        return SkillEnum.BLOCK;
    }

    @Override
    public boolean arePreconditionsMetBeforeConsumingResource(Player player) {
        PlayerAuxiliaryCapabilityInterface auxiliary = IGenericCapability.getUnwrappedPlayerCapability(player, PlayerAuxiliaryCapability.INSTANCE);
        return auxiliary.isWerewolfTransformation() && !player.getCooldowns().isOnCooldown(icon.getItem());
    }

    @Override
    public boolean isResourceConsumptionBypassed(Player player) {
        return false;
    }


    @Override
    public void execute(Level levelIn, Player player, HashMap<String, String> parameters) {

        //if (!player.getCooldowns().isOnCooldown(ModItems.ITEM_VACIO.get())) {
        if (!player.getCooldowns().isOnCooldown(icon.getItem())) {
            int blockPoints = parentCapability.getSkillsPoints().get(getSkillEnum());
            // Reproduce un sonido
            levelIn.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.NEUTRAL, 0.5F, 0.4F / (new Random().nextFloat() * 0.4F + 0.8F));

            if (!levelIn.isClientSide) {
                //if (!levelIn.isClientSide) {

                int absorptionLevel = Mth.abs(blockPoints / 5); //4to nivel de absorption
                //DevilRpg.LOGGER.debug("Absorption level: {}",absorptionLevel);

                // Aplica el efecto de resistencia al daño por 2 segundos
                MobEffectInstance resistanceEffect = new MobEffectInstance(MobEffects.ABSORPTION, ABSORPTION_TICKS + blockPoints, absorptionLevel); // 40 ticks = 2 segundos
                player.addEffect(resistanceEffect);
                parameters.put("ABSORPTION_TICKS", Integer.toString(ABSORPTION_TICKS));
                parameters.put("BLOCK_POINTS", Integer.toString(blockPoints));

                super.executePassiveChildren(getSkillEnum(), levelIn, player, parameters);
            }
            // Cancela el uso de este poder durante el tiempo que dura el efecto
            player.getCooldowns().addCooldown(icon.getItem(), (ABSORPTION_TICKS + blockPoints)); // 40 ticks = 2 segundos
            // }

        }
    }
}
