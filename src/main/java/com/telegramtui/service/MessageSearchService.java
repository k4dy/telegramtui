package com.telegramtui.service;

import com.telegramtui.telegram.TelegramClient;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.telegramtui.model.MessageModel;
import com.telegramtui.model.MessageSearchResult;
import com.telegramtui.model.SenderInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class MessageSearchService {

    private static final Logger log = LoggerFactory.getLogger(MessageSearchService.class.getSimpleName());

    private final TelegramClient client;
    private final MessageParser parser;
    private final AtomicLong changeVersion;
    // shared reference to the message cache owned by MessageService
    private final ConcurrentHashMap<Long, List<MessageModel>> messageCache;

    public MessageSearchService(TelegramClient client, MessageParser parser,
                                AtomicLong changeVersion,
                                ConcurrentHashMap<Long, List<MessageModel>> messageCache) {
        this.client = client;
        this.parser = parser;
        this.changeVersion = changeVersion;
        this.messageCache = messageCache;
    }

    public void searchMessages(String query, Consumer<List<MessageSearchResult>> callback) {
        if (query == null || query.isBlank()) {
            callback.accept(List.of());
            return;
        }
        JsonObject req = new JsonObject();
        req.addProperty("@type", "searchMessages");
        req.addProperty("query", query);
        req.addProperty("limit", 30);
        req.addProperty("offset_date", 0);
        req.addProperty("offset_chat_id", 0);
        req.addProperty("offset_message_id", 0);
        client.send(req.toString(), json -> {
            try {
                JsonObject resp = JsonParser.parseString(json).getAsJsonObject();
                String type = resp.has("@type") ? resp.get("@type").getAsString() : "";
                // TDLib <1.8 returns "messages", TDLib >=1.8 returns "foundMessages"
                if (!type.equals("messages") && !type.equals("foundMessages")) {
                    log.warn("searchMessages unexpected response: {}", json);
                    callback.accept(List.of());
                    return;
                }
                List<MessageSearchResult> results = parseSearchResults(resp);
                changeVersion.incrementAndGet();
                callback.accept(results);
            } catch (Exception e) {
                log.warn("searchMessages parse error: {}", e.getMessage());
                callback.accept(List.of());
            }
        });
    }

    public void searchChatMessages(long chatId, String query,
                                   Consumer<List<MessageSearchResult>> callback) {
        if (query == null || query.isBlank()) {
            callback.accept(List.of());
            return;
        }
        JsonObject req = new JsonObject();
        req.addProperty("@type", "searchChatMessages");
        req.addProperty("chat_id", chatId);
        req.addProperty("query", query);
        req.addProperty("from_message_id", 0);
        req.addProperty("offset", 0);
        req.addProperty("limit", 30);
        client.send(req.toString(), json -> {
            try {
                JsonObject resp = JsonParser.parseString(json).getAsJsonObject();
                String type = resp.has("@type") ? resp.get("@type").getAsString() : "";
                // TDLib returns different type names depending on version
                if (!"messages".equals(type) && !"foundMessages".equals(type)
                        && !"foundChatMessages".equals(type)) {
                    log.warn("searchChatMessages unexpected response: {}", json);
                    callback.accept(List.of());
                    return;
                }
                List<MessageSearchResult> results = parseSearchResults(resp);
                changeVersion.incrementAndGet();
                callback.accept(results);
            } catch (Exception e) {
                log.warn("searchChatMessages parse error: {}", e.getMessage());
                callback.accept(List.of());
            }
        });
    }

    public List<SenderInfo> getSenders() {
        Map<Long, int[]> counts = new HashMap<>();
        Map<Long, String> names = new HashMap<>();
        for (List<MessageModel> msgs : messageCache.values()) {
            synchronized (msgs) {
                for (MessageModel m : msgs) {
                    if (m.senderId() == 0 || m.isOutgoing()) continue;
                    counts.computeIfAbsent(m.senderId(), k -> new int[1])[0]++;
                    names.putIfAbsent(m.senderId(), m.senderName());
                }
            }
        }
        return counts.entrySet().stream()
                .map(e -> new SenderInfo(e.getKey(), names.get(e.getKey()), e.getValue()[0], 0L))
                .sorted((a, b) -> Integer.compare(b.messageCount(), a.messageCount()))
                .collect(java.util.stream.Collectors.toList());
    }

    public List<MessageSearchResult> getMessagesForSender(long senderId, long associatedChatId) {
        List<MessageSearchResult> results = new ArrayList<>();
        if (associatedChatId != 0) {
            List<MessageModel> msgs = messageCache.get(associatedChatId);
            if (msgs != null) {
                synchronized (msgs) {
                    for (MessageModel m : msgs) {
                        results.add(new MessageSearchResult(m.chatId(), m.id(), m.text(), m.timestamp()));
                    }
                }
            }
        } else {
            for (List<MessageModel> msgs : messageCache.values()) {
                synchronized (msgs) {
                    for (MessageModel m : msgs) {
                        if (m.senderId() == senderId) {
                            results.add(new MessageSearchResult(m.chatId(), m.id(), m.text(), m.timestamp()));
                        }
                    }
                }
            }
        }
        results.sort((a, b) -> Long.compare(b.timestamp(), a.timestamp()));
        return results;
    }

    private List<MessageSearchResult> parseSearchResults(JsonObject resp) {
        JsonArray msgs = resp.getAsJsonArray("messages");
        if (msgs == null) return List.of();
        List<MessageSearchResult> results = new ArrayList<>();
        for (JsonElement el : msgs) {
            JsonObject msg = el.getAsJsonObject();
            long chatId = msg.get("chat_id").getAsLong();
            long msgId = msg.get("id").getAsLong();
            long date = msg.has("date") ? msg.get("date").getAsLong() : 0L;
            String text = parser.extractText(msg.getAsJsonObject("content"));
            results.add(new MessageSearchResult(chatId, msgId, text, date));
        }
        return results;
    }
}
