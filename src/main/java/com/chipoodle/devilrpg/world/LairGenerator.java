package com.chipoodle.devilrpg.world;

import com.chipoodle.devilrpg.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Genera una <b>guarida</b> en el mundo: un claro corrupto que <b>cambia el terreno</b> a su alrededor
 * (tierra muerta, arena de almas, espinas de hueso, telarañas) con un <b>núcleo</b> en el centro
 * ({@link ModBlocks#LAIR_CORE_BLOCK}). Mientras el núcleo exista, la guarida spawnea enemigos (lo gestiona
 * {@code LairManager}); al destruirlo, la guarida queda limpiada. Es un lugar que el jugador puede asaltar.
 */
public final class LairGenerator {

    /** Radio de la base de la guarida. */
    private static final int RADIUS = 7;
    /** Radio del "núcleo corrupto" central (arena de almas). */
    private static final int CORRUPT_RADIUS = 4;

    private LairGenerator() {
    }

    /**
     * Genera la guarida centrada en {@code center}. Devuelve la posición del núcleo (el bloque asaltable),
     * o {@code null} si no hubo sitio válido.
     */
    public static BlockPos generate(ServerLevel level, BlockPos center) {
        int baseY = groundY(level, center.getX(), center.getZ());
        if (baseY <= level.getMinBuildHeight()) {
            return null;
        }

        // 1) Nivelar y "corromper" el terreno: aplanar el área y cambiar la superficie.
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > RADIUS) continue;
                int px = center.getX() + x;
                int pz = center.getZ() + z;
                int g = groundY(level, px, pz);
                // Rellenar por debajo hasta el nivel base (terreno muerto).
                for (int y = g; y < baseY; y++) {
                    level.setBlock(new BlockPos(px, y, pz), Blocks.COARSE_DIRT.defaultBlockState(), 3);
                }
                // Recortar lo que sobresalga (dejar el claro despejado).
                for (int y = baseY; y <= g; y++) {
                    if (level.getBlockState(new BlockPos(px, y, pz)).isSolid()) {
                        level.setBlock(new BlockPos(px, y, pz), Blocks.AIR.defaultBlockState(), 3);
                    }
                }
                // Superficie: arena de almas en el centro, tierra muerta alrededor.
                Block surface = dist <= CORRUPT_RADIUS ? Blocks.SOUL_SAND : Blocks.COARSE_DIRT;
                level.setBlock(new BlockPos(px, baseY - 1, pz), surface.defaultBlockState(), 3);
                // Despejar 4 bloques por encima de la superficie.
                for (int y = baseY; y < baseY + 4; y++) {
                    level.setBlock(new BlockPos(px, y, pz), Blocks.AIR.defaultBlockState(), 3);
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

        // 3) Altar central 3x3 con el núcleo en el centro.
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockState altar = (Math.abs(x) == 1 && Math.abs(z) == 1)
                        ? Blocks.CRYING_OBSIDIAN.defaultBlockState() // esquinas
                        : Blocks.BLACKSTONE.defaultBlockState();
                level.setBlock(new BlockPos(center.getX() + x, baseY - 1, center.getZ() + z), altar, 3);
            }
        }
        BlockPos corePos = new BlockPos(center.getX(), baseY, center.getZ());
        level.setBlock(corePos, ModBlocks.LAIR_CORE_BLOCK.get().defaultBlockState(), 3);

        // 4) Tótems con calaveras y antorchas de alma en los cardinales.
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
        return corePos;
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
