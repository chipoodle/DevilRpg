package com.chipoodle.devilrpg.client.gui.skillbook;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.experience.IBaseExperienceCapability;
import com.chipoodle.devilrpg.capability.experience.PlayerExperienceCapabilityProvider;
import com.chipoodle.devilrpg.capability.skill.IBaseSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityProvider;
import com.chipoodle.devilrpg.util.PowerEnum;
import com.chipoodle.devilrpg.util.SkillEnum;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.ReadBookScreen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.client.gui.GuiUtils;

@OnlyIn(Dist.CLIENT)
public class SkillScreen extends BaseBookScreen {
	private PlayerEntity player;
	private LazyOptional<IBaseSkillCapability> skillCap;
	private LazyOptional<IBaseExperienceCapability> expCap;

	private static final String IMG_LOCATION = DevilRpg.MODID + ":textures/gui/";

	private static final HashMap<SkillEnum, ResourceLocation> SKILL_RESOURCES = new HashMap<SkillEnum, ResourceLocation>();
	static {
		SKILL_RESOURCES.put(SkillEnum.SUMMON_SOUL_WOLF, new ResourceLocation(IMG_LOCATION + "direwolf-bt.gif"));
		SKILL_RESOURCES.put(SkillEnum.SUMMON_SOUL_BEAR, new ResourceLocation(IMG_LOCATION + "grizzly-bt.gif"));
		SKILL_RESOURCES.put(SkillEnum.FIREBALL, new ResourceLocation(IMG_LOCATION + "moltenboulder-bt.gif"));
		SKILL_RESOURCES.put(SkillEnum.SUMMON_WISP_HEALTH, new ResourceLocation(IMG_LOCATION + "oaksage-bt.gif"));
		SKILL_RESOURCES.put(SkillEnum.SUMMON_WISP_SPEED,
				new ResourceLocation(IMG_LOCATION + "wolverineheart-bt.gif"));
		SKILL_RESOURCES.put(SkillEnum.TRANSFORM_WEREWOLF, new ResourceLocation(IMG_LOCATION + "werewolf-bt.gif"));
		BOOK_TEXTURES = new ResourceLocation(IMG_LOCATION + "book_cover.png");
	}
	private static final ResourceLocation POWER_RESOURCE = new ResourceLocation(IMG_LOCATION + "empty-box.png");

	private final int bookImageHeight = 192;
	private final int bookImageWidth = 192;
	private final int btnTextureWidth = 45;
	private final int btnTextureHeight = 45;
	private final int btnSize = 20;
	private List<CustomGuiButton> skillButtonList = new ArrayList<>();
	private List<CustomGuiButton> powerButtonList = new ArrayList<>();

	CustomGuiButton skillButtonApretado = null;
	boolean apretado = false;
	int difPosicionX = 0;
	int difPosicionY = 0;
	Double posicionMouseX = 0.0;
	Double posicionMouseY = 0.0;

	class ButtonMouse {
		public static final int LEFT_BUTTON = 0;
		public static final int RIGHT_BUTTON = 1;
	}

	private static final ReadBookScreen.IBookInfo SKILL_BOOK_CONFIG = new ReadBookScreen.IBookInfo() {
		private int TOTAL_PAGES = 2;

		/**
		 * Returns the size of the book
		 */
		public int getPageCount() {
			return TOTAL_PAGES;
		}

		/**
		 * Gets the text from the supplied page number
		 */
		public ITextComponent iGetPageText(int pageNum) {
			return new StringTextComponent("Skill book");
		}

	};

	public static void open(PlayerEntity player) {
		Minecraft.getInstance().enqueue(() -> Minecraft.getInstance().displayGuiScreen(new SkillScreen(player)));
	}

	public SkillScreen(PlayerEntity player) {
		super(SKILL_BOOK_CONFIG);
		this.player = player;
		expCap = player.getCapability(PlayerExperienceCapabilityProvider.EXPERIENCE_CAP);
		skillCap = player.getCapability(PlayerSkillCapabilityProvider.SKILL_CAP);
	}

	@Override
	protected void init() {
		super.init();
		addSkillButtons();
		addPowerButtons();
		loadAssignedPowerButtons();
	}

