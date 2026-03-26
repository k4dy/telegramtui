package com.telegramtui.ui.layout;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;

public class FocusManager {
	private Panel focused = Panel.SIDEBAR;

	public Panel getFocused() {
		return focused;
	}

	public void setFocused(Panel panel) {
		this.focused = panel;
	}

	public boolean handleKey(KeyStroke key) {
		if (key.getKeyType() != KeyType.Character) {
			return false;
		}
		char c = key.getCharacter();
		if (c == 'l' && focused == Panel.SIDEBAR) {
			focused = Panel.CHAT;
			return true;

		}
		if (c == 'h' && focused == Panel.CHAT) {
			focused = Panel.SIDEBAR;
			return true;
		}
		return false;

	}

	public enum Panel {SIDEBAR, CHAT}

}
