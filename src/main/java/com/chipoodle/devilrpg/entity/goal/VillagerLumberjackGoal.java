package com.chipoodle.devilrpg.entity.goal;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.world.VillageGenerator;
import com.chipoodle.devilrpg.world.VillageManager;
import com.chipoodle.devilrpg.world.VillagePantry;
import com.chipoodle.devilrpg.world.VillageStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * <b>Leñador / reforestador</b> de la aldea (etapa B): <b>tala árboles de verdad</b> alrededor del pueblo y <b>los
 * replanta</b>, y lleva la madera al <b>almacén</b> (que es de donde salen los tablones, los palos, los arcos y las
 * flechas de los herreros).
 * <p>
 * Es el <b>LEÑADOR</b>, un oficio propio desde la etapa H: su profesión es <b>FLETCHER</b> (flechero, cuya estación
 * es la <b>mesa de flechas</b> de su taller, en la arboleda) y va a <b>prioridad 4</b>, como los demás oficios. Antes
 * esto lo hacía el <b>recolector</b> (el holgazán) como segundo goal y a prioridad 6, para no gastar un puesto; el
 * jugador pidió separarlos: *"es necesario que haya un aldeano que se especialice únicamente en cortar madera y
 * plantar árboles, para dejar totalmente libre al recolector para que recoja y transporte"*. Y aquella prioridad 6
 * <b>empataba con la taberna</b> (6), así que el aldeano con hambre y leña pendiente no iba a comer.
 * <p>
 * <b>Repuebla el monte de verdad</b>: cada árbol que tala lo replanta <b>en su sitio</b> y con la <b>misma
 * especie</b>; y si se queda sin semilla en la mano, <b>se apunta el hueco</b> para volver con la primera que
 * consiga. Además, cuando lleva una <b>pila de semillas</b> encima (o ya no ve árboles), se va a
 * <b>plantarlas repartidas</b>: un árbol por sitio y con {@link #DISTANCIA_ENTRE_ARBOLES} de separación, empezando
 * por los huecos apuntados. Las semillas que encuentra (las que sueltan las hojas y recoge el recolector, las que
 * deja el jugador en el almacén) terminan en el monte, no apiladas en un cofre.
 * <p>
 * <b>Solo tala árboles DE VERDAD</b>, que es lo importante: el muro de la aldea y las casas son de troncos, así que
 * nunca se corta nada de lo construido. Un tronco cuenta como árbol si su base está sobre tierra y tiene <b>hojas
 * cerca</b> y otro tronco encima (un poste suelto no) y, <b>dentro del recinto</b>, además se pregunta al
 * <b>plano</b> de la aldea ({@link #esParteDeLaAldea}): lo que puso el pueblo no se toca. Con eso el leñador
 * <b>despeja los árboles del monte que quedaron dentro de la muralla</b> —lo pidió el jugador: medido en su guardado
 * había <b>137</b> árboles sueltos dentro de la valla, y el pueblo no los tocaba—, tala y replanta en la
 * <b>arboleda del pueblo</b> ({@link VillageGenerator#enLaArboleda}, el hueco de césped donde la aldea planta
 * <b>sus</b> árboles: es la madera de una aldea que nace <b>sin bosque</b>) y repuebla el monte <b>de fuera</b>. Lo
 * de dentro se tala pero <b>no</b> se replanta: la aldea se despeja.
 * <p>
 * <b>LA ARBOLEDA DEL PUEBLO ES LO PRIMERO, Y SALIR FUERA ES LO ÚLTIMO</b> (lo pidió el jugador: *"todavía el leñador
 * quiere ir afuera de la aldea. Si el bosque dentro de la aldea no tiene todavía árboles que vaya al almacén por polvo
 * de hueso a fertilizar el árbol. El ir afuera es el último de los recursos"*). Mientras la arboleda <b>no esté
 * poblada</b> (menos de {@link #ARBOLES_DE_LA_ARBOLEDA_ESTABLECIDA} de sus doce plazas con árbol), el leñador
 * <b>abona sus plantones</b> con la harina de huesos del pueblo —la que saca el granjero del compostero— y la va a
 * buscar <b>andando</b> al <b>almacén</b> o a la <b>despensa</b>, con la harina <b>en la mano</b> (no a distancia):
 * un plantón abonado es un árbol en minutos.
 * <p>
 * Y el monte de <b>fuera</b> se mira <b>al final</b>: primero la arboleda (sus árboles, sus huecos, sus plantones y sus
 * restos), después los árboles sueltos de dentro, y solo cuando dentro no queda nada que hacer se sale. Antes era una
 * sola búsqueda <b>por distancia</b> y, con el bosque pegado a la valla (medido en su partida: 243 árboles con la base
 * a menos de 102 bloques), el árbol de fuera ganaba casi siempre y su arboleda —2 árboles y 10 plantones— se quedaba
 * sin leñador.
 */
public class VillagerLumberjackGoal extends Goal {

    /** Distancia a la que ya alcanza el tronco para dar el hachazo (y al hueco donde planta). */
    private static final double REACH = 3.5D;
    /** Ticks de hachazo antes de que el árbol caiga (y de faena antes de que la semilla quede plantada). */
    private static final int WORK_TICKS = 25;
    private static final int REST_TICKS = 10;
    /** Sin árboles ni semillas a la vista: a esperar (buscar árboles recorre muchas columnas, no se hace por tick). */
    private static final int IDLE_REST_TICKS = 120;
    /** Si no logra acercarse en este tiempo, abandona ese árbol. */
    private static final int STUCK_LIMIT = 160;
    /** Troncos que lleva encima antes de ir al almacén a descargar. */
    private static final int LLEVAR_TRONCOS = 12;
    /** Semillas de árbol que se lleva del almacén para replantar. */
    private static final int SEMILLAS_POR_VIAJE = 16;
    /**
     * Semillas que aguanta en la mano antes de ir a <b>plantarlas</b>: el leñador no es un vivero andante. En cuanto
     * llega a esta pila se va a repartirlas por el monte (y de paso no se le llena el inventario de semillas).
     */
    private static final int SEMILLAS_PARA_PLANTAR = 8;
    /** Huecos que recuerda (troncos que taló y no pudo replantar en el momento): a esos vuelve con la primera semilla. */
    private static final int MAX_PENDIENTES = 8;
    /**
     * Separación mínima entre árboles: es lo que hace que las semillas se <b>repartan</b> por el monte en vez de
     * apelotonarse en un rincón (y que el árbol nuevo tenga sitio para crecer).
     */
    private static final double DISTANCIA_ENTRE_ARBOLES = 5.0D;
    /** Radio de búsqueda de un claro donde repoblar (alrededor del leñador, como la tala). */
    private static final int RADIO_CLARO = 48;
    /**
     * Clavos a los que se les comprueba la separación: esa comprobación mira un cubo de bloques y se hace con los
     * más cercanos, no con las 800 columnas del barrido.
     */
    private static final int CANDIDATOS_A_COMPROBAR = 8;
    /** Ticks entre los barridos caros: buscar un claro donde plantar y mirar cómo va la arboleda del pueblo. */
    private static final int BARRIDO_COOLDOWN = 40;
    /** Margen alrededor del corral anexo donde NO se planta: una rama no tiene que caerle al ganadero encima. */
    private static final int MARGEN_ANEXO = 3;
    /** Radio de búsqueda de árboles ALREDEDOR DEL LEÑADOR, y radio mínimo (fuera de la valla, que es de troncos). */
    private static final int RADIO_BUSQUEDA = 40;
    private static final double RADIO_MINIMO = VillageGenerator.FENCE_RADIUS + 3.0D;
    /** Hasta dónde se le deja alejar del pueblo (si no, se pierde por el mundo talando). */
    private static final double RADIO_MAXIMO = VillageGenerator.FENCE_RADIUS + 40.0D;
    /** Tronco más alto que tala de una vez (una selva puede tener árboles altísimos). */
    private static final int ALTURA_MAX = 16;
    /** Velocidad al ir al árbol (y al almacén). */
    private static final float VELOCIDAD = 0.6F;
    /**
     * Harina de huesos que se trae del almacén (o de la despensa) para abonar la arboleda. Una tanda, como las
     * semillas: no es un vivero andante.
     */
    private static final int HARINA_POR_VIAJE = 8;
    /**
     * Árboles que tiene que tener la arboleda del pueblo (de sus doce plazas) para darla por <b>poblada</b> y dejar de
     * gastar harina de huesos en ella. Es la mitad: con menos, la arboleda no sostiene la madera del pueblo (se tala y
     * se replanta y se queda pelada) y el leñador se iba al monte de fuera; con la mitad o más, la arboleda se
     * mantiene sola (tala y replanta) y el que sobra lo busca fuera.
     */
    private static final int ARBOLES_DE_LA_ARBOLEDA_ESTABLECIDA = 6;

    private enum Fase { TALAR, PLANTAR, ABONAR, RECOGER_HARINA, ENTREGAR }

    /**
     * Un <b>hueco que se quedó sin replantar</b>: dónde estaba el árbol que se taló y de qué <b>especie</b> era (para
     * poner la misma). Es el "mismo sitio donde estaba el árbol" al que vuelve cuando consigue una semilla.
     */
    private record Hueco(BlockPos pos, @Nullable Item semilla) {
    }

    private final Villager villager;
    private final BlockPos center;
    private final int objectiveIndex;
    @Nullable
    private BlockPos target;
    private Fase fase = Fase.TALAR;
    /** Troncos que taló y se quedaron sin replantar (sin semilla a mano): a esos vuelve luego. */
    private final List<Hueco> pendientes = new ArrayList<>();
    private int workTicks;
    private int restTicks;
    private int stuckTicks;
    private double mejorDistancia = Double.MAX_VALUE;
    private int barridoCooldown;
    /** Cota de la aldea (para los plantones de la arboleda): se pregunta UNA vez, no en cada tick. */
    private int nivelAldea = Integer.MIN_VALUE;
    /** ¿La harina que va a buscar está en el ALMACÉN (y no en la despensa)? Decide a dónde camina y su alcance. */
    private boolean harinaEnElAlmacen;

    public VillagerLumberjackGoal(Villager villager, BlockPos center, int objectiveIndex) {
        this.villager = villager;
        this.center = center;
        this.objectiveIndex = objectiveIndex;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (restTicks > 0) {
            restTicks--;
            return false;
        }
        if (barridoCooldown > 0) {
            barridoCooldown--;
        }
        if (villager.isBaby() || !(villager.level() instanceof ServerLevel level)) {
            return false;
        }
        // El leñador es el RECOLECTOR (el aldeano sin oficio): es el que tiene menos faena fija. El gestor le da el
        // goal solo al holgazán que ya hace de recolector, así que basta con mirar el oficio.
        // El oficio del LEÑADOR es flechero (su estación es la mesa de flechas del taller de la arboleda, etapa H).
        if (villager.getVillagerData().getProfession() != VillagerProfession.FLETCHER) {
            return false;
        }
        if (VillageManager.isVillageUnderAttack(level, objectiveIndex) || VillageManager.estaDescansando(villager)) {
            return false;
        }
        double dx = villager.getX() - center.getX();
        double dz = villager.getZ() - center.getZ();
        if (dx * dx + dz * dz > RADIO_MAXIMO * RADIO_MAXIMO) {
            return false; // se ha ido demasiado lejos del pueblo
        }
        // DENTRO PRIMERO Y FUERA DESPUÉS (el monte de fuera es el ÚLTIMO de los recursos): se busca árbol DENTRO del
        // recinto —la arboleda del pueblo y los árboles sueltos— y solo si ahí no hay nada se mira el bosque de fuera.
        // Ver la cabecera de la clase: antes era una sola búsqueda por distancia y ganaba casi siempre el de fuera.
        BlockPos troncoDentro = buscarArbol(level, true);
        BlockPos troncoFuera = troncoDentro == null ? buscarArbol(level, false) : null;
        BlockPos tronco = troncoDentro != null ? troncoDentro : troncoFuera;
        int troncos = troncosEnMano();
        // Con la mochila llena (o con madera y sin árboles a la vista) se va a descargar al almacén.
        if (troncos >= LLEVAR_TRONCOS || (tronco == null && troncos > 0)) {
            fase = Fase.ENTREGAR;
            target = VillageStorage.puntoDeApoyo(level, center);
            return target != null;
        }
        // LA ARBOLEDA DEL PUEBLO MANDA MIENTRAS NO ESTÉ POBLADA: sus plantones se abonan con la harina de huesos del
        // pueblo y, si no la tiene en la mano, va a por ella al ALMACÉN (o a la despensa) ANDANDO. Es lo que pidió el
        // jugador: *"si el bosque dentro de la aldea no tiene todavía árboles que vaya al almacén por polvo de hueso a
        // fertilizar el árbol; el ir afuera es el último de los recursos"*. Va ANTES de talar (incluso antes de talar la
        // propia arboleda): un plantón abonado es un árbol en minutos, y con la arboleda a medias (medido en su
        // partida: 2 árboles y 10 plantones de 12 plazas) el pueblo no tiene la madera que necesita.
        // OJO: esta comprobación NO va con el cooldown del barrido de claros. Lo llevaba y era un fallo medido: el
        // cooldown solo baja cuando `canUse` llega a la altura del barrido, y como cuando no hay nada que hacer el goal
        // descansa 120 ticks de golpe, tardaba ~40 descansos (¡4 minutos!) en volver a mirar la arboleda; en la medida
        // del arnés Hortensia se pasó la corrida yendo al monte de fuera con 16 de harina esperando en la despensa.
        // Mirar la arboleda son 12 plazas (barato): se mira en cada decisión.
        if (faltaPoblarLaArboleda(level)) {
            BlockPos planton = plantonDeLaArboledaMasCercano(level);
            if (planton != null) {
                if (harinaEnMano() > 0) {
                    fase = Fase.ABONAR;
                    target = planton;
                    return true;
                }
                BlockPos irPorHarina = puntoConHarina(level);
                if (irPorHarina != null) {
                    fase = Fase.RECOGER_HARINA;
                    target = irPorHarina;
                    return true;
                }
            }
        }
        // PLANTAR: repartir las semillas que lleva encima. Dos motivos para ir a plantar: que lleve una PILA (no es un
        // vivero andante) o que no haya árboles a la vista (entonces el monte se queda pelado y hay que reponerlo).
        // Primero los huecos que ya conoce —los troncos que taló y se quedaron sin replantar, que es el mismo sitio
        // donde estaba el árbol— porque mirarlos es barato; después la arboleda del pueblo (su madera) y solo al final
        // el barrido de un claro, que es lo único que planta FUERA.
        int semillas = semillasEnMano();
        if (semillas >= SEMILLAS_PARA_PLANTAR || (tronco == null && semillas > 0)) {
            BlockPos hueco = primerPendiente(level);
            if (hueco == null) {
                hueco = huecoDeLaArboleda(level); // la arboleda del pueblo, primero (es su madera)
            }
            // El claro del monte se busca solo si DENTRO no queda nada por hacer: irse del pueblo es lo último.
            if (hueco == null && troncoDentro == null && barridoCooldown <= 0) {
                hueco = buscarClaro(level);
                barridoCooldown = BARRIDO_COOLDOWN;
            }
            if (hueco != null) {
                fase = Fase.PLANTAR;
                target = hueco;
                return true;
            }
        }
        if (troncoDentro != null) {
            fase = Fase.TALAR;
            target = troncoDentro;
            return true;
        }
        // RESTO COLGANDO (solo en la arboleda del pueblo): un tronco que ya no cuelga de ningún árbol —lo dejó a
        // medias el hachazo viejo— se remata. Va DESPUÉS de los árboles (primero la madera buena) y es lo que
        // limpia los que quedaron flotando en el guardado del jugador SIN tener que tocar el mundo guardado.
        BlockPos resto = buscarRestoColgando(level);
        if (resto != null) {
            fase = Fase.TALAR;
            target = resto;
            return true;
        }
        // Sin árboles ni semillas en la mano: si el almacén tiene semillas, va a por ellas (para replantar).
        if (semillas == 0 && haySemillasEnElAlmacen(level)) {
            fase = Fase.ENTREGAR;
            target = VillageStorage.puntoDeApoyo(level, center);
            return target != null;
        }
        // Y, POR ÚLTIMO, EL MONTE DE FUERA: dentro ya no queda nada que hacer (ni árbol que talar, ni hueco, ni
        // plantón que abonar, ni resto colgando), que es cuando el jugador quiere que salga: *"el ir afuera es el
        // último de los recursos"*.
        if (troncoFuera != null) {
            fase = Fase.TALAR;
            target = troncoFuera;
            return true;
        }
        restTicks = IDLE_REST_TICKS;
        return false;
    }

    @Override
    public void start() {
        workTicks = 0;
        stuckTicks = 0;
        mejorDistancia = Double.MAX_VALUE;
        puntoDePaso = null;
        irAlObjetivo();
    }

    @Override
    public boolean canContinueToUse() {
        if (target != null && stuckTicks >= STUCK_LIMIT) {
            // RENDIRSE = DEJARLO POR UN RATO (I33): el sitio al que no llegó (el tronco, el hueco, el plantón o el
            // almacén) se apunta para no volver a elegir EL MISMO en bucle, que es lo que dejaba al leñador
            // empujando el mismo árbol para siempre.
            VillageManager.marcarPuntoFallido(villager, target);
            return false;
        }
        return target != null && !villager.isBaby() && stuckTicks < STUCK_LIMIT
                && !VillageManager.estaDescansando(villager);
    }

    @Override
    public void tick() {
        if (target == null || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        villager.getLookControl().setLookAt(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D);
        double alcance = switch (fase) {
            case ENTREGAR -> VillageStorage.ALCANCE_ALMACEN;
            // La harina se coge en el almacén o en la despensa: el almacén es una construcción grande y su punto de
            // apoyo pide el alcance de siempre; a la despensa (el kiosco) se llega de cerca, como el granjero.
            case RECOGER_HARINA -> harinaEnElAlmacen ? VillageStorage.ALCANCE_ALMACEN : REACH;
            default -> REACH;
        };
        double distancia = Math.sqrt(villager.distanceToSqr(target.getX() + 0.5D, target.getY() + 0.5D,
                target.getZ() + 0.5D));
        if (distancia > alcance) {
            // SE CAMINA HACIA EL PASO, NO SIEMPRE HACIA EL DESTINO, y el atasco se mide contra ESE paso. Medido con el
            // arnés: yendo directos al almacén desde fuera del muro la ruta existe pero EMPIEZA ALEJÁNDOSE (da la vuelta
            // hasta el portón), así que "no me acerco al destino" marcaba atasco a los 8 s y el leñador se rendía una y
            // otra vez con el almacén apuntado como punto fallido (ver `VillageManager.pasoParaCruzarElMuro` / I112).
            BlockPos paso = pasoDeCamino(level);
            if (!paso.equals(puntoDePaso)) {
                puntoDePaso = paso; // paso nuevo: el progreso se mide de cero
                mejorDistancia = Double.MAX_VALUE;
                stuckTicks = 0;
            }
            double hastaElPaso = Math.sqrt(villager.distanceToSqr(paso.getX() + 0.5D, paso.getY() + 0.5D,
                    paso.getZ() + 0.5D));
            VillageManager.caminarHacia(villager, paso, VELOCIDAD);
            // Atascado = NO ACERCARSE (invariante I3).
            if (hastaElPaso < mejorDistancia - 0.5D) {
                mejorDistancia = hastaElPaso;
                stuckTicks = 0;
            } else {
                stuckTicks++;
            }
            VillageManager.ponerActividad(villager, switch (fase) {
                case TALAR -> "Yendo al arbol";
                case PLANTAR -> "Yendo a plantar";
                case ABONAR -> "Yendo a la arboleda";
                case RECOGER_HARINA -> "Yendo por polvo de hueso";
                case ENTREGAR -> "Llevando la madera";
            });
            return;
        }
        VillageManager.parar(villager);
        villager.swing(InteractionHand.MAIN_HAND);
        if (++workTicks < WORK_TICKS) {
            VillageManager.ponerActividad(villager, switch (fase) {
                case TALAR -> "Talando";
                case PLANTAR -> "Plantando";
                case ABONAR -> "Abonando la arboleda";
                case RECOGER_HARINA -> "Cogiendo polvo de hueso";
                case ENTREGAR -> "Guardando la madera";
            });
            return;
        }
        workTicks = 0;
        switch (fase) {
            case TALAR -> talar(level);
            case PLANTAR -> plantar(level);
            case ABONAR -> abonar(level);
            case RECOGER_HARINA -> recogerHarina(level);
            case ENTREGAR -> entregar(level);
        }
        target = null;
        restTicks = REST_TICKS;
    }

    @Override
    public void stop() {
        target = null;
        restTicks = REST_TICKS;
        villager.getNavigation().stop();
    }

    // --- las faenas ---------------------------------------------------------------------------------

    /**
     * Tala el árbol <b>ENTERO</b>: primero la columna del tronco hacia arriba (hasta {@link #ALTURA_MAX}) y después
     * <b>lo que cuelga de ella</b> (ver {@link #rematarElArbol}), que es lo que se quedaba flotando. Se lleva la
     * madera. Si la base está sobre tierra, <b>replanta</b> ahí mismo una semilla <b>de la misma especie</b>; y si no
     * tiene ninguna a mano, <b>apunta el hueco</b> para volver con la primera que consiga (ver {@link #pendientes}).
     */
    private void talar(ServerLevel level) {
        if (target == null) {
            return;
        }
        BlockState troncoBase = level.getBlockState(target);
        boolean eraBase = esTierra(level.getBlockState(target.below()));
        int topeColumna = target.getY() + ALTURA_MAX;
        List<BlockPos> talados = new ArrayList<>();
        BlockPos p = target;
        while (p.getY() <= topeColumna && level.getBlockState(p).is(BlockTags.LOGS)) {
            picarTronco(level, p);
            talados.add(p.immutable());
            p = p.above();
        }
        // EL RESTO DEL ÁRBOL: lo que cuelga de la columna (la parte torcida y las ramas). Sin esto el árbol se
        // quedaba a medias —ver `rematarElArbol`— y esos troncos se quedaban FLOTANDO para siempre.
        int ramas = talados.isEmpty() ? 0 : rematarElArbol(level, talados, target);
        // Y UNA PASADA ALREDEDOR DEL TOCÓN: los restos que quedaron colgando un poco más allá de las ramas (el jugador
        // los veía en el monte: *"¿por qué el leñador no quita todos los logs flotantes justo debajo del bosque donde
        // tala?"*). Va después de `rematarElArbol` porque aquél va pegado a la columna y éste barre más ancho.
        ramas += limpiarAlrededorDelTocon(level, target);
        int troncos = talados.size() + ramas;
        int altura = 0;
        for (BlockPos t : talados) {
            altura = Math.max(altura, t.getY() - target.getY());
        }
        // LAS RAMAS: al árbol talado se le quitan también SUS hojas. No es capricho: las semillas y los palos que
        // sueltan las hojas al caer se quedaban encima de las copas de los árboles de al lado, en el aire, fuera del
        // alcance de cualquier aldeano (ni el recolector llega a un objeto que está a 5 bloques por encima de sus
        // pies), y el suelo del pueblo se llenaba de plantones tirados: medido en el guardado del jugador, 53
        // plantones de abedul y 19 palos colgados en las copas de su aldea de mar. Desramado, todo cae al suelo —los
        // plantones van al zurrón del leñador, para replantar, y el resto al pie del árbol— y lo recoge el recolector.
        int hojas = troncos > 0 ? desramar(level, target, altura) : 0;
        // Las HOJAS que queden colgando se van solas (mecánica de vanilla).
        if (eraBase && troncos > 0) {
            BlockPos hueco = target;
            Item misma = semillaDeTronco(troncoBase);
            if (!replantar(level, hueco, misma)) {
                apuntarHueco(hueco, misma); // sin semilla a mano: el sitio queda pendiente, no se pierde
            }
        }
        if (troncos > 0) {
            VillageManager.ponerSuceso(villager, "Talo un arbol (" + troncos + ")");
            DevilRpg.LOGGER.info("[Village] El lenador: talo {} tronco(s) ({} de la columna y {} que colgaban) y"
                    + " desramo {} hoja(s)", troncos, talados.size(), ramas, hojas);
        }
    }

    /**
     * Pica un tronco: se lleva la madera al zurrón, la suelta al suelo si no le cabe y toca su sonido.
     */
    private void picarTronco(ServerLevel level, BlockPos p) {
        BlockState tronco = level.getBlockState(p);
        List<ItemStack> drops = Block.getDrops(tronco, level, p, null);
        level.destroyBlock(p, false);
        level.playSound(null, p, tronco.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.7F, 1.0F);
        for (ItemStack drop : drops) {
            ItemStack resto = guardarEnInventario(drop);
            if (!resto.isEmpty()) {
                level.addFreshEntity(new ItemEntity(level, p.getX() + 0.5D, p.getY() + 0.5D, p.getZ() + 0.5D, resto));
            }
        }
    }

    /** Tope de troncos por árbol: la columna ({@link #ALTURA_MAX}) MÁS lo que cuelga de ella. */
    private static final int TRONCOS_MAX_POR_ARBOL = 32;
    /** Radio (en X/Z, desde el tronco que se está talando) por el que se siguen buscando SUS troncos. */
    private static final int RADIO_RAMAS = 3;
    /** Radio (X/Z) de la limpieza que se hace ALREDEDOR DEL TOCÓN después de talar (ver {@link #limpiarAlrededorDelTocon}). */
    private static final int RADIO_LIMPIEZA = 12;
    /** Tope de restos que se rematan en esa pasada, para no pasarse la tarde limpiando el monte. */
    private static final int RESTOS_POR_LIMPIEZA = 16;
    /**
     * Radio (X/Z, alrededor del propio leñador) por el que se buscan restos <b>FUERA de la muralla</b>. No se puede
     * barrer todo el bosque (sería carísimo), así que se mira a su alrededor: según va andando por el monte, los va
     * rematando. Ver {@link #buscarRestoColgando}.
     */
    private static final int RADIO_RESTO_CERCA = 20;

    /**
     * <b>LIMPIA ALREDEDOR DEL TOCÓN.</b> Lo pidió el jugador: *"¿por qué el leñador no quita todos los logs flotantes
     * justo debajo del bosque donde tala? debería poder hacer eso"*.
     * <p>
     * Después de talar se mira una caja de {@link #RADIO_LIMPIEZA} alrededor de la base (desde un bloque por debajo
     * hasta {@link #ALTURA_MAX} + 4 por encima) y se <b>rematan</b> los troncos que quedaron <b>colgando</b>: de pie y
     * sin nada construido pegado ({@link VillageGenerator#esTroncoDeArbol}, que deja fuera el muro, los postes y las
     * casetas) y que <b>no llegan al suelo por troncos</b> ({@link #tieneApoyo}, la prueba de un árbol de verdad).
     * <p>
     * Por qué hacía falta, medido en su guardado con `build/troncos_flotantes.py` (la misma prueba que usa el mod,
     * celda a celda): en la <b>arboleda del pueblo 0 restos</b> —ésos los limpia {@code buscarRestoColgando}— pero en
     * el <b>monte</b> había <b>96</b> troncos flotando donde tala, porque la búsqueda de restos solo miraba dentro de
     * la arboleda (fuera no se podía distinguir un resto de un poste del pueblo). Aquí no hay que distinguirlo: la
     * caja está alrededor de un árbol que <b>acaba de talar él</b>.
     *
     * @return cuántos troncos ha rematado (se suman a los del árbol y se llevan al zurrón como la demás madera)
     */
    private int limpiarAlrededorDelTocon(ServerLevel level, BlockPos base) {
        // La Y se mide DESDE LA COTA del pueblo (I1), no desde la del tocón: la aldea está nivelada y los árboles que
        // él tala están a esa altura, así que la caja cubre el árbol entero sin depender de dónde cayó el bloque.
        int cota = VillageGenerator.cotaDeLaPlaza(level, center);
        int rematados = 0;
        for (int dx = -RADIO_LIMPIEZA; dx <= RADIO_LIMPIEZA && rematados < RESTOS_POR_LIMPIEZA; dx++) {
            for (int dz = -RADIO_LIMPIEZA; dz <= RADIO_LIMPIEZA && rematados < RESTOS_POR_LIMPIEZA; dz++) {
                for (int y = cota - 2;
                        y <= cota + ALTURA_MAX + 6 && rematados < RESTOS_POR_LIMPIEZA; y++) {
                    BlockPos p = new BlockPos(base.getX() + dx, y, base.getZ() + dz);
                    if (!level.getBlockState(p).is(BlockTags.LOGS)) {
                        continue;
                    }
                    if (!VillageGenerator.esTroncoDeArbol(level, p) || tieneApoyo(level, p)) {
                        continue; // construido (o pegado a algo construido), o cuelga de un árbol de verdad
                    }
                    picarTronco(level, p);
                    rematados++;
                }
            }
        }
        if (rematados > 0) {
            DevilRpg.LOGGER.info("[Village] El lenador: remato {} tronco(s) que quedaban colgando alrededor del"
                    + " tocon {}", rematados, base.toShortString());
        }
        return rematados;
    }

    /**
     * <b>Remata el árbol</b>: pica los troncos que <b>cuelgan</b> de la columna que se acaba de talar.
     * <p>
     * Hace falta de verdad, y está <b>medido</b> en el guardado del jugador (aldea 2, la arboleda del pueblo): el
     * tronco de un árbol <b>no siempre es una columna recta</b> —la <b>acacia</b> sube recta y luego <b>tuerce en
     * diagonal</b>: comprobado bloque a bloque, el árbol entero de (1370,120..124,1391) sigue con un tronco en
     * (1369,125,1391), una casilla al lado y una arriba— y el hachazo de antes solo subía en vertical, así que la
     * parte torcida y sus ramas se quedaban <b>en el aire</b>. Encima el desramado les quita las hojas, de modo que
     * el trozo que queda ya <b>no se parece a un árbol</b> ({@code baseDeArbol} exige tierra debajo y
     * {@code esArbolSuelto}, hojas cerca) y se quedaba flotando <b>para siempre</b>: 9 troncos de acacia medidos a
     * y=122..126 en la arboleda del pueblo, sin una hoja encima.
     * <p>
     * Se recorre con una <b>búsqueda corta</b> desde las casillas que se acaban de quedar en aire: solo troncos
     * <b>pegados</b> (o en diagonal hacia arriba, que es como crecen las ramas: {@code dy 0..1}) a uno ya talado,
     * dentro de {@link #RADIO_RAMAS} del tronco y con el tope de {@link #TRONCOS_MAX_POR_ARBOL}. Y cada tronco tiene
     * que pasar {@link VillageGenerator#esTroncoDeArbol}: <b>de pie</b> (eje Y) y <b>sin nada construido pegado</b>,
     * que es lo que deja fuera el <b>muro de la aldea</b> (troncos tumbados, eje X/Z) y los postes de las casas.
     *
     * @return cuántos troncos se han picado de los que colgaban
     */
    private int rematarElArbol(ServerLevel level, List<BlockPos> talados, BlockPos tronco) {
        int presupuesto = TRONCOS_MAX_POR_ARBOL - talados.size();
        if (presupuesto <= 0) {
            DevilRpg.LOGGER.info("[Village] El lenador: el arbol de {} pasa del tope de {} troncos, se deja lo que"
                    + " quede", tronco.toShortString(), TRONCOS_MAX_POR_ARBOL);
            return 0;
        }
        // La FRONTERA son las casillas que se acaban de quedar en aire (el tronco talado): mirando a su alrededor
        // aparecen los trozos que cuelgan del mismo árbol. OJO: se usan las casillas YA VACÍAS como frente, que es
        // lo que permite seguir el rastro de un tronco torcido (su vecino de arriba está en diagonal).
        Deque<BlockPos> frontera = new ArrayDeque<>(talados);
        Set<Long> vistos = new HashSet<>();
        for (BlockPos p : talados) {
            vistos.add(p.asLong());
        }
        int topeY = tronco.getY() + ALTURA_MAX * 2;
        int quitados = 0;
        while (!frontera.isEmpty() && quitados < presupuesto) {
            BlockPos desde = frontera.poll();
            for (int dy = 0; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dy == 0 && dx == 0 && dz == 0) {
                            continue; // él mismo
                        }
                        BlockPos p = desde.offset(dx, dy, dz);
                        if (!vistos.add(p.asLong())) {
                            continue;
                        }
                        if (p.getY() > topeY || Math.abs(p.getX() - tronco.getX()) > RADIO_RAMAS
                                || Math.abs(p.getZ() - tronco.getZ()) > RADIO_RAMAS) {
                            continue; // ni muy alto ni lejos del tronco: eso ya no es el mismo árbol
                        }
                        if (!level.getBlockState(p).is(BlockTags.LOGS)) {
                            continue;
                        }
                        if (!VillageGenerator.esTroncoDeArbol(level, p)) {
                            continue; // el muro (tumbado) o algo construido: no se toca
                        }
                        picarTronco(level, p);
                        quitados++;
                        frontera.add(p.immutable());
                    }
                }
            }
        }
        return quitados;
    }

    /** Radio (en X/Z, desde el tronco) donde se buscan las hojas del árbol talado. */
    private static final int RADIO_COPA = 3;
    /** Tope de hojas por árbol (una copa de roble son ~40; el tope es por si el azar junta varias). */
    private static final int HOJAS_MAX = 120;

    /**
     * <b>Desrama</b> el árbol talado: quita las hojas de su copa, las que están alrededor de su tronco. Los
     * <b>plantones</b> van al zurrón del leñador (son los que necesita para replantar) y lo demás (palos, manzanas)
     * <b>al pie del árbol</b>, al suelo, que es donde el recolector del pueblo lo encuentra; si cayeran desde la copa
     * se quedarían encima de los árboles de al lado, en el aire, para siempre.
     */
    private int desramar(ServerLevel level, BlockPos base, int altura) {
        int quitadas = 0;
        for (int dy = 0; dy <= altura + 2 && quitadas < HOJAS_MAX; dy++) {
            for (int dx = -RADIO_COPA; dx <= RADIO_COPA && quitadas < HOJAS_MAX; dx++) {
                for (int dz = -RADIO_COPA; dz <= RADIO_COPA && quitadas < HOJAS_MAX; dz++) {
                    if (dx * dx + dz * dz > RADIO_COPA * RADIO_COPA) {
                        continue;
                    }
                    BlockPos p = base.offset(dx, dy, dz);
                    BlockState hoja = level.getBlockState(p);
                    if (!hoja.is(BlockTags.LEAVES)) {
                        continue;
                    }
                    List<ItemStack> drops = Block.getDrops(hoja, level, p, null);
                    level.destroyBlock(p, false);
                    for (ItemStack drop : drops) {
                        ItemStack resto = guardarEnInventario(drop);
                        if (!resto.isEmpty()) {
                            // lint:ok I1 porque aqui `base` es el TRONCO de un arbol que existe (una posicion real
                            // del mundo con su Y buena), no el centro ni la base de la aldea.
                            level.addFreshEntity(new ItemEntity(level, base.getX() + 0.5D, base.getY() + 0.5D,
                                    base.getZ() + 0.5D, resto));
                        }
                    }
                    quitadas++;
                }
            }
        }
        return quitadas;
    }

    /**
     * Planta UNA semilla en el hueco al que fue: la del <b>mismo árbol</b> que hubo ahí si es un hueco apuntado, y si
     * no la primera que tenga. Es donde las semillas se <b>reparten</b>: un árbol por sitio (los claros se eligen con
     * {@link #estaDespejado}).
     */
    private void plantar(ServerLevel level) {
        if (target == null) {
            return;
        }
        BlockPos hueco = target;
        if (!esHuecoDeTierra(level, hueco)) {
            pendientes.removeIf(h -> h.pos().equals(hueco)); // el jugador construyó ahí o creció otra cosa
            return;
        }
        ItemStack semilla = sacarSemilla(semillaDelHueco(hueco));
        if (!(semilla.getItem() instanceof BlockItem blockItem)) {
            return; // no le quedan semillas de árbol (otro goal las habrá usado)
        }
        plantarSemilla(level, hueco, blockItem);
        pendientes.removeIf(h -> h.pos().equals(hueco));
        VillageManager.ponerSuceso(villager, "Planto un arbol");
        DevilRpg.LOGGER.info("[Village] El lenador: planto un arbol en {} (le quedan {} semillas)", hueco,
                semillasEnMano());
    }

    /**
     * Planta una semilla en la base del árbol que acaba de talar (la de la misma especie, si la tiene).
     *
     * @return {@code false} si no pudo (no tenía semilla a mano): el sitio queda <b>pendiente</b> y vuelve luego
     */
    private boolean replantar(ServerLevel level, BlockPos base, @Nullable Item preferida) {
        if (!esHuecoDeTierra(level, base)) {
            return true; // ya no hay hueco que replantar: no se apunta nada
        }
        ItemStack semilla = sacarSemilla(preferida);
        if (!(semilla.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        plantarSemilla(level, base, blockItem);
        VillageManager.ponerSuceso(villager, "Replanto el arbol");
        return true;
    }

    /** Pone el bloque de la semilla y su sonido. */
    private static void plantarSemilla(ServerLevel level, BlockPos pos, BlockItem semilla) {
        BlockState plantado = semilla.getBlock().defaultBlockState();
        level.setBlock(pos, plantado, Block.UPDATE_ALL);
        level.playSound(null, pos, plantado.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.7F, 1.0F);
    }

    /**
     * <b>Abona la arboleda del pueblo</b>: echa una <b>harina de huesos</b> —que lleva <b>en la mano</b>, traída del
     * almacén o de la despensa (ver {@link #recogerHarina})— al plantón al que fue, para que el árbol crezca ya. Es lo
     * que hace que una aldea <b>sin bosque</b> —una islita— tenga madera en minutos en vez de esperar a que los
     * plantones crezcan solos: no se inventa madera, se cuida la que el pueblo plantó. El leñador deja de hacerlo en
     * cuanto la arboleda está poblada (ver {@link #faltaPoblarLaArboleda}).
     */
    private void abonar(ServerLevel level) {
        if (target == null || !level.getBlockState(target).is(BlockTags.SAPLINGS)) {
            return; // ya no hay plantón (creció, o alguien lo quitó)
        }
        ItemStack harina = sacarHarina();
        if (harina.isEmpty()) {
            return; // se le acabó la harina por el camino: el siguiente `canUse` lo manda a por más
        }
        if (net.minecraft.world.item.BoneMealItem.growCrop(harina, level, target)) {
            level.playSound(null, target, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 0.8F, 1.0F);
            VillageManager.ponerSuceso(villager, "Abono la arboleda");
            DevilRpg.LOGGER.info("[Village] El lenador: abona la arboleda del pueblo en {} (le quedan {} de harina)",
                    target, harinaEnMano());
        }
    }

    /**
     * <b>Va a por la harina de huesos</b> al almacén (o a la despensa, según dónde la haya) y se trae una tanda. Es el
     * viaje que pidió el jugador (*"que vaya al almacén por polvo de hueso"*): antes la cogía del cofre <b>a
     * distancia</b>, sin ir, y eso no se veía por ningún lado (y el pueblo podía quedarse sin harina sin que el
     * leñador se enterara).
     */
    private void recogerHarina(ServerLevel level) {
        Container origen = harinaEnElAlmacen ? VillageStorage.almacen(level, center)
                : VillagePantry.despensa(level, center);
        if (origen == null) {
            return;
        }
        int cogidas = VillagePantry.sacar(origen, s -> s.is(Items.BONE_MEAL), HARINA_POR_VIAJE);
        if (cogidas > 0) {
            guardarEnInventario(new ItemStack(Items.BONE_MEAL, cogidas));
            VillageManager.ponerSuceso(villager, "Cogio polvo de hueso (" + cogidas + ")");
            DevilRpg.LOGGER.info("[Village] El lenador: se lleva {} de harina de huesos del {} para la arboleda",
                    cogidas, harinaEnElAlmacen ? "almacen" : "despensa");
        }
    }

    /**
     * ¿Todavía hay que <b>poblar la arboleda</b>? Sí mientras tenga menos de
     * {@link #ARBOLES_DE_LA_ARBOLEDA_ESTABLECIDA} árboles (de sus doce plazas): entonces sus plantones se abonan para
     * que crezcan ya. Antes bastaba con que hubiera <b>un</b> árbol para dejar de abonar, y con la arboleda a medias
     * (2 árboles y 10 plantones, medido en la partida del jugador) el pueblo no tenía madera y el leñador se iba al
     * monte de fuera.
     */
    private boolean faltaPoblarLaArboleda(ServerLevel level) {
        int arboles = 0;
        for (BlockPos p : VillageGenerator.plantonesDeLaArboleda(center, nivelDeLaAldea(level))) {
            for (int dy = 0; dy <= ALTURA_MAX; dy++) {
                if (level.getBlockState(p.above(dy)).is(BlockTags.LOGS)) {
                    arboles++;
                    break;
                }
            }
        }
        return arboles < ARBOLES_DE_LA_ARBOLEDA_ESTABLECIDA;
    }

    /** El plantón de la arboleda más cercano (el que se abona), o {@code null} si no queda ninguno. */
    @Nullable
    private BlockPos plantonDeLaArboledaMasCercano(ServerLevel level) {
        BlockPos mejor = null;
        double mejorDist = Double.MAX_VALUE;
        for (BlockPos p : VillageGenerator.plantonesDeLaArboleda(center, nivelDeLaAldea(level))) {
            if (!level.getBlockState(p).is(BlockTags.SAPLINGS)) {
                continue;
            }
            double d = villager.distanceToSqr(p.getX() + 0.5D, p.getY() + 0.5D, p.getZ() + 0.5D);
            if (d < mejorDist) {
                mejorDist = d;
                mejor = p;
            }
        }
        return mejor;
    }

    /**
     * Un <b>hueco libre de la arboleda del pueblo</b> donde plantar, o {@code null} si ya están todos ocupados.
     * <p>
     * Va <b>antes</b> del barrido general a propósito: la arboleda es la madera del pueblo y sus seis celdas están
     * <b>dentro del recinto</b>, donde el barrido general no planta (y una rejilla de 2 en 2 ni siquiera pasa por
     * todas ellas). Sin esto, el leñador talaba la arboleda y el hueco se quedaba vacío, así que la arboleda se
     * apagaba sola (lo reportó el jugador: "no está plantando").
     */
    @Nullable
    private BlockPos huecoDeLaArboleda(ServerLevel level) {
        BlockPos mejor = null;
        double mejorDist = Double.MAX_VALUE;
        for (BlockPos p : VillageGenerator.plantonesDeLaArboleda(center, nivelDeLaAldea(level))) {
            if (!esHuecoDeTierra(level, p)) {
                continue; // ya hay un plantón o un árbol: lo cuida el pueblo
            }
            double d = villager.distanceToSqr(p.getX() + 0.5D, p.getY() + 0.5D, p.getZ() + 0.5D);
            if (d < mejorDist) {
                mejorDist = d;
                mejor = p;
            }
        }
        return mejor;
    }

    /**
     * <b>A dónde va a por la harina de huesos</b>: al punto de apoyo del contenedor que la tenga, o {@code null} si el
     * pueblo no tiene ninguna. OJO: la hace el granjero en su compostero y la guarda en la <b>despensa</b> (ahí vive,
     * junto a las semillas y el abono, porque es un recambio suyo); en el almacén aparece lo que el recolector barre del
     * suelo o lo que el granjero muele de los huesos. Se miran <b>los dos</b> sitios: quedarse solo con el almacén dejaba
     * la arboleda sin abonar nunca. Deja en {@link #harinaEnElAlmacen} de cuál de los dos se trata (para caminar al
     * sitio bueno y con el alcance bueno).
     */
    @Nullable
    private BlockPos puntoConHarina(ServerLevel level) {
        Container despensa = VillagePantry.despensa(level, center);
        if (VillagePantry.contar(despensa, s -> s.is(Items.BONE_MEAL)) > 0) {
            harinaEnElAlmacen = false;
            return VillagePantry.puntoDeApoyo(level, center);
        }
        Container almacen = VillageStorage.almacen(level, center);
        if (VillagePantry.contar(almacen, s -> s.is(Items.BONE_MEAL)) > 0) {
            harinaEnElAlmacen = true;
            return VillageStorage.puntoDeApoyo(level, center);
        }
        return null;
    }

    /** Cuánta harina de huesos lleva <b>en la mano</b> (es la que puede echar: el pueblo no le abastece a distancia). */
    private int harinaEnMano() {
        int n = 0;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (s.is(Items.BONE_MEAL)) {
                n += s.getCount();
            }
        }
        return n;
    }

    /** Saca UNA harina de huesos del zurrón (vacío si no le queda). */
    private ItemStack sacarHarina() {
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (s.is(Items.BONE_MEAL)) {
                ItemStack una = s.copyWithCount(1);
                s.shrink(1);
                if (s.isEmpty()) {
                    villager.getInventory().setItem(i, ItemStack.EMPTY);
                }
                return una;
            }
        }
        return ItemStack.EMPTY;
    }

    /** La cota de la aldea (para los plantones de la arboleda): se pregunta UNA vez, no en cada tick. */
    private int nivelDeLaAldea(ServerLevel level) {
        if (nivelAldea == Integer.MIN_VALUE) {
            nivelAldea = VillageGenerator.cotaDeLaPlaza(level, center);
        }
        return nivelAldea;
    }

    /** Deja la madera en el almacén y coge semillas para seguir replantando. */
    private void entregar(ServerLevel level) {
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (s.isEmpty() || !s.is(ItemTags.LOGS)) {
                continue;
            }
            ItemStack resto = VillageStorage.guardar(level, center, s.copy());
            villager.getInventory().setItem(i, resto);
        }
        Container almacen = VillageStorage.almacen(level, center);
        if (almacen != null && semillasEnMano() == 0) {
            for (ItemStack semilla : List.of(new ItemStack(Items.OAK_SAPLING), new ItemStack(Items.SPRUCE_SAPLING),
                    new ItemStack(Items.BIRCH_SAPLING), new ItemStack(Items.JUNGLE_SAPLING),
                    new ItemStack(Items.ACACIA_SAPLING), new ItemStack(Items.DARK_OAK_SAPLING),
                    new ItemStack(Items.CHERRY_SAPLING), new ItemStack(Items.MANGROVE_PROPAGULE))) {
                if (VillagePantry.sacar(almacen, s -> ItemStack.isSameItem(s, semilla), SEMILLAS_POR_VIAJE) > 0) {
                    guardarEnInventario(semilla.copyWithCount(SEMILLAS_POR_VIAJE));
                    break;
                }
            }
        }
    }

    // --- buscar árboles y claros --------------------------------------------------------------------

    /**
     * El <b>árbol de verdad</b> más cercano al leñador <b>de los de dentro</b> ({@code dentro = true}: la arboleda del
     * pueblo y los árboles sueltos que quedaron dentro de la valla) <b>o de los de fuera</b> ({@code dentro = false}:
     * el monte), o {@code null} si en esa mitad no hay ninguno.
     * <p>
     * Se recorre una rejilla de 2 en 2 alrededor suyo (no por tick: el goal descansa cuando no encuentra nada) y se
     * busca <b>una mitad cada vez</b> para que el de dentro gane siempre: el monte de fuera es el <b>último</b> recurso
     * (lo pidió el jugador). Ver la cabecera de la clase.
     */
    @Nullable
    private BlockPos buscarArbol(ServerLevel level, boolean dentro) {
        BlockPos mejor = null;
        double mejorDist = Double.MAX_VALUE;
        // LA ARBOLEDA DEL PUEBLO SE MIRA SIEMPRE, esté donde esté el leñador: es SU bosque y sus doce plazas se saben
        // (`plantonesDeLaArboleda`). El barrido de abajo es de 40 bloques alrededor del aldeano, así que una arboleda al
        // otro lado del pueblo no se veía: medido con el arnés en la aldea del jugador, Hortensia estaba a 105 bloques
        // de su arboleda (con 2 árboles y 10 plantones), no los veía y se iba al monte de fuera.
        if (dentro) {
            for (BlockPos plaza : VillageGenerator.plantonesDeLaArboleda(center, nivelDeLaAldea(level))) {
                BlockPos base = baseDeArbol(level, plaza.getX(), plaza.getZ());
                if (base == null || VillageManager.esPuntoFallido(villager, base)) {
                    continue;
                }
                BlockPos sitio = celdaDePieParaAlcanzar(level, base);
                if (sitio == null) {
                    continue; // a ese árbol no se llega: a un tronco no se camina (ver `celdaDePieParaAlcanzar` / I116)
                }
                // lint:ok I1 porque aqui `base` es el TRONCO de un arbol que existe (una posicion real del mundo, con
                // su Y buena), no el centro ni la base de la aldea: la distancia al arbol SI es en 3D.
                double dist = villager.distanceToSqr(base.getX() + 0.5D, base.getY() + 0.5D, base.getZ() + 0.5D);
                if (dist < mejorDist) {
                    mejorDist = dist;
                    mejor = base;
                    sitioDelResto = sitio;
                    sitioDelRestoDe = base;
                }
            }
        }
        for (int dx = -RADIO_BUSQUEDA; dx <= RADIO_BUSQUEDA; dx += 2) {
            for (int dz = -RADIO_BUSQUEDA; dz <= RADIO_BUSQUEDA; dz += 2) {
                int x = villager.blockPosition().getX() + dx;
                int z = villager.blockPosition().getZ() + dz;
                double dCentroX = x - center.getX();
                double dCentroZ = z - center.getZ();
                double dCentro = Math.sqrt(dCentroX * dCentroX + dCentroZ * dCentroZ);
                if (dCentro > RADIO_MAXIMO) {
                    continue;
                }
                if (dentro != (dCentro < RADIO_MINIMO)) {
                    continue; // esta pasada es de la otra mitad (dentro o fuera), no de ésta
                }
                BlockPos base = baseDeArbol(level, x, z);
                if (base == null) {
                    continue;
                }
                if (VillageManager.esPuntoFallido(villager, base)) {
                    continue; // a ese árbol no llegó hace poco: se busca otro (I33)
                }
                // DENTRO DEL RECINTO: el muro y las casas son de TRONCOS, así que no se tala a lo loco: solo se tala un
                // ÁRBOL SUELTO, y eso lo decide la FORMA (tronco de pie, con hojas cerca y sin nada construido pegado
                // —ver `VillageGenerator.esArbolSuelto`—), no el plano: el plano de una aldea migrada también tiene
                // dentro los árboles del monte que se encontró (137 medidos en el guardado del jugador), así que
                // preguntarle al plano los daba por construidos y no se talaban NUNCA.
                if (dCentro < RADIO_MINIMO
                        && !VillageGenerator.enLaArboleda(center, new BlockPos(x, 0, z))
                        && !VillageGenerator.esArbolSuelto(level, base)) {
                    continue;
                }
                // lint:ok I1 porque aqui `base` es el tronco de un arbol que existe, no el centro ni la base de la
                // aldea: la distancia al arbol SI es en 3D (un tronco de la ladera esta mas abajo que el pueblo).
                BlockPos sitio = celdaDePieParaAlcanzar(level, base);
                if (sitio == null) {
                    // A UN TRONCO NO SE CAMINA (I114/I116): medido, el leñador se rendía con `destino=jungle_log` y
                    // `ruta=1 nodos ... alcanza=NO` — un tronco a 13 bloques del suelo, sin casilla de pie desde la que
                    // se alcance. Antes de I114 esto pasaba con los RESTOS; con los ÁRBOLES que elige `buscarArbol`
                    // seguía pasando (3 rendiciones medidas en la corrida del 25-sep), así que el filtro va aquí también.
                    continue;
                }
                // lint:ok I1 porque `base` es el tronco de un arbol que existe (su Y es la del mundo, no la del spawn)
                double dist = villager.distanceToSqr(base.getX() + 0.5D, base.getY() + 0.5D, base.getZ() + 0.5D);
                if (dist < mejorDist) {
                    mejorDist = dist;
                    mejor = base;
                    sitioDelResto = sitio;
                    sitioDelRestoDe = base;
                }
            }
        }
        return mejor;
    }

    /**
     * ¿Ese tronco es <b>parte de la aldea construida</b> (el muro, el poste de una casa, el kiosco)? Entonces no se
     * tala. Lo contesta {@link VillageGenerator#esArbolSuelto}: un árbol suelto está de pie, tiene hojas cerca y no
     * tiene nada construido pegado. Antes se le preguntaba al <b>plano</b> de la aldea, y estaba mal: el plano de una
     * aldea migrada también contiene los árboles del monte que se encontró dentro (al capturarlo se escanea el mundo y
     * los troncos no se descartan a propósito, porque el muro y las casas son de troncos), así que el leñador daba por
     * construidos los <b>137</b> árboles que el jugador veía dentro de su aldea.
     */
    private boolean esParteDeLaAldea(ServerLevel level, BlockPos base) {
        return !VillageGenerator.esArbolSuelto(level, base);
    }

    /**
     * Un <b>claro del monte</b> donde poner una semilla nueva: suelo de tierra, <b>cielo abierto</b> (sin cielo el
     * sapling no crece) y <b>separado</b> de cualquier árbol o semilla ({@link #estaDespejado}). Se busca alrededor
     * del leñador, fuera de la valla (igual que la tala) y sin meterse en el corral de los animales.
     */
    @Nullable
    private BlockPos buscarClaro(ServerLevel level) {
        List<BlockPos> candidatos = new ArrayList<>();
        BlockPos base = villager.blockPosition();
        for (int dx = -RADIO_CLARO; dx <= RADIO_CLARO; dx += 2) {
            for (int dz = -RADIO_CLARO; dz <= RADIO_CLARO; dz += 2) {
                int x = base.getX() + dx;
                int z = base.getZ() + dz;
                double dCentroX = x - center.getX();
                double dCentroZ = z - center.getZ();
                double dCentro = Math.sqrt(dCentroX * dCentroX + dCentroZ * dCentroZ);
                if ((dCentro < RADIO_MINIMO && !VillageGenerator.enLaArboleda(center, new BlockPos(x, 0, z)))
                        || dCentro > RADIO_MAXIMO) {
                    continue; // dentro de la valla no se planta (como no se tala), salvo en su arboleda; y muy lejos tampoco
                }
                if (enElAnexo(x, z)) {
                    continue; // el corral de los animales: no se le planta un árbol encima
                }
                BlockPos p = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
                if (!esHuecoDeTierra(level, p) || !level.canSeeSky(p)) {
                    continue;
                }
                candidatos.add(p.immutable());
            }
        }
        // El más cercano primero (menos caminata) y la separación se comprueba solo a los primeros: esa comprobación
        // mira un cubo de bloques alrededor y no se le hace a 800 columnas.
        candidatos.sort(Comparator.comparingDouble(p -> villager.distanceToSqr(p.getX() + 0.5D, p.getY() + 0.5D,
                p.getZ() + 0.5D)));
        for (int i = 0; i < Math.min(candidatos.size(), CANDIDATOS_A_COMPROBAR); i++) {
            if (VillageManager.esPuntoFallido(villager, candidatos.get(i))) {
                continue; // a ese claro no llegó hace poco: se prueba con el siguiente (I33)
            }
            if (estaDespejado(level, candidatos.get(i))) {
                return candidatos.get(i);
            }
        }
        return null;
    }

    /**
     * Si en esa columna hay un árbol, devuelve la posición de su tronco <b>más bajo</b>. Un tronco cuenta como árbol
     * si su base está sobre tierra, tiene <b>otro tronco encima</b> (un poste de una pieza no es un árbol) y tiene
     * <b>hojas cerca</b>.
     */
    @Nullable
    private BlockPos baseDeArbol(ServerLevel level, int x, int z) {
        int cota = VillageGenerator.cotaDeLaPlaza(level, center);
        for (int y = cota + ALTURA_MAX + 4; y > cota - 4; y--) {
            BlockPos p = new BlockPos(x, y, z);
            if (!level.getBlockState(p).is(BlockTags.LOGS)) {
                continue;
            }
            // Es tronco: se baja hasta el más bajo de esa columna.
            BlockPos base = p;
            while (level.getBlockState(base.below()).is(BlockTags.LOGS)) {
                base = base.below();
            }
            if (!esTierra(level.getBlockState(base.below()))) {
                return null; // colgando de otro tronco: forma parte de un árbol ya elegido por su base
            }
            if (!level.getBlockState(base.above()).is(BlockTags.LOGS)) {
                return null; // un tronco de una sola pieza (un poste): no es un árbol
            }
            return tieneHojasCerca(level, base) ? base : null;
        }
        return null;
    }

    /**
     * Un <b>resto colgando de la arboleda del pueblo</b>, o {@code null} si no hay: un tronco que <b>ya no cuelga de
     * ningún árbol</b> porque el hachazo viejo lo dejó a medias (solo picaba la columna vertical: ver
     * {@link #rematarElArbol}).
     * <p>
     * Se busca <b>solo en la arboleda</b>, y no en todo el monte, por dos motivos:
     * <ul>
     *   <li>es <b>el bosque del pueblo</b> (el del informe del jugador: <i>"en el bosque de la aldea deja logs
     *       flotando"</i>), un hueco de césped con sus plazas donde <b>todo</b> árbol lo ha plantado la aldea, así que
     *       un tronco que no llega al suelo ahí es, sin duda, un resto;</li>
     *   <li>fuera solo se puede distinguir un resto de un árbol con la copa puesta (las hojas), y el hachazo ya se las
     *       quitó; y cerca de la valla hay <b>postes del pueblo</b> de tronco de pie que tampoco tienen hojas, así que
     *       buscarlo por todo el monte sería arriesgarse a desmontar algo construido.</li>
     * </ul>
     * La prueba es <b>llegar al suelo por troncos</b> ({@link #tieneApoyo}): un árbol de verdad siempre la pasa
     * (todos sus troncos cuelgan de la base, que está en la tierra), y un resto del hachazo no. Además tiene que ser
     * un tronco <b>de pie y sin nada construido pegado</b> ({@link VillageGenerator#esTroncoDeArbol}).
     */
    @Nullable
    private BlockPos buscarRestoColgando(ServerLevel level) {
        int cota = VillageGenerator.cotaDeLaPlaza(level, center);
        BlockPos mejor = null;
        double mejorDist = Double.MAX_VALUE;
        for (int dx = -RADIO_BUSQUEDA; dx <= RADIO_BUSQUEDA; dx += 2) {
            for (int dz = -RADIO_BUSQUEDA; dz <= RADIO_BUSQUEDA; dz += 2) {
                int x = villager.blockPosition().getX() + dx;
                int z = villager.blockPosition().getZ() + dz;
                if (!VillageGenerator.enLaArboleda(center, new BlockPos(x, 0, z))) {
                    continue; // fuera de la arboleda no se toca nada por esta vía
                }
                // De abajo arriba y se queda con el tronco huérfano MÁS BAJO de la columna: así el hachazo sube desde
                // ahí (y `rematarElArbol` sigue con lo que cuelgue en diagonal).
                BlockPos resto = null;
                for (int y = cota - 4; y <= cota + ALTURA_MAX + 4 && resto == null; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!level.getBlockState(p).is(BlockTags.LOGS)) {
                        continue;
                    }
                    if (!VillageGenerator.esTroncoDeArbol(level, p)) {
                        continue; // tumbado (el muro) o pegado a algo construido: no es un resto del monte
                    }
                    if (tieneApoyo(level, p)) {
                        continue; // cuelga de un árbol de verdad: se tala por su base, pero puede haber un resto encima
                    }
                    resto = p.immutable();
                }
                if (resto == null) {
                    continue;
                }
                if (VillageManager.esPuntoFallido(villager, resto)) {
                    continue; // a ese resto colgando no llegó hace poco: se prueba con el siguiente (I33)
                }
                BlockPos sitio = celdaDePieParaAlcanzar(level, resto);
                if (sitio == null) {
                    continue; // no hay casilla de pie desde la que se alcance: no se le manda a caminar hacia el aire
                }
                double dist = villager.distanceToSqr(resto.getX() + 0.5D, resto.getY() + 0.5D, resto.getZ() + 0.5D);
                if (dist < mejorDist) {
                    mejorDist = dist;
                    mejor = resto;
                    sitioDelResto = sitio;
                    sitioDelRestoDe = resto;
                }
            }
        }
        if (mejor != null) {
            DevilRpg.LOGGER.info("[Village] El lenador: resto colgando en {} (no llega al suelo): lo remata",
                    mejor.toShortString());
            return mejor;
        }
        // Y FUERA DE LA MURALLA, PERO SOLO CERCA DE ÉL: los restos que quedan en el MONTE donde tala. Lo pidió el
        // jugador con una captura (troncos colgando junto al muro): *"¿por qué el leñador no quita todos los logs
        // flotantes justo debajo del bosque donde tala? debería poder hacer eso"*.
        // Aquí no se puede barrer el bosque entero (sería carísimo), así que se mira alrededor del propio leñador; y se
        // deja fuera TODO lo que está dentro del recinto (muralla incluida, que es de TRONCOS de pie, y los postes de
        // las casetas) para no desmontar nada construido. Medido en su guardado (`build/troncos_flotantes.py`): en la
        // arboleda 0 restos (los limpia el barrido de arriba) y en el monte 96 troncos flotando.
        return buscarRestoEnElMonte(level, cota);
    }

    /**
     * Restos colgando <b>en el monte</b> (fuera de la muralla), alrededor del leñador ({@link #RADIO_RESTO_CERCA}). Se
     * piden las mismas pruebas que dentro: tronco <b>de pie y sin nada construido pegado</b>
     * ({@link VillageGenerator#esTroncoDeArbol}) y que <b>no llegue al suelo por troncos</b> ({@link #tieneApoyo}).
     */
    @Nullable
    private BlockPos buscarRestoEnElMonte(ServerLevel level, int cota) {
        BlockPos mejor = null;
        double mejorDist = Double.MAX_VALUE;
        BlockPos aqui = villager.blockPosition();
        for (int dx = -RADIO_RESTO_CERCA; dx <= RADIO_RESTO_CERCA; dx++) {
            for (int dz = -RADIO_RESTO_CERCA; dz <= RADIO_RESTO_CERCA; dz++) {
                int x = aqui.getX() + dx;
                int z = aqui.getZ() + dz;
                double delCentro = Math.sqrt(Math.pow(x - center.getX(), 2) + Math.pow(z - center.getZ(), 2));
                if (delCentro <= VillageGenerator.FENCE_RADIUS + 1.0D
                        || delCentro > VillageGenerator.FENCE_RADIUS + 40.0D) {
                    continue; // dentro del pueblo (el muro y las casas) o más allá de su monte: no se toca
                }
                BlockPos resto = null;
                for (int y = cota - 2; y <= cota + ALTURA_MAX + 4 && resto == null; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!level.getBlockState(p).is(BlockTags.LOGS) || !VillageGenerator.esTroncoDeArbol(level, p)
                            || tieneApoyo(level, p)) {
                        continue;
                    }
                    resto = p.immutable();
                }
                if (resto == null || VillageManager.esPuntoFallido(villager, resto)) {
                    continue;
                }
                BlockPos sitio = celdaDePieParaAlcanzar(level, resto);
                if (sitio == null) {
                    continue; // colgando donde no se alcanza desde ninguna casilla de pie: no se toca
                }
                double dist = villager.distanceToSqr(resto.getX() + 0.5D, resto.getY() + 0.5D, resto.getZ() + 0.5D);
                if (dist < mejorDist) {
                    mejorDist = dist;
                    mejor = resto;
                    sitioDelResto = sitio;
                    sitioDelRestoDe = resto;
                }
            }
        }
        if (mejor != null) {
            DevilRpg.LOGGER.info("[Village] El lenador: resto colgando EN EL MONTE en {} (no llega al suelo): lo remata",
                    mejor.toShortString());
        }
        return mejor;
    }

    /**
     * ¿Ese tronco <b>llega al suelo</b> por otros troncos? Se baja en diagonal (hacia abajo y hacia los lados, que es
     * como bajan las ramas) hasta tocar tierra, con un tope de troncos mirados para no recorrer el mundo.
     */
    private static boolean tieneApoyo(ServerLevel level, BlockPos desde) {
        Deque<BlockPos> cola = new ArrayDeque<>();
        Set<Long> vistos = new HashSet<>();
        cola.add(desde);
        vistos.add(desde.asLong());
        while (!cola.isEmpty() && vistos.size() < TRONCOS_MAX_POR_ARBOL * 2) {
            BlockPos p = cola.poll();
            if (esTierra(level.getBlockState(p.below()))) {
                return true; // tocó el suelo
            }
            for (int dy = -1; dy <= 0; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dy == 0 && dx == 0 && dz == 0) {
                            continue;
                        }
                        BlockPos q = p.offset(dx, dy, dz);
                        if (!vistos.add(q.asLong())) {
                            continue;
                        }
                        if (level.getBlockState(q).is(BlockTags.LOGS)) {
                            cola.add(q);
                        }
                    }
                }
            }
        }
        return false;
    }

    /** ¿Hay hojas alrededor de esa columna? Es lo que distingue un árbol de un poste de madera. */
    private boolean tieneHojasCerca(ServerLevel level, BlockPos base) {        for (int dy = 1; dy <= 6; dy++) {
            BlockPos arriba = base.above(dy);
            for (BlockPos q : BlockPos.betweenClosed(arriba.offset(-2, 0, -2), arriba.offset(2, 0, 2))) {
                if (level.getBlockState(q).is(BlockTags.LEAVES)) {
                    return true;
                }
            }
        }
        return false;
    }

    // --- los huecos pendientes (el mismo sitio donde estaba el árbol) --------------------------------

    /**
     * Apunta un hueco que se quedó sin replantar (el tronco se taló y no había semilla a mano). Se guardan los
     * últimos {@link #MAX_PENDIENTES}: no es un censo del monte, es "por dónde iba".
     */
    private void apuntarHueco(BlockPos pos, @Nullable Item semilla) {
        if (pendientes.size() >= MAX_PENDIENTES) {
            pendientes.remove(0); // el más viejo deja de ser prioritario
        }
        pendientes.add(new Hueco(pos.immutable(), semilla));
    }

    /**
     * El primer hueco apuntado que <b>todavía</b> se puede replantar, o {@code null}. Los que ya no valen (el jugador
     * construyó encima, creció otra cosa, o están <b>dentro del pueblo</b>) se tiran: si no, el leñador volvería a
     * ellos para siempre.
     * <p>
     * Un hueco de <b>dentro de la muralla</b> no se replanta (salvo en la arboleda del pueblo): la aldea se
     * <b>despeja</b> de árboles, que es lo que pidió el jugador; repoblarla sería volver a llenarla de troncos.
     */
    @Nullable
    private BlockPos primerPendiente(ServerLevel level) {
        while (!pendientes.isEmpty()) {
            BlockPos p = pendientes.get(0).pos();
            if (esHuecoDeTierra(level, p) && seReplantaAqui(p)) {
                return p;
            }
            pendientes.remove(0);
        }
        return null;
    }

    /** ¿Ese punto está donde SÍ se replanta? Fuera del recinto, o en la arboleda del pueblo (nunca dentro). */
    private boolean seReplantaAqui(BlockPos p) {
        double dx = p.getX() - center.getX();
        double dz = p.getZ() - center.getZ();
        return Math.sqrt(dx * dx + dz * dz) >= RADIO_MINIMO
                || VillageGenerator.enLaArboleda(center, p);
    }

    /** La semilla con la que hay que replantar ese hueco (la de la especie que había), o {@code null} si es un claro. */
    @Nullable
    private Item semillaDelHueco(BlockPos p) {
        for (Hueco h : pendientes) {
            if (h.pos().equals(p)) {
                return h.semilla();
            }
        }
        return null;
    }

    // --- utilidades ---------------------------------------------------------------------------------

    private static boolean esTierra(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL)
                || state.is(Blocks.MYCELIUM) || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.MUD)
                || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND);
    }

    /**
     * ¿Ahí se puede <b>plantar</b>? Aire libre en el hueco y encima, y <b>tierra de verdad</b> debajo: el tag
     * {@code DIRT} del juego es exactamente lo que acepta un sapling, así que deja fuera la <b>arena</b> de la playa
     * (donde el juego no deja plantarlo y la semilla saltaría) y el <b>camino de tierra</b> de la aldea.
     */
    private static boolean esHuecoDeTierra(ServerLevel level, BlockPos p) {
        return level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()
                && level.getBlockState(p.below()).is(BlockTags.DIRT);
    }

    /**
     * ¿Ese hueco está <b>despejado</b>? Ni troncos, ni hojas, ni semillas a menos de {@link #DISTANCIA_ENTRE_ARBOLES}:
     * es lo que <b>reparte</b> las semillas por el monte en vez de amontonarlas (y lo que le deja sitio al árbol
     * nuevo para crecer).
     */
    private static boolean estaDespejado(ServerLevel level, BlockPos p) {
        int r = (int) DISTANCIA_ENTRE_ARBOLES;
        for (BlockPos q : BlockPos.betweenClosed(p.offset(-r, 0, -r), p.offset(r, 7, r))) {
            BlockState s = level.getBlockState(q);
            if (s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.is(BlockTags.SAPLINGS)) {
                return false;
            }
        }
        return true;
    }

    /** ¿Ese punto (X/Z) es el corral anexo (o su margen)? Ahí no se planta. */
    private boolean enElAnexo(int x, int z) {
        BlockPos anexo = VillageGenerator.baseDeAnexo(center);
        return Math.abs(x - anexo.getX()) <= VillageGenerator.ANEXO_RADIO + MARGEN_ANEXO
                && Math.abs(z - anexo.getZ()) <= VillageGenerator.ANEXO_RADIO + MARGEN_ANEXO;
    }

    /** La semilla que corresponde a ese tronco (para repoblar con la <b>misma especie</b> que había). */
    @Nullable
    private static Item semillaDeTronco(BlockState tronco) {
        if (tronco.is(Blocks.OAK_LOG)) return Items.OAK_SAPLING;
        if (tronco.is(Blocks.SPRUCE_LOG)) return Items.SPRUCE_SAPLING;
        if (tronco.is(Blocks.BIRCH_LOG)) return Items.BIRCH_SAPLING;
        if (tronco.is(Blocks.JUNGLE_LOG)) return Items.JUNGLE_SAPLING;
        if (tronco.is(Blocks.ACACIA_LOG)) return Items.ACACIA_SAPLING;
        if (tronco.is(Blocks.DARK_OAK_LOG)) return Items.DARK_OAK_SAPLING;
        if (tronco.is(Blocks.CHERRY_LOG)) return Items.CHERRY_SAPLING;
        if (tronco.is(Blocks.MANGROVE_LOG)) return Items.MANGROVE_PROPAGULE;
        return null; // un tronco que no conocemos: cualquier semilla vale
    }

    /** ¿Esa semilla es de árbol? (las de árbol se plantan al talar; las demás no). */
    private static boolean esSemillaDeArbol(ItemStack s) {
        return s.is(Items.OAK_SAPLING) || s.is(Items.SPRUCE_SAPLING) || s.is(Items.BIRCH_SAPLING)
                || s.is(Items.JUNGLE_SAPLING) || s.is(Items.ACACIA_SAPLING) || s.is(Items.DARK_OAK_SAPLING)
                || s.is(Items.CHERRY_SAPLING) || s.is(Items.MANGROVE_PROPAGULE);
    }

    /** Saca UNA semilla de árbol del inventario: la de la especie pedida si la tiene, y si no la primera que haya. */
    private ItemStack sacarSemilla(@Nullable Item preferida) {
        int alternativa = -1;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (!esSemillaDeArbol(s)) {
                continue;
            }
            if (preferida != null && s.is(preferida)) {
                return sacarUnaDelSlot(i);
            }
            if (alternativa < 0) {
                alternativa = i;
            }
        }
        return alternativa < 0 ? ItemStack.EMPTY : sacarUnaDelSlot(alternativa);
    }

    /** Saca UNA unidad del hueco {@code slot} del inventario (y lo vacía si se queda sin nada). */
    private ItemStack sacarUnaDelSlot(int slot) {
        ItemStack s = villager.getInventory().getItem(slot);
        ItemStack una = s.copyWithCount(1);
        s.shrink(1);
        if (s.isEmpty()) {
            villager.getInventory().setItem(slot, ItemStack.EMPTY);
        }
        return una;
    }

    private int troncosEnMano() {
        int n = 0;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (s.is(ItemTags.LOGS)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private int semillasEnMano() {
        int n = 0;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack s = villager.getInventory().getItem(i);
            if (esSemillaDeArbol(s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private boolean haySemillasEnElAlmacen(ServerLevel level) {
        return VillagePantry.contar(VillageStorage.almacen(level, center),
                VillagerLumberjackGoal::esSemillaDeArbol) > 0;
    }

    private ItemStack guardarEnInventario(ItemStack stack) {
        ItemStack resto = stack.copy();
        for (int i = 0; i < villager.getInventory().getContainerSize() && !resto.isEmpty(); i++) {
            ItemStack dentro = villager.getInventory().getItem(i);
            if (!dentro.isEmpty() && ItemStack.isSameItemSameComponents(dentro, resto)) {
                int espacio = dentro.getMaxStackSize() - dentro.getCount();
                int mete = Math.min(espacio, resto.getCount());
                dentro.grow(mete);
                resto.shrink(mete);
            }
        }
        for (int i = 0; i < villager.getInventory().getContainerSize() && !resto.isEmpty(); i++) {
            if (villager.getInventory().getItem(i).isEmpty()) {
                villager.getInventory().setItem(i, resto.copy());
                resto = ItemStack.EMPTY;
            }
        }
        return resto;
    }

    private void irAlObjetivo() {
        if (target == null || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        VillageManager.caminarHacia(villager, pasoDeCamino(level), VELOCIDAD);
    }

    /**
     * <b>A DÓNDE SE CAMINA AHORA</b>: al destino y, si hay que <b>salvar la muralla</b> o el destino queda fuera del
     * alcance del planificador, al <b>portón</b> o al punto intermedio que toque
     * ({@link VillageManager#tironConMemoria} / {@code pasoParaCruzarElMuro}). El que llama sigue midiendo su
     * <b>llegada</b> contra el destino —la faena no se adelanta— pero el <b>atasco</b> lo mide contra este paso.
     */
    private BlockPos pasoDeCamino(ServerLevel level) {
        if (fase != Fase.ENTREGAR) {
            // A UN BLOQUE NO SE CAMINA: SE CAMINA A UNA CASILLA DE PIE. Cuando el destino del goal es un bloque —un
            // tronco, un resto colgando—, el planificador del juego devuelve una ruta de **1 nodo** (`alcanza=NO`) y el
            // aldeano **no se mueve**: se queda donde estaba hasta rendirse. Medido (24-sep-2026) en el leñador:
            // `no consigue llegar a 555,66,692 desde 523,63,676 (ruta=1 nodos ... alcanza=NO; destino=jungle_log)` — a
            // 32 bloques y sin dar un paso. Así que se busca la **casilla de pie** desde la que se alcanza el tronco y
            // se camina a ESA; el hachazo sigue apuntando al tronco. Se cachea por destino: buscarla son 100 celdas.
            if (target != null && !target.equals(sitioDelRestoDe)) {
                sitioDelRestoDe = target;
                sitioDelResto = level.getBlockState(target).is(BlockTags.LOGS)
                        ? celdaDePieParaAlcanzar(level, target) : null;
            }
            BlockPos paso = sitioDelResto != null ? sitioDelResto : target;
            // Y SI HAY QUE CRUZAR LA MURALLA, PRIMERO EL PORTÓN (I112/I117): medido, el leñador se rendía 8 veces en
            // los árboles de SELVA DE FUERA (`547,65,703`, `546,65,684`, `548,67,694`...) estando él DENTRO del muro
            // (él a 61 bloques del centro y el árbol a 96): la casilla de pie existe y se alcanza, pero solo desde
            // fuera, y sin el portón empujaba la pared.
            BlockPos porton = VillageManager.pasoParaCruzarElMuro(level, center, villager, paso);
            return porton != null ? porton : paso;
        }
        if (plazaDelPueblo == null) {
            plazaDelPueblo = VillageManager.casillaDeLaCalle(level, center);
        }
        return VillageManager.tironConMemoria(level, center, villager, target, plazaDelPueblo);
    }

    /**
     * <b>¿DESDE DÓNDE SE ALCANZA ESE TRONCO?</b> La casilla de pie (aire a los pies y a la cabeza, suelo firme) desde
     * la que el tronco queda a {@link #REACH} o menos, o {@code null} si no hay ninguna.
     * <p>
     * <b>Por qué</b> (medido, 24-sep-2026): al leñador se le mandaba a <b>caminar hacia el tronco</b>, y un resto
     * colgando está <b>en el aire</b>: se rendía en `555,76,692` (un tronco de selva, con lianas, a 13 bloques del
     * suelo) y en `545,69,691`, apuntaba el sitio como fallido y volvía a elegir el mismo (I33). Con esta prueba, un
     * resto que no se alcanza desde ninguna casilla de pie <b>no se elige</b>, y el que sí se elige se camina a la
     * casilla, que es donde el aldeano puede estar de verdad.
     */
    @Nullable
    private BlockPos celdaDePieParaAlcanzar(ServerLevel level, BlockPos tronco) {
        BlockPos mejor = null;
        double mejorDist = Double.MAX_VALUE;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -2; dy <= 1; dy++) {
                    BlockPos p = tronco.offset(dx, dy, dz);
                    if (!VillageManager.esCeldaDePie(level, p)) {
                        continue;
                    }
                    // Medio bloque de margen: el aldeano no se para siempre justo en el centro de la casilla.
                    double d = Math.sqrt(p.distSqr(tronco));
                    if (d <= REACH - 0.5D && d < mejorDist) {
                        mejorDist = d;
                        mejor = p.immutable();
                    }
                }
            }
        }
        return mejor;
    }

    /** La casilla de pie desde la que se alcanza el resto que se está rematando ahora ({@link #sitioDelRestoDe}). */
    @Nullable
    private BlockPos sitioDelResto;

    /** El resto colgando al que pertenece {@link #sitioDelResto}. */
    @Nullable
    private BlockPos sitioDelRestoDe;

    /** La plaza del pueblo (el último recurso del tirón): se busca UNA vez, no en cada tick. */
    @Nullable
    private BlockPos plazaDelPueblo;

    /** El punto por el que se va ahora mismo ({@link #pasoDeCamino}); si cambia, el progreso se mide de cero. */
    @Nullable
    private BlockPos puntoDePaso;
}
