package com.chipoodle.devilrpg.client.gui.scrollableskillscreen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.client.gui.scrollableskillscreen.model.SkillTreeNode;
import com.chipoodle.devilrpg.util.SkillEnum;
import com.google.common.collect.Maps;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.util.LazyOptional;

@OnlyIn(Dist.CLIENT)
public class GuiSkillTab extends AbstractGui {
	private static final int BACKGROUND_CHUNKS_X_LOOP = 5;//18
	private static final int BACKGROUND_CHUNKS_Y_LOOP = 3;//11

	private static final int BACKGROUND_CHUNKS = 64;//16
	
	private static final int WIDGET_HEIGHT = 27;
	private static final int WIDGET_WIDTH = 28;
	// private static final int TAB_BACKGROUND_X = 234;
	// private static final int TAB_BACKGROUND_Y = 113;
	private static final int TAB_BACKGROUND_X = 284;
	private static final int TAB_BACKGROUND_Y = 163;
	private final Minecraft minecraft;
	private final ScrollableSkillScreen screen;
	private final SkillTabType type;
	private final int index;
	private final SkillElement skillElement;
	private final SkillDisplayInfo displayInfo;
	private final ItemStack icon;
	private final ITextComponent title;
	private final GuiSkillEntry root;
	private final Map<SkillElement, GuiSkillEntry> guis = Maps.newLinkedHashMap();
	private double scrollX;
	private double scrollY;
	private int minX = Integer.MAX_VALUE;
	private int minY = Integer.MAX_VALUE;
	private int maxX = Integer.MIN_VALUE;
	private int maxY = Integer.MIN_VALUE;
	private float fade;
	private boolean centered;
	private int page;

	private LazyOptional<PlayerSkillCapabilityInterface> skillCap;

	private GuiSkillTab(Minecraft minecraft, ScrollableSkillScreen screen, SkillTabType type, int index,
			SkillElement skillElement) {
		this.minecraft = minecraft;
		this.screen = screen;
		this.type = type;
		this.index = index;
		this.skillElement = skillElement;
		this.displayInfo = skillElement.getDisplay();
		this.icon = skillElement.getDisplay().getIcon();
		this.title = skillElement.getDisplay().getTitle();
		this.root = new GuiSkillEntry(this, minecraft, skillElement, 0, 0);
		this.addGuiSkillElement(this.root, skillElement);
		DevilRpg.LOGGER.info("|-----{}", toString());
	}

	public GuiSkillTab(Minecraft mc, ScrollableSkillScreen screen, SkillTabType type, int index, int page,
			SkillElement adv, LazyOptional<PlayerSkillCapabilityInterface> skillCap) {
		this(mc, screen, type, index, adv);
		this.page = page;
		this.skillCap = skillCap;
	}

	public int getPage() {
		return page;
	}

	public SkillElement getSkillElement() {
		return this.skillElement;
	}

	public ITextComponent getTitle() {
		return this.title;
	}

	/**
	 * Renderiza el frame de fondo del tab
	 * 
	 * @param matrixStack
	 * @param offsetX
	 * @param offsetY
	 * @param isSelected
	 */
	public void drawTab(MatrixStack matrixStack, int offsetX, int offsetY, boolean isSelected) {
		this.type.renderTabSelectorBackground(matrixStack, this, offsetX, offsetY, isSelected, this.index);
	}

	public void drawIcon(int offsetX, int offsetY, ItemRenderer renderer) {
		this.type.drawIcon(offsetX, offsetY, this.index, renderer, this.icon);
		
	}
	
	public void drawIconImage(MatrixStack matrixStack, int offsetLeft, int offsetTop) {
		this.type.drawIconImage(matrixStack, offsetLeft,offsetTop, this.index, root);
	}

	/**
	 * Pinta la imagen de fondo dinámicamente inclusive al hacer scroll
	 * 
	 * @param matrixStack
	 */
	public void drawContents(MatrixStack matrixStack) {
		if (!this.centered) {
			this.scrollX = (117 - (this.maxX + this.minX) / 2D);
			//Con este controlamos la altura de los botones del árbol
			this.scrollY = (84 - (this.maxY + this.minY) / 2D);
			this.centered = true;
		}
		RenderSystem.pushMatrix();
		RenderSystem.enableDepthTest();
		RenderSystem.translatef(0.0F, 0.0F, 950.0F);
		RenderSystem.colorMask(false, false, false, false);
		// Oculta el fondo fuera de la ventana
		fill(matrixStack, 4680, 2260, -4680, -2260, -16777216);
		RenderSystem.colorMask(true, true, true, true);
		RenderSystem.translatef(0.0F, 0.0F, -950.0F);
		RenderSystem.depthFunc(518);
		// Pinta el fondo
		fill(matrixStack, TAB_BACKGROUND_X, TAB_BACKGROUND_Y, 0, 0, -16777216);
		RenderSystem.depthFunc(515);
		ResourceLocation resourcelocation = this.displayInfo.getBackground();
		if (resourcelocation != null) {
			this.minecraft.getTextureManager().bind(resourcelocation);
		} else {
			this.minecraft.getTextureManager().bind(TextureManager.INTENTIONAL_MISSING_TEXTURE);
		}

		// Pinta el mosaico dinámico del fondo asícomo los iconos y las conexiones dle
		// arbol de habilidades
		generateBackgroundImageChunks(matrixStack);

		RenderSystem.depthFunc(518);
		RenderSystem.translatef(0.0F, 0.0F, -950.0F);
		RenderSystem.colorMask(false, false, false, false);
		fill(matrixStack, 4680, 2260, -4680, -2260, -16777216);
		RenderSystem.colorMask(true, true, true, true);
		RenderSystem.translatef(0.0F, 0.0F, 950.0F);
		RenderSystem.depthFunc(515);
		RenderSystem.popMatrix();
	}

