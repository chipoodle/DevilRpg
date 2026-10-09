package com.chipoodle.devilrpg.block;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.blockentity.PortonDobleBlockEntity;
import com.chipoodle.devilrpg.init.ModEntityBlocks;
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
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
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
public class DoubleGateBlock extends HorizontalDirectionalBlock implements EntityBlock {

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

    /**
     * <b>¿La hoja está FUERA de su celda?</b> — abierta, o girando hacia cualquiera de las dos posiciones.
     * <p>
     * <b>Existe por la animación del CIERRE</b> (8-oct-2026). Hasta ahora el modelo del estado cerrado se dibujaba en
     * cuanto {@link #OPEN} pasaba a falso, o sea <b>de golpe al empezar a cerrar</b>, y tapaba la hoja que el
     * renderizador todavía tenía que girar de vuelta: la puerta parecía cerrarse de un salto ✗ (lo reportó el jugador:
     * *«no hace la animación inversa cuando cierra, sino que todavía se glitchea»*, después de que la apertura ya fuera
     * bien). Y el modelo no puede depender de {@code OPEN}, porque {@code OPEN} es la <b>lógica</b> (el paso, la aldea,
     * el pathfinding) y cambia de golpe a propósito ✓.
     * <p>
     * Así que la <b>vista</b> tiene su propio dato: mientras esta propiedad esté en verdadero, los modelos del portón
     * <b>no dibujan nada</b> (van con {@code "elements": []}, igual que el estado abierto) y la hoja la pinta el
     * renderizador. El ticker la apaga **sólo cuando el giro ha llegado al final y está cerrado**, y ese tick el
     * renderizador ya está dibujando la hoja justo en la posición de cerrado, así que el modelo entra **sin salto** ✓.
     */
    public static final BooleanProperty HOJA_FUERA = BooleanProperty.create("hoja_fuera");

    /**
     * El <b>grosor</b> de una hoja: <b>2 píxeles</b> (1/8 de bloque). Cerrada mide lo que su celda; abierta es el canto
     * del panel que ha girado 90° ✓.
     */
    private static final double GROSOR = 2.0D;

