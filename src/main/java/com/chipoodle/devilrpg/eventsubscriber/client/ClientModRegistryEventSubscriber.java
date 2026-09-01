/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.chipoodle.devilrpg.eventsubscriber.client;

import net.minecraft.resources.ResourceLocation;
import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.client.gui.hud.ManaBarHudOverlay;
import com.chipoodle.devilrpg.client.gui.hud.MinionPortraitHudOverlay;
import com.chipoodle.devilrpg.client.gui.hud.SkillsIconHudOverlay;
import com.chipoodle.devilrpg.client.gui.hud.StaminaBarHudOverlay;
import com.chipoodle.devilrpg.client.render.entity.model.*;
import com.chipoodle.devilrpg.client.render.entity.renderer.*;
import com.chipoodle.devilrpg.init.ModEntities;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

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
        event.registerLayerDefinition(SoulWispModel.HEALTH_LAYER_LOCATION, SoulWispModel::createBodyLayer);
        event.registerLayerDefinition(SoulWispModel.ARCHER_LAYER_LOCATION, SoulWispModel::createBodyLayer);
        event.registerLayerDefinition(SoulWispModel.CHOPPER_LAYER_LOCATION, SoulWispModel::createBodyLayer);
        event.registerLayerDefinition(SoulWispModel.FORESTER_LAYER_LOCATION, SoulWispModel::createBodyLayer);
        event.registerLayerDefinition(CubeModel.LAYER_LOCATION, CubeModel::createBodyLayer);
        event.registerLayerDefinition(WerewolfTransformedModel.WEREWOLF_LAYER_LOCATION, WerewolfTransformedModel::createBodyLayer);
        event.registerLayerDefinition(SunflowerShulkerModel.DEFAULT_LAYER_LOCATION, SunflowerShulkerModel::createBodyLayer);
        event.registerLayerDefinition(ExplodingSporeBulletModel.DEFAULT_LAYER_LOCATION, ExplodingSporeBulletModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(final EntityRenderersEvent.RegisterRenderers event) {
        DevilRpg.LOGGER.info("----------------------->ClientModEventSubscriber.onRegisterRenderers");

        event.registerEntityRenderer(ModEntities.SOUL_WOLF.get(), SoulWolfRenderer::new);
        event.registerEntityRenderer(ModEntities.SOUL_BEAR.get(), SoulBearRenderer::new);
        event.registerEntityRenderer(ModEntities.SOUL_FROSTBALL.get(), FrostBallRenderer::new);
        event.registerEntityRenderer(ModEntities.GENERIC_ITEM_PROJECTILE.get(), ExplodingSporeBulletRenderer::new);
        event.registerEntityRenderer(ModEntities.LICHEN_SEED_BALL.get(), LichenSeedBallRenderer::new);
        event.registerEntityRenderer(ModEntities.VINE_FLESH_BALL.get(), VineFleshBallRenderer::new);
        //event.registerEntityRenderer(ModEntities.WISP.get(), SoulWispRenderer::new);
        event.registerEntityRenderer(ModEntities.WISP_HEALTH.get(), SoulWispHealthRenderer::new);
        event.registerEntityRenderer(ModEntities.WISP_ARCHER.get(), SoulWispArcherRenderer::new);
        event.registerEntityRenderer(ModEntities.WISP_CHOPPER.get(), SoulWispChopperRenderer::new);
        event.registerEntityRenderer(ModEntities.WISP_FORESTER.get(), SoulWispForesterRenderer::new);
        event.registerEntityRenderer(ModEntities.SUNFLOWER_SHULKER.get(), SunflowerShulkerRenderer::new);
        event.registerEntityRenderer(ModEntities.EXPLODING_SPORE_BULLET.get(), ExplodingSporeBulletRenderer::new);
        event.registerEntityRenderer(ModEntities.AGGRESSIVE_ZOMBIE.get(), AggressiveZombieRenderer::new);
        //ItemBlockRenderTypes.setRenderLayer(ModBlocks.SOUL_VINE_BLOCK.get(), RenderType.translucent());
        //event.registerEntityRenderer(ModEntityTypes.WISP.get(), SoulWispHumanoidRenderer::new);

    }

    @SubscribeEvent
    public static void onRegisterGuiLayersEvent(final RegisterGuiLayersEvent event) {
        DevilRpg.LOGGER.info("----------------------->ClientModEventSubscriber.onRegisterGuiLayersEvent");

        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, "mana"), ManaBarHudOverlay.HUD_MANA_BAR);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, "stamina"), StaminaBarHudOverlay.HUD_STAMINA_BAR);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, "minion_portrait"), MinionPortraitHudOverlay.HUD_MINION_PORTRAITS);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, "skill_icons"), SkillsIconHudOverlay.HUD_SKILL_ICONS);

    }

}
