package com.chipoodle.devilrpg.skillsystem.skillinstance;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityImplementation;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.handler.PlayerDeltaMovementClientHandler;
import com.chipoodle.devilrpg.network.handler.PotionClientHandler;
import com.chipoodle.devilrpg.skillsystem.ISkillContainer;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Random;

public class SkillCharge implements ISkillContainer {

    private final PlayerSkillCapabilityInterface parentCapability;

    public SkillCharge(PlayerSkillCapabilityImplementation parentCapability) {

        this.parentCapability = parentCapability;
    }

    @Override
    public SkillEnum getSkillEnum() {
        return SkillEnum.CHARGE;
    }

    @Override
    public boolean arePreconditionsMetBeforeConsumingMana(Player player) {
        PlayerAuxiliaryCapabilityInterface auxiliary = IGenericCapability.getUnwrappedPlayerCapability(player, PlayerAuxiliaryCapability.INSTANCE);
        return auxiliary.isWerewolfTransformation() && player.isOnGround();
    }

    @Override
    public void execute(Level levelIn, Player player, HashMap<String, String> parameters) {
        // Verifica que el código se esté ejecutando en el lado del servidor
        if (!levelIn.isClientSide) {
            //Vec3 lookVector = player.getLookAngle();
            int autoSpinAttackTicks = 10;
            int chargePoints = parentCapability.getSkillsPoints().get(SkillEnum.CHARGE);

            int j = 1;
            float f7 = player.getYRot();
            float f = player.getXRot();
            float f1 = -Mth.sin(f7 * ((float) Math.PI / 180F)) * Mth.cos(f * ((float) Math.PI / 180F));
            float f2 = -Mth.sin(f * ((float) Math.PI / 180F));
            float f3 = Mth.cos(f7 * ((float) Math.PI / 180F)) * Mth.cos(f * ((float) Math.PI / 180F));
            float f4 = Mth.sqrt(f1 * f1 + f2 * f2 + f3 * f3);
            float f5 = 3.0F * ((1.0F + (float) j) / 4.0F);
            f1 *= f5 / f4;
            f2 *= f5 / f4;
            f3 *= f5 / f4;

            player.push(f1, f2, f3);
            player.move(MoverType.SELF, new Vec3(0.0D, 2.1999999F, 0.0D));
            DevilRpg.LOGGER.info("isOnGround move server");
            int duration = 20;
            MobEffectInstance m = new MobEffectInstance(MobEffects.DAMAGE_BOOST,duration,chargePoints);

            player.startAutoSpinAttack(autoSpinAttackTicks);
            player.addEffect(m);

            CompoundTag movementDeltaVec = new CompoundTag();
            movementDeltaVec.putInt(PlayerDeltaMovementClientHandler.AUTO_SPIN_ATTACK_TICKS, autoSpinAttackTicks);
            movementDeltaVec.putDouble(PlayerDeltaMovementClientHandler.MOTION_X, f1);
            movementDeltaVec.putDouble(PlayerDeltaMovementClientHandler.MOTION_Y, f2);
            movementDeltaVec.putDouble(PlayerDeltaMovementClientHandler.MOTION_Z, f3);
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> ((ServerPlayer) player)), new PlayerDeltaMovementClientHandler(movementDeltaVec));

            // Reproduce un sonido
            levelIn.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.NEUTRAL, 0.5F, 0.4F / (new Random().nextFloat() * 0.4F + 0.8F));
        }
    }
}
