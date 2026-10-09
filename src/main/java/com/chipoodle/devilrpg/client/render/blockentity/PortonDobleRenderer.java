package com.chipoodle.devilrpg.client.render.blockentity;

import com.chipoodle.devilrpg.block.DoubleGateBlock;
import com.chipoodle.devilrpg.blockentity.PortonDobleBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * <b>EL RENDERIZADOR DEL PORTÓN DOBLE ABATIBLE</b>: dibuja la puerta girando sobre su bisagra, interpolada.
 * <p>
 * <b>POR QUÉ UN RENDERIZADOR Y NO UN MODELO</b> (I234, medido): la geometría de un modelo de bloque está encerrada
 * entre <b>−16 y 32 píxeles</b> (`BlockElement`), o sea 1 bloque a un lado de la celda que dibuja y 2 al otro; con eso
 * la hoja abierta queda <b>cruzando el muro</b> — «se queda en medio del marco, no se abre totalmente a lado» ✗. Un
 * renderizador dibuja donde quiera: la hoja queda <b>entera de 3 bloques hacia un lado</b> ✓. Y de paso puede girar
 * <b>interpolado</b>, que un modelo de bloque no puede (el estado cambia de golpe, I232) ✓.
 * <p>
 * <b>Y POR ESO EL ESTADO ABIERTO YA NO DIBUJA NADA</b>: sus modelos van con {@code "elements": []}
 * (`tools/arnes/hacer_modelos_del_porton.py`). Si no, el portón saldría <b>dos veces</b>: una con el modelo y otra con
 * la hoja girada ✗. El estado <b>cerrado no se toca</b>: lo sigue dibujando su modelo, que es el que el jugador dio por
 * bueno ✓.
 * <p>
 * <b>CÓMO ESTÁ CALCULADO, Y POR QUÉ ASÍ</b> (8-oct-2026). Un renderizador no se puede «mirar»: hay que razonar la
 * geometría, así que aquí <b>no se encadena ni una transformación</b> —que es donde es facilísimo equivocarse: la
 * versión anterior giraba cada celda sobre su propio canto y las hojas abanicaban, y además daba por hecho que el muro
 * corría por el eje de la mirada— ✗. En su lugar se define un <b>sistema de la puerta</b> con dos ejes y se calculan
 * los vértices a mano:
 * <ul>
 *   <li>{@code W} = <b>el eje del muro</b>, creciendo en el sentido en que el generador ordenó las celdas (la celda de
 *       la bisagra es la de coordenada MENOR, `VillageGenerator.sellarLasCeldasDelPorton`). Ojo: el eje del muro es el
 *       <b>PERPENDICULAR</b> al que mira el portón —si mira al este, el muro corre en Z— ✓.</li>
 *   <li>{@code N} = hacia donde mira el portón (`FACING`), que es por donde sale la hoja al abrirse.</li>
 *   <li>La hoja es <b>una sola pieza rígida</b> de 3 bloques de ancho: sus vértices son {@code (w, n, y)} con
 *       {@code w ∈ [0,3]} medido <b>desde la bisagra</b>, {@code n} el grosor y {@code y} la altura. Girar es rotar
 *       <b>ese par (w, n)</b> y ya está: toda la puerta se mueve junta porque comparten el mismo origen (la
 *       bisagra) ✓.</li>
 * </ul>
 */
public class PortonDobleRenderer implements BlockEntityRenderer<PortonDobleBlockEntity> {

    /** La textura de la puerta, atada <b>directamente</b> (no por el atlas de bloques). */
    private static final ResourceLocation TEXTURA =
            ResourceLocation.fromNamespaceAndPath("devilrpg", "textures/block/porton_doble.png");

