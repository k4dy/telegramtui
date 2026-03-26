package com.telegramtui.telegram;

import com.telegramtui.app.AppConfig;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class TelegramClient {

	private final AppConfig config;
	private final UpdateHandler updateHandler;
	private final long clientId;
	private final Map<String, Consumer<String>> pendingCallbacks = new ConcurrentHashMap<>();
	private volatile boolean closed = false;

	public TelegramClient(AppConfig config) {
		this.config = config;
		TdJson.INSTANCE.td_execute("{\"@type\":\"setLogVerbosityLevel\",\"new_verbosity_level\":0}");
		this.clientId = TdJson.INSTANCE.td_json_client_create();
		this.updateHandler = new UpdateHandler();
		Thread.ofVirtual().name("tdlib-receive").start(this::receiveLoop);
	}

	public static String extractStringField(String json, String field) {
		String key = "\"" + field + "\":\"";
		int start = json.indexOf(key);
		if (start < 0) {
			return null;
		}
		start += key.length();
		int end = json.indexOf('"', start);
		return end < 0 ? null : json.substring(start, end);
	}

	public static String extractNestedType(String json, String objectField) {
		String marker = "\"" + objectField + "\":{\"@type\":\"";
		int start = json.indexOf(marker);
		if (start < 0) {
			return null;
		}
		start += marker.length();
		int end = json.indexOf('"', start);
		return end < 0 ? null : json.substring(start, end);
	}

	private static String injectExtra(String json, String extra) {
		int lastBrace = json.lastIndexOf('}');
		if (lastBrace < 0) {
			return json;
		}
		return json.substring(0, lastBrace) + ",\"@extra\":\"" + extra + "\"}";
	}

	private void receiveLoop() {
		while (!closed) {
			String json = TdJson.INSTANCE.td_json_client_receive(clientId, 1.0);
			if (json == null) {
				continue;
			}

			String extra = extractStringField(json, "@extra");
			if (extra != null) {
				Consumer<String> cb = pendingCallbacks.remove(extra);
				if (cb != null) {
					cb.accept(json);
					continue;
				}
			}

			if ("updateAuthorizationState".equals(extractStringField(json, "@type"))) {
				String authType = extractNestedType(json, "authorization_state");
				if ("authorizationStateWaitTdlibParameters".equals(authType)) {
					sendParametersFlat();
					continue;
				}
				if ("authorizationStateWaitEncryptionKey".equals(authType)) {
					TdJson.INSTANCE.td_json_client_send(clientId,
							"{\"@type\":\"checkDatabaseEncryptionKey\",\"encryption_key\":\"\"}");
					continue;
				}
				if ("authorizationStateClosed".equals(authType)) {
					closed = true;
				}
			}

			updateHandler.onUpdate(json);
		}
		TdJson.INSTANCE.td_json_client_destroy(clientId);
	}

	private void sendParametersFlat() {
		String extra = UUID.randomUUID().toString();
		String json = "{\"@type\":\"setTdlibParameters\"," + buildParams() + ",\"@extra\":\"" + extra + "\"}";
		pendingCallbacks.put(extra, response -> {
			if ("error".equals(extractStringField(response, "@type"))) {
				sendParametersNested();
			}
		});
		TdJson.INSTANCE.td_json_client_send(clientId, json);
	}

	private void sendParametersNested() {
		String json = "{\"@type\":\"setTdlibParameters\","
				+ "\"parameters\":{\"@type\":\"tdlibParameters\"," + buildParams() + "}}";
		TdJson.INSTANCE.td_json_client_send(clientId, json);
	}

	private String buildParams() {
		String dbDir = Path.of(System.getProperty("user.home"), ".telegramtui", "tdlib").toString();
		String filesDir = Path.of(System.getProperty("user.home"), "Downloads").toString();
		return "\"use_test_dc\":false,"
				+ "\"database_directory\":\"" + dbDir.replace("\\", "\\\\") + "\","
				+ "\"files_directory\":\"" + filesDir.replace("\\", "\\\\") + "\","
				+ "\"use_file_database\":true,"
				+ "\"use_chat_info_database\":true,"
				+ "\"use_message_database\":true,"
				+ "\"use_secret_chats\":false,"
				+ "\"api_id\":" + config.getApiId() + ","
				+ "\"api_hash\":\"" + config.getApiHash() + "\","
				+ "\"system_language_code\":\"en\","
				+ "\"device_model\":\"TelegramTUI\","
				+ "\"system_version\":\"\","
				+ "\"application_version\":\"1.0.0\","
				+ "\"enable_storage_optimizer\":true";
	}

	public void send(String jsonRequest, Consumer<String> callback) {
		String extra = UUID.randomUUID().toString();
		String withExtra = injectExtra(jsonRequest, extra);
		pendingCallbacks.put(extra, response -> {
			if (callback != null) callback.accept(response);
		});
		TdJson.INSTANCE.td_json_client_send(clientId, withExtra);
	}

	public void shutdown() {
		if (!closed) {
			TdJson.INSTANCE.td_json_client_send(clientId, "{\"@type\":\"close\"}");
		}
	}

	public boolean isClosed() {
		return closed;
	}

	public UpdateHandler getUpdateHandler() {
		return updateHandler;
	}
}
