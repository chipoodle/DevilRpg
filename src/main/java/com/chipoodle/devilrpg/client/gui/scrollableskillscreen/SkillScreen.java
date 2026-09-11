package com.chipoodle.devilrpg.client.gui.scrollableskillscreen;

import net.neoforged.neoforge.network.PacketDistributor;
import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.experience.PlayerExperienceCapability;
import com.chipoodle.devilrpg.capability.experience.PlayerExperienceCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.client.gui.scrollableskillscreen.model.ClientSkillBuilderFromJson;
import com.chipoodle.devilrpg.client.gui.scrollableskillscreen.model.CustomSkillButton;
import com.chipoodle.devilrpg.eventsubscriber.client.ClientModKeyInputEventSubscriber;
import com.chipoodle.devilrpg.network.payload.PlayerPassiveSkillPayload;
import com.chipoodle.devilrpg.util.PowerEnum;
import com.chipoodle.devilrpg.util.SkillEnum;
import com.google.common.collect.Maps;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.text.DecimalFormat;
import java.util.*;

@OnlyIn(Dist.CLIENT)
public class SkillScreen extends Screen implements ClientSkillBuilderFromJson.IListener {
    // ============================================================================================
    // SISTEMA DE COORDENADAS DE LA VENTANA
    // --------------------------------------------------------------------------------------------
    // La pantalla de skills está formada por:
    //   1) La VENTANA PRINCIPAL (el marco dibujado por renderWindow): es la imagen window-256b.png,
    //      de tamaño nativo INITIAL_TEXTURE_WIDTH x INITIAL_TEXTURE_HEIGHT, que se escala (ps.scale)
    //      a un tamaño de diseño de WINDOW_WIDTH x (WINDOW_HEIGHT + INFO_SPACE).
    //   2) La VENTANA INTERIOR (donde se ve el árbol de habilidades sobre el mosaico): es el área
    //      rectangular que empieza en (TAB_BACKGROUND_WINDOW_AREA_OFFSET_X, _Y) dentro de la ventana
    //      y mide INNER_SCREEN_WIDTH x INNER_SCREEN_HEIGHT. Aquí se dibuja el fondo (mosaico) y los
    //      skills.
    // Todas estas constantes están en "unidades de diseño de la ventana" (window-local). En tiempo de
    // render se transforman a píxeles de pantalla multiplicando por fitScale y sumando offsetLeft/offsetTop.
    // ============================================================================================

    // ---- Textura base / ventana principal ----
    /** Ancho nativo (px) del marco de la ventana (window-256b.png). Se usa para escalarlo a la ventana. */
    private static final int INITIAL_TEXTURE_WIDTH = 1317;
    /** Alto nativo (px) del marco de la ventana (window-256b.png). */
    private static final int INITIAL_TEXTURE_HEIGHT = 1194;
    /** Ancho de la ventana en unidades de diseño (destino del escalado de INITIAL_TEXTURE_WIDTH). */
    private static final int WINDOW_WIDTH = 304;
    /** Alto de la ventana en unidades de diseño (destino del escalado de INITIAL_TEXTURE_HEIGHT). */
    private static final int WINDOW_HEIGHT = 248;
    /**
     * Espacio extra reservado al pie de la ventana (para los botones de poder/slots asignados).
     * El alto total de la ventana = WINDOW_HEIGHT + INFO_SPACE.
     */
    private static final int INFO_SPACE = 26;
    /** Prefijo de ruta de todas las texturas de la GUI (textures/gui/). */
    private static final String IMG_LOCATION = DevilRpg.MODID + ":textures/gui/";
    /** Textura del marco de la ventana principal. */
    private static final ResourceLocation WINDOW_LOCATION = ResourceLocation.parse(IMG_LOCATION + "window-256b.png");
    /** Textura de las pestañas (tabs) superiores de categorías. */
    private static final ResourceLocation TABS_LOCATION = ResourceLocation.parse(IMG_LOCATION + "advancements/tabs.png");
    /** Textura del "hueco" (empty-box) que ocupa un slot de poder sin skill asignada. */
    private static final ResourceLocation EMPTY_POWER_IMAGE_RESOURCE = ResourceLocation.parse(IMG_LOCATION + "empty-box.png");

    // ---- Ventana interior (área del árbol de habilidades / mosaico) ----
    /** Ancho del área interior donde se dibuja el árbol (y su mosaico de fondo), en unidades de diseño. */
    private static final int INNER_SCREEN_WIDTH = 282;
    /** Alto del área interior donde se dibuja el árbol (y su mosaico de fondo). */
    private static final int INNER_SCREEN_HEIGHT = 160;
    /** Desplazamiento X del origen del área interior dentro de la ventana principal. */
    private static final int TAB_BACKGROUND_WINDOW_AREA_OFFSET_X = 22;
    /** Desplazamiento Y del origen del área interior dentro de la ventana principal. */
    private static final int TAB_BACKGROUND_WINDOW_AREA_OFFSET_Y = 38;

    // ---- Textos (labels) traducibles de la ventana ----
    /** Texto que se muestra cuando una categoría está vacía (no hay skills). */
    private static final Component SAD_LABEL = Component.translatable("advancements.sad_label");
    /** Texto "vacío" que se pinta dentro del área del árbol cuando no hay nada que mostrar. */
    private static final Component EMPTY = Component.translatable("advancements.empty");
    /** Título de la pantalla (gui.skills.title). */
    private static final Component GUI_LABEL = Component.translatable("gui.skills.title");
    /** Etiqueta de "puntos sin gastar" (gui.skills.unspent). */
    private static final Component UNSPENT_LABEL = Component.translatable("gui.skills.unspent");