    /**
     * El tipo de render: {@code entityCutout} ata <b>esta</b> textura y espera las UV en <b>0..1</b>.
     * <p>
     * <b>Esto era otro fallo de la versión anterior</b> ✗: usaba {@code RenderType.cutout()}, que ata el <b>atlas</b> de
     * bloques, y le pasaba UV en <b>0..16</b>. Con el atlas las UV van en 0..1 (espacio de atlas), así que 16 se sale
     * de la textura y se muestrea cualquier cosa. Con {@code entityCutout} la textura del mod va suelta y {@code 0..1}
     * es exactamente el dibujo entero ✓.
     */
    private static RenderType tipo() {
        return RenderType.entityCutout(TEXTURA);
    }

    /**
     * El grosor de la hoja: <b>un bloque entero</b>, el mismo que el modelo del estado CERRADO.
     * <p>
     * <b>Y esto era lo último que chirriaba</b> ✗ (lo describió el jugador: *«cuando está cerrada ocupa un cuadro de
     * espesor… sin embargo cuando se abre se hace muy delgado su espesor… se ve antinatural»*). La hoja medía
     * {@code 2/16} y el modelo cerrado es un cubo de bloque entero, así que el portón <b>cambiaba de grosor</b> al
     * abrirse y, además, en el primer y el último fotograma del giro había un salto de grosor (16 → 2 píxeles) ✓.
     * <p>
     * Poniéndolo a <b>1,0</b> los dos espesores son <b>el mismo</b>: la hoja cerrada ocupa exactamente su celda —igual
     * que el modelo— y <b>no hay salto ninguno</b>, ni al empezar ni al terminar ✓. La puerta abierta queda gruesa, que
     * es como se ve un portón de verdad y como pidió el jugador, en vez de una hoja fina que parecía de otro bloque.
     */
    private static final float GROSOR = 1.0F;

    /**
     * Dónde empieza la hoja dentro de su celda, medido por la normal: {@code 0,5 − grosor/2}. Con el grosor a un bloque
     * entero sale <b>0</b>, o sea que la hoja cerrada ocupa su celda <b>exactamente igual que el modelo</b>: por eso ya
     * no hay ningún salto de grosor al empezar ni al terminar el giro ✓ (antes, con la hoja fina, había que centrarla
     * para disimular el cambio, y aun así se notaba).
     */
    private static final float CENTRO = 0.5F - GROSOR / 2.0F;

    /**
     * <b>DÓNDE ESTÁ EL EJE DE GIRO, medido por la NORMAL: en el CANTO del marco</b>, que es la cara de la hoja por la
     * que abre ({@code CENTRO + GROSOR}), y no en el centro del muro ni en la cara de fuera.
     * <p>
     * <b>Y esto era un fallo que se veía en el juego</b> ✗ (lo describió el jugador: *«el eje de la puerta está justo
     * en medio del pilar… cuando se abre atraviesa una parte del pilar»*). Con el eje en {@code n = 0} —la cara del
     * muro—, al girar 90° el canto de la hoja aterriza en {@code w = −0,44…−0,56}, o sea <b>medio bloque DENTRO del
     * pilar</b> ✓ (por eso parecía que el eje estaba en medio del pilar: la hoja abierta se metía allí). Poniéndolo en
     * el canto, el canto de la hoja queda en {@code w = 0…0,125}: <b>pegado al marco y sin cruzar ni un píxel</b> ✓.
     * <p>
     * Por la <b>normal</b>, en cambio, va donde tiene que ir: la hoja se queda <b>centrada en el grosor del marco</b>
     * (lo pidió el jugador: *«en otro eje horizontal sí debe ir en el centro, en medio del marco, para que siempre rote
     * hacia fuera o hacia adentro en el mismo lugar»*) — eso lo da {@link #CENTRO}, que es dónde está la hoja cerrada.
     */
    private static final float EJE = CENTRO + GROSOR;

    public PortonDobleRenderer(BlockEntityRendererProvider.Context contexto) {
        // No hay nada que sacar del contexto: la textura va atada por el render type y la geometría se calcula.
    }

