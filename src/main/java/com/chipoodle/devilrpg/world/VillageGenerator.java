package com.chipoodle.devilrpg.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Genera una aldea simple (unas cabañas + aldeanos + una valla de madera) en un punto del mundo,
 * buscando tierra firme (evita el agua). Es la "primera aldea" a la que apunta el objetivo del druida.
 */
public final class VillageGenerator {

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

    /** Genera las cabañas, los aldeanos y la valla alrededor del centro. */
    public static void generate(ServerLevel level, BlockPos center) {
        hut(level, center.offset(-6, 0, 0));
        hut(level, center.offset(6, 0, -1));
        hut(level, center.offset(0, 0, 6));

        spawnVillager(level, center.offset(-5, 0, 0), VillagerProfession.FARMER);
        spawnVillager(level, center.offset(5, 0, -1), VillagerProfession.WEAPONSMITH);
        spawnVillager(level, center.offset(0, 0, 5), VillagerProfession.CLERIC);

        fence(level, center);
    }

    /** Valla de madera alrededor de la aldea que ayuda a contener momentáneamente a los monstruos. */
    private static void fence(ServerLevel level, BlockPos center) {
        int radius = 10;
        int posts = 28;
        for (int a = 0; a < posts; a++) {
            double angle = (a / (double) posts) * Math.PI * 2.0;
            int x = (int) Math.round(center.getX() + Math.cos(angle) * radius);
            int z = (int) Math.round(center.getZ() + Math.sin(angle) * radius);
            // Dejar una entrada (un hueco) hacia el centro (ángulo ~0).
            if (Math.abs(Math.cos(angle)) > 0.95 && Math.sin(angle) > -0.3) {
                continue;
            }
            int y = groundY(level, x, z);
            level.setBlock(new BlockPos(x, y + 1, z), Blocks.OAK_FENCE.defaultBlockState(), 3);
        }
    }

    /** Cabaña sencilla: paredes de madera, techo y una mesa de trabajo dentro. */
    private static void hut(ServerLevel level, BlockPos base) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                boolean wall = Math.abs(x) == 2 || Math.abs(z) == 2;
                level.setBlock(base.offset(x, 0, z), wall ? Blocks.OAK_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(base.offset(x, 1, z), Blocks.OAK_PLANKS.defaultBlockState(), 3);
            }
        }
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                level.setBlock(base.offset(x, 2, z), Blocks.SPRUCE_PLANKS.defaultBlockState(), 3);
            }
        }
        level.setBlock(base.offset(0, 0, 0), Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
        level.setBlock(base.offset(0, 1, 0), Blocks.AIR.defaultBlockState(), 3);
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

    private static int groundY(ServerLevel level, int x, int z) {
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z)).getY();
    }
}