    // ---- Botones de poder (slots de skill asignados) ----
    /** X (unidades de diseño) donde empieza la fila de botones de poder. */
    public static final int POWER_INITIAL_X_POSITION = 141;
    /** Separación horizontal entre botones de poder (se suma a SkillWidget.FRAME_SIZE). Más alto = más separados. */
    public static final int POWER_BUTTON_GAP = 5;
    /** Desplazamiento vertical de los botones desde su base. Más alto = más abajo. */
    public static final int POWER_BUTTON_Y_OFFSET = 21;
    /** Tamaño (alto y ancho) del botón = SkillWidget.FRAME_SIZE - este valor. Más alto = botón más pequeño. */
    public static final int POWER_BUTTON_SHRINK = 6;
    /** X del texto "skills" (label) junto a la fila de botones de poder. */
    public static final int SKILL_LABEL_X = 72;
    /** Y del texto "skills" (label). */
    public static final int SKILL_LABEL_Y = 19;
    /** X del label de "puntos sin gastar". */
    public static final int UNSPENT_POINTS_LABEL_X = 46;
    /** Y del label de "puntos sin gastar". */
    public static final int UNSPENT_POINTS_LABEL_Y = 230;

    // --- Botones de rotar conjunto de skills (loadouts) ---
    /** Distancia desde el borde derecho del marco a la X del botón ◀ (bájala = más a la derecha). */
    public static final int SKILL_SET_BUTTON_RIGHT_MARGIN = 63;
    /** Separación horizontal entre el botón ◀ y el ▶. */
    public static final int SKILL_SET_BUTTON_SPACING = 25;
    /** Desplazamiento vertical bajo la ventana (sube/baja los botones). */
    public static final int SKILL_SET_BUTTON_Y_OFFSET = -6;

    // --- Etiqueta del conjunto de skills activo (Set X/Y) ---
    /** Separación horizontal a la izquierda del botón ◀ (bájala para acercar el label). */
    public static final int SKILL_SET_LABEL_X_OFFSET = 19;
    /** Posición vertical del label desde la base de la ventana (bájala = más arriba). */
    public static final int SKILL_SET_LABEL_Y_OFFSET = -1;
    private static int tabPage, maxPages;
    private final ClientSkillBuilderFromJson clientSkillManager;
    private final Map<SkillElement, SkillTab> tabs = Maps.newLinkedHashMap();
    private final Player player;
    private final EnumMap<SkillEnum, ResourceLocation> skillsImages;
    private SkillTab selectedTab;
    private boolean isScrolling;
    private boolean isDraggingToPowerButton;
    /** X (px de pantalla) del borde izquierdo de la ventana principal (= borde de la ventana de diseño). */
    private int offsetLeft;
    /** Y (px de pantalla) del borde superior de la ventana principal. */
    private int offsetTop;
    /** Escala para que la ventana de diseño (WINDOW_WIDTH x WINDOW_HEIGHT+INFO_SPACE) quepa en la pantalla virtual con cualquier guiScale. */
    private float fitScale = 1.0F;
    /** Evita que super.render vuelva a dibujar el fondo encima de la ventana ya renderizada */
    private boolean skipBackgroundRenderOnce = false;

    /** Convierte la coord X de pantalla a coord local de la ventana (dividiendo por fitScale). */
    private double localX(double screenX) {
        return (screenX - offsetLeft) / this.fitScale;
    }

