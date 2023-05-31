package com.chipoodle.devilrpg.block;

import com.chipoodle.devilrpg.blockentity.SoulVineBlockEntity;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public class SoulVineBlock extends Block implements EntityBlock {

    public static final VoxelShape SHAPE = Block.box(4.0D, 0.0D, 4.0D, 12.0D, 15.0D, 12.0D);
    public static final IntegerProperty AGE = BlockStateProperties.AGE_25;
    public static final DirectionProperty DIRECTIONS = BlockStateProperties.FACING;
    public static final IntegerProperty LEVEL = IntegerProperty.create("soulvine_level", 0, 30);
    private static final Integer MAX_AGE = 25;
    protected final Direction growthDirection;

    public SoulVineBlock(Properties properties) {
        super(properties);
        growthDirection = Direction.UP;
        this.registerDefaultState(this.getStateDefinition().any().setValue(AGE, 0));
        this.registerDefaultState(this.getStateDefinition().any().setValue(DIRECTIONS, growthDirection));
        this.registerDefaultState(this.getStateDefinition().any().setValue(LEVEL, 0));
    }

    protected static @NotNull Block getSoulVineBlock() {
        return ModBlocks.SOUL_VINE_BLOCK.get();
    }

    public static BlockState getGrowIntoState(BlockState p_221347_) {
        return p_221347_.cycle(AGE);
    }


    public static boolean canStay(@NotNull BlockState currentBlockState, @NotNull LevelReader levelReader, @NotNull BlockPos currentBlockPos, Direction growthDirection) {
        BlockPos newBlockpos;
        if (currentBlockState.getValue(AGE) == 1)
            newBlockpos = currentBlockPos;
        else {
            if (!canSurvive(levelReader, currentBlockPos))
                return false;
            newBlockpos = currentBlockPos.relative(growthDirection);
        }
        BlockState newBlockState = levelReader.getBlockState(newBlockpos);
        return true;
    }

    public static @NotNull Boolean canSurvive(@NotNull LevelReader levelReader, @NotNull BlockPos currentBlockPos) {
        Block soulVineBlock = getSoulVineBlock();
        Collection<Direction> possibleValues = DIRECTIONS.getPossibleValues();
        for (Direction possibleDirection : possibleValues) {
            BlockState nextState = getNextState(levelReader, currentBlockPos, possibleDirection);
            if (nextState.is(soulVineBlock)) {
               return true;
            }
        }
        return false;
    }

    public static Boolean hasAtLeasOneSolidNeighbourPerpendicularToGrowDirection(@NotNull LevelReader levelReader, @NotNull BlockPos currentBlockPos, Direction growDirection) {
        List<Direction> possibleValues = new ArrayList<>(DIRECTIONS.getPossibleValues()
                .stream()
                .filter(x -> x != growDirection)
                .filter(x -> x != growDirection.getOpposite())
                .sorted(Comparator.comparingInt(Direction::get3DDataValue)).toList());
        for (Direction possibleDirection : possibleValues) {
            BlockState nextState = getNextState(levelReader, currentBlockPos, possibleDirection);
            if (!nextState.isAir()) {
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
        return this.defaultBlockState().setValue(AGE, levelAccessor.getRandom().nextInt(MAX_AGE)).setValue(DIRECTIONS, Direction.getRandom(levelAccessor.getRandom()));
    }

    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext p_53868_) {
        BlockState blockstate = p_53868_.getLevel().getBlockState(p_53868_.getClickedPos().relative(this.growthDirection));
        return !blockstate.is(getSoulVineBlock()) ? this.getStateForPlacement(p_53868_.getLevel()) : getSoulVineBlock().defaultBlockState();
    }

    public boolean isRandomlyTicking(BlockState blockState) {
        return blockState.getValue(AGE) < MAX_AGE;
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> stateDefinition) {
        stateDefinition.add(AGE).add(DIRECTIONS).add(LEVEL);
    }

    public @NotNull VoxelShape getShape(@NotNull BlockState p_53880_, @NotNull BlockGetter p_53881_, @NotNull BlockPos p_53882_, @NotNull CollisionContext p_53883_) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return ModEntityBlocks.SOUL_VINE_ENTITY_BLOCK.get().create(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, @NotNull BlockState blockState, @NotNull BlockEntityType<T> type) {
        return level.isClientSide ? null : (alevel, pos, aBlockstate, blockEntity) -> {
            if (blockEntity instanceof SoulVineBlockEntity soulVineBlockEntity && alevel.getGameTime() % 10 == 0) {
                soulVineBlockEntity.tick(blockState, (ServerLevel) alevel, pos, alevel.getRandom());
            }
        };
    }
}
