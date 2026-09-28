package com.chipoodle.devilrpg.debug;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.entity.goal.VillagerGuardGoal;
import com.chipoodle.devilrpg.survival.ObjectiveTargets;
import com.chipoodle.devilrpg.world.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Locale;
import java.util.Random;

/**
 * ARNES TEMPORAL DE DIAGNOSTICO (no se queda en el mod).
 * <p>
 * Arranca un servidor headless con la partida del jugador copiada en {@code run/world}, fuerza los chunks de la aldea
 * 2, mete un jugador de pega en la plaza (el latido del pueblo necesita jugador cerca) y deja correr el latido de
 * verdad ({@code VillageManager.manageNearby}: reparte oficios, alista a la guardia y le pone sus goals), volcando en
 * el log lo que hace cada guardia.
 */
@EventBusSubscriber(modid = DevilRpg.MODID, bus = EventBusSubscriber.Bus.GAME)
public class GuardHarness {

    private static final BlockPos CENTRO = new BlockPos(470, 63, 646);
    private static final int INDICE = 0;
    /**
     * ¿Se siembra el almacén con <b>pociones de agua ya embotelladas</b>? Para medir el <b>VIAJE AL AGUA</b> del
     * clérigo tiene que estar en {@code false}: si el almacén ya tiene botellas de agua, las usa y <b>nunca</b> coge
     * las de cristal (ver {@code VillagerClericGoal.trabajar}, paso 4), así que el viaje a la orilla no se mide.
     * En {@code true} se mide la cadena de la poción sin el paseo (era como estaba antes de esta ronda).
     */
    private static final boolean SEMBRAR_AGUA_EMBOTELLADA = false;
    /**
     * ¿Se mide el <b>SUEÑO Y LAS CAMAS</b> (noche fija) o el <b>TRABAJO Y EL COMBUSTIBLE</b> (día fijo)? En noche, el
     * arnés pasa el VIGILANTE DE CAMAS cada segundo y se salta las siembras de trabajo (que ensucian el log y mueven
     * al pueblo de sitio). Ver `volcarCamas`.
     */
    private static final boolean MEDIR_NOCHE = false;
    /**
     * ¿Se mide el <b>CIERRE DE PUERTAS</b>? Pone el mundo de <b>día</b> (los aldeanos se levantan y salen: cruzan
     * puertas), se salta las siembras y volca cada 2 s las <b>puertas de madera abiertas</b> del pueblo. Es lo que
     * pide el jugador: *"los aldeanos cuando vayan a dormir tienen que cerrar la puerta porque todas la dejan
     * abierta"*.
     */
    private static final boolean MEDIR_PUERTAS = false;
    /**
     * <b>¿Se mete un bicho DENTRO de la aldea y se deja ahí?</b> Es la reproducción de la queja del jugador
     * (*"Mauricio sigue sin ir a buscar cama y hay varias en la taberna"*): con un monstruo dentro del recinto,
     * `manageNearby` corta el latido entero (`hayEnemigosDentro`), y con él se quedaba sin hacer TODO lo que va
     * detrás, camas incluidas. Se usa un <b>aldeano-zombi</b> a propósito: es un {@code Monster} (cuenta para
     * `hayEnemigosDentro`) pero el sello lo deja en paz —`expulsarHostilesDeLaAldea` no lo toca, para no cortar una
     * curación en marcha—, así que el "bicho dentro" se mantiene toda la corrida sin que lo expulsen en el primer
     * latido. Va con `NoAI` (no pelea ni anda) e invulnerable.
     */
    private static final boolean BICHO_DENTRO = false;
    /**
     * ¿Se mide la <b>COCINA</b> (lo reportó el jugador: *"el cocinero está cocinando FUERA de la taberna, esto no
     * debe ser así, debe estar adentro"*)? Pone el mundo de <b>día</b> (de noche el cocinero se acuesta), viste el
     * almacén con <b>leña</b> y la despensa con <b>carne cruda</b> —que es lo que hace que el cocinero trabaje— y
     * volca cada 2 s dónde está, a qué distancia del ahumador, si lo <b>VE</b> (rayo de colisión) y qué ruta tiene a su
     * casilla de la cocina. Es lo que distingue "cocina dentro" de "cocina a través de la pared".
     */
    private static final boolean MEDIR_COCINA = false;
    /**
     * <b>¿Se le siembran 4 troncos en el zurrón al cocinero?</b> Estaba en `true` para poder medir el horno del pan
     * cuando la ida al almacén estaba cortada (ver {@code sembrarLenaAlCocinero}). Desde la <b>migración 72</b> —la
     * puerta de la taberna al almacén— el cocinero <b>tiene que ir él</b>, así que esto va en {@code false}: lo que se
     * mide es la cadena entera (va por leña → cocina → hornea).
     */
    private static final boolean SEMBRAR_LENA_AL_COCINERO = false;

