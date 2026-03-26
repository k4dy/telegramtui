package com.telegramtui.ui.common;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;

// A bordered panel that can be repositioned and re-labeled at runtime
public class Box {

	private int left;
	private int top;
	private int right;
	private int bottom;
	private TextColor outlineColor;
	private String label;

	public Box(int left, int top, int right, int bottom, TextColor outlineColor, String label) {
		this.left = left;
		this.top = top;
		this.right = right;
		this.bottom = bottom;
		this.outlineColor = outlineColor;
		this.label = label;
	}

	// Call this on every terminal resize to update the box position
	public void setBounds(int left, int top, int right, int bottom) {
		this.left = left;
		this.top = top;
		this.right = right;
		this.bottom = bottom;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public void setOutlineColor(TextColor color) {
		this.outlineColor = color;
	}

	public int getInnerLeft() {
		return left + 1;
	}

	public int getInnerTop() {
		return top + 1;
	}

	public int getInnerRight() {
		return right - 1;
	}

	public int getInnerBottom() {
		return bottom - 1;
	}

	public int getInnerWidth() {
		return right - left - 2;
	}

	public int getInnerHeight() {
		return bottom - top - 2;
	}

	public void draw(TextGraphics graphics) {
		graphics.setBackgroundColor(CatppuccinMocha.BASE);
		graphics.setForegroundColor(outlineColor);
		graphics.putString(left,  top,    "┌");
		graphics.putString(right, top,    "┐");
		graphics.putString(left,  bottom, "└");
		graphics.putString(right, bottom, "┘");
		for (int col = left + 1; col < right; col++) {
			graphics.putString(col, top,    "─");
			graphics.putString(col, bottom, "─");
		}
		for (int row = top + 1; row < bottom; row++) {
			graphics.putString(left,  row, "│");
			graphics.putString(right, row, "│");
		}

		// Label in top-left corner, clipped to fit
		graphics.setForegroundColor(CatppuccinMocha.SUBTEXT0);
		graphics.putString(left + 1, top, TextRenderer.clip(label, getInnerWidth()));
	}
}
