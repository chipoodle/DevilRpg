package com.chipoodle.devilrpg.skillsystem.skillinstance;

import com.chipoodle.devilrpg.block.BloomingSanctuaryBlock;
import com.chipoodle.devilrpg.block.SoulVineBlock;
import com.chipoodle.devilrpg.blockentity.BloomingSanctuaryBlockEntity;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityImplementation;
import com.chipoodle.devilrpg.init.ModBlocks;
import com.chipoodle.devilrpg.skillsystem.AbstractSkillSeedsInInventoryExecutor;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;

public class SkillBloomingSanctuary extends AbstractSkillSeedsInInventoryExecutor {

    public SkillBloomingSanctuary(PlayerSkillCapabilityImplementation parentCapability) {
        super(parentCapability);
    }

    @Override
    public SkillEnum getSkillEnum() {
        return SkillEnum.BLOOMING_SANCTUARY;
    }

    @Override
    public boolean arePreconditionsMetBeforeConsumingResource(Player player) {

        BlockPos playerBlockPos = player.blockPosition();
        //BlockState playerBlockState = player.level.getBlockState(playerBlockPos);
        Vec3 playerLookVector = player.getLookAngle();
        Direction nearestDirection = Direction.getNearest(playerLookVector.x, 0, playerLookVector.z);
        //DevilRpg.LOGGER.info("-------->Direction: {}", nearestDirection);
        BlockPos newBlockpos = playerBlockPos.relative(nearestDirection);

        boolean hasSeeds = super.arePreconditionsMetBeforeConsumingResource(player);

        boolean canPlace = (player.level.getBlockState(newBlockpos).getMaterial().equals(Material.REPLACEABLE_PLANT) ||
                player.level.getBlockState(newBlockpos).getMaterial().equals(Material.TOP_SNOW) ||
                player.level.getBlockState(newBlockpos).getBlock().equals(Blocks.AIR))
                && SoulVineBlock.hasAtLeasOneSolidNeighbourPerpendicularToGrowDirection(player.level, newBlockpos, nearestDirection);


        return !player.getCooldowns().isOnCooldown(icon.getItem()) && canPlace && hasSeeds;
    }

    @Override
    public void execute(Level levelIn, Player player, HashMap<String, String> parameters) {
        if (!player.getCooldowns().isOnCooldown(icon.getItem())) {
            if (!levelIn.isClientSide) {
                levelIn.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 0.5F, 1.0F);
                createSanctuary(player, parentCapability.getSkillsPoints().get(SkillEnum.BLOOMING_SANCTUARY));
                player.getCooldowns().addCooldown(icon.getItem(), 200); // Cooldown de 10 segundos
            }
        }
    }

    private void createSanctuary(Player playerIn, int skillPoints) {
        BlockPos playerBlockPos = playerIn.blockPosition();
        BloomingSanctuaryBlock createdBlock = ModBlocks.BLOOMING_SANCTUARY_BLOCK.get();
        Vec3 playerLookVector = playerIn.getLookAngle();
        Direction nearestDirection = Direction.getNearest(playerLookVector.x, 0, playerLookVector.z);
        BlockPos newBlockpos = playerBlockPos.relative(nearestDirection);

        consumeSeed(playerIn);


        playerIn.getLevel()
                .setBlockAndUpdate(
                        newBlockpos,
                        createdBlock.defaultBlockState()
                                .setValue(BloomingSanctuaryBlock.DIRECTIONS, nearestDirection)
                                .setValue(BloomingSanctuaryBlock.LEVEL, skillPoints)
                                .setValue(BloomingSanctuaryBlock.IS_TOP, false)
                );

        // Establecer el propietario del bloque
        if (playerIn.getLevel().getBlockEntity(newBlockpos) instanceof BloomingSanctuaryBlockEntity blockEntity) {
            blockEntity.setOwnerUUID(playerIn.getUUID());
        }


        BlockState sporeBlossomState = ModBlocks.UPWARD_SPORE_BLOSSOM_BLOCK.get().defaultBlockState();
        BlockPos topBlockPos = newBlockpos.above();
        if (playerIn.getLevel().getBlockState(topBlockPos).getBlock().equals(Blocks.AIR)) {
            playerIn.getLevel()
                    .setBlockAndUpdate(
                            topBlockPos,
                            sporeBlossomState
                    );
        }

    }

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


    private void applySanctuaryEffects(Level level, Player player, int radius) {
        BlockPos playerPos = player.blockPosition();
        level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius))
                .forEach(entity -> {
                    double distance = entity.blockPosition().distSqr(playerPos);

                    if (entity instanceof Player || entity.isAlliedTo(player)) {
                        // Aplicar regeneración a jugadores y aliados
                        entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 100, 1));
                    } else if (distance <= radius * radius) {
                        // Aplicar ralentización a enemigos
                        entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
                    }
                });
    }
}
