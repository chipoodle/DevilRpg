package com.chipoodle.devilrpg.blockentity;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.block.SoulLichenBlock;
import com.chipoodle.devilrpg.init.ModEntityBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.NotNull;

import static com.chipoodle.devilrpg.block.SoulLichenBlock.*;


public class SoulLichenBlockEntity extends BlockEntity {

    public static final int TICK_FACTOR = 10;
    public static int LICHEN_BLOCK_LEVEL_OFFSET = 3;
    private Long creationTime = null;
    private int childCount = 0;

    private int step = 0;
    private int nextStep = 0;

    public SoulLichenBlockEntity(BlockPos pos, BlockState state) {
        super(ModEntityBlocks.SOUL_LICHEN_ENTITY_BLOCK.get(), pos, state);
    }


    public void tick(@NotNull BlockState currentBlockState, @NotNull ServerLevel serverLevel, @NotNull BlockPos currentBlockPos, @NotNull RandomSource randomSource) {
        long currentGameTime = serverLevel.getGameTime();
        if (creationTime == null) {
            creationTime = currentGameTime;
        }

        int skillLevel = currentBlockState.hasProperty(SKILL_LEVEL) ? currentBlockState.getValue(SKILL_LEVEL) : 1;
        int durationInTicks = skillLevel * TICK_FACTOR + 60;
        long durationTime = creationTime + durationInTicks;
        long remainingTime = durationTime - currentGameTime;

        SoulLichenBlock block = ((SoulLichenBlock) currentBlockState.getBlock());
        if (remainingTime <= 0 || block.getOwner() == null) {
            serverLevel.destroyBlock(currentBlockPos, true);
            return;
        }

        step = (int) Math.ceil((float) skillLevel / (float) 4);
        if (step != nextStep) { // limits the spreading ths limiting the area when skill points are higher
            nextStep = step;
            DevilRpg.LOGGER.debug("skillLevel: {} step: {}", skillLevel, step);
            Direction face = currentBlockState.hasProperty(FACE) ? currentBlockState.getValue(FACE) : Direction.DOWN;
            Direction direction = currentBlockState.hasProperty(DIRECTION) ? currentBlockState.getValue(DIRECTION) : Direction.DOWN;
            long faceCount = currentBlockState.getProperties().stream()
                    .filter(prop -> !prop.equals(WATERLOGGED))
                    .filter(prop -> prop instanceof BooleanProperty)
                    .filter(prop -> currentBlockState.getValue(prop).equals(true))
                    .count();

            if (childCount == 0 || (faceCount > 1 && childCount < faceCount)) {
                childCount++;
                long l = block.spreadFromFaceTowardAllDirections(currentBlockState, serverLevel, currentBlockPos, face, false, Math.round(skillLevel - LICHEN_BLOCK_LEVEL_OFFSET), 1);
            }

        }
    }
}

