package com.chipoodle.devilrpg.world;

import com.chipoodle.devilrpg.DevilRpg;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
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
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FurnaceBlock;
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
     * <b>El sitio de cada edificio de planta</b>, relativo al centro del pueblo: es lo que lee {@link #trazado} por
     * <b>índice</b> (los índices son los que usa el código, no un censo de todo lo que hay en la aldea).
     * <p>
     * Antes eran las coordenadas del trazado de radio 36 y todas cabían apretadas en el centro; con el muro a 62 se
     * han <b>repartido</b> por el recinto (cada cuadrante a su aire, sin solaparse con el almacén, que sigue pegado a
     * la plaza) para que el pueblo <b>llene</b> la muralla. Reparto actual:
     * <ul>
     *   <li><b>Casas</b> (0..3): (-36,-7) oeste, (28,-16) este-norte, (-9,36) sur, (12,-32) norte (la grande).</li>
     *   <li><b>Iglesia</b> (4) (-21,-45) y <b>taller de los herreros</b> (5) (3,-47), al norte.</li>
     *   <li><b>Barraca de la milicia</b> (6) (-45,22), al suroeste.</li>
     *   <li><b>Pesquera</b> (7) (20,44), en el campo del sureste: su lago y, diez al norte, la caseta del pescador.</li>
     * </ul>
     * <p>
     * <b>Y AQUÍ SOLO ESTÁN LOS QUE SE COLOCAN POR ESTA TABLA.</b> Los demás sitios del pueblo tienen <b>su propia
     * constante</b>, que es donde hay que mirarlos (un número en dos sitios se desincroniza, I4):
     * <ul>
     *   <li><b>Parcelas de la granja</b> ({@link #FARM_PLOTS}, que además lleva el tamaño y la acequia): sus tres
     *       esquinas estuvieron <b>también</b> aquí (tres filas que nadie leía) y se quitaron para que el sitio de la
     *       huerta viva en un solo sitio.</li>
     *   <li><b>Almacén</b>: {@code VillageStorage.OFFSET} (y no se mueve: moverlo dejaría sus cofres —y lo que hay
     *       dentro— tirados por el recinto viejo).</li>
     *   <li><b>Corral anexo</b>: {@link #ANEXO_DX} (va con {@link #baseDeAnexo}). <b>Taberna</b>:
     *       {@code baseDeLaTaberna}. <b>Arboleda</b>: {@link #PUNTOS_DE_LA_ARBOLEDA}.</li>
     * </ul>
     * OJO con esto: el jugador lo preguntó al revisar el código —*"la constante TRAZADO no tiene la choza del
     * pescador, ¿por qué?"*— y la respuesta era que la pesquera (etapa G) se hizo con su propia copia de las
     * coordenadas; ahora su sitio vive <b>solo aquí</b> ({@link #PESQUERA} lee esta fila).
     */
    private static final int[][] TRAZADO = {
            // x, z  y qué es (para leerlo de un vistazo). Los índices los usa `trazado(center, i)`.
            {-36, -7},   // 0: casa 1 (oeste)
            {28, -16},   // 1: casa 2 (este)
            {-9, 36},    // 2: casa 3 (sur)
            {12, -32},   // 3: casa 4 (norte, la grande)
            {-21, -45},  // 4: iglesia
            {3, -47},    // 5: taller de los herreros
            {-45, 22},   // 6: barraca de la milicia
            {20, 44},    // 7: pesquera (el lago; la caseta va diez celdas al norte)
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
    /**
     * A cuántos bloques de la <b>esquina oeste</b> de la parcela va el <b>compostero</b> (el puesto de trabajo del
     * granjero), <b>fuera</b> de la valla del bancal.
     * <p>
     * Eran <b>2</b> y es un error de <b>una celda</b> con consecuencias: la valla está en {@code corner.x-1}, así que
     * el compostero quedaba <b>pegado</b> a ella y su tapa (un bloque entero) queda a {@code cota+1}: desde ahí, subir
     * al lomo de la valla (1,5) es un paso de <b>0,5</b>, por debajo del {@code maxUpStep} del juego (0,6), así que el
     * granjero <b>trepaba la valla</b> en vez de entrar por la compuerta (lo reportó el jugador: *"siguen subiendo a
     * la valla para poder entrar en vez de usar las compuertas"*; medido en su guardado: los <b>tres</b> bancales
     * tenían ese escalón, y era el compostero). Con <b>3</b>, entre el compostero y la valla queda una celda de aire y
     * el escalón desaparece (I40).
     */
    private static final int COMPOSTERO_DX = 3;
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
        // La cota va con el centro: la grabadora la necesita para dejar el LAGO de la pesquera fuera del plano
        // (ver `esCeldaDelLago`).
        iniciarGrabacion(center, nivelVilla);
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

        // Kiosco de la plaza: plataforma con 4 salidas, la campana EN EL CENTRO y el farol colgado del tejado.
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
        // EL TALLER DEL LEÑADOR (etapa H): junto a la arboleda, con la mesa de flechas (su puesto de trabajo). El
        // leñador es un oficio propio desde la etapa H (antes talaba el recolector).
        asegurarElTallerDelLenador(level, center);
        // LA MINA DEL PUEBLO (etapa I): la caseta del minero (su cortapiedras —el puesto del albañil—, el horno para
        // fundir, la balsa de filtrado y su cama) y la boca del caracol. La caseta es del PUEBLO (entra en el plano:
        // la mantiene el obrero); el pozo y las galerías los cava el minero y quedan fuera del plano (I102).
        asegurarLaMinaDelPueblo(level, center);
        // Y LA ORILLA, seca y pareja, si la aldea nació al nivel del agua (si no, no se toca nada).
        asegurarOrilla(level, center);
        // NIEVE Y VEGETACIÓN QUE QUEDÓ COLGANDO del recorte (aldea de montaña): se limpia antes de dar por hecha la
        // aldea, que si no queda nieve polvo flotando por encima del pueblo (medido: 469 bloques en la suya).
        limpiarRestosColgados(level, center);

        // Remesa inicial de la despensa (semillas, abono y un par de panes): la despensa es el cofre de la cocina de
        // la taberna desde la migración 47 (el kiosco ya no tiene cofre).
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
        /** Centro y cota de la aldea que se está grabando: hacen falta para dejar el LAGO fuera del plano. */
        private final BlockPos center;
        private final int nivel;

        GrabadoraDePlano(BlockPos center, int nivel) {
            this.center = center;
            this.nivel = nivel;
        }

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
         * <p>
         * El <b>lago de la pesquera</b> se descarta aparte, aunque sea agua: lo mantiene
         * {@link #repararLagoDeLaPesquera} (que también vale el hielo de un bioma frío), y en el plano el obrero se
         * pasaría la vida descongelándolo — ver {@link #esCeldaDelLago}.
         */
        VillageSavedData.Blueprint aPlano() {
            List<BlockState> palette = new ArrayList<>();
            Map<BlockState, Integer> indices = new HashMap<>();
            List<Long> posiciones = new ArrayList<>();
            List<Integer> estados = new ArrayList<>();
            for (Map.Entry<Long, BlockState> entrada : bloques.entrySet()) {
                if (esCeldaDelLago(center, nivel, BlockPos.of(entrada.getKey()))) {
                    continue;
                }
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

    private static void iniciarGrabacion(BlockPos center, int nivel) {
        grabadora = new GrabadoraDePlano(center, nivel);
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
                    // El AGUA (y su hielo) no es un hueco que se rellene, igual que en `nivelar`: una terraza que
                    // solape con el lago de la pesquera no puede volver a taparlo (ver `esAguaOHielo`).
                    if (esAguaOHielo(actual)) {
                        continue;
                    }
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

    /** Cuántas parcelas de granja tiene una aldea ({@link #FARM_PLOTS}). */
    public static int parcelasDeGranja() {
        return FARM_PLOTS.length;
    }

    /**
     * La esquina de la parcela {@code i} de esa aldea (a la capa que se pisa). Es la MISMA cuenta que
     * {@link #parcelasDe}, para el que necesite una sola parcela (el granjero, al repartirse los bancales).
     */
    public static BlockPos esquinaDeLaParcela(BlockPos center, int i, int cota) {
        return new BlockPos(center.getX() + FARM_PLOTS[i][0], cota, center.getZ() + FARM_PLOTS[i][1]);
    }

    /**
     * Dónde va el <b>compostero</b> de la parcela {@code i} (el puesto de trabajo del granjero): fuera de su valla,
     * {@link #COMPOSTERO_DX} bloques al oeste de su esquina, a la capa que se pisa.
     * <p>
     * Vive en <b>un solo sitio</b> (I4): lo usan el constructor del bancal, la migración que lo mueve y el granjero
     * para encontrar su compostero y para saber <b>cuál es su bancal</b> (su puesto de trabajo es su compostero, I36).
     */
    public static BlockPos composteroDeLaParcela(BlockPos center, int i, int cota) {
        return new BlockPos(center.getX() + FARM_PLOTS[i][0] - COMPOSTERO_DX, cota, center.getZ() + FARM_PLOTS[i][1]);
    }

    /**
     * Dónde estaba el compostero <b>antes</b> de la migración 64 (a {@code corner.x-2}, <b>pegado a la valla</b>, que
     * es lo que le servía de escalón al granjero para saltarla). Solo lo usa esa migración.
     */
    public static BlockPos composteroViejoDeLaParcela(BlockPos center, int i, int cota) {
        return new BlockPos(center.getX() + FARM_PLOTS[i][0] - 2, cota, center.getZ() + FARM_PLOTS[i][1]);
    }

    /** Códigos de {@link #tipoDeCeldaDeLaHuerta}: fuera de los bancales, celda de cultivo o fila de la acequia. */
    private static final int FUERA_DE_LA_HUERTA = 0;
    private static final int CELDA_DE_CULTIVO = 1;
    private static final int CELDA_DE_ACEQUIA = 2;

    /**
     * ¿Qué es esa casilla dentro de los <b>bancales</b> de la granja? La huerta es <b>geometría fija</b> —las tres
     * parcelas de {@link #FARM_PLOTS}, de {@code PLOT_WIDTH}×{@code PLOT_DEPTH}, con la acequia en
     * {@code PLOT_WATER_ROW}— y su capa de tierra va <b>una por debajo de la cota</b> (los cultivos van a la cota:
     * ver {@link #plot}). Vive en <b>un solo sitio</b> para que el plano, el obrero y el granjero no tengan cada uno
     * su copia del rectángulo (I4): con dos copias, mover una parcela deja a la otra buscando en el sitio viejo.
     */
    private static int tipoDeCeldaDeLaHuerta(BlockPos center, int cota, BlockPos pos) {
        if (pos.getY() != cota - 1) {
            return FUERA_DE_LA_HUERTA;
        }
        for (int[] plot : FARM_PLOTS) {
            int dx = pos.getX() - (center.getX() + plot[0]);
            int dz = pos.getZ() - (center.getZ() + plot[1]);
            if (dx >= 0 && dx < PLOT_WIDTH && dz >= 0 && dz < PLOT_DEPTH) {
                return esFilaDeAcequia(dx, dz) ? CELDA_DE_ACEQUIA : CELDA_DE_CULTIVO;
            }
        }
        return FUERA_DE_LA_HUERTA;
    }

    /**
     * ¿Esa casilla de la parcela es de la <b>acequia</b>? Es la fila del medio, pero <b>sin sus dos extremos</b>
     * ({@code dx = 0} y {@code dx = PLOT_WIDTH-1}): esos dos son <b>celdas de cultivo</b>.
     * <p>
     * El motivo está <b>medido con el arnés</b>: la acequia va <b>tapada con una losa</b> (para que el agua no se
     * congele y para que los aldeanos no se caigan dentro), la losa <b>se pisa</b> a {@code cota+0,5} y las compuertas
     * del bancal caen justo en la fila del medio, así que desde la losa del extremo el aldeano <b>saltaba la valla</b>
     * (de 120,5 a 121,5 hay 1,0, y un mob salta 1,25) en vez de entrar por la compuerta: es la segunda causa del
     * <i>"siguen subiendo a la valla para poder entrar"</i> —medida en el arnés con la granjera Cesarea, que venía por
     * la acequia y saltó por encima de la compuerta este—. Con los extremos como celdas de cultivo (tapa a 119,94) el
     * salto ya no llega (I40). De paso el bancal gana <b>dos celdas plantables</b> por parcela.
     */
    private static boolean esFilaDeAcequia(int dx, int dz) {
        return dz == PLOT_WATER_ROW && dx > 0 && dx < PLOT_WIDTH - 1;
    }

    /**
     * ¿Esa casilla es una <b>celda de cultivo</b> de un bancal (la capa de tierra, sin contar la acequia)? Es la
     * casilla que el pueblo tiene que mantener <b>labrada</b>: vanilla convierte la tierra de cultivo en <b>tierra</b>
     * en cuanto alguien salta encima ({@code FarmBlock.fallOn}) y, sin reponerla, el bancal se queda con calvas que
     * nadie vuelve a labrar (el jugador las describió como <i>"dos espacios que no tienen cultivo y nadie los está
     * reparando"</i>).
     */
    public static boolean esCeldaDeCultivo(BlockPos center, int cota, BlockPos pos) {
        return tipoDeCeldaDeLaHuerta(center, cota, pos) == CELDA_DE_CULTIVO;
    }

    /**
     * El estado <b>bueno</b> de una casilla de bancal (su tierra de cultivo, o la acequia), o {@code null} si esa
     * casilla no es de la huerta. Es lo que el <b>plano</b> tiene que pedir en esas celdas: la huerta la construyó
     * el pueblo, así que entra en el plano <b>aunque el mundo la tenga pisoteada</b> (tierra o césped) en el momento
     * de capturarlo. Hace falta de verdad, y está medido: el plano de una aldea migrada es un <b>escaneo</b> del
     * mundo, y las celdas que ya estaban pisoteadas al capturarlo se descartaban como "terreno natural", así que el
     * obrero <b>no tenía nada que reponer</b> en ellas (aldea 2 del jugador: 2 calvas de césped en el bancal oeste).
     * <p>
     * La <b>humedad</b> no va aquí: es estado transitorio, ver {@link #estadoDelPlano} y {@link #tierraDeCultivo}.
     */
    @Nullable
    public static BlockState estadoDeLaHuerta(BlockPos center, int cota, BlockPos pos) {
        return switch (tipoDeCeldaDeLaHuerta(center, cota, pos)) {
            case CELDA_DE_CULTIVO -> Blocks.FARMLAND.defaultBlockState();
            case CELDA_DE_ACEQUIA -> Blocks.WATER.defaultBlockState();
            default -> null;
        };
    }

    /**
     * La <b>tierra de cultivo como la pondría el juego</b>: regada ({@code moisture} 7) si tiene agua a su nivel o
     * uno por encima en un cuadrado de radio 4 —la misma cuenta que {@code FarmBlock.isNearWater}— y seca si no.
     * Es lo que hay que poner al <b>labrar</b> una calva o al <b>reponer</b> la tierra pisoteada: ponerla seca a
     * secas deja la celda sin regar hasta que el juego la riegue sola (y el cultivo crece más despacio mientras).
     */
    public static BlockState tierraDeCultivo(ServerLevel level, BlockPos pos) {
        BlockState tierra = Blocks.FARMLAND.defaultBlockState();
        for (BlockPos q : BlockPos.betweenClosed(pos.offset(-4, 0, -4), pos.offset(4, 1, 4))) {
            if (tierra.canBeHydrated(level, pos, level.getFluidState(q), q)) {
                return tierra.setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE);
            }
        }
        return tierra;
    }

    /**
     * ¿Ese bloque es el resultado de <b>pisar</b> la tierra de cultivo? Vanilla la convierte en <b>tierra</b> al
     * saltar encima ({@code FarmBlock.fallOn}) y la tierra, pegada al césped, vuelve a ser <b>césped</b>; en una
     * aldea de montaña aparecen además las otras tierras. Es lo que tienen en común las dos mitades del arreglo:
     * el <b>obrero</b> repone la tierra de cultivo que pide el plano y el <b>granjero</b> vuelve a labrar la calva.
     */
    public static boolean esTierraPisoteada(BlockState state) {
        return state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.PODZOL) || state.is(Blocks.ROOTED_DIRT);
    }

    /**
     * ¿Esa celda de bancal es una <b>calva</b>: debería ser tierra de cultivo y ahora es tierra o césped, con la
     * acequia a mano y el hueco de arriba <b>libre</b>? Se exige agua cerca (labrar en seco no sirve de nada) y aire
     * encima: así <b>no se toca nada de lo que crece dentro</b> (I11). Lo usan el granjero (que la labra en su
     * faena) y el reparador de la migración.
     */
    public static boolean esCalvaDeBancal(ServerLevel level, BlockPos tierra) {
        if (!esTierraPisoteada(level.getBlockState(tierra))) {
            return false;
        }
        if (!level.getBlockState(tierra.above()).isAir()) {
            return false;
        }
        return tieneAguaCerca(level, tierra);
    }

    /** ¿Hay agua que <b>riegue</b> esa celda? El agua tiene que estar a su nivel o uno por encima (como vanilla). */
    public static boolean tieneAguaCerca(ServerLevel level, BlockPos pos) {
        BlockState tierra = level.getBlockState(pos);
        for (BlockPos q : BlockPos.betweenClosed(pos.offset(-4, 0, -4), pos.offset(4, 1, 4))) {
            if (tierra.canBeHydrated(level, pos, level.getFluidState(q), q)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Vuelve a <b>labrar</b> las calvas de los bancales de esa aldea (la tierra de cultivo que alguien pisó y el
     * juego convirtió en tierra o césped) y devuelve cuántas labró.
     * <p>
     * Es <b>idempotente</b> y conservador: solo toca celdas de bancal, solo si ahora son tierra o césped, solo si
     * tienen agua cerca y solo si el hueco de arriba está <b>libre</b> —nunca arranca un cultivo ni toca el agua, el
     * compostero, la valla ni lo que haya puesto el jugador (I11)—. Lo llama la <b>migración</b> para las aldeas ya
     * guardadas, cuyo plano se capturó con las calvas dentro; después lo mantiene el <b>granjero</b>.
     */
    public static int labrarCalvasDelBancal(ServerLevel level, BlockPos center) {
        int labradas = 0;
        for (BlockPos parcela : parcelasDe(level, center)) {
            for (int dx = 0; dx < PLOT_WIDTH; dx++) {
                for (int dz = 0; dz < PLOT_DEPTH; dz++) {
                    BlockPos tierra = parcela.offset(dx, -1, dz);
                    if (!esCeldaDeCultivo(center, parcela.getY(), tierra) || !esCalvaDeBancal(level, tierra)) {
                        continue;
                    }
                    // Se pone con `colocar`, igual que cuando el bancal se construye (ver `plot`).
                    colocar(level, tierra, tierraDeCultivo(level, tierra), Block.UPDATE_ALL);
                    labradas++;
                }
            }
        }
        if (labradas > 0) {
            DevilRpg.LOGGER.info("[Village] Aldea en {}: vueltas a labrar {} celda(s) pisoteada(s) de los bancales",
                    center, labradas);
        }
        return labradas;
    }

    /**
     * Asegura el <b>kiosco de la plaza</b> (con su campana) en aldeas que todavía no lo tienen. Es idempotente: si
     * el <b>testigo</b> —la plataforma— ya está a la cota del pueblo, no toca nada.
     * <p>
     * Ojo con la comprobación: se hace por la <b>ALTURA DE LA ALDEA</b> y buscando la <b>plataforma</b>, no por la Y
     * del centro. Con la comprobación vieja (la Y del centro) cada latido colocaba otro contenedor y, como
     * {@code groundY} cuenta el contenedor como suelo, la despensa subía un bloque por latido dejando una columna de
     * piedra debajo (bug que vio el jugador).
     */
    public static void asegurarKiosco(ServerLevel level, BlockPos center) {
        int nivel = cotaDeLaPlaza(level, center);
        if (nivel <= level.getMinBuildHeight() + 1) {
            return;
        }
        // LAS CUATRO ESCALERAS DE LAS ENTRADAS, ASEGURADAS SIEMPRE (23-sep-2026): van ANTES del testigo de la
        // plataforma, porque una escalera se puede perder sin que la plataforma se entere. Medido en el guardado del
        // jugador (aldea 0, centro 470,646, cota 63): la escalera SUR —`(470,63,650)`, la que el constructor pone con
        // `escalera(Direction.NORTH)`— estaba convertida en `dirt_path`, comida por el camino de la plaza (que se
        // dibuja DESPUÉS del kiosco y pintaba sobre el bloque de superficie, que ya era la escalera). El jugador lo
        // preguntó tal cual: *"¿por qué el kiosco tiene un bloque de tierra en vez de escaleras?"*. El testigo de la
        // plataforma (el poste) seguía en pie, así que el latido daba el kiosco por bueno y la escalera no volvía.
        asegurarLasEscalerasDelKiosco(level, center, nivel);
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
     * Las <b>cuatro escaleras de las entradas del kiosco</b>, en pie: es <b>idempotente</b> (solo escribe la celda
     * que no tiene su escalera) y <b>no toca lo que no es suyo</b>: si en esa celda hay algo que no es terreno ni un
     * camino (lo que haya puesto el jugador), se deja como está y se dice en el log.
     * <p>
     * La geometría (qué celda y con qué {@code FACING}) es <b>la misma</b> que la del constructor (I4): las cuatro
     * celdas a {@code KIOSCO_RADIO + 1} del centro, mirando hacia la plataforma.
     */
    private static void asegurarLasEscalerasDelKiosco(ServerLevel level, BlockPos center, int nivel) {
        int r = KIOSCO_RADIO;
        Direction[] hacia = {Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH};
        BlockPos[] celdas = {
                new BlockPos(center.getX() - r - 1, nivel, center.getZ()),
                new BlockPos(center.getX() + r + 1, nivel, center.getZ()),
                new BlockPos(center.getX(), nivel, center.getZ() - r - 1),
                new BlockPos(center.getX(), nivel, center.getZ() + r + 1),
        };
        int repuestas = 0;
        int ocupadas = 0;
        for (int i = 0; i < celdas.length; i++) {
            BlockState estado = level.getBlockState(celdas[i]);
            if (estado.equals(escalera(hacia[i]))) {
                continue; // ya está la suya
            }
            // Solo se repone donde el camino (o el nivelado) pudo comerse la escalera: tierra, camino o aire. Lo que
            // haya puesto el jugador NO se toca (I6).
            if (!esTerrenoNatural(estado) && !estado.isAir()) {
                ocupadas++;
                continue;
            }
            // lint:ok I9 porque esto NO es una construcción nueva que tenga que rehacerse al migrar: es un
            // REPARADOR idempotente de una celda que corre en el latido (`asegurarKiosco` se llama desde
            // `manageNearby`, fuera del bloque de migración), así que llega a las aldeas ya construidas sin subir el
            // trazado — igual que `asegurarHerreria`, `asegurarBarraca` y el propio `asegurarKiosco`.
            colocar(level, celdas[i], escalera(hacia[i]), 3);
            repuestas++;
        }
        if (repuestas > 0) {
            DevilRpg.LOGGER.info("[Village] Aldea en {}: {} escalera(s) de las entradas del kiosco repuestas"
                    + " (el camino se las habia comido)", center, repuestas);
        }
        if (ocupadas > 0) {
            DevilRpg.LOGGER.warn("[Village] Aldea en {}: {} entrada(s) del kiosco sin su escalera porque hay algo"
                    + " puesto ahi (no se toca)", center, ocupadas);
        }
    }

    /**
     * <b>La campana, al CENTRO del kiosco, y el beacon del sello, FUERA</b> (migración 58). Lo pidió el jugador:
     * <i>"sitúa la campana justo en el centro del kiosco y quita el beacon pues nunca se usa"</i>. Hace dos cosas en
     * las aldeas <b>ya construidas</b>:
     * <ul>
     *   <li><b>La campana, al centro.</b> Medido en el guardado del jugador (aldea 2, centro {@code 1414,1414}, cota
     *       120): la campana estaba en {@code (1413,121,1415)}, o sea <b>una celda al oeste y una al sur</b> de la
     *       celda central {@code (1414,121,1414)}, que estaba en aire. Se retira la campana <b>solo si sigue siendo
     *       una campana</b> y se coloca en el centro <b>solo si esa celda está libre</b> (aire): si el jugador ha
     *       puesto algo ahí, no se toca <b>nada</b> (mover una campana no vale tirar lo que es suyo, I6) y queda
     *       dicho en el log. La campana se repone <b>posada</b> ({@code attachment} = {@code floor}) porque su apoyo
     *       —la plataforma de piedra— va justo debajo; se le conserva el {@code facing} que tenía.</li>
     *   <li><b>El beacon, fuera.</b> El <b>sello místico</b> lo encendía en la <b>celda central del tejado</b>
     *       ({@code cota+5}) y lo apagaba al caer la aldea (dejando <b>aire</b>, que además se llevaba por delante al
     *       farol colgado de ahí, I14). Un beacon <b>sin pirámide no hace nada</b> y el sello no vive en el bloque
     *       sino en los datos de la aldea ({@code VillageSavedData.isSiegeResolved}; el haz de partículas de
     *       {@code VillageManager.efectosDeAldeas} sigue saliendo del kiosco igual). Se retira <b>solo si sigue
     *       siendo un beacon</b> y su celda se repone con la <b>piedra del tejado</b>, <b>nunca con aire</b>: de esa
     *       celda <b>cuelga</b> el farol del kiosco.</li>
     * </ul>
     * Es <b>idempotente</b> (si ya está todo bien no escribe ni una celda) y <b>no rehace el kiosco</b>: su testigo
     * es la plataforma y rehacerlo tiraría lo de dentro (I15). Va <b>antes</b> de tirar el plano, para que el plano
     * nuevo se capture con la campana en el centro y <b>sin</b> el beacon: si el beacon siguiera en el plano, el
     * obrero lo repondría en cuanto alguien tocara ese hueco (I8).
     */
    public static void centrarLaCampanaYQuitarElBeacon(ServerLevel level, BlockPos center) {
        int nivel = cotaDeLaPlaza(level, center);
        if (nivel <= level.getMinBuildHeight() + 1) {
            return;
        }
        int cambios = 0;
        // 1) EL BEACON DEL SELLO (celda central del tejado), fuera: se repone la piedra del tejado, que es el
        //    material de esa celda y el APOYO del farol colgado de debajo (I14).
        BlockPos techo = new BlockPos(center.getX(), nivel + KIOSCO_POSTE + 1, center.getZ());
        if (level.getBlockState(techo).is(Blocks.BEACON)) {
            colocar(level, techo, Blocks.STONE_BRICKS.defaultBlockState(), 3);
            cambios++;
        }
        // 2) LA CAMPANA, al centro de la plataforma. Se busca en TODA la huella del kiosco (no solo en la celda
        //    vieja) porque el sitio de la campana ha cambiado de trazado más de una vez.
        BlockPos centro = new BlockPos(center.getX(), nivel + 1, center.getZ());
        java.util.List<BlockPos> viejas = new java.util.ArrayList<>();
        for (int dy = 0; dy <= KIOSCO_POSTE + 1; dy++) {
            for (int dx = -KIOSCO_RADIO; dx <= KIOSCO_RADIO; dx++) {
                for (int dz = -KIOSCO_RADIO; dz <= KIOSCO_RADIO; dz++) {
                    BlockPos p = new BlockPos(center.getX() + dx, nivel + dy, center.getZ() + dz);
                    if (!p.equals(centro) && level.getBlockState(p).is(Blocks.BELL)) {
                        viejas.add(p);
                    }
                }
            }
        }
        boolean centrada = level.getBlockState(centro).is(Blocks.BELL);
        if (!centrada && !viejas.isEmpty() && !level.getBlockState(centro).isAir()) {
            // La celda central está OCUPADA por otra cosa (algo del jugador): no se mueve la campana. Nunca se
            // quita una campana para dejar al pueblo sin su POI de reunión.
            DevilRpg.LOGGER.warn("[Village] Aldea en {}: la celda central del kiosco ({}) no esta libre: la campana"
                    + " se queda donde esta", center, centro);
            viejas.clear();
        }
        if (!viejas.isEmpty()) {
            Direction mira = level.getBlockState(viejas.get(0)).getValue(BellBlock.FACING);
            for (BlockPos vieja : viejas) {
                colocar(level, vieja, Blocks.AIR.defaultBlockState(), 3);
                cambios++;
            }
            if (!centrada) {
                // POSADA en la plataforma (`attachment` = floor): el apoyo va justo debajo. Se le conserva el
                // `facing` que tenía, pero NO un `attachment` de techo: copiarlo dejaría la campana flotando.
                colocar(level, centro, Blocks.BELL.defaultBlockState()
                        .setValue(BellBlock.FACING, mira)
                        .setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR), 3);
                cambios++;
            }
        }
        if (cambios > 0) {
            DevilRpg.LOGGER.info("[Village] Aldea en {}: kiosco al dia (campana al centro y beacon del sello fuera,"
                    + " {} celda(s))", center, cambios);
        }
    }

    /**
     * Asegura el <b>almacén</b> de la aldea: un cobertizo de piedra con <b>cofres dobles</b> donde el constructor (que
     * también es recolector) deja lo que recoge por el pueblo. <b>Crece solo</b>: cuando sus cofres se llenan, se
     * añade otro cofre doble en el siguiente hueco (hasta {@code VillageStorage.MAX_COFRES} dobles).
     * <p>
     * Desde la migración 45 el cobertizo es de <b>7×7</b> y tiene <b>seis</b> cofres dobles (doce cofres) en vez de
     * cinco por cinco con tres: va <b>al lado de la taberna</b>, a su espalda, y el jugador pidió sitio para que
     * sigan entrando cofres conforme crece.
     * <p>
     * <b>EL SUELO VA EN {@code cota - 1}</b> (migración 69, I95): la capa que se pisa es la cota (I1), igual que en el
     * cobertizo del corral anexo y en el taller del leñador. Antes se levantaba el suelo <b>en la cota</b> —una
     * plataforma de un bloque entero, como la del kiosco— pero <b>sin escalón</b>: el kiosco tiene sus cuatro escaleras
     * para subir y el almacén no tenía ninguna, así que los aldeanos se quedaban abajo. Medido en el guardado del
     * jugador (aldea 0, centro {@code 470,646}, cota 63): el suelo era {@code stone_bricks} en {@code y=63} con el
     * césped del pueblo en {@code y=62}, y el punto de apoyo que devolvía el mod era {@code (517,64,666)}: <b>siete
     * aldeanos distintos</b> (los dos herreros, el cocinero, el ganadero, el leñador...) y los <b>seis guardias</b> lo
     * aparcaban con {@code "no consigue llegar a BlockPos{x=517, y=64, z=666}: lo deja por 5 min"}. Ver
     * {@link #bajarElAlmacenAlSuelo} (las aldeas ya construidas).
     */
    public static void asegurarAlmacen(ServerLevel level, BlockPos center) {
        int nivel = cotaDeLaPlaza(level, center);
        if (nivel <= level.getMinBuildHeight() + 1) {
            return;
        }
        BlockPos c = VillageStorage.centro(center);
        if (!(level.getBlockState(new BlockPos(c.getX(), nivel - 1, c.getZ())).is(Blocks.STONE_BRICKS))) {
            // Cobertizo 7x7: suelo de piedra (a `cota - 1`: el suelo del pueblo, I1), ocho postes de tronco y tejado
            // de tablones. El interior se despeja, como en el cobertizo del corral: el aldeano tiene que poder andar
            // por dentro (y el suelo no se sube: se ANDA desde el pueblo).
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    colocar(level, new BlockPos(c.getX() + dx, nivel - 1, c.getZ() + dz),
                            Blocks.STONE_BRICKS.defaultBlockState(), 3);
                    for (int dy = 0; dy <= 2; dy++) {
                        colocar(level, new BlockPos(c.getX() + dx, nivel + dy, c.getZ() + dz),
                                Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
            for (int sx = -3; sx <= 3; sx += 3) {
                for (int sz = -3; sz <= 3; sz += 3) {
                    for (int i = 0; i <= 2; i++) {
                        colocar(level, new BlockPos(c.getX() + sx, nivel + i, c.getZ() + sz),
                                Blocks.OAK_LOG.defaultBlockState(), 3);
                    }
                }
            }
            // Y los cuatro postes de en medio, para que el tejado de 7x7 no quede colgando de las esquinas solas.
            for (int k = -3; k <= 3; k += 3) {
                for (int i = 0; i <= 2; i++) {
                    colocar(level, new BlockPos(c.getX() + k, nivel + i, c.getZ()), Blocks.OAK_LOG.defaultBlockState(), 3);
                    colocar(level, new BlockPos(c.getX(), nivel + i, c.getZ() + k), Blocks.OAK_LOG.defaultBlockState(), 3);
                }
            }
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    colocar(level, new BlockPos(c.getX() + dx, nivel + 3, c.getZ() + dz),
                            Blocks.OAK_PLANKS.defaultBlockState(), 3);
                }
            }
            // Farol colgado del tejado: el almacén se ve (y se ilumina) de noche.
            colocar(level, new BlockPos(c.getX(), nivel + 2, c.getZ()),
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
     * <b>EL COBERTIZO DEL ALMACÉN, AL SUELO</b> (migración 69, I95): lo baja un bloque en las aldeas que ya lo
     * tenían levantado con el suelo EN la cota, que es como lo construía {@link #asegurarAlmacen} antes.
     * <p>
     * <b>Qué estaba mal.</b> El suelo del cobertizo se ponía en la cota (una plataforma de un bloque entero) y el
     * punto de apoyo que devuelve {@link VillageStorage#puntoDeApoyo} caía encima de ella, en {@code cota + 1}. El
     * juego sube <b>0,6 andando</b> y esa plataforma es de <b>1,0</b>, así que <b>ningún aldeano podía subir</b>: el
     * punto era inalcanzable y lo aparcaban 5 min una y otra vez (I33). Medido en el guardado del jugador (aldea 0,
     * centro {@code 470,646}, cota 63): suelo {@code stone_bricks} en {@code y=63}, césped del pueblo en {@code y=62},
     * punto {@code (517,64,666)} y <b>siete aldeanos</b> distintos más los <b>seis guardias</b> con
     * {@code "no consigue llegar a BlockPos{x=517, y=64, z=666}"} en el log. El kiosco es la <b>otra</b> plataforma a
     * la cota y no falla porque tiene <b>escaleras en sus cuatro entradas</b>; el almacén no tenía ninguna, así que
     * aquí se elige la otra convención, la de los otros dos cobertizos del pueblo (el del corral y el taller del
     * leñador): <b>suelo a {@code cota - 1} y se entra andando</b>.
     * <p>
     * <b>Cómo lo baja sin perder nada.</b> Es <b>idempotente</b> (la guardia es la capa de piedra del cobertizo
     * <b>viejo</b>: si en {@code (centro, cota)} no hay {@code stone_bricks}, no hay nada que bajar) y solo toca
     * <b>los bloques del cobertizo</b> (piedra del suelo, troncos de los postes, tablones del tejado, el farol y sus
     * cofres): lo que haya puesto el jugador dentro no se toca. Lo de los cofres se saca <b>a la mano</b> antes de
     * tirarlos —tirar un cofre tira su contenido al suelo (I6)— y se devuelve al almacén nuevo al final, y lo que no
     * quepa se deja en el suelo del cobertizo, donde lo recoge el recolector (nunca se borra nada del pueblo). Va
     * <b>antes</b> de tirar el plano, para que el plano nuevo se capture con el cobertizo a la altura buena (I8).
     */
    public static void bajarElAlmacenAlSuelo(ServerLevel level, BlockPos center) {
        int nivel = cotaDeLaPlaza(level, center);
        if (nivel <= level.getMinBuildHeight() + 1) {
            return;
        }
        BlockPos c = VillageStorage.centro(center);
        if (!level.getBlockState(new BlockPos(c.getX(), nivel, c.getZ())).is(Blocks.STONE_BRICKS)) {
            return; // el cobertizo ya está a la altura buena (o no hay cobertizo): no se toca nada
        }
        // 1) LO DE LOS COFRES, A LA MANO (I6: tirar un cofre tira su contenido al suelo).
        List<ItemStack> dentro = new ArrayList<>();
        for (BlockPos q : BlockPos.betweenClosed(new BlockPos(c.getX() - 3, nivel, c.getZ() - 3),
                new BlockPos(c.getX() + 3, nivel + 4, c.getZ() + 3))) {
            if (!(level.getBlockState(q).getBlock() instanceof ChestBlock)
                    || !(level.getBlockEntity(q) instanceof Container cofre)) {
                continue;
            }
            for (int i = 0; i < cofre.getContainerSize(); i++) {
                if (!cofre.getItem(i).isEmpty()) {
                    dentro.add(cofre.getItem(i).copy());
                }
            }
        }
        // 2) EL COBERTIZO VIEJO, CELDA A CELDA (solo sus bloques: el suelo, los postes, el tejado, el farol y sus
        //    cofres ya vaciados). Lo del jugador no se toca.
        int quitados = 0;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                BlockPos suelo = new BlockPos(c.getX() + dx, nivel, c.getZ() + dz);
                if (level.getBlockState(suelo).is(Blocks.STONE_BRICKS)) {
                    colocar(level, suelo, Blocks.AIR.defaultBlockState(), 3);
                    quitados++;
                }
                for (int dy = 1; dy <= 3; dy++) {
                    BlockPos q = new BlockPos(c.getX() + dx, nivel + dy, c.getZ() + dz);
                    BlockState estado = level.getBlockState(q);
                    if (estado.is(Blocks.OAK_LOG) || estado.is(Blocks.LANTERN)
                            || estado.getBlock() instanceof ChestBlock) {
                        colocar(level, q, Blocks.AIR.defaultBlockState(), 3);
                        quitados++;
                    }
                }
                BlockPos techo = new BlockPos(c.getX() + dx, nivel + 4, c.getZ() + dz);
                if (level.getBlockState(techo).is(Blocks.OAK_PLANKS)) {
                    colocar(level, techo, Blocks.AIR.defaultBlockState(), 3);
                    quitados++;
                }
            }
        }
        // 3) EL COBERTIZO NUEVO (suelo a `cota - 1`, se entra andando) y 4) LO DE LOS COFRES, DE VUELTA.
        asegurarAlmacen(level, center);
        BlockPos apoyo = VillageStorage.puntoDeApoyo(level, center);
        int devueltos = 0;
        int alSuelo = 0;
        for (ItemStack pila : dentro) {
            ItemStack sobra = VillageStorage.guardar(level, center, pila.copy());
            devueltos += pila.getCount() - sobra.getCount();
            if (!sobra.isEmpty()) {
                Block.popResource(level, apoyo, sobra);
                alSuelo += sobra.getCount();
            }
        }
        DevilRpg.LOGGER.info("[Village] Aldea en {}: almacen bajado al suelo ({} celda(s) del cobertizo viejo;"
                        + " {} objeto(s) de los cofres conservados{})", center, quitados, devueltos,
                alSuelo > 0 ? ", " + alSuelo + " al suelo del cobertizo" : "");
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

    /**
     * El puesto de ENTRENAMIENTO de la milicia (lo pidió el jugador: *"¿por qué no pones el puesto de entrenamiento
     * en un campo abierto justo al lado de las barracas?"*): <b>la casilla de delante de la diana, en el PATIO</b> —el
     * campo abierto del sur de la barraca (ver {@link #BARRACA_DIANA})—, que es donde se pone el guardia a entrenar en
     * su turno. Se devuelve la celda de al lado, no la diana, para que el guardia se coloque <b>frente</b> a ella.
     * <p>
     * <b>Por qué se sacó de dentro</b> (medido, 25-sep-2026): con la diana y el puesto <b>dentro</b> de la barraca
     * (`422,671` y `423,671`), <b>seis guardias distintos</b> se rendían ahí con la etiqueta `Yendo a entrenar`: al
     * <b>bloque</b> de la diana el planificador del juego devuelve una ruta que <b>no alcanza</b> (I114) y la celda de
     * dentro de la barraca no se distingue de una calle por las pruebas locales (I118). En el patio está a la vista,
     * en campo abierto y a un bloque de la diana.
     */
    public static BlockPos puestoDeEntrenamiento(BlockPos center, int nivel) {
        BlockPos base = baseDeBarraca(center);
        // La Y va con la COTA de la aldea que pasa quien llama (`cotaDeLaPlaza`), no con la del centro (I1/I12).
        return new BlockPos(base.getX() + BARRACA_DIANA[0], nivel, base.getZ() + BARRACA_DIANA[1] + 1);
    }

    /** Radio de la barraca (huella de 9x9). */
    private static final int BARRACA_RADIO = 4;
    /**
     * Lo que sube el <b>suelo del dormitorio</b> sobre la cota de la aldea (el forjado va uno por debajo).
     * <p>
     * <b>EN 0: LA BARRACA ES DE UN SOLO NIVEL</b> (lo pidió el jugador: *"así haces las barracas de un solo nivel
     * junto con sus camas"*). Todo el trazado está parametrizado por este número, así que con 0 salen solos: las
     * {@link #BARRACA_CAMAS 8 camas} y las dos arcas <b>en la planta baja</b>, <b>sin escalera</b> (los escalones son
     * {@link #BARRACA_ESCALONES} = este número), <b>sin forjado</b> y con el <b>tejado a {@code nivel + 3}</b> y los
     * faroles colgando de él ({@link #BARRACA_FAROL_DY} = 2). La sala de armas (maniquíes y hogar) sigue abajo, en su
     * sitio. Ver I120.
     */
    private static final int BARRACA_PISO2 = 0;
    /**
     * La altura que tenía el <b>piso de arriba</b> en el trazado de DOS PISOS (el que se rehace). Vive aquí porque lo
     * necesita la migración para saber <b>dónde estaban las arcas y las camas viejas</b> (ver
     * {@link #vaciarLasArcasDeLaBarraca} y {@link #barracaConstruida}).
     */
    private static final int BARRACA_PISO2_VIEJO = 4;
    /**
     * Lo que sube el <b>farol del dormitorio</b> sobre la cota: va pegado al <b>tejado</b> (que está un bloque más
     * arriba), así que <b>cuelga</b> de él. Colocado <b>posado</b> —como estaba— no tiene nada debajo y queda
     * flotando (I14). El número vive aquí porque lo usan el constructor y el retrofit de las barracas ya construidas.
     */
    private static final int BARRACA_FAROL_DY = BARRACA_PISO2 + 2;
    /** Camas de la barraca: dos filas de 4, una contra cada pared larga. */
    public static final int BARRACA_CAMAS = 8;
    /**
     * Los <b>maniquíes de entrenamiento</b> de la sala de armas, por su celda ({@code dx},{@code dz} relativos a la
     * base): el de la esquina <b>noroeste</b> y el de la <b>sureste</b>. Cada uno es una <b>paca</b> de paja
     * ({@code HAY_BLOCK}) con su <b>calabaza</b> tallada encima y <b>dos vallas</b> (el travesaño del costado y el
     * palo de arriba). Las celdas viven aquí porque las usan el <b>constructor</b> y el reparador de la mesa de
     * cartografía (<b>migración 60</b>), que tiene que saber cuál es la celda de una paca (invariante I4).
     */
    private static final int[][] BARRACA_MANIQUIES = {
            {-BARRACA_RADIO + 2, -BARRACA_RADIO + 2},
            {BARRACA_RADIO - 2, BARRACA_RADIO - 2}};
    /**
     * El maniquí de la esquina <b>sureste</b>: su <b>paca</b> es la celda en la que el constructor ponía la
     * <b>mesa de cartografía</b>. La mesa se coloca <b>después</b> del maniquí y en la <b>misma celda</b>, así que se
     * comía la paca y el maniquí se quedaba sin base (medido en el guardado del jugador: la paca desaparecida, con
     * la calabaza y las dos vallas todavía en pie y el suelo de piedra debajo). Es lo que devuelve a su sitio el
     * reparador {@link #quitarLaMesaDeLaBarraca}.
     */
    private static final int[] BARRACA_MANIQUI_SURESTE = BARRACA_MANIQUIES[1];

    /**
     * La celda ({@code dx},{@code dz} relativos a la base) de la <b>diana suelta</b> de los arqueros: el rincón
     * <b>suroeste</b> de la sala de armas, <b>pegada a las dos paredes</b>.
     * <p>
     * <b>Antes estaba en la celda del maniquí suroeste</b> ({@code -r+2,+r-2}), que es también la del <b>arca</b> de
     * la sala de armas (la que fue un barril y pasó a cofre, ver {@link #asegurarBarraca}): el arca se coloca
     * <b>después</b>, así que <b>se comía la diana</b> —el constructor cree poner <b>tres</b> y en el mundo solo había
     * <b>dos</b>: medido en el guardado del jugador, aldea 2, barraca en {@code 1369,1436}, cota {@code 120}, con
     * {@code build/barraca_dump.py}: {@code 1371,120,1434} y {@code 1371,121,1434}, y de la tercera ni rastro—.
     * <p>
     * La celda nueva es <b>libre</b> en las tres aldeas del guardado (lo comprueba {@code build/barraca_diana.py},
     * que además transcribe el constructor y canta <b>cualquier</b> celda que se escriba dos veces) y tiene sentido
     * para una diana: <b>se ve al entrar</b> por la puerta norte (está en la diagonal delante-derecha), tiene las
     * <b>dos paredes</b> de tope detrás, <b>no tapa el paso</b> ni a la <b>escalera</b> (que sube por la columna
     * <b>este</b>) ni al <b>hogar</b> (que está en el centro del muro sur) y <b>no es puesto de trabajo</b> de nadie
     * (una {@code TARGET} no es un POI de aldeano, invariante I31). La deja ahí el constructor y la devuelve a su
     * sitio el reparador {@link #moverLaDianaDeLaBarraca} en las barracas ya construidas.
     */
    private static final int[] BARRACA_DIANA = {-1, BARRACA_RADIO + 2};
    /**
     * La celda ({@code dx},{@code dz}) de la <b>diana doble</b>: dos bloques de {@code TARGET} <b>apilados</b>, que es
     * la que da las otras dos dianas del constructor. Va <b>en el mismo patio</b>, a un bloque de la otra (las dos
     * mirando al norte, a la barraca): son las tres dianas de la línea de tiro del patio. Como la suelta, se movió
     * aquí al sacar el entrenamiento de dentro (ver {@link #BARRACA_DIANA}).
     */
    private static final int[] BARRACA_DIANA_DOBLE = {1, BARRACA_RADIO + 2};
    /**
     * Las celdas donde el constructor ponía las dianas <b>ANTES</b> (dentro de la sala de armas): el rincón suroeste y
     * el noreste. Viven aquí para que el reparador {@link #moverLasDianasAlPatioDeLaBarraca} sepa <b>qué</b> dianas
     * hay que sacar de dentro en las barracas ya construidas —y solo si ahí sigue habiendo un {@code TARGET}: lo que
     * haya puesto el jugador no se toca—.
     */
    private static final int[][] BARRACA_DIANAS_VIEJAS = {
            {-BARRACA_RADIO + 1, BARRACA_RADIO - 1},
            {BARRACA_RADIO - 2, -BARRACA_RADIO + 2}};

    /**
     * La columna ({@code dx} relativo a la base) por la que sube la <b>escalera del dormitorio</b>: la última celda
     * del interior, pegada al muro <b>este</b>.
     */
    private static final int BARRACA_ESCALERA_DX = BARRACA_RADIO - 1;
    /**
     * La fila ({@code dz} relativo a la base) del <b>pie</b> de la escalera del dormitorio. Va <b>dos</b> celdas al
     * norte del muro sur y no pegada a él por una razón medida: <b>a un escalón se entra por su lado BAJO</b> (el
     * contrario a la cara alta que marca el {@code FACING}), y el que sube tiene que poder ponerse en la celda de al
     * lado —suelo firme y las dos celdas de su cuerpo libres—. Con el pie pegado al muro sur, la celda de entrada cae
     * <b>dentro de la pared</b> y a la escalera <b>no se puede ni entrar</b> (la vieja, además, miraba al oeste con la
     * cara alta de través: ver {@code build/barraca_subida.py}, que comprueba la entrada, cada escalón y la salida).
     */
    private static final int BARRACA_ESCALERA_PIE_DZ = BARRACA_RADIO - 2;
    /**
     * Los <b>cuatro</b> escalones de la escalera del dormitorio: uno por cada bloque que sube el piso de arriba
     * ({@link #BARRACA_PISO2}), porque con escaleras del juego se sube de <b>medio en medio bloque</b> (dos medios
     * escalones por bloque). El <b>último</b> va en la capa del forjado y su cara alta queda a la altura del suelo del
     * dormitorio, así que del último escalón se sale <b>andando</b>, sin saltar.
     */
    private static final int BARRACA_ESCALONES = BARRACA_PISO2;
    /**
     * La columna ({@code dx}) de la cama del rincón <b>sureste</b> del dormitorio, corrida <b>una celda al oeste</b>:
     * su celda de los pies cae justo <b>encima del hueco del forjado</b> de la escalera, y con una cama ahí el que
     * sube no pasa —medido en el guardado del jugador: el 2º escalón tenía <b>2,0</b> de hueco hasta la cama
     * ({@code (1372,124,1438)}) en vez de los 2,4 = 3 celdas que pide {@code Entity.maxUpStep}, ver I26—.
     */
    private static final int BARRACA_CAMA_CORRIDA_DX = BARRACA_RADIO - 2;
    /**
     * Las columnas ({@code dx} relativas a la base) de las camas de cada fila del dormitorio, de norte a sur. Suman
     * {@link #BARRACA_CAMAS} y <b>ninguna</b> queda en la columna de la escalera ({@link #BARRACA_ESCALERA_DX}) ni
     * encima del hueco del forjado: la del rincón sureste se corre al oeste ({@link #BARRACA_CAMA_CORRIDA_DX}), que la
     * cama es un <b>POI</b> y el pueblo no puede perder ninguna (en vanilla cada cría pide una <b>cama libre</b>).
     */
    private static final int[] BARRACA_CAMAS_NORTE = {-3, -1, 1, 3};
    /**
     * La fila del <b>sur</b>. Con la barraca de DOS pisos, la cama del rincón sureste se corría al oeste
     * ({@code BARRACA_CAMA_CORRIDA_DX}) para no caer en la columna de la escalera. Con la barraca <b>de UN piso</b> no
     * hay escalera, así que la fila vuelve a ser <b>simétrica</b> ({@code -3, -1, 1, 3}) y la del rincón queda al lado
     * del maniquí sureste (que está en {@code +2,+2}), sin pisarlo.
     */
    private static final int[] BARRACA_CAMAS_SUR = {-3, -1, 1, 3};
    /**
     * La fila ({@code dz}) de las dos <b>arcas</b> del dormitorio: van <b>juntas</b> contra el muro oeste (cofre
     * doble). La del muro <b>este</b> estaba en la celda del <b>último escalón</b> —el que sube se la encontraba de
     * frente, con el arca a la altura de los pies—, así que se pasa al lado de la otra.
     */
    private static final int BARRACA_ARCA_DZ = 0;

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
        // Y LAS DIANAS AL PATIO, EN EL SITIO (migración 63, lo pidió el jugador). Va AQUÍ, en la pasada que arregla la
        // barraca YA CONSTRUIDA en el sitio (como el barril y los faroles de arriba), y no en el bloque de migraciones:
        // medido, desde allí NO se ejecutaba para la aldea del jugador (las dianas seguían dentro y los guardias
        // rindiéndose "Yendo a entrenar"). Es idempotente y de tres celdas.
        moverLasDianasAlPatioDeLaBarraca(level, center);
        // Y LOS FAROLES DEL DORMITORIO, COLGADOS DEL TEJADO (migración 55). Se colocaban POSADOS en la celda que va
        // pegada al tejado, y ahí no hay nada debajo (el dormitorio está al aire): quedaban flotando, sin cadena
        // (I14). Medido en el guardado del jugador: 2 en el dormitorio de cada barraca (aldeas 0 y 2). La celda es
        // la BUENA —es la que está justo debajo del tejado de tablones—: lo que estaba mal era el ESTADO, así que
        // esto es un retrofit en el sitio (idempotente y de tres celdas), como el del barril de arriba.
        for (int dz = -BARRACA_RADIO + 2; dz <= BARRACA_RADIO - 2; dz += 3) {
            BlockPos farol = new BlockPos(base.getX(), nivel + BARRACA_FAROL_DY, base.getZ() + dz);
            BlockState estado = level.getBlockState(farol);
            if (estado.is(Blocks.LANTERN) && !estado.getValue(LanternBlock.HANGING)
                    && Block.canSupportCenter(level, farol.above(), Direction.DOWN)) {
                colgar(level, farol);
            }
        }
        // Testigo del trazado NUEVO (etapa F: barraca de DOS PISOS con sala de armas): el hogar del patio de
        // entrenamiento. Una barraca de una planta (sin hogar) se vuelve a levantar entera, que es lo que trae el
        // segundo piso con las camas y el patio.
        if (barracaConstruida(level, center)) {
            return; // la barraca ya está y con el trazado actual
        }
        // ANTES DE REHACERLA, SE VACÍAN SUS ARCAS (I123): rehacer la barraca tira lo que haya dentro de los cofres
        // (mecánica de vanilla), y en la aldea del jugador esas dos arcas están en uso. Se pasa todo al ALMACÉN con
        // `VillageStorage.guardar`, que ya es lo que usa el pueblo para guardar lo suyo: si algo no cupiera, se avisa en
        // el log, pero no se pierde por el camino. Las arcas VIEJAS están a la altura del piso de arriba de entonces
        // ({@link #BARRACA_PISO2_VIEJO}), que es lo que ya no existe en el trazado de un piso.
        vaciarLasArcasDeLaBarraca(level, center, base, nivel);
        BlockPos puerta = barraca(level, base, nivel);
        // Camino de la plaza a su puerta (si no, los guardias tienen que trepar por el césped).
        paths(level, center, doorApproach(level, puerta));
        DevilRpg.LOGGER.info("[Village] Aldea en {}: barraca de la milicia construida en {} (dos pisos: sala de"
                + " armas abajo y {} camas arriba)", center, base, BARRACA_CAMAS);
    }

    /**
     * ¿Está la <b>barraca</b> construida y con el trazado actual? El testigo es el <b>hogar</b> del patio de
     * entrenamiento, y va en el <b>suelo</b> ({@code nivel - 1}, la misma capa que el suelo de piedra: invariante I15,
     * un testigo es plataforma o suelo, <b>no mobiliario</b> —el mobiliario se lo puede llevar el jugador—). Es el
     * testigo del trazado de <b>dos pisos</b>: una barraca de una planta (sin hogar) se vuelve a levantar entera.
     */
    public static boolean barracaConstruida(ServerLevel level, BlockPos center) {
        int nivel = cotaDeLaPlaza(level, center);
        BlockPos base = baseDeBarraca(center);
        // TESTIGO 1 (el de siempre): el hogar del patio de entrenamiento.
        if (!level.getBlockState(new BlockPos(base.getX(), nivel - 1, base.getZ() + BARRACA_RADIO - 1))
                .is(Blocks.CAMPFIRE)) {
            return false;
        }
        // TESTIGO 2 (I123, la barraca de UN PISO): el PRIMER ESCALÓN de la escalera del dormitorio. En el trazado de
        // dos pisos ahí hay un escalón; en el de un piso, esa celda es del INTERIOR (al aire). Sin esta prueba, la
        // barraca vieja pasaba por buena y el jugador seguía con las camas arriba. No vale mirar el forjado: en el
        // trazado nuevo el tejado cae en la MISMA capa (nivel + 3) que el forjado viejo.
        // OJO CON EL MATERIAL: los escalones de la barraca son de ROBLE (`oak_stairs`), no de adoquín — medido en su
        // guardado (`428,63,670` = `oak_stairs facing=north half=bottom`). Preguntando por `COBBLESTONE_STAIRS` el
        // testigo daba "ya está" y la barraca NO se rehacía; con `instanceof StairBlock` vale cualquiera.
        BlockState escalon = level.getBlockState(new BlockPos(base.getX() + BARRACA_ESCALERA_DX, nivel,
                base.getZ() + BARRACA_ESCALERA_PIE_DZ));
        return !(escalon.getBlock() instanceof net.minecraft.world.level.block.StairBlock);
    }

    /**
     * <b>Vacía las dos arcas VIEJAS de la barraca al almacén</b> antes de rehacerla (I123): rehacer una construcción
     * tira lo que haya dentro de sus cofres, así que lo que el pueblo tenga guardado ahí se pasa primero al almacén con
     * {@link VillageStorage#guardar}. Es <b>idempotente</b> (si las arcas ya no están, no hace nada) y no se lleva nada
     * más: camas y maniquíes son bloques, no tienen contenido.
     */
    private static void vaciarLasArcasDeLaBarraca(ServerLevel level, BlockPos center, BlockPos base, int nivel) {
        int y = nivel + BARRACA_PISO2_VIEJO;
        for (int dz = BARRACA_ARCA_DZ; dz <= BARRACA_ARCA_DZ + 1; dz++) {
            BlockPos arca = new BlockPos(base.getX() - BARRACA_RADIO + 1, y, base.getZ() + dz);
            if (!(level.getBlockEntity(arca) instanceof Container cofre)) {
                continue;
            }
            int pasados = 0;
            for (int i = 0; i < cofre.getContainerSize(); i++) {
                ItemStack dentro = cofre.getItem(i);
                if (dentro.isEmpty()) {
                    continue;
                }
                ItemStack resto = VillageStorage.guardar(level, center, dentro.copy());
                if (resto.isEmpty()) {
                    cofre.setItem(i, ItemStack.EMPTY);
                    pasados++;
                }
            }
            if (pasados > 0) {
                DevilRpg.LOGGER.info("[Village] Barraca de {}: {} pila(s) del arca {} pasadas al almacen antes de"
                        + " rehacerla (un piso, I123)", center, pasados, arca.toShortString());
            }
        }
    }

    /**
     * <b>Arregla la escalera del dormitorio de la barraca</b> en una barraca ya construida (<b>migración 59</b>).
     * <p>
     * <b>Lo que había</b> (medido en el guardado del jugador, aldea 2, barraca en {@code 1369,1436}, cota {@code 120},
     * con {@code build/barraca_dump.py} y {@code build/barraca_subida.py}): la escalera tenía <b>tres</b> escalones de
     * los cuatro —el <b>4º lo borraba el propio constructor</b>, porque para ese peldaño la celda del escalón y la del
     * <b>hueco del forjado</b> eran la misma ({@code yPiso2 - 1 = nivel + 3})—, así que el último escalón quedaba a
     * <b>1,0</b> del suelo del dormitorio (<b>solo se subía saltando</b>); el <b>2º</b> tenía una <b>cama</b> justo
     * encima ({@code (1372,124,1438)}: <b>2,0</b> de hueco en vez de los 2,4 = 3 celdas que pide {@code maxUpStep},
     * I26); el <b>arca</b> del este estaba en la celda del último escalón; y el {@code FACING} iba al <b>oeste</b>
     * subiendo al <b>norte</b>, con la cara alta de través (y con el pie pegado al muro sur, por donde no se puede
     * entrar: era imposible <b>entrar</b> a la escalera).
     * <p>
     * <b>Lo que hace</b>, celda por celda y <b>sin rehacer la barraca</b> (rehacerla tiraría las camas y lo de dentro
     * de las arcas):
     * <ol>
     *   <li>los <b>escalones viejos</b>, fuera (solo si siguen siendo escalones, {@code quitarSiEs});</li>
     *   <li>la <b>escalera nueva</b> (los {@code BARRACA_ESCALONES} escalones de medio bloque, cara alta al norte) y el
     *       <b>hueco del forjado</b> abierto encima de los que van <b>por debajo</b> de él —la celda del último es del
     *       escalón: abrirla fue justo el fallo—;</li>
     *   <li>el forjado que <b>ya no hace falta abrir</b> (la celda del sur, la del cuarto escalón fantasma), cerrado;</li>
     *   <li>la <b>cama del rincón sureste</b> (la que estaba encima del hueco), corrida una celda al oeste: es un
     *       <b>POI</b> y el pueblo no puede perder ninguna (en vanilla cada cría pide una cama libre), así que la
     *       nueva se pone <b>antes</b> y la vieja solo se retira si la nueva está puesta;</li>
     *   <li>el <b>arca del este</b> (la que estaba sobre el último escalón), pasada al lado de la del oeste: se pone
     *       la nueva, se le <b>pasa todo lo de dentro</b> —reemplazar un cofre tira su contenido, mecánica de vanilla,
     *       I6— y solo entonces se retira la vieja.</li>
     * </ol>
     * Es <b>idempotente</b> (si ya está todo bien no escribe ni una celda) y <b>no toca lo que puso el jugador</b>:
     * para quitar, solo el bloque esperado; para poner, solo en celda vacía. Y lo que no quepa en el arca nueva
     * (o si el jugador ocupó su celda) va al <b>almacén del pueblo</b>, nunca al suelo.
     */
    public static void arreglarLaEscaleraDeLaBarraca(ServerLevel level, BlockPos center) {
        if (!barracaConstruida(level, center)) {
            return;
        }
        int nivel = cotaDeLaPlaza(level, center);
        BlockPos base = baseDeBarraca(center);
        int bx = base.getX();
        int bz = base.getZ();
        int yPiso2 = nivel + BARRACA_PISO2;
        int yForjado = yPiso2 - 1;
        // 1) LA ESCALERA VIEJA, FUERA. Subía por la MISMA columna pero una celda al sur (el pie pegado al muro sur,
        //    por donde no se entra) y con la cara alta de través (facing=west). Se quitan solo los escalones.
        int quitados = 0;
        for (int i = 0; i < BARRACA_ESCALONES; i++) {
            quitados += quitarSiEs(level, bx + BARRACA_ESCALERA_DX, nivel + i, bz + BARRACA_RADIO - 1 - i,
                    Blocks.OAK_STAIRS);
        }
        // 2) LA ESCALERA NUEVA y su hueco. El hueco se abre con `quitarSiEs` (solo tablones del forjado) y en la
        //    celda del último escalón NO se abre nada: el escalón va ahí (lleva su propio `colocar`).
        for (int k = 0; k < BARRACA_ESCALONES; k++) {
            colocar(level, new BlockPos(bx + BARRACA_ESCALERA_DX, nivel + k, bz + BARRACA_ESCALERA_PIE_DZ - k),
                    Blocks.OAK_STAIRS.defaultBlockState()
                            .setValue(StairBlock.FACING, Direction.NORTH).setValue(StairBlock.HALF, Half.BOTTOM), 3);
            if (k < BARRACA_ESCALONES - 1) {
                quitarSiEs(level, bx + BARRACA_ESCALERA_DX, yForjado, bz + BARRACA_ESCALERA_PIE_DZ - k,
                        Blocks.OAK_PLANKS);
            }
        }
        // 3) El tablón que el hueco viejo se había comido de más (la celda del sur, que era la del cuarto escalón
        //    fantasma) vuelve: el suelo del dormitorio tiene que quedar entero. Solo donde esté vacío.
        colocarSiEstaVacio(level, new BlockPos(bx + BARRACA_ESCALERA_DX, yForjado, bz + BARRACA_RADIO - 1),
                Blocks.OAK_PLANKS.defaultBlockState());
        // 4) LA CAMA QUE ESTORBA, RECOLOCADA. La nueva se pone ANTES (y solo si las dos celdas están vacías) y la
        //    vieja solo se retira si la nueva está puesta: una cama es un POI y el pueblo no puede perder ninguna.
        BlockPos pieNuevo = new BlockPos(bx + BARRACA_CAMA_CORRIDA_DX, yPiso2, bz + BARRACA_RADIO - 2);
        BlockPos cabeceraNueva = pieNuevo.relative(Direction.SOUTH);
        boolean camaNueva = level.getBlockState(pieNuevo).is(Blocks.RED_BED)
                && level.getBlockState(cabeceraNueva).is(Blocks.RED_BED);
        if (!camaNueva && level.getBlockState(pieNuevo).isAir() && level.getBlockState(cabeceraNueva).isAir()) {
            bed(level, pieNuevo, Direction.SOUTH);
            camaNueva = true;
        }
        int camas = 0;
        if (camaNueva) {
            for (int dz = BARRACA_RADIO - 2; dz <= BARRACA_RADIO - 1; dz++) {
                BlockPos vieja = new BlockPos(bx + BARRACA_ESCALERA_DX, yPiso2, bz + dz);
                if (level.getBlockState(vieja).is(Blocks.RED_BED)) {
                    colocar(level, vieja, Blocks.AIR.defaultBlockState(), 3);
                    camas++;
                }
            }
        }
        // 5) EL ARCA DEL ESTE, al lado de la del oeste. Primero se pasa TODO lo de dentro y, si no hay dónde
        //    ponerla (el jugador ocupó la celda), al almacén del pueblo: lo suyo no se pierde nunca (I6).
        BlockPos arcaVieja = new BlockPos(bx + BARRACA_ESCALERA_DX, yPiso2, bz + BARRACA_ARCA_DZ);
        BlockPos arcaNueva = new BlockPos(bx - BARRACA_RADIO + 1, yPiso2, bz + BARRACA_ARCA_DZ + 1);
        boolean arcaPuesta = level.getBlockState(arcaNueva).getBlock() instanceof ChestBlock;
        if (!arcaPuesta && level.getBlockState(arcaNueva).isAir()) {
            colocar(level, arcaNueva, Blocks.CHEST.defaultBlockState()
                    .setValue(ChestBlock.FACING, Direction.EAST), 3);
            arcaPuesta = true;
        }
        int arcas = 0;
        if (level.getBlockState(arcaVieja).getBlock() instanceof ChestBlock) {
            if (arcaPuesta) {
                traspasarElArca(level, arcaVieja, arcaNueva);
            } else if (level.getBlockEntity(arcaVieja) instanceof Container contenedor) {
                guardarEnElAlmacen(level, center, contenedor);   // no hay dónde ponerla: al almacén, no al suelo
            }
            if (contenedorVacio(level, arcaVieja)) {
                colocar(level, arcaVieja, Blocks.AIR.defaultBlockState(), 3);
                arcas++;
            }
        }
        if (quitados > 0 || camas > 0 || arcas > 0) {
            DevilRpg.LOGGER.info("[Village] Barraca de {}: escalera del dormitorio arreglada ({} escalon(es) viejo(s)"
                    + " fuera, {} mitad(es) de cama recolocada(s), {} arca(s) movida(s)); el ultimo escalon queda a la"
                    + " altura del suelo del dormitorio (y={})", center, quitados, camas, arcas, yPiso2);
        }
    }

    /**
     * Quita de la barraca la <b>mesa de cartografía</b> y devuelve su celda a la <b>paca del maniquí</b> sureste
     * (<b>migración 60</b>).
     * <p>
     * <b>Lo que había</b> (medido en el guardado del jugador, aldea 2, barraca en {@code 1369,1436}, cota {@code 120}):
     * la celda {@code 1371,120,1438} tenía una {@code cartography_table}, que es el <b>puesto de trabajo del
     * CARTÓGRAFO</b> —un oficio que este pueblo <b>no</b> tiene, así que un aldeano <b>sin oficio</b> (una cría que
     * crece) lo reclamaría y se volvería cartógrafo—. Y esa celda no era suya: es la <b>paca del maniquí de
     * entrenamiento</b> de la esquina sureste ({@link #BARRACA_MANIQUI_SURESTE}), que el constructor coloca
     * <b>antes</b> y la mesa <b>sustituía</b>, así que el maniquí se quedaba <b>sin base</b> (lo medido: la paca
     * desaparecida, la calabaza y las dos vallas todavía en pie y, debajo de la mesa, el <b>suelo de piedra</b> de la
     * barraca, la capa de {@code cota - 1}). El <b>plano</b> guardaba la mesa, así que el obrero la reponía y no se
     * arreglaba sola.
     * <p>
     * <b>Lo que hace</b>: si en esa celda sigue habiendo una <b>mesa de cartografía</b>, la cambia por la
     * <b>paca</b> ({@code HAY_BLOCK}) del maniquí, que es lo que le toca a esa celda —y <b>en la misma escritura</b>:
     * quitar la mesa y dejar la celda en aire pondría la <b>calabaza a flotar</b> (I14), que es justo el hueco que no
     * se puede dejar—. Es <b>idempotente</b> (si ya es la paca, o si el jugador puso ahí cualquier otra cosa, no
     * escribe ni una celda) y <b>no rehace la barraca</b>: su testigo es el <b>hogar</b> del patio de entrenamiento
     * (I15) y rehacerla tiraría las camas y lo de dentro de las arcas. Va <b>antes</b> de tirar el plano, para que el
     * plano nuevo se capture ya con la paca y sin la mesa (I8: con la mesa en el plano, el obrero la repondría).
     */
    public static void quitarLaMesaDeLaBarraca(ServerLevel level, BlockPos center) {
        if (!barracaConstruida(level, center)) {
            return; // sin barraca del trazado actual no hay ninguna mesa que quitar
        }
        int nivel = cotaDeLaPlaza(level, center);
        BlockPos base = baseDeBarraca(center);
        BlockPos celda = new BlockPos(base.getX() + BARRACA_MANIQUI_SURESTE[0], nivel,
                base.getZ() + BARRACA_MANIQUI_SURESTE[1]);
        if (sustituirSiEs(level, celda, Blocks.CARTOGRAPHY_TABLE, Blocks.HAY_BLOCK.defaultBlockState())) {
            DevilRpg.LOGGER.info("[Village] Barraca de {}: mesa de cartografia fuera de {} (es el puesto del"
                    + " cartografo, un oficio que el pueblo no tiene); su celda vuelve a ser la paca del maniqui",
                    center, celda);
        }
    }

    /**
     * Devuelve la <b>tercera diana</b> a la barraca (<b>migración 61</b>).
     * <p>
     * <b>Lo que había</b> (medido en el guardado del jugador, aldea 2, barraca en {@code 1369,1436}, cota {@code 120},
     * con {@code build/barraca_dump.py} y {@code build/barraca_diana.py}): la <b>primera diana</b> del constructor se
     * colocaba en {@code (bx-r+2, nivel, bz+r-2)} = {@code 1367,120,1438}, que es <b>la misma celda</b> que el
     * <b>arca</b> de la sala de armas ({@code (bx-2, nivel, bz+r-2)}, la que fue un barril y pasó a cofre). El arca se
     * coloca <b>después</b> —y en el mismo método—, así que <b>se comía la diana</b>: de las <b>tres</b> que el
     * constructor cree poner solo había <b>dos</b> en el mundo ({@code 1371,120,1434} y {@code 1371,121,1434}, la
     * doble del rincón noreste), y el <b>plano</b> guardaba esa misma foto, así que el obrero tampoco la reponía.
     * <p>
     * <b>Lo que hace</b>: pone la diana que falta en su celda nueva ({@link #BARRACA_DIANA}: pegada a las dos paredes
     * del rincón suroeste) <b>solo si esa celda está vacía</b> —si el jugador la ocupó, no se le toca nada—. La
     * <b>celda vieja no se toca</b>: es la del <b>arca</b>, que es lo que le toca. Es <b>idempotente</b> (con la
     * diana puesta no escribe ni una celda) y <b>no rehace la barraca</b>: su testigo es el <b>hogar</b> del patio de
     * entrenamiento (I15) y rehacerla tiraría las camas y lo de dentro de las arcas. Va <b>antes</b> de tirar el
     * plano, para que el plano nuevo se capture ya con las <b>tres</b> dianas (I8).
     */
    public static void moverLaDianaDeLaBarraca(ServerLevel level, BlockPos center) {
        if (!barracaConstruida(level, center)) {
            return; // sin barraca del trazado actual no hay sala de armas a la que devolverle la diana
        }
        int nivel = cotaDeLaPlaza(level, center);
        BlockPos base = baseDeBarraca(center);
        BlockPos diana = new BlockPos(base.getX() + BARRACA_DIANA[0], nivel, base.getZ() + BARRACA_DIANA[1]);
        if (colocarSiEstaVacio(level, diana, Blocks.TARGET.defaultBlockState())) {
            DevilRpg.LOGGER.info("[Village] Barraca de {}: la diana que se comia el arca vuelve a la sala de armas,"
                    + " en {} (pegada a las paredes del rincon suroeste)", center, diana);
        }
    }

    /**
     * <b>Saca las dianas de dentro de la barraca y las pone en el PATIO</b> (migración 63, lo pidió el jugador:
     * *"¿por qué no pones el puesto de entrenamiento en un campo abierto justo al lado de las barracas?"*).
     * <p>
     * <b>Lo que había</b> (medido, 25-sep-2026, con el arnés): la diana suelta y la doble estaban <b>dentro</b> de la
     * sala de armas (`422,671` y `426,666` en la aldea del jugador) y el puesto de entrenamiento era la celda de al
     * lado (`423,671`). Ahí <b>seis guardias distintos</b> se rendían con la etiqueta `Yendo a entrenar`: al bloque de
     * la diana el planificador les da una ruta que <b>no alcanza</b>, y una celda de dentro de la barraca no se
     * distingue de una calle por las pruebas locales (I114/I118).
     * <p>
     * <b>Lo que hace</b>: quita la diana de cada celda vieja <b>solo si ahí sigue habiendo un {@code TARGET}</b> (lo
     * que haya puesto el jugador no se toca) y las pone en el patio ({@link #BARRACA_DIANA} y
     * {@link #BARRACA_DIANA_DOBLE}) <b>solo si esas celdas están vacías</b>. Es <b>idempotente</b> (con las dianas ya
     * en el patio no escribe ni una celda) y <b>no rehace la barraca</b> (su testigo es el hogar, I15: rehacerla
     * tiraría las camas y lo de dentro de las arcas). Va <b>antes</b> de tirar el plano, para que el plano nuevo se
     * capture ya con las dianas en el patio (I8).
     */
    public static void moverLasDianasAlPatioDeLaBarraca(ServerLevel level, BlockPos center) {
        if (!barracaConstruida(level, center)) {
            return; // sin barraca del trazado actual no hay dianas que sacar
        }
        int nivel = cotaDeLaPlaza(level, center);
        BlockPos base = baseDeBarraca(center);
        int quitadas = 0;
        for (int[] vieja : BARRACA_DIANAS_VIEJAS) {
            quitadas += quitarSiEs(level, base.getX() + vieja[0], nivel, base.getZ() + vieja[1], Blocks.TARGET);
            // Y la doble tenía DOS bloques apilados: el de encima también se va.
            quitadas += quitarSiEs(level, base.getX() + vieja[0], nivel + 1, base.getZ() + vieja[1], Blocks.TARGET);
        }
        boolean puesta = colocarSiEstaVacio(level, new BlockPos(base.getX() + BARRACA_DIANA[0], nivel,
                base.getZ() + BARRACA_DIANA[1]), Blocks.TARGET.defaultBlockState());
        BlockPos doble = new BlockPos(base.getX() + BARRACA_DIANA_DOBLE[0], nivel, base.getZ() + BARRACA_DIANA_DOBLE[1]);
        puesta |= colocarSiEstaVacio(level, doble, Blocks.TARGET.defaultBlockState());
        puesta |= colocarSiEstaVacio(level, doble.above(), Blocks.TARGET.defaultBlockState());
        if (quitadas > 0 || puesta) {
            DevilRpg.LOGGER.info("[Village] Barraca de {}: dianas al patio ({} fuera, patio {})", center, quitadas,
                    puesta ? "puesto" : "ocupado");
        }
    }

    /**
     * Pasa <b>todo</b> lo de un arca a otra (uniendo pilas primero y usando los huecos después). Hace falta porque
     * reemplazar un cofre <b>tira su contenido</b> al suelo (mecánica de vanilla, I6): el arca vieja solo se retira
     * cuando lo suyo ya está en la nueva. Lo que <b>no quepa</b> se queda donde estaba (y entonces el arca no se
     * retira), así que no se pierde nada.
     */
    private static void traspasarElArca(ServerLevel level, BlockPos origen, BlockPos destino) {
        if (!(level.getBlockEntity(origen) instanceof Container de)
                || !(level.getBlockEntity(destino) instanceof Container a)) {
            return;
        }
        for (int i = 0; i < de.getContainerSize(); i++) {
            ItemStack pila = de.getItem(i);
            if (pila.isEmpty()) {
                continue;
            }
            de.setItem(i, VillagePantry.guardar(a, pila.copy()));
        }
        de.setChanged();
        a.setChanged();
    }

    /**
     * ¿Está <b>vacío</b> ese contenedor? Sirve para poder <b>retirar</b> un cofre sin perder nada (I6): si no es un
     * contenedor (o no tiene BlockEntity) no hay nada que perder.
     */
    private static boolean contenedorVacio(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof Container contenedor)) {
            return true;
        }
        for (int i = 0; i < contenedor.getContainerSize(); i++) {
            if (!contenedor.getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
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
     * La <b>escalera</b> sube de medio en medio bloque ({@link #BARRACA_ESCALONES}) pegada al muro este, con la cara
     * alta mirando <b>hacia donde se sube</b> (al norte) y con el <b>hueco del forjado</b> encima de los escalones que
     * pasan por debajo de él —no en la celda del último, que es del escalón (ver
     * {@link #arreglarLaEscaleraDeLaBarraca})—.
     * <p>
     * Todo pasa por {@link #colocar}, así que <b>entra en el plano</b> y el obrero la repone como cualquier otra
     * construcción (invariante I8): una barraca construida al margen del plano no se repararía nunca.
     */
    private static BlockPos barraca(ServerLevel level, BlockPos base, int nivel) {
        int r = BARRACA_RADIO;
        int bx = base.getX();
        int bz = base.getZ();
        int yPiso2 = nivel + BARRACA_PISO2;   // suelo del dormitorio (el forjado va en yPiso2 - 1)
        int yTejado = yPiso2 + 3;
        // 1) Huella NIVELADA a la cota del pueblo, como las casas: recorta el terreno natural que sobra y
        //    RELLENA lo que falta. Hace falta de verdad: medido en el guardado, el cuadrante oeste de alguna aldea
        //    tiene un charco (23 columnas de agua en la capa de superficie) y en otra faltaba el bloque de suelo
        //    en 12 columnas: sin esto, el suelo de la barraca quedaría flotando. `nivelarHuella` toma la ESQUINA
        //    (y solo mira su X/Z, pero se le da una Y que ya es la cota: invariante I1).
        nivelarHuella(level, new BlockPos(bx - r, nivel, bz - r), 2 * r + 1, 2 * r + 1, nivel);
        // 1b) Y SE DESPEJA EL VOLUMEN DEL PISO DE ARRIBA, si lo hubiera: una barraca del trazado VIEJO (dos pisos) que
        //     se rehace deja arriba su forjado, sus paredes y su tejado en `nivel + 3 .. nivel + 8`; si no se limpian,
        //     quedan FLOTANDO sobre la barraca nueva (I14). Se limpia un bloque de más por lado, como el alero.
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                for (int dy = 3; dy <= BARRACA_PISO2_VIEJO + 4; dy++) {
                    colocar(level, new BlockPos(bx + dx, nivel + dy, bz + dz), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        // 2) SUELOS: piedra en la capa de superficie (nivel-1), el volumen de abajo al aire, el FORJADO del piso de
        //    arriba (tablones) y el volumen del dormitorio también al aire.
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                colocar(level, new BlockPos(bx + dx, nivel - 1, bz + dz), Blocks.STONE_BRICKS.defaultBlockState(), 3);
                for (int dy = 0; dy <= 2; dy++) {
                    colocar(level, new BlockPos(bx + dx, nivel + dy, bz + dz), Blocks.AIR.defaultBlockState(), 3);
                }
                // EL FORJADO SOLO SI HAY PISO ARRIBA: con la barraca de un solo nivel (BARRACA_PISO2 = 0) el forjado
                // caería en `nivel - 1`, que es EL SUELO DE PIEDRA de la sala de armas, y lo cambiaría por tablones.
                if (BARRACA_PISO2 > 0) {
                    colocar(level, new BlockPos(bx + dx, yPiso2 - 1, bz + dz),
                            Blocks.OAK_PLANKS.defaultBlockState(), 3);
                    for (int dy = 0; dy <= 2; dy++) {
                        colocar(level, new BlockPos(bx + dx, yPiso2 + dy, bz + dz),
                                Blocks.AIR.defaultBlockState(), 3);
                    }
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
        // 5) LA SALA DE ARMAS (piso de abajo): maniquíes de paja (para ensayar el golpe), dianas de los arqueros y el
        //    hogar con su fuego. SIN puestos de trabajo de aldeano: la mesa de CARTOGRAFÍA que había aquí era el
        //    puesto del cartógrafo —un oficio que este pueblo no tiene, así que un aldeano sin oficio lo reclamaría—
        //    y, además, caía en la celda de la PACA del maniquí sureste (se coloca después y se la comía). Se quitó
        //    en la migración 60: ver `quitarLaMesaDeLaBarraca`.
        for (int[] m : BARRACA_MANIQUIES) {
            colocar(level, new BlockPos(bx + m[0], nivel, bz + m[1]), Blocks.HAY_BLOCK.defaultBlockState(), 3);
            colocar(level, new BlockPos(bx + m[0], nivel + 1, bz + m[1]), Blocks.CARVED_PUMPKIN.defaultBlockState(), 3);
            colocar(level, new BlockPos(bx + m[0], nivel, bz + m[1] + 1), Blocks.OAK_FENCE.defaultBlockState(), 3);
            colocar(level, new BlockPos(bx + m[0], nivel + 2, bz + m[1]), Blocks.OAK_FENCE.defaultBlockState(), 3);
        }
        // LAS TRES DIANAS de los arqueros, y OJO CON EL ORDEN (lo que se coloca después gana): la suelta iba en la
        // celda del maniquí suroeste, que es la del ARCA de la sala de armas (se coloca unas líneas más abajo), así
        // que el arca se la comía y en el mundo quedaban DOS. Ahora va pegada a las paredes del rincón suroeste
        // (BARRACA_DIANA), que es una celda libre y ninguna otra pieza la usa.
        colocar(level, new BlockPos(bx + BARRACA_DIANA[0], nivel, bz + BARRACA_DIANA[1]),
                Blocks.TARGET.defaultBlockState(), 3);
        colocar(level, new BlockPos(bx + BARRACA_DIANA_DOBLE[0], nivel, bz + BARRACA_DIANA_DOBLE[1]),
                Blocks.TARGET.defaultBlockState(), 3);
        colocar(level, new BlockPos(bx + BARRACA_DIANA_DOBLE[0], nivel + 1, bz + BARRACA_DIANA_DOBLE[1]),
                Blocks.TARGET.defaultBlockState(), 3);
        colocar(level, new BlockPos(bx, nivel - 1, bz + r - 1), Blocks.CAMPFIRE.defaultBlockState(), 3);
        colocar(level, new BlockPos(bx - 2, nivel, bz + r - 2), Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH), 3);
        // 6) LA ESCALERA al dormitorio: BARRACA_ESCALONES escalones de medio bloque (los del juego), pegados al muro
        //    ESTE y subiendo al NORTE, con la CARA ALTA mirando hacia donde se sube (`FACING` = norte). Con el
        //    `FACING` al oeste —como estaba— la cara alta quedaba de través y la escalera no se subía. El pie va dos
        //    celdas al norte del muro sur (con sitio para ENTRAR por su lado bajo) y el último escalón queda EN LA
        //    CAPA DEL FORJADO con su cara alta a la altura del suelo del dormitorio (se sale andando, sin saltar).
        for (int k = 0; k < BARRACA_ESCALONES; k++) {
            BlockPos escalon = new BlockPos(bx + BARRACA_ESCALERA_DX, nivel + k,
                    bz + BARRACA_ESCALERA_PIE_DZ - k);
            colocar(level, escalon, Blocks.OAK_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.NORTH).setValue(StairBlock.HALF, Half.BOTTOM), 3);
            colocar(level, escalon.above(), Blocks.AIR.defaultBlockState(), 3);
        }
        // 6b) EL HUECO DEL FORJADO, encima de los escalones que pasan POR DEBAJO de él (los tres primeros): son las
        //     TRES celdas que necesita el que sube (I26: el juego levanta al jugador 0,6 al ganar un escalón y
        //     comprueba la caja entera, 1,8 + 0,6 = 2,4). OJO: la celda del ÚLTIMO escalón NO es un hueco —el
        //     escalón vive en esa misma capa—; abrirla lo BORRABA (era el fallo que reportó el jugador: la escalera
        //     se quedaba a 1,0 del suelo del dormitorio y "solo se sube saltando").
        for (int k = 0; k < BARRACA_ESCALONES - 1; k++) {
            colocar(level, new BlockPos(bx + BARRACA_ESCALERA_DX, yPiso2 - 1, bz + BARRACA_ESCALERA_PIE_DZ - k),
                    Blocks.AIR.defaultBlockState(), 3);
        }
        // 7) EL DORMITORIO (piso de arriba): las camas en dos filas con el pasillo en medio, con sus arcas y sus
        //    faroles. Son las que dan litera a los guardias y las que dejan crecer al pueblo (vanilla pide una cama
        //    libre por cría). La fila del SUR no lleva cama en la columna de la escalera: ver BARRACA_CAMAS_SUR.
        for (int dx : BARRACA_CAMAS_NORTE) {
            bed(level, new BlockPos(bx + dx, yPiso2, bz - r + 2), Direction.NORTH);
        }
        for (int dx : BARRACA_CAMAS_SUR) {
            bed(level, new BlockPos(bx + dx, yPiso2, bz + r - 2), Direction.SOUTH);
        }
        // Las DOS arcas, juntas contra el muro oeste (forman un cofre doble): la del muro este estaba en la celda
        // del último escalón y el que subía se la encontraba de frente (BARRACA_ARCA_DZ).
        colocar(level, new BlockPos(bx - r + 1, yPiso2, bz + BARRACA_ARCA_DZ), Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.EAST), 3);
        colocar(level, new BlockPos(bx - r + 1, yPiso2, bz + BARRACA_ARCA_DZ + 1), Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.EAST), 3);
        for (int dz = -r + 2; dz <= r - 2; dz += 3) {
            // COLGADOS del tejado (que va justo en la celda de arriba, BARRACA_FAROL_DY): un farol POSADO aquí no
            // tiene NADA debajo —el dormitorio está al aire— y quedaba flotando, sin cadena (I14). Medido en el
            // guardado: 2 faroles así en el dormitorio de cada barraca (aldeas 0 y 2).
            colgar(level, new BlockPos(bx, nivel + BARRACA_FAROL_DY, bz + dz));
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

    /** ¿Ese punto (X/Z) está <b>dentro del corralillo de las gallinas</b>? (su caja vive aquí, I4/I96). */
    public static boolean estaEnElGallinero(BlockPos center, BlockPos p) {
        BlockPos base = baseDeAnexo(center);
        int dx = p.getX() - base.getX();
        int dz = p.getZ() - base.getZ();
        return dx >= GALLINERO_X0 && dx <= GALLINERO_X1 && dz >= GALLINERO_Z0 && dz <= GALLINERO_Z1;
    }

    /**
     * <b>EL PRIMER TRAMO CUANDO HAY UN PORTÓN DE VALLA DE POR MEDIO: el portón.</b>
     * <p>
     * El juego <b>no le deja planificar el camino a un aldeano a través de una puerta de valla cerrada</b> (lo dice
     * I44 y es lo que mide el arnés: {@code createPath} devuelve {@code alcance=NO} con el destino al otro lado), así
     * que un aldeano que se queda <b>fuera</b> del recinto con su faena <b>dentro</b> (o al revés) no camina hacia el
     * portón: se queda pegado a la valla dando vueltas y acaba <b>aparcando el destino 5 min</b> (I33). Medido con el
     * arnés, aldea 0: el ganadero Zacarias, <b>16 s</b> clavado en {@code 516,63,636} —pegado a la valla NORTE del
     * corral— con un huevo de la paja <b>al otro lado</b>, y el destino aparcado en {@code 516,64,639}; y antes, con
     * el almacén al otro lado de la valla, el mismo aldeano aparcó el punto de apoyo del almacén estando dentro.
     * <p>
     * Esto devuelve a qué <b>portón</b> hay que ir <b>primero</b> (o {@code null} si no hay ninguno de por medio y se
     * va directo al destino). El portón solo cuenta si está <b>cerrado</b>: abierto, el camino se planifica solo.
     * Y el orden es el de las cajas, de fuera adentro: para entrar en el <b>corralillo</b> desde fuera del corral,
     * primero la <b>cerca grande</b>; para salir del corralillo, primero <b>su</b> portón.
     */
    @Nullable
    public static BlockPos primerTramoDelPorton(ServerLevel level, BlockPos center, int nivel, BlockPos desde,
                                                BlockPos destino) {
        if (nivel <= level.getMinBuildHeight() + 1) {
            return null;
        }
        boolean yoCorral = estaEnElAnexo(center, desde);
        boolean suCorral = estaEnElAnexo(center, destino);
        boolean yoGallinero = estaEnElGallinero(center, desde);
        boolean suGallinero = estaEnElGallinero(center, destino);
        if (yoGallinero != suGallinero) {
            // Del corralillo al corral (o al revés). Para ENTRAR desde fuera del corral, primero la cerca grande.
            if (!yoGallinero && !yoCorral) {
                BlockPos porton = portonDelCorral(center, nivel);
                return estaCerrado(level, porton) ? porton : null;
            }
            BlockPos porton = portonDelGallinero(center, nivel);
            return estaCerrado(level, porton) ? porton : null;
        }
        if (yoCorral != suCorral) {
            BlockPos porton = portonDelCorral(center, nivel);
            return estaCerrado(level, porton) ? porton : null;
        }
        return null;
    }

    /** ¿Ese portón de valla está ahí y <b>cerrado</b>? (abierto, el aldeano planifica el camino solo). */
    private static boolean estaCerrado(ServerLevel level, BlockPos porton) {
        BlockState estado = level.getBlockState(porton);
        return estado.getBlock() instanceof FenceGateBlock && !estado.getValue(FenceGateBlock.OPEN);
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
        // EL HUECO DE UN PORTÓN ES SAGRADO (I54): un farol posado en la HOJA de una puerta de valla deja la puerta
        // INSERVIBLE —el que cruza ocupa con el cuerpo la celda del farol— y el juego deja de encontrar el camino,
        // así que el aldeano se queda encerrado. El layout viejo del corral ponía un farol en el MEDIO de cada lado
        // de la valla, y el medio del lado oeste ES el portón. Aquí no se pone, pase lo que pase.
        if (abajo.getBlock() instanceof FenceGateBlock) {
            return 0;
        }
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
     * <b>Auto-comprobación de faroles flotantes</b>: recorre el recinto de la aldea y cuenta los faroles <b>sin
     * apoyo</b> (la misma prueba que hace el juego para ponerlos, {@code Block.canSupportCenter}). Se llama al
     * <b>terminar de generar</b> y al <b>terminar de migrar</b> (no en el latido: son ~80.000 bloques) y, si
     * encuentra alguno, lo <b>grita en el log</b> con sus posiciones: el bug de los 16 faroles colgados del aire
     * (14 en la cerca de la granja anexa y 2 en la pesquera) no puede volver en silencio.
     * <p>
     * <b>OJO: cada farol se sostiene por el lado que dice SU PROPIO estado</b> ({@code hanging}): uno <b>colgado</b>
     * necesita un bloque sólido <b>encima</b> y uno <b>posado</b> lo necesita <b>debajo</b>. Mirando los dos lados a
     * la vez —como se hacía antes— se colaban los dos casos que aparecieron en el guardado del jugador (migración
     * 55): un {@code hanging=true} colgado del aire <b>sobre un poste</b> (los faroles de las puntas del porche de la
     * taberna) y un {@code hanging=false} posado en el aire <b>bajo el tejado</b> (los del dormitorio de la barraca),
     * porque en los dos había "algo" al otro lado. Medido: <b>4</b> faroles así en la aldea 2 (2 + 2) y los mismos 4
     * más 14 de la cerca del corral en la 0.
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
                    BlockState estado = level.getBlockState(p);
                    if (!estado.is(Blocks.LANTERN)) {
                        continue;
                    }
                    boolean colgado = estado.getValue(LanternBlock.HANGING);
                    boolean bien = colgado
                            ? Block.canSupportCenter(level, p.above(), Direction.DOWN)
                            : Block.canSupportCenter(level, p.below(), Direction.UP);
                    if (bien) {
                        continue;
                    }
                    flotantes++;
                    if (flotantes <= 10) {
                        DevilRpg.LOGGER.warn("[Village] FAROL SIN APOYO en {} (aldea en {}): {} y {}",
                                p, center, colgado ? "colgado" : "posado",
                                colgado ? "sin bloque encima" : "sin bloque debajo");
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
                BlockState estado = level.getBlockState(alto);
                if (!estado.is(Blocks.LANTERN)) {
                    continue;
                }
                // ¿ES SU FAROL, ya en su sitio? (a un bloque del apoyo y POSADO, no colgado): entonces no se toca.
                // Esto corre en cada latido y antes quitaba y volvía a poner los doce faroles del corral cada 10 s
                // (lo delataba el log: "14 faroles puestos en la cerca del corral anexo" una y otra vez, para
                // siempre) — reconstruir lo que ya está bien es justo lo que prohíbe I6, y encima borraba el farol
                // del jugador que estuviera en esa vertical. Solo se muda lo que de verdad está fuera de su sitio:
                // lo que cuelga de un poste (I14, migración 55) o lo que quedó a 2-3 bloques del apoyo.
                if (dy == 1 && !estado.getValue(LanternBlock.HANGING)) {
                    break;
                }
                colocar(level, alto, Blocks.AIR.defaultBlockState(), 3);   // el farol flotante (o el colgado de un poste)
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
     * Dónde está la <b>pesquera</b> (el centro de su lago), relativa al centro: en el campo del <b>sureste</b>, que es
     * el cuadrante que queda libre (la taberna está al este-norte, el almacén y el corral al este, los bancales al
     * oeste y al suroeste). Es lo que pidió el jugador: <i>"el pescador tendrá su edificio y su lago más adelante"</i>.
     * <p>
     * El sitio <b>vive en {@link #TRAZADO}</b> (índice 7) como el de cualquier otro edificio de planta: antes tenía
     * aquí su propia copia de las coordenadas y el jugador lo notó al revisar el código —*"la constante TRAZADO no
     * tiene la choza del pescador, ¿por qué?"*—, que es el patrón que I4 prohíbe (un número en dos sitios que se
     * pueden desincronizar).
     */
    private static final int[] PESQUERA = TRAZADO[7];
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
     * ¿Está la pesquera hecha? Vale el <b>agua del lago</b> (o su <b>hielo</b>: es la misma agua) o el <b>barril</b>
     * (el puesto): con cualquiera de los dos se da por hecha, así que hace falta perder los dos para que el pueblo la
     * reconstruya (reconstruirla volvería a soltar peces y podría deshacer lo que el jugador haya puesto alrededor).
     * <p>
     * OJO: eso es solo el testigo de "está construida", <b>no</b> de "está entera". A una pesquera con el barril en
     * pie pero el lago <b>seco</b> (el caso del guardado del jugador) la repone
     * {@link #repararLagoDeLaPesquera}, que es quien mira el estanque entero.
     */
    public static boolean pesqueraConstruida(ServerLevel level, BlockPos center) {
        int nivel = cotaDeLaPlaza(level, center);
        BlockPos base = baseDeLaPesquera(center);
        return esAguaOHielo(level.getBlockState(new BlockPos(base.getX(), nivel - 1, base.getZ())))
                || level.getBlockState(puestoDelPescador(level, center)).is(Blocks.BARREL);
    }

    /**
     * Asegura la <b>pesquera</b> (etapa G): el lago con su bandada, la caseta del pescador, su <b>barril</b> (el
     * puesto), la pasarela y los faroles. Idempotente (ver {@link #pesqueraConstruida}); se llama al generar, en la
     * migración y en el latido, como el resto de edificios del pueblo.
     * <p>
     * OJO: {@link #pesqueraConstruida} se conforma con el <b>barril</b> (o el agua) como testigo, así que una
     * pesquera a la que le falta <b>el agua</b> o el <b>barril</b> se da por hecha y no se rehace nunca. Por eso,
     * cuando ya está construida, se le reponen las dos cosas sueltas: el agua del lago
     * ({@link #repararLagoDeLaPesquera}) y el barril (su puesto). Las dos reparaciones son idempotentes y baratas.
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
            // El estanque, de vuelta si un nivelado lo tapó (y el barril, si se perdió con el agua en pie).
            repararLagoDeLaPesquera(level, center, base, nivel);
            asegurarElBarrilDelPescador(level, center, base, nivel);
            return;
        }
        pesquera(level, base, nivel);
        DevilRpg.LOGGER.info("[Village] Aldea en {}: pesquera construida en {} (lago de {}x{}, {} pez(ces) y su"
                + " barril)", center, baseDeLaPesquera(center), 2 * LAGO_RADIO + 1, 2 * LAGO_RADIO + 1,
                LAGO_PECES_INICIAL);
    }

    /**
     * <b>¿Le queda agua al lago?</b> Vale el <b>hielo</b> de un bioma frío (es la misma agua, ver
     * {@link #esAguaOHielo}) y da el lago por lleno cuando el agua es <b>la mitad o más</b> de la capa de arriba
     * de su huella: así los tres postes de la <b>pasarela</b>, que ocupan tres celdas de esa capa, no dan el lago
     * por seco, y un lago a medias (una orilla comida) también se repone.
     */
    private static boolean lagoConAgua(ServerLevel level, BlockPos base, int nivel) {
        int agua = 0;
        for (int dx = -LAGO_RADIO; dx <= LAGO_RADIO; dx++) {
            for (int dz = -LAGO_RADIO; dz <= LAGO_RADIO; dz++) {
                if (esAguaOHielo(level.getBlockState(new BlockPos(base.getX() + dx, nivel - 1, base.getZ() + dz)))) {
                    agua++;
                }
            }
        }
        return agua * 2 >= (2 * LAGO_RADIO + 1) * (2 * LAGO_RADIO + 1);
    }

    /**
     * <b>Devuelve el agua al lago de la pesquera</b> de una aldea ya construida (con su orilla de arena y su fondo),
     * <b>solo</b> en las celdas del lago y <b>solo</b> donde no haya nada construido. Devuelve cuántas celdas ha
     * llenado.
     * <p>
     * Hace falta de verdad, y está <b>medido en el guardado del jugador</b> (aldea 2, centro {@code 1414,1414},
     * cota 120, base del lago {@code 1434,1458}): la pesquera se construyó en la <b>migración 46</b> y las
     * migraciones siguientes volvieron a llamar a {@code farm(level, center)}, que <b>nivela la aldea entera</b>
     * ({@code prepararTerreno} → {@code nivelar}). El nivelado trataba el agua como terreno que sobra, así que
     * rellenó el hueco del lago con <b>tierra</b> en {@code cota-2} y <b>césped</b> en {@code cota-1}: en el
     * guardado quedaban <b>3 celdas de agua</b> de 49 (las tres columnas de los postes de la pasarela, que el
     * nivelado se saltó al toparse con la valla) y el resto era césped. El jugador lo vio como una plaza de césped
     * con la pasarela y los dos faroles encima: <i>"¿por qué la choza para pesca no tiene su estanque para
     * pescar?"</i>. Y no se reparaba solo porque el <b>barril</b> seguía en pie: con él, {@link #pesqueraConstruida}
     * daba la pesquera por hecha.
     * <p>
     * El agua ya no se puede volver a tapar (en {@code nivelar} y {@code nivelarHuella} el agua y el hielo no son un
     * hueco que se rellene), así que esto es lo que arregla las aldeas que ya se quedaron secas. La geometría es
     * <b>la misma</b> que la de {@code pesquera()}: agua a dos capas con el fondo y la orilla de arena.
     */
    public static int repararLagoDeLaPesquera(ServerLevel level, BlockPos center) {
        int nivel = cotaDeLaPlaza(level, center);
        if (nivel <= level.getMinBuildHeight() + 2) {
            return 0;
        }
        return repararLagoDeLaPesquera(level, center, baseDeLaPesquera(center), nivel);
    }

    /** El reparador del lago, con la base y la cota ya calculadas (ver {@link #repararLagoDeLaPesquera}). */
    private static int repararLagoDeLaPesquera(ServerLevel level, BlockPos center, BlockPos base, int nivel) {
        if (lagoConAgua(level, base, nivel)) {
            return 0; // el lago ya está lleno: ni una celda (esto corre también en el latido)
        }
        int bx = base.getX();
        int bz = base.getZ();
        int puestas = 0;
        for (int dx = -LAGO_RADIO - 1; dx <= LAGO_RADIO + 1; dx++) {
            for (int dz = -LAGO_RADIO - 1; dz <= LAGO_RADIO + 1; dz++) {
                boolean dentroDelLago = Math.abs(dx) <= LAGO_RADIO && Math.abs(dz) <= LAGO_RADIO;
                // La capa que se pisa: agua dentro del lago y ARENA en la orilla (la orilla seca que evita que el
                // agua haga cuadros con el césped).
                puestas += anegar(level, new BlockPos(bx + dx, nivel - 1, bz + dz),
                        dentroDelLago ? Blocks.WATER : Blocks.SAND);
                if (dentroDelLago) {
                    // La segunda capa de agua y el fondo de arena, como los pone `pesquera()`.
                    puestas += anegar(level, new BlockPos(bx + dx, nivel - 2, bz + dz), Blocks.WATER);
                    puestas += anegar(level, new BlockPos(bx + dx, nivel - 3, bz + dz), Blocks.SAND);
                }
            }
        }
        if (puestas > 0) {
            DevilRpg.LOGGER.info("[Village] Aldea en {}: lago de la pesquera en {} vuelto a llenar ({} celda(s):"
                    + " un nivelado lo habia tapado)", center, base, puestas);
        }
        return puestas;
    }

    /**
     * Pone {@code bloque} en esa celda del lago <b>solo</b> si ahí hay <b>aire, agua (o hielo) o terreno blando</b>
     * ({@link #sePuedeAnegar}) y solo si no está ya. Devuelve 1 si ha colocado algo.
     */
    private static int anegar(ServerLevel level, BlockPos pos, Block bloque) {
        BlockState actual = level.getBlockState(pos);
        if (actual.is(bloque) || !sePuedeAnegar(actual)) {
            return 0;
        }
        colocar(level, pos, bloque.defaultBlockState(), 3);
        return 1;
    }

    /**
     * ¿Esa celda del lago se puede volver a llenar? Sí para el <b>aire</b>, el <b>agua</b> (o su hielo) y el
     * <b>terreno blando</b> con el que un nivelado tapa un hueco (tierra, césped, arena, nieve…); <b>no</b> para una
     * obra (tablones de la pasarela, postes, el barril, ladrillo) ni para la <b>piedra</b> del terreno.
     * <p>
     * Es lo que garantiza que el reparador del lago no inunde ni rompa nada que no sea el relleno del nivelado: ni
     * la pasarela del pescador ni lo que haya puesto el jugador.
     */
    private static boolean sePuedeAnegar(BlockState state) {
        return state.isAir() || esAguaOHielo(state) || esTierraPisoteada(state)
                || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY)
                || state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.MUD)
                || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.FARMLAND);
    }

    /**
     * El <b>barril</b> del pescador (su puesto de trabajo en vanilla) es uno de los dos testigos de la pesquera, así
     * que si se pierde <b>con el agua en pie</b> nadie lo reponía: {@link #pesqueraConstruida} ya la daba por hecha y
     * el pescador se quedaba sin oficio. Aquí se devuelve a su celda, y <b>solo si está vacía</b>: no se pisa nada de
     * lo que haya ahí (ni un cofre que el jugador haya dejado en su sitio).
     */
    private static void asegurarElBarrilDelPescador(ServerLevel level, BlockPos center, BlockPos base, int nivel) {
        BlockPos puesto = puestoDelPescador(base, nivel);
        if (!level.getBlockState(puesto).isAir()) {
            return;
        }
        colocar(level, puesto, Blocks.BARREL.defaultBlockState(), 3);
        DevilRpg.LOGGER.info("[Village] Aldea en {}: barril del pescador repuesto en {}", center, puesto);
    }

    /**
     * ¿Esa posición es una de las celdas del <b>lago de la pesquera</b> (su agua, su orilla de arena o su fondo)?
     * <p>
     * Sirve para <b>dejar el lago FUERA del plano</b> (ver {@code captureBlueprint}): su agua la mantiene
     * {@link #repararLagoDeLaPesquera}, que da el lago por bueno también con <b>hielo</b> (que es lo que tiene que
     * haber en un bioma frío). Si el plano la pidiera, el obrero se pasaría la vida <b>descongelando</b> el lago
     * —{@code necesitaReparacion} repone el agua en cuanto la ve congelada, regla que hace falta para la
     * <b>acequia</b>, que va tapada con una losa y por eso no se congela—: un tirón de agua cada pocos segundos
     * para nada.
     */
    private static boolean esCeldaDelLago(BlockPos center, int nivel, BlockPos pos) {
        BlockPos base = baseDeLaPesquera(center);
        int dx = pos.getX() - base.getX();
        int dz = pos.getZ() - base.getZ();
        if (Math.abs(dx) > LAGO_RADIO + 1 || Math.abs(dz) > LAGO_RADIO + 1) {
            return false;
        }
        if (pos.getY() == nivel - 1) {
            return true; // el agua y la orilla (la arena que la rodea)
        }
        boolean dentroDelLago = Math.abs(dx) <= LAGO_RADIO && Math.abs(dz) <= LAGO_RADIO;
        return dentroDelLago && (pos.getY() == nivel - 2 || pos.getY() == nivel - 3); // la 2ª capa y el fondo
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

    // --- LA MINA DEL PUEBLO (etapa I: el MINERO) ----------------------------------------------------

    /**
     * <b>Centro de la mina</b> (el eje del caracol), relativo al centro de la aldea.
     * <p>
     * El jugador pidió primero que la mina fuera dentro de la muralla, y después —al ver la caseta construida— que se
     * <b>apartara del centro</b>: *"mueve la cabaña del minero porque está muy cerca del centro, ponlo más bien en un
     * lugar cercano al muro y donde haya mucho espacio que no se haya utilizado aún"*. El solar nuevo
     * ({@code rel (+33,-29)}, base {@code (498,612)}, eje {@code (503,617)}) lo buscó {@code build/solar_mina2.py} en
     * su guardado: <b>625 de 625</b> celdas libres en un entorno de 25×25 (el descampado del noreste), a <b>44</b> del
     * centro (a 18 del muro) y con el subsuelo macizo (la peor racha de agua de las columnas del caracol es de
     * <b>5</b> celdas, muy por debajo del tope con el que el minero da la mina por terminada).
     */
    private static final BlockPos MINA_OFFSET = new BlockPos(33, 0, -29);

    /**
     * <b>EL SEGUNDO POZO</b> (28-sep-2026, lo pidió el jugador: *«estaría bien abrir un segundo pozo»*): el descampado
     * del <b>suroeste</b>, simétrico al primero. <b>MEDIDO</b> con `tools/arnes/columna_mina.py` sobre el guardado del
     * jugador (galería del paso 16, 24 celdas): **sin agua** (el primero se cavó sobre un acuífero y sus galerías
     * salen en `water`), **sin nada construido** y con su zona de exclusión **separada 30 bloques** de la del primero
     * (el candidato del noroeste solo 8). Los otros dos candidatos: el NO sin agua pero pegados, y el SE con agua.
     * <p>
     * lint:ok I9 porque esto NO construye nada: el segundo pozo no lo pone el generador, lo **cava el minero** (es la
     * misma faena de siempre, en otro eje), así que **no hay nada que rehacer** en las aldeas ya construidas.
     */
    private static final BlockPos MINA_OFFSET_2 = new BlockPos(-33, 0, 29);

    /** <b>Los pozos de la mina</b>, en orden: el 0 es el de siempre (noreste) y el 1 el segundo (suroeste). */
    private static final BlockPos[] MINA_OFFSETS = {MINA_OFFSET, MINA_OFFSET_2};
    /** Cuántos pozos tiene una aldea (ver {@link #elegirElPozoActivo}). */
    public static final int POZOS_DE_LA_MINA = MINA_OFFSETS.length;

    /**
     * <b>Qué pozo está cavando el minero AHORA</b>, por aldea. Es un caché (como el de {@link #cotaDeLaPlaza}) para
     * que {@link #centroDeLaMina} no tenga que cambiar de firma en sus once usos: el minero llama a
     * {@link #elegirElPozoActivo} al empezar su vuelta y todo lo demás (el caracol, las galerías, el progreso) sale
     * del pozo que toque.
     */
    private static final java.util.Map<Long, Integer> POZO_ACTIVO = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * El solar de la mina <b>de antes</b> ({@code rel (-9,+12)}): la caseta se construyó ahí (el jugador la vio
     * pegada al centro y con la cama asomando por la puerta) y {@link #deshacerLaMinaVieja} la retira devolviendo el
     * terreno del pueblo. Es geometría <b>heredada</b>: no se usa para construir nada.
     */
    private static final BlockPos MINA_OFFSET_VIEJO = new BlockPos(-9, 0, 12);
    /** Radio del caracol: sus escalones van a 4 del eje (un anillo de 9×9 dentro del solar). */
    public static final int MINA_RADIO = 4;
    /**
     * Un <b>marco de madera</b> (dos postes y su viga) cada X escalones del caracol. Son 16 escalones = <b>8 bloques
     * de descenso</b>: los mismos que hay entre galería y galería, así que el caracol lleva un marco por planta.
     * <p>
     * Antes eran cada 4 escalones (2 bloques) y eso son <b>ocho marcos por vuelta</b>: la madera del pueblo
     * (el leñador trae troncos y el herrero de herramientas los asierra, con un objetivo de 32 tablones) no da para
     * 40 tablones por vuelta, así que el minero se pasaría la vida esperando tablones en vez de cavando.
     */
    public static final int MINA_SOPORTE_CADA = 16;
    /** Cada cuántos bloques de descenso se abre una <b>galería</b>, y cuánto se adentra. */
    public static final int MINA_GALERIA_CADA = 8;
    public static final int MINA_GALERIA_LARGO = 24;
    /** Un marco de madera cada X celdas <b>dentro</b> de una galería (el de la boca lo pone el caracol). */
    public static final int MINA_GALERIA_SOPORTE_CADA = 8;
    /** Hasta dónde baja: el bedrock del overworld está en −64, así que se para antes (y en la lava). */
    public static final int MINA_FONDO = -58;
    /**
     * Radio de la <b>zona de la mina</b>: hasta dónde llega (las galerías salen 24 del eje). Es lo que se usa para
     * <b>excluirla</b> del plano, del nivelado y del tapagujeros del suelo.
     */
    public static final int MINA_EXCLUSION_RADIO = MINA_RADIO + MINA_GALERIA_LARGO + 1;

    /**
     * Radio del <b>pozo</b> (la boca y el caracol): lo que el minero abre <b>cerca de la superficie</b>. Es el que
     * usan las reparaciones que miran la capa del suelo (el tapagujeros de I90 y el plano), porque ahí el minero
     * solo ha tocado esas celdas: usar el radio de toda la zona ({@link #MINA_EXCLUSION_RADIO}) dejaría sin reparar
     * el suelo de media aldea.
     */
    public static final int MINA_POZO_RADIO = MINA_RADIO + 2;

    /**
     * El eje de la mina (el centro del caracol y de la caseta). Se calcula como un <b>desplazamiento</b> del centro
     * de la aldea ({@code center.offset}) a propósito: la <b>Y</b> de aquí no se usa para nada (I1) —toda altura sale
     * de {@link #cotaDeLaPlaza}—, así que no se toca.
     */
    public static BlockPos centroDeLaMina(BlockPos center) {
        return centroDeLaMina(center, pozoActivo(center));
    }

    /** El eje del pozo {@code pozo} de esa aldea (ver {@link #MINA_OFFSETS}). */
    public static BlockPos centroDeLaMina(BlockPos center, int pozo) {
        return center.offset(MINA_OFFSETS[Math.floorMod(pozo, MINA_OFFSETS.length)]);
    }

    /** El pozo que esa aldea está cavando (0 si no se ha elegido ninguno todavía). */
    private static int pozoActivo(BlockPos center) {
        return POZO_ACTIVO.getOrDefault(center.asLong(), 0);
    }

    private static void fijarElPozoActivo(BlockPos center, int pozo) {
        POZO_ACTIVO.put(center.asLong(), pozo);
    }

    /**
     * <b>EL POZO QUE TOCA CAVAR</b>: el primero, empezando por el que estaba activo y dando la vuelta, que <b>no esté
     * terminado</b> ({@link #laMinaLlegoAlTope} con su {@link #progresoDeLaMina}). Si todos están topados se queda
     * donde estaba (no hay faena: el minero se quedará esperando, como antes).
     * <p>
     * Lo llama el <b>minero</b> al empezar su vuelta (`VillagerMinerGoal.canUse`), que es quien tiene el mundo
     * cargado: así el segundo pozo <b>se abre solo</b> en cuanto el primero llega a su tope, sin marcar nada a mano.
     */
    public static int elegirElPozoActivo(ServerLevel level, BlockPos center, int nivel) {
        int activo = pozoActivo(center);
        for (int intento = 0; intento < POZOS_DE_LA_MINA; intento++) {
            int pozo = Math.floorMod(activo + intento, POZOS_DE_LA_MINA);
            fijarElPozoActivo(center, pozo);
            int paso = progresoDeLaMina(level, center, nivel);
            if (!laMinaLlegoAlTope(level, center, nivel, paso)) {
                return pozo; // este pozo tiene faena: es el que se cava
            }
        }
        fijarElPozoActivo(center, activo); // todos topados: se deja como estaba
        return activo;
    }

    /**
     * Las celdas de <b>un anillo completo</b> del caracol, en orden de bajada: 8 celdas por lado a
     * {@link #MINA_RADIO} del eje, empezando en la esquina <b>noreste</b> (que es donde cae la <b>boca</b>, al lado
     * de la caseta) y girando en el sentido de las agujas del reloj.
     */
    private static final int[][] MINA_ANILLO = anilloDeLaMina();

    private static int[][] anilloDeLaMina() {
        List<int[]> celdas = new ArrayList<>();
        for (int dz = -MINA_RADIO; dz < MINA_RADIO; dz++) {
            celdas.add(new int[]{MINA_RADIO, dz});      // lado este, de norte a sur
        }
        for (int dx = MINA_RADIO; dx > -MINA_RADIO; dx--) {
            celdas.add(new int[]{dx, MINA_RADIO});      // lado sur, de este a oeste
        }
        for (int dz = MINA_RADIO; dz > -MINA_RADIO; dz--) {
            celdas.add(new int[]{-MINA_RADIO, dz});     // lado oeste, de sur a norte
        }
        for (int dx = -MINA_RADIO; dx < MINA_RADIO; dx++) {
            celdas.add(new int[]{dx, -MINA_RADIO});     // lado norte, de oeste a este
        }
        return celdas.toArray(new int[0][]);
    }

    /** Cuántos escalones tiene un anillo completo (32 con radio 4). */
    public static int escalonesPorVuelta() {
        return MINA_ANILLO.length;
    }

    /**
     * La <b>Y de la celda</b> del caracol número {@code paso}: baja <b>medio bloque por celda</b>.
     * <p>
     * Los dos bloques de cada <b>par</b> de escalones comparten Y (el caracol va por bloques enteros), así que la Y
     * de la celda {@code p} es {@code (nivel - 1) - (p + 1) / 2}: la vuelta entera (32 celdas) baja <b>16 bloques</b>.
     */
    public static int yDelCaracol(int nivel, int paso) {
        return (nivel - 1) - Math.floorDiv(paso + 1, 2);
    }

    /**
     * La celda del caracol número {@code paso} <b>contada desde el EJE de la mina</b>. Es la forma en la que la usa
     * la caseta (que ya trabaja con el eje) y la de {@link #celdaDelCaracol}, que le pasa el eje sacado del centro de
     * la aldea: tener dos cuentas —una desde el centro y otra desde el eje— fue justo el fallo que colocaba la
     * <b>boca trece bloques más allá</b> (medido con el arnés: la celda de la boca salía con césped y el minero no
     * tenía por dónde empezar).
     */
    private static BlockPos celdaDelCaracolDesdeElEje(BlockPos eje, int nivel, int paso) {
        int[] d = MINA_ANILLO[Math.floorMod(paso, MINA_ANILLO.length)];
        return new BlockPos(eje.getX() + d[0], yDelCaracol(nivel, paso), eje.getZ() + d[1]);
    }

    /**
     * La celda del caracol número {@code paso} ({@code 0} = la boca, pegada a la caseta): su posición con la
     * <b>Y de su pieza</b>. El {@code center} que recibe es el <b>centro de la aldea</b> (el eje de la mina es un
     * desplazamiento suyo, {@link #MINA_OFFSET}).
     * <p>
     * Baja <b>medio bloque por celda</b> y por eso la mina <b>se recorre andando en los dos sentidos</b> (I95: un
     * bloque entero <b>no</b> se sube; 0,5 sí, que es lo que sube un aldeano de un paso, {@code maxUpStep} 0,6).
     */
    public static BlockPos celdaDelCaracol(BlockPos center, int nivel, int paso) {
        return celdaDelCaracolDesdeElEje(centroDeLaMina(center), nivel, paso);
    }

    /**
     * La <b>pieza</b> de la celda {@code paso} del caracol: <b>losa</b> en los pares y <b>adoquín entero</b> en los
     * impares (la huella va a {@code y + 0,5} y a {@code y + 1}, o sea medio bloque de bajada por celda).
     * <p>
     * <b>Por qué LOSAS y no escaleras</b> (que es lo que uno espera de una "escalera en espiral"): una escalera
     * tiene su <b>cara alta en UNA dirección</b> (I26: la cara alta mira hacia donde se sube) y el anillo del caracol
     * tiene <b>esquinas</b>. En una esquina, la celda del escalón mira a la celda de la que se viene (p. ej. al
     * norte) y la que sigue está a un lado (p. ej. al oeste): el que sube sale del escalón por su <b>lado</b>, que
     * está a media altura, y el escalón de después está <b>un bloque entero</b> más abajo — un paso de 1,0 que
     * <b>no se sube</b>. La losa es <b>uniforme en las cuatro direcciones</b> (0,5 exactos de suelo a suelo, gire
     * como gire el anillo), así que la mina se sube por cualquier esquina. Medido en el banco de pruebas con el
     * arnés: el caracol de losas sube y baja entero, el de escaleras se atasca en la primera esquina.
     */
    public static BlockState piezaDelCaracol(int paso) {
        return Math.floorMod(paso, 2) == 0
                ? Blocks.COBBLESTONE_SLAB.defaultBlockState()
                : Blocks.COBBLESTONE.defaultBlockState();
    }

    /** ¿Ese paso del caracol es de <b>losa</b> (los pares)? Los marcos y las antorchas van en las losas. */
    public static boolean esLosaDelCaracol(int paso) {
        return Math.floorMod(paso, 2) == 0;
    }

    /**
     * Las <b>dos celdas de los lados</b> de una celda del caracol (las paredes del túnel): el radio va <b>por dentro
     * y por fuera</b> del anillo, que es por donde se abre el marco de madera (los postes van en las paredes).
     */
    public static BlockPos[] ladosDeLaCeldaDelCaracol(BlockPos center, int nivel, int paso) {
        return ladosDeLaCelda(celdaDelCaracol(center, nivel, paso), paso);
    }

    /** Los dos lados de una celda del caracol ya calculada (ver {@link #ladosDeLaCeldaDelCaracol}). */
    private static BlockPos[] ladosDeLaCelda(BlockPos celda, int paso) {
        int[] d = MINA_ANILLO[Math.floorMod(paso, MINA_ANILLO.length)];
        boolean porElEjeX = Math.abs(d[0]) >= Math.abs(d[1]);
        int sx = porElEjeX ? Integer.signum(d[0]) : 0;
        int sz = porElEjeX ? 0 : Integer.signum(d[1]);
        return new BlockPos[]{celda.offset(sx, 0, sz), celda.offset(-sx, 0, -sz)};
    }

    /** ¿Ese paso del caracol es de los que llevan <b>marco de madera</b> (cada {@link #MINA_SOPORTE_CADA})? */
    public static boolean llevaSoporte(int paso) {
        return paso > 0 && Math.floorMod(paso, MINA_SOPORTE_CADA) == 0;
    }

    /** ¿Ese paso abre <b>galería</b> (cada {@link #MINA_GALERIA_CADA} bloques de descenso)? */
    public static boolean abreGaleria(int paso) {
        // Medio bloque de bajada por celda, así que cada `MINA_GALERIA_CADA` bloques son 2*X pasos.
        return paso > 0 && Math.floorMod(paso, MINA_GALERIA_CADA * 2) == 0;
    }

    /**
     * La <b>dirección de la galería</b> que abre el caracol en ese paso: <b>hacia FUERA del pozo</b>, en cruz
     * (norte, sur, este u oeste según por dónde vaya el anillo).
     * <p>
     * <b>Tiene que ser hacia fuera</b>, y esto costó una tarde: con una dirección que gira sin mirar el anillo, la
     * galería del paso 16 (la esquina suroeste) salía hacia el <b>este</b>, o sea <b>por donde va el caracol</b>: su
     * primera celda es la celda del paso 15 —ya cavada y con su pieza—, así que el minero se <b>comía su propio
     * escalón</b>. Medido con el arnés: el progreso oscilaba 16 → 15 → 16 → 15 y en el log salían en bucle
     * {@code caracol paso 15} / {@code galeria … celda 1 de 24}: el minero no bajaba ni un bloque más.
     */
    public static Direction direccionDeLaGaleria(int paso) {
        int[] d = MINA_ANILLO[Math.floorMod(paso, MINA_ANILLO.length)];
        // En las esquinas los dos ejes valen (los dos apuntan hacia fuera); se elige el de Z, que es lo que reparte
        // las galerías por los cuatro costados en el orden en que el anillo pasa por ellos.
        if (Math.abs(d[1]) >= Math.abs(d[0])) {
            return d[1] > 0 ? Direction.SOUTH : Direction.NORTH;
        }
        return d[0] > 0 ? Direction.EAST : Direction.WEST;
    }

    /**
     * La celda número {@code indice} (1..{@link #MINA_GALERIA_LARGO}) de la galería que sale del paso {@code paso}:
     * a la <b>misma Y</b> que la celda del caracol, adentrándose en cruz desde ella. El suelo de la galería es el
     * terreno de debajo (a {@code y - 1}), o sea que desde la losa del caracol se <b>baja medio bloque</b> a la
     * galería y se sube otro medio al volver.
     */
    public static BlockPos celdaDeLaGaleria(BlockPos center, int nivel, int paso, int indice) {
        BlockPos celda = celdaDelCaracol(center, nivel, paso);
        return celda.relative(direccionDeLaGaleria(paso), indice);
    }

    /** Cuántos pasos del caracol caben desde la capa del suelo hasta {@link #MINA_FONDO} (240 con cota 63). */
    public static int pasosHastaElFondo(int nivel) {
        return 2 * Math.max(0, (nivel - 1) - MINA_FONDO);
    }

    /**
     * <b>Cuántas celdas del caracol están hechas</b>, contadas desde la boca. Es la <b>faena pendiente</b> del minero,
     * y se <b>mira en el mundo</b> (la pieza de cada celda) en vez de llevarse en un contador guardado: así, si el
     * jugador rompe una celda del caracol, el minero <b>la vuelve a hacer</b> en vez de saltársela para siempre, y no
     * hay ningún número que se pueda quedar desincronizado con la mina de verdad.
     * <p>
     * Una celda que <b>abre galería</b> no cuenta como hecha hasta que la galería está <b>entera</b>: si no, el
     * progreso avanzaría con la pieza puesta y la galería se quedaría a medias para siempre. (La pieza <b>sí</b> se
     * pone antes de cavarla: es lo que deja el medio bloque por el que se <b>entra y se sale</b> del túnel, porque el
     * suelo de la galería va un bloque por debajo de la celda anterior del caracol.)
     * <p>
     * Se para en la primera celda que <b>no</b> tiene su pieza (la faena está ahí) o en la que lleva el
     * <b>tope</b> ({@link #laMinaLlegoAlTope}).
     */
    public static int progresoDeLaMina(ServerLevel level, BlockPos center, int nivel) {
        int maximo = pasosHastaElFondo(nivel);
        int paso = 0;
        while (paso < maximo && level.getBlockState(celdaDelCaracol(center, nivel, paso)).is(piezaDelCaracol(paso)
                .getBlock())) {
            if (abreGaleria(paso) && progresoDeLaGaleria(level, center, nivel, paso) < MINA_GALERIA_LARGO) {
                break; // la pieza está, pero su galería no: la faena es la galería
            }
            paso++;
        }
        return paso;
    }

    /**
     * <b>Cuántas celdas de la galería</b> de ese paso están ya abiertas (aire). Igual que el caracol, se <b>mira en
     * el mundo</b> en vez de llevarse en un contador: si al minero le pilla la noche (o un asedio) a media galería,
     * al volver <b>sigue por donde iba</b> en vez de dejar túneles a medias por todo el subsuelo.
     */
    public static int progresoDeLaGaleria(ServerLevel level, BlockPos center, int nivel, int paso) {
        int hechas = 0;
        for (int i = 1; i <= MINA_GALERIA_LARGO; i++) {
            // ABIERTA, y punto: una celda SELLADA (adoquín) NO cuenta como hecha **a propósito**. El sello es un
            // tapón, y en la galería la celda sellada es justo la que se anda, así que un túnel "sellado y dado por
            // hecho" queda **intransitable** (medido el 26-sep-2026: con los sellos contando como hechos el minero
            // se quedó con `hechas=3/24` y la faena en la celda 4, sin ruta hasta ella porque las celdas 1 y 2
            // estaban tapadas, oscilando "Bajando a la mina"/"Volviendo a la caseta"). Lo que hace el minero es
            // **volver a picar el sello**: si era una BOLSA aislada, al picarlo el agua ya no vuelve y el túnel
            // SIGUE (la celda queda de aire y el contador sube); si era un MAR, el agua vuelve, se vuelve a sellar y
            // `sellosSeguidos` sube, que es lo que acaba cerrando la mina con su piedra labrada (`SELLOS_MAXIMOS`).
            // Es exactamente lo que pidió el jugador: *"que selle las bolsas de agua o lava; si es un mar, que pare"*.
            if (level.getBlockState(celdaDeLaGaleria(center, nivel, paso, i)).isAir()) {
                hechas++;
            } else {
                break;
            }
        }
        return hechas;
    }

    /**
     * ¿La mina ya está <b>cerrada</b> en esa celda? El minero deja <b>piedra labrada</b> ({@code STONE_BRICKS}, que
     * no es nada de la mina ni terreno natural) en la celda donde se para: o llegó al <b>fondo</b>
     * ({@link #MINA_FONDO}) o se topó con un <b>mar de agua o de lava</b> que no puede sellar. Es su marca de "hasta
     * aquí", y también la lee el latido para saber que esa aldea ya tiene la mina hecha.
     */
    public static boolean laMinaLlegoAlTope(ServerLevel level, BlockPos center, int nivel, int paso) {
        return paso >= pasosHastaElFondo(nivel)
                || level.getBlockState(celdaDelCaracol(center, nivel, paso)).is(Blocks.STONE_BRICKS);
    }

    /**
     * ¿El minero puede <b>picar</b> esa celda? Sí si es <b>aire</b>, <b>terreno natural</b> (tierra, piedra,
     * deepslate, arena, grava, un mineral...) o una <b>pieza de la propia mina</b> (adoquín, losa, el tronco de un
     * marco, una antorcha). <b>No</b> si es algo que ha puesto el pueblo o el jugador: piedra labrada, tablones,
     * vallas, un cofre...
     * <p>
     * Es la misma lección de I24/I27 (el nivelado no se come lo construido) aplicada al pico: el caracol pasa a un
     * bloque de la <b>pared este de su propia caseta</b> (el anillo va a 4 del eje y la caseta llega a 3), así que sin
     * esta guarda el minero se abriría un boquete en la caseta para poner los postes de un marco — y el obrero se
     * pasaría la vida reponiéndolo.
     */
    public static boolean elMineroPuedePicar(BlockState state) {
        return state.isAir() || esTerrenoNatural(state) || state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.COBBLESTONE_SLAB) || state.is(Blocks.COBBLESTONE_STAIRS)
                || state.is(Blocks.OAK_LOG) || state.is(Blocks.OAK_PLANKS)
                || state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH);
    }

    // --- los puntos de la caseta del minero (lo que el goal necesita para trabajar) -----------------

    /**
     * El eje de la caseta y de la boca, a la cota del pueblo (la <b>capa que se pisa</b>, I1/I95).
     * <p>
     * <b>SIEMPRE EL POZO 0</b> (28-sep-2026, al añadir el segundo pozo): la caseta —con su taller, su horno y su
     * puesto de trabajo— es <b>una sola</b> y está junto al primer pozo. Con el eje <b>activo</b>, en cuanto el minero
     * se pasaba al segundo pozo la caseta se calculaba <b>en el sitio del segundo</b> (y `asegurarLaMinaDelPueblo`
     * llegaba a comprobar su testigo allí, con lo que habría construido una segunda caseta).
     */
    private static BlockPos ejeDeLaCaseta(ServerLevel level, BlockPos center) {
        int nivel = cotaDeLaPlaza(level, center);
        BlockPos eje = centroDeLaMina(center, 0);
        return new BlockPos(eje.getX(), nivel, eje.getZ());
    }

    /** La casilla <b>libre</b> de la caseta donde se para el minero (el centro, debajo del farol). */
    public static BlockPos puntoDeApoyoDeLaCaseta(ServerLevel level, BlockPos center) {
        return ejeDeLaCaseta(level, center);
    }

    /**
     * El <b>puesto de trabajo</b> del minero: el <b>cortapiedras</b> de su caseta (es el puesto del oficio
     * {@code MASON}, y sin él el juego le borra el oficio, I23/I31), o {@code null} si todavía no hay caseta.
     */
    @Nullable
    public static BlockPos puestoDelMinero(ServerLevel level, BlockPos center) {
        BlockPos c = ejeDeLaCaseta(level, center);
        BlockPos puesto = new BlockPos(c.getX() - 2, c.getY(), c.getZ() - 2);
        return level.getBlockState(puesto).is(Blocks.STONECUTTER) ? puesto : null;
    }

    /** La <b>balsa de filtrado</b> (agua a ras del suelo de la caseta) o {@code null} si no está. */
    @Nullable
    public static BlockPos balsaDelMinero(ServerLevel level, BlockPos center) {
        BlockPos c = ejeDeLaCaseta(level, center);
        BlockPos balsa = new BlockPos(c.getX() + 2, c.getY() - 1, c.getZ() - 2);
        return level.getBlockState(balsa).is(Blocks.WATER) ? balsa : null;
    }

    /** El <b>horno</b> del minero (donde funde los minerales) o {@code null} si no está. */
    @Nullable
    public static BlockPos hornoDelMinero(ServerLevel level, BlockPos center) {
        BlockPos c = ejeDeLaCaseta(level, center);
        BlockPos horno = new BlockPos(c.getX() - 2, c.getY(), c.getZ() + 2);
        return level.getBlockState(horno).is(Blocks.FURNACE) ? horno : null;
    }

    /**
     * <b>Deshace la mina VIEJA</b> (migración 71): la caseta que se construyó en el solar de antes
     * ({@link #MINA_OFFSET_VIEJO}) y el pozo que el minero hubiera cavado desde su boca, devolviendo el terreno del
     * pueblo (césped en la capa que se pisa, aire por encima y piedra en el pozo).
     * <p>
     * Hace falta porque el jugador pidió <b>mover</b> la mina (*"mueve la cabaña del minero porque está muy cerca del
     * centro"*): sin esto quedarían las dos casetas (la vieja ya está <b>en el plano</b>, así que el obrero la
     * repondría) y el pozo viejo sería un agujero en el suelo del pueblo que el tapagujeros iría rellenando a
     * medias. Es <b>conservador</b>: solo toca las celdas del solar viejo y las que llevan una pieza de la mina.
     */
    public static void deshacerLaMinaVieja(ServerLevel level, BlockPos center) {
        int nivel = cotaDeLaPlaza(level, center);
        BlockPos viejo = center.offset(MINA_OFFSET_VIEJO);
        // 1) EL POZO VIEJO: de la boca hacia abajo, mientras las celdas del caracol lleven su pieza.
        int pasos = 0;
        for (int paso = 0; paso < pasosHastaElFondo(nivel); paso++) {
            BlockPos pieza = celdaDelCaracolDesdeElEje(viejo, nivel, paso);
            if (!esPiezaDeLaMina(level.getBlockState(pieza))) {
                break; // aquí ya no hay mina
            }
            for (int dy = -1; dy <= 3; dy++) {
                BlockPos p = pieza.above(dy);
                BlockState actual = level.getBlockState(p);
                if (!actual.isAir() && !esPiezaDeLaMina(actual) && !actual.is(Blocks.STONE_BRICKS)) {
                    continue; // algo que no es de la mina (terreno o del pueblo): no se toca
                }
                BlockState nuevo = p.getY() >= nivel ? Blocks.AIR.defaultBlockState()
                        : (p.getY() == nivel - 1 ? Blocks.GRASS_BLOCK.defaultBlockState()
                                : Blocks.STONE.defaultBlockState());
                level.setBlock(p, nuevo, Block.UPDATE_ALL);
            }
            // Y el relleno que el minero hubiera puesto bajo la pieza (adoquín) vuelve a ser piedra.
            if (level.getBlockState(pieza.below()).is(Blocks.COBBLESTONE)) {
                level.setBlock(pieza.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            }
            pasos++;
        }
        // 2) LA CASETA VIEJA (y su marco de la boca): el suelo vuelve a ser césped y lo de encima, aire. El solar es
        //    el de 11x11 alrededor del eje (radio 5), que se midió libre antes de construirla.
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                for (int dy = 0; dy <= 4; dy++) {
                    BlockPos p = new BlockPos(viejo.getX() + dx, nivel - 1 + dy, viejo.getZ() + dz);
                    BlockState actual = level.getBlockState(p);
                    if (actual.isAir()) {
                        continue;
                    }
                    BlockState nuevo = p.getY() == nivel - 1 ? Blocks.GRASS_BLOCK.defaultBlockState()
                            : (p.getY() > nivel - 1 ? Blocks.AIR.defaultBlockState()
                                    : Blocks.STONE.defaultBlockState());
                    level.setBlock(p, nuevo, Block.UPDATE_ALL);
                }
            }
        }
        DevilRpg.LOGGER.info("[Village] Aldea en {}: retirada la mina vieja de {} (caseta y {} celda(s) de pozo):"
                + " el solar vuelve al pueblo", center, viejo, pasos);
    }

    /** ¿Ese bloque es una <b>pieza de la mina</b> (lo que pone el minero en su caracol)? */
    private static boolean esPiezaDeLaMina(BlockState estado) {
        return estado.is(Blocks.COBBLESTONE) || estado.is(Blocks.COBBLESTONE_SLAB)
                || estado.is(Blocks.COBBLESTONE_STAIRS) || estado.is(Blocks.TORCH)
                || estado.is(Blocks.WALL_TORCH) || estado.is(Blocks.STONE_BRICKS);
    }

    /** El índice del anillo del caracol de una columna (o {@code -1} si esa columna no es del anillo). */
    private static int indiceDelAnillo(int dx, int dz) {
        for (int i = 0; i < MINA_ANILLO.length; i++) {
            if (MINA_ANILLO[i][0] == dx && MINA_ANILLO[i][1] == dz) {
                return i;
            }
        }
        return -1;
    }

    /**
     * <b>¿Esa celda es una de las que se andan en el CARACOL?</b> (los pies o la cabeza de un paso del anillo). Es la
     * parte de {@link #esCeldaDePasoDeLaMina} que <b>NO</b> se puede tapiar nunca: la <b>pieza</b> de un paso
     * <b>impar</b> es adoquín, así que un adoquín puesto por el <b>sello del agua</b>
     * ({@code VillagerMinerGoal.aislarDelAgua}) se leería como su pieza y el paso se daría por hecho <b>sin suelo</b>
     * —el que baja detrás se cae al hueco—.
     * <p>
     * <b>Las GALERÍAS, en cambio, SÍ se pueden sellar</b>, y hay que hacerlo (medido el 27-sep-2026): su contador
     * cuenta <b>aire</b> ({@link #progresoDeLaGaleria}), así que un adoquín de sello ahí no engaña a nadie, y la celda
     * de <b>DELANTE</b> de la galería que se está cavando suele ser <b>acuífero todavía</b>: sin sellarla, el agua
     * vuelve al túnel en cuanto el minero sube al taller (medido: la galería del paso 32 pasó de `hechas=7/24` a
     * `0/24` con las siete celdas de agua, y el censo de agua lo dijo —las celdas mojadas eran **las del propio túnel**
     * y la de delante, con la pared oeste ya sellada—).
     */
    public static boolean esCeldaDePasoDelCaracol(BlockPos center, int nivel, BlockPos pos) {
        BlockPos eje = centroDeLaMina(center);
        int dx = pos.getX() - eje.getX();
        int dz = pos.getZ() - eje.getZ();
        if (Math.max(Math.abs(dx), Math.abs(dz)) != MINA_RADIO) {
            return false;
        }
        int indice = indiceDelAnillo(dx, dz);
        if (indice < 0) {
            return false;
        }
        for (int p = Math.max(indice, 0); p < pasosHastaElFondo(nivel); p += MINA_ANILLO.length) {
            int y = yDelCaracol(nivel, p);
            if (pos.getY() == y + 1 || pos.getY() == y + 2) {
                return true;
            }
        }
        return false;
    }

    /**
     * <b>¿Esa celda es una de las que se ANDAN en la mina?</b> (la celda de paso del caracol —la de los pies o la de
     * la cabeza, encima de su pieza— o una celda de una galería). Es la pregunta que necesita el <b>marco de madera</b>
     * del caracol para no taparse a sí mismo el túnel: sus postes van en las <b>paredes</b> (las dos celdas del radio
     * que hay a los lados de la celda), pero en las <b>esquinas del anillo</b> una de esas paredes es… <b>otra celda
     * del caracol</b>, así que el poste caía en el paso del escalón de al lado y lo <b>taponaba</b>. Medido con
     * {@code tools/arnes/columna_mina.py} y con el arnés (26-sep-2026): el marco del paso 16 (la esquina suroeste)
     * dejaba sus dos troncos en {@code 500,55,621} y {@code 500,56,621}, que son los pies y la cabeza del paso 15 —
     * o sea que la mina se quedaba <b>sin salida</b> por su propio soporte y el minero no bajaba.
     */
    public static boolean esCeldaDePasoDeLaMina(BlockPos center, int nivel, BlockPos pos) {
        BlockPos eje = centroDeLaMina(center);
        int dx = pos.getX() - eje.getX();
        int dz = pos.getZ() - eje.getZ();
        // 1) El CARACOL: la columna del anillo, y dentro de ella las dos celdas que van encima de su pieza.
        if (esCeldaDePasoDelCaracol(center, nivel, pos)) {
            return true;
        }
        // 2) Las GALERIAS: la cruz que sale de cada paso que abre galería, a la Y de su celda del caracol (la galería
        //    se anda por su propia celda y por las DOS de encima: desde el 27-sep-2026 el hueco de paso de la
        //    galería son TRES celdas, las mismas que cava `VillagerMinerGoal.picarLaCeldaDeLaGaleria`, porque con
        //    dos celdas el aldeano que viene de la losa del caracol no puede ni entrar ni salir).
        for (int p = MINA_GALERIA_CADA * 2; p < pasosHastaElFondo(nivel); p += MINA_GALERIA_CADA * 2) {
            BlockPos c = celdaDelCaracolDesdeElEje(eje, nivel, p);
            if (pos.getY() != c.getY() && pos.getY() != c.getY() + 1 && pos.getY() != c.getY() + 2) {
                continue;
            }
            Direction d = direccionDeLaGaleria(p);
            int avance = (pos.getX() - c.getX()) * d.getStepX() + (pos.getZ() - c.getZ()) * d.getStepZ();
            if (avance >= 1 && avance <= MINA_GALERIA_LARGO && pos.getX() == c.getX() + d.getStepX() * avance
                    && pos.getZ() == c.getZ() + d.getStepZ() * avance) {
                return true;
            }
        }
        return false;
    }

    /**
     * ¿Ese bloque es <b>terreno del pueblo</b> (lo que el nivelado y el tapagujeros ponen al reparar el suelo)?
     * <p>
     * <b>OJO CON EL ADOQUÍN</b>: NO está en la lista a propósito (26-sep-2026). El nivelado y {@code sellarSuelo}
     * rellenan con <b>césped y tierra</b> (y piedra en el pozo), nunca con adoquín; y desde que el minero
     * <b>sella el agua con adoquín</b> (una celda por cada bolsa o mar que se topa en la galería), meterlo aquí
     * hacía que este reparador <b>volviera a abrir los sellos del minero</b> —agua otra vez dentro del túnel— en la
     * pasada siguiente del latido.
     */
    private static boolean esTerrenoDelPueblo(BlockState estado) {
        return estado.is(Blocks.GRASS_BLOCK) || estado.is(Blocks.DIRT) || estado.is(Blocks.COARSE_DIRT)
                || estado.is(Blocks.ROOTED_DIRT) || estado.is(Blocks.PODZOL) || estado.is(Blocks.MYCELIUM)
                || estado.is(Blocks.STONE) || estado.is(Blocks.GRAVEL)
                || estado.is(Blocks.ANDESITE) || estado.is(Blocks.GRANITE) || estado.is(Blocks.DIORITE)
                || estado.is(Blocks.TUFF) || estado.is(Blocks.SAND) || estado.is(Blocks.RED_SAND)
                || estado.is(Blocks.SANDSTONE) || estado.is(Blocks.CLAY) || estado.is(Blocks.SNOW_BLOCK)
                || estado.is(Blocks.DIRT_PATH) || estado.is(Blocks.COBBLED_DEEPSLATE);
    }

    /**
     * ¿Esa celda es donde va un <b>poste del marco de madera</b> del caracol (uno de los dos lados de la celda de un
     * paso que lleva marco, a la altura del paso)? Es lo que usa el reparador del pozo para saber que un tronco
     * dentro de un paso es SUYO (un marco que se tapó a sí mismo) y no algo del pueblo que no se toca.
     */
    public static boolean esPosteDelMarcoDelCaracol(BlockPos center, int nivel, BlockPos pos) {
        BlockPos eje = centroDeLaMina(center);
        for (int paso = MINA_SOPORTE_CADA; paso < pasosHastaElFondo(nivel); paso += MINA_SOPORTE_CADA) {
            BlockPos celda = celdaDelCaracolDesdeElEje(eje, nivel, paso);
            for (BlockPos lado : ladosDeLaCelda(celda, paso)) {
                for (int dy = 1; dy <= 2; dy++) {
                    if (lado.above(dy).equals(pos)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * <b>ABRE EL POZO DE LA MINA SI EL PUEBLO LO HA VUELTO A TAPAR</b> (26-sep-2026).
     * <p>
     * Lo pidió el jugador —*"el minero no está bajando y está sellada la entrada"*— y la medida está en
     * {@code tools/arnes/columna_mina.py}: en su partida el caracol tenía sus <b>17 piezas puestas</b> (pasos 0 a 16)
     * y, sin embargo, <b>no había ruta de pie</b> desde el suelo hasta la casilla de pie del paso 16 (ni una: el
     * recorrido en anchura no pasaba de la cota del pueblo). Los cortes, medidos celda a celda:
     * <ul>
     *   <li>los pasos <b>1 y 2</b> tenían <b>césped</b> en su celda de paso y los <b>3 y 4</b> en la de la cabeza: es
     *       el <b>nivelado de la aldea</b> ({@link #nivelar}), que rellena con césped hasta {@code nivel - 1} —y esa
     *       capa es justo la que el caracol cruza al salir a la superficie—, más {@link #sellarSuelo}, que tapa
     *       cualquier columna del recinto cuya capa de suelo esté hueca. Los dos protegían la mina <b>por debajo</b>
     *       ({@link #esCeldaDeLaMina} excluye la superficie a propósito), así que el pozo se sellaba por arriba;</li>
     *   <li>el paso <b>15</b> tenía dos <b>troncos</b> en su paso: el marco de madera del paso 16, que cae en la
     *       esquina del anillo (ver {@link #esCeldaDePasoDeLaMina}).</li>
     * </ul>
     * Con el pozo cortado, el minero —que va a una casilla <b>de dentro</b>— no tiene ruta a su faena: se queda
     * arriba, en la boca, alternando "Bajando a la mina" y "Volviendo a la caseta" y <b>sin cavar una celda</b>
     * (medido con el arnés: {@code pasos=16} congelado 1.160 ticks, y sólo subió al 17 cuando el arnés cavó a mano la
     * galería).
     * <p>
     * Esto es el <b>reparador</b> que le devuelve el paso a las aldeas que ya están así (la del jugador): recorre
     * <b>solo lo que el minero ya ha hecho</b> (los pasos con su pieza puesta, y de la galería solo las celdas que ya
     * estaban abiertas), y de sus <b>celdas de paso</b> quita:
     * <ul>
     *   <li>el <b>terreno del pueblo</b> que las volvió a tapar (césped, tierra, piedra, grava...);</li>
     *   <li>los <b>postes del marco</b> que cayeron dentro de un paso ({@code oak_log}/{@code oak_planks}), que es el
     *       fallo de la esquina.</li>
     * </ul>
     * NO excava nada nuevo: una celda sin su pieza no se toca. Es <b>idempotente</b> (si no hay nada que quitar, no
     * escribe ni una celda) y no toca nada que no esté en el paso de la mina, así que la caseta y el pueblo quedan
     * como están.
     *
     * @return cuántas celdas ha vuelto a abrir (el poste cuenta como una)
     */
    public static int despejarElPozoDeLaMina(ServerLevel level, BlockPos center) {
        int nivel = cotaDeLaPlaza(level, center);
        if (nivel <= level.getMinBuildHeight() + 1) {
            return 0;
        }
        int abiertas = 0;
        int postes = 0;
        for (int paso = 0; paso < pasosHastaElFondo(nivel); paso++) {
            BlockPos celda = celdaDelCaracol(center, nivel, paso);
            if (!esPiezaDeLaMina(level.getBlockState(celda))) {
                break; // aquí ya no hay mina: lo que venga no es del minero
            }
            // LAS TRES CELDAS DEL HUECO DE PASO, que son LAS MISMAS que cava el minero (`picarLaCeldaDelCaracol`,
            // dy 1..3): no basta con las dos de los pies y la cabeza. Medido (26-sep-2026, con `build/slice_mina.py`
            // sobre su partida): el nivelado había vuelto a poner césped en SIETE celdas de la capa del suelo sobre el
            // pozo (`507,62,614` … `507,62,620`), y de ésas las de los pasos 1 y 2 caen en los pies/cabeza (se veían
            // en el aviso del arnés) pero las de los pasos 3 a 7 caen en la TERCERA celda del hueco, que también hay
            // que despejar: el planificador del juego mira la altura del aldeano (1,95: dos celdas) y con el techo
            // puesto por el césped el túnel no se anda.
            for (int dy = 1; dy <= 3; dy++) {
                BlockPos p = celda.above(dy);
                BlockState actual = level.getBlockState(p);
                if (esTerrenoDelPueblo(actual)) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    abiertas++;
                } else if ((actual.is(Blocks.OAK_LOG) || actual.is(Blocks.OAK_PLANKS))
                        && esPosteDelMarcoDelCaracol(center, nivel, p)) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    postes++;
                }
            }
            if (!abreGaleria(paso)) {
                continue;
            }
            int hechas = progresoDeLaGaleria(level, center, nivel, paso);
            for (int i = 1; i <= hechas; i++) {
                BlockPos g = celdaDeLaGaleria(center, nivel, paso, i);
                // LAS TRES CELDAS DEL HUECO DE PASO DE LA GALERIA, que son las mismas que cava el minero
                // (`picarLaCeldaDeLaGaleria`, 27-sep-2026): las tres hacen falta para poder ENTRAR y SALIR desde la
                // losa del caracol (el aldeano que baja va de pie encima de la losa y su caja necesita el tercer
                // hueco). Reparar solo dos dejaria la galeria sin entrada y el tunel no se andaria.
                for (int dy = 0; dy <= 2; dy++) {
                    BlockPos p = g.above(dy);
                    BlockState actual = level.getBlockState(p);
                    if (esTerrenoDelPueblo(actual)) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        abiertas++;
                    }
                }
            }
        }
        if (abiertas > 0 || postes > 0) {
            DevilRpg.LOGGER.info("[Village] Aldea en {}: abierto el pozo de la mina ({} celda(s) que el pueblo"
                    + " habia vuelto a tapar y {} poste(s) del marco dentro del paso)", center, abiertas, postes);
        }
        return abiertas + postes;
    }

    /**
     * <b>¿Esa celda es de la MINA?</b> (o sea: del <b>minero</b>, no del pueblo). Es la <b>zona</b> de la mina (el
     * cilindro de {@link #MINA_EXCLUSION_RADIO} por debajo de la capa del suelo), y la usa el <b>nivelado</b> (que
     * rellenaría el pozo con tierra si no) para <b>no</b> reponer el terreno que el minero ha cavado. Para las
     * reparaciones de la <b>capa del suelo</b> (el tapagujeros de I90 y el plano) se usa {@link #estaSobreElPozo},
     * que es mucho más estrecho: alrededor del pozo está la aldea y su suelo se repara igual.
     * <p>
     * La regla es un <b>cilindro</b>: la zona de la mina ({@link #MINA_EXCLUSION_RADIO}) y <b>por debajo de la capa
     * del suelo</b>. La superficie <b>no</b> se excluye: la caseta es del pueblo y el obrero la mantiene.
     */
    public static boolean esCeldaDeLaMina(BlockPos center, int nivel, BlockPos pos) {
        if (pos.getY() >= nivel - 1) {
            return false; // la superficie (y la caseta) es del pueblo
        }
        // SE MIRAN **TODOS LOS POZOS** (28-sep-2026): con el segundo pozo, mirar solo el eje activo dejaba la zona
        // del otro sin excluir, y el nivelado habria rellenado su caracol con tierra (y el tapagujeros, su boca).
        for (BlockPos eje : MINA_OFFSETS) {
            int dx = pos.getX() - center.getX() - eje.getX();
            int dz = pos.getZ() - center.getZ() - eje.getZ();
            if (dx * dx + dz * dz <= MINA_EXCLUSION_RADIO * MINA_EXCLUSION_RADIO) {
                return true;
            }
        }
        return false;
    }

    /**
     * ¿Esa celda cae <b>sobre el pozo</b> de la mina (la boca y el caracol), mirando solo el plano horizontal? Es la
     * pregunta que hacen las reparaciones de la <b>capa del suelo</b> (el tapagujeros I90 y el plano), que solo
     * pueden ver lo que hay cerca de la superficie: alrededor del pozo está la aldea, y su suelo se repara igual.
     * <p>
     * Y mira <b>todos</b> los pozos, por lo mismo que {@link #esCeldaDeLaMina}: si no, el tapagujeros taparía la boca
     * del segundo pozo.
     */
    public static boolean estaSobreElPozo(BlockPos center, BlockPos pos) {
        for (BlockPos eje : MINA_OFFSETS) {
            int dx = pos.getX() - center.getX() - eje.getX();
            int dz = pos.getZ() - center.getZ() - eje.getZ();
            if (dx * dx + dz * dz <= MINA_POZO_RADIO * MINA_POZO_RADIO) {
                return true;
            }
        }
        return false;
    }

    /** La <b>boca de la mina</b> (el primer escalón del caracol), al lado de la caseta. */
    public static BlockPos bocaDeLaMina(BlockPos center, int nivel) {
        return celdaDelCaracol(center, nivel, 0);
    }

    /** Lado de la caseta del minero (7×7: cabe en el solar de 11×11 con holgura). */
    private static final int CASETA_MINERO_LADO = 7;

    /**
     * Asegura la <b>mina del pueblo</b> (etapa I): la <b>caseta del minero</b> (7×7 de piedra, con su
     * <b>cortapiedras</b> —que es su puesto de trabajo, el del albañil—, un <b>horno</b> para fundir, su <b>balsa de
     * agua</b> para filtrar el cobblestone, su <b>cama</b> y su farol) y la <b>boca del caracol</b>, con su marco de
     * entrada. El resto de la mina (los escalones, los soportes y las galerías) lo cava el <b>minero</b>: aquí solo
     * se deja la boca y el primer escalón.
     * <p>
     * Es <b>idempotente</b> (su testigo es el suelo de piedra de la caseta) y la llama el latido, así que llega a las
     * aldeas ya construidas sin migración —igual que la herrería, la barraca o el taller del leñador—. La <b>mina</b>
     * que cava el minero queda <b>fuera del plano</b> (I102): el obrero no la toca. El <b>solar</b> es
     * {@link #MINA_OFFSET} (el descampado del noreste, junto al muro) y la mina <b>vieja</b> (la que se construyó
     * pegada al centro) la retira {@link #deshacerLaMinaVieja} en la migración 71.
     */
    public static void asegurarLaMinaDelPueblo(ServerLevel level, BlockPos center) {
        int nivel = cotaDeLaPlaza(level, center);
        if (nivel <= level.getMinBuildHeight() + 1) {
            return;
        }
        // LA CASETA ES LA DEL POZO 0 (28-sep-2026): es única y vive junto al primer pozo. Con el eje ACTIVO, en cuanto
        // el minero se pasaba al segundo pozo esta función miraba su testigo en el sitio del segundo y habría
        // construido una caseta nueva allí (con su taller y su horno) en mitad del descampado.
        BlockPos c = centroDeLaMina(center, 0);
        BlockPos testigo = new BlockPos(c.getX(), nivel - 1, c.getZ() + CASETA_MINERO_LADO / 2);
        if (level.getBlockState(testigo).is(Blocks.STONE_BRICKS)) {
            return; // la caseta ya está
        }
        casetaDelMinero(level, c, nivel);
        // Y LA HERRAMIENTA CON LA QUE ARRANCA (una vez, y solo si el almacén no tiene ningún pico): a una aldea que
        // ya estaba en marcha no le llega la remesa inicial del almacén, así que sin esto el minero se quedaba
        // plantado pidiendo pico y el herrero sin hierro con el que forjarlo.
        VillageStorage.asegurarElPicoDelMinero(level, center);
        DevilRpg.LOGGER.info("[Village] Aldea en {}: caseta del minero y boca de la mina en {} (caracol de radio {},"
                + " fondo y={})", center, c, MINA_RADIO, MINA_FONDO);
    }

    /** Construye la caseta (7×7) y la boca del caracol. */
    private static void casetaDelMinero(ServerLevel level, BlockPos c, int nivel) {
        int r = CASETA_MINERO_LADO / 2; // 3
        // 1) EL SUELO (a `nivel - 1`, como todas las casas) con la BALSA DE FILTRADO: una celda de agua a ras del
        //    suelo (I95: un hoyo de dos bloques es una TRAMPA —el aldeano que cae dentro no puede subir 1,875—,
        //    mientras que el agua a ras de la capa que se pisa se queda a 0,125 del suelo y se sale de ella andando).
        //    El agua es lo que convierte el cobblestone en pedernal (ver `VillagerMinerGoal`).
        BlockPos balsa = new BlockPos(c.getX() + 2, nivel - 1, c.getZ() - 2);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                BlockPos suelo = new BlockPos(c.getX() + dx, nivel - 1, c.getZ() + dz);
                colocar(level, suelo,
                        (suelo.equals(balsa) ? Blocks.WATER : Blocks.STONE_BRICKS).defaultBlockState(), 3);
                for (int dy = 0; dy <= 2; dy++) {
                    if (suelo.equals(balsa) && dy == 0) {
                        continue; // el agua ocupa la capa del suelo: no se pone aire encima de ella
                    }
                    colocar(level, new BlockPos(c.getX() + dx, nivel + dy, c.getZ() + dz),
                            Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        // 2) LAS PAREDES de piedra (3 de alto) con el HUECO DE LA PUERTA (1x2) en la pared sur.
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (Math.abs(dx) != r && Math.abs(dz) != r) {
                    continue; // interior
                }
                boolean puerta = dx == 0 && dz == r;
                for (int dy = 0; dy <= 2; dy++) {
                    if (puerta && dy <= 1) {
                        continue; // el hueco de la puerta (1x2)
                    }
                    BlockPos p = new BlockPos(c.getX() + dx, nivel + dy, c.getZ() + dz);
                    // Los postes de las esquinas, de tronco (como el resto del pueblo).
                    boolean esquina = Math.abs(dx) == r && Math.abs(dz) == r;
                    colocar(level, p, (esquina ? Blocks.OAK_LOG : Blocks.STONE_BRICKS).defaultBlockState(), 3);
                }
            }
        }
        // 3) EL TEJADO de tablones y el FAROL colgado del centro (I14: colgado, no posado).
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                colocar(level, new BlockPos(c.getX() + dx, nivel + 3, c.getZ() + dz),
                        Blocks.OAK_PLANKS.defaultBlockState(), 3);
            }
        }
        colocar(level, new BlockPos(c.getX(), nivel + 2, c.getZ()),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 3);
        // 4) SU PUESTO DE TRABAJO: el CORTAPIEDRAS (el puesto del albañil, que es el oficio del MINERO). Sin él, el
        //    juego le borra el oficio al aldeano (I23/I31).
        colocar(level, new BlockPos(c.getX() - 2, nivel, c.getZ() - 2), Blocks.STONECUTTER.defaultBlockState(), 3);
        // 5) El HORNO para fundir los minerales (no es puesto de nadie: I31).
        colocar(level, new BlockPos(c.getX() - 2, nivel, c.getZ() + 2),
                Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, Direction.NORTH), 3);
        // 6) Su CAMA (una cama son DOS POIs `HOME` y la necesita: duerme aquí, en su sitio de trabajo, como el
        //    ganadero en su cobertizo). VA DENTRO, pegada a la pared ESTE y sin tocar la puerta: con el pie en
        //    `(c+1, c+2)` y la cabecera al sur, la cabecera caía en la celda de la PARED sur —justo al lado de la
        //    puerta— y la cama asomaba por el hueco (lo vio el jugador: *"mete más la cama del minero porque quedó
        //    fuera y bloquea la puerta"*).
        bed(level, new BlockPos(c.getX() + 2, nivel, c.getZ() - 1), Direction.SOUTH);
        // 7) LA BOCA DEL CARACOL, al lado de la caseta (en la esquina noreste del anillo), con su marco de entrada.
        //    La boca es la celda 0 del caracol y la construye el pueblo (con `colocar`, o sea que ENTRA EN EL PLANO:
        //    el obrero la mantiene). Lo que cava el minero de ahí para abajo es suyo (I102).
        BlockPos boca = celdaDelCaracolDesdeElEje(c, nivel, 0);
        colocar(level, boca, piezaDelCaracol(0), 3);
        colocar(level, boca.above(), Blocks.AIR.defaultBlockState(), 3);
        colocar(level, boca.above(2), Blocks.AIR.defaultBlockState(), 3);
        colocar(level, boca.above(3), Blocks.AIR.defaultBlockState(), 3);
        // El marco de la entrada: dos postes de tronco en las paredes (a `nivel` y `nivel + 1`, que es el hueco de
        // paso) y una viga de tablones encima (a `nivel + 2`: deja las tres celdas que pide el juego para subir,
        // I26). Los lados son las celdas del radio (por dentro y por fuera del anillo).
        BlockPos[] lados = ladosDeLaCelda(boca, 0);
        for (BlockPos lado : lados) {
            for (int dy = 1; dy <= 2; dy++) {
                colocar(level, lado.above(dy), Blocks.OAK_LOG.defaultBlockState(), 3);
            }
        }
        int bx = Integer.signum(lados[0].getX() - boca.getX());
        int bz = Integer.signum(lados[0].getZ() - boca.getZ());
        for (int i = -1; i <= 1; i++) {
            colocar(level, boca.above(3).offset(bx * i, 0, bz * i), Blocks.OAK_PLANKS.defaultBlockState(), 3);
        }
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
        // SI LA ARBOLEDA YA TIENE ALGO (un plantón o un árbol), NO SE TOCA: de replantarla se encarga el LEÑADOR con
        // plantones de verdad sacados del almacén. Antes se rellenaba cada hueco vacío en CADA latido, así que al
        // leñador le reponían el plantón GRATIS en cuanto talaba un árbol del pueblo: su ciclo (semilla -> plantón ->
        // árbol -> troncos, ver `VillageStorage`/`VillagerLumberjackGoal`) quedaba de adorno y la madera salía de la
        // nada. Esto es solo el ARRANQUE de una aldea que nace sin bosque (una islita, un desierto, una llanura
        // pelada); si algún día la arboleda se queda a cero (se murió el leñador y nadie replantó), vuelve a arrancar.
        for (BlockPos p : plantonesDeLaArboleda(center, nivel)) {
            if (!level.getBlockState(p).isAir()) {
                return;
            }
        }
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

    // --- EL TALLER DEL LEÑADOR (etapa H) ------------------------------------------------------------

    /**
     * Esquina noroeste del <b>suelo del taller del leñador</b>, relativa al centro: al <b>oeste de la arboleda</b>
     * (que empieza en {@code ARBOLEDA_X0} = -46), en hierba llana y a dos bloques de ella para no pisar los
     * plantones. Ver {@link #asegurarElTallerDelLenador}.
     */
    private static final int TALLER_LENADOR_X = -52;
    private static final int TALLER_LENADOR_Z = -26;
    /** Lado del cobertizo (5x5, como el del corral anexo): suelo de piedra, postes, tejado y SIN paredes. */
    private static final int TALLER_LENADOR_LADO = 5;

    /**
     * El <b>taller del leñador</b> (etapa H): un cobertizo <b>abierto</b> —suelo de piedra, cuatro postes, tejado de
     * tablones y sin paredes— junto a la <b>arboleda del pueblo</b>, con su <b>mesa de flechas</b> (el puesto de
     * trabajo del <b>flechero</b>, que es el oficio del <b>LEÑADOR</b>), su farol y una pila de troncos.
     * <p>
     * Antes el leñador <b>no era un oficio</b>: talaba el <b>recolector</b> (el holgazán) y el pueblo no gastaba un
     * puesto más. El jugador pidió separarlos —*"es necesario que haya un aldeano que se especialice únicamente en
     * cortar madera y plantar árboles, para dejar totalmente libre al recolector para que recoja y transporte"*—, y
     * un oficio del pueblo necesita <b>su estación</b> (regla: una profesión por estación): la del leñador es la
     * <b>mesa de flechas</b>, que además es el sitio del que salen las flechas de los arqueros de la milicia. Es
     * idempotente (su testigo es la propia mesa: si ya está, no escribe ni una celda) y todo entra en el
     * <b>plano</b> por {@code colocar} (I8), así que el obrero lo repone.
     */
    public static void asegurarElTallerDelLenador(ServerLevel level, BlockPos center) {
        int nivel = cotaDeLaPlaza(level, center);
        if (nivel <= level.getMinBuildHeight() + 1) {
            return;
        }
        if (tallerDelLenadorHecho(level, center, nivel)) {
            return;
        }
        int x0 = center.getX() + TALLER_LENADOR_X;
        int z0 = center.getZ() + TALLER_LENADOR_Z;
        int x1 = x0 + TALLER_LENADOR_LADO - 1;
        int z1 = z0 + TALLER_LENADOR_LADO - 1;
        // El SUELO (cota - 1: la capa que se pisa es la cota, I1), el hueco de dentro y el TEJADO.
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                colocar(level, new BlockPos(x, nivel - 1, z), Blocks.STONE_BRICKS.defaultBlockState(), 3);
                for (int dy = 0; dy <= 2; dy++) {
                    colocar(level, new BlockPos(x, nivel + dy, z), Blocks.AIR.defaultBlockState(), 3);
                }
                colocar(level, new BlockPos(x, nivel + 3, z), Blocks.OAK_PLANKS.defaultBlockState(), 3);
            }
        }
        // Los cuatro POSTES de las esquinas (suben enteros hasta el tejado).
        for (int[] esquina : new int[][]{{x0, z0}, {x0, z1}, {x1, z0}, {x1, z1}}) {
            for (int dy = 0; dy <= 2; dy++) {
                colocar(level, new BlockPos(esquina[0], nivel + dy, esquina[1]),
                        Blocks.OAK_LOG.defaultBlockState(), 3);
            }
        }
        // El FAROL cuelga del centro del tejado (I14: colgado, no posado en el aire).
        colocar(level, new BlockPos((x0 + x1) / 2, nivel + 2, (z0 + z1) / 2),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 3);
        // SU PUESTO DE TRABAJO: la mesa de flechas (lo que le da el oficio de flechero = LEÑADOR).
        colocar(level, new BlockPos(x0 + 1, nivel, z0 + 1), Blocks.FLETCHING_TABLE.defaultBlockState(), 3);
        // Y la madera a medio trabajar: una pila de troncos y un tocón, que es lo que hace un leñador.
        colocar(level, new BlockPos(x1 - 1, nivel, z0 + 1), Blocks.OAK_LOG.defaultBlockState(), 3);
        colocar(level, new BlockPos(x1 - 1, nivel + 1, z0 + 1), Blocks.OAK_LOG.defaultBlockState(), 3);
        colocar(level, new BlockPos(x1 - 1, nivel, z1 - 1), Blocks.STRIPPED_OAK_LOG.defaultBlockState(), 3);
        DevilRpg.LOGGER.info("[Village] Aldea en {}: taller del leñador levantado en {} (mesa de flechas en {}, {}, {})",
                center, new BlockPos(x0, nivel - 1, z0), x0 + 1, nivel, z0 + 1);
    }

    /**
     * ¿Está ya el taller del leñador? El <b>testigo</b> es su <b>mesa de flechas</b> (como el hogar en la barraca,
     * I15): si el jugador se la llevó, el pueblo la repone; si está, no se toca nada de dentro.
     */
    private static boolean tallerDelLenadorHecho(ServerLevel level, BlockPos center, int nivel) {
        int x0 = center.getX() + TALLER_LENADOR_X;
        int z0 = center.getZ() + TALLER_LENADOR_Z;
        return level.getBlockState(new BlockPos(x0 + 1, nivel, z0 + 1)).is(Blocks.FLETCHING_TABLE)
                && level.getBlockState(new BlockPos(x0, nivel - 1, z0)).is(Blocks.STONE_BRICKS);
    }

    /** El <b>punto de apoyo</b> del taller: la casilla libre del suelo delante de la mesa (nunca la mesa: es sólida). */
    public static BlockPos puntoDeApoyoDelTallerDelLenador(ServerLevel level, BlockPos center) {
        int nivel = cotaDeLaPlaza(level, center);
        return new BlockPos(center.getX() + TALLER_LENADOR_X + 2, nivel, center.getZ() + TALLER_LENADOR_Z + 1);
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
        if (!esTroncoDeArbol(level, p)) {
            return false;
        }
        // 1) hojas cerca, por encima: es lo que distingue un árbol de un poste. Se mira hasta 12 bloques arriba para
        // que también cuente la base de un árbol alto (una selva los tiene de 20, pero con 12 sobra para los del
        // pueblo y para no confundir un poste con las hojas de un árbol vecino).
        for (int dy = 1; dy <= 12; dy++) {
            for (BlockPos q : BlockPos.betweenClosed(p.offset(-2, dy, -2), p.offset(2, dy, 2))) {
                if (level.getBlockState(q).is(BlockTags.LEAVES)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Las <b>dos primeras</b> reglas de {@link #esArbolSuelto}, sin la de las hojas: el tronco está <b>de pie</b>
     * (eje Y: los tramos del muro son troncos <b>tumbados</b>, eje X o Z) y <b>no tiene nada construido pegado</b>
     * (ni tablones, ni piedra, ni vallas, ni cristales: los postes de las casas van pegados a sus paredes).
     * <p>
     * Se pregunta aparte porque <b>un tronco a medio talar puede haberse quedado sin copa</b>: el leñador tala el
     * tronco de abajo y desrama el árbol, así que lo que cuelga de la parte torcida ya no tiene hojas que lo
     * delaten y, con la regla de las hojas, dejaría de reconocerse como parte del árbol (es justo el fallo que el
     * jugador vio: <i>"deja logs flotando"</i>). Lo usa {@code VillagerLumberjackGoal.rematarElArbol} para seguir
     * picando <b>lo que cuelga del árbol que está talando</b> sin tocar ni el muro ni los postes del pueblo.
     */
    public static boolean esTroncoDeArbol(ServerLevel level, BlockPos p) {
        BlockState state = level.getBlockState(p);
        if (!state.is(BlockTags.LOGS)) {
            return false;
        }
        if (state.hasProperty(RotatedPillarBlock.AXIS) && state.getValue(RotatedPillarBlock.AXIS) != Direction.Axis.Y) {
            return false; // tronco tumbado: es un tramo del muro
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
     * <b>CIERRA LOS HUECOS DE LAS CASAS DEL JUEGO</b> comparándolas con la <b>plantilla</b> con la que se construyeron.
     * <p>
     * Lo reportó el jugador con captura: *"¿qué ves de extraño en esta casa? ¡si le falta completarse a la pared!
     * corrígelo y checa que el cofre no estorbe"*. Medido en su guardado: la casa de {@code 1427..1431, 1383..1393}
     * (la plantilla {@code plains_medium_house_2}, que pide <b>adoquín</b> en su pared oeste a la altura de la ventana
     * y del poste) tenía <b>aire</b> en DOS celdas —{@code 1427,120,1392} y {@code 1427,121,1392}—, un boquete de
     * 1x2 justo al lado de la puerta. Y el <b>plano</b> de la aldea tampoco tenía esas celdas (se capturó por
     * <b>escaneo</b> del mundo, y el escaneo descarta el aire), así que el <b>obrero no tenía nada que reponer</b>: el
     * agujero era invisible para el pueblo y se quedaba para siempre.
     * <p>
     * Cómo se cierra: la plantilla del juego guarda <b>solo los bloques que tiene</b> ({@code template.blocks()}), así
     * que la comprobación es directa —celda de la plantilla con bloque, mundo con <b>aire</b> ⇒ se repone el bloque de
     * la plantilla—. Dos guardas:
     * <ul>
     *   <li><b>La casa tiene que estar ahí</b>: si menos de la mitad de las celdas de la plantilla coinciden con el
     *       mundo, esa casa no es la de esa plantilla (o el plano no encaja) y <b>no se toca nada</b>. Así un fallo de
     *       cálculo no llena de bloques una casa ajena.</li>
     *   <li><b>Lo que YA hay no se toca</b> (ni un cofre, ni una cama, ni un puesto de trabajo, ni lo que puso el
     *       jugador): solo se rellena el <b>aire</b>. Es lo que pidió el jugador con lo del cofre: el arca de
     *       {@code 1428,120,1392} está pegada al hueco y se queda exactamente donde está (y el bloque del hueco se pone
     *       en la celda de al lado, no encima de ella).</li>
     * </ul>
     * Lo repuesto se devuelve para que el gestor lo apunte en el <b>plano</b> (I8): si no, el obrero no lo mantendría.
     *
     * @return las celdas repuestas (posición → bloque), vacío si no había nada que cerrar
     */
    public static Map<BlockPos, BlockState> cerrarHuecosDeLasCasas(ServerLevel level, BlockPos center) {
        Map<BlockPos, BlockState> repuestas = new LinkedHashMap<>();
        int nivel = cotaDeLaPlaza(level, center);
        if (nivel <= level.getMinBuildHeight() + 1) {
            return repuestas;
        }
        // Las MISMAS construcciones de plantilla y en el MISMO orden que `generate` (los sorteos son deterministas
        // por la posición de la aldea: `RandomSource.create(center.asLong())`), para que la plantilla que se compara
        // sea la que de verdad se colocó.
        RandomSource casas = RandomSource.create(center.asLong());
        List<BlockPos> bases = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        BlockPos[] solares = basesDeCasas(center);
        for (int i = 0; i < solares.length; i++) {
            bases.add(solares[i]);
            ids.add(i == solares.length - 1 ? casaGrandeAleatoria(casas) : casaAleatoria(casas));
        }
        bases.add(baseDeIglesia(center));
        ids.add(iglesiaAleatoria(casas));
        bases.add(baseDeHerreria(center));
        ids.add(HERRERIAS[0]);
        for (int i = 0; i < bases.size(); i++) {
            StructureTemplate template = level.getStructureManager()
                    .getOrCreate(ResourceLocation.withDefaultNamespace(ids.get(i)));
            if (template == null) {
                continue;
            }
            Vec3i tam = template.getSize();
            BlockPos origen = new BlockPos(bases.get(i).getX(), nivel - alturaDeLaPuerta(template), bases.get(i).getZ());
            // PRIMERA PASADA: ¿está la casa ahí? (si la plantilla no encaja con el mundo, no se toca nada)
            List<StructureTemplate.StructureBlockInfo> celdas = celdasDeLaPlantilla(level, ids.get(i), template);
            int encajan = 0;
            int esperadas = 0;
            for (StructureTemplate.StructureBlockInfo info : celdas) {
                if (esBloqueTecnico(info.state())) {
                    continue;
                }
                esperadas++;
                BlockPos p = origen.offset(info.pos());
                BlockState actual = level.getBlockState(p);
                if (!actual.isAir() && actual.getBlock() == info.state().getBlock()) {
                    encajan++;
                }
            }
            if (esperadas == 0 || encajan * 2 < esperadas) {
                DevilRpg.LOGGER.debug("[Village] La casa {} de {} no encaja con su plantilla ({}/{}): no se repara",
                        ids.get(i), bases.get(i).toShortString(), encajan, esperadas);
                continue;
            }
            // SEGUNDA PASADA: lo que la plantilla pide y el mundo tiene en AIRE.
            int cerradas = 0;
            StringBuilder donde = new StringBuilder();
            for (StructureTemplate.StructureBlockInfo info : celdas) {
                if (esBloqueTecnico(info.state())) {
                    continue;
                }
                BlockPos p = origen.offset(info.pos());
                if (!level.getBlockState(p).isAir()) {
                    continue; // ahí ya hay algo (cofre, cama, puesto, o lo que puso el jugador): NO se toca
                }
                // lint:ok I9 porque es una REPARACION idempotente celda a celda (tapa el hueco de una casa con lo que
                // pide SU PLANTILLA) y la llama el latido: no rehace ninguna construccion ni cambia el trazado, asi
                // que no necesita migracion (y las aldeas ya construidas se arreglan solas en la primera pasada).
                colocar(level, p, info.state(), Block.UPDATE_CLIENTS); // si se está grabando, entra en el plano (I8)
                repuestas.put(p.immutable(), info.state());
                cerradas++;
                if (cerradas <= 8) {
                    donde.append(' ').append(p.toShortString()).append('(')
                            .append(info.state().getBlock().toString()
                                    .replace("Block{minecraft:", "").replace("}", ""))
                            .append(')');
                }
            }
            if (cerradas > 0) {
                DevilRpg.LOGGER.info("[Village] Casa {} en {}: {} hueco(s) de la plantilla tapados:{}", ids.get(i),
                        bases.get(i).toShortString(), cerradas, donde);
            }
        }
        return repuestas;
    }

    /** ¿Ese bloque de una plantilla es <b>técnico</b> (no se repone nunca)? Los resuelve `placeVanillaHouse`. */
    private static boolean esBloqueTecnico(BlockState state) {
        return state.isAir() || state.is(Blocks.JIGSAW) || state.is(Blocks.STRUCTURE_VOID);
    }

    /**
     * Las <b>celdas</b> de una plantilla del juego (posición relativa → bloque). La plantilla guarda <b>solo los
     * bloques que tiene</b> (el aire no está en ella), así que esta lista es exactamente "lo que la construcción debe
     * tener" y sirve de verdad para comparar con el mundo.
     * <p>
     * Se lee del NBT de la plantilla ({@code save}) porque la API pública no expone la lista entera: {@code
     * filterBlocks} solo sabe filtrar por <b>un</b> tipo de bloque. Se cachea por id: las plantillas del juego no
     * cambian en una partida.
     */
    private static final Map<String, List<StructureTemplate.StructureBlockInfo>> CELDAS_DE_PLANTILLA = new HashMap<>();

    private static List<StructureTemplate.StructureBlockInfo> celdasDeLaPlantilla(ServerLevel level, String id,
                                                                                 StructureTemplate template) {
        List<StructureTemplate.StructureBlockInfo> cacheada = CELDAS_DE_PLANTILLA.get(id);
        if (cacheada != null) {
            return cacheada;
        }
        List<StructureTemplate.StructureBlockInfo> celdas = new ArrayList<>();
        CompoundTag nbt = template.save(new CompoundTag());
        ListTag paleta = nbt.getList("palette", Tag.TAG_COMPOUND);
        ListTag bloques = nbt.getList("blocks", Tag.TAG_COMPOUND);
        var bloquesDelJuego = level.holderLookup(Registries.BLOCK);
        for (int i = 0; i < bloques.size(); i++) {
            CompoundTag uno = bloques.getCompound(i);
            ListTag pos = uno.getList("pos", Tag.TAG_INT);
            if (pos.size() < 3) {
                continue;
            }
            int indice = uno.getInt("state");
            if (indice < 0 || indice >= paleta.size()) {
                continue;
            }
            BlockState estado = NbtUtils.readBlockState(bloquesDelJuego, paleta.getCompound(indice));
            if (estado.isAir()) {
                continue;
            }
            celdas.add(new StructureTemplate.StructureBlockInfo(
                    new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)), estado, null));
        }
        CELDAS_DE_PLANTILLA.put(id, celdas);
        return celdas;
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
     * cuatro postes, tejado y la <b>campana en el CENTRO</b>, posada en la plataforma. La campana es el
     * <b>POI de reunión</b> del pueblo y en el kiosco es a propósito: aquí se junta la aldea. Del tejado cuelga un
     * <b>farol</b>, que la ilumina de noche. Dentro <b>no hay ningún cofre</b>: la despensa se movió a la cocina de
     * la taberna en la migración 47 (ver {@code VillagePantry}).
     * <p>
     * La campana va en la <b>celda central</b> y <b>apoyada</b> ({@code attachment} = {@code floor}: el apoyo es la
     * propia plataforma, que va justo debajo). Lo pidió el jugador: <i>"sitúa la campana justo en el centro del
     * kiosco"</i> —antes estaba descentrada, una celda al oeste y al sur— y es además como la coloca el propio
     * juego. <b>Esa celda es de la campana</b>: no se pone nada más ahí (el farol va <b>colgado</b> del tejado, en la
     * misma vertical pero cuatro bloques más arriba). Y el <b>tejado no lleva beacon</b>: el <b>sello místico</b> ya
     * no lo enciende (no hace nada sin pirámide y el sello vive en los datos de la aldea, migración 58); su celda es
     * la <b>piedra del centro del tejado</b>, que es de donde <b>cuelga</b> el farol (I14: si quedara aire, el farol
     * se caería).
     */
    private static void kiosco(ServerLevel level, BlockPos center, int nivel) {
        int r = KIOSCO_RADIO;
        int cx = center.getX();
        int cz = center.getZ();
        // Campanas VIEJAS dentro del kiosco: la campana va en el centro (abajo), así que cualquier otra que quedara
        // de un trazado anterior se retira ANTES de colocar la buena (el kiosco viejo la tenía una celda al oeste y
        // al sur). Si no, el kiosco se quedaría con dos campanas y con dos POI de reunión.
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx == 0 && dz == 0) {
                    continue; // la celda del centro es de la campana nueva
                }
                for (int dy = 0; dy <= KIOSCO_POSTE; dy++) {
                    BlockPos vieja = new BlockPos(cx + dx, nivel + dy, cz + dz);
                    if (level.getBlockState(vieja).is(Blocks.BELL)) {
                        colocar(level, vieja, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        // Plataforma 7x7 (radio 3) a la cota del pueblo: se anda un bloque por encima de la plaza.
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
        // LA CAMPANA, en la celda central y POSADA en la plataforma (`attachment` = floor: el apoyo va justo
        // debajo). Es el POI de reunión del pueblo y el pueblo se junta aquí a propósito.
        colocar(level, new BlockPos(cx, nivel + 1, cz),
                Blocks.BELL.defaultBlockState().setValue(BellBlock.FACING, Direction.SOUTH)
                        .setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR), 3);
        // Un farol colgado del tejado, en el centro: el kiosco queda iluminado de noche. Va en la misma vertical que
        // la campana (cuatro bloques más arriba) y cuelga de la piedra del centro del tejado (I14): por eso esa
        // celda nunca puede quedar en aire (ver el beacon del sello, migración 58).
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
        // El ahumador viejo del kiosco se retira (una sola vez: si ya no está, no se toca nada). Su celda vuelve a
        // ser la plataforma de piedra del kiosco, que es lo que era.
        BlockPos viejo = new BlockPos(center.getX() + 2, nivel + 1, center.getZ() + 1);
        if (level.getBlockState(viejo).is(Blocks.SMOKER)) {
            colocar(level, viejo, Blocks.STONE_BRICKS.defaultBlockState(), 3);
            // OJO: aquí se ponía además una MESA DE TRABAJO en la CELDA CENTRAL del kiosco (era la mesa del
            // cocinero cuando el kiosco era la cocina). Ya no: desde la etapa F el cocinero tiene su cocina —y su
            // mesa— en la taberna (`cocinaDeLaTaberna`) y la celda central del kiosco es <b>de la campana</b>
            // (migración 58): poner ahí la mesa dejaba al kiosco sin sitio para su campana en las aldeas viejas.
            DevilRpg.LOGGER.info("[Village] Aldea en {}: el ahumador viejo del kiosco se retiro (la cocina ya esta"
                    + " en la taberna)", center);
        }
    }

    private static BlockState escalera(Direction hacia) {
        return Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, hacia)
                .setValue(StairBlock.HALF, Half.BOTTOM);
    }

    /** Quita el aire y bloques que queden en la columna por encima de {@code baseY+1}. */
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
     * ¿Ese bloque es <b>agua</b>? Vale también el <b>hielo</b> (y el hielo escarchado o el azul de los témpanos,
     * que van en la misma etiqueta): es la misma agua de un bioma frío, congelada en la superficie, y un lago
     * congelado sigue siendo un lago.
     * <p>
     * Hace falta distinguirlo porque <b>el agua NO es un hueco que se rellene</b>: el nivelado tapa los huecos que
     * quedan por debajo de la cota y, tratando el agua como "terreno que sobra" ({@link #esTerrenoRecortable}),
     * rellenaba con tierra y césped el <b>lago de la pesquera</b> —que es agua construida a propósito, como la
     * acequia de la granja—. Medido en el guardado del jugador: el estanque del pescador salía como una plaza de
     * césped (ver {@link #repararLagoDeLaPesquera}).
     */
    private static boolean esAguaOHielo(BlockState state) {
        return !state.getFluidState().isEmpty() || state.is(BlockTags.ICE);
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
                // Y NO SE PISA LO QUE NO ES TERRENO (23-sep-2026): la superficie de esa columna puede ser una
                // ESCALERA (la de una entrada del kiosco), una losa, tierra labrada o un bloque del jugador, y el
                // camino la SUSTITUÍA. Medido en su guardado (aldea 0): la escalera sur del kiosco —la celda
                // `(470,63,650)`, que el constructor pone con `escalera(Direction.NORTH)`— estaba convertida en
                // `dirt_path` (un bloque de tierra apisonada SOBRESALIENDO un bloque, porque `groundY` ya veía la
                // escalera y el camino se pintó encima), y como el plano de la aldea se captura escaneando el mundo,
                // el obrero lo daba por bueno y la escalera no volvía nunca. El jugador lo vio como *"¿por qué el
                // kiosco tiene un bloque de tierra en vez de escaleras?"*.
                if (!esTerrenoNatural(level.getBlockState(new BlockPos(px, y, pz)))) {
                    continue; // ahí hay algo construido: el camino pasa de largo
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
                    BlockPos celda = columna.atY(y);
                    BlockState actual = level.getBlockState(celda);
                    // EL AGUA NO ES UN HUECO QUE SE RELLENA (y el hielo de un bioma frío es la misma agua). Aquí es
                    // donde se tapaba el lago de la pesquera: el agua del estanque, a `cota-1` y `cota-2`, salía
                    // convertida en césped y tierra. Ver `esAguaOHielo` y `repararLagoDeLaPesquera`.
                    if (esAguaOHielo(actual)) {
                        continue;
                    }
                    // LO QUE HA CAVADO EL MINERO NO SE RELLENA (I102): el pozo y las galerías son AIRE con suelo de
                    // aldea debajo —exactamente lo que el nivelado tapa—, así que sin esto, renivelar una aldea con
                    // mina la enterraba entera y el minero volvía a cavarla. Solo se protege el AIRE: el terreno
                    // natural de esa zona se nivela como cualquier otro (si no, la mina dejaría un hoyo en la meseta
                    // de la aldea desde el día en que se genera, antes de que nadie cave).
                    // OJO CON LA CAPA QUE SE PISA (medido el 26-sep-2026): `esCeldaDeLaMina` excluye A PROPÓSITO la
                    // superficie (`pos.getY() >= nivel - 1` es del pueblo: ahí está la caseta), pero el caracol
                    // SALE a la superficie —sus primeros escalones cruzan justo esa capa—, así que este relleno,
                    // que va hasta `baseY - 1`, le volvía a poner CÉSPED encima: medido en la partida del jugador,
                    // los pasos 1 y 2 del caracol tenían césped en la celda de paso y los 3 y 4 en la de la cabeza,
                    // y el pozo quedaba SIN RUTA (el minero no bajaba: `pasos=16` congelado). Por eso aquí también
                    // se protege el pozo (`estaSobreElPozo`), que es la zona de la mina en horizontal.
                    if (actual.isAir() && (esCeldaDeLaMina(center, baseY, celda)
                            || estaSobreElPozo(center, celda))) {
                        continue;
                    }
                    // Y LO QUE TIENE TECHO ENCIMA TAMPOCO ES UN HUECO (28-sep-2026; LA REGLA DEL CIELO): el nivelado
                    // rellena los hoyos del patio a cielo abierto, no el interior de un edificio. Sin esto, la celda
                    // del HOGAR de la taberna (a la cota, bajo el forjado de la posada) se rellenaba de césped y la
                    // fogata desaparecía de su propio hogar.
                    if (actual.isAir() && !level.canSeeSky(celda)) {
                        continue;
                    }
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
                // EL POZO DE LA MINA NO SE TAPA (26-sep-2026): la mina SALE a la superficie —sus primeros escalones
                // cruzan la capa que se pisa—, así que su columna tiene la capa del suelo hueca **a propósito** y
                // este tapagujeros la rellenaba entera (césped arriba y tierra debajo hasta el primer bloque firme):
                // el pozo quedaba sellado y el minero, que va a una casilla de dentro, sin ruta (medido: `pasos=16`
                // congelado). `sellarSuelo` no miraba la mina en absoluto; `nivelar` la protege por `esCeldaDeLaMina`
                // salvo la capa de arriba, así que las dos preguntas van juntas.
                if (estaSobreElPozo(center, new BlockPos(px, baseY - 1, pz))) {
                    continue;
                }
                // Y LO QUE TIENE TECHO ENCIMA NO ES UN HUECO DEL SUELO (28-sep-2026; LA REGLA DEL CIELO, la misma que en
                // el tapagujeros del obrero): este tapado es para los hoyos del patio, **a cielo abierto**, no para el
                // interior de un edificio. MEDIDO con el mapa de capas: la FOGATA del hogar de la taberna —que está a
                // la cota y justo debajo del forjado de la posada— aparecía convertida en TIERRA (su celda salía con
                // `D`), así que el comedor se quedaba sin fuego. Lo pidió el jugador: *"en el centro haya un hueco donde
                // pueda estar una fogata"*.
                if (!level.canSeeSky(new BlockPos(px, baseY - 1, pz))) {
                    continue;
                }
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

    private static void spawnVillager(ServerLevel level, BlockPos pos, @Nullable VillagerProfession profession,
                                      boolean baby) {
        // OJO: NO se usa la Y que nos pasan (la del centro de la aldea). El terreno nivelado puede quedar a otra
        // altura en esta columna (el centro es una columna suelta y las cabañas y caminos ya usan groundY por
        // columna), así que un aldeano colocado a la Y del centro quedaba ENTERRADO: se asfixiaba y moría en
        // ~10 s (1 de daño cada 10 ticks x 20 de vida) y al llegar a la aldea no había ningún aldeano.
        BlockPos posicion = huecoLibre(level, new BlockPos(pos.getX(), groundY(level, pos.getX(), pos.getZ()), pos.getZ()));
        Villager villager = EntityType.VILLAGER.create(level, null, posicion, MobSpawnType.MOB_SUMMONED, true, true);
        if (villager != null) {
            if (profession != null) {
                villager.setVillagerData(villager.getVillagerData().setProfession(profession));
                // SIN ESTO LA PROFESIÓN SE PIERDE: el cerebro vanilla trae el comportamiento `ResetProfession`, que
                // devuelve al aldeano a SIN OFICIO cuando no tiene `JOB_SITE` en el cerebro, su XP es 0 y su nivel es 1.
                // Nuestros aldeanos se nombran por código (no reclaman un puesto de trabajo del juego), así que a los
                // pocos segundos TODOS volvían a `none`: la aldea se quedaba SIN GRANJERO (nadie cosechaba, nadie
                // horneaba pan y la despensa nunca se llenaba: la aldea pasaba hambre con la huerta llena) y sin
                // herreros. Con 1 de XP la condición `getVillagerXp() == 0` ya no se cumple y la profesión se mantiene.
                // (Medido en el guardado del jugador: aldea 10 con 4 aldeanos `none` + 1 holgazán a los 38 s de nacer.)
                villager.setVillagerXp(1);
            }
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
     * Una <b>cría nueva de la aldea</b> (etapa H), <b>sin oficio</b> a propósito.
     * <p>
     * Es la que hace crecer al pueblo por encima de sus puestos, que es de donde sale la <b>milicia</b>: el jugador lo
     * pidió así —*"la milicia se va a ir llenando conforme vayan naciendo y alcanzando la adultez aldeanos"*—. Nace
     * <b>SIN PROFESIÓN</b> (no con el oficio de una plaza, que sería un oficio DUPLICADO: la versión vieja usaba el
     * número de aldeanos vivos como índice de plaza): al crecer, el latido le da una plaza <b>si queda alguna libre</b>
     * (`reponerProfesiones`) y, si no, es gente de sobra: la milicia o un obrero. Nace en el sitio de una de las plazas
     * (la que toque por el reloj, para no apilarlas todas en la misma esquina) y con el hueco libre garantizado.
     */
    public static void spawnBaby(ServerLevel level, BlockPos center) {
        BlockPos spot = VILLAGER_SPOTS[(int) Math.floorMod(level.getGameTime(), VILLAGER_SPOTS.length)];
        spawnVillager(level, center.offset(spot), null, true);
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
     * Los sitios fijos de aldeano de la aldea (uno por plaza de {@link #VILLAGER_SPECIALTIES}: mismo orden y misma
     * longitud, que {@code spawnOneVillager} cruza los dos arrays).
     * <p>
     * Van <b>repartidos en un anillo</b> a unos 24-27 bloques de la plaza, entre el kiosco (radio 3) y los solares
     * nuevos, que con el muro a 62 empiezan a 33-38: siempre en patio abierto y sin caer dentro de una casa, del
     * almacén ni de las parcelas de la granja. (Con el trazado de 36 el anillo estaba a 13-15; al crecer la aldea se
     * ha llevado al doble para que el centro no quede apelotonado.)
     * <p>
     * Los últimos puestos <b>no</b> van en ese anillo, porque viven donde trabajan: el ganadero en el corral, el
     * cocinero junto a la plaza (y desde la taberna, en su cocina), el segundo granjero entre los bancales del sur, el
     * pescador junto a su pesquera, el TERCER granjero junto al tercer bancal y el LEÑADOR al lado de su taller (en la
     * arboleda). Ninguno puede caer <b>bajo un tejado</b> (el del cobertizo del corral, el del kiosco o el del taller):
     * {@code groundY} devolvería la altura del TEJADO y el aldeano aparecería <b>encima</b> de él.
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
            new BlockPos(20, 0, 30),
            // El TERCER GRANJERO (etapa H, lo pidió el jugador: "necesitamos un 3er granjero que vaya a la granja que
            // está vacía"): al oeste del TERCER bancal (el de (-28,34), que tenía su compostero sin dueño), en patio
            // abierto y a la misma altura que el segundo.
            new BlockPos(-34, 0, 30),
            // El LEÑADOR (etapa H): al lado de su taller, en la arboleda (el taller está en (-52..-48, -26..-22)), y
            // FUERA del cobertizo por lo mismo que el ganadero y el cocinero.
            new BlockPos(-50, 0, -19),
            // El MINERO (etapa I): al lado de su caseta y de la boca de la mina. El solar de 11x11 va en rel
            // (+28,-34) (eje (503,617)), en el descampado del noreste y a 18 del muro: el sitio está al sur de la
            // caseta (3 celdas más allá del anillo), en patio abierto y sin caer bajo su tejado.
            new BlockPos(33, 0, -22)
    };
    /**
     * Oficios de la aldea, en el orden en que se ocupan los sitios (<b>mismo orden y misma longitud</b> que
     * {@link #VILLAGER_SPOTS}):
     * <ol>
     *   <li><b>Granjero</b>: cultiva, cosecha, fertiliza y hornea el pan en la despensa. Desde la etapa F hay
     *       <b>dos</b> y desde la etapa H <b>tres</b> (uno por bancal de los tres: con dos, la comida no daba para el
     *       pueblo y el tercer bancal se quedaba sin nadie).</li>
     *   <li><b>Herrero de armas</b> y <b>clérigo</b>: los oficios "de oficio" de la aldea.</li>
     *   <li><b>Herrero de herramientas</b>.</li>
     *   <li><b>Holgazán</b> (nitwit) = el <b>RECOLECTOR</b>: no tiene oficio propio a propósito, así no reclama
     *       ningún puesto de trabajo y se dedica <b>solo</b> a recoger cosas del pueblo, guardarlas en el almacén y
     *       mover las cadenas de suministro. Antes esto lo hacía el constructor y se pasaba el día recolectando en vez
     *       de reparar; y hasta la etapa H también talaba (ver el leñador, la última plaza).</li>
     *   <li><b>Pastor</b> = el <b>GANADERO</b> de la granja anexa (etapa D): vive en el corral de fuera de la valla,
     *       cría a los animales y baja la carne y la lana al almacén.</li>
     *   <li><b>Carnicero</b> = el <b>COCINERO</b> de la aldea (etapa E): cocina en el ahumador la carne cruda y las
     *       patatas que le llegan (crudo = 2 puntos de comida, cocinado = 4) y desde la etapa F lo hace en la
     *       <b>taberna</b>, que es donde come el pueblo.</li>
     *   <li><b>Pescador</b> (etapa G): pesca en el lago de su pesquera y baja el pescado a la despensa.</li>
     *   <li><b>Flechero</b> (etapa H) = el <b>LEÑADOR</b>: su estación es la <b>mesa de flechas</b> de su taller, en la
     *       arboleda. Tala los árboles de verdad, los replanta, repuebla el monte y baja la madera al almacén. Antes
     *       esto lo hacía el recolector "y no gastaba un puesto"; el jugador pidió separarlos para que el recolector
     *       quede libre para recoger y transportar.</li>
     *   <li><b>Albañil (MASON)</b> (etapa I) = el <b>MINERO</b>: su estación es el <b>cortapiedras</b> de su caseta, y su
     *       faena es la <b>mina</b> (ver {@link #asegurarLaMinaDelPueblo}): baja un caracol de escalones de adoquín,
     *       abre galerías, saca los minerales, los funde en su horno, filtra el cobblestone en agua para sacar
     *       <b>pedernal</b> y lo baja todo al almacén. Lo pidió el jugador: *"mejor haz otra profesión que sea de
     *       minero, y que excave el suelo hacia abajo, haciendo túneles, andamiajes, soportes, escaleras en espiral"*.
     *       Hasta aquí el <b>cortapiedras</b> estaba <b>prohibido</b> en la aldea (I31): ahora es la estación de un
     *       oficio del pueblo, con su plaza y su reparto.</li>
     * </ol>
     */
    private static final VillagerProfession[] VILLAGER_SPECIALTIES = {
            VillagerProfession.FARMER, VillagerProfession.WEAPONSMITH, VillagerProfession.CLERIC,
            VillagerProfession.TOOLSMITH, VillagerProfession.NITWIT, VillagerProfession.SHEPHERD,
            VillagerProfession.BUTCHER, VillagerProfession.FARMER, VillagerProfession.FISHERMAN,
            VillagerProfession.FARMER, VillagerProfession.FLETCHER, VillagerProfession.MASON
    };

    /**
     * Cuántos puestos de <b>cada oficio</b> tiene el pueblo: se <b>cuentan</b> los sitios de
     * {@link #VILLAGER_SPECIALTIES} (dos granjeros, un pescador, un herrero de cada...). Lo usa el reparto de la
     * <b>milicia</b> para saber quién <b>cubre un puesto</b> y quién es gente de sobra.
     * <p>
     * Es un <b>método</b> y no una lista escrita a mano en el gestor <b>a propósito</b>: la lista a mano se quedó con
     * <b>siete</b> puestos (los de la etapa E) y cuando llegaron el <b>segundo granjero</b> (etapa F) y el
     * <b>pescador</b> (etapa G) nadie la subió, así que para el reparto esos dos oficios eran "gente de sobra": la
     * milicia se llevaba al pescador y al segundo granjero (medido en el guardado del jugador, aldea 2: los 4
     * espadachines eran los dos pescadores, el segundo granjero y un aldeano sin oficio) y la pesquera y un bancal se
     * quedaban sin nadie. Contándolos, la lista no puede volver a quedarse atrás.
     */
    public static Map<VillagerProfession, Integer> puestosPorOficio() {
        Map<VillagerProfession, Integer> cupo = new HashMap<>();
        for (VillagerProfession oficio : VILLAGER_SPECIALTIES) {
            cupo.merge(oficio, 1, Integer::sum);
        }
        return cupo;
    }

    /**
     * ¿Ese oficio es uno de los del <b>pueblo</b>? Los de fuera (bibliotecario, cartógrafo, albañil, flechero...)
     * <b>no</b> se usan: el pueblo reparte <b>sus</b> puestos ({@link #VILLAGER_SPECIALTIES}), y un aldeano que tome
     * otro oficio vuelve al reparto (ver {@code VillageManager.reponerProfesiones}).
     * <p>
     * Importa porque en vanilla cada <b>bloque de puesto de trabajo</b> da su oficio: el <b>barril</b> es del
     * <b>pescador</b> —que desde la etapa G es un oficio del pueblo, con su pesquera y su lago— y el atril del
     * bibliotecario, así que una cría que creciera al lado de un atril se habría vuelto bibliotecaria. En la aldea no
     * hay puestos de oficios de fuera: las pipas de cerveza de la taberna son de madera con corteza (un `BARREL`
     * sería el puesto del pescador).
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
     * ¿Ese oficio es una <b>faena propia</b>, es decir un goal de oficio que le disputa el turno al de
     * <b>reparar</b>? Es todo oficio del pueblo <b>menos el del recolector</b> ({@code NITWIT}).
     * <p>
     * Hace falta separarlo de {@link #esOficioDelPueblo} porque el <b>recolector ocupa una plaza del reparto</b>
     * (está en {@link #VILLAGER_SPECIALTIES}: es un puesto del pueblo y hay que reponerlo si falta) pero <b>no tiene
     * oficio</b>: sin faena suya, es <b>EL constructor</b> de la aldea y lleva la reparación a prioridad <b>3</b>, por
     * delante de todo (ver {@code VillageManager.marcarObrero} y el orden de {@code VillageManager.vigilarObreros}).
     * <p>
     * Esto era un <b>fallo silencioso</b>: el recolector entró en {@code VILLAGER_SPECIALTIES} al darle el puesto, y
     * las dos preguntas de "¿tiene faena?" se hacían con {@code esOficioDelPueblo}, así que a partir de ahí el
     * constructor del pueblo pasó a reparar a <b>prioridad 5</b> —la última, por detrás de su propio goal de recoger
     * (5) y del de oficio de los demás— mientras los comentarios seguían diciendo que iba a la 3. Medido en el arnés
     * ({@code MEDIR_AGUJERO}, aldea 2 de la copia): el recolector <b>Anselmo</b> tenía marcada la reparación pero
     * <b>nunca</b> aparecía entre sus goals activos ({@code activos=[VillagerCollectGoal VillagerGateGoal]} en las tres
     * medidas, cada 10 s), y el cráter de creeper del jugador se quedaba abierto con él al lado.
     */
    public static boolean tieneFaenaPropia(VillagerProfession profesion) {
        return esOficioDelPueblo(profesion) && profesion != VillagerProfession.NITWIT;
    }

    /**
     * Cuántos <b>puestos fijos</b> tiene una aldea: uno por sitio de {@link #VILLAGER_SPOTS} (once desde la etapa H:
     * tres granjeros, los dos herreros, el clérigo, el recolector, el ganadero, el cocinero, el pescador y el
     * leñador). Lo usa el gestor como <b>tope de crecimiento</b> de los puestos y como <b>aldea sana</b>: a partir de
     * ahí, los que nacen son gente de sobra (la milicia).
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
                    // LA HUERTA ENTRA SIEMPRE, la tenga el mundo como la tenga: sus celdas (tierra de cultivo y
                    // acequia) son geometría fija del pueblo y el plano tiene que pedirlas aunque en este momento
                    // estén pisoteadas (tierra o césped), vaciadas o con el agua congelada. Sin esto el obrero no
                    // tenía NADA que reponer en ellas, porque el plano de una aldea migrada es un ESCANEO del mundo
                    // y la tierra o el césped se descartan como "terreno natural": medido en el guardado del jugador
                    // (aldea 2, cota 120), el bancal oeste tenía 2 calvas de césped en (1384,1430) y (1384,1434) que
                    // NO estaban en el plano — las "dos manchas de tierra" que el jugador veía sin reparar.
                    BlockState state = estadoDeLaHuerta(center, nivel, pos);
                    if (state == null) {
                        state = estadoDelPlano(level.getBlockState(pos));
                        if (seDescartaDelPlano(state)) {
                            continue;
                        }
                    }
                    // LA ARBOLEDA DEL PUEBLO ES DEL LEÑADOR: sus TRONCOS no entran en el plano. El plano de una aldea
                    // migrada es un ESCANEO del mundo, así que los árboles que ya habían crecido en la arboleda
                    // quedaban apuntados; como el leñador los tala y los replanta a propósito (es la madera del
                    // pueblo), el plano pedía reponerlos y el obrero los volvía a levantar, ya sin hojas, como
                    // troncos FLOTANDO. Medido en el guardado del jugador (aldea 2): 9 troncos de acacia de la
                    // arboleda apuntados como huecos. Es el mismo motivo por el que `asegurarArboleda` pone sus
                    // plantones con `setBlock` directo, fuera del plano.
                    if (enLaArboleda(center, pos) && state.is(BlockTags.LOGS)) {
                        continue;
                    }
                    // EL LAGO DE LA PESQUERA, FUERA DEL PLANO: su agua (y su orilla y su fondo) la mantiene
                    // `repararLagoDeLaPesquera`, que también da el lago por bueno con HIELO. En el plano sería al
                    // revés: `necesitaReparacion` repone el agua en cuanto la ve congelada (regla que hace falta
                    // para la acequia, que va tapada con una losa y no se congela) y el obrero se pasaría la vida
                    // descongelando el lago de un bioma frío, donde el hielo es justo lo que tiene que haber.
                    if (esCeldaDelLago(center, nivel, pos)) {
                        continue;
                    }
                    // LA MINA, FUERA DEL PLANO (I102): lo que cava y construye el MINERO es suyo y lo mantiene él
                    // (lo pidió el jugador: *"su lugar de trabajo no lo debe regenerar ningún otro trabajador, ya que
                    // se taladraría seguido"*). Si sus escalones y soportes entraran en el plano, el obrero se
                    // pasaría el día reponiéndolos y el minero cavándolos. Solo hace falta alrededor de la BOCA
                    // (`estaSobreElPozo`): el plano solo mira dos bloques por debajo de la cota, y ahí lo único del
                    // minero es el arranque del caracol. La capa de la cota (la boca, y la caseta) sí es del pueblo.
                    if (pos.getY() < nivel - 1 && estaSobreElPozo(center, pos)) {
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
     * Un bloque del plano <b>tal y como tiene que quedar</b>. Hoy cambia dos estados <b>transitorios</b>, que no son
     * "lo que la aldea debe ser" y que, guardados tal cual, hacían que el obrero "reparase" celdas que estaban bien:
     * <ul>
     *   <li>la <b>puerta de valla</b>, que se guarda y se repone <b>siempre cerrada</b> (la abre el pueblo para pasar
     *       y la vuelve a cerrar), y</li>
     *   <li>la <b>humedad de la tierra de cultivo</b> ({@code moisture} 0..7), que la sube y la baja el propio juego
     *       con el agua de al lado, la sequía y la lluvia.</li>
     * </ul>
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
        // La HUMEDAD de la tierra de cultivo (`moisture` 0..7) también es TRANSITORIA: el juego la sube a 7 en
        // cuanto tiene agua al lado y la va bajando (hasta 0) en seco, y la lluvia la vuelve a subir. Guardando el
        // estado entero, el plano de una aldea quedaba con `moisture=7` y el de otra con otro valor, y comparar el
        // estado COMPLETO daba la celda por "dañada" cada vez que el juego la cambiaba: el obrero se habría pasado
        // la vida "reparando" la huerta. El plano la apunta SECA y regarla es cosa del juego (o de
        // `tierraDeCultivo`, que es lo que se pone al reponer). Ver `VillageManager.necesitaReparacion`.
        if (state.is(Blocks.FARMLAND)) {
            return Blocks.FARMLAND.defaultBlockState();
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
                if (esFilaDeAcequia(dx, dz)) {
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
        int compX = corner.getX() - COMPOSTERO_DX; // FUERA de la valla y sin pegarse a ella (ver COMPOSTERO_DX, I40)
        int compZ = corner.getZ();
        // SI EL COMPOSTERO YA ESTÁ DONDE TIENE QUE ESTAR, NO SE TOCA NADA. Esto corre en el latido (10 s) para cada
        // bancal ya hecho, y quitar el compostero para volverlo a poner **tira su punto de interés** cada vez: el
        // puesto se queda con el ticket cogido y sin dueño (`free_tickets=0`, I23) y **nadie puede reclamarlo**.
        // Medido con el arnés: de los tres granjeros, dos reclamaron su compostero y la tercera se quedó
        // `SIN PUESTO` (su etiqueta y su faena sí, pero sin estación: el cerebro no le registra el trabajo).
        for (int y = nivel - 2; y <= nivel + 2; y++) {
            if (level.getBlockState(new BlockPos(compX, y, compZ)).is(Blocks.COMPOSTER)) {
                return; // ya está: no se quita ni se vuelve a poner (y el suelo de debajo se deja como está)
            }
        }
        // Se quita el compostero de la columna nueva Y el de la VIEJA (`corner.x-2`, pegada a la valla: la colocación
        // vieja, que es lo que le servía de escalón al granjero para saltarla). Solo composteros: lo del jugador se
        // queda. Así el bancal de una aldea ya construida se corrige aunque `farm` no vuelva a pasar (migración 64).
        for (int cx : new int[]{compX, corner.getX() - 2}) {
            for (int y = nivel - PROFUNDIDAD_SOLAR - 2; y <= nivel + 6; y++) {
                BlockPos p = new BlockPos(cx, y, compZ);
                if (level.getBlockState(p).is(Blocks.COMPOSTER)) {
                    colocar(level, p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
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
     * <b>Mueve el compostero de cada bancal UNA celda más afuera</b> (migración 64): de {@code corner.x-2} —pegado a
     * la valla— a {@code corner.x-3} ({@link #COMPOSTERO_DX}).
     * <p>
     * Por qué: pegado a la valla, la tapa del compostero ({@code cota+1}) queda a un paso de <b>0,5</b> del lomo de la
     * valla (1,5), por debajo del {@code maxUpStep} (0,6), así que el granjero <b>subía a la valla</b> para entrar al
     * bancal en vez de usar las compuertas (lo reportó el jugador; medido: los <b>tres</b> bancales de la aldea 2
     * tenían ese escalón, y era el compostero). Una celda más afuera ya no hay desde dónde subir.
     * <p>
     * Es <b>conservador</b>: solo actúa si el compostero viejo <b>sigue siendo un compostero</b> (lo que haya puesto el
     * jugador se queda) y si la celda nueva <b>está libre</b> con suelo firme debajo; si no, lo dice en el log y no
     * toca nada. Y es <b>idempotente</b>: si el compostero ya está en su sitio nuevo, no hace nada.
     *
     * @return los pares {@code {viejo, nuevo}} de los composteros movidos, para que el latido arregle el
     *         {@code JOB_SITE} de los granjeros que apuntaban al viejo (ver {@code VillageManager}).
     */
    public static List<BlockPos[]> moverComposterosDelBancal(ServerLevel level, BlockPos center) {
        List<BlockPos[]> movidos = new ArrayList<>();
        int cota = cotaDeLaPlaza(level, center);
        for (int i = 0; i < FARM_PLOTS.length; i++) {
            BlockPos nuevo = composteroDeLaParcela(center, i, cota);
            if (level.getBlockState(nuevo).is(Blocks.COMPOSTER)) {
                continue; // ya está en su sitio (aldea nueva o ya migrada)
            }
            BlockPos viejo = buscarComposteroEnLaColumna(level, composteroViejoDeLaParcela(center, i, cota));
            if (viejo == null) {
                continue; // no hay compostero viejo que mover
            }
            boolean libre = level.getBlockState(nuevo).isAir() && level.getBlockState(nuevo.above()).isAir()
                    && !level.getBlockState(nuevo.below()).getCollisionShape(level, nuevo.below()).isEmpty();
            if (!libre) {
                DevilRpg.LOGGER.info("[Village] Bancal {} de {}: no se mueve su compostero (la celda nueva {} no está"
                        + " libre): el granjero podrá seguir subiendo a la valla por ahí", i, center.toShortString(),
                        nuevo.toShortString());
                continue;
            }
            colocar(level, nuevo, Blocks.COMPOSTER.defaultBlockState(), Block.UPDATE_ALL);
            colocar(level, viejo, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            movidos.add(new BlockPos[]{viejo, nuevo});
        }
        if (!movidos.isEmpty()) {
            DevilRpg.LOGGER.info("[Village] Aldea en {}: {} compostero(s) de bancal movidos una celda fuera de la valla"
                    + " (pegados a ella eran el escalón para saltarla, I40)", center.toShortString(), movidos.size());
        }
        return movidos;
    }

    /** El compostero que haya en esa columna (un bloque por encima o por debajo de esa Y), o {@code null}. */
    @Nullable
    private static BlockPos buscarComposteroEnLaColumna(ServerLevel level, BlockPos pos) {
        for (int dy = -2; dy <= 2; dy++) {
            BlockPos q = pos.offset(0, dy, 0);
            if (level.getBlockState(q).is(Blocks.COMPOSTER)) {
                return q;
            }
        }
        return null;
    }

    /**
     * El <b>punto al que se camina</b> para usar ese compostero: una celda de al lado con <b>sitio para pararse</b>.
     * <p>
     * El compostero es un bloque <b>sólido</b> y navegar hacia un bloque sólido no lleva a ninguna parte: el aldeano
     * se queda dando vueltas alrededor (es el mismo fallo que documenta {@code VillageStorage.puntoDeApoyo} con el
     * cenador del almacén y el ahumador del kiosco). <b>Medido con el arnés</b>: con el compostero como destino, la
     * granjera Cesarea —que lo tenía a 10 bloques, al otro lado de la valla— se perdió, <b>se subió a la valla</b> y
     * acabó vagando lejos de su bancal.
     */
    public static BlockPos puntoDeApoyoDelCompostero(ServerLevel level, BlockPos compostero) {
        for (BlockPos p : new BlockPos[]{compostero.north(), compostero.south(), compostero.west(), compostero.east()}) {
            if (level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                    && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                    && !level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty()) {
                return p.immutable();
            }
        }
        return compostero; // sin hueco al lado: se devuelve el propio compostero (no hay nada mejor)
    }

    /**
     * <b>Los dos extremos de la acequia, de vuelta a celdas de cultivo</b> (migración 65). La losa que tapa el canal
     * en sus dos últimas celdas se pisa a {@code cota+0,5} y las compuertas del bancal caen justo en la fila del
     * medio: desde ahí el aldeano <b>saltaba la valla</b> (ver {@link #esFilaDeAcequia}). Se quita la losa y el agua
     * se convierte en <b>tierra de cultivo</b> (regada), así que la capa que se pisa vuelve a estar a la altura de la
     * tierra y desde ahí no se llega al lomo de la valla. Es <b>conservador</b> (solo si siguen siendo el agua y su
     * losa: lo que haya puesto el jugador se queda) e <b>idempotente</b>.
     */
    public static int rehacerLosExtremosDeLaAcequia(ServerLevel level, BlockPos center) {
        int cota = cotaDeLaPlaza(level, center);
        int cambios = 0;
        for (int[] plot : FARM_PLOTS) {
            for (int dx : new int[]{0, PLOT_WIDTH - 1}) {
                BlockPos tierra = new BlockPos(center.getX() + plot[0] + dx, cota - 1,
                        center.getZ() + plot[1] + PLOT_WATER_ROW);
                BlockPos losa = tierra.above();
                boolean esAgua = level.getFluidState(tierra).is(net.minecraft.tags.FluidTags.WATER);
                boolean esLosa = level.getBlockState(losa).is(Blocks.OAK_SLAB);
                if (!esAgua && !esLosa) {
                    continue; // ya está hecho (o esa celda la tocó el jugador): no se toca
                }
                if (esLosa) {
                    colocar(level, losa, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    cambios++;
                }
                if (esAgua) {
                    colocar(level, tierra, tierraDeCultivo(level, tierra), Block.UPDATE_ALL);
                    cambios++;
                }
            }
        }
        if (cambios > 0) {
            DevilRpg.LOGGER.info("[Village] Aldea en {}: los extremos de la acequia vuelven a ser celdas de cultivo ({}"
                    + " celdas): desde su losa se saltaba la valla del bancal (I40)", center.toShortString(), cambios);
        }
        return cambios;
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
        for (int i = 0; i < FARM_PLOTS.length; i++) {
            portones.addAll(portonesDeLaParcela(center, i, nivel));
        }
        return portones;
    }

    /**
     * <b>TODOS los portones de valla de la aldea</b>: los doce de los bancales (el granjero los cruza para entrar y
     * salir de su huerta) y los dos del anexo (el del corral y el del gallinero). Vive en <b>un solo sitio</b> (I4):
     * lo usan el goal que los abre ({@code VillagerGateGoal}), el despeje de su hueco y el filtro del plano (I54).
     */
    public static List<BlockPos> todosLosPortones(BlockPos center, int nivel) {
        List<BlockPos> portones = new ArrayList<>(portonesDeLosBancales(center, nivel));
        portones.add(portonDelCorral(center, nivel));
        portones.add(portonDelGallinero(center, nivel));
        return portones;
    }

    /**
     * <b>EL HUECO DE UN PORTÓN ES SAGRADO</b> (I54): ni un farol dentro. El del <b>corral anexo</b> lo tenía: el
     * layout viejo de las luces de la cerca ponía un farol en el <b>medio de cada lado</b> de la valla, y el medio del
     * lado <b>oeste es el portón</b>, así que la hoja llevaba un farol encima. Medido en el guardado del jugador
     * (aldea 2, cota 120): `(1455,121,1414)` = `lantern` con la puerta justo debajo, y el <b>plano pidiendo ese
     * farol</b>. Un farol tiene caja de colisión, así que el aldeano que cruzaba ocupaba esa celda con el cuerpo y el
     * juego <b>no le encontraba camino</b>: se quedaba <b>encerrado en el corral</b> (el jugador: *"el ganadero quiere
     * ir a la taberna y no puede, la única salida está obstruida por una lámpara"*; su ganadera tenía el almacén
     * aparcado de no poder llegar).
     * <p>
     * El farol se <b>muda a un poste de al lado</b> (no se tira: la luz del pueblo se queda donde hacía falta) y
     * devuelve las celdas que ha despejado, para que el <b>plano</b> no las siga pidiendo (si no, el obrero lo
     * repondría). Es <b>idempotente</b> y solo mira <b>faroles</b>: si en el carril hay otra cosa (lo que puso el
     * jugador), no se toca. Se llama desde el latido, así que vale también para las aldeas ya construidas.
     */
    public static List<BlockPos> despejarElHuecoDeLosPortones(ServerLevel level, BlockPos center) {
        int nivel = cotaDeLaPlaza(level, center);
        if (nivel <= level.getMinBuildHeight() + 1) {
            return List.of();
        }
        List<BlockPos> despejadas = new ArrayList<>();
        for (BlockPos porton : todosLosPortones(center, nivel)) {
            BlockState estado = level.getBlockState(porton);
            if (!(estado.getBlock() instanceof FenceGateBlock)) {
                continue; // ahí no hay portón (una aldea vieja, un bancal movido): no hay hueco que despejar
            }
            boolean enX = estado.getValue(FenceGateBlock.FACING).getAxis() == Direction.Axis.X;
            for (int d = -1; d <= 1; d++) {
                // Las tres celdas por las que se cruza (la hoja y las dos de al lado) y, de cada una, su CABEZA: el
                // aldeano mide 1,95, así que ocupa la capa que se pisa y la de encima. Un farol en la de encima lo
                // deja fuera.
                BlockPos carril = enX ? porton.offset(d, 0, 0) : porton.offset(0, 0, d);
                BlockPos cabeza = carril.above();
                if (!level.getBlockState(cabeza).is(Blocks.LANTERN)) {
                    continue;
                }
                BlockPos poste = posteLibreJuntoAlPorton(level, porton, enX);
                // lint:ok I9 porque es una REPARACION idempotente de unas celdas (muda el farol que tapa un porton) y
                // se llama desde el latido: no rehace nada, asi que no necesita migracion.
                colocar(level, cabeza, Blocks.AIR.defaultBlockState(), 3);
                if (poste != null) {
                    farolSobreElPoste(level, poste);
                }
                despejadas.add(cabeza);
                DevilRpg.LOGGER.info("[Village] Aldea en {}: farol mudado del hueco del porton {} a {}",
                        center, porton.toShortString(), poste == null ? "(sin poste libre: se quito)" : poste.toShortString());
            }
        }
        return despejadas;
    }

    /**
     * Un <b>poste de la valla</b> junto al portón al que mudar el farol que lo tapaba: el primero (a lo largo de la
     * valla, que va <b>perpendicular</b> al eje de cruce) que sea valla y tenga el hueco de encima libre. {@code null}
     * si no hay ninguno (entonces el farol se quita y ya: la luz de al lado lo cubre).
     */
    @Nullable
    private static BlockPos posteLibreJuntoAlPorton(ServerLevel level, BlockPos porton, boolean enX) {
        for (int d = 1; d <= 4; d++) {
            for (int signo : new int[]{-1, 1}) {
                BlockPos p = enX ? porton.offset(0, 0, signo * d) : porton.offset(signo * d, 0, 0);
                BlockState estado = level.getBlockState(p);
                if (!estado.is(Blocks.OAK_FENCE) && !estado.is(Blocks.SPRUCE_FENCE)) {
                    continue;
                }
                if (level.getBlockState(p.above()).isAir()) {
                    return p;
                }
            }
        }
        return null;
    }

    /** Las cuatro puertas de valla de <b>una</b> parcela (centro de cada lado del anillo). */
    public static List<BlockPos> portonesDeLaParcela(BlockPos center, int i, int nivel) {
        int[] plot = FARM_PLOTS[i];
        int x0 = center.getX() + plot[0] - 1;
        int x1 = center.getX() + plot[0] + PLOT_WIDTH;
        int z0 = center.getZ() + plot[1] - 1;
        int z1 = center.getZ() + plot[1] + PLOT_DEPTH;
        List<BlockPos> portones = new ArrayList<>();
        portones.add(new BlockPos((x0 + x1) / 2, nivel, z0));
        portones.add(new BlockPos((x0 + x1) / 2, nivel, z1));
        portones.add(new BlockPos(x0, nivel, (z0 + z1) / 2));
        portones.add(new BlockPos(x1, nivel, (z0 + z1) / 2));
        return portones;
    }

    /**
     * La celda por la que ese aldeano <b>ENTRA</b> al bancal {@code i}: la de <b>dentro</b> del portón más cercano a
     * él (un paso del portón hacia el centro del bancal).
     * <p>
     * Hace falta porque las faenas de la huerta se hacen <b>dentro</b>: el alcance de la faena son 3 bloques, así que
     * un granjero parado <b>fuera</b> de la valla alcanzaba las matas de la primera fila y las cosechaba <b>a través de
     * la reja</b> —no le hacía falta entrar y las del centro se quedaban sin cosechar (lo reportó el jugador: *"los
     * granjeros no están entrando a la granja"*)—. Yendo a esa celda, el aldeano se pone al lado del portón,
     * {@code VillagerGateGoal} se lo abre (a 2,6) y entra.
     */
    public static BlockPos entradaDeLaParcela(BlockPos center, int i, int cota, BlockPos desde) {
        BlockPos esquina = esquinaDeLaParcela(center, i, cota);
        BlockPos centro = esquina.offset(PLOT_WIDTH / 2, 0, PLOT_DEPTH / 2);
        BlockPos mejor = null;
        double mejorDist = Double.MAX_VALUE;
        for (BlockPos porton : portonesDeLaParcela(center, i, cota)) {
            BlockPos dentro = porton.offset(Integer.signum(centro.getX() - porton.getX()), 0,
                    Integer.signum(centro.getZ() - porton.getZ()));
            double d = dentro.distSqr(desde);
            if (d < mejorDist) {
                mejorDist = d;
                mejor = dentro;
            }
        }
        return mejor != null ? mejor : esquina;
    }

    /**
     * La celda por la que ese aldeano <b>SALE</b> del bancal {@code i}: la de <b>FUERA</b> del portón más cercano a
     * él (un paso del portón hacia fuera, al revés que {@link #entradaDeLaParcela}).
     * <p>
     * Tiene que ser la de <b>fuera</b> y no la de dentro: {@code VillagerGateGoal} abre el portón solo si el destino
     * del aldeano está <b>al otro lado</b> ({@code vaACruzar}: el que solo pasa por delante no lo abre). Mandándolo a
     * la celda de dentro —la de entrar— el portón <b>no se abría</b> y el granjero se quedaba pegado a la valla toda
     * la noche (medido con el arnés: Isidoro, con su cama ya reclamada, seguía dentro del bancal en `1394,119,1452`,
     * la celda de dentro del portón este).
     */
    public static BlockPos salidaDeLaParcela(BlockPos center, int i, int cota, BlockPos desde) {
        BlockPos esquina = esquinaDeLaParcela(center, i, cota);
        BlockPos centro = esquina.offset(PLOT_WIDTH / 2, 0, PLOT_DEPTH / 2);
        BlockPos mejor = null;
        double mejorDist = Double.MAX_VALUE;
        for (BlockPos porton : portonesDeLaParcela(center, i, cota)) {
            BlockPos fuera = porton.offset(-Integer.signum(centro.getX() - porton.getX()), 0,
                    -Integer.signum(centro.getZ() - porton.getZ()));
            double d = fuera.distSqr(desde);
            if (d < mejorDist) {
                mejorDist = d;
                mejor = fuera;
            }
        }
        return mejor != null ? mejor : esquina;
    }

    /** ¿Ese aldeano está <b>dentro</b> del bancal {@code i}? (en su tierra de cultivo, no en la valla ni fuera) */
    public static boolean estaDentroDeLaParcela(BlockPos center, int i, int cota, BlockPos pos) {        BlockPos esquina = esquinaDeLaParcela(center, i, cota);
        int dx = pos.getX() - esquina.getX();
        int dz = pos.getZ() - esquina.getZ();
        return dx >= 0 && dx < PLOT_WIDTH && dz >= 0 && dz < PLOT_DEPTH && Math.abs(pos.getY() - cota) <= 2;
    }

    /**
     * <b>¿Esa casilla cae sobre la huella de un bancal?</b> (su cuadrado de 9×9 <b>y su valla</b>). Mira solo X/Z y
     * <b>no necesita la cota</b>, así que es lo bastante barato para preguntarlo en un suceso de spawn: es la guarda
     * que impide que <b>nazca un golem dentro de la huerta</b> (lo pidió el jugador: *"hay un golem dentro de una de
     * las parcelas, quítalo de ahí y que ningún golem pueda spawnear dentro de parcelas"*).
     */
    public static boolean sobreLaHuellaDeUnBancal(BlockPos center, BlockPos pos) {
        for (int i = 0; i < FARM_PLOTS.length; i++) {
            int bx = center.getX() + FARM_PLOTS[i][0];
            int bz = center.getZ() + FARM_PLOTS[i][1];
            if (pos.getX() >= bx - 1 && pos.getX() <= bx + PLOT_WIDTH
                    && pos.getZ() >= bz - 1 && pos.getZ() <= bz + PLOT_DEPTH) {
                return true; // la huella (con la valla) de ese bancal
            }
        }
        return false;
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
     * Las <b>tres</b> celdas que el <b>hueco de subida</b> tiene que dejar libres <b>encima de cada escalón</b> del
     * desván (migración 56). Con <b>dos</b> —lo que pedía la regla vieja, I16— la escalera <b>no se sube</b>.
     * <p>
     * <b>Por qué tres y no dos.</b> El juego no sube un escalón "andando": al chocar con la contrahuella levanta al
     * jugador de golpe hasta {@code Entity.maxUpStep()} (0,6) y comprueba la <b>caja entera</b> en esa posición
     * levantada ({@code Entity.collide}: {@code aabb.expandTowards(dx, maxUpStep, dz)} y luego
     * {@code collideWithShapes}, que resuelve la Y ANTES que la horizontal y recorta la subida contra el techo,
     * {@code subida = techo - cabeza}). O sea: el primer bloque <b>sólido</b> que tenga encima la <b>huella</b> (la
     * cara alta del escalón) tiene que estar a {@code 1,8 + 0,6 = 2,4} bloques de ella — y como los bloques van
     * enteros, hacen falta <b>3</b> celdas libres (el techo queda a 3,0 de la huella). Con dos, el techo queda a
     * <b>2,0</b> y el que sube se queda <b>empujado contra la contrahuella</b>, con la cabeza pegada al techo.
     * <p>
     * <b>Medido en el guardado del jugador</b> (aldea 2, centro {@code 1414,1414}, cota {@code 120}, taberna en
     * {@code 1438,1428}, {@code y1=125}, {@code yTecho=130}) con {@code build/taberna_subida.py}: los <b>dos</b>
     * escalones que no se subían eran el <b>2º</b> ({@code dx=5}, {@code dz=11}: su huella, {@code y=127}, tenía el
     * techo de la posada a 2,0 — los tablones de {@code y=129}) y el <b>3º</b> ({@code dx=5}, {@code dz=10}: su
     * huella, {@code y=128}, tenía la placa de tejas a 2,0 — {@code y=130}). Son <b>exactamente</b> los dos bloques
     * que el jugador tuvo que romper a mano para poder pasar.
     */
    private static final int DESVAN_HUECO_ALTO = 3;
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
     * <b>ABRE EL PASO DE LA TABERNA AL ALMACÉN</b> (migración 72): una <b>puerta de servicio en el muro ESTE de la
     * taberna</b>, a la altura del cobertizo del almacén, para que el <b>cocinero</b> pueda ir a por leña.
     * <p>
     * <b>Por qué hace falta</b> (medido con el arnés, 23-sep-2026): el punto de apoyo del almacén
     * <b>no tenía ruta desde la taberna</b> —el planificador acababa <b>6 bloques antes, contra este mismo muro</b>
     * ({@code rutaAlmacen=a1=14n alcance=NO fin=511,63,666 dFin=6.00}), y los cinco accesos del cobertizo daban lo
     * mismo—, así que el cocinero se quedaba plantado en {@code 511,63,666} (a <b>6,00</b> del punto de apoyo, uno más
     * que el alcance de 5,0 con el que se coge la leña), aparcaba el almacén 5 min y <b>no podía ir por leña</b>: sin
     * leña no enciende el ahumador, o sea que <b>no cocina ni hornea el pan</b>. Los herreros y el minero <b>sí</b>
     * llegaban porque vienen del norte; la taberna, que es donde vive y trabaja el cocinero, quedaba <b>sellada por el
     * este</b> por su propio muro.
     * <p>
     * <b>Y la celda no es caprichosa</b>: el cobertizo lleva los <b>postes en una rejilla de 3 en 3</b>, así que la
     * puerta se abre <b>una celda al sur del centro</b> del almacén, que es la columna despejada de postes (y ahí
     * enfrente está su punto de apoyo, 5 bloques al este). Al otro lado del muro quedan <b>dos celdas de suelo llano a
     * la cota</b> (césped y adoquín) antes del suelo del cobertizo, así que <b>no hay escalón</b>: se entra andando.
     * <p>
     * Es <b>idempotente</b> (I6): si ya hay una puerta no escribe nada, y solo actúa si al otro lado está de verdad el
     * almacén y las celdas del paso están libres y con suelo firme (si hay algo puesto —un cofre, un mueble— mejor no
     * meter una puerta ahí). Va <b>antes de tirar el plano</b> (I8) en las aldeas que migran, y el latido la vuelve a
     * llamar como a {@code asegurarHerreria} para que <b>también la tengan las aldeas nuevas</b> y para reponerla si
     * alguien se la lleva (el paso es del pueblo: sin él, el cocinero no come).
     */
    public static void abrirElPasoDeLaTabernaAlAlmacen(ServerLevel level, BlockPos center) {
        int cota = cotaDeLaPlaza(level, center);
        BlockPos base = baseDeLaTaberna(center);
        BlockPos almacen = VillageStorage.centro(center);
        BlockPos puerta = new BlockPos(base.getX() + TABERNA_ANCHO - 1, cota, almacen.getZ() - 1);
        // 1) SOLO SI EL ALMACÉN ESTÁ A LA ESPALDA DEL MURO ESTE (a 4-8 bloques): si no, esta aldea no es este caso y
        //    lo que hubiera en esa celda no se toca. (El cobertizo mide 7x7: su centro queda a 6 de la pared.)
        int dx = almacen.getX() - puerta.getX();
        if (dx < 4 || dx > 8) {
            return;
        }
        // 2) YA ESTÁ HECHO (idempotente): la puerta ya está puesta.
        if (level.getBlockState(puerta).getBlock() instanceof DoorBlock) {
            return;
        }
        // 3) Y EL PASO, LIBRE Y CON SUELO A LOS DOS LADOS: dentro (la taberna) y fuera (el camino al cobertizo). Si
        //    hay algo puesto (un cofre, un mueble, una barricada) mejor no meter una puerta ahí. La celda del muro da
        //    igual que sea el muro o aire: si el jugador se llevó la pared, la puerta va igual (el paso es del pueblo
        //    y el que la rompa se la encuentra repuesta, como cualquier otra celda del plano).
        for (BlockPos p : new BlockPos[]{puerta.west(), puerta.west().above(), puerta.east(), puerta.east().above()}) {
            if (level.getBlockState(p).isSolid()) {
                return;
            }
        }
        for (BlockPos suelo : new BlockPos[]{puerta.west().below(), puerta.east().below()}) {
            if (!level.getBlockState(suelo).isSolid()) {
                return;
            }
        }
        // 4) Y QUE DE VERDAD SEA UN MURO: a los lados (norte y sur) tiene que haber algo sólido pegado. Sin esto, en
        //    una aldea con otra geometría la puerta podría aparecer suelta en el campo abierto.
        if (!level.getBlockState(puerta.north()).isSolid() && !level.getBlockState(puerta.south()).isSolid()) {
            return;
        }
        colocar(level, puerta, Blocks.DARK_OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.EAST)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 3);
        colocar(level, puerta.above(), Blocks.DARK_OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.EAST)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
        DevilRpg.LOGGER.info("[Village] Aldea en {}: abierta la puerta de la taberna al almacen ({}), que es por"
                + " donde va el cocinero a por lena", center, puerta.toShortString());
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
                // Y LA CHIMENEA CON TIRO (28-sep-2026; lo pidió el jugador): el CENTRO del caño tiene que ser AIRE.
                // Una taberna con el pilar macizo (la de antes) falla esta prueba —a propósito, como la de la escalera
                // vieja— y el pueblo la rehace entera: el solar se despeja y lo de sus cofres se guarda en el almacén.
                boolean chimeneaConTiro = level.getBlockState(new BlockPos(base.getX() + TABERNA_HOGAR[0], nivel + 1,
                        base.getZ() + TABERNA_HOGAR[1] - 1)).isAir();
                return aPlomo && escaleraEnL && chimeneaConTiro;
            }
        }
        return false;
    }

    /**
     * Construye la <b>TABERNA</b>: dos plantas y tejado a dos aguas. Abajo, el <b>comedor</b>: la <b>cocina</b> del
     * cocinero, el <b>hogar</b> con su chimenea, la <b>barra</b> con las pipas, seis mesas con sus sillas, la
     * escalera y faroles por todas partes. Arriba, la <b>posada</b>: seis cuartos con sus camas alrededor de la
     * galería (para los viajeros y para la milicia cuando no está de guardia). La puerta da al <b>oeste</b>, a la
     * plaza, con porche y toldo.
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
     * oscuro, con la <b>banda de separación entre plantas</b> en su vuelta. Cubre TODO el vuelo menos el <b>hueco de
     * la escalera</b>, y remata el borde con las <b>cabezas de viga</b> del vuelo (los troncos que se ven bajo el
     * alero).
     * <p>
     * <b>LA BANDA</b> (lo pidió el jugador, que se la puso a mano en un lado y la quería de diseño en los cuatro):
     * <i>"estaría bien que la taberna tenga logs de separación entre un piso y otro… el log es más claro que el log de
     * cada pilar para que lo distingas… estaría bien que estuviera desde el diseño en todos los lados"</i>. En la
     * <b>vuelta del forjado</b> —la línea de los muros, que es lo que se ve desde fuera— va un tronco de <b>ROBLE
     * CLARO</b> en vez del tablón oscuro: una franja de una pieza que separa las dos plantas de un vistazo, y que no
     * se confunde con los <b>postes de roble oscuro</b> del entramado. Medido en su guardado: había puesto
     * {@code oak_log} a {@code y=124} en el muro sur (la vuelta del forjado) y el resto seguía en tablones.
     */
    private static void forjadoDeLaPosada(ServerLevel level, int bx, int bz, int nivel) {
        int y = nivel + TABERNA_PISO2 - 1;
        for (int dx = -TABERNA_VUELO; dx <= TABERNA_ANCHO + TABERNA_VUELO - 1; dx++) {
            for (int dz = -TABERNA_VUELO; dz <= TABERNA_FONDO + TABERNA_VUELO - 1; dz++) {
                if (esHuecoDeLaEscalera(dx, dz)) {
                    continue;
                }
                colocar(level, new BlockPos(bx + dx, y, bz + dz),
                        enLaVueltaDelForjado(dx, dz) ? Blocks.OAK_LOG.defaultBlockState()
                                : Blocks.DARK_OAK_PLANKS.defaultBlockState(), 3);
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
     * ¿Esa celda (relativa a la esquina de la taberna) está en la <b>vuelta del forjado</b>: la línea de los muros, que
     * es donde va la <b>banda de separación entre plantas</b> (troncos de roble claro)? Ver
     * {@code forjadoDeLaPosada} y {@code ponerLaBandaDeLaTaberna} (la migración 68).
     */
    private static boolean enLaVueltaDelForjado(int dx, int dz) {
        return dx >= 0 && dx < TABERNA_ANCHO && dz >= 0 && dz < TABERNA_FONDO
                && (dx == 0 || dx == TABERNA_ANCHO - 1 || dz == 0 || dz == TABERNA_FONDO - 1);
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
        // UN CAÑO HUECO, CON SU TIRO (28-sep-2026; lo pidió el jugador: *"haz la chimenea mas grande de tal manera que
        // en el centro haya un hueco donde pueda estar una fogata hasta abajo y tenga salida el humo hasta arriba"*).
        // ANTES esto era un PILAR MACIZO de ladrillo de la cota al remate: no había tiro, el humo no podía salir y en el
        // centro no cabía nada (medido en el guardado, `build/slice_mina.py`: una columna de `B` de arriba abajo).
        // Ahora es un tubo de 3x3 con el CENTRO DE AIRE desde la cota hasta el remate, y **la cara que da al hogar se
        // deja SIN tocar**: el hogar está justo al otro lado del muro, así que su aire y el tiro quedan conectados y el
        // humo de la fogata sube por dentro y sale por arriba. (Y ya no puede cegarlo el tapagujeros: ver la regla del
        // cielo en `VillageManager.esAgujeroDelSuelo`.)
        for (int y = nivel; y <= yTecho + 5; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
                        colocar(level, new BlockPos(bx + hx, y, bz + hz), Blocks.AIR.defaultBlockState(), 3);
                    } else if (dz != 1) { // la cara sur es la del hogar: se deja como está (ahí está la boca)
                        colocar(level, new BlockPos(bx + hx + dx, y, bz + hz + dz),
                                Blocks.BRICKS.defaultBlockState(), 3);
                    }
                }
            }
        }
        // El remate, ALREDEDOR del tiro (el centro se queda abierto para que el humo salga).
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    colocar(level, new BlockPos(bx + hx + dx, yTecho + 6, bz + hz + dz),
                            Blocks.BRICK_SLAB.defaultBlockState(), 3);
                }
            }
        }
    }

    /**
     * La <b>barra</b> del comedor, en el muro sur: el mostrador de tronco descortezado y, detrás, las <b>pipas</b> de
     * cerveza contra la pared.
     * <p>
     * <b>OJO con el bloque de las pipas</b>: NO se usa {@code BARREL}, porque en vanilla el <b>barril es el puesto de
     * trabajo del PESCADOR</b> y un aldeano sin oficio (una cría que crece, por ejemplo) lo reclamaría y se volvería
     * pescador — un oficio que este pueblo <b>todavía no tiene</b> (tendrá su edificio y su lago más adelante, y
     * entonces su barril va en la <b>pesquera</b>: es un puesto deliberado, I31). Las
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
     * Cambia ese bloque por otro <b>solo si sigue siendo el esperado</b> —la misma guardia que {@link #quitarSiEs},
     * para que una reparación no toque lo que puso el jugador— y lo deja en <b>una sola escritura</b>: sin el aire de
     * en medio que dejaría quitar y volver a poner. La usa el reparador de la <b>mesa de cartografía</b> de la
     * barraca (migración 60), que devuelve esa celda a la <b>paca del maniquí</b>.
     *
     * @return {@code true} si ha cambiado el bloque
     */
    private static boolean sustituirSiEs(ServerLevel level, BlockPos pos, Block esperado, BlockState nuevo) {
        if (!level.getBlockState(pos).is(esperado)) {
            return false;
        }
        colocar(level, pos, nuevo, 3);
        return true;
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
     * el que sube se golpea la cabeza contra el borde, y desde la migración 56 el hueco tiene que ser de
     * {@code DESVAN_HUECO_ALTO} celdas, que es lo que ocupa la subida de 0,6 que da el juego al ganar un escalón—. La
     * cara alta del último escalón queda a la cota del suelo del desván, así que al final del tramo se sale andando.
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
        //    Son TRES celdas por escalón (migración 56), no dos: con dos el techo queda a 2,0 de la huella y el juego
        //    no sube el escalón (levanta al jugador 0,6 y necesita 2,4 libres). Ver `DESVAN_HUECO_ALTO`.
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
     * sube, y {@code DESVAN_HUECO_ALTO} celdas de alto para que la subida de 0,6 del juego quepa). El segundo tramo
     * empieza justo donde acaba el primero, con un escalón más de altura y doblando al oeste.
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
     * El <b>hueco de subida</b> al desván (invariante I16): por encima de <b>cada</b> escalón se abren las
     * {@code DESVAN_HUECO_ALTO} celdas que necesita el que sube —el cuerpo y lo que el juego levanta de golpe al
     * ganar el escalón siguiente— en <b>las dos capas</b> del forjado, los tablones del techo de la posada
     * ({@code yTecho - 1}) y la placa de tejas del suelo del desván ({@code yTecho}). Solo se quitan esas dos capas:
     * el tejado de verdad, un tabique o el mobiliario <b>no</b> se tocan.
     * <p>
     * Son <b>tres</b> y no dos desde la migración 56, y ese número no es cuestión de gusto: ver
     * {@link #DESVAN_HUECO_ALTO}. Las dos celdas de la regla vieja dejaban el techo a 2,0 de la huella, la subida de
     * 0,5 del juego no cabía (necesita 2,4) y la escalera <b>no se subía</b>.
     * <p>
     * La tercera celda casi siempre <b>ya es aire</b> (por encima de las dos capas del forjado está el desván, que
     * {@link #desvanDeLaTaberna(ServerLevel, int, int, int)} acaba de vaciar), así que en la taberna nueva esto abre
     * solo las celdas que de verdad estorban: en el guardado del jugador, <b>dos</b>.
     *
     * @return cuántas celdas se abrieron (0 si el hueco ya estaba hecho: es idempotente)
     */
    private static int abrirElHuecoDelDesvan(ServerLevel level, int bx, int bz, int y1) {
        int abiertos = 0;
        for (int[] c : celdasDeLaEscaleraDelDesvan()) {
            for (int dy = 1; dy <= DESVAN_HUECO_ALTO; dy++) {
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
     * <b>Ensancha el hueco de subida al desván</b> de una taberna ya construida (migración 56). Las tabernas de pie
     * hasta ahora tienen el hueco de <b>dos</b> celdas encima de cada escalón, y con eso la escalera <b>no se sube</b>:
     * el jugador lo reportó (<i>"las escaleras para el 3er piso están bloqueadas por 2 bloques, dejando solo un
     * espacio de un bloque libre; se tienen que romper esos 2 bloques para que se pueda pasar"</i>) y en su guardado
     * (aldea 2, taberna en {@code 1438,1428}) los dos escalones que no se subían eran el 2º y el 3º, con el techo a
     * solo 2,0 de la huella (ver {@link #DESVAN_HUECO_ALTO}).
     * <p>
     * Es el mismo trabajo que hace el constructor ({@link #abrirElHuecoDelDesvan}, el <b>mismo</b> método, para que la
     * geometría no se pueda quedar desparejada), así que es <b>idempotente</b> (solo quita tablones y tejas de las
     * celdas del hueco, y solo si están ahí: lo que haya puesto el jugador no se toca) y <b>no rehace la taberna</b>:
     * no toca ni la despensa, ni las camas, ni los cuartos, ni los escalones.
     * <p>
     * <b>Y hace falta que corra</b> aunque el jugador ya se hubiera roto los bloques a mano: el <b>plano</b> de la
     * aldea (capturado cuando la taberna se construyó, invariante I8) tiene esas celdas como sólidas, así que el
     * <b>obrero las repone</b> y la escalera se vuelve a atascar. Al abrirlas con {@code colocar} entran en el plano
     * nuevo —el de después de la migración— y ya no vuelven. Medido en su guardado: la teja de
     * {@code (1443,130,1438)} está <b>repuesta</b> aunque el jugador la había roto.
     */
    public static void arreglarElHuecoDelDesvan(ServerLevel level, BlockPos center) {
        if (!tabernaConstruida(level, center)) {
            return;
        }
        int nivel = cotaDeLaPlaza(level, center);
        BlockPos base = baseDeLaTaberna(center);
        int abiertos = abrirElHuecoDelDesvan(level, base.getX(), base.getZ(), nivel + TABERNA_PISO2);
        if (abiertos > 0) {
            DevilRpg.LOGGER.info("[Village] Taberna de {}: hueco de subida al desvan ensanchado ({} celda(s) de las"
                    + " dos capas del forjado; el techo tiene que quedar a 3 bloques de la huella de cada escalon)",
                    center, abiertos);
        }
    }

    /**
     * El <b>mobiliario del desván</b>: lo justo para que sea una base del jugador (dos camas, mesa de trabajo, horno,
     * dos cofres —pegados, que se juntan en uno doble—, yunque, un par de faroles y un par de alfombras). <b>Nada de
     * puestos de trabajo de aldeano</b> (barril, caldero, ahumador, alto horno, mesa de herrería, muela, telar, atril,
     * compostero, cortapiedras, soporte de pociones ni campana): un aldeano sin oficio los reclamaría (I31). La cama, el
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
     * El <b>porche</b> de la puerta (al oeste, dando a la plaza): dos postes, el <b>toldo</b> que baja hacia fuera con
     * sus faroles colgados y un par de <b>pipas</b> al lado de la puerta. Es lo primero que se ve al llegar al pueblo.
     * <p>
     * El <b>toldo</b> son <b>dos alturas</b> que bajan hacia fuera: la de <b>dentro</b> (a la altura del forjado de la
     * posada) y la de <b>fuera</b> ({@code bx-3}, encima de los postes) un bloque más baja. Va
     * <b>entero</b>, de punta a punta ({@code pz-3..pz+3}) y <b>hasta la pared</b>: la fila de dentro llega a
     * {@code bx-1}, que es la celda <b>pegada</b> al muro de la taberna ({@code bx}). No siempre fue así —el porche
     * salía solo hasta {@code bx-2} y quedaba una columna de <b>aire</b> entre el toldo y la pared, o sea un techito
     * <b>suelto</b> que no conectaba con la casa (lo reportó el jugador: <i>"el techito que está en la entrada de la
     * taberna está incompleto porque no conecta con la pared"</i>; medido en su guardado: las <b>7 de 7</b> celdas de
     * {@code bx-1} vacías, con la pared sólida detrás)—.
     * <p>
     * Y la celda pegada al muro va con un <b>BLOQUE NORMAL</b> (tablón), no con otro escalón: dos escalones seguidos a
     * la misma altura se ven como un doble peldaño raro contra la pared (lo corrigió el jugador: <i>"se necesita poner
     * un bloque normal y luego ahora sí el bloque de escalera bien alineado"</i>). Así el escalón de al lado apoya su
     * cara alta contra el tablón del muro y el alero se lee como un alero: <b>sube hacia la casa y baja hacia fuera</b>
     * (perfil: tablón en {@code bx-1}, escalón en {@code bx-2}, escalón un bloque más bajo en {@code bx-3}).
     * <p>
     * <b>Los faroles van POR DEBAJO del toldo, nunca en la fila de los escalones.</b> No siempre fue así, y el jugador
     * lo vio: <i>"el pórtico está cortado con un espacio, ¿por qué? debería estar completo"</i>. Los dos faroles de las
     * puntas se colocaban <b>en la misma celda</b> que el escalón del alero (encima del poste) y lo
     * <b>sustituían</b> —el plano guarda el ÚLTIMO bloque de cada celda ({@link GrabadoraDePlano}), así que quedaba
     * apuntado el farol—, de modo que al alero le faltaba un escalón en cada punta y se veía cortado. Y encima un
     * farol <b>colgado</b> ahí no tenía <b>nada encima</b> de lo que colgar: estaba <b>flotando</b> (invariante I14,
     * la misma prueba que hace el juego, {@code Block.canSupportCenter}).
     * <p>
     * Por eso el toldo lleva un <b>soffito de tablones</b> ({@code bx-2}, una capa por debajo de la fila de dentro) que
     * también va de punta a punta: es un bloque <b>sólido</b> y de él <b>cuelgan</b> los tres faroles (uno en cada
     * punta, sobre los postes, y uno en el centro, que es la vertical de la puerta). Los pone {@code colgar}, que es
     * el ayudante de los faroles que van colgados. La repara en las tabernas ya construidas
     * {@link #arreglarPorcheDeLaTaberna(ServerLevel, BlockPos)} (migración 55), celda por celda.
     */
    private static void porcheDeLaTaberna(ServerLevel level, int bx, int bz, int nivel) {
        int pz = TABERNA_PUERTA;
        // 1) LOS DOS POSTES, en las puntas del toldo.
        for (int dz : new int[]{pz - 3, pz + 3}) {
            for (int k = 0; k <= 2; k++) {
                colocar(level, new BlockPos(bx - 3, nivel + k, bz + dz), Blocks.DARK_OAK_FENCE.defaultBlockState(), 3);
            }
        }
        // 2) EL TOLDO: las filas de escalones, ENTERAS de punta a punta y LLEGANDO A LA PARED. Nada más se pone en
        //    esta fila: una celda de aquí es un escalón del alero y, si se ocupa con otra cosa (un farol), el toldo
        //    se ve CORTADO.
        //    OJO CON LA CELDA DE LA PARED (`bx - 1`): el porche salía a `bx - 2` y la pared está en `bx`, así que
        //    quedaba una columna de AIRE entre el toldo y la taberna y el techito se veía SUELTO, sin conectar (lo
        //    reportó el jugador: *"el techito que está en la entrada de la taberna está incompleto porque no conecta
        //    con la pared"*; medido en su guardado: 7 de 7 celdas de `bx-1` vacías, con la pared sólida detrás).
        //    El alero llega hasta `bx - 1`, que es la celda PEGADA al muro.
        for (int dz = pz - 3; dz <= pz + 3; dz++) {
            // LA CELDA PEGADA AL MURO VA CON UN BLOQUE NORMAL, no con otro escalón: dos escalones seguidos a la misma
            // altura se veían como un doble peldaño raro contra la pared (lo reportó el jugador: *"se necesita poner un
            // bloque normal y luego ahora sí el bloque de escalera bien alineado para que quede bien"*). Con el tablón
            // sólido pegado al muro, el escalón de al lado apoya su cara alta contra él y el alero se lee como un
            // alero: sube hacia la casa y baja hacia fuera.
            colocar(level, new BlockPos(bx - 1, nivel + TABERNA_PISO2 - 1, bz + dz),
                    Blocks.DARK_OAK_PLANKS.defaultBlockState(), 3);
            // Y EL ESCALÓN, ALINEADO contra ese bloque (cara alta hacia la casa).
            colocar(level, new BlockPos(bx - 2, nivel + TABERNA_PISO2 - 1, bz + dz), escalonDelToldo(), 3);
            colocar(level, new BlockPos(bx - 3, nivel + TABERNA_PISO2 - 2, bz + dz), escalonDelToldo(), 3);
        }
        // 3) EL SOFFITO: el tablón que cierra el toldo por debajo (una capa por debajo de la fila de dentro), entero
        //    y también hasta la pared: es el TECHO del porche (y el APOYO de los faroles, I14: un farol colgado
        //    necesita un bloque SÓLIDO encima).
        for (int dz = pz - 3; dz <= pz + 3; dz++) {
            colocar(level, new BlockPos(bx - 1, nivel + TABERNA_PISO2 - 2, bz + dz),
                    Blocks.DARK_OAK_PLANKS.defaultBlockState(), 3);
            colocar(level, new BlockPos(bx - 2, nivel + TABERNA_PISO2 - 2, bz + dz),
                    Blocks.DARK_OAK_PLANKS.defaultBlockState(), 3);
        }
        // 4) LOS TRES FAROLES, COLGADOS del soffito: uno en cada punta (encima de los postes) y uno en el centro.
        for (int dz : new int[]{pz - 3, pz, pz + 3}) {
            colgar(level, new BlockPos(bx - 2, nivel + TABERNA_PISO2 - 3, bz + dz));
        }
        // 5) LAS PIPAS, a los dos lados de la puerta y pegadas al muro.
        colocar(level, new BlockPos(bx - 2, nivel, bz + pz - 2), Blocks.OAK_WOOD.defaultBlockState(), 3);
        colocar(level, new BlockPos(bx - 2, nivel, bz + pz + 2), Blocks.OAK_WOOD.defaultBlockState(), 3);
    }

    /**
     * El <b>escalón del toldo</b> del porche: mira al <b>este</b> (la cara alta —por donde se sube— pegada al muro),
     * que es lo que hace que el alero baje hacia fuera. Lo usan el constructor y su reparador, para que no se puedan
     * quedar con dos formas distintas.
     */
    private static BlockState escalonDelToldo() {
        return Blocks.DARK_OAK_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.EAST).setValue(StairBlock.HALF, Half.BOTTOM);
    }

    /**
     * <b>Repara el porche</b> de una taberna ya construida (migración 55). Las tabernas de pie hasta ahora tienen el
     * <b>alero del toldo cortado</b>: los dos faroles de las puntas se colocaban en la celda de su escalón y lo
     * sustituían —y el plano guarda el último bloque de cada celda—, así que faltaba un escalón en cada punta y los
     * dos faroles colgaban <b>del aire</b> (I14). Lo vio el jugador: <i>"el pórtico está cortado con un espacio"</i>.
     * <p>
     * Esto deja las <b>mismas celdas</b> que {@link #porcheDeLaTaberna}, una por una y <b>solo las del porche</b>:
     * <ul>
     *   <li>los dos faroles <b>flotantes</b> de las puntas se retiran, y <b>solo si siguen siendo faroles</b>
     *       ({@code quitarSiEs}: lo que haya puesto el jugador se queda);</li>
     *   <li>su celda se cierra con el <b>escalón</b> que le toca, si quedó vacía;</li>
     *   <li>el <b>soffito de tablones</b> se completa de punta a punta (solo donde esté vacío);</li>
     *   <li>el alero <b>llega hasta la PARED</b> (migración 63): se añade la fila de {@code bx-1} —el escalón a la
     *       altura de la de dentro y su tablón de soffito debajo—, que es lo que cierra el hueco de aire que dejaba al
     *       techito <b>suelto</b> del edificio (solo donde esté vacío: es aditivo);</li>
     *   <li>y los dos faroles de las puntas se <b>cuelgan</b> del soffito, con la misma prueba que hace el juego para
     *       aceptar un farol colgado ({@code Block.canSupportCenter}, I14), así que no puede volver a quedar uno en el
     *       aire.</li>
     * </ul>
     * Es <b>idempotente</b> (en una taberna ya reparada —o construida con el constructor nuevo— no cambia nada) y
     * <b>no rehace la taberna</b>: no toca ni la despensa, ni las camas, ni los cuartos.
     */
    public static void arreglarPorcheDeLaTaberna(ServerLevel level, BlockPos center) {
        if (!tabernaConstruida(level, center)) {
            return;
        }
        int nivel = cotaDeLaPlaza(level, center);
        BlockPos base = baseDeLaTaberna(center);
        int bx = base.getX();
        int bz = base.getZ();
        int pz = TABERNA_PUERTA;
        int yToldo = nivel + TABERNA_PISO2 - 2;   // la fila de FUERA del toldo (y la del soffito de tablones)
        int yDentro = nivel + TABERNA_PISO2 - 1;  // la fila de DENTRO del toldo (y la celda pegada a la pared)
        int yFarol = nivel + TABERNA_PISO2 - 3;   // los faroles, una capa por debajo del soffito
        int cambios = 0;
        for (int dz : new int[]{pz - 3, pz + 3}) {
            // 1) EL FAROL FLOTANTE de la punta, fuera (solo si es un farol: lo del jugador no se toca).
            cambios += quitarSiEs(level, bx - 3, yToldo, bz + dz, Blocks.LANTERN);
            // 2) Y EL ESCALÓN que se comía, en su sitio: la fila de fuera tiene que llegar a las dos puntas.
            if (colocarSiEstaVacio(level, new BlockPos(bx - 3, yToldo, bz + dz), escalonDelToldo())) {
                cambios++;
            }
        }
        // 3) EL TOLDO HASTA LA PARED Y CON BLOQUE NORMAL EN LA CELDA DEL MURO (migraciones 63 y 66). La 63 (lo
        //    reportó el jugador: *"el techito... no conecta con la pared"*) añadió la fila que falta (`bx-1`, pegada al
        //    muro) a la altura de la fila de dentro, con su tablón de soffito debajo. La 66 corrige CÓMO se veía: allí
        //    había puesto un ESCALÓN y dos escalones seguidos a la misma altura quedan como un doble peldaño raro
        //    (*"se necesita poner un bloque normal y luego ahora sí el bloque de escalera bien alineado"*), así que esa
        //    celda pasa a ser un TABLÓN sólido (y solo si sigue siendo el escalón del toldo: lo del jugador se queda).
        for (int dz = pz - 3; dz <= pz + 3; dz++) {
            BlockPos pegado = new BlockPos(bx - 1, yDentro, bz + dz);
            if (level.getBlockState(pegado).equals(escalonDelToldo())) {
                colocar(level, pegado, Blocks.DARK_OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
                cambios++;
            } else if (colocarSiEstaVacio(level, pegado, Blocks.DARK_OAK_PLANKS.defaultBlockState())) {
                cambios++;
            }
            if (colocarSiEstaVacio(level, new BlockPos(bx - 1, yToldo, bz + dz),
                    Blocks.DARK_OAK_PLANKS.defaultBlockState())) {
                cambios++;
            }
        }
        // 4) EL SOFFITO, de punta a punta (el apoyo de los faroles; en las tabernas viejas solo estaba en el centro).
        for (int dz = pz - 3; dz <= pz + 3; dz++) {
            if (colocarSiEstaVacio(level, new BlockPos(bx - 2, yToldo, bz + dz),
                    Blocks.DARK_OAK_PLANKS.defaultBlockState())) {
                cambios++;
            }
        }
        // 5) Y LOS FAROLES DE LAS PUNTAS, COLGADOS del soffito (nunca en la fila de los escalones).
        for (int dz : new int[]{pz - 3, pz + 3}) {
            BlockPos farol = new BlockPos(bx - 2, yFarol, bz + dz);
            if (!level.getBlockState(farol).isAir()) {
                continue;   // ya está (o lo puso el jugador)
            }
            if (!Block.canSupportCenter(level, farol.above(), Direction.DOWN)) {
                continue;   // sin soffito encima no se cuelga nada (I14)
            }
            colgar(level, farol);
            cambios++;
        }
        if (cambios > 0) {
            DevilRpg.LOGGER.info("[Village] Taberna de {}: porche reparado ({} cambio(s) en sus celdas: el alero del"
                    + " toldo entero, hasta la pared, y los faroles de las puntas colgados del soffito)",
                    center, cambios);
        }
    }

    /** Un farol <b>colgado</b> (de un bloque sólido que tiene encima). */
    private static void colgar(ServerLevel level, BlockPos pos) {
        colocar(level, pos, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 3);
    }

    /**
     * <b>La banda de separación entre plantas de la taberna</b> (lo pidió el jugador): la <b>vuelta del forjado</b> de
     * la posada, que es la línea de los muros, se remata con <b>troncos de roble CLARO</b> en vez de los tablones
     * oscuros. Se ve de un vistazo dónde acaba el comedor y empieza la posada, y el tronco claro no se confunde con los
     * <b>postes de roble oscuro</b> del entramado: *"el log es más claro que el log de cada pilar para que lo
     * distingas"*.
     * <p>
     * La construye {@code forjadoDeLaPosada} en la taberna nueva; esto es el <b>reparador de la migración 68</b> para
     * las ya construidas: cambia <b>solo</b> los tablones del diseño ({@code DARK_OAK_PLANKS}) de esa vuelta, así que
     * es <b>idempotente</b> y lo que el jugador haya puesto ahí (en su partida ya había puesto él la banda del muro
     * sur, en {@code oak_log}) se queda como está. Va en el latido, como los demás reparadores de la taberna.
     */
    public static void ponerLaBandaDeLaTaberna(ServerLevel level, BlockPos center) {
        if (!tabernaConstruida(level, center)) {
            return;
        }
        int nivel = cotaDeLaPlaza(level, center);
        BlockPos base = baseDeLaTaberna(center);
        int y = nivel + TABERNA_PISO2 - 1;
        int puestas = 0;
        for (int dx = 0; dx < TABERNA_ANCHO; dx++) {
            for (int dz = 0; dz < TABERNA_FONDO; dz++) {
                if (!enLaVueltaDelForjado(dx, dz)) {
                    continue;
                }
                BlockPos p = new BlockPos(base.getX() + dx, y, base.getZ() + dz);
                if (!level.getBlockState(p).is(Blocks.DARK_OAK_PLANKS)) {
                    continue; // ya es la banda (o es del jugador, o es el hueco de la escalera): no se toca
                }
                // lint:ok I9 porque es una REPARACION idempotente de una vuelta de celdas (la banda de separacion) y se
                // llama desde el latido: no rehace nada, asi que no necesita migracion propia.
                colocar(level, p, Blocks.OAK_LOG.defaultBlockState(), 3);
                puestas++;
            }
        }
        if (puestas > 0) {
            DevilRpg.LOGGER.info("[Village] Taberna de {}: banda de separacion entre plantas puesta ({} tronco(s) de"
                    + " roble en la vuelta del forjado)", center, puestas);
        }
    }

    /**
     * Las <b>luces</b> de la taberna: faroles colgados del forjado en el comedor y del techo en la galería y en los
     * cuartos. Los del <b>porche</b> no están aquí: los cuelga del soffito del toldo su constructor
     * ({@link #porcheDeLaTaberna}), que es quien conoce esa geometría. Una taberna a oscuras es una taberna con
     * bichos dentro.
     * <p>
     * <b>OJO CON LA ESCALERA</b>: el farol de {@code {4, 11}} colgaba <b>justo encima del primer escalón</b> (el pie de
     * la escalera está en {@code bx + TABERNA_ESCALERA_PIE_DX, nivel, bz + TABERNA_ESCALERA_MESETA_Z}) y estorbaba
     * para subir: lo vio el jugador —*"hay que quitar esta lámpara que está justo arriba de las primeras escaleras de
     * la planta baja porque estorba al querer subir por ahí"*—. Un farol tiene caja de colisión, así que el que sube
     * se da con él. Se quita de la lista (el comedor sigue iluminado: el de {@code {4, 7}} está cuatro bloques al
     * lado) y {@link #quitarElFarolDeLaEscalera} lo retira en las tabernas ya construidas.
     */
    private static void lucesDeLaTaberna(ServerLevel level, int bx, int bz, int nivel, int y1, int yTecho) {
        int[][] comedor = {{2, 3}, {5, 3}, {9, 3}, {13, 3}, {16, 3}, {4, 7}, {9, 7}, {13, 7}, {16, 8},
                {9, 12}, {14, 12}, {16, 12}};
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
     * <b>Quita el farol que colgaba encima del primer escalón de la taberna</b> (el del comedor en {@code {4, 11}}), en
     * las tabernas ya construidas. Lo pidió el jugador: *"hay que quitar esta lámpara que está justo arriba de las
     * primeras escaleras de la planta baja porque estorba al querer subir por ahí"*. Un farol tiene caja de colisión,
     * así que el que sube se daba con él.
     * <p>
     * Es <b>idempotente</b> y <b>solo toca ese farol</b> (si en esa celda hay otra cosa —lo que haya puesto el
     * jugador— no se toca). Se llama desde el latido, así que vale también para las aldeas ya construidas.
     *
     * @return la celda del farol que ha quitado (o {@code null} si no había nada que quitar), para que el latido la
     *         saque también del <b>plano</b>: si el plano la sigue pidiendo, el obrero lo repone y esto lo vuelve a
     *         quitar, en un tira y afloja cada 10 s (I8/I6).
     */
    @Nullable
    public static BlockPos quitarElFarolDeLaEscalera(ServerLevel level, BlockPos center) {
        if (!tabernaConstruida(level, center)) {
            return null;
        }
        int nivel = cotaDeLaPlaza(level, center);
        BlockPos base = baseDeLaTaberna(center);
        BlockPos farol = new BlockPos(base.getX() + TABERNA_ESCALERA_PIE_DX, nivel + TABERNA_PISO2 - 2,
                base.getZ() + TABERNA_ESCALERA_MESETA_Z);
        if (level.getBlockState(farol).is(Blocks.LANTERN)) {
            // lint:ok I9 porque es una REPARACION idempotente de una celda (quita un farol que estorba para subir la
            // escalera) y se llama desde el latido: no rehace nada, asi que no necesita migracion.
            colocar(level, farol, Blocks.AIR.defaultBlockState(), 3);
            DevilRpg.LOGGER.info("[Village] Taberna de {}: quitado el farol de encima del primer escalon ({})",
                    center, farol.toShortString());
            return farol;
        }
        return null;
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
