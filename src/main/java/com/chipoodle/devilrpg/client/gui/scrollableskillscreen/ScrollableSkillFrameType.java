package com.chipoodle.devilrpg.client.gui.scrollableskillscreen;

import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public enum ScrollableSkillFrameType {
	TASK("task", 0, TextFormatting.GREEN), 
	CHALLENGE("challenge", 26, TextFormatting.DARK_PURPLE),
	GOAL("goal", 52, TextFormatting.GREEN);

	private final String name;
	private final int icon;
	private final TextFormatting format;
	private final ITextComponent translatedToast;

	private ScrollableSkillFrameType(String nameIn, int iconIn, TextFormatting formatIn) {
		      this.name = nameIn;
		      this.icon = iconIn;
		      this.format = formatIn;
		      this.translatedToast = new TranslationTextComponent("advancements.toast." + nameIn);
		   }

	public String getName() {
		return this.name;
	}

	@OnlyIn(Dist.CLIENT)
	public int getIcon() {
		return this.icon;
	}

	public static ScrollableSkillFrameType byName(String nameIn) {
		for (ScrollableSkillFrameType frametype : values()) {
			if (frametype.name.equals(nameIn)) {
				return frametype;
			}
		}

		throw new IllegalArgumentException("Unknown frame type '" + nameIn + "'");
	}

	public TextFormatting getFormat() {
		return this.format;
	}

	@OnlyIn(Dist.CLIENT)
	public ITextComponent getTranslatedToast() {
		return this.translatedToast;
	}
}