    @Override
    public void render(PortonDobleBlockEntity porton, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
            int luz, int overlay) {
        PortonDobleBlockEntity.Bisagra bisagra = porton.getBisagra();
        if (bisagra == null) {
            return;
        }
        Level level = porton.getLevel();
        if (level == null) {
            return;
        }
        BlockPos celdaBisagra = bisagra.bisagra();
        Direction facing = bisagra.facing();
        BlockState estadoDeLaBisagra = level.getBlockState(celdaBisagra);
        if (!(estadoDeLaBisagra.getBlock() instanceof DoubleGateBlock)) {
            return;
        }
        boolean abierto = estadoDeLaBisagra.getValue(DoubleGateBlock.OPEN);
        // ¿ESTÁ LA HOJA FUERA DE SU CELDA? Abierta, o girando hacia cualquiera de las dos posiciones (lo lleva
        // `DoubleGateBlock.HOJA_FUERA`). Mientras lo esté, el modelo no dibuja y la hoja la pinta este renderizador ✓.
        boolean hojaFuera = estadoDeLaBisagra.getValue(DoubleGateBlock.HOJA_FUERA);
        // Y EL REPOSO LO DIBUJA EL MODELO: cerrado, con la hoja en su sitio y el giro en cero, no hay nada que enseñar
        // aquí (dibujarlo sería pintar la misma cara dos veces en el mismo píxel: parpadeo ✗). En cuanto el giro se
        // mueve —o la hoja está fuera, que incluye el cierre a medias— manda este renderizador, porque es el único que
        // sabe dónde está la hoja ENTRE dos ticks ✓. La condición se mira en el PROGRESO, que es el mismo dato del que
        // sale el dibujo, y no en una bandera interna: con una bandera había un momento sin modelo y sin renderizador, y
        // el portón parpadeaba (lo reportó el jugador: *«la primera vez que abro como que parpadea»*) ✗.
        float progreso;
        if (!abierto && !hojaFuera && porton.getProgresoDelGiro() <= 0.0F
                && porton.getProgresoAnteriorDelGiro() <= 0.0F) {
            return;
        }
        // PROGRESO INTERPOLADO: el mundo se dibuja muchas más veces que los 20 ticks por segundo, así que sin rellenar
        // lo que falta entre el tick anterior y éste el giro se vería a saltos de 7,5° ✗.
        progreso = porton.getProgresoAnteriorDelGiro()
                + (porton.getProgresoDelGiro() - porton.getProgresoAnteriorDelGiro()) * partialTick;

        // LOS DOS EJES. El del muro es el PERPENDICULAR al que mira el portón: si mira al este/oeste, el muro corre en
        // Z. Y crece hacia donde el generador ordenó las celdas (la bisagra es la de coordenada menor), o sea el
        // sentido POSITIVO del eje ✓.
        boolean muroEnZ = facing.getAxis() == Direction.Axis.X;
        float[] ejes = { muroEnZ ? 0.0F : 1.0F, muroEnZ ? 1.0F : 0.0F, facing.getStepX(), facing.getStepZ() };

        // EL GIRO: de 0 a −90°, que con la rotación de abajo lleva la hoja al lado de la NORMAL, o sea hacia donde
        // mira el portón (hacia FUERA del hueco, para no tapar el paso) ✓. Con el signo contrario saldría hacia dentro:
        // es cambiar este menos por un más, y nada más.
        double theta = -Math.PI / 2.0D * progreso;
        float cos = (float) Math.cos(theta);
        float sin = (float) Math.sin(theta);

        VertexConsumer buffer = buffers.getBuffer(tipo());
        PoseStack.Pose pose = poseStack.last();

        for (int celda = 0; celda < 3; celda++) {
            BlockPos c = muroEnZ
                    ? new BlockPos(celdaBisagra.getX(), celdaBisagra.getY(), celdaBisagra.getZ() + celda)
                    : new BlockPos(celdaBisagra.getX() + celda, celdaBisagra.getY(), celdaBisagra.getZ());
            if (!esCeldaDelPorton(level, c, facing)) {
                // Si a la estructura le falta una celda (mundo a medio cargar, portón roto a trozos) NO se dibuja el
                // portón: dibujarlo sería pintar una puerta que ya no está en el mundo ✗.
                return;
            }
            for (int piso = 0; piso < 3; piso++) {
                dibujarCuadrado(buffer, pose, ejes, cos, sin, celda, piso, luz, overlay);
            }
        }
    }

