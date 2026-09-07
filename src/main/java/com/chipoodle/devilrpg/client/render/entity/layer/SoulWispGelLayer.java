package com.chipoodle.devilrpg.client.render.entity.layer;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.client.render.entity.model.CubeModel;
import com.chipoodle.devilrpg.entity.SoulWisp;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

/**
 * Campo de fuerza envolvente del wisp: un cubo transparente que gira alrededor de la entidad,
 * mostrando la textura {@code ice4.png} con el mismo efecto de "energySwirl" (gel) que el lobo/oso.
 */
@OnlyIn(Dist.CLIENT)
public class SoulWispGelLayer<T extends SoulWisp, M extends EntityModel<T>> extends RenderLayer<T, M> {

    private static final ResourceLocation RESOURCE = ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, "textures/entity/soul/ice4.png");

    private final EntityModel<T> model;

    public SoulWispGelLayer(RenderLayerParent<T, M> renderLayerParent, EntityModelSet entityModelSet) {
        super(renderLayerParent);
        this.model = new CubeModel<>(entityModelSet.bakeLayer(CubeModel.LAYER_LOCATION));
    }

    @Override
    public void render(@NotNull PoseStack poseStack, @NotNull MultiBufferSource multiBufferSource, int p_116972_,
                       @NotNull T entity, float p_116974_, float p_116975_, float p_116976_,
                       float p_116977_, float p_116978_, float p_116979_) {
        if (entity.isInvisible()) {
            return;
        }
        float f = (float) entity.tickCount + p_116976_;
        EntityModel<T> entitymodel = this.model;
        entitymodel.prepareMobModel(entity, p_116974_, p_116975_, p_116976_);
        this.getParentModel().copyPropertiesTo(entitymodel);
        VertexConsumer vertexconsumer = multiBufferSource.getBuffer(
                RenderType.energySwirl(RESOURCE, f * 0.01F % 1.0F, f * 0.01F % 1.0F));
        entitymodel.setupAnim(entity, p_116974_, p_116975_, p_116977_, p_116978_, p_116979_);
        entitymodel.renderToBuffer(poseStack, vertexconsumer, p_116972_, OverlayTexture.NO_OVERLAY, 0xFF7F7F7F);
    }
}
