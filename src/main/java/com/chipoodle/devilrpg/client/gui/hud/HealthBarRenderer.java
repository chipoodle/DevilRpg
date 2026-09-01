package com.chipoodle.devilrpg.client.gui.hud;

import com.chipoodle.devilrpg.DevilRpg;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.text.DecimalFormat;

/**
 * Replaces the vanilla heart and armor bars with a custom unified status bar.
 */
public class HealthBarRenderer {

	private final static ResourceLocation overlayBar = ResourceLocation.fromNamespaceAndPath(
			DevilRpg.MODID, "textures/gui/health_texture.png");

	private final static int BAR_WIDTH = 81;
	private final static int BAR_HEIGHT = 9;
	private final static int BAR_SPACING_ABOVE_EXP_BAR = 1;

	private final Minecraft mc;

	public HealthBarRenderer(Minecraft mc) {
		this.mc = mc;
	}

	public HealthBarRenderer() {
		mc = Minecraft.getInstance();
	}

	public void renderBar(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
		Level level = mc.level;
		Player player = mc.player;
		if (player == null)
			return;

		Font fr = mc.font;
		DecimalFormat d = new DecimalFormat("#,###");
		var poseStack = guiGraphics.pose();

		guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

		final int vanillaExpLeftX = screenWidth / 2 - 91; // leftmost edge of the experience bar
		final int vanillaExpTopY = screenHeight - 30 + BAR_SPACING_ABOVE_EXP_BAR; // top of the experience bar

		poseStack.pushPose();
		poseStack.translate(vanillaExpLeftX, vanillaExpTopY - BAR_SPACING_ABOVE_EXP_BAR - BAR_HEIGHT, 0);

		// background of the custom bar
		guiGraphics.blit(overlayBar, 0, 0, 0, 0, BAR_WIDTH, BAR_HEIGHT);
		// armor outline
		guiGraphics.blit(overlayBar, 0, 0, 0, BAR_HEIGHT, (int) (BAR_WIDTH * (player.getArmorValue() / 20f)), BAR_HEIGHT);

		poseStack.pushPose();
		poseStack.translate(1, 1, 0);

		float maxHp = player.getMaxHealth();
		float absorptionAmount = player.getAbsorptionAmount();
		float effectiveHp = player.getHealth() + absorptionAmount;

		poseStack.pushPose();
		poseStack.scale((BAR_WIDTH - 2) * Math.min(1, effectiveHp / maxHp), 1, 1);

		final int NORMAL_TEXTURE_U = BAR_WIDTH; // red texels
		final int REGEN_TEXTURE_U = BAR_WIDTH + 1; // green texels
		final int POISON_TEXTURE_U = BAR_WIDTH + 2; // black texels
		final int WITHER_TEXTURE_U = BAR_WIDTH + 3; // brown texels

		if (player.hasEffect(MobEffects.WITHER)) {
			guiGraphics.blit(overlayBar, 0, 0, WITHER_TEXTURE_U, 0, 1, BAR_HEIGHT - 2);
		} else if (player.hasEffect(MobEffects.POISON)) {
			guiGraphics.blit(overlayBar, 0, 0, POISON_TEXTURE_U, 0, 1, BAR_HEIGHT - 2);
		} else if (player.hasEffect(MobEffects.REGENERATION)) {
			guiGraphics.blit(overlayBar, 0, 0, REGEN_TEXTURE_U, 0, 1, BAR_HEIGHT - 2);
		} else {
			guiGraphics.blit(overlayBar, 0, 0, NORMAL_TEXTURE_U, 0, 1, BAR_HEIGHT - 2);
		}

		poseStack.popPose();
		poseStack.translate(BAR_WIDTH - 3, 1, 0);

		poseStack.pushPose();
		poseStack.scale(0.5f, 0.5f, 1);

		String s = d.format(effectiveHp) + "/" + d.format(maxHp);

		if (absorptionAmount > 0) {
			guiGraphics.drawString(fr, s, -fr.width(s) + 1, 2, 0x5A2B00);
			guiGraphics.drawString(fr, s, -fr.width(s), 1, 0xFFD200);
		} else {
			guiGraphics.drawString(fr, s, -fr.width(s) + 1, 2, 0x4D0000);
			guiGraphics.drawString(fr, s, -fr.width(s), 1, 0xFFFFFF);
		}
		poseStack.popPose();

		poseStack.popPose();
		poseStack.popPose();
	}
}