    public DoubleGateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(OPEN, false)
                .setValue(SIDE, PanelSide.LEFT)
                .setValue(LAYER, PanelLayer.LOW)
                .setValue(JUNTURA, false)
                .setValue(HOJA_FUERA, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, SIDE, LAYER, JUNTURA, HOJA_FUERA);
    }

    /** ¿La hoja abate por el eje X? (la pared corre en Z cuando el portón mira al este o al oeste). */
    private static boolean ejeX(Direction facing) {
        return facing.getAxis() == Direction.Axis.X;
    }


    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // LA SILUETA CON LA QUE SE PULSA ES LA CELDA ENTERA, SIEMPRE (6-oct-2026, lo pidió el jugador: *«la superficie
        // donde hacer click es muy pequeña, tendría que ocupar toda la superficie de la puerta»*). Ésta es la forma que
        // usa el juego para el **rayo del ratón** al apuntar y pulsar, así que con ella entera se acierta en cualquier
        // punto de la puerta — también con el portón ABIERTO, que es cuando antes había que acertarle a un listón de
        // {@value #GROSOR} píxeles ✗. La geometría fina de la hoja sigue estando en el MODELO (lo que se ve) y en
        // {@link #getCollisionShape} (lo que bloquea).
        return Shapes.block();
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
        // PASA LO MISMO CON LA VISTA: mientras la hoja esté fuera de su celda —abierta o girando— la dibuja el
        // renderizador y el modelo no dibuja nada, así que el paso queda libre en cuanto la hoja empieza a moverse ✓
        // (y durante el cierre sigue libre hasta que la hoja llega a su sitio, que es lo natural: no te atrapa a
        // medio cerrar). Ver {@link #HOJA_FUERA}.
        return state.getValue(HOJA_FUERA) ? Shapes.empty() : Shapes.block();
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
                // La LÓGICA (OPEN) y la VISTA (HOJA_FUERA) se ponen juntas: al moverse la hoja, su modelo deja de
                // dibujar y pasa a dibujarla el renderizador ✓. Y da igual si es abrir o cerrar: al CERRAR también se
                // enciende, y es el ticker de la entidad el que la apaga cuando el giro termina — así la animación
                // inversa se ve entera y el modelo sólo entra cuando la hoja ya está en su sitio ✓.
                if (s.getValue(FACING) == facing
                        && (s.getValue(OPEN) != abierto || !s.getValue(HOJA_FUERA))) {
                    level.setBlock(p, s.setValue(OPEN, abierto).setValue(HOJA_FUERA, true), 3);
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

    /**
     * <b>Devuelve la vista al modelo</b>: apaga {@link #HOJA_FUERA} en las nueve celdas del portón.
     * <p>
     * La llama el ticker de la entidad <b>sólo</b> cuando el giro ha llegado al final y el portón está cerrado. En ese
     * momento el renderizador está dibujando la hoja justo en la posición de cerrado, así que el modelo entra <b>sin
     * salto visible</b> ✓ — que es justo lo que faltaba para que el cierre se viera animado (antes el modelo entraba al
     * empezar a cerrar y tapaba el giro ✗).
     *
     * @return cuántas celdas ha devuelto al modelo
     */
    public static int apagarLaHoja(Level level, BlockPos pos, BlockState estado) {
        Direction facing = estado.getValue(FACING);
        boolean muroEnZ = ejeX(facing);
        BlockPos base = pos.offset(0, -estado.getValue(LAYER).indice(), 0);
        int apagadas = 0;
        for (int ancho = -1; ancho <= 1; ancho++) {
            for (int alto = 0; alto <= 2; alto++) {
                BlockPos p = muroEnZ ? base.offset(0, alto, ancho) : base.offset(ancho, alto, 0);
                BlockState s = level.getBlockState(p);
                if (s.getBlock() instanceof DoubleGateBlock && s.getValue(FACING) == facing
                        && s.getValue(HOJA_FUERA)) {
                    level.setBlock(p, s.setValue(HOJA_FUERA, false), 3);
                    apagadas++;
                }
            }
        }
        return apagadas;
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

    /**
     * <b>La entidad del portón va SÓLO EN LA CELDA DE LA BISAGRA</b> (el giro: ver {@link PortonDobleBlockEntity}).
     * <p>
     * Las nueve celdas son el MISMO bloque, así que el juego pediría una entidad en cada una: nueve entidades, nueve
     * progresos y el portón dibujado <b>nueve veces</b>, girando sobre nueve bisagras distintas ✗. La bisagra es la
     * celda que construye `VillageGenerator.sellarLasCeldasDelPorton` con {@code SIDE == LEFT && !JUNTURA && LAYER ==
     * LOW} (la de fuera del lado izquierdo, la primera del barrido), y desde ella —con el {@code FACING}— se deducen
     * las otras ocho, que es lo que hace el renderizador.
     */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        if (!PortonDobleBlockEntity.esLaBisagra(state)) {
            return null; // las otras ocho celdas no llevan entidad: la puerta entera la dibuja la de la bisagra
        }
        return ModEntityBlocks.PORTON_DOBLE_ENTITY_BLOCK.get().create(pos, state);
    }

    /**
     * El ticker va en <b>los dos lados</b> (a diferencia de las vids del mod, que sólo laten en el servidor): el
     * progreso del giro lo necesita el CLIENTE para dibujar, y el estado del bloque ({@code OPEN}) le llega solo por la
     * red, así que no hace falta ningún paquete nuevo. En el servidor se mueve también, pero no se usa para nada: la
     * colisión abierta sigue vacía y eso lo decide el estado, no el progreso.
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, @NotNull BlockState state,
            @NotNull BlockEntityType<T> type) {
        return (nivel, pos, estado, entidad) -> {
            if (entidad instanceof PortonDobleBlockEntity porton) {
                porton.tick(estado);
            }
        };
    }
}
