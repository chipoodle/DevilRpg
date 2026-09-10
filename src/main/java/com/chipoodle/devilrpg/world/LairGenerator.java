package com.chipoodle.devilrpg.world;

import com.chipoodle.devilrpg.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
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
    /** Radio del "núcleo corrupto" central (sculk). */
    private static final int CORRUPT_RADIUS = 6;
    /** Radio del corral macabro y distancia a la que se sitúa del centro de la guarida. */
    private static final int FARM_RADIUS = 4;
    private static final int FARM_DISTANCE = RADIUS + FARM_RADIUS + 4;
    /** Medio ancho del corredor que une la guarida con el corral (todo a la misma altura). */
    private static final int CORRIDOR_HALF = 3;
    /** Profundidad de la base cónica cuando la plataforma flota sobre agua. */
    private static final int SUPPORT_DEPTH = 6;
    /** Ancho/altura del talud exterior que suaviza el borde en tierra. */
    private static final int SLOPE_WIDTH = 6;
    private static final int SLOPE_HEIGHT = 3;
    /** Extensión a recorrer en los barridos: la plataforma entera más el talud. */
    private static final int EXTENT = FARM_DISTANCE + FARM_RADIUS + SLOPE_WIDTH;
    /** Radio (desde el centro de la guarida) de las antorchas de almas. */
    private static final int TORCH_RADIUS = RADIUS - 3;

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
        double dFarm = Math.sqrt(dx * dx + z * z) - FARM_RADIUS;
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
        // ¿El punto cae sobre agua? Si sí, se construye una plataforma AL NIVEL del agua (no sumergida).
        int waterLevel = VillageGenerator.waterSurface(level, center.getX(), center.getZ());
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
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > RADIUS || dist <= CORRUPT_RADIUS) continue;
                if ((x * 31 + z * 17) % 5 != 0) continue;
                level.setBlock(new BlockPos(center.getX() + x, baseY, center.getZ() + z),
                        Blocks.SCULK_VEIN.defaultBlockState()
                                .setValue(MultifaceBlock.getFaceProperty(Direction.UP), true), 3);
            }
        }

        // 2) Espinas de hueso alrededor (pilares que "marcan" la guarida), sin invadir el corral.
        int spines = 16;
        for (int a = 0; a < spines; a++) {
            double angle = (a / (double) spines) * Math.PI * 2.0;
            int x = (int) Math.round(Math.cos(angle) * (RADIUS - 1));
            int z = (int) Math.round(Math.sin(angle) * (RADIUS - 1));
            if (insideFarm(x, z)) continue;
            int height = 2 + (a % 3);
            for (int i = 0; i < height; i++) {
                level.setBlock(new BlockPos(center.getX() + x, baseY + i, center.getZ() + z),
                        Blocks.BONE_BLOCK.defaultBlockState(), 3);
            }
        }

        // 3) Altar central 3x3 con el núcleo en el centro y CATALIZADORES de sculk alrededor: cuando un mob
        //    muere encima, la infección de sculk se expande sola (mecánica vanilla del catalizador).
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockState altar;
                if (Math.abs(x) == 1 && Math.abs(z) == 1) {
                    altar = Blocks.CRYING_OBSIDIAN.defaultBlockState(); // esquinas
                } else if (x == 0 && z == 0) {
                    altar = Blocks.SCULK.defaultBlockState(); // bajo el núcleo
                } else {
                    altar = Blocks.SCULK_CATALYST.defaultBlockState(); // expande la infección
                }
                level.setBlock(new BlockPos(center.getX() + x, baseY - 1, center.getZ() + z), altar, 3);
            }
        }
        BlockPos corePos = new BlockPos(center.getX(), baseY, center.getZ());
        level.setBlock(corePos, ModBlocks.LAIR_CORE_BLOCK.get().defaultBlockState(), 3);

        // 4) Tótems con calaveras en los cardinales (el del lado del corral se omite: lo ocupa el corral).
        int[][] cardinal = {{RADIUS - 2, 0}, {-RADIUS + 2, 0}, {0, RADIUS - 2}, {0, -RADIUS + 2}};
        for (int[] c : cardinal) {
            if (insideFarm(c[0], c[1])) continue;
            int x = center.getX() + c[0];
            int z = center.getZ() + c[1];
            level.setBlock(new BlockPos(x, baseY, z), Blocks.OAK_FENCE.defaultBlockState(), 3);
            level.setBlock(new BlockPos(x, baseY + 1, z), Blocks.OAK_FENCE.defaultBlockState(), 3);
            level.setBlock(new BlockPos(x, baseY + 2, z), Blocks.SKELETON_SKULL.defaultBlockState(), 3);
        }
        // Antorchas de almas en las diagonales, dentro de la plataforma.
        for (int[] c : new int[][]{{TORCH_RADIUS, TORCH_RADIUS}, {-TORCH_RADIUS, TORCH_RADIUS},
                {TORCH_RADIUS, -TORCH_RADIUS}, {-TORCH_RADIUS, -TORCH_RADIUS}}) {
            if (insideFarm(c[0], c[1])) continue;
            level.setBlock(new BlockPos(center.getX() + c[0], baseY, center.getZ() + c[1]),
                    Blocks.SOUL_TORCH.defaultBlockState(), 3);
        }

        // 5) Telarañas dispersas (ambiente de guarida), nunca dentro del corral.
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                if (Math.sqrt(x * x + z * z) > RADIUS - 1) continue;
                if ((x * 31 + z * 17) % 7 != 0 || (x == 0 && z == 0) || insideFarm(x, z)) continue;
                BlockPos webPos = new BlockPos(center.getX() + x, baseY, center.getZ() + z);
                if (level.getBlockState(webPos).isAir()) {
                    level.setBlock(webPos, Blocks.COBWEB.defaultBlockState(), 3);
                }
            }
        }

        // 6) Granja macabra: corral con ganado que el cultivador criará y sacrificará sobre el sculk.
        buildFarm(level, farmCenter, baseY);
        return corePos;
    }

    /** ¿La posición relativa (x, z) cae dentro del corral (o su borde)? */
    private static boolean insideFarm(int x, int z) {
        double dx = x - FARM_DISTANCE;
        return Math.sqrt(dx * dx + z * z) <= FARM_RADIUS + 1;
    }

    /**
     * Construye la granja macabra: un corral cercado, al mismo nivel que el suelo de la guarida, con una
     * abertura abierta hacia el corredor (para que el cultivador entre y salga) y ganado inicial. Dentro hay
     * un parche de sculk y catalizadores, para que lo que muera ahí alimente la infección como en el altar.
     */
    private static void buildFarm(ServerLevel level, BlockPos center, int baseY) {
        // Suelo: tierra muerta, con un parche central de sculk.
        for (int x = -FARM_RADIUS; x <= FARM_RADIUS; x++) {
            for (int z = -FARM_RADIUS; z <= FARM_RADIUS; z++) {
                int sq = x * x + z * z;
                if (sq > FARM_RADIUS * FARM_RADIUS) continue;
                int px = center.getX() + x;
                int pz = center.getZ() + z;
                Block floor = sq <= 2 ? Blocks.SCULK : Blocks.COARSE_DIRT;
                level.setBlock(new BlockPos(px, baseY - 1, pz), floor.defaultBlockState(), 3);
                for (int y = baseY; y < baseY + 3; y++) {
                    if (level.getBlockState(new BlockPos(px, y, pz)).isSolid()) {
                        level.setBlock(new BlockPos(px, y, pz), Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
        // Dos catalizadores dentro del corral: expanden la infección cuando muere un animal encima.
        level.setBlock(new BlockPos(center.getX() + 2, baseY - 1, center.getZ() + 2),
                Blocks.SCULK_CATALYST.defaultBlockState(), 3);
        level.setBlock(new BlockPos(center.getX() + 2, baseY - 1, center.getZ() - 2),
                Blocks.SCULK_CATALYST.defaultBlockState(), 3);

        // Cerca: SIEMPRE a baseY (el suelo ya está nivelado, así que no flota ni se ondula).
        int posts = 24;
        for (int a = 0; a < posts; a++) {
            double angle = (a / (double) posts) * Math.PI * 2.0;
            int x = center.getX() + (int) Math.round(Math.cos(angle) * FARM_RADIUS);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * FARM_RADIUS);
            level.setBlock(new BlockPos(x, baseY, z), Blocks.OAK_FENCE.defaultBlockState(), 3);
        }
        // Abertura hacia el corredor (lado -X del corral), con dos puertas abiertas: el cultivador pasa y el
        // jugador puede cerrarlas.
        BlockState openGate = Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.OPEN, true);
        level.setBlock(new BlockPos(center.getX() - FARM_RADIUS, baseY, center.getZ()), openGate, 3);
        level.setBlock(new BlockPos(center.getX() - FARM_RADIUS, baseY, center.getZ() + 1), openGate, 3);

        // Ganado inicial, dentro del corral.
        spawnAnimal(level, center, baseY, EntityType.COW, 2);
        spawnAnimal(level, center, baseY, EntityType.SHEEP, 2);
        spawnAnimal(level, center, baseY, EntityType.PIG, 1);
        spawnAnimal(level, center, baseY, EntityType.CHICKEN, 2);
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

    /** Busca tierra firme (no agua/lava) cerca de {@code origin}, para no generar la guarida en el agua. */
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
