package com.chipoodle.devilrpg.client.gui.scrollableskillscreen;

import java.awt.TextComponent;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.experience.IBaseExperienceCapability;
import com.chipoodle.devilrpg.capability.experience.PlayerExperienceCapabilityProvider;
import com.chipoodle.devilrpg.capability.skill.IBaseSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityProvider;
import com.chipoodle.devilrpg.client.gui.scrollableskillscreen.model.ClientSkillBuilder;
import com.chipoodle.devilrpg.client.gui.skillbook.CustomGuiButton;
import com.chipoodle.devilrpg.util.PowerEnum;
import com.chipoodle.devilrpg.util.SkillEnum;
import com.google.common.collect.Maps;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.chat.NarratorChatListener;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.client.util.InputMappings;
import net.minecraft.client.util.InputMappings.Input;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.util.LazyOptional;

@OnlyIn(Dist.CLIENT)
public class ScrollableSkillScreen extends Screen implements ClientSkillBuilder.IListener {
	private static final int INITIAL_TEXTURE_HEIGHT = 256;
	private static final int INITIAL_TEXTURE_WIDTH = 308;
	private static final int INNER_SCREEN_WIDTH = 282;
	private static final int INNER_SCREEN_HEIGHT = 162;
	private static final int WINDOW_AREA_OFFSET_X = 10;
	private static final int WINDOW_AREA_OFFSET_Y = 18;
	private static final int INITIAL_WIDTH = 302;
	private static final int INITIAL_HEIGHT = 190;
	private static final int INFO_SPACE = 20;
	// private static final int INITIAL_HEIGHT = 220;
	// private static final ResourceLocation WINDOW = new
	// ResourceLocation("textures/gui/advancements/window.png");
	private static final ResourceLocation WINDOW = new ResourceLocation(
			DevilRpg.MODID + ":textures/gui/window-256b.png");
	private static final ResourceLocation TABS = new ResourceLocation("textures/gui/advancements/tabs.png");
	private static final ITextComponent SAD_LABEL = new TranslationTextComponent("advancements.sad_label");
	private static final ITextComponent EMPTY = new TranslationTextComponent("advancements.empty");
	private static final ITextComponent GUI_LABEL = new TranslationTextComponent("gui.skills.title");
	private static final ITextComponent UNSPENT_LABEL = new TranslationTextComponent("gui.skills.unspent");
	private final ClientSkillBuilder clientSkillManager;
	private final Map<SkillElement, GuiSkillTab> tabs = Maps.newLinkedHashMap();
	private GuiSkillTab selectedTab;
	private boolean isScrolling;
	private boolean isDraggingPowerButton;
	private static int tabPage, maxPages;

	private double mouseX;
	private double mouseY;
	private int offsetX;
	private int offsetY;

	private Input openScreenKeyPressed;
	private static int ESC_KEY = 256;

	private PlayerEntity player;
	private LazyOptional<IBaseSkillCapability> skillCap;
	private LazyOptional<IBaseExperienceCapability> expCap;

	class ButtonMouse {
		public static final int LEFT_BUTTON = 0;
		public static final int RIGHT_BUTTON = 1;
	}

	StringTextComponent unspentSkillHolder;
	private GuiSkillEntry skillEntryGuiApretado;
	private double posicionMouseX;
	private double posicionMouseY;

	private ScrollableSkillScreen() {
		super(NarratorChatListener.NO_TITLE);
		ClientSkillBuilder skillManager = new ClientSkillBuilder(Minecraft.getInstance());
		skillManager.buildSkillTrees();
		this.clientSkillManager = skillManager;
		unspentSkillHolder = new StringTextComponent("");
		isDraggingPowerButton = false;
		skillEntryGuiApretado = null;
	}

