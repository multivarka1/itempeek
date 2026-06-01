package ru.multivarka.itempeek;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PrivateItemMessagePayload(String targets, String message) implements CustomPacketPayload {
    public static final Type<PrivateItemMessagePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(ItemPeek.MODID, "private_item_message"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PrivateItemMessagePayload> CODEC =
            new StreamCodec<RegistryFriendlyByteBuf, PrivateItemMessagePayload>() {
                @Override
                public PrivateItemMessagePayload decode(RegistryFriendlyByteBuf buf) {
                    return new PrivateItemMessagePayload(buf.readUtf(256), buf.readUtf(256));
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, PrivateItemMessagePayload value) {
                    buf.writeUtf(value.targets(), 256);
                    buf.writeUtf(value.message(), 256);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
