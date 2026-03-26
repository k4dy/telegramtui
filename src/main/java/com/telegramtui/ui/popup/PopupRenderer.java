package com.telegramtui.ui.popup;

import com.googlecode.lanterna.graphics.TextGraphics;
import com.telegramtui.model.ChatModel;
import com.telegramtui.model.MessageSearchResult;
import com.telegramtui.model.SenderInfo;
import com.telegramtui.ui.common.CatppuccinMocha;
import com.telegramtui.ui.common.TextRenderer;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

class PopupRenderer {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    // reference to the popup so we can read its current state
    private final CommandPopup p;

    PopupRenderer(CommandPopup p) {
        this.p = p;
    }

    void render(TextGraphics g, int screenWidth, int screenHeight) {
        if (p.mode == CommandPopup.Mode.HIDDEN) return;
        if (p.mode == CommandPopup.Mode.HELP) {
            HelpRenderer.render(g, screenWidth, screenHeight);
            return;
        }
        if (p.mode == CommandPopup.Mode.COMMAND) {
            renderCommandBar(g, screenWidth, screenHeight);
            return;
        }

        int popupWidth = Math.min(Math.max(60, screenWidth * 80 / 100), screenWidth - 4);
        int popupHeight = Math.min(Math.max(10, screenHeight * 70 / 100), screenHeight - 4);
        int left = (screenWidth - popupWidth) / 2;
        int top = Math.max(0, screenHeight * 12 / 100);

        PopupDraw.fillBackground(g, left, top, popupWidth, popupHeight);
        PopupDraw.drawBorder(g, left, top, popupWidth, popupHeight);

        int innerWidth = popupWidth - 4;

        // title shown in the border line at the top
        g.setBackgroundColor(CatppuccinMocha.SURFACE0);
        g.setForegroundColor(CatppuccinMocha.MAUVE);
        String title = switch (p.mode) {
            case MSG_SEARCH -> " Message Search ";
            case SENDER_SEARCH -> " Find Sender ";
            case SENDER_MESSAGES -> " Messages from: "
                    + (p.selectedSender != null ? p.selectedSender.name() : "?") + " ";
            default -> " Find Chat ";
        };
        g.putString(left + 2, top, title);

        // search input row with cursor
        String cursor = "> " + p.query + "_";
        if (cursor.length() > innerWidth) cursor = cursor.substring(cursor.length() - innerWidth);
        g.setBackgroundColor(CatppuccinMocha.SURFACE0);
        g.setForegroundColor(CatppuccinMocha.TEXT);
        g.putString(left + 2, top + 1, TextRenderer.padRight(cursor, innerWidth));

        // divider between input and results
        g.setBackgroundColor(CatppuccinMocha.SURFACE0);
        g.setForegroundColor(CatppuccinMocha.OVERLAY1);
        g.putString(left, top + 2, "├" + "─".repeat(popupWidth - 2) + "┤");

        int resultsAreaHeight = popupHeight - 4;
        int resultsTop = top + 3;
        switch (p.mode) {
            case MSG_SEARCH ->
                    renderMsgResults(g, left, resultsTop, innerWidth, resultsAreaHeight);
            case SENDER_SEARCH ->
                    renderSenderResults(g, left, resultsTop, innerWidth, resultsAreaHeight);
            case SENDER_MESSAGES ->
                    renderSenderMsgResults(g, left, resultsTop, innerWidth, resultsAreaHeight);
            default ->
                    renderChatResults(g, left, resultsTop, innerWidth, resultsAreaHeight);
        }

        // footer hint at the bottom of the popup
        g.setBackgroundColor(CatppuccinMocha.SURFACE0);
        g.setForegroundColor(CatppuccinMocha.OVERLAY0);
        String hint = switch (p.mode) {
            case MSG_SEARCH -> "↑↓ navigate  Enter select / search  Esc close";
            case SENDER_SEARCH -> "↑↓ navigate  Enter drill into messages  Esc close";
            case SENDER_MESSAGES -> "↑↓ navigate  Enter jump to message  Esc back to senders";
            default -> "↑↓ navigate  Enter open  Esc close";
        };
        g.putString(left + 2, top + popupHeight - 2,
                TextRenderer.padRight(TextRenderer.clip(hint, innerWidth), innerWidth));
    }

