package com.chipoodle.devilrpg.client.gui.hud;

import java.text.DecimalFormat;

import org.lwjgl.opengl.GL11;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.mana.IBaseManaCapability;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapabilityProvider;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.LazyOptional;

public class MinionPortraitRenderer extends AbstractGui {

	private final static ResourceLocation overlayBar = new ResourceLocation(
			DevilRpg.MODID + ":textures/gui/mana_texture.png");

	/* These two variables describe the size of the bar */
	private final static int BAR_WIDTH = 81;
	private final static int BAR_HEIGHT = 9;
	private final static int BAR_SPACING_ABOVE_EXP_BAR = 3;
	//LazyOptional<IBaseManaCapability> playerCapability;
	private float manaRun;
	private float maxMana;

	private Minecraft mc;

	public MinionPortraitRenderer(Minecraft mc) {
		super();
		this.mc = mc;
	}

	public MinionPortraitRenderer() {
		super();
		mc = Minecraft.getInstance();
	}

	public void renderStatusBar(int screenWidth, int screenHeight) {
		/* These are the variables that contain world and player information */
		//World world = mc.world;
		PlayerEntity player = mc.player;
		LazyOptional<IBaseManaCapability> playerCapability = mc.player.getCapability(PlayerManaCapabilityProvider.MANA_CAP);
		/*if (playerCapability == null)
			playerCapability = mc.player.getCapability(PlayerManaCapabilityProvider.MANA_CAP);*/

		if (!playerCapability.isPresent())
			return;

		maxMana = playerCapability.map(x -> x.getMaxMana()).orElse(0.0f);
		manaRun = playerCapability.map(x -> x.getMana()).orElse(0.0f);
		if (manaRun < maxMana) {
			manaRun += 0.10;
			playerCapability.ifPresent(x -> x.setMana(manaRun > maxMana ? maxMana : manaRun,player));
		}else {
			manaRun = maxMana;
		} 

		/* This object draws text using the Minecraft font */
		FontRenderer fr = mc.fontRenderer;

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
		mc.getTextureManager().bindTexture(overlayBar);

		// we will draw the status bar just above the hotbar. obtained by inspecting the
		// vanilla hotbar rendering code
		final int vanillaExpLeftX = screenWidth / 2 - 91; // leftmost edge of the experience bar
		final int vanillaExpTopY = screenHeight - 32 + BAR_SPACING_ABOVE_EXP_BAR - BAR_HEIGHT; // top of the experience
																								// bar

		/*
		 * Shift our rendering origin to just above the experience bar The top left
		 * corner of the screen is x=0, y=0
		 */
		GL11.glTranslatef(vanillaExpLeftX, vanillaExpTopY - BAR_SPACING_ABOVE_EXP_BAR - BAR_HEIGHT, 0);

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
		blit(0, 0, 0, 0, BAR_WIDTH, BAR_HEIGHT);

		/* This part draws the inside of the bar, which starts 1 pixel right and down */
		GL11.glPushMatrix();

		/* Shift 1 pixel right and down */
		GL11.glTranslatef(1, 1, 0);
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
		GL11.glScalef((BAR_WIDTH - 2) * Math.min(1, manaRun / maxMana), 1, 1);

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

		final int NORMAL_TEXTURE_U = BAR_WIDTH; // red texels - see mbe40_hud_overlay.png
		blit(0, 0, NORMAL_TEXTURE_U, 0, 1, BAR_HEIGHT - 2);

		GL11.glPopMatrix();

		/* Move to the right end of the bar, minus a few pixels. */
		GL11.glTranslatef(BAR_WIDTH - 3, 1, 0);

		/* The default minecraft font is too big, so I scale it down a bit. */
		GL11.glPushMatrix();
		GL11.glScalef(0.5f, 0.5f, 1);

		/* This generates the string that I want to draw. */
		String s = d.format(manaRun) + "/" + d.format(maxMana);

		fr.drawString(s, -fr.getStringWidth(s) + 1, 2, 0x4D0000);
		fr.drawString(s, -fr.getStringWidth(s), 1, 0xFFFFFF);

		GL11.glPopMatrix();

		GL11.glPopMatrix();

		GL11.glPopMatrix();
		GL11.glPopAttrib();
	}
}