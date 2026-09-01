package com.chipoodle.devilrpg.client.render.entity.layer;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.client.render.entity.model.SoulBearModel;
import com.chipoodle.devilrpg.client.render.entity.model.SoulBearModelHeart;
import com.chipoodle.devilrpg.entity.SoulBear;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

@OnlyIn(Dist.CLIENT)
public class SoulBearArmorLayer<T extends SoulBear> extends RenderLayer<T, SoulBearModelHeart<T>> {
    private static final ResourceLocation RESOURCE = ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, "textures/entity/soulbear/soulbear_armor6.png");
    private final EntityModel<T> model;

    public SoulBearArmorLayer(RenderLayerParent<T, SoulBearModelHeart<T>> renderLayerParent, EntityModelSet entityModelSet) {
        super(renderLayerParent);
        this.model = new SoulBearModel<>(entityModelSet.bakeLayer(SoulBearModel.LAYER_LOCATION));
    }

    public void render(@NotNull PoseStack poseStack, @NotNull MultiBufferSource multiBufferSource, int p_117034_, T entity, float p_117036_, float p_117037_, float p_117038_, float p_117039_, float p_117040_, float p_117041_) {
        ItemStack itemstack = entity.getArmor();
        if (itemstack.getItem() instanceof ArmorItem) {
            this.getParentModel().copyPropertiesTo(this.model);
            this.model.prepareMobModel(entity, p_117036_, p_117037_, p_117038_);
            this.model.setupAnim(entity, p_117036_, p_117037_, p_117039_, p_117040_, p_117041_);
            float f;
            float f1;
            float f2;
            DyedItemColor dyeditemcolor = itemstack.get(DataComponents.DYED_COLOR);
            if (dyeditemcolor != null) {
                int i = dyeditemcolor.rgb();
                f = (float) (i >> 16 & 255) / 255.0F;
                f1 = (float) (i >> 8 & 255) / 255.0F;
                f2 = (float) (i & 255) / 255.0F;
            } else {
                f = 1.0F;
                f1 = 1.0F;
                f2 = 1.0F;
            }

            //VertexConsumer vertexconsumer = multiBufferSource.getBuffer(RenderType.entityCutoutNoCull(horsearmoritem.getTexture()));
            VertexConsumer vertexconsumer = multiBufferSource.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation()));
            int color = 0x7F000000 | ((int) (f * 255.0F) << 16) | ((int) (f1 * 255.0F) << 8) | (int) (f2 * 255.0F);
            this.model.renderToBuffer(poseStack, vertexconsumer, p_117034_, OverlayTexture.NO_OVERLAY, color);
        }
    }

    protected ResourceLocation getTextureLocation() {
        return RESOURCE;
    }
}