	/**
	 * Pinta los chunks del fondo dinámicamente para hacer uno completo aun cuando
	 * se mueva con scroll. También pinta las líneas del árbol y los botones que no
	 * tienen encima el mouse
	 * 
	 * @param matrixStack
	 */

	private void generateBackgroundImageChunks(MatrixStack matrixStack) {
		int i = MathHelper.floor(this.scrollX);
		int j = MathHelper.floor(this.scrollY);
		int k = i % BACKGROUND_CHUNKS;
		int l = j % BACKGROUND_CHUNKS;

		for (int i1 = -1; i1 <= BACKGROUND_CHUNKS_X_LOOP; ++i1) {//18
			for (int j1 = -1; j1 <= BACKGROUND_CHUNKS_Y_LOOP; ++j1) { //11
				blit(matrixStack, k + BACKGROUND_CHUNKS * i1, l + BACKGROUND_CHUNKS * j1, 0.0F, 0.0F, BACKGROUND_CHUNKS,
						BACKGROUND_CHUNKS, BACKGROUND_CHUNKS, BACKGROUND_CHUNKS);
			}
		}
		// pinta las lineas
		this.root.drawConnectionLineToParent(matrixStack, i, j, true);
		this.root.drawConnectionLineToParent(matrixStack, i, j, false);
		// Pinta los skills
		this.root.drawSkills(matrixStack, i, j);
	}

	/**
	 * Pinta el tooltip cuando el puntero está sobre el botón de habilidad. Pinta la
	 * sombra que cubre el fondo cuando se hace hoover al botón de habilidad
	 * 
	 * @param matrixStack
	 * @param mouseX
	 * @param mouseY
	 * @param width
	 * @param height
	 */
	public void drawTabTooltips(MatrixStack matrixStack, int mouseX, int mouseY, int width, int height) {
		RenderSystem.pushMatrix();
		RenderSystem.translatef(0.0F, 0.0F, 200.0F);
		// Pinta la sombra que cubre el fondo cuando se hace hoover en una habilidad
		fill(matrixStack, 0, 0, TAB_BACKGROUND_X, TAB_BACKGROUND_Y, MathHelper.floor(this.fade * 255.0F) << 24);
		boolean flag = false;
		int i = MathHelper.floor(this.scrollX);// posición del fondo en x. Cambia cuando se posiciona con el drag del
												// mouse
		int j = MathHelper.floor(this.scrollY);// posición del fondo en y. Cambia cuando se posiciona con el drag del
												// mouse
		if (mouseX > 0 && mouseX < TAB_BACKGROUND_X && mouseY > 0 && mouseY < TAB_BACKGROUND_Y) {
			for (GuiSkillEntry skillEntryGui : this.guis.values()) {
				if (skillEntryGui.isMouseOver(i, j, mouseX, mouseY)) {
					flag = true;
					skillEntryGui.drawSkillHover(matrixStack, i, j, this.fade, width, height);
					break;
				}
			}
		}

		RenderSystem.popMatrix();
		if (flag) {
			this.fade = MathHelper.clamp(this.fade + 0.02F, 0.0F, 0.3F);
		} else {
			this.fade = MathHelper.clamp(this.fade - 0.04F, 0.0F, 1.0F);
		}
	}

	public boolean isInsideTabSelector(int offsetX, int offsetY, double mouseX, double mouseY) {
		return this.type.inInsideTabSelector(offsetX, offsetY, this.index, mouseX, mouseY);
	}

	/**
	 * Crea y ordena los botones
	 * 
	 * @param minecraft
	 * @param screen
	 * @param tabIndex
	 * @param skillElement
	 * @param skillCap
	 * @return
	 */
	@Nullable
	public static GuiSkillTab create(Minecraft minecraft, ScrollableSkillScreen screen, int tabIndex,
			SkillElement skillElement, LazyOptional<PlayerSkillCapabilityInterface> skillCap) {
		if (skillElement.getDisplay() == null) {
			return null;
		} else {
			// Se ordenan los botones
			SkillTreeNode.layout(skillElement);
			for (SkillTabType skillTabType : SkillTabType.values()) {
				if ((tabIndex % SkillTabType.MAX_TABS) < skillTabType.getMax()) {
					return new GuiSkillTab(minecraft, screen, skillTabType, tabIndex % SkillTabType.MAX_TABS,
							tabIndex / SkillTabType.MAX_TABS, skillElement, skillCap);
				}

				tabIndex -= skillTabType.getMax();
			}

			return null;
		}
	}

