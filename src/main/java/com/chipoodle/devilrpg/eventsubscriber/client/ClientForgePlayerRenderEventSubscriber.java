/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.chipoodle.devilrpg.eventsubscriber.client;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.client.render.entity.renderer.WerewolfItemInHandRenderer;
import com.chipoodle.devilrpg.client.render.entity.renderer.WerewolfCustomRendererBuilder;
import com.chipoodle.devilrpg.client.render.entity.renderer.WerewolfRenderer;
import com.chipoodle.devilrpg.util.EventUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import java.util.function.BiConsumer;

/**
 * Subscribe to events from the FORGE EventBus that should be handled on the
 * PHYSICAL CLIENT side in this class
 *
 * @author Chipoodle
 */
@EventBusSubscriber(modid = DevilRpg.MODID, bus = EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientForgePlayerRenderEventSubscriber {
    /**
     * renders custom 3d person view camera
     */
    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        if (!(event.getEntity() instanceof Player)) return;
    }

    @SubscribeEvent
    public static void onPlayerRender(RenderPlayerEvent.Pre event) {
        BiConsumer<RenderPlayerEvent.Pre, LazyOptional<PlayerAuxiliaryCapabilityInterface>> c = (eve, auxiliar) -> {
            eve.setCanceled(true);
            WerewolfCustomRendererBuilder.init(eve.getRenderer());
            WerewolfCustomRendererBuilder.createRenderer(eve.getEntity());
            WerewolfCustomRendererBuilder.render((AbstractClientPlayer) eve.getEntity(), 0, eve.getPartialTick(), eve.getPoseStack(), eve.getMultiBufferSource(), eve.getPackedLight());
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
            WerewolfRenderer renderer = WerewolfCustomRendererBuilder.createRenderer(Minecraft.getInstance().player);
            WerewolfItemInHandRenderer handRenderer =
                    new WerewolfItemInHandRenderer(instance, instance.getEntityRenderDispatcher(), instance.getItemRenderer(), renderer);
                handRenderer.renderHandsWithItems(event.getPartialTick(), event.getPoseStack(), (MultiBufferSource.BufferSource) event.getMultiBufferSource(), player, event.getPackedLight());
        }

    }

    @SubscribeEvent
    public static void renderInjectedArm(RenderArmEvent event) {
        BiConsumer<RenderArmEvent, LazyOptional<PlayerAuxiliaryCapabilityInterface>> c = (eve, auxiliar) -> {
            eve.setCanceled(true);
        };
        Minecraft instance = Minecraft.getInstance();
        LocalPlayer player = instance.player;
        if (EventUtils.onWerewolfTransformation(player, c, event)) {

        }

    }
}
