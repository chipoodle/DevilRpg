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

    void cancel(long retryAt) {
        if (active) {
            reset(retryAt);
        }
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
            DevilRpg.LOGGER.info("[GuardTerrain] guard={} plan accepted origin={} target={} blocks={} landing={}",
                    villager.getUUID(), villager.blockPosition().toShortString(), strategicTarget.toShortString(),
                    pendingBlocks, landing.toShortString());
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
        int alignedCandidates = 0;
        int invalidLandings = 0;
        int unsupportedColumns = 0;
        int invalidPlacements = 0;
        int overBudgetPlans = 0;

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            double alignment = direction.getStepX() * targetDx + direction.getStepZ() * targetDz;
            if (alignment <= 0.0D) {
                continue;
            }
            for (int rise = 1; rise <= MAX_RISE; rise++) {
                alignedCandidates++;
                BlockPos candidateLanding = origin.relative(direction, rise + 1).above(rise);
                if (!esRellanoValido(level, candidateLanding)
                        || VillageManager.estaProtegida(level, candidateLanding)) {
                    invalidLandings++;
                    continue;
                }
                List<BlockPos> candidateBlocks = new ArrayList<>();
                boolean valid = true;
                for (int step = 1; step <= rise && valid; step++) {
                    BlockPos column = origin.relative(direction, step);
                    int supportY = buscarSuelo(level, column);
                    int topBlockY = origin.getY() + step - 1;
                    if (supportY == Integer.MIN_VALUE || supportY >= topBlockY) {
                        unsupportedColumns++;
                        valid = false;
                        break;
                    }
                    for (int y = supportY + 1; y <= topBlockY; y++) {
                        BlockPos place = new BlockPos(column.getX(), y, column.getZ());
                        BlockPos head = place.above();
                        BlockPos upperBody = place.above(2);
                        String placementFailure = motivoCeldaNoColocable(level, place);
                        boolean headBlocked = !level.getBlockState(head).getCollisionShape(level, head).isEmpty();
                        boolean upperBodyBlocked = !level.getBlockState(upperBody).getCollisionShape(level, upperBody).isEmpty();
                        if (placementFailure != null || headBlocked || upperBodyBlocked) {
                            invalidPlacements++;
                            DevilRpg.LOGGER.warn("[GuardTerrain] placement rejected guard={} direction={} rise={} step={} "
                                            + "place={} reason={} protected={} placeState={} placeFluid={} "
                                            + "head={} headState={} headFluid={} headCollision={} "
                                            + "upperBody={} upperBodyState={} upperBodyFluid={} upperBodyCollision={}",
                                    villager.getUUID(), direction, rise, step, place.toShortString(),
                                    placementFailure == null ? "BODY_CLEARANCE" : placementFailure,
                                    VillageManager.estaProtegida(level, place), level.getBlockState(place),
                                    level.getFluidState(place), head.toShortString(), level.getBlockState(head),
                                    level.getFluidState(head), headBlocked, upperBody.toShortString(),
                                    level.getBlockState(upperBody), level.getFluidState(upperBody), upperBodyBlocked);
                            valid = false;
                            break;
                        }
                        candidateBlocks.add(place);
                    }
                }
                if (!valid || candidateBlocks.isEmpty() || candidateBlocks.size() > MAX_BLOCKS) {
                    if (candidateBlocks.size() > MAX_BLOCKS) {
                        overBudgetPlans++;
                    }
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
            BlockPos feet = villager.blockPosition();
            BlockPos support = feet.below();
            DevilRpg.LOGGER.warn("[GuardTerrain] guard={} plan rejected origin={} target={} aligned={} "
                            + "invalidLandings={} unsupportedColumns={} invalidPlacements={} overBudget={} "
                            + "feetFluid={} headFluid={} support={} supportFluid={} chunkLoaded={}",
                    villager.getUUID(), feet.toShortString(), strategicTarget.toShortString(), alignedCandidates,
                    invalidLandings, unsupportedColumns, invalidPlacements, overBudgetPlans,
                    !level.getFluidState(feet).isEmpty(), !level.getFluidState(feet.above()).isEmpty(),
                    level.getBlockState(support), !level.getFluidState(support).isEmpty(), level.hasChunkAt(feet));
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
        return motivoCeldaNoColocable(level, pos) == null;
    }

    /**
     * Devuelve la primera razón por la que una celda no admite el bloque de la rampa.
     * Mantiene exactamente las mismas reglas de aceptación que celdaColocable; se separa
     * para que los rechazos del planificador sean reproducibles por coordenada y estado.
     */
    private static String motivoCeldaNoColocable(ServerLevel level, BlockPos pos) {
        if (pos == null) {
            return "NULL_POSITION";
        }
        if (!level.hasChunkAt(pos)) {
            return "CHUNK_NOT_LOADED";
        }
        if (VillageManager.estaProtegida(level, pos)) {
            return "PROTECTED_POSITION";
        }
        BlockState state = level.getBlockState(pos);
        if (state.hasBlockEntity()) {
            return "BLOCK_ENTITY";
        }
        if (!state.getCollisionShape(level, pos).isEmpty()) {
            return "PLACEMENT_COLLISION";
        }
        if (!state.getFluidState().isEmpty() && !state.getFluidState().is(FluidTags.WATER)) {
            return "UNSUPPORTED_FLUID";
        }
        return null;
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
