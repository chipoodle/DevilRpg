package com.chipoodle.devilrpg.client.gui.hud;

import java.text.DecimalFormat;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.lwjgl.opengl.GL11;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapabilityInterface;
import com.chipoodle.devilrpg.capability.player_minion.PlayerMinionCapabilityAttacher;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityAttacher;
import com.chipoodle.devilrpg.entity.SoulBearEntity;
import com.chipoodle.devilrpg.entity.SoulWispEntity;
import com.chipoodle.devilrpg.entity.SoulWolfEntity;
import com.mojang.blaze3d.matrix.MatrixStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.potion.Effects;
import net.minecraft.util.ResourceLocation;

public class MinionPortraitRenderer extends AbstractGui {

	private final static ResourceLocation soulwolfPortrait = new ResourceLocation(
			DevilRpg.MODID + ":textures/entity/soulwolf/soulwolf_portrait_256x256.png");
	private final static ResourceLocation soulbearPortrait = new ResourceLocation(
			DevilRpg.MODID + ":textures/entity/soulbear/soulbear_portrait_256x256.png");
	private final static ResourceLocation wispPortrait = new ResourceLocation(
			DevilRpg.MODID + ":textures/entity/flyingwisp/wisp_portrait_a_256x256.png");

	private final static ResourceLocation bars = new ResourceLocation(
			DevilRpg.MODID + ":textures/gui/minionbars_portrait_256x256.png");

	/* These two variables describe the size of the bar */
	private final static int BAR_WIDTH = 81;
	private final static int BAR_HEIGHT = 81;
	// we will draw the status bar just above the hotbar. obtained by inspecting the
	// vanilla hotbar rendering code
	final int vanillaExpLeftX = 1; // leftmost edge of the experience bar
	final int vanillaExpTopY = 1; // top of the experience bar
	private final Minecraft mc;

	public MinionPortraitRenderer(Minecraft mc) {
		super();
		this.mc = mc;
	}

	public MinionPortraitRenderer() {
		super();
		mc = Minecraft.getInstance();
	}

	public void renderPortraits(MatrixStack matrixStack, int screenWidth, int screenHeight) {
		PlayerEntity player = mc.player;

		if (player == null)
			return;

		PlayerSkillCapabilityInterface skillCap = IGenericCapability.getUnwrappedPlayerCapability(player,
				PlayerSkillCapabilityAttacher.SKILL_CAP);
		PlayerMinionCapabilityInterface minionCap = IGenericCapability.getUnwrappedPlayerCapability(player,
				PlayerMinionCapabilityAttacher.MINION_CAP);
		// LazyOptional<IBaseSkillCapability> skillCap =
		// player.getCapability(PlayerSkillCapabilityProvider.SKILL_CAP);
		// LazyOptional<IBaseMinionCapability> minionCap =
		// player.getCapability(PlayerMinionCapabilityProvider.MINION_CAP);

		if (skillCap == null || minionCap == null)
			return;

		ConcurrentLinkedQueue<UUID> soulwolfMinionKeys = minionCap.getSoulWolfMinions();
		ConcurrentLinkedQueue<UUID> soulbearMinionKeys = minionCap.getSoulBearMinions();
		ConcurrentLinkedQueue<UUID> wispMinionKeys = minionCap.getWispMinions();

		int i = 0;
		for (UUID wolfKey : soulwolfMinionKeys) {
			SoulWolfEntity h = (SoulWolfEntity) minionCap.getTameableByUUID(wolfKey, player.level);
			if (h!= null && h.getOwner() != null) {
				renderEntityPortrait(matrixStack, i++, h.getHealth(), h.getMaxHealth(),
						soulwolfPortrait, h);
			}

		}

		for (UUID bearKey : soulbearMinionKeys) {
			SoulBearEntity h = (SoulBearEntity) minionCap.getTameableByUUID(bearKey, player.level);
			if (h != null && h.getOwner() != null) {
				renderEntityPortrait(matrixStack, i++, h.getHealth(), h.getMaxHealth(),
						soulbearPortrait, h);
			}

		}

		for (UUID wispKey : wispMinionKeys) {
			SoulWispEntity h = (SoulWispEntity) minionCap.getTameableByUUID(wispKey, player.level);
			if (h != null && h.getOwner() != null) {
				renderEntityPortrait(matrixStack, i++, h.getHealth(), h.getMaxHealth(),
						wispPortrait, h);
			}

		}
	}

