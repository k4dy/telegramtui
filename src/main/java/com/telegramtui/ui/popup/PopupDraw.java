package com.telegramtui.ui.popup;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.telegramtui.ui.common.CatppuccinMocha;
import com.telegramtui.ui.common.TextRenderer;

final class PopupDraw {

    private PopupDraw() {}

    // fills a rectangle with the popup background color
    static void fillBackground(TextGraphics g, int left, int top, int w, int h) {
        g.setBackgroundColor(CatppuccinMocha.SURFACE0);
        g.setForegroundColor(CatppuccinMocha.SURFACE0);
        for (int r = 0; r < h; r++) {
            g.putString(left, top + r, " ".repeat(w));
        }
    }

    // draws a rounded border around the popup
    static void drawBorder(TextGraphics g, int left, int top, int w, int h) {
        g.setBackgroundColor(CatppuccinMocha.SURFACE0);
        g.setForegroundColor(CatppuccinMocha.MAUVE);
        g.putString(left, top, "┌" + "─".repeat(w - 2) + "┐");
        g.putString(left, top + h - 1, "└" + "─".repeat(w - 2) + "┘");
        for (int r = 1; r < h - 1; r++) {
            g.putString(left, top + r, "│");
            g.putString(left + w - 1, top + r, "│");
        }
    }

    // shows a status message on the first row and clears the rest
    static void renderStatusRow(TextGraphics g, int left, int top,
                                int innerWidth, int visibleRows,
                                String message, TextColor color) {
        g.setBackgroundColor(CatppuccinMocha.SURFACE0);
        g.setForegroundColor(color);
        g.putString(left + 2, top, TextRenderer.padRight(message, innerWidth));
        for (int row = 1; row < visibleRows; row++) {
            g.setBackgroundColor(CatppuccinMocha.SURFACE0);
            g.setForegroundColor(CatppuccinMocha.SURFACE0);
            g.putString(left + 1, top + row, " ".repeat(innerWidth + 2));
        }
    }
}
