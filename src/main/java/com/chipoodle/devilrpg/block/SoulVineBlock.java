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

public class SoulVineBlock extends Block implements EntityBlock {

    public static final VoxelShape SHAPE = Block.box(4.0D, 0.0D, 4.0D, 12.0D, 15.0D, 12.0D);
    public static final IntegerProperty AGE = BlockStateProperties.AGE_25;
    public static final DirectionProperty DIRECTIONS = BlockStateProperties.FACING;
    public static final IntegerProperty LEVEL = IntegerProperty.create("soulvine_level", 0, 30);
    private static final Integer MAX_AGE = 25;
    protected final Direction growthDirection;
    private BlockPos currentBlockPos;

    public SoulVineBlock(Properties properties) {
        super(properties);
        growthDirection = Direction.UP;
        this.registerDefaultState(this.getStateDefinition().any().setValue(AGE, 0));
        this.registerDefaultState(this.getStateDefinition().any().setValue(DIRECTIONS, growthDirection));
        this.registerDefaultState(this.getStateDefinition().any().setValue(LEVEL, 0));
    }

    public SoulVineBlock(Properties properties, BlockPos pos) {
        this(properties);
        currentBlockPos = pos;
    }

    protected static @NotNull Block getBodyBlock() {
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

    public static boolean canStay(@NotNull BlockState blockState, @NotNull LevelReader levelReader, @NotNull BlockPos blockPos, Direction growthDirection) {
        BlockState blockstate = levelReader.getBlockState(blockPos);

        if (!canAttachTo(blockstate)) {
            return false;
        } else {
            return blockstate.is(getBodyBlock()) || blockstate.isFaceSturdy(levelReader, blockPos, growthDirection);
        }
    }

    public @NotNull BlockState getStateForPlacement(LevelAccessor levelAccessor) {
        return this.defaultBlockState().setValue(AGE, levelAccessor.getRandom().nextInt(MAX_AGE)).setValue(DIRECTIONS, Direction.getRandom(levelAccessor.getRandom()));
    }

    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext p_53868_) {
        BlockState blockstate = p_53868_.getLevel().getBlockState(p_53868_.getClickedPos().relative(this.growthDirection));
        return !blockstate.is(this.getBodyBlock()) ? this.getStateForPlacement(p_53868_.getLevel()) : this.getBodyBlock().defaultBlockState();
    }

    public boolean isRandomlyTicking(BlockState blockState) {
        return blockState.getValue(AGE) < MAX_AGE;
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> stateDefinition) {
        stateDefinition.add(AGE).add(DIRECTIONS).add(LEVEL);
    }

    public VoxelShape getShape(@NotNull BlockState p_53880_, @NotNull BlockGetter p_53881_, @NotNull BlockPos p_53882_, @NotNull CollisionContext p_53883_) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModEntityBlocks.SOUL_VINE_ENTITY_BLOCK.get().create(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState blockState, BlockEntityType<T> type) {
        return level.isClientSide ? null : (alevel, pos, aBlockstate, blockEntity) -> {
            if (blockEntity instanceof SoulVineBlockEntity soulVineBlockEntity && alevel.getGameTime() % 10 == 0) {
                soulVineBlockEntity.tick(blockState, (ServerLevel) alevel, pos, alevel.getRandom());
            }
        };
    }
}