    /** Convierte la coord Y de pantalla a coord local de la ventana (dividiendo por fitScale). */
    private double localY(double screenY) {
        return (screenY - offsetTop) / this.fitScale;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.skipBackgroundRenderOnce) {
            super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        }
    }
    private InputConstants.Key openScreenKeyPressed;
    private SkillWidget draggedSkillWidget;
    private double posicionMouseX;
    private double posicionMouseY;
    private double dragPositionMouseX;
    private double dragPositionMouseY;
    private PlayerSkillCapabilityInterface skillCap;
    private PlayerExperienceCapabilityInterface expCap;
    private Set<CustomSkillButton> powerButtonList;
    /** Y donde se pinta el nombre del fondo actual (debajo de los botones de selección de fondo). */
    private int backgroundButtonY;
    /** Y donde se pinta el nombre del skin de widget actual (debajo de sus botones). */
    private int themeButtonY;

    private SkillScreen() {
        super(GameNarrator.NO_TITLE);
        isDraggingToPowerButton = false;
        draggedSkillWidget = null;
        skillsImages = new EnumMap<>(SkillEnum.class);
        Minecraft instance = Minecraft.getInstance();
        this.player = instance.player;
        expCap = Objects.requireNonNull(player).getData(PlayerExperienceCapability.INSTANCE);
        skillCap = player.getData(PlayerSkillCapability.INSTANCE);
        this.clientSkillManager = skillCap == null ? null : skillCap.getClientSkillBuilder();
    }

    public SkillScreen(InputConstants.Key input) {
        this();
        openScreenKeyPressed = input;
        powerButtonList = new LinkedHashSet<>();
    }

    @Override
    protected void init() {
        this.fitScale = Math.min(1.0F,
                Math.min((float) this.width / WINDOW_WIDTH, (float) this.height / WINDOW_HEIGHT));
        offsetLeft = (this.width - (int) (WINDOW_WIDTH * this.fitScale)) / 2;
        offsetTop = (this.height - (int) (WINDOW_HEIGHT * this.fitScale)) / 2;

        this.tabs.clear();
        this.selectedTab = null;
        this.clientSkillManager.setListener(this);

        if (this.selectedTab == null && !this.tabs.isEmpty()) {
            this.clientSkillManager.setSelectedTab(this.tabs.values().iterator().next().getSkillElement(), true);
        } else {
            this.clientSkillManager.setSelectedTab(this.selectedTab == null ? null : this.selectedTab.getSkillElement(),
                    true);
        }
        if (this.tabs.size() > SkillTabType.MAX_TABS) {
            int guiLeft = (this.width - WINDOW_WIDTH) / 2;
            int guiTop = (this.height - WINDOW_HEIGHT) / 2;
            // pinta boton <
            addRenderableWidget(Button.builder(Component.literal("<"), b -> tabPage = Math.max(tabPage - 1, 0))
                    .pos(guiLeft, guiTop - 50).size(20, 20).build());
            // pinta boton >
            addRenderableWidget(Button.builder(Component.literal(">"), b -> tabPage = Math.min(tabPage + 1, maxPages))
                    .pos(guiLeft + WINDOW_WIDTH - 20, guiTop - 50).size(20, 20).build());
        }

        //////////////////////////////////////////
        // Skin del arbol de habilidades: se fija el por defecto (forest_92_raw) y se desactivan
        // los botones < > de cambio de tema.
        SkillWidget.applyDefaultTheme();

        maxPages = this.tabs.size() / SkillTabType.MAX_TABS;
        ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        addPowerButtons();
        loadAssignedPowerButtons();

        // Botones para rotar entre conjuntos de skills asignados (loadouts).
        addSkillSetButtons();

        // Botones ◀ ▶ del FONDO: OCULTOS a propósito (el fondo ya está elegido y fijo, ver
        // SkillBackgroundManager.DEFAULT_BACKGROUND). Para recuperar el selector, descomenta esta llamada y la
        // de renderBackgroundName() en render(), y vuelve a activar applySavedSelection() en el manager.
        // addBackgroundButtons();

        // Botones ◀ ▶ (y "Def") del skin de widget: OCULTOS a propósito, ya está elegido (ver el valor por
        // defecto en SkillWidget.applyDefaultTheme() y la config de cliente). Para recuperar el selector,
        // descomenta esta llamada y la de renderWidgetName() en render().
        // addThemeButtons();
    }

    /**
     * Botones ◀ ▶ para rotar el conjunto de skills activo (loadout). Son transparentes (sin caja
     * gris): dibujan solo la flecha en color madera para que coincidan con la flecha pintada en el
     * marco (que queda debajo), y funcionan como zona de clic encima de ella.
     */
    private void addSkillSetButtons() {
        int y = (int) (offsetTop + WINDOW_HEIGHT * this.fitScale) + SKILL_SET_BUTTON_Y_OFFSET;
        int leftX = (int) (offsetLeft + (WINDOW_WIDTH - SKILL_SET_BUTTON_RIGHT_MARGIN) * this.fitScale);
        int rightX = (int) (offsetLeft + (WINDOW_WIDTH - SKILL_SET_BUTTON_RIGHT_MARGIN + SKILL_SET_BUTTON_SPACING) * this.fitScale);
        addRenderableWidget(new FrameArrowButton(leftX, y, SKILL_SET_BUTTON_SPACING, SKILL_SET_BUTTON_SPACING, "◀", () -> switchSkillSet(-1)));
        addRenderableWidget(new FrameArrowButton(rightX, y, SKILL_SET_BUTTON_SPACING, SKILL_SET_BUTTON_SPACING, "▶", () -> switchSkillSet(1)));
    }

    private void switchSkillSet(int delta) {
        if (skillCap != null) {
            skillCap.rotateSkillSet(delta, player);
            loadAssignedPowerButtons();
        }
    }

    /**
     * Botones ◀ ▶ para recorrer los fondos del árbol de habilidades: ◀ retrocede, ▶ avanza (dan la vuelta al
     * llegar al final) y debajo se muestra el nombre del fondo con su número ("mandala-ag.png  3/39").
     * <p>
     * Están a propósito (se quitaron una vez y se echaron de menos): son la forma de elegir el fondo sin tocar
     * código y de previsualizar fondos nuevos.
     */
    private void addBackgroundButtons() {
        int midX = this.width / 2;
        int by = this.height - 44;
        this.backgroundButtonY = by + 24;
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> SkillBackgroundManager.prev())
                .pos(midX - 34, by).size(20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> SkillBackgroundManager.next())
                .pos(midX + 14, by).size(20, 20).build());
    }

    /**
     * Boton de flecha sin fondo visible: solo pinta la flecha en tono madera (similar a la del marco).
     * Sirve como zona de clic sobre la flecha dibujada en la imagen de fondo.
     */
    private static class FrameArrowButton extends AbstractButton {
        private final Runnable onClickAction;

        FrameArrowButton(int x, int y, int w, int h, String arrow, Runnable onClick) {
            super(x, y, w, h, Component.literal(arrow));
            this.onClickAction = onClick;
        }

        @Override
        public void onPress() {
            onClickAction.run();
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            // No dibujar la caja gris; solo la flecha en color madera (se ve la del marco debajo).
            int color = this.isHovered() ? 0xFFE0A05A : 0xFF9C6B3A;
            guiGraphics.drawCenteredString(Minecraft.getInstance().font, this.getMessage(),
                    this.getX() + this.getWidth() / 2, this.getY() + (this.getHeight() - 8) / 2, color);

            //super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput narrationOutput) {
        }
    }

    /**
     * Botones para recorrer los <b>skins de widget</b> de los nodos (marcos y barras), en la misma fila que los
     * del fondo pero a la derecha: ◀ ▶ cambian de skin al instante y "Def" vuelve al skin por defecto.
     * <p>
     * Sirven para buscar la mejor combinación fondo + widget sin tocar código. La elección se mantiene al
     * cerrar y reabrir la pantalla (ver {@code SkillWidget.userChoseTheme}), pero no sobrevive a reiniciar el
     * juego.
     */
    private void addThemeButtons() {
        int midX = this.width / 2;
        int by = this.height - 44;
        this.themeButtonY = by + 34;
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> SkillWidget.changeWidgetTheme(false))
                .pos(midX + 96, by).size(20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> SkillWidget.changeWidgetTheme(true))
                .pos(midX + 120, by).size(20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Def"), b -> SkillWidget.resetWidgetTheme())
                .pos(midX + 144, by).size(26, 20).build());
    }

    @Override
    public void onClose() {
        this.clientSkillManager.setListener(null);
        super.onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        InputConstants.Key pressedKeyCode = InputConstants.Type.KEYSYM.getOrCreate(keyCode);
        if (openScreenKeyPressed.getName().equals(pressedKeyCode.getName())) {
            this.onClose();
            return true;
        } else
            return super.keyPressed(pressedKeyCode.getValue(), scanCode, modifiers);
    }

    @Override
    /**
     * Punto de entrada del render de la pantalla. Orden:
     * 1) Fondo de la GUI (renderBackground, una sola vez).
     * 2) Indicador de página (X / Y) si hay varias pestañas.
     * 3) Traslada el pose por (offsetLeft, offsetTop) y lo escala por fitScale (unidades de diseño -> px).
     * 4) renderInside (ventana interior: árbol+mosaico) y renderWindow (marco + pestañas + título + info).
     * 5) super.render para dibujar los botones renderables (encima de la ventana).
     * 6) Indicadores superpuestos (skill arrastrado, "Set X/Y", nombre del fondo).
     */
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Fondo UNA sola vez, antes de la ventana (no se repite encima de esta)
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        // fitScale/offsetLeft/offsetTop se calculan en init() (no cambian entre frames; en un resize
        // MC vuelve a llamar a init()). Aqui solo se usan, no se recalculan por frame.
        if (maxPages != 0) {
            Component page = Component.literal(String.format("%d / %d", tabPage + 1, maxPages + 1));
            int width = this.font.width(page);
            //RenderSystem.disableLighting();
            guiGraphics.drawString(this.font, page.getVisualOrderText(),
                    (int) (offsetLeft + ((float) (WINDOW_WIDTH * this.fitScale) / 2) - ((float) width / 2)),
                    offsetTop - 44, -1, true);
        }
        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(offsetLeft, offsetTop, 0.0F);
        pose.scale(this.fitScale, this.fitScale, 1.0F);
        // A partir de aqui se dibuja en coordenadas locales de la ventana (0..WINDOW_WIDTH)
        this.renderInside(guiGraphics, mouseX, mouseY);
        this.renderWindow(guiGraphics);
        this.renderTooltips(guiGraphics, mouseX, mouseY);
        pose.popPose();
        // Botones ENCIMA de la ventana: super.render dibuja los renderables, pero con
        // skipBackgroundRenderOnce desactivamos su renderBackground para no velar la ventana.
        this.skipBackgroundRenderOnce = true;
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.skipBackgroundRenderOnce = false;
        this.renderSkillButtonPressed(guiGraphics);
        this.renderSkillSetIndicator(guiGraphics);
        // this.renderBackgroundName(guiGraphics); // etiqueta del fondo: oculta junto con sus botones
        // this.renderWidgetName(guiGraphics); // etiqueta del skin de widget: oculta junto con sus botones
    }

    /** Muestra bajo los botones el fondo actual ("nombre  3/39") para saber cuál está puesto. */
    private void renderBackgroundName(GuiGraphics guiGraphics) {
        String label = SkillBackgroundManager.getSelectedName() + "  "
                + (SkillBackgroundManager.getSelectedIndex() + 1) + "/" + SkillBackgroundManager.getBackgroundCount();
        guiGraphics.drawCenteredString(this.font, label, this.width / 2, this.backgroundButtonY, 0xFFCC66);
    }

    /** Muestra bajo sus botones el skin de widget actual ("forest_92_raw.png  12/21"). */
    private void renderWidgetName(GuiGraphics guiGraphics) {
        String label = SkillWidget.getWidgetShortName() + "  "
                + (SkillWidget.getWidgetIndex() + 1) + "/" + SkillWidget.getWidgetCount();
        guiGraphics.drawCenteredString(this.font, label, this.width / 2 + 133, this.themeButtonY, 0xFFCC66);
    }

    /** Muestra el conjunto de skills activo ("Set X/Y", traducible) junto a los botones ◀ ▶. */
    private void renderSkillSetIndicator(GuiGraphics guiGraphics) {
        if (skillCap == null) {
            return;
        }
        int current = skillCap.getActiveSkillSetIndex() + 1;
        int total = skillCap.getSkillSetCount();
        Component label = Component.translatable("gui.skills.skill_set", current, total);
        int textWidth = this.font.width(label);
        int x = (int) (offsetLeft + (WINDOW_WIDTH - SKILL_SET_BUTTON_RIGHT_MARGIN) * this.fitScale);
        int y = (int) (offsetTop + WINDOW_HEIGHT * this.fitScale) + SKILL_SET_LABEL_Y_OFFSET;
        guiGraphics.drawString(this.font, label, x - textWidth - SKILL_SET_LABEL_X_OFFSET, y, 0xFFFFAA, true);
    }

    private void renderSkillButtonPressed(GuiGraphics guiGraphics) {
        if (draggedSkillWidget != null) {

            PoseStack poseStack = guiGraphics.pose();
            poseStack.pushPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setShader(GameRenderer::getPositionTexShader);

            // Pinta la imagen del botón
            draggedSkillWidget.drawButton(guiGraphics, (int) posicionMouseX, (int) posicionMouseY, false,
                    draggedSkillWidget.getDisplayInfo().getImage(), true, draggedSkillWidget.isDisabled());

            poseStack.popPose();
        }
    }

    public void skillButtonPressed(SkillWidget skillEntryGui) {
        SkillEnum skillEnum = skillEntryGui.getSkillElement().getSkillCapability();
        DevilRpg.LOGGER.debug("|----------- skillButtonPressed: {}", skillEnum);
        if (!skillEnum.equals(SkillEnum.EMPTY)) {
            if (skillCap != null) {
                HashMap<SkillEnum, Integer> skillsPoints = skillCap.getSkillsPoints();
                HashMap<SkillEnum, Integer> skillsMaxPoints = skillCap.getMaxSkillsPoints();
                Integer points = skillsPoints.get(skillEnum);
                Integer maxPoints = skillsMaxPoints.get(skillEnum);
                if (points < maxPoints) {
                    points += expCap == null ? 0 : expCap.consumePoint();
                    skillsPoints.put(skillEnum, points);
                    skillCap.setSkillsPoints(skillsPoints, player);
                    skillEntryGui.updateFormattedLevelString(points, maxPoints);


                    if (skillEnum.isPassive() && !skillEnum.isForMinion()) {
                        //Para pasivos
                        CompoundTag compoundTag = skillCap.setSkillToByteArray(skillEnum);
                        PacketDistributor.sendToServer(new PlayerPassiveSkillPayload(compoundTag));
                    }
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == ButtonMouse.LEFT_BUTTON) {
            double lmX = localX(mouseX);
            double lmY = localY(mouseY);
            for (SkillTab rootSkillTabGui : this.tabs.values()) {
                if (rootSkillTabGui.getPage() == tabPage) {
                    if (rootSkillTabGui.isInsideTabSelector(0, 0, lmX, lmY)) {
                        this.clientSkillManager.setSelectedTab(rootSkillTabGui.getSkillElement(), true);
                        break;
                    } else {
                        SkillWidget skillEntryGui = selectedTab.getIfInsideIncludingChildren(
                                lmX - TAB_BACKGROUND_WINDOW_AREA_OFFSET_X, lmY - TAB_BACKGROUND_WINDOW_AREA_OFFSET_Y);
                        if (skillEntryGui != null && skillEntryGui.getSkillElement().getParent() != null && !skillEntryGui.isDisabled()) {
                            DevilRpg.LOGGER.info("|----------- mouseClicked: {}", skillEntryGui.getSkillElement().getSkillCapability());
                            this.playDownSound(Minecraft.getInstance().getSoundManager());
                            skillButtonPressed(skillEntryGui);
                            break;
                        }
                    }
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Se dispara cuando se está haciendro drag con el mouse
     */
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == ButtonMouse.RIGHT_BUTTON) {
            this.isScrolling = false;

            if (!isDraggingToPowerButton) {
                double lmX = localX(mouseX);
                double lmY = localY(mouseY);
                draggedSkillWidget = selectedTab.getIfInsideIncludingChildren(
                        lmX - TAB_BACKGROUND_WINDOW_AREA_OFFSET_X, lmY - TAB_BACKGROUND_WINDOW_AREA_OFFSET_Y);
                if (draggedSkillWidget != null && (draggedSkillWidget.isDisabled() || !draggedSkillWidget.getSkillProgress().hasProgress())) {
                    draggedSkillWidget = null;
                }
            }

            if (draggedSkillWidget != null
                    && draggedSkillWidget.getSkillElement()  != null
                    && draggedSkillWidget.getSkillElement().getDisplay() != null
                    && draggedSkillWidget.getSkillElement().getDisplay().getFrame() != null
                    && draggedSkillWidget.getSkillElement().getDisplay().getFrame().equals(SkillFrameType.TASK)
                    && !draggedSkillWidget.isDisabled()) {
                isDraggingToPowerButton = true;
                posicionMouseX = mouseX - draggedSkillWidget.getX() - SkillWidget.FRAME_SIZE / ((double) 2);
                posicionMouseY = mouseY - draggedSkillWidget.getY() - SkillWidget.FRAME_SIZE / ((double) 2);
                return true;
            } else
                draggedSkillWidget = null;
            return false;

        } else {
            if (!this.isScrolling) {
                this.isScrolling = true;
            } else if (this.selectedTab != null) {
                this.selectedTab.dragSelectedGui(dragX, dragY);
            }

            return true;
        }
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int state) {
        boolean returned = super.mouseReleased(mouseX, mouseY, state);
        if (state == ButtonMouse.RIGHT_BUTTON && draggedSkillWidget != null && draggedSkillWidget.getSkillElement().getParent() != null) { // botón derecho
            DevilRpg.LOGGER.info("|----------- rightMouseReleases: {}, mousex: {}, mousey:{}", draggedSkillWidget.getSkillElement().getSkillCapability().getName(), mouseX, mouseY);

            CustomSkillButton copy = powerButtonList.stream().filter(x -> x.isInside(mouseX, mouseY)).findAny().orElse(null);
            if (copy != null) {
                copy.setButtonTexture(draggedSkillWidget.getDisplayInfo().getImage());
                HashMap<PowerEnum, SkillEnum> powerNames = skillCap == null ? null : skillCap.getSkillsNameOfPowers();
                if (powerNames != null) {
                    powerNames.put((PowerEnum) copy.getEnum(), draggedSkillWidget.getSkillElement().getSkillCapability());
                    if (skillCap != null) skillCap.setSkillsNameOfPowers(powerNames, player);
                    addPowerButtons();
                    loadAssignedPowerButtons();
                }
            }
            isDraggingToPowerButton = false;
            draggedSkillWidget = null;
        }
        return returned;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        // super.mouseMoved(mouseX, mouseY);
    }

    @SuppressWarnings("deprecation")
    /**
     * Pinta la VENTANA PRINCIPAL de la pantalla de skills: el marco (window-256b.png) escalado al
     * tamaño de diseño (WINDOW_WIDTH x WINDOW_HEIGHT+INFO_SPACE) y, encima, las pestañas (tabs), su
     * icono, el título "Skills" y la info de nivel/puntos sin usar. Todo en coordenadas locales de la
     * ventana (el pose ya fue trasladado/escalado por {@link #render}).
     */
    public void renderWindow(GuiGraphics guiGraphics) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        // Pinta la pantalla exterior (coordenadas locales de la ventana; el pose ya esta
        // trasladado/escalado por render()). Se tiñe de marron acorde a las pestañas.
        SkillWidget.forceNearestFilter(WINDOW_LOCATION);
        RenderSystem.setShaderColor(0.85F, 0.72F, 0.52F, 1.0F);
        // Dibuja el marco completo (1419x1108) escalado al tamano de la ventana (302x236)
        // para usar su alta resolucion sin tocar las posiciones del layout.
        PoseStack ps = guiGraphics.pose();
        ps.pushPose();
        ps.scale((float) WINDOW_WIDTH / INITIAL_TEXTURE_WIDTH,
                (float) (WINDOW_HEIGHT + INFO_SPACE) / INITIAL_TEXTURE_HEIGHT, 1.0F);
        guiGraphics.blit(WINDOW_LOCATION, 0, 0, 0, 0, INITIAL_TEXTURE_WIDTH, INITIAL_TEXTURE_HEIGHT, INITIAL_TEXTURE_WIDTH, INITIAL_TEXTURE_HEIGHT);
        ps.popPose();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        if (this.tabs.size() > 1) {
            SkillWidget.forceNearestFilter(TABS_LOCATION);

            // Pinta todas las pestañas, tanto la seleccionada como las no seleccionadas
            for (SkillTab skillTabGui : this.tabs.values()) {
                if (skillTabGui.getPage() == tabPage)
                    skillTabGui.drawTab(guiGraphics, 0, 0, skillTabGui == this.selectedTab);
            }

            RenderSystem.defaultBlendFunc();
            // Reset del tinte de las pestañas para no afectar a los iconos
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            // Pinta el ícono o la imagen de la pestaña (tab)
            int k = 0;
            for (SkillTab skillTab : this.tabs.values()) {
                if (skillTab.getPage() == tabPage) {
                    //guiSkillTab.drawIcon(offsetLeft, offsetTop, this.itemRenderer);
                    skillTab.drawIconImage(guiGraphics, 0, 0);
                }
            }
            RenderSystem.disableBlend();

            // Pinta el título sobre el cartel "Skills" del marco (blanco sobre la placa)
            guiGraphics.drawString(this.font, GUI_LABEL, SKILL_LABEL_X, SKILL_LABEL_Y, 0xFFFFFF);

            int unspentPoints = expCap == null ? -1 : expCap.getUnspentPoints();
            int currentLevel = expCap == null ? -1 : expCap.getCurrentLevel();
            // Mensaje compacto para no chocar con los botones de tema (derecha)
            Component infoHolder = Component.literal(
                    "Lv " + currentLevel + "  \u00B7  " + UNSPENT_LABEL.getString() + " " + unspentPoints);
            //Pinta nivel + puntos sin usar, con fuente mas pequeña para que quepa en la placa del marco.
            PoseStack psInfo = guiGraphics.pose();
            psInfo.pushPose();
            psInfo.translate(UNSPENT_POINTS_LABEL_X, UNSPENT_POINTS_LABEL_Y, 0);
            psInfo.scale(0.7F, 0.7F, 1.0F);
            guiGraphics.drawString(this.font, infoHolder, 0, 0, 0xFFFFFF);
            psInfo.popPose();

            //this.font.draw(poseStack, Component.literal("x:"+d.format(posicionMouseX)+" y:"+d.format(posicionMouseY)), (float) posicionMouseX,(float)posicionMouseY, 10526880);
        }

    }
    static DecimalFormat d = new DecimalFormat("#,###.#");

    /**
     * Pinta la VENTANA INTERIOR: el área donde se ve el árbol de habilidades (el rectángulo que empieza
     * en TAB_BACKGROUND_WINDOW_AREA_OFFSET_X/Y y mide INNER_SCREEN_WIDTH x INNER_SCREEN_HEIGHT). Si hay
     * una pestaña seleccionada dibuja su contenido (mosaico de fondo + árbol) con recorte (scissor) y
     * sus tooltips; si no, pinta el mensaje "vacío"/"triste".
     *
     * @param guiGraphics objeto de dibujo de la GUI
     * @param mouseX      X del cursor (px de pantalla)
     * @param mouseY      Y del cursor (px de pantalla)
     */
    @SuppressWarnings("deprecation")
    private void renderInside(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        SkillTab selectedSkillTabGui = this.selectedTab;
        // Pinta el fondo vacío cuando no hay elementos
        if (selectedSkillTabGui == null) {
            guiGraphics.fill(TAB_BACKGROUND_WINDOW_AREA_OFFSET_X, TAB_BACKGROUND_WINDOW_AREA_OFFSET_Y,
                    TAB_BACKGROUND_WINDOW_AREA_OFFSET_X + INNER_SCREEN_WIDTH,
                    TAB_BACKGROUND_WINDOW_AREA_OFFSET_Y + INNER_SCREEN_HEIGHT, -16777216);
            int i = TAB_BACKGROUND_WINDOW_AREA_OFFSET_X + 117;
            guiGraphics.drawCenteredString(this.font, EMPTY, i, TAB_BACKGROUND_WINDOW_AREA_OFFSET_Y + 56 - TAB_BACKGROUND_WINDOW_AREA_OFFSET_X / 2, -1);
            guiGraphics.drawCenteredString(this.font, SAD_LABEL, i, TAB_BACKGROUND_WINDOW_AREA_OFFSET_Y + INNER_SCREEN_HEIGHT - TAB_BACKGROUND_WINDOW_AREA_OFFSET_X, -1);
        } else {
            // Pinta el fondo con elementos: drawContents aplica su propio translate (origen
            // local de la ventana) y el scissor en coordenadas de pantalla (virtual)
            selectedSkillTabGui.drawContents(guiGraphics,
                    TAB_BACKGROUND_WINDOW_AREA_OFFSET_X, TAB_BACKGROUND_WINDOW_AREA_OFFSET_Y,
                    (int) (offsetLeft + TAB_BACKGROUND_WINDOW_AREA_OFFSET_X * this.fitScale),
                    (int) (offsetTop + TAB_BACKGROUND_WINDOW_AREA_OFFSET_Y * this.fitScale),
                    (int) (SkillTab.TAB_BACKGROUND_WIDTH * this.fitScale),
                    (int) (SkillTab.TAB_BACKGROUND_HEIGHT * this.fitScale));
            RenderSystem.depthFunc(515);
            RenderSystem.disableDepthTest();
        }
    }

    @SuppressWarnings("deprecation")
    private void renderTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        // mouse en coordenadas locales de la ventana (diseno)
        double lmX = localX(mouseX);
        double lmY = localY(mouseY);
        // pinta los tooltips de los botones
        if (this.selectedTab != null) {
            PoseStack poseStack = guiGraphics.pose();
            poseStack.pushPose();
            //RenderSystem.enableDepthTest();
            poseStack.translate(TAB_BACKGROUND_WINDOW_AREA_OFFSET_X, TAB_BACKGROUND_WINDOW_AREA_OFFSET_Y, 400.0F);
            this.selectedTab.drawTabTooltips(guiGraphics,
                    (int) lmX - TAB_BACKGROUND_WINDOW_AREA_OFFSET_X,
                    (int) lmY - TAB_BACKGROUND_WINDOW_AREA_OFFSET_Y,
                    0, 0);
            RenderSystem.disableDepthTest();
            poseStack.popPose();
        }

        // Pinta los tooltips de las pestañas
        if (this.tabs.size() > 1) {
            for (SkillTab skillTabGui : this.tabs.values()) {
                if (skillTabGui.getPage() == tabPage
                        && skillTabGui.isInsideTabSelector(0, 0, lmX, lmY)) {
                    guiGraphics.renderTooltip(this.font, skillTabGui.getTitle(), (int) lmX, (int) lmY);
                }
            }
        }

    }

    public void rootSkillAdded(SkillElement advancementIn) {
        //DevilRpg.LOGGER.info("|-------- rootSkillAdded");
        SkillTab advancementtabgui = SkillTab.create(this.minecraft, this, this.tabs.size(), advancementIn, skillCap);
        if (advancementtabgui != null) {
            this.tabs.put(advancementIn, advancementtabgui);
        }
    }

    public void rootSkillRemoved(SkillElement advancementIn) {
        //DevilRpg.LOGGER.info("|-------- rootSkillRemoved");
        SkillTab advancementtabgui = SkillTab.create(this.minecraft, this, this.tabs.size(), advancementIn,
                skillCap);
        if (advancementtabgui != null) {
            this.tabs.remove(advancementIn, advancementtabgui);
        }
    }

    /**
     * Agrega las hojas del nodo raiz
     */
    public void nonRootSkillAdded(SkillElement advancementIn) {
        //DevilRpg.LOGGER.info("|-------- nonRootSkillAdded");
        SkillTab advancementtabgui = this.getTab(advancementIn);
        if (advancementtabgui != null) {
            advancementtabgui.addSkillElement(advancementIn);
        }

    }

    public void nonRootSkillRemoved(SkillElement skillElementIn) {
        //DevilRpg.LOGGER.info("|-------- nonRootSkillRemoved");
        SkillTab advancementtabgui = this.getTab(skillElementIn);
        if (advancementtabgui != null) {
            advancementtabgui.removeSkillElement(skillElementIn);
        }
    }

    public void onUpdateAdvancementProgress(SkillElement skillElementIn, SkillProgress progress) {
        SkillWidget skillEntryGui = this.getSkillElementGui(skillElementIn);
        if (skillEntryGui != null) {
            skillEntryGui.setAdvancementProgress(progress);
        }

    }

    public void setSelectedTab(@Nullable SkillElement skillElement) {
        this.selectedTab = this.tabs.get(skillElement);
    }

    public void advancementsCleared() {
        this.tabs.clear();
        this.selectedTab = null;
    }

    @Nullable
    public SkillWidget getSkillElementGui(SkillElement skillElement) {
        SkillTab skillTabGui = this.getTab(skillElement);
        return skillTabGui == null ? null : skillTabGui.getSkillElementGui(skillElement);
    }

    @Nullable
    private SkillTab getTab(SkillElement skillElement) {
        while (skillElement.getParent() != null) {
            skillElement = skillElement.getParent();
        }

        return this.tabs.get(skillElement);
    }

    public PlayerSkillCapabilityInterface getSkillCap() {
        return skillCap;
    }

    public void setSkillCap(PlayerSkillCapabilityInterface skillCap) {
        this.skillCap = skillCap;
    }

    public PlayerExperienceCapabilityInterface getExpCap() {
        return expCap;
    }

    public void setExpCap(PlayerExperienceCapabilityInterface expCap) {
        this.expCap = expCap;
    }

    protected void addPowerButtons() {
        int k = 0;

        int offLeft = offsetLeft;
        int offTop = offsetTop;
        PowerEnum[] powerList = PowerEnum.values();
        //k = powerList.size();
        // Tamano (ancho y alto) del boton.
        float  xFix = -0.0f;
        int powerButtonSize = SkillWidget.FRAME_SIZE - POWER_BUTTON_SHRINK;
        for (PowerEnum powerEnum : powerList) {
            xFix+= -0.0f; //0.22f;
            int drawnSkillLevel = 0;
            // --- Calcular posicion y tamano del boton con variables descriptivas ---
            // Separacion horizontal entre el centro de cada boton.
            int powerButtonSpacing = powerButtonSize + POWER_BUTTON_GAP;
            // Posicion X de este boton (indice k). Multiplicar las coords de diseno por fitScale
            // para que queden alineadas con la ventana cuando se escala (evita que se descuadre
            // segun el tamano de la ventana / guiScale).
            int powerButtonX = offLeft + (int) ((POWER_INITIAL_X_POSITION + k * powerButtonSpacing + xFix) * this.fitScale);
            // Posicion Y de este boton (fila inferior, desplazada por POWER_BUTTON_Y_OFFSET).
            int powerButtonY = offTop + (int) ((TAB_BACKGROUND_WINDOW_AREA_OFFSET_Y + INNER_SCREEN_HEIGHT + POWER_BUTTON_Y_OFFSET) * this.fitScale);

            CustomSkillButton powerButtons = new CustomSkillButton(
                    powerButtonX,
                    powerButtonY,
                    powerButtonSize,
                    powerButtonSize,
                    ClientModKeyInputEventSubscriber.KeyEvent.getKeyName(powerEnum),
                    EMPTY_POWER_IMAGE_RESOURCE,
                    SkillWidget.BUTTON_IMAGE_SIZE, // 7
                    SkillWidget.BUTTON_IMAGE_SIZE, // 8
                    powerEnum,
                    drawnSkillLevel,
                    this::powerButtonPressed,
                    false,
                    7.0F);

            powerButtons.visible = true;

            powerButtonList.add(powerButtons);
            this.addRenderableWidget(powerButtons);
            k++;
        }

    }

    public void powerButtonPressed(Button pressedButton) {
        DevilRpg.LOGGER.info("--------powerButtonPressed: {} ", pressedButton.getMessage().getContents());

        if (pressedButton instanceof CustomSkillButton pb) {
            HashMap<PowerEnum, SkillEnum> powerNames = skillCap == null ? null : skillCap.getSkillsNameOfPowers();
            if (powerNames != null) {
                pb.setButtonTexture(EMPTY_POWER_IMAGE_RESOURCE);
                DevilRpg.LOGGER.info("pressed button: {} ", pb.getEnum());

                powerNames.put((PowerEnum) pb.getEnum(), SkillEnum.EMPTY);
                if (skillCap != null) skillCap.setSkillsNameOfPowers(powerNames, player);

            }
        }
        loadAssignedPowerButtons();
    }

    protected void loadAssignedPowerButtons() {
        DevilRpg.LOGGER.info("---------loadAssignedPowerButtons ");
        HashMap<PowerEnum, SkillEnum> powerToSkillDictionary = skillCap == null ? null : skillCap.getSkillsNameOfPowers();

        if (powerToSkillDictionary != null) {
            for (CustomSkillButton c : powerButtonList) {
                PowerEnum powerEnumFromButton = (PowerEnum) c.getEnum();
                SkillEnum aSkillEnum = powerToSkillDictionary.getOrDefault(powerEnumFromButton, SkillEnum.EMPTY);
                if (aSkillEnum != null) {
                    if (!aSkillEnum.equals(SkillEnum.EMPTY)) {
                        c.setButtonTexture(skillsImages.get(aSkillEnum));
                    } else {
                        c.setButtonTexture(EMPTY_POWER_IMAGE_RESOURCE);
                    }
                    DevilRpg.LOGGER.debug("--------- {}", aSkillEnum.getName());
                }
            }
        }
    }

    public Map<SkillEnum, ResourceLocation> getSkillsResourceLocations() {
        return skillsImages;
    }

    public void playDownSound(SoundManager p_230988_1_) {
        p_230988_1_.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }


    static class ButtonMouse {
        public static final int LEFT_BUTTON = 0;
        public static final int RIGHT_BUTTON = 1;

        private ButtonMouse() {

        }
    }
}
