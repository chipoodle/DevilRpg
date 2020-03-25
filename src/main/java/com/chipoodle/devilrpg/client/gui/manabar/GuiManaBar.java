package com.chipoodle.devilrpg.client.gui.manabar;

import org.lwjgl.opengl.GL11;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.mana.IBaseManaCapability;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapabilityProvider;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import net.minecraftforge.common.util.LazyOptional;

public class GuiManaBar extends AbstractGui {
	private Minecraft mc;
	private float manaRun;
	private float maxMana;

	private static final ResourceLocation texturepath = new ResourceLocation(
			DevilRpg.MODID + ":textures/gui/mana_bar2.png");

	public GuiManaBar() {
		super();
		mc = Minecraft.getInstance();
	}

	public void draw(RenderGameOverlayEvent event) {

		if (event.isCancelable() || event.getType() != ElementType.EXPERIENCE) {
			return;
		}
		
		LazyOptional<IBaseManaCapability> playerCapability =  mc.player.getCapability(PlayerManaCapabilityProvider.MANA_CAP);
		if(!playerCapability.isPresent())
			return;
		
		maxMana = playerCapability.map(x -> x.getMaxMana()).orElse(0.0f);
		manaRun = playerCapability.map(x -> x.getMana()).orElse(0.0f);
		if (maxMana == 0.0f) {
			return;
		} else {
			manaRun += 0.10;
			playerCapability.ifPresent(x -> x.setMana(manaRun > maxMana ? maxMana : manaRun));

		}

		// Starting position for the mana bar - 2 pixels from the top left
		// corner.
		// The center of the screen can be gotten like this during this event:
		int xPos = (int) (event.getWindow().getScaledWidth() / 1.25);
		int yPos = (int) (event.getWindow().getScaledHeight() / 1.11);

		// Be sure to offset based on your texture size or your texture will not
		// be truly centered:
		// int xPos = (event.getResolution().getScaledWidth() + textureWidth) /
		// 2;
		// int yPos = (event.getResolution().getScaledHeight() + textureHeight)
		// / 2;

		// setting all color values to 1.0F will render the texture as it
		// appears in your texture file
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

		// Somewhere in Minecraft vanilla code it says to do this because of a
		// lighting bug
		GL11.glDisable(GL11.GL_LIGHTING);

		// Bind your texture to the render engine
		this.mc.getTextureManager().bindTexture(texturepath);

		/*
		 * The parameters for drawTexturedModalRect are as follows:
		 * 
		 * drawTexturedModalRect(int x, int y, int u, int v, int width, int height);
		 * 
		 * x and y are the on-screen position at which to render. u and v are the
		 * coordinates of the most upper-left pixel in your texture file from which to
		 * start drawing. width and height are how many pixels to render from the start
		 * point (u, v)
		 */
		// First draw the background layer. In my texture file, it starts at the
		// upper-
		// left corner (x=0, y=0), is 50 pixels long and 4 pixels thick (y
		// value)
		this.blit(xPos, yPos, 0, 0, 50, 4);
		// Then draw the foreground; it's located just below the background in
		// my
		// texture file, so it starts at x=0, y=4, is only 2 pixels thick and 50
		// length
		// Why y=4 and not y=5? Y starts at 0, so 0,1,2,3 = 4 pixels for the
		// background

		// However, we want the length to be based on current mana, so we need a
		// new variable:

		int manabarwidth = (int) ((manaRun / maxMana) * 50);

		// Now we can draw our mana bar at yPos+1 so it centers in the
		// background:

		GL11.glEnable(GL11.GL_BLEND);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		GL11.glDepthMask(false);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		GL11.glDisable(GL11.GL_ALPHA_TEST);
		// Here we draw the background bar which contains a transparent section;
		// note the new size
		this.blit(xPos, yPos, 0, 0, 56, 9); // Contenedor de mana 9 pixeles de ancho
		// You can keep drawing without changing anything, but see the following
		// note!
		this.blit(xPos + 3, yPos + 3, 0, 9, manabarwidth, 3); // Barra de mana
		// NOTE: be sure to reset the openGL settings after you're done or your
		// character model will be messed up
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glDepthMask(true);
	}
}
