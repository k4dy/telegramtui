#!/usr/bin/env bash
# TelegramTUI installer
# Usage: curl -fsSL https://raw.githubusercontent.com/k4dy/telegramtui/master/install.sh | bash

set -e

REPO="k4dy/telegramtui"
JAR_NAME="telegramtui"

say()  { printf '\033[1;32m==> \033[0m%s\n' "$*" >&2; }
warn() { printf '\033[1;33mwarn:\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31merror:\033[0m %s\n' "$*" >&2; exit 1; }

OS="$(uname -s)"
case "$OS" in
  Darwin)
    if [ -w "/usr/local/bin" ]; then
      INSTALL_DIR="/usr/local/bin"
    else
      INSTALL_DIR="${HOME}/.local/bin"
    fi ;;
  Linux)  INSTALL_DIR="/usr/local/bin" ;;
  *)      die "Unsupported OS: $OS" ;;
esac

IS_WSL=false
grep -qiE 'microsoft|wsl' /proc/version 2>/dev/null && IS_WSL=true

install_java_apt() {
  say "Installing Java 21 via apt..."
  sudo apt-get update -qq
  sudo apt-get install -y openjdk-21-jre
}

install_tdlib_apt() {
  if apt-cache show libtd-dev &>/dev/null 2>&1; then
    say "Installing TDLib via apt..."
    sudo apt-get update -qq
    sudo apt-get install -y libtd-dev
  else
    warn "libtd-dev not available in your apt repos (Ubuntu 24.04 LTS does not include it)."
    echo "  Use the full installer which builds TDLib from source (~20 min):"
    echo "  curl -fsSL https://raw.githubusercontent.com/k4dy/telegramtui/master/install-full.sh | bash"
    die "TDLib is required."
  fi
}

check_java() {
  if ! command -v java &>/dev/null; then
    if [ "$OS" = "Darwin" ]; then
      warn "Java not found. Install with: brew install openjdk@21"
      die "Java 21+ is required."
    elif command -v apt-get &>/dev/null; then
      install_java_apt
    elif command -v pacman &>/dev/null; then
      warn "Java not found. Install with: sudo pacman -S jre21-openjdk"
      die "Java 21+ is required."
    else
      warn "Java not found. Install Java 21+ from https://adoptium.net"
      die "Java 21+ is required."
    fi
  fi

  JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d. -f1)
  if [ -z "$JAVA_VERSION" ] || [ "$JAVA_VERSION" -lt 21 ] 2>/dev/null; then
    warn "Java $JAVA_VERSION detected — TelegramTUI requires Java 21+."
    if [ "$OS" = "Darwin" ]; then
      echo "  Upgrade with: brew install openjdk@21"
    elif command -v apt-get &>/dev/null; then
      install_java_apt
      JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d. -f1)
      [ "$JAVA_VERSION" -ge 21 ] 2>/dev/null || die "Java 21+ is required."
    fi
    die "Java 21+ is required."
  fi
  say "Java $JAVA_VERSION found."
}

tdlib_found() {
  ldconfig -p 2>/dev/null | grep -q libtdjson && return 0
  find /usr/lib /usr/local/lib -name "libtdjson*" 2>/dev/null | grep -q . && return 0
  return 1
}

check_tdlib() {
  case "$OS" in
    Darwin)
      [ -f "$(brew --prefix tdlib 2>/dev/null)/lib/libtdjson.dylib" ] && { say "TDLib found."; return; }
      warn "TDLib (libtdjson) not found. Install with: brew install tdlib"
      die "TDLib is required."
      ;;
    Linux)
      tdlib_found && { say "TDLib found."; return; }
      ;;
  esac

  if [ "$OS" = "Linux" ]; then
    if command -v pacman &>/dev/null; then
      warn "TDLib (libtdjson) not found. Install with: yay -S telegram-tdlib"
      die "TDLib is required."
    elif command -v apt-get &>/dev/null; then
      install_tdlib_apt
      tdlib_found || die "TDLib installation failed. Try: sudo apt install libtd-dev"
    else
      warn "TDLib (libtdjson) not found."
      echo "  Build from source: https://github.com/tdlib/td#building"
      die "TDLib is required."
    fi
  fi
  say "TDLib found."
}

fetch_latest_version() {
  if command -v curl &>/dev/null; then
    curl -fsSL "https://api.github.com/repos/$REPO/releases/latest" \
      | grep '"tag_name"' | cut -d'"' -f4
  elif command -v wget &>/dev/null; then
    wget -qO- "https://api.github.com/repos/$REPO/releases/latest" \
      | grep '"tag_name"' | cut -d'"' -f4
  else
    die "curl or wget is required."
  fi
}

install_file() {
  local src="$1" dest="$2" mode="$3"
  if [ -w "$(dirname "$dest")" ]; then
    mv "$src" "$dest"
    chmod "$mode" "$dest"
  else
    sudo mv "$src" "$dest"
    sudo chmod "$mode" "$dest"
  fi
}

download_jar() {
  local tag="$1"
  local version="${tag#v}"
  local url="https://github.com/$REPO/releases/download/$tag/$JAR_NAME-$version.jar"
  local dest="$INSTALL_DIR/$JAR_NAME.jar"
  local tmp
  tmp="$(mktemp)"

  say "Downloading $JAR_NAME $tag..."
  if ! mkdir -p "$INSTALL_DIR" 2>/dev/null; then sudo mkdir -p "$INSTALL_DIR"; fi
  if command -v curl &>/dev/null; then
    curl -fsSL -o "$tmp" "$url"
  else
    wget -qO "$tmp" "$url"
  fi
  install_file "$tmp" "$dest" 644
  echo "$dest"
}

write_wrapper() {
  local jar_path="$1"
  local wrapper="$INSTALL_DIR/$JAR_NAME"
  local tmp
  tmp="$(mktemp)"

  # detect TDLib path for jna.library.path
  local tdlib_lib=""
  if [ "$OS" = "Darwin" ] && command -v brew &>/dev/null; then
    tdlib_lib="$(brew --prefix tdlib 2>/dev/null)/lib"
  fi

  {
    echo '#!/bin/bash'
    if [ -n "$tdlib_lib" ]; then
      echo "exec java -Djna.library.path=\"$tdlib_lib\" -jar \"$jar_path\" \"\$@\""
    else
      echo "exec java -jar \"$jar_path\" \"\$@\""
    fi
  } > "$tmp"
  install_file "$tmp" "$wrapper" 755
  say "Installed wrapper: $wrapper"
}

check_path() {
  if [[ ":$PATH:" != *":$INSTALL_DIR:"* ]]; then
    warn "$INSTALL_DIR is not in PATH. Add this to your shell profile:"
    echo "  export PATH=\"\$PATH:$INSTALL_DIR\""
  fi
}

say "TelegramTUI installer"
check_java
check_tdlib

TAG="$(fetch_latest_version)"
[ -z "$TAG" ] && die "Could not fetch latest release version."
say "Latest release: $TAG"

JAR="$(download_jar "$TAG")"
write_wrapper "$JAR"
check_path

say "Done! Run: telegramtui"
