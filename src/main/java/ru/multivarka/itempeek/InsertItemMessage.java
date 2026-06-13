package ru.multivarka.itempeek;

import net.minecraft.network.FriendlyByteBuf;

public class InsertItemMessage {
    final int slotIndex;
    final String marker;

    public InsertItemMessage(int slotIndex, String marker) {
        this.slotIndex = slotIndex;
        this.marker = marker;
    }

    public static void encode(InsertItemMessage msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.slotIndex);
        buf.writeUtf(msg.marker, 256);
    }

    public static InsertItemMessage decode(FriendlyByteBuf buf) {
        return new InsertItemMessage(buf.readVarInt(), buf.readUtf(256));
    }
}
