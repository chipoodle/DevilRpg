package com.chipoodle.devilrpg.client.gui.scrollableskillscreen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.ScreenUtils;

@OnlyIn(Dist.CLIENT)
public enum SkillTabType {
    ABOVE(0, 0, 28, 32, 8), BELOW(84, 0, 28, 32, 8), LEFT(0, 64, 32, 28, 5), RIGHT(96, 64, 32, 28, 5);

    public static final int MAX_TABS = java.util.Arrays.stream(values()).mapToInt(e -> e.max).sum();
    private final int textureX;
    private final int textureY;
    private final int width;
    private final int height;
    private final int max;

    SkillTabType(int textureX, int textureY, int widthIn, int heightIn, int max) {
        this.textureX = textureX;
        this.textureY = textureY;
        this.width = widthIn;
        this.height = heightIn;
        this.max = max;
    }

    public int getMax() {
        return this.max;
    }

    public void renderTabSelectorBackground(PoseStack poseStack, GuiComponent abstractGui, int offsetX, int offsetY, boolean isSelected, int index) {
        int i = this.textureX;
        if (index > 0) {
            i += this.width;
        }

        if (index == this.max - 1) {
            i += this.width;
        }

        int j = isSelected ? this.textureY + this.height : this.textureY;
        abstractGui.blit(poseStack, offsetX + this.getX(index), offsetY + this.getY(index), i, j, this.width,
                this.height);
    }

    public void drawIcon(int offsetX, int offsetY, int index, ItemRenderer renderItemIn, ItemStack stack) {
        int i = offsetX + this.getX(index);
        int j = offsetY + this.getY(index);
        switch (this) {
            case ABOVE:
                i += 6;
                j += 9;
                break;
            case BELOW:
                i += 6;
                j += 6;
                break;
            case LEFT:
                i += 10;
                j += 5;
                break;
            case RIGHT:
                i += 6;
                j += 5;
        }

        renderItemIn.renderAndDecorateFakeItem(stack, i, j);

    }

    public void drawIconImage(PoseStack poseStack, int offsetX, int offsetY, int index, SkillWidget skillWidget) {
        int i = offsetX + this.getX(index);
        int j = offsetY + this.getY(index);
        switch (this) {
            case ABOVE:
                i += 6;
                j += 9;
                break;
            case BELOW:
                i += 6;
                j += 6;
                break;
            case LEFT:
                i += 10;
                j += 5;
                break;
            case RIGHT:
                i += 6;
                j += 5;
        }

        float width = 20f;
        float height = 20f;
        float xScale = width / SkillWidget.BUTTON_IMAGE_SIZE;
        float yScale = height / SkillWidget.BUTTON_IMAGE_SIZE;


        poseStack.pushPose();
        RenderSystem.enableDepthTest();
        RenderSystem.setShaderTexture(0, skillWidget.getDisplayInfo().getImage());

        poseStack.translate((i - 2), (j), 0);
        poseStack.scale(xScale, yScale, 0);
        poseStack.translate(i * -1.0f, j * -1.0f, 0);

        ScreenUtils.blitWithBorder(poseStack, i, j, 0, 0, SkillWidget.BUTTON_IMAGE_SIZE, SkillWidget.BUTTON_IMAGE_SIZE, SkillWidget.BUTTON_IMAGE_SIZE, SkillWidget.BUTTON_IMAGE_SIZE, 0, 1);
        poseStack.popPose();

    }

    public int getX(int index) {
        switch (this) {
            case ABOVE:
                return (this.width + 4) * index;
            case BELOW:
                return (this.width + 4) * index;
            case LEFT:
                return -this.width + 4;
            case RIGHT:
                return 248;
            default:
                throw new UnsupportedOperationException("Don't know what this tab type is!" + this);
        }
    }

    public int getY(int index) {
        switch (this) {
            case ABOVE:
                return -this.height + 4;
            case BELOW:
                return 136;
            case LEFT:
                return this.height * index;
            case RIGHT:
                return this.height * index;
            default:
                throw new UnsupportedOperationException("Don't know what this tab type is!" + this);
        }
    }

    public boolean inInsideTabSelector(int offsetX, int offsetY, int index, double mouseX, double mouseY) {
        int i = offsetX + this.getX(index);
        int j = offsetY + this.getY(index);
        return mouseX > (double) i && mouseX < (double) (i + this.width) && mouseY > (double) j
                && mouseY < (double) (j + this.height);
    }

}
