package com.telegramtui.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

// Loads the API credentials from ~/.telegramtui/config.properties
public class AppConfig {

	private static final Logger log = LoggerFactory.getLogger(AppConfig.class);
	private static final Path CONFIG_PATH =
			Path.of(System.getProperty("user.home"), ".telegramtui", "config.properties");

	private final int apiId;
	private final String apiHash;

	public AppConfig() {
		Properties props = new Properties();

		// embedded credentials baked in at release time (absent in dev builds)
		try (InputStream in = AppConfig.class.getResourceAsStream("/credentials.properties")) {
			if (in != null) {
				props.load(in);
			}
		} catch (IOException e) {
			log.warn("Could not read embedded credentials.properties", e);
		}

		// user config overrides embedded values, and is the only source in dev builds
		if (Files.exists(CONFIG_PATH)) {
			try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
				props.load(in);
			} catch (IOException e) {
				throw new IllegalStateException("Failed to read config.properties", e);
			}
		}

		this.apiId = Integer.parseInt(props.getProperty("api.id", "0"));
		this.apiHash = props.getProperty("api.hash", "");
		if (apiId == 0 || apiHash.isEmpty()) {
			printSetupInstructions();
			throw new IllegalStateException("api.id or api.hash not configured");
		}
	}

	private static void printSetupInstructions() {
		System.err.println("""
				TelegramTUI requires Telegram API credentials.
				1. Go to https://my.telegram.org and log in
				2. Click "API development tools" and create an app
				3. Create the file: ~/.telegramtui/config.properties
				   with the following content:
				     api.id=YOUR_API_ID
				     api.hash=YOUR_API_HASH
				""");
	}

	public int getApiId() {
		return apiId;
	}

	public String getApiHash() {
		return apiHash;
	}
}
