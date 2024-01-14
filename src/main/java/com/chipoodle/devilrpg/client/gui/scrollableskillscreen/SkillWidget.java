package com.chipoodle.devilrpg.client.gui.scrollableskillscreen;

import com.chipoodle.devilrpg.DevilRpg;
import com.google.common.collect.Lists;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.ScreenUtils;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@OnlyIn(Dist.CLIENT)
public class SkillWidget extends GuiComponent {
    // static final int BUTTON_IMAGE_SIZE = 512;
    public static final int BUTTON_IMAGE_SIZE = 256;
    public static final int TARGET_BUTTON_IMAGE_SIZE = 20;
    public static final int FRAME_SIZE = 26;
    public static final float Y_DISTANCE = 27.0F;
    public static final float X_DISTANCE = 27.0F;
    public static final double X_DISTANCE_FACTOR = 1.7;
    public static final double Y_DISTANCE_FACTOR = 1.3;
    private static final String SKILL_GUI_IMG_LOCATION = DevilRpg.MODID + ":textures/gui/skill";
    private static final int[] LINE_BREAK_VALUES = new int[]{0, 10, -10, 25, -25};
    private static final net.minecraft.network.chat.Component MANA_COST = net.minecraft.network.chat.Component.translatable("gui.skills.mana_cost");
    public static ResourceLocation WIDGETS = new ResourceLocation(SKILL_GUI_IMG_LOCATION + "/widgets.png");
    private static List<ResourceLocation> resourceLocations = new ArrayList<>();
    private static int resourceIndex = 0;
    private final SkillTab skillTabGui;
    private final SkillElement skillElement;
    private final SkillDisplayInfo displayInfo;
    private final int width;
    private final List<FormattedCharSequence> description;
    private final Minecraft minecraft;
    private final List<SkillWidget> children = Lists.newArrayList();
    private final int x;
    private final int y;
    private final float xScale;
    private final float yScale;
    private FormattedCharSequence title;
    //private String levelString;
    private SkillWidget parent;
    private SkillProgress skillProgress;

	/*private void updateLevelString(int skillPoint, int maxSkillPoint) {
		levelString = "" + skillPoint + "/" + maxSkillPoint;
	}*/

    public SkillWidget(SkillTab skillTabGui, Minecraft minecraft, SkillElement skillElement, int skillPoint,
                       int maxSkillPoint) {
        this.skillTabGui = skillTabGui;
        this.skillElement = skillElement;
        this.displayInfo = skillElement.getDisplay();
        this.minecraft = minecraft;

        skillProgress = new SkillProgress(skillPoint, maxSkillPoint);
        updateFormattedLevelString(skillPoint, maxSkillPoint);

        this.x = Mth.floor(getDisplayInfo().getX() * X_DISTANCE * X_DISTANCE_FACTOR);
        this.y = Mth.floor(getDisplayInfo().getY() * Y_DISTANCE * Y_DISTANCE_FACTOR);
        // int i = skillElement.getRequirementCount();
        int i = maxSkillPoint;
        int j = String.valueOf(i).length();
        int k = i > 1 ? minecraft.font.width("  ") + minecraft.font.width("0") * j * 2 + minecraft.font.width("/") : 0;
        int l = 29 + minecraft.font.width(this.title) + k;

        Integer manaCost = 0;
        MutableComponent desc = getDisplayInfo().getDescription().copy();
        if (skillElement.getSkillManaCost() != null) {
            manaCost = skillElement.getSkillManaCost().getManaCost();
            desc = desc.copy().append("\n").append(MANA_COST).append(Component.nullToEmpty(" " + manaCost));
        }
        this.description = Language.getInstance().getVisualOrder(this.getDescriptionLines(
                ComponentUtils.mergeStyles(desc, Style.EMPTY.applyFormat(getDisplayInfo().getFrame().getFormat())),
                l));

        for (FormattedCharSequence ireorderingprocessor : this.description) {
            l = Math.max(l, minecraft.font.width(ireorderingprocessor));
        }

        this.width = l + 3 + 5;

        skillTabGui.getScreen().getSkillsResourceLocations().put(skillElement.getSkillCapability(),
                this.getDisplayInfo().getImage());

        xScale = (float) (TARGET_BUTTON_IMAGE_SIZE) / BUTTON_IMAGE_SIZE;
        yScale = (float) TARGET_BUTTON_IMAGE_SIZE / BUTTON_IMAGE_SIZE;
    }

