package com.chipoodle.devilrpg.world;

import com.chipoodle.devilrpg.DevilRpg;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Genera una aldea simple (cabañas con puerta y cama + aldeanos + valla de madera con puertas) en un
 * punto del mundo. Antes de construir se limpia la vegetación del interior y se nivela el terreno a un
 * nivel base (rellenando los hoyos con tierra, suavizando la pendiente sin aplanarlo del todo). Las
 * cabañas y la valla se asientan al terreno nivelado, la valla se cierra de forma continua y conectada
 * (sin huecos en las diagonales) para que los aldeanos puedan transitar.
 */
public final class VillageGenerator {

    /**
     * Radio de la valla. El pueblo ha crecido por etapas (29 → 36 → <b>62</b>): con 36 la <b>granja de animales</b>
     * (el corral anexo, que ocupa de 43 a 57 del centro) quedaba <b>fuera de la muralla</b>, y eso traía dos
     * problemas medidos en la partida del jugador: los monstruos aparecían dentro del corral de noche y se comían al
     * rebaño, y la guardia no llegaba a defenderlo. Con el muro a 62 <b>la granja entera cabe dentro</b> (el corral
     * queda a 4 bloques del muro) y la aldea tiene sitio para repartir los solares, la iglesia, el taller, la barraca,
     * las parcelas y la arboleda sin apelotonarlos en el centro.
     */
    public static final int FENCE_RADIUS = 62;

    /**
     * Coordenadas del trazado, <b>relativas al centro</b> del pueblo. Antes eran las del trazado de radio 36 y todas
     * cabían apretadas en el centro; con el muro a 62 se han <b>repartido</b> por el recinto (cada cuadrante a su
     * aire, sin solaparse con el almacén, que sigue pegado a la plaza) para que el pueblo <b>llene</b> la muralla:
     * <ul>
     *   <li><b>Casas</b>: (-36,-7) oeste, (28,-16) este-norte, (-9,36) sur, (12,-32) norte (la grande).</li>
     *   <li><b>Iglesia</b> (-21,-45) y <b>taller de los herreros</b> (3,-47), al norte, cada uno en su hueco.</li>
     *   <li><b>Barraca de la milicia</b> (-45,22), al suroeste.</li>
     *   <li><b>Parcelas de la granja</b> (-30,14) y (10,4): la primera al oeste (lejos de la barraca) y la segunda
     *       pegada a la plaza, que es donde el granjero trabaja y deja el trigo.</li>
     *   <li><b>Arboleda</b>: (34..40, -41..-31), en la diagonal noreste, con sitio de sobra.</li>
     *   <li><b>Corral anexo</b>: a 50 del centro (de 43 a 57), ahora <b>dentro</b> de la muralla a 4 bloques de ella.</li>
     * </ul>
     * El <b>almacén</b> no se mueve (va con {@code VillageStorage}, pegado a la plaza): moverlo dejaría los cofres
     * del pueblo —y todo lo que tiene dentro— tirados por el recinto viejo.
     */
    private static final int[][] TRAZADO = {
            // x, z  y qué es (para leerlo de un vistazo)
            {-36, -7},   // casa 1 (oeste)
            {28, -16},   // casa 2 (este)
            {-9, 36},    // casa 3 (sur)
            {12, -32},   // casa 4 (norte, la grande)
            {-21, -45},  // iglesia
            {3, -47},    // taller de los herreros
            {-45, 22},   // barraca de la milicia
            {-30, 14},   // parcela de la granja 1
            {10, 4},     // parcela de la granja 2
            {-28, 34},   // parcela de la granja 3 (la del tercer bancal, con su granjero)
    };

    /** La posición del trazado {@code i} (ver {@link #TRAZADO}) relativa al centro. */
    private static BlockPos trazado(BlockPos center, int i) {
        return center.offset(TRAZADO[i][0], 0, TRAZADO[i][1]);
    }

    /** Esquinas de las <b>tres</b> parcelas de la granja (relativas al centro) y tamaño de cada parcela. */
    private static final int[][] FARM_PLOTS = {{-30, 14}, {10, 4}, {-28, 34}};
    /** Ancho de la parcela (columnas de cultivo). */
    public static final int PLOT_WIDTH = 9;
    /**
     * Fondo de la parcela: la acequia va en la fila del medio, así que salen <b>4 carriles de cultivo por lado</b>
     * (antes 2: la parcela era de fondo 5 y la producción se quedaba corta: el jugador lo pidió).
     */
    public static final int PLOT_DEPTH = 9;
    /** Fila de la acequia dentro de la parcela (la del medio). */
    private static final int PLOT_WATER_ROW = PLOT_DEPTH / 2;
    /** Bloques de <b>terraza</b> (patio llano) que se allanan alrededor de una construcción. */
    private static final int MARGEN_TERRAZA = 2;
    /**
     * Camas que caben en una casa <b>según su tamaño</b>: las pequeñas (interior de unos 5x5) aguantan 2 y las
     * grandes/medianas 4. Meter más dejaba las camas apiladas o tapando el pasillo (el jugador lo vio).
     */
    private static int camasSegunTamano(Vec3i tam) {
        int interior = Math.max(1, tam.getX() - 2) * Math.max(1, tam.getZ() - 2);
        return interior >= 45 ? 4 : 2;
    }
    /**
     * Hasta cuántos bloques por debajo de la superficie se busca suelo al rellenar el solar de una construcción
     * (la casa mediana dejaba huecos de dos bloques: ver {@code placeVanillaHouse}).
     */
    private static final int PROFUNDIDAD_SOLAR = 4;

    /**
     * Radio del área que se nivela alrededor del centro (todo hasta donde empieza la valla, para que no
     * queden huecos ni abismos entre la zona nivelada y la valla).
     */
    private static final int LEVEL_RADIUS = FENCE_RADIUS + 2;

    /** Profundidad máxima (en bloques hacia abajo) de la estructura flotante bajo la isla. */
    private static final int ISLAND_SUPPORT_DEPTH = 9;

    /** Ancho (bloques) del talud exterior que suaviza el borde de la aldea (meseta natural). */
    private static final int SLOPE_WIDTH = 10;
    /**
     * Hasta dónde llega la aldea <b>por fuera</b>: el final del talud. Es el número que hay que mirar desde fuera
     * (lo usa la guarida para no pisar el pueblo) cuando cambie el radio de la muralla.
     */
    public static final int RADIO_EXTERIOR = LEVEL_RADIUS + SLOPE_WIDTH;
    /** Cuántos bloques baja el terreno a lo largo del talud. */
    private static final int SLOPE_HEIGHT = 5;

    /** Dirección hacia afuera de la puerta (la cabaña mira al norte). */
    private static final Direction FRONT = Direction.NORTH;

    private VillageGenerator() {
    }

    /** Busca tierra firme (no agua) cerca de {@code origin}, escaneando en anillos hacia afuera. */
    public static BlockPos findLand(ServerLevel level, BlockPos origin) {
        for (int r = 0; r < 24; r++) {
            for (int x = origin.getX() - r; x <= origin.getX() + r; x++) {
                for (int z = origin.getZ() - r; z <= origin.getZ() + r; z++) {
                    int y = groundY(level, x, z);
                    Block below = level.getBlockState(new BlockPos(x, y - 1, z)).getBlock();
                    if (below != Blocks.WATER && below != Blocks.LAVA
                            && level.getBlockState(new BlockPos(x, y, z)).isAir()) {
                        return new BlockPos(x, y, z);
                    }
                }
            }
        }
        return origin;
    }

    /**
     * <b>Regla de agua compartida</b> por la aldea y la guarida: devuelve el nivel del agua (su superficie) si
     * la zona cae sobre agua, o {@code -1} si es tierra firme.
     * <p>
     * Se considera "sobre agua" si la <b>columna central</b> es agua (que es lo que se miraba antes) <b>o si
     * el agua es al menos la mitad de la zona</b>. Mirar solo la columna central fallaba en la costa: el
     * centro caía en tierra, el resto de la zona en el mar, se elegía el camino de tierra y, como la mediana
     * de alturas se iba al fondo marino, la obra quedaba <b>sumergida</b>. Con la mitad o más de la zona en
     * tierra, en cambio, la mediana ya cae en tierra y las columnas de agua se rellenan hasta ese nivel, así
     * que no hace falta isla.
     *
     * @param radius radio de la zona a revisar (incluyendo el talud, para detectar la costa a tiempo)
     */
    public static int waterSurfaceForArea(ServerLevel level, BlockPos center, int radius) {
        int centerSurface = waterSurface(level, center.getX(), center.getZ());
        List<Integer> surfaces = new ArrayList<>();
        int columns = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z > radius * radius) continue; // disco, igual que la plataforma
                columns++;
                int surface = waterSurface(level, center.getX() + x, center.getZ() + z);
                if (surface >= 0) {
                    surfaces.add(surface);
                }
            }
        }
        if (columns == 0 || surfaces.isEmpty()) {
            return -1;
        }
        if (centerSurface < 0 && surfaces.size() * 2 < columns) {
            return -1; // centro en tierra y el agua es minoría: se nivela como siempre
        }
        Collections.sort(surfaces);
        return surfaces.get(surfaces.size() / 2); // mediana: estable en costas
    }

    /**
     * Genera las casas, los aldeanos, los caminos y la valla alrededor del centro.
     * <p>
     * Devuelve el <b>plano canónico</b> de la aldea: mientras construye las estructuras, la grabadora apunta cada
     * bloque que coloca ({@link #colocar}), así que el plano es <b>lo que la aldea debe ser</b> y no una foto de
     * lo que quedó. El terreno (limpieza, nivelado, isla) se hace ANTES de encender la grabadora, porque eso no
     * forma parte del diseño que el obrero debe reponer.
     */
    public static VillageSavedData.Blueprint generate(ServerLevel level, BlockPos center) {
        // IDEMPOTENCIA (lo pidió el jugador: "haz algo para que no se vuelvan a repetir"): si la aldea YA está
        // construida, generar se sale SIN TOCAR NADA. Sin este guardia, una segunda pasada de generación (dos
        // caminos que llamen a generate, una aldea que se dio por no generada...) despeja el volumen y vuelve a
        // levantar todo ENCIMA: las casas pierden lo que tengan dentro y, sobre todo, la huerta se queda en brotes
        // con los vegetales tirados por la parcela (medido en el banco de pruebas, que llamaba a generate dos veces:
        // 90 cultivos de 216 y 207 pilas de vegetales por el suelo). El testigo es el BANCO DE CULTIVO: solo lo
        // planta el generador y se planta al final, así que si hay bancal la aldea se construyó entera.
        if (bancalHecho(level, center.offset(FARM_PLOTS[0][0], 0, FARM_PLOTS[0][1]),
                cotaDeLaPlaza(level, center))) {
            DevilRpg.LOGGER.info("[Village] Aldea en {}: ya estaba construida (hay bancal): no se vuelve a generar",
                    center);
            return captureBlueprint(level, center);
        }
        // Despejar hasta cubrir el talud exterior (que rodea el área nivelada): dentro del volumen de la aldea no
        // queda nada que no sea terreno (ni vegetación ni restos de estructuras del mundo).
        despejarVolumen(level, center, LEVEL_RADIUS + SLOPE_WIDTH);
        // Si la zona cae sobre agua (ver waterSurfaceForArea), construir una isla flotante AL NIVEL DEL AGUA:
        // la aldea no se puede mover del objetivo, así que si el objetivo cayó en el océano o un lago, se
        // levanta la isla. Si no, nivelar el terreno como siempre.
        //
        // OJO: de aquí sale la COTA DE LA ALDEA (el nivel al que se coloca TODO). Se hereda del propio terreno, sin
        // muestrear nada: muestrear un anillo con `groundY` era el error de siempre, porque sobre una casa devuelve
        // el TEJADO y la cota salía un bloque alta (medido en el guardado: suelo a y=62 y suelos de casa a y=63).
        int waterLevel = waterSurfaceForArea(level, center, LEVEL_RADIUS + SLOPE_WIDTH);
        int nivelVilla;
        if (waterLevel >= 0) {
            buildFloatingIsland(level, center, LEVEL_RADIUS, waterLevel);
            nivelVilla = waterLevel + 1; // el suelo de la isla va al nivel del agua: se anda un bloque por encima
        } else {
            nivelVilla = levelTerrain(level, center, LEVEL_RADIUS);
        }

        // --- A partir de aquí se GRABA el plano canónico (solo estructuras, no terreno) ---
        iniciarGrabacion();
        // Posiciones de las casas (base). Desde la Iteración 3 refinada son CASAS DE VERDAD, plantillas del
        // propio juego (ver placeVanillaHouse), no cabañas procedurales. Los solares salen de basesDeCasas: una
        // sola lista, para que el generador y la migración no se puedan desincronizar (antes estaban escritos dos
        // veces y al agrandar la aldea una de las dos copias se quedaba con el trazado viejo).
        BlockPos[] bases = basesDeCasas(center);

        // CASAS PRIMERO: hay que colocarlas para saber dónde quedó cada puerta (cada plantilla la trae donde
        // quiere) y que los caminos lleguen de verdad a ella. La última es la "grande" (con cama extra), y en el
        // sitio de la vieja torre va la IGLESIA del juego.
        RandomSource casas = RandomSource.create(center.asLong());
        BlockPos[] puertas = new BlockPos[7];
        puertas[0] = placeVanillaHouse(level, bases[0], casaAleatoria(casas), nivelVilla);
        puertas[1] = placeVanillaHouse(level, bases[1], casaAleatoria(casas), nivelVilla);
        puertas[2] = placeVanillaHouse(level, bases[2], casaAleatoria(casas), nivelVilla);
        puertas[3] = placeVanillaHouse(level, bases[3], casaGrandeAleatoria(casas), nivelVilla);
        puertas[4] = placeVanillaHouse(level, baseDeIglesia(center), iglesiaAleatoria(casas), nivelVilla);
        // HERRERÍA: el taller de los dos herreros, con la construcción de herrero del propio juego (fragua, muelle de
        // afilar y arca). Se le pone dentro la mesa de herrería del herrero de HERRAMIENTAS, que la plantilla no trae.
        puertas[5] = placeVanillaHouse(level, baseDeHerreria(center), HERRERIAS[0], nivelVilla);
        // El herrero de HERRAMIENTAS necesita su mesa de herrería: la plantilla del de armas solo trae el muelle.
        puestoDeTrabajo(level, baseDeHerreria(center), nivelVilla, Blocks.SMITHING_TABLE);
        // BARRACA de la milicia (donde viven los guardias): va DESPUÉS de las casas, para que su solar no pise
        // ninguno de sus solares ni el bancal, y ANTES de los caminos, para que su puerta tenga el suyo.
        puertas[6] = barraca(level, baseDeBarraca(center), nivelVilla);

        // Caminos DESPUÉS, del centro a la puerta de cada construcción (ya se sabe dónde está).
        paths(level, center, puertas);

        // Kiosco de la plaza: plataforma con 4 salidas, la campana ARRIBA y el cofre doble de la despensa dentro.
        kiosco(level, center, nivelVilla);

        // LA TABERNA (etapa F): el comedor del pueblo, con la cocina del cocinero abajo y la posada (camas) arriba.
        asegurarTaberna(level, center);
        // Y SU CAMINO: el que sale de la plaza y llega hasta la puerta oeste (la que da a la plaza).
        caminoALaTaberna(level, center);
        // LA PESQUERA (etapa G): el lago, la caseta del pescador y su barril (el pueblo ya usa barriles: son suyos).
        asegurarPesquera(level, center);
        caminoALaPesquera(level, center);

        // Granja: da trabajo al aldeano granjero y produce la comida que come la aldea (Iteración 3). Se le pasa
        // LA COTA YA CALCULADA: si la recalculara aquí, la muestra del terreno incluiría las casas y la iglesia
        // recién colocadas (`groundY` sobre un tejado devuelve el tejado) y el nivelado subiría la aldea un
        // bloque: casas hundidas, zanjas y el muro enterrado (el bug que reportó el jugador).
        farm(level, center, nivelVilla);

        // LA ARBOLEDA DEL PUEBLO: cuatro plantones en un hueco de césped de la diagonal noreste. Es lo que da madera a
        // una aldea que nace sin bosque (una islita): el leñador los tala y los replanta como cualquier árbol.
        asegurarArboleda(level, center);
        // Y LA ORILLA, seca y pareja, si la aldea nació al nivel del agua (si no, no se toca nada).
        asegurarOrilla(level, center);
        // NIEVE Y VEGETACIÓN QUE QUEDÓ COLGANDO del recorte (aldea de montaña): se limpia antes de dar por hecha la
        // aldea, que si no queda nieve polvo flotando por encima del pueblo (medido: 469 bloques en la suya).
        limpiarRestosColgados(level, center);

        // Remesa inicial de la despensa (semillas, abono y un par de panes): el kiosco ya tiene el cofre doble.
        VillagePantry.remesaInicial(VillagePantry.despensa(level, center));

        // Aldeanos frente a las casas, y el golem que protege la aldea.
        spawnVillagers(level, center);

        // Faroles con poste distribuidos por la aldea (evitan spawn de zombies con la mecánica vanilla).
        torches(level, center);

        // (La vieja torre de vigilancia procedural ya no se pone: en su sitio va la IGLESIA del juego, que se
        // coloca arriba con las demás construcciones y trae campanario.)

        fence(level, center);

        // AUTOCOMPROBACIÓN (guardia del bug de los faroles flotantes): si algo quedó colgado del aire, sale en el log.
        auditarFarolesFlotantes(level, center);

        return terminarGrabacion();
    }

    // --- Plano canónico de la aldea (lo que el obrero debe reponer) ---------------------------------

    /**
     * Grabadora del <b>plano canónico</b>: mientras está activa (solo durante la construcción de las estructuras)
     * apunta cada bloque que el generador coloca. Guarda por <b>posición</b> (no una lista con repetidos), así que
     * si un bloque se coloca y luego se sustituye, queda el último estado.
     */
    private static final class GrabadoraDePlano {
        private final Map<Long, BlockState> bloques = new LinkedHashMap<>();

        void apunta(BlockPos pos, BlockState state) {
            bloques.put(pos.asLong(), state);
        }

        /** ¿Ese bloque se descarta del plano? Misma regla que {@link #seDescartaDelPlano} (aire y terreno natural,
         *  conservando la tierra de cultivo y el agua de la granja). */
        private boolean seDescarta(BlockState state) {
            return seDescartaDelPlano(state);
        }

        /**
         * Convierte lo grabado en el plano: se descartan el aire (los despejes) y el terreno natural. El resultado
         * es la <b>paleta</b> más dos arrays paralelos (posiciones comprimidas e índices de paleta).
         */
        VillageSavedData.Blueprint aPlano() {
            List<BlockState> palette = new ArrayList<>();
            Map<BlockState, Integer> indices = new HashMap<>();
            List<Long> posiciones = new ArrayList<>();
            List<Integer> estados = new ArrayList<>();
            for (Map.Entry<Long, BlockState> entrada : bloques.entrySet()) {
                // El estado BUENO (ver `estadoDelPlano`): una puerta de valla entra en el plano siempre cerrada.
                BlockState state = estadoDelPlano(entrada.getValue());
                if (seDescarta(state)) {
                    continue;
                }
                Integer indice = indices.get(state);
                if (indice == null) {
                    indice = palette.size();
                    palette.add(state);
                    indices.put(state, indice);
                }
                posiciones.add(entrada.getKey());
                estados.add(indice);
            }
            long[] posicionesArray = new long[posiciones.size()];
            int[] estadosArray = new int[estados.size()];
            for (int i = 0; i < posicionesArray.length; i++) {
                posicionesArray[i] = posiciones.get(i);
                estadosArray[i] = estados.get(i);
            }
            return new VillageSavedData.Blueprint(palette, posicionesArray, estadosArray);
        }
    }

    /** Grabadora activa ({@code null} = no se está grabando: el terreno no entra en el plano). */
    private static GrabadoraDePlano grabadora = null;

    private static void iniciarGrabacion() {
        grabadora = new GrabadoraDePlano();
    }

    private static VillageSavedData.Blueprint terminarGrabacion() {
        VillageSavedData.Blueprint plano = grabadora == null ? null : grabadora.aPlano();
        grabadora = null;
        return plano;
    }

    /**
     * Nivela la <b>huella</b> de una construcción (más un pequeño patio alrededor) a la cota que le pasan y devuelve
     * esa cota.
     * <p>
     * La cota es SIEMPRE la de la aldea ({@link #prepararTerreno}), nunca una calculada aquí: intentar deducir el
     * nivel de cada construcción por su cuenta fue el origen de todas las casas altas, hundidas y de las zanjas
     * alrededor (y de paso, muestrear con {@code groundY} cerca de una casa devuelve el <b>tejado</b>). Se
     * <b>recorta</b> el terreno que sobra (solo si es natural, nunca lo que hayas construido tú) y se <b>rellena</b>
     * con tierra lo que falta.
     */
    private static int nivelarHuella(ServerLevel level, BlockPos base, int anchoX, int anchoZ, int nivel) {
        // Se allana la huella MÁS una terraza alrededor (MARGEN_TERRAZA) a la COTA DE LA ALDEA que nos pasan: al no
        // calcular un nivel propio, la casa no puede quedar ni más alta ni más baja que el suelo del pueblo, así que
        // no aparecen zanjas (que era lo que pasaba al nivelar cada casa por su cuenta).
        for (int dx = -MARGEN_TERRAZA; dx < anchoX + MARGEN_TERRAZA; dx++) {
            for (int dz = -MARGEN_TERRAZA; dz < anchoZ + MARGEN_TERRAZA; dz++) {
                int x = base.getX() + dx;
                int z = base.getZ() + dz;
                int suelo = groundY(level, x, z);
                for (int y = nivel; y < suelo; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    // OJO: `esTerrenoRecortable`, NO `esTerrenoNatural`. Éste último da los TRONCOS por terreno y
                    // este recorte se los comía: la terraza de una construcción (o el margen de una parcela de la
                    // granja, que se solapa con la casa de al lado) le borraba a la casa los postes de tronco de la
                    // pared -> "le falta parte de la pared entre ventanas" (medido en el guardado: los 11 bloques
                    // que faltaban eran una fila entera de postes, en la fila del margen de la parcela).
                    if (esTerrenoRecortable(level.getBlockState(p))) {
                        colocar(level, p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
                for (int y = suelo; y < nivel; y++) {
                    BlockState actual = level.getBlockState(new BlockPos(x, y, z));
                    if (!actual.isAir() && !esTerrenoRecortable(actual)) {
                        continue; // no se tapa nada construido (ni un tronco del muro o de una casa)
                    }
                    // La capa que se pisa va con CÉSPED (no tierra): si el patio se rellenó, se ve verde como el
                    // resto y no como un borde marrón alrededor de la casa.
                    colocar(level, new BlockPos(x, y, z),
                            (y == nivel - 1 ? Blocks.GRASS_BLOCK : Blocks.DIRT).defaultBlockState(),
                            Block.UPDATE_CLIENTS);
                }
                // Si el nivelado RECORTÓ el terreno, la capa que se pisa se quedó con la tierra de debajo a la vista:
                // se le devuelve el césped para que no se vea un parche marrón alrededor de la construcción.
                ponerCesped(level, x, z, nivel);
            }
        }
        return nivel;
    }

    /** Le devuelve el césped a la capa que se pisa si el nivelado la dejó con tierra (nunca toca una construcción). */
    private static void ponerCesped(ServerLevel level, int x, int z, int nivel) {
        BlockPos p = new BlockPos(x, nivel - 1, z);
        BlockState actual = level.getBlockState(p);
        if (actual.is(Blocks.DIRT) || actual.is(Blocks.COARSE_DIRT) || actual.is(Blocks.PODZOL)
                || actual.is(Blocks.ROOTED_DIRT) || actual.is(Blocks.MYCELIUM)) {
            colocar(level, p, Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * Edad actual de un cultivo, leyendo la propiedad {@code age} <b>de su propio estado</b>.
     * <p>
     * Hace falta porque cada cultivo tiene su rango: el trigo/zanahoria/patata usan {@code CropBlock.AGE} (0-7) y el
     * <b>betabel</b> tiene la suya (0-3). Usar {@code CropBlock.AGE} a secas con el betabel petaba
     * ({@code Cannot set property age ... does not exist in Block{minecraft:beetroots}}) y tumbaba el mundo al generar
     * una aldea. {@code CropBlock.getAgeProperty()} es {@code protected}, así que se busca en el estado.
     */
    public static int edadDelCultivo(BlockState state) {
        for (Property<?> p : state.getProperties()) {
            if (p instanceof IntegerProperty edad && "age".equals(edad.getName())) {
                return state.getValue(edad);
            }
        }
        return -1;
    }

    /** Pone un cultivo en su edad <b>máxima</b>, con su propia propiedad de edad. */
    public static BlockState cultivoMaduro(BlockState state) {
        for (Property<?> p : state.getProperties()) {
            if (p instanceof IntegerProperty edad && "age".equals(edad.getName())) {
                int max = state.getBlock() instanceof CropBlock crop
                        ? crop.getMaxAge()
                        : edad.getPossibleValues().size() - 1;
                return state.setValue(edad, Math.min(max, edad.getPossibleValues().size() - 1));
            }
        }
        return state;
    }

    /**
     * Pone un cultivo <b>recién plantado</b> (edad 0), con su propia propiedad de edad. Es como se siembra la huerta
     * de una aldea nueva: ver {@code plot()}.
     */
    public static BlockState cultivoInicial(BlockState state) {
        for (Property<?> p : state.getProperties()) {
            if (p instanceof IntegerProperty edad && "age".equals(edad.getName())) {
                return state.setValue(edad, 0);
            }
        }
        return state;
    }

    /**
     * Esquina de las dos parcelas de la granja de esa aldea. Lo usan los aldeanos que trabajan la tierra
     * ({@code VillagerFarmGoal}) para saber dónde plantar y cosechar, y el obrero para no poner faroles encima.
     * <p>
     * La Y es <b>LA COTA DE LA ALDEA</b> (la capa por la que se anda, donde están los cultivos), nunca la Y del centro
     * del objetivo ni la del centro que se saca del plano: con una Y mala el granjero buscaba los cultivos decenas de
     * bloques por debajo del suelo, no veía ninguno y se pasaba el día dando vueltas sin cosechar.
     */
    public static BlockPos[] parcelasDe(ServerLevel level, BlockPos center) {
        int cota = cotaDeLaPlaza(level, center);
        BlockPos[] parcelas = new BlockPos[FARM_PLOTS.length];
        for (int i = 0; i < FARM_PLOTS.length; i++) {
            parcelas[i] = new BlockPos(center.getX() + FARM_PLOTS[i][0], cota, center.getZ() + FARM_PLOTS[i][1]);
        }
        return parcelas;
    }

    /**
     * Asegura el <b>kiosco de la plaza</b> (y con él la <b>despensa</b>: el cofre doble de dentro) en aldeas que
     * todavía no lo tienen. Es idempotente: si el cofre ya está a la cota del pueblo, no toca nada.
     * <p>
     * Ojo con la comprobación: se hace por la <b>ALTURA DE LA ALDEA</b> y buscando el <b>cofre</b>, no por la Y del
     * centro. Con la comprobación vieja (barril + Y del centro) cada latido colocaba otro contenedor y, como
     * {@code groundY} cuenta el contenedor como suelo, la despensa subía un bloque por latido dejando una columna de
     * piedra debajo (bug que vio el jugador).
     */
    public static void asegurarKiosco(ServerLevel level, BlockPos center) {
        int nivel = cotaDeLaPlaza(level, center);
        if (nivel <= level.getMinBuildHeight() + 1) {
            return;
        }
        // El testigo es la PLATAFORMA (su poste), no la despensa: el cofre de la comida vive desde la migración 46
        // en la cocina de la taberna (lo pidió el jugador). Con el testigo viejo —que exigía el cofre— el kiosco se
        // reconstruía en cada latido buscando un cofre que ya no está en él (y reconstruirlo tira lo de dentro).
        if (level.getBlockState(new BlockPos(center.getX() + KIOSCO_RADIO, nivel, center.getZ()))
                .is(Blocks.STONE_BRICKS)) {
            return; // el kiosco ya está y con el tamaño actual
        }
        kiosco(level, center, nivel);
        DevilRpg.LOGGER.info("[Village] Aldea en {}: kiosco de la plaza colocado a la cota {}", center, nivel);
    }

    /**
     * Asegura el <b>almacén</b> de la aldea: un cobertizo de piedra con <b>cofres dobles</b> donde el constructor (que
     * también es recolector) deja lo que recoge por el pueblo. <b>Crece solo</b>: cuando sus cofres se llenan, se
     * añade otro cofre doble en el siguiente hueco (hasta {@code VillageStorage.MAX_COFRES} dobles).
     * <p>
     * Desde la migración 45 el cobertizo es de <b>7×7</b> y tiene <b>seis</b> cofres dobles (doce cofres) en vez de
     * cinco por cinco con tres: va <b>al lado de la taberna</b>, a su espalda, y el jugador pidió sitio para que
     * sigan entrando cofres conforme crece.
     */
    public static void asegurarAlmacen(ServerLevel level, BlockPos center) {
        int nivel = cotaDeLaPlaza(level, center);
        if (nivel <= level.getMinBuildHeight() + 1) {
            return;
        }
        BlockPos c = VillageStorage.centro(center);
        if (!(level.getBlockState(new BlockPos(c.getX(), nivel, c.getZ())).is(Blocks.STONE_BRICKS))) {
            // Cobertizo 7x7: suelo de piedra, ocho postes de tronco y tejado de tablones.
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    colocar(level, new BlockPos(c.getX() + dx, nivel, c.getZ() + dz),
                            Blocks.STONE_BRICKS.defaultBlockState(), 3);
                }
            }
            for (int sx = -3; sx <= 3; sx += 3) {
                for (int sz = -3; sz <= 3; sz += 3) {
                    for (int i = 1; i <= 3; i++) {
                        colocar(level, new BlockPos(c.getX() + sx, nivel + i, c.getZ() + sz),
                                Blocks.OAK_LOG.defaultBlockState(), 3);
                    }
                }
            }
            // Y los cuatro postes de en medio, para que el tejado de 7x7 no quede colgando de las esquinas solas.
            for (int k = -3; k <= 3; k += 3) {
                for (int i = 1; i <= 3; i++) {
                    colocar(level, new BlockPos(c.getX() + k, nivel + i, c.getZ()), Blocks.OAK_LOG.defaultBlockState(), 3);
                    colocar(level, new BlockPos(c.getX(), nivel + i, c.getZ() + k), Blocks.OAK_LOG.defaultBlockState(), 3);
                }
            }
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    colocar(level, new BlockPos(c.getX() + dx, nivel + 4, c.getZ() + dz),
                            Blocks.OAK_PLANKS.defaultBlockState(), 3);
                }
            }
            // Farol colgado del tejado: el almacén se ve (y se ilumina) de noche.
            colocar(level, new BlockPos(c.getX(), nivel + 3, c.getZ()),
                    Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 3);
            DevilRpg.LOGGER.info("[Village] Aldea en {}: almacen construido en {}", center, c);
        }
        // Cofres: primero se recolocan los que hayan quedado flotando (bug de la Y del centro) y, si no hay ninguno o
        // si están TODOS llenos, se coloca otro doble EN EL SUELO DEL ALMACÉN (el almacén crece hacia dentro).
        VillageStorage.repararCofresFlotantes(level, center);
        if (VillageStorage.cofresColocados(level, center) == 0 || VillageStorage.lleno(level, center)) {
            if (VillageStorage.colocarSiguientePar(level, center)) {
                DevilRpg.LOGGER.info("[Village] Aldea en {}: almacen ampliado ({} cofres)",
                        center, VillageStorage.cofresColocados(level, center));
            }
        }
    }

    /**
     * <b>Mueve el almacén</b> del pueblo a su sitio nuevo (al este de la taberna, lejos de su puerta principal) sin
     * perder nada: primero <b>pasa lo que tengan los cofres viejos</b> al almacén nuevo, después <b>retira el
     * cobertizo viejo</b> (solo sus bloques: lo que haya puesto el jugador no se toca) y por último levanta el nuevo.
     * <p>
     * Es idempotente: si el cobertizo nuevo ya está y no queda ningún cofre viejo, no hace nada. Lo pidió el jugador:
     * <i>"el almacén está demasiado pegado a la puerta principal de la taberna; ponlo a lado o intégralo dentro de la
     * taberna con suficiente espacio para que se vayan poniendo más cofres"</i>.
     */
    public static void moverAlmacen(ServerLevel level, BlockPos center) {
        int nivel = cotaDeLaPlaza(level, center);
        if (nivel <= level.getMinBuildHeight() + 1) {
            return;
        }
        BlockPos nuevo = VillageStorage.centro(center);
        BlockPos viejo = VillageStorage.centroViejo(center);
        boolean nuevoHecho = level.getBlockState(new BlockPos(nuevo.getX(), nivel, nuevo.getZ()))
                .is(Blocks.STONE_BRICKS);
        // 1) LO QUE HUBIERA EN LOS COFRES VIEJOS, AL ALMACÉN NUEVO (y si no cupiera, al suelo del nuevo, donde el
        //    recolector del pueblo lo recoge: tirar un cofre tira su contenido al suelo).
        asegurarAlmacen(level, center);
        int movidos = 0;
        for (BlockPos p : VillageStorage.cofresViejos(level, center)) {
            if (!(level.getBlockEntity(p) instanceof Container contenedor)) {
                continue;
            }
            for (int i = 0; i < contenedor.getContainerSize(); i++) {
                ItemStack pila = contenedor.getItem(i);
                if (pila.isEmpty()) {
                    continue;
                }
                ItemStack sobra = VillageStorage.guardar(level, center, pila.copy());
                contenedor.setItem(i, ItemStack.EMPTY);
                movidos++;
                if (!sobra.isEmpty()) {
                    Block.popResource(level, nuevo.above(), sobra);
                }
            }
            contenedor.setChanged();
        }
        // 2) EL COBERTIZO VIEJO SE RETIRA (solo sus bloques: el suelo de piedra, los postes, el tejado, el farol y
        //    los cofres ya vaciados).
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos suelo = new BlockPos(viejo.getX() + dx, nivel, viejo.getZ() + dz);
                if (level.getBlockState(suelo).is(Blocks.STONE_BRICKS)) {
                    colocar(level, suelo, Blocks.AIR.defaultBlockState(), 3);
                }
                for (int y = nivel + 1; y <= nivel + 4; y++) {
                    BlockPos q = new BlockPos(viejo.getX() + dx, y, viejo.getZ() + dz);
                    BlockState estado = level.getBlockState(q);
                    if (estado.is(Blocks.OAK_LOG) || estado.is(Blocks.OAK_PLANKS) || estado.is(Blocks.LANTERN)
                            || estado.getBlock() instanceof ChestBlock) {
                        colocar(level, q, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
        if (!nuevoHecho || movidos > 0) {
            DevilRpg.LOGGER.info("[Village] Aldea en {}: almacen movido de {} a {} ({} pila(s) de objeto pasadas"
                    + " al nuevo)", center, viejo, nuevo, movidos);
        }
    }

    /**
     * Solar de la <b>BARRACA</b> de la milicia, relativo al centro: al <b>oeste</b> del pueblo (a 26,13), que es el
     * cuadrante que queda libre (entre la casa del noroeste, el bancal de la granja y la puerta oeste del muro) y
     * deja el edificio entero dentro de la valla.
     */
    public static BlockPos baseDeBarraca(BlockPos center) {
        return trazado(center, 6);
    }

    /** Radio de la barraca (huella de 9x9). */
    private static final int BARRACA_RADIO = 4;
    /** Camas de la barraca: dos filas de 4, una contra cada pared larga. */
    public static final int BARRACA_CAMAS = 8;

    /**
     * Asegura la <b>BARRACA de la milicia</b> en una aldea que todavía no la tiene (migración y latido). Es
     * idempotente: comprueba el <b>suelo a la cota</b> (como el kiosco y el almacén) y, si ya está, no toca nada —
     * reconstruirla borraría las camas y lo que los guardias tengan dentro.
     */
    public static void asegurarBarraca(ServerLevel level, BlockPos center) {
        int nivel = cotaDeLaPlaza(level, center);
        if (nivel <= level.getMinBuildHeight() + 1) {
            return;
        }
        BlockPos base = baseDeBarraca(center);
        // El barril de la primera versión (etapa F) pasa a COFRE: el barril es el puesto del PESCADOR y aquí no hay
        // pescadores (todavía). Se arregla en el sitio, sin rehacer la barraca.
        BlockPos barril = new BlockPos(base.getX() - 2, nivel, base.getZ() + BARRACA_RADIO - 2);
        if (level.getBlockState(barril).is(Blocks.BARREL)) {
            // lint:ok I9 porque no se añade construcción: es un retrofit en el sitio de la pasada idempotente.
            colocar(level, barril, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH), 3);
        }
        // Testigo del trazado NUEVO (etapa F: barraca de DOS PISOS con sala de armas): el hogar del patio de
        // entrenamiento. Una barraca de una planta (sin hogar) se vuelve a levantar entera, que es lo que trae el
        // segundo piso con las camas y el patio.
        if (level.getBlockState(new BlockPos(base.getX(), nivel - 1, base.getZ() + BARRACA_RADIO - 1))
                .is(Blocks.CAMPFIRE)) {
            return; // la barraca ya está y con el trazado actual
        }
        BlockPos puerta = barraca(level, base, nivel);
        // Camino de la plaza a su puerta (si no, los guardias tienen que trepar por el césped).
        paths(level, center, doorApproach(level, puerta));
        DevilRpg.LOGGER.info("[Village] Aldea en {}: barraca de la milicia construida en {} (dos pisos: sala de"
                + " armas abajo y {} camas arriba)", center, base, BARRACA_CAMAS);
    }

    /**
     * Construye la <b>barraca</b> (etapa F: <b>dos pisos</b>) y devuelve su puerta.
     * <p>
     * Abajo, la <b>sala de armas</b>: el patio de entrenamiento de la milicia, con el suelo de piedra (los guardias
     * entrenan ahí), <b>maniquíes</b> de paja con calabaza (para ensayar el golpe), <b>dianas</b> para los arqueros,
     * un banco de armas (vallas con farol) y el fuego del hogar. Arriba, el <b>dormitorio</b>: {@link #BARRACA_CAMAS}
     * camas en dos filas, con sus arcas y faroles.
     * <p>
     * <b>Alturas</b> (invariante I1): {@code nivel} es <b>la capa que se pisa</b> del pueblo, así que el suelo
     * sólido va en {@code nivel - 1} y las paredes, la puerta y las camas de abajo en {@code nivel}; el forjado del
     * piso de arriba en {@code nivel + 3} y sus camas en {@code nivel + 4}.
     * <p>
     * Todo pasa por {@link #colocar}, así que <b>entra en el plano</b> y el obrero la repone como cualquier otra
     * construcción (invariante I8): una barraca construida al margen del plano no se repararía nunca.
     */
    private static BlockPos barraca(ServerLevel level, BlockPos base, int nivel) {
        int r = BARRACA_RADIO;
        int bx = base.getX();
        int bz = base.getZ();
        int yPiso2 = nivel + 4;   // suelo del dormitorio (el forjado va en yPiso2 - 1)
        int yTejado = yPiso2 + 3;
        // 1) Huella NIVELADA a la cota del pueblo, como las casas: recorta el terreno natural que sobra y
        //    RELLENA lo que falta. Hace falta de verdad: medido en el guardado, el cuadrante oeste de alguna aldea
        //    tiene un charco (23 columnas de agua en la capa de superficie) y en otra faltaba el bloque de suelo
        //    en 12 columnas: sin esto, el suelo de la barraca quedaría flotando. `nivelarHuella` toma la ESQUINA
        //    (y solo mira su X/Z, pero se le da una Y que ya es la cota: invariante I1).
        nivelarHuella(level, new BlockPos(bx - r, nivel, bz - r), 2 * r + 1, 2 * r + 1, nivel);
        // 2) SUELOS: piedra en la capa de superficie (nivel-1), el volumen de abajo al aire, el FORJADO del piso de
        //    arriba (tablones) y el volumen del dormitorio también al aire.
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                colocar(level, new BlockPos(bx + dx, nivel - 1, bz + dz), Blocks.STONE_BRICKS.defaultBlockState(), 3);
                for (int dy = 0; dy <= 2; dy++) {
                    colocar(level, new BlockPos(bx + dx, nivel + dy, bz + dz), Blocks.AIR.defaultBlockState(), 3);
                }
                colocar(level, new BlockPos(bx + dx, yPiso2 - 1, bz + dz), Blocks.OAK_PLANKS.defaultBlockState(), 3);
                for (int dy = 0; dy <= 2; dy++) {
                    colocar(level, new BlockPos(bx + dx, yPiso2 + dy, bz + dz), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        // 3) PAREDES de los dos pisos por el borde de la huella: piedra en la base, tablones, postes de tronco y
        //    troneras (vallas) que son lo que le da su aire de cuartel.
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (Math.abs(dx) != r && Math.abs(dz) != r) {
                    continue; // interior
                }
                boolean poste = (dx == -r || dx == r) && (dz == -r || dz == r);
                for (int dy = 0; dy < (yTejado - nivel); dy++) {
                    BlockPos p = new BlockPos(bx + dx, nivel + dy, bz + dz);
                    int piso = dy < 3 ? nivel : yPiso2;
                    if (dy == 0 || dy == (yPiso2 - nivel)) {
                        colocar(level, p, Blocks.COBBLESTONE.defaultBlockState(), 3);
                    } else if (poste) {
                        colocar(level, p, Blocks.OAK_LOG.defaultBlockState(), 3);
                    } else if (dy == piso - nivel + 2 && (dx + dz) % 3 == 0) {
                        colocar(level, p, Blocks.OAK_FENCE.defaultBlockState(), 3); // tronera
                    } else {
                        colocar(level, p, Blocks.OAK_PLANKS.defaultBlockState(), 3);
                    }
                }
            }
        }
        // 4) Puerta de dos bloques en el centro de la pared NORTE, a la capa que se pisa (`nivel`): es por donde
        //    llega el camino de la plaza y se entra sin escalón.
        BlockPos puerta = new BlockPos(bx, nivel, bz - r);
        colocar(level, puerta, Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 3);
        colocar(level, puerta.above(), Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
        // 5) LA SALA DE ARMAS (piso de abajo): maniquíes de paja (para ensayar el golpe), dianas de los arqueros, el
        //    hogar con su fuego y una mesa con el mapa de las guaridas.
        int[][] maniquies = {{-r + 2, -r + 2}, {r - 2, r - 2}};
        for (int[] m : maniquies) {
            colocar(level, new BlockPos(bx + m[0], nivel, bz + m[1]), Blocks.HAY_BLOCK.defaultBlockState(), 3);
            colocar(level, new BlockPos(bx + m[0], nivel + 1, bz + m[1]), Blocks.CARVED_PUMPKIN.defaultBlockState(), 3);
            colocar(level, new BlockPos(bx + m[0], nivel, bz + m[1] + 1), Blocks.OAK_FENCE.defaultBlockState(), 3);
            colocar(level, new BlockPos(bx + m[0], nivel + 2, bz + m[1]), Blocks.OAK_FENCE.defaultBlockState(), 3);
        }
        colocar(level, new BlockPos(bx - r + 2, nivel, bz + r - 2), Blocks.TARGET.defaultBlockState(), 3);
        colocar(level, new BlockPos(bx + r - 2, nivel, bz - r + 2), Blocks.TARGET.defaultBlockState(), 3);
        colocar(level, new BlockPos(bx + r - 2, nivel + 1, bz - r + 2), Blocks.TARGET.defaultBlockState(), 3);
        colocar(level, new BlockPos(bx, nivel - 1, bz + r - 1), Blocks.CAMPFIRE.defaultBlockState(), 3);
        colocar(level, new BlockPos(bx + 2, nivel, bz + r - 2), Blocks.CARTOGRAPHY_TABLE.defaultBlockState(), 3);
        colocar(level, new BlockPos(bx - 2, nivel, bz + r - 2), Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH), 3);
        // 6) LA ESCALERA al dormitorio, pegada a la pared sur, con el hueco en el forjado.
        for (int i = 0; i < 4; i++) {
            BlockPos escalon = new BlockPos(bx + r - 1, nivel + i, bz + r - 1 - i);
            colocar(level, escalon, Blocks.OAK_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.WEST).setValue(StairBlock.HALF, Half.BOTTOM), 3);
            colocar(level, escalon.above(), Blocks.AIR.defaultBlockState(), 3);
            colocar(level, new BlockPos(bx + r - 1, yPiso2 - 1, bz + r - 1 - i), Blocks.AIR.defaultBlockState(), 3);
        }
        // 7) EL DORMITORIO (piso de arriba): las camas en dos filas con el pasillo en medio, con su arca y sus
        //    faroles. Son las que dan litera a los guardias y las que dejan crecer al pueblo (vanilla pide una cama
        //    libre por cría).
        for (int dx = -r + 1; dx <= r - 1; dx += 2) {
            bed(level, new BlockPos(bx + dx, yPiso2, bz - r + 2), Direction.NORTH);
            bed(level, new BlockPos(bx + dx, yPiso2, bz + r - 2), Direction.SOUTH);
        }
        colocar(level, new BlockPos(bx - r + 1, yPiso2, bz), Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.EAST), 3);
        colocar(level, new BlockPos(bx + r - 1, yPiso2, bz), Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.WEST), 3);
        for (int dz = -r + 2; dz <= r - 2; dz += 3) {
            colocar(level, new BlockPos(bx, yPiso2 + 2, bz + dz), Blocks.LANTERN.defaultBlockState(), 3);
        }
        // 8) TEJADO (a la altura del segundo piso) con alero de un bloque de sobra y faroles colgados del centro: de
        //    noche la barraca se ve desde lejos y no spawnean monstruos dentro (que es lo que evitaría que la
        //    guardia durmiera).
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                colocar(level, new BlockPos(bx + dx, yTejado, bz + dz), Blocks.OAK_PLANKS.defaultBlockState(), 3);
            }
        }
        colocar(level, new BlockPos(bx, nivel + 2, bz - 1),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 3);
        colocar(level, new BlockPos(bx, nivel + 2, bz + 1),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 3);
        return puerta;
    }

    // --- GRANJA ANEXA DE ANIMALES (etapa D: fuera de la valla, con su aldeano y dentro del patrullaje) -----------

    /** Distancia del centro de la aldea al centro del corral. */
    private static final int ANEXO_DX = 50;
    /**
     * Radio del corral: <b>huella de 19x19</b> desde la migración 45 (antes 15x15). El jugador lo pidió al ver a los
     * animales apretados y algunos fuera: el corral <b>no se encoge nunca</b>, se ensancha. Con 9 el corral sigue
     * entero dentro de la muralla (la valla queda a 3 bloques del muro) y cabe el doble de rebaño sin amontonarse.
     */
    public static final int ANEXO_RADIO = 9;
    /** Radio del corral <b>viejo</b> (15x15): lo usa la migración 45 para retirar su valla y su cobertizo. */
    private static final int ANEXO_RADIO_VIEJO = 7;
    /** Ancho (en Z) del camino que baja de la puerta este del muro al corral. */
    private static final int ANEXO_CAMINO_ANCHO = 5;
    /**
     * Cuánto se espera antes de volver a soltar el <b>rebaño inicial</b> si el corral se quedó <b>sin ningún
     * animal</b> (los mató una horda, se los llevó el jugador...). Es <b>1 día de juego</b> (antes 3): la granja no se
     * queda muerta para siempre, pero tampoco es un grifo de carne (si no, matar las vacas y esperar un rato daría
     * comida gratis). Se bajó de 3 días a 1 porque con 3 el pueblo podía pasarse <b>una hora de juego</b> sin carne y
     * sin poder criar (medido en el guardado del jugador: el rebaño se quedó en la pareja mínima —o por debajo— tras
     * una temporada de hambre y el relevo tardaba demasiado).
     */
    public static final long ANEXO_REBANO_ESPERA_TICKS = 24000L;

    /** Especies del corral anexo (las que cría y cuida el ganadero). */
    private static final List<EntityType<? extends net.minecraft.world.entity.animal.Animal>> ANEXO_ESPECIES =
            List.of(EntityType.COW, EntityType.SHEEP, EntityType.PIG, EntityType.CHICKEN);

    /**
     * Adultos que hacen falta para que una especie del corral pueda <b>criar</b>. Con <b>dos</b> el ganadero tiene
     * pareja; con <b>uno solo</b> esa especie no se reproduce <b>nunca</b> más y el pueblo se queda sin su carne (o sin
     * lana, o sin huevos). Lo pidió el jugador: "que los aparee para que siempre haya una pareja".
     */
    public static final int PAREJA_MINIMA = 2;
    /** Gallinas que se consideran <b>bandada</b>: con menos, ni huevos ni pollos para la despensa. */
    public static final int GALLINAS_MINIMAS = 4;

    /** Las especies del corral (para que el ganadero recorra exactamente las mismas que se sueltan aquí). */
    public static List<EntityType<? extends net.minecraft.world.entity.animal.Animal>> especiesDelCorral() {
        return ANEXO_ESPECIES;
    }

    /** Centro del corral anexo (relativo al centro de la aldea): al <b>este</b>, fuera de la valla. */
    public static BlockPos baseDeAnexo(BlockPos center) {
        return center.offset(ANEXO_DX, 0, 0);
    }

    /** ¿Ese punto (X/Z) está dentro del corral anexo? (lo usan la guardia y el ganadero, sin mirar la Y). */
    public static boolean estaEnElAnexo(BlockPos center, BlockPos p) {
        BlockPos base = baseDeAnexo(center);
        int dx = p.getX() - base.getX();
        int dz = p.getZ() - base.getZ();
        return Math.abs(dx) <= ANEXO_RADIO && Math.abs(dz) <= ANEXO_RADIO;
    }

    /**
     * Punto de apoyo del corral: el suelo llano <b>dentro</b> del corral, al lado de la puerta (nunca la valla ni el
     * bebedero: la navegación no puede "llegar" a un bloque sólido, ver {@code VillagePantry.puntoDeApoyo}).
     */
    public static BlockPos puntoDeApoyoAnexo(ServerLevel level, BlockPos center) {
        int nivel = cotaDeLaPlaza(level, center);
        BlockPos base = baseDeAnexo(center);
        return new BlockPos(base.getX() - ANEXO_RADIO + 3, nivel, base.getZ());
    }

    // --- EL GALLINERO (los pollos encerrados) y los PORTONES (etapa E) -------------------------------

    /**
     * Caja del <b>gallinero</b> (interior, relativa a la base del corral): la franja <b>norte</b>, pegada a la valla
     * del corral por el oeste y el norte. Ahí no estorba a nada: el cobertizo está al este, el bebedero al sur y la
     * <b>línea de patrulla de la guardia</b> ({@code x = base-4}, {@code z} de {@code base-2} a {@code base+6}) pasa
     * fuera de la caja a propósito: un guardia no tiene que acabar dentro de un corral de pollos.
     */
    private static final int GALLINERO_X0 = -8;
    private static final int GALLINERO_X1 = -4;
    private static final int GALLINERO_Z0 = -8;
    private static final int GALLINERO_Z1 = -7;
    /** X (relativa a la base) del <b>portón</b> del gallinero, en el centro de su pared sur. */
    private static final int GALLINERO_PUERTA_X = -6;

    /** El <b>portón del corral</b> (en su valla oeste, mirando al camino de la aldea). */
    public static BlockPos portonDelCorral(BlockPos center, int nivel) {
        BlockPos base = baseDeAnexo(center);
        return new BlockPos(base.getX() - ANEXO_RADIO, nivel, base.getZ());
    }

    /** El <b>portón del gallinero</b> (en su pared sur, mirando al corral). */
    public static BlockPos portonDelGallinero(BlockPos center, int nivel) {
        BlockPos base = baseDeAnexo(center);
        return new BlockPos(base.getX() + GALLINERO_PUERTA_X, nivel, base.getZ() + GALLINERO_Z1 + 1);
    }

    /** Dónde se sueltan (y se meten) las gallinas: dentro del gallinero, en su esquina noroeste. */
    public static BlockPos centroDelGallinero(BlockPos center, int nivel) {
        BlockPos base = baseDeAnexo(center);
        return new BlockPos(base.getX() + GALLINERO_X0, nivel, base.getZ() + GALLINERO_Z0);
    }

    /**
     * Asegura el <b>suelo y la cerca del corral anexo</b> (y con la cerca, su <b>portón de valla</b>). Es idempotente
     * y va <b>aparte</b> de {@code asegurarGranjaAnexa} (que sale antes de tiempo cuando el corral ya está): así una
     * explosión no deja el corral <b>agujereado y sin portón</b> para siempre.
     * <p>
     * Medido en el guardado del jugador: la franja <b>oeste</b> del corral se quedó <b>sin suelo</b> (aire, con el
     * <b>agua del mar</b> colándose por debajo) y el terreno firme estaba <b>3-4 bloques por debajo de la cota</b>. Sin
     * apoyo, la <b>puerta de madera se cayó sola</b> (una de valla no necesita apoyo, pero entonces queda colgando) y
     * la valla y el gallinero se quedaron <b>en el aire</b>, sobre el agua. Con esto el pueblo lo repone solo al latido
     * siguiente: en su aldea fueron <b>286 bloques de suelo</b>, el portón y una valla.
     */
    public static void asegurarCercaDelAnexo(ServerLevel level, BlockPos center) {
        int nivel = cotaDeLaPlaza(level, center);
        if (nivel <= level.getMinBuildHeight() + 1 || !anexoConstruido(level, center)) {
            return; // sin cota o sin corral no hay nada que asegurar
        }
        BlockPos base = baseDeAnexo(center);
        // 1) EL SUELO: el portón necesita APOYO (una puerta de valla no, pero una de madera sí, y los animales no
        //    tienen que caerse por el agujero).
        sellarSueloDelCorral(level, base, nivel);
        // 2) LA CERCA: cada celda del anillo que se haya quedado en AIRE se vuelve a poner. Solo el aire: lo que haya
        //    puesto el jugador no se toca. El hueco del portón lleva su puerta de valla (o se cambia la puerta vieja).
        BlockPos porton = portonDelCorral(center, nivel);
        int repuestos = 0;
        for (int dx = -ANEXO_RADIO; dx <= ANEXO_RADIO; dx++) {
            for (int dz = -ANEXO_RADIO; dz <= ANEXO_RADIO; dz++) {
                if (Math.abs(dx) != ANEXO_RADIO && Math.abs(dz) != ANEXO_RADIO) {
                    continue; // solo el anillo de la valla
                }
                BlockPos p = new BlockPos(base.getX() + dx, nivel, base.getZ() + dz);
                if (p.equals(porton)) {
                    if (asegurarPorton(level, p)) {
                        repuestos++;
                    }
                    continue;
                }
                if (!level.getBlockState(p).isAir()) {
                    continue;
                }
                colocar(level, p, Blocks.OAK_FENCE.defaultBlockState(), 3);
                repuestos++;
            }
        }
        if (repuestos > 0) {
            DevilRpg.LOGGER.info("[Village] Aldea en {}: repuestos {} bloques de la cerca del corral anexo (y su porton)",
                    center, repuestos);
        }
        // 3) LA LUZ DEL CORRAL: el anexo está FUERA de la muralla, así que de noche los monstruos aparecían dentro
        //    del corral y mataban al rebaño (medido en el guardado del jugador: las ovejas pasaron de 5 a NINGUNA
        //    entre dos sesiones, y el jugador lo resumió en "el corral no está generando carne"). Con faroles en los
        //    postes de la cerca (las cuatro esquinas y los cuatro medios lados) el corral queda iluminado y no
        //    spawnean dentro. Es idempotente: donde ya hay luz, no se toca nada.
        asegurarLucesDelCorral(level, base, nivel);
    }

    /**
     * Enciende el <b>corral anexo</b>: un farol sobre cada poste de las cuatro esquinas y de los cuatro medios lados
     * de la cerca. Es lo que impide que aparezcan monstruos dentro del corral de noche (el anexo está fuera de la
     * muralla) y, con ellos, que se coman al rebaño. Solo se coloca donde <b>no hay nada</b>: lo del jugador no se toca.
     */
    private static void asegurarLucesDelCorral(ServerLevel level, BlockPos base, int nivel) {
        int puestos = 0;
        java.util.List<BlockPos> apoyos = new java.util.ArrayList<>();
        for (int dx = -ANEXO_RADIO; dx <= ANEXO_RADIO; dx += ANEXO_RADIO) {
            for (int dz = -ANEXO_RADIO; dz <= ANEXO_RADIO; dz += ANEXO_RADIO) {
                apoyos.add(new BlockPos(base.getX() + dx, nivel, base.getZ() + dz));
            }
        }
        for (int k = -ANEXO_RADIO + 2; k <= ANEXO_RADIO - 2; k += 2 * (ANEXO_RADIO - 2)) {
            apoyos.add(new BlockPos(base.getX() + k, nivel, base.getZ() - ANEXO_RADIO));
            apoyos.add(new BlockPos(base.getX() + k, nivel, base.getZ() + ANEXO_RADIO));
            apoyos.add(new BlockPos(base.getX() - ANEXO_RADIO, nivel, base.getZ() + k));
            apoyos.add(new BlockPos(base.getX() + ANEXO_RADIO, nivel, base.getZ() + k));
        }
        // La casilla que se le pasa es la DEL POSTE (la cerca va a `nivel`): el farol queda justo encima. Antes se le
        // pasaba `nivel + 2` —contando un poste que no existía— y los 8 faroles del corral quedaban FLOTANDO.
        puestos += posarFarolesFlotantes(level, apoyos);
        for (BlockPos apoyo : apoyos) {
            puestos += farolSobreElPoste(level, apoyo);
        }
        if (puestos > 0) {
            DevilRpg.LOGGER.info("[Village] Aldea en {}: {} faroles puestos en la cerca del corral anexo"
                    + " (de noche no spawnean monstruos dentro)", base, puestos);
        }
    }

    /**
     * Un <b>farol sobre un poste</b>: se le pasa la casilla del <b>apoyo</b> (el poste de la cerca, o el suelo) y el
     * farol queda <b>encima</b>. Si el apoyo está vacío se pone el poste de valla.
     * <p>
     * Antes el ayudante colocaba el farol en la casilla que le dieran y <b>daba por hecho</b> que debajo había un
     * poste. Los llamantes se equivocaron de altura (le pasaban la casilla del farol contando un poste que no
     * existía), y el guardado del jugador tenía <b>16 faroles flotando</b>: 14 en la cerca del corral (a un bloque
     * por encima del poste) y los 2 de la pesquera (a tres bloques del suelo, sobre la orilla del lago). Ahora el
     * apoyo lo garantiza el propio ayudante: un farol sin apoyo no puede volver a construirse.
     *
     * @return 1 si ha puesto el farol (0 si ese hueco ya estaba ocupado por algo del jugador)
     */
    private static int farolSobreElPoste(ServerLevel level, BlockPos apoyo) {
        BlockState abajo = level.getBlockState(apoyo);
        if (abajo.isAir()) {
            colocar(level, apoyo, Blocks.OAK_FENCE.defaultBlockState(), 3);   // el poste que falta
        } else if (!Block.canSupportCenter(level, apoyo, Direction.UP)) {
            // `canSupportCenter` es LA MISMA prueba que hace el juego para poner un farol (una valla vale: sostiene
            // por el centro), así que si esto falla el juego tampoco lo aceptaría ahí.
            return 0; // lo que hay ahí no sostiene un farol (o es del jugador): no se toca
        }
        BlockPos alto = apoyo.above();
        if (!level.getBlockState(alto).isAir()) {
            return 0; // ya hay un farol (o lo que puso el jugador)
        }
        colocar(level, alto, Blocks.LANTERN.defaultBlockState(), 3);
        return 1;
    }

    /**
     * <b>Auto-comprobación de faroles flotantes</b>: recorre el recinto de la aldea y cuenta los faroles que no
     * cuelgan de nada ni están sobre un apoyo (la misma prueba que hace el juego para ponerlos). Se llama al
     * <b>terminar de generar</b> y al <b>terminar de migrar</b> (no en el latido: son ~80.000 bloques) y, si
     * encuentra alguno, lo <b>grita en el log</b> con sus posiciones: el bug de los 16 faroles colgados del aire
     * (14 en la cerca de la granja anexa y 2 en la pesquera) no puede volver en silencio.
     *
     * @return cuántos faroles sin apoyo ha encontrado
     */
    public static int auditarFarolesFlotantes(ServerLevel level, BlockPos center) {
        int nivel = cotaDeLaPlaza(level, center);
        int radio = FENCE_RADIUS + 8;
        int flotantes = 0;
        for (int dx = -radio; dx <= radio; dx++) {
            for (int dz = -radio; dz <= radio; dz++) {
                for (int dy = -2; dy <= 18; dy++) {
                    BlockPos p = new BlockPos(center.getX() + dx, nivel + dy, center.getZ() + dz);
                    if (!level.getBlockState(p).is(Blocks.LANTERN)) {
                        continue;
                    }
                    boolean colgado = Block.canSupportCenter(level, p.above(), Direction.DOWN);
                    boolean sobre = Block.canSupportCenter(level, p.below(), Direction.UP);
                    if (colgado || sobre) {
                        continue;
                    }
                    flotantes++;
                    if (flotantes <= 10) {
                        DevilRpg.LOGGER.warn("[Village] FAROL FLOTANTE en {} (aldea en {}): ni colgado ni con apoyo",
                                p, center);
                    }
                }
            }
        }
        if (flotantes > 0) {
            DevilRpg.LOGGER.warn("[Village] Aldea en {}: {} farol(es) SIN APOYO (fallo de construccion)",
                    center, flotantes);
        }
        return flotantes;
    }

    /**
     * <b>Baja los faroles que quedaron flotando</b> en las casillas indicadas: si hay un farol sin apoyo se retira y
     * se vuelve a poner <b>sobre el apoyo</b> que le toca. Es la reparación de las aldeas ya construidas con el
     * ayudante viejo (el plano se recaptura después, así que el obrero repone la posición buena).
     */
    private static int posarFarolesFlotantes(ServerLevel level, java.util.List<BlockPos> apoyos) {
        int arreglados = 0;
        for (BlockPos apoyo : apoyos) {
            for (int dy = 1; dy <= 3; dy++) {
                BlockPos alto = apoyo.above(dy);
                if (!level.getBlockState(alto).is(Blocks.LANTERN)) {
                    continue;
                }
                colocar(level, alto, Blocks.AIR.defaultBlockState(), 3);   // el farol flotante
                arreglados += farolSobreElPoste(level, apoyo);
                break;
            }
        }
        return arreglados;
    }

    /**
     * Pone el <b>portón de valla</b> en el hueco del corral: si hay una puerta de madera vieja se retira <b>entera</b>
     * (sus dos mitades) y si el hueco está vacío (una explosión) se pone igual. No toca nada que no sea la puerta
     * vieja o el aire.
     *
     * @return {@code true} si ha puesto el portón
     */
    private static boolean asegurarPorton(ServerLevel level, BlockPos porton) {
        BlockState actual = level.getBlockState(porton);
        if (actual.is(Blocks.OAK_FENCE_GATE)) {
            return false; // ya está
        }
        if (actual.is(Blocks.OAK_DOOR)) {
            colocar(level, porton.above(), Blocks.AIR.defaultBlockState(), 3); // la mitad de arriba de la puerta
        } else if (!actual.isAir()) {
            return false; // el jugador puso otra cosa ahí: no se toca
        }
        colocar(level, porton, Blocks.OAK_FENCE_GATE.defaultBlockState()
                .setValue(FenceGateBlock.FACING, Direction.WEST)
                .setValue(FenceGateBlock.OPEN, false)
                .setValue(FenceGateBlock.IN_WALL, false), 3);
        return true;
    }

    /**
     * <b>Tapa el suelo del corral anexo</b>: rellena de tierra (con césped en la capa que se pisa) las columnas del
     * recinto que estén <b>huecas</b> —aire <b>o agua suelta</b>— hasta encontrar terreno firme, y <b>respeta el
     * bebedero</b>, que es agua a propósito.
     * <p>
     * Es lo que evita el cráter de una explosión: sin suelo firme la <b>puerta del corral se cae sola</b> (necesita
     * apoyo) y los animales se caen por el agujero. Y de paso retira el <b>agua que se cuela</b> desde el bebedero
     * (medido en el guardado del jugador: el agua había invadido cinco columnas del corral), así que el corral no
     * acaba encharcado. Es idempotente: donde el suelo ya está, no toca nada.
     */
    private static void sellarSueloDelCorral(ServerLevel level, BlockPos base, int nivel) {
        int tapados = 0;
        for (int dx = -ANEXO_RADIO; dx <= ANEXO_RADIO; dx++) {
            for (int dz = -ANEXO_RADIO; dz <= ANEXO_RADIO; dz++) {
                if (esBebedero(dx, dz)) {
                    continue; // el bebedero es agua a propósito: no se tapa
                }
                int x = base.getX() + dx;
                int z = base.getZ() + dz;
                for (int y = nivel - 1; y > nivel - PROFUNDIDAD_TAPADO && y > level.getMinBuildHeight(); y--) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState s = level.getBlockState(p);
                    if (!s.isAir() && !s.is(Blocks.WATER)) {
                        break; // ya se llegó a suelo firme
                    }
                    colocar(level, p, (y == nivel - 1 ? Blocks.GRASS_BLOCK : Blocks.DIRT).defaultBlockState(), 3);
                    tapados++;
                }
            }
        }
        if (tapados > 0) {
            DevilRpg.LOGGER.info("[Village] Corral anexo en {}: tapados {} bloques del suelo (aire o agua suelta)",
                    base, tapados);
        }
    }

    /** ¿Esa celda (relativa a la base del corral) es el <b>bebedero</b>? (agua a propósito: no se tapa) */
    private static boolean esBebedero(int dx, int dz) {
        return dz == 4 && dx >= -4 && dx <= -2;
    }

    // --- LA PESQUERA (etapa G: el pescador, su edificio y su lago) -----------------------------------

    /**
     * Dónde está la <b>pesquera</b>, relativa al centro: en el campo del <b>sureste</b>, que es el cuadrante que
     * queda libre (la taberna está al este-norte, el almacén y el corral al este, los bancales al oeste y al
     * suroeste). Es lo que pidió el jugador: <i>"el pescador tendrá su edificio y su lago más adelante"</i>.
     */
    private static final int[] PESQUERA = {20, 44};
    /** Radio del <b>lago</b> (huella de 7x7) y peces que caben dentro. */
    public static final int LAGO_RADIO = 3;
    public static final int LAGO_PECES_MAX = 6;
    /** Peces que se sueltan al construir la pesquera: el lago arranca con bandada y se repuebla solo, despacio. */
    private static final int LAGO_PECES_INICIAL = 4;
    /** Ancho (X) y fondo (Z) de la <b>caseta</b> del pescador. */
    private static final int PESQUERA_ANCHO = 5;
    private static final int PESQUERA_FONDO = 5;

    /** La esquina de la caseta del pescador, relativa a la base de la pesquera (que es el CENTRO del lago). */
    private static BlockPos casetaDeLaPesquera(BlockPos base, int nivel) {
        return new BlockPos(base.getX() - PESQUERA_ANCHO / 2, nivel, base.getZ() - 10);
    }

    /** La base de la pesquera (el centro del lago), relativa al centro de la aldea. */
    public static BlockPos baseDeLaPesquera(BlockPos center) {
        return center.offset(PESQUERA[0], 0, PESQUERA[1]);
    }

    private static BlockPos puestoDelPescador(BlockPos base, int nivel) {
        return new BlockPos(base.getX() + 1, nivel, base.getZ() - 5);
    }

    /**
     * El <b>puesto de trabajo del pescador</b>: el <b>barril</b> de la pesquera, en la orilla junto a su puerta. En
     * vanilla el barril es el puesto del <b>pescador</b>, y hasta esta etapa el pueblo no usaba barriles a propósito
     * (las pipas de la taberna son de madera con corteza) para que nadie tomara ese oficio sin tener dónde pescar.
     */
    public static BlockPos puestoDelPescador(ServerLevel level, BlockPos center) {
        return puestoDelPescador(baseDeLaPesquera(center), cotaDeLaPlaza(level, center));
    }

    private static BlockPos trabajoDelPescador(BlockPos base, int nivel) {
        return new BlockPos(base.getX(), nivel, base.getZ() - 2);
    }

    /** Dónde se pone a pescar: el final de la <b>pasarela</b>, sobre el agua del lago. */
    public static BlockPos trabajoDelPescador(ServerLevel level, BlockPos center) {
        return trabajoDelPescador(baseDeLaPesquera(center), cotaDeLaPlaza(level, center));
    }

    /** La caja del lago, para buscar los peces que nadan dentro. La Y sale de la COTA, nunca de la del centro (I1). */
    private static AABB cajaDelLago(ServerLevel level, BlockPos center) {
        BlockPos base = baseDeLaPesquera(center);
        int nivel = cotaDeLaPlaza(level, center);
        return new AABB(base.getX() - LAGO_RADIO, nivel - 4.0D, base.getZ() - LAGO_RADIO,
                base.getX() + LAGO_RADIO + 1, nivel + 2.0D, base.getZ() + LAGO_RADIO + 1);
    }

    /** Cuántos peces nadan ahora mismo en el lago. */
    public static int pecesEnElLago(ServerLevel level, BlockPos center) {
        return level.getEntitiesOfClass(net.minecraft.world.entity.animal.AbstractFish.class,
                cajaDelLago(level, center)).size();
    }

    /** El pez <b>más cercano a la pasarela</b> (el que saca el pescador), o {@code null} si el lago está vacío. */
    @Nullable
    public static net.minecraft.world.entity.animal.AbstractFish pezMasCercano(ServerLevel level, BlockPos center) {
        BlockPos trabajo = trabajoDelPescador(level, center);
        net.minecraft.world.entity.animal.AbstractFish mejor = null;
        double mejorDist = Double.MAX_VALUE;
        for (net.minecraft.world.entity.animal.AbstractFish pez : level.getEntitiesOfClass(
                net.minecraft.world.entity.animal.AbstractFish.class, cajaDelLago(level, center))) {
            double d = pez.distanceToSqr(trabajo.getX() + 0.5D, trabajo.getY(), trabajo.getZ() + 0.5D);
            if (d < mejorDist) {
                mejorDist = d;
                mejor = pez;
            }
        }
        return mejor;
    }

    /**
     * ¿Está la pesquera hecha? Vale el <b>agua del lago</b> o el <b>barril</b> (el puesto): con cualquiera de los dos
     * se da por hecha, así que hace falta perder los dos para que el pueblo la reconstruya (reconstruirla volvería a
     * soltar peces y podría deshacer lo que el jugador haya puesto alrededor).
     */
    public static boolean pesqueraConstruida(ServerLevel level, BlockPos center) {
        int nivel = cotaDeLaPlaza(level, center);
        BlockPos base = baseDeLaPesquera(center);
        return level.getBlockState(new BlockPos(base.getX(), nivel - 1, base.getZ())).is(Blocks.WATER)
                || level.getBlockState(puestoDelPescador(level, center)).is(Blocks.BARREL);
    }

    /**
     * Asegura la <b>pesquera</b> (etapa G): el lago con su bandada, la caseta del pescador, su <b>barril</b> (el
     * puesto), la pasarela y los faroles. Idempotente (ver {@link #pesqueraConstruida}); se llama al generar, en la
     * migración y en el latido, como el resto de edificios del pueblo.
     */
    public static void asegurarPesquera(ServerLevel level, BlockPos center) {
        int nivel = cotaDeLaPlaza(level, center);
        if (nivel <= level.getMinBuildHeight() + 1) {
            return;
        }
        // Los faroles del lago, ANTES del early-return: en una pesquera ya construida hay que reparar los dos que
        // quedaron flotando (ver `farolSobreElPoste`). Es idempotente: si ya están bien, no toca nada.
        BlockPos base = baseDeLaPesquera(center);
        java.util.List<BlockPos> esquinas = new java.util.ArrayList<>();
        esquinas.add(new BlockPos(base.getX() - LAGO_RADIO - 1, nivel, base.getZ() - LAGO_RADIO - 1));
        esquinas.add(new BlockPos(base.getX() + LAGO_RADIO + 1, nivel, base.getZ() + LAGO_RADIO + 1));
        posarFarolesFlotantes(level, esquinas);
        for (BlockPos esquina : esquinas) {
            farolSobreElPoste(level, esquina);
        }
        if (pesqueraConstruida(level, center)) {
            return;
        }
        pesquera(level, base, nivel);
        DevilRpg.LOGGER.info("[Village] Aldea en {}: pesquera construida en {} (lago de {}x{}, {} pez(ces) y su"
                + " barril)", center, baseDeLaPesquera(center), 2 * LAGO_RADIO + 1, 2 * LAGO_RADIO + 1,
                LAGO_PECES_INICIAL);
    }

    /**
     * Construye la pesquera: el <b>lago</b> (7x7, dos capas de agua y fondo de arena, con su orilla seca), la
     * <b>pasarela</b> de tablones hasta el centro del lago (donde se pone el pescador), la <b>caseta</b> de 5x5 con su
     * puerta mirando al agua, su cama, su arca y su farol, el <b>barril</b> (el puesto de trabajo) y los peces.
     */
    private static void pesquera(ServerLevel level, BlockPos base, int nivel) {
        int bx = base.getX();
        int bz = base.getZ();
        BlockState tablon = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        // 1) EL LAGO: 7x7 de agua a dos capas (nivel-1 y nivel-2) con el fondo de arena, y la orilla (un anillo
        //    alrededor) seca y de arena, para que el agua no se salga ni haga cuadros con el césped.
        for (int dx = -LAGO_RADIO - 1; dx <= LAGO_RADIO + 1; dx++) {
            for (int dz = -LAGO_RADIO - 1; dz <= LAGO_RADIO + 1; dz++) {
                boolean dentroDelLago = Math.abs(dx) <= LAGO_RADIO && Math.abs(dz) <= LAGO_RADIO;
                BlockPos p = new BlockPos(bx + dx, nivel - 1, bz + dz);
                if (dentroDelLago) {
                    colocar(level, p, Blocks.WATER.defaultBlockState(), 3);
                    colocar(level, p.below(), Blocks.WATER.defaultBlockState(), 3);
                    colocar(level, new BlockPos(bx + dx, nivel - 3, bz + dz), Blocks.SAND.defaultBlockState(), 3);
                } else {
                    colocar(level, p, Blocks.SAND.defaultBlockState(), 3);
                }
                for (int y = nivel; y <= nivel + 4; y++) {
                    colocar(level, new BlockPos(bx + dx, y, bz + dz), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        // 2) LA PASARELA: tablones sobre el agua, del borde norte al centro del lago, con sus postes dentro del agua.
        for (int dz = -4; dz <= -1; dz++) {
            colocar(level, new BlockPos(bx, nivel, bz + dz), tablon, 3);
            if (dz != -4) {
                colocar(level, new BlockPos(bx, nivel - 1, bz + dz), Blocks.OAK_FENCE.defaultBlockState(), 3);
            }
        }
        // 3) LA CASETA del pescador: suelo de tablones, muros con la puerta en el centro del muro SUR (sale derecho a
        //    la pasarela), ventanas de cristal, tejado a dos aguas, su cama, su arca y su farol.
        BlockPos caseta = casetaDeLaPesquera(base, nivel);
        for (int dx = 0; dx < PESQUERA_ANCHO; dx++) {
            for (int dz = 0; dz < PESQUERA_FONDO; dz++) {
                colocar(level, new BlockPos(caseta.getX() + dx, nivel - 1, caseta.getZ() + dz), tablon, 3);
                for (int dy = 0; dy <= 4; dy++) {
                    colocar(level, new BlockPos(caseta.getX() + dx, nivel + dy, caseta.getZ() + dz),
                            Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        for (int dx = 0; dx < PESQUERA_ANCHO; dx++) {
            for (int dz = 0; dz < PESQUERA_FONDO; dz++) {
                boolean borde = dx == 0 || dx == PESQUERA_ANCHO - 1 || dz == 0 || dz == PESQUERA_FONDO - 1;
                if (!borde) {
                    continue;
                }
                boolean puerta = dz == PESQUERA_FONDO - 1 && dx == PESQUERA_ANCHO / 2;
                for (int dy = 0; dy <= 2; dy++) {
                    BlockPos p = new BlockPos(caseta.getX() + dx, nivel + dy, caseta.getZ() + dz);
                    if (puerta && dy <= 1) {
                        colocar(level, p, Blocks.DARK_OAK_DOOR.defaultBlockState()
                                .setValue(DoorBlock.FACING, Direction.SOUTH)
                                .setValue(DoorBlock.HALF,
                                        dy == 0 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER), 3);
                    } else if (dy == 0) {
                        colocar(level, p, Blocks.STONE_BRICKS.defaultBlockState(), 3);
                    } else if (dy == 1 && dz == PESQUERA_FONDO - 1 && dx % 2 == 1) {
                        colocar(level, p, Blocks.GLASS_PANE.defaultBlockState(), 3);
                    } else {
                        colocar(level, p, tablon, 3);
                    }
                }
            }
        }
        for (int dx = 0; dx < PESQUERA_ANCHO; dx++) {
            for (int dz = 0; dz < PESQUERA_FONDO; dz++) {
                colocar(level, new BlockPos(caseta.getX() + dx, nivel + 3, caseta.getZ() + dz), tablon, 3);
                if (dz == PESQUERA_FONDO / 2) {
                    colocar(level, new BlockPos(caseta.getX() + dx, nivel + 4, caseta.getZ() + dz), tablon, 3);
                }
            }
        }
        colocar(level, new BlockPos(caseta.getX() + 1, nivel + 2, caseta.getZ() + 2),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 3);
        bed(level, new BlockPos(caseta.getX() + 1, nivel, caseta.getZ() + 1), Direction.SOUTH);
        colocar(level, new BlockPos(caseta.getX() + 3, nivel, caseta.getZ() + 1),
                Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH), 3);
        // 4) EL PUESTO DEL PESCADOR: el BARRIL, en la orilla junto a la puerta. Es lo que le da el oficio.
        colocar(level, puestoDelPescador(base, nivel), Blocks.BARREL.defaultBlockState(), 3);
        // 5) FAROLES en dos esquinas del lago: la pesquera se ve de noche y el agua no cría bichos. La casilla que se
        //    le pasa es la del POSTE (el suelo de la orilla está a `nivel - 1`), así que el farol queda justo encima.
        //    Antes se le pasaba `nivel + 2` y estos dos faroles salían FLOTANDO a tres bloques del suelo.
        farolSobreElPoste(level, new BlockPos(bx - LAGO_RADIO - 1, nivel, bz - LAGO_RADIO - 1));
        farolSobreElPoste(level, new BlockPos(bx + LAGO_RADIO + 1, nivel, bz + LAGO_RADIO + 1));
        // 6) LOS PECES: el lago arranca con su bandada (y se repuebla solo: ver `reponerPecesDelLago`).
        sueltaPeces(level, base, nivel, LAGO_PECES_INICIAL);
    }

    /**
     * Suelta {@code cuantos} peces en el lago (dos de cada tres son <b>cods</b> y el tercero un <b>salmón</b>), en
     * celdas de agua con agua encima, que es donde el pez nada. Son <b>peces de verdad</b>: el pescador saca uno y el
     * lago se queda con uno menos, así que la comida del pueblo sale del lago, no de un contador.
     */
    private static int sueltaPeces(ServerLevel level, BlockPos base, int nivel, int cuantos) {
        int puestos = 0;
        for (int i = 0; i < cuantos; i++) {
            int dx = level.random.nextInt(2 * LAGO_RADIO - 1) - (LAGO_RADIO - 1);
            int dz = level.random.nextInt(2 * LAGO_RADIO - 1) - (LAGO_RADIO - 1);
            BlockPos agua = new BlockPos(base.getX() + dx, nivel - 2, base.getZ() + dz);
            if (!level.getBlockState(agua).is(Blocks.WATER) || !level.getBlockState(agua.above()).is(Blocks.WATER)) {
                continue;
            }
            net.minecraft.world.entity.Entity creado = (i % 3 == 2 ? EntityType.SALMON : EntityType.COD).create(level);
            if (!(creado instanceof net.minecraft.world.entity.Mob pez)) {
                continue;
            }
            pez.moveTo(agua.getX() + 0.5D, agua.getY(), agua.getZ() + 0.5D,
                    level.random.nextFloat() * 360.0F, 0.0F);
            pez.setPersistenceRequired();
            level.addFreshEntity(pez);
            puestos++;
        }
        return puestos;
    }

    /**
     * <b>Repuebla el lago</b>: si le quedan menos de {@link #LAGO_PECES_MAX} peces, suelta uno. Se llama desde el
     * latido (cada dos minutos), así que el lago se recupera <b>despacio</b>: lo que el pueblo pesca está limitado por
     * lo que cría su lago, no por un contador de comida.
     */
    public static int reponerPecesDelLago(ServerLevel level, BlockPos center) {
        int nivel = cotaDeLaPlaza(level, center);
        if (nivel <= level.getMinBuildHeight() + 1 || !pesqueraConstruida(level, center)) {
            return 0;
        }
        if (pecesEnElLago(level, center) >= LAGO_PECES_MAX) {
            return 0;
        }
        return sueltaPeces(level, baseDeLaPesquera(center), nivel, 1);
    }

    /** El <b>camino</b> del pueblo a la pesquera: de la plaza al lado oeste de la caseta (no cruza ningún bancal). */
    public static void caminoALaPesquera(ServerLevel level, BlockPos center) {
        BlockPos base = baseDeLaPesquera(center);
        line(level, center, new BlockPos(base.getX() - 4, cotaDeLaPlaza(level, center), base.getZ() - 8));
    }

    // --- LA ARBOLEDA DEL PUEBLO (la madera de una aldea sin bosque) ---------------------------------

    /**
     * Caja de la <b>arboleda del pueblo</b> (relativa al centro): desde la etapa F es un <b>bosquecillo</b> de
     * <b>22×18</b> en la esquina <b>noroeste</b> (x -46..-25, z -32..-15), con <b>doce plazas</b> de árbol, que es lo
     * que pidió el jugador: <i>"necesitamos que se mueva el lugar donde están los árboles a un área más grande, ya que
     * tenemos más espacio, y podría ser un pequeño bosque donde el leñador pueda cortar y replantar"</i>.
     * <p>
     * Está libre de todo lo demás (los caminos radiales van por los ejes y hacia el norte, y los solares empiezan más
     * adentro) y a 56 del centro como mucho, así que cabe entero dentro de la muralla (radio 62).
     */
    private static final int ARBOLEDA_X0 = -46;
    private static final int ARBOLEDA_X1 = -25;
    private static final int ARBOLEDA_Z0 = -32;
    private static final int ARBOLEDA_Z1 = -15;

    /**
     * ¿Ese punto (X/Z) cae dentro de la <b>arboleda del pueblo</b>? Es la <b>única excepción</b> a la regla de "dentro
     * de la valla no se tala" (el muro y las casas son de troncos): la arboleda es <b>de la aldea</b> y ahí el leñador
     * tala y replanta a propósito.
     */
    public static boolean enLaArboleda(BlockPos center, BlockPos p) {
        int dx = p.getX() - center.getX();
        int dz = p.getZ() - center.getZ();
        return dx >= ARBOLEDA_X0 && dx <= ARBOLEDA_X1 && dz >= ARBOLEDA_Z0 && dz <= ARBOLEDA_Z1;
    }

    /**
     * Las <b>doce plazas</b> del bosquecillo (rejilla de 4×3, repartidas por la caja) a la cota del pueblo. Son doce
     * porque el área da de sobra: cuanto más grande es la arboleda, más madera sostiene sin quedarse pelada, y el
     * leñador tiene donde talar y replantar sin quedarse sin árboles.
     */
    public static BlockPos[] plantonesDeLaArboleda(BlockPos center, int nivel) {
        int[] xs = repartir(ARBOLEDA_X0 + 2, ARBOLEDA_X1 - 2, 4);
        int[] zs = repartir(ARBOLEDA_Z0 + 2, ARBOLEDA_Z1 - 2, 3);
        BlockPos[] plazas = new BlockPos[xs.length * zs.length];
        int i = 0;
        for (int z : zs) {
            for (int x : xs) {
                plazas[i++] = new BlockPos(center.getX() + x, nivel, center.getZ() + z);
            }
        }
        return plazas;
    }

    /** {@code cuantos} valores repartidos (redondeados) entre {@code desde} y {@code hasta}, ambos incluidos. */
    private static int[] repartir(int desde, int hasta, int cuantos) {
        int[] valores = new int[cuantos];
        for (int i = 0; i < cuantos; i++) {
            valores[i] = desde + (int) Math.round((hasta - desde) * (i / (double) (cuantos - 1)));
        }
        return valores;
    }

    /**
     * El <b>centro de la arboleda</b> (el punto por el que pasa la ronda de la guardia): una casilla libre de césped,
     * a dos bloques de cada plantón, así que nadie se queda plantado donde va a crecer un tronco.
     */
    public static BlockPos puntoDeApoyoDeLaArboleda(BlockPos center, int nivel) {
        return new BlockPos(center.getX() + (ARBOLEDA_X0 + ARBOLEDA_X1) / 2, nivel,
                center.getZ() + (ARBOLEDA_Z0 + ARBOLEDA_Z1) / 2);
    }

    /**
     * <b>La arboleda del pueblo.</b> Es la respuesta a la aldea que nace donde <b>no hay bosque</b> (una islita, un
     * desierto, una llanura pelada): sin árboles no hay troncos, y sin troncos se caen los tablones, los palos, los
     * arcos, las flechas y los escudos, así que el pueblo dejaría de ser autosuficiente. Los <b>fundadores traen los
     * plantones</b> —igual que traen las semillas de la remesa inicial de la despensa— y el <b>leñador</b> los tala y
     * los replanta como cualquier árbol: madera de verdad, de árboles que crecen de verdad, sin contadores ni magia.
     * OJO: los plantones se ponen con {@code level.setBlock} <b>DIRECTO</b>, no con {@link #colocar}: así <b>no entran
     * en el plano</b>. Si entraran, el obrero vería "aquí debería haber un plantón" donde ya hay un <b>árbol</b> y lo
     * "repararía" devolviéndolo a plantón en cada latido (la arboleda nunca crecería).
     */
    public static void asegurarArboleda(ServerLevel level, BlockPos center) {
        int nivel = cotaDeLaPlaza(level, center);
        if (nivel <= level.getMinBuildHeight() + 1) {
            return;
        }
        // El SUELO: césped a la cota (en una islita ya está; en arena, piedra o nieve se trae tierra). Solo se toca el
        // terreno natural (o el hueco): lo que haya puesto el jugador no se toca.
        for (int dx = ARBOLEDA_X0; dx <= ARBOLEDA_X1; dx++) {
            for (int dz = ARBOLEDA_Z0; dz <= ARBOLEDA_Z1; dz++) {
                BlockPos suelo = new BlockPos(center.getX() + dx, nivel - 1, center.getZ() + dz);
                BlockState actual = level.getBlockState(suelo);
                if (actual.isAir() || actual.is(Blocks.WATER) || esTerrenoRecortable(actual)) {
                    colocar(level, suelo, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                }
            }
        }
        Block planton = plantonDelBioma(level, center);
        int puestos = 0;
        for (BlockPos p : plantonesDeLaArboleda(center, nivel)) {
            if (!level.getBlockState(p).isAir()) {
                continue; // ya hay un plantón, un árbol (o algo del jugador): no se toca
            }
            if (!level.getBlockState(p.below()).is(BlockTags.DIRT)) {
                continue; // sin tierra debajo no crece
            }
            level.setBlock(p, planton.defaultBlockState(), Block.UPDATE_ALL); // DIRECTO: fuera del plano (ver arriba)
            puestos++;
        }
        if (puestos > 0) {
            DevilRpg.LOGGER.info("[Village] Aldea en {}: arboleda del pueblo plantada ({} plantones de {})",
                    center, puestos, planton.getName().getString());
        }
    }

    /**
     * El <b>árbol de la tierra</b> para la arboleda (el que plantarían los fundadores): el del bioma donde está el
     * pueblo, y <b>roble</b> si no hay uno claro (una islita, una playa, mar abierto). Así la arboleda no desentona.
     */
    private static Block plantonDelBioma(ServerLevel level, BlockPos center) {
        var bioma = level.getBiome(center);
        if (bioma.is(BiomeTags.IS_TAIGA) || bioma.value().getBaseTemperature() < 0.15F) {
            return Blocks.SPRUCE_SAPLING; // taiga o cualquier tierra fría (allí el árbol de siempre es la picea)
        }
        if (bioma.is(BiomeTags.IS_JUNGLE)) {
            return Blocks.JUNGLE_SAPLING;
        }
        if (bioma.is(BiomeTags.IS_SAVANNA) || bioma.is(BiomeTags.IS_BADLANDS)) {
            return Blocks.ACACIA_SAPLING;
        }
        if (bioma.is(Biomes.DARK_FOREST)) {
            return Blocks.DARK_OAK_SAPLING;
        }
        if (bioma.is(Biomes.CHERRY_GROVE)) {
            return Blocks.CHERRY_SAPLING;
        }
        return Blocks.OAK_SAPLING; // el de siempre (y el que traen los fundadores cuando no hay árbol claro)
    }

    // --- LA ORILLA DE LA ALDEA DE MAR (la islita) ---------------------------------------------------

    /** Ancho (bloques) del anillo de <b>orilla seca</b> que se saca alrededor de una aldea que está al nivel del agua. */
    private static final int ORILLA_ANCHO = 4;

    /**
     * <b>Alisa la orilla</b> de una aldea que está <b>al nivel del agua</b> (la islita).
     * <p>
     * El terreno llano del pueblo queda <b>a la misma altura que el mar</b>, así que el primer escalón del talud
     * asoma a la cota en unas casillas y en otras queda un bloque por debajo: el resultado es una orilla <b>a
     * cuadros</b> (medido en el guardado del jugador: agua a y=62 pegada a césped a y=62, en parches cuadrados).
     * Aquí se rellena el anillo de orilla con <b>césped a la cota</b> —solo donde hay <b>agua o aire</b>, nunca encima
     * de nada construido (el camino del corral anexo se respeta)— para que la isla tenga una <b>playa seca y pareja</b>
     * y el agua empiece en un borde limpio.
     * <p>
     * En una aldea de <b>tierra adentro</b> no se toca nada: ahí el talud es lo que la hace parecer una meseta
     * natural. Se decide mirando si hay <b>agua a la capa que se pisa</b> en el anillo (barato y sin guardar nada).
     */
    public static void asegurarOrilla(ServerLevel level, BlockPos center) {
        int nivel = cotaDeLaPlaza(level, center);
        if (nivel <= level.getMinBuildHeight() + 1) {
            return;
        }
        int interior = LEVEL_RADIUS;
        int exterior = interior + ORILLA_ANCHO;
        if (!hayAguaEnElAnillo(level, center, nivel, interior, exterior)) {
            return; // aldea de tierra adentro: su talud está bien como está
        }
        // La orilla, seca y pareja: césped a la cota en todo el anillo.
        int puestos = 0;
        for (int x = -exterior; x <= exterior; x++) {
            for (int z = -exterior; z <= exterior; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist <= interior || dist > exterior) {
                    continue;
                }
                int px = center.getX() + x;
                int pz = center.getZ() + z;
                for (int y = nivel - 1; y > nivel - 1 - PROFUNDIDAD_TAPADO && y > level.getMinBuildHeight(); y--) {
                    BlockPos p = new BlockPos(px, y, pz);
                    BlockState s = level.getBlockState(p);
                    if (!s.isAir() && !s.is(Blocks.WATER)) {
                        break; // suelo firme (o algo construido: hasta aquí)
                    }
                    colocar(level, p, (y == nivel - 1 ? Blocks.GRASS_BLOCK : Blocks.DIRT).defaultBlockState(), 3);
                    puestos++;
                }
            }
        }
        if (puestos > 0) {
            DevilRpg.LOGGER.info("[Village] Aldea en {}: orilla seca y pareja ({} bloques de cesped en el anillo)",
                    center, puestos);
        }
    }

    /** ¿Hay agua a la <b>capa que se pisa</b> en el anillo del talud? (es lo que distingue una aldea de orilla) */
    private static boolean hayAguaEnElAnillo(ServerLevel level, BlockPos center, int nivel, int interior, int exterior) {
        for (int x = -exterior; x <= exterior; x += 2) {
            for (int z = -exterior; z <= exterior; z += 2) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist <= interior || dist > exterior) {
                    continue;
                }
                if (level.getBlockState(new BlockPos(center.getX() + x, nivel - 1, center.getZ() + z))
                        .is(Blocks.WATER)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Asegura el <b>gallinero</b> (el corralillo de los pollos: valla con <b>tejado</b> y su portón) en las aldeas
     * que ya tenían el corral sin él. Idempotente: se comprueba por una <b>valla testigo</b> (la esquina sureste,
     * segunda hilada), así que si el jugador se lleva una valla suelta no se reconstruye el gallinero encima.
     */
    public static void asegurarGallinero(ServerLevel level, BlockPos center) {
        int nivel = cotaDeLaPlaza(level, center);
        if (nivel <= level.getMinBuildHeight() + 1 || !anexoConstruido(level, center)) {
            return;
        }
        BlockPos base = baseDeAnexo(center);
        BlockPos testigo = new BlockPos(base.getX() + GALLINERO_X1 + 1, nivel + 1, base.getZ() + GALLINERO_Z1 + 1);
        if (level.getBlockState(testigo).is(Blocks.OAK_FENCE)) {
            return; // el gallinero ya está
        }
        gallinero(level, base, nivel);
        DevilRpg.LOGGER.info("[Village] Aldea en {}: gallinero del corral anexo construido en {} (pollos encerrados)",
                center, centroDelGallinero(center, nivel));
    }

    /**
     * Asegura la <b>granja anexa de animales</b> (corral de 19x19 con cobertizo, bebedero y camino desde la puerta
     * este) en una aldea que todavía no la tiene. Es <b>idempotente</b> y, como el kiosco o la barraca, comprueba el
     * suelo a la cota: si ya está, no toca nada (reconstruirla borraría lo que el jugador tenga dentro).
     */
    public static void asegurarGranjaAnexa(ServerLevel level, BlockPos center) {
        int nivel = cotaDeLaPlaza(level, center);
        if (nivel <= level.getMinBuildHeight() + 1) {
            return;
        }
        if (anexoConstruido(level, center)) {
            return; // el corral ya está: no se vuelve a construir
        }
        granjaAnexa(level, baseDeAnexo(center), nivel);
        DevilRpg.LOGGER.info("[Village] Aldea en {}: granja anexa de animales construida en {} (corral de {}x{})",
                center, baseDeAnexo(center), 2 * ANEXO_RADIO + 1, 2 * ANEXO_RADIO + 1);
    }

    /** ¿Ese bloque lo puso el <b>corral anexo</b>? Solo esos se retiran al rehacerlo: lo del jugador no se toca. */
    private static boolean esDelCorral(BlockState s) {
        return s.is(Blocks.OAK_FENCE) || s.is(Blocks.OAK_FENCE_GATE) || s.is(Blocks.OAK_LOG)
                || s.is(Blocks.OAK_PLANKS) || s.is(Blocks.STONE_BRICKS) || s.is(Blocks.HAY_BLOCK)
                || s.is(Blocks.LOOM) || s.is(Blocks.RED_BED) || s.is(Blocks.LANTERN)
                || s.is(Blocks.WATER) || s.is(Blocks.DIRT_PATH);
    }

    /**
     * <b>Ensancha el corral anexo</b> al tamaño nuevo (<b>19x19</b> en vez de 15x15) en una aldea que ya lo tenía:
     * retira lo del corral <b>viejo</b> (su valla, su portón, el cobertizo, el gallinero, la paja, el bebedero y los
     * faroles: solo esos bloques) y lo vuelve a levantar entero con {@link #granjaAnexa}.
     * <p>
     * Lo pidió el jugador: <i>"¿por qué hiciste la granja más pequeña? ... reubícala pero no la hagas más
     * pequeña"</i>. Medido en su guardado antes de tocar nada: el corral estaba en <b>15x15</b> (radio 7), sin
     * cambios —lo que él veía fuera eran animales <b>salvajes</b> del mundo, ninguno con la marca del rebaño del
     * pueblo—, así que aquí no se encoge nada: se ensancha a 19x19, que es el doble de superficie para el rebaño.
     */
    public static void ensancharElCorral(ServerLevel level, BlockPos center) {
        int nivel = cotaDeLaPlaza(level, center);
        if (nivel <= level.getMinBuildHeight() + 1) {
            return;
        }
        BlockPos base = baseDeAnexo(center);
        if (level.getBlockState(new BlockPos(base.getX() - ANEXO_RADIO, nivel, base.getZ() - ANEXO_RADIO))
                .is(Blocks.OAK_FENCE)) {
            return; // ya es el corral grande
        }
        // OJO: aquí NO se puede preguntar `anexoConstruido` (mira la valla del corral NUEVO, así que diría que no hay
        // corral). Se busca la valla del corral VIEJO (radio 7): si no está, es que no hay corral que ensanchar y lo
        // construye `asegurarGranjaAnexa`, que va después en la migración.
        boolean corralViejo = level.getBlockState(new BlockPos(base.getX() - ANEXO_RADIO_VIEJO, nivel,
                        base.getZ() - ANEXO_RADIO_VIEJO)).is(Blocks.OAK_FENCE)
                || level.getBlockState(new BlockPos(base.getX() + ANEXO_RADIO_VIEJO, nivel,
                        base.getZ() - ANEXO_RADIO_VIEJO)).is(Blocks.OAK_FENCE);
        if (!corralViejo) {
            return;
        }
        int quitados = 0;
        for (int dx = -ANEXO_RADIO_VIEJO - 1; dx <= ANEXO_RADIO_VIEJO + 1; dx++) {
            for (int dz = -ANEXO_RADIO_VIEJO - 1; dz <= ANEXO_RADIO_VIEJO + 1; dz++) {
                for (int y = nivel - 1; y <= nivel + 4; y++) {
                    BlockPos p = new BlockPos(base.getX() + dx, y, base.getZ() + dz);
                    if (esDelCorral(level.getBlockState(p))) {
                        colocar(level, p, Blocks.AIR.defaultBlockState(), 3);
                        quitados++;
                    }
                }
            }
        }
        granjaAnexa(level, base, nivel);
        // LAS GALLINAS, AL GALLINERO NUEVO: el corralillo se movió con el ensanche, así que las gallinas que andaban
        // por el corral se quedarían fuera (y sus huevos por el suelo, que es justo lo que el jugador no quiere). Se
        // meten dentro, que es donde viven.
        BlockPos dentro = centroDelGallinero(center, nivel);
        int metidas = 0;
        for (net.minecraft.world.entity.animal.Animal animal : level.getEntitiesOfClass(
                net.minecraft.world.entity.animal.Animal.class,
                new AABB(base).inflate(ANEXO_RADIO + 2, 12.0D, ANEXO_RADIO + 2))) {
            if (animal.getType() != EntityType.CHICKEN) {
                continue;
            }
            animal.moveTo(dentro.getX() + 0.5D + (metidas % 3), dentro.getY(),
                    dentro.getZ() + 0.5D + ((metidas / 3) % 2), animal.getYRot(), 0.0F);
            metidas++;
        }
        DevilRpg.LOGGER.info("[Village] Aldea en {}: corral anexo ensanchado a {}x{} ({} bloque(s) del viejo"
                + " retirados, {} gallina(s) al gallinero nuevo)", center, 2 * ANEXO_RADIO + 1, 2 * ANEXO_RADIO + 1,
                quitados, metidas);
    }

    /**
     * ¿Está ya el corral anexo? Se miran <b>dos</b> marcas (la valla de la esquina y el suelo de piedra del
     * cobertizo): así, si al jugador se le cae una valla suelta, la aldea <b>no</b> vuelve a levantar todo el corral
     * encima de lo que tenga dentro.
     * <p>
     * Lo usa también la <b>guardia</b>: sin corral, su ronda no baja al anexo (patrullaría un descampado).
     */
    public static boolean anexoConstruido(ServerLevel level, BlockPos center) {
        int nivel = cotaDeLaPlaza(level, center);
        BlockPos base = baseDeAnexo(center);
        boolean vallaEsquina = level.getBlockState(new BlockPos(base.getX() - ANEXO_RADIO, nivel,
                base.getZ() - ANEXO_RADIO)).is(Blocks.OAK_FENCE);
        boolean sueloCobertizo = level.getBlockState(new BlockPos(base.getX() + ANEXO_RADIO - 2, nivel - 1,
                base.getZ())).is(Blocks.STONE_BRICKS);
        return vallaEsquina && sueloCobertizo;
    }

    /**
     * Construye el <b>corral de la granja</b>: huella <b>nivelada a la cota del pueblo</b> (como las casas y la
     * barraca: sin zanjas ni escalones), camino desde la plaza hasta su portón, valla de roble con <b>portón de
     * valla</b> (que el pueblo abre con {@code VillagerGateGoal}: el juego no deja que un aldeano abra una puerta de
     * valla), <b>gallinero</b> con tejado para los pollos, cobertizo con cama y telar del ganadero, bebedero de agua
     * y heno.
     * <p>
     * Desde que la muralla creció al <b>radio 62</b> la granja está <b>dentro</b> del pueblo (a 50 del centro, de 43
     * a 57), que es lo que pidió el jugador: así los monstruos no aparecen dentro del corral de noche ni se comen al
     * rebaño, y la guardia la defiende en su ronda.
     * <p>
     * Todo pasa por {@link #colocar}, así que <b>entra en el plano</b> (invariante I8) y el obrero lo repone.
     */
    private static void granjaAnexa(ServerLevel level, BlockPos base, int nivel) {
        int r = ANEXO_RADIO;
        int bx = base.getX();
        int bz = base.getZ();
        int cx = bx - ANEXO_DX; // X del centro de la aldea
        // 1) HUELLA: el corral y el camino que llega a su portón, a la cota del pueblo. `nivelarHuella` toma la
        //    ESQUINA (y solo mira su X/Z, pero se le da una Y que ya es la cota: invariante I1).
        nivelarHuella(level, new BlockPos(bx - r, nivel, bz - r), 2 * r + 1, 2 * r + 1, nivel);
        // EL CAMINO: desde el borde de la plaza hasta el portón del corral. OJO con la dirección: el portón está en
        // el lado OESTE y mira al pueblo, así que el camino viene DEL PUEBLO. Cuando el corral estaba fuera de la
        // muralla el camino bajaba del muro hacia fuera (de menor a mayor X, justo al revés); con la granja dentro
        // (muro al radio 62) el tramo va del centro (X menor) al portón (X mayor) y hay que recorrerlo en orden.
        int caminoDesde = Math.min(cx + 6, bx - r - 1);
        int caminoHasta = Math.max(cx + 6, bx - r - 1);
        nivelarHuella(level, new BlockPos(caminoDesde, nivel, bz - ANEXO_CAMINO_ANCHO / 2),
                caminoHasta - caminoDesde + 1, ANEXO_CAMINO_ANCHO, nivel);
        // Y una red de seguridad bajo el corral: si justo debajo pasa una barranca (o el mar, que aquí está al lado),
        // el nivelado deja el suelo HUECO y la puerta del corral —que necesita apoyo— se cae sola. Con el suelo
        // tapado, la valla, el portón y el bebedero se apoyan en algo. (Esto mismo lo repite `asegurarCercaDelAnexo`
        // en cada latido, así que una explosión tampoco deja el corral agujereado.)
        sellarSueloDelCorral(level, base, nivel);
        // El camino, marcado en el suelo (la capa que se pisa es `nivel`, el suelo sólido `nivel-1`).
        for (int x = caminoDesde; x <= caminoHasta; x++) {
            for (int dz = -1; dz <= 1; dz++) {
                colocar(level, new BlockPos(x, nivel - 1, bz + dz), Blocks.DIRT_PATH.defaultBlockState(), 3);
            }
        }
        // 2) LA VALLA: anillo de valla de roble (los animales no saltan 1,5 bloques) con el PORTÓN en el lado OESTE,
        //    mirando al camino de la aldea. Es una puerta de VALLA (no una de madera): encaja con la valla, que era
        //    lo que no cuadraba. OJO: el juego NO deja que un aldeano abra una puerta de valla, así que el pueblo se
        //    la abre y se la cierra con su propio goal (`VillagerGateGoal`): el ganadero entra y sale y el ganado no.
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                boolean borde = Math.abs(dx) == r || Math.abs(dz) == r;
                if (!borde || (dx == -r && dz == 0)) {
                    continue; // interior, o el hueco del portón
                }
                colocar(level, new BlockPos(bx + dx, nivel, bz + dz), Blocks.OAK_FENCE.defaultBlockState(), 3);
            }
        }
        colocar(level, new BlockPos(bx - r, nivel, bz), Blocks.OAK_FENCE_GATE.defaultBlockState()
                .setValue(FenceGateBlock.FACING, Direction.WEST)
                .setValue(FenceGateBlock.OPEN, false)
                .setValue(FenceGateBlock.IN_WALL, false), 3);
        // 3) COBERTIZO (al este, pegado a la valla): suelo de piedra, cuatro postes, tejado de tablones y SIN
        //    paredes, para que el ganadero y el ganado pasen por debajo. Dentro: su CAMA, su TELAR (puesto de trabajo
        //    de pastor), paja y un farol.
        int sx1 = bx + r - 5;
        int sx2 = bx + r - 1;
        for (int x = sx1; x <= sx2; x++) {
            for (int dz = -2; dz <= 2; dz++) {
                colocar(level, new BlockPos(x, nivel - 1, bz + dz), Blocks.STONE_BRICKS.defaultBlockState(), 3);
                for (int dy = 0; dy <= 3; dy++) {
                    colocar(level, new BlockPos(x, nivel + dy, bz + dz), Blocks.AIR.defaultBlockState(), 3);
                }
                colocar(level, new BlockPos(x, nivel + 3, bz + dz), Blocks.OAK_PLANKS.defaultBlockState(), 3);
            }
        }
        for (int[] esquina : new int[][]{{sx1, -2}, {sx1, 2}, {sx2, -2}, {sx2, 2}}) {
            for (int dy = 0; dy <= 2; dy++) {
                colocar(level, new BlockPos(esquina[0], nivel + dy, bz + esquina[1]),
                        Blocks.OAK_LOG.defaultBlockState(), 3);
            }
        }
        colocar(level, new BlockPos((sx1 + sx2) / 2, nivel + 2, bz),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 3);
        bed(level, new BlockPos(sx1 + 1, nivel, bz - 1), Direction.SOUTH);
        // El TELAR es el puesto de trabajo del pastor (vanilla): sin él, el juego le borra el oficio al aldeano.
        // Va en el sitio exacto (el suelo del cobertizo ya está puesto, así que no hace falta buscar hueco).
        colocar(level, new BlockPos(sx2 - 1, nivel, bz + 1), Blocks.LOOM.defaultBlockState(), 3);
        colocar(level, new BlockPos(sx2 - 1, nivel, bz - 1), Blocks.HAY_BLOCK.defaultBlockState(), 3);
        colocar(level, new BlockPos(sx1, nivel, bz + 1), Blocks.HAY_BLOCK.defaultBlockState(), 3);
        // 4) BEBEDERO: una alberca de 3x1 a ras del suelo del corral (la capa que se pisa sigue libre).
        for (int dx = -4; dx <= -2; dx++) {
            colocar(level, new BlockPos(bx + dx, nivel - 1, bz + 4), Blocks.WATER.defaultBlockState(), 3);
        }
        // 5) Dos islas de paja más y un par de vallas sueltas donde rascarse: da vida al corral y no estorba.
        //    (La de (bx-5, bz-2) se movió al sur: su sitio viejo es ahora la pared del gallinero.)
        colocar(level, new BlockPos(bx - 5, nivel, bz + 2), Blocks.HAY_BLOCK.defaultBlockState(), 3);
        colocar(level, new BlockPos(bx + 2, nivel, bz - 5), Blocks.HAY_BLOCK.defaultBlockState(), 3);
        // 6) EL GALLINERO: los pollos, encerrados y con su tejado (y su portón, que el pueblo abre).
        gallinero(level, base, nivel);
    }

    /**
     * Construye el <b>gallinero</b>: un corralillo de valla <b>con tejado</b> dentro del corral anexo. Una valla sola
     * no encierra a una gallina (aletea y salta), así que el techo es lo que de verdad las deja <b>encerradas</b>
     * —que es lo que pidió el jugador— y además los <b>huevos</b> caen dentro del corralillo, a mano del ganadero.
     * Comparte la valla del corral por el oeste y el norte, y su <b>portón</b> (pared sur, mirando al corral) lo abre
     * el pueblo con {@code VillagerGateGoal}: los pollos no lo abren nunca.
     */
    private static void gallinero(ServerLevel level, BlockPos base, int nivel) {
        int x0 = base.getX() + GALLINERO_X0;
        int x1 = base.getX() + GALLINERO_X1;
        int z0 = base.getZ() + GALLINERO_Z0;
        int z1 = base.getZ() + GALLINERO_Z1;
        // La SEGUNDA hilada de valla sobre la del corral (oeste y norte): es la que sujeta el tejado.
        for (int z = z0 - 1; z <= z1 + 1; z++) {
            colocar(level, new BlockPos(x0 - 1, nivel + 1, z), Blocks.OAK_FENCE.defaultBlockState(), 3);
        }
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            colocar(level, new BlockPos(x, nivel + 1, z0 - 1), Blocks.OAK_FENCE.defaultBlockState(), 3);
        }
        // Pared SUR (con el hueco del portón, dos bloques de alto) y pared ESTE.
        int puertaX = base.getX() + GALLINERO_PUERTA_X;
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            for (int dy = 0; dy <= 1; dy++) {
                if (x == puertaX && dy == 0) {
                    continue; // el hueco del portón (a ras de suelo; encima va la valla)
                }
                colocar(level, new BlockPos(x, nivel + dy, z1 + 1), Blocks.OAK_FENCE.defaultBlockState(), 3);
            }
        }
        colocar(level, new BlockPos(puertaX, nivel, z1 + 1), Blocks.OAK_FENCE_GATE.defaultBlockState()
                .setValue(FenceGateBlock.FACING, Direction.SOUTH)
                .setValue(FenceGateBlock.OPEN, false)
                .setValue(FenceGateBlock.IN_WALL, false), 3);
        for (int z = z0 - 1; z <= z1 + 1; z++) {
            for (int dy = 0; dy <= 1; dy++) {
                colocar(level, new BlockPos(x1 + 1, nivel + dy, z), Blocks.OAK_FENCE.defaultBlockState(), 3);
            }
        }
        // El TEJADO, un bloque por encima de las paredes y con un ala de sobra: es lo que de verdad las encierra.
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            for (int z = z0 - 1; z <= z1 + 2; z++) {
                colocar(level, new BlockPos(x, nivel + 2, z), Blocks.OAK_PLANKS.defaultBlockState(), 3);
            }
        }
        // Dentro: paja para anidar (en la columna del ESTE, para no tapar la esquina donde se sueltan y se meten las
        // gallinas) y un farol colgado del tejado, para verlas.
        colocar(level, new BlockPos(x1, nivel, z0), Blocks.HAY_BLOCK.defaultBlockState(), 3);
        colocar(level, new BlockPos(x1, nivel, z1), Blocks.HAY_BLOCK.defaultBlockState(), 3);
        colocar(level, new BlockPos(puertaX, nivel + 1, z1),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 3);
        // LA MUDANZA, en dos pasos y UNA sola vez (al construirlo; después, las de dentro ya no salen solas):
        // 1) Las GALLINAS del corral entran al gallinero, repartidas por sus huecos libres (la columna de la paja no
        //    cuenta, así que quedan 8 sitios: justo el tope de gallinas).
        AABB corral = new AABB(base).inflate(ANEXO_RADIO + 2, 8.0D, ANEXO_RADIO + 2);
        int columnasLibres = (GALLINERO_X1 - 1) - GALLINERO_X0 + 1;
        int gallinas = 0;
        for (net.minecraft.world.entity.animal.Chicken gallina
                : level.getEntitiesOfClass(net.minecraft.world.entity.animal.Chicken.class, corral)) {
            int celda = gallinas++ % (columnasLibres * 2);
            gallina.moveTo(x0 + 0.5D + (celda % columnasLibres), nivel,
                    z0 + 0.5D + (celda / columnasLibres), gallina.getYRot(), 0.0F);
        }
        // 2) Lo que NO sea gallina y estuviera justo donde se acaba de levantar el gallinero sale al corral: un
        //    cerdo dentro de un corral de pollos estorba y, si le tocó una pared o la paja, se asfixia.
        AABB cajaGallinero = new AABB(x0 - 1, nivel, z0 - 1, x1 + 2, nivel + 3, z1 + 2);
        int sacados = 0;
        for (net.minecraft.world.entity.animal.Animal animal : level.getEntitiesOfClass(
                net.minecraft.world.entity.animal.Animal.class, cajaGallinero)) {
            if (animal instanceof net.minecraft.world.entity.animal.Chicken) {
                continue; // las gallinas se quedan (son las dueñas)
            }
            animal.moveTo(puertaX + 0.5D + (sacados % 2) * 0.9D, nivel, z1 + 2.5D + (sacados / 2) * 0.9D,
                    animal.getYRot(), 0.0F);
            sacados++;
        }
    }

    /** ¿Está el corral <b>sin ningún animal</b> de las especies del anexo? (para el rebaño inicial). */
    public static boolean corralVacio(ServerLevel level, BlockPos center) {
        return animalesDelCorral(level, center).isEmpty();
    }

    /** Los animales del corral (solo las especies del anexo, no cualquier bicho que pase por ahí). */
    public static List<net.minecraft.world.entity.animal.Animal> animalesDelCorral(ServerLevel level, BlockPos center) {
        BlockPos base = baseDeAnexo(center);
        AABB caja = new AABB(base).inflate(ANEXO_RADIO + 2, 8.0D, ANEXO_RADIO + 2);
        List<net.minecraft.world.entity.animal.Animal> dentro = new ArrayList<>();
        for (net.minecraft.world.entity.animal.Animal animal : level.getEntitiesOfClass(
                net.minecraft.world.entity.animal.Animal.class, caja)) {
            if (ANEXO_ESPECIES.contains(animal.getType())) {
                dentro.add(animal);
            }
        }
        return dentro;
    }

    /**
     * Suelta el <b>rebaño inicial</b> del corral (2 vacas, 2 ovejas, 2 puercos y 4 gallinas). Lo llama el gestor de
     * la aldea al construir el anexo y, después, solo si el corral se quedó <b>vacío</b> y ha pasado
     * {@link #ANEXO_REBANO_ESPERA_TICKS} (ver {@code VillageManager}).
     */
    public static void criarRebanoInicial(ServerLevel level, BlockPos center) {
        int nivel = cotaDeLaPlaza(level, center);
        for (EntityType<? extends net.minecraft.world.entity.animal.Animal> tipo : ANEXO_ESPECIES) {
            criarAnimales(level, tipo, sitioDelRebano(tipo, center, nivel),
                    tipo == EntityType.CHICKEN ? 4 : 2);
        }
        DevilRpg.LOGGER.info("[Village] Aldea en {}: rebano inicial del corral anexo ({} animales)",
                center, animalesDelCorral(level, center).size());
    }

    /**
     * <b>Repone la pareja</b> de una especie (dos adultos) en su rincón del corral, marcados como del rebaño.
     * <p>
     * Es la red de seguridad de la <b>pareja</b> (lo pidió el jugador: "que siempre haya una pareja"): mientras a una
     * especie le queden <b>dos</b> animales, el ganadero puede criarla; con <b>uno solo</b> ya no hay cría posible y
     * esa especie no volvería <b>nunca</b> (ni carne de vaca, ni lana, ni huevos). Con la misma espera larga que el
     * rebaño inicial, para que siga sin ser un grifo de carne.
     */
    public static void criarParejaDe(ServerLevel level, BlockPos center,
                                     EntityType<? extends net.minecraft.world.entity.animal.Animal> tipo,
                                     int cuantos) {
        if (cuantos <= 0) {
            return;
        }
        int nivel = cotaDeLaPlaza(level, center);
        criarAnimales(level, tipo, sitioDelRebano(tipo, center, nivel), cuantos);
        DevilRpg.LOGGER.info("[Village] Aldea en {}: {} {} repuestos en el corral (se habia quedado sin pareja)",
                center, cuantos, tipo.getDescription().getString());
    }

    /**
     * ¿A alguna especie del corral le falta la <b>pareja</b> (menos de dos adultos)? Entonces no hay cría posible y el
     * pueblo tiene que traer animales. Devuelve {@code true} si ha repuesto alguna (y deja el log).
     */
    public static boolean reponerParejasDelCorral(ServerLevel level, BlockPos center) {
        List<net.minecraft.world.entity.animal.Animal> corral = animalesDelCorral(level, center);
        boolean repuesta = false;
        for (EntityType<? extends net.minecraft.world.entity.animal.Animal> tipo : ANEXO_ESPECIES) {
            int adultos = 0;
            for (net.minecraft.world.entity.animal.Animal animal : corral) {
                if (animal.getType() == tipo && !animal.isBaby()) {
                    adultos++;
                }
            }
            int faltan = tipo == EntityType.CHICKEN ? GALLINAS_MINIMAS - adultos : PAREJA_MINIMA - adultos;
            if (faltan > 0) {
                criarParejaDe(level, center, tipo, faltan);
                repuesta = true;
            }
        }
        return repuesta;
    }

    /** Dónde vive (y dónde se suelta) cada especie dentro del corral: su rincón de siempre. */
    private static BlockPos sitioDelRebano(EntityType<?> tipo, BlockPos center, int nivel) {
        BlockPos base = baseDeAnexo(center);
        if (tipo == EntityType.SHEEP) {
            return new BlockPos(base.getX() + 1, nivel, base.getZ() + 4);
        }
        if (tipo == EntityType.PIG) {
            return new BlockPos(base.getX() - 4, nivel, base.getZ() + 1);
        }
        if (tipo == EntityType.CHICKEN) {
            // Las GALLINAS, DENTRO del gallinero: encerradas desde el primer día (una valla sola no las para y el
            // jugador las quiere dentro, con sus huevos a mano del ganadero).
            return centroDelGallinero(center, nivel);
        }
        return new BlockPos(base.getX() - 2, nivel, base.getZ() - 4); // vacas
    }

    /** Marca (datos persistentes) que dice que ese animal es <b>del corral de la aldea</b> y viaja con él. */
    private static final String REBANO_TAG = "DevilRpgDelCorral";
    /**
     * Radio (desde el corral) en el que se <b>reconoce</b> al ganado del pueblo en una partida vieja. Es ancho a
     * propósito: medido en el guardado del jugador, el rebaño escapado se había ido a <b>80-87 bloques</b> del corral
     * (y el que más lejos, a 130). Se usa <b>una sola vez</b>, al migrar, y nunca dentro de la muralla: allí manda el
     * jugador (sus corrales y sus animales no se tocan).
     */
    private static final double REBANO_ADOPCION = ANEXO_RADIO + 89;

    /** ¿Ese animal lleva la <b>marca del pueblo</b>? (solo a ésos se les manda de vuelta al corral). */
    public static boolean esDelRebano(net.minecraft.world.entity.animal.Animal animal) {
        return animal.getPersistentData().getBoolean(REBANO_TAG);
    }

    /**
     * ¿Ese animal está <b>dentro del corral</b>? Es el <b>rectángulo de la valla</b> (el mismo que mira la guardia),
     * no un radio.
     * <p>
     * Y no es un detalle: el "ya está en casa" era un <b>radio de 26 bloques</b> desde el centro del corral (el
     * corral tiene 9), así que un animal que se salía por el portón y se quedaba pastando <b>al lado de la valla</b>
     * contaba como "dentro" y no volvía <b>nunca</b>. Medido en el guardado del jugador (aldea 2): la vaca del pueblo
     * a <b>12,1</b> bloques del corral y la oveja a <b>13,5</b>, las dos fuera de la valla y con el portón abierto.
     * Todo lo que esté fuera de la valla es "perdido".
     */
    public static boolean enElCorral(BlockPos center, net.minecraft.world.entity.Entity animal) {
        return estaEnElAnexo(center, animal.blockPosition());
    }

    /**
     * La caja donde se busca al <b>ganado del pueblo perdido</b>: el <b>recinto entero de la aldea</b> (su radio, con
     * un margen) <b>y</b> el radio de reconocimiento alrededor del corral (el rebaño se iba a 80-130 bloques, fuera de
     * la muralla). Antes era solo la caja del corral (±98 de su base), así que un animal marcado que se hubiera ido al
     * <b>otro extremo</b> del pueblo (a 112 de la base del corral) no se veía nunca.
     * <p>
     * La Y se mide desde la <b>cota</b> (invariante I1), no desde la Y del centro, y con la banda de siempre (±24): no
     * se trae a casa a un bicho de una cueva, pero sí a uno que esté en el tejado de al lado.
     */
    private static AABB cajaDelGanadoPerdido(BlockPos center, int nivel) {
        BlockPos base = baseDeAnexo(center);
        double radio = LEVEL_RADIUS + 8;
        double x0 = Math.min(center.getX() - radio, base.getX() - REBANO_ADOPCION);
        double x1 = Math.max(center.getX() + radio, base.getX() + REBANO_ADOPCION);
        double z0 = Math.min(center.getZ() - radio, base.getZ() - REBANO_ADOPCION);
        double z1 = Math.max(center.getZ() + radio, base.getZ() + REBANO_ADOPCION);
        return new AABB(x0, nivel - 24.0D, z0, x1, nivel + 24.0D, z1);
    }

    /**
     * <b>Reconoce</b> (una sola vez, al migrar) al ganado del pueblo que se había escapado antes de que existiera la
     * marca. Solo mira animales que:
     * <ul>
     *   <li>son de las especies del corral,</li>
     *   <li>son <b>persistentes</b> (el juego solo los marca así cuando alguien los ha criado o tocado: un bicho
     *       salvaje no lo es, y los del pueblo sí, que se sueltan con la marca puesta),</li>
     *   <li>no van montados ni atados con una cuerda (ésos son de alguien: el jugador), y</li>
     *   <li>están <b>fuera de la muralla</b> y a menos de {@link #REBANO_ADOPCION} del corral.</li>
     * </ul>
     * Lo de dentro de la muralla no se toca jamás: si el jugador tiene allí su corral, son suyos. (Un animal del
     * pueblo que se cuele <b>dentro</b> de la muralla ya lleva la marca, así que no depende de esto para volver: ver
     * {@link #ganadoPerdidoDelPueblo}.)
     */
    public static int adoptarGanadoPerdido(ServerLevel level, BlockPos center) {
        int nivel = cotaDeLaPlaza(level, center);
        if (nivel <= level.getMinBuildHeight() + 1 || !anexoConstruido(level, center)) {
            return 0;
        }
        BlockPos base = baseDeAnexo(center);
        AABB caja = new AABB(base).inflate(REBANO_ADOPCION + 8, 24.0D, REBANO_ADOPCION + 8);
        int adoptados = 0;
        for (net.minecraft.world.entity.animal.Animal animal
                : level.getEntitiesOfClass(net.minecraft.world.entity.animal.Animal.class, caja)) {
            if (!ANEXO_ESPECIES.contains(animal.getType())
                    || esDelRebano(animal)
                    || !animal.isPersistenceRequired()
                    || animal.isPassenger() || animal.isVehicle() || animal.isLeashed()) {
                continue;
            }
            double alCorral = distanciaEnXZ(animal, base.getX() + 0.5D, base.getZ() + 0.5D);
            double alPueblo = distanciaEnXZ(animal, center.getX() + 0.5D, center.getZ() + 0.5D);
            if (alCorral > REBANO_ADOPCION || alPueblo <= LEVEL_RADIUS + 2) {
                continue; // demasiado lejos, o dentro de la muralla (ahí manda el jugador)
            }
            animal.getPersistentData().putBoolean(REBANO_TAG, true);
            adoptados++;
        }
        if (adoptados > 0) {
            DevilRpg.LOGGER.info("[Village] Aldea en {}: {} animal(es) sueltos reconocidos como ganado del pueblo"
                    + " (estaban fuera de la muralla, a menos de {} bloques del corral)", center, adoptados,
                    (int) REBANO_ADOPCION);
        }
        return adoptados;
    }

    /**
     * Distancia <b>horizontal</b> (XZ) de una entidad a un punto: la aldea es un recinto en XZ y la Y de un animal
     * que se ha escapado no dice nada de lo lejos que está (invariante I2).
     */
    private static double distanciaEnXZ(net.minecraft.world.entity.Entity entidad, double x, double z) {
        double dx = entidad.getX() - x;
        double dz = entidad.getZ() - z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * <b>Marca el rebaño y devuelve los que se han perdido.</b> Los devueltos son los que tienen que <b>volver a
     * casa</b>: el que llama (el latido) les pone el goal de volver andando ({@code VuelveAlCorralGoal}), y solo si
     * no encuentran el camino se los mete a mano (último recurso).
     * <p>
     * Hace falta porque el ganado se escapa por el <b>portón</b> cuando el pueblo se lo abre: medido en el guardado del
     * jugador, su corral tenía <b>1 vaca</b> dentro y <b>8 vacas, 6 ovejas, 6 gallinas y 2 puercos</b> repartidos a
     * 76-83 bloques del pueblo. Un corral vacío <b>no da carne</b>: el ganadero no ve animales, así que no puede criar
     * ni sacrificar, y la granja entera se queda muerta.
     * <p>
     * Los que están <b>dentro</b> del corral se marcan (el rebaño inicial y sus crías ya son del pueblo); los que
     * andan fuera y lleven la marca se devuelven. Los que anden sueltos <b>sin</b> marca no se tocan: podrían ser del
     * jugador (ni los marcados que van <b>montados</b> o <b>atados con una cuerda</b>: ésos son de alguien). Es lo que
     * hace un pastor de verdad: traer de vuelta a la res que se le fue.
     */
    public static List<net.minecraft.world.entity.animal.Animal> ganadoPerdidoDelPueblo(ServerLevel level,
                                                                                       BlockPos center) {
        int nivel = cotaDeLaPlaza(level, center);
        List<net.minecraft.world.entity.animal.Animal> perdidos = new ArrayList<>();
        if (nivel <= level.getMinBuildHeight() + 1 || !anexoConstruido(level, center)) {
            return perdidos; // sin cota o sin corral no hay rebaño que recoger
        }
        for (net.minecraft.world.entity.animal.Animal animal : level.getEntitiesOfClass(
                net.minecraft.world.entity.animal.Animal.class, cajaDelGanadoPerdido(center, nivel))) {
            if (!ANEXO_ESPECIES.contains(animal.getType()) || animal.isPassenger() || animal.isVehicle()
                    || animal.isLeashed()) {
                continue; // no es del corral, o va montado/atado: no se toca
            }
            if (enElCorral(center, animal)) {
                animal.getPersistentData().putBoolean(REBANO_TAG, true); // está en el corral: es del pueblo
                continue;
            }
            if (!esDelRebano(animal)) {
                continue; // suelto y sin marca: no es nuestro
            }
            perdidos.add(animal);
        }
        return perdidos;
    }

    /**
     * Dónde se deja a un animal que vuelve <b>a la fuerza</b> (último recurso, cuando no encuentra el camino): las
     * gallinas a su gallinero y el resto a un hueco libre del corral (nunca en el bebedero ni en el gallinero de las
     * gallinas).
     */
    public static BlockPos destinoDelAnimal(ServerLevel level, BlockPos center, int nivel,
                                            net.minecraft.world.entity.animal.Animal animal, int indice) {
        if (animal instanceof net.minecraft.world.entity.animal.Chicken) {
            BlockPos gallinero = centroDelGallinero(center, nivel);
            return new BlockPos(gallinero.getX() + indice % 4, gallinero.getY(), gallinero.getZ() + indice % 2);
        }
        List<BlockPos> libres = huecosLibresDelCorral(center, nivel);
        return libres.isEmpty() ? puntoDeApoyoAnexo(level, center) : libres.get(indice % libres.size());
    }

    /**
     * Huecos del corral donde se puede soltar un animal sin meterlo en el bebedero ni en el gallinero (que es de las
     * gallinas). Se calculan al vuelo: son pocos bloques y esto solo corre cuando hay ganado perdido que recoger.
     */
    private static List<BlockPos> huecosLibresDelCorral(BlockPos center, int nivel) {
        BlockPos base = baseDeAnexo(center);
        List<BlockPos> libres = new ArrayList<>();
        for (int dx = -ANEXO_RADIO + 2; dx <= ANEXO_RADIO - 2; dx++) {
            for (int dz = -ANEXO_RADIO + 2; dz <= ANEXO_RADIO - 2; dz++) {
                if (esBebedero(dx, dz)) {
                    continue; // el agua es para beber, no para pisarla
                }
                if (dx >= GALLINERO_X0 - 1 && dx <= GALLINERO_X1 + 1
                        && dz >= GALLINERO_Z0 - 1 && dz <= GALLINERO_Z1 + 1) {
                    continue; // dentro del gallinero no se mete una vaca
                }
                libres.add(new BlockPos(base.getX() + dx, nivel, base.getZ() + dz));
            }
        }
        return libres;
    }

    /** Suelta {@code cuantos} animales de esa especie, separados un poco para que no se apilen. */
    private static void criarAnimales(ServerLevel level, EntityType<? extends net.minecraft.world.entity.animal.Animal> tipo,
                                      BlockPos pos, int cuantos) {
        for (int i = 0; i < cuantos; i++) {
            net.minecraft.world.entity.animal.Animal animal = tipo.create(level);
            if (animal == null) {
                continue;
            }
            animal.moveTo(pos.getX() + 0.5D + (i % 2) * 0.8D, pos.getY(), pos.getZ() + 0.5D + (i / 2) * 0.8D,
                    level.random.nextFloat() * 360.0F, 0.0F);
            // Persistentes: que el juego no se los lleve por lejanía (el corral está fuera del muro y a veces el
            // jugador está lejos). Se quedan donde viven.
            animal.setPersistenceRequired();
            // Y MARCADOS como del rebaño del pueblo: es lo que permite reconocerlos si se escapan por el portón y
            // traerlos de vuelta sin tocar a los animales sueltos del jugador (ver {@link #ganadoPerdidoDelPueblo}).
            animal.getPersistentData().putBoolean(REBANO_TAG, true);
            level.addFreshEntity(animal);
        }
    }

    /**
     * Posiciones base de las casas de la aldea, relativas al centro (las mismas que usa {@link #generate}).
     * Con la aldea agrandada <b>al radio 62</b> los solares se han <b>repartido</b> por el recinto: cada casa va a un
     * cuadrante distinto, a unos 33-38 bloques del centro, dejando sitio entre ellas y sin pisar el almacén (que
     * sigue pegado a la plaza), las parcelas, la iglesia, el taller ni la barraca. Con el trazado de 36 estaban a
     * 21-25 y, al crecer la muralla, se habrían quedado apelotonadas en el centro.
     */
    public static BlockPos[] basesDeCasas(BlockPos center) {
        return new BlockPos[]{
                trazado(center, 0),
                trazado(center, 1),
                trazado(center, 2),
                // La cuarta casa (la "grande", con cama extra) va al norte, en su propio cuadrante.
                trazado(center, 3),
        };
    }

    /**
     * Solares de los trazados <b>ANTIGUOS</b>: las cuatro casas y la iglesia del trazado <b>de radio 29</b>, más las
     * cuatro casas, la iglesia, el <b>taller de los herreros</b> y la <b>barraca</b> del trazado <b>de radio 36</b>.
     * Están escritos aquí a mano y <b>no</b> se deben "arreglar": son las coordenadas del pasado, y su único uso es
     * <b>limpiarlas</b> al migrar (si no, el pueblo se queda con los edificios viejos de pie al lado de los nuevos).
     */
    private static final int[][] SOLARES_ANTIGUOS = {
            // Trazado de radio 29 (el más viejo)
            {-17, -3}, {16, -4}, {-3, 17}, {10, -18}, // casas (la 4ª, la grande)
            {-9, -20},                                // iglesia
            // Trazado de radio 36 (el que se sustituye al pasar al radio 62)
            {-21, -4}, {20, -5}, {-5, 21}, {13, -23}, // casas (la 4ª, la grande)
            {-12, -25},                               // iglesia
            {2, -26},                                 // taller de los herreros
            {-26, 13},                                // barraca de la milicia
    };
    /**
     * Esquinas de las parcelas de la granja de los trazados antiguos (se devuelven a césped): las del radio 29 y las
     * del 36.
     */
    private static final int[][] PARCELAS_ANTIGUAS = {{-16, 8}, {8, 6}, {-20, 10}, {10, 6}};
    /**
     * Radios de las vallas de los trazados antiguos: esos anillos hay que <b>borrarlos</b>, que si no queda un muro
     * (o dos) cruzando el pueblo por medio. El muro de la aldea se rehace en {@code rehacerMuro} al radio actual.
     */
    private static final int[] RADIOS_MURO_ANTIGUOS = {29, 36};

    /**
     * <b>Limpia el trazado antiguo</b> de una aldea que se migra al trazado agrandado: quita las construcciones de
     * los solares viejos (casas, iglesia, taller y barraca), devuelve a césped las parcelas viejas de la granja y
     * barre los caminos de tierra apisonada del trazado viejo (los caminos nuevos se vuelven a dibujar después, en
     * {@link #actualizarCasas}).
     * <p>
     * Solo se lleva lo <b>construido</b>: el terreno, los troncos y las hojas no se tocan.
     */
    public static void limpiarTrazadoAntiguo(ServerLevel level, BlockPos center) {
        int nivel = cotaDeLaPlaza(level, center);
        if (nivel <= level.getMinBuildHeight() + 1) {
            return;
        }
        int quitados = 0;
        // 1) Construcciones viejas. La caja va desde la base hacia +x/+z porque la base de una plantilla es su
        //    ESQUINA, no su centro (la mayor es la casa mediana, 13x11): de -2 a +13 en x y de -2 a +12 en z cubre
        //    cualquier casa vieja con margen.
        for (int[] solar : SOLARES_ANTIGUOS) {
            BlockPos c = center.offset(solar[0], 0, solar[1]);
            sacarVecinosDe(level, c, center); // nadie dentro de una casa que se va a derribar
            for (int dx = -2; dx <= 13; dx++) {
                for (int dz = -2; dz <= 12; dz++) {
                    for (int y = nivel - 1; y <= nivel + 13; y++) {
                        BlockPos p = new BlockPos(c.getX() + dx, y, c.getZ() + dz);
                        BlockState state = level.getBlockState(p);
                        if (state.isAir() || esTerrenoNatural(state)) {
                            continue; // el terreno no se toca
                        }
                        // OJO con los TRONCOS: aquí SÍ se quitan. La casa vieja deja en pie sus postes de esquina (son
                        // troncos) y al migrar quedaban cuatro palos sueltos donde estaba el edificio. El MURO de la
                        // aldea no corre peligro: sus anillos viejos (29 y 36) se borran aparte en el paso 2, y estas
                        // cajas (los solares) están a 16-25 del centro, lejos de la muralla actual (radio 62).
                        colocar(level, p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                        quitados++;
                    }
                }
            }
            // El suelo de las casas viejas era de TIERRA: al derribarlas quedaban parches marrones. Se devuelve el
            // césped a la capa que se pisa (solo donde no haya quedado nada construido).
            for (int dx = -2; dx <= 13; dx++) {
                for (int dz = -2; dz <= 12; dz++) {
                    ponerCesped(level, c.getX() + dx, c.getZ() + dz, nivel);
                }
            }
        }
        // 2) MUROS VIEJOS (radios 29 y 36): al agrandar la aldea esos anillos quedan DENTRO del recinto, así que hay
        //    que borrarlos o el pueblo se queda con una muralla (o dos) de troncos y piedra cruzándolo por medio. Se
        //    hace AQUÍ, antes de levantar las casas nuevas: los anillos viejos cruzan por dentro de los solares
        //    nuevos y limpiarlos después les arrancaría trozos de pared.
        int muroViejo = 0;
        for (int radioViejo : RADIOS_MURO_ANTIGUOS) {
            for (BlockPos p : anilloDelMuro(center, radioViejo)) {
                for (int y = nivel - 1; y <= nivel + 8; y++) {
                    BlockPos q = new BlockPos(p.getX(), y, p.getZ());
                    BlockState state = level.getBlockState(q);
                    boolean restosDeMuro = state.is(Blocks.OAK_LOG) || state.is(Blocks.COBBLESTONE)
                            || state.is(Blocks.COBBLESTONE_STAIRS) || state.is(Blocks.COBBLESTONE_WALL)
                            || state.is(Blocks.COBBLESTONE_SLAB);
                    if (!restosDeMuro) {
                        continue;
                    }
                    colocar(level, q, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    muroViejo++;
                }
                // El relleno de tierra del muro viejo se devuelve a césped: si no, queda una franja de tierra a la
                // vista.
                ponerCesped(level, p.getX(), p.getZ(), nivel);
            }
        }
        // 3) Parcelas viejas de la granja: tierra de cultivo, acequia, losas, cultivos y composteros se devuelven a
        //    césped. `farm()` corre DESPUÉS y vuelve a hacer las parcelas en su sitio nuevo.
        for (int[] parcela : PARCELAS_ANTIGUAS) {
            BlockPos c = center.offset(parcela[0], 0, parcela[1]);
            for (int dx = -1; dx <= PLOT_WIDTH; dx++) {
                for (int dz = -1; dz <= PLOT_DEPTH; dz++) {
                    int x = c.getX() + dx;
                    int z = c.getZ() + dz;
                    for (int y = nivel - 1; y <= nivel + 3; y++) {
                        BlockPos p = new BlockPos(x, y, z);
                        BlockState state = level.getBlockState(p);
                        if (state.isAir() || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) {
                            continue;
                        }
                        if (y == nivel - 1) {
                            // La capa que se pisa vuelve a ser césped (era tierra de cultivo o la acequia).
                            if (state.is(Blocks.FARMLAND) || state.is(Blocks.WATER)
                                    || esTerrenoRecortable(state)) {
                                colocar(level, p, Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                            }
                            continue;
                        }
                        colocar(level, p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        quitados++;
                    }
                }
            }
        }
        // 4) Caminos del trazado viejo: los de tierra apisonada a ras de suelo se borran TODOS y `paths()` los
        //    vuelve a trazar hacia las puertas nuevas. Si no, quedarían caminos que llevan a casas que ya no están.
        int caminos = 0;
        for (int dx = -FENCE_RADIUS; dx <= FENCE_RADIUS; dx++) {
            for (int dz = -FENCE_RADIUS; dz <= FENCE_RADIUS; dz++) {
                if (dx * dx + dz * dz > FENCE_RADIUS * FENCE_RADIUS) {
                    continue;
                }
                for (int y = nivel - 2; y <= nivel + 1; y++) {
                    BlockPos p = new BlockPos(center.getX() + dx, y, center.getZ() + dz);
                    if (level.getBlockState(p).is(Blocks.DIRT_PATH)) {
                        colocar(level, p, Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
                        caminos++;
                    }
                }
            }
        }
        DevilRpg.LOGGER.info("[Village] Aldea en {}: trazado antiguo limpiado ({} bloques, {} muro viejo, "
                + "{} camino)", center, quitados, muroViejo, caminos);
    }

    /**
     * Limpia la <b>vegetación del anexo</b>: el terreno que entra en el recinto al agrandar la aldea (del muro
     * viejo hacia fuera, radio {@code RADIOS_MURO_ANTIGUOS[0]} - 3 hasta el final del talud) estaba <b>fuera</b> de la
     * aldea, así que puede tener árboles que se quedarían dentro del pueblo, sobre el talud o atravesando el muro
     * nuevo.
     * <p>
     * Hay que llamarlo <b>antes</b> de nivelar: el nivelado respeta los troncos a propósito (los del muro son
     * troncos), así que un árbol en un hoyo acaba con el hoyo rellenado a su alrededor y el árbol dentro. Se salta
     * la caja del <b>almacén</b>, cuyos postes también son troncos y caen justo en el borde de la banda.
     */
    public static void limpiarVegetacionDelAnexo(ServerLevel level, BlockPos center) {
        int rMin = RADIOS_MURO_ANTIGUOS[0] - 3;
        int rMax = LEVEL_RADIUS + SLOPE_WIDTH;
        BlockPos almacen = VillageStorage.centro(center);
        int quitados = 0;
        for (int dx = -rMax; dx <= rMax; dx++) {
            for (int dz = -rMax; dz <= rMax; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist <= rMin || dist > rMax) {
                    continue;
                }
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                if (Math.abs(x - almacen.getX()) <= 4 && Math.abs(z - almacen.getZ()) <= 4) {
                    continue; // los postes del almacén no son vegetación
                }
                int topY = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z)).getY();
                // De arriba abajo: se quitan hojas y troncos y se PARA al llegar al suelo (así no se baja 60 bloques
                // por columna para nada).
                for (int y = topY; y > topY - 60 && y >= level.getMinBuildHeight(); y--) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(p);
                    if (isVegetation(state)) {
                        colocar(level, p, Blocks.AIR.defaultBlockState(), 3);
                        quitados++;
                        continue;
                    }
                    if (!state.isAir()) {
                        break; // suelo: se acabó el árbol
                    }
                }
            }
        }
        if (quitados > 0) {
            DevilRpg.LOGGER.info("[Village] Aldea en {}: {} bloques de vegetacion quitados del anexo", center, quitados);
        }
    }

    /**
     * ¿Ese tronco es un <b>árbol suelto</b> (del monte) y no parte de la aldea construida? Es la pregunta clave para
     * despejar el recinto y para que el leñador sepa qué puede talar, y se contesta por la <b>forma</b>, no por el
     * plano: el plano de una aldea migrada <b>también contiene los árboles</b> que se encontró dentro (al capturarlo
     * se escanea el mundo y los troncos no se descartan a propósito, porque el muro y las casas son de troncos), así
     * que preguntarle al plano daba "es del pueblo" para los 137 árboles del jugador.
     * <p>
     * Un árbol suelto cumple las tres:
     * <ol>
     *   <li>el tronco está <b>de pie</b> (eje Y): los tramos del muro son troncos <b>tumbados</b> (eje X o Z);</li>
     *   <li>tiene <b>hojas cerca</b> por encima (un poste no las tiene);</li>
     *   <li>no tiene <b>nada construido pegado</b> (ni tablones, ni piedra, ni vallas, ni cristales): los postes de las
     *       casas van pegados a sus paredes.</li>
     * </ol>
     */
    public static boolean esArbolSuelto(ServerLevel level, BlockPos p) {
        BlockState state = level.getBlockState(p);
        if (!state.is(BlockTags.LOGS)) {
            return false;
        }
        if (state.hasProperty(RotatedPillarBlock.AXIS) && state.getValue(RotatedPillarBlock.AXIS) != Direction.Axis.Y) {
            return false; // tronco tumbado: es un tramo del muro
        }
        // 1) hojas cerca, por encima: es lo que distingue un árbol de un poste. Se mira hasta 12 bloques arriba para
        // que también cuente la base de un árbol alto (una selva los tiene de 20, pero con 12 sobra para los del
        // pueblo y para no confundir un poste con las hojas de un árbol vecino).
        boolean hojas = false;
        for (int dy = 1; dy <= 12 && !hojas; dy++) {
            for (BlockPos q : BlockPos.betweenClosed(p.offset(-2, dy, -2), p.offset(2, dy, 2))) {
                if (level.getBlockState(q).is(BlockTags.LEAVES)) {
                    hojas = true;
                    break;
                }
            }
        }
        if (!hojas) {
            return false;
        }
        // 2) nada construido pegado (los postes de las casas van pegados a sus paredes).
        for (BlockPos q : BlockPos.betweenClosed(p.offset(-1, -1, -1), p.offset(1, 1, 1))) {
            if (q.equals(p)) {
                continue;
            }
            BlockState vecino = level.getBlockState(q);
            if (vecino.isAir() || esTerrenoNatural(vecino)) {
                continue; // aire, tierra, agua, hojas, otros troncos...: el monte
            }
            return false; // tablones, piedra, valla, cristal...: es parte de una construcción
        }
        return true;
    }

    /**
     * <b>Despeja los árboles que quedaron DENTRO del recinto</b> (menos los de la <b>arboleda del pueblo</b>), que es
     * lo que hace un pueblo al fundarse: se asienta en un claro, no dentro del bosque.
     * <p>
     * Hace falta porque el generador <b>sí</b> despeja el volumen al construir ({@code despejarVolumen}), pero las
     * aldeas <b>migradas</b> se encontraron el bosque ya dentro: medido en el guardado del jugador había <b>137
     * árboles</b> de verdad dentro de la muralla, y encima quedaron <b>grabados en el plano</b>, así que el leñador
     * los daba por construidos y no los tocaba <b>nunca</b> (lo reportó el jugador: "el leñador no está cortando los
     * árboles que están dentro de la aldea").
     * <p>
     * Solo se quitan <b>hojas</b> y troncos que pasen {@link #esArbolSuelto} (nunca otra cosa): el muro —troncos
     * tumbados—, los postes de las casas y los del almacén se quedan donde están. La arboleda del pueblo
     * ({@link #enLaArboleda}) tampoco se toca: es la madera del pueblo.
     */
    public static int limpiarArbolesDeDentro(ServerLevel level, BlockPos center) {
        int nivel = cotaDeLaPlaza(level, center);
        if (nivel <= level.getMinBuildHeight() + 1) {
            return 0;
        }
        int quitados = 0;
        int troncos = 0;
        int techo = nivel + ALTURA_MAXIMA_DE_ARBOL;
        for (int dx = -FENCE_RADIUS; dx <= FENCE_RADIUS; dx++) {
            for (int dz = -FENCE_RADIUS; dz <= FENCE_RADIUS; dz++) {
                if (dx * dx + dz * dz > FENCE_RADIUS * FENCE_RADIUS) {
                    continue;
                }
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                if (enLaArboleda(center, new BlockPos(x, 0, z))) {
                    continue; // la arboleda del pueblo es su madera: no se despeja
                }
                for (int y = nivel - 1; y <= techo; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(p);
                    if (state.is(BlockTags.LEAVES)) {
                        colocar(level, p, Blocks.AIR.defaultBlockState(), 3);
                        quitados++;
                        continue;
                    }
                    if (!esArbolSuelto(level, p)) {
                        continue; // ni el terreno, ni lo construido, ni el muro
                    }
                    colocar(level, p, Blocks.AIR.defaultBlockState(), 3);
                    quitados++;
                    troncos++;
                }
            }
        }
        if (quitados > 0) {
            DevilRpg.LOGGER.info("[Village] Aldea en {}: {} bloques de arboles quitados de DENTRO del recinto"
                    + " ({} troncos; la arboleda y lo construido no se tocan)", center, quitados, troncos);
        }
        return quitados;
    }

    /**
     * <b>Migración de aldeas viejas</b>: sustituye las cabañas procedurales ({@link #hut}, las de las partidas
     * anteriores) por las <b>casas del juego</b>. Por cada casa se limpia su solar y se coloca la plantilla nueva.
     * <p>
     * El despeje quita <b>solo lo construido</b> (nunca el terreno: se salta lo que devuelve
     * {@link #esTerrenoNatural}) en una caja alrededor de la base, que se lleva también el tejado, los muebles y
     * cualquier escalón suelto de la cabaña vieja. Después se vuelven a trazar los caminos, porque la puerta de
     * la casa nueva puede dar a otro lado.
     */
    public static BlockPos[] actualizarCasas(ServerLevel level, BlockPos center) {
        // Aviso: rehacer una casa borra lo que haya dentro (queda en el log con un WARN).
        DevilRpg.LOGGER.warn("[Village] Aldea en {}: se rehacen las casas al nivel del terreno (se pierde lo de "
                + "dentro de las casas viejas)", center);
        BlockPos[] bases = basesDeCasas(center);
        BlockPos[] puertas = new BlockPos[bases.length];
        RandomSource casas = RandomSource.create(center.asLong());
        // Se vuelve a dejar TODO el terreno de la aldea a una sola cota (protegiendo lo que no sea natural) y las
        // casas se colocan a esa cota: así desaparecen las zanjas y los hundimientos de las aldeas ya construidas.
        // El ANEXO (el terreno que ahora entra en el recinto) estaba fuera de la aldea: su vegetación se quita ANTES
        // de nivelar, porque el nivelado respeta los troncos (los del muro son troncos) y un árbol en un hoyo acaba
        // con el hoyo rellenado a su alrededor y el árbol dentro.
        limpiarVegetacionDelAnexo(level, center);
        int nivelVilla = prepararTerreno(level, center);
        // Se derriba el TRAZADO ANTIGUO (casas, iglesia, parcelas y caminos de los solares viejos) antes de levantar
        // el nuevo: si no, la aldea agrandada tendría los edificios nuevos Y los viejos, uno al lado del otro.
        limpiarTrazadoAntiguo(level, center);
        for (int i = 0; i < bases.length; i++) {
            BlockPos base = bases[i];
            // SEGURIDAD: si hay aldeanos o golems dentro de la casa que se va a rehacer, se les saca a la plaza
            // antes de tocar nada. Si no, al colocar la plantilla podrían quedar dentro de una pared (asfixia).
            sacarVecinosDe(level, base, center);
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    int suelo = groundY(level, base.getX() + dx, base.getZ() + dz);
                    for (int dy = -2; dy <= 9; dy++) {
                        BlockPos p = new BlockPos(base.getX() + dx, suelo + dy, base.getZ() + dz);
                        BlockState state = level.getBlockState(p);
                        if (state.isAir() || esTerrenoNatural(state)) {
                            continue; // el terreno (y el agua, los caminos y los cultivos) no se toca aquí
                        }
                        colocar(level, p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
            puertas[i] = placeVanillaHouse(level, base,
                    i == bases.length - 1 ? casaGrandeAleatoria(casas) : casaAleatoria(casas), nivelVilla);
        }
        // Los caminos se vuelven a trazar DESPUÉS de quitar los que hubieran quedado en alto (encima de los tejados
        // por el bug de la cota del kiosco): si no, seguirían ahí y el plano los daría por buenos.
        limpiarCaminosFlotantes(level, center, nivelVilla);
        paths(level, center, puertas);
        DevilRpg.LOGGER.info("[Village] Aldea vieja en {}: cabañas sustituidas por casas del juego", center);
        return puertas;
    }

    /**
     * Coloca un bloque y, si se está grabando, lo apunta en el plano. <b>Todo</b> lo que construye el generador
     * pasa por aquí: así el plano es lo que la aldea <i>debe</i> ser, no una foto del estado en que se la
     * encontró (que era el problema de capturarlo leyendo el mundo: si la aldea ya estaba dañada, ese daño se
     * volvía "lo correcto").
     */
    private static void colocar(ServerLevel level, BlockPos pos, BlockState state, int flags) {
        level.setBlock(pos, state, flags);
        if (grabadora != null) {
            grabadora.apunta(pos, state);
        }
    }

    /**
     * Apunta en el plano todo lo que hay en una caja. Se usa para las <b>plantillas</b> de las casas: sus bloques
     * los coloca {@code StructureTemplate.placeInWorld}, que no pasa por {@link #colocar}, así que hay que
     * leerlos después (ya limpiados los bloques técnicos).
     */
    private static void apuntarCaja(ServerLevel level, BlockPos origen, Vec3i tam) {
        if (grabadora == null) {
            return;
        }
        for (int dx = 0; dx < tam.getX(); dx++) {
            for (int dy = 0; dy < tam.getY(); dy++) {
                for (int dz = 0; dz < tam.getZ(); dz++) {
                    BlockPos pos = origen.offset(dx, dy, dz);
                    grabadora.apunta(pos, level.getBlockState(pos));
                }
            }
        }
    }

    /**
     * Coloca <b>camas de más</b> dentro de una casa (hasta {@code cuantas}): busca huecos libres de dos bloques con
     * suelo firme y va poniendo camas. Con más camas la aldea puede crecer (cada cría necesita una cama libre).
     * <p>
     * OJO: la cama es un obstáculo, así que <b>nunca se pone en la entrada ni pegada a ella</b> (bloqueaba el paso y
     * el aldeano se quedaba entrando y saliendo de la casa sin poder llegar a dormir) y se exige <b>aire encima</b>
     * (una cama sin hueco arriba no sirve para dormir).
     *
     * @return cuántas camas colocó de verdad
     */
    private static int camasExtra(ServerLevel level, BlockPos origen, Vec3i tam, int cuantas, @Nullable BlockPos puerta) {
        int puestas = 0;
        int pdx = puerta == null ? Integer.MIN_VALUE : puerta.getX() - origen.getX();
        int pdz = puerta == null ? Integer.MIN_VALUE : puerta.getZ() - origen.getZ();
        for (int dy = 1; dy < tam.getY() - 1 && puestas < cuantas; dy++) {
            for (int dx = 1; dx < tam.getX() - 1 && puestas < cuantas; dx++) {
                for (int dz = 1; dz < tam.getZ() - 2 && puestas < cuantas; dz++) {
                    // La entrada y su casilla contigua quedan libres (pasillo de la casa).
                    if (Math.abs(dx - pdx) <= 1 && Math.abs(dz - pdz) <= 1) {
                        continue;
                    }
                    BlockPos pies = origen.offset(dx, dy, dz);
                    BlockPos cabeza = pies.relative(Direction.SOUTH);
                    if (!level.getBlockState(pies).isAir() || !level.getBlockState(cabeza).isAir()) {
                        continue;
                    }
                    if (!level.getBlockState(pies.above()).isAir() || !level.getBlockState(cabeza.above()).isAir()) {
                        continue; // sin hueco arriba la cama no se puede usar
                    }
                    // NI ENCIMA DE OTRA CAMA: una cama es sólida, así que servía de "suelo" y se apilaban.
                    if (level.getBlockState(pies.below()).getBlock() instanceof net.minecraft.world.level.block.BedBlock
                            || level.getBlockState(cabeza.below()).getBlock() instanceof net.minecraft.world.level.block.BedBlock) {
                        continue;
                    }
                    if (!level.getBlockState(pies.below()).isSolid() || !level.getBlockState(cabeza.below()).isSolid()) {
                        continue;
                    }
                    bed(level, pies);
                    puestas++;
                }
            }
        }
        return puestas;
    }

    /**
     * Segunda <b>puerta</b> en la pared de enfrente de la casa (para poder cruzarla). Solo se pone si en esa pared hay
     * un bloque macizo y hay aire a los dos lados: así nunca se abre un boquete en la plantilla.
     */
    private static void puertaExtra(ServerLevel level, BlockPos origen, Vec3i tam, BlockPos puerta) {
        // La puerta original está en el interior de la caja: se refleja en el eje Z (pared de enfrente).
        int dx = puerta.getX() - origen.getX();
        int dy = puerta.getY() - origen.getY();
        int dz = tam.getZ() - 1 - (puerta.getZ() - origen.getZ());
        BlockPos espejo = origen.offset(dx, dy, dz);
        BlockState pared = level.getBlockState(espejo);
        if (pared.isAir() || !pared.isSolid() || pared.getBlock() instanceof DoorBlock) {
            return;
        }
        BlockPos fuera = espejo.relative(Direction.NORTH);
        BlockPos dentro = espejo.relative(Direction.SOUTH);
        if (!level.getBlockState(fuera).isAir() || !level.getBlockState(dentro).isAir()) {
            return;
        }
        if (!level.getBlockState(espejo.above()).isSolid()) {
            return; // hace falta el dintel para colgar la puerta de arriba
        }
        colocar(level, espejo, Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.SOUTH)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_ALL);
        colocar(level, espejo.above(), Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.SOUTH)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    /**
     * <b>Iglesia</b> de la aldea: el <b>templo con campanario</b> del propio juego. Sustituye a la torre
     * procedural de vigilancia ({@link #tower}, ahora LEGACY).
     * <p>
     * El juego trae dos templos y solo se usa uno: {@code plains_temple_4}, que es el que tiene <b>torre</b>
     * (12 bloques de alto). El otro ({@code plains_temple_3}) es un edificio bajo de 11x7x7 <b>sin torre</b> y el
     * jugador, con razón, lo veía como un cobertizo y no como una iglesia ("¿dónde está la iglesia?"). Ojo: la
     * campana de la aldea es la de la <b>plaza</b> — ninguna de las dos plantillas trae campana dentro, aunque el
     * comentario antiguo decía que sí.
     */
    private static final String[] IGLESIAS = {
            "village/plains/houses/plains_temple_4",
    };

    /** Dónde va la iglesia de la aldea (repartida también con la aldea agrandada). */
    private static BlockPos baseDeIglesia(BlockPos center) {
        return trazado(center, 4);
    }

    /**
     * <b>Puesto de trabajo de un herrero</b> de la aldea: el <b>muelle de afilar</b> (el del herrero de ARMAS) o la
     * <b>mesa de herrería</b> (el de HERRAMIENTAS). Lo usa su goal para ir a trabajar a su sitio.
     *
     * @param armas {@code true} para el muelle del herrero de armas, {@code false} para la mesa del de herramientas
     */
    @Nullable
    public static BlockPos puestoDeHerreria(ServerLevel level, BlockPos center, boolean armas) {
        int nivel = cotaDeLaPlaza(level, center);
        if (nivel <= level.getMinBuildHeight() + 1) {
            return null;
        }
        return buscarBloque(level, baseDeHerreria(center), nivel, armas ? Blocks.GRINDSTONE : Blocks.SMITHING_TABLE);
    }

    /**
     * <b>Herrería</b> de la aldea: la <b>casa del herrero del propio juego</b> ({@code plains_weaponsmith_1}), con su
     * fragua (lava), su muelle de afilar ({@code grindstone}, que es el <b>puesto de trabajo</b> del herrero de armas)
     * y su arca. Se pone en el hueco libre del norte, entre la iglesia y la casa grande (único solar de 9x11 que queda
     * dentro del recinto: comprobado con las huellas de todo lo demás).
     * <p>
     * Los dos herreros de la aldea viven aquí: la plantilla trae el muelle del de ARMAS y a la vuelta se le pone
     * dentro la <b>mesa de herrería</b> ({@code smithing_table}) del de HERRAMIENTAS, que es su puesto de trabajo.
     * Sin puesto de trabajo los aldeanos no pueden reclamarlo y el juego les acaba borrando el oficio.
     */
    private static final String[] HERRERIAS = {
            "village/plains/houses/plains_weaponsmith_1",
    };

    private static BlockPos baseDeHerreria(BlockPos center) {
        return trazado(center, 5);
    }

    /**
     * Asegura la <b>herrería</b> de la aldea (y los puestos de trabajo de los dos herreros). Es <b>idempotente</b>:
     * si la plantilla ya está puesta (se busca su muelle de afilar) no toca nada; si falta la mesa de herrería, la
     * coloca. La usan la generación de aldeas nuevas y la migración de las ya construidas.
     */
    public static void asegurarHerreria(ServerLevel level, BlockPos center) {
        int nivel = cotaDeLaPlaza(level, center);
        if (nivel <= level.getMinBuildHeight() + 1) {
            return;
        }
        BlockPos base = baseDeHerreria(center);
        if (buscarBloque(level, base, nivel, Blocks.GRINDSTONE) == null) {
            // OJO: colocar la plantilla borra lo que haya en su solar (queda en el log con un WARN por casa).
            BlockPos puerta = placeVanillaHouse(level, base, HERRERIAS[0], nivel);
            // Camino hasta su puerta, como a las casas.
            if (puerta != null) {
                line(level, center, doorApproach(level, puerta));
            }
            DevilRpg.LOGGER.info("[Village] Aldea en {}: herreria construida en {} (puerta {})", center, base, puerta);
        }
        if (buscarBloque(level, base, nivel, Blocks.SMITHING_TABLE) == null) {
            puestoDeTrabajo(level, base, nivel, Blocks.SMITHING_TABLE);
            DevilRpg.LOGGER.info("[Village] Aldea en {}: mesa de herreria puesta en el taller", center);
        }
    }

    /**
     * Busca un bloque concreto en el solar de un edificio (para saber si ya está construido o puesto).
     * <p>
     * OJO CON LA Y: la caja se mide desde {@code nivel} (LA COTA), NUNCA desde la Y de la base. La base se construye
     * con la Y del centro del objetivo, que es la del spawn del jugador (101 con la aldea a 75): buscando el muelle
     * a esa altura no se encontraba NUNCA, así que la herrería se volvía a construir cada 10 s y, al destruir su
     * cofre, el juego TIRABA EL BOTÍN al suelo (mecánica vanilla de `Containers.dropContentsOnDestroy`): el jugador
     * veía la herrería "escupiendo" espadas, picos, antorchas y puertas sin razón.
     */
    @Nullable
    private static BlockPos buscarBloque(ServerLevel level, BlockPos base, int nivel, Block bloque) {
        for (BlockPos q : BlockPos.betweenClosed(new BlockPos(base.getX() - 1, nivel - 3, base.getZ() - 1),
                new BlockPos(base.getX() + 12, nivel + 9, base.getZ() + 12))) {
            if (level.getBlockState(q).is(bloque)) {
                return q.immutable();
            }
        }
        return null;
    }

    /**
     * Coloca un <b>puesto de trabajo</b> (mesa de herrería, muelle...) dentro de un edificio: el primer hueco libre
     * con suelo firme y sitio de sobra, para no romper nada de la plantilla.
     */
    private static void puestoDeTrabajo(ServerLevel level, BlockPos base, int nivel, Block puesto) {
        for (int dx = 1; dx <= 10; dx++) {
            for (int dz = 1; dz <= 11; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    BlockPos p = new BlockPos(base.getX() + dx, nivel + dy, base.getZ() + dz);
                    if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()
                            && level.getBlockState(p.below()).isSolid()) {
                        colocar(level, p, puesto.defaultBlockState(), Block.UPDATE_ALL);
                        DevilRpg.LOGGER.debug("[Village] puesto de trabajo {} en {}", puesto, p);
                        return;
                    }
                }
            }
        }
    }

    private static String iglesiaAleatoria(RandomSource random) {
        return IGLESIAS[random.nextInt(IGLESIAS.length)];
    }

    /** ¿Esa plantilla es una iglesia (templo)? Las iglesias no llevan cama: no se les pone ninguna. */
    private static boolean esIglesia(String id) {
        for (String iglesia : IGLESIAS) {
            if (iglesia.equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** ¿Es la herrería? Tampoco es un dormitorio: no se le ponen camas ni puerta extra. */
    private static boolean esHerreria(String id) {
        for (String herreria : HERRERIAS) {
            if (herreria.equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** ¿Es una VIVIENDA (casa)? Solo a las casas se les ponen camas de más y la segunda puerta. */
    private static boolean esVivienda(String id) {
        return !esIglesia(id) && !esHerreria(id);
    }

    /**
     * Sustituye la <b>torre procedural</b> vieja por la <b>iglesia del juego</b> en una aldea ya construida: se
     * limpia el solar (solo lo construido: el terreno se protege) y se coloca el templo, que ya trae campanario.
     */
    public static void actualizarTemplo(ServerLevel level, BlockPos center) {
        BlockPos base = baseDeIglesia(center);
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                int suelo = groundY(level, base.getX() + dx, base.getZ() + dz);
                for (int dy = -2; dy <= 12; dy++) {
                    BlockPos p = new BlockPos(base.getX() + dx, suelo + dy, base.getZ() + dz);
                    BlockState state = level.getBlockState(p);
                    if (state.isAir() || esTerrenoNatural(state)) {
                        continue;
                    }
                    colocar(level, p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        placeVanillaHouse(level, base, iglesiaAleatoria(RandomSource.create(center.asLong())),
                prepararTerreno(level, center));
        DevilRpg.LOGGER.info("[Village] Aldea en {}: torre de vigilancia sustituida por una iglesia", center);
    }

    /**
     * <b>LEGACY — NO USAR.</b> Torre de vigilancia procedural (dos pilares de cobblestone con plataforma). Desde
     * que la aldea usa las construcciones del juego, la sustituye una <b>iglesia</b> de vanilla
     * ({@link #actualizarTemplo}), así que ya no se llama.
     */
    private static void tower(ServerLevel level, BlockPos base) {
        int y = groundY(level, base.getX(), base.getZ());
        int height = 6; // altura útil de la torre
        // Paredes de la torre (3x3, hueco interior).
        for (int i = 0; i < height; i++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    boolean wallTower = Math.abs(x) == 1 || Math.abs(z) == 1;
                    if (wallTower) {
                        colocar(level, new BlockPos(base.getX() + x, y + i, base.getZ() + z), Blocks.COBBLESTONE.defaultBlockState(), 3);
                    } else {
                        colocar(level, new BlockPos(base.getX() + x, y + i, base.getZ() + z), Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
        // Plataforma superior (piso de madera).
        int topY = y + height;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                colocar(level, new BlockPos(base.getX() + x, topY, base.getZ() + z), Blocks.OAK_PLANKS.defaultBlockState(), 3);
            }
        }
        // Almenas (murete) alrededor del borde superior.
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                boolean edge = Math.abs(x) == 1 || Math.abs(z) == 1;
                if (edge && (x + z) % 2 == 0) { // espacios intercalados
                    colocar(level, new BlockPos(base.getX() + x, topY + 1, base.getZ() + z), Blocks.COBBLESTONE.defaultBlockState(), 3);
                }
            }
        }
        // Escalera de acceso por un lateral (sube en espiral simple: una cara).
        for (int i = 0; i < height; i++) {
            colocar(level, new BlockPos(base.getX(), y + i, base.getZ() + 1), Blocks.AIR.defaultBlockState(), 3);
            if (i < 2) {
                colocar(level, new BlockPos(base.getX(), y + i, base.getZ() + 2), Blocks.DIRT.defaultBlockState(), 3);
            }
        }
        colocar(level, new BlockPos(base.getX(), y + 1, base.getZ() + 2), Blocks.OAK_PLANKS.defaultBlockState(), 3);
    }

    /** Lado del kiosco de la plaza (radio): 3 -> plataforma de 7x7 (antes 5x5: el jugador lo quería más grande). */
    private static final int KIOSCO_RADIO = 3;

    /** Radio del kiosco (lo usa también la despensa para su punto de apoyo, que va en el patio de delante). */
    public static int kioscoRadio() {
        return KIOSCO_RADIO;
    }
    /** Altura de los cuatro postes del kiosco sobre la plataforma. */
    private static final int KIOSCO_POSTE = 4;

    /**
     * <b>Kiosco de la plaza</b>: plataforma de piedra con <b>4 salidas</b> (una escalera en el centro de cada lado),
     * cuatro postes, tejado y la <b>campana arriba</b>. Dentro, sobre la plataforma, el <b>cofre doble de la
     * despensa</b>: el centro de la cadena de suministro (allí el granjero guarda el trigo y hornea el pan, y de
     * allí come la aldea).
     * <p>
     * La despensa es un COFRE y no un barril <b>a propósito</b>: el barril es el puesto de trabajo del
     * <b>pescador</b>, así que un aldeano sin oficio lo reclamaba y la aldea acababa con un pescador. El cofre no da
     * oficio a nadie.
     */
    private static void kiosco(ServerLevel level, BlockPos center, int nivel) {
        int r = KIOSCO_RADIO;
        int cx = center.getX();
        int cz = center.getZ();
        // Si había una campana suelta en el centro (aldeas viejas), se quita: la campana va ahora arriba del kiosco.
        for (int dy = -1; dy <= 1; dy++) {
            BlockPos viejo = new BlockPos(cx, nivel + dy, cz);
            if (level.getBlockState(viejo).is(Blocks.BELL)) {
                colocar(level, viejo, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        // Plataforma 5x5 a la cota del pueblo: se anda un bloque por encima de la plaza.
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                colocar(level, new BlockPos(cx + dx, nivel, cz + dz), Blocks.STONE_BRICKS.defaultBlockState(), 3);
            }
        }
        // 4 salidas: una escalera en el centro de cada lado, mirando hacia la plataforma (se sube de un paso).
        colocar(level, new BlockPos(cx - r - 1, nivel, cz), escalera(Direction.EAST), 3);
        colocar(level, new BlockPos(cx + r + 1, nivel, cz), escalera(Direction.WEST), 3);
        colocar(level, new BlockPos(cx, nivel, cz - r - 1), escalera(Direction.SOUTH), 3);
        colocar(level, new BlockPos(cx, nivel, cz + r + 1), escalera(Direction.NORTH), 3);
        // Cuatro postes y el tejado.
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                for (int i = 1; i <= KIOSCO_POSTE; i++) {
                    colocar(level, new BlockPos(cx + sx * r, nivel + i, cz + sz * r),
                            Blocks.STONE_BRICKS.defaultBlockState(), 3);
                }
            }
        }
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                colocar(level, new BlockPos(cx + dx, nivel + KIOSCO_POSTE + 1, cz + dz),
                        Blocks.STONE_BRICKS.defaultBlockState(), 3);
            }
        }
        // La campana va DENTRO del kiosco, sobre la plataforma y al lado del cofre (a la izquierda según se entra).
        colocar(level, new BlockPos(cx - 1, nivel + 1, cz + 1),
                Blocks.BELL.defaultBlockState().setValue(BellBlock.FACING, Direction.SOUTH)
                        .setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR), 3);
        // Un farol colgado del tejado, en el centro: el kiosco queda iluminado de noche.
        colocar(level, new BlockPos(cx, nivel + KIOSCO_POSTE, cz),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 3);
        // OJO: aquí YA NO va el cofre de la despensa. Estuvo en el kiosco hasta la migración 46 y el jugador pidió
        // moverlo a la taberna ("el cofre de la comida ya no tiene sentido que esté en el kiosco central... sería
        // mejor moverlo a la taberna, tomar un cuarto y convertirlo en almacén de comida"): ahora la despensa es el
        // cofre doble de la COCINA de la taberna (ver `TABERNA_DESPENSA` y `VillagePantry`). La migración rescata lo
        // que hubiera en el cofre viejo (a la despensa nueva o al almacén) antes de retirarlo.
    }

    /**
     * Un <b>cofre doble</b> mirando a {@code mira}, con las dos mitades en {@code a} y {@code b} (adyacentes).
     * <p>
     * Las mitades hay que marcarlas <b>a mano</b> (LEFT/RIGHT): al colocar dos cofres con {@code setBlock} no pasa por
     * la colocación de vanilla y sin la marca quedarían dos cofres <b>sueltos</b> (27 casillas cada uno en vez de un
     * cofre doble de 54). La regla de vanilla ({@code ChestBlock.getConnectedDirection}) es: la mitad <b>LEFT</b> tiene
     * a su pareja en {@code mira.getClockWise()} y la <b>RIGHT</b> en {@code mira.getCounterClockWise()}, así que el
     * orden depende de hacia dónde mire el cofre.
     */
    private static void cofreDoble(ServerLevel level, BlockPos a, BlockPos b, Direction mira) {
        boolean aEsIzquierda = b.subtract(a).equals(mira.getClockWise().getNormal());
        colocar(level, a, cofre(aEsIzquierda ? ChestType.LEFT : ChestType.RIGHT, mira), 3);
        colocar(level, b, cofre(aEsIzquierda ? ChestType.RIGHT : ChestType.LEFT, mira), 3);
    }

    /** Cofre mirando a {@code mira}; {@code tipo} marca la mitad (LEFT/RIGHT) para formar un cofre doble. */
    private static BlockState cofre(ChestType tipo, Direction mira) {
        return Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, mira)
                .setValue(ChestBlock.TYPE, tipo);
    }

    // --- LA COCINA DEL PUEBLO (etapa E, en la taberna desde la etapa F) ------------------------------

    /**
     * El <b>puesto del cocinero</b>: el ahumador de la <b>cocina de la taberna</b> (etapa F). Antes estaba en la
     * plataforma del kiosco, al lado de la despensa; desde que hay taberna, el cocinero trabaja en su cocina (la
     * esquina noroeste del comedor) y el pueblo va allí a comer. Su casilla de delante (un bloque al norte) queda
     * libre a propósito: es donde se pone él a cocinar (ver {@code VillagerCookGoal}).
     */
    public static BlockPos puestoDelCocinero(ServerLevel level, BlockPos center) {
        int nivel = cotaDeLaPlaza(level, center);
        BlockPos base = baseDeLaTaberna(center);
        return new BlockPos(base.getX() + TABERNA_COCINA[0], nivel, base.getZ() + TABERNA_COCINA[1]);
    }

    /**
     * Asegura la <b>cocina del pueblo</b>, que desde la etapa F está <b>en la taberna</b>: el ahumador (puesto de
     * trabajo del cocinero), su mesa de trabajo, el caldero y los barriles. Es <b>idempotente</b>: si el ahumador ya
     * está en su sitio, no toca nada.
     * <p>
     * Si la despensa del kiosco todavía tuviera el <b>ahumador viejo</b>, se retira (se convierte en su sitio de
     * siempre: la plataforma del kiosco) para que no queden dos cocinas y el cocinero no se líe.
     */
    public static void asegurarCocina(ServerLevel level, BlockPos center) {
        int nivel = cotaDeLaPlaza(level, center);
        if (nivel <= level.getMinBuildHeight() + 1) {
            return;
        }
        // La cocina va dentro de la taberna: sin taberna no hay cocina.
        asegurarTaberna(level, center);
        if (!tabernaConstruida(level, center)) {
            return;
        }
        // El ahumador viejo del kiosco se retira (una sola vez: si ya no está, no se toca nada).
        BlockPos viejo = new BlockPos(center.getX() + 2, nivel + 1, center.getZ() + 1);
        if (level.getBlockState(viejo).is(Blocks.SMOKER)) {
            colocar(level, viejo, Blocks.STONE_BRICKS.defaultBlockState(), 3);
            colocar(level, new BlockPos(center.getX(), nivel + 1, center.getZ()),
                    Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
            DevilRpg.LOGGER.info("[Village] Aldea en {}: el ahumador viejo del kiosco se retiro (la cocina ya esta"
                    + " en la taberna)", center);
        }
    }

    private static BlockState escalera(Direction hacia) {
        return Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, hacia)
                .setValue(StairBlock.HALF, Half.BOTTOM);
    }

    /** Quita el aire y bloques que queden en la columna por encima de {@code baseY+1} (deja la campana al aire). */
    private static void clearColumnAbove(ServerLevel level, int x, int z, int baseY) {
        for (int yy = baseY + 1; yy <= baseY + 8 && yy < level.getMaxBuildHeight(); yy++) {
            BlockState bs = level.getBlockState(new BlockPos(x, yy, z));
            if (bs.isAir()) continue;
            // Solo limpiar bloques que no sean estructuras (tierra/cesped de la isla o nivelado).
            colocar(level, new BlockPos(x, yy, z), Blocks.AIR.defaultBlockState(), 3);
        }
    }

    /**
     * Coloca faroles distribuidos por la aldea (poste de valla + lanterna encima) en varios puntos, para
     * evitar que los zombies normales aparezcan de noche con la mecánica vanilla (luz).
     */
    private static void torches(ServerLevel level, BlockPos center) {
        int[][] spots = {
                {8, 0, -8},
                // (0,-15) caía DENTRO de la iglesia nueva (x -12..0, z -25..-13): el farol salía en su tejado.
                {5, 0, -12},
                // (12,12) y (-19,8) caían dentro de las parcelas de la granja al agrandarlas a 9x9 (4 carriles por
                // lado): el farol salía plantado entre los cultivos. Se corren a fuera del bancal.
                {22, 0, 10},
                {-13, 0, -6},
                {5, 0, 14},
                {0, 0, 15},
                {-24, 0, 20},
        };
        for (int[] s : spots) {
            // NUNCA dentro de la granja: antes había un farol plantado en medio del trigo (visto en juego).
            if (insideFarm(s[0], s[2])) {
                DevilRpg.LOGGER.debug("[Village] farol en ({}, {}) omitido: cae dentro de la granja", s[0], s[2]);
                continue;
            }
            BlockPos spot = center.offset(s[0], 0, s[2]);
            int y = groundY(level, spot.getX(), spot.getZ());
            // Poste de valla (2 bloques) y lanterna encima.
            colocar(level, new BlockPos(spot.getX(), y, spot.getZ()), Blocks.OAK_FENCE.defaultBlockState(), 3);
            colocar(level, new BlockPos(spot.getX(), y + 1, spot.getZ()), Blocks.OAK_FENCE.defaultBlockState(), 3);
            colocar(level, new BlockPos(spot.getX(), y + 2, spot.getZ()), Blocks.LANTERN.defaultBlockState(), 3);
        }
    }

    /** Spawnea un golem de hierro que defiende la aldea. */
    private static void spawnIronGolem(ServerLevel level, BlockPos pos) {
        // Fijar la Y al suelo real (la isla/terreno) y comprobar que quepa, para que no spawnee bajo la aldea
        // ni se asfixie.
        int y = spawnY(level, pos.getX(), pos.getZ());
        BlockPos posicion = huecoLibre(level, new BlockPos(pos.getX(), y, pos.getZ()));
        IronGolem golem = EntityType.IRON_GOLEM.create(level, null, posicion, MobSpawnType.MOB_SUMMONED, true, true);
        if (golem != null) {
            golem.moveTo(posicion.getX() + 0.5D, posicion.getY(), posicion.getZ() + 0.5D, 0.0F, 0.0F);
            golem.setPersistenceRequired();
            level.addFreshEntity(golem);
            DevilRpg.LOGGER.debug("[Village] golem de hierro en {}", posicion);
        }
    }

    /**
     * Construye la base de la aldea cuando esta cae sobre agua. Cubre TODA el área (hasta donde empieza la
     * valla) con tierra al mismo nivel, y por debajo una estructura de troncos que se estrecha hacia el
     * fondo (como una base flotante). Así no quedan huecos ni abismos entre la superficie y la valla.
     */
    private static void buildFloatingIsland(ServerLevel level, BlockPos center, int radius, int surfaceY) {
        // El suelo de la isla queda A NIVEL del agua (reemplaza la capa superior de agua). El nivel lo decide
        // waterSurfaceForArea (mediana de las columnas con agua), no una sola columna.
        int islandTop = surfaceY;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > radius) continue; // base CIRCULAR (no cuadrada)
                BlockPos top = new BlockPos(center.getX() + x, islandTop, center.getZ() + z);
                int g = groundY(level, top.getX(), top.getZ());
                // Rellenar con tierra TODA la columna hasta islandTop (por debajo del nivel de la isla).
                for (int y = Math.min(g, islandTop); y < islandTop; y++) {
                    colocar(level, new BlockPos(top.getX(), y, top.getZ()), Blocks.DIRT.defaultBlockState(), 3);
                }
                // Si el terreno sobresale por encima de la isla, recortarlo para dejar la superficie plana.
                if (g > islandTop) {
                    for (int y = islandTop + 1; y < g; y++) {
                        colocar(level, new BlockPos(top.getX(), y, top.getZ()), Blocks.AIR.defaultBlockState(), 3);
                    }
                }
                colocar(level, top, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            }
        }
        // Base de apoyo en forma de MONTAÑA (cono circular que se estrecha suavemente hacia abajo), en vez
        // de un cubo cuadrado: usa distancia euclidiana y decrece de a poco, como las islas del terreno.
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > radius) continue;
                for (int depth = 1; depth <= ISLAND_SUPPORT_DEPTH; depth++) {
                    // El radio del cono se estrecha ~1 bloque por nivel (suave, circular).
                    double shrink = depth * 0.9;
                    if (dist > radius - shrink) continue;
                    int y = islandTop - depth;
                    if (y <= level.getMinBuildHeight()) break;
                    // Tierra/piedra como cuerpo de la "montaña", con troncos en el borde (raíces).
                    boolean root = dist > radius - shrink - 1.0;
                    Block block = root ? Blocks.OAK_LOG : (depth <= ISLAND_SUPPORT_DEPTH / 2 ? Blocks.DIRT : Blocks.STONE);
                    colocar(level, new BlockPos(center.getX() + x, y, center.getZ() + z), block.defaultBlockState(), 3);
                }
            }
        }
    }

    /**
     * Casas de aldea del <b>propio juego</b> (plantillas {@code minecraft:village/plains/houses/...}) que se usan
     * como hogares de la aldea: construcciones de verdad, con su interior, su cama y su puesto de trabajo, en vez
     * de las cabañas procedurales de antes. Se eligen de forma determinista por la posición de la aldea.
     */
    private static final String[] VANILLA_HOUSES = {
            "village/plains/houses/plains_small_house_1",
            "village/plains/houses/plains_small_house_2",
            "village/plains/houses/plains_small_house_3",
            "village/plains/houses/plains_small_house_4",
            "village/plains/houses/plains_small_house_5",
            "village/plains/houses/plains_small_house_6",
            "village/plains/houses/plains_small_house_7",
            "village/plains/houses/plains_small_house_8",
            "village/plains/houses/plains_medium_house_1",
            "village/plains/houses/plains_medium_house_2",
    };

    /**
     * Casas <b>grandes</b>: la cuarta casa de la aldea es siempre una de estas, y además se le pone una
     * <b>cama extra</b> dentro (ver {@link #camaExtra}). En vanilla hace falta una cama libre por cría, así que
     * con 4 camas la aldea puede llegar a 4 aldeanos.
     */
    private static final String[] CASAS_GRANDES = {
            "village/plains/houses/plains_medium_house_1",
            "village/plains/houses/plains_medium_house_2",
    };

    private static boolean esCasaGrande(String id) {
        for (String grande : CASAS_GRANDES) {
            if (grande.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static String casaGrandeAleatoria(RandomSource random) {
        return CASAS_GRANDES[random.nextInt(CASAS_GRANDES.length)];
    }

    private static String casaAleatoria(RandomSource random) {
        return VANILLA_HOUSES[random.nextInt(VANILLA_HOUSES.length)];
    }

    /**
     * Pone una <b>cama extra</b> dentro de una casa (para las grandes): busca un hueco libre de dos bloques con
     * suelo firme debajo y coloca la cama ahí. Es lo que permite que la aldea críe más aldeanos.
     */
    private static void camaExtra(ServerLevel level, BlockPos origen, Vec3i tam) {
        for (int dx = 1; dx < tam.getX() - 1; dx++) {
            for (int dz = 1; dz < tam.getZ() - 2; dz++) {
                BlockPos pies = origen.offset(dx, 1, dz);
                BlockPos cabeza = pies.relative(Direction.SOUTH);
                if (level.getBlockState(pies).isAir() && level.getBlockState(cabeza).isAir()
                        && level.getBlockState(pies.below()).isSolid()
                        && level.getBlockState(cabeza.below()).isSolid()) {
                    bed(level, pies);
                    DevilRpg.LOGGER.debug("[Village] Cama extra en {} ({})", pies, tam);
                    return;
                }
            }
        }
    }

    /**
     * Coloca una <b>casa de aldea del propio juego</b> (<code>StructureTemplate</code> de vanilla) y devuelve la
     * posición de su puerta, para que el camino llegue a ella de verdad. Antes de colocarla se despeja su solar
     * (si no, quedarían hojas, tierra o piedra dentro de la casa), después se quitan los bloques técnicos que
     * traen las plantillas (<code>jigsaw</code> y <code>structure_void</code>) y, si la casa no trae cama, se le
     * pone una: los aldeanos necesitan cama para criar.
     */
    private static BlockPos placeVanillaHouse(ServerLevel level, BlockPos base, String id, int nivel) {
        // OJO: en 1.21 getOrCreate devuelve la plantilla directamente (no un Optional).
        StructureTemplate template = level.getStructureManager()
                .getOrCreate(ResourceLocation.withDefaultNamespace(id));
        if (template == null) {
            // No debería pasar (son plantillas del juego): se deja el solar libre y se sigue con la aldea.
            DevilRpg.LOGGER.warn("[Village] No se encontro la casa {}: se deja el solar vacio", id);
            return base.offset(0, 0, -2);
        }
        Vec3i tam = template.getSize();
        // La casa se coloca de forma que SU PUERTA quede a la cota de la aldea (el nivel por el que se anda): así
        // se entra sin escalón y sin quedar hundido. No vale colocar siempre la capa y=0 en `nivel-1`, porque cada
        // plantilla del juego tiene la puerta a una altura distinta (medido leyendo las plantillas):
        //   · casa pequeña y templo 3: base en y=0, puerta en y=1  -> origen en nivel-1
        //   · casa MEDIANA: y=0 es una plataforma de TIERRA de 13x11 y la puerta está en y=2 -> origen en nivel-2.
        //     Con el nivel-1 de antes, esa plataforma de tierra quedaba a la vista como un borde marrón alrededor
        //     de la casa (el jugador lo veía como "una zanja") y el piso/puerta quedaban un bloque por encima del
        //     patio.
        //   · templo 4 (la iglesia con campanario): puerta en y=0 -> origen en nivel. Con el nivel-1 de antes, la
        //     puerta de la iglesia quedaba enterrada medio bloque y la iglesia parecía rota.
        int puertaY = alturaDeLaPuerta(template);
        nivelarHuella(level, base, tam.getX(), tam.getZ(), nivel);
        BlockPos origen = new BlockPos(base.getX(), nivel - puertaY, base.getZ());
        // 1) Solar limpio: fuera todo lo que haya en la huella de la casa (y 4 bloques por encima del tejado).
        // NUNCA se despeja por debajo de la capa de superficie del pueblo (`nivel - 1`): el despeje empezaba en
        // `origen.y`, y en la casa mediana (puerta en y=2) eso son DOS capas de suelo por debajo del suelo del
        // pueblo -> donde la plantilla no pone nada quedaba un hoyo de dos bloques (medido en el guardado:
        // 62=aire, 61=aire, 60=tierra en la caja de la casa mediana).
        int capaMinima = Math.max(origen.getY(), nivel - 1);
        for (int dx = 0; dx < tam.getX(); dx++) {
            for (int dz = 0; dz < tam.getZ(); dz++) {
                for (int y = capaMinima; y < origen.getY() + tam.getY() + 4; y++) {
                    BlockPos p = new BlockPos(origen.getX() + dx, y, origen.getZ() + dz);
                    if (!level.getBlockState(p).isAir()) {
                        colocar(level, p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        // 2) La construcción del juego, tal cual viene.
        template.placeInWorld(level, origen, origen, new StructurePlaceSettings(), level.random, Block.UPDATE_CLIENTS);
        // 3) Devuelve el SUELO DEL PUEBLO a los huecos del solar que la plantilla no ocupa.
        // La caja de la plantilla es mayor que el edificio (y la casa mediana trae además una plataforma de tierra
        // con huecos), así que donde la plantilla no pone nada el terreno quedaba 1 o 2 bloques por debajo del suelo
        // del pueblo: LA ZANJA que se veía alrededor de las casas. Se rellena la columna hasta la capa de superficie
        // (césped arriba, tierra debajo) SOLO si está hueca: nunca se tapa una construcción ni se sube nada por
        // encima del suelo del pueblo.
        int superficie = nivel - 1;
        for (int dx = 0; dx < tam.getX(); dx++) {
            for (int dz = 0; dz < tam.getZ(); dz++) {
                int x = origen.getX() + dx;
                int z = origen.getZ() + dz;
                int tope = Integer.MIN_VALUE;
                for (int y = superficie; y >= superficie - PROFUNDIDAD_SOLAR; y--) {
                    if (level.getBlockState(new BlockPos(x, y, z)).isSolid()) {
                        tope = y;
                        break;
                    }
                }
                if (tope == Integer.MIN_VALUE || tope >= superficie) {
                    continue; // ya está a nivel, o hay una construcción en la capa de superficie
                }
                for (int y = tope + 1; y <= superficie; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!level.getBlockState(p).isAir()) {
                        break;
                    }
                    colocar(level, p, y == superficie ? Blocks.GRASS_BLOCK.defaultBlockState()
                                    : Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        // 4) Limpieza de bloques técnicos, y de paso se busca la puerta y se cuentan las camas.
        BlockPos puerta = null;
        int camas = 0;
        for (int dx = 0; dx < tam.getX(); dx++) {
            for (int dz = 0; dz < tam.getZ(); dz++) {
                for (int dy = 0; dy < tam.getY(); dy++) {
                    BlockPos p = origen.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(p);
                    if (state.is(Blocks.JIGSAW) || state.is(Blocks.STRUCTURE_VOID)) {
                        // Bloque técnico de la plantilla: se sustituye por lo que el propio juego declara para
                        // ese enchufe (ver bloqueTecnicoFinal).
                        colocar(level, p, bloqueTecnicoFinal(level, p), Block.UPDATE_CLIENTS);
                        continue;
                    }
                    if (state.getBlock() instanceof DoorBlock
                            && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
                        puerta = p;
                    }
                    if (state.getBlock() instanceof BedBlock) {
                        camas++;
                    }
                }
            }
        }
        // Cama de respaldo en las CASAS que no traigan ninguna (los aldeanos necesitan cama para criar). Ni a la
        // iglesia ni a la herrería se les pone: no son dormitorios. OJO con la altura: la cama va en la capa de
        // ARRIBA del suelo (dy = 1), que es el nivel por el que se anda dentro de la casa.
        if (camas == 0 && esVivienda(id)) {
            bed(level, origen.offset(tam.getX() / 2, 1, tam.getZ() / 2));
            camas++;
        }
        // CAMAS DE MÁS: las casas se llenan hasta MIN_CAMAS_POR_CASA (los niños duermen aquí, y con más camas la
        // aldea puede crecer más allá de los 4 aldeanos de antes).
        if (esVivienda(id) && camas < camasSegunTamano(tam)) {
            camas += camasExtra(level, origen, tam, camasSegunTamano(tam) - camas, puerta);
        }
        // Las casas GRANDES llevan una cama extra: en vanilla hace falta una cama libre por cría, así que con 4
        // camas la aldea puede crecer hasta 4 aldeanos.
        if (esVivienda(id) && esCasaGrande(id)) {
            camaExtra(level, origen, tam);
        }
        // PUERTA EXTRA: una segunda puerta en la pared de enfrente (la casa se puede cruzar). Solo se pone si esa
        // pared es maciza y hay aire a los dos lados, así no se rompe la plantilla.
        if (puerta != null && esVivienda(id)) {
            puertaExtra(level, origen, tam, puerta);
        }
        // Escalón de entrada: si el suelo de fuera quedó por debajo del piso de la casa, se sube con escaleras
        // pegadas a la puerta (si no, no se puede entrar al edificio).
        if (puerta != null) {
            escalonDeEntrada(level, puerta);
        }
        // Los bloques de la plantilla los coloca placeInWorld, no `colocar`: se apuntan ahora, ya limpios.
        apuntarCaja(level, origen, tam);
        DevilRpg.LOGGER.debug("[Village] Casa {} colocada en {} (puerta {})", id, origen, puerta);
        return puerta != null ? puerta : origen.offset(0, 0, -2);
    }

    /**
     * Y (dentro de la plantilla) de la <b>mitad de abajo de la puerta</b>: es la altura a la que hay que dejar la
     * plantilla para que se entre a ras del suelo de la aldea.
     * <p>
     * Si la plantilla no trae puerta (no debería pasar en las casas de aldea), se devuelve 1, que es lo que usan
     * las casas pequeñas del juego.
     */
    private static int alturaDeLaPuerta(StructureTemplate template) {
        for (StructureTemplate.StructureBlockInfo info
                : template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.OAK_DOOR)) {
            BlockState state = info.state();
            if (state.getBlock() instanceof DoorBlock
                    && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
                return info.pos().getY();
            }
        }
        return 1;
    }

    /**
     * Saca de una casa (caja de ±5 alrededor de su base) a los aldeanos y golems que estén dentro, y los deja en
     * la plaza de la aldea. Se usa antes de rehacer una casa para que nadie quede enterrado por la plantilla nueva.
     * A los jugadores no se les toca.
     */
    private static void sacarVecinosDe(ServerLevel level, BlockPos base, BlockPos center) {
        // Destino: la plaza, pero buscando un hueco libre de verdad (en el centro está la campana, y no queremos
        // dejar a nadie dentro de un bloque).
        BlockPos destino = huecoLibre(level,
                new BlockPos(center.getX(), spawnY(level, center.getX(), center.getZ()), center.getZ()));
        List<LivingEntity> dentro = new ArrayList<>();
        dentro.addAll(level.getEntitiesOfClass(Villager.class, new AABB(base).inflate(5.0D, 9.0D, 5.0D)));
        dentro.addAll(level.getEntitiesOfClass(IronGolem.class, new AABB(base).inflate(5.0D, 9.0D, 5.0D)));
        for (LivingEntity entidad : dentro) {
            entidad.teleportTo(destino.getX() + 0.5D, destino.getY(), destino.getZ() + 0.5D);
            DevilRpg.LOGGER.info("[Village] {} sacado de la casa que se va a rehacer", entidad.getName().getString());
        }
    }

    /**
     * Pone un <b>escalón de entrada</b> delante de una puerta: si el suelo de fuera está por debajo del piso de la
     * casa, se apilan escaleras de roble en la columna de delante (mirando hacia la casa) hasta el nivel del piso.
     * Sin esto, una casa cuyo terreno de alrededor quedó más bajo no se puede entrar.
     */
    private static void escalonDeEntrada(ServerLevel level, BlockPos puerta) {
        BlockState estadoPuerta = level.getBlockState(puerta);
        if (!(estadoPuerta.getBlock() instanceof DoorBlock)) {
            return;
        }
        Direction fuera = estadoPuerta.getValue(DoorBlock.FACING);
        BlockPos exterior = puerta.relative(fuera);
        int piso = puerta.getY() - 1; // el suelo de la casa: la puerta va justo encima
        for (int y = groundY(level, exterior.getX(), exterior.getZ()); y <= piso; y++) {
            colocar(level, new BlockPos(exterior.getX(), y, exterior.getZ()),
                    Blocks.OAK_STAIRS.defaultBlockState()
                            .setValue(StairBlock.FACING, fuera.getOpposite())
                            .setValue(StairBlock.HALF, Half.BOTTOM),
                    Block.UPDATE_ALL);
        }
    }

    /**
     * ¿Ese bloque se descarta al capturar un plano? Fuera el aire (los despejes) y el terreno natural (tierra,
     * hierba, piedra, vegetación…), que no se "repara". <b>Excepción</b>: el agua y la tierra de cultivo de la
     * granja SÍ se conservan, porque las construyó el generador y así el obrero puede reponer la parcela si
     * alguien la pisotea (la tierra de cultivo se convierte en tierra al saltar encima) o si le vacían la acequia.
     * Los cultivos no: esos son cosa del granjero.
     * <p>
     * OJO: esto lo usan <b>los dos</b> caminos, el plano canónico (grabado al construir) y el escaneo del mundo que
     * se hace al migrar una aldea vieja. El escaneo antes saltaba la tierra de cultivo, así que los planos de las
     * aldeas migradas no la tenían y el obrero no reponía las parcelas pisoteadas.
     */
    static boolean seDescartaDelPlano(BlockState state) {
        if (state.is(Blocks.FARMLAND) || state.is(Blocks.WATER)) {
            return false;
        }
        // El MURO de la aldea es de TRONCOS: si se descartaran como si fueran vegetación, no estarían en el plano
        // y el obrero no podría reponer los que rompe un asedio (era justo el bug del jugador: "cuando la aldea se
        // defiende, todas las maderas del muro desaparecen y no vuelven"). La vegetación dentro del recinto no es
        // un problema: el generador la limpia antes de construir, así que cualquier tronco de ahí dentro es el muro.
        if (state.is(BlockTags.LOGS)) {
            return false;
        }
        return state.isAir() || esTerrenoNatural(state);
    }

    /**
     * ¿Es terreno natural? Eso <b>no</b> se apunta en el plano de la aldea: la tierra, la hierba, el agua, la
     * piedra, la arena y la vegetación no se "reparan" (si no, el obrero se pondría a rellenar los hoyos que caves
     * tú, o a replantar árboles). Lo que sí entra son las cosas construidas, incluidas las que van a ras de suelo:
     * los caminos de tierra apisonada, el suelo de las casas, los composteros, la base de la torre...
     * <p>
     * OJO con los <b>MINERALES</b>: entran aquí como terreno. No lo estaban, y al allanar un monte con una veta
     * dentro el recorte se llevaba la piedra de alrededor pero <b>dejaba los minerales FLOTANDO en el aire</b> (visto
     * en juego), y encima los metía en el plano de la aldea como si fueran parte del pueblo (el obrero los
     * "reparaba"). Lo mismo con el resto del subsuelo natural: {@code BASE_STONE_OVERWORLD} (piedra, granito,
     * diorita, andesita, tuff, deepslate...), las tierras, la arena, la terracota, el hielo y la nieve.
     */
    private static boolean esTerrenoNatural(BlockState state) {
        if (!state.getFluidState().isEmpty() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) {
            return true;
        }
        Block block = state.getBlock();
        if (block instanceof CropBlock || block instanceof BushBlock) {
            return true;
        }
        // Vetas del overworld (piedra y deepslate), una por mineral.
        if (state.is(BlockTags.COAL_ORES) || state.is(BlockTags.COPPER_ORES) || state.is(BlockTags.IRON_ORES)
                || state.is(BlockTags.GOLD_ORES) || state.is(BlockTags.REDSTONE_ORES) || state.is(BlockTags.LAPIS_ORES)
                || state.is(BlockTags.DIAMOND_ORES) || state.is(BlockTags.EMERALD_ORES)) {
            return true;
        }
        return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER)
                || state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.TERRACOTTA)
                || state.is(BlockTags.SCULK_REPLACEABLE) || state.is(BlockTags.ICE) || state.is(BlockTags.SNOW)
                || state.is(BlockTags.NYLIUM)
                || block == Blocks.GRASS_BLOCK || block == Blocks.DIRT || block == Blocks.COARSE_DIRT
                || block == Blocks.ROOTED_DIRT || block == Blocks.PODZOL || block == Blocks.MYCELIUM
                || block == Blocks.FARMLAND || block == Blocks.STONE || block == Blocks.DEEPSLATE
                || block == Blocks.GRAVEL || block == Blocks.SAND || block == Blocks.RED_SAND
                || block == Blocks.SANDSTONE || block == Blocks.CLAY || block == Blocks.SNOW_BLOCK
                || block == Blocks.ICE || block == Blocks.PACKED_ICE || block == Blocks.MUD
                || block == Blocks.MOSS_BLOCK || block == Blocks.BEDROCK;
    }

    /**
     * ¿Ese bloque es <b>terreno que puede sobrar</b> al nivelar? Es el terreno natural de verdad (tierra, hierba,
     * piedra, arena, agua…) <b>sin</b> los troncos ni las hojas: esos son el <b>muro de la aldea</b> (o un árbol),
     * y recortarlos con el nivelado era lo que hacía desaparecer las maderas del muro al renivelar una aldea ya
     * construida.
     */
    private static boolean esTerrenoRecortable(BlockState state) {
        return esTerrenoNatural(state) && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES);
    }

    /**
     * Con qué sustituir un bloque técnico de una plantilla una vez colocada a mano.
     * <p>
     * Lo <b>correcto</b> es lo que declara el propio juego: los {@code minecraft:jigsaw} son los "enchufes" con
     * los que vanilla encaja las piezas de la aldea, y cada uno guarda un <b>{@code final_state}</b>: el bloque
     * que el juego pondría en esa celda al hacer la conexión (el del camino, por ejemplo, o aire). Se usa ese,
     * parseándolo con {@link BlockStateParser} porque en 1.21 {@link JigsawBlockEntity#getFinalState()} devuelve
     * el texto del estado, no un {@code BlockState} (el estado puede referirse a bloques aún no cargados).
     * Si el {@code final_state} es aire —o si es un {@code structure_void}, que no tiene enchufe— se copia un
     * vecino real para no dejar un agujero (típicamente en el suelo de la casa).
     */
    private static BlockState bloqueTecnicoFinal(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof JigsawBlockEntity jigsaw) {
            String texto = jigsaw.getFinalState();
            if (texto != null && !texto.isBlank()) {
                try {
                    BlockState finalState = BlockStateParser
                            .parseForBlock(level.holderLookup(Registries.BLOCK), texto, false)
                            .blockState();
                    if (!finalState.isAir()) {
                        return finalState;
                    }
                } catch (CommandSyntaxException e) {
                    DevilRpg.LOGGER.warn("[Village] final_state ilegible '{}' en {}: se rellena con un vecino",
                            texto, pos);
                }
            }
        }
        return rellenoParaTecnico(level, pos);
    }

    /**
     * Último recurso para un bloque técnico sin {@code final_state}: se copia un vecino que sí sea un bloque de
     * verdad, mirando primero los lados (el suelo o la pared que lo rodea) y después arriba y abajo. Si no
     * hubiera ninguno, se deja aire.
     */
    private static BlockState rellenoParaTecnico(ServerLevel level, BlockPos pos) {
        Direction[] orden = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST,
                Direction.UP, Direction.DOWN};
        for (Direction dir : orden) {
            BlockState vecino = level.getBlockState(pos.relative(dir));
            if (!vecino.isAir() && !vecino.is(Blocks.JIGSAW) && !vecino.is(Blocks.STRUCTURE_VOID)
                    && vecino.getFluidState().isEmpty()) {
                return vecino;
            }
        }
        return Blocks.AIR.defaultBlockState();
    }

    /** Camino de tierra apisonada (el de pala) de 2 bloques de ancho entre el centro y cada casa. */
    private static void paths(ServerLevel level, BlockPos center, BlockPos... puertas) {
        for (BlockPos puerta : puertas) {
            line(level, center, doorApproach(level, puerta));
        }
    }

    /**
     * Punto frente a la puerta de una casa: se mira la puerta <b>de verdad</b> (las casas de vanilla traen la
     * suya y no siempre da al norte) y se sale 2 bloques hacia donde ella da. Si esa posición no fuera una
     * puerta, se usa el frente clásico ({@link #FRONT}).
     */
    private static BlockPos doorApproach(ServerLevel level, BlockPos door) {
        BlockState state = level.getBlockState(door);
        Direction fuera = state.getBlock() instanceof DoorBlock ? state.getValue(DoorBlock.FACING) : FRONT;
        return door.relative(fuera, 2);
    }

    /**
     * Dibuja un camino de tierra apisonada de 2 bloques de ancho en el plano XZ entre dos puntos, a ras
     * de suelo (sobre el bloque de superficie). No toca la celda del centro (donde va la campana) y <b>no dibuja
     * nada donde el suelo esté a otra altura</b>: antes, al pasar por encima de una casa, `groundY` devolvía el
     * TEJADO y el camino se pintaba encima del tejado (arrancándole bloques), así que ahora esas columnas se
     * saltan y el camino solo existe donde hay patio al mismo nivel.
     */
    private static void line(ServerLevel level, BlockPos from, BlockPos to) {
        int steps = Math.max(Math.abs(to.getX() - from.getX()), Math.abs(to.getZ() - from.getZ()));
        // El ancho de 2 bloques se aplica en el eje perpendicular a la dirección del camino (en el plano XZ).
        boolean horizontal = Math.abs(to.getX() - from.getX()) >= Math.abs(to.getZ() - from.getZ());
        int widthX = horizontal ? 0 : 1;
        int widthZ = horizontal ? 1 : 0;
        // El nivel del camino se toma de LA COTA DE LA ALDEA (la plaza), NO de `groundY(centro)`: en el centro está
        // el KIOSCO (con su tejado), así que `groundY` devolvía el tejado del kiosco y el camino se pintaba a esa
        // altura, encima de los tejados de las casas (y los aldeanos acababan subidos ahí).
        int nivelCamino = cotaDeLaPlaza(level, from) - 1; // el patio, a la altura del suelo
        for (int i = 0; i <= steps; i++) {
            int x = from.getX() + (int) Math.round((to.getX() - from.getX()) * (i / (double) Math.max(1, steps)));
            int z = from.getZ() + (int) Math.round((to.getZ() - from.getZ()) * (i / (double) Math.max(1, steps)));
            // No dibujar sobre la celda del centro (ahí va la campana).
            if (x == from.getX() && z == from.getZ()) continue;
            for (int w = 0; w <= 1; w++) {
                int px = x + widthX * w;
                int pz = z + widthZ * w;
                // groundY da el bloque transitable (uno sobre el sólido); el camino va SOBRE el bloque
                // sólido de la superficie, un bloque por debajo, para quedar a ras de suelo.
                int y = groundY(level, px, pz) - 1;
                if (Math.abs(y - nivelCamino) > 1) {
                    continue; // ahí hay una construcción (o un desnivel fuerte): el camino no sube por encima
                }
                colocar(level, new BlockPos(px, y, pz), Blocks.DIRT_PATH.defaultBlockState(), 3);
            }
        }
    }

    /**
     * Nivela el terreno del área de la aldea a <b>UNA sola cota</b> (la mediana del área) y devuelve esa cota: todas
     * las construcciones se colocan luego a ese nivel, así <b>no hay zanjas ni casas hundidas</b> (que es lo que
     * pasaba cuando cada casa se nivelaba por su cuenta).
     * <p>
     * Se rellena con tierra lo que esté por debajo y se recorta lo que sobresalga, <b>solo si es terreno natural</b>:
     * así se puede volver a llamar en una aldea ya construida (migración) sin cargarse lo que haya puesto el jugador.
     * <p>
     * OJO: la mediana se saca del propio terreno, así que esto es para terreno <b>limpio</b> (antes de construir).
     * En una aldea ya construida la muestra sale falseada (las casas, y sobre todo sus tejados, cuentan como
     * "suelo": `groundY` sobre un tejado devuelve el tejado) y la mediana sube un bloque; para eso está
     * {@link #prepararTerreno}, que lee la cota de la plaza y nivela a ella.
     */
    public static int levelTerrain(ServerLevel level, BlockPos center, int radius) {
        List<Integer> heights = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                heights.add(groundY(level, center.getX() + x, center.getZ() + z));
            }
        }
        Collections.sort(heights);
        int baseY = heights.get(heights.size() / 2); // mediana
        nivelar(level, center, radius, baseY);
        return baseY;
    }

    /**
     * Deja el terreno del área a la cota {@code baseY} (la que le digan) y añade el talud exterior.
     * <p>
     * Dos protecciones imprescindibles, porque esto se llama también en aldeas <b>ya construidas</b>:
     * <ul>
     *   <li>Al <b>rellenar</b> solo se pone tierra donde hay aire o terreno natural: nunca se tapa una
     *       construcción. El relleno era a ciegas y enterraba los troncos del muro de la aldea (medido en el
     *       guardado del jugador: el muro desaparecía bajo la tierra al renivelar).</li>
     *   <li>Al <b>recortar</b> solo se quita terreno de verdad ({@link #esTerrenoRecortable}): los troncos y las
     *       hojas no son "terreno que sobra", así que el muro no se desmonta al renivelar.</li>
     * </ul>
     */
    private static void nivelar(ServerLevel level, BlockPos center, int radius, int baseY) {
        // ¿Es una aldea DE MAR? Se pregunta ANTES de tocar nada: el relleno de abajo tapa el agua del anillo con
        // tierra, así que preguntándolo después la respuesta sería "no" y los picos de las esquinas se rebajarían
        // (dejando aire que tendría que entrar el agua) en vez de ahogarse como toca. Es el mismo criterio que la
        // orilla seca.
        boolean aldeaDeMar = hayAguaEnElAnillo(level, center, baseY, radius, radius + ORILLA_ANCHO);
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                BlockPos columna = new BlockPos(center.getX() + x, 0, center.getZ() + z);
                int g = groundY(level, center.getX() + x, center.getZ() + z);
                // Rellenar las columnas que estén por debajo del nivel base (sin tapar lo construido). La capa que
                // se pisa va con césped, para que un relleno no se vea como un parche de tierra.
                for (int y = g; y < baseY; y++) {
                    BlockState actual = level.getBlockState(columna.atY(y));
                    if (!actual.isAir() && !esTerrenoRecortable(actual)) {
                        continue; // ni lo construido ni los troncos (muro, casas) se tapan
                    }
                    colocar(level, columna.atY(y),
                            (y == baseY - 1 ? Blocks.GRASS_BLOCK : Blocks.DIRT).defaultBlockState(), 3);
                }
                // Recortar lo que sobresalga del nivel base (solo terreno, nunca el muro ni un árbol).
                // OJO: el bucle CORTA hasta el primer bloque que no sea terreno. `groundY` de una columna con una
                // construcción devuelve SU ALTURA (el tejado), así que sin esta parada el recorte subía por dentro de
                // la casa y se comía todo lo que fuera de un material "de terreno": los paneles de terracota de los
                // muros Tudor desaparecían (lo reportó el jugador: "quedan incompletas las paredes").
                for (int y = baseY; y < g; y++) {
                    BlockState actual = level.getBlockState(columna.atY(y));
                    if (!esTerrenoRecortable(actual)) {
                        break; // aquí empieza lo construido: esta columna ya no se recorta
                    }
                    colocar(level, columna.atY(y), Blocks.AIR.defaultBlockState(), 3);
                }
                ponerCesped(level, columna.getX(), columna.getZ(), baseY);
            }
        }
        // Talud exterior: una pendiente escalonada en el borde para que la aldea parezca una MESETA natural
        // (como el terreno vanilla) en vez de un cubo de paredes verticales. Se le pasa si la aldea es de mar (ver
        // arriba) para que los picos de las esquinas se ahoguen en vez de rebajarse.
        addOuterSlope(level, center, radius, baseY, aldeaDeMar);
        // Y, por último, se TAPAN los huecos del suelo (ver sellarSuelo): con el terreno llano, un barranco o una
        // cueva justo debajo dejan agujeros en la plaza por los que se caen los aldeanos.
        sellarSuelo(level, center, radius, baseY);
    }

    /**
     * Cuántos bloques hacia abajo se rellena como mucho al tapar un hueco del suelo de la aldea (una barranca
     * profunda se tapa entera igual: esto solo evita bajar sin fin en un agujero abierto al vacío).
     */
    private static final int PROFUNDIDAD_TAPADO = 64;

    /**
     * <b>Tapa los huecos del suelo de la aldea.</b>
     * <p>
     * El nivelado deja la aldea llana, pero si justo debajo pasa una <b>barranca, una cueva o una mina</b>, el
     * recorte del terreno abre el techo de la cavidad y en la plaza quedan <b>agujeros</b> por los que se caen los
     * aldeanos (visto en juego). Aquí, en cada columna del recinto cuya capa de suelo esté <b>hueca</b> (aire), se
     * rellena hacia abajo con tierra hasta encontrar suelo firme, y se le devuelve el césped a la capa que se pisa.
     * <p>
     * Solo se tapan las columnas de <b>aire</b>: el agua de la acequia de la granja y la de un charco se dejan como
     * están (y el agua de dentro del recinto la rellena antes el propio nivelado, porque cuenta como terreno).
     */
    private static void sellarSuelo(ServerLevel level, BlockPos center, int radius, int baseY) {
        int tapados = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z > radius * radius) {
                    continue; // solo el recinto (disco), igual que el nivelado
                }
                int px = center.getX() + x;
                int pz = center.getZ() + z;
                if (!level.getBlockState(new BlockPos(px, baseY - 1, pz)).isAir()) {
                    continue; // el suelo está: no hay hueco que tapar
                }
                for (int y = baseY - 1; y > baseY - PROFUNDIDAD_TAPADO && y > level.getMinBuildHeight(); y--) {
                    BlockPos p = new BlockPos(px, y, pz);
                    if (!level.getBlockState(p).isAir()) {
                        break; // ya se llegó a suelo firme
                    }
                    colocar(level, p, (y == baseY - 1 ? Blocks.GRASS_BLOCK : Blocks.DIRT).defaultBlockState(), 3);
                    tapados++;
                }
            }
        }
        if (tapados > 0) {
            DevilRpg.LOGGER.info("[Village] Aldea en {}: tapados {} bloques de huecos del suelo (barrancas/cuevas)",
                    center, tapados);
        }
    }

    /**
     * <b>Cota de una aldea ya construida</b>: se <b>lee</b> de la plaza (el centro, donde no hay casas) y se
     * nivela el terreno a ella. Es la que usan las migraciones de aldeas viejas.
     * <p>
     * Nunca se recalcula la mediana de toda el área (como hace {@link #levelTerrain} en terreno limpio): con la
     * aldea construida esa muestra incluye los <b>tejados</b> (`groundY` sobre una casa devuelve su tejado, no el
     * suelo) y el <b>muro</b>, así que la mediana salía un bloque alta. Ese bloque de más es exactamente lo que
     * producía las casas "arriba por un bloque", las zanjas de un bloque alrededor y el muro enterrado bajo la
     * tierra (todo medido en el guardado del jugador).
     * <p>
     * Si la aldea va <b>sobre agua</b>, la cota es la de la isla (nivel del agua + 1) y <b>no</b> se nivela nada:
     * allanar ahí rellenaría el mar.
     */
    public static int prepararTerreno(ServerLevel level, BlockPos center) {
        int waterLevel = waterSurfaceForArea(level, center, LEVEL_RADIUS + SLOPE_WIDTH);
        if (waterLevel >= 0) {
            return waterLevel + 1;
        }
        int cota = cotaDeLaPlaza(level, center);
        nivelar(level, center, LEVEL_RADIUS, cota);
        return cota;
    }

    /**
     * La cota (nivel por el que se anda) del <b>suelo llano de la plaza</b>: la mediana de {@code groundY} en un
     * disco pequeño alrededor del centro. Es el único trozo de la aldea del que se puede fiar la medida cuando ya
     * hay construcciones, porque en la plaza no hay ninguna (ni casas, ni muro, ni granja).
     * <p>
     * Pública porque la usan también el almacén y los goals de los aldeanos para saber a qué altura está el pueblo
     * (colocar cosas a la Y del centro del objetivo es un error: puede caer en otra capa y quedar flotando).
     */
    public static int cotaDeLaPlaza(ServerLevel level, BlockPos center) {
        List<Integer> alturas = new ArrayList<>();
        int radio = 6;
        for (int x = -radio; x <= radio; x++) {
            for (int z = -radio; z <= radio; z++) {
                if (x * x + z * z > radio * radio) {
                    continue;
                }
                alturas.add(groundY(level, center.getX() + x, center.getZ() + z));
            }
        }
        Collections.sort(alturas);
        return alturas.get(alturas.size() / 2);
    }

    /**
     * Añade un talud (pendiente escalonada) alrededor del área plana de la aldea: cuanto más lejos del
     * borde, más baja el terreno, hasta encontrarse con el terreno natural. Así el borde no es un corte
     * vertical (cubo) sino una meseta con laderas, como las que genera el terreno vanilla.
     */
    private static void addOuterSlope(ServerLevel level, BlockPos center, int innerRadius, int baseY,
                                      boolean aldeaDeMar) {
        int outer = innerRadius + SLOPE_WIDTH;
        for (int x = -outer; x <= outer; x++) {
            for (int z = -outer; z <= outer; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist <= innerRadius || dist > outer) continue;
                double fraction = (dist - innerRadius) / (double) SLOPE_WIDTH;
                int stepsDown = (int) Math.round(fraction * SLOPE_HEIGHT);
                int targetY = baseY - stepsDown;
                int px = center.getX() + x;
                int pz = center.getZ() + z;
                int g = groundY(level, px, pz);
                // Rellenar hasta el nivel del talud si el terreno está por debajo (sin tapar lo construido).
                for (int y = g; y < targetY; y++) {
                    BlockState actual = level.getBlockState(new BlockPos(px, y, pz));
                    if (!actual.isAir() && !esTerrenoRecortable(actual)) {
                        continue;
                    }
                    colocar(level, new BlockPos(px, y, pz),
                            (y == targetY - 1 ? Blocks.GRASS_BLOCK : Blocks.DIRT).defaultBlockState(), 3);
                }
                // Recortar si el terreno natural sobresale por encima del talud (nunca troncos ni construcciones):
                // se para en el primer bloque construido, igual que en `nivelar` (ver el porqué allí).
                for (int y = targetY; y < g; y++) {
                    if (!esTerrenoRecortable(level.getBlockState(new BlockPos(px, y, pz)))) {
                        break;
                    }
                    colocar(level, new BlockPos(px, y, pz), Blocks.AIR.defaultBlockState(), 3);
                }
                // Capa superficial del talud (cesped), salvo que sea agua.
                BlockPos surface = new BlockPos(px, targetY - 1, pz);
                if (!level.getBlockState(surface).is(Blocks.WATER)) {
                    colocar(level, surface, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                }
            }
        }
        // Y, por último, los PICOS DE LAS ESQUINAS (ver el método): la meseta es CUADRADA y el talud es REDONDO, así
        // que las cuatro esquinas se quedaban sobresaliendo del talud, como triángulos de tierra pegados a la isla.
        quitarPicosDeLasEsquinas(level, center, innerRadius, baseY, aldeaDeMar);
    }

    /**
     * <b>Quita los picos de las cuatro esquinas</b> de la meseta: los vio el jugador en su isla y los llamó
     * "triángulos de tierra de cada esquina". <b>Donde hay un pico, tiene que haber agua.</b>
     * <p>
     * El motivo es que el suelo llano de la aldea es un <b>cuadrado</b> ({@code nivelar} allana de {@code -radio} a
     * {@code +radio} en X y en Z) y el talud es un <b>círculo</b> (mide la distancia con raíz). En las diagonales el
     * cuadrado llega a {@code radio * √2} = <b>53,7</b> y el talud solo baja hasta {@code radio + 10} = <b>48</b>, así
     * que a cada esquina le sobraba un <b>triángulo</b> allanado a la cota del pueblo, colgado por encima del mar y con
     * las paredes del corte a la vista (tierra). Medido en el guardado del jugador: en la diagonal, el talud bajaba
     * hasta la Y 58 en los pasos 30-33 y en el 34 ya estaba otra vez a la cota, y de ahí al agua el borde era un
     * corte vertical.
     * <p>
     * Qué se hace con el pico depende de dónde esté la aldea:
     * <ul>
     *   <li><b>Aldea de mar</b> (la del jugador): el pico <b>se quita entero y su sitio lo ocupa el agua</b>. Se baja
     *       hasta el <b>fondo natural</b> (lo primero que no sea relleno del pueblo: la grava, la arena o la piedra
     *       del fondo marino, que sigue ahí debajo tal cual lo dejó el terreno) y se rellena de agua hasta la
     *       superficie del mar, así que la isla queda <b>redonda</b> y el agua llega limpia hasta el talud. Lo pidió
     *       el jugador: <i>"ahí debe haber agua"</i>.</li>
     *   <li><b>Aldea de tierra adentro</b>: no hay mar que poner, así que el pico se <b>rebaja hasta la base del
     *       talud</b> (una terraza baja, con césped) y la meseta queda con la misma pendiente por todos lados.</li>
     * </ul>
     * Solo se toca:
     * <ul>
     *   <li>lo que está <b>dentro del cuadrado</b> que allanó el pueblo ({@code |x|,|z| <= radio}), y</li>
     *   <li>lo que está <b>más allá del talud</b> ({@code dist > radio + SLOPE_WIDTH}), o sea el pico, y</li>
     *   <li>lo que tiene <b>relleno del pueblo encima</b> (césped o tierra a la altura del allanado, o ya rebajado en
     *       una pasada anterior): una <b>loma natural</b>, una duna o una playa de arena <b>no se tocan</b>.</li>
     * </ul>
     */
    private static void quitarPicosDeLasEsquinas(ServerLevel level, BlockPos center, int radius, int baseY,
                                                 boolean aldeaDeMar) {
        int outer = radius + SLOPE_WIDTH;
        int sueloBajo = baseY - SLOPE_HEIGHT;
        int celdas = 0;
        int cubos = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist <= outer) {
                    continue; // dentro del talud no hay pico: el talud ya bajó esa celda
                }
                int px = center.getX() + x;
                int pz = center.getZ() + z;
                // OJO con la convención de `groundY`: devuelve LA CAPA QUE SE PISA (el suelo sólido está en g-1), que
                // es la misma que usa `nivelar`. Comparando `g` con el bloque del suelo, TODAS las celdas se
                // descartaban y el arreglo no hacía NADA (por eso el jugador volvió a ver el triangulito y en el log
                // no salía ni una línea de "picos de las esquinas quitados").
                int g = groundY(level, px, pz) - 1;   // el bloque de arriba de la columna
                if (g > baseY - 1 || g < sueloBajo - 1) {
                    continue; // ni es el allanado del pueblo ni un pico ya rebajado
                }
                BlockState arriba = level.getBlockState(new BlockPos(px, g, pz));
                if (!arriba.is(Blocks.GRASS_BLOCK) && !arriba.is(Blocks.DIRT)) {
                    continue; // arena, grava o piedra a la vista: terreno natural, no un pico del allanado
                }
                if (aldeaDeMar) {
                    cubos += ahogarElPico(level, px, pz, g, baseY, sueloBajo);
                } else {
                    for (int y = sueloBajo - 1; y <= g; y++) {
                        BlockPos p = new BlockPos(px, y, pz);
                        if (esTerrenoRecortable(level.getBlockState(p))) {
                            colocar(level, p, Blocks.AIR.defaultBlockState(), 3);
                            cubos++;
                        }
                    }
                    BlockPos surface = new BlockPos(px, sueloBajo - 2, pz);
                    if (level.getBlockState(surface).is(Blocks.DIRT)) {
                        colocar(level, surface, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                    }
                }
                celdas++;
            }
        }
        if (celdas > 0) {
            DevilRpg.LOGGER.info("[Village] Aldea en {}: picos de las esquinas quitados ({} celdas, {} bloques)"
                    + "{}", center, celdas, cubos,
                    aldeaDeMar ? " — el hueco lo ocupa el agua" : " — rebajados a la base del talud");
        }
    }

    /**
     * Hunde una celda del pico de la esquina hasta el <b>fondo natural</b> y la llena de <b>agua</b> hasta la
     * superficie del mar, para que en ese sitio haya agua como en el resto del mar de al lado.
     * <p>
     * Devuelve cuántos bloques de agua ha puesto. Se hunde <b>siempre</b> que la celda tenga <b>relleno del pueblo</b>
     * encima (césped o tierra a la cota): da igual que el fondo natural esté a un bloque del agua o a veinte, porque el
     * agua rellena hasta la superficie del mar y la celda queda como el mar de al lado. (Antes se saltaba cuando el
     * fondo estaba a menos de tres bloques y esas celdas se quedaban como un <b>triangulito de tierra</b> pegado a la
     * isla: es justo lo que el jugador volvió a ver en la vista desde arriba. Lo que sí se respeta es una <b>playa
     * natural</b>: si la capa de arriba es arena o grava, esa celda no es relleno del pueblo y no se toca.)
     */
    private static int ahogarElPico(ServerLevel level, int px, int pz, int g, int baseY, int sueloBajo) {
        int fondo = fondoBajoElRelleno(level, px, pz, Math.min(g, baseY - 1));
        int puestos = 0;
        // La superficie del mar de una aldea de mar es la capa que se pisa menos uno: el pueblo se nivela a
        // `nivelDelAgua + 1` (ver `prepararTerreno`). Se rellena de agua hasta ahí, como el mar de al lado.
        for (int y = fondo + 1; y <= baseY - 1; y++) {
            BlockPos p = new BlockPos(px, y, pz);
            BlockState actual = level.getBlockState(p);
            if (!esTerrenoRecortable(actual) && !actual.isAir() && !actual.is(Blocks.WATER)) {
                break; // algo construido: hasta aquí
            }
            if (actual.is(Blocks.WATER)) {
                continue;
            }
            colocar(level, p, Blocks.WATER.defaultBlockState(), 3);
            puestos++;
        }
        return puestos;
    }

    /**
     * La capa de <b>fondo natural</b> de una columna: se baja desde la superficie saltando el <b>relleno del
     * pueblo</b> (césped y tierra) y se para en lo primero que no sea relleno (grava, arena, piedra...), que es como
     * estaba el terreno antes de allanar. Se limita el rebaje a {@link #MAX_REBAJE_DE_ESQUINA} bloques por si la
     * columna es todo relleno.
     */
    private static int fondoBajoElRelleno(ServerLevel level, int px, int pz, int arriba) {
        int limite = arriba - MAX_REBAJE_DE_ESQUINA;
        int y = arriba;
        while (y > limite) {
            BlockState s = level.getBlockState(new BlockPos(px, y, pz));
            if (!s.is(Blocks.DIRT) && !s.is(Blocks.GRASS_BLOCK) && !s.is(Blocks.COARSE_DIRT)
                    && !s.is(Blocks.PODZOL) && !s.is(Blocks.ROOTED_DIRT)) {
                break; // fondo natural
            }
            y--;
        }
        return y;
    }

    /** Cuánto se rebaja como mucho el pico de una esquina al buscar el fondo natural (evita bajar sin fin). */
    private static final int MAX_REBAJE_DE_ESQUINA = 24;

    /**
     * Repasa el <b>talud</b> de una aldea ya construida: le quita los picos de las esquinas (ver
     * {@link #quitarPicosDeLasEsquinas}). Es idempotente y solo toca terreno allanado por el pueblo, así que se puede
     * llamar en la migración sin miedo: en una aldea al día no hace nada.
     */
    public static void asegurarTalud(ServerLevel level, BlockPos center) {
        int cota = cotaDeLaPlaza(level, center);
        if (cota <= level.getMinBuildHeight() + 1) {
            return;
        }
        // ¿Aldea de mar? Se pregunta aquí, con el terreno tal y como está (el anillo de la orilla): si hay agua, los
        // picos se ahogan; si no, se rebajan a la base del talud.
        boolean aldeaDeMar = hayAguaEnElAnillo(level, center, cota, LEVEL_RADIUS, LEVEL_RADIUS + ORILLA_ANCHO);
        quitarPicosDeLasEsquinas(level, center, LEVEL_RADIUS, cota, aldeaDeMar);
    }

    /** Hasta dónde se busca la nieve/vegetación colgada por encima de la cota (una montaña alta deja restos arriba). */
    private static final int ALTURA_DE_RESTOS = 56;

    /**
     * Quita los <b>restos que quedan colgando</b> cuando la aldea se genera <b>en una montaña</b> (lo pidió el
     * jugador: "quita también la nieve que se quedó flotando").
     * <p>
     * El nivelado recorta el terreno que sobresale de la cota, pero hay cosas que <b>no cuentan como suelo</b> y se
     * quedan en el aire:
     * <ul>
     *   <li>la <b>nieve polvo</b> ({@code powder_snow}): no bloquea el movimiento, así que {@code groundY} (que usa el
     *       mapa de alturas) <b>no la ve</b> y el recorte para en el bloque de debajo — medido en el guardado del
     *       jugador: <b>469 bloques de nieve polvo</b> flotando dentro del recinto de su aldea de montaña, que es
     *       justo lo que se veía desde arriba;</li>
     *   <li>las <b>capas de nieve</b> y el <b>hielo</b> que quedan sin apoyo;</li>
     *   <li>las <b>plantas</b> (matas, flores, hierba alta) que se quedan colgadas.</li>
     * </ul>
     * Solo se quita lo que está <b>por encima de la cota</b>, <b>dentro del término del pueblo</b> y <b>sin nada
     * debajo</b> (aire): la nieve apoyada en el suelo del pueblo —que en un bioma nevado es lo normal— se queda, y
     * todo lo construido (tejados, segundos pisos, faroles colgados, vallas) no es nieve ni planta, así que no se
     * toca.
     */
    public static int limpiarRestosColgados(ServerLevel level, BlockPos center) {
        int nivel = cotaDeLaPlaza(level, center);
        if (nivel <= level.getMinBuildHeight() + 1) {
            return 0;
        }
        int radio = LEVEL_RADIUS + SLOPE_WIDTH;
        int quitados = 0;
        for (int x = -radio; x <= radio; x++) {
            for (int z = -radio; z <= radio; z++) {
                if (x * x + z * z > radio * radio) {
                    continue; // el término del pueblo, como el resto de las pasadas de terreno
                }
                for (int y = nivel; y <= nivel + ALTURA_DE_RESTOS; y++) {
                    BlockPos p = new BlockPos(center.getX() + x, y, center.getZ() + z);
                    BlockState s = level.getBlockState(p);
                    boolean nieveOHielo = s.is(Blocks.SNOW) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.POWDER_SNOW)
                            || s.is(Blocks.ICE) || s.is(Blocks.PACKED_ICE) || s.is(Blocks.BLUE_ICE);
                    boolean planta = s.getBlock() instanceof BushBlock || s.getBlock() instanceof GrowingPlantBlock;
                    if (!nieveOHielo && !planta) {
                        continue; // lo construido no es ni nieve ni planta: no se toca
                    }
                    if (!level.getBlockState(p.below()).isAir()) {
                        continue; // apoyada: se queda (la nieve del suelo del pueblo, por ejemplo)
                    }
                    colocar(level, p, Blocks.AIR.defaultBlockState(), 3);
                    quitados++;
                }
            }
        }
        if (quitados > 0) {
            DevilRpg.LOGGER.info("[Village] Aldea en {}: {} bloque(s) de nieve/vegetacion retirados de lo alto"
                    + " (quedaban colgados del recorte del terreno)", center, quitados);
        }
        return quitados;
    }

    /**
     * Las celdas del <b>anillo del muro</b>, en orden angular y sin huecos: se muestrea el círculo y se rellenan
     * los saltos entre muestras con pasos cardinales. Lo usan {@link #fence} (construirlo) y
     * {@link #rehacerMuro} (limpiar los restos antes de reconstruirlo).
     */
    private static List<BlockPos> anilloDelMuro(BlockPos center) {
        return anilloDelMuro(center, FENCE_RADIUS);
    }

    /** Las celdas del anillo de un radio concreto (ver {@link #anilloDelMuro(BlockPos)}). */
    private static List<BlockPos> anilloDelMuro(BlockPos center, int r) {
        List<BlockPos> pts = new ArrayList<>();
        int samples = 720;
        for (int a = 0; a <= samples; a++) {
            double ang = (a / (double) samples) * Math.PI * 2.0;
            int x = (int) Math.round(center.getX() + Math.cos(ang) * r);
            int z = (int) Math.round(center.getZ() + Math.sin(ang) * r);
            BlockPos p = new BlockPos(x, 0, z);
            if (pts.isEmpty() || !pts.get(pts.size() - 1).equals(p)) {
                pts.add(p);
            }
        }
        List<BlockPos> ring = new ArrayList<>();
        for (int i = 0; i < pts.size(); i++) {
            fillCardinal(pts.get(i), pts.get((i + 1) % pts.size()), ring);
        }
        return ring;
    }

    /**
     * <b>Rehace el muro</b> de una aldea ya construida (migración): limpia los restos que hayan quedado en la
     * línea del muro (troncos enterrados por un nivelado viejo, columnas sueltas, peldaños) y lo vuelve a levantar
     * entero con {@link #fence}.
     * <p>
     * Hace falta porque el muro no se puede "reparar" bloque a bloque cuando lo que falta <b>no está en el plano</b>:
     * un tronco enterrado o comido por el nivelado dejaba un hueco de aire que ningún plano recuerda. Aquí el muro
     * se reconstruye desde cero, que es determinista (misma línea, mismas entradas), así que la aldea recupera su
     * muro exactamente como lo haría una aldea nueva.
     */
    public static void rehacerMuro(ServerLevel level, BlockPos center) {
        // LA COTA DEL MURO ES LA DE LA ALDEA, no la mediana de `groundY` en el anillo: en el anillo está el MURO
        // VIEJO y los troncos son sólidos, así que `groundY` devolvía su tope y el muro se reconstruía un bloque más
        // alto en CADA migración (el jugador lo vio: ya iba por 6 de alto).
        int baseY = cotaDeLaPlaza(level, center);
        // Se limpia la franja del muro quitando SOLO los restos del muro (troncos, piedra, escaleras, muretes), nunca
        // el terreno. Ojo: los troncos NO se pueden dar por "terreno natural" (eso era lo que dejaba el muro viejo en
        // pie y el nuevo encima).
        //
        // El anillo del trazado ANTIGUO (radio 29) NO se limpia aquí: lo hace `limpiarTrazadoAntiguo` dentro de la
        // migración de casas, ANTES de levantar las casas nuevas. Aquí sería tarde y le arrancaría trozos de pared a
        // las dos casas nuevas que caen sobre ese anillo.
        int quitados = 0;
        for (BlockPos p : anilloDelMuro(center)) {
            for (int y = baseY - 1; y <= baseY + 8; y++) {
                BlockPos q = new BlockPos(p.getX(), y, p.getZ());
                BlockState state = level.getBlockState(q);
                boolean restosDeMuro = state.is(Blocks.OAK_LOG) || state.is(Blocks.COBBLESTONE)
                        || state.is(Blocks.COBBLESTONE_STAIRS) || state.is(Blocks.COBBLESTONE_WALL)
                        || state.is(Blocks.COBBLESTONE_SLAB);
                if (!restosDeMuro) {
                    continue;
                }
                level.setBlock(q, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                quitados++;
            }
        }
        fence(level, center);
        DevilRpg.LOGGER.info("[Village] Aldea en {}: muro reconstruido a la cota {} ({} restos quitados)",
                center, baseY, quitados);
    }

    /**
     * Muro de madera y piedra alrededor de la aldea, más realista: logs horizontales (2 bloques de alto)
     * con columnas verticales de cobblestone cada cierta distancia, y 4 entradas de cobblestone en los
     * puntos cardinales (norte/sur/este/oeste).
     */
    private static void fence(ServerLevel level, BlockPos center) {
        int r = FENCE_RADIUS;
        List<BlockPos> ring = anilloDelMuro(center);

        // Altura del muro: LA COTA DE LA ALDEA (el anillo ya está allanado a esa cota). Antes se sacaba de la mediana
        // de `groundY` en el anillo, y al reconstruir el muro esa medida devolvía el tope del muro viejo: el muro
        // subía un bloque en cada migración.
        int baseY = cotaDeLaPlaza(level, center);
        // Rellenar el suelo del anillo hasta justo debajo de la superficie (sin dejar el bloque de tierra
        // que sobresalía por encima del nivel de la villa). El muro se apoya en el suelo de la aldea.
        for (BlockPos p : ring) {
            int g = groundY(level, p.getX(), p.getZ());
            for (int y = g; y < baseY; y++) {
                colocar(level, new BlockPos(p.getX(), y, p.getZ()), Blocks.DIRT.defaultBlockState(), 3);
            }
        }

        // Recorrer el muro bloque a bloque, detectando columnas y entradas.
        int columnEvery = 4;  // una columna de cobblestone cada 4 bloques de muro
        int idx = 0;
        int n = ring.size();
        for (int i = 0; i < n; i++) {
            BlockPos cur = ring.get(i);
            BlockPos next = ring.get((i + 1) % n);
            // Dirección del tramo: eje horizontal del log (X si la pared corre en X, Z si corre en Z).
            Direction.Axis wallAxis = cur.getX() != next.getX() ? Direction.Axis.X : Direction.Axis.Z;

            // Entradas en los 4 puntos cardinales.
            boolean northEntrance = cur.getZ() == center.getZ() - r && cur.getX() == center.getX();
            boolean southEntrance = cur.getZ() == center.getZ() + r && cur.getX() == center.getX();
            boolean eastEntrance = cur.getX() == center.getX() + r && cur.getZ() == center.getZ();
            boolean westEntrance = cur.getX() == center.getX() - r && cur.getZ() == center.getZ();

            if (northEntrance || southEntrance || eastEntrance || westEntrance) {
                entrance(level, center, cur, r, baseY);
            } else if (idx % columnEvery == 0) {
                column(level, cur, baseY);
            } else {
                wall(level, cur, baseY, wallAxis);
            }
            idx++;
        }
    }

    /** Añade a {@code ring} los bloques de un tramo recto entre dos puntos, con pasos cardinales (sin huecos). */
    private static void fillCardinal(BlockPos from, BlockPos to, List<BlockPos> ring) {
        ring.add(from);
        int x = from.getX();
        int z = from.getZ();
        while (x != to.getX() || z != to.getZ()) {
            if (x != to.getX()) {
                x += Math.signum(to.getX() - x);
            } else if (z != to.getZ()) {
                z += Math.signum(to.getZ() - z);
            }
            ring.add(new BlockPos(x, 0, z));
        }
    }

    /** Bloque de muro: 2 logs horizontales (eje según la pared), apoyados sobre la superficie de la aldea. */
    private static void wall(ServerLevel level, BlockPos p, int baseY, Direction.Axis axis) {
        BlockState log = Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
        // baseY es la superficie transitable; el bloque sólido está en baseY-1. El primer log va en baseY.
        colocar(level, new BlockPos(p.getX(), baseY, p.getZ()), log, 3);
        colocar(level, new BlockPos(p.getX(), baseY + 1, p.getZ()), log, 3);
    }

    /** Columna vertical de cobblestone (3 bloques sobre la superficie) con un pequeño remate. */
    private static void column(ServerLevel level, BlockPos p, int baseY) {
        for (int i = 0; i <= 2; i++) {
            colocar(level, new BlockPos(p.getX(), baseY + i, p.getZ()), Blocks.COBBLESTONE.defaultBlockState(), 3);
        }
        colocar(level, new BlockPos(p.getX(), baseY + 3, p.getZ()), Blocks.COBBLESTONE_STAIRS.defaultBlockState(), 3);
    }

    /**
     * Entrada de cobblestone en un punto cardinal: columna de cobblestone a cada lado, hueco central y
     * dintel de cobblestone encima. El eje de la entrada es perpendicular a la dirección cardinal.
     */
    private static void entrance(ServerLevel level, BlockPos center, BlockPos p, int r, int baseY) {
        // Eje perpendicular a la entrada (si la entrada está en N/S, los lados se reparten en X; si en E/O, en Z).
        boolean northSouth = Math.abs(p.getZ() - center.getZ()) == r;
        int signX = northSouth ? 1 : 0;
        int signZ = northSouth ? 0 : 1;
        for (int i = 0; i <= 2; i++) {
            colocar(level, new BlockPos(p.getX() - signX, baseY + i, p.getZ() - signZ), Blocks.COBBLESTONE.defaultBlockState(), 3);
            colocar(level, new BlockPos(p.getX() + signX, baseY + i, p.getZ() + signZ), Blocks.COBBLESTONE.defaultBlockState(), 3);
        }
        colocar(level, new BlockPos(p.getX(), baseY + 3, p.getZ()), Blocks.COBBLESTONE.defaultBlockState(), 3);
    }

    /** ¿Es un bloque de vegetación que debe limpiarse? */
    private static boolean isVegetation(BlockState state) {
        if (state.isAir()) return false;
        return state.is(BlockTags.LOGS)
                || state.is(BlockTags.LEAVES)
                || state.getBlock() instanceof BushBlock
                || state.getBlock() instanceof GrowingPlantBlock
                || state.getBlock() == Blocks.CACTUS
                || state.getBlock() == Blocks.BAMBOO
                || state.getBlock() == Blocks.BAMBOO_SAPLING
                || state.getBlock() == Blocks.SUGAR_CANE;
    }

    /**
     * <b>Despeja el volumen de la aldea</b> en una aldea NUEVA: dentro del radio, se lleva <b>todo lo que no sea
     * terreno natural</b> (árboles, follaje, flores, pasto, bambú, cañas, pero también <b>minas, mazmorras, ruinas,
     * cofres y cualquier resto de estructura</b> que caiga dentro) barriendo la columna completa desde el bloque más
     * alto (heightmap) hacia abajo.
     * <p>
     * Barrido por columna y no solo la superficie porque el bambú y los tallos nacen varios bloques por debajo. Y de
     * todo el volumen porque lo que no se quita aquí <b>queda dentro del pueblo</b>: los minerales sueltos flotando
     * tras el recorte (visto en juego) y las estructuras del mundo (una mina atravesando la plaza, con sus cofres)
     * acaban además en el PLANO de la aldea, con lo que el obrero las "reparaba" como si fueran del pueblo.
     * <p>
     * Solo se usa al <b>generar</b> una aldea (todavía no hay nada construido): en la migración de una aldea ya
     * construida, el despeje lo hacen las rutinas que solo quitan lo que no es terreno, para no derribar el pueblo.
     */
    private static void despejarVolumen(ServerLevel level, BlockPos center, int radius) {
        int quitados = 0;
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                int topY = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z)).getY();
                // Desde el bloque más alto de la columna hacia abajo ~60 bloques: cubre árboles, bambú y
                // cualquier planta o resto de estructura que esté varios bloques por debajo de la superficie.
                for (int y = topY; y > topY - 60 && y >= level.getMinBuildHeight(); y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    // La vegetación se va siempre (troncos y hojas incluidos, vegetación de por medio); del resto se
                    // respeta SOLO el terreno natural (el agua y la lava también: de esas se encarga el nivelado).
                    if (isVegetation(state) || !esTerrenoNatural(state)) {
                        colocar(level, pos, Blocks.AIR.defaultBlockState(), 3);
                        quitados++;
                    }
                }
            }
        }
        if (quitados > 0) {
            DevilRpg.LOGGER.info("[Village] Aldea en {}: despejados {} bloques del volumen de la aldea", center, quitados);
        }
    }

    /**
     * <b>LEGACY — NO USAR.</b> Cabaña procedural de las primeras versiones de la aldea (3 bloques de alto en el
     * interior, puerta al frente, cama, escaleras y cimientos con pilares).
     * <p>
     * Desde el refinamiento de la Iteración 3 las casas son <b>plantillas del propio juego</b>
     * ({@link #placeVanillaHouse}), así que esta ya no se llama desde ningún sitio: se deja como referencia
     * histórica y para no perder el código, pero <b>no la llames</b> (o tendrías aldeas con cabañas viejas otra
     * vez, que es justo el problema que se arregló). {@code VillageManager} sustituye las que existan con
     * {@link #actualizarCasas}.
     * <p>
     * Cabaña asentada al terreno nivelado con 3 bloques de alto en el interior, puerta al frente, cama de
     * 2 bloques (pie + cabeza), escaleras en la entrada cuando queda alto sobre el suelo, y —si el centro
     * está bajo agua— piso sobre el agua con pilares de valla que bajan al menos 3 bloques.
     */
    private static BlockPos hut(ServerLevel level, BlockPos base) {
        // 1) Suelo de la cabaña = el del centro (ya nivelado), para no apilar tierra hasta un máximo.
        int floorY = groundY(level, base.getX(), base.getZ());
        // 2) Si el centro está bajo agua, subir el piso sobre la superficie y sostener la casa con pilares.
        int waterSurface = waterSurface(level, base.getX(), base.getZ());
        boolean overWater = waterSurface > floorY;
        if (overWater) {
            floorY = waterSurface + 1;
        }

        // 3) Rellenar columnas bajas hasta floorY; si está sobre agua, los pilares sostienen desde abajo.
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                int g = groundY(level, base.getX() + x, base.getZ() + z);
                if (!overWater) {
                    for (int y = g; y < floorY; y++) {
                        colocar(level, new BlockPos(base.getX() + x, y, base.getZ() + z), Blocks.DIRT.defaultBlockState(), 3);
                    }
                }
            }
        }
        // 4) Pilares de valla (≥3 bloques bajo el agua) que terminan en un bloque de madera, bajo cada esquina.
        if (overWater) {
            int seabed = groundY(level, base.getX(), base.getZ());
            for (int x = -2; x <= 2; x += 4) {
                for (int z = -2; z <= 2; z += 4) {
                    pillar(level, base.getX() + x, base.getZ() + z, floorY, seabed);
                }
            }
        }
        // 5) Paredes: solo el perímetro se rellena; el interior queda vacío con 3 bloques de alto.
        //    El hueco de la puerta (x==0, z==-2) está en los dos niveles inferiores del frente.
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                boolean perimeter = Math.abs(x) == 2 || Math.abs(z) == 2;
                boolean doorColumn = x == 0 && z == -2;
                for (int lift = 0; lift <= 2; lift++) {
                    int y = floorY + lift;
                    boolean hole = doorColumn && lift <= 1;
                    BlockState state = (perimeter && !hole)
                            ? Blocks.OAK_PLANKS.defaultBlockState()
                            : Blocks.AIR.defaultBlockState();
                    colocar(level, new BlockPos(base.getX() + x, y, base.getZ() + z), state, 3);
                }
            }
        }
        // Techo (nivel 4 = floorY+3), cubriendo todo el hueco.
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                colocar(level, new BlockPos(base.getX() + x, floorY + 3, base.getZ() + z), Blocks.SPRUCE_PLANKS.defaultBlockState(), 3);
            }
        }
        // 6) Puerta en el frente (z=-2, mirando hacia afuera), cama de 2 bloques (pie + cabeza), y escaleras.
        BlockPos doorBottom = new BlockPos(base.getX(), floorY, base.getZ() - 2);
        door(level, doorBottom);
        bed(level, new BlockPos(base.getX(), floorY, base.getZ()));
        entranceStairs(level, doorBottom);
        return new BlockPos(base.getX(), floorY, base.getZ());
    }

    /** Coloca una cama completa (pie + cabeza) mirando hacia el sur (dentro de la cabaña). */
    private static void bed(ServerLevel level, BlockPos footPos) {
        bed(level, footPos, Direction.SOUTH);
    }

    /**
     * Coloca una cama completa (pie en {@code footPos} y cabecera un bloque hacia {@code facing}, que es donde va
     * la almohada). La orientación se pasa porque la barraca tiene camas en dos paredes opuestas y todas con la
     * cabecera contra la pared.
     */
    private static void bed(ServerLevel level, BlockPos footPos, Direction facing) {
        BlockState foot = Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.FACING, facing).setValue(BedBlock.PART, BedPart.FOOT);
        BlockState head = Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.FACING, facing).setValue(BedBlock.PART, BedPart.HEAD);
        colocar(level, footPos, foot, 3);
        colocar(level, footPos.relative(facing), head, 3);
    }

    /** Pilar de vallas que baja desde el piso hasta el fondo marino, ≥3 bloques bajo el agua, terminando en madera. */
    private static void pillar(ServerLevel level, int x, int z, int topY, int seabed) {
        int depth = Math.max(3, topY - seabed); // al menos 3 bloques bajo el agua
        int bottom = topY - depth;
        if (bottom < seabed) bottom = seabed;
        for (int y = bottom; y < topY; y++) {
            colocar(level, new BlockPos(x, y, z), Blocks.OAK_FENCE.defaultBlockState(), 3);
        }
        // Bloque de madera como base del pilar.
        colocar(level, new BlockPos(x, bottom, z), Blocks.OAK_LOG.defaultBlockState(), 3);
    }

    /**
     * Escaleras de roble frente a la puerta cuando el suelo exterior está más de 2 bloques por debajo del
     * piso. Se generan en la dirección de la puerta (hacia afuera, {@link VillageGenerator#FRONT}) y
     * ascienden hacia la entrada, con los escalones orientados en la dirección correcta.
     */
    private static void entranceStairs(ServerLevel level, BlockPos doorBottom) {
        int sx = doorBottom.getX() + FRONT.getStepX();
        int sz = doorBottom.getZ() + FRONT.getStepZ();
        int outsideGround = groundY(level, sx, sz);
        int rise = doorBottom.getY() - outsideGround;
        if (rise < 2) return;
        // Escalones subiendo hacia la puerta: el más alto queda pegado a la entrada y orientado a la puerta.
        for (int i = 0; i < rise; i++) {
            BlockPos stairPos = new BlockPos(
                    doorBottom.getX() + FRONT.getStepX() * (1 + i),
                    doorBottom.getY() - 1 - i,
                    doorBottom.getZ() + FRONT.getStepZ() * (1 + i));
            colocar(level, stairPos, Blocks.OAK_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, FRONT.getOpposite()), 3);
        }
    }

    /**
     * Altura (Y) de la superficie del agua en una columna: el bloque de agua más alto donde encuentra
     * agua sobre un bloque sólido. Devuelve {@code -1} si no hay agua (tierra firme).
     */
    public static int waterSurface(ServerLevel level, int x, int z) {
        int y = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z)).getY();
        for (int yy = y; yy > y - 48; yy--) {
            BlockState bs = level.getBlockState(new BlockPos(x, yy, z));
            if (bs.getBlock() == Blocks.WATER) {
                return yy;
            }
            if (bs.isSolid() && bs.getBlock() != Blocks.WATER && bs.getBlock() != Blocks.LAVA) {
                return -1; // bloque sólido por encima del agua -> tierra firme
            }
        }
        return -1;
    }

    private static void door(ServerLevel level, BlockPos pos) {
        colocar(level, pos, Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, FRONT).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 3);
        colocar(level, pos.above(), Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, FRONT).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
    }

    private static void spawnVillager(ServerLevel level, BlockPos pos, VillagerProfession profession, boolean baby) {
        // OJO: NO se usa la Y que nos pasan (la del centro de la aldea). El terreno nivelado puede quedar a otra
        // altura en esta columna (el centro es una columna suelta y las cabañas y caminos ya usan groundY por
        // columna), así que un aldeano colocado a la Y del centro quedaba ENTERRADO: se asfixiaba y moría en
        // ~10 s (1 de daño cada 10 ticks x 20 de vida) y al llegar a la aldea no había ningún aldeano.
        BlockPos posicion = huecoLibre(level, new BlockPos(pos.getX(), groundY(level, pos.getX(), pos.getZ()), pos.getZ()));
        Villager villager = EntityType.VILLAGER.create(level, null, posicion, MobSpawnType.MOB_SUMMONED, true, true);
        if (villager != null) {
            villager.setVillagerData(villager.getVillagerData().setProfession(profession));
            // SIN ESTO LA PROFESIÓN SE PIERDE: el cerebro vanilla trae el comportamiento `ResetProfession`, que
            // devuelve al aldeano a SIN OFICIO cuando no tiene `JOB_SITE` en el cerebro, su XP es 0 y su nivel es 1.
            // Nuestros aldeanos se nombran por código (no reclaman un puesto de trabajo del juego), así que a los
            // pocos segundos TODOS volvían a `none`: la aldea se quedaba SIN GRANJERO (nadie cosechaba, nadie
            // horneaba pan y la despensa nunca se llenaba: la aldea pasaba hambre con la huerta llena) y sin
            // herreros. Con 1 de XP la condición `getVillagerXp() == 0` ya no se cumple y la profesión se mantiene.
            // (Medido en el guardado del jugador: aldea 10 con 4 aldeanos `none` + 1 holgazán a los 38 s de nacer.)
            villager.setVillagerXp(1);
            if (baby) {
                // Los que llegan para repoblar una aldea debilitada nacen CRÍAS y crecen solos (vanilla).
                villager.setBaby(true);
            }
            villager.moveTo(posicion.getX() + 0.5D, posicion.getY(), posicion.getZ() + 0.5D, 0.0F, 0.0F);
            villager.setPersistenceRequired();
            level.addFreshEntity(villager);
            DevilRpg.LOGGER.debug("[Village] aldeano {} en {} (bebe: {})", profession, posicion, baby);
        }
    }

    /**
     * Primer hueco de 2 bloques de alto (pies y cabeza libres) desde {@code pos} hacia arriba. Es la red de
     * seguridad para que ninguna entidad de la aldea aparezca dentro de un bloque y se asfixie.
     */
    public static BlockPos huecoLibre(ServerLevel level, BlockPos pos) {
        BlockPos p = pos;
        for (int i = 0; i < 8; i++) {
            if (level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                    && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()) {
                return p;
            }
            p = p.above();
        }
        return pos;
    }

    /**
     * Los sitios fijos de aldeano de la aldea (uno por profesión), relativos al centro. Son <b>7</b> puestos
     * (granjero, dos herreros, clérigo, el holgazán recolector, el ganadero del corral y el cocinero de la cocina).
     * <p>
     * Van <b>repartidos en un anillo</b> a unos 24-27 bloques de la plaza, entre el kiosco (radio 3) y los solares
     * nuevos, que con el muro a 62 empiezan a 33-38: siempre en patio abierto y sin caer dentro de una casa, del
     * almacén ni de las parcelas de la granja. (Con el trazado de 36 el anillo estaba a 13-15; al crecer la aldea se
     * ha llevado al doble para que el centro no quede apelotonado.)
     * <p>
     * Los dos últimos puestos <b>no</b> van en ese anillo: el ganadero vive en el corral y el cocinero en la plaza.
     * Ninguno de los dos puede caer bajo un tejado (el del cobertizo del corral o el del kiosco): {@code groundY}
     * devolvería la altura del TEJADO y el aldeano aparecería <b>encima</b> de él.
     */
    private static final BlockPos[] VILLAGER_SPOTS = {
            new BlockPos(-22, 0, -14), new BlockPos(21, 0, -14), new BlockPos(-7, 0, 22),
            new BlockPos(26, 0, 5), new BlockPos(7, 0, 22),
            // El GANADERO vive en su corral (a 47 del centro, dentro del corral —que ahora está DENTRO de la muralla—
            // y fuera del cobertizo: si el punto cayera bajo su tejado, `groundY` devolvería la altura del TEJADO y
            // el aldeano aparecería encima de él).
            new BlockPos(47, 0, 0),
            // El COCINERO (etapa E) junto a la plaza; desde la taberna (etapa F) vive en su cocina.
            new BlockPos(6, 0, 6),
            // El SEGUNDO GRANJERO (etapa F, lo pidió el jugador: "una 3ª parcela con su granjero porque hay poca
            // comida"): entre los dos bancales del sur, en patio abierto.
            new BlockPos(-22, 0, 30),
            // El PESCADOR (etapa G): en patio abierto al norte de su pesquera (el lago está al sur, en (20,44)).
            new BlockPos(20, 0, 30)
    };
    /**
     * Oficios de la aldea, en el orden en que se ocupan los sitios:
     * <ol>
     *   <li><b>Granjero</b>: cultiva, cosecha, fertiliza y hornea el pan en la despensa. Desde la etapa F hay
     *       <b>dos</b> (uno por bancal de los tres, que con uno la huerta no daba para el pueblo).</li>
     *   <li><b>Herrero de armas</b> y <b>clérigo</b>: los oficios "de oficio" de la aldea.</li>
     *   <li><b>Herrero de herramientas</b>.</li>
     *   <li><b>Holgazán</b> (nitwit) = el <b>RECOLECTOR</b>: no tiene oficio propio a propósito, así no reclama
     *       ningún puesto de trabajo y se dedica <b>solo</b> a recoger cosas del pueblo y guardarlas en el almacén.
     *       Antes esto lo hacía el constructor y se pasaba el día recolectando en vez de reparar.</li>
     *   <li><b>Pastor</b> = el <b>GANADERO</b> de la granja anexa (etapa D): vive en el corral de fuera de la valla,
     *       cría a los animales y baja la carne y la lana al almacén.</li>
     *   <li><b>Carnicero</b> = el <b>COCINERO</b> de la aldea (etapa E): cocina en el ahumador la carne cruda y las
     *       patatas que le llegan (crudo = 2 puntos de comida, cocinado = 4) y desde la etapa F lo hace en la
     *       <b>taberna</b>, que es donde come el pueblo.</li>
     * </ol>
     */
    private static final VillagerProfession[] VILLAGER_SPECIALTIES = {
            VillagerProfession.FARMER, VillagerProfession.WEAPONSMITH, VillagerProfession.CLERIC,
            VillagerProfession.TOOLSMITH, VillagerProfession.NITWIT, VillagerProfession.SHEPHERD,
            VillagerProfession.BUTCHER, VillagerProfession.FARMER, VillagerProfession.FISHERMAN
    };

    /**
     * ¿Ese oficio es uno de los del <b>pueblo</b>? Los de fuera (pescador, bibliotecario, cartógrafo, albañil...) no
     * se usan: el pueblo reparte <b>sus</b> puestos, y un aldeano que tome otro oficio vuelve al reparto (ver
     * {@code VillageManager.reponerProfesiones}).
     * <p>
     * Importa porque en vanilla cada <b>bloque de puesto de trabajo</b> da su oficio: el <b>barril</b> es del
     * <b>pescador</b> y el atril del bibliotecario, así que una cría que creciera al lado de un barril suelto (los de
     * la taberna, sin ir más lejos) se habría vuelto pescador. Hasta que el pescador tenga su edificio y su lago
     * (etapa siguiente), el pueblo no usa barriles: las pipas de cerveza son de madera con corteza.
     */
    public static boolean esOficioDelPueblo(VillagerProfession profesion) {
        for (VillagerProfession oficio : VILLAGER_SPECIALTIES) {
            if (oficio == profesion) {
                return true;
            }
        }
        return false;
    }

    /**
     * Cuántos <b>puestos fijos</b> tiene una aldea: uno por sitio de {@link #VILLAGER_SPOTS}. Lo usa el gestor como
     * <b>tope de crecimiento</b>: la aldea crece hasta cubrir sus puestos (6 desde la etapa D, con el ganadero) y, a
     * partir de ahí, los que nacen son gente de sobra (la milicia).
     */
    public static int puestosDelPueblo() {
        return VILLAGER_SPOTS.length;
    }

    /**
     * El sitio (slot) del <b>primer puesto que le falta</b> a la aldea: si no hay ningún aldeano vivo con ese
     * oficio, devuelve su sitio para reponerlo. Devuelve {@code -1} si están todas cubiertas.
     * <p>
     * Se usa al repoblar: antes se reponía "el sitio siguiente" (el número de aldeanos vivos), así que si mataban al
     * recolector (último sitio) y quedaban 4 aldeanos, el nuevo salía con el oficio del sitio 4… o podía repetir un
     * oficio y dejar la aldea sin el que de verdad faltaba.
     * <p>
     * OJO con los oficios REPETIDOS (hay <b>dos granjeros</b> desde la etapa F): se miran por <b>número</b>, no por
     * "está o no está". Con la comprobación vieja, en cuanto había un granjero el segundo puesto se daba por cubierto
     * y el pueblo se quedaba con un solo bancal trabajado para siempre.
     */
    public static int slotDeProfesionFaltante(java.util.Collection<VillagerProfession> vivas) {
        List<VillagerProfession> restantes = new ArrayList<>(vivas);
        for (int i = 0; i < VILLAGER_SPECIALTIES.length; i++) {
            if (restantes.remove(VILLAGER_SPECIALTIES[i])) {
                continue; // ese puesto ya está cubierto (se gasta uno de los vivos)
            }
            return i;
        }
        return -1;
    }

    /**
     * Profesión que le toca al puesto {@code slot} (los puestos de {@link #VILLAGER_SPOTS}). Lo usa la aldea para
     * <b>devolverle el oficio</b> a un aldeano que se quedó sin ninguno (ver {@code VillageManager}).
     */
    public static VillagerProfession profesionDeSlot(int slot) {
        return VILLAGER_SPECIALTIES[Math.floorMod(slot, VILLAGER_SPECIALTIES.length)];
    }

    /** Vuelve a poner los aldeanos y el golem de una aldea ya construida (ver {@code VillageManager}). */
    public static void spawnVillagers(ServerLevel level, BlockPos center) {
        for (int slot = 0; slot < VILLAGER_SPOTS.length; slot++) {
            spawnOneVillager(level, center, slot, false);
        }
        // El golem SOLO si no hay ya uno: al repoblar una aldea cuyo golem sobrevivió, antes aparecía un
        // segundo golem (bug visto en juego). El radio va DERIVADO del tamaño de la aldea (con el recinto
        // agrandado un golem que estuviera junto al muro se quedaba fuera de un radio fijo).
        if (level.getEntitiesOfClass(IronGolem.class, new AABB(center).inflate(FENCE_RADIUS + 20.0D)).isEmpty()) {
            spawnIronGolem(level, center.offset(4, 0, 4));
        } else {
            DevilRpg.LOGGER.debug("[Village] la aldea ya tiene golem: no se duplica");
        }
    }

    /**
     * Repone <b>un</b> aldeano en la aldea, en el sitio que le toque según {@code slot} (los tres sitios fijos,
     * uno por profesión). Lo usa la repoblación escalonada de {@code VillageManager}: una aldea debilitada se
     * recupera de a poco. Con {@code baby} el que llega nace <b>cría</b> y crece sola (vanilla), que es como se
     * ve el relevo generacional.
     */
    public static void spawnOneVillager(ServerLevel level, BlockPos center, int slot, boolean baby) {
        int i = Math.floorMod(slot, VILLAGER_SPOTS.length);
        spawnVillager(level, center.offset(VILLAGER_SPOTS[i]), VILLAGER_SPECIALTIES[i], baby);
    }

    /** Y del suelo sólido (ignora agua/lava) en una columna (x, z). */
    private static int groundY(ServerLevel level, int x, int z) {
        int y = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z)).getY();
        for (int yy = y; yy > y - 48; yy--) {
            BlockState bs = level.getBlockState(new BlockPos(x, yy, z));
            if (bs.isSolid() && bs.getBlock() != Blocks.WATER && bs.getBlock() != Blocks.LAVA) {
                return yy + 1;
            }
        }
        return y;
    }

    /**
     * Y de spawn segura en una columna: si hay suelo sólido (isla o terreno) usa su superficie transitable;
     * si es agua abierta, devuelve justo sobre la superficie del agua para que la entidad no se hunda.
     */
    public static int spawnY(ServerLevel level, int x, int z) {
        int g = groundY(level, x, z);
        // Si debajo de groundY-1 hay agua, es agua abierta -> spawn sobre la superficie del agua.
        BlockState aboveGround = level.getBlockState(new BlockPos(x, g - 1, z));
        if (aboveGround.getBlock() == Blocks.WATER) {
            int surface = waterSurface(level, x, z);
            return Math.max(surface, g) + 1;
        }
        return g;
    }

    // --- Iteración 3: la aldea viva ----------------------------------------------------------------

    /**
     * Hasta cuántos bloques por encima del suelo del pueblo se apunta en el plano (la torre de la iglesia es lo más
     * alto que hay: {@code plains_temple_4} mide 12 de alto).
     */
    private static final int ALTURA_MAXIMA_DEL_PLANO = 12;

    /**
     * Hasta qué altura se buscan los árboles de dentro del recinto al despejarlo: una selva puede tener árboles
     * altísimos, y las hojas de arriba también se quitan.
     */
    private static final int ALTURA_MAXIMA_DE_ARBOL = 32;

    /**
     * Captura el <b>plano</b> de la aldea: todos los bloques construidos dentro del radio de la valla, en una banda
     * de altura medida desde la <b>cota de la aldea</b> (sin vegetación ni cultivos: los árboles y la huerta son cosa
     * del campo y del granjero).
     * Es lo que usa el <b>aldeano obrero</b> ({@code VillagerRepairGoal}) para saber qué falta y volver a
     * ponerlo bloque a bloque, en vez de que el gestor reconstruya la aldea entera de golpe.
     */
    public static VillageSavedData.Blueprint captureBlueprint(ServerLevel level, BlockPos center) {
        List<BlockState> palette = new ArrayList<>();
        Map<BlockState, Integer> indices = new HashMap<>();
        List<Long> posiciones = new ArrayList<>();
        List<Integer> estados = new ArrayList<>();
        int nivel = cotaDeLaPlaza(level, center); // cota del suelo del pueblo (leída de la plaza, no del tejado)
        for (int dx = -FENCE_RADIUS; dx <= FENCE_RADIUS; dx++) {
            for (int dz = -FENCE_RADIUS; dz <= FENCE_RADIUS; dz++) {
                if (dx * dx + dz * dz > FENCE_RADIUS * FENCE_RADIUS) {
                    continue;
                }
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                // La banda se mide desde LA COTA DE LA ALDEA (la plaza), NO desde el suelo de cada columna: `groundY`
                // sobre una casa devuelve su TEJADO, así que el escaneo empezaba por ENCIMA del tejado y el cuerpo
                // de la casa (paredes, suelo, ventanas, postes de tronco) se quedaba FUERA del plano: el obrero no
                // podía reponerlo. Auditoría bloque a bloque contra las plantillas del juego: los planos capturados
                // así cubrían solo el 20-50% de cada construcción.
                // Se empieza 2 bloques por debajo del nivel por el que se anda (suelo de las casas, caminos,
                // composteros y el tronco de abajo del muro) y se sube hasta cubrir la torre de la iglesia.
                for (int y = nivel - 2; y <= nivel + ALTURA_MAXIMA_DEL_PLANO; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = estadoDelPlano(level.getBlockState(pos));
                    if (seDescartaDelPlano(state)) {
                        continue;
                    }
                    Integer indice = indices.get(state);
                    if (indice == null) {
                        indice = palette.size();
                        palette.add(state);
                        indices.put(state, indice);
                    }
                    posiciones.add(pos.asLong());
                    estados.add(indice);
                }
            }
        }
        long[] posicionesArray = new long[posiciones.size()];
        int[] estadosArray = new int[estados.size()];
        for (int i = 0; i < posicionesArray.length; i++) {
            posicionesArray[i] = posiciones.get(i);
            estadosArray[i] = estados.get(i);
        }
        return new VillageSavedData.Blueprint(palette, posicionesArray, estadosArray);
    }

    /**
     * Un bloque del plano <b>tal y como tiene que quedar</b>. Hoy solo cambia una cosa: en una <b>puerta de valla</b>
     * el estado abierto/cerrado es <b>transitorio</b> (la abre el pueblo para pasar y la vuelve a cerrar), así que el
     * plano la guarda y el obrero la repone <b>siempre cerrada</b>.
     * <p>
     * Hace falta de verdad, y está medido: el plano de la aldea 2 del jugador guardaba el portón del corral
     * <b>abierto</b> ({@code open:true}, capturado mientras el fallo lo dejaba así), de modo que cada vez que un asedio
     * se llevaba el portón el obrero lo <b>reconstruía abierto</b> y el rebaño se volvía a salir. Se aplica también al
     * <b>leer</b> el plano (ver {@code VillageManager.blueprintState}), así que las aldeas ya guardadas se arreglan
     * sin migración.
     */
    public static BlockState estadoDelPlano(BlockState state) {
        if (state.getBlock() instanceof FenceGateBlock && state.getValue(FenceGateBlock.OPEN)) {
            return state.setValue(FenceGateBlock.OPEN, false);
        }
        return state;
    }

    /**
     * <b>Granja</b> de la aldea: dos parcelas con su acequia, los cultivos ya crecidos y un compostador (el
     * puesto de trabajo del granjero). Es lo que hace que el aldeano granjero tenga faena y que la aldea
     * produzca la <b>comida</b> que luego se come (ver {@code VillageManager}).
     */
    public static void farm(ServerLevel level, BlockPos center) {
        farm(level, center, prepararTerreno(level, center));
    }

    /**
     * Quita los <b>caminos que quedaron en alto</b> (encima de los tejados) por el bug de la cota del kiosco: los
     * caminos de tierra apisonada solo pueden estar a ras del suelo del pueblo, así que cualquier {@code dirt_path}
     * por encima de la cota es basura del trazado viejo.
     */
    public static void limpiarCaminosFlotantes(ServerLevel level, BlockPos center, int nivel) {
        int quitados = 0;
        for (int dx = -FENCE_RADIUS; dx <= FENCE_RADIUS; dx++) {
            for (int dz = -FENCE_RADIUS; dz <= FENCE_RADIUS; dz++) {
                if (dx * dx + dz * dz > FENCE_RADIUS * FENCE_RADIUS) {
                    continue;
                }
                for (int y = nivel + 2; y <= nivel + 12; y++) {
                    BlockPos p = new BlockPos(center.getX() + dx, y, center.getZ() + dz);
                    if (level.getBlockState(p).is(Blocks.DIRT_PATH)) {
                        colocar(level, p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                        quitados++;
                    }
                }
            }
        }
        if (quitados > 0) {
            DevilRpg.LOGGER.info("[Village] Aldea en {}: quitados {} camino(s) que quedaron encima de los tejados",
                    center, quitados);
        }
    }

    /** Igual, pero a la cota que le digan (la de la aldea, para que la huerta quede al mismo nivel que el resto). */
    public static void farm(ServerLevel level, BlockPos center, int nivel) {
        for (int[] plot : FARM_PLOTS) {
            plot(level, center.offset(plot[0], 0, plot[1]), nivel);
        }
    }

    /**
     * ¿Esa posición (relativa al centro de la aldea) cae dentro de alguna parcela de la granja? Se usa para no
     * plantar faroles encima de los cultivos. Lleva 1 bloque de margen para que el poste no roce la parcela.
     */
    private static boolean insideFarm(int x, int z) {
        for (int[] plot : FARM_PLOTS) {
            if (x >= plot[0] - 1 && x <= plot[0] + PLOT_WIDTH
                    && z >= plot[1] - 1 && z <= plot[1] + PLOT_DEPTH) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parcela de {@code PLOT_WIDTH}×{@code PLOT_DEPTH} (9×5): cuatro filas de cultivos, acequia de agua en medio
     * y compostador al lado.
     * <p>
     * La parcela se nivela a <b>un solo nivel</b> ({@code base} = la columna más alta del terreno): antes cada
     * columna usaba <b>su</b> {@code groundY}, así que en terreno irregular la acequia quedaba un bloque por
     * debajo de la tierra de cultivo y, como la tierra solo se hidrata con agua a su nivel o uno por encima
     * ({@code FarmBlock.isNearWater}), el trigo se <b>secaba</b> (visto en juego). Ahora el agua y la tierra de
     * cultivo van a la <b>misma altura</b> y las columnas bajas se rellenan de tierra hasta ese nivel.
     */
    /**
     * ¿Está ya hecho ese bancal? Se mira el <b>suelo</b>: con varias celdas de <b>tierra de cultivo</b> a la capa de
     * abajo, el bancal está construido y <b>no hay que volver a nivelarlo</b>.
     * <p>
     * Existe por un fallo medido: el <b>nivelado de la huella</b> ({@link #nivelarHuella}) <b>recorta</b> el terreno
     * que sobresale de la cota y, en una parcela en <b>cuesta</b> (una aldea de montaña), ese recorte se llevaba por
     * delante los <b>cultivos ya crecidos</b> de las celdas altas: salían como <b>objetos tirados por toda la
     * parcela</b> y luego se replantaban brotes nuevos (lo volvió a ver el jugador: <i>"¿por qué los vegetales están
     * como item por toda la parcela?"</i>). Medido en su aldea de montaña, justo después de migrar: las tres parcelas
     * con sus 71 cultivos puestos pero casi todos de edad 0-1 y semillas de trigo y de remolacha tiradas por el suelo.
     * <p>
     * La tierra de cultivo <b>sí</b> está en el plano, así que si alguien la pisotea la repone el obrero: no hace
     * falta rehacer el bancal entero (y rehacerlo es lo que rompía la huerta).
     */
    private static boolean bancalHecho(ServerLevel level, BlockPos corner, int nivel) {
        int tierra = 0;
        for (int dx = 0; dx < PLOT_WIDTH; dx++) {
            for (int dz = 0; dz < PLOT_DEPTH; dz++) {
                if (level.getBlockState(new BlockPos(corner.getX() + dx, nivel - 1, corner.getZ() + dz))
                        .is(Blocks.FARMLAND)) {
                    tierra++;
                }
            }
        }
        return tierra >= 8;
    }

    /**
     * ¿Queda algún <b>cultivo vivo</b> en ese bancal? Es la segunda capa de protección de la huerta: aunque el
     * bancal no esté "hecho" (le falte tierra de cultivo en algunas celdas, por ejemplo porque alguien la pisoteó),
     * si hay plantas dentro <b>no se nivela nada</b> —el nivelado recorta el terreno y se llevaría por delante los
     * cultivos de las celdas altas, que acabarían tirados por la parcela como objetos—.
     */
    private static boolean hayCultivos(ServerLevel level, BlockPos corner, int nivel) {
        for (int dx = 0; dx < PLOT_WIDTH; dx++) {
            for (int dz = 0; dz < PLOT_DEPTH; dz++) {
                if (level.getBlockState(new BlockPos(corner.getX() + dx, nivel, corner.getZ() + dz)).getBlock()
                        instanceof CropBlock) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void plot(ServerLevel level, BlockPos corner, int nivel) {
        // SI EL BANCAL YA ESTÁ, NO SE NIVELA NI SE REPLANTA: solo se asegura lo que NO toca los cultivos (el
        // compostero del granjero y la valla con sus faroles). Ver `bancalHecho`.
        if (bancalHecho(level, corner, nivel)) {
            composteroDelBancal(level, corner, nivel);
            cercaDelBancal(level, corner, nivel);
            return;
        }
        Block[] plants = {Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS};
        // SEGUNDA CAPA: si el bancal todavía tiene cultivos (le falta tierra en algunas celdas, pero hay plantas),
        // NO SE NIVELA NADA. El nivelado de la huella RECORTA el terreno que sobresale de la cota y, en una parcela
        // en cuesta (una aldea de montaña), ese recorte se lleva por delante los cultivos de las celdas altas: salen
        // como OBJETOS tirados por toda la parcela (lo que el jugador vio dos veces: "las granjas todavía spawnnean
        // con vegetales como items sobre ellos"). Sin nivelar, lo único que pasa es que se replanta lo que falte.
        boolean conCultivos = hayCultivos(level, corner, nivel);
        int base = conCultivos ? nivel : nivelarHuella(level, corner, PLOT_WIDTH, PLOT_DEPTH, nivel);
        for (int dx = 0; dx < PLOT_WIDTH; dx++) {
            for (int dz = 0; dz < PLOT_DEPTH; dz++) {
                int x = corner.getX() + dx;
                int z = corner.getZ() + dz;
                // NO SE PISA LA COSECHA (lo pidió el jugador: "todavía no hay comida, el granjero no cosecha").
                // Antes esta pasada REPLANTABA las 144 celdas cada vez que corría —y la limpieza de aquí abajo se
                // llevaba por delante los cultivos ya crecidos—, así que cada migración dejaba la huerta entera de
                // brotes y el pueblo se quedaba ~20 minutos sin una sola cosecha, con la despensa vacía (los aldeanos
                // hambrientos ya se la habían comido). Medido en su guardado: 144 cultivos y solo 2 maduros, justo
                // después de una migración. Ahora, si la celda ya tiene un cultivo, se deja tal cual está.
                BlockPos posCultivo = new BlockPos(x, base, z);
                if (level.getBlockState(posCultivo).getBlock() instanceof CropBlock) {
                    continue; // hay algo plantado y creciendo: lo cuida el granjero, no se reinicia
                }
                // 2) Solar LIMPIO: se quita lo que hubiera por encima del suelo (restos de una versión anterior del
                // trazado...). Sin esto quedaban capas viejas y dos composteadores apilados.
                for (int y = base; y <= base + 3; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!level.getBlockState(p).isAir()) {
                        colocar(level, p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
                if (dz == PLOT_WATER_ROW) {
                    // Acequia central: el agua va a ras de la tierra de cultivo y riega las cuatro filas. Se CUBRE
                    // con una losa (que se pisa) por dos motivos medidos en la partida del jugador:
                    //  1) el agua expuesta se CONGELA en biomas helados (habia parcelas con `ice` en el canal): el
                    //     hielo no hidrata (`FarmBlock.isNearWater` usa el fluido) y los cultivos se secaban, asi que
                    //     la aldea pasaba hambre con la despensa vacia;
                    //  2) al pisar el canal los aldeanos se caian dentro y PISOTEABAN la tierra de cultivo de al lado
                    //     (la convertian en tierra), y el obrero no daba abasto a reponerla.
                    colocar(level, new BlockPos(x, base - 1, z), Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
                    colocar(level, new BlockPos(x, base, z),
                            Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                            Block.UPDATE_ALL);
                    continue;
                }
                BlockPos posTierra = new BlockPos(x, base - 1, z);
                if (!level.getBlockState(posTierra).is(Blocks.FARMLAND)) {
                    // La tierra de cultivo solo se pone (o se repone) si falta: volver a ponerla reinicia su humedad.
                    colocar(level, posTierra, Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_ALL);
                }
                BlockState crop = plants[dx % plants.length].defaultBlockState();
                // Cada cultivo tiene SU propiedad de edad y su máximo (el trigo 0-7, el betabel 0-3): se pregunta.
                // Se planta JOVEN (no maduro): una huerta madura de salida es una cosecha servida y cualquier
                // aldeano (o el propio cerebro vanilla del granjero, `HarvestFarmland`) la arrasa en los primeros
                // segundos de llegar el jugador, dejando la parcela pelada y los cultivos tirados por el suelo como
                // items (era lo que se veía al llegar a una aldea nueva). Joven, la aldea ve crecer su huerta y la
                // cosecha la hace el granjero, que SÍ la lleva a la despensa (con la harina de huesos de la remesa
                // crece enseguida).
                crop = cultivoInicial(crop);
                colocar(level, posCultivo, crop, Block.UPDATE_ALL);
            }
        }
        composteroDelBancal(level, corner, nivel);
        // 4) LA VALLA DEL BANCAL, con sus PORTONES y sus FAROLES (lo pidió el jugador: "todas las parcelas deben
        //    estar rodeadas de vallas con varias fence gates y que tengan mucha iluminación para que los plantíos
        //    crezcan rápido"). La luz no es decorativa: un cultivo solo crece con luz 9 o más, así que con faroles
        //    en los postes la huerta sigue creciendo DE NOCHE (sin luz, la mitad del día se pierde).
        cercaDelBancal(level, corner, nivel);
    }

    /**
     * El <b>compostero</b> del bancal (el puesto de trabajo del granjero), a dos bloques de su esquina y fuera de su
     * valla. Se separa del resto del bancal porque es lo único que hay que <b>asegurar</b> en un bancal ya hecho: si
     * se rehiciera el bancal entero, el nivelado se llevaría por delante los cultivos (ver {@link #bancalHecho}).
     */
    private static void composteroDelBancal(ServerLevel level, BlockPos corner, int nivel) {
        // Tres cosas, en este orden (importa):
        //  1) Se quita el compostero VIEJO de la columna ANTES de medir el suelo. Si no, `groundY` cuenta el
        //     compostero como si fuera suelo y el nuevo sube un bloque en cada migración: medido en el guardado
        //     del jugador, el compostero estaba en la capa 64 con el suelo del pueblo en la 62 (flotando).
        //  2) Se mide el suelo ya limpio y se rellena la columna hasta la capa de DEBAJO del compostero (césped
        //     arriba, tierra debajo). Antes el relleno se quedaba una capa corto (`y < nivelCompostero - 1`) y la
        //     limpieza se llevaba por delante el bloque de superficie -> el compostero quedaba FLOTANDO.
        //  3) Se coloca el compostero apoyado en esa capa.
        int base = nivel;
        int compX = corner.getX() - 2; // fuera de la valla del bancal (ver `cercaDelBancal`)
        int compZ = corner.getZ();
        for (int y = nivel - PROFUNDIDAD_SOLAR - 2; y <= nivel + 6; y++) {
            BlockPos p = new BlockPos(compX, y, compZ);
            if (level.getBlockState(p).is(Blocks.COMPOSTER)) {
                colocar(level, p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        int sueloCompostero = groundY(level, compX, compZ);
        int nivelCompostero = Math.max(base, sueloCompostero);
        for (int y = sueloCompostero - 1; y < nivelCompostero; y++) {
            BlockPos p = new BlockPos(compX, y, compZ);
            BlockState actual = level.getBlockState(p);
            if (!actual.isAir() && !esTerrenoRecortable(actual)) {
                continue; // no se tapa nada construido (ni un tronco)
            }
            colocar(level, p, (y == nivelCompostero - 1 ? Blocks.GRASS_BLOCK : Blocks.DIRT).defaultBlockState(),
                    Block.UPDATE_ALL);
        }
        colocar(level, new BlockPos(compX, nivelCompostero, compZ), Blocks.COMPOSTER.defaultBlockState(), Block.UPDATE_ALL);
    }

    /**
     * <b>La valla del bancal</b>: un anillo de valla de roble alrededor de la parcela (1 bloque por fuera de la
     * tierra de cultivo), con <b>cuatro puertas de valla</b> —una en el centro de cada lado— y <b>faroles</b> en las
     * cuatro esquinas y en los cuatro medios lados, que es lo que deja crecer los cultivos de noche.
     * <p>
     * Las puertas las abre el <b>pueblo</b> con {@code VillagerGateGoal} (el juego no deja que un aldeano abra una
     * puerta de valla), así que el granjero entra y sale por ellas igual que por el portón del corral; y los faroles
     * van <b>encima de los postes</b>, que es donde alumbran el bancal entero sin estorbar el paso.
     */
    private static void cercaDelBancal(ServerLevel level, BlockPos corner, int nivel) {
        int x0 = corner.getX() - 1;
        int x1 = corner.getX() + PLOT_WIDTH;
        int z0 = corner.getZ() - 1;
        int z1 = corner.getZ() + PLOT_DEPTH;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean borde = x == x0 || x == x1 || z == z0 || z == z1;
                if (!borde) {
                    continue;
                }
                boolean medio = (x == (x0 + x1) / 2) || (z == (z0 + z1) / 2);
                boolean esquina = (x == x0 || x == x1) && (z == z0 || z == z1);
                if (!esquina && medio) {
                    // Puerta de valla en el centro de cada lado: el granjero entra por donde le pille mejor.
                    boolean ejeX = (z == z0 || z == z1);
                    colocar(level, new BlockPos(x, nivel, z), Blocks.OAK_FENCE_GATE.defaultBlockState()
                            .setValue(FenceGateBlock.FACING, ejeX ? Direction.NORTH : Direction.EAST)
                            .setValue(FenceGateBlock.OPEN, false)
                            .setValue(FenceGateBlock.IN_WALL, false), Block.UPDATE_ALL);
                    continue;
                }
                colocar(level, new BlockPos(x, nivel, z), Blocks.OAK_FENCE.defaultBlockState(), Block.UPDATE_ALL);
                if (esquina || medio) {
                    // Farol en lo alto del poste: alumbra el bancal y los cultivos crecen también de noche.
                    BlockPos alto = new BlockPos(x, nivel + 1, z);
                    if (level.getBlockState(alto).isAir()) {
                        colocar(level, alto, Blocks.LANTERN.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
        }
    }

    /**
     * Las <b>puertas de valla de los bancales</b> (cuatro por parcela), para que {@code VillagerGateGoal} se las abra
     * al granjero igual que el portón del corral: el juego no deja que un aldeano abra una puerta de valla.
     */
    public static List<BlockPos> portonesDeLosBancales(BlockPos center, int nivel) {
        List<BlockPos> portones = new ArrayList<>();
        for (int[] plot : FARM_PLOTS) {
            int x0 = center.getX() + plot[0] - 1;
            int x1 = center.getX() + plot[0] + PLOT_WIDTH;
            int z0 = center.getZ() + plot[1] - 1;
            int z1 = center.getZ() + plot[1] + PLOT_DEPTH;
            portones.add(new BlockPos((x0 + x1) / 2, nivel, z0));
            portones.add(new BlockPos((x0 + x1) / 2, nivel, z1));
            portones.add(new BlockPos(x0, nivel, (z0 + z1) / 2));
            portones.add(new BlockPos(x1, nivel, (z0 + z1) / 2));
        }
        return portones;
    }

    // --- LA TABERNA (etapa F: la posada del pueblo) --------------------------------------------------------------

    /**
     * <b>Medidas de la taberna.</b> La primera taberna (etapa F) era pequeña y sosa —13x12, dos pisos bajos y una
     * escalera pegada a la pared— y el jugador la vio <i>"muy pequeña y muy sencilla"</i>, así que se rehízo con los
     * planos que trajo (el <i>Building map: Inn</i> de dos plantas) y el arte conceptual de la posada con entramado:
     * la planta baja es el <b>comedor</b> (cocina, hogar con chimenea, barra con las pipas, seis mesas y la escalera)
     * y la planta alta la <b>posada</b> (seis cuartos con sus camas alrededor de una galería).
     * <p>
     * La <b>fachada mira al oeste</b> (a la plaza): la puerta va en el centro del muro oeste con su porche y su
     * toldo, y el camino del pueblo llega desde la plaza. La planta alta <b>vuela</b> un bloque sobre la baja (el
     * <i>jetty</i> de las casas con entramado del arte) y el tejado es a dos aguas, muy empinado, con los frontones
     * de cal y madera y la <b>chimenea de ladrillo</b> pegada al muro norte.
     */
    public static final int TABERNA_ANCHO = 19;
    /** Fondo (Z) de la taberna. */
    public static final int TABERNA_FONDO = 15;
    /** Lo que sube el suelo de la posada sobre la cota del pueblo (y lo que sube el alero sobre ese suelo). */
    private static final int TABERNA_PISO2 = 5;
    private static final int TABERNA_ALERO = 5;
    /** Cuánto vuela la planta alta sobre la baja: sus muros van un bloque por fuera. */
    private static final int TABERNA_VUELO = 0;   // los dos pisos, a plomo (el jugador vio los pilares defasados)
    /**
     * La fila (Z) de la <b>cumbrera</b> del tejado (en medio del fondo) y las <b>capas</b> que hacen falta para que
     * las dos vertientes se junten en ella. Viven aquí (y no dentro de {@code tejadoDeLaTaberna}) porque el
     * <b>desván</b> también las necesita: es la forma del tejado la que dice qué celdas son su relleno interior.
     */
    private static final int TABERNA_CUMBRERA = (TABERNA_FONDO - 1) / 2;
    private static final int TABERNA_TEJADO_PASOS = TABERNA_CUMBRERA + TABERNA_VUELO + 1;
    /** La puerta: la principal va en el centro del muro oeste, que es el que mira a la plaza. */
    private static final int TABERNA_PUERTA = 7;
    /**
     * La <b>puerta de servicio</b>, en el muro SUR (la que da a la parte de atrás). El jugador pidió una segunda
     * salida para no tener que cruzar todo el comedor. Va en <b>dx=3</b> y no en dx=4 —que es lo primero que se
     * piensa— porque dx=4 es una columna de <b>poste</b> del entramado (i%4==0) y la puerta le partiría el tronco;
     * en dx=3 la puerta queda con <b>cal</b> encima y la viga arriba, igual que la del oeste. Delante, por dentro
     * (dz=13), el paso está libre: la <b>barra</b> ocupa dx=7..13 en dz=12..13 y la escalera no llega a dz=13.
     */
    private static final int TABERNA_PUERTA_SUR = 3;
    /**
     * La <b>escalera en L</b> de la taberna: <b>dobla en la esquina</b>. El primer tramo baja por el comedor de
     * <b>este a oeste</b> (el pie mira al este, al comedor, con todo el espacio libre delante) y desemboca en una
     * <b>meseta</b> en la esquina suroeste; de ahí el segundo tramo sube <b>de sur a norte</b> pegado al muro oeste
     * hasta la galería de la posada. Es <b>doble</b> (dos bloques de ancho) en los dos tramos y en la meseta.
     * <p>
     * Antes era un tramo recto pegado al muro, con el <b>primer escalón metido en la esquina</b> al lado de una
     * <b>mesa con sillas</b> que lo tapaba: el jugador lo reportó dos veces (<i>"no se puede acceder a la escalera,
     * hay una mesa con sillas que la bloquea"</i>). Ahora el pie está en el comedor, la mesa que estorbaba
     * ({@code 4,11}) ya no se pone, y la escalera se sube doblando en la esquina.
     * <p>
     * <b>OJO con la orientación</b>: en las escaleras del juego la cara alta (por donde se sube) es la que marca
     * {@code FACING}, así que cada tramo mira hacia donde SUBE (el primero al oeste, el segundo al norte).
     */
    private static final int TABERNA_ESCALERA_X = 1;
    private static final int TABERNA_ESCALERA_ANCHO = 2;
    /** La fila (Z) del escalón de arriba, ya en la galería de la posada. */
    private static final int TABERNA_ESCALERA_TOPE_Z = 8;
    /** La fila (Z) donde va la <b>meseta</b> de la esquina (2 de fondo: {@code MESETA_Z} y {@code MESETA_Z + 1}). */
    private static final int TABERNA_ESCALERA_MESETA_Z = 11;
    /** La columna (X) del <b>primer escalón</b> (el pie del tramo de abajo, mirando al comedor). */
    private static final int TABERNA_ESCALERA_PIE_DX = 4;
    /**
     * La <b>escalera del desván</b> (migración 52): una <b>L de dos tramos</b> dentro del <b>cuarto suroeste de la
     * posada</b>, que es el cuarto que se sacrifica para meterla. Antes subía en recto por el carril <b>norte</b> de
     * la galería ({@code dz=7}) y <b>tapaba el corredor</b> por el que se entra a los cuartos del segundo piso: el
     * jugador lo reportó (<i>"al poner la escalera al tercer piso tapaste el corredor que permite que se entre a los
     * diferentes cuartos del 2do piso; mejor sacrifica un cuarto del 2do piso para poner ahí una escalera y libera el
     * corredor"</i>). Ahora la galería queda <b>entera</b> libre —los dos carriles, {@code dz=7} y {@code dz=8}— y se
     * sube por dentro del cuarto.
     * <p>
     * El primer tramo sube <b>de sur a norte</b> por la columna {@code dx=5} (el pie mira al fondo del cuarto, que es
     * por donde se llega a él) y el segundo <b>de este a oeste</b> por la fila {@code dz=10}, pegada al muro del pozo.
     * El tope va en esa fila a propósito: con el último escalón en la fila del alero ({@code dz=13}) el que saliera se
     * daría con el <b>tejado</b> —allí solo hay <b>un</b> bloque libre—, mientras que en {@code dz=10} hay
     * <b>cuatro</b>.
     */
    private static final int DESVAN_ESCALERA_PIE_DX = 5;
    /** La fila (Z) del <b>pie</b> de la escalera del desván, en el fondo del cuarto suroeste. */
    private static final int DESVAN_ESCALERA_PIE_Z = 12;
    /** La fila (Z) del <b>tramo de arriba</b> (el que dobla al oeste), pegada al muro que cierra el pozo. */
    private static final int DESVAN_ESCALERA_Z_ALTO = 10;
    /**
     * Los <b>seis</b> escalones del desván: el desnivel que hay entre el suelo de la posada ({@code y1}) y el del
     * desván ({@code yTecho + 1}), que es {@code TABERNA_PISO2 + 1} bloques. Con escaleras del juego se sube de
     * medio en medio bloque (ver {@code escaleraDeLaTaberna}), así que hacen falta seis.
     */
    private static final int DESVAN_ESCALONES = TABERNA_PISO2 + 1;
    /**
     * La escalera del desván <b>vieja</b> (la de la migración 51), la que <b>tapaba la galería</b>:
     * {@code DESVAN_ESCALONES} escalones en recto por el carril norte ({@code dz=7}) desde {@code dx=3}. Solo la usan
     * los reparadores de la migración 52, que tienen que <b>deshacerla</b> y volver a cerrar el hueco que abrió en
     * las dos capas del forjado (el techo de la posada y la placa del tejado).
     */
    private static final int DESVAN_ESCALERA_VIEJA_PIE_DX = TABERNA_ESCALERA_X + TABERNA_ESCALERA_ANCHO;
    private static final int DESVAN_ESCALERA_VIEJA_Z = TABERNA_ESCALERA_TOPE_Z - 1;
    /** El hogar (con su chimenea), en el muro norte; y el ahumador del cocinero, en la cocina. */
    private static final int[] TABERNA_HOGAR = {9, 0};
    private static final int[] TABERNA_COCINA = {4, 2};
    /**
     * <b>El almacén de comida</b>: el cofre DOBLE de la despensa del pueblo, en la cocina de la taberna (contra su
     * muro norte, las dos mitades en {@code (2,1)} y {@code (3,1)}). Lo pidió el jugador: <i>"el cofre de la comida
     * ya no tiene sentido que esté en el kiosco central... sería mejor moverlo a la taberna, tomar un cuarto y
     * convertirlo en almacén de comida"</i>. Va donde el cocinero cocina y donde el pueblo viene a comer.
     */
    public static final int[] TABERNA_DESPENSA = {2, 1};
    /** Las <b>cinco</b> mesas del comedor, relativas a la esquina de la taberna (la del pie de la escalera se quitó). */
    private static final int[][] TABERNA_MESAS = {{10, 3}, {14, 4}, {16, 7}, {14, 10}, {10, 10}};

    /**
     * Coordenada de la taberna, relativa al centro: al <b>sureste</b>, pegada al almacén (que está en 18,18) y en el
     * cuadrante libre entre el almacén, la casa del este y el corral. Lo pidió el jugador: <i>"una taberna donde
     * trabaje el cocinero y todos vayan a comer ahí... el almacén puede estar a lado de la taberna"</i>.
     */
    private static final int[] TABERNA = {24, 14};

    /** La esquina (base) de la taberna, relativa al centro. */
    public static BlockPos baseDeLaTaberna(BlockPos center) {
        return center.offset(TABERNA[0], 0, TABERNA[1]);
    }

    /** La <b>puerta</b> de la taberna: el centro del muro oeste, que es el que mira a la plaza. */
    public static BlockPos puertaDeLaTaberna(ServerLevel level, BlockPos center) {
        int nivel = cotaDeLaPlaza(level, center);
        BlockPos base = baseDeLaTaberna(center);
        return new BlockPos(base.getX(), nivel, base.getZ() + TABERNA_PUERTA);
    }

    /**
     * Los <b>puntos de la taberna</b> donde come el pueblo: el centro de cada una de las seis mesas, a la cota del
     * pueblo. Los usa el goal de la taberna: cada aldeano va al suyo (repartidos por su UUID) y así no se apilan
     * todos en la misma mesa.
     */
    public static BlockPos[] puntosDeLaTaberna(BlockPos center, int nivel) {
        BlockPos base = baseDeLaTaberna(center);
        BlockPos[] puntos = new BlockPos[TABERNA_MESAS.length];
        for (int i = 0; i < TABERNA_MESAS.length; i++) {
            puntos[i] = new BlockPos(base.getX() + TABERNA_MESAS[i][0], nivel, base.getZ() + TABERNA_MESAS[i][1]);
        }
        return puntos;
    }

    /**
     * ¿Está la taberna construida? Los testigos son sus <b>cuatro postes de esquina</b>, que en esta taberna son de
     * <b>roble oscuro</b> (es lo que la distingue de la primera taberna, de roble claro), <b>y su escalera doble</b>.
     * Con cuatro postes hace falta que se caigan los cuatro para que el pueblo la reconstruya entera (y reconstruirla
     * tira lo que haya dentro).
     * <p>
     * La escalera entra en la prueba desde la migración 45: la taberna de la 44 tiene la escalera de <b>un</b> bloque
     * de ancho (y sin acceso), así que esta prueba falla —a propósito— y se rehace entera con la escalera doble y la
     * chimenea por fuera: el solar se despeja y lo que hubiera en sus cofres se guarda antes en el almacén.
     */
    public static boolean tabernaConstruida(ServerLevel level, BlockPos center) {
        int nivel = cotaDeLaPlaza(level, center);
        BlockPos base = baseDeLaTaberna(center);
        int[][] esquinas = {{0, 0}, {TABERNA_ANCHO - 1, 0}, {0, TABERNA_FONDO - 1}, {TABERNA_ANCHO - 1, TABERNA_FONDO - 1}};
        for (int[] e : esquinas) {
            if (level.getBlockState(new BlockPos(base.getX() + e[0], nivel + 1, base.getZ() + e[1]))
                    .is(Blocks.DARK_OAK_LOG)) {
                // Y tiene que ser la taberna de los DOS PISOS A PLOMO —el pilar de la posada cayendo justo encima
                // del de abajo— y con la ESCALERA DOBLE nueva: si no, es una taberna vieja (la del vuelo de un
                // bloque, con los pilares defasados y la escalera sin acceso) y el pueblo la rehace entera.
                boolean aPlomo = level.getBlockState(new BlockPos(base.getX(), nivel + TABERNA_PISO2, base.getZ()))
                        .is(Blocks.DARK_OAK_LOG);
                // Y la ESCALERA EN L (la del pie en el comedor y la que dobla en la esquina): los dos escalones que
                // la identifican. Una taberna con la escalera vieja (recta y tapada por la mesa) se rehace entera.
                boolean escaleraEnL = level.getBlockState(new BlockPos(
                        base.getX() + TABERNA_ESCALERA_PIE_DX, nivel,
                        base.getZ() + TABERNA_ESCALERA_MESETA_Z)).is(Blocks.DARK_OAK_STAIRS)
                        && level.getBlockState(new BlockPos(base.getX() + TABERNA_ESCALERA_X, nivel + 4,
                        base.getZ() + TABERNA_ESCALERA_TOPE_Z)).is(Blocks.DARK_OAK_STAIRS);
                return aPlomo && escaleraEnL;
            }
        }
        return false;
    }

    /**
     * Construye la <b>TABERNA</b>: dos plantas y tejado a dos aguas. Abajo, el <b>comedor</b>: la <b>cocina</b> del
     * cocinero, el <b>hogar</b> con su chimenea, la <b>barra</b> con las pipas, seis mesas con sus sillas, la
     * escalera y faroles por todas partes. Arriba, la <b>posada</b>: seis cuartos con sus camas alrededor de la
     * galería (para los viajeros y para la milicia cuando no está de guardia). La puerta da al <b>oeste</b>, a la
     * plaza, con porche, toldo y enseña.
     * <p>
     * Todo pasa por {@link #colocar}, así que <b>entra en el plano</b> (invariante I8) y el obrero lo repone.
     */
    private static void taberna(ServerLevel level, BlockPos center, BlockPos base, int nivel) {
        int bx = base.getX();
        int bz = base.getZ();
        int ancho = TABERNA_ANCHO;
        int fondo = TABERNA_FONDO;
        int y1 = nivel + TABERNA_PISO2;                  // donde se anda en la posada
        int yTecho = y1 + TABERNA_ALERO;                 // el alero: de ahí para arriba, el tejado
        // 1) EL SOLAR, DESPEJADO. El despeje cubre también la taberna VIEJA (la de 13x12 cabía dentro de ésta: si no,
        //    sus muros, su forjado y su tejado se quedarían dentro del edificio nuevo) y lo que hubiera en sus cofres
        //    se guarda ANTES en el almacén, porque tirar un cofre tira su contenido al suelo.
        despejarSolarDeLaTaberna(level, center, bx, bz, nivel, yTecho + 12);
        // 2) CIMIENTOS Y SUELO: piedra debajo, zócalo de piedra labrada alrededor (se ve, como en el arte) y tablones
        //    en la capa que se pisa. La planta baja se anda a la cota del pueblo, como el resto de las casas.
        for (int dx = -TABERNA_VUELO - 1; dx <= ancho + TABERNA_VUELO; dx++) {
            for (int dz = -TABERNA_VUELO - 1; dz <= fondo + TABERNA_VUELO; dz++) {
                boolean dentro = dx >= 0 && dx < ancho && dz >= 0 && dz < fondo;
                colocar(level, new BlockPos(bx + dx, nivel - 2, bz + dz), Blocks.COBBLESTONE.defaultBlockState(), 3);
                colocar(level, new BlockPos(bx + dx, nivel - 1, bz + dz),
                        dentro ? Blocks.DARK_OAK_PLANKS.defaultBlockState()
                               : Blocks.STONE_BRICKS.defaultBlockState(), 3);
            }
        }
        // 3) LOS CUATRO MUROS DE LA PLANTA BAJA: cal y entramado de roble oscuro, con la PUERTA en el centro del muro
        //    oeste (el que mira a la plaza, por donde el pueblo entra a comer).
        muroTudor(level, bx, bz, 0, 1, fondo, nivel, TABERNA_PISO2 - 1, 2, TABERNA_PUERTA, Direction.WEST, true);
        muroTudor(level, bx + ancho - 1, bz, 0, 1, fondo, nivel, TABERNA_PISO2 - 1, 2, -1, Direction.WEST, true);
        muroTudor(level, bx, bz, 1, 0, ancho, nivel, TABERNA_PISO2 - 1, 2, -1, Direction.WEST, true);
        muroTudor(level, bx, bz + fondo - 1, 1, 0, ancho, nivel, TABERNA_PISO2 - 1, 2, -1, Direction.WEST, true);
        // 3b) LA PUERTA DE SERVICIO, en el muro SUR (migración 51). Se vuelve a pasar el muro entero con el índice
        //     de la puerta: así la puerta SOBRESCRIBE esas dos celdas (la de abajo y la de arriba) y el resto del
        //     muro queda igual (mismo método, mismas reglas de postes, cal y ventanas). Mira al SUR, que es hacia
        //     donde se sale; delante (dz=15) el patio está a la cota, así que el escalón de entrada no pone nada
        //     (solo lo haría si el suelo de fuera hubiera quedado más bajo que el piso).
        muroTudor(level, bx, bz + fondo - 1, 1, 0, ancho, nivel, TABERNA_PISO2 - 1, 2, TABERNA_PUERTA_SUR,
                Direction.SOUTH, true);
        escalonDeEntrada(level, new BlockPos(bx + TABERNA_PUERTA_SUR, nivel, bz + fondo - 1));
        // 4) EL COMEDOR: la cocina del cocinero (con su ahumador), el hogar con su chimenea, la barra con las pipas y
        //    las seis mesas con sus sillas.
        cocinaDeLaTaberna(level, bx, bz, nivel);
        hogarDeLaTaberna(level, bx, bz, nivel);
        barraDeLaTaberna(level, bx, bz, nivel);
        for (int[] mesa : TABERNA_MESAS) {
            mesaConSillas(level, new BlockPos(bx + mesa[0], nivel, bz + mesa[1]));
        }
        // 5) LA ESCALERA (sube pegada al muro oeste, dentro de su caja) y EL FORJADO DE LA POSADA, con su hueco.
        //    El orden importa: la escalera se coloca DESPUÉS del forjado, porque el hueco es justo donde ella sube.
        forjadoDeLaPosada(level, bx, bz, nivel);
        escaleraDeLaTaberna(level, bx, bz, nivel, y1);
        // 6) LOS MUROS DE LA POSADA, un bloque por fuera de los de abajo (el vuelo del arte conceptual): la cal y el
        //    entramado de arriba se apoyan en las cabezas de viga del forjado.
        int largoTramo = fondo + 2 * TABERNA_VUELO;
        int largoFrente = ancho + 2 * TABERNA_VUELO;
        muroTudor(level, bx - TABERNA_VUELO, bz - TABERNA_VUELO, 0, 1, largoTramo, y1, TABERNA_ALERO, 2, -1,
                Direction.WEST, false);
        muroTudor(level, bx + ancho - 1 + TABERNA_VUELO, bz - TABERNA_VUELO, 0, 1, largoTramo, y1, TABERNA_ALERO, 2, -1,
                Direction.WEST, false);
        muroTudor(level, bx - TABERNA_VUELO, bz - TABERNA_VUELO, 1, 0, largoFrente, y1, TABERNA_ALERO, 2, -1,
                Direction.WEST, false);
        muroTudor(level, bx - TABERNA_VUELO, bz + fondo - 1 + TABERNA_VUELO, 1, 0, largoFrente, y1, TABERNA_ALERO, 2, -1,
                Direction.WEST, false);
        // 7) LA POSADA (los cuartos, las camas y la galería), su techo de tablones y el TEJADO a dos aguas.
        posadaDeLaTaberna(level, bx, bz, y1, yTecho);
        techoDeLaPosada(level, bx, bz, yTecho);
        tejadoDeLaTaberna(level, bx, bz, fondo, yTecho);
        // La chimenea, la ÚLTIMA: tiene que atravesar el forjado, el techo y el tejado (si fuera antes, el tejado
        // la enterraría).
        chimeneaDeLaTaberna(level, bx, bz, nivel, yTecho);
        // 8) EL PORCHE de la puerta (al oeste, dando a la plaza) y TODAS LAS LUCES de la taberna.
        porcheDeLaTaberna(level, bx, bz, nivel);
        lucesDeLaTaberna(level, bx, bz, nivel, y1, yTecho);
        // 9) EL DESVÁN (migración 51) y EL POZO DE LA ESCALERA, TAPADO. Va al FINAL a propósito: el desván vacía el
        //    interior del tejado, abre el hueco de subida en el techo de la posada y monta la escalera nueva, que
        //    ocupa una de las celdas donde `lucesDeLaTaberna` cuelga un farol de la galería (el de dz=7, dx=6, justo
        //    el cuarto escalón). Si el desván fuera antes, ese farol volvería a caer en mitad de la escalera... y
        //    encima se quedaría colgado del aire, porque el desván abre el tablón que lo sostiene.
        desvanDeLaTaberna(level, bx, bz, nivel);
        // Y el pozo de la escalera vieja, cerrado por el sur: daba al cuarto suroeste de la posada y el primero que
        // se asomaba se caía al comedor.
        muroDelHuecoDeLaEscalera(level, bx, bz, y1, yTecho);
    }

    /** ¿Es esta celda (relativa a la esquina) el <b>hueco de la escalera</b> en el forjado de la posada? */
    private static boolean esHuecoDeLaEscalera(int dx, int dz) {
        // El hueco llega hasta la MESETA (cuatro filas, de TOPE_Z a TOPE_Z+3): con tres filas, el que sube desde la
        // meseta al primer escalón de arriba golpeaba con la cabeza en el borde del forjado (lo reportó el jugador:
        // "los 2 bloques de madera que están justo debajo de los pies míos estorban a todo el que quiere subir, su
        // cabeza topa con ellos"). El tramo de ABAJO sí va bajo el forjado: se sube con dos bloques de altura libre.
        return dx >= TABERNA_ESCALERA_X && dx < TABERNA_ESCALERA_X + TABERNA_ESCALERA_ANCHO
                && dz >= TABERNA_ESCALERA_TOPE_Z && dz <= TABERNA_ESCALERA_TOPE_Z + 3;
    }

    /**
     * El <b>forjado de la posada</b>: el suelo del piso de arriba (y el techo del comedor) en tablones de roble
     * oscuro. Cubre TODO el vuelo (un bloque por fuera de los muros de abajo) menos el <b>hueco de la escalera</b>, y
     * remata el borde con las <b>cabezas de viga</b> del vuelo (los troncos que se ven bajo el alero).
     */
    private static void forjadoDeLaPosada(ServerLevel level, int bx, int bz, int nivel) {
        int y = nivel + TABERNA_PISO2 - 1;
        for (int dx = -TABERNA_VUELO; dx <= TABERNA_ANCHO + TABERNA_VUELO - 1; dx++) {
            for (int dz = -TABERNA_VUELO; dz <= TABERNA_FONDO + TABERNA_VUELO - 1; dz++) {
                if (esHuecoDeLaEscalera(dx, dz)) {
                    continue;
                }
                colocar(level, new BlockPos(bx + dx, y, bz + dz), Blocks.DARK_OAK_PLANKS.defaultBlockState(), 3);
            }
        }
        // Las cabezas de viga: en el anillo que vuela (fuera de los muros de abajo), un tronco cada cuatro bloques,
        // justo debajo del forjado. Es lo que hace que el vuelo se vea SOSTENIDO (y no flotando).
        for (int dx = -TABERNA_VUELO; dx <= TABERNA_ANCHO + TABERNA_VUELO - 1; dx++) {
            for (int dz = -TABERNA_VUELO; dz <= TABERNA_FONDO + TABERNA_VUELO - 1; dz++) {
                boolean vuela = dx < 0 || dx >= TABERNA_ANCHO || dz < 0 || dz >= TABERNA_FONDO;
                if (vuela && (dx + dz) % 4 == 0) {
                    colocar(level, new BlockPos(bx + dx, y - 1, bz + dz), Blocks.DARK_OAK_LOG.defaultBlockState(), 3);
                }
            }
        }
    }

    /**
     * La <b>escalera en L</b> al piso de arriba: el primer tramo sube del comedor (este→oeste) hasta la <b>meseta</b>
     * de la esquina suroeste, y el segundo sube de ahí (sur→norte) pegado al muro oeste hasta la galería de la
     * posada. Los dos tramos y la meseta son <b>dobles</b> (dos bloques de ancho).
     * <p>
     * <b>OJO con la orientación</b>: en las escaleras del juego la cara alta (por donde se sube) es la que marca
     * {@code FACING}, así que cada tramo mira hacia donde <b>sube</b> (el de abajo al oeste, el de arriba al norte).
     * La escalera vieja subía hacia el norte mirando al sur y no se podía subir (lo reportó el jugador).
     */
    private static void escaleraDeLaTaberna(ServerLevel level, int bx, int bz, int nivel, int y1) {
        BlockState tablon = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        // 1) EL TRAMO DE ABAJO (el pie, mirando al comedor): dos escalones de este a oeste.
        for (int k = 0; k < TABERNA_ESCALERA_ANCHO; k++) {
            for (int dz = TABERNA_ESCALERA_MESETA_Z; dz <= TABERNA_ESCALERA_MESETA_Z + 1; dz++) {
                BlockPos escalon = new BlockPos(bx + TABERNA_ESCALERA_PIE_DX - k, nivel + k, bz + dz);
                colocar(level, escalon, Blocks.DARK_OAK_STAIRS.defaultBlockState()
                        .setValue(StairBlock.FACING, Direction.WEST).setValue(StairBlock.HALF, Half.BOTTOM), 3);
                colocar(level, escalon.above(), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        // 2) LA MESETA de la esquina (2x2, a la altura a la que llega el tramo de abajo): tablones y el aire libre
        //    encima, para que se pueda estar de pie en ella.
        for (int dx = TABERNA_ESCALERA_X; dx < TABERNA_ESCALERA_X + TABERNA_ESCALERA_ANCHO; dx++) {
            for (int dz = TABERNA_ESCALERA_MESETA_Z; dz <= TABERNA_ESCALERA_MESETA_Z + 1; dz++) {
                colocar(level, new BlockPos(bx + dx, nivel + 1, bz + dz), tablon, 3);
                for (int dy = 2; dy <= 3; dy++) {
                    colocar(level, new BlockPos(bx + dx, nivel + dy, bz + dz), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        // 3) EL TRAMO DE ARRIBA: tres escalones de sur a norte, pegados al muro oeste, hasta la galería.
        for (int i = 0; i < 3; i++) {
            for (int k = 0; k < TABERNA_ESCALERA_ANCHO; k++) {
                BlockPos escalon = new BlockPos(bx + TABERNA_ESCALERA_X + k, nivel + 2 + i,
                        bz + TABERNA_ESCALERA_MESETA_Z - 1 - i);
                colocar(level, escalon, Blocks.DARK_OAK_STAIRS.defaultBlockState()
                        .setValue(StairBlock.FACING, Direction.NORTH).setValue(StairBlock.HALF, Half.BOTTOM), 3);
                colocar(level, escalon.above(), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        // 4) LA CAJA: el muro del ESTE del pozo (las tres filas del hueco), del suelo de la posada al techo. Sin él,
        //    el hueco del forjado es un pozo abierto al lado de la galería y el primero que paseara se caería.
        for (int dz = TABERNA_ESCALERA_TOPE_Z; dz <= TABERNA_ESCALERA_MESETA_Z; dz++) {
            for (int y = y1; y < y1 + TABERNA_ALERO; y++) {
                colocar(level, new BlockPos(bx + TABERNA_ESCALERA_X + TABERNA_ESCALERA_ANCHO, y, bz + dz),
                        tablon, 3);
            }
        }
    }

    /**
     * El <b>muro que cierra el pozo de la escalera por el sur</b> (la fila pegada al borde del hueco del forjado).
     * El pozo (dx 1..2, dz 8..11) tiene muro al <b>este</b> (la caja, {@code escaleraDeLaTaberna}) y la pared oeste de
     * la casa al oeste, pero por el <b>sur</b> daba de lleno al <b>cuarto suroeste de la posada</b>: se podía entrar
     * andando desde el cuarto y caer al comedor. Lo avisó el jugador: <i>"arriba hay un cuarto que está abierto
     * porque da precisamente al hueco de las escaleras; estaría bien que se tapara con una pared, para que nadie se
     * cayera"</i>.
     * <p>
     * Son <b>tablones de roble oscuro</b> (el mismo material que los tabiques de los cuartos, {@code muroDeCuarto}):
     * una valla no frena a un aldeano igual y no pega con el entramado. Va del suelo de la posada ({@code y1}) al
     * techo, y <b>NO</b> se toca la salida de la escalera (dz=8, la fila del último escalón, que se deja abierta) ni
     * nada que no sea aire: es <b>idempotente</b> y no le tira al jugador lo que tenga puesto ahí.
     */
    private static void muroDelHuecoDeLaEscalera(ServerLevel level, int bx, int bz, int y1, int yTecho) {
        BlockState tablon = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        int dzMuro = TABERNA_ESCALERA_TOPE_Z + 4;   // la primera fila CON suelo al sur del hueco (dz=12)
        int puestos = 0;
        for (int dx = TABERNA_ESCALERA_X; dx < TABERNA_ESCALERA_X + TABERNA_ESCALERA_ANCHO; dx++) {
            for (int y = y1; y < yTecho; y++) {
                BlockPos p = new BlockPos(bx + dx, y, bz + dzMuro);
                BlockState actual = level.getBlockState(p);
                if (actual.is(tablon.getBlock())) {
                    continue;   // ya está puesto (idempotente)
                }
                if (!actual.isAir()) {
                    continue;   // no se toca lo que no sea aire (lo que haya puesto el jugador se queda)
                }
                colocar(level, p, tablon, 3);
                puestos++;
            }
        }
        if (puestos > 0) {
            DevilRpg.LOGGER.info("[Village] Taberna en {}: pozo de la escalera cerrado por el sur ({} tablon(es) en"
                    + " dx {}..{}, dz {})", new BlockPos(bx, y1, bz), TABERNA_ESCALERA_X,
                    TABERNA_ESCALERA_X + TABERNA_ESCALERA_ANCHO - 1, dzMuro);
        }
    }

    /**
     * Un <b>tramo de muro Tudor</b>: <b>solera</b> (piedra labrada en la planta baja, tablones en el vuelo de la
     * posada), paneles de <b>cal</b> (terracota blanca) con el <b>entramado de roble oscuro</b> —postes cada cuatro
     * bloques y viga arriba— y <b>ventanas</b> de dos cristales entre poste y poste. Si {@code indicePuerta >= 0}, en
     * esa celda va la <b>puerta</b> de dos bloques, que es por donde entra el pueblo.
     * <p>
     * El tramo se recorre desde {@code (x0,z0)} sumando {@code (dx,dz)} {@code largo} veces: el mismo método sirve
     * para los cuatro muros y para las dos plantas (lo único que cambia es dónde está la solera y la ventana).
     */
    private static void muroTudor(ServerLevel level, int x0, int z0, int dx, int dz, int largo, int yBase, int alto,
                                  int kVentana, int indicePuerta, Direction miraPuerta, boolean plantaBaja) {
        for (int i = 0; i < largo; i++) {
            int x = x0 + dx * i;
            int z = z0 + dz * i;
            boolean poste = i % 4 == 0 || i == largo - 1;
            // La ventana son DOS bloques de CRISTAL ENTERO (no paneles). Un `glass_pane` se dibuja segun sus cuatro
            // conexiones y NO conecta con los troncos de los postes, asi que se veia cortado (media ventana) o como
            // una franja fina. Lo pidio el jugador: "mejor pon ventanas de cristal completo de las de cubo". Un
            // bloque de cristal no tiene conexiones: siempre se ve entero y la ventana queda de dos de ancho.
            boolean ventana = i % 4 == 1 || i % 4 == 2;
            for (int k = 0; k < alto; k++) {
                BlockPos p = new BlockPos(x, yBase + k, z);
                if (i == indicePuerta && k <= 1) {
                    colocar(level, p, Blocks.DARK_OAK_DOOR.defaultBlockState()
                            .setValue(DoorBlock.FACING, miraPuerta)
                            .setValue(DoorBlock.HALF, k == 0 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER), 3);
                } else if (poste) {
                    colocar(level, p, Blocks.DARK_OAK_LOG.defaultBlockState(), 3);
                } else if (k == 0) {
                    colocar(level, p, (plantaBaja ? Blocks.STONE_BRICKS : Blocks.DARK_OAK_PLANKS).defaultBlockState(), 3);
                } else if (k == alto - 1) {
                    colocar(level, p, Blocks.DARK_OAK_PLANKS.defaultBlockState(), 3);
                } else if (ventana && k == kVentana) {
                    colocar(level, p, Blocks.GLASS.defaultBlockState(), 3); // cristal ENTERO (ver arriba)
                } else {
                    // El panel de CAL (antes terracota blanca): NO puede ser un bloque con etiqueta de terreno
                    // (terracota lo es, para las aldeas de meseta) o el recorte del nivelado se lo come.
                    colocar(level, p, Blocks.SMOOTH_QUARTZ.defaultBlockState(), 3);
                }
            }
        }
    }

    /**
     * La <b>cocina</b> del comedor (esquina noroeste, cerrada con sus dos tabiques): el <b>ahumador</b> del cocinero
     * —su puesto de trabajo, que es el que busca {@code VillagerCookGoal}— con la casilla de delante libre para que
     * se ponga a cocinar, el horno, la mesa de trabajo, su arca y un farol.
     * <p>
     * <b>OJO con los bloques que se ponen aquí</b>: nada de <b>barriles</b> (puesto del PESCADOR) ni de
     * <b>calderos</b> (puesto del CURTIDOR) — un aldeano sin oficio los reclamaría y se pondría un oficio que este
     * pueblo no tiene. El hogar va de ladrillo y el fuego es un campfire metido en el muro, así que no se pisa.
     */
    private static void cocinaDeLaTaberna(ServerLevel level, int bx, int bz, int nivel) {
        int alto = TABERNA_PISO2 - 1;
        BlockState tablon = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        // Los dos tabiques (el este y el sur), con el hueco de la puerta en el sur.
        for (int dz = 1; dz <= 5; dz++) {
            for (int k = 0; k < alto; k++) {
                colocar(level, new BlockPos(bx + 6, nivel + k, bz + dz), tablon, 3);
            }
        }
        for (int dx = 1; dx <= 6; dx++) {
            for (int k = 0; k < alto; k++) {
                if (dx == 3 && k <= 1) {
                    continue; // la puerta de la cocina (dos de alto)
                }
                colocar(level, new BlockPos(bx + dx, nivel + k, bz + 5), tablon, 3);
            }
        }
        // El puesto del cocinero: el ahumador (con su casilla de delante libre: él se pone al norte).
        colocar(level, new BlockPos(bx + TABERNA_COCINA[0], nivel, bz + TABERNA_COCINA[1]),
                Blocks.SMOKER.defaultBlockState().setValue(
                        net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,
                        Direction.NORTH), 3);
        colocar(level, new BlockPos(bx + 2, nivel, bz + 2), Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
        colocar(level, new BlockPos(bx + 1, nivel, bz + 1), Blocks.FURNACE.defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,
                        Direction.SOUTH), 3);
        colocar(level, new BlockPos(bx + 5, nivel, bz + 4), Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.WEST), 3);
        colocar(level, new BlockPos(bx + 1, nivel, bz + 4), Blocks.POTTED_FERN.defaultBlockState(), 3);
        // EL ALMACÉN DE COMIDA DEL PUEBLO (migración 47): el cofre DOBLE de la despensa, contra el muro norte de la
        // cocina. Es la comida de la aldea y vive donde el cocinero cocina y donde el pueblo viene a comer; antes
        // estaba en el kiosco de la plaza (lo pidió el jugador: "no tiene sentido que esté en el kiosco central").
        BlockPos despensa = new BlockPos(bx + TABERNA_DESPENSA[0], nivel, bz + TABERNA_DESPENSA[1]);
        cofreDoble(level, despensa, despensa.east(), Direction.SOUTH);
    }

    /**
     * El <b>hogar</b> de la taberna: un hogar de ladrillo en el muro norte, con el <b>fuego</b> metido dentro del
     * muro (una casilla que no se pisa, así nadie se quema al pasar) y su repisa de madera. La boca del hogar mira al
     * comedor y su cara de la calle la tapa la <b>chimenea</b> ({@link #chimeneaDeLaTaberna}, que se pone al final,
     * cuando ya está el tejado).
     */
    private static void hogarDeLaTaberna(ServerLevel level, int bx, int bz, int nivel) {
        int hx = TABERNA_HOGAR[0];
        int hz = TABERNA_HOGAR[1];
        for (int dx = hx - 1; dx <= hx + 1; dx++) {
            for (int k = 0; k <= 2; k++) {
                colocar(level, new BlockPos(bx + dx, nivel + k, bz + hz), Blocks.BRICKS.defaultBlockState(), 3);
            }
        }
        // El fuego, en la boca del hogar (el aire de encima deja ver la llama desde el comedor).
        colocar(level, new BlockPos(bx + hx, nivel, bz + hz), Blocks.CAMPFIRE.defaultBlockState(), 3);
        colocar(level, new BlockPos(bx + hx, nivel + 1, bz + hz), Blocks.AIR.defaultBlockState(), 3);
        colocar(level, new BlockPos(bx + hx, nivel + 2, bz + hz), Blocks.AIR.defaultBlockState(), 3);
        // Y el hogar se remata con su repisa de madera, a la altura de la viga del muro.
        for (int dx = hx - 2; dx <= hx + 2; dx++) {
            colocar(level, new BlockPos(bx + dx, nivel + TABERNA_PISO2 - 1, bz + hz),
                    Blocks.DARK_OAK_PLANKS.defaultBlockState(), 3);
        }
    }

    /**
     * La <b>chimenea</b> del hogar: un caño de ladrillo <b>por fuera</b> del muro norte (como en el arte conceptual),
     * que sube pegado a la fachada, atraviesa el vuelo de la posada, su muro y el <b>tejado</b> (por eso se coloca la
     * última: si no, el tejado la taparía) y sale por encima con su remate de losa.
     * <p>
     * Va por <b>fuera</b> a propósito, y no metida en el muro: el fuego del hogar está en la boca del muro y su cara
     * norte daba a la calle, así que <b>se veía la llama desde fuera</b> (lo avisó el jugador: <i>"la chimenea está
     * sin protección externa"</i>). Con el caño por delante, el hogar queda tapado por el ladrillo.
     */
    private static void chimeneaDeLaTaberna(ServerLevel level, int bx, int bz, int nivel, int yTecho) {
        int hx = TABERNA_HOGAR[0];
        int hz = TABERNA_HOGAR[1] - 1;   // un bloque por FUERA del muro: tapa la boca del hogar
        for (int y = nivel; y <= yTecho + 5; y++) {
            colocar(level, new BlockPos(bx + hx, y, bz + hz), Blocks.BRICKS.defaultBlockState(), 3);
        }
        colocar(level, new BlockPos(bx + hx, yTecho + 6, bz + hz), Blocks.BRICK_SLAB.defaultBlockState(), 3);
    }

    /**
     * La <b>barra</b> del comedor, en el muro sur: el mostrador de tronco descortezado y, detrás, las <b>pipas</b> de
     * cerveza contra la pared.
     * <p>
     * <b>OJO con el bloque de las pipas</b>: NO se usa {@code BARREL}, porque en vanilla el <b>barril es el puesto de
     * trabajo del PESCADOR</b> y un aldeano sin oficio (una cría que crece, por ejemplo) lo reclamaría y se volvería
     * pescador — un oficio que este pueblo <b>todavía no tiene</b> (tendrá su edificio y su lago más adelante). Las
     * pipas son de madera con corteza ({@code OAK_WOOD}), que se ve como un tonel y no es puesto de nadie; el
     * mostrador va de tronco descortezado, así que se distinguen.
     */
    private static void barraDeLaTaberna(ServerLevel level, int bx, int bz, int nivel) {
        // La barra va de dx=7 a dx=13: el pie de la escalera está en dx=4 y su carril de entrada es dx=5..6, así que
        // empezando en dx=5 su extremo (2x2) quedaba JUSTO delante de las escaleras (lo reportó el jugador: "hay 4
        // bloques que estorban, 2 de madera pelada y 2 de madera normal, justo enfrente de las escaleras").
        for (int dx = 7; dx <= 13; dx++) {
            colocar(level, new BlockPos(bx + dx, nivel, bz + 12), Blocks.STRIPPED_OAK_LOG.defaultBlockState(), 3);
            colocar(level, new BlockPos(bx + dx, nivel, bz + 13), Blocks.OAK_WOOD.defaultBlockState(), 3);
        }
        colocar(level, new BlockPos(bx + 8, nivel, bz + 11), Blocks.POTTED_DANDELION.defaultBlockState(), 3);
    }

    /**
     * Repara la <b>escalera de una taberna ya construida</b> (migración 48). Dos cosas que solo se notan subiendo:
     * <ul>
     *   <li>El <b>hueco del forjado</b> tiene que llegar hasta la meseta. Con el hueco corto (tres filas), el que sube
     *       desde la meseta al primer escalón de arriba da con la cabeza en el borde del piso de arriba.</li>
     *   <li>La <b>barra</b> no puede empezar antes de dx=7: su extremo (2x2) quedaba justo delante del pie de la
     *       escalera. La barra se corre al este (dx=12..13) para dejarla igual de larga.</li>
     * </ul>
     * Es <b>idempotente</b> y solo toca las celdas de la barra y del forjado: nunca reconstruye la taberna (eso
     * borraría la despensa, las camas y lo que el jugador tenga dentro).
     */
    public static void arreglarEscaleraDeLaTaberna(ServerLevel level, BlockPos center) {
        if (!tabernaConstruida(level, center)) {
            return; // no hay taberna nueva que reparar (una vieja la rehace `asegurarTaberna` entera)
        }
        int nivel = cotaDeLaPlaza(level, center);
        BlockPos base = baseDeLaTaberna(center);
        int yForjado = nivel + TABERNA_PISO2 - 1;
        // 1) El forjado que tapa la subida: fuera (el hueco llega hasta la meseta).
        int quitados = 0;
        for (int dx = TABERNA_ESCALERA_X; dx < TABERNA_ESCALERA_X + TABERNA_ESCALERA_ANCHO; dx++) {
            for (int dz = TABERNA_ESCALERA_TOPE_Z; dz <= TABERNA_ESCALERA_TOPE_Z + 3; dz++) {
                if (!esHuecoDeLaEscalera(dx, dz)) {
                    continue;
                }
                quitados += quitarSiEs(level, base.getX() + dx, yForjado, base.getZ() + dz,
                        Blocks.DARK_OAK_PLANKS);
            }
        }
        // 2) La barra: fuera su extremo oeste (el que tapaba la entrada) y sus dos bloques nuevos al este.
        int movidos = 0;
        for (int dx = 5; dx <= 6; dx++) {
            movidos += quitarSiEs(level, base.getX() + dx, nivel, base.getZ() + 12, Blocks.STRIPPED_OAK_LOG);
            movidos += quitarSiEs(level, base.getX() + dx, nivel, base.getZ() + 13, Blocks.OAK_WOOD);
        }
        for (int dx = 12; dx <= 13; dx++) {
            colocar(level, new BlockPos(base.getX() + dx, nivel, base.getZ() + 12),
                    Blocks.STRIPPED_OAK_LOG.defaultBlockState(), 3);
            colocar(level, new BlockPos(base.getX() + dx, nivel, base.getZ() + 13),
                    Blocks.OAK_WOOD.defaultBlockState(), 3);
        }
        DevilRpg.LOGGER.info("[Village] Taberna de {}: escalera reparada ({} tablon(es) del forjado fuera del hueco,"
                + " {} bloque(s) de barra movidos al este)", center, quitados, movidos);
    }

    /**
     * Vuelve a pasar los <b>muros Tudor y los frontones</b> de una taberna ya construida (migración 49; desde la 51
     * también deja puesta la <b>puerta de servicio</b> del muro sur). El recorte del nivelado se comía los paneles de
     * cal (la terracota contaba como terreno) y las paredes quedaban con agujeros: los postes, la solera, los
     * tablones y los cristales seguían ahí, pero <b>toda la cal</b> era aire.
     * <p>
     * Solo se vuelven a pasar constructores de <b>estructura</b> ({@code muroTudor} y {@code tejadoDeLaTaberna}): no
     * se toca la posada (camas), ni la cocina, ni la despensa, así que es <b>idempotente</b> y no borra nada de dentro.
     */
    public static void rehacerMurosDeLaTaberna(ServerLevel level, BlockPos center) {
        if (!tabernaConstruida(level, center)) {
            return;
        }
        int nivel = cotaDeLaPlaza(level, center);
        BlockPos base = baseDeLaTaberna(center);
        int bx = base.getX();
        int bz = base.getZ();
        int ancho = TABERNA_ANCHO;
        int fondo = TABERNA_FONDO;
        int y1 = nivel + TABERNA_PISO2;
        int yTecho = y1 + TABERNA_ALERO;
        // 1) Los cuatro muros del comedor (la puerta principal va en el oeste, como al construirla; y desde la
        //    migración 51 la de servicio va en el sur: es este mismo constructor con el índice de la puerta).
        muroTudor(level, bx, bz, 0, 1, fondo, nivel, TABERNA_PISO2 - 1, 2, TABERNA_PUERTA, Direction.WEST, true);
        muroTudor(level, bx + ancho - 1, bz, 0, 1, fondo, nivel, TABERNA_PISO2 - 1, 2, -1, Direction.WEST, true);
        muroTudor(level, bx, bz, 1, 0, ancho, nivel, TABERNA_PISO2 - 1, 2, -1, Direction.WEST, true);
        muroTudor(level, bx, bz + fondo - 1, 1, 0, ancho, nivel, TABERNA_PISO2 - 1, 2, TABERNA_PUERTA_SUR,
                Direction.SOUTH, true);
        escalonDeEntrada(level, new BlockPos(bx + TABERNA_PUERTA_SUR, nivel, bz + fondo - 1));
        // 2) Los cuatro del vuelo de la posada (un bloque por fuera, como al construirla).
        int largoTramo = fondo + 2 * TABERNA_VUELO;
        int largoFrente = ancho + 2 * TABERNA_VUELO;
        muroTudor(level, bx - TABERNA_VUELO, bz - TABERNA_VUELO, 0, 1, largoTramo, y1, TABERNA_ALERO, 2, -1,
                Direction.WEST, false);
        muroTudor(level, bx + ancho - 1 + TABERNA_VUELO, bz - TABERNA_VUELO, 0, 1, largoTramo, y1, TABERNA_ALERO, 2,
                -1, Direction.WEST, false);
        muroTudor(level, bx - TABERNA_VUELO, bz - TABERNA_VUELO, 1, 0, largoFrente, y1, TABERNA_ALERO, 2, -1,
                Direction.WEST, false);
        muroTudor(level, bx - TABERNA_VUELO, bz + fondo - 1 + TABERNA_VUELO, 1, 0, largoFrente, y1, TABERNA_ALERO, 2,
                -1, Direction.WEST, false);
        // 3) El tejado (y con él la cal de los frontones): solo tablones, escaleras, cristales y cal.
        tejadoDeLaTaberna(level, bx, bz, fondo, yTecho);
        DevilRpg.LOGGER.info("[Village] Taberna de {}: muros y frontones repasados (la cal que se comia el nivelado)",
                center);
    }

    /**
     * <b>Repara el desván</b> de una taberna ya construida (migración 51): el mismo trabajo que hace
     * {@link #desvanDeLaTaberna(ServerLevel, int, int, int)} al construirla, para las tabernas que ya estaban de pie
     * cuando el hueco bajo el tejado era macizo. Es <b>idempotente</b> (el relleno que ya no está no se busca, y el
     * mobiliario se coloca solo en celdas vacías) y <b>no rehace la taberna</b>: no toca ni la despensa, ni las camas,
     * ni los cuartos.
     * <p>
     * <b>OJO con el orden</b>: va DESPUÉS de {@link #rehacerMurosDeLaTaberna}, que vuelve a pasar el tejado entero
     * (con su relleno interior). Si fuera antes, el tejado repasado volvería a tapiar el desván.
     * <p>
     * Desde la migración 52 monta la escalera <b>en el cuarto suroeste</b> (la de la 51 subía por la galería y tapaba
     * el corredor de los cuartos): a una taberna que todavía tenga aquélla hay que pasarle <b>también</b>
     * {@link #moverLaEscaleraDelDesvan}, que la deshace y cierra el hueco que dejó en el techo.
     */
    public static void desvanDeLaTaberna(ServerLevel level, BlockPos center) {
        if (!tabernaConstruida(level, center)) {
            return;
        }
        BlockPos base = baseDeLaTaberna(center);
        desvanDeLaTaberna(level, base.getX(), base.getZ(), cotaDeLaPlaza(level, center));
    }

    /**
     * <b>Cierra el pozo de la escalera</b> de una taberna ya construida (migración 51): el mismo muro que se pone al
     * construirla ({@link #muroDelHuecoDeLaEscalera}), para las tabernas que ya estaban de pie con el hueco abierto
     * al cuarto suroeste de la posada. Solo toca esas celdas (y solo si están vacías), así que es idempotente.
     */
    public static void cerrarElHuecoDeLaEscalera(ServerLevel level, BlockPos center) {
        if (!tabernaConstruida(level, center)) {
            return;
        }
        int nivel = cotaDeLaPlaza(level, center);
        BlockPos base = baseDeLaTaberna(center);
        muroDelHuecoDeLaEscalera(level, base.getX(), base.getZ(), nivel + TABERNA_PISO2,
                nivel + TABERNA_PISO2 + TABERNA_ALERO);
    }

    /**
     * <b>Mueve la escalera del desván</b> de la galería al cuarto suroeste (migración 52). La escalera de la 51 subía
     * en recto por el carril <b>norte</b> de la galería de la posada ({@code dz=7}) y <b>tapaba el corredor</b> por el
     * que se entra a los cuartos del segundo piso: el jugador lo reportó (<i>"al poner la escalera al tercer piso
     * tapaste el corredor que permite que se entre a los diferentes cuartos del 2do piso; mejor sacrifica un cuarto
     * del 2do piso para poner ahí una escalera y libera el corredor"</i>). Aquí se <b>deshace</b> aquella escalera y se
     * deja la galería <b>entera</b> libre; la escalera nueva (dentro del cuarto suroeste) y su hueco los pone
     * {@link #desvanDeLaTaberna(ServerLevel, int, int, int)}, que se llama al final.
     * <p>
     * El deshacer es <b>celda por celda</b>:
     * <ul>
     *   <li>Los <b>{@code DESVAN_ESCALONES} escalones</b> de la galería: fuera (solo si siguen siendo escalones
     *       nuestros, {@code quitarSiEs}).</li>
     *   <li>El <b>hueco que abrieron en el techo</b>: se vuelve a cerrar. Por cada escalón se reponen las dos celdas
     *       que tenía abiertas encima, con <b>tablones</b> en la capa del techo de la posada ({@code yTecho - 1}) y
     *       <b>tejas de la placa</b> en la del suelo del desván ({@code yTecho}), que es lo que había antes: el techo
     *       queda <b>sólido</b> como estaba.</li>
     *   <li>El <b>farol de la galería</b> que se comió el cuarto escalón (en {@code dx=6}, {@code dz=7}): se cuelga
     *       otra vez de su tablón, ya repuesto. Y el farol de <b>repuesto</b> que se colgó al lado (en el carril sur,
     *       {@code dz=8}) se retira: la galería vuelve a tener los suyos y ningún otro.</li>
     *   <li>La <b>cama del cuarto suroeste</b>: se retira de ahí (el cuarto pasa a ser la caja de la escalera). Su
     *       sustituta la pone {@code amueblarElDesvan} arriba, así que el pueblo <b>no pierde ninguna cama</b>.</li>
     * </ul>
     * Es <b>idempotente</b> (solo quita el bloque <b>si es del tipo esperado</b>, {@code quitarSiEs}, y solo repone
     * donde está <b>vacío</b>, {@code colocarSiEstaVacio}) y <b>no rehace la taberna</b>: no toca ni la despensa, ni
     * las camas de los otros cuartos, ni lo que el jugador tenga puesto.
     * <p>
     * <b>OJO con el orden</b>: va DESPUÉS de {@link #rehacerMurosDeLaTaberna}, que vuelve a pasar el tejado entero
     * (con su relleno interior), y después de {@link #desvanDeLaTaberna(ServerLevel, BlockPos)} —el vaciado del
     * desván va después del tejado, invariante I20—; el cierre del hueco viejo no toca nada del desván (solo las dos
     * capas del forjado del piso de abajo).
     */
    public static void moverLaEscaleraDelDesvan(ServerLevel level, BlockPos center) {
        if (!tabernaConstruida(level, center)) {
            return;
        }
        int nivel = cotaDeLaPlaza(level, center);
        BlockPos base = baseDeLaTaberna(center);
        int bx = base.getX();
        int bz = base.getZ();
        int y1 = nivel + TABERNA_PISO2;
        int yTecho = y1 + TABERNA_ALERO;
        // 1) LA ESCALERA VIEJA, FUERA DE LA GALERÍA: cada escalón y, en su celda, las dos que tenía abiertas encima
        //    (por donde pasaba la cabeza). Reponiendo las capas del forjado, el techo vuelve a quedar sólido.
        int quitados = 0;
        for (int i = 0; i < DESVAN_ESCALONES; i++) {
            int x = bx + DESVAN_ESCALERA_VIEJA_PIE_DX + i;
            int y = y1 + i;
            int z = bz + DESVAN_ESCALERA_VIEJA_Z;
            quitados += quitarSiEs(level, x, y, z, Blocks.DARK_OAK_STAIRS);
            reponerLaCapaDelForjado(level, x, y, z, yTecho);
            for (int dy = 1; dy <= 2; dy++) {
                reponerLaCapaDelForjado(level, x, y + dy, z, yTecho);
            }
        }
        // 2) EL FAROL DE LA GALERÍA que se comió su cuarto escalón (dx=6, dz=7): vuelve a colgarse de su tablón, que
        //    se acaba de reponer (invariante I14: un farol colgado necesita un bloque sólido ENCIMA). Y el de repuesto
        //    que se puso en el carril sur (dz=8) sobra.
        BlockPos farolSuyo = new BlockPos(bx + DESVAN_ESCALERA_VIEJA_PIE_DX + 3, y1 + 3,
                bz + DESVAN_ESCALERA_VIEJA_Z);
        if (level.getBlockState(farolSuyo).isAir() && level.getBlockState(farolSuyo.above()).is(Blocks.DARK_OAK_PLANKS)) {
            colgar(level, farolSuyo);
        }
        BlockPos farolDeRepuesto = farolSuyo.offset(0, 0, 1);
        if (level.getBlockState(farolDeRepuesto).is(Blocks.LANTERN)) {
            colocar(level, farolDeRepuesto, Blocks.AIR.defaultBlockState(), 3);
        }
        // 3) LA CAMA DEL CUARTO SUROESTE, RETIRADA (el cuarto es ahora la caja de la escalera). Se retira la cama de
        //    la aldea (la roja, que es la que pone `posadaDeLaTaberna`): lo que el jugador haya puesto ahí no se toca.
        int camas = 0;
        for (int dz : new int[]{12, 13}) {
            BlockPos p = new BlockPos(bx + 4, y1, bz + dz);
            if (level.getBlockState(p).is(Blocks.RED_BED)) {
                colocar(level, p, Blocks.AIR.defaultBlockState(), 3);
                camas++;
            }
        }
        // 4) LA ESCALERA NUEVA (dentro del cuarto), EL HUECO NUEVO, el vaciado del desván y el mobiliario —con la
        //    cama recolocada—: todo idempotente y en el mismo sitio que al construir la taberna.
        desvanDeLaTaberna(level, bx, bz, nivel);
        DevilRpg.LOGGER.info("[Village] Taberna de {}: escalera del desvan movida al cuarto suroeste (galeria libre;"
                + " {} escalon(es) de la vieja fuera, {} mitad(es) de cama retirada(s))", center, quitados, camas);
    }

    /**
     * Repone la <b>capa del forjado</b> que le toca a esa celda: los <b>tablones</b> del techo de la posada
     * ({@code yTecho - 1}) o las <b>tejas</b> de la placa del tejado ({@code yTecho}), que es el suelo del desván. En
     * cualquier otra altura no hace nada (esa celda no era del forjado). Solo se pone donde está <b>vacío</b>, para no
     * pisar lo que haya puesto el jugador. La usa el reparador que vuelve a cerrar el hueco de la escalera vieja.
     */
    private static void reponerLaCapaDelForjado(ServerLevel level, int x, int y, int z, int yTecho) {
        if (y == yTecho - 1) {
            colocarSiEstaVacio(level, new BlockPos(x, y, z), Blocks.DARK_OAK_PLANKS.defaultBlockState());
        } else if (y == yTecho) {
            colocarSiEstaVacio(level, new BlockPos(x, y, z), Blocks.DEEPSLATE_TILES.defaultBlockState());
        }
    }

    /** Quita ese bloque <b>si es del tipo esperado</b> (para que una reparación no toque lo que puso el jugador). */
    private static int quitarSiEs(ServerLevel level, int x, int y, int z, Block bloque) {
        BlockPos pos = new BlockPos(x, y, z);
        if (level.getBlockState(pos).is(bloque)) {
            level.removeBlock(pos, false);
            return 1;
        }
        return 0;
    }

    /**
     * Coloca ese bloque <b>solo si la celda está vacía</b> (aire). Lo usan los reparadores de la migración, que
     * tienen que ser <b>idempotentes</b> y no pueden pisar lo que haya puesto el jugador: reemplazar un bloque con
     * {@code colocar} no lo tira al suelo, pero un <b>cofre</b> reemplazado sí pierde su contenido (mecánica del
     * juego), así que el mobiliario de una reparación se pone siempre con este guardia.
     *
     * @return {@code true} si ha colocado el bloque
     */
    private static boolean colocarSiEstaVacio(ServerLevel level, BlockPos pos, BlockState state) {
        if (!level.getBlockState(pos).isAir()) {
            return false;
        }
        colocar(level, pos, state, 3);
        return true;
    }

    /**
     * La <b>posada</b> (el piso de arriba): una <b>galería</b> de dos bloques de ancho que cruza la casa de este a
     * oeste, con <b>seis cuartos</b> alrededor (tres al norte y tres al sur) y sus <b>camas</b>, sus arcas y sus
     * faroles. Es el plano del <i>Building map: Inn</i> que trajo el jugador.
     * <p>
     * Los dos carriles de la galería ({@code dz=7} y {@code dz=8}) son el <b>corredor</b> del piso: en el muro norte
     * ({@code dz=6}) están las puertas de los cuartos del norte y en el sur ({@code dz=9}) las de los del sur, así que
     * <b>nada</b> puede ocuparlos (el jugador reportó que la escalera del desván, que subía por el carril norte, le
     * tapaba el paso a los cuartos: ver {@link #escaleraDelDesvan}). El cuarto <b>suroeste</b> está recortado por la
     * caja de la escalera de la taberna y desde la migración 52 es, además, la caja de la escalera del desván.
     */
    private static void posadaDeLaTaberna(ServerLevel level, int bx, int bz, int y1, int yTecho) {
        int alto = yTecho - y1;
        BlockState tablon = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        // Los dos muros de la galería (norte en lz=6 y sur en lz=9), con la puerta de cada cuarto. Van de lz=1 a
        // lz=17 (el INTERIOR: los dos pisos van a plomo, así que los cuartos empiezan un bloque más adentro que
        // cuando la planta alta volaba). El muro sur empieza en lz=4 porque a su izquierda va la CAJA DE LA ESCALERA.
        int[] puertasNorte = {2, 9, 15};
        int[] puertasSur = {4, 9, 15};
        for (int dx = 1; dx <= TABERNA_ANCHO - 2; dx++) {
            boolean puertaNorte = false;
            boolean puertaSur = false;
            for (int p : puertasNorte) {
                puertaNorte |= dx == p;
            }
            for (int p : puertasSur) {
                puertaSur |= dx == p;
            }
            if (dx >= 4) {
                muroDeCuarto(level, bx + dx, bz + 9, y1, alto, tablon, puertaSur, Direction.NORTH);
            }
            muroDeCuarto(level, bx + dx, bz + 6, y1, alto, tablon, puertaNorte, Direction.SOUTH);
        }
        // Los tabiques que separan los cuartos entre sí (a los dos lados de la galería).
        for (int dx : new int[]{6, 12}) {
            for (int dz = 1; dz <= 5; dz++) {
                for (int k = 0; k < alto; k++) {
                    colocar(level, new BlockPos(bx + dx, y1 + k, bz + dz), tablon, 3);
                }
            }
            for (int dz = 10; dz <= TABERNA_FONDO - 2; dz++) {
                for (int k = 0; k < alto; k++) {
                    colocar(level, new BlockPos(bx + dx, y1 + k, bz + dz), tablon, 3);
                }
            }
        }
        // LAS CAMAS: dos por cuarto con la cabecera contra el muro. El cuarto SUROESTE va sin cama desde la migración
        // 52: es el cuarto que se sacrifica para meter la escalera del desván (`escaleraDelDesvan`), y su cama se
        // recoloca en el desván (`amueblarElDesvan`) para que el pueblo no pierda ninguna (en vanilla cada cría
        // necesita una cama libre).
        int[][] camas = {{1, 2, -1}, {4, 2, -1},                       // cuarto noroeste (cabeza al norte)
                {8, 2, -1}, {10, 2, -1},                               // norte (centro)
                {14, 2, -1}, {17, 2, -1},                              // noreste
                {8, 12, 1}, {10, 12, 1},                               // sur (centro)
                {14, 12, 1}, {17, 12, 1}};                             // sureste
        for (int[] c : camas) {
            bed(level, new BlockPos(bx + c[0], y1, bz + c[1]), c[2] < 0 ? Direction.NORTH : Direction.SOUTH);
        }
        // LAS ARCAS de cada cuarto (una por cuarto, en su esquina).
        int[][] arcas = {{5, 1}, {7, 1}, {13, 1}, {5, 10}, {11, 10}, {17, 10}};
        for (int[] a : arcas) {
            Direction mira = a[1] < 7 ? Direction.NORTH : Direction.SOUTH;
            colocar(level, new BlockPos(bx + a[0], y1, bz + a[1]),
                    Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, mira), 3);
        }
    }

    /** Un muro de la posada con su puerta (o sin ella): tablones de roble oscuro y, si toca, la puerta. */
    private static void muroDeCuarto(ServerLevel level, int x, int z, int y1, int alto, BlockState tablon,
                                     boolean conPuerta, Direction mira) {
        for (int k = 0; k < alto; k++) {
            if (conPuerta && k <= 1) {
                colocar(level, new BlockPos(x, y1 + k, z), Blocks.DARK_OAK_DOOR.defaultBlockState()
                        .setValue(DoorBlock.FACING, mira)
                        .setValue(DoorBlock.HALF, k == 0 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER), 3);
            } else {
                colocar(level, new BlockPos(x, y1 + k, z), tablon, 3);
            }
        }
    }

    /** Una <b>mesa</b> del comedor: poste de valla con su plato (placa) y cuatro <b>sillas</b> de escalera alrededor. */
    private static void mesaConSillas(ServerLevel level, BlockPos centro) {
        colocar(level, centro, Blocks.DARK_OAK_FENCE.defaultBlockState(), 3);
        colocar(level, centro.above(), Blocks.OAK_PRESSURE_PLATE.defaultBlockState(), 3);
        // OJO con la orientación de las sillas: en una escalera del juego la cara alta (la que marca FACING) es el
        // respaldo, así que la silla "mira" al lado contrario. El respaldo va del lado de FUERA, para que quien se
        // siente quede de cara a la mesa (antes estaban justo al revés, de espaldas).
        Object[][] sillas = {{1, 0, Direction.EAST}, {-1, 0, Direction.WEST},
                {0, 1, Direction.SOUTH}, {0, -1, Direction.NORTH}};
        for (Object[] s : sillas) {
            // lint:ok I1 porque la Y de `centro` es la COTA de la aldea que le pasa `taberna()` (no la del centro
            // del objetivo): la mesa se coloca a la capa que se pisa.
            BlockPos p = new BlockPos(centro.getX() + (int) s[0], centro.getY(), centro.getZ() + (int) s[1]);
            colocar(level, p, Blocks.DARK_OAK_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, (Direction) s[2])
                    .setValue(StairBlock.HALF, Half.BOTTOM), 3);
        }
    }

    /** El <b>techo de la posada</b>: los tablones de los que cuelgan los faroles y sobre los que se apoya el tejado. */
    private static void techoDeLaPosada(ServerLevel level, int bx, int bz, int yTecho) {
        for (int dx = 0; dx < TABERNA_ANCHO; dx++) {
            for (int dz = 0; dz < TABERNA_FONDO; dz++) {
                colocar(level, new BlockPos(bx + dx, yTecho - 1, bz + dz),
                        Blocks.DARK_OAK_PLANKS.defaultBlockState(), 3);
            }
        }
    }

    /**
     * El <b>tejado a dos aguas</b>: la cumbrera va en el eje X, en medio del fondo, y las dos vertientes bajan hasta
     * el alero (que <b>vuela</b> dos bloques por fuera de los muros de la posada). Cada vertiente es una escalera de
     * tejas —con la cara alta mirando a la cumbrera, que es hacia donde sube— y el hueco de dentro va <b>macizo</b>,
     * para que no quede una buhardilla a oscuras donde críen los bichos. Los <b>frontones</b> (este y oeste) se
     * cierran con cal y entramado, con su ventana.
     * <p>
     * Desde la migración 51 ese relleno interior <b>se vacía</b> después ({@link #desvanDeLaTaberna}) para que el
     * hueco sea un <b>tercer piso</b>: la forma del tejado (cumbrera y capas) la comparten los dos métodos.
     */
    private static void tejadoDeLaTaberna(ServerLevel level, int bx, int bz, int fondo, int yTecho) {
        int ancho = TABERNA_ANCHO;
        int cumbrera = TABERNA_CUMBRERA;
        int pasos = TABERNA_TEJADO_PASOS;   // hasta que las dos vertientes se juntan en la cumbrera
        for (int p = 0; p < pasos; p++) {
            int y = yTecho + p;
            int zN = bz - TABERNA_VUELO - 1 + p;
            int zS = bz + fondo + TABERNA_VUELO - p;
            for (int dx = -TABERNA_VUELO - 1; dx <= ancho + TABERNA_VUELO; dx++) {
                colocar(level, new BlockPos(bx + dx, y, zN), Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState()
                        .setValue(StairBlock.FACING, Direction.SOUTH).setValue(StairBlock.HALF, Half.BOTTOM), 3);
                colocar(level, new BlockPos(bx + dx, y, zS), Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState()
                        .setValue(StairBlock.FACING, Direction.NORTH).setValue(StairBlock.HALF, Half.BOTTOM), 3);
                // El relleno macizo va SOLO por dentro de los frontones: en la columna de fuera (la que vuela sobre
                // el frontón) se dejan los dos bordes de teja y nada más, que si no el alero tapa la cal y el
                // entramado del frontón (y el frontón es lo bonito de una casa con entramado).
                if (dx > -TABERNA_VUELO - 1 && dx < ancho + TABERNA_VUELO) {
                    for (int z = zN + 1; z <= zS - 1; z++) {
                        colocar(level, new BlockPos(bx + dx, y, z), Blocks.DEEPSLATE_TILES.defaultBlockState(), 3);
                    }
                }
            }
        }
        for (int dx = -TABERNA_VUELO - 1; dx <= ancho + TABERNA_VUELO; dx++) {
            colocar(level, new BlockPos(bx + dx, yTecho + pasos, bz + cumbrera),
                    Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState(), 3);
        }
        // Los frontones: el triángulo que cierra el tejado por el este y por el oeste.
        for (int dz = -TABERNA_VUELO; dz <= fondo + TABERNA_VUELO - 1; dz++) {
            int arriba = Math.min(Math.min(dz + TABERNA_VUELO + 1, fondo + TABERNA_VUELO - dz), pasos - 1);
            for (int y = yTecho; y < yTecho + arriba; y++) {
                for (int dx : new int[]{-TABERNA_VUELO, ancho + TABERNA_VUELO - 1}) {
                    BlockState estado;
                    if (y == yTecho || y == yTecho + arriba - 1 || (y - yTecho) % 3 == 0) {
                        estado = Blocks.DARK_OAK_PLANKS.defaultBlockState();
                    } else if (y == yTecho + 2 && Math.abs(dz - cumbrera) <= 1) {
                        estado = Blocks.GLASS.defaultBlockState(); // la ventana del fronton, de cristal entero
                    } else {
                        estado = Blocks.SMOOTH_QUARTZ.defaultBlockState(); // la cal de los frontones (ver `muroTudor`)
                    }
                    colocar(level, new BlockPos(bx + dx, y, bz + dz), estado, 3);
                }
            }
        }
    }

    /**
     * El <b>DESVÁN</b> de la taberna (migración 51): el hueco bajo el tejado a dos aguas, <b>vaciado</b> y amueblado
     * como <b>tercer piso</b> del jugador. Lo pidió él: <i>"el cobertizo (el techo de color negro) está todo relleno
     * con bloques; estaría bien que sirviera como un 3er piso donde el jugador pueda establecerse, que tenga todo lo
     * necesario para ser una base, sin modificar la apariencia externa... y escaleras para llegar ahí (dentro de la
     * taberna, no fuera)"</i>.
     * <p>
     * <b>La apariencia de fuera no se toca, celda por celda</b>: del tejado solo se quitan las <b>tejas de relleno</b>
     * ({@code DEEPSLATE_TILES}) del interior (dx 1..17), nunca las escaleras de las dos vertientes, ni la cumbrera,
     * ni las columnas de los frontones (dx=0 y dx=18: la cal, el entramado y sus cristales se quedan tal cual). El
     * <b>suelo</b> del desván ya estaba puesto: es la <b>placa del tejado</b> ({@code yTecho}) con los tablones del
     * techo de la posada justo debajo ({@link #techoDeLaPosada}, en {@code yTecho - 1}), así que el desván <b>se pisa
     * en {@code yTecho + 1}</b> y no hay que añadir suelo.
     * <p>
     * <b>Se sube por dentro</b> y <b>sin tocar la galería</b> (migración 52): {@code DESVAN_ESCALONES} escalones de
     * medio bloque en <b>L</b> dentro del <b>cuarto suroeste</b> de la posada —el cuarto que se sacrifica para
     * meterlos ({@link #escaleraDelDesvan})— que atraviesan las <b>dos capas</b> del forjado (los tablones del techo
     * de la posada y la placa de tejas) por un hueco que cubre lo que se sube —la regla de I16: si el hueco no llega,
     * el que sube se golpea la cabeza contra el borde—. La cara alta del último escalón queda a la cota del suelo del
     * desván, así que al final del tramo se sale andando, sin saltar.
     */
    private static void desvanDeLaTaberna(ServerLevel level, int bx, int bz, int nivel) {
        int y1 = nivel + TABERNA_PISO2;      // donde se anda en la posada
        int yTecho = y1 + TABERNA_ALERO;     // la placa del tejado: el suelo del desván (se pisa en yTecho + 1)
        // 1) EL INTERIOR DEL TEJADO, VACÍO. Solo tejas de relleno: las escaleras de las vertientes, la cumbrera, los
        //    frontones y su ventana se quedan celda por celda iguales, así que desde fuera se ve idéntico.
        int vaciados = 0;
        for (int dx = 1; dx <= TABERNA_ANCHO - 2; dx++) {
            for (int dz = 0; dz < TABERNA_FONDO; dz++) {
                for (int y = yTecho + 1; y <= yTecho + TABERNA_TEJADO_PASOS; y++) {
                    BlockPos relleno = new BlockPos(bx + dx, y, bz + dz);
                    if (!level.getBlockState(relleno).is(Blocks.DEEPSLATE_TILES)) {
                        continue;
                    }
                    colocar(level, relleno, Blocks.AIR.defaultBlockState(), 3);
                    vaciados++;
                }
            }
        }
        // 2) LA ESCALERA, EN EL CUARTO SUROESTE (migración 52; la de la 51 subía por el carril norte de la galería y
        //    tapaba el corredor de los cuartos). No ocupa ni una celda de la galería: se sube por dentro del cuarto.
        escaleraDelDesvan(level, bx, bz, y1);
        // 3) EL HUECO DE SUBIDA, encima de los escalones, en LAS DOS capas del forjado. Va después de la escalera
        //    porque solo se abren los TABLONES del techo de la posada y las TEJAS de la placa: lo demás no se toca.
        int abiertos = abrirElHuecoDelDesvan(level, bx, bz, y1);
        // 4) EL MOBILIARIO (una base para el jugador), con la cama que se recoloca del cuarto sacrificado.
        amueblarElDesvan(level, bx, bz, yTecho + 1);
        if (vaciados > 0 || abiertos > 0) {
            DevilRpg.LOGGER.info("[Village] Taberna en {}: desvan vaciado ({} teja(s) de relleno) y hueco de subida"
                    + " abierto ({} celda(s)); se pisa en y={}", new BlockPos(bx, yTecho + 1, bz), vaciados, abiertos,
                    yTecho + 1);
        }
    }

    /**
     * La <b>escalera del desván</b>: una <b>L de dos tramos</b> dentro del cuarto suroeste de la posada (ver las
     * constantes {@code DESVAN_ESCALERA_*}). El primer tramo sube <b>de sur a norte</b> por la columna {@code dx=5} y
     * el segundo <b>de este a oeste</b> por la fila {@code dz=10}, pegada al muro que cierra el pozo.
     * <p>
     * <b>OJO con la orientación</b> (igual que en {@code escaleraDeLaTaberna}): en las escaleras del juego la cara
     * alta —por donde se sube— es la que marca {@code FACING}, así que cada tramo mira hacia donde <b>sube</b> (el de
     * abajo al norte, el de arriba al oeste).
     */
    private static void escaleraDelDesvan(ServerLevel level, int bx, int bz, int y1) {
        int tramoNorte = DESVAN_ESCALERA_PIE_Z - DESVAN_ESCALERA_Z_ALTO + 1;   // el tramo de abajo
        for (int[] c : celdasDeLaEscaleraDelDesvan()) {
            Direction sube = c[2] < tramoNorte ? Direction.NORTH : Direction.WEST;
            colocar(level, new BlockPos(bx + c[0], y1 + c[2], bz + c[1]),
                    Blocks.DARK_OAK_STAIRS.defaultBlockState()
                            .setValue(StairBlock.FACING, sube).setValue(StairBlock.HALF, Half.BOTTOM), 3);
        }
    }

    /**
     * Las celdas de la <b>escalera del desván</b>, en orden de subida: cada fila es {@code {dx, dz, escalón}} y la Y
     * de ese escalón es {@code y1 + escalón}. Se calculan en un solo sitio para que la escalera y su <b>hueco</b>
     * ({@link #abrirElHuecoDelDesvan}) no se puedan quedar desparejados (invariante I16: el hueco cubre lo que se
     * sube). El segundo tramo empieza justo donde acaba el primero, con un escalón más de altura y doblando al oeste.
     */
    private static int[][] celdasDeLaEscaleraDelDesvan() {
        int tramoNorte = DESVAN_ESCALERA_PIE_Z - DESVAN_ESCALERA_Z_ALTO + 1;
        int[][] celdas = new int[DESVAN_ESCALONES][];
        for (int k = 0; k < tramoNorte; k++) {                              // 1) de sur a norte por dx=5
            celdas[k] = new int[]{DESVAN_ESCALERA_PIE_DX, DESVAN_ESCALERA_PIE_Z - k, k};
        }
        for (int k = 0; k < DESVAN_ESCALONES - tramoNorte; k++) {            // 2) de este a oeste por dz=10
            celdas[tramoNorte + k] = new int[]{
                    DESVAN_ESCALERA_PIE_DX - 1 - k, DESVAN_ESCALERA_Z_ALTO, tramoNorte + k};
        }
        return celdas;
    }

    /**
     * El <b>hueco de subida</b> al desván (invariante I16): por encima de <b>cada</b> escalón se abren las dos celdas
     * que ocupa el que sube —la de la cabeza y la de encima— en <b>las dos capas</b> del forjado, los tablones del
     * techo de la posada ({@code yTecho - 1}) y la placa de tejas del suelo del desván ({@code yTecho}). Solo se
     * quitan esas dos capas: el tejado de verdad, un tabique o el mobiliario <b>no</b> se tocan.
     *
     * @return cuántas celdas se abrieron (0 si el hueco ya estaba hecho: es idempotente)
     */
    private static int abrirElHuecoDelDesvan(ServerLevel level, int bx, int bz, int y1) {
        int abiertos = 0;
        for (int[] c : celdasDeLaEscaleraDelDesvan()) {
            for (int dy = 1; dy <= 2; dy++) {
                BlockPos hueco = new BlockPos(bx + c[0], y1 + c[2] + dy, bz + c[1]);
                BlockState actual = level.getBlockState(hueco);
                if (actual.is(Blocks.DARK_OAK_PLANKS) || actual.is(Blocks.DEEPSLATE_TILES)) {
                    colocar(level, hueco, Blocks.AIR.defaultBlockState(), 3);
                    abiertos++;
                }
            }
        }
        return abiertos;
    }

    /**
     * El <b>mobiliario del desván</b>: lo justo para que sea una base del jugador (dos camas, mesa de trabajo, horno,
     * dos cofres —pegados, que se juntan en uno doble—, yunque, un par de faroles y un par de alfombras). <b>Nada de
     * puestos de trabajo de aldeano</b> (barril, caldero, ahumador, alto horno, mesa de herrería, muela, telar, atril,
     * compostero, cortapiedras, soporte de pociones ni campana): un aldeano sin oficio los reclamaría. La cama, el
     * cofre, el <b>horno normal</b> (el del cocinero es el ahumador), la mesa de trabajo y el yunque <b>no</b> son
     * puestos de trabajo; la cama sí es POI, pero es justo lo que el jugador quiere en su base.
     * <p>
     * La <b>segunda cama</b> es la que se <b>recoloca</b> del cuarto suroeste (migración 52), el que se sacrifica para
     * meter la escalera del desván: en vanilla cada cría necesita una <b>cama libre</b>, así que el pueblo no puede
     * perder ninguna. Va pegada a la del desván, con la cabecera al mismo lado.
     * <p>
     * Todo se coloca <b>solo si la celda está vacía</b> ({@link #colocarSiEstaVacio}): así la reparación de la
     * migración es idempotente y no le tira al jugador lo que tenga dentro (reemplazar un cofre tira su contenido).
     * <p>
     * El reparto deja libre el pasillo central (dz=5..9, que es donde el tejado es más alto) y amontona lo demás en
     * la fila norte (dz=3), que tiene tres bloques de alto; los dos faroles van sobre postes de tronco pegados a los
     * frontones (dx=1 y dx=17), en la fila de la <b>cumbrera</b> (dz=7), que es donde el tejado está más alto y el
     * poste con su farol caben.
     */
    private static void amueblarElDesvan(ServerLevel level, int bx, int bz, int y) {
        // LAS DOS CAMAS. La primera es la del desván; la segunda, la que se recoloca del cuarto suroeste (migración
        // 52), para que el pueblo no pierda una cama. La almohada va al lado que marca FACING.
        BlockPos pie = new BlockPos(bx + 4, y, bz + 3);
        if (level.getBlockState(pie).isAir() && level.getBlockState(pie.relative(Direction.SOUTH)).isAir()) {
            bed(level, pie, Direction.SOUTH);
        }
        BlockPos pieRecolocada = new BlockPos(bx + 5, y, bz + 3);
        if (level.getBlockState(pieRecolocada).isAir()
                && level.getBlockState(pieRecolocada.relative(Direction.SOUTH)).isAir()) {
            bed(level, pieRecolocada, Direction.SOUTH);
        }
        // LA MESA DE TRABAJO y EL HORNO.
        colocarSiEstaVacio(level, new BlockPos(bx + 6, y, bz + 3), Blocks.CRAFTING_TABLE.defaultBlockState());
        colocarSiEstaVacio(level, new BlockPos(bx + 7, y, bz + 3), Blocks.FURNACE.defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,
                        Direction.SOUTH));
        // LOS DOS COFRES, pegados el uno al otro y mirando al pasillo.
        for (int dx : new int[]{9, 10}) {
            colocarSiEstaVacio(level, new BlockPos(bx + dx, y, bz + 3),
                    Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
        }
        // EL YUNQUE, aparte de la fila, para poder rodearlo.
        colocarSiEstaVacio(level, new BlockPos(bx + 12, y, bz + 3), Blocks.ANVIL.defaultBlockState());
        // LOS DOS FAROLES sobre un poste de TRONCO de roble oscuro (como el entramado de abajo), en la fila de la
        // cumbrera (dz=7: la que tiene el tejado más alto, así que el poste y su farol caben). El ayudante
        // `farolSobreElPoste` recibe la casilla del APOYO y garantiza el poste y el farol encima (invariante I14),
        // así que aquí no puede quedar un farol colgado del aire.
        for (int dx : new int[]{1, TABERNA_ANCHO - 2}) {
            BlockPos poste = new BlockPos(bx + dx, y, bz + TABERNA_CUMBRERA);
            colocarSiEstaVacio(level, poste, Blocks.DARK_OAK_LOG.defaultBlockState());
            farolSobreElPoste(level, poste);
        }
        // Y LA ALFOMBRA (roja, como las camas de la posada), delante del taller.
        colocarSiEstaVacio(level, new BlockPos(bx + 6, y, bz + 4), Blocks.RED_CARPET.defaultBlockState());
        colocarSiEstaVacio(level, new BlockPos(bx + 7, y, bz + 4), Blocks.RED_CARPET.defaultBlockState());
    }

    /**
     * El <b>porche</b> de la puerta (al oeste, dando a la plaza): dos postes, el <b>toldo</b> que baja hacia fuera, la
     * <b>enseña</b> de la taberna colgada con su farol y un par de <b>pipas</b> al lado de la puerta. Es lo primero
     * que se ve al llegar al pueblo.
     */
    private static void porcheDeLaTaberna(ServerLevel level, int bx, int bz, int nivel) {
        int pz = TABERNA_PUERTA;
        for (int dz : new int[]{pz - 3, pz + 3}) {
            for (int k = 0; k <= 2; k++) {
                colocar(level, new BlockPos(bx - 3, nivel + k, bz + dz), Blocks.DARK_OAK_FENCE.defaultBlockState(), 3);
            }
        }
        for (int dz = pz - 3; dz <= pz + 3; dz++) {
            colocar(level, new BlockPos(bx - 2, nivel + TABERNA_PISO2 - 1, bz + dz),
                    Blocks.DARK_OAK_STAIRS.defaultBlockState()
                            .setValue(StairBlock.FACING, Direction.EAST).setValue(StairBlock.HALF, Half.BOTTOM), 3);
            colocar(level, new BlockPos(bx - 3, nivel + TABERNA_PISO2 - 2, bz + dz),
                    Blocks.DARK_OAK_STAIRS.defaultBlockState()
                            .setValue(StairBlock.FACING, Direction.EAST).setValue(StairBlock.HALF, Half.BOTTOM), 3);
        }
        for (int dz = pz - 1; dz <= pz + 1; dz++) {
            colocar(level, new BlockPos(bx - 2, nivel + TABERNA_PISO2 - 2, bz + dz),
                    Blocks.DARK_OAK_PLANKS.defaultBlockState(), 3);
        }
        colocar(level, new BlockPos(bx - 2, nivel + TABERNA_PISO2 - 3, bz + pz),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 3);
        colocar(level, new BlockPos(bx - 3, nivel + TABERNA_PISO2 - 2, bz + pz - 3),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 3);
        colocar(level, new BlockPos(bx - 3, nivel + TABERNA_PISO2 - 2, bz + pz + 3),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 3);
        colocar(level, new BlockPos(bx - 2, nivel, bz + pz - 2), Blocks.OAK_WOOD.defaultBlockState(), 3);
        colocar(level, new BlockPos(bx - 2, nivel, bz + pz + 2), Blocks.OAK_WOOD.defaultBlockState(), 3);
    }

    /** Un farol <b>colgado</b> (de un bloque sólido que tiene encima). */
    private static void colgar(ServerLevel level, BlockPos pos) {
        colocar(level, pos, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 3);
    }

    /**
     * Las <b>luces</b> de la taberna: faroles colgados del forjado en el comedor, del techo en la galería y en los
     * cuartos, y un par bajo el toldo del porche. Una taberna a oscuras es una taberna con bichos dentro.
     */
    private static void lucesDeLaTaberna(ServerLevel level, int bx, int bz, int nivel, int y1, int yTecho) {
        int[][] comedor = {{2, 3}, {5, 3}, {9, 3}, {13, 3}, {16, 3}, {4, 7}, {9, 7}, {13, 7}, {16, 8},
                {4, 11}, {9, 12}, {14, 12}, {16, 12}};
        for (int[] l : comedor) {
            colgar(level, new BlockPos(bx + l[0], nivel + TABERNA_PISO2 - 2, bz + l[1]));
        }
        int[][] posada = {{3, 3}, {9, 3}, {16, 3}, {4, 12}, {9, 12}, {16, 12}, {6, 7}, {12, 7},
                {TABERNA_ESCALERA_X, TABERNA_ESCALERA_TOPE_Z + 2},
                {TABERNA_ESCALERA_X + 1, TABERNA_ESCALERA_TOPE_Z + 2}};
        for (int[] l : posada) {
            colgar(level, new BlockPos(bx + l[0], yTecho - 2, bz + l[1]));
        }
        // La galería va a media altura entre los dos pisos: su farol cuelga del techo de la posada.
        for (int dx : new int[]{3, 9, 15}) {
            colgar(level, new BlockPos(bx + dx, yTecho - 2, bz + 7));
        }
    }

    /**
     * Despeja el <b>solar de la taberna</b> antes de levantarla. Cubre de sobra la taberna vieja (que cabía dentro de
     * la nueva), así que aquí se tiran de una vez sus muros, su forjado y su tejado.
     * <p>
     * Lo que hubiera en <b>cofres</b> se guarda ANTES en el <b>almacén del pueblo</b>: tirar un cofre tira su
     * contenido al suelo (mecánica del juego) y el pueblo no puede perder lo que tenía guardado.
     * <p>
     * <b>OJO con el almacén NUEVO</b>, que desde la migración 45 está al lado (a 3 bloques de la esquina este): el
     * despeje llega justo hasta el borde del tejado (un bloque por fuera del forjado), ni uno más, para no rozarle ni
     * la columna oeste de su cobertizo.
     */
    private static void despejarSolarDeLaTaberna(ServerLevel level, BlockPos center, int bx, int bz, int nivel,
                                                 int hastaY) {
        for (int dx = -TABERNA_VUELO - 3; dx <= TABERNA_ANCHO + TABERNA_VUELO; dx++) {
            for (int dz = -TABERNA_VUELO - 2; dz <= TABERNA_FONDO + TABERNA_VUELO + 1; dz++) {
                for (int y = nivel; y <= hastaY; y++) {
                    BlockPos p = new BlockPos(bx + dx, y, bz + dz);
                    if (level.getBlockState(p).isAir()) {
                        continue;
                    }
                    if (level.getBlockEntity(p) instanceof Container contenedor) {
                        guardarEnElAlmacen(level, center, contenedor);
                    }
                    colocar(level, p, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    /**
     * Vacía un contenedor en el <b>almacén del pueblo</b>. Lo que no quepa ahí se deja caer en el almacén: el
     * recolector del pueblo lo recoge y lo guarda (mejor eso que perderlo al tirar el cofre donde estaba).
     */
    private static void guardarEnElAlmacen(ServerLevel level, BlockPos center, Container contenedor) {
        for (int i = 0; i < contenedor.getContainerSize(); i++) {
            ItemStack pila = contenedor.getItem(i);
            if (pila.isEmpty()) {
                continue;
            }
            ItemStack sobra = VillageStorage.guardar(level, center, pila.copy());
            contenedor.setItem(i, ItemStack.EMPTY);
            if (!sobra.isEmpty()) {
                Block.popResource(level, VillageStorage.centro(center), sobra);
            }
        }
        contenedor.setChanged();
    }

    /**
     * <b>Retira el cofre de la despensa del kiosco</b> (migración 47): la comida del pueblo vive desde aquí en el
     * <b>almacén de comida de la taberna</b> (la cocina). Lo que hubiera en el cofre viejo <b>no se pierde</b>: se
     * pasa primero a la despensa nueva y lo que no quepa (o si la taberna todavía no está) al <b>almacén</b> del
     * pueblo; solo entonces se retiran las dos mitades del cofre.
     * <p>
     * Va <b>después</b> del almacén y <b>después</b> de la taberna, para que el destino exista. Es <b>idempotente</b>:
     * si el cofre viejo ya no está, no toca nada. (Las dos mitades de un cofre doble comparten el mismo contenedor,
     * así que los objetos se leen una sola vez.)
     */
    public static void retirarDespensaDelKiosco(ServerLevel level, BlockPos center) {
        int nivel = cotaDeLaPlaza(level, center);
        java.util.List<BlockPos> viejos = new java.util.ArrayList<>();
        for (int dx = 0; dx <= 1; dx++) {
            BlockPos p = new BlockPos(center.getX() + dx, nivel + 1, center.getZ() + 1);
            if (level.getBlockState(p).getBlock() instanceof ChestBlock) {
                viejos.add(p);
            }
        }
        if (viejos.isEmpty()) {
            return;   // ya se retiró (o esa aldea nunca tuvo el cofre en el kiosco)
        }
        Container nueva = VillagePantry.despensaDeLaTaberna(level, center);
        Container viejo = ChestBlock.getContainer((ChestBlock) level.getBlockState(viejos.get(0)).getBlock(),
                level.getBlockState(viejos.get(0)), level, viejos.get(0), true);
        int movidos = 0;
        if (viejo != null) {
            for (int i = 0; i < viejo.getContainerSize(); i++) {
                ItemStack pila = viejo.getItem(i);
                if (pila.isEmpty()) {
                    continue;
                }
                ItemStack resto = pila.copy();
                if (nueva != null) {
                    resto = VillagePantry.guardar(nueva, resto);   // primero la despensa nueva
                }
                if (!resto.isEmpty()) {
                    resto = VillageStorage.guardar(level, center, resto);   // y lo que no quepa, al almacén
                }
                int puestos = pila.getCount() - resto.getCount();
                movidos += puestos;
                if (puestos > 0) {
                    pila.shrink(puestos);
                    viejo.setItem(i, pila.isEmpty() ? ItemStack.EMPTY : pila);
                }
            }
            viejo.setChanged();
        }
        for (BlockPos p : viejos) {
            colocar(level, p, Blocks.AIR.defaultBlockState(), 3);
        }
        DevilRpg.LOGGER.info("[Village] Aldea en {}: despensa retirada del kiosco ({} objeto(s) pasados a la despensa"
                + " de la taberna o al almacen)", center, movidos);
    }

    /**
     * El <b>camino de la plaza a la taberna</b>: sale de la plaza hacia el sur y luego tuerce al este. No va en
     * recta porque la recta cruza la <b>parcela de la granja</b> (que está en medio), y un camino no debe pisar los
     * cultivos; muere en la puerta oeste, que es la que da a la plaza.
     */
    public static void caminoALaTaberna(ServerLevel level, BlockPos center) {
        BlockPos esquina = center.offset(2, 0, 24);
        line(level, center, esquina);
        BlockPos puerta = puertaDeLaTaberna(level, center);
        line(level, esquina, new BlockPos(puerta.getX() - 2, puerta.getY(), puerta.getZ()));
    }

    /**
     * Asegura la <b>taberna</b> en aldeas ya construidas. Es <b>idempotente</b>: se comprueba por sus cuatro postes
     * de esquina, que en esta taberna son de <b>roble oscuro</b> (la vieja era de roble claro). Se llama al generar,
     * en la migración y en el latido, como el resto de edificios del pueblo.
     * <p>
     * Si la aldea todavía tiene la <b>taberna vieja</b> (13x12, con la escalera que no se podía subir), la prueba
     * falla y aquí se levanta la nueva: su solar se despeja entero —la vieja cabía dentro— y lo que hubiera en sus
     * cofres se guarda antes en el almacén.
     */
    public static void asegurarTaberna(ServerLevel level, BlockPos center) {
        int nivel = cotaDeLaPlaza(level, center);
        if (nivel <= level.getMinBuildHeight() + 1) {
            return;
        }
        if (!tabernaConstruida(level, center)) {
            BlockPos base = baseDeLaTaberna(center);
            taberna(level, center, base, nivel);
            DevilRpg.LOGGER.info("[Village] Aldea en {}: taberna construida en {} (dos plantas de cal y entramado con"
                    + " vuelo, {}x{})", center, base, TABERNA_ANCHO, TABERNA_FONDO);
        }
        // La remesa inicial del ALMACÉN DE COMIDA (semillas para sembrar, abono y un par de panes) se asegura aquí:
        // la despensa del pueblo vive en la cocina de la taberna desde la migración 46 (`VillagePantry`). Si ya tiene
        // cosas dentro no se le añade nada (ver `remesaInicial`), así que llamarlo siempre es seguro.
        VillagePantry.remesaInicial(VillagePantry.despensa(level, center));
    }

    /**
     * Convierte la aldea en <b>ruinas</b> (Iteración 3): se derrumba parte de lo construido y el sitio se llena
     * de telarañas y piedra mohosa. Es <b>determinista</b> (semilla sacada del objetivo), así que la misma aldea
     * caída se ve igual siempre, y solo se llama una vez: al caer la aldea.
     */
    public static void ruin(ServerLevel level, BlockPos center, int objectiveIndex) {
        RandomSource random = RandomSource.create(objectiveIndex * 31L + 7L);
        int base = spawnY(level, center.getX(), center.getZ());
        int cambiados = 0;
        // Tope de cambios: la pasada es una sola vez, pero no queremos clavar el servidor con 30.000 bloques.
        int maxCambios = 2500;
        for (int dx = -FENCE_RADIUS; dx <= FENCE_RADIUS && cambiados < maxCambios; dx++) {
            for (int dz = -FENCE_RADIUS; dz <= FENCE_RADIUS && cambiados < maxCambios; dz++) {
                if (dx * dx + dz * dz > FENCE_RADIUS * FENCE_RADIUS) {
                    continue;
                }
                for (int dy = 0; dy <= 7; dy++) {
                    BlockPos pos = new BlockPos(center.getX() + dx, base + dy, center.getZ() + dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || state.is(Blocks.BEDROCK)) {
                        continue;
                    }
                    float r = random.nextFloat();
                    if (r < 0.35F) {
                        colocar(level, pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    } else if (r < 0.45F) {
                        colocar(level, pos, Blocks.COBWEB.defaultBlockState(), Block.UPDATE_CLIENTS);
                    } else if (r < 0.55F) {
                        colocar(level, pos, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                    } else if (r < 0.62F) {
                        colocar(level, pos, Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), Block.UPDATE_CLIENTS);
                    } else {
                        continue;
                    }
                    cambiados++;
                }
            }
        }
        DevilRpg.LOGGER.info("[Village] Aldea {} queda en ruinas: {} bloques cambiados", objectiveIndex, cambiados);
    }
}
