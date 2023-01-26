package com.chipoodle.devilrpg.client.gui.scrollableskillscreen;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.experience.IBaseExperienceCapability;
import com.chipoodle.devilrpg.capability.experience.PlayerExperienceCapabilityProvider;
import com.chipoodle.devilrpg.capability.skill.IBasePlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityProvider;
import com.chipoodle.devilrpg.client.gui.scrollableskillscreen.model.ClientSkillBuilder;
import com.chipoodle.devilrpg.client.gui.scrollableskillscreen.model.CustomSkillButton;
import com.chipoodle.devilrpg.eventsubscriber.client.ClientModKeyInputEventSubscriber;
import com.chipoodle.devilrpg.util.PowerEnum;
import com.chipoodle.devilrpg.util.SkillEnum;
import com.google.common.collect.Maps;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SimpleSound;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.chat.NarratorChatListener;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.util.InputMappings;
import net.minecraft.client.util.InputMappings.Input;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.client.gui.widget.ExtendedButton;

@OnlyIn(Dist.CLIENT)
public class ScrollableSkillScreen extends Screen implements ClientSkillBuilder.IListener {
	private static final int INITIAL_TEXTURE_WIDTH = 308;
	private static final int INITIAL_TEXTURE_HEIGHT = 256;
	private static final int INNER_SCREEN_WIDTH = 282;
	private static final int INNER_SCREEN_HEIGHT = 162;
	private static final int WINDOW_AREA_OFFSET_X = 10;
	private static final int WINDOW_AREA_OFFSET_Y = 18;
	private static final int INITIAL_WIDTH = 302;
	private static final int INITIAL_HEIGHT = 190;
	private static final int INFO_SPACE = 20;
	private static final String IMG_LOCATION = DevilRpg.MODID + ":textures/gui/";
	private static final ResourceLocation WINDOW_LOCATION = new ResourceLocation(IMG_LOCATION + "window-256b.png");
	private static final ResourceLocation TABS_LOCATION = new ResourceLocation("textures/gui/advancements/tabs.png");
	private static final ResourceLocation EMPTY_POWER_IMAGE_RESOURCE = new ResourceLocation(IMG_LOCATION + "celtic/empty.png");
 
	private static final ITextComponent SAD_LABEL = new TranslationTextComponent("advancements.sad_label");
	private static final ITextComponent EMPTY = new TranslationTextComponent("advancements.empty");
	private static final ITextComponent GUI_LABEL = new TranslationTextComponent("gui.skills.title");
	private static final ITextComponent UNSPENT_LABEL = new TranslationTextComponent("gui.skills.unspent");
	private final ClientSkillBuilder clientSkillManager;
	private final Map<SkillElement, GuiSkillTab> tabs = Maps.newLinkedHashMap();
	private GuiSkillTab selectedTab;
	private boolean isScrolling;
	private boolean isDraggingToPowerButton;
	private static int tabPage, maxPages;

	private double mouseX;
	private double mouseY;
	private int offsetLeft;
	private int offsetTop;

	private Input openScreenKeyPressed;

	
	private GuiSkillEntry skillEntryGuiApretado;
	private double posicionMouseX;
	private double posicionMouseY;

	private double dragPositionMouseX;
	private double dragPositionMouseY;

	private PlayerEntity player;
	private LazyOptional<IBasePlayerSkillCapability> skillCap;
	private LazyOptional<IBaseExperienceCapability> expCap;

	private Set<CustomSkillButton> powerButtonList;

	private EnumMap<SkillEnum, ResourceLocation> skillsImages;

	class ButtonMouse {
		private ButtonMouse() {

		}

		public static final int LEFT_BUTTON = 0;
		public static final int RIGHT_BUTTON = 1;
	}

	private ScrollableSkillScreen() {
		super(NarratorChatListener.NO_TITLE);
		ClientSkillBuilder skillManager = new ClientSkillBuilder(/*Minecraft.getInstance()*/);
		skillManager.buildSkillTrees();
		this.clientSkillManager = skillManager;
		isDraggingToPowerButton = false;
		skillEntryGuiApretado = null;
		skillsImages = new EnumMap<>(SkillEnum.class);
	}

