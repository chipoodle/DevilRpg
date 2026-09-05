package com.chipoodle.devilrpg.spawner;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

/**
 * Regla generica de spawn para el {@link CustomSpawner}.
 * <p>
 * Cada regla define QUÉ entidad spawnea, cada CUÁNTO (intervalo aleatorio), la PROBABILIDAD de
 * spawnear (dependiente del contexto, ej. distancia del jugador a su punto de inicio), DÓNDE
 * colocarla (posicion valida lejos del jugador) y cómo CONFIGURARLA tras crearse.
 * <p>
 * Permite reutilizar el mismo spawneador para otras entidades personalizadas del mod.
 */
public interface CustomSpawnRule {

    /** Entidad a spawneaer. */
    EntityType<? extends Mob> getEntityType();

    /** Intervalo minimo (ticks) entre intentos de spawn. 20 ticks = 1 segundo. */
    int getMinIntervalTicks();

    /** Intervalo maximo (ticks) entre intentos de spawn. */
    int getMaxIntervalTicks();

    /**
     * Probabilidad (0.0 a 1.0) de que spawnee en este intento. Se evalua por jugador.
     * Ej. para el zombie agresivo: a mayor distancia del jugador a su punto de inicio, mayor probabilidad.
     */
    float getSpawnChance(ServerLevel level, ServerPlayer player);

    /**
     * Devuelve una posicion valida (bloque solido + aire encima, lejos del jugador) donde spawneaer,
     * o {@code null} si no hay ninguna valida en este intento.
     */
    @Nullable BlockPos findSpawnPosition(ServerLevel level, ServerPlayer player);

    /** Configura la entidad tras crearla. Por defecto no hace nada (la entidad puede auto-configurarse). */
    default void configureEntity(Mob entity, ServerLevel level, ServerPlayer player) {
    }

    /**
     * Numero maximo de criaturas de ESTE tipo que pueden existir a la vez en el nivel (mundo).
     * Cuando se alcanza, el spawneador deja de crear mas hasta que alguna muera/desaparezca.
     * Por defecto sin limite.
     */
    default int getMaxAliveInLevel() {
        return Integer.MAX_VALUE;
    }

    /**
     * Numero minimo de criaturas a spawneaer cuando se cumple la regla (junto con
     * {@link #getMaxSpawnCount()} se elige una cantidad aleatoria entre ambos). Por defecto 1.
     */
    default int getMinSpawnCount() {
        return 1;
    }

    /** Numero maximo de criaturas a spawneaer (aleatorio entre {@link #getMinSpawnCount()} y este). Por defecto 1. */
    default int getMaxSpawnCount() {
        return 1;
    }
}
