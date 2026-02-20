package ru.multivarka.itempeek;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ItemPeekPayload(int slotIndex) implements CustomPacketPayload {
    public static final Type<ItemPeekPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ItemPeek.MODID, "show_item"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ItemPeekPayload> CODEC =
            new StreamCodec<RegistryFriendlyByteBuf, ItemPeekPayload>() {
                @Override
                public ItemPeekPayload decode(RegistryFriendlyByteBuf buf) {
                    return new ItemPeekPayload(buf.readVarInt());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, ItemPeekPayload value) {
                    buf.writeVarInt(value.slotIndex());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
