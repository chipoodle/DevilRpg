package com.chipoodle.devilrpg.client.render.entity.model;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.entity.SunflowerShulker;
import com.google.common.collect.ImmutableList;
import net.minecraft.client.model.ListModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

@OnlyIn(Dist.CLIENT)
public class SunflowerShulkerModel<T extends SunflowerShulker> extends ListModel<T> {

    public static final ModelLayerLocation DEFAULT_LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, "sunflowershulker"), "main");
    private final ModelPart base;
    private final ModelPart lid;
    private final ModelPart head;

    public SunflowerShulkerModel(ModelPart root) {
        super(RenderType::entityCutoutNoCullZOffset);
        this.base = root.getChild("base");
        this.lid = root.getChild("lid");
        this.head = root.getChild("head");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition base1 = partdefinition.addOrReplaceChild("base", CubeListBuilder.create().texOffs(36, 35).addBox(-6.0F, -1.0F, -6.0F, 12.0F, 1.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition cerda2_r1 = base1.addOrReplaceChild("cerda2_r1", CubeListBuilder.create().texOffs(0, 47).addBox(-6.75F, -2.5F, 0.0F, 13.5F, 8.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0052F, -6.5F, -0.0199F, 0.0F, -2.3562F, 0.0F));

        PartDefinition cerda1_r1 = base1.addOrReplaceChild("cerda1_r1", CubeListBuilder.create().texOffs(48, 0).addBox(-6.75F, -2.5F, 0.0F, 13.5F, 8.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0659F, -6.5F, 0.0806F, 0.0F, -0.7854F, 0.0F));

        PartDefinition lid = partdefinition.addOrReplaceChild("lid", CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, -16.0F, -8.0F, 16.0F, 2.0F, 16.0F, new CubeDeformation(0.0F))
                .texOffs(0, 18).addBox(-7.0F, -18.0F, -7.0F, 14.0F, 2.0F, 14.0F, new CubeDeformation(0.0F))
                .texOffs(0, 34).addBox(-6.0F, -19.0F, -6.0F, 12.0F, 1.0F, 12.0F, new CubeDeformation(0.0F))
                .texOffs(42, 18).addBox(-5.0F, -20.0F, -5.0F, 10.0F, 1.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition head = partdefinition.addOrReplaceChild("head", CubeListBuilder.create().texOffs(85, 31).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 13.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -12.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 128, 128);
    }

    public void setupAnim(T p_103735_, float p_103736_, float p_103737_, float p_103738_, float p_103739_, float p_103740_) {
        float f = p_103738_ - (float) p_103735_.tickCount;
        float f1 = (0.5F + p_103735_.getClientPeekAmount(f)) * (float) Math.PI;
        //float f2 = -1.0F + Mth.sin(f1);
        float f3 = 0.0F;
        if (f1 > (float) Math.PI) {
            f3 = Mth.sin(p_103738_ * 0.1F) * 0.7F;
        }

        //this.lid.setPos(0.0F, 16.0F + Mth.sin(f1) * 8.0F + f3, 0.0F);
        this.lid.setPos(0.0F, 26.0F + Mth.sin(f1) * 1.1F + f3, 0.0F);
        //if (p_103735_.getClientPeekAmount(f) > 0.3F) {
        //this.lid.yRot = f2 * f2 * f2 * f2 * (float) Math.PI * 0.125F; //rotación de la cabeza del hongo
        //} else {
        this.lid.yRot = 0.0F;
        // }

        this.head.setPos(0.0F, 12.0F, 0.0F);
        this.head.xRot = p_103740_ * ((float) Math.PI / 180F);
        this.head.yRot = (p_103735_.yHeadRot - 180.0F - p_103735_.yBodyRot) * ((float) Math.PI / 180F);
        this.lid.yRot = this.head.yRot;
    }

    public @NotNull Iterable<ModelPart> parts() {
        return ImmutableList.of(this.base, this.lid);
    }

    //public ModelPart getLid() {
    //return this.lid;
    //}

    public ModelPart getHead() {
        return this.head;
    }
}