package com.chipoodle.devilrpg.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashSet;
import java.util.Set;

/**
 * Genera una aldea simple (cabañas con puerta y cama + aldeanos + valla de madera con puertas) en un
 * punto del mundo. Las cabañas y la valla se asientan al terreno real (aplanando la base para no flotar
 * en pendientes), se evitar el agua, se limpia la vegetación del interior y la valla se cierra de forma
 * continua para que los aldeanos puedan transitar.
 */
public final class VillageGenerator {

    /** Radio de la valla (más grande para dar espacio libre de movimiento en el interior). */
    private static final int FENCE_RADIUS = 22;

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

    /** Genera las cabañas, los aldeanos, los caminos y la valla alrededor del centro. */
    public static void generate(ServerLevel level, BlockPos center) {
        clearVegetation(level, center, FENCE_RADIUS);
        BlockPos h0 = hut(level, center.offset(-9, 0, -1));
        BlockPos h1 = hut(level, center.offset(9, 0, -2));
        BlockPos h2 = hut(level, center.offset(0, 0, 9));
        paths(level, center, h0, h1, h2);

        // Aldeanos justo frente a la puerta de cada cabaña (más alejados del centro para dejar espacio
        // libre y permitir que la valla sea más grande).
        spawnVillager(level, center.offset(-9, 0, -4), VillagerProfession.FARMER);
        spawnVillager(level, center.offset(9, 0, -5), VillagerProfession.WEAPONSMITH);
        spawnVillager(level, center.offset(0, 0, 7), VillagerProfession.CLERIC);

        fence(level, center);
    }

    /** Camino de tierra apisonada (el de pala) entre el centro y cada cabaña. */
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
            level.setBlock(new BlockPos(x, y, z), Blocks.DIRT_PATH.defaultBlockState(), 3);
        }
    }

    /**
     * Valla de madera CLOSA y conectada alrededor de la aldea: un círculo continuo de 1 bloque, con
     * puertas de valla en los accesos (norte/sur/este/oeste) para que los aldeanos puedan salir.
     */
    private static void fence(ServerLevel level, BlockPos center) {
        int r = FENCE_RADIUS;
        Set<Long> ring = new HashSet<>();
        for (int x = -r; x <= r; x++) {
            int zTop = (int) Math.round(Math.sqrt(r * r - x * x));
            ring.add(key(x, zTop));
            ring.add(key(x, -zTop));
        }
        for (int z = -r; z <= r; z++) {
            int xSide = (int) Math.round(Math.sqrt(r * r - z * z));
            ring.add(key(xSide, z));
            ring.add(key(-xSide, z));
        }
        // Puertas de valla en los ejes (accesos de los caminos).
        Set<Long> gates = Set.of(key(0, r), key(0, -r), key(r, 0), key(-r, 0));

        for (long k : ring) {
            int x = (int) (k >> 32);
            int z = (int) k;
            int y = groundY(level, center.getX() + x, center.getZ() + z);
            BlockState state;
            if (gates.contains(k)) {
                // En los accesos norte/sur la valla corre este-oeste; en este/oeste corre norte-sur.
                Direction facing = (Math.abs(z) == r) ? Direction.NORTH : Direction.EAST;
                state = Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, facing);
            } else {
                state = Blocks.OAK_FENCE.defaultBlockState();
            }
            level.setBlock(new BlockPos(center.getX() + x, y + 1, center.getZ() + z), state, 3);
        }
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    /** Limpia la vegetación (árboles, flores, hierba, cactus, cañas, etc.) dentro del radio. */
    private static void clearVegetation(ServerLevel level, BlockPos center, int radius) {
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                for (int y = center.getY() - 1; y <= center.getY() + 18; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) continue;
                    Block block = state.getBlock();
                    boolean vegetation = state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)
                            || block instanceof BushBlock || block instanceof GrowingPlantBlock
                            || block == Blocks.CACTUS || block == Blocks.BAMBOO || block == Blocks.BAMBOO_SAPLING
                            || block == Blocks.SUGAR_CANE;
                    if (vegetation) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    /**
     * Cabaña asentada al terreno con 3 bloques de alto en el interior, puerta al frente, escaleras en la
     * entrada cuando queda alto sobre el suelo, y —si el centro está bajo agua— piso sobre el agua con
     * pilares de valla que bajan al menos 3 bloques y terminan en un bloque de madera.
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
        // Techo (nivel 4 = floorY+3), solo sobre el perímetro y cubriendo todo el hueco.
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                level.setBlock(new BlockPos(base.getX() + x, floorY + 3, base.getZ() + z), Blocks.SPRUCE_PLANKS.defaultBlockState(), 3);
            }
        }
        // 6) Puerta en el frente (z=-2, mirando hacia afuera), cama dentro, y escaleras si el suelo exterior queda muy abajo.
        BlockPos doorBottom = new BlockPos(base.getX(), floorY, base.getZ() - 2);
        door(level, doorBottom);
        level.setBlock(new BlockPos(base.getX(), floorY, base.getZ()),
                Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT), 3);
        entranceStairs(level, doorBottom);
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
}
