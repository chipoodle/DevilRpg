package com.chipoodle.devilrpg.client.gui.scrollableskillscreen;

import com.chipoodle.devilrpg.DevilRpg;
import com.google.common.collect.Lists;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.gui.ScreenUtils;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@OnlyIn(Dist.CLIENT)
public class SkillWidget {
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
    public static ResourceLocation WIDGETS = ResourceLocation.parse(SKILL_GUI_IMG_LOCATION + "/widgets.png");
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
    /*private void updateLevelString(int skillPoint, int maxSkillPoint) {
        levelString = "" + skillPoint + "/" + maxSkillPoint;
    }*/
    private SkillProgress skillProgress;

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

        // Obtenemos el texto del título como String
        StringBuilder titleBuilder = new StringBuilder();
        this.title.accept((index, style, codePoint) -> {
            titleBuilder.append(Character.toChars(codePoint));
            return true;
        });
        String titleText = titleBuilder.toString();

        // Calculamos el ancho del título
        int titleWidth = minecraft.font.width(titleText);

        // Calculamos el ancho del texto de progreso (excepto para 0/0 y 1/0)
        int progressWidth = 0;
        if (!(maxSkillPoint == 0 && (skillPoint == 0 || skillPoint == 1))) {
            String progressText = skillProgress.getSkillPointText();
            progressWidth = minecraft.font.width(progressText) + 5; // +5 de margen
        }

        // Ancho base (icono + márgenes)
        int baseWidth = 29; // 26 (icono) + 3 (margen)

        // Creamos la descripción
        Integer manaCost = 0;
        MutableComponent desc = getDisplayInfo().getDescription().copy();
        if (skillElement.getSkillManaCost() != null) {
            manaCost = skillElement.getSkillManaCost().getManaCost();
            desc = desc.copy().append("\n").append(MANA_COST).append(Component.nullToEmpty(" " + manaCost));
        }

        // Calculamos el ancho mínimo basado en título y progreso
        int minWidth = baseWidth + titleWidth + progressWidth;

        // Generamos las líneas de descripción
        this.description = Language.getInstance().getVisualOrder(this.getDescriptionLines(
                ComponentUtils.mergeStyles(desc, Style.EMPTY.applyFormat(getDisplayInfo().getFrame().getFormat())),
                minWidth));

        // Calculamos el ancho máximo entre todas las líneas de descripción
        int descriptionWidth = minWidth;
        for (FormattedCharSequence line : this.description) {
            descriptionWidth = Math.max(descriptionWidth, minecraft.font.width(line));
        }

        // Añadimos márgenes adicionales
        descriptionWidth += 8;

        // El ancho final es el mayor entre el ancho mínimo y el de la descripción
        this.width = Math.max(minWidth, descriptionWidth);

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
            URL resourceURL = DevilRpg.class.getClassLoader().getResource("assets/devilrpg/textures/gui/skill/widget");

