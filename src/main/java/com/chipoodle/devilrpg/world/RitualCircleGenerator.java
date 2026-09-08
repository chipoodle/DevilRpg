package com.chipoodle.devilrpg.world;

import com.chipoodle.devilrpg.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Genera el círculo ritual de invocación del druida en el punto de inicio del mundo (el "ancla").
 * <p>
 * Consiste en un anillo de piedras (estilo Stonehenge), unos restos de clérigos (bloques de hueso) y la
 * {@link com.chipoodle.devilrpg.block.LoreStoneBlock} en el centro. Se genera una sola vez por mundo
 * (el invocador comprueba si ya existe la roca).
 */
public final class RitualCircleGenerator {

    private RitualCircleGenerator() {
    }

    /** Genera el círculo en el spawn del mundo si aún no se generó (si la roca ya existe, no hace nada). */
    public static void generate(ServerLevel level, BlockPos spawn) {
        BlockPos center = new BlockPos(spawn.getX(), groundY(level, spawn.getX(), spawn.getZ()), spawn.getZ());

        // Si la roca de los clérigos ya está puesta, el círculo ya se generó antes.
        if (level.getBlockState(center).is(ModBlocks.LORE_STONE_BLOCK.get())) {
            return;
        }

        // Anillo de piedras alrededor del centro: cada columna se asienta en su propio terreno.
        int radius = 12;
        int columns = 24;
        for (int a = 0; a < columns; a++) {
            double angle = (a / (double) columns) * Math.PI * 2.0;
            int x = (int) Math.round(center.getX() + Math.cos(angle) * radius);
            int z = (int) Math.round(center.getZ() + Math.sin(angle) * radius);
            net.minecraft.world.level.block.Block block = (a % 2 == 0 ? Blocks.COBBLESTONE : Blocks.MOSSY_COBBLESTONE);

            // Cada 5ª columna se hace un "trilito": dos pilares (3 bloques) + barra horizontal encima.
            boolean trilithon = (a % 5 == 0);
            if (trilithon) {
                // Segundo pilar, desplazado 1 bloque en la tangente del anillo (-sin, cos).
                int tx = x + (int) Math.round(-Math.sin(angle));
                int tz = z + (int) Math.round(Math.cos(angle));
                // Base común = el más bajo de los dos terrenos, para que la barra quede horizontal y
                // ambos pilares queden asentados (el del lado más alto queda un poco "encajado").
                int baseY = Math.min(groundY(level, x, z), groundY(level, tx, tz));
                for (int h = 0; h < 3; h++) {
                    level.setBlock(new BlockPos(x, baseY + h, z), block.defaultBlockState(), 3);
                    level.setBlock(new BlockPos(tx, baseY + h, tz), block.defaultBlockState(), 3);
                }
                // Barra horizontal (lintel) sobre ambos pilares.
                level.setBlock(new BlockPos(x, baseY + 3, z), block.defaultBlockState(), 3);
                level.setBlock(new BlockPos(tx, baseY + 3, tz), block.defaultBlockState(), 3);
            } else {
                // Columna simple de 3 bloques asentada en su propio terreno.
                int baseY = groundY(level, x, z);
                for (int h = 0; h < 3; h++) {
                    level.setBlock(new BlockPos(x, baseY + h, z), block.defaultBlockState(), 3);
                }
            }
        }

        // Restos de clérigos (bloques de hueso) cerca del centro, asentados.
        level.setBlock(center.offset(2, 0, 0).atY(groundY(level, center.getX() + 2, center.getZ())), Blocks.BONE_BLOCK.defaultBlockState(), 3);
        level.setBlock(center.offset(-2, 0, 1).atY(groundY(level, center.getX() - 2, center.getZ() + 1)), Blocks.BONE_BLOCK.defaultBlockState(), 3);
        level.setBlock(center.offset(0, 0, -2).atY(groundY(level, center.getX(), center.getZ() - 2)), Blocks.BONE_BLOCK.defaultBlockState(), 3);

        // Roca de los clérigos (la que se lee) en el centro, asentada.
        level.setBlock(center, ModBlocks.LORE_STONE_BLOCK.get().defaultBlockState(), 3);
    }

    /** Y del suelo (top de terreno sólido) en una columna (x, z). */
    private static int groundY(ServerLevel level, int x, int z) {
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z)).getY();
    }
}
