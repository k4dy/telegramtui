package com.telegramtui.ui.common;

import com.googlecode.lanterna.graphics.TextGraphics;

public class StatusPane {


	public static void render(TextGraphics graphics, int col, int row, int maxWidth,
	                          String status, String modeLabel) {
		String mode = modeLabel.isEmpty() ? "" : " | " + modeLabel;
		String text = "v1.0 | " + status + mode;
		TextRenderer.drawClipped(graphics, text, col, row, maxWidth,
				CatppuccinMocha.SUBTEXT0, CatppuccinMocha.BASE);
	}
}