            if (resourceURL != null) {
                Path directory = Paths.get(resourceURL.toURI());

                // Utiliza Files.walk para recorrer el directorio y encontrar archivos que cumplan con ciertos criterios
                try (Stream<Path> walk = Files.walk(directory, FileVisitOption.FOLLOW_LINKS)) {
                    resourceLocations = walk.filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().startsWith("a-gui-texture-widget-for-rpg-game-celtic-style") && path.toString().endsWith(".png"))
                            .map(path -> {
                                // Convierte la ruta del archivo a una ruta relativa al directorio del mod
                                String relativePath = directory.relativize(path).toString();
                                String resourcePath = SKILL_GUI_IMG_LOCATION + "/widget/" + relativePath.replace(File.separator, "/");
                                // Crea un ResourceLocation y agrégalo a la lista
                                return ResourceLocation.parse(resourcePath);
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
    /**
     * Fuerza el filtrado NEAREST en una textura de la GUI. En 1.21.1 las texturas se suben
     * sin configurar el filtro y OpenGL usa el valor por defecto (MAG=GL_LINEAR), por lo que
     * al ampliarlas se ven borrosas. Con NEAREST la ampliación queda nítida como en 1.19.4.
     * Nota: no obtener el id con getTexture().getId() (devuelve un id invalido antes de subir
     * la textura y crashea en GlStateManager._bindTexture); usamos setShaderTexture como el
     * resto del render.
     */
    public static void forceNearestFilter(ResourceLocation rl) {
        RenderSystem.setShaderTexture(0, rl);
        GlStateManager._texParameter(3553, 10240, 9728); // GL_TEXTURE_MAG_FILTER = GL_NEAREST
        GlStateManager._texParameter(3553, 10241, 9728); // GL_TEXTURE_MIN_FILTER = GL_NEAREST
    }

    public void drawSkills(GuiGraphics guiGraphics, int x, int y) {
        // DevilRpg.LOGGER.info("|---drawSkill x" +x+" y "+y+" title: "+
        // this.displayInfo.getTitle());

        if (!this.getDisplayInfo().isHidden() || (this.skillProgress != null && this.skillProgress.isDone())) {

            SkillState skillState = getSkillState();

            // Texturas de los marcos, y barras de título de los tooltips
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            forceNearestFilter(WIDGETS);

            // Pinta el icono del botón
            guiGraphics.blit(WIDGETS, x + this.x + 3, y + this.y, this.getDisplayInfo().getFrame().getIcon(),
                    128 + skillState.getId() * FRAME_SIZE, FRAME_SIZE, FRAME_SIZE);

            // Pinta el marco botón
            drawButton(guiGraphics, x, y, false, this.getDisplayInfo().getImage(), false, isDisabled());

            // Pinta a los hijos
            for (SkillWidget childrenEntry : this.children) {
                childrenEntry.drawSkills(guiGraphics, x, y);
            }
        }

    }

    public void drawHoveredSkill(GuiGraphics guiGraphics, int x, int y, float fade, int width, int height) {
        boolean widthFlag = width + x + this.x + this.width + FRAME_SIZE >= this.skillTabGui.getScreen().width;
        String skillProgressString = this.skillProgress == null ? null : this.skillProgress.getSkillPointText();
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
        forceNearestFilter(WIDGETS);
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
                this.render9Sprite(guiGraphics, i1, l + FRAME_SIZE - j1, this.width, j1, 10, 200, FRAME_SIZE, 0, 52);
            } else {
                this.render9Sprite(guiGraphics, i1, l, this.width, j1, 10, 200, FRAME_SIZE, 0, 52);
            }
        }

        // pinta la mitad izquierda de la barra del título
        guiGraphics.blit(WIDGETS, i1, l, 0, advancementstate.getId() * FRAME_SIZE, j, FRAME_SIZE);
        // pinta la mitad derecha de la barra del título
        guiGraphics.blit(WIDGETS, i1 + j, l, 200 - k, advancementstate1.getId() * FRAME_SIZE, k, FRAME_SIZE);
        // Pinta el marco del boton
        guiGraphics.blit(WIDGETS, x + this.x + 3, y + this.y, this.getDisplayInfo().getFrame().getIcon(),
                128 + advancementstate2.getId() * FRAME_SIZE, FRAME_SIZE, FRAME_SIZE);

        if (widthFlag) {
            // pinta título
            guiGraphics.drawString(this.minecraft.font, this.title, (i1 + 5), (y + this.y + 9), -1, true);
            if (skillProgressString != null) {
                guiGraphics.drawString(this.minecraft.font, skillProgressString, (x + this.x - i), (y + this.y + 9),
                        -1, true);
            }
        } else {
            // pinta título
            guiGraphics.drawString(this.minecraft.font, this.title, (x + this.x + 32), (y + this.y + 9), -1, true);
            if (skillProgressString != null) {
                guiGraphics.drawString(this.minecraft.font, skillProgressString, (x + this.x + this.width - i - 5),
                        (y + this.y + 9), -1, true);
            }
        }

        if (flag1) {
            for (int k1 = 0; k1 < this.description.size(); ++k1) {
                // Pinta contenido
                guiGraphics.drawString(this.minecraft.font, this.description.get(k1), (i1 + 5),
                        (l + FRAME_SIZE - j1 + 7 + k1 * 9), -5592406, false);
            }

        } else {
            for (int l1 = 0; l1 < this.description.size(); ++l1) {
                // Pinta contenido
                guiGraphics.drawString(this.minecraft.font, this.description.get(l1), (i1 + 5),
                        (y + this.y + 9 + 17 + l1 * 9), -5592406, false);
            }
        }

        SkillState skillState = getSkillState();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, WIDGETS);
        // Pinta el ícono del botón
        guiGraphics.blit(WIDGETS, x + this.x + 3, y + this.y, this.getDisplayInfo().getFrame().getIcon(),
                128 + skillState.getId() * FRAME_SIZE, FRAME_SIZE, FRAME_SIZE);

        drawButton(guiGraphics, x, y, false, this.getDisplayInfo().getImage(), false, isDisabled());

        // Pinta el icono del marco
        // this.minecraft.getItemRenderer().renderItemAndEffectIntoGuiWithoutEntity(this.displayInfo.getIcon(),x
        // + this.x + 8, y + this.y + 5);
    }