    private static float getTextWidth(StringSplitter manager, List<FormattedText> text) {
        return (float) text.stream().mapToDouble(manager::stringWidth).max().orElse(0.0D);
    }

    public static void changeWidgetTheme(boolean forward) {
        resourceLocations = loadWidgetThemeImages();
        int size = resourceLocations.size();

        if (size > 0) {
            if (forward) {
                // Move forward in the list
                resourceIndex = (resourceIndex + 1) % size;
            } else {
                // Move backward in the list
                resourceIndex = (resourceIndex - 1 + size) % size;
            }

            WIDGETS = resourceLocations.get(resourceIndex);
        }
    }

    public static List<ResourceLocation> loadWidgetThemeImages() {
        if (!resourceLocations.isEmpty()) {
            return resourceLocations;
        }

        try {
            // Obtén la URL de los recursos desde el classloader del mod
            URL resourceURL = DevilRpg.class.getClassLoader().getResource("assets/devilrpg/textures/gui/skill");

            if (resourceURL != null) {
                Path directory = Paths.get(resourceURL.toURI());

                // Utiliza Files.walk para recorrer el directorio y encontrar archivos que cumplan con ciertos criterios
                try (Stream<Path> walk = Files.walk(directory, FileVisitOption.FOLLOW_LINKS)) {
                    resourceLocations = walk.filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().startsWith("widget") && path.toString().endsWith(".png"))
                            .map(path -> {
                                // Convierte la ruta del archivo a una ruta relativa al directorio del mod
                                String relativePath = directory.relativize(path).toString();
                                String resourcePath = SKILL_GUI_IMG_LOCATION + "/" + relativePath.replace(File.separator, "/");
                                // Crea un ResourceLocation y agrégalo a la lista
                                return new ResourceLocation(resourcePath);
                            })
                            .sorted()
                            .toList();
                }
            }
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }

