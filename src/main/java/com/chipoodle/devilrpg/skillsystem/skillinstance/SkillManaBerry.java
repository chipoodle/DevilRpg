package com.chipoodle.devilrpg.skillsystem.skillinstance;

import com.chipoodle.devilrpg.block.ManaBerryBushBlock;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityImplementation;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.init.ModBlocks;
import com.chipoodle.devilrpg.skillsystem.AbstractSkillSeedsInInventoryExecutor;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;

public class SkillManaBerry extends AbstractSkillSeedsInInventoryExecutor {

    public SkillManaBerry(PlayerSkillCapabilityImplementation parentCapability) {
        super(parentCapability);
    }

    @Override
    public SkillEnum getSkillEnum() {
        return SkillEnum.MANA_BERRY;
    }

    @Override
    public boolean arePreconditionsMetBeforeConsumingResource(Player player) {
        boolean hasSeeds = super.arePreconditionsMetBeforeConsumingResource(player);
        Vec3 playerLookVector = player.getLookAngle();
        Direction nearestDirection = Direction.getNearest(playerLookVector.x, 0, playerLookVector.z);
        BlockPos playerBlockPos = player.blockPosition();
        BlockPos newBlockpos = playerBlockPos.relative(nearestDirection);
        BlockState belowBlockstate = player.level.getBlockState(newBlockpos.below());
        boolean canPlace = player.level.isEmptyBlock(newBlockpos) && belowBlockstate.is(BlockTags.DIRT);

        return !player.getCooldowns().isOnCooldown(icon.getItem()) && canPlace && hasSeeds;
    }

    @Override
    public void execute(Level levelIn, Player player, HashMap<String, String> parameters) {
        if (!player.getCooldowns().isOnCooldown(icon.getItem())) {
            if (!levelIn.isClientSide) {
                PlayerSkillCapabilityInterface skillCap = IGenericCapability.getUnwrappedPlayerCapability(player, PlayerSkillCapability.INSTANCE);
                setVine(levelIn, player, skillCap);
                player.getCooldowns().addCooldown(icon.getItem(), 20);
            }
        }
    }

    private void setVine(Level level, Player playerIn, PlayerSkillCapabilityInterface skillCap) {
        ManaBerryBushBlock createdBlock = ModBlocks.MANA_BERRY_BUSH_BLOCK.get();
        Vec3 playerLookVector = playerIn.getLookAngle();
        Direction nearestDirection = Direction.getNearest(playerLookVector.x, 0, playerLookVector.z);
        BlockPos playerBlockPos = playerIn.blockPosition();
        BlockPos newBlockpos = playerBlockPos.relative(nearestDirection);
        BlockState belowBlockstate = level.getBlockState(newBlockpos.below());
        if (level.isEmptyBlock(newBlockpos) && belowBlockstate.is(BlockTags.DIRT)) {
            // Consumir una semilla del inventario
            consumeSeed(playerIn);

            int skillPoints = skillCap.getSkillsPoints().get(SkillEnum.MANA_BERRY);
            level
                    .setBlockAndUpdate(
                            newBlockpos,
                            createdBlock.defaultBlockState()
                                    //.setValue(SoulVineBlock.AGE, 1)
                                    //.setValue(SoulVineBlock.DIRECTIONS, nearestDirection)
                                    .setValue(ManaBerryBushBlock.LEVEL, skillPoints)
                            //.setValue(SoulVineBlock.HAS_CHILDREN, false)
                    );

        }
    }
}
