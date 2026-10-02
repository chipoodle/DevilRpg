package com.chipoodle.devilrpg.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import com.chipoodle.devilrpg.entity.goal.VillagerGateGoal;
import org.jetbrains.annotations.Nullable;

/**
 * <b>M2 · LA AUTORIDAD DE RECADOS</b> (I151 — fase 1 del cerebro propio de la aldea; el plan entero está en
 * {@code docs/aldea-cerebro.md}).
 * <p>
 * Es <b>el único sitio donde se decide si un recado se puede atender</b>, antes de andar. Existe porque el fallo de
 * estos días no era el buscador de caminos —el A* del juego es bueno— sino <b>mandar al aldeano a celdas imposibles y
 * volver a mandarlo después de rendirse</b>:
 * <ul>
 *   <li>{@code casillaDePieCercaDe} devolvía <b>la propia faena</b> cuando no había ninguna casilla libre alrededor, así
 *       que el aldeano <b>empujaba el mueble</b> (la mesa de la taberna, un huevo dentro de la valla) hasta rendirse.
 *       Aquí eso es <b>{@code null}</b>: no hay dónde ponerse, luego no se persigue.</li>
 *   <li>Un recado puede tener casilla y <b>no tener camino</b> (el aldeano encerrado en el corral con el portón cerrado
 *       —clase D—). Se comprueba con una búsqueda de ruta de verdad ({@code createPath} + {@code canReach}).</li>
 * </ul>
 * <b>Coste</b>: la comprobación de ruta es cara, así que se pregunta <b>una vez por recado</b> (al elegirlo), no en cada
 * tick. Y <b>ojo</b>: esto valida el destino <b>final</b>; los <b>tramos intermedios</b> (el portón, que cerrado no se
 * pisa) siguen siendo cosa de {@code destinoDelTramo} y de M3.
 */
public final class VillageErrands {

    private VillageErrands() {
    }