	public ScrollableSkillScreen(Input input) {
		this();
		openScreenKeyPressed = input;
		this.player = Minecraft.getInstance().player;
		expCap = player.getCapability(PlayerExperienceCapabilityProvider.EXPERIENCE_CAP);
		skillCap = player.getCapability(PlayerSkillCapabilityProvider.SKILL_CAP);
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
			int guiLeft = (this.width - INITIAL_WIDTH) / 2;
			int guiTop = (this.height - INITIAL_HEIGHT) / 2;
			// pinta boton <
			addButton(new ExtendedButton(guiLeft, guiTop - 50, 20, 20, new StringTextComponent("<"),
					b -> tabPage = Math.max(tabPage - 1, 0)));
			// pinta boton >
			addButton(new ExtendedButton(guiLeft + INITIAL_WIDTH - 20, guiTop - 50, 20, 20,
					new StringTextComponent(">"), b -> tabPage = Math.min(tabPage + 1, maxPages)));
			maxPages = this.tabs.size() / SkillTabType.MAX_TABS;
		}
		///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		addPowerButtons();
		loadAssignedPowerButtons();
	}

	@Override
	public void onClose() {
		this.clientSkillManager.setListener((ClientSkillBuilder.IListener) null);
		super.onClose();
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		Input pressedKeyCode = InputMappings.Type.KEYSYM.getOrCreate(keyCode);
		if (openScreenKeyPressed.getName().equals(pressedKeyCode.getName())) {
			this.onClose();
			return true;
		} else
			return super.keyPressed(pressedKeyCode.getValue(), scanCode, modifiers);
	}

	@Override
	public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {

		offsetLeft = (this.width - INITIAL_WIDTH) / 2;
		offsetTop = (this.height - INITIAL_HEIGHT) / 2;
		this.renderBackground(matrixStack);
		if (maxPages != 0) {
			ITextComponent page = new StringTextComponent(String.format("%d / %d", tabPage + 1, maxPages + 1));
			int width = this.font.width(page);
			RenderSystem.disableLighting();
			this.font.drawShadow(matrixStack, page.getVisualOrderText(), offsetLeft + (INITIAL_WIDTH / 2) - (width / 2),
					offsetTop - 44, -1);
		}
		this.renderInside(matrixStack, mouseX, mouseY);
		this.renderWindow(matrixStack);
		this.renderTooltips(matrixStack, mouseX, mouseY);
		super.render(matrixStack, mouseX, mouseY, partialTicks);
		renderSkillButtonApretado(matrixStack);
	}

	private void renderSkillButtonApretado(MatrixStack matrixStack) {
		if (skillEntryGuiApretado != null) {
			skillEntryGuiApretado.drawButton(matrixStack, (int) posicionMouseX, (int) posicionMouseY, false,
					skillEntryGuiApretado.getDisplayInfo().getImage(), true,skillEntryGuiApretado.isDisabled());
		}
	}

	public void skillButtonPressed(GuiSkillEntry skillEntryGui) {
		SkillEnum skilEnum = skillEntryGui.getSkillElement().getSkillCapability();
		DevilRpg.LOGGER.info("|----------- skillButtonPressed: {}",skilEnum);
		if (!skilEnum.equals(SkillEnum.EMPTY)) {
			skillCap.ifPresent(aSkillCap -> {
				HashMap<SkillEnum, Integer> skillsPoints = aSkillCap.getSkillsPoints();
				HashMap<SkillEnum, Integer> skillsMaxPoints = aSkillCap.getMaxSkillsPoints();
				Integer points = skillsPoints.get(skilEnum);
				Integer maxPoints = skillsMaxPoints.get(skilEnum);
				if (points < maxPoints) {
					points += expCap.map(IBaseExperienceCapability::consumePoint).orElse(0);
					skillsPoints.put(skilEnum, points);
					aSkillCap.setSkillsPoints(skillsPoints, player);
					skillEntryGui.updateFormattedLevelString(points, maxPoints);





					//Para pasivos
				}

			});
		}
	}
	
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == ButtonMouse.LEFT_BUTTON) {
			for (GuiSkillTab rootSkillTabGui : this.tabs.values()) {
				if (rootSkillTabGui.getPage() == tabPage) {
					if (rootSkillTabGui.isInsideTabSelector(offsetLeft, offsetTop, mouseX, mouseY)) {
						this.clientSkillManager.setSelectedTab(rootSkillTabGui.getSkillElement(), true);
						break;
					} else {
						GuiSkillEntry skillEntryGui = selectedTab.getIfInsideIncludingChildren(
								mouseX - offsetLeft - WINDOW_AREA_OFFSET_X, mouseY - offsetTop - WINDOW_AREA_OFFSET_Y);
						if (skillEntryGui != null && skillEntryGui.getSkillElement().getParent() != null  && !skillEntryGui.isDisabled() ) {
							DevilRpg.LOGGER.info("|----------- mouseClicked: {}",skillEntryGui.getSkillElement().getSkillCapability());
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
				skillEntryGuiApretado = selectedTab.getIfInsideIncludingChildren(
						mouseX - offsetLeft - WINDOW_AREA_OFFSET_X, mouseY - offsetTop - WINDOW_AREA_OFFSET_Y);
				if(skillEntryGuiApretado!= null && (skillEntryGuiApretado.isDisabled() || !skillEntryGuiApretado.getSkillProgress().hasProgress())) {
					skillEntryGuiApretado = null;
				}
			}

			if (skillEntryGuiApretado != null && skillEntryGuiApretado.getSkillElement().getDisplay().getFrame().equals(
					ScrollableSkillFrameType.TASK) /* skillEntryGuiApretado.getSkillElement().getParent() != null */
					&& !skillEntryGuiApretado.isDisabled()) {
				isDraggingToPowerButton = true;
				posicionMouseX = mouseX - skillEntryGuiApretado.getX() - GuiSkillEntry.FRAME_SIZE / ((double) 2);
				posicionMouseY = mouseY - skillEntryGuiApretado.getY() - GuiSkillEntry.FRAME_SIZE / ((double) 2);
				return true;
			}
			else
				skillEntryGuiApretado = null;
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
		if (state == ButtonMouse.RIGHT_BUTTON && skillEntryGuiApretado != null && skillEntryGuiApretado.getSkillElement().getParent() != null) { // botón derecho
			DevilRpg.LOGGER.info("|----------- rightMouseReleases: {}", skillEntryGuiApretado.getSkillElement().getSkillCapability());

			CustomSkillButton copy = powerButtonList.stream().filter(x -> x.isInside(mouseX, mouseY)).findAny().orElse(null);
			if (copy != null) {
				copy.setButtonTexture(skillEntryGuiApretado.getDisplayInfo().getImage());
				HashMap<PowerEnum, SkillEnum> powerNames = skillCap.map(IBasePlayerSkillCapability::getSkillsNameOfPowers).orElse(null);
				if (powerNames != null) {
					powerNames.put((PowerEnum) copy.getEnum(), skillEntryGuiApretado.getSkillElement().getSkillCapability());
					skillCap.ifPresent(x -> x.setSkillsNameOfPowers(powerNames, player));
					addPowerButtons();
					loadAssignedPowerButtons();
				}
			}
			isDraggingToPowerButton = false;
			skillEntryGuiApretado = null;
		}
		return returned;
	}

	@Override
	public void mouseMoved(double mouseX, double mouseY) {
		// super.mouseMoved(mouseX, mouseY);
		this.mouseX = mouseX;
		this.mouseY = mouseY;
	}

	@SuppressWarnings("deprecation")
	public void renderWindow(MatrixStack matrixStack) {
		RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
		RenderSystem.enableBlend();
		// Pinta la pantalla exterior
		this.minecraft.getTextureManager().bind(WINDOW_LOCATION);
		AbstractGui.blit(matrixStack, offsetLeft, offsetTop, 0, 0, INITIAL_WIDTH, INITIAL_HEIGHT + INFO_SPACE,
				INITIAL_TEXTURE_WIDTH, INITIAL_TEXTURE_HEIGHT);

		if (this.tabs.size() > 1) {
			this.minecraft.getTextureManager().bind(TABS_LOCATION);

			// Pinta todas las pestañas, tanto la seleccionada como las no seleccionadas
			for (GuiSkillTab skillTabGui : this.tabs.values()) {
				if (skillTabGui.getPage() == tabPage)
					skillTabGui.drawTab(matrixStack, offsetLeft, offsetTop, skillTabGui == this.selectedTab);
			}

		
			RenderSystem.enableRescaleNormal();
			RenderSystem.defaultBlendFunc();
			// Pinta el ícono o la imagen de la pestaña (tab)
			int k = 0;
			for (GuiSkillTab guiSkillTab : this.tabs.values()) {
				if (guiSkillTab.getPage() == tabPage) {
					//guiSkillTab.drawIcon(offsetLeft, offsetTop, this.itemRenderer);
					guiSkillTab.drawIconImage(matrixStack,offsetLeft, offsetTop);
				}
			}
			RenderSystem.disableBlend();

			// Pinta el título
			this.font.draw(matrixStack, GUI_LABEL, (offsetLeft + 8), (offsetTop + 6), 4210752);

			int unspentPoints = expCap.map(y -> y.getUnspentPoints()).orElse(-1);
			if (unspentPoints != 0) {
				StringTextComponent unspentSkillHolder = new StringTextComponent("");
				unspentSkillHolder.append(UNSPENT_LABEL);
				unspentSkillHolder.append("" + unspentPoints);
				//Pinta puntos sin usar
				this.font.draw(matrixStack, unspentSkillHolder, (offsetLeft + 8), (offsetTop + INITIAL_HEIGHT), 4210752);
			}

		}

		// Pinta coordenadas de mouse (DEBUG)
		/*this.font.draw(matrixStack,
				new StringTextComponent(
						"x: " + (offsetLeft + WINDOW_AREA_OFFSET_X) + " y: " + (offsetTop + WINDOW_AREA_OFFSET_Y)),
				(float) (offsetLeft + 14), (float) (offsetTop + 18), 4210752);

		this.font.draw(matrixStack, new StringTextComponent("offsetLeft: " + offsetLeft + " offsetTop: " + offsetTop),
				(float) (offsetLeft + 14), (float) (offsetTop + 28), 4210752);

		this.font.draw(matrixStack,
				new StringTextComponent("I width: " + (offsetLeft + WINDOW_AREA_OFFSET_X + INNER_SCREEN_WIDTH)
						+ " I height: " + (offsetTop + WINDOW_AREA_OFFSET_Y + INNER_SCREEN_HEIGHT)),
				(float) (offsetLeft + 14), (float) (offsetTop + 38), 4210752);

		
		this.font.draw(matrixStack, new StringTextComponent("mouseX: " + mouseX + " mouseY: " + mouseY),
				(float) (offsetLeft + 14), (float) (offsetTop + 48), 4210752);

		this.font.draw(matrixStack,
				new StringTextComponent(
						"ScrollX: " + selectedTab.getScrollX() + " ScrollY: " + selectedTab.getScrollY()),
				(float) (offsetLeft + 14), (float) (offsetTop + 58), 4210752);*/

	}

	/**
	 * Pinta el fondo incluyendo los botones de las skills
	 * 
	 * @param matrixStack
	 * @param mouseX
	 * @param mouseY

	 */
	@SuppressWarnings("deprecation")
	private void renderInside(MatrixStack matrixStack, int mouseX, int mouseY) {
		GuiSkillTab selectedSkillTabGui = this.selectedTab;
		// Pinta el fondo vacío cuando no hay elementos
		if (selectedSkillTabGui == null) {
			fill(matrixStack, offsetLeft + WINDOW_AREA_OFFSET_X, offsetTop + WINDOW_AREA_OFFSET_Y,
					offsetLeft + WINDOW_AREA_OFFSET_X + INNER_SCREEN_WIDTH,
					offsetTop + WINDOW_AREA_OFFSET_Y + INNER_SCREEN_HEIGHT, -16777216);
			int i = offsetLeft + WINDOW_AREA_OFFSET_X + 117;
			drawCenteredString(matrixStack, this.font, EMPTY, i,
					offsetTop + WINDOW_AREA_OFFSET_Y + 56 - WINDOW_AREA_OFFSET_X / 2, -1);
			drawCenteredString(matrixStack, this.font, SAD_LABEL, i,
					offsetTop + WINDOW_AREA_OFFSET_Y + INNER_SCREEN_HEIGHT - WINDOW_AREA_OFFSET_X, -1);
		} else {
			// Pinta el fondo con elementos
			RenderSystem.pushMatrix();
			// Se posiciona al inicio de la ventana + offsets
			RenderSystem.translatef((float) (offsetLeft + WINDOW_AREA_OFFSET_X),
					(float) (offsetTop + WINDOW_AREA_OFFSET_Y), 0.0F);
			// Pinta el fondo del tab
			selectedSkillTabGui.drawContents(matrixStack);
			RenderSystem.popMatrix();
			RenderSystem.depthFunc(515);
			RenderSystem.disableDepthTest();
		}
	}

	@SuppressWarnings("deprecation")
	private void renderTooltips(MatrixStack matrixStack, int mouseX, int mouseY) {
		RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
		// pinta los tooltips de los botones
		if (this.selectedTab != null) {
			RenderSystem.pushMatrix();
			RenderSystem.enableDepthTest();
			RenderSystem.translatef((float) (offsetLeft + WINDOW_AREA_OFFSET_X),
					(float) (offsetTop + WINDOW_AREA_OFFSET_Y), 400.0F);
			this.selectedTab.drawTabTooltips(matrixStack, mouseX - offsetLeft - WINDOW_AREA_OFFSET_X,
					mouseY - offsetTop - WINDOW_AREA_OFFSET_Y, offsetLeft, offsetTop);
			RenderSystem.disableDepthTest();
			RenderSystem.popMatrix();
		}

		// Pinta los tooltips de las pestañas
		if (this.tabs.size() > 1) {
			for (GuiSkillTab skillTabGui : this.tabs.values()) {
				if (skillTabGui.getPage() == tabPage
						&& skillTabGui.isInsideTabSelector(offsetLeft, offsetTop, (double) mouseX, (double) mouseY)) {
					this.renderTooltip(matrixStack, skillTabGui.getTitle(), mouseX, mouseY);
				}
			}
		}

	}

	public boolean isInsideInnerFrame(double coordX, double coordY, int frameWidth, int frameHeight) {
		return coordX + frameWidth >= 0 && coordY + frameHeight >= 0 && coordX <= (INNER_SCREEN_WIDTH)
				&& coordY <= (INNER_SCREEN_HEIGHT);
	}

	public void rootSkillAdded(SkillElement advancementIn) {
		DevilRpg.LOGGER.info("|-------- rootSkillAdded");
		GuiSkillTab advancementtabgui = GuiSkillTab.create(this.minecraft, this, this.tabs.size(), advancementIn,
				skillCap);
		if (advancementtabgui != null) {
			this.tabs.put(advancementIn, advancementtabgui);
		}
	}

	public void rootSkillRemoved(SkillElement advancementIn) {
		DevilRpg.LOGGER.info("|-------- rootSkillRemoved");
		GuiSkillTab advancementtabgui = GuiSkillTab.create(this.minecraft, this, this.tabs.size(), advancementIn,
				skillCap);
		if (advancementtabgui != null) {
			this.tabs.remove(advancementIn, advancementtabgui);
		}
	}

	/**
	 * Agrega las hojas del nodo raiz
	 */
	public void nonRootSkillAdded(SkillElement advancementIn) {
		DevilRpg.LOGGER.info("|-------- nonRootSkillAdded");
		GuiSkillTab advancementtabgui = this.getTab(advancementIn);
		if (advancementtabgui != null) {
			advancementtabgui.addSkillElement(advancementIn);
		}

	}

	public void nonRootSkillRemoved(SkillElement skillElementIn) {
		DevilRpg.LOGGER.info("|-------- nonRootSkillRemoved");
		GuiSkillTab advancementtabgui = this.getTab(skillElementIn);
		if (advancementtabgui != null) {
			advancementtabgui.removeSkillElement(skillElementIn);
		}
	}

	public void onUpdateAdvancementProgress(SkillElement skillElementIn, SkillProgress progress) {
		GuiSkillEntry skillEntryGui = this.getSkillElementGui(skillElementIn);
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
	public GuiSkillEntry getSkillElementGui(SkillElement skillElement) {
		GuiSkillTab skillTabGui = this.getTab(skillElement);
		return skillTabGui == null ? null : skillTabGui.getSkillElementGui(skillElement);
	}

	@Nullable
	private GuiSkillTab getTab(SkillElement skillElement) {
		while (skillElement.getParent() != null) {
			skillElement = skillElement.getParent();
		}

		return this.tabs.get(skillElement);
	}

	public LazyOptional<IBasePlayerSkillCapability> getSkillCap() {
		return skillCap;
	}

	public void setSkillCap(LazyOptional<IBasePlayerSkillCapability> skillCap) {
		this.skillCap = skillCap;
	}

	public LazyOptional<IBaseExperienceCapability> getExpCap() {
		return expCap;
	}

	public void setExpCap(LazyOptional<IBaseExperienceCapability> expCap) {
		this.expCap = expCap;
	}

	protected void addPowerButtons() {
		int k = 0;

		int offLeft = (this.width - INITIAL_WIDTH) / 2;
		int offTop = (this.height - INITIAL_HEIGHT) / 2;
		List<PowerEnum> powerList = Arrays.asList(PowerEnum.values());
		k = powerList.size();
		for (PowerEnum powerEnum : powerList) {
			int drawnSkillLevel = 0;
			CustomSkillButton powrButtons = new CustomSkillButton(
					//(WINDOW_AREA_OFFSET_X+ offLeft + INNER_SCREEN_WIDTH/8) + (k * (GuiSkillEntry.FRAME_SIZE + 10)),
					(WINDOW_AREA_OFFSET_X+ offLeft +10) + (k * (GuiSkillEntry.FRAME_SIZE + 10)),
					WINDOW_AREA_OFFSET_Y + offTop + INNER_SCREEN_HEIGHT + 2, 
					GuiSkillEntry.FRAME_SIZE-5, // 3
					GuiSkillEntry.FRAME_SIZE-5, // 4
					ClientModKeyInputEventSubscriber.getKeyName(powerEnum), 
					EMPTY_POWER_IMAGE_RESOURCE, 
					GuiSkillEntry.BUTTON_IMAGE_SIZE, // 7
					GuiSkillEntry.BUTTON_IMAGE_SIZE, // 8
					powerEnum, 
					drawnSkillLevel, 
					this::powerButtonPressed, 
					false, 
					7.0F);

			powrButtons.visible = true;

			powerButtonList.add(powrButtons);
			addButton(powrButtons);
			k++;
		}
		
	}

	public void powerButtonPressed(Button pressedButton) {
		DevilRpg.LOGGER.info("--------powerButtonPressed: {} ", pressedButton.getMessage().getContents());
		
		if(pressedButton instanceof CustomSkillButton) {
			CustomSkillButton pb = (CustomSkillButton) pressedButton;
			HashMap<PowerEnum, SkillEnum> powerNames = skillCap.map(IBasePlayerSkillCapability::getSkillsNameOfPowers).orElse(null);
			if (powerNames != null) {
				pb.setButtonTexture(EMPTY_POWER_IMAGE_RESOURCE);
				DevilRpg.LOGGER.info("pb.getEnum(): {} ",pb.getEnum());
				
				powerNames.put((PowerEnum) pb.getEnum(), SkillEnum.EMPTY);
				skillCap.ifPresent(x -> x.setSkillsNameOfPowers(powerNames, player));
			
			}
		}
		addPowerButtons();
		loadAssignedPowerButtons();
	}

	protected void loadAssignedPowerButtons() {
		DevilRpg.LOGGER.info("---------loadAssignedPowerButtons ");
		HashMap<PowerEnum, SkillEnum> powerToSkillDictionary = skillCap.map(x -> x.getSkillsNameOfPowers()).orElse(null);

		if (powerToSkillDictionary != null) {
			for (CustomSkillButton c : powerButtonList) {
				PowerEnum powerEnumFromButton = (PowerEnum) c.getEnum();
				SkillEnum aSkillEnum = powerToSkillDictionary.getOrDefault(powerEnumFromButton,SkillEnum.EMPTY);
				if(aSkillEnum!= null) {
					if(!aSkillEnum.equals(SkillEnum.EMPTY)) {
						c.setButtonTexture(skillsImages.get(aSkillEnum));
					}
					else {
						c.setButtonTexture(EMPTY_POWER_IMAGE_RESOURCE);
					}
				}
			}
		}
	}

	@Override
	public <T extends Widget> T addButton(T widget) {
		super.buttons.add(widget);
		return super.addWidget(widget);
	}

	public Map<SkillEnum, ResourceLocation> getSkillsResourceLocations() {
		return skillsImages;
	}

	public void playDownSound(SoundHandler p_230988_1_) {
		p_230988_1_.play(SimpleSound.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}
}
