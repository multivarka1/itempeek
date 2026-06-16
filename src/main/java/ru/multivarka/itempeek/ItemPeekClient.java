package ru.multivarka.itempeek;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.common.NeoForge;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import com.mojang.datafixers.util.Either;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
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
        if (event.getItemStack().isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Screen currentScreen = mc.gui.screen();
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
        Component title = textElement instanceof Component component
                ? component
                : Component.literal(textElement.getString());

        elements.set(0, Either.right(new ChatItemTooltipComponent(event.getItemStack(), title)));
    }

    public static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;

        normalizeLegacyKeyModifiers();

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> contScreen)) return;

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
        mc.gui.setScreen(null);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (pendingChatMarker == null) {
            return;
        }

        String marker = pendingChatMarker;
        pendingChatMarker = null;
        Minecraft.getInstance().gui.setScreen(new ItemPeekChatScreen(marker));
    }

    public static void registerTooltipFactories(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(ChatItemTooltipComponent.class, ChatItemClientTooltipComponent::new);
    }
}

