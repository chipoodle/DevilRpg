package com.chipoodle.devilrpg.block;

import com.chipoodle.devilrpg.blockentity.SoulLichenBlockEntity;
import com.chipoodle.devilrpg.init.ModEntities;
import com.chipoodle.devilrpg.init.ModEntityBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

public class SoulLichenBlock extends MultifaceBlock implements SimpleWaterloggedBlock, EntityBlock {

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final IntegerProperty SKILL_LEVEL = IntegerProperty.create("soullichen_level", 0, 30);
    public static final DirectionProperty FACE = DirectionProperty.create("soullichen_face");
    public static final DirectionProperty DIRECTION = DirectionProperty.create("soullichen_direction");
    //public static final IntegerProperty CURRENT_AGE = IntegerProperty.create("soullichen_age", 0, 30);
    private final MultifaceSpreader spreader = new MultifaceSpreader(this);
    private final MultifaceSpreader.DefaultSpreaderConfig config = new MultifaceSpreader.DefaultSpreaderConfig(this);
    private LivingEntity owner;
    //private static final Integer MAX_AGE = 25;

    public SoulLichenBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this
                .defaultBlockState()
                .setValue(WATERLOGGED, Boolean.FALSE)
                .setValue(SKILL_LEVEL, 0)
                .setValue(FACE, Direction.DOWN)
                .setValue(DIRECTION, Direction.DOWN)
                //.trySetValue(CURRENT_AGE, 0)
        );
    }

    public static ToIntFunction<BlockState> emission(int p_181223_) {
        return (p_181221_) -> MultifaceBlock.hasAnyFace(p_181221_) ? p_181223_ : 0;
    }

    public static boolean hasFace(BlockState p_153901_, @NotNull Direction p_153902_) {
        BooleanProperty booleanproperty = getFaceProperty(p_153902_);
        return p_153901_.hasProperty(booleanproperty) && p_153901_.getValue(booleanproperty);
    }

    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> stateDefinition) {
        stateDefinition.add(WATERLOGGED).add(SKILL_LEVEL).add(FACE).add(DIRECTION)
                //.add(CURRENT_AGE)
        ;
        super.createBlockStateDefinition(stateDefinition);
    }

    public @NotNull BlockState updateShape(BlockState p_153302_, @NotNull Direction p_153303_, @NotNull BlockState p_153304_, @NotNull LevelAccessor p_153305_, @NotNull BlockPos p_153306_, @NotNull BlockPos p_153307_) {
        if (p_153302_.getValue(WATERLOGGED)) {
            p_153305_.scheduleTick(p_153306_, Fluids.WATER, Fluids.WATER.getTickDelay(p_153305_));
        }
        return super.updateShape(p_153302_, p_153303_, p_153304_, p_153305_, p_153306_, p_153307_);
    }

    @SuppressWarnings("deprecation")
    public @NotNull FluidState getFluidState(BlockState fluidState) {
        return fluidState.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(fluidState);
    }

    public boolean propagatesSkylightDown(BlockState p_181225_, @NotNull BlockGetter blockGetter, @NotNull BlockPos blockPos) {
        return p_181225_.getFluidState().isEmpty();
    }

    public @NotNull MultifaceSpreader getSpreader() {
        return this.spreader;
    }

    public Optional<MultifaceSpreader.SpreadPos> spreadFromRandomFaceTowardRandomDirection(
            BlockState p_221620_, LevelAccessor p_221621_, BlockPos p_221622_, RandomSource p_221623_, int skillPoints, int age) {
        return Direction.allShuffled(p_221623_).stream().filter((p_221680_) -> {
            return this.config.canSpreadFrom(p_221620_, p_221680_);
        }).map((p_221629_) -> {
            return this.spreadFromFaceTowardRandomDirection(p_221620_, p_221621_, p_221622_, p_221629_, p_221623_, false, skillPoints, age);
        }).filter(Optional::isPresent).findFirst().orElse(Optional.empty());
    }

    public Optional<MultifaceSpreader.SpreadPos> spreadFromFaceTowardRandomDirection(
            BlockState blockState,
            LevelAccessor levelAccessor,
            BlockPos blockPos,
            Direction face,
            RandomSource randomSource,
            boolean aBoolean,
            int skillPoints, int age) {
        return Direction.allShuffled(randomSource).stream().map((direction) ->
                        spreadFromFaceTowardDirection(blockState, levelAccessor, blockPos, face, direction, aBoolean, skillPoints, age))
                .filter(Optional::isPresent)
                .findFirst()
                .orElse(Optional.empty());
    }

    public Optional<MultifaceSpreader.SpreadPos> spreadFromFaceTowardDirection(
            BlockState blockState,
            LevelAccessor levelAccessor,
            BlockPos blockPos,
            Direction face,
            Direction direction,
            boolean aBoolean, int skillPoints, int age) {
        //DevilRpg.LOGGER.debug("BEGIN ==================================== spreadFromFaceTowardDirection skillPoints {}", skillPoints);
        return skillPoints < 0 ? Optional.empty() : getSpreadFromFaceTowardDirection(blockState, levelAccessor, blockPos, face, direction, this::canSpreadInto)
                .flatMap((spreadPos)
                        -> {
                    //DevilRpg.LOGGER.debug("END ================================ spreadFromFaceTowardDirection spreadPos {}", spreadPos);
                    return this.spreadToFace(levelAccessor, spreadPos, aBoolean, skillPoints, direction, age);
                });
    }

    public boolean canSpreadInto(BlockGetter p_221685_, BlockPos p_221686_, MultifaceSpreader.SpreadPos p_221687_) {
        BlockState blockstate = p_221685_.getBlockState(p_221687_.pos());
        return this.stateCanBeReplaced(p_221685_, p_221686_, p_221687_.pos(), p_221687_.face(), blockstate) && isValidStateForPlacement(p_221685_, blockstate, p_221687_.pos(), p_221687_.face());
    }

    protected boolean stateCanBeReplaced(BlockGetter p_221688_, BlockPos p_221689_, BlockPos p_221690_, Direction p_221691_, BlockState p_221692_) {
        return p_221692_.isAir() || p_221692_.is(this) || p_221692_.is(Blocks.WATER) && p_221692_.getFluidState().isSource();
    }

    public Optional<MultifaceSpreader.SpreadPos> getSpreadFromFaceTowardDirection(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, Direction face, Direction direction, MultifaceSpreader.SpreadPredicate spreadPredicate) {
        //DevilRpg.LOGGER.debug("--- getSpreadFromFaceTowardDirection direction.getAxis() == face.getAxis(): {}", direction.getAxis() == face.getAxis());
        ArrayList<Direction> directions = new ArrayList<>();
        directions.add(direction);
        if (direction.getAxis() == face.getAxis()) {
            if (direction.getAxis().isHorizontal()) {
                directions = Arrays.stream(Direction.values()).filter(dir -> dir.getAxis().isVertical()).collect(Collectors.toCollection(ArrayList::new));
            }
            if (direction.getAxis().isVertical()) {
                directions = Arrays.stream(Direction.values()).filter(dir -> dir.getAxis().isHorizontal()).collect(Collectors.toCollection(ArrayList::new));
            }
        }
        for (Direction directionElement : directions) {
            /*DevilRpg.LOGGER.debug("--->> getSpreadFromFaceTowardDirection  config.isOtherBlockValidAsSource(blockState) {} || " +
                    "hasFace(blockState, face) {}  && " +
                    "!hasFace(blockState, direction) {}", config.isOtherBlockValidAsSource(blockState), hasFace(blockState, face), !hasFace(blockState, directionElement));*/
            if (config.isOtherBlockValidAsSource(blockState) ||
                    hasFace(blockState, face) &&
                            !hasFace(blockState, directionElement)) {

                for (MultifaceSpreader.SpreadType multifacespreader$spreadtype : config.getSpreadTypes()) {
                    MultifaceSpreader.SpreadPos multifacespreader$spreadpos = multifacespreader$spreadtype.getSpreadPos(blockPos, directionElement, face);
                    //DevilRpg.LOGGER.debug("--- test SpreadPos: {} direction {} face {} ", multifacespreader$spreadpos, directionElement, face);
                    if (spreadPredicate.test(blockGetter, blockPos, multifacespreader$spreadpos)) {
                        //DevilRpg.LOGGER.debug("--- spreadPredicate success:");
                        return Optional.of(multifacespreader$spreadpos);
                    }
                }
            }
        }
        return Optional.empty();
    }

    public boolean isValidStateForPlacement(@NotNull BlockGetter blockGetter, @NotNull BlockState blockState, @NotNull BlockPos blockPos, @NotNull Direction face) {
        //DevilRpg.LOGGER.debug("------ isValidStateForPlacement 1st condition: {} && ({} || {})", this.isFaceSupported(face), !blockState.is(this), !hasFace(blockState, face));
        if (this.isFaceSupported(face) && (!blockState.is(this) || !hasFace(blockState, face))) {
            BlockPos blockpos = blockPos.relative(face);
            //DevilRpg.LOGGER.debug("------ isValidStateForPlacement 2nd condition: canAttachTo {} ", secondCondition);
            return canAttachTo(blockGetter, face, blockpos, blockGetter.getBlockState(blockpos));
        } else {
            return false;
        }
    }

    @Nullable
    public BlockState getStateForPlacement(@NotNull BlockState blockState, @NotNull BlockGetter blockGetter,
                                           @NotNull BlockPos blockPos, @NotNull Direction face, int skillPoints, Direction direction, int age) {
        //DevilRpg.LOGGER.debug("--- getStateForPlacement");
        boolean isNotValidStateForPlacement = !this.isValidStateForPlacement(blockGetter, blockState, blockPos, face);
        //DevilRpg.LOGGER.debug("------- isNotValidStateForPlacement: {}", isNotValidStateForPlacement);
        if (isNotValidStateForPlacement) {
            return null;
        } else {
            BlockState blockstate;
            if (blockState.is(this)) {
                blockstate = blockState;
            } else if (this.isWaterloggable() && blockState.getFluidState().isSourceOfType(Fluids.WATER)) {
                blockstate = this.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, Boolean.TRUE);
            } else {
                blockstate = this.defaultBlockState();
            }


            //DevilRpg.LOGGER.debug("------- getStateForPlacement -> blockStateResult  ");
            return blockstate
                    .setValue(getFaceProperty(face), Boolean.TRUE)
                    .setValue(SKILL_LEVEL, skillPoints)
                    .setValue(FACE, face)
                    .setValue(DIRECTION, direction)
                    //.setValue(CURRENT_AGE,age)
            ;
        }
    }

    public Optional<MultifaceSpreader.SpreadPos> spreadToFace(LevelAccessor levelAccessor, MultifaceSpreader.SpreadPos spreadPos,
                                                              boolean p_221596_, int skillPoints, Direction direction, int age) {
        BlockState blockstate = levelAccessor.getBlockState(spreadPos.pos());
        //DevilRpg.LOGGER.debug("---> spreadToFace blockstate{} direction: {}", blockstate, direction);
        return this.placeBlock(levelAccessor, spreadPos, blockstate, p_221596_, skillPoints, direction, age) ? Optional.of(spreadPos) : Optional.empty();
    }

    public boolean placeBlock(LevelAccessor p_221702_, MultifaceSpreader.SpreadPos p_221703_,
                              BlockState p_221704_, boolean p_221705_, int skillPoints, Direction direction, int age) {
        //DevilRpg.LOGGER.debug("---> placeBlock {} direction {} ", p_221703_, direction);
        BlockState blockstate = this.getStateForPlacement(p_221704_, p_221702_, p_221703_.pos(), p_221703_.face(), skillPoints, direction, age);

        if (blockstate != null) {
            if (p_221705_) {
                p_221702_.getChunk(p_221703_.pos()).markPosForPostprocessing(p_221703_.pos());
            }
            //DevilRpg.LOGGER.debug("------> setBlock");
            return p_221702_.setBlock(p_221703_.pos(), blockstate, 2);
        } else {
            return false;
        }
    }

    public long spreadFromFaceTowardAllDirections(
            BlockState blockState, LevelAccessor levelAccessor, BlockPos blockPos,
            Direction face, boolean aBoolean, int skillPoints, int age) {
        return Direction.stream().map((p_221656_) ->
                        spreadFromFaceTowardDirection(blockState, levelAccessor, blockPos, face, p_221656_, aBoolean, skillPoints, age))
                .filter(Optional::isPresent).count();
    }

    private boolean isWaterloggable() {
        return this.stateDefinition.getProperties().contains(BlockStateProperties.WATERLOGGED);
    }

    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos blockPos, @NotNull BlockState blockState, @Nullable LivingEntity livingEntity, @NotNull ItemStack itemStack) {
        super.setPlacedBy(level, blockPos, blockState, livingEntity, itemStack);
        this.setOwner(livingEntity);
    }

    public LivingEntity getOwner() {
        return this.owner;
    }

    private void setOwner(LivingEntity livingEntity) {
        this.owner = livingEntity;
    }

    @Deprecated
    @Override
    public void entityInside(@NotNull BlockState blockState, @NotNull Level level, @NotNull BlockPos blockPos, @NotNull Entity entity) {
        if (entity instanceof LivingEntity && entity.getType() != ModEntities.LICHEN_SEEDBALL.get()) {
            entity.makeStuckInBlock(blockState, new Vec3(0.8D, 0.75D, 0.8D));
            if (!level.isClientSide) {
                applySoulLichenEffects(level, entity, (Player) owner);
            }
        }
    }

    public static void applySoulLichenEffects(@NotNull Level level, @NotNull Entity entity, Player owner) {
        entity.hurt(level.damageSources().playerAttack(owner), 1.0F);
        // Aplicar aceleración al movimiento
        double speedBoost = -0.4; // Ajusta este valor según lo rápido que quieras que sea el impulso
        double motionX = entity.getX() - entity.xOld;
        double motionZ = entity.getZ() - entity.zOld;
        double speed = Math.sqrt(motionX * motionX + motionZ * motionZ);

        entity.setDeltaMovement(entity.getDeltaMovement().multiply(
                (motionX / speed) * speedBoost,
                0.0,
                (motionZ / speed) * speedBoost
        ));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return ModEntityBlocks.SOUL_LICHEN_ENTITY_BLOCK.get().create(pos, state);
    }


    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, @NotNull BlockState blockState, @NotNull BlockEntityType<T> type) {
        return level.isClientSide ? null : (alevel, pos, aBlockstate, blockEntity) -> {
            if (blockEntity instanceof SoulLichenBlockEntity soulLichenBlockEntity && alevel.getGameTime() % 5 == 0) {
                soulLichenBlockEntity.tick(blockState, (ServerLevel) alevel, pos, alevel.getRandom());
                //DevilRpg.LOGGER.info("-------->tick. this: {}", this.getClass().getSimpleName());
            }
        };
    }
}
