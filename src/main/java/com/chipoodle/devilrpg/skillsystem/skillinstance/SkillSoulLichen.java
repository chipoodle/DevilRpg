package com.chipoodle.devilrpg.skillsystem.skillinstance;

import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityImplementation;
import com.chipoodle.devilrpg.entity.LichenSeedBall;
import com.chipoodle.devilrpg.skillsystem.AbstractSkillExecutor;
import com.chipoodle.devilrpg.skillsystem.AbstractSkillSeedsInInventoryExecutor;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Random;

public class SkillSoulLichen extends AbstractSkillSeedsInInventoryExecutor {

    public SkillSoulLichen(PlayerSkillCapabilityImplementation parentCapability) {
        super(parentCapability);
    }

    @Override
    public SkillEnum getSkillEnum() {
        return SkillEnum.SOULLICHEN;
    }

    @Override
    public boolean arePreconditionsMetBeforeConsumingResource(Player player) {
        boolean hasSeeds = super.arePreconditionsMetBeforeConsumingResource(player);
        return !player.getCooldowns().isOnCooldown(icon.getItem()) && hasSeeds;
    }

    @Override
    public void execute(Level levelIn, Player player, HashMap<String, String> parameters) {
        if (!player.getCooldowns().isOnCooldown(icon.getItem())) {
            if (!levelIn.isClientSide) {

                // Consumir una semilla del inventario
                consumeSeed(player);

                /*Random rand = new Random();
                levelIn.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.CHICKEN_EGG, SoundSource.NEUTRAL, 0.5F,
                        0.4F / (rand.nextFloat() * 0.4F + 0.8F));*/

                /*PlayerSkillCapabilityInterface skillCap = IGenericCapability.getUnwrappedPlayerCapability(player,
                        PlayerSkillCapability.INSTANCE);*/
                //setLichen(levelIn, player, skillCap);

                Random random = new Random();
                levelIn.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.NEUTRAL, 0.5F, 0.4F / (random.nextFloat() * 0.4F + 0.8F));
                LichenSeedBall lichenSeedBallEntity = new LichenSeedBall(levelIn, player);
                lichenSeedBallEntity.updateLevel(player, SkillEnum.SOULLICHEN);

                Vec3 deltaMovement = player.getDeltaMovement();

                if (Double.isNaN(deltaMovement.x) || Double.isNaN(deltaMovement.z))
                    player.setDeltaMovement(0.0d, deltaMovement.y, 0.0d);
                lichenSeedBallEntity.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 1.0F);

                levelIn.addFreshEntity(lichenSeedBallEntity);
                player.getCooldowns().addCooldown(icon.getItem(), 20);

            }
        }
    }

}
