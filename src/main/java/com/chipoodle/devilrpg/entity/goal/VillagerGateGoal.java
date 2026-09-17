package com.chipoodle.devilrpg.entity.goal;

import com.chipoodle.devilrpg.world.VillageGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Los <b>portones de valla</b> del anexo (el del corral y el del gallinero).
 * <p>
 * El juego <b>no deja</b> que un aldeano abra una puerta de valla: el cerebro vanilla solo sabe abrir
 * {@code DoorBlock}, así que con un portón el ganadero se quedaría <b>fuera del gallinero</b> (sin poder recoger los
 * huevos) o <b>encerrado</b> en el corral. Este goal es el que se lo abre al acercarse y se lo cierra al pasar.
 * <p>
 * Dos reglas para que sea educado:
 * <ul>
 *   <li><b>Solo abre</b> con un aldeano del pueblo pegado al portón: el ganado no se escapa por un portón abierto
 *       todo el día.</li>
 *   <li><b>Solo cierra</b> el portón que abrió el propio pueblo ({@link #ABIERTOS}): si lo abre el jugador, se queda
 *       como él lo deje.</li>
 * </ul>
 * No ocupa <b>ninguna bandera</b> ({@code Goal.Flag}): no mueve al aldeano, así que trabaja <b>a la vez</b> que su
 * faena (caminar, cuidar el rebaño, patrullar) sin quitársela.
 */
public class VillagerGateGoal extends Goal {

    /** Distancia a la que el aldeano ya está pegado al portón: se le abre. */
    private static final double ABRIR = 2.6D;
    /** Sin ningún aldeano a esta distancia, el portón se vuelve a cerrar. */
    private static final double CERRAR = 3.0D;
    /**
     * Hasta dónde vigila el aldeano su portón (el que más cerca tenga). Es más largo que {@link #CERRAR} a propósito:
     * el aldeano que cruza tiene que <b>seguir vigilando</b> mientras se aleja, o el portón se le quedaría abierto
     * detrás.
     */
    private static final double RADIO = 16.0D;

    /**
     * Portones que ha abierto <b>el pueblo</b> (por nivel y posición comprimida): solo ésos se vuelven a cerrar. El
     * mapa es débil para que cerrar un mundo no deje el nivel colgado.
     */
    private static final Map<ServerLevel, Set<Long>> ABIERTOS = new WeakHashMap<>();

    private final Villager villager;
    private final BlockPos center;
    @Nullable
    private BlockPos[] portones;
    @Nullable
    private BlockPos porton;

    public VillagerGateGoal(Villager villager, BlockPos center) {
        this.villager = villager;
        this.center = center;
        // SIN banderas: no mueve al aldeano, así que no compite con su faena (con MOVE se la interrumpiría).
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    public boolean canUse() {
        if (villager.isBaby() || !(villager.level() instanceof ServerLevel level)) {
            return false;
        }
        // A propósito NO se mira si descansa ni si hay refriega: el portón tiene que poder abrirse también de noche
        // (el ganadero se va a la cama dentro del corral) y en plena pelea (para huir o refugiarse).
        porton = portonMasCercano(level);
        return porton != null;
    }

    @Override
    public boolean canContinueToUse() {
        return porton != null && !villager.isBaby() && distancia(porton) <= RADIO;
    }

    @Override
    public void tick() {
        if (porton == null || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        BlockState estado = level.getBlockState(porton);
        if (!(estado.getBlock() instanceof FenceGateBlock)) {
            porton = null; // se llevaron el portón (o el jugador puso otra cosa): no hay nada que abrir
            return;
        }
        boolean abierto = estado.getValue(FenceGateBlock.OPEN);
        double distancia = distancia(porton);
        if (!abierto) {
            olvidar(level, porton); // si el jugador lo cerró a mano, ya no es "nuestro"
            if (distancia <= ABRIR) {
                abrir(level, porton, estado);
            }
        } else if (distancia > CERRAR && nadieCerca(level, porton)) {
            cerrar(level, porton, estado);
        }
    }

    @Override
    public void stop() {
        porton = null;
    }

    // --- los portones -------------------------------------------------------------------------------

    /** El portón que le toca a este aldeano (el más cercano), o {@code null} si no tiene ninguno a {@link #RADIO}. */
    @Nullable
    private BlockPos portonMasCercano(ServerLevel level) {
        if (portones == null) {
            // La cota se pregunta UNA vez por goal (mira el terreno de la plaza): los portones no se mueven.
            int nivel = VillageGenerator.cotaDeLaPlaza(level, center);
            portones = new BlockPos[]{VillageGenerator.portonDelCorral(center, nivel),
                    VillageGenerator.portonDelGallinero(center, nivel)};
        }
        BlockPos mejor = null;
        double mejorDistancia = RADIO;
        for (BlockPos p : portones) {
            double d = distancia(p);
            if (d <= mejorDistancia) {
                mejorDistancia = d;
                mejor = p;
            }
        }
        return mejor;
    }

    /** Abre el portón (con su chirrido) y lo apunta como "abierto por el pueblo". */
    private void abrir(ServerLevel level, BlockPos porton, BlockState estado) {
        level.setBlock(porton, estado.setValue(FenceGateBlock.OPEN, true), Block.UPDATE_ALL);
        level.playSound(null, porton, SoundEvents.FENCE_GATE_OPEN, SoundSource.BLOCKS, 0.7F, 1.0F);
        ABIERTOS.computeIfAbsent(level, l -> new HashSet<>()).add(porton.asLong());
    }

    /** Cierra el portón, pero <b>solo</b> si lo abrió el pueblo (si lo abrió el jugador, manda él). */
    private void cerrar(ServerLevel level, BlockPos porton, BlockState estado) {
        Set<Long> abiertos = ABIERTOS.get(level);
        if (abiertos == null || !abiertos.remove(porton.asLong())) {
            return; // no es nuestro: se queda como está
        }
        level.setBlock(porton, estado.setValue(FenceGateBlock.OPEN, false), Block.UPDATE_ALL);
        level.playSound(null, porton, SoundEvents.FENCE_GATE_CLOSE, SoundSource.BLOCKS, 0.7F, 1.0F);
    }

    /** Deja de contar como "abierto por el pueblo" (el jugador lo cerró a mano). */
    private void olvidar(ServerLevel level, BlockPos porton) {
        Set<Long> abiertos = ABIERTOS.get(level);
        if (abiertos != null) {
            abiertos.remove(porton.asLong());
        }
    }

    /** ¿Está el portón despejado? (si hay un aldeano pegado, NO se cierra: podría estar cruzando). */
    private boolean nadieCerca(ServerLevel level, BlockPos porton) {
        return level.getEntitiesOfClass(Villager.class, new AABB(porton).inflate(CERRAR)).isEmpty();
    }

    private double distancia(BlockPos porton) {
        return Math.sqrt(villager.distanceToSqr(porton.getX() + 0.5D, porton.getY() + 0.5D, porton.getZ() + 0.5D));
    }
}
