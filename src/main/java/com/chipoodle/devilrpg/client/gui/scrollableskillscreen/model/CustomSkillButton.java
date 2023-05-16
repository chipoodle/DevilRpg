package com.chipoodle.devilrpg.client.gui.scrollableskillscreen.model;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.ScreenUtils;
import net.minecraftforge.client.gui.widget.ExtendedButton;


public class CustomSkillButton extends Button {
    private final float xScale;
    private final float yScale;
    private final float scaledText;
    private final Enum<?> skillName;
    boolean showSkillNumber;
    private ResourceLocation resourceLocation;
    private int drawnSkillLevel;

    public CustomSkillButton(int x, int y, int buttonWidth, int buttonHeight, String buttonText, ResourceLocation textureResource,
                             int textureWidth, int textureHeight, Enum<?> skillName, int drawnSkillLevel, Button.OnPress function,
                             boolean showSkillNumber, float scaledText) {
        super(x, y, buttonWidth, buttonHeight, Component.literal(buttonText), function, DEFAULT_NARRATION);
        resourceLocation = textureResource;
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
    public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        //super.renderButton(poseStack, mouseX, mouseY, partialTicks);
        if (this.visible) {
            Minecraft instance = Minecraft.getInstance();
            this.isHovered = this.isMouseOver(mouseX, mouseY);


            poseStack.pushPose();
            int i = this.getYImage(this.isHoveredOrFocused());
            ScreenUtils.blitWithBorder(poseStack, WIDGETS_LOCATION, this.getX(), this.getY(), 0, 46 + i * 20, this.width, this.height, 200, 20, 2, 3, 2, 2, this.getBlitOffset());
            poseStack.popPose();

            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, resourceLocation);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, this.alpha);

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
            // Pinta el icono del botón
            blit(poseStack, getX(), getY(), this.getBlitOffset(), 0.0F, 0.0F, this.width, this.height, this.width, this.height);
            this.renderBg(poseStack, instance, mouseX, mouseY);

            //Pinta el texto debajo del botón
            poseStack.pushPose();
            poseStack.translate((this.getX() - this.getX() * 0.5f) - 1, (this.getY() - this.getY() * 0.5f) - 1, 0);
            poseStack.scale(0.5f, 0.5f, 0);
            drawCenteredString(poseStack, instance.font, this.getMessage(), this.getX() + this.width + 3, this.getY() + (this.height) + 23, getFGColor());
            poseStack.popPose();

        }


    }

    public boolean isInside(double mouseX, double mouseY) {
        return mouseX >= this.getX() && mouseY >= this.getY() && mouseX < this.getX() + this.width && mouseY < this.getY() + this.height;
    }

    public ResourceLocation getButtonTexture() {
        return resourceLocation;
    }

    public void setButtonTexture(ResourceLocation buttonTexture) {
        this.resourceLocation = buttonTexture;
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
        this.setX(x);
        this.setY(y);
    }
}