package com.telegramtui.ui.layout;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.telegramtui.telegram.AuthState;
import com.telegramtui.telegram.TelegramClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class AuthScreen {

    private static final Logger log = LoggerFactory.getLogger(AuthScreen.class.getSimpleName());

    private final TelegramClient client;
    private final StringBuilder input = new StringBuilder();

    private String statusMessage = "";
    private boolean statusIsError = false;
    private boolean submitting = false;
    private boolean done = false;
    private boolean dirty = false;

    // errors and info from TDLib callbacks are stored here and picked up by the main loop
    private volatile String pendingError = null;
    private volatile String pendingInfo = null;
    private long statusShownAt = 0L;

    private Step step = Step.PHONE;
    private String passwordHint = "";

    private Mode mode = Mode.INSERT;
    private final StringBuilder commandBuffer = new StringBuilder();

    // package-private so AuthRenderer can reference them
    enum Step { PHONE, CODE, PASSWORD, RECOVERY_CODE }
    enum Mode { INSERT, COMMAND }

    public AuthScreen(TelegramClient client) {
        this.client = client;
    }

    public void start() throws IOException, InterruptedException {
        var terminal = new DefaultTerminalFactory().createTerminal();
        var screen = new TerminalScreen(terminal);
        screen.startScreen();
        screen.clear();
        draw(screen);
        screen.refresh();

        while (!done) {
            KeyStroke key = screen.pollInput();
            if (key != null && !submitting) {
                handleKey(key);
            }

            // TDLib callback wants to move to the recovery code step
            String info = pendingInfo;
            if (info != null) {
                pendingInfo = null;
                step = Step.RECOVERY_CODE;
                input.setLength(0);
                submitting = false;
                statusMessage = info;
                statusIsError = false;
                statusShownAt = System.currentTimeMillis();
                mode = Mode.INSERT;
                dirty = true;
            }

            // TDLib callback reported an error
            String err = pendingError;
            if (err != null) {
                pendingError = null;
                submitting = false;
                input.setLength(0);
                statusMessage = "Error: " + err;
                statusIsError = true;
                statusShownAt = System.currentTimeMillis();
                dirty = true;
            }

            // clear status message after 4 seconds
            if (!statusMessage.isEmpty() && !submitting
                    && System.currentTimeMillis() - statusShownAt > 4000) {
                statusIsError = false;
                statusMessage = "";
                dirty = true;
            }

            AuthState state = client.getUpdateHandler().pollAuthState();
            if (state != null) {
                handleAuthState(state);
                dirty = true;
            }

            TerminalSize newSize = screen.doResizeIfNecessary();
            if (newSize != null) {
                screen.clear();
                dirty = true;
            }

            if (dirty) {
                draw(screen);
                screen.refresh();
                dirty = false;
            }

            Thread.sleep(50);
        }
        screen.stopScreen();
    }

    private void handleKey(KeyStroke key) {
        switch (mode) {
            case COMMAND -> handleCommandKey(key);
            case INSERT  -> handleInsertKey(key);
        }
    }

    private void handleCommandKey(KeyStroke key) {
        switch (key.getKeyType()) {
            case Character -> {
                char ch = key.getCharacter();
                if (commandBuffer.isEmpty() && ch == 'i') {
                    mode = Mode.INSERT;
                    statusMessage = "";
                    statusIsError = false;
                } else {
                    commandBuffer.append(ch);
                }
            }
            case Backspace -> {
                if (!commandBuffer.isEmpty()) commandBuffer.deleteCharAt(commandBuffer.length() - 1);
            }
            case Enter -> {
                if (!commandBuffer.isEmpty()) {
                    executeCommand(commandBuffer.toString());
                    commandBuffer.setLength(0);
                }
            }
            case Escape -> {
                commandBuffer.setLength(0);
                statusMessage = "";
                statusIsError = false;
            }
            default -> { /* ignore */ }
        }
        dirty = true;
    }

    private void handleInsertKey(KeyStroke key) {
        switch (key.getKeyType()) {
            case Character -> {
                if (statusIsError) { statusIsError = false; statusMessage = ""; }
                input.append(key.getCharacter());
                dirty = true;
            }
            case Backspace -> {
                if (!input.isEmpty()) { input.deleteCharAt(input.length() - 1); dirty = true; }
            }
            case Enter -> {
                if (!input.isEmpty()) submitCurrentInput();
            }
            case Escape -> {
                mode = Mode.COMMAND;
                commandBuffer.setLength(0);
                dirty = true;
            }
            default -> { /* ignore */ }
        }
    }

    private void executeCommand(String cmd) {
        switch (cmd.trim()) {
            case "hint" -> {
                if (step == Step.PASSWORD) {
                    statusMessage = passwordHint.isEmpty() ? "No hint set" : "Hint: " + passwordHint;
                    statusIsError = false;
                } else {
                    statusMessage = "Not available at this step";
                    statusIsError = true;
                }
                statusShownAt = System.currentTimeMillis();
            }
            case "forgot" -> {
                if (step == Step.PASSWORD) {
                    requestPasswordRecovery();
                } else {
                    statusMessage = "Not available at this step";
                    statusIsError = true;
                    statusShownAt = System.currentTimeMillis();
                }
            }
            case "q", "quit" -> System.exit(0);
            default -> {
                statusMessage = "Unknown: " + cmd;
                statusIsError = true;
                statusShownAt = System.currentTimeMillis();
            }
        }
    }

    private void submitCurrentInput() {
        String text = input.toString();
        submitting = true;
        statusIsError = false;
        dirty = true;

        switch (step) {
            case PHONE -> {
                statusMessage = "Sending...";
                String phone = text.replaceAll("\\s+", "");
                client.send(
                        "{\"@type\":\"setAuthenticationPhoneNumber\",\"phone_number\":\"" + phone + "\"}",
                        result -> handleSubmitResult(result, "Phone rejected"));
            }
            case CODE -> {
                statusMessage = "Verifying...";
                client.send(
                        "{\"@type\":\"checkAuthenticationCode\",\"code\":\"" + text.trim() + "\"}",
                        result -> handleSubmitResult(result, "Code rejected"));
            }
            case PASSWORD -> {
                statusMessage = "Verifying...";
                String escaped = text.replace("\\", "\\\\").replace("\"", "\\\"");
                client.send(
                        "{\"@type\":\"checkAuthenticationPassword\",\"password\":\"" + escaped + "\"}",
                        result -> handleSubmitResult(result, "Password rejected"));
            }
            case RECOVERY_CODE -> {
                statusMessage = "Verifying...";
                client.send(
                        "{\"@type\":\"recoverAuthenticationPassword\",\"recovery_code\":\"" + text.trim() + "\"}",
                        result -> handleSubmitResult(result, "Recovery code rejected"));
            }
        }
    }

    private void handleSubmitResult(String result, String logPrefix) {
        if ("error".equals(TelegramClient.getString(result, "@type"))) {
            String msg = TelegramClient.getString(result, "message");
            log.warn("{}: {}", logPrefix, msg);
            if ("UPDATE_APP_TO_LOGIN".equals(msg)) {
                String os = System.getProperty("os.name", "").toLowerCase();
                String fix = os.contains("mac")
                        ? "brew install tdlib --HEAD"
                        : "re-install using install-full.sh — see github.com/k4dy/telegramtui";
                pendingError = "TDLib too old. Update it and restart: " + fix;
            } else {
                pendingError = msg != null ? msg : "Unknown error";
            }
        }
    }

    private void requestPasswordRecovery() {
        client.send(
                "{\"@type\":\"requestAuthenticationPasswordRecovery\"}",
                result -> {
                    if ("error".equals(TelegramClient.getString(result, "@type"))) {
                        String msg = TelegramClient.getString(result, "message");
                        pendingError = msg != null ? msg : "Recovery unavailable";
                    } else {
                        pendingInfo = "Email sent";
                    }
                });
    }

    private void handleAuthState(AuthState state) {
        if (state instanceof AuthState.WaitPhone) {
            step = Step.PHONE;
            input.setLength(0);
            passwordHint = "";
            statusMessage = "";
            statusIsError = false;
            submitting = false;
            mode = Mode.INSERT;
        } else if (state instanceof AuthState.WaitCode) {
            step = Step.CODE;
            input.setLength(0);
            statusMessage = "";
            statusIsError = false;
            submitting = false;
        } else if (state instanceof AuthState.WaitPassword) {
            AuthState.WaitPassword wp = (AuthState.WaitPassword) state;
            step = Step.PASSWORD;
            passwordHint = wp.hint();
            input.setLength(0);
            statusMessage = "";
            statusIsError = false;
            submitting = false;
        } else if (state instanceof AuthState.Ready) {
            done = true;
        }
    }

    private void draw(Screen screen) throws IOException {
        AuthRenderer.draw(screen, mode, step, input.toString(),
                submitting, statusMessage, statusIsError, commandBuffer.toString());
    }
}
