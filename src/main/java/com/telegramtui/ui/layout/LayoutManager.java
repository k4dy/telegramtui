package com.telegramtui.ui.layout;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.screen.Screen;
import com.telegramtui.ui.common.Box;
import com.telegramtui.ui.common.CatppuccinMocha;
import com.telegramtui.ui.common.StatusPane;

public class LayoutManager {

	private static final int HORIZONTAL_MARGIN = 1;
	private static final int BOTTOM_MARGIN = 3;
	private static final int TOP_MARGIN = 0;
	private static final int GAP_BETWEEN_PANELS = 2;

	private static boolean focusMode = false;

	public static void setFocusMode(boolean enabled) {
		focusMode = enabled;
	}

	public static boolean isFocusMode() {
		return focusMode;
	}

	// Persistent box instances — label and outline color survive across redraws
	private static final Box sidebarBox =
			new Box(0, 0, 0, 0, CatppuccinMocha.OVERLAY2, "");
	private static final Box chatBox =
			new Box(0, 0, 0, 0, CatppuccinMocha.OVERLAY2, "[0]-Chat");
	private static final Box statusBox =
			new Box(0, 0, 0, 0, CatppuccinMocha.OVERLAY2, "");

	public static Box getSidebarBox() {
		return sidebarBox;
	}

	public static Box getChatBox() {
		return chatBox;
	}

	public static Box getStatusBox() {
		return statusBox;
	}

	public static int siderbarWidth(int totalWidth) {
		return totalWidth * 30 / 100;
	}

	public static int chatWidth(int totalWidth) {
		return totalWidth - siderbarWidth(totalWidth);
	}

	public static void draw(Screen screen, String connectionLabel, FocusManager focusManager, String modeLabel) {
		TerminalSize terminalSize = screen.getTerminalSize();
		int totalWidth = terminalSize.getColumns();
		int totalHeight = terminalSize.getRows();
		TextGraphics graphics = screen.newTextGraphics();

		graphics.setBackgroundColor(CatppuccinMocha.BASE);
		graphics.setForegroundColor(CatppuccinMocha.BASE);
		graphics.fill(' ');

		if (focusMode) {
			chatBox.setOutlineColor(CatppuccinMocha.MAUVE);
			chatBox.setBounds(0, TOP_MARGIN, totalWidth - 1, totalHeight - 1);
			chatBox.draw(graphics);
		} else {
			int usableWidth = totalWidth - 2 * HORIZONTAL_MARGIN - GAP_BETWEEN_PANELS;
			int sidebarInnerWidth = usableWidth * 30 / 100;

			final int sidebarLeft = HORIZONTAL_MARGIN;
			final int sidebarRight = HORIZONTAL_MARGIN + sidebarInnerWidth + 1;
			final int chatLeft = sidebarRight + GAP_BETWEEN_PANELS;
			final int chatRight = totalWidth - 1;
			final int panelTop = TOP_MARGIN;
			final int sidebarBottom = totalHeight - 1 - BOTTOM_MARGIN;
			final int chatBottom = totalHeight - 1;
			final int statusBarTop = totalHeight - BOTTOM_MARGIN;

			// Highlight the focused panel's border in purple
			sidebarBox.setOutlineColor(focusManager.getFocused() == FocusManager.Panel.SIDEBAR
					? CatppuccinMocha.MAUVE : CatppuccinMocha.OVERLAY2);
			chatBox.setOutlineColor(focusManager.getFocused() == FocusManager.Panel.CHAT
					? CatppuccinMocha.MAUVE : CatppuccinMocha.OVERLAY2);

			sidebarBox.setBounds(sidebarLeft, panelTop, sidebarRight, sidebarBottom);
			chatBox.setBounds(chatLeft, panelTop, chatRight, chatBottom);
			statusBox.setBounds(sidebarLeft, statusBarTop, sidebarRight, totalHeight - 1);

			sidebarBox.draw(graphics);
			chatBox.draw(graphics);
			statusBox.setLabel("");
			statusBox.draw(graphics);

			StatusPane.render(graphics,
					statusBox.getInnerLeft(),
					statusBox.getInnerTop(),
					statusBox.getInnerWidth(),
					connectionLabel,
					modeLabel);
		}
	}
}
