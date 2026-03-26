package com.telegramtui.ui.chat;

import com.googlecode.lanterna.graphics.TextGraphics;
import com.telegramtui.model.ChatModel;
import com.telegramtui.ui.common.Box;
import com.telegramtui.ui.common.CatppuccinMocha;

import java.util.ArrayList;
import java.util.List;

public class ChatTabBar {

    private final List<ChatModel> tabs = new ArrayList<>();
    private int activeTabIndex = -1;

    // Opens a chat tab. Switches to it if already open.
    // Returns true if a new tab was created (caller should load message history),
    // or false if an existing tab was just activated.
    public boolean openChat(ChatModel chat) {
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).id() == chat.id()) {
                activeTabIndex = i;
                return false;
            }
        }

        tabs.add(chat);
        activeTabIndex = tabs.size() - 1;
        return true;
    }

    public void closeCurrentTab() {
        if (activeTabIndex < 0) return;
        tabs.remove(activeTabIndex);
        activeTabIndex = tabs.isEmpty() ? -1 : Math.min(activeTabIndex, tabs.size() - 1);
    }

    public void closeOtherTabs() {
        if (activeTabIndex < 0) return;
        ChatModel current = tabs.get(activeTabIndex);
        tabs.clear();
        tabs.add(current);
        activeTabIndex = 0;
    }

    // Switches to the tab at the given index (used for '1'-'5' key shortcuts)
    // Returns true if the index was valid
    public boolean switchToTab(int index) {
        if (index < 0 || index >= tabs.size()) return false;
        activeTabIndex = index;
        return true;
    }

    public void switchToNextTab() {
        if (tabs.isEmpty()) return;
        activeTabIndex = (activeTabIndex + 1) % tabs.size();
    }

    public ChatModel activeChat() {
        if (activeTabIndex < 0 || activeTabIndex >= tabs.size()) return null;
        return tabs.get(activeTabIndex);
    }

    public int getActiveTabIndex() {
        return activeTabIndex;
    }

    public int getTabCount() {
        return tabs.size();
    }

    // Draws tab labels inside the top border row of the given box
    public void render(TextGraphics g, Box box) {
        int borderRow = box.getInnerTop() - 1;
        int col = box.getInnerLeft();
        int maxCol = col + box.getInnerWidth();
        for (int i = 0; i < tabs.size(); i++) {
            String label = formatLabel(i, tabs.get(i));
            if (col + label.length() > maxCol) break;
            g.setBackgroundColor(CatppuccinMocha.BASE);
            g.setForegroundColor(i == activeTabIndex ? CatppuccinMocha.MAUVE : CatppuccinMocha.OVERLAY2);
            g.putString(col, borderRow, label);
            col += label.length() + 1;
        }
    }

    private static String formatLabel(int index, ChatModel chat) {
        return "[" + (index + 1) + "]-" + firstWord(chat.title());
    }

    // returns only the first word (everything before the first space), or the full string if no space
    private static String firstWord(String text) {
        int firstSpace = text.indexOf(' ');
        return firstSpace > 0 ? text.substring(0, firstSpace) : text;
    }
}
