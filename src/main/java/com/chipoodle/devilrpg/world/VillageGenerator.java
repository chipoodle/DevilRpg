package com.chipoodle.devilrpg.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

/**
 * Genera una aldea simple (unas cabañas + aldeanos) en un punto del mundo. Es la "primera aldea" a la
 * que apunta el objetivo del druida: ahí el jugador ayuda a los aldeanos a defenderse.
 */
public final class VillageGenerator {

    private VillageGenerator() {
    }

    /** Genera 3 cabañas y 3 aldeanos alrededor del centro. */
    public static void generate(ServerLevel level, BlockPos center) {
        // Cabañas (estructura simple) alrededor del centro.
        hut(level, center.offset(-6, 0, 0));
        hut(level, center.offset(6, 0, -1));
        hut(level, center.offset(0, 0, 6));

        // Aldeanos vanilla.
        spawnVillager(level, center.offset(-5, 0, 0), VillagerProfession.FARMER);
        spawnVillager(level, center.offset(5, 0, -1), VillagerProfession.WEAPONSMITH);
        spawnVillager(level, center.offset(0, 0, 5), VillagerProfession.CLERIC);
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
        // Techo.
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                level.setBlock(base.offset(x, 2, z), Blocks.SPRUCE_PLANKS.defaultBlockState(), 3);
            }
        }
        // Una mesa de trabajo dentro.
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
}
