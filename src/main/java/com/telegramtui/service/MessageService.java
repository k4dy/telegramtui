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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class.getSimpleName());
    private static final int HISTORY_LIMIT = 50;
    private static final int MAX_RETRIES = 4;
    private static final long RETRY_DELAY = 800;

    private final TelegramClient client;
    private final MessageParser parser = new MessageParser();
    private final AtomicLong version = new AtomicLong(0);
    private final MessageSearchService searchService;

    private final ConcurrentHashMap<Long, List<MessageModel>> messageCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, AtomicInteger> retryCount = new ConcurrentHashMap<>();

    public MessageService(TelegramClient client) {
        this.client = client;
        this.searchService = new MessageSearchService(client, parser, version, messageCache);
    }

    public void loadMessages(long chatId) {
        retryCount.put(chatId, new AtomicInteger(0));
        client.send("{\"@type\":\"openChat\",\"chat_id\":" + chatId + "}",
                resp -> fetchHistory(chatId));
    }

    public void loadMessagesAround(long chatId, long fromMessageId) {
        String req = "{\"@type\":\"getChatHistory\","
                + "\"chat_id\":" + chatId + ","
                + "\"from_message_id\":" + fromMessageId + ","
                + "\"offset\":-25,"
                + "\"limit\":50,"
                + "\"only_local\":false}";
        client.send(req, json -> handleHistory(chatId, json));
    }

    public List<MessageModel> getMessages(long chatId) {
        List<MessageModel> msgs = messageCache.get(chatId);
        return msgs != null ? new ArrayList<>(msgs) : new ArrayList<>();
    }

    // increments every time the cache changes — used by the UI to know when to redraw
    public long getChangeVersion() {
        return version.get();
    }

    public boolean isLoading(long chatId) {
        return retryCount.containsKey(chatId);
    }

    public boolean isChatLoaded(long chatId) {
        List<MessageModel> msgs = messageCache.get(chatId);
        return msgs != null && !msgs.isEmpty();
    }

    public void deleteMessages(long chatId, long messageId) {
        List<MessageModel> msgs = messageCache.get(chatId);
        if (msgs != null) {
            synchronized (msgs) {
                msgs.removeIf(m -> m.id() == messageId);
            }
            version.incrementAndGet();
        }
        JsonObject req = new JsonObject();
        req.addProperty("@type", "deleteMessages");
        req.addProperty("chat_id", chatId);
        JsonArray ids = new JsonArray();
        ids.add(messageId);
        req.add("message_ids", ids);
        req.addProperty("revoke", true);
        client.send(req.toString(), null);
    }

    public void editMessage(long chatId, long messageId, String text) {
        JsonObject textObj = new JsonObject();
        textObj.addProperty("@type", "formattedText");
        textObj.addProperty("text", text);

        JsonObject content = new JsonObject();
        content.addProperty("@type", "inputMessageText");
        content.add("text", textObj);

        JsonObject req = new JsonObject();
        req.addProperty("@type", "editMessageText");
        req.addProperty("chat_id", chatId);
        req.addProperty("message_id", messageId);
        req.add("input_message_content", content);

        client.send(req.toString(), null);
    }

    public void sendMessage(long chatId, String text) {
        sendMessage(chatId, text, 0);
    }

    public void sendMessage(long chatId, String text, long replyToMessageId) {
        JsonObject textObj = new JsonObject();
        textObj.addProperty("@type", "formattedText");
        textObj.addProperty("text", text);

        JsonObject content = new JsonObject();
        content.addProperty("@type", "inputMessageText");
        content.add("text", textObj);

        JsonObject req = new JsonObject();
        req.addProperty("@type", "sendMessage");
        req.addProperty("chat_id", chatId);
        if (replyToMessageId > 0) {
            JsonObject replyTo = new JsonObject();
            replyTo.addProperty("@type", "inputMessageReplyToMessage");
            replyTo.addProperty("message_id", replyToMessageId);
            req.add("reply_to", replyTo);
        }
        req.add("input_message_content", content);

        client.send(req.toString(), null);
    }

    public void onUpdate(String json) {
        String type = TelegramClient.getString(json, "@type");
        if (!"updateNewMessage".equals(type) && !"updateUser".equals(type)
                && !"updateMessageContent".equals(type) && !"updateDeleteMessages".equals(type)) return;
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        switch (type) {
            case "updateNewMessage" -> handleNewMessage(obj.getAsJsonObject("message"));
            case "updateUser" -> parser.handleUser(obj.getAsJsonObject("user"));
            case "updateMessageContent" -> handleMessageContent(obj);
            case "updateDeleteMessages" -> handleDeleteMessages(obj);
            default -> { }
        }
    }

    public List<SenderInfo> getSenders() {
        return searchService.getSenders();
    }

    public List<MessageSearchResult> getMessagesForSender(long senderId, long associatedChatId) {
        return searchService.getMessagesForSender(senderId, associatedChatId);
    }

    public void searchChatMessages(long chatId, String query,
                                   Consumer<List<MessageSearchResult>> callback) {
        searchService.searchChatMessages(chatId, query, callback);
    }

    public void searchMessages(String query, Consumer<List<MessageSearchResult>> callback) {
        searchService.searchMessages(query, callback);
    }

    private void fetchHistory(long chatId) {
        String req = "{\"@type\":\"getChatHistory\","
                + "\"chat_id\":" + chatId + ","
                + "\"from_message_id\":0,"
                + "\"offset\":0,"
                + "\"limit\":" + HISTORY_LIMIT + ","
                + "\"only_local\":false}";
        client.send(req, json -> handleHistory(chatId, json));
    }

    private void handleHistory(long chatId, String json) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        if (!obj.has("@type") || !"messages".equals(obj.get("@type").getAsString())) {
            log.warn("Unexpected getChatHistory response: {}", json);
            return;
        }
        JsonArray arr = obj.getAsJsonArray("messages");
        if (arr == null || arr.isEmpty()) {
            scheduleRetry(chatId);
            return;
        }
        List<MessageModel> list = new ArrayList<>();
        for (JsonElement el : arr) {
            MessageModel m = parser.parseMessage(el.getAsJsonObject());
            if (m != null) list.add(m);
        }
        // TDLib returns newest-first — reverse so we get chronological order
        Collections.reverse(list);
        messageCache.put(chatId, Collections.synchronizedList(new ArrayList<>(list)));
        version.incrementAndGet();

        if (list.size() < HISTORY_LIMIT) {
            scheduleRetry(chatId);
        } else {
            retryCount.remove(chatId);
        }
    }

    // waits a bit and tries fetching history again — used when TDLib returns an empty response
    private void scheduleRetry(long chatId) {
        AtomicInteger counter = retryCount.get(chatId);
        if (counter == null) return;
        int attempt = counter.incrementAndGet();
        if (attempt > MAX_RETRIES) {
            retryCount.remove(chatId);
            return;
        }
        //System.out.println("scheduling retry #" + attempt + " for chat " + chatId);
        new Thread(() -> {
            try {
                Thread.sleep(RETRY_DELAY * attempt);
            } catch (InterruptedException ignored) { }
            fetchHistory(chatId);
        }).start();
    }

    private void handleNewMessage(JsonObject msg) {
        if (msg == null) return;
        long chatId = msg.get("chat_id").getAsLong();
        MessageModel m = parser.parseMessage(msg);
        if (m == null) return;
        List<MessageModel> cached = messageCache.get(chatId);
        if (cached == null) {
            cached = Collections.synchronizedList(new ArrayList<>());
            messageCache.put(chatId, cached);
        }
        cached.add(m);
        version.incrementAndGet();
    }

    private void handleMessageContent(JsonObject obj) {
        long chatId = obj.get("chat_id").getAsLong();
        long messageId = obj.get("message_id").getAsLong();
        List<MessageModel> msgs = messageCache.get(chatId);
        if (msgs == null) return;
        String newText = parser.extractText(obj.getAsJsonObject("new_content"));
        synchronized (msgs) {
            for (int i = 0; i < msgs.size(); i++) {
                if (msgs.get(i).id() == messageId) {
                    MessageModel old = msgs.get(i);
                    msgs.set(i, new MessageModel(old.id(), old.chatId(), old.senderId(),
                            old.senderName(), newText, old.contentType(),
                            old.fileId(), old.localFilePath(), old.forwardedFrom(), old.urls(),
                            old.timestamp(), old.isOutgoing(), true, old.replyToMessageId()));
                    break;
                }
            }
        }
        version.incrementAndGet();
    }

    private void handleDeleteMessages(JsonObject obj) {
        long chatId = obj.get("chat_id").getAsLong();
        JsonArray ids = obj.getAsJsonArray("message_ids");
        if (ids == null) return;
        List<MessageModel> msgs = messageCache.get(chatId);
        if (msgs == null) return;
        synchronized (msgs) {
            for (JsonElement el : ids) {
                long mid = el.getAsLong();
                msgs.removeIf(m -> m.id() == mid);
            }
        }
        version.incrementAndGet();
    }
}
