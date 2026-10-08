package com.chipoodle.devilrpg.blockentity;

import com.chipoodle.devilrpg.block.DoubleGateBlock;
import com.chipoodle.devilrpg.init.ModEntityBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * <b>EL GIRO DEL PORTÓN DOBLE ABATIBLE</b> (lo pidió el jugador: *«que al abrirse gire 90° sobre su bisagra de forma
 * suave»*).
 * <p>
 * <b>Por qué hay una entidad de bloque</b>, y no se pudo evitar: la geometría de un modelo de bloque está encerrada
 * entre <b>−16 y 32 píxeles</b> (`BlockElement`), o sea <b>1 bloque a un lado y 2 al otro</b> desde la celda que
 * dibuja. Medido y documentado en I234: con el modelo abierto la hoja quedaba <b>cruzando el muro</b> —«se queda en
 * medio del marco, no se abre totalmente a lado»— ✗. Un renderizador no tiene esa ventana: dibuja dónde le da la gana,
 * así que la hoja puede quedar <b>entera de 3 bloques hacia un lado</b> ✓. Y de propina da el giro <b>interpolado</b>,
 * que con un modelo de bloque es imposible (el estado cambia de golpe, I232).
 * <p>
 * <b>La entidad va SOLO en la celda de la BISAGRA.</b> Las nueve celdas del portón son el mismo bloque, así que el
 * juego crearía nueve entidades iguales y el portón se dibujaría <b>nueve veces</b> (una por entidad), superpuestas y
 * girando sobre nueve bisagras distintas ✗. La bisagra es la celda que construye `VillageGenerator.sellarLasCeldasDelPorton`
 * con {@code SIDE == LEFT && JUNTURA == false && LAYER == LOW} (la de fuera del lado izquierdo), y {@link #newBlockEntity}
 * devuelve {@code null} en las otras ocho. Desde esa celda, y sabiendo el {@code FACING}, se deduce dónde están las
 * demás: la línea del muro es su perpendicular, y de ahí salen las nueve celdas.
 * <p>
 * <b>El progreso (0 = cerrado, 1 = abierto)</b> se mueve cada tick hacia el objetivo que dice el estado del bloque
 * ({@code OPEN}), en <b>los dos lados</b>: el servidor no lo necesita para nada (la colisión la decide el estado), pero
 * el cliente sí, y el estado le llega solo por la red. Por eso mismo el progreso <b>no se guarda en el NBT como
 * verdad</b>: si se guardara, al recargar el trozo el portón podría quedarse a medio abrir para siempre. Se guarda
 * (y se reenvía con {@link #getUpdateTag()}) sólo como punto de partida del giro, y siempre se re-corrige hacia el
 * objetivo en el primer tick.
 */
public class PortonDobleBlockEntity extends BlockEntity {

    /**
     * <b>El giro dura 12 ticks (0,6 s).</b> Es la velocidad que pidió el jugador (*«~12 ticks, 0,6 s»*); más rápido se
     * ve como un salto y más lento se ve como una puerta pesada que no es lo que es.
     */
    public static final int TICKS_DEL_GIRO = 12;

    /** Lo que avanza el giro cada tick. */
    private static final float PASO_DEL_GIRO = 1.0F / TICKS_DEL_GIRO;

    /** 0.0 = cerrado (la hoja tumbada en el plano del muro), 1.0 = abierto (girada 90°, perpendicular). */
    private float progresoDelGiro;

    /**
     * El progreso <b>al final del tick anterior</b>. Existe sólo para el renderizador: los fotogramas no caen en los
     * ticks (a 60 FPS hay tres por tick), así que el ángulo se interpola entre el tick anterior y éste con el
     * {@code partialTick} y el giro se ve fluido en vez de a saltos de 7,5° ✗.
     */
    private float progresoDelGiroAnterior;


    public PortonDobleBlockEntity(BlockPos pos, BlockState state) {
        super(ModEntityBlocks.PORTON_DOBLE_ENTITY_BLOCK.get(), pos, state);
    }

    /**
     * <b>¿Esta celda tiene que llevar la entidad?</b> Sí sólo la bisagra: la de fuera del lado izquierdo y el piso de
     * abajo. Si se le pone a las nueve, el portón se dibuja nueve veces (ver la nota de la clase).
     */
    public static boolean esLaBisagra(BlockState state) {
        return state.getBlock() instanceof DoubleGateBlock
                && state.getValue(DoubleGateBlock.SIDE) == DoubleGateBlock.PanelSide.LEFT
                && !state.getValue(DoubleGateBlock.JUNTURA)
                && state.getValue(DoubleGateBlock.LAYER) == DoubleGateBlock.PanelLayer.LOW;
    }

    /** El ticker del portón: mueve el giro hacia donde dice el estado del bloque. Vale para cliente y servidor. */
    public void tick(@NotNull BlockState estado) {
        this.progresoDelGiroAnterior = this.progresoDelGiro;
        float objetivo = estado.getValue(DoubleGateBlock.OPEN) ? 1.0F : 0.0F;
        if (this.progresoDelGiro < objetivo) {
            this.progresoDelGiro = Math.min(objetivo, this.progresoDelGiro + PASO_DEL_GIRO);
        } else if (this.progresoDelGiro > objetivo) {
            this.progresoDelGiro = Math.max(objetivo, this.progresoDelGiro - PASO_DEL_GIRO);
        }
    }

    /** El progreso del giro tal cual (0 cerrado, 1 abierto). */
    public float getProgresoDelGiro() {
        return this.progresoDelGiro;
    }

    /** El progreso en el tick anterior, para que el renderizador interpole entre los dos con el {@code partialTick}. */
    public float getProgresoAnteriorDelGiro() {
        return this.progresoDelGiroAnterior;
    }

    /** Para el renderizador: no hay nada que dibujar mientras el portón esté cerrado y no haya llegado a girar. */

    /**
     * <b>Dónde está la bisagra</b>, en coordenadas de mundo, y con la orientación del portón.
     * <p>
     * La línea del muro es la <b>perpendicular</b> a {@code FACING} (el portón mira a un lado del muro, así que el muro
     * corre de través). Y las otras dos hojas salen <b>hacia el lado positivo</b> de esa línea: el generador ordena las
     * tres celdas por coordenada creciente y pone la bisagra en la primera, y la bisagra es la celda
     * {@code SIDE == LEFT} — que en el estado del bloque es justo esa.
     */
    @Nullable
    public Bisagra getBisagra() {
        if (this.level == null) {
            return null;
        }
        BlockState estado = this.getBlockState();
        if (!(estado.getBlock() instanceof DoubleGateBlock)) {
            return null;
        }
        return new Bisagra(this.worldPosition, estado.getValue(DoubleGateBlock.FACING));
    }

    /**
     * Dónde está la bisagra y hacia dónde mira el portón. Con esas dos cosas el renderizador sabe colocar las nueve
     * hojas: la <b>línea del muro</b> es la <b>perpendicular</b> a {@code facing} (el portón mira a un lado del muro,
     * así que el muro corre de través), y la <b>normal</b> es el propio {@code facing}, que es hacia donde sale la hoja
     * al girar.
     *
     * @param bisagra la celda de la bisagra, en coordenadas de mundo
     * @param facing  hacia donde mira el portón ({@code DoubleGateBlock.FACING}, leído de la bisagra)
     */
    public record Bisagra(BlockPos bisagra, Direction facing) {
    }

    @Override
    protected void saveAdditional(CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putFloat("progresoDelGiro", this.progresoDelGiro);
    }

    @Override
    public void loadAdditional(CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.progresoDelGiro = tag.getFloat("progresoDelGiro");
        // El "anterior" arranca igual que el actual: si no, el primer fotograma interpolaría desde 0 y la hoja daría
        // un salto hacia atrás (de su posición real a la de cerrado) antes de empezar a girar ✗.
        this.progresoDelGiroAnterior = this.progresoDelGiro;
    }

    /**
     * Lo que se manda al cliente <b>al cargar el trozo</b>. El estado del bloque (que es quien dice si está abierto)
     * ya viaja solo, pero el progreso del giro no: sin esto, un portón abierto recién cargado aparecería <b>cerrado</b>
     * y giraría al llegar el primer tick.
     */
    @Override
    public @NotNull CompoundTag getUpdateTag(@NotNull HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        this.saveAdditional(tag, registries);
        return tag;
    }
}
