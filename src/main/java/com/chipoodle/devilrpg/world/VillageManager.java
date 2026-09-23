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
import com.chipoodle.devilrpg.survival.VillageNames;
import com.chipoodle.devilrpg.util.MissionRewards;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

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
    /**
     * Radio (horizontal, desde el CENTRO de la aldea) dentro del cual se considera que el jugador <b>está en la
     * aldea</b> y por tanto el asedio <b>corre</b>. Fuera de ahí —o con el jugador desconectado— el reloj se
     * <b>PAUSA</b>.
     * <p>
     * Lo pidió el jugador, con una aldea entera perdida: <i>"me alejé de la aldea unos cientos de cubos, volando y
     * regresé antes de que nocheciera y cuando regresé ya estaba abandonada"</i>. El fallo era que {@code tickTicks}
     * corría <b>siempre</b>: al irse con los asediadores dentro de los muros, el tiempo expiraba durante su ausencia
     * y, <b>en el mismo tick de volver</b> —cuando los zombis se cargan otra vez y vuelven a contar como "dentro"—,
     * la aldea caía: aldeanos muertos, casas derruidas y telarañas. Un asedio es una pelea: sin el jugador delante
     * no puede perderse, y al volver se le da el tiempo entero otra vez (ver {@code tick}).
     */
    private static final double RADIO_ASEDIO_CON_JUGADOR = 128.0D;
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

    /**
     * Aldeanos que tiene una aldea <b>sana</b>: <b>una por puesto</b> del pueblo ({@link VillageGenerator#puestosDelPueblo}),
     * que es lo que pone el generador. Es el tope de la "salud" para la presión de los asedios.
     * <p>
     * Estaba clavado en <b>5</b> (los puestos de la etapa A) y con once puestos eso dejaba "sana" a una aldea a la
     * que le faltaba más de la mitad de la gente: se pide al pueblo, no se escribe a mano (I5).
     */
    public static final int VILLAGERS_FOR_FULL_HEALTH = VillageGenerator.puestosDelPueblo();
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

    /**
     * <b>Cuánto aguanta el sello a un intruso antes de rechazarlo</b> (2 min). En ese rato el que defiende es el
     * <b>pueblo</b> (la milicia): el sello solo actúa si el bicho <b>sigue dentro</b>, que es la red de seguridad que
     * impide que uno solo deje la aldea congelada para siempre (ver {@code expulsarHostilesDeLaAldea}). Es lo que
     * hace que la defensa se <b>vea</b>: antes el sello lo echaba en el primer latido (10 s) y un agresivo que entraba
     * andando desaparecía sin pelea.
     */
    private static final long SELLO_ANTES_DE_EXPULSAR_TICKS = 2L * 60L * 20L;
    /**
     * Desde cuándo lleva <b>cada intruso dentro</b> de una aldea protegida (el {@code gameTime} de la primera vez que
     * se le vio dentro). Se olvida en cuanto sale (o muere): el reloj del sello no corre si el bicho sale y vuelve.
     * <p>
     * La clave lleva <b>la aldea</b>: el latido de una aldea solo puede limpiar (y mirar) lo <b>suyo</b>; si no, el
     * latido de la aldea de al lado borraría el reloj de un intruso de ésta y éste no se rechazaría nunca.
     */
    private record Intruso(int aldea, UUID uuid) {
    }

    private static final Map<Intruso, Long> INTRUSOS_DENTRO = new java.util.concurrent.ConcurrentHashMap<>();

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
    /** Marca (en los datos persistentes del aldeano) de <b>cuántos enemigos ha matado</b> ese guardia (I62). */
    public static final String GUARD_KILLS_TAG = "DevilRpgGuardiaMatanzas";
    /**
     * Matanzas que necesita un guardia para llegar <b>al tope</b> de la milicia. La progresión es <b>lineal y
     * gradual</b> a propósito (lo pidió el jugador: *"la progresión es gradual, no tiene que ser tan rápida"*): con
     * 24, un guardia que se pelee con una horda por noche sube un escalón cada pocas noches, no de golpe.
     */
    private static final int GUARD_MATANZAS_PARA_EL_TOPE = 24;
    /** Cada cuántas matanzas se cuenta un <b>nivel</b> (el que se ve en su etiqueta y se avisa en el log). */
    private static final int GUARD_MATANZAS_POR_NIVEL = 3;
    /**
     * <b>Ticks de entrenamiento en la barraca que valen como UNA matanza</b> (5 minutos). Entrenar también fortalece
     * (lo pidió el jugador: *"o entrenando en la sala de entrenamiento de sus barracas"*, y le pareció bien que diera
     * "un progreso lento de fuerza"), pero <b>despacio</b>: 24 matanzas para el tope = <b>dos horas</b> de diana, así
     * que la milicia se hace de verdad en las peleas.
     */
    private static final int GUARD_TICKS_DE_ENTRENO_POR_MATANZA = 20 * 60 * 5;
    /** Marca (en los datos persistentes del aldeano) de los ticks que ese guardia lleva entrenando. */
    public static final String GUARD_TRAINING_TAG = "DevilRpgGuardiaEntreno";
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
     *         <li>La <b>fachada da al oeste</b> (a la plaza), con <b>porche</b>, toldo y <b>camino</b> desde la
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
     *   <li>51: tres cosas de la <b>taberna</b> que pidió el jugador:
     *       <ul>
     *         <li><b>Una SEGUNDA PUERTA</b>, en el muro <b>sur</b> (dx=3), mirando al patio de atrás: se sale del
     *             comedor sin cruzar toda la casa. La repone {@code rehacerMurosDeLaTaberna} (es el mismo
     *             constructor de muro con el índice de la puerta), así que las tabernas ya construidas la reciben.</li>
     *         <li><b>El POZO DE LA ESCALERA, tapado.</b> El hueco del forjado (dx 1..2, dz 8..11) daba de lleno al
     *             <b>cuarto suroeste</b> de la posada: se entraba andando desde el cuarto y se caía al comedor. Se
     *             cierra con tablones en la fila dz=12, del suelo de la posada al techo, <b>sin tocar la subida</b>
     *             (la salida de la escalera es dz=8).</li>
     *         <li><b>El DESVÁN</b>: el hueco bajo el tejado a dos aguas estaba macizo ("el cobertizo está todo relleno
     *             de bloques") y pasa a ser un <b>tercer piso</b> para el jugador, amueblado como base (cama, mesa de
     *             trabajo, horno, dos cofres, yunque, faroles y alfombra). Se vacía <b>solo el relleno interior</b>
     *             (las tejas de dx 1..17): la cáscara del tejado, los frontones y su ventana se quedan <b>celda por
     *             celda</b> como estaban, así que desde fuera se ve igual. Se sube por dentro, por un hueco abierto en
     *             las dos capas del forjado que cubre lo que se sube (la regla I16).</li>
     *       </ul></li>
     *   <li>52: la <b>ESCALERA DEL DESVÁN, fuera de la galería</b> (lo pidió el jugador: <i>"al poner la escalera al
     *       tercer piso tapaste el corredor que permite que se entre a los diferentes cuartos del 2do piso; mejor
     *       sacrifica un cuarto del 2do piso para poner ahí una escalera y libera el corredor"</i>). La escalera de la
     *       51 subía en recto por el carril <b>norte</b> de la galería de la posada y <b>tapaba el corredor</b> de los
     *       cuartos (las puertas de los del norte están en ese carril): ahora sube en <b>L</b> dentro del <b>cuarto
     *       suroeste</b> —el cuarto que se sacrifica para meterla— y la galería queda <b>entera</b> libre, los dos
     *       carriles. La cama de ese cuarto se <b>recoloca en el desván</b> (en vanilla cada cría necesita una cama
     *       libre: el pueblo no puede perder ninguna) y el reparador deshace la escalera vieja y vuelve a cerrar su
     *       hueco en el techo, con tablones y tejas, así que el techo queda sólido como estaba.</li>
     *   <li>54: la <b>HUERTA QUE NADIE VOLVÍA A LABRAR</b> (lo vio el jugador con captura: <i>"de esta parcela veo que
     *       hay dos espacios que no tienen cultivo y nadie los está reparando para hacerlos cultivables"</i>). Vanilla
     *       convierte la <b>tierra de cultivo en tierra</b> cuando alguien salta encima ({@code FarmBlock.fallOn}) y en
     *       la aldea conviven aldeanos, animales y el jugador: el bancal se pisa y se queda con <b>calvas</b>. Nadie
     *       las reponía por <b>dos motivos medidos</b> en su guardado (aldea 2): el <b>plano</b> de una aldea migrada
     *       es un <b>escaneo</b> del mundo y la tierra o el césped de esas celdas se descartaban como "terreno
     *       natural" (no estaban en el plano, así que el obrero no tenía nada que reponer) y el <b>granjero</b> solo
     *       sembraba en tierra de cultivo ya hecha, nunca la volvía a labrar. Ahora la huerta <b>entra siempre en el
     *       plano</b> (su geometría es fija: ver {@code VillageGenerator.estadoDeLaHuerta}) y el <b>granjero la
     *       labra</b> en su faena antes de sembrar; este reparador, idempotente, vuelve a labrar las calvas de una
     *       aldea ya construida (solo celdas de bancal que ahora son tierra o césped, con agua cerca y el hueco de
     *       arriba libre: <b>no arranca ningún cultivo</b>, I11).</li>
     *   <li>55: el <b>TOLDO DEL PORCHE, ENTERO</b> (lo vio el jugador: <i>"el pórtico está cortado con un espacio, ¿por
     *       qué? debería estar completo"</i>). Los dos <b>faroles de las puntas</b> del alero se colocaban en la
     *       <b>misma celda</b> que su <b>escalón</b> —encima del poste— y lo <b>sustituían</b> (el plano guarda el
     *       último bloque de cada celda), así que al toldo le faltaba un escalón en cada punta y se veía cortado; y
     *       además un farol <b>colgado</b> ahí no tenía <b>nada encima</b> de lo que colgar, así que estaba
     *       <b>flotando</b> (I14). Ahora el toldo lleva un <b>soffito de tablones</b> de punta a punta y los tres
     *       faroles <b>cuelgan</b> de él (uno en cada punta y el del centro). Este reparador arregla las tabernas ya
     *       construidas <b>solo en las celdas del porche</b> (no rehace la taberna: no se pierde ni la despensa ni las
     *       camas). Y, en la misma pasada, los <b>dos faroles del DORMITORIO de la barraca</b>: se colocaban
     *       <b>posados</b> en la celda que va pegada al tejado, donde no hay nada debajo, así que quedaban flotando
     *       (I14; medido: 2 por barraca en las aldeas 0 y 2). Eso es un retrofit <b>en el sitio</b> —la celda es la
     *       buena, lo que estaba mal era el estado (<b>colgados</b> del tejado)—, sin rehacer nada.</li>
     *   <li>56: la <b>ESCALERA DEL DESVÁN, QUE NO SE SUBÍA</b> (lo reportó el jugador: <i>"las escaleras para el 3er
     *       piso están bloqueadas por 2 bloques, dejando solo un espacio de un bloque libre; se tienen que romper esos
     *       2 bloques para que se pueda pasar"</i>). El <b>hueco de subida</b> abría <b>dos</b> celdas encima de cada
     *       escalón (la cabeza y la de encima) y con eso el <b>techo queda a 2,0</b> de la huella: el juego, al ganar un
     *       escalón, <b>levanta al jugador 0,6 de golpe</b> ({@code Entity.maxUpStep}) y comprueba la caja entera ahí
     *       arriba, así que necesita <b>2,4</b> libres y el escalón <b>no se sube</b> (el que sube se queda empujado
     *       contra la contrahuella). Medido en su guardado (aldea 2, taberna en {@code 1438,1428}, cota 120): los dos
     *       escalones atascados eran el 2º y el 3º —su techo, los tablones del techo de la posada ({@code y=129}) y la
     *       placa de tejas ({@code y=130}), a solo dos bloques de la huella—, que son <b>exactamente</b> los dos
     *       bloques que él tuvo que romper a mano. Ahora el hueco es de <b>tres</b> celdas por escalón
     *       ({@code DESVAN_HUECO_ALTO}) y este reparador lo ensancha en las tabernas ya construidas, <b>solo en las
     *       celdas del hueco</b> (dos en su partida). Hace falta aunque el jugador ya se hubiera roto los bloques: el
     *       <b>plano</b> los tiene sólidos y el obrero los <b>repone</b>; al abrirlos con {@code colocar} entran en el
     *       plano nuevo (I8) y ya no vuelven.</li>
     *   <li>57: el <b>ESTANQUE DE LA PESQUERA, DE VUELTA</b> (lo reportó el jugador con captura: <i>"¿por qué la choza
     *       para pesca no tiene su estanque para pescar?"</i>). La pesquera se construyó en la <b>migración 46</b> y
     *       todas las migraciones siguientes vuelven a llamar a {@code farm(level, center)}, que <b>nivela la aldea
     *       entera</b>: el nivelado daba el <b>agua</b> por "terreno que sobra" y rellenó el hueco del lago con
     *       <b>tierra</b> en {@code cota-2} y <b>césped</b> en {@code cota-1}. Medido en su guardado (aldea 2, centro
     *       {@code 1414,1414}, cota 120, base del lago {@code 1434,1458}): de las <b>49</b> celdas del lago solo
     *       quedaban <b>3</b> de agua —las tres columnas de los postes de la pasarela, que el nivelado se saltó al
     *       toparse con la valla— y el resto era césped, con la pasarela y los dos faroles encima. Y no se reparaba
     *       solo porque el <b>barril</b> seguía en pie ({@code pesqueraConstruida} se conforma con él). Arreglado de
     *       raíz (el agua y el hielo <b>no</b> son un hueco que se rellene: ver {@code nivelar} y {@code nivelarHuella})
     *       y, para las aldeas que ya se quedaron secas, con este reparador (<b>solo las celdas del lago</b>, y solo
     *       donde no haya nada construido: no inunda ni rompe nada más, e idempotente).</li>
     *   <li>58: la <b>CAMPANA, AL CENTRO DEL KIOSCO</b>, y el <b>BEACON DEL SELLO, FUERA</b> (lo pidió el jugador:
     *       <i>"sitúa la campana justo en el centro del kiosco y quita el beacon pues nunca se usa"</i>). Medido en su
     *       guardado (aldea 2, centro {@code 1414,1414}, cota 120): la campana estaba en {@code (1413,121,1415)},
     *       <b>una celda al oeste y una al sur</b> del centro, y la celda central {@code (1414,121,1414)} estaba en
     *       aire; el <b>beacon</b> estaba en la <b>celda central del tejado</b> ({@code 1414,125,1414}). Ahora la
     *       campana se coloca <b>posada en la celda central</b> (su apoyo es la plataforma, que va justo debajo) y el
     *       <b>sello místico</b> ya no enciende ningún beacon: no hacía nada sin pirámide y el sello vive en los
     *       <b>datos de la aldea</b> ({@code isSiegeResolved}, que es lo que miran la protección y el haz de
     *       partículas). Este reparador mueve la campana <b>solo si sigue siendo una campana</b> y <b>solo si la
     *       celda central está libre</b>, y quita el beacon <b>solo si sigue siendo un beacon</b>, reponiendo la
     *       <b>piedra del tejado</b> en su celda (de ahí <b>cuelga</b> el farol del kiosco, I14: dejarla en aire se
     *       lo llevaba por delante). No rehace el kiosco (su testigo es la plataforma, I15) y va antes de tirar el
     *       plano para que el plano nuevo lo capture ya centrado y <b>sin</b> el beacon (I8).</li>
     *   <li>59: la <b>ESCALERA DEL DORMITORIO DE LA BARRACA</b>, que no se subía (lo reportó el jugador como <i>"las
     *       escaleras para el 3er piso están bloqueadas"</i>, que era la taberna, y al medir la barraca salió esto).
     *       Medido en su guardado (aldea 2, barraca en {@code 1369,1436}, cota 120): la escalera tenía <b>tres</b>
     *       escalones de los cuatro —el <b>4º lo borraba el propio constructor</b>, porque para ese peldaño la celda
     *       del escalón y la del <b>hueco del forjado</b> eran la misma ({@code yPiso2 - 1 = nivel + 3}), así que el
     *       último quedaba a <b>1,0</b> del suelo del dormitorio y <b>solo se subía saltando</b>—, el <b>2º</b> llevaba
     *       una <b>cama</b> justo encima ({@code (1372,124,1438)}: <b>2,0</b> de hueco en vez de 2,4, I26), el
     *       <b>arca</b> del este estaba en la celda del último escalón y el {@code FACING} iba al <b>oeste</b> subiendo
     *       al <b>norte</b> (cara alta de través; y con el pie pegado al muro sur <b>no se podía ni entrar</b>). Este
     *       reparador recoloca la escalera (cuatro escalones de medio bloque, cara alta al norte), abre el hueco del
     *       forjado <b>solo encima de los escalones que pasan por debajo</b> de él, cierra el tablón que el hueco viejo
     *       se comía de más, corre <b>una celda al oeste</b> la cama del rincón sureste y pasa el arca del este al lado
     *       de la del oeste (con lo de dentro: un cofre reemplazado pierde su contenido, I6). Solo toca esas celdas: no
     *       rehace la barraca (rehacerla tiraría las camas y las arcas).</li>
     *   <li>60: la <b>MESA DE CARTOGRAFÍA DE LA BARRACA</b>, fuera (la pidió quitar el jugador al verla: <i>"sí,
     *       quítalo"</i>). Medido en su guardado (aldea 2, barraca en {@code 1369,1436}, cota {@code 120}): la celda
     *       {@code 1371,120,1438} tenía una {@code cartography_table}, que es el <b>puesto de trabajo del
     *       CARTÓGRAFO</b> —un oficio que este pueblo no tiene, así que un aldeano <b>sin oficio</b> lo reclamaría— y
     *       que además caía en la celda de la <b>paca del maniquí</b> de entrenamiento sureste: el constructor la
     *       coloca <b>después</b> del maniquí y en la <b>misma celda</b>, así que el maniquí se quedaba <b>sin
     *       base</b> (con la calabaza y las dos vallas en pie y el suelo de piedra debajo). Y el <b>plano</b> guardaba
     *       la mesa, así que el obrero la reponía. Este reparador la quita <b>solo si sigue siendo la mesa</b> y
     *       devuelve la celda a su <b>paca</b>: idempotente, de una sola celda y sin rehacer la barraca (su testigo
     *       es el hogar, I15). Va antes de tirar el plano para que el plano nuevo sea el bueno (I8).</li>
     *   <li>61: la <b>TERCERA DIANA DE LA BARRACA</b>, de vuelta (quedó apuntado al cerrar la 60, misma barraca y mismo
     *       patrón). Medido en su guardado (aldea 2, barraca en {@code 1369,1436}, cota {@code 120}): la primera diana
     *       se colocaba en {@code 1367,120,1438}, que es <b>la misma celda</b> que el <b>arca</b> de la sala de armas
     *       (la que fue un barril y pasó a cofre) y el arca se coloca <b>después</b>, así que <b>se la comía</b>: de
     *       las <b>tres</b> dianas que el constructor cree poner solo había <b>dos</b> ({@code 1371,120,1434} y
     *       {@code 1371,121,1434}) y el <b>plano</b> guardaba la misma foto. Ahora la diana suelta va <b>pegada a las
     *       dos paredes del rincón suroeste</b> ({@code BARRACA_DIANA}) y este reparador la devuelve ahí <b>solo si la
     *       celda está vacía</b>; la celda vieja <b>no se toca</b> (es la del arca). Idempotente, de una celda, sin
     *       rehacer la barraca (su testigo es el hogar, I15) y antes de tirar el plano (I8).</li>
     *   <li>63: el <b>TOLDO DEL PORCHE, HASTA LA PARED</b> (lo reportó el jugador: <i>"el techito que está en la
     *       entrada de la taberna está incompleto porque no conecta con la pared"</i>). Medido en su guardado (aldea 2,
     *       taberna en {@code 1438,1428}, cota {@code 120}): la pared de la taberna está en {@code bx} y el toldo salía
     *       solo hasta {@code bx-2}, así que las <b>7 de 7</b> celdas de {@code bx-1} (la columna entre el alero y el
     *       muro, a las dos alturas del toldo) estaban <b>vacías</b> y el techito se veía <b>suelto</b>: un alero
     *       apoyado en sus postes y a un bloque de la casa. Este reparador <b>añade la fila que falta</b> —el escalón
     *       a la altura de la fila de dentro y su tablón de soffito debajo, de punta a punta— <b>solo donde la celda
     *       esté vacía</b>: es <b>aditivo</b> (no quita nada, así que no puede comerse lo que haya puesto el jugador)
     *       y su celda de al lado, en el muro, ya es sólida (medida: {@code dark_oak_planks}). Idempotente y solo en
     *       las celdas del porche: no rehace la taberna (ni la despensa ni las camas).</li>
     *   <li>64: el <b>COMPOSTERO DEL BANCAL, UNA CELDA MÁS AFUERA</b> (lo reportó el jugador: <i>"siguen subiendo a la
     *       valla para poder entrar en vez de usar las compuertas"</i>, y de paso el granjero no lo encontraba). El
     *       compostero —el <b>puesto de trabajo del granjero</b>— estaba pegado a la valla del bancal
     *       ({@code corner.x-2}, con la valla en {@code corner.x-1}): su tapa queda a {@code cota+1} y desde ahí subir
     *       al lomo de la valla (1,5) es un paso de <b>0,5</b>, por debajo del {@code maxUpStep} (0,6), así que el
     *       granjero <b>trepaba la valla</b>. Medido en su guardado: los <b>tres</b> bancales tenían ese escalón y era
     *       el compostero. Se mueve a {@code corner.x-3} ({@code COMPOSTERO_DX}, I40), que deja una celda de aire entre
     *       el compostero y la valla. Es <b>conservador</b> (solo si el viejo sigue siendo un compostero y la celda
     *       nueva está libre con suelo firme) e <b>idempotente</b>. Y al granjero cuyo {@code JOB_SITE} apuntaba al
     *       compostero viejo se le <b>suelta el puesto</b> ({@code liberarPuesto}, I23) para que el latido le dé el
     *       nuevo: así vuelve a compostar (su búsqueda miraba la columna de la <b>valla</b> y no lo veía nunca, por eso
     *       no había harina de huesos ni abono).</li>
     *   <li>66: el <b>TOLDO DEL PORCHE, CON BLOQUE NORMAL EN LA CELDA DEL MURO</b> (lo corrigió el jugador al verlo:
     *       <i>"el techito que pusiste quedó bastante extraño; se necesita poner un bloque normal y luego ahora sí el
     *       bloque de escalera bien alineado para que quede bien"</i>). La 63 había cerrado el hueco entre el toldo y
     *       la pared añadiendo la fila de {@code bx-1} con un <b>escalón</b> más: dos escalones seguidos a la misma
     *       altura se ven como un <b>doble peldaño</b> raro contra el muro. Ahora esa celda es un <b>tablón sólido</b> y
     *       el escalón de {@code bx-2} apoya su cara alta contra él (el alero sube hacia la casa y baja hacia fuera).
     *       Solo cambia el escalón del toldo si sigue siéndolo (lo que ponga el jugador se queda), es idempotente y va
     *       en el mismo reparador del porche ({@code arreglarPorcheDeLaTaberna}).</li>
     *   <li>67: <b>LA MURALLA SE REPONE Y EL PLANO SE VUELVE A CAPTURAR</b> (lo reportó el jugador: *"la aldea ha tenido
     *       daños en su muralla y nadie ha ido a repararlo"*). El <b>obrero no puede reponer lo que no está en el
     *       plano</b> (I8) y una muralla dañada <b>antes</b> de capturarlo —o una aldea migrada, cuyo plano es un
     *       <b>escaneo</b> del mundo— deja esos agujeros <b>fuera</b> del plano para siempre: medido en su guardado
     *       (aldea 2, cota 120) con {@code build/obras_pendientes.py}, de las <b>7.296</b> celdas del plano solo
     *       <b>una</b> estaba pendiente (un farol), o sea que <b>ningún</b> agujero de la muralla estaba apuntado. La
     *       migración reconstruye el muro entero ({@code rehacerMuro}, que ya corría en este bloque) y tira el plano
     *       para volver a capturarlo con la muralla <b>entera</b>: a partir de ahí el obrero sí mantiene lo que se
     *       rompa. Es la misma lección de I50 (una construcción con un hueco que el plano no recuerda) aplicada al
     *       muro.</li>
     *   <li>68: la <b>BANDA DE SEPARACIÓN ENTRE PLANTAS DE LA TABERNA</b> (lo pidió el jugador, que se la había puesto a
     *       mano en un lado: *"estaría bien que la taberna tenga logs de separación entre un piso y otro… el log es más
     *       claro que el log de cada pilar para que lo distingas… estaría bien que estuviera desde el diseño en todos
     *       los lados"*). La <b>vuelta del forjado</b> de la posada (la línea de los muros, lo que se ve desde fuera)
     *       va en <b>troncos de roble CLARO</b> en vez de tablones oscuros, en los <b>cuatro</b> lados: separa las dos
     *       plantas de un vistazo y no se confunde con los postes de roble oscuro del entramado. Lo construye
     *       {@code forjadoDeLaPosada} en la taberna nueva y lo repone {@code ponerLaBandaDeLaTaberna} (idempotente: solo
     *       cambia los tablones del diseño, así que lo que el jugador tenga puesto ahí se queda).</li>
     *   <li>69: <b>EL COBERTIZO DEL ALMACÉN, AL SUELO</b> (lo reportó el jugador: *"el punto de apoyo del almacén
     *       (517,64,666) es inalcanzable"*). El suelo se ponía <b>en la cota</b> —una plataforma de un bloque entero,
     *       como la del kiosco— pero <b>sin el escalón</b> que sí tienen las cuatro entradas del kiosco, así que
     *       ningún aldeano podía subir (el juego sube <b>0,6</b> andando) y el punto de apoyo de
     *       {@code VillageStorage.puntoDeApoyo} —que caía <b>encima</b> de la plataforma, en {@code cota + 1}— se
     *       quedaba <b>inalcanzable</b>: medido en su guardado (aldea 0, centro {@code 470,646}, cota 63), <b>siete
     *       aldeanos</b> distintos (los dos herreros, el cocinero, el ganadero, el leñador...) y los <b>seis
     *       guardias</b> lo aparcaban con {@code "no consigue llegar a BlockPos{x=517, y=64, z=666}: lo deja por 5
     *       min"}. Ahora el cobertizo se construye como los otros dos del pueblo (el del corral anexo y el taller del
     *       leñador): <b>suelo a {@code cota - 1}, la capa que se pisa es la cota</b> (I1) y se entra <b>andando</b>.
     *       Lo baja {@code bajarElAlmacenAlSuelo} en las aldeas ya construidas, conservando lo de sus cofres
     *       (I6/I95).</li>
     *   <li>70: la <b>MINA DEL PUEBLO Y LA CASETA DEL MINERO</b> (etapa I, el <b>minero</b> que pidió el jugador: *"mejor
     *       haz otra profesión que sea de minero, y que excave el suelo hacia abajo, haciendo túneles, andamiajes de
     *       soporte, escaleras en espiral, todo para sacar minerales"*). La construye el latido
     *       ({@code asegurarLaMinaDelPueblo}, idempotente), pero <b>el plano tiene que volver a capturarse</b> para que
     *       la <b>caseta</b> —que es del pueblo— entre en él: sin esto el obrero no tendría nada que reponer de la
     *       caseta, y es justo lo que pasó con la muralla en la 67 (I8). El <b>pozo</b> y las <b>galerías</b> que cava
     *       el minero <b>no</b> entran (I102): son suyos y los mantiene él.</li>
     *   <li>71: la <b>MINA, AL DESCAMPADO DEL NORESTE Y CON LA CAMA DENTRO</b> (lo pidió el jugador nada más verla:
     *       *"mete más la cama del minero porque quedó fuera y bloquea la puerta. También mueve la cabaña del minero
     *       porque está muy cerca del centro, ponlo más bien en un lugar cercano al muro y donde haya mucho espacio que
     *       no se haya utilizado aún"*). El solar pasa de {@code rel (-9,+12)} (a <b>15</b> de la plaza) a
     *       {@code rel (+33,-29)} (a <b>44</b> del centro y a <b>18</b> del muro), elegido con {@code
     *       build/solar_mina2.py} sobre su guardado: <b>625 de 625</b> celdas libres en un entorno de 25×25 y el
     *       subsuelo macizo. La <b>cama</b> pasa al lado este, dentro (su cabecera caía en la celda de la <b>pared
     *       sur</b>, justo al lado de la puerta, y asomaba por el hueco). La mina <b>vieja</b> la retira
     *       {@code deshacerLaMinaVieja} (caseta y pozo, devolviendo el terreno) y el plano se vuelve a capturar, o
     *       quedarían las dos casetas.</li>
     * </ul>
     */
    public static final int CURRENT_LAYOUT = 71;

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
    /**
     * Cuántas veces más rápido crecen las <b>crías</b> que en vanilla (vanilla tarda 20 min: con 3, unos 7). Lo pidió
     * el jugador: *"los niños deben crecer más rápido para suplir a los aldeanos muertos en la noche"*.
     */
    private static final int BABY_GROWTH_SPEEDUP = 3;    /** Al llegar aquí (3 días de juego) el aldeano muere de viejo y deja el relevo a los jóvenes. */
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
        // SIEMBRA DEL DIARIO (una sola vez, y solo en partidas ya empezadas): una aldea que este mundo ya dio por
        // resuelta (salvada o caída) es una aldea en la que el jugador estuvo, así que se le apunta como descubierta y
        // revelada. Sin esto, en la partida de siempre el Diario nacería vacío y la barra de aldea no sabría ni hacia
        // dónde ir: justo el problema que el jugador quería resolver (*"no tengo las coordenadas para poder
        // regresar"*). Se hace una vez porque en cuanto haya UNA aldea apuntada ya no se vuelve a mirar.
        sembrarElDiarioSiHaceFalta(saved, player);
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
                // DESCUBRIMIENTO (lo pidió el jugador): al ENTRAR en la aldea queda apuntada como descubierta, y eso
                // le pone su NOMBRE en la barra de aldea y la guarda en el Diario del Invocado con sus coordenadas.
                // Solo cuenta si ha entrado de verdad (no vale verla de lejos, que para eso está el aviso de
                // `noticeIfNear`).
                PlayerAuxiliaryCapabilityInterface aux =
                        IGenericCapability.getUnwrappedPlayerCapability(player, PlayerAuxiliaryCapability.INSTANCE);
                if (aux != null && !aux.isAldeaVisitada(i)) {
                    aux.visitarAldea(i, player);
                    player.displayClientMessage(Component.literal(
                            "Has llegado a " + VillageNames.nombre(i) + "."), false);
                    DevilRpg.LOGGER.info("[Village] {} ha descubierto la aldea {} ({})",
                            player.getName().getString(), i, VillageNames.nombre(i));
                }
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
            //
            // Y EL SELLO EXPULSA A LOS QUE YA ESTÁN DENTRO **antes** de mirar si hay enemigos (etapa H): el aura corta
            // los spawns, pero no tocaba a los que entraron antes de vencer el asedio, a los que se colaron por un
            // portón abierto o a los que se cargan del guardado — se quedaban dentro para siempre. Y peor: con un
            // bicho dentro, `hayEnemigosDentro` cortaba el latido ENTERO (ni oficios, ni comida, ni reparaciones, ni
            // milicia: la otra mitad del fallo de I11), así que el pueblo se quedaba congelado por un esqueleto en una
            // cueva. Ahora, en una aldea protegida, primero se expulsa y después se mira.
            // Y LAS CAMAS, SIEMPRE (ni asedio ni bicho dentro lo impiden): es lo primero que se atiende y va FUERA del
            // bloque de abajo, que está detrás de `!isUnderAttack` y de `hayEnemigosDentro`. Un aldeano sin cama tiene
            // que recibirla también —y sobre todo— la noche del asedio (ver `atenderCamasDelPueblo`).
            if (level.getGameTime() % VILLAGE_POLL_TICKS == 0L && !saved.isFallen(i)) {
                atenderCamasDelPueblo(level, target, i);
            }
            if (level.getGameTime() % VILLAGE_POLL_TICKS == 0L && !saved.isFallen(i) && !isUnderAttack(level, i)) {
                if (saved.isSiegeResolved(i)) {
                    expulsarHostilesDeLaAldea(level, target, i);
                }
                if (hayEnemigosDentro(level, target)) {
                    continue; // quedan bichos dentro (los de las cuevas de debajo, por ejemplo): no se toca el pueblo
                }
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
                            && vivos < VillageGenerator.puestosDelPueblo() + MILICIA_MAX
                            && level.getGameTime() - saved.getRepopulatedAt(i) >= REPOPULATE_INTERVAL_TICKS) {
                        // Crecer cuesta comida: una aldea hambrienta no se recupera hasta que la granja produzca.
                        // El que llega nace CRÍA (crece sola, mecánica vanilla): así se ve el relevo generacional.
                        //
                        // DOS ARREGLOS (etapa H). 1) EL TOPE ERA INALCANZABLE: esta rama exige que TODAS las
                        // especialidades estén vivas (o sea ≥ puestos adultos) y a la vez `vivos < puestosDelPueblo()`
                        // ⇒ contradicción: el pueblo no podía parir NUNCA por aquí, y las crías que había eran las de
                        // vanilla (el pan de `feedVillagers`). Ahora crece hasta cubrir sus PUESTOS Y LA MILICIA, que
                        // es lo que pidió el jugador: *"la milicia se va a ir llenando conforme vayan naciendo y
                        // alcanzando la adultez aldeanos"*. 2) NACÍA CON OFICIO: `spawnOneVillager(..., vivos, true)`
                        // usaba el número de vivos como ÍNDICE DE PLAZA, así que el aldeano 10 nacía con el oficio de
                        // la plaza 10 (un oficio DUPLICADO). Ahora la cría nace SIN OFICIO: al crecer, el reparto le
                        // da una plaza si queda libre y, si no, engrosa la milicia.
                        VillageGenerator.spawnBaby(level, target);
                        saved.setFood(i, saved.getFood(i) - FOOD_TO_GROW);
                        saved.markRepopulated(i, level.getGameTime());
                        DevilRpg.LOGGER.info("[Village] Aldea {} crece: aldeano {}/{} (comida {})",
                                i, vivos + 1, VillageGenerator.puestosDelPueblo() + MILICIA_MAX, saved.getFood(i));
                    }
                }
            }
        }
    }

    /**
     * <b>El sello místico EXPULSA a los hostiles que ya están dentro</b> de una aldea protegida (etapa H).
     * <p>
     * El aura ({@code MobSpawnEvent.PositionCheck} + {@code EntityJoinLevelEvent}) corta los <b>spawns</b>, pero no
     * toca a los que ya estaban: los que entraron <b>antes</b> de vencer el asedio, los que se colaron por un
     * <b>portón abierto</b> (el pueblo los abre para pasar) o los que se <b>cargan del guardado</b>. Ésos se quedaban
     * dentro para siempre —y con uno dentro, {@code hayEnemigosDentro} cortaba el latido entero (I11), así que el
     * pueblo se congelaba—.
     * <p>
     * <b>PERO EL SELLO NO ES LO PRIMERO: PRIMERO DEFIENDE EL PUEBLO.</b> Antes esto expulsaba <b>en el acto</b> (el
     * latido es cada 10 s), así que un agresivo que <b>entraba andando</b> de día desaparecía de la aldea antes de que
     * nadie lo tocara: el jugador lo vio y lo cantó como lo que es —*"llegaron unos zombies agresivos durante el día
     * a la aldea, pero no pasó mucho tiempo y fueron teletransportados a fuera; esto se ve antinatural"*—. Ahora el
     * sello <b>espera</b> {@link #SELLO_ANTES_DE_EXPULSAR_TICKS} (2 min) con el intruso dentro: en ese rato la
     * <b>milicia</b> lo ve y va a por él (los guardias persiguen a cualquier monstruo que esté <b>dentro del
     * recinto</b>, aunque esté lejos: ver {@code VillagerGuardGoal.buscarEnemigo}) y el bicho se muere como cualquier
     * otro. Solo si <b>sigue dentro</b> pasado ese tiempo —nadie ha podido con él: está en un tejado, dentro de una
     * casa, en un hueco— el sello lo <b>rechaza</b>, que es la red de seguridad que impide que un solo bicho deje el
     * pueblo congelado para siempre.
     * <p>
     * Se mira solo lo que está <b>a la altura del pueblo</b> (recinto en XZ + banda sobre la cota, como cualquier
     * recuento de I11): un bicho en una cueva 20 bloques por debajo no está "dentro de la aldea" y no se toca. Se
     * dejan en paz los <b>aldeanos-zombi</b> (una curación en marcha es cosa del jugador), no se corre con un asedio
     * activo (ésos son los asediadores, que están ahí a propósito) ni con una <b>horda del mundo</b> en curso
     * ({@code isUnderAttack} cubre las dos: mientras la aldea está siendo atacada, el sello no toca a nadie). Se les
     * echa <b>fuera del muro</b>, en su misma dirección y con el portal de la marca: no se les mata, así que no hay
     * botín gratis y la horda puede volver andando, que es como está pensado.
     *
     * @return cuántos ha expulsado
     */
    private static int expulsarHostilesDeLaAldea(ServerLevel level, BlockPos center, int objectiveIndex) {
        int cota = VillageGenerator.cotaDeLaPlaza(level, center);
        double fueraDelMuro = VillageGenerator.FENCE_RADIUS + 6.0D;
        long ahora = level.getGameTime();
        java.util.Set<Intruso> vistosDentro = new HashSet<>();
        int expulsados = 0;
        for (Monster bicho : level.getEntitiesOfClass(Monster.class,
                new AABB(center).inflate(VillageGenerator.FENCE_RADIUS + 8.0D, 24.0D,
                        VillageGenerator.FENCE_RADIUS + 8.0D))) {
            if (bicho.isRemoved() || bicho instanceof net.minecraft.world.entity.monster.ZombieVillager) {
                continue; // un aldeano-zombi puede ser una curación en marcha: no se toca
            }
            if (!dentroDelRecinto(cota, center, bicho, VillageGenerator.FENCE_RADIUS)) {
                continue; // en XZ sí, pero en una cueva de debajo: no está "dentro"
            }
            vistosDentro.add(new Intruso(objectiveIndex, bicho.getUUID()));
            // ¿DESDE CUÁNDO ESTÁ DENTRO? Mientras no lleve aquí SELLO_ANTES_DE_EXPULSAR_TICKS, el que trabaja es el
            // pueblo (la milicia), no el sello: es lo que hace que la defensa se VEA.
            long desde = INTRUSOS_DENTRO.computeIfAbsent(new Intruso(objectiveIndex, bicho.getUUID()), k -> ahora);
            if (ahora - desde < SELLO_ANTES_DE_EXPULSAR_TICKS) {
                continue;
            }
            double dx = bicho.getX() - (center.getX() + 0.5D);
            double dz = bicho.getZ() - (center.getZ() + 0.5D);
            double distancia = Math.max(0.001D, Math.sqrt(dx * dx + dz * dz));
            double x = center.getX() + 0.5D + dx / distancia * fueraDelMuro;
            double z = center.getZ() + 0.5D + dz / distancia * fueraDelMuro;
            // `randomTeleport` busca un sitio seguro alrededor (es el teletransporte del enderman); si no lo encuentra
            // —o el destino no está cargado— el bicho se va del pueblo igual, pero sin dejar botín.
            if (!bicho.randomTeleport(x, cota, z, false)) {
                bicho.discard();
            }
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SCULK_SOUL,
                    bicho.getX(), bicho.getY() + 1.0D, bicho.getZ(), 12, 0.3D, 0.5D, 0.3D, 0.02D);
            level.playSound(null, bicho.blockPosition(), net.minecraft.sounds.SoundEvents.SCULK_SHRIEKER_SHRIEK,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.7F, 1.2F);
            expulsados++;
        }
        // Se olvida SOLO lo de ESTA aldea (el latido de una aldea no puede tocar el reloj de las demás) que ya no
        // está dentro: el bicho que salió (o murió) vuelve a empezar de cero si vuelve a entrar.
        INTRUSOS_DENTRO.keySet().removeIf(k -> k.aldea() == objectiveIndex && !vistosDentro.contains(k));
        if (expulsados > 0) {
            DevilRpg.LOGGER.info("[Village] Aldea {}: el sello ha expulsado a {} hostil(es) que llevaban {} s dentro"
                            + " (la milicia no pudo con ellos)", objectiveIndex, expulsados,
                    SELLO_ANTES_DE_EXPULSAR_TICKS / 20);
            // Y SE DICE, que es lo que hace que no parezca un teletransporte raro: el jugador tiene que saber que fue
            // el sello (y por qué) y no un bicho que desaparece solo.
            announceNearby(level, center, "El sello de la aldea ha rechazado a los intrusos.");
        }
        return expulsados;
    }

    /**
     * <b>UN GOLEM NO APARECE EN UN PISO DE ARRIBA.</b> El golem de hierro lo <b>suma un aldeano</b> cuando da el aviso
     * de alarma, y lo hace <b>donde está él</b>: si el aldeano está en la <b>posada</b>, en el <b>desván</b> de la
     * taberna o en el dormitorio de la barraca, el golem sale <b>dentro de la casa</b> —no defiende el pueblo y se
     * queda atrapado arriba—. Lo reportó el jugador: *"los golems no deben spawnear en el 3er piso"*, y medido en su
     * guardado había uno en `(1446.8,131,1430.4)`: **+11** sobre la cota, o sea el desván de la taberna, al lado del
     * aldeano que lo había sumado.
     * <p>
     * Se llama al <b>entrar al mundo</b> (el mismo sitio donde el sello corta los spawns), así que vale para las dos
     * vías: la de vanilla (el aldeano que lo suma) y la del mod ({@code VillageGenerator.spawnIronGolem}, que usa
     * {@code groundY} y por eso puede dar con los pies en un <b>tejado</b>). Si el golem aparece dentro de una aldea y
     * por encima del suelo, se le <b>baja a una casilla libre a la cota</b>, al lado de la plaza.
     */
    public static void bajarElGolemAlSuelo(ServerLevel level, net.minecraft.world.entity.animal.IronGolem golem) {
        VillageSavedData saved = VillageSavedData.get(level);
        if (golem.isRemoved()) {
            return;
        }
        for (int i : saved.generatedIndices()) {
            BlockPos centro = centroDe(level, i);
            if (centro == null || saved.isFallen(i)) {
                continue;
            }
            int cota = VillageGenerator.cotaDeLaPlaza(level, centro);
            if (golem.getY() <= cota + 1.0D) {
                continue; // ya está a nivel del suelo: no se toca
            }
            if (!dentroDelRecinto(cota, centro, golem, VillageGenerator.FENCE_RADIUS)) {
                continue; // no es de esta aldea
            }
            BlockPos destino = casillaAlSueloDeLaPlaza(level, centro, cota);
            if (destino == null) {
                return; // no hay sitio libre a la cota: mejor dejarlo donde está que meterlo en un bloque
            }
            long altura = Math.round(golem.getY() - cota); // medido ANTES de moverlo (si no, el aviso dice 0)
            golem.moveTo(destino.getX() + 0.5D, destino.getY(), destino.getZ() + 0.5D,
                    golem.getYRot(), golem.getXRot());
            golem.getNavigation().stop();
            DevilRpg.LOGGER.info("[Village] Aldea {}: un golem aparecio {} bloque(s) por encima del suelo (dentro de"
                    + " un edificio): se le baja a {}", i, altura, destino.toShortString());
            return;
        }
    }

    /**
     * Una casilla <b>libre a la cota</b> cerca de la plaza (fuera del kiosco): suelo firme y seco debajo y dos celdas
     * libres (pies y cabeza). Es donde se posa a un golem que apareció en un piso de arriba.
     */
    @Nullable
    private static BlockPos casillaAlSueloDeLaPlaza(ServerLevel level, BlockPos centro, int cota) {
        int desde = VillageGenerator.kioscoRadio() + 1;
        for (int r = desde; r <= 24; r++) {
            for (BlockPos p : BlockPos.betweenClosed(centro.offset(-r, 0, -r), centro.offset(r, 0, r))) {
                if (Math.max(Math.abs(p.getX() - centro.getX()), Math.abs(p.getZ() - centro.getZ())) != r) {
                    continue; // el interior ya se miró en los anillos anteriores
                }
                BlockPos q = new BlockPos(p.getX(), cota, p.getZ());
                if (!level.getBlockState(q).getCollisionShape(level, q).isEmpty()
                        || !level.getBlockState(q.above()).getCollisionShape(level, q.above()).isEmpty()) {
                    continue;
                }
                BlockState suelo = level.getBlockState(q.below());
                if (suelo.getCollisionShape(level, q.below()).isEmpty() || !suelo.getFluidState().isEmpty()) {
                    continue; // sin suelo firme, o sobre agua: ahí no se le posa
                }
                return q;
            }
        }
        return null;
    }

    /**
     * <b>Los golems de la aldea, a la CALLE.</b> El latido los mira y baja a los que estén dentro del recinto y por
     * encima del suelo (ver {@link #bajarElGolemAlSuelo}): así se corrige también el que <b>ya</b> estaba en un piso
     * de arriba antes de este arreglo, sin esperar a que se muera. Son uno o dos por aldea, y solo se les toca si
     * están por encima de la cota.
     */
    private static void bajarGolemsDeLosPisos(ServerLevel level, BlockPos center) {
        int cota = VillageGenerator.cotaDeLaPlaza(level, center);
        for (net.minecraft.world.entity.animal.IronGolem golem : level.getEntitiesOfClass(
                net.minecraft.world.entity.animal.IronGolem.class, new AABB(center)
                        .inflate(VillageGenerator.FENCE_RADIUS + 8.0D, 24.0D, VillageGenerator.FENCE_RADIUS + 8.0D))) {
            if (!golem.isRemoved() && golem.getY() > cota + 1.0D
                    && dentroDelRecinto(cota, center, golem, VillageGenerator.FENCE_RADIUS)) {
                bajarElGolemAlSuelo(level, golem);
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
        // LA PRESIÓN DEL ABANDONO SE ACUMULA AQUÍ, EN EL RELOJ DEL MUNDO (I98): son los minutos que una aldea
        // lleva sin que nadie la socorra, y es lo que hace que el mundo le mande una horda. Antes se acumulaba
        // SOLO dentro de `pickHordeTarget`, y esa función solo corre cuando ya ha tocado un roll de horda: el
        // primer roll solo fijaba la base (presión 0) y la aldea no podía ser elegida hasta el SEGUNDO, 20-40 min
        // después. Medido en la partida del jugador: `Pressure` vacío en el guardado y **ni una** línea de horda
        // hacia una aldea en dos días de logs. Va en un latido de 10 s (no por tick): es una suma y no hace falta
        // más resolución.
        acumularPresionDelAbandono(level);
        // Hordas que el MUNDO manda contra una aldea (Iteración 3): se resuelven aparte de los asedios que
        // arranca el jugador al llegar.
        tickWorldSieges(level);

        List<VillageDefense> list = DEFENSES.get(level);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (int i = list.size() - 1; i >= 0; i--) {
            VillageDefense d = list.get(i);
            // EL RELOJ NO CORRE SIN EL JUGADOR (ver RADIO_ASEDIO_CON_JUGADOR): si se va, el asedio se PAUSA —no se da
            // por perdido— y las aldeas no caen a sus espaldas. Un asedio que se resuelve sin nadie delante es una
            // derrota a ciegas, y es exactamente lo que le pasó al jugador: se fue con los zombis dentro del muro y
            // al volver la aldea ya estaba en ruinas.
            if (!jugadorEnLaAldea(level, d)) {
                if (!d.enPausa) {
                    d.enPausa = true;
                    DevilRpg.LOGGER.info("[Village] Asedio de la aldea {} EN PAUSA: el jugador no esta en la aldea"
                                    + " (a {} bloques del centro): el reloj se para y la aldea NO puede caer",
                            d.objectiveIndex, Math.round(distanciaAlCentro(level, d)));
                }
                continue;
            }
            if (d.enPausa) {
                d.enPausa = false;
                // Al volver se le da el tiempo ENTERO otra vez: ni el margen ya gastado ni el tiempo que corrió
                // durante su ausencia. Si la ola ya estaba fuera, el minuto y medio de margen para limpiarla; si
                // todavía no había salido, el margen de exploración de nuevo.
                d.tickTicks = d.waveSpawned ? GRACE_TICKS : 0L;
                DevilRpg.LOGGER.info("[Village] Aldea {}: el jugador ha vuelto al asedio; se le da el tiempo entero"
                                + " otra vez ({} s de margen)",
                        d.objectiveIndex, (d.waveSpawned ? SIEGE_TIMEOUT_TICKS : GRACE_TICKS) / 20);
            }
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
                        /** Si la aldea acabó CAYENDO (para no revelar la siguiente: ver más abajo). */
                        boolean cayo = false;
                        if (waveCleared) {
                            if (d.wave.isEmpty()) {
                                grantReward(player, d.objectiveIndex);
                                marcarSelloMistico(d.center, player);
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
                            marcarSelloMistico(d.center, player);
                            player.displayClientMessage(Component.literal(isCurrentObjective
                                    ? "Los monstruos no lograron entrar: ¡la aldea está a salvo! El objetivo avanza."
                                    : "Los monstruos no lograron entrar: ¡la aldea está a salvo!"), false);
                        } else {
                            // La aldea ha caído de verdad (los monstruos entraron y sobrevivieron al tiempo):
                            // se marca caída (definitiva) y queda en ruinas. Antes solo se avisaba por chat y la
                            // aldea seguía "viva", así que el gestor la repoblaba más tarde como si nada.
                            fallVillage(level, VillageSavedData.get(level), d.objectiveIndex, d.center);
                            cayo = true;
                            // Y AQUÍ **NO** SE REVELA LA SIGUIENTE (lo pidió el jugador): de una aldea que cae no
                            // queda ni barra de aldea ni distancia, solo el aviso con el RUMBO —*"si cae la aldea,
                            // que no aparezca en la barra de objetivos, y que solo salga un mensaje en el chat
                            // indicando su dirección sin decir cuántos bloques está"*—. Al encontrarla y entrar en
                            // ella, su nombre y sus coordenadas se apuntan solos en el Diario del Invocado.
                            String hacia = rumboALaSiguiente(d.objectiveIndex, d.center, aux);
                            player.displayClientMessage(Component.literal("La aldea cayó: los monstruos aguantaron"
                                    + " dentro de los muros. Un superviviente alcanza a decirte que hay otra aldea"
                                    + " hacia el " + hacia + "... y no sabe cuánto queda."), false);
                        }
                        if (isCurrentObjective) {
                            if (!cayo) {
                                // LA ALDEA SE SALVÓ: se anuncia su NOMBRE y se pone al día el Diario, y **el objetivo
                                // NO avanza**: la barra de aldea se queda enseñando ESTA aldea (con su nombre, que ya
                                // la has descubierto) hasta que le hables al CLÉRIGO, que es quien te pasa a la
                                // siguiente. Lo pidió el jugador: *"una vez ganado el asedio APAREZCA en la barra de
                                // aldea el nombre de la aldea actual recién ganada y sólo cuando vaya con el clérigo
                                // cambie al siguiente objetivo… sin revelar aún el nombre"*.
                                anunciarLaAldeaSalvada(player, d.objectiveIndex);
                            } else {
                                // CAYÓ: esa aldea ya no se puede salvar, así que el objetivo SÍ avanza (y sin revelar
                                // nada: solo queda el aviso con el rumbo, ver arriba).
                                aux.setObjectiveIndex(d.objectiveIndex + 1, player);
                            }
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
        // Y EL DIARIO, NO UN LIBRO EN BLANCO (23-sep-2026): aquí se daba un `Items.WRITTEN_BOOK` a secas —sin
        // contenido y sin la marca del mod (`"receta (por ahora un libro genérico)"`)—, y el jugador se quedaba con
        // un libro que NO era el Diario: `esElDiario` era false, el Diario no se reescribía y las aldeas
        // descubiertas no aparecían por ningún lado (medido en su guardado: un `written_book` con el NBT
        // `{count, Slot, id}` a secas). Ahora se le da el de verdad si no lo tiene (y si lo tiene, se pone al día:
        // de eso ya se encarga `actualizarSiLoTiene` al salvarse la aldea).
        com.chipoodle.devilrpg.item.DiarioDelInvocado.entregarSiNoLoTiene(player);

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
            DevilRpg.LOGGER.info("[Horda] la aldea {} lleva {} min sin socorro: elegida como objetivo",
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
        DevilRpg.LOGGER.info("[Horda] la aldea {} esta siendo atacada: {} enemigos marchan a por ella",
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
                DevilRpg.LOGGER.info("[Horda] la aldea {} resistio el ataque (presion reiniciada)",
                        siege.objectiveIndex);
                announceNearby(level, siege.center, "La aldea ha resistido: los monstruos han sido rechazados.");
                // Recompensa para quien la defendió (ver registerDefender).
                rewardWorldSiegeDefenders(level, siege);
                list.remove(i);
                continue;
            }
            int vivos = observeVillagers(level, saved, siege.objectiveIndex, siege.center);
            if (vivos == 0 && !hayJugadorEnLaAldea(level, siege.center)) {
                // SIN NADIE DELANTE LA ALDEA NO CAE (ver RADIO_ASEDIO_CON_JUGADOR). El mundo puede mandar hordas
                // cuando quiera, pero una aldea no se pierde por estar el jugador lejos: se le estaría diciendo que
                // ha perdido algo que no pudo defender. La horda se queda donde está y, si el jugador vuelve, la
                // pelea sigue (y con él delante, esta misma comprobación ya decide de verdad).
                if (!siege.avisadoSinDefensor) {
                    siege.avisadoSinDefensor = true;
                    DevilRpg.LOGGER.info("[Horda] la aldea {} se ha quedado sin aldeanos, pero NO cae: no hay"
                            + " ningun jugador alli que pueda defenderla", siege.objectiveIndex);
                }
            } else if (vivos == 0) {
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
     * <b>El estado de una aldea, dicho en una línea</b>: es lo que enseña el <b>Diario del Invocado</b> (I87) y lo que
     * vuelca el arnés, así que la regla vive <b>aquí y en un solo sitio</b> (no copiada dentro del objeto).
     * <p>
     * El orden importa: una aldea caída está caída aunque haya bichos dentro; y el asedio se mira <b>antes</b> que el
     * sello, porque una aldea con el sello puesto puede estar siendo atacada otra vez (el sello corta los spawns de
     * dentro, no las hordas que ya vienen de fuera).
     */
    public static String estadoDeLaAldea(ServerLevel level, int objectiveIndex) {
        VillageSavedData saved = VillageSavedData.get(level);
        if (saved.isFallen(objectiveIndex)) {
            return "EN RUINAS";
        }
        if (isUnderAttack(level, objectiveIndex)) {
            return "en asedio";
        }
        if (saved.isSiegeResolved(objectiveIndex)) {
            return "a salvo, con el sello puesto";
        }
        return "viva, sin socorrer";
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

    /**
     * Distancia <b>horizontal</b> (bloques) del jugador que defiende esta aldea a su centro. Devuelve
     * {@link Double#MAX_VALUE} si ese jugador no está conectado.
     */
    private static double distanciaAlCentro(ServerLevel level, VillageDefense d) {
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(d.playerUUID);
        if (p == null) {
            return Double.MAX_VALUE;
        }
        double dx = p.getX() - (d.center.getX() + 0.5D);
        double dz = p.getZ() - (d.center.getZ() + 0.5D);
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** ¿El jugador que defiende esta aldea sigue en ella (ver {@link #RADIO_ASEDIO_CON_JUGADOR})? */
    private static boolean jugadorEnLaAldea(ServerLevel level, VillageDefense d) {
        return distanciaAlCentro(level, d) <= RADIO_ASEDIO_CON_JUGADOR;
    }

    /** ¿Hay ALGÚN jugador en la aldea, o sea alguien que pueda defenderla (hordas del mundo)? */
    private static boolean hayJugadorEnLaAldea(ServerLevel level, BlockPos center) {
        double limite = RADIO_ASEDIO_CON_JUGADOR * RADIO_ASEDIO_CON_JUGADOR;
        for (ServerPlayer p : level.players()) {
            double dx = p.getX() - (center.getX() + 0.5D);
            double dz = p.getZ() - (center.getZ() + 0.5D);
            if (dx * dx + dz * dz <= limite) {
                return true;
            }
        }
        return false;
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
     * <b>El abandono de las aldeas, contado por el RELOJ DEL MUNDO</b> (I98): cada
     * {@link #VILLAGE_POLL_TICKS} se le suma a la presión de cada aldea <b>generada</b>, <b>no caída</b> y que
     * <b>no esté ya bajo ataque</b> el tiempo que ha pasado (multiplicado por {@link #pressureMultiplier}, que es
     * como se premia a las aldeas débiles).
     * <p>
     * Los tres requisitos son los mismos que usa {@link #pickHordeTarget} para elegir objetivo, así que lo que se
     * acumula aquí es exactamente lo que allí se lee. Y como se acumula por <b>tiempo del mundo</b> (ticks), avanza
     * aunque el chunk esté descargado y <b>no se reinicia al cerrar el juego</b>: lo que se pierde al cerrar es el
     * turno del roll de la horda (ver {@code HordeManager}), no el abandono de la aldea.
     */
    private static void acumularPresionDelAbandono(ServerLevel level) {
        long gameTime = level.getGameTime();
        if (gameTime % VILLAGE_POLL_TICKS != 0L) {
            return;
        }
        VillageSavedData saved = VillageSavedData.get(level);
        for (int i = 0; i <= MAX_OBJECTIVES; i++) {
            if (!saved.isGenerated(i) || saved.isFallen(i) || isUnderAttack(level, i)) {
                continue; // ya está resuelta, caída o siendo atacada: no acumula abandono
            }
            saved.accruePressure(i, gameTime, pressureMultiplier(saved.getHealth(i)));
        }
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

        // Y LOS GOLEMS, A LA CALLE (I61): el aldeano que da el aviso de alarma suma el golem donde está él, así que
        // uno que esté en un piso de arriba (la posada, el desván de la taberna) lo saca dentro de la casa. Aquí se
        // baja a los que estén en alto, incluido el que ya estaba antes de este arreglo.
        bajarGolemsDeLosPisos(level, center);

        ageVillagers(level, aldeanos);
    }

    /**
     * <b>El rebaño del pueblo que se ha escapado vuelve a casa</b> (lo pidió el jugador: "cuando el ganadero entra al
     * corral, deja la puerta abierta y los animales se salen; dale la capacidad para meterlos de vuelta").
     * <p>
     * Antes esto era un <b>teleport</b> directo al corral y solo miraba a los que estaban a más de 26 bloques del
     * centro del corral: un animal que se salía por el portón y se quedaba pastando al lado de la valla contaba como
     * "dentro" y no volvía nunca (medido en el guardado del jugador, aldea 2: la vaca del pueblo a 12,1 bloques del
     * corral y la oveja a 13,5). Ahora:
     * <ul>
     *   <li>se <b>andan</b> el camino (goal {@code VuelveAlCorralGoal}: va al portón por fuera, se le abre cuando
     *       llega y entra; se le cierra detrás), que es como tiene que verse un rebaño, y</li>
     *   <li>solo si el animal <b>no encuentra el camino</b> (atascado) se le mete a mano: último recurso.</li>
     * </ul>
     * Los animales del <b>jugador</b> no se tocan (no llevan la marca del pueblo), ni los que van montados o atados
     * con una cuerda. Es idempotente: al animal que ya lleva el goal no se le pone otro.
     */
    private static void traerElRebanoALaCasa(ServerLevel level, BlockPos center, int objectiveIndex) {
        int mandados = 0;
        for (net.minecraft.world.entity.animal.Animal animal
                : VillageGenerator.ganadoPerdidoDelPueblo(level, center)) {
            if (com.chipoodle.devilrpg.entity.goal.VuelveAlCorralGoal.asegurar(animal, center)) {
                mandados++;
            }
        }
        if (mandados > 0) {
            DevilRpg.LOGGER.info("[Village] Aldea {}: {} animal(es) del rebano vuelven al corral andando",
                    objectiveIndex, mandados);
        }
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
            // LA HUERTA, OTRA VEZ CULTIVABLE (migración 54, lo vio el jugador: "dos espacios que no tienen cultivo y
            // nadie los está reparando"). `farm` sale antes de tiempo si el bancal ya está hecho (`bancalHecho`, la
            // guardia I11 que impide que el nivelado se lleve los cultivos por delante), así que las CALVAS que deja
            // el pisoteo no las toca nadie: aquí se vuelven a labrar, celda a celda y solo si de verdad son una
            // calva. Va antes de recapturar el plano para que el plano nuevo las tenga (aunque ya las pide
            // `estadoDeLaHuerta`) y es idempotente.
            VillageGenerator.labrarCalvasDelBancal(level, center);
            // EL COMPOSTERO DEL BANCAL, UNA CELDA MÁS AFUERA (migración 64, lo reportó el jugador: *"siguen subiendo a
            // la valla para poder entrar en vez de usar las compuertas"*). El compostero del granjero —su puesto de
            // trabajo— estaba PEGADO a la valla del bancal (`corner.x-2`, con la valla en `corner.x-1`) y su tapa
            // queda a `cota+1`: subir al lomo de la valla (1,5) desde ahí es un paso de 0,5, por debajo del
            // `maxUpStep` del juego (0,6), así que el granjero la TREPABA en vez de entrar por la compuerta. Medido
            // en su guardado: los TRES bancales tenían ese escalón, y era el compostero. Se mueve una celda afuera
            // (`COMPOSTERO_DX`, I40) y, de paso, el granjero ya lo ENCUENTRA: su búsqueda miraba en la columna de la
            // valla y no lo veía nunca (por eso no compostaba ni abonaba).
            List<BlockPos[]> composterosMovidos = VillageGenerator.moverComposterosDelBancal(level, center);
            for (BlockPos[] par : composterosMovidos) {
                BlockPos viejo = par[0];
                BlockPos nuevo = par[1];
                for (Villager granjero : aldeanos) {
                    // El PUESTO de ese granjero se MUDA con su compostero (la estación es suya, I36): así no cambia
                    // de bancal. Un puesto que ya no está tampoco se queda cogido (I23).
                    Optional<GlobalPos> suyo = granjero.getBrain().getMemory(MemoryModuleType.JOB_SITE);
                    if (suyo.isPresent() && suyo.get().pos().equals(viejo)) {
                        moverPuestoDeTrabajo(level, granjero, viejo, nuevo);
                    }
                }
            }
            // Y LOS EXTREMOS DE LA ACEQUIA, DE VUELTA A CELDA DE CULTIVO (migración 65, la SEGUNDA causa del mismo
            // reporte: "siguen subiendo a la valla para poder entrar"). La acequia va tapada con una losa (para que
            // no se congele y para que nadie se caiga dentro), la losa se pisa a `cota+0,5` y las compuertas del
            // bancal caen justo en la fila del medio: desde la losa del EXTREMO, el aldeano saltaba la valla (de
            // 120,5 a 121,5 hay 1,0 y un mob salta 1,25). Medido con el arnés: la granjera Cesarea venía por la
            // acequia y saltó por encima de la compuerta este. Los dos extremos vuelven a ser celdas de cultivo (la
            // capa que se pisa queda a la altura de la tierra) y el bancal gana dos celdas plantables.
            VillageGenerator.rehacerLosExtremosDeLaAcequia(level, center);
            // EL CORRAL, ENSANCHADO (migración 45): el corral pasa de 15x15 a 19x19 y se retira el viejo (solo sus
            // bloques). Va ANTES de `asegurarGranjaAnexa`, que si no saldría antes de tiempo al ver el corral viejo.
            VillageGenerator.ensancharElCorral(level, center);
            // EL ALMACÉN SE MUEVE (migración 45): delante de la puerta de la taberna (18,18) a su lado (48,21), y
            // más grande. Lo que hubiera en los cofres viejos se pasa al nuevo antes de retirar el viejo.
            VillageGenerator.moverAlmacen(level, center);
            // Y EL COBERTIZO DEL ALMACÉN, AL SUELO (migración 69, I95): se levantaba con el suelo EN la cota (una
            // plataforma de un bloque entero) y SIN escalón, así que el punto de apoyo que devuelve
            // `VillageStorage.puntoDeApoyo` caía encima de ella (a `cota + 1`) y NINGÚN aldeano podía subir: el
            // juego sube 0,6 andando y esa plataforma es de 1,0. Medido en el guardado del jugador (aldea 0, cota 63):
            // el punto era `(517,64,666)` y lo aparcaban 5 min siete aldeanos distintos y los seis guardias
            // ("no consigue llegar a BlockPos{x=517, y=64, z=666}"). El kiosco también es una plataforma a la cota,
            // pero tiene escaleras en sus cuatro entradas; aquí se elige la convención de los otros dos cobertizos
            // (el del corral y el taller del leñador): suelo a `cota - 1` y se entra andando. Va antes de tirar el
            // plano (I8) y conserva lo de los cofres (I6).
            VillageGenerator.bajarElAlmacenAlSuelo(level, center);
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
            // EL DESVÁN Y EL POZO DE LA ESCALERA (migración 51, lo pidió el jugador): el hueco bajo el tejado pasa a
            // ser un tercer piso amueblado (se vacía SOLO el relleno interior: la cáscara del tejado, los frontones y
            // su ventana quedan celda por celda igual, así que desde fuera se ve lo mismo) y el pozo de la escalera se
            // cierra por el sur, que daba al cuarto suroeste de la posada. Van DESPUÉS de `rehacerMurosDeLaTaberna`
            // (que vuelve a pasar el tejado entero, relleno incluido) y son idempotentes: no rehacen la taberna, así
            // que no se pierde ni la despensa ni las camas.
            VillageGenerator.desvanDeLaTaberna(level, center);
            VillageGenerator.cerrarElHuecoDeLaEscalera(level, center);
            // LA ESCALERA DEL DESVÁN, FUERA DE LA GALERÍA (migración 52, lo pidió el jugador): la de la 51 subía por
            // el carril norte de la galería y TAPABA EL CORREDOR por el que se entra a los cuartos del segundo piso.
            // Aquí se deshace aquélla (sus seis escalones y el hueco que abrió en las dos capas del forjado, que se
            // vuelve a cerrar con tablones y tejas), se recuelga el farol de la galería que se comió su cuarto escalón
            // y se retira la cama del cuarto suroeste —que pasa a ser la caja de la escalera—: su sustituta la pone el
            // desván, así que el pueblo no pierde ninguna cama. La escalera nueva (dentro del cuarto) y su hueco los
            // monta `desvanDeLaTaberna`, que este reparador vuelve a llamar. Es idempotente y no rehace la taberna.
            VillageGenerator.moverLaEscaleraDelDesvan(level, center);
            // EL TOLDO DEL PORCHE, ENTERO (migración 55, lo vio el jugador: "el pórtico está cortado con un espacio,
            // ¿por qué? debería estar completo"). Los dos faroles de las puntas del alero se colocaban en la celda de
            // su ESCALÓN y lo sustituían (y el plano guarda el último bloque de cada celda), así que faltaba un
            // escalón en cada punta del toldo y los dos faroles colgaban del aire (I14). Se repara SOLO el porche,
            // celda por celda: no rehace la taberna, así que no se pierde ni la despensa ni las camas.
            VillageGenerator.arreglarPorcheDeLaTaberna(level, center);
            // LA ESCALERA DEL DESVÁN, QUE SE SUBE (migración 56, lo reportó el jugador: "las escaleras para el 3er
            // piso están bloqueadas por 2 bloques... se tienen que romper esos 2 bloques para que se pueda pasar").
            // El hueco de subida abría dos celdas por escalón y el techo quedaba a 2,0 de la huella: la subida de 0,6
            // que da el juego al ganar un escalón ({@code maxUpStep}) no cabía (necesita 2,4), así que los escalones
            // 2º y 3º del desván no se subían. Aquí se ensancha el hueco a tres celdas por escalón, celda por celda y
            // SOLO en las celdas del hueco (idempotente: solo quita tablones y tejas de ahí). Va también cuando el
            // jugador se hubiera roto los bloques a mano: el plano los tiene sólidos y el obrero los reponía.
            VillageGenerator.arreglarElHuecoDelDesvan(level, center);
            // Y su CAMINO desde la plaza (torcido, para no cruzar la parcela de la granja).
            VillageGenerator.caminoALaTaberna(level, center);
            // LA PESQUERA (etapa G): el lago, la caseta del pescador, su BARRIL (el puesto) y sus peces. Va antes de
            // tirar el plano, como todo lo demás, y con su camino desde la plaza (tampoco cruza ningún bancal).
            VillageGenerator.asegurarPesquera(level, center);
            VillageGenerator.caminoALaPesquera(level, center);
            // MIGRACIÓN 57: EL AGUA DEL ESTANQUE, DE VUELTA. El `farm` de arriba niveló la aldea entera y, como el
            // agua contaba como "terreno que sobra", tapó con tierra y césped el hueco del lago —la pesquera se
            // quedaba sin estanque y sin poder pescar en él (lo vio el jugador: "¿por qué la choza para pesca no
            // tiene su estanque para pescar?"), y no se reparaba sola porque el barril seguía en pie y con él
            // `pesqueraConstruida` ya la daba por hecha—. Va AQUÍ, después de todo lo que nivela, para que esta
            // migración devuelva el agua en la misma pasada. Solo toca las celdas del lago (agua, orilla y fondo) y
            // solo donde no haya nada construido: no inunda nada más. Idempotente: si el lago ya tiene agua, no hace
            // ni una escritura (y el latido la vuelve a llamar por si un día se seca otra vez).
            VillageGenerator.repararLagoDeLaPesquera(level, center);
            // LA DESPENSA, DE LA PLAZA A LA TABERNA (migración 47, lo pidió el jugador): el cofre de la comida del
            // kiosco se retira, con lo que tuviera dentro pasado ANTES a la despensa nueva (la cocina de la taberna)
            // y lo que no quepa al almacén. Va después de la taberna —que ya está construida arriba, con su cofre
            // en la cocina— y antes de tirar el plano, para que el kiosco sin cofre entre en el plano nuevo.
            VillageGenerator.retirarDespensaDelKiosco(level, center);
            // LA CAMPANA, AL CENTRO DEL KIOSCO, Y EL BEACON DEL SELLO, FUERA (migración 58, lo pidió el jugador:
            // "sitúa la campana justo en el centro del kiosco y quita el beacon pues nunca se usa"). La campana
            // estaba descentrada (una celda al oeste y al sur) y el beacon del sello ocupaba la celda central del
            // tejado sin hacer nada (un beacon sin pirámide no da efecto ni luz). Va AQUÍ, después de todo lo que
            // construye el kiosco y ANTES de tirar el plano: así el plano nuevo se captura con la campana centrada y
            // sin el beacon (si el beacon siguiera en el plano, el obrero lo repondría). Es idempotente, no rehace el
            // kiosco (su testigo es la plataforma, I15) y solo mueve/quita si el bloque sigue siendo el suyo.
            VillageGenerator.centrarLaCampanaYQuitarElBeacon(level, center);
            // LA ESCALERA DEL DORMITORIO DE LA BARRACA, QUE SE SUBE (migración 59, medida al mirar la barraca de la
            // milicia): tenía tres escalones de cuatro (el 4º lo borraba su propio hueco del forjado), una cama
            // encima del 2º, el arca sobre el último y el FACING de través. Este reparador recoloca la escalera, abre
            // el hueco que falta, cierra el tablón que sobraba, corre la cama y pasa el arca: solo esas celdas, sin
            // rehacer la barraca (rehacerla tiraría las camas y las arcas). Idempotente y antes de tirar el plano,
            // para que el plano nuevo ya traiga la escalera buena (I8).
            VillageGenerator.arreglarLaEscaleraDeLaBarraca(level, center);
            // LA MESA DE CARTOGRAFÍA DE LA BARRACA, FUERA (migración 60, la pidió quitar el jugador): es el puesto del
            // cartógrafo (un oficio que este pueblo no tiene, así que un aldeano sin oficio lo reclamaría) y caía en
            // la celda de la paca del maniquí sureste, que el constructor coloca antes y la mesa se comía. Este
            // reparador la quita solo si sigue siendo la mesa y devuelve la celda a su paca: idempotente, de una
            // celda, sin rehacer la barraca (su testigo es el hogar, I15) y antes de tirar el plano (I8).
            VillageGenerator.quitarLaMesaDeLaBarraca(level, center);
            // LA TERCERA DIANA DE LA BARRACA, DE VUELTA (migración 61): la diana suelta se colocaba en la MISMA celda
            // que el arca de la sala de armas —que se coloca después y se la comía—, así que el constructor creía
            // poner tres y en el mundo solo había dos (medido: `1371,120,1434` y `1371,121,1434`, y el plano igual).
            // El reparador la pone en su celda nueva (el rincón suroeste, pegada a las dos paredes) SOLO si está
            // vacía: idempotente, de una celda, sin rehacer la barraca (su testigo es el hogar, I15) y antes de
            // tirar el plano, para que el plano nuevo se capture ya con las tres (I8).
            VillageGenerator.moverLaDianaDeLaBarraca(level, center);
            // EL TALLER DEL LEÑADOR (etapa H, migración 62): el leñador deja de ser el recolector y pasa a ser un
            // oficio propio, así que necesita SU estación: un cobertizo abierto junto a la arboleda con la MESA DE
            // FLECHAS (el puesto del flechero) y su farol. Va aquí, con el resto de lo que construye el pueblo y antes
            // de tirar el plano (I8), y es idempotente (su testigo es la propia mesa, I15): si ya está, no escribe ni
            // una celda. Los dos puestos nuevos (el 3er granjero y el leñador) NO se siembran aquí: los repone el
            // latido al ver que sus plazas están vacías (`slotDeProfesionFaltante`), y el 3er bancal ya existe.
            VillageGenerator.asegurarElTallerDelLenador(level, center);
            // LA MINA DEL PUEBLO (etapa I, migración 70): la caseta del minero y la boca del caracol. Va ANTES de
            // tirar el plano (I8), o sea que el plano nuevo se captura ya con la caseta dentro y el obrero la
            // mantiene; el pozo y las galerías quedan fuera por `esCeldaDeLaMina`/`estaSobreElPozo` (I102).
            // Y EN LA 71 SE RETIRA LA MINA VIEJA (la del solar pegado al centro, que el jugador mandó mover al
            // descampado del noreste): se deshace ANTES de volver a construirla, o quedarían las dos casetas —la
            // vieja está en el plano y el obrero la repondría— y el pozo viejo abierto en el suelo del pueblo.
            VillageGenerator.deshacerLaMinaVieja(level, center);
            VillageGenerator.asegurarLaMinaDelPueblo(level, center);
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
        // Y LA CASETA DEL MINERO, igual de idempotente (su testigo es su suelo de piedra): así se le repone si
        // alguien se la llevó por delante. Lo que cava el minero (el pozo) es SUYO: no se toca (I102).
        VillageGenerator.asegurarLaMinaDelPueblo(level, center);
        // Y LOS HUECOS DE LAS CASAS DEL JUEGO (lo vio el jugador: "¿qué ves de extraño en esta casa? ¡si le falta
        // completarse a la pared! corrígelo y checa que el cofre no estorbe"): una pared con un boquete de 1x2 al lado
        // de la puerta que NADIE reponía, porque el plano de la aldea se capturó por escaneo del mundo y el escaneo
        // descarta el aire: el hueco no existía para el obrero. Aquí se compara cada construcción de plantilla con su
        // plantilla y se rellena lo que falte en aire (sin tocar cofres, camas ni nada que ya esté puesto), y lo
        // repuesto se apunta en el PLANO para que el obrero lo mantenga (I8).
        Map<BlockPos, BlockState> huecosTapados = VillageGenerator.cerrarHuecosDeLasCasas(level, center);
        if (!huecosTapados.isEmpty()) {
            VillageSavedData.Blueprint plano = saved.getBlueprint(objectiveIndex);
            if (plano != null) {
                for (Map.Entry<BlockPos, BlockState> celda : huecosTapados.entrySet()) {
                    plano = plano.conCelda(celda.getKey(), celda.getValue());
                }
                saved.setBlueprint(objectiveIndex, plano);
            }
            DevilRpg.LOGGER.info("[Village] Aldea {}: {} hueco(s) de las casas del juego tapados desde su plantilla",
                    objectiveIndex, huecosTapados.size());
        }
        // BARRACA de la milicia: lo mismo (idempotente). Si el jugador se llevó su suelo de piedra, se vuelve a
        // levantar entera; si está, no se toca (reconstruirla borraría las camas y lo que haya dentro).
        VillageGenerator.asegurarBarraca(level, center);
        // KIOSCO: la plataforma de la plaza con su CAMPANA en el centro (el POI de reunión del pueblo) y su farol
        // colgado del tejado. Si falta —en una aldea vieja—, se levanta. Ya NO tiene cofre: la despensa vive en la
        // cocina de la taberna desde la migración 47.
        VillageGenerator.asegurarKiosco(level, center);
        // ALMACÉN del pueblo: cobertizo con cofre doble (que crece) donde el constructor recolector va dejando lo que
        // recoge. Es una construcción aparte, al lado de la plaza.
        VillageGenerator.asegurarAlmacen(level, center);
        // Y SU REMESA INICIAL DE MADERA (128 troncos, lo pidió el jugador): el fuego del pueblo —el ahumador del
        // cocinero y la fragua del herrero— y la sierra del herrero se pagan con troncos del almacén, y una aldea
        // recién fundada no tiene ni uno hasta que el leñador tale los primeros árboles. Va AQUÍ, en la misma pasada
        // en que `asegurarAlmacen` coloca el primer cofre (el almacén todavía está vacío), y solo se le pone al
        // almacén vacío: a una aldea en marcha no se le añade nada (misma regla que la remesa de la despensa).
        // Y EL PICO DEL MINERO VA EN LA MISMA REMESA (etapa I): la mina no se puede ni empezar sin herramienta y el
        // herrero de herramientas necesita HIERRO para forjar más, que es justo lo que la mina produce: sin una
        // remesa inicial de un pico y unos lingotes, el pueblo se quedaría esperándose a sí mismo para siempre (el
        // leñador trae la madera, pero el hierro no lo trae nadie hasta que hay mina).
        VillageStorage.remesaInicialDelAlmacen(level, center);
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
        // TALLER DEL LEÑADOR (etapa H): idempotente (su testigo es la mesa de flechas). Si el jugador se lleva la mesa
        // o el tejado, el pueblo lo vuelve a levantar; así el leñador nunca se queda sin su puesto de trabajo.
        VillageGenerator.asegurarElTallerDelLenador(level, center);
        // Y LA ORILLA de la aldea de mar (islita): el terreno llano queda a la altura del agua, así que su borde sale
        // "a cuadros" (agua a la cota pegada a césped a la cota). Se saca un anillo de playa seca y pareja; en una
        // aldea de tierra adentro no toca nada (lo decide mirando si hay agua a la capa que se pisa en el anillo).
        VillageGenerator.asegurarOrilla(level, center);
        // COCINA del pueblo (etapa E): desde la etapa F vive en la taberna, con su mesa y su ahumador. Aquí solo
        // queda retirar el ahumador VIEJO del kiosco (su celda vuelve a ser la piedra de la plataforma); en el kiosco
        // ya no se pone ninguna mesa: la celda central es de la campana (migración 58).
        VillageGenerator.asegurarCocina(level, center);
        // TABERNA (etapa F): el comedor del pueblo (abajo) y la posada (arriba). Idempotente (se comprueba por su
        // barra): si el jugador se lleva media taberna, el pueblo la vuelve a levantar.
        VillageGenerator.asegurarTaberna(level, center);
        // Y LA BANDA DE SEPARACIÓN ENTRE PLANTAS DE LA TABERNA (migración 68, lo pidió el jugador: *"estaría bien que
        // la taberna tenga logs de separación entre un piso y otro… que estuviera desde el diseño en todos los lados"*):
        // la vuelta del forjado de la posada se remata con troncos de roble CLARO en los cuatro lados. La pone el
        // diseño en la taberna nueva y aquí se repone en las ya construidas (idempotente: solo cambia los tablones del
        // diseño, así que lo que el jugador ya hubiera puesto —en su partida, la banda del muro sur— se queda).
        VillageGenerator.ponerLaBandaDeLaTaberna(level, center);
        // Y EL FAROL QUE COLGABA ENCIMA DEL PRIMER ESCALON, FUERA (lo pidió el jugador: "hay que quitar esta lámpara
        // que está justo arriba de las primeras escaleras de la planta baja porque estorba al querer subir por ahí").
        // Idempotente y solo toca ese farol: vale también para las tabernas ya construidas.
        BlockPos farolDeLaEscalera = VillageGenerator.quitarElFarolDeLaEscalera(level, center);
        // Y EL FAROL QUE TAPABA UN PORTÓN, MUDADO A UN POSTE (I54). El layout viejo de las luces de la cerca del
        // corral ponía un farol en el MEDIO de cada lado de la valla, y el medio del lado oeste ES el portón: la hoja
        // lo llevaba encima y con él NADIE podía cruzar (el jugador: *"el ganadero quiere ir a la taberna y no puede,
        // la única salida está obstruida por una lámpara"*; su ganadera tenía el almacén aparcado de no poder llegar).
        List<BlockPos> fueraDelPlano = new ArrayList<>(VillageGenerator.despejarElHuecoDeLosPortones(level, center));
        // Y LAS CELDAS DE LOS DOS FAROLES QUE EL PUEBLO **QUITA** SALEN TAMBIÉN DEL PLANO (I8/I6): si el plano sigue
        // pidiendo un farol que el latido retira, el OBRERO lo repone en la pasada siguiente y el reparador lo vuelve
        // a quitar —un tira y afloja cada 10 s—. Medido en el log del jugador: *"Taberna de ...: quitado el farol de
        // encima del primer escalon (1442, 123, 1439)"* repetido toda la sesión, y `build/obras_pendientes.py` lo
        // delataba como la **única** celda pendiente de la aldea 2 (el plano la pedía y el mundo la tenía en aire).
        if (farolDeLaEscalera != null) {
            fueraDelPlano.add(farolDeLaEscalera);
        }
        if (!fueraDelPlano.isEmpty()) {
            VillageSavedData.Blueprint plano = saved.getBlueprint(objectiveIndex);
            if (plano != null) {
                for (BlockPos celda : fueraDelPlano) {
                    plano = plano.sinCelda(celda.asLong());
                }
                saved.setBlueprint(objectiveIndex, plano);
            }
            DevilRpg.LOGGER.info("[Village] Aldea {}: {} celda(s) de faroles que el pueblo retira, fuera del plano",
                    objectiveIndex, fueraDelPlano.size());
        }
        // PESQUERA (etapa G): el lago del pescador, su caseta y su barril. Idempotente (vale el agua del lago o el
        // barril como testigo): si el jugador se lleva media pesquera, el pueblo la vuelve a levantar.
        VillageGenerator.asegurarPesquera(level, center);
        // PORTONES DEL ANEXO, RED DE SEGURIDAD: si un portón del corral o del gallinero se queda abierto, el rebaño
        // se sale (lo reportó el jugador: "cuando el ganadero entra al corral, deja la puerta abierta y los animales
        // se salen"). El goal de los portones ya los cierra en cuanto el aldeano pasa, pero puede no haber ningún
        // aldeano con el portón a la vista (se fue, murió, o el chunk se descargó) e incluso quedarse abierto en el
        // guardado: medido en el suyo, el portón del corral de la aldea 2 estaba abierto (`open:true`) con la vaca y
        // la oveja del pueblo fuera. Esto mira SOLO esas dos casillas: dos bloques por latido, idempotente.
        com.chipoodle.devilrpg.entity.goal.VillagerGateGoal.vigilarPortonesDelAnexo(level, center);
        // REBAÑO: el corral se llena UNA vez (al construirlo o al migrar). Después se mantiene solo, con DOS reglas:
        //   1) RECOGER AL QUE SE ESCAPA. El corral solo tiene el portón, y el pueblo lo abre para pasar (el juego no
        //      deja que un aldeano abra una puerta de valla, de ahí `VillagerGateGoal`): con las horas, el ganado se
        //      cuela por el hueco y se pierde. Medido en el guardado del jugador: quedaba UNA vaca dentro y 8 vacas,
        //      6 ovejas, 6 gallinas y 2 puercos sueltos a 76-83 bloques del pueblo. Y un corral vacío NO DA CARNE: el
        //      ganadero no ve animales, no cría ni sacrifica, y la granja entera se muere. Los del rebaño (marcados)
        //      vuelven a casa ANDANDO, y solo si no encuentran el camino se los mete a mano (último recurso); los
        //      animales sueltos SIN marca no se tocan (pueden ser del jugador), ni los que van montados o atados.
        //   2) REPONER LA PAREJA. Si a una especie le quedan menos de dos adultos ya no puede criar NUNCA (ni carne de
        //      vaca, ni lana, ni huevos): el pueblo le trae la pareja. Lo pidió el jugador.
        // Las dos van con la espera larga de 3 días de juego, para que esto no sea un grifo de carne gratis.
        // OJO: la espera es SOLO para reponer. La PRIMERA vez (marca 0 = nunca se ha soltado el rebaño) es YA.
        // Midiendo la espera desde 0, en un mundo con menos de 3 días de juego (gameTime < 72000) el corral se
        // quedaba VACÍO PARA SIEMPRE: medido en el guardado del jugador, el anexo se construyó con el reloj del
        // mundo en 24200 (un mundo joven) y no soltó ni un animal.
        traerElRebanoALaCasa(level, center, objectiveIndex);
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
        // UNA PROFESIÓN POR ESTACIÓN (etapa H): el pueblo ADMINISTRA sus oficios. Si un oficio tiene más titulares que
        // plazas (pasa solo: una cría que crece y reclama un compuesto con el ticket libre, un aldeano que toma un
        // puesto por el bloque, o `reponerProfesiones` dando una plaza cuando el titular no estaba cargado en ese
        // latido), el que sobra PIERDE el oficio y su ticket, y vuelve al reparto como gente de sobra (la milicia o un
        // obrero). Es lo que pidió el jugador: "eliminar que haya una duplicidad de profesiones (una profesión por
        // estación permitida y administrada por el sistema de aldea)".
        podarOficiosDuplicados(level, aldeanos, objectiveIndex);
        // NOMBRES SIN REPETIR (lo vio el jugador: dos "Bibiana" en el mismo pueblo). Con 48 nombres y 11-18 aldeanos,
        // el nombre "al azar por UUID" se repite; aquí se le asigna a cada aldeano un nombre libre y se le guarda.
        repartirNombres(aldeanos);
        // Y LOS TICKETS PERDIDOS: una estación de un oficio del pueblo con el ticket COGIDO pero sin dueño vivo. El
        // caso medido (aldea 2): el compostero del TERCER bancal tenía `free_tickets=0` y ningún aldeano con él en el
        // cerebro, así que el tercer granjero (su titular) no podía reclamarlo: la estación quedaba muerta y el
        // oficio sin su puesto de trabajo (sin `JOB_SITE` vanilla no le registra la actividad de trabajar).
        soltarTicketsPerdidos(level, aldeanos, center, objectiveIndex);
        // Y CADA TITULAR, CON SU ESTACIÓN (etapa H): el clérigo y el ganadero de la aldea 2 tenían su oficio pero NO
        // su puesto de trabajo en el cerebro (el soporte de pociones estaba libre y el telar con el ticket cogido sin
        // dueño), así que vanilla no les registraba la actividad de trabajar y se quedaban en IDLE: es el fallo de
        // "el aldeano que da vueltas sobre su eje" y el rol huérfano que quedaba en el reparto.
        reclamarEstacionesDelPueblo(level, aldeanos, center, objectiveIndex);
        // OJO: LAS CAMAS NO SE REPARTEN AQUÍ. Este método (y todo `tickVillageLife`) solo corre en la aldea EN PAZ:
        // `manageNearby` lo salta entero mientras hay un asedio o un bicho dentro del recinto. Con las camas aquí
        // dentro, el aldeano al que le faltaba cama se quedaba sin ella justo la noche en que más falta hace (la del
        // asedio, con monstruos dentro) y el jugador lo veía plantado con "Sin cama" para siempre. Ahora van en
        // `atenderCamasDelPueblo`, que se llama pase lo que pase (ver `manageNearby`).
        // Y EL QUE SE QUEDA DENTRO DE UNA CASA: si lleva 30 s sin moverse de celda en un piso (o un sótano), se le baja
        // a la plaza (ver `rescatarAldeanosAtrapados`).
        rescatarAldeanosAtrapados(level, aldeanos, center);
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
        // MINERO (etapa I): el albañil vive en su caseta, al lado de la boca de la mina, y su faena es cavar el
        // caracol, abrir galerías, sacar el mineral, fundirlo, colar el adoquín en su balsa y bajarlo al almacén.
        for (Villager villager : aldeanos) {
            if (!villager.isBaby() && !VillagerGuardGoal.esGuardia(villager)
                    && villager.getVillagerData().getProfession() == VillagerProfession.MASON) {
                asegurarGoalDelMinero(villager, center, objectiveIndex);
            }
        }
        // OBREROS: puede haber VARIOS (hasta MAX_BUILDERS) repartiéndose el trabajo, y se RECALCULA quiénes son en
        // cada latido (ver más abajo), porque la marca de obrero no se le quitaba a NADIE: un granjero que fue
        // obrero cuando la aldea estaba débil (murió gente y era el único adulto) se quedaba reparando caminos para
        // siempre. El jugador lo vio: quitó un bloque del camino y fue el granjero a reponerlo en vez del obrero.
        // RECOLECTOR: el aldeano sin oficio (holgazán) se dedica SOLO a recoger cosas y guardarlas en el almacén (y a
        // mover las cadenas de suministro). El constructor, así, se dedica solo a reparar (antes llevaba los dos goals
        // y se pasaba el día recolectando) y el LEÑADOR tampoco es él: desde la etapa H la madera es un oficio aparte
        // (el flechero, ver abajo), que es lo que pidió el jugador —"dejar totalmente libre al recolector"—.
        for (Villager villager : aldeanos) {
            if (!villager.isBaby() && !VillagerGuardGoal.esGuardia(villager)
                    && villager.getVillagerData().getProfession() == VillagerProfession.NITWIT) {
                asegurarGoalDeRecolector(villager, center, objectiveIndex);
            }
        }
        // LEÑADOR (etapa H): el FLECHERO vive en el taller de la arboleda, tala árboles de verdad y los replanta, y
        // baja la madera al almacén (de donde salen los tablones, los palos, los arcos y las flechas). Va a prioridad
        // 4, como los demás oficios (antes era un segundo goal del recolector a prioridad 6, que empataba con la
        // taberna y le quitaba la comida al aldeano con hambre).
        for (Villager villager : aldeanos) {
            if (!villager.isBaby() && !VillagerGuardGoal.esGuardia(villager)
                    && villager.getVillagerData().getProfession() == VillagerProfession.FLETCHER) {
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
        // CLÉRIGO (etapa H): prepara POCIONES de verdad en su soporte de pociones con lo que el pueblo junta (el
        // recolector le deja el botín en el almacén). Era el ÚNICO rol sin goal propio del mod: su faena era la
        // actividad de trabajar de vanilla, que necesita su `JOB_SITE` (que ahora reclama el latido).
        for (Villager villager : aldeanos) {
            if (!villager.isBaby() && !VillagerGuardGoal.esGuardia(villager)
                    && villager.getVillagerData().getProfession() == VillagerProfession.CLERIC) {
                asegurarGoalDelClerigo(villager, center, objectiveIndex);
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
        // Y LAS PUERTAS DE MADERA: las van dejando abiertas al pasar (el juego tiene su comportamiento para cerrarlas
        // —`InteractWithDoor`— pero con los aldeanos del pueblo no cierra nada: el jugador las encuentra todas
        // abiertas, sobre todo al irse a dormir). El pueblo las cierra por su cuenta: ver `VillagerDoorGoal`.
        for (Villager villager : aldeanos) {
            if (!villager.isBaby()) {
                asegurarGoalDePuertas(villager);
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
        // Y EL EQUIPO, POR CERCANÍA Y EN CADA LATIDO (lo pidió el jugador: *"¡DEBEN DE ARMARSE! diario tienen que
        // checarlo, cada que se acerquen al almacén"*): al guardia que esté JUNTO al almacén se le revisa el equipo
        // AQUÍ, sin depender de que su goal consiga llevarle a un destino navegando — que es lo que estaba fallando:
        // medido con el arnés, los 5 guardias de su aldea seguían sin nada y con la marca de revisión en 0 toda la
        // corrida (nunca llegaban a `equipar`). El que pase por delante del almacén se arma; el que no, a la próxima.
        for (Villager guardia : aldeanos) {
            if (VillagerGuardGoal.esGuardia(guardia)) {
                if (!VillagerGuardGoal.equiparSiEstaCercaDelAlmacen(level, guardia, center, objectiveIndex)
                        && !guardia.getMainHandItem().is(net.minecraft.world.item.Items.BOW)) {
                    // SI LE FALTA EL EQUIPO, EL PUEBLO LE MANDA AL ALMACÉN. Hace falta porque el goal del guardia puede
                    // no estar corriendo (o quedarse atascado): medido con el arnés, los 5 guardias de su aldea se
                    // quedaban sin nada toda la corrida con el equipo esperando en el almacén. Se le manda andando, como
                    // hacen el obrero y el recolector: sin teletransportes.
                    caminarHacia(guardia, VillageStorage.puntoDeApoyo(level, center), 0.6F);
                }
            }
        }
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
        // EL ORDEN MANDA: primero el aldeano <b>SIN FAENA</b> (el holgazán/recolector: es EL constructor del pueblo, y
        // es el único al que se le pone la reparación a prioridad 3, por delante de todo), después los que YA eran
        // obreros y NO son granjeros (para no cambiarlos cada latido), después los demás adultos con otro oficio, y
        // SOLO al final los granjeros (mejor una huerta más lenta que una aldea en ruinas). A los que SOBRAN se les
        // quita la marca: el aldeano vuelve a su oficio.
        //
        // OJO: el holgazán (NITWIT, el recolector) estaba EXCLUIDO de ser obrero —y con él fuera, los tres obreros
        // eran siempre aldeanos CON oficio, que llevan la reparación a prioridad 5, o sea la última—, así que la obra
        // la hacía el que menos tiempo tenía. El propio mod dice lo contrario en `VillageStorage`: "el constructor
        // —que también es recolector—". El jugador lo notó: *"la aldea ha tenido daños en su muralla y nadie ha ido a
        // repararlo; el recolector está de flojo"*.
        int deseados = Math.max(1, Math.min(MAX_BUILDERS, adultos - 1));
        List<Villager> orden = new ArrayList<>();
        for (int pasada = 0; pasada < 5; pasada++) {
            for (Villager villager : aldeanos) {
                if (!puedeSerObrero(villager) || orden.contains(villager)) {
                    continue;
                }
                boolean yaEra = villager.getPersistentData().getBoolean(BUILDER_TAG);
                boolean esGranjero = villager.getVillagerData().getProfession() == VillagerProfession.FARMER;
                boolean sinFaena = !VillageGenerator.tieneFaenaPropia(villager.getVillagerData().getProfession());
                boolean toca = switch (pasada) {
                    case 0 -> sinFaena;               // el holgazán/recolector: el constructor del pueblo
                    case 1 -> yaEra && !esGranjero;   // los obreros de siempre que no son granjeros
                    case 2 -> !yaEra && !esGranjero;  // los demás adultos con otro oficio
                    case 3 -> yaEra;                  // un granjero que ya hacía de obrero
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
     * ¿Ese aldeano puede ser obrero? Ni crías, ni el ganadero ni el cocinero —tienen su propio puesto y el rebaño o
     * la cocina se quedan sin nadie—, ni un guardia.
     * <p>
     * El <b>holgazán</b> ({@code NITWIT}, el <b>recolector</b>) <b>SÍ</b> puede: es el aldeano <b>sin faena</b> del
     * pueblo y su reparación va a prioridad <b>3</b>, por delante de todo (ver {@code marcarObrero}), que es lo que el
     * pueblo necesita de él cuando hay obra. Estuvo excluido y el resultado fue el contrario del diseño: los obreros
     * eran aldeanos <b>con oficio</b> —que reparan a prioridad <b>5</b>, la última— y la muralla dañada se quedaba sin
     * tocar (lo reportó el jugador: *"la aldea ha tenido daños en su muralla y nadie ha ido a repararlo; el recolector
     * está de flojo"*). Con el holgazán dentro, él barre el suelo cuando <b>no hay obra</b>; mientras la haya, repara.
     */
    private static boolean puedeSerObrero(Villager villager) {
        VillagerProfession profesion = villager.getVillagerData().getProfession();
        return !villager.isBaby() && profesion != VillagerProfession.SHEPHERD
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
        //
        // SE CUENTAN de los sitios del pueblo (`VillageGenerator.puestosPorOficio()`), NO de una lista escrita aquí:
        // la lista a mano se quedó con SIETE puestos (los de la etapa E) y cuando llegaron el SEGUNDO GRANJERO
        // (etapa F) y el PESCADOR (etapa G) nadie la subió, así que esos dos oficios eran "gente de sobra" para el
        // reparto: la milicia se llevaba al pescador y al segundo granjero y la pesquera y un bancal se quedaban sin
        // nadie. Medido en el guardado del jugador (aldea 2, 10 adultos): los 4 espadachines eran los DOS
        // PESCADORES, el SEGUNDO GRANJERO (la guardia Bibiana, `9036d1d0`) y un aldeano sin oficio.
        Map<VillagerProfession, Integer> cupo = VillageGenerator.puestosPorOficio();

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
                    // SE ALTERNAN (lo pidió el jugador: "tampoco he visto ningún arquero; al crearse deberían alternarse"): con el
                    // corte por número, los cuatro primeros eran espadachines y en milicias pequeñas NO HABÍA NI UN
                    // ARQUERO. Alternando salen 4 espadachines y 3 arqueros con la milicia llena (la formación de
                    // siempre) y con dos guardias ya hay uno de cada.
                    i % 2 == 0 ? VillagerGuardGoal.ESPADACHIN : VillagerGuardGoal.ARQUERO);
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

    // --- LA MILICIA APRENDE: más vida y más daño conforme mata (I62) --------------------------------

    /**
     * <b>La vida y el daño del zombie agresivo MÁS FUERTE que puede salir</b>, con la misma cuenta que usa su
     * escalado ({@code AggressiveZombieEntity.adjustAttributesBasedOnSpawnDistance}): el factor de la distancia
     * máxima ({@code 1 + maxScaleMultiplier}) por el de la amenaza máxima
     * ({@code 1 + ThreatLevel.maxExtraDifficulty()}). Con los valores de hoy (10 de vida, 0,7 de daño, +300 % de
     * escala y +80 % de amenaza) sale <b>72 de vida y 5,04 de daño</b> (y si le toca espada de hierro, más daño).
     * <p>
     * Se calcula <b>del perfil</b> a propósito, no de números escritos aquí: es el tope que pidió el jugador
     * (*"el tope es prácticamente tan fuerte como el zombie agresivo más fuerte que puede generarse después de
     * aplicarse todas las reglas"*), y así, si un día se cambia el perfil, el tope de la milicia se mueve con él.
     */
    private static double topeDeVidaDelZombiAgresivo() {
        return com.chipoodle.devilrpg.spawnprofile.AggressiveZombieSpawnProfile.INSTANCE.baseHealth() * topeDeEscala();
    }

    private static double topeDeDanoDelZombiAgresivo() {
        return com.chipoodle.devilrpg.spawnprofile.AggressiveZombieSpawnProfile.INSTANCE.baseDamage() * topeDeEscala();
    }

    private static double topeDeEscala() {
        var perfil = com.chipoodle.devilrpg.spawnprofile.AggressiveZombieSpawnProfile.INSTANCE;
        return (1.0D + perfil.maxScaleMultiplier())
                * (1.0D + com.chipoodle.devilrpg.survival.ThreatLevel.maxExtraDifficulty());
    }

    /** Cuántos enemigos ha matado ese guardia (0 si nunca ha matado o no es guardia). */
    public static int matanzasDeGuardia(Villager villager) {
        return villager.getPersistentData().getInt(GUARD_KILLS_TAG);
    }

    /** Su <b>nivel</b> (1..): lo que se ve en su etiqueta y lo que se avisa en el log al subir. */
    public static int nivelDeGuardia(Villager villager) {
        return 1 + (int) (progresoDeGuardia(villager) / GUARD_MATANZAS_POR_NIVEL);
    }

    /**
     * <b>Lo que ha aprendido</b> ese guardia, en "matanzas equivalentes": sus enemigos muertos MÁS lo que ha entrenado
     * en la barraca (1 matanza por cada {@link #GUARD_TICKS_DE_ENTRENO_POR_MATANZA} ticks de diana). Es lo que usan el
     * nivel y los atributos, así que entrenar también se nota —despacio—.
     */
    public static double progresoDeGuardia(Villager villager) {
        int entreno = villager.getPersistentData().getInt(GUARD_TRAINING_TAG);
        return matanzasDeGuardia(villager)
                + entreno / (double) GUARD_TICKS_DE_ENTRENO_POR_MATANZA;
    }

    /**
     * <b>El guardia ha entrenado</b> esos ticks en la diana de la barraca (una sesión de faena): se le apunta y se le
     * recalculan los atributos, igual que al matar. Se llama desde su goal.
     */
    public static void sumarEntrenamiento(Villager guardia, int ticks) {
        if (ticks <= 0 || !VillagerGuardGoal.esGuardia(guardia)) {
            return;
        }
        int antes = (int) progresoDeGuardia(guardia);
        var datos = guardia.getPersistentData();
        datos.putInt(GUARD_TRAINING_TAG, datos.getInt(GUARD_TRAINING_TAG) + ticks);
        aplicarLoAprendido(guardia);
        int ahora = (int) progresoDeGuardia(guardia);
        if (ahora != antes) {
            DevilRpg.LOGGER.info("[Village] {} (guardia) sube al nivel {} entrenando en la barraca: {} de {} matanzas"
                            + " equivalentes (vida {} y dano {})", guardia.getName().getString(),
                    nivelDeGuardia(guardia), redondear(progresoDeGuardia(guardia)), GUARD_MATANZAS_PARA_EL_TOPE,
                    redondear(guardia.getAttribute(Attributes.MAX_HEALTH) == null ? 0.0D
                            : guardia.getAttribute(Attributes.MAX_HEALTH).getValue()),
                    redondear(guardia.getAttribute(Attributes.ATTACK_DAMAGE) == null ? 0.0D
                            : guardia.getAttribute(Attributes.ATTACK_DAMAGE).getValue()));
        }
    }

    /**
     * <b>Un guardia ha matado a un enemigo</b>: se le apunta y se le aplica lo aprendido. El aviso de subida de
     * nivel sale en el log (y en su etiqueta, que lleva el nivel), que es como el jugador lo ve.
     */
    public static void sumarMatanzaDeGuardia(Villager guardia) {
        var datos = guardia.getPersistentData();
        int antes = datos.getInt(GUARD_KILLS_TAG);
        int ahora = antes + 1;
        datos.putInt(GUARD_KILLS_TAG, ahora);
        aplicarLoAprendido(guardia);
        if (antes / GUARD_MATANZAS_POR_NIVEL != ahora / GUARD_MATANZAS_POR_NIVEL) {
            DevilRpg.LOGGER.info("[Village] {} (guardia) sube al nivel {}: {} enemigo(s) y sus atributos son vida {}"
                            + " y dano {} (tope de la milicia: vida {} y dano {})",
                    guardia.getName().getString(), nivelDeGuardia(guardia), ahora,
                    redondear(guardia.getAttribute(Attributes.MAX_HEALTH) == null ? 0.0D
                            : guardia.getAttribute(Attributes.MAX_HEALTH).getValue()),
                    redondear(guardia.getAttribute(Attributes.ATTACK_DAMAGE) == null ? 0.0D
                            : guardia.getAttribute(Attributes.ATTACK_DAMAGE).getValue()),
                    redondear(topeDeVidaDelZombiAgresivo()), redondear(topeDeDanoDelZombiAgresivo()));
        }
    }

    private static double redondear(double valor) {
        return Math.round(valor * 100.0D) / 100.0D;
    }

    /**
     * <b>LO QUE HA APRENDIDO ESE GUARDIA, EN SUS ATRIBUTOS.</b> Idempotente: se <b>recalcula</b> desde su contador de
     * matanzas (nunca se acumula sobre el valor anterior, que es lo que convertiría el latido en una máquina de
     * subirle la vida para siempre). La vida va de la de un aldeano (20) al tope del zombie más fuerte (72) y el daño
     * de 2 a su daño (5,04, que <b>sin contar</b> la espada: con la de hierro el guardia pega ~9).
     * <p>
     * Cuando la vida máxima sube, se le suma a la <b>actual</b> lo mismo que ha subido la máxima: el guardia se
     * fortalece sin curarse del todo (sigue con las heridas de la pelea).
     */
    public static void aplicarLoAprendido(Villager guardia) {
        if (!VillagerGuardGoal.esGuardia(guardia)) {
            return;
        }
        double avance = Math.min(1.0D, progresoDeGuardia(guardia) / (double) GUARD_MATANZAS_PARA_EL_TOPE);
        double vidaAldeano = 20.0D;
        double danoAldeano = 2.0D;
        double vida = vidaAldeano + (topeDeVidaDelZombiAgresivo() - vidaAldeano) * avance;
        double dano = danoAldeano + (topeDeDanoDelZombiAgresivo() - danoAldeano) * avance;
        var attrVida = guardia.getAttribute(Attributes.MAX_HEALTH);
        if (attrVida != null && Math.abs(attrVida.getBaseValue() - vida) > 0.01D) {
            double ganado = vida - attrVida.getBaseValue();
            attrVida.setBaseValue(vida);
            if (ganado > 0.0D) {
                guardia.setHealth(Math.min((float) vida, guardia.getHealth() + (float) ganado));
            } else if (guardia.getHealth() > vida) {
                guardia.setHealth((float) vida);
            }
        }
        var attrDano = guardia.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attrDano != null && Math.abs(attrDano.getBaseValue() - dano) > 0.01D) {
            attrDano.setBaseValue(dano);
        }
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
        // Y SE ESPEJA EN LA MARCA SINCRONIZADA (ver `VillagerGuardGoal.sincronizarMarcaDeGuardia`): es lo que hace que
        // el CLIENTE sepa que este aldeano es de la milicia y el render le ponga el modelo con su armadura y su arma.
        // Sin esto, `esGuardia` daba false en el cliente (los datos persistentes no viajan) y los guardias se veían
        // como aldeanos normales, sin equipo (lo pidió el jugador).
        VillagerGuardGoal.sincronizarMarcaDeGuardia(villager);
        // Y EL GRANJERO MANDA SOBRE EL MILITAR (lo pidió el jugador: *"recuerda que tiene prioridad el granjero que el
        // militar a la hora de asignar"*, viendo 3 parcelas con solo 2 granjeros): un guardia NO ocupa una plaza de
        // oficio del pueblo. Antes conservaba su oficio —y con él el ticket de su estación—, así que una plaza de
        // granjero quedaba "cubierta" por un guardia que no pisaba el bancal: el reparto veía 3 granjeros, el tercer
        // bancal se quedaba sin nadie y un aldeano nuevo no podía reclamar la estación (el ticket era del guardia).
        // Ahora se le suelta la estación y se queda SIN OFICIO: su plaza queda libre para un granjero de verdad, y si
        // un día deja la guardia, el reparto le da otra (o la misma).
        if (VillageGenerator.esOficioDelPueblo(villager.getVillagerData().getProfession())) {
            liberarPuesto(villager);
            villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NONE));
            villager.refreshBrain(level);
        }
        // Y LO QUE HA APRENDIDO ESE GUARDIA (I62): sus atributos se recalculan desde su contador de matanzas. Es
        // idempotente (no acumula) y va aquí, en el latido, para que un guardia recargado del guardado —o uno al que
        // le cambiara el tope— vuelva a tener lo suyo.
        aplicarLoAprendido(villager);
        // Un obrero que pasa a la guardia deja de ser obrero (tiene su puesto).
        desmarcarObrero(villager);
        // Y TAMBIÉN LA RECOGIDA DE SU OFICIO: los dos goals piden MOVE y el de recoger se engancha ANTES que el de la
        // guardia, así que con la misma prioridad (3) el empate lo ganaba el de recoger y el guardia se pasaba el
        // rato barriendo el término del pueblo y llendo al almacén antes que patrullar (medido en el orden de
        // inserción: el reparto de recogida va antes del alistamiento). "Un guardia tiene su puesto": recoger es de
        // los demás, y son los que barren el suelo.
        for (WrappedGoal wrapped : List.copyOf(villager.goalSelector.getAvailableGoals())) {
            if (wrapped.getGoal() instanceof com.chipoodle.devilrpg.entity.goal.VillagerPickupGoal) {
                villager.goalSelector.removeGoal(wrapped.getGoal());
            }
        }
        // OJO: el puesto de TRABAJO no se le toca. Se probó (medido con el arnés): quitarle el `JOB_SITE` y el oficio
        // para que el cerebro no mantuviera la actividad de trabajar sale CARO — `VillagerProfession.NONE` tiene por
        // predicado de puesto adquirible `ALL_ACQUIRABLE_JOBS`, así que el aldeano se pone a BUSCAR estación entre las
        // 48 casillas de alrededor, la reclama y vuelve a tener oficio (el guardia recuperaba su composter en cada
        // latido, y de paso le podía quitar el puesto a un oficio del pueblo). El guardia conserva su composter (o el
        // que tuviera) y el cerebro tira de él solo en los huecos entre rondas, que con el puesto de ronda arreglado
        // son raros.
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
        // La marca SINCRONIZADA también se pone a cero: el cliente deja de verlo como guardia y el render vuelve a
        // dibujarlo como aldeano normal (con su ropa de oficio) en el acto.
        VillagerGuardGoal.sincronizarMarcaDeGuardia(villager);
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
            // LA GUARDIA NO CUENTA COMO TITULAR DE UN OFICIO (ver `alistarGuardia`): su plaza está libre para un
            // trabajador de verdad, así que no se apunta en la lista de "oficios cubiertos".
            if (VillagerGuardGoal.esGuardia(villager)) {
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
                // OJO: ANTES de borrar la memoria hay que SOLTAR el ticket del punto de interés. Borrar la memoria a
                // secas deja el puesto COGIDO PARA SIEMPRE (el juego solo lo suelta al morir el aldeano, en
                // `Villager.releaseAllPois`), y entonces ningún aldeano puede volver a reclamarlo. Medido en el
                // guardado del jugador (aldea 2): el muelle de afilar y la mesa de herrería tenían `free_tickets=0` y
                // NINGÚN aldeano con ese sitio en la memoria, así que el herrero de armas tenía el puesto solo como
                // `POTENTIAL_JOB_SITE` y el de herramientas, ni eso.
                liberarPuesto(villager);
                DevilRpg.LOGGER.info("[Village] Aldea {}: un aldeano habia tomado el oficio de {} (de fuera del"
                        + " pueblo): vuelve al reparto de puestos", objectiveIndex, profesion);
            } else {
                // Y SE CUENTA UNO POR TITULAR, NO UNO POR OFICIO. `slotDeProfesionFaltante` **gasta una plaza por cada
                // vivo** con ese oficio (el pueblo tiene TRES granjeros), así que la lista tiene que llevar una entrada
                // por aldeano. Con el `add` de antes (solo la primera vez, `!presentes.contains`) el pueblo veía
                // cubierto el primer bancal y creía libres los otros dos: daba de alta un granjero de MÁS en cada
                // latido y `podarOficiosDuplicados` —que sí cuenta titulares— se lo quitaba acto seguido. Es el bucle
                // de 10 s que el jugador vio en su log: "aldeano sin oficio recupera el puesto de farmer" +
                // "f033ee63 tenia el oficio de farmer de mas (el pueblo tiene 3 plaza(s))".
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

    /**
     * <b>Suelta el ticket</b> del puesto de trabajo (y de la cita pendiente) antes de quitarle la memoria al aldeano.
     * <p>
     * Es lo que hace el propio juego cuando un aldeano muere ({@code Villager.releaseAllPois}) y lo que
     * {@code Brain.eraseMemory} <b>no</b> hace: la memoria se va, pero el punto de interés sigue <b>cogido</b>, así
     * que <b>nadie</b> puede volver a reclamarlo nunca (vanilla solo da un ticket por puesto).
     * <p>
     * Medido en el guardado del jugador (aldea 2, centro 1414,1414): el <b>muelle de afilar</b> (1419,120,1368) y la
     * <b>mesa de herrería</b> (1418,120,1368) tenían {@code free_tickets=0} y ningún aldeano con ese sitio en la
     * memoria — el único herrero de armas lo tenía como {@code POTENTIAL_JOB_SITE} (encontrado, imposible de
     * reclamar) y el de herramientas, ni eso. Sin {@code JOB_SITE} el cerebro <b>no registra la actividad de
     * trabajar</b> (vanilla la condiciona a esa memoria), el aldeano se queda en IDLE todo el día y el jugador lo ve
     * "dando vueltas" con la etiqueta "Paseando".
     */
    private static void liberarPuesto(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return;
        }
        PoiManager poi = level.getPoiManager();
        for (MemoryModuleType<GlobalPos> tipo : List.of(MemoryModuleType.JOB_SITE, MemoryModuleType.POTENTIAL_JOB_SITE)) {
            Optional<GlobalPos> sitio = villager.getBrain().getMemory(tipo);
            if (sitio.isEmpty()) {
                continue;
            }
            // OJO: `release` **revienta** si en esa celda ya no hay punto de interés —`IllegalStateException: POI never
            // registered at ...`, medido al mover el compostero del bancal (migración 64): la memoria apuntaba al
            // compostero viejo, que ya no existe—. Un puesto que ya no está no hay que soltarlo: basta con borrar la
            // memoria, y así el reparto le da otro.
            if (poi.getType(sitio.get().pos()).isPresent()) {
                poi.release(sitio.get().pos());
            }
            villager.getBrain().eraseMemory(tipo);
        }
    }

    /**
     * Le da a ese aldeano su <b>puesto NUEVO</b> cuando la estación se ha <b>movido</b> (migración 64: el compostero
     * del bancal pasa una celda más afuera).
     * <p>
     * Es mejor que soltarle el puesto y esperar a que el latido se lo vuelva a dar: la estación es <b>suya</b> (I36),
     * así que no tiene por qué cambiar de bancal, y no se queda sin ella si el POI nuevo tarda en registrarse.
     * <b>Medido con el arnés</b>: soltando el puesto, de los tres granjeros <b>dos</b> lo recuperaron y la tercera se
     * quedó {@code SIN PUESTO} (con su faena y su etiqueta, pero sin estación: el cerebro no le registra la actividad
     * de trabajar, I23).
     */
    private static void moverPuestoDeTrabajo(ServerLevel level, Villager villager, BlockPos viejo, BlockPos nuevo) {
        PoiManager poi = level.getPoiManager();
        if (poi.getType(viejo).isPresent()) {
            poi.release(viejo); // (si en la celda vieja ya no hay POI, `release` reventaría: ver `liberarPuesto`)
        }
        java.util.function.Predicate<net.minecraft.core.Holder<
                net.minecraft.world.entity.ai.village.poi.PoiType>> vale =
                villager.getVillagerData().getProfession().heldJobSite();
        java.util.function.BiPredicate<net.minecraft.core.Holder<
                net.minecraft.world.entity.ai.village.poi.PoiType>, BlockPos> cual = (tipo, pos) -> pos.equals(nuevo);
        if (poi.take(vale, cual, nuevo, 1).isEmpty()) {
            liberarPuesto(villager); // no se ha podido coger el nuevo: se suelta y el latido lo reintenta
            return;
        }
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), nuevo));
        villager.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
        DevilRpg.LOGGER.info("[Village] {}: su estacion se muda con el compostero ({} -> {})", villager.getUUID(),
                viejo.toShortString(), nuevo.toShortString());
    }

    /**
     * <b>Una profesión por estación</b>: quita el oficio a los titulares que <b>sobran</b> de cada oficio del pueblo.
     * <p>
     * Las plazas de cada oficio son las de {@link VillageGenerator#puestosPorOficio()} (tres granjeros, un pescador,
     * un leñador...). Se cuentan los titulares <b>cargados</b> y, si hay más que plazas, los que sobran (en orden
     * <b>estable</b> por UUID, para que no cambie quién se queda en cada latido) pierden el oficio:
     * <ul>
     *   <li>se les <b>suelta el ticket</b> de su estación ({@link #liberarPuesto}: si no, el puesto se queda cogido
     *       para siempre y su titular legítimo no puede reclamarlo, el fallo medido en I23),</li>
     *   <li>se quedan <b>SIN OFICIO</b> (su goal viejo queda inerte: todos los goals de oficio releen la profesión) y
     *       vuelven al reparto: plaza libre si la hay y, si no, <b>gente de sobra</b> (la milicia o un obrero).</li>
     * </ul>
     * De dónde salen los duplicados (medido): (a) el oficio también lo da <b>el bloque</b> —cualquier aldeano sin
     * oficio reclama una estación libre y el juego le pone ese oficio—, (b) <b>`reponerProfesiones`</b> puede dar una
     * plaza cuando el titular está en un chunk descargado o fuera del radio de conteo, y (c) un aldeano <b>curado</b>
     * vuelve con su profesión vieja. Es lo que el jugador vio con dos pescadores en la aldea 2 (una plaza).
     */
    private static void podarOficiosDuplicados(ServerLevel level, List<Villager> aldeanos, int objectiveIndex) {
        Map<VillagerProfession, Integer> cupo = VillageGenerator.puestosPorOficio();
        Map<VillagerProfession, List<Villager>> porOficio = new HashMap<>();
        for (Villager villager : aldeanos) {
            if (villager.isBaby()) {
                continue; // las crías no tienen oficio (y no gastan plaza)
            }
            porOficio.computeIfAbsent(villager.getVillagerData().getProfession(), k -> new ArrayList<>()).add(villager);
        }
        for (Map.Entry<VillagerProfession, List<Villager>> entrada : porOficio.entrySet()) {
            int plazas = cupo.getOrDefault(entrada.getKey(), 0);
            List<Villager> titulares = entrada.getValue();
            if (plazas <= 0 || titulares.size() <= plazas) {
                continue; // oficio de fuera del pueblo: de eso se encarga `reponerProfesiones`
            }
            titulares.sort(Comparator.comparing(v -> v.getUUID().toString()));
            for (int i = plazas; i < titulares.size(); i++) {
                Villager sobrante = titulares.get(i);
                liberarPuesto(sobrante);
                sobrante.getBrain().eraseMemory(MemoryModuleType.SECONDARY_JOB_SITE);
                sobrante.setVillagerData(sobrante.getVillagerData().setProfession(VillagerProfession.NONE));
                sobrante.refreshBrain(level); // con el oficio nuevo se le rehacen los comportamientos por defecto
                DevilRpg.LOGGER.info("[Village] Aldea {}: {} tenia el oficio de {} de mas (el pueblo tiene {} plaza(s)):"
                        + " se queda sin oficio y pasa a gente de sobra (milicia u obrero)", objectiveIndex,
                        sobrante.getUUID(), entrada.getKey(), plazas);
            }
        }
    }

    /**
     * <b>Soltar los tickets PERDIDOS</b> de las estaciones del pueblo: un puesto de trabajo con el ticket cogido
     * ({@code free_tickets = 0}) pero <b>sin ningún aldeano que lo tenga en el cerebro</b>. El juego solo suelta el
     * ticket al morir el aldeano (I23), así que un ticket perdido deja la estación <b>muerta para siempre</b>: su
     * titular legítimo no puede reclamarla y el oficio se queda sin puesto de trabajo (sin {@code JOB_SITE} vanilla
     * no le registra la actividad de trabajar y el aldeano cae a IDLE).
     * <p>
     * Medido en el guardado del jugador (aldea 2): el <b>compostero del tercer bancal</b> tenía {@code free_tickets=0}
     * y nadie con él en la memoria —ni {@code JOB_SITE} ni {@code POTENTIAL_JOB_SITE}—, así que el <b>tercer
     * granjero</b> (su titular) no podía reclamarlo. La consulta es general (por el tipo de puesto del oficio, con
     * {@code heldJobSite}), así que vale para cualquier oficio del pueblo, sin listas de coordenadas.
     * <p>
     * Y solo se sueltan los de los oficios a los que les <b>falta gente</b> (cupo contra titulares): si el oficio está
     * cubierto, su estación se deja en paz —puede ser de un aldeano que ahora mismo está en un chunk descargado, y no
     * se le quita el puesto a nadie por eso—.
     */
    private static void soltarTicketsPerdidos(ServerLevel level, List<Villager> aldeanos, BlockPos center,
                                              int objectiveIndex) {
        Map<VillagerProfession, Integer> cupo = VillageGenerator.puestosPorOficio();
        Map<VillagerProfession, Integer> titulares = new HashMap<>();
        Set<Long> reclamados = new HashSet<>();
        for (Villager villager : aldeanos) {
            if (villager.isBaby()) {
                continue;
            }
            VillagerProfession profesion = villager.getVillagerData().getProfession();
            if (VillageGenerator.esOficioDelPueblo(profesion)) {
                titulares.merge(profesion, 1, Integer::sum);
            }
            for (MemoryModuleType<GlobalPos> tipo : List.of(MemoryModuleType.JOB_SITE,
                    MemoryModuleType.POTENTIAL_JOB_SITE)) {
                villager.getBrain().getMemory(tipo).ifPresent(sitio -> reclamados.add(sitio.pos().asLong()));
            }
        }
        PoiManager poi = level.getPoiManager();
        for (Map.Entry<VillagerProfession, Integer> entrada : cupo.entrySet()) {
            if (titulares.getOrDefault(entrada.getKey(), 0) >= entrada.getValue()) {
                continue; // ese oficio tiene toda su gente: su estación no se toca
            }
            for (var registro : poi.getInSquare(entrada.getKey().heldJobSite(), center,
                    VillageGenerator.FENCE_RADIUS, PoiManager.Occupancy.IS_OCCUPIED).toList()) {
                BlockPos puesto = registro.getPos();
                if (reclamados.contains(puesto.asLong())) {
                    continue; // alguien lo tiene de verdad en el cerebro
                }
                poi.release(puesto);
                DevilRpg.LOGGER.info("[Village] Aldea {}: el puesto de {} de {} estaba cogido SIN dueno: se suelta"
                        + " para que lo reclame su titular", objectiveIndex, entrada.getKey(), puesto);
            }
        }
    }

    /**
     * <b>Cada titular, con SU estación</b> (etapa H): un aldeano con un oficio del pueblo que <b>no tiene
     * {@code JOB_SITE}</b> va y <b>reclama</b> el puesto de su oficio (el más cercano que esté libre y, si no hay
     * ninguno libre, uno con el <b>ticket perdido</b>: cogido y sin dueño, que se suelta y se vuelve a coger).
     * <p>
     * Hace falta porque el oficio y el puesto de trabajo son dos cosas distintas y hay aldeanos con el oficio pero sin
     * puesto: sin {@code JOB_SITE} vanilla <b>no le registra la actividad de trabajar</b> y el aldeano cae a IDLE (el
     * fallo de "da vueltas sobre su eje" que el jugador vio con el herrero). Medido en su guardado (aldea 2): el
     * <b>clérigo</b> tenía su soporte de pociones <b>libre</b> (nadie lo había reclamado) y el <b>ganadero</b> su
     * telar con el ticket cogido sin dueño. Y no se le quita el puesto a nadie: si otro aldeano <b>cargado</b> lo
     * tiene en el cerebro, ese puesto se respeta.
     */
    private static void reclamarEstacionesDelPueblo(ServerLevel level, List<Villager> aldeanos, BlockPos center,
                                                    int objectiveIndex) {
        PoiManager poi = level.getPoiManager();
        for (Villager villager : aldeanos) {
            if (villager.isBaby()) {
                continue;
            }
            if (villager.getBrain().hasMemoryValue(MemoryModuleType.JOB_SITE)) {
                // UN PUESTO QUE YA NO ESTÁ NO SE QUEDA COGIDO (I23): si la estación que tiene en la memoria ya no es
                // un punto de interés (se la movieron —el compostero del bancal, migración 64— o se la quitó el
                // jugador), se le suelta aquí mismo y este mismo latido le da otra. Sin esto el aldeano se queda con
                // una memoria que apunta al aire y **no vuelve a reclamar nunca** (porque el reparto solo mira a los
                // que NO tienen puesto). Medido con el arnés: al mover el compostero, una de las tres granjeras se
                // quedó `SIN PUESTO` para siempre.
                Optional<GlobalPos> suyo = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
                if (suyo.isEmpty() || poi.getType(suyo.get().pos()).isPresent()) {
                    continue; // su puesto sigue ahí: no se le toca
                }
                liberarPuesto(villager);
            }
            VillagerProfession profesion = villager.getVillagerData().getProfession();
            if (!VillageGenerator.esOficioDelPueblo(profesion)) {
                continue;
            }
            java.util.function.Predicate<net.minecraft.core.Holder<net.minecraft.world.entity.ai.village.poi.PoiType>>
                    vale = profesion.heldJobSite();
            // SE MIRAN TODAS LAS ESTACIONES DE SU OFICIO, ORDENADAS POR CERCANÍA AL ALDEANO, Y SE ELIGE ASÍ:
            //   1) una LIBRE,
            //   2) si no hay, una OCUPADA **SIN DUEÑO** (el ticket perdido: alguien lo cogió y ya no está),
            //   3) y solo si no hay otra cosa, la ocupada más cercana (y el `deOtro` de abajo decide).
            // OJO CON EL PASO 2, que es el que estaba mal: antes se cogía **la ocupada más cercana** y, si era de
            // otro aldeano, se abandonaba. Con el compostero del BANCAL 0 con el ticket perdido y el del bancal 1
            // (más cerca del centro, de donde salía la búsqueda) en manos de otro granjero, el TERCER granjero se
            // quedaba **SIN PUESTO PARA SIEMPRE** y su bancal sin cosechar: medido con el arnés, el bancal 0 se
            // quedaba con **37 plantas maduras** que no bajaban ni una en cuatro minutos, con el compostero libre
            // (`poi=SI`, `dueño: NADIE`) y la granjera con `job=SIN PUESTO`. Es el reporte del jugador: *"otra vez
            // los granjeros están dejando demasiadas parcelas sin cosechar... ya no hay verduras para comer"*.
            List<BlockPos> estaciones = new ArrayList<>();
            Set<Long> conEspacio = new HashSet<>();
            for (var registro : poi.getInSquare(vale, center, VillageGenerator.FENCE_RADIUS,
                    PoiManager.Occupancy.HAS_SPACE).toList()) {
                conEspacio.add(registro.getPos().asLong());
            }
            for (var registro : poi.getInSquare(vale, center, VillageGenerator.FENCE_RADIUS,
                    PoiManager.Occupancy.ANY).toList()) {
                estaciones.add(registro.getPos());
            }
            estaciones.sort(Comparator.comparingDouble(p ->
                    villager.distanceToSqr(p.getX() + 0.5D, p.getY() + 0.5D, p.getZ() + 0.5D)));
            BlockPos puesto = null;
            for (BlockPos p : estaciones) {
                if (conEspacio.contains(p.asLong())) {
                    puesto = p; // libre
                    break;
                }
            }
            boolean libre = puesto != null;
            if (puesto == null) {
                for (BlockPos p : estaciones) {
                    if (!reclamadoPorAlguien(aldeanos, p)) {
                        puesto = p; // ocupada SIN dueño (ticket perdido): se suelta y se coge
                        break;
                    }
                }
            }
            if (puesto == null && !estaciones.isEmpty()) {
                puesto = estaciones.get(0); // no hay otra: la más cercana
            }
            if (puesto == null) {
                continue; // su oficio no tiene estación construida (todavía): no hay nada que reclamar
            }
            boolean deOtro = false;
            for (Villager otro : aldeanos) {
                if (otro == villager) {
                    continue;
                }
                var suyo = otro.getBrain().getMemory(MemoryModuleType.JOB_SITE);
                if (suyo.isPresent() && suyo.get().pos().equals(puesto)) {
                    deOtro = true;
                    break;
                }
            }
            if (deOtro) {
                continue; // es de otro aldeano que está aquí: no se le quita
            }
            if (!libre) {
                poi.release(puesto); // ticket perdido: se suelta y se vuelve a coger (I23)
            }
            final BlockPos elegido = puesto;
            java.util.function.BiPredicate<net.minecraft.core.Holder<
                    net.minecraft.world.entity.ai.village.poi.PoiType>, BlockPos> cual =
                    (tipo, pos) -> pos.equals(elegido);
            if (poi.take(vale, cual, elegido, 1).isEmpty()) {
                continue; // no se ha podido (el chunk no está cargado...): se reintenta en el latido siguiente
            }
            villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), elegido));
            villager.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
            DevilRpg.LOGGER.info("[Village] Aldea {}: {} reclama su estacion de {} en {}", objectiveIndex,
                    villager.getUUID(), profesion, elegido.toShortString());
        }
    }

    /** ¿Ese puesto lo tiene alguien <b>en el cerebro</b> (de puesto o solo como posible)? Es el "ticket perdido". */
    private static boolean reclamadoPorAlguien(List<Villager> aldeanos, BlockPos puesto) {
        for (Villager v : aldeanos) {
            for (MemoryModuleType<GlobalPos> tipo : List.of(MemoryModuleType.JOB_SITE,
                    MemoryModuleType.POTENTIAL_JOB_SITE)) {
                var suyo = v.getBrain().getMemory(tipo);
                if (suyo.isPresent() && suyo.get().pos().equals(puesto)) {
                    return true;
                }
            }
        }
        return false;
    }

    // --- LAS CAMAS DEL PUEBLO: cada aldeano con la suya (como las estaciones de trabajo) ---------------------

    /** Radio en el que se busca cama para un aldeano sin cama (el mismo que usa vanilla para adquirir POIs). */
    private static final int RADIO_CAMA = 48;
    /** Hasta dónde se le deja quedarse de su cama para acostarle desde el latido (bloques): ver `acostarAlQueNoLlega`. */
    private static final int RADIO_ACOSTARSE = 6;
    /**
     * <b>Celda de espera</b> de cada aldeano al que se le ha dado una cama a la que el planificador no llega: la celda
     * (a la que SÍ llega y desde la que ve la cama) a la que se le manda para acostarle. Se calcula al darle la cama y
     * se usa en {@link #acostarAlQueNoLlega}.
     */
    private static final Map<UUID, BlockPos> ESPERA_PARA_DORMIR = new java.util.concurrent.ConcurrentHashMap<>();
    /** Cuántas camas se prueban antes de rendirse (las más cercanas <b>al aldeano</b>): con 8 sobra en una aldea. */
    private static final int CAMAS_A_PROBAR = 8;
    /**
     * Latidos seguidos ({@code VILLAGE_POLL_TICKS} cada uno) en los que un aldeano, en su hora de descanso, no
     * consigue ni acercarse a su cama, antes de <b>darle otra</b>: se le suelta la cama (con su ticket, I23) y se le
     * apunta como punto fallido (I33) para que el reparto no se la vuelva a dar y le busque una que sí alcance.
     */
    private static final int LATIDOS_PARA_RENUNCIAR_A_LA_CAMA = 6;
    /**
     * Seguimiento de un aldeano que <b>no consigue llegar a su cama</b>: la cama que persigue, lo más cerca que ha
     * estado de ella y los latidos seguidos <b>sin acercarse</b> (la misma regla que I3: atascado es no acercarse).
     * Se olvida al acostarse, al cambiar de cama y al renunciar a ella (ver {@link #acostarAlQueNoLlega}).
     */
    private record SinLlegar(BlockPos cama, double mejor, int fallos) {
    }

    /** Latidos seguidos sin acercarse a la cama, por aldeano (ver {@link #acostarAlQueNoLlega}). */
    private static final Map<UUID, SinLlegar> SIN_LLEGAR_A_LA_CAMA = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * <b>El reparto de camas del pueblo, pase lo que pase</b>: se le da cama al aldeano que no tiene y se acuesta al
     * que no consigue dar el último paso. La llama {@code manageNearby} en su latido <b>sin</b> las guardas de
     * "aldea en paz".
     * <p>
     * <b>POR QUÉ NO PUEDE IR CON EL RESTO DEL LATIDO</b> (lo reportó el jugador: *"Mauricio sigue sin ir a buscar cama
     * y hay varias en la taberna"*, con la etiqueta <b>"Sin cama"</b> y de noche con los bichos dentro). El latido
     * entero —{@code tickVillageLife}, y con él {@code prepareRepairs}, donde vivía el reparto de camas— está detrás
     * de dos guardas: {@code !isUnderAttack(...)} y {@code hayEnemigosDentro(...) -> continue}. Las dos tienen sentido
     * para lo que repuebla y para lo que administra oficios (no se repone gente mientras los monstruos la están
     * matando), pero <b>la cama no</b>: la noche del asedio es justo cuando hace falta. Un aldeano al que le faltaba
     * cama se quedaba sin ella durante todo el asedio y, como el hambre y la edad tampoco corrían, el estado se
     * quedaba congelado con él de pie y sin cama a la vista del jugador.
     */
    private static void atenderCamasDelPueblo(ServerLevel level, BlockPos center, int objectiveIndex) {
        List<Villager> aldeanos = level.getEntitiesOfClass(Villager.class, new AABB(center).inflate(FALLEN_CHECK_RADIUS));
        reclamarCamasDelPueblo(level, aldeanos, center);
        acostarAlQueNoLlega(level, aldeanos);
        // Y SI ALGUIEN SE QUEDA SIN CAMA, SE DICE EN EL LOG CON NOMBRE Y MOTIVO (una vez por cambio, no cada latido):
        // es la queja del jugador y así se ve de un vistazo si es que no hay camas, si están todas cogidas o si la que
        // le toca no le sirve.
        StringBuilder sinCama = new StringBuilder();
        int censados = 0;
        for (Villager villager : aldeanos) {
            censados++;
            if (!villager.getBrain().hasMemoryValue(MemoryModuleType.HOME)) {
                sinCama.append(sinCama.isEmpty() ? "" : ", ").append(nombreDe(villager))
                        .append(villager.isBaby() ? " (cria)" : "");
            }
        }
        String clave = sinCama.toString();
        String recordado = SIN_CAMA_LOGGED.getOrDefault(level, "");
        if (!clave.equals(recordado)) {
            SIN_CAMA_LOGGED.put(level, clave);
            if (clave.isEmpty()) {
                DevilRpg.LOGGER.info("[Village] Aldea {}: los {} aldeanos (crias incluidas) tienen cama",
                        objectiveIndex, censados);
            } else {
                DevilRpg.LOGGER.warn("[Village] Aldea {}: SIN CAMA {} de {} aldeanos (camas del pueblo: {})",
                        objectiveIndex, clave, censados, contarCamas(level, center));
            }
        }
    }

    /** Último aviso de "sin cama" de cada mundo, para no repetir el mismo WARN en cada latido. */
    private static final Map<ServerLevel, String> SIN_CAMA_LOGGED = new HashMap<>();

    /**
     * <b>Cada aldeano, con SU CAMA</b>: al que no tiene {@code HOME} se le reclama una cama del pueblo.
     * <p>
     * Hace falta porque la aldea <b>no administraba las camas</b> y el juego solo se las da a quien pilla un rato
     * ocioso en la franja en que vanilla las reclama: el jugador lo vio con dos granjeros (etiqueta <b>"Sin cama"</b>
     * encima, de pie en la huerta toda la noche) y lo medimos en su guardado: <b>29 POIs de cama</b> alrededor de la
     * plaza para <b>12 aldeanos</b>, y <b>3 sin cama reclamada</b>. Sin cama, en la franja de descanso el aldeano no
     * tiene a dónde ir: se queda <b>plantado donde le pilló la noche</b> (y el mod le pone "Sin cama", que es lo que
     * avisa de que falta algo).
     * <p>
     * <b>TAMBIÉN LAS CRÍAS</b> (lo preguntó el jugador: *"Mauricio sigue sin ir a buscar cama y hay varias en la
     * taberna"*). Mauricio es una <b>cría</b> —de día su etiqueta dice «Mauricio (Sin oficio) · Jugando»— y el reparto
     * las <b>saltaba a propósito</b> («una cría duerme con el pueblo»), pero eso no se sostiene: vanilla sí permite que
     * una cría reclame cama (Ubaldo y Nicasio, también crías de esa misma aldea, la tienen) y la etiqueta de la
     * <b>noche</b> le decía al jugador <b>"Sin cama"</b>, porque la rama de descanso se mira <b>antes</b> que la de
     * cría. Dos crías de la aldea se quedaban, pues, de pie toda la noche con un cartel que pedía una cama. Ahora
     * entran en el reparto como cualquier aldeano.
     * <p>
     * <b>POR QUÉ NO BASTABA CON RECLAMAR CUALQUIER CAMA LIBRE</b> (medido con el arnés, vigilante de camas en
     * `GuardHarness`): vanilla le <b>borra el HOME</b> al aldeano desde el comportamiento {@code ValidateNearbyPoi}
     * del cerebro, que el aldeano lleva registrado para {@code HOME}:
     * <pre>
     *   if (!poiManager.exists(pos, HOME))            memory.erase();   // (a) la cama ya no está
     *   else if (bedIsOccupied(level, pos, entity))    memory.erase();   // (b) OCCUPIED y él no está durmiendo
     * </pre>
     * y solo mira a <b>16 bloques o menos</b>. Y una cama son <b>DOS POIs {@code HOME}</b> (uno por mitad), así que
     * dos aldeanos pueden acabar con <b>una mitad cada uno</b>: el que se duerme pone {@code OCCUPIED} en las dos
     * mitades y al otro —que está cerca y no duerme— le cae la rama (b) y se queda sin cama. Medido en su partida:
     * Isidoro (granjero) tenía la cama de {@code 1442,131,1432} <b>cuya otra mitad era del herrero de herramientas</b>
     * ({@code 1443,131,1432}), el herrero se durmió en ella y a Isidoro le borraron el HOME; el latido se la volvía a
     * dar (<b>la misma</b>, porque se pedía «la libre más cercana a la plaza»), y así en bucle.
     * <p>
     * Por eso la cama que se le da tiene que ser <b>suya de verdad</b>: entera (las dos mitades), <b>sin nadie
     * durmiendo</b>, <b>sin compañero de cama</b> (que la otra mitad no sea de otro aldeano) y <b>a la que puede
     * llegar</b> —se comprueba con la ruta de la navegación, como hace la adquisición de vanilla, que exige
     * {@code path.canReach()}: así un granjero del bancal no se queda con una cama del desván de la posada a 52
     * bloques—. Y se busca <b>desde el aldeano</b>, no desde la plaza.
     * <p>
     * Es el mismo mecanismo que {@link #reclamarEstacionesDelPueblo} para los puestos (I23): se respeta la cama que
     * <b>otro aldeano tenga en la memoria</b>, y si el POI está cogido sin dueño se <b>suelta y se vuelve a coger</b>
     * (una cama con el ticket perdido no la puede reclamar nadie nunca).
     */
    private static void reclamarCamasDelPueblo(ServerLevel level, List<Villager> aldeanos, BlockPos center) {
        PoiManager poi = level.getPoiManager();
        Predicate<Holder<PoiType>> esCama = h -> h.is(PoiTypes.HOME);
        for (Villager villager : aldeanos) {
            if (villager.getBrain().hasMemoryValue(MemoryModuleType.HOME)) {
                continue; // ya tiene cama
            }
            BlockPos cama = buscarCamaPara(level, poi, esCama, villager, center, PoiManager.Occupancy.HAS_SPACE);
            boolean libre = cama != null;
            if (cama == null) {
                // ÚLTIMO RECURSO: una cama con el ticket COGIDO sin dueño (un ticket perdido no lo puede reclamar
                // nadie nunca): se suelta y se vuelve a coger, como en las estaciones (I23). OJO: solo si de verdad no
                // es de nadie (`camaUtilizable` mira a los aldeanos de ALREDEDOR DE LA CAMA), porque soltar y coger el
                // ticket de una cama que SÍ es de otro es quitársela: medido, dos aldeanos con la misma cama.
                cama = buscarCamaPara(level, poi, esCama, villager, center, PoiManager.Occupancy.IS_OCCUPIED);
            }
            if (cama == null) {
                continue; // el pueblo no tiene camas que le sirvan (todavía): se reintenta en el latido siguiente
            }
            if (!libre) {
                poi.release(cama);
            }
            final BlockPos elegida = cama;
            BiPredicate<Holder<PoiType>, BlockPos> cual = (tipo, pos) -> pos.equals(elegida);
            if (poi.take(esCama, cual, elegida, 1).isEmpty()) {
                continue; // no se ha podido (el chunk no está cargado...): se reintenta en el latido siguiente
            }
            villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), elegida));
            DevilRpg.LOGGER.info("[Village] {} no tenia cama: reclama la de {} (aldea en {}; {}; {})",
                    villager.getUUID(), elegida.toShortString(), center.toShortString(),
                    libre ? "libre y sin companero de cama" : "ticket perdido", estadoDeLaCama(level, elegida));
        }
    }

    /**
     * La primera cama <b>que le sirve</b> a ese aldeano, de las más cercanas <b>a él</b>: {@link #camaUtilizable
     * entera, sin nadie durmiendo y sin compañero de cama} y <b>alcanzable</b> (ruta de verdad).
     * <p>
     * <b>Y SI NINGUNA RUTA LLEGA, SE LE DA LA QUE MÁS SE ACERCA</b> ({@code mejorSinLlegar}). Hace falta de verdad y
     * es el caso del jugador: un <b>granjero dentro de su bancal</b> —cercado con valla y con las <b>compuertas
     * cerradas</b>— no puede planificar la salida, porque <b>el juego no deja que un aldeano abra una puerta de
     * valla</b> (por eso el pueblo tiene su propio {@code VillagerGateGoal}). Medido con el arnés: la ruta de Isidoro
     * (bancal 2) a su cama acababa en {@code 1394,120,1452}, <b>la propia compuerta</b>, a 15 bloques del destino
     * ({@code alcance=NO}), y lo mismo con TODAS las camas libres de la aldea: sin llegar a ninguna, el aldeano se
     * quedaba sin cama —y vanilla tampoco se la daba, que exige {@code path.canReach()}—. Es un <b>abrazo mortal</b>:
     * sin cama no sale del bancal, y desde el bancal no alcanza ninguna cama. Dándole la cama a la que más se acerca,
     * en cuanto pisa la compuerta el portón se abre y la ruta se completa.
     */
    @Nullable
    private static BlockPos buscarCamaPara(ServerLevel level, PoiManager poi, Predicate<Holder<PoiType>> esCama,
                                           Villager villager, BlockPos center, PoiManager.Occupancy ocupacion) {
        List<BlockPos> candidatas = poi
                .findAllClosestFirstWithType(esCama, p -> true, villager.blockPosition(), RADIO_CAMA, ocupacion)
                .map(par -> par.getSecond())
                .limit(CAMAS_A_PROBAR)
                .toList();
        // ¿Está METIDO en un bancal (cercado con valla y compuertas cerradas)? Entonces vale la cama a la que su ruta
        // más se acerque: el goal del granjero lo saca por la compuerta al anochecer (`Tarea.SALIR`) y la alcanza.
        // FUERA de un bancal no: una cama a la que NO llega no le sirve de nada y además le cuesta el HOME, porque
        // vanilla se lo borra a los 60 s de no poder llegar (`SetWalkTargetFromBlockMemory`). Medido con el arnés: el
        // herrero de herramientas recibía una cama del dormitorio de la barraca que está AL OTRO LADO de un muro de
        // adoquín (su ruta acababa a 2,11 bloques de ella, y para dormir hay que estar a ≤2,0): no se dormía nunca,
        // perdía la cama cada 60 s y el latido se la volvía a dar, en bucle.
        boolean encerrado = estaEnUnBancal(level, villager, center);
        BlockPos mejorSinLlegar = null;
        double mejorDistanciaAlFinal = Double.MAX_VALUE;
        for (BlockPos cama : candidatas) {
            if (!camaUtilizable(level, cama, villager)) {
                continue;
            }
            // UNA CAMA A LA QUE YA NO LLEGÓ (aparcada, I33) NO SE LE VUELVE A DAR: si no, el reparto se la da otra vez
            // y el aldeano entra en el bucle de siempre (reclamar → no llegar → perderla a los 60 s → reclamarla).
            if (esPuntoFallido(villager, cama)) {
                continue;
            }
            var camino = villager.getNavigation().createPath(cama, 1);
            if (camino == null) {
                continue;
            }
            if (camino.canReach()) {
                ESPERA_PARA_DORMIR.remove(villager.getUUID()); // llega solo: no hace falta celda de espera
                return cama; // la buena: llega de verdad
            }
            if (!encerrado) {
                // No llega por el camino del juego: ¿puede al menos ACERCARSE a una celda desde la que se le pueda
                // acostar (I43)? Si sí, se le da la cama y el latido lo lleva y lo acuesta; si no, esta cama no es
                // para él (le costaría el HOME a los 60 s).
                BlockPos espera = celdaParaAcostarse(level, villager, cama);
                if (espera == null) {
                    continue;
                }
                ESPERA_PARA_DORMIR.put(villager.getUUID(), espera);
                DevilRpg.LOGGER.info("[Village] {} no llega a su cama por el camino del juego: se le da {} y se le"
                                + " mandara a {} para acostarle", villager.getUUID(), cama.toShortString(),
                        espera.toShortString());
                return cama;
            }
            // Encerrado en un bancal: se guarda la que MÁS se acerca, medida por dónde acaba su ruta.
            double distanciaAlFinal = camino.getEndNode() == null ? Double.MAX_VALUE
                    : camino.getEndNode().asBlockPos().distSqr(cama);
            if (distanciaAlFinal < mejorDistanciaAlFinal) {
                mejorDistanciaAlFinal = distanciaAlFinal;
                mejorSinLlegar = cama;
            }
        }
        if (mejorSinLlegar != null) {
            DevilRpg.LOGGER.info("[Village] {} esta encerrado en un bancal: ninguna cama libre esta a su alcance, se le"
                            + " da la que MAS se acerca, {} (a {} bloque(s) del final de su ruta): el goal del granjero"
                            + " lo saca por la compuerta al anochecer",
                    villager.getUUID(), mejorSinLlegar.toShortString(),
                    Math.round(Math.sqrt(mejorDistanciaAlFinal)));
        }
        return mejorSinLlegar;
    }

    /**
     * <b>La celda de espera</b>: la más cercana a la cama a la que el aldeano <b>sí llega</b> y desde la que
     * <b>ve la cama</b> (para poder acostarle desde ahí). {@code null} si no hay ninguna.
     * <p>
     * Hace falta porque el <b>planificador del juego</b> a veces no le deja dar los <b>últimos pasos</b> a una cama que
     * tiene a la vista: <b>medido con el arnés</b> (sonda de rutas celda a celda) en la partida del jugador, con el
     * herrero de herramientas, su cama libre estaba a <b>0,87</b> bloques, entraba en la casa (25 nodos hasta
     * `1447,120,1404`, dentro del dormitorio) y desde ahí las rutas a las celdas de al lado de la cama acababan a
     * <b>2,00</b> y <b>3,00</b> bloques ({@code SleepInBed} exige <b>menos de 2,0</b> para acostarse). Se prueba de la
     * más cercana a la más lejana y se para en la primera que valga (que suele estar a 2-3 celdas de la cama).
     */
    @Nullable
    private static BlockPos celdaParaAcostarse(ServerLevel level, Villager villager, BlockPos cama) {
        List<BlockPos> candidatas = new ArrayList<>();
        for (BlockPos q : BlockPos.betweenClosed(cama.offset(-RADIO_ACOSTARSE, -1, -RADIO_ACOSTARSE),
                cama.offset(RADIO_ACOSTARSE, 1, RADIO_ACOSTARSE))) {
            double d = q.distSqr(cama);
            if (d < 1.0D || d > (double) (RADIO_ACOSTARSE * RADIO_ACOSTARSE)) {
                continue; // la propia cama no vale (es a la que no llega), ni nada más lejos del radio
            }
            candidatas.add(q.immutable());
        }
        candidatas.sort(Comparator.comparingDouble(p -> p.distSqr(cama)));
        // PRIMERO las que el aldeano alcanza ANDANDO (lo normal).
        for (BlockPos celda : candidatas) {
            if (!celdaLibreParaAcostarse(level, celda) || !hayVistaLibre(level, celda, cama, villager)) {
                continue; // hay un muro en medio, o ahi no se puede ni estar de pie
            }
            var camino = villager.getNavigation().createPath(celda, 1);
            if (camino != null && camino.canReach()) {
                return celda;
            }
        }
        // Y SI NINGUNA (el planificador no le lleva ni a la celda de al lado de su cama), la más cercana que VEA: el
        // latido le lleva y, si no puede andando, le mueve esos últimos bloques. Una cama que se ve y está a un paso
        // no se descarta: con camas de sobra, el que no duerme es el aldeano, no la cama.
        //
        // OJO CON LO QUE **NO** SE EXIGE AQUÍ (medido con el arnés, aldea 2): la celda de espera **puede estar en
        // otra planta**. Se probó a exigir "misma planta y a ≤3 bloques" para no mover a nadie a través del techo, y
        // el resultado fue el contrario: los aldeanos de abajo **se quedaban SIN CAMA** (`CAMAS RESUMEN: … SIN CAMA=4`)
        // porque todas las camas que quedaban libres eran las de la posada y ninguna pasaba el filtro. Lo que hace
        // falta es que la celda sea **de verdad** una celda (donde se pueda estar de pie, `celdaLibreParaAcostarse`) y
        // que **vea la cama de verdad** (sin muros en medio, ver `hayVistaLibre`): con esas dos cosas, llevarle a la
        // celda de al lado de su cama y acostarle es lo que ya hacía I43, y el aldeano duerme.
        for (BlockPos celda : candidatas) {
            if (celdaLibreParaAcostarse(level, celda) && hayVistaLibre(level, celda, cama, villager)) {
                return celda;
            }
        }
        return null;
    }

    /**
     * <b>¿Se puede ESTAR de pie en esa celda?</b> Nada sólido dentro, nada sólido a la altura de la cabeza y suelo
     * firme debajo. Es lo que tiene que cumplir la <b>celda de espera</b> de una cama: no basta con que desde ella
     * <i>se vea</i> la cama (eso solo mira la línea de visión), porque el latido <b>mueve al aldeano a esa celda</b>
     * cuando no llega andando ({@code acostarAlQueNoLlega}). Sin esta comprobación, la celda de espera podía caer
     * <b>dentro de un muro</b> o <b>en el aire al otro lado de la pared</b> —medido en el guardado del jugador: la
     * celda de espera de la cama {@code 1446,125,1429} (posada) salía en {@code 1446,125,1427}, dos bloques al norte
     * y FUERA del edificio, porque el rayo de visión golpeaba el muro de la posada y ese muro está a un bloque de la
     * cama (ver {@link #hayVistaLibre})— y al aldeano se le movía allí: caía a la calle desde la altura de la posada
     * (medido: los aldeanos aparecían en {@code 1446,120,1427}, justo debajo) y no se acostaba nunca.
     */
    private static boolean celdaLibreParaAcostarse(ServerLevel level, BlockPos celda) {
        return level.getBlockState(celda).getCollisionShape(level, celda).isEmpty()
                && level.getBlockState(celda.above()).getCollisionShape(level, celda.above()).isEmpty()
                && !level.getBlockState(celda.below()).getCollisionShape(level, celda.below()).isEmpty();
    }

    /**
     * ¿Se ve ese bloque desde esa celda <b>sin nada sólido en medio</b>? Es el rayo de colisión del juego: {@code MISS}
     * = vía libre, y el golpe contra el <b>propio objetivo</b> —o contra el bloque de al lado, que es la otra mitad del
     * mismo mueble: la cama tiene dos— tampoco cuenta: la mirada acaba <b>dentro</b> del objetivo. Lo que bloquea de
     * verdad es otra cosa (un muro), y eso se ve porque el bloque golpeado no es el objetivo.
     * <p>
     * Lo usan el <b>sueño</b> (no se acuesta a nadie a través de un muro: {@link #celdaParaAcostarse}) y la
     * <b>cocina</b> (no se cocina desde la plaza: ver {@code VillagerCookGoal}).
     */
    public static boolean hayVistaLibre(ServerLevel level, BlockPos desde, BlockPos objetivo, Villager villager) {
        Vec3 ojo = new Vec3(desde.getX() + 0.5D, desde.getY() + 1.0D, desde.getZ() + 0.5D);
        Vec3 meta = new Vec3(objetivo.getX() + 0.5D, objetivo.getY() + 0.5D, objetivo.getZ() + 0.5D);
        var choque = level.clip(new net.minecraft.world.level.ClipContext(ojo, meta,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, villager));
        if (choque.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
            return true; // sin nada en medio
        }
        return choque instanceof net.minecraft.world.phys.BlockHitResult golpe
                && esElObjetivoOSuOtraMitad(level, golpe.getBlockPos(), objetivo);
    }

    /**
     * ¿El bloque que corta el rayo es el <b>propio objetivo</b> o su <b>otra mitad</b>? La mirada acaba <b>dentro</b>
     * del objetivo, así que el rayo choca con él y eso <b>no</b> es un muro; y la <b>cama</b> son dos bloques, así que
     * chocar con su otra mitad tampoco.
     * <p>
     * OJO: antes valía <b>cualquier</b> bloque a un bloque de distancia del objetivo ({@code distManhattan <= 1}) y eso
     * metía un <b>MURO</b> en la cuenta: medido en el guardado del jugador, la celda de espera de una cama de la
     * posada salía <b>fuera del edificio</b>, al otro lado de su muro (el muro está a un bloque de la cama) y al
     * aldeano se le movía allí, cayéndose a la calle (ver {@code celdaLibreParaAcostarse}).
     */
    private static boolean esElObjetivoOSuOtraMitad(ServerLevel level, BlockPos golpeado, BlockPos objetivo) {
        if (golpeado.equals(objetivo)) {
            return true;
        }
        if (golpeado.distManhattan(objetivo) != 1) {
            return false;
        }
        return level.getBlockState(objetivo).is(net.minecraft.tags.BlockTags.BEDS)
                && level.getBlockState(golpeado).is(net.minecraft.tags.BlockTags.BEDS);
    }

    /**
     * <b>EL QUE NO CONSIGUE DAR EL ÚLTIMO PASO A SU CAMA: EL PUEBLO LE LLEVA Y LE ACOSTA.</b>
     * <p>
     * Si el aldeano está en su hora de descanso, tiene cama, la cama está <b>libre</b>, la tiene <b>a la vista</b> y a
     * menos de {@link #RADIO_ACOSTARSE} bloques, se le acuesta con la misma llamada que usa el juego
     * ({@code LivingEntity.startSleeping}, que además marca la cama como ocupada). Y si aún no está cerca, se le manda
     * a la <b>celda de espera</b> ({@link #ESPERA_PARA_DORMIR}) desde la que sí se puede —el planificador no le lleva
     * a la cama, pero sí a esa celda—. Es el remate del caso medido del <b>herrero de herramientas</b>: entraba en el
     * dormitorio y se quedaba de pie toda la noche a 2 bloques de su cama, y —peor— vanilla se la borraba a los 60 s
     * por no llegar (I43).
     */
    private static void acostarAlQueNoLlega(ServerLevel level, List<Villager> aldeanos) {
        for (Villager villager : aldeanos) {
            if (villager.isSleeping() || !estaDescansando(villager)) {
                continue;
            }
            // LA GUARDIA NO DUERME DE SERVICIO, ASÍ QUE NO SE LE QUITA LA CAMA. Su goal la tiene de ronda o en la
            // puerta toda la noche (I28: "la guardia de noche es parte del servicio"), así que no se acerca a su cama
            // —y aquí se le acababa quitando por "no acercarse en 6 latidos": medido en el log del jugador, los
            // `SIN CAMA` eran guardias (Genoveva, Prudencio) y la cama volvía al reparto un latido sí y otro no—.
            // Un guardia conserva su cama: la usa cuando le toca descansar de verdad.
            if (com.chipoodle.devilrpg.entity.goal.VillagerGuardGoal.esGuardia(villager)) {
                continue;
            }
            Optional<GlobalPos> suya = villager.getBrain().getMemory(MemoryModuleType.HOME);
            if (suya.isEmpty()) {
                continue;
            }
            BlockPos cama = suya.get().pos();
            BlockState estado = level.getBlockState(cama);
            if (!estado.is(BlockTags.BEDS) || estado.getValue(BedBlock.OCCUPIED)) {
                continue; // la cama ya no está (o la ocupa otro): que lo arregle el reparto
            }
            if (villager.blockPosition().distSqr(cama) > (double) (RADIO_ACOSTARSE * RADIO_ACOSTARSE)
                    || !hayVistaLibre(level, villager.blockPosition(), cama, villager)) {
                // Todavía no está donde se le puede acostar: se le manda a su CELDA DE ESPERA (la de al lado de la
                // cama). El planificador no le lleva a la cama, pero a esa celda casi siempre sí.
                BlockPos espera = ESPERA_PARA_DORMIR.get(villager.getUUID());
                if (espera != null) {
                    var camino = villager.getNavigation().createPath(espera, 1);
                    if (camino == null || !camino.canReach()) {
                        // Y SI NO LE LLEVA NI A ESA (medido: el herrero se quedaba a 2,00 bloques de su cama, y para
                        // acostarse hacen falta menos de 2,0), EL PUEBLO LE LLEVA: se le mueve a la celda —que está a
                        // la vista y a menos de RADIO_ACOSTARSE bloques, no es un salto a ciegas— y en la pasada
                        // siguiente se le acuesta. Es la tarea propia que sustituye a lo que el juego hace mal.
                        villager.getNavigation().stop();
                        villager.moveTo(espera.getX() + 0.5D, espera.getY(), espera.getZ() + 0.5D,
                                villager.getYRot(), villager.getXRot());
                        DevilRpg.LOGGER.info("[Village] {} no llega ni andando a la celda de al lado de su cama: el"
                                        + " pueblo le lleva a {} (la cama {} la tiene a la vista)",
                                villager.getUUID(), espera.toShortString(), cama.toShortString());
                        continue;
                    }
                    caminarHacia(villager, espera, 0.6F);
                    ponerActividad(villager, "Yendo a dormir");
                }
                // Y SI NI ASÍ (ni se acerca, ni su celda de espera le sirve), SE LE DA OTRA CAMA: se le suelta la suya
                // —con su ticket, I23: dejarlo cogido la dejaría muerta para nadie— y se apunta como punto fallido
                // (I33) para que el reparto no se la vuelva a dar y le busque una que SÍ alcance. El contador solo
                // sube cuando NO se acerca (I3), así que a un aldeano que va andando a su cama desde lejos no se le
                // quita nada. Sin esto el bucle era eterno: vanilla le borra el HOME a los 60 s por no llegar, el
                // reparto se lo vuelve a dar (el mismo) y vuelta a empezar — medido en el log del jugador: el mismo
                // aldeano reclamando la misma cama de la posada (1446,125,1429) una y otra vez sin acostarse nunca.
                SinLlegar antes = SIN_LLEGAR_A_LA_CAMA.get(villager.getUUID());
                double distancia = Math.sqrt(villager.blockPosition().distSqr(cama));
                boolean mismaCama = antes != null && antes.cama().equals(cama);
                boolean acercandose = mismaCama && distancia < antes.mejor() - 0.5D;
                int fallos = mismaCama && !acercandose ? antes.fallos() + 1 : 0;
                double mejor = mismaCama && !acercandose ? Math.min(antes.mejor(), distancia) : distancia;
                SIN_LLEGAR_A_LA_CAMA.put(villager.getUUID(), new SinLlegar(cama, mejor, fallos));
                if (fallos >= LATIDOS_PARA_RENUNCIAR_A_LA_CAMA) {
                    SIN_LLEGAR_A_LA_CAMA.remove(villager.getUUID());
                    ESPERA_PARA_DORMIR.remove(villager.getUUID());
                    level.getPoiManager().release(cama); // el ticket vuelve a estar libre (I23)
                    villager.getBrain().eraseMemory(MemoryModuleType.HOME);
                    marcarPuntoFallido(villager, cama);
                    DevilRpg.LOGGER.info("[Village] {} no consigue llegar a su cama {} en {} latidos sin acercarse:"
                                    + " se le da otra (la suya queda libre y aparcada)",
                            villager.getUUID(), cama.toShortString(), fallos);
                }
                continue;
            }
            ESPERA_PARA_DORMIR.remove(villager.getUUID());
            SIN_LLEGAR_A_LA_CAMA.remove(villager.getUUID());
            villager.startSleeping(cama);
            DevilRpg.LOGGER.info("[Village] {} no llegaba a su cama por el camino del juego (esta a {} bloque(s) y la"
                            + " tiene a la vista): el pueblo le acuesta en {}",
                    villager.getUUID(),
                    Math.round(Math.sqrt(villager.distanceToSqr(cama.getX() + 0.5D, cama.getY() + 0.5D,
                            cama.getZ() + 0.5D))), cama.toShortString());
        }
    }

    /** ¿Ese aldeano está <b>metido en un bancal</b> (cercado con valla y con las compuertas cerradas)? */
    private static boolean estaEnUnBancal(ServerLevel level, Villager villager, BlockPos center) {
        int cota = VillageGenerator.cotaDeLaPlaza(level, center);
        for (int i = 0; i < VillageGenerator.parcelasDeGranja(); i++) {
            if (VillageGenerator.estaDentroDeLaParcela(center, i, cota, villager.blockPosition())) {
                return true;
            }
        }
        return false;
    }

    /**
     * ¿Esa cama está <b>entera</b>, <b>sin nadie durmiendo</b> y <b>sin compañero de cama</b>? Es lo que evita el
     * bucle de vanilla (ver {@link #reclamarCamasDelPueblo}): una cama son <b>dos POIs</b> {@code HOME} —una por
     * mitad— y al aldeano que comparte cama y no duerme le borran el HOME.
     * <p>
     * El dueño se busca <b>alrededor de la cama</b> ({@link #laTieneOtro}), tanto en la propia cama como en su
     * <b>otra mitad</b>: es lo que impide que la rama del «ticket perdido» le <b>robe</b> la cama a un aldeano que la
     * tiene en memoria (medido: dos aldeanos con la misma cama en el cerebro).
     */
    private static boolean camaUtilizable(ServerLevel level, BlockPos cama, Villager villager) {
        BlockState estado = level.getBlockState(cama);
        if (!estado.is(BlockTags.BEDS) || estado.getValue(BedBlock.OCCUPIED)) {
            return false; // ni es una cama (POI viejo) o hay alguien durmiendo en ella
        }
        BlockPos pareja = parejaDeLaCama(estado, cama);
        if (pareja != null) {
            BlockState laOtra = level.getBlockState(pareja);
            if (!laOtra.is(BlockTags.BEDS) || laOtra.getValue(BedBlock.OCCUPIED)) {
                return false; // media cama (rota) o alguien durmiendo en la otra mitad
            }
            if (laTieneOtro(level, villager, pareja)) {
                return false; // COMPARTIDA: esa mitad es de otro aldeano (vanilla le borraría el HOME)
            }
            // Y ADEMÁS: si el TICKET de la otra mitad está COGIDO, esa mitad es de alguien aunque a su dueño no se le
            // vea alrededor de la cama (va andando hacia ella, está en su bancal...). Medido con el arnés (aldea 2):
            // dos aldeanos acabaron con **media cama cada uno** (`1452,125,1429` y su mitad `1452,125,1430`), que es
            // el bucle que I43 describe —vanilla le borra el HOME al que comparte y no duerme—.
            if (level.getPoiManager().getCountInRange(h -> h.is(PoiTypes.HOME), pareja, 1,
                    PoiManager.Occupancy.IS_OCCUPIED) > 0) {
                return false;
            }
        }
        return !laTieneOtro(level, villager, cama);
    }

    /** La otra mitad del bloque de la cama (la pareja HEAD/FOOT), o {@code null} si no es una cama de dos bloques. */
    @Nullable
    private static BlockPos parejaDeLaCama(BlockState cama, BlockPos pos) {
        if (!cama.hasProperty(BedBlock.PART) || !cama.hasProperty(BedBlock.FACING)) {
            return null;
        }
        Direction haciaLaPareja = cama.getValue(BedBlock.PART) == BedPart.FOOT
                ? cama.getValue(BedBlock.FACING) : cama.getValue(BedBlock.FACING).getOpposite();
        return pos.relative(haciaLaPareja);
    }

    /**
     * ¿Esa cama la tiene en el cerebro <b>otro</b> aldeano? Se mira a los aldeanos que hay <b>alrededor de la CAMA</b>
     * (no a la lista del censo, que se arma alrededor de la plaza): el dueño de una cama puede estar lejos del centro
     * —un granjero en su bancal, un leñador en la arboleda— y entonces la lista no lo ve, la cama parece «libre» y se
     * le acaba dando a otro. Medido: dos aldeanos con la misma cama en memoria y el ticket en uno solo, con el
     * consiguiente borrado del HOME de vanilla.
     */
    private static boolean laTieneOtro(ServerLevel level, Villager villager, BlockPos cama) {
        for (Villager otro : level.getEntitiesOfClass(Villager.class, new AABB(cama).inflate(RADIO_CAMA))) {
            if (otro == villager) {
                continue;
            }
            Optional<GlobalPos> suya = otro.getBrain().getMemory(MemoryModuleType.HOME);
            if (suya.isPresent() && suya.get().pos().equals(cama)) {
                return true;
            }
        }
        return false;
    }

    /** Para el log: cómo está la cama que se acaba de reclamar (bloque y si alguien duerme en ella). */
    private static String estadoDeLaCama(ServerLevel level, BlockPos cama) {
        BlockState estado = level.getBlockState(cama);
        String bloque = estado.getBlock().toString().replace("Block{minecraft:", "").replace("}", "");
        return bloque + " ocupada=" + (estado.hasProperty(BedBlock.OCCUPIED) && estado.getValue(BedBlock.OCCUPIED));
    }

    // --- EL ALDEANO QUE SE QUEDA DENTRO DE UNA CASA: se le baja a la plaza -----------------------------------

    /** Dónde y desde cuándo lleva quieto cada aldeano que está metido en un piso (para el rescate). */
    private static final java.util.Map<UUID, long[]> ATRAPADOS = new java.util.concurrent.ConcurrentHashMap<>();
    /** Sin moverse de celda este tiempo, dentro de un piso, se le baja a la plaza (30 s). */
    private static final int ATRAPADO_TICKS = 30 * 20;

    /**
     * <b>Rescata al aldeano que se ha quedado atascado dentro de una casa</b> (en un piso, por encima de la capa de la
     * calle, o en un sótano): si lleva {@link #ATRAPADO_TICKS} <b>sin moverse de celda</b>, se le baja a la plaza.
     * <p>
     * Hace falta de verdad, y está medido con el arnés: el <b>recolector</b> subió al desván de la taberna a por unas
     * <b>camas tiradas</b> en el suelo, desde ahí <b>no alcanzaba el almacén</b> (ruta degenerada de 1 nodo) y, al
     * intentar salir hacia la plaza, se quedó <b>encajado</b> contra los cofres del desván: la ruta a la plaza se
     * calculaba (9 nodos) pero el aldeano <b>no se movía</b> — 30 s clavado en la misma celda, y así para siempre (o
     * hasta morirse de hambre ahí arriba, que es lo que pasó en una de las corridas).
     * <p>
     * No se toca a quien está <b>durmiendo</b> ni en la franja de descanso (dormir en la posada es legítimo), ni al que
     * anda a la altura de la calle. Solo se rescata al que está <b>fuera de esa altura y quieto</b>.
     */
    private static void rescatarAldeanosAtrapados(ServerLevel level, List<Villager> aldeanos, BlockPos center) {
        int cota = VillageGenerator.cotaDeLaPlaza(level, center);
        for (Villager villager : aldeanos) {
            if (villager.isBaby() || villager.isSleeping()
                    || villager.getBrain().isActive(net.minecraft.world.entity.schedule.Activity.REST)
                    // EN LA CALLE = con los PIES a la altura de la calle (o medio escalón por encima, que es lo que
                    // sube andando: I26/I95). OJO: antes esto era "a menos de 4 bloques de la cota", y con una casa
                    // cuyo suelo queda a 2 o 3 bloques de la calle el aldeano atrapado DENTRO de ella —en la
                    // escalera, con la cama en el piso de arriba— quedaba EXENTO y no lo rescataba nadie. Medido con
                    // el arnés: la granjera Ursula congelada en 428,65,669 (cota 63), su bancal sin cosechar y sin
                    // poder llegar a su puesto (`no consigue llegar a 440,63,668` cada 5 min), con 36 plantas
                    // maduras esperando. Con el criterio de la altura de los pies, a los 30 s se le baja a la plaza.
                    || villager.getY() <= cota + 0.6D) {
                ATRAPADOS.remove(villager.getUUID()); // en la calle (o descansando): no hay nada que rescatar
                continue;
            }
            long celda = villager.blockPosition().asLong();
            long ahora = level.getGameTime();
            long[] antes = ATRAPADOS.get(villager.getUUID());
            if (antes == null || antes[0] != celda) {
                ATRAPADOS.put(villager.getUUID(), new long[]{celda, ahora});
                continue; // se acaba de mover (o es la primera vez que se le ve ahí): se le da tiempo
            }
            if (ahora - antes[1] < ATRAPADO_TICKS) {
                continue;
            }
            ATRAPADOS.remove(villager.getUUID());
            BlockPos estaba = villager.blockPosition();
            BlockPos destino = casillaLibreDeLaPlaza(level, center, cota);
            villager.getNavigation().stop();
            villager.teleportTo(destino.getX() + 0.5D, destino.getY(), destino.getZ() + 0.5D);
            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            DevilRpg.LOGGER.info("[Village] {} estaba atascado dentro de una casa en {}: lo bajo a la plaza ({})",
                    villager.getUUID(), estaba.toShortString(), destino.toShortString());
        }
    }

    /** Una celda con <b>sitio para pararse</b> a la altura de la calle, cerca de la plaza (anillos desde el centro). */
    private static BlockPos casillaLibreDeLaPlaza(ServerLevel level, BlockPos center, int cota) {
        for (int r = 2; r <= 12; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue; // el interior ya se miró en los anillos anteriores
                    }
                    BlockPos p = new BlockPos(center.getX() + dx, cota, center.getZ() + dz);
                    if (level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                            && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                            && !level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty()) {
                        return p;
                    }
                }
            }
        }
        return new BlockPos(center.getX(), cota, center.getZ());
    }

    /** Marca a un aldeano como obrero y le pone el goal de reparación. */    private static void marcarObrero(Villager villager, BlockPos center, int objectiveIndex) {
        boolean yaEra = villager.getPersistentData().getBoolean(BUILDER_TAG);
        villager.getPersistentData().putBoolean(BUILDER_TAG, true);
        // Un obrero CON FAENA FIJA lleva la reparación POR DEBAJO de su goal de oficio (prioridad 5 contra 4):
        // primero lo suyo y, cuando no tiene faena, repara. Vale para CUALQUIER oficio del pueblo, no solo para el
        // granjero y los herreros: con la lista corta (los tres de la etapa E) el PESCADOR (etapa G) y los oficios
        // nuevos reparaban en vez de trabajar, porque su Repair (3) quedaba por encima de su faena (4) con la misma
        // bandera MOVE. Es el fallo de I23, que decía "un aldeano con faena fija" y el código solo cumplía a medias.
        // OJO: la pregunta NO es "¿es oficio del pueblo?" sino "¿tiene una faena que compita con reparar?": el
        // RECOLECTOR ocupa plaza del pueblo (está en VILLAGER_SPECIALTIES) pero no tiene oficio, y es EL constructor.
        // Ver `VillageGenerator.tieneFaenaPropia`.
        boolean tieneFaena = VillageGenerator.tieneFaenaPropia(villager.getVillagerData().getProfession());
        asegurarGoalDeObrero(villager, center, objectiveIndex, tieneFaena ? 5 : 3);
        // Y al que NO tiene faena (el holgazán, el clérigo, un aldeano sin oficio) se le quita la recogida: los dos
        // goals quedarían a prioridad 3 y el de recoger se engancha antes, así que el "constructor" se pasaba el día
        // barriendo el pueblo en vez de reparar (que es justo lo que se quiso evitar cuando se le dio el goal al
        // recolector). El que no es obrero sigue recogiendo lo suyo normalmente.
        if (!tieneFaena) {
            for (WrappedGoal wrapped : List.copyOf(villager.goalSelector.getAvailableGoals())) {
                if (wrapped.getGoal() instanceof com.chipoodle.devilrpg.entity.goal.VillagerPickupGoal) {
                    villager.goalSelector.removeGoal(wrapped.getGoal());
                }
            }
        }
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
     * Le pone al <b>leñador</b> su goal de <b>talar y reforestar</b> (etapas B y H), a prioridad <b>4</b>: es su
     * oficio, como el del granjero o el del herrero, así que va por delante de la taberna (6) y por detrás de recoger
     * lo suyo del suelo (3).
     * <p>
     * Antes era un <b>segundo goal del recolector</b> (el holgazán) a prioridad 6: el pueblo no gastaba un puesto más
     * —lo que se anotó como decisión—, pero el jugador pidió separarlos, y además esa prioridad <b>empataba con la
     * taberna</b> (6), así que el aldeano con hambre y leña pendiente no iba a comer. El oficio del leñador es
     * <b>FLETCHER</b> (flechero) y su estación la <b>mesa de flechas</b> de su taller, en la arboleda (una profesión
     * por estación: ver {@code VillageGenerator.asegurarElTallerDelLenador}).
     */
    private static void asegurarGoalDeLenador(Villager villager, BlockPos center, int objectiveIndex) {
        for (WrappedGoal wrapped : List.copyOf(villager.goalSelector.getAvailableGoals())) {
            if (wrapped.getGoal() instanceof VillagerLumberjackGoal) {
                return;
            }
        }
        villager.goalSelector.addGoal(4, new VillagerLumberjackGoal(villager, center, objectiveIndex));
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

    /**
     * Le pone a un aldeano el goal de las <b>puertas de madera</b> (cierra la que cruza). Prioridad <b>2</b> y
     * <b>sin banderas</b>, como el de los portones: no mueve al aldeano, así que va a la vez que su faena. Lo pidió el
     * jugador: *"los aldeanos cuando vayan a dormir tienen que cerrar la puerta porque todas la dejan abierta"*.
     */
    private static void asegurarGoalDePuertas(Villager villager) {
        for (WrappedGoal wrapped : villager.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof com.chipoodle.devilrpg.entity.goal.VillagerDoorGoal) {
                return;
            }
        }
        villager.goalSelector.addGoal(2, new com.chipoodle.devilrpg.entity.goal.VillagerDoorGoal(villager));
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
     * Le pone al <b>MINERO</b> su goal de la <b>mina</b> (etapa I): baja el caracol, abre galerías, saca el mineral,
     * lo funde en su horno, cuela el adoquín en su balsa para sacar pedernal y lo baja todo al almacén. Prioridad
     * <b>4</b>, la de los oficios (como el granjero, el pescador o los herreros).
     * <p>
     * Su oficio es {@code MASON} (el del <b>cortapiedras</b> de su caseta) porque es el que el juego da al puesto de
     * trabajo que hay allí: una profesión por estación (I31), y hasta la etapa I el cortapiedras estaba
     * <b>prohibido</b> en la aldea justo por eso.
     */
    private static void asegurarGoalDelMinero(Villager villager, BlockPos center, int objectiveIndex) {
        for (WrappedGoal wrapped : villager.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof com.chipoodle.devilrpg.entity.goal.VillagerMinerGoal) {
                return;
            }
        }
        villager.goalSelector.addGoal(com.chipoodle.devilrpg.entity.goal.VillagerMinerGoal.PRIORIDAD,
                new com.chipoodle.devilrpg.entity.goal.VillagerMinerGoal(villager, center, objectiveIndex));
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
     * {@code VillagerPickupGoal.PRIORIDAD} (<b>por debajo</b> de la faena de su oficio) con un radio corto: primero
     * trabaja y, cuando no tiene faena, recoge lo suyo del suelo. Antes iba por ENCIMA del oficio (3 contra 4) y el
     * jugador vio lo que eso significa: el granjero Isidoro dejaba la huerta para ir a por sus materiales.
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

    /** Le pone al <b>clérigo</b> su goal de <b>preparar pociones</b> en el soporte de la iglesia (etapa H). */
    private static void asegurarGoalDelClerigo(Villager villager, BlockPos center, int objectiveIndex) {
        for (WrappedGoal wrapped : List.copyOf(villager.goalSelector.getAvailableGoals())) {
            if (wrapped.getGoal() instanceof com.chipoodle.devilrpg.entity.goal.VillagerClericGoal) {
                return;
            }
        }
        villager.goalSelector.addGoal(com.chipoodle.devilrpg.entity.goal.VillagerClericGoal.PRIORIDAD,
                new com.chipoodle.devilrpg.entity.goal.VillagerClericGoal(villager, center, objectiveIndex));
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

    // --- lo que no se alcanza, se deja por un rato (I33 para TODOS los goals del pueblo) ---------------

    /** Sitio (empaquetado con `asLong`) al que ese aldeano no llegó, y hasta cuándo no lo reintenta. */
    private static final String PUNTO_FALLIDO_TAG = "DevilRpgPuntoFallido";
    private static final String PUNTO_FALLIDO_HASTA_TAG = "DevilRpgPuntoFallidoHasta";
    /** Cuánto se aparca un sitio inalcanzable (5 min de juego): el mundo cambia y se vuelve a intentar. */
    public static final int PUNTO_FALLIDO_TICKS = 5 * 60 * 20;

    /**
     * <b>Apuntar un sitio al que el aldeano no llegó</b> (invariante I33, para TODOS los goals del pueblo): un goal
     * de faena que se rinde con {@code stuckTicks >= STUCK_LIMIT} y vuelve a elegir un destino <b>determinista</b> (la
     * misma mata, el mismo tronco, la misma caja, el mismo punto de ronda) se queda en <b>bucle</b>: empuja el mismo
     * obstáculo para siempre. El jugador lo vio con la guardia del corral y con Isidoro ("Guardando lo suyo ...
     * moviéndose errático"); el patrón estaba en <b>todos</b> los goals.
     * <p>
     * El sitio se apunta en los <b>datos persistentes</b> del aldeano (no en el goal, que se pierde al descargar el
     * chunk) y {@link #esPuntoFallido} lo salta hasta dentro de {@link #PUNTO_FALLIDO_TICKS}. Queda en el log, que es
     * lo que permite ver <b>qué</b> sitio del pueblo es el inalcanzable (si es un cofre o una caja, hay algo real que
     * arreglar en el mundo; si es una mata o un tronco, se deja hasta que el mundo cambie).
     */
    public static void marcarPuntoFallido(Villager villager, @Nullable BlockPos punto) {
        if (punto == null) {
            return;
        }
        BlockPos p = punto.immutable();
        CompoundTag datos = villager.getPersistentData();
        datos.putLong(PUNTO_FALLIDO_TAG, p.asLong());
        datos.putLong(PUNTO_FALLIDO_HASTA_TAG, villager.level().getGameTime() + PUNTO_FALLIDO_TICKS);
        DevilRpg.LOGGER.info("[Village] {} no consigue llegar a {}: lo deja por {} min y sigue con lo demas",
                villager.getUUID(), p, PUNTO_FALLIDO_TICKS / (60 * 20));
    }

    /** ¿Ese sitio está <b>aparcado</b> para ese aldeano? (no llegó a él hace poco: ver {@link #marcarPuntoFallido}) */
    public static boolean esPuntoFallido(Villager villager, @Nullable BlockPos punto) {
        if (punto == null) {
            return false;
        }
        CompoundTag datos = villager.getPersistentData();
        if (!datos.contains(PUNTO_FALLIDO_TAG) || !datos.contains(PUNTO_FALLIDO_HASTA_TAG)) {
            return false;
        }
        return villager.level().getGameTime() < datos.getLong(PUNTO_FALLIDO_HASTA_TAG)
                && datos.getLong(PUNTO_FALLIDO_TAG) == punto.asLong();
    }

    /** Deja de caminar: se quita el destino del cerebro para que no siga yendo a un sitio ya resuelto. */    public static void parar(Villager villager) {
        villager.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
        // Y TAMBIÉN el LOOK_TARGET: el cerebro del aldeano tiene "andar hacia donde mira"
        // (`SetWalkTargetFromLookTarget` en su paquete IDLE), así que con el destino borrado y la mirada puesta en el
        // sitio al que iba, el paseo lo devolvía a esa misma casilla — que al llegar es SU PROPIA CASILLA, que es
        // "dar vueltas sobre sí mismo". Parar es dejar de caminar Y de mirar a un sitio ya resuelto.
        villager.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET);
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
            if (dx * dx + dz * dz > limite) {
                continue;
            }
            // LA ALTURA, CON LA COTA DE LA ALDEA (I12), no con el nivel del mar: la banda es la MISMA que usa
            // `dentroDelRecinto`. Antes se medía contra `level.getSeaLevel()` con un margen de 96, así que una cueva
            // veinte bloques por debajo de la plaza y una loma treinta por encima contaban como "dentro de la aldea"
            // (y el sello cortaba spawns que no eran de la aldea, mientras no distinguía bien el suelo del pueblo).
            double dy = pos.getY() - VillageGenerator.cotaDeLaPlaza(level, centro);
            if (dy >= -RECINTO_DY_ABAJO && dy <= RECINTO_DY_ARRIBA) {
                return true;
            }
        }
        return false;
    }

    /** Tope de objetivos que se miran al comprobar la protección (de sobra para cualquier partida). */
    private static final int MAX_OBJECTIVES = 64;

    /** Marca (en los datos del aldeano) de que el nombre flotante lo puso el mod, y cuándo. */
    public static final String ACTIVIDAD_TAG = "DevilRpgActividad";
    /**
     * Marca (en los datos persistentes del aldeano) de que <b>es gente de una aldea del MOD</b>. La pone el latido al
     * adoptar a los aldeanos de un pueblo ({@link #repartirNombres}) y es lo que decide si lleva la <b>etiqueta</b> con
     * su nombre y su oficio.
     * <p>
     * Lo pidió el jugador: *"las villas normales (vanilla) tienen a sus aldeanos con las mismas etiquetas que la villa
     * de mi mod. No deberían de tener etiqueta de nombre y profesión: eso sólo es para los aldeanos del mod"*. Los
     * aldeanos de las aldeas de vanilla (y los que andan sueltos) <b>no</b> la llevan y se quedan como siempre.
     */
    public static final String DEL_PUEBLO_TAG = "DevilRpgDelPueblo";
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
     * <p>
     * OJO: con <b>48 nombres</b> y una aldea de 11-18 aldeanos, "al azar por UUID" <b>repite nombres</b> (con 11
     * aldeanos hay ~70% de probabilidad de que al menos dos se llamen igual: es la paradoja del cumpleaños). El
     * jugador lo vio: <b>dos "Bibiana"</b> en el mismo pueblo. Por eso existe {@link #NOMBRE_TAG}: el nombre que se
     * le asigna a cada aldeano <b>se guarda</b> en sus datos y el reparto procura que <b>no se repita</b> dentro de la
     * aldea ({@link #repartirNombres}). Lo de arriba sigue valiendo como nombre de arranque: el que no tenga nombre
     * guardado (una partida vieja, un aldeano recién nacido) usa el de su UUID hasta el latido siguiente.
     */
    private static final String[] NOMBRES = {
            "Anselmo", "Bartolo", "Casimiro", "Dionisio", "Eustaquio", "Fabricio", "Gervasio", "Hipolito",
            "Isidoro", "Jacinto", "Leoncio", "Mauricio", "Nicasio", "Onofre", "Prudencio", "Quintin",
            "Remigio", "Saturnino", "Teodoro", "Ubaldo", "Valeriano", "Wenceslao", "Ximeno", "Zacarias",
            "Aurelia", "Bibiana", "Cesarea", "Dorotea", "Eufemia", "Filomena", "Genoveva", "Hortensia",
            "Isabel", "Josefa", "Leocadia", "Manuela", "Nicolasa", "Obdulia", "Petronila", "Ramona",
            "Segismunda", "Tomasa", "Ursula", "Vicenta", "Waldina", "Ximena", "Yolanda", "Zenobia",
    };

    /** Nombre asignado a ese aldeano (en sus datos persistentes). Vacío si aún no se le ha repartido uno. */
    private static final String NOMBRE_TAG = "DevilRpgNombre";

    /** Nombre del aldeano: el que tenga <b>asignado</b> y, si no, el de su UUID (estable, sin guardar nada). */
    public static String nombreDe(Villager villager) {
        String asignado = villager.getPersistentData().getString(NOMBRE_TAG);
        if (!asignado.isEmpty()) {
            return asignado;
        }
        long bits = villager.getUUID().getMostSignificantBits() ^ villager.getUUID().getLeastSignificantBits();
        return NOMBRES[Math.floorMod((int) (bits ^ (bits >>> 32)), NOMBRES.length)];
    }

    /**
     * ¿Ese aldeano es <b>gente de una aldea del mod</b>? (ver {@link #DEL_PUEBLO_TAG}). Solo a esos se les pone la
     * etiqueta con el nombre y el oficio: los de las aldeas de vanilla se quedan sin etiqueta.
     */
    public static boolean esDelPueblo(Villager villager) {
        return villager.getPersistentData().getBoolean(DEL_PUEBLO_TAG);
    }

    /**
     * <b>Nombres sin repetir</b> dentro de la aldea (lo vio el jugador: dos "Bibiana"): a cada aldeano que aún no
     * tenga nombre asignado se le da el primer nombre libre <b>empezando por el de su UUID</b> (así el que ya se
     * llamaba de una manera la conserva casi siempre) y se le <b>guarda</b> en sus datos: el nombre viaja con él en la
     * partida y no vuelve a cambiar. Los nombres ya asignados no se tocan.
     */
    private static void repartirNombres(List<Villager> aldeanos) {
        // ADOPCIÓN: los aldeanos que el latido ve en un pueblo del mod quedan MARCADOS como gente del pueblo, y esa
        // marca es la que les da derecho a la etiqueta (nombre y oficio). Un aldeano de una aldea de vanilla no pasa
        // por aquí, así que no se le pone ninguna etiqueta (lo pidió el jugador).
        for (Villager villager : aldeanos) {
            villager.getPersistentData().putBoolean(DEL_PUEBLO_TAG, true);
        }
        Set<String> usados = new HashSet<>();
        for (Villager villager : aldeanos) {
            String nombre = villager.getPersistentData().getString(NOMBRE_TAG);
            if (!nombre.isEmpty()) {
                usados.add(nombre);
            }
        }
        for (Villager villager : aldeanos) {
            if (!villager.getPersistentData().getString(NOMBRE_TAG).isEmpty()) {
                continue;
            }
            long bits = villager.getUUID().getMostSignificantBits() ^ villager.getUUID().getLeastSignificantBits();
            int inicio = Math.floorMod((int) (bits ^ (bits >>> 32)), NOMBRES.length);
            for (int salto = 0; salto < NOMBRES.length; salto++) {
                String candidato = NOMBRES[(inicio + salto) % NOMBRES.length];
                if (usados.add(candidato)) {
                    villager.getPersistentData().putString(NOMBRE_TAG, candidato);
                    break;
                }
            }
        }
    }

    /**
     * Nombre del <b>oficio</b> del aldeano. Se usan los nombres del mod (en español, como el resto de sus textos) y
     * para cualquier oficio de vanilla que acabe en la aldea se cae al nombre traducido del propio juego.
     */
    public static String nombreDeOficio(Villager villager) {
        // La guardia manda sobre el oficio: un guardia puede ser (por ejemplo) un segundo granjero, pero lo que el
        // jugador tiene que ver en su etiqueta es que está de guardia y con qué.
        if (VillagerGuardGoal.esGuardia(villager)) {
            // Y SU NIVEL (I62): el guardia se hace más fuerte matando, así que su etiqueta lo dice ("Guardia
            // espadachín · nv 3"): es como el jugador ve la progresión sin abrir nada.
            String arma = VillagerGuardGoal.tipoDe(villager) == VillagerGuardGoal.ARQUERO
                    ? "Guardia arquero" : "Guardia espadachín";
            return arma + " · nv " + nivelDeGuardia(villager);
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
        if (profesion == VillagerProfession.FISHERMAN) {
            // El pescador SIEMPRE estuvo fuera de esta tabla: su etiqueta salía de la traducción vanilla, y como el
            // idioma del servidor es inglés el jugador leía "Isidoro (Fisherman)". Aquí va en español, como el resto.
            return "Pescador"; // la pesquera y su lago (etapa G)
        }
        if (profesion == VillagerProfession.FLETCHER) {
            return "Leñador"; // el flechero del taller de la arboleda (etapa H): tala, replanta y baja la madera
        }
        if (profesion == VillagerProfession.MASON) {
            return "Minero"; // el albañil del cortapiedras de la caseta de la mina (etapa I): cava, funde y filtra
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
        if (!esDelPueblo(villager)) {
            return; // no es gente de una aldea del mod: sin etiqueta (ni nombre ni oficio)
        }
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
            if (!esDelPueblo(villager)) {
                // NO es gente de una aldea del mod (aldea de vanilla, aldeano suelto): no se le pone etiqueta NINGUNA.
                // Y si el mod se la había puesto antes (partidas viejas, cuando se etiquetaba a todo el que pasara
                // cerca), se le QUITA — pero solo si la puso el mod (`ACTIVIDAD_TAG`), para no borrar un nombre que le
                // hayas puesto tú con una etiqueta de nombre.
                if (villager.getPersistentData().getBoolean(ACTIVIDAD_TAG)) {
                    villager.setCustomName(null);
                    villager.setCustomNameVisible(false);
                    villager.getPersistentData().putBoolean(ACTIVIDAD_TAG, false);
                }
                continue;
            }
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
        BlockPos centro = centroDe(level, objectiveIndex);
        for (int i = 0; i < plano.size(); i++) {
            BlockPos pos = plano.posAt(i);
            long comprimida = pos.asLong();
            if (excluir.contains(comprimida) || reclamadoPorOtro(level, comprimida, builder)) {
                continue;
            }
            // LA ARBOLEDA DEL PUEBLO NO SE "REPARA": es la madera del leñador (tala y replanta a propósito), así que
            // reponer sus TRONCOS sería levantar troncos flotando. Las aldeas ya guardadas los tienen apuntados en el
            // plano (el de una aldea migrada es un ESCANEO del mundo y el árbol ya había crecido), así que se
            // descartan AL LEER, sin migración —igual que `VillageGenerator.estadoDelPlano` con el portón—. Medido en
            // el guardado del jugador (aldea 2): 9 troncos de acacia de la arboleda apuntados como huecos.
            if (centro != null && VillageGenerator.enLaArboleda(centro, pos)
                    && VillageGenerator.estadoDelPlano(plano.stateAt(i)).is(net.minecraft.tags.BlockTags.LOGS)) {
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
            // Se compara contra el estado BUENO (el plano pasado por `estadoDelPlano`: el portón, siempre cerrado).
            if (!necesitaReparacion(level.getBlockState(pos), VillageGenerator.estadoDelPlano(plano.stateAt(i)))) {
                continue;
            }
            mejorDist = dist;
            mejor = pos;
        }
        if (mejor != null) {
            return mejor;
        }
        // SEGUNDA PASADA: LOS AGUJEROS DEL SUELO. El plano no los tiene (un cráter de creeper está en terreno natural,
        // que el generador no apuntó), así que sin esto nadie los tapaba: el obrero daba la aldea por completa con el
        // hoyo abierto (medido en la partida del jugador: 0 pendientes en el plano y el cráter ahí).
        return buscarAgujeroEnElSuelo(level, objectiveIndex, centro, from, excluir, builder);
    }

    /**
     * Busca el <b>agujero del suelo</b> más cercano dentro del recinto (ver {@link #esAgujeroDelSuelo}). Recorre una
     * vez el disco de la aldea —la columna entera de arriba abajo en la banda de la cota— y solo se llama cuando el
     * plano <b>no</b> tiene nada pendiente, así que el coste se paga únicamente en aldeas sanas.
     */
    @Nullable
    private static BlockPos buscarAgujeroEnElSuelo(ServerLevel level, int objectiveIndex, @Nullable BlockPos centro,
            BlockPos from, Set<Long> excluir, UUID builder) {
        if (centro == null) {
            return null;
        }
        int cota = VillageGenerator.cotaDeLaPlaza(level, centro);
        int radio = VillageGenerator.FENCE_RADIUS;
        BlockPos mejor = null;
        double mejorDist = REPAIR_SEARCH_RADIUS * REPAIR_SEARCH_RADIUS;
        for (int dx = -radio; dx <= radio; dx++) {
            for (int dz = -radio; dz <= radio; dz++) {
                if (dx * dx + dz * dz > radio * radio) {
                    continue;
                }
                int x = centro.getX() + dx;
                int z = centro.getZ() + dz;
                double dist = from.distSqr(new BlockPos(x, cota, z));
                if (dist >= mejorDist) {
                    continue; // más lejos que el mejor agujero que ya tengo: ni se miran los bloques
                }
                // De ARRIBA abajo: el primer agujero de la columna (el más alto con suelo debajo) es el que toca.
                for (int dy = 1; dy >= -AGUJERO_MAX_PROFUNDIDAD; dy--) {
                    BlockPos pos = new BlockPos(x, cota + dy, z);
                    long comprimida = pos.asLong();
                    if (excluir.contains(comprimida) || reclamadoPorOtro(level, comprimida, builder)) {
                        continue;
                    }
                    if (!esAgujeroDelSuelo(level, objectiveIndex, pos)) {
                        continue;
                    }
                    mejorDist = dist;
                    mejor = pos;
                    break;
                }
            }
        }
        return mejor;
    }

    /**
     * ¿Hay que reponer algo en esa posición? Se repone si está en <b>aire</b> (lo típico: lo rompió un asedio) o si
     * el bloque que hay es el resultado de un destrozo concreto: la <b>tierra de cultivo se convierte en tierra</b>
     * cuando alguien salta encima, así que si el plano dice tierra de cultivo (o la acequia) y ahora hay tierra o
     * hierba, se vuelve a poner. Cualquier otra cosa (lo que haya puesto el jugador) no se toca.
     * <p>
     * La tierra de cultivo <b>con otra humedad NO está rota</b> (ver más abajo).
     */
    private static boolean necesitaReparacion(BlockState actual, BlockState esperado) {
        if (actual.isAir()) {
            return true;
        }
        if (actual.equals(esperado)) {
            return false;
        }
        // Misma clase de bloque y el resto son PROPIEDADES que cambian solas: la HUMEDAD de la tierra de cultivo
        // (`moisture` 0..7) la sube el juego con el agua de al lado, la baja en seco y la vuelve a subir con la
        // lluvia. Comparando el estado ENTERO, la celda se daba por dañada cada pocos segundos y el obrero se
        // pasaba la vida "reparando" la huerta (ver `VillageGenerator.estadoDelPlano`).
        if (actual.is(Blocks.FARMLAND) && esperado.is(Blocks.FARMLAND)) {
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
        return eraHuerta && VillageGenerator.esTierraPisoteada(actual);
    }

    /**
     * Lo mismo, mirando el plano: ¿ese hueco hay que reponerlo? (lo consulta el obrero en cada tick).
     * <p>
     * Los <b>troncos de la arboleda del pueblo</b> nunca: esa arboleda es la madera del <b>leñador</b> (tala y
     * replanta a propósito), así que reponerlos sería levantar troncos flotando (ver {@link #findRepairTarget}).
     */
    public static boolean necesitaReparacion(ServerLevel level, int objectiveIndex, BlockPos pos) {
        BlockState esperado = blueprintState(level, objectiveIndex, pos);
        if (esperado == null) {
            // NO ESTÁ EN EL PLANO: todavía puede ser un AGUJERO DEL SUELO (el cráter que deja un creeper), que es lo
            // que nadie tapaba. Ver `esAgujeroDelSuelo`.
            return esAgujeroDelSuelo(level, objectiveIndex, pos);
        }
        BlockPos centro = centroDe(level, objectiveIndex);
        if (centro != null && VillageGenerator.enLaArboleda(centro, pos)
                && esperado.is(net.minecraft.tags.BlockTags.LOGS)) {
            return false;
        }
        return necesitaReparacion(level.getBlockState(pos), esperado);
    }

    /** Materiales del <b>suelo</b> de la aldea: lo que el generador pone al nivelar y al tapar barrancas y cuevas. */
    private static final Set<net.minecraft.world.level.block.Block> SUELO_DE_LA_ALDEA = Set.of(Blocks.DIRT,
            Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT,
            Blocks.PODZOL, Blocks.ROOTED_DIRT, Blocks.MUD, Blocks.GRAVEL, Blocks.SAND, Blocks.STONE,
            Blocks.ANDESITE, Blocks.DIORITE, Blocks.GRANITE, Blocks.DIRT_PATH, Blocks.FARMLAND);

    /** Hasta qué profundidad se considera "agujero del suelo" (un cráter de creeper no pasa de 3-4 bloques). */
    private static final int AGUJERO_MAX_PROFUNDIDAD = 4;

    /**
     * <b>¿Es un AGUJERO DEL SUELO de la aldea?</b> (lo que deja un creeper al estallar, o lo que cave un bicho): una
     * celda de <b>aire</b> dentro del recinto, en la banda de la cota, con <b>suelo justo debajo</b>.
     * <p>
     * Hace falta porque la reparación de siempre va <b>por el plano</b>, y un cráter en terreno natural <b>no está en
     * el plano</b>: el jugador lo vio tal cual —*"hay un hoyo que dejó un creeper durante el asedio, ¿por qué nadie lo
     * está reparando? Ahí está Leoncio el recolector, él debería de ser también constructor"*— y medido en su partida
     * (`build/obras_pendientes2.py`): **0 celdas pendientes en el plano** y el hoyo ahí, sin que nadie lo tocara.
     * <p>
     * Se tapa <b>de abajo arriba</b>: cada celda tapada deja suelo debajo de la de encima, así que la siguiente
     * pasada la ve y la tapa también; el cráter se rellena solo, capa a capa, igual que lo haría el jugador.
     * <p>
     * <b>La capa de aire donde se anda NO es un agujero</b> ({@code dy >= 0} se descarta). Esto no es un detalle: la
     * cota es la <b>Y del aire sobre el suelo</b> ({@code VillageGenerator.groundY} devuelve {@code suelo + 1}), así
     * que con la banda vieja ({@code dy <= +1}) el aire en el que el aldeano tiene los <b>pies</b> contaba como
     * agujero —tiene suelo debajo, que es la hierba— y el obrero se declaraba <b>agujero a sí mismo</b>: medido en el
     * arnés ({@code MEDIR_AGUJERO}, aldea 2 de la copia), los tres obreros devolvían {@code veObjetivo} = su propia
     * {@code blockPosition} y su meta era <b>poner un bloque de hierba donde estaban de pie</b>. El cráter de verdad,
     * en cambio, está <b>por debajo</b> de la capa de aire: el suelo de la aldea llega hasta {@code cota - 1}. Por eso
     * el agujero es {@code cota - 1} hacia abajo y nunca la capa de arriba.
     */
    public static boolean esAgujeroDelSuelo(ServerLevel level, int objectiveIndex, BlockPos pos) {
        if (!level.getBlockState(pos).isAir()) {
            return false;
        }
        BlockPos centro = centroDe(level, objectiveIndex);
        if (centro == null) {
            return false;
        }
        double dx = pos.getX() - centro.getX();
        double dz = pos.getZ() - centro.getZ();
        if (dx * dx + dz * dz > (double) VillageGenerator.FENCE_RADIUS * VillageGenerator.FENCE_RADIUS) {
            return false; // fuera del recinto: el campo se deja como está (la naturaleza hace lo suyo)
        }
        int cota = VillageGenerator.cotaDeLaPlaza(level, centro);
        int dy = pos.getY() - cota;
        // La capa de la cota es AIRE DE TRANSITO (ahí se anda, ver groundY): un agujero empieza justo debajo. Y hacia
        // abajo, hasta donde llega un cráter; más allá es una cueva y no se tapa.
        if (dy > -1 || dy < -AGUJERO_MAX_PROFUNDIDAD) {
            return false;
        }
        // LA MINA NO ES UN AGUJERO QUE TAPAR (I102): un pozo de mina es, por bloques, idéntico a un cráter de
        // creeper (aire con suelo de aldea debajo), así que sin esta exclusión el obrero lo rellenaba a los diez
        // segundos y el minero cavaba contra él para siempre. Lo pidió el jugador: *"su lugar de trabajo no lo debe
        // regenerar ningún otro trabajador, ya que se taladraría seguido; quien puede regenerar lo que construya es
        // el propio minero"*.
        if (VillageGenerator.estaSobreElPozo(centro, pos)) {
            return false;
        }
        return esSueloDeLaAldea(level.getBlockState(pos.below()));
    }

    private static boolean esSueloDeLaAldea(BlockState state) {
        return SUELO_DE_LA_ALDEA.contains(state.getBlock());
    }

    /**
     * El bloque con el que se tapa un hueco: <b>el del plano</b> si esa celda está en el plano (así se repone
     * exactamente lo que había: el camino, la huerta, la hierba) y, si no está, el del <b>suelo</b>: <b>hierba</b> en
     * la capa de arriba y <b>tierra</b> por debajo, que es como está hecho el suelo de la aldea (el generador nivela
     * con tierra y pone hierba encima).
     * <p>
     * La capa de arriba es {@code cota - 1}, <b>no</b> la cota: la cota es la Y del <b>aire</b> sobre el suelo
     * ({@code VillageGenerator.groundY} devuelve {@code suelo + 1}), así que el último bloque sólido del suelo está
     * justo debajo. Poniendo la hierba en {@code y >= cota} el cráter se tapaba con <b>tierra</b> (y encima se
     * levantaba un escalón de hierba en la capa de aire): medido en el arnés, el agujero abierto en
     * {@code cota - 1} pedía {@code minecraft:dirt} en vez de {@code minecraft:grass_block}.
     */
    @Nullable
    public static BlockState bloqueParaReparar(ServerLevel level, int objectiveIndex, BlockPos pos) {
        BlockState delPlano = blueprintState(level, objectiveIndex, pos);
        if (delPlano != null) {
            return delPlano;
        }
        if (!esAgujeroDelSuelo(level, objectiveIndex, pos)) {
            return null;
        }
        BlockPos centro = centroDe(level, objectiveIndex);
        int cota = centro == null ? pos.getY() : VillageGenerator.cotaDeLaPlaza(level, centro);
        return (pos.getY() >= cota - 1 ? Blocks.GRASS_BLOCK : Blocks.DIRT).defaultBlockState();
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

    /**
     * El bloque que debería haber en esa posición según el plano de la aldea ({@code null} si no está en él).
     * <p>
     * Se pasa por {@link VillageGenerator#estadoDelPlano}: una <b>puerta de valla</b> se repone <b>siempre cerrada</b>
     * (el plano de una aldea vieja puede tenerla guardada abierta —medido en el guardado del jugador, aldea 2: el
     * portón del corral estaba {@code open:true} en el plano—, y el obrero la reconstruía abierta cada vez que un
     * asedio se la llevaba) y la <b>tierra de cultivo</b> entra <b>sin humedad</b> (el plano no guarda un estado
     * transitorio; el obrero la riega al reponerla, ver {@code VillagerRepairGoal}).
     */
    @Nullable
    public static BlockState blueprintState(ServerLevel level, int objectiveIndex, BlockPos pos) {
        VillageSavedData.Blueprint plano = VillageSavedData.get(level).getBlueprint(objectiveIndex);
        if (plano == null) {
            return null;
        }
        long comprimida = pos.asLong();
        for (int i = 0; i < plano.size(); i++) {
            if (plano.positions()[i] == comprimida) {
                return VillageGenerator.estadoDelPlano(plano.stateAt(i));
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
     * <b>Reparte las raciones</b>: <b>una ración = un punto de comida</b> (lo mismo que comía la
     * aldea antes, ahora repartido boca por boca) por cada aldeano que lleve sin comer {@link #EAT_INTERVAL_TICKS} o
     * más, y <b>primero al que hace más tiempo que no come</b> (si la comida no llega para todos, el hambre se
     * reparte en vez de cebar siempre a los mismos). Cada ración sale <b>de verdad</b> de la despensa.
     * <p>
     * El reloj que manda es <b>el de cada aldeano</b> ({@link #ultimaComida}), no el del mundo: ver el comentario
     * de abajo. Las <b>crías no gastan ración</b> (maman de la aldea) y su reloj no corre (ver {@link #pasarHambre}).
     * <p>
     * OJO con cómo se saca: se pide el total <b>por valor y de una sola vez</b>. Sacando un punto por boca, uno a
     * uno, cada aldeano se llevaría una <b>hogaza entera</b> (4 puntos: {@code sacarComida} redondea a piezas
     * completas) y el pueblo comería cuatro veces más de lo que le toca. {@code sacarComida} devuelve los puntos que
     * de verdad había, así que se marca como comidos a los que les tocó (los que llevaban más tiempo sin comer).
     *
     * @return cuántas raciones se han repartido (0 = el pueblo no tiene nada que dar)
     */
    private static int repartirRaciones(ServerLevel level, BlockPos center, List<Villager> aldeanos) {
        // LA COMIDA LA MANDA EL RELOJ DE LOS ALDEANOS, NO UN TICK DEL MUNDO. Aquí había una puerta
        // `gameTime % EAT_INTERVAL_TICKS != 0 -> return 0` ("las raciones se reparten en el latido del minuto") que
        // ataba la comida de TODO el pueblo a UN tick exacto de cada minuto: si en ese tick el latido no corría —el
        // jugador lejos, o el latido cortado porque hay bichos dentro (I12/I46)— el pueblo entero se saltaba esa
        // comida aunque los aldeanos llevaran su minuto esperando, y la siguiente no llegaba hasta el minuto
        // siguiente. Ahora la comida la pide **el aldeano que hace más tiempo que no come**: cuando ese cumple su
        // intervalo, come el pueblo que esté esperando (el grupo sigue sincronizado porque una comida los marca a
        // todos a la vez, así que en la práctica es la misma comida de antes, solo que ya no se pierde por un latido
        // que no corrió).
        Container despensa = VillagePantry.despensa(level, center);
        if (despensa == null || aldeanos.isEmpty()) {
            return 0;
        }
        // El hambre de cada uno, leída UNA vez por aldeano (y de paso se le estrena la marca al que llega nuevo).
        Map<Villager, Long> ultima = new HashMap<>();
        long laMasVieja = Long.MAX_VALUE; // la marca del que hace MÁS tiempo que no come (las crías no cuentan)
        for (Villager villager : aldeanos) {
            long suya = ultimaComida(level, villager);
            ultima.put(villager, suya);
            if (!villager.isBaby() && suya < laMasVieja) {
                laMasVieja = suya;
            }
        }
        if (laMasVieja == Long.MAX_VALUE || level.getGameTime() - laMasVieja < EAT_INTERVAL_TICKS) {
            return 0; // todavía no le toca a nadie
        }
        List<Villager> bocas = new ArrayList<>();
        for (Villager villager : aldeanos) {
            if (!villager.isBaby() && level.getGameTime() - ultima.get(villager) >= EAT_INTERVAL_TICKS) {
                bocas.add(villager); // las crías maman de la aldea: no gastan ración (y su reloj no corre, I52)
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
        int bocasSinRacion = 0;
        for (Villager villager : aldeanos) {
            if (villager.isBaby()) {
                // UNA CRÍA MAMA DE LA ALDEA: no gasta ración (ver `repartirRaciones`) y SU RELOJ DE COMIDA NO CORRE.
                // Hay que REFRESCARLE la marca, no solo saltárselo: su `DevilRpgUltimaComida` se estrena el día que
                // nace (lo estrena `ultimaComida` la primera vez que el latido la ve) y ya no se toca más, así que
                // el día que CREZCA —vanilla, 20 min— aparece con 20 min "sin comer", pasa el umbral de muerte
                // (`STARVATION_DEATH_TICKS`, 10 min) y muere en el primer latido, con la despensa llena.
                // Medido en el guardado del jugador (aldea 2, centro 1414,1414, cota 120): Ubaldo, cría "Sin
                // oficio", murió "de hambre (19 min sin comer)" y el chat dijo *"la despensa esta vacia"* con el
                // contador en 64 puntos (y 986 en la despensa de verdad); sus dos compañeras de cría (Mauricio,
                // marca 86400, y Nicasio, 92400) seguían con la marca del día en que nacieron.
                marcarComida(level, villager);
                continue;
            }
            long sinComer = level.getGameTime() - ultimaComida(level, villager);
            if (sinComer < HAMBRE_PACIENTE_TICKS) {
                continue;
            }
            algunaBocaSinComer = true;
            bocasSinRacion++;
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
                BlockPos centro = centroDe(level, objectiveIndex); // el aviso es para el jugador que esté cerca
                // LO QUE SE DICE ES LO QUE SE HA MEDIDO. El aviso cantaba *"la despensa esta vacia"* SIEMPRE que
                // hubiera una boca sin su ración, sin mirar la despensa: el jugador lo vio con el cofre de comida
                // delante (aldea 2: `comida 64 puntos`, y 986 en la despensa de verdad) y con razón dejó de
                // creerse el cartel. Ahora el aviso lleva delante lo que se ha contado.
                int enLaDespensa = centro == null ? 0 : VillagePantry.comida(level, centro);
                DevilRpg.LOGGER.info("[Village] La aldea {} pasa hambre: {} boca(s) sin su racion y {} punto(s) en"
                        + " la despensa", objectiveIndex, bocasSinRacion, enLaDespensa);
                if (centro != null) {
                    announceNearby(level, centro, enLaDespensa <= 0
                            ? "La aldea pasa hambre: la despensa esta vacia."
                            : "La aldea pasa hambre: " + bocasSinRacion + " boca(s) sin su racion (quedan "
                                    + enLaDespensa + " puntos en la despensa).");
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
            // LOS CRIOS CRECEN MÁS RÁPIDO (lo pidió el jugador: *"los niños deben crecer más rápido para suplir a los
            // aldeanos muertos en la noche"*). Vanilla tarda 20 minutos; aquí se les envejece un extra por latido, así
            // que crecen al triple (~7 min) y una noche mala se repone en un par de días. Al llegar a 0 son adultos.
            if (villager.isBaby()) {
                villager.setAge(Math.min(0, villager.getAge() + VILLAGE_POLL_TICKS * (BABY_GROWTH_SPEEDUP - 1)));
            }
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
     * <b>Siembra el Diario del Invocado</b> en una partida ya empezada: marca como descubiertas (y reveladas) las
     * aldeas que el mundo ya dio por resueltas, porque son aldeas en las que el jugador estuvo. Es <b>idempotente por
     * construcción</b>: en cuanto tiene una apuntada, sale sin recorrer nada.
     */
    private static void sembrarElDiarioSiHaceFalta(VillageSavedData saved, ServerPlayer player) {
        PlayerAuxiliaryCapabilityInterface aux =
                IGenericCapability.getUnwrappedPlayerCapability(player, PlayerAuxiliaryCapability.INSTANCE);
        if (aux == null || !aux.getAldeasVisitadas().isEmpty()) {
            return; // no hay capability, o ya tiene aldeas apuntadas: nada que sembrar
        }
        int sembradas = 0;
        for (int i = 0; i <= MAX_OBJECTIVES; i++) {
            if (saved.isGenerated(i) && saved.isSiegeResolved(i)) {
                aux.visitarAldea(i, player);
                aux.revelarAldea(i, player);
                sembradas++;
            }
        }
        if (sembradas > 0) {
            DevilRpg.LOGGER.info("[Village] Diario del Invocado sembrado para {}: {} aldea(s) que ya resolvio esta"
                    + " partida", player.getName().getString(), sembradas);
        }
    }

    /** Hacia dónde cae la aldea SIGUIENTE, para los avisos que no pueden enseñar la distancia (ver I87). */
    private static String rumboALaSiguiente(int objectiveIndex, BlockPos center, PlayerAuxiliaryCapabilityInterface aux) {
        Vec3 ancla = aux.getAnchorPoint();
        if (ancla == null) {
            ancla = aux.getSpawnPoint();
        }
        if (ancla == null) {
            return "noreste";
        }
        BlockPos objetivo = ObjectiveTargets.targetOf(ancla, objectiveIndex + 1);
        return ObjectiveTargets.direccionHacia(center, objetivo);
    }

    /**
     * <b>Al salvarse una aldea se anuncia su NOMBRE</b> (y el Diario del Invocado se pone al día). La <b>dirección</b>
     * de la siguiente <b>NO</b> se revela aquí: eso es al <b>hablar con el clérigo</b>
     * ({@link #elClerigoSenalaLaAldeaActual}). Lo pidió el jugador: *"cuando se gane el asedio aparezca el nombre de
     * la aldea y se actualice el libro del invocado, pero SOLO cuando se hable con el clérigo es cuando ya aparezca en
     * los objetivos hacia dónde está la aldea y su distancia como actualmente está"*.
     */
    private static void anunciarLaAldeaSalvada(ServerPlayer player, int objectiveIndex) {
        String nombre = VillageNames.nombre(objectiveIndex);
        player.displayClientMessage(Component.literal("Has salvado " + nombre + ". Los clérigos hablan de otra aldea:"
                + " háblale al clérigo del pueblo y te encenderá el camino."), false);
        com.chipoodle.devilrpg.item.DiarioDelInvocado.actualizarSiLoTiene(player);
        DevilRpg.LOGGER.info("[Village] Aldea {} ({}) salvada: se anuncia su nombre y se pone al dia el Diario; la"
                + " direccion de la siguiente, solo al hablar con el clerigo", objectiveIndex, nombre);
    }

    /**
     * <b>Hablar con el clérigo</b>: es el que <b>pasa a la aldea siguiente</b>. Mientras la aldea actual no se haya
     * salvado, la barra se queda en ella (con su nombre); cuando el jugador le habla al clérigo <b>después de
     * salvarla</b>, el objetivo <b>avanza</b> a la siguiente y el clérigo le <b>revela la dirección y la distancia</b>
     * —sin nombre todavía: el nombre llega al entrar en ella—. Lo pidió el jugador: *"una vez ganado el asedio
     * APAREZCA en la barra de aldea el nombre de la aldea actual recién ganada y sólo cuando vaya con el clérigo
     * cambie al siguiente objetivo que es la siguiente aldea y su distancia sin revelar aún el nombre"*.
     * <p>
     * Vale cualquier clérigo de cualquier pueblo del mod (los clérigos son la orden que invocó al jugador, así que
     * todos saben lo mismo). Si ya se la habían señalado, lo dice con otras palabras y no repite el aviso.
     */
    public static void elClerigoSenalaLaAldeaActual(ServerLevel level, Villager clerigo, ServerPlayer player) {
        PlayerAuxiliaryCapabilityInterface aux =
                IGenericCapability.getUnwrappedPlayerCapability(player, PlayerAuxiliaryCapability.INSTANCE);
        if (aux == null) {
            return;
        }
        Vec3 ancla = aux.getAnchorPoint();
        if (ancla == null) {
            ancla = aux.getSpawnPoint();
        }
        if (ancla == null) {
            return;
        }
        VillageSavedData saved = VillageSavedData.get(level);
        int actual = aux.getObjectiveIndex();
        // ¿LA ACTUAL YA SE SALVÓ? Entonces el clérigo le pasa a la SIGUIENTE (avanza el objetivo). Si no (te están
        // asediando, o la anterior cayó y esta es la que te toca), le señala la que le toca ahora mismo.
        boolean salvada = saved.isSiegeResolved(actual) && !saved.isFallen(actual);
        int objetivo = salvada ? actual + 1 : actual;
        if (salvada) {
            aux.setObjectiveIndex(objetivo, player);
        }
        BlockPos destino = ObjectiveTargets.targetOf(ancla, objetivo);
        String hacia = ObjectiveTargets.direccionHacia(player.blockPosition(), destino);
        double dx = destino.getX() + 0.5D - player.getX();
        double dz = destino.getZ() + 0.5D - player.getZ();
        int metros = (int) Math.sqrt(dx * dx + dz * dz);
        String quien = "El clérigo " + nombreDe(clerigo);
        if (aux.isAldeaRevelada(objetivo) || aux.isAldeaVisitada(objetivo)) {
            player.displayClientMessage(Component.literal(quien + ": \"Ya te lo dije: la aldea cae hacia el " + hacia
                    + ", a unos " + metros + " pasos. La barra de aldea te guía.\""), false);
            return;
        }
        aux.revelarAldea(objetivo, player);
        player.displayClientMessage(Component.literal(quien + ": \"" + (salvada
                ? "Habéis salvado este pueblo. Los clérigos sentimos otra aldea hacia el "
                : "Los clérigos sentimos una aldea hacia el ")
                + hacia + ", a unos " + metros + " pasos de aquí. Mira arriba: la barra de aldea ya te guía; camina con"
                + " la runa encendida.\""), false);
        DevilRpg.LOGGER.info("[Village] El clerigo {} señala la aldea {} a {} (hacia el {}, {} m{})", nombreDe(clerigo),
                objetivo, player.getName().getString(), hacia, metros, salvada ? ", tras salvar la " + actual : "");
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
        // Si el beacon del sello VIEJO sigue en el tejado del kiosco, se apaga: su celda vuelve a ser la PIEDRA del
        // tejado, nunca aire. De esa celda CUELGA el farol del kiosco (I14) y dejarla en aire —como se hacía— se
        // llevaba el farol por delante. (Desde la migración 58 el sello no enciende ningún beacon.)
        int cota = VillageGenerator.cotaDeLaPlaza(level, center);
        BlockPos sello = new BlockPos(center.getX(), cota + 5, center.getZ());
        if (level.getBlockState(sello).is(Blocks.BEACON)) {
            level.setBlockAndUpdate(sello, Blocks.STONE_BRICKS.defaultBlockState());
        }
        VillageGenerator.ruin(level, center, objectiveIndex);
        DevilRpg.LOGGER.info("[Village] La aldea {} ha CAÍDO y queda en ruinas", objectiveIndex);
    }

    /**
     * <b>Sello místico de la aldea</b>: al vencer su asedio, la aldea queda protegida del poder de la oscuridad
     * (ninguna criatura hostil puede <b>aparecer</b> entre sus muros; fuera, en el campo, sí) y se levanta un
     * <b>haz de luz</b> sobre el kiosco como señal. El sello dura mientras la aldea viva: si cae (todos los aldeanos
     * muertos) queda abandonada y el poder se apaga.
     * <p>
     * El sello vive en los <b>datos de la aldea</b> ({@code VillageSavedData.isSiegeResolved}, que es lo que miran la
     * protección y el haz de partículas de {@link #efectosDeAldeas}), <b>no en un bloque</b>: hasta la migración 58
     * se encendía además un <b>beacon</b> en el tejado del kiosco, que <b>no hacía nada</b> (un beacon sin pirámide
     * no da efecto ni luz) y que el jugador mandó quitar: <i>"quita el beacon pues nunca se usa"</i>.
     */
    private static void marcarSelloMistico(BlockPos center, ServerPlayer player) {
        player.displayClientMessage(Component.literal(
                "La campana tañe una sola vez... y un zumbido antiguo recorre el empedrado: "
                        + "el poder místico sella la aldea. Ninguna criatura de la oscuridad podrá alzarse entre sus muros."), false);
        DevilRpg.LOGGER.info("[Village] Aldea en {}: sello místico activo (no aparecerán enemigos dentro)", center);
    }

    /**
     * Efectos visuales de las aldeas, cada pocos ticks: el <b>haz de luz</b> del sello místico (una columna de
     * partículas sobre el kiosco de la plaza) y las <b>partículas oscuras</b> de los enemigos que entran en una aldea
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
            // HAZ DE LUZ: columna de partículas brillantes sobre el kiosco (se ve a lo lejos). Es la ÚNICA señal del
            // sello desde la migración 58 (el beacon del tejado se quitó: no hacía nada).
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
        /** Si ya se avisó (una sola vez) de que esta aldea no cae porque no hay nadie que la defienda. */
        boolean avisadoSinDefensor;

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
        /** El reloj está parado porque el jugador no está en la aldea (ver {@link #RADIO_ASEDIO_CON_JUGADOR}). */
        boolean enPausa;

        VillageDefense(int objectiveIndex, UUID playerUUID, BlockPos center) {
            this.objectiveIndex = objectiveIndex;
            this.playerUUID = playerUUID;
            this.center = center;
        }
    }
}
