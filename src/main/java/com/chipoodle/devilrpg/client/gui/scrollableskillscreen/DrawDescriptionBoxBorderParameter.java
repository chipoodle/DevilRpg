package com.chipoodle.devilrpg.client.gui.scrollableskillscreen;

import com.mojang.blaze3d.matrix.MatrixStack;

public class DrawDescriptionBoxBorderParameter {
	public MatrixStack matrixStack;
	public int x;
	public int y;
	public int borderToU;
	public int borderToV;
	public int uOffset;
	public int vOffset;
	public int uWidth;
	public int vHeight;

	public DrawDescriptionBoxBorderParameter(MatrixStack matrixStack, int x, int y, int borderToU, int borderToV,
			int uOffset, int vOffset, int uWidth, int vHeight) {
		this.matrixStack = matrixStack;
		this.x = x;
		this.y = y;
		this.borderToU = borderToU;
		this.borderToV = borderToV;
		this.uOffset = uOffset;
		this.vOffset = vOffset;
		this.uWidth = uWidth;
		this.vHeight = vHeight;
	}
}