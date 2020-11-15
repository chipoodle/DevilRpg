package com.chipoodle.devilrpg.client.gui.skillbook;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.fml.client.gui.GuiUtils;

public class CustomGuiButton extends Button {
	private ResourceLocation resourceLocation;
	private int textureWidth;
	private int textureHeight;
	private float scale = 0.075F;
	private float scaledText = 7.0f;
	private float zLevel = 2;
	private Enum<?> skillName;
	private int pageBelonging;
	private int drawnSkillLevel;
	boolean showSkillNumber;

	public CustomGuiButton(int x, int y, int widthIn, int heightIn, String buttonText, ResourceLocation textureResource,
			int textureWidth, int textureHeight, Enum<?> skillName, int drawnSkillLevel, IPressable function,
			int pageBelonging, boolean showSkillNumber) {
		super(x, y, widthIn, heightIn, new StringTextComponent(buttonText), function);
		resourceLocation = textureResource;
		this.textureWidth = textureWidth;
		this.textureHeight = textureHeight;
		this.skillName = skillName;
		this.pageBelonging = pageBelonging;
		this.drawnSkillLevel = drawnSkillLevel;
		this.showSkillNumber = showSkillNumber;
	}

	/**
	 * Draws this button to the screen.
	 */
	@Override
	public void renderButton(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
		if (this.visible) {
			Minecraft instance = Minecraft.getInstance();
			instance.getTextureManager().bindTexture(resourceLocation);
			// this.isHovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x +
			// this.width && mouseY < this.y + this.height;
			this.isHovered = this.isMouseOver(mouseX, mouseY); // isInside(mouseX, mouseY);
			int i = 3 * this.getYImage(this.isHovered());

			RenderSystem.pushMatrix();
			RenderSystem.enableBlend();
			RenderSystem.defaultBlendFunc();
			RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
					GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
					GlStateManager.DestFactor.ZERO);
			RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
					GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
			RenderSystem.translatef((this.x - this.x * scale) - 1, (this.y - this.y * scale) - 1, 0);
			RenderSystem.scalef(scale, scale, scale);

			GuiUtils.drawContinuousTexturedBox(resourceLocation, this.x, this.y, i, i, Math.round(this.width / scale),
					Math.round(this.height / scale), Math.round(this.textureWidth / scale),
					Math.round(this.textureHeight / scale), 0, 0, 0, 0, zLevel);
			renderCenteredText(matrixStack);
			RenderSystem.popMatrix();
		}
	}

	private void renderCenteredText(MatrixStack matrixStack) {
		int xTitle = (this.x + this.width / 2) + 5;
		int yTitle = (this.y + this.height) + 20;
		int textPositionX = this.x;
		int textPositionY = this.y;
		Minecraft instance = Minecraft.getInstance();
		FontRenderer fontrenderer = instance.fontRenderer;
		int color = getFGColor() | MathHelper.ceil(this.alpha * 255.0F) << 24;
		if (packedFGColor != 0) {
			color = getFGColor() | MathHelper.ceil(this.alpha * 255.0F) << 4; // packedFGColor;
		} else if (!this.active) {
			color = 10526880;
		} else if (this.isHovered()) {
			color = 16777120;

		}

		RenderSystem.translatef((textPositionX - textPositionX * scaledText) - 1,
				(textPositionY - textPositionY * scaledText) - 1, 0);
		RenderSystem.scalef(scaledText, scaledText, scaledText);
		this.drawCenteredString(matrixStack,fontrenderer, getMessage(), xTitle, yTitle, color);
		if (showSkillNumber)
			this.drawCenteredString(matrixStack,fontrenderer, drawnSkillLevel + "", this.x + this.width,
					(this.y + this.height) - 20, color);
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

	public float getScale() {
		return this.scale;
	}

	public Enum<?> getSkillName() {
		return skillName;
	}

	public int getPageBelonging() {
		return pageBelonging;
	}

	public float getzLevel() {
		return zLevel;
	}

	public int getDrawnSkillLevel() {
		return drawnSkillLevel;
	}

	public void setDrawnSkillLevel(int drawnSkillLevel) {
		this.drawnSkillLevel = drawnSkillLevel;
	}

	public void setzLevel(int i) {
		zLevel = i;
	}

	public float getHeight() {
		return this.height;
	}

}