	protected void addSkillButtons() {
		int offsetFromScreenLeft = (width - bookImageWidth) / 2;
		// int btnPosX = offsetFromScreenLeft + bookImageWidth / 2 - this.width / 2;
		// int btnPosY = bookImageHeight / 2 - this.height;
		final int pageBelonging = 0;
		HashMap<SkillEnum, Integer> skillsPoints = skillCap.map(x -> x.getSkillsPoints()).orElse(null);
		int k = 0;
		for (SkillEnum s : Arrays.asList(SkillEnum.values())) {
			Double level = (double) (k / 2) + 1;
			CustomGuiButton custom = new CustomGuiButton(offsetFromScreenLeft + 55 * ((k % 2) + 1),
					10 + 30 * level.intValue(), btnSize, btnSize, s.getName(), SKILL_RESOURCES.get(s), btnTextureWidth,
					btnTextureHeight, s, skillsPoints.get(s), x -> skillButtonPressed(x), pageBelonging, true);
			skillButtonList.add(custom);
			addButton(custom);
			k++;
		}
		updateButtons();
	}

	protected void addPowerButtons() {
		int offsetFromScreenLeft = (width - bookImageWidth) / 2;
		int k = 0;
		for (PowerEnum p : Arrays.asList(PowerEnum.values())) {
			int drawnSkillLevel = 0;
			CustomGuiButton custom = new CustomGuiButton(offsetFromScreenLeft + 33 * (k + 1), 135, btnSize, btnSize,
					p.getDescription(), POWER_RESOURCE, btnTextureWidth, btnTextureHeight, p, drawnSkillLevel,
					x -> powerButtonPressed(x), 0, false);
			powerButtonList.add(custom);
			addButton(custom);
			k++;
		}
		updateButtons();
	}

	protected void loadAssignedPowerButtons() {
		HashMap<PowerEnum, SkillEnum> powerNames = skillCap.map(x -> x.getSkillsNameOfPowers()).orElse(null);

		for (CustomGuiButton c : powerButtonList) {
			PowerEnum pe = (PowerEnum) c.getSkillName();
			SkillEnum se = powerNames.get(pe);
			c.setButtonTexture(skillButtonList.stream().filter(x -> x.getSkillName().equals(se)).findAny()
					.map(x -> x.getButtonTexture()).orElse(POWER_RESOURCE));
		}
		updateButtons();
	}

	@Override
	protected void updateButtons() {
		super.updateButtons();
		HashMap<SkillEnum, Integer> skillsPoints = skillCap.map(x -> x.getSkillsPoints()).orElse(null);
		HashMap<SkillEnum, Integer> maxSkillsPoints = skillCap.map(x -> x.getMaxSkillsPoints()).orElse(null);
		if (!skillButtonList.isEmpty()) {
			skillButtonList.stream().forEach(x -> {
				x.visible = this.currPage == x.getPageBelonging();
				if (x.visible) {
					x.setDrawnSkillLevel(skillsPoints.get(x.getSkillName()));
					x.active = skillsPoints.get(x.getSkillName()) < maxSkillsPoints.get(x.getSkillName())
							&& expCap.map(y -> y.getUnspentPoints() > 0).orElse(false);
				}
			});
		}

		if (!powerButtonList.isEmpty())
			powerButtonList.stream()
					.forEach(x -> x.visible = this.currPage == x.getPageBelonging() || x.getPageBelonging() == 0);
	}

