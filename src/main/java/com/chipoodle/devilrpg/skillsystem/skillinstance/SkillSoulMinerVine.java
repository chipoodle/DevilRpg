package com.chipoodle.devilrpg.skillsystem.skillinstance;

import com.chipoodle.devilrpg.block.SoulMinerVineBlock;
import com.chipoodle.devilrpg.blockentity.SoulMinerVineBlockEntity;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityImplementation;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.init.ModBlocks;
import com.chipoodle.devilrpg.skillsystem.AbstractSkillSeedsInInventoryExecutor;
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

public class SkillSoulMinerVine extends AbstractSkillSeedsInInventoryExecutor {

    public SkillSoulMinerVine(PlayerSkillCapabilityImplementation parentCapability) {
        super(parentCapability);
    }

    @Override
    public SkillEnum getSkillEnum() {
        return SkillEnum.SOULSHIELDVINE;
    }

    @Override
    public boolean arePreconditionsMetBeforeConsumingResource(Player player) {
        BlockPos playerBlockPos = player.blockPosition();
        //BlockState playerBlockState = player.level().getBlockState(playerBlockPos);
        Vec3 playerLookVector = player.getLookAngle();
        Direction nearestDirection = Direction.getNearest(playerLookVector.x, 0, playerLookVector.z);
        //DevilRpg.LOGGER.info("-------->Direction: {}", nearestDirection);
        BlockPos newBlockpos = playerBlockPos.relative(nearestDirection);

        boolean hasSeeds = super.arePreconditionsMetBeforeConsumingResource(player);

        boolean canPlace = (player.level().getBlockState(newBlockpos).canBeReplaced() ||
                player.level().getBlockState(newBlockpos).isAir())
                && SoulMinerVineBlock.hasAtLeasOneSolidNeighbourPerpendicularToGrowDirection(player.level(), newBlockpos, nearestDirection);
        return !player.getCooldowns().isOnCooldown(icon.getItem()) && canPlace && hasSeeds;
    }

    @Override
    public void execute(Level levelIn, Player player, HashMap<String, String> parameters) {
        if (!player.getCooldowns().isOnCooldown(icon.getItem())) {
            if (!levelIn.isClientSide) {
                Random rand = new Random();
                levelIn.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.CHICKEN_EGG, SoundSource.NEUTRAL, 0.5F,
                        0.4F / (rand.nextFloat() * 0.4F + 0.8F));

                PlayerSkillCapabilityInterface skillCap = IGenericCapability.getUnwrappedPlayerCapability(player, PlayerSkillCapability.INSTANCE);
                //setVine(levelIn, player, skillCap);
                setVine(levelIn, player, skillCap);
                player.getCooldowns().addCooldown(icon.getItem(), 20);
            }
        }
    }

    private void setVine(Level level, Player playerIn, PlayerSkillCapabilityInterface skillCap) {
        BlockPos playerBlockPos = playerIn.blockPosition();
        SoulMinerVineBlock createdBlock = ModBlocks.SOUL_MINER_VINE_BLOCK.get();
        BlockState playerBlockState = level.getBlockState(playerBlockPos);
        Vec3 playerLookVector = playerIn.getLookAngle();
        Direction nearestDirection = Direction.getNearest(playerLookVector.x, 0, playerLookVector.z);
        //DevilRpg.LOGGER.info("-------->Direction: {}", nearestDirection);
        BlockPos newBlockpos = playerBlockPos.relative(nearestDirection);

        // Consumir una semilla del inventario
        consumeSeed(playerIn);

        int skillPoints = skillCap.getSkillsPoints().get(SkillEnum.SOULMINERVINE);
        level
                .setBlockAndUpdate(
                        newBlockpos,
                        createdBlock.defaultBlockState()
                                .setValue(SoulMinerVineBlock.AGE, 1)
                                .setValue(SoulMinerVineBlock.DIRECTIONS, nearestDirection)
                                .setValue(SoulMinerVineBlock.LEVEL, skillPoints)
                                .setValue(SoulMinerVineBlock.HAS_CHILDREN, false)
                );
        // Marcar este bloque como la RAÍZ de la planta (destino de los items minados).
        if (level.getBlockEntity(newBlockpos) instanceof SoulMinerVineBlockEntity minerBE) {
            minerBE.setRootInfo(newBlockpos, null);
        }
    }

}
