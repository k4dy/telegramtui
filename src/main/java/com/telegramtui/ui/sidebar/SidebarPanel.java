package com.telegramtui.ui.sidebar;

import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.telegramtui.model.ChatModel;
import com.telegramtui.model.FolderModel;
import com.telegramtui.service.ChatService;
import com.telegramtui.service.FolderService;
import com.telegramtui.ui.common.Box;
import com.telegramtui.ui.common.CatppuccinMocha;
import com.telegramtui.ui.common.TextRenderer;

import java.util.List;

public class SidebarPanel {


	private final ChatService chatService;
	private final FolderService folderService;

	private int selectedFolderId = ChatService.MAIN_LIST_ID;
	private int selectedIndex = 0;
	private int scrollOffset = 0;
	private ChatModel openedChat = null;

	public SidebarPanel(ChatService chatService, FolderService folderService) {
		this.chatService = chatService;
		this.folderService = folderService;
	}

	public void render(TextGraphics g, Box box) {
		int x = box.getInnerLeft();
		int y = box.getInnerTop();
		int w = box.getInnerWidth();
		int h = box.getInnerHeight();

		// Draw tabs on the border line (row above inner area)
		renderTabs(g, x, y - 1, w);

		List<ChatModel> chats = chatService.getChatsForList(selectedFolderId);
		int listTop = y;
		int listHeight = h;

		if (chats.isEmpty()) {
			String msg = chatService.isStarted() ? "No chats" : "Loading...";
			int msgRow = listTop + listHeight / 2;
			int msgCol = x + Math.max(0, (w - msg.length()) / 2);
			for (int i = 0; i < listHeight; i++) {
				g.setBackgroundColor(CatppuccinMocha.BASE);
				g.setForegroundColor(CatppuccinMocha.BASE);
				g.putString(x, listTop + i, " ".repeat(w));
			}
			g.setBackgroundColor(CatppuccinMocha.BASE);
			g.setForegroundColor(CatppuccinMocha.OVERLAY0);
			g.putString(msgCol, msgRow, msg);
			return;
		}

		if (selectedIndex >= chats.size()) {
			selectedIndex = chats.size() - 1;
		}
		if (selectedIndex < scrollOffset) scrollOffset = selectedIndex;
		if (selectedIndex >= scrollOffset + listHeight) scrollOffset = selectedIndex - listHeight + 1;

		for (int i = 0; i < listHeight; i++) {
			int chatIdx = scrollOffset + i;
			int row = listTop + i;
			if (chatIdx >= chats.size()) {
				g.setBackgroundColor(CatppuccinMocha.BASE);
				g.setForegroundColor(CatppuccinMocha.BASE);
				g.putString(x, row, " ".repeat(w));
				continue;
			}
			ChatModel chat = chats.get(chatIdx);
			boolean selected = chatIdx == selectedIndex;
			boolean hasUnread = chat.unreadCount() > 0;

			// Background: selected > unread > normal
			var bg = selected ? CatppuccinMocha.SURFACE1
					: hasUnread ? CatppuccinMocha.SURFACE0
					: CatppuccinMocha.BASE;
			g.setBackgroundColor(bg);

			String badge = hasUnread ? " [" + chat.unreadCount() + "]" : "";
			var badgeColor = chat.isMuted() ? CatppuccinMocha.OVERLAY0 : CatppuccinMocha.RED;
			int titleWidth = w - badge.length();

			g.setForegroundColor(selected ? CatppuccinMocha.TEXT : CatppuccinMocha.SUBTEXT1);
			g.putString(x, row, TextRenderer.padRight(chat.title(), titleWidth));

			if (hasUnread) {
				g.setForegroundColor(selected ? CatppuccinMocha.TEXT : badgeColor);
				g.putString(x + titleWidth, row, badge);
			}
		}
	}

	public ChatModel getOpenedChat() {
		return openedChat;
	}

	public boolean handleKey(KeyStroke key) {
		if (key.getKeyType() == KeyType.Enter) {
			List<ChatModel> chats = chatService.getChatsForList(selectedFolderId);
			if (!chats.isEmpty() && selectedIndex < chats.size()) {
				openedChat = chats.get(selectedIndex);
			}
			return true;
		}
		if (key.getKeyType() != KeyType.Character) return false;
		char c = key.getCharacter();

		// Number keys switch folders: 0 = All, 1-9 = user folders
		if (c >= '0' && c <= '9') {
			int num = c - '0';
			if (num == 0) {
				selectedFolderId = ChatService.MAIN_LIST_ID;
				selectedIndex = 0;
				scrollOffset = 0;
				return true;
			}
			List<FolderModel> folders = folderService.getFolders();
			int idx = num - 1;
			if (idx < folders.size()) {
				selectedFolderId = folders.get(idx).id();
				selectedIndex = 0;
				scrollOffset = 0;
				return true;
			}
			return false;
		}

		if (c == 'j') {
			List<ChatModel> chats = chatService.getChatsForList(selectedFolderId);
			if (selectedIndex < chats.size() - 1) selectedIndex++;
			return true;
		}
		if (c == 'k') {
			if (selectedIndex > 0) selectedIndex--;
			return true;
		}
		return false;
	}

	private void renderTabs(TextGraphics g, int x, int y, int maxWidth) {
		List<FolderModel> folders = folderService.getFolders();
		int col = x;

		col = drawTab(g, col, y, x + maxWidth, "[0]All", selectedFolderId == ChatService.MAIN_LIST_ID);

		for (int i = 0; i < folders.size() && i < 9; i++) {
			FolderModel folder = folders.get(i);
			String label = "[" + (i + 1) + "]" + folder.title();
			if (col + label.length() > x + maxWidth) break;
			col = drawTab(g, col, y, x + maxWidth, label, selectedFolderId == folder.id());
		}

		g.setBackgroundColor(CatppuccinMocha.BASE);
		g.setForegroundColor(CatppuccinMocha.OVERLAY2);
		for (int c = col; c < x + maxWidth; c++) {
			g.putString(c, y, "─");
		}
	}

	private int drawTab(TextGraphics g, int col, int row, int maxCol, String label, boolean active) {
		if (col >= maxCol) return col;
		g.setBackgroundColor(CatppuccinMocha.BASE);
		g.setForegroundColor(active ? CatppuccinMocha.MAUVE : CatppuccinMocha.OVERLAY0);
		g.putString(col, row, label);
		return col + label.length() + 1;
	}
}

