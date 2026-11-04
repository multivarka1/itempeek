package ru.multivarka.itempeek;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.datafixers.util.Either;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import ru.multivarka.itempeek.client.tooltip.ChatItemClientTooltipComponent;
import ru.multivarka.itempeek.client.tooltip.ChatItemTooltipComponent;
import ru.multivarka.itempeek.mixin.client.ChatComponentAccessor;
import ru.multivarka.itempeek.mixin.client.GuiAccessor;

import java.util.List;
import java.util.Optional;

@Mod(value = ItemPeek.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = ItemPeek.MODID, value = Dist.CLIENT)
public class ItemPeekClient {
    private static final String ITEMPEEK_CATEGORY = "key.categories.itempeek";
    private static KeyMapping SHOW_ITEM_KEY;
    private static KeyMapping INSERT_ITEM_KEY;
    private static String pendingChatMarker;

    public ItemPeekClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        ItemPeek.LOGGER.info("Client setup initialized for itempeek");
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
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

    @SubscribeEvent
    public static void onTooltipGather(RenderTooltipEvent.GatherComponents event) {
        if (ModList.get().isLoaded("obscure_tooltips")) {
            return;
        }

        if (event.getItemStack().isEmpty()) {
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
        Optional<FormattedText> firstText = first.left();
        if (firstText.isEmpty()) {
            return;
        }

        FormattedText text = firstText.get();
        if (!event.getItemStack().getHoverName().getString().equals(text.getString())) {
            return;
        }

        Component title = text instanceof Component component
                ? component
                : Component.literal(text.getString());
        elements.set(0, Either.right(new ChatItemTooltipComponent(event.getItemStack(), title)));
    }

    @SubscribeEvent
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

    @SubscribeEvent
    public static void renderInlineItemIconsAfterChatScreen(ScreenEvent.Render.Post event) {
        if (event.getScreen() instanceof ChatScreen) {
            renderInlineItemIcons(event.getGuiGraphics(), true);
        }
    }

    private static void renderInlineItemIcons(GuiGraphics graphics, boolean chatFocused) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.chatVisibility().get().name().equals("HIDDEN")) {
            return;
        }

        ChatComponent chat = mc.gui.getChat();
        ChatComponentAccessor accessor = (ChatComponentAccessor) chat;
        List<GuiMessage.Line> lines = accessor.itempeek$getTrimmedMessages();
        int firstLine = accessor.itempeek$getChatScrollbarPos();
        int lineCount = chat.getLinesPerPage();
        int tick = ((GuiAccessor) mc.gui).itempeek$getTickCount();
        float chatOpacityMultiplier = (float) (mc.options.chatOpacity().get() * 0.9D + 0.1D);
        graphics.pose().pushPose();
        float scale = (float) chat.getScale();
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.pose().translate(4.0F, mc.getWindow().getGuiScaledHeight() - 40.0F, 0.0F);

        for (int row = 0; row < lineCount; row++) {
            int lineIndex = firstLine + row;
            if (lineIndex >= lines.size()) {
                break;
            }

            GuiMessage.Line line = lines.get(lineIndex);
            float opacity = chatOpacity(line, tick, chatFocused) * (chatFocused ? 1.0F : chatOpacityMultiplier);
            if (opacity <= 0.0F) {
                continue;
            }

            for (InlineItemIcon icon : findInlineItemIcons(line.content(), mc.font)) {
                graphics.pose().pushPose();
                graphics.pose().translate(icon.x(), -row * 9.0F - 10.0F, 0.0F);
                graphics.pose().scale(0.75F, 0.75F, 1.0F);
                Lighting.setupFor3DItems();
                graphics.setColor(1.0F, 1.0F, 1.0F, opacity);
                graphics.renderItem(icon.stack(), 0, 0);
                graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
                Lighting.setupFor3DItems();
                graphics.pose().popPose();
            }
        }

