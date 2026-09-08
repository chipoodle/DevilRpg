package com.chipoodle.devilrpg.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Genera una aldea simple (cabañas con puerta y cama + aldeanos + valla de madera con puertas) en un
 * punto del mundo. Las cabañas y la valla se asientan al terreno real (aplanando la base para no flotar
 * en pendientes) y se evita el agua.
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
        hut(level, center.offset(-8, 0, 0));
        hut(level, center.offset(8, 0, -1));
        hut(level, center.offset(0, 0, 8));

        spawnVillager(level, center.offset(-7, 0, 0), VillagerProfession.FARMER);
        spawnVillager(level, center.offset(7, 0, -1), VillagerProfession.WEAPONSMITH);
        spawnVillager(level, center.offset(0, 0, 7), VillagerProfession.CLERIC);

        fence(level, center);
    }

    /** Valla de madera alrededor de la aldea, con un par de puertas de valla. */
    private static void fence(ServerLevel level, BlockPos center) {
        int radius = 14;
        int posts = 40;
        for (int a = 0; a < posts; a++) {
            double angle = (a / (double) posts) * Math.PI * 2.0;
            int x = (int) Math.round(center.getX() + Math.cos(angle) * radius);
            int z = (int) Math.round(center.getZ() + Math.sin(angle) * radius);
            int y = groundY(level, x, z);
            boolean gate = (a % 20 == 0); // par de puertas de valla en lados opuestos
            BlockState state = gate ? Blocks.OAK_FENCE_GATE.defaultBlockState() : Blocks.OAK_FENCE.defaultBlockState();
            level.setBlock(new BlockPos(x, y + 1, z), state, 3);
        }
    }

    /**
     * Cabaña asentada al terreno: aplanar la base (rellenar las columnas bajas con tierra) y construir
     * sobre ella, con puerta y cama dentro.
     */
    private static void hut(ServerLevel level, BlockPos base) {
        // 1) Suelo más alto del área 5x5 para apoyar la cabaña sin que flote.
        int floorY = groundY(level, base.getX(), base.getZ());
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                floorY = Math.max(floorY, groundY(level, base.getX() + x, base.getZ() + z));
            }
        }
        // 2) Rellenar las columnas más bajas hasta floorY para aplanar el terreno.
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                int g = groundY(level, base.getX() + x, base.getZ() + z);
                for (int y = g; y < floorY; y++) {
                    level.setBlock(new BlockPos(base.getX() + x, y, base.getZ() + z), Blocks.DIRT.defaultBlockState(), 3);
                }
            }
        }
        // 3) Paredes + techo (hueco para la puerta en el frente z=-2).
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                boolean wall = (Math.abs(x) == 2 || Math.abs(z) == 2) && !(x == 0 && z == -2);
                level.setBlock(new BlockPos(base.getX() + x, floorY, base.getZ() + z),
                        wall ? Blocks.OAK_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(new BlockPos(base.getX() + x, floorY + 1, base.getZ() + z), Blocks.OAK_PLANKS.defaultBlockState(), 3);
            }
        }
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                level.setBlock(new BlockPos(base.getX() + x, floorY + 2, base.getZ() + z), Blocks.SPRUCE_PLANKS.defaultBlockState(), 3);
            }
        }
        // 4) Puerta en el frente (z=-2) y cama dentro.
        door(level, new BlockPos(base.getX(), floorY, base.getZ() - 2));
        level.setBlock(new BlockPos(base.getX(), floorY, base.getZ()),
                Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT), 3);
    }

    private static void door(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.SOUTH).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 3);
        level.setBlock(pos.above(), Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.SOUTH).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
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
}
