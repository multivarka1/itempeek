package ru.multivarka.itempeek;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClearPendingItemPayload() implements CustomPacketPayload {
    public static final ClearPendingItemPayload INSTANCE = new ClearPendingItemPayload();
    public static final Type<ClearPendingItemPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(ItemPeek.MODID, "clear_pending_item"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClearPendingItemPayload> CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
