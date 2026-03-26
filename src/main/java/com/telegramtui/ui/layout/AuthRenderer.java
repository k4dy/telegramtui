package com.telegramtui.ui.layout;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.screen.Screen;
import com.telegramtui.ui.common.Box;
import com.telegramtui.ui.common.CatppuccinMocha;

import java.io.IOException;

class AuthRenderer {

    private static final int BOX_W = 54;
    private static final int BOX_H = 11;

    private AuthRenderer() {}

    static void draw(Screen screen, AuthScreen.Mode mode, AuthScreen.Step step,
                     String input, boolean submitting, String statusMessage,
                     boolean statusIsError, String commandBuffer) throws IOException {
        TerminalSize size = screen.getTerminalSize();
        int cols = size.getColumns();
        int rows = size.getRows();
        TextGraphics g = screen.newTextGraphics();

        // fill background
        g.setBackgroundColor(CatppuccinMocha.BASE);
        g.setForegroundColor(CatppuccinMocha.BASE);
        g.fill(' ');

        // center the dialog box on screen
        int boxLeft = (cols - BOX_W) / 2;
        int boxTop = (rows - BOX_H) / 2;
        int boxRight = boxLeft + BOX_W - 1;
        int boxBottom = boxTop + BOX_H - 1;

        new Box(boxLeft, boxTop, boxRight, boxBottom, CatppuccinMocha.SURFACE1, "").draw(g);

        // app title
        String title = "TelegramTUI";
        g.setForegroundColor(CatppuccinMocha.MAUVE);
        g.setBackgroundColor(CatppuccinMocha.BASE);
        g.putString(boxLeft + (BOX_W - title.length()) / 2, boxTop + 2, title);

        // label above the input field
        g.setForegroundColor(CatppuccinMocha.SUBTEXT1);
        String label = switch (step) {
            case PHONE -> "Phone number (with country code):";
            case CODE -> "Enter code sent to your phone:  ";
            case PASSWORD -> "Two-Factor Authentication:      ";
            case RECOVERY_CODE -> "Enter recovery code:            ";
        };
        g.putString(boxLeft + 1 + (BOX_W - 2 - label.length()) / 2, boxTop + 4, label);

        // input field — shows typed text, command buffer, or status message
        int inputX = boxLeft + 3;
        int inputY = boxTop + 6;
        int inputW = BOX_W - 6;
        String fieldContent;
        var fieldColor = CatppuccinMocha.TEXT;
        int cursorOffset;

        if (mode == AuthScreen.Mode.COMMAND) {
            // show whatever command the user is typing
            fieldContent = padRight(" " + commandBuffer, inputW);
            fieldColor = CatppuccinMocha.MAUVE;
            cursorOffset = 1 + commandBuffer.length();
        } else if (submitting) {
            fieldContent = padRight(" " + statusMessage, inputW);
            fieldColor = CatppuccinMocha.YELLOW;
            cursorOffset = 1 + statusMessage.length();
        } else if (statusIsError) {
            fieldContent = padRight(" " + statusMessage, inputW);
            fieldColor = CatppuccinMocha.RED;
            cursorOffset = 1 + statusMessage.length();
        } else {
            // mask password characters with asterisks
            boolean maskInput = step == AuthScreen.Step.PASSWORD;
            String display = maskInput ? "*".repeat(input.length()) : input;
            fieldContent = padRight(" " + display, inputW);
            cursorOffset = 1 + input.length();
        }

        g.setBackgroundColor(CatppuccinMocha.SURFACE0);
        g.setForegroundColor(fieldColor);
        g.putString(inputX, inputY, fieldContent);

        // row below input: command menu or info message (not errors — those show in the field)
        g.setBackgroundColor(CatppuccinMocha.BASE);
        if (!statusIsError && !submitting && !statusMessage.isEmpty()) {
            g.setForegroundColor(CatppuccinMocha.OVERLAY0);
            String truncated = statusMessage.length() > BOX_W - 4
                    ? statusMessage.substring(0, BOX_W - 4) : statusMessage;
            g.putString(boxLeft + 1 + (BOX_W - 2 - truncated.length()) / 2, boxTop + 7, truncated);
        } else if (mode == AuthScreen.Mode.COMMAND) {
            g.setForegroundColor(CatppuccinMocha.OVERLAY0);
            String menu = "hint   forgot   quit";
            g.putString(boxLeft + 1 + (BOX_W - 2 - menu.length()) / 2, boxTop + 7, menu);
        }

        // bottom row: hint telling the user what mode they're in
        g.setForegroundColor(CatppuccinMocha.OVERLAY0);
        g.setBackgroundColor(CatppuccinMocha.BASE);
        String hint = mode == AuthScreen.Mode.COMMAND
                ? "[i] insert mode"
                : "[ESC] command mode   [Enter] confirm";
        g.putString(boxLeft + 1 + (BOX_W - 2 - hint.length()) / 2, boxTop + 8, hint);

        // move the terminal cursor to where the user is typing
        screen.setCursorPosition(new TerminalPosition(inputX + cursorOffset, inputY));
    }

    private static String padRight(String s, int length) {
        if (s.length() >= length) return s.substring(0, length);
        return s + " ".repeat(length - s.length());
    }
}
