package ru.multivarka.itempeek;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record InsertItemPayload(int slotIndex, String marker) implements CustomPacketPayload {
    public static final Type<InsertItemPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ItemPeek.MODID, "insert_item"));

    public static final StreamCodec<RegistryFriendlyByteBuf, InsertItemPayload> CODEC =
            new StreamCodec<RegistryFriendlyByteBuf, InsertItemPayload>() {
                @Override
                public InsertItemPayload decode(RegistryFriendlyByteBuf buf) {
                    return new InsertItemPayload(buf.readVarInt(), buf.readUtf(256));
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, InsertItemPayload value) {
                    buf.writeVarInt(value.slotIndex());
                    buf.writeUtf(value.marker(), 256);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
