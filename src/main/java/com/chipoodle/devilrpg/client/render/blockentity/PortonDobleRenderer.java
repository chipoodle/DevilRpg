package com.chipoodle.devilrpg.client.render.blockentity;

import com.chipoodle.devilrpg.block.DoubleGateBlock;
import com.chipoodle.devilrpg.blockentity.PortonDobleBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/**
 * <b>EL RENDERIZADOR DEL PORTÓN DOBLE ABATIBLE</b>: dibuja las dos hojas girando sobre su bisagra.
 * <p>
 * <b>Por qué un renderizador y no un modelo</b> (I234, medido): la geometría de un modelo de bloque está encerrada
 * entre <b>−16 y 32 píxeles</b> (`BlockElement`), o sea 1 bloque a un lado de la celda que dibuja y 2 al otro. Con eso
 * la hoja abierta queda <b>cruzando el muro</b> —«se queda en medio del marco, no se abre totalmente a lado»— ✗. Un
 * renderizador dibuja donde quiera, así que la hoja puede quedar <b>entera de 3 bloques hacia un lado</b>, y encima
 * puede girar <b>interpolado</b> (que un modelo de bloque no puede: el estado cambia de golpe, I232) ✓.
 * <p>
 * <b>Y por eso el estado ABIERTO ya no dibuja nada</b>: sus modelos van con {@code "elements": []}
 * (`tools/arnes/hacer_modelos_del_porton.py`), así que la única geometría abierta es la de aquí. Sin eso el portón
 * saldría <b>dos veces</b>: una con el modelo viejo (cruzando el muro) y otra con la hoja girada ✗.
 * <p>
 * <b>Qué dibuja, exactamente</b>: las <b>nueve hojas</b> del portón (3 celdas de ancho × 3 pisos) como <b>cubos</b>, cada
 * una con <b>su tercio de la textura</b> —el mismo reparto que los modelos cerrados, que es lo que hace que las tres
 * celdas se lean como una sola puerta y no como tres listones— y las <b>seis caras</b> de cada cubo, para que se vea
 * desde los dos lados y no quede hueco por el canto. El giro va de <b>0° (tumbada en el plano del muro)</b> a <b>90°
 * (perpendicular al muro)</b>, con el progreso <b>interpolado con {@code partialTick}</b> para que se vea fluido.
 * <p>
 * <b>Las dos hojas giran cada una sobre SU bisagra</b>, y las bisagras están en los <b>extremos exteriores</b> del portón
 * (así lo construye `VillageGenerator.sellarLasCeldasDelPorton`: la hoja izquierda pegada a su jamba, la derecha a la
 * suya y la juntura en el medio). Es lo correcto: abriendo las dos hacia fuera, <b>todo el centro del hueco queda
 * libre</b> y sólo quedan los dos cantos pegados a las jambas — como abre un portón de dos hojas de verdad ✓.
 */
public class PortonDobleRenderer implements BlockEntityRenderer<PortonDobleBlockEntity> {

    /** El tercio HORIZONTAL de la textura (u): el trozo que le toca a cada una de las tres celdas. 16/3 = 5,33 px. */
    private static final float TERCIO_U = 16.0F / 3.0F;

    /** El tercio VERTICAL (v): uno por piso. */
    private static final float TERCIO_V = 16.0F / 3.0F;

    /**
     * El grosor de la hoja, en bloques: <b>2 píxeles de los 16 de la celda</b> (1/8). Es el mismo que declara
     * {@link DoubleGateBlock}, y tiene que serlo: la hoja se ve como un listón, y la colisión (que sigue vacía al
     * abrir) ya deja pasar.
     */
    private static final float GROSOR = 2.0F / 16.0F;

    /**
     * Los 8 vértices de una hoja en su sistema local: la hoja es <b>x 0..1</b> (el ancho de una celda, que al girar se
     * convierte en la profundidad del arco), <b>y 0..1</b> (su piso) y <b>z 0..{@value #GROSOR}</b> (el canto).
     */
    private static final float[][] V = {
            { 0.0F, 0.0F, 0.0F }, // 0
            { 1.0F, 0.0F, 0.0F }, // 1
            { 1.0F, 1.0F, 0.0F }, // 2
            { 0.0F, 1.0F, 0.0F }, // 3
            { 0.0F, 0.0F, GROSOR },// 4
            { 1.0F, 0.0F, GROSOR },// 5
            { 1.0F, 1.0F, GROSOR },// 6
            { 0.0F, 1.0F, GROSOR },// 7
    };

