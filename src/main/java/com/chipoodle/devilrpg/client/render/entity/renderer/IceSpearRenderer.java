package com.chipoodle.devilrpg.client.render.entity.renderer;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.entity.IceSpear;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Dibuja la lanza de hielo del wisp arquero como un <b>sprite de escarcha</b> que siempre mira a la cámara.
 * <p>
 * Se renderiza a mano (un quad con la textura) en vez de con {@code ThrownItemRenderer} porque la textura del
 * proyectil ({@code textures/entity/frostball/freeze_texture.png}) es una textura de entidad: vive fuera de
 * {@code textures/item} y {@code textures/block}, así que NO está en el atlas de bloques y un modelo de item
 * la mostraría como textura perdida. Aquí se enlaza directo con {@code RenderType.entityCutoutNoCull}, como
 * hacen los modelos de mobs, y como es un billboard nunca se ve "de canto" girando con el vuelo.
 */
@OnlyIn(Dist.CLIENT)
public class IceSpearRenderer extends EntityRenderer<IceSpear> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, "textures/entity/frostball/freeze_texture.png");
    /** Tamaño del sprite en bloques: pequeño, es un proyectil. */
    private static final float SIZE = 0.5F;

    public IceSpearRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
        this.shadowStrength = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(IceSpear entity) {
        return TEXTURE;
    }

    @Override
    public void render(IceSpear entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        // Billboard: el quad se orienta con la cámara, así siempre se ve la escarcha de frente.
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.scale(SIZE, SIZE, SIZE);
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        // Se ilumina a plena luz (como el brillo del hielo), sin depender de la luz del sitio.
        int light = LightTexture.FULL_BRIGHT;
        vertex(consumer, pose, light, -0.5F, 0.5F, 0.0F, 0.0F);
        vertex(consumer, pose, light, 0.5F, 0.5F, 1.0F, 0.0F);
        vertex(consumer, pose, light, 0.5F, -0.5F, 1.0F, 1.0F);
        vertex(consumer, pose, light, -0.5F, -0.5F, 0.0F, 1.0F);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, int light,
                               float x, float y, float u, float v) {
        consumer.addVertex(pose, x, y, 0.0F)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
    }
}
