package com.chipoodle.devilrpg.client.render.entity.renderer;

import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.client.render.IRenderUtilities;
import com.chipoodle.devilrpg.util.EventUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.util.LazyOptional;

import java.lang.reflect.Field;
import java.util.Random;
import java.util.function.BiConsumer;

public class WerewolfCustomRendererBuilder {

    @OnlyIn(Dist.CLIENT)
    public static WerewolfRenderer newWolf = null;
    private static EntityRenderDispatcher entityRenderDispatcher;
    private static Font font;
    private static EntityModelSet entityModelSet;
    private static ItemInHandRenderer itemInHandRenderer;
    private static ItemRenderer itemRenderer;
    private static BlockRenderDispatcher blockRenderDispatcher;

    protected static final RandomSource random = RandomSource.create();

    public WerewolfCustomRendererBuilder() {

    }

    public static void init(LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer) {
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

            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void createRenderer(RenderPlayerEvent.Pre eve) {
        if (newWolf == null) {
            EntityRendererProvider.Context cc = new EntityRendererProvider.Context(entityRenderDispatcher, itemRenderer, blockRenderDispatcher, itemInHandRenderer, null, entityModelSet, font);
            newWolf = new WerewolfRenderer(cc, false);
            eve.getEntity().refreshDimensions();
            /*
            DevilRpg.LOGGER.debug("Created layer: {}, client side: {}", newWolf, event.getEntity().level.isClientSide());*/
            IRenderUtilities.rotationParticles(Minecraft.getInstance().level, random, eve.getEntity(),
                    ParticleTypes.EFFECT, 17, 1);
        }
    }

    public static void render(RenderPlayerEvent.Pre eve) {
        newWolf.render((AbstractClientPlayer) eve.getEntity(), 0, eve.getPartialTick(), eve.getPoseStack(), eve.getMultiBufferSource(), eve.getPackedLight());
    }

    public static void releaseRender(RenderPlayerEvent.Pre event, BiConsumer<RenderPlayerEvent.Pre, LazyOptional<PlayerAuxiliaryCapabilityInterface>> c) {
        if (!EventUtils.onWerewolfTransformation(event.getEntity(), c, event) && newWolf != null) {
            newWolf = null;
            event.getEntity().refreshDimensions();
            IRenderUtilities.rotationParticles(Minecraft.getInstance().level, random, event.getEntity(),
                    ParticleTypes.EFFECT, 17, 1);
        }

    }

}
