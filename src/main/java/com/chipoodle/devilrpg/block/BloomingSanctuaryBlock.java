package com.chipoodle.devilrpg.block;

import com.chipoodle.devilrpg.blockentity.BloomingSanctuaryBlockEntity;
import com.chipoodle.devilrpg.init.ModBlocks;
import com.chipoodle.devilrpg.init.ModEntityBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class BloomingSanctuaryBlock extends FallingBlock implements EntityBlock {

    public static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 15.0D, 15.0D, 15.0D);
    public static final DirectionProperty DIRECTIONS = BlockStateProperties.FACING;
    public static final IntegerProperty LEVEL = IntegerProperty.create("bloomingsanctuary_level", 0, 30);
    public static final DirectionProperty BLOOMING_SANCTUARY_FACING = DirectionProperty.create("bloomingsanctuary_facing", BlockStateProperties.FACING.getPossibleValues());
    public static final IntegerProperty DECAY_STAGE = IntegerProperty.create("decay_stage", 0, 3);
    public static final BooleanProperty IS_TOP = BooleanProperty.create("is_top");


    protected final Direction growthDirection;

    public BloomingSanctuaryBlock(Properties properties) {
        super(properties);
        growthDirection = Direction.UP;
        this.registerDefaultState(this.defaultBlockState()
                .setValue(DIRECTIONS, growthDirection)
                .setValue(LEVEL, 0)
                .setValue(BLOOMING_SANCTUARY_FACING, Direction.UP)
                .setValue(DECAY_STAGE, 0)
                .setValue(IS_TOP,false)
        );

    }

    protected static @NotNull Block getBloomingSanctuaryBlock() {
        return ModBlocks.BLOOMING_SANCTUARY_BLOCK.get();
    }

    public static boolean canStay(@NotNull BlockState currentBlockState, @NotNull LevelReader levelReader, @NotNull BlockPos currentBlockPos, Direction growthDirection) {
        return canSurvive(levelReader, currentBlockPos);
    }

    public static @NotNull Boolean canSurvive(@NotNull LevelReader levelReader, @NotNull BlockPos currentBlockPos) {
        Block soulVineBlock = getBloomingSanctuaryBlock();
        Collection<Direction> possibleValues = DIRECTIONS.getPossibleValues();
        for (Direction possibleDirection : possibleValues) {
            BlockState nextState = getNextState(levelReader, currentBlockPos, possibleDirection);
            if (nextState.is(soulVineBlock)) {
                return true;
            }
        }
        return false;
    }

    @NotNull
    private static BlockState getNextState(@NotNull LevelReader levelReader, @NotNull BlockPos currentBlockPos, Direction possibleDirection) {
        BlockPos nextPos = currentBlockPos.relative(possibleDirection);
        return levelReader.getBlockState(nextPos);
    }

    public @NotNull BlockState getStateForPlacement(LevelAccessor levelAccessor) {
        return this.defaultBlockState()
                .setValue(DIRECTIONS, Direction.getRandom(levelAccessor.getRandom()))
                .setValue(LEVEL, 0)
                .setValue(BLOOMING_SANCTUARY_FACING, Direction.UP)
                .setValue(DECAY_STAGE, 0)
                .setValue(IS_TOP,false)
                ;
    }

    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext p_53868_) {
        BlockState blockstate = p_53868_.getLevel().getBlockState(p_53868_.getClickedPos().relative(this.growthDirection));
        return !blockstate.is(getBloomingSanctuaryBlock()) ? this.getStateForPlacement(p_53868_.getLevel()) : getBloomingSanctuaryBlock().defaultBlockState();
    }

    /*public boolean isRandomlyTicking(BlockState blockState) {
        return true;
    }*/

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> stateDefinition) {
        stateDefinition
                .add(DIRECTIONS)
                .add(LEVEL)
                .add(BLOOMING_SANCTUARY_FACING)
                .add(DECAY_STAGE)
                .add(IS_TOP)
        ;
    }

    public @NotNull VoxelShape getShape(@NotNull BlockState p_53880_, @NotNull BlockGetter p_53881_, @NotNull BlockPos p_53882_, @NotNull CollisionContext p_53883_) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return ModEntityBlocks.BLOOMING_SANCTUARY_ENTITY_BLOCK.get().create(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, @NotNull BlockState blockState, @NotNull BlockEntityType<T> type) {
        return level.isClientSide ? null : (alevel, pos, aBlockstate, blockEntity) -> {
            if (blockEntity instanceof BloomingSanctuaryBlockEntity bloomingSanctuaryBlockEntity && alevel.getGameTime() % 20 == 0) {
                bloomingSanctuaryBlockEntity.tick(blockState, (ServerLevel) alevel, pos, alevel.getRandom());
            }
        };
    }


    public boolean propagatesSkylightDown(BlockState p_153695_, BlockGetter p_153696_, BlockPos p_153697_) {
        return true;
    }

    public float getShadeBrightness(BlockState p_153689_, BlockGetter p_153690_, BlockPos p_153691_) {
        return 1.0F;
    }

}
