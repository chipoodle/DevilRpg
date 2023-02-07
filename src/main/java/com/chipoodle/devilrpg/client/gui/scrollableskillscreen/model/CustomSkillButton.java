package com.chipoodle.devilrpg.client.gui.scrollableskillscreen.model;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.fml.client.gui.GuiUtils;
import net.minecraftforge.fml.client.gui.widget.ExtendedButton;

public class CustomSkillButton extends ExtendedButton {
	private ResourceLocation resourceLocation;
	private final int textureWidth;
	private final int textureHeight;
	private final float xScale;
	private final float yScale;
	private final float scaledText;
	private final Enum<?> skillName;
	private int drawnSkillLevel;
	boolean showSkillNumber;

	public CustomSkillButton(int x, int y, int buttonWidth, int buttonHeight, String buttonText, ResourceLocation textureResource,
			int textureWidth, int textureHeight, Enum<?> skillName, int drawnSkillLevel, IPressable function,
			boolean showSkillNumber, float scaledText) {
		super(x, y, buttonWidth, buttonHeight, new StringTextComponent(buttonText), function);
		resourceLocation = textureResource;
		this.textureWidth = textureWidth;
		this.textureHeight = textureHeight;
		this.skillName = skillName;
		this.drawnSkillLevel = drawnSkillLevel;
		this.showSkillNumber = showSkillNumber;
		this.scaledText = scaledText;
		
		xScale = (float) buttonWidth / textureWidth;
		yScale = (float) buttonHeight / textureHeight;
	}
	
	public static float roundAvoid(float value, int places) {
	    float scale = (float) Math.pow(10, places);
	    return Math.round(value * scale) / scale;
	}

	/**
	 * Draws this button to the screen.
	 */
	@Override
	public void renderButton(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
		//super.renderButton(matrixStack, mouseX, mouseY, partialTicks);
		
		if (this.visible) {
			Minecraft instance = Minecraft.getInstance();
            this.isHovered = this.isMouseOver(mouseX, mouseY); 
            int k = this.getYImage(this.isHovered());
            GuiUtils.drawContinuousTexturedBox(matrixStack, WIDGETS_LOCATION, this.x, this.y, 0, 46 + k * 20, this.width, this.height, 200, 20, 2, 3, 2, 2, this.getBlitOffset());
            this.renderBg(matrixStack, instance, mouseX, mouseY);

			instance.getTextureManager().bind(resourceLocation);
			RenderSystem.pushMatrix();
			RenderSystem.enableBlend();
			RenderSystem.defaultBlendFunc();
			RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ZERO);
			RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

			RenderSystem.translatef((this.x - this.x * xScale), (this.y - this.y * yScale), 0);
			RenderSystem.scalef(xScale, yScale, 0);
			RenderSystem.translatef(this.x * -xScale/width, this.y * -yScale/height, 0);
			
			AbstractGui.blit(matrixStack, x, y, 0, 0,textureWidth, textureHeight, textureWidth, textureHeight);

			RenderSystem.popMatrix();
			//renderCenteredText(matrixStack);
			RenderSystem.pushMatrix();
			//RenderSystem.translatef((this.x), (this.y), 0);
			//RenderSystem.scalef(0.5f, 0.5f, 0);
			//RenderSystem.translatef(this.x * -1.0f, this.y * -1.0f, 0);
			RenderSystem.translatef((this.x - this.x * 0.5f) - 1,(this.y - this.y * 0.5f) - 1, 0);
			RenderSystem.scalef(0.5f, 0.5f,0);
			drawCenteredString(matrixStack, instance.font, this.getMessage(), this.x + this.width+3, this.y + (this.height) + 23, getFGColor());
			RenderSystem.popMatrix();
		}
		
	}

	@SuppressWarnings("deprecation")
	private void renderCenteredText(MatrixStack matrixStack) {
		
		RenderSystem.pushMatrix();
		int xTitle = (this.x + this.width / 2) + 5;
		int yTitle = (this.y + this.height) + 20;
		int textPositionX = this.x;
		int textPositionY = this.y;
		Minecraft instance = Minecraft.getInstance();
		FontRenderer fontrenderer = instance.font;
		int color = getFGColor() | MathHelper.ceil(this.alpha * 255.0F) << 24;
		if (packedFGColor != 0) {
			color = getFGColor() | MathHelper.ceil(this.alpha * 255.0F) << 4; // packedFGColor;
		} else if (!this.active) {
			color = 10526880;
		} else if (this.isHovered()) {
			color = 16777120;

		}

		RenderSystem.translatef((textPositionX - textPositionX * scaledText) - 1,(textPositionY - textPositionY * scaledText) - 1, 0);
		RenderSystem.scalef(scaledText, scaledText, scaledText);
		AbstractGui.drawCenteredString(matrixStack,fontrenderer, getMessage(), xTitle, yTitle, color);
		if (showSkillNumber)
			AbstractGui.drawCenteredString(matrixStack,fontrenderer, drawnSkillLevel + "", this.x + this.width,(this.y + this.height) - 20, color);
		RenderSystem.popMatrix();
	}

	public boolean isInside(double mouseX, double mouseY) {
		return mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
	}

	public ResourceLocation getButtonTexture() {
		return resourceLocation;
	}

	public void setButtonTexture(ResourceLocation buttonTexture) {
		this.resourceLocation = buttonTexture;
	}

	public int getTextureWidth() {
		return textureWidth;
	}

	public int getTextureHeight() {
		return textureHeight;
	}

	public Enum<?> getEnum() {
		return skillName;
	}

	public int getDrawnSkillLevel() {
		return drawnSkillLevel;
	}

	public void setDrawnSkillLevel(int drawnSkillLevel) {
		this.drawnSkillLevel = drawnSkillLevel;
	}

    public void setCoords(int x, int y) {
    	this.x = x;
    	this.y = y;
    }
}