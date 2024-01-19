package com.chipoodle.devilrpg.client.render.entity.renderer;

import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.util.EventUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.util.LazyOptional;

import java.lang.reflect.Field;
import java.util.Objects;
import java.util.function.BiConsumer;

public class WerewolfCustomRendererBuilder {

    protected static final RandomSource random = RandomSource.create();
    @OnlyIn(Dist.CLIENT)
    public static WerewolfRenderer newWolf = null;
    private static EntityRenderDispatcher entityRenderDispatcher;
    private static Font font;
    private static EntityModelSet entityModelSet;
    private static ItemInHandRenderer itemInHandRenderer;
    private static ItemRenderer itemRenderer;
    private static BlockRenderDispatcher blockRenderDispatcher;

    public WerewolfCustomRendererBuilder() {

    }

    public static EntityRenderDispatcher init(LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer) {
        if (entityRenderDispatcher == null) {
            try {
                Field fontField = renderer.getClass().getSuperclass().getSuperclass().getDeclaredField("font");
                fontField.setAccessible(true);
                font = (Font) fontField.get(renderer);

                Field entityRenderDispatcherField = renderer.getClass().getSuperclass().getSuperclass().getDeclaredField("entityRenderDispatcher");
                entityRenderDispatcherField.setAccessible(true);
                entityRenderDispatcher = (EntityRenderDispatcher) entityRenderDispatcherField.get(renderer);

                Field entityModelsField = entityRenderDispatcher.getClass().getDeclaredField("entityModels");
                entityModelsField.setAccessible(true);
                entityModelSet = (EntityModelSet) entityModelsField.get(entityRenderDispatcher);

                Field itemInHandRendererField = entityRenderDispatcher.getClass().getDeclaredField("itemInHandRenderer");
                itemInHandRendererField.setAccessible(true);
                itemInHandRenderer = (ItemInHandRenderer) itemInHandRendererField.get(entityRenderDispatcher);

                Field itemRendererField = entityRenderDispatcher.getClass().getDeclaredField("itemRenderer");
                itemRendererField.setAccessible(true);
                itemRenderer = (ItemRenderer) itemRendererField.get(entityRenderDispatcher);

                Field blockRenderDispatcherField = entityRenderDispatcher.getClass().getDeclaredField("blockRenderDispatcher");
                blockRenderDispatcherField.setAccessible(true);
                blockRenderDispatcher = (BlockRenderDispatcher) blockRenderDispatcherField.get(entityRenderDispatcher);

                //itemInHandRenderer = new WerewolfItemInHandRenderer(Minecraft.getInstance(), entityRenderDispatcher,itemRenderer);

            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        return entityRenderDispatcher;
    }

    public static WerewolfRenderer createRenderer(Player player) {
        if (newWolf == null) {

            if(entityRenderDispatcher == null){
                hardInit(player);
            }

            EntityRendererProvider.Context cc = new EntityRendererProvider.Context(entityRenderDispatcher, itemRenderer, blockRenderDispatcher, itemInHandRenderer, null, entityModelSet, font);
            newWolf = new WerewolfRenderer(cc, false);
            player.refreshDimensions();
            /*
            DevilRpg.LOGGER.debug("Created layer: {}, client side: {}", newWolf, event.getEntity().level.isClientSide());*/
            //IRenderUtilities.rotationParticles(Minecraft.getInstance().level, random, eve.getEntity(), ParticleTypes.EFFECT, 17, 1);
        }
        return newWolf;
    }

    public static void releaseRender(RenderPlayerEvent.Pre event, BiConsumer<RenderPlayerEvent.Pre, LazyOptional<PlayerAuxiliaryCapabilityInterface>> c) {
        if (!EventUtils.onWerewolfTransformation(event.getEntity(), c, event) && newWolf != null) {
            newWolf = null;
            event.getEntity().refreshDimensions();
            //IRenderUtilities.rotationParticles(Minecraft.getInstance().level, random, event.getEntity(), ParticleTypes.EFFECT, 17, 1);
        }

    }

    public static void render(AbstractClientPlayer entity, float i,float partialTicks, PoseStack poseStack, MultiBufferSource multiBufferSource, int packedLight) {
        newWolf.render(entity, i,partialTicks, poseStack, multiBufferSource,packedLight);
    }

    private static void hardInit(Player player){
        Minecraft instance = Minecraft.getInstance();
        EntityRenderer<? super LocalPlayer> renderer = instance.getEntityRenderDispatcher().getRenderer(Objects.requireNonNull(player));
        init((LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>) renderer);
    }
}
