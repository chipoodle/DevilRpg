package com.chipoodle.devilrpg.client.render.entity.layer;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.client.render.entity.model.SoulWolfModel;
import com.chipoodle.devilrpg.client.render.entity.model.SoulWolfModelHeart;
import com.chipoodle.devilrpg.entity.SoulWolf;
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

@OnlyIn(Dist.CLIENT)
public class SoulWolfSkinLayer<T extends SoulWolf> extends RenderLayer<T, SoulWolfModelHeart<T>> {
    private static final ResourceLocation RESOURCE = ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, "textures/entity/soulwolf/tattoo2.png");
    private final EntityModel<T> model;

    public SoulWolfSkinLayer(RenderLayerParent<T, SoulWolfModelHeart<T>> renderLayerParent, EntityModelSet entityModelSet) {
        super(renderLayerParent);
        this.model = new SoulWolfModel<>(entityModelSet.bakeLayer(SoulWolfModel.LAYER_LOCATION));
    }

    public void render(@NotNull PoseStack poseStack, @NotNull MultiBufferSource multiBufferSource, int p_117034_, @NotNull T entity, float p_117036_, float p_117037_, float p_117038_, float p_117039_, float p_117040_, float p_117041_) {
        this.getParentModel().copyPropertiesTo(this.model);
        this.model.prepareMobModel(entity, p_117036_, p_117037_, p_117038_);
        this.model.setupAnim(entity, p_117036_, p_117037_, p_117039_, p_117040_, p_117041_);
        float f;
        float f1;
        float f2;

        f = 1.0F;
        f1 = 1.0F;
        f2 = 1.0F;

        VertexConsumer vertexconsumer = multiBufferSource.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation()));
        this.model.renderToBuffer(poseStack, vertexconsumer, p_117034_, OverlayTexture.NO_OVERLAY, 0x66FFFFFF);



    }

    protected ResourceLocation getTextureLocation() {
        return RESOURCE;
    }
}