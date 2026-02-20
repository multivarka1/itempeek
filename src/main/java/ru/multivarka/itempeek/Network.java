package ru.multivarka.itempeek;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

@EventBusSubscriber(modid = ItemPeek.MODID)
public final class Network {
    private Network() {}

    public static void init() {
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(ItemPeek.MODID);
        registrar.playToServer(ItemPeekPayload.TYPE, ItemPeekPayload.CODEC, (msg, ctx) -> {
            var player = (ServerPlayer) ctx.player();
            ctx.enqueueWork(() -> handleShowItem(player, msg.slotIndex()));
        });
    }

    private static void handleShowItem(ServerPlayer player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= player.containerMenu.slots.size()) {
            return;
        }

        ItemStack stack = player.containerMenu.getSlot(slotIndex).getItem();
        if (stack.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.itempeek.no_item").withStyle(ChatFormatting.RED));
            return;
        }

        Component itemName = stack.getHoverName().copy();

        ChatFormatting rarityColor;
        Rarity rarity = stack.getRarity();
        switch (rarity) {
            case UNCOMMON -> rarityColor = ChatFormatting.YELLOW;
            case RARE -> rarityColor = ChatFormatting.AQUA;
            case EPIC -> rarityColor = ChatFormatting.LIGHT_PURPLE;
            default -> rarityColor = ChatFormatting.WHITE;
        }

        Component itemColored = itemName.copy().withStyle(style -> style.withColor(rarityColor).withItalic(Boolean.FALSE));

        Component leftBracket = Component.literal("[").withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(Boolean.FALSE));
        Component rightBracket = Component.literal("]").withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(Boolean.FALSE));
        Component shown = Component.literal(" ")
                .append(leftBracket)
                .append(itemColored)
                .append(rightBracket);

        shown = shown.copy().withStyle(s -> s.withHoverEvent(new HoverEvent.ShowItem(stack)));

        int count = stack.getCount();
        net.minecraft.network.chat.MutableComponent baseMsg = (count > 1)
                ? Component.translatable("message.itempeek.shows_item_count", player.getName(), shown, count)
                : Component.translatable("message.itempeek.shows_item", player.getName(), shown);
        Component msg = baseMsg.withStyle(ChatFormatting.GRAY);

        for (ServerPlayer target : player.level().getServer().getPlayerList().getPlayers()) {
            target.sendSystemMessage(msg);
        }
    }

    public static void sendShowItemToServer(int slotIndex) {
        ClientPacketDistributor.sendToServer(new ItemPeekPayload(slotIndex));
    }
}
