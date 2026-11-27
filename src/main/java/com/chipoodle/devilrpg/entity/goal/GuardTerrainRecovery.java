package com.chipoodle.devilrpg.entity.goal;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.world.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Recuperación de terreno de la milicia. No posee navegación: todos los desplazamientos se emiten
 * mediante VillageManager.caminarHacia y, por tanto, el cerebro del aldeano conserva la autoridad.
 *
 * Construye una rampa corta de columnas escalonadas únicamente hacia un rellano natural, con soporte
 * real, espacio para el cuerpo y fuera de las zonas protegidas. La colocación es incremental y acotada.
 */
final class GuardTerrainRecovery {
    static final int STUCK_TRIGGER_TICKS = 60;
    private static final int ACTION_COOLDOWN_TICKS = 10;
    private static final int MAX_RISE = 2;
    private static final int MAX_SUPPORT_DEPTH = 4;
    private static final int MAX_BLOCKS = 8;
    private static final int MAX_ACTIVE_TICKS = 400;
    private static final float MOVE_SPEED = 0.6F;

    private final List<BlockPos> pendingBlocks = new ArrayList<>();
    private BlockPos landing;
    private int nextBlock;
    private long nextActionAt;
    private long startedAt;
    private boolean active;

    boolean isActive() {
        return active;
    }

    /**
     * @return true mientras la recuperación controla el destino temporal; false cuando no aplica o terminó.
     */
    boolean tick(ServerLevel level, Villager villager, BlockPos strategicTarget, boolean mayStart) {
        long now = level.getGameTime();
        if (!active) {
            if (!mayStart || strategicTarget == null || villager.isSleeping() || now < nextActionAt) {
                return false;
            }
            if (!plan(level, villager, strategicTarget)) {
                nextActionAt = now + 100L;
                return false;
            }
            active = true;
            startedAt = now;
            nextActionAt = now;
            DevilRpg.LOGGER.info("[GuardTerrain] guard={} recovery started blocks={} landing={}",
                    villager.getUUID(), pendingBlocks.size(), landing.toShortString());
        }

        if (now - startedAt > MAX_ACTIVE_TICKS) {
            reset(now + 100L);
            return false;
        }

        if (nextBlock < pendingBlocks.size()) {
            if (now < nextActionAt) {
                return true;
            }
            BlockPos pos = pendingBlocks.get(nextBlock);
            if (!celdaColocable(level, pos)) {
                reset(now + 100L);
                return false;
            }
            BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
            if (!level.setBlock(pos, cobble, Block.UPDATE_ALL)
                    && !level.getBlockState(pos).is(Blocks.COBBLESTONE)) {
                reset(now + 100L);
                return false;
            }
            if (!level.getBlockState(pos).is(Blocks.COBBLESTONE)) {
                reset(now + 100L);
                return false;
            }
            level.playSound(null, pos, SoundType.STONE.getPlaceSound(), SoundSource.BLOCKS, 0.65F, 1.0F);
            nextBlock++;
            DevilRpg.LOGGER.debug("[GuardTerrain] guard={} placed {}/{} at {}",
                    villager.getUUID(), nextBlock, pendingBlocks.size(), pos.toShortString());
            nextActionAt = now + ACTION_COOLDOWN_TICKS;
            return true;
        }

        if (landing == null || !esRellanoValido(level, landing)) {
            reset(now + 100L);
            return false;
        }
        if (villager.blockPosition().closerThan(landing, 1.5D)) {
            DevilRpg.LOGGER.info("[GuardTerrain] guard={} recovered at {} after {} ticks",
                    villager.getUUID(), landing.toShortString(), now - startedAt);
            reset(now + 40L);
            return false;
        }
        VillageManager.caminarHacia(villager, landing, MOVE_SPEED);
        return true;
    }

