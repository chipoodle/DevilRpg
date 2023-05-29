package com.chipoodle.devilrpg.block;

import com.chipoodle.devilrpg.blockentity.SoulVineBlockEntity;
import com.chipoodle.devilrpg.init.ModBlocks;
import com.chipoodle.devilrpg.init.ModEntityBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.NetherVines;
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

    public static boolean canGrowInto(BlockState p_154869_) {
        return NetherVines.isValidGrowthState(p_154869_);
    }

    public static BlockState getGrowIntoState(BlockState p_221347_, RandomSource p_221348_) {
        return p_221347_.cycle(AGE);
    }

    protected static boolean canAttachTo(BlockState p_153321_) {
        return !p_153321_.isAir();
    }


    public static boolean canStay(@NotNull BlockState currentBlockState, @NotNull LevelReader levelReader, @NotNull BlockPos currentBlockPos, Direction growthDirection) {
        BlockPos newBlockpos;
        if (currentBlockState.getValue(AGE) == 1)
            newBlockpos = currentBlockPos;
        else {
            if (!areSurroundingBlocksPlausible(levelReader, currentBlockPos))
                return false;
            newBlockpos = currentBlockPos.relative(growthDirection);
        }
        BlockState newBlockState = levelReader.getBlockState(newBlockpos);
        return true;

        /*if (!canAttachTo(newBlockState)) {

            // buscar una nueva dirección donde si

            return false;
        } else {
            boolean faceSturdy = newBlockState.isFaceSturdy(levelReader, newBlockpos, growthDirection);
            return faceSturdy;
        }*/
    }

    public static Object getNextDirection(@NotNull BlockState currentBlockState, @NotNull LevelReader levelReader, @NotNull BlockPos currentBlockPos, Direction growthDirection) {
        BlockPos newBlockpos = currentBlockPos.relative(growthDirection);
        return null;

    }

    /**
     * Verifies if at least one cube around is soulvine
     *
     * @param levelReader     a level reader
     * @param currentBlockPos current block position
     * @return wheter or not an adyascent block is soulvine
     */
    private static boolean areSurroundingBlocksPlausible(@NotNull LevelReader levelReader, @NotNull BlockPos currentBlockPos) {
        Block soulVineBlock = getSoulVineBlock();


        boolean isSoulVine = false;
        boolean isFaceSturdy = false;
        Direction directionFromsourceVine = null;
        for (Direction possibleDirection : DIRECTIONS.getPossibleValues()) {
            BlockState nextState = getNextState(levelReader, currentBlockPos, possibleDirection);

            //DevilRpg.LOGGER.info("-----------------{} {}---------------",growDirection,currentBlockPos);
            //DevilRpg.LOGGER.info("direction: {} next block: {} next position: {}",possibleDirection, nextState.getBlock(), nextPos);

            if (nextState.is(soulVineBlock)) {
                directionFromsourceVine = possibleDirection;
                isSoulVine = true;
            }

            if (!nextState.is(soulVineBlock) && (canAttachTo(nextState))) {
                isFaceSturdy = true;
            }

            if (isSoulVine && isFaceSturdy)
                return true;
        }
        if(isSoulVine){
            Direction oppositeDirectionSourceVine = directionFromsourceVine.getOpposite();
            Direction finalDirectionFromsourceVine = directionFromsourceVine;
            List<Direction> directions = DIRECTIONS.getPossibleValues().stream()
                    .filter(dir -> !dir.equals(oppositeDirectionSourceVine))
                    .filter(dir -> !dir.equals(finalDirectionFromsourceVine))
                    .toList();
            for (Direction possibleDirection : directions){

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
