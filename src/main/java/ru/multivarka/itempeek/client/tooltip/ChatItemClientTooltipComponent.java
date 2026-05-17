package ru.multivarka.itempeek.client.tooltip;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

public final class ChatItemClientTooltipComponent implements ClientTooltipComponent {
    private static final int ICON_SIZE = 16;
    private static final int ICON_PADDING = 4;

    private final FormattedCharSequence text;
    private final ItemStack stack;

    public ChatItemClientTooltipComponent(ChatItemTooltipComponent component) {
        this.stack = component.stack();
        this.text = component.title().getVisualOrderText();
    }

    @Override
    public int getHeight(Font font) {
        return Math.max(ICON_SIZE, font.lineHeight + 2);
    }

    @Override
    public int getWidth(Font font) {
        return ICON_SIZE + ICON_PADDING + font.width(this.text);
    }

    @Override
    public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
        int componentHeight = Math.max(ICON_SIZE, font.lineHeight + 2);
        int iconY = y + (componentHeight - ICON_SIZE) / 2 - 1;
        graphics.item(this.stack, x, iconY);
        graphics.itemDecorations(font, this.stack, x, iconY);
    }

    @Override
    public void extractText(GuiGraphicsExtractor graphics, Font font, int x, int y) {
        int height = Math.max(ICON_SIZE, font.lineHeight + 2);
        int textY = y + (height - font.lineHeight) / 2;
        int textX = x + ICON_SIZE + ICON_PADDING;
        graphics.text(font, this.text, textX, textY, -1, true);
    }
}

