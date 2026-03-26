package com.telegramtui.ui.popup;

public sealed interface PopupAction {
    // user picked a chat to open
    record OpenChat(long chatId) implements PopupAction {}

    // user picked a specific message to jump to
    record JumpToMessage(long chatId, long messageId) implements PopupAction {}

    // user typed a plain command like "q" or "logout"
    record PlainCommand(String cmd) implements PopupAction {}
}
