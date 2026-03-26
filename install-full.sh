#!/usr/bin/env bash
# TelegramTUI full installer — builds TDLib from source, then installs TelegramTUI
# Use this on Ubuntu 24.04 LTS or any system without a pre-built TDLib package
# Usage: curl -fsSL https://raw.githubusercontent.com/k4dy/telegramtui/master/install-full.sh | bash

set -e

say()  { printf '\033[1;32m==> \033[0m%s\n' "$*" >&2; }
warn() { printf '\033[1;33mwarn:\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31merror:\033[0m %s\n' "$*" >&2; exit 1; }

OS="$(uname -s)"

tdlib_found() {
  ldconfig -p 2>/dev/null | grep -q libtdjson && return 0
  find /usr/lib /usr/local/lib -name "libtdjson*" 2>/dev/null | grep -q . && return 0
  return 1
}

build_tdlib_linux() {
  say "Installing TDLib build dependencies..."
  if command -v apt-get &>/dev/null; then
    sudo apt-get update -qq
    sudo apt-get install -y git cmake gperf libssl-dev zlib1g-dev g++
  else
    die "Unsupported package manager. Install cmake, gperf, libssl-dev, zlib1g-dev, g++ manually and re-run."
  fi

  say "Cloning TDLib..."
  TDLIB_DIR="$(mktemp -d)"
  git clone --depth 1 https://github.com/tdlib/td.git "$TDLIB_DIR"

  say "Building TDLib — this takes ~20 minutes..."
  mkdir -p "$TDLIB_DIR/build"
  cd "$TDLIB_DIR/build"
  cmake -DCMAKE_BUILD_TYPE=Release .. -DCMAKE_INSTALL_PREFIX=/usr/local
  cmake --build . --target tdjson -- -j"$(nproc)"
  sudo cmake --install . || true
  sudo ldconfig
  cd /
  rm -rf "$TDLIB_DIR"
  say "TDLib installed."
}

case "$OS" in
  Darwin)
    say "macOS: building TDLib HEAD via Homebrew (~20 min)..."
    brew install tdlib --HEAD
    ;;
  Linux)
    if tdlib_found; then
      say "TDLib already installed, skipping build."
    else
      build_tdlib_linux
    fi
    ;;
  *)
    die "Unsupported OS: $OS"
    ;;
esac

say "Installing TelegramTUI..."
INSTALL_SH="https://raw.githubusercontent.com/k4dy/telegramtui/master/install.sh"
if command -v curl &>/dev/null; then
  curl -fsSL "$INSTALL_SH" | bash
elif command -v wget &>/dev/null; then
  wget -qO- "$INSTALL_SH" | bash
else
  die "curl or wget is required to download TelegramTUI."
fi
