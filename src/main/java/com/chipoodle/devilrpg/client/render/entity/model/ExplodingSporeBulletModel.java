package com.chipoodle.devilrpg.client.render.entity.model;

import com.chipoodle.devilrpg.DevilRpg;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

@OnlyIn(Dist.CLIENT)
public class ExplodingSporeBulletModel<T extends Entity> extends HierarchicalModel<T> {
   public static final ModelLayerLocation DEFAULT_LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, "explodingsporebullet"), "main");
   private final ModelPart root;
   private final ModelPart main;

   public ExplodingSporeBulletModel(ModelPart p_170916_) {
      this.root = p_170916_;
      this.main = p_170916_.getChild("main");
   }

   public static LayerDefinition createBodyLayer() {
      MeshDefinition meshdefinition = new MeshDefinition();
      PartDefinition partdefinition = meshdefinition.getRoot();
      partdefinition.addOrReplaceChild("main", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -4.0F, -1.0F, 8.0F, 8.0F, 2.0F).texOffs(0, 10).addBox(-1.0F, -4.0F, -4.0F, 2.0F, 8.0F, 8.0F).texOffs(20, 0).addBox(-4.0F, -1.0F, -4.0F, 8.0F, 2.0F, 8.0F), PartPose.ZERO);
      return LayerDefinition.create(meshdefinition, 64, 32);
   }

   public @NotNull ModelPart root() {
      return this.root;
   }

   public void setupAnim(@NotNull T entity, float p_103717_, float p_103718_, float p_103719_, float p_103720_, float p_103721_) {
      this.main.yRot = p_103720_ * ((float)Math.PI / 180F);
      this.main.xRot = p_103721_ * ((float)Math.PI / 180F);
   }
}