    private void renderChatResults(TextGraphics g, int left, int top,
                                   int innerWidth, int visibleRows) {
        adjustScroll(visibleRows);
        List<ChatModel> results = p.chatResults;

        for (int row = 0; row < visibleRows; row++) {
            int idx = p.scrollOffset + row;
            boolean sel = (idx == p.selectedIndex);
            var bg = sel ? CatppuccinMocha.SURFACE1 : CatppuccinMocha.SURFACE0;

            clearRow(g, bg, left + 1, top + row, innerWidth + 2);
            if (idx >= results.size()) continue;
            ChatModel chat = results.get(idx);

            g.setForegroundColor(sel ? CatppuccinMocha.MAUVE : CatppuccinMocha.OVERLAY0);
            g.putString(left + 1, top + row, sel ? "▶" : " ");

            int unreadWidth = chat.unreadCount() > 0 ? 6 : 0;
            int timeWidth = 6;
            int titleWidth = innerWidth - unreadWidth - timeWidth - 2;
            g.setForegroundColor(sel ? CatppuccinMocha.TEXT : CatppuccinMocha.SUBTEXT1);
            g.putString(left + 2, top + row,
                    TextRenderer.padRight(TextRenderer.clip(chat.title(), Math.max(0, titleWidth)), titleWidth));

            if (chat.unreadCount() > 0) {
                g.setForegroundColor(chat.isMuted() ? CatppuccinMocha.OVERLAY0 : CatppuccinMocha.RED);
                g.putString(left + 2 + titleWidth + 1, top + row,
                        TextRenderer.padRight("[" + Math.min(chat.unreadCount(), 99) + "]", 4));
            }
            if (chat.lastMessageTime() > 0) {
                g.setForegroundColor(CatppuccinMocha.OVERLAY1);
                g.putString(left + 2 + innerWidth - timeWidth, top + row,
                        TIME_FMT.format(Instant.ofEpochSecond(chat.lastMessageTime())));
            }
        }
    }

    private void renderMsgResults(TextGraphics g, int left, int top,
                                  int innerWidth, int visibleRows) {
        if (p.msgSearchPending) {
            PopupDraw.renderStatusRow(g, left, top, innerWidth, visibleRows,
                    "Searching...", CatppuccinMocha.OVERLAY1);
            return;
        }
        List<MessageSearchResult> results = p.msgResults;
        if (results.isEmpty()) {
            PopupDraw.renderStatusRow(g, left, top, innerWidth, visibleRows,
                    "No results — press Enter to search", CatppuccinMocha.OVERLAY0);
            return;
        }
        adjustScroll(visibleRows);
        renderMessageResultRows(g, left, top, innerWidth, visibleRows, results, 16);
    }

    private void renderSenderResults(TextGraphics g, int left, int top,
                                     int innerWidth, int visibleRows) {
        List<SenderInfo> results = p.senderResults;
        if (results.isEmpty()) {
            PopupDraw.renderStatusRow(g, left, top, innerWidth, visibleRows,
                    "No senders found in loaded history", CatppuccinMocha.OVERLAY0);
            return;
        }
        adjustScroll(visibleRows);

        for (int row = 0; row < visibleRows; row++) {
            int idx = p.scrollOffset + row;
            boolean sel = (idx == p.selectedIndex);
            var bg = sel ? CatppuccinMocha.SURFACE1 : CatppuccinMocha.SURFACE0;

            clearRow(g, bg, left + 1, top + row, innerWidth + 2);
            if (idx >= results.size()) continue;
            SenderInfo s = results.get(idx);

            g.setForegroundColor(sel ? CatppuccinMocha.MAUVE : CatppuccinMocha.OVERLAY0);
            g.putString(left + 1, top + row, sel ? "▶" : " ");

            int countWidth = 6;
            int nameWidth = innerWidth - countWidth - 2;
            g.setForegroundColor(sel ? CatppuccinMocha.TEXT : CatppuccinMocha.SUBTEXT1);
            g.putString(left + 2, top + row,
                    TextRenderer.padRight(TextRenderer.clip(s.name(), nameWidth), nameWidth));
            g.setForegroundColor(CatppuccinMocha.OVERLAY1);
            g.putString(left + 2 + nameWidth + 1, top + row, s.messageCount() + " msg");
        }
    }

