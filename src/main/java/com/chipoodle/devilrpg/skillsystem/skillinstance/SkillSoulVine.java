package com.chipoodle.devilrpg.skillsystem.skillinstance;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.block.SoulVineBlock;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityImplementation;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.init.ModBlocks;
import com.chipoodle.devilrpg.skillsystem.AbstractSkillContainer;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Random;

public class SkillSoulVine extends AbstractSkillContainer {

    public SkillSoulVine(PlayerSkillCapabilityImplementation parentCapability) {
        super(parentCapability);
    }

    @Override
    public SkillEnum getSkillEnum() {
        return SkillEnum.SOULVINE;
    }

    @Override
    public void execute(Level levelIn, Player playerIn, HashMap<String, String> parameters) {
        if (!levelIn.isClientSide) {
            Random rand = new Random();
            levelIn.playSound(null, playerIn.getX(), playerIn.getY(), playerIn.getZ(),
                    SoundEvents.CHICKEN_EGG, SoundSource.NEUTRAL, 0.5F,
                    0.4F / (rand.nextFloat() * 0.4F + 0.8F));

            PlayerSkillCapabilityInterface skillCap = IGenericCapability.getUnwrappedPlayerCapability(playerIn,
                    PlayerSkillCapability.INSTANCE);

            setVine(levelIn, playerIn, skillCap);
        }
    }

    private void setVine(Level levelIn, Player playerIn, PlayerSkillCapabilityInterface skillCap) {
        BlockPos playerBlockPos = playerIn.blockPosition();
        SoulVineBlock createdBlock = ModBlocks.SOUL_VINE_BLOCK.get();
        BlockState playerBlockState = levelIn.getBlockState(playerBlockPos);
        Vec3 playerLookVector = playerIn.getLookAngle();
        Direction nearestDirection = Direction.getNearest(playerLookVector.x, 0, playerLookVector.z);
        DevilRpg.LOGGER.info("-------->Direction: {}" , nearestDirection);
        BlockPos newBlockpos = playerBlockPos.relative(nearestDirection);

        //createdBlock.setGrowthDirection(nearestDirection);
        //createdBlock.setEdad(5);
        if (playerBlockState.getBlock().equals(Blocks.AIR)) {
            int skillPoints = skillCap.getSkillsPoints().get(SkillEnum.SOULVINE);

            DevilRpg.LOGGER.info("-------->placed block: {} calculatedProgression: {}", createdBlock,skillPoints);
            levelIn
                    .setBlockAndUpdate(
                            newBlockpos,
                            createdBlock.defaultBlockState()
                                    .setValue(SoulVineBlock.AGE,1)
                                    .setValue(SoulVineBlock.DIRECTIONS,nearestDirection)
                                    .setValue(SoulVineBlock.LEVEL,skillPoints)
                    );
            //levelIn.scheduleTick(newBlockpos,createdBlock,1);


        }
    }

}
