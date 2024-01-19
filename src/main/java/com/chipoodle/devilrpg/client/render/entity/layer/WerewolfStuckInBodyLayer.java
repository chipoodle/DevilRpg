package com.chipoodle.devilrpg.client.render.entity.layer;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.client.render.entity.model.WerewolfTransformedModel;
import com.chipoodle.devilrpg.client.render.entity.renderer.WerewolfRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public abstract class WerewolfStuckInBodyLayer<T extends LivingEntity, M extends WerewolfTransformedModel<T>> extends RenderLayer<T, M> {
    public WerewolfStuckInBodyLayer(LivingEntityRenderer<T, M> p_117564_) {
        super(p_117564_);
    }

    public WerewolfStuckInBodyLayer(WerewolfRenderer livingEntityRenderer) {
        super((RenderLayerParent<T, M>) livingEntityRenderer);
    }

    protected abstract int numStuck(T p_117565_);

    protected abstract void renderStuckItem(PoseStack p_117566_, MultiBufferSource p_117567_, int p_117568_, Entity p_117569_, float p_117570_, float p_117571_, float p_117572_, float p_117573_);

    public void render(@NotNull PoseStack p_117586_, @NotNull MultiBufferSource p_117587_, int p_117588_, @NotNull T entity, float p_117590_, float p_117591_, float p_117592_, float p_117593_, float p_117594_, float p_117595_) {
        int i = this.numStuck(entity);
        RandomSource randomsource = RandomSource.create(entity.getId());
        if (i > 0) {
            for (int j = 0; j < i; ++j) {
                p_117586_.pushPose();
                //DevilRpg.LOGGER.debug("--------------- i {}, j {}", i, j);
                ModelPart modelpart = this.getParentModel().getRandomModelPart(randomsource);
                ModelPart.Cube modelpart$cube = null;
                while (modelpart$cube == null) {
                    ModelPart randomModelPart = null;
                    try {
                        List<ModelPart> modelPartList = modelpart.getAllParts().filter(x -> !x.isEmpty()).toList();
                        if (!modelPartList.isEmpty()) {
                            randomModelPart = modelPartList.get(randomsource.nextInt(modelPartList.size()));
                            //DevilRpg.LOGGER.debug("--------------- randomModelPart.isEmpty(): {}", randomModelPart.isEmpty());
                            if (!randomModelPart.isEmpty()) {
                                modelpart$cube = randomModelPart.getRandomCube(randomsource);
                                //DevilRpg.LOGGER.debug("--------------- modelpart$cube{}", modelpart$cube);
                            }else {
                                modelpart = this.getParentModel().getRandomModelPart(randomsource);
                                randomsource = RandomSource.create(System.currentTimeMillis());
                            }
                        }
                    } catch (IllegalArgumentException e) {
                        DevilRpg.LOGGER.error("---------------Illegal cube in ModelPart:  {}", randomModelPart, e);
                    }
                }

                modelpart.translateAndRotate(p_117586_);
                float f = randomsource.nextFloat();
                float f1 = randomsource.nextFloat();
                float f2 = randomsource.nextFloat();
                //float f3 = Mth.lerp(f, modelpart$cube.minX, modelpart$cube.maxX) / 16.0F;
                float f3 = Mth.lerp(f, Math.min(modelpart$cube.minX, modelpart$cube.maxX), Math.max(modelpart$cube.minX, modelpart$cube.maxX)) / 16.0F;
                float f4 = Mth.lerp(f1, modelpart$cube.minY, modelpart$cube.maxY) / 16.0F;
                float f5 = Mth.lerp(f2, modelpart$cube.minZ, modelpart$cube.maxZ) / 16.0F;
                p_117586_.translate(f3, f4, f5);
                f = -1.0F * (f * 2.0F - 1.0F);
                f1 = -1.0F * (f1 * 2.0F - 1.0F);
                f2 = -1.0F * (f2 * 2.0F - 1.0F);
                this.renderStuckItem(p_117586_, p_117587_, p_117588_, entity, f, f1, f2, p_117592_);
                p_117586_.popPose();
            }

        }
    }
}