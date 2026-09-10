package com.chipoodle.devilrpg.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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
    private static final int FENCE_RADIUS = 29;

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

    /** Genera las cabañas, los aldeanos, los caminos y la valla alrededor del centro. */
    public static void generate(ServerLevel level, BlockPos center) {
        // Limpiar hasta cubrir el talud exterior (que rodea el área nivelada).
        clearVegetation(level, center, LEVEL_RADIUS + SLOPE_WIDTH);
        // Si la zona cae sobre agua (ver waterSurfaceForArea), construir una isla flotante AL NIVEL DEL AGUA:
        // la aldea no se puede mover del objetivo, así que si el objetivo cayó en el océano o un lago, se
        // levanta la isla. Si no, nivelar el terreno como siempre.
        int waterLevel = waterSurfaceForArea(level, center, LEVEL_RADIUS + SLOPE_WIDTH);
        if (waterLevel >= 0) {
            buildFloatingIsland(level, center, LEVEL_RADIUS, waterLevel);
        } else {
            levelTerrain(level, center, LEVEL_RADIUS);
        }
        // Posiciones de las cabañas (base). La puerta mira a FRONT (norte), en base.z-2.
        BlockPos h0 = center.offset(-17, 0, -3);
        BlockPos h1 = center.offset(16, 0, -4);
        BlockPos h2 = center.offset(-3, 0, 17);

        // Caminos PRIMERO, sobre el suelo nivelado (así no se generan sobre el techo de las casas ni
        // sobre la campana). Van del centro hasta justo frente a la puerta de cada cabaña.
        paths(level, center, h0, h1, h2);

        // Construir las cabañas DESPUÉS del camino (el camino no queda sobre ellas).
        hut(level, h0);
        hut(level, h1);
        hut(level, h2);

        // Campana al final, en el centro, limpiando su columna (nadie la tapa).
        bell(level, center);

        // Aldeanos justo frente a la puerta de cada cabaña.
        spawnVillager(level, center.offset(-17, 0, -6), VillagerProfession.FARMER);
        spawnVillager(level, center.offset(16, 0, -7), VillagerProfession.WEAPONSMITH);
        spawnVillager(level, center.offset(-3, 0, 14), VillagerProfession.CLERIC);

        // Golem de hierro que protege la aldea.
        spawnIronGolem(level, center.offset(4, 0, 4));

        // Faroles con poste distribuidos por la aldea (evitan spawn de zombies con la mecánica vanilla).
        torches(level, center);

        // Torre de vigilancia en un punto estratégico (cerca de la entrada norte, mirando hacia fuera).
        tower(level, center.offset(-9, 0, -20));

        fence(level, center);
    }

    /**
     * Torre de vigilancia de cobblestone (para futuros arqueros/guardias): base sólida, hueco interior,
     * plataforma superior con almenas y escalera de acceso lateral.
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
                        level.setBlock(new BlockPos(base.getX() + x, y + i, base.getZ() + z), Blocks.COBBLESTONE.defaultBlockState(), 3);
                    } else {
                        level.setBlock(new BlockPos(base.getX() + x, y + i, base.getZ() + z), Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
        // Plataforma superior (piso de madera).
        int topY = y + height;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                level.setBlock(new BlockPos(base.getX() + x, topY, base.getZ() + z), Blocks.OAK_PLANKS.defaultBlockState(), 3);
            }
        }
        // Almenas (murete) alrededor del borde superior.
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                boolean edge = Math.abs(x) == 1 || Math.abs(z) == 1;
                if (edge && (x + z) % 2 == 0) { // espacios intercalados
                    level.setBlock(new BlockPos(base.getX() + x, topY + 1, base.getZ() + z), Blocks.COBBLESTONE.defaultBlockState(), 3);
                }
            }
        }
        // Escalera de acceso por un lateral (sube en espiral simple: una cara).
        for (int i = 0; i < height; i++) {
            level.setBlock(new BlockPos(base.getX(), y + i, base.getZ() + 1), Blocks.AIR.defaultBlockState(), 3);
            if (i < 2) {
                level.setBlock(new BlockPos(base.getX(), y + i, base.getZ() + 2), Blocks.DIRT.defaultBlockState(), 3);
            }
        }
        level.setBlock(new BlockPos(base.getX(), y + 1, base.getZ() + 2), Blocks.OAK_PLANKS.defaultBlockState(), 3);
    }

    /** Coloca una campana en el centro de la aldea (marcador de la villa), sobre un soporte de piedra. */
    private static void bell(ServerLevel level, BlockPos center) {
        int y = groundY(level, center.getX(), center.getZ());
        // Limpiar la columna del centro por arriba para que no quede tierra apilada sobre la campana.
        clearColumnAbove(level, center.getX(), center.getZ(), y);
        // Apoyar la campana con un bloque de piedra debajo (la campana FLOOR necesita bloque sólido debajo).
        level.setBlock(new BlockPos(center.getX(), y - 1, center.getZ()), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(new BlockPos(center.getX(), y, center.getZ()),
                Blocks.BELL.defaultBlockState().setValue(BellBlock.FACING, Direction.SOUTH).setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR), 3);
    }

    /** Quita el aire y bloques que queden en la columna por encima de {@code baseY+1} (deja la campana al aire). */
    private static void clearColumnAbove(ServerLevel level, int x, int z, int baseY) {
        for (int yy = baseY + 1; yy <= baseY + 8 && yy < level.getMaxBuildHeight(); yy++) {
            BlockState bs = level.getBlockState(new BlockPos(x, yy, z));
            if (bs.isAir()) continue;
            // Solo limpiar bloques que no sean estructuras (tierra/cesped de la isla o nivelado).
            level.setBlock(new BlockPos(x, yy, z), Blocks.AIR.defaultBlockState(), 3);
        }
    }

    /**
     * Coloca faroles distribuidos por la aldea (poste de valla + lanterna encima) en varios puntos, para
     * evitar que los zombies normales aparezcan de noche con la mecánica vanilla (luz).
     */
    private static void torches(ServerLevel level, BlockPos center) {
        int[][] spots = {
                {8, 0, -8},
                {-9, 0, 8},
                {0, 0, -15},
                {12, 0, 6},
                {-13, 0, -6},
                {5, 0, 14},
                {0, 0, 15},
                {-16, 0, 5},
        };
        for (int[] s : spots) {
            BlockPos spot = center.offset(s[0], 0, s[2]);
            int y = groundY(level, spot.getX(), spot.getZ());
            // Poste de valla (2 bloques) y lanterna encima.
            level.setBlock(new BlockPos(spot.getX(), y, spot.getZ()), Blocks.OAK_FENCE.defaultBlockState(), 3);
            level.setBlock(new BlockPos(spot.getX(), y + 1, spot.getZ()), Blocks.OAK_FENCE.defaultBlockState(), 3);
            level.setBlock(new BlockPos(spot.getX(), y + 2, spot.getZ()), Blocks.LANTERN.defaultBlockState(), 3);
        }
    }

    /** Spawnea un golem de hierro que defiende la aldea. */
    private static void spawnIronGolem(ServerLevel level, BlockPos pos) {
        // Fijar la Y al suelo real (la isla/terreno) para que no spawnee bajo la aldea ni se sofoque.
        int y = spawnY(level, pos.getX(), pos.getZ());
        IronGolem golem = EntityType.IRON_GOLEM.create(level, null, new BlockPos(pos.getX(), y, pos.getZ()), MobSpawnType.MOB_SUMMONED, true, true);
        if (golem != null) {
            golem.moveTo(pos.getX() + 0.5D, y, pos.getZ() + 0.5D, 0.0F, 0.0F);
            golem.setPersistenceRequired();
            level.addFreshEntity(golem);
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
                    level.setBlock(new BlockPos(top.getX(), y, top.getZ()), Blocks.DIRT.defaultBlockState(), 3);
                }
                // Si el terreno sobresale por encima de la isla, recortarlo para dejar la superficie plana.
                if (g > islandTop) {
                    for (int y = islandTop + 1; y < g; y++) {
                        level.setBlock(new BlockPos(top.getX(), y, top.getZ()), Blocks.AIR.defaultBlockState(), 3);
                    }
                }
                level.setBlock(top, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
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
                    level.setBlock(new BlockPos(center.getX() + x, y, center.getZ() + z), block.defaultBlockState(), 3);
                }
            }
        }
    }

    /** Camino de tierra apisonada (el de pala) de 2 bloques de ancho entre el centro y cada cabaña. */
    private static void paths(ServerLevel level, BlockPos center, BlockPos h0, BlockPos h1, BlockPos h2) {
        line(level, center, doorApproach(h0));
        line(level, center, doorApproach(h1));
        line(level, center, doorApproach(h2));
    }

    /** Punto justo frente a la puerta de una cabaña (la puerta mira a {@link VillageGenerator#FRONT}). */
    private static BlockPos doorApproach(BlockPos hutCenter) {
        int fx = FRONT.getStepX() * 3;
        int fz = FRONT.getStepZ() * 3;
        return hutCenter.offset(fx, 0, fz);
    }

    /**
     * Dibuja un camino de tierra apisonada de 2 bloques de ancho en el plano XZ entre dos puntos, a ras
     * de suelo. No toca la celda del centro (donde va la campana) y, si una posición quedó elevada (sobre
     * el techo de una casa), baja el camino a la superficie real rellenando con tierra hasta el suelo.
     */
    private static void line(ServerLevel level, BlockPos from, BlockPos to) {
        int steps = Math.max(Math.abs(to.getX() - from.getX()), Math.abs(to.getZ() - from.getZ()));
        // El ancho de 2 bloques se aplica en el eje perpendicular a la dirección del camino (en el plano XZ).
        boolean horizontal = Math.abs(to.getX() - from.getX()) >= Math.abs(to.getZ() - from.getZ());
        int widthX = horizontal ? 0 : 1;
        int widthZ = horizontal ? 1 : 0;
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
                level.setBlock(new BlockPos(px, y, pz), Blocks.DIRT_PATH.defaultBlockState(), 3);
            }
        }
    }

    /**
     * Nivela el terreno del área de la aldea: toma la altura base (mediana de las alturas de suelo), rellena
     * con tierra las columnas que estén por debajo y recorta las que estén por encima, dejando toda el área
     * (hasta donde empieza la valla) al mismo nivel para que no queden abismos ni desniveles.
     */
    private static void levelTerrain(ServerLevel level, BlockPos center, int radius) {
        List<Integer> heights = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                heights.add(groundY(level, center.getX() + x, center.getZ() + z));
            }
        }
        Collections.sort(heights);
        int baseY = heights.get(heights.size() / 2); // mediana

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int g = groundY(level, center.getX() + x, center.getZ() + z);
                // Rellenar las columnas que estén por debajo del nivel base.
                for (int y = g; y < baseY; y++) {
                    level.setBlock(new BlockPos(center.getX() + x, y, center.getZ() + z), Blocks.DIRT.defaultBlockState(), 3);
                }
                // Recortar las columnas que sobresalgan por encima del nivel base.
                for (int y = baseY + 1; y < g; y++) {
                    level.setBlock(new BlockPos(center.getX() + x, y, center.getZ() + z), Blocks.AIR.defaultBlockState(), 3);
                }
                // Asegurar la capa superficial al nivel base.
                level.setBlock(new BlockPos(center.getX() + x, baseY - 1, center.getZ() + z), Blocks.DIRT.defaultBlockState(), 3);
            }
        }
        // Talud exterior: una pendiente escalonada en el borde para que la aldea parezca una MESETA natural
        // (como el terreno vanilla) en vez de un cubo de paredes verticales.
        addOuterSlope(level, center, radius, baseY);
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
                // Rellenar hasta el nivel del talud si el terreno está por debajo.
                for (int y = g; y < targetY; y++) {
                    level.setBlock(new BlockPos(px, y, pz), Blocks.DIRT.defaultBlockState(), 3);
                }
                // Recortar si el terreno natural sobresale por encima del talud.
                for (int y = targetY; y < g; y++) {
                    BlockState bs = level.getBlockState(new BlockPos(px, y, pz));
                    if (bs.isSolid()) {
                        level.setBlock(new BlockPos(px, y, pz), Blocks.AIR.defaultBlockState(), 3);
                    }
                }
                // Capa superficial del talud (cesped), salvo que sea agua.
                BlockPos surface = new BlockPos(px, targetY - 1, pz);
                if (!level.getBlockState(surface).is(Blocks.WATER)) {
                    level.setBlock(surface, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                }
            }
        }
    }

    /**
     * Muro de madera y piedra alrededor de la aldea, más realista: logs horizontales (2 bloques de alto)
     * con columnas verticales de cobblestone cada cierta distancia, y 4 entradas de cobblestone en los
     * puntos cardinales (norte/sur/este/oeste).
     */
    private static void fence(ServerLevel level, BlockPos center) {
        int r = FENCE_RADIUS;
        // Anillo en orden angular (deduplicando consecutivos), para poder recorrerlo y conocer la
        // dirección de cada tramo.
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
        // Secuencia continua de celdas del muro (rellenando los saltos con pasos cardinales para que no
        // queden huecos en la diagonal).
        List<BlockPos> ring = new ArrayList<>();
        for (int i = 0; i < pts.size(); i++) {
            BlockPos from = pts.get(i);
            BlockPos to = pts.get((i + 1) % pts.size());
            fillCardinal(from, to, ring);
        }

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
                level.setBlock(new BlockPos(p.getX(), y, p.getZ()), Blocks.DIRT.defaultBlockState(), 3);
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
        level.setBlock(new BlockPos(p.getX(), baseY, p.getZ()), log, 3);
        level.setBlock(new BlockPos(p.getX(), baseY + 1, p.getZ()), log, 3);
    }

    /** Columna vertical de cobblestone (3 bloques sobre la superficie) con un pequeño remate. */
    private static void column(ServerLevel level, BlockPos p, int baseY) {
        for (int i = 0; i <= 2; i++) {
            level.setBlock(new BlockPos(p.getX(), baseY + i, p.getZ()), Blocks.COBBLESTONE.defaultBlockState(), 3);
        }
        level.setBlock(new BlockPos(p.getX(), baseY + 3, p.getZ()), Blocks.COBBLESTONE_STAIRS.defaultBlockState(), 3);
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
            level.setBlock(new BlockPos(p.getX() - signX, baseY + i, p.getZ() - signZ), Blocks.COBBLESTONE.defaultBlockState(), 3);
            level.setBlock(new BlockPos(p.getX() + signX, baseY + i, p.getZ() + signZ), Blocks.COBBLESTONE.defaultBlockState(), 3);
        }
        level.setBlock(new BlockPos(p.getX(), baseY + 3, p.getZ()), Blocks.COBBLESTONE.defaultBlockState(), 3);
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
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    /**
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
                        level.setBlock(new BlockPos(base.getX() + x, y, base.getZ() + z), Blocks.DIRT.defaultBlockState(), 3);
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
                    level.setBlock(new BlockPos(base.getX() + x, y, base.getZ() + z), state, 3);
                }
            }
        }
        // Techo (nivel 4 = floorY+3), cubriendo todo el hueco.
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                level.setBlock(new BlockPos(base.getX() + x, floorY + 3, base.getZ() + z), Blocks.SPRUCE_PLANKS.defaultBlockState(), 3);
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
        level.setBlock(footPos, foot, 3);
        level.setBlock(footPos.relative(Direction.SOUTH), head, 3);
    }

    /** Pilar de vallas que baja desde el piso hasta el fondo marino, ≥3 bloques bajo el agua, terminando en madera. */
    private static void pillar(ServerLevel level, int x, int z, int topY, int seabed) {
        int depth = Math.max(3, topY - seabed); // al menos 3 bloques bajo el agua
        int bottom = topY - depth;
        if (bottom < seabed) bottom = seabed;
        for (int y = bottom; y < topY; y++) {
            level.setBlock(new BlockPos(x, y, z), Blocks.OAK_FENCE.defaultBlockState(), 3);
        }
        // Bloque de madera como base del pilar.
        level.setBlock(new BlockPos(x, bottom, z), Blocks.OAK_LOG.defaultBlockState(), 3);
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
            level.setBlock(stairPos, Blocks.OAK_STAIRS.defaultBlockState()
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
        level.setBlock(pos, Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, FRONT).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 3);
        level.setBlock(pos.above(), Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, FRONT).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
    }

    private static void spawnVillager(ServerLevel level, BlockPos pos, VillagerProfession profession) {
        Villager villager = EntityType.VILLAGER.create(level, null, pos, MobSpawnType.MOB_SUMMONED, true, true);
        if (villager != null) {
            villager.setVillagerData(villager.getVillagerData().setProfession(profession));
            villager.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
            villager.setPersistenceRequired();
            level.addFreshEntity(villager);
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
}
