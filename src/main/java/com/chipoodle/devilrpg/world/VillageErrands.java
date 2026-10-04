package com.chipoodle.devilrpg.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
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
        // I178 · SE MIRAN TODAS LAS PUERTAS DE ALREDEDOR, NO SOLO LAS DEL CAMINO RECTO (30-sep-2026, medido). El sondeo
        // por la línea al recado **fallaba justo en el caso que importa**: el aldeano pegado a la valla de **su parcela**
        // con el destino a un bloque y `rutaViva=3 nodos alcanzaba=NO` —la compuerta que estorba está en el **punto medio
        // del lado**, no en la recta—, así que no la encontraba y el pueblo se quedaba fuera. Ahora se mira un cuadro
        // alrededor del aldeano (radio 8, que cubre de sobra una parcela de 9×9) y se abre la que **separa** de verdad.
        // El coste se paga **una vez cada 10 ticks por aldeano** (no en cada tick): el barrido completo de 9×9×5 en cada
        // tick fue lo que ya me mordió antes.
        CompoundTag datos = villager.getPersistentData();
        long ahora = level.getGameTime();
        if (ahora - datos.getLong("DevilRpgPuertasMiradas") < 10L) {
            return;
        }
        datos.putLong("DevilRpgPuertasMiradas", ahora);
        BlockPos yo = villager.blockPosition();
        // I179 · PRIMERO LOS PORTONES **CONOCIDOS** DEL PUEBLO (30-sep-2026, medido). El aldeano puede estar encerrado
        // lejos de la puerta que le estorba: medido, el minero DENTRO del corral (628, 78, 566) con su recado en el
        // almacén, y el portón del corral a **21 bloques** (fuera de cualquier cuadro). Pero la geometría es conocida —el
        // portón del corral y los de las tres parcelas— así que se prueban ESOS primero: barato (una docena de celdas) y
        // completo. Es el patrón de «conjunto de candidatas» que pide el diseño del nivel 3, en vez de adivinar.
        long centroGuardado = datos.getLong(com.chipoodle.devilrpg.world.VillageManager.CENTRO_TAG);
        if (centroGuardado != 0L) {
            BlockPos centro = BlockPos.of(centroGuardado);
            int nivel = VillageGenerator.cotaDeLaPlaza(level, centro);
            // I196 · Y SI UNO ESTÁ DENTRO DEL CORRAL Y EL OTRO FUERA, LA COMPUERTA QUE SEPARA ES **LA DEL RECINTO**
            // (3-oct-2026, MEDIDO). Aquí estaba el fallo de la ráfaga del ganadero: `Segismunda`, en `626, 78, 565`
            // (justo al ESTE de la valla), con su recado en `622, 78, 565` (dentro) — los dos al **mismo lado** del
            // portón del corral (`607, 78, 566`, el centro de la valla OESTE), así que la prueba de eje decía «esta
            // puerta no los separa» y **no se abría**; y la valla que SÍ los separa es la ESTE (`x=625`), que **no
            // tiene compuerta**. Cerrada la única entrada, el planificador no puede cruzar y el aldeano se rinde con
            // `alcanza=NO` una y otra vez (20 y 17 avisos en las corridas 86 y 87, `[Gate]` = 0: nunca se abrió nada).
            // Cuando uno está dentro y el otro fuera, la compuerta del recinto **es** la que hay que abrir: se abre
            // sin la prueba de eje (abrir de más es barato; el goal de los portones la vuelve a cerrar si no hay nadie).
            BlockPos baseAnexo = VillageGenerator.baseDeAnexo(centro);
            boolean yoDentro = dentroDe(baseAnexo, VillageGenerator.ANEXO_RADIO, yo);
            boolean recadoDentro = dentroDe(baseAnexo, VillageGenerator.ANEXO_RADIO, recado);
            if (yoDentro != recadoDentro) {
                // I197: el corral tiene DOS portones (oeste y este), así que se prueba el que separa de verdad: si el
                // aldeano está al este y su recado dentro, el portón del este es el suyo (el del oeste le deja a 40
                // bloques de vuelta). Se abre el que esté entre los dos y, si ninguno lo está, el del lado del recado.
                BlockPos este = VillageGenerator.portonDelCorralEste(centro, nivel);
                BlockPos oeste = VillageGenerator.portonDelCorral(centro, nivel);
                boolean yoAlEste = yo.getX() > centro.getX();
                BlockPos primero = yoAlEste ? este : oeste;
                BlockPos segundo = yoAlEste ? oeste : este;
                if (primero != null && abrirSinMirarElEje(level, primero)) {
                    return;
                }
                if (segundo != null && abrirSinMirarElEje(level, segundo)) {
                    return;
                }
            }
            BlockPos portonDelCorral = VillageGenerator.portonDelCorral(centro, nivel);
            if (portonDelCorral != null && mirarYQuizáAbrir(level, portonDelCorral, yo, recado)) {
                return;
            }
            for (int i = 0; i < VillageGenerator.numeroDeParcelas(); i++) {
                for (BlockPos compuerta : VillageGenerator.portonesDeLaParcela(centro, i, nivel)) {
                    if (mirarYQuizáAbrir(level, compuerta, yo, recado)) {
                        return;
                    }
                }
            }
        }
        // Y DESPUÉS, LAS PUERTAS DE ALREDEDOR (las casas, la taberna, el almacén): un cuadro de radio 8 que cubre de
        // sobra una parcela de 9×9.
        for (BlockPos p : BlockPos.betweenClosed(yo.offset(-8, -2, -8), yo.offset(8, 2, 8))) {
            if (mirarYQuizáAbrir(level, p.immutable(), yo, recado)) {
                return; // con una que se abra, ya se sigue caminando
            }
        }
    }

    /**
     * I196 · Abre esa compuerta <b>sin la prueba de eje</b>: se usa cuando se sabe que <b>es</b> la que separa (el
     * aldeano dentro del corral y su recado fuera, o al revés). Devuelve {@code true} si la abrió.
     */
    private static boolean abrirSinMirarElEje(ServerLevel level, BlockPos p) {
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
        VillagerGateGoal.abrirParaUnAldeano(level, p.immutable());
        return true;
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
