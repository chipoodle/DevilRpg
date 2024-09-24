package com.chipoodle.devilrpg.client.render.entity.layer;

import com.chipoodle.devilrpg.client.render.entity.model.SunflowerShulkerModel;
import com.chipoodle.devilrpg.client.render.entity.renderer.SunflowerShulkerRenderer;
import com.chipoodle.devilrpg.entity.SunflowerShulker;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

@OnlyIn(Dist.CLIENT)
public class SunflowerShulkerHeadLayer extends RenderLayer<SunflowerShulker, SunflowerShulkerModel<SunflowerShulker>> {
   public SunflowerShulkerHeadLayer(RenderLayerParent<SunflowerShulker, SunflowerShulkerModel<SunflowerShulker>> renderLayerParent) {
      super(renderLayerParent);
   }

   public void render(@NotNull PoseStack poseStack, MultiBufferSource multiBufferSource, int p_117447_, SunflowerShulker sunflowerShulker, float p_117449_, float p_117450_, float p_117451_, float p_117452_, float p_117453_, float p_117454_) {
      ResourceLocation resourcelocation = SunflowerShulkerRenderer.getTextureLocation(sunflowerShulker.getColor());
      VertexConsumer vertexconsumer = multiBufferSource.getBuffer(RenderType.entitySolid(resourcelocation));
      this.getParentModel().getHead().render(poseStack, vertexconsumer, p_117447_, LivingEntityRenderer.getOverlayCoords(sunflowerShulker, 0.0F));
   }
}