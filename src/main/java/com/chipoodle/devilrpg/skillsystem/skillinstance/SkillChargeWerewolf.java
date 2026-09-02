package com.chipoodle.devilrpg.skillsystem.skillinstance;

import net.minecraft.world.item.ItemStack;
import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.skillsystem.AbstractSkillExecutor;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Random;

public class SkillChargeWerewolf extends AbstractSkillExecutor {
    public static final int DAMAGE_BOOST_DURATION_IN_TICKS = 25;
    public static final double MAX_UP_BLOCKS = 2.0;
    // Multiplicador del dano del charge sobre el ataque del hombre lobo. Sube/baja este valor
    // para hacer mas/menos dano. 1.0 = dano = ataque base + items + pasivos + DAMAGE_BOOST.
    public static final float CHARGE_DAMAGE_MULTIPLIER = 1.1F;
    // Danio extra aditivo por nivel de habilidad (escale con el nivel del CHARGE, poco a poco).
    public static final float CHARGE_DAMAGE_PER_LEVEL = 0.2F;

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
        return auxiliary.isWerewolfTransformation() && player.onGround() && !player.getCooldowns().isOnCooldown(icon.getItem());
    }

    @Override
    public boolean isResourceConsumptionBypassed(Player player) {
        return false;
    }

    @Override
    public void execute(Level levelIn, Player player, HashMap<String, String> parameters) {
        if (!player.getCooldowns().isOnCooldown(icon.getItem())) {
            // Direccion: avanzar SIEMPRE hacia enfrente (solo yaw, horizontal) para que pegue a la
            // entidad que tengas delante. Si la vista esta hacia arriba (pitch<0) se sube un poco
            // (max MAX_UP_BLOCKS); si no, va plano hacia enfrente.
            float yaw = player.getYRot();
            float pitch = player.getXRot();
            float f1 = -Mth.sin(yaw * ((float) Math.PI / 180F));
            float f3 = Mth.cos(yaw * ((float) Math.PI / 180F));
            float hLen = Mth.sqrt(f1 * f1 + f3 * f3);
            if (hLen > 1.0E-4F) {
                f1 /= hLen;
                f3 /= hLen;
            }
            int j = 1;
            float f5 = 3.0F * ((1.0F + (float) j) / 4.0F); // 1.5
            f1 *= f5;
            f3 *= f5;
            float lookUp = Mth.clamp(-pitch / 90.0F, 0.0F, 1.0F);
            float f2 = lookUp * (float) MAX_UP_BLOCKS;

            int autoSpinAttackTicks = 10;
            int chargePoints = parentCapability.getSkillsPoints().get(SkillEnum.CHARGE);
            if (!levelIn.isClientSide) {
                // Aplicar el DAMAGE_BOOST ANTES de calcular el dano para que cuente
                // (suma a ATTACK_DAMAGE y por tanto al dano del spin).
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, DAMAGE_BOOST_DURATION_IN_TICKS, chargePoints));
                // Dano = ataque del hombre lobo (base + items + pasivos + el buff recien aplicado)
                // x multiplicador + un extra pequeno por nivel de habilidad.
                float chargeDamage = (float) (player.getAttributeValue(Attributes.ATTACK_DAMAGE) * CHARGE_DAMAGE_MULTIPLIER + chargePoints * CHARGE_DAMAGE_PER_LEVEL);
                player.push(f1, f2, f3);
                player.startAutoSpinAttack(autoSpinAttackTicks, chargeDamage, ItemStack.EMPTY);

                // Reproduce un sonido
                levelIn.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.NEUTRAL, 0.5F, 0.4F / (new Random().nextFloat() * 0.4F + 0.8F));
            } else {
                Minecraft m = Minecraft.getInstance();
                LocalPlayer clientPlayer = m.player;
                if (clientPlayer != null && clientPlayer.onGround()) {
                    float chargeDamage = (float) (clientPlayer.getAttributeValue(Attributes.ATTACK_DAMAGE) * CHARGE_DAMAGE_MULTIPLIER + chargePoints * CHARGE_DAMAGE_PER_LEVEL);
                    clientPlayer.push(f1, f2, f3);
                    clientPlayer.startAutoSpinAttack(autoSpinAttackTicks, chargeDamage, ItemStack.EMPTY);
                }
            }
            player.getCooldowns().addCooldown(icon.getItem(), 20);
        }
    }
}
