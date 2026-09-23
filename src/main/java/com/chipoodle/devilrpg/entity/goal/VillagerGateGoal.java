package com.chipoodle.devilrpg.entity.goal;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.world.VillageGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
 * Cinco reglas para que sea educado:
 * <ul>
 *   <li><b>Solo abre</b> si el aldeano <b>va a cruzar</b> (su destino del cerebro está al otro lado del portón): el
 *       ganado no se escapa por un portón abierto todo el día. Antes bastaba con estar cerca, y como su sitio de
 *       trabajo ({@code puntoDeApoyoAnexo}) está a <b>3 bloques</b> del portón del corral, el portón se quedaba
 *       abierto mientras trabajaba dentro: <b>ese era el fallo que reportó el jugador</b> ("cuando el ganadero entra
 *       al corral, deja la puerta abierta y los animales se salen").</li>
 *   <li><b>Cierra en cuanto ha pasado</b>: se apunta de qué lado del portón estaba al abrirlo y, en cuanto su lado
 *       cambia, se cierra (ya no espera a que se aleje 3 bloques, que era lo que no llegaba a pasar nunca).</li>
 *   <li><b>Con un animal del corral en el hueco no se abre</b> (y si se abre, se cierra): el portón es el único sitio
 *       por donde se escapa el rebaño. Se le da un margen corto de espera para que un aldeano no se quede encerrado.
 *       El animal que <b>vuelve a casa</b> desde fuera no cuenta: ése no se está escapando, está entrando.</li>
 *   <li><b>Al rebaño que vuelve no se le cierra la puerta</b>: mientras un animal del pueblo viene a casa
 *       ({@code VuelveAlCorralGoal}) el portón es suyo ({@link #REBANO_USANDO}), que si no el aldeano que anda por el
 *       corral se lo cerraría en las narices en cada tick.</li>
 *   <li><b>Nunca se queda abierto</b>: si pasa {@link #MAXIMO_ABIERTO} sin que nadie lo cruce, se cierra igual (el
 *       aldeano puede volver a abrirlo). Y si <b>no hay ningún aldeano</b> que lo vigile, lo cierra la red de
 *       seguridad del latido ({@link #vigilarPortonesDelAnexo}).</li>
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
     * Si hay un <b>animal del corral</b> pegado al portón, no se le abre: es por donde se escapa el ganado. El portón
     * es el único hueco de la cerca del corral y el pueblo lo abre muchas veces al día (a por los huevos, a la pata
     * del cobertizo, a dormir...), así que con las horas el rebaño se colaba y se perdía: medido en el guardado del
     * jugador, quedaba <b>una vaca</b> dentro y el resto repartido a 80-130 bloques del pueblo.
     * <p>
     * <b>Y es SOLO para el portón del CORRAL</b> (el que sale fuera del recinto), no para el del <b>gallinero</b>: ése
     * va de la caseta de las gallinas al corral, o sea <b>de dentro adentro</b>, y las gallinas <b>viven</b> ahí. Con
     * el mismo radio para los dos, el portón del gallinero <b>no se abría nunca</b>: medido en el guardado del jugador
     * (aldea 0, corral en {@code 520,646}), <b>5 de las 8 gallinas</b> estaban a menos de 2,5 del portón —es su casa—
     * y con la puerta cerrada el ganadero se quedaba fuera con el trabajo dentro (y con el arnés: <b>2 gallinas en el
     * hueco</b> en los volcados y los huevos del corralillo con <b>2.000 ticks</b> de edad sin recoger). Para el
     * gallinero la guardia es el <b>hueco</b> ({@link #HUECO}: el animal tiene que estar <b>en la puerta</b>), no los
     * que andan por su corralillo.
     */
    private static final double ANIMAL_AL_PORTON = 2.5D;
    /**
     * Pero <b>no para siempre</b>: si el animal no se aparta en este tiempo, el portón se abre igual. Un aldeano
     * encerrado en el corral por una vaca tercosa no puede hacer su faena (y el ganadero vive ahí dentro).
     * <p>
     * <b>Y TIENE QUE SER MÁS CORTO QUE LA PACIENCIA DEL ALDEANO QUE LO NECESITA.</b> Estaba en <b>600</b> (30 s) y los
     * goals que tienen que cruzar se rinden antes: {@code VillagerAnimalFarmGoal.STUCK_LIMIT} = 160 (8 s),
     * {@code VillagerPickupGoal.STUCK_LIMIT} = 140 (7 s) y {@code VillagerCollectGoal} = 140 (7 s). Una espera más
     * larga que la paciencia es una espera <b>infinita</b>: el portón nunca se abre para el que lo pide. Con 120 (6 s)
     * el portón se abre <b>siempre</b> antes de que el aldeano se rinda.
     */
    private static final int ESPERA_MAXIMA = 120;
    /**
     * <b>La espera del portón del GALLINERO</b> (2 s, mucho más corta): ahí el animal <b>vive</b> — medido con el
     * arnés, <b>2 gallinas dentro del hueco</b> y 5 a 2,5 casi todo el rato—, así que con la espera larga el portón no
     * se abría <b>nunca</b> y el ganadero se quedaba fuera con los huevos del corralillo dentro (medido: huevos con
     * <b>2.000 ticks</b> de edad sin recoger y el aldeano yendo y viniendo en la puerta). Las gallinas que se cuelan
     * al corral no se pierden para el pueblo (siguen contando como rebaño y el ganadero las <b>cría dentro del
     * corralillo</b>: sus crías nacen en {@code centroDelGallinero}), pero con la puerta cerrada los huevos no los
     * cogía nadie.
     */
    private static final int ESPERA_PEN = 40;
    /**
     * Y un portón abierto no puede quedarse abierto <b>sin que nadie lo cruce</b> más de esto (5 s): el aldeano que
     * lo abrió puede plantarse al lado (su puesto, un hueco que reparar, un objeto que recoger) y entonces la regla
     * de "cierra al alejarse" no se cumpliría <b>nunca</b>. Pasado el plazo se cierra y, si vuelve a necesitarlo, lo
     * abre otra vez.
     */
    private static final int MAXIMO_ABIERTO = 100;
    /** Ticks que no se vuelve a abrir un portón recién cerrado (que el aldeano acabe de salir del hueco). */
    private static final int ENFRIAMIENTO = 20;

    /** El hueco del portón y su borde: quien esté aquí está cruzando (no se le cierra). */
    private static final double HUECO = 1.5D;
    /**
     * Cuántos ticks vale la marca de "lo está usando el REBAÑO": mientras un animal del pueblo viene a casa, su goal
     * pide el portón <b>en cada tick</b> (ver {@link #abrirParaElRebano}). Si deja de pedirlo (ya entró, se fue, lo
     * ató el jugador...), la marca caduca y el portón vuelve a ser cosa de todos.
     */
    private static final int REBANO_FRESCO = 20;
    /**
     * Red de seguridad del latido: un portón del anexo abierto más de esto (5 s) y <b>sin ningún jugador cerca</b> se
     * cierra. Es lo que impide que un portón se quede abierto para siempre cuando no hay ningún aldeano que lo
     * vigile: el del guardado del jugador estaba <b>abierto</b> ({@code open:true}) con el rebaño fuera.
     */
    private static final int VIGILANCIA = 100;
    /** A qué distancia del portón un JUGADOR manda: no se le cierra a nadie en las narices. */
    private static final double JUGADOR_AL_PORTON = 7.0D;

    /**
     * Portones que ha abierto <b>el pueblo</b> (por nivel y posición comprimida): solo ésos se vuelven a cerrar
     * cuando los cierra un aldeano. El mapa es débil para que cerrar un mundo no deje el nivel colgado.
     */
    private static final Map<ServerLevel, Set<Long>> ABIERTOS = new WeakHashMap<>();
    /** Cuándo se vio abierto cada portón (para la red de seguridad del latido). También débil, por el nivel. */
    private static final Map<ServerLevel, Map<Long, Long>> ABIERTO_DESDE = new WeakHashMap<>();
    /**
     * Portones que está usando <b>el rebaño</b> ahora mismo (nivel → posición → tick del último uso). Mientras la
     * marca esté fresca, el portón es <b>suyo</b>: ni el goal de los aldeanos ni la red de seguridad se lo cierran
     * (lo cierra el rebaño al pasar), porque si no se lo cerrarían en las narices cada tick y el animal no entraría.
     */
    private static final Map<ServerLevel, Map<Long, Long>> REBANO_USANDO = new WeakHashMap<>();

    private final Villager villager;
    private final BlockPos center;
    @Nullable
    private BlockPos[] portones;
    @Nullable
    private BlockPos porton;
    /** Ticks que lleva esperando a que el animal pegado al portón se aparte (ver {@link #ESPERA_MAXIMA}). */
    private int esperando;
    /** Ticks que lleva abierto el portón que abrió <b>este</b> aldeano (ver {@link #MAXIMO_ABIERTO}). */
    private int abierto;
    /** Ticks que no se le abre (acaba de cerrarlo: ver {@link #ENFRIAMIENTO}). */
    private int enfriamiento;
    /** ¿Lo abrió ESTE aldeano? (solo entonces se le aplican el plazo y el cruce). */
    private boolean loAbriYo;
    /** De qué lado del portón estaba el aldeano al abrirlo (0 = estaba en el hueco, o no lo abrió él). */
    private int ladoAlAbrir;
    /**
     * Tick a partir del cual se puede <b>recalcular</b> la lista de portones guardada (ver {@link #portonMasCercano}):
     * como mucho una vez cada 5 s, porque la cota mira el terreno de la plaza.
     */
    private long proximoRecalculo;

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
        if (enfriamiento > 0) {
            enfriamiento--;
        }
        boolean abiertoAhora = estado.getValue(FenceGateBlock.OPEN);
        double distancia = distancia(porton);
        // SI EL PORTÓN QUE TIENE NO ES EL DE AL LADO, SE VUELVE A ELEGIR. `portonMasCercano` solo se llama desde
        // `canUse`, y `canUse` NO se vuelve a llamar mientras el goal está corriendo (sigue corriendo mientras tenga
        // un portón a menos de `RADIO`): un portón elegido a mala hora —la aldea a medio construir, la casilla sin
        // cargar— se quedaba pegado PARA SIEMPRE y el aldeano «vigilaba» una puerta lejana sin abrir la que tenía al
        // lado. Medido con el arnés: Isidoro, con su compuerta a 0,87 bloques, tenía elegida la del norte (a 7,09),
        // así que no abría ninguna y se pasaba la noche encerrado en el bancal con su cama al otro lado.
        if (distancia > ABRIR) {
            BlockPos laDeAlLado = portonMasCercano(level);
            if (laDeAlLado != null && distancia(laDeAlLado) < distancia) {
                porton = laDeAlLado;
                return; // con el portón bueno, en el tick siguiente
            }
        }
        if (!abiertoAhora) {
            abierto = 0;
            loAbriYo = false;
            ladoAlAbrir = 0;
            olvidar(level, porton); // si el jugador lo cerró a mano, ya no es "nuestro"
            if (distancia <= ABRIR && enfriamiento <= 0 && vaACruzar(porton, estado)) {
                // El ganado no cruza por un portón abierto: con un animal pegado se espera a que se aparte un poco
                // (pero no para siempre, que el aldeano no se quede encerrado).
                if (animalEnElHueco(level, porton) && esperando++ < esperaMaxima(porton)) {
                    return;
                }
                esperando = 0;
                abrir(level, porton, estado);
            } else if (distancia > ABRIR) {
                esperando = 0;
            }
            return;
        }
        // ABIERTO. Se cuenta lo que lleva así el que abrió ESTE aldeano (o el que esté usando él): es el plazo que
        // impide que un portón se quede abierto mientras su aldeano trabaja justo al lado.
        abierto++;
        if (haCruzado(porton, estado)) {
            // (a) EL FALLO DEL JUGADOR: el aldeano ya ha pasado al otro lado. Se cierra YA, sin esperar a que se
            // aleje (su puesto está a 3 bloques del portón y nunca llegaba a alejarse lo bastante).
            cerrar(level, porton, estado);
            return;
        }
        if (rebanoUsando(level, porton)) {
            // Si el portón lo está usando el REBAÑO (un animal del pueblo que se escapó y vuelve a casa), es SU
            // puerta: lo cierra el rebaño al pasar (o el latido). Sin esto, el aldeano que anda por el corral le
            // cerraría la puerta en las narices <b>cada tick</b> y el animal no entraría nunca. Va ANTES de la regla
            // del animal en el hueco a propósito: mientras el rebaño entra, la puerta es suya (y al que se cuele
            // fuera lo trae de vuelta este mismo mecanismo, que es justo lo que pidió el jugador).
            return;
        }
        if (animalEnElHueco(level, porton) && distancia > ABRIR) {
            // Abierto por el pueblo y con un animal del corral en el hueco: se cierra YA (aunque el aldeano ande
            // cerca), que es por donde se escapan. Si el aldeano está cruzando (pegado al portón), se le deja pasar.
            cerrar(level, porton, estado);
            return;
        }
        if (loAbriYo && abierto >= MAXIMO_ABIERTO) {
            // Lo abrió él y no lo ha cruzado en 5 s: se cierra igual (lo vuelve a abrir si lo necesita). Es lo que
            // impide que el portón se quede abierto mientras el aldeano se planta justo al lado a trabajar.
            cerrar(level, porton, estado);
            return;
        }
        if (distancia > CERRAR && nadieCerca(level, porton)) {
            cerrar(level, porton, estado);
        }
    }

    @Override
    public void stop() {
        porton = null;
        esperando = 0;
        abierto = 0;
        loAbriYo = false;
        ladoAlAbrir = 0;
    }

    // --- los portones -------------------------------------------------------------------------------

    /** El portón que le toca a este aldeano (el más cercano), o {@code null} si no tiene ninguno a {@link #RADIO}. */
    @Nullable
    private BlockPos portonMasCercano(ServerLevel level) {
        if (portones == null) {
            portones = portonesDelAnexo(center, VillageGenerator.cotaDeLaPlaza(level, center)).toArray(new BlockPos[0]);
        }
        BlockPos mejor = puertaValidaMasCercana(level);
        if (mejor == null && level.getGameTime() >= proximoRecalculo) {
            // NINGUNA puerta de la lista guardada está cerca Y NO ES UNA PUERTA DE VERDAD: la lista está RANCIA. Y
            // pasa de verdad: el goal se crea UNA sola vez por aldeano, así que si se creó con la aldea a medio migrar
            // —cuando `cotaDeLaPlaza` todavía no devolvía la cota definitiva— sus posiciones caen al aire y el aldeano
            // se queda SIN PODER ABRIR NINGUNA PUERTA para siempre (las del anexo, que sí caían bien, le quedaban a
            // más de 16 bloques). Medido con el arnés: Isidoro, con su compuerta a 1,15 bloques, no la abría nunca y
            // se pasaba la noche encerrado en el bancal. Se recalcula (como mucho, una vez cada 5 s: la cota mira el
            // terreno).
            proximoRecalculo = level.getGameTime() + 100;
            portones = portonesDelAnexo(center, VillageGenerator.cotaDeLaPlaza(level, center)).toArray(new BlockPos[0]);
            mejor = puertaValidaMasCercana(level);
        }
        return mejor;
    }

    /** La puerta de valla <b>de verdad</b> (existe en el mundo) más cercana de la lista, o {@code null}. */
    @Nullable
    private BlockPos puertaValidaMasCercana(ServerLevel level) {
        BlockPos mejor = null;
        double mejorDistancia = RADIO;
        for (BlockPos p : portones) {
            if (!(level.getBlockState(p).getBlock() instanceof FenceGateBlock)) {
                continue; // esa ya no es una puerta (o la lista está rancia): no cuenta
            }
            double d = distancia(p);
            if (d <= mejorDistancia) {
                mejorDistancia = d;
                mejor = p;
            }
        }
        return mejor;
    }

    /**
     * Los portones que abre y cierra el pueblo cerca de este aldeano: los dos del <b>anexo</b> (el corral y el
     * gallinero), que son los que guardan animales, y las <b>cuatro puertas de valla de cada bancal</b> de la granja
     * (etapa F). El jugador no puede abrirlas con un aldeano (el juego solo le deja abrir puertas de madera), así que
     * las abre el pueblo. La lista vive en <b>un solo sitio</b> ({@link VillageGenerator#todosLosPortones}, I4): la
     * usan también el despeje del hueco y el filtro del plano (I54).
     */
    private static List<BlockPos> portonesDelAnexo(BlockPos center, int nivel) {
        return VillageGenerator.todosLosPortones(center, nivel);
    }

    /** Abre el portón (con su chirrido) y lo apunta como "abierto por el pueblo". */
    private void abrir(ServerLevel level, BlockPos porton, BlockState estado) {
        if (!abrirPorton(level, porton, estado)) {
            return;
        }
        loAbriYo = true;
        ladoAlAbrir = lado(estado, porton, villager.getX(), villager.getZ());
        abierto = 0;
        // Y SE LE HACE REHACER EL CAMINO CON LA COMPUERTA YA ABIERTA. La ruta que traía el aldeano se calculó con ella
        // CERRADA —el juego no le deja planificar a través de una puerta de valla cerrada—, así que acaba en su propia
        // casilla: el aldeano se queda pegado a la valla, la compuerta se cierra a los 5 s sin que nadie la cruce y
        // vuelta a empezar. Borrándole el destino, el cerebro lo vuelve a pedir (y el goal del granjero también) y la
        // ruta nueva SÍ cruza. Medido con el arnés: Isidoro (bancal 2) se quedaba toda la noche en `1394,119,1452`,
        // la celda de dentro de su compuerta, con la cama reclamada al otro lado y las cuatro compuertas cerradas.
        villager.getNavigation().stop();
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.PATH);
    }

    /** Abre el portón y lo registra. Devuelve {@code false} si el bloque ya no es un portón de valla. */
    private static boolean abrirPorton(ServerLevel level, BlockPos porton, BlockState estado) {
        if (!(estado.getBlock() instanceof FenceGateBlock)) {
            return false;
        }
        level.setBlock(porton, estado.setValue(FenceGateBlock.OPEN, true), Block.UPDATE_ALL);
        level.playSound(null, porton, SoundEvents.FENCE_GATE_OPEN, SoundSource.BLOCKS, 0.7F, 1.0F);
        ABIERTOS.computeIfAbsent(level, l -> new HashSet<>()).add(porton.asLong());
        return true;
    }

    /** Cierra el portón, pero <b>solo</b> si lo abrió el pueblo (si lo abrió el jugador, manda él). */
    private void cerrar(ServerLevel level, BlockPos porton, BlockState estado) {
        if (cerrarPorton(level, porton, estado)) {
            enfriamiento = ENFRIAMIENTO;
            abierto = 0;
            loAbriYo = false;
            ladoAlAbrir = 0;
        }
    }

    /** Cierra (si es del pueblo y sigue abierto) y lo saca de la lista. Devuelve {@code true} si lo ha cerrado. */
    private static boolean cerrarPorton(ServerLevel level, BlockPos porton, BlockState estado) {
        Set<Long> abiertos = ABIERTOS.get(level);
        if (abiertos == null || !abiertos.remove(porton.asLong())) {
            return false; // no es nuestro: se queda como está
        }
        olvidar(level, porton);
        if (!(estado.getBlock() instanceof FenceGateBlock) || !estado.getValue(FenceGateBlock.OPEN)) {
            return false; // ya estaba cerrado (lo cerró el jugador): no hay nada que hacer
        }
        level.setBlock(porton, estado.setValue(FenceGateBlock.OPEN, false), Block.UPDATE_ALL);
        level.playSound(null, porton, SoundEvents.FENCE_GATE_CLOSE, SoundSource.BLOCKS, 0.7F, 1.0F);
        return true;
    }

    /** Deja de contar como "abierto por el pueblo" (el jugador lo cerró a mano). */
    private static void olvidar(ServerLevel level, BlockPos porton) {
        Set<Long> abiertos = ABIERTOS.get(level);
        if (abiertos != null) {
            abiertos.remove(porton.asLong());
        }
        Map<Long, Long> desde = ABIERTO_DESDE.get(level);
        if (desde != null) {
            desde.remove(porton.asLong());
        }
        Map<Long, Long> usando = REBANO_USANDO.get(level);
        if (usando != null) {
            usando.remove(porton.asLong());
        }
    }

    /** ¿Está el portón despejado? (si hay un aldeano pegado, NO se cierra: podría estar cruzando). */
    private boolean nadieCerca(ServerLevel level, BlockPos porton) {
        return level.getEntitiesOfClass(Villager.class, new AABB(porton).inflate(CERRAR)).isEmpty();
    }

    /**
     * ¿Va este aldeano a <b>cruzar</b> el portón? Se mira su destino del <b>cerebro</b> ({@code WALK_TARGET}, que es
     * el que manda: invariante I5) y se compara el lado del portón en el que está él con el del destino. Si su
     * destino está del mismo lado que él, solo está <b>pasando por delante</b> (o trabajando al lado) y no hay nada
     * que abrir: un portón abierto "por si acaso" es por donde se escapa el rebaño.
     * <p>
     * <b>Y VA AL PORTÓN MISMO cuando su destino ES la celda del portón</b> (I96): a una puerta de valla solo se va
     * para cruzarla (la celda no es un sitio donde pararse), y hay caminos en los que el destino <b>final</b> cae del
     * <b>mismo lado del plano</b> de la puerta y el portón es un <b>rodeo obligado</b>: el almacén está al <b>este</b>
     * de la puerta <b>oeste</b> del corral, así que yendo del corral al almacén el aldeano sale por el oeste y vuelve
     * a rodear la cerca por el sur. Sin esta regla el portón <b>no se abría</b> y el aldeano se quedaba oscilando
     * pegado a la valla (medido con el arnés: `destino=511,63,646` / `destino=517,63,666` alternándose en
     * `512,63,647`, con el almacén a 19 bloques y sin cruzar nunca).
     */
    private boolean vaACruzar(BlockPos porton, BlockState estado) {
        WalkTarget objetivo = villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        if (objetivo == null) {
            return false; // sin destino no va a ningún sitio
        }
        BlockPos destino = objetivo.getTarget().currentBlockPosition();
        if (destino.equals(porton)) {
            return true; // va a la puerta: solo se va a una puerta para cruzarla
        }
        int mio = lado(estado, porton, villager.getX(), villager.getZ());
        int suyo = lado(estado, porton, destino.getX() + 0.5D, destino.getZ() + 0.5D);
        return suyo != 0 && suyo != mio;
    }

    /** ¿Ha pasado ya el aldeano al otro lado del portón que abrió él? (entonces se cierra en el acto). */
    private boolean haCruzado(BlockPos porton, BlockState estado) {
        if (ladoAlAbrir == 0) {
            return false; // no lo abrió él (o lo abrió con el cuerpo en el hueco): manda la regla de alejarse
        }
        int actual = lado(estado, porton, villager.getX(), villager.getZ());
        return actual != 0 && actual != ladoAlAbrir;
    }

    /**
     * De qué lado del plano del portón está un punto (XZ): {@code -1}, {@code 0} (en el hueco) o {@code +1}.
     * <p>
     * El eje por el que se cruza es el del {@code FACING}: en una puerta de valla el {@code FACING} mira al lado por
     * el que se entra (la del corral mira al <b>oeste</b> y se cruza de este a oeste; la del gallinero al <b>sur</b>;
     * las de los bancales, al norte o al este según el lado), y la valla va perpendicular. Se mide en <b>XZ</b>
     * (invariante I2): el portón es un plano vertical y la Y no dice nada.
     */
    private static int lado(BlockState estado, BlockPos porton, double x, double z) {
        boolean enX = estado.getValue(FenceGateBlock.FACING).getAxis() == Direction.Axis.X;
        double centro = (enX ? porton.getX() : porton.getZ()) + 0.5D;
        double distancia = (enX ? x : z) - centro;
        return distancia > 0.5D ? 1 : (distancia < -0.5D ? -1 : 0);
    }

    /** ¿Ese portón es <b>el del corralillo de las gallinas</b>? (su guardia y su espera son las suyas: ver I96). */
    private boolean esElDelGallinero(BlockPos porton) {
        return porton.equals(VillageGenerator.portonDelGallinero(center, porton.getY()));
    }

    /** La espera de "hay un animal en el hueco": corta en el gallinero (ahí vive el rebaño), larga en el corral. */
    private int esperaMaxima(BlockPos porton) {
        return esElDelGallinero(porton) ? ESPERA_PEN : ESPERA_MAXIMA;
    }

    /** ¿Hay un animal del <b>corral</b> (y de dentro, no uno que esté volviendo) en el hueco del portón? */
    private boolean animalEnElHueco(ServerLevel level, BlockPos porton) {
        // El portón del GALLINERO no es una salida del corral (va de la caseta al corral, de dentro adentro) y las
        // gallinas viven pegadas a él: ahí solo bloquea el que está EN LA PUERTA. En el del CORRAL sí se mira el
        // radio largo, que es el que impide que el rebaño se cuele fuera del recinto (ver ANIMAL_AL_PORTON).
        double radio = esElDelGallinero(porton) ? HUECO : ANIMAL_AL_PORTON;
        for (Animal animal : level.getEntitiesOfClass(Animal.class, new AABB(porton).inflate(radio))) {
            if (!VillageGenerator.especiesDelCorral().contains(animal.getType())) {
                continue;
            }
            if (VillageGenerator.enElCorral(center, animal)) {
                return true; // está DENTRO del corral y pegado al hueco: ése es el que se escapa
            }
        }
        return false;
    }

    /**
     * ¿Está el <b>rebaño</b> usando este portón ahora mismo? (marca fresca de {@link #abrirParaElRebano}). Entonces no
     * se le cierra: el animal del pueblo que vuelve a casa entra por ahí.
     */
    private static boolean rebanoUsando(ServerLevel level, BlockPos porton) {
        Map<Long, Long> usando = REBANO_USANDO.get(level);
        if (usando == null) {
            return false;
        }
        Long marca = usando.get(porton.asLong());
        return marca != null && level.getGameTime() - marca <= REBANO_FRESCO;
    }

    private double distancia(BlockPos porton) {
        return Math.sqrt(villager.distanceToSqr(porton.getX() + 0.5D, porton.getY() + 0.5D, porton.getZ() + 0.5D));
    }

    // --- lo que usa el rebaño y el latido ------------------------------------------------------------

    /**
     * Abre el portón para que <b>entre el rebaño</b> que vuelve a casa (lo llama {@code VuelveAlCorralGoal}, que lo
     * pide en cada tick mientras el animal está cerca). Queda apuntado como abierto por el pueblo y como
     * <b>usado por el rebaño</b> ({@link #REBANO_USANDO}), así que mientras el animal venga a casa nadie más se lo
     * cierra; al pasar, lo cierra el propio rebaño (o la red de seguridad del latido).
     */
    public static boolean abrirParaElRebano(ServerLevel level, BlockPos porton) {
        REBANO_USANDO.computeIfAbsent(level, l -> new HashMap<>()).put(porton.asLong(), level.getGameTime());
        BlockState estado = level.getBlockState(porton);
        if (!(estado.getBlock() instanceof FenceGateBlock) || estado.getValue(FenceGateBlock.OPEN)) {
            return false;
        }
        return abrirPorton(level, porton, estado);
    }

    /**
     * Cierra el portón que abrió el pueblo <b>después de que el rebaño haya pasado</b>: nunca con un aldeano en el
     * hueco (ni a punto de llegar, radio {@link #ABRIR}), ni con <b>otro</b> animal del corral cruzando (el que
     * acaba de entrar no se cuenta: {@code excepto}). Si no lo cierra ahora, lo cierra la red de seguridad del latido.
     */
    public static boolean cerrarParaElRebano(ServerLevel level, BlockPos porton, Animal excepto) {
        BlockState estado = level.getBlockState(porton);
        if (!(estado.getBlock() instanceof FenceGateBlock) || !estado.getValue(FenceGateBlock.OPEN)) {
            return false;
        }
        if (!level.getEntitiesOfClass(Villager.class, new AABB(porton).inflate(ABRIR)).isEmpty()) {
            return false; // un aldeano está cruzando (o le falta un paso)
        }
        for (Animal animal : level.getEntitiesOfClass(Animal.class, new AABB(porton).inflate(HUECO))) {
            if (animal != excepto && VillageGenerator.especiesDelCorral().contains(animal.getType())) {
                return false; // otro del rebaño está entrando detrás
            }
        }
        return cerrarPorton(level, porton, estado);
    }

    /**
     * <b>Red de seguridad de los portones del anexo</b> (lo pidió el jugador: el ganadero dejaba la puerta abierta y
     * los animales se salían). La llama el <b>latido</b> de la aldea.
     * <p>
     * Mira <b>solo</b> las dos casillas de los portones del anexo (el del corral y el del gallinero, calculadas como
     * en {@code asegurarCercaDelAnexo}): no recorre ningún mundo, así que es barato e idempotente. Un portón abierto
     * más de {@link #VIGILANCIA} (5 s) <b>sin ningún jugador cerca</b> se cierra. También se cierra en el acto si no
     * hay nadie en el hueco (ni aldeano ni animal del rebaño): no lo está usando nadie.
     * <p>
     * Es lo que tapa los dos casos que se le escapan al goal: que no haya ningún aldeano con el portón a la vista
     * (murió, se fue, o el chunk se descargó) y que el portón se quedara abierto en el guardado. Medido en el
     * guardado del jugador: el portón del corral de la aldea 2 estaba <b>abierto</b> ({@code open:true}) con la vaca
     * y la oveja del pueblo fuera, a 12 y 13 bloques del corral.
     *
     * @return cuántos portones ha cerrado
     */
    public static int vigilarPortonesDelAnexo(ServerLevel level, BlockPos center) {
        int nivel = VillageGenerator.cotaDeLaPlaza(level, center);
        if (nivel <= level.getMinBuildHeight() + 1) {
            return 0;
        }
        int cerrados = 0;
        Map<Long, Long> desde = ABIERTO_DESDE.computeIfAbsent(level, l -> new HashMap<>());
        for (BlockPos porton : List.of(VillageGenerator.portonDelCorral(center, nivel),
                VillageGenerator.portonDelGallinero(center, nivel))) {
            BlockState estado = level.getBlockState(porton);
            if (!(estado.getBlock() instanceof FenceGateBlock) || !estado.getValue(FenceGateBlock.OPEN)) {
                desde.remove(porton.asLong());
                olvidar(level, porton); // ya está cerrado: si lo cerró el jugador, deja de ser del pueblo
                continue;
            }
            if (jugadorCerca(level, porton)) {
                continue; // no se le cierra un portón a un jugador en las narices
            }
            if (rebanoUsando(level, porton)) {
                continue; // lo está usando el rebaño (un animal del pueblo vuelve a casa): lo cierra él
            }
            long marca = desde.computeIfAbsent(porton.asLong(), k -> level.getGameTime());
            // CON UN ALDEANO METIDO EN EL HUECO no se cierra NUNCA (ni por el plazo): cerrarlo encima de él lo deja
            // atrapado en el bloque del portón, empujando y girando sobre sí mismo (medido en el guardado del
            // jugador: la guardia espadachín del puesto 1, en `1455.62,120,1414.67` con el portón del corral
            // `open:false`, o sea con el portón cerrado y ella dentro). El plazo de 5 s sigue valiendo para el
            // aldeano que solo está AL LADO (trabajando junto al portón), que es para lo que se puso.
            if (alguienEnElHuecoDeVerdad(level, porton)) {
                continue;
            }
            boolean usado = alguienEnElHueco(level, porton);
            if (!usado || level.getGameTime() - marca >= VIGILANCIA) {
                if (cerrarPorton(level, porton, estado)) {
                    desde.remove(porton.asLong());
                    cerrados++;
                }
            }
        }
        if (cerrados > 0) {
            DevilRpg.LOGGER.info("[Village] Aldea en {}: {} porton(es) del anexo cerrados por la red de seguridad"
                    + " (llevaban abiertos mas de {} s sin nadie delante)", center, cerrados, VIGILANCIA / 20);
        }
        return cerrados;
    }

    /** ¿Hay un jugador en el portón (a {@link #JUGADOR_AL_PORTON} o menos)? (entonces el portón es cosa suya). */
    private static boolean jugadorCerca(ServerLevel level, BlockPos porton) {
        return !level.getEntitiesOfClass(Player.class, new AABB(porton).inflate(JUGADOR_AL_PORTON)).isEmpty();
    }

    /**
     * ¿Hay alguien cruzando el portón? Un aldeano cuenta con el radio de {@link #ABRIR} (el que va a cruzar está a
     * punto de llegar) y un animal del rebaño con el del hueco. <b>Ojo</b>: el puesto del ganadero está a 3 bloques
     * del portón del corral, así que un aldeano a esa distancia <b>no</b> basta para dejarlo abierto: para eso está
     * el plazo {@link #VIGILANCIA} (y el {@link #MAXIMO_ABIERTO} del propio goal).
     */
    private static boolean alguienEnElHueco(ServerLevel level, BlockPos porton) {
        if (!level.getEntitiesOfClass(Villager.class, new AABB(porton).inflate(ABRIR)).isEmpty()) {
            return true;
        }
        for (Animal animal : level.getEntitiesOfClass(Animal.class, new AABB(porton).inflate(HUECO))) {
            if (VillageGenerator.especiesDelCorral().contains(animal.getType())
                    && !animal.isPassenger() && !animal.isVehicle()) {
                return true;
            }
        }
        return false;
    }

    /**
     * ¿Hay un aldeano <b>dentro del hueco</b> del portón (a {@link #HUECO}, no a {@link #ABRIR})? Con uno ahí no se
     * cierra por plazo: cerrarlo encima lo deja <b>atrapado en el bloque</b> del portón (medido en el guardado del
     * jugador: la guardia del puesto 1 estaba en el hueco con el portón ya cerrado). Un aldeano a {@code ABRIR} solo
     * está <b>al lado</b> (trabajando junto al portón), y ése sí tiene que dejar que se cierre a los 5 s.
     */
    private static boolean alguienEnElHuecoDeVerdad(ServerLevel level, BlockPos porton) {
        return !level.getEntitiesOfClass(Villager.class, new AABB(porton).inflate(HUECO)).isEmpty();
    }
}
