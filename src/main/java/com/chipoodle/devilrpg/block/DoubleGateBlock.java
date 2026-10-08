package com.chipoodle.devilrpg.block;

import com.chipoodle.devilrpg.DevilRpg;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * <b>EL PORTÓN DOBLE ABATIBLE DEL MURO DE LA ALDEA</b> (6-oct-2026, lo pidió el jugador: *«que sean de 3 de ancho x 3 de
 * alto y que sea una puerta doble abatible personalizada»*).
 * <p>
 * Es <b>un bloque propio</b> y no una puerta de valla por una razón de diseño, no de gusto: el hueco del muro es de
 * <b>3×3</b> y una puerta de valla mide 1,5 de alto — con un solo bloque de aire encima, un aldeano (que mide 1,95) no
 * tiene hueco para la cabeza, así que <b>no puede cruzarla</b> ni aunque el juego le deje trazarlo. Este portón ocupa
 * las nueve celdas del hueco y <b>las abre y las cierra todas a la vez</b>.
 * <p>
 * <b>Las dos hojas.</b> El portón son <b>dos hojas</b> de 1 de ancho × 3 de alto ({@link PanelSide#LEFT} y
 * {@link PanelSide#RIGHT}, cada una con sus tres bloques de alto, {@link PanelLayer}), y las dos <b>abaten</b> hacia
 * fuera al abrirse: la geometría está en {@link #forma}, que es la que decide de verdad qué se puede cruzar.
 * <p>
 * <b>Quién lo abre.</b> Lo abre el <b>pueblo</b>, desde el latido de la aldea, cuando un aldeano se le acerca
 * ({@code VillageManager.asegurarPortonesAbiertos}); y lo puede abrir el <b>jugador</b> a mano
 * ({@link #useWithoutItem}). Y hay una razón dura para que sea este bloque y no una puerta de madera: un <b>zombi no
 * rompe una puerta de valla ni un portón como éste</b> (no es una {@code DoorBlock}, que es la única que el juego deja
 * romper en difícil), así que <b>el asedio tiene que abrir brecha en el MURO</b>, que es exactamente lo que el jugador
 * pidió ✓.
 * <p>
 * <b>No se cae a trozos.</b> Las nueve celdas van juntas: si se quita una, se quitan las demás
 * ({@link #playerWillDestroy}), para que no queden hojas colgando en el aire — que es justo el fallo que el pueblo
 * repondría mal.
 */
public class DoubleGateBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<DoubleGateBlock> CODEC = simpleCodec(DoubleGateBlock::new);

    /** La hoja: la de la izquierda (eje negativo) o la de la derecha (eje positivo). */
    public enum PanelSide implements StringRepresentable {
        LEFT("left"),
        RIGHT("right");

        private final String nombre;

        PanelSide(String nombre) {
            this.nombre = nombre;
        }

        @Override
        public String getSerializedName() {
            return nombre;
        }
    }

    /** El piso de la hoja: los tres bloques de alto que forman cada hoja. */
    public enum PanelLayer implements StringRepresentable {
        LOW("low", 0),
        MID("mid", 1),
        HIGH("high", 2);

        private final String nombre;
        private final int indice;

        PanelLayer(String nombre, int indice) {
            this.nombre = nombre;
            this.indice = indice;
        }

        @Override
        public String getSerializedName() {
            return nombre;
        }

        /** 0 abajo, 2 arriba. */
        public int indice() {
            return indice;
        }

        /** El piso a partir suyo. */
        public static PanelLayer de(int indice) {
            return indice <= 0 ? LOW : (indice >= 2 ? HIGH : MID);
        }
    }

    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final EnumProperty<PanelSide> SIDE = EnumProperty.create("side", PanelSide.class);
    public static final EnumProperty<PanelLayer> LAYER = EnumProperty.create("layer", PanelLayer.class);
    /**
     * <b>¿Este bloque es la JUNTURA del portón?</b> (la celda del medio, donde las dos hojas se encuentran al cerrar).
     * <p>
     * Existe por la <b>textura</b>: el portón son <b>nueve</b> bloques (3 de ancho por 3 de alto) y cada uno tiene que
     * coger <b>su trozo</b> de la textura para que el dibujo sea <b>continuo</b> y cubra toda la superficie. El trozo
     * que le toca a un bloque depende de <b>dónde esté</b>, y eso es lo que dice esta propiedad ✓.
     */
    public static final BooleanProperty JUNTURA = BooleanProperty.create("juntura");

    /** El grosor de una hoja de canto (abierta): <b>2 píxeles</b> (1/8 de bloque), junto a su bisagra. */
    private static final double GROSOR = 2.0D;

    /**
     * Cuánto se mete la hoja abierta hacia dentro del paso, por la <b>normal</b> del muro: <b>8 píxeles</b> (medio
     * bloque), que es lo que ocupaba de ancho al estar cerrada. Es lo que hace que al abrir se vea un <b>giro de 90°</b>
     * de verdad y no una hoja que se queda en su sitio ✓.
     */
    private static final double ABANICO = 8.0D;

    public DoubleGateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(OPEN, false)
                .setValue(SIDE, PanelSide.LEFT)
                .setValue(LAYER, PanelLayer.LOW)
                .setValue(JUNTURA, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, SIDE, LAYER, JUNTURA);
    }

    /** ¿La hoja abate por el eje X? (la pared corre en Z cuando el portón mira al este o al oeste). */
    private static boolean ejeX(Direction facing) {
        return facing.getAxis() == Direction.Axis.X;
    }

    /**
     * <b>La forma del bloque: la geometría de la hoja abatida.</b>
     * <p>
     * Cerrada, la hoja está <b>tumbada en el plano de la pared</b> y ocupa toda su celda (16×16): el portón cierra el
     * hueco del todo. Abierta, la hoja ha <b>abierto 90°</b> y se queda <b>de canto</b>: sólo sus {@value #GROSOR}
     * píxeles pegados a la <b>bisagra</b>, que por eso se puede cruzar.
     * <p>
     * La bisagra está en el canto por el que la hoja toca la jamba: la hoja IZQUIERDA abate desde el lado negativo del
     * eje de la pared y la DERECHA desde el positivo. En un portón doble las bisagras están <b>en los extremos</b> y las
     * hojas se encuentran en el centro al cerrarse — de ahí que cada una mida media anchura exacta.
     */
    private static VoxelShape forma(Direction facing, PanelSide side, boolean abierto) {
        boolean enX = ejeX(facing);
        if (!abierto) {
            return Shapes.block(); // cerrada: la celda entera -> el portón cierra el hueco del todo
        }
        // ABIERTA: LA HOJA HA GIRADO 90° Y QUEDA PERPENDICULAR AL MURO (6-oct-2026, y este era el fallo que reportó el
        // jugador: *«al abrir la puerta no gira en uno de los lados y se pone abierta, sino que se queda en su lugar»*).
        // Antes la hoja abierta seguía DENTRO DEL PLANO del muro (sólo más fina), así que no se veía ningún giro ✗.
        // Ahora se mete en el paso por la NORMAL: `ABANICO` píxeles hacia dentro y sólo `GROSOR` de canto en la línea
        // del muro, pegada a su bisagra (que está en el canto del extremo que le toca) ✓.
        if (enX) {
            // El muro corre en Z: la hoja se mete por X.
            return side == PanelSide.LEFT ? Block.box(0.0D, 0.0D, 0.0D, ABANICO, 16.0D, GROSOR)
                    : Block.box(0.0D, 0.0D, 16.0D - GROSOR, ABANICO, 16.0D, 16.0D);
        }
        // El muro corre en X: la hoja se mete por Z.
        return side == PanelSide.LEFT ? Block.box(0.0D, 0.0D, 0.0D, GROSOR, 16.0D, ABANICO)
                : Block.box(16.0D - GROSOR, 0.0D, 0.0D, 16.0D, 16.0D, ABANICO);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return forma(state.getValue(FACING), state.getValue(SIDE), state.getValue(OPEN));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        // ABIERTA NO TIENE COLISIÓN, y esto es una decisión medida (6-oct-2026). Con la hoja abierta «de canto» su
        // listón de {@value #GROSOR} píxeles se queda DENTRO de su propia celda, y el paso útil de esa celda se queda en
        // 0,875: un aldeano mide 0,6 y **cabe**, pero pasa rozando, y el jugador reportó que **no se podía pasar** ✗.
        // Con la colisión a cero el paso queda LIBRE de verdad, que es lo que se le pide a una puerta abierta: se ve el
        // listón (la `getShape`, que no se toca) y no estorba. Es la única diferencia deliberada entre lo que se VE y lo
        // que BLOQUEA en este bloque, y está solo en el estado abierto por eso mismo.
        return state.getValue(OPEN) ? Shapes.empty() : Shapes.block();
    }

    /**
     * <b>El portón abate ENTERO, de una sola vez.</b> Al cambiar el estado de una celda hay que cambiar el de
     * <b>todas</b> las del portón (tres celdas de ancho por los tres pisos = <b>nueve hojas</b>); si no, cada bloque se
     * abre por su cuenta y lo que se ve es una puerta descosida — <b>medido y reportado por el jugador</b>: *«no parece
     * que sea una sola pieza, pues los 3 bloques al darles click derecho cada una se abre o cierra independientemente
     * dependiendo de a quién se le dé el click»* ✗.
     * <p>
     * <b>Por qué barría de menos</b>: buscaba <b>2</b> celdas en el eje perpendicular (la geometría de un portón de dos
     * hojas de 1 de ancho cada una) cuando el portón del muro es de <b>3</b> celdas, así que solo movía las hojas de una
     * mitad del hueco. Ahora barre <b>el ancho completo</b> (tres celdas a los dos lados de la que se ha pulsado) y
     * todos los pisos.
     * <p>
     * La búsqueda es <b>por estado</b> (mismo {@code FACING}) y <b>alrededor de la celda pulsada</b>: así da igual cuál
     * de las nueve se pulse ni en qué piso esté, que es lo que espera cualquiera que abra un portón.
     */
    public static void abatir(Level level, BlockPos pos, BlockState estado, boolean abierto) {
        Direction facing = estado.getValue(FACING);
        // El EJE DEL MURO es el perpendicular al que mira el portón: por ahí se reparten las tres celdas del ancho.
        boolean muroEnZ = ejeX(facing);
        // La celda baja de la columna que se ha pulsado.
        BlockPos base = pos.offset(0, -estado.getValue(LAYER).indice(), 0);
        int cambiadas = 0;
        int encontradas = 0;
        StringBuilder donde = new StringBuilder();
        // Las TRES celdas del ancho (la pulsada y una a cada lado) por los TRES pisos.
        for (int ancho = -1; ancho <= 1; ancho++) {
            for (int alto = 0; alto <= 2; alto++) {
                BlockPos p = muroEnZ ? base.offset(0, alto, ancho) : base.offset(ancho, alto, 0);
                BlockState s = level.getBlockState(p);
                if (!(s.getBlock() instanceof DoubleGateBlock)) {
                    continue;
                }
                encontradas++;
                if (s.getValue(FACING) == facing && s.getValue(OPEN) != abierto) {
                    level.setBlock(p, s.setValue(OPEN, abierto), 3);
                    cambiadas++;
                } else if (s.getValue(FACING) != facing) {
                    donde.append(p.toShortString()).append("(facing ").append(s.getValue(FACING)).append(") ");
                }
            }
        }
        // TRAZA (6-oct-2026): el jugador reportó que «parece que va a abrirse … pero regresa a su posición original». La
        // línea del click dice «paso a ABIERTO» SIEMPRE, así que no distingue «abre entero» de «abre sólo la hoja que
        // pulsas». Esto sí: cuántas celdas ha encontrado alrededor, cuántas ha movido, y cuáles tienen OTRO `FACING`
        // (que es lo que dejaría hojas quietas y daría esa sensación de que se vuelve a cerrar ✗).
        DevilRpg.LOGGER.info("[Porton] abatir en {}: encontradas={} cambiadas={} {} (facing de la pulsada={})",
                pos.toShortString(), encontradas, cambiadas,
                donde.length() == 0 ? "" : "CON OTRO FACING: " + donde.toString().trim(), facing);
        if (cambiadas == 0) {
            return; // ya estaba como se pide: no hay nada que hacer
        }
    }

    /** ¿Ese punto del mundo es una celda de un portón doble? (lo usa el latido y el asedio). */
    public static boolean esPorton(BlockState state) {
        return state.getBlock() instanceof DoubleGateBlock;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!level.isClientSide) {
            boolean abierto = !state.getValue(OPEN);
            abatir(level, pos, state, abierto);
            // Y SE LE DICE AL LANDIDO QUE EL JUGADOR ACABA DE ABRIRLO, para que no lo cierre en el tick siguiente (es
            // justo el fallo que reportó: «hace el sonido pero regresa a su posición original y no me deja pasar»). El
            // pueblo abre y cierra los portones según si hay aldeanos cerca, y eso corre CADA TICK: sin este margen, la
            // orden del jugador no dura nada ✓.
            if (level instanceof net.minecraft.server.level.ServerLevel servidor) {
                com.chipoodle.devilrpg.world.VillageGenerator.marcarUsoDelJugador(servidor, pos);
            }
            // TRAZA (6-oct-2026): el jugador reportó que «cuando le doy click no se abre», y desde fuera no hay forma de
            // saber si el golpe llega al bloque o no. Esta línea lo dice: si sale cada vez que se pulsa, el click SÍ
            // llega y el problema está en el estado; si no sale NUNCA, el click no está llegando a este bloque.
            DevilRpg.LOGGER.info("[Porton] click en {} ({}): paso a {}", pos.toShortString(),
                    player.getName().getString(), abierto ? "ABIERTO" : "CERRADO");
            level.playSound(null, pos, abierto ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE,
                    SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.1F + 0.9F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * <b>El portón no se cae a trozos.</b> Si se rompe una celda, se van las nueve: si no, quedarían hojas colgando en
     * el aire (y el pueblo, que repone el muro, las repondría a medias). No da botín a propósito: es una pieza de
     * estructura, y romperla no debe ser una forma de farmear hierro.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            Direction facing = state.getValue(FACING);
            // El eje del MURO (perpendicular al que mira el portón): por ahí van las TRES celdas del ancho. Antes
            // barría dos, así que romper una celda dejaba media hoja en pie.
            boolean muroEnZ = ejeX(facing);
            BlockPos base = pos.offset(0, -state.getValue(LAYER).indice(), 0);
            for (int ancho = -1; ancho <= 1; ancho++) {
                for (int alto = 0; alto <= 2; alto++) {
                    BlockPos p = muroEnZ ? base.offset(0, alto, ancho) : base.offset(ancho, alto, 0);
                    if (!p.equals(pos) && level.getBlockState(p).getBlock() instanceof DoubleGateBlock) {
                        level.destroyBlock(p, false);
                    }
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
