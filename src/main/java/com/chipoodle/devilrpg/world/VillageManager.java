package com.chipoodle.devilrpg.world;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.capability.experience.PlayerExperienceCapability;
import com.chipoodle.devilrpg.capability.experience.PlayerExperienceCapabilityInterface;
import com.chipoodle.devilrpg.entity.AggressiveZombieEntity;
import com.chipoodle.devilrpg.entity.goal.VillagerCollectGoal;
import com.chipoodle.devilrpg.entity.goal.VillagerFarmGoal;
import com.chipoodle.devilrpg.entity.goal.VillagerGuardGoal;
import com.chipoodle.devilrpg.entity.goal.VillagerLumberjackGoal;
import com.chipoodle.devilrpg.entity.goal.VillagerRepairGoal;
import com.chipoodle.devilrpg.init.ModEntities;
import com.chipoodle.devilrpg.survival.ObjectiveTargets;
import com.chipoodle.devilrpg.util.MissionRewards;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Gestor del asedio a la primera aldea. La aldea se pre-genera antes de que el jugador llegue; al llegar,
 * tras un pequeño margen para explorar, se lanza una ola de monstruos desde fuera de la valla. Si el
 * jugador la limpia a tiempo -> la aldea se salva (recompensa + avanza el objetivo); si no -> cae (avanza
 * sin recompensa).
 */
public final class VillageManager {

    /** Distancia a la que se pre-genera la aldea antes de llegar el jugador. */
    public static final int PRE_GENERATE_RADIUS = 140;
    /** Radio de llegada al objetivo (se considera "en la aldea"). */
    public static final int ARRIVE_RADIUS = 24;
    /** Radio al que se AVISA al jugador de que hay una aldea cerca (antes de llegar). */
    public static final int NOTICE_RADIUS = 100;
    /** Ticks de margen para explorar la aldea antes del asedio (90 s). */
    private static final int GRACE_TICKS = 90 * 20;
    /** Ticks extra para limpiar la ola tras el asedio (2 min). */
    private static final int SIEGE_TIMEOUT_TICKS = 2 * 60 * 20;
    /** Número base de monstruos agresivos en la ola (escala con el índice del objetivo). */
    private static final int DEFAULT_WAVE = 8;
    /** Incremento máximo de la ola por alejarse (límite: no crece infinitamente). */
    private static final int MAX_WAVE_EXTRA = 20;
    /**
     * Zona mínima/máxima (bloques) a la que spawnea la ola: <b>derivada del radio de la valla</b>, siempre FUERA.
     * <p>
     * Antes eran 32/40 fijos: cuando la valla estaba en 36 los monstruos habrían aparecido <b>dentro</b> del muro.
     * <b>Hoy la valla está en 62</b> ({@code VillageGenerator.FENCE_RADIUS}), así que la ola nace a 65-73 del
     * centro: un pueblo grande NO se defiende "en las afueras" — casi todo lo que rodea la plaza está dentro.
     */
    private static final int WAVE_SPAWN_MIN = VillageGenerator.FENCE_RADIUS + 3;
    private static final int WAVE_SPAWN_MAX = VillageGenerator.FENCE_RADIUS + 11;
    /**
     * Radio del <b>perímetro</b> de la aldea (la valla, {@code VillageGenerator.FENCE_RADIUS}): a partir de
     * aquí se considera que un zombie del asedio <b>no ha entrado</b>.
     */
    private static final int PERIMETER_RADIUS = VillageGenerator.FENCE_RADIUS;
    /**
     * Banda <b>vertical</b> del recinto: por debajo de la cota se admiten {@code RECINTO_DY_ABAJO} bloques (una
     * zanja, la acequia, el corral algo más bajo) y por encima {@code RECINTO_DY_ARRIBA} (el segundo piso de la
     * taberna, un tejado, lo alto del muro). Un bicho <b>mucho</b> más abajo (una cueva bajo la plaza) o más
     * arriba (una repisa del monte) NO está dentro del pueblo, aunque su distancia horizontal diga que sí.
     * <p>
     * Medido en el guardado del jugador (aldea 1, cota 95): con la regla vieja —solo horizontal— contaban como
     * "dentro de la aldea" <b>24</b> monstruos, y <b>18</b> de ellos estaban en cuevas (de {@code y=5} a
     * {@code y=89}); con la banda quedan <b>6</b>, todos a la altura del pueblo. Eso tenía dos consecuencias:
     * el latido del pueblo se paraba (no cultivaban, ni comían, ni reparaban, ni se repoblaba) por un esqueleto
     * en una cueva, y un asediador que se metiera en una cueva bajo la plaza hacía CAER la aldea sin que el
     * jugador pudiera verlo ("si no llegan a los muros, no asedian y no pueden ganar", que es la regla que ya
     * estaba escrita para el asedio).
     */
    private static final int RECINTO_DY_ABAJO = 6;
    private static final int RECINTO_DY_ARRIBA = 16;
    /** Cada cuánto se informa del asedio en curso (15 s): un asedio que se pierde a ciegas es una derrota injusta. */
    private static final int SIEGE_STATUS_INTERVAL = 15 * 20;
    /** Cuenta atrás (segundos que quedan) que se avisa aparte, para que el final no pille por sorpresa. */
    private static final int[] SIEGE_WARN_SECONDS = {30, 10};

    // --- Salud del asentamiento (Iteración 3, paso 2) ----------------------------------------------

    /** Aldeanos que tiene una aldea sana (los que pone el generador): es el tope de la "salud". */
    public static final int VILLAGERS_FOR_FULL_HEALTH = 5;
    /** Cada cuánto se repone UN aldeano en una aldea debilitada (5 min). */
    private static final int REPOPULATE_INTERVAL_TICKS = 5 * 60 * 20;
    /**
     * Cuánta presión extra acumula la aldea por cada aldeano que le falta. Con 1 aldeano acumula el doble, y
     * vacía dos veces y media: <i>los monstruos huelen la debilidad</i>.
     */
    private static final double PRESSURE_PER_MISSING_VILLAGER = 0.5D;
    /** Cada cuánto se pasa revista a los aldeanos de una aldea (10 s). */
    private static final int VILLAGE_POLL_TICKS = 200;

    // --- Vida del asentamiento (Iteración 3, paso 4) ------------------------------------------------

    /**
     * Comida que come la aldea: <b>cada minuto de juego</b> se lleva un punto por aldeano vivo de la
     * <b>despensa</b> (ver {@code VillagePantry}). Ya no hay "la granja produce 8" en abstracto: lo que se come es
     * el trigo que el granjero cultiva y el pan que hornea.
     * <p>
     * Desde la etapa E la ración es <b>de cada aldeano</b> ({@link #COMIDA_TAG}): se le da de comer al que hace más
     * tiempo que no come, y el que se queda sin ración pasa hambre <b>él</b> (no "la aldea" en abstracto). Cada
     * ración vale <b>un punto de comida</b> (lo que comía la aldea entera por aldeano y minuto desde el principio):
     * un pan son <b>4 raciones</b>, o sea que un pan da de comer a cuatro aldeanos.
     */
    private static final int EAT_INTERVAL_TICKS = 60 * 20;
    /**
     * Las raciones se reparten en el latido de la aldea que <b>cierra el minuto</b> (un {@code %} sobre
     * {@code gameTime}), así que {@link #EAT_INTERVAL_TICKS} tiene que ser <b>múltiplo</b> de
     * {@link #VILLAGE_POLL_TICKS}: si alguien cambia la cadencia del latido por un número que no lo divida, el
     * minuto no caería nunca en un latido y <b>el pueblo no comería</b> (morirían de hambre en silencio). En vez de
     * confiar en que nadie lo toque, se avisa al cargar la clase.
     */
    static {
        if (EAT_INTERVAL_TICKS % VILLAGE_POLL_TICKS != 0) {
            DevilRpg.LOGGER.error("[Village] EAT_INTERVAL_TICKS ({}) no es multiplo del latido de la aldea ({}):"
                    + " las raciones no se repartirian nunca", EAT_INTERVAL_TICKS, VILLAGE_POLL_TICKS);
        }
    }
    /** Marca (datos persistentes del aldeano) con el <b>gameTime de su última ración</b>: su hambre personal. */
    private static final String COMIDA_TAG = "DevilRpgUltimaComida";
    /**
     * Raciones perdidas antes de que el hambre se note: a partir de aquí el aldeano va con <b>Debilidad</b> y
     * <b>Lentitud</b> (3 raciones = 3 minutos sin comer).
     */
    private static final int HAMBRE_PACIENTE_TICKS = 3 * EAT_INTERVAL_TICKS;
    /** Puntos de comida que da un pan (es lo que vale en vanilla). */
    private static final int FOOD_PER_BREAD = 4;
    /**
     * Tope de la despensa a efectos del contador de comida (el barril real aguanta más, pero con esto basta para
     * que la aldea esté "llena": pan, carne y trigo de sobra).
     */
    private static final int MAX_FOOD = 64;
    /** Comida que cuesta que llegue un aldeano nuevo (nacer o mudarse). */
    private static final int FOOD_TO_GROW = 8;
    /** Tiempo de hambre continua (ticks) antes de que se muera un aldeano: 10 min. */
    private static final long STARVATION_DEATH_TICKS = 10L * 60L * 20L;

    // --- Obrero de la aldea (Iteración 3, A1) ------------------------------------------------------

    /** Marca (en los datos persistentes del aldeano) del que es el <b>obrero</b> de la aldea. */
    public static final String BUILDER_TAG = "DevilRpgBuilder";

    // --- Guardia de la aldea (Iteración 3, milicia) ------------------------------------------------

