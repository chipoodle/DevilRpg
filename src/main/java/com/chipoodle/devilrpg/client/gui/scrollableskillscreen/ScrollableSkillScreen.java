package com.chipoodle.devilrpg.client.gui.scrollableskillscreen;

import java.util.Map;

import javax.annotation.Nullable;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.client.gui.scrollableskillscreen.model.ClientScrollableSkillManager;
import com.google.common.collect.Maps;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.IGuiEventListener;
import net.minecraft.client.gui.chat.NarratorChatListener;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.network.play.ClientPlayNetHandler;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ScrollableSkillScreen extends Screen implements ClientScrollableSkillManager.IListener {
	private static final int WINDOW_AREA_OFFSET_Y = 18;
	private static final int WINDOW_AREA_OFFSET_X = 9;
	// private static final int INITIAL_HEIGHT = 140;
	// private static final int INITIAL_WIDTH = 252;
	private static final int INITIAL_HEIGHT = 190;
	private static final int INITIAL_WIDTH = 302;
	private static final ResourceLocation WINDOW = new ResourceLocation(
			DevilRpg.MODID + ":textures/gui/window-256a.png");
	// private static final ResourceLocation WINDOW = new
	// ResourceLocation("textures/gui/advancements/window.png");
	private static final ResourceLocation TABS = new ResourceLocation("textures/gui/advancements/tabs.png");
	private static final ITextComponent SAD_LABEL = new TranslationTextComponent("advancements.sad_label");
	private static final ITextComponent EMPTY = new TranslationTextComponent("advancements.empty");
	private static final ITextComponent GUI_LABEL = new TranslationTextComponent("gui.advancements");
	private final ClientScrollableSkillManager clientSkillManager;
	private final Map<SkillElement, SkillTabGui> tabs = Maps.newLinkedHashMap();
	private SkillTabGui selectedTab;
	private boolean isScrolling;
	private static int tabPage, maxPages;

	private int openScreenKeyPressed;

	public ScrollableSkillScreen() {
		super(NarratorChatListener.EMPTY);
		ClientScrollableSkillManager skillManager = new ClientScrollableSkillManager(Minecraft.getInstance());
		skillManager.buildSkillTrees();
		this.clientSkillManager = skillManager;
	}

	public ScrollableSkillScreen(int keyCode) {
		this();
		openScreenKeyPressed = keyCode;
	}

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
		if (this.tabs.size() > ScrollableSkillTabType.MAX_TABS) {
			int guiLeft = (this.width - INITIAL_WIDTH) / 2;
			int guiTop = (this.height - INITIAL_HEIGHT) / 2;
			addButton(new net.minecraft.client.gui.widget.button.Button(guiLeft, guiTop - 50, 20, 20,
					new net.minecraft.util.text.StringTextComponent("<"), b -> tabPage = Math.max(tabPage - 1, 0)));
			addButton(new net.minecraft.client.gui.widget.button.Button(guiLeft + INITIAL_WIDTH - 20, guiTop - 50, 20,
					20, new net.minecraft.util.text.StringTextComponent(">"),
					b -> tabPage = Math.min(tabPage + 1, maxPages)));
			maxPages = this.tabs.size() / ScrollableSkillTabType.MAX_TABS;
		}
	}

	public void onClose() {
		this.clientSkillManager.setListener((ClientScrollableSkillManager.IListener) null);
		ClientPlayNetHandler clientplaynethandler = this.minecraft.getConnection();
		if (clientplaynethandler != null) {
			// clientplaynethandler.sendPacket(CSeenAdvancementsPacket.closedScreen());
		}

	}

	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		/*
		 * if (this.minecraft.gameSettings.keyBindAdvancements.matchesKey(keyCode,
		 * scanCode)) { this.minecraft.displayGuiScreen((Screen) null);
		 * this.minecraft.mouseHelper.grabMouse(); return true; } else { return
		 * super.keyPressed(keyCode, scanCode, modifiers); }
		 */

		if (openScreenKeyPressed == keyCode) {
			this.closeScreen();
			return true;
		} else
			return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@SuppressWarnings("deprecation")
	public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
		int i = (this.width - INITIAL_WIDTH) / 2;
		int j = (this.height - INITIAL_HEIGHT) / 2;
		this.renderBackground(matrixStack);
		if (maxPages != 0) {
			ITextComponent page = new StringTextComponent(String.format("%d / %d", tabPage + 1, maxPages + 1));
			int width = this.font.getStringPropertyWidth(page);
			RenderSystem.disableLighting();
			this.font.func_238407_a_(matrixStack, page.func_241878_f(), i + (INITIAL_WIDTH / 2) - (width / 2), j - 44,
					-1);
		}
		this.drawWindowBackground(matrixStack, mouseX, mouseY, i, j);
		this.renderWindow(matrixStack, i, j);
		this.drawWindowTooltips(matrixStack, mouseX, mouseY, i, j);
	}

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			int i = (this.width - INITIAL_WIDTH) / 2;
			int j = (this.height - INITIAL_HEIGHT) / 2;

			for (SkillTabGui rootSkillTabGui : this.tabs.values()) {
				if (rootSkillTabGui.getPage() == tabPage) {
					DevilRpg.LOGGER.info("|----------- tabPage: "+tabPage);
					if (rootSkillTabGui.isInsideTabSelector(i, j, mouseX, mouseY)) {
						this.clientSkillManager.setSelectedTab(rootSkillTabGui.getSkillElement(), true);
						break;
					} else {
						SkillEntryGui ifInsideAnyChild = selectedTab.getIfInsideAnyChild(mouseX - i - WINDOW_AREA_OFFSET_X,
								mouseY - j - WINDOW_AREA_OFFSET_Y);
						if (ifInsideAnyChild != null) {
							DevilRpg.LOGGER.info("|----------- mouseClicked: " + ifInsideAnyChild.getSkillElement().getId());
							break;
						}
					}
				}
			}
		}

		return super.mouseClicked(mouseX, mouseY, button);
	}

	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (button != 0) {
			this.isScrolling = false;
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
	private void drawWindowBackground(MatrixStack matrixStack, int mouseX, int mouseY, int offsetX, int offsetY) {
		SkillTabGui selectedSkillTabGui = this.selectedTab;
		// Pinta el fondo vacío cuando no hay elementos
		if (selectedSkillTabGui == null) {
			fill(matrixStack, offsetX + WINDOW_AREA_OFFSET_X, offsetY + WINDOW_AREA_OFFSET_Y,
					offsetX + WINDOW_AREA_OFFSET_X + 234, offsetY + WINDOW_AREA_OFFSET_Y + 113, -16777216);
			int i = offsetX + WINDOW_AREA_OFFSET_X + 117;
			drawCenteredString(matrixStack, this.font, EMPTY, i,
					offsetY + WINDOW_AREA_OFFSET_Y + 56 - WINDOW_AREA_OFFSET_X / 2, -1);
			drawCenteredString(matrixStack, this.font, SAD_LABEL, i,
					offsetY + WINDOW_AREA_OFFSET_Y + 113 - WINDOW_AREA_OFFSET_X, -1);
		} else {
			// Pinta el fondo con elementos
			RenderSystem.pushMatrix();
			// Se posiciona al inicio de la ventana + offsets
			RenderSystem.translatef((float) (offsetX + WINDOW_AREA_OFFSET_X), (float) (offsetY + WINDOW_AREA_OFFSET_Y),
					0.0F);
			selectedSkillTabGui.drawTabBackground(matrixStack);
			RenderSystem.popMatrix();
			RenderSystem.depthFunc(515);
			RenderSystem.disableDepthTest();
		}
	}

	@SuppressWarnings("deprecation")
	public void renderWindow(MatrixStack matrixStack, int offsetX, int offsetY) {
		RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
		RenderSystem.enableBlend();
		this.minecraft.getTextureManager().bindTexture(WINDOW);
		AbstractGui.blit(matrixStack, offsetX, offsetY, 0, 0, INITIAL_WIDTH, INITIAL_HEIGHT, 308, 256);

		if (this.tabs.size() > 1) {
			this.minecraft.getTextureManager().bindTexture(TABS);

			for (SkillTabGui skillTabGui : this.tabs.values()) {
				if (skillTabGui.getPage() == tabPage)
					skillTabGui.renderTabSelectorBackground(matrixStack, offsetX, offsetY,
							skillTabGui == this.selectedTab);
			}

			RenderSystem.enableRescaleNormal();
			RenderSystem.defaultBlendFunc();

			// Pinta la imagen del tabulador
			for (SkillTabGui advancementtabgui1 : this.tabs.values()) {
				if (advancementtabgui1.getPage() == tabPage)
					advancementtabgui1.drawIcon(offsetX, offsetY, this.itemRenderer);
			}

			RenderSystem.disableBlend();
		}

		this.font.func_243248_b(matrixStack, GUI_LABEL, (float) (offsetX + 8), (float) (offsetY + 6), 4210752);
	}

	@SuppressWarnings("deprecation")
	private void drawWindowTooltips(MatrixStack matrixStack, int mouseX, int mouseY, int offsetX, int offsetY) {
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
			for (SkillTabGui skillTabGui : this.tabs.values()) {
				if (skillTabGui.getPage() == tabPage
						&& skillTabGui.isInsideTabSelector(offsetX, offsetY, (double) mouseX, (double) mouseY)) {
					this.renderTooltip(matrixStack, skillTabGui.getTitle(), mouseX, mouseY);
				}
			}
		}

	}

	public void rootAdvancementAdded(SkillElement advancementIn) {
		SkillTabGui advancementtabgui = SkillTabGui.create(this.minecraft, this, this.tabs.size(), advancementIn);
		if (advancementtabgui != null) {
			this.tabs.put(advancementIn, advancementtabgui);
		}
	}

	public void rootAdvancementRemoved(SkillElement advancementIn) {
	}

	public void nonRootAdvancementAdded(SkillElement advancementIn) {
		DevilRpg.LOGGER.info("|-------- nonRootAdvancementAdded");
		SkillTabGui advancementtabgui = this.getTab(advancementIn);
		if (advancementtabgui != null) {
			advancementtabgui.addSkillElement(advancementIn);
		}

	}

	public void nonRootAdvancementRemoved(SkillElement skillElementIn) {
	}

	public void onUpdateAdvancementProgress(SkillElement skillElementIn, SkillProgress progress) {
		SkillEntryGui skillEntryGui = this.getSkillElementGui(skillElementIn);
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
	public SkillEntryGui getSkillElementGui(SkillElement skillElement) {
		SkillTabGui skillTabGui = this.getTab(skillElement);
		return skillTabGui == null ? null : skillTabGui.getSkillElementGui(skillElement);
	}

	@Nullable
	private SkillTabGui getTab(SkillElement skillElement) {
		while (skillElement.getParent() != null) {
			skillElement = skillElement.getParent();
		}

		return this.tabs.get(skillElement);
	}

	public <T extends Widget> T addButton(T button) {
		this.buttons.add(button);
		return this.addListener(button);
	}
}