    /** Las cuatro esquinas de cada cara, en orden <b>antihorario visto desde fuera</b> (si no, se descarta la cara). */
    private static final int[][] CARAS = {
            { 0, 1, 2, 3 }, // norte (−Z)
            { 5, 4, 7, 6 }, // sur (+Z)
            { 1, 5, 6, 2 }, // este (+X)
            { 4, 0, 3, 7 }, // oeste (−X)
            { 3, 2, 6, 7 }, // arriba (+Y)
            { 4, 5, 1, 0 }, // abajo (−Y)
    };

    private static final Direction[] NORMALES = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN
    };

    /**
     * El renderizador no necesita ningún modelo ni textura horneada —dibuja con {@code VertexConsumer} directo—, pero
     * el juego construye los renderizadores de bloque con un {@link BlockEntityRendererProvider.Context}, así que el
     * constructor tiene que aceptarlo (es la firma que pide {@code PortonDobleRenderer::new}).
     */
    public PortonDobleRenderer(BlockEntityRendererProvider.Context contexto) {
        // Sin estado que sacar del contexto: la textura se resuelve por el render type (`RenderType.cutout()` usa la
        // del atlas de bloques) y la geometría se calcula. El parámetro está porque lo exige la firma.
    }

    @Override
    public void render(PortonDobleBlockEntity porton, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
            int luzEmpaquetada, int overlay) {
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
        // EL REPOSO LO DIBUJA EL MODELO, NO ESTE RENDERIZADOR: cerrado y sin haber girado todavía, la hoja la pinta su
        // modelo (el estado cerrado no se toca: se ve bien) y dibujarla aquí otra vez sería pintar la misma cara dos
        // veces en el mismo píxel (parpadeo). En cuanto el ticker corre una vez manda este renderizador —también al
        // final del cierre—, porque es el único que sabe dónde está la hoja entre dos ticks (partialTick) ✓.
        if (!abierto && porton.estaEnReposo()) {
            return;
        }
        // PROGRESO INTERPOLADO: entre dos ticks el mundo se dibuja varias veces (y los FPS no son 20), así que sin
        // interpolar el giro se vería a saltos de 7,5° ✗. El progreso viaja en pasos de 1/12 y aquí se rellena lo que
        // falta entre el tick anterior y éste.
        float progresoInterpolado = porton.getProgresoAnteriorDelGiro()
                + (porton.getProgresoDelGiro() - porton.getProgresoAnteriorDelGiro()) * partialTick;
        // Y EL SIGNO, que es lo que hace que las hojas se abran hacia FUERA en vez de cruzarse: la celda de la bisagra
        // es la de fuera del lado izquierdo, así que su hoja (izquierda) sale hacia el lado NEGATIVO del eje del muro
        // y la derecha hacia el POSITIVO. Con las dos hacia el mismo lado, las dos hojas acabarían encima de la misma
        // jamba y una se metería dentro de la otra ✗.
        // El eje del muro: el portón mira a un lado, así que el muro corre DE TRAVÉS (si mira al este/oeste, en X).
        boolean muroEnX = facing.getAxis() == Direction.Axis.X;

        VertexConsumer buffer = buffers.getBuffer(RenderType.cutout());

        for (int hoja = 0; hoja < 3; hoja++) {
            BlockPos celda = muroEnX
                    ? new BlockPos(celdaBisagra.getX() + hoja, celdaBisagra.getY(), celdaBisagra.getZ())
                    : new BlockPos(celdaBisagra.getX(), celdaBisagra.getY(), celdaBisagra.getZ() + hoja);
            if (!esCeldaDelPorton(level, celda, facing)) {
                // Si a la estructura le falta una celda (mundo a medio cargar, portón roto a trozos) no se dibuja el
                // portón entero: dibujarlo sería pintar una puerta que ya no está en el mundo ✗.
                return;
            }
            boolean izquierda = hoja == 0;
            double grados = (izquierda ? -1.0D : 1.0D) * progresoInterpolado * 90.0D;
            // LA JAMBA: el canto de esta celda que da al CENTRO del portón (la izquierda abate por su canto derecho y
            // la derecha por el izquierdo). Es el eje de giro, y por eso se mide en coordenadas de la CELDA (0 o 1).
            double jamba = izquierda ? 1.0D : 0.0D;
            // El tercio de textura que le toca a esta celda, contado a lo largo del muro desde la bisagra (0, 1, 2),
            // que es el MISMO reparto que tienen los modelos cerrados: entre las tres cubren la textura entera y las
            // tres se leen como una sola puerta. Y sale bien en los dos sentidos sin espejar nada: la hoja izquierda
            // lleva su juntura (u alto) en su jamba izquierda, y la derecha lleva el suyo (u bajo) en su jamba derecha,
            // que es exactamente cómo están los tercios en los modelos de cada celda ✓.
            float u0 = hoja * TERCIO_U;
            for (int piso = 0; piso < 3; piso++) {
                poseStack.pushPose();
                dibujarHoja(buffer, poseStack, celdaBisagra, muroEnX, hoja, piso, jamba, grados, u0,
                        piso * TERCIO_V, luzEmpaquetada);
                poseStack.popPose();
            }
        }
    }

    /**
     * Dibuja una hoja (un piso de una celda) como un cubo de seis caras.
     * <p>
     * El orden es <b>primero trasladar a la celda, luego al eje de giro, y girar al final</b>, y no al revés: el giro
     * tiene que ser <b>sobre la jamba</b>. Con la jamba en el origen del sistema cuando se gira, la hoja barre el arco
     * con su canto pegado a ella; girando antes de trasladar, la hoja giraría sobre la esquina del bloque y se saldría
     * del portón ✗. El {@code pushPose}/{@code popPose} es por eso mismo: cada hoja parte del sistema del bloque de la
     * bisagra, sin arrastrar la traslación de la anterior.
     */
    private void dibujarHoja(VertexConsumer buffer, PoseStack poseStack, BlockPos celdaBisagra, boolean muroEnX,
            int hoja, int piso, double jamba, double grados, float u0, float v0, int luz) {
        // Al sistema del BLOQUE de la bisagra (el renderizador dibuja desde su celda).
        poseStack.translate(celdaBisagra.getX(), celdaBisagra.getY(), celdaBisagra.getZ());
        // A la celda de esta hoja (por la línea del muro) y a su piso (por la altura).
        poseStack.translate(muroEnX ? hoja : 0.0D, piso, muroEnX ? 0.0D : hoja);
        // Al eje de giro: la jamba de ESTA hoja.
        poseStack.translate(muroEnX ? jamba : 0.0D, 0.0D, muroEnX ? 0.0D : jamba);
        poseStack.mulPose(Axis.YP.rotationDegrees((float) grados));

        PoseStack.Pose pose = poseStack.last();
        for (int cara = 0; cara < CARAS.length; cara++) {
            int[] esquinas = CARAS[cara];
            Direction normal = NORMALES[cara];
            float brillo = brilloDeCara(normal, grados);
            float[] uv = uvDeCara(normal, u0, v0);
            // La normal hay que girarla CON la hoja: si no, la cara que va quedando de canto se ilumina como si
            // siguiera de frente y el giro no se lee ✗.
            Vector3f n = new Vector3f(normal.getStepX(), 0.0F, normal.getStepZ());
            n.rotateY((float) Math.toRadians(-grados));
            float ny = normal.getStepY();
            for (int i = 0; i < 4; i++) {
                float[] v = V[esquinas[i]];
                buffer.addVertex(pose, v[0], v[1], v[2])
                        .setColor(brillo, brillo, brillo, 1.0F)
                        .setUv(uv[i * 2], uv[i * 2 + 1])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(luz)
                        .setNormal(n.x(), ny, n.z());
            }
        }
    }

    /** ¿Esa celda es del portón? (mismo bloque y mismo {@code FACING}). */
    private static boolean esCeldaDelPorton(Level level, BlockPos celda, Direction facing) {
        BlockState estado = level.getBlockState(celda);
        return estado.getBlock() instanceof DoubleGateBlock && estado.getValue(DoubleGateBlock.FACING) == facing;
    }

    /**
     * Las <b>cuatro</b> coordenadas de textura de la cara, en el orden de sus vértices: {@code u0, v0} para la primera
     * esquina y así hasta la cuarta, que es lo que espera {@code setUv}.
     */
    private static float[] uvDeCara(Direction normal, float u0, float v0) {
        float u1 = u0 + TERCIO_U;
        float v1 = v0 + TERCIO_V;
        // Las caras de delante y detrás enseñan el tercio de SU columna: es como las celdas cerradas cubren la textura
        // entera entre las tres, y lo que hace que al abrirse se siga leyendo como una sola puerta.
        return switch (normal.getAxis()) {
            case Z -> new float[] { u0, v0, u1, v0, u1, v1, u0, v1 };
            // Los cantos (arriba, abajo y los lados finos) no tienen tercio propio en la textura: se estiran con su
            // vertical del piso, que es lo que hace el modelo cerrado con sus caras este/oeste.
            default -> new float[] { 0.0F, v0, 16.0F, v0, 16.0F, v1, 0.0F, v1 };
        };
    }

    /**
     * El brillo de la cara, el <b>mismo que el juego le da a los modelos de bloque</b>: arriba 1, abajo 0,5 y los lados
     * según su eje (0,8 los que miran al norte/sur, 0,6 al este/oeste). Se calcula sobre la normal <b>ya girada</b>
     * porque, si no, la hoja se iluminaría igual de frente que de canto y el giro no se leería ✗. El signo no importa
     * (el juego mira el EJE de la normal, no hacia dónde apunta), así que basta con el eje dominante que queda tras
     * girar.
     */
    private static float brilloDeCara(Direction normal, double grados) {
        if (normal.getAxis() == Direction.Axis.Y) {
            return normal == Direction.UP ? 1.0F : 0.5F;
        }
        Vector3f n = new Vector3f(normal.getStepX(), 0.0F, normal.getStepZ());
        n.rotateY((float) Math.toRadians(-grados));
        return Math.abs(n.z()) >= Math.abs(n.x()) ? 0.8F : 0.6F;
    }

    /**
     * <b>Dónde se dibuja el portón</b>: es lo que el juego usa para decidir si el renderizador entra en pantalla, y su
     * valor por defecto es <b>la celda de la entidad</b> — la bisagra, que está en una ESQUINA del portón. Con eso,
     * mirando el portón abierto desde el otro lado del muro la hoja entera cae <b>fuera del recorte</b> y
     * <b>desaparece</b> ✗. La caja cubre las nueve celdas (3 por la línea del muro, 3 de alto) más el <b>arco entero del
     * giro</b> (3 bloques hacia cada lado de la normal): el giro no se sabe hacia dónde va a estar (la hoja izquierda
     * sale por un lado y la derecha por el otro), así que se cubren los dos ✓.
     */
    @Override
    public AABB getRenderBoundingBox(PortonDobleBlockEntity porton) {
        PortonDobleBlockEntity.Bisagra bisagra = porton.getBisagra();
        if (bisagra == null) {
            return new AABB(porton.getBlockPos());
        }
        boolean muroEnX = bisagra.facing().getAxis() == Direction.Axis.X;
        BlockPos desde = bisagra.bisagra();
        // Las TRES celdas del ancho por los TRES pisos.
        BlockPos hasta = desde.offset(muroEnX ? 2 : 0, 2, muroEnX ? 0 : 2);
        // Y el arco: 3 bloques hacia cada lado de la normal (que es el eje que NO es el del muro).
        BlockPos esquinaA = desde.offset(muroEnX ? 0 : -3, 0, muroEnX ? -3 : 0);
        BlockPos esquinaB = hasta.offset(muroEnX ? 0 : 3, 0, muroEnX ? 3 : 0);
        return AABB.encapsulatingFullBlocks(esquinaA, esquinaB);
    }
}
