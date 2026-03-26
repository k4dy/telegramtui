package com.telegramtui.telegram;

import com.telegramtui.service.ChatService;
import com.telegramtui.service.FileService;
import com.telegramtui.service.FolderService;
import com.telegramtui.service.MessageService;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

public class UpdateHandler {
	private final AtomicReference<String> connectionLabel = new AtomicReference<>("Connecting...");
	private final LinkedBlockingQueue<AuthState> authStatesQueue = new LinkedBlockingQueue<>();
	private ChatService chatService;
	private FolderService folderService;
	private MessageService messageService;
	private FileService fileService;


	private static AuthState toAuthState(String type) {
		if (type == null) {
			return null;
		}
		return switch (type) {
			case "authorizationStateWaitPhoneNumber" -> new AuthState.WaitPhone();
			case "authorizationStateWaitCode" -> new AuthState.WaitCode();
			case "authorizationStateReady" -> new AuthState.Ready();
			case "authorizationStateClosed" -> new AuthState.Closed();
			default -> null;
		};
	}

	public void setFolderService(FolderService folderService) {
		this.folderService = folderService;
	}

	public void setChatService(ChatService chatService) {
		this.chatService = chatService;
	}

	public void onUpdate(String json) {
		String type = TelegramClient.extractStringField(json, "@type");
		if ("updateAuthorizationState".equals(type)) {
			String authType = TelegramClient.extractNestedType(json, "authorization_state");
			if ("authorizationStateWaitPassword".equals(authType)) {
				String hint = TelegramClient.extractStringField(json, "password_hint");
				String emailPattern = TelegramClient.extractStringField(json, "recovery_email_address_pattern");
				authStatesQueue.offer(new AuthState.WaitPassword(
						hint != null ? hint : "",
						emailPattern != null ? emailPattern : ""));
			} else {
				AuthState state = toAuthState(authType);
				if (state != null) {
					authStatesQueue.offer(state);
				}
			}
		} else if ("updateFile".equals(type)) {
			if (fileService != null) {
				fileService.onUpdateFile(json);
			}
		} else if ("updateConnectionState".equals(type)) {
			String connType = TelegramClient.extractNestedType(json, "state");
			String label = switch (connType == null ? "" : connType) {
				case "connectionStateReady" -> "Connected";
				case "connectionStateConnecting" -> "Connecting...";
				case "connectionStateUpdating" -> "Updating...";
				default -> connType != null ? connType : "...";
			};
			connectionLabel.set(label);
		} else {
			if (chatService != null) {
				chatService.onUpdate(json);
			}
			if (folderService != null) {
				folderService.onUpdate(json);
			}
			if (messageService != null) {
				messageService.onUpdate(json);
			}
		}

	}

	public String getConnectionLabel() {
		return connectionLabel.get();
	}

	public AuthState pollAuthState() {
		return authStatesQueue.poll();
	}

	public void setMessageService(MessageService messageService) {
		this.messageService = messageService;
	}

	public void setFileService(FileService fileService) {
		this.fileService = fileService;
	}
}
