/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.chipoodle.devilrpg.eventsubscriber.client;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.client.render.entity.renderer.WerewolfCustomRendererBuilder;
import com.chipoodle.devilrpg.client.render.entity.renderer.WerewolfItemInHandRenderer;
import com.chipoodle.devilrpg.util.EventUtils;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.BiConsumer;

/**
 * Subscribe to events from the FORGE EventBus that should be handled on the
 * PHYSICAL CLIENT side in this class
 *
 * @author Chipoodle
 */
@EventBusSubscriber(modid = DevilRpg.MODID, bus = EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientForgeCameraEventSubscriber {
    private static final Class<?>[] types = {double.class, double.class, double.class};
    private static Method cameraMethod = null;

    /**
     * renders custom 3d person view camera
     */
    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        if (!(event.getEntity() instanceof Player)) return;
    }

    /**
     * @param event
     * @throws NoSuchMethodException
     * @throws SecurityException
     * @throws IllegalAccessException
     * @throws IllegalArgumentException
     * @throws InvocationTargetException
     */
    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {

        Player player = Minecraft.getInstance().player;
        if (!Minecraft.getInstance().options.getCameraType().equals(CameraType.FIRST_PERSON)) {
            BiConsumer<ViewportEvent.ComputeCameraAngles, LazyOptional<PlayerAuxiliaryCapabilityInterface>> c = (eve, auxiliar) -> {
            };
            if (EventUtils.onWerewolfTransformation(player, c, event)) {
                if (cameraMethod == null) {
                    cameraMethod = event.getCamera().getClass().getDeclaredMethod("move", types);
                    cameraMethod.setAccessible(true);
                }
                //event.getCamera().setAnglesInternal();
                cameraMethod.invoke(event.getCamera(), 0.5D, 1.1D, 0.0D);
            }
        }
    }


    @SubscribeEvent
    public static void onPlayerRender(RenderPlayerEvent.Pre event) {
        BiConsumer<RenderPlayerEvent.Pre, LazyOptional<PlayerAuxiliaryCapabilityInterface>> c = (eve, auxiliar) -> {
            eve.setCanceled(true);
            WerewolfCustomRendererBuilder.init(eve.getRenderer());
            WerewolfCustomRendererBuilder.createRenderer(eve);
            WerewolfCustomRendererBuilder.render(eve);
        };
        WerewolfCustomRendererBuilder.releaseRender(event, c);
    }

    @SubscribeEvent
    public static void onPlayerRender(RenderPlayerEvent.Post event) {
        BiConsumer<RenderPlayerEvent.Post, LazyOptional<PlayerAuxiliaryCapabilityInterface>> c = (eve, auxiliar) -> {
            eve.setCanceled(true);
            LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer = eve.getRenderer();


        };
    }

    @SubscribeEvent
    public static void onRenderHandEvent(RenderHandEvent event) {
        BiConsumer<RenderHandEvent, LazyOptional<PlayerAuxiliaryCapabilityInterface>> c = (eve, auxiliar) -> {
            eve.setCanceled(true);
        };

        Minecraft instance = Minecraft.getInstance();
        LocalPlayer player = instance.player;
        if (EventUtils.onWerewolfTransformation(player, c, event)) {

            //DevilRpg.LOGGER.debug("hand: {} itemStack {} ",event.getHand(),event.getItemStack());
            // Access player and hand:

            InteractionHand hand = event.getHand();

            // Render both hands regardless of item presence:
            if (hand == InteractionHand.MAIN_HAND || hand == InteractionHand.OFF_HAND) {
                // Render the hand using vanilla rendering:
                WerewolfItemInHandRenderer handRenderer = new WerewolfItemInHandRenderer(instance, instance.getEntityRenderDispatcher(), instance.getItemRenderer());
                handRenderer.renderHandsWithItems(event.getPartialTick(), event.getPoseStack(), (MultiBufferSource.BufferSource) event.getMultiBufferSource(), player, event.getPackedLight());

            }

        }


    }
}
