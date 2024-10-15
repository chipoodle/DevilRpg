package com.chipoodle.devilrpg.blockentity;


import com.chipoodle.devilrpg.init.ModEntityBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jetbrains.annotations.NotNull;

import static com.chipoodle.devilrpg.block.ManaBerryBushBlock.*;

public class ManaBerryBlockEntity extends BlockEntity {

    public static final int TICK_FACTOR = 20;
    private Long timeOfCreation = null;


    public ManaBerryBlockEntity(BlockPos pos, BlockState state) {
        super(ModEntityBlocks.MANA_BERRY_ENTITY_BLOCK.get(), pos, state);
    }

    public boolean tick(@NotNull BlockState state, @NotNull ServerLevel world, @NotNull BlockPos currentBlockPos, @NotNull RandomSource randomSource) {
        if (timeOfCreation == null) {
            timeOfCreation = world.getGameTime();
        }

        Integer currentAge = state.getValue(AGE);
        int skillLevel = state.getValue(LEVEL);
        int currentDecay = state.getValue(DECAY_STAGE);
        Integer duration = skillLevel * TICK_FACTOR + 80;
        /*boolean hasChildren = state.getValue(HAS_CHILDREN);*/

        //DevilRpg.LOGGER.info("-------->tick. Age {} ", currentAge);

        long timeElapsed = world.getGameTime() - timeOfCreation;
        int newDecayStage = (int) ((timeElapsed * 4) / duration); // 4 etapas de decadencia

        // Actualiza el estado de decadencia
        if (newDecayStage != currentDecay && newDecayStage <= 3) {
            state = state.setValue(DECAY_STAGE, newDecayStage);
            world.setBlockAndUpdate(currentBlockPos, state);
        }

        if (timeOfCreation + duration < world.getGameTime()) {
            world.destroyBlock(currentBlockPos, true);
            return false;
        }

        /*double length = skillLevel * 0.5 + 10;
        if (currentAge < length) {
            //DevilRpg.LOGGER.info("-------->Direction: {}, AGE {}, LEVEL {}, duration: {}", currentDirection, currentAge, skillLevel, duration);


        }*/

        if (currentAge < 3 && newDecayStage < 4) {
            BlockState blockstate = state.setValue(AGE, currentAge + 1);
            world.setBlock(currentBlockPos, blockstate, 2);
            world.gameEvent(GameEvent.BLOCK_CHANGE, currentBlockPos, GameEvent.Context.of(blockstate));
        }
        if (currentAge == 3 && world.getGameTime() % 8 == 0) {
            popResource(world, currentBlockPos, new ItemStack(Items.SWEET_BERRIES, 1));
            world.playSound((Player) null, currentBlockPos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 0.8F + world.random.nextFloat() * 0.4F);
        }
        if(newDecayStage >= 4){
            BlockState blockstate = state.setValue(AGE, 0);
            world.setBlock(currentBlockPos, blockstate, 2);
            world.gameEvent(GameEvent.BLOCK_CHANGE, currentBlockPos, GameEvent.Context.of(blockstate));
        }
        return true;
    }


}

