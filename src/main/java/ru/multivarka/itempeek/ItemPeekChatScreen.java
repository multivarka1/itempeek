package ru.multivarka.itempeek;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import ru.multivarka.itempeek.chat.ParsedPrivateMessage;
import ru.multivarka.itempeek.chat.PrivateMessageCommandParser;
import ru.multivarka.itempeek.config.ItemPeekConfigSnapshot;
import java.util.Arrays;

public final class ItemPeekChatScreen extends ChatScreen {
    private static final java.util.Set<String> DEFAULT_ALIASES = java.util.Set.of("msg", "tell", "w", "minecraft:msg", "minecraft:tell", "minecraft:w");

    private final String marker;

    public ItemPeekChatScreen(String marker) {
        super(marker + " ", false);
        this.marker = marker;
    }

    @Override
    public void handleChatInput(String message, boolean addToRecent) {
        String normalized = normalizeChatMessage(message);
        ParsedPrivateMessage parsed = PrivateMessageCommandParser.parse(normalized, DEFAULT_ALIASES).orElse(null);
        if (parsed == null || !parsed.message().contains(this.marker)) {
            super.handleChatInput(message, addToRecent);
            return;
        }

        if (addToRecent) {
            Minecraft.getInstance().gui.hud.getChat().addRecentChat(normalized);
        }

        ClientPacketDistributor.sendToServer(new PrivateItemMessagePayload(parsed.target(), parsed.message()));
    }

    @Override
    public void removed() {
        ClientPacketDistributor.sendToServer(ClearPendingItemPayload.INSTANCE);
        super.removed();
    }
}
