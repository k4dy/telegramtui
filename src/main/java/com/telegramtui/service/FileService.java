package com.telegramtui.service;

import com.telegramtui.telegram.TelegramClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    private final TelegramClient client;
    private final Map<Long, Consumer<String>> pendingDownloads = new ConcurrentHashMap<>();

    public FileService(TelegramClient client) {
        this.client = client;
    }

    public void downloadFile(long fileId, String knownLocalPath, Consumer<String> onComplete) {
        if (knownLocalPath != null && !knownLocalPath.isBlank()) {
            onComplete.accept(knownLocalPath);
            return;
        }
        if (fileId <= 0) {
            log.warn("downloadFile called with invalid fileId={}", fileId);
            return;
        }
        pendingDownloads.put(fileId, onComplete);
        client.send(
                "{\"@type\":\"downloadFile\",\"file_id\":" + fileId
                        + ",\"priority\":1,\"synchronous\":false}",
                result -> {
                    if ("error".equals(TelegramClient.extractStringField(result, "@type"))) {
                        log.warn("Download error for fileId={}: {}", fileId,
                                TelegramClient.extractStringField(result, "message"));
                        pendingDownloads.remove(fileId);
                        return;
                    }
                    String path = extractLocalPathFromJson(result);
                    if (!path.isEmpty() && result.contains("\"is_downloading_completed\":true")) {
                        Consumer<String> cb = pendingDownloads.remove(fileId);
                        if (cb != null) cb.accept(moveToDownloads(path));
                    }
                });
    }

    public void onUpdateFile(String json) {
        long fileId = extractFileIdFromUpdate(json);
        if (fileId <= 0) return;
        Consumer<String> cb = pendingDownloads.get(fileId);
        if (cb == null) return;
        if (json.contains("\"is_downloading_completed\":true")) {
            String path = extractLocalPathFromJson(json);
            if (!path.isEmpty()) {
                pendingDownloads.remove(fileId);
                cb.accept(moveToDownloads(path));
            }
        }
    }

    private String moveToDownloads(String tdlibPath) {
        try {
            Path src = Path.of(tdlibPath);
            Path dest = Path.of(System.getProperty("user.home"), "Downloads", src.getFileName().toString());
            Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING);
            return dest.toString();
        } catch (Exception e) {
            log.warn("Failed to move {} to Downloads: {}", tdlibPath, e.getMessage());
            return tdlibPath;
        }
    }

    private long extractFileIdFromUpdate(String json) {
        // TDLib sends {"@type":"updateFile","file":{"@type":"file","id":N,...}}
        // so we find "file":{ first, then look for "id": inside it
        int fileStart = json.indexOf("\"file\":{");
        if (fileStart < 0) return 0;
        int searchFrom = fileStart + "\"file\":{".length();
        int idIdx = json.indexOf("\"id\":", searchFrom);
        if (idIdx < 0) return 0;
        idIdx += "\"id\":".length();
        int end = json.indexOf(',', idIdx);
        if (end < 0) end = json.indexOf('}', idIdx);
        if (end < 0) return 0;
        try {
            return Long.parseLong(json.substring(idIdx, end).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String extractLocalPathFromJson(String json) {
        String marker = "\"path\":\"";
        int idx = json.indexOf(marker);
        if (idx < 0) return "";
        idx += marker.length();
        StringBuilder sb = new StringBuilder();
        while (idx < json.length()) {
            char c = json.charAt(idx);
            if (c == '"') break;
            if (c == '\\' && idx + 1 < json.length()) {
                idx++;
                sb.append(json.charAt(idx));
            } else {
                sb.append(c);
            }
            idx++;
        }
        return sb.toString();
    }
}