        return resourceLocations;
    }

    /**
     * Actualiza los puntos utilizads / puntos máximos del skill
     *
     * @param skillPoint
     * @param maxSkillPoint
     */
    public void updateFormattedLevelString(int skillPoint, int maxSkillPoint) {
        //updateLevelString(skillPoint, maxSkillPoint);
        updateTitle();
        skillProgress.update(skillPoint, maxSkillPoint);
    }

    private void updateTitle() {
        this.title = Language.getInstance()
                .getVisualOrder(this.minecraft.font.substrByWidth(getDisplayInfo().getTitle(), 163));
		/*if (skillElement.getParent() != null) {
			IReorderingProcessor levelPr = IReorderingProcessor.forward(" " + levelString, Style.EMPTY);
			this.title = IReorderingProcessor.composite(this.title, levelPr);
		}*/
    }

    private List<FormattedText> getDescriptionLines(Component component, int maxWidth) {
        StringSplitter charactermanager = this.minecraft.font.getSplitter();
        List<FormattedText> list = null;
        float f = Float.MAX_VALUE;

        for (int i : LINE_BREAK_VALUES) {
            List<FormattedText> list1 = charactermanager.splitLines(component, maxWidth - i, Style.EMPTY);
            float f1 = Math.abs(getTextWidth(charactermanager, list1) - maxWidth);
            if (f1 <= 10.0F) {
                return list1;
            }

            if (f1 < f) {
                f = f1;
                list = list1;
            }
        }

        return list;
    }

    @Nullable
    private SkillWidget getFirstVisibleParent(SkillElement skillIn) {
        do {
            skillIn = skillIn.getParent();
        } while (skillIn != null && skillIn.getDisplay() == null);

        return skillIn != null && skillIn.getDisplay() != null ? this.skillTabGui.getSkillElementGui(skillIn) : null;
    }

    public void drawConnectionLineToParent(PoseStack matrixStack, int x, int y, boolean dropShadow) {
        if (this.parent != null) {
            int i = x + this.parent.x + FRAME_SIZE / 2;
            int j = x + this.parent.x + FRAME_SIZE + 4;
            int k = y + this.parent.y + FRAME_SIZE / 2;
            int l = x + this.x + 13;
            int i1 = y + this.y + 13;
            int j1 = dropShadow ? -16777216 : -1;
            if (dropShadow) {
                this.hLine(matrixStack, j, i, k - 1, j1);
                this.hLine(matrixStack, j + 1, i, k, j1);
                this.hLine(matrixStack, j, i, k + 1, j1);
                this.hLine(matrixStack, l, j - 1, i1 - 1, j1);
                this.hLine(matrixStack, l, j - 1, i1, j1);
                this.hLine(matrixStack, l, j - 1, i1 + 1, j1);
                this.vLine(matrixStack, j - 1, i1, k, j1);
                this.vLine(matrixStack, j + 1, i1, k, j1);
            } else {
                this.hLine(matrixStack, j, i, k, j1);
                this.hLine(matrixStack, l, j, i1, j1);
                this.vLine(matrixStack, j, i1, k, j1);
            }
        }

        for (SkillWidget skillWidget : this.children) {
            skillWidget.drawConnectionLineToParent(matrixStack, x, y, dropShadow);
        }

    }

    /**
     * @return
     */
    private SkillState getSkillState() {
        SkillState skillState;
        float f = this.skillProgress == null ? 0.0F : this.skillProgress.getPercent();
        if (f >= 1.0F) {
            skillState = SkillState.OBTAINED;
        } else {
            skillState = SkillState.UNOBTAINED;
        }
        return skillState;
    }

    public void setAdvancementProgress(SkillProgress advancementProgressIn) {
        this.skillProgress = advancementProgressIn;
    }

    public void addGuiSkill(SkillWidget guiSkillsIn) {
        //DevilRpg.LOGGER.info("|----------- addGuiSkill {}", (guiSkillsIn.title.toString()));
        this.children.add(guiSkillsIn);
    }

    public boolean isDisabled() {
        if (this.parent == null)
            return false;

        return !(parent.getSkillProgress().hasProgress() || parent.getSkillProgress().isDone());
    }

    /**
     * Pinta los marcos con sus elementos internos cuando el mouse no está sobre
     * ellos
     *
     * @param matrixStack
     * @param x
     * @param y
     */
    public void drawSkills(PoseStack matrixStack, int x, int y) {
        // DevilRpg.LOGGER.info("|---drawSkill x" +x+" y "+y+" title: "+
        // this.displayInfo.getTitle());

        if (!this.getDisplayInfo().isHidden() || (this.skillProgress != null && this.skillProgress.isDone())) {

            SkillState skillState = getSkillState();

            // Texturas de los marcos, y barras de título de los tooltips
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, WIDGETS);

            // Pinta el icono del botón
            this.blit(matrixStack, x + this.x + 3, y + this.y, this.getDisplayInfo().getFrame().getIcon(),
                    128 + skillState.getId() * FRAME_SIZE, FRAME_SIZE, FRAME_SIZE);

            // Pinta el marco botón
            drawButton(matrixStack, x, y, false, this.getDisplayInfo().getImage(), false, isDisabled());

            // Pinta a los hijos
            for (SkillWidget childrenEntry : this.children) {
                childrenEntry.drawSkills(matrixStack, x, y);
            }
        }

    }

    public void drawHoveredSkill(PoseStack matrixStack, int x, int y, float fade, int width, int height) {
        boolean widthFlag = width + x + this.x + this.width + FRAME_SIZE >= this.skillTabGui.getScreen().width;
        String skillProgressString = this.skillProgress == null ? null : this.skillProgress.getProgressText();
        int i = skillProgressString == null ? 0 : this.minecraft.font.width(skillProgressString);
        boolean flag1 = 113 - y - this.y - FRAME_SIZE <= 6 + this.description.size() * 9;
        float f = this.skillProgress == null ? 0.0F : this.skillProgress.getPercent();
        int j = Mth.floor(f * this.width);
        SkillState advancementstate;
        SkillState advancementstate1;
        SkillState advancementstate2;

        if (f >= 1.0F) {
            j = this.width / 2;
            advancementstate = SkillState.OBTAINED;
            advancementstate1 = SkillState.OBTAINED;
            advancementstate2 = SkillState.OBTAINED;
        } else if (j < 2) {
            j = this.width / 2;
            advancementstate = SkillState.UNOBTAINED;
            advancementstate1 = SkillState.UNOBTAINED;
            advancementstate2 = SkillState.UNOBTAINED;
        } else if (j > this.width - 2) {
            j = this.width / 2;
            advancementstate = SkillState.OBTAINED;
            advancementstate1 = SkillState.OBTAINED;
            advancementstate2 = SkillState.UNOBTAINED;
        } else {
            advancementstate = SkillState.OBTAINED;
            advancementstate1 = SkillState.UNOBTAINED;
            advancementstate2 = SkillState.UNOBTAINED;
        }

        int k = this.width - j;
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, WIDGETS);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();

        // RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        // RenderSystem.enableBlend();
        int l = y + this.y;
        int i1;
        if (widthFlag) {
            i1 = x + this.x - this.width + FRAME_SIZE + 6;
        } else {
            i1 = x + this.x;
        }

        int j1 = 32 + this.description.size() * 9;

        if (!this.description.isEmpty()) {
            if (flag1) {
                this.render9Sprite(matrixStack, i1, l + FRAME_SIZE - j1, this.width, j1, 10, 200, FRAME_SIZE, 0, 52);
            } else {
                this.render9Sprite(matrixStack, i1, l, this.width, j1, 10, 200, FRAME_SIZE, 0, 52);
            }
        }

        // pinta la mitad izquierda de la barra del título
        this.blit(matrixStack, i1, l, 0, advancementstate.getId() * FRAME_SIZE, j, FRAME_SIZE);
        // pinta la mitad derecha de la barra del título
        this.blit(matrixStack, i1 + j, l, 200 - k, advancementstate1.getId() * FRAME_SIZE, k, FRAME_SIZE);
        // Pinta el marco del boton
        this.blit(matrixStack, x + this.x + 3, y + this.y, this.getDisplayInfo().getFrame().getIcon(),
                128 + advancementstate2.getId() * FRAME_SIZE, FRAME_SIZE, FRAME_SIZE);

        if (widthFlag) {
            // pinta título
            this.minecraft.font.drawShadow(matrixStack, this.title, (i1 + 5), (y + this.y + 9), -1);
            if (skillProgressString != null) {
                this.minecraft.font.drawShadow(matrixStack, skillProgressString, (x + this.x - i), (y + this.y + 9),
                        -1);
            }
        } else {
            // pinta título
            this.minecraft.font.drawShadow(matrixStack, this.title, (x + this.x + 32), (y + this.y + 9), -1);
            if (skillProgressString != null) {
                this.minecraft.font.drawShadow(matrixStack, skillProgressString, (x + this.x + this.width - i - 5),
                        (y + this.y + 9), -1);
            }
        }

        if (flag1) {
            for (int k1 = 0; k1 < this.description.size(); ++k1) {
                // Pinta contenido
                this.minecraft.font.draw(matrixStack, this.description.get(k1), (i1 + 5),
                        (float) (l + FRAME_SIZE - j1 + 7 + k1 * 9), -5592406);
            }

        } else {
            for (int l1 = 0; l1 < this.description.size(); ++l1) {
                // Pinta contenido
                this.minecraft.font.draw(matrixStack, this.description.get(l1), (i1 + 5),
                        (float) (y + this.y + 9 + 17 + l1 * 9), -5592406);
            }
        }

        SkillState skillState = getSkillState();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, WIDGETS);
        // Pinta el ícono del botón
        this.blit(matrixStack, x + this.x + 3, y + this.y, this.getDisplayInfo().getFrame().getIcon(),
                128 + skillState.getId() * FRAME_SIZE, FRAME_SIZE, FRAME_SIZE);

        drawButton(matrixStack, x, y, false, this.getDisplayInfo().getImage(), false, isDisabled());

        // Pinta el icono del marco
        // this.minecraft.getItemRenderer().renderItemAndEffectIntoGuiWithoutEntity(this.displayInfo.getIcon(),x
        // + this.x + 8, y + this.y + 5);
    }

    protected void render9Sprite(PoseStack p_97288_, int p_97289_, int p_97290_, int p_97291_, int p_97292_, int p_97293_, int p_97294_, int p_97295_, int p_97296_, int p_97297_) {
        this.blit(p_97288_, p_97289_, p_97290_, p_97296_, p_97297_, p_97293_, p_97293_);
        this.renderRepeating(p_97288_, p_97289_ + p_97293_, p_97290_, p_97291_ - p_97293_ - p_97293_, p_97293_, p_97296_ + p_97293_, p_97297_, p_97294_ - p_97293_ - p_97293_, p_97295_);
        this.blit(p_97288_, p_97289_ + p_97291_ - p_97293_, p_97290_, p_97296_ + p_97294_ - p_97293_, p_97297_, p_97293_, p_97293_);
        this.blit(p_97288_, p_97289_, p_97290_ + p_97292_ - p_97293_, p_97296_, p_97297_ + p_97295_ - p_97293_, p_97293_, p_97293_);
        this.renderRepeating(p_97288_, p_97289_ + p_97293_, p_97290_ + p_97292_ - p_97293_, p_97291_ - p_97293_ - p_97293_, p_97293_, p_97296_ + p_97293_, p_97297_ + p_97295_ - p_97293_, p_97294_ - p_97293_ - p_97293_, p_97295_);
        this.blit(p_97288_, p_97289_ + p_97291_ - p_97293_, p_97290_ + p_97292_ - p_97293_, p_97296_ + p_97294_ - p_97293_, p_97297_ + p_97295_ - p_97293_, p_97293_, p_97293_);
        this.renderRepeating(p_97288_, p_97289_, p_97290_ + p_97293_, p_97293_, p_97292_ - p_97293_ - p_97293_, p_97296_, p_97297_ + p_97293_, p_97294_, p_97295_ - p_97293_ - p_97293_);
        this.renderRepeating(p_97288_, p_97289_ + p_97293_, p_97290_ + p_97293_, p_97291_ - p_97293_ - p_97293_, p_97292_ - p_97293_ - p_97293_, p_97296_ + p_97293_, p_97297_ + p_97293_, p_97294_ - p_97293_ - p_97293_, p_97295_ - p_97293_ - p_97293_);
        this.renderRepeating(p_97288_, p_97289_ + p_97291_ - p_97293_, p_97290_ + p_97293_, p_97293_, p_97292_ - p_97293_ - p_97293_, p_97296_ + p_97294_ - p_97293_, p_97297_ + p_97293_, p_97294_, p_97295_ - p_97293_ - p_97293_);
    }

    protected void renderRepeating(PoseStack p_97278_, int p_97279_, int p_97280_, int p_97281_, int p_97282_, int p_97283_, int p_97284_, int p_97285_, int p_97286_) {
        for (int i = 0; i < p_97281_; i += p_97285_) {
            int j = p_97279_ + i;
            int k = Math.min(p_97285_, p_97281_ - i);

            for (int l = 0; l < p_97282_; l += p_97286_) {
                int i1 = p_97280_ + l;
                int j1 = Math.min(p_97286_, p_97282_ - l);
                this.blit(p_97278_, j, i1, p_97283_, p_97284_, k, j1);
            }
        }

    }

    /**
     * @param matrixStack
     * @param x
     * @param y
     * @param mostrarPuntos
     * @param image
     * @param superpuesto
     * @param disabled
     */
    @SuppressWarnings("deprecation")
    public void drawButton(PoseStack matrixStack, int x, int y, boolean mostrarPuntos, ResourceLocation image,
                           boolean superpuesto, boolean disabled) {

        if (mostrarPuntos) {
            // Pinta puntos asignados/puntos máximos
            matrixStack.pushPose();
            //drawOuterSkillLevel(matrixStack, (int) (x), (int) (y + FRAME_SIZE));
            matrixStack.popPose();
        }
        int posX = this.x + x;
        int posY = this.y + y;

        // Pinta el botón
        matrixStack.pushPose();
        if (superpuesto) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO);
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        } else {
            RenderSystem.enableDepthTest();
            if (disabled) {
                RenderSystem.setShaderColor(100.0F, 100.0F, 100.0F, 0.4F);
                RenderSystem.enableBlend();
            }
        }

        RenderSystem.setShaderTexture(0, image);
        //this.minecraft.getTextureManager().bind(image);
        matrixStack.translate((posX), (posY), 0);
        matrixStack.scale(xScale, yScale, 0);
        matrixStack.translate(posX * -1.0f, posY * -1.0f, 0);
        ScreenUtils.blitWithBorder(matrixStack, (int) (posX + BUTTON_IMAGE_SIZE * 0.302734375),
                (int) (posY + BUTTON_IMAGE_SIZE * 0.1171875), 0, 0, BUTTON_IMAGE_SIZE, BUTTON_IMAGE_SIZE,
                BUTTON_IMAGE_SIZE, BUTTON_IMAGE_SIZE, 0, 1);

        if (disabled)
            RenderSystem.disableBlend();

        matrixStack.popPose();

    }

    public boolean isMouseOver(int scrollX, int scrollY, int mouseX, int mouseY) {
        if (!this.getDisplayInfo().isHidden() || this.skillProgress != null && this.skillProgress.isDone()) {
            int i = scrollX + this.x + 3;
            int j = i + FRAME_SIZE - 1;
            int k = scrollY + this.y;
            int l = k + FRAME_SIZE - 1;
            return mouseX >= i && mouseX <= j && mouseY >= k && mouseY <= l;
        } else {
            return false;
        }
    }

    public void attachToParent() {
        if (this.parent == null && this.skillElement.getParent() != null) {
            this.parent = this.getFirstVisibleParent(this.skillElement);
            if (this.parent != null) {
                this.parent.addGuiSkill(this);
            }
        }

    }

    public int getY() {
        return this.y;
    }

    public int getX() {
        return this.x;
    }

    /*-----------------------------*/
    /*public void skillButtonPressed(Button pressedButton) {
        CustomSkillButton pressed = ((CustomSkillButton) pressedButton);
        SkillEnum skilEnum = (SkillEnum) pressed.getEnum();

        skillTabGui.getScreen().getSkillCap().ifPresent(x -> {
            HashMap<SkillEnum, Integer> skillsPoints = x.getSkillsPoints();
            Integer e = skillsPoints.get(((CustomSkillButton) pressedButton).getEnum());
            if (e != null && skillTabGui.getScreen().getExpCap().isPresent()) {
                e += skillTabGui.getScreen().getExpCap().map(exp -> exp.consumePoint()).orElse(0);
                skillsPoints.put(skilEnum, e);
                x.setSkillsPoints(skillsPoints, minecraft.player);
            }

        });
    }*/

    @Override
    public String toString() {
        return "SkillEntryGui [" + ", children=" + children.size() + ", width=" + width + ", x=" + x + ", y=" + y + "]";
    }

    public final List<SkillWidget> getChildren() {
        return children;
    }

    public SkillElement getSkillElement() {
        return skillElement;
    }

    public SkillDisplayInfo getDisplayInfo() {
        return displayInfo;
    }

    public SkillProgress getSkillProgress() {
        return skillProgress;
    }
}
