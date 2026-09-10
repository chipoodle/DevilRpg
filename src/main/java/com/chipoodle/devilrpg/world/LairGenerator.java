package com.chipoodle.devilrpg.world;

import com.chipoodle.devilrpg.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Genera una <b>guarida</b> en el mundo: un claro corrupto que <b>cambia el terreno</b> a su alrededor
 * (tierra muerta, sculk, espinas de hueso, telarañas) con un <b>núcleo</b> en el centro
 * ({@link ModBlocks#LAIR_CORE_BLOCK}). Mientras el núcleo exista, la guarida spawnea enemigos (lo gestiona
 * {@code LairManager}); al destruirlo, la guarida queda limpiada. Es un lugar que el jugador puede asaltar.
 * <p>
 * La guarida y el <b>corral macabro</b> son una <b>sola plataforma</b> a un mismo nivel: se nivelan juntos
 * (con un corredor que los une) a la altura mediana del terreno. Si el punto cae sobre agua, la plataforma
 * se construye al nivel del agua con una base cónica, así nunca queda sumergida. En tierra se le añade un
 * <b>talud</b> exterior para que parezca una meseta natural y no un cubo.
 */
public final class LairGenerator {

    /** Radio de la plataforma principal (el "claro" de la guarida, donde está el altar). */
    private static final int RADIUS = 10;
    /** Radio del "núcleo corrupto" (sculk): cubre el santuario y el anillo alrededor del foso. */
    private static final int CORRUPT_RADIUS = 8;
    /** Radio del corral macabro y distancia a la que se sitúa del centro de la guarida. */
    private static final int FARM_RADIUS = 4;
    private static final int FARM_DISTANCE = RADIUS + FARM_RADIUS + 4;
    /**
     * Orla <b>plana</b> alrededor del corral, dentro de la plataforma. Es imprescindible: el corral es un
     * foso y, si el terreno empezara a bajar justo en su borde (como pasa en el talud exterior), la pared del
     * foso quedaría de 1 bloque por ese lado y los animales se escaparían. Con la orla, el foso siempre tiene
     * sus 2 bloques de pared alrededor.
     */
    private static final int FARM_RIM = 3;
    /** Profundidad del corral-foso: 2 bloques, más de lo que salta un animal, así que no se escapan. */
    private static final int FARM_DEPTH = 2;
    /** Medio ancho del corredor que une la guarida con el corral (todo a la misma altura). */
    private static final int CORRIDOR_HALF = 3;
    /** Profundidad de la base cónica cuando la plataforma flota sobre agua. */
    private static final int SUPPORT_DEPTH = 6;
    /** Ancho/altura del talud exterior que suaviza el borde en tierra. */
    private static final int SLOPE_WIDTH = 6;
    private static final int SLOPE_HEIGHT = 3;
    /** Extensión a recorrer en los barridos: la plataforma entera más el talud. */
    private static final int EXTENT = FARM_DISTANCE + FARM_RADIUS + FARM_RIM + SLOPE_WIDTH;
    /** Radio (desde el centro de la guarida) de las antorchas de almas. */
    private static final int TORCH_RADIUS = RADIUS - 3;
    /** Radio de la caja de sellos que blinda el núcleo (1 => caja de 3x3x3, con el núcleo en el centro). */
    public static final int SEAL_RADIUS = 1;
    /**
     * Foso perimetral del santuario: anillo entre estos dos radios (en bloques) y la profundidad a la que
     * queda su <b>fondo transitable</b> (medida desde el nivel del suelo de la guarida).
     * <p>
     * El fondo es de <b>arena de almas</b> y NO de magma, a propósito: la infección de sculk tiene que poder
     * seguir extendiéndose y un foso de magma la encerraría (el sculk no puede cruzar un hueco de aire, pero
     * sí bajar por el subsuelo y cruzar el fondo del foso si es sólido y convertible). Además, 4 bloques de
     * caída es más de lo que un mob se atreve a bajar ({@code maxFallDistance} es 3), así que no se meten
     * dentro y se quedan atrapados.
     */
    private static final double MOAT_INNER = 3.5D;
    private static final double MOAT_OUTER = 6.0D;
    private static final int MOAT_FLOOR_DEPTH = 5;

    private LairGenerator() {
    }

    /**
     * Distancia con signo al borde de la plataforma, en coordenadas relativas al centro de la guarida:
     * <b>negativa dentro</b> (guarida, corredor o corral), positiva fuera. El corral va hacia el +X, unido a
     * la guarida por un corredor, de modo que las tres zonas se nivelan y se escalonan juntas.
     */
    private static double platformDistance(double x, double z) {
        double dLair = Math.sqrt(x * x + z * z) - RADIUS;
        double dx = x - FARM_DISTANCE;
        double dFarm = Math.sqrt(dx * dx + z * z) - (FARM_RADIUS + FARM_RIM);
        // Corredor: distancia al segmento (0,0)-(FARM_DISTANCE,0) menos su medio ancho.
        double t = Math.max(0.0, Math.min(FARM_DISTANCE, x));
        double dCorridor = Math.sqrt((x - t) * (x - t) + z * z) - CORRIDOR_HALF;
        return Math.min(dLair, Math.min(dFarm, dCorridor));
    }

    /**
     * Genera la guarida centrada en {@code center}. Devuelve la posición del núcleo (el bloque asaltable),
     * o {@code null} si no hubo sitio válido.
     */
    public static BlockPos generate(ServerLevel level, BlockPos center) {
        BlockPos farmCenter = center.offset(FARM_DISTANCE, 0, 0);
        // Mismas reglas que la aldea (VillageGenerator.waterSurfaceForArea): si la zona cae sobre agua, se
        // construye una plataforma AL NIVEL DEL AGUA y no queda sumergida. OJO: esto se decide sobre TODA la
        // huella, no sobre la columna del centro. Decidiéndolo por el centro, una guarida en la costa (centro
        // en tierra, resto en el mar) elegía el camino de tierra y, como la mediana de alturas se iba al fondo
        // marino, quedaba construida bajo el agua.
        int waterLevel = VillageGenerator.waterSurfaceForArea(level, center, EXTENT);
        int baseY; // posición transitable de la plataforma (el suelo sólido queda en baseY-1)
        if (waterLevel >= 0) {
            baseY = waterLevel + 1;
            buildFloatingBase(level, center, waterLevel);
        } else {
            baseY = levelTerrain(level, center);
            addOuterSlope(level, center, baseY);
        }
        if (baseY <= level.getMinBuildHeight()) {
            return null;
        }

        // 1) Superficie de TODA la plataforma (guarida + corredor + corral): sculk en el núcleo corrupto,
        //    tierra muerta en el resto, y se despeja lo que sobresalga. Al ser una sola pasada, el corral
        //    queda exactamente al mismo nivel que el suelo de la guarida.
        for (int x = -EXTENT; x <= EXTENT; x++) {
            for (int z = -EXTENT; z <= EXTENT; z++) {
                if (platformDistance(x, z) > 0) continue;
                int px = center.getX() + x;
                int pz = center.getZ() + z;
                double dist = Math.sqrt(x * x + z * z);
                Block surface = dist <= CORRUPT_RADIUS ? Blocks.SCULK : Blocks.COARSE_DIRT;
                level.setBlock(new BlockPos(px, baseY - 1, pz), surface.defaultBlockState(), 3);
                for (int y = baseY; y < baseY + 4; y++) {
                    if (level.getBlockState(new BlockPos(px, y, pz)).isSolid()) {
                        level.setBlock(new BlockPos(px, y, pz), Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
        // 1b) Venas de sculk salpicando el terreno alrededor del núcleo corrupto.
        //
        //      OJO con la cara: en un MultifaceBlock la propiedad se llama por DONDE ESTÁ EL APOYO, no por
        //      dónde se ve la vena. `UP_AABB` es la franja de ARRIBA del hueco (y 15→16), así que `up=true`
        //      cuelga del bloque de encima; `DOWN_AABB` es la franja de ABAJO (y 0→1), así que una vena
        //      TUMBADA en el suelo es `down=true`. Aquí estaba el bug: puestas con `up=true` en el suelo, con
        //      aire encima, quedaban FLOTANDO en el aire. (El soul lichen del mod ya usaba DOWN, por eso aquél
        //      se ve bien.) Además `setBlock` no valida la colocación, así que el juego no las borraba.
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > RADIUS || dist <= CORRUPT_RADIUS) continue;
                if ((x * 31 + z * 17) % 5 != 0) continue;
                BlockPos veinPos = new BlockPos(center.getX() + x, baseY, center.getZ() + z);
                BlockState current = level.getBlockState(veinPos);
                if (!level.getBlockState(veinPos.below()).isSolid()) {
                    // Sin suelo debajo no hay dónde apoyarla: si había una vena vieja mal puesta (flotando),
                    // se limpia en vez de dejarla ahí colgada.
                    if (current.is(Blocks.SCULK_VEIN)) {
                        level.setBlock(veinPos, Blocks.AIR.defaultBlockState(), 3);
                    }
                    continue;
                }
                // Si ya había una vena, se reescribe (así se corrigen las que quedaron flotando de antes);
                // cualquier otra cosa que ocupe el hueco se respeta.
                if (!current.isAir() && !current.is(Blocks.SCULK_VEIN)) continue;
                level.setBlock(veinPos, Blocks.SCULK_VEIN.defaultBlockState()
                        .setValue(MultifaceBlock.getFaceProperty(Direction.DOWN), true), 3);
            }
        }

        // 2) Espinas de hueso alrededor (pilares que "marcan" la guarida). Van desfasadas media muesca para
        //    que NINGUNA caiga en los cardinales: ahí están los puentes del foso y no deben estorbar.
        int spines = 16;
        for (int a = 0; a < spines; a++) {
            double angle = ((a + 0.5) / spines) * Math.PI * 2.0;
            int x = (int) Math.round(Math.cos(angle) * (RADIUS - 1));
            int z = (int) Math.round(Math.sin(angle) * (RADIUS - 1));
            if (insideFarm(x, z)) continue;
            int height = 2 + (a % 3);
            for (int i = 0; i < height; i++) {
                level.setBlock(new BlockPos(center.getX() + x, baseY + i, center.getZ() + z),
                        Blocks.BONE_BLOCK.defaultBlockState(), 3);
            }
        }

        // 3) Santuario: el núcleo, blindado por la CAJA DE SELLOS (inquebrantable mientras viva el cultivador
        //    de la guarida; de abrirla se encarga LairManager). Sobre la isla interior del foso van los
        //    CATALIZADORES de sculk: cuando un mob muere encima, la infección se expande sola.
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                level.setBlock(new BlockPos(center.getX() + x, baseY - 1, center.getZ() + z),
                        Blocks.SCULK.defaultBlockState(), 3);
            }
        }
        BlockPos corePos = new BlockPos(center.getX(), baseY, center.getZ());
        level.setBlock(corePos, ModBlocks.LAIR_CORE_BLOCK.get().defaultBlockState(), 3);
        buildSealCage(level, corePos);
        // Catalizadores en las diagonales de la isla (los cardinales son la entrada de los puentes).
        for (int[] c : new int[][]{{2, 2}, {-2, 2}, {2, -2}, {-2, -2}}) {
            level.setBlock(new BlockPos(center.getX() + c[0], baseY - 1, center.getZ() + c[1]),
                    Blocks.SCULK_CATALYST.defaultBlockState(), 3);
        }
        // Losa de obsidiana llorosa donde cada puente toca la isla.
        for (int[] c : new int[][]{{3, 0}, {-3, 0}, {0, 3}, {0, -3}}) {
            level.setBlock(new BlockPos(center.getX() + c[0], baseY - 1, center.getZ() + c[1]),
                    Blocks.CRYING_OBSIDIAN.defaultBlockState(), 3);
        }

        // 4) Tótems con calaveras en las diagonales (los cardinales son las entradas de los puentes) y
        //    antorchas de almas marcando cada entrada.
        for (int[] c : new int[][]{{6, 6}, {-6, 6}, {6, -6}, {-6, -6}}) {
            int x = center.getX() + c[0];
            int z = center.getZ() + c[1];
            level.setBlock(new BlockPos(x, baseY, z), Blocks.OAK_FENCE.defaultBlockState(), 3);
            level.setBlock(new BlockPos(x, baseY + 1, z), Blocks.OAK_FENCE.defaultBlockState(), 3);
            level.setBlock(new BlockPos(x, baseY + 2, z), Blocks.SKELETON_SKULL.defaultBlockState(), 3);
        }
        for (int[] c : new int[][]{{TORCH_RADIUS, 0}, {-TORCH_RADIUS, 0}, {0, TORCH_RADIUS}, {0, -TORCH_RADIUS}}) {
            if (insideFarm(c[0], c[1])) continue;
            level.setBlock(new BlockPos(center.getX() + c[0], baseY, center.getZ() + c[1]),
                    Blocks.SOUL_TORCH.defaultBlockState(), 3);
        }

        // 5) Granja macabra: corral cerrado con ganado que el cultivador criará y sacrificará sobre el sculk.
        buildFarm(level, farmCenter, baseY);

        // 6) Foso del santuario: se cava AL FINAL, para que nada de lo anterior quede flotando dentro.
        buildMoat(level, center, baseY);
        return corePos;
    }

    /**
     * Construye la caja de sellos (inquebrantable) que blinda el núcleo hasta que muera el guardián.
     * <b>Público</b> porque {@code LairManager} vuelve a levantarla cuando la guarida consagra un guardián de
     * relevo (si mataste al anterior y no rompiste el núcleo).
     */
    public static void buildSealCage(ServerLevel level, BlockPos corePos) {
        BlockState seal = ModBlocks.SCULK_SEAL_BLOCK.get().defaultBlockState();
        for (int x = -SEAL_RADIUS; x <= SEAL_RADIUS; x++) {
            for (int y = -SEAL_RADIUS; y <= SEAL_RADIUS; y++) {
                for (int z = -SEAL_RADIUS; z <= SEAL_RADIUS; z++) {
                    if (x == 0 && y == 0 && z == 0) continue; // el centro es el núcleo
                    level.setBlock(corePos.offset(x, y, z), seal, 3);
                }
            }
        }
    }

    /**
     * Cava el foso que rodea al santuario: un anillo de 4 bloques de caída con el <b>fondo de arena de
     * almas</b>. Solo se cruza andando por los cuatro <b>puentes de hueso</b> de los cardinales, de un bloque
     * de ancho, así que llegar al núcleo obliga a rodear o a construir bajo el fuego enemigo.
     * <p>
     * <b>La infección sí puede cruzar</b>: el sculk baja por el subsuelo de la isla, cruza el fondo (arena de
     * almas, que es convertible) y sube por el otro lado. Con magma en el fondo eso era imposible y el foso
     * actuaba de barrera que contenía la mancha.
     */
    private static void buildMoat(ServerLevel level, BlockPos center, int baseY) {
        int outer = (int) Math.ceil(MOAT_OUTER);
        for (int x = -outer; x <= outer; x++) {
            for (int z = -outer; z <= outer; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist < MOAT_INNER || dist > MOAT_OUTER) continue;
                int px = center.getX() + x;
                int pz = center.getZ() + z;
                if (x == 0 || z == 0) {
                    // Puente de hueso: se conserva el suelo y se despeja lo que haya encima.
                    level.setBlock(new BlockPos(px, baseY - 1, pz), Blocks.BONE_BLOCK.defaultBlockState(), 3);
                    for (int y = baseY; y < baseY + 4; y++) {
                        if (level.getBlockState(new BlockPos(px, y, pz)).isSolid()) {
                            level.setBlock(new BlockPos(px, y, pz), Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
                    continue;
                }
                // Foso: se vacía hasta el fondo y se pone un suelo sólido y convertible por el sculk.
                for (int y = baseY - 1; y > baseY - MOAT_FLOOR_DEPTH; y--) {
                    level.setBlock(new BlockPos(px, y, pz), Blocks.AIR.defaultBlockState(), 3);
                }
                level.setBlock(new BlockPos(px, baseY - MOAT_FLOOR_DEPTH, pz),
                        Blocks.SOUL_SAND.defaultBlockState(), 3);
            }
        }
    }

    /** ¿La posición relativa (x, z) cae dentro del corral (o su orla plana)? */
    private static boolean insideFarm(int x, int z) {
        double dx = x - FARM_DISTANCE;
        return Math.sqrt(dx * dx + z * z) <= FARM_RADIUS + FARM_RIM;
    }

    /**
     * Construye la granja macabra: el ganado vive en un <b>foso de {@link #FARM_DEPTH} bloques</b>, con un
     * parche de sculk y catalizadores en el fondo (para que lo que se sacrifique ahí alimente la infección).
     * <p>
     * <b>Nada de vallas</b>: una valla no es un bloque convertible por el sculk, así que un cercado de vallas
     * frenaba la infección justo en el corral. Las paredes del foso son tierra (convertible) y el foso es un
     * hueco, así que la mancha entra y sale sin obstáculo. Los animales no pueden saltar 2 bloques, así que el
     * foso los contiene igual que una valla.
     * <p>
     * La <b>salida es exclusiva del cultivador</b>: un escalón de 1 bloque en el borde (el resto del foso tiene
     * su pared entera de 2) y una puerta de madera cerrada en el borde de fuera. Los animales no pueden abrir
     * puertas; el cultivador sí ({@code OpenDoorGoal} + {@code setCanOpenDoors(true)}, como los aldeanos).
     */
    private static void buildFarm(ServerLevel level, BlockPos center, int baseY) {
        int floor = baseY - FARM_DEPTH; // nivel transitable del fondo del foso

        // 1) Cavar el foso (el disco del corral).
        for (int x = -FARM_RADIUS; x <= FARM_RADIUS; x++) {
            for (int z = -FARM_RADIUS; z <= FARM_RADIUS; z++) {
                if (x * x + z * z > FARM_RADIUS * FARM_RADIUS) continue;
                carveFarmColumn(level, center.getX() + x, center.getZ() + z, baseY, floor);
            }
        }
        // Las dos columnas vecinas a la salida se cavan COMPLETAS: así la pared sigue teniendo sus 2 bloques a
        // los lados y el único escalón que sube desde el fondo es el de la salida.
        carveFarmColumn(level, center.getX() - FARM_RADIUS, center.getZ() - 1, baseY, floor);
        carveFarmColumn(level, center.getX() - FARM_RADIUS, center.getZ() + 1, baseY, floor);

        // 2) Fondo del foso: parche de sculk en el centro (donde caen los sacrificios), tierra muerta alrededor.
        for (int x = -FARM_RADIUS; x <= FARM_RADIUS; x++) {
            for (int z = -FARM_RADIUS; z <= FARM_RADIUS; z++) {
                int sq = x * x + z * z;
                if (sq > FARM_RADIUS * FARM_RADIUS) continue;
                Block block = sq <= 2 ? Blocks.SCULK : Blocks.COARSE_DIRT;
                level.setBlock(new BlockPos(center.getX() + x, floor - 1, center.getZ() + z),
                        block.defaultBlockState(), 3);
            }
        }
        // Dos catalizadores en el fondo: expanden la infección cuando muere un animal encima.
        level.setBlock(new BlockPos(center.getX() + 2, floor - 1, center.getZ() + 2),
                Blocks.SCULK_CATALYST.defaultBlockState(), 3);
        level.setBlock(new BlockPos(center.getX() + 2, floor - 1, center.getZ() - 2),
                Blocks.SCULK_CATALYST.defaultBlockState(), 3);

        // 3) Salida del cultivador: escalón de 1 bloque en el borde -X (el que mira al corredor) y puerta
        //    cerrada justo fuera. La columna del escalón se sube un bloque respecto al fondo del foso.
        int stepX = center.getX() - FARM_RADIUS;
        int stepZ = center.getZ();
        for (int y = baseY - 1; y < baseY + 2; y++) {
            level.setBlock(new BlockPos(stepX, y, stepZ), Blocks.AIR.defaultBlockState(), 3);
        }
        level.setBlock(new BlockPos(stepX, floor, stepZ), Blocks.COARSE_DIRT.defaultBlockState(), 3);
        placeDoor(level, new BlockPos(stepX - 1, baseY, stepZ), Direction.WEST);

        // Ganado inicial, en el fondo del foso.
        spawnAnimal(level, center, floor, EntityType.COW, 2);
        spawnAnimal(level, center, floor, EntityType.SHEEP, 2);
        spawnAnimal(level, center, floor, EntityType.PIG, 1);
        spawnAnimal(level, center, floor, EntityType.CHICKEN, 2);
    }

    /**
     * Cava una columna del foso del corral: deja el suelo transitable en {@code floor} (bloque sólido justo
     * debajo) y despeja el aire por encima, incluido lo que hubiera a nivel del suelo.
     */
    private static void carveFarmColumn(ServerLevel level, int px, int pz, int baseY, int floor) {
        for (int y = floor; y < baseY + 2; y++) {
            level.setBlock(new BlockPos(px, y, pz), Blocks.AIR.defaultBlockState(), 3);
        }
        level.setBlock(new BlockPos(px, floor - 1, pz), Blocks.COARSE_DIRT.defaultBlockState(), 3);
    }

    /** Coloca una puerta de dos bloques (mitad inferior + superior), cerrada. */
    private static void placeDoor(ServerLevel level, BlockPos pos, Direction facing) {
        level.setBlock(pos, Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 3);
        level.setBlock(pos.above(), Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
    }

    /**
     * Marca del <b>ganado de la granja macabra</b>. Los animales del corral llevan esta etiqueta para que los
     * demás enemigos de la guarida (los {@code AggressiveZombieEntity}) <b>no los cazen</b>: son el rebaño del
     * cultivador y si los matan se queda sin nada que criar ni sacrificar. Los animales <b>salvajes</b> que
     * entren en la guarida sí siguen siendo cazados (así es como la infección se alimenta sola).
     */
    public static final String LIVESTOCK_TAG = "devilrpg_livestock";

    /** ¿Es ganado de una granja macabra? (ver {@link #LIVESTOCK_TAG}). */
    public static boolean isLivestock(net.minecraft.world.entity.Entity entity) {
        return entity.getTags().contains(LIVESTOCK_TAG);
    }

    /** Spawnea {@code count} animales del tipo dado dentro del corral, a la altura del suelo. */
    private static void spawnAnimal(ServerLevel level, BlockPos center, int baseY,
                                    EntityType<? extends net.minecraft.world.entity.animal.Animal> type, int count) {
        for (int i = 0; i < count; i++) {
            int x = center.getX() + level.random.nextInt(3) - 1;
            int z = center.getZ() + level.random.nextInt(3) - 1;
            var animal = type.create(level, null, new BlockPos(x, baseY, z),
                    net.minecraft.world.entity.MobSpawnType.MOB_SUMMONED, true, true);
            if (animal != null) {
                animal.moveTo(x + 0.5D, baseY, z + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
                animal.setPersistenceRequired();
                // Es ganado del cultivador, no una presa: los zombies de la guarida no deben cazarlo.
                animal.addTag(LIVESTOCK_TAG);
                level.addFreshEntity(animal);
            }
        }
    }

    /**
     * Nivela la plataforma completa (guarida + corredor + corral) a la mediana de alturas del terreno y
     * devuelve el nivel transitable resultante.
     */
    private static int levelTerrain(ServerLevel level, BlockPos center) {
        List<Integer> heights = new ArrayList<>();
        for (int x = -EXTENT; x <= EXTENT; x++) {
            for (int z = -EXTENT; z <= EXTENT; z++) {
                if (platformDistance(x, z) > 0) continue;
                heights.add(groundY(level, center.getX() + x, center.getZ() + z));
            }
        }
        Collections.sort(heights);
        int baseY = heights.get(heights.size() / 2);
        for (int x = -EXTENT; x <= EXTENT; x++) {
            for (int z = -EXTENT; z <= EXTENT; z++) {
                if (platformDistance(x, z) > 0) continue;
                int px = center.getX() + x;
                int pz = center.getZ() + z;
                int g = groundY(level, px, pz);
                if (g < baseY) {
                    // Terreno más bajo: se rellena hasta el nivel de la plataforma.
                    for (int y = g; y < baseY; y++) {
                        level.setBlock(new BlockPos(px, y, pz), Blocks.DIRT.defaultBlockState(), 3);
                    }
                } else {
                    // Terreno más alto: se recorta (incluido el bloque en baseY, si no quedaría un escalón).
                    for (int y = baseY; y < g; y++) {
                        if (level.getBlockState(new BlockPos(px, y, pz)).isSolid()) {
                            level.setBlock(new BlockPos(px, y, pz), Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
                }
                level.setBlock(new BlockPos(px, baseY - 1, pz), Blocks.DIRT.defaultBlockState(), 3);
            }
        }
        return baseY;
    }

    /**
     * Talud exterior de la plataforma (en tierra): pendiente escalonada en el borde para que parezca una
     * meseta natural y no un cubo.
     */
    private static void addOuterSlope(ServerLevel level, BlockPos center, int baseY) {
        int outer = EXTENT;
        for (int x = -outer; x <= outer; x++) {
            for (int z = -outer; z <= outer; z++) {
                double d = platformDistance(x, z);
                if (d <= 0 || d > SLOPE_WIDTH) continue;
                double fraction = d / SLOPE_WIDTH;
                int targetY = baseY - (int) Math.round(fraction * SLOPE_HEIGHT);
                int px = center.getX() + x;
                int pz = center.getZ() + z;
                int g = groundY(level, px, pz);
                for (int y = g; y < targetY; y++) {
                    level.setBlock(new BlockPos(px, y, pz), Blocks.DIRT.defaultBlockState(), 3);
                }
                for (int y = targetY; y < g; y++) {
                    if (level.getBlockState(new BlockPos(px, y, pz)).isSolid()) {
                        level.setBlock(new BlockPos(px, y, pz), Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    /**
     * Construye la base cuando la plataforma cae sobre agua: suelo A NIVEL del agua (reemplazando la capa
     * superior) y, debajo, una base cónica de tierra/piedra que se estrecha (como una isla), para que no se
     * vea sumergida ni como un cubo cuadrado.
     */
    private static void buildFloatingBase(ServerLevel level, BlockPos center, int waterLevel) {
        // 1) Plataforma: rellenar/recortar cada columna hasta el nivel del agua.
        for (int x = -EXTENT; x <= EXTENT; x++) {
            for (int z = -EXTENT; z <= EXTENT; z++) {
                if (platformDistance(x, z) > 0) continue;
                int px = center.getX() + x;
                int pz = center.getZ() + z;
                int g = groundY(level, px, pz);
                for (int y = Math.min(g, waterLevel); y < waterLevel; y++) {
                    level.setBlock(new BlockPos(px, y, pz), Blocks.DIRT.defaultBlockState(), 3);
                }
                if (g > waterLevel) {
                    for (int y = waterLevel; y < g; y++) {
                        if (level.getBlockState(new BlockPos(px, y, pz)).isSolid()) {
                            level.setBlock(new BlockPos(px, y, pz), Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
                }
                // El bloque de suelo queda al nivel del agua (no por encima).
                level.setBlock(new BlockPos(px, waterLevel - 1, pz), Blocks.DIRT.defaultBlockState(), 3);
            }
        }
        // 2) Base cónica (sigue la forma de la plataforma) que se estrecha hacia abajo, como una isla.
        for (int x = -EXTENT; x <= EXTENT; x++) {
            for (int z = -EXTENT; z <= EXTENT; z++) {
                for (int depth = 1; depth <= SUPPORT_DEPTH; depth++) {
                    double shrink = depth * 0.9;
                    if (platformDistance(x, z) > -shrink) continue;
                    int px = center.getX() + x;
                    int pz = center.getZ() + z;
                    int y = waterLevel - 1 - depth;
                    if (y <= level.getMinBuildHeight()) break;
                    Block block = depth <= SUPPORT_DEPTH / 2 ? Blocks.DIRT : Blocks.STONE;
                    level.setBlock(new BlockPos(px, y, pz), block.defaultBlockState(), 3);
                }
            }
        }
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
}
