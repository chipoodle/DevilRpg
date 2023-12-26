package com.chipoodle.devilrpg.client.gui.hud;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.capability.stamina.PlayerStaminaCapability;
import com.chipoodle.devilrpg.capability.stamina.PlayerStaminaCapabilityInterface;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.common.util.LazyOptional;

import java.text.DecimalFormat;
import java.util.Objects;

public class StaminaBarHudOverlay {

    private final static ResourceLocation overlayBar = new ResourceLocation(DevilRpg.MODID + ":textures/gui/stamina_texture.png");

    /* These two variables describe the size of the bar */
    private final static int BAR_WIDTH = 81;
    private final static int BAR_HEIGHT = 9;
    private final static int BAR_SPACING_ABOVE_EXP_BAR = 1;
    public static final IGuiOverlay HUD_STAMINA_BAR = (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if(player.isCreative())
            return;


        PlayerAuxiliaryCapabilityInterface unwrappedPlayerCapability = IGenericCapability.getUnwrappedPlayerCapability(player, PlayerAuxiliaryCapability.INSTANCE);
        if(Objects.nonNull(unwrappedPlayerCapability) && !unwrappedPlayerCapability.isWerewolfTransformation()) {
            return;
        }

        LazyOptional<PlayerStaminaCapabilityInterface> playerCapability = mc.player.getCapability(PlayerStaminaCapability.INSTANCE);
        if (!playerCapability.isPresent()) return;

        float maxStamina = playerCapability.map(PlayerStaminaCapabilityInterface::getMaxStamina).orElse(0.0f);
        float staminaRun = playerCapability.map(PlayerStaminaCapabilityInterface::getStamina).orElse(0.0f);
        Font fr = mc.font;
        DecimalFormat d = new DecimalFormat("#,###.##");
        poseStack.pushPose();
        {
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.setShaderTexture(0, overlayBar);
            final int vanillaExpLeftX = screenWidth / 2 + 91; // leftmost edge of the experience bar
            final int vanillaExpTopY = screenHeight + BAR_SPACING_ABOVE_EXP_BAR - BAR_HEIGHT ; // top of the experience bar
            poseStack.translate(screenWidth-BAR_WIDTH, vanillaExpTopY - BAR_SPACING_ABOVE_EXP_BAR - BAR_HEIGHT - 5, 0);
            //Draw only the black bar
            gui.blit(poseStack, 0, 0, 0, 0, BAR_WIDTH, BAR_HEIGHT);
            poseStack.pushPose();
            {
                //move inside the black bar to draw
                poseStack.translate(1, 1, 0);
                poseStack.pushPose();
                {
                    poseStack.scale((BAR_WIDTH - 2) * Math.min(1, staminaRun / maxStamina), 1, 1);
                    gui.blit(poseStack, 0, 0, BAR_WIDTH, 0, 1, BAR_HEIGHT - 2);
                }
                poseStack.popPose();
                /* Move to the right end of the bar, minus a few pixels to draw the text */
                poseStack.translate(BAR_WIDTH - 3, 1, 0);
                /* The default minecraft font is too big, so I scale it down a bit. */

                poseStack.pushPose();
                {
                    poseStack.scale(0.5f, 0.5f, 1);
                    /* This generates the string that I want to draw. */
                    String s = d.format(staminaRun) + "/" + d.format(maxStamina);
                    fr.draw(poseStack, s, -fr.width(s) + 1, 2, 0x4D0000);
                    fr.draw(poseStack, s, -fr.width(s), 1, 0xFFFFFF);
                }
                poseStack.popPose();
            }
            poseStack.popPose();
        }
        poseStack.popPose();
    };
}