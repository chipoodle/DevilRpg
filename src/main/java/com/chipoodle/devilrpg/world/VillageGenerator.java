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
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.ChestType;
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

    /** Radio de la valla (un 30% más grande que antes). */
    public static final int FENCE_RADIUS = 29;

    /** Esquinas de las parcelas de la granja (relativas al centro) y tamaño de cada parcela. */
    private static final int[][] FARM_PLOTS = {{-16, 8}, {8, 6}};
    private static final int PLOT_WIDTH = 9;
    private static final int PLOT_DEPTH = 5;
    /** Fila de la acequia dentro de la parcela (la del medio). */
    private static final int PLOT_WATER_ROW = PLOT_DEPTH / 2;
    /** Bloques de <b>terraza</b> (patio llano) que se allanan alrededor de una construcción. */
    private static final int MARGEN_TERRAZA = 2;
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
        // Limpiar hasta cubrir el talud exterior (que rodea el área nivelada).
        clearVegetation(level, center, LEVEL_RADIUS + SLOPE_WIDTH);
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
        // propio juego (ver placeVanillaHouse), no cabañas procedurales.
        BlockPos h0 = center.offset(-17, 0, -3);
        BlockPos h1 = center.offset(16, 0, -4);
        BlockPos h2 = center.offset(-3, 0, 17);

        // CASAS PRIMERO: hay que colocarlas para saber dónde quedó cada puerta (cada plantilla la trae donde
        // quiere) y que los caminos lleguen de verdad a ella. La última es la "grande" (con cama extra), y en el
        // sitio de la vieja torre va la IGLESIA del juego.
        RandomSource casas = RandomSource.create(center.asLong());
        BlockPos[] puertas = new BlockPos[5];
        puertas[0] = placeVanillaHouse(level, h0, casaAleatoria(casas), nivelVilla);
        puertas[1] = placeVanillaHouse(level, h1, casaAleatoria(casas), nivelVilla);
        puertas[2] = placeVanillaHouse(level, h2, casaAleatoria(casas), nivelVilla);
        puertas[3] = placeVanillaHouse(level, center.offset(10, 0, -18), casaGrandeAleatoria(casas), nivelVilla);
        puertas[4] = placeVanillaHouse(level, baseDeIglesia(center), iglesiaAleatoria(casas), nivelVilla);

        // Caminos DESPUÉS, del centro a la puerta de cada construcción (ya se sabe dónde está).
        paths(level, center, puertas);

        // Kiosco de la plaza: plataforma con 4 salidas, la campana ARRIBA y el cofre doble de la despensa dentro.
        kiosco(level, center, nivelVilla);

        // Granja: da trabajo al aldeano granjero y produce la comida que come la aldea (Iteración 3). Se le pasa
        // LA COTA YA CALCULADA: si la recalculara aquí, la muestra del terreno incluiría las casas y la iglesia
        // recién colocadas (`groundY` sobre un tejado devuelve el tejado) y el nivelado subiría la aldea un
        // bloque: casas hundidas, zanjas y el muro enterrado (el bug que reportó el jugador).
        farm(level, center, nivelVilla);

        // Remesa inicial de la despensa (semillas, abono y un par de panes): el kiosco ya tiene el cofre doble.
        VillagePantry.remesaInicial(VillagePantry.despensa(level, center));

        // Aldeanos frente a las casas, y el golem que protege la aldea.
        spawnVillagers(level, center);

        // Faroles con poste distribuidos por la aldea (evitan spawn de zombies con la mecánica vanilla).
        torches(level, center);

        // (La vieja torre de vigilancia procedural ya no se pone: en su sitio va la IGLESIA del juego, que se
        // coloca arriba con las demás construcciones y trae campanario.)

        fence(level, center);

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
                BlockState state = entrada.getValue();
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
     * Esquinas de las dos parcelas de la granja de esa aldea. Lo usan los aldeanos que trabajan la tierra
     * ({@code VillagerFarmGoal}) para saber dónde plantar y cosechar, y el obrero para no poner faroles encima.
     */
    public static BlockPos[] parcelasDe(BlockPos center) {
        BlockPos[] parcelas = new BlockPos[FARM_PLOTS.length];
        for (int i = 0; i < FARM_PLOTS.length; i++) {
            parcelas[i] = center.offset(FARM_PLOTS[i][0], 0, FARM_PLOTS[i][1]);
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
        if (VillagePantry.despensa(level, center) != null) {
            return; // el kiosco (con su cofre) ya está
        }
        kiosco(level, center, nivel);
        VillagePantry.remesaInicial(VillagePantry.despensa(level, center));
        DevilRpg.LOGGER.info("[Village] Aldea en {}: kiosco de la plaza y despensa colocados a la cota {}", center, nivel);
    }

    /**
     * Asegura el <b>almacén</b> de la aldea: un cobertizo de piedra con <b>cofre doble</b> donde el constructor (que
     * también es recolector) deja lo que recoge por el pueblo. <b>Crece solo</b>: cuando sus cofres se llenan, se
     * añade otro cofre doble en el siguiente hueco (hasta {@code VillageStorage.MAX_COFRES} dobles).
     */
    public static void asegurarAlmacen(ServerLevel level, BlockPos center) {
        int nivel = cotaDeLaPlaza(level, center);
        if (nivel <= level.getMinBuildHeight() + 1) {
            return;
        }
        BlockPos c = VillageStorage.centro(center);
        if (!(level.getBlockState(new BlockPos(c.getX(), nivel, c.getZ())).is(Blocks.STONE_BRICKS))) {
            // Cobertizo: suelo 5x5, cuatro postes de tronco y tejado de tablones.
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    colocar(level, new BlockPos(c.getX() + dx, nivel, c.getZ() + dz),
                            Blocks.STONE_BRICKS.defaultBlockState(), 3);
                }
            }
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    for (int i = 1; i <= 3; i++) {
                        colocar(level, new BlockPos(c.getX() + sx * 2, nivel + i, c.getZ() + sz * 2),
                                Blocks.OAK_LOG.defaultBlockState(), 3);
                    }
                }
            }
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
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

    /** Posiciones base de las casas de la aldea, relativas al centro (las mismas que usa {@link #generate}). */
    public static BlockPos[] basesDeCasas(BlockPos center) {
        return new BlockPos[]{
                center.offset(-17, 0, -3),
                center.offset(16, 0, -4),
                center.offset(-3, 0, 17),
                // La cuarta casa (la "grande", con cama extra) va al norte, en el hueco libre entre la torre y la
                // primera casa, sin pisar la granja ni los caminos.
                center.offset(10, 0, -18),
        };
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
        int nivelVilla = prepararTerreno(level, center);
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

    /** Dónde va la iglesia de la aldea (el mismo sitio que ocupaba la torre de vigilancia). */
    private static BlockPos baseDeIglesia(BlockPos center) {
        return center.offset(-9, 0, -20);
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

    /** Lado del kiosco de la plaza (radio): 2 -> plataforma de 5x5. */
    private static final int KIOSCO_RADIO = 2;
    /** Altura de los cuatro postes del kiosco sobre la plataforma. */
    private static final int KIOSCO_POSTE = 3;

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
        // El cofre DOBLE de la despensa, sobre la plataforma. Las dos mitades se marcan LEFT/RIGHT a mano: al
        // colocarlas con setBlock no pasa por la colocación de vanilla y sin esto quedarían dos cofres sueltos.
        colocar(level, new BlockPos(cx, nivel + 1, cz + 1), cofre(ChestType.LEFT), 3);
        colocar(level, new BlockPos(cx + 1, nivel + 1, cz + 1), cofre(ChestType.RIGHT), 3);
    }

    /** Cofre mirando al sur; {@code tipo} marca la mitad (LEFT/RIGHT) para formar un cofre doble. */
    private static BlockState cofre(ChestType tipo) {
        return Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH)
                .setValue(ChestBlock.TYPE, tipo);
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
                {0, 0, -15},
                {12, 0, 12},
                {-13, 0, -6},
                {5, 0, 14},
                {0, 0, 15},
                {-19, 0, 8},
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
        // Cama de respaldo en las CASAS que no traigan ninguna (los aldeanos necesitan cama para criar). A la
        // iglesia no se le pone: no es un dormitorio. OJO con la altura: la cama va en la capa de ARRIBA del
        // suelo (dy = 1), que es el nivel por el que se anda dentro de la casa.
        if (camas == 0 && !esIglesia(id)) {
            bed(level, origen.offset(tam.getX() / 2, 1, tam.getZ() / 2));
        }
        // Las casas GRANDES llevan una cama extra: en vanilla hace falta una cama libre por cría, así que con 4
        // camas la aldea puede crecer hasta 4 aldeanos.
        if (esCasaGrande(id)) {
            camaExtra(level, origen, tam);
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
     */
    private static boolean esTerrenoNatural(BlockState state) {
        if (!state.getFluidState().isEmpty() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) {
            return true;
        }
        Block block = state.getBlock();
        if (block instanceof CropBlock || block instanceof BushBlock) {
            return true;
        }
        return block == Blocks.GRASS_BLOCK || block == Blocks.DIRT || block == Blocks.COARSE_DIRT
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
        int nivelCamino = groundY(level, from.getX(), from.getZ()) - 1; // el patio, a la altura del centro
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
                for (int y = baseY; y < g; y++) {
                    if (esTerrenoRecortable(level.getBlockState(columna.atY(y)))) {
                        colocar(level, columna.atY(y), Blocks.AIR.defaultBlockState(), 3);
                    }
                }
                ponerCesped(level, columna.getX(), columna.getZ(), baseY);
            }
        }
        // Talud exterior: una pendiente escalonada en el borde para que la aldea parezca una MESETA natural
        // (como el terreno vanilla) en vez de un cubo de paredes verticales.
        addOuterSlope(level, center, radius, baseY);
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
    private static void addOuterSlope(ServerLevel level, BlockPos center, int innerRadius, int baseY) {
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
                // Recortar si el terreno natural sobresale por encima del talud (nunca troncos ni construcciones).
                for (int y = targetY; y < g; y++) {
                    if (esTerrenoRecortable(level.getBlockState(new BlockPos(px, y, pz)))) {
                        colocar(level, new BlockPos(px, y, pz), Blocks.AIR.defaultBlockState(), 3);
                    }
                }
                // Capa superficial del talud (cesped), salvo que sea agua.
                BlockPos surface = new BlockPos(px, targetY - 1, pz);
                if (!level.getBlockState(surface).is(Blocks.WATER)) {
                    colocar(level, surface, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                }
            }
        }
    }

    /**
     * Las celdas del <b>anillo del muro</b>, en orden angular y sin huecos: se muestrea el círculo y se rellenan
     * los saltos entre muestras con pasos cardinales. Lo usan {@link #fence} (construirlo) y
     * {@link #rehacerMuro} (limpiar los restos antes de reconstruirlo).
     */
    private static List<BlockPos> anilloDelMuro(BlockPos center) {
        int r = FENCE_RADIUS;
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
        List<BlockPos> ring = anilloDelMuro(center);
        List<Integer> heights = new ArrayList<>();
        for (BlockPos p : ring) {
            heights.add(groundY(level, p.getX(), p.getZ()));
        }
        Collections.sort(heights);
        int baseY = heights.get(heights.size() / 2);
        // Se limpia la franja del muro (de la superficie hacia arriba) quitando SOLO lo que no es terreno: los
        // restos del muro viejo. Nunca se toca el suelo.
        for (BlockPos p : ring) {
            for (int y = baseY - 1; y <= baseY + 5; y++) {
                BlockState state = level.getBlockState(new BlockPos(p.getX(), y, p.getZ()));
                if (state.isAir() || esTerrenoNatural(state)) {
                    continue;
                }
                level.setBlock(new BlockPos(p.getX(), y, p.getZ()), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        fence(level, center);
        DevilRpg.LOGGER.info("[Village] Aldea en {}: muro reconstruido", center);
    }

    /**
     * Muro de madera y piedra alrededor de la aldea, más realista: logs horizontales (2 bloques de alto)
     * con columnas verticales de cobblestone cada cierta distancia, y 4 entradas de cobblestone en los
     * puntos cardinales (norte/sur/este/oeste).
     */
    private static void fence(ServerLevel level, BlockPos center) {
        int r = FENCE_RADIUS;
        List<BlockPos> ring = anilloDelMuro(center);

        // Altura uniforme (mediana) para que el muro no quede escalonado en terreno ondulado.
        List<Integer> heights = new ArrayList<>();
        for (BlockPos p : ring) {
            heights.add(groundY(level, p.getX(), p.getZ()));
        }
        Collections.sort(heights);
        int baseY = heights.get(heights.size() / 2);
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
     * Limpia TODA la vegetación (árboles, follaje, flores, pasto, bambú, cactus, cañas, etc.) dentro del
     * radio. Barre la columna completa desde el bloque más alto (heightmap) hacia abajo, para que cubra
     * también los tallos/árboles que nacen en el suelo y no solo la punta (el bambú es un bloque sólido,
     * así que no bastaba con usar la "superficie" del suelo). Se llama ANTES de generar la aldea.
     */
    private static void clearVegetation(ServerLevel level, BlockPos center, int radius) {
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                int topY = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z)).getY();
                // Desde el bloque más alto de la columna hacia abajo ~60 bloques: cubre árboles, bambú y
                // cualquier planta que nazca en el suelo, aunque su base esté varios bloques por debajo.
                for (int y = topY; y > topY - 60 && y >= level.getMinBuildHeight(); y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (isVegetation(state)) {
                        colocar(level, pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
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
        BlockState foot = Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT);
        BlockState head = Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD);
        colocar(level, footPos, foot, 3);
        colocar(level, footPos.relative(Direction.SOUTH), head, 3);
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
     * Los cuatro sitios fijos de aldeano de la aldea (uno por profesión), relativos al centro. Son 4 desde que
     * hay 4 casas (y 4 camas): así la cuarta casa tiene su dueño. El <b>herrero</b> (TOOLSMITH) es el que en el
     * futuro trabajará los materiales de la aldea.
     */
    private static final BlockPos[] VILLAGER_SPOTS = {
            new BlockPos(-8, 0, -8), new BlockPos(9, 0, -8), new BlockPos(-2, 0, -11),
            new BlockPos(14, 0, -8)
    };
    private static final VillagerProfession[] VILLAGER_SPECIALTIES = {
            VillagerProfession.FARMER, VillagerProfession.WEAPONSMITH, VillagerProfession.CLERIC,
            VillagerProfession.TOOLSMITH
    };

    /** Vuelve a poner los aldeanos y el golem de una aldea ya construida (ver {@code VillageManager}). */
    public static void spawnVillagers(ServerLevel level, BlockPos center) {
        for (int slot = 0; slot < VILLAGER_SPOTS.length; slot++) {
            spawnOneVillager(level, center, slot, false);
        }
        // El golem SOLO si no hay ya uno: al repoblar una aldea cuyo golem sobrevivió, antes aparecía un
        // segundo golem (bug visto en juego).
        if (level.getEntitiesOfClass(IronGolem.class, new AABB(center).inflate(48.0D)).isEmpty()) {
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
                    BlockState state = level.getBlockState(pos);
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
     * <b>Granja</b> de la aldea: dos parcelas con su acequia, los cultivos ya crecidos y un compostador (el
     * puesto de trabajo del granjero). Es lo que hace que el aldeano granjero tenga faena y que la aldea
     * produzca la <b>comida</b> que luego se come (ver {@code VillageManager}).
     */
    public static void farm(ServerLevel level, BlockPos center) {
        farm(level, center, prepararTerreno(level, center));
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
    private static void plot(ServerLevel level, BlockPos corner, int nivel) {
        Block[] plants = {Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS};
        // 1) La parcela se nivela a LA COTA DE LA ALDEA (la que nos pasan): agua y tierra de cultivo a la misma
        // altura que el resto del pueblo, así ni se seca ni queda en un hoyo.
        int base = nivelarHuella(level, corner, PLOT_WIDTH, PLOT_DEPTH, nivel);
        for (int dx = 0; dx < PLOT_WIDTH; dx++) {
            for (int dz = 0; dz < PLOT_DEPTH; dz++) {
                int x = corner.getX() + dx;
                int z = corner.getZ() + dz;
                // 2) Solar LIMPIO: se quita lo que hubiera por encima del suelo (cultivos, restos de una versión
                // anterior del trazado...). Sin esto quedaban capas viejas y dos composteadores apilados.
                for (int y = base; y <= base + 3; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!level.getBlockState(p).isAir()) {
                        colocar(level, p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
                if (dz == PLOT_WATER_ROW) {
                    // Acequia central: el agua va a ras de la tierra de cultivo y riega las cuatro filas.
                    colocar(level, new BlockPos(x, base - 1, z), Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
                    continue;
                }
                colocar(level, new BlockPos(x, base - 1, z), Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_ALL);
                BlockState crop = plants[dx % plants.length].defaultBlockState();
                if (crop.getBlock() instanceof CropBlock cropBlock) {
                    // Cada cultivo tiene su edad máxima (el trigo 7, la remolacha 3): se pregunta, no se asume.
                    crop = crop.setValue(CropBlock.AGE, cropBlock.getMaxAge());
                }
                colocar(level, new BlockPos(x, base, z), crop, Block.UPDATE_ALL);
            }
        }
        // Compostero (puesto de trabajo del granjero). Tres cosas, en este orden (importa):
        //  1) Se quita el compostero VIEJO de la columna ANTES de medir el suelo. Si no, `groundY` cuenta el
        //     compostero como si fuera suelo y el nuevo sube un bloque en cada migración: medido en el guardado
        //     del jugador, el compostero estaba en la capa 64 con el suelo del pueblo en la 62 (flotando).
        //  2) Se mide el suelo ya limpio y se rellena la columna hasta la capa de DEBAJO del compostero (césped
        //     arriba, tierra debajo). Antes el relleno se quedaba una capa corto (`y < nivelCompostero - 1`) y la
        //     limpieza se llevaba por delante el bloque de superficie -> el compostero quedaba FLOTANDO.
        //  3) Se coloca el compostero apoyado en esa capa.
        int compX = corner.getX() - 1;
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
