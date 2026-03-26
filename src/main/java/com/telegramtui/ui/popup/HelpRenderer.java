package com.telegramtui.ui.popup;

import com.googlecode.lanterna.graphics.TextGraphics;
import com.telegramtui.ui.common.CatppuccinMocha;
import com.telegramtui.ui.common.TextRenderer;

// draws the help overlay with all keybindings listed in a table
final class HelpRenderer {

    private HelpRenderer() {}

    // each row is {key, description, style} — "H" means section header, "T" means telescope mode
    private static final String[][] LINES = {
        {"Navigation",          "",                                    "H"},
        {"h / l",               "focus left / right panel",           ""},
        {"j / k",               "move up / down",                     ""},
        {"J / K",               "jump 10 messages",                   ""},
        {"G",                   "jump to newest message",             ""},
        {"Enter",               "open chat / deselect message",       ""},
        {"i",                   "enter insert mode",                  ""},
        {"Esc",                 "exit insert / cancel",               ""},
        {"",                    "",                                    ""},
        {"View",                "",                                    "H"},
        {"f",                   "toggle fullscreen chat",             ""},
        {"x / X",               "close tab / close all other tabs",   ""},
        {"Tab",                 "cycle to next tab",                  ""},
        {"q",                   "quit",                               ""},
        {"",                    "",                                    ""},
        {"Telescope",           "",                                    "H"},
        {"/",                   "chat search  — filter as you type",  "T"},
        {"?",                   "message search  — Enter to query",   "T"},
        {"@",                   "sender search  — drill into msgs",   "T"},
        {"",                    "",                                    ""},
        {"Commands  (via :)",   "",                                    "H"},
        {"help",                "show this help",                     ""},
        {"logout",              "log out of Telegram",                ""},
        {"q / quit",            "quit",                               ""},
    };

    static void render(TextGraphics g, int screenWidth, int screenHeight) {
        int popupHeight = LINES.length + 5;
        int popupWidth = Math.min(58, screenWidth - 4);
        int left = (screenWidth - popupWidth) / 2;
        int top = Math.max(1, (screenHeight - popupHeight) / 2);

        PopupDraw.fillBackground(g, left, top, popupWidth, popupHeight);
        PopupDraw.drawBorder(g, left, top, popupWidth, popupHeight);

        g.setBackgroundColor(CatppuccinMocha.SURFACE0);
        g.setForegroundColor(CatppuccinMocha.MAUVE);
        g.putString(left + 2, top + 1, "Keybindings & Commands");

        for (int i = 0; i < LINES.length; i++) {
            String key   = LINES[i][0];
            String desc  = LINES[i][1];
            String style = LINES[i][2];
            if (key.isEmpty()) continue;

            g.setBackgroundColor(CatppuccinMocha.SURFACE0);
            int row = top + 3 + i;

            if ("H".equals(style)) {
                // section header row
                boolean isTelescope = "Telescope".equals(key);
                g.setForegroundColor(isTelescope ? CatppuccinMocha.TEAL : CatppuccinMocha.MAUVE);
                g.putString(left + 2, row, key);
            } else if ("T".equals(style)) {
                // telescope-specific keybinding
                g.setForegroundColor(CatppuccinMocha.TEAL);
                g.putString(left + 2, row, TextRenderer.padRight(key, 5));
                g.setForegroundColor(CatppuccinMocha.SUBTEXT1);
                g.putString(left + 7, row, desc);
            } else {
                // normal keybinding
                g.setForegroundColor(CatppuccinMocha.YELLOW);
                g.putString(left + 2, row, TextRenderer.padRight(key, 12));
                g.setForegroundColor(CatppuccinMocha.TEXT);
                g.putString(left + 14, row, desc);
            }
        }

        String footer = "press any key to close";
        g.setForegroundColor(CatppuccinMocha.OVERLAY0);
        g.putString(left + (popupWidth - footer.length()) / 2, top + popupHeight - 2, footer);
    }
}
