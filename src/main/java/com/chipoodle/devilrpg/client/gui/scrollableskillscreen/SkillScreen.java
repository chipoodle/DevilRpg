package com.chipoodle.devilrpg.client.gui.scrollableskillscreen;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.experience.PlayerExperienceCapability;
import com.chipoodle.devilrpg.capability.experience.PlayerExperienceCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.client.gui.scrollableskillscreen.model.ClientSkillBuilderFromJson;
import com.chipoodle.devilrpg.client.gui.scrollableskillscreen.model.CustomSkillButton;
import com.chipoodle.devilrpg.eventsubscriber.client.ClientModKeyInputEventSubscriber;
import com.chipoodle.devilrpg.init.ModNetwork;
import com.chipoodle.devilrpg.network.handler.PlayerPassiveSkillServerHandler;
import com.chipoodle.devilrpg.util.PowerEnum;
import com.chipoodle.devilrpg.util.SkillEnum;
import com.google.common.collect.Maps;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.Minecraft;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nullable;
import java.util.*;

@OnlyIn(Dist.CLIENT)
public class SkillScreen extends Screen implements ClientSkillBuilderFromJson.IListener {
    private static final int INITIAL_TEXTURE_WIDTH = 308;
    private static final int INITIAL_TEXTURE_HEIGHT = 256;
    private static final int INNER_SCREEN_WIDTH = 282;
    private static final int INNER_SCREEN_HEIGHT = 162;
    private static final int WINDOW_AREA_OFFSET_X = 10;
    private static final int WINDOW_AREA_OFFSET_Y = 18;
    private static final int WINDOW_WIDTH = 302;
    private static final int WINDOW_HEIGHT = 190;
    private static final int INFO_SPACE = 20;
    private static final String IMG_LOCATION = DevilRpg.MODID + ":textures/gui/";
    private static final ResourceLocation WINDOW_LOCATION = new ResourceLocation(IMG_LOCATION + "window-256b.png");
    private static final ResourceLocation TABS_LOCATION = new ResourceLocation("textures/gui/advancements/tabs.png");
    private static final ResourceLocation EMPTY_POWER_IMAGE_RESOURCE = new ResourceLocation(IMG_LOCATION + "empty-box.png");

    private static final Component SAD_LABEL = Component.translatable("advancements.sad_label");
    private static final Component EMPTY = Component.translatable("advancements.empty");
    private static final Component GUI_LABEL = Component.translatable("gui.skills.title");
    private static final Component UNSPENT_LABEL = Component.translatable("gui.skills.unspent");
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
    private InputConstants.Key openScreenKeyPressed;
    private SkillWidget draggedSkillWidget;
    private double posicionMouseX;
    private double posicionMouseY;
    private double dragPositionMouseX;
    private double dragPositionMouseY;
    private LazyOptional<PlayerSkillCapabilityInterface> skillCap;
    private LazyOptional<PlayerExperienceCapabilityInterface> expCap;
    private Set<CustomSkillButton> powerButtonList;

    private SkillScreen() {
        super(GameNarrator.NO_TITLE);
        isDraggingToPowerButton = false;
        draggedSkillWidget = null;
        skillsImages = new EnumMap<>(SkillEnum.class);

        this.player = Minecraft.getInstance().player;
        expCap = player.getCapability(PlayerExperienceCapability.INSTANCE);
        skillCap = player.getCapability(PlayerSkillCapability.INSTANCE);
        ClientSkillBuilderFromJson skillManager = skillCap.map(s -> s.getClientSkillBuilder()).orElse(null); //new ClientSkillBuilder(/*Minecraft.getInstance()*/);
        //skillManager.buildSkillTrees();
        this.clientSkillManager = skillManager;
    }

    public SkillScreen(InputConstants.Key input) {
        this();
        openScreenKeyPressed = input;
        powerButtonList = new LinkedHashSet<>();
    }

