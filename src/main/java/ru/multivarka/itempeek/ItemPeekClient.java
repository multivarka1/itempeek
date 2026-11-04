package ru.multivarka.itempeek;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.datafixers.util.Either;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.common.NeoForge;
import ru.multivarka.itempeek.mixin.client.ChatComponentAccessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import ru.multivarka.itempeek.client.tooltip.ChatItemClientTooltipComponent;
import ru.multivarka.itempeek.client.tooltip.ChatItemTooltipComponent;

@Mod(value = ItemPeek.MODID, dist = Dist.CLIENT)
public class ItemPeekClient {
    private static final KeyMapping.Category ITEMPEEK_CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(ItemPeek.MODID, "itempeek"));
    private static KeyMapping SHOW_ITEM_KEY;
    private static KeyMapping INSERT_ITEM_KEY;
    private static String pendingChatMarker;

    public ItemPeekClient(IEventBus modEventBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        modEventBus.addListener(ItemPeekClient::onClientSetup);
        modEventBus.addListener(ItemPeekClient::registerKeys);
        modEventBus.addListener(ItemPeekClient::registerTooltipFactories);
        NeoForge.EVENT_BUS.addListener(ItemPeekClient::onTooltipGather);
        NeoForge.EVENT_BUS.addListener((RenderGuiLayerEvent.Post event) -> renderInlineItemIcons(event));
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Render.Post event) -> renderInlineItemIconsAfterChatScreen(event));
        NeoForge.EVENT_BUS.addListener(ItemPeekClient::onKeyInput);
        NeoForge.EVENT_BUS.addListener(ItemPeekClient::onClientTick);
    }

    static void onClientSetup(FMLClientSetupEvent event) {
        ItemPeek.LOGGER.info("Client setup initialized for itempeek");
    }

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(ITEMPEEK_CATEGORY);
        SHOW_ITEM_KEY = new KeyMapping(
                "key.itempeek.show_item",
                KeyConflictContext.GUI,
                KeyModifier.SHIFT,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_T,
                ITEMPEEK_CATEGORY);
        INSERT_ITEM_KEY = new KeyMapping(
                "key.itempeek.insert_item",
                KeyConflictContext.GUI,
                KeyModifier.SHIFT,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_Y,
                ITEMPEEK_CATEGORY);
        event.register(SHOW_ITEM_KEY);
        event.register(INSERT_ITEM_KEY);
    }

    public static void onTooltipGather(RenderTooltipEvent.GatherComponents event) {
        ItemStack stack = event.getItemStack();
        if (ModList.get().isLoaded("obscure_tooltips") || stack.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Screen currentScreen = mc.screen;
        if (currentScreen != null && !(currentScreen instanceof ChatScreen)) {
            return;
        }

        List<Either<FormattedText, TooltipComponent>> elements = event.getTooltipElements();
        if (elements.isEmpty()) {
            return;
        }

        Either<FormattedText, TooltipComponent> first = elements.get(0);
        if (first.right().isPresent()) {
            return;
        }

        Optional<FormattedText> firstText = first.left();
        if (firstText.isEmpty()) {
            return;
        }

        FormattedText textElement = firstText.get();
        if (!stack.getHoverName().getString().equals(textElement.getString())) {
            return;
        }

        Component title = textElement instanceof Component component
                ? component
                : Component.literal(textElement.getString());

        elements.set(0, Either.right(new ChatItemTooltipComponent(stack, title)));
    }

    public static void renderInlineItemIcons(RenderGuiLayerEvent.Post event) {
        if (!VanillaGuiLayers.CHAT.equals(event.getName())) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen instanceof ChatScreen) {
            return;
        }

        renderInlineItemIcons(event.getGuiGraphics(), false);
    }

    public static void renderInlineItemIconsAfterChatScreen(ScreenEvent.Render.Post event) {
        if (event.getScreen() instanceof ChatScreen) {
            renderInlineItemIcons(event.getGuiGraphics(), true);
        }
    }

    private static void renderInlineItemIcons(GuiGraphicsExtractor graphics, boolean chatFocused) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.chatVisibility().get().name().equals("HIDDEN")) {
            return;
        }

        ChatComponent chat = mc.gui.getChat();
        ChatComponentAccessor accessor = (ChatComponentAccessor) chat;
        List<GuiMessage.Line> lines = accessor.itempeek$getTrimmedMessages();
        int firstLine = accessor.itempeek$getChatScrollbarPos();
        int lineCount = chat.getLinesPerPage();
        int tick = mc.gui.getGuiTicks();
        graphics.pose().pushMatrix();
        float scale = (float) chat.getScale();
        graphics.pose().scale(scale, scale);
        graphics.pose().translate(4.0F, graphics.guiHeight() - 40.0F);

        for (int row = 0; row < lineCount; row++) {
            int lineIndex = firstLine + row;
            if (lineIndex >= lines.size()) {
                break;
            }

            GuiMessage.Line line = lines.get(lineIndex);
            if (!isChatLineVisible(line, tick, chatFocused)) {
                continue;
            }

            for (InlineItemIcon icon : findInlineItemIcons(line.content(), mc.font)) {
                graphics.pose().pushMatrix();
                graphics.pose().translate(icon.x(), -row * 9.0F - 10.0F);
                graphics.pose().scale(0.75F, 0.75F);
                graphics.item(icon.stack(), 0, 0);
                graphics.pose().popMatrix();
            }
        }

        graphics.pose().popMatrix();
    }

    private static boolean isChatLineVisible(GuiMessage.Line line, int tick, boolean focused) {
        if (focused) {
            return true;
        }

        int age = tick - line.addedTime();
        return age >= 0 && age < 200;
    }

    private static List<InlineItemIcon> findInlineItemIcons(FormattedCharSequence text, net.minecraft.client.gui.Font font) {
        List<InlineItemIcon> icons = new ArrayList<>();
        StringBuilder lineText = new StringBuilder();
        boolean[] inItem = {false};
        float[] itemX = {0.0F};
        ItemStack[] itemStack = {ItemStack.EMPTY};
        StringBuilder itemPrefix = new StringBuilder();
        text.accept((index, style, codePoint) -> {
            HoverEvent hover = style.getHoverEvent();
            if (hover instanceof HoverEvent.ShowItem showItem) {
                if (!inItem[0]) {
                    ItemStack hoveredStack = showItem.item().create();
                    if (!hoveredStack.isEmpty()) {
                        itemX[0] = font.width(lineText.toString());
                        itemStack[0] = hoveredStack.copyWithCount(1);
                        itemPrefix.setLength(0);
                    }
                }

                if (itemPrefix.length() < ItemPeek.ITEM_ICON_SPACER.length() + 1) {
                    itemPrefix.appendCodePoint(codePoint);
                    if (itemPrefix.length() == ItemPeek.ITEM_ICON_SPACER.length() + 1
                            && itemPrefix.toString().equals(ItemPeek.ITEM_ICON_SPACER + "[")
                            && !itemStack[0].isEmpty()) {
                        icons.add(new InlineItemIcon(itemX[0], itemStack[0]));
                    }
                }
                inItem[0] = true;
            } else {
                itemPrefix.setLength(0);
                itemStack[0] = ItemStack.EMPTY;
                inItem[0] = false;
            }

            lineText.appendCodePoint(codePoint);
            return true;
        });
        return icons;
    }

    private record InlineItemIcon(float x, ItemStack stack) {}

    public static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;

        normalizeLegacyKeyModifiers();

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (!(mc.screen instanceof AbstractContainerScreen<?> contScreen)) return;

        Slot hovered = contScreen.getHoveredSlot();
        if (hovered == null || !hovered.hasItem()) return;

        int menuIndex = contScreen.getMenu().slots.indexOf(hovered);
        if (menuIndex < 0) return;

        if (matches(event, SHOW_ITEM_KEY)) {
            sendShowItemToServer(menuIndex);
        } else if (matches(event, INSERT_ITEM_KEY)) {
            insertItemIntoChat(mc, menuIndex, hovered);
        }
    }

    private static boolean matches(InputEvent.Key event, KeyMapping keyMapping) {
        return keyMapping != null && keyMapping.isActiveAndMatches(InputConstants.Type.KEYSYM.getOrCreate(event.getKey()));
    }

    private static void normalizeLegacyKeyModifiers() {
        normalizeLegacyKeyModifier(SHOW_ITEM_KEY, GLFW.GLFW_KEY_T);
        normalizeLegacyKeyModifier(INSERT_ITEM_KEY, GLFW.GLFW_KEY_Y);
    }

    private static void normalizeLegacyKeyModifier(KeyMapping keyMapping, int defaultKey) {
        if (keyMapping != null
                && keyMapping.getKeyModifier() == KeyModifier.NONE
                && keyMapping.getKey().getValue() == defaultKey) {
            keyMapping.setKeyModifierAndCode(KeyModifier.SHIFT, keyMapping.getKey());
        }
    }

    private static void sendShowItemToServer(int slotIndex) {
        ClientPacketDistributor.sendToServer(new ItemPeekPayload(slotIndex));
    }

    private static void insertItemIntoChat(Minecraft mc, int slotIndex, Slot slot) {
        String marker = "[" + slot.getItem().getHoverName().getString() + "]";
        ClientPacketDistributor.sendToServer(new InsertItemPayload(slotIndex, marker));
        pendingChatMarker = marker;
        mc.setScreen(null);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (pendingChatMarker == null) {
            return;
        }

        String marker = pendingChatMarker;
        pendingChatMarker = null;
        Minecraft.getInstance().setScreen(new ItemPeekChatScreen(marker));
    }

    public static void registerTooltipFactories(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(ChatItemTooltipComponent.class, ChatItemClientTooltipComponent::new);
    }
}

