package com.telegramtui.ui.layout;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.telegramtui.service.ChatService;
import com.telegramtui.service.FileService;
import com.telegramtui.service.FolderService;
import com.telegramtui.service.MessageService;
import com.telegramtui.telegram.TelegramClient;
import com.telegramtui.ui.chat.ConversationPanel;
import com.telegramtui.ui.popup.CommandPopup;
import com.telegramtui.ui.sidebar.SidebarPanel;

import java.io.IOException;

public class MainScreen {

	private static final long IDLE_REDRAW_INTERVAL_MS = 200;

	private final TelegramClient client;
	private final MessageService messageService;
	private final SidebarPanel sidebarPanel;
	private final ConversationPanel conversationPanel;
	private final FocusManager focusManager = new FocusManager();
	private final CommandPopup commandPopup;
	private final MainInputRouter inputRouter;

	public MainScreen(TelegramClient client, ChatService chatService,
	                  FolderService folderService, MessageService messageService,
	                  FileService fileService) {
		this.client = client;
		this.messageService = messageService;
		this.sidebarPanel = new SidebarPanel(chatService, folderService);
		this.conversationPanel = new ConversationPanel(messageService, chatService, fileService);
		this.commandPopup = new CommandPopup(chatService, messageService);
		this.inputRouter = new MainInputRouter(commandPopup, conversationPanel, focusManager,
				sidebarPanel, chatService);
	}

	public void start() throws IOException, InterruptedException {
		var terminal = new DefaultTerminalFactory().createTerminal();
		var screen = new TerminalScreen(terminal);
		screen.startScreen();

		screen.clear();
		drawLayout(screen);
		screen.refresh();

		boolean dirty = false;
		long lastRedrawMs = System.currentTimeMillis();
		long lastSeenVersion = messageService.getChangeVersion();

		while (true) {
			// Drain all pending keystrokes before redrawing
			boolean gotInput = false;
			MainInputRouter.Action pendingAction = MainInputRouter.Action.CONTINUE;
			KeyStroke key;
			while ((key = screen.pollInput()) != null) {
				pendingAction = inputRouter.route(key);
				gotInput = true;
				dirty = true;
				if (pendingAction != MainInputRouter.Action.CONTINUE) break;
			}
			if (pendingAction == MainInputRouter.Action.LOGOUT) {
				client.send("{\"@type\":\"logOut\"}", null);
				break;
			}
			if (pendingAction == MainInputRouter.Action.QUIT) break;
			if (pendingAction == MainInputRouter.Action.TOGGLE_FOCUS) {
				LayoutManager.setFocusMode(!LayoutManager.isFocusMode());
				screen.clear();
				dirty = true;
			}

			long currentVersion = messageService.getChangeVersion();
			if (currentVersion != lastSeenVersion) {
				lastSeenVersion = currentVersion;
				dirty = true;
			}

			long now = System.currentTimeMillis();
			boolean timeElapsed = (now - lastRedrawMs) >= IDLE_REDRAW_INTERVAL_MS;

			TerminalSize newSize = screen.doResizeIfNecessary();
			if (newSize != null) {
				screen.clear();
				drawLayout(screen);
				screen.refresh(Screen.RefreshType.COMPLETE);
				lastRedrawMs = now;
				dirty = false;
			} else if (dirty || timeElapsed) {
				drawLayout(screen);
				screen.refresh();
				lastRedrawMs = now;
				dirty = false;
			}

			if (!gotInput) Thread.sleep(10);
		}
		screen.stopScreen();
	}

	private void drawLayout(Screen screen) {
		// hide the terminal cursor — without this it sits at (0,0) and looks like a highlighted square
		screen.setCursorPosition(null);

		String modeLabel = commandPopup.isActive() ? commandPopup.getModeLabel()
				                   : conversationPanel.getModeHint();

		// clear label before drawing so the box border has no stale text next to the corner
		LayoutManager.getChatBox().setLabel("");
		LayoutManager.draw(screen, client.getUpdateHandler().getConnectionLabel(), focusManager,
				modeLabel);
		TextGraphics g = screen.newTextGraphics();
		if (!LayoutManager.isFocusMode()) {
			sidebarPanel.render(g, LayoutManager.getSidebarBox());
		}
		conversationPanel.render(g, LayoutManager.getChatBox());
		TerminalSize size = screen.getTerminalSize();
		commandPopup.render(g, size.getColumns(), size.getRows());
	}
}
