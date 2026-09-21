package com.chipoodle.devilrpg.entity.goal;

import com.chipoodle.devilrpg.world.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * <b>EL ALDEANO CIERRA LA PUERTA QUE CRUZA.</b> Lo pidió el jugador: *"los aldeanos cuando vayan a dormir tienen que
 * cerrar la puerta porque todas la dejan abierta"*.
 * <p>
 * El juego tiene su propio comportamiento para esto ({@code InteractWithDoor}, con la memoria {@code DOORS_TO_CLOSE}),
 * pero <b>con los aldeanos del pueblo no cierra nada</b>: la puerta se abre al pasar y se queda abierta —y con ella el
 * pueblo entero: la taberna, las casas y la posada—, que es justo lo que el jugador ve. Así que el pueblo lo hace por
 * su cuenta, igual que hace con las <b>puertas de valla</b> ({@link VillagerGateGoal}, que el juego <b>no deja</b> abrir
 * a un aldeano).
 * <p>
 * Cómo funciona, y con cuidado de no meterse donde no le llaman:
 * <ul>
 *   <li>Solo mira las puertas <b>de madera</b> (las de hierro no las puede abrir un aldeano) y solo si están
 *       <b>abiertas</b> y <b>pegadas a él</b> (en su propia casilla o al lado: la que está cruzando).</li>
 *   <li>Se apunta que la ha <b>usado</b> cuando está <b>en el hueco</b> (a menos de 1,5). Una puerta que solo tiene al
 *       lado —o la que el jugador dejó abierta y el aldeano pasa por delante— <b>no se toca</b>: no es suya.</li>
 *   <li>Cuando ya ha pasado al otro lado, la cierra ({@code DoorBlock.setOpen(..., false)}: cierra las dos mitades y
 *       suena la puerta) tras unos ticks de cortesía.</li>
 *   <li>Y <b>no se le cierra a un jugador al lado</b> (a menos de 2,5): sería cerrarle la puerta en las narices.</li>
 * </ul>
 * Va <b>sin banderas</b> (como el goal de los portones): no mueve al aldeano, así que no compite con su faena.
 */
public class VillagerDoorGoal extends Goal {

    /** Distancia a la que se da por hecho que el aldeano está <b>cruzando</b> la puerta (en el hueco). */
    private static final double HUECO = 1.5D;
    /** Hasta dónde se sigue vigilando la puerta después de cruzarla (para cerrarla al otro lado). */
    private static final double RADIO = 3.0D;
    /** Ticks de cortesía con la puerta abierta y el aldeano ya fuera del hueco, antes de cerrarla. */
    private static final int ESPERA = 5;
    /** A un jugador a menos de esto no se le cierra la puerta. */
    private static final double JUGADOR = 2.5D;

    private final Villager villager;
    @Nullable
    private BlockPos puerta;
    /** ¿La ha cruzado (ha estado en el hueco)? Si no, la puerta no es suya y no se toca. */
    private boolean laUse;
    /** Ticks que lleva fuera del hueco desde que la usó. */
    private int fuera;

    public VillagerDoorGoal(Villager villager) {
        this.villager = villager;
        // SIN banderas: no mueve al aldeano (no interrumpe su faena).
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    public boolean canUse() {
        if (villager.isBaby() || !(villager.level() instanceof ServerLevel level)) {
            return false;
        }
        // A propósito NO se mira si descansa ni si hay refriega: la puerta se cierra también de noche (cuando el
        // aldeano se va a dormir, que es justo cuando el jugador las encuentra abiertas).
        puerta = puertaAbiertaPegada(level);
        laUse = false;
        fuera = 0;
        return puerta != null;
    }

    @Override
    public boolean canContinueToUse() {
        return puerta != null && !villager.isBaby() && distancia(puerta) <= RADIO;
    }

    @Override
    public void tick() {
        if (puerta == null || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        BlockState estado = level.getBlockState(puerta);
        if (!(estado.getBlock() instanceof DoorBlock) || !estado.getValue(DoorBlock.OPEN)) {
            puerta = null; // la cerró otro (o se la llevaron): nada que hacer
            return;
        }
        if (distancia(puerta) <= HUECO) {
            laUse = true; // la está cruzando: es suya
            fuera = 0;
            return;
        }
        if (!laUse) {
            return; // solo pasaba por delante: no se toca la puerta de nadie
        }
        if (++fuera < ESPERA) {
            return;
        }
        if (level.getNearestPlayer(puerta.getX() + 0.5D, puerta.getY() + 0.5D, puerta.getZ() + 0.5D, JUGADOR, false)
                != null) {
            fuera = 0; // hay un jugador en la puerta: no se le cierra
            return;
        }
        cerrar(level, estado);
        puerta = null;
    }

    @Override
    public void stop() {
        puerta = null;
        laUse = false;
        fuera = 0;
    }

    /**
     * La puerta de <b>madera abierta</b> que tiene <b>pegada</b> (en su casilla o al lado: el aldeano que la cruza está
     * a un bloque de ella). Se mira una caja de 3x3x3 alrededor suyo, no el pueblo entero: aquí solo interesa la que
     * está usando.
     */
    @Nullable
    private BlockPos puertaAbiertaPegada(ServerLevel level) {
        BlockPos base = villager.blockPosition();
        BlockPos mejor = null;
        double mejorDistancia = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(base.offset(-1, -1, -1), base.offset(1, 1, 1))) {
            BlockState estado = level.getBlockState(p);
            if (!(estado.getBlock() instanceof DoorBlock) || !estado.getValue(DoorBlock.OPEN)
                    || !DoorBlock.isWoodenDoor(level, p)) {
                continue;
            }
            // La mitad de ABAJO (la de arriba es la misma puerta): se normaliza para cerrarla una sola vez.
            BlockPos abajo = estado.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? p.below() : p.immutable();
            double d = distancia(abajo);
            if (d < mejorDistancia) {
                mejorDistancia = d;
                mejor = abajo;
            }
        }
        return mejor;
    }

    /** Cierra la puerta entera (las dos mitades, con su sonido) y lo apunta en la etiqueta del aldeano. */
    private void cerrar(ServerLevel level, BlockState estado) {
        if (!(estado.getBlock() instanceof DoorBlock puertaBloque) || puerta == null) {
            return;
        }
        puertaBloque.setOpen(villager, level, estado, puerta, false);
        level.playSound(null, puerta, SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.BLOCKS, 0.7F, 1.0F);
        VillageManager.ponerActividad(villager, "Cerrando la puerta");
    }

    /** Distancia del aldeano al centro de la casilla de la puerta. */
    private double distancia(BlockPos p) {
        return Math.sqrt(villager.distanceToSqr(p.getX() + 0.5D, p.getY() + 0.5D, p.getZ() + 0.5D));
    }
}
