package com.chipoodle.devilrpg.client.gui.skillbook;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.DialogTexts;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.chat.NarratorChatListener;
import net.minecraft.client.gui.screen.ReadBookScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.widget.button.ChangePageButton;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.WrittenBookItem;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.IReorderingProcessor;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.ITextProperties;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class BaseBookScreen extends Screen {
	protected int bookImageWidth = 192;
	protected int bookImageHeight = 192;
	protected int bookImageScaleWidth = 192;
	protected int bookImageScaleHeight = 192;
	protected int uOffset = 0;
	protected int vOffset = 0;
	public static final ReadBookScreen.IBookInfo EMPTY_BOOK = new ReadBookScreen.IBookInfo() {
		/**
		 * Returns the size of the book
		 */
		public int getPageCount() {
			return 0;
		}

		public ITextProperties getPageRaw(int p_230456_1_) {
			return ITextProperties.EMPTY;
		}

	};
	public static ResourceLocation BOOK_TEXTURES = new ResourceLocation("textures/gui/book.png");
	private ReadBookScreen.IBookInfo bookInfo;
	private int currPage;
	public int getCurrPage() {
		return currPage;
	}

	/** Holds a copy of the page text, split into page width lines */
	private List<IReorderingProcessor> cachedPageLines = Collections.emptyList();
	private int cachedPage = -1;
	private ITextComponent pageMsg = StringTextComponent.EMPTY;
	private ChangePageButton buttonNextPage;
	private ChangePageButton buttonPreviousPage;
	/** Determines if a sound is played when the page is turned */
	private final boolean pageTurnSounds;

	public BaseBookScreen(ReadBookScreen.IBookInfo bookInfoIn) {
		this(bookInfoIn, true);
	}

	public BaseBookScreen() {
		this(EMPTY_BOOK, false);
	}

	private BaseBookScreen(ReadBookScreen.IBookInfo bookInfoIn, boolean pageTurnSoundsIn) {
		super(NarratorChatListener.NO_TITLE);
		this.bookInfo = bookInfoIn;
		this.pageTurnSounds = pageTurnSoundsIn;
	}

	public void setBookAccess(ReadBookScreen.IBookInfo p_214155_1_) {
		this.bookInfo = p_214155_1_;
		this.currPage = MathHelper.clamp(this.currPage, 0, p_214155_1_.getPageCount());
		this.updateButtons();
		this.cachedPage = -1;
	}

	/**
	 * Moves the book to the specified page and returns true if it exists, false
	 * otherwise
	 */
	public boolean showPage(int pageNum) {
		int i = MathHelper.clamp(pageNum, 0, this.bookInfo.getPageCount() - 1);
		if (i != this.currPage) {
			this.currPage = i;
			this.updateButtons();
			this.cachedPage = -1;
			return true;
		} else {
			return false;
		}
	}

	/**
	 * I'm not sure why this exists. The function it calls is public and does all of
	 * the work
	 */
	protected boolean showPage2(int pageNum) {
		return this.showPage(pageNum);
	}

	protected void init() {
		this.addDoneButton();
		this.addChangePageButtons();
		//this.testButtons();
	}

	/**
	 * Button done.
	 */
	protected void addDoneButton() {
		this.addButton(new Button(this.width / 2 - 100, 196, 200, 20, DialogTexts.GUI_DONE, (p_214161_1_) -> {
			this.minecraft.setScreen((Screen) null);
		}));
	}

	/**
	 * Buttons next and previous.
	 */
	protected void addChangePageButtons() {
		int i = (this.width - bookImageWidth) / 2;
		int j = 2;
		this.buttonNextPage = this.addButton(new ChangePageButton(i + 116, 159, true, (p_214159_1_) -> {
			this.nextPage();
		}, this.pageTurnSounds));
		this.buttonPreviousPage = this.addButton(new ChangePageButton(i + 43, 159, false, (p_214158_1_) -> {
			this.previousPage();
		}, this.pageTurnSounds));
		this.updateButtons();
	}
	
	protected void testButtons() {
		this.addButton(new Button(10, 10, 50, 20, new StringTextComponent("bIWidth-10" ), (boton) -> {
			bookImageWidth=bookImageWidth-10;
		}));
		this.addButton(new Button(70, 10, 50, 20, new StringTextComponent("bIWidth+10"), (boton) -> {
			bookImageWidth=bookImageWidth+10;
		}));
		this.addButton(new Button(10, 30, 50, 20, new StringTextComponent("bIHeight-10"), (boton) -> {
			bookImageHeight=bookImageHeight-10;
		}));
		this.addButton(new Button(70, 30, 50, 20, new StringTextComponent("bIHeight+10"), (boton) -> {
			bookImageHeight=bookImageHeight+10;
		}));

		
		this.addButton(new Button(10, 50, 50, 20, new StringTextComponent("bISWidth-10"), (boton) -> {
			bookImageScaleWidth=bookImageScaleWidth-10;
		}));
		this.addButton(new Button(70, 50, 50, 20, new StringTextComponent("bISWidth+10"), (boton) -> {
			bookImageScaleWidth=bookImageScaleWidth+10;
		}));
		this.addButton(new Button(10, 70, 50, 20, new StringTextComponent("bISHeight-10"), (boton) -> {
			bookImageScaleHeight=bookImageScaleHeight-10;
		}));
		this.addButton(new Button(70, 70, 50, 20, new StringTextComponent("bISHeight+10"), (boton) -> {
			bookImageScaleHeight=bookImageScaleHeight+10;
		}));
		
		this.addButton(new Button(10, 90, 50, 20, new StringTextComponent("uOffset-10"), (boton) -> {
			uOffset=uOffset-10;
		}));
		this.addButton(new Button(70, 90, 50, 20, new StringTextComponent("uOffset+10"), (boton) -> {
			uOffset=uOffset+10;
		}));
		this.addButton(new Button(10, 110, 50, 20, new StringTextComponent("vOffset-10"), (boton) -> {
			vOffset=vOffset-10;
		}));
		this.addButton(new Button(70, 110, 50, 20, new StringTextComponent("vOffset+10"), (boton) -> {
			vOffset=vOffset+10;
		}));
	}
	
	private void drawtestButtonsTexts(MatrixStack matrixStack) {
		drawText(matrixStack, bookImageWidth+"", 120, 12);
		drawText(matrixStack, bookImageHeight+"", 120, 32);
		drawText(matrixStack, bookImageScaleWidth+"", 120, 52);
		drawText(matrixStack, bookImageScaleHeight+"", 120, 72);
		drawText(matrixStack, uOffset+"", 120, 92);
		drawText(matrixStack, vOffset+"", 120, 112);
	
	}

	private int getPageCount() {
		return this.bookInfo.getPageCount();
	}

	/**
	 * Moves the display back one page
	 */
	protected void previousPage() {
		if (this.currPage > 0) {
			--this.currPage;
		}

		this.updateButtons();
	}

	/**
	 * Moves the display forward one page
	 */
	protected void nextPage() {
		if (this.currPage < this.getPageCount() - 1) {
			++this.currPage;
		}

		this.updateButtons();
	}

	protected void updateButtons() {
		this.buttonNextPage.visible = this.currPage < this.getPageCount() - 1;
		this.buttonPreviousPage.visible = this.currPage > 0;
	}

	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (super.keyPressed(keyCode, scanCode, modifiers)) {
			return true;
		} else {
			switch (keyCode) {
			case 266:
				this.buttonPreviousPage.onPress();
				return true;
			case 267:
				this.buttonNextPage.onPress();
				return true;
			default:
				return false;
			}
		}
	}

	public void drawText(MatrixStack matrixStack, String s,float textPositionX,float textPositionY) {
		this.font.draw(matrixStack, s, textPositionX, textPositionY, 0);
	}
	
	public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
		this.renderBackground(matrixStack);
		RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
		this.minecraft.getTextureManager().bind(BOOK_TEXTURES);
		int x = (this.width - bookImageWidth) / 2;
		int y = 2;
		
		AbstractGui.blit(matrixStack, x, y,uOffset, vOffset, bookImageWidth, bookImageHeight,bookImageScaleWidth, bookImageScaleHeight);
		//drawtestButtonsTexts(matrixStack);

		if (this.cachedPage != this.currPage) {
			ITextProperties itextproperties = this.bookInfo.getPage(this.currPage);
			this.cachedPageLines = this.font.split(itextproperties, 114);
			this.pageMsg = new TranslationTextComponent("book.pageIndicator", this.currPage + 1,Math.max(this.getPageCount(), 1));
		}

		this.cachedPage = this.currPage;
		int i1 = this.font.width(this.pageMsg);
		this.font.draw(matrixStack, this.pageMsg, (float) (x - i1 + bookImageWidth - 44), 18.0F, 0);
		int k = Math.min(128 / 9, this.cachedPageLines.size());

		for (int l = 0; l < k; ++l) {
			IReorderingProcessor ireorderingprocessor = this.cachedPageLines.get(l);
			this.font.drawShadow(matrixStack, ireorderingprocessor, (float) (x + 36), (float) (12 + l * 9), 0);
		}

		Style style = this.getClickedComponentStyleAt((double) mouseX, (double) mouseY);
		if (style != null) {
			this.renderComponentHoverEffect(matrixStack, style, mouseX, mouseY);
		}

		super.render(matrixStack, mouseX, mouseY, partialTicks);
	}

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			Style style = this.getClickedComponentStyleAt(mouseX, mouseY);
			if (style != null && this.handleComponentClicked(style)) {
				return true;
			}
		}

		return super.mouseClicked(mouseX, mouseY, button);
	}

	public boolean handleComponentClicked(Style style) {
		ClickEvent clickevent = style.getClickEvent();
		if (clickevent == null) {
			return false;
		} else if (clickevent.getAction() == ClickEvent.Action.CHANGE_PAGE) {
			String s = clickevent.getValue();

			try {
				int i = Integer.parseInt(s) - 1;
				return this.showPage2(i);
			} catch (Exception exception) {
				return false;
			}
		} else {
			boolean flag = super.handleComponentClicked(style);
			if (flag && clickevent.getAction() == ClickEvent.Action.RUN_COMMAND) {
				this.minecraft.setScreen((Screen) null);
			}

			return flag;
		}
	}

	@Nullable
	public Style getClickedComponentStyleAt(double p_238805_1_, double p_238805_3_) {
		if (this.cachedPageLines.isEmpty()) {
			return null;
		} else {
			int i = MathHelper.floor(p_238805_1_ - (double) ((this.width - bookImageWidth) / 2) - 36.0D);
			int j = MathHelper.floor(p_238805_3_ - 2.0D - 30.0D);
			if (i >= 0 && j >= 0) {
				int k = Math.min(128 / 9, this.cachedPageLines.size());
				if (i <= 114 && j < 9 * k + k) {
					int l = j / 9;
					if (l >= 0 && l < this.cachedPageLines.size()) {
						IReorderingProcessor ireorderingprocessor = this.cachedPageLines.get(l);
						return this.minecraft.font.getSplitter().componentStyleAtWidth(ireorderingprocessor, i);
					} else {
						return null;
					}
				} else {
					return null;
				}
			} else {
				return null;
			}
		}
	}

	public static List<String> nbtPagesToStrings(CompoundNBT p_214157_0_) {
		ListNBT listnbt = p_214157_0_.getList("pages", 8).copy();
		Builder<String> builder = ImmutableList.builder();

		for (int i = 0; i < listnbt.size(); ++i) {
			builder.add(listnbt.getString(i));
		}

		return builder.build();
	}

	@OnlyIn(Dist.CLIENT)
	public interface IBookInfo {
		/**
		 * Returns the size of the book
		 */
		int getPageCount();

		ITextProperties getPageRaw(int p_230456_1_);

		default ITextProperties getPage(int p_238806_1_) {
			return p_238806_1_ >= 0 && p_238806_1_ < this.getPageCount() ? this.getPageRaw(p_238806_1_)
					: ITextProperties.EMPTY;
		}

		static ReadBookScreen.IBookInfo fromItem(ItemStack p_216917_0_) {
			Item item = p_216917_0_.getItem();
			if (item == Items.WRITTEN_BOOK) {
				return new ReadBookScreen.WrittenBookInfo(p_216917_0_);
			} else {
				return (ReadBookScreen.IBookInfo) (item == Items.WRITABLE_BOOK
						? new ReadBookScreen.UnwrittenBookInfo(p_216917_0_)
						: ReadBookScreen.EMPTY_ACCESS);
			}
		}
	}

	@OnlyIn(Dist.CLIENT)
	public static class WritableBookAccess implements ReadBookScreen.IBookInfo {
		private final List<String> pages;

		public WritableBookAccess(ItemStack p_i50617_1_) {
			this.pages = readPages(p_i50617_1_);
		}

		private static List<String> readPages(ItemStack p_216919_0_) {
			CompoundNBT compoundnbt = p_216919_0_.getTag();
			return (List<String>) (compoundnbt != null ? ReadBookScreen.convertPages(compoundnbt)
					: ImmutableList.of());
		}

		/**
		 * Returns the size of the book
		 */
		public int getPageCount() {
			return this.pages.size();
		}

		public ITextProperties getPageRaw(int p_230456_1_) {
			return ITextProperties.of(this.pages.get(p_230456_1_));
		}
	}

	@OnlyIn(Dist.CLIENT)
	public static class WrittenBookInfo implements ReadBookScreen.IBookInfo {
		private final List<String> pages;

		public WrittenBookInfo(ItemStack p_i50616_1_) {
			this.pages = readPages(p_i50616_1_);
		}

		private static List<String> readPages(ItemStack stack) {
			CompoundNBT compoundnbt = stack.getTag();
			return (List<String>) (compoundnbt != null && WrittenBookItem.makeSureTagIsValid(compoundnbt)
					? ReadBookScreen.convertPages(compoundnbt)
					: ImmutableList.of(ITextComponent.Serializer.toJson(
							(new TranslationTextComponent("book.invalid.tag")).withStyle(TextFormatting.DARK_RED))));
		}

		/**
		 * Returns the size of the book
		 */
		public int getPageCount() {
			return this.pages.size();
		}

		public ITextProperties getPageRaw(int p_230456_1_) {
			String s = this.pages.get(p_230456_1_);

			try {
				ITextProperties itextproperties = ITextComponent.Serializer.fromJson(s);
				if (itextproperties != null) {
					return itextproperties;
				}
			} catch (Exception exception) {
			}

			return ITextProperties.of(s);
		}

	}
}
