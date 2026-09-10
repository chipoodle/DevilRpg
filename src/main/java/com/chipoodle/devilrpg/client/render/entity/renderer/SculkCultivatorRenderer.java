package com.chipoodle.devilrpg.client.render.entity.renderer;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.entity.SculkCultivatorEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

/**
 * Renderer del {@link SculkCultivatorEntity}: usa el modelo humanoide del zombie con la textura del zombie
 * agresivo y un <b>tinte escarlata/morado</b> encima, para que se vea como un cultista del sculk (mismo
 * color que el resto de enemigos del mod) y no como un zombie común.
 */
@OnlyIn(Dist.CLIENT)
public class SculkCultivatorRenderer extends MobRenderer<SculkCultivatorEntity, ZombieModel<SculkCultivatorEntity>> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, "textures/entity/aggressive_zombie/aggressive_zombie.png");
    /** Tinte escarlata/morado del cultivo de sculk (ARGB: alfa, rojo, verde, azul). */
    private static final int SCULK_TINT = 0xB03A1540;

    public SculkCultivatorRenderer(EntityRendererProvider.Context context) {
        super(context, new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
        this.addLayer(new TintLayer(this));
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull SculkCultivatorEntity entity) {
        return TEXTURE;
    }

    /** Layer que repinta el modelo con el tinte escarlata del sculk (le da el color propio). */
    static class TintLayer extends RenderLayer<SculkCultivatorEntity, ZombieModel<SculkCultivatorEntity>> {

        TintLayer(RenderLayerParent<SculkCultivatorEntity, ZombieModel<SculkCultivatorEntity>> parent) {
            super(parent);
        }

        @Override
        public void render(@NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int light,
                           @NotNull SculkCultivatorEntity entity, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(TEXTURE));
            this.getParentModel().renderToBuffer(poseStack, consumer, light,
                    LivingEntityRenderer.getOverlayCoords(entity, 0.0F), SCULK_TINT);
        }
    }
}