    private void renderSenderMsgResults(TextGraphics g, int left, int top,
                                        int innerWidth, int visibleRows) {
        if (p.senderSearchPending) {
            PopupDraw.renderStatusRow(g, left, top, innerWidth, visibleRows,
                    "Searching...", CatppuccinMocha.OVERLAY1);
            return;
        }
        List<MessageSearchResult> results = p.senderMsgResults;
        if (results.isEmpty()) {
            String msg = p.query.length() > 0
                    ? "No matches — press Enter to search via Telegram"
                    : "No history loaded — type to search via Telegram";
            PopupDraw.renderStatusRow(g, left, top, innerWidth, visibleRows,
                    msg, CatppuccinMocha.OVERLAY0);
            return;
        }
        adjustScroll(visibleRows);
        renderMessageResultRows(g, left, top, innerWidth, visibleRows, results, 14);
    }

    // shared row renderer used by both message search and sender message results
    private void renderMessageResultRows(TextGraphics g, int left, int top,
                                         int innerWidth, int visibleRows,
                                         List<MessageSearchResult> results, int maxChatNameWidth) {
        for (int row = 0; row < visibleRows; row++) {
            int idx = p.scrollOffset + row;
            boolean sel = (idx == p.selectedIndex);
            var bg = sel ? CatppuccinMocha.SURFACE1 : CatppuccinMocha.SURFACE0;

            clearRow(g, bg, left + 1, top + row, innerWidth + 2);
            if (idx >= results.size()) continue;
            MessageSearchResult r = results.get(idx);

            g.setForegroundColor(sel ? CatppuccinMocha.MAUVE : CatppuccinMocha.OVERLAY0);
            g.putString(left + 1, top + row, sel ? "▶" : " ");

            String time = r.timestamp() > 0
                    ? "[" + TIME_FMT.format(Instant.ofEpochSecond(r.timestamp())) + "]"
                    : "[--:--]";
            g.setForegroundColor(CatppuccinMocha.OVERLAY1);
            g.putString(left + 2, top + row, time);

            ChatModel chat = p.chatService.getChat(r.chatId());
            String chatName = chat != null ? chat.title() : "Chat " + r.chatId();
            int chatNameWidth = Math.min(chatName.length(), maxChatNameWidth);
            g.setForegroundColor(CatppuccinMocha.TEAL);
            g.putString(left + 10, top + row,
                    TextRenderer.padRight(TextRenderer.clip(chatName, chatNameWidth), chatNameWidth));

            int snippetStart = left + 10 + chatNameWidth + 3;
            int snippetWidth = left + 2 + innerWidth - snippetStart;
            if (snippetWidth > 4) {
                g.setForegroundColor(CatppuccinMocha.OVERLAY0);
                g.putString(left + 10 + chatNameWidth, top + row, " — ");
                g.setForegroundColor(sel ? CatppuccinMocha.TEXT : CatppuccinMocha.SUBTEXT0);
                g.putString(snippetStart, top + row, TextRenderer.clip(r.text(), snippetWidth));
            }
        }
    }

    private void renderCommandBar(TextGraphics g, int screenWidth, int screenHeight) {
        int popupWidth = Math.min(52, screenWidth - 4);
        int left = (screenWidth - popupWidth) / 2;
        int top = (screenHeight - 3) / 2;

        PopupDraw.fillBackground(g, left, top, popupWidth, 3);
        PopupDraw.drawBorder(g, left, top, popupWidth, 3);

        int innerWidth = popupWidth - 4;
        String line = ":" + p.query + "_";
        if (line.length() > innerWidth) line = line.substring(line.length() - innerWidth);
        g.setBackgroundColor(CatppuccinMocha.SURFACE0);
        g.setForegroundColor(CatppuccinMocha.TEXT);
        g.putString(left + 2, top + 1, TextRenderer.padRight(line, innerWidth));
    }

    private void adjustScroll(int visibleRows) {
        if (p.selectedIndex < p.scrollOffset) {
            p.scrollOffset = p.selectedIndex;
        } else if (p.selectedIndex >= p.scrollOffset + visibleRows) {
            p.scrollOffset = p.selectedIndex - visibleRows + 1;
        }
    }

    private static void clearRow(TextGraphics g, com.googlecode.lanterna.TextColor bg,
                                  int x, int y, int width) {
        g.setBackgroundColor(bg);
        g.setForegroundColor(bg);
        g.putString(x, y, " ".repeat(width));
    }
}
