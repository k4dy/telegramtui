package com.telegramtui.model;

/**
 * A sender entry used by the @ sender-search popup.
 * Private-chat contacts have associatedChatId != 0 (and senderId == 0).
 * Group participants have senderId != 0 (and associatedChatId == 0).
 */
public record SenderInfo(long senderId, String name, int messageCount, long associatedChatId) {}
