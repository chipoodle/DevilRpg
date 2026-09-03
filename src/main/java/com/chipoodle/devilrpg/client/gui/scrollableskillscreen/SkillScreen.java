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
import com.chipoodle.devilrpg.init.ModNetwork;
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
    // El marco del usuario es 1419x1108 (misma proporcion que 302x236). Se dibuja completo,
    // escalado a la ventana de 302x(WINDOW_HEIGHT+INFO_SPACE) para aprovechar su nitidez.
    private static final int INITIAL_TEXTURE_WIDTH = 1419;
    private static final int INITIAL_TEXTURE_HEIGHT = 1108;
    private static final int INNER_SCREEN_WIDTH = 282;
    private static final int INNER_SCREEN_HEIGHT = 162;
    private static final int WINDOW_AREA_OFFSET_X = 10;
    private static final int WINDOW_AREA_OFFSET_Y = 18;
    private static final int WINDOW_WIDTH = 302;
    private static final int WINDOW_HEIGHT = 210;
    private static final int INFO_SPACE = 26;
    private static final String IMG_LOCATION = DevilRpg.MODID + ":textures/gui/";
    private static final ResourceLocation WINDOW_LOCATION = ResourceLocation.parse(IMG_LOCATION + "window-256b.png");
    private static final ResourceLocation TABS_LOCATION = ResourceLocation.parse(IMG_LOCATION + "advancements/tabs.png");
    private static final ResourceLocation EMPTY_POWER_IMAGE_RESOURCE = ResourceLocation.parse(IMG_LOCATION + "empty-box.png");

    private static final Component SAD_LABEL = Component.translatable("advancements.sad_label");
    private static final Component EMPTY = Component.translatable("advancements.empty");
    private static final Component GUI_LABEL = Component.translatable("gui.skills.title");
    private static final Component UNSPENT_LABEL = Component.translatable("gui.skills.unspent");
    public static final int POWER_INITIAL_X_POSITION = 145;
    // Separacion extra entre botones (se suma a SkillWidget.FRAME_SIZE). Mas alto = mas juntos/lejos.
    public static final int POWER_BUTTON_GAP = 1;
    // Desplazamiento vertical de los botones desde su base. Mas alto = mas abajo.
    public static final int POWER_BUTTON_Y_OFFSET = 6;
    // Se resta a SkillWidget.FRAME_SIZE para el tamano (alto y ancho) de cada boton.
    public static final int POWER_BUTTON_SHRINK = 6;
    private static int tabPage, maxPages;
    private final ClientSkillBuilderFromJson clientSkillManager;
    private final Map<SkillElement, SkillTab> tabs = Maps.newLinkedHashMap();
    private final Player player;
    private final EnumMap<SkillEnum, ResourceLocation> skillsImages;
    private SkillTab selectedTab;
    private boolean isScrolling;
    private boolean isDraggingToPowerButton;
    private int offsetLeft;
    private int offsetTop;
    /** Escala para que la ventana (302x210) quepa en la pantalla virtual con cualquier guiScale */
    private float fitScale = 1.0F;
    /** Evita que super.render vuelva a dibujar el fondo encima de la ventana ya renderizada */
    private boolean skipBackgroundRenderOnce = false;

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
            maxPages = this.tabs.size() / SkillTabType.MAX_TABS;
        }

        //////////////////////////////////////////
        addThemeButtons();

        maxPages = this.tabs.size() / SkillTabType.MAX_TABS;
        ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        addPowerButtons();
        loadAssignedPowerButtons();
    }

    private void addThemeButtons() {
        // Botones de cambio de tema, en la barra de info inferior a la DERECHA (la zona
        // izquierda la ocupa el mensaje de puntos sin usar) para que no se estorben.
        int infoY = (int) (offsetTop + WINDOW_HEIGHT * this.fitScale) + 2;

        Button themeForward = Button.builder(
                        Component.literal(">"),
                        b -> {
                            SkillWidget.changeWidgetTheme(true);
                        }
                )
                .pos((int) (offsetLeft + (WINDOW_WIDTH - 28) * this.fitScale), infoY)
                .size(20, 20)
                .build();

        Button themeBackwards = Button.builder(
                        Component.literal("<"),
                        b -> {
                            SkillWidget.changeWidgetTheme(false);
                        }
                )
                .pos((int) (offsetLeft + (WINDOW_WIDTH - 48) * this.fitScale), infoY)
                .size(20, 20)
                .build();

        addRenderableWidget(themeForward);
        addRenderableWidget(themeBackwards);
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
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Fondo UNA sola vez, antes de la ventana (no se repite encima de esta)
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        // Escala la ventana para que quepa en la pantalla virtual con cualquier guiScale.
        // Con guiScale 1-2 fitScale=1 y el comportamiento es identico al original.
        this.fitScale = Math.min(1.0F,
                Math.min((float) this.width / WINDOW_WIDTH, (float) this.height / WINDOW_HEIGHT));
        offsetLeft = (this.width - (int) (WINDOW_WIDTH * this.fitScale)) / 2;
        offsetTop = (this.height - (int) (WINDOW_HEIGHT * this.fitScale)) / 2;

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
            double lmX = (mouseX - offsetLeft) / this.fitScale;
            double lmY = (mouseY - offsetTop) / this.fitScale;
            for (SkillTab rootSkillTabGui : this.tabs.values()) {
                if (rootSkillTabGui.getPage() == tabPage) {
                    if (rootSkillTabGui.isInsideTabSelector(0, 0, lmX, lmY)) {
                        this.clientSkillManager.setSelectedTab(rootSkillTabGui.getSkillElement(), true);
                        break;
                    } else {
                        SkillWidget skillEntryGui = selectedTab.getIfInsideIncludingChildren(
                                lmX - WINDOW_AREA_OFFSET_X, lmY - WINDOW_AREA_OFFSET_Y);
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
                double lmX = (mouseX - offsetLeft) / this.fitScale;
                double lmY = (mouseY - offsetTop) / this.fitScale;
                draggedSkillWidget = selectedTab.getIfInsideIncludingChildren(
                        lmX - WINDOW_AREA_OFFSET_X, lmY - WINDOW_AREA_OFFSET_Y);
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

            // Pinta el título (blanco para que se vea sobre el marco oscuro)
            guiGraphics.drawString(this.font, GUI_LABEL, 8, 6, 0xFFFFFF);

            int unspentPoints = expCap == null ? -1 : expCap.getUnspentPoints();
            int currentLevel = expCap == null ? -1 : expCap.getCurrentLevel();
            // Mensaje compacto para no chocar con los botones de tema (derecha)
            Component infoHolder = Component.literal(
                    "Lv " + currentLevel + "  \u00B7  " + UNSPENT_LABEL.getString() + " " + unspentPoints);
            //Pinta nivel + puntos sin usar, con fuente mas pequeña para que quepa en la placa del marco.
            PoseStack psInfo = guiGraphics.pose();
            psInfo.pushPose();
            psInfo.translate(46, 198, 0);
            psInfo.scale(0.7F, 0.7F, 1.0F);
            guiGraphics.drawString(this.font, infoHolder, 0, 0, 0xFFFFFF);
            psInfo.popPose();

            //this.font.draw(poseStack, Component.literal("x:"+d.format(posicionMouseX)+" y:"+d.format(posicionMouseY)), (float) posicionMouseX,(float)posicionMouseY, 10526880);
        }

    }
    static DecimalFormat d = new DecimalFormat("#,###.#");

    /**
     * Pinta el fondo incluyendo los botones de las skills
     *
     * @param poseStack poseStack
     * @param mouseX X mouse
     * @param mouseY Y mouse
     */
    @SuppressWarnings("deprecation")
    private void renderInside(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        SkillTab selectedSkillTabGui = this.selectedTab;
        // Pinta el fondo vacío cuando no hay elementos
        if (selectedSkillTabGui == null) {
            guiGraphics.fill(WINDOW_AREA_OFFSET_X, WINDOW_AREA_OFFSET_Y,
                    WINDOW_AREA_OFFSET_X + INNER_SCREEN_WIDTH,
                    WINDOW_AREA_OFFSET_Y + INNER_SCREEN_HEIGHT, -16777216);
            int i = WINDOW_AREA_OFFSET_X + 117;
            guiGraphics.drawCenteredString(this.font, EMPTY, i, WINDOW_AREA_OFFSET_Y + 56 - WINDOW_AREA_OFFSET_X / 2, -1);
            guiGraphics.drawCenteredString(this.font, SAD_LABEL, i, WINDOW_AREA_OFFSET_Y + INNER_SCREEN_HEIGHT - WINDOW_AREA_OFFSET_X, -1);
        } else {
            // Pinta el fondo con elementos: drawContents aplica su propio translate (origen
            // local de la ventana) y el scissor en coordenadas de pantalla (virtual)
            selectedSkillTabGui.drawContents(guiGraphics,
                    WINDOW_AREA_OFFSET_X, WINDOW_AREA_OFFSET_Y,
                    (int) (offsetLeft + WINDOW_AREA_OFFSET_X * this.fitScale),
                    (int) (offsetTop + WINDOW_AREA_OFFSET_Y * this.fitScale),
                    (int) (SkillTab.TAB_BACKGROUND_X * this.fitScale),
                    (int) (SkillTab.TAB_BACKGROUND_Y * this.fitScale));
            RenderSystem.depthFunc(515);
            RenderSystem.disableDepthTest();
        }
    }

    @SuppressWarnings("deprecation")
    private void renderTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        // mouse en coordenadas locales de la ventana (diseno)
        double lmX = (mouseX - offsetLeft) / this.fitScale;
        double lmY = (mouseY - offsetTop) / this.fitScale;
        // pinta los tooltips de los botones
        if (this.selectedTab != null) {
            PoseStack poseStack = guiGraphics.pose();
            poseStack.pushPose();
            //RenderSystem.enableDepthTest();
            poseStack.translate(WINDOW_AREA_OFFSET_X, WINDOW_AREA_OFFSET_Y, 400.0F);
            this.selectedTab.drawTabTooltips(guiGraphics,
                    (int) lmX - WINDOW_AREA_OFFSET_X,
                    (int) lmY - WINDOW_AREA_OFFSET_Y,
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
        for (PowerEnum powerEnum : powerList) {
            int drawnSkillLevel = 0;
            // --- Calcular posicion y tamano del boton con variables descriptivas ---
            // Separacion horizontal entre el centro de cada boton.
            int powerButtonSpacing = SkillWidget.FRAME_SIZE + POWER_BUTTON_GAP;
            // Posicion X de este boton (indice k).
            int powerButtonX = offLeft + POWER_INITIAL_X_POSITION + (k * powerButtonSpacing);
            // Posicion Y de este boton (fila inferior, desplazada por POWER_BUTTON_Y_OFFSET).
            int powerButtonY = WINDOW_AREA_OFFSET_Y + offTop + INNER_SCREEN_HEIGHT + POWER_BUTTON_Y_OFFSET;
            // Tamano (ancho y alto) del boton.
            int powerButtonSize = SkillWidget.FRAME_SIZE - POWER_BUTTON_SHRINK;

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
