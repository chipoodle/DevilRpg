package com.chipoodle.devilrpg.skillsystem.skillinstance;

import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityImplementation;
import com.chipoodle.devilrpg.entity.LichenSeedBall;
import com.chipoodle.devilrpg.entity.VineFleshPuppetSeedBall;
import com.chipoodle.devilrpg.skillsystem.AbstractSkillExecutor;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Random;

public class SkillVineFleshPuppet extends AbstractSkillExecutor {

    public SkillVineFleshPuppet(PlayerSkillCapabilityImplementation parentCapability) {
        super(parentCapability);
    }

    @Override
    public SkillEnum getSkillEnum() {
        return SkillEnum.VINEFLESHBALL;
    }

    @Override
    public boolean arePreconditionsMetBeforeConsumingResource(Player player) {
        return !player.getCooldowns().isOnCooldown(icon.getItem());
    }

    @Override
    public void execute(Level levelIn, Player player, HashMap<String, String> parameters) {
        if (!player.getCooldowns().isOnCooldown(icon.getItem())) {
            if (!levelIn.isClientSide) {
                /*Random rand = new Random();
                levelIn.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.CHICKEN_EGG, SoundSource.NEUTRAL, 0.5F,
                        0.4F / (rand.nextFloat() * 0.4F + 0.8F));*/

                /*PlayerSkillCapabilityInterface skillCap = IGenericCapability.getUnwrappedPlayerCapability(player,
                        PlayerSkillCapability.INSTANCE);*/
                //setLichen(levelIn, player, skillCap);

                Random random = new Random();
                levelIn.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.NEUTRAL, 0.5F, 0.4F / (random.nextFloat() * 0.4F + 0.8F));
                VineFleshPuppetSeedBall vineFleshBallEntity = new VineFleshPuppetSeedBall(levelIn, player);
                vineFleshBallEntity.updateLevel(player, SkillEnum.VINEFLESHBALL);

                Vec3 deltaMovement = player.getDeltaMovement();

                if (Double.isNaN(deltaMovement.x) || Double.isNaN(deltaMovement.z))
                    player.setDeltaMovement(0.0d, deltaMovement.y, 0.0d);
                vineFleshBallEntity.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 1.0F);

                levelIn.addFreshEntity(vineFleshBallEntity);
                player.getCooldowns().addCooldown(icon.getItem(), 20);

            }
        }
    }

}
