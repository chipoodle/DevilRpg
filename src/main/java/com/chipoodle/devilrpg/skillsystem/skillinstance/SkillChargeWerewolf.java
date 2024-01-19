package com.chipoodle.devilrpg.skillsystem.skillinstance;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.skillsystem.AbstractSkillContainer;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Random;

public class SkillChargeWerewolf extends AbstractSkillContainer {
    public static final int DAMAGE_BOOST_DURATION_IN_TICKS = 25;

    public SkillChargeWerewolf(PlayerSkillCapabilityInterface parentCapability) {
        super(parentCapability);
    }

    @Override
    public SkillEnum getSkillEnum() {
        return SkillEnum.CHARGE;
    }

    @Override
    public boolean arePreconditionsMetBeforeConsumingResource(Player player) {
        PlayerAuxiliaryCapabilityInterface auxiliary = IGenericCapability.getUnwrappedPlayerCapability(player, PlayerAuxiliaryCapability.INSTANCE);
        return auxiliary.isWerewolfTransformation() && player.isOnGround();
    }

    @Override
    public boolean isResourceConsumptionBypassed(Player player) {
        return false;
    }

    @Override
    public void execute(Level levelIn, Player player, HashMap<String, String> parameters) {
        if (!player.getCooldowns().isOnCooldown(icon.getItem())) {
            float f7 = player.getYRot();
            float f = player.getXRot();
            float f1 = -Mth.sin(f7 * ((float) Math.PI / 180F)) * Mth.cos(f * ((float) Math.PI / 180F));
            float f2 = -Mth.sin(f * ((float) Math.PI / 180F));
            float f3 = Mth.cos(f7 * ((float) Math.PI / 180F)) * Mth.cos(f * ((float) Math.PI / 180F));
            int autoSpinAttackTicks = 10;
            if (!levelIn.isClientSide) {
                //Vec3 lookVector = player.getLookAngle();
                int chargePoints = parentCapability.getSkillsPoints().get(SkillEnum.CHARGE);

                int j = 1;

                float f4 = Mth.sqrt(f1 * f1 + f2 * f2 + f3 * f3);
                float f5 = 3.0F * ((1.0F + (float) j) / 4.0F);
                f1 *= f5 / f4;
                f2 *= f5 / f4;
                f3 *= f5 / f4;

                player.push(f1, f2, f3);
                player.move(MoverType.SELF, new Vec3(0.0D, 2.1999999F, 0.0D));
                //DevilRpg.LOGGER.info("isOnGround move server");
                MobEffectInstance m = new MobEffectInstance(MobEffects.DAMAGE_BOOST, DAMAGE_BOOST_DURATION_IN_TICKS, chargePoints);
                player.addEffect(m);
                player.startAutoSpinAttack(autoSpinAttackTicks);

                // Reproduce un sonido
                levelIn.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.NEUTRAL, 0.5F, 0.4F / (new Random().nextFloat() * 0.4F + 0.8F));
            } else {
                Minecraft m = Minecraft.getInstance();
                LocalPlayer clientPlayer = m.player;
                if (clientPlayer != null && clientPlayer.isOnGround()) {


                    clientPlayer.push(f1, f2, f3);
                    clientPlayer.move(MoverType.SELF, new Vec3(0.0D, 2.1999999F, 0.0D));
                    DevilRpg.LOGGER.info("isOnGround move client");
                    clientPlayer.startAutoSpinAttack(autoSpinAttackTicks);
                }
            }
            player.getCooldowns().addCooldown(icon.getItem(), 20);
        }
    }
}
