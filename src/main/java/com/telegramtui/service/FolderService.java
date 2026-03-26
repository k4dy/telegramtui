package com.telegramtui.service;

import com.telegramtui.telegram.TelegramClient;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.telegramtui.model.FolderModel;

import java.util.ArrayList;
import java.util.List;

public class FolderService {

	private final TelegramClient client;
	private volatile List<FolderModel> folders = List.of();

	public FolderService(TelegramClient client) {
		this.client = client;
	}

	public void onUpdate(String json) {
		if (!"updateChatFolders".equals(TelegramClient.extractStringField(json, "@type"))) return;
		JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

		JsonArray filters = obj.getAsJsonArray("chat_folders");
		List<FolderModel> list = new ArrayList<>();
		for (JsonElement el : filters) {
			JsonObject f = el.getAsJsonObject();
			if (!f.has("id")) continue;
			int id = f.get("id").getAsInt();
			String title = parseFolderTitle(f);
			if (title == null) continue;
			list.add(new FolderModel(id, title));
		}

		folders = List.copyOf(list);

		// Load chats for each folder
		for (FolderModel folder : folders) {
			client.send(
					"{\"@type\":\"loadChats\",\"chat_list\":"
							+ "{\"@type\":\"chatListFolder\","
							+ "\"chat_folder_id\":" + folder.id() + "},"
							+ "\"limit\":100}", null);
		}
	}

	public List<FolderModel> getFolders() {
		return folders;
	}

	private static String parseFolderTitle(JsonObject f) {
		JsonObject name = f.getAsJsonObject("name");
		if (name == null) return null;
		// TDLib ≥ 1.8: name is a FormattedText object {text: {text: "..."}}
		JsonObject textObj = name.getAsJsonObject("text");
		if (textObj != null && textObj.has("text")) return textObj.get("text").getAsString();
		// older format: name.text is a plain string
		if (name.has("text") && name.get("text").isJsonPrimitive()) return name.get("text").getAsString();
		return null;
	}

}