	private void renderEntityPortrait(MatrixStack matrixStack, int i, float health, float maxHealth,
			ResourceLocation overlayBar, LivingEntity entity) {

		/* This object draws text using the Minecraft font */
		FontRenderer fr = mc.font;

		/* This object inserts commas into number strings */
		DecimalFormat d = new DecimalFormat("#,###");

		/* Saving the current state of OpenGL so that I can restore it when I'm done */
		GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
		GL11.glPushMatrix();

		/*
		 * I like to indent the code whenever I push. It helps me visualize what is
		 * happening better. This is a personal preference though.
		 */

		/* Set the rendering color to white */
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

		/* This method tells OpenGL to draw with the custom texture */
		mc.getTextureManager().bind(overlayBar);
		

		/*
		 * Shift our rendering origin to just above the experience bar The top left
		 * corner of the screen is x=0, y=0
		 */
		/* establece la posición inicial del componente (imagen con barra incluida) */
		GL11.glTranslatef(vanillaExpLeftX + 20 * i, vanillaExpTopY, 0);

		/* Escala toda la imagen con todo y barra */
		GL11.glScalef(0.2f, 0.2f, 0.2f);
		/*
		 * Draw a part of the image file at the current position
		 *
		 * The first two arguments are the x,y position that you want to draw the
		 * texture at (with respect to the current transformations).
		 *
		 * The next four arguments specify what part of the image file to draw, in the
		 * order below:
		 *
		 * 1. Left-most side 2. Top-most side 3. Right-most side 4. Bottom-most side
		 *
		 * The units of these four arguments are pixels in the image file. These
		 * arguments will form a rectangle, which is then "cut" from your image and used
		 * as the texture
		 *
		 * This line draws the background of the custom bar
		 */
		
		blit(matrixStack, 0, 0, 0, 0, BAR_WIDTH, BAR_HEIGHT);
		GL11.glPushMatrix();
		/* This method tells OpenGL to draw with the custom texture */
		mc.getTextureManager().bind(bars);

		
		GL11.glPushMatrix();
		GL11.glScalef(1.0f, 2.0f, 1.0f);
		GL11.glTranslatef(0,-40, 0);
		//Barra negra de fondo
		blit(matrixStack, 0, BAR_HEIGHT, 0, BAR_HEIGHT,  (BAR_WIDTH + 20), 9);
		/*
		 * This line draws the outline effect that corresponds to how much armor the
		 * player has. I slide the right-most side of the rectangle using the minion's
		 * armor value.
		 */
		GL11.glScalef(1.04f, 1.2f, 1.0f);
		GL11.glTranslatef(-1.2f,-12.0f, 0);
		blit(matrixStack, 0, BAR_HEIGHT-2, 0, BAR_HEIGHT+9, (int) (BAR_WIDTH * (entity.getArmorValue() / 20f)), 9);
		//blit(matrixStack, 0, BAR_HEIGHT-2, 0, BAR_HEIGHT+9, (int) (BAR_WIDTH+20), 9);
		GL11.glPopMatrix();
		
		
		/* This part draws the inside of the bar, which starts 1 pixel right and down */
		GL11.glPushMatrix();
		/* Shift the bar BAR_HEIGHT + 2 pixel down */
		GL11.glTranslatef(0, BAR_HEIGHT + 2, 0);

		GL11.glScalef(1.0f, 2.0f, 1.0f);

		/* Shift 1 pixel right and down */
		GL11.glTranslatef(1, 1, 0);

		/*
		 * These few numbers will store the HP values of the player. This includes the
		 * Health Boost and Absorption potion effects
		 */
		float maxHp = entity.getMaxHealth();
		float absorptionAmount = entity.getAbsorptionAmount();
		float effectiveHp = entity.getHealth()+absorptionAmount;

		/*
		 * The part of the bar that fills up will be a rectangle that stretches based on
		 * how much hp the player has. To do this, I need to use a scaling transform,
		 * which is why I push again.
		 */
		GL11.glPushMatrix();

		/*
		 * I scale horizontally based on the fraction effectiveHp/maxHp. However, I
		 * don't want the scaled value to exceed the actual width of the bar's interior,
		 * so I cap it at 1 using Math.min.
		 *
		 * The width of the bar's interior is BAR_WIDTH - 2
		 */
		GL11.glScalef((BAR_WIDTH - 2) * Math.min(1, effectiveHp / maxHp), 1, 1);

		/*
		 * This chain of if-else block checks if the player has any status effects. I
		 * check if a potion effect is active, then draw the filling bar based on what
		 * potion effect is active. Unfortunately, it is not possible to use a switch
		 * statement here since multiple potion effects may be active at once.
		 *
		 * The effects I check for are 20: Wither 19: Poison 10: Regeneration
		 *
		 * For a more comprehensive list of status effects, see
		 * http:minecraft.gamepedia.com/Status_effect
		 */

		final int WITHER_EFFECT_ID = 20; // is now MobEffects.WITHER
		final int POISON_EFFECT_ID = 19; // is now MobEffects.POISON
		final int REGEN_EFFECT_ID = 10; // is now MobEffects.REGENERATION
		final int NORMAL_TEXTURE_U = BAR_WIDTH; // red texels - see mbe40_hud_overlay.png
		final int REGEN_TEXTURE_U = BAR_WIDTH + 1; // green texels
		final int POISON_TEXTURE_U = BAR_WIDTH + 2; // black texels
		final int WITHER_TEXTURE_U = BAR_WIDTH + 3; // brown texels
		final int ABSORPION_TEXTURE_U = BAR_WIDTH + 3; // brown texels

		if (entity.hasEffect(Effects.ABSORPTION)) {
			blit(matrixStack, 0, 0, ABSORPION_TEXTURE_U, 0, 1, BAR_HEIGHT - 2);
			//entity.setAbsorptionAmount(entity.getEffect(Effects.ABSORPTION).getAmplifier());
		} else if (entity.hasEffect(Effects.WITHER)) {
			blit(matrixStack, 0, 0, WITHER_TEXTURE_U, 0, 1, BAR_HEIGHT - 2);
		} else if (entity.hasEffect(Effects.POISON)) {
			blit(matrixStack, 0, 0, POISON_TEXTURE_U, 0, 1, BAR_HEIGHT - 2);
		} else if (entity.hasEffect(Effects.REGENERATION)) {
			blit(matrixStack, 0, 0, REGEN_TEXTURE_U, 0, 1, BAR_HEIGHT - 2);
		} else {
			blit(matrixStack, 0, 0, NORMAL_TEXTURE_U, 0, 1, BAR_HEIGHT - 2);
		}
		GL11.glPopMatrix();


		
		
		/* This generates the string that I want to draw. */
		String s = d.format(effectiveHp) + "/" + d.format(maxHp);
		int textWidth = fr.width(s);
		
		/* Move to the right end of the bar, minus a few pixels. */
		GL11.glTranslatef(3+textWidth+textWidth/2, -0.5f, 0);

		/* The default minecraft font is too big, so I scale it down a bit. */
		GL11.glPushMatrix();
		GL11.glScalef(1.6f, 0.8f, 1);


		/*
		 * If the player has the absorption effect, draw the string in gold color,
		 * otherwise draw the string in white color. For each case, I call drawString
		 * twice, once to draw the shadow, and once for the actual string.
		 */
		if (entity.hasEffect(Effects.ABSORPTION)) {

			/* Draw the shadow string */
			fr.draw(matrixStack, s, -fr.width(s) + 1, 2, 0x5A2B00);

			/* Draw the actual string */
			fr.draw(matrixStack, s, -fr.width(s), 1, 0xFFD200);
		} else {
			fr.draw(matrixStack, s, -fr.width(s) + 1, 2, 0x4D0000);
			fr.draw(matrixStack, s, -fr.width(s), 1, 0xFFFFFF);
		}
		GL11.glPopMatrix();

		GL11.glPopMatrix();

		GL11.glPopMatrix();
		GL11.glPopMatrix();
		GL11.glPopAttrib();
	}
}