package com.telegramtui.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class NativeLibLoader {

	private static final Logger log = LoggerFactory.getLogger(NativeLibLoader.class);

	private static final String[] SEARCH_PATHS = {
			System.getProperty("user.home") + "/.telegramtui/lib/libtdjson.dylib",
			"/opt/homebrew/lib/libtdjson.dylib",
			"/usr/local/lib/libtdjson.dylib",
			"/usr/lib/libtdjson.so",
			"/usr/lib/x86_64-linux-gnu/libtdjson.so",
			"/usr/lib/aarch64-linux-gnu/libtdjson.so",
			"/usr/local/lib/libtdjson.so",
	};

	// Debian packages TDLib into versioned subdirs, e.g. /usr/lib/x86_64-linux-gnu/TDLib1.8.38/libtdjson.so
	private static final String[] SEARCH_DIRS = {
			"/usr/lib/x86_64-linux-gnu",
			"/usr/lib/aarch64-linux-gnu",
			"/usr/lib",
	};

	public static void load() {
		for (String path : SEARCH_PATHS) {
			if (tryLoad(path)) return;
		}
		for (String base : SEARCH_DIRS) {
			String found = findInTdlibSubdir(base);
			if (found != null && tryLoad(found)) return;
		}
		log.error("Failed to load TDLib. Check if TDLib is installed.");
		throw new UnsatisfiedLinkError("libtdjson not found in any known location");
	}

	private static boolean tryLoad(String path) {
		File file = new File(path);
		if (!file.exists()) return false;
		try {
			System.load(path);
			System.setProperty("jna.library.path", file.getParent());
			return true;
		} catch (UnsatisfiedLinkError e) {
			return false;
		}
	}

	private static String findInTdlibSubdir(String base) {
		try (var stream = Files.newDirectoryStream(Path.of(base), "TDLib*")) {
			for (Path sub : stream) {
				Path lib = sub.resolve("libtdjson.so");
				if (Files.exists(lib)) return lib.toString();
			}
		} catch (Exception ignored) {}
		return null;
	}
}