    /** Marca (en los datos persistentes del aldeano) del que está <b>alistado en la guardia</b>. */
    public static final String GUARD_TAG = "DevilRpgGuardia";
    /** Tipo de guardia: 0 = <b>espadachín</b> (espada + escudo), 1 = <b>arquero</b> (arco + flechas). */
    public static final String GUARD_TYPE_TAG = "DevilRpgGuardiaTipo";
    /**
     * Número de guardia (0..{@code MILICIA_MAX-1}): fija su puerta en el relevo nocturno y su secuencia de ronda,
     * para que no se apelotonen ni hagan todos el mismo recorrido.
     */
    public static final String GUARD_INDEX_TAG = "DevilRpgGuardiaPuesto";
    /**
     * Tamaño de la milicia: <b>4 espadachines y 3 arqueros</b>, que es la formación con la que el jugador quiere que
     * marchen a la guarida. Si la aldea cría más gente que eso, los demás siguen con lo suyo (y pueden ser obreros).
     */
    public static final int MILICIA_MAX = 7;
    private static final int MILICIA_ESPADACHINES = 4;
    /**
     * Versión del trazado de la aldea. Se sube cuando cambia el diseño y hay que <b>arreglar las ya construidas</b>:
     * <ul>
     *   <li>1: granja con cultivos, acequia y compostador.</li>
     *   <li>2: la parcela se nivela a un solo nivel, porque antes el agua quedaba un bloque por debajo de la
     *       tierra de cultivo y los cultivos se secaban.</li>
     *   <li>3: el plano de la aldea apunta también lo que está <b>a ras de suelo</b> (composteros, suelos de las
     *       casas, base de la torre, caminos), que antes quedaba fuera y el obrero no reponía.</li>
     *   <li>4: el plano es <b>canónico</b>: lo graba el propio generador mientras construye la aldea
     *       ({@code VillageGenerator.generate} devuelve el plano), en vez de fotografiarla leyendo el mundo. Así
     *       el daño previo (o capturar la aldea en mal momento) ya no se confunde con "lo correcto".</li>
     *   <li>5: las construcciones se nivelan a la <b>mediana</b> de su huella (con la más alta quedaban subidas
     *       sobre un zócalo de tierra) y las casas llevan <b>escalón de entrada</b>.</li>
     *   <li>6: las aldeas viejas cambian sus <b>cabañas procedurales por las casas del juego</b>
     *       (`VillageGenerator.actualizarCasas`), con la marca persistida `hasNewHouses` para no rehacer dos veces
     *       una casa ya nueva.</li>
     *   <li>7: cuarta casa (la "grande", con <b>cama extra</b>) y reparación de la <b>tierra pisoteada</b>. La
     *       versión de casas (`CURRENT_HOUSES`) decide si hay que rehacer las tres viejas o solo añadir la cuarta.</li>
     *   <li>8: las construcciones se nivelan al <b>terreno de alrededor</b> (antes, a la mediana de su propia
     *       huella: si el solar venía alto, la casa quedaba sobre un zócalo), la <b>iglesia</b> del juego sustituye
     *       a la torre procedural y la aldea pasa a <b>4 aldeanos</b>.</li>
     *   <li>9: el suelo de las casas va en el bloque de superficie (antes uno más arriba, así que la casa quedaba
     *       <b>un bloque por encima</b> del patio) y los caminos ya <b>no se dibujan sobre los tejados</b>.</li>
     *   <li>10: el nivel de referencia pasa a ser el <b>mínimo del patio inmediato</b> (anillo a 2 bloques) en vez
     *       de la mediana de un anillo a 4: medido en partida, las 6 puertas de la aldea quedaban a 74 con el patio
     *       a 73, o sea <b>un bloque por encima</b>. Con el mínimo, la construcción queda a ras o algo metida en el
     *       lado alto del terreno, nunca por encima.</li>
     *   <li>11: se vuelve a la <b>mediana del patio inmediato</b> (el mínimo dejaba todas las casas un bloque
     *       hundidas) y el desnivel se salva con el <b>escalón de entrada</b>, como pidió el jugador. Además el
     *       plano capturado por <b>escaneo</b> (aldeas migradas) ya incluye la <b>tierra de cultivo y el agua</b> de
     *       la granja: antes las saltaba, así que el obrero no reponía las parcelas pisoteadas.</li>
     *   <li>12: se allana también una <b>terraza</b> (patio de `MARGEN_TERRAZA` bloques) alrededor de cada
     *       construcción: allanar solo la huella dejaba el patio de al lado con su pendiente y la casa parecía
     *       hundida por un lado. El nivel se saca ahora del anillo que hay <b>justo fuera</b> de la terraza.</li>
     *   <li>13: la aldea entera se nivela a <b>UNA sola cota</b> (`levelTerrain` devuelve esa cota y todas las
     *       construcciones se colocan a ella): nivelar cada casa por su cuenta era lo que dejaba <b>zanjas</b>
     *       alrededor y casas hundidas. La migración vuelve a allanar el terreno (protegiendo lo que no sea
     *       natural) y rehace casas y granja a esa cota.</li>
     *   <li>14: la cota de la aldea se <b>hereda del terreno</b> (`prepararTerreno`: la isla devuelve su nivel y el
     *       terreno se nivela y devuelve su cota) en vez de deducirse muestreando un anillo con `groundY`, que sobre
     *       una casa devuelve el <b>tejado</b>: medido en el guardado, el suelo de la aldea estaba a y=62 y los
     *       suelos de las casas a y=63 (un bloque altos) en una aldea sobre el océano.</li>
     *   <li>15: <b>el renivelado deja de comerse la aldea</b>. Tres arreglos que iban juntos:
     *       <ul>
     *         <li>la cota de una aldea <b>ya construida</b> se lee de la <b>plaza</b> (`cotaDeLaPlaza`), no de la
     *             mediana de toda el área: con las casas puestas esa mediana incluye los <b>tejados</b> y salía un
     *             bloque alta, que es el bloque de más que dejaba las casas "arriba", las <b>zanjas</b> de un
     *             bloque alrededor y el <b>muro enterrado</b> (medido en el guardado: troncos del muro con tierra
     *             encima y el suelo de la plaza a y=62 con los suelos de las casas a y=63);</li>
     *         <li>el nivelado ya no <b>rellena encima de una construcción</b> ni <b>recorta troncos</b>: el relleno
     *             era a ciegas y enterraba el muro, y el recorte daba los troncos por "terreno natural";</li>
     *         <li>el <b>muro se reconstruye</b> en la migración (`VillageGenerator.rehacerMuro`) y sus <b>troncos
     *             entran en el plano</b>, así que el obrero puede reponerlos cuando un asedio los rompe (era el bug
     *             del jugador: al defender la aldea, las maderas del muro no volvían nunca).</li>
     *       </ul></li>
     *   <li>16: cada construcción se coloca <b>alineada por su propia puerta</b> (`alturaDeLaPuerta`), no con la
     *       capa y=0 en `nivel-1`. Leyendo las plantillas del juego, cada una tiene la puerta a una altura distinta:
     *       <ul>
     *         <li>la casa <b>mediana</b> (y la cuarta casa, que siempre es mediana) trae en y=0 una <b>plataforma de
     *             TIERRA de 13x11</b> y la puerta en y=2: con el nivel-1 de antes esa tierra quedaba a la vista como
     *             un borde marrón que el jugador veía como "una zanja" y la casa quedaba un bloque alta;</li>
     *         <li>el <b>templo con campanario</b> (`plains_temple_4`, la iglesia) tiene la puerta en y=0, así que con
     *             el nivel-1 quedaba con la puerta medio enterrada y parecía rota;</li>
     *         <li>y el relleno del nivelado pone <b>césped</b> en la capa que se pisa (antes tierra: se veía como un
     *             parche marrón alrededor de las casas).</li>
     *       </ul>
     *       Además la iglesia es siempre el templo <b>con torre</b>: el otro templo del juego es un edificio bajo sin
     *       torre y sin campana, y el jugador lo veía como un cobertizo ("¿dónde está la iglesia?").</li>
     *   <li>17: <b>se acabó la zanja de un bloque alrededor de las casas</b>. El despeje del solar borra la caja
     *       ENTERA de la plantilla (incluida la capa de césped) y casi todas las plantillas del juego son más
     *       pequeñas que su caja: el borde que queda alrededor de las paredes se quedaba un bloque por debajo del
     *       suelo del pueblo. Ahora, después de colocar la construcción, se devuelve el <b>césped</b> a la capa de
     *       superficie en los huecos del solar (y también donde el nivelado recortó el terreno, que dejaba la
     *       tierra a la vista). Medido en el guardado: el suelo dentro de la caja de la casa mediana estaba en la
     *       capa 61 y el del pueblo en la 62 (un bloque de zanja), con la puerta ya a ras.</li>
     *   <li>18: <b>zanja de DOS bloques de las casas medianas</b>. El despeje del solar empezaba en
     *       {@code origen.y = nivel - alturaDeLaPuerta}, y la casa mediana tiene la puerta en {@code y=2}: se llevaba
     *       <b>dos</b> capas de suelo (61 y 62) y, donde la plantilla no pone nada (su caja es 13x11 y el edificio va
     *       metido hacia dentro, con una plataforma de tierra llena de huecos), el terreno quedaba en la 60. Medido
     *       en el guardado del jugador: {@code 62=aire 61=aire 60=tierra} en la caja de la casa mediana. Ahora el
     *       despeje no baja nunca de la capa de superficie del pueblo y, después de colocar la construcción, se
     *       <b>rellena la columna hasta el suelo del pueblo</b> (césped arriba, tierra debajo) donde la plantilla no
     *       ponga nada. Solo afecta a las casas medianas, así que el jugador veía la zanja de 2 bloques en 2 casas.</li>
     *   <li>19: <b>el compostero de la granja ya no flota</b>. Se medía el suelo ANTES de quitar el compostero viejo,
     *       así que `groundY` lo contaba como suelo y el nuevo subía un bloque en cada migración; y el relleno se
     *       quedaba una capa corto, dejando el compostero en el aire. Medido en el guardado: compostero en la 64 con
     *       el suelo del pueblo en la 62. Ahora se quita el viejo, se mide el suelo limpio, se rellena la columna
     *       hasta la capa de debajo (césped arriba) y se apoya el compostero ahí.</li>
     *   <li>20: <b>el nivelado ya no trata los TRONCOS como terreno</b>. `nivelarHuella` (huella + terraza de cada
     *       construcción y de cada parcela de la granja), `nivelar` (área de la aldea) y el talud usaban
     *       `esTerrenoNatural`, que da los troncos por "terreno natural": al recortar, <b>borraban los postes de
     *       tronco de las paredes</b> y al rellenar los <b>tapaban con tierra</b>. Como el margen de una parcela de
     *       la granja se solapa con la casa de al lado (parcela en (-16,8) con margen 2 y caja de la casa hasta
     *       z=+7), la migración le comía a la casa una <b>fila entera de postes</b>: es el "le falta parte de la
     *       pared entre ventanas" del jugador. Auditoría bloque a bloque contra las plantillas del juego (12
     *       plantillas reales, 7 aldeas, 5 construcciones cada una): los 11 bloques que faltaban eran exactamente
     *       esa fila. Ahora las tres rutas usan `esTerrenoRecortable` (terreno de verdad, sin troncos ni hojas).</li>
     *   <li>21: se <b>quitan los caminos que quedaron encima de los tejados</b> (bug de la cota del kiosco) en todas las
     *       aldeas: la limpieza se hace en esta misma migración, porque `actualizarCasas` ya no corre en aldeas que
     *       tienen las casas al día y la limpieza no llegaba a ejecutarse.</li>
     *   <li>22: el <b>muro vuelve a su altura</b>. Se medía su cota con `groundY` en el anillo (donde está el muro
     *       viejo, cuyos troncos son sólidos), así que cada reconstrucción lo subía un bloque (el jugador lo vio de 6
     *       de alto). Ahora la cota es la de la aldea y la limpieza previa quita los restos del muro de verdad
     *       (troncos, piedra, escaleras, muretes), así que el trozo de más desaparece. Esta versión existe porque el
     *       arreglo era solo de código y las aldeas en 21 no volvían a migrar.</li>
     *   <li>23: el <b>kiosco es más grande</b> (plataforma 7x7 y postes de 4, antes 5x5 y 3), con el cofre del centro
     *       conservado al agrandarlo. Las casas se rehacen (casas 14) con <b>más camas</b> (hasta 4 por casa, para que
     *       duerman los niños) y una <b>segunda puerta</b> en la pared de enfrente.</li>
     *   <li>24: la <b>aldea es más grande</b>: radio de la valla de 29 a 36 (+24% de recinto). Los solares se
     *       reparten a 20-25 bloques del centro (antes 16-18, todo apelotonado), la granja se separa, la iglesia va
     *       a su cuadrante y los sitios de los aldeanos se reparten en un anillo. En la migración se <b>derriba el
     *       trazado antiguo</b> (casas, iglesia, parcelas y caminos viejos) y se borra el <b>anillo del muro viejo
     *       (radio 29)</b>, que si no quedaría una muralla cruzando el pueblo por dentro. Las casas se rehacen
     *       (casas 15) en los solares nuevos.</li>
     *   <li>25: <b>nada del mundo dentro de la aldea</b>. El <b>subsuelo natural entra como terreno</b> (piedra,
     *       deepslate, tierra, arena y <b>los minerales</b>): antes no, así que al recortar un monte con una veta
     *       dentro la piedra de alrededor se iba y los <b>minerales quedaban flotando en el aire</b> (visto en
     *       juego), y encima entraban en el plano de la aldea (el obrero los "reparaba"). Además, el nivelado ahora
     *       <b>tapa los huecos del suelo</b>: si debajo pasa una barranca, una cueva o una mina, el recorte abría su
     *       techo y en la plaza quedaban agujeros por los que se caían los aldeanos. En las aldeas nuevas, el
     *       volumen entero se despeja antes de construir (fuera minas, mazmorras y ruinas). El plano se vuelve a
     *       capturar sin los minerales.</li>
     *   <li>26: la <b>herrería</b>. La aldea tiene el taller de herrero del propio juego
     *       ({@code plains_weaponsmith_1}: fragua, muelle de afilar y arca) y dentro la <b>mesa de herrería</b> del
     *       herrero de herramientas: son los <b>puestos de trabajo</b> de los dos herreros, que hasta ahora no
     *       existían en la aldea (el jugador lo notó: "hay un herrero pero no veo su estación de trabajo"). Entra en
     *       el plano, con su camino.</li>
     *   <li>27: la <b>granja pasa a 4 carriles por lado</b> (parcela de 9x5 a 9x9, 72 cultivos por parcela) y se
     *       recolocan las dos parcelas a (-20,10) y (10,6): las aldeas ya construidas tienen que rehacer sus
     *       bancales (y las parcelas nuevas tapan a las viejas, así que no quedan bancales sueltos).</li>
     *   <li>29: <b>vuelve el herrero de HERRAMIENTAS</b> (y con él su mesa en el taller). Se retiró en el 28 al dejar
     *       un solo herrero por aldea, pero el jugador lo corrigió: hacen falta <b>los dos</b> herreros, que van a
     *       fabricar la indumentaria de la guardia (espada, escudo, armadura, arco y flechas) repartiéndose el
     *       trabajo. Esta versión repone la <b>mesa de herrería</b> que el 28 quitó —y con ella el oficio— y devuelve
     *       el tope de aldeanos a 5.</li>
     *   <li>30: la <b>BARRACA de la milicia</b> al oeste del pueblo (huella 9x9, puerta al norte con su camino y
     *       {@code BARRACA_CAMAS} = 8 camas): el edificio que pidió el jugador para la guardia ("una barraca con
     *       MUCHAS CAMAS"). Las aldeas ya construidas la reciben aquí, porque el sitio que ocupa estaba vacío y no
     *       hay que borrar nada de lo suyo (solo el volumen donde se levanta, que en el trazado actual es patio).</li>
     *   <li>31: la <b>GRANJA ANEXA DE ANIMALES</b> (etapa D) al este y <b>fuera de la valla</b>: corral de 15x15 con
     *       cobertizo (cama y telar del pastor), bebedero, heno y una puerta de madera al camino que baja de la
     *       puerta este del muro. Con ella llega el <b>sexto puesto</b> del pueblo, el <b>ganadero</b> (pastor), que
     *       cría el rebaño y baja la carne y la lana al almacén. Se construye donde antes solo estaba el talud, así
     *       que no borra nada del jugador.</li>
     *   <li>32: la <b>COCINA DEL PUEBLO</b> (etapa E): un <b>ahumador</b> (puesto de trabajo del <b>cocinero</b>) y su
     *       mesa sobre la plataforma del kiosco, al lado de la despensa. Con ella llega el <b>séptimo puesto</b>, el
     *       cocinero, que convierte la carne cruda y las patatas en su versión cocinada: en el contador de comida de
     *       la aldea eso es el <b>doble</b> (crudo 2 puntos, cocinado 4).</li>
     *   <li>33: el <b>GALLINERO</b> del corral anexo (etapa E, lo pidió el jugador: "una granja de pollos... que estén
     *       encerrados y que los huevos se recojan") y los <b>PORTONES DE VALLA</b>: el corral y el gallinero llevan
     *       puerta de valla (que encaja con la valla) en vez de la puerta de madera, y las abre y cierra el pueblo con
     *       su goal, porque el juego no deja que un aldeano abra una puerta de valla.</li>
     *   <li>34: el corral anexo <b>se repara solo</b> (lo pidió el jugador al ver la granja rota): el <b>suelo</b> del
     *       corral se tapa (en su partida la franja oeste se quedó sin suelo, con el agua del mar por debajo, y sin
     *       apoyo la puerta de madera se cayó sola) y la <b>cerca y el portón</b> se reponen en cada latido
     *       ({@code asegurarCercaDelAnexo}), así que un hueco no deja escapar a los animales.</li>
     *   <li>35: la <b>ARBOLEDA DEL PUEBLO</b> (etapa E, lo pidió el jugador al pensar en la aldea que nace en medio del
     *       mar): un rectángulo de césped en la diagonal noreste con <b>seis plantones</b> del árbol del bioma. Es la
     *       madera de una aldea <b>sin bosque</b>: los fundadores traen los plantones (como traen las semillas) y el
     *       leñador los tala y los replanta, con la excepción de "dentro de la valla no se tala" acotada a esa caja.</li>
     *   <li>36: la <b>ORILLA SECA</b> de la aldea de mar (lo pidió el jugador al ver el borde "a cuadros"): cuando el
     *       pueblo nace <b>al nivel del agua</b>, su terreno llano queda a la misma altura que el mar y el primer
     *       escalón del talud asoma en unas casillas y en otras no. Se rellena el anillo de orilla con césped (solo
     *       donde hay agua o aire) y la isla queda con su <b>playa pareja</b> y el agua en un borde limpio.</li>
     *   <li>37: el <b>GANADO VUELVE A CASA</b> (lo pidió el jugador: "que los aparee para que siempre haya una pareja"
     *       y "revisa por qué no está generando carne"). El corral solo tenía el portón, que el pueblo abre para pasar,
     *       así que con las horas el rebaño se colaba y se perdía: en su partida quedaba <b>una vaca</b> dentro y el
     *       resto repartido a 80-130 bloques. Un corral vacío no da carne. Ahora el rebaño va <b>marcado</b>, el que se
     *       pierde <b>vuelve</b> al corral, al que le falta <b>pareja</b> se la trae el pueblo, y el ganadero solo cría
     *       (nunca sacrifica) por debajo de dos adultos.</li>
     *   <li>38: los <b>PICOS DE LAS ESQUINAS</b> del talud (lo pidió el jugador al verlos en su isla: "esos triángulos
     *       de tierra de cada esquina... ahí debe haber agua"). El suelo llano del pueblo es un <b>cuadrado</b> y el
     *       talud un <b>círculo</b>, así que a cada esquina le sobraba un triángulo allanado colgando sobre el mar,
     *       con las caras del corte a la vista. Ahora el pico <b>se quita y su sitio lo ocupa el agua</b> (se baja al
     *       fondo natural y se rellena hasta la superficie del mar), así que la isla queda redonda; en una aldea de
     *       tierra adentro se rebaja a la base del talud.</li>
     *   <li>39: la aldea <b>se despeja por dentro</b> y cada oficio <b>recoge lo suyo del suelo</b> (lo pidió el
     *       jugador: "nadie recoge los materiales del suelo y el recolector no se da abasto... que cada oficio recoja
     *       los materiales propios de su oficio" y "el leñador no está cortando los árboles que están dentro de la
     *       aldea"). Los <b>árboles que quedaron dentro del recinto</b> se quitan de una vez al migrar (137 medidos en
     *       su guardado; el generador sí despejaba el volumen, pero las aldeas migradas se encontraron el bosque
     *       dentro y encima quedó grabado en el plano, así que el leñador los daba por construidos). Y el pueblo
     *       aprende a <b>barrer</b>: el <b>recolector</b> llega más lejos (hasta 64 del centro, donde caía el botín de
     *       las refriegas) y cada oficio tiene su goal de <b>recogida por oficio</b> (el herrero el hierro y el equipo
     *       de los enemigos, el granjero el grano, el ganadero la carne, el cocinero lo que cocina, el clérigo lo
     *       suyo), que guarda la comida en la despensa y los materiales en el almacén.</li>
     *   <li>40: <b>LA MURALLA SE VA AL RADIO 62</b> (lo pidió el jugador: "necesitamos que la villa sea más grande
     *       para que quepa la granja dentro"): con el muro a 36 el <b>corral anexo</b> (que ocupa de 43 a 57 del
     *       centro) quedaba <b>fuera</b>, y eso costaba medido: los monstruos aparecían dentro del corral de noche y
     *       se comían al rebaño, y la guardia no llegaba a defenderlo. Con el muro a 62 <b>la granja entera cabe
     *       dentro</b> (a 4 bloques de la valla) y el trazado se ha <b>repartido</b> por el recinto: las cuatro casas
     *       de 21-25 pasan a 33-38 del centro, la iglesia, el taller y la barraca se van a sus cuadrantes, las
     *       parcelas de la granja se separan (una al oeste y otra pegada a la plaza), la arboleda se lleva a la
     *       diagonal noreste y los sitios de los aldeanos al doble de distancia. Los muros viejos (29 y 36) y las
     *       construcciones del trazado de 36 se <b>derriban</b> al migrar: si no, el pueblo se queda con dos
     *       murallas y con los edificios viejos al lado de los nuevos.</li>
     *   <li>41: <b>LA TABERNA, EL BOSQUE, EL TERCER BANCAL Y LA BARRACA DE DOS PISOS</b> (etapa F, lo pidió el
     *       jugador: <i>"una 3ª parcela con su granjero porque hay poca comida; todas las parcelas rodeadas de vallas
     *       con varias fence gates y mucha iluminación; moved los árboles a un área más grande, un pequeño bosque
     *       donde el leñador tale y replante; una taberna donde trabaje el cocinero y todos vayan a comer, con dos
     *       pisos y el segundo con camas; el almacén a lado de la taberna; las barracas más bonitas, con área de
     *       entrenamiento y un segundo piso con las camas"</i>).
     *       <ul>
     *         <li><b>Tercer bancal</b> (-28,34) y <b>segundo granjero</b> (el oficio se repite: los puestos se miran
     *             por número, no por "está o no está").</li>
     *         <li>Los <b>tres bancales</b> van <b>cercados</b> con <b>cuatro puertas de valla</b> cada uno (las abre
     *             el pueblo) y <b>faroles en los postes</b>, que es lo que deja crecer los cultivos también de
     *             noche.</li>
     *         <li>La <b>arboleda</b> pasa a ser un <b>bosque de 22×18</b> en la esquina noroeste, con <b>doce</b>
     *             plazas de árbol: el leñador tala y replanta ahí.</li>
     *         <li>La <b>TABERNA</b> (24,16), pegada al almacén: abajo el comedor con barra, pipas, mesas y sillas y la
     *             <b>cocina del cocinero</b> (el ahumador del kiosco se retira); arriba la <b>posada</b> con seis
     *             camas. El pueblo va allí a <b>comer</b> cuando tiene hambre (y sale con regeneración).</li>
     *         <li>La <b>barraca</b> pasa a <b>dos pisos</b>: abajo la sala de armas (maniquíes, dianas, hogar y mesa
     *             de mapas) y arriba las {@code BARRACA_CAMAS} camas.</li>
     *       </ul></li>
     *   <li>42: el <b>triangulito de tierra de las esquinas</b> de la aldea de mar (el jugador lo volvió a ver desde
     *       arriba): el primer arreglo de los picos <b>se saltaba</b> las celdas cuyo fondo natural estaba a uno o dos
     *       bloques del agua —y eran justo las que quedaban como un triangulito pegado a la isla—. Ahora, en una aldea
     *       de mar, <b>toda</b> celda de esquina con relleno del pueblo se hunde hasta el fondo natural y se llena de
     *       agua hasta la superficie del mar (lo que sí se respeta es una <b>playa natural</b>: si la capa de arriba es
     *       arena o grava, no es relleno del pueblo y no se toca). Verificado sobre su guardado: de las 180 celdas de
     *       pico, <b>174 pasan a agua</b> y las 6 restantes son arena natural.</li>
     *   <li>43: la <b>nieve y la vegetación que quedaban colgando</b> en una aldea de <b>montaña</b> (lo pidió el
     *       jugador: <i>"quita también la nieve que se quedó flotando cuando la aldea se genera en una montaña"</i>).
     *       El recorte del terreno quita el suelo que sobresale de la cota, pero la <b>nieve polvo</b>
     *       ({@code powder_snow}) no bloquea el movimiento y el mapa de alturas no la ve, así que el recorte paraba
     *       debajo y la nieve se quedaba <b>en el aire</b>: medido en su aldea de montaña (cota 95), <b>469 bloques
     *       de nieve polvo</b> flotando dentro del recinto, que es lo que se veía desde arriba. Ahora se retiran (solo
     *       los que <b>no tienen nada debajo</b>) la nieve, el hielo y las plantas colgadas. La nieve apoyada en el
     *       suelo del pueblo se queda.</li>
     *   <li>44: la <b>TABERNA GRANDE</b> (lo pidió el jugador: <i>"arregla la taberna, está muy pequeña y muy
     *       sencilla; las escaleras están mal orientadas y no se puede subir"</i>). Se rehace entera con el plano que
     *       trajo (el <i>Building map: Inn</i>) y el arte conceptual de la posada con entramado:
     *       <ul>
     *         <li>Pasa de <b>13×12 a 19×15</b> (y a 21×17 en la planta alta, que <b>vuela</b> un bloque sobre la baja,
     *             el <i>jetty</i> del arte) y de dos pisos bajos a dos pisos de cuatro bloques de alto.</li>
     *         <li>Abajo, el <b>comedor</b>: la <b>cocina</b> del cocinero (con su ahumador), el <b>hogar</b> de
     *             ladrillo con su chimenea, la <b>barra</b> con las pipas, <b>seis mesas</b> con sus sillas y la
     *             escalera. Arriba, la <b>posada</b>: <b>seis cuartos</b> con once camas alrededor de una galería.</li>
     *         <li>La <b>ESCALERA</b> iba al revés (subía al norte mirando al sur), así que se veía bien y no se podía
     *             subir: en las escaleras del juego la cara alta —por donde se sube— es la que marca {@code FACING}.
     *             Ahora sube dentro de su <b>caja</b> cerrada, pegada al muro oeste, y desemboca en la galería.</li>
     *         <li>La <b>fachada da al oeste</b> (a la plaza), con <b>porche</b>, toldo, enseña y <b>camino</b> desde la
     *             plaza (el camino va torcido a propósito: en recta cruzaba la parcela de la granja).</li>
     *         <li>El <b>solar se despeja entero</b> antes de levantarla (la taberna vieja cabía dentro) y lo que
     *             hubiera en sus cofres se guarda antes en el <b>almacén</b>, para no perder nada.</li>
     *       </ul></li>
     *   <li>45: cuatro arreglos que pidió el jugador de una vez:
     *       <ul>
     *         <li><b>La ESCALERA de la taberna, accesible y doble.</b> La migración 44 la dejó <b>sin acceso</b>: el
     *             primer escalón estaba metido en la esquina suroeste del comedor, con el escalón de arriba delante,
     *             la pared al este y la pared al sur (no se podía ni llegar a él). Ahora es de <b>dos bloques de
     *             ancho</b>, arranca a dos bloques de la pared sur (se entra <b>de lado</b>, desde el comedor) y
     *             desemboca en la galería de la posada. El cuarto pequeño del suroeste se recorta para dejarle sitio a
     *             la caja de la escalera.</li>
     *         <li><b>La CHIMENEA, por fuera.</b> El hogar daba su cara norte a la calle y <b>se veía la llama desde
     *             fuera</b>: el caño de ladrillo pasa a ir <b>por delante</b> del muro (como en el arte conceptual) y
     *             tapa la boca del hogar.</li>
     *         <li><b>El ALMACÉN, al lado de la taberna</b> (48, 21) en vez de delante de su puerta principal (18, 18),
     *             y más grande: cobertizo de <b>7x7</b> con <b>seis</b> cofres dobles (doce cofres) para que siga
     *             creciendo. Lo que hubiera en los cofres viejos se <b>pasa al nuevo antes</b> de retirar el viejo
     *             (tirar un cofre tira su contenido al suelo).</li>
     *         <li><b>El CORRAL, más grande</b> (19x19 en vez de 15x15) y el <b>rebaño que se había perdido</b>: la
     *             caja de búsqueda del ganado del pueblo llegaba a 48 bloques del corral y había animales con la marca
     *             a 51-57 que no volvían nunca. Solo se retiran los bloques del corral viejo (lo del jugador no se
     *             toca).</li>
     *         <li>Y la <b>HUERTA que se reiniciaba al migrar</b>: el <b>nivelado de la huella</b> de una parcela
     *             <b>recortaba</b> el terreno que sobresalía y, en una parcela en cuesta (una aldea de montaña), ese
     *             recorte se llevaba por delante los <b>cultivos crecidos</b> de las celdas altas: salían como
     *             <i>"vegetales como item por toda la parcela"</i> (lo que vio el jugador) y se replantaban brotes.
     *             Ahora una parcela ya hecha <b>no se toca</b>: solo se aseguran su compostero y su valla.</li>
     *       </ul></li>
     *   <li>46: la <b>PESQUERA</b> (etapa G, lo pidió el jugador: <i>"el pescador tendrá su edificio y su lago más
     *       adelante"</i>): un <b>lago</b> de 7x7 en el campo del sureste, con su pasarela, y la <b>caseta del
     *       pescador</b> (5x5, con su puerta mirando al agua, su cama, su arca y su farol). El <b>barril</b> —que en
     *       vanilla es el puesto del pescador y que el pueblo no usaba a propósito desde la etapa F— pasa a estar en
     *       la pesquera, con su dueño: el <b>PESCADOR</b> es un puesto fijo más (9 en total), pesca peces <b>de
     *       verdad</b> del lago (la entidad se va) y baja el pescado crudo a la despensa, donde el cocinero lo ahúma
     *       (crudo = 2 puntos de comida, cocinado = 4, como la carne del corral: la segunda fuente de proteína). El
     *       lago <b>se repuebla solo y despacio</b> (un pez cada dos minutos, hasta 6), así que la pesca está limitada
     *       por lo que cría el lago y no por un contador.</li>
     * </ul>
     */
    public static final int CURRENT_LAYOUT = 49;

    /**
     * Versión de las <b>casas</b> que debe tener una aldea: 0 = cabañas procedurales (partidas viejas),
     * 1 = las tres casas del juego, 2 = las cuatro (la última es la "grande", con cama extra), 3 = además la
     * <b>iglesia</b> en el sitio de la vieja torre, 4 = casas con el <b>suelo a ras</b> del patio (antes iban un
     * bloque altas), 5 = con el nivel de referencia corregido (mínimo del patio), 6 = con la <b>mediana del
     * patio inmediato</b> y escalón para el desnivel (el mínimo las dejaba hundidas), 7 = con <b>terraza</b>
     * allanada alrededor, 8 = con la <b>aldea entera a una sola cota</b> (sin zanjas), 9 = a la <b>cota de la
     * plaza</b> (con la mediana contaminada por los tejados quedaban un bloque altas, con su zanja de un bloque
     * alrededor), 10 = <b>alineadas por su puerta</b> (la casa mediana entierra así su plataforma de tierra, que se
     * veía como un borde/zanja marrón) y con la <b>iglesia del campanario</b> (`plains_temple_4`), 11 = con el
     * <b>borde del solar a nivel</b> (se devuelve el césped a la capa de superficie alrededor de las paredes: la
     * caja de la plantilla es más grande que el edificio y quedaba una zanja de un bloque alrededor de cada casa),
     * 12 = con el <b>solar relleno hasta el suelo del pueblo</b> (la casa mediana dejaba huecos de DOS bloques: el
     * despeje no baja ya de la capa de superficie y se rellena la columna donde la plantilla no pone nada),
     * 13 = con el <b>nivelado que respeta los troncos</b> (el margen de la parcela de la granja le borraba a la casa
     * una fila entera de postes de la pared), 14 = con <b>más camas</b> (hasta 4 por casa, para que duerman los niños)
     * y una <b>segunda puerta</b> en la pared de enfrente, 15 = en los <b>solares nuevos</b> de la aldea agrandada
     * (radio 36; antes a 16-18 del centro, ahora a 20-25 y repartidas por cuadrantes), 16 = en los <b>solares del
     * trazado de radio 62</b> (a 33-38 del centro, repartidos para que la aldea llene la muralla nueva y para que la
     * granja de animales quepa dentro).
     * Se sube cuando cambia el número, el tipo o la <b>altura</b> de las construcciones, y la migración solo hace lo
     * que falte (rehacer una casa borra lo que tenga dentro).
     */
    public static final int CURRENT_HOUSES = 16;
    /** Radio alrededor del obrero en el que se buscan huecos que reponer (derivado del radio de la aldea). */
    private static final double REPAIR_SEARCH_RADIUS = VillageGenerator.FENCE_RADIUS + 4.0D;
    /** Cuánto puede estar el hueco por encima / por debajo del obrero para que intente alcanzarlo. */
    private static final int REPAIR_MAX_UP = 5;
    private static final int REPAIR_MAX_DOWN = 6;
    /** Cuántos obreros puede tener una aldea a la vez (se reparten los huecos). */
    private static final int MAX_BUILDERS = 3;
    /** Si un obrero se queda atascado con un hueco, la reserva caduca y otro puede cogerlo. */
    private static final long CLAIM_TIMEOUT_TICKS = 60L * 20L;
    /**
     * Reservas de huecos: posición comprimida → (obrero que lo tiene, tick en que lo cogió). Es lo que evita que
     * varios obreros vayan al MISMO agujero cuando hay más de uno trabajando.
     */
    private static final Map<ServerLevel, Map<Long, Reclamo>> CLAIMS = new HashMap<>();

    private record Reclamo(UUID builder, long tick) {
    }
    /** Etiqueta de los datos persistentes del aldeano con su fecha de nacimiento. */
    private static final String BORN_TAG = "DevilRpgVillagerBorn";
    /** A partir de esta edad (2 días de juego) el aldeano es viejo y va más lento. */
    private static final long VILLAGER_OLD_AGE_TICKS = 2L * 24000L;
    /** Al llegar aquí (3 días de juego) el aldeano muere de viejo y deja el relevo a los jóvenes. */
    private static final long VILLAGER_LIFESPAN_TICKS = 3L * 24000L;

    private static final Map<ServerLevel, List<VillageDefense>> DEFENSES = new HashMap<>();

    private VillageManager() {
    }

