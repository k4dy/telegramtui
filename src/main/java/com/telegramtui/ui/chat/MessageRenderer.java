package com.telegramtui.ui.chat;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.telegramtui.model.MessageModel;
import com.telegramtui.ui.common.CatppuccinMocha;
import com.telegramtui.ui.common.TextRenderer;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MessageRenderer {

    private MessageRenderer() {}

    public static List<List<MessageModel>> groupBySender(List<MessageModel> messages) {
        List<List<MessageModel>> groups = new ArrayList<>();
        for (MessageModel m : messages) {
            if (groups.isEmpty()
                    || groups.get(groups.size() - 1).get(0).senderId() != m.senderId()) {
                groups.add(new ArrayList<>());
            }
            groups.get(groups.size() - 1).add(m);
        }
        return groups;
    }

    public static int groupHeight(List<MessageModel> group, int panelWidth, boolean isGroupChat,
            Map<Long, MessageModel> msgById) {
        boolean isOutgoing = group.get(0).isOutgoing();
        int wrapWidth = textWrapWidth(isOutgoing, panelWidth);
        int rows = 2; // top margin row + name/timestamp row
        for (MessageModel m : group) {
            if (!m.forwardedFrom().isEmpty()) {
                rows++; // forwarded header row
            }
            if (m.replyToMessageId() > 0 && msgById.containsKey(m.replyToMessageId())) {
                rows++; // gap row before reply block
                rows++; // reply header row
            }
            rows += TextRenderer.wrap(m.text().isEmpty() ? " " : m.text(), wrapWidth).size();
        }
        rows++; // padding row
        return rows;
    }

    public static void renderGroup(TextGraphics g, List<MessageModel> group,
            int panelX, int panelY, int panelWidth, boolean isGroupChat, int selectedPos,
            int clipTop, int clipBottom, Map<Long, MessageModel> msgById) {
        MessageModel first = group.get(0);
        boolean isOutgoing = first.isOutgoing();

        // Highlight the whole group background when a message in it is selected
        TextColor bg = (selectedPos >= 0) ? CatppuccinMocha.SURFACE1
                : isOutgoing ? CatppuccinMocha.SURFACE0 : CatppuccinMocha.BASE;
        TextColor nameFg = isOutgoing ? CatppuccinMocha.OVERLAY1 : CatppuccinMocha.LAVENDER;

        int row = panelY;

        if (row >= clipTop && row <= clipBottom) {
            fillRow(g, bg, panelX, row, panelWidth);
        }
        row++;

        String name = isOutgoing
                ? (first.senderName().isEmpty() ? "You" : first.senderName())
                : first.senderName();
        MessageModel last = group.get(group.size() - 1);
        String ts = formatTimestamp(last.timestamp());
        if (last.isEdited()) ts = "[edited] " + ts;

        if (row >= clipTop && row <= clipBottom) {
            fillRow(g, bg, panelX, row, panelWidth);
            g.setForegroundColor(nameFg);
            g.putString(panelX + 1, row, TextRenderer.clip(name, panelWidth - ts.length() - 3));
            g.setForegroundColor(CatppuccinMocha.OVERLAY0);
            g.putString(panelX + panelWidth - ts.length() - 1, row, ts);
        }
        row++;

        int wrapWidth = textWrapWidth(isOutgoing, panelWidth);
        int msgIdx = 0;
        for (MessageModel m : group) {
            TextColor msgBg = (msgIdx == selectedPos) ? CatppuccinMocha.SURFACE2 : bg;

            if (!m.forwardedFrom().isEmpty()) {
                if (row >= clipTop && row <= clipBottom) {
                    fillRow(g, msgBg, panelX, row, panelWidth);
                    g.setForegroundColor(CatppuccinMocha.MAUVE);
                    String fwdLine = "⟫ Forwarded from " + m.forwardedFrom();
                    g.putString(panelX + 1, row, TextRenderer.clip(fwdLine, panelWidth - 2));
                }
                row++;
            }

            if (m.replyToMessageId() > 0) {
                MessageModel orig = msgById.get(m.replyToMessageId());
                if (orig != null) {
                    if (row >= clipTop && row <= clipBottom) {
                        fillRow(g, msgBg, panelX, row, panelWidth);
                    }
                    row++;
                    if (row >= clipTop && row <= clipBottom) {
                        fillRow(g, msgBg, panelX, row, panelWidth);
                        String origSender = orig.senderName().isEmpty() ? "You" : orig.senderName();
                        String replyHeader = "↩ " + origSender + ": "
                                + TextRenderer.clip(orig.text(), panelWidth - origSender.length() - 6);
                        g.setForegroundColor(CatppuccinMocha.TEAL);
                        g.putString(panelX + 1, row, replyHeader);
                    }
                    row++;
                }
            }

            List<String> lines = TextRenderer.wrap(m.text().isEmpty() ? " " : m.text(), wrapWidth);
            boolean isPlainText = "messageText".equals(m.contentType())
                    || "unknown".equals(m.contentType());
            TextColor textFg = isPlainText ? CatppuccinMocha.TEXT : CatppuccinMocha.OVERLAY1;
            for (String line : lines) {
                if (row >= clipTop && row <= clipBottom) {
                    fillRow(g, msgBg, panelX, row, panelWidth);
                    g.setForegroundColor(textFg);
                    g.putString(panelX + 1, row, isOutgoing ? "❯ " + line : line);
                }
                row++;
            }
            msgIdx++;
        }

        if (row >= clipTop && row <= clipBottom) {
            fillRow(g, bg, panelX, row, panelWidth);
        }
    }

    // Outgoing messages leave room for the ❯ prefix
    private static int textWrapWidth(boolean isOutgoing, int panelWidth) {
        return isOutgoing ? panelWidth - 4 : panelWidth - 2;
    }

    private static void fillRow(TextGraphics g, TextColor bg, int x, int row, int width) {
        g.setBackgroundColor(bg);
        g.setForegroundColor(bg);
        g.putString(x, row, " ".repeat(width));
    }

    private static String formatTimestamp(long unixSeconds) {
        LocalDateTime dt = LocalDateTime.ofEpochSecond(unixSeconds, 0, ZoneOffset.UTC);
        return String.format("%02d:%02d", dt.getHour(), dt.getMinute());
    }
}
