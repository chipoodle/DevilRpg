package com.chipoodle.devilrpg.client.gui.scrollableskillscreen;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.client.gui.skillbook.CustomGuiButton;
import com.chipoodle.devilrpg.util.SkillEnum;
import com.google.common.collect.Lists;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.IReorderingProcessor;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.CharacterManager;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.ITextProperties;
import net.minecraft.util.text.LanguageMap;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class GuiSkillEntry extends AbstractGui {
	private static final ResourceLocation WIDGETS = new ResourceLocation("textures/gui/advancements/widgets.png");
	private static final int[] LINE_BREAK_VALUES = new int[] { 0, 10, -10, 25, -25 };
	private static int FRAME_SIZE = 26;
	private static int BOTON_FACTOR_POSICION = 25; //25
	private static Float BOTON_TRANSLATION_FACTOR = 152.0F;
	private static Float BOTON_SCALE_FACTOR = 0.0400F;

	private final GuiSkillTab skillTabGui;
	private final SkillElement skillElement;
	private final SkillDisplayInfo displayInfo;
	private final IReorderingProcessor title;
	private final int width;
	private final List<IReorderingProcessor> description;
	private final Minecraft minecraft;
	private GuiSkillEntry parent;
	private final List<GuiSkillEntry> children = Lists.newArrayList();
	private SkillProgress skillProgress;
	private final int x;
	private final int y;

	private String levelString;

	public GuiSkillEntry(GuiSkillTab skillTabGui, Minecraft minecraft, SkillElement skillElement, int skillPoint,
			int maxSkillPoint) {
		this.skillTabGui = skillTabGui;
		this.skillElement = skillElement;
		this.displayInfo = skillElement.getDisplay();
		this.minecraft = minecraft;
		this.title = LanguageMap.getInstance()
				.getVisualOrder(minecraft.font.substrByWidth(displayInfo.getTitle(), 163));
		this.x = MathHelper.floor(displayInfo.getX() * 28.0F * 2);
		this.y = MathHelper.floor(displayInfo.getY() * 27.0F * 2);
		// int i = advancement.getRequirementCount();
		int i = 0;
		int j = String.valueOf(i).length();
		int k = i > 1
				? minecraft.font.width("  ") + minecraft.font.width("0") * j * 2
						+ minecraft.font.width("/")
				: 0;
		int l = 29 + minecraft.font.width(this.title) + k;
		this.description = LanguageMap.getInstance().getVisualOrder(
				this.getDescriptionLines(TextComponentUtils.mergeStyles(displayInfo.getDescription().copy(),
						Style.EMPTY.applyFormat(displayInfo.getFrame().getFormat())), l));

		for (IReorderingProcessor ireorderingprocessor : this.description) {
			l = Math.max(l, minecraft.font.width(ireorderingprocessor));
		}

		this.width = l + 3 + 5;

		updateFormattedLevelString(skillPoint, maxSkillPoint);
	}

	public void updateFormattedLevelString(int skillPoint, int maxSkillPoint) {
		levelString = "" + skillPoint + "/" + maxSkillPoint;
	}

	private static float getTextWidth(CharacterManager manager, List<ITextProperties> text) {
		return (float) text.stream().mapToDouble(manager::stringWidth).max().orElse(0.0D);
	}

	private List<ITextProperties> getDescriptionLines(ITextComponent component, int maxWidth) {
		CharacterManager charactermanager = this.minecraft.font.getSplitter();
		List<ITextProperties> list = null;
		float f = Float.MAX_VALUE;

		for (int i : LINE_BREAK_VALUES) {
			List<ITextProperties> list1 = charactermanager.splitLines(component, maxWidth - i, Style.EMPTY);
			float f1 = Math.abs(getTextWidth(charactermanager, list1) - (float) maxWidth);
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
	private GuiSkillEntry getFirstVisibleParent(SkillElement skillIn) {
		do {
			skillIn = skillIn.getParent();
		} while (skillIn != null && skillIn.getDisplay() == null);

		return skillIn != null && skillIn.getDisplay() != null ? this.skillTabGui.getSkillElementGui(skillIn) : null;
	}

	public void drawConnectionLineToParent(MatrixStack matrixStack, int x, int y, boolean dropShadow) {
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

		for (GuiSkillEntry advancemententrygui : this.children) {
			advancemententrygui.drawConnectionLineToParent(matrixStack, x, y, dropShadow);
		}

	}

	/**
	 * @return
	 */
	private SkillState getSkillState() {
		float f = this.skillProgress == null ? 0.0F : this.skillProgress.getPercent();
		SkillState skillState;
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

	public void addGuiSkill(GuiSkillEntry guiSkillsIn) {
		DevilRpg.LOGGER.info("|----------- addGuiSkill " + (guiSkillsIn.title.toString()));
		this.children.add(guiSkillsIn);
	}

	/**
	 * Pinta los marcos con sus elementos internos cuando el mouse no está sobre
	 * ellos
	 * 
	 * @param matrixStack
	 * @param x
	 * @param y
	 */
	public void drawSkills(MatrixStack matrixStack, int x, int y) {
		// DevilRpg.LOGGER.info("|---drawSkill x" +x+" y "+y+" title: "+
		// this.displayInfo.getTitle());

		if (!this.displayInfo.isHidden() || this.skillProgress != null && this.skillProgress.isDone()) {
			
			if (isInsideInnerScreen(x, y,FRAME_SIZE/2,FRAME_SIZE/2)) {
				SkillState skillState = getSkillState();
	
				// Texturas de los marcos, y barras de título de los tooltips
				this.minecraft.getTextureManager().bind(WIDGETS);
	
				RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
				RenderSystem.enableBlend();
				
				// Pinta el marco
				this.blit(matrixStack, x + this.x + 3, y + this.y, this.displayInfo.getFrame().getIcon(),128 + skillState.getId() * FRAME_SIZE, FRAME_SIZE, FRAME_SIZE);
	
				pintarBoton(matrixStack, x, y, skillState);
	
				// Pinta el icono del marco
				// this.minecraft.getItemRenderer().renderItemAndEffectIntoGuiWithoutEntity(this.displayInfo.getIcon(),x
				// + this.x + 8, y + this.y + 5);
			}
			if (isInsideInnerScreen(x, y,0,0)) {
				drawSkillLevel(matrixStack, x, y);
			}
		}

		// Pinta a los hijos
		for (GuiSkillEntry childrenEntry : this.children) {
			childrenEntry.drawSkills(matrixStack, x, y);
		}

	}

	public void drawSkillHover(MatrixStack matrixStack, int x, int y, float fade, int width, int height) {
		boolean flag = width + x + this.x + this.width + FRAME_SIZE >= this.skillTabGui.getScreen().width;
		String s = this.skillProgress == null ? null : this.skillProgress.getProgressText();
		int i = s == null ? 0 : this.minecraft.font.width(s);
		boolean flag1 = 113 - y - this.y - FRAME_SIZE <= 6 + this.description.size() * 9;
		float f = this.skillProgress == null ? 0.0F : this.skillProgress.getPercent();
		int j = MathHelper.floor(f * (float) this.width);
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
		this.minecraft.getTextureManager().bind(WIDGETS);
		RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
		RenderSystem.enableBlend();
		int l = y + this.y;
		int i1;
		if (flag) {
			i1 = x + this.x - this.width + FRAME_SIZE + 6;
		} else {
			i1 = x + this.x;
		}

		int j1 = 32 + this.description.size() * 9;
		if (!this.description.isEmpty()) {
			if (flag1) {
				this.drawDescriptionBox(matrixStack, i1, l + FRAME_SIZE - j1, this.width, j1, 10, 200, FRAME_SIZE, 0,
						52);
			} else {
				this.drawDescriptionBox(matrixStack, i1, l, this.width, j1, 10, 200, FRAME_SIZE, 0, 52);
			}
		}

		this.blit(matrixStack, i1, l, 0, advancementstate.getId() * FRAME_SIZE, j, FRAME_SIZE);
		this.blit(matrixStack, i1 + j, l, 200 - k, advancementstate1.getId() * FRAME_SIZE, k, FRAME_SIZE);
		// Pinta el marco
		this.blit(matrixStack, x + this.x + 3, y + this.y, this.displayInfo.getFrame().getIcon(),
				128 + advancementstate2.getId() * FRAME_SIZE, FRAME_SIZE, FRAME_SIZE);
		if (flag) {
			this.minecraft.font.drawShadow(matrixStack, this.title, (float) (i1 + 5),(float) (y + this.y + 9), -1);
			if (s != null) {
				this.minecraft.font.drawShadow(matrixStack, s, (float) (x + this.x - i),
						(float) (y + this.y + 9), -1);
			}
		} else {
			this.minecraft.font.drawShadow(matrixStack, this.title, (float) (x + this.x + 32),(float) (y + this.y + 9), -1);
			if (s != null) {
				this.minecraft.font.drawShadow(matrixStack, s,
						(float) (x + this.x + this.width - i - 5), (float) (y + this.y + 9), -1);
			}
		}

		if (flag1) {
			for (int k1 = 0; k1 < this.description.size(); ++k1) {
				this.minecraft.font.draw(matrixStack, this.description.get(k1), (float) (i1 + 5),(float) (l + FRAME_SIZE - j1 + 7 + k1 * 9), -5592406);
			}
		} else {
			for (int l1 = 0; l1 < this.description.size(); ++l1) {
				/////////////this.minecraft.font.drawString(matrixStack, this.description.get(l1), (float) (i1 + 5),(float) (y + this.y + 9 + 17 + l1 * 9), -5592406);
			}
		}

		SkillState skillState = getSkillState();

		pintarBoton(matrixStack, x, y, skillState);
		drawSkillLevel(matrixStack, x, y);

		// Pinta el icono del marco
		// this.minecraft.getItemRenderer().renderItemAndEffectIntoGuiWithoutEntity(this.displayInfo.getIcon(),x
		// + this.x + 8, y + this.y + 5);
	}

	/**
	 * @param matrixStack
	 * @param x           de scroll
	 * @param y           de scroll
	 * @param skillState  (MatrixStack matrixStack, int x, int y, float uOffset,
	 *                    float vOffset, int width, int height, int textureWidth,
	 *                    int textureHeight)
	 */
	private void pintarBoton(MatrixStack matrixStack, int x, int y, SkillState skillState) {
		// Pinta el botón
		
		this.minecraft.getTextureManager().bind(this.displayInfo.getImage());
		RenderSystem.pushMatrix();
		RenderSystem.enableDepthTest();
		RenderSystem.scalef(BOTON_SCALE_FACTOR,BOTON_SCALE_FACTOR, BOTON_SCALE_FACTOR);
		RenderSystem.translatef(BOTON_TRANSLATION_FACTOR, BOTON_TRANSLATION_FACTOR, BOTON_TRANSLATION_FACTOR);
		AbstractGui.blit(matrixStack, (x + this.x - 1)*BOTON_FACTOR_POSICION, (y + this.y - 4)*BOTON_FACTOR_POSICION, 0, 0, FRAME_SIZE*20, FRAME_SIZE*20, 512, 512);
		RenderSystem.popMatrix();

		// drawSkillLevel(matrixStack, x, y);
	}

	/**
	 * @param matrixStack
	 * @param x           de scroll
	 * @param y           de scroll
	 */
	private void drawSkillLevel(MatrixStack matrixStack, int x, int y) {
		if (!getSkillElement().getSkillCapability().equals(SkillEnum.EMPTY)) {
			String levelString = getLevelString();
			this.minecraft.font.drawShadow(matrixStack, levelString, (float) (x + getX()),
					(float) (y + getY()), -1);
		}
	}

	/**
	 * @param x
	 * @param y
	 * @return
	 */
	private boolean isInsideInnerScreen(int x, int y,int frameWidth, int frameHeight) {
		return  Objects.nonNull(this.skillTabGui.getScreen())
				&& this.skillTabGui.getScreen().isInsideInnerFrame(x + getX(), y + getY(),frameWidth,frameHeight);
	}

	protected void drawDescriptionBox(MatrixStack matrixStack, int x, int y, int width, int height, int padding,
			int uWidth, int vHeight, int uOffset, int vOffset) {
		this.blit(matrixStack, x, y, uOffset, vOffset, padding, padding);
		this.drawDescriptionBoxBorder(matrixStack, x + padding, y, width - padding - padding, padding,
				uOffset + padding, vOffset, uWidth - padding - padding, vHeight);
		this.blit(matrixStack, x + width - padding, y, uOffset + uWidth - padding, vOffset, padding, padding);
		this.blit(matrixStack, x, y + height - padding, uOffset, vOffset + vHeight - padding, padding, padding);
		this.drawDescriptionBoxBorder(matrixStack, x + padding, y + height - padding, width - padding - padding,
				padding, uOffset + padding, vOffset + vHeight - padding, uWidth - padding - padding, vHeight);
		this.blit(matrixStack, x + width - padding, y + height - padding, uOffset + uWidth - padding,
				vOffset + vHeight - padding, padding, padding);
		this.drawDescriptionBoxBorder(matrixStack, x, y + padding, padding, height - padding - padding, uOffset,
				vOffset + padding, uWidth, vHeight - padding - padding);
		this.drawDescriptionBoxBorder(matrixStack, x + padding, y + padding, width - padding - padding,
				height - padding - padding, uOffset + padding, vOffset + padding, uWidth - padding - padding,
				vHeight - padding - padding);
		this.drawDescriptionBoxBorder(matrixStack, x + width - padding, y + padding, padding,
				height - padding - padding, uOffset + uWidth - padding, vOffset + padding, uWidth,
				vHeight - padding - padding);
	}

	protected void drawDescriptionBoxBorder(MatrixStack matrixStack, int x, int y, int borderToU, int borderToV,
			int uOffset, int vOffset, int uWidth, int vHeight) {
		for (int i = 0; i < borderToU; i += uWidth) {
			int j = x + i;
			int k = Math.min(uWidth, borderToU - i);

			for (int l = 0; l < borderToV; l += vHeight) {
				int i1 = y + l;
				int j1 = Math.min(vHeight, borderToV - l);
				this.blit(matrixStack, j, i1, uOffset, vOffset, k, j1);
			}
		}

	}

	public boolean isMouseOver(int x, int y, int mouseX, int mouseY) {
		if (!this.displayInfo.isHidden() || this.skillProgress != null && this.skillProgress.isDone()) {
			int i = x + this.x;
			int j = i + FRAME_SIZE;
			int k = y + this.y;
			int l = k + FRAME_SIZE;
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

	@Override
	public String toString() {
		return "SkillEntryGui [" + ", children=" + children.size() + ", width=" + width + ", x=" + x + ", y=" + y + "]";
	}

	public final List<GuiSkillEntry> getChildren() {
		return children;
	}

	public SkillElement getSkillElement() {
		return skillElement;
	}

	public String getLevelString() {
		return levelString;
	}

	/*-----------------------------*/
	public void skillButtonPressed(Button pressedButton) {
		CustomGuiButton pressed = ((CustomGuiButton) pressedButton);
		SkillEnum skilEnum = (SkillEnum) pressed.getSkillName();

		skillTabGui.getScreen().getSkillCap().ifPresent(x -> {
			HashMap<SkillEnum, Integer> skillsPoints = x.getSkillsPoints();
			Integer e = skillsPoints.get(((CustomGuiButton) pressedButton).getSkillName());
			if (e != null && skillTabGui.getScreen().getExpCap().isPresent()) {
				e += skillTabGui.getScreen().getExpCap().map(exp -> exp.consumePoint()).orElse(0);
				skillsPoints.put(skilEnum, e);
				x.setSkillsPoints(skillsPoints, minecraft.player);
				// updateButtons();
			}

		});
	}

	/*
	 * protected void updateButtons() { HashMap<SkillEnum, Integer> skillsPoints =
	 * skillCap.map(x -> x.getSkillsPoints()).orElse(null); HashMap<SkillEnum,
	 * Integer> maxSkillsPoints = skillCap.map(x ->
	 * x.getMaxSkillsPoints()).orElse(null); if (!skillButtonList.isEmpty()) {
	 * skillButtonList.stream().forEach(x -> { x.visible = this.getCurrPage() ==
	 * x.getPageBelonging(); if (x.visible) {
	 * x.setDrawnSkillLevel(skillsPoints.get(x.getSkillName())); x.active =
	 * skillsPoints.get(x.getSkillName()) < maxSkillsPoints.get(x.getSkillName()) &&
	 * expCap.map(y -> y.getUnspentPoints() > 0).orElse(false); } }); }
	 * 
	 * if (!powerButtonList.isEmpty()) powerButtonList.stream() .forEach(x ->
	 * x.visible = this.getCurrPage() == x.getPageBelonging() ||
	 * x.getPageBelonging() == 0); }
	 */
}