	public void dragSelectedGui(double dragX, double dragY) {
		if (this.maxX - this.minX > TAB_BACKGROUND_X) {
			this.scrollX = MathHelper.clamp(this.scrollX + dragX, -(this.maxX - TAB_BACKGROUND_X), 0.0D);
		}

		if (this.maxY - this.minY > TAB_BACKGROUND_Y) {
			this.scrollY = MathHelper.clamp(this.scrollY + dragY, -(this.maxY - TAB_BACKGROUND_Y), 0.0D);
		}

	}

	/**
	 * Agrega el Skill a la lista de guis envolviendolo en un
	 * ScrollableSkillEntryGui
	 * 
	 * @param skillElement
	 */
	public void addSkillElement(SkillElement skillElement) {
		DevilRpg.LOGGER.info("|-------- addSkillElement");
		if (skillElement.getDisplay() != null) {

			skillCap.ifPresent(skillCap -> {
				HashMap<SkillEnum, Integer> skillsPoints = skillCap.getSkillsPoints();
				HashMap<SkillEnum, Integer> skillsMaxPoints = skillCap.getMaxSkillsPoints();
				Integer points = skillsPoints.get(skillElement.getSkillCapability());
				Integer maxPonits = skillsMaxPoints.get(skillElement.getSkillCapability());
				GuiSkillEntry skillentryGui = new GuiSkillEntry(this, this.minecraft, skillElement,
						(points == null ? 0 : points), (maxPonits == null ? 0 : maxPonits));
				this.addGuiSkillElement(skillentryGui, skillElement);
			});

		}
	}

	/**
	 * Remueve el Skill a la lista de guis envolviendolo en un
	 * ScrollableSkillEntryGui
	 * 
	 * @param skillElement
	 */
	public void removeSkillElement(SkillElement skillElement) {
		DevilRpg.LOGGER.info("|-------- removeSkillElement");
		if (skillElement.getDisplay() != null) {

			skillCap.ifPresent(skillCap -> {
				this.guis.remove(skillElement);
			});

		}
	}

	/**
	 * Establece los valores para la posición del skill al moverse el fondo cuando
	 * se hace scroll
	 * 
	 * @param skillEntryGuiIn
	 * @param skillElement
	 */
	private void addGuiSkillElement(GuiSkillEntry skillEntryGuiIn, SkillElement skillElement) {
		this.guis.put(skillElement, skillEntryGuiIn);
		int i = skillEntryGuiIn.getX();
		int j = i + WIDGET_WIDTH;
		int k = skillEntryGuiIn.getY();
		int l = k + WIDGET_HEIGHT;
		this.minX = Math.min(this.minX, i);
		this.maxX = Math.max(this.maxX, j);
		this.minY = Math.min(this.minY, k);
		this.maxY = Math.max(this.maxY, l);

		for (GuiSkillEntry skillEntryGui : this.guis.values()) {
			skillEntryGui.attachToParent();
		}

	}

	@Nullable
	public GuiSkillEntry getSkillElementGui(SkillElement skillElement) {
		return this.guis.get(skillElement);
	}

	public ScrollableSkillScreen getScreen() {
		return this.screen;
	}

	public GuiSkillEntry getIfInsideIncludingChildren(double mouseX, double mouseY) {
		int i = MathHelper.floor(this.scrollX);// posición del fondo en x. Cambia cuando se posiciona con el drag del
												// mouse
		int j = MathHelper.floor(this.scrollY);// posición del fondo en y. Cambia cuando se posiciona con el drag del
												// mouse

		List<GuiSkillEntry> collect = this.guis.entrySet().stream().map(x -> x.getValue()).collect(Collectors.toList());
		return findIfInsideIncludingChildren(collect, (int) mouseX, (int) mouseY, i, j);
	}

	private GuiSkillEntry findIfInsideIncludingChildren(List<GuiSkillEntry> collect, int mouseX, int mouseY,
			int scrollX, int scrollY) {
		for (GuiSkillEntry skillEntryGui : collect) {
			if (skillEntryGui.isMouseOver(scrollX, scrollY, mouseX, mouseY)) {
				return skillEntryGui;
			}
			GuiSkillEntry result = findIfInsideIncludingChildren(skillEntryGui.getChildren(), mouseX, mouseY, scrollX,
					scrollY);
			if (result != null)
				return result;
		}
		return null;
	}

	public Map<SkillElement, GuiSkillEntry> getGuis() {
		return guis;
	}

	public double getScrollX() {
		return scrollX;
	}

	public double getScrollY() {
		return scrollY;
	}

}
