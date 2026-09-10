package com.chipoodle.devilrpg.block;

import com.chipoodle.devilrpg.survival.LairManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * Núcleo de una <b>guarida</b>: el bloque central que mantiene activa la guarida (spawneando enemigos a
 * su alrededor). Cuando el jugador lo destruye, la guarida queda "limpiada" (deja de spawnear y da
 * recompensa). Es el objetivo asaltable de la guarida.
 */
public class LairCoreBlock extends Block {

    public LairCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                         @NotNull BlockState newState, boolean movedByPiston) {
        // Solo cuando el bloque cambia de verdad (no por otras actualizaciones de estado).
        if (!state.is(newState.getBlock()) && !level.isClientSide && level instanceof ServerLevel serverLevel) {
            LairManager.onCoreBroken(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