    private boolean plan(ServerLevel level, Villager villager, BlockPos strategicTarget) {
        pendingBlocks.clear();
        landing = null;
        nextBlock = 0;

        BlockPos origin = villager.blockPosition();
        double targetDx = strategicTarget.getX() - villager.getX();
        double targetDz = strategicTarget.getZ() - villager.getZ();
        List<BlockPos> bestBlocks = null;
        BlockPos bestLanding = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            double alignment = direction.getStepX() * targetDx + direction.getStepZ() * targetDz;
            if (alignment <= 0.0D) {
                continue;
            }
            for (int rise = 1; rise <= MAX_RISE; rise++) {
                BlockPos candidateLanding = origin.relative(direction, rise + 1).above(rise);
                if (!esRellanoValido(level, candidateLanding)
                        || VillageManager.estaProtegida(level, candidateLanding)) {
                    continue;
                }
                List<BlockPos> candidateBlocks = new ArrayList<>();
                boolean valid = true;
                for (int step = 1; step <= rise && valid; step++) {
                    BlockPos column = origin.relative(direction, step);
                    int supportY = buscarSuelo(level, column);
                    int topBlockY = origin.getY() + step - 1;
                    if (supportY == Integer.MIN_VALUE || supportY >= topBlockY) {
                        valid = false;
                        break;
                    }
                    for (int y = supportY + 1; y <= topBlockY; y++) {
                        BlockPos place = new BlockPos(column.getX(), y, column.getZ());
                        BlockPos head = place.above();
                        BlockPos upperBody = place.above(2);
                        if (!celdaColocable(level, place) || VillageManager.estaProtegida(level, place)
                                || !level.getBlockState(head).getCollisionShape(level, head).isEmpty()
                                || !level.getBlockState(upperBody).getCollisionShape(level, upperBody).isEmpty()) {
                            valid = false;
                            break;
                        }
                        candidateBlocks.add(place);
                    }
                }
                if (!valid || candidateBlocks.isEmpty() || candidateBlocks.size() > MAX_BLOCKS) {
                    continue;
                }
                double score = alignment - candidateBlocks.size() * 0.25D - rise * 0.1D;
                if (score > bestScore) {
                    bestScore = score;
                    bestBlocks = candidateBlocks;
                    bestLanding = candidateLanding;
                }
            }
        }

        if (bestBlocks == null || bestLanding == null) {
            return false;
        }
        pendingBlocks.addAll(bestBlocks);
        landing = bestLanding;
        return true;
    }

    private static int buscarSuelo(ServerLevel level, BlockPos column) {
        for (int depth = 1; depth <= MAX_SUPPORT_DEPTH; depth++) {
            BlockPos below = column.below(depth);
            if (!level.hasChunkAt(below)) {
                return Integer.MIN_VALUE;
            }
            BlockState state = level.getBlockState(below);
            if (!state.getCollisionShape(level, below).isEmpty() && state.getFluidState().isEmpty()) {
                return below.getY();
            }
        }
        return Integer.MIN_VALUE;
    }

    private static boolean esRellanoValido(ServerLevel level, BlockPos feet) {
        if (feet == null || !level.hasChunkAt(feet) || VillageManager.estaProtegida(level, feet)) {
            return false;
        }
        BlockPos support = feet.below();
        BlockState floor = level.getBlockState(support);
        return !floor.getCollisionShape(level, support).isEmpty()
                && floor.getFluidState().isEmpty()
                && esEspacioLibre(level, feet)
                && esEspacioLibre(level, feet.above());
    }

    private static boolean celdaColocable(ServerLevel level, BlockPos pos) {
        if (pos == null || !level.hasChunkAt(pos) || VillageManager.estaProtegida(level, pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.hasBlockEntity() || !state.getCollisionShape(level, pos).isEmpty()) {
            return false;
        }
        return state.getFluidState().isEmpty() || state.getFluidState().is(FluidTags.WATER);
    }

    private static boolean esEspacioLibre(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }

    private void reset(long retryAt) {
        active = false;
        pendingBlocks.clear();
        landing = null;
        nextBlock = 0;
        nextActionAt = retryAt;
        startedAt = 0L;
    }
}
