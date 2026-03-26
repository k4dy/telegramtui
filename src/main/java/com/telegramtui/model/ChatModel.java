package com.telegramtui.model;

public record ChatModel(
		long id,
		String title,
		String lastMessage,
		long lastMessageTime,
		int unreadCount,
		String chatType,
		boolean isMuted) {
}