    /**
     * Dibuja <b>un cuadrado</b> de la hoja (una celda de ancho por un piso) como un cubo de seis caras, ya girado.
     * <p>
     * Cada cuadrado lleva <b>su tercio de la textura</b> —el mismo reparto que los modelos cerrados, uno por celda y
     * uno por piso—, que es lo que hace que la puerta se lea como una sola y no como tres listones ✓. Los cantos
     * (arriba, abajo y los dos laterales finos) no tienen tercio propio y se estiran con su franja vertical, igual que
     * hacen las caras este/oeste del modelo cerrado.
     */
    private static void dibujarCuadrado(VertexConsumer buffer, PoseStack.Pose pose, float[] ejes, float cos, float sin,
            int celda, int piso, int luz, int overlay) {
        float w0 = celda;
        float w1 = celda + 1.0F;
        float y0 = piso;
        float y1 = piso + 1.0F;
        float n0 = CENTRO;
        float n1 = CENTRO + GROSOR;
        // La textura entera está repartida en tercios: uno por celda (horizontal) y uno por piso (vertical).
        float u0 = celda / 3.0F;
        float u1 = (celda + 1.0F) / 3.0F;
        float v0 = piso / 3.0F;
        float v1 = (piso + 1.0F) / 3.0F;

        // Las seis caras, cada una con sus CUATRO esquinas en (w, n, y) y sus cuatro UV en el mismo orden. El normal
        // va en el sistema de la puerta: (0, 1, 0) es la cara de delante (+N) y (1, 0, 0) la del extremo libre.
        cara(buffer, pose, ejes, cos, sin, luz, overlay,
                new float[][] { { w0, n1, y0 }, { w0, n1, y1 }, { w1, n1, y1 }, { w1, n1, y0 } },
                new float[] { u0, v1, u0, v0, u1, v0, u1, v1 }, 0.0F, 1.0F, 0.0F);
        cara(buffer, pose, ejes, cos, sin, luz, overlay,
                new float[][] { { w0, n0, y0 }, { w1, n0, y0 }, { w1, n0, y1 }, { w0, n0, y1 } },
                new float[] { u0, v1, u1, v1, u1, v0, u0, v0 }, 0.0F, -1.0F, 0.0F);
        cara(buffer, pose, ejes, cos, sin, luz, overlay,
                new float[][] { { w1, n0, y0 }, { w1, n1, y0 }, { w1, n1, y1 }, { w1, n0, y1 } },
                new float[] { 1.0F, v1, 0.0F, v1, 0.0F, v0, 1.0F, v0 }, 1.0F, 0.0F, 0.0F);
        cara(buffer, pose, ejes, cos, sin, luz, overlay,
                new float[][] { { w0, n0, y0 }, { w0, n1, y0 }, { w0, n1, y1 }, { w0, n0, y1 } },
                new float[] { 0.0F, v1, 1.0F, v1, 1.0F, v0, 0.0F, v0 }, -1.0F, 0.0F, 0.0F);
        cara(buffer, pose, ejes, cos, sin, luz, overlay,
                new float[][] { { w0, n0, y1 }, { w1, n0, y1 }, { w1, n1, y1 }, { w0, n1, y1 } },
                new float[] { 0.0F, v0, 1.0F, v0, 1.0F, v1, 0.0F, v1 }, 0.0F, 0.0F, 1.0F);
        cara(buffer, pose, ejes, cos, sin, luz, overlay,
                new float[][] { { w0, n0, y0 }, { w0, n1, y0 }, { w1, n1, y0 }, { w1, n0, y0 } },
                new float[] { 0.0F, v0, 0.0F, v1, 1.0F, v1, 1.0F, v0 }, 0.0F, 0.0F, -1.0F);
    }