    protected void render9Sprite(GuiGraphics guiGraphics, int p_97289_, int p_97290_, int p_97291_, int p_97292_, int p_97293_, int p_97294_, int p_97295_, int p_97296_, int p_97297_) {
        guiGraphics.blit(WIDGETS, p_97289_, p_97290_, p_97296_, p_97297_, p_97293_, p_97293_);
        this.renderRepeating(guiGraphics, p_97289_ + p_97293_, p_97290_, p_97291_ - p_97293_ - p_97293_, p_97293_, p_97296_ + p_97293_, p_97297_, p_97294_ - p_97293_ - p_97293_, p_97295_);
        guiGraphics.blit(WIDGETS, p_97289_ + p_97291_ - p_97293_, p_97290_, p_97296_ + p_97294_ - p_97293_, p_97297_, p_97293_, p_97293_);
        guiGraphics.blit(WIDGETS, p_97289_, p_97290_ + p_97292_ - p_97293_, p_97296_, p_97297_ + p_97295_ - p_97293_, p_97293_, p_97293_);
        this.renderRepeating(guiGraphics, p_97289_ + p_97293_, p_97290_ + p_97292_ - p_97293_, p_97291_ - p_97293_ - p_97293_, p_97293_, p_97296_ + p_97293_, p_97297_ + p_97295_ - p_97293_, p_97294_ - p_97293_ - p_97293_, p_97295_);
        guiGraphics.blit(WIDGETS, p_97289_ + p_97291_ - p_97293_, p_97290_ + p_97292_ - p_97293_, p_97296_ + p_97294_ - p_97293_, p_97297_ + p_97295_ - p_97293_, p_97293_, p_97293_);
        this.renderRepeating(guiGraphics, p_97289_, p_97290_ + p_97293_, p_97293_, p_97292_ - p_97293_ - p_97293_, p_97296_, p_97297_ + p_97293_, p_97294_, p_97295_ - p_97293_ - p_97293_);
        this.renderRepeating(guiGraphics, p_97289_ + p_97293_, p_97290_ + p_97293_, p_97291_ - p_97293_ - p_97293_, p_97292_ - p_97293_ - p_97293_, p_97296_ + p_97293_, p_97297_ + p_97293_, p_97294_ - p_97293_ - p_97293_, p_97295_ - p_97293_ - p_97293_);
        this.renderRepeating(guiGraphics, p_97289_ + p_97291_ - p_97293_, p_97290_ + p_97293_, p_97293_, p_97292_ - p_97293_ - p_97293_, p_97296_ + p_97294_ - p_97293_, p_97297_ + p_97293_, p_97294_, p_97295_ - p_97293_ - p_97293_);
    }

