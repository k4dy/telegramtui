package com.telegramtui.app;

import com.telegramtui.service.ChatService;
import com.telegramtui.service.FileService;
import com.telegramtui.service.FolderService;
import com.telegramtui.service.MessageService;
import com.telegramtui.telegram.NativeLibLoader;
import com.telegramtui.telegram.TelegramClient;
import com.telegramtui.ui.layout.AuthScreen;
import com.telegramtui.ui.layout.MainScreen;

public class App {

	public static void main(String[] args) throws Exception {
		NativeLibLoader.load();

		AppConfig config = new AppConfig();
		TelegramClient telegramClient = new TelegramClient(config);
		ChatService chatService = new ChatService(telegramClient);
		FolderService folderService = new FolderService(telegramClient);
		MessageService messageService = new MessageService(telegramClient);
		FileService fileService = new FileService(telegramClient);
		telegramClient.getUpdateHandler().setMessageService(messageService);
		telegramClient.getUpdateHandler().setChatService(chatService);
		telegramClient.getUpdateHandler().setFolderService(folderService);
		telegramClient.getUpdateHandler().setFileService(fileService);

		Thread shutdownHook = new Thread(telegramClient::shutdown);
		Runtime.getRuntime().addShutdownHook(shutdownHook);

		new AuthScreen(telegramClient).start();
		chatService.start();
		new MainScreen(telegramClient, chatService, folderService, messageService, fileService).start();

		try {
			Runtime.getRuntime().removeShutdownHook(shutdownHook);
		} catch (IllegalStateException ignored) {
		}

		telegramClient.shutdown();
		long deadline = System.currentTimeMillis() + 5000;
		while (!telegramClient.isClosed() && System.currentTimeMillis() < deadline) {
			Thread.sleep(50);
		}
	}
}
