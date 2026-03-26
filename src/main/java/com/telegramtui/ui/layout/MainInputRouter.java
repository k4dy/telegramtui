package com.telegramtui.ui.layout;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.telegramtui.model.ChatModel;
import com.telegramtui.service.ChatService;
import com.telegramtui.ui.chat.ConversationPanel;
import com.telegramtui.ui.popup.CommandPopup;
import com.telegramtui.ui.popup.PopupAction;
import com.telegramtui.ui.sidebar.SidebarPanel;

public class MainInputRouter {

	private final CommandPopup commandPopup;
	private final ConversationPanel conversationPanel;
	private final FocusManager focusManager;
	private final SidebarPanel sidebarPanel;
	private final ChatService chatService;

	public MainInputRouter(CommandPopup commandPopup, ConversationPanel conversationPanel,
	                   FocusManager focusManager, SidebarPanel sidebarPanel,
	                   ChatService chatService) {
		this.commandPopup = commandPopup;
		this.conversationPanel = conversationPanel;
		this.focusManager = focusManager;
		this.sidebarPanel = sidebarPanel;
		this.chatService = chatService;
	}

	private static boolean isChar(KeyStroke key, char c) {
		return key.getKeyType() == KeyType.Character && key.getCharacter() == c;
	}

	public Action route(KeyStroke key) {
		if (commandPopup.isActive()) {
			commandPopup.handleKey(key);
		} else if (conversationPanel.isInInsertMode()) {
			conversationPanel.handleKey(key);
		} else if (isChar(key, ':')) {
			commandPopup.openCommand();
		} else if (isChar(key, '/')) {
			commandPopup.openChatSearch();
		} else if (isChar(key, '?')) {
			commandPopup.openMsgSearch();
		} else if (isChar(key, '@')) {
			commandPopup.openSenderSearch();
		} else if (key.getKeyType() == KeyType.Enter
				&& focusManager.getFocused() == FocusManager.Panel.SIDEBAR) {
			sidebarPanel.handleKey(key);
			conversationPanel.openChat(sidebarPanel.getOpenedChat());
			focusManager.setFocused(FocusManager.Panel.CHAT);
		} else if (!focusManager.handleKey(key)) {
			if (isChar(key, 'f')) {
				return Action.TOGGLE_FOCUS;
			} else if (isChar(key, 'x')) {
				conversationPanel.closeCurrentTab();
			} else if (isChar(key, 'X')) {
				conversationPanel.closeOtherTabs();
			} else if (focusManager.getFocused() == FocusManager.Panel.SIDEBAR) {
				if (!sidebarPanel.handleKey(key) && isChar(key, 'q')) {
					return Action.QUIT;
				}
			} else {
				if (!conversationPanel.handleKey(key) && isChar(key, 'q')) {
					return Action.QUIT;
				}
			}
		}

		PopupAction action = commandPopup.consumeAction();
		if (action instanceof PopupAction.PlainCommand pc) {
			return switch (pc.cmd()) {
				case "q", "quit" -> Action.QUIT;
				case "logout" -> Action.LOGOUT;
				case "f" -> Action.TOGGLE_FOCUS;
				case "x" -> { conversationPanel.closeCurrentTab(); yield Action.CONTINUE; }
				case "xx" -> { conversationPanel.closeOtherTabs(); yield Action.CONTINUE; }
				default -> Action.CONTINUE;
			};
		} else if (action instanceof PopupAction.OpenChat oc) {
			ChatModel chat = chatService.getChat(oc.chatId());
			if (chat != null) {
				conversationPanel.openChat(chat);
				focusManager.setFocused(FocusManager.Panel.CHAT);
			}
		} else if (action instanceof PopupAction.JumpToMessage jm) {
			ChatModel chat = chatService.getChat(jm.chatId());
			if (chat != null) {
				conversationPanel.openChat(chat);
				conversationPanel.jumpToMessage(jm.messageId());
				focusManager.setFocused(FocusManager.Panel.CHAT);
			}
		}
		return Action.CONTINUE;
	}

	public enum Action {CONTINUE, QUIT, LOGOUT, TOGGLE_FOCUS}
}
