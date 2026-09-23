package com.chipoodle.devilrpg.world;

import com.chipoodle.devilrpg.DevilRpg;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
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

    /**
     * Las posiciones (reales, a la cota del pueblo) de los cofres del almacén <b>viejo</b>.
     * <p>
     * OJO con la Y: el cobertizo viejo (18,18) se levantó con el suelo <b>en la cota</b> (la plataforma de un bloque
     * de antes de la migración 69), así que sus cofres están en {@code cota + 1}. Es geometría <b>heredada</b>: no se
     * iguala a la de {@link #pos} porque esta lista sirve para <b>vaciar</b> lo que hay en el cobertizo viejo de una
     * partida sin migrar, no para saber dónde van los cofres nuevos.
     */
    public static List<BlockPos> cofresViejos(ServerLevel level, BlockPos villageCenter) {
        int nivel = VillageGenerator.cotaDeLaPlaza(level, villageCenter);
        List<BlockPos> fuera = new ArrayList<>();
        for (BlockPos rel : COFRES_VIEJOS) {
            fuera.add(new BlockPos(villageCenter.getX() + rel.getX(), nivel + 1, villageCenter.getZ() + rel.getZ()));
        }
        return fuera;
    }

    /**
     * Punto de apoyo para que un aldeano vaya al almacén: una casilla <b>LIBRE del suelo</b> del cobertizo. No se
     * navega hacia el cofre (es sólido y el aldeano se quedaría dando vueltas alrededor).
     * <p>
     * OJO CON EL CENTRO: el cobertizo lleva sus <b>postes</b> en una rejilla de 3 en 3 ({@code dx,dz = -3, 0, +3}), así
     * que <b>el centro exacto es un POSTE de tronco</b>. Medido en el guardado del jugador (aldea 2): el punto de
     * apoyo caía en el tronco de (1462,121,1435) y el herrero —que va y viene del almacén en cada pieza— navegaba
     * hacia un bloque <b>sólido</b>, que es justo el fallo que el pueblo ya documentó con el ahumador del kiosco:
     * <i>la navegación no puede llegar a un bloque sólido y el aldeano se queda dando vueltas alrededor</i>. Ahora se
     * devuelve la primera casilla del suelo con <b>sitio para pararse</b> (nada sólido a la capa que se pisa ni
     * encima, y suelo firme debajo), empezando por el centro y abriéndose en anillos.
     * <p>
     * <b>Y LA CASILLA ES LA COTA, no {@code cota + 1}</b> (I1/I95): la capa que se pisa es la cota y el suelo del
     * cobertizo va en {@code cota - 1}, así que la casilla de pie está en {@code nivel}. Antes se devolvía
     * {@code nivel + 1} porque el cobertizo se construía con el suelo EN la cota (una plataforma de un bloque entero,
     * como la del kiosco) pero <b>sin el escalón</b> que sí tiene el kiosco en sus cuatro entradas: ningún aldeano
     * podía subir (el juego solo sube 0,6 andando), así que el punto era <b>inalcanzable</b> y lo dejaban aparcado 5
     * min una y otra vez. Medido en el guardado del jugador (aldea 0, centro {@code 470,646}, cota 63): el suelo del
     * cobertizo era {@code stone_bricks} en {@code y=63} con el suelo del pueblo (césped) en {@code y=62}, y el punto
     * de apoyo devuelto era {@code BlockPos{x=517, y=64, z=666}}. Ver {@link VillageGenerator#bajarElAlmacenAlSuelo}
     * para las aldeas ya construidas.
     */
    public static BlockPos puntoDeApoyo(ServerLevel level, BlockPos villageCenter) {
        int nivel = VillageGenerator.cotaDeLaPlaza(level, villageCenter);
        BlockPos c = centro(villageCenter);
        for (int r = 0; r <= RADIO_APOYO; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue; // el interior ya se miró en los anillos anteriores
                    }
                    BlockPos p = new BlockPos(c.getX() + dx, nivel, c.getZ() + dz);
                    if (casillaLibre(level, p)) {
                        return p;
                    }
                }
            }
        }
        return new BlockPos(c.getX(), nivel, c.getZ()); // sin hueco libre: se devuelve el centro (no hay nada mejor)
    }

    /**
     * Hasta dónde se buscan casillas libres alrededor del centro del cobertizo. Con 4 anillos se sale de la rejilla de
     * postes y del doble cofre sin alejarse del almacén (el cobertizo mide 7x7).
     */
    private static final int RADIO_APOYO = 4;

    /**
     * ¿Esa casilla es suelo del cobertizo con <b>sitio para pararse</b>? Hace falta suelo firme debajo y nada sólido
     * en la propia casilla ni encima: así valen el pasillo y las plantas, y quedan fuera los postes y los cofres.
     */
    private static boolean casillaLibre(ServerLevel level, BlockPos p) {
        return level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                && !level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty();
    }

    /** Distancia a la que un aldeano ya alcanza el almacén para descargar. */
    public static final double ALCANCE_ALMACEN = 5.0D;

    /**
     * <b>Reserva de LEÑA del almacén</b>: el fuego del pueblo —el <b>ahumador</b> del cocinero y la
     * <b>fundición</b> del herrero— <b>nunca</b> baja de aquí.
     * <p>
     * Hace falta porque la madera es <b>dos cosas a la vez</b> en esta aldea: la <b>materia prima</b> del herrero
     * (1 tronco → 4 tablones, y de ahí el escudo de la milicia, el arco y las flechas) y el <b>combustible</b> de
     * todo lo que quema. Sin reserva, el fuego se come la madera que el leñador trajo y el pueblo se queda sin
     * escudos, arcos ni flechas; medido en el guardado del jugador (aldea 2), el almacén tenía <b>157 troncos de
     * acacia</b>, así que con 32 de reserva sobra margen y el fuego solo gasta el excedente.
     */
    public static final int RESERVA_LENA = 32;

    /** ¿Ese objeto es <b>LEÑA</b>? Cualquier tronco vale de combustible (es lo que el leñador sube al almacén). */
    public static boolean esLena(ItemStack s) {
        return !s.isEmpty() && s.is(net.minecraft.tags.ItemTags.LOGS);
    }

    /** Cuánta leña hay en el almacén (reserva incluida). */
    public static int cuentaLena(ServerLevel level, BlockPos villageCenter) {
        return cuenta(level, villageCenter, VillageStorage::esLena);
    }

    /** ¿Queda leña <b>por encima de la reserva</b> para quemar? (si no, el fuego no se enciende). */
    public static boolean hayLenaParaQuemar(ServerLevel level, BlockPos villageCenter) {
        return cuentaLena(level, villageCenter) > RESERVA_LENA;
    }

    /**
     * Saca del almacén leña <b>para quemarla</b>, respetando la {@link #RESERVA_LENA reserva}: devuelve {@code null}
     * si no hay excedente (y entonces el aparato no funciona: es lo que pidió el jugador —*"los aparatos donde se
     * tenga que quemar necesitan ir por logs al almacén para que se use de combustible y funcionen"*—).
     */
    @Nullable
    public static ItemStack quitarLena(ServerLevel level, BlockPos villageCenter, int cuantas) {
        int excedente = cuentaLena(level, villageCenter) - RESERVA_LENA;
        if (excedente <= 0) {
            return null;
        }
        return quitar(level, villageCenter, VillageStorage::esLena, Math.min(Math.max(1, cuantas), excedente));
    }

    /**
     * Troncos con los que <b>arranca</b> el almacén de una aldea (la <b>remesa inicial de madera</b>): dos pilas
     * completas. Lo pidió el jugador: *"considera entonces que inicialmente tenga la aldea suficiente madera en el
     * almacén, unos 128 logs"*.
     */
    public static final int REMESA_INICIAL_TRONCOS = 128;

    /**
     * <b>Remesa inicial de madera</b>: deja {@link #REMESA_INICIAL_TRONCOS} troncos en el almacén de una aldea que
     * acaba de nacer.
     * <p>
     * Hace falta porque la madera es <b>tres cosas</b> en este pueblo y ninguna se puede improvisar: el
     * <b>combustible</b> del ahumador del cocinero y de la fragua del herrero, la <b>materia prima</b> de la sierra
     * (tablones y palos → escudos, arcos y flechas) y la <b>obra</b> del propio pueblo. Una aldea recién fundada no
     * tiene ni un tronco hasta que el <b>leñador</b> tale los primeros árboles y los baje, así que el ahumador y la
     * fragua nacerían apagados (y con la {@link #RESERVA_LENA reserva de 32} no habría nada que quemar sin comerse
     * la madera del herrero).
     * <p>
     * <b>Solo se le pone al almacén VACÍO</b>, con la misma regla que la remesa de la despensa
     * ({@link VillagePantry#remesaInicial}): así una aldea ya en marcha —con lo que ha juntado el recolector— no
     * recibe nada, y esto no es un grifo de troncos. Se llama desde el bloque de "asegurar" del latido, justo
     * después de {@link VillageGenerator#asegurarAlmacen} (que es quien coloca el primer cofre doble), así que en la
     * misma pasada en que el almacén nace ya tiene su madera dentro.
     */
    public static void remesaInicialDeMadera(ServerLevel level, BlockPos villageCenter) {
        Container caja = almacen(level, villageCenter);
        if (caja == null || VillagePantry.contar(caja, s -> true) > 0) {
            return; // sin almacén, o con cosas dentro (aldea en marcha): no se toca
        }
        for (int pila = 0; pila < REMESA_INICIAL_TRONCOS / 64; pila++) {
            ItemStack resto = VillagePantry.guardar(caja, new ItemStack(Items.OAK_LOG, 64));
            if (!resto.isEmpty()) {
                break; // no cupo (raro: el almacén está recién hecho): se deja lo que entró
            }
        }
        DevilRpg.LOGGER.info("[Village] almacen: remesa inicial de madera ({} troncos de roble para el fuego del"
                + " cocinero, la fragua del herrero y su sierra)", cuentaLena(level, villageCenter));
    }

    /**
     * Posición REAL de uno de los cofres del almacén: X/Z del hueco y <b>Y = la cota del pueblo</b> (encima del suelo
     * del cobertizo, que va en {@code cota - 1}: I1/I95). OJO: nunca la Y del centro del objetivo, que puede caer en
     * otra capa y dejar el cofre FLOTANDO por encima del almacén (el bug que vio el jugador).
     */
    private static BlockPos pos(ServerLevel level, BlockPos villageCenter, BlockPos rel) {
        int nivel = VillageGenerator.cotaDeLaPlaza(level, villageCenter);
        return new BlockPos(villageCenter.getX() + rel.getX(), nivel, villageCenter.getZ() + rel.getZ());
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
    /**
     * <b>Saca</b> del almacén la primera cosa que cumpla el filtro (hasta {@code cuantas} unidades) y la devuelve, o
     * {@code null} si no hay nada. Lo usa el <b>clérigo</b> para coger sus ingredientes de las pociones: el almacén es
     * donde el recolector deja el botín del pueblo (pepitas de oro, ojos de araña, pólvora) y donde el jugador trae lo
     * que no se puede conseguir aquí (verruga del Nether, polvo de blaze, botellas de agua).
     */
    @Nullable
    public static ItemStack quitar(ServerLevel level, BlockPos villageCenter, java.util.function.Predicate<ItemStack> filtro,
                                   int cuantas) {
        Container caja = almacen(level, villageCenter);
        if (caja == null) {
            return null;
        }
        for (int i = 0; i < caja.getContainerSize(); i++) {
            ItemStack stack = caja.getItem(i);
            if (stack.isEmpty() || !filtro.test(stack)) {
                continue;
            }
            int n = Math.min(Math.max(1, cuantas), stack.getCount());
            ItemStack sacado = stack.copyWithCount(n);
            stack.shrink(n);
            caja.setChanged();
            return sacado;
        }
        return null;
    }

    /**
     * <b>Cuántas unidades</b> hay en el almacén que cumplan el filtro (para saber si el pueblo tiene para una receta:
     * por ejemplo 8 pepitas de oro para la zanahoria dorada del clérigo).
     */
    public static int cuenta(ServerLevel level, BlockPos villageCenter, java.util.function.Predicate<ItemStack> filtro) {
        Container caja = almacen(level, villageCenter);
        if (caja == null) {
            return 0;
        }
        int n = 0;
        for (int i = 0; i < caja.getContainerSize(); i++) {
            ItemStack stack = caja.getItem(i);
            if (!stack.isEmpty() && filtro.test(stack)) {
                n += stack.getCount();
            }
        }
        return n;
    }

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
     * <p>
     * La capa buena es <b>la cota</b> (el suelo del cobertizo va en {@code cota - 1}: I1/I95). El recuadro sigue
     * mirando de {@code cota - 2} hacia arriba para que también recoja los cofres del cobertizo <b>viejo</b> (los que
     * están en {@code cota + 1}, la plataforma de antes de la migración 69): así, si {@link
     * VillageGenerator#bajarElAlmacenAlSuelo} no llegara a correr, el latido acaba bajándolos igual.
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
            if (p.getY() == nivel) {
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
        int alSuelo = 0;
        if (nuevo != null) {
            for (ItemStack stack : dentro) {
                // LO QUE NO QUEPA, AL SUELO (nunca se borra nada del pueblo): aquí se juntan los cofres de un
                // cobertizo entero y el almacén puede haber vuelto a nacer con UN solo par, así que lo que sobre se
                // deja en el suelo del cobertizo, donde lo recoge el recolector. Antes se descartaba en silencio.
                ItemStack sobra = VillagePantry.guardar(nuevo, stack);
                if (!sobra.isEmpty()) {
                    Block.popResource(level, puntoDeApoyo(level, villageCenter), sobra);
                    alSuelo += sobra.getCount();
                }
            }
        }
        DevilRpg.LOGGER.info("[Village] almacen: cofres flotantes recolocados al suelo ({} objeto(s) conservados{})",
                dentro.size(), alSuelo > 0 ? ", " + alSuelo + " al suelo del cobertizo" : "");
    }

    /** Cofre mirando al norte; {@code tipo} marca la mitad (LEFT al oeste, RIGHT al este) para el cofre doble. */
    private static BlockState cofre(ChestType tipo) {
        return Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH)
                .setValue(ChestBlock.TYPE, tipo);
    }
}