    @Override
    protected void init() {
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
        ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        addPowerButtons();
        loadAssignedPowerButtons();
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
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {

        offsetLeft = (this.width - WINDOW_WIDTH) / 2;
        offsetTop = (this.height - WINDOW_HEIGHT) / 2;
        this.renderBackground(poseStack);
        if (maxPages != 0) {
            Component page = Component.literal(String.format("%d / %d", tabPage + 1, maxPages + 1));
            int width = this.font.width(page);
            //RenderSystem.disableLighting();
            this.font.drawShadow(poseStack, page.getVisualOrderText(), offsetLeft + (WINDOW_WIDTH / 2) - (width / 2),
                    offsetTop - 44, -1);
        }
        this.renderInside(poseStack, mouseX, mouseY);
        this.renderWindow(poseStack);
        this.renderTooltips(poseStack, mouseX, mouseY);
        super.render(poseStack, mouseX, mouseY, partialTicks);
        this.renderSkillButtonPressed(poseStack);
    }

    private void renderSkillButtonPressed(PoseStack poseStack) {
        if (draggedSkillWidget != null) {

            poseStack.pushPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setShader(GameRenderer::getPositionTexShader);

            // Texturas de los marcos, y barras de título de los tooltips
            //RenderSystem.setShaderTexture(0, WIDGETS);
            // Pinta el icono del botón
            this.blit(poseStack, draggedSkillWidget.getX() + 3, draggedSkillWidget.getY(), draggedSkillWidget.getDisplayInfo().getFrame().getIcon(),
                   0,SkillWidget.FRAME_SIZE, SkillWidget.FRAME_SIZE);

            draggedSkillWidget.drawButton(poseStack, (int) posicionMouseX, (int) posicionMouseY, false,
                    draggedSkillWidget.getDisplayInfo().getImage(), true, draggedSkillWidget.isDisabled());

            poseStack.popPose();
        }
    }

    public void skillButtonPressed(SkillWidget skillEntryGui) {
        SkillEnum skillEnum = skillEntryGui.getSkillElement().getSkillCapability();
        DevilRpg.LOGGER.debug("|----------- skillButtonPressed: {}", skillEnum);
        if (!skillEnum.equals(SkillEnum.EMPTY)) {
            skillCap.ifPresent(aSkillCap -> {
                HashMap<SkillEnum, Integer> skillsPoints = aSkillCap.getSkillsPoints();
                HashMap<SkillEnum, Integer> skillsMaxPoints = aSkillCap.getMaxSkillsPoints();
                Integer points = skillsPoints.get(skillEnum);
                Integer maxPoints = skillsMaxPoints.get(skillEnum);
                if (points < maxPoints) {
                    points += expCap.map(PlayerExperienceCapabilityInterface::consumePoint).orElse(0);
                    skillsPoints.put(skillEnum, points);
                    aSkillCap.setSkillsPoints(skillsPoints, player);
                    skillEntryGui.updateFormattedLevelString(points, maxPoints);


                    if (skillEnum.isPassive() && !skillEnum.iforMinion()) {
                        //Para pasivos
                        CompoundTag compoundTag = aSkillCap.setSkillToByteArray(skillEnum);
                        ModNetwork.CHANNEL.sendToServer(new PlayerPassiveSkillServerHandler(compoundTag));
                    }
                }
            });
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == ButtonMouse.LEFT_BUTTON) {
            for (SkillTab rootSkillTabGui : this.tabs.values()) {
                if (rootSkillTabGui.getPage() == tabPage) {
                    if (rootSkillTabGui.isInsideTabSelector(offsetLeft, offsetTop, mouseX, mouseY)) {
                        this.clientSkillManager.setSelectedTab(rootSkillTabGui.getSkillElement(), true);
                        break;
                    } else {
                        SkillWidget skillEntryGui = selectedTab.getIfInsideIncludingChildren(
                                mouseX - offsetLeft - WINDOW_AREA_OFFSET_X, mouseY - offsetTop - WINDOW_AREA_OFFSET_Y);
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
                draggedSkillWidget = selectedTab.getIfInsideIncludingChildren(
                        mouseX - offsetLeft - WINDOW_AREA_OFFSET_X, mouseY - offsetTop - WINDOW_AREA_OFFSET_Y);
                if (draggedSkillWidget != null && (draggedSkillWidget.isDisabled() || !draggedSkillWidget.getSkillProgress().hasProgress())) {
                    draggedSkillWidget = null;
                }
            }

            if (draggedSkillWidget != null && draggedSkillWidget.getSkillElement().getDisplay().getFrame().equals(
                    SkillFrameType.TASK) /* skillEntryGuiApretado.getSkillElement().getParent() != null */
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
            DevilRpg.LOGGER.info("|----------- rightMouseReleases: {}", draggedSkillWidget.getSkillElement().getSkillCapability());

            CustomSkillButton copy = powerButtonList.stream().filter(x -> x.isInside(mouseX, mouseY)).findAny().orElse(null);
            if (copy != null) {
                copy.setButtonTexture(draggedSkillWidget.getDisplayInfo().getImage());
                HashMap<PowerEnum, SkillEnum> powerNames = skillCap.map(PlayerSkillCapabilityInterface::getSkillsNameOfPowers).orElse(null);
                if (powerNames != null) {
                    powerNames.put((PowerEnum) copy.getEnum(), draggedSkillWidget.getSkillElement().getSkillCapability());
                    skillCap.ifPresent(x -> x.setSkillsNameOfPowers(powerNames, player));
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
    public void renderWindow(PoseStack poseStack) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        // Pinta la pantalla exterior
        RenderSystem.setShaderTexture(0, WINDOW_LOCATION);
        this.blit(poseStack, offsetLeft, offsetTop, 0, 0, WINDOW_WIDTH, WINDOW_HEIGHT + INFO_SPACE, INITIAL_TEXTURE_WIDTH, INITIAL_TEXTURE_HEIGHT);

        if (this.tabs.size() > 1) {
            RenderSystem.setShaderTexture(0, TABS_LOCATION);

            // Pinta todas las pestañas, tanto la seleccionada como las no seleccionadas
            for (SkillTab skillTabGui : this.tabs.values()) {
                if (skillTabGui.getPage() == tabPage)
                    skillTabGui.drawTab(poseStack, offsetLeft, offsetTop, skillTabGui == this.selectedTab);
            }

            RenderSystem.defaultBlendFunc();
            // Pinta el ícono o la imagen de la pestaña (tab)
            int k = 0;
            for (SkillTab skillTab : this.tabs.values()) {
                if (skillTab.getPage() == tabPage) {
                    //guiSkillTab.drawIcon(offsetLeft, offsetTop, this.itemRenderer);
                    skillTab.drawIconImage(poseStack, offsetLeft, offsetTop);
                }
            }
            RenderSystem.disableBlend();

            // Pinta el título
            this.font.draw(poseStack, GUI_LABEL, (offsetLeft + 8), (offsetTop + 6), 4210752);

            int unspentPoints = expCap.map(y -> y.getUnspentPoints()).orElse(-1);
            if (unspentPoints != 0) {
                Component unspentSkillHolder = Component.literal("" + UNSPENT_LABEL.getString() + " " + unspentPoints);
                //Pinta puntos sin usar
                this.font.draw(poseStack, unspentSkillHolder, (offsetLeft + 8), (offsetTop + WINDOW_HEIGHT), 4210752);
            }

        }

        // Pinta coordenadas de mouse (DEBUG)
		/*this.font.draw(poseStack,
				new StringTextComponent(
						"x: " + (offsetLeft + WINDOW_AREA_OFFSET_X) + " y: " + (offsetTop + WINDOW_AREA_OFFSET_Y)),
				(float) (offsetLeft + 14), (float) (offsetTop + 18), 4210752);

		this.font.draw(poseStack, new StringTextComponent("offsetLeft: " + offsetLeft + " offsetTop: " + offsetTop),
				(float) (offsetLeft + 14), (float) (offsetTop + 28), 4210752);

		this.font.draw(poseStack,
				new StringTextComponent("I width: " + (offsetLeft + WINDOW_AREA_OFFSET_X + INNER_SCREEN_WIDTH)
						+ " I height: " + (offsetTop + WINDOW_AREA_OFFSET_Y + INNER_SCREEN_HEIGHT)),
				(float) (offsetLeft + 14), (float) (offsetTop + 38), 4210752);


		this.font.draw(poseStack, new StringTextComponent("mouseX: " + mouseX + " mouseY: " + mouseY),
				(float) (offsetLeft + 14), (float) (offsetTop + 48), 4210752);

		this.font.draw(poseStack,
				new StringTextComponent(
						"ScrollX: " + selectedTab.getScrollX() + " ScrollY: " + selectedTab.getScrollY()),
				(float) (offsetLeft + 14), (float) (offsetTop + 58), 4210752);*/

    }

    /**
     * Pinta el fondo incluyendo los botones de las skills
     *
     * @param poseStack
     * @param mouseX
     * @param mouseY
     */
    @SuppressWarnings("deprecation")
    private void renderInside(PoseStack poseStack, int mouseX, int mouseY) {
        SkillTab selectedSkillTabGui = this.selectedTab;
        // Pinta el fondo vacío cuando no hay elementos
        if (selectedSkillTabGui == null) {
            fill(poseStack, offsetLeft + WINDOW_AREA_OFFSET_X, offsetTop + WINDOW_AREA_OFFSET_Y,
                    offsetLeft + WINDOW_AREA_OFFSET_X + INNER_SCREEN_WIDTH,
                    offsetTop + WINDOW_AREA_OFFSET_Y + INNER_SCREEN_HEIGHT, -16777216);
            int i = offsetLeft + WINDOW_AREA_OFFSET_X + 117;
            drawCenteredString(poseStack, this.font, EMPTY, i,
                    offsetTop + WINDOW_AREA_OFFSET_Y + 56 - WINDOW_AREA_OFFSET_X / 2, -1);
            drawCenteredString(poseStack, this.font, SAD_LABEL, i,
                    offsetTop + WINDOW_AREA_OFFSET_Y + INNER_SCREEN_HEIGHT - WINDOW_AREA_OFFSET_X, -1);
        } else {
            // Pinta el fondo con elementos
            poseStack.pushPose();
            // Se posiciona al inicio de la ventana + offsets
            poseStack.translate((float) (offsetLeft + WINDOW_AREA_OFFSET_X),
                    (float) (offsetTop + WINDOW_AREA_OFFSET_Y), 0.0F);
            // Pinta el fondo del tab
            RenderSystem.applyModelViewMatrix();
            selectedSkillTabGui.drawContents(poseStack);
            poseStack.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.depthFunc(515);
            RenderSystem.disableDepthTest();
        }
    }

    @SuppressWarnings("deprecation")
    private void renderTooltips(PoseStack poseStack1, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        // pinta los tooltips de los botones
        if (this.selectedTab != null) {
            PoseStack poseStack = RenderSystem.getModelViewStack();
            poseStack.pushPose();
            //RenderSystem.enableDepthTest();
            poseStack.translate((float) (offsetLeft + WINDOW_AREA_OFFSET_X), (float) (offsetTop + WINDOW_AREA_OFFSET_Y), 400.0F);
            RenderSystem.applyModelViewMatrix();
            this.selectedTab.drawTabTooltips(poseStack1, mouseX - offsetLeft - WINDOW_AREA_OFFSET_X, mouseY - offsetTop - WINDOW_AREA_OFFSET_Y, offsetLeft, offsetTop);
            RenderSystem.disableDepthTest();
            poseStack.popPose();
            RenderSystem.applyModelViewMatrix();
        }

        // Pinta los tooltips de las pestañas
        if (this.tabs.size() > 1) {
            for (SkillTab skillTabGui : this.tabs.values()) {
                if (skillTabGui.getPage() == tabPage
                        && skillTabGui.isInsideTabSelector(offsetLeft, offsetTop, mouseX, mouseY)) {
                    this.renderTooltip(poseStack1, skillTabGui.getTitle(), mouseX, mouseY);
                }
            }
        }

    }

    public boolean isInsideInnerFrame(double coordX, double coordY, int frameWidth, int frameHeight) {
        return coordX + frameWidth >= 0 && coordY + frameHeight >= 0 && coordX <= (INNER_SCREEN_WIDTH)
                && coordY <= (INNER_SCREEN_HEIGHT);
    }

    public void rootSkillAdded(SkillElement advancementIn) {
        //DevilRpg.LOGGER.info("|-------- rootSkillAdded");
        SkillTab advancementtabgui = SkillTab.create(this.minecraft, this, this.tabs.size(), advancementIn,
                skillCap);
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

    public LazyOptional<PlayerSkillCapabilityInterface> getSkillCap() {
        return skillCap;
    }

    public void setSkillCap(LazyOptional<PlayerSkillCapabilityInterface> skillCap) {
        this.skillCap = skillCap;
    }

    public LazyOptional<PlayerExperienceCapabilityInterface> getExpCap() {
        return expCap;
    }

    public void setExpCap(LazyOptional<PlayerExperienceCapabilityInterface> expCap) {
        this.expCap = expCap;
    }

    protected void addPowerButtons() {
        int k = 0;

        int offLeft = (this.width - WINDOW_WIDTH) / 2;
        int offTop = (this.height - WINDOW_HEIGHT) / 2;
        List<PowerEnum> powerList = Arrays.asList(PowerEnum.values());
        k = powerList.size();
        for (PowerEnum powerEnum : powerList) {
            int drawnSkillLevel = 0;
            CustomSkillButton powrButtons = new CustomSkillButton(
                    (WINDOW_AREA_OFFSET_X + offLeft + 10) + (k * (SkillWidget.FRAME_SIZE + 10)),
                    WINDOW_AREA_OFFSET_Y + offTop + INNER_SCREEN_HEIGHT + 2,
                    SkillWidget.FRAME_SIZE - 5, // 3
                    SkillWidget.FRAME_SIZE - 5, // 4
                    ClientModKeyInputEventSubscriber.KeyEvent.getKeyName(powerEnum),
                    EMPTY_POWER_IMAGE_RESOURCE,
                    SkillWidget.BUTTON_IMAGE_SIZE, // 7
                    SkillWidget.BUTTON_IMAGE_SIZE, // 8
                    powerEnum,
                    drawnSkillLevel,
                    this::powerButtonPressed,
                    false,
                    7.0F);

            powrButtons.visible = true;

            powerButtonList.add(powrButtons);
            this.addRenderableWidget(powrButtons);
            k++;
        }

    }

    public void powerButtonPressed(Button pressedButton) {
        DevilRpg.LOGGER.info("--------powerButtonPressed: {} ", pressedButton.getMessage().getContents());

        if (pressedButton instanceof CustomSkillButton) {
            CustomSkillButton pb = (CustomSkillButton) pressedButton;
            HashMap<PowerEnum, SkillEnum> powerNames = skillCap.map(PlayerSkillCapabilityInterface::getSkillsNameOfPowers).orElse(null);
            if (powerNames != null) {
                pb.setButtonTexture(EMPTY_POWER_IMAGE_RESOURCE);
                DevilRpg.LOGGER.info("pressed button: {} ", pb.getEnum());

                powerNames.put((PowerEnum) pb.getEnum(), SkillEnum.EMPTY);
                skillCap.ifPresent(x -> x.setSkillsNameOfPowers(powerNames, player));

            }
        }
        loadAssignedPowerButtons();
    }

    protected void loadAssignedPowerButtons() {
        DevilRpg.LOGGER.info("---------loadAssignedPowerButtons ");
        HashMap<PowerEnum, SkillEnum> powerToSkillDictionary = skillCap.map(x -> x.getSkillsNameOfPowers()).orElse(null);

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
                    DevilRpg.LOGGER.debug("--------- {}",aSkillEnum.getName());
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


    class ButtonMouse {
        public static final int LEFT_BUTTON = 0;
        public static final int RIGHT_BUTTON = 1;

        private ButtonMouse() {

        }
    }
}
