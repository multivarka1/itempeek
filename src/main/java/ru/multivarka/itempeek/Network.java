package ru.multivarka.itempeek;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid = ItemPeek.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Network {
    private static final String PROTOCOL_VERSION = "1";
    private static final int MAX_SHOW_ITEM_HOVER_COUNT = 99;
    private static final Map<UUID, PendingItem> PENDING_ITEMS = new ConcurrentHashMap<>();
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(ItemPeek.MODID, "main"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(s -> true)
            .serverAcceptedVersions(s -> true)
            .simpleChannel();
    private static final ru.multivarka.itempeek.spam.ItemPeekRateLimiter LIMITER = new ru.multivarka.itempeek.spam.ItemPeekRateLimiter();

    private Network() {}

    public static void reset(UUID id) { LIMITER.clear(id); }
    public static void resetAll() { LIMITER.clearAll(); }
    public static int stateCount(UUID id) { return LIMITER.count(id); }

    public static void init() {
        int id = 0;
        CHANNEL.messageBuilder(ItemPeekMessage.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ItemPeekMessage::encode)
                .decoder(ItemPeekMessage::decode)
                .consumerMainThread((msg, ctx) -> handleServerMessage(ctx, player -> handleShowItem(player, msg.slotIndex)))
                .add();
        CHANNEL.messageBuilder(InsertItemMessage.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(InsertItemMessage::encode)
                .decoder(InsertItemMessage::decode)
                .consumerMainThread((msg, ctx) -> handleServerMessage(ctx, player -> prepareInsertedItem(player, msg.slotIndex, msg.marker)))
                .add();
        CHANNEL.messageBuilder(PrivateItemMessage.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PrivateItemMessage::encode)
                .decoder(PrivateItemMessage::decode)
                .consumerMainThread((msg, ctx) -> handleServerMessage(ctx, player -> sendPrivateItemMessage(player, msg.targets, msg.message)))
                .add();
        CHANNEL.messageBuilder(ClearPendingItemMessage.class, id, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ClearPendingItemMessage::encode)
                .decoder(ClearPendingItemMessage::decode)
                .consumerMainThread((msg, ctx) -> handleServerMessage(ctx, player -> PENDING_ITEMS.remove(player.getUUID())))
                .add();
    }

    private static void handleServerMessage(Supplier<NetworkEvent.Context> ctxSupplier, ServerPlayerAction action) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ServerPlayer player = ctx.getSender();
        if (player != null) {
            action.accept(player);
        }
        ctx.setPacketHandled(true);
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
        MutableComponent baseMsg = (count > 1)
                ? Component.translatable("message.itempeek.shows_item_count", player.getName(), shown, count)
                : Component.translatable("message.itempeek.shows_item", player.getName(), shown);
        Component msg = baseMsg.withStyle(ChatFormatting.GRAY);

        for (ServerPlayer target : player.server.getPlayerList().getPlayers()) {
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

    @SubscribeEvent
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
        Component message = Component.literal(before)
                .append(createShownItem(pending.stack(), false))
                .append(after);

        if (broadcastBeautifiedChatMessage(event.getPlayer(), message)) {
            event.setCanceled(true);
            return;
        }

        event.setMessage(message);
    }

    private static boolean broadcastBeautifiedChatMessage(ServerPlayer player, Component message) {
        if (!ModList.get().isLoaded("beautifiedchatserver")) {
            return false;
        }

        try {
            Component formatted = createBeautifiedChatMessage(player, message);
            player.server.getPlayerList().broadcastSystemMessage(formatted, false);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            ItemPeek.LOGGER.warn("Unable to preserve item hover in Beautified Chat Server message", exception);
            return false;
        }
    }

    private static Component createBeautifiedChatMessage(ServerPlayer player, Component chatMessage)
            throws ReflectiveOperationException {
        String username = player.getName().getString();
        String timestamp = new SimpleDateFormat(getBeautifiedStringConfig("timestampFormat"))
                .format(new Date());
        MutableComponent result = Component.literal("");

        for (String segment : getBeautifiedStringConfig("chatMessageFormat").split("%", -1)) {
            ChatFormatting colour = getBeautifiedColour(segment, username);
            Component piece;
            if (segment.equalsIgnoreCase("timestamp")) {
                piece = Component.literal(timestamp);
            } else if (segment.equalsIgnoreCase("username")) {
                piece = Component.literal(createBeautifiedUsername(username));
            } else if (segment.equalsIgnoreCase("chatmessage")) {
                piece = chatMessage.copy();
            } else {
                piece = Component.literal(segment);
            }

            result.append(piece.copy().withStyle(colour));
        }

        return result;
    }

    private static String createBeautifiedUsername(String username) throws ReflectiveOperationException {
        if (!getBeautifiedBooleanConfig("showRankTitles")) {
            return username;
        }

        Optional<?> rank = getBeautifiedRank(username);
        if (rank.isEmpty()) {
            return username;
        }

        String rankTitle = getBeautifiedStringConfig("rankTitleFormat")
                .replace("%rank", capitalizeEveryWord(rank.get().toString()));
        return rankTitle + username;
    }

    private static String getBeautifiedStringConfig(String fieldName) throws ReflectiveOperationException {
        return getBeautifiedConfigField(fieldName).get(null).toString();
    }

    private static boolean getBeautifiedBooleanConfig(String fieldName) throws ReflectiveOperationException {
        return getBeautifiedConfigField(fieldName).getBoolean(null);
    }

    private static Field getBeautifiedConfigField(String fieldName) throws ReflectiveOperationException {
        return Class.forName("com.natamus.beautifiedchatserver_common_forge.config.ConfigHandler")
                .getField(fieldName);
    }

    private static ChatFormatting getBeautifiedColour(String segment, String username) throws ReflectiveOperationException {
        Method method = Class.forName("com.natamus.beautifiedchatserver_common_forge.util.Util")
                .getMethod("getColour", String.class, String.class);
        return (ChatFormatting) method.invoke(null, segment, username);
    }

    private static Optional<?> getBeautifiedRank(String username) throws ReflectiveOperationException {
        Method method = Class.forName("com.natamus.beautifiedchatserver_common_forge.util.Util")
                .getMethod("getRankOfPlayer", String.class);
        return (Optional<?>) method.invoke(null, username);
    }

    private static String capitalizeEveryWord(String value) {
        String[] words = value.replace('_', ' ').split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(word.substring(0, 1).toUpperCase(Locale.ROOT));
            if (word.length() > 1) {
                result.append(word.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return result.toString();
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
            var selector = EntityArgument.players().parse(new StringReader(targetsText));
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

    @SubscribeEvent
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

        ItemStack hoverStack = stack.copy();
        hoverStack.setCount(Math.min(stack.getCount(), Math.min(stack.getMaxStackSize(), MAX_SHOW_ITEM_HOVER_COUNT)));
        HoverEvent.ItemStackInfo info = new HoverEvent.ItemStackInfo(hoverStack);
        shown = shown.copy().withStyle(s -> s.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM, info)));
        if (includeCount && stack.getCount() > 1) {
            shown = shown.copy().append(Component.literal(" x" + stack.getCount())
                    .withStyle(ChatFormatting.GRAY));
        }
        return shown;
    }

    public static void sendShowItemToServer(int slotIndex) {
        CHANNEL.sendToServer(new ItemPeekMessage(slotIndex));
    }

    public static void sendInsertItemToServer(int slotIndex, String marker) {
        CHANNEL.sendToServer(new InsertItemMessage(slotIndex, marker));
    }

    public static void sendPrivateItemMessageToServer(String targets, String message) {
        CHANNEL.sendToServer(new PrivateItemMessage(targets, message));
    }

    public static void clearPendingItemOnServer() {
        CHANNEL.sendToServer(ClearPendingItemMessage.INSTANCE);
    }

    private interface ServerPlayerAction {
        void accept(ServerPlayer player);
    }

    private record PendingItem(ItemStack stack, String marker) {}
}
