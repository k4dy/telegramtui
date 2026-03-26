package com.telegramtui.ui.common;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;

import java.util.ArrayList;
import java.util.List;

public class TextRenderer {

	private TextRenderer() {
	}

	public static String clip(String text, int maxWidth) {
		if (maxWidth <= 0) return "";
		return text.length() > maxWidth ? text.substring(0, maxWidth) : text;
	}

	public static List<String> wrap(String text, int maxWidth) {
		List<String> lines = new ArrayList<>();
		if (maxWidth <= 0) {
			return lines;
		}
		for (String segment : text.split("\n", -1)) {
			if (segment.isEmpty()) {
				lines.add("");
				continue;
			}
			int start = 0;
			while (start < segment.length()) {
				int end = Math.min(start + maxWidth, segment.length());
				lines.add(segment.substring(start, end));
				start = end;
			}
		}
		return lines;
	}

	public static void drawClipped(TextGraphics graphics, String text,
	                               int col, int row, int maxWidth,
	                               TextColor fg, TextColor bg) {
		graphics.setBackgroundColor(bg);
		graphics.setForegroundColor(fg);
		graphics.putString(col, row, clip(text, maxWidth));
	}

	public static String padRight(String s, int len) {
		if (s == null) s = "";
		if (s.length() >= len) return s.substring(0, len);
		return s + " ".repeat(len - s.length());
	}

	public static int drawWrapped(TextGraphics graphics, List<String> lines,
	                              int col, int row, int maxWidth, int maxLines,
	                              TextColor fg, TextColor bg) {
		graphics.setBackgroundColor(bg);
		graphics.setForegroundColor(fg);
		int drawn = 0;
		for (int i = 0; i < lines.size() && drawn < maxLines; i++) {
			graphics.putString(col, row + drawn, clip(lines.get(i), maxWidth));
			drawn++;
		}
		return drawn;
	}
}
