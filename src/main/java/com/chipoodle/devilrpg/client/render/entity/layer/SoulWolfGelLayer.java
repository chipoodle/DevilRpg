package com.chipoodle.devilrpg.client.render.entity.layer;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.client.render.entity.model.SoulWolfModel;
import com.chipoodle.devilrpg.client.render.entity.model.SoulWolfModelHeart;
import com.chipoodle.devilrpg.entity.SoulWolf;
import com.chipoodle.devilrpg.util.IRenderUtilities;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

@OnlyIn(Dist.CLIENT)
public class SoulWolfGelLayer<T extends SoulWolf> extends GhostEnergyLayer<T, SoulWolfModelHeart<T>> {
    //private static final ResourceLocation WOLF_GEL = new ResourceLocation(DevilRpg.MODID + ":textures/entity/soul/soulgelghost.png");
    private static final ResourceLocation WOLF_GEL = new ResourceLocation(DevilRpg.MODID + ":textures/entity/soul/air_small.png");
    private static final ResourceLocation WOLF_GEL_ANGRY = new ResourceLocation(DevilRpg.MODID + ":textures/entity/soul/firestone3.png");
    private final EntityModel<T> soulWolfModel;
    RenderLayerParent<T, SoulWolfModelHeart<T>> renderLayerParent;

    public SoulWolfGelLayer(RenderLayerParent<T, SoulWolfModelHeart<T>> renderLayerParent, EntityModelSet entityModelSet) {
        super(renderLayerParent);
        this.soulWolfModel = new SoulWolfModel<>(entityModelSet.bakeLayer(SoulWolfModel.LAYER_LOCATION));
        this.renderLayerParent = renderLayerParent;
    }

    public void render(@NotNull PoseStack poseStack, @NotNull MultiBufferSource multiBufferSource, int p_117472_, @NotNull T entity, float p_117474_, float p_117475_, float p_117476_, float p_117477_, float p_117478_, float p_117479_) {
        groovyMethod(poseStack, multiBufferSource, p_117472_, entity, p_117474_, p_117475_, p_117476_, p_117477_, p_117478_, p_117479_);
        super.render(poseStack, multiBufferSource, p_117472_, entity, p_117474_, p_117475_, p_117476_, p_117477_, p_117478_, p_117479_);
    }

    protected float xOffset(float p_225634_1_) {
        return p_225634_1_ * 0.01F;
    }

    protected @NotNull ResourceLocation getTextureLocation(@NotNull SoulWolf soulWolf) {
        return soulWolf.isAngry()? WOLF_GEL_ANGRY : WOLF_GEL;
    }

    @Override
    protected EntityModel<T> model() {
        return soulWolfModel;
    }

    private void groovyMethod(PoseStack p_117421_, MultiBufferSource p_117422_, int p_117423_, T entity, float p_117425_, float p_117426_, float partialTicks, float p_117428_, float p_117429_, float p_117430_) {
        if (!entity.isInvisible()) {
            VertexConsumer vertexconsumer;
            vertexconsumer = p_117422_.getBuffer(RenderType.entityCutoutNoCull(this.getTextureLocation(entity)));
            float[] rgbArray = IRenderUtilities.groovyRed(entity, partialTicks);
            this.getParentModel().prepareMobModel(entity, p_117425_, p_117426_, partialTicks);
            this.getParentModel().setupAnim(entity, p_117425_, p_117426_, p_117428_, p_117429_, p_117430_);
            this.getParentModel().renderToBuffer(p_117421_, vertexconsumer, p_117423_, LivingEntityRenderer.getOverlayCoords(entity, 0.0F), 1.0F, 1.0F, 1.0F, 1.0F);
            coloredCutoutModelCopyLayerRender(this.getParentModel(), this.getParentModel(), this.getTextureLocation(entity), p_117421_, p_117422_, p_117423_, entity, p_117425_, p_117426_, p_117428_, p_117429_, p_117430_, partialTicks, rgbArray[0], rgbArray[1], rgbArray[2]);
        }
    }
}