package ru.multivarka.itempeek;

import net.minecraft.network.FriendlyByteBuf;

public class ClearPendingItemMessage {
    public static final ClearPendingItemMessage INSTANCE = new ClearPendingItemMessage();

    public static void encode(ClearPendingItemMessage msg, FriendlyByteBuf buf) {
    }

    public static ClearPendingItemMessage decode(FriendlyByteBuf buf) {
        return INSTANCE;
    }
}
