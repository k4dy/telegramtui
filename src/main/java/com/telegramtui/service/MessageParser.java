package com.telegramtui.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.telegramtui.model.MessageModel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class MessageParser {

    private final ConcurrentHashMap<Long, String> userNames = new ConcurrentHashMap<>();

    public void handleUser(JsonObject user) {
        if (user == null || !user.has("id")) return;
        long id = user.get("id").getAsLong();
        String first = user.has("first_name") ? user.get("first_name").getAsString() : "";
        String last = user.has("last_name") ? user.get("last_name").getAsString() : "";
        String name = (first + " " + last).trim();
        if (!name.isEmpty()) {
            userNames.put(id, name);
        }
    }

    public MessageModel parseMessage(JsonObject msg) {
        if (msg == null || msg.isJsonNull()) return null;
        if (!msg.has("id") || !msg.has("chat_id")) return null;
        long id = msg.get("id").getAsLong();
        long chatId = msg.get("chat_id").getAsLong();

        long senderId = 0;
        JsonObject sender = msg.getAsJsonObject("sender_id");
        if (sender != null && "messageSenderUser".equals(sender.get("@type").getAsString())) {
            senderId = sender.get("user_id").getAsLong();
        }
        String senderName = userNames.getOrDefault(senderId, String.valueOf(senderId));

        boolean isOutgoing = msg.has("is_outgoing") && msg.get("is_outgoing").getAsBoolean();
        long date = msg.has("date") ? msg.get("date").getAsLong() : 0;
        boolean isEdited = msg.has("edit_date") && msg.get("edit_date").getAsLong() > 0;

        long replyToId = 0;
        if (msg.has("reply_to") && !msg.get("reply_to").isJsonNull()) {
            JsonObject replyTo = msg.getAsJsonObject("reply_to");
            if (replyTo.has("message_id")) replyToId = replyTo.get("message_id").getAsLong();
        }

        JsonObject content = msg.getAsJsonObject("content");
        String contentType = (content != null && content.has("@type"))
                ? content.get("@type").getAsString() : "unknown";

        long fileId = parseFileId(content, contentType);
        String localFilePath = parseLocalPath(content, contentType);
        String forwardedFrom = extractForwardedFrom(msg);
        List<String> urls = extractUrls(content, contentType);

        return new MessageModel(id, chatId, senderId, senderName,
                extractText(content),
                contentType,
                fileId,
                localFilePath,
                forwardedFrom,
                urls,
                date, isOutgoing, isEdited, replyToId);
    }

    public String extractText(JsonObject content) {
        if (content == null || content.isJsonNull() || !content.has("@type")) return "";
        String type = content.get("@type").getAsString();
        return switch (type) {
            case "messageText" -> {
                JsonObject textObj = content.getAsJsonObject("text");
                yield (textObj != null && textObj.has("text"))
                        ? textObj.get("text").getAsString() : "";
            }
            case "messageDocument" -> {
                JsonObject doc = content.getAsJsonObject("document");
                if (doc == null) yield "[Document]";
                String fileName = doc.has("file_name") ? doc.get("file_name").getAsString() : "unknown";
                String mimeType = doc.has("mime_type") ? doc.get("mime_type").getAsString() : "";
                long size = 0;
                JsonObject file = doc.getAsJsonObject("document");
                if (file != null && file.has("size")) size = file.get("size").getAsLong();
                String caption = extractCaption(content);
                String result = "📎 " + fileName + "\n" + formatSize(size)
                        + (mimeType.isEmpty() ? "" : " · " + mimeType);
                yield caption.isEmpty() ? result : result + "\n" + caption;
            }
            case "messagePhoto" -> {
                String dims = "";
                JsonObject photo = content.getAsJsonObject("photo");
                if (photo != null && photo.has("sizes")) {
                    JsonArray sizes = photo.getAsJsonArray("sizes");
                    if (sizes != null && !sizes.isEmpty()) {
                        JsonObject largest = sizes.get(sizes.size() - 1).getAsJsonObject();
                        int w = largest.has("width") ? largest.get("width").getAsInt() : 0;
                        int h = largest.has("height") ? largest.get("height").getAsInt() : 0;
                        if (w > 0 && h > 0) dims = " " + w + "×" + h;
                    }
                }
                String caption = extractCaption(content);
                String result = "[Photo" + dims + "]";
                yield caption.isEmpty() ? result : result + "\n" + caption;
            }
            case "messageVoiceNote" -> {
                JsonObject voice = content.getAsJsonObject("voice_note");
                int dur = (voice != null && voice.has("duration"))
                        ? voice.get("duration").getAsInt() : 0;
                yield "🎤 Voice Message · " + formatDuration(dur);
            }
            case "messageVideoNote" -> {
                JsonObject video = content.getAsJsonObject("video_note");
                int dur = (video != null && video.has("duration"))
                        ? video.get("duration").getAsInt() : 0;
                yield "🎥 Video Message · " + formatDuration(dur);
            }
            case "messageVideo" -> {
                JsonObject video = content.getAsJsonObject("video");
                String fileName = (video != null && video.has("file_name"))
                        ? video.get("file_name").getAsString() : "video";
                int dur = (video != null && video.has("duration"))
                        ? video.get("duration").getAsInt() : 0;
                String caption = extractCaption(content);
                String result = "🎬 " + fileName + " · " + formatDuration(dur);
                yield caption.isEmpty() ? result : result + "\n" + caption;
            }
            case "messageAudio" -> {
                JsonObject audio = content.getAsJsonObject("audio");
                String title = (audio != null && audio.has("title"))
                        ? audio.get("title").getAsString() : "Unknown";
                String performer = (audio != null && audio.has("performer"))
                        ? audio.get("performer").getAsString() : "";
                int dur = (audio != null && audio.has("duration"))
                        ? audio.get("duration").getAsInt() : 0;
                String caption = extractCaption(content);
                String label = performer.isEmpty() ? title : performer + " — " + title;
                String result = "🎵 " + label + " · " + formatDuration(dur);
                yield caption.isEmpty() ? result : result + "\n" + caption;
            }
            case "messageSticker" -> {
                JsonObject sticker = content.getAsJsonObject("sticker");
                String emoji = (sticker != null && sticker.has("emoji"))
                        ? sticker.get("emoji").getAsString() : "?";
                yield "[Sticker: " + emoji + "]";
            }
            case "messageContact" -> {
                JsonObject contact = content.getAsJsonObject("contact");
                if (contact == null) yield "[Contact]";
                String first = contact.has("first_name")
                        ? contact.get("first_name").getAsString() : "";
                String last = contact.has("last_name")
                        ? contact.get("last_name").getAsString() : "";
                String name = (first + " " + last).trim();
                String phone = contact.has("phone_number")
                        ? contact.get("phone_number").getAsString() : "";
                yield "👤 " + name + (phone.isEmpty() ? "" : " · " + phone);
            }
            case "messageLocation" -> {
                JsonObject loc = content.getAsJsonObject("location");
                if (loc == null) yield "[Location]";
                double lat = loc.has("latitude") ? loc.get("latitude").getAsDouble() : 0;
                double lon = loc.has("longitude") ? loc.get("longitude").getAsDouble() : 0;
                yield String.format("📍 %.4f, %.4f", lat, lon);
            }
            case "messageVenue" -> {
                JsonObject venue = content.getAsJsonObject("venue");
                if (venue == null) yield "[Venue]";
                String vTitle = venue.has("title") ? venue.get("title").getAsString() : "";
                String address = venue.has("address") ? venue.get("address").getAsString() : "";
                yield "📍 " + vTitle + (address.isEmpty() ? "" : " · " + address);
            }
            case "messagePoll" -> {
                JsonObject poll = content.getAsJsonObject("poll");
                if (poll == null) yield "[Poll]";
                String question = extractPollText(poll, "question");
                int total = poll.has("total_voter_count")
                        ? poll.get("total_voter_count").getAsInt() : 0;
                StringBuilder sb = new StringBuilder(question);
                if (poll.has("options")) {
                    for (JsonElement el : poll.getAsJsonArray("options")) {
                        JsonObject opt = el.getAsJsonObject();
                        String optText = extractPollText(opt, "text");
                        int votes = opt.has("voter_count")
                                ? opt.get("voter_count").getAsInt() : 0;
                        sb.append("\n── ").append(optText).append("  ").append(votes).append("/").append(total);
                    }
                }
                yield sb.toString();
            }
            default -> "[" + type.replace("message", "") + "]";
        };
    }

    private String extractCaption(JsonObject content) {
        JsonObject caption = content.getAsJsonObject("caption");
        if (caption != null && caption.has("text")) {
            String text = caption.get("text").getAsString();
            return text.isEmpty() ? "" : text;
        }
        return "";
    }

    // Handles both plain string and nested text object formats for poll questions/options
    private String extractPollText(JsonObject obj, String field) {
        if (!obj.has(field)) return "?";
        JsonElement el = obj.get(field);
        if (el.isJsonPrimitive()) return el.getAsString();
        if (el.isJsonObject()) {
            JsonObject textObj = el.getAsJsonObject();
            return textObj.has("text") ? textObj.get("text").getAsString() : "?";
        }
        return "?";
    }

    private long parseFileId(JsonObject content, String type) {
        if (content == null) return 0;
        try {
            JsonObject fileObj = getFileObject(content, type);
            return fileObj != null && fileObj.has("id") ? fileObj.get("id").getAsLong() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private String parseLocalPath(JsonObject content, String type) {
        if (content == null) return "";
        try {
            JsonObject fileObj = getFileObject(content, type);
            if (fileObj == null) return "";
            JsonObject local = fileObj.getAsJsonObject("local");
            if (local == null) return "";
            boolean done = local.has("is_downloading_completed")
                    && local.get("is_downloading_completed").getAsBoolean();
            if (!done) return "";
            return local.has("path") ? local.get("path").getAsString() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private JsonObject getFileObject(JsonObject content, String type) {
        return switch (type) {
            case "messageDocument" -> {
                JsonObject doc = content.getAsJsonObject("document");
                yield doc != null ? doc.getAsJsonObject("document") : null;
            }
            case "messagePhoto" -> {
                JsonObject photo = content.getAsJsonObject("photo");
                if (photo == null) yield null;
                JsonArray sizes = photo.getAsJsonArray("sizes");
                if (sizes == null || sizes.isEmpty()) yield null;
                yield sizes.get(sizes.size() - 1).getAsJsonObject().getAsJsonObject("photo");
            }
            case "messageVoiceNote" -> {
                JsonObject voice = content.getAsJsonObject("voice_note");
                yield voice != null ? voice.getAsJsonObject("voice") : null;
            }
            case "messageVideoNote" -> {
                JsonObject video = content.getAsJsonObject("video_note");
                yield video != null ? video.getAsJsonObject("video") : null;
            }
            case "messageVideo" -> {
                JsonObject video = content.getAsJsonObject("video");
                yield video != null ? video.getAsJsonObject("video") : null;
            }
            case "messageAudio" -> {
                JsonObject audio = content.getAsJsonObject("audio");
                yield audio != null ? audio.getAsJsonObject("audio") : null;
            }
            default -> null;
        };
    }

    private String extractForwardedFrom(JsonObject msg) {
        if (!msg.has("forward_info") || msg.get("forward_info").isJsonNull()) return "";
        JsonObject fwd = msg.getAsJsonObject("forward_info");
        if (fwd == null || !fwd.has("origin")) return "";
        JsonObject origin = fwd.getAsJsonObject("origin");
        if (origin == null) return "";
        String originType = origin.has("@type") ? origin.get("@type").getAsString() : "";
        return switch (originType) {
            case "messageForwardOriginUser" -> {
                long userId = origin.has("sender_user_id")
                        ? origin.get("sender_user_id").getAsLong() : 0;
                yield userNames.getOrDefault(userId, "Unknown");
            }
            case "messageForwardOriginChannel" ->
                    origin.has("title") ? origin.get("title").getAsString() : "Channel";
            case "messageForwardOriginHiddenUser" ->
                    origin.has("sender_name") ? origin.get("sender_name").getAsString() : "Unknown";
            case "messageForwardOriginMessageImport" ->
                    origin.has("sender_name") ? origin.get("sender_name").getAsString() : "Import";
            default -> "Unknown";
        };
    }

    private List<String> extractUrls(JsonObject content, String type) {
        if (content == null || !"messageText".equals(type)) return List.of();
        JsonObject textObj = content.getAsJsonObject("text");
        if (textObj == null || !textObj.has("entities")) return List.of();
        String fullText = textObj.has("text") ? textObj.get("text").getAsString() : "";
        List<String> urls = new ArrayList<>();
        for (JsonElement el : textObj.getAsJsonArray("entities")) {
            JsonObject entity = el.getAsJsonObject();
            JsonObject entityType = entity.has("type") ? entity.getAsJsonObject("type") : null;
            if (entityType == null) continue;
            String typeStr = entityType.has("@type") ? entityType.get("@type").getAsString() : "";
            if ("textEntityTypeUrl".equals(typeStr)) {
                int offset = entity.has("offset") ? entity.get("offset").getAsInt() : 0;
                int length = entity.has("length") ? entity.get("length").getAsInt() : 0;
                if (offset >= 0 && offset + length <= fullText.length()) {
                    urls.add(fullText.substring(offset, offset + length));
                }
            } else if ("textEntityTypeTextUrl".equals(typeStr) && entityType.has("url")) {
                urls.add(entityType.get("url").getAsString());
            }
        }
        return List.copyOf(urls);
    }

    private static String formatSize(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    private static String formatDuration(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
