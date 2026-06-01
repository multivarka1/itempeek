package ru.multivarka.itempeek;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ItemPeekChatScreen extends ChatScreen {
    private static final Pattern PRIVATE_MESSAGE = Pattern.compile("^/(msg|tell|w)\\s+(\\S+)\\s+(.+)$");

    private final String marker;

    public ItemPeekChatScreen(String marker) {
        super(marker + " ", false);
        this.marker = marker;
    }

    @Override
    public void handleChatInput(String message, boolean addToRecent) {
        String normalized = normalizeChatMessage(message);
        Matcher matcher = PRIVATE_MESSAGE.matcher(normalized);
        if (!matcher.matches() || !matcher.group(3).contains(this.marker)) {
            super.handleChatInput(message, addToRecent);
            return;
        }

        if (addToRecent) {
            Minecraft.getInstance().gui.getChat().addRecentChat(normalized);
        }

        ClientPacketDistributor.sendToServer(new PrivateItemMessagePayload(matcher.group(2), matcher.group(3)));
    }

    @Override
    public void removed() {
        ClientPacketDistributor.sendToServer(ClearPendingItemPayload.INSTANCE);
        super.removed();
    }
}
