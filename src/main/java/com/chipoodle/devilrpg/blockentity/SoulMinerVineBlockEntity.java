package com.chipoodle.devilrpg.blockentity;

import net.minecraft.tags.BlockTags;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.block.SoulMinerVineBlock;
import com.chipoodle.devilrpg.init.ModEntityBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.chipoodle.devilrpg.block.SoulMinerVineBlock.*;


public class SoulMinerVineBlockEntity extends BlockEntity {

    public static final int TICK_FACTOR = 140;
    /** Multiplica la longitud de la rama para que la planta cae un 30% mas profundo. */
    private static final double BRANCH_DEPTH_MULTIPLIER = 1.3D;
    private Long timeOfCreation = null;

    /**
     * Posicion de la raiz de la planta (el primer bloque creado al lanzar el poder). Todos los
     * bloques de la enredadera la conocen para transportar los items minados hasta ahi.
     */
    private BlockPos rootPos = null;
    /**
     * Posicion del bloque padre (el que esta del lado de la raiz). {@code null} en la raiz.
     */
    private BlockPos parentPos = null;
    /**
     * Items/minerales recien minados que esperan ser transportados hacia la raiz (pipe).
     */
    private final List<ItemStack> transportBuffer = new ArrayList<>();

    public SoulMinerVineBlockEntity(BlockPos pos, BlockState state) {
        super(ModEntityBlocks.SOUL_MINER_VINE_ENTITY_BLOCK.get(), pos, state);
    }

    /** Configura la raiz y el padre. La raiz se llama con {@code (pos, null)}. */
    public void setRootInfo(BlockPos rootPos, BlockPos parentPos) {
        this.rootPos = rootPos;
        this.parentPos = parentPos;
        this.setChanged();
    }

    /** Añade items al buffer de transporte (vienen de un bloque minado o de un hijo). */
    public void addToBuffer(List<ItemStack> items) {
        this.transportBuffer.addAll(items);
        this.setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (rootPos != null) tag.putLong("rootPos", rootPos.asLong());
        if (parentPos != null) tag.putLong("parentPos", parentPos.asLong());
        if (!transportBuffer.isEmpty()) {
            var list = new net.minecraft.nbt.ListTag();
            for (ItemStack stack : transportBuffer) {
                list.add(stack.saveOptional(registries));
            }
            tag.put("transportBuffer", list);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("rootPos")) rootPos = BlockPos.of(tag.getLong("rootPos"));
        if (tag.contains("parentPos")) parentPos = BlockPos.of(tag.getLong("parentPos"));
        if (tag.contains("transportBuffer")) {
            var list = tag.getList("transportBuffer", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                ItemStack stack = ItemStack.parseOptional(registries, list.getCompound(i));
                if (!stack.isEmpty()) transportBuffer.add(stack.copy());
            }
        }
    }

    /** Cuando el bloque se destruye (decadencia, rompido por el jugador, etc.) y aun transportaba
     *  items, estos se sueltan en la posicion de ese bloque. */
    @Override
    public void setRemoved() {
        if (!this.isRemoved() && !transportBuffer.isEmpty() && this.level instanceof ServerLevel serverLevel) {
            dropBufferHere(serverLevel);
        }
        super.setRemoved();
    }

    public boolean tick(@NotNull BlockState state, @NotNull ServerLevel world, @NotNull BlockPos currentBlockPos, @NotNull RandomSource randomSource) {
        if (timeOfCreation == null) {
            timeOfCreation = world.getGameTime();
        }

        // Transportar los items minados hacia la raiz (pipe) ANTES de crecer.
        transportItems(world);

        Integer currentAge = state.getValue(AGE); // Obtener el AGE actual
        int skillLevel = state.getValue(LEVEL); // Obtener el nivel de habilidad
        int maxBranchLength = Math.min(SoulMinerVineBlock.MAX_VINE_AGE,
                (int) Math.round((skillLevel + 5) * BRANCH_DEPTH_MULTIPLIER)); // +30% de profundidad, sin superar AGE max
        int currentDecay = state.getValue(DECAY_STAGE);
        Direction currentDirection = state.getValue(DIRECTIONS);
        Integer duration = TICK_FACTOR;
        boolean hasChildren = state.getValue(HAS_CHILDREN);

        long timeElapsed = world.getGameTime() - timeOfCreation;
        int newDecayStage = (int) ((timeElapsed * 4) / duration); // 4 etapas de decadencia

        // Actualiza el estado de decadencia
        if (newDecayStage != currentDecay && newDecayStage <= 3) {
            state = state.setValue(DECAY_STAGE, newDecayStage);
            world.setBlockAndUpdate(currentBlockPos, state);
        }

        // Si la planta ha alcanzado su duración máxima, destruye el bloque
        if (!canStay(state, world, currentBlockPos, currentDirection) || timeOfCreation + duration < world.getGameTime()) {
            world.destroyBlock(currentBlockPos, true);
            return hasChildren;
        }

        // Verificar si la rama ha alcanzado su longitud máxima
        if (currentAge < maxBranchLength) { // Comparar AGE con la longitud máxima
            if (!hasChildren) {
                BlockPos childBlockPos;
                BlockState childBlockState;

                ArrayList<Direction> directions = new ArrayList<>(growthDirections(randomSource));

                for (Direction nextDirection : directions) {
                    childBlockPos = currentBlockPos.relative(nextDirection);
                    childBlockState = world.getBlockState(childBlockPos);

                    if (isMineable(childBlockState)) {
                        state = setBlockDirection(state, world, currentBlockPos, nextDirection);
                        mineBlockAndExpand(state, world, currentDirection, childBlockPos, currentBlockPos, nextDirection, randomSource, currentAge, maxBranchLength);
                        return true;
                    }
                }
            }
        }
        return hasChildren;
    }