    /**
     * La casilla donde un aldeano <b>puede estar de pie</b> para atender esa faena (la propia, o la más cercana de
     * alrededor), o <b>{@code null}</b> si no hay ninguna. Devolver {@code null} es la diferencia entre «no se puede» y
     * «empuja el mueble hasta rendirse».
     */
    @Nullable
    public static BlockPos casillaPosible(ServerLevel level, @Nullable BlockPos faena) {
        if (faena == null) {
            return null;
        }
        if (VillageManager.esCeldaDePie(level, faena)) {
            return faena;
        }
        BlockPos mejor = null;
        double mejorDist = Double.MAX_VALUE;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 1; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos p = faena.offset(dx, dy, dz);
                    if (!VillageManager.esCeldaDePie(level, p)) {
                        continue;
                    }
                    double d = p.distSqr(faena);
                    if (d < mejorDist) {
                        mejorDist = d;
                        mejor = p;
                    }
                }
            }
        }
        return mejor;
    }

    /**
     * ¿Merece la pena el recado? <b>Casilla posible</b> y <b>ruta que la alcance</b>. Si contesta que no, la tarea
     * <b>no lo empieza</b>: lo aparca (I33) y prueba con el siguiente, que es lo que evita el bucle
     * «rendirse → volver a elegir el mismo → rendirse».
     */
    public static boolean elRecadoEsPosible(ServerLevel level, Villager villager, @Nullable BlockPos faena) {
        BlockPos casilla = casillaPosible(level, faena);
        return casilla != null && VillageManager.hayRutaQueAlcanza(villager, casilla);
    }

    /**
     * <b>M3 · LA ALDEA ABRE SUS PROPIAS PUERTAS</b> (I151). Si el aldeano está <b>dentro</b> del recinto del corral
     * anexo y su recado está <b>fuera</b> (o al revés), el portón se abre <b>antes</b> de pedir la ruta.
     * <p>
     * <b>Por qué hace falta (medido)</b>: el ganadero se rendía con la etiqueta «Bajando lo del corral» desde
     * {@code 613, 62, 574} —<b>dentro</b> del corral— con {@code ruta=1 nodos … alcanza=NO} hacia el almacén. El
     * planificador del juego <b>no da ruta a través de un portón cerrado</b>, y el portón solo se abría cuando el
     * aldeano ya estaba <b>pegado</b> a él (2,6 bloques): si no hay ruta <b>hasta</b> el portón, nunca llega a pedirlo —
     * círculo cerrado. Abriéndolo la aldea <b>antes</b>, la ruta existe y el aldeano sale.
     * <p>
     * Y es la misma pieza que resuelve <b>los pollos</b>: la puerta se abre para el aldeano que va a cruzar y se cierra
     * detrás (de eso se encarga {@code VillagerGateGoal}), y un pollo no sabe abrirla, así que se queda dentro.
     */
    public static void abrirLaPuertaSiHaceFalta(ServerLevel level, Villager villager, BlockPos center, int nivel,
            @Nullable BlockPos recado) {
        BlockPos base = VillageGenerator.baseDeAnexo(center);
        boolean elAldeanoDentro = dentroDe(base, VillageGenerator.ANEXO_RADIO, villager.blockPosition());
        boolean elRecadoDentro = dentroDe(base, VillageGenerator.ANEXO_RADIO, recado);
        // Solo se abre si CRUZA: si el aldeano está dentro y su recado también, la puerta se queda CERRADA (si no, se
        // escapan las gallinas, que es justo lo que hay que evitar).
        if (elAldeanoDentro != elRecadoDentro) {
            BlockPos porton = VillageGenerator.portonDelCorral(center, nivel);
            if (porton != null) {
                VillagerGateGoal.abrirParaUnAldeano(level, porton);
            }
        }
    }

    /** ¿Ese punto cae dentro del recinto (cuadrado de radio {@code radio} alrededor de {@code base})? */
    private static boolean dentroDe(BlockPos base, int radio, @Nullable BlockPos p) {
        return p != null && Math.abs(p.getX() - base.getX()) <= radio && Math.abs(p.getZ() - base.getZ()) <= radio;
    }

    /**
     * I169 · <b>SI PARA LLEGAR HAY QUE CRUZAR UNA PUERTA CERRADA, SE ABRE.</b> (30-sep-2026)
     * <p>
     * Es la última familia que quedaba en el registro: destinos que <b>sí son casillas de pie</b> pero
     * <b>sin ruta</b> —`ruta=1 nodos … alcanza=NO` entre dos casillas normales—, con la <b>valla de la parcela</b> o la
     * del <b>corral</b> en medio y su compuerta cerrada: `Guardando lo suyo`, `Recogiendo lo suyo`, `Labrando la
     * huerta`, `Recogiendo el corral`… El planificador no cruza una compuerta cerrada, así que el aldeano se rinde
     * teniendo el destino a un paso.
     * <p>
     * <b>CÓMO SE HACE SIN HACER ESTRAGOS</b>: se miran las puertas y compuertas <b>alrededor del aldeano</b> (radio 4) y
     * solo se abre la que <b>de verdad lo separa de su recado</b>: la puerta está en una pared, así que se compara en qué
     * lado de esa pared está el aldeano y en qué lado está el recado (por la dirección en la que la puerta separa). Si
     * los dos están del mismo lado —o el recado está en la misma casilla—, <b>no se toca</b>: así <b>no se abren las del
     * corral</b> para que no se escapen las gallinas. No hace falta saber de qué aldea es el aldeano: la puerta está a
     * su lado, y con eso basta.
     */
    public static void abrirLoQueCierreElPaso(ServerLevel level, Villager villager, @Nullable BlockPos recado) {
        if (recado == null) {
            return;
        }
        BlockPos yo = villager.blockPosition();
        int dx = Integer.signum(recado.getX() - yo.getX());
        int dz = Integer.signum(recado.getZ() - yo.getZ());
        if (dx == 0 && dz == 0) {
            return; // ya está encima del recado
        }
        // SE SONDEA EL CAMINO, NO EL VECINDARIO: una L de 6 pasos hacia el recado (y la casilla de encima, que es
        // donde está la hoja de la puerta). Un barrido de 9×9×5 por aldeano y por tick serían ~400 consultas cada
        // tick; esto son 24, y la compuerta que estorba está justo ahí, en el camino.
        for (int paso = 1; paso <= 6; paso++) {
            for (int altura = 0; altura <= 1; altura++) {
                if (!mirarYQuizáAbrir(level, yo.offset(dx * paso, altura, 0), yo, recado)
                        && !mirarYQuizáAbrir(level, yo.offset(0, altura, dz * paso), yo, recado)) {
                    continue;
                }
                return; // con una que se abra, ya se sigue caminando; si hace falta otra, se abrirá en el siguiente tramo
            }
        }
    }

    /** Mira esa casilla: si es una puerta/compuerta cerrada que <b>separa</b> al aldeano de su recado, la abre. */
    private static boolean mirarYQuizáAbrir(ServerLevel level, BlockPos p, BlockPos yo, BlockPos recado) {
        BlockState estado = level.getBlockState(p);
        boolean esPuerta = estado.getBlock() instanceof DoorBlock;
        boolean esCompuerta = estado.getBlock() instanceof FenceGateBlock;
        if (!esPuerta && !esCompuerta) {
            return false;
        }
        boolean abierta = esPuerta ? estado.getValue(DoorBlock.OPEN) : estado.getValue(FenceGateBlock.OPEN);
        if (abierta) {
            return false;
        }
        // LA DIRECCIÓN EN LA QUE LA PUERTA SEPARA. La convención del juego es que una puerta o compuerta **se cruza a
        // lo largo de su `FACING`** (una compuerta que mira al norte se pasa yendo de norte a sur), así que ése es el
        // eje que se prueba primero. Y para no arriesgar una rendición por equivocarme de eje, se prueba **también** el
        // perpendicular: abrir una puerta de más es barato (el propio goal de los portones la vuelve a cerrar si no hay
        // nadie dentro), pero **no** abrir la que estorba es una rendición segura.
        Direction eje = estado.getValue(HorizontalDirectionalBlock.FACING);
        for (Direction separa : new Direction[]{eje, eje.getClockWise()}) {
            int ladoYo = Integer.signum(separa.getStepX() * (yo.getX() - p.getX())
                    + separa.getStepZ() * (yo.getZ() - p.getZ()));
            int ladoRecado = Integer.signum(separa.getStepX() * (recado.getX() - p.getX())
                    + separa.getStepZ() * (recado.getZ() - p.getZ()));
            if (ladoYo != 0 && ladoRecado != 0 && ladoYo != ladoRecado) {
                VillagerGateGoal.abrirParaUnAldeano(level, p.immutable());
                return true;
            }
        }
        return false; // los dos del mismo lado (o el aldeano encima): esta puerta no es la que hay que abrir
    }
}
