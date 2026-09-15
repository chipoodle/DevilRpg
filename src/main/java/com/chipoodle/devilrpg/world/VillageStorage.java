package com.chipoodle.devilrpg.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.jetbrains.annotations.Nullable;

/**
 * <b>Almacén de la aldea</b>: una construcción aparte (al lado de la plaza) con <b>cofres dobles</b> donde el
 * <b>constructor</b> —que también es recolector— va dejando lo que recoge por el pueblo (semillas sueltas, trigo,
 * harina de huesos, troncos, tablones...).
 * <p>
 * El almacén <b>crece</b>: cuando todos sus cofres están llenos, se coloca otro cofre doble en el siguiente hueco
 * del recinto (hasta {@link #MAX_COFRES} dobles). Así el pueblo no pierde lo que se cae al suelo.
 */
public final class VillageStorage {

    /** Centro del almacén, relativo al centro de la aldea (una esquina libre del recinto). */
    private static final BlockPos OFFSET = new BlockPos(18, 0, 18);
    /** Pares de cofres (cada par = un cofre doble), en el orden en que se van colocando. */
    private static final BlockPos[] COFRES = {
            new BlockPos(17, 1, 19), new BlockPos(18, 1, 19),
            new BlockPos(17, 1, 17), new BlockPos(18, 1, 17),
            new BlockPos(19, 1, 17), new BlockPos(19, 1, 18),
    };
    /** Cofres dobles como mucho (más allá, el almacén ya no da para más). */
    public static final int MAX_COFRES = COFRES.length / 2;

    private VillageStorage() {
    }

    /** Centro del almacén de esa aldea. */
    public static BlockPos centro(BlockPos villageCenter) {
        return villageCenter.offset(OFFSET.getX(), 0, OFFSET.getZ());
    }

    /** El primer cofre del almacén (el contenedor combinado si es doble), o {@code null} si no hay. */
    @Nullable
    public static Container almacen(ServerLevel level, BlockPos villageCenter) {
        for (BlockPos rel : COFRES) {
            BlockPos p = villageCenter.offset(rel.getX(), rel.getY(), rel.getZ());
            BlockState state = level.getBlockState(p);
            if (state.getBlock() instanceof ChestBlock cofre) {
                Container c = ChestBlock.getContainer(cofre, state, level, p, true);
                if (c != null) {
                    return c;
                }
            }
        }
        return null;
    }

    /** ¿Cuántos cofres dobles hay ya colocados? */
    public static int cofresColocados(ServerLevel level, BlockPos villageCenter) {
        int n = 0;
        for (BlockPos rel : COFRES) {
            if (level.getBlockState(villageCenter.offset(rel.getX(), rel.getY(), rel.getZ()))
                    .getBlock() instanceof ChestBlock) {
                n++;
            }
        }
        return n;
    }

    /** ¿Están llenos TODOS los cofres colocados? (entonces toca crecer) */
    public static boolean lleno(ServerLevel level, BlockPos villageCenter) {
        Container c = almacen(level, villageCenter);
        if (c == null) {
            return true;
        }
        for (int i = 0; i < c.getContainerSize(); i++) {
            if (c.getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Guarda en el almacén lo que quepa y devuelve lo que no cupo. */
    public static ItemStack guardar(ServerLevel level, BlockPos villageCenter, ItemStack stack) {
        Container c = almacen(level, villageCenter);
        return c == null ? stack : VillagePantry.guardar(c, stack);
    }

    /**
     * Coloca el siguiente <b>par</b> de cofres (un cofre doble) en el primer hueco libre del recinto. Las dos
     * mitades se marcan LEFT/RIGHT a mano: al colocarlas con setBlock no pasa por la colocación de vanilla y sin eso
     * quedarían dos cofres sueltos en vez de un doble.
     */
    public static boolean colocarSiguientePar(ServerLevel level, BlockPos villageCenter) {
        for (int i = 0; i + 1 < COFRES.length; i += 2) {
            BlockPos a = villageCenter.offset(COFRES[i].getX(), COFRES[i].getY(), COFRES[i].getZ());
            BlockPos b = villageCenter.offset(COFRES[i + 1].getX(), COFRES[i + 1].getY(), COFRES[i + 1].getZ());
            if (level.getBlockState(a).getBlock() instanceof ChestBlock
                    || level.getBlockState(b).getBlock() instanceof ChestBlock) {
                continue; // ese par ya está puesto
            }
            level.setBlockAndUpdate(a, cofre(ChestType.LEFT));
            level.setBlockAndUpdate(b, cofre(ChestType.RIGHT));
            return true;
        }
        return false;
    }

    /** Cofre mirando al norte; {@code tipo} marca la mitad (LEFT al oeste, RIGHT al este) para el cofre doble. */
    private static BlockState cofre(ChestType tipo) {
        return Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH)
                .setValue(ChestBlock.TYPE, tipo);
    }
}
