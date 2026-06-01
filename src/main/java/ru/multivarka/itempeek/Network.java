package ru.multivarka.itempeek;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.Map;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Network {
    private static final Map<UUID, PendingItem> PENDING_ITEMS = new ConcurrentHashMap<>();

    private Network() {}

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(ItemPeek.MODID);
        registrar.playToServer(ItemPeekPayload.TYPE, ItemPeekPayload.CODEC, (msg, ctx) -> {
            var player = (ServerPlayer) ctx.player();
            ctx.enqueueWork(() -> handleShowItem(player, msg.slotIndex()));
        });
        registrar.playToServer(InsertItemPayload.TYPE, InsertItemPayload.CODEC, (msg, ctx) -> {
            var player = (ServerPlayer) ctx.player();
            ctx.enqueueWork(() -> prepareInsertedItem(player, msg.slotIndex(), msg.marker()));
        });
        registrar.playToServer(PrivateItemMessagePayload.TYPE, PrivateItemMessagePayload.CODEC, (msg, ctx) -> {
            var player = (ServerPlayer) ctx.player();
            ctx.enqueueWork(() -> sendPrivateItemMessage(player, msg.targets(), msg.message()));
        });
        registrar.playToServer(ClearPendingItemPayload.TYPE, ClearPendingItemPayload.CODEC, (msg, ctx) -> {
            var player = (ServerPlayer) ctx.player();
            ctx.enqueueWork(() -> PENDING_ITEMS.remove(player.getUUID()));
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

        Component shown = createShownItem(stack, true, false);

        int count = stack.getCount();
        net.minecraft.network.chat.MutableComponent baseMsg = (count > 1)
                ? Component.translatable("message.itempeek.shows_item_count", player.getName(), shown, count)
                : Component.translatable("message.itempeek.shows_item", player.getName(), shown);
        Component msg = baseMsg.withStyle(ChatFormatting.GRAY);

        for (ServerPlayer target : player.level().getServer().getPlayerList().getPlayers()) {
            target.sendSystemMessage(msg);
        }
    }

    private static void prepareInsertedItem(ServerPlayer player, int slotIndex, String marker) {
        if (slotIndex < 0 || slotIndex >= player.containerMenu.slots.size()) {
            return;
        }

        ItemStack stack = player.containerMenu.getSlot(slotIndex).getItem();
        if (stack.isEmpty() || marker.isBlank() || marker.length() > 256) {
            return;
        }

        PENDING_ITEMS.put(player.getUUID(), new PendingItem(stack.copy(), marker));
    }

    public static void onServerChat(ServerChatEvent event) {
        PendingItem pending = PENDING_ITEMS.remove(event.getPlayer().getUUID());
        if (pending == null) {
            return;
        }

        String rawText = event.getRawText();
        int markerIndex = rawText.indexOf(pending.marker());
        if (markerIndex < 0) {
            return;
        }

        String before = rawText.substring(0, markerIndex);
        String after = rawText.substring(markerIndex + pending.marker().length());
        event.setMessage(Component.literal(before)
                .append(createShownItem(pending.stack(), false))
                .append(after));
    }

    private static void sendPrivateItemMessage(ServerPlayer sender, String targetsText, String message) {
        PendingItem pending = PENDING_ITEMS.remove(sender.getUUID());
        if (pending == null || targetsText.isBlank() || message.length() > 256) {
            return;
        }

        int markerIndex = message.indexOf(pending.marker());
        if (markerIndex < 0) {
            return;
        }

        Component content = Component.literal(message.substring(0, markerIndex))
                .append(createShownItem(pending.stack(), false))
                .append(message.substring(markerIndex + pending.marker().length()));
        try {
            var source = sender.createCommandSourceStack();
            var selector = EntityArgument.players().parse(new StringReader(targetsText), source);
            Collection<ServerPlayer> targets = selector.findPlayers(source);
            if (targets.isEmpty()) {
                throw EntityArgument.NO_PLAYERS_FOUND.create();
            }

            ChatType.Bound incoming = ChatType.bind(ChatType.MSG_COMMAND_INCOMING, source);
            for (ServerPlayer target : targets) {
                ChatType.Bound outgoing = ChatType.bind(ChatType.MSG_COMMAND_OUTGOING, source)
                        .withTargetName(target.getDisplayName());
                sender.sendSystemMessage(outgoing.decorate(content));
                target.sendSystemMessage(incoming.decorate(content));
            }
        } catch (CommandSyntaxException exception) {
            sender.sendSystemMessage(Component.literal(exception.getMessage()).withStyle(ChatFormatting.RED));
        }
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING_ITEMS.remove(event.getEntity().getUUID());
    }

    private static Component createShownItem(ItemStack stack, boolean leadingSpace) {
        return createShownItem(stack, leadingSpace, true);
    }

    private static Component createShownItem(ItemStack stack, boolean leadingSpace, boolean includeCount) {
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
        Component shown = Component.literal(leadingSpace ? " " : "")
                .append(leftBracket)
                .append(itemColored)
                .append(rightBracket);

        ItemStackTemplate template = ItemStackTemplate.fromNonEmptyStack(stack);
        shown = shown.copy().withStyle(s -> s.withHoverEvent(new HoverEvent.ShowItem(template)));
        if (includeCount && stack.getCount() > 1) {
            shown = shown.copy().append(Component.literal(" x" + stack.getCount())
                    .withStyle(ChatFormatting.GRAY));
        }
        return shown;
    }

    private record PendingItem(ItemStack stack, String marker) {}
}
