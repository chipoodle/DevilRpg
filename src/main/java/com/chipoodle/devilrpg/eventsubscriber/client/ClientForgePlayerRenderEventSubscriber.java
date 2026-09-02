/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.chipoodle.devilrpg.eventsubscriber.client;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.client.render.entity.renderer.WerewolfCustomRendererHelper;
import com.chipoodle.devilrpg.client.render.entity.renderer.WerewolfItemInHandRenderer;
import com.chipoodle.devilrpg.client.render.entity.renderer.WerewolfRenderer;
import com.chipoodle.devilrpg.util.EventUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.function.BiConsumer;

/**
 * Subscribe to events from the FORGE EventBus that should be handled on the
 * PHYSICAL CLIENT side in this class
 *
 * @author Chipoodle
 */
@EventBusSubscriber(modid = DevilRpg.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ClientForgePlayerRenderEventSubscriber {


    private static WerewolfItemInHandRenderer handRenderer;

    /**
     * renders custom 3d person view camera
     */
    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        if (!(event.getEntity() instanceof Player)) {
        }
    }

    @SubscribeEvent
    public static void onPlayerRender(RenderPlayerEvent.Pre event) {
        BiConsumer<RenderPlayerEvent.Pre, PlayerAuxiliaryCapabilityInterface> c = (eve, auxiliar) -> {
            // En 1.21.1 RenderPlayerEvent.Pre ya no es cancelable, asi que NO se dibuja aqui
            // (se dibujaria debajo del modelo normal del jugador). Solo se prepara el renderer
            // y se oculta el modelo del jugador (reflection) para que no aparezca junto al lobo.
            WerewolfCustomRendererHelper.init(eve.getRenderer());
            WerewolfCustomRendererHelper.createRenderer(eve.getEntity());
            WerewolfCustomRendererHelper.setPlayerModelVisible(eve.getRenderer(), false);
        };
        WerewolfCustomRendererHelper.releaseRender(event, c);
    }

    @SubscribeEvent
    public static void onPlayerRender(RenderPlayerEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && EventUtils.onWerewolfTransformation(player, (eve, auxiliar) -> {
            // Dibuja el hombre lobo ENCIMA del modelo del jugador (Post) para cubrirlo y
            // evitar el doble modelo (Pre ya no se puede cancelar en 1.21.1).
            if (WerewolfCustomRendererHelper.newWolf != null) {
                WerewolfCustomRendererHelper.render((AbstractClientPlayer) eve.getEntity(), 0,
                        eve.getPartialTick(), eve.getPoseStack(), eve.getMultiBufferSource(), eve.getPackedLight());
            }
        }, event)) {
        }
    }
    @SubscribeEvent
    public static void onRenderHandEvent(RenderHandEvent event) {
        BiConsumer<RenderHandEvent, PlayerAuxiliaryCapabilityInterface> c = (eve, auxiliar) -> {
            // RenderHandEvent SÍ es cancelable en NeoForge 1.21.1. Al transformarnos cancelamos
            // la mano vanilla para que solo se dibuje la del hombre lobo y no se superpongan.
            eve.setCanceled(true);
        };

        Minecraft instance = Minecraft.getInstance();
        LocalPlayer player = instance.player;
        if (EventUtils.onWerewolfTransformation(player, c, event)) {
            WerewolfRenderer renderer = WerewolfCustomRendererHelper.createRenderer(Minecraft.getInstance().player);
            if (handRenderer == null)
                handRenderer = new WerewolfItemInHandRenderer(instance, instance.getEntityRenderDispatcher(), instance.getItemRenderer(), renderer);
            if (event.getHand() == InteractionHand.MAIN_HAND)
                handRenderer.renderHandsWithItems(event.getPartialTick(), event.getPoseStack(), (MultiBufferSource.BufferSource) event.getMultiBufferSource(), player, event.getPackedLight());
        }

    }

}
