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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Genera una aldea simple (cabañas con puerta y cama + aldeanos + valla de madera con puertas) en un
 * punto del mundo. Antes de construir se limpia la vegetación del interior y se nivela el terreno a un
 * nivel base (rellenando los hoyos con tierra, suavizando la pendiente sin aplanarlo del todo). Las
 * cabañas y la valla se asientan al terreno nivelado, la valla se cierra de forma continua y conectada
 * (sin huecos en las diagonales) para que los aldeanos puedan transitar.
 */
public final class VillageGenerator {

    /** Radio de la valla (más grande para dar espacio libre de movimiento en el interior). */
    private static final int FENCE_RADIUS = 22;

    /** Radio del área que se nivela alrededor del centro de la aldea. */
    private static final int LEVEL_RADIUS = 12;

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
        levelTerrain(level, center, LEVEL_RADIUS);
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
     * Nivela el terreno del área de la aldea: toma la altura base (mediana de las alturas de suelo) y
     * rellena con tierra las columnas que estén por debajo. Así se suaviza la pendiente y se rellenan los
     * hoyos, pero no se aplana todo (las zonas más altas se conservan).
     */
    private static void levelTerrain(ServerLevel level, BlockPos center, int radius) {
        List<Integer> heights = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                heights.add(groundY(level, center.getX() + x, center.getZ() + z));
            }
        }
        Collections.sort(heights);
        int baseY = heights.get(heights.size() / 2); // mediana

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int g = groundY(level, center.getX() + x, center.getZ() + z);
                // Rellenar solo hasta el nivel base; las columnas por encima se dejan (pendiente suave).
                for (int y = g; y < baseY; y++) {
                    level.setBlock(new BlockPos(center.getX() + x, y, center.getZ() + z), Blocks.DIRT.defaultBlockState(), 3);
                }
            }
        }
    }

    /**
     * Valla de madera cerrada y CONECTADA alrededor de la aldea. El anillo se dibuja con pasos
     * cardinales (nunca diagonales) para que cada valla tenga vecino ortogonal y no queden huecos; donde
     * habría un salto diagonal se inserta la valla intermedia. Se dejan puertas de valla en los accesos
     * (norte/sur/este/oeste).
     */
    private static void fence(ServerLevel level, BlockPos center) {
        int r = FENCE_RADIUS;
        // Puntos del anillo en orden angular, deduplicando consecutivos.
        List<BlockPos> pts = new ArrayList<>();
        int samples = 720;
        for (int a = 0; a <= samples; a++) {
            double ang = (a / (double) samples) * Math.PI * 2.0;
            int x = (int) Math.round(center.getX() + Math.cos(ang) * r);
            int z = (int) Math.round(center.getZ() + Math.sin(ang) * r);
            BlockPos p = new BlockPos(x, 0, z);
            if (pts.isEmpty() || !pts.get(pts.size() - 1).equals(p)) {
                pts.add(p);
            }
        }
        // Conectar con pasos cardinales (cada tramo nunca deja un hueco diagonal).
        Set<Long> cells = new HashSet<>();
        for (int i = 0; i < pts.size(); i++) {
            BlockPos from = pts.get(i);
            BlockPos to = pts.get((i + 1) % pts.size());
            connect(level, from, to, cells);
        }
        // Puertas de valla en los accesos de los caminos (ejes cardinales).
        for (long k : cells) {
            int x = (int) (k >> 32);
            int z = (int) (k & 0xFFFFFFFFL);
            int y = groundY(level, x, z);
            BlockState state;
            boolean northSouthGate = Math.abs(z - center.getZ()) == r && x == center.getX();
            boolean eastWestGate = Math.abs(x - center.getX()) == r && z == center.getZ();
            if (northSouthGate) {
                state = Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.NORTH);
            } else if (eastWestGate) {
                state = Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.EAST);
            } else {
                state = Blocks.OAK_FENCE.defaultBlockState();
            }
            level.setBlock(new BlockPos(x, y + 1, z), state, 3);
        }
    }

    /** Añade los bloques de un tramo recto (solo pasos cardinales) al conjunto de celdas de la valla. */
    private static void connect(ServerLevel level, BlockPos from, BlockPos to, Set<Long> cells) {
        cells.add(key(from.getX(), from.getZ()));
        int x = from.getX();
        int z = from.getZ();
        while (x != to.getX() || z != to.getZ()) {
            if (x != to.getX()) {
                x += Math.signum(to.getX() - x);
            } else if (z != to.getZ()) {
                z += Math.signum(to.getZ() - z);
            }
            cells.add(key(x, z));
        }
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    /** Limpia la vegetación (árboles, flores, hierba, cactus, cañas, etc.) dentro del radio, por columna. */
    private static void clearVegetation(ServerLevel level, BlockPos center, int radius) {
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                int surface = groundY(level, x, z);
                // Escanear desde justo debajo de la superficie (para plantas bajas) hasta 16 bloques arriba
                // (para árboles). Así cubre el terreno real, no un rango fijo alrededor del centro.
                for (int y = surface - 2; y <= surface + 16; y++) {
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
     * Cabaña asentada al terreno nivelado con 3 bloques de alto en el interior, puerta al frente, cama de
     * 2 bloques (pie + cabeza), escaleras en la entrada cuando queda alto sobre el suelo, y —si el centro
     * está bajo agua— piso sobre el agua con pilares de valla que bajan al menos 3 bloques.
     */
    private static BlockPos hut(ServerLevel level, BlockPos base) {
        // 1) Suelo de la cabaña = el del centro (ya nivelado), para no apilar tierra hasta un máximo.
        int floorY = groundY(level, base.getX(), base.getZ());
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
        // Techo (nivel 4 = floorY+3), cubriendo todo el hueco.
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                level.setBlock(new BlockPos(base.getX() + x, floorY + 3, base.getZ() + z), Blocks.SPRUCE_PLANKS.defaultBlockState(), 3);
            }
        }
        // 6) Puerta en el frente (z=-2, mirando hacia afuera), cama de 2 bloques (pie + cabeza), y escaleras.
        BlockPos doorBottom = new BlockPos(base.getX(), floorY, base.getZ() - 2);
        door(level, doorBottom);
        bed(level, new BlockPos(base.getX(), floorY, base.getZ()));
        entranceStairs(level, doorBottom);
        return new BlockPos(base.getX(), floorY, base.getZ());
    }

    /** Coloca una cama completa (pie + cabeza) mirando hacia el sur (dentro de la cabaña). */
    private static void bed(ServerLevel level, BlockPos footPos) {
        BlockState foot = Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT);
        BlockState head = Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD);
        level.setBlock(footPos, foot, 3);
        level.setBlock(footPos.relative(Direction.SOUTH), head, 3);
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
