## TelegramTUI

A terminal Telegram client with vim keybindings, inspired by [lazygit](https://github.com/jesseduffield/lazygit). Built
in Java 21 with [Lanterna](https://github.com/mabe02/lanterna) and [TDLib](https://core.telegram.org/tdlib).

TelegramTUI combines vim-style navigation with telescope-like fuzzy search to let you stay completely keyboard-driven.
The idea is simple: open the chat you want with `/`, switch to fullscreen with `f`, and never touch the mouse. Normal
mode gives you `hjkl` to move through messages, single-key actions for reply, delete, and file handling, and telescope
search that works across chats, messages, and senders.

---

## Screenshots

![Login screen](assets/01-login.jpg)

Enter your phone number, then the verification code Telegram sends you. If you have 2FA enabled, the password prompt
follows. After that your session is saved to `~/.telegramtui/` and login won't be needed again.

---

![Main view](assets/02-mainView.jpg)

Split view — chat list on the left, conversation on the right. Your Telegram folders map directly to the sidebar tabs:
`0` for all chats, `1`–`9` for folders in order. Each chat you open gets a numbered tab at the top of the conversation
panel.

---

![Focus mode](assets/03-focusView.jpg)

`f` hides the sidebar and expands the conversation to full width. Good for longer reads or when you want fewer things on
screen. `Tab` and number keys still switch between open tabs.

---

![Search](assets/04-find.jpg)

Three search modes: `/` filters your chat list as you type, `?` does full-text message search through Telegram's
servers, `@` lets you pick a sender and browse their messages. All three open the same popup — `j`/`k` to move, `Enter`
to confirm, `Esc` to close.

---

![Help overlay](assets/05-help.jpg)

`:` opens the command bar. Type `help` and hit Enter to get this overlay. Same place for `logout` and `q` — basically
Vim's `:` but for a chat client.

---

### What you can do in normal mode

Once you're in a chat, `j`/`k` moves between messages and `h`/`l` switches focus between the sidebar and the
conversation. Select a message and you get single-key actions: `r` to reply, `e` to edit your own message, `d` to
delete, `o` to open an attachment or link directly in your default app. Files are downloaded on demand and opened
immediately — no manual path copying.

---

## Requirements

- **Java 21+**
- **TDLib** — the official Telegram C++ library
    - macOS: `brew install tdlib --HEAD` (regular `brew install tdlib` installs an older version that won't work)
    - Arch: `yay -S telegram-tdlib`
    - Ubuntu 24.10+ / Debian: `sudo apt install libtd-dev`
    - Ubuntu 24.04 LTS: use `install-full.sh` below (builds from source)
    - Other: [build from source](https://github.com/tdlib/td#building)

## Install

### curl (macOS & Linux & WSL)

Quick install — requires TDLib already installed on your system:

```bash
curl -fsSL https://raw.githubusercontent.com/k4dy/telegramtui/master/install.sh | bash
```

Full install — builds TDLib from source, then installs TelegramTUI. Safest option, no pre-installed dependencies
needed (~20 min):

```bash
curl -fsSL https://raw.githubusercontent.com/k4dy/telegramtui/master/install-full.sh | bash
```

### Homebrew

```bash
brew tap k4dy/telegramtui
brew install telegramtui
```

### AUR

`telegram-tdlib` is a dependency that gets built from source automatically. If you want to see its progress separately
before installing TelegramTUI (~10–20 min), you can pre-build it first:

```bash
yay -S telegram-tdlib
yay -S telegramtui
```

Or let yay handle everything at once:

```bash
yay -S telegramtui
```

### apt (Ubuntu 24.10+ / Debian)

```bash
sudo apt install libtd-dev
curl -fsSL https://raw.githubusercontent.com/k4dy/telegramtui/master/install.sh | bash
```

### Ubuntu 24.04 LTS

Ubuntu 24.04 doesn't ship TDLib in its repositories. Use the full installer which builds TDLib from source (~20 min):

```bash
curl -fsSL https://raw.githubusercontent.com/k4dy/telegramtui/master/install-full.sh | bash
```

### Windows (WSL)

Install [WSL](https://learn.microsoft.com/en-us/windows/wsl/install) if you haven't already (run `wsl --install` in
PowerShell, then restart), then open your WSL terminal.

**Ubuntu 24.10+** — install TDLib first, then TelegramTUI:

```bash
sudo apt install libtd-dev
curl -fsSL https://raw.githubusercontent.com/k4dy/telegramtui/master/install.sh | bash
```

**Ubuntu 24.04 LTS** — use the full installer (builds TDLib from source, ~20 min):

```bash
curl -fsSL https://raw.githubusercontent.com/k4dy/telegramtui/master/install-full.sh | bash
```

### Manual

Download the latest JAR from [Releases](https://github.com/k4dy/telegramtui/releases):

```bash
java -Djna.library.path=/path/to/tdlib/lib -jar telegramtui-*.jar
```

---

## First run

TelegramTUI will ask for your phone number, then a verification code from Telegram. If you have two-factor
authentication, your password is requested next. Your session is saved to `~/.telegramtui/` and reused on future
launches.

---

## Navigation

TelegramTUI uses vim-style navigation throughout. There are two modes:

- **Normal mode** — navigate with `hjkl`, trigger actions with single keys
- **Insert mode** — type and send messages (enter with `i`, exit with `Esc`)

### Cheatsheet

#### Movement

| Key       | Action                                |
|-----------|---------------------------------------|
| `h`       | focus sidebar                         |
| `l`       | focus chat                            |
| `j` / `k` | move down / up                        |
| `J` / `K` | jump 10 rows                          |
| `G`       | jump to newest message                |
| `Enter`   | open selected chat / deselect message |

#### Chat tabs

| Key     | Action               |
|---------|----------------------|
| `Tab`   | next tab             |
| `1`–`9` | switch to tab N      |
| `x`     | close current tab    |
| `X`     | close all other tabs |

#### Messages

| Key   | Action                      |
|-------|-----------------------------|
| `i`   | enter insert mode (compose) |
| `e`   | edit selected message       |
| `d`   | delete selected message     |
| `r`   | reply to selected message   |
| `o`   | open attachment             |
| `Esc` | exit insert mode / deselect |

#### View

| Key | Action                 |
|-----|------------------------|
| `f` | toggle fullscreen chat |
| `q` | quit                   |

#### Search (telescope)

| Key | Opens                                         |
|-----|-----------------------------------------------|
| `/` | chat search — fuzzy filter as you type        |
| `?` | message search — full-text via TDLib          |
| `@` | sender search — drill into messages by sender |

Inside any telescope popup: `j`/`k` move through results, `Enter` confirms, `Esc` closes.

#### Command bar

Press `:` to open the command bar:

| Command      | Action               |
|--------------|----------------------|
| `help`       | keybinding reference |
| `logout`     | log out              |
| `q` / `quit` | quit                 |

---

## Building from source

```bash
git clone https://github.com/k4dy/telegramtui.git
cd telegramtui
mvn package -DskipTests
java -jar target/telegramtui-*.jar
```

Or run directly:

```bash
mvn compile exec:java
```

You'll need your own Telegram API credentials from [my.telegram.org](https://my.telegram.org). Create
`~/.telegramtui/config.properties`:

```properties
api.id=YOUR_API_ID
api.hash=YOUR_API_HASH
```

---

## Architecture

### Package layout

```
src/main/java/com/telegramtui/
├── app/          entry point, config, lifecycle
├── model/        domain records (Chat, Message, Folder…)
├── telegram/     TDLib client, update routing, auth states
├── service/      chat, folder, message and file services
└── ui/
    ├── layout/   screens, input routing
    ├── chat/     conversation panel, message rendering, tabs
    ├── sidebar/  chat list, folder navigation
    ├── popup/    telescope search, command bar, help overlay
    └── common/   shared widgets, Catppuccin Mocha palette
```

### Design decisions

**Three layers.** TDLib → services → UI. Each service owns its data and exposes simple read methods. The UI just reads
and renders — it never touches the caches directly. Easy to follow when something breaks.

**Immutable models.** `ChatModel`, `MessageModel` etc. are Java records. When TDLib sends an update, I replace the old
record in the map instead of mutating it. Saved me a few threading headaches early on.

**Rendering in its own class.** Each panel has a `*Renderer` that only draws — no state, no logic. The panel class
decides what to show, the renderer decides how. Easier to tweak the UI without worrying about breaking something else.

**One place for keyboard routing.** `MainInputRouter` handles all keypresses and decides which panel gets them. Having
one router that knows the full app state is simpler.

**Search like telescope.nvim.** `/` for chats, `?` for messages, `@` for senders — all open the same popup style. Local
filtering for chats, TDLib server search for messages. Took a while to get right but it's the feature I use most.

---

## Credits

- [lazygit](https://github.com/jesseduffield/lazygit) — inspiration for the overall layout and keyboard-first UX
- [telescope.nvim](https://github.com/nvim-telescope/telescope.nvim) — inspiration for the fuzzy search popup style
- [TDLib](https://github.com/tdlib/td) — Telegram's official client library
- [Catppuccin](https://github.com/catppuccin/catppuccin) — color scheme

## License

[MIT](LICENSE)