	public ScrollableSkillScreen(Input input) {
		this();
		openScreenKeyPressed = input;
		this.player = Minecraft.getInstance().player;
		expCap = player.getCapability(PlayerExperienceCapabilityProvider.EXPERIENCE_CAP);
		skillCap = player.getCapability(PlayerSkillCapabilityProvider.SKILL_CAP);
		DevilRpg.LOGGER.info("ScrollableSkillScreen consturctor. openScreenKeyPressed: {}", openScreenKeyPressed);
	}

	protected void init() {
		this.tabs.clear();
		this.selectedTab = null;
		this.clientSkillManager.setListener(this);
		if (this.selectedTab == null && !this.tabs.isEmpty()) {
			this.clientSkillManager.setSelectedTab(this.tabs.values().iterator().next().getSkillElement(), true);
		} else {
			this.clientSkillManager.setSelectedTab(this.selectedTab == null ? null : this.selectedTab.getSkillElement(),true);
		}
		if (this.tabs.size() > SkillTabType.MAX_TABS) {
			int guiLeft = (this.width - INITIAL_WIDTH) / 2;
			int guiTop = (this.height - INITIAL_HEIGHT) / 2;
			//pinta boton <
			addButton(new net.minecraft.client.gui.widget.button.Button(guiLeft, guiTop - 50, 20, 20,new net.minecraft.util.text.StringTextComponent("<"), b -> tabPage = Math.max(tabPage - 1, 0)));
			//pinta boton >
			addButton(new net.minecraft.client.gui.widget.button.Button(guiLeft + INITIAL_WIDTH - 20, guiTop - 50, 20,20, new net.minecraft.util.text.StringTextComponent(">"),b -> tabPage = Math.min(tabPage + 1, maxPages)));
			maxPages = this.tabs.size() / SkillTabType.MAX_TABS;
		}
	}