    /**
     * Escribe una cara: las cuatro esquinas en (w, n, y), el normal en el sistema de la puerta y las cuatro UV.
     * <p>
     * El <b>orden</b> de las esquinas decide si la cara se ve o se descarta (el render type recorta las traseras), y
     * como el sistema de la puerta cambia de mano según a dónde mire el portón, <b>no se puede dar por bueno un orden
     * fijo</b>: se calcula el normal de la cara tal y como ha quedado y, si apunta al revés del que toca, se invierte
     * el orden (con sus UV) ✓. Así no se cae una cara por haber elegido mal el sentido.
     */
    private static void cara(VertexConsumer buffer, PoseStack.Pose pose, float[] ejes, float cos, float sin, int luz,
            int overlay, float[][] esquinas, float[] uv, float nw, float nn, float ny) {
        // El normal, girado con la puerta. OJO: la componente VERTICAL (ny) va tal cual, no se gira —el giro es sobre
        // el eje Y— y las dos horizontales salen de rotar el par (nw, nn). Calcularlo pasando (nw, nn) por `vertice`
        // con y=0 dejaba el normal de ARRIBA y ABAJO en CERO (su parte horizontal es nula), y esas dos caras se
        // descartaban por completo; lo cazó la comprobación numérica (32 caras mal) ✗.
        float nwG = nw * cos + nn * sin;
        float nnG = -nw * sin + nn * cos;
        float mundoNX = ejes[0] * nwG + ejes[2] * nnG;
        float mundoNZ = ejes[1] * nwG + ejes[3] * nnG;
        float brillo = brillo(mundoNX, ny, mundoNZ);

        float[][] mundo = new float[4][];
        for (int i = 0; i < 4; i++) {
            mundo[i] = vertice(ejes, cos, sin, esquinas[i][0], esquinas[i][1], esquinas[i][2]);
        }
        boolean invertir = productoVectorialEsNegativo(mundo, mundoNX, ny, mundoNZ);
        for (int i = 0; i < 4; i++) {
            int k = invertir ? 3 - i : i;
            buffer.addVertex(pose, mundo[k][0], mundo[k][1], mundo[k][2])
                    .setColor(brillo, brillo, brillo, 1.0F)
                    .setUv(uv[k * 2], uv[k * 2 + 1])
                    .setOverlay(overlay)
                    .setLight(luz)
                    .setNormal(mundoNX, ny, mundoNZ);
        }
    }

    /**
     * Un punto {@code (w, n, y)} del sistema de la puerta, <b>girado sobre el eje</b> y llevado a coordenadas del mundo
     * relativas al bloque de la bisagra (que es el origen que da el juego a este renderizador).
     * <p>
     * El giro es sobre la recta {@code (w = 0, n = EJE)}, así que primero se lleva el punto a esa recta ({@code dn}),
     * se gira, y se devuelve — no girar sobre {@code n = 0}, que es lo que metía la hoja dentro del pilar ✗.
     */
    private static float[] vertice(float[] ejes, float cos, float sin, float w, float n, float y) {
        float dn = n - EJE;
        float wG = w * cos + dn * sin;
        float nG = -w * sin + dn * cos + EJE;
        return new float[] { ejes[0] * wG + ejes[2] * nG, y, ejes[1] * wG + ejes[3] * nG };
    }

