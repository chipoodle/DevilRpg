package com.chipoodle.devilrpg.world;

import com.chipoodle.devilrpg.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Genera una <b>guarida</b> en el mundo: un claro corrupto que <b>cambia el terreno</b> a su alrededor
 * (tierra muerta, arena de almas, espinas de hueso, telarañas) con un <b>núcleo</b> en el centro
 * ({@link ModBlocks#LAIR_CORE_BLOCK}). Mientras el núcleo exista, la guarida spawnea enemigos (lo gestiona
 * {@code LairManager}); al destruirlo, la guarida queda limpiada. Es un lugar que el jugador puede asaltar.
 * <p>
 * Si el punto cae sobre agua, la guarida se construye sobre una <b>plataforma al nivel del agua</b> (con
 * base cónica), así nunca queda sumergida. En tierra se nivela el claro y se le añade un <b>talud</b>
 * exterior para que parezca una meseta natural y no un cubo.
 */
public final class LairGenerator {

    /** Radio de la base de la guarida. */
    private static final int RADIUS = 7;
    /** Radio del "núcleo corrupto" central (arena de almas). */
    private static final int CORRUPT_RADIUS = 4;
    /** Profundidad de la base cónica cuando la guarida flota sobre agua. */
    private static final int SUPPORT_DEPTH = 6;
    /** Ancho/altura del talud exterior que suaviza el borde en tierra. */
    private static final int SLOPE_WIDTH = 6;
    private static final int SLOPE_HEIGHT = 3;

    private LairGenerator() {
    }

    /**
     * Genera la guarida centrada en {@code center}. Devuelve la posición del núcleo (el bloque asaltable),
     * o {@code null} si no hubo sitio válido.
     */
    public static BlockPos generate(ServerLevel level, BlockPos center) {
        // ¿El punto cae sobre agua? Si sí, se construye una plataforma AL NIVEL del agua (no sumergida).
        int waterLevel = VillageGenerator.waterSurface(level, center.getX(), center.getZ());
        int baseY; // posición transitable de la guarida (el suelo sólido queda en baseY-1)
        if (waterLevel >= 0) {
            baseY = waterLevel + 1;
            buildFloatingBase(level, center, RADIUS, waterLevel);
        } else {
            baseY = levelTerrain(level, center, RADIUS);
            addOuterSlope(level, center, RADIUS, baseY);
        }
        if (baseY <= level.getMinBuildHeight()) {
            return null;
        }

        // 1) Marcar la superficie: SCULK en el centro (infección), tierra muerta alrededor con venas de
        //    sculk. Los catalizadores del altar (abajo) expanden la infección cuando muere un mob encima.
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > RADIUS) continue;
                int px = center.getX() + x;
                int pz = center.getZ() + z;
                Block surface = dist <= CORRUPT_RADIUS ? Blocks.SCULK : Blocks.COARSE_DIRT;
                level.setBlock(new BlockPos(px, baseY - 1, pz), surface.defaultBlockState(), 3);
                // Venas de sculk salpicando el terreno circundante.
                if (dist > CORRUPT_RADIUS && (x * 31 + z * 17) % 5 == 0) {
                    level.setBlock(new BlockPos(px, baseY, pz),
                            Blocks.SCULK_VEIN.defaultBlockState()
                                    .setValue(MultifaceBlock.getFaceProperty(Direction.UP), true), 3);
                }
                for (int y = baseY; y < baseY + 4; y++) {
                    BlockState above = level.getBlockState(new BlockPos(px, y, pz));
                    if (above.isSolid()) {
                        level.setBlock(new BlockPos(px, y, pz), Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }

        // 2) Espinas de hueso alrededor (pilares que "marcan" la guarida).
        for (int a = 0; a < 12; a++) {
            double angle = (a / 12.0) * Math.PI * 2.0;
            int x = (int) Math.round(center.getX() + Math.cos(angle) * (RADIUS - 1));
            int z = (int) Math.round(center.getZ() + Math.sin(angle) * (RADIUS - 1));
            int height = 2 + (a % 3);
            for (int i = 0; i < height; i++) {
                level.setBlock(new BlockPos(x, baseY + i, z), Blocks.BONE_BLOCK.defaultBlockState(), 3);
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

        // 4) Tótems con calaveras en los cardinales.
        int[][] cardinal = {{RADIUS - 2, 0}, {-RADIUS + 2, 0}, {0, RADIUS - 2}, {0, -RADIUS + 2}};
        for (int[] c : cardinal) {
            int x = center.getX() + c[0];
            int z = center.getZ() + c[1];
            level.setBlock(new BlockPos(x, baseY, z), Blocks.OAK_FENCE.defaultBlockState(), 3);
            level.setBlock(new BlockPos(x, baseY + 1, z), Blocks.OAK_FENCE.defaultBlockState(), 3);
            level.setBlock(new BlockPos(x, baseY + 2, z), Blocks.SKELETON_SKULL.defaultBlockState(), 3);
        }
        for (int[] c : new int[][]{{CORRUPT_RADIUS + 2, CORRUPT_RADIUS + 2}, {-CORRUPT_RADIUS - 2, CORRUPT_RADIUS + 2},
                {CORRUPT_RADIUS + 2, -CORRUPT_RADIUS - 2}, {-CORRUPT_RADIUS - 2, -CORRUPT_RADIUS - 2}}) {
            level.setBlock(new BlockPos(center.getX() + c[0], baseY, center.getZ() + c[1]),
                    Blocks.SOUL_TORCH.defaultBlockState(), 3);
        }

        // 5) Telarañas dispersas (ambiente de guarida).
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                if (Math.sqrt(x * x + z * z) > RADIUS - 1) continue;
                if ((x * 31 + z * 17) % 7 == 0 && (x != 0 || z != 0)) {
                    BlockPos webPos = new BlockPos(center.getX() + x, baseY, center.getZ() + z);
                    if (level.getBlockState(webPos).isAir()) {
                        level.setBlock(webPos, Blocks.COBWEB.defaultBlockState(), 3);
                    }
                }
            }
        }

        // 6) Granja macabra: un corral con ganado que el cultivador criará y sacrificará sobre el sculk.
        buildFarm(level, center.offset(RADIUS + 6, 0, 0), baseY);
        return corePos;
    }

    /**
     * Construye la granja macabra: un corral cercado con una puerta y ganado inicial (vacas, ovejas,
     * cerdos y pollos). El cultivador del sculk se encarga de criarlos y sacrificar algunos sobre el sculk.
     */
    private static void buildFarm(ServerLevel level, BlockPos center, int baseY) {
        int r = 5;
        for (int a = 0; a < 28; a++) {
            double angle = (a / 28.0) * Math.PI * 2.0;
            int x = (int) Math.round(center.getX() + Math.cos(angle) * r);
            int z = (int) Math.round(center.getZ() + Math.sin(angle) * r);
            int y = groundY(level, x, z);
            // Una puerta de valla en un lado para que el cultivador pueda entrar.
            boolean gate = (a == 7);
            level.setBlock(new BlockPos(x, y, z),
                    gate ? Blocks.OAK_FENCE_GATE.defaultBlockState() : Blocks.OAK_FENCE.defaultBlockState(), 3);
        }
        // Suelo del corral: tierra muerta para que combine con la guarida.
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (Math.sqrt(x * x + z * z) > r) continue;
                int px = center.getX() + x;
                int pz = center.getZ() + z;
                int g = groundY(level, px, pz);
                level.setBlock(new BlockPos(px, g - 1, pz), Blocks.COARSE_DIRT.defaultBlockState(), 3);
            }
        }
        // Ganado inicial.
        spawnAnimal(level, center, EntityType.COW, 2);
        spawnAnimal(level, center, EntityType.SHEEP, 2);
        spawnAnimal(level, center, EntityType.PIG, 1);
        spawnAnimal(level, center, EntityType.CHICKEN, 2);
    }

    /** Spawnea {@code count} animales del tipo dado alrededor del centro del corral. */
    private static void spawnAnimal(ServerLevel level, BlockPos center, net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.animal.Animal> type, int count) {
        for (int i = 0; i < count; i++) {
            int x = center.getX() + level.random.nextInt(5) - 2;
            int z = center.getZ() + level.random.nextInt(5) - 2;
            int y = groundY(level, x, z);
            var animal = type.create(level, null, new BlockPos(x, y, z),
                    net.minecraft.world.entity.MobSpawnType.MOB_SUMMONED, true, true);
            if (animal != null) {
                animal.moveTo(x + 0.5D, y, z + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
                animal.setPersistenceRequired();
                level.addFreshEntity(animal);
            }
        }
    }

    /**
     * Nivela el claro a la mediana de alturas y devuelve el nivel transitable resultante.
     */
    private static int levelTerrain(ServerLevel level, BlockPos center, int radius) {
        List<Integer> heights = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                heights.add(groundY(level, center.getX() + x, center.getZ() + z));
            }
        }
        Collections.sort(heights);
        int baseY = heights.get(heights.size() / 2);
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int px = center.getX() + x;
                int pz = center.getZ() + z;
                int g = groundY(level, px, pz);
                for (int y = g; y < baseY; y++) {
                    level.setBlock(new BlockPos(px, y, pz), Blocks.DIRT.defaultBlockState(), 3);
                }
                for (int y = baseY + 1; y < g; y++) {
                    level.setBlock(new BlockPos(px, y, pz), Blocks.AIR.defaultBlockState(), 3);
                }
                level.setBlock(new BlockPos(px, baseY - 1, pz), Blocks.DIRT.defaultBlockState(), 3);
            }
        }
        return baseY;
    }

    /**
     * Talud exterior de la guarida (en tierra): pendiente escalonada en el borde para que parezca una
     * meseta natural y no un cubo.
     */
    private static void addOuterSlope(ServerLevel level, BlockPos center, int innerRadius, int baseY) {
        int outer = innerRadius + SLOPE_WIDTH;
        for (int x = -outer; x <= outer; x++) {
            for (int z = -outer; z <= outer; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist <= innerRadius || dist > outer) continue;
                double fraction = (dist - innerRadius) / (double) SLOPE_WIDTH;
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
     * Construye la base de la guarida cuando cae sobre agua: plataforma circular con el suelo A NIVEL del
     * agua (reemplazando la capa superior de agua) y, debajo, una base cónica de tierra/piedra que se
     * estrecha (como una isla), para que no se vea sumergida ni como un cubo cuadrado.
     */
    private static void buildFloatingBase(ServerLevel level, BlockPos center, int radius, int waterLevel) {
        // 1) Plataforma: rellenar/recortar cada columna hasta el nivel del agua (suelo sólido en waterLevel).
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > radius) continue;
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
        // 2) Base cónica (circular) que se estrecha hacia abajo, como una isla/montaña.
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > radius) continue;
                int px = center.getX() + x;
                int pz = center.getZ() + z;
                for (int depth = 1; depth <= SUPPORT_DEPTH; depth++) {
                    double shrink = depth * 0.9;
                    if (dist > radius - shrink) continue;
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
