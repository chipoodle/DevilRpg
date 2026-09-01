package com.chipoodle.devilrpg.client.render.entity.layer;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.client.render.entity.model.WerewolfTransformedModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class WerewolfArmorLayer<T extends LivingEntity, M extends WerewolfTransformedModel<T>, A extends WerewolfTransformedModel<T>> extends RenderLayer<T, M> {
   private final A innerModel;
   private final A outerModel;

   public WerewolfArmorLayer(RenderLayerParent<T, M> p_117075_, A p_117076_, A p_117077_) {
      super(p_117075_);
      this.innerModel = p_117076_;
      this.outerModel = p_117077_;
   }

   public void render(PoseStack p_117096_, MultiBufferSource p_117097_, int p_117098_, T livingEntity, float p_117100_, float p_117101_, float p_117102_, float p_117103_, float p_117104_, float p_117105_) {
      this.renderArmorPiece(p_117096_, p_117097_, livingEntity, EquipmentSlot.CHEST, p_117098_, this.getArmorModel(EquipmentSlot.CHEST));
      this.renderArmorPiece(p_117096_, p_117097_, livingEntity, EquipmentSlot.LEGS, p_117098_, this.getArmorModel(EquipmentSlot.LEGS));
      this.renderArmorPiece(p_117096_, p_117097_, livingEntity, EquipmentSlot.FEET, p_117098_, this.getArmorModel(EquipmentSlot.FEET));
      this.renderArmorPiece(p_117096_, p_117097_, livingEntity, EquipmentSlot.HEAD, p_117098_, this.getArmorModel(EquipmentSlot.HEAD));
   }

   private void renderArmorPiece(PoseStack p_117119_, MultiBufferSource multiBufferSource, T livingEntity, EquipmentSlot p_117122_, int p_117123_, A p_117124_) {
      ItemStack itemstack = livingEntity.getItemBySlot(p_117122_);
      if (itemstack.getItem() instanceof ArmorItem armoritem) {
         if (armoritem.getEquipmentSlot() == p_117122_) {
            this.getParentModel().copyPropertiesTo(p_117124_);
            this.setPartVisibility(p_117124_, p_117122_);
            net.minecraft.client.model.Model model = getArmorModelHook(livingEntity, itemstack, p_117122_, p_117124_);
            boolean flag = this.usesInnerModel(p_117122_);
            boolean flag1 = itemstack.hasFoil();
            ArmorMaterial armormaterial = armoritem.getMaterial().value();
            int i = net.neoforged.neoforge.client.extensions.common.IClientItemExtensions.of(itemstack).getDefaultDyeColor(itemstack);
            for (int j = 0; j < armormaterial.layers().size(); j++) {
               ArmorMaterial.Layer layer = armormaterial.layers().get(j);
               int k = net.neoforged.neoforge.client.extensions.common.IClientItemExtensions.of(itemstack).getArmorLayerTintColor(itemstack, livingEntity, layer, j, i);
               if (k != 0) {
                  this.renderModel(p_117119_, multiBufferSource, p_117123_, flag1, model, k, this.getArmorResource(livingEntity, itemstack, layer, flag, p_117122_));
               }
            }

         }
      }
   }

   protected void setPartVisibility(A p_117126_, EquipmentSlot p_117127_) {
      //p_117126_.setAllVisible(false);
      switch (p_117127_) {
         case HEAD:
            p_117126_.head.visible = true;
            //p_117126_.hat.visible = true;
            break;
         case CHEST:
            p_117126_.body.visible = true;
            p_117126_.rightArm.visible = true;
            p_117126_.leftArm.visible = true;
            break;
         case LEGS:
            p_117126_.body.visible = true;
            p_117126_.rightLeg.visible = true;
            p_117126_.leftLeg.visible = true;
            break;
         case FEET:
            p_117126_.rightLeg.visible = true;
            p_117126_.leftLeg.visible = true;
      }

   }

   private void renderModel(PoseStack p_117107_, MultiBufferSource p_117108_, int p_117109_, boolean p_117111_, net.minecraft.client.model.Model p_117112_, int color, ResourceLocation armorResource) {
      VertexConsumer vertexconsumer = ItemRenderer.getArmorFoilBuffer(p_117108_, RenderType.armorCutoutNoCull(armorResource), p_117111_);
      p_117112_.renderToBuffer(p_117107_, vertexconsumer, p_117109_, OverlayTexture.NO_OVERLAY, color);
   }

   private A getArmorModel(EquipmentSlot p_117079_) {
      return this.usesInnerModel(p_117079_) ? this.innerModel : this.outerModel;
   }

   private boolean usesInnerModel(EquipmentSlot p_117129_) {
      return p_117129_ == EquipmentSlot.LEGS;
   }

   /*=================================== FORGE START =========================================*/

   /**
    * Hook to allow item-sensitive armor model. for HumanoidArmorLayer.
    */
   protected net.minecraft.client.model.Model getArmorModelHook(T entity, ItemStack itemStack, EquipmentSlot slot, A model) {
      return model;
   }

   /**
    * More generic ForgeHook version of the above function, it allows for Items to have more control over what texture they provide.
    *
    * @param entity Entity wearing the armor
    * @param stack ItemStack for the armor
    * @param layer Armor material layer
    * @param innerModel Whether the inner armor model is used
    * @param slot Slot ID that the item is in
    * @return ResourceLocation pointing at the armor's texture
    */
   public ResourceLocation getArmorResource(net.minecraft.world.entity.Entity entity, ItemStack stack, ArmorMaterial.Layer layer, boolean innerModel, EquipmentSlot slot) {
      return net.neoforged.neoforge.client.ClientHooks.getArmorTexture(entity, stack, layer, innerModel, slot);
   }
   /*=================================== FORGE END ===========================================*/
}
