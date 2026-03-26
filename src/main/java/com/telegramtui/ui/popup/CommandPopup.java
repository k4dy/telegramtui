package com.telegramtui.ui.popup;

import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.telegramtui.model.ChatModel;
import com.telegramtui.model.MessageSearchResult;
import com.telegramtui.model.SenderInfo;
import com.telegramtui.service.ChatService;
import com.telegramtui.service.MessageService;

import java.util.List;
import java.util.stream.Collectors;

public class CommandPopup {

    enum Mode { HIDDEN, COMMAND, CHAT_SEARCH, MSG_SEARCH, SENDER_SEARCH, SENDER_MESSAGES, HELP }

    // package-private so PopupRenderer can read them directly
    Mode mode = Mode.HIDDEN;
    final StringBuilder query = new StringBuilder();

    List<ChatModel> chatResults = List.of();
    volatile List<MessageSearchResult> msgResults = List.of();
    volatile boolean msgSearchPending = false;

    List<SenderInfo> senderResults = List.of();
    SenderInfo selectedSender = null;
    volatile List<MessageSearchResult> senderMsgResults = List.of();
    volatile boolean senderSearchPending = false;

    int selectedIndex = 0;
    int scrollOffset = 0;

    // used by PopupRenderer to look up chat names in result rows
    final ChatService chatService;

    private final MessageService messageService;
    private final PopupRenderer renderer;

    private List<SenderInfo> allSenders = List.of();
    private volatile List<MessageSearchResult> senderBaseResults = List.of();
    private volatile boolean senderJumpWhenReady = false;
    private PopupAction pendingAction = null;

    public CommandPopup(ChatService chatService, MessageService messageService) {
        this.chatService = chatService;
        this.messageService = messageService;
        this.renderer = new PopupRenderer(this);
    }

    public void openCommand() {
        mode = Mode.COMMAND;
        query.setLength(0);
        pendingAction = null;
    }

    public void openChatSearch() {
        mode = Mode.CHAT_SEARCH;
        query.setLength(0);
        selectedIndex = 0;
        scrollOffset = 0;
        pendingAction = null;
        msgResults = List.of();
        msgSearchPending = false;
        chatResults = chatService.getAllChats();
    }

    public void openMsgSearch() {
        mode = Mode.MSG_SEARCH;
        query.setLength(0);
        selectedIndex = 0;
        scrollOffset = 0;
        pendingAction = null;
        msgResults = List.of();
        msgSearchPending = false;
    }

    public void openSenderSearch() {
        mode = Mode.SENDER_SEARCH;
        query.setLength(0);
        selectedIndex = 0;
        scrollOffset = 0;
        pendingAction = null;
        selectedSender = null;
        senderMsgResults = List.of();
        allSenders = buildSenderList();
        senderResults = allSenders;
    }

    public void close() {
        mode = Mode.HIDDEN;
        query.setLength(0);
        msgResults = List.of();
        msgSearchPending = false;
        selectedSender = null;
        senderMsgResults = List.of();
        senderBaseResults = List.of();
        senderSearchPending = false;
        senderJumpWhenReady = false;
    }

    public boolean isActive() {
        return mode != Mode.HIDDEN;
    }

    public String getModeLabel() {
        return switch (mode) {
            case COMMAND -> "-- COMMAND --";
            case CHAT_SEARCH -> "-- SEARCH: chats --";
            case MSG_SEARCH -> "-- SEARCH: messages --";
            case SENDER_SEARCH -> "-- SEARCH: sender --";
            case SENDER_MESSAGES -> "-- SEARCH: messages from "
                    + (selectedSender != null ? selectedSender.name() : "?") + " --";
            default -> "";
        };
    }

    // returns and clears any pending action — called each frame by InputRouter
    public PopupAction consumeAction() {
        PopupAction a = pendingAction;
        pendingAction = null;
        return a;
    }

    public boolean handleKey(KeyStroke key) {
        if (mode == Mode.HIDDEN) return false;

        if (mode == Mode.HELP) {
            close();
            return true;
        }

        if (mode == Mode.COMMAND) {
            return switch (key.getKeyType()) {
                case Escape -> { close(); yield true; }
                case Enter -> { confirmCommand(); yield true; }
                case Backspace -> {
                    if (!query.isEmpty()) query.deleteCharAt(query.length() - 1);
                    yield true;
                }
                case Character -> { query.append(key.getCharacter()); yield true; }
                default -> true;
            };
        }

        // in sender messages, Esc goes back to the sender list instead of closing
        if (mode == Mode.SENDER_MESSAGES && key.getKeyType() == KeyType.Escape) {
            mode = Mode.SENDER_SEARCH;
            query.setLength(0);
            selectedIndex = 0;
            scrollOffset = 0;
            senderResults = allSenders;
            return true;
        }

        return switch (key.getKeyType()) {
            case Escape -> { close(); yield true; }
            case Enter -> { confirmSelected(); yield true; }
            case ArrowUp -> { moveSelection(-1); yield true; }
            case ArrowDown -> { moveSelection(1); yield true; }
            case Backspace -> {
                if (!query.isEmpty()) {
                    query.deleteCharAt(query.length() - 1);
                    updateResults();
                }
                yield true;
            }
            case Character -> {
                query.append(key.getCharacter());
                updateResults();
                yield true;
            }
            default -> true;
        };
    }

    public void render(TextGraphics g, int screenWidth, int screenHeight) {
        renderer.render(g, screenWidth, screenHeight);
    }