    /**
     * Si el normal que sale de las esquinas (el producto vectorial de los dos primeros lados) apunta <b>al revés</b>
     * del que toca. Sólo importa el signo, así que basta con mirar el producto escalar.
     */
    private static boolean productoVectorialEsNegativo(float[][] m, float nx, float ny, float nz) {
        float ax = m[1][0] - m[0][0];
        float ay = m[1][1] - m[0][1];
        float az = m[1][2] - m[0][2];
        float bx = m[2][0] - m[0][0];
        float by = m[2][1] - m[0][1];
        float bz = m[2][2] - m[0][2];
        float cx = ay * bz - az * by;
        float cy = az * bx - ax * bz;
        float cz = ax * by - ay * bx;
        return cx * nx + cy * ny + cz * nz < 0.0F;
    }

    /**
     * El brillo de la cara, el <b>mismo que el juego da a los modelos de bloque</b>: arriba 1, abajo 0,5, y los lados
     * 0,8 si miran al norte/sur y 0,6 al este/oeste. Se calcula sobre el normal <b>ya girado</b>, porque si no la hoja
     * se iluminaría igual de frente que de canto y el giro no se leería ✗.
     */
    private static float brillo(float nx, float ny, float nz) {
        if (Math.abs(ny) > 0.5F) {
            return ny > 0.0F ? 1.0F : 0.5F;
        }
        return Math.abs(nz) >= Math.abs(nx) ? 0.8F : 0.6F;
    }

    /** ¿Esa celda es del portón? (mismo bloque y mismo {@code FACING}). */
    private static boolean esCeldaDelPorton(Level level, BlockPos celda, Direction facing) {
        BlockState estado = level.getBlockState(celda);
        return estado.getBlock() instanceof DoubleGateBlock
                && estado.getValue(DoubleGateBlock.FACING) == facing;
    }

    /**
     * <b>Dónde se dibuja el portón</b>: es lo que el juego usa para decidir si el renderizador entra en pantalla, y su
     * valor por defecto es <b>la celda de la entidad</b> —la bisagra, que está en una ESQUINA del portón—. Con eso,
     * mirando la puerta abierta desde el otro lado del muro la hoja entera cae <b>fuera del recorte</b> y
     * <b>desaparece</b> ✗. La caja cubre las nueve celdas (3 por la línea del muro, 3 de alto) <b>más el arco entero del
     * giro</b> (3 bloques hacia cada lado de la normal): no se sabe en qué punto del recorrido estará la hoja, así que
     * se cubre todo ✓.
     */
    @Override
    public AABB getRenderBoundingBox(PortonDobleBlockEntity porton) {
        PortonDobleBlockEntity.Bisagra bisagra = porton.getBisagra();
        if (bisagra == null) {
            return new AABB(porton.getBlockPos());
        }
        boolean muroEnZ = bisagra.facing().getAxis() == Direction.Axis.X;
        BlockPos desde = bisagra.bisagra();
        // Las tres celdas del ancho por los tres pisos...
        BlockPos hasta = desde.offset(muroEnZ ? 0 : 2, 2, muroEnZ ? 2 : 0);
        // ...y el arco: 4 bloques hacia CADA lado de la NORMAL, que es el eje que NO es el del muro (si el portón
        // mira al este, el muro corre en Z y la hoja sale por X). Con el eje cambiado no se cubría el arco y la hoja
        // se recortaba al girar ✗; y son 4 y no 3 porque el canto de la hoja llega a 3,05 bloques (lleva encima su
        // grosor descentrado), así que 3 se quedaba 5 centésimas corto ✗. Medido con la comprobación numérica.
        BlockPos arcoA = desde.offset(muroEnZ ? -4 : 0, 0, muroEnZ ? 0 : -4);
        BlockPos arcoB = hasta.offset(muroEnZ ? 4 : 0, 0, muroEnZ ? 0 : 4);
        return AABB.encapsulatingFullBlocks(arcoA, arcoB);
    }
}
