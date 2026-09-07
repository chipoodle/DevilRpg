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
        int y = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn).getY();
        BlockPos center = new BlockPos(spawn.getX(), y, spawn.getZ());

        // Si la roca de los clérigos ya está puesta, el círculo ya se generó antes.
        if (level.getBlockState(center).is(ModBlocks.LORE_STONE_BLOCK.get())) {
            return;
        }

        // Anillo de piedras alrededor del centro (el doble de radio, más columnas y cada una de 3 bloques).
        int radius = 8;
        int columns = 20;
        for (int a = 0; a < columns; a++) {
            double angle = (a / (double) columns) * Math.PI * 2.0;
            int x = (int) Math.round(center.getX() + Math.cos(angle) * radius);
            int z = (int) Math.round(center.getZ() + Math.sin(angle) * radius);
            net.minecraft.world.level.block.Block block = (a % 2 == 0 ? Blocks.COBBLESTONE : Blocks.MOSSY_COBBLESTONE);
            // Columna de 3 bloques de alto.
            for (int h = 0; h < 3; h++) {
                level.setBlock(new BlockPos(x, center.getY() + h, z), block.defaultBlockState(), 3);
            }
        }

        // Restos de clérigos (bloques de hueso) cerca del anillo.
        level.setBlock(center.offset(2, 0, 0), Blocks.BONE_BLOCK.defaultBlockState(), 3);
        level.setBlock(center.offset(-2, 0, 1), Blocks.BONE_BLOCK.defaultBlockState(), 3);
        level.setBlock(center.offset(0, 0, -2), Blocks.BONE_BLOCK.defaultBlockState(), 3);

        // Roca de los clérigos (la que se lee) en el centro.
        level.setBlock(center, ModBlocks.LORE_STONE_BLOCK.get().defaultBlockState(), 3);
    }
}
