package com.chipoodle.devilrpg.client.gui.scrollableskillscreen;

import java.util.Map;

import javax.annotation.Nullable;

import com.chipoodle.devilrpg.DevilRpg;
import com.google.common.collect.Maps;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.chat.NarratorChatListener;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.play.ClientPlayNetHandler;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ScrollableSkillScreen extends Screen implements ClientScrollableSkillManager.IListener {
	private static final ResourceLocation WINDOW = new ResourceLocation("textures/gui/advancements/window.png");
	private static final ResourceLocation TABS = new ResourceLocation("textures/gui/advancements/tabs.png");
	private static final ITextComponent SAD_LABEL = new TranslationTextComponent("advancements.sad_label");
	private static final ITextComponent EMPTY = new TranslationTextComponent("advancements.empty");
	private static final ITextComponent GUI_LABEL = new TranslationTextComponent("gui.advancements");
	private final ClientScrollableSkillManager clientSkillManager;
	private final Map<SkillElement, ScrollableSkillTabGui> tabs = Maps.newLinkedHashMap();
	private ScrollableSkillTabGui selectedTab;
	private boolean isScrolling;
	private static int tabPage, maxPages;

	private int pressedOpenScreen;

	public ScrollableSkillScreen() {
		super(NarratorChatListener.EMPTY);
		ClientScrollableSkillManager skillManager = new ClientScrollableSkillManager(Minecraft.getInstance());
		skillManager.buildSkillTrees();
		this.clientSkillManager = skillManager;
	}

	public ScrollableSkillScreen(int keyCode) {
		this();
		pressedOpenScreen = keyCode;
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
		if (this.tabs.size() > ScrollableSkillTabType.MAX_TABS) {
			int guiLeft = (this.width - 252) / 2;
			int guiTop = (this.height - 140) / 2;
			addButton(new net.minecraft.client.gui.widget.button.Button(guiLeft, guiTop - 50, 20, 20,
					new net.minecraft.util.text.StringTextComponent("<"), b -> tabPage = Math.max(tabPage - 1, 0)));
			addButton(new net.minecraft.client.gui.widget.button.Button(guiLeft + 252 - 20, guiTop - 50, 20, 20,
					new net.minecraft.util.text.StringTextComponent(">"),
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

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			int i = (this.width - 252) / 2;
			int j = (this.height - 140) / 2;

			for (ScrollableSkillTabGui advancementtabgui : this.tabs.values()) {
				if (advancementtabgui.getPage() == tabPage
						&& advancementtabgui.isInsideTabSelector(i, j, mouseX, mouseY)) {
					this.clientSkillManager.setSelectedTab(advancementtabgui.getSkillElement(), true);
					break;
				}
			}
		}

		return super.mouseClicked(mouseX, mouseY, button);
	}

	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		/*
		 * if (this.minecraft.gameSettings.keyBindAdvancements.matchesKey(keyCode,
		 * scanCode)) { this.minecraft.displayGuiScreen((Screen) null);
		 * this.minecraft.mouseHelper.grabMouse(); return true; } else { return
		 * super.keyPressed(keyCode, scanCode, modifiers); }
		 */

		if (pressedOpenScreen == keyCode) {
			this.closeScreen();
			return true;
		} else
			return super.keyPressed(keyCode, scanCode, modifiers);
	}

	public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
		int i = (this.width - 252) / 2;
		int j = (this.height - 140) / 2;
		this.renderBackground(matrixStack);
		if (maxPages != 0) {
			net.minecraft.util.text.ITextComponent page = new net.minecraft.util.text.StringTextComponent(
					String.format("%d / %d", tabPage + 1, maxPages + 1));
			int width = this.font.getStringPropertyWidth(page);
			RenderSystem.disableLighting();
			this.font.func_238407_a_(matrixStack, page.func_241878_f(), i + (252 / 2) - (width / 2), j - 44, -1);
		}
		this.drawWindowBackground(matrixStack, mouseX, mouseY, i, j);
		this.renderWindow(matrixStack, i, j);
		this.drawWindowTooltips(matrixStack, mouseX, mouseY, i, j);
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

	private void drawWindowBackground(MatrixStack matrixStack, int mouseX, int mouseY, int offsetX, int offsetY) {
		ScrollableSkillTabGui advancementtabgui = this.selectedTab;
		if (advancementtabgui == null) {
			fill(matrixStack, offsetX + 9, offsetY + 18, offsetX + 9 + 234, offsetY + 18 + 113, -16777216);
			int i = offsetX + 9 + 117;
			drawCenteredString(matrixStack, this.font, EMPTY, i, offsetY + 18 + 56 - 9 / 2, -1);
			drawCenteredString(matrixStack, this.font, SAD_LABEL, i, offsetY + 18 + 113 - 9, -1);
		} else {
			RenderSystem.pushMatrix();
			RenderSystem.translatef((float) (offsetX + 9), (float) (offsetY + 18), 0.0F);
			advancementtabgui.drawTabBackground(matrixStack);
			RenderSystem.popMatrix();
			RenderSystem.depthFunc(515);
			RenderSystem.disableDepthTest();
		}
	}

	public void renderWindow(MatrixStack matrixStack, int offsetX, int offsetY) {
		RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
		RenderSystem.enableBlend();
		this.minecraft.getTextureManager().bindTexture(WINDOW);
		this.blit(matrixStack, offsetX, offsetY, 0, 0, 252, 140);
		if (this.tabs.size() > 1) {
			this.minecraft.getTextureManager().bindTexture(TABS);

			for (ScrollableSkillTabGui advancementtabgui : this.tabs.values()) {
				if (advancementtabgui.getPage() == tabPage)
					advancementtabgui.renderTabSelectorBackground(matrixStack, offsetX, offsetY,
							advancementtabgui == this.selectedTab);
			}

			RenderSystem.enableRescaleNormal();
			RenderSystem.defaultBlendFunc();

			for (ScrollableSkillTabGui advancementtabgui1 : this.tabs.values()) {
				if (advancementtabgui1.getPage() == tabPage)
					advancementtabgui1.drawIcon(offsetX, offsetY, this.itemRenderer);
			}

			RenderSystem.disableBlend();
		}

		this.font.func_243248_b(matrixStack, GUI_LABEL, (float) (offsetX + 8), (float) (offsetY + 6), 4210752);
	}

	private void drawWindowTooltips(MatrixStack matrixStack, int mouseX, int mouseY, int offsetX, int offsetY) {
		RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
		if (this.selectedTab != null) {
			RenderSystem.pushMatrix();
			RenderSystem.enableDepthTest();
			RenderSystem.translatef((float) (offsetX + 9), (float) (offsetY + 18), 400.0F);
			this.selectedTab.drawTabTooltips(matrixStack, mouseX - offsetX - 9, mouseY - offsetY - 18, offsetX,
					offsetY);
			RenderSystem.disableDepthTest();
			RenderSystem.popMatrix();
		}

		if (this.tabs.size() > 1) {
			for (ScrollableSkillTabGui advancementtabgui : this.tabs.values()) {
				if (advancementtabgui.getPage() == tabPage
						&& advancementtabgui.isInsideTabSelector(offsetX, offsetY, (double) mouseX, (double) mouseY)) {
					this.renderTooltip(matrixStack, advancementtabgui.getTitle(), mouseX, mouseY);
				}
			}
		}

	}

	public void rootAdvancementAdded(SkillElement advancementIn) {
		ScrollableSkillTabGui advancementtabgui = ScrollableSkillTabGui.create(this.minecraft, this, this.tabs.size(),
				advancementIn);
		if (advancementtabgui != null) {
			this.tabs.put(advancementIn, advancementtabgui);
		}
	}

	public void rootAdvancementRemoved(SkillElement advancementIn) {
	}

	public void nonRootAdvancementAdded(SkillElement advancementIn) {
		DevilRpg.LOGGER.info("|-------- nonRootAdvancementAdded");
		ScrollableSkillTabGui advancementtabgui = this.getTab(advancementIn);
		if (advancementtabgui != null) {
			advancementtabgui.addSkillElement(advancementIn);
		}

	}

	public void nonRootAdvancementRemoved(SkillElement advancementIn) {
	}

	public void onUpdateAdvancementProgress(SkillElement advancementIn, ScrollableSkillProgress progress) {
		ScrollableSkillEntryGui advancemententrygui = this.getAdvancementGui(advancementIn);
		if (advancemententrygui != null) {
			advancemententrygui.setAdvancementProgress(progress);
		}

	}

	public void setSelectedTab(@Nullable SkillElement advancementIn) {
		this.selectedTab = this.tabs.get(advancementIn);
	}

	public void advancementsCleared() {
		this.tabs.clear();
		this.selectedTab = null;
	}

	@Nullable
	public ScrollableSkillEntryGui getAdvancementGui(SkillElement advancement) {
		ScrollableSkillTabGui advancementtabgui = this.getTab(advancement);
		return advancementtabgui == null ? null : advancementtabgui.getAdvancementGui(advancement);
	}

	@Nullable
	private ScrollableSkillTabGui getTab(SkillElement advancement) {
		while (advancement.getParent() != null) {
			advancement = advancement.getParent();
		}

		return this.tabs.get(advancement);
	}

}
