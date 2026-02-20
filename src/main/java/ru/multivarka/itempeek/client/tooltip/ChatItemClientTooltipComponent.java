package ru.multivarka.itempeek.client.tooltip;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

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
    public void renderImage(Font font, int x, int y, int width, int height, GuiGraphics graphics) {
        int tooltipHeight = Math.max(ICON_SIZE, font.lineHeight + 2);
        int iconY = y + (tooltipHeight - ICON_SIZE) / 2 - 1;
        graphics.renderItem(this.stack, x, iconY);
        graphics.renderItemDecorations(font, this.stack, x, iconY);
    }

    @Override
    public void renderText(Font font, int x, int y, Matrix4f pose, MultiBufferSource.BufferSource buffer) {
        int height = Math.max(ICON_SIZE, font.lineHeight + 2);
        int textY = y + (height - font.lineHeight) / 2;
        int textX = x + ICON_SIZE + ICON_PADDING;
        font.drawInBatch(this.text, (float) textX, (float) textY, -1, true, pose, buffer, Font.DisplayMode.NORMAL, 0, 15728880);
    }
}

