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
import net.minecraft.world.level.block.StairBlock;

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

    /** Genera las cabañas, los aldeanos, los caminos y la valla alrededor del centro. */
    public static void generate(ServerLevel level, BlockPos center) {
        BlockPos h0 = hut(level, center.offset(-8, 0, 0));
        BlockPos h1 = hut(level, center.offset(8, 0, -1));
        BlockPos h2 = hut(level, center.offset(0, 0, 8));
        paths(level, center, h0, h1, h2);

        spawnVillager(level, center.offset(-7, 0, 0), VillagerProfession.FARMER);
        spawnVillager(level, center.offset(7, 0, -1), VillagerProfession.WEAPONSMITH);
        spawnVillager(level, center.offset(0, 0, 7), VillagerProfession.CLERIC);

        fence(level, center);
    }

    /** Camino de grava entre el centro y cada cabaña. */
    private static void paths(ServerLevel level, BlockPos center, BlockPos h0, BlockPos h1, BlockPos h2) {
        line(level, center, h0);
        line(level, center, h1);
        line(level, center, h2);
    }

    private static void line(ServerLevel level, BlockPos from, BlockPos to) {
        int steps = Math.max(Math.abs(to.getX() - from.getX()), Math.abs(to.getZ() - from.getZ()));
        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 1.0 : (double) i / steps;
            int x = (int) Math.round(from.getX() + (to.getX() - from.getX()) * t);
            int z = (int) Math.round(from.getZ() + (to.getZ() - from.getZ()) * t);
            int y = groundY(level, x, z);
            level.setBlock(new BlockPos(x, y, z), Blocks.GRAVEL.defaultBlockState(), 3);
        }
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
     * Cabaña asentada al terreno con 4 bloques de altura, escaleras en la entrada cuando queda alto sobre
     * el suelo, y —si el centro está bajo agua— piso sobre el agua con pilares de valla que bajan al
     * menos 3 bloques y terminan en un bloque de madera.
     */
    private static BlockPos hut(ServerLevel level, BlockPos base) {
        // 1) Suelo más alto del área 5x5 para apoyar la cabaña sin que flote.
        int floorY = groundY(level, base.getX(), base.getZ());
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                floorY = Math.max(floorY, groundY(level, base.getX() + x, base.getZ() + z));
            }
        }
        // 2) Si el centro está bajo agua, subir el piso sobre la superficie y sostener la casa con pilares.
        int waterSurface = waterTop(level, base.getX(), base.getZ());
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
        // 5) Paredes (3 niveles) + techo: hueco para la puerta en el frente z=-2. Altura total 4 bloques.
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                boolean wall = (Math.abs(x) == 2 || Math.abs(z) == 2) && !(x == 0 && z == -2);
                level.setBlock(new BlockPos(base.getX() + x, floorY, base.getZ() + z),
                        wall ? Blocks.OAK_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(new BlockPos(base.getX() + x, floorY + 1, base.getZ() + z), Blocks.OAK_PLANKS.defaultBlockState(), 3);
                level.setBlock(new BlockPos(base.getX() + x, floorY + 2, base.getZ() + z), Blocks.OAK_PLANKS.defaultBlockState(), 3);
            }
        }
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                level.setBlock(new BlockPos(base.getX() + x, floorY + 3, base.getZ() + z), Blocks.SPRUCE_PLANKS.defaultBlockState(), 3);
            }
        }
        // 6) Puerta en el frente (z=-2), cama dentro, y escaleras si el suelo exterior queda muy abajo.
        door(level, new BlockPos(base.getX(), floorY, base.getZ() - 2));
        level.setBlock(new BlockPos(base.getX(), floorY, base.getZ()),
                Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT), 3);
        entranceStairs(level, new BlockPos(base.getX(), floorY, base.getZ() - 2));
        return new BlockPos(base.getX(), floorY, base.getZ());
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

    /** Escaleras de roble frente a la puerta cuando el suelo exterior está más de 2 bloques por debajo del piso. */
    private static void entranceStairs(ServerLevel level, BlockPos doorBottom) {
        int outsideGround = groundY(level, doorBottom.getX(), doorBottom.getZ() - 1);
        int rise = doorBottom.getY() - outsideGround;
        if (rise < 2) return;
        // Escalón en la dirección de la puerta (frente = -z), ascendiendo hacia el piso.
        for (int i = 0; i < rise; i++) {
            BlockPos stairPos = new BlockPos(doorBottom.getX(), outsideGround + i, doorBottom.getZ() - 1 - i);
            level.setBlock(stairPos, Blocks.OAK_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH), 3);
        }
    }

    /** Superficie del agua (Y del bloque de agua más alto) en una columna, o floorY si no hay agua. */
    private static int waterTop(ServerLevel level, int x, int z) {
        int y = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z)).getY();
        for (int yy = y; yy > y - 48; yy--) {
            BlockState bs = level.getBlockState(new BlockPos(x, yy, z));
            if (bs.getBlock() == Blocks.WATER) {
                return yy;
            }
            if (bs.isSolid() && bs.getBlock() != Blocks.WATER) {
                return yy + 1;
            }
        }
        return y;
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
