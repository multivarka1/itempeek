package ru.multivarka.itempeek;

import net.minecraft.network.FriendlyByteBuf;

public class PrivateItemMessage {
    final String targets;
    final String message;

    public PrivateItemMessage(String targets, String message) {
        this.targets = targets;
        this.message = message;
    }

    public static void encode(PrivateItemMessage msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.targets, 256);
        buf.writeUtf(msg.message, 256);
    }

    public static PrivateItemMessage decode(FriendlyByteBuf buf) {
        return new PrivateItemMessage(buf.readUtf(256), buf.readUtf(256));
    }
}
