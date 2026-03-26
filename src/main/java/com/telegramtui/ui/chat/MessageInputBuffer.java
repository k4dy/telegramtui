package com.telegramtui.ui.chat;

import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.telegramtui.model.ChatModel;
import com.telegramtui.model.MessageModel;
import com.telegramtui.service.MessageService;
import com.telegramtui.ui.common.CatppuccinMocha;
import com.telegramtui.ui.common.TextRenderer;

import java.util.List;

public class MessageInputBuffer {

    public enum Result { CONSUMED, SENT, CANCELLED }

    private final MessageService messageService;
    private final StringBuilder buffer = new StringBuilder();

    private boolean insertMode = false;
    private long editingMessageId = 0;
    private boolean deletePending = false;
    private long replyToMessageId = 0;
    private String replyToSenderName = "";
    private String replyToText = "";

    public MessageInputBuffer(MessageService messageService) {
        this.messageService = messageService;
    }

    public boolean isInInsertMode() {
        return insertMode;
    }

    public boolean isDeletePending() {
        return deletePending;
    }

    public boolean isEmpty() {
        return buffer.isEmpty();
    }

    public String getModeHint() {
        if (insertMode) return "-- INSERT --";
        return "";
    }

    public void startInsert() {
        insertMode = true;
    }

    public void startEdit(MessageModel m) {
        editingMessageId = m.id();
        buffer.setLength(0);
        buffer.append(m.text());
        insertMode = true;
    }

    public void startReply(MessageModel m) {
        replyToMessageId = m.id();
        replyToSenderName = m.isOutgoing() ? "You" : m.senderName();
        replyToText = m.text();
        insertMode = true;
    }

    public void startDelete() {
        deletePending = true;
    }

    public Result handleInsertKey(KeyStroke key, ChatModel chat) {
        if (key.getKeyType() == KeyType.Escape) {
            insertMode = false;
            if (editingMessageId > 0) {
                // cancel edit: clear edit context and the buffer (which held the old message text)
                editingMessageId = 0;
                buffer.setLength(0);
            }
            return Result.CANCELLED;
        }
        if (key.getKeyType() == KeyType.Enter) {
            String text = buffer.toString().trim();
            if (!text.isEmpty() && chat != null) {
                if (editingMessageId > 0) {
                    messageService.editMessage(chat.id(), editingMessageId, text);
                    editingMessageId = 0;
                } else {
                    messageService.sendMessage(chat.id(), text, replyToMessageId);
                    replyToMessageId = 0;
                    replyToSenderName = "";
                    replyToText = "";
                    buffer.setLength(0);
                    return Result.SENT;
                }
            }
            buffer.setLength(0);
            insertMode = false;
            return Result.SENT;
        }
        if (key.getKeyType() == KeyType.Backspace) {
            if (!buffer.isEmpty()) buffer.deleteCharAt(buffer.length() - 1);
            return Result.CONSUMED;
        }
        if (key.getKeyType() == KeyType.Character) {
            buffer.append(key.getCharacter());
            return Result.CONSUMED;
        }
        return Result.CONSUMED;
    }

    public boolean handleDeleteConfirmation(KeyStroke key, ChatModel chat,
                                            int selectedMsgIndex, List<MessageModel> msgs) {
        boolean deleted = false;
        if (key.getKeyType() == KeyType.Character && key.getCharacter() == 'y') {
            if (chat != null && selectedMsgIndex >= 0 && selectedMsgIndex < msgs.size()) {
                messageService.deleteMessages(chat.id(), msgs.get(selectedMsgIndex).id());
                deleted = true;
            }
        }
        deletePending = false;
        return deleted;
    }

    public List<String> getWrappedLines(int wrapWidth) {
        if (buffer.isEmpty()) return List.of("");
        return TextRenderer.wrap(buffer.toString(), wrapWidth);
    }

    public void renderReplyContext(TextGraphics g, int x, int y, int w) {
        g.setBackgroundColor(CatppuccinMocha.BASE);
        if (replyToMessageId > 0) {
            String line = "↩ " + replyToSenderName + ": "
                    + TextRenderer.clip(replyToText, w - replyToSenderName.length() - 4);
            g.setForegroundColor(CatppuccinMocha.TEAL);
            g.putString(x, y, TextRenderer.padRight(line, w));
        } else {
            g.setForegroundColor(CatppuccinMocha.BASE);
            g.putString(x, y, " ".repeat(w));
        }
    }

    public void renderInput(TextGraphics g, int x, int y, int w,
                            List<String> inputLines, int inputHeight, String hintText) {
        g.setBackgroundColor(CatppuccinMocha.BASE);
        if (deletePending) {
            g.setForegroundColor(CatppuccinMocha.RED);
            g.putString(x, y, TextRenderer.padRight("Delete this message? (y / n)", w));
        } else if (hintText != null) {
            g.setForegroundColor(CatppuccinMocha.OVERLAY2);
            g.putString(x, y, TextRenderer.padRight("  " + hintText, w));
        } else {
            g.setForegroundColor(CatppuccinMocha.TEXT);
            for (int li = 0; li < inputHeight; li++) {
                String lineText = li < inputLines.size() ? inputLines.get(li) : "";
                String prefix = (li == 0) ? "❯ " : "  ";
                String cursor = (insertMode && li == inputHeight - 1) ? "│" : "";
                g.putString(x, y + li, TextRenderer.padRight(prefix + lineText + cursor, w));
            }
        }
    }

}