    @NotNull
    private BlockState setBlockDirection(@NotNull BlockState blockState, @NotNull ServerLevel serverLevel, @NotNull BlockPos currentBlockPos, Direction currentDirection) {
        this.setChanged();
        blockState = blockState.setValue(SOULVINE_FACING, currentDirection);
        serverLevel.setBlockAndUpdate(currentBlockPos, blockState);
        return blockState;
    }

    private void mineBlockAndExpand(@NotNull BlockState blockState, @NotNull ServerLevel serverLevel, Direction currentDirection, BlockPos childBlockPos, BlockPos currentBlockPos, Direction childDirection, @NotNull RandomSource randomSource, int currentAge, int maxBranchLength) {
        this.setChanged();

        // Capturar los drops (sin soltarlos en el suelo) y absorberlos al buffer de la planta.
        List<ItemStack> drops = captureDrops(serverLevel, childBlockPos, serverLevel.getBlockState(childBlockPos));
        // Elimina el bloque que está siendo minado SIN soltar los items (ya los capturamos).
        serverLevel.destroyBlock(childBlockPos, false);
        this.transportBuffer.addAll(drops);

        // Establece el bloque hijo en la primera dirección
        BlockState childBlockState = SoulMinerVineBlock
                .getGrowIntoState(blockState)
                .setValue(AGE, blockState.getValue(AGE) + 1) // Incrementar AGE para el bloque hijo
                .setValue(DIRECTIONS, currentDirection);

        serverLevel.setBlockAndUpdate(childBlockPos, childBlockState);
        serverLevel.setBlockAndUpdate(currentBlockPos, blockState.setValue(HAS_CHILDREN, true));
        // El hijo hereda la raiz y su padre es este bloque.
        linkChild(serverLevel, childBlockPos, this.worldPosition);


        List<Direction> possibleDirections = new ArrayList<>(growthDirections(randomSource));
        possibleDirections.remove(currentDirection.getOpposite()); // Evitar volver hacia atrás


        if (currentAge  == maxBranchLength - 1) {
            if (possibleDirections.isEmpty()) {
                return;
            }

            //Puede haber menos de 3 posibles direcciones
            int branchesToCreate = Math.min(3, possibleDirections.size());
            for (int i = 0; i < branchesToCreate; i++) {
                // Obtener una dirección aleatoria válida
                Direction nextDirection = possibleDirections.remove(0);
                BlockPos nextBlockPos = currentBlockPos.relative(nextDirection);
                BlockState nextBlockState = serverLevel.getBlockState(nextBlockPos);

                if (isMineable(nextBlockState)) {
                    // Capturar drops y minar el bloque y expandirse
                    List<ItemStack> branchDrops = captureDrops(serverLevel, nextBlockPos, nextBlockState);
                    serverLevel.destroyBlock(nextBlockPos, false);
                    this.transportBuffer.addAll(branchDrops);
                    BlockState newBlockState = SoulMinerVineBlock
                            .getGrowIntoState(blockState)
                            .setValue(AGE, blockState.getValue(AGE) + 1) // Incrementar AGE para la nueva rama
                            .setValue(DIRECTIONS, nextDirection);
                    DevilRpg.LOGGER.info("==============>");
                    serverLevel.setBlockAndUpdate(nextBlockPos, newBlockState);
                    linkChild(serverLevel, nextBlockPos, this.worldPosition);
                }
            }
        }
    }

    /** Configura el BlockEntity de un bloque hijo: hereda la raiz y su padre es el bloque actual. */
    private void linkChild(ServerLevel serverLevel, BlockPos childPos, BlockPos currentPos) {
        if (serverLevel.getBlockEntity(childPos) instanceof SoulMinerVineBlockEntity childBE) {
            childBE.setRootInfo(rootPos != null ? rootPos : currentPos, currentPos);
        }
    }

