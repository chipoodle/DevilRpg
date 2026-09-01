package com.chipoodle.devilrpg.client.gui.hud;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.capability.stamina.PlayerStaminaCapability;
import com.chipoodle.devilrpg.capability.stamina.PlayerStaminaCapabilityInterface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.text.DecimalFormat;
import java.util.Objects;

public class StaminaBarHudOverlay {
    private final static ResourceLocation overlayBar = ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, "textures/gui/stamina_texture.png");

    /* These two variables describe the size of the bar */
    private final static int BAR_WIDTH = 81;
    private final static int BAR_HEIGHT = 9;
    private final static int BAR_SPACING_ABOVE_EXP_BAR = 1;
    private static final int RIGHT_OFFSET = 15;
    public static final LayeredDraw.Layer HUD_STAMINA_BAR = (guiGraphics, deltaTracker) -> {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || player.isCreative())
            return;

        PlayerAuxiliaryCapabilityInterface unwrappedPlayerCapability = IGenericCapability.getUnwrappedPlayerCapability(player, PlayerAuxiliaryCapability.INSTANCE);
        if (Objects.nonNull(unwrappedPlayerCapability) && !unwrappedPlayerCapability.isWerewolfTransformation()) {
            return;
        }

        PlayerStaminaCapabilityInterface playerCapability = player.getData(PlayerStaminaCapability.INSTANCE);
        if (playerCapability == null) return;

        float maxStamina = playerCapability.getMaxStamina();
        float staminaRun = playerCapability.getStamina();
        Font fr = mc.font;
        DecimalFormat d = new DecimalFormat("#,###.#");
        var poseStack = guiGraphics.pose();
        int screenWidth = guiGraphics.guiWidth();
        int screenHeight = guiGraphics.guiHeight();
        poseStack.pushPose();
        {
            guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            final int vanillaExpTopY = screenHeight + BAR_SPACING_ABOVE_EXP_BAR - BAR_HEIGHT; // top of the experience bar
            poseStack.translate(screenWidth - BAR_WIDTH - RIGHT_OFFSET, vanillaExpTopY - BAR_SPACING_ABOVE_EXP_BAR - BAR_HEIGHT - 5, 0);

            //Draw only the black bar
            guiGraphics.blit(overlayBar, 0, 0, 0, 0, BAR_WIDTH, BAR_HEIGHT);
            poseStack.pushPose();
            {
                //move inside the black bar to draw
                poseStack.translate(1, 1, 0);
                poseStack.pushPose();
                {
                    poseStack.scale((BAR_WIDTH - 2) * Math.min(1, staminaRun / maxStamina), 1, 1);
                    guiGraphics.blit(overlayBar, 0, 0, BAR_WIDTH, 0, 1, BAR_HEIGHT - 2);
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
                    guiGraphics.drawString(fr, s, -fr.width(s) + 1, 2, 0x4D0000);
                    guiGraphics.drawString(fr, s, -fr.width(s), 1, 0xFFFFFF);
                }
                poseStack.popPose();
            }
            poseStack.popPose();
        }
        poseStack.popPose();
    };
}
