package com.telegramtui.ui.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Opens files and URLs with the system's default application. */
public class SystemOpen {

    private static final Logger log = LoggerFactory.getLogger(SystemOpen.class);

    private SystemOpen() {}

    /** Opens a local file path or URL with the system default app. */
    public static void open(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isBlank()) return;
        String os = System.getProperty("os.name", "").toLowerCase();
        String cmd = os.contains("mac") ? "open" : "xdg-open";
        try {
            new ProcessBuilder(cmd, pathOrUrl).redirectErrorStream(true).start();
        } catch (Exception e) {
            log.warn("Failed to open '{}': {}", pathOrUrl, e.getMessage());
        }
    }
}
