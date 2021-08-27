package com.chipoodle.devilrpg.client.gui.scrollableskillscreen;

import java.util.HashMap;
import java.util.List;

import javax.annotation.Nullable;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.client.gui.skillbook.CustomGuiButton;
import com.chipoodle.devilrpg.util.SkillEnum;
import com.google.common.collect.Lists;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.platform.GlStateManager;
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
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class GuiSkillEntry extends AbstractGui {
	static final int BUTTON_IMAGE_SIZE = 512;
	static int FRAME_SIZE = 26;
	static Float BOTON_SCALE_FACTOR = 0.0400F;
	static Float BOTON_TRANSLATION_FACTOR = 152.0F;
	static int BOTON_FACTOR_POSICION = 25; // 25

	private static final ResourceLocation WIDGETS = new ResourceLocation("textures/gui/advancements/widgets.png");
	private static final int[] LINE_BREAK_VALUES = new int[] { 0, 10, -10, 25, -25 };

	private final GuiSkillTab skillTabGui;
	private final SkillElement skillElement;
	private final SkillDisplayInfo displayInfo;
	private IReorderingProcessor title;
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

		skillProgress = new SkillProgress(skillPoint, maxSkillPoint);
		updateFormattedLevelString(skillPoint, maxSkillPoint);
		
		this.x = MathHelper.floor(getDisplayInfo().getX() * 28.0F * 2);
		this.y = MathHelper.floor(getDisplayInfo().getY() * 27.0F * 2);
		//int i = skillElement.getRequirementCount();
		int i = maxSkillPoint;
		int j = String.valueOf(i).length();
		int k = i > 1 ? minecraft.font.width("  ") + minecraft.font.width("0") * j * 2 + minecraft.font.width("/") : 0;
		int l = 29 + minecraft.font.width(this.title) + k;
		this.description = LanguageMap.getInstance().getVisualOrder(
				this.getDescriptionLines(TextComponentUtils.mergeStyles(getDisplayInfo().getDescription().copy(),
						Style.EMPTY.applyFormat(getDisplayInfo().getFrame().getFormat())), l));

		for (IReorderingProcessor ireorderingprocessor : this.description) {
			l = Math.max(l, minecraft.font.width(ireorderingprocessor));
		}

		this.width = l + 3 + 5;

		skillTabGui.getScreen().getSkillsResourceLocations().put(skillElement.getSkillCapability(),this.getDisplayInfo().getImage());
	}

	/**
	 * Actualiza los puntos utilizads / puntos máximos del skill
	 * 
	 * @param skillPoint
	 * @param maxSkillPoint
	 */
	public void updateFormattedLevelString(int skillPoint, int maxSkillPoint) {
		updateLevelString(skillPoint, maxSkillPoint);
		updateTitle();
		skillProgress.update(skillPoint, maxSkillPoint);
	}

	private void updateLevelString(int skillPoint, int maxSkillPoint) {
		levelString = "" + skillPoint + "/" + maxSkillPoint;
	}
	
	private void updateTitle() {
		//StringTextComponent level = new StringTextComponent(" "+levelString);
		this.title = LanguageMap.getInstance().getVisualOrder(this.minecraft.font.substrByWidth(getDisplayInfo().getTitle(), 163));
		if(skillElement.getParent() != null) {
			IReorderingProcessor levelPr = IReorderingProcessor.forward(" "+levelString, Style.EMPTY);
			this.title = IReorderingProcessor.composite(this.title, levelPr);
		}
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

	public void addGuiSkill(GuiSkillEntry guiSkillsIn) {
		DevilRpg.LOGGER.info("|----------- addGuiSkill {}", (guiSkillsIn.title.toString()));
		this.children.add(guiSkillsIn);
	}

	public boolean isDisabled() {
		if(this.parent == null)
			return false;
				
		return !(parent.getSkillProgress().hasProgress() || parent.getSkillProgress().isDone() ) ;
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

		// Pinta a los hijos
		for (GuiSkillEntry childrenEntry : this.children) {
			childrenEntry.drawSkills(matrixStack, x, y);
		}

		if (!this.getDisplayInfo().isHidden() || (this.skillProgress != null && this.skillProgress.isDone())) {

			SkillState skillState = getSkillState();
			
			// Texturas de los marcos, y barras de título de los tooltips
			this.minecraft.getTextureManager().bind(WIDGETS);

			// Pinta el marco del botón
			this.blit(matrixStack, x + this.x + 3, y + this.y, this.getDisplayInfo().getFrame().getIcon(),
					128 + skillState.getId() * FRAME_SIZE, FRAME_SIZE, FRAME_SIZE);

			// Pinta el icono del marco
			// this.minecraft.getItemRenderer().renderAndDecorateFakeItem(this.displayInfo.getIcon(),x
			// + this.x + 8, y + this.y + 5);

			//RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
			//RenderSystem.enableBlend();
			
			// Pinta el botón
			drawButton(matrixStack, x, y, false, this.getDisplayInfo().getImage(), false,isDisabled());
		}


	}

	public void drawSkillHover(MatrixStack matrixStack, int x, int y, float fade, int width, int height) {
		boolean widthFlag = width + x + this.x + this.width + FRAME_SIZE >= this.skillTabGui.getScreen().width;
		String skillProgressString = this.skillProgress == null ? null : this.skillProgress.getProgressText();
		int i = skillProgressString == null ? 0 : this.minecraft.font.width(skillProgressString);
		boolean flag1 = 113 - y - this.y - FRAME_SIZE <= 6 + this.description.size() * 9;
		float f = this.skillProgress == null ? 0.0F : this.skillProgress.getPercent();
		int j = MathHelper.floor(f *  this.width);
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
		//RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
		//RenderSystem.enableBlend();
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
				this.drawDescriptionBox(new DrawDescriptionBoxParameter(matrixStack, i1, l + FRAME_SIZE - j1,
						this.width, j1, 10, 200, FRAME_SIZE, 0, 52));
			} else {
				this.drawDescriptionBox(new DrawDescriptionBoxParameter(matrixStack, i1, l, this.width, j1, 10, 200,
						FRAME_SIZE, 0, 52));
			}
		}

		//pinta la mitad izquierda de la barra del título
		this.blit(matrixStack, i1, l, 0, advancementstate.getId() * FRAME_SIZE, j, FRAME_SIZE);
		//pinta la mitad derecha de la barra del título
		this.blit(matrixStack, i1 + j, l, 200 - k, advancementstate1.getId() * FRAME_SIZE, k, FRAME_SIZE);
		// Pinta el marco del ícono
		this.blit(matrixStack, x + this.x + 3, y + this.y, this.getDisplayInfo().getFrame().getIcon(),
				128 + advancementstate2.getId() * FRAME_SIZE, FRAME_SIZE, FRAME_SIZE);
	
		if (widthFlag) {
			//pinta título
			this.minecraft.font.drawShadow(matrixStack, this.title,  (i1 + 5), (y + this.y + 9), -1);
			if (skillProgressString != null) {
				this.minecraft.font.drawShadow(matrixStack, skillProgressString, (x + this.x - i), (y + this.y + 9), -1);
			}
		} else {
			//pinta título
			this.minecraft.font.drawShadow(matrixStack, this.title, (x + this.x + 32),  (y + this.y + 9), -1);
			if (skillProgressString != null) {
				this.minecraft.font.drawShadow(matrixStack, skillProgressString, (x + this.x + this.width - i - 5), (y + this.y + 9), -1);
			}
		}

		
		if (flag1) {
			for (int k1 = 0; k1 < this.description.size(); ++k1) {
				//Pinta contenido
				this.minecraft.font.draw(matrixStack, this.description.get(k1), (i1 + 5),
						(float) (l + FRAME_SIZE - j1 + 7 + k1 * 9), -5592406);
			}
			
			
		} else {
			for (int l1 = 0; l1 < this.description.size(); ++l1) {
				//Pinta contenido
				this.minecraft.font.draw(matrixStack, this.description.get(l1), (i1 + 5),
						(float) (y + this.y + 9 + 17 + l1 * 9), -5592406);
			}
		}

		drawButton(matrixStack, x, y, false, this.getDisplayInfo().getImage(), false,isDisabled());

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
	@SuppressWarnings("deprecation")
	public void drawButton(MatrixStack matrixStack, int x, int y, boolean mostrarPuntos, ResourceLocation image,
			boolean superpuesto,boolean disabled) {
				
		if (mostrarPuntos) {
			// Pinta puntos asignados/puntos máximos
			RenderSystem.pushMatrix();
			drawOuterSkillLevel(matrixStack, (int) (x), (int) (y + FRAME_SIZE));
			RenderSystem.popMatrix();
		}
		int posX = this.x + x;
		int posY = this.y + y;

		// Pinta el botón
		RenderSystem.pushMatrix();
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
			if(disabled){
				RenderSystem.color4f(100.0F, 100.0F, 100.0F, 0.4F);
				RenderSystem.enableBlend();
			}
		}
		
		this.minecraft.getTextureManager().bind(image);
		RenderSystem.translatef(posX - posX * BOTON_SCALE_FACTOR - 1, posY - posY * BOTON_SCALE_FACTOR - 1, 1);
		RenderSystem.scalef(BOTON_SCALE_FACTOR, BOTON_SCALE_FACTOR, 0);
		RenderSystem.translatef(168, 90, 0);
		AbstractGui.blit(matrixStack, posX, posY, 0, 0, FRAME_SIZE * 20 - 2, FRAME_SIZE * 20 - 2, BUTTON_IMAGE_SIZE,
				BUTTON_IMAGE_SIZE);

		if(disabled)
			RenderSystem.disableBlend();
		
		RenderSystem.popMatrix();

	}

	/**
	 * @param matrixStack
	 * @param x           de scroll
	 * @param y           de scroll
	 */
	private void drawOuterSkillLevel(MatrixStack matrixStack, int x, int y) {
		if (!getSkillElement().getSkillCapability().equals(SkillEnum.EMPTY)) {
			this.minecraft.font.draw(matrixStack, levelString, (x + getX()), (y + getY()), -1);
		}
	}

	protected void drawDescriptionBox(DrawDescriptionBoxParameter descriptionBoxParam) {
		this.blit(descriptionBoxParam.matrixStack, descriptionBoxParam.x, descriptionBoxParam.y,
				descriptionBoxParam.uOffset, descriptionBoxParam.vOffset, descriptionBoxParam.padding,
				descriptionBoxParam.padding);
		this.drawDescriptionBoxBorder(new DrawDescriptionBoxBorderParameter(descriptionBoxParam.matrixStack,
				descriptionBoxParam.x + descriptionBoxParam.padding, descriptionBoxParam.y,
				descriptionBoxParam.width - descriptionBoxParam.padding - descriptionBoxParam.padding,
				descriptionBoxParam.padding, descriptionBoxParam.uOffset + descriptionBoxParam.padding,
				descriptionBoxParam.vOffset,
				descriptionBoxParam.uWidth - descriptionBoxParam.padding - descriptionBoxParam.padding,
				descriptionBoxParam.vHeight));
		this.blit(descriptionBoxParam.matrixStack,
				descriptionBoxParam.x + descriptionBoxParam.width - descriptionBoxParam.padding, descriptionBoxParam.y,
				descriptionBoxParam.uOffset + descriptionBoxParam.uWidth - descriptionBoxParam.padding,
				descriptionBoxParam.vOffset, descriptionBoxParam.padding, descriptionBoxParam.padding);
		this.blit(descriptionBoxParam.matrixStack, descriptionBoxParam.x,
				descriptionBoxParam.y + descriptionBoxParam.height - descriptionBoxParam.padding,
				descriptionBoxParam.uOffset,
				descriptionBoxParam.vOffset + descriptionBoxParam.vHeight - descriptionBoxParam.padding,
				descriptionBoxParam.padding, descriptionBoxParam.padding);
		this.drawDescriptionBoxBorder(new DrawDescriptionBoxBorderParameter(descriptionBoxParam.matrixStack,
				descriptionBoxParam.x + descriptionBoxParam.padding,
				descriptionBoxParam.y + descriptionBoxParam.height - descriptionBoxParam.padding,
				descriptionBoxParam.width - descriptionBoxParam.padding - descriptionBoxParam.padding,
				descriptionBoxParam.padding, descriptionBoxParam.uOffset + descriptionBoxParam.padding,
				descriptionBoxParam.vOffset + descriptionBoxParam.vHeight - descriptionBoxParam.padding,
				descriptionBoxParam.uWidth - descriptionBoxParam.padding - descriptionBoxParam.padding,
				descriptionBoxParam.vHeight));
		this.blit(descriptionBoxParam.matrixStack,
				descriptionBoxParam.x + descriptionBoxParam.width - descriptionBoxParam.padding,
				descriptionBoxParam.y + descriptionBoxParam.height - descriptionBoxParam.padding,
				descriptionBoxParam.uOffset + descriptionBoxParam.uWidth - descriptionBoxParam.padding,
				descriptionBoxParam.vOffset + descriptionBoxParam.vHeight - descriptionBoxParam.padding,
				descriptionBoxParam.padding, descriptionBoxParam.padding);
		this.drawDescriptionBoxBorder(new DrawDescriptionBoxBorderParameter(descriptionBoxParam.matrixStack,
				descriptionBoxParam.x, descriptionBoxParam.y + descriptionBoxParam.padding, descriptionBoxParam.padding,
				descriptionBoxParam.height - descriptionBoxParam.padding - descriptionBoxParam.padding,
				descriptionBoxParam.uOffset, descriptionBoxParam.vOffset + descriptionBoxParam.padding,
				descriptionBoxParam.uWidth,
				descriptionBoxParam.vHeight - descriptionBoxParam.padding - descriptionBoxParam.padding));
		this.drawDescriptionBoxBorder(new DrawDescriptionBoxBorderParameter(descriptionBoxParam.matrixStack,
				descriptionBoxParam.x + descriptionBoxParam.padding,
				descriptionBoxParam.y + descriptionBoxParam.padding,
				descriptionBoxParam.width - descriptionBoxParam.padding - descriptionBoxParam.padding,
				descriptionBoxParam.height - descriptionBoxParam.padding - descriptionBoxParam.padding,
				descriptionBoxParam.uOffset + descriptionBoxParam.padding,
				descriptionBoxParam.vOffset + descriptionBoxParam.padding,
				descriptionBoxParam.uWidth - descriptionBoxParam.padding - descriptionBoxParam.padding,
				descriptionBoxParam.vHeight - descriptionBoxParam.padding - descriptionBoxParam.padding));
		this.drawDescriptionBoxBorder(new DrawDescriptionBoxBorderParameter(descriptionBoxParam.matrixStack,
				descriptionBoxParam.x + descriptionBoxParam.width - descriptionBoxParam.padding,
				descriptionBoxParam.y + descriptionBoxParam.padding, descriptionBoxParam.padding,
				descriptionBoxParam.height - descriptionBoxParam.padding - descriptionBoxParam.padding,
				descriptionBoxParam.uOffset + descriptionBoxParam.uWidth - descriptionBoxParam.padding,
				descriptionBoxParam.vOffset + descriptionBoxParam.padding, descriptionBoxParam.uWidth,
				descriptionBoxParam.vHeight - descriptionBoxParam.padding - descriptionBoxParam.padding));
	}

	protected void drawDescriptionBoxBorder(DrawDescriptionBoxBorderParameter parameterObject) {
		for (int i = 0; i < parameterObject.borderToU; i += parameterObject.uWidth) {
			int j = parameterObject.x + i;
			int k = Math.min(parameterObject.uWidth, parameterObject.borderToU - i);

			for (int l = 0; l < parameterObject.borderToV; l += parameterObject.vHeight) {
				int i1 = parameterObject.y + l;
				int j1 = Math.min(parameterObject.vHeight, parameterObject.borderToV - l);
				this.blit(parameterObject.matrixStack, j, i1, parameterObject.uOffset, parameterObject.vOffset, k, j1);
			}
		}

	}

	public boolean isMouseOver(int scrollX, int scrollY, int mouseX, int mouseY) {
		if (!this.getDisplayInfo().isHidden() || this.skillProgress != null && this.skillProgress.isDone()) {
			int i = scrollX + this.x;
			int j = i + FRAME_SIZE;
			int k = scrollY + this.y;
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
			}

		});
	}

	public SkillDisplayInfo getDisplayInfo() {
		return displayInfo;
	}
	
	public SkillProgress getSkillProgress() {
		return skillProgress;
	}
}