    protected void renderRepeating(GuiGraphics guiGraphics, int p_97279_, int p_97280_, int p_97281_, int p_97282_, int p_97283_, int p_97284_, int p_97285_, int p_97286_) {
        for (int i = 0; i < p_97281_; i += p_97285_) {
            int j = p_97279_ + i;
            int k = Math.min(p_97285_, p_97281_ - i);

            for (int l = 0; l < p_97282_; l += p_97286_) {
                int i1 = p_97280_ + l;
                int j1 = Math.min(p_97286_, p_97282_ - l);
                guiGraphics.blit(WIDGETS, j, i1, p_97283_, p_97284_, k, j1);
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
    public void drawButton(GuiGraphics guiGraphics, int x, int y, boolean mostrarPuntos, ResourceLocation image,
                           boolean superpuesto, boolean disabled) {

        PoseStack matrixStack = guiGraphics.pose();

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

        forceNearestFilter(image);
        //this.minecraft.getTextureManager().bind(image);
        matrixStack.translate((posX), (posY), 0);
        matrixStack.scale(xScale, yScale, 0);
        matrixStack.translate(posX * -1.0f, posY * -1.0f, 0);
        ScreenUtils.blitWithBorder(guiGraphics, (int) (posX + BUTTON_IMAGE_SIZE * 0.302734375),
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



    // Caché para almacenar la forma y variaciones fijas de cada conexión
    private static final Map<String, int[]> branchVariationCache = new HashMap<>();

    public void drawConnectionLineToParent(GuiGraphics guiGraphics, int x, int y, boolean dropShadow) {
        drawConnectionLineToParent(guiGraphics, x, y, dropShadow, 0);
    }

    public void drawConnectionLineToParent(GuiGraphics guiGraphics, int x, int y, boolean dropShadow, int depth) {
        if (this.parent != null) {
            int startX = x + this.parent.x + FRAME_SIZE / 2;
            int startY = y + this.parent.y + FRAME_SIZE / 2;
            int endX = x + this.x + FRAME_SIZE / 2;
            int endY = y + this.y + FRAME_SIZE / 2;

            drawCurvedLine(guiGraphics, startX, startY, endX, endY, dropShadow, depth);
        }

        for (SkillWidget skillWidget : this.children) {
            skillWidget.drawConnectionLineToParent(guiGraphics, x, y, dropShadow, depth + 1);
        }
    }

    private void drawCurvedLine(GuiGraphics guiGraphics, int startX, int startY, int endX, int endY, boolean dropShadow, int depth) {
        String cacheKey = this.parent.hashCode() + "->" + this.hashCode();

        // Variación almacenada para cada rama
        int[] variation = branchVariationCache.computeIfAbsent(cacheKey, k -> {
            int varX = (int) (Math.random() * 6 - 3);
            int varY = (int) (Math.random() * 6 - 3);
            return new int[]{varX, varY};
        });

        int midX = (startX + endX) / 2 + variation[0];
        int midY = (startY + endY) / 2 + variation[1];

        int thickness = Math.max(5 - depth, 2); // Ajustado para mejor rendimiento

        drawSmoothCurvedLine(guiGraphics, startX, startY, midX, midY, endX, endY, thickness);
    }

    private void drawSmoothCurvedLine(GuiGraphics guiGraphics, int x1, int y1, int midX, int midY, int x2, int y2, int thickness) {
        int colorDark = 0xFF5C3317; // Marrón oscuro
        int colorLight = 0xFFD2B48C; // Marrón claro

        // Generar la curvatura de la rama con gradiente vertical
        for (int i = -thickness / 2; i <= thickness / 2; i++) {
            double ratio = (i + thickness / 2) / (double) thickness; // Controla el gradiente vertical
            int color = interpolateColor(colorDark, colorLight, ratio);

            drawGradientBezier(guiGraphics, x1, y1 + i, midX, midY + i, x2, y2 + i, color);
        }
    }

    private void drawGradientBezier(GuiGraphics guiGraphics, int x1, int y1, int midX, int midY, int x2, int y2, int color) {
        int prevX = x1, prevY = y1;

        for (double t = 0; t <= 1; t += 0.02) {
            int x = (int) ((1 - t) * (1 - t) * x1 + 2 * (1 - t) * t * midX + t * t * x2);
            int y = (int) ((1 - t) * (1 - t) * y1 + 2 * (1 - t) * t * midY + t * t * y2);

            drawThickSegment(guiGraphics, prevX, prevY, x, y, color);
            prevX = x;
            prevY = y;
        }
    }

    // Método para interpolar colores con un gradiente vertical
    private int interpolateColor(int startColor, int endColor, double ratio) {
        int r1 = (startColor >> 16) & 0xFF;
        int g1 = (startColor >> 8) & 0xFF;
        int b1 = startColor & 0xFF;

        int r2 = (endColor >> 16) & 0xFF;
        int g2 = (endColor >> 8) & 0xFF;
        int b2 = endColor & 0xFF;

        int r = (int) (r1 + (r2 - r1) * ratio);
        int g = (int) (g1 + (g2 - g1) * ratio);
        int b = (int) (b1 + (b2 - b1) * ratio);

        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }

    private void drawThickSegment(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;

        while (true) {
            guiGraphics.fill(x1, y1, x1 + 1, y1 + 1, color);
            if (x1 == x2 && y1 == y2) break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x1 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y1 += sy;
            }
        }
    }

}