    /**
     * Avisa al jugador de que hay una aldea cerca (una única vez por jugador y objetivo): mensaje en
     * pantalla y sonido de campana lejana. Se dispara al entrar en {@link #NOTICE_RADIUS} bloques.
     * El aviso se guarda, para no repetirlo en cada partida.
     */
    public static void noticeIfNear(ServerLevel level, ServerPlayer player, int objectiveIndex, BlockPos target) {
        VillageSavedData saved = VillageSavedData.get(level);
        if (saved.isNoticed(objectiveIndex, player.getUUID())) {
            return;
        }
        saved.markNoticed(objectiveIndex, player.getUUID());
        player.displayClientMessage(Component.literal(
                "Divisas una aldea a lo lejos... la campana llama, y algo se agita en la oscuridad."), false);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BELL_BLOCK, SoundSource.AMBIENT, 1.0F, 1.0F);
    }

    /**
     * Pre-genera la aldea (cabañas + aldeanos + valla) en el punto del objetivo, si aún no existe.
     * <p>
     * La marca de "ya generada" es <b>persistente</b>: antes vivía en memoria y, al reiniciar la partida, la
     * aldea se volvía a generar <b>encima</b> de la que ya había (nivelaba el terreno, despejaba vegetación y
     * reconstruía cabañas y valla, cargándose lo que hubieras construido cerca).
     */
    public static void preGenerate(ServerLevel level, int objectiveIndex, BlockPos target) {
        VillageSavedData saved = VillageSavedData.get(level);
        if (saved.isGenerated(objectiveIndex)) {
            return;
        }
        // `generate` devuelve el PLANO CANÓNICO de la aldea (lo que el generador colocó mientras construía), así
        // que se guarda aquí mismo, con la aldea recién hecha: el obrero sabrá qué reponer sin depender de en qué
        // estado se encontrara la aldea después.
        VillageSavedData.Blueprint plano = VillageGenerator.generate(level, target);
        saved.markGenerated(objectiveIndex);
        if (plano != null) {
            saved.setBlueprint(objectiveIndex, plano);
            saved.setLayout(objectiveIndex, CURRENT_LAYOUT);
            // Esta aldea nace ya con las casas del juego (las cuatro): que la migración no las vuelva a construir.
            saved.setCasasVersion(objectiveIndex, CURRENT_HOUSES);
            DevilRpg.LOGGER.info("[Village] Aldea {} pre-generada en {} (plano de {} bloques)",
                    objectiveIndex, target, plano.size());
        } else {
            DevilRpg.LOGGER.info("[Village] Aldea {} pre-generada en {}", objectiveIndex, target);
        }
    }

    /**
     * Gestiona también las aldeas de objetivos <b>ya superados</b> que el jugador tenga cerca, no solo la del
     * objetivo actual.
     * <p>
     * Antes, en cuanto avanzabas de objetivo la aldea anterior dejaba de gestionarse: si volvías a ella (o
     * pasabas cerca), no se pre-generaba, no avisaba y no se podía asediar — se quedaba congelada, y con el
     * tiempo eso dejaba aldeas "muertas" por el mundo. Ahora, cualquier aldea a menos de
     * {@link #PRE_GENERATE_RADIUS} bloques del jugador se pre-genera, avisa y puede asediarse; los tres pasos
     * son idempotentes (pre-generado, avisado y resuelto se guardan en {@link VillageSavedData}), así que
     * llamarlo cada tick no repite nada.
     */
    public static void manageNearby(ServerLevel level, ServerPlayer player, Vec3 anchor, int currentIndex) {
        VillageSavedData saved = VillageSavedData.get(level);
        BlockPos playerPos = player.blockPosition();
        for (int i = 0; i <= currentIndex; i++) {
            BlockPos target = ObjectiveTargets.targetOf(anchor, i);
            // LA Y DEL CENTRO ES LA DE LA ALDEA, no la del spawn del jugador: `targetOf` da la altura del ancla (en la
            // partida, 101 con las aldeas a 62-75) y esa Y se arrastraba a TODA la aldea (los goals buscaban los
            // cultivos a esa altura y no veían ninguno, el granjero daba vueltas sin cosechar, el recolector no
            // recogía y los zombies del asedio intentaban caminar 30 bloques por encima del pueblo).
            if (saved.isGenerated(i)) {
                BlockPos centro = centroDe(level, i);
                if (centro != null) {
                    // lint:ok I1 -- aquí SÍ se quiere la Y del plano, que ya es la cota (ver centroDe)
                    target = new BlockPos(target.getX(), centro.getY(), target.getZ());
                }
            }
            double distSqr = ObjectiveTargets.horizontalDistSqr(playerPos, target);
            if (distSqr > (double) PRE_GENERATE_RADIUS * PRE_GENERATE_RADIUS) {
                continue;
            }
            if (!saved.isGenerated(i)) {
                preGenerate(level, i, target);
            }
            noticeIfNear(level, player, i, target);
            if (distSqr <= (double) ARRIVE_RADIUS * ARRIVE_RADIUS) {
                start(level, player, i, target);
            }
            // SALUD DE LA ALDEA (Iteración 3): se cuenta a los aldeanos vivos y la aldea se recupera DE A POCO.
            // Antes solo se repoblaba la aldea VACÍA, y de golpe (3 aldeanos + golem): ahora una aldea
            // debilitada repone un aldeano cada REPOPULATE_INTERVAL_TICKS, y una vacía se rehace entera de una
            // vez para que no quede muerta si el jugador llega justo después de una masacre. Va AQUÍ y no en
            // start() porque start() sale antes si el asedio de ese objetivo ya se resolvió (aldea "salvada"),
            // y entonces la aldea se quedaba vacía para siempre: es el caso que reportó el jugador.
            // No se toca si la aldea ya cayó (isFallen: la derrota es definitiva) ni si hay asedio en curso
            // (si no, repoblaríamos mientras los monstruos la están matando).
            if (level.getGameTime() % VILLAGE_POLL_TICKS == 0L && !saved.isFallen(i) && !isUnderAttack(level, i)
                    && !hayEnemigosDentro(level, target)) {
                int vivos = observeVillagers(level, saved, i, target);
                if (vivos > 0) {
                    tickVillageLife(level, saved, i, target);
                }
                if (vivos == 0) {
                    VillageGenerator.spawnVillagers(level, target);
                    saved.markRepopulated(i, level.getGameTime());
                    DevilRpg.LOGGER.info("[Village] Aldea {} estaba vacia: aldeanos y golem repuestos", i);
                } else {
                    // Qué oficios quedan vivos y cuál falta (si mataron al recolector, vuelve un recolector; si al
                    // granjero, un granjero): no se repone "el sitio siguiente".
                    List<VillagerProfession> vivas = level
                            .getEntitiesOfClass(Villager.class, new AABB(target).inflate(FALLEN_CHECK_RADIUS)).stream()
                            .filter(v -> !v.isBaby())
                            .map(v -> v.getVillagerData().getProfession())
                            .toList();
                    int slot = VillageGenerator.slotDeProfesionFaltante(vivas);
                    if (slot >= 0) {
                        // PUESTO VACÍO: se repone YA y ADULTO, sin esperar el turno de crecimiento (5 min) ni a que
                        // crezca una cría (20 min). Una aldea sin recolector acumula basura por el suelo y sin
                        // granjero pasa hambre, así que el relevo de un puesto que se ha quedado vacío no puede
                        // tardar una eternidad (era la queja del jugador: "se murió el recolector").
                        // OJO: esto NO se limita por el tope de población. El tope es para CRECER (crías), no para
                        // cubrir un puesto FIJO: si la aldea ya tiene tanta gente como puestos, el que llega es uno
                        // de más y los sobrantes son la milicia. Medido en el guardado del jugador: aldea con 7
                        // aldeanos y tope 7, migrada a la etapa E, SIN carnicero y con el ahumador sin dueño — el
                        // puesto nuevo no llegaba NUNCA porque el tope ya estaba lleno.
                        // TAMPOCO se limita por la COMIDA (lo pidió el jugador: "el que cuida los animales no produce
                        // carne aún"). Un puesto fijo es el que PRODUCE: medido en su guardado, la aldea tenía 8
                        // aldeanos, <b>0 de comida</b> y <b>sin ganadero</b> (el corral construido y vacío de dueño)
                        // porque reponer un puesto exigía comida de sobra — y sin ganadero no hay carne, así que la
                        // aldea no podía salir del hambre nunca. Con la aldea muerta de hambre el puesto no cuesta
                        // nada; con comida de sobra, se le cobra como a cualquier boca nueva.
                        VillageGenerator.spawnOneVillager(level, target, slot, false);
                        if (saved.getFood(i) >= FOOD_TO_GROW) {
                            saved.setFood(i, saved.getFood(i) - FOOD_TO_GROW);
                        }
                        saved.markRepopulated(i, level.getGameTime());
                        DevilRpg.LOGGER.info("[Village] Aldea {}: repuesto el puesto de {} que se habia quedado vacio "
                                + "(comida {})", i, VillageGenerator.profesionDeSlot(slot), saved.getFood(i));
                    } else if (saved.getFood(i) >= FOOD_TO_GROW
                            && vivos < VillageGenerator.puestosDelPueblo()
                            && level.getGameTime() - saved.getRepopulatedAt(i) >= REPOPULATE_INTERVAL_TICKS) {
                        // Crecer cuesta comida: una aldea hambrienta no se recupera hasta que la granja produzca.
                        // El que llega nace CRÍA (crece sola, mecánica vanilla): así se ve el relevo generacional.
                        VillageGenerator.spawnOneVillager(level, target, vivos, true);
                        saved.setFood(i, saved.getFood(i) - FOOD_TO_GROW);
                        saved.markRepopulated(i, level.getGameTime());
                        DevilRpg.LOGGER.info("[Village] Aldea {} se recupera: aldeano {}/{} (comida {})",
                                i, vivos + 1, VillageGenerator.puestosDelPueblo(), saved.getFood(i));
                    }
                }
            }
        }
    }

    /**
     * ¿Ese bicho está <b>dentro del recinto</b> de la aldea? La aldea es un recinto en <b>XZ</b> (invariante I2:
     * un aldeano unos bloques por encima del suelo sigue estando en su pueblo), pero además hay que estar <b>a la
     * altura del pueblo</b> ({@link #RECINTO_DY_ABAJO}/{@link #RECINTO_DY_ARRIBA} sobre la cota): un bicho en una
     * cueva bajo la plaza no ha pasado los muros.
     * <p>
     * Es la <b>única</b> verdad de "dentro de la aldea" para bichos (la usan el latido del pueblo, el perímetro del
     * asedio y las partículas de intrusión). Mide la cota, así que <b>no</b> se llama dentro de un bucle: para
     * bucles está la variante con la cota ya medida.
     */
    public static boolean dentroDelRecinto(ServerLevel level, BlockPos center, net.minecraft.world.entity.Entity bicho,
                                          double radio) {
        return dentroDelRecinto(VillageGenerator.cotaDeLaPlaza(level, center), center, bicho, radio);
    }

    /** Igual, con la cota ya medida (para bucles: la cota se pide UNA vez, no por bicho). */
    public static boolean dentroDelRecinto(int cota, BlockPos center, net.minecraft.world.entity.Entity bicho,
                                          double radio) {
        double dx = bicho.getX() - (center.getX() + 0.5D);
        double dz = bicho.getZ() - (center.getZ() + 0.5D);
        if (dx * dx + dz * dz > radio * radio) {
            return false;
        }
        double dy = bicho.getY() - cota;
        return dy >= -RECINTO_DY_ABAJO && dy <= RECINTO_DY_ARRIBA;
    }

    /**
     * ¿Hay <b>monstruos DENTRO de la aldea</b> ahora mismo? No es lo mismo que {@link #isUnderAttack} (que mira los
     * asedios declarados): en la partida del jugador hay zombies agresivos sueltos que entran al pueblo y matan
     * aldeanos <b>sin que haya asedio</b>, y con eso el gestor seguía repoblando.
     * <p>
     * Medido en el log del jugador (aldea 10, 01:50-02:00): la aldea repuso <b>6 puestos seguidos</b> (8 de comida
     * cada uno) entre zombies que mataban al recién llegado, con el <b>granjero ya muerto</b>; la despensa bajó de
     * <b>20 a 0</b> y la aldea pasó hambre <b>por repoblar en plena masacre</b>. Con monstruos dentro no se repuebla:
     * primero hay que limpiar el pueblo (o esperar a que se vayan).
     * <p>
     * El radio es el del muro y la altura la del pueblo ({@link #dentroDelRecinto}): contando solo la horizontal, un
     * esqueleto en una cueva a 60 bloques bajo la plaza <b>congelaba el pueblo entero</b> (ni cultivos, ni comida, ni
     * reparaciones, ni repoblación) sin que hubiera nadie dentro.
     */
    private static boolean hayEnemigosDentro(ServerLevel level, BlockPos center) {
        double radio = VillageGenerator.FENCE_RADIUS;
        List<Monster> monstruos = level.getEntitiesOfClass(Monster.class, new AABB(center).inflate(radio));
        if (monstruos.isEmpty()) {
            return false;   // el caso normal (y el más barato): no se mide ni la cota
        }
        int cota = VillageGenerator.cotaDeLaPlaza(level, center);
        for (Monster monstruo : monstruos) {
            if (dentroDelRecinto(cota, center, monstruo, radio)) {
                return true;
            }
        }
        return false;
    }

    /** Inicia el asedio al llegar el jugador a la aldea (con un margen antes de la ola). */
    public static void start(ServerLevel level, ServerPlayer player, int objectiveIndex, BlockPos target) {
        // No re-lanzar si ya hay un asedio activo para este jugador/objetivo.
        for (VillageDefense d : DEFENSES.getOrDefault(level, List.of())) {
            if (d.playerUUID.equals(player.getUUID()) && d.objectiveIndex == objectiveIndex) {
                return;
            }
        }
        // Ni si el mundo ya la está atacando por su cuenta (horda dirigida a esta aldea): no se apilan dos
        // oleadas sobre la misma aldea.
        if (isUnderWorldSiege(level, objectiveIndex)) {
            return;
        }
        // Ni si el asedio de este objetivo ya se resolvió (salvada o caída): se guarda, así que al reiniciar
        // la partida no se puede repetir la recompensa ni volver a asediar la misma aldea.
        VillageSavedData saved = VillageSavedData.get(level);
        if (saved.isSiegeResolved(objectiveIndex)) {
            return;
        }
        if (!saved.isGenerated(objectiveIndex)) {
            preGenerate(level, objectiveIndex, target);
        }
        DEFENSES.computeIfAbsent(level, l -> new ArrayList<>())
                .add(new VillageDefense(objectiveIndex, player.getUUID(), target));
        player.displayClientMessage(Component.literal("Llegaste a la aldea... los monstruos se acercan."), false);
    }

    /** Se llama en el tick del servidor: gestiona el margen, la ola y la resolución del asedio. */
    public static void tick(ServerLevel level) {
        // Hordas que el MUNDO manda contra una aldea (Iteración 3): se resuelven aparte de los asedios que
        // arranca el jugador al llegar.
        tickWorldSieges(level);

        List<VillageDefense> list = DEFENSES.get(level);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (int i = list.size() - 1; i >= 0; i--) {
            VillageDefense d = list.get(i);
            d.tickTicks++;

            // Tras el margen de exploración, lanza la ola desde FUERA de la valla.
            if (!d.waveSpawned && d.tickTicks >= GRACE_TICKS) {
                spawnWave(level, d);
                d.waveSpawned = true;
                ServerPlayer p = level.getServer().getPlayerList().getPlayer(d.playerUUID);
                if (p != null) {
                    p.displayClientMessage(Component.literal("¡Defiende la aldea de los monstruos! Los asediadores"
                            + " van marcados con un brillo: si aguantan dentro del muro, la aldea cae."), false);
                }
            }

            if (d.waveSpawned) {
                // ESTADO DEL ASEDIO: un asedio que se pierde sin decir cómo iba es una derrota a ciegas (el
                // jugador vio caer su aldea 1 DENTRO de las murallas, peleando, sin saber que quedaba un
                // atacante de la ola dentro: la valla está a 62 del centro, así que el pueblo es casi todo
                // lo que se ve alrededor de la plaza).
                informarDelAsedio(level, d);
                boolean waveCleared = isWaveCleared(level, d.wave);
                boolean timeout = d.tickTicks > GRACE_TICKS + SIEGE_TIMEOUT_TICKS;
                if (waveCleared || timeout) {
                    // Al acabarse el tiempo, si queda algún zombie FUERA del perímetro (sin pasar los muros) se
                    // considera que el asedio FRACASÓ y la aldea se salva. Sin esta regla, unos pocos zombies
                    // escondidos que nunca llegan al centro (y por tanto no se pueden matar) hacían caer la
                    // aldea sin que el jugador pudiera evitarlo: si no llegan, no asedian, no pueden ganar.
                    boolean siegeFailed = timeout && !waveCleared && !allZombiesInsidePerimeter(level, d);
                    ServerPlayer player = level.getServer().getPlayerList().getPlayer(d.playerUUID);
                    if (player != null) {
                        PlayerAuxiliaryCapabilityInterface aux = IGenericCapability.getUnwrappedPlayerCapability(player, PlayerAuxiliaryCapability.INSTANCE);
                        // El objetivo solo avanza si es el objetivo ACTUAL: desde la Iteración 3 también se
                        // pueden asediar aldeas de objetivos ya superados (siguen vivas y gestionadas), y
                        // avanzar el índice con uno viejo haría RETROCEDER al jugador.
                        boolean isCurrentObjective = aux != null && aux.getObjectiveIndex() == d.objectiveIndex;
                        if (waveCleared) {
                            if (d.wave.isEmpty()) {
                                grantReward(player, d.objectiveIndex);
                                marcarSelloMistico(level, d.center, player);
                                player.displayClientMessage(Component.literal(isCurrentObjective
                                        ? "¡Has salvado la aldea! El objetivo avanza."
                                        : "¡Has salvado la aldea!"), false);
                            } else {
                                // La ola se dio por limpia porque los atacantes dejaron de estar cargados (el
                                // jugador se alejó y se descargaron los chunks), no porque los matara: la aldea
                                // se salva igual, pero NO hay recompensa. Misma regla que en las hordas del mundo.
                                DevilRpg.LOGGER.info("[Village] Aldea {} salvada sin limpiar la horda ({} atacantes "
                                                + "sin confirmar): sin recompensa", d.objectiveIndex, d.wave.size());
                                player.displayClientMessage(Component.literal(isCurrentObjective
                                        ? "Los monstruos se dispersaron: la aldea está a salvo. El objetivo avanza."
                                        : "Los monstruos se dispersaron: la aldea está a salvo."), false);
                            }
                        } else if (siegeFailed) {
                            grantReward(player, d.objectiveIndex);
                            marcarSelloMistico(level, d.center, player);
                            player.displayClientMessage(Component.literal(isCurrentObjective
                                    ? "Los monstruos no lograron entrar: ¡la aldea está a salvo! El objetivo avanza."
                                    : "Los monstruos no lograron entrar: ¡la aldea está a salvo!"), false);
                        } else {
                            // La aldea ha caído de verdad (los monstruos entraron y sobrevivieron al tiempo):
                            // se marca caída (definitiva) y queda en ruinas. Antes solo se avisaba por chat y la
                            // aldea seguía "viva", así que el gestor la repoblaba más tarde como si nada.
                            fallVillage(level, VillageSavedData.get(level), d.objectiveIndex, d.center);
                            player.displayClientMessage(Component.literal(isCurrentObjective
                                    ? "La aldea cayó: los monstruos aguantaron dentro de los muros. El objetivo avanza."
                                    : "La aldea cayó: los monstruos aguantaron dentro de los muros."), false);
                        }
                        if (isCurrentObjective) {
                            aux.setObjectiveIndex(d.objectiveIndex + 1, player);
                        }
                    }
                    // Los que queden vivos dejan de asediar (no convergen al centro): los que no llegaron a
                    // entrar se quedan por el mundo como zombies agresivos normales, con el escalado por
                    // distancia que ya traen de su spawn.
                    disableGoToCenter(level, d.wave);
                    // Se guarda que este asedio ya se resolvió: no se relanza al reiniciar la partida.
                    VillageSavedData.get(level).markSiegeResolved(d.objectiveIndex);
                    list.remove(i);
                }
            }
        }
    }

    /**
     * Informa del asedio en curso (barra de acción cada {@link #SIEGE_STATUS_INTERVAL} y cuenta atrás en
     * {@link #SIEGE_WARN_SECONDS}): cuántos atacantes quedan, cuántos están <b>dentro del muro</b> (los únicos que
     * pueden hacer caer la aldea) y cuánto tiempo queda. Además canta cada baja al momento: es el progreso real.
     */
    private static void informarDelAsedio(ServerLevel level, VillageDefense d) {
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(d.playerUUID);
        if (p == null) {
            return;
        }
        int vivos = contarAtacantesVivos(level, d);
        long restante = GRACE_TICKS + SIEGE_TIMEOUT_TICKS - d.tickTicks;
        if (d.ultimosVivos >= 0 && vivos < d.ultimosVivos) {
            p.displayClientMessage(Component.literal(vivos == 0
                    ? "¡El último asediador ha caído!"
                    : "Asediador abatido: quedan " + vivos + "."), true);
        }
        d.ultimosVivos = vivos;
        long segundos = Math.max(0L, restante / 20L);
        boolean toca = d.tickTicks % SIEGE_STATUS_INTERVAL == 0;
        for (int aviso : SIEGE_WARN_SECONDS) {
            if (segundos == aviso) {
                toca = true;
            }
        }
        if (!toca) {
            return;
        }
        int cota = VillageGenerator.cotaDeLaPlaza(level, d.center);
        int dentro = contarAtacantesDentro(level, d, cota);
        String tiempo = String.format("%d:%02d", segundos / 60L, segundos % 60L);
        String estado = dentro > 0
                ? "quedan " + vivos + " y " + dentro + " DENTRO del muro"
                : "quedan " + vivos + " atacantes (ninguno ha pasado el muro)";
        String desenlace = dentro > 0 ? " · si aguantan dentro, la aldea cae" : " · si no entran, no pueden ganar";
        p.displayClientMessage(Component.literal("Asedio a la aldea: " + estado + " · " + tiempo + desenlace), true);
    }

    /** Atacantes de la ola que siguen <b>vivos</b> (los descargados no cuentan: el juego ya los da por idos). */
    private static int contarAtacantesVivos(ServerLevel level, VillageDefense d) {
        int vivos = 0;
        for (UUID uuid : d.wave) {
            net.minecraft.world.entity.Entity e = level.getEntity(uuid);
            if (e != null && e.isAlive()) {
                vivos++;
            }
        }
        return vivos;
    }

    /** Atacantes vivos que están <b>dentro del recinto</b> (los únicos que pueden hacer caer la aldea). */
    private static int contarAtacantesDentro(ServerLevel level, VillageDefense d, int cota) {
        int dentro = 0;
        for (UUID uuid : d.wave) {
            net.minecraft.world.entity.Entity e = level.getEntity(uuid);
            if (e != null && e.isAlive() && dentroDelRecinto(cota, d.center, e, PERIMETER_RADIUS)) {
                dentro++;
            }
        }
        return dentro;
    }

    /** Brillo de asediador: saber a QUIÉN hay que matar sin adivinarlo entre los bichos de la noche. */
    private static void marcarAsediadores(ServerLevel level, List<UUID> wave, boolean brillo) {
        for (UUID uuid : wave) {
            net.minecraft.world.entity.Entity e = level.getEntity(uuid);
            if (e != null) {
                e.setGlowingTag(brillo);
            }
        }
    }

    private static void spawnWave(ServerLevel level, VillageDefense d) {
        Random random = new Random();
        // La ola crece al alejarse del ancla, pero con un LÍMITE: no se extiende infinitamente.
        int count = DEFAULT_WAVE + Math.min(d.objectiveIndex * 2, MAX_WAVE_EXTRA);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            // FUERA de la valla (hoy FENCE_RADIUS = 62, no 36: el radio viejo de este comentario hizo medir mal
            // una defensa entera): spawnea entre WAVE_SPAWN_MIN y WAVE_SPAWN_MAX bloques del centro.
            int dist = WAVE_SPAWN_MIN + random.nextInt(WAVE_SPAWN_MAX - WAVE_SPAWN_MIN);
            int x = (int) Math.round(d.center.getX() + Math.cos(angle) * dist);
            int z = (int) Math.round(d.center.getZ() + Math.sin(angle) * dist);
            int y = VillageGenerator.spawnY(level, x, z);
            AggressiveZombieEntity zombie = ModEntities.AGGRESSIVE_ZOMBIE.get()
                    .create(level, null, new BlockPos(x, y, z), MobSpawnType.MOB_SUMMONED, true, true);
            if (zombie != null) {
                zombie.moveTo(x + 0.5D, y, z + 0.5D, 0.0F, 0.0F);
                zombie.setVillageCenter(d.center); // para que converja hacia la aldea si no ataca
                // Marcado con la aldea: al morir se descuenta de la ola, y así la recompensa solo se paga si
                // de verdad se limpió la horda (no si se dispersó al descargarse los chunks).
                zombie.setWorldSiegeIndex(d.objectiveIndex);
                level.addFreshEntity(zombie);
                d.wave.add(zombie.getUUID());
            }
        }
        // Los asediadores van marcados con brillo (ver `marcarAsediadores`): sin la marca, en una noche con
        // decenas de bichos alrededor el jugador no puede saber a quién tiene que matar.
        marcarAsediadores(level, d.wave, true);
    }

    private static boolean isWaveCleared(ServerLevel level, List<UUID> wave) {
        for (UUID uuid : wave) {
            net.minecraft.world.entity.Entity e = level.getEntity(uuid);
            if (e != null && e.isAlive()) {
                return false;
            }
        }
        return true;
    }

    /**
     * ¿Están <b>todos</b> los zombies vivos de la ola dentro del perímetro de la aldea (pasados los muros)?
     * Se usa al agotarse el tiempo: si alguno se quedó fuera, el asedio fracasó y la aldea se salva.
     * <p>
     * "Dentro" es {@link #dentroDelRecinto}: el disco del pueblo (en XZ, invariante I2) <b>y</b> a la altura del
     * pueblo. Midiendo solo la horizontal, un asediador que se caía a una cueva bajo la plaza contaba como
     * invasor y la aldea <b>caía sin que el jugador pudiera hacer nada</b> (no se le ve, y hay que cavar a
     * ciegas): la regla escrita es justo la contraria ("si no llegan a los muros, no asedian y no pueden ganar").
     */
    private static boolean allZombiesInsidePerimeter(ServerLevel level, VillageDefense d) {
        int cota = VillageGenerator.cotaDeLaPlaza(level, d.center);
        for (UUID uuid : d.wave) {
            net.minecraft.world.entity.Entity e = level.getEntity(uuid);
            if (e == null || !e.isAlive()) {
                continue;
            }
            if (!dentroDelRecinto(cota, d.center, e, PERIMETER_RADIUS)) {
                return false; // éste no llegó a entrar
            }
        }
        return true;
    }

    /** Desactiva el goal de converger al centro en los zombies vivos de la ola (cuando la aldea cayó). */
    private static void disableGoToCenter(ServerLevel level, List<UUID> wave) {
        for (UUID uuid : wave) {
            net.minecraft.world.entity.Entity e = level.getEntity(uuid);
            if (e instanceof AggressiveZombieEntity zombie) {
                zombie.setGoToCenterActive(false);
                // Y se le quita la marca de asediador: con eso vuelve a poder DESCARTARSE por alejarse como cualquier
                // otro bicho (mientras estaba en campaña no se desprendía de él a propósito, ver
                // `AggressiveZombieEntity.removeWhenFarAway`). Si no, los supervivientes de cada asedio se quedarían
                // por el mundo para siempre.
                zombie.setWorldSiegeIndex(-1);
                // Y el brillo de asediador: si el asedio ya se resolvió, ese bicho es un zombie agresivo normal.
                zombie.setGlowingTag(false);
            }
        }
    }

    /**
     * Niveles de experiencia vanilla que paga salvar una aldea. La recompensa va <b>en experiencia</b>: subir
     * un nivel dispara el flujo de siempre del mod ({@code PlayerXpEvent.LevelChange} →
     * {@code setCurrentLevel}, que da <b>1 punto de habilidad por nivel</b>), así que el punto llega con la
     * experiencia de verdad en vez de regalarse suelto. Antes se regalaban 3 puntos directos con
     * {@code addUnspentPoints}, que no subían nada la experiencia.
     */
    private static final int REWARD_EXPERIENCE_LEVELS = 1;

    /**
     * Recompensa por salvar la aldea: materiales, un libro y <b>1 nivel de experiencia</b> (que trae su punto
     * de habilidad por el camino normal). No se llama si la aldea cae.
     */
    private static void grantReward(ServerPlayer player, int objectiveIndex) {
        player.addItem(new ItemStack(Items.IRON_INGOT, 8));
        player.addItem(new ItemStack(Items.LEATHER, 6));
        player.addItem(new ItemStack(Items.WRITTEN_BOOK)); // receta (por ahora un libro genérico)

        int puntosGanados = MissionRewards.giveExperienceLevels(player, REWARD_EXPERIENCE_LEVELS);
        String premio = MissionRewards.describe(REWARD_EXPERIENCE_LEVELS, puntosGanados);
        player.displayClientMessage(Component.literal("La aldea te lo agradece: " + premio + "."), false);
        PlayerExperienceCapabilityInterface expCap =
                IGenericCapability.getUnwrappedPlayerCapability(player, PlayerExperienceCapability.INSTANCE);
        DevilRpg.LOGGER.info("[Village] Aldea {} salvada: {} (quedan {} puntos)",
                objectiveIndex, premio, expCap != null ? expCap.getUnspentPoints() : -1);
    }

    // --- Iteración 3: el mundo también juega (hordas que van a por una aldea) -------------------------

    /** Aldea objetivo de una horda: su índice de objetivo y el centro (la posición del objetivo). */
    public record Settlement(int objectiveIndex, BlockPos center) {
    }

    /** Radio (respecto al jugador) en el que se buscan aldeas a las que mandar una horda. */
    private static final double HORDE_TARGET_RADIUS = 220.0D;
    /** Presión (ticks de abandono) a partir de la cual una aldea empieza a ser objetivo de las hordas. */
    private static final int PRESSURE_MIN_TICKS = 8 * 60 * 20;   // 8 min de juego
    /**
     * Radio alrededor del centro donde se cuentan los aldeanos para decidir si la aldea ha caído (y para saber qué
     * oficios quedan vivos). Tiene que cubrir <b>hasta dónde llegan los goals del pueblo</b>: el <b>leñador</b>
     * trabaja hasta {@code FENCE_RADIUS + 40} (76) y el corral anexo está a 43-57. Con el radio viejo (+28 = 64) un
     * leñador talando a 70 bloques <b>no contaba</b>: la aldea creía que se le había muerto el recolector y le
     * reponía un <b>duplicado</b>, y su salud bajaba sin motivo.
     */
    private static final double FALLEN_CHECK_RADIUS = VillageGenerator.FENCE_RADIUS + 44;
    /** Radio al que se avisa a los jugadores de lo que pasa en una aldea. */
    private static final double SIEGE_WARN_RADIUS = 160.0D;
    /**
     * Fracción de un punto de habilidad que paga rechazar una horda del mundo (la que el mundo manda a por una
     * aldea sin que el jugador la provoque), o sea la enésima parte de la experiencia del nivel del jugador.
     * <p>
     * <b>Antes pagaba 1 nivel entero = 1 punto de habilidad por horda</b>, y esta recompensa es
     * <b>repetible</b>: el mundo lanza una horda cada 3-20 min, así que en una tarde el jugador se completaba
     * el árbol de habilidades sin jugar el resto del mod. Con 1/6 hacen falta 6 hordas rechazadas para un
     * nivel (y su punto), que es lo que el jugador propuso. Lo que se sigue pagando es experiencia de verdad:
     * sube la barra, respeta el nivel y escala con él (a nivel 30 la sexta parte son ~18 puntos).
     * <p>
     * Salvar una aldea en el <b>asedio clásico</b> sigue pagando 1 nivel entero
     * ({@link #REWARD_EXPERIENCE_LEVELS}): aquel se resuelve <b>una sola vez por aldea</b> (queda guardado en
     * {@code VillageSavedData}), así que no es farmeable.
     */
    private static final int WORLD_SIEGE_REWARD_FRACTION = 6;
    /**
     * Botín de una horda rechazada: <b>chips de metal</b> (pepitas de hierro) y algo de <b>cuero</b>, al azar.
     * <p>
     * Son a propósito <b>pocos y variables</b> (lo pidió el jugador: los 4 lingotes de hierro fijos de antes eran
     * demasiado). 9 chips son un lingote en la mesa del herrero de herramientas y 9 carnes podridas son un cuero,
     * así que una horda viene a valer <b>un tercio de lingote</b> y, de vez en cuando, un cuero.
     */
    private static final int WORLD_SIEGE_REWARD_PEPITAS_MIN = 1;
    private static final int WORLD_SIEGE_REWARD_PEPITAS_MAX = 3;
    /** Cueros como mucho (0 entra: muchas veces no cae ninguno). */
    private static final int WORLD_SIEGE_REWARD_CUEROS_MAX = 2;
    /** Asedios dirigidos por el mundo (en curso). Como {@link #DEFENSES}, no se persisten. */
    private static final Map<ServerLevel, List<WorldSiege>> WORLD_SIEGES = new HashMap<>();

    /**
     * Elige la aldea que debe atacar una horda: la <b>más descuidada</b> (mayor presión) de las que están a
     * menos de {@link #HORDE_TARGET_RADIUS} del jugador, ya generadas, que no hayan caído y que no estén ya
     * bajo ataque. Devuelve {@code null} si no hay ninguna (entonces la horda va a por el jugador, como antes).
     * <p>
     * La presión se acumula aquí mismo: son ticks de juego que la aldea lleva sin que nadie la atienda, así
     * que avanza aunque el chunk esté descargado.
     */
    public static Settlement pickHordeTarget(ServerLevel level, ServerPlayer player, int currentIndex) {
        Vec3 anchor = anchorOf(player);
        if (anchor == null) {
            return null;
        }
        VillageSavedData saved = VillageSavedData.get(level);
        long gameTime = level.getGameTime();
        BlockPos playerPos = player.blockPosition();
        Settlement best = null;
        int bestPressure = 0;
        for (int i = 0; i <= currentIndex; i++) {
            if (saved.isFallen(i) || !saved.isGenerated(i) || isUnderAttack(level, i)) {
                continue;
            }
            BlockPos target = ObjectiveTargets.targetOf(anchor, i);
            if (ObjectiveTargets.horizontalDistSqr(playerPos, target) > HORDE_TARGET_RADIUS * HORDE_TARGET_RADIUS) {
                continue;
            }
            int pressure = saved.accruePressure(i, gameTime, pressureMultiplier(saved.getHealth(i)));
            if (pressure < PRESSURE_MIN_TICKS || pressure <= bestPressure) {
                continue;
            }
            bestPressure = pressure;
            best = new Settlement(i, target);
        }
        if (best != null) {
            DevilRpg.LOGGER.info("[Village] Horda dirigida a la aldea {} (presión {} min sin atención)",
                    best.objectiveIndex(), Math.round(bestPressure / 1200.0D));
        }
        return best;
    }

    /**
     * Registra una horda del mundo que va a por una aldea. La resuelve {@link #tickWorldSieges}: si los
     * enemigos caen, la aldea resiste (y su presión vuelve a cero); si se queda sin aldeanos, la aldea cae.
     */
    public static void startWorldSiege(ServerLevel level, Settlement settlement, List<UUID> wave) {
        if (wave.isEmpty()) {
            return;
        }
        WORLD_SIEGES.computeIfAbsent(level, l -> new ArrayList<>())
                .add(new WorldSiege(settlement.objectiveIndex(), settlement.center(), wave));
        // Los que marchan contra la aldea van marcados con brillo, igual que la ola del asedio clásico: es la
        // única forma de saber a quién hay que parar cuando llegan de noche entre los bichos del campo.
        marcarAsediadores(level, wave, true);
        DevilRpg.LOGGER.info("[Village] La aldea {} está siendo atacada: {} enemigos marchan a por ella",
                settlement.objectiveIndex(), wave.size());
        announceNearby(level, settlement.center(),
                "Los tambores suenan: los monstruos marchan contra una aldea cercana.");
    }

    /** Resuelve los asedios del mundo: aldea resiste (se reinicia su presión) o cae (sin aldeanos). */
    private static void tickWorldSieges(ServerLevel level) {
        List<WorldSiege> list = WORLD_SIEGES.get(level);
        if (list == null || list.isEmpty()) {
            return;
        }
        VillageSavedData saved = VillageSavedData.get(level);
        for (int i = list.size() - 1; i >= 0; i--) {
            WorldSiege siege = list.get(i);
            if (isWaveCleared(level, siege.wave)) {
                saved.resetPressure(siege.objectiveIndex);
                DevilRpg.LOGGER.info("[Village] La aldea {} resistió el ataque (presión reiniciada)",
                        siege.objectiveIndex);
                announceNearby(level, siege.center, "La aldea ha resistido: los monstruos han sido rechazados.");
                // Recompensa para quien la defendió (ver registerDefender).
                rewardWorldSiegeDefenders(level, siege);
                list.remove(i);
                continue;
            }
            int vivos = observeVillagers(level, saved, siege.objectiveIndex, siege.center);
            if (vivos == 0) {
                fallVillage(level, saved, siege.objectiveIndex, siege.center);
                announceNearby(level, siege.center, "La aldea ha caído: no queda nadie con vida entre sus muros.");
                // El objetivo avanza para quien lo tuviera pendiente: esa aldea ya no se puede salvar. Se
                // marca como resuelta para que no se lance además el asedio clásico al llegar.
                for (ServerPlayer p : level.players()) {
                    PlayerAuxiliaryCapabilityInterface aux = IGenericCapability.getUnwrappedPlayerCapability(p, PlayerAuxiliaryCapability.INSTANCE);
                    if (aux != null && aux.getObjectiveIndex() == siege.objectiveIndex) {
                        p.displayClientMessage(Component.literal(
                                "La aldea del objetivo ha caído... El objetivo avanza."), false);
                        aux.setObjectiveIndex(siege.objectiveIndex + 1, p);
                    }
                }
                disableGoToCenter(level, siege.wave);
                list.remove(i);
            }
        }
    }

    /** ¿Esa aldea está siendo atacada ahora mismo (por el jugador o por el mundo)? */
    private static boolean isUnderAttack(ServerLevel level, int objectiveIndex) {
        return isUnderWorldSiege(level, objectiveIndex) || isUnderPlayerSiege(level, objectiveIndex);
    }

    /**
     * Lo mismo, público, para el <b>zombie asediador</b>: al cargarse comprueba si el asedio del que formaba parte
     * sigue existiendo. Los asedios no se persisten, así que si el mundo se guardó a mitad (o el asedio se resolvió
     * mientras el zombie estaba descargado), el zombie se queda sin campaña y hay que devolverle el descarte normal.
     */
    public static boolean hayAsedio(ServerLevel level, int objectiveIndex) {
        return isUnderAttack(level, objectiveIndex);
    }

    /**
     * Apunta a un <b>defensor</b> de la aldea: lo llama {@code AggressiveZombieEntity.hurt} cada vez que
     * alguien le pega a un enemigo de una horda del mundo. Vale el jugador y también sus <b>minions</b> (el
     * mérito es del dueño), que es como pelea medio mod. Si esa aldea no tiene horda en curso, no hace nada.
     */
    public static void registerDefender(ServerLevel level, int objectiveIndex, @Nullable Entity attacker) {
        Player player = playerBehind(attacker);
        if (player == null) {
            return;
        }
        for (WorldSiege siege : WORLD_SIEGES.getOrDefault(level, List.of())) {
            if (siege.objectiveIndex == objectiveIndex) {
                siege.defenders.add(player.getUUID());
                return;
            }
        }
    }

    /** El jugador detrás de un atacante: él mismo, o el dueño si el que pega es un minion suyo. */
    @Nullable
    private static Player playerBehind(@Nullable Entity attacker) {
        if (attacker instanceof Player player) {
            return player;
        }
        if (attacker instanceof OwnableEntity ownable && ownable.getOwner() instanceof Player owner) {
            return owner;
        }
        return null;
    }

    /**
     * Un atacante de una horda ha muerto: se quita de la lista de <b>atacantes vivos</b> de su asedio (tanto de
     * los asedios del mundo como del asedio clásico del jugador). Eso permite distinguir "los mataron a todos"
     * de "se descargaron los chunks al alejarse el jugador", y que la recompensa solo se cobre en el primer caso.
     */
    public static void onSiegeAttackerKilled(ServerLevel level, UUID attacker) {
        for (WorldSiege siege : WORLD_SIEGES.getOrDefault(level, List.of())) {
            siege.wave.remove(attacker);
        }
        for (VillageDefense defense : DEFENSES.getOrDefault(level, List.of())) {
            defense.wave.remove(attacker);
        }
    }

    /**
     * Paga a quienes defendieron la aldea de una horda del mundo: <b>1/6 de un punto de habilidad</b> en
     * experiencia (ver {@link #WORLD_SIEGE_REWARD_FRACTION}) y un <b>botín pequeño y variable</b> del pueblo
     * (chips de metal y cuero). Si nadie intervino, la aldea se defendió sola y no se paga nada.
     * <p>
     * La fracción se calcula con el nivel <b>de cada defensor</b>, así que el mismo rechazo vale más para
     * quien más nivel tiene: es la sexta parte de lo que le queda para subir.
     * <p>
     * <b>El botín son pepitas, no lingotes</b> (lo pidió el jugador): antes daba 4 lingotes de hierro fijos, y
     * esto es una recompensa <b>repetible</b> (una horda cada 3-20 min): 4 lingotes por horda convertían al
     * pueblo en una mina. Ahora da entre {@link #WORLD_SIEGE_REWARD_PEPITAS_MIN} y
     * {@link #WORLD_SIEGE_REWARD_PEPITAS_MAX} <b>chips de metal</b> y hasta
     * {@link #WORLD_SIEGE_REWARD_CUEROS_MAX} de <b>cuero</b>, tirados al azar: 9 chips son un lingote en la mesa
     * del herrero de herramientas, así que una horda viene a ser <b>un tercio de lingote</b> como mucho.
     */
    private static void rewardWorldSiegeDefenders(ServerLevel level, WorldSiege siege) {
        if (!siege.wave.isEmpty()) {
            // El asedio se da por resuelto porque los atacantes han dejado de estar cargados (el jugador se
            // alejó y se descargaron los chunks), no porque los mataran: no se paga nada.
            DevilRpg.LOGGER.info("[Village] Aldea {} resistió sin que nadie limpiara la horda ({} atacantes "
                            + "sin confirmar): sin recompensa", siege.objectiveIndex, siege.wave.size());
            return;
        }
        if (siege.defenders.isEmpty()) {
            DevilRpg.LOGGER.info("[Village] Aldea {} resistió sin ayuda de nadie: sin recompensa",
                    siege.objectiveIndex);
            return;
        }
        for (UUID uuid : siege.defenders) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
            if (player == null) {
                continue; // se desconectó antes de que acabara
            }
            int xp = MissionRewards.giveSkillPointFraction(player, WORLD_SIEGE_REWARD_FRACTION);
            String premio = MissionRewards.describeFraction(xp, WORLD_SIEGE_REWARD_FRACTION);
            // Botín ALEATORIO y corto: es lo que el pueblo tiene a mano, no un pago.
            int pepitas = WORLD_SIEGE_REWARD_PEPITAS_MIN
                    + player.getRandom().nextInt(WORLD_SIEGE_REWARD_PEPITAS_MAX - WORLD_SIEGE_REWARD_PEPITAS_MIN + 1);
            int cueros = player.getRandom().nextInt(WORLD_SIEGE_REWARD_CUEROS_MAX + 1);
            player.addItem(new ItemStack(Items.IRON_NUGGET, pepitas));
            String botin = pepitas + (pepitas == 1 ? " chip" : " chips") + " de metal";
            if (cueros > 0) {
                player.addItem(new ItemStack(Items.LEATHER, cueros));
                botin += " y " + cueros + " de cuero";
            }
            player.displayClientMessage(Component.literal(
                    "Rechazaste la horda que iba a por la aldea: " + premio + " y el pueblo te da " + botin + "."),
                    false);
            DevilRpg.LOGGER.info("[Village] Aldea {} resistió: {} y {} para {}", siege.objectiveIndex, premio,
                    botin, player.getGameProfile().getName());
        }
    }

    private static boolean isUnderWorldSiege(ServerLevel level, int objectiveIndex) {
        for (WorldSiege siege : WORLD_SIEGES.getOrDefault(level, List.of())) {
            if (siege.objectiveIndex == objectiveIndex) {
                return true;
            }
        }
        return false;
    }

    private static boolean isUnderPlayerSiege(ServerLevel level, int objectiveIndex) {
        for (VillageDefense defense : DEFENSES.getOrDefault(level, List.of())) {
            if (defense.objectiveIndex == objectiveIndex) {
                return true;
            }
        }
        return false;
    }

    /** Aldeanos vivos alrededor del centro de una aldea. */
    private static int countVillagers(ServerLevel level, BlockPos center) {
        return level.getEntitiesOfClass(Villager.class, new AABB(center).inflate(FALLEN_CHECK_RADIUS)).size();
    }

    /**
     * Cuenta los aldeanos vivos de una aldea y apunta su <b>salud</b>. Si el chunk no está cargado devuelve
     * {@link VillageSavedData#HEALTH_UNKNOWN} y no toca nada: contar entidades descargadas daría 0 y la aldea
     * parecería muerta (se marcaría caída sin motivo y se repoblaría a lo tonto).
     */
    private static int observeVillagers(ServerLevel level, VillageSavedData saved, int objectiveIndex, BlockPos center) {
        if (!level.isLoaded(center)) {
            return VillageSavedData.HEALTH_UNKNOWN;
        }
        int vivos = countVillagers(level, center);
        saved.setHealth(objectiveIndex, vivos);
        return vivos;
    }

    /** Una aldea con pocos aldeanos acumula presión más rápido: los monstruos van a por las débiles. */
    private static double pressureMultiplier(int health) {
        if (health == VillageSavedData.HEALTH_UNKNOWN || health >= VILLAGERS_FOR_FULL_HEALTH) {
            return 1.0D;
        }
        return 1.0D + (VILLAGERS_FOR_FULL_HEALTH - Math.max(0, health)) * PRESSURE_PER_MISSING_VILLAGER;
    }

    /**
     * Un latido de la vida de la aldea (cada {@link #VILLAGE_POLL_TICKS}, solo en aldeas en paz y con aldeanos):
     * <ul>
     *   <li><b>Come de su despensa</b>: cada minuto de juego come <b>cada aldeano</b> (su ración personal, ver
     *       {@link #repartirRaciones}), y el que se queda sin ella pasa hambre <b>él</b>, no "la aldea" en abstracto.
     *       Lo que hay dentro es lo que ha cultivado y horneado su granjero ({@code VillagePantry} +
     *       {@code VillagerFarmGoal}), así que sin granjero no hay pan y llega el hambre.</li>
     *   <li><b>Reparte pan</b> para que críen (vanilla pide 12 puntos de comida).</li>
     *   <li><b>Reparan</b>: una aldea sana vuelve a levantar lo que se cayó en el último ataque.</li>
     *   <li><b>Envejecen</b>: ver {@link #ageVillagers}.</li>
     * </ul>
     */
    private static void tickVillageLife(ServerLevel level, VillageSavedData saved, int objectiveIndex, BlockPos center) {
        List<Villager> aldeanos = level.getEntitiesOfClass(Villager.class, new AABB(center).inflate(FALLEN_CHECK_RADIUS));
        // COMER, ALDEANO POR ALDEANO (etapa E): cada uno tiene SU hambre. Se reparte de uno en uno y primero al que
        // hace más tiempo que no come, para que la comida que hay se reparta de verdad (antes era un contador de
        // aldea: se comía "la aldea", y daba igual quién).
        int raciones = repartirRaciones(level, center, aldeanos);
        // LA DESPENSA ES PARA LA COMIDA: lo que no sea comida ni recambio del granjero (una pluma, cuero, hierro,
        // un tronco...) se mueve al ALMACÉN. Lo pidió el jugador: "los materiales que no pertenezcan a la despensa,
        // que los muevan al almacén, como las plumas". Así el barril no se llena de materiales y la comida no se
        // queda sin sitio (ni el contador de comida mirando cosas que no se comen).
        int sacados = VillagePantry.limpiarDespensa(level, center);
        if (sacados > 0) {
            DevilRpg.LOGGER.info("[Village] Aldea {}: {} cosas que no eran comida movidas de la despensa al almacen",
                    objectiveIndex, sacados);
        }
        int comida = Math.min(MAX_FOOD, VillagePantry.comida(level, center));
        saved.setFood(objectiveIndex, comida);

        // HAMBRE: cada aldeano que lleve sin comer más de HAMBRE_PACIENTE_TICKS va débil; y si se alarga, muere ÉL.
        pasarHambre(level, saved, objectiveIndex, aldeanos);

        // MEDIDA (para poder ajustar el hambre con números, no a ojo): cada 5 min, lo que hay y lo que se come.
        if (level.getGameTime() % (5L * 60L * 20L) == 0L) {
            DevilRpg.LOGGER.info("[Village] Aldea {}: comida {} puntos, {} aldeanos, {} camas, {} raciones en el ultimo minuto",
                    objectiveIndex, comida, aldeanos.size(), contarCamas(level, center), raciones);
        }

        // COMER DE VERDAD: se les reparte pan de la despensa (lo recogen ellos) para que puedan criar como en vanilla.
        feedVillagers(level, saved, objectiveIndex, center, aldeanos);

        // OBRERO: se asegura de que exista el PLANO de la aldea y de que haya un aldeano que repare. El trabajo
        // lo hace su goal, andando y bloque a bloque (ver VillagerRepairGoal): aquí solo se prepara.
        prepareRepairs(level, saved, objectiveIndex, center, aldeanos);

        ageVillagers(level, aldeanos);
    }

    /**
     * Deja la aldea lista para repararse <b>sola y de verdad</b>: captura el <b>plano</b> la primera vez (qué
     * bloque debería haber en cada sitio) y nombra un <b>obrero</b> si no lo hay. Al de partidas viejas se le
     * pone antes la granja, para que el plano la incluya.
     */
    private static void prepareRepairs(ServerLevel level, VillageSavedData saved, int objectiveIndex, BlockPos center, List<Villager> aldeanos) {
        // Cambios de TRAZADO que hay que aplicar también a las aldeas ya construidas:
        //  1-2: granja (cultivos, acequia, compostero) y su nivelado a un solo nivel.
        //  3:   el plano apunta también lo que está a ras de suelo.
        //  4:   el plano pasa a ser canónico (lo graba el generador).
        //  5:   nivelado por mediana + escalón de entrada en las casas.
        //  6:   las cabañas procedurales se sustituyen por CASAS DEL JUEGO (con la marca `hasNewHouses`, para no
        //       reconstruir las que ya son nuevas: rehacer una casa borra lo que haya dentro).
        if (saved.getLayout(objectiveIndex) < CURRENT_LAYOUT) {
            int casas = saved.getCasasVersion(objectiveIndex);
            // DESPEJE DEL RECINTO: los árboles que quedaron DENTRO de la muralla se quitan (el pueblo se funda en un
            // claro). Va lo PRIMERO, antes de rehacer nada, y se distingue un árbol del muro por su FORMA (el muro son
            // troncos tumbados y los postes de las casas van pegados a sus paredes), no por el plano: el plano de una
            // aldea migrada también tiene dentro esos árboles. Medido en el guardado del jugador: 137 árboles dentro.
            VillageGenerator.limpiarArbolesDeDentro(level, center);
            if (casas < CURRENT_HOUSES) {
                // Se rehacen TODAS las construcciones con el nivelado nuevo (a la cota de la plaza, no a la mediana
                // contaminada por los tejados) y se añaden las que falten (cuarta casa e iglesia). OJO: rehacer una
                // casa borra lo que tenga dentro; queda en el log con un WARN.
                VillageGenerator.actualizarCasas(level, center);
                VillageGenerator.actualizarTemplo(level, center);
                saved.setCasasVersion(objectiveIndex, CURRENT_HOUSES);
            }
            // El MURO se reconstruye entero: si un nivelado viejo lo enterró o se comió sus troncos, sus huecos no
            // están en ningún plano y el obrero no podría reponerlos nunca (el muro no se puede "reparar a medias").
            VillageGenerator.rehacerMuro(level, center);
            // CAMINOS EN ALTO: los que el bug de la cota del kiosco dejó encima de los tejados se quitan aquí (esta
            // migración corre siempre al subir la versión, mientras que `actualizarCasas` solo corre si cambian las
            // casas: por eso la limpieza anterior no llegaba a ejecutarse en aldeas ya actualizadas).
            VillageGenerator.limpiarCaminosFlotantes(level, center, VillageGenerator.cotaDeLaPlaza(level, center));
            VillageGenerator.farm(level, center);
            // EL CORRAL, ENSANCHADO (migración 45): el corral pasa de 15x15 a 19x19 y se retira el viejo (solo sus
            // bloques). Va ANTES de `asegurarGranjaAnexa`, que si no saldría antes de tiempo al ver el corral viejo.
            VillageGenerator.ensancharElCorral(level, center);
            // EL ALMACÉN SE MUEVE (migración 45): delante de la puerta de la taberna (18,18) a su lado (48,21), y
            // más grande. Lo que hubiera en los cofres viejos se pasa al nuevo antes de retirar el viejo.
            VillageGenerator.moverAlmacen(level, center);
            // HERRERÍA: el taller de los herreros del juego, con su muelle y su mesa de herrería (sus puestos de
            // trabajo). Va AQUÍ, antes de tirar el plano, para que la herrería y su camino entren en el plano nuevo.
            VillageGenerator.asegurarHerreria(level, center);
            // BARRACA de la milicia: igual, antes de tirar el plano, para que el edificio y sus camas entren en el
            // plano y el obrero los reponga.
            VillageGenerator.asegurarBarraca(level, center);
            // GRANJA ANEXA de animales (etapa D): FUERA de la valla, al este, con su corral y su cobertizo. Va aquí
            // por el mismo motivo: sus bloques tienen que entrar en el plano nuevo para que el obrero la reponga.
            VillageGenerator.asegurarGranjaAnexa(level, center);
            // GALLINERO y CERCA/PORTONES del anexo (etapa E): los pollos encerrados con su tejado, el suelo del
            // corral tapado (un cráter deja la puerta sin apoyo) y las puertas de VALLA (en vez de la puerta de
            // madera). Van aparte de `asegurarGranjaAnexa` —que sale antes de tiempo si el corral ya está— para que
            // lleguen también a las aldeas que ya tenían corral, y antes de tirar el plano para que entren en él.
            VillageGenerator.asegurarGallinero(level, center);
            VillageGenerator.asegurarCercaDelAnexo(level, center);
            // ARBOLEDA DEL PUEBLO (etapa E): los cuatro plantones del hueco de césped, para la aldea que nace donde no
            // hay bosque. Va antes de tirar el plano, como todo lo demás.
            VillageGenerator.asegurarArboleda(level, center);
            // Y LA ORILLA de la aldea de mar (islita): seca y pareja, para que el agua no haga cuadros en el borde.
            VillageGenerator.asegurarOrilla(level, center);
            // LOS RESTOS COLGADOS (etapa F, lo pidió el jugador): en una aldea de MONTAÑA el recorte del terreno deja
            // nieve polvo (que no cuenta como suelo), capas de nieve y plantas colgando por encima del pueblo. Se
            // retiran las que no tienen nada debajo.
            VillageGenerator.limpiarRestosColgados(level, center);
            // LOS PICOS DE LAS ESQUINAS (etapa E, lo pidió el jugador): el suelo llano es un cuadrado y el talud un
            // círculo, así que a las cuatro esquinas les sobraba un triángulo allanado colgando sobre el mar, con el
            // corte a la vista. Se rebajan a la base del talud.
            VillageGenerator.asegurarTalud(level, center);
            // COCINA del pueblo (etapa E): el ahumador del cocinero, en el kiosco. Va antes de tirar el plano para
            // que entre en él y el obrero lo reponga.
            VillageGenerator.asegurarCocina(level, center);
            // Y LA TABERNA (etapa F): el comedor del pueblo con la cocina dentro y la posada arriba. Va antes del
            // plano, como todo lo demás (y antes de la cocina, que ahora vive en ella: la llama `asegurarCocina`).
            // Desde la migración 44 es la taberna GRANDE: si la aldea todavía tiene la vieja (de roble claro), la
            // prueba de `tabernaConstruida` falla, `asegurarTaberna` despeja su solar entero (la vieja cabía dentro)
            // y levanta la nueva; lo que hubiera en sus cofres se guarda antes en el almacén.
            VillageGenerator.asegurarTaberna(level, center);
            // LA ESCALERA DE LA TABERNA (migración 48, lo pidió el jugador): el hueco del forjado tiene que llegar
            // hasta la meseta (con el hueco corto, quien sube da con la cabeza en el borde del piso de arriba) y la
            // barra se corre al este (su extremo 2x2 quedaba justo delante del pie de la escalera). Solo toca esas
            // celdas: no rehace la taberna (eso borraría la despensa y las camas).
            VillageGenerator.arreglarEscaleraDeLaTaberna(level, center);
            // LA CAL DE LOS MUROS (migración 49): el recorte del nivelado se comía los paneles de terracota de los
            // muros Tudor (contaba como "terreno que sobra") y las paredes quedaban con agujeros. Ya está arreglado
            // de raíz (el recorte para en el primer bloque construido) y aquí se REPASAN los muros y los frontones de
            // la taberna que ya existe: `muroTudor` y `tejadoDeLaTaberna` solo ponen estructura, así que volver a
            // pasarlos es idempotente y no toca ni la despensa ni las camas.
            VillageGenerator.rehacerMurosDeLaTaberna(level, center);
            // Y su CAMINO desde la plaza (torcido, para no cruzar la parcela de la granja).
            VillageGenerator.caminoALaTaberna(level, center);
            // LA PESQUERA (etapa G): el lago, la caseta del pescador, su BARRIL (el puesto) y sus peces. Va antes de
            // tirar el plano, como todo lo demás, y con su camino desde la plaza (tampoco cruza ningún bancal).
            VillageGenerator.asegurarPesquera(level, center);
            VillageGenerator.caminoALaPesquera(level, center);
            // LA DESPENSA, DE LA PLAZA A LA TABERNA (migración 47, lo pidió el jugador): el cofre de la comida del
            // kiosco se retira, con lo que tuviera dentro pasado ANTES a la despensa nueva (la cocina de la taberna)
            // y lo que no quepa al almacén. Va después de la taberna —que ya está construida arriba, con su cofre
            // en la cocina— y antes de tirar el plano, para que el kiosco sin cofre entre en el plano nuevo.
            VillageGenerator.retirarDespensaDelKiosco(level, center);
            // REBAÑO ESCAPADO (una sola vez, al migrar): antes de que existiera la marca del rebaño, el ganado que se
            // colaba por el portón se perdía sin remedio y el corral se quedaba vacío (y sin carne). Aquí se reconoce
            // el que anda suelto FUERA de la muralla y cerca del corral; luego, en el latido, vuelve a casa.
            VillageGenerator.adoptarGanadoPerdido(level, center);
            // El plano se tira: hay que volver a capturarlo, ya con las casas nuevas, el muro y las reglas actuales.
            saved.clearBlueprint(objectiveIndex);
            saved.setLayout(objectiveIndex, CURRENT_LAYOUT);
            // AUTOCOMPROBACIÓN de faroles flotantes (guardia del bug de los 16 faroles colgados del aire): se mira
            // al terminar la migración, que es cuando ya está todo lo nuevo construido y reparado.
            VillageGenerator.auditarFarolesFlotantes(level, center);
            DevilRpg.LOGGER.info("[Village] Aldea {}: trazado actualizado a la version {} (casas, muro, granja y plano)",
                    objectiveIndex, CURRENT_LAYOUT);
        }
        if (!saved.hasBlueprint(objectiveIndex)) {
            VillageSavedData.Blueprint plano = VillageGenerator.captureBlueprint(level, center);
            saved.setBlueprint(objectiveIndex, plano);
            DevilRpg.LOGGER.info("[Village] Aldea {}: plano guardado ({} bloques)", objectiveIndex, plano.size());
        }
        // HERRERÍA: en las aldeas que ya estaban al día (o en las nuevas) se asegura igualmente: es idempotente y así
        // también se le repone la mesa de herrería si alguien se la llevó.
        VillageGenerator.asegurarHerreria(level, center);
        // BARRACA de la milicia: lo mismo (idempotente). Si el jugador se llevó su suelo de piedra, se vuelve a
        // levantar entera; si está, no se toca (reconstruirla borraría las camas y lo que haya dentro).
        VillageGenerator.asegurarBarraca(level, center);
        // KIOSCO + DESPENSA: la plataforma de la plaza con su campana arriba y el cofre doble (si falta en aldeas
        // viejas). Es donde el granjero guarda el trigo, donde hornea el pan y de donde come la aldea.
        VillageGenerator.asegurarKiosco(level, center);
        // ALMACÉN del pueblo: cobertizo con cofre doble (que crece) donde el constructor recolector va dejando lo que
        // recoge. Es una construcción aparte, al lado de la plaza.
        VillageGenerator.asegurarAlmacen(level, center);
        // GRANJA ANEXA de animales (etapa D): igual (idempotente). Si el jugador se llevó la valla, se vuelve a
        // levantar; si está, no se toca (reconstruirla borraría su cobertizo y lo que tenga dentro).
        VillageGenerator.asegurarGranjaAnexa(level, center);
        // CERCA, PORTONES, SUELO y GALLINERO del anexo (etapa E): idempotentes. El portón de valla sustituye a la
        // puerta vieja de madera (y lo abre el pueblo con `VillagerGateGoal`, porque el juego no deja que un aldeano
        // abra una puerta de valla), el suelo del corral se tapa (si no, la puerta se cae por falta de apoyo y los
        // animales se caen por el agujero) y el gallinero mete a los pollos en un corralillo con tejado, para que no
        // se salgan y sus huevos queden dentro, a mano del ganadero.
        VillageGenerator.asegurarCercaDelAnexo(level, center);
        VillageGenerator.asegurarGallinero(level, center);
        // ARBOLEDA DEL PUEBLO (etapa E): idempotente. La aldea que nace sin bosque (una islita, un desierto, una
        // llanura pelada) planta aquí sus cuatro árboles y el leñador los tala y los replanta: sin esto no habría
        // troncos y se caerían los tablones, los palos, los arcos, las flechas y los escudos.
        VillageGenerator.asegurarArboleda(level, center);
        // Y LA ORILLA de la aldea de mar (islita): el terreno llano queda a la altura del agua, así que su borde sale
        // "a cuadros" (agua a la cota pegada a césped a la cota). Se saca un anillo de playa seca y pareja; en una
        // aldea de tierra adentro no toca nada (lo decide mirando si hay agua a la capa que se pisa en el anillo).
        VillageGenerator.asegurarOrilla(level, center);
        // COCINA del pueblo (etapa E): el ahumador y la mesa del cocinero. Idempotente (va aparte de `asegurarKiosco`
        // porque aquél sale antes de tiempo cuando el kiosco ya está). Desde la etapa F la cocina vive en la taberna.
        VillageGenerator.asegurarCocina(level, center);
        // TABERNA (etapa F): el comedor del pueblo (abajo) y la posada (arriba). Idempotente (se comprueba por su
        // barra): si el jugador se lleva media taberna, el pueblo la vuelve a levantar.
        VillageGenerator.asegurarTaberna(level, center);
        // PESQUERA (etapa G): el lago del pescador, su caseta y su barril. Idempotente (vale el agua del lago o el
        // barril como testigo): si el jugador se lleva media pesquera, el pueblo la vuelve a levantar.
        VillageGenerator.asegurarPesquera(level, center);
        // REBAÑO: el corral se llena UNA vez (al construirlo o al migrar). Después se mantiene solo, con DOS reglas:
        //   1) RECOGER AL QUE SE ESCAPA. El corral solo tiene el portón, y el pueblo lo abre para pasar (el juego no
        //      deja que un aldeano abra una puerta de valla, de ahí `VillagerGateGoal`): con las horas, el ganado se
        //      cuela por el hueco y se pierde. Medido en el guardado del jugador: quedaba UNA vaca dentro y 8 vacas,
        //      6 ovejas, 6 gallinas y 2 puercos sueltos a 76-83 bloques del pueblo. Y un corral vacío NO DA CARNE: el
        //      ganadero no ve animales, no cría ni sacrifica, y la granja entera se muere. Los del rebaño (marcados)
        //      vuelven a casa; los animales sueltos SIN marca no se tocan (pueden ser del jugador).
        //   2) REPONER LA PAREJA. Si a una especie le quedan menos de dos adultos ya no puede criar NUNCA (ni carne de
        //      vaca, ni lana, ni huevos): el pueblo le trae la pareja. Lo pidió el jugador.
        // Las dos van con la espera larga de 3 días de juego, para que esto no sea un grifo de carne gratis.
        // OJO: la espera es SOLO para reponer. La PRIMERA vez (marca 0 = nunca se ha soltado el rebaño) es YA.
        // Midiendo la espera desde 0, en un mundo con menos de 3 días de juego (gameTime < 72000) el corral se
        // quedaba VACÍO PARA SIEMPRE: medido en el guardado del jugador, el anexo se construyó con el reloj del
        // mundo en 24200 (un mundo joven) y no soltó ni un animal.
        VillageGenerator.recogerGanadoPerdido(level, center);
        long marcaRebano = saved.getAnexoAnimales(objectiveIndex);
        if (marcaRebano == 0L
                || level.getGameTime() - marcaRebano >= VillageGenerator.ANEXO_REBANO_ESPERA_TICKS) {
            if (VillageGenerator.corralVacio(level, center)) {
                VillageGenerator.criarRebanoInicial(level, center);
                saved.setAnexoAnimales(objectiveIndex, level.getGameTime());
            } else if (VillageGenerator.reponerParejasDelCorral(level, center)) {
                saved.setAnexoAnimales(objectiveIndex, level.getGameTime());
            }
        }
        // EL LAGO SE REPUEBLA: cada dos minutos, un pez (hasta el tope de 6). Lo que pesca el pueblo está limitado por
        // lo que cría su lago, no por un contador de comida: el pescador saca un pez de verdad y el lago tarda en
        // recuperarlo. (Se pregunta la cota y la lista de peces solo cada dos minutos, no por tick.)
        if (level.getGameTime() % (20L * 120L) == 0L) {
            VillageGenerator.reponerPecesDelLago(level, center);
        }
        // PROFESIONES PERDIDAS: a los aldeanos de una partida vieja el juego les BORRÓ el oficio (el cerebro
        // vanilla trae `ResetProfession`: sin puesto de trabajo en el cerebro, con XP 0 y nivel 1, devuelve al
        // aldeano a SIN OFICIO). Sin granjero no hay huerta ni pan y la aldea pasa hambre con la despensa vacía,
        // así que aquí se le devuelve el oficio que falta a cada aldeano que se quedó sin ninguno.
        reponerProfesiones(level, aldeanos, objectiveIndex);
        // HERREROS: los DOS (armas y herramientas) trabajan en el taller del pueblo: cogen los materiales del almacén,
        // fabrican en su puesto (muelle de afilar / mesa de herrería) y dejan la pieza en el almacén, de donde se
        // equipará la futura guardia. Los goals no se guardan con la partida: se reponen al verlos.
        for (Villager villager : aldeanos) {
            VillagerProfession profesion = villager.getVillagerData().getProfession();
            if (!villager.isBaby() && !VillagerGuardGoal.esGuardia(villager)
                    && (profesion == VillagerProfession.WEAPONSMITH || profesion == VillagerProfession.TOOLSMITH)) {
                asegurarGoalDeHerrero(villager, center, objectiveIndex);
            }
        }
        // GRANJERO: los goals no se guardan con la partida, así que se le repone cada vez que se le ve. Cultiva,
        // cosecha, fertiliza con la harina del compostero y trae el trigo a la despensa.
        for (Villager villager : aldeanos) {
            if (!villager.isBaby() && !VillagerGuardGoal.esGuardia(villager)
                    && villager.getVillagerData().getProfession() == VillagerProfession.FARMER) {
                asegurarGoalDeGranjero(villager, center, objectiveIndex);
            }
        }
        // OBREROS: puede haber VARIOS (hasta MAX_BUILDERS) repartiéndose el trabajo, y se RECALCULA quiénes son en
        // cada latido (ver más abajo), porque la marca de obrero no se le quitaba a NADIE: un granjero que fue
        // obrero cuando la aldea estaba débil (murió gente y era el único adulto) se quedaba reparando caminos para
        // siempre. El jugador lo vio: quitó un bloque del camino y fue el granjero a reponerlo en vez del obrero.
        // RECOLECTOR: el aldeano sin oficio (holgazán) se dedica SOLO a recoger cosas y guardarlas en el almacén. El
        // constructor, así, se dedica solo a reparar (antes llevaba los dos goals y se pasaba el día recolectando).
        for (Villager villager : aldeanos) {
            if (!villager.isBaby() && !VillagerGuardGoal.esGuardia(villager)
                    && villager.getVillagerData().getProfession() == VillagerProfession.NITWIT) {
                asegurarGoalDeRecolector(villager, center, objectiveIndex);
                asegurarGoalDeLenador(villager, center, objectiveIndex);
            }
        }
        // GANADERO (etapa D): el pastor vive con el rebaño en la granja anexa. Cría, recoge lo que sueltan los
        // animales (huevos, lana, carne de los sacrificios) y lo baja al almacén. Es un puesto FIJO del pueblo, así
        // que no lo toca el reparto de obreros ni la milicia.
        for (Villager villager : aldeanos) {
            if (!villager.isBaby() && !VillagerGuardGoal.esGuardia(villager)
                    && villager.getVillagerData().getProfession() == VillagerProfession.SHEPHERD) {
                asegurarGoalDeGanadero(villager, center, objectiveIndex);
            }
        }
        // COCINERO (etapa E): el carnicero cocina en el ahumador del kiosco la carne cruda y las patatas: crudo = 2
        // puntos de comida, cocinado = 4, así que es la palanca del hambre del pueblo. Puesto fijo, como el ganadero.
        for (Villager villager : aldeanos) {
            if (!villager.isBaby() && !VillagerGuardGoal.esGuardia(villager)
                    && villager.getVillagerData().getProfession() == VillagerProfession.BUTCHER) {
                asegurarGoalDeCocinero(villager, center, objectiveIndex);
            }
        }
        // PESCADOR (etapa G): pesca en el lago de su pesquera y baja el pescado crudo a la despensa (el cocinero lo
        // ahúma: crudo = 2 puntos, cocinado = 4, igual que la carne del corral). Puesto fijo, como el ganadero.
        for (Villager villager : aldeanos) {
            if (!villager.isBaby() && !VillagerGuardGoal.esGuardia(villager)
                    && villager.getVillagerData().getProfession() == VillagerProfession.FISHERMAN) {
                asegurarGoalDelPescador(villager, center, objectiveIndex);
            }
        }
        // PORTONES del anexo (etapa E): los abre y los cierra el PUEBLO, porque el juego no deja que un aldeano abra
        // una puerta de valla. Se le pone a TODOS los adultos (al ganadero, que vive ahí; a la guardia, que patrulla
        // el corral; y a cualquiera que baje al anexo), y no ocupa banderas: va a la vez que su faena.
        for (Villager villager : aldeanos) {
            if (!villager.isBaby()) {
                asegurarGoalDePortones(villager, center);
            }
        }
        // RECOGIDA POR OFICIO (lo pidió el jugador: "nadie recoge los materiales del suelo y el recolector no se da
        // abasto; que cada oficio recoja los materiales propios de su oficio"): cada aldeano con oficio barre del
        // suelo SUS materiales (el herrero el hierro y el equipo de los enemigos, el granjero el grano y las
        // semillas, el ganadero la carne y los huevos, el cocinero lo que cocina, el clérigo lo suyo) y los guarda
        // donde le toca: la comida a la despensa y los materiales al almacén. Va por encima de su faena pero con un
        // radio corto, así que no es un barrendero: recoge lo que se encuentra yendo a trabajar.
        for (Villager villager : aldeanos) {
            if (!villager.isBaby() && !VillagerGuardGoal.esGuardia(villager)
                    && com.chipoodle.devilrpg.entity.goal.VillagerPickupGoal.tieneMateriales(
                            villager.getVillagerData().getProfession())) {
                asegurarGoalDeRecogidaPorOficio(villager, center, objectiveIndex);
            }
        }
        // LA TABERNA (etapa F): el aldeano con hambre se va a la taberna a comer y a reponer energía (lo pidió el
        // jugador: "una taberna donde trabaje el cocinero y todos vayan a comer ahí cuando lo necesiten... los
        // soldados pueden pasar cuando no estén de guardia"). Va a prioridad 6, por debajo de todos los oficios y del
        // guardia: primero se trabaja y, cuando no hay faena (o el guardia está entre rondas), se va a la mesa.
        for (Villager villager : aldeanos) {
            if (!villager.isBaby()) {
                asegurarGoalDeLaTaberna(villager, center, objectiveIndex);
            }
        }
        // GUARDIA (milicia): los aldeanos adultos que SOBRAN (cubiertos los puestos fijos: granjero, los dos
        // herreros, clérigo y recolector) se alistan. Se calcula ANTES del reparto de obreros, porque un guardia
        // tiene su puesto y no puede acabar de constructor.
        repartirGuardia(level, aldeanos, center, objectiveIndex);
        // MARCHA A LA GUARIDA: con la formación completa (4 espadachines y 3 arqueros) la milicia se va a atacar el
        // núcleo de la guarida de la aldea. Se decide aquí, que es donde ya está la lista de aldeanos.
        comprobarMarcha(level, aldeanos, center, objectiveIndex);
        // El RECOLECTOR (holgazán) no cuenta para el reparto de obreros: es un puesto fijo y no debe acabar de
        // constructor (si no, se pasa el día reparando y no recoge nada).
        int adultos = 0;
        for (Villager villager : aldeanos) {
            if (puedeSerObrero(villager)) {
                adultos++;
            }
        }
        // Se reserva al menos un aldeano para lo suyo (huerta, comercio...) si hay gente de sobra.
        int deseados = Math.max(1, Math.min(MAX_BUILDERS, adultos - 1));
        // EL ORDEN MANDA: primero los que YA eran obreros y NO son granjeros (para no cambiarlos cada latido),
        // después los demás adultos que no son granjero ni holgazán, y SOLO al final los granjeros (mejor una huerta
        // más lenta que una aldea en ruinas). A los que SOBRAN se les quita la marca: el aldeano vuelve a su oficio.
        List<Villager> orden = new ArrayList<>();
        for (int pasada = 0; pasada < 4; pasada++) {
            for (Villager villager : aldeanos) {
                if (!puedeSerObrero(villager) || orden.contains(villager)) {
                    continue;
                }
                boolean yaEra = villager.getPersistentData().getBoolean(BUILDER_TAG);
                boolean esGranjero = villager.getVillagerData().getProfession() == VillagerProfession.FARMER;
                boolean toca = switch (pasada) {
                    case 0 -> yaEra && !esGranjero;   // los obreros de siempre que no son granjeros
                    case 1 -> !yaEra && !esGranjero;  // los demás adultos con otro oficio
                    case 2 -> yaEra;                  // un granjero que ya hacía de obrero
                    default -> true;                  // y, si no llega nadie, cualquier granjero
                };
                if (toca) {
                    orden.add(villager);
                }
            }
        }
        for (int i = 0; i < orden.size(); i++) {
            if (i < deseados) {
                marcarObrero(orden.get(i), center, objectiveIndex);
            } else {
                desmarcarObrero(orden.get(i));
            }
        }
    }

    /**
     * ¿Ese aldeano puede ser obrero? (ni crías, ni el recolector, ni el ganadero ni un guardia, que tienen su propio
     * puesto). El ganadero entra aquí desde la etapa D: si no, el reparto de obreros se lo llevaba a reparar caminos
     * y el rebaño se quedaba sin nadie que lo cuidara.
     */
    private static boolean puedeSerObrero(Villager villager) {
        VillagerProfession profesion = villager.getVillagerData().getProfession();
        return !villager.isBaby() && profesion != VillagerProfession.NITWIT
                && profesion != VillagerProfession.SHEPHERD
                && profesion != VillagerProfession.BUTCHER
                && !VillagerGuardGoal.esGuardia(villager);
    }

    /**
     * Alista en la <b>guardia</b> a los aldeanos adultos que <b>sobran</b> (lo pidió el jugador: la milicia se forma
     * solo cuando están cubiertos los oficios del pueblo) y desalista a los que ya no sobran.
     * <p>
     * <b>Quién sobra</b>: se reparten los <b>puestos fijos</b> (1 granjero, 1 herrero de armas, 1 de herramientas,
     * 1 clérigo, 1 recolector, 1 ganadero y 1 cocinero) en orden <b>estable</b> (por UUID): los primeros de cada
     * oficio se quedan con su
     * puesto y los demás son gente de sobra. Así la guardia no le quita el granjero ni los herreros a la aldea (que
     * es lo que la dejaría sin comer y sin indumentaria) y con 5 aldeanos —los que tiene una aldea sana— no hay
     * guardia: hacen falta <b>crías</b>, o sea una aldea que crece.
     * <p>
     * El <b>tipo</b> va por número: los primeros son espadachines y el resto arqueros, en la proporción que el
     * jugador quiere para la marcha a la guarida (4 espadachines y 3 arqueros).
     */
    private static void repartirGuardia(ServerLevel level, List<Villager> aldeanos, BlockPos center,
                                        int objectiveIndex) {
        // Puestos fijos que NO pueden quedarse sin cubrir (cupo por oficio). Lo que sobre de cada oficio, o los
        // oficios que no estén en la lista, son candidatos.
        Map<VillagerProfession, Integer> cupo = new HashMap<>();
        cupo.put(VillagerProfession.FARMER, 1);
        cupo.put(VillagerProfession.WEAPONSMITH, 1);
        cupo.put(VillagerProfession.TOOLSMITH, 1);
        cupo.put(VillagerProfession.CLERIC, 1);
        cupo.put(VillagerProfession.NITWIT, 1); // el recolector
        cupo.put(VillagerProfession.SHEPHERD, 1); // el ganadero de la granja anexa (etapa D)
        cupo.put(VillagerProfession.BUTCHER, 1); // el cocinero del kiosco (etapa E)

        List<Villager> adultos = new ArrayList<>();
        for (Villager villager : aldeanos) {
            if (puedeSerGuardia(villager)) {
                adultos.add(villager);
            }
        }
        adultos.sort(Comparator.comparing(v -> v.getUUID().toString())); // orden ESTABLE entre latidos

        Map<VillagerProfession, Integer> usados = new HashMap<>();
        List<Villager> sobrantes = new ArrayList<>();
        for (Villager villager : adultos) {
            VillagerProfession profesion = villager.getVillagerData().getProfession();
            int yaHay = usados.getOrDefault(profesion, 0);
            if (yaHay < cupo.getOrDefault(profesion, 0)) {
                usados.put(profesion, yaHay + 1);
                continue; // cubre un puesto fijo del pueblo
            }
            sobrantes.add(villager);
        }

        // Los primeros se alistan (hasta el tope de la milicia); el resto vuelve a la vida civil.
        for (int i = 0; i < sobrantes.size(); i++) {
            Villager villager = sobrantes.get(i);
            if (i >= MILICIA_MAX) {
                desalistarGuardia(villager);
                continue;
            }
            alistarGuardia(level, villager, center, objectiveIndex, i,
                    i < MILICIA_ESPADACHINES ? VillagerGuardGoal.ESPADACHIN : VillagerGuardGoal.ARQUERO);
        }
        // Y los que YA no sobran (murió gente, la aldea necesita su oficio) dejan la guardia: si no, la aldea se
        // quedaría sin granjero o sin herreros por tener milicia.
        for (Villager villager : aldeanos) {
            if (VillagerGuardGoal.esGuardia(villager) && !sobrantes.contains(villager)) {
                desalistarGuardia(villager);
            }
        }
    }

    /**
     * ¿Ese aldeano puede alistarse? Basta con que <b>no sea una cría</b>: los puestos fijos se reparten en
     * {@link #repartirGuardia}, así que el que no cubre ninguno es, por definición, gente de sobra (incluidos los
     * que se quedaron <b>sin oficio</b> porque los puestos del pueblo ya estaban cubiertos).
     */
    private static boolean puedeSerGuardia(Villager villager) {
        return !villager.isBaby();
    }

    /** Alista a un aldeano (si no lo estaba) con su tipo y su número, y le pone su goal de guardia. */
    private static void alistarGuardia(ServerLevel level, Villager villager, @Nullable BlockPos center,
                                       int objectiveIndex, int indice, int tipo) {
        boolean yaEra = VillagerGuardGoal.esGuardia(villager);
        boolean mismoTipo = VillagerGuardGoal.tipoDe(villager) == tipo
                && villager.getPersistentData().getInt(GUARD_INDEX_TAG) == indice;
        villager.getPersistentData().putBoolean(GUARD_TAG, true);
        villager.getPersistentData().putInt(GUARD_TYPE_TAG, tipo);
        villager.getPersistentData().putInt(GUARD_INDEX_TAG, indice);
        // Un obrero que pasa a la guardia deja de ser obrero (tiene su puesto).
        desmarcarObrero(villager);
        // OJO: el pánico del aldeano (que le hace huir) NO se puede quitar desde aquí: `Brain.addActivity` solo AÑADE
        // comportamientos (no reemplaza) y `removeAllBehaviors` se lleva por delante el cerebro entero. Lo apaga el
        // propio goal del guardia en combate, borrando los recuerdos que lo disparan (ver `VillagerGuardGoal.calmar`).
        if (center != null) {
            asegurarGoalDeGuardia(villager, center, objectiveIndex);
        }
        if (!yaEra) {
            DevilRpg.LOGGER.info("[Village] Aldea {}: {} se alista en la guardia como {}",
                    objectiveIndex, villager.getUUID(), tipo == VillagerGuardGoal.ARQUERO ? "arquero" : "espadachin");
        } else if (!mismoTipo) {
            DevilRpg.LOGGER.info("[Village] Aldea {}: {} cambia de puesto en la guardia (indice {}, {})",
                    objectiveIndex, villager.getUUID(), indice,
                    tipo == VillagerGuardGoal.ARQUERO ? "arquero" : "espadachin");
        }
    }

    /** Saca a un aldeano de la guardia y le quita su goal (vuelve a su oficio). */
    private static void desalistarGuardia(Villager villager) {
        if (!VillagerGuardGoal.esGuardia(villager)) {
            return;
        }
        villager.getPersistentData().putBoolean(GUARD_TAG, false);
        for (WrappedGoal wrapped : List.copyOf(villager.goalSelector.getAvailableGoals())) {
            if (wrapped.getGoal() instanceof VillagerGuardGoal) {
                villager.goalSelector.removeGoal(wrapped.getGoal());
            }
        }
        DevilRpg.LOGGER.info("[Village] {} deja la guardia y vuelve a su oficio", villager.getUUID());
    }

    /**
     * Le pone al guardia su goal. Prioridad <b>3</b>: por delante de los goals de oficio (4), porque cuando está de
     * guardia está de guardia; y por detrás de las prioridades de combate/huida de vanilla, que son más urgentes.
     */
    public static void asegurarGoalDeGuardia(Villager villager, BlockPos center, int objectiveIndex) {
        for (WrappedGoal wrapped : List.copyOf(villager.goalSelector.getAvailableGoals())) {
            if (wrapped.getGoal() instanceof VillagerGuardGoal) {
                return;
            }
        }
        villager.goalSelector.addGoal(3, new VillagerGuardGoal(villager, center, objectiveIndex));
    }

    // --- la marcha de la milicia contra la guarida --------------------------------------------------

    /** Arqueros que hacen falta, con los espadachines, para que la milicia marche (la formación del jugador). */
    private static final int MILICIA_ARCHEROS = MILICIA_MAX - MILICIA_ESPADACHINES;
    /**
     * Cuánto dura una marcha (12 min). Si se pasa, la milicia vuelve: una marcha eterna dejaría la aldea sin guardia
     * para siempre si la guarida no se puede limpiar (por ejemplo, con el núcleo todavía sellado).
     */
    private static final int MARCHA_TICKS = 12 * 60 * 20;

    /**
     * Marcha de la milicia contra la guarida de la aldea: a dónde va y cuándo se le acaba el tiempo.
     * <p>
     * No se persiste a propósito (como {@code DEFENSES}): es un <b>asalto</b>. Si el mundo se recarga a mitad, los
     * guardias están donde estaban y la marcha se puede volver a decidir en el siguiente latido.
     */
    private record Marcha(BlockPos destino, long fin) {
    }

    /** Marchas en curso por nivel y objetivo. */
    private static final Map<ServerLevel, Map<Integer, Marcha>> MARCHAS = new HashMap<>();

    /**
     * A dónde marcha la milicia de esa aldea ahora mismo (el <b>núcleo de la guarida</b>), o {@code null} si no hay
     * marcha. Lo consulta el goal del guardia en cada tick, así que empezar o acabar la marcha se nota al momento sin
     * tener que tocar a cada aldeano.
     */
    @Nullable
    public static BlockPos marchaDe(ServerLevel level, int objectiveIndex) {
        Map<Integer, Marcha> mapa = MARCHAS.get(level);
        Marcha marcha = mapa != null ? mapa.get(objectiveIndex) : null;
        return marcha != null ? marcha.destino() : null;
    }

    /**
     * Decide si la milicia <b>se forma y marcha</b> contra la guarida de la aldea (lo pidió el jugador: *"con 4
     * guardias y 3 arqueros se forman y marchan a atacar la guarida"*), y cierra la marcha cuando toca.
     * <ul>
     *   <li><b>Marcha</b>: hace falta la formación completa (4 espadachines y 3 arqueros), que la guarida esté
     *       cargada ({@code LairManager.nucleoDe}) y que la aldea esté en paz (nadie se va de asalto con la aldea
     *       bajo ataque o con monstruos dentro).</li>
     *   <li><b>Vuelta</b>: cuando la guarida queda limpia (núcleo destruido) o se acaba el tiempo.</li>
     * </ul>
     */
    private static void comprobarMarcha(ServerLevel level, List<Villager> aldeanos, BlockPos center, int objectiveIndex) {
        Marcha enCurso = MARCHAS.getOrDefault(level, Map.of()).get(objectiveIndex);
        if (enCurso != null) {
            boolean limpia = com.chipoodle.devilrpg.survival.LairManager.estaLimpia(level, objectiveIndex);
            if (limpia || level.getGameTime() > enCurso.fin()) {
                MARCHAS.get(level).remove(objectiveIndex);
                announceNearby(level, center, "La milicia vuelve a la aldea"
                        + (limpia ? ": la guarida ha quedado limpia." : ": se acabó el tiempo del asalto."));
                DevilRpg.LOGGER.info("[Village] Aldea {}: la milicia vuelve ({})",
                        objectiveIndex, limpia ? "guarida limpia" : "se acabo el tiempo");
            }
            return;
        }
        // Con la aldea en peligro NO se va nadie de asalto.
        if (isUnderAttack(level, objectiveIndex) || hayEnemigosDentro(level, center)) {
            return;
        }
        int espadachines = 0;
        int arqueros = 0;
        for (Villager villager : aldeanos) {
            if (!VillagerGuardGoal.esGuardia(villager)) {
                continue;
            }
            if (VillagerGuardGoal.tipoDe(villager) == VillagerGuardGoal.ARQUERO) {
                arqueros++;
            } else {
                espadachines++;
            }
        }
        if (espadachines < MILICIA_ESPADACHINES || arqueros < MILICIA_ARCHEROS) {
            return; // todavía no está la formación
        }
        BlockPos nucleo = com.chipoodle.devilrpg.survival.LairManager.nucleoDe(level, objectiveIndex);
        if (nucleo == null) {
            return; // la guarida de esta aldea todavía no está generada
        }
        MARCHAS.computeIfAbsent(level, l -> new HashMap<>())
                .put(objectiveIndex, new Marcha(nucleo, level.getGameTime() + MARCHA_TICKS));
        announceNearby(level, center, "La milicia se forma: " + espadachines + " espadachines y " + arqueros
                + " arqueros marchan contra la guarida.");
        DevilRpg.LOGGER.info("[Village] Aldea {}: la milicia marcha contra la guarida en {} ({} espadachines, {} arqueros)",
                objectiveIndex, nucleo, espadachines, arqueros);
    }

    /**
     * Devuelve el <b>oficio perdido</b> a los aldeanos que se quedaron <b>sin ninguno</b>.
     * <p>
     * Hace falta porque el cerebro vanilla trae el comportamiento {@code ResetProfession}: si el aldeano no tiene
     * un puesto de trabajo ({@code JOB_SITE}) en el cerebro, su XP es 0 y su nivel es 1, el juego le <b>borra la
     * profesión</b> y lo deja de SIN OFICIO. Nuestros aldeanos se nombran por código, así que en las aldeas ya
     * construidas TODOS acabaron sin oficio: no había granjero (nadie cosechaba ni horneaba, la despensa se
     * quedaba con la remesa inicial y la aldea pasaba hambre con la huerta llena), ni herreros, ni recolector.
     * <p>
     * Se repone <b>la profesión que falta</b> (igual que al repoblar), no una cualquiera, y se le pone 1 de XP para
     * que el juego no se la vuelva a borrar.
     */
    private static void reponerProfesiones(ServerLevel level, List<Villager> aldeanos, int objectiveIndex) {
        List<VillagerProfession> presentes = new ArrayList<>();
        List<Villager> sinOficio = new ArrayList<>();
        for (Villager villager : aldeanos) {
            if (villager.isBaby()) {
                continue;
            }
            VillagerProfession profesion = villager.getVillagerData().getProfession();
            if (profesion == VillagerProfession.NONE) {
                sinOficio.add(villager);
            } else if (!VillageGenerator.esOficioDelPueblo(profesion)) {
                // OFICIO DE FUERA: el pueblo reparte SUS oficios, así que uno que no sea de la lista vuelve al
                // reparto. Hace falta de verdad: en vanilla el <b>barril</b> es el puesto del <b>PESCADOR</b> y el
                // <b>atril</b> el del bibliotecario, así que una cría que crecía y reclamaba uno de esos bloques (el
                // jugador avisó de los barriles de la taberna) se convertía en pescador y el pueblo perdía un puesto.
                // El pescador (y su edificio y su lago) llegarán más adelante, con su propia etapa.
                sinOficio.add(villager);
                villager.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE);
                DevilRpg.LOGGER.info("[Village] Aldea {}: un aldeano habia tomado el oficio de {} (de fuera del"
                        + " pueblo): vuelve al reparto de puestos", objectiveIndex, profesion);
            } else if (!presentes.contains(profesion)) {
                presentes.add(profesion);
            }
        }
        for (Villager villager : sinOficio) {
            int slot = VillageGenerator.slotDeProfesionFaltante(presentes);
            if (slot < 0) {
                return; // ya están todos los oficios cubiertos
            }
            VillagerProfession nueva = VillageGenerator.profesionDeSlot(slot);
            villager.setVillagerData(villager.getVillagerData().setProfession(nueva));
            // Con 1 de XP el comportamiento vanilla `ResetProfession` (que exige XP 0) ya no le borra el oficio.
            villager.setVillagerXp(Math.max(1, villager.getVillagerXp()));
            villager.refreshBrain(level); // que su cerebro active lo de su oficio (granja, comercio...)
            presentes.add(nueva);
            DevilRpg.LOGGER.info("[Village] Aldea {}: aldeano sin oficio recupera el puesto de {}", objectiveIndex, nueva);
        }
    }

    /** Marca a un aldeano como obrero y le pone el goal de reparación. */
    private static void marcarObrero(Villager villager, BlockPos center, int objectiveIndex) {
        boolean yaEra = villager.getPersistentData().getBoolean(BUILDER_TAG);
        villager.getPersistentData().putBoolean(BUILDER_TAG, true);
        // Un GRANJERO que tenga que hacer de obrero (no había más adultos) lleva la reparación POR DEBAJO de su goal
        // de granja (prioridad 5 contra 4): primero la huerta y, cuando no tiene faena, repara. Si no, se pasaría el
        // día reparando y la aldea pasaría hambre.
        boolean esGranjero = villager.getVillagerData().getProfession() == VillagerProfession.FARMER;
        asegurarGoalDeObrero(villager, center, objectiveIndex, esGranjero ? 5 : 3);
        if (!yaEra) {
            DevilRpg.LOGGER.info("[Village] Aldea {}: {} es obrero de la aldea", objectiveIndex, villager.getUUID());
        }
    }

    /**
     * Quita la marca de obrero (y su goal de reparación): el aldeano vuelve a lo suyo.
     * <p>
     * Hace falta porque la marca <b>no se le quitaba a nadie</b>: el granjero que hizo de obrero cuando la aldea se
     * quedó sin adultos se pasaba la vida reparando caminos en vez de cuidar la huerta (el jugador lo vio: quitó un
     * bloque del camino y apareció el granjero a reponerlo, con su etiqueta "Repuso camino").
     */
    private static void desmarcarObrero(Villager villager) {
        if (!villager.getPersistentData().getBoolean(BUILDER_TAG)) {
            return;
        }
        villager.getPersistentData().putBoolean(BUILDER_TAG, false);
        for (WrappedGoal wrapped : List.copyOf(villager.goalSelector.getAvailableGoals())) {
            if (wrapped.getGoal() instanceof VillagerRepairGoal) {
                villager.goalSelector.removeGoal(wrapped.getGoal());
            }
        }
        DevilRpg.LOGGER.info("[Village] {} deja de ser obrero de la aldea y vuelve a su oficio", villager.getUUID());
    }

    private static void asegurarGoalDeObrero(Villager villager, BlockPos center, int objectiveIndex, int prioridad) {
        for (WrappedGoal wrapped : List.copyOf(villager.goalSelector.getAvailableGoals())) {
            if (wrapped.getGoal() instanceof VillagerRepairGoal) {
                if (wrapped.getPriority() == prioridad) {
                    return;
                }
                villager.goalSelector.removeGoal(wrapped.getGoal()); // prioridad vieja: se corrige una vez
                break;
            }
        }
        villager.goalSelector.addGoal(prioridad, new VillagerRepairGoal(villager, center, objectiveIndex));
    }

    /** Le pone al <b>constructor</b> su goal de recolector: recoge lo del pueblo y lo guarda en el almacén. */
    private static void asegurarGoalDeRecolector(Villager villager, BlockPos center, int objectiveIndex) {
        for (WrappedGoal wrapped : villager.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof VillagerCollectGoal) {
                return;
            }
        }
        villager.goalSelector.addGoal(5, new VillagerCollectGoal(villager, center, objectiveIndex));
    }

    /**
     * Le pone al <b>recolector</b> su goal de <b>leñador/reforestador</b> (etapa B), a prioridad <b>6</b>: por debajo
     * de recoger (5), así que primero barre el pueblo y, cuando no hay nada que recoger, se va al monte a talar y
     * replantar. Es el mismo aldeano a propósito: el pueblo no gasta un puesto más (los fijos ya son granjero, los dos
     * herreros, clérigo y recolector, y de los sobrantes sale la milicia).
     */
    private static void asegurarGoalDeLenador(Villager villager, BlockPos center, int objectiveIndex) {
        for (WrappedGoal wrapped : villager.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof VillagerLumberjackGoal) {
                return;
            }
        }
        villager.goalSelector.addGoal(6, new VillagerLumberjackGoal(villager, center, objectiveIndex));
    }

    /**
     * Le pone al <b>ganadero</b> (pastor) su goal de la <b>granja anexa</b> (etapa D): va al corral de fuera de la
     * valla, cría a los animales con la comida del pueblo, recoge lo que sueltan y lo baja al almacén. Prioridad
     * <b>4</b>, la misma que el granjero y los herreros: es su oficio, no un extra.
     */
    private static void asegurarGoalDeGanadero(Villager villager, BlockPos center, int objectiveIndex) {
        for (WrappedGoal wrapped : villager.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof com.chipoodle.devilrpg.entity.goal.VillagerAnimalFarmGoal) {
                return;
            }
        }
        villager.goalSelector.addGoal(4, new com.chipoodle.devilrpg.entity.goal.VillagerAnimalFarmGoal(villager,
                center, objectiveIndex));
    }

    /**
     * Le pone al <b>pescador</b> su goal de <b>pesca</b> (etapa G): va a la pasarela de su pesquera, pesca un pez del
     * lago (uno de verdad: se va del lago) y lo baja crudo a la despensa. Prioridad <b>4</b>, como los demás oficios.
     */
    private static void asegurarGoalDelPescador(Villager villager, BlockPos center, int objectiveIndex) {
        for (WrappedGoal wrapped : villager.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof com.chipoodle.devilrpg.entity.goal.VillagerFisherGoal) {
                return;
            }
        }
        villager.goalSelector.addGoal(com.chipoodle.devilrpg.entity.goal.VillagerFisherGoal.PRIORIDAD,
                new com.chipoodle.devilrpg.entity.goal.VillagerFisherGoal(villager, center, objectiveIndex));
    }

    /**
     * Le pone al <b>cocinero</b> (carnicero) su goal de <b>cocina</b> (etapa E): cocina en el ahumador del kiosco la
     * carne cruda y las patatas del pueblo (crudo = 2 puntos de comida, cocinado = 4). Prioridad <b>4</b>, como los
     * demás oficios.
     */
    private static void asegurarGoalDeCocinero(Villager villager, BlockPos center, int objectiveIndex) {
        for (WrappedGoal wrapped : villager.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof com.chipoodle.devilrpg.entity.goal.VillagerCookGoal) {
                return;
            }
        }
        villager.goalSelector.addGoal(4, new com.chipoodle.devilrpg.entity.goal.VillagerCookGoal(villager,
                center, objectiveIndex));
    }

    /**
     * Le pone a un aldeano del pueblo el goal de los <b>portones</b> del anexo (etapa E): abre el suyo al acercarse y
     * lo cierra al pasar. Prioridad <b>2</b> y <b>sin banderas</b>: no mueve al aldeano, así que va a la vez que su
     * faena (si pidiera {@code MOVE}, se la interrumpiría).
     */
    private static void asegurarGoalDePortones(Villager villager, BlockPos center) {
        for (WrappedGoal wrapped : villager.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof com.chipoodle.devilrpg.entity.goal.VillagerGateGoal) {
                return;
            }
        }
        villager.goalSelector.addGoal(2, new com.chipoodle.devilrpg.entity.goal.VillagerGateGoal(villager, center));
    }

    /** Le pone al <b>herrero</b> su goal de taller (coger material, fabricar en su puesto y dejarlo en el almacén). */
    private static void asegurarGoalDeHerrero(Villager villager, BlockPos center, int objectiveIndex) {
        for (WrappedGoal wrapped : villager.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof com.chipoodle.devilrpg.entity.goal.VillagerSmithGoal) {
                return;
            }
        }
        villager.goalSelector.addGoal(4, new com.chipoodle.devilrpg.entity.goal.VillagerSmithGoal(villager, center,
                objectiveIndex));
    }

    /**
     * Le pone al aldeano su goal de <b>ir a comer a la taberna</b> (etapa F). Va a prioridad
     * {@code VillagerTavernGoal.PRIORIDAD} (por debajo de su oficio y del guardia): el aldeano come cuando de verdad
     * tiene hambre y no tiene faena que hacer.
     */
    private static void asegurarGoalDeLaTaberna(Villager villager, BlockPos center, int objectiveIndex) {
        for (WrappedGoal wrapped : villager.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof com.chipoodle.devilrpg.entity.goal.VillagerTavernGoal) {
                return;
            }
        }
        villager.goalSelector.addGoal(com.chipoodle.devilrpg.entity.goal.VillagerTavernGoal.PRIORIDAD,
                new com.chipoodle.devilrpg.entity.goal.VillagerTavernGoal(villager, center, objectiveIndex));
    }

    /**
     * Le pone al aldeano su goal de <b>recogida por oficio</b>: barre del suelo SUS materiales (ver
     * {@code VillagerPickupGoal.materialesDe}) y los guarda donde le toca. Va a prioridad
     * {@code VillagerPickupGoal.PRIORIDAD} (por encima de la faena de su oficio) con un radio corto: recoge lo que
     * se encuentra yendo a trabajar y, cuando el suelo está limpio, vuelve a lo suyo.
     */
    private static void asegurarGoalDeRecogidaPorOficio(Villager villager, BlockPos center, int objectiveIndex) {
        for (WrappedGoal wrapped : villager.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof com.chipoodle.devilrpg.entity.goal.VillagerPickupGoal) {
                return;
            }
        }
        villager.goalSelector.addGoal(com.chipoodle.devilrpg.entity.goal.VillagerPickupGoal.PRIORIDAD,
                new com.chipoodle.devilrpg.entity.goal.VillagerPickupGoal(villager, center, objectiveIndex));
    }

    /** Le pone al <b>granjero</b> su goal de cultivar/cosechar/fertilizar y llevar el trigo a la despensa. */
    private static void asegurarGoalDeGranjero(Villager villager, BlockPos center, int objectiveIndex) {
        for (WrappedGoal wrapped : villager.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof VillagerFarmGoal) {
                return;
            }
        }
        villager.goalSelector.addGoal(4, new VillagerFarmGoal(villager, center, objectiveIndex));
    }

    /** ¿Esa aldea está siendo atacada ahora mismo? (el obrero no trabaja en plena refriega). */
    public static boolean isVillageUnderAttack(ServerLevel level, int objectiveIndex) {
        return isUnderAttack(level, objectiveIndex);
    }

    /** Centros de las aldeas ya calculados (del plano), para no recorrer el plano en cada intento de spawn. */
    private static final Map<ServerLevel, Map<Integer, BlockPos>> CENTROS = new HashMap<>();

    /**
     * Centro de una aldea: se saca del <b>plano</b> guardado (la caja de lo construido), así que vale también para
     * aldeas que el jugador ya dejó atrás. Se cachea porque esto se consulta en cada intento de spawn.
     */
    @Nullable
    public static BlockPos centroDe(ServerLevel level, int objectiveIndex) {
        VillageSavedData saved = VillageSavedData.get(level);
        Map<Integer, BlockPos> cache = CENTROS.computeIfAbsent(level, l -> new HashMap<>());
        BlockPos cached = cache.get(objectiveIndex);
        if (cached != null) {
            return cached;
        }
        VillageSavedData.Blueprint plano = saved.getBlueprint(objectiveIndex);
        if (plano == null || plano.size() == 0) {
            return null;
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < plano.size(); i++) {
            BlockPos p = plano.posAt(i);
            minX = Math.min(minX, p.getX());
            maxX = Math.max(maxX, p.getX());
            minZ = Math.min(minZ, p.getZ());
            maxZ = Math.max(maxZ, p.getZ());
        }
        BlockPos centro = new BlockPos((minX + maxX) / 2, 0, (minZ + maxZ) / 2);
        // OJO CON LA Y: no vale 0. Los goals del pueblo usan este centro para buscar los CULTIVOS y los cofres
        // (`parcela.offset(dx, 0, dz)` y luego un barrido de ±1 en vertical): con Y=0 buscaban a 60 bloques por
        // debajo del suelo, no veían ni un cultivo y el granjero se pasaba el día dando vueltas sin cosechar
        // ("hay betabel y zanahoria y no los cosecha"), y las distancias salían con un desnivel enorme. La Y que
        // vale es LA COTA DE LA ALDEA (la capa por la que se anda, que es donde están los cultivos y los cofres).
        if (!level.hasChunkAt(centro)) {
            return centro; // chunk sin cargar: se devuelve sin cachear, para no guardar una Y mala
        }
        BlockPos conCota = new BlockPos(centro.getX(), VillageGenerator.cotaDeLaPlaza(level, centro), centro.getZ());
        cache.put(objectiveIndex, conCota);
        return conCota;
    }

    /**
     * Manda a un aldeano a un sitio <b>por el cerebro</b> ({@code WALK_TARGET}/{@code LOOK_TARGET}), que es como se
     * mueven los aldeanos del juego (igual que hace su propio {@code HarvestFarmland}).
     * <p>
     * NO se navega a mano ({@code getNavigation().moveTo}) porque el cerebro del aldeano escribe su propio destino
     * en cada tick (ir a su puesto, a la plaza, a la cama, a pasear) y pisa el nuestro: el aldeano se iba a otro
     * lado a mitad de camino ("primero da vueltas y se va a otro lado antes de recogerlos"). Poniendo el destino en
     * el cerebro, el que camina es él y nadie le quita el rumbo.
     */
    public static void caminarHacia(Villager villager, BlockPos objetivo, float velocidad) {
        villager.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET,
                new net.minecraft.world.entity.ai.memory.WalkTarget(
                        new net.minecraft.world.entity.ai.behavior.BlockPosTracker(objetivo), velocidad, 1));
        villager.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET,
                new net.minecraft.world.entity.ai.behavior.BlockPosTracker(objetivo));
    }

    /** Deja de caminar: se quita el destino del cerebro para que no siga yendo a un sitio ya resuelto. */
    public static void parar(Villager villager) {
        villager.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
        villager.getNavigation().stop();
    }

    /** El plano cambió (migración): se tira el centro cacheado de esa aldea. */
    public static void olvidarCentro(ServerLevel level, int objectiveIndex) {
        Map<Integer, BlockPos> cache = CENTROS.get(level);
        if (cache != null) {
            cache.remove(objectiveIndex);
        }
    }

    /**
     * ¿Ese punto cae dentro de una aldea <b>protegida</b>? Una aldea queda protegida cuando se <b>vence su asedio</b>
     * (el clásico o una horda del mundo) y sigue viva: entonces ninguna criatura hostil puede <b>aparecer</b> entre
     * sus muros (fuera sí, en el campo). Si la aldea cae (todos los aldeanos muertos) deja de estar protegida y queda
     * abandonada.
     */
    public static boolean estaProtegida(ServerLevel level, BlockPos pos) {
        VillageSavedData saved = VillageSavedData.get(level);
        double limite = (double) VillageGenerator.FENCE_RADIUS * VillageGenerator.FENCE_RADIUS;
        for (int i = 0; i <= MAX_OBJECTIVES; i++) {
            if (!saved.isGenerated(i) || saved.isFallen(i) || !saved.isSiegeResolved(i)) {
                continue;
            }
            BlockPos centro = centroDe(level, i);
            if (centro == null) {
                continue;
            }
            double dx = pos.getX() - centro.getX();
            double dz = pos.getZ() - centro.getZ();
            if (dx * dx + dz * dz <= limite && Math.abs(pos.getY() - level.getSeaLevel()) < 96) {
                return true;
            }
        }
        return false;
    }

    /** Tope de objetivos que se miran al comprobar la protección (de sobra para cualquier partida). */
    private static final int MAX_OBJECTIVES = 64;

    /** Marca (en los datos del aldeano) de que el nombre flotante lo puso el mod, y cuándo. */
    public static final String ACTIVIDAD_TAG = "DevilRpgActividad";
    private static final String ACTIVIDAD_HORA_TAG = "DevilRpgActividadTick";
    /** Cuándo fue lo último que <b>hizo</b> el aldeano (un suceso), para dejar verlo unos segundos. */
    private static final String SUCESO_HORA_TAG = "DevilRpgSucesoTick";
    /** Cuánto se queda en la cabeza lo que acaba de hacer (5 s): después vuelve sola la actividad de fondo. */
    private static final int SUCESO_TICKS = 100;

    /**
     * Nombre del bloque <b>en español</b> para las etiquetas y los avisos.
     * <p>
     * OJO: no se usa {@code state.getBlock().getName()}, porque las traducciones las resuelve EL SERVIDOR y ahí el
     * idioma es inglés (por eso la etiqueta enseñaba "Farmer"): los textos del mod van a mano, como el resto.
     */
    public static String nombreEnEspanol(net.minecraft.world.level.block.state.BlockState state) {
        net.minecraft.world.level.block.Block b = state.getBlock();
        if (b == net.minecraft.world.level.block.Blocks.OAK_LOG
                || b == net.minecraft.world.level.block.Blocks.STRIPPED_OAK_LOG) {
            return "tronco de roble";
        }
        if (b == net.minecraft.world.level.block.Blocks.OAK_PLANKS) {
            return "tablones";
        }
        if (b == net.minecraft.world.level.block.Blocks.COBBLESTONE) {
            return "piedra";
        }
        if (b == net.minecraft.world.level.block.Blocks.STONE_BRICKS
                || b == net.minecraft.world.level.block.Blocks.MOSSY_STONE_BRICKS
                || b == net.minecraft.world.level.block.Blocks.CRACKED_STONE_BRICKS) {
            return "piedra labrada";
        }
        if (b == net.minecraft.world.level.block.Blocks.DIRT_PATH) {
            return "camino";
        }
        if (b == net.minecraft.world.level.block.Blocks.OAK_SLAB) {
            return "losa";
        }
        if (b == net.minecraft.world.level.block.Blocks.OAK_STAIRS
                || b == net.minecraft.world.level.block.Blocks.COBBLESTONE_STAIRS) {
            return "escaleras";
        }
        if (b == net.minecraft.world.level.block.Blocks.LANTERN) {
            return "farol";
        }
        if (b == net.minecraft.world.level.block.Blocks.OAK_FENCE) {
            return "valla";
        }
        if (b == net.minecraft.world.level.block.Blocks.GLASS_PANE
                || b == net.minecraft.world.level.block.Blocks.WHITE_STAINED_GLASS_PANE) {
            return "cristal";
        }
        if (b == net.minecraft.world.level.block.Blocks.DIRT
                || b == net.minecraft.world.level.block.Blocks.GRASS_BLOCK) {
            return "tierra";
        }
        if (b == net.minecraft.world.level.block.Blocks.OAK_DOOR) {
            return "puerta";
        }
        if (b == net.minecraft.world.level.block.Blocks.OAK_PRESSURE_PLATE) {
            return "placa";
        }
        if (b == net.minecraft.world.level.block.Blocks.COMPOSTER) {
            return "compostero";
        }
        if (b == net.minecraft.world.level.block.Blocks.FARMLAND) {
            return "tierra de cultivo";
        }
        if (b == net.minecraft.world.level.block.Blocks.WATER) {
            return "agua";
        }
        if (b == net.minecraft.world.level.block.Blocks.BELL) {
            return "campana";
        }
        return "un bloque";
    }

    /**
     * Nombres de pila de los aldeanos. Se le asigna uno <b>al azar pero estable</b>: se saca de su UUID, así que el
     * mismo aldeano se llama siempre igual (y no hay que guardarlo en la partida).
     */
    private static final String[] NOMBRES = {
            "Anselmo", "Bartolo", "Casimiro", "Dionisio", "Eustaquio", "Fabricio", "Gervasio", "Hipolito",
            "Isidoro", "Jacinto", "Leoncio", "Mauricio", "Nicasio", "Onofre", "Prudencio", "Quintin",
            "Remigio", "Saturnino", "Teodoro", "Ubaldo", "Valeriano", "Wenceslao", "Ximeno", "Zacarias",
            "Aurelia", "Bibiana", "Cesarea", "Dorotea", "Eufemia", "Filomena", "Genoveva", "Hortensia",
            "Isabel", "Josefa", "Leocadia", "Manuela", "Nicolasa", "Obdulia", "Petronila", "Ramona",
            "Segismunda", "Tomasa", "Ursula", "Vicenta", "Waldina", "Ximena", "Yolanda", "Zenobia",
    };

    /** Nombre del aldeano: estable y sacado de su UUID (no hace falta guardarlo). */
    public static String nombreDe(Villager villager) {
        long bits = villager.getUUID().getMostSignificantBits() ^ villager.getUUID().getLeastSignificantBits();
        return NOMBRES[Math.floorMod((int) (bits ^ (bits >>> 32)), NOMBRES.length)];
    }

    /**
     * Nombre del <b>oficio</b> del aldeano. Se usan los nombres del mod (en español, como el resto de sus textos) y
     * para cualquier oficio de vanilla que acabe en la aldea se cae al nombre traducido del propio juego.
     */
    public static String nombreDeOficio(Villager villager) {
        // La guardia manda sobre el oficio: un guardia puede ser (por ejemplo) un segundo granjero, pero lo que el
        // jugador tiene que ver en su etiqueta es que está de guardia y con qué.
        if (VillagerGuardGoal.esGuardia(villager)) {
            return VillagerGuardGoal.tipoDe(villager) == VillagerGuardGoal.ARQUERO
                    ? "Guardia arquero" : "Guardia espadachín";
        }
        VillagerProfession profesion = villager.getVillagerData().getProfession();
        if (profesion == VillagerProfession.FARMER) {
            return "Granjero";
        }
        if (profesion == VillagerProfession.WEAPONSMITH) {
            return "Herrero de armas";
        }
        if (profesion == VillagerProfession.TOOLSMITH) {
            return "Herrero de herramientas";
        }
        if (profesion == VillagerProfession.CLERIC) {
            return "Clérigo";
        }
        if (profesion == VillagerProfession.NITWIT) {
            return "Recolector"; // el holgazán es el recolector de la aldea
        }
        if (profesion == VillagerProfession.SHEPHERD) {
            return "Ganadero"; // el pastor de la granja anexa (etapa D)
        }
        if (profesion == VillagerProfession.BUTCHER) {
            return "Cocinero"; // el carnicero de la cocina del kiosco (etapa E)
        }
        if (profesion == VillagerProfession.NONE) {
            return "Sin oficio";
        }
        ResourceLocation clave = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profesion);
        return Component.translatable("entity.minecraft.villager." + (clave != null ? clave.getPath() : "none"))
                .getString();
    }

    /**
     * Pone el <b>texto flotante</b> sobre la cabeza del aldeano: <b>su nombre y su oficio</b> arriba y, debajo, lo que
     * está haciendo (se ve en el juego en tiempo real). Se usa la etiqueta de nombre de vanilla (que admite varias
     * líneas), así que funciona igual en un jugador y en servidor.
     * <p>
     * Se puede apagar en {@code devilrpg-server.toml} → {@code [village] mostrarActividadAldeanos = false}: en ese
     * caso se retira el texto (solo si lo puso el mod, para no borrar un nombre que le hayas puesto tú).
     */
    public static void ponerActividad(Villager villager, @Nullable String texto) {
        boolean activado = com.chipoodle.devilrpg.config.DevilRpgConfig.MOSTRAR_ACTIVIDAD_ALDEANOS;
        boolean esNuestro = villager.getPersistentData().getBoolean(ACTIVIDAD_TAG);
        if (!activado) {
            if (esNuestro) {
                villager.setCustomName(null);
                villager.setCustomNameVisible(false);
                villager.getPersistentData().putBoolean(ACTIVIDAD_TAG, false);
            }
            return;
        }
        if (texto == null) {
            return;
        }
        // Si acaba de HACER algo, lo que hizo se queda unos segundos en la cabeza y no se pisa con el verbo de lo que
        // está haciendo (que es lo de fondo y vuelve solo cuando el suceso caduca).
        if (sucesoReciente(villager)) {
            return;
        }
        etiqueta(villager, texto);
    }

    /**
     * Pone en la cabeza del aldeano <b>lo que acaba de hacer</b> (un suceso): "Guardó 12 de trigo y horneó 2 panes",
     * "Repuso Tronco de roble"... Es más informativo que el verbo de lo que está haciendo, así que se queda
     * {@link #SUCESO_TICKS} (5 s) y después la etiqueta vuelve sola a la actividad.
     * <p>
     * El texto tiene que ser <b>corto</b> (una etiqueta de nombre no se parte sola y se dibuja en una línea): como
     * arriba ya va el nombre y el oficio, aquí no hace falta repetir "el granjero".
     */
    public static void ponerSuceso(Villager villager, @Nullable String texto) {
        if (!com.chipoodle.devilrpg.config.DevilRpgConfig.MOSTRAR_ACTIVIDAD_ALDEANOS || texto == null
                || texto.isBlank()) {
            return;
        }
        villager.getPersistentData().putLong(SUCESO_HORA_TAG, villager.level().getGameTime());
        etiqueta(villager, texto);
    }

    /** ¿Ese aldeano acaba de hacer algo? (mientras sí, no se le pisa la etiqueta con la actividad de fondo). */
    private static boolean sucesoReciente(Villager villager) {
        long t = villager.getPersistentData().getLong(SUCESO_HORA_TAG);
        return t != 0L && villager.level().getGameTime() - t < SUCESO_TICKS;
    }

    /**
     * Escribe la etiqueta del aldeano: <b>NOMBRE y OFICIO</b> arriba y, debajo, el texto que le pasen (lo que hace o
     * lo que acaba de hacer). La profesión se lee en cada refresco, así que si le cambia el oficio (o se le repone,
     * ver {@code reponerProfesiones}) la etiqueta se actualiza sola.
     */
    private static void etiqueta(Villager villager, String texto) {
        String etiqueta = nombreDe(villager) + " (" + nombreDeOficio(villager) + ")\n" + texto;
        villager.getPersistentData().putLong(ACTIVIDAD_HORA_TAG, villager.level().getGameTime());
        String actual = villager.getCustomName() == null ? "" : villager.getCustomName().getString();
        if (!etiqueta.equals(actual)) {
            villager.setCustomName(Component.literal(etiqueta));
            villager.setCustomNameVisible(true);
            villager.getPersistentData().putBoolean(ACTIVIDAD_TAG, true);
        }
    }

    /** ¿Ese aldeano ha dicho lo que hace hace poco? (si no, se le pone el texto genérico). */
    private static boolean actividadReciente(Villager villager) {
        long t = villager.getPersistentData().getLong(ACTIVIDAD_HORA_TAG);
        return t != 0L && villager.level().getGameTime() - t < 60L;
    }

    /**
     * Refresco <b>genérico</b> de las etiquetas: si un aldeano no ha dicho nada hace un segundo, se le pone lo que
     * está haciendo según su cerebro vanilla (trabajando, de charla, paseando, durmiendo) para que nunca se quede sin
     * texto ni con uno viejo. Lo llama el tick del jugador cada segundo.
     */
    public static void refrescarEtiquetas(ServerLevel level, ServerPlayer player) {
        if (!com.chipoodle.devilrpg.config.DevilRpgConfig.MOSTRAR_ACTIVIDAD_ALDEANOS) {
            return;
        }
        for (Villager villager : level.getEntitiesOfClass(Villager.class,
                new AABB(player.blockPosition()).inflate(64.0D))) {
            if (actividadReciente(villager)) {
                continue;
            }
            if (villager.isSleeping()) {
                ponerActividad(villager, "Durmiendo");
            } else if (VillagerGuardGoal.esGuardia(villager)) {
                // Un guardia de SERVICIO no duerme aunque el cerebro esté en la franja de descanso (su goal no se corta
                // a propósito): si le falta equipo va al almacén y, si no, está de ronda. La etiqueta genérica lo
                // llamaba "Durmiendo" y el jugador veía a la guardia plantada en el almacén con la etiqueta de dormida
                // (medido en su partida: la única guardia, sin espada, horas en el almacén y "Durmiendo").
                ponerActividad(villager, "De guardia");
            } else if (estaDescansando(villager)) {
                // OJO: `estaDescansando` es la FRANJA de descanso del cerebro (toda la noche), NO que esté en la cama.
                // Antes se le ponía "Durmiendo" a secas y el jugador veía al granjero **de pie en la calle**
                // "durmiendo" (lo preguntó: "¿por qué el granjero está durmiendo parado?"). Ahora la etiqueta dice lo
                // que pasa de verdad, y de paso avisa de lo que falta: si no tiene cama, lo dice.
                ponerActividad(villager, tieneCama(villager) ? "Yendo a la cama" : "Sin cama");
            } else if (villager.isBaby()) {
                ponerActividad(villager, "Jugando");
            } else if (villager.getBrain().isActive(net.minecraft.world.entity.schedule.Activity.WORK)) {
                ponerActividad(villager, "Trabajando");
            } else if (villager.getBrain().isActive(net.minecraft.world.entity.schedule.Activity.MEET)) {
                ponerActividad(villager, "De charla");
            } else {
                ponerActividad(villager, "Paseando");
            }
        }
    }

    /**
     * ¿Ese aldeano tiene <b>cama propia</b>? Es la memoria {@code HOME} del cerebro (donde vanilla guarda la cama que
     * ha reclamado). Sirve para que la etiqueta no mienta: un aldeano en la franja de descanso que <b>no</b> tiene
     * cama está despierto y dando vueltas, y el pueblo debería saberlo (lleva su propia etiqueta: "Sin cama").
     */
    public static boolean tieneCama(Villager villager) {
        return villager.getBrain().hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.HOME);
    }

    /**
     * ¿El aldeano está en su <b>hora de descanso</b> (yendo a la cama o dentro de ella)? Mientras descansa, NINGÚN
     * goal del pueblo debe estar activo: si no, el aldeano se queda "andando" en la cama (sus goals tienen el flag
     * MOVE y ganan al cerebro vanilla que quiere dormir). Se mira el <b>cerebro</b> del aldeano (la actividad REST es
     * la que usa vanilla para irse a dormir) y también {@code isSleeping} por si ya está dentro.
     */
    public static boolean estaDescansando(Villager villager) {
        if (villager.isSleeping()) {
            return true;
        }
        return villager.getBrain().isActive(net.minecraft.world.entity.schedule.Activity.REST);
    }

    /**
     * El hueco del plano que hay que reponer más cercano al obrero, o {@code null} si no hay nada roto a su
     * alcance. {@code excluir} trae las posiciones comprimidas que ese obrero ya descartó por inalcanzables, y
     * se saltan los huecos que otro obrero tenga reservados.
     */
    @Nullable
    public static BlockPos findRepairTarget(ServerLevel level, int objectiveIndex, BlockPos from, Set<Long> excluir, UUID builder) {
        VillageSavedData.Blueprint plano = VillageSavedData.get(level).getBlueprint(objectiveIndex);
        if (plano == null) {
            return null;
        }
        BlockPos mejor = null;
        double mejorDist = REPAIR_SEARCH_RADIUS * REPAIR_SEARCH_RADIUS;
        for (int i = 0; i < plano.size(); i++) {
            BlockPos pos = plano.posAt(i);
            long comprimida = pos.asLong();
            if (excluir.contains(comprimida) || reclamadoPorOtro(level, comprimida, builder)) {
                continue;
            }
            int dy = pos.getY() - from.getY();
            if (dy > REPAIR_MAX_UP || dy < -REPAIR_MAX_DOWN) {
                continue;
            }
            // La DISTANCIA antes de mirar el bloque: el plano de la aldea (radio 62) tiene miles de posiciones y
            // preguntar el bloque de todas ellas en cada decisión era el trabajo más caro del obrero.
            double dist = pos.distSqr(from);
            if (dist >= mejorDist) {
                continue;
            }
            if (!necesitaReparacion(level.getBlockState(pos), plano.stateAt(i))) {
                continue;
            }
            mejorDist = dist;
            mejor = pos;
        }
        return mejor;
    }

    /**
     * ¿Hay que reponer algo en esa posición? Se repone si está en <b>aire</b> (lo típico: lo rompió un asedio) o si
     * el bloque que hay es el resultado de un destrozo concreto: la <b>tierra de cultivo se convierte en tierra</b>
     * cuando alguien salta encima, así que si el plano dice tierra de cultivo (o la acequia) y ahora hay tierra o
     * hierba, se vuelve a poner. Cualquier otra cosa (lo que haya puesto el jugador) no se toca.
     */
    private static boolean necesitaReparacion(BlockState actual, BlockState esperado) {
        if (actual.isAir()) {
            return true;
        }
        if (actual.equals(esperado)) {
            return false;
        }
        // HIELO (o nieve) donde el plano dice AGUA: en los biomas helados la acequia se congela, y el hielo no
        // hidrata la tierra de cultivo (FarmBlock.isNearWater usa el fluido), así que los cultivos se secaban y la
        // aldea pasaba hambre. Se repone el agua (y con la losa que la cubre ya no vuelve a congelarse).
        boolean eraAgua = esperado.is(Blocks.WATER);
        boolean congelada = actual.is(Blocks.ICE) || actual.is(Blocks.PACKED_ICE) || actual.is(Blocks.FROSTED_ICE)
                || actual.is(Blocks.SNOW_BLOCK) || actual.is(Blocks.SNOW);
        if (eraAgua && congelada) {
            return true;
        }
        boolean eraHuerta = esperado.is(Blocks.FARMLAND) || esperado.is(Blocks.WATER);
        boolean pisoteada = actual.is(Blocks.DIRT) || actual.is(Blocks.GRASS_BLOCK) || actual.is(Blocks.COARSE_DIRT)
                || actual.is(Blocks.PODZOL) || actual.is(Blocks.ROOTED_DIRT);
        return eraHuerta && pisoteada;
    }

    /** Lo mismo, mirando el plano: ¿ese hueco hay que reponerlo? (lo consulta el obrero en cada tick). */
    public static boolean necesitaReparacion(ServerLevel level, int objectiveIndex, BlockPos pos) {
        BlockState esperado = blueprintState(level, objectiveIndex, pos);
        return esperado != null && necesitaReparacion(level.getBlockState(pos), esperado);
    }

    /**
     * Reserva un hueco para un obrero. Devuelve {@code false} si otro lo tiene cogido todavía (así, con varios
     * obreros, cada uno va a un sitio distinto en vez de amontonarse en el mismo agujero).
     */
    public static boolean reclamarHueco(ServerLevel level, BlockPos pos, UUID builder) {
        Map<Long, Reclamo> mapa = CLAIMS.computeIfAbsent(level, l -> new HashMap<>());
        long ahora = level.getGameTime();
        if (reclamadoPorOtro(level, pos.asLong(), builder)) {
            return false;
        }
        if (mapa.size() > 512) {
            mapa.values().removeIf(reclamo -> ahora - reclamo.tick() >= CLAIM_TIMEOUT_TICKS);
        }
        mapa.put(pos.asLong(), new Reclamo(builder, ahora));
        return true;
    }

    /** Suelta la reserva de un hueco (al colocarlo, al abandonarlo o al parar el goal). */
    public static void liberarHueco(ServerLevel level, BlockPos pos) {
        Map<Long, Reclamo> mapa = CLAIMS.get(level);
        if (mapa != null) {
            mapa.remove(pos.asLong());
        }
    }

    private static boolean reclamadoPorOtro(ServerLevel level, long posComprimida, UUID builder) {
        Map<Long, Reclamo> mapa = CLAIMS.get(level);
        if (mapa == null) {
            return false;
        }
        Reclamo reclamo = mapa.get(posComprimida);
        return reclamo != null && !reclamo.builder().equals(builder)
                && level.getGameTime() - reclamo.tick() < CLAIM_TIMEOUT_TICKS;
    }

    /** El bloque que debería haber en esa posición según el plano de la aldea ({@code null} si no está en él). */
    @Nullable
    public static BlockState blueprintState(ServerLevel level, int objectiveIndex, BlockPos pos) {
        VillageSavedData.Blueprint plano = VillageSavedData.get(level).getBlueprint(objectiveIndex);
        if (plano == null) {
            return null;
        }
        long comprimida = pos.asLong();
        for (int i = 0; i < plano.size(); i++) {
            if (plano.positions()[i] == comprimida) {
                return plano.stateAt(i);
            }
        }
        return null;
    }

    /**
     * Reparte <b>pan de verdad sacado de la despensa</b>: a un aldeano que todavía no puede criar (en vanilla hacen
     * falta 12 puntos de comida en total) se le deja un pan en el suelo, que <b>recoge él mismo</b>. Así se ve la
     * comida, se la llevan andando y con eso nacen crías. El pan <b>sale del barril</b> (si no hay pan horneado, no
     * se reparte nada) y solo se da uno por latido, para que la aldea no se quede sin reservas. A los viejos no se
     * les da: ya no crían.
     * <p>
     * <b>La cría va ligada a las CAMAS LIBRES</b> (etapa E, lo pidió el jugador): si el pueblo no tiene una cama de
     * sobra, no se reparte pan para criar. Es la regla de vanilla (una cama por aldeano) puesta donde de verdad
     * decide algo: en la comida. Antes se repartía pan siempre y el pueblo crecía hasta que ya no cabía nadie.
     */
    private static void feedVillagers(ServerLevel level, VillageSavedData saved, int objectiveIndex, BlockPos center,
                                     List<Villager> aldeanos) {
        if (!hayCamaLibre(level, center, aldeanos)) {
            return; // sin cama libre no se cría: no se gasta el pan
        }
        for (Villager villager : aldeanos) {
            if (villager.isBaby() || villager.canBreed()) {
                continue; // las crías no comen de la despensa y el que ya puede criar no lo necesita
            }
            if (ageOf(level, villager) >= VILLAGER_OLD_AGE_TICKS) {
                continue; // viejo: ya no cría, no se le da pan
            }
            // Un pan del barril (barril -> suelo -> lo recoge él): lo que come la aldea es lo que se cultivó y se
            // horneó. Uno por latido, y si la despensa no tiene pan no se reparte nada.
            if (darPanDeLaDespensa(level, center, villager)) {
                return;
            }
        }
    }

    /** Reparte un pan del barril al aldeano indicado (devuelve false si la despensa no tiene pan). */
    private static boolean darPanDeLaDespensa(ServerLevel level, BlockPos center, Villager villager) {
        if (VillagePantry.sacar(VillagePantry.despensa(level, center), s -> s.is(Items.BREAD), 1) == 0) {
            return false;
        }
        ItemEntity pan = new ItemEntity(level, villager.getX(), villager.getY() + 0.4D, villager.getZ(),
                new ItemStack(Items.BREAD));
        pan.setDefaultPickUpDelay();
        level.addFreshEntity(pan);
        return true;
    }

    /**
     * <b>Reparte las raciones</b> de este minuto: <b>una ración = un punto de comida</b> (lo mismo que comía la
     * aldea antes, ahora repartido boca por boca) por cada aldeano que lleve sin comer {@link #EAT_INTERVAL_TICKS} o
     * más, y <b>primero al que hace más tiempo que no come</b> (si la comida no llega para todos, el hambre se
     * reparte en vez de cebar siempre a los mismos). Cada ración sale <b>de verdad</b> de la despensa.
     * <p>
     * OJO con cómo se saca: se pide el total <b>por valor y de una sola vez</b>. Sacando un punto por boca, uno a
     * uno, cada aldeano se llevaría una <b>hogaza entera</b> (4 puntos: {@code sacarComida} redondea a piezas
     * completas) y el pueblo comería cuatro veces más de lo que le toca. {@code sacarComida} devuelve los puntos que
     * de verdad había, así que se marca como comidos a los que les tocó (los que llevaban más tiempo sin comer).
     *
     * @return cuántas raciones se han repartido (0 = el pueblo no tiene nada que dar)
     */
    private static int repartirRaciones(ServerLevel level, BlockPos center, List<Villager> aldeanos) {
        if (level.getGameTime() % EAT_INTERVAL_TICKS != 0L) {
            return 0; // las raciones se reparten en el latido del minuto
        }
        Container despensa = VillagePantry.despensa(level, center);
        if (despensa == null || aldeanos.isEmpty()) {
            return 0;
        }
        // El hambre de cada uno, leída UNA vez por aldeano (y de paso se le estrena la marca al que llega nuevo).
        Map<Villager, Long> ultima = new HashMap<>();
        for (Villager villager : aldeanos) {
            ultima.put(villager, ultimaComida(level, villager));
        }
        List<Villager> bocas = new ArrayList<>();
        for (Villager villager : aldeanos) {
            if (!villager.isBaby() && level.getGameTime() - ultima.get(villager) >= EAT_INTERVAL_TICKS) {
                bocas.add(villager); // las crías maman de la aldea: no gastan ración
            }
        }
        if (bocas.isEmpty()) {
            return 0;
        }
        bocas.sort(Comparator.comparingLong(v -> ultima.get(v)));
        int raciones = Math.min(VillagePantry.sacarComida(despensa, bocas.size()), bocas.size());
        for (int i = 0; i < raciones; i++) {
            marcarComida(level, bocas.get(i));
        }
        return raciones;
    }

    /** El <b>hambre de cada aldeano</b>: debilidad y lentitud a las {@link #HAMBRE_PACIENTE_TICKS} raciones sin comer,
     *  y muerte por hambre a los {@link #STARVATION_DEATH_TICKS}. Antes moría "uno al azar de la aldea". */
    private static void pasarHambre(ServerLevel level, VillageSavedData saved, int objectiveIndex,
                                    List<Villager> aldeanos) {
        boolean algunaBocaSinComer = false;
        for (Villager villager : aldeanos) {
            if (villager.isBaby()) {
                continue;
            }
            long sinComer = level.getGameTime() - ultimaComida(level, villager);
            if (sinComer < HAMBRE_PACIENTE_TICKS) {
                continue;
            }
            algunaBocaSinComer = true;
            if (!villager.hasEffect(MobEffects.WEAKNESS)) {
                villager.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 60, 0, false, false));
            }
            if (!villager.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
                villager.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 60, 0, false, false));
            }
            if (sinComer >= STARVATION_DEATH_TICKS) {
                DevilRpg.LOGGER.info("[Village] Un aldeano de la aldea {} ha muerto de hambre ({} min sin comer)",
                        objectiveIndex, sinComer / (60L * 20L));
                villager.hurt(level.damageSources().generic(), Float.MAX_VALUE);
            }
        }
        // El aviso de aldea (una vez) y el de recuperación: es lo que se ve en el chat y en el log.
        if (algunaBocaSinComer) {
            if (saved.getStarvingSince(objectiveIndex) == 0L) {
                saved.setStarvingSince(objectiveIndex, level.getGameTime());
                DevilRpg.LOGGER.info("[Village] La aldea {} pasa hambre: hay bocas sin su racion", objectiveIndex);
                BlockPos centro = centroDe(level, objectiveIndex); // el aviso es para el jugador que esté cerca
                if (centro != null) {
                    announceNearby(level, centro, "La aldea pasa hambre: la despensa esta vacia.");
                }
            }
        } else if (saved.getStarvingSince(objectiveIndex) != 0L) {
            saved.setStarvingSince(objectiveIndex, 0L);
            DevilRpg.LOGGER.info("[Village] La aldea {} vuelve a tener comida", objectiveIndex);
        }
    }

    /**
     * Cuándo comió por última vez ese aldeano (gameTime). La primera vez que se le ve, come ahora.
     * <p>
     * Público porque lo usa el goal de la <b>taberna</b> ({@code VillagerTavernGoal}): el aldeano que tiene hambre
     * (lleva {@link #EAT_INTERVAL_TICKS} o más sin comer) se va a la taberna a comer, y allí se le marca con
     * {@link #marcarComida} y se le quita la ración abstracta del minuto (nadie come dos veces).
     */
    public static long ultimaComida(ServerLevel level, Villager villager) {
        CompoundTag datos = villager.getPersistentData();
        if (!datos.contains(COMIDA_TAG)) {
            datos.putLong(COMIDA_TAG, level.getGameTime());
            return level.getGameTime();
        }
        return datos.getLong(COMIDA_TAG);
    }

    /** ¿Ese aldeano tiene <b>hambre</b>? (lleva una ración sin comer: el mismo criterio que las raciones del minuto) */
    public static boolean tieneHambre(ServerLevel level, Villager villager) {
        return level.getGameTime() - ultimaComida(level, villager) >= EAT_INTERVAL_TICKS;
    }

    /** Marca que ese aldeano acaba de comer (así el reparto abstracto del minuto no le da otra ración). */
    public static void marcarComida(ServerLevel level, Villager villager) {
        villager.getPersistentData().putLong(COMIDA_TAG, level.getGameTime());
    }

    /** Camas del pueblo (puntos de interés `HOME` alrededor de la plaza): es el tope real de población. */
    private static long contarCamas(ServerLevel level, BlockPos center) {
        return level.getPoiManager().getCountInRange(h -> h.is(PoiTypes.HOME), center,
                VillageGenerator.FENCE_RADIUS + 6, PoiManager.Occupancy.ANY);
    }

    /** ¿Hay una cama de sobra para una cría? (los aldeanos de la lista incluyen a las crías). */
    private static boolean hayCamaLibre(ServerLevel level, BlockPos center, List<Villager> aldeanos) {
        return contarCamas(level, center) > aldeanos.size();
    }

    /**
     * <b>Edad</b> de un aldeano en ticks de juego: la primera vez que se le ve se le apunta la fecha de
     * nacimiento en sus datos persistentes (viaja con él en el guardado). {@code 0} si acaba de conocerse.
     */
    private static long ageOf(ServerLevel level, Villager villager) {
        CompoundTag datos = villager.getPersistentData();
        if (!datos.contains(BORN_TAG)) {
            datos.putLong(BORN_TAG, level.getGameTime());
            return 0L;
        }
        return level.getGameTime() - datos.getLong(BORN_TAG);
    }

    /**
     * <b>Envejecimiento visible</b>: a partir de {@link #VILLAGER_OLD_AGE_TICKS} (2 días de juego) el aldeano es
     * viejo: va más lento y más débil (y ya no se le reparte pan, así que no cría). Al llegar a
     * {@link #VILLAGER_LIFESPAN_TICKS} (3 días) muere de viejo con la animación y el sonido de muerte normales,
     * no con un borrado seco.
     */
    private static void ageVillagers(ServerLevel level, List<Villager> aldeanos) {
        for (Villager villager : aldeanos) {
            long edad = ageOf(level, villager);
            if (edad >= VILLAGER_LIFESPAN_TICKS) {
                DevilRpg.LOGGER.info("[Village] Un aldeano murio de viejo a los {} dias de juego",
                        edad / 24000L);
                villager.hurt(level.damageSources().generic(), Float.MAX_VALUE);
            } else if (edad >= VILLAGER_OLD_AGE_TICKS) {
                if (!villager.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
                    villager.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 60, 0, false, false));
                }
                if (!villager.hasEffect(MobEffects.WEAKNESS)) {
                    villager.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 60, 0, false, false));
                }
            }
        }
    }

    /**
     * La aldea cae: se queda sin aldeanos y eso es <b>definitivo</b> (no vuelve a ser objetivo de hordas ni se
     * repuebla). Se deja en <b>ruinas</b> ({@link VillageGenerator#ruin}) para que el jugador vea lo que pasó
     * cuando vuelva. Idempotente: si ya estaba caída no hace nada.
     */
    private static void fallVillage(ServerLevel level, VillageSavedData saved, int objectiveIndex, BlockPos center) {
        if (saved.isFallen(objectiveIndex)) {
            return;
        }
        saved.markFallen(objectiveIndex);
        // La aldea cae: se apaga el sello (queda abandonada y las criaturas vuelven a poder aparecer entre las ruinas).
        int cota = VillageGenerator.cotaDeLaPlaza(level, center);
        BlockPos sello = new BlockPos(center.getX(), cota + 5, center.getZ());
        if (level.getBlockState(sello).is(Blocks.BEACON)) {
            level.setBlockAndUpdate(sello, Blocks.AIR.defaultBlockState());
        }
        VillageGenerator.ruin(level, center, objectiveIndex);
        DevilRpg.LOGGER.info("[Village] La aldea {} ha CAÍDO y queda en ruinas", objectiveIndex);
    }

    /**
     * <b>Sello místico de la aldea</b>: al vencer su asedio, la aldea queda protegida del poder de la oscuridad
     * (ninguna criatura hostil puede <b>aparecer</b> entre sus muros; fuera, en el campo, sí) y se enciende un
     * <b>faro</b> en lo alto del kiosco como señal. El sello dura mientras la aldea viva: si cae (todos los aldeanos
     * muertos) queda abandonada y el poder se apaga.
     */
    private static void marcarSelloMistico(ServerLevel level, BlockPos center, ServerPlayer player) {
        int cota = VillageGenerator.cotaDeLaPlaza(level, center);
        BlockPos sello = new BlockPos(center.getX(), cota + 5, center.getZ());
        if (!level.getBlockState(sello).is(Blocks.BEACON)) {
            level.setBlockAndUpdate(sello, Blocks.BEACON.defaultBlockState());
        }
        player.displayClientMessage(Component.literal(
                "La campana tañe una sola vez... y un zumbido antiguo recorre el empedrado: "
                        + "el poder místico sella la aldea. Ninguna criatura de la oscuridad podrá alzarse entre sus muros."), false);
        DevilRpg.LOGGER.info("[Village] Aldea en {}: sello místico activo (no aparecerán enemigos dentro)", center);
    }

    /**
     * Efectos visuales de las aldeas, cada pocos ticks: el <b>haz de luz</b> del sello místico (una columna de
     * partículas sobre el faro del kiosco) y las <b>partículas oscuras</b> de los enemigos que entran en una aldea
     * asediada (así se ve la intrusión desde lejos).
     */
    public static void efectosDeAldeas(ServerLevel level, ServerPlayer player) {
        VillageSavedData saved = VillageSavedData.get(level);
        for (int i = 0; i <= MAX_OBJECTIVES; i++) {
            BlockPos centro = centroDe(level, i);
            if (centro == null || !saved.isGenerated(i)) {
                continue;
            }
            double distSqr = player.blockPosition().distSqr(centro);
            boolean protegida = saved.isSiegeResolved(i) && !saved.isFallen(i);
            // HAZ DE LUZ: columna de partículas brillantes sobre el faro del kiosco (se ve a lo lejos).
            if (protegida && distSqr < 128.0D * 128.0D) {
                int cota = VillageGenerator.cotaDeLaPlaza(level, centro);
                for (int h = 0; h < 14; h++) {
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                            centro.getX() + 0.5D, cota + 6.0D + h * 0.9D, centro.getZ() + 0.5D,
                            1, 0.08D, 0.0D, 0.08D, 0.0D);
                }
            }
            // INTRUSIÓN: chispas oscuras sobre los enemigos que están dentro del perímetro de la aldea. Se marcan
            // los que de verdad están DENTRO (recinto + altura): un bicho en una cueva bajo la plaza no es un
            // invasor, y marcarlo hacía creer al jugador que la aldea estaba tomada.
            if (distSqr < 160.0D * 160.0D) {
                List<net.minecraft.world.entity.Mob> bichos = level.getEntitiesOfClass(
                        net.minecraft.world.entity.Mob.class,
                        new AABB(centro).inflate(VillageGenerator.FENCE_RADIUS + 8.0D),
                        mob -> mob.getType().getCategory() == net.minecraft.world.entity.MobCategory.MONSTER);
                if (!bichos.isEmpty()) {
                    int cotaBichos = VillageGenerator.cotaDeLaPlaza(level, centro);
                    for (net.minecraft.world.entity.Mob mob : bichos) {
                        if (dentroDelRecinto(cotaBichos, centro, mob, VillageGenerator.FENCE_RADIUS)) {
                            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SCULK_SOUL,
                                    mob.getX(), mob.getY() + 1.9D, mob.getZ(), 2, 0.25D, 0.15D, 0.25D, 0.01D);
                        }
                    }
                }
            }
        }
    }

    /** Manda un mensaje a los jugadores que estén cerca de la aldea (distancia HORIZONTAL: la aldea es un recinto). */
    private static void announceNearby(ServerLevel level, BlockPos center, String message) {
        for (ServerPlayer p : level.players()) {
            double dx = p.getX() - center.getX();
            double dz = p.getZ() - center.getZ();
            if (dx * dx + dz * dz <= SIEGE_WARN_RADIUS * SIEGE_WARN_RADIUS) {
                p.displayClientMessage(Component.literal(message), false);
            }
        }
    }

    /** Punto de inicio del jugador (ancla; o su spawn si aún no hay ancla). */
    private static Vec3 anchorOf(Player player) {
        PlayerAuxiliaryCapabilityInterface aux = IGenericCapability.getUnwrappedPlayerCapability(player, PlayerAuxiliaryCapability.INSTANCE);
        if (aux == null) {
            return null;
        }
        Vec3 anchor = aux.getAnchorPoint();
        return anchor != null ? anchor : aux.getSpawnPoint();
    }

    /** Datos de un asedio dirigido por el mundo (horda que va a por una aldea por su cuenta). */
    private static final class WorldSiege {
        final int objectiveIndex;
        final BlockPos center;
        final List<UUID> wave;
        /**
         * En los asedios del mundo esta lista guarda solo los atacantes <b>vivos</b>: cada muerte se descuenta
         * ({@link #onWorldSiegeAttackerKilled}), así que quedar vacía significa "los mataron a todos". Eso es lo
         * que separa una defensa de verdad de un asedio que se resolvió porque el jugador se alejó.
         */
        final Set<UUID> defenders = new HashSet<>();

        WorldSiege(int objectiveIndex, BlockPos center, List<UUID> wave) {
            this.objectiveIndex = objectiveIndex;
            this.center = center;
            this.wave = wave;
        }
    }

    /** Datos de un asedio en curso. */
    private static final class VillageDefense {
        final int objectiveIndex;
        final UUID playerUUID;
        final BlockPos center;
        final List<UUID> wave = new ArrayList<>();
        long tickTicks;
        boolean waveSpawned;
        /** Último recuento de atacantes vivos, para cantar cada baja (y no repetir el mensaje). */
        int ultimosVivos = -1;

        VillageDefense(int objectiveIndex, UUID playerUUID, BlockPos center) {
            this.objectiveIndex = objectiveIndex;
            this.playerUUID = playerUUID;
            this.center = center;
        }
    }
}
