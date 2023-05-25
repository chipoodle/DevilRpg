package com.chipoodle.devilrpg.client.render.entity.layer;

import com.chipoodle.devilrpg.client.render.entity.model.WerewolfTransformedModel;
import com.chipoodle.devilrpg.client.render.entity.renderer.WerewolfRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class WerewolfArrowLayer<T extends LivingEntity, M extends WerewolfTransformedModel<T>> extends WerewolfStuckInBodyLayer<T, M> {
    private final EntityRenderDispatcher dispatcher;

    public WerewolfArrowLayer(EntityRendererProvider.Context context, WerewolfRenderer livingEntityRenderer) {
        super(livingEntityRenderer);
        this.dispatcher = context.getEntityRenderDispatcher();
    }

    protected int numStuck(T p_116567_) {
        return p_116567_.getArrowCount();
    }

    protected void renderStuckItem(PoseStack p_116569_, MultiBufferSource p_116570_, int p_116571_, Entity p_116572_, float p_116573_, float p_116574_, float p_116575_, float p_116576_) {
        float f = Mth.sqrt(p_116573_ * p_116573_ + p_116575_ * p_116575_);
        Arrow arrow = new Arrow(p_116572_.level, p_116572_.getX(), p_116572_.getY(), p_116572_.getZ());
        arrow.setYRot((float) (Math.atan2((double) p_116573_, (double) p_116575_) * (double) (180F / (float) Math.PI)));
        arrow.setXRot((float) (Math.atan2((double) p_116574_, (double) f) * (double) (180F / (float) Math.PI)));
        arrow.yRotO = arrow.getYRot();
        arrow.xRotO = arrow.getXRot();
        this.dispatcher.render(arrow, 0.0D, 0.0D, 0.0D, 0.0F, p_116576_, p_116569_, p_116570_, p_116571_);
    }
}