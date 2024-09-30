package com.chipoodle.devilrpg.skillsystem.skillinstance;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.block.SoulShieldVineBlock;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityImplementation;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.init.ModBlocks;
import com.chipoodle.devilrpg.skillsystem.AbstractSkillExecutor;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Random;

public class SkillSoulShieldVine extends AbstractSkillExecutor {

    public SkillSoulShieldVine(PlayerSkillCapabilityImplementation parentCapability) {
        super(parentCapability);
    }

    @Override
    public SkillEnum getSkillEnum() {
        return SkillEnum.SOULSHIELDVINE;
    }

    @Override
    public boolean arePreconditionsMetBeforeConsumingResource(Player player) {
        BlockPos playerBlockPos = player.blockPosition();
        BlockState playerBlockState = player.level.getBlockState(playerBlockPos);
        Vec3 playerLookVector = player.getLookAngle();
        Direction nearestDirection = Direction.getNearest(playerLookVector.x, 0, playerLookVector.z);
        //DevilRpg.LOGGER.info("-------->Direction: {}", nearestDirection);
        BlockPos newBlockpos = playerBlockPos.relative(nearestDirection);
        boolean canPlace = playerBlockState.getBlock().equals(Blocks.AIR)
                && player.level.getBlockState(newBlockpos).getBlock().equals(Blocks.AIR)
                && SoulShieldVineBlock.hasAtLeasOneSolidNeighbourPerpendicularToGrowDirection(player.level, newBlockpos, nearestDirection);
        return !player.getCooldowns().isOnCooldown(icon.getItem()) && canPlace;
    }

    @Override
    public void execute(Level levelIn, Player player, HashMap<String, String> parameters) {
        if (!player.getCooldowns().isOnCooldown(icon.getItem())) {
            if (!levelIn.isClientSide) {
                Random rand = new Random();
                levelIn.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.CHICKEN_EGG, SoundSource.NEUTRAL, 0.5F,
                        0.4F / (rand.nextFloat() * 0.4F + 0.8F));

                PlayerSkillCapabilityInterface skillCap = IGenericCapability.getUnwrappedPlayerCapability(player,
                        PlayerSkillCapability.INSTANCE);
                //setVine(levelIn, player, skillCap);
                createDome(levelIn, player, skillCap);
                player.getCooldowns().addCooldown(icon.getItem(), 20);
            }
        }
    }

    private void createDome(Level level, Player playerIn, PlayerSkillCapabilityInterface skillCap) {
        BlockPos playerBlockPos = playerIn.blockPosition();
        SoulShieldVineBlock createdBlock = ModBlocks.SOUL_SHIELD_VINE_BLOCK.get();
        int radius = 2;  // Ajusta el radio de la esfera
        int skillPoints = skillCap.getSkillsPoints().get(SkillEnum.SOULSHIELDVINE);


        // Iterar sobre un cubo de dimensiones 2 * radius + 1 centrado en el jugador
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {

                    BlockPos domeBlockPos = playerBlockPos.offset(x, y, z);

                    // Calcular la distancia desde el centro (jugador) hasta el bloque actual
                    double distanceSquared = x * x + y * y + z * z;

                    // Comprobar si el bloque está dentro de la esfera (<= radio^2)
                    if (distanceSquared <= radius * radius && distanceSquared * 2.1 >= radius * radius) {
                        // Solo colocar el bloque si el espacio está vacío
                        if (level.getBlockState(domeBlockPos).getBlock().equals(Blocks.AIR)
                                || level.getBlockState(domeBlockPos).getMaterial().equals(Material.REPLACEABLE_PLANT)) {

                            Direction directionFromPlayer = getDirectionFromOffset(x, y, z);
                            DevilRpg.LOGGER.info(directionFromPlayer);

                            level.setBlockAndUpdate(
                                    domeBlockPos,
                                    createdBlock.defaultBlockState()
                                            .setValue(SoulShieldVineBlock.AGE, 1)
                                            .setValue(SoulShieldVineBlock.DIRECTIONS, directionFromPlayer) // Dirección arbitraria
                                            .setValue(SoulShieldVineBlock.LEVEL, skillPoints)
                            );
                        }
                    }
                }
            }
        }
    }

    // Método para obtener la dirección a partir del offset
    private Direction getDirectionFromOffset(int x, int y, int z) {
        if (Math.abs(x) > Math.abs(z)) {
            return x > 0 ? Direction.EAST : Direction.WEST;
        } else if (Math.abs(z) > Math.abs(x)) {
            return z > 0 ? Direction.SOUTH : Direction.NORTH;
        } else if (y > 0) {
            return Direction.UP;
        } else {
            return Direction.DOWN;
        }
    }
}
