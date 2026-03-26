package com.telegramtui.model;

public record MessageSearchResult(long chatId, long messageId, String text, long timestamp) {}
