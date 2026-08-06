package ru.multivarka.itempeek;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;

import ru.multivarka.itempeek.chat.ParsedPrivateMessage;
import ru.multivarka.itempeek.chat.PrivateMessageCommandParser;
import java.util.Set;

public final class ItemPeekChatScreen extends ChatScreen {
    private static final Set<String> DEFAULT_ALIASES = Set.of("msg", "tell", "w", "minecraft:msg", "minecraft:tell", "minecraft:w");

    private final String marker;

    public ItemPeekChatScreen(String marker) {
        super(marker + " ");
        this.marker = marker;
    }

    @Override
    public boolean handleChatInput(String message, boolean addToRecent) {
        String normalized = normalizeChatMessage(message);
        ParsedPrivateMessage parsed = PrivateMessageCommandParser.parse(normalized, DEFAULT_ALIASES).orElse(null);
        if (parsed == null || !parsed.message().contains(this.marker)) {
            return super.handleChatInput(message, addToRecent);
        }

        if (addToRecent) {
            Minecraft.getInstance().gui.getChat().addRecentChat(normalized);
        }

        Network.sendPrivateItemMessageToServer(parsed.target(), parsed.message());
        return true;
    }

    @Override
    public void removed() {
        Network.clearPendingItemOnServer();
        super.removed();
    }
}
