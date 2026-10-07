package com.chipoodle.devilrpg.block;

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
     * El grosor de una hoja: <b>2 píxeles</b> (1/8 de bloque). Cerrada mide 8 de fondo (media anchura del hueco, que
     * es de 16) y abierta se queda en sus 2 de bisagra.
     */
    private static final double GROSOR = 2.0D;
    private static final double ANCHO_HOJA = 8.0D;

    public DoubleGateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(OPEN, false)
                .setValue(SIDE, PanelSide.LEFT)
                .setValue(LAYER, PanelLayer.LOW));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, SIDE, LAYER);
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
            return Shapes.block(); // cerrada: la celda entera, las dos hojas se juntan en el centro
        }
        // Abierta: de canto junto a la bisagra (lado negativo para la hoja izquierda, positivo para la derecha).
        if (enX) {
            return side == PanelSide.LEFT ? Block.box(0.0D, 0.0D, 0.0D, GROSOR, 16.0D, 16.0D)
                    : Block.box(16.0D - GROSOR, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);
        }
        return side == PanelSide.LEFT ? Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, GROSOR)
                : Block.box(0.0D, 0.0D, 16.0D - GROSOR, 16.0D, 16.0D, 16.0D);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return forma(state.getValue(FACING), state.getValue(SIDE), state.getValue(OPEN));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        // La colisión es la MISMA que la forma: abierta de canto se puede cruzar, cerrada no. Y así el jugador ve lo
        // que hay: si la hoja se ve de canto, se pasa.
        return forma(state.getValue(FACING), state.getValue(SIDE), state.getValue(OPEN));
    }

    /**
     * <b>El portón abate entero.</b> Al cambiar el estado de una celda hay que cambiar el de <b>las nueve</b> (las dos
     * hojas por los tres pisos); si no, quedarían hojas a medio abrir o pisos cerrados con el resto abierto, y el
     * hueco no se podría cruzar aunque se viera abierto. La búsqueda del resto es <b>por estado</b> (mismo
     * {@code FACING}) y <b>alrededor de esta celda</b>, que es como se comporta un portón: no hay que saber dónde
     * empezó.
     */
    public static void abatir(Level level, BlockPos pos, BlockState estado, boolean abierto) {
        Direction facing = estado.getValue(FACING);
        boolean enX = ejeX(facing);
        // La celda baja de la columna: las nueve celdas son 2 de ancho (eje perpendicular) por 3 de alto.
        BlockPos base = pos.offset(0, -estado.getValue(LAYER).indice(), 0);
        for (int ancho = 0; ancho <= 1; ancho++) {
            for (int alto = 0; alto <= 2; alto++) {
                BlockPos p = enX ? base.offset(0, alto, ancho) : base.offset(ancho, alto, 0);
                BlockState s = level.getBlockState(p);
                if (s.getBlock() instanceof DoubleGateBlock && s.getValue(FACING) == facing
                        && s.getValue(OPEN) != abierto) {
                    level.setBlock(p, s.setValue(OPEN, abierto), 3);
                }
            }
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
            boolean enX = ejeX(facing);
            BlockPos base = pos.offset(0, -state.getValue(LAYER).indice(), 0);
            for (int ancho = 0; ancho <= 1; ancho++) {
                for (int alto = 0; alto <= 2; alto++) {
                    BlockPos p = enX ? base.offset(0, alto, ancho) : base.offset(ancho, alto, 0);
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
