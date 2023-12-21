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
import com.chipoodle.devilrpg.client.render.entity.renderer.WerewolfCustomRendererBuilder;
import com.chipoodle.devilrpg.skillsystem.skillinstance.SkillShapeshiftWerewolf;
import com.chipoodle.devilrpg.util.EventUtils;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.gui.overlay.NamedGuiOverlay;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.BiConsumer;

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
    //private static final HealthBarRenderer healthBarRenderer = new HealthBarRenderer();
    //private static final ManaBarRenderer manaBarRenderer = new ManaBarRenderer();
    //private static final MinionPortraitRenderer minionPortraitRenderer = new MinionPortraitRenderer();

    private static final Class<?>[] tipos = {double.class, double.class, double.class};
    private static Method method = null;

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
            if (aux == null || !aux.isPresent() || !aux.map(x -> x.isWerewolfTransformation()).orElse(true)) return;

            player.swinging = false;
            if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                if (event.getAction() == GLFW.GLFW_PRESS) {
                    //DevilRpg.LOGGER.info("onMouseRawEvent GLFW_MOUSE_BUTTON_RIGHT pressed. setWerewolfAttack true");
                    aux.ifPresent(werwolf -> werwolf.setWerewolfAttack(true, player));
                }
            }

            if (event.getAction() == GLFW.GLFW_RELEASE) {
                //DevilRpg.LOGGER.info("onMouseRawEvent released. setWerewolfAttack false");
                aux.ifPresent(werwolf -> werwolf.setWerewolfAttack(false, player));
            }
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
                    SkillShapeshiftWerewolf skill = (SkillShapeshiftWerewolf) skillCapability.createSkillExecutor(SkillEnum.TRANSFORM_WEREWOLF);
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

    @SubscribeEvent
    public static void onRenderHandEvent(RenderHandEvent event) {

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
    public static void onCameraSetup(ViewportEvent.ComputeCameraAngles event) throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {

        Player player = Minecraft.getInstance().player;
        if (!Minecraft.getInstance().options.getCameraType().equals(CameraType.FIRST_PERSON)) {
            BiConsumer<ViewportEvent.ComputeCameraAngles, LazyOptional<PlayerAuxiliaryCapabilityInterface>> c = (eve, auxiliar) -> {
            };
            if (EventUtils.onWerewolfTransformation(player, c, event)) {
                if (method == null) {
                    method = event.getCamera().getClass().getDeclaredMethod("move", tipos);
                    method.setAccessible(true);
                }
                method.invoke(event.getCamera(), 0.5D, 1.1D, 0.0D);
            }
        }
    }

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
            WerewolfCustomRendererBuilder.createRenderer(eve);
            WerewolfCustomRendererBuilder.render(eve);

        };
        WerewolfCustomRendererBuilder.releaseRender(event, c);
    }

    @SubscribeEvent
    public static void onPlayerRender(RenderPlayerEvent.Post event) {
        BiConsumer<RenderPlayerEvent.Pre, LazyOptional<PlayerAuxiliaryCapabilityInterface>> c = (eve, auxiliar) -> {
            eve.setCanceled(true);
            LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer = eve.getRenderer();


        };
    }

}
