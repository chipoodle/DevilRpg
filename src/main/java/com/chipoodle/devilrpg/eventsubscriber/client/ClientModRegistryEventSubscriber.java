/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.chipoodle.devilrpg.eventsubscriber.client;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.client.gui.hud.ManaBarHudOverlay;
import com.chipoodle.devilrpg.client.gui.hud.SkillsIconHudOverlay;
import com.chipoodle.devilrpg.client.gui.hud.MinionPortraitHudOverlay;
import com.chipoodle.devilrpg.client.gui.hud.StaminaBarHudOverlay;
import com.chipoodle.devilrpg.client.render.entity.model.*;
import com.chipoodle.devilrpg.client.render.entity.renderer.*;
import com.chipoodle.devilrpg.init.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Subscribe to events from the MOD EventBus that should be handled on the
 * PHYSICAL CLIENT side in this class
 */
@EventBusSubscriber(modid = DevilRpg.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModRegistryEventSubscriber {

    public static final int SOULVINE_COLOR = 0xAF3F1F;


    /*@SubscribeEvent
    public static void onRegisterBlockColors(final RegisterColorHandlersEvent.Block event) {
        DevilRpg.LOGGER.info("----------------------->ClientModEventSubscriber.onRegisterBlockColors");
        BlockColor iBlockColor = (state, reader, pos, tint) -> SOULVINE_COLOR;
        event.getBlockColors().register(iBlockColor, ModBlocks.SOUL_VINE_BLOCK.get());
    }

    @SubscribeEvent
    public static void onRegisterItemColors(final RegisterColorHandlersEvent.Item event) {
        DevilRpg.LOGGER.info("----------------------->ClientModEventSubscriber.onRegisterItemColors");
        ItemColor iItemColor = (itemStack, anInteger) -> SOULVINE_COLOR;
        event.getItemColors().register(iItemColor, ModBlocks.SOUL_VINE_BLOCK.get());
    }*/

    @SubscribeEvent
    public static void onRegisterLayers(final EntityRenderersEvent.RegisterLayerDefinitions event) {
        DevilRpg.LOGGER.info("----------------------->ClientModEventSubscriber.onRegisterLayers");
        event.registerLayerDefinition(SoulBearModel.LAYER_LOCATION, SoulBearModel::createBodyLayer);
        event.registerLayerDefinition(SoulWolfModel.LAYER_LOCATION, SoulWolfModel::createBodyLayer);
        event.registerLayerDefinition(SoulBearModelHeart.LAYER_LOCATION, SoulBearModelHeart::createBodyLayer);
        event.registerLayerDefinition(SoulWolfModelHeart.LAYER_LOCATION, SoulWolfModelHeart::createBodyLayer);
        event.registerLayerDefinition(SoulWispModel.DEFAULT_LAYER_LOCATION, SoulWispModel::createBodyLayer);
        event.registerLayerDefinition(SoulWispModel.BOMBER_LAYER_LOCATION, SoulWispModel::createBodyLayer);
        event.registerLayerDefinition(SoulWispModel.ARCHER_LAYER_LOCATION, SoulWispModel::createBodyLayer);
        //event.registerLayerDefinition(WerewolfHumanModel.WEREWOLF_LAYER_LOCATION, WerewolfHumanModel::createBodyLayer);
        event.registerLayerDefinition(WerewolfTransformedModel.WEREWOLF_LAYER_LOCATION, WerewolfTransformedModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(final EntityRenderersEvent.RegisterRenderers event) {
        DevilRpg.LOGGER.info("----------------------->ClientModEventSubscriber.onRegisterRenderers");

        event.registerEntityRenderer(ModEntities.SOUL_WOLF.get(), SoulWolfRenderer::new);
        event.registerEntityRenderer(ModEntities.SOUL_BEAR.get(), SoulBearRenderer::new);
        event.registerEntityRenderer(ModEntities.SOUL_ICEBALL.get(), FrostBallRenderer::new);
        event.registerEntityRenderer(ModEntities.WISP.get(), SoulWispRenderer::new);
        event.registerEntityRenderer(ModEntities.WISP_BOMB.get(), SoulWispBomberRenderer::new);
        event.registerEntityRenderer(ModEntities.WISP_ARCHER.get(), SoulWispArcherRenderer::new);
        //ItemBlockRenderTypes.setRenderLayer(ModBlocks.SOUL_VINE_BLOCK.get(), RenderType.translucent());
        //event.registerEntityRenderer(ModEntityTypes.WISP.get(), SoulWispHumanoidRenderer::new);

    }

    @SubscribeEvent
    public static void onRegisterGuiOverlaysEvent(final RegisterGuiOverlaysEvent event) {
        DevilRpg.LOGGER.info("----------------------->ClientModEventSubscriber.onRegisterGuiOverlaysEvent");

        event.registerAboveAll("mana", ManaBarHudOverlay.HUD_MANA_BAR);
        event.registerAboveAll("stamina", StaminaBarHudOverlay.HUD_STAMINA_BAR);
        event.registerAboveAll("minion_portrait", MinionPortraitHudOverlay.HUD_MINION_PORTRAITS);
        event.registerAboveAll("skill_icons", SkillsIconHudOverlay.HUD_SKILL_ICONS);

    }

}