    private void updateResults() {
        if (mode == Mode.CHAT_SEARCH) {
            String lower = query.toString().toLowerCase();
            chatResults = chatService.getAllChats().stream()
                    .filter(c -> lower.isEmpty() || c.title().toLowerCase().contains(lower))
                    .collect(Collectors.toList());
            selectedIndex = 0;
            scrollOffset = 0;
        } else if (mode == Mode.SENDER_SEARCH) {
            String lower = query.toString().toLowerCase();
            senderResults = allSenders.stream()
                    .filter(s -> lower.isEmpty() || s.name().toLowerCase().contains(lower))
                    .collect(Collectors.toList());
            selectedIndex = 0;
            scrollOffset = 0;
        } else if (mode == Mode.SENDER_MESSAGES) {
            String lower = query.toString().toLowerCase();
            List<MessageSearchResult> base = senderBaseResults.isEmpty()
                    ? messageService.getMessagesForSender(
                            selectedSender.senderId(), selectedSender.associatedChatId())
                    : senderBaseResults;
            senderMsgResults = base.stream()
                    .filter(m -> lower.isEmpty() || m.text().toLowerCase().contains(lower))
                    .collect(Collectors.toList());
            selectedIndex = 0;
            scrollOffset = 0;
        }
        // MSG_SEARCH updates only on Enter (TDLib search call)
    }

    private void moveSelection(int delta) {
        int size = currentResultSize();
        if (size == 0) return;
        selectedIndex = Math.max(0, Math.min(size - 1, selectedIndex + delta));
    }

    private int currentResultSize() {
        return switch (mode) {
            case MSG_SEARCH -> msgResults.size();
            case SENDER_SEARCH -> senderResults.size();
            case SENDER_MESSAGES -> senderMsgResults.size();
            default -> chatResults.size();
        };
    }

    private void confirmCommand() {
        String q = query.toString().trim();
        if (q.isEmpty()) { close(); return; }
        if ("help".equals(q)) {
            mode = Mode.HELP;
            query.setLength(0);
        } else {
            pendingAction = new PopupAction.PlainCommand(q);
            close();
        }
    }

    private void confirmSelected() {
        String q = query.toString().trim();

        if (mode == Mode.MSG_SEARCH) {
            List<MessageSearchResult> current = msgResults;
            if (!current.isEmpty() && selectedIndex < current.size()) {
                MessageSearchResult r = current.get(selectedIndex);
                pendingAction = new PopupAction.JumpToMessage(r.chatId(), r.messageId());
                close();
            } else {
                msgSearchPending = true;
                msgResults = List.of();
                selectedIndex = 0;
                scrollOffset = 0;
                messageService.searchMessages(q, results -> {
                    msgResults = results;
                    msgSearchPending = false;
                });
            }
        } else if (mode == Mode.SENDER_SEARCH) {
            if (!senderResults.isEmpty() && selectedIndex < senderResults.size()) {
                selectedSender = senderResults.get(selectedIndex);
                senderBaseResults = List.of();
                senderMsgResults = messageService.getMessagesForSender(
                        selectedSender.senderId(), selectedSender.associatedChatId());
                mode = Mode.SENDER_MESSAGES;
                query.setLength(0);
                selectedIndex = 0;
                scrollOffset = 0;
            }
        } else if (mode == Mode.SENDER_MESSAGES) {
            List<MessageSearchResult> current = senderMsgResults;
            if (!current.isEmpty() && selectedIndex < current.size()) {
                MessageSearchResult r = current.get(selectedIndex);
                pendingAction = new PopupAction.JumpToMessage(r.chatId(), r.messageId());
                close();
            } else if (senderSearchPending) {
                senderJumpWhenReady = true;
            } else {
                String searchQ = query.toString().trim();
                if (!searchQ.isEmpty() && selectedSender != null
                        && selectedSender.associatedChatId() != 0) {
                    senderSearchPending = true;
                    senderMsgResults = List.of();
                    selectedIndex = 0;
                    scrollOffset = 0;
                    messageService.searchChatMessages(
                            selectedSender.associatedChatId(), searchQ, results -> {
                                if (senderJumpWhenReady && results.size() == 1) {
                                    pendingAction = new PopupAction.JumpToMessage(
                                            results.get(0).chatId(), results.get(0).messageId());
                                    close();
                                } else {
                                    senderBaseResults = results;
                                    senderMsgResults = results;
                                    senderSearchPending = false;
                                    senderJumpWhenReady = false;
                                }
                            });
                }
            }
        } else {
            if (!chatResults.isEmpty() && selectedIndex < chatResults.size()) {
                pendingAction = new PopupAction.OpenChat(chatResults.get(selectedIndex).id());
                close();
            }
        }
    }

    // builds the sender list from private chats and from loaded message history
    private List<SenderInfo> buildSenderList() {
        java.util.Set<String> seen = new java.util.HashSet<>();
        List<SenderInfo> result = new java.util.ArrayList<>();

        for (ChatModel c : chatService.getAllChats()) {
            if (!"chatTypePrivate".equals(c.chatType())) continue;
            if (seen.add(c.title().toLowerCase())) {
                result.add(new SenderInfo(0, c.title(), 0, c.id()));
            }
        }
        for (SenderInfo s : messageService.getSenders()) {
            if (seen.add(s.name().toLowerCase())) {
                result.add(s);
            }
        }
        return result;
    }
}
