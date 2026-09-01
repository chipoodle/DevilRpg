package com.chipoodle.devilrpg.client.render.entity.model;

import com.chipoodle.devilrpg.DevilRpg;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

@OnlyIn(Dist.CLIENT)
public class CubeModel<T extends LivingEntity> extends HierarchicalModel<T> {

    //public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, "soulbearheart"), "main");
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, "cubemodel"), "armor");

    public final ModelPart root;
    private final ModelPart shield;


    public CubeModel(ModelPart root) {
        //super(model, true, 16.0F, 4.0F, 2.25F, 2.0F, 24);
        super(RenderType::entityTranslucent);
        this.root = root;
        shield = root.getChild("shield");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        partdefinition.addOrReplaceChild("shield",
                CubeListBuilder.create().texOffs(18, 14)
                        .addBox(-7.0F, -7.0F, -7.0F, 14.0F, 19.0F, 14.0F),
                PartPose.offsetAndRotation(0.0F, 14.0F, -3.0F, ((float) Math.PI / 2F), 0.0F, 0.0F));


        return LayerDefinition.create(meshdefinition, 64, 32);
    }

    public void setupAnim(@NotNull T entityIn, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        //super.setupAnim(entityIn, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
    }

    @Override
    public void renderToBuffer(@NotNull PoseStack p_103111_, @NotNull VertexConsumer p_103112_, int p_103113_, int p_103114_, int p_103115_) {
        super.renderToBuffer(p_103111_, p_103112_, p_103113_, p_103114_, p_103115_);
    }

    @Override
    public @NotNull ModelPart root() {
        return root;
    }

    /*public void prepareMobModel(T p_104132_, float p_104133_, float p_104134_, float p_104135_) {
        this.body.setPos(0.0F, 14.0F, 2.0F);
        this.body.xRot = ((float) Math.PI / 2F);
        //this.body.zRot = p_104132_.getBodyRollAngle(p_104135_, -0.16F);
    }*/
}