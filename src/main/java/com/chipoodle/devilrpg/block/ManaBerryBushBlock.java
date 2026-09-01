package com.chipoodle.devilrpg.block;

import net.minecraft.world.ItemInteractionResult;
import com.mojang.serialization.MapCodec;
import com.chipoodle.devilrpg.blockentity.ManaBerryBlockEntity;
import com.chipoodle.devilrpg.init.ModEntityBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ManaBerryBushBlock extends BushBlock implements EntityBlock {
    public static final MapCodec<ManaBerryBushBlock> CODEC = simpleCodec(ManaBerryBushBlock::new);

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    //private static final float HURT_SPEED_THRESHOLD = 0.003F;
    //public static final int MAX_AGE = 3;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;

    public static final IntegerProperty LEVEL = IntegerProperty.create("soulvine_level", 0, 30);
    public static final IntegerProperty DECAY_STAGE = IntegerProperty.create("decay_stage", 0, 3);
    private static final VoxelShape SAPLING_SHAPE = Block.box(3.0D, 0.0D, 3.0D, 13.0D, 8.0D, 13.0D);
    private static final VoxelShape MID_GROWTH_SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 16.0D, 15.0D);

    public ManaBerryBushBlock(Properties p_57249_) {
        super(p_57249_);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(AGE, 0)
                .setValue(LEVEL, 0)
                .setValue(DECAY_STAGE, 0)
        );
    }

    public @NotNull ItemStack getCloneItemStack(@NotNull BlockGetter blockGetter, @NotNull BlockPos blockPos, @NotNull BlockState blockState) {
        return new ItemStack(Items.SWEET_BERRIES);
    }

    public @NotNull VoxelShape getShape(BlockState p_57291_, @NotNull BlockGetter blockGetter, @NotNull BlockPos blockPos, @NotNull CollisionContext collisionContext) {
        if (p_57291_.getValue(AGE) == 0) {
            return SAPLING_SHAPE;
        } else {
            return p_57291_.getValue(AGE) < 3 ? MID_GROWTH_SHAPE : super.getShape(p_57291_, blockGetter, blockPos, collisionContext);
        }
    }

    public boolean isRandomlyTicking(BlockState p_57284_) {
        return p_57284_.getValue(AGE) < 3;
    }

    public @NotNull ItemInteractionResult useItemOn(@NotNull ItemStack itemStack, @NotNull BlockState blockState, @NotNull Level level, @NotNull BlockPos blockPos, @NotNull Player player, @NotNull InteractionHand interactionHand, @NotNull BlockHitResult blockHitResult) {
        int i = blockState.getValue(AGE);
        boolean flag = i == 3;
        if (!flag && player.getItemInHand(interactionHand).is(Items.BONE_MEAL)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        } else if (i > 1) {
            int j = 1 + level.random.nextInt(2);
            popResource(level, blockPos, new ItemStack(Items.SWEET_BERRIES, j + (flag ? 1 : 0)));
            level.playSound((Player) null, blockPos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 0.8F + level.random.nextFloat() * 0.4F);
            BlockState blockstate = blockState.setValue(AGE, 1);
            level.setBlock(blockPos, blockstate, 2);
            level.gameEvent(GameEvent.BLOCK_CHANGE, blockPos, GameEvent.Context.of(player, blockstate));
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        } else {
            return super.useItemOn(itemStack, blockState, level, blockPos, player, interactionHand, blockHitResult);
        }
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_57282_) {
        p_57282_
                .add(AGE)
                .add(LEVEL)
                .add(DECAY_STAGE)
        ;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return ModEntityBlocks.MANA_BERRY_ENTITY_BLOCK.get().create(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, @NotNull BlockState blockState, @NotNull BlockEntityType<T> type) {
        return level.isClientSide ? null : (alevel, pos, aBlockstate, blockEntity) -> {
            if (blockEntity instanceof ManaBerryBlockEntity manaBerryBlockEntity && alevel.getGameTime() % 20 == 0) {
                manaBerryBlockEntity.tick(blockState, (ServerLevel) alevel, pos, alevel.getRandom());
            }
        };
    }
}
