package com.chipoodle.devilrpg.client.render.entity.renderer;

import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.util.EventUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

public class WerewolfCustomRendererHelper {

    protected static final RandomSource random = RandomSource.create();
    @OnlyIn(Dist.CLIENT)
    public static WerewolfRenderer newWolf = null;
    private static EntityRenderDispatcher entityRenderDispatcher;
    private static Font font;
    private static EntityModelSet entityModelSet;
    private static ItemInHandRenderer itemInHandRenderer;
    private static ItemRenderer itemRenderer;
    private static BlockRenderDispatcher blockRenderDispatcher;

    public WerewolfCustomRendererHelper() {

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

    public static void releaseRender(RenderPlayerEvent.Pre event, BiConsumer<RenderPlayerEvent.Pre, PlayerAuxiliaryCapabilityInterface> c) {
        if (!EventUtils.onWerewolfTransformation(event.getEntity(), c, event) && newWolf != null) {
            newWolf = null;
            event.getEntity().refreshDimensions();
            // Restaurar el modelo del jugador (oculto durante la transformacion)
            setPlayerModelVisible(event.getRenderer(), true);
        }

    }

    private static List<RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>> hiddenHandLayers = null;

    /**
     * Oculta/restaura el modelo del jugador via reflection (RenderPlayerEvent.Pre ya no es
     * cancelable en 1.21.1, asi que para que no aparezca el modelo normal junto al lobo se
     * ponen invisibles las partes del modelo del jugador). Tambien oculta la capa de items
     * en mano (PlayerItemInHandLayer) para que el lobo no muestre el objeto (ataca con las
     * garras; los stats/durabilidad del item se usan igual en la logica de daño).
     */
    public static void setPlayerModelVisible(LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer, boolean visible) {
        try {
            PlayerModel<AbstractClientPlayer> model = renderer.getModel();
            for (Field f : model.getClass().getFields()) {
                if (ModelPart.class.isAssignableFrom(f.getType())) {
                    ModelPart part = (ModelPart) f.get(model);
                    if (part != null) {
                        Field vf = ModelPart.class.getDeclaredField("visible");
                        vf.setAccessible(true);
                        vf.setBoolean(part, visible);
                    }
                }
            }
            setPlayerItemLayersHidden(renderer, !visible);
        } catch (Exception e) {
            DevilRpg.LOGGER.error("setPlayerModelVisible fallo: {}", e.toString());
        }
    }

    private static void setPlayerItemLayersHidden(LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer, boolean hide) {
        try {
            Field layersField = LivingEntityRenderer.class.getDeclaredField("layers");
            layersField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>> layers =
                    (List<RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>>) layersField.get(renderer);
            if (hide) {
                if (hiddenHandLayers == null) {
                    hiddenHandLayers = new ArrayList<>();
                    Iterator<RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>> it = layers.iterator();
                    while (it.hasNext()) {
                        RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> layer = it.next();
                        // Oculta tambien la armadura (HumanoidArmorLayer) para que no se vea
                        // incrustada en el cuerpo del lobo; solo se muestran las capas no relacionadas.
                        if (layer instanceof ItemInHandLayer || layer instanceof HumanoidArmorLayer) {
                            hiddenHandLayers.add(layer);
                            it.remove();
                        }
                    }
                }
            } else if (hiddenHandLayers != null) {
                layers.addAll(hiddenHandLayers);
                hiddenHandLayers = null;
            }
        } catch (Exception e) {
            DevilRpg.LOGGER.error("setPlayerItemLayersHidden fallo: {}", e.toString());
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
