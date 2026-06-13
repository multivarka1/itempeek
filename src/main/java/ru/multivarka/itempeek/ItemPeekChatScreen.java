package ru.multivarka.itempeek;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ItemPeekChatScreen extends ChatScreen {
    private static final Pattern PRIVATE_MESSAGE = Pattern.compile("^/(msg|tell|w)\\s+(\\S+)\\s+(.+)$");

    private final String marker;

    public ItemPeekChatScreen(String marker) {
        super(marker + " ");
        this.marker = marker;
    }

    @Override
    public boolean handleChatInput(String message, boolean addToRecent) {
        String normalized = normalizeChatMessage(message);
        Matcher matcher = PRIVATE_MESSAGE.matcher(normalized);
        if (!matcher.matches() || !matcher.group(3).contains(this.marker)) {
            return super.handleChatInput(message, addToRecent);
        }

        if (addToRecent) {
            Minecraft.getInstance().gui.getChat().addRecentChat(normalized);
        }

        Network.sendPrivateItemMessageToServer(matcher.group(2), matcher.group(3));
        return true;
    }

    @Override
    public void removed() {
        Network.clearPendingItemOnServer();
        super.removed();
    }
}
