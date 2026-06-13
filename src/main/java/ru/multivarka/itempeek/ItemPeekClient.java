package ru.multivarka.itempeek;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.datafixers.util.Either;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;
import ru.multivarka.itempeek.client.tooltip.ChatItemClientTooltipComponent;
import ru.multivarka.itempeek.client.tooltip.ChatItemTooltipComponent;

public class ItemPeekClient {
    private static final String ITEMPEEK_CATEGORY = "key.categories.itempeek";
    private static KeyMapping SHOW_ITEM_KEY;
    private static KeyMapping INSERT_ITEM_KEY;
    private static String pendingChatMarker;

    @Mod.EventBusSubscriber(modid = ItemPeek.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
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
        public static void registerTooltipFactories(RegisterClientTooltipComponentFactoriesEvent event) {
            event.register(ChatItemTooltipComponent.class, ChatItemClientTooltipComponent::new);
        }
    }

    @Mod.EventBusSubscriber(modid = ItemPeek.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ForgeBusEvents {
        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            if (event.getAction() != GLFW.GLFW_PRESS) {
                return;
            }

            normalizeLegacyKeyModifiers();

            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                return;
            }

            if (!(mc.screen instanceof AbstractContainerScreen<?> contScreen)) {
                return;
            }

            Slot hovered = contScreen.getSlotUnderMouse();
            if (hovered == null || !hovered.hasItem()) {
                return;
            }

            int menuIndex = contScreen.getMenu().slots.indexOf(hovered);
            if (menuIndex < 0) {
                return;
            }

            if (matches(event, SHOW_ITEM_KEY)) {
                Network.sendShowItemToServer(menuIndex);
            } else if (matches(event, INSERT_ITEM_KEY)) {
                insertItemIntoChat(mc, menuIndex, hovered);
            }
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END || pendingChatMarker == null) {
                return;
            }

            String marker = pendingChatMarker;
            pendingChatMarker = null;
            Minecraft.getInstance().setScreen(new ItemPeekChatScreen(marker));
        }

        @SubscribeEvent
        public static void onGatherTooltip(RenderTooltipEvent.GatherComponents event) {
            ItemStack stack = event.getItemStack();
            if (stack.isEmpty()) {
                return;
            }

            Minecraft mc = Minecraft.getInstance();
            Screen currentScreen = mc.screen;
            if (currentScreen != null && !(currentScreen instanceof ChatScreen)) {
                return;
            }

            var tooltipElements = event.getTooltipElements();
            if (tooltipElements.isEmpty()) {
                return;
            }

            var first = tooltipElements.get(0);
            Component title = first.left()
                    .filter(Component.class::isInstance)
                    .map(Component.class::cast)
                    .orElse(null);
            if (title == null) {
                return;
            }

            tooltipElements.remove(0);
            tooltipElements.add(0, Either.right(new ChatItemTooltipComponent(stack, title)));
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
        Network.sendInsertItemToServer(slotIndex, marker);
        pendingChatMarker = marker;
        mc.setScreen(null);
    }
}
