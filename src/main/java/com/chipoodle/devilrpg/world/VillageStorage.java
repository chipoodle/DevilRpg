package com.chipoodle.devilrpg.world;

import com.chipoodle.devilrpg.DevilRpg;
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

import java.util.ArrayList;
import java.util.List;

/**
 * <b>Almacén de la aldea</b>: una construcción aparte (al lado de la plaza) con <b>cofres dobles</b> donde el
 * <b>constructor</b> —que también es recolector— va dejando lo que recoge por el pueblo (semillas sueltas, trigo,
 * harina de huesos, troncos, tablones...).
 * <p>
 * El almacén <b>crece</b>: cuando todos sus cofres están llenos, se coloca otro cofre doble en el siguiente hueco
 * del recinto (hasta {@link #MAX_COFRES} dobles). Así el pueblo no pierde lo que se cae al suelo.
 */
public final class VillageStorage {

    /**
     * Centro del almacén, relativo al centro de la aldea. Desde la migración 45 va <b>al este de la taberna</b>
     * (48, 21): antes estaba en (18, 18), justo delante de la <b>puerta principal</b> de la taberna (lo avisó el
     * jugador: <i>"el almacén está demasiado pegado a la puerta principal"</i>). Ahora está <b>al lado</b>, a la
     * espalda de la taberna y con sitio de sobra para que el cobertizo <b>crezca</b>.
     */
    private static final BlockPos OFFSET = new BlockPos(48, 0, 21);
    /**
     * Pares de cofres (cada par = un cofre doble), en el orden en que se van colocando: <b>seis</b> cofres dobles
     * (doce cofres) alrededor de las paredes norte y sur del cobertizo, que desde la migración 45 es de <b>7x7</b>
     * (antes 5x5 y tres dobles). Lo pidió el jugador: <i>"con suficiente espacio para que se vayan poniendo más
     * cofres conforme vaya creciendo"</i>.
     */
    private static final BlockPos[] COFRES = {
            new BlockPos(46, 1, 19), new BlockPos(47, 1, 19),
            new BlockPos(48, 1, 19), new BlockPos(49, 1, 19),
            new BlockPos(50, 1, 19), new BlockPos(51, 1, 19),
            new BlockPos(46, 1, 23), new BlockPos(47, 1, 23),
            new BlockPos(48, 1, 23), new BlockPos(49, 1, 23),
            new BlockPos(50, 1, 23), new BlockPos(51, 1, 23),
    };
    /** Cofres dobles como mucho (más allá, el almacén ya no da para más). */
    public static final int MAX_COFRES = COFRES.length / 2;

    /**
     * El almacén <b>viejo</b> (18, 18) con sus tres cofres dobles: lo usa la migración 45 para <b>vaciar</b> sus
     * cofres (pasando lo que tengan al nuevo) y <b>retirar</b> su cobertizo. Tirar un cofre tira su contenido al
     * suelo (mecánica del juego) y el almacén guarda lo que el pueblo ha ido recogiendo: no se puede perder.
     */
    private static final BlockPos OFFSET_VIEJO = new BlockPos(18, 0, 18);
    private static final BlockPos[] COFRES_VIEJOS = {
            new BlockPos(17, 1, 19), new BlockPos(18, 1, 19),
            new BlockPos(17, 1, 17), new BlockPos(18, 1, 17),
            new BlockPos(19, 1, 17), new BlockPos(19, 1, 18),
    };

    private VillageStorage() {
    }

    /** Centro del almacén de esa aldea. */
    public static BlockPos centro(BlockPos villageCenter) {
        return villageCenter.offset(OFFSET.getX(), 0, OFFSET.getZ());
    }

    /** Centro del almacén <b>viejo</b> (el de antes de la migración 45), para poder vaciarlo y retirarlo. */
    public static BlockPos centroViejo(BlockPos villageCenter) {
        return villageCenter.offset(OFFSET_VIEJO.getX(), 0, OFFSET_VIEJO.getZ());
    }

    /** Las posiciones (reales, a la cota del pueblo) de los cofres del almacén <b>viejo</b>. */
    public static List<BlockPos> cofresViejos(ServerLevel level, BlockPos villageCenter) {
        int nivel = VillageGenerator.cotaDeLaPlaza(level, villageCenter);
        List<BlockPos> fuera = new ArrayList<>();
        for (BlockPos rel : COFRES_VIEJOS) {
            fuera.add(new BlockPos(villageCenter.getX() + rel.getX(), nivel + 1, villageCenter.getZ() + rel.getZ()));
        }
        return fuera;
    }

