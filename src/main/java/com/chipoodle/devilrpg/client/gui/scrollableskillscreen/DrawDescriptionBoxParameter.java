package com.chipoodle.devilrpg.client.gui.scrollableskillscreen;

import com.mojang.blaze3d.matrix.MatrixStack;

public class DrawDescriptionBoxParameter {
	public MatrixStack matrixStack;
	public int x;
	public int y;
	public int width;
	public int height;
	public int padding;
	public int uWidth;
	public int vHeight;
	public int uOffset;
	public int vOffset;

	public DrawDescriptionBoxParameter(MatrixStack matrixStack, int x, int y, int width, int height, int padding,
			int uWidth, int vHeight, int uOffset, int vOffset) {
		this.matrixStack = matrixStack;
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.padding = padding;
		this.uWidth = uWidth;
		this.vHeight = vHeight;
		this.uOffset = uOffset;
		this.vOffset = vOffset;
	}
}