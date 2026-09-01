package com.chipoodle.devilrpg.block;

import com.mojang.serialization.MapCodec;
import com.chipoodle.devilrpg.blockentity.SoulShieldVineBlockEntity;
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

public class SoulShieldVineBlock extends Block implements EntityBlock {
    public static final MapCodec<SoulShieldVineBlock> CODEC = simpleCodec(SoulShieldVineBlock::new);

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }


    public static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 15.0D, 15.0D, 15.0D);
    public static final DirectionProperty DIRECTIONS = BlockStateProperties.FACING;
    public static final IntegerProperty LEVEL = IntegerProperty.create("soulshieldvine_level", 0, 30);
    public static final DirectionProperty SOULVINE_FACING = DirectionProperty.create("soulshieldvine_facing", BlockStateProperties.FACING.getPossibleValues());

    public static final IntegerProperty DECAY_STAGE = IntegerProperty.create("decay_stage", 0, 3);

    protected final Direction growthDirection;

    public SoulShieldVineBlock(Properties properties) {
        super(properties);
        growthDirection = Direction.UP;
        this.registerDefaultState(this.defaultBlockState()
                .setValue(DIRECTIONS, growthDirection)
                .setValue(LEVEL, 0)
                .setValue(SOULVINE_FACING, Direction.UP)
                .setValue(DECAY_STAGE, 0)
        );

    }

    protected static @NotNull Block getSoulShieldVineBlock() {
        return ModBlocks.SOUL_SHIELD_VINE_BLOCK.get();
    }

    /*public static BlockState getGrowIntoState(BlockState p_221347_) {
        return p_221347_;
    }*/

    public static boolean canStay(@NotNull BlockState currentBlockState, @NotNull LevelReader levelReader, @NotNull BlockPos currentBlockPos, Direction growthDirection) {
        return canSurvive(levelReader, currentBlockPos);
    }

    public static @NotNull Boolean canSurvive(@NotNull LevelReader levelReader, @NotNull BlockPos currentBlockPos) {
        Block soulVineBlock = getSoulShieldVineBlock();
        Collection<Direction> possibleValues = DIRECTIONS.getPossibleValues();
        for (Direction possibleDirection : possibleValues) {
            BlockState nextState = getNextState(levelReader, currentBlockPos, possibleDirection);
            if (nextState.is(soulVineBlock)) {
                return true;
            }
        }
        return false;
    }

    /*public static Boolean hasAtLeasOneSolidNeighbourPerpendicularToGrowDirection(@NotNull LevelReader levelReader, @NotNull BlockPos currentBlockPos, Direction growDirection) {
        List<Direction> possibleValues = new ArrayList<>(DIRECTIONS.getPossibleValues()
                .stream()
                .filter(x -> x != growDirection)
                .filter(x -> x != growDirection.getOpposite())
                .sorted(Comparator.comparingInt(Direction::get3DDataValue)).toList());
        //possibleValues.remove(Direction.UP);
        //possibleValues.add(0, Direction.UP);
        for (Direction possibleDirection : possibleValues) {
            BlockState nextState = getNextState(levelReader, currentBlockPos, possibleDirection);
            if (!nextState.isAir() && !(nextState.getBlock() instanceof SoulShieldVineBlock)) {
                return true;
            }
        }
        return false;
    }*/


    @NotNull
    private static BlockState getNextState(@NotNull LevelReader levelReader, @NotNull BlockPos currentBlockPos, Direction possibleDirection) {
        BlockPos nextPos = currentBlockPos.relative(possibleDirection);
        return levelReader.getBlockState(nextPos);
    }

    public @NotNull BlockState getStateForPlacement(LevelAccessor levelAccessor) {
        return this.defaultBlockState()
                .setValue(DIRECTIONS, Direction.getRandom(levelAccessor.getRandom()))
                .setValue(LEVEL, 0)
                .setValue(SOULVINE_FACING, Direction.UP)
                .setValue(DECAY_STAGE, 0)
                ;
    }

    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext p_53868_) {
        BlockState blockstate = p_53868_.getLevel().getBlockState(p_53868_.getClickedPos().relative(this.growthDirection));
        return !blockstate.is(getSoulShieldVineBlock()) ? this.getStateForPlacement(p_53868_.getLevel()) : getSoulShieldVineBlock().defaultBlockState();
    }

    /*public boolean isRandomlyTicking(BlockState blockState) {
        return true;
    }*/

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> stateDefinition) {
        stateDefinition
                .add(DIRECTIONS)
                .add(LEVEL)
                .add(SOULVINE_FACING)
                .add(DECAY_STAGE)
        ;
    }

    public @NotNull VoxelShape getShape(@NotNull BlockState p_53880_, @NotNull BlockGetter p_53881_, @NotNull BlockPos p_53882_, @NotNull CollisionContext p_53883_) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return ModEntityBlocks.SOUL_SHIELD_VINE_ENTITY_BLOCK.get().create(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, @NotNull BlockState blockState, @NotNull BlockEntityType<T> type) {
        return level.isClientSide ? null : (alevel, pos, aBlockstate, blockEntity) -> {
            if (blockEntity instanceof SoulShieldVineBlockEntity soulVineBlockEntity && alevel.getGameTime() % 3 == 0) {
                soulVineBlockEntity.tick(blockState, (ServerLevel) alevel, pos, alevel.getRandom());
            }
        };
    }
}