    /** Obtiene los drops de un bloque (sin soltarlos) usando la loot table estandar sin herramienta. */
    private List<ItemStack> captureDrops(ServerLevel level, BlockPos pos, BlockState state) {
        return Block.getDrops(state, level, pos, null, null, ItemStack.EMPTY);
    }

    /** Transporta los items del buffer hacia la raiz: si es la raiz los escupe, si no, los envia al padre. */
    private void transportItems(ServerLevel level) {
        if (transportBuffer.isEmpty()) {
            return;
        }
        // Rastro de particulas indicando el flujo del pipe hacia la raiz.
        spawnFlowParticles(level);
        if (parentPos == null) {
            // Es la raiz: escupir todos los items en el suelo.
            spitOutItems(level);
        } else {
            // Enviar al bloque padre (hacia la raiz).
            BlockEntity parentBE = level.getBlockEntity(parentPos);
            if (parentBE instanceof SoulMinerVineBlockEntity parentVine) {
                parentVine.addToBuffer(transportBuffer);
                transportBuffer.clear();
            } else {
                // El padre ya no existe (bloque destruido): soltar los items aqui.
                dropBufferHere(level);
            }
        }
    }

    /** Escupe todos los items del buffer en la posicion de la raiz. */
    private void spitOutItems(ServerLevel level) {
        double x = this.worldPosition.getX() + 0.5;
        double y = this.worldPosition.getY() + 0.5;
        double z = this.worldPosition.getZ() + 0.5;
        for (ItemStack stack : transportBuffer) {
            ItemEntity item = new ItemEntity(level, x, y, z, stack.copy());
            // Velocidad aleatoria hacia arriba para que "salten" al salir de la raiz.
            item.setDeltaMovement(level.random.nextGaussian() * 0.15, 0.35 + level.random.nextDouble() * 0.15, level.random.nextGaussian() * 0.15);
            item.setPickUpDelay(10);
            level.addFreshEntity(item);
        }
        transportBuffer.clear();
    }

    /** Suelta los items del buffer en la posicion de este bloque (bloque destruido/roto). */
    private void dropBufferHere(ServerLevel level) {
        double x = this.worldPosition.getX() + 0.5;
        double y = this.worldPosition.getY() + 0.5;
        double z = this.worldPosition.getZ() + 0.5;
        for (ItemStack stack : transportBuffer) {
            level.addFreshEntity(new ItemEntity(level, x, y, z, stack.copy()));
        }
        transportBuffer.clear();
    }

    /** Emite particulas del flujo del pipe hacia la raiz. */
    private void spawnFlowParticles(ServerLevel level) {
        double px = this.worldPosition.getX() + 0.5;
        double py = this.worldPosition.getY() + 0.5;
        double pz = this.worldPosition.getZ() + 0.5;
        double vx = 0.0, vy = 0.0, vz = 0.0;
        if (parentPos != null) {
            vx = parentPos.getX() - this.worldPosition.getX();
            vy = parentPos.getY() - this.worldPosition.getY();
            vz = parentPos.getZ() - this.worldPosition.getZ();
            double len = Math.sqrt(vx * vx + vy * vy + vz * vz);
            if (len > 0) {
                vx /= len;
                vy /= len;
                vz /= len;
            }
        } else {
            vy = 0.3; // la raiz expulsa hacia arriba
        }
        level.sendParticles(ParticleTypes.SOUL, px, py, pz, 1, vx * 0.06, vy * 0.06, vz * 0.06, 0.05);
    }

    /**
     * Direcciones de crecimiento sesgadas hacia ABAJO: DOWN aparece 3 veces al inicio, de modo que la
     * enredadera tiende a cavar mas hacia abajo que hacia los lados.
     */
    private List<Direction> growthDirections(RandomSource randomSource) {
        List<Direction> dirs = new ArrayList<>(Direction.allShuffled(randomSource));
        List<Direction> biased = new ArrayList<>(List.of(Direction.DOWN, Direction.DOWN, Direction.DOWN));
        for (Direction d : dirs) {
            if (d != Direction.DOWN) {
                biased.add(d);
            }
        }
        return biased;
    }

    private boolean isMineable(BlockState blockState) {
        // Verifica si el bloque es roca, tierra o minerales
        return blockState.is(BlockTags.MINEABLE_WITH_PICKAXE)
                || blockState.is(BlockTags.MINEABLE_WITH_SHOVEL)
                || blockState.is(BlockTags.MINEABLE_WITH_AXE)
                || blockState.is(BlockTags.LEAVES)
                || blockState.is(Blocks.NETHERRACK)
                || blockState.is(Blocks.BASALT)
                || blockState.is(Blocks.SNOW)
                ;
    }
}