    /** ¿Se mide LA HUERTA (lo que hay tirado en los bancales y el zurron de cada granjero)? Ver igilarLaHuerta. */
    private static final boolean MEDIR_HUERTA = false;
    /**
     * ¿Se mide <b>LA MILICIA Y EL SANADOR</b> (I62/I64)? Deja el mundo de DÍA, suelta <b>dos zombis flojos</b> dentro
     * de la aldea (para que la guardia pelee, mate y suba de nivel) y <b>hiere</b> a tres guardias al 35 % de su vida
     * (para que el clérigo tenga a quién curar). Cada segundo vuelca la vida, el NIVEL y las MATANZAS de cada guardia
     * y lo que está haciendo el clérigo.
     */
    private static final boolean MEDIR_MILICIA = false;
    /** ¿Se mide LA CASA DEL HUECO (la pared a la que le faltaba un bloque y el cofre de al lado)? Ver olcarLaParedYElCofre. */
    private static final boolean MEDIR_HUECO_CASA = false;
    /**
     * ¿Se miden LAS ALDEAS CON NOMBRE, EL REVELADO Y EL DIARIO DEL INVOCADO (I87)? Es un modo de <b>solo
     * lectura</b> (no siembra, no barre, no cambia la hora): deja correr el latido con el jugador de pega y vuelca
     * cada 10 s qué aldeas tiene <b>descubiertas</b> (`aldeasVisitadas`) y <b>reveladas</b> (`aldeasReveladas`), el
     * nombre y el estado de cada una, qué dibujaría la <b>barra de aldea</b>, el <b>rumbo</b> a la siguiente y las
     * <b>líneas del Diario</b> tal cual las leería el jugador.
     * <p>
     * Lo que se busca: (1) que un guardado <b>viejo</b> (sin los campos nuevos en la capability) cargue sin
     * reventar; (2) que la <b>siembra</b> apunte las aldeas que esa partida ya resolvió (`[Village] Diario del
     * Invocado sembrado para …`); (3) que la barra quede <b>OCULTA</b> mientras no haya revelado ni visitado nada; y
     * (4) que el Diario liste esas aldeas con nombre, coordenadas, estado y rumbo.
     */
    private static final boolean MEDIR_ALDEAS = false;
    /**
     * ¿Se mide EL ASEDIO CLÁSICO DE PRINCIPIO A FIN, para ver al <b>clérigo revelando la siguiente al vencer</b>
     * (I87)? Es la única parte del revelado que no se puede medir por método suelto: hace falta un asedio de verdad.
     * <p>
     * Cómo lo monta: a los 15 s arranca el asedio del objetivo <b>3</b> (una aldea que NO existe todavía: se genera
     * ahí mismo, así que su asedio está sin resolver — las otras tres de esa partida ya lo están) y deja al jugador
     * de pega <b>dentro</b> de esa aldea (si no, el reloj se queda EN PAUSA, I86). Cuando la ola sale (90 s de
     * margen), se la deja pelear 10 s y <b>se limpia desde el arnés</b>: la milicia recién nacida de una aldea a
     * ~2.000 bloques no puede con una ola escalada, y lo que se mide aquí es la <b>resolución</b> (salvada), no el
     * combate.
     * <p>
     * Lo que se busca en el log: `[Village] Aldea 3 salvada: revelada la aldea 4 a … (hacia el …)` y, en el volcado
     * del arnés, `revelada(4)=true` con la barra en `Aldea  (… m)`.
     * <p>
     * <b>MEDIDO (22-sep-2026): con un jugador de pega esto NO llega a la resolución, y se sabe por qué.</b> El reloj
     * del asedio solo corre con el jugador del asedio <b>en la lista del servidor</b> (I86), y un {@code FakePlayer}
     * <b>no está en ella</b>: `distanciaAlCentro` devuelve {@code MAX_VALUE}, el asedio queda EN PAUSA y la ola nunca
     * sale. Las dos corridas lo enseñan: `hayAsedio(3)=true` toda la corrida, `agresivos=0` siempre y
     * `revelada(4)=false`. O sea: este modo <b>sí</b> mide que el asedio existe, que el estado de la aldea es
     * `en asedio` (y que el Diario lo enseña así) y que <b>sin jugador de verdad el reloj no corre</b>; para ver al
     * clérigo revelar al vencer hace falta <b>jugar el asedio</b> (o un cliente conectado).
     * <p>
     * De paso, la primera corrida destapó un problema del <b>montaje</b>: el jugador de pega tampoco carga chunks, así
     * que la aldea 3 se descargaba (`aldeanos3=11` y diez segundos después `0`). Se fuerzan los chunks de esa aldea al
     * arrancar el asedio y con eso los 11 aldeanos se mantienen.
     */
    private static final boolean MEDIR_ASEDIO_VIVO = false;
    /**
     * ¿Se mide EL ASALTO A LA MURALLA (I89)? Pone <b>un asaltante de verdad</b> fuera de la muralla (radio 66, con el
     * centro de la aldea y SIN objetivo: así corre la <b>marcha</b>, que es la que taladra) y va volcando la línea de
     * bloques entre él y la muralla (radios 66..56) para ver si <b>abre brecha</b>.
     * <p>
     * Lo que se busca en el log: `[Siege] un zombie empieza a TALADRAR hacia la aldea en … (presupuesto 40 bloques)`
     * y que en la línea aparezca <b>aire</b> donde había muralla. Y de paso que <b>no</b> toque nada de dentro.
     */
    private static final boolean MEDIR_MURO = false;
    /**
     * ¿Se mide LA REPARACIÓN DE UN AGUJERO DEL SUELO (el cráter de un creeper, que NO está en el plano)? Abre un
     * cráter de 3x3x2 en el suelo de la aldea y vuelca sus dos capas con letras ('.'=aire, 'G'=hierba, 'D'=tierra,
     * 'P'=camino) para ver si el CONSTRUCTOR lo va tapando capa a capa.
     * <p>
     * Lo pidió el jugador: *"hay un hoyo que dejó un creeper durante el asedio, ¿por qué nadie lo está reparando? Ahí
     * está Leoncio el recolector, él debería de ser también constructor"*.
     */
    private static final boolean MEDIR_AGUJERO = false;
    /**
     * ¿Se mide <b>EL LEÑADOR</b> (lo pidió el jugador: *"todavía el leñador quiere ir afuera de la aldea. Si el bosque
     * dentro de la aldea no tiene todavía árboles que vaya al almacén por polvo de hueso a fertilizar el árbol. El ir
     * afuera es el último de los recursos"*)?
     * <p>
     * Se corre sobre <b>su aldea de verdad</b> (aldea 0, centro 470/646, cota 63, con su arboleda: 2 árboles y 10
     * plantones) y volca cada 10 s: el estado de la arboleda, los huesos y la harina de la despensa, y dónde está y a
     * dónde camina <b>el leñador</b> (flechero) y los granjeros, diciendo si el destino cae <b>dentro</b> de la valla o
     * fuera.
     * <p>
     * A los 10 s se le siembra al pueblo lo que en su partida <b>no</b> hay (8 huesos y 16 de harina de huesos): así se
     * mide (1) que los primeros volcados, <b>sin</b> harina, el leñador se queda <b>dentro</b>, y (2) que con harina va
     * a por ella ("Yendo por polvo de hueso") y abona los plantones, y (3) que el <b>granjero muele</b> los huesos
     * ("Hizo N polvo de hueso").
     */
    private static final boolean MEDIR_LENADOR = false;
    /**
     * <b>LAS HORDAS DEL MUNDO</b> (I98): mide que la <b>presión del abandono</b> de una aldea se acumule <b>sola</b>
     * (en el reloj del mundo, cada 10 s, desde el latido) y que el mundo mande una horda <b>a la aldea</b> en el
     * <b>PRIMER</b> turno del roll: antes hacía falta un <b>segundo</b> roll (20-40 min después, porque el primero
     * solo fijaba la base de la presión) y el contador del roll vivía en memoria, así que cerrar el juego lo ponía a
     * cero. Medido en la partida del jugador: **ni una** línea de horda hacia una aldea en dos días de logs y
     * {@code Pressure} vacío en el guardado.
     * <p>
     * <b>El montaje</b> (corre en la <b>aldea 0</b>, centro {@code 470,646}, cota 63): hay que copiar el mundo con
     * {@code level.dat -> Data.Time} puesto a un valor que haga que el <b>turno</b> del roll
     * ({@code gameTime / intervalo}) cambie unos <b>16 s</b> después de arrancar — con el reloj a <b>21629</b> y el
     * intervalo de ~21957 ticks cambia en ~330 ticks — y el modo siembra la presión de la aldea 0 a <b>8 min
     * exactos</b> a los 20 ticks ({@code accruePressure(0, gameTime - 9600)}), así que la aldea ya pasa el umbral y
     * además se ve cómo la presión <b>sigue subiendo</b> con los latidos.
     * <p>
     * <b>Lo que se busca</b>: la presión subiendo sola (9600 → 9800 → 10000…), `[Horda] la aldea 0 lleva 8 min sin
     * socorro: elegida como objetivo`, `[Horda] N de M enemigos van a por la aldea 0` y `[Horda] la aldea 0 esta
     * siendo atacada`, con los asediadores contados en el volcado y el estado de la aldea en `en asedio`.
     */
    private static final boolean MEDIR_HORDAS = false;
    /** Ticks de presión que hacen falta para que el mundo mande una horda ({@code PRESSURE_MIN_TICKS} = 8 min). */
    private static final long PRESION_MINIMA = 8L * 60L * 20L;
    /**
     * <b>EL ALMACÉN Y LOS HUEVOS DEL GALLINERO</b> (I95/I96/I97): mide las dos cosas que reportó el jugador —
     * *"el punto de apoyo del almacén (517,64,666) es inalcanzable"* y *"el ganadero no coge los huevos del
     * gallinero"*— sobre su aldea (aldea 0, centro {@code 470,646}, cota 63).
     * <p>
     * Cada 2 s vuelca: el punto de apoyo que devuelve el mod y <b>la capa del suelo del cobertizo</b> (¿está en
     * {@code cota - 1}?); <b>la ruta de un aldeano</b> hasta ese punto (con {@code canReach} y dónde acaba, que es la
     * prueba del caminante del juego); lo que tienen dentro los cofres del almacén (para ver que la migración 69
     * <b>no pierde nada</b>); el <b>ganadero</b> (posición, distancia al almacén, etiqueta, goals activos, zurrón y
     * su punto aparcado); los <b>huevos</b> que hay en el suelo del corral con su edad; y el <b>portón del
     * gallinero</b> con cuántas gallinas tiene pegadas.
     * <p>
     * Y a los 10 s (y luego cada 20 s) siembra <b>dos huevos</b> en el gallinero: uno en el SUELO del corralillo
     * ({@code 513,638}, al que solo se llega ENTRANDO) y otro <b>encima de la paja</b> ({@code 516,639}, cuya celda
     * está a {@code cota + 1}).
     * <p>
     * <b>OJO con el instrumento</b>: el recuento de huevos va con la caja <b>alrededor de la base del corral</b>; con
     * {@code AABB(CENTRO).inflate(40)} el corral cae <b>fuera</b> y el contador decía "0 huevos" siempre.
     */
    private static final boolean MEDIR_ALMACEN_Y_HUEVOS = false;
    /**
     * <b>`MEDIR_MINERO = true` — LA MINA DEL MINERO</b> (etapa I, invariante I102).
     * <p>
     * Deja el mundo de <b>día</b> (de noche el minero descansa como todos, {@code estaDescansando}), barre los bichos
     * (uno dentro del recinto corta el latido del pueblo entero) y a los 10 s se asegura de que hay un <b>MINERO</b>:
     * si no hay ninguno con el oficio {@code MASON}, lo planta el arnés con la puerta del propio mod
     * ({@code VillageGenerator.spawnOneVillager(level, CENTRO, 11, false)}: el sitio 11 es el albañil), para no
     * depender de que el reparto de puestos le toque en la corrida.
     * <p>
     * Cada 2 s vuelca el <b>avance real de la mina</b> —celdas del caracol hechas y su Y, medido en el mundo con
     * {@code progresoDeLaMina}, más el tope de piedra labrada— y lo que hace el minero (posición, destino, zurrón,
     * goals, etiqueta y el desgaste de su pico), y cada 10 s el <b>almacén</b> (adoquín, carbón, lingotes, pedernal
     * y picos). Lo que se busca: que el caracol <b>suba de paso</b> con el tiempo, que salgan <b>lingotes</b> y
     * <b>pedernal</b> al almacén, y que el minero no se quede plantado con la etiqueta "Paving" o "Sin destino".
     * <p>
     * <b>Y SE VUELCA EL POZO ENTERO</b> ({@code POZO}): de cada paso del caracol, su <b>pieza</b> y sus <b>dos celdas
     * de paso</b> (pies y cabeza), con {@code *} en la que esté TAPADA. Lo pidió el jugador para el caso *"el minero no
     * está bajando y está sellada la entrada"*: la boca sola no lo dice —la boca está ABIERTA— y lo que corta el paso
     * está <b>más abajo, en el propio caracol</b>. Y con la <b>ruta viva</b> ({@code nav=[…]}) y la <b>ruta a su
     * faena</b> ({@code rutaFaena=[…]}) del minero en la misma línea se distingue "no hay camino hasta su celda" (el
     * pozo está cortado) de "hay camino y no va".
     */
    private static final boolean MEDIR_MINERO = false;
    /**
     * <b>MEDIR_PEPITAS = true</b> — LA CADENA DEL HIERRO DE LOS RAIDS (26-sep-2026). Lo pidio el jugador: *"los
     * guardias, cuando maten zombis que vengan de algun raid del mundo, conseguiran hierro"*. Mide los cuatro
     * eslabones: (1) que el zombi de raid SUELTE pepitas de hierro (1-2 por `dropCustomDeathLoot`), (2) que alguien
     * las LEVANTE, (3) que lleguen al ALMACEN y (4) que el HERRERO las gaste en un pico de hierro (27 pepitas).
     */
    private static final boolean MEDIR_PEPITAS = false;
    /** Dónde se planta el bicho (relativo a la plaza): dentro del recinto (radio 62) y a la altura del pueblo. */
    private static final BlockPos BICHO_EN = new BlockPos(6, 0, 6);
    private static boolean listo = false;
    private static int ticks = 0;
    /** Cuantas veces se ha visto a un granjero SUBIDO a la valla de su bancal (el bug que se mide). */
    private static int subidasALaValla = 0;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ServerLevel level = event.getServer().overworld();
        FakePlayer pega;
        if (!listo) {
            listo = true;
            pega = preparar(level, event.getServer());
        } else {
            pega = FakePlayerFactory.getMinecraft(level);
        }
        ticks++;
        // A los 15 s (chunks ya cargados) se le deja al almacen lo que el pueblo NO puede fabricar, para medir la
        // cadena del CLERIGO: verruga del Nether, polvo de blaze y BOTELLAS DE CRISTAL (para que tenga que ir al agua
        // a llenarlas: ver SEMBRAR_AGUA_EMBOTELLADA); y el botin que ya barre el recolector (pepitas de oro,
        // zanahorias, ojos de arana) para la zanahoria dorada.
        // OJO: en esta pasada se VACIA EL ALMACEN entero, asi que los modos que SIEMBRAN el almacen tienen que quedar
// fuera. MEDIR_EQUIPO no estaba y su medida salio inconclusa por esto: sembro el almacen a los 10 s y a los 30 s
// (t=600) este bloque lo vacio, asi que el equipo desaparecio antes de que ningun guardia llegara a verlo.
if (!MEDIR_NOCHE && !MEDIR_PUERTAS && !MEDIR_COCINA && !MEDIR_ALDEAS && !MEDIR_EQUIPO && !MEDIR_LENADOR
                && !MEDIR_HORDAS && !MEDIR_ALMACEN_Y_HUEVOS && !MEDIR_MINERO && ticks == 600) {
            // --- TERCERA MEDIDA: LA REMESA INICIAL DE MADERA ---------------------------------------------------
            // Se VACIA el almacen entero (como el de una aldea recien fundada, que nace sin nada dentro): en la
            // siguiente pasada del latido el pueblo tiene que meter su remesa inicial de 128 troncos, UNA vez.
            var caja = com.chipoodle.devilrpg.world.VillageStorage.almacen(level, CENTRO);
            int sacados = 0;
            if (caja != null) {
                for (int i = 0; i < caja.getContainerSize(); i++) {
                    if (!caja.getItem(i).isEmpty()) {
                        sacados++;
                    }
                    caja.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
                }
                caja.setChanged();
            }
            DevilRpg.LOGGER.info("[Arnes] REMESA: almacen vaciado ({} pila(s) fuera, {} troncos antes): en la"
                    + " siguiente pasada del latido tiene que entrar la remesa inicial",
                    sacados, com.chipoodle.devilrpg.world.VillageStorage.cuentaLena(level, CENTRO));
        }
        // Los bichos que YA venian en el guardado dentro del recinto BLOQUEAN el latido del pueblo
        // (`hayEnemigosDentro`): sin esto el reparto de oficios y la guardia ni se tocan. Se barren cada segundo.
        // OJO: en la medida de LA MILICIA **no se barre**, porque los bichos que hay dentro son los que se acaban de
        // sembrar para que la guardia pelee (medido: con el barrido, el zombi desaparecia en el mismo segundo, la
        // guardia se quedaba con la etiqueta "Atacando" un instante y volvia a su ronda, y no habia ni una muerte).
        if (ticks % 20 == 0 && !MEDIR_MILICIA && !MEDIR_MURO && !MEDIR_HORDAS && !MEDIR_PEPITAS) {
            if (BICHO_DENTRO) {
                // ...pero para medir EL BUG DEL LATIDO CORTADO hay que dejar UNO dentro a proposito.
                mantenerBichoDentro(level);
            } else {
                for (net.minecraft.world.entity.Mob m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                        new AABB(CENTRO).inflate(140))) {
                    if (m instanceof net.minecraft.world.entity.monster.Monster) {
                        m.discard();
                    }
                }
            }
        }
        // SIN MUERTES POR VEJEZ EN LA MEDIDA: el mod le pone a cada aldeano su fecha de nacimiento
        // (`DevilRpgVillagerBorn`) y a los 3 dias de juego (una hora de servidor) muere de viejo. En una corrida
        // larga eso repuebla la aldea a mitad de la medida (UUID nuevos y aldeanos sin cama recien llegados), asi
        // que aqui se les REJUVENECE: la medida del sueno no se ensucia con el relevo generacional.
        if (ticks % 200 == 0) {
            for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(140))) {
                v.getPersistentData().putLong("DevilRpgVillagerBorn", level.getGameTime());
            }
        }
        // El latido de la aldea, tal cual lo llama el tick del jugador (con el ancla del objetivo 2).
        if (pega != null) {
            pega.moveTo(CENTRO.getX() + 0.5D, CENTRO.getY(), CENTRO.getZ() + 0.5D);
            VillageManager.manageNearby(level, pega, ancla(), INDICE);
        }
        if (MEDIR_ALDEAS) {
            // LAS ALDEAS CON NOMBRE Y EL DIARIO (I87): solo lectura, cada 10 s.
            if (ticks % 200 == 0) {
                volcarAldeas(level, pega);
            }
        }
        if (MEDIR_PUERTAS) {
            // LAS PUERTAS, cada 2 s: los aldeanos estan de dia (se levantan y salen) y cruzan puertas: se cuenta
            // cuantas de madera quedan ABIERTAS en el pueblo. Lo que se busca es que BAJE (las cierran al pasar).
            if (ticks % 40 == 0) {
                volcarPuertas(level);
            }
        } else if (MEDIR_ASEDIO_VIVO) {
            medirElAsedioVivo(level, pega);
        } else if (MEDIR_MURO) {
            medirElAsaltoAlMuro(level, pega);
        } else if (MEDIR_AGUJERO) {
            medirElAgujeroDelSuelo(level, pega);
        } else if (MEDIR_LENADOR) {
            medirElLenador(level, pega);
        } else if (MEDIR_HORDAS) {
            medirLasHordas(level, pega);
        } else if (MEDIR_ALMACEN_Y_HUEVOS) {
            medirElAlmacenYLosHuevos(level);
        } else if (MEDIR_PEPITAS) {
            medirLasPepitas(level, ticks);
        } else if (MEDIR_MINERO) {
            medirElMinero(level);
        } else if (MEDIR_EQUIPO) {
            medirElEquipoDeLaGuardia(level, pega);
        } else if (MEDIR_COCINA) {
            // LA COCINA, cada 2 s: donde esta el cocinero, si VE el ahumador y si tiene ruta a su casilla.
            if (ticks == 400) {
                sembrarLaCocina(level);
            }
            if (ticks % 40 == 0) {
                vigilarCocinero(level);
            }
        } else if (MEDIR_NOCHE) {
            // EL VIGILANTE DE CAMAS, cada segundo (la transicion se canta sola cuando el HOME desaparece o se reclama).
            if (ticks % 20 == 0) {
                volcarCamas(level);
            }
            // Y A RESOLUCION DE TICK: al perderse la cama se imprime como estaba EN EL TICK ANTERIOR, que es lo que
            // dice quien la borro (ver `vigilarCamasCadaTick`).
            vigilarCamasCadaTick(level);
            // Y EL QUE ESTA DENTRO DE UN BANCAL: su destino, sus goals activos y el estado de las compuertas.
            if (ticks % 40 == 0) {
                vigilarGranjerosEnElBancal(level);
            }
            // Y EL QUE NO TIENE CAMA: sondeo de rutas a las celdas que importan (donde se corta el camino).
            if (ticks % 100 == 0) {
                sondarRutasDelSinCama(level);
            }
        } else if (MEDIR_MILICIA) {
            // LA MILICIA Y EL SANADOR (I62/I64): a los 60 s (chunks cargados, oficios repartidos y guardia alistada:
            // a los 30 s TODAVIA no hay guardias y la siembra se quedaba sin heridos, medido) se siembra la pelea, y a
            // partir de ahí se vuelca cada segundo quién pelea, quién sube de nivel y a quién cura el clérigo.
            if (ticks == 3000) {
                sembrarLaMilicia(level);
            }
            if (ticks == 3040) {
                golpearConLaGuardia(level); // 2 s despues: las entidades recien anadidas ya estan en el nivel
            }
            if (ticks % 20 == 0) {
                volcarLaMilicia(level);
            }
        } else {
            if (ticks == 400 || ticks == 1400) {
                volcarObjetos(level);
                volcarCamas(level);
            }
            if (ticks % 20 == 0) {
                volcar(level);
            }
            if (ticks % 40 == 0) {
                volcarCombustible(level);
            }
            // LA HUERTA (lo reporto el jugador: "los granjeros estan dejando muchos vegetales en el suelo cuando
            // cosechan"): lo que hay TIRADO en cada bancal y el zurron de cada granjero (con sus huecos libres).
            if (MEDIR_HUERTA && ticks % 40 == 0) {
                vigilarLaHuerta(level);
                // Y EL QUE NO SE MUEVE DE SU COLUMNA: el "atrapado en casa" que mide `rescatarAldeanosAtrapados`.
                vigilarAtrapados(level);
            }
            // Y AL COCINERO, LENA EN EL ZURRON (cada 30 s si se ha quedado sin ella): la pierna de la lena esta rota
            // EN ESTA ALDEA —medido, ver la 5.a corrida de `medidas-huerta.txt`: el punto de apoyo del almacen no
            // tiene ruta desde la taberna (`rutaAlmacen=a1=15n alcance=NO fin=511,63,666 dFin=6.00`)— y sin esta
            // siembra no se puede medir LO QUE SE QUIERE MEDIR AQUI: **que el pan lo hornea el cocinero**.
            if (MEDIR_HUERTA && ticks >= 600 && ticks % 600 == 0) {
                if (SEMBRAR_LENA_AL_COCINERO) {
                    sembrarLenaAlCocinero(level);
                }
                // Y TRIGO EN LA DESPENSA, por lo mismo: con la reserva de cria por delante (3 de trigo por hogaza + 4
                // de reserva = 7), en una corrida de minutos el trigo de la huerta no llega al umbral y el horno no se
                // llega a medir NUNCA. Se repone para medir **el horno del pan**, no el ritmo de la huerta.
                sembrarTrigoEnLaDespensa(level);
            }
        }
        // LA CASA DEL HUECO (el reporte del jugador con captura): las dos celdas de la pared que le faltaban, sus
        // vecinas y el COFRE de al lado con lo que tiene dentro. Cada 2 s, para ver el antes (aire) y el después
        // (adoquín) en la MISMA corrida y comprobar que el cofre no se toca.
        if (MEDIR_HUECO_CASA && ticks % 40 == 0) {
            volcarLaParedYElCofre(level);
        }
        // EL PORCHE DE LA TABERNA (migracion 63): se mide la columna `bx-1`, la que queda ENTRE el toldo (bx-2) y la
        // pared de la taberna (bx). A los 10 s el latido ya migro la aldea, asi que esto es "despues".
        if (ticks == 200) {
            volcarPorche(level, "DESPUES");
        }
    }

    /**
     * <b>LA MINA DEL MINERO</b> (etapa I, I102): ver el bloque de {@code MEDIR_MINERO} arriba para el montaje.
     */
    /**
     * <b>Vacia el carbon (y el carbon vegetal) del almacen</b>: asi el minero TIENE que fabricarselo quemando un
     * tronco en el horno (1 tronco -> 1 carbon vegetal, la receta de vanilla) para poder hacer antorchas. Es lo que se
     * mide con esta siembra (ver {@code medirElMinero}).
     */
    private static void vaciarElCarbonDelAlmacen(ServerLevel level) {
        net.minecraft.world.Container almacen = com.chipoodle.devilrpg.world.VillageStorage.almacen(level, CENTRO);
        if (almacen == null) {
            return;
        }
        int fuera = 0;
        for (int i = 0; i < almacen.getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack s = almacen.getItem(i);
            if (s.is(net.minecraft.world.item.Items.COAL) || s.is(net.minecraft.world.item.Items.CHARCOAL)) {
                fuera += s.getCount();
                almacen.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
            }
        }
        DevilRpg.LOGGER.info("[Arnes] CARBON: {} unidad(es) fuera del almacen (para medir el carbon vegetal del"
                + " minero)", fuera);
    }

    /**
     * <b>LA CADENA DEL HIERRO DE LOS RAIDS</b>, eslabon a eslabon (ver {@link #MEDIR_PEPITAS}). Cada 2.000 ticks:
     * planta un <b>zombi de raid</b> ({@code AggressiveZombieEntity}, el mismo que trae el asedio) junto a la plaza,
     * lo mata <b>atribuido a la guardia</b> (como `MEDIR_MILICIA`, para que cuente como matanza suya) y a los 40
     * ticks cuenta: pepitas EN EL SUELO, pepitas que quedan 200 ticks despues (¿las levanto alguien?), pepitas en el
     * ALMACEN y picos del almacen. Asi se ve donde se rompe la cadena.
     */
    private static void medirLasPepitas(ServerLevel level, long ticks) {
        level.setDayTime(6000L);

        if (ticks % 400 == 20) {
            BlockPos donde = new BlockPos(CENTRO.getX() + 6, com.chipoodle.devilrpg.world.VillageGenerator.spawnY(
                    level, CENTRO.getX() + 6, CENTRO.getZ() + 6), CENTRO.getZ() + 6);
            com.chipoodle.devilrpg.entity.AggressiveZombieEntity z =
                    com.chipoodle.devilrpg.init.ModEntities.AGGRESSIVE_ZOMBIE.get().create(level);
            if (z == null) {
                return;
            }
            z.moveTo(donde.getX() + 0.5D, donde.getY(), donde.getZ() + 0.5D, 0.0F, 0.0F);
            z.setVillageCenter(new BlockPos(CENTRO.getX(), donde.getY(), CENTRO.getZ()));
            level.addFreshEntity(z);
            pepitasPlantadas = z;
            DevilRpg.LOGGER.info("[Arnes] PEPITAS t={} planto un zombi de raid en {}", ticks, donde.toShortString());
            return;
        }
        if (ticks % 400 == 60 && pepitasPlantadas != null && pepitasPlantadas.isAlive()) {
            // LO MATA LA GUARDIA (atribuido, como en MEDIR_MILICIA): el botin de `dropCustomDeathLoot` sale igual,
            // pero asi la muerte cuenta como suya, que es lo que pidio el jugador.
            Villager guardia = aldeanoMasCercano(level, pepitasPlantadas.blockPosition());
            pepitasQueLlevabaElAsesino = guardia == null ? -1 : pepitasEnElZurron(guardia);
            pepitasAsesino = guardia;
            pepitasPlantadas.hurt(guardia != null ? level.damageSources().mobAttack(guardia)
                    : level.damageSources().generic(), 1000.0F);
            return;
        }
        if (ticks % 400 == 100) {
            // EL ESLABON (1) Y (2): el zombi suelta y ALGUIEN lo levanta. OJO: el mod NO tira las pepitas al suelo
            // cuando las mata un aldeano del pueblo —las mete en el ZURRON DEL QUE MATA (`dropCustomDeathLoot`:
            // "el que mata, lootea")—, asi que medir "en el suelo" daba 0 SIEMPRE y parecia que no soltaba nada
            // (medido en las cuatro corridas de `medida-pepitas*.log`: `EN EL SUELO: 0` en todas). Lo que hay que
            // mirar es el ZURRON del asesino.
            DevilRpg.LOGGER.info("[Arnes] PEPITAS t={} TRAS LA MUERTE: en el suelo={} · el que mato ({}{}) lleva {}"
                            + " pepitas (llevaba {} antes){}{}", ticks, pepitasEnElSuelo(level),
                    pepitasAsesino == null ? "nadie" : pepitasAsesino.getCustomName() == null ? "aldeano"
                            : pepitasAsesino.getCustomName().getString().replace("\n", " | "),
                    pepitasAsesino != null && esGuardia(pepitasAsesino) ? " (GUARDIA)" : "",
                    pepitasAsesino == null ? 0 : pepitasEnElZurron(pepitasAsesino), pepitasQueLlevabaElAsesino,
                    pepitasPlantadas != null && pepitasPlantadas.isAlive() ? " (el zombi SIGUE vivo)" : "");
            return;
        }
        if (ticks % 400 == 300) {
            // (3) ¿SALEN DE AHI? Se cuentan las pepitas de TODOS los zurrones del pueblo (y quien las lleva), las del
            // almacen y los picos: es lo que dice en QUE ESLABON se rompe la cadena del hierro.
            StringBuilder quien = new StringBuilder();
            int enZurrones = 0;
            // 300 bloques, no 140: el aldeano que lleva el botin se va al MUELLE o al monte y con 140 se salia de la
            // cuenta (medido: `en zurrones=1 [Zacarias … Yendo al muelle]` y dos muestras despues `en zurrones=0`
            // con la pepita todavia en su zurron: era el radio del escaneo, no que la hubiera dejado).
            for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(300))) {
                int n = pepitasEnElZurron(v);
                if (n > 0) {
                    enZurrones += n;
                    quien.append(v.getCustomName() == null ? "aldeano"
                            : v.getCustomName().getString().replace("\n", " | ")).append(esGuardia(v) ? " (GUARDIA)"
                                    : "").append('=').append(n).append(' ');
                }
            }
            DevilRpg.LOGGER.info("[Arnes] PEPITAS t={} 200 ticks despues: en el suelo={} en zurrones={} [{}] en el"
                            + " ALMACEN={} picos={} (por material: {})", ticks, pepitasEnElSuelo(level), enZurrones,
                    quien.toString().trim(), pepitasEnElAlmacen(level), picosEnElAlmacen(level),
                    picosPorMaterial(level));
            pepitasPlantadas = null;
            pepitasAsesino = null;
        }
    }

    /** Los picos del almacen POR MATERIAL: es lo que dice si el herrero ha gastado las pepitas en uno de HIERRO. */
    private static String picosPorMaterial(ServerLevel level) {
        net.minecraft.world.Container caja = com.chipoodle.devilrpg.world.VillageStorage.almacen(level, CENTRO);
        if (caja == null) {
            return "-";
        }
        int madera = 0;
        int piedra = 0;
        int hierro = 0;
        int diamante = 0;
        int otros = 0;
        for (int i = 0; i < caja.getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack s = caja.getItem(i);
            if (!(s.getItem() instanceof net.minecraft.world.item.PickaxeItem)) {
                continue;
            }
            if (s.is(net.minecraft.world.item.Items.WOODEN_PICKAXE)) {
                madera += s.getCount();
            } else if (s.is(net.minecraft.world.item.Items.STONE_PICKAXE)) {
                piedra += s.getCount();
            } else if (s.is(net.minecraft.world.item.Items.IRON_PICKAXE)) {
                hierro += s.getCount();
            } else if (s.is(net.minecraft.world.item.Items.DIAMOND_PICKAXE)) {
                diamante += s.getCount();
            } else {
                otros += s.getCount();
            }
        }
        return "madera " + madera + ", piedra " + piedra + ", hierro " + hierro + ", diamante " + diamante
                + ", otros " + otros;
    }

    private static com.chipoodle.devilrpg.entity.AggressiveZombieEntity pepitasPlantadas = null;
    /** El aldeano al que se le atribuye la muerte (el que lootea, ver `dropCustomDeathLoot`). */
    private static Villager pepitasAsesino = null;
    /** Pepitas que llevaba el asesino ANTES de la muerte (para ver que el botin es NUEVO). */
    private static int pepitasQueLlevabaElAsesino = -1;

    /** Pepitas de hierro que lleva ESE aldeano en el zurron (donde el mod mete el botin del que mata). */
    private static int pepitasEnElZurron(Villager v) {
        int total = 0;
        for (int i = 0; i < v.getInventory().getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack s = v.getInventory().getItem(i);
            if (s.is(net.minecraft.world.item.Items.IRON_NUGGET)) {
                total += s.getCount();
            }
        }
        return total;
    }

    /** Cuantas pepitas de hierro hay tiradas por el suelo del pueblo. */
    private static int pepitasEnElSuelo(ServerLevel level) {
        int total = 0;
        for (net.minecraft.world.entity.item.ItemEntity it : level.getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class, new AABB(CENTRO).inflate(80))) {
            if (it.getItem().is(net.minecraft.world.item.Items.IRON_NUGGET)) {
                total += it.getItem().getCount();
            }
        }
        return total;
    }

    /** Pepitas de hierro en TODOS los cofres del pueblo (el almacen puede tener varios). */
    private static int pepitasEnTodosLosCofres(ServerLevel level) {
        int total = 0;
        for (BlockPos p : BlockPos.betweenClosed(CENTRO.offset(-24, -5, -24), CENTRO.offset(24, 5, 24))) {
            if (level.getBlockEntity(p) instanceof net.minecraft.world.Container c) {
                total += com.chipoodle.devilrpg.world.VillagePantry.contar(c,
                        s -> s.is(net.minecraft.world.item.Items.IRON_NUGGET));
            }
        }
        return total;
    }

    /** Picos en TODOS los cofres del pueblo (los forja el herrero de herramientas). */
    private static int picosEnTodosLosCofres(ServerLevel level) {
        int total = 0;
        for (BlockPos p : BlockPos.betweenClosed(CENTRO.offset(-24, -5, -24), CENTRO.offset(24, 5, 24))) {
            if (level.getBlockEntity(p) instanceof net.minecraft.world.Container c) {
                total += com.chipoodle.devilrpg.world.VillagePantry.contar(c,
                        s -> s.getItem() instanceof net.minecraft.world.item.PickaxeItem);
            }
        }
        return total;
    }

    /** Cuantas pepitas de hierro hay en el almacen del pueblo. */
    private static int pepitasEnElAlmacen(ServerLevel level) {
        net.minecraft.world.Container caja = com.chipoodle.devilrpg.world.VillageStorage.almacen(level, CENTRO);
        return caja == null ? -1 : com.chipoodle.devilrpg.world.VillagePantry.contar(caja,
                s -> s.is(net.minecraft.world.item.Items.IRON_NUGGET));
    }

    /** Cuantos picos hay en el almacen del pueblo (los forja el herrero de herramientas). */
    private static int picosEnElAlmacen(ServerLevel level) {
        net.minecraft.world.Container caja = com.chipoodle.devilrpg.world.VillageStorage.almacen(level, CENTRO);
        return caja == null ? -1 : com.chipoodle.devilrpg.world.VillagePantry.contar(caja,
                s -> s.getItem() instanceof net.minecraft.world.item.PickaxeItem
                        || s.is(net.minecraft.world.item.Items.IRON_PICKAXE));
    }

    /**
     * El aldeano mas cercano a un punto (la muerte se le atribuye a el, como hace `MEDIR_MILICIA`)…
     * <b>PREFIRIENDO A UN GUARDIA</b> (27-sep-2026): el jugador pidio que *"los guardias, al matar zombis de un raid,
     * consigan hierro"*, y midiendo con el aldeano mas cercano a secas el que mataba era un <b>herrero de armas</b>
     * (cuyo goal SI tiene su "deja lo tuyo"), asi que la cadena parecia funcionar y no se medía lo que el jugador
     * pedia. Aqui se busca primero entre los que llevan el `VillagerGuardGoal`.
     */
    private static Villager aldeanoMasCercano(ServerLevel level, BlockPos pos) {
        Villager mejor = null;
        double mejorD = Double.MAX_VALUE;
        Villager mejorGuardia = null;
        double mejorDGuardia = Double.MAX_VALUE;
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(pos).inflate(60))) {
            double d = v.distanceToSqr(pos.getX(), pos.getY(), pos.getZ());
            if (esGuardia(v)) {
                if (d < mejorDGuardia) {
                    mejorDGuardia = d;
                    mejorGuardia = v;
                }
            } else if (d < mejorD) {
                mejorD = d;
                mejor = v;
            }
        }
        return mejorGuardia != null ? mejorGuardia : mejor;
    }

    /** ¿Ese aldeano lleva el goal de la guardia? (es lo que el jugador llama "un guardia"). */
    private static boolean esGuardia(Villager v) {
        for (net.minecraft.world.entity.ai.goal.WrappedGoal w : v.goalSelector.getAvailableGoals()) {
            if (w.getGoal() instanceof VillagerGuardGoal) {
                return true;
            }
        }
        return false;
    }

    private static void medirElMinero(ServerLevel level) {        level.setDayTime(6000L); // de dia: de noche el minero descansa (estaDescansando)
        var aldeanos = level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(140));
        java.util.List<Villager> mineros = new java.util.ArrayList<>();
        for (Villager v : aldeanos) {
            if (!v.isBaby() && v.getVillagerData().getProfession()
                    == net.minecraft.world.entity.npc.VillagerProfession.MASON) {
                mineros.add(v);
            }
        }
        if (mineros.isEmpty()) {
            if (ticks == 200) {
                com.chipoodle.devilrpg.world.VillageGenerator.spawnOneVillager(level, CENTRO, 11, false);
                DevilRpg.LOGGER.info("[Arnes] MINERO: no habia ninguno con el oficio de albanil (MASON): plantado uno"
                        + " en su sitio con la puerta del mod (sitio 11)");
            }
            return;
        }
        // A LOS 60 s SE CAVA A MANO LA PRIMERA GALERIA (la del paso 16) para poder medir LO QUE VIENE DESPUES: el
        // caracol no avanza de paso hasta que su galeria esta ENTERA (I102), y con el servidor headless corriendo a
        // los ticks que le deja el equipo eso son muchos minutos de reloj. Con la galeria ya abierta se ve si el minero
        // pone la pieza de esa celda y SIGUE bajando (paso 17 en adelante), que es lo que hay que comprobar.
        // Y A LOS 10 s SE LE VACIA EL CARBON AL ALMACEN: es para poder medir que el minero se fabrica el CARBON
        // VEGETAL quemando un tronco (sin carbon en el cofre, o lo saca de un tronco o no hace antorchas).
        if (ticks == 200) {
            vaciarElCarbonDelAlmacen(level);
        }
        // Y A LOS 20 s SE DA POR TERMINADO **EL POZO 1** (28-sep-2026): se le pone la PIEDRA LABRADA del tope en su
        // frente, que es lo que lee `VillageGenerator.laMinaLlegoAlTope`. Es la forma de medir el SEGUNDO POZO sin
        // esperar 240 pasos: en cuanto el pozo 1 esta topado, el minero tiene que ELEGIR el 2 (el suroeste, eje
        // 470,646) y empezar su caracol desde el paso 0.
        if (ticks == 400) {
            int nivelTope = com.chipoodle.devilrpg.world.VillageGenerator.cotaDeLaPlaza(level, CENTRO);
            int pasoTope = com.chipoodle.devilrpg.world.VillageGenerator.progresoDeLaMina(level, CENTRO, nivelTope);
            BlockPos celdaTope = com.chipoodle.devilrpg.world.VillageGenerator.celdaDelCaracol(CENTRO, nivelTope,
                    pasoTope);
            level.setBlock(celdaTope, net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState(),
                    net.minecraft.world.level.block.Block.UPDATE_ALL);
            DevilRpg.LOGGER.info("[Arnes] TOPE t={} el pozo 1 se da por terminado: piedra labrada en {} (paso {})",
                    ticks, celdaTope.toShortString(), pasoTope);
        }
        // EL CAVADO A MANO DE LA GALERIA DEL PASO 16 YA NO HACE FALTA (27-sep-2026): se metio porque el caracol no
        // avanza de paso hasta que su galeria esta ENTERA (I102) y el minero no podia ENTRAR en ella (la boca de dos
        // celdas de hueco es inalcanzable desde la losa del caracol, medido). Con el hueco de paso de TRES celdas el
        // minero la cava EL SOLO: medido en la corrida `medida-galeria-3alto.log`, 2/24 -> 18/24 entre t=160 y
        // t=1.160 (~1.000 ticks, unas 8 celdas por minuto de servidor). Dejarlo cavado a mano ya solo tapaba lo que
        // se quiere medir, asi que NO se toca la mina.
        if (ticks % 40 != 0) {
            return;
        }
        int cota = com.chipoodle.devilrpg.world.VillageGenerator.cotaDeLaPlaza(level, CENTRO);
        int paso = com.chipoodle.devilrpg.world.VillageGenerator.progresoDeLaMina(level, CENTRO, cota);
        int fondo = com.chipoodle.devilrpg.world.VillageGenerator.pasosHastaElFondo(cota);
        BlockPos cara = com.chipoodle.devilrpg.world.VillageGenerator.celdaDelCaracol(CENTRO, cota, paso);
        boolean tope = com.chipoodle.devilrpg.world.VillageGenerator.laMinaLlegoAlTope(level, CENTRO, cota, paso);
        String topeBloque = level.getBlockState(cara).getBlock().toString()
                .replace("Block{minecraft:", "").replace("}", "");
        DevilRpg.LOGGER.info("[Arnes] MINA t={} pasos={}/{} cara={} (y={} · {} bloques por debajo del suelo)"
                        + " bloqueDeLaCara={} TOPE={} caseta={} puesto={} balsa={} horno={}",
                ticks, paso, fondo, cara.toShortString(), cara.getY(), (cota - 1) - cara.getY(), topeBloque,
                tope ? "SI" : "NO",
                com.chipoodle.devilrpg.world.VillageGenerator.centroDeLaMina(CENTRO).toShortString(),
                com.chipoodle.devilrpg.world.VillageGenerator.puestoDelMinero(level, CENTRO),
                com.chipoodle.devilrpg.world.VillageGenerator.balsaDelMinero(level, CENTRO),
                com.chipoodle.devilrpg.world.VillageGenerator.hornoDelMinero(level, CENTRO));
        // LA BOCA DE LA MINA Y LO QUE TIENE ENCIMA (I125): el jugador la vio SELLADA. Se vuelca la casilla de la boca,
        // las de encima y **qué quiere el PLANO** en cada una: si el plano pide ahí un bloque, el obrero lo repone y
        // tapa el pozo — que es la sospecha (el plano guardó la caseta con su suelo sólido sobre la bajada).
        BlockPos boca = com.chipoodle.devilrpg.world.VillageGenerator.bocaDeLaMina(CENTRO, cota);
        StringBuilder bocas = new StringBuilder();
        for (int dy = -1; dy <= 3; dy++) {
            BlockPos q = boca.above(dy);
            var delPlano = com.chipoodle.devilrpg.world.VillageManager.blueprintState(level, INDICE, q);
            String aqui = level.getBlockState(q).getBlock().toString().replace("Block{minecraft:", "").replace("}", "");
            String plano = delPlano == null ? "-"
                    : delPlano.getBlock().toString().replace("Block{minecraft:", "").replace("}", "");
            bocas.append(q.toShortString()).append('=').append(aqui).append("(plano:").append(plano).append(") ");
        }
        DevilRpg.LOGGER.info("[Arnes] BOCA DE LA MINA t={} {} -> {}", ticks, boca.toShortString(), bocas.toString().trim());
        volcarElPozo(level, cota, paso, fondo);
        volcarLaGaleria(level, cota, paso);
        volcarElAguaDeLaGaleria(level, cota, paso);
        for (Villager v : mineros) {
            // DIAGNOSTICO (por que el minero se queda SIN GOAL CORRIENDO): se vuelca TODO lo que mira su `canUse`
            // —el turno y la comida, el sitio aparcado (I33) con su hora, y la lista COMPLETA de goals con cual
            // corre—, porque "goals=[]" a secas no dice si el goal no está puesto, si no puede empezar o si está
            // esperando a algo. Es lo que costó dos corridas entender en el atasco de la muralla (I112).
            StringBuilder goals = new StringBuilder();
            StringBuilder todos = new StringBuilder();
            for (net.minecraft.world.entity.ai.goal.WrappedGoal w : v.goalSelector.getAvailableGoals()) {
                String simple = w.getGoal().getClass().getSimpleName();
                todos.append(simple).append(w.isRunning() ? "* " : " ");
                if (w.isRunning()) {
                    goals.append(simple).append(' ');
                }
            }
            var wt = v.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
            var pico = v.getMainHandItem();
            StringBuilder zurron = new StringBuilder();
            for (int i = 0; i < v.getInventory().getContainerSize(); i++) {
                var s = v.getInventory().getItem(i);
                if (!s.isEmpty()) {
                    zurron.append(i).append(':').append(s.getCount()).append('x')
                            .append(s.getItem().toString().replace("Item{minecraft:", "").replace("}", "")).append(' ');
                }
            }
            var datos = v.getPersistentData();
            BlockPos apoyo = com.chipoodle.devilrpg.world.VillageStorage.puntoDeApoyo(level, CENTRO);
            long gameTime = level.getGameTime();
            // LA RUTA VIVA (la que esta ejecutando el caminante AHORA) y la ruta a su FAENA (lo que dice el
            // planificador del juego cuando se le pregunta por el destino de su cerebro): es lo que distingue "no hay
            // camino hasta su celda" (el pozo esta cortado) de "hay camino y el aldeano no va".
            var caminoVivo = v.getNavigation().getPath();
            String nav = caminoVivo == null ? "sin ruta"
                    : (caminoVivo.getNodeCount() + " nodos hasta "
                            + caminoVivo.getEndNode().asBlockPos().toShortString()
                            + (caminoVivo.canReach() ? " alcanza" : " NO alcanza"));
            BlockPos destinoM = wt == null ? null : wt.getTarget().currentBlockPosition();
            String rutaFaena = destinoM == null ? "-" : rutaDetallada(v, destinoM);
            // LA SONDA DEL POZO, PASO A PASO: se le pregunta AL PLANIFICADOR DEL JUEGO por la casilla de pie de cada
            // paso del caracol, empezando por la boca, y se PARA en el primero que NO alcanza. Es lo que distingue
            // "el destino que le doy no es una casilla valida" de "el pozo esta cortado en tal celda": con
            // `destino=SIN DESTINO` a secas, el log no lo decia (el cerebro del aldeano BORRA el WALK_TARGET cuando
            // el planificador no llega, asi que el sintoma es el mismo en los dos casos).
            // OJO CON LA PRECISION (nos mordio en la primera pasada): `createPath(celda, 1)` da por ALCANZADA una
            // celda que este a 1 de distancia, asi que decia `SI` con el fin de la ruta en la celda de AL LADO. Aqui
            // se pregunta con 0 (la celda EXACTA) y se imprime tambien lo que hay en los pies y en la cabeza, que es
            // lo que el planificador mira (el aldeano mide 1,95: dos celdas).
            // Y VA CADA 10 s, NO CADA 2 s: cada sonda son ~20 busquedas de ruta DEL JUEGO y con el servidor headless
            // eso se nota (medido: "Can't keep up! ... 137 ticks behind").
            if (ticks % 200 == 0) {
                StringBuilder sonda = new StringBuilder();
                for (int p = 0; p <= Math.min(fondo, Math.max(paso + 2, 12)); p++) {
                    BlockPos pie = com.chipoodle.devilrpg.world.VillageGenerator.celdaDelCaracol(CENTRO, cota, p)
                            .above(com.chipoodle.devilrpg.world.VillageGenerator.esLosaDelCaracol(p) ? 0 : 1);
                    var caminoP = v.getNavigation().createPath(pie, 0);
                    boolean alcanzaP = caminoP != null && caminoP.canReach();
                    sonda.append(p).append(':').append(pie.toShortString()).append('(')
                            .append(nombre(level, pie.getX(), pie.getY(), pie.getZ())).append('/')
                            .append(nombre(level, pie.getX(), pie.getY() + 1, pie.getZ())).append(")=")
                            .append(caminoP == null ? "NO(nula)"
                                    : caminoP.getNodeCount() + "n/" + (alcanzaP ? "SI" : "NO")
                                            + " fin=" + caminoP.getEndNode().asBlockPos().toShortString())
                            .append(' ');
                    if (!alcanzaP) {
                        break;
                    }
                }
                DevilRpg.LOGGER.info("[Arnes] SONDA DEL POZO t={} pos={} {}", ticks,
                        v.blockPosition().toShortString(), sonda.toString().trim());
            }
            // LA SONDA DE LA CASETA (26-sep-2026): el modelo `ruta_atasco.py` SI encuentra ruta desde el almacen
            // hasta el apoyo de la caseta (57 pasos, entrando por su puerta sur) y el planificador del JUEGO no
            // (`rutaFaena … alcance=NO`). Esta sonda pregunta celda a celda, de fuera adentro, y para en la primera
            // que el juego no alcanza: dice si el que se rinde es el mundo (algo tapia) o el planificador.
            if (ticks % 200 == 0) {
                BlockPos apoyoCaseta = com.chipoodle.devilrpg.world.VillageGenerator.puntoDeApoyoDeLaCaseta(level, CENTRO);
                BlockPos puesto = com.chipoodle.devilrpg.world.VillageGenerator.puestoDelMinero(level, CENTRO);
                StringBuilder sc = new StringBuilder();
                java.util.List<BlockPos> escala = new java.util.ArrayList<>();
                escala.add(apoyoCaseta.south(4));
                escala.add(apoyoCaseta.south(3));
                escala.add(apoyoCaseta.south(2));
                escala.add(apoyoCaseta.south(1));
                escala.add(apoyoCaseta);
                if (puesto != null) {
                    escala.add(puesto.north());
                    escala.add(puesto.west());
                }
                for (BlockPos q : escala) {
                    var cq = v.getNavigation().createPath(q, 0);
                    boolean ok = cq != null && cq.canReach();
                    sc.append(q.toShortString()).append('(').append(nombre(level, q.getX(), q.getY(), q.getZ()))
                            .append('/').append(nombre(level, q.getX(), q.getY() - 1, q.getZ())).append(")=")
                            .append(cq == null ? "NO(nula)"
                                    : cq.getNodeCount() + "n/" + (ok ? "SI" : "NO") + " fin="
                                            + cq.getEndNode().asBlockPos().toShortString())
                            .append(' ');
                    if (!ok) {
                        break;
                    }
                }
                DevilRpg.LOGGER.info("[Arnes] SONDA DE LA CASETA t={} pos={} apoyo={} puesto={} {}", ticks,
                        v.blockPosition().toShortString(), apoyoCaseta.toShortString(),
                        puesto == null ? "-" : puesto.toShortString(), sc.toString().trim());
            }
            // LA SONDA DE LA GALERIA (27-sep-2026): al planificador del juego, celda a celda de la galeria que toca,
            // desde donde esta el minero. Es lo que decide si la boca de la galeria se puede ANDAR desde el caracol
            // (el atasco que tenia al minero 3.600 ticks con `hechas=3/24`): ver `sondarLaGaleria`.
            if (ticks % 200 == 0) {
                sondarLaGaleria(level, v, cota, paso);
            }
            // MEDIDA DEL PICO (26-sep-2026): se le deja al borde de romperse cada 2.000 ticks para medir que, al
            // romperse, SUELTA la faena, va al almacen y VUELVE con otro (los forja el herrero de herramientas).
            var enMano = v.getMainHandItem();
            if (ticks % 2000 == 0 && !enMano.isEmpty() && (enMano.is(net.minecraft.world.item.Items.WOODEN_PICKAXE)
                    || enMano.is(net.minecraft.world.item.Items.STONE_PICKAXE)
                    || enMano.is(net.minecraft.world.item.Items.IRON_PICKAXE))) {
                enMano.setDamageValue(Math.max(0, enMano.getMaxDamage() - 1));
                DevilRpg.LOGGER.info("[Arnes] PICO t={} al borde de romperse ({}/{})", ticks,
                        enMano.getDamageValue(), enMano.getMaxDamage());
            }            DevilRpg.LOGGER.info("[Arnes] MINERO t={} pos={} cara={} dCara={} destino={} pico={}({}/{}) zurron=[{}]"
                            + " goals=[{}] TODOS=[{}] nav=[{}] rutaFaena=[{}] etiqueta={}",
                    ticks, v.blockPosition().toShortString(), cara.toShortString(),
                    fmt(Math.sqrt(v.distanceToSqr(cara.getX() + 0.5D, cara.getY() + 0.5D, cara.getZ() + 0.5D))),
                    wt == null ? "SIN DESTINO" : wt.getTarget().currentBlockPosition().toShortString(),
                    pico.isEmpty() ? "SIN PICO"
                            : pico.getItem().toString().replace("Item{minecraft:", "").replace("}", ""),
                    pico.isEmpty() ? 0 : pico.getDamageValue(), pico.isEmpty() ? 0 : pico.getMaxDamage(),
                    zurron.toString().trim(), goals.toString().trim(), todos.toString().trim(), nav, rutaFaena,
                    v.getCustomName() == null ? "-" : v.getCustomName().getString().replace("\n", " | "));
            DevilRpg.LOGGER.info("[Arnes] MINERO-ESTADO t={} descansando={} hambre={} comida={} apoyo={} aparcado={}"
                            + " aparcadoHasta={} gameTime={} picoEnMano={}",
                    ticks, com.chipoodle.devilrpg.world.VillageManager.estaDescansando(v),
                    com.chipoodle.devilrpg.world.VillageManager.tieneHambre(level, v),
                    com.chipoodle.devilrpg.world.VillagePantry.comida(level, CENTRO), apoyo,
                    apoyo != null && com.chipoodle.devilrpg.world.VillageManager.esPuntoFallido(v, apoyo),
                    datos.contains("DevilRpgPuntoFallidoHasta") ? datos.getLong("DevilRpgPuntoFallidoHasta") : -1L,
                    gameTime, !v.getMainHandItem().isEmpty());
        }
        if (ticks % 200 != 0) {
            return;
        }
        // EL ALMACEN: lo que la mina tiene que estar metiendo (adoquin, carbon, lingotes, pedernal y picos).
        var caja = com.chipoodle.devilrpg.world.VillageStorage.almacen(level, CENTRO);
        int adoquin = 0;
        int carbon = 0;
        int lingotes = 0;
        int pedernal = 0;
        int picos = 0;
        int crudos = 0;
        if (caja != null) {
            for (int i = 0; i < caja.getContainerSize(); i++) {
                var s = caja.getItem(i);
                if (s.isEmpty()) {
                    continue;
                }
                if (s.is(net.minecraft.world.item.Items.COBBLESTONE)
                        || s.is(net.minecraft.world.item.Items.COBBLED_DEEPSLATE)) {
                    adoquin += s.getCount();
                } else if (s.is(net.minecraft.world.item.Items.COAL)) {
                    carbon += s.getCount();
                } else if (s.is(net.minecraft.world.item.Items.IRON_INGOT)
                        || s.is(net.minecraft.world.item.Items.COPPER_INGOT)
                        || s.is(net.minecraft.world.item.Items.GOLD_INGOT)) {
                    lingotes += s.getCount();
                } else if (s.is(net.minecraft.world.item.Items.FLINT)) {
                    pedernal += s.getCount();
                } else if (s.is(net.minecraft.world.item.Items.IRON_PICKAXE)) {
                    picos += s.getCount();
                } else if (s.is(net.minecraft.world.item.Items.RAW_IRON)
                        || s.is(net.minecraft.world.item.Items.RAW_COPPER)
                        || s.is(net.minecraft.world.item.Items.RAW_GOLD)) {
                    crudos += s.getCount();
                }
            }
        }
        DevilRpg.LOGGER.info("[Arnes] ALMACEN DE LA MINA t={}: {} adoquin, {} carbon, {} lingote(s), {} crudo(s),"
                        + " {} pedernal, {} pico(s) | leña={}",
                ticks, adoquin, carbon, lingotes, crudos, pedernal, picos,
                com.chipoodle.devilrpg.world.VillageStorage.cuentaLena(level, CENTRO));
        // LAS DOS CASETAS: la VIEJA (rel -9,+12 = 461,658) tiene que haber vuelto a ser terreno del pueblo y la NUEVA
        // (rel +33,-29 = 503,617) tiene que tener su caseta con la CAMA DENTRO (pegada a la pared este). Y el PLANO
        // tiene que haberse movido con ella: se cuentan sus celdas en los dos solares.
        DevilRpg.LOGGER.info("[Arnes] CASETA VIEJA (461,658): suelo={} dentro={} cortapiedras={} cama={} boca={}",
                nombre(level, 461, 62, 658), nombre(level, 461, 63, 658), nombre(level, 459, 63, 656),
                nombre(level, 462, 63, 661), nombre(level, 465, 62, 654));
        DevilRpg.LOGGER.info("[Arnes] CASETA NUEVA (503,617): suelo={} dentro={} cortapiedras={} horno={} camaPie={}"
                        + " camaCabecera={} boca={}",
                nombre(level, 503, 62, 617), nombre(level, 503, 63, 617), nombre(level, 501, 63, 615),
                nombre(level, 501, 63, 619), nombre(level, 505, 63, 616), nombre(level, 505, 63, 617),
                nombre(level, 507, 62, 613));
        var plano = com.chipoodle.devilrpg.world.VillageSavedData.get(level).getBlueprint(INDICE);
        int enViejo = 0;
        int enNuevo = 0;
        if (plano != null) {
            for (int i = 0; i < plano.size(); i++) {
                BlockPos p = plano.posAt(i);
                if (Math.abs(p.getX() - 461) <= 5 && Math.abs(p.getZ() - 658) <= 5) {
                    enViejo++;
                }
                if (Math.abs(p.getX() - 503) <= 5 && Math.abs(p.getZ() - 617) <= 5) {
                    enNuevo++;
                }
            }
        }
        DevilRpg.LOGGER.info("[Arnes] PLANO: {} celda(s) en el solar VIEJO (461,658) y {} en el NUEVO (503,617)",
                enViejo, enNuevo);
    }

    /** El nombre del bloque de una celda (para las comprobaciones de la caseta). */
    private static String nombre(ServerLevel level, int x, int y, int z) {
        return level.getBlockState(new BlockPos(x, y, z)).getBlock().toString()
                .replace("Block{minecraft:", "").replace("}", "");
    }

    /**
     * <b>EL POZO ENTERO, PASO A PASO</b> (el instrumento del "esta sellada la entrada"): de cada celda del caracol
     * imprime su <b>pieza</b>, la celda de los <b>pies</b> y la de la <b>cabeza</b> (las dos que tienen que estar
     * libres para poder andar por el túnel), con un <b>{@code *}</b> delante del paso que está TAPADO y el nombre del
     * bloque que lo tapa. Y de la <b>galería</b> del primer paso que la abre, cuántas celdas están abiertas.
     * <p>
     * Se volcan los pasos hasta un poco más allá de la faena (y como mínimo los ocho primeros, que son los que cruzan
     * la capa del suelo y por donde se <b>entra</b>): así se ve si el corte está arriba (el pueblo ha vuelto a poner
     * el suelo encima del pozo) o abajo (el propio marco de madera del caracol).
     */
    private static void volcarElPozo(ServerLevel level, int cota, int pasoActual, int fondo) {
        StringBuilder sb = new StringBuilder();
        int hasta = Math.min(fondo, Math.max(pasoActual + 3, 8));
        for (int p = 0; p <= hasta; p++) {
            BlockPos celda = com.chipoodle.devilrpg.world.VillageGenerator.celdaDelCaracol(CENTRO, cota, p);
            String pieza = nombre(level, celda.getX(), celda.getY(), celda.getZ());
            String pies = nombre(level, celda.getX(), celda.getY() + 1, celda.getZ());
            String cabeza = nombre(level, celda.getX(), celda.getY() + 2, celda.getZ());
            boolean tapado = !esLibre(pies) || !esLibre(cabeza);
            sb.append(tapado ? " *" : "  ").append(p).append(':').append(pieza).append('/').append(pies)
                    .append('/').append(cabeza);
        }
        String galeria = "-";
        int pasoGaleria = com.chipoodle.devilrpg.world.VillageGenerator.abreGaleria(pasoActual) ? pasoActual
                : Math.max(0, (pasoActual / 16) * 16);
        if (pasoGaleria > 0) {
            int abiertas = 0;
            for (int i = 1; i <= com.chipoodle.devilrpg.world.VillageGenerator.MINA_GALERIA_LARGO; i++) {
                BlockPos g = com.chipoodle.devilrpg.world.VillageGenerator.celdaDeLaGaleria(CENTRO, cota, pasoGaleria, i);
                if (level.getBlockState(g).isAir()) {
                    abiertas++;
                }
            }
            galeria = "paso " + pasoGaleria + "=" + abiertas + "/"
                    + com.chipoodle.devilrpg.world.VillageGenerator.MINA_GALERIA_LARGO;
        }
        DevilRpg.LOGGER.info("[Arnes] POZO t={} faena={} (hasta el paso {}) eje={} datos=\"paso:pieza/pies/cabeza\"{}"
                        + " | galeria={}", ticks, pasoActual, hasta,
                com.chipoodle.devilrpg.world.VillageGenerator.centroDeLaMina(CENTRO).toShortString(), sb.toString(),
                galeria);
    }

    /**
     * <b>LA GALERIA DEL PASO QUE LA ABRE, celda a celda</b> (el instrumento del acuifero): imprime las primeras
     * celdas de la galeria con lo que hay en cada una (aire, adoquin = agua SELLADA, piedra labrada = tope) y
     * cuantas cuenta el mod como hechas (`progresoDeLaGaleria`). Es lo que distingue "la galeria avanza" de "el
     * agua vuelve a entrar y el contador se queda en cero" — el bucle que tuvo al minero 110.000 ticks en el paso 32.
     * <p>
     * <b>Y DESDE EL 27-sep-2026 SE VUELCA TAMBIEN EL SUELO Y EL TECHO</b> de cada celda
     * ({@code celda/suelo/+1/+2}), que es lo que hacia falta para medir el atasco de la boca: con la galeria de DOS
     * celdas de hueco el vecino de la Y de la losa del caracol sale BLOCKED y el tunel no se puede ni entrar (ver
     * {@code VillagerMinerGoal.picarLaCeldaDeLaGaleria}). Con esto se ve de un vistazo si una celda tiene suelo
     * (se anda), si le falta (agujero) y si tiene los tres huecos de paso.
     */
    private static void volcarLaGaleria(ServerLevel level, int cota, int paso) {
        if (!com.chipoodle.devilrpg.world.VillageGenerator.abreGaleria(paso)) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 8; i++) {
            BlockPos g = com.chipoodle.devilrpg.world.VillageGenerator.celdaDeLaGaleria(CENTRO, cota, paso, i);
            sb.append(i).append(':').append(nombre(level, g.getX(), g.getY(), g.getZ()))
                    .append("/suelo=").append(nombre(level, g.getX(), g.getY() - 1, g.getZ()))
                    .append("/+1=").append(nombre(level, g.getX(), g.getY() + 1, g.getZ()))
                    .append("/+2=").append(nombre(level, g.getX(), g.getY() + 2, g.getZ())).append(' ');
        }
        DevilRpg.LOGGER.info("[Arnes] GALERIA t={} paso={} hechas={}/{} (1..8) {}", ticks, paso,
                com.chipoodle.devilrpg.world.VillageGenerator.progresoDeLaGaleria(level, CENTRO, cota, paso),
                com.chipoodle.devilrpg.world.VillageGenerator.MINA_GALERIA_LARGO, sb.toString().trim());
    }

    /**
     * <b>LA SONDA DE LA GALERIA</b> (27-sep-2026): se le pregunta <b>AL PLANIFICADOR DEL JUEGO</b>, celda a celda de
     * la galeria que toca, si hay ruta hasta ella <b>desde donde esta el minero ahora mismo</b>
     * ({@code createPath(celda, 0)}: la celda EXACTA, sin tolerancia). Es la medida que decide el atasco de la boca:
     * con la galeria de dos celdas el juego devolvia ruta <b>nula o que no alcanza</b> a las celdas de dentro, y el
     * aldeano se quedaba en la celda del caracol con el destino borrado del cerebro.
     * <p>
     * Solo se pregunta cuando el minero esta <b>cerca</b> (a menos de {@value #SONDA_GALERIA_RADIO} bloques del
     * centro de la mina): desde la superficie el planificador no llega ni con ruta buena (su region son 56 bloques y
     * la heuristica no baja por un caracol), y eso ya se mide en `rutaFaena`.
     */
    private static void sondarLaGaleria(ServerLevel level, Villager v, int cota, int paso) {
        if (!com.chipoodle.devilrpg.world.VillageGenerator.abreGaleria(paso)) {
            return;
        }
        BlockPos boca = com.chipoodle.devilrpg.world.VillageGenerator.celdaDeLaGaleria(CENTRO, cota, paso, 1);
        if (v.blockPosition().distSqr(boca) > SONDA_GALERIA_RADIO * SONDA_GALERIA_RADIO) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 6; i++) {
            BlockPos g = com.chipoodle.devilrpg.world.VillageGenerator.celdaDeLaGaleria(CENTRO, cota, paso, i);
            var camino = v.getNavigation().createPath(g, 0);
            boolean ok = camino != null && camino.canReach();
            sb.append(i).append(':').append(g.toShortString()).append('=')
                    .append(camino == null ? "NO(nula)"
                            : camino.getNodeCount() + "n/" + (ok ? "SI" : "NO") + " fin="
                                    + camino.getEndNode().asBlockPos().toShortString())
                    .append(' ');
        }
        DevilRpg.LOGGER.info("[Arnes] SONDA DE LA GALERIA t={} pos={} paso={} {}", ticks,
                v.blockPosition().toShortString(), paso, sb.toString().trim());
    }

    /** Radio (bloques) dentro del cual se le pregunta al planificador por las celdas de la galeria. */
    private static final int SONDA_GALERIA_RADIO = 24;

    /**
     * <b>EL AGUA QUE RODEA LA GALERIA</b> (27-sep-2026): lista las celdas <b>con fluido</b> de la caja que envuelve
     * las primeras {@value #AGUA_CELDAS} celdas de la galeria que toca (2 de margen en horizontal y de {@code -2} a
     * {@code +4} en vertical). Es el instrumento que dice <b>DE DONDE entra el agua</b> cuando el tunel se inunda, que
     * es lo que hacia falta para el arreglo de las paredes: medido, con la galeria secada celda a celda el agua
     * volvia por una vecina que estaba SECA al secarla (una veta picada en la pared o el tercer hueco del techo).
     * Sin esto, "el agua vuelve" no dice ni por donde ni a que altura.
     */
    private static void volcarElAguaDeLaGaleria(ServerLevel level, int cota, int paso) {
        if (!com.chipoodle.devilrpg.world.VillageGenerator.abreGaleria(paso)) {
            return;
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        int y = 0;
        for (int i = 1; i <= AGUA_CELDAS; i++) {
            BlockPos g = com.chipoodle.devilrpg.world.VillageGenerator.celdaDeLaGaleria(CENTRO, cota, paso, i);
            minX = Math.min(minX, g.getX());
            maxX = Math.max(maxX, g.getX());
            minZ = Math.min(minZ, g.getZ());
            maxZ = Math.max(maxZ, g.getZ());
            y = g.getY();
        }
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (BlockPos q : BlockPos.betweenClosed(new BlockPos(minX - 2, y - 2, minZ - 2),
                new BlockPos(maxX + 2, y + 4, maxZ + 2))) {
            if (!level.getFluidState(q).isEmpty()) {
                n++;
                if (n <= 30) {
                    sb.append(q.toShortString()).append('=').append(nombre(level, q.getX(), q.getY(), q.getZ()))
                            .append(' ');
                }
            }
        }
        DevilRpg.LOGGER.info("[Arnes] AGUA t={} paso={} celdas con fluido={}{}", ticks, paso, n,
                n == 0 ? " (la galeria esta SECA)" : " -> " + sb.toString().trim());
    }

    /** Cuantas celdas de la galeria entran en la caja del volcado de agua. */
    private static final int AGUA_CELDAS = 6;

    /** ¿Esa celda se puede pisar (o es la de la cabeza)? Todo lo que no choque: aire, hierba, agua, cultivos. */
    private static boolean esLibre(String bloque) {        return bloque.equals("air") || bloque.equals("cave_air") || bloque.equals("water")
                || bloque.equals("short_grass") || bloque.equals("grass") || bloque.equals("tall_grass")
                || bloque.equals("torch") || bloque.equals("wall_torch") || bloque.equals("wheat")
                || bloque.equals("carrots") || bloque.equals("potatoes") || bloque.equals("beetroots")
                || bloque.equals("oak_sapling") || bloque.equals("dirt_path");
    }

    /** El bicho de la medida (el aldeano-zombi que se deja dentro de la aldea): se reutiliza, no se duplica. */
    private static net.minecraft.world.entity.monster.ZombieVillager bicho = null;

    /**
     * <b>Mantiene UN bicho dentro de la aldea</b> para medir el latido cortado: si no está (lo barrió otra cosa, se
     * descargó el chunk...), se vuelve a plantar; y si está, se le deja clavado en su celda (sin IA no se mueve, pero
     * un empujón lo saca del recinto y entonces la medida dejaría de ser "con un bicho dentro").
     */
    private static void mantenerBichoDentro(ServerLevel level) {
        int cota = com.chipoodle.devilrpg.world.VillageGenerator.cotaDeLaPlaza(level, CENTRO);
        BlockPos donde = CENTRO.offset(BICHO_EN.getX(), cota - CENTRO.getY(), BICHO_EN.getZ());
        if (bicho == null || bicho.isRemoved() || !bicho.isAlive()) {
            bicho = net.minecraft.world.entity.EntityType.ZOMBIE_VILLAGER.create(level);
            if (bicho == null) {
                return;
            }
            bicho.moveTo(donde.getX() + 0.5D, donde.getY(), donde.getZ() + 0.5D, 0.0F, 0.0F);
            bicho.setNoAi(true);
            bicho.setInvulnerable(true);
            bicho.setPersistenceRequired();
            level.addFreshEntity(bicho);
            DevilRpg.LOGGER.info("[Arnes] BICHO DENTRO: plantado un aldeano-zombi en {} (cota {})", donde, cota);
            return;
        }
        bicho.moveTo(donde.getX() + 0.5D, donde.getY(), donde.getZ() + 0.5D, bicho.getYRot(), bicho.getXRot());
    }

    /**
     * <b>¿Está el latido del pueblo cortado?</b> Se cuenta lo mismo que mira el mod
     * (`hayEnemigosDentro`: monstruos dentro del recinto en XZ y a la altura del pueblo) y se imprime junto al
     * censo de camas, para poder decir en la misma línea "hay bicho dentro" y "a este no le han dado cama".
     */
    private static String estadoDelRecinto(ServerLevel level) {
        int cota = com.chipoodle.devilrpg.world.VillageGenerator.cotaDeLaPlaza(level, CENTRO);
        int dentro = 0;
        for (net.minecraft.world.entity.monster.Monster m : level.getEntitiesOfClass(
                net.minecraft.world.entity.monster.Monster.class,
                new AABB(CENTRO).inflate(com.chipoodle.devilrpg.world.VillageGenerator.FENCE_RADIUS))) {
            if (VillageManager.dentroDelRecinto(cota, CENTRO, m,
                    com.chipoodle.devilrpg.world.VillageGenerator.FENCE_RADIUS)) {
                dentro++;
            }
        }
        return dentro == 0 ? "NO (el latido corre entero)" : "SI (" + dentro + " monstruo(s): latido cortado)";
    }

    /**
     * <b>LA PELEA Y EL SANADOR, SEMBRADOS A MANO</b> (I62/I64): dos zombis <b>flojos</b> dentro de la aldea —para que
     * la guardia pelee y mate— y tres guardias <b>heridos</b> al 35 % de su vida, para que el clérigo tenga pacientes
     * sin depender de que un zombi acierte. Los zombis pegan poco a propósito (1 de daño): la medida es de la
     * guardia, no de una masacre de aldeanos.
     */
    private static void sembrarLaMilicia(ServerLevel level) {
        int cota = com.chipoodle.devilrpg.world.VillageGenerator.cotaDeLaPlaza(level, CENTRO);
        // Primero los guardias: se hiere a tres y se apunta dónde está el primero, que es donde se sueltan los
        // zombis (a 3 bloques y SIN IA: la guardia los ve, los mata y sube de nivel sin que ellos maten a nadie).
        Villager primero = null;
        int heridos = 0;
        int guardias = 0;
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(140))) {
            if (!com.chipoodle.devilrpg.entity.goal.VillagerGuardGoal.esGuardia(v)) {
                continue;
            }
            guardias++;
            if (primero == null) {
                primero = v;
            }
            if (heridos < 3) {
                v.setHealth(Math.max(1.0F, v.getMaxHealth() * 0.35F));
                heridos++;
            }
        }
        BlockPos junto = primero == null ? CENTRO.offset(14, 0, 6) : primero.blockPosition().offset(3, 0, 0);
        int zombis = 0;
        for (int i = 0; i < 4; i++) {
            BlockPos donde = junto.offset(i % 2, 0, i / 2);
            net.minecraft.world.entity.monster.Zombie z = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
            if (z == null) {
                continue;
            }
            z.moveTo(donde.getX() + 0.5D, donde.getY(), donde.getZ() + 0.5D, 0.0F, 0.0F);
            z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(4.0D);
            z.setHealth(4.0F);
            z.setNoAi(true); // no pelean: la medida es de la guardia (un zombi con IA podia matar a un aldeano)
            z.setPersistenceRequired();
            level.addFreshEntity(z);
            zombis++;
        }
        DevilRpg.LOGGER.info("[Arnes] MILICIA: {} zombi(s) flojo(s) junto a {} y {} de {} guardia(s) herido(s) al 35 %"
                + " (el clerigo tiene que ir a curarlos)", zombis,
                primero == null ? "la plaza" : nombreCorto(primero), heridos, guardias);
    }

    /**
     * <b>LOS GOLPES, ATRIBUIDOS A LA GUARDIA</b> (I62): la pelea de verdad —que el goal de la guardia ataque a un
     * monstruo— ya está medida aparte; lo que aquí se mide es el <b>enganche de la muerte</b>: el daño va con
     * {@code mobAttack(guardia)}, que es exactamente lo que produce el juego cuando el aldeano pega, así que la muerte
     * pasa por {@code LivingDeathEvent} con el aldeano como dueño del daño.
     * <p>
     * Va <b>en un tick posterior</b> al sembrado a propósito: una entidad recién añadida al nivel no aparece todavía
     * en las consultas (`getEntitiesOfClass`) hasta que el mundo da un tick, así que haciéndolo en el mismo tick el
     * barrido no encontraba a los zombis y no había ni un golpe (medido: la línea de la siembra salía y la de los
     * golpes no).
     */
    private static void golpearConLaGuardia(ServerLevel level) {
        Villager guardia = null;
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(140))) {
            if (com.chipoodle.devilrpg.entity.goal.VillagerGuardGoal.esGuardia(v)) {
                guardia = v;
                break;
            }
        }
        int golpes = 0;
        if (guardia != null) {
            for (net.minecraft.world.entity.Mob m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                    new AABB(CENTRO).inflate(48))) {
                if (m instanceof net.minecraft.world.entity.monster.Monster) {
                    m.hurt(level.damageSources().mobAttack(guardia), 100.0F);
                    golpes++;
                }
            }
        }
        DevilRpg.LOGGER.info("[Arnes] MILICIA: {} golpe(s) mortal(es) de {} (matanzas={}, nv={})",
                golpes, guardia == null ? "nadie" : nombreCorto(guardia),
                guardia == null ? 0 : VillageManager.matanzasDeGuardia(guardia),
                guardia == null ? 0 : VillageManager.nivelDeGuardia(guardia));
    }

    /**
     * Cada segundo: <b>cada guardia</b> (vida, NIVEL, matanzas y etiqueta) y <b>el clérigo</b> (su etiqueta y el
     * herido más cercano, con su vida y a cuántos bloques está). Es la medida de I62 (la milicia aprende) y de I64
     * (el clérigo sana).
     */
    private static void volcarLaMilicia(ServerLevel level) {
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(140))) {
            if (!com.chipoodle.devilrpg.entity.goal.VillagerGuardGoal.esGuardia(v)) {
                continue;
            }
            // OJO: la ETIQUETA (que lleva la actividad) va la ULTIMA y sin saltos de linea: el nombre del aldeano es
            // "Nombre (Oficio)\nActividad" y con el `\n` en medio el log partia la linea y no se veia la medida.
            DevilRpg.LOGGER.info("[Arnes] GUARDIA {} nv={} matanzas={} vida={}/{} pos={} | etiqueta: {}",
                    nombreCorto(v), VillageManager.nivelDeGuardia(v), VillageManager.matanzasDeGuardia(v),
                    redondo(v.getHealth()), v.getMaxHealth(), v.blockPosition().toShortString(), etiquetaDe(v));
        }
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(140))) {
            if (v.isBaby() || v.getVillagerData().getProfession()
                    != net.minecraft.world.entity.npc.VillagerProfession.CLERIC) {
                continue;
            }
            double mejor = Double.MAX_VALUE;
            String quien = "ninguno";
            for (Villager otro : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(140))) {
                if (otro == v || !otro.isAlive() || otro.getHealth() >= otro.getMaxHealth() * 0.75D) {
                    continue;
                }
                double d = v.distanceToSqr(otro);
                if (d < mejor) {
                    mejor = d;
                    quien = nombreCorto(otro) + " (" + redondo(otro.getHealth()) + "/" + otro.getMaxHealth()
                            + " a " + redondo((float) Math.sqrt(d)) + " bloques)";
                }
            }
            DevilRpg.LOGGER.info("[Arnes] CLERIGO {} vida={}/{} pos={} | herido mas cercano: {} | etiqueta: {}",
                    nombreCorto(v), redondo(v.getHealth()), v.getMaxHealth(),
                    v.blockPosition().toShortString(), quien, etiquetaDe(v));
        }
    }

    /** El nombre del aldeano sin el oficio detrás (lo que va antes del paréntesis). */
    private static String nombreCorto(Villager v) {
        String nombre = v.getName().getString();
        int parentesis = nombre.indexOf(" (");
        return parentesis > 0 ? nombre.substring(0, parentesis) : nombre;
    }

    private static double redondo(float valor) {
        return Math.round(valor * 10.0F) / 10.0D;
    }

    /** La etiqueta que el jugador ve sobre la cabeza del aldeano (nombre y, debajo, lo que está haciendo). */
    private static String etiquetaDe(Villager v) {
        return v.getCustomName() == null ? "(sin etiqueta)" : v.getCustomName().getString().replace("\n", " / ");
    }

    /** Leña al almacén y carne cruda a la despensa: sin eso el cocinero no tiene nada que cocinar (ni con qué quemar). */
    private static void sembrarLaCocina(ServerLevel level) {        var resto = com.chipoodle.devilrpg.world.VillageStorage.guardar(level, CENTRO,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_LOG, 40));
        var despensa = com.chipoodle.devilrpg.world.VillagePantry.despensa(level, CENTRO);
        int carnes = 0;
        for (int i = 0; i < 32; i++) {
            if (com.chipoodle.devilrpg.world.VillagePantry.guardar(despensa,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BEEF, 1)).isEmpty()) {
                carnes++;
            }
        }
        DevilRpg.LOGGER.info("[Arnes] COCINA: sembrados 40 troncos (sobraron {}) y {} carnes crudas en la despensa",
                resto.isEmpty() ? 0 : resto.getCount(), carnes);
    }

    /**
     * <b>¿DÓNDE COCINA EL COCINERO?</b> Se imprime su posición, la distancia a la <b>casilla de la cocina</b> y al
     * <b>ahumador</b>, si <b>VE</b> el ahumador (rayo de colisión: si está fuera del comedor, el rayo choca con la
     * pared) y su <b>ruta</b> a la casilla de la cocina (nodos, si alcanza y dónde acaba). Es la diferencia entre
     * "cocina dentro de la taberna" y "cocina a través de la pared" (el bug que reportó el jugador).
     */
    private static void vigilarCocinero(ServerLevel level) {
        int cota = com.chipoodle.devilrpg.world.VillageGenerator.cotaDeLaPlaza(level, CENTRO);
        BlockPos ahumador = com.chipoodle.devilrpg.world.VillageGenerator.puestoDelCocinero(level, CENTRO);
        BlockPos casilla = new BlockPos(ahumador.getX(), ahumador.getY(), ahumador.getZ() - 1);
        BlockPos base = com.chipoodle.devilrpg.world.VillageGenerator.baseDeLaTaberna(CENTRO);
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(RADIO_CENSO))) {
            if (v.isBaby()
                    || v.getVillagerData().getProfession() != net.minecraft.world.entity.npc.VillagerProfession.BUTCHER) {
                continue;
            }
            Vec3 ojo = new Vec3(v.getX(), v.getY() + 1.0D, v.getZ());
            Vec3 meta = Vec3.atCenterOf(ahumador);
            var choque = level.clip(new net.minecraft.world.level.ClipContext(ojo, meta,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, v));
            boolean ve = choque.getType() == net.minecraft.world.phys.HitResult.Type.MISS
                    || level.getBlockState(choque.getBlockPos()).is(net.minecraft.world.level.block.Blocks.SMOKER);
            boolean dentro = v.getX() >= base.getX() + 1 && v.getX() <= base.getX() + 7
                    && v.getZ() >= base.getZ() + 1 && v.getZ() <= base.getZ() + 12
                    && Math.abs(v.getY() - cota) < 2.0D;
            StringBuilder goals = new StringBuilder();
            for (net.minecraft.world.entity.ai.goal.WrappedGoal w : v.goalSelector.getAvailableGoals()) {
                if (w.isRunning()) {
                    goals.append(w.getGoal().getClass().getSimpleName()).append(' ');
                }
            }
            var wt = v.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
            DevilRpg.LOGGER.info("[Arnes] COCINERO pos={} dentroDeLaTaberna={} VEelAhumador={} dCasilla={} dAhumador={}"
                            + " destino={} goals=[{}] etiqueta={} · casilla={} ruta: {}",
                    v.blockPosition().toShortString(), dentro ? "SI" : "NO", ve ? "SI" : "NO",
                    fmt(Math.sqrt(v.distanceToSqr(casilla.getX() + 0.5D, casilla.getY() + 0.5D, casilla.getZ() + 0.5D))),
                    fmt(Math.sqrt(v.distanceToSqr(meta.x, meta.y, meta.z))),
                    wt == null ? "SIN DESTINO" : wt.getTarget().currentBlockPosition().toShortString(),
                    goals.toString().trim(),
                    v.getCustomName() == null ? "-" : v.getCustomName().getString().replace("\n", " | "),
                    casilla.toShortString(), rutaDetallada(v, casilla));
        }
    }

    /**
     * <b>LA PARED QUE LE FALTABA A LA CASA Y EL COFRE DE AL LADO</b> (reporte del jugador: *"¿qué ves de extraño en
     * esta casa? ¡si le falta completarse a la pared! corrígelo y checa que el cofre no estorbe"*). Imprime el bloque
     * de las DOS celdas del hueco (`1427,120,1392` y `1427,121,1392`), el de sus vecinas (la ventana de al lado y el
     * poste de la esquina) y el <b>cofre</b> de `1428,120,1392` con sus objetos: así se ve en el log el antes (aire)
     * y el después (adoquín) y que el cofre sigue donde estaba y con lo suyo.
     */
    private static void volcarLaParedYElCofre(ServerLevel level) {
        StringBuilder sb = new StringBuilder();
        for (BlockPos p : new BlockPos[]{new BlockPos(1427, 120, 1392), new BlockPos(1427, 121, 1392),
                new BlockPos(1427, 120, 1391), new BlockPos(1427, 121, 1391), new BlockPos(1427, 120, 1393),
                new BlockPos(1428, 120, 1392)}) {
            String nombre = level.getBlockState(p).getBlock().toString()
                    .replace("Block{minecraft:", "").replace("}", "");
            sb.append(' ').append(p.toShortString()).append('=').append(nombre);
        }
        StringBuilder cofre = new StringBuilder();
        if (level.getBlockEntity(new BlockPos(1428, 120, 1392)) instanceof net.minecraft.world.Container c) {
            cofre.append("cofre[").append(c.getContainerSize()).append(" huecos]");
            for (int i = 0; i < c.getContainerSize(); i++) {
                var s = c.getItem(i);
                if (!s.isEmpty()) {
                    cofre.append(' ').append(i).append(':').append(s.getCount()).append('x')
                            .append(s.getItem().toString().replace("Item{minecraft:", "").replace("}", ""));
                }
            }
        } else {
            cofre.append("SIN COFRE (o no es un contenedor)");
        }
        DevilRpg.LOGGER.info("[Arnes] CASA hueco:{} · {}", sb, cofre);
    }

    /**
     * <b>¿QUÉ HAY TIRADO EN LA HUERTA Y QUÉ LLEVA EL GRANJERO ENCIMA?</b> (lo reportó el jugador: *"los granjeros están
     * dejando muchos vegetales en el suelo cuando cosechan"*). Por bancal, imprime cada objeto del suelo (qué es,
     * cuántos, su edad en ticks y la celda) y, por granjero, su posición, el bancal en el que está, su etiqueta y su
     * <b>zurrón</b> (hueco a hueco, con los huecos libres): es lo que dice si lo que se cae es por el TOPE de semillas,
     * por no caberle o porque nadie lo recoge.
     */
    private static void vigilarLaHuerta(ServerLevel level) {
        int cota = com.chipoodle.devilrpg.world.VillageGenerator.cotaDeLaPlaza(level, CENTRO);
        // LA TIERRA, CULTIVO A CULTIVO (reporte del jugador: "otra vez los granjeros están dejando demasiadas
        // parcelas sin cosechar... ya no hay verduras para comer"): cuántas plantas están MADURAS (edad al máximo de
        // su cultivo), cuántas creciendo, cuántas celdas VACÍAS y cuántas PISOTEADAS. Es lo que distingue "el granjero
        // va lento" de "el granjero no está".
        for (int i = 0; i < com.chipoodle.devilrpg.world.VillageGenerator.parcelasDeGranja(); i++) {
            BlockPos esq = com.chipoodle.devilrpg.world.VillageGenerator.esquinaDeLaParcela(CENTRO, i, cota);
            int maduras = 0;
            int creciendo = 0;
            int vacias = 0;
            int pisoteadas = 0;
            int sinTierra = 0;
            int ancho = com.chipoodle.devilrpg.world.VillageGenerator.PLOT_WIDTH;
            int fondo = com.chipoodle.devilrpg.world.VillageGenerator.PLOT_DEPTH;
            for (int dx = 0; dx < ancho; dx++) {
                for (int dz = 0; dz < fondo; dz++) {
                    BlockPos suelo = new BlockPos(esq.getX() + dx, cota - 1, esq.getZ() + dz);
                    BlockPos planta = new BlockPos(esq.getX() + dx, cota, esq.getZ() + dz);
                    var abajo = level.getBlockState(suelo);
                    var arriba = level.getBlockState(planta);
                    if (!abajo.is(net.minecraft.world.level.block.Blocks.FARMLAND)) {
                        if (abajo.is(net.minecraft.world.level.block.Blocks.WATER)) {
                            continue; // la acequia del centro no es celda de cultivo
                        }
                        sinTierra++;
                        continue;
                    }
                    if (arriba.getBlock() instanceof net.minecraft.world.level.block.CropBlock cultivo) {
                        if (com.chipoodle.devilrpg.world.VillageGenerator.edadDelCultivo(arriba)
                                >= cultivo.getMaxAge()) {
                            maduras++;
                        } else {
                            creciendo++;
                        }
                    } else if (arriba.isAir()) {
                        vacias++;
                    } else {
                        pisoteadas++;
                    }
                }
            }
            DevilRpg.LOGGER.info("[Arnes] TIERRA bancal {}: MADURAS {} | creciendo {} | VACIAS {} | pisoteadas {}"
                    + " | sin tierra de cultivo {}", i, maduras, creciendo, vacias, pisoteadas, sinTierra);
        }
        // Y LA COMIDA DE VERDAD: los puntos de la despensa, lo que hay dentro (trigo, semillas, harina, pan,
        // vegetales) y lo que hay en el almacén. Es lo que responde a "ya no hay verduras para comer".
        var despensa = com.chipoodle.devilrpg.world.VillagePantry.despensa(level, CENTRO);
        int trigo = 0;
        int semillas = 0;
        int pan = 0;
        int harina = 0;
        int vegetales = 0;
        if (despensa != null) {
            for (int i = 0; i < despensa.getContainerSize(); i++) {
                var s = despensa.getItem(i);
                if (s.isEmpty()) {
                    continue;
                }
                if (s.is(net.minecraft.world.item.Items.WHEAT)) {
                    trigo += s.getCount();
                } else if (s.is(net.minecraft.world.item.Items.BREAD)) {
                    pan += s.getCount();
                } else if (s.is(net.minecraft.world.item.Items.BONE_MEAL)) {
                    harina += s.getCount();
                } else if (com.chipoodle.devilrpg.world.VillagePantry.esVegetal(s)) {
                    vegetales += s.getCount();
                } else if (s.is(net.minecraft.world.item.Items.WHEAT_SEEDS)
                        || s.is(net.minecraft.world.item.Items.BEETROOT_SEEDS)
                        || s.is(net.minecraft.world.item.Items.CARROT)
                        || s.is(net.minecraft.world.item.Items.POTATO)) {
                    semillas += s.getCount();
                }
            }
        }
        DevilRpg.LOGGER.info("[Arnes] COMIDA: despensa {} punto(s) [trigo {} semillas {} pan {} harina {} vegetales {}]",
                com.chipoodle.devilrpg.world.VillagePantry.comida(level, CENTRO), trigo, semillas, pan, harina,
                vegetales);
        var caja = com.chipoodle.devilrpg.world.VillageStorage.almacen(level, CENTRO);
        int comidaAlmacen = 0;
        if (caja != null) {
            for (int i = 0; i < caja.getContainerSize(); i++) {
                var s = caja.getItem(i);
                if (!s.isEmpty() && (com.chipoodle.devilrpg.world.VillagePantry.esCarneCruda(s)
                        || com.chipoodle.devilrpg.world.VillagePantry.esCarneCocida(s)
                        || com.chipoodle.devilrpg.world.VillagePantry.esVegetal(s)
                        || s.is(net.minecraft.world.item.Items.WHEAT_SEEDS)
                        || s.is(net.minecraft.world.item.Items.BEETROOT_SEEDS))) {
                    comidaAlmacen += s.getCount();
                }
            }
        }
        DevilRpg.LOGGER.info("[Arnes] COMIDA EN EL ALMACEN (carne y semillas): {}", comidaAlmacen);
        // ¿HAY BICHOS DENTRO? Es lo que decide si el LATIDO DEL PUEBLO corre o no (`hayEnemigosDentro`): con uno
        // dentro, `tickVillageLife` se salta ENTERO (no reparte oficios, no da estaciones, no engancha goals) y los
        // goals que ya estuvieran puestos siguen — es la diferencia entre "el granjero no cosecha porque no tiene
        // goal" y "cosecha poco".
        StringBuilder bichos = new StringBuilder();
        int bichosDentro = 0;
        for (net.minecraft.world.entity.Mob m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                new AABB(CENTRO).inflate(80))) {
            if (!(m instanceof net.minecraft.world.entity.monster.Monster)) {
                continue;
            }
            if (Math.sqrt(m.distanceToSqr(CENTRO.getX() + 0.5D, CENTRO.getY() + 0.5D, CENTRO.getZ() + 0.5D))
                    <= com.chipoodle.devilrpg.world.VillageGenerator.FENCE_RADIUS) {
                bichosDentro++;
                bichos.append(' ').append(m.getType().toString().replace("entity.minecraft.", ""))
                        .append('@').append(m.blockPosition().toShortString());
            }
        }
        DevilRpg.LOGGER.info("[Arnes] BICHOS DENTRO DEL RECINTO: {}{}", bichosDentro, bichos);
        // Y LOS GOLEMS: el jugador vio *"un golem dentro de una de las parcelas"* y pidio que no pueda spawnear
        // ninguno ahi. Aqui se cuentan los que estan SOBRE LA HUELLA de un bancal (la misma prueba X/Z que usa el
        // mod en `CommonForgeGolemEventSubscriber` y en el latido): lo que se busca es que el contador sea 0 en
        // TODAS las muestras, con el que venia en el guardado ya sacado a la calle.
        StringBuilder golems = new StringBuilder();
        int golemsDentro = 0;
        for (net.minecraft.world.entity.animal.IronGolem g : level.getEntitiesOfClass(
                net.minecraft.world.entity.animal.IronGolem.class, new AABB(CENTRO).inflate(140))) {
            if (!com.chipoodle.devilrpg.world.VillageGenerator.sobreLaHuellaDeUnBancal(
                    CENTRO, g.blockPosition())) {
                continue;
            }
            golemsDentro++;
            golems.append(' ').append(g.blockPosition().toShortString()).append("(vida=")
                    .append((int) g.getHealth()).append(')');
        }
        DevilRpg.LOGGER.info("[Arnes] GOLEMS DENTRO DE LA HUERTA: {}{}", golemsDentro, golems);
        // Y EL COCINERO, que es quien HORNEA el pan desde la quinta vuelta (el granjero solo deja el trigo): su
        // etiqueta dice lo que esta haciendo y el `COMIDA:` de arriba, cuanto trigo y cuanto pan hay en la despensa.
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(RADIO_CENSO))) {
            if (v.getVillagerData().getProfession()
                    != net.minecraft.world.entity.npc.VillagerProfession.BUTCHER) {
                continue;
            }
            var puntoDeApoyo = com.chipoodle.devilrpg.world.VillageStorage.puntoDeApoyo(level, CENTRO);
            // Y SI EL PUNTO DE APOYO NO TIENE RUTA, ¿TIENE RUTA ALGUNO DE LOS OTROS ACCESOS DEL COBERTIZO? Es lo que
            // decide si el arreglo es "varios accesos del almacen" (como las cuatro compuertas del bancal) o si el
            // problema es que la zona entera esta sellada desde donde esta el aldeano.
            StringBuilder rutasAlAlmacen = new StringBuilder();
            if (puntoDeApoyo != null) {
                for (BlockPos celda : new BlockPos[]{puntoDeApoyo, puntoDeApoyo.offset(0, 0, -2),
                        puntoDeApoyo.offset(0, 0, 2), puntoDeApoyo.offset(-2, 0, 0), puntoDeApoyo.offset(2, 0, 0)}) {
                    rutasAlAlmacen.append(' ').append(celda.toShortString()).append('=')
                            .append(ruta(level, v, celda));
                }
            }
            var destino = v.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
            StringBuilder susGoals = new StringBuilder();
            for (net.minecraft.world.entity.ai.goal.WrappedGoal w : v.goalSelector.getAvailableGoals()) {
                if (w.isRunning()) {
                    susGoals.append(w.getGoal().getClass().getSimpleName()).append(' ');
                }
            }
            DevilRpg.LOGGER.info("[Arnes] COCINERO {} nombre={} pos={} destino={} goals=[{}] rutaAlmacen={}"
                            + " accesosDelAlmacen={} etiqueta={}", uuid8(v),
                    nombreCorto(v), v.blockPosition().toShortString(),
                    destino == null ? "SIN DESTINO" : destino.getTarget().currentBlockPosition().toShortString(),
                    susGoals.toString().trim(),
                    // LA RUTA AL PUNTO DE APOYO DEL ALMACEN: es la pierna de la lena (medida: el cocinero se quedaba
                    // 40 s en 511,63,666 contra la pared de una casa con destino 517,63,666, y acababa aparcandolo).
                    puntoDeApoyo == null ? "-" : rutaDetallada(v, puntoDeApoyo),
                    rutasAlAlmacen.toString().trim(), etiquetaDe(v));
        }
        for (int i = 0; i < com.chipoodle.devilrpg.world.VillageGenerator.parcelasDeGranja(); i++) {
            StringBuilder dentro = new StringBuilder();
            int cuantos = 0;
            for (net.minecraft.world.entity.item.ItemEntity it : level.getEntitiesOfClass(
                    net.minecraft.world.entity.item.ItemEntity.class, new AABB(CENTRO).inflate(140))) {
                if (!com.chipoodle.devilrpg.world.VillageGenerator.estaDentroDeLaParcela(CENTRO, i, cota,
                        it.blockPosition())) {
                    continue;
                }
                cuantos++;
                dentro.append(' ').append(it.getItem().getHoverName().getString()).append('x')
                        .append(it.getItem().getCount()).append("@").append(it.blockPosition().toShortString())
                        .append("(edad ").append(it.tickCount).append(')');
            }
            DevilRpg.LOGGER.info("[Arnes] HUERTA bancal {}: {} objeto(s) en el suelo:{}", i, cuantos, dentro);
        }
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(RADIO_CENSO))) {
            if (v.isBaby()
                    || v.getVillagerData().getProfession() != net.minecraft.world.entity.npc.VillagerProfession.FARMER) {
                continue;
            }
            StringBuilder zurron = new StringBuilder();
            int libres = 0;
            for (int i = 0; i < v.getInventory().getContainerSize(); i++) {
                var s = v.getInventory().getItem(i);
                if (s.isEmpty()) {
                    libres++;
                } else {
                    zurron.append(' ').append(i).append(':').append(s.getCount()).append('x')
                            .append(s.getHoverName().getString());
                }
            }
            int bancal = -1;
            for (int i = 0; i < com.chipoodle.devilrpg.world.VillageGenerator.parcelasDeGranja(); i++) {
                if (com.chipoodle.devilrpg.world.VillageGenerator.estaDentroDeLaParcela(CENTRO, i, cota,
                        v.blockPosition())) {
                    bancal = i;
                }
            }
            StringBuilder goals = new StringBuilder();
            for (net.minecraft.world.entity.ai.goal.WrappedGoal w : v.goalSelector.getAvailableGoals()) {
                if (w.isRunning()) {
                    goals.append(w.getGoal().getClass().getSimpleName()).append(' ');
                }
            }
            var wt = v.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
            var sitio = v.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
            var posible = v.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).orElse(null);
            DevilRpg.LOGGER.info("[Arnes] GRANJERO {} nombre={} pos={} bancal={} huecosLibres={}/{} destino={}"
                            + " goals=[{}] job={} potencial={} guardia={}/{} zurron:{} etiqueta={}",
                    uuid8(v), nombreCorto(v), v.blockPosition().toShortString(), bancal, libres,
                    v.getInventory().getContainerSize(),
                    wt == null ? "SIN DESTINO" : wt.getTarget().currentBlockPosition().toShortString(),
                    goals.toString().trim(),
                    sitio == null ? "SIN PUESTO" : sitio.pos().toShortString(),
                    posible == null ? "-" : posible.pos().toShortString(),
                    v.getPersistentData().getBoolean(VillageManager.GUARD_TAG) ? "SI" : "no",
                    com.chipoodle.devilrpg.entity.goal.VillagerGuardGoal.esGuardia(v) ? "SI" : "no",
                    zurron, etiquetaDe(v));
        }
        // LOS COMPOSTEROS (el puesto del granjero): la celda, si es un punto de interes registrado y quien lo tiene.
        var poi = level.getPoiManager();
        for (int i = 0; i < com.chipoodle.devilrpg.world.VillageGenerator.parcelasDeGranja(); i++) {
            BlockPos comp = com.chipoodle.devilrpg.world.VillageGenerator.composteroDeLaParcela(CENTRO, i, cota);
            StringBuilder quien = new StringBuilder();
            for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(RADIO_CENSO))) {
                var s = v.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
                if (s != null && s.pos().equals(comp)) {
                    quien.append(' ').append(nombreCorto(v));
                }
            }
            DevilRpg.LOGGER.info("[Arnes] COMPOSTERO bancal {} en {} bloque={} poi={} dueno(s):{}", i,
                    comp.toShortString(), nombre(level, comp.getX(), comp.getY(), comp.getZ()),
                    poi.getType(comp).isPresent() ? "SI" : "NO", quien.length() == 0 ? " NADIE" : quien.toString());
        }
    }

    /** La columna (x,z) de cada aldeano en el barrido anterior, y cuantos barridos lleva sin cambiarla. */
    private static final java.util.Map<String, String> columnaAnterior = new java.util.HashMap<>();
    private static final java.util.Map<String, Integer> quietoBarridos = new java.util.HashMap<>();

    /**
     * <b>EL QUE NO SE MUEVE DE SU COLUMNA</b> (el "atrapado en casa" que mide
     * {@code VillageManager.rescatarAldeanosAtrapados}): cada 2 s, por cada aldeano adulto que <b>no</b> esté en un
     * bancal y tenga los pies <b>por encima de la cota</b> (o sea, fuera de la altura de la calle), imprime su celda
     * <b>horizontal</b>, la Y exacta, los bloques de sus pies y de su cabeza, si está <b>durmiendo</b>, las
     * <b>actividades</b> de su cerebro, su {@code WALK_TARGET}, sus goals corriendo y su etiqueta; y si lleva
     * {@code QUIETO=n} barridos en la misma columna, con la ruta a la plaza.
     * <p>
     * Es la sonda que dice si el rescate va a dispararse: el rescate cuenta <b>celdas X/Z</b> (a propósito: contando la
     * Y, un aldeano que <b>bota</b> en una escalera reinicia el contador y no se le rescata nunca — es lo que se midió
     * con Ursula en 428,669 botando entre y=65 y y=67 con su bancal lleno de matas maduras).
     */
    private static void vigilarAtrapados(ServerLevel level) {
        int cota = com.chipoodle.devilrpg.world.VillageGenerator.cotaDeLaPlaza(level, CENTRO);
        BlockPos plaza = com.chipoodle.devilrpg.world.VillageManager.casillaDeLaCalle(level, CENTRO);
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(RADIO_CENSO))) {
            if (v.isBaby() || v.isSleeping()
                    || v.getBrain().isActive(Activity.REST) || v.getY() <= cota + 0.6D) {
                continue;
            }
            boolean enBancal = false;
            for (int i = 0; i < com.chipoodle.devilrpg.world.VillageGenerator.parcelasDeGranja(); i++) {
                if (com.chipoodle.devilrpg.world.VillageGenerator.estaDentroDeLaParcela(CENTRO, i, cota,
                        v.blockPosition())) {
                    enBancal = true;
                }
            }
            if (enBancal) {
                continue; // dentro de su bancal: eso no es estar atrapado (y su rescate no debe sacarlo de ahi)
            }
            String uuid = uuid8(v);
            String columna = v.blockPosition().getX() + "," + v.blockPosition().getZ();
            String antes = columnaAnterior.put(uuid, columna);
            // OJO: nada de `merge`/`put` dentro de un ternario: `put` devuelve el valor ANTERIOR (null la primera vez)
            // y al desencajarlo revienta el servidor entero (medido: NPE en la primera pasada y crash del arnes).
            int quieto;
            if (antes != null && antes.equals(columna)) {
                quieto = quietoBarridos.getOrDefault(uuid, 0) + 1;
            } else {
                quieto = 0;
            }
            quietoBarridos.put(uuid, quieto);
            BlockPos pies = v.blockPosition();
            StringBuilder goals = new StringBuilder();
            for (net.minecraft.world.entity.ai.goal.WrappedGoal w : v.goalSelector.getAvailableGoals()) {
                if (w.isRunning()) {
                    goals.append(w.getGoal().getClass().getSimpleName()).append(' ');
                }
            }
            StringBuilder acts = new StringBuilder();
            for (Activity a : v.getBrain().getActiveActivities()) {
                acts.append(a.getName()).append(' ');
            }
            var wt = v.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
            var home = v.getBrain().getMemory(MemoryModuleType.HOME);
            DevilRpg.LOGGER.info("[Arnes] ATRAPADO {} {} columna={} y={} QUIETO={} pies={} cabeza={} durmiendo={}"
                            + " home={} actividades=[{}] destino={} goals=[{}] plaza={} rutaAPlaza={} etiqueta={}",
                    uuid, str(v.getVillagerData().getProfession()), columna, fmt(v.getY()), quieto,
                    nombre(level, pies.getX(), pies.getY(), pies.getZ()),
                    nombre(level, pies.getX(), pies.getY() + 1, pies.getZ()), v.isSleeping(),
                    home.map(g -> g.pos().toShortString()).orElse("NINGUNA"), acts.toString().trim(),
                    wt == null ? "SIN DESTINO" : wt.getTarget().currentBlockPosition().toShortString(),
                    goals.toString().trim(), plaza.toShortString(), rutaDetallada(v, plaza), etiquetaDe(v));
        }
    }

    /**
     * Le pone <b>4 troncos en el zurron al cocinero</b> (solo si no le queda ninguno: la siembra se repite cada 30 s,
     * así que no se acumulan). El porqué está en la llamada: en esta aldea la <b>ida al almacén está cortada</b> por la
     * geometría (medido: `rutaAlmacen=a1=15n alcance=NO fin=511,63,666 dFin=6.00`), así que sin esta siembra el
     * cocinero no llega nunca a encender el ahumador y <b>no se puede medir lo que se quiere medir</b>: que el pan lo
     * hornea él. Lo que se mide es el horno (el ahumador, la receta y el tope de la reserva), no la pierna de la leña.
     */
    private static void sembrarLenaAlCocinero(ServerLevel level) {
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(RADIO_CENSO))) {
            if (v.isBaby() || v.getVillagerData().getProfession()
                    != net.minecraft.world.entity.npc.VillagerProfession.BUTCHER) {
                continue;
            }
            boolean yaTiene = false;
            int hueco = -1;
            for (int i = 0; i < v.getInventory().getContainerSize(); i++) {
                var s = v.getInventory().getItem(i);
                if (com.chipoodle.devilrpg.world.VillageStorage.esLena(s)) {
                    yaTiene = true;
                } else if (s.isEmpty() && hueco < 0) {
                    hueco = i;
                }
            }
            if (yaTiene || hueco < 0) {
                continue;
            }
            v.getInventory().setItem(hueco,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_LOG, 4));
            DevilRpg.LOGGER.info("[Arnes] SEMBRADO: 4 troncos en el zurron del cocinero {} ({}) para poder medir el"
                    + " horno del pan", uuid8(v), v.blockPosition().toShortString());
        }
    }

    /** Le repone <b>16 de trigo</b> a la despensa (el porqué está en la llamada: medir el horno, no el ritmo de la huerta). */
    private static void sembrarTrigoEnLaDespensa(ServerLevel level) {
        var despensa = com.chipoodle.devilrpg.world.VillagePantry.despensa(level, CENTRO);
        if (despensa == null) {
            return;
        }
        com.chipoodle.devilrpg.world.VillagePantry.guardar(despensa,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WHEAT, 16));
        DevilRpg.LOGGER.info("[Arnes] SEMBRADO: 16 de trigo en la despensa (para poder medir el HORNO del pan:"
                + " quedan {} de trigo)", com.chipoodle.devilrpg.world.VillagePantry.contar(despensa,
                s -> s.is(net.minecraft.world.item.Items.WHEAT)));
    }

    private static FakePlayer preparar(ServerLevel level, net.minecraft.server.MinecraftServer server) {
        int cx = CENTRO.getX() >> 4;
        int cz = CENTRO.getZ() >> 4;
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                level.setChunkForced(cx + dx, cz + dz, true);
            }
        }
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        // LA HORA SE FIJA SEGUN LO QUE SE MIDA: NOCHE (18000 = medianoche) para el sueño y las camas, DIA (6000 =
        // mediodia) para el trabajo, el combustible y el CIERRE DE PUERTAS (los aldeanos tienen que salir y cruzar).
        level.setDayTime(MEDIR_NOCHE && !MEDIR_PUERTAS ? 18000L : 6000L);
        // Sin bichos: la ronda se mide sola (el combate va antes que la ronda y los guardias se morian peleando).
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        for (net.minecraft.world.entity.Mob m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                new AABB(CENTRO).inflate(160))) {
            if (m instanceof net.minecraft.world.entity.monster.Monster) {
                m.discard();
            }
        }
        FakePlayer pega = FakePlayerFactory.getMinecraft(level);
        pega.moveTo(CENTRO.getX() + 0.5D, CENTRO.getY(), CENTRO.getZ() + 0.5D);
        DevilRpg.LOGGER.info("[Arnes] ALDEA 2 centro={} aldeanos={} gameTime={} ancla={}", CENTRO,
                level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(96)).size(), level.getGameTime(),
                ancla());
        return pega;
    }

    /** El ancla del jugador que hace que el objetivo 2 caiga en el centro de la aldea (misma cuenta que el mod). */
    private static Vec3 ancla() {
        Random rnd = new Random(0x5DEECE66DL + INDICE);
        double distancia = 800 + INDICE * 600 + rnd.nextDouble() * 400;
        double rad = Math.toRadians(45.0D);
        return new Vec3(CENTRO.getX() - Math.cos(rad) * distancia, CENTRO.getY(),
                CENTRO.getZ() - Math.sin(rad) * distancia);
    }

    /** Se hace UNA vez (el escenario de los revelados), no en cada volcado. */
    private static boolean escenarioDeReveladosHecho = false;

    /**
     * LOS TRES TEXTOS DE LA BARRA y LOS DOS REVELADOS, medidos de verdad (I87):
     * <ol>
     *   <li>los tres textos que decide {@code VillageBarText} (oculta / sin nombre / con nombre);</li>
     *   <li>el <b>clérigo</b> al vencer el asedio: revela la SIGUIENTE y dice quién habla (nombre de un clérigo real
     *       del pueblo, de los que el arnés tiene censados);</li>
     *   <li>la <b>piedra de invocación</b>: revela el objetivo actual;</li>
     *   <li>y que los dos son <b>idempotentes</b> (repetirlos no cambia nada).</li>
     * </ol>
     * Se llama al objetivo 3 (que NO está en el guardado) y al 5, para que ninguno venga ya revelado por la siembra.
     */
    private static void probarLosRevelados(ServerLevel level, FakePlayer pega,
                                           com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface aux) {
        // EL JUGADOR DE PEGA NO TRAE ANCLA (el arnés se la pasa por parámetro al latido, no vive en su capability):
        // se la ponemos, que es lo que hace el mod al entrar al mundo. Sin ancla, la PIEDRA no puede calcular dónde
        // cae la aldea y no revela nada — lo cazó la primera corrida de esta medida ("PIEDRA despues:
        // revelada(5)=false"), y por eso la piedra ahora deja un WARN en el log cuando le pasa.
        aux.setAnchorPoint(ancla(), pega);
        aux.setSpawnPoint(ancla(), pega);
        DevilRpg.LOGGER.info("[Arnes] ancla del jugador de pega puesta en la capability: {}", ancla());

        DevilRpg.LOGGER.info("[Arnes] BARRA oculta      -> {}",
                String.valueOf(com.chipoodle.devilrpg.survival.VillageBarText.texto(3, false, false, 1234, "->")));
        DevilRpg.LOGGER.info("[Arnes] BARRA revelada    -> {}",
                com.chipoodle.devilrpg.survival.VillageBarText.texto(3, false, true, 1234, "->"));
        DevilRpg.LOGGER.info("[Arnes] BARRA descubierta -> {}",
                com.chipoodle.devilrpg.survival.VillageBarText.texto(3, true, true, 12, "arriba"));

        // LA TABLA DE NOMBRES ENTERA (la del jugador, con su lore): verifica de una vez el ORDEN y, sobre todo, la
        // CODIFICACION — estas lineas llevan los acentos (Raíz, Rocío, Florumbría, Páramo, Lúgubria…) y se comprueban
        // luego a nivel de bytes en el log, que es lo unico que distingue un acento bien puesto de un destrozo.
        for (int i = 0; i < com.chipoodle.devilrpg.survival.VillageNames.cuantos() + 2; i++) {
            DevilRpg.LOGGER.info("[Arnes] NOMBRE aldea {} = \"{}\"", i,
                    com.chipoodle.devilrpg.survival.VillageNames.nombre(i));
        }

        // 1) EL CLERIGO YA NO REVELA AL VENCER: ahora eso es al HABLAR con él (ver `probarElLibroYElClerigo`, que lo
        // mide con el método nuevo `VillageManager.elClerigoSenalaLaAldeaActual`). Aquí solo se deja constancia.
        aux.setObjectiveIndex(3, pega);
        DevilRpg.LOGGER.info("[Arnes] CLERIGO: al vencer un asedio ya NO se revela nada (revelada(3)={}); la"
                + " direccion la da HABLAR con el clerigo", aux.isAldeaRevelada(3));

        // 2) LA PIEDRA DE INVOCACION, con un objetivo que no venga revelado (el 5).
        aux.setObjectiveIndex(5, pega);
        DevilRpg.LOGGER.info("[Arnes] PIEDRA antes: revelada(5)={}", aux.isAldeaRevelada(5));
        try {
            com.chipoodle.devilrpg.block.LoreStoneBlock.revelarLaAldeaDeLaPiedra(pega);
        } catch (Exception e) {
            DevilRpg.LOGGER.warn("[Arnes] PIEDRA: el mensaje al jugador de pega fallo ({})", e.toString());
        }
        DevilRpg.LOGGER.info("[Arnes] PIEDRA despues: revelada(5)={} barra=\"{}\"", aux.isAldeaRevelada(5),
                com.chipoodle.devilrpg.survival.VillageBarText.texto(5, aux.isAldeaVisitada(5),
                        aux.isAldeaRevelada(5), 1234, "->"));
        // Y EL CASO CONTRARIO: la piedra con un objetivo que YA está revelado solo confirma (no cambia nada).
        try {
            com.chipoodle.devilrpg.block.LoreStoneBlock.revelarLaAldeaDeLaPiedra(pega);
        } catch (Exception e) {
            DevilRpg.LOGGER.warn("[Arnes] PIEDRA (2a vez): {}", e.toString());
        }
        aux.setObjectiveIndex(0, pega);
        DevilRpg.LOGGER.info("[Arnes] el escenario de revelados termina; el Diario NO cambia (solo lo visitado): {}",
                aux.getAldeasVisitadas());
    }

    /**
     * LA ETIQUETA ES SOLO DE LOS ALDEANOS DEL MOD (lo pidió el jugador: *"las villas normales (vanilla) tienen a sus
     * aldeanos con las mismas etiquetas que la villa de mi mod… eso sólo es para los aldeanos del mod"*). Se mide:
     * <ol>
     *   <li>cuántos aldeanos de la aldea 2 están marcados como del pueblo y cuántos llevan etiqueta;</li>
     *   <li>que a uno al que se le QUITA la marca (como un aldeano de vanilla) se le borre la etiqueta y no se le
     *       vuelva a poner, y que al devolvérsela la recupere;</li>
     *   <li>el estado de los aldeanos que andan LEJOS de las aldeas del mod (aldeas de vanilla) y qué pasa cuando el
     *       jugador pasa a su lado: la etiqueta que el mod les hubiera puesto tiene que desaparecer.</li>
     * </ol>
     */
    private static void probarLasEtiquetas(ServerLevel level, FakePlayer pega) {
        var cerca = level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(96.0D));
        int marcados = 0;
        int etiquetados = 0;
        for (Villager v : cerca) {
            if (VillageManager.esDelPueblo(v)) {
                marcados++;
            }
            if (v.getCustomName() != null) {
                etiquetados++;
            }
        }
        DevilRpg.LOGGER.info("[Arnes] ETIQUETAS aldea 2: {} aldeano(s), {} del pueblo (marcados), {} con etiqueta",
                cerca.size(), marcados, etiquetados);
        if (!cerca.isEmpty()) {
            Villager uno = cerca.get(0);
            DevilRpg.LOGGER.info("[Arnes] ETIQUETAS ejemplo ANTES: delPueblo={} etiqueta=\"{}\"",
                    VillageManager.esDelPueblo(uno), etiquetaDe(uno));
            uno.getPersistentData().putBoolean(VillageManager.DEL_PUEBLO_TAG, false);
            VillageManager.refrescarEtiquetas(level, pega);
            DevilRpg.LOGGER.info("[Arnes] ETIQUETAS ejemplo SIN la marca: delPueblo={} etiqueta=\"{}\" (tiene que"
                    + " quedar vacia)", VillageManager.esDelPueblo(uno), etiquetaDe(uno));
            uno.getPersistentData().putBoolean(VillageManager.DEL_PUEBLO_TAG, true);
            uno.getPersistentData().putLong("DevilRpgActividadTick", 0L);
            uno.getPersistentData().putBoolean(VillageManager.ACTIVIDAD_TAG, false);
            VillageManager.refrescarEtiquetas(level, pega);
            DevilRpg.LOGGER.info("[Arnes] ETIQUETAS ejemplo CON la marca: delPueblo={} etiqueta=\"{}\"",
                    VillageManager.esDelPueblo(uno), etiquetaDe(uno));
        }
        // Y LOS DE FUERA (aldeas de vanilla): cuántos hay, cuántos llevan etiqueta del mod (de antes del arreglo) y
        // qué pasa cuando el jugador se pone a su lado y se refrescan las etiquetas.
        Villager fuera = null;
        int cuantos = 0;
        int fueraEtiquetados = 0;
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(4000.0D))) {
            boolean deAlguna = false;
            for (int i = 0; i <= 3; i++) {
                BlockPos c = VillageManager.centroDe(level, i);
                if (c != null && c.distSqr(v.blockPosition()) < 200.0D * 200.0D) {
                    deAlguna = true;
                    break;
                }
            }
            if (deAlguna) {
                continue;
            }
            cuantos++;
            if (v.getCustomName() != null) {
                fueraEtiquetados++;
                if (fuera == null) {
                    fuera = v;
                }
            }
        }
        DevilRpg.LOGGER.info("[Arnes] ETIQUETAS FUERA de las aldeas del mod: {} aldeano(s), {} con etiqueta del mod"
                + " (de antes del arreglo)", cuantos, fueraEtiquetados);
        if (fuera != null) {
            DevilRpg.LOGGER.info("[Arnes] ETIQUETAS uno de fuera ANTES: pos={} delPueblo={} etiqueta=\"{}\"",
                    fuera.blockPosition(), VillageManager.esDelPueblo(fuera), etiquetaDe(fuera));
            pega.moveTo(fuera.getX(), fuera.getY(), fuera.getZ());
            VillageManager.refrescarEtiquetas(level, pega);
            DevilRpg.LOGGER.info("[Arnes] ETIQUETAS uno de fuera DESPUES de pasar el jugador: delPueblo={} etiqueta="
                    + "\"{}\" (tiene que quedar vacia)", VillageManager.esDelPueblo(fuera), etiquetaDe(fuera));
            pega.moveTo(CENTRO.getX() + 0.5D, CENTRO.getY(), CENTRO.getZ() + 0.5D);
        }
    }

    /** ¿Se mide EL EQUIPO DE LA GUARDIA (I94)? Ver {@link #medirElEquipoDeLaGuardia}. */
    private static final boolean MEDIR_EQUIPO = false;

    /**
     * EL EQUIPO DE LA GUARDIA (I94): siembra el <b>almacén</b> con piezas de prueba —una espada de hierro <b>normal</b> y
     * una de oro <b>encantada</b> (Filo V), un casco de diamante <b>normal</b> y uno de cuero <b>encantado</b>
     * (Protección IV), un arco encantado (Potencia III), un escudo y flechas— y marca la <b>revisión diaria</b> como
     * pendiente en todos los guardias (la marca es el día de juego y en una corrida corta no cambia). Lo que se mide:
     * quién coge qué (<b>los encantados tienen prioridad</b>), que el que se cambia <b>deja lo viejo en el almacén</b> y
     * que el que no tenía arma se arma.
     */
    private static void medirElEquipoDeLaGuardia(ServerLevel level, FakePlayer pega) {
        if (ticks == 200) {
            var almacen = com.chipoodle.devilrpg.world.VillageStorage.almacen(level, CENTRO);
            if (almacen == null) {
                DevilRpg.LOGGER.warn("[Arnes] EQUIPO: no hay almacen en {}", CENTRO);
                return;
            }
            int guardias = 0;
            for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(120))) {
                if (com.chipoodle.devilrpg.entity.goal.VillagerGuardGoal.esGuardia(v)) {
                    v.getPersistentData().putLong("DevilRpgEquipoRevisado", 0L);
                    guardias++;
                }
            }
            com.chipoodle.devilrpg.world.VillagePantry.guardar(almacen, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
            net.minecraft.world.item.ItemStack espadaEncantada = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_SWORD);
            espadaEncantada.enchant(level.registryAccess().holderOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS), 5);
            com.chipoodle.devilrpg.world.VillagePantry.guardar(almacen, espadaEncantada);
            com.chipoodle.devilrpg.world.VillagePantry.guardar(almacen, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_HELMET));
            net.minecraft.world.item.ItemStack cascoEncantado = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEATHER_HELMET);
            cascoEncantado.enchant(level.registryAccess().holderOrThrow(net.minecraft.world.item.enchantment.Enchantments.PROTECTION), 4);
            com.chipoodle.devilrpg.world.VillagePantry.guardar(almacen, cascoEncantado);
            net.minecraft.world.item.ItemStack arcoEncantado = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOW);
            arcoEncantado.enchant(level.registryAccess().holderOrThrow(net.minecraft.world.item.enchantment.Enchantments.POWER), 3);
            com.chipoodle.devilrpg.world.VillagePantry.guardar(almacen, arcoEncantado);
            com.chipoodle.devilrpg.world.VillagePantry.guardar(almacen, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW, 32));
            com.chipoodle.devilrpg.world.VillagePantry.guardar(almacen, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHIELD));
            DevilRpg.LOGGER.info("[Arnes] EQUIPO: almacen sembrado (espada hierro NORMAL, espada oro FILO V, casco"
                    + " diamante NORMAL, casco cuero PROTECCION IV, arco POTENCIA III, escudo, 32 flechas) con {}"
                    + " guardia(s) y la revision diaria PENDIENTE", guardias);
        }
        if (ticks % 200 != 0 || ticks < 200) {
            return;
        }
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(120))) {
            if (!com.chipoodle.devilrpg.entity.goal.VillagerGuardGoal.esGuardia(v)) {
                continue;
            }
            DevilRpg.LOGGER.info("[Arnes] EQUIPO t={} GUARDIA {} mano=[{}] escudo=[{}] casco=[{}] peto=[{}] grebas=[{}]"
                            + " botas=[{}] revisado={}", ticks, v.getUUID().toString().substring(0, 8),
                    comoSeVe(v.getMainHandItem()), comoSeVe(v.getOffhandItem()),
                    comoSeVe(v.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD)),
                    comoSeVe(v.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST)),
                    comoSeVe(v.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS)),
                    comoSeVe(v.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET)),
                    v.getPersistentData().getLong("DevilRpgEquipoRevisado"));
        }
        var almacen = com.chipoodle.devilrpg.world.VillageStorage.almacen(level, CENTRO);
        if (almacen != null) {
            DevilRpg.LOGGER.info("[Arnes] EQUIPO t={} ALMACEN: espadaHierro={} espadaOroEncantada={} cascoDiamante={}"
                            + " cascoCueroEncantado={} arcoEncantado={} escudo={} cuero={} hierro={}", ticks,
                    cuenta(almacen, net.minecraft.world.item.Items.IRON_SWORD, false), cuenta(almacen, net.minecraft.world.item.Items.GOLDEN_SWORD, true),
                    cuenta(almacen, net.minecraft.world.item.Items.DIAMOND_HELMET, false), cuenta(almacen, net.minecraft.world.item.Items.LEATHER_HELMET, true),
                    cuenta(almacen, net.minecraft.world.item.Items.BOW, true), cuenta(almacen, net.minecraft.world.item.Items.SHIELD, false),
                    cuenta(almacen, net.minecraft.world.item.Items.LEATHER_HELMET, false), cuenta(almacen, net.minecraft.world.item.Items.IRON_SWORD, false));
        }
    }

    /** Un objeto, con su nombre y una "(E)" si va encantado (o "-" si está vacío). */
    private static String comoSeVe(net.minecraft.world.item.ItemStack s) {
        return s.isEmpty() ? "-" : s.getHoverName().getString() + (s.isEnchanted() ? "(E)" : "");
    }

    /** Cuántas unidades de ese objeto hay en el contenedor, contando solo lo encantado ({@code true}) o solo lo normal. */
    private static int cuenta(net.minecraft.world.Container c, net.minecraft.world.item.Item item, boolean encantado) {
        return com.chipoodle.devilrpg.world.VillagePantry.contar(c, s -> s.is(item) && s.isEnchanted() == encantado);
    }

    /** El asaltante de la medida del muro y la última línea que se ha volcado (para no repetir). */
    private static com.chipoodle.devilrpg.entity.AggressiveZombieEntity asaltante = null;
    private static String ultimaLineaDelMuro = "";
    /** El rumbo por el que se asalta (radianes, 0 = +X = este): el que TENGA muralla de verdad. */
    private static double rumboDelAsalto = 0.0D;

    /**
     * EL ASALTO A LA MURALLA (I89): ver {@link #MEDIR_MURO}.
     * <p>
     * La primera corrida de esta medida (aldea del jugador) no midió nada, y por dos motivos que están arreglados aquí:
     * el asaltante se ponía <b>siempre al este</b> y en el este, a la altura del suelo, <b>no hay muralla</b> (solo una
     * valla de bancal en r=59); y aparecía a la Y de la cota, que en ese punto es <b>dentro del terreno</b>, así que se
     * asfixiaba en los primeros segundos (una sola línea volcada y el asaltante quieto los 200 s). Ahora se busca el
     * rumbo con mampostería, se aparece en el suelo con {@code VillageGenerator.spawnY} y se volca su IA (vivo, sin IA,
     * marcha activa, ruta, objetivo y goals) junto a la columna de la muralla capa a capa.
     */
    private static void medirElAsaltoAlMuro(ServerLevel level, FakePlayer pega) {
        int cota = com.chipoodle.devilrpg.world.VillageGenerator.cotaDeLaPlaza(level, CENTRO);
        if (ticks == 300 && asaltante == null) {
            int mejorRumbo = 0;
            int mejorSolidos = -1;
            for (int k = 0; k < 8; k++) {
                double ang = k * Math.PI / 4.0D;
                int solidos = 0;
                for (int r = 56; r <= 66; r++) {
                    int x = CENTRO.getX() + (int) Math.round(Math.cos(ang) * r);
                    int z = CENTRO.getZ() + (int) Math.round(Math.sin(ang) * r);
                    for (int y = cota; y <= cota + 5; y++) {
                        if (!level.getBlockState(new BlockPos(x, y, z)).isAir()) {
                            solidos++;
                        }
                    }
                }
                DevilRpg.LOGGER.info("[Arnes] MURO rumbo {} ({} grados): {} bloque(s) en el anillo r=56..66,"
                        + " y=cota..cota+5", k, k * 45, solidos);
                if (solidos > mejorSolidos) {
                    mejorSolidos = solidos;
                    mejorRumbo = k;
                }
            }
            rumboDelAsalto = mejorRumbo * Math.PI / 4.0D;
            int x = CENTRO.getX() + (int) Math.round(Math.cos(rumboDelAsalto) * 14);
            int z = CENTRO.getZ() + (int) Math.round(Math.sin(rumboDelAsalto) * 14);
            int y = com.chipoodle.devilrpg.world.VillageGenerator.spawnY(level, x, z);
            asaltante = com.chipoodle.devilrpg.init.ModEntities.AGGRESSIVE_ZOMBIE.get().create(level);
            if (asaltante != null) {
                asaltante.moveTo(x + 0.5D, y, z + 0.5D, 0.0F, 0.0F);
                asaltante.setVillageCenter(new BlockPos(CENTRO.getX(), y, CENTRO.getZ()));
                asaltante.setGoToCenterActive(true);
                asaltante.recargarTunel();
                asaltante.setPersistenceRequired();
                level.addFreshEntity(asaltante);
                // CON OBJETIVO: es lo que hace que `BreakBlockGoal` entre en juego (su `canUse` pide objetivo). Sin
                // objetivo, el asaltante solo puede taladrar por la marcha (`MoveToVillageCenterGoal`), y solo cuando
                // su navegación se declara "hecha": medido, anduvo del radio 66 al 58 y se quedó ahí los 135 s sin
                // romper un bloque. El jugador de pega es el objetivo y va invulnerable (la medida no es el combate).
                pega.setInvulnerable(true);
                asaltante.setTarget(pega);
                // MURO INTERIOR DE PRUEBA (r=30, DENTRO de la aldea): es lo que separa las dos mitades de la regla. Se
                // levanta un muro de piedra delante de cada asaltante y se ve QUIÉN lo pica:
                //   - el asaltante SIN aldea (worldSiegeIndex < 0) es el ASEDIO INICIAL: tiene que picarlo (la aldea
                //     todavía no tiene campo de fuerza);
                //   - el de la aldea 0 (GANADA, con campo de fuerza): no puede picar NADA de dentro.
                cercoDeLaPlaza(level, y);
                // SIN TESTIGOS: se quitan los aldeanos y los golems del pueblo (es una COPIA) para que el único objetivo
                // posible sea el jugador de pega. Medido: con los aldeanos dentro, el asaltante se iba detrás de uno de
                // ellos (llegó a r=67 del centro, FUERA del pueblo) y no llegaba ni a acercarse al cerco de la plaza.
                for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(120))) {
                    v.discard();
                }
                for (net.minecraft.world.entity.animal.IronGolem golem : level.getEntitiesOfClass(
                        net.minecraft.world.entity.animal.IronGolem.class, new AABB(CENTRO).inflate(120))) {
                    golem.discard();
                }

                asaltanteDos = com.chipoodle.devilrpg.init.ModEntities.AGGRESSIVE_ZOMBIE.get().create(level);
                if (asaltanteDos != null) {
                    int x2 = CENTRO.getX() + (int) Math.round(Math.cos(ANGULO_DEL_SEGUNDO) * 14);
                    int z2 = CENTRO.getZ() + (int) Math.round(Math.sin(ANGULO_DEL_SEGUNDO) * 14);
                    int y2 = com.chipoodle.devilrpg.world.VillageGenerator.spawnY(level, x2, z2);
                    asaltanteDos.moveTo(x2 + 0.5D, y2, z2 + 0.5D, 0.0F, 0.0F);
                    asaltanteDos.setVillageCenter(new BlockPos(CENTRO.getX(), y2, CENTRO.getZ()));
                    asaltanteDos.setGoToCenterActive(true);
                    asaltanteDos.recargarTunel();
                    asaltanteDos.setPersistenceRequired();
                    asaltanteDos.setWorldSiegeIndex(0); // la aldea 0 del jugador: YA GANADA -> con campo de fuerza
                    level.addFreshEntity(asaltanteDos);
                    asaltanteDos.setTarget(pega);
                    DevilRpg.LOGGER.info("[Arnes] MURO: 2º asaltante (aldea 0 = GANADA, con campo de fuerza) en {} con"
                            + " muro interior delante: NO deberia poder picarlo", asaltanteDos.blockPosition());
                }
                DevilRpg.LOGGER.info("[Arnes] MURO: los dos muros interiores de prueba (r=30, 12x3) levantados");
                DevilRpg.LOGGER.info("[Arnes] MURO: asaltante puesto en {} (cota {}, rumbo {} con {} bloque(s)),"
                                + " CON objetivo (el jugador de pega) y marchando al centro {}", asaltante.blockPosition(),
                        cota, mejorRumbo, mejorSolidos, CENTRO);
            }
        }
        if (asaltante == null) {
            return;
        }
        // El asaltante va a por el jugador de pega (que está en la plaza, dentro): lo que se mide es si ABRE BRECHA.
        asaltante.setTarget(pega);
        if (ticks % 60 != 0) {
            return;
        }
        StringBuilder goals = new StringBuilder();
        for (net.minecraft.world.entity.ai.goal.WrappedGoal w : asaltante.goalSelector.getAvailableGoals()) {
            goals.append(w.getPriority()).append(':').append(w.getGoal().getClass().getSimpleName());
            goals.append(w.isRunning() ? "* " : " ");
        }
        DevilRpg.LOGGER.info("[Arnes] MURO t={} vivo={} noAi={} pos={} dCentro={} dObjetivo={} nav={} navHecha={}"
                        + " goals=[{}]", ticks, asaltante.isAlive(), asaltante.isNoAi(), asaltante.blockPosition(),
                (int) Math.sqrt(asaltante.distanceToSqr(CENTRO.getX(), asaltante.getY(), CENTRO.getZ())),
                (int) Math.sqrt(asaltante.distanceToSqr(pega.getX(), pega.getY(), pega.getZ())),
                asaltante.getNavigation().getTargetPos(), asaltante.getNavigation().isDone(),
                goals.toString().trim());
        // LO QUE TIENE ALREDEDOR: la rejilla de 3x3 a la altura de los pies y de la cabeza, con el nombre del bloque.
        // Es lo que dice CONTRA QUÉ se está dando de bruces (una casa del pueblo, la valla del bancal o el muro).
        StringBuilder rededor = new StringBuilder();
        BlockPos zp = asaltante.blockPosition();
        for (int dy = 0; dy <= 1; dy++) {
            rededor.append("y").append(zp.getY() + dy).append(':');
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    var bs = level.getBlockState(new BlockPos(zp.getX() + dx, zp.getY() + dy, zp.getZ() + dz));
                    String nombre = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                            .getKey(bs.getBlock()).getPath().replace("minecraft:", "");
                    rededor.append(bs.isAir() ? "." : nombre).append(' ');
                }
            }
            rededor.append('|');
        }
        DevilRpg.LOGGER.info("[Arnes] MURO t={} al lado del asaltante: {}", ticks, rededor.toString());
        // LA BANDA DE LA MURALLA (r 57..67, las seis capas de cota a cota+5): cuántos bloques hay en cada capa. Si la
        // cuenta BAJA, hay BRECHA. Se cuenta toda la banda (no una línea) porque el asaltante se desvía del rumbo:
        // medido, acabó 10 bloques al sur de la línea que se estaba mirando.
        StringBuilder capas = new StringBuilder();
        for (int dy = 0; dy <= 5; dy++) {
            capas.append("y").append(cota + dy).append(':').append(bloquesEnLaBanda(level, cota + dy)).append(' ');
        }
        String linea = capas.toString();
        if (!linea.equals(ultimaLineaDelMuro)) {
            ultimaLineaDelMuro = linea;
            DevilRpg.LOGGER.info("[Arnes] MURO t={} banda r=57..67 por capas: {}", ticks, linea);
        }
    }

    /** Bloques (no aire) en la banda de la muralla (r = 57..67) a esa altura. */
    private static int bloquesEnLaBanda(ServerLevel level, int y) {        int n = 0;
        for (int dx = -67; dx <= 67; dx++) {
            for (int dz = -67; dz <= 67; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 < 57 * 57 || d2 > 67 * 67) {
                    continue;
                }
                if (!level.getBlockState(new BlockPos(CENTRO.getX() + dx, y, CENTRO.getZ() + dz)).isAir()) {
                    n++;
                }
            }
        }
        return n;
    }

    /** El 2º asaltante de la medida: el de la aldea YA GANADA (con campo de fuerza). */
    private static com.chipoodle.devilrpg.entity.AggressiveZombieEntity asaltanteDos = null;
    /** Rumbo del 2º asaltante (25°), para que no se pise con el primero (0° = este). */
    private static final double ANGULO_DEL_SEGUNDO = Math.toRadians(25.0D);

    /**
     * Levanta un <b>muro interior de prueba</b> (piedra, 12 de ancho y 3 de alto) a 30 bloques del centro, en el rumbo
     * {@code ang}: está DENTRO de la aldea, así que solo lo puede picar quien <b>no</b> tenga campo de fuerza.
     */
    private static void muroInterior(ServerLevel level, double ang, int cota, int ancho) {
        int puestos = 0;
        for (int t = -(ancho / 2); t <= ancho / 2; t++) {
            for (int dy = 0; dy <= 2; dy++) {
                int mx = CENTRO.getX() + (int) Math.round(Math.cos(ang) * 30.0D - Math.sin(ang) * t);
                int mz = CENTRO.getZ() + (int) Math.round(Math.sin(ang) * 30.0D + Math.cos(ang) * t);
                BlockPos p = new BlockPos(mx, cota + dy, mz);
                if (level.getBlockState(p).isAir()) {
                    level.setBlock(p, net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState(), 3);
                    puestos++;
                }
            }
        }
        DevilRpg.LOGGER.info("[Arnes] MURO: muro interior de prueba en el rumbo {} grados ({} bloque(s) nuevos)",
                (int) Math.toDegrees(ang), puestos);
    }

    /**
     * Cierra un ANILLO de piedra (radio 7, 3 de alto) alrededor de la plaza, con el jugador de pega DENTRO: así el
     * asaltante TIENE que picar para llegar a él. Un muro corto no mide nada —se rodea andando—, que es lo que pasó en
     * la corrida anterior: con 12 bloques de ancho, el asaltante entró por el lado sin picar.
     */
    private static void cercoDeLaPlaza(ServerLevel level, int cota) {
        int puestos = 0;
        for (int ang = 0; ang < 360; ang += 3) {
            double a = Math.toRadians(ang);
            for (int dy = 0; dy <= 2; dy++) {
                int mx = CENTRO.getX() + (int) Math.round(Math.cos(a) * 7.0D);
                int mz = CENTRO.getZ() + (int) Math.round(Math.sin(a) * 7.0D);
                BlockPos p = new BlockPos(mx, cota + dy, mz);
                if (level.getBlockState(p).isAir()) {
                    level.setBlock(p, net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState(), 3);
                    puestos++;
                }
            }
        }
        DevilRpg.LOGGER.info("[Arnes] MURO: cerco de la PLAZA cerrado (r=7, 3 de alto, {} bloque(s) nuevos) con el"
                + " jugador de pega dentro: hay que picarlo para llegar a el", puestos);
    }

    /**
     * EL DIARIO COMO LIBRO y LA CHARLA CON EL CLÉRIGO (ronda del 22-sep-2026): monta el libro tal cual se le entrega
     * al jugador (título, autor y páginas) y comprueba que <b>hablar con un clérigo del pueblo</b> le enciende la
     * aldea que le toca — que es lo que pidió el jugador: *"SOLO cuando se hable con el clérigo es cuando ya aparezca
     * en los objetivos hacia dónde está la aldea y su distancia"*.
     */
    private static void probarElLibroYElClerigo(ServerLevel level, FakePlayer pega,
            com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface aux) {
        var libro = com.chipoodle.devilrpg.item.DiarioDelInvocado.crear(pega);
        var contenido = libro.get(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT);
        DevilRpg.LOGGER.info("[Arnes] LIBRO: esElDiario={} titulo=\"{}\" autor=\"{}\" paginas={}",
                com.chipoodle.devilrpg.item.DiarioDelInvocado.esElDiario(libro),
                contenido == null ? "-" : contenido.title().get(false),
                contenido == null ? "-" : contenido.author(),
                contenido == null ? 0 : contenido.pages().size());
        if (contenido != null) {
            var paginas = contenido.getPages(false);
            for (int p = 0; p < paginas.size(); p++) {
                DevilRpg.LOGGER.info("[Arnes] LIBRO pagina {}: {}", p + 1,
                        paginas.get(p).getString().replace("\n", " | "));
            }
        }
        var clerigos = level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(96.0D),
                v -> v.isAlive()
                        && v.getVillagerData().getProfession()
                                == net.minecraft.world.entity.npc.VillagerProfession.CLERIC
                        && VillageManager.esDelPueblo(v));
        DevilRpg.LOGGER.info("[Arnes] CLERIGO-CHARLA: {} clerigo(s) del pueblo encontrados", clerigos.size());
        if (clerigos.isEmpty() || aux == null) {
            return;
        }
        aux.setObjectiveIndex(7, pega); // un objetivo que no venga revelado por la siembra
        DevilRpg.LOGGER.info("[Arnes] CLERIGO-CHARLA antes: indice={} revelada(7)={}", aux.getObjectiveIndex(),
                aux.isAldeaRevelada(7));
        try {
            VillageManager.elClerigoSenalaLaAldeaActual(level, clerigos.get(0), pega);
        } catch (Exception e) {
            DevilRpg.LOGGER.warn("[Arnes] CLERIGO-CHARLA fallo: {}", e.toString());
        }
        DevilRpg.LOGGER.info("[Arnes] CLERIGO-CHARLA despues: revelada(7)={} (tiene que ser true)",
                aux.isAldeaRevelada(7));
        try {
            VillageManager.elClerigoSenalaLaAldeaActual(level, clerigos.get(0), pega);
        } catch (Exception e) {
            DevilRpg.LOGGER.warn("[Arnes] CLERIGO-CHARLA (2a vez): {}", e.toString());
        }
        DevilRpg.LOGGER.info("[Arnes] CLERIGO-CHARLA otra vez: revelada(7)={} (sigue true, no se repite el aviso)",
                aux.isAldeaRevelada(7));

        // LA CHARLA CUANDO LA ALDEA ACTUAL YA ESTÁ SALVADA (el caso del jugador): el clérigo AVANZA el objetivo a la
        // siguiente y le revela la dirección SIN NOMBRE (el nombre llega al entrar en ella). En esta partida la aldea
        // INDICE (2) está Resolved, o sea ganada.
        aux.setObjectiveIndex(INDICE, pega);
        DevilRpg.LOGGER.info("[Arnes] CHARLA-TRAS-GANAR antes: indice={} barra(2)=\"{}\" revelada(3)={}",
                aux.getObjectiveIndex(),
                com.chipoodle.devilrpg.survival.VillageBarText.texto(INDICE, aux.isAldeaVisitada(INDICE),
                        aux.isAldeaRevelada(INDICE), 0, "arriba"),
                aux.isAldeaRevelada(3));
        try {
            VillageManager.elClerigoSenalaLaAldeaActual(level, clerigos.get(0), pega);
        } catch (Exception e) {
            DevilRpg.LOGGER.warn("[Arnes] CHARLA-TRAS-GANAR fallo: {}", e.toString());
        }
        DevilRpg.LOGGER.info("[Arnes] CHARLA-TRAS-GANAR despues: indice={} revelada(3)={} barra(3)=\"{}\" (tiene que"
                        + " ser la 3, revelada y SIN NOMBRE)", aux.getObjectiveIndex(), aux.isAldeaRevelada(3),
                com.chipoodle.devilrpg.survival.VillageBarText.texto(3, aux.isAldeaVisitada(3),
                        aux.isAldeaRevelada(3), 1234, "->"));
        aux.setObjectiveIndex(0, pega);
    }

    /** ¿Ya se le ha sembrado al pueblo la harina y los huesos de la medida del leñador? */
    private static boolean sembradoElLenador = false;

    /**
     * EL LEÑADOR Y EL POLVO DE HUESO: ver {@link #MEDIR_LENADOR}. Todo lo que se volca es <b>estado real del mundo</b>
     * (bloques de la arboleda, contenedores y zurrones), y el destino al que camina cada aldeano sale de su propia
     * navegación, así que dice si va <b>dentro</b> de la valla (62) o fuera.
     */
    /**
     * EL ALMACÉN Y LOS HUEVOS DEL GALLINERO (I95/I96/I97). Ver la ayuda de {@link #MEDIR_ALMACEN_Y_HUEVOS}.
     */
    private static void medirElAlmacenYLosHuevos(ServerLevel level) {
        int cota = com.chipoodle.devilrpg.world.VillageGenerator.cotaDeLaPlaza(level, CENTRO);
        BlockPos apoyo = com.chipoodle.devilrpg.world.VillageStorage.puntoDeApoyo(level, CENTRO);
        // SIEMBRA DE HUEVOS: uno en el SUELO del corralillo (el rincon oeste: solo se llega ENTRANDO por el porton) y
        // otro ENCIMA de la paja (su celda esta a cota + 1, que es el caso que se quedaba a 1,803 del alcance viejo).
        if (ticks == 200 || (ticks > 200 && ticks % 400 == 0)) {
            BlockPos base = com.chipoodle.devilrpg.world.VillageGenerator.baseDeAnexo(CENTRO);
            BlockPos suelo = new BlockPos(base.getX() - 7, cota, base.getZ() - 8);
            BlockPos paja = new BlockPos(base.getX() - 4, cota + 1, base.getZ() - 7);
            for (BlockPos p : new BlockPos[]{suelo, paja}) {
                net.minecraft.world.entity.item.ItemEntity huevo = new net.minecraft.world.entity.item.ItemEntity(
                        level, p.getX() + 0.5D, p.getY(), p.getZ() + 0.5D,
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EGG, 1));
                huevo.setPickUpDelay(0);
                level.addFreshEntity(huevo);
            }
            DevilRpg.LOGGER.info("[Arnes] HUEVOS: sembrados 2 huevos (suelo {} y paja {})", suelo, paja);
        }
        if (ticks % 40 != 0) {
            return;
        }
        // 1) EL ALMACEN: la capa del suelo (tiene que ser `cota - 1`) y la ruta de un aldeano hasta el punto de apoyo.
        var caja = com.chipoodle.devilrpg.world.VillageStorage.almacen(level, CENTRO);
        int cosas = 0;
        StringBuilder cofre = new StringBuilder();
        if (caja != null) {
            for (int i = 0; i < caja.getContainerSize(); i++) {
                if (!caja.getItem(i).isEmpty()) {
                    cosas += caja.getItem(i).getCount();
                    if (cofre.length() < 80) {
                        cofre.append(cofre.length() > 0 ? ", " : "").append(caja.getItem(i).getCount()).append('x')
                                .append(caja.getItem(i).getItem());
                    }
                }
            }
        }
        Villager referencia = null;
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(140))) {
            if (v.getVillagerData().getProfession() == net.minecraft.world.entity.npc.VillagerProfession.SHEPHERD) {
                referencia = v;
                break;
            }
        }
        DevilRpg.LOGGER.info("[Arnes] ALMACEN t={} apoyo={} (cota={}; suelo debajo={} | dos debajo={}) ruta={}"
                        + " | COFRE: {} objeto(s) [{}]",
                ticks, apoyo.toShortString(), cota,
                level.getBlockState(apoyo.below()).getBlock().getName().getString(),
                level.getBlockState(apoyo.below(2)).getBlock().getName().getString(),
                referencia == null ? "sin ganadero" : rutaDetallada(referencia, apoyo), cosas, cofre);
        // 2) EL GANADERO: donde esta, que hace, que lleva y que tiene aparcado.
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(140))) {
            var prof = v.getVillagerData().getProfession();
            if (prof != net.minecraft.world.entity.npc.VillagerProfession.SHEPHERD) {
                continue;
            }
            StringBuilder goals = new StringBuilder();
            for (var w : v.goalSelector.getAvailableGoals()) {
                if (w.isRunning()) {
                    goals.append(goals.length() > 0 ? "+" : "").append(w.getPriority()).append(':')
                            .append(w.getGoal().getClass().getSimpleName());
                }
            }
            StringBuilder zurron = new StringBuilder();
            for (int i = 0; i < v.getInventory().getContainerSize(); i++) {
                if (!v.getInventory().getItem(i).isEmpty()) {
                    zurron.append(zurron.length() > 0 ? ", " : "").append(v.getInventory().getItem(i).getCount())
                            .append('x').append(v.getInventory().getItem(i).getItem());
                }
            }
            long aparcado = v.getPersistentData().getLong("DevilRpgPuntoFallido");
            long hasta = v.getPersistentData().getLong("DevilRpgPuntoFallidoHasta");
            double dApoyo = Math.sqrt(v.distanceToSqr(apoyo.getX() + 0.5D, apoyo.getY(), apoyo.getZ() + 0.5D));
            var cerebro = v.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET)
                    .orElse(null);
            BlockPos destino = cerebro == null ? null : cerebro.getTarget().currentBlockPosition();
            DevilRpg.LOGGER.info("[Arnes] GANADERO t={} pos={} dApoyo={} zurron=[{}] goals=[{}] destino={}"
                            + " etiqueta=\"{}\" aparcado={} (hasta {}, gameTime {})",
                    ticks, v.blockPosition().toShortString(), fmt(dApoyo), zurron, goals,
                    destino == null ? "-" : destino.toShortString(), etiquetaDe(v),
                    aparcado == 0L ? "-" : BlockPos.of(aparcado).toShortString(), hasta, level.getGameTime());
        }
        // 3) LOS HUEVOS del corral (si el ganadero los coge, desaparecen) y EL PORTON del gallinero con sus gallinas.
        //    OJO CON EL INSTRUMENTO: la caja va alrededor de la BASE DEL CORRAL, no del centro de la aldea.
        int huevos = 0;
        StringBuilder donde = new StringBuilder();
        AABB corral = new AABB(com.chipoodle.devilrpg.world.VillageGenerator.baseDeAnexo(CENTRO)).inflate(20.0D);
        for (net.minecraft.world.entity.item.ItemEntity it : level.getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class, corral)) {
            if (!it.getItem().is(net.minecraft.world.item.Items.EGG)) {
                continue;
            }
            huevos++;
            donde.append(donde.length() > 0 ? ", " : "").append(it.blockPosition().toShortString())
                    .append("(edad ").append(it.tickCount).append(')');
        }
        BlockPos porton = com.chipoodle.devilrpg.world.VillageGenerator.portonDelGallinero(CENTRO, cota);
        var estado = level.getBlockState(porton);
        int enElHueco = level.getEntitiesOfClass(net.minecraft.world.entity.animal.Chicken.class,
                new AABB(porton).inflate(1.5D)).size();
        int pegadas = level.getEntitiesOfClass(net.minecraft.world.entity.animal.Chicken.class,
                new AABB(porton).inflate(2.5D)).size();
        DevilRpg.LOGGER.info("[Arnes] HUEVOS t={}: {} en el suelo [{}] | PORTON {} open={} gallinas: {} en el hueco,"
                        + " {} a 2.5 | gallinas en el corral={}",
                ticks, huevos, donde, porton.toShortString(),
                estado.hasProperty(net.minecraft.world.level.block.FenceGateBlock.OPEN)
                        && estado.getValue(net.minecraft.world.level.block.FenceGateBlock.OPEN),
                enElHueco, pegadas, com.chipoodle.devilrpg.world.VillageGenerator.animalesDelCorral(level, CENTRO)
                        .stream().filter(a -> a.getType() == net.minecraft.world.entity.EntityType.CHICKEN).count());
    }

    /**
     * LAS HORDAS DEL MUNDO (I98): la presión del abandono y el turno del roll. Ver {@link #MEDIR_HORDAS}.
     */
    private static void medirLasHordas(ServerLevel level, FakePlayer pega) {
        var saved = com.chipoodle.devilrpg.world.VillageSavedData.get(level);
        double threat = com.chipoodle.devilrpg.survival.ThreatLevel.current(level);
        int intervalo = (int) (3 * 60 * 20 + (20 * 60 * 20 - 3 * 60 * 20) * (1.0 - threat));
        if (ticks == 20) {
            // EL JUGADOR DE PEGA NECESITA ANCLA (como en la partida de verdad): sin ella, `pickHordeTarget` no puede
            // calcular dónde cae ninguna aldea y devuelve null sin mirar nada.
            var aux = com.chipoodle.devilrpg.capability.IGenericCapability.getUnwrappedPlayerCapability(pega,
                    com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability.INSTANCE);
            aux.setAnchorPoint(ancla(), pega);
            aux.setSpawnPoint(ancla(), pega);
            DevilRpg.LOGGER.info("[Arnes] HORDAS t=20 ANTES: presion(aldea 0)={} ticks ({} min) | gameTime={}"
                            + " intervalo={} turno={} | estado=\"{}\" | ancla del jugador puesta en {}",
                    saved.getPressureTicks(0), Math.round(saved.getPressureTicks(0) / 1200.0D), level.getGameTime(),
                    intervalo, level.getGameTime() / intervalo,
                    com.chipoodle.devilrpg.world.VillageManager.estadoDeLaAldea(level, 0), ancla());
            // Y SE SIEMBRA a 8 min EXACTOS: la aldea ya pasa el umbral, y lo que se mide después es que la presión
            // SIGUE subiendo sola (latido a latido) y que la aldea es ELEGIDA como objetivo.
            int sembrada = saved.accruePressure(0, level.getGameTime() - PRESION_MINIMA, 1.0D);
            DevilRpg.LOGGER.info("[Arnes] HORDAS: presion de la aldea 0 sembrada a {} ticks ({} min de {} necesarios)"
                            + "; el turno del roll cambia en ~{} ticks",
                    sembrada, Math.round(sembrada / 1200.0D), Math.round(PRESION_MINIMA / 1200.0D),
                    intervalo - (level.getGameTime() % intervalo));
        }
        // LA ELECCION DE OBJETIVO, que es el nucleo del arreglo: con la presion ya acumulada, `pickHordeTarget` tiene
        // que devolver la aldea 0. OJO: el SPAWNEO de la oleada NO se puede medir headless —`HordeManager` usa
        // `level.players()` y el jugador de pega NO esta en esa lista (es la misma limitacion que el reloj del asedio,
        // I86)—, asi que lo que se mide aqui es la eleccion; la marcha la ve el jugador en juego.
        if (ticks >= 60 && ticks % 100 == 0) {
            var elegida = com.chipoodle.devilrpg.world.VillageManager.pickHordeTarget(level, pega, INDICE);
            DevilRpg.LOGGER.info("[Arnes] HORDAS t={} OBJETIVO ELEGIDO = {}", ticks,
                    elegida == null ? "NINGUNO" : ("aldea " + elegida.objectiveIndex() + " centro " + elegida.center()));
        }
        if (ticks % 40 != 0) {
            return;
        }
        int asediadores = 0;
        double masCerca = Double.MAX_VALUE;
        for (net.minecraft.world.entity.Mob m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                new AABB(CENTRO).inflate(220))) {
            if (m instanceof com.chipoodle.devilrpg.entity.AggressiveZombieEntity z && z.getWorldSiegeIndex() == 0) {
                asediadores++;
                masCerca = Math.min(masCerca, Math.sqrt(m.distanceToSqr(CENTRO.getX() + 0.5D, CENTRO.getY(),
                        CENTRO.getZ() + 0.5D)));
            }
        }
        DevilRpg.LOGGER.info("[Arnes] HORDAS t={} gameTime={} turno={} presion(aldea 0)={} ticks ({} min) |"
                        + " estado=\"{}\" | asediadores de la aldea 0: {} (el mas cerca a {})",
                ticks, level.getGameTime(), level.getGameTime() / intervalo, saved.getPressureTicks(0),
                Math.round(saved.getPressureTicks(0) / 1200.0D),
                com.chipoodle.devilrpg.world.VillageManager.estadoDeLaAldea(level, 0), asediadores,
                asediadores == 0 ? "-" : Math.round(masCerca));
    }

    /**
     * ¿Se planta al LEÑADOR en la plaza al empezar (ver {@code sembrarRestosDelLenador})? En {@code true} sirve para
     * medir sus limpiezas (si está atascado en la muralla no tala). En {@code false} se le planta <b>EN EL ATASCO DEL
     * MURO</b> (`CENTRO.offset(57, 0, 26)` = `527,63,672`) con 16 troncos en el zurrón: es la medida del <b>cruce del
     * muro</b> (I112) — el almacén queda a 11 bloques pero con la muralla en medio.
     */
    private static final boolean PLANTAR_AL_LENADOR_EN_LA_PLAZA = false;

    /**
     * <b>Planta al LEÑADOR en la plaza y le cuelga dos TRONCOS FLOTANTES</b> pegados a un árbol de la arboleda del
     * pueblo: es para poder medir la limpieza de restos (ver {@code medirElLenador}). El traslado hace falta porque en
     * su partida el leñador se queda pegado a la <b>muralla</b> (527,63,672, r=62) intentando llegar al almacén: con la
     * madera en el zurrón no tala nada y la medida se queda sin faena.
     */
    private static void sembrarRestosDelLenador(ServerLevel level) {
        int cota = com.chipoodle.devilrpg.world.VillageGenerator.cotaDeLaPlaza(level, CENTRO);
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(140))) {
            if (v.isBaby() || v.getVillagerData().getProfession()
                    != net.minecraft.world.entity.npc.VillagerProfession.FLETCHER) {
                continue;
            }
            if (!PLANTAR_AL_LENADOR_EN_LA_PLAZA) {
                // PLANTADO EN EL ATASCO DEL MURO con la madera en el zurrón: es la medida del CRUCE DEL MURO (I112).
                // Antes solo se dejaba donde estuviera y se medía el atasco; ahora, con `pasoParaCruzarElMuro`, lo que se
                // mide es que **sí llega**: cruza el portón y descarga en el almacén.
                BlockPos atasco = CENTRO.offset(57, 0, 26);
                BlockPos venia = v.blockPosition();
                v.getNavigation().stop();
                v.teleportTo(atasco.getX() + 0.5D, atasco.getY(), atasco.getZ() + 0.5D);
                v.getInventory().clearContent();
                v.getInventory().addItem(new net.minecraft.world.item.ItemStack(
                        net.minecraft.world.item.Items.OAK_LOG, 16));
                DevilRpg.LOGGER.info("[Arnes] LENADOR: plantado EN EL ATASCO DEL MURO {} con 16 troncos (venia en {})"
                                + " — se mide si cruza el porton y llega al almacen (el almacen esta a 11 bloques pero"
                                + " con la muralla en medio)", atasco.toShortString(), venia.toShortString());
                continue;
            }
            BlockPos plaza = com.chipoodle.devilrpg.world.VillageManager.casillaDeLaCalle(level, CENTRO);
            v.getNavigation().stop();
            v.teleportTo(plaza.getX() + 0.5D, plaza.getY(), plaza.getZ() + 0.5D);
            DevilRpg.LOGGER.info("[Arnes] LENADOR: plantado en la plaza {} (venia pegado a la muralla y no llegaba al"
                    + " almacen: con la madera en el zurron no talaba)", plaza.toShortString());
        }
        int puestos = 0;
        for (int dx = -34; dx <= -4 && puestos < 2; dx++) {
            for (int dz = -38; dz <= -4 && puestos < 2; dz++) {
                BlockPos columna = new BlockPos(CENTRO.getX() + dx, cota, CENTRO.getZ() + dz);
                if (!com.chipoodle.devilrpg.world.VillageGenerator.enLaArboleda(CENTRO, columna)) {
                    continue;
                }
                boolean hayArbol = false;
                for (int dy = 0; dy <= 4; dy++) {
                    if (level.getBlockState(columna.above(dy)).is(net.minecraft.tags.BlockTags.LOGS)) {
                        hayArbol = true;
                    }
                }
                if (!hayArbol) {
                    continue; // solo junto a un arbol de la arboleda
                }
                for (int dy = 5; dy <= 10 && puestos < 2; dy++) {
                    BlockPos flotante = columna.offset(4, dy, 0);
                    if (!level.getBlockState(flotante).isAir()) {
                        continue;
                    }
                    level.setBlock(flotante, net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState(), 3);
                    DevilRpg.LOGGER.info("[Arnes] LENADOR: tronco FLOTANTE de prueba en {} (junto al arbol de {})",
                            flotante.toShortString(), columna.toShortString());
                    puestos++;
                }
            }
        }
        DevilRpg.LOGGER.info("[Arnes] LENADOR: {} tronco(s) flotante(s) de prueba colgados", puestos);
    }

    private static void medirElLenador(ServerLevel level, FakePlayer pega) {
        int cota = com.chipoodle.devilrpg.world.VillageGenerator.cotaDeLaPlaza(level, CENTRO);
        var despensa = com.chipoodle.devilrpg.world.VillagePantry.despensa(level, CENTRO);
        // A LOS 10 s, DOS COSAS PARA PODER MEDIR LA LIMPIEZA DE RESTOS DEL LENADOR (lo ultimo que se anadio):
        //  (1) al lenador se le planta EN LA PLAZA: venia de FUERA, pegado a la muralla (527,63,672, r=62), y desde ahi
        //      no conseguia llegar al almacen, asi que se quedaba con la madera en el zurron y NO TALABA NADA (medido:
        //      "no consigue llegar a 517,63,666" y el contador de restos del mundo sin moverse en toda la corrida);
        //  (2) se le cuelgan DOS TRONCOS FLOTANTES pegados a un arbol de la arboleda: al talar ese arbol, la pasada
        //      nueva (`limpiarAlrededorDelTocon`, radio 6 alrededor del tocon) tiene que rematarlos.
        if (ticks == 200) {
            sembrarRestosDelLenador(level);
        }
        // A los 10 s (chunks cargados y el latido ya repartido): huesos y harina de huesos, que es justo lo que en la
        // partida del jugador NO hay (medido: 0 de harina en toda la aldea y 1 hueso guardado, con los tres
        // composteros a nivel 1, 1 y 5 de 8).
        if (!sembradoElLenador && ticks == 200 && despensa != null) {
            sembradoElLenador = true;
            int huesosAntes = com.chipoodle.devilrpg.world.VillagePantry.contar(despensa,
                    s -> s.is(net.minecraft.world.item.Items.BONE));
            int harinaAntes = com.chipoodle.devilrpg.world.VillagePantry.contar(despensa,
                    s -> s.is(net.minecraft.world.item.Items.BONE_MEAL));
            com.chipoodle.devilrpg.world.VillagePantry.guardar(despensa,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BONE, 8));
            com.chipoodle.devilrpg.world.VillagePantry.guardar(despensa,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BONE_MEAL, 16));
            DevilRpg.LOGGER.info("[Arnes] LENADOR: despensa sembrada con 8 huesos y 16 de harina de hueso (antes: {}"
                    + " hueso(s) y {} de harina)", huesosAntes, harinaAntes);
        }
        // Y la harina se le va REPONIENDO: el granjero se la lleva para abonar la huerta en cuanto la ve, así que sin
        // reponerla solo se mediría el caso "no hay harina". Con esto se mide el que pidió el jugador: que el leñador
        // vaya a por ella y abone la arboleda en vez de irse al monte.
        if (ticks >= 400 && ticks % 200 == 0 && despensa != null) {
            com.chipoodle.devilrpg.world.VillagePantry.guardar(despensa,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BONE_MEAL, 16));
        }
        if (ticks % 200 != 0 || despensa == null) {
            return;
        }
        int arboles = 0;
        int plantones = 0;
        int huecos = 0;
        for (BlockPos p : com.chipoodle.devilrpg.world.VillageGenerator.plantonesDeLaArboleda(CENTRO, cota)) {
            boolean arbol = false;
            boolean planton = false;
            for (int dy = 0; dy <= 18; dy++) {
                var st = level.getBlockState(p.above(dy));
                if (st.is(net.minecraft.tags.BlockTags.LOGS)) {
                    arbol = true;
                    break;
                }
                if (st.is(net.minecraft.tags.BlockTags.SAPLINGS)) {
                    planton = true;
                }
            }
            if (arbol) {
                arboles++;
            } else if (planton) {
                plantones++;
            } else {
                huecos++;
            }
        }
        DevilRpg.LOGGER.info("[Arnes] LENADOR t={} ARBOLEDA: {} arbol(es), {} planton(es), {} hueco(s) | DESPENSA: {}"
                        + " hueso(s), {} harina de hueso", ticks, arboles, plantones, huecos,
                com.chipoodle.devilrpg.world.VillagePantry.contar(despensa,
                        s -> s.is(net.minecraft.world.item.Items.BONE)),
                com.chipoodle.devilrpg.world.VillagePantry.contar(despensa,
                        s -> s.is(net.minecraft.world.item.Items.BONE_MEAL)));
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(140))) {
            var prof = v.getVillagerData().getProfession();
            boolean lenador = prof == net.minecraft.world.entity.npc.VillagerProfession.FLETCHER;
            if (!lenador && prof != net.minecraft.world.entity.npc.VillagerProfession.FARMER) {
                continue;
            }
            BlockPos nav = v.getNavigation().getTargetPos();
            double dCentro = Math.sqrt(v.distanceToSqr(CENTRO.getX() + 0.5D, v.getY(), CENTRO.getZ() + 0.5D));
            double dNav = nav == null ? -1.0D
                    : Math.sqrt(nav.distSqr(new BlockPos(CENTRO.getX(), nav.getY(), CENTRO.getZ())));
            int madera = 0;
            int semillas = 0;
            int harina = 0;
            for (int i = 0; i < v.getInventory().getContainerSize(); i++) {
                var s = v.getInventory().getItem(i);
                if (s.is(net.minecraft.tags.ItemTags.LOGS)) {
                    madera += s.getCount();
                }
                if (s.getDescriptionId().contains("sapling")) {
                    semillas += s.getCount();
                }
                if (s.is(net.minecraft.world.item.Items.BONE_MEAL)) {
                    harina += s.getCount();
                }
            }
            DevilRpg.LOGGER.info("[Arnes] LENADOR {} {} pos={} dCentro={} destino={} destinoDentro={} zurron=[madera={}"
                            + " semillas={} harina={}] etiqueta=\"{}\"", lenador ? "LENADOR" : "GRANJERO",
                    v.getUUID().toString().substring(0, 8), v.blockPosition(), (int) dCentro, nav,
                    dNav < 0 ? "?" : (dNav < 62.0D), madera, semillas, harina, etiquetaDe(v));
        }
    }

    /** El cráter de la medida: centro y si ya se ha abierto. */
    private static BlockPos centroDelAgujero = null;

    /** LA REPARACIÓN DE UN AGUJERO DEL SUELO: ver {@link #MEDIR_AGUJERO}. */
    private static void medirElAgujeroDelSuelo(ServerLevel level, FakePlayer pega) {
        int cota = com.chipoodle.devilrpg.world.VillageGenerator.cotaDeLaPlaza(level, CENTRO);
        if (ticks == 300) {
            centroDelAgujero = new BlockPos(CENTRO.getX() + 7, cota, CENTRO.getZ() + 7);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = 0; dy >= -1; dy--) {
                        level.setBlock(centroDelAgujero.offset(dx, dy, dz),
                                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                                net.minecraft.world.level.block.Block.UPDATE_ALL);
                    }
                }
            }
            DevilRpg.LOGGER.info("[Arnes] AGUJERO: crater de 3x3x2 abierto en {} (cota {}) — lo tiene que tapar un"
                    + " CONSTRUCTOR", centroDelAgujero, cota);
            // DIAGNOSTICO: ¿reconoce la REGLA el crater? ¿que bloque pondria? ¿lo ve el buscador del constructor?
            int reconocidas = 0;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = 0; dy >= -1; dy--) {
                        BlockPos p = centroDelAgujero.offset(dx, dy, dz);
                        boolean es = VillageManager.esAgujeroDelSuelo(level, INDICE, p);
                        var bloque = VillageManager.bloqueParaReparar(level, INDICE, p);
                        if (es) {
                            reconocidas++;
                        }
                        if (dy == 0 || (dx == 0 && dz == 0)) {
                            DevilRpg.LOGGER.info("[Arnes] AGUJERO celda {} esAgujero={} bloqueParaReparar={}", p, es,
                                    bloque == null ? "null" : bloque.getBlock());
                        }
                    }
                }
            }
            DevilRpg.LOGGER.info("[Arnes] AGUJERO: {} de 18 celdas reconocidas como agujero del suelo", reconocidas);
            BlockPos objetivo = VillageManager.findRepairTarget(level, INDICE, centroDelAgujero, new java.util.HashSet<>(),
                    java.util.UUID.randomUUID());
            DevilRpg.LOGGER.info("[Arnes] AGUJERO: findRepairTarget dice {} (el constructor deberia ir ahi)", objetivo);
            var constructores = level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(96.0D),
                    v -> v.getPersistentData().getBoolean("DevilRpgBuilder"));
            DevilRpg.LOGGER.info("[Arnes] AGUJERO: {} constructor(es) marcados en la aldea", constructores.size());
        }
        if (centroDelAgujero == null || ticks % 200 != 0) {
            return;
        }
        DevilRpg.LOGGER.info("[Arnes] AGUJERO t={} capa cota: {} | capa cota-1: {}", ticks,
                capaDelAgujero(level, centroDelAgujero, 0), capaDelAgujero(level, centroDelAgujero, -1));
        // Y QUE ESTA HACIENDO CADA CONSTRUCTOR: su objetivo de reparacion, si descansa, si hay asedio, sus goals.
        for (Villager b : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(96.0D),
                v -> v.getPersistentData().getBoolean("DevilRpgBuilder"))) {
            BlockPos suObjetivo = VillageManager.findRepairTarget(level, INDICE, b.blockPosition(),
                    new java.util.HashSet<>(), b.getUUID());
            StringBuilder goals = new StringBuilder();
            for (net.minecraft.world.entity.ai.goal.WrappedGoal w : b.goalSelector.getAvailableGoals()) {
                goals.append(w.getPriority()).append(':').append(w.getGoal().getClass().getSimpleName());
                goals.append(w.isRunning() ? "* " : " ");
            }
            DevilRpg.LOGGER.info("[Arnes] CONSTRUCTOR {} pos={} destino={} descansando={} asedio={} veObjetivo={}"
                            + " goals=[{}] etiqueta=\"{}\"", VillageManager.nombreDe(b), b.blockPosition(),
                    b.getNavigation().getTargetPos(), VillageManager.estaDescansando(b),
                    VillageManager.isVillageUnderAttack(level, INDICE), suObjetivo, goals.toString().trim(),
                    etiquetaDe(b));
        }
    }

    /** Una capa del cráter en 3 líneas de 3 letras: '.'=aire, 'G'=hierba, 'D'=tierra, 'P'=camino, '?'=otro. */
    private static String capaDelAgujero(ServerLevel level, BlockPos centro, int dy) {
        StringBuilder sb = new StringBuilder();
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                var bs = level.getBlockState(centro.offset(dx, dy, dz));
                String letra = bs.isAir() ? "."
                        : bs.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK) ? "G"
                        : bs.is(net.minecraft.world.level.block.Blocks.DIRT) ? "D"
                        : bs.is(net.minecraft.world.level.block.Blocks.DIRT_PATH) ? "P"
                        : "?";
                sb.append(letra);
            }
            sb.append(' ');
        }
        return sb.toString().trim();
    }

    /** Ticks que lleva la ola a la vista (para limpiarla a los 10 s) y dónde está la aldea del asedio. */
    private static int ticksDeOlaVista = 0;
    private static BlockPos centroDelAsedio = null;

    /** EL ASEDIO DE VERDAD (I87): ver {@link #MEDIR_ASEDIO_VIVO}. */
    private static void medirElAsedioVivo(ServerLevel level, FakePlayer pega) {
        if (ticks == 300) {
            centroDelAsedio = com.chipoodle.devilrpg.survival.ObjectiveTargets.targetOf(ancla(), 3);
            // FORZAR LOS CHUNKS DE LA ALDEA 3: el jugador de pega NO carga chunks (no es un jugador de verdad) y sin
            // ellos la aldea se descarga a los pocos segundos. Medido en la primera corrida de este modo:
            // `aldeanos3=11` al generarse y `0` diez segundos después, con `agresivos=0` — la ola no llegaba a
            // spawnear porque su trozo de mundo no estaba cargado. Se fuerzan los mismos 13x13 que ya se fuerzan en
            // la aldea 2 en `preparar`.
            int cx3 = centroDelAsedio.getX() >> 4;
            int cz3 = centroDelAsedio.getZ() >> 4;
            for (int dx = -6; dx <= 6; dx++) {
                for (int dz = -6; dz <= 6; dz++) {
                    level.setChunkForced(cx3 + dx, cz3 + dz, true);
                }
            }
            com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface aux =
                    com.chipoodle.devilrpg.capability.IGenericCapability.getUnwrappedPlayerCapability(
                            pega, com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability.INSTANCE);
            if (aux != null) {
                aux.setAnchorPoint(ancla(), pega);
                aux.setSpawnPoint(ancla(), pega);
                // El asedio de la 3 tiene que ser "el actual", o el clérigo no revela: el aviso es para el jugador
                // que lo está viviendo.
                aux.setObjectiveIndex(3, pega);
            }
            DevilRpg.LOGGER.info("[Arnes] ASEDIO: arrancando el asedio de la aldea 3 en {} (se genera ahora; las"
                    + " otras tres de esa partida ya estan resueltas)", centroDelAsedio);
            VillageManager.start(level, pega, 3, centroDelAsedio);
            DevilRpg.LOGGER.info("[Arnes] ASEDIO: arrancado, hayAsedio(3)={}", VillageManager.hayAsedio(level, 3));
        }
        if (centroDelAsedio == null) {
            return;
        }
        // El jugador de pega, DENTRO de la aldea 3: si no, el reloj del asedio se queda EN PAUSA (I86) y no se
        // resolveria nunca. Y se gestiona hasta el objetivo 3, que es el que se esta asediando.
        pega.moveTo(centroDelAsedio.getX() + 0.5D, centroDelAsedio.getY() + 1.0D, centroDelAsedio.getZ() + 0.5D);
        VillageManager.manageNearby(level, pega, ancla(), 3);
        int bichos = level.getEntitiesOfClass(com.chipoodle.devilrpg.entity.AggressiveZombieEntity.class,
                new AABB(centroDelAsedio).inflate(150.0D)).size();
        if (bichos > 0) {
            ticksDeOlaVista++;
        }
        if (ticks % 100 == 0) {
            com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface aux =
                    com.chipoodle.devilrpg.capability.IGenericCapability.getUnwrappedPlayerCapability(
                            pega, com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability.INSTANCE);
            DevilRpg.LOGGER.info("[Arnes] ASEDIO t={} agresivos={} hayAsedio(3)={} revelada(4)={} aldeanos3={}",
                    ticks, bichos, VillageManager.hayAsedio(level, 3),
                    aux != null && aux.isAldeaRevelada(4),
                    level.getEntitiesOfClass(Villager.class, new AABB(centroDelAsedio).inflate(96.0D)).size());
        }
        // A LOS 10 s DE VER LA OLA, SE LIMPIA DESDE EL ARNES (ver el javadoc de MEDIR_ASEDIO_VIVO).
        if (bichos > 0 && ticksDeOlaVista == 200) {
            DevilRpg.LOGGER.info("[Arnes] ASEDIO: limpiando la ola ({} agresivo(s)) desde el arnes: el asedio tiene"
                    + " que resolverse SALVADO y el clerigo revelar la aldea 4", bichos);
            for (com.chipoodle.devilrpg.entity.AggressiveZombieEntity z
                    : level.getEntitiesOfClass(com.chipoodle.devilrpg.entity.AggressiveZombieEntity.class,
                    new AABB(centroDelAsedio).inflate(150.0D))) {
                z.hurt(level.damageSources().generic(), Float.MAX_VALUE);
            }
        }
    }

    /**
     * Vuelca el estado del descubrimiento y el revelado (I87), lo que dibujaría la barra de aldea, el rumbo a la
     * siguiente y las LÍNEAS DEL DIARIO tal cual las manda el objeto al usarlo.
     */
    private static void volcarAldeas(ServerLevel level, FakePlayer pega) {
        com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface aux =
                com.chipoodle.devilrpg.capability.IGenericCapability.getUnwrappedPlayerCapability(
                        pega, com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability.INSTANCE);
        if (aux == null) {
            DevilRpg.LOGGER.info("[Arnes] ALDEAS: el jugador de pega no tiene capability (no se puede medir)");
            return;
        }
        int indice = aux.getObjectiveIndex();
        DevilRpg.LOGGER.info("[Arnes] ALDEAS indice={} visitadas={} reveladas={}",
                indice, aux.getAldeasVisitadas(), aux.getAldeasReveladas());
        if (!escenarioDeReveladosHecho) {
            escenarioDeReveladosHecho = true;
            probarLosRevelados(level, pega, aux);
            probarLasEtiquetas(level, pega);
            probarElLibroYElClerigo(level, pega, aux);
        }
        for (int i = 0; i <= indice; i++) {
            DevilRpg.LOGGER.info("[Arnes] ALDEA {} nombre=\"{}\" visitada={} revelada={} centro={} estado=\"{}\"",
                    i, com.chipoodle.devilrpg.survival.VillageNames.nombre(i),
                    aux.isAldeaVisitada(i), aux.isAldeaRevelada(i), VillageManager.centroDe(level, i),
                    VillageManager.estadoDeLaAldea(level, i));
        }
        // LO QUE DIBUJARIA LA BARRA DE ALDEA: la aldea ACTUAL, la siguiente y una que NO esté en el guardado (el caso
        // que importa de verdad: la que viene DESPUÉS de una que cayó tiene que salir OCULTA hasta que la piedra o un
        // clérigo la revelen — es lo que pidió el jugador).
        for (int i : new int[]{indice, indice + 1, indice + 3}) {
            boolean vis = aux.isAldeaVisitada(i);
            boolean rev = aux.isAldeaRevelada(i);
            DevilRpg.LOGGER.info("[Arnes] BARRA DE ALDEA (aldea {}): {}", i, !vis && !rev
                    ? "OCULTA (ni direccion ni nombre: hay que leer la piedra o ganar un asedio)"
                    : (vis
                            ? "con NOMBRE: \"" + com.chipoodle.devilrpg.survival.VillageNames.nombre(i) + "\""
                            : "sin nombre: \"Aldea\""));
        }
        BlockPos siguiente = com.chipoodle.devilrpg.survival.ObjectiveTargets.targetOf(ancla(), indice + 1);
        DevilRpg.LOGGER.info("[Arnes] RUMBO a la aldea {}: {}", indice + 1,
                com.chipoodle.devilrpg.survival.ObjectiveTargets.direccionHacia(CENTRO, siguiente));
        for (net.minecraft.network.chat.Component linea
                : com.chipoodle.devilrpg.item.DiarioDelInvocado.lineas(pega)) {
            DevilRpg.LOGGER.info("[Arnes] DIARIO: {}", linea.getString());
        }
    }

    /** Cuántas cosas de ese tipo hay en el almacén (para confirmar el sembrado). */
    private static int cuenta(ServerLevel level, net.minecraft.world.item.Item item) {
        return com.chipoodle.devilrpg.world.VillageStorage.cuenta(level, CENTRO, s -> s.is(item));
    }

    // --- LA GRANJA: que bancal trabaja cada granjero y si se sube a la valla ----------------------------------
    // FARM_PLOTS y PLOT_WIDTH son privados en el generador: se copian aqui para la medida (el arnes es temporal).
    private static final int[][] PARCELAS = {{-30, 14}, {10, 4}, {-28, 34}};
    private static final int ANCHO_PARCELA = 9;

    /** El indice del bancal en el que esta ese aldeano (mirando el rectangulo de su valla), o -1 si esta fuera. */
    private static int bancalDe(ServerLevel level, Villager v) {
        int cota = com.chipoodle.devilrpg.world.VillageGenerator.cotaDeLaPlaza(level, CENTRO);
        if (Math.abs(v.getY() - cota) > 4) {
            return -1;
        }
        for (int i = 0; i < PARCELAS.length; i++) {
            int x0 = CENTRO.getX() + PARCELAS[i][0];
            int z0 = CENTRO.getZ() + PARCELAS[i][1];
            if (v.getX() >= x0 - 1 && v.getX() <= x0 + ANCHO_PARCELA
                    && v.getZ() >= z0 - 1 && v.getZ() <= z0 + ANCHO_PARCELA) {
                return i;
            }
        }
        return -1;
    }

    /** ¿Esta SUBIDO a la valla del bancal? (de pie sobre una valla o una compuerta: el bloque de debajo es eso) */
    private static boolean subidoALaValla(ServerLevel level, Villager v) {
        var debajo = level.getBlockState(v.blockPosition().below());
        return debajo.is(net.minecraft.world.level.block.Blocks.OAK_FENCE)
                || debajo.is(net.minecraft.world.level.block.Blocks.OAK_FENCE_GATE);
    }

    /** El compostero del bancal `i` de la aldea medida (misma cuenta que el generador: ver la migracion 64). */
    private static BlockPos composteroDelBancal(ServerLevel level, int i) {
        int cota = com.chipoodle.devilrpg.world.VillageGenerator.cotaDeLaPlaza(level, CENTRO);
        return new BlockPos(CENTRO.getX() + PARCELAS[i][0] - 3, cota, CENTRO.getZ() + PARCELAS[i][1]);
    }

    /**
     * El <b>porche de la taberna</b>: la columna {@code bx-1}, que es la que queda <b>entre</b> el toldo
     * ({@code bx-2}) y la <b>pared</b> de la taberna ({@code bx}). Si esas celdas estan vacias, el techito no
     * conecta con el edificio (el bug que reporto el jugador). Se mira, por celda: el escalon del alero pegado al
     * muro, su tablon de soffito debajo y que la pared de al lado sea solida.
     */
    private static void volcarPorche(ServerLevel level, String etiqueta) {
        if (!com.chipoodle.devilrpg.world.VillageGenerator.tabernaConstruida(level, CENTRO)) {
            DevilRpg.LOGGER.info("[Arnes] PORCHE {}: no hay taberna construida en {}", etiqueta, CENTRO);
            return;
        }
        int nivel = com.chipoodle.devilrpg.world.VillageGenerator.cotaDeLaPlaza(level, CENTRO);
        BlockPos base = com.chipoodle.devilrpg.world.VillageGenerator.baseDeLaTaberna(CENTRO);
        int bx = base.getX();
        int bz = base.getZ();
        int pz = 7;          // TABERNA_PUERTA (privada en el generador: se copia aqui para la medida)
        int yToldo = nivel + 5 - 2;    // TABERNA_PISO2 = 5
        int yDentro = nivel + 5 - 1;
        int pegadas = 0;
        for (int dz = pz - 3; dz <= pz + 3; dz++) {
            var escalon = level.getBlockState(new BlockPos(bx - 1, yDentro, bz + dz));
            var tablon = level.getBlockState(new BlockPos(bx - 1, yToldo, bz + dz));
            var pared = level.getBlockState(new BlockPos(bx, yDentro, bz + dz));
            boolean pegado = escalon.getBlock() instanceof net.minecraft.world.level.block.StairBlock
                    && tablon.is(net.minecraft.world.level.block.Blocks.DARK_OAK_PLANKS)
                    && !pared.isAir();
            if (pegado) {
                pegadas++;
            }
            DevilRpg.LOGGER.info("[Arnes] PORCHE {} dz{}: dx-1 escalon={} tablon={} pared={} -> {}",
                    etiqueta, dz, escalon.getBlock(), tablon.getBlock(), pared.getBlock(),
                    pegado ? "PEGADO A LA PARED" : "HUECO");
        }
        DevilRpg.LOGGER.info("[Arnes] PORCHE {}: celdas del toldo PEGADAS a la pared: {}/7 (base {}, cota {})",
                etiqueta, pegadas, base, nivel);
    }

    /**
     * EL COMBUSTIBLE (lo que se mide en esta ronda): cuanta <b>lena</b> queda en el almacen (y si se queda clavada en
     * la reserva), cuanta <b>carne cruda</b> queda en la despensa, y que hacen el <b>cocinero</b> (ahumador) y los
     * <b>herreros</b> (fundicion): su posicion, cuanta lena llevan encima, su destino y su etiqueta.
     */
    private static void volcarCombustible(ServerLevel level) {
        int lena = com.chipoodle.devilrpg.world.VillageStorage.cuentaLena(level, CENTRO);
        var despensa = com.chipoodle.devilrpg.world.VillagePantry.despensa(level, CENTRO);
        int crudo = com.chipoodle.devilrpg.world.VillagePantry.contar(despensa,
                com.chipoodle.devilrpg.world.VillagePantry::sePuedeCocinar);
        StringBuilder linea = new StringBuilder();
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(64))) {
            if (v.isBaby()) {
                continue;
            }
            var prof = v.getVillagerData().getProfession();
            boolean cocinero = prof == net.minecraft.world.entity.npc.VillagerProfession.BUTCHER;
            boolean herrero = prof == net.minecraft.world.entity.npc.VillagerProfession.WEAPONSMITH
                    || prof == net.minecraft.world.entity.npc.VillagerProfession.TOOLSMITH;
            if (!cocinero && !herrero) {
                continue;
            }
            int encima = 0;
            for (int i = 0; i < v.getInventory().getContainerSize(); i++) {
                var s = v.getInventory().getItem(i);
                if (com.chipoodle.devilrpg.world.VillageStorage.esLena(s)) {
                    encima += s.getCount();
                }
            }
            var wt = v.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
            linea.append("\n    ").append(cocinero ? "COCINERO" : "HERRERO").append(' ')
                    .append(v.getUUID().toString().substring(0, 8))
                    .append(" pos=").append(v.blockPosition().toShortString())
                    .append(" lenaEncima=").append(encima)
                    .append(" destino=").append(wt == null ? "SIN DESTINO"
                            : wt.getTarget().currentBlockPosition().toShortString())
                    .append(" etiqueta=").append(v.getCustomName() == null ? "-"
                            : v.getCustomName().getString().replace("\n", " | "));
        }
        DevilRpg.LOGGER.info("[Arnes] t={} COMBUSTIBLE: lenaEnAlmacen={} (reserva={}) carneCrudaEnDespensa={}{}",
                level.getGameTime(), lena, com.chipoodle.devilrpg.world.VillageStorage.RESERVA_LENA, crudo, linea);
    }

    /**
     * La ruta <b>con detalle</b>: con `accuracy` 1 y 2, cuantos nodos tiene, si de verdad <b>ALCANZA</b>
     * (`canReach`, que es lo que exige vanilla en `AcquirePoi`) y <b>donde acaba</b> (y a que distancia del destino).
     * Es lo que distingue "hay ruta" de "la ruta llega": una cama es un bloque al que no se puede subir, asi que la
     * ruta termina AL LADO y `canReach` puede decir que no.
     */
    private static String rutaDetallada(Villager v, BlockPos destino) {
        StringBuilder sb = new StringBuilder();
        for (int acc : new int[]{1, 2}) {
            try {
                var camino = v.getNavigation().createPath(destino, acc);
                if (camino == null) {
                    sb.append(" a").append(acc).append("=NO(nula)");
                    continue;
                }
                var fin = camino.getEndNode();
                double d = fin == null ? -1.0D : Math.sqrt(fin.asBlockPos().distSqr(destino));
                sb.append(" a").append(acc).append('=').append(camino.getNodeCount()).append("n alcance=")
                        .append(camino.canReach() ? "SI" : "NO").append(" fin=")
                        .append(fin == null ? "?" : fin.asBlockPos().toShortString())
                        .append(" dFin=").append(fmt(d));
            } catch (RuntimeException e) {
                sb.append(" a").append(acc).append("=ERROR:").append(e.getClass().getSimpleName());
            }
        }
        return sb.toString().trim();
    }

    /** ¿Encuentra el aldeano camino hasta esa celda? ("SI"/"NO"/"?"): es `PathNavigation.createPath`. */    private static String ruta(ServerLevel level, Villager v, BlockPos destino) {
        try {
            var camino = v.getNavigation().createPath(destino, 1);
            if (camino == null) {
                return "NO(nulo)";
            }
            return camino.getNodeCount() > 0 ? "SI(" + camino.getNodeCount() + ")" : "NO(vacio)";
        } catch (RuntimeException e) {
            return "ERROR:" + e.getClass().getSimpleName();
        }
    }

    /**
     * LAS CAMAS: aldeano por aldeano, si tiene cama en la memoria del cerebro, si esta durmiendo y que actividad tiene
     * activa (REST/WORK/MEET), con la hora del mundo. Es lo que mide "por que dice Sin cama si sobran camas".
     * <p>
     * Y EL VIGILANTE: se guarda la cama de cada aldeano de la pasada anterior, y cuando CAMBIA se canta la transicion
     * con el estado de la cama vieja, que es lo que dice QUIEN la borra. La sospecha (codigo de vanilla,
     * {@code ValidateNearbyPoi}, que el aldeano lleva registrado para {@code HOME}):
     * <pre>
     *   if (!poiManager.exists(pos, HOME))            memory.erase();   // (a) la cama ya no esta
     *   else if (bedIsOccupied(level, pos, entity))    memory.erase();   // (b) OCCUPIED y el no duerme
     * </pre>
     * Asi que al perderla se imprime: el bloque que hay, si el POI existe, la propiedad OCCUPIED de la cama, quien
     * duerme en ella (aldeano o jugador), quien mas la tiene en el cerebro y a que distancia estaba el aldeano.
     */
    private static final java.util.Map<String, String> camaAnterior = new java.util.HashMap<>();
    /**
     * Radio del censo de camas: el MOD reparte camas a los aldeanos dentro de {@code FALLEN_CHECK_RADIUS}
     * ({@code FENCE_RADIUS + 44} = 106), así que el arnés tiene que censar lo mismo. Con 64 (lo que medía antes) los
     * aldeanos que están más lejos de la plaza —justo los que se quedan sin cama— no salían en el recuento y el
     * resumen decía "SIN CAMA=0" con el jugador viendo "Sin cama" encima de Mauricio.
     */
    private static final double RADIO_CENSO = com.chipoodle.devilrpg.world.VillageGenerator.FENCE_RADIUS + 44.0;

    private static void volcarCamas(ServerLevel level) {
        DevilRpg.LOGGER.info("[Arnes] CAMAS: dayTime={} (franja {}) · UN BICHO DENTRO: {}",
                level.getDayTime() % 24000, level.getDayTime() % 24000 >= 12000 ? "DESCANSO" : "dia",
                estadoDelRecinto(level));
        // EL RESUMEN (el criterio de "arreglado"): cuantos aldeanos tienen cama, cuantos COMPARTEN cama (dos aldeanos
        // con la misma cama: la mitad de la misma cama o la misma casilla) y quien se queda SIN cama. SE CENSAN
        // TAMBIEN LAS CRIAS: el reparto las incluia en el debe (Mauricio y Leoncio eran crias con "Sin cama" encima) y
        // con el censo solo de adultos el resumen decia "SIN CAMA=0" con el jugador viendo lo contrario.
        int adultos = 0;
        int crias = 0;
        int conCama = 0;
        int durmiendo = 0;
        java.util.Map<String, java.util.List<String>> porCama = new java.util.TreeMap<>();
        java.util.List<String> sinCama = new java.util.ArrayList<>();
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(RADIO_CENSO))) {
            String uuid = v.getUUID().toString().substring(0, 8);
            var home = v.getBrain().getMemory(MemoryModuleType.HOME);
            String cama = home.map(g -> g.pos().toShortString()).orElse("NINGUNA");
            String existe = "";
            if (home.isPresent()) {
                BlockPos p = home.get().pos();
                boolean poi = level.getPoiManager().getType(p).isPresent();
                String bloque = level.getBlockState(p).getBlock().toString()
                        .replace("Block{minecraft:", "").replace("}", "");
                existe = " poi=" + (poi ? "SI" : "NO") + " bloque=" + bloque + " " + estadoDeLaCama(level, p, v);
            }
            String anterior = camaAnterior.put(uuid, cama);
            if (v.isBaby()) {
                crias++;
            } else {
                adultos++;
            }
            if (v.isSleeping()) {
                durmiendo++;
            }
            if (home.isPresent()) {
                conCama++;
                porCama.computeIfAbsent(claveDeLaCama(level, home.get().pos()), k -> new java.util.ArrayList<>())
                        .add(uuid);
            } else {
                sinCama.add(uuid + "(" + str(v.getVillagerData().getProfession())
                        + (v.isBaby() ? ",cria" : "") + ")");
            }
            if (anterior != null && !anterior.equals(cama)) {
                if ("NINGUNA".equals(cama)) {
                    DevilRpg.LOGGER.info("[Arnes] CAMA PERDIDA {} prof={} tenia={} -> SIN CAMA · {} · pos={}",
                            uuid, str(v.getVillagerData().getProfession()), anterior,
                            diagnosticoDeLaCama(level, anterior, v), v.blockPosition().toShortString());
                } else {
                    DevilRpg.LOGGER.info("[Arnes] CAMA RECLAMADA {} prof={} {} -> {} · {}",
                            uuid, str(v.getVillagerData().getProfession()),
                            "NINGUNA".equals(anterior) ? "SIN CAMA" : anterior, cama,
                            diagnosticoDeLaCama(level, cama, v));
                }
            }
            DevilRpg.LOGGER.info("[Arnes] CAMA {} nombre={} prof={} home={}{} durmiendo={} REST={} WORK={} MEET={} pos={}",
                    uuid, v.getCustomName() == null ? "-" : v.getCustomName().getString().replace("\n", " | "),
                    str(v.getVillagerData().getProfession()),
                    cama, existe, v.isSleeping(),
                    v.getBrain().isActive(Activity.REST), v.getBrain().isActive(Activity.WORK),
                    v.getBrain().isActive(Activity.MEET), v.blockPosition().toShortString());
        }
        if (ticks % 200 == 0) {
            StringBuilder compartidas = new StringBuilder();
            int ok = 0;
            for (var entrada : porCama.entrySet()) {
                if (entrada.getValue().size() > 1) {
                    compartidas.append(" [").append(entrada.getKey()).append(": ")
                            .append(String.join("+", entrada.getValue())).append(']');
                } else {
                    ok++;
                }
            }
            DevilRpg.LOGGER.info("[Arnes] CAMAS RESUMEN: aldeanos={} (adultos={} crias={}) conCama={} (camas distintas"
                            + " ocupadas={}) COMPARTIDAS={}{} SIN CAMA={}{} DURMIENDO={} · UN BICHO DENTRO: {}",
                    adultos + crias, adultos, crias, conCama, ok, porCama.size() - ok, compartidas, sinCama.size(),
                    sinCama.isEmpty() ? "" : " " + String.join(" ", sinCama), durmiendo, estadoDelRecinto(level));
            // Y POR QUE NO LE DAN CAMA: para el primer aldeano sin cama, las 8 camas libres mas cercanas con el
            // motivo por el que la reclamacion las descarta (o la acepta).
            for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(RADIO_CENSO))) {
                if (v.getBrain().hasMemoryValue(MemoryModuleType.HOME)) {
                    continue;
                }
                DevilRpg.LOGGER.info("[Arnes] SIN CAMA {} {} nombre={} pos={} (dist a la plaza {})", uuid8(v),
                        str(v.getVillagerData().getProfession()),
                        v.getCustomName() == null ? "-" : v.getCustomName().getString().replace("\n", " | "),
                        v.blockPosition().toShortString(),
                        fmt(Math.sqrt(v.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(CENTRO)))));
                var poi = level.getPoiManager();
                poi.findAllClosestFirstWithType(h -> h.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.HOME),
                                p -> true, v.blockPosition(), 48,
                                net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.HAS_SPACE)
                        .limit(8)
                        .forEach(par -> {
                            BlockPos p = par.getSecond();
                            var est = level.getBlockState(p);
                            String bloque = est.getBlock().toString().replace("Block{minecraft:", "")
                                    .replace("}", "");
                            boolean ocupada = est.hasProperty(net.minecraft.world.level.block.BedBlock.OCCUPIED)
                                    && est.getValue(net.minecraft.world.level.block.BedBlock.OCCUPIED);
                            String pareja = "?";
                            if (est.hasProperty(net.minecraft.world.level.block.BedBlock.PART)
                                    && est.hasProperty(net.minecraft.world.level.block.BedBlock.FACING)) {
                                var hacia = est.getValue(net.minecraft.world.level.block.BedBlock.PART)
                                        == net.minecraft.world.level.block.state.properties.BedPart.FOOT
                                        ? est.getValue(net.minecraft.world.level.block.BedBlock.FACING)
                                        : est.getValue(net.minecraft.world.level.block.BedBlock.FACING).getOpposite();
                                var pe = level.getBlockState(p.relative(hacia));
                                boolean peOcupada = pe.hasProperty(net.minecraft.world.level.block.BedBlock.OCCUPIED)
                                        && pe.getValue(net.minecraft.world.level.block.BedBlock.OCCUPIED);
                                pareja = pe.getBlock().toString().replace("Block{minecraft:", "").replace("}", "")
                                        + " ocupada=" + peOcupada;
                            }
                            DevilRpg.LOGGER.info("[Arnes]   CAMA CANDIDATA {} bloque={} ocupada={} pareja=[{}] {}"
                                            + " dist={}", p.toShortString(), bloque, ocupada, pareja,
                                    rutaDetallada(v, p),
                                    fmt(Math.sqrt(v.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(p)))));
                        });
                break; // con uno basta para ver el motivo
            }
            // LAS PUERTAS ABIERTAS DEL PUEBLO (lo pidio el jugador: "los aldeanos cuando vayan a dormir tienen que
            // cerrar la puerta porque todas la dejan abierta").
            volcarPuertas(level);
        }
    }

    private static String uuid8(Villager v) {
        return v.getUUID().toString().substring(0, 8);
    }

    /**
     * LAS PUERTAS DE MADERA ABIERTAS del pueblo (la mitad de abajo de cada una). Lo que se busca con el arreglo es que
     * el numero <b>BAJE</b>: los aldeanos cierran la que cruzan (`VillagerDoorGoal`). Las que estan abiertas y nadie
     * cruza no se tocan (pueden ser del jugador), asi que el numero no tiene por que llegar a cero.
     */
    private static void volcarPuertas(ServerLevel level) {
        StringBuilder abiertas = new StringBuilder();
        int cuantas = 0;
        int atrapados = 0;
        java.util.Set<Long> ahora = new java.util.HashSet<>();
        for (BlockPos q : BlockPos.betweenClosed(CENTRO.offset(-56, -8, -56), CENTRO.offset(56, 12, 56))) {
            var est = level.getBlockState(q);
            if (!est.is(net.minecraft.tags.BlockTags.WOODEN_DOORS)) {
                continue;
            }
            boolean abajo = est.getValue(net.minecraft.world.level.block.DoorBlock.HALF)
                    == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER;
            if (!est.getValue(net.minecraft.world.level.block.DoorBlock.OPEN)) {
                // PUERTA CERRADA CON ALGUIEN DENTRO: es el bug que reportó el jugador ("un aldeano lo movió y empezó a
                // caminar erráticamente"): el aldeano queda atrapado contra la caja de colisión de la puerta cerrada.
                // OJO con el criterio: NO vale "la caja de la entidad TOCA la celda" (un aldeano en la celda de al
                // lado roza la puerta con el hombro y daría un falso positivo): se cuenta el CENTRO de la entidad
                // DENTRO de la celda de la puerta, que es estar de verdad en el hueco.
                if (abajo) {
                    for (net.minecraft.world.entity.Entity e : level.getEntitiesOfClass(
                            net.minecraft.world.entity.Entity.class, new AABB(q).inflate(1.0D, 0.5D, 1.0D))) {
                        var p = e.position();
                        boolean dentro = p.x >= q.getX() && p.x < q.getX() + 1.0D
                                && p.z >= q.getZ() && p.z < q.getZ() + 1.0D
                                && p.y >= q.getY() - 0.2D && p.y <= q.getY() + 2.0D;
                        if (dentro) {
                            atrapados++;
                            DevilRpg.LOGGER.info("[Arnes] PUERTA CERRADA CON ALGUIEN DENTRO en {}: {} pos=({},{},{})"
                                            + " velocidad={}",
                                    q.toShortString(), e.getType().toShortString(), fmt(p.x), fmt(p.y), fmt(p.z),
                                    fmt(e.getDeltaMovement().horizontalDistance()));
                        }
                    }
                }
                continue;
            }
            if (abajo) {
                cuantas++;
                ahora.add(q.asLong());
                if (cuantas <= 8) {
                    abiertas.append(' ').append(q.toShortString());
                }
            }
        }
        // Y LAS QUE SE HAN CERRADO desde el barrido anterior, con el aldeano que tenia al lado: es la prueba de que
        // las cierra el pueblo (`VillagerDoorGoal`).
        for (long antes : puertasAbiertasAnteriores) {
            if (ahora.contains(antes)) {
                continue;
            }
            BlockPos p = BlockPos.of(antes);
            StringBuilder quien = new StringBuilder();
            for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(p).inflate(4.0))) {
                quien.append(' ').append(uuid8(v));
                // Y SU ETIQUETA: el que la ha cerrado la tiene puesta ("Cerrando la puerta": la escribe el goal), asi
                // que esto dice QUIEN la cerro y no solo quien andaba cerca.
                if (v.getCustomName() != null) {
                    quien.append('(').append(v.getCustomName().getString().replace("\n", " | ")).append(')');
                }
            }
            DevilRpg.LOGGER.info("[Arnes] PUERTA CERRADA en {} (aldeano(s) al lado:{})", p.toShortString(), quien);
        }
        puertasAbiertasAnteriores = ahora;
        DevilRpg.LOGGER.info("[Arnes] PUERTAS DE MADERA ABIERTAS en el pueblo: {}{} (cerradas CON alguien dentro: {})",
                cuantas, abiertas, atrapados);
    }

    /** Las puertas abiertas del barrido anterior (para cantar las que se cierran). */
    private static java.util.Set<Long> puertasAbiertasAnteriores = new java.util.HashSet<>();

    /**
     * EL QUE ESTA DENTRO DE UN BANCAL (de noche): se imprime su posicion exacta, su cama, el destino de su cerebro
     * ({@code WALK_TARGET}), los <b>goals que tiene corriendo</b> (es lo que dice si la pierna de salir del bancal del
     * goal del granjero esta activa) y el <b>estado de las cuatro compuertas</b> de su bancal (abierta/cerrada), que
     * es lo que decide si puede salir.
     */
    private static void vigilarGranjerosEnElBancal(ServerLevel level) {
        int cota = com.chipoodle.devilrpg.world.VillageGenerator.cotaDeLaPlaza(level, CENTRO);
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(96))) {
            if (v.isBaby()) {
                continue;
            }
            int dentro = -1;
            for (int i = 0; i < com.chipoodle.devilrpg.world.VillageGenerator.parcelasDeGranja(); i++) {
                if (com.chipoodle.devilrpg.world.VillageGenerator.estaDentroDeLaParcela(CENTRO, i, cota,
                        v.blockPosition())) {
                    dentro = i;
                }
            }
            if (dentro < 0) {
                continue;
            }
            StringBuilder goals = new StringBuilder();
            for (net.minecraft.world.entity.ai.goal.WrappedGoal w : v.goalSelector.getAvailableGoals()) {
                if (w.isRunning()) {
                    goals.append(w.getGoal().getClass().getSimpleName()).append(' ');
                }
            }
            var wt = v.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
            var home = v.getBrain().getMemory(MemoryModuleType.HOME);
            StringBuilder portones = new StringBuilder();
            for (BlockPos p : com.chipoodle.devilrpg.world.VillageGenerator.portonesDeLaParcela(CENTRO, dentro, cota)) {
                var est = level.getBlockState(p);
                boolean abierta = est.hasProperty(net.minecraft.world.level.block.FenceGateBlock.OPEN)
                        && est.getValue(net.minecraft.world.level.block.FenceGateBlock.OPEN);
                portones.append(' ').append(p.toShortString()).append(abierta ? "=ABIERTA" : "=cerrada");
            }
            DevilRpg.LOGGER.info("[Arnes] EN-BANCAL {} {} dentro={} pos=({},{},{}) durmiendo={} REST={} home={}"
                            + " destino={} goals=[{}] compuertas:{}", uuid8(v),
                    str(v.getVillagerData().getProfession()), dentro, fmt(v.getX()), fmt(v.getY()), fmt(v.getZ()),
                    v.isSleeping(), v.getBrain().isActive(Activity.REST),
                    home.map(g -> g.pos().toShortString()).orElse("NINGUNA"),
                    wt == null ? "SIN DESTINO" : wt.getTarget().currentBlockPosition().toShortString(),
                    goals.toString().trim(), portones);
        }
    }

    /** La cama que tenia cada aldeano en el tick ANTERIOR, y como estaba (para el vigilante de cada tick). */
    private static final java.util.Map<String, String> camaTickAnterior = new java.util.HashMap<>();
    private static final java.util.Map<String, String> estadoTickAnterior = new java.util.HashMap<>();

    /**
     * <b>VIGILANTE A RESOLUCIÓN DE TICK</b>: se llama cada tick de servidor y solo escribe cuando la cama de un
     * aldeano <b>cambia</b>. Al <b>PERDERLA</b> imprime cómo estaba la cama <b>en el tick anterior</b>, que es lo que
     * identifica al culpable: vanilla ({@code ValidateNearbyPoi}) borra el {@code HOME} si el <b>POI ya no está</b> o
     * si la cama está <b>{@code OCCUPIED}</b> y el aldeano <b>no</b> está durmiendo (y solo mira a ≤16 bloques).
     */
    private static void vigilarCamasCadaTick(ServerLevel level) {
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(RADIO_CENSO))) {
            if (v.isBaby()) {
                continue;
            }
            String uuid = uuid8(v);
            var home = v.getBrain().getMemory(MemoryModuleType.HOME);
            String cama = home.map(g -> g.pos().toShortString()).orElse("NINGUNA");
            String estado = home.map(g -> "poi=" + (level.getPoiManager().getType(g.pos()).isPresent() ? "SI" : "NO")
                    + " " + estadoDeLaCama(level, g.pos(), v)).orElse("-");
            String antes = camaTickAnterior.put(uuid, cama);
            String estadoAntes = estadoTickAnterior.put(uuid, estado);
            if (antes == null || antes.equals(cama)) {
                continue;
            }
            if ("NINGUNA".equals(cama)) {
                DevilRpg.LOGGER.info("[Arnes] PERDIDA-TICK {} {} tenia={} · EN EL TICK ANTERIOR: {} · pos={}"
                                + " durmiendo={} REST={} dist={} · MEMORIAS QUE LE QUEDAN={} · ACTIVIDADES={}", uuid,
                        str(v.getVillagerData().getProfession()), antes, estadoAntes,
                        v.blockPosition().toShortString(), v.isSleeping(), v.getBrain().isActive(Activity.REST),
                        fmt(Math.sqrt(v.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(
                                posDe(antes))))),
                        memoriasPresentes(v), v.getBrain().getActiveActivities());
            } else {
                DevilRpg.LOGGER.info("[Arnes] RECLAMADA-TICK {} {} {} -> {} · {}", uuid,
                        str(v.getVillagerData().getProfession()),
                        "NINGUNA".equals(antes) ? "SIN CAMA" : antes, cama, estado);
            }
        }
    }

    /** Las memorias que le QUEDAN al aldeano (solo las que tienen valor): dice si el borrado es de HOME o de todo. */
    private static String memoriasPresentes(Villager v) {
        StringBuilder sb = new StringBuilder();
        for (var entrada : v.getBrain().getMemories().entrySet()) {
            if (entrada.getValue().isPresent()) {
                String nombre = String.valueOf(entrada.getKey()).replace("minecraft:", "");
                String valor = String.valueOf(entrada.getValue().get());
                if (valor.length() > 40) {
                    valor = valor.substring(0, 40) + "...";
                }
                sb.append(nombre).append('=').append(valor).append(" | ");
            }
        }
        return sb.length() == 0 ? "(ninguna)" : sb.toString().trim();
    }

    /**
     * SONDA DE RUTAS DEL ALDEANO SIN CAMA (temporal): desde su posicion, prueba la ruta a las celdas que importan
     * (su cama, la compuerta de la habitacion, el pasillo, el interior) para ver DONDE se corta el camino. Imprime
     * tambien el bloque que tiene debajo y a los lados, que es lo que suele explicar el corte.
     */
    private static void sondarRutasDelSinCama(ServerLevel level) {
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(96))) {
            if (v.isBaby() || v.getBrain().hasMemoryValue(MemoryModuleType.HOME)) {
                continue;
            }
            BlockPos p = v.blockPosition();
            DevilRpg.LOGGER.info("[Arnes] SONDA {} {} pos={} debajo={} suelo={} norte={} sur={} este={} oeste={}",
                    uuid8(v), str(v.getVillagerData().getProfession()), p.toShortString(),
                    level.getBlockState(p.below()).getBlock(), level.getBlockState(p).getBlock(),
                    level.getBlockState(p.north()).getBlock(), level.getBlockState(p.south()).getBlock(),
                    level.getBlockState(p.east()).getBlock(), level.getBlockState(p.west()).getBlock());
            for (BlockPos destino : new BlockPos[]{
                    new BlockPos(1442, 120, 1401),   // fuera, al oeste de la compuerta
                    new BlockPos(1443, 120, 1401),   // la compuerta (abierta)
                    new BlockPos(1444, 120, 1401),   // justo dentro
                    new BlockPos(1445, 120, 1401),   // dentro, 2
                    new BlockPos(1446, 120, 1401),   // dentro, 3
                    new BlockPos(1447, 120, 1401),   // dentro, 4 (la pared norte esta al este de aqui)
                    new BlockPos(1447, 120, 1403),   // dentro, bajando
                    new BlockPos(1447, 120, 1405),   // dentro, abajo
                    new BlockPos(1450, 120, 1405),   // junto a la cama
                    new BlockPos(1452, 120, 1404)} ) { // al lado de la cabecera
                DevilRpg.LOGGER.info("[Arnes]   RUTA a {} -> {}", destino.toShortString(),
                        rutaDetallada(v, destino));
            }
            BlockPos compuerta = new BlockPos(1443, 120, 1401);
            var est = level.getBlockState(compuerta);
            DevilRpg.LOGGER.info("[Arnes]   COMPUERTA {}: bloque={} colision={} arriba={} abajo={}",
                    compuerta.toShortString(), est.getBlock(),
                    est.getCollisionShape(level, compuerta).isEmpty() ? "vacia" : "NO vacia",
                    level.getBlockState(compuerta.above()).getBlock(),
                    level.getBlockState(compuerta.below()).getBlock());
            break;
        }
    }

    /** La posición de un texto "x, y, z" (el que imprime `BlockPos.toShortString`). */    private static BlockPos posDe(String texto) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(-?\\d+), ?(-?\\d+), ?(-?\\d+)").matcher(texto);
        if (!m.find()) {
            return CENTRO;
        }
        return new BlockPos(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
    }

    /**
     * La clave de la <b>cama entera</b> (las dos mitades son dos POIs distintos): así dos aldeanos que tienen una
     * mitad cada uno cuentan como cama COMPARTIDA, que es justo el caso que vanilla castiga borrándole el HOME al que
     * no duerme.
     */
    private static String claveDeLaCama(ServerLevel level, BlockPos p) {
        var estado = level.getBlockState(p);
        BlockPos pareja = null;
        if (estado.hasProperty(net.minecraft.world.level.block.BedBlock.PART)
                && estado.hasProperty(net.minecraft.world.level.block.BedBlock.FACING)) {
            var hacia = estado.getValue(net.minecraft.world.level.block.BedBlock.PART)
                    == net.minecraft.world.level.block.state.properties.BedPart.FOOT
                    ? estado.getValue(net.minecraft.world.level.block.BedBlock.FACING)
                    : estado.getValue(net.minecraft.world.level.block.BedBlock.FACING).getOpposite();
            pareja = p.relative(hacia);
        }
        if (pareja == null) {
            return p.toShortString();
        }
        String a = p.toShortString();
        String b = pareja.toShortString();
        return a.compareTo(b) <= 0 ? a + "+" + b : b + "+" + a;
    }

    /** Lo que se puede leer de una cama: OCCUPIED, quien duerme en ella y quien mas la tiene reclamada. */
    private static String estadoDeLaCama(ServerLevel level, BlockPos p, Villager dueno) {
        var estado = level.getBlockState(p);
        String ocupada = estado.hasProperty(net.minecraft.world.level.block.BedBlock.OCCUPIED)
                ? String.valueOf(estado.getValue(net.minecraft.world.level.block.BedBlock.OCCUPIED)) : "?";
        StringBuilder quien = new StringBuilder();
        for (net.minecraft.world.entity.LivingEntity e : level.getEntitiesOfClass(
                net.minecraft.world.entity.LivingEntity.class, new AABB(p).inflate(3.0))) {
            if (e.isSleeping() && e.blockPosition().closerToCenterThan(net.minecraft.world.phys.Vec3.atCenterOf(p), 3.0)) {
                quien.append(e == dueno ? " EL MISMO" : " " + e.getType().toShortString());
            }
        }
        return "OCCUPIED=" + ocupada + " durmiendoEnElla=[" + quien.toString().trim() + "]";
    }

    /** El parte de la cama que se acaba de perder (o de la que se acaba de reclamar): bloque, POI, dueno y distancia. */
    private static String diagnosticoDeLaCama(ServerLevel level, String posTexto, Villager v) {
        BlockPos p = null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(-?\\d+), ?(-?\\d+), ?(-?\\d+)").matcher(posTexto);
        if (m.find()) {
            p = new BlockPos(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        }
        if (p == null) {
            return "no se pudo leer la posicion '" + posTexto + "'";
        }
        boolean poi = level.getPoiManager().getType(p).isPresent();
        String bloque = level.getBlockState(p).getBlock().toString().replace("Block{minecraft:", "").replace("}", "");
        StringBuilder duenos = new StringBuilder();
        for (Villager otro : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(64))) {
            var suya = otro.getBrain().getMemory(MemoryModuleType.HOME);
            if (suya.isPresent() && suya.get().pos().equals(p)) {
                duenos.append(otro == v ? " EL" : " " + otro.getUUID().toString().substring(0, 8));
            }
            // ¿La OTRA MITAD de la cama (la pareja de bloques) es de alguien? Vanilla cuenta cada mitad como un POI
            // HOME, asi que dos aldeanos pueden acabar "compartiendo" cama y al que no duerme le borra el HOME.
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                BlockPos mitad = p.relative(d);
                if (level.getBlockState(mitad).is(net.minecraft.tags.BlockTags.BEDS)
                        && suya.isPresent() && suya.get().pos().equals(mitad)) {
                    duenos.append(" [otra mitad: ").append(otro.getUUID().toString().substring(0, 8)).append(']');
                }
            }
        }
        double dist = Math.sqrt(v.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(p)));
        return "poi=" + (poi ? "SI" : "NO") + " bloque=" + bloque + " " + estadoDeLaCama(level, p, v)
                + " duennos=[" + duenos.toString().trim() + "] dist=" + fmt(dist) + " (vanilla borra si dist<=16 y"
                + " la cama esta ocupada o el POI no existe)";
    }

    /** Los objetos tirados por el pueblo (a eso va el recolector) y los que estén en la taberna, sobre todo arriba. */
    private static void volcarObjetos(ServerLevel level) {
        BlockPos taberna = com.chipoodle.devilrpg.world.VillageGenerator.baseDeLaTaberna(CENTRO);
        int cota = com.chipoodle.devilrpg.world.VillageGenerator.cotaDeLaPlaza(level, CENTRO);
        int total = 0;
        int enLaTaberna = 0;
        int arriba = 0;
        for (net.minecraft.world.entity.item.ItemEntity it : level.getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class, new AABB(CENTRO).inflate(140))) {
            total++;
            BlockPos p = it.blockPosition();
            if (Math.abs(p.getX() - taberna.getX()) <= 20 && Math.abs(p.getZ() - taberna.getZ()) <= 16) {
                enLaTaberna++;
                if (p.getY() > cota + 6) {
                    arriba++;
                    DevilRpg.LOGGER.info("[Arnes] OBJETO arriba en la taberna: {} x{} en {}", 
                            it.getItem().getHoverName().getString(), it.getItem().getCount(), p.toShortString());
                }
            }
        }
        DevilRpg.LOGGER.info("[Arnes] OBJETOS en el pueblo: {} (en la taberna: {}, de esos por encima del forjado: {})",
                total, enLaTaberna, arriba);
    }

    private static void volcar(ServerLevel level) {
        java.util.Map<String, Integer> censo = new java.util.TreeMap<>();
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(96))) {
            censo.merge(v.isBaby() ? "CRIA" : str(v.getVillagerData().getProfession()), 1, Integer::sum);
            boolean esClerigo = !v.isBaby()
                    && v.getVillagerData().getProfession() == net.minecraft.world.entity.npc.VillagerProfession.CLERIC;
            // LA GRANJA (lo que se mide en esta ronda): se sigue a los GRANJEROS, con su bancal y si van subidos a la
            // valla (el bug: trepaban por el compostero pegado a ella).
            boolean esGranjero = !v.isBaby()
                    && v.getVillagerData().getProfession() == net.minecraft.world.entity.npc.VillagerProfession.FARMER;
            // EL RECOLECTOR (holgazan): que goal tiene ACTIVO y a donde va, que es lo que hay que medir ahora (el
            // jugador lo vio "de charla en el 3er piso sin hacer nada").
            boolean esRecolector = !v.isBaby()
                    && v.getVillagerData().getProfession() == net.minecraft.world.entity.npc.VillagerProfession.NITWIT;
            if (esRecolector) {
                StringBuilder activos = new StringBuilder();
                for (net.minecraft.world.entity.ai.goal.WrappedGoal w : v.goalSelector.getAvailableGoals()) {
                    if (w.isRunning()) {
                        activos.append(w.getGoal().getClass().getSimpleName()).append(' ');
                    }
                }
                WalkTarget wtr = v.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
                // ¿ENCUENTRA CAMINO? Es la pregunta de esta ronda: se queda clavado en el desvan con el almacen como
                // destino, asi que se prueban las rutas al almacen, a la plaza y al hueco del desvan.
                int cotaRec = com.chipoodle.devilrpg.world.VillageGenerator.cotaDeLaPlaza(level, CENTRO);
                String rutaAlmacen = ruta(level, v, new BlockPos(1461, 121, 1434));
                String rutaPlaza = ruta(level, v, new BlockPos(CENTRO.getX(), cotaRec, CENTRO.getZ()));
                String rutaHueco = ruta(level, v, new BlockPos(1442, 131, 1438));
                DevilRpg.LOGGER.info("[Arnes] t={} {} RECOLECTOR pos=({},{},{}) activos=[{}] destino={} rutas[almacen={}"
                                + " plaza={} huecoDesvan={}] etiqueta={}",
                        level.getGameTime(), v.getUUID().toString().substring(0, 8), fmt(v.getX()), fmt(v.getY()),
                        fmt(v.getZ()), activos.toString().trim(),
                        wtr == null ? "SIN DESTINO" : wtr.getTarget().currentBlockPosition().toShortString(),
                        rutaAlmacen, rutaPlaza, rutaHueco,
                        v.getCustomName() == null ? "-" : v.getCustomName().getString().replace("\n", " | "));
                continue;
            }
            if (esGranjero) {
                int bancal = bancalDe(level, v);
                boolean valla = subidoALaValla(level, v);
                if (valla) {
                    subidasALaValla++;
                }
                WalkTarget wtg = v.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
                var puesto = v.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
                DevilRpg.LOGGER.info("[Arnes] t={} {} GRANJERO bancal={} valla={} puesto={} pos=({},{},{}) destino={}"
                                + " etiqueta={}",
                        level.getGameTime(), v.getUUID().toString().substring(0, 8), bancal, valla ? "SI" : "no",
                        puesto == null ? "SIN PUESTO" : puesto.pos().toShortString(),
                        fmt(v.getX()), fmt(v.getY()), fmt(v.getZ()),
                        wtg == null ? "SIN DESTINO" : wtg.getTarget().currentBlockPosition().toShortString(),
                        v.getCustomName() == null ? "-" : v.getCustomName().getString().replace("\n", " | "));
                continue;
            }
            if (!v.isBaby() && !VillagerGuardGoal.esGuardia(v) && !esClerigo) {
                continue; // de los adultos solo se sigue a la guardia, al CLERIGO y a los GRANJEROS
            }
            WalkTarget wt = v.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
            DevilRpg.LOGGER.info("[Arnes] t={} {} pos=({},{},{}) destino={} oficio={} trabajo={} puesto={} etiqueta={}",
                    level.getGameTime(), v.getUUID().toString().substring(0, 8), fmt(v.getX()), fmt(v.getY()),
                    fmt(v.getZ()),
                    wt == null ? "SIN DESTINO" : wt.getTarget().currentBlockPosition().toShortString(),
                    str(v.getVillagerData().getProfession()), v.getBrain().isActive(Activity.WORK),
                    v.getPersistentData().getInt(VillageManager.GUARD_INDEX_TAG),
                    v.getCustomName() == null ? "-" : v.getCustomName().getString().replace("\n", " | "));
        }
        // EL RESUMEN DE LA GRANJA: cuantos granjeros hay en cada bancal (el reparto) y cuantas veces se les ha visto
        // subidos a la valla desde que arranco el arnes.
        StringBuilder bancales = new StringBuilder();
        for (int i = 0; i < PARCELAS.length; i++) {
            int enEl = 0;
            for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(CENTRO).inflate(96))) {
                if (!v.isBaby() && v.getVillagerData().getProfession()
                        == net.minecraft.world.entity.npc.VillagerProfession.FARMER && bancalDe(level, v) == i) {
                    enEl++;
                }
            }
            BlockPos comp = composteroDelBancal(level, i);
            boolean hayCompostero = level.getBlockState(comp).is(net.minecraft.world.level.block.Blocks.COMPOSTER);
            bancales.append(" bancal").append(i).append("=granjeros:").append(enEl)
                    .append("/compostero:").append(hayCompostero ? "SI" : "NO");
        }
        DevilRpg.LOGGER.info("[Arnes] t={} GRANJA {} · subidas a la valla (acumulado): {}",
                level.getGameTime(), bancales, subidasALaValla);
        DevilRpg.LOGGER.info("[Arnes] t={} CENSO {}", level.getGameTime(), censo);
    }

    private static String str(Object o) {
        return String.valueOf(o).replace("minecraft:", "");
    }

    private static String fmt(double d) {
        return String.format(Locale.ROOT, "%.2f", d);
    }
}

