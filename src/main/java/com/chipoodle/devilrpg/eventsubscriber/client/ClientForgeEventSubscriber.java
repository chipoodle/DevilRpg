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
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.NamedGuiOverlay;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.lwjgl.glfw.GLFW;

import static net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.ARMOR_LEVEL;
import static net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.PLAYER_HEALTH;

/**
 * Subscribe to events from the FORGE EventBus that should be handled on the
 * PHYSICAL CLIENT side in this class
 *
 * @author Chipoodle
 */
@EventBusSubscriber(modid = DevilRpg.MODID, bus = EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientForgeEventSubscriber {

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onEvent(RenderGuiOverlayEvent.Pre event) {
        NamedGuiOverlay health = PLAYER_HEALTH.type();
        NamedGuiOverlay armor = ARMOR_LEVEL.type();

        /*if (event.getOverlay().equals(health)) {
            healthBarRenderer.renderBar(event.getPoseStack(), event.getWindow().getGuiScaledWidth(),
                    event.getWindow().getGuiScaledHeight());
            manaBarRenderer.renderBar(event.getPoseStack(), event.getWindow().getGuiScaledWidth(),
                    event.getWindow().getGuiScaledHeight());
            minionPortraitRenderer.renderPortraits(event.getPoseStack(), event.getWindow().getGuiScaledWidth(),
                    event.getWindow().getGuiScaledHeight());
            event.setCanceled(true);
        }

        if (event.getOverlay().equals(armor)) {
            event.setCanceled(true);
        }*/
    }


    /**
     * The RenderGameOverlayEvent.Post event is called after each game overlay
     * element is rendered. Similar to the RenderGameOverlayEvent.Pre event, it is
     * called multiple times.
     * <p>
     * If you want something to be rendered over an existing vanilla element, you
     * would render it here.
     */
    /*@SubscribeEvent(receiveCanceled = true)
    public static void onEvent(RenderGameOverlayEvent.Post event) {
        // If it's not one of the above cases, do nothing
        if (Objects.requireNonNull(event.getType()) == RenderGameOverlayEvent.ElementType.HEALTH) {
        }
    }*/

    /**
     * Non cancellable event
     *
     * @param event
     */
    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        // event.getPlayer().sendMessage(new StringTextComponent("------>
        // PlayerInteractEvent.LeftClickEmpty"));
		
		/*BiConsumer<PlayerInteractEvent.LeftClickEmpty, LazyOptional<IBaseAuxiliarCapability>> c = (eve, auxiliar) -> {
			eve.getPlayer().isSwingInProgress = false;
		};
		EventUtils.onTransformation(event.getPlayer(), c, event);*/

    }


    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent(priority = EventPriority.NORMAL, receiveCanceled = true)
    public static void onMouseRawEvent(InputEvent.MouseButton.Pre event) {

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player != null) {
            LazyOptional<PlayerAuxiliaryCapabilityInterface> aux = player.getCapability(PlayerAuxiliaryCapability.INSTANCE);
            if (!aux.isPresent() || !aux.map(PlayerAuxiliaryCapabilityInterface::isWerewolfTransformation).orElse(true))
                return;

            if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                if (event.getAction() == GLFW.GLFW_PRESS) {
                    //DevilRpg.LOGGER.info("onMouseRawEvent GLFW_MOUSE_BUTTON_RIGHT pressed. setWerewolfAttack true");
                    aux.ifPresent(werewolf -> werewolf.setWerewolfAttack(true, player));
                    event.setCanceled(true);
                }
            }

            if (event.getAction() == GLFW.GLFW_RELEASE) {
                //DevilRpg.LOGGER.info("onMouseRawEvent released. setWerewolfAttack false");
                aux.ifPresent(werewolf -> werewolf.setWerewolfAttack(false, player));
                event.setCanceled(false);
            }
            //player.swinging = false;
            //event.setCanceled(true);
        }
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent(priority = EventPriority.NORMAL, receiveCanceled = true)
    public static void onMouseRawEventPost(InputEvent.MouseButton.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player != null) {
            LazyOptional<PlayerAuxiliaryCapabilityInterface> aux = player.getCapability(PlayerAuxiliaryCapability.INSTANCE);
            if (!aux.isPresent() || !aux.map(PlayerAuxiliaryCapabilityInterface::isWerewolfTransformation).orElse(true))
                return;

        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerTickAttack(TickEvent.PlayerTickEvent event) {
        if (event.side.equals(LogicalSide.CLIENT) && event.phase == TickEvent.Phase.START) {
            PlayerSkillCapabilityInterface skillCapability = IGenericCapability.getUnwrappedPlayerCapability(event.player, PlayerSkillCapability.INSTANCE);
            PlayerAuxiliaryCapabilityInterface auxCapability = IGenericCapability.getUnwrappedPlayerCapability(event.player, PlayerAuxiliaryCapability.INSTANCE);
            boolean werewolfTransformation = auxCapability.isWerewolfTransformation();
            boolean werewolfAttack = auxCapability.isWerewolfAttack();
            if (werewolfTransformation && werewolfAttack) {
                //DevilRpg.LOGGER.info("clientForgeEvent.onPlayerTickAttack");
                //float s = 5L;
                //LivingEntity target = null;
                //DevilRpg.LOGGER.info("Skill.playerTickEventAttack.attackTime {} player.tickCount {}",attackTime,player.tickCount);
                int points = skillCapability.getSkillsPoints().get(SkillEnum.TRANSFORM_WEREWOLF);
                float t = (15L - points * 0.5F);
                long attackTime = (long) t;
                if (Math.floor(event.player.tickCount % attackTime) == 0) {
                    DevilRpg.LOGGER.info("---> Cadence: {}", attackTime);
                    SkillShapeshiftWerewolf skill = (SkillShapeshiftWerewolf) skillCapability.getLoadedSkillExecutor(SkillEnum.TRANSFORM_WEREWOLF);
                    skill.playerTickEventAttack(event.player, auxCapability);
                }
            }
        }
    }

    /*
     * @SubscribeEvent public static void onDrawHighlightEvent(DrawHighlightEvent
     * event) { ClientPlayerEntity player = Minecraft.getInstance().player;
     * //player.isSwingInProgress = false; }
     */

}
