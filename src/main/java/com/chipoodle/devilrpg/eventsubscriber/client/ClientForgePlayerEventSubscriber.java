/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.chipoodle.devilrpg.eventsubscriber.client;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.skillsystem.skillinstance.SkillShapeshiftWerewolf;
import com.chipoodle.devilrpg.util.EventUtils;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.lwjgl.glfw.GLFW;

import java.util.function.BiConsumer;

/**
 * Subscribe to events from the NeoForge EventBus that should be handled on the
 * PHYSICAL CLIENT side in this class
 *
 * @author Chipoodle
 */
@EventBusSubscriber(modid = DevilRpg.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ClientForgePlayerEventSubscriber {

    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        BiConsumer<PlayerInteractEvent.LeftClickEmpty, PlayerAuxiliaryCapabilityInterface> c = (eve, aux) -> {
            eve.getEntity().swinging = false;
        };
        EventUtils.onWerewolfTransformation(event.getEntity(), c, event);
    }

    @SubscribeEvent
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        BiConsumer<PlayerInteractEvent.RightClickEmpty, PlayerAuxiliaryCapabilityInterface> c = (eve, aux) -> {
            eve.getEntity().swinging = false;
        };
        EventUtils.onWerewolfTransformation(event.getEntity(), c, event);
    }


    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent(priority = EventPriority.NORMAL, receiveCanceled = true)
    public static void onMouseRawEvent(InputEvent.MouseButton.Pre event) {

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player != null) {
            PlayerAuxiliaryCapabilityInterface aux = player.getData(PlayerAuxiliaryCapability.INSTANCE);
            if (aux == null || !aux.isWerewolfTransformation())
                return;

            if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                if (event.getAction() == GLFW.GLFW_PRESS) {
                    if (!aux.isWerewolfAttack()) {
                        aux.setWerewolfAttack(true, player);
                        event.setCanceled(true);
                    }
                }
            }

            if (event.getAction() == GLFW.GLFW_RELEASE) {
                if (aux.isWerewolfAttack()) {
                    aux.setWerewolfAttack(false, player);
                }
                event.setCanceled(false);
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent(priority = EventPriority.NORMAL, receiveCanceled = true)
    public static void onMouseRawEventPost(InputEvent.MouseButton.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player != null) {
            PlayerAuxiliaryCapabilityInterface aux = player.getData(PlayerAuxiliaryCapability.INSTANCE);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerTickAttack(PlayerTickEvent.Pre event) {
        if (event.getEntity().level().isClientSide) {
            PlayerSkillCapabilityInterface skillCapability = IGenericCapability.getUnwrappedPlayerCapability(event.getEntity(), PlayerSkillCapability.INSTANCE);
            PlayerAuxiliaryCapabilityInterface auxCapability = IGenericCapability.getUnwrappedPlayerCapability(event.getEntity(), PlayerAuxiliaryCapability.INSTANCE);
            boolean werewolfTransformation = auxCapability.isWerewolfTransformation();
            boolean werewolfAttack = auxCapability.isWerewolfAttack();
            if (werewolfTransformation && werewolfAttack) {
                int points = skillCapability.getSkillsPoints().get(SkillEnum.TRANSFORM_WEREWOLF);
                float t = (15L - points * 0.5F);
                long attackTime = (long) t;
                if (Math.floor(event.getEntity().tickCount % attackTime) == 0) {
                    SkillShapeshiftWerewolf skill = (SkillShapeshiftWerewolf) skillCapability.getLoadedSkillExecutor(SkillEnum.TRANSFORM_WEREWOLF);
                    skill.playerTickEventAttack(event.getEntity(), auxCapability);
                }
            }
        }
    }
}