        graphics.pose().popPose();
    }

    private static float chatOpacity(GuiMessage.Line line, int tick, boolean focused) {
        if (focused) {
            return 1.0F;
        }

        int age = tick - line.addedTime();
        if (age < 0 || age >= 200) {
            return 0.0F;
        }

        float fade = Math.max(0.0F, Math.min(1.0F, 1.0F - age / 200.0F));
        fade = Math.max(0.0F, Math.min(1.0F, fade * 10.0F));
        return fade * fade;
    }

    private static List<InlineItemIcon> findInlineItemIcons(FormattedCharSequence text, net.minecraft.client.gui.Font font) {
        List<InlineItemIcon> icons = new java.util.ArrayList<>();
        StringBuilder lineText = new StringBuilder();
        boolean[] inItem = {false};
        float[] itemX = {0.0F};
        ItemStack[] itemStack = {ItemStack.EMPTY};
        StringBuilder itemPrefix = new StringBuilder();
        text.accept((index, style, codePoint) -> {
            HoverEvent hover = style.getHoverEvent();
            boolean hasItemHover = hover != null && hover.getAction() == HoverEvent.Action.SHOW_ITEM;
            if (hasItemHover && !inItem[0]) {
                HoverEvent.ItemStackInfo info = hover.getValue(HoverEvent.Action.SHOW_ITEM);
                if (info != null && !info.getItemStack().isEmpty()) {
                    itemX[0] = font.width(lineText.toString());
                    itemStack[0] = info.getItemStack().copyWithCount(1);
                    itemPrefix.setLength(0);
                }
            }
            if (hasItemHover && itemPrefix.length() < ItemPeek.ITEM_ICON_SPACER.length() + 1) {
                itemPrefix.appendCodePoint(codePoint);
                if (itemPrefix.length() == ItemPeek.ITEM_ICON_SPACER.length() + 1
                        && itemPrefix.toString().equals(ItemPeek.ITEM_ICON_SPACER + "[")
                        && !itemStack[0].isEmpty()) {
                    icons.add(new InlineItemIcon(itemX[0], itemStack[0]));
                }
            }
            if (!hasItemHover) {
                itemPrefix.setLength(0);
                itemStack[0] = ItemStack.EMPTY;
            }
            inItem[0] = hasItemHover;
            lineText.appendCodePoint(codePoint);
            return true;
        });
        return icons;
    }

    private record InlineItemIcon(float x, ItemStack stack) {}

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;

        normalizeLegacyKeyModifiers();

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (!(mc.screen instanceof AbstractContainerScreen<?> contScreen)) return;

        Slot hovered = contScreen.getSlotUnderMouse();
        if (hovered == null || !hovered.hasItem()) return;

        int menuIndex = contScreen.getMenu().slots.indexOf(hovered);
        if (menuIndex < 0) return;

        if (matches(event, SHOW_ITEM_KEY)) {
            Network.sendShowItemToServer(menuIndex);
        } else if (matches(event, INSERT_ITEM_KEY)) {
            insertItemIntoChat(mc, menuIndex, hovered);
        }
    }

    private static boolean matches(InputEvent.Key event, KeyMapping keyMapping) {
        if (keyMapping == null || !keyMapping.matches(event.getKey(), event.getScanCode())) {
            return false;
        }
        KeyModifier modifier = keyMapping.getKeyModifier();
        return modifier == KeyModifier.NONE || modifier.isActive(KeyConflictContext.GUI);
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

    private static void insertItemIntoChat(Minecraft mc, int slotIndex, Slot slot) {
        String marker = "[" + slot.getItem().getHoverName().getString() + "]";
        PacketDistributor.sendToServer(new InsertItemPayload(slotIndex, marker));
        pendingChatMarker = marker;
        mc.setScreen(null);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (pendingChatMarker == null) {
            return;
        }

        String marker = pendingChatMarker;
        pendingChatMarker = null;
        Minecraft.getInstance().setScreen(new ItemPeekChatScreen(marker));
    }

    @EventBusSubscriber(modid = ItemPeek.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void registerTooltipFactories(RegisterClientTooltipComponentFactoriesEvent event) {
            event.register(ChatItemTooltipComponent.class, ChatItemClientTooltipComponent::new);
        }
    }
}
