package com.chipoodle.devilrpg.client.gui.skill;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.ReadBookScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.ResourceLocation;

public class SkillScreen extends ReadBookScreen {
	private PlayerEntity player;	
	
	private static final ReadBookScreen.IBookInfo SKILL_BOOK = new ReadBookScreen.IBookInfo() {
	      /**
	       * Returns the size of the book
	       */
	      public int getPageCount() {
	         return 2;
	      }

	      /**
	       * Gets the text from the supplied page number
	       */
	      public ITextComponent iGetPageText(int pageNum) {
	         return new StringTextComponent("Skill book");
	      }
	   };
	   private static ResourceLocation[] bookPageTextures = new ResourceLocation[SKILL_BOOK.getPageCount()];
	   
	   public static void open(PlayerEntity player) {
			Minecraft.getInstance().displayGuiScreen(new SkillScreen(player));
		}
	
	public SkillScreen(PlayerEntity player) {
		super(SKILL_BOOK);
		this.player = player;
	}

	@Override
	protected void init() {
		super.init();
		
	}
	
}