	@Override
	public void render(int parWidth, int parHeight, float p_73863_3_) {
		super.render(parWidth, parHeight, p_73863_3_);

		int offsetFromScreenLeft = (this.width - 192) / 2;

		int unspentPoints = expCap.map(y -> y.getUnspentPoints()).orElse(-1);
		if (unspentPoints != 0) {
			String s = "Unspent: " + unspentPoints;
			int widthOfString = this.getStringWidth(s);
			float textPositionX = offsetFromScreenLeft - widthOfString + bookImageWidth - 44;
			float textPositionY = 16.0F;
			this.font.drawString(s, textPositionX, textPositionY, 0);
		}

		if (skillButtonApretado != null) {
			RenderSystem.pushMatrix();
			RenderSystem.enableBlend();
			RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
					GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
					GlStateManager.DestFactor.ZERO);
			RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
					GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
			RenderSystem.scalef(skillButtonApretado.getScale(), skillButtonApretado.getScale(),
					(float) (skillButtonApretado.getScale() + 0.5));
			GuiUtils.drawContinuousTexturedBox(skillButtonApretado.getButtonTexture(),
					Math.round(this.posicionMouseX.intValue() / skillButtonApretado.getScale()
							- difPosicionX / skillButtonApretado.getScale()),
					Math.round(this.posicionMouseY.intValue() / skillButtonApretado.getScale()
							- difPosicionY / skillButtonApretado.getScale()),
					0, 0, Math.round(skillButtonApretado.getWidth() / skillButtonApretado.getScale()),
					Math.round(skillButtonApretado.getHeight() / skillButtonApretado.getScale()),
					Math.round(skillButtonApretado.getTextureWidth() / skillButtonApretado.getScale()),
					Math.round(skillButtonApretado.getTextureHeight() / skillButtonApretado.getScale()), 0, 0, 0, 0,
					skillButtonApretado.getzLevel());
			RenderSystem.popMatrix();
		}
	}

	/**
	 * Called when a mouse button is pressed and the mouse is moved around.
	 * Parameters are : mouseX, mouseY, lastButtonClicked & timeSinceMouseClick.
	 * 
	 * @return
	 */
	@Override
	public boolean mouseDragged(double parMouseX, double parMouseY, int parLastButtonClicked,
			double parTimeSinceMouseClick1, double parTimeSinceMouseClick2) {
		boolean returned = super.mouseDragged(parMouseX, parMouseY, parLastButtonClicked, parTimeSinceMouseClick1,
				parTimeSinceMouseClick2);
		if (parLastButtonClicked == ButtonMouse.RIGHT_BUTTON) {
			this.posicionMouseX = parMouseX;
			this.posicionMouseY = parMouseY;

			CustomGuiButton skillButton = skillButtonList.stream()
					.filter(x -> !apretado && x.isInside(posicionMouseX, posicionMouseY)).findAny().orElse(null);
			if (skillButton != null) {
				apretado = true;
				skillButtonApretado = skillButton;
				difPosicionX = posicionMouseX.intValue() - skillButtonApretado.x;
				difPosicionY = posicionMouseY.intValue() - skillButtonApretado.y;
			}
		}
		return returned;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int state) {
		boolean returned = super.mouseReleased(mouseX, mouseY, state);
		if (state == ButtonMouse.RIGHT_BUTTON) { // botón derecho
			if (skillButtonApretado != null) {
				CustomGuiButton copy = powerButtonList.stream().filter(x -> x.isInside(mouseX, mouseY)).findAny()
						.orElse(null);
				if (copy != null) {
					copy.setButtonTexture(skillButtonApretado.getButtonTexture());
					HashMap<PowerEnum, SkillEnum> powerNames = skillCap.map(x -> x.getSkillsNameOfPowers())
							.orElse(null);
					powerNames.put((PowerEnum) copy.getSkillName(), (SkillEnum) skillButtonApretado.getSkillName());
					skillCap.ifPresent(x -> x.setSkillsNameOfPowers(powerNames));
				}
				flushPressedButton();
			}
		}
		return returned;
	}

	@Override
	public boolean keyPressed(int p_keyPressed_1_, int p_keyPressed_2_, int p_keyPressed_3_) {
		return super.keyPressed(p_keyPressed_1_, p_keyPressed_2_, p_keyPressed_3_);

	}

	private void flushPressedButton() {
		skillButtonApretado = null;
		apretado = false;
		difPosicionX = 0;
		difPosicionY = 0;
		posicionMouseX = 0.0;
		posicionMouseY = 0.0;
	}

	public void skillButtonPressed(Button pressedButton) {
		CustomGuiButton pressed = ((CustomGuiButton) pressedButton);
		SkillEnum skilEnum = (SkillEnum) pressed.getSkillName();
		HashMap<SkillEnum, Integer> skillsPoints = skillCap.map(x -> x.getSkillsPoints()).orElse(null);

		Integer e = skillsPoints.get(((CustomGuiButton) pressedButton).getSkillName());
		e += expCap.map(x -> x.consumePoint()).orElse(0);
		skillsPoints.put(skilEnum, e);
		skillCap.ifPresent(x -> x.setSkillsPoints(skillsPoints));
		updateButtons();
	}

	public void powerButtonPressed(Button x) {
		updateButtons();

	}
}