	public void onClose() {
		this.clientSkillManager.setListener((ClientSkillBuilder.IListener) null);
		super.onClose();
	}

	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		Input pressedKeyCode = InputMappings.Type.KEYSYM.getOrCreate(keyCode);
		if (openScreenKeyPressed.getName().equals(pressedKeyCode.getName())) {
			this.onClose();
			return true;
		} else
			return super.keyPressed(pressedKeyCode.getValue(), scanCode, modifiers);
	}

	@SuppressWarnings("deprecation")
	public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
		offsetX = (this.width - INITIAL_WIDTH) / 2;
		offsetY = (this.height - INITIAL_HEIGHT) / 2;
		this.renderBackground(matrixStack);
		if (maxPages != 0) {
			ITextComponent page = new StringTextComponent(String.format("%d / %d", tabPage + 1, maxPages + 1));
			int width = this.font.width(page);
			RenderSystem.disableLighting();
			this.font.drawShadow(matrixStack, page.getVisualOrderText(), offsetX + (INITIAL_WIDTH / 2) - (width / 2),offsetY - 44, -1);
		}
		this.drawWindowBackground(matrixStack, mouseX, mouseY);
		this.renderWindow(matrixStack);
		this.drawWindowTooltips(matrixStack, mouseX, mouseY);
	}

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == ButtonMouse.LEFT_BUTTON) {
			for (GuiSkillTab rootSkillTabGui : this.tabs.values()) {
				if (rootSkillTabGui.getPage() == tabPage) {
					if (rootSkillTabGui.isInsideTabSelector(offsetX, offsetY, mouseX, mouseY)) {
						this.clientSkillManager.setSelectedTab(rootSkillTabGui.getSkillElement(), true);
						break;
					} else {
						GuiSkillEntry skillEntryGui = selectedTab.getIfInsideIncludingChildren(mouseX - offsetX - WINDOW_AREA_OFFSET_X, mouseY - offsetY - WINDOW_AREA_OFFSET_Y);
						if (skillEntryGui != null) {
							DevilRpg.LOGGER.info("|----------- mouseClicked: "+ skillEntryGui.getSkillElement().getSkillCapability());
							skillButtonPressed(skillEntryGui);
							break;
						}
					}
				}
			}
		}

		return super.mouseClicked(mouseX, mouseY, button);
	}

	public void skillButtonPressed(GuiSkillEntry skillEntryGui) {
		SkillEnum skilEnum = skillEntryGui.getSkillElement().getSkillCapability();
		if (!skilEnum.equals(SkillEnum.EMPTY)) {
			skillCap.ifPresent(skillCap -> {
				HashMap<SkillEnum, Integer> skillsPoints = skillCap.getSkillsPoints();
				HashMap<SkillEnum, Integer> skillsMaxPoints = skillCap.getMaxSkillsPoints();
				Integer points = skillsPoints.get(skilEnum);
				Integer maxPoints = skillsMaxPoints.get(skilEnum);
				if (points < maxPoints) {
					points += expCap.map(x -> x.consumePoint()).orElse(0);
					skillsPoints.put(skilEnum, points);
					skillCap.setSkillsPoints(skillsPoints, player);
					skillEntryGui.updateFormattedLevelString(points, maxPoints);
				}

			});
		}
	}

	/**
	 * Se dispara cuando se está hiciendro drag con el mouse
	 */
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (button == ButtonMouse.RIGHT_BUTTON) {
			this.isScrolling = false;
			this.posicionMouseX = mouseX;
			this.posicionMouseY = mouseY;
			
			if (!isDraggingPowerButton) {
				skillEntryGuiApretado = selectedTab.getIfInsideIncludingChildren(mouseX - offsetX - WINDOW_AREA_OFFSET_X, mouseY - offsetY - WINDOW_AREA_OFFSET_Y);
			}
			if (skillEntryGuiApretado != null) {
				isDraggingPowerButton = true;
				DevilRpg.LOGGER.info("|----------- rightMouseDragged: " + skillEntryGuiApretado.getSkillElement().getSkillCapability());
				
				return true;
			}

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
		if (state == ButtonMouse.RIGHT_BUTTON) { // botón derecho
			if (skillEntryGuiApretado != null) {
				DevilRpg.LOGGER.info("|----------- rightMouseReleases: " + skillEntryGuiApretado.getSkillElement().getSkillCapability());
				/*CustomGuiButton copy = powerButtonList.stream().filter(x -> x.isInside(mouseX, mouseY)).findAny().orElse(null);
				if (copy != null) {
					copy.setButtonTexture(skillButtonApretado.getButtonTexture());
					HashMap<PowerEnum, SkillEnum> powerNames = skillCap.map(x -> x.getSkillsNameOfPowers()).orElse(null);
					powerNames.put((PowerEnum) copy.getSkillName(), (SkillEnum) skillButtonApretado.getSkillName());
					skillCap.ifPresent(x -> x.setSkillsNameOfPowers(powerNames, player));
				}*/
				isDraggingPowerButton = false;
				skillEntryGuiApretado = null;
			}
		}
		return returned;
	}

	@Override
	public void mouseMoved(double xPos, double mouseY) {
		super.mouseMoved(xPos, mouseY);
		this.mouseX = xPos;
		this.mouseY = mouseY;
	}

	@SuppressWarnings("deprecation")
	public void renderWindow(MatrixStack matrixStack) {
		RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
		RenderSystem.enableBlend();
		this.minecraft.getTextureManager().bind(WINDOW);

		// Pinta la pantalla exterior
		// AbstractGui.blit(matrixStack, offsetX, offsetY, 0, 0, INITIAL_WIDTH,
		// INITIAL_HEIGHT, INITIAL_TEXTURE_WIDTH, INITIAL_TEXTURE_HEIGHT);
		AbstractGui.blit(matrixStack, offsetX, offsetY, 0, 0, INITIAL_WIDTH, INITIAL_HEIGHT + INFO_SPACE,INITIAL_TEXTURE_WIDTH, INITIAL_TEXTURE_HEIGHT);

		if (this.tabs.size() > 1) {
			this.minecraft.getTextureManager().bind(TABS);

			for (GuiSkillTab skillTabGui : this.tabs.values()) {
				if (skillTabGui.getPage() == tabPage)
					skillTabGui.renderTabSelectorBackground(matrixStack, offsetX, offsetY,
							skillTabGui == this.selectedTab);
			}

			RenderSystem.enableRescaleNormal();
			RenderSystem.defaultBlendFunc();

			// Pinta la imagen de la pestaña
			for (GuiSkillTab advancementtabgui1 : this.tabs.values()) {
				if (advancementtabgui1.getPage() == tabPage)
					advancementtabgui1.drawIcon(offsetX, offsetY, this.itemRenderer);
			}

			RenderSystem.disableBlend();
		}

		 this.font.draw(matrixStack, GUI_LABEL, (float) (offsetX + 8), (float) (offsetY + 6), 4210752);

		int unspentPoints = expCap.map(y -> y.getUnspentPoints()).orElse(-1);
		if (unspentPoints != 0) {
			/*unspentSkillHolder.append(UNSPENT_LABEL);
			unspentSkillHolder.appendString("" + unspentPoints);
			this.font.drawText(matrixStack, unspentSkillHolder, (float) (offsetX + 8),
					(float) (offsetY + INITIAL_HEIGHT), 4210752);*/
		}

		// Pinta coordenadas de mouse (DEBUG)
		this.font.draw(matrixStack,
				new StringTextComponent(
						"x: " + (offsetX + WINDOW_AREA_OFFSET_X) + " y: " + (offsetY + WINDOW_AREA_OFFSET_Y)),
				(float) (offsetX + 14), (float) (offsetY + 18), 4210752);

		this.font.draw(matrixStack, new StringTextComponent("offsetX: " + offsetX + " offsetY: " + offsetY),
				(float) (offsetX + 14), (float) (offsetY + 28), 4210752);

		this.font.draw(matrixStack,
				new StringTextComponent("I width: " + (offsetX + WINDOW_AREA_OFFSET_X + INNER_SCREEN_WIDTH) + " I height: "
						+ (offsetY + WINDOW_AREA_OFFSET_Y + INNER_SCREEN_HEIGHT)),
				(float) (offsetX + 14), (float) (offsetY + 38), 4210752);
		 
		/*if (isInsideInnerFrame(mouseX, mouseY,14,48))
			this.font.drawText(matrixStack, new StringTextComponent("mouseX: " + mouseX + " mouseY: " + mouseY),(float) (offsetX + 14), (float) (offsetY + 48), 4210752);
		*/
	}

	/**
	 * Pinta el fondo incluyendo los botones de las skills
	 * 
	 * @param matrixStack
	 * @param mouseX
	 * @param mouseY
	 * @param offsetX
	 * @param offsetY
	 */
	@SuppressWarnings("deprecation")
	private void drawWindowBackground(MatrixStack matrixStack, int mouseX, int mouseY) {
		GuiSkillTab selectedSkillTabGui = this.selectedTab;
		// Pinta el fondo vacío cuando no hay elementos
		if (selectedSkillTabGui == null) {
			fill(matrixStack, offsetX + WINDOW_AREA_OFFSET_X, offsetY + WINDOW_AREA_OFFSET_Y,
					offsetX + WINDOW_AREA_OFFSET_X + INNER_SCREEN_WIDTH, offsetY + WINDOW_AREA_OFFSET_Y + INNER_SCREEN_HEIGHT,-16777216);
			int i = offsetX + WINDOW_AREA_OFFSET_X + 117;
			drawCenteredString(matrixStack, this.font, EMPTY, i,offsetY + WINDOW_AREA_OFFSET_Y + 56 - WINDOW_AREA_OFFSET_X / 2, -1);
			drawCenteredString(matrixStack, this.font, SAD_LABEL, i,offsetY + WINDOW_AREA_OFFSET_Y + INNER_SCREEN_HEIGHT - WINDOW_AREA_OFFSET_X, -1);
		} else {
			// Pinta el fondo con elementos
			RenderSystem.pushMatrix();
			// Se posiciona al inicio de la ventana + offsets
			RenderSystem.translatef((float) (offsetX + WINDOW_AREA_OFFSET_X), (float) (offsetY + WINDOW_AREA_OFFSET_Y),
					0.0F);
			//Pinta el fondo del tab
			selectedSkillTabGui.drawTabBackground(matrixStack);
			RenderSystem.popMatrix();
			RenderSystem.depthFunc(515);
			RenderSystem.disableDepthTest();
		}
	}

	@SuppressWarnings("deprecation")
	private void drawWindowTooltips(MatrixStack matrixStack, int mouseX, int mouseY) {
		RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
		if (this.selectedTab != null) {
			RenderSystem.pushMatrix();
			RenderSystem.enableDepthTest();
			RenderSystem.translatef((float) (offsetX + WINDOW_AREA_OFFSET_X), (float) (offsetY + WINDOW_AREA_OFFSET_Y),
					400.0F);
			this.selectedTab.drawTabTooltips(matrixStack, mouseX - offsetX - WINDOW_AREA_OFFSET_X,
					mouseY - offsetY - WINDOW_AREA_OFFSET_Y, offsetX, offsetY);
			RenderSystem.disableDepthTest();
			RenderSystem.popMatrix();
		}

		if (this.tabs.size() > 1) {
			for (GuiSkillTab skillTabGui : this.tabs.values()) {
				if (skillTabGui.getPage() == tabPage
						&& skillTabGui.isInsideTabSelector(offsetX, offsetY, (double) mouseX, (double) mouseY)) {
					this.renderTooltip(matrixStack, skillTabGui.getTitle(), mouseX, mouseY);
				}
			}
		}

	}

	/*@SuppressWarnings("deprecation")
	private void drawSkillLevel(MatrixStack matrixStack) {
		if (this.selectedTab != null) {
			RenderSystem.pushMatrix();
			RenderSystem.enableDepthTest();
			RenderSystem.translatef((float) (offsetX + WINDOW_AREA_OFFSET_X), (float) (offsetY + WINDOW_AREA_OFFSET_Y),400.0F);

			this.selectedTab.drawSkillLevel(matrixStack);

			RenderSystem.disableDepthTest();
			RenderSystem.popMatrix();
		}
	}*/

	public boolean isInsideInnerFrame(double coordX, double coordY,int frameWidth, int frameHeight) {
		return coordX+frameWidth >= 0 && coordY+frameHeight >= 0 && coordX <= (INNER_SCREEN_WIDTH) && coordY <= (INNER_SCREEN_HEIGHT);
	}

	public void rootSkillAdded(SkillElement advancementIn) {
		GuiSkillTab advancementtabgui = GuiSkillTab.create(this.minecraft, this, this.tabs.size(), advancementIn,
				skillCap);
		if (advancementtabgui != null) {
			this.tabs.put(advancementIn, advancementtabgui);
		}
	}

	public void rootSkillRemoved(SkillElement advancementIn) {
	}

	public void nonRootSkillAdded(SkillElement advancementIn) {
		DevilRpg.LOGGER.info("|-------- nonRootAdvancementAdded");
		GuiSkillTab advancementtabgui = this.getTab(advancementIn);
		if (advancementtabgui != null) {
			advancementtabgui.addSkillElement(advancementIn);
		}

	}

	public void nonRootSkillRemoved(SkillElement skillElementIn) {
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

	public <T extends Widget> T addButton(T button) {
		this.buttons.add(button);
		return this.addWidget(button);
	}

	public LazyOptional<IBaseSkillCapability> getSkillCap() {
		return skillCap;
	}

	public void setSkillCap(LazyOptional<IBaseSkillCapability> skillCap) {
		this.skillCap = skillCap;
	}

	public LazyOptional<IBaseExperienceCapability> getExpCap() {
		return expCap;
	}

	public void setExpCap(LazyOptional<IBaseExperienceCapability> expCap) {
		this.expCap = expCap;
	}
	
	
}
