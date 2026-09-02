package com.chipoodle.devilrpg.client.gui.scrollableskillscreen.model;

import com.chipoodle.devilrpg.client.gui.scrollableskillscreen.SkillWidget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.gui.ScreenUtils;


public class CustomSkillButton extends Button {
    private static final ResourceLocation WIDGETS_LOCATION = ResourceLocation.parse("devilrpg:textures/gui/advancements/widgets.png");
    private static final ResourceLocation EMPTY_SLOT_IMAGE = ResourceLocation.parse("devilrpg:textures/gui/empty-box.png");
    private final float xScale;
    private final float yScale;
    private final float scaledText;
    private final Enum<?> skillName;
    boolean showSkillNumber;
    private ResourceLocation skillResourceLocation;
    private int drawnSkillLevel;

    private final int textureWidth;
    private final int textureHeight;

    public CustomSkillButton(int x, int y, int buttonWidth, int buttonHeight, String buttonText, ResourceLocation textureResource,
                             int textureWidth, int textureHeight, Enum<?> skillName, int drawnSkillLevel, Button.OnPress function,
                             boolean showSkillNumber, float scaledText) {
        super(x, y, buttonWidth, buttonHeight, Component.literal(buttonText), function, DEFAULT_NARRATION);
        skillResourceLocation = textureResource;
        this.skillName = skillName;
        this.drawnSkillLevel = drawnSkillLevel;
        this.showSkillNumber = showSkillNumber;
        this.scaledText = scaledText;

        xScale = (float) buttonWidth / textureWidth;
        yScale = (float) buttonHeight / textureHeight;

        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
    }

    public static float roundAvoid(float value, int places) {
        float scale = (float) Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }

    /**
     * Draws this button to the screen.
     */
    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        //super.renderButton(poseStack, mouseX, mouseY, partialTicks);
        if (this.visible) {
            Minecraft instance = Minecraft.getInstance();
            //this.isHovered = this.isMouseOver(mouseX, mouseY);


            /*Minecraft minecraft = Minecraft.getInstance();
            RenderSystem.setShaderTexture(0, resourceLocation);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, this.alpha);
            RenderSystem.enableBlend();
            RenderSystem.enableDepthTest();
            blitNineSliced(poseStack, this.getX(), this.getY(), this.getWidth(), this.getHeight(), 20, 4, 200, 20, 0, this.getTextureY());
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            int i = getFGColor();
            this.renderString(poseStack, minecraft.font, i | Mth.ceil(this.alpha * 255.0F) << 24);
            */


            PoseStack poseStack = guiGraphics.pose();

            RenderSystem.setShader(GameRenderer::getPositionTexShader);

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();

            // Fondo: imagen por defecto (empty-box) como ranura del boton.
            SkillWidget.forceNearestFilter(EMPTY_SLOT_IMAGE);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            guiGraphics.blit(EMPTY_SLOT_IMAGE, getX(), getY(), 0, 0.0F, 0.0F, this.width, this.height,
                    this.width, this.height);

            // Icono de la skill encima (si hay skill asignada); el empty-box queda detras.
            // Los iconos de skill tienen fondo transparente, asi que el marco del empty-box
            // se ve alrededor.
            if (this.skillResourceLocation != null && !this.skillResourceLocation.equals(EMPTY_SLOT_IMAGE)) {
                SkillWidget.forceNearestFilter(this.skillResourceLocation);
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, this.alpha);
                guiGraphics.blit(this.skillResourceLocation, getX(), getY(), 0, 0.0F, 0.0F, this.width, this.height,
                        this.width, this.height);
            }

            // Contorno iluminado al pasar el mouse (antes lo daba el 9-slice, eliminado)
            if (this.isHoveredOrFocused()) {
                int borderColor = 0xFFF0E0B8;
                guiGraphics.fill(getX(), getY(), getX() + this.width, getY() + 1, borderColor);
                guiGraphics.fill(getX(), getY() + this.height - 1, getX() + this.width, getY() + this.height, borderColor);
                guiGraphics.fill(getX(), getY(), getX() + 1, getY() + this.height, borderColor);
                guiGraphics.fill(getX() + this.width - 1, getY(), getX() + this.width, getY() + this.height, borderColor);
            }


            //Pinta el texto debajo del botón
            poseStack.pushPose();
            poseStack.translate((this.getX() - this.getX() * 0.5f) - 1, (this.getY() - this.getY() * 0.5f) - 1, 0);
            poseStack.scale(0.5f, 0.5f, 0);
            guiGraphics.drawCenteredString(instance.font, this.getMessage(), this.getX() + this.width + 3, this.getY() + (this.height) + 23, getFGColor());
            poseStack.popPose();

        }


    }

    public boolean isInside(double mouseX, double mouseY) {
        return mouseX >= this.getX() && mouseY >= this.getY() && mouseX < this.getX() + this.width && mouseY < this.getY() + this.height;
    }

    public ResourceLocation getButtonTexture() {
        return skillResourceLocation;
    }

    public void setButtonTexture(ResourceLocation buttonTexture) {
        this.skillResourceLocation = buttonTexture;
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