    /**
     * Punto de apoyo para que un aldeano vaya al almacén: una casilla <b>del suelo del cobertizo</b> que no tiene
     * cofre encima (el pasillo del medio del cobertizo de 7x7). No se navega hacia el cofre (es sólido y el aldeano
     * se quedaría dando vueltas alrededor).
     */
    public static BlockPos puntoDeApoyo(ServerLevel level, BlockPos villageCenter) {
        int nivel = VillageGenerator.cotaDeLaPlaza(level, villageCenter);
        BlockPos c = centro(villageCenter);
        return new BlockPos(c.getX(), nivel + 1, c.getZ());
    }

    /** Distancia a la que un aldeano ya alcanza el almacén para descargar. */
    public static final double ALCANCE_ALMACEN = 5.0D;

    /**
     * Posición REAL de uno de los cofres del almacén: X/Z del hueco y <b>Y = cota del pueblo + 1</b> (encima del
     * suelo del cobertizo). OJO: nunca la Y del centro del objetivo, que puede caer en otra capa y dejar el cofre
     * FLOTANDO por encima del almacén (el bug que vio el jugador).
     */
    private static BlockPos pos(ServerLevel level, BlockPos villageCenter, BlockPos rel) {
        int nivel = VillageGenerator.cotaDeLaPlaza(level, villageCenter);
        return new BlockPos(villageCenter.getX() + rel.getX(), nivel + 1, villageCenter.getZ() + rel.getZ());
    }

    /** El primer cofre del almacén (el contenedor combinado si es doble), o {@code null} si no hay. */
    @Nullable
    public static Container almacen(ServerLevel level, BlockPos villageCenter) {
        for (BlockPos rel : COFRES) {
            BlockPos p = pos(level, villageCenter, rel);
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
            if (level.getBlockState(pos(level, villageCenter, rel)).getBlock() instanceof ChestBlock) {
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
            BlockPos a = pos(level, villageCenter, COFRES[i]);
            BlockPos b = pos(level, villageCenter, COFRES[i + 1]);
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

    /**
     * <b>Reparación</b>: quita los cofres que hayan quedado flotando por encima del almacén (bug de la Y del centro)
     * conservando lo que tuvieran dentro y los vuelve a poner en el suelo del cobertizo.
     */
    public static void repararCofresFlotantes(ServerLevel level, BlockPos villageCenter) {
        int nivel = VillageGenerator.cotaDeLaPlaza(level, villageCenter);
        BlockPos c = centro(villageCenter);
        List<ItemStack> dentro = new ArrayList<>();
        boolean saco = false;
        for (BlockPos q : BlockPos.betweenClosed(
                new BlockPos(c.getX() - 3, nivel - 2, c.getZ() - 3),
                new BlockPos(c.getX() + 3, nivel + 8, c.getZ() + 3))) {
            BlockState state = level.getBlockState(q);
            if (!(state.getBlock() instanceof ChestBlock)) {
                continue;
            }
            BlockPos p = q.immutable();
            if (p.getY() == nivel + 1) {
                continue; // está donde debe
            }
            if (level.getBlockEntity(p) instanceof Container contenedor) {
                for (int i = 0; i < contenedor.getContainerSize(); i++) {
                    if (!contenedor.getItem(i).isEmpty()) {
                        dentro.add(contenedor.getItem(i).copy());
                    }
                }
            }
            level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
            saco = true;
        }
        if (!saco) {
            return;
        }
        colocarSiguientePar(level, villageCenter);
        Container nuevo = almacen(level, villageCenter);
        if (nuevo != null) {
            for (ItemStack stack : dentro) {
                VillagePantry.guardar(nuevo, stack);
            }
        }
        DevilRpg.LOGGER.info("[Village] almacen: cofres flotantes recolocados al suelo ({} objeto(s) conservados)",
                dentro.size());
    }

    /** Cofre mirando al norte; {@code tipo} marca la mitad (LEFT al oeste, RIGHT al este) para el cofre doble. */
    private static BlockState cofre(ChestType tipo) {
        return Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH)
                .setValue(ChestBlock.TYPE, tipo);
    }
}
