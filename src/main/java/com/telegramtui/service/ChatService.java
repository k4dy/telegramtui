package com.telegramtui.service;

import com.telegramtui.telegram.TelegramClient;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.telegramtui.model.ChatModel;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ChatService {

	// folder ID for the main chat list
	public static final int MAIN_LIST_ID = 0;
	private final TelegramClient client;
	private volatile boolean started = false;
	private final ConcurrentHashMap<Long, ChatModel> chats = new ConcurrentHashMap<>();
	// maps chat id -> (folder id -> sort order)
	private final ConcurrentHashMap<Long, ConcurrentHashMap<Integer, Long>> chatPositions = new ConcurrentHashMap<>();


	public ChatService(TelegramClient client) {
		this.client = client;
	}

	private static int listToFolderId(JsonObject list) {
		if (list == null) {
			return -1;
		}
		String type = list.get("@type").getAsString();
		if ("chatListMain".equals(type)) {
			return MAIN_LIST_ID;
		}
		if ("chatListFolder".equals(type)) {
			return list.get("chat_folder_id").getAsInt();
		}
		return -1;
	}

	public boolean isStarted() {
		return started;
	}

	public void start() {
		started = true;
		client.send("{\"@type\":\"loadChats\",\"chat_list\":null,\"limit\":100}", null);
	}

	public void onUpdate(String json) {
		String type = TelegramClient.extractStringField(json, "@type");
		if (type == null) return;
		JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
		switch (type) {
			case "updateNewChat" -> handleNewChat(obj.getAsJsonObject("chat"));
			case "updateChatLastMessage" -> handleLastMessage(obj);
			case "updateChatReadInbox" -> handleReadInbox(obj);
			case "updateChatPosition" -> handleChatPosition(obj);
			case "updateChatNotificationSettings" -> handleNotificationSettings(obj);
		}
	}

	// Sets unread count to 0 when a chat is opened (optimistic update)
	public void markRead(long chatId) {
		ChatModel existing = chats.get(chatId);
		if (existing == null || existing.unreadCount() == 0) return;
		chats.put(chatId, new ChatModel(
				existing.id(), existing.title(),
				existing.lastMessage(), existing.lastMessageTime(),
				0, existing.chatType(), existing.isMuted()));
	}

	public ChatModel getChat(long chatId) {
		return chats.get(chatId);
	}

	public List<ChatModel> getAllChats() {
		return chats.values().stream()
				.sorted((a, b) -> Long.compare(b.lastMessageTime(), a.lastMessageTime()))
				.collect(Collectors.toList());
	}

	public List<ChatModel> getChatsForList(int folderId) {
		return chats.values().stream()
				.filter(c -> {
					ConcurrentHashMap<Integer, Long> posMap = chatPositions.get(c.id());
					return posMap != null && posMap.containsKey(folderId);
				})
				.sorted((a, b) -> Long.compare(getPosition(b.id(), folderId), getPosition(a.id(), folderId)))
				.collect(Collectors.toList());
	}

	private long getPosition(long chatId, int folderId) {
		ConcurrentHashMap<Integer, Long> posMap = chatPositions.get(chatId);
		return posMap == null ? 0L : posMap.getOrDefault(folderId, 0L);
	}

	private void handleNewChat(JsonObject chat) {
		if (chat == null) return;
		long id = chat.get("id").getAsLong();
		String title = chat.has("title") ? chat.get("title").getAsString() : "";
		int unread = chat.has("unread_count") ? chat.get("unread_count").getAsInt() : 0;
		String chatType = chat.has("type")
				                  ? chat.getAsJsonObject("type").get("@type").getAsString()
				                  : "chatTypeUnknown";
		boolean muted = parseMuted(chat.getAsJsonObject("notification_settings"));
		String[] msg = extractLastMessage(chat.getAsJsonObject("last_message"));
		chats.put(id, new ChatModel(id, title, msg[0], Long.parseLong(msg[1]), unread, chatType, muted));

		if (chat.has("positions")) {
			parsePositions(id, chat.getAsJsonArray("positions"));
		}

	}

	private void handleLastMessage(JsonObject obj) {
		long chatId = obj.get("chat_id").getAsLong();
		ChatModel existing = chats.get(chatId);
		if (existing == null) return;
		String[] msg = extractLastMessage(obj.getAsJsonObject("last_message"));
		chats.put(chatId, new ChatModel(
				existing.id(), existing.title(),
				msg[0], Long.parseLong(msg[1]),
				existing.unreadCount(), existing.chatType(), existing.isMuted()));
	}

	private void handleChatPosition(JsonObject obj) {
		long chatId = obj.get("chat_id").getAsLong();
		JsonObject position = obj.getAsJsonObject("position");
		if (position == null) {
			return;
		}
		int folderId = listToFolderId(position.getAsJsonObject("list"));
		if (folderId < 0) {
			return;
		}
		long order = position.get("order").getAsLong();
		ConcurrentHashMap<Integer, Long> posMap = chatPositions.computeIfAbsent(chatId, k -> new ConcurrentHashMap<>());
		if (order == 0) {
			posMap.remove(folderId);
		} else {
			posMap.put(folderId, order);
		}
	}

	private void parsePositions(long chatId, JsonArray positions) {
		ConcurrentHashMap<Integer, Long> posMap =
				chatPositions.computeIfAbsent(chatId, k -> new ConcurrentHashMap<>());
		for (JsonElement el : positions) {
			JsonObject pos = el.getAsJsonObject();
			int folderId = listToFolderId(pos.getAsJsonObject("list"));
			if (folderId < 0) continue;
			long order = pos.get("order").getAsLong();
			posMap.put(folderId, order);
		}
	}

	private void handleReadInbox(JsonObject obj) {
		long chatId = obj.get("chat_id").getAsLong();
		int unread = obj.get("unread_count").getAsInt();
		ChatModel existing = chats.get(chatId);
		if (existing == null) return;
		chats.put(chatId, new ChatModel(
				existing.id(), existing.title(),
				existing.lastMessage(), existing.lastMessageTime(),
				unread, existing.chatType(), existing.isMuted()));
	}

	private void handleNotificationSettings(JsonObject obj) {
		long chatId = obj.get("chat_id").getAsLong();
		ChatModel existing = chats.get(chatId);
		if (existing == null) return;
		boolean muted = parseMuted(obj.getAsJsonObject("notification_settings"));
		chats.put(chatId, new ChatModel(
				existing.id(), existing.title(),
				existing.lastMessage(), existing.lastMessageTime(),
				existing.unreadCount(), existing.chatType(), muted));
	}

	private static boolean parseMuted(JsonObject settings) {
		if (settings == null || settings.isJsonNull()) return false;
		return settings.has("mute_for") && settings.get("mute_for").getAsInt() > 0;
	}

	private String[] extractLastMessage(JsonObject msg) {
		if (msg == null || msg.isJsonNull()) return new String[]{"", "0"};
		long date = msg.has("date") ? msg.get("date").getAsLong() : 0L;
		String snippet = "";
		JsonObject content = msg.getAsJsonObject("content");
		if (content != null) {
			String contentType = content.get("@type").getAsString();
			if ("messageText".equals(contentType)) {
				JsonObject text = content.getAsJsonObject("text");
				snippet = (text != null && text.has("text")) ?
						          text.get("text").getAsString() : "";
			} else {
				snippet = "[" + contentType.replace("message", "") + "]";
			}
		}
		return new String[]{snippet, String.valueOf(date)};
	}
}

