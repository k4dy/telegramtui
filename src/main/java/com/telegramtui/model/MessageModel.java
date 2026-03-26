package com.telegramtui.model;

import java.util.List;

public record MessageModel(
		long id,
		long chatId,
		long senderId,
		String senderName,
		String text,
		String contentType,
		long fileId,
		String localFilePath,
		String forwardedFrom,
		List<String> urls,
		long timestamp,
		boolean isOutgoing,
		boolean isEdited,
		long replyToMessageId
) {
}
